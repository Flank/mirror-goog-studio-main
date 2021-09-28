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
import java.util.Collection;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Test very simple cases on class redefinitions. */
@RunWith(Parameterized.class)
public class SimpleTest extends AgentBasedClassRedefinerTestBase {
    @Parameterized.Parameters
    public static Collection<String> artFlags() {
        return ALL_ART_FLAGS;
    }

    public SimpleTest(String artFlag) {
        super(artFlag);
    }

    @Test
    public void testSimpleClassRedefinition() throws Exception {
        android.loadDex(DEX_LOCATION);
        android.launchActivity(ACTIVITY_CLASS);

        android.triggerMethod(ACTIVITY_CLASS, "getStatus");
        Assert.assertTrue(android.waitForInput("NOT SWAPPED", RETURN_VALUE_TIMEOUT));

        Deploy.SwapRequest request = createRequest("app.Target", "app/Target.dex", false);
        redefiner.redefine(request);

        Deploy.AgentSwapResponse response = redefiner.getSwapAgentResponse();
        Assert.assertEquals(Deploy.AgentSwapResponse.Status.OK, response.getStatus());

        android.triggerMethod(ACTIVITY_CLASS, "getStatus");
        Assert.assertTrue(android.waitForInput("JUST SWAPPED", RETURN_VALUE_TIMEOUT));
    }

    /**
     * This method tests a few things: 1. We can redefine a class before the class is loaded. 2.
     * Class initializiers are not loaded when redefinition completes. 3. Class is succesfully
     * redefined and initializers are invoked upon class loading and behave as expected.
     */
    @Test
    public void testRedefiningNotLoaded() throws Exception {
        android.loadDex(DEX_LOCATION);
        android.launchActivity(ACTIVITY_CLASS);

        Deploy.SwapRequest request =
                createRequest("app.ClinitTarget", "app/ClinitTarget.dex", false);
        redefiner.redefine(request);

        Deploy.AgentSwapResponse response = redefiner.getSwapAgentResponse();
        Assert.assertEquals(Deploy.AgentSwapResponse.Status.OK, response.getStatus());

        android.triggerMethod(ACTIVITY_CLASS, "printCounter");
        Assert.assertTrue(android.waitForInput("TestActivity.counter = 0", RETURN_VALUE_TIMEOUT));

        android.triggerMethod(ACTIVITY_CLASS, "getClassInitializerStatus");
        Assert.assertTrue(android.waitForInput("ClinitTarget JUST SWAPPED", RETURN_VALUE_TIMEOUT));

        android.triggerMethod(ACTIVITY_CLASS, "printCounter");
        Assert.assertTrue(android.waitForInput("TestActivity.counter = 1", RETURN_VALUE_TIMEOUT));
    }

    @Test
    public void testRedefineAddingClasses() throws Exception {
        android.loadDex(DEX_LOCATION);
        android.launchActivity(ACTIVITY_CLASS);

        Deploy.SwapRequest request =
                createRequest(
                        ImmutableMap.of("app.Wrapper", "app/Wrapper.dex"),
                        ImmutableMap.of(
                                "app.NewClass",
                                "app/NewClass.dex",
                                "app.Wrapper$Inner",
                                "app/Wrapper$Inner.dex"),
                        false);
        redefiner.redefine(request);

        Deploy.AgentSwapResponse response = redefiner.getSwapAgentResponse();
        Assert.assertEquals(Deploy.AgentSwapResponse.Status.OK, response.getStatus());

        android.triggerMethod(ACTIVITY_CLASS, "getNewClassStatus");
        Assert.assertTrue(android.waitForInput("public=1package=1", RETURN_VALUE_TIMEOUT));
    }

    @Test
    public void testRedefineMissingClass() throws Exception {
        android.loadDex(DEX_LOCATION);
        android.launchActivity(ACTIVITY_CLASS);

        Deploy.SwapRequest request = createRequest("app.NonExistentClass", "app/Target.dex", false);
        redefiner.redefine(request);

        Deploy.AgentSwapResponse response = redefiner.getSwapAgentResponse();
        Assert.assertEquals(Deploy.AgentSwapResponse.Status.CLASS_NOT_FOUND, response.getStatus());
    }
}
