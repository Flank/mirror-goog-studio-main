/*
 * Copyright (C) 2020 The Android Open Source Project
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

import java.io.IOException;

import java.util.Collection;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class LiveLiteralUpdateTest extends AgentTestBase {
    @Parameterized.Parameters
    public static Collection<String> artFlags() {
        return ALL_ART_FLAGS;
    }
    private LocalLiveLiteralUpdateClient installer;

    public LiveLiteralUpdateTest(String artFlag) {
        super(artFlag);
    }

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        installer = new LocalLiveLiteralUpdateClient(android, dexLocation);
        installer.startServer();
    }

    @After
    @Override
    public void tearDown() {
        super.tearDown();
        installer.stopServer();
    }

    @Test
    public void testSimpleClassRedefinition() throws Exception {
        android.loadDex(DEX_LOCATION);
        android.launchActivity(ACTIVITY_CLASS);

        android.triggerMethod(ACTIVITY_CLASS, "getStatus");
        Assert.assertTrue(android.waitForInput("NOT SWAPPED", RETURN_VALUE_TIMEOUT));

        Deploy.LiveLiteralUpdateRequest request = createRequest();
        installer.update(request);

        // TODO: Next step is to set up the reply.
        // Deploy.AgentLiveLiteralUpdateResponse response = installer.getLiveLiteralAgentResponse();
        // Assert.assertEquals(Deploy.AgentLiveLiteralUpdateResponse.Status.OK, response.getStatus());

        // TODO: For now we just check if the agent successfully received the request.
        Assert.assertTrue(android.waitForInput("Live Literal Update on VM", RETURN_VALUE_TIMEOUT));
    }


    protected Deploy.LiveLiteralUpdateRequest createRequest() {
        // PLACEHOLDER REQUEST FOR NOW.
        Deploy.LiveLiteralUpdateRequest request =
                Deploy.LiveLiteralUpdateRequest.newBuilder().build();
        return request;
    }

            /** A helper to communicate SwapRequest to the agent with the on-host installer. */
    protected static class LocalLiveLiteralUpdateClient extends InstallServerTestClient {
        protected LocalLiveLiteralUpdateClient(
                FakeAndroidDriver android, TemporaryFolder messageDir) {
            super(android, messageDir);
        }

        private void update(Deploy.LiveLiteralUpdateRequest request) {
            Deploy.SendAgentMessageRequest agentRequest =
                    Deploy.SendAgentMessageRequest.newBuilder()
                            .setAgentCount(1)
                            .setAgentRequest(
                                    Deploy.AgentRequest.newBuilder()
                                            .setLiveLiteralRequest(request)
                                            .build())
                            .build();
            Deploy.InstallServerRequest serverRequest =
                    Deploy.InstallServerRequest.newBuilder()
                            .setType(Deploy.InstallServerRequest.Type.HANDLE_REQUEST)
                            .setSendRequest(agentRequest)
                            .build();
            callInstaller(serverRequest.toByteArray());
        }

        void callInstaller(byte[] message) {
            try {
                sendMessage(message);
                attachAgent();
            } catch (IOException e) {
                System.err.println(e);
            }
        }

        protected Deploy.AgentLiveLiteralUpdateResponse getLiveLiteralAgentResponse() throws IOException {
            return getAgentResponse().getLiveLiteralResponse();
        }
    }
}
