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

import com.android.tools.fakeandroid.FakeAndroidDriver;
import com.android.tools.profiler.GrpcUtils;
import com.android.tools.profiler.PerfDriver;
import com.android.tools.profiler.TransportStubWrapper;
import com.android.tools.profiler.proto.Commands;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Memory;
import com.android.tools.profiler.proto.Memory.AllocationEvent;
import com.android.tools.profiler.proto.Transport;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.HashSet;
import java.util.concurrent.CountDownLatch;

import static com.android.tools.profiler.memory.UnifiedPipelineMemoryTestUtils.findClassTag;
import static com.android.tools.profiler.memory.UnifiedPipelineMemoryTestUtils.startAllocationTracking;
import static com.google.common.truth.Truth.assertThat;

public class UnifiedPipelineMemoryTest {
    private static final String ACTIVITY_CLASS = "com.activity.MemoryActivity";
    private static final int SAMPLING_RATE_FULL = 1;

    // We currently only test O+ test scenarios.
    @Rule
    public PerfDriver myPerfDriver = new PerfDriver(ACTIVITY_CLASS, 26, true);
    private GrpcUtils myGrpc;
    private TransportStubWrapper myTransportWrapper;
    private FakeAndroidDriver myAndroidDriver;

    @Before
    public void setup() {
        myGrpc = myPerfDriver.getGrpc();
        myAndroidDriver = myPerfDriver.getFakeAndroidDriver();
        myTransportWrapper = new TransportStubWrapper(myGrpc.getTransportAsyncStub());
    }

    @Test
    public void countAllocationsAndDeallocation() throws Exception {
        // Find MemTestEntity class tag
        int[] memTestEntityIdFinal = new int[1];
        myTransportWrapper.getEvents(
                event -> {
                    int id = findClassTag(event.getMemoryAllocContexts().getContexts(), "MemTestEntity");
                    memTestEntityIdFinal[0] = id;
                    return id != 0;
                },
                event -> event.getKind() == Common.Event.Kind.MEMORY_ALLOC_CONTEXTS,
                (unused) -> startAllocationTracking(myPerfDriver));
        int memTestEntityId = memTestEntityIdFinal[0];
        assertThat(memTestEntityId).isNotEqualTo(0);

        final int allocationCount = 10;
        HashSet<Integer> allocTags = new HashSet<Integer>();
        HashSet<Integer> deallocTags = new HashSet<Integer>();

        CountDownLatch gcLatch = new CountDownLatch(1);
        myTransportWrapper.getEvents(
                event -> {
                    for (AllocationEvent evt :
                            event.getMemoryAllocEvents().getEvents().getEventsList()) {
                        if (evt.getEventCase() == AllocationEvent.EventCase.ALLOC_DATA) {
                            AllocationEvent.Allocation alloc = evt.getAllocData();
                            assertThat(alloc.getThreadId()).isGreaterThan(0);
                            if (alloc.getClassTag() == memTestEntityId) {
                                System.out.printf("Alloc recorded: tag=%d\n", alloc.getTag());
                                assertThat(allocTags.add(alloc.getTag())).isTrue();
                            }
                        } else if (evt.getEventCase() == AllocationEvent.EventCase.FREE_DATA) {
                            AllocationEvent.Deallocation dealloc = evt.getFreeData();
                            if (allocTags.contains(dealloc.getTag())) {
                                System.out.printf("Free recorded: tag=%d\n", dealloc.getTag());
                                assertThat(deallocTags.add(dealloc.getTag())).isTrue();
                            }
                        }
                    }

                    return deallocTags.size() >= allocationCount;
                },
                event -> event.getKind() == Common.Event.Kind.MEMORY_ALLOC_EVENTS,
                (unused) -> {
                    // Create several instances of MemTestEntity and when done free and collect them.
                    myAndroidDriver.setProperty(
                            "allocation.count", Integer.toString(allocationCount));
                    myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "allocate");
                    assertThat(myAndroidDriver.waitForInput("allocation_count=" + allocationCount))
                            .isTrue();
                    myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "free");
                    assertThat(myAndroidDriver.waitForInput("free_count=0")).isTrue();
                    // Make some allocation noise here to emulate how real apps work.
                    myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "makeAllocationNoise");

