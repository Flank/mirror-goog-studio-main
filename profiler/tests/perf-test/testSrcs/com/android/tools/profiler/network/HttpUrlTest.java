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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class HttpUrlTest {
    @Parameterized.Parameters
    public static Collection<Boolean> data() {
        return Arrays.asList(new Boolean[] {false, true});
    }

    private static final String ACTIVITY_CLASS = "com.activity.network.HttpUrlActivity";

    private boolean myIsOPlusDevice = true;

    public HttpUrlTest(boolean isOPlusDevice) {
        myIsOPlusDevice = isOPlusDevice;
    }

    @Test
    public void testHttpGet() throws IOException {
        PerfDriver driver = new PerfDriver(myIsOPlusDevice);
        driver.start(ACTIVITY_CLASS);
        final String getSuccess = "HttpUrlGet SUCCESS";
        driver.getFakeAndroidDriver().triggerMethod(ACTIVITY_CLASS, "runGet");
        assertThat(driver.getFakeAndroidDriver().waitForInput(getSuccess)).isTrue();

        GrpcUtils grpc = driver.getGrpc();
        NetworkStubWrapper stubWrapper = new NetworkStubWrapper(grpc.getNetworkStub());
        NetworkProfiler.HttpRangeResponse httpRangeResponse =
                stubWrapper.getAllHttpRange(grpc.getProcessId());
        assertThat(httpRangeResponse.getDataList().size()).isEqualTo(1);

        long connectionId = httpRangeResponse.getDataList().get(0).getConnId();
        HttpDetailsResponse requestDetails = stubWrapper.getHttpDetails(connectionId, Type.REQUEST);
        assertThat(requestDetails.getRequest().getUrl().contains("?activity=HttpUrlGet")).isTrue();

        HttpDetailsResponse responseDetails =
                stubWrapper.getHttpDetails(connectionId, Type.RESPONSE);
        String responseFields = responseDetails.getResponse().getFields();
        assertThat(responseFields.contains("HTTP/1.0 200 OK")).isTrue();

        String payloadId = stubWrapper.getResponsePayloadId(connectionId);
        assertThat(payloadId.isEmpty()).isFalse();
        Profiler.BytesResponse bytesResponse =
                grpc.getProfilerStub().getBytes(BytesRequest.newBuilder().setId(payloadId).build());
        assertThat(bytesResponse.getContents().toStringUtf8()).isEqualTo(getSuccess);
    }

    @Test
    public void testHttpPost() throws IOException {
        PerfDriver driver = new PerfDriver(myIsOPlusDevice);
        driver.start(ACTIVITY_CLASS);
        final String postSuccess = "HttpUrlPost SUCCESS";
        driver.getFakeAndroidDriver().triggerMethod(ACTIVITY_CLASS, "runPost");
        assertThat(driver.getFakeAndroidDriver().waitForInput(postSuccess)).isTrue();

        GrpcUtils grpc = driver.getGrpc();
        NetworkStubWrapper stubWrapper = new NetworkStubWrapper(grpc.getNetworkStub());
        NetworkProfiler.HttpRangeResponse httpRangeResponse =
                stubWrapper.getAllHttpRange(grpc.getProcessId());
        assertThat(httpRangeResponse.getDataList().size()).isEqualTo(1);

        long connectionId = httpRangeResponse.getDataList().get(0).getConnId();
        HttpDetailsResponse requestDetails = stubWrapper.getHttpDetails(connectionId, Type.REQUEST);
        assertThat(requestDetails.getRequest().getUrl().contains("?activity=HttpUrlPost")).isTrue();
        // TODO: Assert request body when it is supported.
    }
}
