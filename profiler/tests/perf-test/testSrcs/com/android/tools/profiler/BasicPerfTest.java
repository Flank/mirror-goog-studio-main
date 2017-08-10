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

import static org.junit.Assert.assertEquals;

import com.android.tools.profiler.proto.EventProfiler.ActivityDataResponse;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class BasicPerfTest {
    @Parameterized.Parameters
    public static Collection<Boolean> data() {
        return Arrays.asList(new Boolean[] {false, true});
    }

    private boolean myIsOPlusDevice;

    public BasicPerfTest(boolean isOPlusDevice) {
        myIsOPlusDevice = isOPlusDevice;
    }

    @Test
    public void testPerfGetActivity() throws Exception {
        PerfDriver myDriver = new PerfDriver(myIsOPlusDevice);
        //Start the test driver.
        myDriver.start("com.activity.MyActivity");
        GrpcUtils grpc = myDriver.getGrpc();

        // Verify that the activity we launched was created.
        ActivityDataResponse response = grpc.getActivity(grpc.getProcessId());
        assertEquals(response.getData(0).getName(), "My Activity");
    }
}
