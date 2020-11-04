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
import com.android.tools.fakeandroid.FakeAndroidDriver;
import com.android.tools.fakeandroid.ProcessRunner;
import com.android.tools.idea.protobuf.ByteString;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

public abstract class AgentBasedClassRedefinerTestBase extends AgentTestBase {

    // Location of all the dex files to be swapped in to test hotswapping.
    private static final String DEX_SWAP_LOCATION =
            ProcessRunner.getProcessPath("app.swap.dex.location");

    protected LocalTestAgentBasedClassRedefiner redefiner;

    public AgentBasedClassRedefinerTestBase(String artFlag) {
        super(artFlag);
    }

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        redefiner = new LocalTestAgentBasedClassRedefiner(android, dexLocation);
        redefiner.startServer();
    }

    @After
    @Override
    public void tearDown() {
        super.tearDown();
        redefiner.stopServer();
    }

    protected Deploy.SwapRequest createRequest(
            String name, String dex, boolean restart, Deploy.ClassDef.FieldReInitState... states)
            throws IOException {
        Deploy.ClassDef.Builder classBuilder =
                Deploy.ClassDef.newBuilder()
                        .setName(name)
                        .setDex(ByteString.copyFrom(getSplittedDex(dex)));
        for (Deploy.ClassDef.FieldReInitState state : states) {
            classBuilder.addFields(state);
        }

        Deploy.ClassDef classDef = classBuilder.build();

        Deploy.SwapRequest request =
                Deploy.SwapRequest.newBuilder()
                        .addModifiedClasses(classDef)
                        .setPackageName(PACKAGE)
                        .setRestartActivity(restart)
                        .setStructuralRedefinition(true)
                        .setVariableReinitialization(true)
                        .build();
        return request;
    }

    protected Deploy.SwapRequest createRequest(
            Map<String, String> modifiedClasses, Map<String, String> newClasses, boolean restart)
            throws IOException {

        Deploy.SwapRequest.Builder request =
                Deploy.SwapRequest.newBuilder().setPackageName(PACKAGE).setRestartActivity(restart);

        for (String name : modifiedClasses.keySet()) {
            request.addModifiedClasses(
                    Deploy.ClassDef.newBuilder()
                            .setName(name)
                            .setDex(
                                    ByteString.copyFrom(
                                            getSplittedDex(modifiedClasses.get(name)))));
        }

        for (String name : newClasses.keySet()) {
            request.addNewClasses(
                    Deploy.ClassDef.newBuilder()
                            .setName(name)
                            .setDex(ByteString.copyFrom(getSplittedDex(newClasses.get(name)))));
        }

        return request.build();
    }

    /** A helper to communicate SwapRequest to the agent with the on-host installer. */
    protected static class LocalTestAgentBasedClassRedefiner extends InstallServerTestClient {
        protected LocalTestAgentBasedClassRedefiner(
                FakeAndroidDriver android, TemporaryFolder messageDir) {
            super(android, messageDir);
        }

        protected void redefine(Deploy.SwapRequest request, boolean unused) {
            Deploy.SendAgentMessageRequest agentRequest =
                    Deploy.SendAgentMessageRequest.newBuilder()
                            .setAgentCount(1)
                            .setAgentRequest(
                                    Deploy.AgentRequest.newBuilder()
                                            .setSwapRequest(request)
                                            .build())
                            .build();
            Deploy.InstallServerRequest serverRequest =
                    Deploy.InstallServerRequest.newBuilder()
                            .setSendRequest(agentRequest)
                            .build();
            redefine(serverRequest.toByteArray());
        }

        protected void redefine(byte[] message) {
            try {
                sendMessage(message);
                attachAgent();
            } catch (IOException e) {
                System.err.println(e);
            }
        }

        public void redefine(Deploy.SwapRequest request) {
            redefine(request, true);
        }

        protected Deploy.AgentSwapResponse getSwapAgentResponse() throws IOException {
            return getAgentResponse().getSwapResponse();
        }
    }

    /**
     * The ":test-app-swap" rule output contains a list of dex file that we can hotswap. Use this
     * method to extract a class from the output.
     *
     * @return The single file dex code of a given class compiled in our dex_library
     *     ":test-app-swap" rule.
     */
    protected static byte[] getSplittedDex(String name) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(DEX_SWAP_LOCATION))) {
            for (ZipEntry entry = zis.getNextEntry(); entry != null; entry = zis.getNextEntry()) {
                if (!entry.getName().equals(name)) {
                    continue;
                }

                byte[] buffer = new byte[1024];
                ByteArrayOutputStream dexContent = new ByteArrayOutputStream();

                int len;
                while ((len = zis.read(buffer)) > 0) {
                    dexContent.write(buffer, 0, len);
                }
                return dexContent.toByteArray();
            }
            Assert.fail("Cannot find " + name + " in " + DEX_SWAP_LOCATION);
            return null;
        }
    }
}
