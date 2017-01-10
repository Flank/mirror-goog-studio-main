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

import com.android.annotations.NonNull;
import com.android.testutils.classloader.SingleClassLoader;
import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.WildcardType;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.After;
import org.junit.Test;

/**
 * Test cases for {@link JvmWideVariable}.
 */
public class JvmWideVariableTest {

    @NonNull private JvmWideVariable<String> variable =
            new JvmWideVariable<>("group", "name", String.class, "Some text");

    @After
    public void tearDown() {
        variable.unregister();
    }

    @Test
    public void testRead() {
        assertThat(variable.get()).isEqualTo("Some text");
    }

    @Test
    public void testInitializeTwiceThenRead() {
        variable = new JvmWideVariable<>("group", "name", String.class, "Some other text");
        // Expect that the second default value is ignored
        assertThat(variable.get()).isEqualTo("Some text");
    }

    @Test
    public void testWriteThenRead() {
        variable.set("Some other text");
        assertThat(variable.get()).isEqualTo("Some other text");
    }

    @Test
    public void testWriteThenReadTwice() {
        variable.set("Some other text");
        assertThat(variable.get()).isEqualTo("Some other text");
        variable.set("Yet some other text");
        assertThat(variable.get()).isEqualTo("Yet some other text");
    }

    @Test
    public void testSameVariable() {
        variable.set("This text");

        JvmWideVariable<String> variable2 =
                new JvmWideVariable<>("group", "name", String.class, "That text");
        assertThat(variable.get()).isEqualTo("This text");
        assertThat(variable2.get()).isEqualTo("This text");

        variable2.set("That text");
        assertThat(variable.get()).isEqualTo("That text");
        assertThat(variable2.get()).isEqualTo("That text");
    }

    @Test
    public void testDifferentVariables() {
        variable.set("This text");

        JvmWideVariable<String> variable2 =
                new JvmWideVariable<>("group", "name2", String.class, "That text");
        assertThat(variable.get()).isEqualTo("This text");
        assertThat(variable2.get()).isEqualTo("That text");

        variable2.set("That text");
        assertThat(variable.get()).isEqualTo("This text");
        assertThat(variable2.get()).isEqualTo("That text");

        variable2.unregister();
    }

    @Test
    public void testNullValues() {
        JvmWideVariable<String> variable2 =
                new JvmWideVariable<>("group", "name2", String.class, null);
        assertThat(variable2.get()).isNull();

        variable2.set("Some text");
        assertThat(variable2.get()).isEqualTo("Some text");

        variable2.set(null);
        assertThat(variable2.get()).isNull();

        variable2.unregister();
    }

