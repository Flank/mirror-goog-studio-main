/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.primitives.Longs;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging;
import com.google.wireless.android.sdk.stats.GradleBuildProfile;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.UUID;

public class ProfileWrapper {
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

    private final BuildbotClient client;

    public ProfileWrapper() {
        this.client = defaultBuildbotClient();
    }

    public Logging.GradleBenchmarkResult wrap(
            @NonNull GradleBuildProfile profile,
            @NonNull Logging.Benchmark benchmark,
            @NonNull Logging.BenchmarkMode benchmarkMode,
            @NonNull ProjectScenario scenario) {
        Logging.GradleBenchmarkResult.Builder result =
                Logging.GradleBenchmarkResult.newBuilder()
                        .setTimestamp(Timestamps.fromMillis(System.currentTimeMillis()))
                        .setProfile(profile)
                        .setBenchmark(benchmark)
                        .setBenchmarkMode(benchmarkMode)
                        .setFlags(
                                Logging.GradleBenchmarkResult.Flags.newBuilder(
                                        scenario.getFlags()));

        // The environment variable USE_GRADLE_NIGHTLY is somewhat misnamed,
        // It really means test against the newest version that we have checked in.
        // Those tests will be skipped if that is the same as the minimum version.
        if (!Strings.isNullOrEmpty(System.getenv("USE_GRADLE_NIGHTLY"))) {
            result.getFlagsBuilder()
                    .setGradleVersion(
                            Logging.GradleBenchmarkResult.Flags.GradleVersion.UPCOMING_GRADLE);
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
            Logging.GradleBenchmarkResult.ScheduledBuild.Builder scheduledBuild =
                    Logging.GradleBenchmarkResult.ScheduledBuild.newBuilder();
            Long buildNumber = Longs.tryParse(buildBotBuildNumber);
            if (buildNumber != null) {
                scheduledBuild.setBuildbotBuildNumber(buildNumber);

                try {
                    setCommitInfo(result, buildNumber);
                } catch (IllegalStateException | IOException e) {
                    System.out.println(e.getMessage());
                }
            }
            result.setScheduledBuild(scheduledBuild);

        } else {
            Logging.GradleBenchmarkResult.Experiment.Builder experiment =
                    Logging.GradleBenchmarkResult.Experiment.newBuilder();
            String experimentComment = System.getenv("BENCHMARK_EXPERIMENT");
            if (experimentComment != null) {
                experiment.setComment(experimentComment);
            }
            result.setExperiment(experiment);
        }

        return result.build();
    }

    /**
     * Attempts to find commit information about a result and attach it as a Commit proto.
     *
     * @throws IllegalStateException if it gets an unexpected buildbot result.
     */
    private void setCommitInfo(Logging.GradleBenchmarkResult.Builder result, long buildNumber)
            throws IOException {
        if (client == null) {
            return;
        }

        List<BuildbotClient.Change> changes = client.getChanges(buildNumber);
        if (changes == null) {
            result.setCommit(
                    Logging.GradleBenchmarkResult.Commit.newBuilder()
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
                Logging.GradleBenchmarkResult.Commit.newBuilder()
                        .setAuthor(change.who)
                        .setHash(change.revision)
                        .setLink(change.revlink)
                        .setTimestamp(timestamp)
                        .setComment(change.comments));
    }
}
