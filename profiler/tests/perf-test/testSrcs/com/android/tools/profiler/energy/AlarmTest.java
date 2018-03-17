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
import com.android.tools.profiler.proto.EnergyProfiler.AlarmCancelled.CancelActionCase;
import com.android.tools.profiler.proto.EnergyProfiler.AlarmFired.FireActionCase;
import com.android.tools.profiler.proto.EnergyProfiler.AlarmSet.SetActionCase;
import com.android.tools.profiler.proto.EnergyProfiler.AlarmSet.Type;
import com.android.tools.profiler.proto.EnergyProfiler.EnergyEvent;
import com.android.tools.profiler.proto.EnergyProfiler.EnergyEvent.MetadataCase;
import com.android.tools.profiler.proto.EnergyProfiler.EnergyEventsResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AlarmTest {
    private static final String ACTIVITY_CLASS = "com.activity.energy.AlarmActivity";

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
        if (myPerfDriver != null) {
            if (mySession != null) {
                myGrpc.endSession(mySession.getSessionId());
            }
            myPerfDriver.tearDown();
        }
    }

    @Test
    public void testSetAndCancelIntentAlarm() throws Exception {
        myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "setAndCancelIntentAlarm");
        assertThat(myAndroidDriver.waitForInput("INTENT ALARM CANCELLED")).isTrue();

        EnergyEventsResponse response =
                TestUtils.waitForAndReturn(
                        () -> myStubWrapper.getAllEnergyEvents(mySession),
                        resp -> resp.getEventsCount() == 2);
        assertThat(response.getEventsCount()).isEqualTo(2);

        EnergyEvent setEvent = response.getEvents(0);
        assertThat(setEvent.getTimestamp()).isGreaterThan(0L);
        assertThat(setEvent.getPid()).isEqualTo(mySession.getPid());
        assertThat(setEvent.getEventId()).isGreaterThan(0);
        assertThat(setEvent.getIsTerminal()).isFalse();
        assertThat(setEvent.getMetadataCase()).isEqualTo(MetadataCase.ALARM_SET);
        assertThat(setEvent.getAlarmSet().getType()).isEqualTo(Type.RTC);
        assertThat(setEvent.getAlarmSet().getTriggerMs()).isEqualTo(1000);
        assertThat(setEvent.getAlarmSet().getWindowMs()).isEqualTo(-1);
        assertThat(setEvent.getAlarmSet().getIntervalMs()).isEqualTo(0);
        assertThat(setEvent.getAlarmSet().getSetActionCase()).isEqualTo(SetActionCase.OPERATION);
        assertThat(setEvent.getAlarmSet().getOperation().getCreatorPackage())
                .isEqualTo("com.example");
        assertThat(setEvent.getAlarmSet().getOperation().getCreatorUid()).isEqualTo(1);

        EnergyEvent cancelEvent = response.getEvents(1);
        assertThat(cancelEvent.getTimestamp()).isGreaterThan(0L);
        assertThat(cancelEvent.getPid()).isEqualTo(mySession.getPid());
        assertThat(cancelEvent.getEventId()).isEqualTo(setEvent.getEventId());
        assertThat(cancelEvent.getIsTerminal()).isTrue();
        assertThat(cancelEvent.getMetadataCase()).isEqualTo(MetadataCase.ALARM_CANCELLED);
        assertThat(cancelEvent.getAlarmCancelled().getCancelActionCase())
                .isEqualTo(CancelActionCase.OPERATION);
        assertThat(cancelEvent.getAlarmCancelled().getOperation().getCreatorPackage())
                .isEqualTo("com.example");
        assertThat(cancelEvent.getAlarmCancelled().getOperation().getCreatorUid()).isEqualTo(1);
    }

    @Test
    public void testSetAndCancelListenerAlarm() throws Exception {
        myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "setAndCancelListenerAlarm");
        assertThat(myAndroidDriver.waitForInput("LISTENER ALARM CANCELLED")).isTrue();

        EnergyEventsResponse response =
                TestUtils.waitForAndReturn(
                        () -> myStubWrapper.getAllEnergyEvents(mySession),
                        resp -> resp.getEventsCount() == 2);
        assertThat(response.getEventsCount()).isEqualTo(2);

        EnergyEvent setEvent = response.getEvents(0);
        assertThat(setEvent.getTimestamp()).isGreaterThan(0L);
        assertThat(setEvent.getPid()).isEqualTo(mySession.getPid());
        assertThat(setEvent.getEventId()).isGreaterThan(0);
        assertThat(setEvent.getIsTerminal()).isFalse();
        assertThat(setEvent.getMetadataCase()).isEqualTo(MetadataCase.ALARM_SET);
        assertThat(setEvent.getAlarmSet().getType()).isEqualTo(Type.RTC_WAKEUP);
        assertThat(setEvent.getAlarmSet().getTriggerMs()).isEqualTo(2000);
        assertThat(setEvent.getAlarmSet().getWindowMs()).isEqualTo(-1);
        assertThat(setEvent.getAlarmSet().getIntervalMs()).isEqualTo(0);
        assertThat(setEvent.getAlarmSet().getSetActionCase()).isEqualTo(SetActionCase.LISTENER);
        assertThat(setEvent.getAlarmSet().getListener().getTag()).isEqualTo("foo");

        EnergyEvent cancelEvent = response.getEvents(1);
        assertThat(cancelEvent.getEventId()).isEqualTo(setEvent.getEventId());
        assertThat(cancelEvent.getIsTerminal()).isTrue();
        assertThat(cancelEvent.getMetadataCase()).isEqualTo(MetadataCase.ALARM_CANCELLED);
        assertThat(cancelEvent.getAlarmCancelled().getCancelActionCase())
                .isEqualTo(CancelActionCase.LISTENER);
        assertThat(cancelEvent.getAlarmCancelled().getListener().getTag()).isEqualTo("foo");
    }

    @Test
    public void testSetAndFireListenerAlarm() throws Exception {
        myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "fireListenerAlarm");
        assertThat(myAndroidDriver.waitForInput("LISTENER ALARM FIRED")).isTrue();

        EnergyEventsResponse response =
                TestUtils.waitForAndReturn(
                        () -> myStubWrapper.getAllEnergyEvents(mySession),
                        resp -> resp.getEventsCount() == 2);
        assertThat(response.getEventsCount()).isEqualTo(2);

        EnergyEvent setEvent = response.getEvents(0);
        assertThat(setEvent.getEventId()).isGreaterThan(0);
        assertThat(setEvent.getIsTerminal()).isFalse();
        assertThat(setEvent.getMetadataCase()).isEqualTo(MetadataCase.ALARM_SET);
        assertThat(setEvent.getAlarmSet().getSetActionCase()).isEqualTo(SetActionCase.LISTENER);
        String tag = setEvent.getAlarmSet().getListener().getTag();

        EnergyEvent fireEvent = response.getEvents(1);
        assertThat(fireEvent.getEventId()).isEqualTo(setEvent.getEventId());
        assertThat(fireEvent.getIsTerminal()).isTrue();
        assertThat(fireEvent.getMetadataCase()).isEqualTo(MetadataCase.ALARM_FIRED);
        assertThat(fireEvent.getAlarmFired().getFireActionCase())
                .isEqualTo(FireActionCase.LISTENER);
        assertThat(fireEvent.getAlarmFired().getListener().getTag()).isEqualTo(tag);
    }
}
