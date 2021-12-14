/*
 * Copyright (C) 2021 The Android Open Source Project
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
public class LiveEditInstrumentationTest extends AgentTestBase {
    @Parameterized.Parameters
    public static Collection<String> artFlags() {
        return ALL_ART_FLAGS;
    }

    private LiveEditClient installer;

    public LiveEditInstrumentationTest(String artFlag) {
        super(artFlag);
    }

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        installer = new LiveEditClient(android, dexLocation);
        installer.startServer();
    }

    @After
    @Override
    public void tearDown() {
        super.tearDown();
        installer.stopServer();
    }

    @Test
    public void testTransformSucceeds() throws Exception {
        android.loadDex(DEX_LOCATION);
        android.launchActivity(ACTIVITY_CLASS);

        Deploy.LiveEditClass clazz =
                Deploy.LiveEditClass.newBuilder().setClassName("app/StubTarget").build();
        Deploy.LiveEditRequest request =
                Deploy.LiveEditRequest.newBuilder()
                        .setTargetClass(clazz)
                        .setPackageName(PACKAGE)
                        .build();

        installer.update(request);
        Deploy.LiveEditResponse response = installer.getLiveEditResponse();
        Assert.assertEquals(Deploy.LiveEditResponse.Status.OK, response.getStatus());
    }

    // TODO: Move this out of this class or rename this class's name to be something that
    //       is more general.
    @Test
    public void testFunctionRecompose() throws Exception {
        android.loadDex(DEX_LOCATION);
        android.launchActivity(ACTIVITY_CLASS);

        Deploy.LiveEditClass clazz =
                Deploy.LiveEditClass.newBuilder()
                        .setClassName("pkg/LiveEditRecomposeTarget")
                        .build();
        Deploy.LiveEditRequest request =
                Deploy.LiveEditRequest.newBuilder()
                        .setTargetClass(clazz)
                        .setComposable(true)
                        .setStartOffset(1234)
                        .setEndOffset(12340)
                        .setPackageName(PACKAGE)
                        .build();

        installer.update(request);
        Deploy.LiveEditResponse response = installer.getLiveEditResponse();
        Assert.assertEquals(Deploy.LiveEditResponse.Status.OK, response.getStatus());

        Assert.assertTrue(
                android.waitForInput("invalidateGroupsWithKey(1122)", RETURN_VALUE_TIMEOUT));
    }

    protected static class LiveEditClient extends InstallServerTestClient {
        protected LiveEditClient(FakeAndroidDriver android, TemporaryFolder messageDir) {
            super(android, messageDir);
        }

        private void update(Deploy.LiveEditRequest request) {
            Deploy.SendAgentMessageRequest agentRequest =
                    Deploy.SendAgentMessageRequest.newBuilder()
                            .setAgentCount(1)
                            .setAgentRequest(
                                    Deploy.AgentRequest.newBuilder().setLeRequest(request).build())
                            .build();
            Deploy.InstallServerRequest serverRequest =
                    Deploy.InstallServerRequest.newBuilder().setSendRequest(agentRequest).build();
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

        protected Deploy.LiveEditResponse getLiveEditResponse() throws IOException {
            return getAgentResponse().getLeResponse();
        }
    }
}
