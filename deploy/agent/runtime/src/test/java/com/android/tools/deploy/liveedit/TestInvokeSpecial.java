/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.deploy.liveedit;

import static com.android.tools.deploy.liveedit.Utils.buildClass;
import static com.android.tools.deploy.liveedit.Utils.classToType;

import org.junit.Assert;

public class TestInvokeSpecial {

    @org.junit.Test
    public void testInvokeSuperHash() throws Exception {
        byte[] byteCode = buildClass(InvokeSpecialChild.class);

        InvokeSpecialChild obj = new InvokeSpecialChild();
        MethodBodyEvaluator body = new MethodBodyEvaluator(byteCode, "callSuperGetHash()I");
        String type = classToType(InvokeSpecialChild.class);
        Object result = body.eval(obj, type, new Object[] {});

        Assert.assertEquals("invokespecial (super.hash)", obj.callSuperGetHash(), result);
    }

    @org.junit.Test
    public void testInvokeSuperArrayGetter() throws Exception {
        byte[] byteCode = buildClass(InvokeSpecialChild.class);

        InvokeSpecialChild obj = new InvokeSpecialChild();
        MethodBodyEvaluator body = new MethodBodyEvaluator(byteCode, "callgetArrayValue([II)I");
        String type = classToType(InvokeSpecialChild.class);
        int[] array = new int[] {5};
        int index = 0;
        Object result = body.eval(obj, type, new Object[] {array, index});

        Assert.assertEquals(
                "invokespecial (super.hash)", obj.callgetArrayValue(array, index), result);
    }

    @org.junit.Test
    public void testInvokeSuperParameters() throws Exception {
        byte[] bc = buildClass(InvokeSpecialChild.class);
        InvokeSpecialChild obj = new InvokeSpecialChild();
        String type = classToType(InvokeSpecialChild.class);
        Object result = null;
        MethodBodyEvaluator method = null;

        boolean z = true;
        method = new MethodBodyEvaluator(bc, "callSuperParamBool(Z)Z");
        result = method.eval(obj, type, new Object[] {z});
        Assert.assertEquals("invokespecial bool", obj.callSuperParamBool(z), result);

        char c = 'f';
        method = new MethodBodyEvaluator(bc, "callSuperParamChar(C)C");
        result = method.eval(obj, type, new Object[] {c});
        Assert.assertEquals("invokespecial char", obj.callSuperParamChar(c), result);

        byte b = 0x1;
        method = new MethodBodyEvaluator(bc, "callSuperParamByte(B)B");
        result = method.eval(obj, type, new Object[] {b});
        Assert.assertEquals("invokespecial byte", obj.callSuperParamByte(b), result);

        short s = 0x2;
        method = new MethodBodyEvaluator(bc, "callSuperParamShort(S)S");
        result = method.eval(obj, type, new Object[] {s});
        Assert.assertEquals("invokespecial short ", obj.callSuperParamShort(s), result);

        int i = 1000;
        method = new MethodBodyEvaluator(bc, "callSuperParamInt(I)I");
        result = method.eval(obj, type, new Object[] {i});
        Assert.assertEquals("invokespecial int", obj.callSuperParamInt(i), result);

        long j = 2000;
        method = new MethodBodyEvaluator(bc, "callSuperParamLong(J)J");
        result = method.eval(obj, type, new Object[] {j});
        Assert.assertEquals("invokespecial long", obj.callSuperParamLong(j), result);

        float f = 3000.0f;
        method = new MethodBodyEvaluator(bc, "callSuperParamFloat(F)F");
        result = method.eval(obj, type, new Object[] {f});
        Assert.assertEquals("invokespecial float", obj.callSuperParamFloat(f), result);

        double d = 4000.0;
        method = new MethodBodyEvaluator(bc, "callSuperParamDouble(D)D");
        result = method.eval(obj, type, new Object[] {d});
        Assert.assertEquals("invokespecial double", obj.callSuperParamDouble(d), result);

        Object o = Integer.valueOf(5000);
        method =
                new MethodBodyEvaluator(
                        bc, "callSuperParamObject(Ljava/lang/Object;)Ljava/lang/Object;");
        result = method.eval(obj, type, new Object[] {o});
        Assert.assertEquals("invokespecial object", obj.callSuperParamObject(o), result);

        int[] a = new int[] {6000};
        method = new MethodBodyEvaluator(bc, "callSuperParamArray([I)[I");
        result = method.eval(obj, type, new Object[] {a});
        Assert.assertEquals("invokespecial array", obj.callSuperParamArray(a), result);
    }
}
