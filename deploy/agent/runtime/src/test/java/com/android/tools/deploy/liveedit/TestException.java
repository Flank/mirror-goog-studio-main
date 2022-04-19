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

import com.android.tools.deploy.interpreter.HoudiniConfiguration;
import java.lang.reflect.Method;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;

public class TestException {

    static {
        LiveEditStubs.init(TestException.class.getClassLoader());
    }

    // Temporary. Delete this and enabled all tests once we get out of Canary
    @Before
    public void setup() {
        HoudiniConfiguration.ENABLED = true;
    }

    @After
    public void tearDown() {
        HoudiniConfiguration.ENABLED = HoudiniConfiguration.ENABLED_DEFAULT_VALUE;
    }

    @org.junit.Test
    public void testExceptionFromAppAfterInterpreter() throws Exception {
        if (!HoudiniConfiguration.ENABLED) {
            return;
        }
        String className = "InvokeException";
        String methodName = "throwFromAppAfterInterpreter";
        String methodDesc = "()V";
        Class clazz = InvokeException.class;
        byte[] byteCode = buildClass(clazz);
        int stubInvocationLineNumber = -1;

        try {
            LiveEditStubs.addClass(className, new Interpretable(byteCode), false);
            LiveEditStubs.addLiveEditedMethod(className, methodName, methodDesc);
            Object[] parameters = {null, null};
            stubInvocationLineNumber = getNextLineNumber();
            stubbedMethod(className, methodName, methodDesc, parameters);
        } catch (Exception e) {
            Assert.assertEquals(
                    "Expected exception", e.getClass(), ArrayIndexOutOfBoundsException.class);

            StackTraceElement[] actual = slice(e.getStackTrace(), 0, 3);
            StackTraceElement[] expected = new StackTraceElement[actual.length];
            expected[0] =
                    new StackTraceElement(
                            "com.android.tools.deploy.liveedit.InvokeException",
                            "appThrowException",
                            "InvokeException.java",
                            InvokeException.appThrowExceptionLineNumber);
            expected[1] =
                    new StackTraceElement(
                            "com.android.tools.deploy.liveedit.InvokeException",
                            "throwFromAppAfterInterpreter",
                            "InvokeException.java",
                            InvokeException.throwFromAppLineNumber);
            expected[2] =
                    new StackTraceElement(
                            "com.android.tools.deploy.liveedit.TestException",
                            "testExceptionFromAppAfterInterpreter",
                            "TestException.java",
                            stubInvocationLineNumber);
            assertStackTraceMatch("Unexpected stack trace", expected, actual);
        } finally {
            LiveEditStubs.deleteClass(className);
        }
    }

    @org.junit.Test
    public void testExceptionFromInterpreter() throws Exception {
        if (!HoudiniConfiguration.ENABLED) {
            return;
        }
        String className = "InvokeException";
        String methodName = "throwFromInterpreter";
        String methodDesc = "()V";
        Class clazz = InvokeException.class;
        byte[] byteCode = buildClass(clazz);
        int stubInvocationLineNumber = -1;

        try {
            LiveEditStubs.addClass(className, new Interpretable(byteCode), false);
            LiveEditStubs.addLiveEditedMethod(className, methodName, methodDesc);
            Object[] parameters = {null, null};
            stubInvocationLineNumber = getNextLineNumber();
            stubbedMethod(className, methodName, methodDesc, parameters);
        } catch (Exception e) {
            Assert.assertEquals(
                    "Expected exception", e.getClass(), ArrayIndexOutOfBoundsException.class);
            StackTraceElement[] actual = slice(e.getStackTrace(), 0, 2);
            StackTraceElement[] expected = new StackTraceElement[actual.length];
            expected[0] =
                    new StackTraceElement(
                            "com.android.tools.deploy.liveedit.InvokeException",
                            "throwFromInterpreter",
                            "InvokeException.java",
                            InvokeException.throwFromInterpreterLineNumber);
            expected[1] =
                    new StackTraceElement(
                            "com.android.tools.deploy.liveedit.TestException",
                            "testExceptionFromInterpreter",
                            "TestException.java",
                            stubInvocationLineNumber);
            assertStackTraceMatch("Unexpected stack trace", expected, actual);

        } finally {
            LiveEditStubs.deleteClass(className);
        }
    }

