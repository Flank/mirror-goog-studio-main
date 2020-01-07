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

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.fakeandroid.FakeAndroidDriver;
import com.android.tools.profiler.ProfilerConfig;
import com.android.tools.profiler.ProfilerRule;
import com.android.tools.profiler.proto.Common.Event;
import com.android.tools.profiler.proto.Network;
import com.android.tools.transport.device.SdkLevel;
import com.android.tools.transport.grpc.Grpc;
import com.android.tools.transport.grpc.TransportAsyncStubWrapper;
import com.android.tools.transport.grpc.TransportStubWrapper;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class UnifiedPipelineHttpUrlTest {
    @Parameterized.Parameters
    public static Collection<SdkLevel> parameters() {
        // Unified pipeline only available O+
        return Arrays.asList(SdkLevel.O, SdkLevel.P);
    }

    private static final String ACTIVITY_CLASS = "com.activity.network.HttpUrlActivity";

    @Rule public final ProfilerRule myProfilerRule;

    private FakeAndroidDriver myAndroidDriver;
    private Grpc myGrpc;

    public UnifiedPipelineHttpUrlTest(SdkLevel sdkLevel) {
        myProfilerRule =
                new ProfilerRule(
                        ACTIVITY_CLASS,
                        sdkLevel,
                        new ProfilerConfig() {
                            @Override
                            public boolean usesUnifiedPipeline() {
                                return true;
                            }
                        });
    }

    @Before
    public void before() {
        myAndroidDriver = myProfilerRule.getTransportRule().getAndroidDriver();
        myGrpc = myProfilerRule.getTransportRule().getGrpc();
    }

    @Test
    public void testHttpGet() {
        final String getSuccess = "HttpUrlGet SUCCESS";

        TransportStubWrapper transportStubWrapper = TransportStubWrapper.create(myGrpc);
        TransportAsyncStubWrapper transportAsyncStubWrapper = TransportAsyncStubWrapper.create(myGrpc);

        Map<Long, List<Event>> httpEventsMap =
                transportAsyncStubWrapper.getEvents(
                        5,
                        event -> event.getKind() == Event.Kind.NETWORK_HTTP_CONNECTION
                                        || event.getKind() == Event.Kind.NETWORK_HTTP_THREAD,
                        () -> {
                            myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "runGet");
                            assertThat(myAndroidDriver.waitForInput(getSuccess)).isTrue();
                        });
        assertThat(httpEventsMap).hasSize(1);

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

            Event responseCompletedEvent = httpEvents.get(2);
            Network.NetworkHttpConnectionData.HttpResponseCompleted responseData =
                    responseCompletedEvent.getNetworkHttpConnection().getHttpResponseCompleted();
            assertThat(responseData.getPayloadId())
                    .isEqualTo(responseCompletedEvent.getGroupId() + "_response");

            assertThat(transportStubWrapper.toBytes(responseData.getPayloadId())).isEqualTo(getSuccess);
        }
    }

    @Test
    public void testHttpPost() {
        final String getSuccess = "HttpUrlPost SUCCESS";

        TransportStubWrapper transportStubWrapper = TransportStubWrapper.create(myGrpc);
        TransportAsyncStubWrapper transportAsyncStubWrapper = TransportAsyncStubWrapper.create(myGrpc);

        Map<Long, List<Event>> httpEventsMap =
                transportAsyncStubWrapper.getEvents(
                        6,
                        event -> event.getKind() == Event.Kind.NETWORK_HTTP_CONNECTION
                                        || event.getKind() == Event.Kind.NETWORK_HTTP_THREAD,
                        () -> {
                            myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "runPost");
                            assertThat(myAndroidDriver.waitForInput(getSuccess)).isTrue();
                        });
        assertThat(httpEventsMap).hasSize(1);

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

            Event requestCompleteEvent = httpEvents.get(1);
            Network.NetworkHttpConnectionData.HttpRequestCompleted requestData =
                    requestCompleteEvent.getNetworkHttpConnection().getHttpRequestCompleted();
            assertThat(requestData.getPayloadId())
                    .isEqualTo(requestCompleteEvent.getGroupId() + "_request");

            assertThat(transportStubWrapper.toBytes(requestData.getPayloadId())).isEqualTo("TestRequestBody");

            Event responseCompletedEvent = httpEvents.get(3);
            Network.NetworkHttpConnectionData.HttpResponseCompleted responseData =
                    responseCompletedEvent.getNetworkHttpConnection().getHttpResponseCompleted();
            assertThat(responseData.getPayloadId())
                    .isEqualTo(responseCompletedEvent.getGroupId() + "_response");

            assertThat(transportStubWrapper.toBytes(responseData.getPayloadId())).isEqualTo(getSuccess);
        }
    }

    @Test
    public void testHttpGet_CallResposeMethodBeforeConnect() {
        final String getSuccess = "HttpUrlGet SUCCESS";

        TransportAsyncStubWrapper transportAsyncStubWrapper = TransportAsyncStubWrapper.create(myGrpc);

        Map<Long, List<Event>> httpEventsMap =
                transportAsyncStubWrapper.getEvents(
                        5,
                        event -> event.getKind() == Event.Kind.NETWORK_HTTP_CONNECTION
                                        || event.getKind() == Event.Kind.NETWORK_HTTP_THREAD,
                        () -> {
                            myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "runGet_CallResponseMethodBeforeConnect");
                            assertThat(myAndroidDriver.waitForInput(getSuccess)).isTrue();
                        });
        assertThat(httpEventsMap).hasSize(1);

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
