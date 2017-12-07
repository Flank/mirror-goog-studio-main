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

import com.android.testutils.TestUtils;
import com.google.gson.stream.JsonWriter;
import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

/**
 * Utility class used for logging and outputing benchmark which can be consumed by our dashboard
 * systems. e.g. buildbots gather the output data and upload to bigstore/perfgate for regression
 * tracking and analysis.
 *
 * Currently, this is expected to be used in tests run via Bazel's test runners for logging
 * performance data. The data is output in JSON format at the location as specified by the
 * TEST_UNDECLARED_OUTPUT_DIR environment variable. At the end of the test(s), the output files are
 * zipped and stored at WORKSPACE_ROOT/bazel-testlogs for external consumption. Also see
 * https://docs.bazel.build/versions/master/test-encyclopedia.html.
 *
 * Usage example:
 * BenchmarkLogger logger = new BenchmarkLogger("My Test");
 * Benchmark benchmark1 = new Benchmark("Total Run Time");
 * logger.addSamples(benchmark1, new MetricSample(timestampMs1, yourTestTotalRunTime));
 * Benchmark benchmark2 = new Benchmark("Peak Memory");
 * logger.addSamples(benchmark2, new MetricSample(timestampMs1, yourTestPeakMemory));
 * logger.commit();
 *
 * This writes out the following data file:
 * {
 *  "metric":
 *  "My Test",
 *  "benchmarks": [
 *    {
 *      "benchmark": "Total Run Time",
 *      "data": {
 *        "timestampMs1": yourTestTotalRunTime
 *      }
 *    },
 *    {
 *      "benchmark": "Peak Memory",
 *      "data": {
 *        "timestampMs2": yourTestPeakMemory
 *      }
 *    }
 *  ]
 * }
 */
public class BenchmarkLogger {
    private static Logger getLogger() {
        return Logger.getInstance(BenchmarkLogger.class);
    }

    @NotNull private final String myMetricName;
    @NotNull private final File myOutputDirectory;
    @NotNull private final Map<Benchmark, List<MetricSample>> mySamples;

    /**
     * @param metricName The name to be used to associate the logged data with. e.g. the legend name
     *     for a line series on a dashboard (usually the name of a test).
     */
    public BenchmarkLogger(@NotNull String metricName) {
        myMetricName = metricName;
        myOutputDirectory = TestUtils.getTestOutputDir();

        // Preserve insertion order - mostly for test purposes.
        mySamples = new LinkedHashMap<>();
    }

    @TestOnly
    @NotNull
    File getOutputDirectory() {
        return myOutputDirectory;
    }

    /**
     * @param benchmark The benchmark to add data to. This should match the name of the dashboard
     *     you want to add data to for the metric.
     * @param data Series of sample data to be added.
     */
    public void addSamples(@NotNull Benchmark benchmark, @NotNull MetricSample... data) {
        mySamples.putIfAbsent(benchmark, new ArrayList<>());
        mySamples.get(benchmark).addAll(Arrays.asList(data));
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
                            .value(benchmark.myBenchmarkName)
                            .name("data");

                    {
                        writer.beginObject();
                        for (MetricSample sample : samples) {
                            writer.name(Long.toString(sample.mySampleTimestampMs))
                                    .value(sample.mySampleData);
                        }
                        writer.endObject();
                    }

                    writer.endObject();
                }
                writer.endArray();
            }
            writer.endObject();
            writer.flush();
        } catch (IOException e) {
            getLogger().error(e);
        }
    }

    /**
     * A helper method for logging a single data point for a metric to a single benchmark.
     * Note - if you need to write multiple data points or to multiple benchmarks for the same metric within a test,
     * use the {@link #addSamples(Benchmark, MetricSample...)} and {@link #commit()} APIs manually instead.
     */
    public static void log(@NotNull Benchmark benchmark, @NotNull String metricName, long data) {
        long utcMs = Instant.now().toEpochMilli();
        BenchmarkLogger logger = new BenchmarkLogger(metricName);
        logger.addSamples(benchmark, new MetricSample(utcMs, data));
        logger.commit();
    }

    /**
     * Wrapper for the configurations of the benchmark that the logger will add data to. TODO add
     * regression analysis configurations.
     */
    public static class Benchmark {
        /**
         * Name of the benchmark (e.g. this should match the name of the dashboard to add data to)
         */
        @NotNull private final String myBenchmarkName;

        public Benchmark(@NotNull String name) {
            myBenchmarkName = name;
        }

        @Override
        public int hashCode() {
            return myBenchmarkName.hashCode();
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Benchmark)) {
                return false;
            }

            return myBenchmarkName == ((Benchmark) other).myBenchmarkName;
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
