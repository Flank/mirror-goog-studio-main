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
import org.junit.Assert;
import org.junit.Test;

/** Test cases where the agent fail to redefine classes for various reasons. */
public class AgentBasedClassRedefinerFailureTest extends AgentBasedClassRedefinerTestBase {

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
        Assert.assertEquals(Deploy.AgentSwapResponse.Status.JVMTI_ERROR, response.getStatus());

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

        Deploy.SwapRequest request =
                createRequest(
                        ImmutableMap.of(
                                "app.FailedTarget",
                                "app/FailedTarget.dex",
                                "app.Target",
                                "app/Target.dex"),
                        ImmutableMap.of(),
                        false);
        redefiner.redefine(request);

        Deploy.AgentSwapResponse response = redefiner.getAgentResponse();
        Assert.assertEquals(Deploy.AgentSwapResponse.Status.JVMTI_ERROR, response.getStatus());

        android.triggerMethod(ACTIVITY_CLASS, "getFailedTargetStatus");
        Assert.assertTrue(android.waitForInput("FailedTarget NOT SWAPPED 1", RETURN_VALUE_TIMEOUT));

        android.triggerMethod(ACTIVITY_CLASS, "getStatus");
        Assert.assertTrue(android.waitForInput("NOT SWAPPED 1", RETURN_VALUE_TIMEOUT));
    }

    @Test
    public void testUnparseableSwapRequest() throws Exception {
        android.loadDex(DEX_LOCATION);
        android.launchActivity(ACTIVITY_CLASS);

        android.triggerMethod(ACTIVITY_CLASS, "getStatus");
        Assert.assertTrue(android.waitForInput("NOT SWAPPED", RETURN_VALUE_TIMEOUT));

        // Send a garbage swap request.
        redefiner.redefine(new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF});

        Deploy.AgentSwapResponse response = redefiner.getAgentResponse();
        Assert.assertEquals(
                Deploy.AgentSwapResponse.Status.REQUEST_PARSE_FAILED, response.getStatus());
    }
}
