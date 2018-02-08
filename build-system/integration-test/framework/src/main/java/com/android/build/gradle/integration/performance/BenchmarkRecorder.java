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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
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

    private static List<ProfileUploader> UPLOADERS;

    @NonNull
    private static List<ProfileUploader> defaultUploaders() {
        if (UPLOADERS == null) {
            synchronized (BenchmarkRecorder.class) {
                if (UPLOADERS == null) {
                    List<ProfileUploader> uploaders = new ArrayList<>();

                    try {
                        uploaders.add(GoogleStorageProfileUploader.getInstance());
                    } catch (Exception e) {
                        System.out.println(
                                "couldn't add GoogleStorageProfileUploader to the list of default uploaders, reason: "
                                        + e
                                        + ", this means that benchmark results will not be uploaded to GCS");
                    }

                    try {
                        uploaders.add(DanaProfileUploader.fromEnvironment());
                    } catch (Exception e) {
                        System.out.println(
                                "couldn't add DanaProfileUploader to the list of default uploaders, reason: "
                                        + e
                                        + ", this means that benchmark results will not be uploaded to Dana");
                    }

                    try {
                        uploaders.add(LocalCSVProfileUploader.fromEnvironment());
                    } catch (Exception e) {
                        System.out.println(
                                "couldn't add LocalCSVProfileUploader to the list of default uploaders, reason: "
                                        + e
                                        + ", this means that benchmark results will not be saved locally as CSVs");
                    }

                    UPLOADERS = Collections.unmodifiableList(uploaders);
                }
            }
        }

        return UPLOADERS;
    }

    @Nullable
    private static BuildbotClient defaultBuildbotClient() {
        try {
            return BuildbotClient.fromEnvironment();
        } catch (IllegalArgumentException e) {
            System.out.println(
                    "unable to create a buildbot client from current environment: "
                            + e.getMessage()
                            + ", this means that your benchmark results will not have commit information attached to them");
            return null;
        }
    }

    @NonNull private final List<ProfileUploader> uploaders;

    @NonNull private final List<GradleBenchmarkResult.Builder> benchmarkResults = new ArrayList<>();

    @NonNull private final ProfileCapturer capturer;

    @Nullable private final BuildbotClient client;


    public BenchmarkRecorder(@NonNull ProfileCapturer capturer) {
        this(capturer, defaultUploaders());
    }

    public BenchmarkRecorder(
            @NonNull ProfileCapturer capturer, @NonNull List<ProfileUploader> uploaders) {
        this(capturer, uploaders, defaultBuildbotClient());
    }

    public BenchmarkRecorder(
            @NonNull ProfileCapturer capturer,
            @NonNull List<ProfileUploader> uploaders,
            @Nullable BuildbotClient client) {
        this.uploaders = uploaders;
        this.capturer = capturer;
        this.client = client;
    }

    /**
     * Records any captured benchmarks in the given runnable inside this class's state, ready to be
     * uploaded at a later time.
     *
     * @param scenario the ProjectScenario to use in the resulting benchmark result
     * @param benchmark the Benchmark to use in the resulting benchmark result
     * @param benchmarkMode the BenchmarkMode to use in the resulting benchmark result
     * @param r the runnable to capture profiles inside
     * @return the number of profiles captured
     * @throws Exception if an exception is thrown in the runnable, it gets rethrown
     */
    public int record(
            @NonNull ProjectScenario scenario,
            @NonNull Benchmark benchmark,
            @NonNull BenchmarkMode benchmarkMode,
            @NonNull ExceptionRunnable r)
            throws Exception {

        Collection<GradleBuildProfile> profiles = capturer.capture(r);

        for (GradleBuildProfile profile : profiles) {
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

                    try {
                        setCommitInfo(result, buildNumber);
                    } catch (IllegalStateException e) {
                        System.out.println(e.getMessage());
                    }
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

        return profiles.size();
    }

    public void uploadAsync() {
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

        for (ProfileUploader uploader : uploaders) {
            synchronized (BenchmarkRecorder.class) {
                OUTSTANDING_UPLOADS.add(
                        EXECUTOR.submit(
                                () -> {
                                    try {
                                        uploader.uploadData(results);
                                    } catch (IOException e) {
                                        throw new UncheckedIOException(e);
                                    }
                                }));
            }
        }
    }

    /**
     * Attempts to find commit information about a result and attach it as a Commit proto.
     *
     * @throws IllegalStateException if it gets an unexpected buildbot result.
     */
    private void setCommitInfo(GradleBenchmarkResult.Builder result, long buildNumber)
            throws IOException {
        if (client == null) {
            return;
        }

        List<BuildbotClient.Change> changes = client.getChanges(buildNumber);
        if (changes == null) {
            result.setCommit(
                    GradleBenchmarkResult.Commit.newBuilder()
                            .setAuthor("nobody")
                            .setComment("manually triggered")
                            .setHash("")
                            .setLink("")
                            .build());
            return;
        }

        /*
         * This shouldn't be possible, as getChanges() returns null when it gets an empty list of
         * changes from buildbot. It doesn't hurt to be defensive, though.
         */
        Preconditions.checkState(!changes.isEmpty(), "got an empty list of changes from buildbot");
        Preconditions.checkState(
                changes.size() == 1,
                "expected 1 change for build ID " + buildNumber + " but found " + changes.size());

        BuildbotClient.Change change = changes.get(0);
        Timestamp timestamp =
                Timestamps.fromMillis(
                        BuildbotClient.dateFromChange(change).toInstant().toEpochMilli());

        result.setCommit(
                GradleBenchmarkResult.Commit.newBuilder()
                        .setAuthor(change.who)
                        .setHash(change.revision)
                        .setLink(change.revlink)
                        .setTimestamp(timestamp)
                        .setComment(change.comments));
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
