/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.ide.common.util;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.android.testutils.classloader.SingleClassLoader;
import com.google.common.base.Throwables;
import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.junit.Test;

/** Test cases for {@link JvmWideVariable}. */
@SuppressWarnings("ResultOfObjectAllocationIgnored")
public class JvmWideVariableTest {

    @Test
    public void testRead() {
        JvmWideVariable<String> variable =
                new JvmWideVariable<>(JvmWideVariableTest.class, "name", String.class, "Some text");
        assertThat(variable.get()).isEqualTo("Some text");
        variable.unregister();
    }

    @Test
    public void testInitializeTwiceThenRead() {
        new JvmWideVariable<>(JvmWideVariableTest.class, "name", String.class, "Some text");
        JvmWideVariable<String> variable =
                new JvmWideVariable<>(
                        JvmWideVariableTest.class, "name", String.class, "Some other text");

        // Expect that the second default value is ignored
        assertThat(variable.get()).isEqualTo("Some text");

        variable.unregister();
    }

    @Test
    public void testWriteThenRead() {
        JvmWideVariable<String> variable =
                new JvmWideVariable<>(JvmWideVariableTest.class, "name", String.class, "Some text");
        variable.set("Some other text");

        assertThat(variable.get()).isEqualTo("Some other text");

        variable.unregister();
    }

    @Test
    public void testWriteThenReadTwice() {
        JvmWideVariable<String> variable =
                new JvmWideVariable<>(JvmWideVariableTest.class, "name", String.class, "Some text");

        variable.set("Some other text");
        assertThat(variable.get()).isEqualTo("Some other text");

        variable.set("Yet some other text");
        assertThat(variable.get()).isEqualTo("Yet some other text");

        variable.unregister();
    }

    @Test
    public void testSameVariable() {
        JvmWideVariable<String> variable =
                new JvmWideVariable<>(JvmWideVariableTest.class, "name", String.class, "This text");
        JvmWideVariable<String> variable2 =
                new JvmWideVariable<>(JvmWideVariableTest.class, "name", String.class, "That text");
        assertThat(variable.get()).isEqualTo("This text");
        assertThat(variable2.get()).isEqualTo("This text");

        variable2.set("Other text");
        assertThat(variable.get()).isEqualTo("Other text");
        assertThat(variable2.get()).isEqualTo("Other text");

        variable.unregister();
    }

    @Test
    public void testDifferentVariables() {
        JvmWideVariable<String> variable =
                new JvmWideVariable<>(JvmWideVariableTest.class, "name", String.class, "This text");
        JvmWideVariable<String> variable2 =
                new JvmWideVariable<>(
                        JvmWideVariableTest.class, "name2", String.class, "That text");
        assertThat(variable.get()).isEqualTo("This text");
        assertThat(variable2.get()).isEqualTo("That text");

        variable2.set("Other text");
        assertThat(variable.get()).isEqualTo("This text");
        assertThat(variable2.get()).isEqualTo("Other text");

        variable.unregister();
        variable2.unregister();
    }

    @Test
    public void testNullValues() {
        JvmWideVariable<String> variable =
                new JvmWideVariable<>(JvmWideVariableTest.class, "name", String.class, null);
        assertThat(variable.get()).isNull();

        variable.set("Some text");
        assertThat(variable.get()).isEqualTo("Some text");

        variable.set(null);
        assertThat(variable.get()).isNull();

        variable.unregister();
    }

    @Test
    public void testDefaultValueSupplier() {
        JvmWideVariable<String> variable =
                new JvmWideVariable<>(JvmWideVariableTest.class, "name", String.class, "Some text");

        new JvmWideVariable<>(
                JvmWideVariableTest.class,
                "name",
                TypeToken.of(String.class),
                () -> {
                    fail("This should not be executed");
                    return null;
                });

        JvmWideVariable<String> variable2 =
                new JvmWideVariable<>(
                        JvmWideVariableTest.class,
                        "name2",
                        TypeToken.of(String.class),
                        () -> "This should be executed");
        assertThat(variable2.get()).isEqualTo("This should be executed");

        variable.unregister();
        variable2.unregister();
    }

