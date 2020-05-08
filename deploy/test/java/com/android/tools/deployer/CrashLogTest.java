/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.deployer;

import com.android.tools.deploy.proto.Deploy;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Test cases where the agent fail to redefine classes for various reasons. */
@RunWith(Parameterized.class)
public class CrashLogTest extends AgentBasedClassRedefinerTestBase {

    @Parameterized.Parameters
    public static Collection<String> artFlags() {
        return ALL_ART_FLAGS;
    }

    public CrashLogTest(String artFlag) {
        super(artFlag);
    }

    @Test
    public void testExceptionAfterSwap() throws Exception {
        android.loadDex(DEX_LOCATION);
        android.launchActivity(ACTIVITY_CLASS);

        android.triggerMethod(ACTIVITY_CLASS, "getStatus");
        Assert.assertTrue(android.waitForInput("NOT SWAPPED 0", RETURN_VALUE_TIMEOUT));

        Deploy.SwapRequest request = createRequest(ImmutableMap.of(), ImmutableMap.of(), false);
        request = request.toBuilder().setOverlaySwap(true).build();
        redefiner.redefine(request);

        android.triggerMethod(ACTIVITY_CLASS, "fakeCrash");

        List<File> logs = getExceptionLogs();
        Assert.assertTrue(logs.size() == 1);
        try (FileInputStream stream = new FileInputStream(logs.get(0))) {
            Deploy.AgentExceptionLog log = Deploy.AgentExceptionLog.parseFrom(stream);
            Assert.assertTrue(log.getAgentAttachTimeNs() <= log.getEventTimeNs());
            Assert.assertTrue(log.getAgentAttachCount() == 1);
            Assert.assertTrue(
                    log.getAgentPurpose()
                            == Deploy.AgentExceptionLog.AgentPurpose.APPLY_CODE_CHANGES);
        }
    }

    @Test
    public void testExceptionAfterStartup() throws Exception {
        startupAgent();
        android.loadDex(DEX_LOCATION);
        android.launchActivity(ACTIVITY_CLASS);
        android.triggerMethod(ACTIVITY_CLASS, "fakeCrash");

        List<File> logs = getExceptionLogs();
        Assert.assertTrue(logs.size() == 1);
        try (FileInputStream stream = new FileInputStream(logs.get(0))) {
            Deploy.AgentExceptionLog log = Deploy.AgentExceptionLog.parseFrom(stream);
            Assert.assertTrue(log.getAgentAttachTimeNs() <= log.getEventTimeNs());
            Assert.assertTrue(log.getAgentAttachCount() == 1);
            Assert.assertTrue(
                    log.getAgentPurpose() == Deploy.AgentExceptionLog.AgentPurpose.STARTUP_AGENT);
        }
    }

    private List<File> getExceptionLogs() {
        File logDir = new File(dataDir, ".agent-logs");
        ArrayList<File> logFiles = new ArrayList<>();
        try (DirectoryStream<Path> dir = Files.newDirectoryStream(logDir.toPath(), "*.log")) {
            for (Path log : dir) {
                logFiles.add(log.toFile());
            }
        } catch (IOException io) {
            // Ignore.
        }
        return logFiles;
    }
}
