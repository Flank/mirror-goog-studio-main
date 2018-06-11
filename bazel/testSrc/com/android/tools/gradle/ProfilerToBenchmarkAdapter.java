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
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * Translates a {@link com.google.wireless.android.sdk.stats.GradleBuildProfileSpan} into a {link
 * BenchmarkLogger}
 */
public class ProfilerToBenchmarkAdapter {

    private static final Logger LOGGER =
            Logger.getLogger(ProfilerToBenchmarkAdapter.class.getName());

    private Benchmark benchmark;
    private Map<String, Metric> metrics;

    public ProfilerToBenchmarkAdapter(Benchmark benchmark) {
        this.benchmark = benchmark;
        metrics = new HashMap<>();
    }

    @SuppressWarnings("MethodMayBeStatic")
    public void adapt(GradleBuildProfile profile) {

        consolidate(profile, isTransform.negate(), (it) -> it.getTask().getType())
                .forEach(
                        (type, timings) ->
                                addMetric(GradleTaskExecutionType.forNumber(type).name(), timings));

        consolidate(profile, isTransform, (it) -> it.getTransform().getType())
                .forEach(
                        (type, timings) ->
                                addMetric(
                                        GradleTransformExecutionType.forNumber(type).name(),
                                        timings));
    }

    public void commit() {
        metrics.values().forEach(Metric::commit);
    }

    /**
     * Add a metric
     *
     * @param metricName
     * @param timing
     */
    private void addMetric(String metricName, long timing) {
        Metric metric = metrics.computeIfAbsent(metricName, Metric::new);
        long utcMs = Instant.now().toEpochMilli();
        metric.addSamples(benchmark, new Metric.MetricSample(utcMs, timing));
        LOGGER.info(metricName + " : " + timing);
    }

    /**
     * Consolidate all tasks or transforms of the same type under a single value by adding each
     * element duration together.
     *
     * @param profile the profile information
     * @param predicate predicate to filter spans from the passed profile
     * @param idProvider function to return the id to consolidate a particular task or transform
     *     type under.
     * @return a map of id to consolidated duration.
     */
    private static Map<Integer, Long> consolidate(
            GradleBuildProfile profile,
            Predicate<GradleBuildProfileSpan> predicate,
            Function<GradleBuildProfileSpan, Integer> idProvider) {
        Map<Integer, Long> consolidatedTimings = new HashMap<>();
        profile.getSpanList()
                .stream()
                .filter(predicate)
                .forEach(
                        it -> {
                            consolidatedTimings.put(
                                    idProvider.apply(it),
                                    consolidatedTimings.getOrDefault(idProvider.apply(it), 0L)
                                            + it.getDurationInMs());
                        });
        return consolidatedTimings;
    }

    private static final Predicate<GradleBuildProfileSpan> isTransform =
            gradleBuildProfileSpan ->
                    gradleBuildProfileSpan.getType()
                            == GradleBuildProfileSpan.ExecutionType.TASK_TRANSFORM;
}
