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

import com.android.testutils.classloader.MultiClassLoader;
import com.android.testutils.classloader.SingleClassLoader;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Ignore;
import org.junit.Test;

/** Test cases for {@link BuildSessionVariable}. */
@SuppressWarnings("ResultOfObjectAllocationIgnored")
public class BuildSessionVariableTest {

    @Test
    public void testRead() {
        BuildSessionVariable<String> variable =
                new BuildSessionVariable<>("group", "name", String.class, "Some text");
        assertThat(variable.get()).isEqualTo("Some text");
        variable.unregister();
    }

    @Test
    public void testInitializeTwiceThenRead() {
        BuildSessionVariable<String> variable =
                new BuildSessionVariable<>("group", "name", String.class, "Some text");
        BuildSessionVariable<String> variable2 =
                new BuildSessionVariable<>("group", "name", String.class, "Some other text");

        // Expect that the second default value is ignored
        assertThat(variable2.get()).isEqualTo("Some text");

        variable.unregister();
        variable2.unregister();
    }

    @Test
    public void testWriteThenRead() {
        BuildSessionVariable<String> variable =
                new BuildSessionVariable<>("group", "name", String.class, "Some text");
        variable.set("Some other text");

        assertThat(variable.get()).isEqualTo("Some other text");

        variable.unregister();
    }

    @Test
    public void testWriteThenReadTwice() {
        BuildSessionVariable<String> variable =
                new BuildSessionVariable<>("group", "name", String.class, "Some text");

        variable.set("Some other text");
        assertThat(variable.get()).isEqualTo("Some other text");

        variable.set("Yet some other text");
        assertThat(variable.get()).isEqualTo("Yet some other text");

        variable.unregister();
    }

    @Test
    public void testSameVariable() {
        BuildSessionVariable<String> variable =
                new BuildSessionVariable<>("group", "name", String.class, "This text");
        BuildSessionVariable<String> variable2 =
                new BuildSessionVariable<>("group", "name", String.class, "That text");
        assertThat(variable.get()).isEqualTo("This text");
        assertThat(variable2.get()).isEqualTo("This text");

        variable2.set("Other text");
        assertThat(variable.get()).isEqualTo("Other text");
        assertThat(variable2.get()).isEqualTo("Other text");

        variable.unregister();
        variable2.unregister();
    }

    @Test
    public void testDifferentVariables() {
        BuildSessionVariable<String> variable =
                new BuildSessionVariable<>("group", "name", String.class, "This text");
        BuildSessionVariable<String> variable2 =
                new BuildSessionVariable<>("group", "name2", String.class, "That text");
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
        BuildSessionVariable<String> variable =
                new BuildSessionVariable<>("group", "name", String.class, null);
        assertThat(variable.get()).isNull();

        variable.set("Some text");
        assertThat(variable.get()).isEqualTo("Some text");

        variable.set(null);
        assertThat(variable.get()).isNull();

        variable.unregister();
    }

    @Test
    public void testDefaultValueSupplier() {
        BuildSessionVariable<String> variable =
                new BuildSessionVariable<>("group", "name", String.class, "Some text");

        BuildSessionVariable<String> sameVariable =
                new BuildSessionVariable<>(
                        "group",
                        "name",
                        TypeToken.of(String.class),
                        () -> {
                            fail("This should not be executed");
                            return null;
                        });

        BuildSessionVariable<String> variable2 =
                new BuildSessionVariable<>(
                        "group",
                        "name2",
                        TypeToken.of(String.class),
                        () -> "This should be executed");
        assertThat(variable2.get()).isEqualTo("This should be executed");

        variable.unregister();
        sameVariable.unregister();
        variable2.unregister();
    }

    @Test
    public void testVariables_WithSameGroupSameName_DifferentSimpleTypes() throws Exception {
        BuildSessionVariable<String> variable =
                new BuildSessionVariable<>("group", "name", String.class, "Some text");

        // Create another build session variable with the same group, same name, but different type,
        // expect failure (currently we fail when the variable is accessed, not when it is created)
        BuildSessionVariable<Integer> variable2 =
                new BuildSessionVariable<>("group", "name", Integer.class, 1);
        try {
            @SuppressWarnings("unused")
            Integer value = variable2.get();
            fail("Expected ClassCastException");
        } catch (ClassCastException e) {
            assertThat(e.getMessage())
                    .isEqualTo("java.lang.String cannot be cast to java.lang.Integer");
        }

        variable.unregister();
        variable2.unregister();
    }

