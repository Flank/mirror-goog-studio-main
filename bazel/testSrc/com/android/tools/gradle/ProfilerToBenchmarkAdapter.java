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

package com.android.tools.gradle;

import com.android.tools.build.gradle.internal.profile.GradleTaskExecutionType;
import com.android.tools.build.gradle.internal.profile.GradleTransformExecutionType;
import com.android.tools.perflogger.Benchmark;
import com.android.tools.perflogger.Metric;
import com.google.wireless.android.sdk.stats.GradleBuildProfile;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Translates a {@link com.google.wireless.android.sdk.stats.GradleBuildProfileSpan} into a {link
 * BenchmarkLogger}
 */
public class ProfilerToBenchmarkAdapter {

    private Benchmark benchmark;
    private Map<String, Metric> metrics;

    public ProfilerToBenchmarkAdapter(Benchmark benchmark) {
        this.benchmark = benchmark;
        metrics = new HashMap<>();
    }

    public void adapt(GradleBuildProfile profile) {
        Map<Integer, Long> timingsPerTask = consolidate(profile);

        timingsPerTask.forEach(
                (type, timings) -> {
                    GradleTaskExecutionType gradleTaskExecutionType =
                            GradleTaskExecutionType.forNumber(type);
                    Metric metric =
                            metrics.computeIfAbsent(gradleTaskExecutionType.name(), Metric::new);
                    long utcMs = Instant.now().toEpochMilli();
                    metric.addSamples(benchmark, new Metric.MetricSample(utcMs, timings));
                });
    }

    public void commit() {
        metrics.values().forEach(Metric::commit);
    }

    private Map<Integer, Long> consolidate(GradleBuildProfile profile) {
        Map<Integer, Long> timingsPerTask = new HashMap<>();

        for (GradleBuildProfileSpan span : profile.getSpanList()) {
            if (span.getType() == GradleBuildProfileSpan.ExecutionType.TASK_EXECUTION) {
                int taskType = span.getTask().getType();
                if (taskType == GradleTaskExecutionType.TRANSFORM_VALUE) {
                    // find out why this type is always 0 and publish once resolved.
                    System.out.println(
                            "Transform "
                                    + GradleTransformExecutionType.forNumber(
                                            span.getTransform().getType())
                                    + "("
                                    + span.getTransform().getType()
                                    + ")"
                                    + " : "
                                    + span.getDurationInMs());
                } else {
                    timingsPerTask.put(
                            taskType,
                            timingsPerTask.getOrDefault(taskType, 0L) + span.getDurationInMs());
                }
            }
        }
        return timingsPerTask;
    }
}
