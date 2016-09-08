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
import com.android.builder.Version;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.storage.Storage;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.primitives.Longs;
import com.google.protobuf.util.Timestamps;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging;
import com.google.wireless.android.sdk.stats.AndroidStudioStats;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Uploads profiling data to Google Storage from the gradle performance tests.
 */
public class GradleProfileUploader implements Closeable {

    private static final String STORAGE_SCOPE =
            "https://www.googleapis.com/auth/devstorage.read_write";

    private static final String STORAGE_BUCKET = "android-gradle-logging-benchmark-results";

    @NonNull private static Uploader sUploader = GradleProfileUploader::uploadData;

    private final boolean enabled;

    @Nullable private final Logging.Benchmark benchmark;

    @Nullable private final Logging.BenchmarkMode benchmarkMode;

    private Path temporaryFile;

    public GradleProfileUploader(
            boolean enabled,
            @Nullable Logging.Benchmark benchmark,
            @Nullable Logging.BenchmarkMode benchmarkMode) {
        this.enabled = enabled;
        this.benchmark = benchmark;
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
                    .resolve("profile.rawproto");
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
        Preconditions.checkNotNull(benchmark);
        Preconditions.checkNotNull(benchmarkMode);

        AndroidStudioStats.GradleBuildProfile profile =
                    AndroidStudioStats.GradleBuildProfile.parseFrom(
                            Files.readAllBytes(temporaryFile));
        Files.delete(temporaryFile);
        Files.delete(temporaryFile.getParent());

        Logging.GradleBenchmarkResult.Builder gradleBenchmarkResult =
                Logging.GradleBenchmarkResult.newBuilder()
                        .setProfile(profile)
                        .setResultId(UUID.randomUUID().toString())
                        .setBenchmark(benchmark)
                        .setBenchmarkMode(benchmarkMode)
                        .setHostname(InetAddress.getLocalHost().getHostName())
                        .setTimestamp(Timestamps.fromMillis(System.currentTimeMillis()));

        String userName = System.getProperty("user.name");
        if (userName != null) {
            gradleBenchmarkResult.setUsername(userName);
        }

        String buildBotBuildNumber = System.getenv("BUILDBOT_BUILDNUMBER");
        if (buildBotBuildNumber != null) {
            Logging.GradleBenchmarkResult.ScheduledBuild.Builder scheduledBuild =
                    Logging.GradleBenchmarkResult.ScheduledBuild.newBuilder();
            Long buildNumber = Longs.tryParse(buildBotBuildNumber);
            if (buildNumber != null) {
                scheduledBuild.setBuildbotBuildNumber(buildNumber);
            }
            gradleBenchmarkResult.setScheduledBuild(scheduledBuild);
        } else {
            Logging.GradleBenchmarkResult.Experiment.Builder experiment =
                    Logging.GradleBenchmarkResult.Experiment.newBuilder();
            // TODO: way to set experiment comment
            gradleBenchmarkResult.setExperiment(experiment);
        }

        sUploader.uploadData(gradleBenchmarkResult.build());
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

        void uploadData(@NonNull Logging.GradleBenchmarkResult result) throws IOException;
    }

    private static void uploadData(@NonNull Logging.GradleBenchmarkResult result)
            throws IOException {
        GoogleCredential credential;
        try {
            credential =
                    GoogleCredential.getApplicationDefault()
                            .createScoped(Collections.singleton(STORAGE_SCOPE));
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Authentication failed:\n "
                            + "see https://cloud.google.com/storage/docs/xml-api/java-samples"
                            + "#setup-env\n"
                            + "And run $ gcloud beta auth application-default login",
                    e);
        }

        HttpTransport httpTransport;
        try {
            httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();

        Storage storage =
                new Storage.Builder(httpTransport, jsonFactory, credential)
                        .setApplicationName(
                                "Android-Gradle-Plugin-Performance-Test-Upload/"
                                        + Version.ANDROID_GRADLE_PLUGIN_VERSION)
                        .build();

        byte[] bytes = result.toByteArray();

        InputStreamContent content =
                new InputStreamContent("application/octet-stream", new ByteArrayInputStream(bytes));

        Instant timestamp = Instant.ofEpochMilli(Timestamps.toMillis(result.getTimestamp()));
        HashCode sha1 = Hashing.sha1().hashBytes(bytes);

        String name = DateTimeFormatter.ISO_INSTANT.format(timestamp) + "_" + sha1.toString();

        storage.objects().insert(STORAGE_BUCKET, null, content).setName(name).execute();
    }
}
