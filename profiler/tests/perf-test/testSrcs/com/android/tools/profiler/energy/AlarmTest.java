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
import com.android.tools.profiler.ProfilerConfig;
import com.android.tools.profiler.ProfilerRule;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Common.Session;
import com.android.tools.profiler.proto.Energy.AlarmCancelled.CancelActionCase;
import com.android.tools.profiler.proto.Energy.AlarmFired.FireActionCase;
import com.android.tools.profiler.proto.Energy.AlarmSet.SetActionCase;
import com.android.tools.profiler.proto.Energy.AlarmSet.Type;
import com.android.tools.profiler.proto.Energy.EnergyEventData.MetadataCase;
import com.android.tools.profiler.proto.EnergyProfiler.EnergyEventsResponse;
import com.android.tools.transport.TestUtils;
import com.android.tools.transport.device.SdkLevel;
import com.android.tools.transport.grpc.Grpc;
import com.android.tools.transport.grpc.TransportAsyncStubWrapper;
import java.util.*;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class AlarmTest {
    @Parameters(name = "{index}: SdkLevel={0}, UnifiedPipeline={1}")
    public static Collection<Object[]> parameters() {
        return Arrays.asList(new Object[][]{
                {SdkLevel.O, false},
                {SdkLevel.O, true},
                {SdkLevel.P, false},
                {SdkLevel.P, true}
        });
    }

    private static final String ACTIVITY_CLASS = "com.activity.energy.AlarmActivity";

    @Rule public final ProfilerRule myProfilerRule;

    private boolean myIsUnifiedPipeline;
    private Grpc myGrpc;
    private FakeAndroidDriver myAndroidDriver;
    private Session mySession;

    public AlarmTest(SdkLevel sdkLevel, boolean isUnifiedPipeline) {
        myIsUnifiedPipeline = isUnifiedPipeline;

        myProfilerRule =
                new ProfilerRule(
                        ACTIVITY_CLASS,
                        sdkLevel,
                        new ProfilerConfig() {
                            @Override
                            public boolean usesUnifiedPipeline() {
                                return isUnifiedPipeline;
                            }
                        });
    }

    @Before
    public void setUp() {
        myAndroidDriver = myProfilerRule.getTransportRule().getAndroidDriver();
        myGrpc = myProfilerRule.getTransportRule().getGrpc();
        mySession = myProfilerRule.getSession();
    }

    @Test
    public void testSetAndCancelIntentAlarm() {
        final String methodName = "setAndCancelIntentAlarm";
        final String expectedResponse = "INTENT ALARM CANCELLED";

        TransportAsyncStubWrapper transportWrapper = TransportAsyncStubWrapper.create(myGrpc);
        EnergyStubWrapper energyWrapper = EnergyStubWrapper.create(myGrpc);

        List<Common.Event> energyEvents = new ArrayList<>();
        if (myIsUnifiedPipeline) {
            Map<Long, List<Common.Event>> eventGroups =
                    transportWrapper.getEvents(
                            2,
                            event -> event.getKind() == Common.Event.Kind.ENERGY_EVENT,
                            () -> triggerMethod(methodName, expectedResponse));
            for (List<Common.Event> eventList : eventGroups.values()) {
                energyEvents.addAll(eventList);
            }
        } else {
            triggerMethod(methodName, expectedResponse);
            EnergyEventsResponse response =
                    TestUtils.waitForAndReturn(
                            () -> energyWrapper.getAllEnergyEvents(mySession),
                            resp -> resp.getEventsCount() == 2,
                            20);
            energyEvents.addAll(response.getEventsList());
        }
        assertThat(energyEvents).hasSize(2);

        Common.Event setEvent = energyEvents.get(0);
        assertThat(setEvent.getTimestamp()).isGreaterThan(0L);
        assertThat(setEvent.getPid()).isEqualTo(mySession.getPid());
        assertThat(setEvent.getGroupId()).isGreaterThan(0L);
        assertThat(setEvent.getIsEnded()).isFalse();
        assertThat(setEvent.getEnergyEvent().getCallstack()).contains(methodName);
        assertThat(setEvent.getEnergyEvent().getMetadataCase()).isEqualTo(MetadataCase.ALARM_SET);
        assertThat(setEvent.getEnergyEvent().getAlarmSet().getType())
                .isEqualTo(Type.ELAPSED_REALTIME_WAKEUP);
        assertThat(setEvent.getEnergyEvent().getAlarmSet().getTriggerMs()).isEqualTo(1000);
        assertThat(setEvent.getEnergyEvent().getAlarmSet().getWindowMs()).isEqualTo(-1);
        assertThat(setEvent.getEnergyEvent().getAlarmSet().getIntervalMs()).isEqualTo(0);
        assertThat(setEvent.getEnergyEvent().getAlarmSet().getSetActionCase())
                .isEqualTo(SetActionCase.OPERATION);
        assertThat(setEvent.getEnergyEvent().getAlarmSet().getOperation().getCreatorPackage())
                .isEqualTo("com.example");
        assertThat(setEvent.getEnergyEvent().getAlarmSet().getOperation().getCreatorUid())
                .isEqualTo(1);

        Common.Event cancelEvent = energyEvents.get(1);
        assertThat(cancelEvent.getTimestamp()).isAtLeast(setEvent.getTimestamp());
        assertThat(cancelEvent.getPid()).isEqualTo(mySession.getPid());
        assertThat(cancelEvent.getGroupId()).isEqualTo(setEvent.getGroupId());
        assertThat(cancelEvent.getIsEnded()).isTrue();
        assertThat(cancelEvent.getEnergyEvent().getCallstack()).contains(methodName);
        assertThat(cancelEvent.getEnergyEvent().getMetadataCase())
                .isEqualTo(MetadataCase.ALARM_CANCELLED);
        assertThat(cancelEvent.getEnergyEvent().getAlarmCancelled().getCancelActionCase())
                .isEqualTo(CancelActionCase.OPERATION);
        assertThat(
                        cancelEvent
                                .getEnergyEvent()
                                .getAlarmCancelled()
                                .getOperation()
                                .getCreatorPackage())
                .isEqualTo("com.example");
        assertThat(cancelEvent.getEnergyEvent().getAlarmCancelled().getOperation().getCreatorUid())
                .isEqualTo(1);
    }

    @Test
    public void testSetAndCancelListenerAlarm() {
        final String methodName = "setAndCancelListenerAlarm";
        final String expectedResponse = "LISTENER ALARM CANCELLED";

        TransportAsyncStubWrapper transportWrapper = TransportAsyncStubWrapper.create(myGrpc);
        EnergyStubWrapper energyWrapper = EnergyStubWrapper.create(myGrpc);

        List<Common.Event> energyEvents = new ArrayList<>();
        if (myIsUnifiedPipeline) {
            Map<Long, List<Common.Event>> eventGroups =
                    transportWrapper.getEvents(
                            2,
                            event -> event.getKind() == Common.Event.Kind.ENERGY_EVENT,
                            () -> triggerMethod(methodName, expectedResponse));
            for (List<Common.Event> eventList : eventGroups.values()) {
                energyEvents.addAll(eventList);
            }
        } else {
            triggerMethod(methodName, expectedResponse);
            EnergyEventsResponse response =
                    TestUtils.waitForAndReturn(
                            () -> energyWrapper.getAllEnergyEvents(mySession),
                            resp -> resp.getEventsCount() == 2);
            energyEvents.addAll(response.getEventsList());
        }
        assertThat(energyEvents).hasSize(2);

        Common.Event setEvent = energyEvents.get(0);
        assertThat(setEvent.getTimestamp()).isGreaterThan(0L);
        assertThat(setEvent.getPid()).isEqualTo(mySession.getPid());
        assertThat(setEvent.getGroupId()).isGreaterThan(0L);
        assertThat(setEvent.getIsEnded()).isFalse();
        assertThat(setEvent.getEnergyEvent().getCallstack()).contains(methodName);
        assertThat(setEvent.getEnergyEvent().getMetadataCase()).isEqualTo(MetadataCase.ALARM_SET);
        assertThat(setEvent.getEnergyEvent().getAlarmSet().getType()).isEqualTo(Type.RTC_WAKEUP);
        assertThat(setEvent.getEnergyEvent().getAlarmSet().getTriggerMs()).isEqualTo(2000);
        assertThat(setEvent.getEnergyEvent().getAlarmSet().getWindowMs()).isEqualTo(-1);
        assertThat(setEvent.getEnergyEvent().getAlarmSet().getIntervalMs()).isEqualTo(0);
        assertThat(setEvent.getEnergyEvent().getAlarmSet().getSetActionCase())
                .isEqualTo(SetActionCase.LISTENER);
        assertThat(setEvent.getEnergyEvent().getAlarmSet().getListener().getTag()).isEqualTo("foo");

        Common.Event cancelEvent = energyEvents.get(1);
        assertThat(cancelEvent.getTimestamp()).isAtLeast(setEvent.getTimestamp());
        assertThat(cancelEvent.getGroupId()).isEqualTo(setEvent.getGroupId());
        assertThat(cancelEvent.getIsEnded()).isTrue();
        assertThat(cancelEvent.getEnergyEvent().getCallstack()).contains(methodName);
        assertThat(cancelEvent.getEnergyEvent().getMetadataCase())
                .isEqualTo(MetadataCase.ALARM_CANCELLED);
        assertThat(cancelEvent.getEnergyEvent().getAlarmCancelled().getCancelActionCase())
                .isEqualTo(CancelActionCase.LISTENER);
        assertThat(cancelEvent.getEnergyEvent().getAlarmCancelled().getListener().getTag())
                .isEqualTo("foo");
    }

    @Test
    public void testSetAndFireIntentAlarm() {
        String methodName = "fireIntentAlarm";
        String expectedResponse = "INTENT ALARM FIRED";

        TransportAsyncStubWrapper transportWrapper = TransportAsyncStubWrapper.create(myGrpc);
        EnergyStubWrapper energyWrapper = EnergyStubWrapper.create(myGrpc);

        List<Common.Event> energyEvents = new ArrayList<>();
        if (myIsUnifiedPipeline) {
            Map<Long, List<Common.Event>> eventGroups =
                    transportWrapper.getEvents(
                            2,
                            event -> event.getKind() == Common.Event.Kind.ENERGY_EVENT,
                            () -> triggerMethod(methodName, expectedResponse));
            for (List<Common.Event> eventList : eventGroups.values()) {
                energyEvents.addAll(eventList);
            }
        } else {
            triggerMethod(methodName, expectedResponse);
            EnergyEventsResponse response =
                    TestUtils.waitForAndReturn(
                            () -> energyWrapper.getAllEnergyEvents(mySession),
                            resp -> resp.getEventsCount() == 2);
            assertThat(response.getEventsCount()).isEqualTo(2);
            energyEvents.addAll(response.getEventsList());
        }

        Common.Event setEvent = energyEvents.get(0);
        assertThat(setEvent.getTimestamp()).isGreaterThan(0L);
        assertThat(setEvent.getGroupId()).isGreaterThan(0L);
        assertThat(setEvent.getIsEnded()).isFalse();
        assertThat(setEvent.getEnergyEvent().getMetadataCase()).isEqualTo(MetadataCase.ALARM_SET);
        assertThat(setEvent.getEnergyEvent().getAlarmSet().getSetActionCase())
                .isEqualTo(SetActionCase.OPERATION);

        Common.Event fireEvent = energyEvents.get(1);
        assertThat(fireEvent.getTimestamp()).isAtLeast(setEvent.getTimestamp());
        assertThat(fireEvent.getGroupId()).isEqualTo(setEvent.getGroupId());
        assertThat(fireEvent.getIsEnded()).isFalse();
        assertThat(fireEvent.getEnergyEvent().getMetadataCase())
                .isEqualTo(MetadataCase.ALARM_FIRED);
        assertThat(fireEvent.getEnergyEvent().getAlarmFired().getFireActionCase())
                .isEqualTo(FireActionCase.OPERATION);
        assertThat(fireEvent.getEnergyEvent().getAlarmFired().getOperation().getCreatorPackage())
                .isEqualTo("foo.bar");
        assertThat(fireEvent.getEnergyEvent().getAlarmFired().getOperation().getCreatorUid())
                .isEqualTo(2);
    }

    @Test
    public void testSetAndFireListenerAlarm() {
        final String methodName = "fireListenerAlarm";
        final String expectedResponse = "LISTENER ALARM FIRED";

        TransportAsyncStubWrapper transportWrapper = TransportAsyncStubWrapper.create(myGrpc);
        EnergyStubWrapper energyWrapper = EnergyStubWrapper.create(myGrpc);

        List<Common.Event> energyEvents = new ArrayList<>();
        if (myIsUnifiedPipeline) {
            Map<Long, List<Common.Event>> eventGroups =
                    transportWrapper.getEvents(
                            2,
                            event -> event.getKind() == Common.Event.Kind.ENERGY_EVENT,
                            () -> triggerMethod(methodName, expectedResponse));
            for (List<Common.Event> eventList : eventGroups.values()) {
                energyEvents.addAll(eventList);
            }
        } else {
            triggerMethod(methodName, expectedResponse);
            EnergyEventsResponse response =
                    TestUtils.waitForAndReturn(
                            () -> energyWrapper.getAllEnergyEvents(mySession),
                            resp -> resp.getEventsCount() == 2);
            energyEvents.addAll(response.getEventsList());
        }
        assertThat(energyEvents).hasSize(2);

        Common.Event setEvent = energyEvents.get(0);
        assertThat(setEvent.getTimestamp()).isGreaterThan(0L);
        assertThat(setEvent.getGroupId()).isGreaterThan(0L);
        assertThat(setEvent.getIsEnded()).isFalse();
        assertThat(setEvent.getEnergyEvent().getMetadataCase()).isEqualTo(MetadataCase.ALARM_SET);
        assertThat(setEvent.getEnergyEvent().getAlarmSet().getSetActionCase())
                .isEqualTo(SetActionCase.LISTENER);
        String tag = setEvent.getEnergyEvent().getAlarmSet().getListener().getTag();

        Common.Event fireEvent = energyEvents.get(1);
        assertThat(fireEvent.getTimestamp()).isAtLeast(setEvent.getTimestamp());
        assertThat(fireEvent.getGroupId()).isEqualTo(setEvent.getGroupId());
        assertThat(fireEvent.getIsEnded()).isTrue();
        assertThat(fireEvent.getEnergyEvent().getMetadataCase())
                .isEqualTo(MetadataCase.ALARM_FIRED);
        assertThat(fireEvent.getEnergyEvent().getAlarmFired().getFireActionCase())
                .isEqualTo(FireActionCase.LISTENER);
        assertThat(fireEvent.getEnergyEvent().getAlarmFired().getListener().getTag())
                .isEqualTo(tag);
    }

    @Test
    public void testSetAndCancelNonWakeupAlarm() {
        // TODO: figure out how to test this case on the streaming RPC in unified pipeline.
        Assume.assumeFalse(myIsUnifiedPipeline);

        EnergyStubWrapper energyWrapper = EnergyStubWrapper.create(myGrpc);

        myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "setAndCancelNonWakeupAlarm");
        assertThat(myAndroidDriver.waitForInput("NON-WAKEUP ALARM")).isTrue();

        EnergyEventsResponse response = energyWrapper.getAllEnergyEvents(mySession);
        assertThat(response.getEventsCount()).isEqualTo(0);
    }

    private void triggerMethod(String methodName, String expectedResponse) {
        myAndroidDriver.triggerMethod(ACTIVITY_CLASS, methodName);
        assertThat(myAndroidDriver.waitForInput(expectedResponse)).isTrue();
    }
}
