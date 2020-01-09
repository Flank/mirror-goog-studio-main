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
import com.android.tools.profiler.proto.Memory.*;
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

public final class JniTest {
    private static final String ACTIVITY_CLASS = "com.activity.memory.NativeCodeActivity";
    private static final int RETRY_INTERVAL_MILLIS = 50;

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
        MemoryStubWrapper stubWrapper = MemoryStubWrapper.create(myGrpc);

        // Start memory tracking.
        TrackAllocationsResponse trackResponse = stubWrapper.startAllocationTracking(mySession);
        assertThat(trackResponse.getStatus().getStatus()).isEqualTo(TrackStatus.Status.SUCCESS);
        // Ensure the initialization process is finished.
        assertThat(myAndroidDriver.waitForInput("Tracking initialization")).isTrue();

        // Find JNITestEntity class tag
        int testEntityId =
                TestUtils.waitForAndReturn(
                        () -> {
                            MemoryData data = stubWrapper.getJvmtiData(mySession, 0, Long.MAX_VALUE);
                            return findClassTag(
                                    data.getBatchAllocationContextsList(), "JNITestEntity");
                        },
                        tag -> tag != 0);
        assertThat(testEntityId).isNotEqualTo(0);

        final int refCount = 10;
        myAndroidDriver.setProperty("jni.refcount", Integer.toString(refCount));
        myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "createRefs");
        assertThat(myAndroidDriver.waitForInput("createRefs")).isTrue();

        myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "deleteRefs");
        assertThat(myAndroidDriver.waitForInput("deleteRefs")).isTrue();

        boolean allRefsAccounted = false;
        int refsReported = 0;
        HashSet<Long> refs = new HashSet<>();
        HashSet<Integer> tags = new HashSet<>();

        long startTime = 0;
        while (!allRefsAccounted) {
            MemoryData jvmtiData =
                    stubWrapper.getJvmtiData(mySession, startTime, Long.MAX_VALUE);
            System.out.printf(
                    "getJvmtiData, start time=%d, end time=%d, alloc entries=%d, jni entries=%d\n",
                    startTime,
                    jvmtiData.getEndTimestamp(),
                    jvmtiData.getBatchAllocationEventsCount(),
                    jvmtiData.getJniReferenceEventBatchesCount());

            for (BatchAllocationEvents sample : jvmtiData.getBatchAllocationEventsList()) {
                assertThat(sample.getTimestamp()).isGreaterThan(startTime);
                for (AllocationEvent event : sample.getEventsList()) {
                    if (event.getEventCase() == AllocationEvent.EventCase.ALLOC_DATA) {
                        AllocationEvent.Allocation alloc = event.getAllocData();
                        if (alloc.getClassTag() == testEntityId) {
                            tags.add(alloc.getTag());
                            System.out.printf(
                                    "Add obj tag: %d, %d\n", alloc.getTag(), event.getTimestamp());
                        }
                    }
                }
            }


            for (BatchJNIGlobalRefEvent batch : jvmtiData.getJniReferenceEventBatchesList()) {
                // the context list count would be larger than the jni events because we also have context
                // events from alloc/free batches, but we should always have a context of the same timestamp
                // as the batched jni ref event.
                BatchAllocationContexts contexts = jvmtiData.getBatchAllocationContextsList().stream()
                        .filter(context -> context.getTimestamp() == batch.getTimestamp()).findFirst().get();
                assertThat(batch.getTimestamp()).isGreaterThan(startTime);
                for (JNIGlobalReferenceEvent event : batch.getEventsList()) {
                    long refValue = event.getRefValue();
                    assertThat(event.getThreadId()).isGreaterThan(0);
                    if (event.getEventType() == JNIGlobalReferenceEvent.Type.CREATE_GLOBAL_REF
                            && tags.contains(event.getObjectTag())) {
                        System.out.printf(
                                "Add JNI ref: %d tag:%d %d\n",
                                refValue, event.getObjectTag(), event.getTimestamp());
                        String refRelatedOutput = String.format("JNI ref created %d", refValue);
                        assertThat(myAndroidDriver.waitForInput(refRelatedOutput)).isTrue();
                        assertThat(refs.add(refValue)).isTrue();
                        refsReported++;
                    }
                    if (event.getEventType() == JNIGlobalReferenceEvent.Type.DELETE_GLOBAL_REF
                            // Test that reference value was reported when created
                            && refs.contains(refValue)) {
                        System.out.printf(
                                "Remove JNI ref: %d tag:%d %d\n",
                                refValue, event.getObjectTag(), event.getTimestamp());
                        String refRelatedOutput = String.format("JNI ref deleted %d", refValue);
                        assertThat(myAndroidDriver.waitForInput(refRelatedOutput)).isTrue();
                        assertThat(tags.contains(event.getObjectTag())).isTrue();
                        refs.remove(refValue);
                        if (refs.isEmpty() && refsReported == refCount) {
                            allRefsAccounted = true;
                        }
                    }
                    validateMemoryMap(contexts.getMemoryMap(), event.getBacktrace());
                }
            }

            startTime = Math.max(jvmtiData.getEndTimestamp(), startTime);
            Thread.sleep(RETRY_INTERVAL_MILLIS);
        }
        assertThat(allRefsAccounted).isTrue();
    }
}
