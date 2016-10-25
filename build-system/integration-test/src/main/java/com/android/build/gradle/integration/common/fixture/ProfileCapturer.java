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
import com.android.build.gradle.integration.performance.BenchmarkRecorder;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging;
import com.google.wireless.android.sdk.stats.GradleBuildProfile;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

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

    @NonNull private final Path profileDirectory;
    @Nullable private final BenchmarkRecorder benchmarkRecorder;
    @Nullable private final Logging.BenchmarkMode benchmarkMode;
    @NonNull private final ImmutableSet<Path> existingProfiles;

    public ProfileCapturer(
            @Nullable BenchmarkRecorder benchmarkRecorder,
            @Nullable Logging.BenchmarkMode benchmarkMode,
            @NonNull Path profileDirectory)
            throws IOException {
        this.benchmarkRecorder = benchmarkRecorder;
        this.benchmarkMode = benchmarkMode;
        this.profileDirectory = profileDirectory;
        if (benchmarkMode != null && benchmarkRecorder == null) {
            throw new IllegalStateException("Need to set a profile manager to record profiles");
        }
        if (benchmarkMode != null) {
            this.existingProfiles = getProfiles();
        } else {
            this.existingProfiles = ImmutableSet.of();
        }

    }

    @Override
    public void close() throws IOException {
        if (benchmarkMode == null) {
            return;
        }
        Preconditions.checkNotNull(benchmarkRecorder);

        Set<Path> newProfiles = Sets.difference(getProfiles(), existingProfiles);

        if (newProfiles.size() != 1) {
            throw new IllegalStateException(
                    "Expected a profile to be written to " + profileDirectory);
        }

        GradleBuildProfile profile =
                GradleBuildProfile.parseFrom(
                        Files.readAllBytes(Iterables.getOnlyElement(newProfiles)));

        Logging.GradleBenchmarkResult.Builder benchmarkResult =
                Logging.GradleBenchmarkResult.newBuilder()
                        .setProfile(profile)
                        .setBenchmarkMode(benchmarkMode);

        benchmarkRecorder.recordBenchmarkResult(benchmarkResult);
    }


    private ImmutableSet<Path> getProfiles() throws IOException {
        if (!Files.exists(profileDirectory)) {
            return ImmutableSet.of();
        }
        return ImmutableSet.copyOf(
                Files.walk(profileDirectory)
                        .filter(Files::isRegularFile)
                        .collect(Collectors.toSet()));
    }
}
