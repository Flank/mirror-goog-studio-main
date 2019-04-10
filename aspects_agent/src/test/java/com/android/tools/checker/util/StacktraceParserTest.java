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

package com.android.tools.checker.util;

import static com.android.tools.checker.agent.AgentTestUtils.stackTraceBuilder;
import static com.android.tools.checker.util.StacktraceParser.formattedCallstack;
import static com.android.tools.checker.util.StacktraceParser.stackTraceToString;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class StacktraceParserTest {

    @Test
    public void stackTraceToBaselineName() {
        StackTraceElement[] stackTrace =
                stackTraceBuilder(
                        "com.example.MyClass", "topMethod", "com.example.MyClass", "caller");
        assertEquals(
                "com.example.MyClass.topMethod|com.example.MyClass.caller",
                stackTraceToString(stackTrace));
    }

    @Test
    public void formatStackTraceName() {
        String expected = "com.example.MyClass.topMethod\n  com.example.MyClass.caller\n";
        assertEquals(
                expected,
                formattedCallstack("com.example.MyClass.topMethod|com.example.MyClass.caller"));
    }
}
