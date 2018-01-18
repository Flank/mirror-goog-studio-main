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

public class JniTest {
    private static final String ACTIVITY_CLASS = "com.activity.NativeCodeActivity";

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
        mySession =
                myGrpc.beginSessionWithAgent(
                        myPerfDriver.getPid(), myPerfDriver.getCommunicationPort());
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

    private void validateMemoryMap(MemoryMap map, NativeBacktrace backtrace) {
        assertThat(backtrace.getAddressesList().isEmpty()).isFalse();
        assertThat(map.getRegionsList().isEmpty()).isFalse();
        for (long addr : backtrace.getAddressesList()) {
            boolean found = false;
            for (MemoryMap.MemoryRegion region : map.getRegionsList()) {
                if (region.getStartAddress() <= addr && region.getEndAddress() > addr) {
                    found = true;
                    break;
                }
            }
            assertThat(found).isTrue();
        }
    }

    // Just create native activity and see that it can load native library.
    @Test
    public void countCreatedAndDeleteRefEvents() throws Exception {
        FakeAndroidDriver androidDriver = myPerfDriver.getFakeAndroidDriver();
        MemoryStubWrapper stubWrapper = new MemoryStubWrapper(myGrpc.getMemoryStub());

        // Start memory tracking.
        TrackAllocationsResponse trackResponse = stubWrapper.startAllocationTracking(mySession);
        assertThat(trackResponse.getStatus()).isEqualTo(TrackAllocationsResponse.Status.SUCCESS);
        MemoryData jvmtiData = stubWrapper.getJvmtiData(mySession, 0, Long.MAX_VALUE);

        // Wait before we start getting acutal data.
        while (jvmtiData.getAllocationSamplesList().size() == 0) {
            jvmtiData = stubWrapper.getJvmtiData(mySession, 0, Long.MAX_VALUE);
        }

        // Find JNITestEntity class tag
        int testEntityId = findClassTag(jvmtiData.getAllocationSamplesList(), "JNITestEntity");
        assertThat(testEntityId).isNotEqualTo(0);
        long startTime = jvmtiData.getEndTimestamp();

        final int refCount = 10;
        HashSet<Integer> tags = new HashSet<Integer>();
        HashSet<Long> refs = new HashSet<Long>();
        int refsReported = 0;

        androidDriver.setProperty("jni.refcount", Integer.toString(refCount));
        androidDriver.triggerMethod(ACTIVITY_CLASS, "createRefs");
        assertThat(androidDriver.waitForInput("createRefs")).isTrue();

        androidDriver.triggerMethod(ACTIVITY_CLASS, "deleteRefs");
        assertThat(androidDriver.waitForInput("deleteRefs")).isTrue();

        boolean allRefsAccounted = false;
        int maxLoopCount = refCount * 200;

        // Aborts if we loop too many times and somehow didn't get
        // back jni ref events. This way the test won't timeout
        // even when fails.
        while (!allRefsAccounted && maxLoopCount-- > 0) {
            jvmtiData = stubWrapper.getJvmtiData(mySession, startTime, Long.MAX_VALUE);
            long endTime = jvmtiData.getEndTimestamp();
            System.out.printf(
                    "getJvmtiData, start time=%d, end time=%d, alloc entries=%d, jni entries=%d\n",
                    startTime,
                    endTime,
                    jvmtiData.getAllocationSamplesList().size(),
                    jvmtiData.getJniReferenceEventBatchesList().size());

            for (BatchAllocationSample sample : jvmtiData.getAllocationSamplesList()) {
                assertThat(sample.getTimestamp()).isGreaterThan(startTime);
                for (AllocationEvent event : sample.getEventsList()) {
                    if (event.getEventCase() == AllocationEvent.EventCase.ALLOC_DATA) {
                        AllocationEvent.Allocation alloc = event.getAllocData();
                        if (alloc.getClassTag() == testEntityId) {
                            tags.add(alloc.getTag());
                            System.out.printf("Add obj tag: %d\n", alloc.getTag());
                        }
                    }
                }
            }

            for (BatchJNIGlobalRefEvent batch : jvmtiData.getJniReferenceEventBatchesList()) {
                assertThat(batch.getTimestamp()).isGreaterThan(startTime);
                for (JNIGlobalReferenceEvent event : batch.getEventsList()) {
                    long refValue = event.getRefValue();
                    if (event.getEventType() == JNIGlobalReferenceEvent.Type.CREATE_GLOBAL_REF) {
                        if (tags.contains(event.getObjectTag())) {
                            System.out.printf(
                                    "Add JNI ref: %d tag:%d\n", refValue, event.getObjectTag());
                            String refRelatedOutput = String.format("JNI ref created %d", refValue);
                            assertThat(androidDriver.waitForInput(refRelatedOutput)).isTrue();
                            assertThat(refs.add(refValue)).isTrue();
                            refsReported++;
                        }
                    }
                    if (event.getEventType() == JNIGlobalReferenceEvent.Type.DELETE_GLOBAL_REF) {
                        // Test that reference value was reported when created
                        if (refs.contains(refValue)) {
                            System.out.printf(
                                    "Remove JNI ref: %d tag:%d\n", refValue, event.getObjectTag());
                            String refRelatedOutput = String.format("JNI ref deleted %d", refValue);
                            assertThat(androidDriver.waitForInput(refRelatedOutput)).isTrue();
                            assertThat(tags.contains(event.getObjectTag())).isTrue();
                            refs.remove(refValue);
                            if (refs.isEmpty() && refsReported == refCount) {
                                allRefsAccounted = true;
                            }
                        }
                    }
                    validateMemoryMap(batch.getMemoryMap(), event.getBacktrace());
                }
            }

            if (jvmtiData.getAllocationSamplesList().size() > 0) {
                assertThat(endTime).isGreaterThan(startTime);
                startTime = endTime;
            }
        }

        assertThat(refsReported).isEqualTo(refCount);
        assertThat(refs.isEmpty()).isTrue();
    }
}
