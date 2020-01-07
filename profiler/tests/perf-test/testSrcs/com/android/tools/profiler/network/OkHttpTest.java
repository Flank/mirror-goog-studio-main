/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.android.tools.profiler.ProfilerRule;
import com.android.tools.profiler.proto.Common.Session;
import com.android.tools.profiler.proto.NetworkProfiler;
import com.android.tools.profiler.proto.NetworkProfiler.HttpConnectionData;
import com.android.tools.profiler.proto.NetworkProfiler.HttpDetailsRequest.Type;
import com.android.tools.profiler.proto.NetworkProfiler.HttpDetailsResponse;
import com.android.tools.transport.TestUtils;
import com.android.tools.transport.device.SdkLevel;
import com.android.tools.transport.grpc.Grpc;
import com.android.tools.transport.grpc.TransportStubWrapper;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class OkHttpTest {

    @Parameterized.Parameters
    public static Collection<SdkLevel> parameters() {
        return Arrays.asList(SdkLevel.N, SdkLevel.O, SdkLevel.P);
    }

    private static final String ACTIVITY_CLASS = "com.activity.network.OkHttpActivity";

    @Rule public final ProfilerRule myProfilerRule;

    private FakeAndroidDriver myAndroidDriver;
    private Grpc myGrpc;
    private Session mySession;

    public OkHttpTest(SdkLevel sdkLevel) {
        myProfilerRule = new ProfilerRule(ACTIVITY_CLASS, sdkLevel);
    }

    @Before
    public void before() {
        myAndroidDriver = myProfilerRule.getTransportRule().getAndroidDriver();
        myGrpc = myProfilerRule.getTransportRule().getGrpc();
        mySession = myProfilerRule.getSession();
    }

    @Test
    public void testOkHttp3Get() {
        String okHttp3Get = "OKHTTP3GET";
        myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "runOkHttp3Get");
        assertThat(myAndroidDriver.waitForInput(okHttp3Get)).isTrue();

        NetworkStubWrapper networkStubWrapper = NetworkStubWrapper.create(myGrpc);
        TransportStubWrapper transportStubWrapper = TransportStubWrapper.create(myGrpc);

        NetworkProfiler.HttpRangeResponse httpRangeResponse =
                networkStubWrapper.getNonEmptyHttpRange(mySession);
        assertThat(httpRangeResponse.getDataList().size()).isEqualTo(1);

        long connectionId = httpRangeResponse.getDataList().get(0).getConnId();
        HttpDetailsResponse requestDetails = networkStubWrapper.getHttpDetails(connectionId, Type.REQUEST);
        assertThat(requestDetails.getRequest().getUrl().contains("?method=" + okHttp3Get)).isTrue();
        waitForResponseFields200(networkStubWrapper, connectionId);

        HttpDetailsResponse threadDetails =
                networkStubWrapper.getHttpDetails(connectionId, Type.ACCESSING_THREADS);
        assertThat(threadDetails.getAccessingThreads().getThreadList().size()).isEqualTo(1);

        String payloadId = networkStubWrapper.getPayloadId(connectionId, Type.RESPONSE_BODY);
        assertThat(payloadId.isEmpty()).isFalse();
        assertThat(transportStubWrapper.toBytes(payloadId).contains(okHttp3Get)).isTrue();
    }

    @Test
    public void testOkHttp3Post() {
        String okHttp3Post = "OKHTTP3POST";
        myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "runOkHttp3Post");
        assertThat(myAndroidDriver.waitForInput(okHttp3Post)).isTrue();

        NetworkStubWrapper networkStubWrapper = NetworkStubWrapper.create(myGrpc);
        TransportStubWrapper transportStubWrapper = TransportStubWrapper.create(myGrpc);

        NetworkProfiler.HttpRangeResponse httpRangeResponse =
                networkStubWrapper.getNonEmptyHttpRange(mySession);
        assertThat(httpRangeResponse.getDataList().size()).isEqualTo(1);

        long connectionId = httpRangeResponse.getDataList().get(0).getConnId();
        HttpDetailsResponse requestDetails = networkStubWrapper.getHttpDetails(connectionId, Type.REQUEST);
        assertThat(requestDetails.getRequest().getUrl().contains("?method=" + okHttp3Post))
            .isTrue();

        String payloadId = networkStubWrapper.getPayloadId(connectionId, Type.REQUEST_BODY);
        assertThat(payloadId.isEmpty()).isFalse();
        assertThat(transportStubWrapper.toBytes(payloadId)).isEqualTo("OkHttp3 request body");
    }

    @Test
    public void testOkHttp2Get() {
        String okHttp2Get = "OKHTTP2GET";
        myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "runOkHttp2Get");
        assertThat(myAndroidDriver.waitForInput(okHttp2Get)).isTrue();

        NetworkStubWrapper networkStubWrapper = NetworkStubWrapper.create(myGrpc);
        TransportStubWrapper transportStubWrapper = TransportStubWrapper.create(myGrpc);

        NetworkProfiler.HttpRangeResponse httpRangeResponse =
                networkStubWrapper.getNonEmptyHttpRange(mySession);
        assertThat(httpRangeResponse.getDataList().size()).isEqualTo(1);

        long connectionId = httpRangeResponse.getDataList().get(0).getConnId();
        HttpDetailsResponse requestDetails = networkStubWrapper.getHttpDetails(connectionId, Type.REQUEST);
        assertThat(requestDetails.getRequest().getUrl().contains("?method=" + okHttp2Get)).isTrue();
        waitForResponseFields200(networkStubWrapper, connectionId);

        HttpDetailsResponse threadDetails =
                networkStubWrapper.getHttpDetails(connectionId, Type.ACCESSING_THREADS);
        assertThat(threadDetails.getAccessingThreads().getThreadList().size()).isEqualTo(1);

        String payloadId = networkStubWrapper.getPayloadId(connectionId, Type.RESPONSE_BODY);
        assertThat(payloadId.isEmpty()).isFalse();
        assertThat(transportStubWrapper.toBytes(payloadId).contains(okHttp2Get)).isTrue();
    }

    @Test
    public void testOkHttp2Post() {
        String okHttp2Post = "OKHTTP2POST";
        myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "runOkHttp2Post");
        assertThat(myAndroidDriver.waitForInput(okHttp2Post)).isTrue();

        NetworkStubWrapper networkStubWrapper = NetworkStubWrapper.create(myGrpc);
        TransportStubWrapper transportStubWrapper = TransportStubWrapper.create(myGrpc);

        NetworkProfiler.HttpRangeResponse httpRangeResponse =
                networkStubWrapper.getNonEmptyHttpRange(mySession);
        assertThat(httpRangeResponse.getDataList().size()).isEqualTo(1);

        long connectionId = httpRangeResponse.getDataList().get(0).getConnId();
        HttpDetailsResponse requestDetails = networkStubWrapper.getHttpDetails(connectionId, Type.REQUEST);
        assertThat(requestDetails.getRequest().getUrl().contains("?method=" + okHttp2Post))
            .isTrue();

        String payloadId = networkStubWrapper.getPayloadId(connectionId, Type.REQUEST_BODY);
        assertThat(payloadId.isEmpty()).isFalse();
        assertThat(transportStubWrapper.toBytes(payloadId)).isEqualTo("OkHttp2 request body");
    }

    @Test
    public void testOkHttp2AndOkHttp3Get() {
        String okhttp2AndOkHttp3Get = "OKHTTP2ANDOKHTTP3GET";
        myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "runOkHttp2AndOkHttp3Get");
        assertThat(myAndroidDriver.waitForInput(okhttp2AndOkHttp3Get)).isTrue();

        NetworkStubWrapper networkStubWrapper = NetworkStubWrapper.create(myGrpc);

        NetworkProfiler.HttpRangeResponse httpRangeResponse =
                TestUtils.waitForAndReturn(
                        () -> networkStubWrapper.getNonEmptyHttpRange(mySession),
                        resp -> resp.getDataList().size() == 2);

        long connectionId = httpRangeResponse.getDataList().get(0).getConnId();
        HttpDetailsResponse requestDetails = networkStubWrapper.getHttpDetails(connectionId, Type.REQUEST);
        String urlQuery = "?method=" + okhttp2AndOkHttp3Get;
        assertThat(requestDetails.getRequest().getUrl().contains(urlQuery)).isTrue();

        connectionId = httpRangeResponse.getDataList().get(1).getConnId();
        requestDetails = networkStubWrapper.getHttpDetails(connectionId, Type.REQUEST);
        assertThat(requestDetails.getRequest().getUrl().contains(urlQuery)).isTrue();
    }

    @Test
    public void testOkHttp2GetAbortedByError() {
        String okHttp2Error = "OKHTTP2ERROR";
        myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "runOkHttp2GetAbortedByError");
        assertThat(myAndroidDriver.waitForInput(okHttp2Error)).isTrue();

        assertNetworkErrorBehavior(NetworkStubWrapper.create(myGrpc));
    }

    @Test
    public void testOkHttp3GetAbortedByError() {
        String okHttp3Error = "OKHTTP3ERROR";
        myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "runOkHttp3GetAbortedByError");
        assertThat(myAndroidDriver.waitForInput(okHttp3Error)).isTrue();

        assertNetworkErrorBehavior(NetworkStubWrapper.create(myGrpc));
    }

    private void assertNetworkErrorBehavior(NetworkStubWrapper networkStubWrapper) {
        // Wait until get two responses: 1 aborted and 1 completed.
        // Both failed and successful requests should have valid time ranges.
        NetworkProfiler.HttpRangeResponse httpRangeResponse =
                TestUtils.waitForAndReturn(
                        networkStubWrapper.createHttpRangeSupplier(mySession),
                        resp ->
                                resp.getDataList().size() == 2
                                        && resp.getDataList()
                                                .stream()
                                                .allMatch(item -> (item.getEndTimestamp() > 0)));

        // The first request should have no response fields after being aborted
        HttpConnectionData connectionAborted = httpRangeResponse.getDataList().get(0);
        HttpDetailsResponse responseDetails =
                networkStubWrapper.getHttpDetails(connectionAborted.getConnId(), Type.RESPONSE);
        assertThat(responseDetails.getResponse().getFields()).isEmpty();
        // TODO(b/69328111): Once the error message is being propagated through, check it here.

        // Even though the request was aborted, it should still have thread information available
        TestUtils.waitFor(
                () -> {
                    HttpDetailsResponse threadDetails =
                            networkStubWrapper.getHttpDetails(
                                    connectionAborted.getConnId(), Type.ACCESSING_THREADS);
                    return threadDetails.getAccessingThreads().getThreadList().size() == 1;
                });

        // The second request should have completed normally
        HttpConnectionData connectionSuccess = httpRangeResponse.getDataList().get(1);
        waitForResponseFields200(networkStubWrapper, connectionSuccess.getConnId());

        // The successful connection should naturally start after the failed connection finished)
        assertThat(connectionAborted.getEndTimestamp())
                .isLessThan(connectionSuccess.getStartTimestamp());
    }

    private static void waitForResponseFields200(NetworkStubWrapper stubWrapper, long connId) {
        TestUtils.waitFor(
                () -> {
                    HttpDetailsResponse details = stubWrapper.getHttpDetails(connId, Type.RESPONSE);
                    return details.getResponse().getFields().contains("response-status-code = 200");
                });
    }
}
