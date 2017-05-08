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
import com.android.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
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
import java.util.List;
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

    @NonNull private final Logging.Benchmark benchmark;

    @NonNull private final ProjectScenario projectScenario;

    @NonNull private final ProfileUploader uploader;

    @NonNull private final List<GradleBenchmarkResult.Builder> benchmarkResults = new ArrayList<>();

    public BenchmarkRecorder(
            @NonNull Logging.Benchmark benchmark, @NonNull ProjectScenario projectScenario) {
        this(benchmark, projectScenario, GoogleStorageProfileUploader.INSTANCE);
    }

    @VisibleForTesting
    public BenchmarkRecorder(
            @NonNull Logging.Benchmark benchmark,
            @NonNull ProjectScenario projectScenario,
            @NonNull ProfileUploader uploader) {
        this.benchmark = benchmark;
        this.projectScenario = projectScenario;
        this.uploader = uploader;
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
        Timestamp timestamp = Timestamps.fromMillis(System.currentTimeMillis());
        List<GradleBenchmarkResult> results =
                benchmarkResults
                        .stream()
                        .map(builder -> builder.setTimestamp(timestamp).build())
                        .collect(Collectors.toList());

        checkAllUploadsAreDistinct(results);

        uploader.uploadData(results);
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
