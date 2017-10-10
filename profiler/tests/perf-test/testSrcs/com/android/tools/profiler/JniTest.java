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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class JniTest {
    @Parameterized.Parameters
    public static Collection<Boolean> data() {
        return Arrays.asList(new Boolean[] {false, true});
    }

    private boolean myIsOPlusDevice;
    private static final String ACTIVITY_CLASS = "com.activity.NativeCodeActivity";

    public JniTest(boolean isOPlusDevice) {
        myIsOPlusDevice = isOPlusDevice;
    }

    // Just create native activity and see that it can load native library.
    @Test
    public void createNativeActivity() throws Exception {
        PerfDriver driver = new PerfDriver(myIsOPlusDevice);
        driver.start(ACTIVITY_CLASS);
        GrpcUtils grpc = driver.getGrpc();

        // Verify that the activity we launched was created.
        ActivityDataResponse response = grpc.getActivity(grpc.getProcessId());
        assertThat(response.getData(0).getName()).isEqualTo("NativeCodeActivity");
    }

    // Test correctness of invokation of native code.
    @Test
    public void callSomeNativeCode() throws Exception {
        PerfDriver driver = new PerfDriver(myIsOPlusDevice);
        driver.start(ACTIVITY_CLASS);
        driver.getFakeAndroidDriver().triggerMethod(ACTIVITY_CLASS, "CallNativeToString");
        assertThat(driver.getFakeAndroidDriver().waitForInput("CallNativeToString - ok")).isTrue();
    }

    // Allocate and release global jni references.
    @Test
    public void createSomeGlobalRefs() throws Exception {
        PerfDriver driver = new PerfDriver(myIsOPlusDevice);
        driver.start(ACTIVITY_CLASS);
        driver.getFakeAndroidDriver().triggerMethod(ACTIVITY_CLASS, "CreateSomeGlobalRefs");
        assertThat(driver.getFakeAndroidDriver().waitForInput("CreateSomeGlobalRefs - ok")).isTrue();
    }
}
