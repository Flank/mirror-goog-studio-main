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
import com.android.tools.profiler.proto.MemoryProfiler.*;
import java.util.HashSet;
import java.util.List;
import org.junit.Test;

public class JniTest {

    private boolean myIsOPlusDevice = true;
    private static final String ACTIVITY_CLASS = "com.activity.NativeCodeActivity";

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

    // Just create native activity and see that it can load native library.
    @Test
    public void countCreatedAndDeleteRefEvents() throws Exception {
        PerfDriver driver = new PerfDriver(myIsOPlusDevice);
        driver.start(ACTIVITY_CLASS);
        GrpcUtils grpc = driver.getGrpc();
        MemoryStubWrapper stubWrapper = new MemoryStubWrapper(grpc.getMemoryStub());
        FakeAndroidDriver androidDriver = driver.getFakeAndroidDriver();

        // Start memory tracking.
        TrackAllocationsResponse trackResponse =
                stubWrapper.startAllocationTracking(grpc.getProcessId());
        assertThat(trackResponse.getStatus()).isEqualTo(TrackAllocationsResponse.Status.SUCCESS);
        MemoryData jvmtiData = stubWrapper.getJvmtiData(grpc.getProcessId(), 0, Long.MAX_VALUE);

        // Wait before we start getting acutal data.
        while (jvmtiData.getAllocationSamplesList().size() == 0) {
            jvmtiData = stubWrapper.getJvmtiData(grpc.getProcessId(), 0, Long.MAX_VALUE);
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
        int maxLoopCount = refCount * 100;

        // Aborts if we loop too many times and somehow didn't get
        // back jni ref events. This way the test won't timeout
        // even when fails.
        while (!allRefsAccounted && maxLoopCount-- > 0) {
            jvmtiData = stubWrapper.getJvmtiData(grpc.getProcessId(), startTime, Long.MAX_VALUE);
            long endTime = jvmtiData.getEndTimestamp();

            for (BatchAllocationSample sample : jvmtiData.getAllocationSamplesList()) {
                assertThat(sample.getTimestamp()).isGreaterThan(startTime);
                for (AllocationEvent event : sample.getEventsList()) {
                    if (event.getEventCase() == AllocationEvent.EventCase.ALLOC_DATA) {
                        AllocationEvent.Allocation alloc = event.getAllocData();
                        if (alloc.getClassTag() == testEntityId) {
                            tags.add(alloc.getTag());
                        }
                    } else if (event.getEventCase() == AllocationEvent.EventCase.FREE_DATA) {
                        AllocationEvent.Deallocation dealloc = event.getFreeData();
                        if (tags.contains(dealloc.getTag())) {
                            tags.remove(dealloc.getTag());
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
                            String refRelatedOutput = String.format("JNI ref created %d", refValue);
                            assertThat(androidDriver.waitForInput(refRelatedOutput)).isTrue();
                            assertThat(refs.add(refValue)).isTrue();
                            refsReported++;
                        }
                    }
                    if (event.getEventType() == JNIGlobalReferenceEvent.Type.DELETE_GLOBAL_REF) {
                        // Test that reference value was reported when created
                        if (refs.contains(refValue)) {
                            String refRelatedOutput = String.format("JNI ref deleted %d", refValue);
                            assertThat(androidDriver.waitForInput(refRelatedOutput)).isTrue();
                            assertThat(tags.contains(event.getObjectTag())).isTrue();

                            refs.remove(refValue);
                            if (refs.isEmpty() && refsReported == refCount) {
                                allRefsAccounted = true;
                            }
                        }
                    }
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
