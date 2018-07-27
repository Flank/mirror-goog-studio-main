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
package com.android.tools.perflogger;

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.testutils.TestUtils;
import com.google.common.collect.ImmutableList;
import com.google.gson.stream.JsonWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class used for logging and outputting benchmark which can be consumed by our dashboard
 * systems. e.g. buildbots gather the output data and upload to bigstore/perfgate for regression
 * tracking and analysis.
 *
 * <p>Currently, this is expected to be used in tests run via Bazel's test runners for logging
 * performance data. The data is output in JSON format at the location as specified by the
 * TEST_UNDECLARED_OUTPUT_DIR environment variable. At the end of the test(s), the output files are
 * zipped and stored at WORKSPACE_ROOT/bazel-testlogs for external consumption. Also see
 * https://docs.bazel.build/versions/master/test-encyclopedia.html.
 *
 * <pre>
 * Usage example:
 * Metric metric = new Metric("My Test");
 * Benchmark benchmark1 = new Benchmark("Total Run Time");
 * metric.addSamples(benchmark1, new MetricSample(timestampMs1, yourTestTotalRunTime));
 * Benchmark benchmark2 = new Benchmark("Peak Memory");
 * metric.addSamples(benchmark2, new MetricSample(timestampMs1, yourTestPeakMemory));
 * metric.commit();
 *
 * This writes out the following data file:
 * {
 *  "metric":
 *  "My Test",
 *  "benchmarks": [
 *    {
 *      "benchmark": "Total Run Time",
 *      "project": "Your Project",
 *      "data": {
 *        "timestampMs1": yourTestTotalRunTime
 *      }
 *    },
 *    {
 *      "benchmark": "Peak Memory",
 *      "project": "Your Project",
 *      "data": {
 *        "timestampMs2": yourTestPeakMemory
 *      }
 *    }
 *  ]
 * }
 * </pre>
 */
public class Metric {
    private static Logger getLogger() {
        return Logger.getLogger(Metric.class.getName());
    }


    // Our perf data upload script does not permit a certain set of characters in the file names.
    private static final String INVALID_CHARACTERS = "[\\[\\]\\s]";
    private static final String REPLACEMENT_CHARACTER = "-";

    @NonNull private final String myMetricName;
    @NonNull private final File myOutputDirectory;
    @NonNull private final Map<Benchmark, List<MetricSample>> mySamples;
    @NonNull private final Map<Benchmark, ImmutableList<Analyzer>> myAnalyzers;

    /**
     * @param metricName The name to be used to associate the logged data with. e.g. the legend name
     *     for a line series on a dashboard (usually the name of a test).
     */
    public Metric(@NonNull String metricName) {
        String replacedMetricName =
                metricName.replaceAll(INVALID_CHARACTERS, REPLACEMENT_CHARACTER);
        if (!replacedMetricName.equals(metricName)) {
            getLogger()
                    .info(
                            String.format(
                                    "Metric name contains disallowed characters and have been renamed to %s",
                                    replacedMetricName));
        }

        myMetricName = replacedMetricName;
        myOutputDirectory = TestUtils.getTestOutputDir();

        // Preserve insertion order - mostly for test purposes.
        mySamples = new LinkedHashMap<>();
        myAnalyzers = new LinkedHashMap<>();
    }

    @VisibleForTesting
    @NonNull
    String getMetricName() {
        return myMetricName;
    }

    @VisibleForTesting
    @NonNull
    File getOutputDirectory() {
        return myOutputDirectory;
    }

    /**
     * @param benchmark The benchmark to add data to. This should match the name of the dashboard
     *     you want to add data to for the metric.
     * @param data Series of sample data to be added.
     */
    public void addSamples(@NonNull Benchmark benchmark, @NonNull MetricSample... data) {
        mySamples.putIfAbsent(benchmark, new ArrayList<>());
        mySamples.get(benchmark).addAll(Arrays.asList(data));
    }

    /**
     * Assigns a collection of perfgate analyzers to this metric for a given benchmark.
     *
     * @param benchmark The {@link Benchmark} to set the analyzers for.
     * @param analyzers The {@link Analyzer} Collection to be assigned to this metric for the given
     *     benchmark.
     */
    public void setAnalyzers(
            @NonNull Benchmark benchmark, @NonNull Collection<Analyzer> analyzers) {
        myAnalyzers.put(benchmark, ImmutableList.copyOf(analyzers));
    }

    /** Writes the logged benchmark data out to the test output directory. */
    public void commit() {
        if (mySamples.isEmpty()) {
            return;
        }

        try {
            File outputFile = new File(myOutputDirectory, myMetricName + ".json");
            JsonWriter writer = new JsonWriter(new FileWriter(outputFile));
            writer.setIndent("  ");
            writer.beginObject().name("metric").value(myMetricName).name("benchmarks").beginArray();
            {
                for (Map.Entry<Benchmark, List<MetricSample>> entry : mySamples.entrySet()) {
                    Benchmark benchmark = entry.getKey();
                    List<MetricSample> samples = entry.getValue();
                    if (samples.isEmpty()) {
                        continue;
                    }
                    writer.beginObject()
                            .name("benchmark")
                            .value(benchmark.getName())
                            .name("project")
                            .value(benchmark.getProject())
                            .name("data");
                    {
                        writer.beginObject();
                        for (MetricSample sample : samples) {
                            writer.name(Long.toString(sample.mySampleTimestampMs))
                                    .value(sample.mySampleData);
                        }
                        writer.endObject();
                    }
                    Collection<Analyzer> analyzers = myAnalyzers.get(benchmark);
                    if (analyzers != null && !analyzers.isEmpty()) {
                        writer.name("analyzers").beginArray();
                        {
                            for (Analyzer analyzer : analyzers) {
                                writer.beginObject();
                                for (Map.Entry<String, String> analyzerEntry :
                                        analyzer.getNameValueMap().entrySet()) {
                                    writer.name(analyzerEntry.getKey());
                                    writer.value(analyzerEntry.getValue());
                                }
                                writer.endObject();
                            }
                        }
                        writer.endArray();
                    }
                    writer.endObject();
                }
                writer.endArray();
            }
            writer.endObject();
            writer.flush();
        } catch (IOException e) {
            getLogger().log(Level.ALL, "Failed to commit", e);
        }
    }

    /** Wrapper for a time-value data pair. */
    public static class MetricSample {
        private final long mySampleTimestampMs;
        private final long mySampleData;

        public MetricSample(long timestampMs, long data) {
            mySampleTimestampMs = timestampMs;
            mySampleData = data;
        }
    }
}
