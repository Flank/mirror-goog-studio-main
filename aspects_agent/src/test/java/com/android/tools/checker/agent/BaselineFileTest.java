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
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

import com.android.tools.checker.TestUtils;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import org.junit.Test;

public class BaselineFileTest {

    @Test
    public void parseBaselineFile() {
        String baselineContent =
                "com.example.MyClass.topMethod|com.example.MyClass.caller\n"
                        + "com.example.MyOtherClass.callee|com.example.CrossClass.caller\n";

        StackTraceElement[] stackTrace1 =
                stackTraceBuilder(
                        "com.example.MyClass", "topMethod", "com.example.MyClass", "caller");
        StackTraceElement[] stackTrace2 =
                stackTraceBuilder(
                        "com.example.MyOtherClass", "callee", "com.example.CrossClass", "caller");

        Baseline baseline = Baseline.getInstance(true);
        baseline.parse(new ByteArrayInputStream(baselineContent.getBytes()));
        assertTrue(baseline.isIgnored(stackTrace1));
        assertTrue(baseline.isIgnored(stackTrace2));
    }

    @Test
    public void addStackTracesToBaseline() {
        StackTraceElement[] stackTrace =
                stackTraceBuilder(
                        "com.example.MyClass", "topMethod", "com.example.MyClass", "caller");
        Baseline baseline = Baseline.getInstance(true);
        assertFalse(baseline.isIgnored(stackTrace));

        baseline.ignoreStackTrace(stackTrace);
        assertTrue(baseline.isIgnored(stackTrace));
    }

    @Test
    public void logActiveBaselineEntries() throws IOException {
        File log = File.createTempFile("activeBaselineLog", ".txt");
        Baseline baseline = new Baseline(log);

        StackTraceElement[] stackTrace =
                stackTraceBuilder(
                        "Test2",
                        "blockingMethod",
                        TestUtils.getReflectMethodAccessorClass(),
                        "invoke0");
        boolean isIgnored = baseline.isIgnored(stackTrace);
        assertFalse(isIgnored);
        List<String> content = Files.readAllLines(log.toPath());
        // Baseline doesn't contain the blocking method, so it shouldn't be logged
        assertTrue(content.isEmpty());

        // Add the method to the baseline.
        baseline.ignoreStackTrace(stackTrace);
        isIgnored = baseline.isIgnored(stackTrace);
        assertTrue(isIgnored);
        content = Files.readAllLines(log.toPath());
        // Method is logged as an active baseline entry
        assertEquals(1, content.size());
        assertEquals("Test2.blockingMethod|" + TestUtils.getReflectInvokeMethod(), content.get(0));

        // Check again if the method is ignored. It shouldn't be logged again.
        isIgnored = baseline.isIgnored(stackTrace);
        assertTrue(isIgnored);
        content = Files.readAllLines(log.toPath());
        // Method is logged as an active baseline entry
        assertEquals(1, content.size());
        assertEquals("Test2.blockingMethod|" + TestUtils.getReflectInvokeMethod(), content.get(0));

        // Add another method to the baseline but don't check if it's ignored yet.
        stackTrace =
                stackTraceBuilder(
                        "Test2",
                        "blockingMethod2",
                        TestUtils.getReflectMethodAccessorClass(),
                        "invoke0");
        baseline.ignoreStackTrace(stackTrace);
        content = Files.readAllLines(log.toPath());
        // Baseline has changed, but the newly added method was not yet logged.
        assertEquals(1, content.size());

        // Check now if the second method is ignored. Since it is, it should be logged.
        isIgnored = baseline.isIgnored(stackTrace);
        assertTrue(isIgnored);
        content = Files.readAllLines(log.toPath());
        assertEquals(2, content.size());
        assertEquals("Test2.blockingMethod|" + TestUtils.getReflectInvokeMethod(), content.get(0));
        assertEquals("Test2.blockingMethod2|" + TestUtils.getReflectInvokeMethod(), content.get(1));
    }
}
