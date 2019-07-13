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

package com.android.tools.profiler.performance;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.fakeandroid.FakeAndroidDriver;
import com.android.tools.perflogger.Benchmark;
import com.android.tools.perflogger.Metric;
import com.android.tools.perflogger.Metric.MetricSample;
import com.android.tools.perflogger.WindowDeviationAnalyzer;
import com.android.tools.profiler.MemoryPerfDriver;
import com.android.tools.profiler.PerfDriver;
import com.android.tools.profiler.memory.MemoryStubWrapper;
import com.android.tools.profiler.proto.Memory;
import com.android.tools.profiler.proto.MemoryProfiler.TrackAllocationsResponse;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.regex.Pattern;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LiveAllocationTest {

    /**
     * Parameters: {AllocationCount, AllocationSize, IsTracking, SamplingRate}.
     *
     * <p>When IsTracking is false, SamplingRate is ignored.
     */
    @Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(
                new Object[][] {
                    {1000000, 10, false, 0}, {1000000, 10, true, 1},
                    {1000000, 10, true, 0}, {1000000, 10, true, 10},
                    {10000, 1000, false, 0}, {10000, 1000, true, 1},
                    {100, 100000, false, 0}, {100, 100000, true, 1},
                });
    }

    private static final int NUM_SAMPLES = 10;
    private static final String ACTIVITY_CLASS = "com.activity.MemoryActivity";
    private static final String PROFILER_PROJECT_NAME = "Android Studio Profilers";
    private static final String LIVE_ALLOCATION_BENCHMARK_NAME =
            "Profiler Live Allocation Timing (ms)";
    private static final Pattern ALLOCATION_TIMING_PATTERN =
            Pattern.compile("(.*)allocation_timing=(?<result>.*)");

    // We currently only test O+ test scenarios.
    @Rule public final PerfDriver myPerfDriver = new MemoryPerfDriver(ACTIVITY_CLASS, 26);

    private long myAllocationCount;
    private long myAllocationSize;
    private boolean myIsTracking;
    private int mySamplingRate;

    public LiveAllocationTest(
            long allocationCount, long allocationSize, boolean isTracking, int samplingRate) {
        myAllocationCount = allocationCount;
        myAllocationSize = allocationSize;
        myIsTracking = isTracking;
        mySamplingRate = samplingRate;
        myPerfDriver.setLiveAllocSamplingRate(samplingRate);
    }

    @Test
    public void testAllocationTiming() {
        if (myIsTracking) {
            MemoryStubWrapper myStubWrapper =
                    new MemoryStubWrapper(myPerfDriver.getGrpc().getMemoryStub());
            TrackAllocationsResponse trackResponse =
                    myStubWrapper.startAllocationTracking(myPerfDriver.getSession());
            assertThat(trackResponse.getStatus().getStatus())
                    .isEqualTo(Memory.TrackStatus.Status.SUCCESS);
        }

        FakeAndroidDriver androidDriver = myPerfDriver.getFakeAndroidDriver();

        Benchmark benchmark =
                new Benchmark.Builder(LIVE_ALLOCATION_BENCHMARK_NAME)
                        .setProject(PROFILER_PROJECT_NAME)
                        .build();
        Metric metric =
                new Metric(
                        String.format(
                                "allocation-count_%d-size_%d-tracking_%s",
                                myAllocationCount,
                                myAllocationSize,
                                myIsTracking
                                        ? "on" + samplingRateToString(mySamplingRate)
                                        : "off"));

        androidDriver.setProperty("allocation.count", String.valueOf(myAllocationCount));
        androidDriver.setProperty("allocation.size", String.valueOf(myAllocationSize));

        for (int i = 0; i < NUM_SAMPLES; ++i) {
            androidDriver.triggerMethod(ACTIVITY_CLASS, "gc");
            assertThat(androidDriver.waitForInput("MemoryActivity.gc")).isTrue();
            androidDriver.triggerMethod(ACTIVITY_CLASS, "allocate");
            assertThat(androidDriver.waitForInput("MemoryActivity.allocate")).isTrue();
            assertThat(androidDriver.waitForInput("allocation_count=" + myAllocationCount))
                    .isTrue();
            String allocationTiming = androidDriver.waitForInput(ALLOCATION_TIMING_PATTERN);
            assertThat(allocationTiming).isNotEmpty();
            metric.addSamples(
                    benchmark,
                    new MetricSample(
                            Instant.now().toEpochMilli(), Long.parseLong(allocationTiming)));
            androidDriver.triggerMethod(ACTIVITY_CLASS, "free");
            assertThat(androidDriver.waitForInput("free_count=0")).isTrue();
        }
        metric.setAnalyzers(benchmark, Collections.singleton(new WindowDeviationAnalyzer.Builder()
                .addMeanTolerance(new WindowDeviationAnalyzer.MeanToleranceParams.Builder().build())
                .build()));
        metric.commit();
    }

    private static String samplingRateToString(int samplingRate) {
        if (samplingRate == 0) {
            return "-sample_none";
        }
        if (samplingRate == 1) {
            // Full tracking mode, keep existing Perfgate metric names.
            return "";
        }
        if (samplingRate > 1) {
            return "-sample_every_" + samplingRate;
        }
        return "-invalid_sampling_rate";
    }
}
