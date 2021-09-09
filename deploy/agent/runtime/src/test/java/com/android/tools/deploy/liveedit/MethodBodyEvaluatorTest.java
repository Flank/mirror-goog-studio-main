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
import java.util.function.Supplier;
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

        Parent actual = (Parent) result;
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

        Parent actual = (Parent) result;
        Parent expected = owner.newParentWithParameter(4);
        Assert.assertEquals(expected.getId(), actual.getId());
    }

    @org.junit.Test
    public void testProtectedConstructor() throws Exception {
        byte[] classInput = buildClass(Child.class);
        Child child = new Child(0);
        Object result =
                new MethodBodyEvaluator(classInput, "callProtectedConstructor")
                        .eval(child, Child.class.getTypeName(), new Object[] {});

        Assert.assertNotNull(result);
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
    public void testInheritedProtectedMethod() throws Exception {
        byte[] classInput = buildClass(Child.class);
        int a = 1;
        Child c = new Child(1);
        Object result =
                new MethodBodyEvaluator(classInput, "callInheritedProtectedMethod")
                        .eval(c, Parent.class.getTypeName(), new Object[] {a});

        int actual = (Integer) result;
        int expected = c.callInheritedProtectedMethod(a);
        Assert.assertEquals(expected, actual);
    }

    @org.junit.Test
    public void testLongValue() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        TestTarget owner = new TestTarget();
        Object result =
                new MethodBodyEvaluator(classInput, "getLongFields")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        Long l = (Long) result;
        Assert.assertEquals(owner.getLongFields(), l.longValue());
    }

    @org.junit.Test
    public void testFloatValue() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        TestTarget owner = new TestTarget();

        Object result =
                new MethodBodyEvaluator(classInput, "getFloatFields")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        Float f = (Float) result;
        Assert.assertTrue(owner.getFloatFields() == f.floatValue());
    }

    @org.junit.Test
    public void testDoubleValue() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        TestTarget owner = new TestTarget();

        Object result =
                new MethodBodyEvaluator(classInput, "getDoubleFields")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        Double d = (Double) result;
        Assert.assertTrue(owner.getDoubleFields() == d.doubleValue());
    }

    @org.junit.Test
    public void testBooleanValue() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        TestTarget owner = new TestTarget();

        Object result =
                new MethodBodyEvaluator(classInput, "getBooleanFields")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        Boolean z = (Boolean) result;
        Assert.assertEquals(owner.getBooleanFields(), z);
    }

    @org.junit.Test
    public void testByteValue() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        TestTarget owner = new TestTarget();

        Object result =
                new MethodBodyEvaluator(classInput, "getByteFields")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        Byte b = (Byte) result;
        Assert.assertEquals(owner.getByteFields(), b.byteValue());
    }

    @org.junit.Test
    public void testShortValue() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        TestTarget owner = new TestTarget();

        Object result =
                new MethodBodyEvaluator(classInput, "getShortFields")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        Short s = (Short) result;
        Assert.assertEquals(owner.getShortFields(), s.byteValue());
    }

    @org.junit.Test
    public void testCharValue() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        TestTarget owner = new TestTarget();

        Object result =
                new MethodBodyEvaluator(classInput, "getCharacterFields")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        Character c = (Character) result;
        Assert.assertEquals(owner.getCharacterFields(), c.charValue());
    }

    @org.junit.Test
    public void testIntValue() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        TestTarget owner = new TestTarget();

        Object result =
                new MethodBodyEvaluator(classInput, "getIntegerFields")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        Integer i = (Integer) result;
        Assert.assertEquals(owner.getIntegerFields(), i.intValue());
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

    @org.junit.Test
    public void testParentStatic() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        TestTarget owner = new TestTarget();

        Object result =
                new MethodBodyEvaluator(classInput, "callParentStaticPlusFive")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        Integer r = (Integer) result;
        Assert.assertTrue(owner.callParentStaticPlusFive() == r.intValue());
    }

    @org.junit.Test
    public void testLambda() throws IOException, ClassNotFoundException {
        String pkg = "com.android.tools.deploy.liveedit.";
        String lambdaSimpleName = "StaticGetterFactoryKt$create$1";
        String lambdaBinaryName = pkg + lambdaSimpleName;
        String targetSimpleName = "StaticGetterFactoryKt";
        String targetBinaryName = pkg + targetSimpleName;

        byte[] interpretedClass =
                buildClass(
                        StaticGetterFactoryKt.class.getClassLoader().loadClass(targetBinaryName));
        byte[][] supportClasses = new byte[2][];
        supportClasses[0] = buildClass(lambdaBinaryName);
        supportClasses[1] = interpretedClass;
        MethodBodyEvaluator evaluator =
                new MethodBodyEvaluator(
                        interpretedClass,
                        "create",
                        supportClasses,
                        this.getClass().getClassLoader());

        // Make sure the lambda class is not loaded
        Supplier<Integer> sLiveEdit = (Supplier<Integer>) evaluator.evalStatic(new Object[] {});
        Supplier<Integer> sVM = StaticGetterFactoryKt.create();
        Assert.assertNotEquals(
                sLiveEdit.getClass().getClassLoader(), sVM.getClass().getClassLoader());

        // Increase the VM static value, this should leave the one in LiveEdit unchanged.
        StaticGetterFactoryKt.setStatic(StaticGetterFactoryKt.getStatic() + 1);
        // Check that indeed the previous instruction did not affect support classes
        Assert.assertNotEquals(sLiveEdit.get(), sVM.get());
    }

    private static byte[] buildClass(String binaryClassName)
            throws IOException, ClassNotFoundException {
        Class klass = Class.forName(binaryClassName);
        return buildClass(klass);
    }
    /**
     * Extract the bytecode data from a class file from its name. If this is run from intellij, it
     * searches for .class file in the idea out director. If this is run from a jar file, it
     * extracts the class file from the jar.
     */
    private static byte[] buildClass(Class clazz) throws IOException {
        InputStream in = null;
        String pathToSearch = "/" + clazz.getName().replaceAll("\\.", "/") + ".class";
        if (StudioPathManager.isRunningFromSources()) {
            String loc =
                    StudioPathManager.getSourcesRoot()
                            + "/tools/adt/idea/out/test/android.sdktools.deployer.deployer-runtime-support"
                            + pathToSearch;
            File file = new File(loc);
            if (file.exists()) {
                in = new FileInputStream(file);
            } else {
                in = clazz.getResourceAsStream(pathToSearch);
            }
        } else {
            in = clazz.getResourceAsStream(pathToSearch);
        }

        if (in == null) {
            throw new IllegalStateException(
                    "Unable to load '" + clazz + "' from classLoader " + clazz.getClassLoader());
        }
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        byte[] buffer = new byte[0xFFFF];
        for (int len = in.read(buffer); len != -1; len = in.read(buffer)) {
            os.write(buffer, 0, len);
        }
        return os.toByteArray();
    }

    @org.junit.Test
    public void testReturnVoid() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        TestTarget owner = new TestTarget();

        Object result =
                new MethodBodyEvaluator(classInput, "functionReturningVoid")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        Assert.assertTrue("Expected void value", result == null);
    }

    @org.junit.Test
    public void testTryFinally() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        TestTarget owner = new TestTarget();
        Integer result =
                (Integer)
                        new MethodBodyEvaluator(classInput, "tryFinally")
                                .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        Assert.assertEquals("finally block missed", result.intValue(), owner.tryFinally());
    }

    @org.junit.Test
    public void testTryCatch() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        TestTarget owner = new TestTarget();
        Integer result =
                (Integer)
                        new MethodBodyEvaluator(classInput, "tryCatch")
                                .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        Assert.assertEquals("catch block missed", result.intValue(), owner.tryCatch());
    }

    @org.junit.Test
    public void testParameterInvokeTypes() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        TestTarget owner = new TestTarget();

        Object result =
                new MethodBodyEvaluator(classInput, "invokeBooleanParamWithBool")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {true});
        Boolean r = (Boolean) result;
        Assert.assertTrue(
                "Invoke with typed parameters", owner.invokeBooleanParamWithBool(true) == r);
    }

    @org.junit.Test
    public void testStaticParameterInvokeTypes() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        Object result =
                new MethodBodyEvaluator(classInput, "staticInvokeBooleanParamWithBool")
                        .evalStatic(new Object[] {true});
        Boolean r = (Boolean) result;
        Assert.assertTrue(
                "Static invoke with type parameters",
                TestTarget.staticInvokeBooleanParamWithBool(true) == r);
    }

    @org.junit.Test
    public void testTableSwitch() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        for (int i = 0; i < 5; i++) {
            Object result =
                    new MethodBodyEvaluator(classInput, "tableSwitch1to4")
                            .evalStatic(new Object[] {i});
            Integer r = (Integer) result;
            Assert.assertTrue("Tableswitch for value " + i, TestTarget.tableSwitch1to4(i) == r);
        }
    }

    @org.junit.Test
    public void testLookupSwitch() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        for (int i = 0; i < 16; i++) {
            Object result =
                    new MethodBodyEvaluator(classInput, "lookupSwitch1_5_10_15")
                            .evalStatic(new Object[] {i});
            Integer r = (Integer) result;
            Assert.assertTrue("LookupSwitch for value " + i, TestTarget.lookupSwitch1_5_10_15(i) == r);
        }
    }
}
