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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.TypeToken;
import java.lang.management.ManagementFactory;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import javax.management.Attribute;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.management.ReflectionException;

/**
 * A proxy object that can access a JVM-wide variable. A JVM-wide variable is a variable that can be
 * accessible from everywhere in the JVM, even when the JVM contains classes that are loaded
 * multiple times by different class loaders. It is required that the type (class) of a JVM-wide
 * variable be loaded by a single class loader.
 *
 * <p>This class addresses the limitation of static variables in the presence of multiple class
 * loaders. It is not uncommon to assume that a static variable is unique and a public static
 * variable is accessible across the JVM. This assumption is correct as long as the defining class
 * of the static variable is loaded by only one class loader. If the defining class is loaded
 * multiple times by different class loaders, the JVM considers them as different classes, and this
 * assumption will break (i.e., the JVM might contain multiple instances of supposedly the same
 * static variable).
 *
 * <p>JVM-wide variables, on the other hand, allow a class loaded by different class loaders to
 * still reference the same variable. That is, changes to a JVM-wide variable made from a class
 * loaded by one class loader can be seen from the same class loaded by a different class loader.
 *
 * <p>The usage of this class is as follows. Suppose we previously used a static variable:
 * <pre>{@code
 * public final class Counter {
 *   public static final AtomicInteger COUNT = new AtomicInteger(0);
 * }
 * }</pre>
 *
 * <p>We can then convert the static variable into a JVM-wide variable:
 * <pre>{@code
 * public final class Counter {
 *    public static final JvmWideVariable<AtomicInteger> COUNT =
 *      new JvmWideVariable<>(
 *        "my.package.Counter", "COUNT", TypeToken.of(AtomicInteger.class), new AtomicInteger(0));
 * }
 * }</pre>
 *
 * <p>Note that in the above example, {@code Counter.COUNT} is still a static variable of {@code
 * Counter}, with the previously discussed limitation (not only the {@code Counter} class but even
 * the {@code JvmWideVariable} class itself might be loaded multiple times by different class
 * loaders). What has changed is that {@code Counter.COUNT} is now able to access a JVM-wide
 * variable of type {@code AtomicInteger}.
 *
 * <p>It may be helpful to think of {@code JvmWideVariable} as an object wrapper/converter: When
 * converting a static variable, whatever type we would use for the variable, we should use the same
 * type again for the JVM-wide variable wrapped by a {@code JvmWideVariable} instance.

 * <p>Where the context is clear, it might be easier to refer to variables of type {@code
 * JvmWideVariable} as JVM-wide variables, although strictly speaking they are not, but through them
 * we can access JVM-wide variables.
 *
 * <p>Also note that the type of a JVM-wide variable must be loaded by a single class loader. In the
 * above example, {@code Counter.COUNT} can access a JVM-wide variable as its type, {@code
 * AtomicInteger}, belongs to Java’s core classes and should never be loaded by a custom class
 * loader. If a JVM-wide variable’s type is loaded multiple times by different class loaders, it
 * might introduce some runtime casting exception as they are essentially different types.
 * (Therefore, using {@code JvmWideVariable} is probably not yet a complete solution to the
 * singleton design pattern being broken in the presence of multiple class loaders.)
 *
 * <p>Since a JVM-wide variable is by definition a shared variable, the client of this class needs
 * to provide proper synchronization when accessing a JVM-wide variable outside of this class, for
 * example by using thread-safe types such as {@link java.util.concurrent.atomic.AtomicInteger} and
 * {@link java.util.concurrent.ConcurrentMap}, using (implicit or explicit) locks where the locks
 * need to work across class loaders, or using the {@link #doCallableSynchronized(Callable)} method
 * and the like provided by this class.
 *
 * <p>For example, suppose we have a static variable of a non-thread-safe type (e.g., {@link
 * Integer}) and we use a synchronized block when modifying the variable:
 * <pre>{@code
 * public final class Counter {
 *   public static Integer COUNT = 0;
 *   public static synchronized void increaseCounter() {
 *     COUNT++;
 *   }
 * }
 * }</pre>
 *
 * <p>Then, the converted JVM-wide implementation can be as follows:
 * <pre>{@code
 * public final class Counter {
 *   public static final JvmWideVariable<Integer> COUNT =
 *     new JvmWideVariable<>("my.package.Counter", "COUNT", TypeToken.of(AtomicInteger.class), 0);
 *     public static void increaseCounter() {
 *       doRunnableSynchronized(() -> {
 *         COUNT++;
 *       });
 *     }
 * }
 * }</pre>
 *
 * <p>This class is thread-safe.
 *
 * @param <T> The type of the JVM-wide variable. Must be loaded by a single class loader.
 */