                    new Thread(() -> {
                        while (gcLatch.getCount() > 0) {
                            // Trigger GCs in order for the free events to be triggered.
                            myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "gc");
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException ignored) {
                            }
                        }
                    }).start();
                });

        gcLatch.countDown();
        // allocationCount of instances should have been created/deleted.
        assertThat(allocTags).hasSize(allocationCount);
        assertThat(deallocTags).isEqualTo(allocTags);
    }

    @Test
    public void updateAllocationSamplingRate() throws Exception {
        myAndroidDriver = myPerfDriver.getFakeAndroidDriver();

        // Find MemTestEntity (index == 0) and MemNoiseEntity (index == 1) class tags
        int[] memTestEntityIdFinal = new int[2];
        boolean[] hasInitialSamplingRate = new boolean[1];
        myTransportWrapper.getEvents(
                event -> {
                    if (event.getKind() == Common.Event.Kind.MEMORY_ALLOC_CONTEXTS) {
                        if (memTestEntityIdFinal[0] == 0) {
                            memTestEntityIdFinal[0] =
                                    findClassTag(
                                            event.getMemoryAllocContexts().getContexts(),
                                            "MemTestEntity");
                        }
                        if (memTestEntityIdFinal[1] == 0) {
                            memTestEntityIdFinal[1] =
                                    findClassTag(
                                            event.getMemoryAllocContexts().getContexts(),
                                            "MemNoiseEntity");
                        }
                    } else if (event.getKind() == Common.Event.Kind.MEMORY_ALLOC_SAMPLING) {
                        assertThat(event.getMemoryAllocSampling().getSamplingNumInterval())
                                .isEqualTo(SAMPLING_RATE_FULL);
                        hasInitialSamplingRate[0] = true;
                    }
                    return memTestEntityIdFinal[0] != 0
                            && memTestEntityIdFinal[1] != 0
                            && hasInitialSamplingRate[0];
                },
                event ->
                        (event.getKind() == Common.Event.Kind.MEMORY_ALLOC_CONTEXTS
                                || event.getKind() == Common.Event.Kind.MEMORY_ALLOC_SAMPLING),
                (unused) -> startAllocationTracking(myPerfDriver));
        int memTestEntityId = memTestEntityIdFinal[0];
        int memNoiseEntityId = memTestEntityIdFinal[1];
        assertThat(memTestEntityId).isNotEqualTo(0);
        assertThat(memNoiseEntityId).isNotEqualTo(0);

        final int samplingNumInterval = 2;
        final int allocationCount = 10;
        HashSet<Integer> allocTags = new HashSet<>();
        HashSet<Integer> noiseAllocTags = new HashSet<>();

        // Set sampling rate and verify we receive an event for it.
        myTransportWrapper.getEvents(
                event -> event.getMemoryAllocSampling().getSamplingNumInterval() == samplingNumInterval,
                event -> event.getKind() == Common.Event.Kind.MEMORY_ALLOC_SAMPLING,
                (unused) -> {
                    myGrpc.getTransportStub().execute(
                            Transport.ExecuteRequest.newBuilder()
                                    .setCommand(Commands.Command.newBuilder()
                                            .setType(Commands.Command.CommandType.MEMORY_ALLOC_SAMPLING)
                                            .setPid(myPerfDriver.getSession().getPid())
                                            .setMemoryAllocSampling(
                                                    Memory.MemoryAllocSamplingData
                                                            .newBuilder()
                                                            .setSamplingNumInterval(samplingNumInterval)))
                                    .build());
                    // printf from the agent signalling that the sample rate has been updated.
                    assertThat(myAndroidDriver.waitForInput("Setting sampling rate")).isTrue();
                });

        // Verify allocation data are now sampled.
        myTransportWrapper.getEvents(
                // Continue until we see MemNoiseEntity since those are allocated after MemTestEntity
                event -> {
                    for (AllocationEvent evt :
                            event.getMemoryAllocEvents().getEvents().getEventsList()) {
                        if (evt.getEventCase() == AllocationEvent.EventCase.ALLOC_DATA) {
                            AllocationEvent.Allocation alloc = evt.getAllocData();
                            if (alloc.getClassTag() == memTestEntityId) {
                                assertThat(allocTags.add(alloc.getTag())).isTrue();
                            } else if (alloc.getClassTag() == memNoiseEntityId) {
                                noiseAllocTags.add(alloc.getTag());
                            }
                        }
                    }
                    return !noiseAllocTags.isEmpty();
                },
                event -> event.getKind() == Common.Event.Kind.MEMORY_ALLOC_EVENTS,
                (unused) -> {
                    // Create several instances of MemTestEntity.
                    myAndroidDriver.setProperty(
                            "allocation.count", Integer.toString(allocationCount));
                    myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "allocate");
                    assertThat(myAndroidDriver.waitForInput("allocation_count=" + allocationCount))
                            .isTrue();
                    // Make some allocation noise so we count MemTestEntity correctly.
                    myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "makeAllocationNoise");
                });

        // If other objects are allocated between the MemTestEntity objects, we may get more than
        // (allocationCount / samplingNumInterval) MemTestEntity objects. So we just need to verify
        // that the number of tagged MemTestEntity is less than total. There is a chance that all
        // MemTestEntity objects happen to be selected as samples but that should be extremely rare.
        assertThat(allocTags.size()).isGreaterThan(0);
        assertThat(allocTags.size()).isAtMost(allocationCount);

        // Verify allocation data are now fully recorded.
        myTransportWrapper.getEvents(
                event -> {
                    for (AllocationEvent evt :
                            event.getMemoryAllocEvents().getEvents().getEventsList()) {
                        if (evt.getEventCase() == AllocationEvent.EventCase.ALLOC_DATA) {
                            assertThat(event.getTimestamp()).isGreaterThan(0L);
                            AllocationEvent.Allocation alloc = evt.getAllocData();
                            if (alloc.getClassTag() == memTestEntityId) {
                                System.out.println("Alloc recorded: tag=" + alloc.getTag());
                                assertThat(allocTags.add(alloc.getTag())).isTrue();
                            }
                        }
                    }
                    return allocTags.size() == allocationCount * 2;
                },
                event -> event.getKind() == Common.Event.Kind.MEMORY_ALLOC_EVENTS,
                (unused) -> {
                    // Set sampling rate back to full.
                    myGrpc.getTransportStub().execute(
                            Transport.ExecuteRequest.newBuilder()
                                    .setCommand(Commands.Command.newBuilder()
                                            .setType(Commands.Command.CommandType.MEMORY_ALLOC_SAMPLING)
                                            .setPid(myPerfDriver.getSession().getPid())
                                            .setMemoryAllocSampling(
                                                    Memory.MemoryAllocSamplingData
                                                            .newBuilder()
                                                            .setSamplingNumInterval(SAMPLING_RATE_FULL)))
                                    .build());
                    // printf from the agent signalling that the sample rate has been updated.
                    assertThat(myAndroidDriver.waitForInput("Setting sampling rate")).isTrue();

                    // Create more instances of MemTestEntity. We keep the previously allocated objects so we
                    // can verify a new heap walk was performed.
                    myAndroidDriver.setProperty(
                            "allocation.count", Integer.toString(allocationCount));
                    myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "allocate");
                    assertThat(
                            myAndroidDriver.waitForInput(
                                    "allocation_count=" + allocationCount * 2))
                            .isTrue();
                });

        // Verify allocation data are no longer sampled and we get what's already on the heap.
        assertThat(allocTags.size()).isEqualTo(allocationCount * 2);
    }
}
