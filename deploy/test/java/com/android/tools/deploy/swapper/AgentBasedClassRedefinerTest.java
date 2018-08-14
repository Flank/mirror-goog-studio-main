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
package com.android.tools.deploy.swapper;

import com.android.tools.deploy.proto.Deploy;
import com.android.tools.deploy.proto.Deploy.AgentConfig;
import com.android.tools.fakeandroid.FakeAndroidDriver;
import com.android.tools.fakeandroid.ProcessRunner;
import com.google.protobuf.ByteString;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

public class AgentBasedClassRedefinerTest extends ClassRedefinerTestBase {
    private static final String ACTIVITY_CLASS =
            "com.android.tools.deploy.swapper.testapp.TestActivity";

    // Location of the initial test-app that has the ACTIVITY_CLASS
    private static final String DEX_LOCATION = ProcessRunner.getProcessPath("app.dex.location");

    private static final String PACKAGE = "package.name.does.matter.in.this.test.";
    
    private static final String LOCAL_HOST = "127.0.0.1";
    private static final int RETURN_VALUE_TIMEOUT = 1000;

    private FakeAndroidDriver android;

    private LocalTestAgentBasedClassRedefiner redefiner;

    private TemporaryFolder dexLocation;

    @Before
    public void setUp() throws Exception {
        dexLocation = new TemporaryFolder();
        dexLocation.create();

        android = new FakeAndroidDriver(LOCAL_HOST);
        android.start();

        redefiner = new LocalTestAgentBasedClassRedefiner(android, dexLocation, false);
    }

    @After
    public void tearDown() {
        android.stop();
    }

    private Deploy.SwapRequest createRequest(String name, String dex, boolean restart)
            throws IOException {
        Deploy.ClassDef classDef =
                Deploy.ClassDef.newBuilder()
                        .setName(name)
                        .setDex(ByteString.copyFrom(getSplittedDex(dex)))
                        .build();
        Deploy.SwapRequest request =
                Deploy.SwapRequest.newBuilder()
                        .addClasses(classDef)
                        .setPackageName(PACKAGE)
                        .setRestartActivity(restart)
                        .build();
        return request;
    }

    @Test
    public void testSimpleClassRedefinition() throws Exception {
        android.loadDex(DEX_LOCATION);
        android.launchActivity(ACTIVITY_CLASS);

        android.triggerMethod(ACTIVITY_CLASS, "getStatus");
        Assert.assertTrue(android.waitForInput("NOT SWAPPED", RETURN_VALUE_TIMEOUT));

        Deploy.SwapRequest request =
                createRequest(
                        "com.android.tools.deploy.swapper.testapp.Target",
                        "com/android/tools/deploy/swapper/testapp/Target.dex",
                        true);
        redefiner.redefine(request);

        android.triggerMethod(ACTIVITY_CLASS, "getStatus");
        Assert.assertTrue(android.waitForInput("JUST SWAPPED", RETURN_VALUE_TIMEOUT));
    }

    @Test
    public void testSimpleClassRedefinitionWithActivityRestart() throws Exception {
        redefiner = new LocalTestAgentBasedClassRedefiner(android, dexLocation, true);

        android.loadDex(DEX_LOCATION);
        android.launchActivity(ACTIVITY_CLASS);

        android.triggerMethod(ACTIVITY_CLASS, "getStatus");
        Assert.assertTrue(android.waitForInput("NOT SWAPPED", RETURN_VALUE_TIMEOUT));

        Deploy.SwapRequest request =
                createRequest(
                        "com.android.tools.deploy.swapper.testapp.Target",
                        "com/android/tools/deploy/swapper/testapp/Target.dex",
                        true);
        redefiner.redefine(request);

        android.triggerMethod(ACTIVITY_CLASS, "getStatus");

        Assert.assertTrue(
                android.waitForInput("APPLICATION_INFO_CHANGED triggered", RETURN_VALUE_TIMEOUT));
        Assert.assertTrue(android.waitForInput("JUST SWAPPED", RETURN_VALUE_TIMEOUT));
    }

    @Test
    public void testFailedClassRedefinitionWithActivityRestart() throws Exception {
        redefiner = new LocalTestAgentBasedClassRedefiner(android, dexLocation, true);

        android.loadDex(DEX_LOCATION);
        android.launchActivity(ACTIVITY_CLASS);

        android.triggerMethod(ACTIVITY_CLASS, "getStatus");
        Assert.assertTrue(android.waitForInput("NOT SWAPPED 0", RETURN_VALUE_TIMEOUT));

        Deploy.SwapRequest request =
                createRequest(
                        "com.android.tools.deploy.swapper.testapp.Target",
                        "com/android/tools/deploy/swapper/testapp/ClinitTarget.dex",
                        true);
        // TODO: Change shouldSucceed to false when we can actually check the status of the swap.
        redefiner.redefine(request, true /* Because we don't know the return status ATM */);

        android.triggerMethod(ACTIVITY_CLASS, "getStatus");
        Assert.assertTrue(android.waitForInput("NOT SWAPPED 1", RETURN_VALUE_TIMEOUT));
    }

