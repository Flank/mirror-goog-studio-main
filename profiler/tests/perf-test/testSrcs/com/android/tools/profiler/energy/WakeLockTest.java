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
import com.android.tools.profiler.proto.Common.Session;
import com.android.tools.profiler.proto.EnergyProfiler.EnergyEvent;
import com.android.tools.profiler.proto.EnergyProfiler.EnergyEvent.MetadataCase;
import com.android.tools.profiler.proto.EnergyProfiler.EnergyEventsResponse;
import com.android.tools.profiler.proto.EnergyProfiler.WakeLockAcquired.CreationFlag;
import com.android.tools.profiler.proto.EnergyProfiler.WakeLockAcquired.Level;
import com.android.tools.profiler.proto.EnergyProfiler.WakeLockReleased.ReleaseFlag;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class WakeLockTest {
    private static final String ACTIVITY_CLASS = "com.activity.energy.WakeLockActivity";
    private static final int RETRY_INTERVAL_MILLIS = 200;
    private static final int RETRY_LIMIT = 10;

    private PerfDriver myPerfDriver;
    private GrpcUtils myGrpc;
    private FakeAndroidDriver myAndroidDriver;
    private EnergyStubWrapper myStubWrapper;
    private Session mySession;

    @Before
    public void setUp() throws Exception {
        myPerfDriver = new PerfDriver(true);
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
        myGrpc.endSession(mySession.getSessionId());
        myPerfDriver.tearDown();
    }

    @Test
    public void testAcquire() throws Exception {
        myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "runAcquire");
        assertThat(myAndroidDriver.waitForInput("WAKE LOCK ACQUIRED")).isTrue();

        EnergyEventsResponse response = myStubWrapper.getAllEnergyEvents(mySession);
        assertThat(response.getEventsCount()).isEqualTo(1);

        EnergyEvent energyEvent = response.getEvents(0);
        assertThat(energyEvent.getTimestamp()).isGreaterThan(0L);
        assertThat(energyEvent.getPid()).isEqualTo(mySession.getPid());
        assertThat(energyEvent.getEventId()).isGreaterThan(0);
        assertThat(energyEvent.getMetadataCase()).isEqualTo(MetadataCase.WAKE_LOCK_ACQUIRED);
        assertThat(energyEvent.getWakeLockAcquired().getLevel())
                .isEqualTo(Level.SCREEN_DIM_WAKE_LOCK);
        assertThat(energyEvent.getWakeLockAcquired().getFlagsList())
                .containsExactly(CreationFlag.ACQUIRE_CAUSES_WAKEUP, CreationFlag.ON_AFTER_RELEASE);
        assertThat(energyEvent.getWakeLockAcquired().getTag()).isEqualTo("Foo");
        assertThat(energyEvent.getWakeLockAcquired().getTimeout()).isEqualTo(0);
    }

    @Test
    public void testRelease() throws Exception {
        myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "runAcquireAndRelease");
        assertThat(myAndroidDriver.waitForInput("WAKE LOCK RELEASED")).isTrue();

        EnergyEventsResponse response =
                waitForAndReturn(
                        () -> myStubWrapper.getAllEnergyEvents(mySession),
                        resp -> resp.getEventsCount() == 2);
        assertThat(response.getEventsCount()).isEqualTo(2);
        List<EnergyEvent> sortedEnergyEvents =
                response.getEventsList()
                        .stream()
                        .sorted((o1, o2) -> o1.getMetadataCase().compareTo(o2.getMetadataCase()))
                        .collect(Collectors.toList());

        EnergyEvent acquiredEvent = sortedEnergyEvents.get(0);
        assertThat(acquiredEvent.getTimestamp()).isGreaterThan(0L);
        assertThat(acquiredEvent.getPid()).isEqualTo(mySession.getPid());
        assertThat(acquiredEvent.getEventId()).isGreaterThan(0);
        assertThat(acquiredEvent.getMetadataCase()).isEqualTo(MetadataCase.WAKE_LOCK_ACQUIRED);
        assertThat(acquiredEvent.getWakeLockAcquired().getLevel())
                .isEqualTo(Level.PARTIAL_WAKE_LOCK);
        assertThat(acquiredEvent.getWakeLockAcquired().getFlagsCount()).isEqualTo(0);
        assertThat(acquiredEvent.getWakeLockAcquired().getTag()).isEqualTo("Bar");
        assertThat(acquiredEvent.getWakeLockAcquired().getTimeout()).isEqualTo(1000);

        EnergyEvent releasedEvent = sortedEnergyEvents.get(1);
        assertThat(releasedEvent.getTimestamp()).isGreaterThan(0L);
        assertThat(releasedEvent.getPid()).isEqualTo(mySession.getPid());
        assertThat(releasedEvent.getEventId()).isEqualTo(acquiredEvent.getEventId());
        assertThat(releasedEvent.getMetadataCase()).isEqualTo(MetadataCase.WAKE_LOCK_RELEASED);
        assertThat(releasedEvent.getWakeLockReleased().getFlagsList())
                .containsExactly(ReleaseFlag.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY);
        assertThat(releasedEvent.getWakeLockReleased().getIsHeld()).isTrue();
    }

    /**
     * Run the loop and wait until condition is true or the retry limit is reached. Returns the
     * result afterwards.
     *
     * @param supplier a function that returns the desired result.
     * @param condition tests whether the result is desired.
     * @param <T> type of the desired result.
     * @return the result from the last run (condition met or timeout).
     */
    private static <T> T waitForAndReturn(Supplier<T> supplier, Predicate<T> condition) {
        T result = supplier.get();
        int count = 0;
        while (!condition.test(result) && count < RETRY_LIMIT) {
            System.out.println("Retrying condition");
            ++count;
            try {
                Thread.sleep(RETRY_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                e.printStackTrace(System.out);
                break;
            }
            result = supplier.get();
        }
        return result;
    }
}
