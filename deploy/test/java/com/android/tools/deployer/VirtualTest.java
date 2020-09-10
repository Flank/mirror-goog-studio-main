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
public class VirtualTest extends AgentBasedClassRedefinerTestBase {

    @Parameterized.Parameters
    public static Collection<String> artFlags() {
        return ALL_ART_FLAGS;
    }

    public VirtualTest(String artFlag) {
        super(artFlag);
    }

    @Test
    public void testaddVirual() throws Exception {
        // Available only with test flag turned on.
        Assume.assumeTrue(artFlag != null);

        android.loadDex(DEX_LOCATION);
        android.launchActivity(ACTIVITY_CLASS);

        android.triggerMethod(ACTIVITY_CLASS, "getVirtualsResult");
        Assert.assertTrue(android.waitForInput("getVirtualsResult = 0", RETURN_VALUE_TIMEOUT));

        Deploy.SwapRequest request = createRequest("app.Virtuals", "app/Virtuals.dex", false);
        redefiner.redefine(request);

        Deploy.AgentSwapResponse response = redefiner.getSwapAgentResponse();
        Assert.assertEquals(Deploy.AgentSwapResponse.Status.OK, response.getStatus());

        android.triggerMethod(ACTIVITY_CLASS, "getVirtualsResult");
        Assert.assertTrue(android.waitForInput("getVirtualsResult = 99", RETURN_VALUE_TIMEOUT));
    }
}