    @Test
    public void testGetFullName() {
        // Test valid full name
        JvmWideVariable.getFullName("group", "name", "tag");

        // Test invalid full names
        try {
            JvmWideVariable.getFullName("group with space", "name", "tag");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }
        try {
            JvmWideVariable.getFullName("group", "name with :", "tag");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }
        try {
            JvmWideVariable.getFullName("group", "name with =", "tag");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }
        try {
            JvmWideVariable.getFullName("group", "name", "tag with ,");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }
        try {
            JvmWideVariable.getFullName("group", "name", "");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    @Test
    public void testCollectComponentClasses_SimpleType() {
        Collection<Class<?>> classes =
                JvmWideVariable.collectComponentClasses(new TypeToken<String>() {}.getType());
        assertThat(classes).containsExactly(String.class);
    }

    @Test
    public void testCollectComponentClasses_ParameterizedType() {
        Collection<Class<?>> classes =
                JvmWideVariable.collectComponentClasses(new TypeToken<List<String>>() {}.getType());
        assertThat(classes).containsExactly(List.class, String.class);
    }

    @Test
    public void testCollectComponentClasses_GenericArrayType() {
        Collection<Class<?>> classes =
                JvmWideVariable.collectComponentClasses(
                        new TypeToken<List<String>[]>() {}.getType());
        assertThat(classes).containsExactly(List.class, String.class);
    }

    @Test
    public void testCollectComponentClasses_UpperBoundWildcardType() {
        Collection<Class<?>> classes =
                JvmWideVariable.collectComponentClasses(
                        new TypeToken<Class<? extends CharSequence>>() {}.getType());
        assertThat(classes).containsExactly(Class.class, CharSequence.class);
    }

    @Test
    public void testCollectComponentClasses_LowerBoundWildcardType() {
        Collection<Class<?>> classes =
                JvmWideVariable.collectComponentClasses(
                        new TypeToken<Class<? super String>>() {}.getType());
        assertThat(classes).containsExactly(Class.class, String.class, Object.class);
    }

    @Test
    public void testVariables_WithBootstrapAndNonBootstrapClassLoaders_SimpleTypes()
            throws Exception {
        // Create a JVM-wide variable whose type is loaded by the bootstrap class loader, expect
        // success
        JvmWideVariable<String> variable =
                new JvmWideVariable<>(JvmWideVariableTest.class, "foo", String.class, null);

        // Create a JVM-wide variable whose type is loaded by the application (non-bootstrap) class
        // loader, expect failure
        try {
            new JvmWideVariable<>(JvmWideVariableTest.class, "fooCounter", FooCounter.class, null);
            fail("Expected VerifyException");
        } catch (VerifyException e) {
            assertThat(e.getMessage())
                    .isEqualTo(
                            String.format(
                                    "Type %s used to define JVM-wide variable %s must be loaded by"
                                            + " the bootstrap class loader but is loaded by %s",
                                    FooCounter.class,
                                    JvmWideVariable.getFullName(
                                            JvmWideVariableTest.class.getName(),
                                            "fooCounter",
                                            FooCounter.class.getName()),
                                    FooCounter.class.getClassLoader()));
        }

        // Create a JVM-wide variable whose type is loaded by a custom class loader, expect failure
        SingleClassLoader classLoader = new SingleClassLoader(FooCounter.class.getName());
        Class<?> clazz = classLoader.load();
        try {
            new JvmWideVariable<>(JvmWideVariableTest.class, "fooCounter", clazz, null);
            fail("Expected VerifyException");
        } catch (VerifyException e) {
            assertThat(e.getMessage())
                    .isEqualTo(
                            String.format(
                                    "Type %s used to define JVM-wide variable %s must be loaded by"
                                            + " the bootstrap class loader but is loaded by %s",
                                    clazz,
                                    JvmWideVariable.getFullName(
                                            JvmWideVariableTest.class.getName(),
                                            "fooCounter",
                                            FooCounter.class.getName()),
                                    clazz.getClassLoader()));
        }

        variable.unregister();
    }

    @Test
    public void testVariables_WithBootstrapAndNonBootstrapClassLoaders_ComplexTypes()
            throws Exception {
        // Create a JVM-wide variable whose type is a ParameterizeType, and its argument's type is
        // loaded by the bootstrap class loader, expect success
        JvmWideVariable<List<String>> variable =
                new JvmWideVariable<>(
                        JvmWideVariableTest.class,
                        "foo",
                        new TypeToken<List<String>>() {},
                        () -> null);

        // Create a JVM-wide variable whose type is a ParameterizeType, and its argument's type is
        // loaded by the application (non-bootstrap) class loader, expect failure
        try {
            new JvmWideVariable<>(
                    JvmWideVariableTest.class,
                    "fooCounter",
                    new TypeToken<List<FooCounter>>() {},
                    () -> null);
            fail("Expected VerifyException");
        } catch (VerifyException e) {
            String tag =
                    JvmWideVariable.collectComponentClasses(
                                    new TypeToken<List<FooCounter>>() {}.getType())
                            .stream()
                            .map(Class::getName)
                            .collect(Collectors.joining("-"));
            assertThat(e.getMessage())
                    .isEqualTo(
                            String.format(
                                    "Type %s used to define JVM-wide variable %s must be loaded by"
                                            + " the bootstrap class loader but is loaded by %s",
                                    JvmWideVariableTest.FooCounter.class,
                                    JvmWideVariable.getFullName(
                                            JvmWideVariableTest.class.getName(), "fooCounter", tag),
                                    JvmWideVariableTest.FooCounter.class.getClassLoader()));
        }

        variable.unregister();
    }

    @Test
    public void testVariables_WithSameGroupNameTag_DifferentSimpleTypes() throws Exception {
        JvmWideVariable<String> variable =
                new JvmWideVariable<>(
                        JvmWideVariableTest.class.getName(),
                        "name",
                        "tag",
                        TypeToken.of(String.class),
                        () -> "Some text");

        // Create another JVM-wide variable with the same group, same name, same tag, but different
        // type, expect failure
        JvmWideVariable<Integer> variable2 =
                new JvmWideVariable<>(
                        JvmWideVariableTest.class.getName(),
                        "name",
                        "tag",
                        TypeToken.of(Integer.class),
                        () -> 1);
        try {
            @SuppressWarnings("unused")
            Integer value = variable2.get();
            fail("Expected ClassCastException");
        } catch (ClassCastException e) {
            assertThat(e.getMessage())
                    .isEqualTo("java.lang.String cannot be cast to java.lang.Integer");
        }

        variable.unregister();
    }

    @Test
    public void testVariables_WithSameGroupNameTag_DifferentComplexTypes() throws Exception {
        // Create a JVM-wide variable with a ParameterizedType
        JvmWideVariable<List<String>> variable =
                new JvmWideVariable<>(
                        JvmWideVariableTest.class.getName(),
                        "name",
                        "tag",
                        new TypeToken<List<String>>() {},
                        () -> ImmutableList.of("Some text"));

        // Create another JVM-wide variable with the same group, same name, same tag, but different
        // ParameterizedType, expect failure
        JvmWideVariable<List<Integer>> variable2 =
                new JvmWideVariable<>(
                        JvmWideVariableTest.class.getName(),
                        "name",
                        "tag",
                        new TypeToken<List<Integer>>() {},
                        () -> null);
        try {
            @SuppressWarnings({"unused", "ConstantConditions"})
            Integer value = variable2.get().get(0);
            fail("Expected ClassCastException");
        } catch (ClassCastException e) {
            assertThat(e.getMessage())
                    .isEqualTo("java.lang.String cannot be cast to java.lang.Integer");
        }

        variable.unregister();
    }

    @Test
    public void testExecuteCallableSynchronously() throws ExecutionException {
        JvmWideVariable<String> variable =
                new JvmWideVariable<>(JvmWideVariableTest.class, "name", String.class, "Some text");
        Integer result = variable.executeCallableSynchronously(() -> 1);

        assertThat(result).isEqualTo(1);

        variable.unregister();
    }

    @Test
    public void testExecuteCallableSynchronously_ThrowingExecutionException() {
        JvmWideVariable<String> variable =
                new JvmWideVariable<>(JvmWideVariableTest.class, "name", String.class, "Some text");
        try {
            variable.executeCallableSynchronously(
                    () -> {
                        throw new IllegalStateException("Some exception");
                    });
            fail("Expected ExecutionException");
        } catch (ExecutionException e) {
            assertThat(Throwables.getRootCause(e)).isInstanceOf(IllegalStateException.class);
            assertThat(Throwables.getRootCause(e)).hasMessage("Some exception");
        }
        variable.unregister();
    }

    @Test
    public void testDifferentClassLoaders() throws Exception {
        // Get a JVM-wide variable from a class loaded by the default class loader
        Integer counter = FooCounter.getCounterValue();

        // Load the same class with a custom class loader
        SingleClassLoader classLoader = new SingleClassLoader(FooCounter.class.getName());
        Class<?> clazz = classLoader.load();
        assertThat(clazz.getClassLoader()).isNotEqualTo(FooCounter.class.getClassLoader());

        // Get the JVM-wide variable from the same class loaded by the custom class loader
        Method method = clazz.getMethod("getCounterValue");
        method.setAccessible(true);
        Integer counter2 = (Integer) method.invoke(null);

        // Assert that they are the same instance
        assertThat(counter2).isSameAs(counter);
    }

    /**
     * Sample class containing a {@link JvmWideVariable} static field.
     */
    private static class FooCounter {

        private static final JvmWideVariable<Integer> COUNT =
                new JvmWideVariable<>(FooCounter.class, "COUNT", Integer.class, 1);

        public FooCounter() {
        }

        public static Integer getCounterValue() {
            return COUNT.get();
        }
    }

    @Test
    public void testUnregister() throws Exception {
        JvmWideVariable<String> variable =
                new JvmWideVariable<>(JvmWideVariableTest.class, "name", String.class, "Some text");
        JvmWideVariable<String> sameVariable =
                new JvmWideVariable<>(JvmWideVariableTest.class, "name", String.class, "Some text");
        JvmWideVariable<String> variable2 =
                new JvmWideVariable<>(
                        JvmWideVariableTest.class, "name2", String.class, "Some other text");

        // Unregister the JVM-wide variable, expect that access to it afterwards will fail
        variable.unregister();
        try {
            variable.get();
            fail("Expected VerifyException");
        } catch (VerifyException e) {
            assertThat(e.getMessage()).contains("has already been unregistered");
        }

        // Check that access to the same JVM-wide variable from another JvmWideVariable instance
        // will also fail
        try {
            sameVariable.get();
            fail("Expected VerifyException");
        } catch (VerifyException e) {
            assertThat(e.getMessage()).contains("has already been unregistered");
        }

        // Check that access to the second variable will still succeed
        assertThat(variable2.get()).isEqualTo("Some other text");

        // Let another JvmWideVariable instance re-register the JVM-wide variable
        JvmWideVariable<String> anotherSameVariable =
                new JvmWideVariable<>(JvmWideVariableTest.class, "name", String.class, "Some text");

        // The first variable was unregistered, it will not be used even if the underlying JVM-wide
        // variable is re-registered
        try {
            variable.get();
            fail("Expected VerifyException");
        } catch (VerifyException e) {
            assertThat(e.getMessage()).contains("has already been unregistered");
        }

        // The second variable was never unregistered, so it can still be used if the underlying
        // JVM-wide variable is re-registered
        assertThat(sameVariable.get()).isEqualTo("Some text");

        // Access to the other variables should succeed as normal
        assertThat(anotherSameVariable.get()).isEqualTo("Some text");
        assertThat(variable2.get()).isEqualTo("Some other text");

        sameVariable.unregister();
        variable2.unregister();
    }
}
