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
import com.android.tools.profiler.MemoryPerfDriver;
import com.android.tools.profiler.PerfDriver;
import com.android.tools.profiler.TestUtils;
import com.android.tools.profiler.proto.MemoryProfiler.AllocatedClass;
import com.android.tools.profiler.proto.MemoryProfiler.AllocationEvent;
import com.android.tools.profiler.proto.MemoryProfiler.BatchAllocationSample;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryData;
import com.android.tools.profiler.proto.MemoryProfiler.ThreadInfo;
import com.android.tools.profiler.proto.MemoryProfiler.TrackAllocationsResponse;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;

public class MemoryTest {
    private static final String ACTIVITY_CLASS = "com.activity.MemoryActivity";

    // We currently only test O+ test scenarios.
    @Rule public final PerfDriver myPerfDriver = new MemoryPerfDriver(ACTIVITY_CLASS, 26);

    private int findClassTag(List<BatchAllocationSample> samples, String className) {
        for (BatchAllocationSample sample : samples) {
            for (AllocationEvent event : sample.getEventsList()) {
                if (event.getEventCase() == AllocationEvent.EventCase.CLASS_DATA) {
                    AllocatedClass classAlloc = event.getClassData();
                    if (classAlloc.getClassName().contains(className)) {
                        return classAlloc.getClassId();
                    }
                }
            }
        }
        return 0;
    }

    @Test
    public void countAllocationsAndDeallocation() throws Exception {
        MemoryStubWrapper stubWrapper =
                new MemoryStubWrapper(myPerfDriver.getGrpc().getMemoryStub());

        // Start memory tracking.
        TrackAllocationsResponse trackResponse =
                stubWrapper.startAllocationTracking(myPerfDriver.getSession());
        assertThat(trackResponse.getStatus()).isEqualTo(TrackAllocationsResponse.Status.SUCCESS);
        MemoryData jvmtiData =
                TestUtils.waitForAndReturn(
                        () ->
                                stubWrapper.getJvmtiData(
                                        myPerfDriver.getSession(), 0, Long.MAX_VALUE),
                        value -> value.getAllocationSamplesList().size() != 0);

        // Find MemTestEntity class tag
        int memTestEntityId = findClassTag(jvmtiData.getAllocationSamplesList(), "MemTestEntity");
        assertThat(memTestEntityId).isNotEqualTo(0);
        long startTime = jvmtiData.getEndTimestamp();

        FakeAndroidDriver androidDriver = myPerfDriver.getFakeAndroidDriver();
        final int allocationCount = 10;
        HashSet<Integer> allocTags = new HashSet<Integer>();
        HashSet<Integer> deallocTags = new HashSet<Integer>();
        HashMap<Integer, String> idToThreadName = new HashMap<Integer, String>();

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
            jvmtiData =
                    stubWrapper.getJvmtiData(myPerfDriver.getSession(), startTime, Long.MAX_VALUE);
            long endTime = jvmtiData.getEndTimestamp();
            System.out.printf(
                    "getJvmtiData called. endTime=%d, alloc samples=%d\n",
                    endTime, jvmtiData.getAllocationSamplesList().size());

            // Read alloc/dealloc reports and count how many instances of
            // MemTestEntity were created. At the same time keeping track of
            // tags.
            for (BatchAllocationSample sample : jvmtiData.getAllocationSamplesList()) {
                for (ThreadInfo ti : sample.getThreadInfosList()) {
                    assertThat(ti.getThreadId()).isGreaterThan(0);
                    idToThreadName.put(ti.getThreadId(), ti.getThreadName());
                }
                assertThat(sample.getTimestamp()).isGreaterThan(startTime);
                for (AllocationEvent event : sample.getEventsList()) {
                    if (event.getEventCase() == AllocationEvent.EventCase.ALLOC_DATA) {
                        AllocationEvent.Allocation alloc = event.getAllocData();
                        assertThat(alloc.getThreadId()).isGreaterThan(0);
                        assertThat(idToThreadName.containsKey(alloc.getThreadId())).isTrue();
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
            if (jvmtiData.getAllocationSamplesList().size() > 0) {
                assertThat(endTime).isGreaterThan(startTime);
                startTime = endTime;
            }
        }

        // allocationCount of instances should have been created/deleted.
        assertThat(deallocTags).isEqualTo(allocTags);
    }

    @Test
    public void setSamplingRate() throws Exception {
        MemoryStubWrapper stubWrapper =
                new MemoryStubWrapper(myPerfDriver.getGrpc().getMemoryStub());
        FakeAndroidDriver androidDriver = myPerfDriver.getFakeAndroidDriver();
        final int samplingNumInterval = 10;

        // Start memory tracking.
        TrackAllocationsResponse trackResponse =
                stubWrapper.startAllocationTracking(myPerfDriver.getSession());
        assertThat(trackResponse.getStatus()).isEqualTo(TrackAllocationsResponse.Status.SUCCESS);

        // Set sampling rate.
        stubWrapper.setSamplingRate(myPerfDriver.getSession(), samplingNumInterval);
        assertThat(androidDriver.waitForInput("sampling_num_interval=" + samplingNumInterval))
                .isTrue();
    }
}
