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

public class IfTest {

    @org.junit.Test
    public void testIfEq() throws Exception {
        byte[] classInput = buildClass(IfTarget.class);
        int i = 1;
        MethodBodyEvaluator ev = new MethodBodyEvaluator(classInput, "testIfEq", "(I)Z");
        Object result = ev.evalStatic(new Object[] {i});
        Assert.assertEquals("testIfEq", IfTarget.testIfEq(i), result);
    }

    @org.junit.Test
    public void testIfNEq() throws Exception {
        byte[] classInput = buildClass(IfTarget.class);
        int i = 1;
        MethodBodyEvaluator ev = new MethodBodyEvaluator(classInput, "testIfNeq", "(I)Z");
        Object result = ev.evalStatic(new Object[] {i});
        Assert.assertEquals("testIfEq", IfTarget.testIfNeq(i), result);
    }

    @org.junit.Test
    public void testIfGt() throws Exception {
        byte[] classInput = buildClass(IfTarget.class);
        int i = 1;
        MethodBodyEvaluator ev = new MethodBodyEvaluator(classInput, "testIfGt", "(I)Z");
        Object result = ev.evalStatic(new Object[] {i});
        Assert.assertEquals("testIfGt", IfTarget.testIfGt(i), result);
    }

    @org.junit.Test
    public void testIfGe() throws Exception {
        byte[] classInput = buildClass(IfTarget.class);
        int i = 1;
        MethodBodyEvaluator ev = new MethodBodyEvaluator(classInput, "testIfGe", "(I)Z");
        Object result = ev.evalStatic(new Object[] {i});
        Assert.assertEquals("testIfGe", IfTarget.testIfGe(i), result);
    }

    @org.junit.Test
    public void testIfLt() throws Exception {
        byte[] classInput = buildClass(IfTarget.class);
        int i = 1;
        MethodBodyEvaluator ev = new MethodBodyEvaluator(classInput, "testIfLt", "(I)Z");
        Object result = ev.evalStatic(new Object[] {i});
        Assert.assertEquals("testIfLt", IfTarget.testIfLt(i), result);
    }

    @org.junit.Test
    public void testIfLe() throws Exception {
        byte[] classInput = buildClass(IfTarget.class);
        int i = 1;
        MethodBodyEvaluator ev = new MethodBodyEvaluator(classInput, "testIfLe", "(I)Z");
        Object result = ev.evalStatic(new Object[] {i});
        Assert.assertEquals("testIfLe", IfTarget.testIfLe(i), result);
    }

    @org.junit.Test
    public void testIfNull() throws Exception {
        byte[] classInput = buildClass(IfTarget.class);
        Object o = new Object();
        MethodBodyEvaluator ev =
                new MethodBodyEvaluator(classInput, "testIfNull", "(Ljava/lang/Object;)Z");
        Object result = null;

        result = ev.evalStatic(new Object[] {o});
        Assert.assertEquals("testIfNull", IfTarget.testIfNull(o), result);

        result = ev.evalStatic(new Object[] {null});
        Assert.assertEquals("testIfNull", IfTarget.testIfNull(null), result);
    }

    @org.junit.Test
    public void testIfNonNull() throws Exception {
        byte[] classInput = buildClass(IfTarget.class);
        Object o = new Object();
        MethodBodyEvaluator ev =
                new MethodBodyEvaluator(classInput, "testIfNonNull", "(Ljava/lang/Object;)Z");
        Object result = null;

        result = ev.evalStatic(new Object[] {o});
        Assert.assertEquals("testIfNonNull", IfTarget.testIfNonNull(o), result);

        result = ev.evalStatic(new Object[] {null});
        Assert.assertEquals("testIfNonNull", IfTarget.testIfNonNull(null), result);
    }
}
