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
    public void testSimpleUpdate() throws Exception {
        android.loadDex(DEX_LOCATION);
        android.launchActivity(ACTIVITY_CLASS);

        Deploy.LiveLiteralUpdateRequest request =
                Deploy.LiveLiteralUpdateRequest.newBuilder()
                        .addUpdates(
                                Deploy.LiveLiteral.newBuilder()
                                        .setHelperClass("app/LiveLiteralOffsetLookupKt")
                                        .setOffset(10001)
                                        .setType("Ljava/lang/String;")
                                        .setValue("value1"))
                        .addUpdates(
                                Deploy.LiveLiteral.newBuilder()
                                        .setHelperClass("app/LiveLiteralOffsetLookupKt")
                                        .setOffset(10002)
                                        .setType("B")
                                        .setValue("2"))
                        .addUpdates(
                                Deploy.LiveLiteral.newBuilder()
                                        .setHelperClass("app/LiveLiteralOffsetLookupKt")
                                        .setOffset(10003)
                                        .setType("C")
                                        .setValue("X"))
                        .addUpdates(
                                Deploy.LiveLiteral.newBuilder()
                                        .setHelperClass("app/LiveLiteralOffsetLookupKt")
                                        .setOffset(10004)
                                        .setType("J")
                                        .setValue("" + Long.MAX_VALUE))
                        .addUpdates(
                                Deploy.LiveLiteral.newBuilder()
                                        .setHelperClass("app/LiveLiteralOffsetLookupKt")
                                        .setOffset(10005)
                                        .setType("S")
                                        .setValue("5"))
                        .addUpdates(
                                Deploy.LiveLiteral.newBuilder()
                                        .setHelperClass("app/LiveLiteralOffsetLookupKt")
                                        .setOffset(10006)
                                        .setType("F")
                                        .setValue("1.234E-10"))
                        .addUpdates(
                                Deploy.LiveLiteral.newBuilder()
                                        .setHelperClass("app/LiveLiteralOffsetLookupKt")
                                        .setOffset(10007)
                                        .setType("D")
                                        .setValue("" + Double.MIN_VALUE))
                        .addUpdates(
                                Deploy.LiveLiteral.newBuilder()
                                        .setHelperClass("app/LiveLiteralOffsetLookupKt")
                                        .setOffset(10008)
                                        .setType("Z")
                                        .setValue("true"))
                        .setPackageName(PACKAGE)
                        .build();
        installer.update(request);

        Deploy.AgentLiveLiteralUpdateResponse response = installer.getLiveLiteralAgentResponse();
        Assert.assertEquals(Deploy.AgentLiveLiteralUpdateResponse.Status.OK, response.getStatus());

        Assert.assertTrue(android.waitForInput(
                "updateLiveLiteralValue(key1, class java.lang.String, value1)",
                RETURN_VALUE_TIMEOUT));
        Assert.assertTrue(
                android.waitForInput(
                        "updateLiveLiteralValue(key2, class java.lang.Byte, 2)",
                        RETURN_VALUE_TIMEOUT));
        Assert.assertTrue(
                android.waitForInput(
                        "updateLiveLiteralValue(key3, class java.lang.Character, X)",
                        RETURN_VALUE_TIMEOUT));
        Assert.assertTrue(
                android.waitForInput(
                        "updateLiveLiteralValue(key4, class java.lang.Long, "
                                + Long.MAX_VALUE
                                + ")",
                        RETURN_VALUE_TIMEOUT));
        Assert.assertTrue(
                android.waitForInput(
                        "updateLiveLiteralValue(key5, class java.lang.Short, 5)",
                        RETURN_VALUE_TIMEOUT));
        Assert.assertTrue(
                android.waitForInput(
                        "updateLiveLiteralValue(key6, class java.lang.Float, 1.234E-10)",
                        RETURN_VALUE_TIMEOUT));
        Assert.assertTrue(
                android.waitForInput(
                        "updateLiveLiteralValue(key7, class java.lang.Double, "
                                + Double.MIN_VALUE
                                + ")",
                        RETURN_VALUE_TIMEOUT));
        Assert.assertTrue(
                android.waitForInput(
                        "updateLiveLiteralValue(key8, class java.lang.Boolean, true)",
                        RETURN_VALUE_TIMEOUT));
    }

    @Test
    public void testUpdateByOffSet() throws Exception {
        android.loadDex(DEX_LOCATION);
        android.launchActivity(ACTIVITY_CLASS);

        Deploy.LiveLiteralUpdateRequest request =
                Deploy.LiveLiteralUpdateRequest.newBuilder()
                        .addUpdates(
                                Deploy.LiveLiteral.newBuilder()
                                        .setHelperClass("app/LiveLiteralOffsetLookupKt")
                                        .setOffset(159)
                                        .setType("I")
                                        .setValue("100"))
                        .setPackageName(PACKAGE)
                        .build();
        installer.update(request);
        Deploy.AgentLiveLiteralUpdateResponse response = installer.getLiveLiteralAgentResponse();
        Assert.assertEquals(Deploy.AgentLiveLiteralUpdateResponse.Status.OK, response.getStatus());

        Assert.assertTrue(
                android.waitForInput(
                        "updateLiveLiteralValue(Int_func_foo_bar_LiveLiteral_variable, class java.lang.Integer, 100)",
                        RETURN_VALUE_TIMEOUT));
    }

    @Test
    public void testHelperNotFound() throws Exception {
        android.loadDex(DEX_LOCATION);
        android.launchActivity(ACTIVITY_CLASS);

        Deploy.LiveLiteralUpdateRequest request =
                Deploy.LiveLiteralUpdateRequest.newBuilder()
                        .addUpdates(
                                Deploy.LiveLiteral.newBuilder()
                                        .setHelperClass("app/ThisClassCannotBeFound")
                                        .setOffset(159)
                                        .setType("I")
                                        .setValue("100"))
                        .setPackageName(PACKAGE)
                        .build();
        installer.update(request);
        Deploy.AgentLiveLiteralUpdateResponse response = installer.getLiveLiteralAgentResponse();
        Assert.assertEquals(
                Deploy.AgentLiveLiteralUpdateResponse.Status.ERROR, response.getStatus());
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
