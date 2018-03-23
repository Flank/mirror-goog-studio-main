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
import com.android.tools.profiler.proto.EnergyProfiler.Location;
import com.android.tools.profiler.proto.EnergyProfiler.LocationChanged;
import com.android.tools.profiler.proto.EnergyProfiler.LocationRequest;
import com.android.tools.profiler.proto.EnergyProfiler.LocationRequest.Priority;
import com.android.tools.profiler.proto.EnergyProfiler.LocationUpdateRemoved;
import com.android.tools.profiler.proto.EnergyProfiler.LocationUpdateRequested;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class LocationTest {
    private static final String ACTIVITY_CLASS = "com.activity.energy.LocationActivity";

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
    public void testListenerLocationRequest() {
        myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "listenerRequestAndRemoveLocationUpdates");
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
        assertThat(request.getSmallestDisplacementMeters()).isEqualTo(100.0f);
        assertThat(request.getPriority()).isEqualTo(Priority.HIGH_ACCURACY);

        EnergyEvent locationChangeEvent = response.getEvents(1);
        assertThat(locationChangeEvent.getTimestamp()).isGreaterThan(0L);
        assertThat(locationChangeEvent.getPid()).isEqualTo(mySession.getPid());
        assertThat(locationChangeEvent.getEventId()).isEqualTo(requestEvent.getEventId());
        assertThat(locationChangeEvent.getIsTerminal()).isFalse();
        assertThat(locationChangeEvent.getMetadataCase()).isEqualTo(MetadataCase.LOCATION_CHANGED);
        assertThat(locationChangeEvent.getLocationChanged().getActionCase())
                .isEqualTo(LocationChanged.ActionCase.LISTENER);
        Location location = locationChangeEvent.getLocationChanged().getListener().getLocation();
        assertThat(location.getProvider()).isEqualTo("network");
        assertThat(location.getAccuracy()).isEqualTo(100.0f);
        assertThat(location.getLatitude()).isEqualTo(30.0);
        assertThat(location.getLongitude()).isEqualTo(60.0);

        EnergyEvent removeEvent = response.getEvents(2);
        assertThat(removeEvent.getTimestamp()).isGreaterThan(0L);
        assertThat(removeEvent.getPid()).isEqualTo(mySession.getPid());
        assertThat(removeEvent.getEventId()).isEqualTo(requestEvent.getEventId());
        assertThat(removeEvent.getIsTerminal()).isTrue();
        assertThat(removeEvent.getMetadataCase()).isEqualTo(MetadataCase.LOCATION_UPDATE_REMOVED);
        assertThat(removeEvent.getLocationUpdateRemoved().getActionCase())
                .isEqualTo(LocationUpdateRemoved.ActionCase.LISTENER);

        String requestStack = TestUtils.getBytes(myGrpc, requestEvent.getTraceId());
        assertThat(requestStack).contains("request");
        String removeStack = TestUtils.getBytes(myGrpc, removeEvent.getTraceId());
        assertThat(removeStack).contains("remove");
    }

    @Test
    public void testIntentLocationRequest() {
        myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "intentRequestAndRemoveLocationUpdates");
        assertThat(myAndroidDriver.waitForInput("INTENT LOCATION UPDATES")).isTrue();

        EnergyEventsResponse response =
                TestUtils.waitForAndReturn(
                        () -> myStubWrapper.getAllEnergyEvents(mySession),
                        resp -> resp.getEventsCount() == 2);
        assertThat(response.getEventsCount()).isEqualTo(2);

        EnergyEvent requestEvent = response.getEvents(0);
        assertThat(requestEvent.getTimestamp()).isGreaterThan(0L);
        assertThat(requestEvent.getPid()).isEqualTo(mySession.getPid());
        assertThat(requestEvent.getEventId()).isGreaterThan(0);
        assertThat(requestEvent.getIsTerminal()).isFalse();
        assertThat(requestEvent.getMetadataCase())
                .isEqualTo(MetadataCase.LOCATION_UPDATE_REQUESTED);
        assertThat(requestEvent.getLocationUpdateRequested().getActionCase())
                .isEqualTo(LocationUpdateRequested.ActionCase.INTENT);
        LocationRequest request = requestEvent.getLocationUpdateRequested().getRequest();
        assertThat(request.getProvider()).isEqualTo("");
        assertThat(request.getIntervalMs()).isEqualTo(2000);
        assertThat(request.getFastestIntervalMs()).isEqualTo(2000);
        assertThat(request.getSmallestDisplacementMeters()).isEqualTo(50.0f);
        assertThat(request.getPriority()).isEqualTo(Priority.BALANCED);

        EnergyEvent removeEvent = response.getEvents(1);
        assertThat(removeEvent.getTimestamp()).isGreaterThan(0L);
        assertThat(removeEvent.getPid()).isEqualTo(mySession.getPid());
        assertThat(removeEvent.getEventId()).isEqualTo(requestEvent.getEventId());
        assertThat(removeEvent.getIsTerminal()).isTrue();
        assertThat(removeEvent.getMetadataCase()).isEqualTo(MetadataCase.LOCATION_UPDATE_REMOVED);
        assertThat(removeEvent.getLocationUpdateRemoved().getActionCase())
                .isEqualTo(LocationUpdateRemoved.ActionCase.INTENT);

        String requestStack = TestUtils.getBytes(myGrpc, requestEvent.getTraceId());
        assertThat(requestStack).contains("request");
        String removeStack = TestUtils.getBytes(myGrpc, removeEvent.getTraceId());
        assertThat(removeStack).contains("remove");
    }
}
