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
import com.android.build.gradle.AndroidGradleOptions;
import com.android.build.gradle.integration.performance.BenchmarkRecorder;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging;
import com.google.wireless.android.sdk.stats.AndroidStudioStats;
import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Collects build profiles from gradle builds.
 *
 * <p>If benchmark mode is set:
 *
 * <ol>
 *     <li>Adds an argument to the build to output a profile in a known location.</li>
 *     <li>At the end of the build load the profile proto from the file and package it in a
 *         benchmark proto builder, which is handed to the {@link BenchmarkRecorder}.</li>
 *     <li>The {@link BenchmarkRecorder} is then responsible for populating the benchmark fields
 *         and uploading the proto</li>
 * </ol>
 *
 * <p>Intended to be used in a try-with-resources block around a build invocation.
 */
public class ProfileCapturer implements Closeable {

    @NonNull private Path benchmarkDirectory;
    @Nullable private BenchmarkRecorder benchmarkRecorder;
    @Nullable private final Logging.BenchmarkMode benchmarkMode;

    private Path temporaryFile;

    public ProfileCapturer(
            @Nullable BenchmarkRecorder benchmarkRecorder,
            @Nullable Logging.BenchmarkMode benchmarkMode,
            @NonNull Path benchmarkDirectory) {
        this.benchmarkRecorder = benchmarkRecorder;
        this.benchmarkMode = benchmarkMode;
        this.benchmarkDirectory = benchmarkDirectory;
        if (benchmarkMode != null && benchmarkRecorder == null) {
            throw new IllegalStateException("Need to set a profile manager to record profiles");
        }
    }

    /**
     * Inject the arguments to output the profile.
     *
     * @param args the original arguments.
     * @return the arguments with the benchmark argument added if applicable.
     */
    public List<String> appendArg(@NonNull List<String> args) {
        if (benchmarkMode == null) {
            return args;
        }
        try {
            Files.createDirectories(benchmarkDirectory);
            temporaryFile =
                    Files.createTempDirectory(benchmarkDirectory, "benchmark")
                            .resolve("benchmark.rawproto");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (!Files.isDirectory(temporaryFile.getParent())) {
            throw new RuntimeException(
                    "Profile directory " + temporaryFile.getParent() + "should have been created.");
        }
        if (Files.exists(temporaryFile)) {
            throw new RuntimeException(
                    "Profile file " + temporaryFile + " should not have been created.");
        }

        return ImmutableList.<String>builder()
                .addAll(args)
                .add(
                        "-P"
                                + AndroidGradleOptions.PROPERTY_BENCHMARK_PROFILE_FILE
                                + "="
                                + temporaryFile.toString())
                .build();
    }

    @Override
    public void close() throws IOException {
        if (benchmarkMode == null) {
            return;
        }
        Preconditions.checkNotNull(benchmarkRecorder);
        if (temporaryFile == null) {
            throw new IllegalStateException("appendArg must be called");
        }
        if (!Files.isRegularFile(temporaryFile)) {
            throw new RuntimeException(
                    "Profile infrastructure failure: "
                            + "Profile "
                            + temporaryFile
                            + " should have been written.");
        }

        AndroidStudioStats.GradleBuildProfile profile =
                AndroidStudioStats.GradleBuildProfile.parseFrom(Files.readAllBytes(temporaryFile));

        Logging.GradleBenchmarkResult.Builder benchmarkResult =
                Logging.GradleBenchmarkResult.newBuilder()
                        .setProfile(profile)
                        .setBenchmarkMode(benchmarkMode);

        benchmarkRecorder.recordBenchmarkResult(benchmarkResult);
    }
}
