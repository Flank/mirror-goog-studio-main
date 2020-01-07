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
public class UnifiedPipelineOkHttpTest {
    @Parameterized.Parameters
    public static Collection<SdkLevel> parameters() {
        // Unified pipeline only available O+
        return Arrays.asList(SdkLevel.O);
    }

    private static final String ACTIVITY_CLASS = "com.activity.network.OkHttpActivity";
    private static final String EXPECTED_RESPONSE_CODE = "response-status-code = 200";

    @Rule public final ProfilerRule myProfilerRule;

    private FakeAndroidDriver myAndroidDriver;
    private Grpc myGrpc;

    public UnifiedPipelineOkHttpTest(SdkLevel sdkLevel) {
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
    public void setup() {
        myAndroidDriver = myProfilerRule.getTransportRule().getAndroidDriver();
        myGrpc = myProfilerRule.getTransportRule().getGrpc();
    }

    @Test
    public void testOkHttp3Get() {
        final String okHttp3Get = "OKHTTP3GET";

        TransportStubWrapper transportStubWrapper = TransportStubWrapper.create(myGrpc);
        TransportAsyncStubWrapper transportAsyncStubWrapper = TransportAsyncStubWrapper.create(myGrpc);

        Map<Long, List<Event>> httpEventsMap =
                transportAsyncStubWrapper.getEvents(
                        5,
                        event ->
                                event.getKind() == Event.Kind.NETWORK_HTTP_CONNECTION
                                        || event.getKind() == Event.Kind.NETWORK_HTTP_THREAD,
                        () -> {
                            myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "runOkHttp3Get");
                            assertThat(myAndroidDriver.waitForInput(okHttp3Get)).isTrue();
                        });
        assertThat(httpEventsMap).hasSize(1);

        for (List<Event> httpEvents : httpEventsMap.values()) {
            List<Event> httpConnectionEvents = httpEvents.stream()
                    .filter(e -> e.getKind() == Event.Kind.NETWORK_HTTP_CONNECTION)
                    .collect(Collectors.toList());
            // No request body in get test. So we only expect to get back 4 events instead of 5.
            assertThat(httpConnectionEvents).hasSize(4);
            Event requestStartedEvent = httpConnectionEvents.get(0);
            Network.NetworkHttpConnectionData.HttpRequestStarted requestData =
                    requestStartedEvent.getNetworkHttpConnection().getHttpRequestStarted();
            assertThat(requestData.getMethod()).isEqualTo("GET");
            assertThat(requestData.getUrl()).contains("?method=" + okHttp3Get);

            Event responseStartedEvent = httpConnectionEvents.get(1);
            Network.NetworkHttpConnectionData.HttpResponseStarted responseData =
                    responseStartedEvent.getNetworkHttpConnection().getHttpResponseStarted();
            assertThat(responseData.getFields()).contains(EXPECTED_RESPONSE_CODE);

            assertThat(httpEvents.stream()
                    .filter(e -> e.getKind() == Event.Kind.NETWORK_HTTP_THREAD).count()).isEqualTo(1);

            Event responseCompletedEvent = httpConnectionEvents.get(2);
            Network.NetworkHttpConnectionData.HttpResponseCompleted responseCompleteData =
                    responseCompletedEvent.getNetworkHttpConnection().getHttpResponseCompleted();
            assertThat(responseCompleteData.getPayloadId())
                    .isEqualTo(responseCompletedEvent.getGroupId() + "_response");

            assertThat(transportStubWrapper.toBytes(responseCompleteData.getPayloadId())).contains(okHttp3Get);
        }
    }

