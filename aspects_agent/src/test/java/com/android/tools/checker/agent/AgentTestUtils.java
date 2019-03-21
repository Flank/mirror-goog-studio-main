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

package com.android.tools.checker.agent;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.function.Consumer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;

public class AgentTestUtils {

    private static byte[] renameClass(byte[] input, String oldClassName, String newClassName) {
        try {
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            ClassVisitor visitor =
                    new ClassRemapper(
                            writer,
                            new SimpleRemapper(ImmutableMap.of(oldClassName, newClassName)));
            ClassReader reader = new ClassReader(input);
            reader.accept(visitor, ClassReader.EXPAND_FRAMES);
            return writer.toByteArray();
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    static void callMethod(
            Class<?> clazz, Object instance, String methodName, boolean isPrivateMethod)
            throws NoSuchMethodException, IllegalAccessException {
        Method method = clazz.getDeclaredMethod(methodName);
        if (isPrivateMethod) {
            method.setAccessible(true);
        }
        try {
            method.invoke(instance);
        } catch (InvocationTargetException e) {
            Throwable t = e.getCause();
            if (!(t instanceof RuntimeException)) {
                throw new RuntimeException(t);
            } else {
                throw (RuntimeException) t;
            }
        }
    }

    static void callMethod(Object instance, String methodName, boolean isPrivateMethod)
            throws NoSuchMethodException, IllegalAccessException {
        callMethod(instance.getClass(), instance, methodName, isPrivateMethod);
    }

    public static void callMethod(Object instance, String methodName)
            throws NoSuchMethodException, IllegalAccessException {
        callMethod(instance, methodName, false);
    }

    public static StackTraceElement[] stackTraceBuilder(
            String class1, String method1, String class2, String method2) {
        StackTraceElement topElement = new StackTraceElement(class1, method1, null, 0);
        StackTraceElement secondTopElement = new StackTraceElement(class2, method2, null, 0);

        StackTraceElement[] stackTrace = new StackTraceElement[5];
        // Ignore the first two elements, as they should be Thread.currentThread().getStackTrace()
        // and the method who called Baseline#isWhitelisted.
        stackTrace[2] = topElement;
        stackTrace[3] = secondTopElement;

        return stackTrace;
    }

    public static Class<?> loadAndTransform(
            String className,
            ImmutableMap<String, String> matcher,
            Consumer<String> notFoundCallback)
            throws IOException {
        String binaryTestClassName = TestClass.class.getCanonicalName().replace('.', '/');
        byte[] classInput =
                ByteStreams.toByteArray(
                        Objects.requireNonNull(
                                InstrumentTest.class
                                        .getClassLoader()
                                        .getResourceAsStream(binaryTestClassName + ".class")));
        byte[] newClass =
                Transform.transformClass(
                        classInput,
                        TestClass.class.getCanonicalName(),
                        matcher::get,
                        notFoundCallback);
        newClass = renameClass(newClass, binaryTestClassName, "Test2");

        TestClassLoader cl = new TestClassLoader(InstrumentTest.class.getClassLoader());
        return cl.defineClass(className, newClass);
    }
}
