package com.android.tools.checker.agent;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.fail;

import com.android.tools.checker.TestAssertions;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;

public class InstrumentTest {
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

    private static void callMethod(
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

    private static void callMethod(Object instance, String methodName, boolean isPrivateMethod)
            throws NoSuchMethodException, IllegalAccessException {
        callMethod(instance.getClass(), instance, methodName, isPrivateMethod);
    }

    private static void callMethod(Object instance, String methodName)
            throws NoSuchMethodException, IllegalAccessException {
        callMethod(instance, methodName, false);
    }

    private static Class<?> loadAndTransform(
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

    @Test
    public void testInstance()
            throws IOException, IllegalAccessException, InstantiationException,
                    NoSuchMethodException {
        Set<String> notFound = new HashSet<>();
        ImmutableMap<String, String> matcher =
                ImmutableMap.of(
                        "com.android.tools.checker.agent.TestClass.privateMethodThrows()V",
                        "com.android.tools.checker.TestAssertions#fail",
                        "com.android.tools.checker.agent.TestClass.methodNop()V",
                        "com.android.tools.checker.TestAssertions#count");
        Object instance = loadAndTransform("Test2", matcher, notFound::add).newInstance();
        TestAssertions.count = 0;
        assertEquals(0, TestAssertions.count);
        callMethod(instance, "methodNop");
        assertEquals(1, TestAssertions.count);
        callMethod(instance, "methodDoNotThrow");

        try {
            callMethod(instance, "publicMethodThrows");
            fail("Method must throw an exception");
        } catch (RuntimeException e) {
            assertEquals("Fail", e.getMessage());
        }

        try {
            callMethod(instance, "privateMethodThrows", true);
            fail("Method must throw an exception");
        } catch (RuntimeException e) {
            assertEquals("Fail", e.getMessage());
        }

        Assert.assertThat(
                notFound,
                CoreMatchers.not(
                        CoreMatchers.hasItems(
                                "com.android.tools.checker.agent.TestClass.methodNop()V",
                                "com.android.tools.checker.agent.TestClass.privateMethodThrows()V")));
    }

    @Test
    public void testSimplifiedName()
            throws IOException, IllegalAccessException, InstantiationException,
                    NoSuchMethodException {
        Set<String> notFound = new HashSet<>();
        ImmutableMap<String, String> matcher =
                ImmutableMap.of(
                        "com.android.tools.checker.agent.TestClass.methodNop",
                        "com.android.tools.checker.TestAssertions#count");
        Object instance = loadAndTransform("Test2", matcher, notFound::add).newInstance();
        TestAssertions.count = 0;
        assertEquals(0, TestAssertions.count);
        callMethod(instance, "methodNop");
        assertEquals(1, TestAssertions.count);
    }

    @Test
    public void testAnnotations()
            throws IOException, IllegalAccessException, InstantiationException,
                    NoSuchMethodException {
        Set<String> notFound = new HashSet<>();
        ImmutableMap<String, String> matcher =
                ImmutableMap.of(
                        "@com.android.tools.checker.BlockingTest",
                        "com.android.tools.checker.TestAssertions#count");
        Object instance = loadAndTransform("Test2", matcher, notFound::add).newInstance();
        TestAssertions.count = 0;
        assertEquals(0, TestAssertions.count);
        callMethod(instance, "blockingMethod");
        assertEquals(1, TestAssertions.count);
    }

    @Test
    public void testStaticMethod()
            throws IOException, IllegalAccessException, NoSuchMethodException {
        Set<String> notFound = new HashSet<>();
        ImmutableMap<String, String> matcher =
                ImmutableMap.of(
                        "com.android.tools.checker.agent.TestClass.staticMethodThrows()V",
                        "com.android.tools.checker.TestAssertions#fail",
                        "com.android.tools.checker.agent.TestClass.staticMethodNop()V",
                        "com.android.tools.checker.TestAssertions#count");
        Class<?> newDefinedCLass = loadAndTransform("Test2", matcher, notFound::add);

        TestAssertions.count = 0;
        assertEquals(0, TestAssertions.count);
        callMethod(newDefinedCLass, null, "staticMethodNop", false);
        assertEquals(1, TestAssertions.count);
        try {
            callMethod(newDefinedCLass, null, "staticMethodThrows", false);
            fail("Method must throw an exception");
        } catch (RuntimeException e) {
            assertEquals("Fail", e.getMessage());
        }

        Assert.assertThat(
                notFound,
                CoreMatchers.hasItems(
                        "com.android.tools.checker.agent.TestClass.<init>()V",
                        "com.android.tools.checker.agent.TestClass.methodNop()V",
                        "com.android.tools.checker.agent.TestClass.privateMethodThrows()V",
                        "com.android.tools.checker.agent.TestClass.methodDoNotThrow()V",
                        "com.android.tools.checker.agent.TestClass.publicMethodThrows()V"));
    }
}
