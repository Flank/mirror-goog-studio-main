/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.android.build.gradle.integration.performance.ActdProfileUploader;
import com.android.tools.build.gradle.internal.profile.GradleTaskExecutionType;
import com.android.tools.build.gradle.internal.profile.GradleTransformExecutionType;
import com.google.common.collect.Lists;
import com.google.protobuf.ProtocolMessageEnum;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.Benchmark;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.BenchmarkMode;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.GradleBenchmarkResult;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.GradleBenchmarkResult.Flags;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.GradleBenchmarkResult.ScheduledBuild;
import com.google.wireless.android.sdk.stats.GradleBuildProfile;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan;
import com.google.wireless.android.sdk.stats.GradleBuildProject;
import com.google.wireless.android.sdk.stats.GradleBuildVariant;
import com.google.wireless.android.sdk.stats.GradleTaskExecution;
import com.google.wireless.android.sdk.stats.GradleTransformExecution;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class RandomGradleBenchmark {
    @NonNull private static final ThreadLocalRandom RAND = ThreadLocalRandom.current();

    public static List<Logging.GradleBenchmarkResult> randomBenchmarkResults() {
        return randomBenchmarkResults(RAND.nextLong(1, Long.MAX_VALUE));
    }

    public static Logging.GradleBenchmarkResult randomBenchmarkResult() {
        return randomBenchmarkResult(RAND.nextLong(1, Long.MAX_VALUE));
    }

    private static List<Logging.GradleBenchmarkResult> randomBenchmarkResults(long buildId) {
        int count = RAND.nextInt(5, 15);
        List<Logging.GradleBenchmarkResult> results = Lists.newArrayListWithCapacity(count);
        for (int i = 0; i < count; i++) {
            results.add(randomBenchmarkResult(buildId));
        }
        return results;
    }

    private static Logging.GradleBenchmarkResult randomBenchmarkResult(long buildId) {
        Flags.Builder flags =
                Flags.newBuilder()
                        .setAapt(random(Flags.Aapt.values()))
                        .setBranch(random(Flags.Branch.values()))
                        .setCompiler(random(Flags.Compiler.values()))
                        .setJacoco(random(Flags.Jacoco.values()))
                        .setMinification(random(Flags.Minification.values()));

        GradleBuildProfile.Builder profile =
                GradleBuildProfile.newBuilder().setBuildTime(randomDuration());

        ScheduledBuild.Builder scheduledBuild =
                GradleBenchmarkResult.ScheduledBuild.newBuilder().setBuildbotBuildNumber(buildId);

        int projectCount = RAND.nextInt(2, 5);
        int variantCount = RAND.nextInt(4, 10);
        int spanCount = RAND.nextInt(10, 11);

        for (int i = 0; i < projectCount; i++) {
            GradleBuildProject.Builder project =
                    GradleBuildProject.newBuilder()
                            .setId(i)
                            .setAndroidPlugin(random(GradleBuildProject.PluginType.values()));
            for (int j = 0; j < variantCount; j++) {
                project.addVariant(
                        GradleBuildVariant.newBuilder()
                                .setId(j)
                                .setVariantType(random(GradleBuildVariant.VariantType.values())));
            }
            profile.addProject(project);
        }

        for (int i = 0; i < spanCount; i++) {
            GradleBuildProfileSpan.Builder span =
                    GradleBuildProfileSpan.newBuilder()
                            .setProject(RAND.nextInt(0, projectCount - 1))
                            .setVariant(RAND.nextInt(0, variantCount - 1))
                            .setDurationInMs(randomDuration())
                            .setType(random(GradleBuildProfileSpan.ExecutionType.values()));

            switch (span.getType()) {
                case TASK_TRANSFORM:
                case TASK_TRANSFORM_PREPARATION:
                    GradleTransformExecution.Builder transform =
                            GradleTransformExecution.newBuilder()
                                    .setType(
                                            random(GradleTransformExecutionType.values())
                                                    .getNumber());
                    span.setTransform(transform);
                    break;
                case TASK_EXECUTION:
                    GradleTaskExecution.Builder task =
                            GradleTaskExecution.newBuilder()
                                    .setType(random(GradleTaskExecutionType.values()).getNumber());
                    span.setTask(task);
                    break;
                default:
            }

            profile.addSpan(span);
        }

        return GradleBenchmarkResult.newBuilder()
                .setBenchmarkMode(random(BenchmarkMode.values()))
                .setBenchmark(random(Benchmark.values()))
                .setFlags(flags)
                .setProfile(profile)
                .setScheduledBuild(scheduledBuild)
                .build();
    }

    /**
     * Gets a random enum value for a given array of proto enums.
     *
     * <p>Example:
     *
     * <pre>
     *     BenchmarkMode bm = random(BenchmarkMode.values());
     * </pre>
     */
    @NonNull
    private static <T extends ProtocolMessageEnum> T random(@NonNull T[] array) {
        T t;
        while (true) {
            t = array[RAND.nextInt(array.length)];
            try {
                if (t.getNumber() == -1) {
                    continue;
                }
                return t;
            } catch (IllegalArgumentException e) {
                // it's not possible to call .getNumber() on the UNRECOGNIZED enum element, doing so
                // causes an IllegalArgumentException. We do nothing in this catch block in order
                // to loop again and grab a different random enum element.
            }
        }
    }

    private static long randomDuration() {
        long value = RAND.nextLong(1, 10000);

        // To make sure samples aren't filtered out, we add the lower threshold if our value is
        // below it. This is something of an implementation detail, but if the implementation is
        // ever removed, this code should fail to compile. I'm okay with that.
        if (value < ActdProfileUploader.BENCHMARK_VALUE_THRESHOLD_MILLIS) {
            value += ActdProfileUploader.BENCHMARK_VALUE_THRESHOLD_MILLIS;
        }

        return value;
    }
}
