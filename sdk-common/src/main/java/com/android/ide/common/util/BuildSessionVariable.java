/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.google.common.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * A proxy object that can access a build session variable. A build session variable is similar to a
 * JVM-wide variable (a variable that can be accessible from everywhere in the JVM, even when the
 * JVM contains classes that are loaded multiple times by different class loaders); see {@link
 * JvmWideVariable}. However, it has one important extension: Its life time is limited to a build.
 *
 * <p>Specifically, a build session variable is initialized with a default value when the variable
 * is first created and will be recreated and re-initialized (via the given default value supplier)
 * at the start of every subsequent build within the same Gradle daemon.
 *
 * <p>This class addresses two potential issues when using static variables.
 *
 * <ol>
 *   <li>Life time: Static variables persist across builds since the same Gradle daemon can be used
 *       to execute multiple builds. This is usually unintended, as we want to limit the variables'
 *       life time to a build. For example, if a build fails or is canceled by Ctrl-C, the states of
 *       static variables may be corrupted and may break the next build. Even if the build completes
 *       normally, the states of static variables are not "fresh" (e.g., a counter may have
 *       increased and the next build's counter will not start from 0).
 *   <li>Scope: Static variables are unique per class loader of the class that loads them. If the
 *       plugin is loaded multiple times by different class loaders, static variables are no longer
 *       unique. This can be addressed by using {@link JvmWideVariable}; however, {@link
 *       JvmWideVariable} does not handle the issue with the variables' life time, whereas {@link
 *       BuildSessionVariable} can handle both issues.
 * </ol>
 *
 * <p>This class requires that the {@link #buildStarted()} method be called every time the plugin is
 * applied and the {@link #buildFinished()} method be called at the end of a build. If the plugin is
 * applied multiple times, these methods may be called more than once in a build. This is currently
 * done in {@code com.android.build.gradle.internal.BuildSessionHelper}.
 *
 * <p>A {@link BuildSessionVariable} instance should typically be assigned to some static field of a
 * class, not to an instance field or a local variable within a method, since the actual build
 * session variable will not automatically be garbage-collected when it is no longer used, as one
 * would have expected from an instance field or a local variable. Additionally, this class
 * maintains a set of all {@link BuildSessionVariable} instances that it creates (within the current
 * class loader), so creating instance fields or local variables of type {@link
 * BuildSessionVariable} would increase the size of this set with every new build, which is not what
 * we want.
 *
 * <p>The usage of this class is as follows. Suppose we previously used a static variable:
 *
 * <pre>{@code
 * public final class Counter {
 *   public static Integer COUNT = 0;
 *   public static synchronized void increaseCounter() {
 *     COUNT++;
 *   }
 * }
 * }</pre>
 *
 * <p>We can then convert the static variable into a build session variable:
 *
 * <pre>{@code
 * public final class Counter {
 *   public static final BuildSessionVariable<Integer> COUNT =
 *       new BuildSessionVariable<>("my.package.Counter", "COUNT", Integer.class, 0);
 *     public static void increaseCounter() {
 *       COUNT.executeRunnableSynchronously(() -> {
 *         COUNT.set(COUNT.get() + 1);
 *       });
 *     }
 * }
 * }</pre>
 *
 * <p>Note that in the above example, {@code Counter.COUNT} is still a static variable of {@code
 * Counter}, with the previously discussed limitations. What has changed is that {@code
 * Counter.COUNT} is now able to access a build session variable of type {@code AtomicInteger}. (The
 * type of the build session variable after the conversion is the same as the type of the static
 * variable before the conversion.)
 *
 * <p>Where the context is clear, it might be easier to refer to variables of type {@code
 * BuildSessionVariable} as build session variables, although strictly speaking they are not, but
 * through them we can access build session variables.
 *
 * <p>This class is thread-safe.
 *
 * @param <T> The type of the build session variable. Must be loaded by a single class loader.
 * @see JvmWideVariable
 */
public final class BuildSessionVariable<T> {

    /** Whether the current build has started and is not yet finished. */
    private static boolean buildStarted = false;

    /**
     * The JVM-wide variable table, which is a map from the variable's full name to the actual build
     * session variable (an {@link AtomicReference} holding the variable's value).
     */
    @Nullable
    private static JvmWideVariable<ConcurrentMap<String, AtomicReference<Object>>>
            jvmWideVariableTable = null;

    /**
     * Whether the JVM-wide variable table is created by this class, or by this same class but
     * loaded by a different class loader.
     */
    @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
    @Nullable
    private static Boolean variableTableCreatedByThisClass = null;

    /**
     * The set of all {@link BuildSessionVariable} instances created by this class (within the
     * current class loader).
     */
    @NonNull
    private static Set<BuildSessionVariable> buildSessionVariableSet = Sets.newConcurrentHashSet();

    /** The actual build session variable. */
    @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
    @Nullable
    private AtomicReference<T> variable;

    /** The full name of the build session variable. */
    @NonNull private final String fullName;

    /** The supplier that produces the default value of the build session variable. */
    @NonNull private final Supplier<T> defaultValueSupplier;

    /**
     * Creates a {@code BuildSessionVariable} instance that can access a build session variable.
     *
     * <p>The group, name, type, and default value of the build session variable resemble the (fully
     * qualified) name of the defining class of a static variable, the name of the static variable,
     * its type, and its default value.
     *
     * <p>This method creates the build session variable and initializes it with the default value
     * if the variable does not yet exist.
     *
     * <p>Unlike JVM-wide variables (see {@link JvmWideVariable}), the build session variable will
     * be recreated and re-initialized (via the given value supplier) at the start of every
     * subsequent build within the same Gradle daemon.
     *
     * <p>A build session variable is uniquely defined by its group and name. The client should
     * provide the same variable with the same type and default value. If the client provides a
     * different type for a variable that already exists, it will result in a runtime casting
     * exception. However, if the client provides a different default value for a variable that
     * already exists, this method will simply ignore that value and will not throw an exception.
     *
     * <p>The type {@code T} of the variable must be loaded by a single class loader. Currently,
     * this method requires the single class loader to be the bootstrap class loader.
     *
     * <p>The client needs to explicitly pass type {@code T} via a {@link Class} or a {@link
     * TypeToken} instance. This method takes a ({@code TypeToken} as it is more general (it can
     * capture complex types such as {@code Map<K, V>}). If the type is simple (can be represented
     * fully by a {@code Class} instance), the client can use the {@link
     * #BuildSessionVariable(String, String, Class, Object)} method instead.
     *
     * @param group the group of the variable
     * @param name the name of the variable
     * @param typeToken the type of the variable
     * @param defaultValueSupplier the supplier that produces the default value of the variable. It
     *     is called only when the variable is first created. The supplied value can be null.
     * @see JvmWideVariable#JvmWideVariable(String, String, TypeToken, Supplier)
     */
    public BuildSessionVariable(
            @NonNull String group,
            @NonNull String name,
            @NonNull TypeToken<T> typeToken,
            @NonNull Supplier<T> defaultValueSupplier) {
        // Collect all classes that are involved in defining the build session variable's type and
        // check that they are all loaded by the bootstrap class loader
        Type type = typeToken.getType();
        for (Class<?> clazz : JvmWideVariable.collectComponentClasses(type)) {
            Preconditions.checkArgument(
                    clazz.getClassLoader() == null,
                    "Type %s used to define build session variable %s:%s must be loaded"
                            + " by the bootstrap class loader but is loaded by %s",
                    clazz,
                    group,
                    name,
                    clazz.getClassLoader());
        }

        // When the build starts, the buildStarted() method should have already been called.
        // However, when running unit tests, that method may not be called since there is no actual
        // build. To handle that case, we call that method here.
        if (!buildStarted) {
            buildStarted();
        }

        // The variable table should have been created in the buildStarted() method
        Preconditions.checkNotNull(jvmWideVariableTable);
        ConcurrentMap<String, AtomicReference<Object>> variableTable = jvmWideVariableTable.get();
        Preconditions.checkNotNull(variableTable);

        // Get the build session variable, creating it first if it has not been created
        this.fullName = group + ":" + name;
        //noinspection unchecked
        this.variable =
                (AtomicReference<T>)
                        variableTable.computeIfAbsent(
                                fullName,
                                (any) -> new AtomicReference<>(defaultValueSupplier.get()));
        this.defaultValueSupplier = defaultValueSupplier;

        buildSessionVariableSet.add(this);
    }

    /**
     * Creates a {@code BuildSessionVariable} instance that can access a build session variable.
     * This method will call {@link #BuildSessionVariable(String, String, TypeToken, Supplier)}. See
     * the javadoc of that method for more details.
     *
     * @see #BuildSessionVariable(String, String, TypeToken, Supplier)
     */
    public BuildSessionVariable(
            @NonNull String group,
            @NonNull String name,
            @NonNull Class<T> type,
            @Nullable T defaultValue) {
        this(group, name, TypeToken.of(type), () -> defaultValue);
    }

    /**
     * Creates the variable table and initializes (or re-initializes) build session variables with
     * default values when a build starts.
     */
    public static synchronized void buildStarted() {
        // This method is called every time the plugin is applied (each time, the plugin's classes
        // may be either reused or reloaded). If this class is loaded multiple times, we want to
        // execute this method fully once per class loader and per build.
        if (buildStarted) {
            return;
        }
        buildStarted = true;

        Preconditions.checkState(jvmWideVariableTable == null);
        Preconditions.checkState(variableTableCreatedByThisClass == null);

        // Get the variable table, creating it first if it has not been created
        variableTableCreatedByThisClass = false;
        jvmWideVariableTable =
                new JvmWideVariable<>(
                        BuildSessionVariable.class.getName(),
                        "jvmWideVariableTable",
                        new TypeToken<ConcurrentMap<String, AtomicReference<Object>>>() {},
                        () -> {
                            variableTableCreatedByThisClass = true;
                            return new ConcurrentHashMap<>();
                        });

        // If this is the first build, at this point there are no build session variables yet
        // (buildSessionVariableSet is empty), and we are done with this method. For any subsequent
        // build, we need to recreate the build session variables and re-initialize their values.
        ConcurrentMap<String, AtomicReference<Object>> variableTable = jvmWideVariableTable.get();
        Preconditions.checkNotNull(variableTable);
        for (BuildSessionVariable buildSessionVariable : buildSessionVariableSet) {
            Preconditions.checkState(buildSessionVariable.variable == null);
            buildSessionVariable.variable =
                    variableTable.computeIfAbsent(
                            buildSessionVariable.fullName,
                            (any) ->
                                    new AtomicReference<>(
                                            buildSessionVariable.defaultValueSupplier.get()));
        }
    }

    /** Releases the variable table and build session variables when a build is finished. */
    public static synchronized void buildFinished() {
        // Similar to the buildStarted() method, we want to execute this method fully once per
        // class loader and per build
        if (!buildStarted) {
            return;
        }
        buildStarted = false;

        Preconditions.checkNotNull(jvmWideVariableTable);
        Preconditions.checkNotNull(variableTableCreatedByThisClass);

        // Release the variable table by un-registering the JVM-wide variable and un-linking any
        // references to it
        if (variableTableCreatedByThisClass) {
            jvmWideVariableTable.unregister();
        }
        jvmWideVariableTable = null;
        variableTableCreatedByThisClass = null;

        // Also un-link any references to the actual build session variables
        for (BuildSessionVariable buildSessionVariable : buildSessionVariableSet) {
            Preconditions.checkNotNull(buildSessionVariable.variable);
            buildSessionVariable.variable = null;
        }
    }

    /** Returns the current value of this build session variable. */
    @Nullable
    public T get() {
        Preconditions.checkNotNull(variable);
        return variable.get();
    }

    /** Sets a value to this build session variable. */
    public void set(@Nullable T value) {
        Preconditions.checkNotNull(variable);
        variable.set(value);
    }

    /**
     * Executes the given action, where the execution is synchronized on the build session variable.
     * The variable is unchanged during a build even when it is assigned with a new value.
     *
     * <p>This method is used to replace a static synchronized method operating on a static variable
     * when converting the static variable into a build session variable. (See the javadoc of {@link
     * BuildSessionVariable}.)
     *
     * @throws ExecutionException if an exception occurred during the execution of the action
     */
    @Nullable
    public <V> V executeCallableSynchronously(@NonNull Callable<V> action)
            throws ExecutionException {
        Preconditions.checkNotNull(variable);
        // The variable (an AtomicReference) is unchanged during a build (although the value that it
        // contains may change)
        //noinspection SynchronizeOnNonFinalField
        synchronized (variable) {
            try {
                return action.call();
            } catch (Exception e) {
                throw new ExecutionException(e);
            }
        }
    }

    /**
     * Unregisters this {@code BuildSessionVariable} instance.
     *
     * <p>WARNING: This method must be used only by tests.
     */
    @VisibleForTesting
    void unregister() {
        Preconditions.checkNotNull(jvmWideVariableTable);
        ConcurrentMap<String, AtomicReference<Object>> variableTable = jvmWideVariableTable.get();
        Preconditions.checkNotNull(variableTable);
        // The actual build session variable may have already been unregistered via another
        // BuildSessionVariable instance
        if (variableTable.containsKey(fullName)) {
            variableTable.remove(fullName);
        }

        Preconditions.checkNotNull(variable);
        variable = null;

        Preconditions.checkState(buildSessionVariableSet.contains(this));
        buildSessionVariableSet.remove(this);
    }
}
