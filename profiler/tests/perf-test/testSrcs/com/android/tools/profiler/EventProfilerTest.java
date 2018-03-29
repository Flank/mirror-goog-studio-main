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

import java.util.Arrays;
import java.util.Collection;
import java.util.regex.Pattern;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class EventProfilerTest {
    @Parameterized.Parameters
    public static Collection<Boolean> data() {
        return Arrays.asList(new Boolean[] {false, true});
    }

    private boolean myIsOPlusDevice;
    private PerfDriver myPerfDriver;
    private GrpcUtils myGrpc;
    private FakeAndroidDriver myAndroidDriver;

    public EventProfilerTest(boolean isOPlusDevice) {
        myIsOPlusDevice = isOPlusDevice;
    }

    @Before
    public void setup() throws Exception {
        myPerfDriver = new PerfDriver(myIsOPlusDevice);
        myPerfDriver.start("com.activity.event.EventActivity");
        myGrpc = myPerfDriver.getGrpc();
        myAndroidDriver = myPerfDriver.getFakeAndroidDriver();
        if (myIsOPlusDevice) {
            myGrpc.beginSessionWithAgent(
                    myPerfDriver.getPid(), myPerfDriver.getCommunicationPort());
        } else {
            myGrpc.beginSession(myPerfDriver.getPid());
        }
    }

    @Test
    public void testInputMethodManagerDoesntLeakInputConnection() throws Exception {
        // Capture initial handle to InputConnection
        myAndroidDriver.triggerMethod("com.activity.event.EventActivity", "printConnection");
        String connection =
                myAndroidDriver
                        .waitForInput(Pattern.compile("(.*)(Connection\\:)(?<result>.*)"))
                        .trim();
        // Accept input and wait for the input thread to loop around capturing required input.
        myAndroidDriver.triggerMethod("com.activity.event.EventActivity", "acceptInput");
        Thread.sleep(1000);

        // Verify that we have captured input and our wrapper captures the expected InputConnection
        myAndroidDriver.triggerMethod("com.activity.event.EventActivity", "printConnection");
        String wrappedConnection =
                myAndroidDriver
                        .waitForInput(Pattern.compile("(.*)(WrapperConnection\\:)(?<result>.*)"))
                        .trim();
        assertThat(wrappedConnection).matches(connection);

        // Disable capturing input and verify the inner connection has been set back to null.
        myAndroidDriver.triggerMethod("com.activity.event.EventActivity", "blockInput");
        Thread.sleep(1000);
        myAndroidDriver.triggerMethod("com.activity.event.EventActivity", "printConnection");
        assertThat(myAndroidDriver.waitForInput("WrapperConnection: null")).isTrue();
    }

    @Test
    public void testNoRecursionOnWeakReferenceApis() throws Exception {
        // Accept input and wait for the input thread to loop around capturing required input.
        myAndroidDriver.triggerMethod("com.activity.event.EventActivity", "acceptInput");
        // Wait a little, we should have the same wrapped connection we initially had.
        Thread.sleep(500);
        myAndroidDriver.triggerMethod(
                "com.activity.event.EventActivity", "printInputConnectionTreeDepth");
        String depth =
                myAndroidDriver
                        .waitForInput(
                                Pattern.compile("(.*)(InputConnectionTree Depth\\: )(?<result>.*)"))
                        .trim();
        // 1 Is the wrapper, 1 is the underlaying connection.
        assertThat(Integer.parseInt(depth)).isEqualTo(2);
    }
}
