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

package com.android.tools.profiler.memory;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.fakeandroid.FakeAndroidDriver;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Memory;
import com.android.tools.profiler.proto.Memory.AllocatedClass;
import com.android.tools.profiler.proto.Memory.AllocationEvent;
import com.android.tools.profiler.proto.Memory.BatchAllocationContexts;
import com.android.tools.profiler.proto.Memory.BatchAllocationEvents;
import com.android.tools.profiler.proto.MemoryProfiler.AllocationSamplingRateEvent;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryData;
import com.android.tools.profiler.proto.MemoryProfiler.TrackAllocationsResponse;
import com.android.tools.transport.TestUtils;
import com.android.tools.transport.device.SdkLevel;
import com.android.tools.transport.grpc.Grpc;
import java.util.HashSet;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class MemoryTest {
    private static final String ACTIVITY_CLASS = "com.activity.memory.MemoryActivity";
    private static final int RETRY_INTERVAL_MILLIS = 50;
    private static final int SAMPLING_RATE_FULL = 1;

    // We currently only test O+ test scenarios.
    @Rule public final MemoryRule myMemoryRule = new MemoryRule(ACTIVITY_CLASS, SdkLevel.O);

    private FakeAndroidDriver myAndroidDriver;
    private Grpc myGrpc;
    private Common.Session mySession;

    @Before
    public void setUp() {
        myAndroidDriver = myMemoryRule.getTransportRule().getAndroidDriver();
        myGrpc = myMemoryRule.getTransportRule().getGrpc();
        mySession = myMemoryRule.getProfilerRule().getSession();
    }

    private int findClassTag(List<BatchAllocationContexts> samples, String className) {
        for (BatchAllocationContexts sample : samples) {
            for (AllocatedClass classAlloc : sample.getClassesList()) {
                if (classAlloc.getClassName().contains(className)) {
                    return classAlloc.getClassId();
                }
            }
        }
        return 0;
    }

    @Test
    public void countAllocationsAndDeallocation() throws Exception {
        MemoryStubWrapper stubWrapper = MemoryStubWrapper.create(myGrpc);
        FakeAndroidDriver androidDriver = myAndroidDriver;

        // Start memory tracking.
        TrackAllocationsResponse trackResponse =
                stubWrapper.startAllocationTracking(mySession);
        assertThat(trackResponse.getStatus().getStatus())
                .isEqualTo(Memory.TrackStatus.Status.SUCCESS);
        // Ensure the initialization process is finished.
        assertThat(androidDriver.waitForInput("Tracking initialization")).isTrue();

        // Find MemTestEntity class tag
        int memTestEntityId =
                TestUtils.waitForAndReturn(
                        () -> {
                            MemoryData data =
                                    stubWrapper.getJvmtiData(
                                            mySession, 0, Long.MAX_VALUE);
                            return findClassTag(
                                    data.getBatchAllocationContextsList(), "MemTestEntity");
                        },
                        tag -> tag != 0,
                        RETRY_INTERVAL_MILLIS);
        assertThat(memTestEntityId).isNotEqualTo(0);

        long startTime = 0;
        final int allocationCount = 10;
        HashSet<Integer> allocTags = new HashSet<>();
        HashSet<Integer> deallocTags = new HashSet<>();

        // Create several instances of MemTestEntity and when done free and collect them.
        androidDriver.setProperty("allocation.count", Integer.toString(allocationCount));
        androidDriver.triggerMethod(ACTIVITY_CLASS, "allocate");
        assertThat(androidDriver.waitForInput("allocation_count=" + allocationCount)).isTrue();
        androidDriver.triggerMethod(ACTIVITY_CLASS, "free");
        assertThat(androidDriver.waitForInput("free_count=0")).isTrue();
        // Make some allocation noise here to emulate how real apps work.
        androidDriver.triggerMethod(ACTIVITY_CLASS, "makeAllocationNoise");

        // ALLOC_DATA events are available shortly, but FREE_DATA events need System.gc
        // and a while to happen.
        while (deallocTags.size() < allocationCount) {
            androidDriver.triggerMethod(ACTIVITY_CLASS, "gc");
            MemoryData jvmtiData =
                    stubWrapper.getJvmtiData(mySession, startTime, Long.MAX_VALUE);
            long endTime = jvmtiData.getEndTimestamp();
            System.out.printf(
                    "getJvmtiData called. endTime=%d, alloc samples=%d\n",
                    endTime, jvmtiData.getBatchAllocationEventsList().size());

            // Read alloc/dealloc reports and count how many instances of
            // MemTestEntity were created. At the same time keeping track of
            // tags.
            for (BatchAllocationEvents sample : jvmtiData.getBatchAllocationEventsList()) {
                assertThat(sample.getTimestamp()).isGreaterThan(startTime);
                for (AllocationEvent event : sample.getEventsList()) {
                    if (event.getEventCase() == AllocationEvent.EventCase.ALLOC_DATA) {
                        AllocationEvent.Allocation alloc = event.getAllocData();
                        assertThat(alloc.getThreadId()).isGreaterThan(0);
                        if (alloc.getClassTag() == memTestEntityId) {
                            System.out.printf("Alloc recorded: tag=%d\n", alloc.getTag());
                            assertThat(allocTags.add(alloc.getTag())).isTrue();
                        }
                    } else if (event.getEventCase() == AllocationEvent.EventCase.FREE_DATA) {
                        AllocationEvent.Deallocation dealloc = event.getFreeData();
                        if (allocTags.contains(dealloc.getTag())) {
                            System.out.printf("Free recorded: tag=%d\n", dealloc.getTag());
                            assertThat(deallocTags.add(dealloc.getTag())).isTrue();
                        }
                    }
                }
            }
            startTime = Math.max(startTime, endTime);
            Thread.sleep(RETRY_INTERVAL_MILLIS);
        }

        // allocationCount of instances should have been created/deleted.
        assertThat(deallocTags).isEqualTo(allocTags);
    }

    @Test
    public void updateAllocationSamplingRate() throws Exception {
        MemoryStubWrapper stubWrapper = MemoryStubWrapper.create(myGrpc);
        FakeAndroidDriver androidDriver = myAndroidDriver;
        final int samplingNumInterval = 2;
        final int allocationCount = 10;
        HashSet<Integer> allocTags = new HashSet<>();
        HashSet<Integer> noiseAllocTags = new HashSet<>();

        // Start memory tracking.
        TrackAllocationsResponse trackResponse =
                stubWrapper.startAllocationTracking(mySession);
        assertThat(trackResponse.getStatus().getStatus())
                .isEqualTo(Memory.TrackStatus.Status.SUCCESS);
        // Ensure the initialization process is finished.
        assertThat(androidDriver.waitForInput("Tracking initialization")).isTrue();

        // Find the class tags we need.
        int memTestEntityId =
                TestUtils.waitForAndReturn(
                        () -> {
                            MemoryData data =
                                    stubWrapper.getJvmtiData(
                                            mySession, 0, Long.MAX_VALUE);
                            return findClassTag(
                                    data.getBatchAllocationContextsList(), "MemTestEntity");
                        },
                        tag -> tag != 0,
                        RETRY_INTERVAL_MILLIS);
        assertThat(memTestEntityId).isNotEqualTo(0);
        int memNoiseEntityId =
                TestUtils.waitForAndReturn(
                        () -> {
                            MemoryData data =
                                    stubWrapper.getJvmtiData(
                                            mySession, 0, Long.MAX_VALUE);
                            return findClassTag(
                                    data.getBatchAllocationContextsList(), "MemNoiseEntity");
                        },
                        tag -> tag != 0,
                        RETRY_INTERVAL_MILLIS);
        assertThat(memNoiseEntityId).isNotEqualTo(0);

        // Also verify we have an initial sampling rate.
        MemoryData jvmtiData =
                TestUtils.waitForAndReturn(
                        () ->
                                stubWrapper.getJvmtiData(
                                        mySession, 0, Long.MAX_VALUE),
                        value -> value.getAllocSamplingRateEventsCount() != 0,
                        RETRY_INTERVAL_MILLIS);
        assertThat(jvmtiData.getAllocSamplingRateEventsCount()).isEqualTo(1);
        AllocationSamplingRateEvent samplingRateEvent = jvmtiData.getAllocSamplingRateEvents(0);
        long lastSamplingRateEventTimestamp = samplingRateEvent.getTimestamp();
        assertThat(lastSamplingRateEventTimestamp).isGreaterThan(0L);
        assertThat(samplingRateEvent.getSamplingRate().getSamplingNumInterval())
                .isEqualTo(SAMPLING_RATE_FULL);

        // Set sampling rate.
        stubWrapper.setSamplingRate(mySession, samplingNumInterval);
        // printf from the agent signalling that the sample rate has been updated.
        assertThat(androidDriver.waitForInput("Setting sampling rate")).isTrue();

        // Create several instances of MemTestEntity.
        androidDriver.setProperty("allocation.count", Integer.toString(allocationCount));
        androidDriver.triggerMethod(ACTIVITY_CLASS, "allocate");
        assertThat(androidDriver.waitForInput("allocation_count=" + allocationCount)).isTrue();
        // Make some allocation noise so we count MemTestEntity correctly.
        androidDriver.triggerMethod(ACTIVITY_CLASS, "makeAllocationNoise");

        // Verify allocation data are now sampled.
        long startTime = 0;
        while (allocTags.size() == 0 || noiseAllocTags.size() == 0) {
            jvmtiData =
                    stubWrapper.getJvmtiData(mySession, startTime, Long.MAX_VALUE);
            System.out.println(
                    "getJvmtiData called. alloc samples="
                            + jvmtiData.getBatchAllocationEventsCount());

            for (BatchAllocationEvents sample : jvmtiData.getBatchAllocationEventsList()) {
                for (AllocationEvent event : sample.getEventsList()) {
                    if (event.getEventCase() == AllocationEvent.EventCase.ALLOC_DATA) {
                        AllocationEvent.Allocation alloc = event.getAllocData();
                        if (alloc.getClassTag() == memTestEntityId) {
                            System.out.println("Alloc recorded: tag=" + alloc.getTag());
                            assertThat(allocTags.add(alloc.getTag())).isTrue();
                        } else if (alloc.getClassTag() == memNoiseEntityId) {
                            noiseAllocTags.add(alloc.getTag());
                        }
                    }
                }
            }
            startTime = Math.max(startTime, jvmtiData.getEndTimestamp());
            Thread.sleep(RETRY_INTERVAL_MILLIS);
        }

        // If other objects are allocated between the MemTestEntity objects, we may get more than
        // (allocationCount / samplingNumInterval) MemTestEntity objects. So we just need to verify
        // that the number of tagged MemTestEntity is less than total. There is a chance that all
        // MemTestEntity objects happen to be selected as samples but that should be extremely rare.
        assertThat(allocTags.size()).isAtMost(allocationCount);

        // Verify all AllocationSamplingRateEvents also came with JVMTI data.
        jvmtiData = stubWrapper.getJvmtiData(mySession, 0, Long.MAX_VALUE);
        assertThat(jvmtiData.getAllocSamplingRateEventsCount()).isEqualTo(2);
        samplingRateEvent = jvmtiData.getAllocSamplingRateEvents(1);
        assertThat(samplingRateEvent.getTimestamp()).isGreaterThan(lastSamplingRateEventTimestamp);
        assertThat(samplingRateEvent.getSamplingRate().getSamplingNumInterval())
                .isEqualTo(samplingNumInterval);
        lastSamplingRateEventTimestamp = samplingRateEvent.getTimestamp();

        // Record allocation count
        MemoryData memoryData =
                stubWrapper.getMemoryData(mySession, 0, Long.MAX_VALUE);
        int allocStatsCount = memoryData.getAllocStatsSamplesCount();
        assertThat(allocStatsCount).isGreaterThan(0);
        int allocCount =
                memoryData
                        .getAllocStatsSamples(allocStatsCount - 1)
                        .getAllocStats()
                        .getJavaAllocationCount();

        // Set sampling rate back to full.
        stubWrapper.setSamplingRate(mySession, SAMPLING_RATE_FULL);
        // printf from the agent signalling that the sample rate has been updated.
        assertThat(androidDriver.waitForInput("Setting sampling rate")).isTrue();

        // Create more instances of MemTestEntity. We keep the previously allocated objects so we
        // can verify a new heap walk was performed.
        androidDriver.setProperty("allocation.count", Integer.toString(allocationCount));
        androidDriver.triggerMethod(ACTIVITY_CLASS, "allocate");
        assertThat(androidDriver.waitForInput("allocation_count=" + allocationCount * 2)).isTrue();

        // Verify allocation data are no longer sampled and we get what's already on the heap.
        while (allocTags.size() < allocationCount * 2) {
            jvmtiData =
                    stubWrapper.getJvmtiData(mySession, startTime, Long.MAX_VALUE);
            System.out.println(
                    "getJvmtiData called. alloc samples="
                            + jvmtiData.getBatchAllocationEventsCount());

            for (BatchAllocationEvents sample : jvmtiData.getBatchAllocationEventsList()) {
                for (AllocationEvent event : sample.getEventsList()) {
                    if (event.getEventCase() == AllocationEvent.EventCase.ALLOC_DATA) {
                        assertThat(event.getTimestamp()).isGreaterThan(0L);
                        AllocationEvent.Allocation alloc = event.getAllocData();
                        if (alloc.getClassTag() == memTestEntityId) {
                            System.out.println("Alloc recorded: tag=" + alloc.getTag());
                            assertThat(allocTags.add(alloc.getTag())).isTrue();
                        }
                    }
                }
            }
            startTime = Math.max(startTime, jvmtiData.getEndTimestamp());
            Thread.sleep(RETRY_INTERVAL_MILLIS);
        }
        assertThat(allocTags.size()).isEqualTo(allocationCount * 2);

        // Verify all AllocationSamplingRateEvents also came with JVMTI data.
        jvmtiData = stubWrapper.getJvmtiData(mySession, 0, Long.MAX_VALUE);
        assertThat(jvmtiData.getAllocSamplingRateEventsCount()).isEqualTo(3);
        samplingRateEvent = jvmtiData.getAllocSamplingRateEvents(2);
        assertThat(samplingRateEvent.getTimestamp()).isGreaterThan(lastSamplingRateEventTimestamp);
        assertThat(samplingRateEvent.getSamplingRate().getSamplingNumInterval())
                .isEqualTo(SAMPLING_RATE_FULL);

        // Verify alloc count did not reset after switching back to full.
        memoryData =
                stubWrapper.getMemoryData(
                        mySession, memoryData.getEndTimestamp(), Long.MAX_VALUE);
        allocStatsCount = memoryData.getAllocStatsSamplesCount();
        assertThat(allocStatsCount).isGreaterThan(0);
        assertThat(
                        memoryData
                                .getAllocStatsSamples(allocStatsCount - 1)
                                .getAllocStats()
                                .getJavaAllocationCount())
                .isAtLeast(allocCount);
    }
}
