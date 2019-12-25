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
import java.util.Arrays;
import java.util.Collection;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class BasicPerfTest {
    @Parameterized.Parameters
    public static Collection<Integer> data() {
        return Arrays.asList(24, 26);
    }

    private static final String ACTIVITY_CLASS = "com.activity.MyActivity";

    @Rule public final PerfDriver myPerfDriver;

    public BasicPerfTest(int sdkLevel) {
        myPerfDriver = new PerfDriver(ACTIVITY_CLASS, sdkLevel);
    }

    @Test
    public void testPerfGetActivity() throws Exception {
        // Verify that the activity we launched was created.
        ActivityDataResponse response =
                TestUtils.waitForAndReturn(
                        () -> myPerfDriver.getGrpc().getActivity(myPerfDriver.getSession()),
                        value -> !value.getDataList().isEmpty());
        assertThat(response.getData(0).getName()).isEqualTo("My Activity");
    }
}
