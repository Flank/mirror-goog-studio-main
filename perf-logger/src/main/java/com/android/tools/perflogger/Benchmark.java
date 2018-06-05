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

package com.android.tools.perflogger;

import com.android.annotations.NonNull;
import java.time.Instant;
import java.util.Objects;

/**
 * Wrapper for the configurations of the benchmark that the logger will add data to. TODO add
 * regression analysis configurations.
 */
public class Benchmark {
    private static final String DEFAULT_PROJECT_NAME = "Perfgate for Android Studio";

    @NonNull private final String myBenchmarkName;
    @NonNull private final String myProjectName;

    private Benchmark(@NonNull Builder builder) {
        myBenchmarkName = builder.myBenchmarkName;
        myProjectName = builder.myProjectName;
    }

    public String getName() {
        return myBenchmarkName;
    }

    public String getProject() {
        return myProjectName;
    }

    /**
     * Log a single data point for a metric to this benchmark. Note - if you need to write multiple
     * data points or to multiple benchmarks for the same metric within a test, use the {@link
     * Metric#addSamples(Benchmark, Metric.MetricSample...)} and {@link Metric#commit()} APIs
     * manually instead.
     */
    public void log(@NonNull String metricName, long data) {
        long utcMs = Instant.now().toEpochMilli();
        Metric metric = new Metric(metricName);
        metric.addSamples(this, new Metric.MetricSample(utcMs, data));
        metric.commit();
    }

    @Override
    public int hashCode() {
        return Objects.hash(myBenchmarkName, myProjectName);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Benchmark)) {
            return false;
        }

        Benchmark otherBenchmark = (Benchmark) other;
        return myBenchmarkName == otherBenchmark.myBenchmarkName
                && myProjectName == otherBenchmark.myProjectName;
    }

    public static class Builder {
        /** Analogous to the name of the Perfgate dashboard. * */
        @NonNull private final String myBenchmarkName;
        /** Analogous to the Perfgate project grouping. * */
        @NonNull private String myProjectName = DEFAULT_PROJECT_NAME;

        public Builder(@NonNull String benchmarkName) {
            myBenchmarkName = benchmarkName;
        }

        @NonNull
        public Builder setProject(@NonNull String projectName) {
            myProjectName = projectName;
            return this;
        }

        @NonNull
        public Benchmark build() {
            return new Benchmark(this);
        }
    }
}