    @Test
    public void testVariables_WithSameGroupSameName_DifferentComplexTypes() throws Exception {
        // Create a build session variable with a ParameterizedType
        BuildSessionVariable<List<String>> variable =
                new BuildSessionVariable<>(
                        "group",
                        "name",
                        new TypeToken<List<String>>() {},
                        () -> ImmutableList.of("Some text"));

        // Create another build session variable with the same group, same name, but a different
        // ParameterizedType, expect failure (currently we fail when the variable is accessed, not
        // when it is created)
        BuildSessionVariable<List<Integer>> variable2 =
                new BuildSessionVariable<>(
                        "group", "name", new TypeToken<List<Integer>>() {}, () -> null);
        try {
            @SuppressWarnings({"unused", "ConstantConditions"})
            Integer value = variable2.get().get(0);
            fail("Expected ClassCastException");
        } catch (ClassCastException e) {
            assertThat(e.getMessage())
                    .isEqualTo("java.lang.String cannot be cast to java.lang.Integer");
        }

        variable.unregister();
        variable2.unregister();
    }

    @Test
    public void testVariables_WithBootstrapAndNonBootstrapClassLoaders_SimpleTypes()
            throws Exception {
        // Create a build session variable whose type is loaded by the bootstrap class loader,
        // expect success
        BuildSessionVariable<String> variable =
                new BuildSessionVariable<>("group", "foo", String.class, null);

        // Create a build session variable whose type is loaded by the application (non-bootstrap)
        // class loader, expect failure
        try {
            new BuildSessionVariable<>("group", "fooCounter", FooCounter.class, null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage())
                    .isEqualTo(
                            String.format(
                                    "Type %s used to define build session variable %s:%s must be loaded by"
                                            + " the bootstrap class loader but is loaded by %s",
                                    FooCounter.class,
                                    "group",
                                    "fooCounter",
                                    FooCounter.class.getClassLoader()));
        }

        // Create a build session variable whose type is loaded by a custom class loader, expect failure
        SingleClassLoader classLoader = new SingleClassLoader(FooCounter.class.getName());
        Class<?> clazz = classLoader.load();
        try {
            new BuildSessionVariable<>("group", "fooCounter", clazz, null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage())
                    .isEqualTo(
                            String.format(
                                    "Type %s used to define build session variable %s:%s must be loaded by"
                                            + " the bootstrap class loader but is loaded by %s",
                                    clazz, "group", "fooCounter", clazz.getClassLoader()));
        }

        variable.unregister();
    }

    @Test
    public void testVariables_WithBootstrapAndNonBootstrapClassLoaders_ComplexTypes()
            throws Exception {
        // Create a build session variable whose type is a ParameterizeType, and its argument's type
        // is loaded by the bootstrap class loader, expect success
        BuildSessionVariable<List<String>> variable =
                new BuildSessionVariable<>(
                        "group", "foo", new TypeToken<List<String>>() {}, () -> null);

        // Create a build session variable whose type is a ParameterizeType, and its argument's type
        // is loaded by the application (non-bootstrap) class loader, expect failure
        try {
            new BuildSessionVariable<>(
                    "group",
                    "fooCounter",
                    new TypeToken<List<BuildSessionVariableTest.FooCounter>>() {},
                    () -> null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage())
                    .isEqualTo(
                            String.format(
                                    "Type %s used to define build session variable %s:%s must be loaded by the"
                                            + " bootstrap class loader but is loaded by %s",
                                    BuildSessionVariableTest.FooCounter.class,
                                    "group",
                                    "fooCounter",
                                    BuildSessionVariableTest.FooCounter.class.getClassLoader()));
        }

        variable.unregister();
    }

    @Test
    public void testExecuteCallableSynchronously() throws ExecutionException {
        BuildSessionVariable<String> variable =
                new BuildSessionVariable<>("group", "name", String.class, "Some text");
        Integer result = variable.executeCallableSynchronously(() -> 1);

        assertThat(result).isEqualTo(1);

        variable.unregister();
    }

    @Test
    public void testExecuteCallableSynchronously_ThrowingExecutionException() {
        BuildSessionVariable<String> variable =
                new BuildSessionVariable<>("group", "name", String.class, "Some text");
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
        // Get a build session variable from a class loaded by the default class loader
        Integer counter = FooCounter.getCounterValue();

        // Load the same class with a custom class loader
        SingleClassLoader classLoader = new SingleClassLoader(FooCounter.class.getName());
        Class<?> clazz = classLoader.load();
        assertThat(clazz.getClassLoader()).isNotEqualTo(FooCounter.class.getClassLoader());

        // Get the build session variable from the same class loaded by the custom class loader
        Method method = clazz.getMethod("getCounterValue");
        method.setAccessible(true);
        Integer counter2 = (Integer) method.invoke(null);

        // Assert that they are the same instance
        assertThat(counter2).isSameAs(counter);
    }