    @org.junit.Test
    public void testExceptionThrowAndCaughtInsideMethod() throws Exception {
        if (!HoudiniConfiguration.ENABLED) {
            return;
        }
        String className = "InvokeException";
        String methodName = "tryAndCatch";
        String methodDesc = "()Ljava/lang/StackTraceElement;";
        Class clazz = InvokeException.class;
        byte[] byteCode = buildClass(clazz);

        try {
            LiveEditStubs.addClass(className, new Interpretable(byteCode), false);
            LiveEditStubs.addLiveEditedMethod(className, methodName, methodDesc);
            Object[] parameters = {null, null};
            StackTraceElement[] actual = {
                (StackTraceElement) stubbedMethod(className, methodName, methodDesc, parameters)
            };
            StackTraceElement[] expected = new StackTraceElement[1];
            expected[0] =
                    new StackTraceElement(
                            "com.android.tools.deploy.liveedit.InvokeException",
                            "tryAndCatch",
                            "InvokeException.java",
                            InvokeException.tryAndCatchLineNumber);
            assertStackTraceMatch("inner interpreted method exception", expected, actual);
        } finally {
            LiveEditStubs.deleteClass(className);
        }
    }

    public Object stubbedMethod(
            String internalClassName, String methodName, String methodDesc, Object[] parameters) {
        return LiveEditStubs.stubL(internalClassName, methodName, methodDesc, parameters);
    }

    @org.junit.Test
    public void testLiveEditStubsStructure() {
        if (!HoudiniConfiguration.ENABLED) {
            return;
        }
        Class clazz = LiveEditStubs.class;
        Method[] methods = clazz.getDeclaredMethods();

        int doXXXCount = 0;
        for (Method method : methods) {
            if (method.getName().startsWith("doStub")) {
                doXXXCount++;
            }
        }

        Assert.assertEquals(
                "Only one doStubXXX method can exist in " + clazz.getCanonicalName(),
                1,
                doXXXCount);

        int subXCount = 0;
        for (Method method : methods) {
            if (method.getName().startsWith("stub")) {
                subXCount++;
            }
        }

        Assert.assertEquals(
                "stubX method count unexpected " + clazz.getCanonicalName(), 10, subXCount);
    }

    static int getNextLineNumber() {
        int line = Thread.currentThread().getStackTrace()[2].getLineNumber() + 1;
        return line;
    }

    StackTraceElement[] slice(StackTraceElement[] elements, int start, int length) {
        StackTraceElement[] slice = new StackTraceElement[length];
        for (int i = 0; i < length; i++) {
            slice[i] = elements[start + i];
        }
        return slice;
    }

    // We cannot use assertArrayEqual since openjdk 8 and 9 differ in terms of how StackTraceElement
    // "equals" is implemented (the latter uses module and classloader).
    private static void assertStackTraceMatch(
            String msg, StackTraceElement[] expected, StackTraceElement[] actual) {

        if (expected.length != actual.length) {
            msg += " array size differ, expected=" + expected.length + " but got=" + actual.length;
            Assert.fail(msg);
        }

        for (int i = 0; i < expected.length; i++) {
            if (!areEqual(expected[i], actual[i])) {
                msg += " at element " + i;
                msg += "expected = (" + expected[i].toString() + ")";
                msg += "actual= (" + actual[i].toString() + ")";
                Assert.fail(msg);
            }
        }
    }

    private static boolean areEqual(StackTraceElement a, StackTraceElement b) {
        return a.getLineNumber() == b.getLineNumber()
                && a.getClassName().equals(b.getClassName())
                && a.getMethodName().equals(b.getMethodName())
                && a.getFileName().equals(b.getFileName());
    }
}
