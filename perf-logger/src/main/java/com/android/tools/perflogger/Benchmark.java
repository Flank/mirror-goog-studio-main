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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.collect.ImmutableMap;
import com.google.gson.annotations.Expose;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/** Wrapper for the configurations of the benchmark that the logger will add data to. */
public class Benchmark {
    private static final String DEFAULT_PROJECT_NAME = "Perfgate for Android Studio";

    @NonNull @Expose private final String name;
    @NonNull @Expose private final String projectName;

    @Nullable @Expose private final String description;
    @Nullable @Expose private final ImmutableMap<String, String> metadata;

    private Benchmark(@NonNull Builder builder) {
        name = builder.benchmarkName + getHostSuffix();
        projectName = builder.projectName;
        description = builder.description;
        metadata = builder.metadata;
    }

    /**
     * If a Benchmark is run on a platform other than Linux, the Benchmark is automatically renamed
     * so that results from different hosts don't appear on the same chart.
     *
     * @return an OS-specific string, to be appended to the name of a Benchmark
     */
    private static String getHostSuffix() {
        if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_LINUX) {
            // Linux is the "default" platform, so tests run on Linux are not suffixed
            return "";
        } else if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_DARWIN) {
            return "_mac";
        } else if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS) {
            return "_windows";
        }

        throw new RuntimeException("Unexpected platform.");
    }

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public String getProjectName() {
        return projectName;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    @Nullable
    public ImmutableMap<String, String> getMetadata() {
        return metadata;
    }

    /**
     * Log a single data point for a metric to this benchmark. Note - if you need to write multiple
     * data points or to multiple benchmarks for the same metric within a test, use the {@link
     * Metric#addSamples(Benchmark, Metric.MetricSample...)} and {@link Metric#commit()} APIs
     * manually instead.
     */
    public void log(@NonNull String metricName, long data) {
        log(metricName, data, null);
    }

    public void log(@NonNull String metricName, long data, @Nullable Analyzer analyzer) {
        long utcMs = Instant.now().toEpochMilli();
        Metric metric = new Metric(metricName);
        metric.addSamples(this, new Metric.MetricSample(utcMs, data));
        if (analyzer != null) {
            metric.setAnalyzers(this, Collections.singleton(analyzer));
        }
        metric.commit();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Benchmark benchmark = (Benchmark) o;
        return Objects.equals(name, benchmark.name)
                && Objects.equals(projectName, benchmark.projectName)
                && Objects.equals(description, benchmark.description)
                && Objects.equals(metadata, benchmark.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, projectName, description, metadata);
    }

    public static class Builder {
        /** Analogous to the name of the Perfgate dashboard. * */
        @NonNull private final String benchmarkName;
        /** Analogous to the Perfgate project grouping. * */
        @NonNull private String projectName = DEFAULT_PROJECT_NAME;

        /** Plain-text description. shown alongside the the Perfgate dashboard */
        @Nullable public String description = null;
        /**
         * Key-Value metadata information to be saved with the Benchmark for programmatic access.
         * Not necessarily surfaced in any interface. Null keys and values are not tolerated.
         */
        @Nullable public ImmutableMap<String, String> metadata = null;

        public Builder(@NonNull String benchmarkName) {
            this.benchmarkName = benchmarkName;
        }

        @NonNull
        public Builder setProject(@NonNull String projectName) {
            this.projectName = projectName;
            return this;
        }

        @NonNull
        public Builder setDescription(@NonNull String description) {
            this.description = description;
            return this;
        }

        @NonNull
        public Builder setMetadata(@NonNull Map<String, String> metadata) {
            this.metadata = ImmutableMap.copyOf(metadata);
            return this;
        }

        @NonNull
        public Benchmark build() {
            return new Benchmark(this);
        }
    }
}
