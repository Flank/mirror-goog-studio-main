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
import com.google.common.reflect.TypeToken;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.management.InstanceNotFoundException;
import javax.management.ObjectName;
import org.junit.After;
import org.junit.Test;

/** Test cases for {@link JvmWideVariable}. */
@SuppressWarnings("ResultOfObjectAllocationIgnored")
public class JvmWideVariableTest {

    @After
    public void tearDown() {
        JvmWideVariable.unregisterAll();
    }

    @Test
    public void testRead() {
        JvmWideVariable<String> variable =
                new JvmWideVariable<>("group", "name", String.class, "Some text");
        assertThat(variable.get()).isEqualTo("Some text");
    }

    @Test
    public void testInitializeTwiceThenRead() {
        new JvmWideVariable<>("group", "name", String.class, "Some text");
        JvmWideVariable<String> variable =
                new JvmWideVariable<>("group", "name", String.class, "Some other text");

        // Expect that the second default value is ignored
        assertThat(variable.get()).isEqualTo("Some text");
    }

    @Test
    public void testWriteThenRead() {
        JvmWideVariable<String> variable =
                new JvmWideVariable<>("group", "name", String.class, "Some text");
        variable.set("Some other text");

        assertThat(variable.get()).isEqualTo("Some other text");
    }

    @Test
    public void testWriteThenReadTwice() {
        JvmWideVariable<String> variable =
                new JvmWideVariable<>("group", "name", String.class, "Some text");

        variable.set("Some other text");
        assertThat(variable.get()).isEqualTo("Some other text");

        variable.set("Yet some other text");
        assertThat(variable.get()).isEqualTo("Yet some other text");
    }

    @Test
    public void testSameVariable() {
        JvmWideVariable<String> variable =
                new JvmWideVariable<>("group", "name", String.class, "This text");
        JvmWideVariable<String> variable2 =
                new JvmWideVariable<>("group", "name", String.class, "That text");
        assertThat(variable.get()).isEqualTo("This text");
        assertThat(variable2.get()).isEqualTo("This text");

        variable2.set("Other text");
        assertThat(variable.get()).isEqualTo("Other text");
        assertThat(variable2.get()).isEqualTo("Other text");
    }

    @Test
    public void testDifferentVariables() {
        JvmWideVariable<String> variable =
                new JvmWideVariable<>("group", "name", String.class, "This text");
        JvmWideVariable<String> variable2 =
                new JvmWideVariable<>("group", "name2", String.class, "That text");
        assertThat(variable.get()).isEqualTo("This text");
        assertThat(variable2.get()).isEqualTo("That text");

        variable2.set("Other text");
        assertThat(variable.get()).isEqualTo("This text");
        assertThat(variable2.get()).isEqualTo("Other text");
    }

    @Test
    public void testNullValues() {
        JvmWideVariable<String> variable =
                new JvmWideVariable<>("group", "name2", String.class, null);
        assertThat(variable.get()).isNull();

        variable.set("Some text");
        assertThat(variable.get()).isEqualTo("Some text");

        variable.set(null);
        assertThat(variable.get()).isNull();
    }

    @Test
    public void testDefaultValueSupplier() {
        new JvmWideVariable<>("group", "name", String.class, "Some text");

        new JvmWideVariable<>(
                "group",
                "name",
                TypeToken.of(String.class),
                () -> {
                    fail("This should not be executed");
                    return null;
                });

        JvmWideVariable<String> variable2 =
                new JvmWideVariable<>(
                        "group",
                        "name2",
                        TypeToken.of(String.class),
                        () -> "This should be executed");
        assertThat(variable2.get()).isEqualTo("This should be executed");
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
    public void testVariables_WithSameGroupSameName_DifferentSimpleTypes() throws Exception {
        new JvmWideVariable<>("group", "name", String.class, "Some text");

        // Create another JVM-wide variable with the same group, same name, but different type,
        // expect failure
        try {
            new JvmWideVariable<>("group", "name", Integer.class, 1);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage())
                    .isEqualTo(
                            String.format(
                                    "Expected type %s but found type %s"
                                            + " for JVM-wide variable %s:%s",
                                    String.class, Integer.class, "group", "name"));
        }
    }

