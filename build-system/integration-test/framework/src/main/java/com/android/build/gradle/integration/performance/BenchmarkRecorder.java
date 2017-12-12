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
import com.android.build.gradle.integration.common.fixture.ProfileCapturer;
import com.android.builder.utils.ExceptionRunnable;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.primitives.Longs;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.Benchmark;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.BenchmarkMode;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.GradleBenchmarkResult;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.GradleBenchmarkResult.Flags;
import com.google.wireless.android.sdk.stats.GradleBuildProfile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import javax.annotation.concurrent.NotThreadSafe;

/** */
@NotThreadSafe
public final class BenchmarkRecorder {
    public enum UploadStrategy {
        /*
         * Instructs BenchmarkRecorder to upload all of the GradleBenchmarkResults that it records.
         */
        ALL {
            @Override
            public List<GradleBenchmarkResult> filter(List<GradleBenchmarkResult> results) {
                return results;
            }
        },

        /*
         * Instructs BenchmarkRecorder to upload all only the fastest of the GradleBenchmarkResults
         * that has been recorded. Useful if you want to run a benchmark a number of times in order
         * to get a more stable result.
         */
        FASTEST {
            @Override
            public List<GradleBenchmarkResult> filter(List<GradleBenchmarkResult> results) {
                Optional<GradleBenchmarkResult> result =
                        results.stream()
                                .min(Comparator.comparingLong(r -> r.getProfile().getBuildTime()));

                Preconditions.checkArgument(
                        result.isPresent(), "empty list of benchmarkResults found");
                return Arrays.asList(result.get());
            }
        };

        public abstract List<GradleBenchmarkResult> filter(List<GradleBenchmarkResult> results);
    }

    /** Variables for asynchronously uploading profiles. */
    private static final int WORK_QUEUE_SIZE = 64;

    private static final int NUM_THREADS = 3;

    @NonNull
    private static final ExecutorService EXECUTOR =
            new ThreadPoolExecutor(
                    NUM_THREADS,
                    NUM_THREADS,
                    0L,
                    TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(WORK_QUEUE_SIZE));

    @NonNull
    private static final List<Future<?>> OUTSTANDING_UPLOADS = new ArrayList<>(WORK_QUEUE_SIZE);

    @NonNull private final List<ProfileUploader> uploaders;

    @NonNull private final List<GradleBenchmarkResult.Builder> benchmarkResults = new ArrayList<>();

    @NonNull private final ProfileCapturer capturer;

    private static List<ProfileUploader> defaultUploaders() {
        List<ProfileUploader> uploaders =
                Arrays.asList(
                        GoogleStorageProfileUploader.INSTANCE,
                        ActdProfileUploader.fromEnvironment(),
                        LocalCSVProfileUploader.fromEnvironment());

        return Collections.unmodifiableList(uploaders);
    }

    public BenchmarkRecorder(@NonNull ProfileCapturer capturer) {
        this(capturer, defaultUploaders());

    }

    public BenchmarkRecorder(
            @NonNull ProfileCapturer capturer, @NonNull List<ProfileUploader> uploaders) {
        this.uploaders = uploaders;
        this.capturer = capturer;
    }

    public void record(
            @NonNull ProjectScenario scenario,
            @NonNull Benchmark benchmark,
            @NonNull BenchmarkMode benchmarkMode,
            @NonNull ExceptionRunnable r)
            throws Exception {

        for (GradleBuildProfile profile : capturer.capture(r)) {
            GradleBenchmarkResult.Builder result =
                    GradleBenchmarkResult.newBuilder()
                            .setProfile(profile)
                            .setBenchmark(benchmark)
                            .setBenchmarkMode(benchmarkMode)
                            .setFlags(Flags.newBuilder(scenario.getFlags()));

            // The environment variable USE_GRADLE_NIGHTLY is somewhat misnamed,
            // It really means test against the newest version that we have checked in.
            // Those tests will be skipped if that is the same as the minimum version.
            if (!Strings.isNullOrEmpty(System.getenv("USE_GRADLE_NIGHTLY"))) {
                result.getFlagsBuilder()
                        .setGradleVersion(
                                GradleBenchmarkResult.Flags.GradleVersion.UPCOMING_GRADLE);
            }

            result.setResultId(UUID.randomUUID().toString());

            try {
                result.setHostname(InetAddress.getLocalHost().getHostName());
            } catch (UnknownHostException ignored) {
            }

            String userName = System.getProperty("user.name");
            if (userName != null) {
                result.setUsername(userName);
            }

            String buildBotBuildNumber = System.getenv("BUILDBOT_BUILDNUMBER");
            if (buildBotBuildNumber != null) {
                GradleBenchmarkResult.ScheduledBuild.Builder scheduledBuild =
                        GradleBenchmarkResult.ScheduledBuild.newBuilder();
                Long buildNumber = Longs.tryParse(buildBotBuildNumber);
                if (buildNumber != null) {
                    scheduledBuild.setBuildbotBuildNumber(buildNumber);
                }
                result.setScheduledBuild(scheduledBuild);
            } else {
                GradleBenchmarkResult.Experiment.Builder experiment =
                        GradleBenchmarkResult.Experiment.newBuilder();
                String experimentComment = System.getenv("BENCHMARK_EXPERIMENT");
                if (experimentComment != null) {
                    experiment.setComment(experimentComment);
                }
                result.setExperiment(experiment);
            }

            benchmarkResults.add(result);
        }
    }

    public void uploadAsync() {
        uploadAsync(UploadStrategy.ALL);
    }

    public void uploadAsync(UploadStrategy strategy) {
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

        benchmarkResults.clear();

        validateResults(results);

        List<GradleBenchmarkResult> filteredResults = strategy.filter(results);
        Preconditions.checkArgument(!results.isEmpty(), "UploadStrategy returned no results");

        for (ProfileUploader uploader : uploaders) {
            synchronized (BenchmarkRecorder.class) {
                OUTSTANDING_UPLOADS.add(
                        EXECUTOR.submit(
                                () -> {
                                    try {
                                        uploader.uploadData(filteredResults);
                                    } catch (IOException e) {
                                        throw new UncheckedIOException(e);
                                    }
                                }));
            }
        }
    }

    public static void awaitUploads(long timeout, @NonNull TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        synchronized (BenchmarkRecorder.class) {
            long start = System.nanoTime();
            for (Future<?> future : OUTSTANDING_UPLOADS) {
                future.get(timeout, unit);
            }
            OUTSTANDING_UPLOADS.clear();
            Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
            System.out.println("[BenchmarkRecorder]: spent " + elapsed + " waiting for uploads");
        }
    }

    private static void validateResults(@NonNull List<GradleBenchmarkResult> benchmarkResults) {
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
