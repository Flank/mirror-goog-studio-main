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

import com.android.annotations.NonNull;
import com.android.tools.build.gradle.internal.profile.GradleTaskExecutionType;
import com.android.tools.build.gradle.internal.profile.GradleTransformExecutionType;
import com.android.tools.perflogger.Benchmark;
import com.android.tools.perflogger.Metric;
import com.google.wireless.android.sdk.stats.GradleBuildProfile;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * Translates a {@link com.google.wireless.android.sdk.stats.GradleBuildProfileSpan} into a {link
 * Metric} map.
 */
public class ProfilerToBenchmarkAdapter {

    private static final Logger LOGGER =
            Logger.getLogger(ProfilerToBenchmarkAdapter.class.getName());

    @NonNull private final Benchmark benchmark;
    @NonNull private final BenchmarkRun benchmarkRun;
    @NonNull private final Map<String, Metric> metrics;

    @NonNull
    private final List<ConsolidatedRunTimings> consolidatedTimingsPerIterations = new ArrayList<>();

    public ProfilerToBenchmarkAdapter(
            @NonNull Benchmark benchmark, @NonNull BenchmarkRun benchmarkRun) {
        this.benchmark = benchmark;
        this.benchmarkRun = benchmarkRun;
        metrics = new HashMap<>();
    }

    @SuppressWarnings("MethodMayBeStatic")
    public void adapt(long iterationStartTime, @NonNull GradleBuildProfile profile) {

        consolidatedTimingsPerIterations.add(
                new ConsolidatedRunTimings(
                        iterationStartTime,
                        profile.getBuildTime(),
                        consolidate(profile, isTask, (it) -> it.getTask().getType()),
                        consolidate(profile, isTransform, (it) -> it.getTransform().getType())));
    }

    public void commit() {

        final int upperLimit =
                benchmarkRun.iterations
                        - (benchmarkRun.removeUpperOutliers + benchmarkRun.removeLowerOutliers);
        if (upperLimit < benchmarkRun.removeLowerOutliers || benchmarkRun.removeLowerOutliers < 0) {
            throw new RuntimeException(
                    String.format(
                            "Invalid upper (%d) and/or lower (%d) outliers removal settings",
                            benchmarkRun.removeLowerOutliers, benchmarkRun.removeUpperOutliers));
        }
        Metric totalBuildTime = new Metric("TOTAL_BUILD_TIME");

        consolidatedTimingsPerIterations
                .stream()
                .sorted(Comparator.comparingLong(ConsolidatedRunTimings::getBuildTime))
                .skip(benchmarkRun.removeLowerOutliers)
                .limit(
                        benchmarkRun.iterations
                                - (benchmarkRun.removeUpperOutliers
                                        + benchmarkRun.removeLowerOutliers))
                .forEach(
                        consolidatedRunTimings -> {
                            totalBuildTime.addSamples(
                                    benchmark,
                                    new Metric.MetricSample(
                                            consolidatedRunTimings.startTime,
                                            consolidatedRunTimings.buildTime));

                            consolidatedRunTimings.timingsForTasks.forEach(
                                    (type, timing) ->
                                            addMetricSample(
                                                    GradleTaskExecutionType.forNumber(type).name(),
                                                    consolidatedRunTimings.startTime,
                                                    timing));
                            consolidatedRunTimings.timingsForTransforms.forEach(
                                    (type, timing) ->
                                            addMetricSample(
                                                    GradleTransformExecutionType.forNumber(type)
                                                            .name(),
                                                    consolidatedRunTimings.startTime,
                                                    timing));
                        });
        metrics.values().forEach(Metric::commit);
        totalBuildTime.commit();
    }

    /**
     * Add a metric sample
     *
     * @param metricName the metric name
     * @param utcMs the universal time
     * @param timing the duration of the metric for this metric sample
     */
    private void addMetricSample(String metricName, long utcMs, long timing) {
        Metric metric = metrics.computeIfAbsent(metricName, Metric::new);
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
                        it ->
                                consolidatedTimings.put(
                                        idProvider.apply(it),
                                        consolidatedTimings.getOrDefault(idProvider.apply(it), 0L)
                                                + it.getDurationInMs()));
        return consolidatedTimings;
    }

    private static final Predicate<GradleBuildProfileSpan> isTransform =
            gradleBuildProfileSpan ->
                    gradleBuildProfileSpan.getType()
                            == GradleBuildProfileSpan.ExecutionType.TASK_TRANSFORM;

    private static final Predicate<GradleBuildProfileSpan> isTask =
            // ignore task type 65 because those are transform tasks, which are accounted for in the
            // transform spans.
            gradleBuildProfileSpan ->
                    gradleBuildProfileSpan.getType()
                                    == GradleBuildProfileSpan.ExecutionType.TASK_EXECUTION
                            && gradleBuildProfileSpan.getTask() != null
                            && gradleBuildProfileSpan.getTask().getType() != 65;

    private static final class ConsolidatedRunTimings {
        final long startTime;
        final long buildTime;
        final Map<Integer, Long> timingsForTasks;
        final Map<Integer, Long> timingsForTransforms;

        private ConsolidatedRunTimings(
                long startTime,
                long buildTime,
                Map<Integer, Long> timingsForTasks,
                Map<Integer, Long> timingsForTransforms) {
            this.startTime = startTime;
            this.buildTime = buildTime;
            this.timingsForTasks = timingsForTasks;
            this.timingsForTransforms = timingsForTransforms;
        }

        long getBuildTime() {
            return buildTime;
        }
    }
}
