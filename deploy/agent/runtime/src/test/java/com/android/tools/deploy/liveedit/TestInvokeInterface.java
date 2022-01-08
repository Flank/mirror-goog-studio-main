/*
 * Copyright (C) 2022 The Android Open Source Project
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

import org.junit.Assert;

public class TestInvokeInterface {
    @org.junit.Test
    public void testInvokeSuperHash() throws Exception {
        byte[] byteCode = buildClass(InvokeInterface.class);

        MethodBodyEvaluator body = new MethodBodyEvaluator(byteCode, "callInterface", "()I");
        Object result = body.evalStatic(new Object[] {});
        Integer expected = Integer.valueOf(0);
        Assert.assertEquals("invokeinterface", expected, result);
    }
}
