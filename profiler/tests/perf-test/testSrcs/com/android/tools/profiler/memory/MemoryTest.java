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

import com.android.tools.profiler.*;
import com.android.tools.profiler.proto.Common.*;
import com.android.tools.profiler.proto.MemoryProfiler.*;
import com.android.tools.profiler.proto.Profiler.*;
import java.util.HashSet;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class MemoryTest {
    private static final String ACTIVITY_CLASS = "com.activity.MemoryActivity";

    private PerfDriver myPerfDriver;
    private GrpcUtils myGrpc;
    private Session mySession;

    @Before
    public void setup() throws Exception {
        // We currently only test O+ test scenarios.
        myPerfDriver = new PerfDriver(true);
        myPerfDriver.start(ACTIVITY_CLASS);
        myGrpc = myPerfDriver.getGrpc();

        // For Memory tests, we need to invoke beginSession and startMonitoringApp to properly
        // initialize the memory cache and establish the perfa->perfd connection
        BeginSessionResponse response =
                myGrpc.getProfilerStub()
                        .beginSession(
                                BeginSessionRequest.newBuilder()
                                        .setDeviceId(1234)
                                        .setProcessId(myGrpc.getProcessId())
                                        .build());
        mySession = response.getSession();
        myGrpc.getMemoryStub()
                .startMonitoringApp(MemoryStartRequest.newBuilder().setSession(mySession).build());
    }

    @After
    public void tearDown() {
        myGrpc.getProfilerStub()
                .endSession(
                        EndSessionRequest.newBuilder()
                                .setSessionId(mySession.getSessionId())
                                .build());
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
        MemoryData jvmtiData = stubWrapper.getJvmtiData(mySession, 0, Long.MAX_VALUE);

        // Wait before we start getting actual data.
        while (jvmtiData.getAllocationSamplesList().size() == 0) {
            jvmtiData = stubWrapper.getJvmtiData(mySession, 0, Long.MAX_VALUE);
        }

        // Find MemTestEntity class tag
        int memTestEntityId = findClassTag(jvmtiData.getAllocationSamplesList(), "MemTestEntity");
        assertThat(memTestEntityId).isNotEqualTo(0);
        long startTime = jvmtiData.getEndTimestamp();

        FakeAndroidDriver androidDriver = myPerfDriver.getFakeAndroidDriver();
        final int allocationCount = 10;
        int allocationsDone = 0;
        int allocationsReported = 0;
        int deallocationsReported = 0;
        int maxLoopCount = allocationCount * 100;
        HashSet<Integer> tags = new HashSet<Integer>();

        // Aborts if we loop too many times and somehow didn't get
        // back the alloc/dealloc data in time. This way the test won't timeout
        // even when fails.
        while (deallocationsReported < allocationCount && maxLoopCount-- > 0) {
            // Create several instances of MemTestEntity and when
            // done free and collect them.
            if (allocationsDone < allocationCount) {
                androidDriver.triggerMethod(ACTIVITY_CLASS, "allocate");
                allocationsDone++;
                androidDriver.triggerMethod(ACTIVITY_CLASS, "size");
                String size_response = String.format("size %d", allocationsDone);
                assertThat(androidDriver.waitForInput(size_response)).isTrue();
            } else {
                androidDriver.triggerMethod(ACTIVITY_CLASS, "free");
                androidDriver.triggerMethod(ACTIVITY_CLASS, "gc");
                androidDriver.triggerMethod(ACTIVITY_CLASS, "size");
                assertThat(androidDriver.waitForInput("size 0")).isTrue();
            }
            // Make some allocation noise here to emulate how real apps work.
            androidDriver.triggerMethod(ACTIVITY_CLASS, "makeAllocationNoise");

            jvmtiData = stubWrapper.getJvmtiData(mySession, startTime, Long.MAX_VALUE);
            long endTime = jvmtiData.getEndTimestamp();
            System.out.printf("getJvmtiData called. endTime=%d, alloc samples=%d\n",
                            endTime, jvmtiData.getAllocationSamplesList().size());

            // Read alloc/dealloc reports and count how many instances of
            // MemTestEntity were created. At the same time keeping track of
            // tags.
            for (BatchAllocationSample sample : jvmtiData.getAllocationSamplesList()) {
                assertThat(sample.getTimestamp()).isGreaterThan(startTime);
                for (AllocationEvent event : sample.getEventsList()) {
                    if (event.getEventCase() == AllocationEvent.EventCase.ALLOC_DATA) {
                        AllocationEvent.Allocation alloc = event.getAllocData();
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

            if (jvmtiData.getAllocationSamplesList().size() > 0) {
                assertThat(endTime).isGreaterThan(startTime);
                startTime = endTime;
            }
        }

        // allocationCount of instances should have been
        // created/deleted and all tags must be acounted for.
        assertThat(allocationsReported).isEqualTo(allocationCount);
        assertThat(deallocationsReported).isEqualTo(allocationCount);
        assertThat(tags.isEmpty()).isTrue();
    }
}
