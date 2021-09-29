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
import org.junit.Test;

public class CmpTest {
    @org.junit.Test
    public void testIcmpEq() throws Exception {
        byte[] classInput = buildClass(CmpTarget.class);
        int i1 = 1;
        int i2 = 2;
        MethodBodyEvaluator ev = new MethodBodyEvaluator(classInput, "testIcmpEq(II)Z");
        Object result = null;

        result = ev.evalStatic(new Object[] {i1, i2});
        Assert.assertEquals("icmpEq", CmpTarget.testIcmpEq(i1, i2), result);

        result = ev.evalStatic(new Object[] {i2, i1});
        Assert.assertEquals("icmpEq", CmpTarget.testIcmpEq(i1, i2), result);

        result = ev.evalStatic(new Object[] {i1, i1});
        Assert.assertEquals("icmpEq", CmpTarget.testIcmpEq(i1, i1), result);
    }

    @org.junit.Test
    public void testIcmpNeq() throws Exception {
        byte[] classInput = buildClass(CmpTarget.class);
        int i1 = 1;
        int i2 = 2;
        MethodBodyEvaluator ev = new MethodBodyEvaluator(classInput, "testIcmpNeq(II)Z");
        Object result = null;

        result = ev.evalStatic(new Object[] {i1, i2});
        Assert.assertEquals("icmpNeq", CmpTarget.testIcmpNeq(i1, i2), result);

        result = ev.evalStatic(new Object[] {i2, i1});
        Assert.assertEquals("icmpNeq", CmpTarget.testIcmpNeq(i2, i1), result);

        result = ev.evalStatic(new Object[] {i1, i1});
        Assert.assertEquals("icmpNeq", CmpTarget.testIcmpNeq(i1, i1), result);
    }

    @org.junit.Test
    public void testIcmpGe() throws Exception {
        byte[] classInput = buildClass(CmpTarget.class);
        int i1 = 1;
        int i2 = 2;
        MethodBodyEvaluator ev = new MethodBodyEvaluator(classInput, "testIcmpGe(II)Z");
        Object result = null;

        result = ev.evalStatic(new Object[] {i1, i2});
        Assert.assertEquals("icmpGe", CmpTarget.testIcmpGe(i1, i2), result);

        result = ev.evalStatic(new Object[] {i2, i1});
        Assert.assertEquals("icmpGe", CmpTarget.testIcmpGe(i2, i1), result);

        result = ev.evalStatic(new Object[] {i1, i1});
        Assert.assertEquals("icmpGe", CmpTarget.testIcmpGe(i1, i1), result);
    }

    @org.junit.Test
    public void testIcmpLe() throws Exception {
        byte[] classInput = buildClass(CmpTarget.class);
        int i1 = 1;
        int i2 = 2;
        MethodBodyEvaluator ev = new MethodBodyEvaluator(classInput, "testIcmpLe(II)Z");
        Object result = null;

        result = ev.evalStatic(new Object[] {i1, i2});
        Assert.assertEquals("icmpLe", CmpTarget.testIcmpLe(i1, i2), result);

        result = ev.evalStatic(new Object[] {i2, i1});
        Assert.assertEquals("icmpLe", CmpTarget.testIcmpLe(i2, i1), result);

        result = ev.evalStatic(new Object[] {i1, i1});
        Assert.assertEquals("icmpLe", CmpTarget.testIcmpLe(i1, i1), result);
    }

    @org.junit.Test
    public void testIcmpGt() throws Exception {
        byte[] classInput = buildClass(CmpTarget.class);
        int i1 = 1;
        int i2 = 2;
        MethodBodyEvaluator ev = new MethodBodyEvaluator(classInput, "testIcmpGt(II)Z");
        Object result = null;

        result = ev.evalStatic(new Object[] {i1, i2});
        Assert.assertEquals("icmpGt", CmpTarget.testIcmpGt(i1, i2), result);

        result = ev.evalStatic(new Object[] {i2, i1});
        Assert.assertEquals("icmpGt", CmpTarget.testIcmpGt(i2, i1), result);

        result = ev.evalStatic(new Object[] {i1, i1});
        Assert.assertEquals("icmpGt", CmpTarget.testIcmpGt(i1, i1), result);
    }

    @org.junit.Test
    public void testIcmpLt() throws Exception {
        byte[] classInput = buildClass(CmpTarget.class);
        int i1 = 1;
        int i2 = 2;
        MethodBodyEvaluator ev = new MethodBodyEvaluator(classInput, "testIcmpLt(II)Z");
        Object result = null;

        result = ev.evalStatic(new Object[] {i1, i2});
        Assert.assertEquals("icmpLt", CmpTarget.testIcmpLt(i1, i2), result);

        result = ev.evalStatic(new Object[] {i2, i1});
        Assert.assertEquals("icmpLt", CmpTarget.testIcmpLt(i2, i1), result);

        result = ev.evalStatic(new Object[] {i1, i1});
        Assert.assertEquals("icmpLt", CmpTarget.testIcmpLt(i1, i1), result);
    }

    @Test
    public void testIcmpAne() throws Exception {
        byte[] classInput = buildClass(CmpTarget.class);
        Object o1 = new Object();
        Object o2 = new Object();
        MethodBodyEvaluator ev =
                new MethodBodyEvaluator(
                        classInput, "testIcmpLe(Ljava/lang/Object;Ljava/lang/Object;)Z");
        Object result = null;

        result = ev.evalStatic(new Object[] {o1, null});
        Assert.assertEquals("icmpLe", CmpTarget.testIcmpLe(o1, null), result);

        result = ev.evalStatic(new Object[] {null, o2});
        Assert.assertEquals("icmpLe", CmpTarget.testIcmpLe(null, o2), result);

        result = ev.evalStatic(new Object[] {o1, o1});
        Assert.assertEquals("icmpLe", CmpTarget.testIcmpLe(o1, o1), result);

        result = ev.evalStatic(new Object[] {null, null});
        Assert.assertEquals("icmpLe", CmpTarget.testIcmpLe(null, null), result);
    }

    @org.junit.Test
    public void testIcmpAeq() throws Exception {
        byte[] classInput = buildClass(CmpTarget.class);
        Object o1 = new Object();
        Object o2 = new Object();
        MethodBodyEvaluator ev =
                new MethodBodyEvaluator(
                        classInput, "testIcmpNe(Ljava/lang/Object;Ljava/lang/Object;)Z");
        Object result = null;

        result = ev.evalStatic(new Object[] {o1, null});
        Assert.assertEquals("icmpNe", CmpTarget.testIcmpNe(o1, null), result);

        result = ev.evalStatic(new Object[] {null, o2});
        Assert.assertEquals("icmpNe", CmpTarget.testIcmpNe(null, o2), result);

        result = ev.evalStatic(new Object[] {o1, o1});
        Assert.assertEquals("icmpNe", CmpTarget.testIcmpNe(o1, o1), result);

        result = ev.evalStatic(new Object[] {null, null});
        Assert.assertEquals("icmpNe", CmpTarget.testIcmpNe(null, null), result);

        Integer i1 = 1;
        Integer i2 = 1;
        result = ev.evalStatic(new Object[] {i1, i2});
        Assert.assertEquals("icmpNe", CmpTarget.testIcmpNe(i1, i2), result);
    }
}
