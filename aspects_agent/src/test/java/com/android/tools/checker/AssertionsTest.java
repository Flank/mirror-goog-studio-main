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

package com.android.tools.checker;

import static com.android.tools.checker.agent.AgentTestUtils.callMethod;
import static com.android.tools.checker.agent.AgentTestUtils.loadAndTransform;
import static com.android.tools.checker.agent.AgentTestUtils.stackTraceBuilder;
import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;

import com.android.tools.checker.agent.Baseline;
import com.google.common.collect.ImmutableMap;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class AssertionsTest {

    @Test
    public void exceptionThrownWhenMethodCalledFromWrongThread()
            throws IOException, IllegalAccessException, InstantiationException {
        Set<String> notFound = new HashSet<>();
        ImmutableMap<String, String> matcher =
                ImmutableMap.of(
                        "@com.android.tools.checker.BlockingTest",
                        "com.android.tools.checker.Assertions#assertIsEdt");
        Object instance = loadAndTransform("Test2", matcher, notFound::add).newInstance();

        try {
            Baseline.getInstance(true); // Force a fresh baseline
            callMethod(instance, "blockingMethod");
            fail("Exception was expected to be thrown as the method was not called from EDT.");
        } catch (Exception ignore) {
            // Expected
        }
    }

    @Test
    public void methodInBaselineSkipsExceptionThrow()
            throws IOException, IllegalAccessException, InstantiationException {
        Set<String> notFound = new HashSet<>();
        ImmutableMap<String, String> matcher =
                ImmutableMap.of(
                        "@com.android.tools.checker.BlockingTest",
                        "com.android.tools.checker.Assertions#assertIsEdt");
        Object instance = loadAndTransform("Test2", matcher, notFound::add).newInstance();
        String baseline = "Test2.blockingMethod|sun.reflect.NativeMethodAccessorImpl.invoke0";
        Baseline.getInstance(true).parse(new ByteArrayInputStream(baseline.getBytes()));

        try {
            callMethod(instance, "blockingMethod");
        } catch (Exception ignore) {
            fail("Whitelisted method is not supposed to cause an exception to be thrown.");
        }
    }

    @Test
    public void exceptionNotThrownWhenGeneratingBaseline()
            throws IOException, IllegalAccessException, InstantiationException {
        System.setProperty("aspects.baseline.export.path", "/any/path");
        Set<String> notFound = new HashSet<>();
        ImmutableMap<String, String> matcher =
                ImmutableMap.of(
                        "@com.android.tools.checker.BlockingTest",
                        "com.android.tools.checker.Assertions#assertIsEdt");
        Object instance = loadAndTransform("Test2", matcher, notFound::add).newInstance();

        try {
            Baseline baseline = Baseline.getInstance(true);
            callMethod(instance, "blockingMethod");
            // In addition to not throwing an exception, we should add the method to the whitelist
            StackTraceElement[] stackTrace =
                    stackTraceBuilder(
                            "Test2",
                            "blockingMethod",
                            "sun.reflect.NativeMethodAccessorImpl",
                            "invoke0");
            assertTrue(baseline.isWhitelisted(stackTrace));
            System.clearProperty("aspects.baseline.export.path");
        } catch (Exception ignore) {
            fail("Exception is not supposed to be thrown when generating the baseline.");
        }
    }
}
