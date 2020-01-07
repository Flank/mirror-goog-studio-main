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
import com.android.tools.profiler.proto.Energy.*;
import com.android.tools.profiler.proto.Energy.EnergyEventData.MetadataCase;
import com.android.tools.profiler.proto.Energy.LocationRequest.Priority;
import com.android.tools.profiler.proto.EnergyProfiler.EnergyEventsResponse;
import com.android.tools.transport.TestUtils;
import com.android.tools.transport.device.SdkLevel;
import com.android.tools.transport.grpc.Grpc;
import com.android.tools.transport.grpc.TransportAsyncStubWrapper;
import java.util.*;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class LocationTest {
    @Parameters(name = "{index}: SdkLevel={0}, UnifiedPipeline={1}")
    public static Collection<Object[]> parameters() {
        return Arrays.asList(new Object[][]{
                {SdkLevel.O, false},
                {SdkLevel.O, true},
                {SdkLevel.P, false},
                {SdkLevel.P, true}
        });
    }

    private static final String ACTIVITY_CLASS = "com.activity.energy.LocationActivity";
    private static final float EPSILON = 0.0001f;

    @Rule public final ProfilerRule myProfilerRule;

    private boolean myIsUnifiedPipeline;
    private Grpc myGrpc;
    private FakeAndroidDriver myAndroidDriver;
    private Session mySession;

    public LocationTest(SdkLevel sdkLevel, boolean isUnifiedPipeline) {
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
    public void testListenerLocationRequest() {
        final String methodName = "listenerRequestAndRemoveLocationUpdates";
        final String expectedResponse = "LISTENER LOCATION UPDATES";

        TransportAsyncStubWrapper transportWrapper = TransportAsyncStubWrapper.create(myGrpc);
        EnergyStubWrapper energyWrapper = EnergyStubWrapper.create(myGrpc);

        List<Common.Event> energyEvents = new ArrayList<>();
        if (myIsUnifiedPipeline) {
            Map<Long, List<Common.Event>> eventGroups =
                    transportWrapper.getEvents(
                            3,
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
                            resp -> resp.getEventsCount() == 3);
            energyEvents.addAll(response.getEventsList());
        }
        assertThat(energyEvents).hasSize(3);

        Common.Event requestEvent = energyEvents.get(0);
        assertThat(requestEvent.getTimestamp()).isGreaterThan(0L);
        assertThat(requestEvent.getPid()).isEqualTo(mySession.getPid());
        assertThat(requestEvent.getGroupId()).isGreaterThan(0L);
        assertThat(requestEvent.getIsEnded()).isFalse();
        assertThat(requestEvent.getEnergyEvent().getCallstack()).contains(methodName);
        assertThat(requestEvent.getEnergyEvent().getMetadataCase())
                .isEqualTo(MetadataCase.LOCATION_UPDATE_REQUESTED);
        assertThat(requestEvent.getEnergyEvent().getLocationUpdateRequested().getActionCase())
                .isEqualTo(LocationUpdateRequested.ActionCase.LISTENER);
        LocationRequest request =
                requestEvent.getEnergyEvent().getLocationUpdateRequested().getRequest();
        assertThat(request.getProvider()).isEqualTo("gps");
        assertThat(request.getIntervalMs()).isEqualTo(1000);
        assertThat(request.getFastestIntervalMs()).isEqualTo(1000);
        assertThat(request.getSmallestDisplacementMeters()).isWithin(EPSILON).of(100.0f);
        assertThat(request.getPriority()).isEqualTo(Priority.HIGH_ACCURACY);

        Common.Event locationChangeEvent = energyEvents.get(1);
        assertThat(locationChangeEvent.getTimestamp()).isAtLeast(requestEvent.getTimestamp());
        assertThat(locationChangeEvent.getPid()).isEqualTo(mySession.getPid());
        assertThat(locationChangeEvent.getGroupId()).isEqualTo(requestEvent.getGroupId());
        assertThat(locationChangeEvent.getIsEnded()).isFalse();
        assertThat(locationChangeEvent.getEnergyEvent().getMetadataCase())
                .isEqualTo(MetadataCase.LOCATION_CHANGED);
        assertThat(locationChangeEvent.getEnergyEvent().getLocationChanged().getActionCase())
                .isEqualTo(LocationChanged.ActionCase.LISTENER);
        Location location = locationChangeEvent.getEnergyEvent().getLocationChanged().getLocation();
        assertThat(location.getProvider()).isEqualTo("network");
        assertThat(location.getAccuracy()).isWithin(EPSILON).of(100.0f);
        assertThat(location.getLatitude()).isWithin(EPSILON).of(30.0f);
        assertThat(location.getLongitude()).isWithin(EPSILON).of(60.0f);

        Common.Event removeEvent = energyEvents.get(2);
        assertThat(removeEvent.getTimestamp()).isAtLeast(locationChangeEvent.getTimestamp());
        assertThat(removeEvent.getPid()).isEqualTo(mySession.getPid());
        assertThat(removeEvent.getGroupId()).isEqualTo(requestEvent.getGroupId());
        assertThat(removeEvent.getIsEnded()).isTrue();
        assertThat(removeEvent.getEnergyEvent().getCallstack()).contains(methodName);
        assertThat(removeEvent.getEnergyEvent().getMetadataCase())
                .isEqualTo(MetadataCase.LOCATION_UPDATE_REMOVED);
        assertThat(removeEvent.getEnergyEvent().getLocationUpdateRemoved().getActionCase())
                .isEqualTo(LocationUpdateRemoved.ActionCase.LISTENER);
    }

    @Test
    public void testIntentLocationRequest() {
        final String methodName = "intentRequestAndRemoveLocationUpdates";
        final String expectedResponse = "INTENT LOCATION UPDATES";

        TransportAsyncStubWrapper transportWrapper = TransportAsyncStubWrapper.create(myGrpc);
        EnergyStubWrapper energyWrapper = EnergyStubWrapper.create(myGrpc);

        List<Common.Event> energyEvents = new ArrayList<>();
        if (myIsUnifiedPipeline) {
            Map<Long, List<Common.Event>> eventGroups =
                    transportWrapper.getEvents(
                            3,
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
                            resp -> resp.getEventsCount() == 3);
            energyEvents.addAll(response.getEventsList());
        }
        assertThat(energyEvents).hasSize(3);

        Common.Event requestEvent = energyEvents.get(0);
        assertThat(requestEvent.getTimestamp()).isGreaterThan(0L);
        assertThat(requestEvent.getPid()).isEqualTo(mySession.getPid());
        assertThat(requestEvent.getGroupId()).isGreaterThan(0L);
        assertThat(requestEvent.getIsEnded()).isFalse();
        assertThat(requestEvent.getEnergyEvent().getCallstack()).contains(methodName);
        assertThat(requestEvent.getEnergyEvent().getMetadataCase())
                .isEqualTo(MetadataCase.LOCATION_UPDATE_REQUESTED);
        assertThat(requestEvent.getEnergyEvent().getLocationUpdateRequested().getActionCase())
                .isEqualTo(LocationUpdateRequested.ActionCase.INTENT);
        assertThat(
                        requestEvent
                                .getEnergyEvent()
                                .getLocationUpdateRequested()
                                .getIntent()
                                .getCreatorPackage())
                .isEqualTo("com.example");
        assertThat(
                        requestEvent
                                .getEnergyEvent()
                                .getLocationUpdateRequested()
                                .getIntent()
                                .getCreatorUid())
                .isEqualTo(123);
        LocationRequest request =
                requestEvent.getEnergyEvent().getLocationUpdateRequested().getRequest();
        assertThat(request.getProvider()).isEqualTo("");
        assertThat(request.getIntervalMs()).isEqualTo(2000);
        assertThat(request.getFastestIntervalMs()).isEqualTo(2000);
        assertThat(request.getSmallestDisplacementMeters()).isWithin(EPSILON).of(50.0f);
        assertThat(request.getPriority()).isEqualTo(Priority.BALANCED);

        Common.Event locationChangeEvent = energyEvents.get(1);
        assertThat(locationChangeEvent.getTimestamp()).isAtLeast(requestEvent.getTimestamp());
        assertThat(locationChangeEvent.getPid()).isEqualTo(mySession.getPid());
        assertThat(locationChangeEvent.getGroupId()).isEqualTo(requestEvent.getGroupId());
        assertThat(locationChangeEvent.getIsEnded()).isFalse();
        assertThat(locationChangeEvent.getEnergyEvent().getMetadataCase())
                .isEqualTo(MetadataCase.LOCATION_CHANGED);
        assertThat(locationChangeEvent.getEnergyEvent().getLocationChanged().getActionCase())
                .isEqualTo(LocationChanged.ActionCase.INTENT);
        assertThat(
                        locationChangeEvent
                                .getEnergyEvent()
                                .getLocationChanged()
                                .getIntent()
                                .getCreatorPackage())
                .isEqualTo("com.example");
        assertThat(
                        locationChangeEvent
                                .getEnergyEvent()
                                .getLocationChanged()
                                .getIntent()
                                .getCreatorUid())
                .isEqualTo(123);
        Location location = locationChangeEvent.getEnergyEvent().getLocationChanged().getLocation();
        assertThat(location.getProvider()).isEqualTo("passive");
        assertThat(location.getAccuracy()).isWithin(EPSILON).of(50.0f);
        assertThat(location.getLatitude()).isWithin(EPSILON).of(60.0);
        assertThat(location.getLongitude()).isWithin(EPSILON).of(30.0);

        Common.Event removeEvent = energyEvents.get(2);
        assertThat(removeEvent.getTimestamp()).isAtLeast(locationChangeEvent.getTimestamp());
        assertThat(removeEvent.getPid()).isEqualTo(mySession.getPid());
        assertThat(removeEvent.getGroupId()).isEqualTo(requestEvent.getGroupId());
        assertThat(removeEvent.getIsEnded()).isTrue();
        assertThat(removeEvent.getEnergyEvent().getCallstack()).contains(methodName);
        assertThat(removeEvent.getEnergyEvent().getMetadataCase())
                .isEqualTo(MetadataCase.LOCATION_UPDATE_REMOVED);
        assertThat(removeEvent.getEnergyEvent().getLocationUpdateRemoved().getActionCase())
                .isEqualTo(LocationUpdateRemoved.ActionCase.INTENT);
    }

    @Test
    public void testGmsIntentLocationRequest() {
        final String methodName = "gmsIntentRequestAndRemoveLocationUpdates";
        final String expectedResponse = "GMS INTENT LOCATION UPDATES";

        TransportAsyncStubWrapper transportWrapper = TransportAsyncStubWrapper.create(myGrpc);
        EnergyStubWrapper energyWrapper = EnergyStubWrapper.create(myGrpc);

        List<Common.Event> energyEvents = new ArrayList<>();
        if (myIsUnifiedPipeline) {
            Map<Long, List<Common.Event>> eventGroups =
                    transportWrapper.getEvents(
                            3,
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
                            resp -> resp.getEventsCount() == 3);
            energyEvents.addAll(response.getEventsList());
        }
        assertThat(energyEvents).hasSize(3);

        Common.Event requestEvent = energyEvents.get(0);
        assertThat(requestEvent.getTimestamp()).isGreaterThan(0L);
        assertThat(requestEvent.getPid()).isEqualTo(mySession.getPid());
        assertThat(requestEvent.getGroupId()).isGreaterThan(0L);
        assertThat(requestEvent.getIsEnded()).isFalse();
        assertThat(requestEvent.getEnergyEvent().getCallstack()).contains(methodName);
        assertThat(requestEvent.getEnergyEvent().getMetadataCase())
                .isEqualTo(MetadataCase.LOCATION_UPDATE_REQUESTED);
        assertThat(requestEvent.getEnergyEvent().getLocationUpdateRequested().getActionCase())
                .isEqualTo(LocationUpdateRequested.ActionCase.INTENT);
        assertThat(
                        requestEvent
                                .getEnergyEvent()
                                .getLocationUpdateRequested()
                                .getIntent()
                                .getCreatorPackage())
                .isEqualTo("com.google.gms");
        assertThat(
                        requestEvent
                                .getEnergyEvent()
                                .getLocationUpdateRequested()
                                .getIntent()
                                .getCreatorUid())
                .isEqualTo(1);
        LocationRequest request =
                requestEvent.getEnergyEvent().getLocationUpdateRequested().getRequest();
        assertThat(request.getProvider()).isEqualTo("fused");
        assertThat(request.getIntervalMs()).isEqualTo(100);
        assertThat(request.getFastestIntervalMs()).isEqualTo(10);
        assertThat(request.getSmallestDisplacementMeters()).isWithin(EPSILON).of(1.0f);
        assertThat(request.getPriority()).isEqualTo(Priority.HIGH_ACCURACY);

        Common.Event locationChangeEvent = energyEvents.get(1);
        assertThat(locationChangeEvent.getTimestamp()).isAtLeast(requestEvent.getTimestamp());
        assertThat(locationChangeEvent.getPid()).isEqualTo(mySession.getPid());
        assertThat(locationChangeEvent.getGroupId()).isEqualTo(requestEvent.getGroupId());
        assertThat(locationChangeEvent.getIsEnded()).isFalse();
        assertThat(locationChangeEvent.getEnergyEvent().getMetadataCase())
                .isEqualTo(MetadataCase.LOCATION_CHANGED);
        assertThat(locationChangeEvent.getEnergyEvent().getLocationChanged().getActionCase())
                .isEqualTo(LocationChanged.ActionCase.INTENT);
        assertThat(
                        locationChangeEvent
                                .getEnergyEvent()
                                .getLocationChanged()
                                .getIntent()
                                .getCreatorPackage())
                .isEqualTo("com.google.gms");
        assertThat(
                        locationChangeEvent
                                .getEnergyEvent()
                                .getLocationChanged()
                                .getIntent()
                                .getCreatorUid())
                .isEqualTo(1);
        Location location = locationChangeEvent.getEnergyEvent().getLocationChanged().getLocation();
        assertThat(location.getProvider()).isEqualTo("gps");
        assertThat(location.getAccuracy()).isWithin(EPSILON).of(10.0f);
        assertThat(location.getLatitude()).isWithin(EPSILON).of(45.0);
        assertThat(location.getLongitude()).isWithin(EPSILON).of(45.0);

        Common.Event removeEvent = energyEvents.get(2);
        assertThat(removeEvent.getTimestamp()).isAtLeast(locationChangeEvent.getTimestamp());
        assertThat(removeEvent.getPid()).isEqualTo(mySession.getPid());
        assertThat(removeEvent.getGroupId()).isEqualTo(requestEvent.getGroupId());
        assertThat(removeEvent.getIsEnded()).isTrue();
        assertThat(removeEvent.getEnergyEvent().getCallstack()).contains(methodName);
        assertThat(removeEvent.getEnergyEvent().getMetadataCase())
                .isEqualTo(MetadataCase.LOCATION_UPDATE_REMOVED);
        assertThat(removeEvent.getEnergyEvent().getLocationUpdateRemoved().getActionCase())
                .isEqualTo(LocationUpdateRemoved.ActionCase.INTENT);
    }

    @Test
    public void testMissingRequestStarted() {
        final String methodName = "updateLocationWithoutStartingRequest";
        final String expectedResponse = "LISTENER LOCATION UPDATES";

        TransportAsyncStubWrapper transportWrapper = TransportAsyncStubWrapper.create(myGrpc);
        EnergyStubWrapper energyWrapper = EnergyStubWrapper.create(myGrpc);

        List<Common.Event> energyEvents = new ArrayList<>();
        if (myIsUnifiedPipeline) {
            Map<Long, List<Common.Event>> eventGroups =
                    transportWrapper.getEvents(
                            1,
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
                            resp -> resp.getEventsCount() == 1);
            energyEvents.addAll(response.getEventsList());
        }
        assertThat(energyEvents).hasSize(1);

        Common.Event locationChangeEvent = energyEvents.get(0);
        assertThat(locationChangeEvent.getGroupId()).isGreaterThan(0L);
        assertThat(locationChangeEvent.getEnergyEvent().getMetadataCase())
                .isEqualTo(MetadataCase.LOCATION_CHANGED);
    }

    private void triggerMethod(String methodName, String expectedResponse) {
        myAndroidDriver.triggerMethod(ACTIVITY_CLASS, methodName);
        assertThat(myAndroidDriver.waitForInput(expectedResponse)).isTrue();
    }
}
