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
public class AgentBasedClassRedefinerDesugarTest extends AgentBasedClassRedefinerTestBase {

    @Test
    public void testLambdasHotSwap() throws Exception {
        android.loadDex(DEX_LOCATION);
        android.launchActivity(ACTIVITY_CLASS);

        android.triggerMethod(ACTIVITY_CLASS, "getLambdaTargetStatus");
        Assert.assertTrue(
                android.waitForInput("LambdaTarget NOT SWAPPED 0:0", RETURN_VALUE_TIMEOUT));

        Deploy.SwapRequest request =
                createRequest("app.LambdaTarget", "app/LambdaTarget.dex", false);
        redefiner.redefine(request, true);

        android.triggerMethod(ACTIVITY_CLASS, "getLambdaTargetStatus");
        Assert.assertTrue(
                android.waitForInput("LambdaTarget JUST SWAPPED 1:1", RETURN_VALUE_TIMEOUT));
    }

    @Test
    public void testLambdasChangedCaptureHotSwap() throws Exception {
        android.loadDex(DEX_LOCATION);
        android.launchActivity(ACTIVITY_CLASS);

        android.triggerMethod(ACTIVITY_CLASS, "getLambdaFailedTargetStatus");
        Assert.assertTrue(
                android.waitForInput("LambdaFailedTarget NOT SWAPPED 0:0", RETURN_VALUE_TIMEOUT));

        Deploy.SwapRequest request =
                createRequest("app.LambdaFailedTarget", "app/LambdaFailedTarget.dex", false);
        redefiner.redefine(request, false);

        android.triggerMethod(ACTIVITY_CLASS, "getLambdaFailedTargetStatus");
        Assert.assertTrue(
                android.waitForInput("LambdaFailedTarget NOT SWAPPED 1:1", RETURN_VALUE_TIMEOUT));
    }
}
