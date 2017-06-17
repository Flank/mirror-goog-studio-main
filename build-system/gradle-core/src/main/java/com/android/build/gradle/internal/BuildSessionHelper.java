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

package com.android.build.gradle.internal;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.annotations.concurrency.Immutable;
import com.android.builder.Version;
import com.android.ide.common.util.JvmWideVariable;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.TypeToken;
import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.gradle.api.Project;

/**
 * Helper class with static methods to access the {@link BuildSessionSingleton} object. It
 * internally manages the creation and release of this object at the beginning and end of a build.
 *
 * <p>This class does not allow callers to directly hold an instance of the {@link
 * BuildSessionSingleton} object because it must be released (available for garbage collection) at
 * the end of a build, and we don't want to leak any references that prevent it from being released.
 *
 * <p>This class is thread-safe.
 */
public final class BuildSessionHelper {

    /**
     * A {@link BuildSessionInterface} instance that is either the actual {@link
     * BuildSessionSingleton} object or a proxy to that singleton object if the object's class is
     * loaded by a different class loader.
     */
    @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
    @Nullable
    private static BuildSessionInterface singleton = null;

    /**
     * Creates the {@link BuildSessionSingleton} object, only if it has not been created earlier in
     * the JVM for the current build.
     *
     * <p>Here, a "build" refers to the entire Gradle build. For composite builds, it means the
     * whole build that includes included builds.
     *
     * <p>This method enforces that within a build, only one version of the plugin is loaded. If the
     * plugin is loaded multiple times with the same version, there is still only one instance of
     * {@link BuildSessionSingleton} in the JVM.
     *
     * <p>This method should be called immediately when the plugin is first applied to a project.
     *
     * @param project the project that the plugin is applied to
     * @throws IllegalStateException if another version of the plugin has been loaded in the current
     *     build
     */
    public static synchronized void startOnce(@NonNull Project project) {
        Set<JvmWideVariable<?>> jvmWidePluginVersionRecords =
                verifyPluginVersion(
                        project,
                        Version.ANDROID_GRADLE_PLUGIN_VERSION,
                        Version.ANDROID_GRADLE_COMPONENT_PLUGIN_VERSION);

        // If the plugin is loaded multiple times, there will be multiple calls to startOnce() in a
        // build. The variable below indicates whether this is the first call.
        AtomicBoolean buildFirstStarted = new AtomicBoolean(false);

        if (singleton == null) {
            // Create or get the BuildSessionSingleton object (it might have already been created by
            // a previous called to startOnce() from this same class loaded by a different class
            // loader)
            JvmWideVariable<Object> jvmWideSingleton =
                    new JvmWideVariable<>(
                            BuildSessionSingleton.class.getPackage().getName(),
                            BuildSessionSingleton.class.getSimpleName(),
                            TypeToken.of(Object.class),
                            () -> {
                                buildFirstStarted.set(true);
                                return new BuildSessionSingleton(project.getGradle());
                            });
            Object buildSessionSingleton = jvmWideSingleton.get();
            Preconditions.checkNotNull(buildSessionSingleton);

            // Either store the actual BuildSessionSingleton object or create a proxy to that object
            // if it is created from a different class loader
            if (buildSessionSingleton instanceof BuildSessionInterface) {
                singleton = (BuildSessionInterface) buildSessionSingleton;
            } else {
                singleton =
                        (BuildSessionInterface)
                                Proxy.newProxyInstance(
                                        BuildSessionInterface.class.getClassLoader(),
                                        new Class[] {BuildSessionInterface.class},
                                        new DelegateInvocationHandler(buildSessionSingleton));
            }

            // At the end of a build, we release the singleton object by un-registering the JVM-wide
            // variable and un-linking any references to it
            executeLastWhenBuildFinished(
                    () -> {
                        if (buildFirstStarted.get()) {
                            jvmWideSingleton.unregister();
                        }
                        singleton = null;
                    });
        }

        // Also un-register the JVM-wide variables that keep track of plugin versions at the end of
        // a build (we allow different plugin versions to be used across different builds). We don't
        // need to un-link the references to them since there are none.
        if (buildFirstStarted.get()) {
            executeLastWhenBuildFinished(
                    () -> jvmWidePluginVersionRecords.forEach(JvmWideVariable::unregister));
        }
    }

