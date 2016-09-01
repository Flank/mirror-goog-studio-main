/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.build.gradle.integration.common.fixture;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.build.gradle.AndroidGradleOptions;
import com.android.build.gradle.integration.performance.BenchmarkMode;
import com.android.builder.profile.ThreadRecorder;
import com.android.utils.ILogger;
import com.android.utils.StdLogger;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.stream.JsonWriter;
import com.google.wireless.android.sdk.stats.AndroidStudioStats;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Temporary implementation of the legacy json uploader for performance tests.
 *
 * <p>Uploads when {@code RECORD_SPANS} is set.
 *
 * <p>Will go away in a few weeks once we switch to uploading proto files.
 *
 * <p>Most of this implementation is simply moved from the production profile.
 */
@Deprecated
public class GradleProfileUploader implements Closeable {

    private static boolean ENABLED = !Strings.isNullOrEmpty(System.getenv("RECORD_SPANS"));

    private static final ILogger LOGGER = new StdLogger(StdLogger.Level.VERBOSE);

    @NonNull
    private static Uploader sUploader = GradleProfileUploader::uploadData;

    private final boolean enabled;

    @Nullable
    private final String benchmarkName;

    @Nullable
    private final BenchmarkMode benchmarkMode;

    private Path temporaryFile;

    public GradleProfileUploader(
            boolean enabled,
            @Nullable String benchmarkName,
            @Nullable BenchmarkMode benchmarkMode) {
        this.enabled = enabled;
        this.benchmarkName = benchmarkName;
        this.benchmarkMode = benchmarkMode;
    }

