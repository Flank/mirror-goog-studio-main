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

import com.android.tools.idea.util.StudioPathManager;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.jetbrains.eval4j.ObjectValue;
import org.junit.Assert;

public class MethodBodyEvaluatorTest {

    @org.junit.Test
    public void testSimpleReturn() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        TestTarget owner = new TestTarget();

        // return 5
        Object result =
                new MethodBodyEvaluator(classInput, "returnFive")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        Integer intResult = (Integer) result;
        Assert.assertEquals(owner.returnFive(), intResult.intValue());

        // return Happiness
        result =
                new MethodBodyEvaluator(classInput, "returnHappiness")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        String stringResult = (String) result;
        Assert.assertEquals(owner.returnHappiness(), stringResult);

        result =
                new MethodBodyEvaluator(classInput, "returnField")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        stringResult = (String) result;
        Assert.assertEquals(owner.returnField(), stringResult);

        result =
                new MethodBodyEvaluator(classInput, "returnPlusOne")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {1});
        intResult = (Integer) result;
        Assert.assertEquals(owner.returnPlusOne(1), intResult.intValue());

        result =
                new MethodBodyEvaluator(classInput, "returnSeventeen")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        intResult = (Integer) result;
        Assert.assertEquals(owner.returnSeventeen(), intResult.intValue());
    }

    @org.junit.Test
    public void testInvokeSpecialForPrivateMethod() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        TestTarget owner = new TestTarget();

        Object result =
                new MethodBodyEvaluator(classInput, "getPrivateField")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});

        Integer integer = (Integer) result;
        Assert.assertEquals(owner.getPrivateField(), integer.intValue());
    }

    @org.junit.Test
    public void testConstructor() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        TestTarget owner = new TestTarget();
        Object result =
                new MethodBodyEvaluator(classInput, "newParent")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});

        Parent actual = (Parent) ((ObjectValue) result).getValue();
        Parent expected = owner.newParent();
        Assert.assertEquals(actual.getId(), expected.getId());
    }

    // Test constructor with inheritance

    @org.junit.Test
    public void testConstructorWithParameter() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        TestTarget owner = new TestTarget();
        Object result =
                new MethodBodyEvaluator(classInput, "newParentWithParameter")
                        .eval(
                                owner,
                                TestTarget.class.getTypeName(),
                                new Object[] {Integer.valueOf(4)});

        Parent actual = (Parent) ((ObjectValue) result).getValue();
        Parent expected = owner.newParentWithParameter(4);
        Assert.assertEquals(expected.getId(), actual.getId());
    }

    @org.junit.Test
    public void testSuperMethod() throws Exception {
        byte[] classInput = buildClass(Child.class);
        int a = 1;
        Child c = new Child(1);
        Object result =
                new MethodBodyEvaluator(classInput, "callSuperMethod")
                        .eval(c, Parent.class.getTypeName(), new Object[] {a});

        int actual = (Integer) result;
        int expected = c.callSuperMethod(a);
        Assert.assertEquals(expected, actual);
    }

    @org.junit.Test
    public void testBooleanArray() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        TestTarget owner = new TestTarget();

        Object result =
                new MethodBodyEvaluator(classInput, "returnBooleanFromArray")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        Boolean b = (Boolean) result;
        Assert.assertEquals(owner.returnBooleanFromArray(), b.booleanValue());
    }

    @org.junit.Test
    public void testCharArray() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        TestTarget owner = new TestTarget();

        Object result =
                new MethodBodyEvaluator(classInput, "returnCharFromArray")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        Character charResult = (Character) result;
        Assert.assertEquals(owner.returnCharFromArray(), charResult.charValue());
    }

    @org.junit.Test
    public void testByteArray() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        TestTarget owner = new TestTarget();

        Object result =
                new MethodBodyEvaluator(classInput, "returnByteFromArray")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        Byte byteResult = (Byte) result;
        Assert.assertEquals(owner.returnByteFromArray(), byteResult.byteValue());
    }

    @org.junit.Test
    public void testShortArray() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        TestTarget owner = new TestTarget();

        Object result =
                new MethodBodyEvaluator(classInput, "returnShortFromArray")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        Short shortResult = (Short) result;
        Assert.assertEquals(owner.returnShortFromArray(), shortResult.shortValue());
    }

    @org.junit.Test
    public void testObjectArray() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        TestTarget owner = new TestTarget();

        Object result =
                new MethodBodyEvaluator(classInput, "returnObjectFromArray")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        Object objectResult = result;
        Assert.assertEquals(owner.returnObjectFromArray(), objectResult);
    }

    @org.junit.Test
    public void testIntArray() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        TestTarget owner = new TestTarget();

        Object result =
                new MethodBodyEvaluator(classInput, "returnIntFromArray")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        Integer r = (Integer) result;
        Assert.assertEquals(owner.returnIntFromArray(), r.intValue());
    }

    @org.junit.Test
    public void testLongArray() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        TestTarget owner = new TestTarget();

        Object result =
                new MethodBodyEvaluator(classInput, "returnLongFromArray")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        Long r = (Long) result;
        Assert.assertEquals(owner.returnLongFromArray(), r.longValue());
    }

    @org.junit.Test
    public void testFloatArray() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        TestTarget owner = new TestTarget();

        Object result =
                new MethodBodyEvaluator(classInput, "returnFloatFromArray")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        Float r = (Float) result;
        Assert.assertTrue(owner.returnFloatFromArray() == r.floatValue());
    }

    @org.junit.Test
    public void testDoubleArray() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        TestTarget owner = new TestTarget();

        Object result =
                new MethodBodyEvaluator(classInput, "returnDoubleFromArray")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        Double r = (Double) result;
        Assert.assertTrue(owner.returnDoubleFromArray() == r.doubleValue());
    }

    @org.junit.Test
    public void testInstanceOf() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        TestTarget owner = new TestTarget();

        Object result =
                new MethodBodyEvaluator(classInput, "isInstanceOf")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        Boolean z = (Boolean) result;
        Assert.assertEquals(owner.isInstanceOf(), z.booleanValue());
    }

    /**
     * Extract the bytecode data from a class file from a class. If this is run from intellij, it
     * searches for .class file in the idea out director. If this is run from a jar file, it
     * extracts the class file from the jar.
     */
    private static byte[] buildClass(Class clazz) throws IOException {
        InputStream in = null;
        if (StudioPathManager.isRunningFromSources()) {
            String loc =
                    StudioPathManager.getSourcesRoot()
                            + "/tools/adt/idea/out/test/android.sdktools.deployer.deployer-runtime-support/"
                            + clazz.getName().replaceAll("\\.", "/")
                            + ".class";
            File file = new File(loc);
            if (file.exists()) {
                in = new FileInputStream(file);
            } else {
                in = clazz.getResourceAsStream(clazz.getSimpleName() + ".class");
            }
        } else {
            in = clazz.getResourceAsStream(clazz.getSimpleName() + ".class");
        }

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        byte[] buffer = new byte[0xFFFF];
        for (int len = in.read(buffer); len != -1; len = in.read(buffer)) {
            os.write(buffer, 0, len);
        }
        return os.toByteArray();
    }
}