    /**
     * Verifies that only one version of the plugin (and the component model plugin) is loaded in
     * the current build. Also make sure that the plugin is not applied twice to a project.
     *
     * @return the JVM-wide variables used to keep track of plugin versions
     */
    @VisibleForTesting
    @NonNull
    static Set<JvmWideVariable<?>> verifyPluginVersion(
            @NonNull Project project,
            @NonNull String pluginVersion,
            @NonNull String componentModelPluginVersion) {
        JvmWideVariable<?> jvmWidePluginVersion =
                doVerifyPluginVersion(
                        project.getProjectDir(), "Android Gradle plugin", pluginVersion);
        JvmWideVariable<?> jvmWideComponentModelPluginVersion =
                doVerifyPluginVersion(
                        project.getProjectDir(),
                        "Android Gradle component model plugin",
                        componentModelPluginVersion);
        return ImmutableSet.of(jvmWidePluginVersion, jvmWideComponentModelPluginVersion);
    }

    @NonNull
    private static JvmWideVariable<?> doVerifyPluginVersion(
            @NonNull File projectDir, @NonNull String pluginName, @NonNull String pluginVersion) {
        JvmWideVariable<ConcurrentMap<String, String>> jvmWideMap =
                new JvmWideVariable<>(
                        "PLUGIN_VERSION",
                        pluginName,
                        new TypeToken<ConcurrentMap<String, String>>() {},
                        ConcurrentHashMap::new);
        ConcurrentMap<String, String> projectToPluginVersionMap = jvmWideMap.get();
        Preconditions.checkNotNull(projectToPluginVersionMap);

        AtomicBoolean pluginWasAlreadyApplied = new AtomicBoolean(true);
        projectToPluginVersionMap.computeIfAbsent(
                projectDir.getAbsolutePath(),
                (any) -> {
                    pluginWasAlreadyApplied.set(false);
                    return pluginVersion;
                });

        if (pluginWasAlreadyApplied.get()) {
            throw new IllegalStateException(
                    String.format(
                            "%1$s %2$s must not be applied to project %3$s"
                                    + " since version %4$s was already applied to this project",
                            pluginName,
                            pluginVersion,
                            projectDir.getAbsolutePath(),
                            projectToPluginVersionMap.get(projectDir.getAbsolutePath())));
        }

        if (projectToPluginVersionMap.values().stream().distinct().count() > 1) {
            throw new IllegalStateException(
                    "Using multiple versions of the plugin is not allowed.\n\t"
                            + Joiner.on("\n\t")
                                    .withKeyValueSeparator(" is using " + pluginName + " ")
                                    .join(projectToPluginVersionMap));
        }

        return jvmWideMap;
    }

    /** Verifies that the singleton has been created. */
    @VisibleForTesting
    static synchronized void verifySingletonExists() {
        if (singleton == null) {
            throw new IllegalStateException(
                    "Instance does not exist."
                            + " BuildSessionHelper.startOnce(Gradle) must be called first.");
        }
    }

    /**
     * Registers an action that will be executed last when a build is finished.
     *
     * <p>This method must not be made public. It is reserved to perform important clean-up actions
     * at the very end of a build.
     */
    @VisibleForTesting
    static void executeLastWhenBuildFinished(@NonNull Runnable action) {
        verifySingletonExists();
        //noinspection ConstantConditions
        singleton.executeLastWhenBuildFinished(action);
    }

    /**
     * Returns a {@link BuildSessionInterface} instance that is either the actual {@link
     * BuildSessionSingleton} object or a proxy to that singleton object if the object's class is
     * loaded by a different class loader.
     *
     * <p>This method must not be made public (see the javadoc of {@code BuildSessionHelper}. It is
     * used for testing only.
     */
    @VisibleForTesting
    @Nullable
    static synchronized BuildSessionInterface getSingleton() {
        return singleton;
    }

    /**
     * Invocation handler that delegates method calls to another object. This is part of the pattern
     * to create a "true" singleton (object that is unique across class loaders).
     */
    @Immutable
    private static final class DelegateInvocationHandler implements InvocationHandler {

        @NonNull private final Object delegate;

        public DelegateInvocationHandler(@NonNull Object delegate) {
            this.delegate = delegate;
        }

        @Nullable
        @Override
        public Object invoke(@NonNull Object proxy, @NonNull Method method, @NonNull Object[] args)
                throws Throwable {
            return delegate.getClass()
                    .getMethod(method.getName(), method.getParameterTypes())
                    .invoke(delegate, args);
        }
    }
}
