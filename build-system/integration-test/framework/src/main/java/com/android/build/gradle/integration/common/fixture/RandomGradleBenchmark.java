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
import com.google.wireless.android.sdk.stats.GradleBuildProfile;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan;
import com.google.wireless.android.sdk.stats.GradleBuildProject;
import com.google.wireless.android.sdk.stats.GradleBuildVariant;
import com.google.wireless.android.sdk.stats.GradleTaskExecution;
import com.google.wireless.android.sdk.stats.GradleTransformExecution;
import java.util.List;
import java.util.Random;

public final class RandomGradleBenchmark {
    public static List<Logging.GradleBenchmarkResult> randomBenchmarkResults() {
        return randomBenchmarkResults(randomNonzeroLong());
    }

    public static Logging.GradleBenchmarkResult randomBenchmarkResult() {
        return randomBenchmarkResult(randomNonzeroLong());
    }

    private static List<Logging.GradleBenchmarkResult> randomBenchmarkResults(long buildId) {
        int count = randomInt(5, 15);
        List<Logging.GradleBenchmarkResult> results = Lists.newArrayListWithCapacity(count);
        for (int i = 0; i < count; i++) {
            results.add(randomBenchmarkResult(buildId));
        }
        return results;
    }

    private static Logging.GradleBenchmarkResult randomBenchmarkResult(long buildId) {
        Logging.GradleBenchmarkResult.Flags flags =
                Logging.GradleBenchmarkResult.Flags.newBuilder()
                        .setAapt(random(Logging.GradleBenchmarkResult.Flags.Aapt.values()))
                        .setBranch(random(Logging.GradleBenchmarkResult.Flags.Branch.values()))
                        .setCompiler(random(Logging.GradleBenchmarkResult.Flags.Compiler.values()))
                        .setJacoco(random(Logging.GradleBenchmarkResult.Flags.Jacoco.values()))
                        .setMinification(
                                random(Logging.GradleBenchmarkResult.Flags.Minification.values()))
                        .build();

        Logging.GradleBenchmarkResult.ScheduledBuild.Builder scheduledBuild =
                Logging.GradleBenchmarkResult.ScheduledBuild.newBuilder()
                        .setBuildbotBuildNumber(buildId);

        GradleBuildProfile.Builder profile =
                GradleBuildProfile.newBuilder().setBuildTime(randomDuration());

        int projectCount = randomInt(2, 5);
        int variantCount = randomInt(4, 10);
        int spanCount = randomInt(10, 11);

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
                            .setProject(randomInt(0, projectCount - 1))
                            .setVariant(randomInt(0, variantCount - 1))
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

        return Logging.GradleBenchmarkResult.newBuilder()
                .setBenchmarkMode(random(Logging.BenchmarkMode.values()))
                .setBenchmark(random(Logging.Benchmark.values()))
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
            t = array[new Random().nextInt(array.length)];
            try {
                t.getNumber();
                return t;
            } catch (IllegalArgumentException e) {
                // it's not possible to call .getNumber() on the UNRECOGNIZED enum element, doing so
                // causes an IllegalArgumentException. We do nothing in this catch block in order
                // to loop again and grab a different random enum element.
            }
        }
    }

    private static long randomDuration() {
        long value = Math.abs(new Random().nextLong());
        if (value == Long.MIN_VALUE) {
            return Long.MAX_VALUE;
        }

        // to make sure samples aren't filtered out, we add the threshold if it's below it
        if (value < ActdProfileUploader.BENCHMARK_VALUE_THRESHOLD_MILLIS) {
            value += ActdProfileUploader.BENCHMARK_VALUE_THRESHOLD_MILLIS;
        }

        return value;
    }

    private static long randomNonzeroLong() {
        long value = Math.abs(new Random().nextLong());
        if (value == Long.MIN_VALUE) {
            return Long.MAX_VALUE;
        }
        return value + 1;
    }

    private static int randomInt(int min, int max) {
        return new Random().nextInt(max - min + 1) + min;
    }
}
