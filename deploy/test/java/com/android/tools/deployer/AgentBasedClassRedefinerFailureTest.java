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
import com.android.tools.deploy.protobuf.ByteString;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

/** Test cases where the agent fail to redefine classes for various reasons. */
public class AgentBasedClassRedefinerFailureTest extends AgentBasedClassRedefinerTestBase {

    @Ignore("b/117240186")
    @Test
    public void testFailedClassRedefinitionWithActivityRestart() throws Exception {
        android.loadDex(DEX_LOCATION);
        android.launchActivity(ACTIVITY_CLASS);

        android.triggerMethod(ACTIVITY_CLASS, "getStatus");
        Assert.assertTrue(android.waitForInput("NOT SWAPPED 0", RETURN_VALUE_TIMEOUT));

        Deploy.SwapRequest request = createRequest("app.Target", "app/ClinitTarget.dex", true);
        redefiner.redefine(request);

        // Agent should request an activity restart.
        Deploy.AgentSwapResponse response = redefiner.getAgentResponse();
        Assert.assertEquals(
                Deploy.AgentSwapResponse.Status.NEED_ACTIVITY_RESTART, response.getStatus());

        // Fake an app info changed event.
        android.triggerMethod(ACTIVITY_CLASS, "updateAppInfo");
        Assert.assertTrue(
                android.waitForInput("APPLICATION_INFO_CHANGED aborted", RETURN_VALUE_TIMEOUT));

        response = redefiner.getAgentResponse();
        Assert.assertEquals(Deploy.AgentSwapResponse.Status.ERROR, response.getStatus());

        android.triggerMethod(ACTIVITY_CLASS, "getStatus");
        Assert.assertTrue(android.waitForInput("NOT SWAPPED 1", RETURN_VALUE_TIMEOUT));
    }

    @Test
    public void testFailHotSwap() throws Exception {
        android.loadDex(DEX_LOCATION);
        android.launchActivity(ACTIVITY_CLASS);

        android.triggerMethod(ACTIVITY_CLASS, "getFailedTargetStatus");
        Assert.assertTrue(android.waitForInput("FailedTarget NOT SWAPPED 0", RETURN_VALUE_TIMEOUT));

        Deploy.SwapRequest request =
                createRequest("app.FailedTarget", "app/FailedTarget.dex", false);
        redefiner.redefine(request);

        Deploy.AgentSwapResponse response = redefiner.getAgentResponse();
        Assert.assertEquals(Deploy.AgentSwapResponse.Status.ERROR, response.getStatus());

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
                        .setName("app.FailedTarget")
                        .setDex(ByteString.copyFrom(getSplittedDex("app/FailedTarget.dex")))
                        .build();
        Deploy.ClassDef classDef2 =
                Deploy.ClassDef.newBuilder()
                        .setName("app.Target")
                        .setDex(ByteString.copyFrom(getSplittedDex("app/Target.dex")))
                        .build();
        Deploy.SwapRequest request =
                Deploy.SwapRequest.newBuilder()
                        .addClasses(classDef1)
                        .addClasses(classDef2)
                        .setPackageName(PACKAGE)
                        .setRestartActivity(false)
                        .build();
        redefiner.redefine(request);

        Deploy.AgentSwapResponse response = redefiner.getAgentResponse();
        Assert.assertEquals(Deploy.AgentSwapResponse.Status.ERROR, response.getStatus());

        android.triggerMethod(ACTIVITY_CLASS, "getFailedTargetStatus");
        Assert.assertTrue(android.waitForInput("FailedTarget NOT SWAPPED 1", RETURN_VALUE_TIMEOUT));

        android.triggerMethod(ACTIVITY_CLASS, "getStatus");
        Assert.assertTrue(android.waitForInput("NOT SWAPPED 1", RETURN_VALUE_TIMEOUT));
    }
}