    @Test
    public void testVariables_WithSameGroupSameName_DifferentSimpleTypes() throws Exception {
        // Create another JVM-wide variable with the same group, same name, but different type,
        // expect failure
        try {
            new JvmWideVariable<>("group", "name", Integer.class, null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo(
                    String.format(
                            "Expected type %s but found type %s for JVM-wide variable %s:%s",
                            String.class, Integer.class, "group", "name"));
        }
    }

    @Test
    public void testVariables_WithSameGroupSameName_DifferentParameterizedTypes() throws Exception {
        // Create a JVM-wide variable with a ParameterizedType
        JvmWideVariable<List<String>> complexVariable =
                new JvmWideVariable<>("group", "foo", getParameterizedToken(String.class), null);

        // Create another JVM-wide variable with the same group, same name, but a different
        // ParameterizedType, expect failure
        try {
            new JvmWideVariable<>("group", "foo", getParameterizedToken(Integer.class), null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo(
                    String.format(
                            "Expected type %s but found type %s for JVM-wide variable %s:%s",
                            getParameterizedToken(String.class).getType(),
                            getParameterizedToken(Integer.class).getType(),
                            "group",
                            "foo"));
        }

        complexVariable.unregister();
    }

    @Test
    public void testVariables_WithSameGroupSameName_DifferentGenericArrayTypes() throws Exception {
        // Create a JVM-wide variable with a GenericArrayType
        JvmWideVariable<List<String>[]> complexVariable =
                new JvmWideVariable<>("group", "foo", getGenericArrayToken(String.class), null);

        // Create another JVM-wide variable with the same group, same name, but a different
        // GenericArrayType, expect failure
        try {
            new JvmWideVariable<>("group", "foo", getGenericArrayToken(Integer.class), null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo(
                    String.format(
                            "Expected type %s but found type %s for JVM-wide variable %s:%s",
                            getGenericArrayToken(String.class).getType(),
                            getGenericArrayToken(Integer.class).getType(),
                            "group",
                            "foo"));
        }

        complexVariable.unregister();
    }

    @Test
    public void testVariables_WithSameGroupSameName_DifferentUpperBoundWildcardTypes()
            throws Exception {
        // Create a JVM-wide variable with an upper-bound WildcardType
        JvmWideVariable<Class<? extends CharSequence>> complexVariable =
                new JvmWideVariable<>(
                        "group", "foo", getUpperBoundWildcardToken(CharSequence.class), null);

        // Create another JVM-wide variable with the same group, same name, but a different
        // upper-bound WildcardType, expect failure
        try {
            new JvmWideVariable<>("group", "foo", getUpperBoundWildcardToken(Number.class), null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo(
                    String.format(
                            "Expected type %s but found type %s for JVM-wide variable %s:%s",
                            getUpperBoundWildcardToken(CharSequence.class).getType(),
                            getUpperBoundWildcardToken(Number.class).getType(),
                            "group",
                            "foo"));
        }

        complexVariable.unregister();
    }

    @Test
    public void testVariables_WithSameGroupSameName_DifferentLowerBoundWildcardTypes()
            throws Exception {
        // Create a JVM-wide variable with a lower-bound WildcardType
        JvmWideVariable<Class<? super String>> complexVariable =
                new JvmWideVariable<>(
                        "group", "foo", getLowerBoundWildcardToken(String.class), null);

        // Create another JVM-wide variable with the same group, same name, but a different
        // lower-bound WildcardType, expect failure
        try {
            new JvmWideVariable<>("group", "foo", getLowerBoundWildcardToken(Integer.class), null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo(
                    String.format(
                            "Expected type %s but found type %s for JVM-wide variable %s:%s",
                            getLowerBoundWildcardToken(String.class).getType(),
                            getLowerBoundWildcardToken(Integer.class).getType(),
                            "group",
                            "foo"));
        }

        complexVariable.unregister();
    }

    @Test
    public void testVariables_WithBootstrapAndNonBootstrapClassLoaders_SimpleTypes()
            throws Exception {
        // Create a JVM-wide variable whose type is loaded by the bootstrap class loader, expect
        // success
        JvmWideVariable<String> fooVariable =
                new JvmWideVariable<>("group", "foo", String.class, null);
        fooVariable.unregister();

        // Create a JVM-wide variable whose type is loaded by the application (non-bootstrap) class
        // loader, expect failure
        try {
            new JvmWideVariable<>("group", "fooCounter", FooCounter.class, null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo(
                    String.format(
                            "Type %s used to define JVM-wide variable %s:%s must be loaded by the"
                                    + " bootstrap class loader but is loaded by %s",
                            FooCounter.class,
                            "group",
                            "fooCounter",
                            FooCounter.class.getClassLoader()));
        }

        // Create a JVM-wide variable whose type is loaded by a custom class loader, expect failure
        SingleClassLoader classLoader = new SingleClassLoader(FooCounter.class.getName());
        Class clazz = classLoader.load();
        try {
            new JvmWideVariable<>("group", "fooCounter", clazz, null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo(
                    String.format(
                            "Type %s used to define JVM-wide variable %s:%s must be loaded by the"
                                    + " bootstrap class loader but is loaded by %s",
                            clazz,
                            "group",
                            "fooCounter",
                            clazz.getClassLoader()));
        }
    }

    @Test
    public void testVariables_WithBootstrapAndNonBootstrapClassLoaders_ParameterizeTypes()
            throws Exception {
        // Create a JVM-wide variable whose type is a ParameterizeType, and its argument's type is
        // loaded by the bootstrap class loader, expect success
        JvmWideVariable<List<String>> fooVariable =
                new JvmWideVariable<>("group", "foo", getParameterizedToken(String.class), null);
        fooVariable.unregister();

        // Create a JVM-wide variable whose type is a ParameterizeType, and its argument's type is
        // loaded by the application (non-bootstrap) class loader, expect failure
        try {
            new JvmWideVariable<>(
                    "group", "fooCounter", getParameterizedToken(FooCounter.class), null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo(
                    String.format(
                            "Type %s used to define JVM-wide variable %s:%s must be loaded by the"
                                    + " bootstrap class loader but is loaded by %s",
                            FooCounter.class,
                            "group",
                            "fooCounter",
                            FooCounter.class.getClassLoader()));
        }
    }

    @Test
    public void testVariables_WithBootstrapAndNonBootstrapClassLoaders_GenericArrayTypes()
            throws Exception {
        // Create a JVM-wide variable whose type is a GenericArrayType, and its component type is
        // loaded by the bootstrap class loader, expect success
        JvmWideVariable<List<String>[]> fooVariable =
                new JvmWideVariable<>("group", "foo", getGenericArrayToken(String.class), null);
        fooVariable.unregister();

        // Create a JVM-wide variable whose type is a GenericArrayType, and its component type is
        // loaded by the application (non-bootstrap) class loader, expect failure
        try {
            new JvmWideVariable<>(
                    "group", "fooCounter", getGenericArrayToken(FooCounter.class), null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo(
                    String.format(
                            "Type %s used to define JVM-wide variable %s:%s must be loaded by the"
                                    + " bootstrap class loader but is loaded by %s",
                            FooCounter.class,
                            "group",
                            "fooCounter",
                            FooCounter.class.getClassLoader()));
        }
    }

    @Test
    public void testVariables_WithBootstrapAndNonBootstrapClassLoaders_UpperBoundWildcardTypes()
            throws Exception {
        // Create a JVM-wide variable whose type is an upper-bound WildcardType, and the upper-bound
        // type is loaded by the bootstrap class loader, expect success
        JvmWideVariable<Class<? extends CharSequence>> fooVariable =
                new JvmWideVariable<>(
                        "group", "foo", getUpperBoundWildcardToken(CharSequence.class), null);
        fooVariable.unregister();

        // Create a JVM-wide variable whose type is an upper-bound WildcardType, and the upper-bound
        // type is loaded by the application (non-bootstrap) class loader, expect failure
        try {
            new JvmWideVariable<>(
                    "group", "fooCounter", getUpperBoundWildcardToken(FooCounter.class), null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo(
                    String.format(
                            "Type %s used to define JVM-wide variable %s:%s must be loaded by the"
                                    + " bootstrap class loader but is loaded by %s",
                            FooCounter.class,
                            "group",
                            "fooCounter",
                            FooCounter.class.getClassLoader()));
        }
    }

    @Test
    public void testVariables_WithBootstrapAndNonBootstrapClassLoaders_LowerBoundWildcardTypes()
            throws Exception {
        // Create a JVM-wide variable whose type is an lower-bound WildcardType, and the lower-bound
        // type is loaded by the bootstrap class loader, expect success
        JvmWideVariable<Class<? super String>> fooVariable =
                new JvmWideVariable<>(
                        "group", "foo", getLowerBoundWildcardToken(String.class), null);
        fooVariable.unregister();

        // Create a JVM-wide variable whose type is a lower-bound WildcardType, and the lower-bound
        // type is loaded by the application (non-bootstrap) class loader, expect failure
        try {
            new JvmWideVariable<>(
                    "group", "fooCounter", getLowerBoundWildcardToken(FooCounter.class), null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo(
                    String.format(
                            "Type %s used to define JVM-wide variable %s:%s must be loaded by the"
                                    + " bootstrap class loader but is loaded by %s",
                            FooCounter.class,
                            "group",
                            "fooCounter",
                            FooCounter.class.getClassLoader()));
        }
    }

    @Test
    public void testDoSynchronized() throws ExecutionException {
        int result = variable.doCallableSynchronized(() -> 1);
        assertThat(result).isEqualTo(1);
    }

    @Test
    public void testDifferentClassLoaders() throws Exception {
        // Get a JVM-wide variable from a class loaded by the default class loader
        Integer counter = FooCounter.getCounterValue();

        // Load the same class with a custom class loader
        SingleClassLoader classLoader = new SingleClassLoader(FooCounter.class.getName());
        Class clazz = classLoader.load();
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
                new JvmWideVariable<>(
                        FooCounter.class.getName(),
                        "COUNT",
                        Integer.class,
                        new Integer(1));

        public FooCounter() {
        }

        public static Integer getCounterValue() {
            return COUNT.get();
        }
    }

    /**
     * Returns the {@link TypeToken} for a {@link List} of elements (e.g., {@code List<String>}).
     * The returned {@code TypeToken} captures a {@link ParameterizedType}.
     */
    @NonNull
    private static <E> TypeToken<List<E>> getParameterizedToken(@NonNull Class<E> elementClass) {
        return new TypeToken<List<E>>() {}
                .where(new TypeParameter<E>() {}, TypeToken.of(elementClass));
    }

    /**
     * Returns the {@link TypeToken} for an array of {@link List} (e.g., {@code List<String>[]}).
     * The returned {@code TypeToken} captures a {@link GenericArrayType}.
     */
    @NonNull
    private static <E> TypeToken<List<E>[]> getGenericArrayToken(@NonNull Class<E> elementClass) {
        return new TypeToken<List<E>[]>() {}
                .where(new TypeParameter<E>() {}, TypeToken.of(elementClass));
    }

    /**
     * Returns the {@link TypeToken} for a {@link Class} of upper-bound {@link WildcardType}. The
     * returned {@code TypeToken} captures a {@link ParameterizedType} of upper-bound {@link
     * WildcardType}.
     */
    @NonNull
    private static <T> TypeToken<Class<? extends T>> getUpperBoundWildcardToken(
            @NonNull Class<T> typeClass) {
        return new TypeToken<Class<? extends T>>() {}
                .where(new TypeParameter<T>() {}, TypeToken.of(typeClass));
    }

    /**
     * Returns the {@link TypeToken} for a {@link Class} of lower-bound {@link WildcardType}. The
     * returned {@code TypeToken} captures a {@link ParameterizedType} of lower-bound {@link
     * WildcardType}.
     */
    @NonNull
    private static <T> TypeToken<Class<? super T>> getLowerBoundWildcardToken(
            @NonNull Class<T> typeClass) {
        return new TypeToken<Class<? super T>>() {}
                .where(new TypeParameter<T>() {}, TypeToken.of(typeClass));
    }
}