    /**
     * Inject the arguments to output the profile.
     *
     * @param args the original arguments.
     * @return the arguments with the benchmark argument added if applicable.
     */
    public List<String> appendArg(@NonNull List<String> args) {
        if (!enabled) {
            return args;
        }
        try {
            temporaryFile = Files.createTempDirectory("gradle_profile_proto")
                    .resolve("profile.proto");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (!Files.isDirectory(temporaryFile.getParent())) {
            throw new RuntimeException("Profile directory " + temporaryFile.getParent()
                    + "should have been created.");
        }
        if (Files.exists(temporaryFile)) {
            throw new RuntimeException("Profile file " + temporaryFile
                    + " should have been created.");
        }

        return ImmutableList.<String>builder()
                .addAll(args)
                .add("-P" + AndroidGradleOptions.PROPERTY_BENCHMARK_PROFILE_FILE + "=" +
                        temporaryFile.toString())
                .build();
    }

    @Override
    public void close() throws IOException {
        if (!enabled) {
            return;
        }
        if (temporaryFile == null) {
            throw new IllegalStateException("appendArg must be called");
        }
        if (!Files.isRegularFile(temporaryFile)) {
            throw new RuntimeException("Profile infrastructure failure: "
                    + "Profile " + temporaryFile + " should have been written.");
        }
        Preconditions.checkNotNull(benchmarkName);
        Preconditions.checkNotNull(benchmarkMode);
        sUploader.uploadData(temporaryFile, benchmarkName, benchmarkMode);
    }

    /**
     * Allow the upload method to be substituted for testing purposes.
     */
    @VisibleForTesting
    public static void setUploader(@NonNull Uploader uploader) {
        sUploader = uploader;
    }

    @VisibleForTesting
    public interface Uploader {

        void uploadData(
                @NonNull Path outputFile,
                @NonNull String benchmarkName,
                @NonNull BenchmarkMode benchmarkMode) throws IOException;
    }

    private static void uploadData(@NonNull Path outputFile,
            @NonNull String benchmarkName,
            @NonNull BenchmarkMode benchmarkMode) throws IOException {
        if (!ENABLED) {
            LOGGER.info("RECORD_SPANS is not set, not uploading or deleting the profile."
                    + "Profile file: %1Ss.", outputFile);
            return;
        }

        try (Closeable ignored = () -> {
            Files.delete(outputFile);
            Files.delete(outputFile.getParent());
        }) {
            LOGGER.info("Uploading profile %1$s", outputFile);
            URL u = new URL("http://android-devtools-logging.appspot.com/log/");
            HttpURLConnection conn = (HttpURLConnection) u.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");

            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            byte[] data = convertToJson(outputFile, benchmarkName, benchmarkMode);

            conn.setRequestProperty("Content-Length", String.valueOf(data.length));
            try (OutputStream os = conn.getOutputStream()) {
                os.write(data);
            }

            String line;
            try (BufferedReader reader =
                         new BufferedReader(new InputStreamReader(conn.getInputStream()))) {

                while ((line = reader.readLine()) != null) {
                    LOGGER.verbose("From POST : " + line);
                }
            }
            conn.disconnect();
            LOGGER.info("Upload complete.");
        }
    }

    //TODO: remove once infra accepts a proto directly
    private static byte[] convertToJson(
            @NonNull Path outputFile,
            @NonNull String benchmarkName,
            @NonNull BenchmarkMode benchmarkMode) throws IOException {
        AndroidStudioStats.GradleBuildProfile profile =
                AndroidStudioStats.GradleBuildProfile.parseFrom(Files.readAllBytes(outputFile));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeDebugRecords(new OutputStreamWriter(baos), profile, benchmarkName, benchmarkMode);
        return baos.toByteArray();
    }


    private static void writeDebugRecords(
            @NonNull Writer writer,
            @NonNull AndroidStudioStats.GradleBuildProfile profile,
            @NonNull String benchmarkName,
            @NonNull BenchmarkMode benchmarkMode) throws IOException {
        ImmutableMap.Builder<String, String> properties = ImmutableMap.builder();
        properties.put("build_id", UUID.randomUUID().toString());
        properties.put("os_name", profile.getOsName());
        properties.put("os_version", profile.getOsVersion());
        properties.put("java_version", profile.getJavaVersion());
        properties.put("java_vm_version", profile.getJavaVmVersion());
        properties.put("max_memory", Long.toString(profile.getMaxMemory()));
        if (benchmarkName != null) {
            properties.put("benchmark_name", benchmarkName);
        }
        if (benchmarkMode != null) {
            properties.put("benchmark_mode", benchmarkMode.toString());
        }
        // Set next_gen_plugin to true as long as one of the project use the component model plugin.
        for (AndroidStudioStats.GradleBuildProject project :
                profile.getProjectList()) {
            if (project.getPluginGeneration()
                    == AndroidStudioStats.GradleBuildProject.PluginGeneration.COMPONENT_MODEL) {
                properties.put("next_gen_plugin", "true");
                break;
            }
        }
        write(writer,
                AndroidStudioStats.GradleBuildProfileSpan.newBuilder()
                        .setId(1)
                        .setStartTimeInMs(profile.getMemorySample(0).getTimestamp())
                        .setType(
                                AndroidStudioStats.GradleBuildProfileSpan.ExecutionType.INITIAL_METADATA)
                        .build(),
                properties.build());
        // Spans
        for (AndroidStudioStats.GradleBuildProfileSpan span : profile.getSpanList()) {
            ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
            if (span.getProject() != 0) {
                builder.put("project", Long.toString(span.getProject()));
                if (span.getVariant() != 0) {
                    builder.put("variant", Long.toString(span.getVariant()));
                }
            }
            write(writer, span, builder.build());
        }
        // Final metadata
        write(writer,
                AndroidStudioStats.GradleBuildProfileSpan.newBuilder()
                        .setId(ThreadRecorder.get().allocationRecordId())
                        .setStartTimeInMs(profile.getMemorySample(0).getTimestamp())
                        .setType(
                                AndroidStudioStats.GradleBuildProfileSpan.ExecutionType.FINAL_METADATA)
                        .build(),
                ImmutableMap.of(
                        "build_time", Long.toString(profile.getBuildTime()),
                        "gc_count", Long.toString(profile.getGcCount()),
                        "gc_time", Long.toString(profile.getGcTime())));
    }

    private static void write(
            @NonNull Writer writer,
            @NonNull AndroidStudioStats.GradleBuildProfileSpan executionRecord,
            @NonNull Map<String, String> attributes)
            throws IOException {
        // We want to keep the underlying stream open.
        //noinspection IOResourceOpenedButNotSafelyClosed
        JsonWriter mJsonWriter = new JsonWriter(writer);
        mJsonWriter.beginObject();
        {
            mJsonWriter.name("id").value(executionRecord.getId());
            mJsonWriter.name("parentId").value(executionRecord.getParentId());
            mJsonWriter.name("startTimeInMs").value(executionRecord.getStartTimeInMs());
            mJsonWriter.name("durationInMs").value(executionRecord.getDurationInMs());
            String type = executionRecord.getType().toString();
            if (executionRecord.hasTask()) {
                type = type + "_" + executionRecord.getTask().getType().toString();
            } else if (executionRecord.hasTransform()) {
                type = type + "_" + executionRecord.getTransform().getType().toString();
            }
            mJsonWriter.name("type").value(type);
            mJsonWriter.name("attributes");
            mJsonWriter.beginArray();
            {
                for (Map.Entry<String, String> entry : attributes.entrySet()) {
                    mJsonWriter.beginObject();
                    {
                        mJsonWriter.name("name").value(entry.getKey());
                        mJsonWriter.name("value").value(entry.getValue());
                    }
                    mJsonWriter.endObject();
                }
            }
            mJsonWriter.endArray();
        }
        mJsonWriter.endObject();
        mJsonWriter.flush();
        writer.append("\n");
    }

}
