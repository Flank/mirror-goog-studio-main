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

package com.android.build.gradle.tasks;

import static com.google.common.base.Preconditions.checkArgument;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.external.gson.NativeBuildConfigValue;
import com.android.build.gradle.external.gson.NativeLibraryValue;
import com.android.build.gradle.external.gson.PlainFileGsonTypeAdaptor;
import com.android.build.gradle.internal.model.CoreExternalNativeBuild;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.builder.core.AndroidBuilder;
import com.android.ide.common.process.BuildCommandException;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessInfoBuilder;
import com.android.ide.common.process.ProcessOutput;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.utils.ILogger;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.io.FileBackedOutputStream;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Shared utility methods for dealing with external native build tasks.
 */
public class ExternalNativeBuildTaskUtils {
    /**
     * Utility function that takes an ABI string and returns the corresponding output folder. Output
     * folder is where build artifacts are placed.
     */
    @NonNull
    static File getOutputFolder(@NonNull File jsonFolder, @NonNull String abi) {
        return new File(jsonFolder, abi);
    }

    /**
     * Utility function that gets the name of the output JSON for a particular ABI.
     */
    @NonNull
    public static File getOutputJson(@NonNull File jsonFolder, @NonNull String abi) {
        return new File(getOutputFolder(jsonFolder, abi), "android_gradle_build.json");
    }

    @NonNull
    public static List<File> getOutputJsons(@NonNull File jsonFolder,
            @NonNull Collection<String> abis) {
        List<File> outputs = Lists.newArrayList();
        for (String abi : abis) {
            outputs.add(getOutputJson(jsonFolder, abi));
        }
        return outputs;
    }

    /**
     * Deserialize a JSON file into NativeBuildConfigValue. Emit task-specific exception if there is
     * an issue.
     */
    @NonNull
    static NativeBuildConfigValue getNativeBuildConfigValue(
            @NonNull File json,
            @NonNull String groupName) throws IOException {
        checkArgument(!Strings.isNullOrEmpty(groupName),
                "group name missing in", json);

        Gson gson = new GsonBuilder()
                .registerTypeAdapter(File.class, new PlainFileGsonTypeAdaptor())
                .create();
        List<String> lines = Files.readLines(json, Charsets.UTF_8);
        NativeBuildConfigValue config = gson.fromJson(Joiner.on("\n").join(lines),
                NativeBuildConfigValue.class);
        if (config.libraries == null) {
            return config;
        }
        for (NativeLibraryValue library : config.libraries.values()) {
            library.groupName = groupName;
        }
        return config;
    }

    /**
     * Deserialize a JSON files into NativeBuildConfigValue.
     */
    @NonNull
    public static Collection<NativeBuildConfigValue> getNativeBuildConfigValues(
            @NonNull Collection<File> jsons,
            @NonNull String groupName) throws IOException {
        List<NativeBuildConfigValue> configValues = Lists.newArrayList();
        for (File json : jsons) {
            configValues.add(getNativeBuildConfigValue(json, groupName));
        }
        return configValues;
    }

    /** Return true if we should regenerate out-of-date JSON files. */
    public static boolean shouldRegenerateOutOfDateJsons(@NonNull ProjectOptions options) {
        return options.get(BooleanOption.IDE_BUILD_MODEL_ONLY)
                || options.get(BooleanOption.IDE_BUILD_MODEL_ONLY_ADVANCED)
                || options.get(BooleanOption.IDE_INVOKED_FROM_IDE)
                || options.get(BooleanOption.IDE_REFRESH_EXTERNAL_NATIVE_MODEL);
    }

    public static boolean isExternalNativeBuildEnabled(@NonNull CoreExternalNativeBuild config) {
        return (config.getNdkBuild().getPath() != null)
                || (config.getCmake().getPath() != null);
    }

    public static class ExternalNativeBuildProjectPathResolution {
        @Nullable
        public final String errorText;
        @Nullable
        public final NativeBuildSystem buildSystem;
        @Nullable
        public final File makeFile;
        @Nullable public final File externalNativeBuildDir;

