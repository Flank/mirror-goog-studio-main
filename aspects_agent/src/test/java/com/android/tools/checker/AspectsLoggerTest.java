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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.checker.agent.Baseline;
import com.google.common.collect.ImmutableMap;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class AspectsLoggerTest {

    @Test
    public void exceptionThrownWhenMethodCalledFromWrongThread()
            throws IOException, IllegalAccessException, InstantiationException,
                    NoSuchMethodException {
        File log = File.createTempFile("log", ".txt");

        Set<String> notFound = new HashSet<>();
        ImmutableMap<String, String> matcher =
                ImmutableMap.of(
                        "@com.android.tools.checker.BlockingTest",
                        "com.android.tools.checker.AspectsLogger#logIfNotEdt");
        Object instance = loadAndTransform("Test2", matcher, notFound::add).newInstance();

        // Log file not set
        callMethod(instance, "blockingMethod");
        List<String> content = Files.readAllLines(log.toPath());
        assertTrue(content.isEmpty());

        // Log file set. Method whitelisted.
        AspectsLogger.aspectsAgentLog = log;
        String baseline = "Test2.blockingMethod|sun.reflect.NativeMethodAccessorImpl.invoke0";
        Baseline.getInstance(true).parse(new ByteArrayInputStream(baseline.getBytes()));
        callMethod(instance, "blockingMethod");
        content = Files.readAllLines(log.toPath());
        assertTrue(content.isEmpty());

        // Log file set. Method not whitelisted.
        Baseline.getInstance(true); // Empty baseline
        callMethod(instance, "blockingMethod");
        content = Files.readAllLines(log.toPath());
        assertEquals(1, content.size());
        assertEquals(
                "Test2.blockingMethod|sun.reflect.NativeMethodAccessorImpl.invoke0",
                content.get(0));
    }
}
