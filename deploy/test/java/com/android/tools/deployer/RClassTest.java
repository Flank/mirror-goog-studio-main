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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Test the R class field verification. */
@RunWith(Parameterized.class)
public class RClassTest extends AgentBasedClassRedefinerTestBase {
    @Parameterized.Parameters
    public static Collection<String> artFlags() {
        return ALL_ART_FLAGS;
    }

    public RClassTest(String artFlag) {
        super(artFlag);
    }

    @Test
    public void testRClassRedefineRename() throws Exception {
        // TODO: ART doesn't support removal yet.
        Assume.assumeTrue(artFlag == null);
        android.loadDex(DEX_LOCATION);
        android.launchActivity(ACTIVITY_CLASS);

        Deploy.SwapRequest request = createRequest("app.R$Strings", "app/R$Strings.dex", false);
        redefiner.redefine(request);

        Deploy.JvmtiError foo = null;

        Deploy.AgentSwapResponse response = redefiner.getSwapAgentResponse();
        Assert.assertEquals(Deploy.AgentSwapResponse.Status.JVMTI_ERROR, response.getStatus());

        List<Deploy.JvmtiError.Details> details = response.getJvmtiError().getDetailsList();

        HashMap<String, Deploy.JvmtiError.Details.Type> fieldErrors = new HashMap<>();
        for (Deploy.JvmtiError.Details detail : details) {
            fieldErrors.put(detail.getName(), detail.getType());
            Assert.assertEquals("app/R$Strings", detail.getClassName());
        }

        Assert.assertTrue(fieldErrors.containsKey("FIELD_B"));
        Assert.assertEquals(
                fieldErrors.get("FIELD_B"), Deploy.JvmtiError.Details.Type.FIELD_REMOVED);
        Assert.assertTrue(fieldErrors.containsKey("FIELD_C"));
        Assert.assertEquals(fieldErrors.get("FIELD_C"), Deploy.JvmtiError.Details.Type.FIELD_ADDED);
    }

    @Test
    public void testRClassRedefineAddId() throws Exception {
        // Available only with test flag turned on.
        Assume.assumeTrue(artFlag != null);
        android.loadDex(DEX_LOCATION);
        android.launchActivity(ACTIVITY_CLASS);

        Deploy.SwapRequest request = createRequest("app.R$id", "app/R$id.dex", false);
        redefiner.redefine(request);

        Deploy.AgentSwapResponse response = redefiner.getSwapAgentResponse();
        Assert.assertEquals(Deploy.AgentSwapResponse.Status.OK, response.getStatus());

        // No need to verify the content of the id because those are inlined.
    }

    @Test
    public void testRClassUnstableId() throws Exception {
        // Available only with test flag turned on.
        Assume.assumeTrue(artFlag != null);
        android.loadDex(DEX_LOCATION);
        android.launchActivity(ACTIVITY_CLASS);
        Deploy.ClassDef.FieldReInitState state =
                Deploy.ClassDef.FieldReInitState.newBuilder()
                        .setName("a")
                        .setType("I")
                        .setStaticVar(true)
                        .setValue("10")
                        .setState(Deploy.ClassDef.FieldReInitState.VariableState.CONSTANT)
                        .build();
        Deploy.SwapRequest request =
                createRequest("app.R$UnstableId", "app/R$UnstableId.dex", true, state);
        redefiner.redefine(request);
        Deploy.AgentSwapResponse response = redefiner.getSwapAgentResponse();
        Assert.assertEquals(
                Deploy.AgentSwapResponse.Status.UNSUPPORTED_REINIT_R_CLASS_VALUE_MODIFIED,
                response.getStatus());
    }
}
