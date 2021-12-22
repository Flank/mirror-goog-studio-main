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

import org.junit.Assert;

public class TestLocalVariable {

    static {
        LiveEditStubs.init(TestLocalVariable.class.getClassLoader());
    }

    @org.junit.Test
    // Test that variable using two slots are properly placed on the local variable stack
    public void testLocalVariableStackLong() throws Exception {
        String className = "InvokeLocalVariable";
        String methodName = "callMethodWithLong";
        String methodDesc = "(JJ)J";
        Class clazz = InvokeLocalVariable.class;
        byte[] byteCode = buildClass(clazz);

        long v0 = 1;
        long v1 = 2;
        try {
            LiveEditStubs.addClass(className, byteCode, false);
            LiveEditStubs.addLiveEditedMethod(className, methodName, methodDesc);
            Object[] parameters = {null, null, v0, v1};
            Long l = (Long) LiveEditStubs.stubL(className, methodName, methodDesc, parameters);
            Assert.assertEquals("LocalVariable unalignment", l, Long.valueOf(v0 + v1));
        } finally {
            LiveEditStubs.deleteClass(className);
        }
    }

    @org.junit.Test
    // Test that variable using two slots are properly placed on the local variable stack
    public void testLocalVariableStackDouble() throws Exception {
        String className = "InvokeLocalVariable";
        String methodName = "callMethodWithDouble";
        String methodDesc = "(DD)D";
        Class clazz = InvokeLocalVariable.class;
        byte[] byteCode = buildClass(clazz);

        double v0 = 1;
        double v1 = 2;
        try {
            LiveEditStubs.addClass(className, byteCode, false);
            LiveEditStubs.addLiveEditedMethod(className, methodName, methodDesc);
            Object[] parameters = {null, null, v0, v1};
            Double d = (Double) LiveEditStubs.stubL(className, methodName, methodDesc, parameters);
            Assert.assertEquals("LocalVariable unalignment", d, Double.valueOf(v0 + v1));
        } finally {
            LiveEditStubs.deleteClass(className);
        }
    }
}