        private ExternalNativeBuildProjectPathResolution(
                @Nullable NativeBuildSystem buildSystem,
                @Nullable File makeFile,
                @Nullable File externalNativeBuildDir,
                @Nullable String errorText) {
            checkArgument(makeFile == null || buildSystem != null,
                    "Expected path and buildSystem together, no taskClass");
            checkArgument(makeFile != null || buildSystem == null,
                    "Expected path and buildSystem together, no path");
            checkArgument(makeFile == null || errorText == null,
                    "Expected path or error but both existed");
            this.buildSystem = buildSystem;
            this.makeFile = makeFile;
            this.externalNativeBuildDir = externalNativeBuildDir;
            this.errorText = errorText;
        }
    }

    /**
     * Resolve the path of any native build project.
     * @param config -- the AndroidConfig
     * @return Path resolution.
     */
    @NonNull
    public static ExternalNativeBuildProjectPathResolution getProjectPath(
            @NonNull CoreExternalNativeBuild config) {
        // Path discovery logic:
        // If there is exactly 1 path in the DSL, then use it.
        // If there are more than 1, then that is an error. The user has specified both cmake and
        //    ndkBuild in the same project.

        Map<NativeBuildSystem, File> externalProjectPaths = getExternalBuildExplicitPaths(config);
        if (externalProjectPaths.size() > 1) {
            return new ExternalNativeBuildProjectPathResolution(
                    null, null, null, "More than one externalNativeBuild path specified");
        }

        if (externalProjectPaths.isEmpty()) {
            // No external projects present.
            return new ExternalNativeBuildProjectPathResolution(null, null, null, null);
        }

        NativeBuildSystem buildSystem = externalProjectPaths.keySet().iterator().next();
        return new ExternalNativeBuildProjectPathResolution(
                buildSystem,
                externalProjectPaths.get(buildSystem),
                getExternalNativeBuildPath(config).get(buildSystem),
                null);
    }

    /**
     * @return a map of generate task to path from DSL. Zero entries means there are no paths in
     * the DSL. Greater than one entries means that multiple paths are specified, this is an error.
     */
    @NonNull
    private static Map<NativeBuildSystem, File> getExternalBuildExplicitPaths(
            @NonNull CoreExternalNativeBuild config) {
        Map<NativeBuildSystem, File> map = new EnumMap<>(NativeBuildSystem.class);
        File cmake = config.getCmake().getPath();
        File ndkBuild = config.getNdkBuild().getPath();

        if (cmake != null) {
            map.put(NativeBuildSystem.CMAKE, cmake);
        }
        if (ndkBuild != null) {
            map.put(NativeBuildSystem.NDK_BUILD, ndkBuild);
        }
        return map;
    }

    @NonNull
    private static Map<NativeBuildSystem, File> getExternalNativeBuildPath(
            @NonNull CoreExternalNativeBuild config) {
        Map<NativeBuildSystem, File> map = new EnumMap<>(NativeBuildSystem.class);
        File cmake = config.getCmake().getBuildStagingDirectory();
        File ndkBuild = config.getNdkBuild().getBuildStagingDirectory();
        if (cmake != null) {
            map.put(NativeBuildSystem.CMAKE, cmake);
        }
        if (ndkBuild != null) {
            map.put(NativeBuildSystem.NDK_BUILD, ndkBuild);
        }

        return map;
    }

    /**
     * Execute an external process and log the result in the case of a process exceptions.
     * Returns the info part of the log so that it can be parsed by ndk-build parser;
     * @throws BuildCommandException when the build failed.
     */
    @NonNull
    public static String executeBuildProcessAndLogError(
            @NonNull AndroidBuilder androidBuilder,
            @NonNull ProcessInfoBuilder process,
            boolean logStdioToInfo)
            throws BuildCommandException, IOException {
        ProgressiveLoggingProcessOutputHandler handler =
                new ProgressiveLoggingProcessOutputHandler(androidBuilder.getLogger(),
                        logStdioToInfo);
        try {
            // Log the command to execute but only in verbose (ie --info)
            androidBuilder.getLogger().verbose(process.toString());
            androidBuilder.executeProcess(process.createProcess(), handler)
                    .rethrowFailure().assertNormalExitValue();

            return handler.getStandardOutputString();
        } catch (ProcessException e) {
            // Also, add process output to the process exception so that it can be analyzed by
            // caller. Use combined stderr stdout instead of just stdout because compiler errors
            // go to stdout.
            String combinedMessage = String.format("%s\n%s", e.getMessage(),
                    handler.getCombinedOutputString());
            throw new BuildCommandException(combinedMessage);
        }
    }

