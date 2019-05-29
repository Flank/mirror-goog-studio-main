package com.android.tools.checker.agent;

import static com.android.tools.checker.agent.AgentTestUtils.callMethod;
import static com.android.tools.checker.agent.AgentTestUtils.loadAndTransform;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.fail;

import com.android.tools.checker.TestAssertions;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;

public class InstrumentTest {

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
    public void testMethodAnnotations()
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
    public void testClassAnnotations()
            throws IOException, IllegalAccessException, InstantiationException,
                    NoSuchMethodException {
        Set<String> notFound = new HashSet<>();
        ImmutableMap<String, String> matcher =
                ImmutableMap.of(
                        "@com.android.tools.checker.AnotherTestAnnotation",
                        "com.android.tools.checker.TestAssertions#count",
                        "@com.android.tools.checker.BlockingTest",
                        "com.android.tools.checker.TestAssertions#count");
        Object instance = loadAndTransform("Test2", matcher, notFound::add).newInstance();
        TestAssertions.count = 0;
        assertEquals(0, TestAssertions.count);
        callMethod(instance, "methodNop");
        // methodNop is also annotated with @AnotherTestAnnotation but we only intercept it once
        assertEquals(1, TestAssertions.count);
        callMethod(instance, "publicMethodThrows");
        // publicMethodThrows calls privateMethodThrows, which is also annotated.
        assertEquals(3, TestAssertions.count);
        callMethod(instance, "staticMethodNop");
        assertEquals(4, TestAssertions.count);
        // Blocking method is annotated with both @AnotherTestAnnotation (from the class) and
        // @BlockingTest (from the method itself), so we intercept it twice.
        callMethod(instance, "blockingMethod");
        assertEquals(6, TestAssertions.count);
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
