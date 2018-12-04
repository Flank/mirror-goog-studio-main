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
import java.util.HashMap;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

/** Test the R class field verification. */
public class AgentBasedClassRedefinerRClassTest extends AgentBasedClassRedefinerTestBase {
    @Test
    public void testRClassRedefine() throws Exception {
        android.loadDex(DEX_LOCATION);

        Deploy.SwapRequest request = createRequest("app.R$Strings", "app/R$Strings.dex", false);
        redefiner.redefine(request);

        Deploy.AgentSwapResponse response = redefiner.getAgentResponse();
        Assert.assertEquals(Deploy.AgentSwapResponse.Status.ERROR, response.getStatus());

        List<Deploy.JvmtiErrorDetails> details = response.getJvmtiErrorDetailsList();

        HashMap<String, Deploy.JvmtiErrorDetails.Type> fieldErrors = new HashMap<>();
        for (Deploy.JvmtiErrorDetails detail : details) {
            fieldErrors.put(detail.getName(), detail.getType());
            Assert.assertEquals("app/R$Strings", detail.getClassName());
        }

        Assert.assertTrue(fieldErrors.containsKey("FIELD_B"));
        Assert.assertEquals(
                fieldErrors.get("FIELD_B"), Deploy.JvmtiErrorDetails.Type.FIELD_REMOVED);
        Assert.assertTrue(fieldErrors.containsKey("FIELD_C"));
        Assert.assertEquals(fieldErrors.get("FIELD_C"), Deploy.JvmtiErrorDetails.Type.FIELD_ADDED);
    }
}