    /**
     * A process output handler that receives STDOUT and STDERR progressively (as it is happening)
     * and logs the output line-by-line to Gradle. This class also collected precise byte-for-byte
     * output.
     */
    private static class ProgressiveLoggingProcessOutputHandler implements ProcessOutputHandler {
        @NonNull
        private final ILogger logger;
        @NonNull private final FileBackedOutputStream standardOutput;
        @NonNull private final FileBackedOutputStream combinedOutput;
        @NonNull
        private final ProgressiveLoggingProcessOutput loggingProcessOutput;
        private final boolean logStdioToInfo;

        public ProgressiveLoggingProcessOutputHandler(
                @NonNull ILogger logger, boolean logStdioToInfo) {
            this.logger = logger;
            this.logStdioToInfo = logStdioToInfo;
            standardOutput = new FileBackedOutputStream(2048);
            combinedOutput = new FileBackedOutputStream(2048);
            loggingProcessOutput = new ProgressiveLoggingProcessOutput();
        }

        @NonNull
        String getStandardOutputString() throws IOException {
            return standardOutput.asByteSource().asCharSource(Charsets.UTF_8).read();
        }

        @NonNull
        String getCombinedOutputString() throws IOException {
            return combinedOutput.asByteSource().asCharSource(Charsets.UTF_8).read();
        }

        @NonNull @Override public ProcessOutput createOutput() {
            return loggingProcessOutput;
        }

        @Override public void handleOutput(@NonNull ProcessOutput processOutput)
                throws ProcessException {
            // Nothing to do here because the process output is handled as it comes in.
        }

        private class ProgressiveLoggingProcessOutput implements ProcessOutput {
            @NonNull
            private final ProgressiveLoggingOutputStream outputStream;
            @NonNull
            private final ProgressiveLoggingOutputStream errorStream;

            ProgressiveLoggingProcessOutput() {
                outputStream = new ProgressiveLoggingOutputStream(logStdioToInfo, standardOutput);
                errorStream = new ProgressiveLoggingOutputStream(true /* logStdioToInfo */, null);
            }

            @NonNull @Override public ProgressiveLoggingOutputStream getStandardOutput() {
                return outputStream;
            }

            @NonNull @Override public ProgressiveLoggingOutputStream getErrorOutput() {
                return errorStream;
            }

            @Override public void close() throws IOException {
            }

            private class ProgressiveLoggingOutputStream extends OutputStream {
                private static final int INITIAL_BUFFER_SIZE = 256;
                @NonNull
                byte[] buffer = new byte[INITIAL_BUFFER_SIZE];
                int nextByteIndex = 0;
                private final boolean logToInfo;
                private final FileBackedOutputStream individualOutput;

                ProgressiveLoggingOutputStream(
                        boolean logToInfo, FileBackedOutputStream individualOutput) {
                    this.logToInfo = logToInfo;
                    this.individualOutput = individualOutput;
                }

                @Override public void write(int b) throws IOException {
                    combinedOutput.write(b);
                    if (individualOutput != null) {
                        individualOutput.write(b);
                    }
                    // Check for /r and /n respectively
                    if (b == 0x0A || b == 0x0D) {
                        printBuffer();
                    } else {
                        writeBuffer(b);
                    }
                }

                private void writeBuffer(int b) {
                    if (nextByteIndex == buffer.length) {
                        buffer = Arrays.copyOf(buffer, buffer.length * 2);
                    }
                    buffer[nextByteIndex] = (byte) b;
                    nextByteIndex++;
                }

                private void printBuffer() throws UnsupportedEncodingException {
                    if (nextByteIndex == 0) {
                        return;
                    }
                    String line = new String(buffer, 0, nextByteIndex, "UTF-8");
                    if (logToInfo) {
                        logger.info(line);
                    }
                    nextByteIndex = 0;
                }

                @Override public void flush() throws IOException {
                    printBuffer();
                }

                @Override public void close() throws IOException {
                    printBuffer();
                }
            }
        }
    }
}