    @Test
    public void testOkHttp3Post() {
        final String okHttp3Post = "OKHTTP3POST";

        TransportStubWrapper transportStubWrapper = TransportStubWrapper.create(myGrpc);
        TransportAsyncStubWrapper transportAsyncStubWrapper = TransportAsyncStubWrapper.create(myGrpc);

        Map<Long, List<Event>> httpEventsMap =
                transportAsyncStubWrapper.getEvents(
                        6,
                        event ->
                                event.getKind() == Event.Kind.NETWORK_HTTP_CONNECTION
                                        || event.getKind() == Event.Kind.NETWORK_HTTP_THREAD,
                        () -> {
                            myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "runOkHttp3Post");
                            assertThat(myAndroidDriver.waitForInput(okHttp3Post)).isTrue();
                        });
        assertThat(httpEventsMap).hasSize(1);
        for (List<Event> httpEvents : httpEventsMap.values()) {
            List<Event> httpConnectionEvents = httpEvents.stream()
                    .filter(e -> e.getKind() == Event.Kind.NETWORK_HTTP_CONNECTION)
                    .collect(Collectors.toList());
            assertThat(httpConnectionEvents).hasSize(5);
            Event requestStartedEvent = httpConnectionEvents.get(0);
            Network.NetworkHttpConnectionData.HttpRequestStarted data =
                    requestStartedEvent.getNetworkHttpConnection().getHttpRequestStarted();
            assertThat(data.getMethod()).isEqualTo("POST");
            assertThat(data.getUrl()).contains("?method=" + okHttp3Post);

            Event requestCompleteEvent = httpConnectionEvents.get(1);
            Network.NetworkHttpConnectionData.HttpRequestCompleted requestData =
                    requestCompleteEvent.getNetworkHttpConnection().getHttpRequestCompleted();
            assertThat(requestData.getPayloadId())
                    .isEqualTo(requestCompleteEvent.getGroupId() + "_request");
            assertThat(transportStubWrapper.toBytes(requestData.getPayloadId())).isEqualTo("OkHttp3 request body");
        }
    }

    @Test
    public void testOkHttp2Get() {
        final String okHttp2Get = "OKHTTP2GET";

        TransportStubWrapper transportStubWrapper = TransportStubWrapper.create(myGrpc);
        TransportAsyncStubWrapper transportAsyncStubWrapper = TransportAsyncStubWrapper.create(myGrpc);


        Map<Long, List<Event>> httpEventsMap =
                transportAsyncStubWrapper.getEvents(
                        5,
                        event ->
                                event.getKind() == Event.Kind.NETWORK_HTTP_CONNECTION
                                        || event.getKind() == Event.Kind.NETWORK_HTTP_THREAD,
                        () -> {
                            myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "runOkHttp2Get");
                            assertThat(myAndroidDriver.waitForInput(okHttp2Get)).isTrue();
                        });
        assertThat(httpEventsMap).hasSize(1);
        for (List<Event> httpEvents : httpEventsMap.values()) {
            List<Event> httpConnectionEvents = httpEvents.stream()
                    .filter(e -> e.getKind() == Event.Kind.NETWORK_HTTP_CONNECTION)
                    .collect(Collectors.toList());
            // No request body in get test. So we only expect to get back 4 events instead of 5.
            assertThat(httpConnectionEvents).hasSize(4);
            Event requestStartedEvent = httpConnectionEvents.get(0);
            Network.NetworkHttpConnectionData.HttpRequestStarted requestData =
                    requestStartedEvent.getNetworkHttpConnection().getHttpRequestStarted();
            assertThat(requestData.getMethod()).isEqualTo("GET");
            assertThat(requestData.getUrl()).contains("?method=" + okHttp2Get);

            Event responseStartedEvent = httpConnectionEvents.get(1);
            Network.NetworkHttpConnectionData.HttpResponseStarted responseData =
                    responseStartedEvent.getNetworkHttpConnection().getHttpResponseStarted();
            assertThat(responseData.getFields()).contains(EXPECTED_RESPONSE_CODE);

            assertThat(httpEvents.stream()
                    .filter(e -> e.getKind() == Event.Kind.NETWORK_HTTP_THREAD).count()).isEqualTo(1);

            Event responseCompletedEvent = httpConnectionEvents.get(2);
            Network.NetworkHttpConnectionData.HttpResponseCompleted responseCompleteData =
                    responseCompletedEvent.getNetworkHttpConnection().getHttpResponseCompleted();
            assertThat(responseCompleteData.getPayloadId())
                    .isEqualTo(responseCompletedEvent.getGroupId() + "_response");
            assertThat(transportStubWrapper.toBytes(responseCompleteData.getPayloadId())).contains(okHttp2Get);
        }
    }

    @Test
    public void testOkHttp2Post() {
        final String okHttp2Post = "OKHTTP2POST";

        TransportStubWrapper transportStubWrapper = TransportStubWrapper.create(myGrpc);
        TransportAsyncStubWrapper transportAsyncStubWrapper = TransportAsyncStubWrapper.create(myGrpc);


        Map<Long, List<Event>> httpEventsMap =
                transportAsyncStubWrapper.getEvents(
                        6,
                        event ->
                                event.getKind() == Event.Kind.NETWORK_HTTP_CONNECTION
                                        || event.getKind() == Event.Kind.NETWORK_HTTP_THREAD,
                        () -> {
                            myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "runOkHttp2Post");
                            assertThat(myAndroidDriver.waitForInput(okHttp2Post)).isTrue();
                        });
        assertThat(httpEventsMap).hasSize(1);
        for (List<Event> httpEvents : httpEventsMap.values()) {
            List<Event> httpConnectionEvents = httpEvents.stream()
                    .filter(e -> e.getKind() == Event.Kind.NETWORK_HTTP_CONNECTION)
                    .collect(Collectors.toList());
            assertThat(httpConnectionEvents).hasSize(5);
            Event requestStartedEvent = httpConnectionEvents.get(0);
            Network.NetworkHttpConnectionData.HttpRequestStarted data =
                    requestStartedEvent.getNetworkHttpConnection().getHttpRequestStarted();
            assertThat(data.getMethod()).isEqualTo("POST");
            assertThat(data.getUrl()).contains("?method=" + okHttp2Post);

            Event requestCompleteEvent = httpConnectionEvents.get(1);
            Network.NetworkHttpConnectionData.HttpRequestCompleted requestData =
                    requestCompleteEvent.getNetworkHttpConnection().getHttpRequestCompleted();
            assertThat(requestData.getPayloadId())
                    .isEqualTo(requestCompleteEvent.getGroupId() + "_request");
            assertThat(transportStubWrapper.toBytes(requestData.getPayloadId()))
                    .isEqualTo("OkHttp2 request body");
        }
    }

    @Test
    public void testOkHttp2AndOkHttp3Get() {
        String okhttp2AndOkHttp3Get = "OKHTTP2ANDOKHTTP3GET";

        TransportAsyncStubWrapper transportAsyncStubWrapper = TransportAsyncStubWrapper.create(myGrpc);

        Map<Long, List<Event>> httpEventsMap =
                transportAsyncStubWrapper.getEvents(
                        10,
                        event ->
                                event.getKind() == Event.Kind.NETWORK_HTTP_CONNECTION
                                        || event.getKind() == Event.Kind.NETWORK_HTTP_THREAD,
                        () -> {
                            myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "runOkHttp2AndOkHttp3Get");
                            assertThat(myAndroidDriver.waitForInput(okhttp2AndOkHttp3Get)).isTrue();
                        });
        assertThat(httpEventsMap).hasSize(2);

        String urlQuery = "?method=" + okhttp2AndOkHttp3Get;
        for (List<Event> httpEvents : httpEventsMap.values()) {
            List<Event> httpConnectionEvents = httpEvents.stream()
                    .filter(e -> e.getKind() == Event.Kind.NETWORK_HTTP_CONNECTION)
                    .collect(Collectors.toList());
            assertThat(httpConnectionEvents).hasSize(4);
            Event requestStartedEvent = httpConnectionEvents.get(0);
            Network.NetworkHttpConnectionData.HttpRequestStarted data =
                    requestStartedEvent.getNetworkHttpConnection().getHttpRequestStarted();
            assertThat(data.getMethod()).isEqualTo("GET");
            assertThat(data.getUrl()).contains(urlQuery);
        }
    }

    @Test
    public void testOkHttp2GetAbortedByError() {
        String okHttp2Error = "OKHTTP2ERROR";

        TransportAsyncStubWrapper transportAsyncStubWrapper = TransportAsyncStubWrapper.create(myGrpc);

        Map<Long, List<Event>> httpEventsMap =
                transportAsyncStubWrapper.getEvents(
                        8,
                        event ->
                                event.getKind() == Event.Kind.NETWORK_HTTP_CONNECTION
                                        || event.getKind() == Event.Kind.NETWORK_HTTP_THREAD,
                        () -> {
                            myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "runOkHttp2GetAbortedByError");
                            assertThat(myAndroidDriver.waitForInput(okHttp2Error)).isTrue();
                        });
        assertThat(httpEventsMap).hasSize(2);

        Long[] connectionIds = new Long[2];
        httpEventsMap.keySet().toArray(connectionIds);

        // First connection should have been aborted
        List<Event> failedEvents = httpEventsMap.get(connectionIds[0]);
        List<Event> failedConnectionEvents = failedEvents.stream()
                .filter(e -> e.getKind() == Event.Kind.NETWORK_HTTP_CONNECTION)
                .collect(Collectors.toList());
        Event abortedEvent = failedConnectionEvents.get(failedConnectionEvents.size() - 1);
        assertThat(abortedEvent.getNetworkHttpConnection().hasHttpClosed()).isTrue();
        assertThat(abortedEvent.getNetworkHttpConnection().getHttpClosed().getCompleted()).isFalse();

        // Even though the request was aborted, it should still have thread information available
        assertThat(failedEvents.stream()
                .filter(e -> e.getKind() == Event.Kind.NETWORK_HTTP_THREAD).count()).isEqualTo(1);

        List<Event> successEvents = httpEventsMap.get(connectionIds[1]);
        List<Event> successConnectionEvents = successEvents.stream()
                .filter(e -> e.getKind() == Event.Kind.NETWORK_HTTP_CONNECTION)
                .collect(Collectors.toList());
        assertThat(successConnectionEvents).hasSize(4);
        Event responseStartedEvent = successConnectionEvents.get(1);
        Network.NetworkHttpConnectionData.HttpResponseStarted responseData =
                responseStartedEvent.getNetworkHttpConnection().getHttpResponseStarted();
        assertThat(responseData.getFields()).contains(EXPECTED_RESPONSE_CODE);
        Event successEvent = successConnectionEvents.get(3);
        assertThat(successEvent.getNetworkHttpConnection().getHttpClosed().getCompleted()).isTrue();
    }

    @Test
    public void testOkHttp3GetAbortedByError() {
        String okHttp3Error = "OKHTTP3ERROR";

        TransportAsyncStubWrapper transportAsyncStubWrapper = TransportAsyncStubWrapper.create(myGrpc);

        Map<Long, List<Event>> httpEventsMap =
                transportAsyncStubWrapper.getEvents(
                        8,
                        event ->
                                event.getKind() == Event.Kind.NETWORK_HTTP_CONNECTION
                                        || event.getKind() == Event.Kind.NETWORK_HTTP_THREAD,
                        () -> {
                            myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "runOkHttp3GetAbortedByError");
                            assertThat(myAndroidDriver.waitForInput(okHttp3Error)).isTrue();
                        });
        assertThat(httpEventsMap).hasSize(2);

        Long[] connectionIds = new Long[2];
        httpEventsMap.keySet().toArray(connectionIds);

        // First connection should have been aborted
        List<Event> failedEvents = httpEventsMap.get(connectionIds[0]);
        List<Event> failedConnectionEvents = failedEvents.stream()
                .filter(e -> e.getKind() == Event.Kind.NETWORK_HTTP_CONNECTION)
                .collect(Collectors.toList());
        Event abortedEvent = failedConnectionEvents.get(failedConnectionEvents.size() - 1);
        assertThat(abortedEvent.getNetworkHttpConnection().hasHttpClosed()).isTrue();
        assertThat(abortedEvent.getNetworkHttpConnection().getHttpClosed().getCompleted()).isFalse();

        // Even though the request was aborted, it should still have thread information available
        assertThat(failedEvents.stream()
                .filter(e -> e.getKind() == Event.Kind.NETWORK_HTTP_THREAD).count()).isEqualTo(1);

        List<Event> successEvents = httpEventsMap.get(connectionIds[1]);
        List<Event> successConnectionEvents = successEvents.stream()
                .filter(e -> e.getKind() == Event.Kind.NETWORK_HTTP_CONNECTION)
                .collect(Collectors.toList());
        assertThat(successConnectionEvents).hasSize(4);
        Event responseStartedEvent = successConnectionEvents.get(1);
        Network.NetworkHttpConnectionData.HttpResponseStarted responseData =
                responseStartedEvent.getNetworkHttpConnection().getHttpResponseStarted();
        assertThat(responseData.getFields()).contains(EXPECTED_RESPONSE_CODE);
        Event successEvent = successConnectionEvents.get(3);
        assertThat(successEvent.getNetworkHttpConnection().getHttpClosed().getCompleted()).isTrue();
    }
}