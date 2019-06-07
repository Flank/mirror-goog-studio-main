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

package com.android.tools.profiler.energy;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.fakeandroid.FakeAndroidDriver;
import com.android.tools.profiler.GrpcUtils;
import com.android.tools.profiler.PerfDriver;
import com.android.tools.profiler.TestUtils;
import com.android.tools.profiler.TransportStubWrapper;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Common.Session;
import com.android.tools.profiler.proto.Energy.EnergyEventData.MetadataCase;
import com.android.tools.profiler.proto.Energy.WakeLockAcquired.Level;
import com.android.tools.profiler.proto.Energy.WakeLockReleased.ReleaseFlag;
import com.android.tools.profiler.proto.EnergyProfiler.EnergyEventsResponse;
import java.util.*;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class WakeLockTest {
    @Parameters(name = "{index}: SdkLevel={0}, UnifiedPipeline={1}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {{26, false}, {26, true}, {28, false}, {28, true}});
    }

    private static final String ACTIVITY_CLASS = "com.activity.energy.WakeLockActivity";

    @Rule public final PerfDriver myPerfDriver;

    private boolean myIsUnifiedPipeline;
    private GrpcUtils myGrpc;
    private FakeAndroidDriver myAndroidDriver;
    private EnergyStubWrapper myEnergyWrapper;
    private TransportStubWrapper myTransportWrapper;
    private Session mySession;

    public WakeLockTest(int sdkLevel, boolean isUnifiedPipeline) {
        myPerfDriver = new PerfDriver(ACTIVITY_CLASS, sdkLevel, isUnifiedPipeline);
        myIsUnifiedPipeline = isUnifiedPipeline;
    }

    @Before
    public void setUp() throws Exception {
        myAndroidDriver = myPerfDriver.getFakeAndroidDriver();
        myGrpc = myPerfDriver.getGrpc();
        myEnergyWrapper = new EnergyStubWrapper(myGrpc.getEnergyStub());
        myTransportWrapper = new TransportStubWrapper(myGrpc.getTransportStub());
        mySession = myPerfDriver.getSession();
    }

    @Test
    public void testAcquireAndRelease() throws Exception {
        final String methodName = "runAcquireAndRelease";
        final String expectedResponse = "WAKE LOCK RELEASED";

        List<Common.Event> energyEvents = new ArrayList<>();
        if (myIsUnifiedPipeline) {
            Map<Long, List<Common.Event>> eventGroups =
                    myTransportWrapper.getEvents(
                            2,
                            event -> event.getKind() == Common.Event.Kind.ENERGY_EVENT,
                            (unused) -> triggerMethod(methodName, expectedResponse));
            for (List<Common.Event> eventList : eventGroups.values()) {
                energyEvents.addAll(eventList);
            }
        } else {
            triggerMethod(methodName, expectedResponse);
            EnergyEventsResponse response =
                    TestUtils.waitForAndReturn(
                            () -> myEnergyWrapper.getAllEnergyEvents(mySession),
                            resp -> resp.getEventsCount() == 2);
            energyEvents.addAll(response.getEventsList());
        }
        assertThat(energyEvents).hasSize(2);

        Common.Event acquiredEvent = energyEvents.get(0);
        assertThat(acquiredEvent.getTimestamp()).isGreaterThan(0L);
        assertThat(acquiredEvent.getPid()).isEqualTo(mySession.getPid());
        assertThat(acquiredEvent.getGroupId()).isGreaterThan(0L);
        assertThat(acquiredEvent.getIsEnded()).isFalse();
        assertThat(acquiredEvent.getEnergyEvent().getMetadataCase())
                .isEqualTo(MetadataCase.WAKE_LOCK_ACQUIRED);
        assertThat(acquiredEvent.getEnergyEvent().getWakeLockAcquired().getLevel())
                .isEqualTo(Level.PARTIAL_WAKE_LOCK);
        assertThat(acquiredEvent.getEnergyEvent().getWakeLockAcquired().getFlagsCount())
                .isEqualTo(0);
        assertThat(acquiredEvent.getEnergyEvent().getWakeLockAcquired().getTag()).isEqualTo("Bar");
        assertThat(acquiredEvent.getEnergyEvent().getWakeLockAcquired().getTimeout())
                .isEqualTo(1000);

        Common.Event releasedEvent = energyEvents.get(1);
        assertThat(releasedEvent.getTimestamp()).isAtLeast(acquiredEvent.getTimestamp());
        assertThat(releasedEvent.getPid()).isEqualTo(mySession.getPid());
        assertThat(releasedEvent.getGroupId()).isEqualTo(acquiredEvent.getGroupId());
        assertThat(releasedEvent.getIsEnded()).isTrue();
        assertThat(releasedEvent.getEnergyEvent().getCallstack()).contains(ACTIVITY_CLASS);
        assertThat(releasedEvent.getEnergyEvent().getMetadataCase())
                .isEqualTo(MetadataCase.WAKE_LOCK_RELEASED);
        assertThat(releasedEvent.getEnergyEvent().getWakeLockReleased().getFlagsList())
                .containsExactly(ReleaseFlag.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY);
    }

    /**
     * If wake lock creation happens before profiler is attached, verify that we still get
     * WakeLockAcquired events.
     */
    @Test
    public void testAcquireWithoutNewWakeLock() throws Exception {
        final String methodName = "runAcquireWithoutNewWakeLock";
        final String expectedResponse = "WAKE LOCK ACQUIRED";

        List<Common.Event> energyEvents = new ArrayList<>();
        if (myIsUnifiedPipeline) {
            Map<Long, List<Common.Event>> eventGroups =
                    myTransportWrapper.getEvents(
                            1,
                            event -> event.getKind() == Common.Event.Kind.ENERGY_EVENT,
                            (unused) -> triggerMethod(methodName, expectedResponse));
            for (List<Common.Event> eventList : eventGroups.values()) {
                energyEvents.addAll(eventList);
            }
        } else {
            triggerMethod(methodName, expectedResponse);
            EnergyEventsResponse response =
                    TestUtils.waitForAndReturn(
                            () -> myEnergyWrapper.getAllEnergyEvents(mySession),
                            resp -> resp.getEventsCount() == 1);
            energyEvents.addAll(response.getEventsList());
        }
        assertThat(energyEvents).hasSize(1);

        Common.Event acquiredEvent = energyEvents.get(0);
        assertThat(acquiredEvent.getTimestamp()).isGreaterThan(0L);
        assertThat(acquiredEvent.getPid()).isEqualTo(mySession.getPid());
        assertThat(acquiredEvent.getGroupId()).isGreaterThan(0L);
        assertThat(acquiredEvent.getIsEnded()).isFalse();
        assertThat(acquiredEvent.getEnergyEvent().getMetadataCase())
                .isEqualTo(MetadataCase.WAKE_LOCK_ACQUIRED);
        assertThat(acquiredEvent.getEnergyEvent().getWakeLockAcquired().getLevel())
                .isEqualTo(Level.PARTIAL_WAKE_LOCK);
        assertThat(acquiredEvent.getEnergyEvent().getWakeLockAcquired().getFlagsCount())
                .isEqualTo(0);
        assertThat(acquiredEvent.getEnergyEvent().getWakeLockAcquired().getTag()).isEqualTo("Foo");
        assertThat(acquiredEvent.getEnergyEvent().getWakeLockAcquired().getTimeout()).isEqualTo(0);
    }

    private void triggerMethod(String methodName, String expectedResponse) {
        myAndroidDriver.triggerMethod(ACTIVITY_CLASS, methodName);
        assertThat(myAndroidDriver.waitForInput(expectedResponse)).isTrue();
    }
}
