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
import org.junit.Assert;
import org.junit.Test;

/** Verify some basic swapping that involves Kotlin compiler generated classes. */
public class AgentBasedClassRedefinerKotlinTest extends AgentBasedClassRedefinerTestBase {

    @Test
    public void testBasicKotlin() throws Exception {
        android.loadDex(DEX_LOCATION);
        android.launchActivity(ACTIVITY_CLASS);

        android.triggerMethod(ACTIVITY_CLASS, "getKotlinSimpleTargetStatus");
        Assert.assertTrue(
                android.waitForInput("KotlinSimpleTarget NOT SWAPPED", RETURN_VALUE_TIMEOUT));

        Deploy.SwapRequest request =
                createRequest("pkg.KotlinSimpleTarget", "pkg/KotlinSimpleTarget.dex", false);
        redefiner.redefine(request);

        Deploy.AgentSwapResponse response = redefiner.getAgentResponse();
        Assert.assertEquals(Deploy.AgentSwapResponse.Status.OK, response.getStatus());

        android.triggerMethod(ACTIVITY_CLASS, "getKotlinSimpleTargetStatus");
        Assert.assertTrue(
                android.waitForInput("KotlinSimpleTarget JUST SWAPPED", RETURN_VALUE_TIMEOUT));
    }

    @Test
    public void testBasicKotlinFailed() throws Exception {
        android.loadDex(DEX_LOCATION);
        android.launchActivity(ACTIVITY_CLASS);

        android.triggerMethod(ACTIVITY_CLASS, "getKotlinFailedTargetStatus");
        Assert.assertTrue(
                android.waitForInput("KotlinFailedTarget NOT SWAPPED", RETURN_VALUE_TIMEOUT));

        Deploy.SwapRequest request =
                createRequest("pkg.KotlinFailedTarget", "pkg/KotlinFailedTarget.dex", false);
        redefiner.redefine(request);

        Deploy.AgentSwapResponse response = redefiner.getAgentResponse();
        Assert.assertEquals(Deploy.AgentSwapResponse.Status.ERROR, response.getStatus());

        android.triggerMethod(ACTIVITY_CLASS, "getKotlinFailedTargetStatus");
        Assert.assertTrue(
                android.waitForInput("KotlinFailedTarget NOT SWAPPED", RETURN_VALUE_TIMEOUT));
    }

    @Test
    public void testKotlinCompanion() throws Exception {
        android.loadDex(DEX_LOCATION);
        android.launchActivity(ACTIVITY_CLASS);

        android.triggerMethod(ACTIVITY_CLASS, "getKotlinCompanionTargetStatus");
        Assert.assertTrue(
                android.waitForInput("KotlinCompanionTarget NOT SWAPPED", RETURN_VALUE_TIMEOUT));

        Deploy.SwapRequest request =
                createRequest(
                        "pkg.KotlinCompanionTarget$Instance",
                        "pkg/KotlinCompanionTarget$Instance.dex",
                        false);
        redefiner.redefine(request);

        Deploy.AgentSwapResponse response = redefiner.getAgentResponse();
        Assert.assertEquals(Deploy.AgentSwapResponse.Status.OK, response.getStatus());

        android.triggerMethod(ACTIVITY_CLASS, "getKotlinCompanionTargetStatus");
        Assert.assertTrue(
                android.waitForInput("KotlinCompanionTarget JUST SWAPPED", RETURN_VALUE_TIMEOUT));
    }

    @Test
    public void testKotlinCoroutine() throws Exception {
        android.loadDex(DEX_LOCATION);
        android.launchActivity(ACTIVITY_CLASS);

        android.triggerMethod(ACTIVITY_CLASS, "getKotlinCoroutineTargetStatus");

        // Note: Arithmetic Series: (100*(100-1))/2 = 4950
        Assert.assertTrue(android.waitForInput("KotlinCoroutineTarget 4950", RETURN_VALUE_TIMEOUT));

        Deploy.SwapRequest request =
                createRequest(
                        "pkg.KotlinCoroutineTarget$getStatus$1",
                        "pkg/KotlinCoroutineTarget$getStatus$1.dex",
                        false);
        redefiner.redefine(request);

        Deploy.AgentSwapResponse response = redefiner.getAgentResponse();
        Assert.assertEquals(Deploy.AgentSwapResponse.Status.OK, response.getStatus());

        android.triggerMethod(ACTIVITY_CLASS, "getKotlinCoroutineTargetStatus");

        Assert.assertTrue(android.waitForInput("KotlinCoroutineTarget 0", RETURN_VALUE_TIMEOUT));
    }
}
