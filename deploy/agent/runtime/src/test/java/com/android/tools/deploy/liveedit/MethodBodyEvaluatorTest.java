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

public class MethodBodyEvaluatorTest {

    private static final float NO_DELTA = 0.0f;

    @org.junit.Test
    public void testSimpleReturn() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        TestTarget owner = new TestTarget();

        // return 5
        Object result =
                new MethodBodyEvaluator(classInput, "returnFive", "()I")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        Integer intResult = (Integer) result;
        Assert.assertEquals(owner.returnFive(), intResult.intValue());

        // return Happiness
        result =
                new MethodBodyEvaluator(classInput, "returnHappiness", "()Ljava/lang/String;")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        String stringResult = (String) result;
        Assert.assertEquals(owner.returnHappiness(), stringResult);

        result =
                new MethodBodyEvaluator(classInput, "returnField", "()Ljava/lang/String;")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        stringResult = (String) result;
        Assert.assertEquals(owner.returnField(), stringResult);

        result =
                new MethodBodyEvaluator(classInput, "returnPlusOne", "(I)I")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {1});
        intResult = (Integer) result;
        Assert.assertEquals(owner.returnPlusOne(1), intResult.intValue());

        result =
                new MethodBodyEvaluator(classInput, "returnSeventeen", "()I")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        intResult = (Integer) result;
        Assert.assertEquals(owner.returnSeventeen(), intResult.intValue());
    }

    @org.junit.Test
    public void testInvokeSpecialForPrivateMethod() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        TestTarget owner = new TestTarget();

        Object result =
                new MethodBodyEvaluator(classInput, "getPrivateField", "()I")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});

        Integer integer = (Integer) result;
        Assert.assertEquals(owner.getPrivateField(), integer.intValue());
    }

    @org.junit.Test
    public void testConstructor() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        TestTarget owner = new TestTarget();
        Object result =
                new MethodBodyEvaluator(
                                classInput,
                                "newParent",
                                "()Lcom/android/tools/deploy/liveedit/Parent;")
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
                new MethodBodyEvaluator(
                                classInput,
                                "newParentWithParameter",
                                "(I)Lcom/android/tools/deploy/liveedit/Parent;")
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
                new MethodBodyEvaluator(
                                classInput,
                                "callProtectedConstructor",
                                "()Lcom/android/tools/deploy/liveedit/Parent;")
                        .eval(child, Child.class.getTypeName(), new Object[] {});

        Assert.assertNotNull(result);
    }

    @org.junit.Test
    public void testSuperMethod() throws Exception {
        byte[] classInput = buildClass(Child.class);
        int a = 1;
        Child c = new Child(1);
        Object result =
                new MethodBodyEvaluator(classInput, "callSuperMethod", "(I)I")
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
                new MethodBodyEvaluator(classInput, "callInheritedProtectedMethod", "(I)I")
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
                new MethodBodyEvaluator(classInput, "getLongFields", "()J")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        Long l = (Long) result;
        Assert.assertEquals(owner.getLongFields(), l.longValue());
    }

    @org.junit.Test
    public void testFloatValue() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        TestTarget owner = new TestTarget();

        Object result =
                new MethodBodyEvaluator(classInput, "getFloatFields", "()F")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        Float f = (Float) result;
        Assert.assertTrue(owner.getFloatFields() == f.floatValue());
    }

    @org.junit.Test
    public void testDoubleValue() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        TestTarget owner = new TestTarget();

        Object result =
                new MethodBodyEvaluator(classInput, "getDoubleFields", "()D")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        Double d = (Double) result;
        Assert.assertTrue(owner.getDoubleFields() == d.doubleValue());
    }

    @org.junit.Test
    public void testBooleanValue() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        TestTarget owner = new TestTarget();

        Object result =
                new MethodBodyEvaluator(classInput, "getBooleanFields", "()Z")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        Boolean z = (Boolean) result;
        Assert.assertEquals(owner.getBooleanFields(), z);
    }

    @org.junit.Test
    public void testByteValue() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        TestTarget owner = new TestTarget();

        Object result =
                new MethodBodyEvaluator(classInput, "getByteFields", "()B")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        Byte b = (Byte) result;
        Assert.assertEquals(owner.getByteFields(), b.byteValue());
    }

    @org.junit.Test
    public void testShortValue() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        TestTarget owner = new TestTarget();

        Object result =
                new MethodBodyEvaluator(classInput, "getShortFields", "()S")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        Short s = (Short) result;
        Assert.assertEquals(owner.getShortFields(), s.byteValue());
    }

    @org.junit.Test
    public void testCharValue() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        TestTarget owner = new TestTarget();

        Object result =
                new MethodBodyEvaluator(classInput, "getCharacterFields", "()C")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        Character c = (Character) result;
        Assert.assertEquals(owner.getCharacterFields(), c.charValue());
    }

    @org.junit.Test
    public void testIntValue() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        TestTarget owner = new TestTarget();

        Object result =
                new MethodBodyEvaluator(classInput, "getIntegerFields", "()I")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        Integer i = (Integer) result;
        Assert.assertEquals(owner.getIntegerFields(), i.intValue());
    }

    @org.junit.Test
    public void testBooleanArray() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        TestTarget owner = new TestTarget();

        Object result =
                new MethodBodyEvaluator(classInput, "returnBooleanFromArray", "()Z")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        Boolean b = (Boolean) result;
        Assert.assertEquals(owner.returnBooleanFromArray(), b.booleanValue());
    }

    @org.junit.Test
    public void testCharArray() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        TestTarget owner = new TestTarget();

        Object result =
                new MethodBodyEvaluator(classInput, "returnCharFromArray", "()C")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        Character charResult = (Character) result;
        Assert.assertEquals(owner.returnCharFromArray(), charResult.charValue());
    }

    @org.junit.Test
    public void testByteArray() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        TestTarget owner = new TestTarget();

        Object result =
                new MethodBodyEvaluator(classInput, "returnByteFromArray", "()B")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        Byte byteResult = (Byte) result;
        Assert.assertEquals(owner.returnByteFromArray(), byteResult.byteValue());
    }

    @org.junit.Test
    public void testShortArray() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        TestTarget owner = new TestTarget();

        Object result =
                new MethodBodyEvaluator(classInput, "returnShortFromArray", "()S")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        Short shortResult = (Short) result;
        Assert.assertEquals(owner.returnShortFromArray(), shortResult.shortValue());
    }

    @org.junit.Test
    public void testObjectArray() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        TestTarget owner = new TestTarget();

        Object result =
                new MethodBodyEvaluator(classInput, "returnObjectFromArray", "()Ljava/lang/Object;")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        Object objectResult = result;
        Assert.assertEquals(owner.returnObjectFromArray(), objectResult);
    }

    @org.junit.Test
    public void testIntArray() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        TestTarget owner = new TestTarget();

        Object result =
                new MethodBodyEvaluator(classInput, "returnIntFromArray", "()I")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        Integer r = (Integer) result;
        Assert.assertEquals(owner.returnIntFromArray(), r.intValue());
    }

    @org.junit.Test
    public void testLongArray() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        TestTarget owner = new TestTarget();

        Object result =
                new MethodBodyEvaluator(classInput, "returnLongFromArray", "()J")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        Long r = (Long) result;
        Assert.assertEquals(owner.returnLongFromArray(), r.longValue());
    }

    @org.junit.Test
    public void testFloatArray() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        TestTarget owner = new TestTarget();

        Object result =
                new MethodBodyEvaluator(classInput, "returnFloatFromArray", "()F")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        Float r = (Float) result;
        Assert.assertTrue(owner.returnFloatFromArray() == r.floatValue());
    }

    @org.junit.Test
    public void testDoubleArray() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        TestTarget owner = new TestTarget();

        Object result =
                new MethodBodyEvaluator(classInput, "returnDoubleFromArray", "()D")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        Double r = (Double) result;
        Assert.assertTrue(owner.returnDoubleFromArray() == r.doubleValue());
    }

    @org.junit.Test
    public void testInstanceOf() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        TestTarget owner = new TestTarget();

        Object result =
                new MethodBodyEvaluator(classInput, "isInstanceOf", "()Z")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        Boolean z = (Boolean) result;
        Assert.assertEquals(owner.isInstanceOf(), z.booleanValue());
    }

    @org.junit.Test
    public void testParentStatic() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        TestTarget owner = new TestTarget();

        Object result =
                new MethodBodyEvaluator(classInput, "callParentStaticPlusFive", "()I")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        Integer r = (Integer) result;
        Assert.assertTrue(owner.callParentStaticPlusFive() == r.intValue());
    }

    @org.junit.Test
    public void testReturnVoid() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        TestTarget owner = new TestTarget();

        Object result =
                new MethodBodyEvaluator(classInput, "functionReturningVoid", "()V")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        Assert.assertTrue("Expected void value", result == null);
    }

    @org.junit.Test
    public void testTryFinally() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        TestTarget owner = new TestTarget();
        Integer result =
                (Integer)
                        new MethodBodyEvaluator(classInput, "tryFinally", "()I")
                                .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        Assert.assertEquals("finally block missed", result.intValue(), owner.tryFinally());
    }

    @org.junit.Test
    public void testTryCatch() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        TestTarget owner = new TestTarget();
        Integer result =
                (Integer)
                        new MethodBodyEvaluator(classInput, "tryCatch", "()I")
                                .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        Assert.assertEquals("catch block missed", result.intValue(), owner.tryCatch());
    }

    @org.junit.Test
    public void testParameterInvokeTypes() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        TestTarget owner = new TestTarget();

        Object result =
                new MethodBodyEvaluator(classInput, "invokeBooleanParamWithBool", "(Z)Z")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {true});
        Boolean r = (Boolean) result;
        Assert.assertTrue(
                "Invoke with typed parameters", owner.invokeBooleanParamWithBool(true) == r);
    }

    @org.junit.Test
    public void testStaticParameterInvokeTypes() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        Object result =
                new MethodBodyEvaluator(classInput, "staticInvokeBooleanParamWithBool", "(Z)Z")
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
                    new MethodBodyEvaluator(classInput, "tableSwitch1to4", "(I)I")
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
                    new MethodBodyEvaluator(classInput, "lookupSwitch1_5_10_15", "(I)I")
                            .evalStatic(new Object[] {i});
            Integer r = (Integer) result;
            Assert.assertTrue("LookupSwitch for value " + i, TestTarget.lookupSwitch1_5_10_15(i) == r);
        }
    }

    @org.junit.Test
    public void testOverload() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        Object result =
                new MethodBodyEvaluator(classInput, "returnMax", "(I)I")
                        .evalStatic(new Object[] {0});
        Integer i = (Integer) result;
        Assert.assertTrue("Overload (I)I", TestTarget.returnMax(0) == i);

        result =
                new MethodBodyEvaluator(classInput, "returnMax", "(J)J")
                        .evalStatic(new Object[] {0L});
        Long l = (Long) result;
        Assert.assertTrue("Overload (L)L", TestTarget.returnMax(0L) == l);
    }

    // Tests passing arrays to static and non-static methods.
    @org.junit.Test
    public void testArrayParameter() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        Object result =
                new MethodBodyEvaluator(classInput, "testArrayParameters", "()I")
                        .evalStatic(new Object[0]);
        Integer i = (Integer) result;
        Assert.assertEquals("Array parameters", TestTarget.testArrayParameters(), i.intValue());
    }

    @org.junit.Test
    public void testMultiArrayIntParameter() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        Object result =
                new MethodBodyEvaluator(classInput, "testMultiIntArray", "()I")
                        .evalStatic(new Object[0]);
        Integer i = (Integer) result;
        Assert.assertEquals("Multi Array Int get/set", TestTarget.testMultiIntArray(), i.intValue());
    }

    @org.junit.Test
    public void testMultiArrayCharParameter() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        Object result =
                new MethodBodyEvaluator(classInput, "testMultiCharacterArray", "()I")
                        .evalStatic(new Object[0]);
        Integer i = (Integer) result;
        Assert.assertEquals("Multi Array Char get/set", TestTarget.testMultiCharacterArray(), i.intValue());
    }

    @org.junit.Test
    public void testMultiArrayByteParameter() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        Object result =
                new MethodBodyEvaluator(classInput, "testMultiByteArray", "()I")
                        .evalStatic(new Object[0]);
        Integer i = (Integer) result;
        Assert.assertEquals("Multi Array Byte get/set", TestTarget.testMultiByteArray(), i.intValue());
    }

    @org.junit.Test
    public void testMultiArrayShortParameter() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        Object result =
                new MethodBodyEvaluator(classInput, "testMultiShortArray", "()I")
                        .evalStatic(new Object[0]);
        Integer i = (Integer) result;
        Assert.assertEquals("Multi Array Short get/set", TestTarget.testMultiShortArray(), i.intValue());
    }

    @org.junit.Test
    public void testMultiArrayLongParameter() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        Object result =
                new MethodBodyEvaluator(classInput, "testMultiLongArray", "()J")
                        .evalStatic(new Object[0]);
        Long l = (Long) result;
        Assert.assertEquals("Multi Array Long get/set", TestTarget.testMultiLongArray(), l.longValue());
    }

    @org.junit.Test
    public void testMultiArrayFloatParameter() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        Object result =
                new MethodBodyEvaluator(classInput, "testMultiFloatArray", "()F")
                        .evalStatic(new Object[0]);
        Float f = (Float) result;
        Assert.assertEquals(TestTarget.testMultiFloatArray(), f.floatValue(), NO_DELTA);
    }
    @org.junit.Test
    public void testMultiArrayDoubleParameter() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        Object result =
                new MethodBodyEvaluator(classInput, "testMultiDoubleArray", "()D")
                        .evalStatic(new Object[0]);
        Double d = (Double) result;
        Assert.assertEquals(TestTarget.testMultiDoubleArray(), d.doubleValue(), NO_DELTA);
    }

    @org.junit.Test
    public void testMultiArrayObjectParameter() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        Object result =
                new MethodBodyEvaluator(classInput, "testMultiObjectArray", "()Ljava/lang/Integer;")
                        .evalStatic(new Object[0]);
        Assert.assertEquals(
                "Multi Array Object get/set", TestTarget.testMultiObjectArray(), result);
    }

    @org.junit.Test
    public void testAccessProtectedParent() throws Exception {
        byte[] classInput = buildClass(Child.class);
        Child child = new Child(0);
        int protectedFieldValue = 5;
        Object result =
                new MethodBodyEvaluator(classInput, "accessParentProtectedField", "(I)I")
                        .eval(child, Child.class.getTypeName(), new Object[] {protectedFieldValue});
        Integer i = (Integer) result;

        Assert.assertEquals(
                "Accessed parent field",
                child.accessParentProtectedField(protectedFieldValue),
                i.intValue());
    }
}
