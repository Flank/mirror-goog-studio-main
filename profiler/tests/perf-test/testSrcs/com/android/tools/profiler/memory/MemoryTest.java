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
import com.android.tools.profiler.*;
import com.android.tools.profiler.proto.Common.*;
import com.android.tools.profiler.proto.MemoryProfiler.*;
import com.android.tools.profiler.proto.Profiler.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class MemoryTest {
    private static final String ACTIVITY_CLASS = "com.activity.MemoryActivity";

    // We currently only test O+ test scenarios.
    @Rule public final PerfDriver myPerfDriver = new PerfDriver(ACTIVITY_CLASS, 26);

    private GrpcUtils myGrpc;
    private Session mySession;

    @Before
    public void setup() throws Exception {
        myGrpc = myPerfDriver.getGrpc();
        mySession = myPerfDriver.getSession();

        // For Memory tests, we need to invoke beginSession and startMonitoringApp to properly
        // initialize the memory cache and establish the perfa->perfd connection
        myGrpc.getMemoryStub()
                .startMonitoringApp(MemoryStartRequest.newBuilder().setSession(mySession).build());
    }

    @After
    public void tearDown() {
        myGrpc.getMemoryStub()
                .stopMonitoringApp(MemoryStopRequest.newBuilder().setSession(mySession).build());
    }

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
        MemoryStubWrapper stubWrapper = new MemoryStubWrapper(myGrpc.getMemoryStub());

        // Start memory tracking.
        TrackAllocationsResponse trackResponse = stubWrapper.startAllocationTracking(mySession);
        assertThat(trackResponse.getStatus()).isEqualTo(TrackAllocationsResponse.Status.SUCCESS);
        MemoryData jvmtiData =
                TestUtils.waitForAndReturn(
                        () -> stubWrapper.getJvmtiData(mySession, 0, Long.MAX_VALUE),
                        value -> value.getAllocationSamplesList().size() != 0);

        // Find MemTestEntity class tag
        int memTestEntityId = findClassTag(jvmtiData.getAllocationSamplesList(), "MemTestEntity");
        assertThat(memTestEntityId).isNotEqualTo(0);
        final long startTime = jvmtiData.getEndTimestamp();

        FakeAndroidDriver androidDriver = myPerfDriver.getFakeAndroidDriver();
        final int allocationCount = 10;
        int allocationsReported = 0;
        int deallocationsReported = 0;

        // Create several instances of MemTestEntity and when done free and collect them.
        androidDriver.setProperty("allocation.count", Integer.toString(allocationCount));
        androidDriver.triggerMethod(ACTIVITY_CLASS, "allocate");
        assertThat(androidDriver.waitForInput("size " + allocationCount)).isTrue();
        androidDriver.triggerMethod(ACTIVITY_CLASS, "free");
        assertThat(androidDriver.waitForInput("size 0")).isTrue();
        // Make some allocation noise here to emulate how real apps work.
        androidDriver.triggerMethod(ACTIVITY_CLASS, "makeAllocationNoise");

        // ALLOC_DATA events are available shortly, but FREE_DATA events need System.gc
        // and a while to happen.
        while (deallocationsReported < allocationCount) {
            androidDriver.triggerMethod(ACTIVITY_CLASS, "gc");
            jvmtiData = stubWrapper.getJvmtiData(mySession, startTime, Long.MAX_VALUE);
            System.out.printf(
                    "getJvmtiData called. endTime=%d, alloc samples=%d\n",
                    jvmtiData.getEndTimestamp(), jvmtiData.getAllocationSamplesList().size());

            HashMap<Integer, String> idToThreadName = new HashMap<Integer, String>();
            for (BatchAllocationSample sample : jvmtiData.getAllocationSamplesList()) {
                for (ThreadInfo ti : sample.getThreadInfosList()) {
                    assertThat(ti.getThreadId()).isGreaterThan(0);
                    idToThreadName.put(ti.getThreadId(), ti.getThreadName());
                }
            }

            // Read alloc/dealloc reports and count how many instances of
            // MemTestEntity were created. At the same time keeping track of
            // tags.
            HashSet<Integer> tags = new HashSet<Integer>();
            allocationsReported = 0;
            deallocationsReported = 0;
            for (BatchAllocationSample sample : jvmtiData.getAllocationSamplesList()) {
                assertThat(sample.getTimestamp()).isGreaterThan(startTime);
                for (AllocationEvent event : sample.getEventsList()) {
                    if (event.getEventCase() == AllocationEvent.EventCase.ALLOC_DATA) {
                        AllocationEvent.Allocation alloc = event.getAllocData();
                        assertThat(alloc.getThreadId()).isGreaterThan(0);
                        assertThat(idToThreadName.containsKey(alloc.getThreadId())).isTrue();
                        if (alloc.getClassTag() == memTestEntityId) {
                            allocationsReported++;
                            System.out.printf("Alloc recorded: tag=%d\n", alloc.getTag());
                            assertThat(tags.add(alloc.getTag())).isTrue();
                        }
                    } else if (event.getEventCase() == AllocationEvent.EventCase.FREE_DATA) {
                        AllocationEvent.Deallocation dealloc = event.getFreeData();
                        if (tags.contains(dealloc.getTag())) {
                            deallocationsReported++;
                            System.out.printf("Free recorded: tag=%d\n", dealloc.getTag());
                            tags.remove(dealloc.getTag());
                        }
                    }
                }
            }
        }

        // allocationCount of instances should have been created/deleted.
        assertThat(allocationsReported).isEqualTo(allocationCount);
        assertThat(deallocationsReported).isEqualTo(allocationCount);
    }
}
