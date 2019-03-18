/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.checker.agent;

import static com.android.tools.checker.agent.AgentTestUtils.stackTraceBuilder;
import static junit.framework.TestCase.assertTrue;

import java.io.ByteArrayInputStream;
import org.junit.Test;

public class BaselineFileTest {
    @Test
    public void parseBaselineFile() {
        String baseline =
                "com.example.MyClass.topMethod|com.example.MyClass.caller\n"
                        + "com.example.MyOtherClass.callee|com.example.CrossClass.caller\n";

        StackTraceElement[] stackTrace1 =
                stackTraceBuilder(
                        "com.example.MyClass", "topMethod", "com.example.MyClass", "caller");
        StackTraceElement[] stackTrace2 =
                stackTraceBuilder(
                        "com.example.MyOtherClass", "callee", "com.example.CrossClass", "caller");

        Baseline.parse(new ByteArrayInputStream(baseline.getBytes()));
        assertTrue(Baseline.isWhitelisted(stackTrace1));
        assertTrue(Baseline.isWhitelisted(stackTrace2));
    }
}