public final class JvmWideVariable<T> {

    @NonNull private final String group;
    @NonNull private final String name;

    // The MBeanServer below is a JVM-wide singleton object (it is the same instance even if
    // JvmWideVariable might be loaded by different class loaders) and therefore can be used for
    // synchronization within this class.
    @NonNull private static final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
    @NonNull private final ObjectName objectName;

    /**
     * Creates a {@code JvmWideVariable} instance that can access a JVM-wide variable.
     *
     * <p>The group, name, type, and default value of the JVM-wide variable resemble the (fully
     * qualified) name of the defining class of a static variable, the name of the static variable,
     * its type, and its default value.
     *
     * <p>This method creates the JVM-wide variable and initializes it with the default value if the
     * variable does not yet exist.
     *
     * <p>A JVM-wide variable is uniquely defined by its group and name. The client should provide
     * the same variable with the same type and default value. If the client provides a different
     * type for a variable that already exists, this method will throw an exception. However, if the
     * client provides a different default value for a variable that already exists, this method
     * will simply ignore that value and will not throw an exception.
     *
     * <p>The type {@code T} of the variable must be loaded by a single class loader. Currently,
     * this method requires the single class loader to be the bootstrap class loader.
     *
     * <p>Since the generic type {@code T} does not exist at run time, the client needs to
     * explicitly pass that type via a {@link Class} or a {@link TypeToken} instance. This method
     * takes a ({@code TypeToken} as it is more general (it supports capturing complex types such as
     * {@code Map<K, V>}). If the given type is simple (can be represented fully by a {@code Class}
     * instance), the client can use the {@link #JvmWideVariable(String, String, Class, Object)}
     * method instead.
     *
     * @param group the group of the variable
     * @param name the name of the variable
     * @param typeToken the type of the variable
     * @param defaultValue the default value of the variable, can be null
     */
    public JvmWideVariable(
            @NonNull String group,
            @NonNull String name,
            @NonNull TypeToken<T> typeToken,
            @Nullable T defaultValue) {
        // Collect all classes that are involved in defining the JVM-wide variable's type
        Type type = typeToken.getType();
        ImmutableSet.Builder<Class<?>> builder = ImmutableSet.builder();
        collectComponentClasses(type, builder);
        Set<Class<?>> classes = builder.build();

        for (Class<?> clazz : classes) {
            Preconditions.checkArgument(
                    clazz.getClassLoader() == null,
                    "Type %s used to define JVM-wide variable %s:%s must be loaded by the bootstrap"
                            + " class loader but is loaded by %s",
                    clazz, group, name, clazz.getClassLoader());
        }

        this.group = group;
        this.name = name;

        try {
            this.objectName =
                    new ObjectName(
                            ValueWrapper.class.getPackage().getName()
                                    + ":type=" + ValueWrapper.class.getSimpleName()
                                    + ",group=" + group
                                    + ",name=" + name);
        } catch (MalformedObjectNameException e) {
            throw new RuntimeException(e);
        }

        // Create and initialize the JVM-wide variable if it does not yet exist.
        //
        // Concurrency concern: Although server.isRegistered() and server.registerMBean() are
        // thread-safe individually, we synchronize both of them together so that the if-block can
        // run atomically. Note that several other places in this class also use the shared server
        // variable without being guarded by "synchronized (server)" and can interfere with this
        // synchronized block. However, as long as the unregister() method is used correctly (called
        // lastly in a test method; see its javadoc), those places will always run with a registered
        // variable. Therefore, they can at most run concurrently with the condition check of the if
        // statement below but can never run concurrently with the then-block of the if statement,
        // which makes this synchronized block safe.
        boolean variableExists = true;
        synchronized (server) {
            if (!server.isRegistered(objectName)) {
                variableExists = false;
                ValueWrapper objectWrapper = new ValueWrapper();
                objectWrapper.setValue(defaultValue);
                objectWrapper.setType(type);
                try {
                    server.registerMBean(objectWrapper, objectName);
                } catch (InstanceAlreadyExistsException | MBeanRegistrationException
                        | NotCompliantMBeanException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        // Check if the type is consistent.
        // We don't need to wrap this code in a synchronized block as server.getAttribute() is
        // thread-safe.
        if (variableExists) {
            Type existingType;
            try {
                // The cast below should be safe, as Type should be loaded by the bootstrap class
                // loader
                existingType =
                        (Type) server.getAttribute(objectName, ValueWrapperMBean.TYPE_PROPERTY);
            } catch (MBeanException | AttributeNotFoundException | InstanceNotFoundException
                    | ReflectionException e) {
                throw new RuntimeException(e);
            }
            Preconditions.checkArgument(
                    existingType.equals(type),
                    "Expected type %s but found type %s for JVM-wide variable %s:%s",
                    existingType, type, group, name);
        }
    }

    /**
     * Creates a {@code JvmWideVariable} instance that can access a JVM-wide variable. This method
     * will call {@link #JvmWideVariable(String, String, TypeToken, Object)}. See the javadoc of
     * that method for more details.
     *
     * @see #JvmWideVariable(String, String, TypeToken, Object)
     */
    public JvmWideVariable(
            @NonNull String group,
            @NonNull String name,
            @NonNull Class<T> type,
            @Nullable T defaultValue) {
        this(group, name, TypeToken.of(type), defaultValue);
    }

    /**
     * Collects all classes that are involved in defining a type.
     */
    private void collectComponentClasses(
            @NonNull Type type, @NonNull ImmutableSet.Builder<Class<?>> builder) {
        if (type instanceof Class<?>) {
            builder.add((Class<?>) type);
        } else if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            collectComponentClasses(parameterizedType.getRawType(), builder);
            if (parameterizedType.getOwnerType() != null) {
                collectComponentClasses(parameterizedType.getOwnerType(), builder);
            }
            for (Type componentType : parameterizedType.getActualTypeArguments()) {
                collectComponentClasses(componentType, builder);
            }
        } else if (type instanceof GenericArrayType) {
            collectComponentClasses(((GenericArrayType) type).getGenericComponentType(), builder);
        } else if (type instanceof WildcardType) {
            WildcardType wildcardType = (WildcardType) type;
            for (Type componentType : wildcardType.getLowerBounds()) {
                collectComponentClasses(componentType, builder);
            }
            for (Type componentType : wildcardType.getUpperBounds()) {
                collectComponentClasses(componentType, builder);
            }
        } else {
            throw new IllegalArgumentException(
                    "Type " + type + " is not yet supported by the JvmWideVariable class");
        }
    }

    @NonNull
    public String getGroup() {
        return group;
    }

    @NonNull
    public String getName() {
        return name;
    }

    /**
     * Returns the current value of this JVM-wide variable.
     */
    @Nullable
    public T get() {
        // We don't need to wrap this code in a synchronized block as server.getAttribute() is
        // thread-safe.
        try {
            // The cast below should be safe, as we already have the preconditions in the
            // constructor to prevent a casting exception here.
            return (T) server.getAttribute(objectName, ValueWrapperMBean.VALUE_PROPERTY);
        } catch (MBeanException | AttributeNotFoundException | InstanceNotFoundException
                | ReflectionException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sets a value to this JVM-wide variable.
     */
    public void set(@Nullable T value) {
        // We don't need to wrap this code in a synchronized block as server.setAttribute() is
        // thread-safe.
        try {
            server.setAttribute(objectName, new Attribute(ValueWrapperMBean.VALUE_PROPERTY, value));
        } catch (InstanceNotFoundException | AttributeNotFoundException
                | InvalidAttributeValueException | MBeanException | ReflectionException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Unregisters this JVM-wide variable, as if it was never created. This method should be used
     * only in tests to reset the MBeanServer's state at the very end of a test method so that the
     * next test method can run on a fresh state. Using this method outside a test will break the
     * thread safety of this class.
     */
    @VisibleForTesting
    void unregister() {
        // We don't need to wrap this code in a synchronized block as server.unregisterMBean()
        // is thread-safe.
        try {
            server.unregisterMBean(objectName);
        } catch (InstanceNotFoundException | MBeanRegistrationException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Executes the given action, where the execution is synchronized on the JVM-wide variable (an
     * MBean). The MBean is unchanged even when the variable is assigned with a new value.
     *
     * <p>This method is used to replace a static synchronized method operating on a static variable
     * when converting the static variable into a JVM-wide variable. (See the javadoc of {@link
     * JvmWideVariable}.)
     *
     * @throws ExecutionException if an exception occurred during the execution of the action
     */
    public <V> V doCallableSynchronized(Callable<V> action) throws ExecutionException {
        Object mBean;
        try {
            // Get the MBean that represents the JVM-wide variable
            mBean = server.getAttribute(objectName, ValueWrapperMBean.THIS_INSTANCE_PROPERTY);
        } catch (MBeanException | AttributeNotFoundException | InstanceNotFoundException
                | ReflectionException e) {
            throw new RuntimeException(e);
        }
        synchronized (mBean) {
            try {
                return action.call();
            } catch (Exception e) {
                throw new ExecutionException(e);
            }
        }
    }

    /**
     * Executes the given action, where the execution is synchronized on the JVM-wide variable.
     *
     * @see #doCallableSynchronized(Callable)
     */
    public <V> V doSupplierSynchronized(Supplier<V> action) {
        try {
            return doCallableSynchronized(() -> action.get());
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Executes the given action, where the execution is synchronized on the JVM-wide variable.
     *
     * @see #doCallableSynchronized(Callable)
     */
    public void doRunnableSynchronized(Runnable action) {
        doSupplierSynchronized(() -> {
            action.run();
            return null;
        });
    }

    /**
     * The MBean that represents a JVM-wide variable and contains its value and type.
     */
    private static final class ValueWrapper<T> implements ValueWrapperMBean<T> {

        @Nullable private T value;

        @NonNull private Type type;

        @Nullable
        @Override
        public T getValue() {
            return this.value;
        }

        @Override
        public void setValue(@Nullable T value) {
            this.value = value;
        }

        @NonNull
        @Override
        public Type getType() {
            return this.type;
        }

        @Override
        public void setType(@NonNull Type type) {
            this.type = type;
        }

        @Override
        public ValueWrapperMBean<T> getThisInstance() {
            return this;
        }
    }

    /**
     * The MBean interface, as required by a standard MBean implementation. The implementing class
     * {@link ValueWrapper} will represent a JVM-wide variable.
     */
    public interface ValueWrapperMBean<T> {

        String VALUE_PROPERTY = "Value";

        String TYPE_PROPERTY = "Type";

        String THIS_INSTANCE_PROPERTY = "ThisInstance";

        @Nullable T getValue();

        void setValue(@Nullable T value);

        @NonNull Type getType();

        void setType(@NonNull Type type);

        @NonNull ValueWrapperMBean<T> getThisInstance();
    }
}
