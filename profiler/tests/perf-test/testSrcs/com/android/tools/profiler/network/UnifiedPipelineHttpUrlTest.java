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

package com.android.tools.profiler.network;

import com.android.tools.profiler.GrpcUtils;
import com.android.tools.profiler.PerfDriver;
import com.android.tools.profiler.proto.Common.Event;
import com.android.tools.profiler.proto.Network;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.truth.Truth.assertThat;

@RunWith(Parameterized.class)
public class UnifiedPipelineHttpUrlTest {
    @Parameterized.Parameters
    public static Collection<Integer> data() {
        // TODO the agent currently does not know about the unified pipeline flag pre-O, so the
        // http data are not made available via the new GRPC calls. We need to pass in the flag
        // via Gradle at compile-time.
        return Arrays.asList(26, 28);
    }

    private static final String ACTIVITY_CLASS = "com.activity.network.HttpUrlActivity";

    @Rule public final PerfDriver myPerfDriver;

    private GrpcUtils myGrpc;

    public UnifiedPipelineHttpUrlTest(int sdkLevel) {
        myPerfDriver = new PerfDriver(ACTIVITY_CLASS, sdkLevel, true);
    }

    @Before
    public void setup() {
        myGrpc = myPerfDriver.getGrpc();
    }

    @Test
    public void testHttpGet() throws Exception {
        final String getSuccess = "HttpUrlGet SUCCESS";
        Map<Long, List<Event>> httpEventsMap = NetworkStubWrapper.getHttpEvents(myGrpc.getProfilerStub(),
                myPerfDriver, ACTIVITY_CLASS, "runGet", getSuccess, 1);
        assertThat(httpEventsMap.size()).isEqualTo(1);
        for (List<Event> httpEvents : httpEventsMap.values()) {
            httpEvents = httpEvents.stream()
                    .filter(e -> e.getKind() == Event.Kind.NETWORK_HTTP_CONNECTION)
                    .collect(Collectors.toList());
            // No request body in get test. So we only expect to get back 4 events instead of 5.
            assertThat(httpEvents).hasSize(4);
            Event requestStartedEvent = httpEvents.get(0);
            Network.NetworkHttpConnectionData.HttpRequestStarted data =
                    requestStartedEvent.getNetworkHttpConnection().getHttpRequestStarted();
            assertThat(data.getMethod()).isEqualTo("GET");
            assertThat(data.getUrl()).contains("?activity=HttpUrlGet");
            // TODO validate payload id and payload
        }
    }

    @Test
    public void testHttpPost() throws Exception {
        final String getSuccess = "HttpUrlPost SUCCESS";
        Map<Long, List<Event>> httpEventsMap = NetworkStubWrapper.getHttpEvents(myGrpc.getProfilerStub(),
                myPerfDriver, ACTIVITY_CLASS, "runPost", getSuccess, 1);
        assertThat(httpEventsMap.size()).isEqualTo(1);
        for (List<Event> httpEvents : httpEventsMap.values()) {
            httpEvents = httpEvents.stream()
                    .filter(e -> e.getKind() == Event.Kind.NETWORK_HTTP_CONNECTION)
                    .collect(Collectors.toList());
            assertThat(httpEvents).hasSize(5);
            Event requestStartedEvent = httpEvents.get(0);
            Network.NetworkHttpConnectionData.HttpRequestStarted data =
                    requestStartedEvent.getNetworkHttpConnection().getHttpRequestStarted();
            assertThat(data.getMethod()).isEqualTo("POST");
            assertThat(data.getUrl()).contains("?activity=HttpUrlPost");
            // TODO validate payload id and payload
        }
    }

    @Test
    public void testHttpGet_CallResposeMethodBeforeConnect() throws Exception {
        final String getSuccess = "HttpUrlGet SUCCESS";
        Map<Long, List<Event>> httpEventsMap = NetworkStubWrapper.getHttpEvents(myGrpc.getProfilerStub(),
                myPerfDriver, ACTIVITY_CLASS, "runGet_CallResponseMethodBeforeConnect", getSuccess, 1);
        assertThat(httpEventsMap.size()).isEqualTo(1);
        for (List<Event> httpEvents : httpEventsMap.values()) {
            httpEvents = httpEvents.stream()
                    .filter(e -> e.getKind() == Event.Kind.NETWORK_HTTP_CONNECTION)
                    .collect(Collectors.toList());
            // No request body in get test. So we only expect to get back 4 events instead of 5.
            assertThat(httpEvents).hasSize(4);
            Event requestStartedEvent = httpEvents.get(0);
            Network.NetworkHttpConnectionData.HttpRequestStarted requestData =
                    requestStartedEvent.getNetworkHttpConnection().getHttpRequestStarted();
            assertThat(requestData.getMethod()).isEqualTo("GET");

            Event responseStartedEvent = httpEvents.get(1);
            Network.NetworkHttpConnectionData.HttpResponseStarted responseData =
                    responseStartedEvent.getNetworkHttpConnection().getHttpResponseStarted();
            assertThat(responseData.getFields()).contains("HTTP/1.0 200 OK");
            // If we got here, we're done - our response completed as expected
        }
    }
}
