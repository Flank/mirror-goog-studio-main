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
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class EventProfilerTest {

    private static final String ACTIVITY_CLASS = "com.activity.event.EventActivity";

    @Parameterized.Parameters
    public static Collection<Integer> data() {
        return Arrays.asList(24, 26);
    }

    private FakeAndroidDriver myAndroidDriver;

    @Rule public final PerfDriver myPerfDriver;

    public EventProfilerTest(int sdkLevel) {
        myPerfDriver = new PerfDriver(ACTIVITY_CLASS, sdkLevel);
    }

    @Before
    public void setup() throws Exception {
        myAndroidDriver = myPerfDriver.getFakeAndroidDriver();
    }

    @Test
    public void testInputMethodManagerDoesntLeakInputConnection() throws Exception {
        // Capture initial handle to InputConnection
        myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "printConnection");
        String connection =
                myAndroidDriver
                        .waitForInput(Pattern.compile("(.*)(Connection\\:)(?<result>.*)"))
                        .trim();
        // Accept input and wait for the input thread to loop around capturing required input.
        myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "acceptInput");
        Thread.sleep(1000);

        // Verify that we have captured input and our wrapper captures the expected InputConnection
        myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "printConnection");
        String wrappedConnection =
                myAndroidDriver
                        .waitForInput(Pattern.compile("(.*)(WrapperConnection\\:)(?<result>.*)"))
                        .trim();
        assertThat(wrappedConnection).matches(connection);

        // Disable capturing input and verify the inner connection has been set back to null.
        myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "blockInput");
        myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "clearConnection");
        Thread.sleep(1000);
        myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "printConnection");
        assertThat(myAndroidDriver.waitForInput("WrapperConnection: null")).isTrue();
    }

    @Test
    public void testNoRecursionOnWeakReferenceApis() throws Exception {
        // Accept input and wait for the input thread to loop around capturing required input.
        myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "acceptInput");
        // Wait a little, we should have the same wrapped connection we initially had.
        Thread.sleep(500);
        myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "printInputConnectionTreeDepth");
        String depth =
                myAndroidDriver
                        .waitForInput(
                                Pattern.compile("(.*)(InputConnectionTree Depth\\: )(?<result>.*)"))
                        .trim();
        // 1 Is the wrapper, 1 is the underlaying connection.
        assertThat(Integer.parseInt(depth)).isEqualTo(2);
    }
}
