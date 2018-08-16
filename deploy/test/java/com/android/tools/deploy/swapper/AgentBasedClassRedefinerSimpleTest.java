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
import org.junit.Assert;
import org.junit.Test;

/** Test very simple cases on class redefinitions. */
public class AgentBasedClassRedefinerSimpleTest extends AgentBasedClassRedefinerTestBase {
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
}
