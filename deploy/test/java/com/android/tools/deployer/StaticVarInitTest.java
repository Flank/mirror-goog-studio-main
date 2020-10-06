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

        android.triggerMethod(ACTIVITY_CLASS, "getStaticFinalPrimitives");
        Assert.assertTrue(
                android.waitForInput(
                        "NoSuchFieldException on StaticVarInit.AddStaticFinalPrimitives.X_INT",
                        RETURN_VALUE_TIMEOUT));

        // Our Deployer / D8 computes this but since we don't invoke them from this test, we
        // manually create it here.

        Deploy.SwapRequest request =
                createRequest(
                        "app.StaticVarInit$AddStaticFinalPrimitives",
                        "app/StaticVarInit$AddStaticFinalPrimitives.dex",
                        false,
                        Deploy.ClassDef.FieldReInitState.newBuilder()
                                .setName("X_BYTE")
                                .setType("B")
                                .setStaticVar(true)
                                .setValue("100")
                                .setState(Deploy.ClassDef.FieldReInitState.VariableState.CONSTANT)
                                .build(),
                        Deploy.ClassDef.FieldReInitState.newBuilder()
                                .setName("X_INT")
                                .setType("I")
                                .setStaticVar(true)
                                .setValue("99")
                                .setState(Deploy.ClassDef.FieldReInitState.VariableState.CONSTANT)
                                .build(),
                        Deploy.ClassDef.FieldReInitState.newBuilder()
                                .setName("X_CHAR")
                                .setType("C")
                                .setStaticVar(true)
                                .setValue("!")
                                .setState(Deploy.ClassDef.FieldReInitState.VariableState.CONSTANT)
                                .build(),
                        Deploy.ClassDef.FieldReInitState.newBuilder()
                                .setName("X_LONG")
                                .setType("J")
                                .setStaticVar(true)
                                .setValue("1000")
                                .setState(Deploy.ClassDef.FieldReInitState.VariableState.CONSTANT)
                                .build(),
                        Deploy.ClassDef.FieldReInitState.newBuilder()
                                .setName("X_SHORT")
                                .setType("S")
                                .setStaticVar(true)
                                .setValue("7")
                                .setState(Deploy.ClassDef.FieldReInitState.VariableState.CONSTANT)
                                .build(),
                        Deploy.ClassDef.FieldReInitState.newBuilder()
                                .setName("X_FLOAT")
                                .setType("F")
                                .setStaticVar(true)
                                .setValue("3.14")
                                .setState(Deploy.ClassDef.FieldReInitState.VariableState.CONSTANT)
                                .build(),
                        Deploy.ClassDef.FieldReInitState.newBuilder()
                                .setName("X_DOUBLE")
                                .setType("D")
                                .setStaticVar(true)
                                .setValue("3e-100")
                                .setState(Deploy.ClassDef.FieldReInitState.VariableState.CONSTANT)
                                .build(),
                        Deploy.ClassDef.FieldReInitState.newBuilder()
                                .setName("X_BOOLEAN")
                                .setType("Z")
                                .setStaticVar(true)
                                .setValue("true")
                                .setState(Deploy.ClassDef.FieldReInitState.VariableState.CONSTANT)
                                .build());
        redefiner.redefine(request);

        Deploy.AgentSwapResponse response = redefiner.getSwapAgentResponse();
        Assert.assertEquals(Deploy.AgentSwapResponse.Status.OK, response.getStatus());

        android.triggerMethod(ACTIVITY_CLASS, "getStaticFinalPrimitives");
        Assert.assertTrue(
                android.waitForInput(
                        "StaticVarInit.X = 100 99 ! 1000 7 3.14 3.0E-100 true",
                        RETURN_VALUE_TIMEOUT));
    }

    @Test
    public void testRemoveStaticVar() throws Exception {
        // Available only with test flag turned on.
        Assume.assumeTrue(artFlag != null);

        android.loadDex(DEX_LOCATION);
        android.launchActivity(ACTIVITY_CLASS);

        // Our Deployer / D8 computes this but since we don't invoke them from this test, we
        // manually create it here.

        Deploy.SwapRequest request =
                createRequest(
                        "app.StaticVarInit$RemoveStaticFinalPrimitives",
                        "app/StaticVarInit$RemoveStaticFinalPrimitives.dex",
                        false,
                        Deploy.ClassDef.FieldReInitState.newBuilder()
                                .setName("X_INT_NOT_THERE")
                                .setType("I")
                                .setStaticVar(true)
                                .setValue("99")
                                .setState(Deploy.ClassDef.FieldReInitState.VariableState.CONSTANT)
                                .build());
        redefiner.redefine(request);
        Deploy.AgentSwapResponse response = redefiner.getSwapAgentResponse();
        Assert.assertEquals(Deploy.AgentSwapResponse.Status.JVMTI_ERROR, response.getStatus());
    }

    @Test
    public void testInitNonStaticNotSupported() throws Exception {
        // Available only with test flag turned on.
        Assume.assumeTrue(artFlag != null);

        android.loadDex(DEX_LOCATION);
        android.launchActivity(ACTIVITY_CLASS);

        android.triggerMethod(ACTIVITY_CLASS, "getStaticFinalPrimitives");
        Assert.assertTrue(
                android.waitForInput(
                        "NoSuchFieldException on StaticVarInit.AddStaticFinalPrimitives.X_INT",
                        RETURN_VALUE_TIMEOUT));

        // Our Deployer / D8 computes this but since we don't invoke them from this test, we
        // manually create it here.

        Deploy.SwapRequest request =
                createRequest(
                        "app.StaticVarInit$AddStaticFinalPrimitives",
                        "app/StaticVarInit$AddStaticFinalPrimitives.dex",
                        false,
                        Deploy.ClassDef.FieldReInitState.newBuilder()
                                .setName("NOT_STATIC")
                                .setType("Z")
                                .setStaticVar(false)
                                .setValue("true")
                                .setState(Deploy.ClassDef.FieldReInitState.VariableState.CONSTANT)
                                .build());
        redefiner.redefine(request);

        Deploy.AgentSwapResponse response = redefiner.getSwapAgentResponse();
        Assert.assertEquals(
                Deploy.AgentSwapResponse.Status.UNSUPPORTED_REINIT_NON_STATIC_PRIMITIVE,
                response.getStatus());
    }

    @Test
    public void testInitNonConstantNotSupported() throws Exception {
        // Available only with test flag turned on.
        Assume.assumeTrue(artFlag != null);

        android.loadDex(DEX_LOCATION);
        android.launchActivity(ACTIVITY_CLASS);

        android.triggerMethod(ACTIVITY_CLASS, "getStaticFinalPrimitives");
        Assert.assertTrue(
                android.waitForInput(
                        "NoSuchFieldException on StaticVarInit.AddStaticFinalPrimitives.X_INT",
                        RETURN_VALUE_TIMEOUT));

        // Our Deployer / D8 computes this but since we don't invoke them from this test, we
        // manually create it here.

        Deploy.SwapRequest request =
                createRequest(
                        "app.StaticVarInit$AddStaticFinalPrimitives",
                        "app/StaticVarInit$AddStaticFinalPrimitives.dex",
                        false,
                        Deploy.ClassDef.FieldReInitState.newBuilder()
                                .setName("STATIC")
                                .setType("Z")
                                .setStaticVar(true)
                                .setValue("true")
                                .setState(Deploy.ClassDef.FieldReInitState.VariableState.UNKNOWN)
                                .build());
        redefiner.redefine(request);

        Deploy.AgentSwapResponse response = redefiner.getSwapAgentResponse();
        Assert.assertEquals(
                Deploy.AgentSwapResponse.Status.UNSUPPORTED_REINIT_STATIC_PRIMITIVE_NOT_CONSTANT,
                response.getStatus());
    }

    @Test
    public void testInitStaticObjectNotSupported() throws Exception {
        // Available only with test flag turned on.
        Assume.assumeTrue(artFlag != null);

        android.loadDex(DEX_LOCATION);
        android.launchActivity(ACTIVITY_CLASS);

        // Our Deployer / D8 computes this but since we don't invoke them from this test, we
        // manually create it here.

        Deploy.SwapRequest request =
                createRequest(
                        "app.StaticVarInit$AddStaticFinalPrimitives",
                        "app/StaticVarInit$AddStaticFinalPrimitives.dex",
                        false,
                        Deploy.ClassDef.FieldReInitState.newBuilder()
                                .setName("STATIC")
                                .setType("Lcom/example/SomeType;")
                                .setStaticVar(true)
                                .setValue("null")
                                .setState(Deploy.ClassDef.FieldReInitState.VariableState.CONSTANT)
                                .build());
        redefiner.redefine(request);

        Deploy.AgentSwapResponse response = redefiner.getSwapAgentResponse();
        Assert.assertEquals(
                Deploy.AgentSwapResponse.Status.UNSUPPORTED_REINIT_STATIC_OBJECT,
                response.getStatus());
    }

    @Test
    public void testInitStaticArrayNotSupported() throws Exception {
        // Available only with test flag turned on.
        Assume.assumeTrue(artFlag != null);

        android.loadDex(DEX_LOCATION);
        android.launchActivity(ACTIVITY_CLASS);

        // Our Deployer / D8 computes this but since we don't invoke them from this test, we
        // manually create it here.

        Deploy.SwapRequest request =
                createRequest(
                        "app.StaticVarInit$AddStaticFinalPrimitives",
                        "app/StaticVarInit$AddStaticFinalPrimitives.dex",
                        false,
                        Deploy.ClassDef.FieldReInitState.newBuilder()
                                .setName("STATIC")
                                .setType("[I")
                                .setStaticVar(true)
                                .setValue("null")
                                .setState(Deploy.ClassDef.FieldReInitState.VariableState.CONSTANT)
                                .build());
        redefiner.redefine(request);

        Deploy.AgentSwapResponse response = redefiner.getSwapAgentResponse();
        Assert.assertEquals(
                Deploy.AgentSwapResponse.Status.UNSUPPORTED_REINIT_STATIC_ARRAY,
                response.getStatus());
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

        Deploy.AgentSwapResponse response = redefiner.getSwapAgentResponse();
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

        // Our Deployer / D8 computes this but since we don't invoke them from this test, we
        // manually create it here.
        Deploy.ClassDef.FieldReInitState state =
                Deploy.ClassDef.FieldReInitState.newBuilder()
                        .setName("Y")
                        .setType("I")
                        .setStaticVar(true)
                        .setValue("89")
                        .setState(Deploy.ClassDef.FieldReInitState.VariableState.CONSTANT)
                        .build();

        Deploy.SwapRequest request =
                createRequest("app.StaticVarInit", "app/StaticVarInit.dex", false, state);
        redefiner.redefine(request);

        Deploy.AgentSwapResponse response = redefiner.getSwapAgentResponse();
        Assert.assertEquals(Deploy.AgentSwapResponse.Status.OK, response.getStatus());

        android.triggerMethod(ACTIVITY_CLASS, "getStaticIntFromVirtual");

        // TODO: This needs to be = 89 once static initialization is completed.
        Assert.assertTrue(
                android.waitForInput("getStaticIntFromVirtual = 89", RETURN_VALUE_TIMEOUT));
    }
}
