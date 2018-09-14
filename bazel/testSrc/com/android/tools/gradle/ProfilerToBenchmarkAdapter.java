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
import com.android.tools.perflogger.Analyzer;
import com.android.tools.perflogger.Benchmark;
import com.android.tools.perflogger.MedianWindowDeviationAnalyzer;
import com.android.tools.perflogger.Metric;
import com.google.wireless.android.sdk.stats.GarbageCollectionStats;
import com.google.wireless.android.sdk.stats.GradleBuildMemorySample;
import com.google.wireless.android.sdk.stats.GradleBuildProfile;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan.ExecutionType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
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

    private static final EnumSet<ExecutionType> CONFIGURATION_TYPES =
            EnumSet.of(
                    ExecutionType.BASE_PLUGIN_PROJECT_CONFIGURE,
                    ExecutionType.BASE_PLUGIN_PROJECT_BASE_EXTENSION_CREATION,
                    ExecutionType.BASE_PLUGIN_PROJECT_TASKS_CREATION,
                    ExecutionType.TASK_MANAGER_CREATE_TASKS,
                    ExecutionType.BASE_PLUGIN_CREATE_ANDROID_TASKS);

    @NonNull private final Benchmark benchmark;
    @NonNull private final BenchmarkRun benchmarkRun;
    @NonNull private final Map<String, Metric> metrics;

    @NonNull
    private static final Analyzer DEFAULT_ANALYZER =
            new MedianWindowDeviationAnalyzer.Builder()
                    .setMetricAggregate(Analyzer.MetricAggregate.MEDIAN)
                    .setRunInfoQueryLimit(50)
                    .setRecentWindowSize(25)
                    // constant term of 10.0 ms to ignore regressions in trivial tasks
                    .setConstTerm(10.0)
                    // recommended value
                    .setMadCoeff(1.0)
                    // flag 10% regressions
                    .setMedianCoeff(0.10)
                    .build();

    @NonNull
    private static final Analyzer MIN_ANALYZER =
            new MedianWindowDeviationAnalyzer.Builder()
                    .setMetricAggregate(Analyzer.MetricAggregate.MIN)
                    .setRunInfoQueryLimit(50)
                    .setRecentWindowSize(25)
                    // constant term of 10.0 ms to ignore regressions in trivial tasks
                    .setConstTerm(10.0)
                    // recommended value
                    .setMadCoeff(1.0)
                    // flag 10% regressions
                    .setMedianCoeff(0.10)
                    .build();

    @NonNull
    private static final List<String> MIN_ANALYZER_METRICS =
            Arrays.asList("DEX_MERGER", "EXTERNAL_LIBS_MERGER");

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
                        getGcTime(profile),
                        consolidate(profile, isTask, (it) -> it.getTask().getType()),
                        consolidate(profile, isTransform, (it) -> it.getTransform().getType()),
                        consolidate(profile, isConfiguration, (it) -> it.getType().getNumber())));
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
        Metric totalGcTime = new Metric("TOTAL_GC_TIME");
        Metric totalBuildTimeNoGc = new Metric("TOTAL_BUILD_TIME_NO_GC");

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
                            totalGcTime.addSamples(
                                    benchmark,
                                    new Metric.MetricSample(
                                            consolidatedRunTimings.startTime,
                                            consolidatedRunTimings.gcTime));
                            totalBuildTimeNoGc.addSamples(
                                    benchmark,
                                    new Metric.MetricSample(
                                            consolidatedRunTimings.startTime,
                                            consolidatedRunTimings.buildTime
                                                    - consolidatedRunTimings.gcTime));
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
                            consolidatedRunTimings.timingsForConfiguration.forEach(
                                    (type, timing) ->
                                            addMetricSample(
                                                    ExecutionType.forNumber(type).name(),
                                                    consolidatedRunTimings.startTime,
                                                    timing));
                        });
        metrics.values().forEach(it -> setAnalyzers(it, benchmark));
        metrics.values().forEach(Metric::commit);
        totalBuildTime.commit();
        totalGcTime.commit();
        totalBuildTimeNoGc.commit();
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
     * Set the analyzers for the metric
     *
     * @param metric the metric whose analyzers are set
     * @param benchmark the benchmark for which the metric's analyzers are set
     */
    private static void setAnalyzers(Metric metric, Benchmark benchmark) {
        if (MIN_ANALYZER_METRICS.contains(metric.getMetricName())) {
            metric.setAnalyzers(benchmark, Arrays.asList(MIN_ANALYZER));
        } else {
            metric.setAnalyzers(benchmark, Arrays.asList(DEFAULT_ANALYZER));
        }
    }


    /**
     * Returns the total garbage collection time from a gradle build, in milliseconds.
     *
     * @param profile the profile information
     * @return the total garbage collection time from a gradle build, in milliseconds.
     */
    private static long getGcTime(GradleBuildProfile profile) {
        long gcTime = 0;
        for (GradleBuildMemorySample memorySample : profile.getMemorySampleList()) {
            for (GarbageCollectionStats gcStats :
                    memorySample.getJavaProcessStats().getGarbageCollectionStatsList()) {
                if (gcStats.getGcTime() > 0) {
                    gcTime += gcStats.getGcTime();
                }
            }
        }
        return gcTime;
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
                    gradleBuildProfileSpan.getType() == ExecutionType.TASK_TRANSFORM;

    private static final Predicate<GradleBuildProfileSpan> isTask =
            // ignore task type 65 because those are transform tasks, which are accounted for in the
            // transform spans.
            gradleBuildProfileSpan ->
                    gradleBuildProfileSpan.getType() == ExecutionType.TASK_EXECUTION
                            && gradleBuildProfileSpan.getTask() != null
                            && gradleBuildProfileSpan.getTask().getType() != 65;

    private static final Predicate<GradleBuildProfileSpan> isConfiguration =
            gradleBuildProfileSpan ->
                    CONFIGURATION_TYPES.contains(gradleBuildProfileSpan.getType());

    private static final class ConsolidatedRunTimings {
        final long startTime;
        final long buildTime;
        final long gcTime;
        final Map<Integer, Long> timingsForTasks;
        final Map<Integer, Long> timingsForTransforms;
        final Map<Integer, Long> timingsForConfiguration;

        private ConsolidatedRunTimings(
                long startTime,
                long buildTime,
                long gcTime,
                Map<Integer, Long> timingsForTasks,
                Map<Integer, Long> timingsForTransforms,
                Map<Integer, Long> timingsForConfiguration) {
            this.startTime = startTime;
            this.buildTime = buildTime;
            this.gcTime = gcTime;
            this.timingsForTasks = timingsForTasks;
            this.timingsForTransforms = timingsForTransforms;
            this.timingsForConfiguration = timingsForConfiguration;
        }

        long getBuildTime() {
            return buildTime;
        }
    }
}
