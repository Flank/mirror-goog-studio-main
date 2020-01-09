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
public final class HttpUrlTest {
    @Parameterized.Parameters
    public static Collection<SdkLevel> parameters() {
        return Arrays.asList(SdkLevel.N, SdkLevel.O, SdkLevel.P);
    }

    private static final String ACTIVITY_CLASS = "com.activity.network.HttpUrlActivity";

    @Rule public final ProfilerRule myProfilerRule;

    private FakeAndroidDriver myAndroidDriver;
    private Grpc myGrpc;
    private Session mySession;

    public HttpUrlTest(SdkLevel sdkLevel) {
        myProfilerRule = new ProfilerRule(ACTIVITY_CLASS, sdkLevel);
    }

    @Before
    public void setup() {
        myAndroidDriver = myProfilerRule.getTransportRule().getAndroidDriver();
        myGrpc = myProfilerRule.getTransportRule().getGrpc();
        mySession = myProfilerRule.getSession();
    }

    @Test
    public void testHttpGet() {
        String getSuccess = "HttpUrlGet SUCCESS";
        myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "runGet");
        assertThat(myAndroidDriver.waitForInput(getSuccess)).isTrue();

        NetworkStubWrapper networkStubWrapper = NetworkStubWrapper.create(myGrpc);
        TransportStubWrapper transportStubWrapper = TransportStubWrapper.create(myGrpc);

        NetworkProfiler.HttpRangeResponse httpRangeResponse =
                networkStubWrapper.getNonEmptyHttpRange(mySession);
        assertThat(httpRangeResponse.getDataList().size()).isEqualTo(1);

        long connectionId = httpRangeResponse.getDataList().get(0).getConnId();
        HttpDetailsResponse requestDetails = networkStubWrapper.getHttpDetails(connectionId, Type.REQUEST);
        assertThat(requestDetails.getRequest().getUrl().contains("?activity=HttpUrlGet")).isTrue();
        TestUtils.waitFor(
                () -> {
                    HttpDetailsResponse details =
                            networkStubWrapper.getHttpDetails(connectionId, Type.RESPONSE);
                    return details.getResponse().getFields().contains("HTTP/1.0 200 OK");
                });

        String payloadId = networkStubWrapper.getPayloadId(connectionId, Type.RESPONSE_BODY);
        assertThat(payloadId.isEmpty()).isFalse();

        assertThat(transportStubWrapper.toBytes(payloadId)).isEqualTo(getSuccess);
    }

    @Test
    public void testHttpPost() {
        String postSuccess = "HttpUrlPost SUCCESS";
        myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "runPost");
        assertThat(myAndroidDriver.waitForInput(postSuccess)).isTrue();

        NetworkStubWrapper networkStubWrapper = NetworkStubWrapper.create(myGrpc);
        TransportStubWrapper transportStubWrapper = TransportStubWrapper.create(myGrpc);

        NetworkProfiler.HttpRangeResponse httpRangeResponse =
                networkStubWrapper.getNonEmptyHttpRange(mySession);
        assertThat(httpRangeResponse.getDataList().size()).isEqualTo(1);

        long connectionId = httpRangeResponse.getDataList().get(0).getConnId();
        HttpDetailsResponse requestDetails = networkStubWrapper.getHttpDetails(connectionId, Type.REQUEST);
        assertThat(requestDetails.getRequest().getUrl().contains("?activity=HttpUrlPost")).isTrue();

        String payloadId = networkStubWrapper.getPayloadId(connectionId, Type.REQUEST_BODY);
        assertThat(payloadId.isEmpty()).isFalse();
        assertThat(transportStubWrapper.toBytes(payloadId)).isEqualTo("TestRequestBody");
    }

    @Test
    public void testHttpGet_CallResposeMethodBeforeConnect() {
        String getSuccess = "HttpUrlGet SUCCESS";
        myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "runGet_CallResponseMethodBeforeConnect");
        assertThat(myAndroidDriver.waitForInput(getSuccess)).isTrue();

        NetworkStubWrapper networkStubWrapper = NetworkStubWrapper.create(myGrpc);
        NetworkProfiler.HttpRangeResponse httpRangeResponse =
                networkStubWrapper.getNonEmptyHttpRange(mySession);
        assertThat(httpRangeResponse.getDataList().size()).isEqualTo(1);

        long connectionId = httpRangeResponse.getDataList().get(0).getConnId();
        TestUtils.waitFor(
                () -> {
                    HttpDetailsResponse details =
                            networkStubWrapper.getHttpDetails(connectionId, Type.RESPONSE);
                    return details.getResponse().getFields().contains("HTTP/1.0 200 OK");
                });
        // If we got here, we're done - our response completed as expected
    }

}
