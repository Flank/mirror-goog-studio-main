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

public class JniTest {
    private static final String ACTIVITY_CLASS = "com.activity.NativeCodeActivity";

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
        MemoryData jvmtiData =
                TestUtils.waitForAndReturn(
                        () -> stubWrapper.getJvmtiData(mySession, 0, Long.MAX_VALUE),
                        value -> value.getAllocationSamplesList().size() != 0);

        // Find JNITestEntity class tag
        int testEntityId = findClassTag(jvmtiData.getAllocationSamplesList(), "JNITestEntity");
        assertThat(testEntityId).isNotEqualTo(0);
        final long startTime = jvmtiData.getEndTimestamp();

        final int refCount = 10;
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
            System.out.printf(
                    "getJvmtiData, start time=%d, end time=%d, alloc entries=%d, jni entries=%d\n",
                    startTime,
                    jvmtiData.getEndTimestamp(),
                    jvmtiData.getAllocationSamplesList().size(),
                    jvmtiData.getJniReferenceEventBatchesList().size());

            HashSet<Integer> tags = new HashSet<>();
            HashMap<Integer, String> idToThreadName = new HashMap<>();
            for (BatchAllocationSample sample : jvmtiData.getAllocationSamplesList()) {
                assertThat(sample.getTimestamp()).isGreaterThan(startTime);
                for (ThreadInfo ti : sample.getThreadInfosList()) {
                    assertThat(ti.getThreadId()).isGreaterThan(0);
                    idToThreadName.put(ti.getThreadId(), ti.getThreadName());
                }
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

            int refsReported = 0;
            HashSet<Long> refs = new HashSet<>();
            for (BatchJNIGlobalRefEvent batch : jvmtiData.getJniReferenceEventBatchesList()) {
                assertThat(batch.getTimestamp()).isGreaterThan(startTime);
                for (ThreadInfo ti : batch.getThreadInfosList()) {
                    assertThat(ti.getThreadId()).isGreaterThan(0);
                    idToThreadName.put(ti.getThreadId(), ti.getThreadName());
                }
                for (JNIGlobalReferenceEvent event : batch.getEventsList()) {
                    long refValue = event.getRefValue();
                    assertThat(event.getThreadId()).isGreaterThan(0);
                    assertThat(idToThreadName.containsKey(event.getThreadId())).isTrue();
                    if (event.getEventType() == JNIGlobalReferenceEvent.Type.CREATE_GLOBAL_REF
                            && tags.contains(event.getObjectTag())) {
                        System.out.printf(
                                "Add JNI ref: %d tag:%d\n", refValue, event.getObjectTag());
                        String refRelatedOutput = String.format("JNI ref created %d", refValue);
                        assertThat(androidDriver.waitForInput(refRelatedOutput)).isTrue();
                        assertThat(refs.add(refValue)).isTrue();
                        refsReported++;
                    }
                    if (event.getEventType() == JNIGlobalReferenceEvent.Type.DELETE_GLOBAL_REF
                            // Test that reference value was reported when created
                            && refs.contains(refValue)) {
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
                    validateMemoryMap(batch.getMemoryMap(), event.getBacktrace());
                }
            }
        }
        assertThat(allRefsAccounted).isTrue();
    }
}
