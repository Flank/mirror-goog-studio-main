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

import com.android.tools.profiler.FakeAndroidDriver;
import com.android.tools.profiler.GrpcUtils;
import com.android.tools.profiler.PerfDriver;
import com.android.tools.profiler.TestUtils;
import com.android.tools.profiler.proto.Common.Session;
import com.android.tools.profiler.proto.EnergyProfiler.EnergyEvent;
import com.android.tools.profiler.proto.EnergyProfiler.EnergyEvent.MetadataCase;
import com.android.tools.profiler.proto.EnergyProfiler.EnergyEventsResponse;
import com.android.tools.profiler.proto.EnergyProfiler.WakeLockAcquired.Level;
import com.android.tools.profiler.proto.EnergyProfiler.WakeLockReleased.ReleaseFlag;
import java.util.Arrays;
import java.util.Collection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class WakeLockTest {
    @Parameters
    public static Collection<Integer> data() {
        return Arrays.asList(26, 28);
    }

    private static final String ACTIVITY_CLASS = "com.activity.energy.WakeLockActivity";

    private int mySdkLevel;
    private PerfDriver myPerfDriver;
    private GrpcUtils myGrpc;
    private FakeAndroidDriver myAndroidDriver;
    private EnergyStubWrapper myStubWrapper;
    private Session mySession;

    public WakeLockTest(int sdkLevel) {
        mySdkLevel = sdkLevel;
    }

    @Before
    public void setUp() throws Exception {
        myPerfDriver = new PerfDriver(mySdkLevel);
        myPerfDriver.start(ACTIVITY_CLASS);
        myAndroidDriver = myPerfDriver.getFakeAndroidDriver();
        myGrpc = myPerfDriver.getGrpc();
        myStubWrapper = new EnergyStubWrapper(myGrpc.getEnergyStub());
        mySession =
                myGrpc.beginSessionWithAgent(
                        myPerfDriver.getPid(), myPerfDriver.getCommunicationPort());
    }

    @After
    public void tearDown() throws Exception {
        if (myPerfDriver != null) {
            if (mySession != null) {
                myGrpc.endSession(mySession.getSessionId());
            }
            myPerfDriver.tearDown();
        }
    }

    @Test
    public void testAcquireAndRelease() throws Exception {
        myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "runAcquireAndRelease");
        assertThat(myAndroidDriver.waitForInput("WAKE LOCK RELEASED")).isTrue();

        EnergyEventsResponse response =
                TestUtils.waitForAndReturn(
                        () -> myStubWrapper.getAllEnergyEvents(mySession),
                        resp -> resp.getEventsCount() == 2);
        assertThat(response.getEventsCount()).isEqualTo(2);

        EnergyEvent acquiredEvent = response.getEvents(0);
        assertThat(acquiredEvent.getTimestamp()).isGreaterThan(0L);
        assertThat(acquiredEvent.getPid()).isEqualTo(mySession.getPid());
        assertThat(acquiredEvent.getEventId()).isGreaterThan(0);
        assertThat(acquiredEvent.getIsTerminal()).isFalse();
        assertThat(acquiredEvent.getMetadataCase()).isEqualTo(MetadataCase.WAKE_LOCK_ACQUIRED);
        assertThat(acquiredEvent.getWakeLockAcquired().getLevel())
                .isEqualTo(Level.PARTIAL_WAKE_LOCK);
        assertThat(acquiredEvent.getWakeLockAcquired().getFlagsCount()).isEqualTo(0);
        assertThat(acquiredEvent.getWakeLockAcquired().getTag()).isEqualTo("Bar");
        assertThat(acquiredEvent.getWakeLockAcquired().getTimeout()).isEqualTo(1000);

        EnergyEvent releasedEvent = response.getEvents(1);
        assertThat(releasedEvent.getTimestamp()).isAtLeast(acquiredEvent.getTimestamp());
        assertThat(releasedEvent.getPid()).isEqualTo(mySession.getPid());
        assertThat(releasedEvent.getEventId()).isEqualTo(acquiredEvent.getEventId());
        assertThat(releasedEvent.getIsTerminal()).isTrue();
        assertThat(releasedEvent.getMetadataCase()).isEqualTo(MetadataCase.WAKE_LOCK_RELEASED);
        assertThat(releasedEvent.getWakeLockReleased().getFlagsList())
                .containsExactly(ReleaseFlag.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY);

        String stack = TestUtils.getBytes(myGrpc, releasedEvent.getTraceId());
        assertThat(stack).contains(ACTIVITY_CLASS);
    }

    /**
     * If wake lock creation happens before profiler is attached, verify that we still get
     * WakeLockAcquired events.
     */
    @Test
    public void testAcquireWithoutNewWakeLock() throws Exception {
        myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "runAcquireWithoutNewWakeLock");
        assertThat(myAndroidDriver.waitForInput("WAKE LOCK ACQUIRED")).isTrue();

        EnergyEventsResponse response =
                TestUtils.waitForAndReturn(
                        () -> myStubWrapper.getAllEnergyEvents(mySession),
                        resp -> resp.getEventsCount() == 1);
        assertThat(response.getEventsCount()).isEqualTo(1);

        EnergyEvent acquiredEvent = response.getEvents(0);
        assertThat(acquiredEvent.getTimestamp()).isGreaterThan(0L);
        assertThat(acquiredEvent.getPid()).isEqualTo(mySession.getPid());
        assertThat(acquiredEvent.getEventId()).isGreaterThan(0);
        assertThat(acquiredEvent.getIsTerminal()).isFalse();
        assertThat(acquiredEvent.getMetadataCase()).isEqualTo(MetadataCase.WAKE_LOCK_ACQUIRED);
        assertThat(acquiredEvent.getWakeLockAcquired().getLevel())
                .isEqualTo(Level.PARTIAL_WAKE_LOCK);
        assertThat(acquiredEvent.getWakeLockAcquired().getFlagsCount()).isEqualTo(0);
        assertThat(acquiredEvent.getWakeLockAcquired().getTag()).isEqualTo("Foo");
        assertThat(acquiredEvent.getWakeLockAcquired().getTimeout()).isEqualTo(0);
    }
}
