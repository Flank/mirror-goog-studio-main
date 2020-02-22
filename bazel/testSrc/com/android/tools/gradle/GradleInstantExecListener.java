/*
 * Copyright (C) 2020 The Android Open Source Project
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
import com.android.tools.perflogger.Benchmark;
import com.android.tools.perflogger.Metric;
import java.io.File;
import java.util.Collections;

/**
 * This is a simple benchmark performance stats collector that simply measures total build time. At
 * the moment, it is not possible to collect performance stats about AGP when instant execution is
 * used, so this is a replacement.
 */
@SuppressWarnings("unused")
public class GradleInstantExecListener implements BenchmarkListener {

    private Benchmark benchmark;
    private Metric totalBuildTime;

    @Override
    public void configure(
            @NonNull File home, @NonNull Gradle gradle, @NonNull BenchmarkRun benchmarkRun) {}

    @Override
    public void benchmarkStarting(@NonNull Benchmark benchmark) {
        this.benchmark = benchmark;
        this.totalBuildTime = new Metric(ProfilerToBenchmarkAdapter.TOTAL_BUILD_TIME_METRIC);
    }

    @Override
    public void benchmarkDone() {
        totalBuildTime.setAnalyzers(
                benchmark,
                Collections.singleton(ProfilerToBenchmarkAdapter.TOTAL_BUILD_TIME_ANALYZER));
        totalBuildTime.commit();
    }

    private long iterationStartTime;

    @Override
    public void iterationStarting() {
        iterationStartTime = System.currentTimeMillis();
    }

    @Override
    public void iterationDone() {
        long currentTime = System.currentTimeMillis();
        totalBuildTime.addSamples(
                benchmark, new Metric.MetricSample(currentTime, currentTime - iterationStartTime));
    }
}
