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
import com.android.annotations.concurrency.GuardedBy;
import com.android.annotations.concurrency.Immutable;
import com.android.builder.model.Version;
import com.android.utils.JvmWideVariable;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.google.common.reflect.TypeToken;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import javax.annotation.concurrent.ThreadSafe;
import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Gradle;

/**
 * A singleton object that exists across class loaders, across builds, and is specific to a plugin
 * version.
 *
 * <p>The singleton object is created when the first build starts and continues to live across
 * builds until the JVM exits. The object is unique (per plugin version) across class loaders even
 * if the plugin is loaded multiple times. If different plugin versions are loaded, there will be
 * multiple {@code BuildSessionImpl} objects corresponding to each of the plugin versions in the
 * JVM.
 *
 * <p>Here, a build refers to the entire Gradle build, which includes included builds in the case of
 * composite builds. Note that the Gradle daemon never executes two builds at the same time,
 * although it may execute sub-builds (for sub-projects) or included builds in parallel.
 *
 * <p>To ensure proper usage, the {@link #initialize(Gradle)} method must be called immediately
 * whenever a new build starts. (It may be called more than once in a build; if so, subsequent calls
 * simply return immediately since the build has already started.)
 */
@ThreadSafe
public final class BuildSessionImpl implements BuildSession {

    /**
     * A {@link BuildSession} instance that is either the actual {@link BuildSessionImpl} singleton
     * object or a proxy to that object if the object's class is loaded by a different class loader.
     */
    @NonNull
    private static final BuildSession singleton =
            createBuildSessionSingleton(Version.ANDROID_GRADLE_PLUGIN_VERSION);

    @NonNull
    @VisibleForTesting
    static BuildSession createBuildSessionSingleton(@NonNull String pluginVersion) {
        Object buildSessionSingleton =
                Verify.verifyNotNull(
                        new JvmWideVariable<>(
                                        BuildSessionImpl.class.getName(),
                                        BuildSessionImpl.class.getSimpleName(),
                                        pluginVersion,
                                        TypeToken.of(Object.class),
                                        BuildSessionImpl::new)
                                .get());

        if (buildSessionSingleton instanceof BuildSession) {
            return (BuildSession) buildSessionSingleton;
        } else {
            return (BuildSession)
                    Proxy.newProxyInstance(
                            BuildSession.class.getClassLoader(),
                            new Class[] {BuildSession.class},
                            new DelegateInvocationHandler(buildSessionSingleton));
        }
    }

    /**
     * Returns a {@link BuildSession} instance that is either the actual {@link BuildSessionImpl}
     * singleton object or a proxy to that object if the object's class is loaded by a different
     * class loader.
     */
    @NonNull
    public static BuildSession getSingleton() {
        return singleton;
    }

    /** Whether a new build has started. */
    @GuardedBy("this")
    private boolean buildStarted = false;

    @GuardedBy("this")
    @NonNull
    private LinkedHashMap<String, Runnable> buildFinishedActions = new LinkedHashMap<>();

    @Override
    public synchronized void initialize(@NonNull Gradle gradle) {
        // If the build has already started, return immediately
        if (buildStarted) {
            return;
        }

        buildStarted = true;

        // Register a handler to execute at the end of the build. We need to use the "root" Gradle
        // object to get to the end of both regular builds and composite builds.
        Gradle rootGradle = gradle;
        while (rootGradle.getParent() != null) {
            rootGradle = rootGradle.getParent();
        }
        rootGradle.addBuildListener(
                new BuildListener() {
                    @Override
                    public void buildStarted(Gradle gradle) {}

                    @Override
                    public void settingsEvaluated(Settings settings) {}

                    @Override
                    public void projectsLoaded(Gradle gradle) {}

                    @Override
                    public void projectsEvaluated(Gradle gradle) {}

                    @Override
                    public void buildFinished(BuildResult buildResult) {
                        BuildSessionImpl.this.buildFinished();
                    }
                });
    }

    @Override
    public synchronized void executeOnceWhenBuildFinished(
            @NonNull String actionGroup, @NonNull String actionName, @NonNull Runnable action) {
        buildFinishedActions.putIfAbsent(actionGroup + ":" + actionName, action);
    }

    // We don't need to synchronize this method as it should be executed only once per build
    @SuppressWarnings({"FieldAccessNotGuarded", "GuardedBy"})
    private void buildFinished() {
        // Note: If this method is not executed fully (e.g., due to an exception or Ctrl-C occurring
        // in this method or in a previous method also registered with Gradle to be run at the end
        // of the build), it may result in a corrupted state, which might affect the next build.
        // However, the chance of it happening is probably negligible.
        Preconditions.checkState(
                buildStarted, "buildStarted must be true when buildFinished() is called.");

        try {
            buildFinishedActions.values().forEach(Runnable::run);
        } finally {
            buildFinishedActions.clear();
            buildStarted = false;
        }
    }

    /**
     * Invocation handler that delegates method calls to another object. This is part of the pattern
     * to create and access a "true" singleton (object that is unique across class loaders).
     */
    @Immutable
    @VisibleForTesting
    static final class DelegateInvocationHandler implements InvocationHandler {

        @NonNull private final Object delegate;

        public DelegateInvocationHandler(@NonNull Object delegate) {
            this.delegate = delegate;
        }

        @SuppressWarnings("unused") // Used via reflection in tests
        @NonNull
        public Object getDelegate() {
            return delegate;
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
