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
import com.android.tools.profiler.proto.Common.Session;
import com.android.tools.profiler.proto.EnergyProfiler.EnergyEvent;
import com.android.tools.profiler.proto.EnergyProfiler.EnergyEvent.MetadataCase;
import com.android.tools.profiler.proto.EnergyProfiler.EnergyEventsResponse;
import com.android.tools.profiler.proto.EnergyProfiler.Location;
import com.android.tools.profiler.proto.EnergyProfiler.LocationChanged;
import com.android.tools.profiler.proto.EnergyProfiler.LocationRequest;
import com.android.tools.profiler.proto.EnergyProfiler.LocationRequest.Priority;
import com.android.tools.profiler.proto.EnergyProfiler.LocationUpdateRemoved;
import com.android.tools.profiler.proto.EnergyProfiler.LocationUpdateRequested;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LocationTest {
    @Parameters
    public static Collection<Integer> data() {
        return Arrays.asList(26, 28);
    }

    private static final String ACTIVITY_CLASS = "com.activity.energy.LocationActivity";
    private static final float EPSILON = 0.0001f;

    @Rule public final PerfDriver myPerfDriver;

    private GrpcUtils myGrpc;
    private FakeAndroidDriver myAndroidDriver;
    private EnergyStubWrapper myStubWrapper;
    private Session mySession;

    public LocationTest(int sdkLevel) {
        myPerfDriver = new PerfDriver(ACTIVITY_CLASS, sdkLevel);
    }

    @Before
    public void setUp() throws Exception {
        myAndroidDriver = myPerfDriver.getFakeAndroidDriver();
        myGrpc = myPerfDriver.getGrpc();
        myStubWrapper = new EnergyStubWrapper(myGrpc.getEnergyStub());
        mySession = myPerfDriver.getSession();
    }

    @Test
    public void testListenerLocationRequest() {
        String methodName = "listenerRequestAndRemoveLocationUpdates";
        myAndroidDriver.triggerMethod(ACTIVITY_CLASS, methodName);
        assertThat(myAndroidDriver.waitForInput("LISTENER LOCATION UPDATES")).isTrue();

        EnergyEventsResponse response =
                TestUtils.waitForAndReturn(
                        () -> myStubWrapper.getAllEnergyEvents(mySession),
                        resp -> resp.getEventsCount() == 3);
        assertThat(response.getEventsCount()).isEqualTo(3);

        EnergyEvent requestEvent = response.getEvents(0);
        assertThat(requestEvent.getTimestamp()).isGreaterThan(0L);
        assertThat(requestEvent.getPid()).isEqualTo(mySession.getPid());
        assertThat(requestEvent.getEventId()).isGreaterThan(0);
        assertThat(requestEvent.getIsTerminal()).isFalse();
        assertThat(requestEvent.getMetadataCase())
                .isEqualTo(MetadataCase.LOCATION_UPDATE_REQUESTED);
        assertThat(requestEvent.getLocationUpdateRequested().getActionCase())
                .isEqualTo(LocationUpdateRequested.ActionCase.LISTENER);
        LocationRequest request = requestEvent.getLocationUpdateRequested().getRequest();
        assertThat(request.getProvider()).isEqualTo("gps");
        assertThat(request.getIntervalMs()).isEqualTo(1000);
        assertThat(request.getFastestIntervalMs()).isEqualTo(1000);
        assertThat(request.getSmallestDisplacementMeters()).isWithin(EPSILON).of(100.0f);
        assertThat(request.getPriority()).isEqualTo(Priority.HIGH_ACCURACY);

        EnergyEvent locationChangeEvent = response.getEvents(1);
        assertThat(locationChangeEvent.getTimestamp()).isAtLeast(requestEvent.getTimestamp());
        assertThat(locationChangeEvent.getPid()).isEqualTo(mySession.getPid());
        assertThat(locationChangeEvent.getEventId()).isEqualTo(requestEvent.getEventId());
        assertThat(locationChangeEvent.getIsTerminal()).isFalse();
        assertThat(locationChangeEvent.getMetadataCase()).isEqualTo(MetadataCase.LOCATION_CHANGED);
        assertThat(locationChangeEvent.getLocationChanged().getActionCase())
                .isEqualTo(LocationChanged.ActionCase.LISTENER);
        Location location = locationChangeEvent.getLocationChanged().getLocation();
        assertThat(location.getProvider()).isEqualTo("network");
        assertThat(location.getAccuracy()).isWithin(EPSILON).of(100.0f);
        assertThat(location.getLatitude()).isWithin(EPSILON).of(30.0f);
        assertThat(location.getLongitude()).isWithin(EPSILON).of(60.0f);

        EnergyEvent removeEvent = response.getEvents(2);
        assertThat(removeEvent.getTimestamp()).isAtLeast(locationChangeEvent.getTimestamp());
        assertThat(removeEvent.getPid()).isEqualTo(mySession.getPid());
        assertThat(removeEvent.getEventId()).isEqualTo(requestEvent.getEventId());
        assertThat(removeEvent.getIsTerminal()).isTrue();
        assertThat(removeEvent.getMetadataCase()).isEqualTo(MetadataCase.LOCATION_UPDATE_REMOVED);
        assertThat(removeEvent.getLocationUpdateRemoved().getActionCase())
                .isEqualTo(LocationUpdateRemoved.ActionCase.LISTENER);

        String requestStack = TestUtils.getBytes(myGrpc, requestEvent.getTraceId());
        assertThat(requestStack).contains(methodName);
        String removeStack = TestUtils.getBytes(myGrpc, removeEvent.getTraceId());
        assertThat(removeStack).contains(methodName);
    }

    @Test
    public void testIntentLocationRequest() {
        String methodName = "intentRequestAndRemoveLocationUpdates";
        myAndroidDriver.triggerMethod(ACTIVITY_CLASS, methodName);
        assertThat(myAndroidDriver.waitForInput("INTENT LOCATION UPDATES")).isTrue();

        EnergyEventsResponse response =
                TestUtils.waitForAndReturn(
                        () -> myStubWrapper.getAllEnergyEvents(mySession),
                        resp -> resp.getEventsCount() == 3);
        assertThat(response.getEventsCount()).isEqualTo(3);

        EnergyEvent requestEvent = response.getEvents(0);
        assertThat(requestEvent.getTimestamp()).isGreaterThan(0L);
        assertThat(requestEvent.getPid()).isEqualTo(mySession.getPid());
        assertThat(requestEvent.getEventId()).isGreaterThan(0);
        assertThat(requestEvent.getIsTerminal()).isFalse();
        assertThat(requestEvent.getMetadataCase())
                .isEqualTo(MetadataCase.LOCATION_UPDATE_REQUESTED);
        assertThat(requestEvent.getLocationUpdateRequested().getActionCase())
                .isEqualTo(LocationUpdateRequested.ActionCase.INTENT);
        assertThat(requestEvent.getLocationUpdateRequested().getIntent().getCreatorPackage())
                .isEqualTo("com.example");
        assertThat(requestEvent.getLocationUpdateRequested().getIntent().getCreatorUid())
                .isEqualTo(123);
        LocationRequest request = requestEvent.getLocationUpdateRequested().getRequest();
        assertThat(request.getProvider()).isEqualTo("");
        assertThat(request.getIntervalMs()).isEqualTo(2000);
        assertThat(request.getFastestIntervalMs()).isEqualTo(2000);
        assertThat(request.getSmallestDisplacementMeters()).isWithin(EPSILON).of(50.0f);
        assertThat(request.getPriority()).isEqualTo(Priority.BALANCED);

        EnergyEvent locationChangeEvent = response.getEvents(1);
        assertThat(locationChangeEvent.getTimestamp()).isAtLeast(requestEvent.getTimestamp());
        assertThat(locationChangeEvent.getPid()).isEqualTo(mySession.getPid());
        assertThat(locationChangeEvent.getEventId()).isEqualTo(requestEvent.getEventId());
        assertThat(locationChangeEvent.getIsTerminal()).isFalse();
        assertThat(locationChangeEvent.getMetadataCase()).isEqualTo(MetadataCase.LOCATION_CHANGED);
        assertThat(locationChangeEvent.getLocationChanged().getActionCase())
                .isEqualTo(LocationChanged.ActionCase.INTENT);
        assertThat(locationChangeEvent.getLocationChanged().getIntent().getCreatorPackage())
                .isEqualTo("com.example");
        assertThat(locationChangeEvent.getLocationChanged().getIntent().getCreatorUid())
                .isEqualTo(123);
        Location location = locationChangeEvent.getLocationChanged().getLocation();
        assertThat(location.getProvider()).isEqualTo("passive");
        assertThat(location.getAccuracy()).isWithin(EPSILON).of(50.0f);
        assertThat(location.getLatitude()).isWithin(EPSILON).of(60.0);
        assertThat(location.getLongitude()).isWithin(EPSILON).of(30.0);

        EnergyEvent removeEvent = response.getEvents(2);
        assertThat(removeEvent.getTimestamp()).isAtLeast(locationChangeEvent.getTimestamp());
        assertThat(removeEvent.getPid()).isEqualTo(mySession.getPid());
        assertThat(removeEvent.getEventId()).isEqualTo(requestEvent.getEventId());
        assertThat(removeEvent.getIsTerminal()).isTrue();
        assertThat(removeEvent.getMetadataCase()).isEqualTo(MetadataCase.LOCATION_UPDATE_REMOVED);
        assertThat(removeEvent.getLocationUpdateRemoved().getActionCase())
                .isEqualTo(LocationUpdateRemoved.ActionCase.INTENT);

        String requestStack = TestUtils.getBytes(myGrpc, requestEvent.getTraceId());
        assertThat(requestStack).contains(methodName);
        String removeStack = TestUtils.getBytes(myGrpc, removeEvent.getTraceId());
        assertThat(removeStack).contains(methodName);
    }

    @Test
    public void testGmsIntentLocationRequest() {
        String methodName = "gmsIntentRequestAndRemoveLocationUpdates";
        myAndroidDriver.triggerMethod(ACTIVITY_CLASS, methodName);
        assertThat(myAndroidDriver.waitForInput("GMS INTENT LOCATION UPDATES")).isTrue();

        EnergyEventsResponse response =
                TestUtils.waitForAndReturn(
                        () -> myStubWrapper.getAllEnergyEvents(mySession),
                        resp -> resp.getEventsCount() == 3);
        assertThat(response.getEventsCount()).isEqualTo(3);

        EnergyEvent requestEvent = response.getEvents(0);
        assertThat(requestEvent.getTimestamp()).isGreaterThan(0L);
        assertThat(requestEvent.getPid()).isEqualTo(mySession.getPid());
        assertThat(requestEvent.getEventId()).isGreaterThan(0);
        assertThat(requestEvent.getIsTerminal()).isFalse();
        assertThat(requestEvent.getMetadataCase())
                .isEqualTo(MetadataCase.LOCATION_UPDATE_REQUESTED);
        assertThat(requestEvent.getLocationUpdateRequested().getActionCase())
                .isEqualTo(LocationUpdateRequested.ActionCase.INTENT);
        assertThat(requestEvent.getLocationUpdateRequested().getIntent().getCreatorPackage())
                .isEqualTo("com.google.gms");
        assertThat(requestEvent.getLocationUpdateRequested().getIntent().getCreatorUid())
                .isEqualTo(1);
        LocationRequest request = requestEvent.getLocationUpdateRequested().getRequest();
        assertThat(request.getProvider()).isEqualTo("fused");
        assertThat(request.getIntervalMs()).isEqualTo(100);
        assertThat(request.getFastestIntervalMs()).isEqualTo(10);
        assertThat(request.getSmallestDisplacementMeters()).isWithin(EPSILON).of(1.0f);
        assertThat(request.getPriority()).isEqualTo(Priority.HIGH_ACCURACY);

        EnergyEvent locationChangeEvent = response.getEvents(1);
        assertThat(locationChangeEvent.getTimestamp()).isAtLeast(requestEvent.getTimestamp());
        assertThat(locationChangeEvent.getPid()).isEqualTo(mySession.getPid());
        assertThat(locationChangeEvent.getEventId()).isEqualTo(requestEvent.getEventId());
        assertThat(locationChangeEvent.getIsTerminal()).isFalse();
        assertThat(locationChangeEvent.getMetadataCase()).isEqualTo(MetadataCase.LOCATION_CHANGED);
        assertThat(locationChangeEvent.getLocationChanged().getActionCase())
                .isEqualTo(LocationChanged.ActionCase.INTENT);
        assertThat(locationChangeEvent.getLocationChanged().getIntent().getCreatorPackage())
                .isEqualTo("com.google.gms");
        assertThat(locationChangeEvent.getLocationChanged().getIntent().getCreatorUid())
                .isEqualTo(1);
        Location location = locationChangeEvent.getLocationChanged().getLocation();
        assertThat(location.getProvider()).isEqualTo("gps");
        assertThat(location.getAccuracy()).isWithin(EPSILON).of(10.0f);
        assertThat(location.getLatitude()).isWithin(EPSILON).of(45.0);
        assertThat(location.getLongitude()).isWithin(EPSILON).of(45.0);

        EnergyEvent removeEvent = response.getEvents(2);
        assertThat(removeEvent.getTimestamp()).isAtLeast(locationChangeEvent.getTimestamp());
        assertThat(removeEvent.getPid()).isEqualTo(mySession.getPid());
        assertThat(removeEvent.getEventId()).isEqualTo(requestEvent.getEventId());
        assertThat(removeEvent.getIsTerminal()).isTrue();
        assertThat(removeEvent.getMetadataCase()).isEqualTo(MetadataCase.LOCATION_UPDATE_REMOVED);
        assertThat(removeEvent.getLocationUpdateRemoved().getActionCase())
                .isEqualTo(LocationUpdateRemoved.ActionCase.INTENT);

        String requestStack = TestUtils.getBytes(myGrpc, requestEvent.getTraceId());
        assertThat(requestStack).contains(methodName);
        String removeStack = TestUtils.getBytes(myGrpc, removeEvent.getTraceId());
        assertThat(removeStack).contains(methodName);
    }

    @Test
    public void testMissingRequestStarted() {
        myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "updateLocationWithoutStartingRequest");
        assertThat(myAndroidDriver.waitForInput("LISTENER LOCATION UPDATES")).isTrue();

        EnergyEventsResponse response =
                TestUtils.waitForAndReturn(
                        () -> myStubWrapper.getAllEnergyEvents(mySession),
                        resp -> resp.getEventsCount() == 1);
        assertThat(response.getEventsCount()).isEqualTo(1);

        EnergyEvent locationChangeEvent = response.getEvents(0);
        assertThat(locationChangeEvent.getEventId()).isGreaterThan(0);
        assertThat(locationChangeEvent.getMetadataCase()).isEqualTo(MetadataCase.LOCATION_CHANGED);
    }
}
