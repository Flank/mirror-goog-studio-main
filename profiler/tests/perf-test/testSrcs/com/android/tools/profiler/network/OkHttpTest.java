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

import com.android.tools.profiler.FakeAndroidDriver;
import com.android.tools.profiler.GrpcUtils;
import com.android.tools.profiler.PerfDriver;
import com.android.tools.profiler.proto.NetworkProfiler;
import com.android.tools.profiler.proto.NetworkProfiler.HttpDetailsRequest.Type;
import com.android.tools.profiler.proto.NetworkProfiler.HttpDetailsResponse;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profiler.proto.Profiler.BytesRequest;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class OkHttpTest {

    @Parameterized.Parameters
    public static Collection<Boolean> data() {
        return Arrays.asList(new Boolean[] {false, true});
    }

    private static final String ACTIVITY_CLASS = "com.activity.network.OkHttpActivity";

    private boolean myIsOPlusDevice = true;
    private PerfDriver myPerfDriver;
    private FakeAndroidDriver myAndroidDriver;
    private GrpcUtils myGrpc;

    public OkHttpTest(boolean isOPlusDevice) {
        myIsOPlusDevice = isOPlusDevice;
    }

    @Before
    public void before() throws IOException {
        myPerfDriver = new PerfDriver(myIsOPlusDevice);
        myPerfDriver.start(ACTIVITY_CLASS);
        myAndroidDriver = myPerfDriver.getFakeAndroidDriver();
        myGrpc = myPerfDriver.getGrpc();
    }

    @Test
    public void testOkHttp3Get() {
        String okHttp3Get = "OKHTTP3GET";
        myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "runOkHttp3Get");
        assertThat(myAndroidDriver.waitForInput(okHttp3Get)).isTrue();

        NetworkStubWrapper stubWrapper = new NetworkStubWrapper(myGrpc.getNetworkStub());
        NetworkProfiler.HttpRangeResponse httpRangeResponse =
                stubWrapper.getAllHttpRange(myGrpc.getProcessId());
        assertThat(httpRangeResponse.getDataList().size()).isEqualTo(1);

        long connectionId = httpRangeResponse.getDataList().get(0).getConnId();
        HttpDetailsResponse requestDetails = stubWrapper.getHttpDetails(connectionId, Type.REQUEST);
        assertThat(requestDetails.getRequest().getUrl().contains("?method=" + okHttp3Get)).isTrue();

        HttpDetailsResponse responseDetails =
                stubWrapper.getHttpDetails(connectionId, Type.RESPONSE);
        String responseFields = responseDetails.getResponse().getFields();
        assertThat(responseFields.contains("response-status-code = 200")).isTrue();

        String payloadId = stubWrapper.getResponsePayloadId(connectionId);
        assertThat(payloadId.isEmpty()).isFalse();
        Profiler.BytesResponse bytesResponse =
                myGrpc.getProfilerStub()
                        .getBytes(BytesRequest.newBuilder().setId(payloadId).build());
        assertThat(bytesResponse.getContents().toStringUtf8().contains(okHttp3Get)).isTrue();
    }

    @Test
    public void testOkHttp3Post() {
        String okHttp3Post = "OKHTTP3POST";
        myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "runOkHttp3Post");
        assertThat(myAndroidDriver.waitForInput(okHttp3Post)).isTrue();

        NetworkStubWrapper stubWrapper = new NetworkStubWrapper(myGrpc.getNetworkStub());
        NetworkProfiler.HttpRangeResponse httpRangeResponse =
                stubWrapper.getAllHttpRange(myGrpc.getProcessId());
        assertThat(httpRangeResponse.getDataList().size()).isEqualTo(1);

        long connectionId = httpRangeResponse.getDataList().get(0).getConnId();
        HttpDetailsResponse requestDetails = stubWrapper.getHttpDetails(connectionId, Type.REQUEST);
        assertThat(requestDetails.getRequest().getUrl().contains("?method=" + okHttp3Post))
            .isTrue();

        HttpDetailsResponse responseDetails =
                stubWrapper.getHttpDetails(connectionId, Type.RESPONSE);
        String responseFields = responseDetails.getResponse().getFields();
        assertThat(responseFields.contains("response-status-code = 200")).isTrue();
        // TODO: Assert request body when it is supported.
    }

    @Test
    public void testOkHttp2Get() {
        String okHttp2Get = "OKHTTP2GET";
        myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "runOkHttp2Get");
        assertThat(myAndroidDriver.waitForInput(okHttp2Get)).isTrue();

        NetworkStubWrapper stubWrapper = new NetworkStubWrapper(myGrpc.getNetworkStub());
        NetworkProfiler.HttpRangeResponse httpRangeResponse =
                stubWrapper.getAllHttpRange(myGrpc.getProcessId());
        assertThat(httpRangeResponse.getDataList().size()).isEqualTo(1);

        long connectionId = httpRangeResponse.getDataList().get(0).getConnId();
        HttpDetailsResponse requestDetails = stubWrapper.getHttpDetails(connectionId, Type.REQUEST);
        assertThat(requestDetails.getRequest().getUrl().contains("?method=" + okHttp2Get)).isTrue();

        HttpDetailsResponse responseDetails =
                stubWrapper.getHttpDetails(connectionId, Type.RESPONSE);
        String responseFields = responseDetails.getResponse().getFields();
        assertThat(responseFields.contains("response-status-code = 200")).isTrue();

        String payloadId = stubWrapper.getResponsePayloadId(connectionId);
        assertThat(payloadId.isEmpty()).isFalse();
        Profiler.BytesResponse bytesResponse =
                myGrpc.getProfilerStub()
                        .getBytes(BytesRequest.newBuilder().setId(payloadId).build());
        assertThat(bytesResponse.getContents().toStringUtf8().contains(okHttp2Get)).isTrue();
    }

    @Test
    public void testOkHttp2Post() {
        String okHttp2Post = "OKHTTP2POST";
        myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "runOkHttp2Post");
        assertThat(myAndroidDriver.waitForInput(okHttp2Post)).isTrue();

        NetworkStubWrapper stubWrapper = new NetworkStubWrapper(myGrpc.getNetworkStub());
        NetworkProfiler.HttpRangeResponse httpRangeResponse =
                stubWrapper.getAllHttpRange(myGrpc.getProcessId());
        assertThat(httpRangeResponse.getDataList().size()).isEqualTo(1);

        long connectionId = httpRangeResponse.getDataList().get(0).getConnId();
        HttpDetailsResponse requestDetails = stubWrapper.getHttpDetails(connectionId, Type.REQUEST);
        assertThat(requestDetails.getRequest().getUrl().contains("?method=" + okHttp2Post))
            .isTrue();

        HttpDetailsResponse responseDetails =
                stubWrapper.getHttpDetails(connectionId, Type.RESPONSE);
        String responseFields = responseDetails.getResponse().getFields();
        assertThat(responseFields.contains("response-status-code = 200")).isTrue();
        // TODO: Assert request body when it is supported.
    }

    @Test
    public void testOkHttp2AndOkHttp3Get() {
        String okhttp2AndOkHttp3Get = "OKHTTP2ANDOKHTTP3GET";
        myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "runOkHttp2AndOkHttp3Get");
        assertThat(myAndroidDriver.waitForInput(okhttp2AndOkHttp3Get)).isTrue();

        NetworkStubWrapper stubWrapper = new NetworkStubWrapper(myGrpc.getNetworkStub());
        NetworkProfiler.HttpRangeResponse httpRangeResponse =
                stubWrapper.getAllHttpRange(myGrpc.getProcessId());
        assertThat(httpRangeResponse.getDataList().size()).isEqualTo(2);

        long connectionId = httpRangeResponse.getDataList().get(0).getConnId();
        HttpDetailsResponse requestDetails = stubWrapper.getHttpDetails(connectionId, Type.REQUEST);
        String urlQuery = "?method=" + okhttp2AndOkHttp3Get;
        assertThat(requestDetails.getRequest().getUrl().contains(urlQuery)).isTrue();

        connectionId = httpRangeResponse.getDataList().get(1).getConnId();
        requestDetails = stubWrapper.getHttpDetails(connectionId, Type.REQUEST);
        assertThat(requestDetails.getRequest().getUrl().contains(urlQuery)).isTrue();
    }
}
