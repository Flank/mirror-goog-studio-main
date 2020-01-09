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

package com.android.tools.profiler;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.profiler.proto.EventProfiler.ActivityDataResponse;
import com.android.tools.profiler.proto.EventProfiler.EventDataRequest;
import com.android.tools.profiler.proto.EventServiceGrpc;
import com.android.tools.transport.TestUtils;
import com.android.tools.transport.device.SdkLevel;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * A very basic test which ensures that a profiler session successfully started.
 */
@RunWith(Parameterized.class)
public class BasicTest {
    @Parameterized.Parameters
    public static Collection<SdkLevel> parameters() {
        return Arrays.asList(SdkLevel.N, SdkLevel.O);
    }

    private static final String ACTIVITY_CLASS = "com.activity.EmptyActivity";

    @Rule public final ProfilerRule myProfilerRule;

    public BasicTest(SdkLevel sdkLevel) {
        myProfilerRule = new ProfilerRule(ACTIVITY_CLASS, sdkLevel);
    }

    @Test
    public void findActivityNameBySession() {
        // Verify that the activity we launched was created.
        EventServiceGrpc.EventServiceBlockingStub eventStub =
                EventServiceGrpc.newBlockingStub(
                        myProfilerRule.getTransportRule().getGrpc().getChannel());

        EventDataRequest request =
                EventDataRequest.newBuilder().setSession(myProfilerRule.getSession()).build();
        ActivityDataResponse response =
                TestUtils.waitForAndReturn(
                        () -> eventStub.getActivityData(request),
                        value -> !value.getDataList().isEmpty());

        assertThat(response.getData(0).getName()).isEqualTo("EmptyActivity");
    }
}
