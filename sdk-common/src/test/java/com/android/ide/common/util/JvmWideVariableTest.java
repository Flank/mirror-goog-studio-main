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
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.junit.After;
import org.junit.Test;

/**
 * Test cases for {@link JvmWideVariable}.
 */
public class JvmWideVariableTest {

    @NonNull private JvmWideVariable<String> variable =
            new JvmWideVariable<>("group", "name", TypeToken.of(String.class), "Some text");

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
        variable =
                new JvmWideVariable<>(
                        "group", "name", TypeToken.of(String.class), "Some other text");
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
                new JvmWideVariable<>("group", "name", TypeToken.of(String.class), "That text");
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
                new JvmWideVariable<>("group", "name2", TypeToken.of(String.class), "That text");
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
                new JvmWideVariable<>("group", "name2", TypeToken.of(String.class), null);
        assertThat(variable2.get()).isNull();

        variable2.set("Some text");
        assertThat(variable2.get()).isEqualTo("Some text");

        variable2.set(null);
        assertThat(variable2.get()).isNull();

        variable2.unregister();
    }

    @Test
    public void testVariables_WithSameGroupSameNameDifferentTypes() throws Exception {
        // Create another JVM-wide variable with the same group, same name, but different type,
        // expect failure
        try {
            new JvmWideVariable<>("group", "name", TypeToken.of(Integer.class), new Integer(1));
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo(
                    String.format(
                            "Expected type %s but found type %s for JVM-wide variable %s:%s",
                            TypeToken.of(String.class),
                            TypeToken.of(Integer.class),
                            "group",
                            "name"));
        }
    }

    @Test
    public void testVariables_WithSameGroupSameNameDifferentComplexTypes() throws Exception {
        // Create a JVM-wide variable with a complex type
        JvmWideVariable<Map<String, Integer>> complexVariable =
                new JvmWideVariable<>("group", "foo", mapToken(String.class, Integer.class), null);

        // Create another JVM-wide variable with the same group, same name, but different complex
        // type, expect failure
        try {
            new JvmWideVariable<>("group", "foo", mapToken(Integer.class, String.class), null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo(
                    String.format(
                            "Expected type %s but found type %s for JVM-wide variable %s:%s",
                            mapToken(String.class, Integer.class),
                            mapToken(Integer.class, String.class),
                            "group",
                            "foo"));
        }

        complexVariable.unregister();
    }

    @Test
    public void testVariables_WithNonBootstrapClassLoaders() throws Exception {
        // Create a JVM-wide variable for an instance of a class loaded by the bootstrap class
        // loader, expect success
        JvmWideVariable<Integer> fooVariable =
                new JvmWideVariable<>("group", "foo", TypeToken.of(Integer.class), new Integer(1));
        fooVariable.unregister();

        // Create a JVM-wide variable for an instance of a class loaded by the application
        // (non-bootstrap) class loader, expect failure
        FooCounter fooCounter1 = new FooCounter();
        try {
            new JvmWideVariable<>(
                    "group", "fooCounter1", TypeToken.of(FooCounter.class), fooCounter1);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo(
                    String.format(
                            "Type %s of JVM-wide variable %s:%s must be loaded by the bootstrap"
                                    + " class loader but is loaded by %s",
                            fooCounter1.getClass(),
                            "group",
                            "fooCounter1",
                            fooCounter1.getClass().getClassLoader()));
        }

        // Create an instance of the same class loaded by a custom class loader
        SingleClassLoader classLoader = new SingleClassLoader(FooCounter.class.getName());
        Class clazz = classLoader.load();
        Constructor constructor  = clazz.getConstructor();
        constructor.setAccessible(true);
        Object fooCounter2 = constructor.newInstance();

        // Create a JVM-wide variable for that instance, expect failure
        try {
            new JvmWideVariable<>(
                    "group", "fooCounter2", TypeToken.of(clazz), fooCounter2);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo(
                    String.format(
                            "Type %s of JVM-wide variable %s:%s must be loaded by the bootstrap"
                                    + " class loader but is loaded by %s",
                            fooCounter2.getClass(),
                            "group",
                            "fooCounter2",
                            fooCounter2.getClass().getClassLoader()));
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

    private static class FooCounter {

        private static final JvmWideVariable<Integer> COUNT =
                new JvmWideVariable<>(
                        FooCounter.class.getName(),
                        "COUNT",
                        TypeToken.of(Integer.class),
                        new Integer(1));

        public FooCounter() {
        }

        public static Integer getCounterValue() {
            return COUNT.get();
        }
    }

    /**
     * Returns a {@link TypeToken} for the {@link Map} type.
     */
    private static <K, V> TypeToken<Map<K, V>> mapToken(Class<K> keyClass, Class<V> valueClass) {
        return new TypeToken<Map<K, V>>() {}
                .where(new TypeParameter<K>() {}, TypeToken.of(keyClass))
                .where(new TypeParameter<V>() {}, TypeToken.of(valueClass));
    }
}