    /**
     * This method test a few things: 1. We can redefine a class before the class is loaded. 2.
     * Class initializiers are not loaded when redefinition completes. 3. Class is succesfully
     * redefined and initializers are invoke upon class loading and behave as expected.
     */
    @Test
    public void testRedefiningNotLoaded() throws Exception {
        android.loadDex(DEX_LOCATION);
        android.launchActivity(ACTIVITY_CLASS);

        Deploy.SwapRequest request =
                createRequest(
                        "com.android.tools.deploy.swapper.testapp.ClinitTarget",
                        "com/android/tools/deploy/swapper/testapp/ClinitTarget.dex",
                        true);
        redefiner.redefine(request);

        android.triggerMethod(ACTIVITY_CLASS, "printCounter");
        Assert.assertTrue(android.waitForInput("TestActivity.counter = 0", RETURN_VALUE_TIMEOUT));

        android.triggerMethod(ACTIVITY_CLASS, "getClassInitializerStatus");
        Assert.assertTrue(android.waitForInput("ClinitTarget JUST SWAPPED", RETURN_VALUE_TIMEOUT));

        android.triggerMethod(ACTIVITY_CLASS, "printCounter");
        Assert.assertTrue(android.waitForInput("TestActivity.counter = 1", RETURN_VALUE_TIMEOUT));
    }

    @Test
    public void testFailHotSwap() throws Exception {
        android.loadDex(DEX_LOCATION);
        android.launchActivity(ACTIVITY_CLASS);

        android.triggerMethod(ACTIVITY_CLASS, "getFailedTargetStatus");
        Assert.assertTrue(android.waitForInput("FailedTarget NOT SWAPPED 0", RETURN_VALUE_TIMEOUT));

        Deploy.SwapRequest request =
                createRequest(
                        "com.android.tools.deploy.swapper.testapp.FailedTarget",
                        "com/android/tools/deploy/swapper/testapp/FailedTarget.dex",
                        true);
        redefiner.redefine(request, false);

        android.triggerMethod(ACTIVITY_CLASS, "getFailedTargetStatus");
        Assert.assertTrue(android.waitForInput("FailedTarget NOT SWAPPED 1", RETURN_VALUE_TIMEOUT));
    }

    @Test
    public void testFailHotSwapAllOrNone() throws Exception {
        android.loadDex(DEX_LOCATION);
        android.launchActivity(ACTIVITY_CLASS);

        android.triggerMethod(ACTIVITY_CLASS, "getFailedTargetStatus");
        Assert.assertTrue(android.waitForInput("FailedTarget NOT SWAPPED 0", RETURN_VALUE_TIMEOUT));

        android.triggerMethod(ACTIVITY_CLASS, "getStatus");
        Assert.assertTrue(android.waitForInput("NOT SWAPPED 0", RETURN_VALUE_TIMEOUT));

        Deploy.ClassDef classDef1 =
                Deploy.ClassDef.newBuilder()
                        .setName("com.android.tools.deploy.swapper.testapp.FailedTarget")
                        .setDex(
                                ByteString.copyFrom(
                                        getSplittedDex(
                                                "com/android/tools/deploy/swapper/testapp/FailedTarget.dex")))
                        .build();
        Deploy.ClassDef classDef2 =
                Deploy.ClassDef.newBuilder()
                        .setName("com.android.tools.deploy.swapper.testapp.Target")
                        .setDex(
                                ByteString.copyFrom(
                                        getSplittedDex(
                                                "com/android/tools/deploy/swapper/testapp/Target.dex")))
                        .build();
        Deploy.SwapRequest request =
                Deploy.SwapRequest.newBuilder()
                        .addClasses(classDef1)
                        .addClasses(classDef2)
                        .setPackageName(PACKAGE)
                        .setRestartActivity(false)
                        .build();
        redefiner.redefine(request, false);

        android.triggerMethod(ACTIVITY_CLASS, "getFailedTargetStatus");
        Assert.assertTrue(android.waitForInput("FailedTarget NOT SWAPPED 1", RETURN_VALUE_TIMEOUT));

        android.triggerMethod(ACTIVITY_CLASS, "getStatus");
        Assert.assertTrue(android.waitForInput("NOT SWAPPED 1", RETURN_VALUE_TIMEOUT));
    }

    private static class LocalTestAgentBasedClassRedefiner extends ClassRedefiner {
        private final TemporaryFolder messageDir;
        private final FakeAndroidDriver android;
        private String messageLocation;

        private LocalTestAgentBasedClassRedefiner(
                FakeAndroidDriver android, TemporaryFolder messageDir, boolean shouldRestart) {
            this.android = android;
            this.messageDir = messageDir;
        }

        public void redefine(Deploy.SwapRequest request, boolean shouldSucceed) {
            try {
                AgentConfig.Builder agentConfig = AgentConfig.newBuilder();
                agentConfig.setSwapRequest(request);

                File pb = Files.createTempFile("messageDir", "msg.pb").toFile();
                FileOutputStream out = new FileOutputStream(pb);
                agentConfig.build().writeTo(out);

                android.attachAgent(
                        ProcessRunner.getProcessPath("swap.agent.location")
                                + "="
                                + pb.getAbsolutePath(),
                        shouldSucceed);
                // TODO(acleung): We have no way to two way communicate with the Agent for now
                // so we are just going wait for a log statement.
                if (shouldSucceed) {
                    android.waitForInput("Done HotSwapping!");
                } else {
                    android.waitForError("Hot swap failed.", RETURN_VALUE_TIMEOUT);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void redefine(Deploy.SwapRequest request) {
            redefine(request, true);
        }
    }
}
