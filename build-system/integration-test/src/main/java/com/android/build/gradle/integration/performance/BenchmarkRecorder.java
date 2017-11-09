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

package com.android.build.gradle.integration.performance;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.primitives.Longs;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.GradleBenchmarkResult;
import com.google.wireless.android.sdk.stats.GradleBuildProfile;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Records and sends profiles to be uploaded profiles from a single project.
 *
 * <p>A collaborator with GradleTestProject.
 *
 * <p>When building using GradleTestProject with {@link
 *     com.android.build.gradle.integration.common.fixture.BaseGradleExecutor#recordBenchmark(Logging.BenchmarkMode)}
 *     <ol>
 *         <li>A profile is collected by the
 *             {@link com.android.build.gradle.integration.common.fixture.ProfileCapturer} and
 *             is stored in memory.</li>
 *         <li>When the test using GradleTestProject is complete {@link #doUploads()} is called,
 *             which calls the given {@link ProfileUploader#uploadData(List)}.</li>
 *     </ol>
 */
public final class BenchmarkRecorder {
    public enum UploadStrategy {
        /*
         * Instructs BenchmarkRecorder to upload all of the GradleBenchmarkResults that it records.
         */
        ALL,

        /*
         * Instructs BenchmarkRecorder to upload all only the fastest of the GradleBenchmarkResults
         * that has been recorded. Useful if you want to run a benchmark a number of times in order
         * to get a more stable result.
         */
        FASTEST
    }

    @NonNull private final Logging.Benchmark benchmark;

    @NonNull private final ProjectScenario projectScenario;

    @NonNull private final List<ProfileUploader> uploaders;

    @NonNull private final List<GradleBenchmarkResult.Builder> benchmarkResults = new ArrayList<>();

    @NonNull private final UploadStrategy uploadStrategy;

    public BenchmarkRecorder(
            @NonNull Logging.Benchmark benchmark, @NonNull ProjectScenario projectScenario) {
        this(benchmark, projectScenario, null);
    }

    @VisibleForTesting
    public BenchmarkRecorder(
            @NonNull Logging.Benchmark benchmark,
            @NonNull ProjectScenario projectScenario,
            @Nullable List<ProfileUploader> uploaders) {
        this(benchmark, projectScenario, uploaders, UploadStrategy.ALL);
    }

    public BenchmarkRecorder(
            @NonNull Logging.Benchmark benchmark,
            @NonNull ProjectScenario projectScenario,
            @Nullable List<ProfileUploader> uploaders,
            @NonNull UploadStrategy uploadStrategy) {
        this.benchmark = benchmark;
        this.projectScenario = projectScenario;
        this.uploaders = uploaders == null ? defaultUploaders() : uploaders;
        this.uploadStrategy = uploadStrategy;
    }

    private List<ProfileUploader> defaultUploaders() {
        List<ProfileUploader> uploaders = Lists.newLinkedList();
        uploaders.add(GoogleStorageProfileUploader.INSTANCE);

        try {
            uploaders.add(ActdProfileUploader.fromEnvironment());
        } catch (IllegalStateException e) {
            System.out.println("unable to create act-d profile uploader: " + e);
        }

        return uploaders;
    }

    public void recordBenchmarkResult(@NonNull GradleBenchmarkResult.Builder benchmarkResult) {

        benchmarkResult.setResultId(UUID.randomUUID().toString());

        try {
            benchmarkResult.setHostname(InetAddress.getLocalHost().getHostName());
        } catch (UnknownHostException ignored) {
        }

        String userName = System.getProperty("user.name");
        if (userName != null) {
            benchmarkResult.setUsername(userName);
        }

        String buildBotBuildNumber = System.getenv("BUILDBOT_BUILDNUMBER");
        if (buildBotBuildNumber != null) {
            GradleBenchmarkResult.ScheduledBuild.Builder scheduledBuild =
                    GradleBenchmarkResult.ScheduledBuild.newBuilder();
            Long buildNumber = Longs.tryParse(buildBotBuildNumber);
            if (buildNumber != null) {
                scheduledBuild.setBuildbotBuildNumber(buildNumber);
            }
            benchmarkResult.setScheduledBuild(scheduledBuild);
        } else {
            GradleBenchmarkResult.Experiment.Builder experiment =
                    GradleBenchmarkResult.Experiment.newBuilder();
            String experimentComment = System.getenv("BENCHMARK_EXPERIMENT");
            if (experimentComment != null) {
                experiment.setComment(experimentComment);
            }
            benchmarkResult.setExperiment(experiment);
        }

        GradleBenchmarkResult.Flags.Builder flags = GradleBenchmarkResult.Flags.newBuilder();

        flags.mergeFrom(projectScenario.getFlags());

        GradleBuildProfile profile = benchmarkResult.getProfile();

        if (profile.hasGradleVersion()) {
            if (profile.getGradleVersion().endsWith("+0000")
                    || !Strings.isNullOrEmpty(System.getenv("USE_GRADLE_NIGHTLY"))) {
                // Using nightly gradle version.
                flags.setGradleVersion(GradleBenchmarkResult.Flags.GradleVersion.UPCOMING_GRADLE);
            }
        }

        benchmarkResult.setFlags(flags);
        benchmarkResult.setBenchmark(benchmark);
        benchmarkResults.add(benchmarkResult);
    }

    public void doUploads() throws IOException {
        // If a benchmark failed or was skipped for whatever reason, there will be no results. In
        // this case, there's no uploading to do so we just break early.
        if (benchmarkResults.isEmpty()) {
            return;
        }

        Timestamp timestamp = Timestamps.fromMillis(System.currentTimeMillis());
        List<GradleBenchmarkResult> results =
                benchmarkResults
                        .stream()
                        .map(builder -> builder.setTimestamp(timestamp).build())
                        .collect(Collectors.toList());

        checkAllUploadsAreDistinct(results);

        if (uploadStrategy == UploadStrategy.FASTEST) {
            Optional<GradleBenchmarkResult> result =
                    results.stream()
                            .min(Comparator.comparingLong(r -> r.getProfile().getBuildTime()));

            Preconditions.checkArgument(result.isPresent(), "empty list of benchmarkResults found");
            results = Arrays.asList(result.get());
        }

        for (ProfileUploader uploader : uploaders) {
            uploader.uploadData(results);
        }
    }

    private static void checkAllUploadsAreDistinct(
            @NonNull List<GradleBenchmarkResult> benchmarkResults) {
        Set<GradleBenchmarkResult> benchmarkResultIds =
                benchmarkResults
                        .stream()
                        .map(
                                profile ->
                                        profile.toBuilder()
                                                .clearProfile()
                                                .clearResultId()
                                                .clearTimestamp()
                                                .build())
                        .collect(Collectors.toSet());
        if (benchmarkResultIds.size() < benchmarkResults.size()) {
            throw new IllegalStateException(
                    "Some benchmark results are not distinct!\n "
                            + "There are "
                            + benchmarkResults.size()
                            + " benchmark results, "
                            + benchmarkResultIds.size()
                            + " of which are unique.\n"
                            + Joiner.on('\n').join(benchmarkResultIds));
        }

        for (GradleBenchmarkResult benchmarkResult : benchmarkResultIds) {
            if (!PerformanceTestUtil.BENCHMARK_MODES.contains(benchmarkResult.getBenchmarkMode())) {
                throw new IllegalStateException(
                        "Cannot upload benchmark result, invalid benchmark mode "
                                + benchmarkResult.getBenchmarkMode()
                                + ".\n"
                                + "Possible modes: "
                                + Joiner.on(' ').join(PerformanceTestUtil.BENCHMARK_MODES)
                                + ".\n"
                                + "Problematic benchmark result:\n"
                                + benchmarkResult);
            }
        }
    }
}
