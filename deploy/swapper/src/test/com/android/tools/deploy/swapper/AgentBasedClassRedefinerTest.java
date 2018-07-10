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

import com.android.tools.fakeandroid.FakeAndroidDriver;
import com.android.tools.fakeandroid.ProcessRunner;
import java.io.FileOutputStream;
import java.util.Map;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class AgentBasedClassRedefinerTest extends ClassRedefinerTestBase {
    private static final String ACTIVITY_CLASS =
            "com.android.tools.deploy.swapper.testapp.TestActivity";

    // Location of the initial test-app that has the ACTIVITY_CLASS
    private static final String DEX_LOCATION = ProcessRunner.getProcessPath("app.dex.location");

    // Location of all the dex files to be swapped in to test hotswapping.
    private static final String DEX_SWAP_LOCATION =
            ProcessRunner.getProcessPath("app.swap.dex.location");

    private static final String LOCAL_HOST = "127.0.0.1";
    private static final int RETURN_VALUE_TIMEOUT = 1000;

    private FakeAndroidDriver android;

    private AgentBasedClassRedefiner redefiner;

    @Before
    public void setUp() throws Exception {
        TemporaryFolder dexLocation = new TemporaryFolder();
        dexLocation.create();

        android = new FakeAndroidDriver(LOCAL_HOST);
        android.start();

        redefiner = new LocalTestAgentBasedClassRedefiner(android, dexLocation);
    }

    @After
    public void tearDown() {
        android.stop();
    }

    @Test
    public void testSimpleClassRedefinition() throws Exception {
        android.loadDex(DEX_LOCATION);
        android.launchActivity(ACTIVITY_CLASS);

        android.triggerMethod(ACTIVITY_CLASS, "getStatus");
        Assert.assertTrue(android.waitForInput("NOT SWAPPED", RETURN_VALUE_TIMEOUT));

        redefiner.redefineClass(
                "com.android.tools.deploy.swapper.testapp.Target",
                getSplittedDex("com/android/tools/deploy/swapper/testapp/Target.dex"));
        redefiner.commit();

        android.triggerMethod(ACTIVITY_CLASS, "getStatus");
        Assert.assertTrue(android.waitForInput("JUST SWAPPED", RETURN_VALUE_TIMEOUT));
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

        redefiner.redefineClass(
                "com.android.tools.deploy.swapper.testapp.ClinitTarget",
                getSplittedDex("com/android/tools/deploy/swapper/testapp/ClinitTarget.dex"));
        redefiner.commit();

        android.triggerMethod(ACTIVITY_CLASS, "printCounter");
        Assert.assertTrue(android.waitForInput("TestActivity.counter = 0", RETURN_VALUE_TIMEOUT));

        android.triggerMethod(ACTIVITY_CLASS, "getClassInitializerStatus");
        Assert.assertTrue(android.waitForInput("ClinitTarget JUST SWAPPED", RETURN_VALUE_TIMEOUT));

        android.triggerMethod(ACTIVITY_CLASS, "printCounter");
        Assert.assertTrue(android.waitForInput("TestActivity.counter = 1", RETURN_VALUE_TIMEOUT));
    }

    /**
     * Modified the Redefiner to:
     *
     * <p>1. "Push" the dex files by just copying to a local temp directory.
     *
     * <p>2. On commit, attach the agent with a local agent shared object.
     */
    private static class LocalTestAgentBasedClassRedefiner extends AgentBasedClassRedefiner {
        private final TemporaryFolder dexLocation;
        private final FakeAndroidDriver android;

        private LocalTestAgentBasedClassRedefiner(
                FakeAndroidDriver android, TemporaryFolder dexLocation) {
            this.android = android;
            this.dexLocation = dexLocation;
        }

        @Override
        protected String pushToDevice(Map<String, byte[]> classesToRedefine) {
            for (Map.Entry<String, byte[]> entry : classesToRedefine.entrySet()) {
                try (FileOutputStream fos =
                        new FileOutputStream(dexLocation.newFile(entry.getKey() + ".dex"))) {
                    fos.write(entry.getValue());
                } catch (Exception e) {
                    Assert.fail(e.getMessage());
                }
            }
            return dexLocation.getRoot().getAbsolutePath();
        }

        @Override
        public void commit() {
            super.commit();
            android.attachAgent(
                    ProcessRunner.getProcessPath("swap.agent.location")
                            + "="
                            + "<none>," // App ID.
                            + dexLocation.getRoot().getAbsolutePath()
                            + "/");
            // TODO(acleung): We have no way to two way communicate with the Agent for now
            // so we are just going wait for a log statement.
            android.waitForInput(
                    "Done HotSwapping " + dexLocation.getRoot().getAbsolutePath() + "/");
        }
    }
}
