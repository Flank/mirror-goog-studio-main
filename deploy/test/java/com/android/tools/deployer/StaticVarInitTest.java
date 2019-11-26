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
package com.android.tools.deployer;

import com.android.tools.deploy.proto.Deploy;
import java.util.Collection;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class StaticVarInitTest extends AgentBasedClassRedefinerTestBase {

    @Parameterized.Parameters
    public static Collection<String> artFlags() {
        return ALL_ART_FLAGS;
    }

    public StaticVarInitTest(String artFlag) {
        super(artFlag);
    }

    @Test
    public void testStaticVarInit() throws Exception {
        // Available only with test flag turned on.
        Assume.assumeTrue(artFlag != null);

        android.loadDex(DEX_LOCATION);
        android.launchActivity(ACTIVITY_CLASS);

        android.triggerMethod(ACTIVITY_CLASS, "getStaticFinalInt");
        Assert.assertTrue(
                android.waitForInput(
                        "NoSuchFieldException on StaticVarInit.AddStaticFinalInt.X",
                        RETURN_VALUE_TIMEOUT));

        Deploy.SwapRequest request =
                createRequest(
                        "app.StaticVarInit$AddStaticFinalInt",
                        "app/StaticVarInit$AddStaticFinalInt.dex",
                        false);
        redefiner.redefine(request);

        Deploy.AgentSwapResponse response = redefiner.getAgentResponse();
        Assert.assertEquals(Deploy.AgentSwapResponse.Status.OK, response.getStatus());

        // TODO: Incorrect. Once we finish static variables initalization, this needs to be X = 99.
        android.triggerMethod(ACTIVITY_CLASS, "getStaticFinalInt");
        Assert.assertTrue(android.waitForInput("StaticVarInit.X = 0", RETURN_VALUE_TIMEOUT));
    }

    @Test
    public void testBackgroundThreadSuspend() throws Exception {
        android.loadDex(DEX_LOCATION);
        android.launchActivity(ACTIVITY_CLASS);

        android.triggerMethod(ACTIVITY_CLASS, "startBackgroundThread");
        Assert.assertTrue(android.waitForInput("Background Thread Started", RETURN_VALUE_TIMEOUT));

        android.triggerMethod(ACTIVITY_CLASS, "waitBackgroundThread");
        Assert.assertTrue(android.waitForInput("Not Waiting Yet", RETURN_VALUE_TIMEOUT));

        Deploy.SwapRequest request =
                createRequest(
                        "app.StaticVarInit$BgThread", "app/StaticVarInit$BgThread.dex", false);
        redefiner.redefine(request);

        Deploy.AgentSwapResponse response = redefiner.getAgentResponse();
        Assert.assertEquals(Deploy.AgentSwapResponse.Status.OK, response.getStatus());

        android.triggerMethod(ACTIVITY_CLASS, "waitBackgroundThread");
        Assert.assertTrue(android.waitForInput("Background Thread Finished", RETURN_VALUE_TIMEOUT));
    }


    @Test
    public void testStaticVarFromVirtual() throws Exception {
        // Available only with test flag turned on.
        Assume.assumeTrue(artFlag != null);

        android.loadDex(DEX_LOCATION);
        android.launchActivity(ACTIVITY_CLASS);

        android.triggerMethod(ACTIVITY_CLASS, "getStaticIntFromVirtual");
        Assert.assertTrue(android.waitForInput("getStaticIntFromVirtual = -89", RETURN_VALUE_TIMEOUT));

        Deploy.SwapRequest request =
          createRequest(
            "app.StaticVarInit",
            "app/StaticVarInit.dex",
            false);
        redefiner.redefine(request);

        Deploy.AgentSwapResponse response = redefiner.getAgentResponse();
        Assert.assertEquals(Deploy.AgentSwapResponse.Status.OK, response.getStatus());

        android.triggerMethod(ACTIVITY_CLASS, "getStaticIntFromVirtual");

        // TODO: This needs to be = 89 once static initialization is completed.
        Assert.assertTrue(android.waitForInput("getStaticIntFromVirtual = 0", RETURN_VALUE_TIMEOUT));
    }
}