    /** Sample class containing a {@link BuildSessionVariable} static field. */
    private static class FooCounter {

        private static final BuildSessionVariable<Integer> COUNT =
                new BuildSessionVariable<>(FooCounter.class.getName(), "COUNT", Integer.class, 1);

        public FooCounter() {}

        public static Integer getCounterValue() {
            return COUNT.get();
        }
    }

    /*
     * The tests above are similar to the tests in JvmWideVariableTest. The tests below are specific
     * to BuildSessionVariable, i.e. we want to test that its life time is limited to a build.
     */

    @Test
    @Ignore("issuetracker.google.com/issues/62878541")
    public void testLifeTime() {
        // Count the number of times the variable is initialized
        AtomicInteger count = new AtomicInteger(0);
        BuildSessionVariable<String> variable =
                new BuildSessionVariable<>(
                        "group",
                        "name",
                        TypeToken.of(String.class),
                        () -> {
                            count.getAndIncrement();
                            return "Some text";
                        });
        assertThat(count.get()).isEqualTo(1);

        // Modify the variable's value
        variable.set("Other text");

        // Finish the build, expect that access/modification to the variable is not allowed
        BuildSessionVariable.buildFinished();
        try {
            variable.get();
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            // Expected
        }
        try {
            variable.set("Other text");
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            // Expected
        }
        assertThat(count.get()).isEqualTo(1);

        // Start a new build, expect that the variable is re-initialized and access/modification to
        // it is allowed
        BuildSessionVariable.buildStarted();
        assertThat(variable.get()).isEqualTo("Some text");
        assertThat(count.get()).isEqualTo(2);

        variable.set("Other text");
        assertThat(variable.get()).isEqualTo("Other text");
    }

    @Test
    @Ignore("issuetracker.google.com/issues/62878541")
    public void testLifeTime_DifferentClassLoaders() throws Exception {
        // Create a build session variable when the BuildSessionVariable class is loaded by the
        // default class loader. Also count the number of times the variable is initialized.
        AtomicInteger count = new AtomicInteger(0);
        BuildSessionVariable<String> variable =
                new BuildSessionVariable<>(
                        "group",
                        "name",
                        TypeToken.of(String.class),
                        () -> {
                            count.getAndIncrement();
                            return "Some text";
                        });
        assertThat(count.get()).isEqualTo(1);

        // Load the BuildSessionVariable class with a custom class loader
        MultiClassLoader classLoader =
                new MultiClassLoader(
                        ImmutableList.of(
                                BuildSessionVariable.class.getName(),
                                JvmWideVariable.class.getName()));
        Class<?> clazz = classLoader.load().get(0);
        assertThat(clazz.getClassLoader()).isNotEqualTo(FooCounter.class.getClassLoader());

        // Get the same build session variable from the BuildSessionVariable class loaded by the
        // custom class loader
        Method buildStartedMethod = clazz.getMethod("buildStarted");
        buildStartedMethod.invoke(null);
        Constructor constructor =
                clazz.getConstructor(String.class, String.class, Class.class, Object.class);
        Object variable2 = constructor.newInstance("group", "name", String.class, "Other text");
        assertThat(count.get()).isEqualTo(1);

        // Check that the two BuildSessionVariable instances both access the same underlying build
        // session variable
        Method getMethod = clazz.getMethod("get");
        assertThat(variable2).isNotSameAs(variable);
        assertThat(getMethod.invoke(variable2)).isEqualTo("Some text");

        // Check that changes to the variable from one class loader can be seen from the other
        variable.set("Other text");
        assertThat(getMethod.invoke(variable2)).isEqualTo("Other text");

        // Finish the build from both class loaders
        BuildSessionVariable.buildFinished();
        Method buildFinishedMethod = clazz.getMethod("buildFinished");
        buildFinishedMethod.invoke(null);

        // Start a new build, expect that the variable is re-initialized
        BuildSessionVariable.buildStarted();
        buildStartedMethod.invoke(null);
        assertThat(variable.get()).isEqualTo("Some text");
        assertThat(getMethod.invoke(variable2)).isEqualTo("Some text");
        assertThat(count.get()).isEqualTo(2);

        // Clean up this test
        buildFinishedMethod.invoke(null);
        Method clearBuildSessionVariableSetMethod =
                clazz.getDeclaredMethod("clearBuildSessionVariableSet");
        clearBuildSessionVariableSetMethod.setAccessible(true);
        clearBuildSessionVariableSetMethod.invoke(null);
    }
}