    @Test
    public void testVariables_WithSameGroupSameName_DifferentComplexTypes() throws Exception {
        // Create a JVM-wide variable with a ParameterizedType
        new JvmWideVariable<>("group", "name", new TypeToken<List<String>>() {}, () -> null);

        // Create another JVM-wide variable with the same group, same name, but a different
        // ParameterizedType, expect failure
        try {
            new JvmWideVariable<>("group", "name", new TypeToken<List<Integer>>() {}, () -> null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage())
                    .isEqualTo(
                            String.format(
                                    "Expected type %s but found type %s"
                                            + " for JVM-wide variable %s:%s",
                                    new TypeToken<List<String>>() {},
                                    new TypeToken<List<Integer>>() {},
                                    "group",
                                    "name"));
        }
    }

    @Test
    public void testVariables_WithBootstrapAndNonBootstrapClassLoaders_SimpleTypes()
            throws Exception {
        // Create a JVM-wide variable whose type is loaded by the bootstrap class loader, expect
        // success
        new JvmWideVariable<>("group", "foo", String.class, null);

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
        Class<?> clazz = classLoader.load();
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
    public void testVariables_WithBootstrapAndNonBootstrapClassLoaders_ComplexTypes()
            throws Exception {
        // Create a JVM-wide variable whose type is a ParameterizeType, and its argument's type is
        // loaded by the bootstrap class loader, expect success
        new JvmWideVariable<>("group", "foo", new TypeToken<List<String>>() {}, () -> null);

        // Create a JVM-wide variable whose type is a ParameterizeType, and its argument's type is
        // loaded by the application (non-bootstrap) class loader, expect failure
        try {
            new JvmWideVariable<>(
                    "group", "fooCounter", new TypeToken<List<FooCounter>>() {}, () -> null);
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
    public void testExecuteCallableSynchronously() throws ExecutionException {
        JvmWideVariable<String> variable =
                new JvmWideVariable<>("group", "name", String.class, "Some text");
        Integer result = variable.executeCallableSynchronously(() -> 1);
        assertThat(result).isEqualTo(1);
    }

    @Test
    public void testExecuteCallableSynchronously_ThrowingExecutionException() {
        JvmWideVariable<String> variable =
                new JvmWideVariable<>("group", "name", String.class, "Some text");
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
                new JvmWideVariable<>(FooCounter.class.getName(), "COUNT", Integer.class, 1);

        public FooCounter() {
        }

        public static Integer getCounterValue() {
            return COUNT.get();
        }
    }

    @Test
    public void testUnregister() throws Exception {
        JvmWideVariable<String> variable =
                new JvmWideVariable<>("group", "name", String.class, "Some text");
        JvmWideVariable<String> variable2 =
                new JvmWideVariable<>("group", "name2", String.class, "Some other text");

        // Unregister the first JVM-wide variable, expect that access to it afterwards will fail,
        // while access to the second variable will still succeed
        variable.unregister();
        try {
            variable.get();
        } catch (RuntimeException e) {
            assertThat(e.getCause()).isInstanceOf(InstanceNotFoundException.class);
        }
        assertThat(variable2.get()).isEqualTo("Some other text");
    }

    @Test
    public void testUnregisterAll() throws Exception {
        JvmWideVariable<String> variable =
                new JvmWideVariable<>("group", "name", String.class, "Some text");
        JvmWideVariable<String> variable2 =
                new JvmWideVariable<>("group", "name2", String.class, "Some other text");

        // Unregister all JVM-wide variables, expect that access to them afterwards will fail
        JvmWideVariable.unregisterAll();
        try {
            variable.get();
        } catch (RuntimeException e) {
            assertThat(e.getCause()).isInstanceOf(InstanceNotFoundException.class);
        }
        try {
            variable2.get();
        } catch (RuntimeException e) {
            assertThat(e.getCause()).isInstanceOf(InstanceNotFoundException.class);
        }

        // Register a dummy object that is not a JVM-wide variable with the MBean server
        ObjectName dummyObjectName =
                new ObjectName(
                        JvmWideVariableTest.class.getSimpleName()
                                + ":type=dummy,group=dummy,name=dummy");
        Object dummy = new Dummy();
        ManagementFactory.getPlatformMBeanServer().registerMBean(dummy, dummyObjectName);

        // Unregister all JVM-wide variables, expect that the dummy object is still registered
        JvmWideVariable.unregisterAll();
        assertThat(ManagementFactory.getPlatformMBeanServer().queryNames(dummyObjectName, null))
                .contains(dummyObjectName);
    }

    /** Dummy class used by testUnregisterAll() test. */
    private static final class Dummy implements DummyMBean {}

    /** Dummy interface used by testUnregisterAll() test. */
    public interface DummyMBean {}
}
