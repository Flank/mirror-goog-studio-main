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
import com.android.annotations.VisibleForTesting;
import com.android.annotations.concurrency.Immutable;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.api.Project;
import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Gradle;

/**
 * A singleton object that shares the life cycle of a build: It is created when the plugin is first
 * applied to a project and released (available for garbage collection) when the build is finished.
 * It is created again when the plugin is applied again in the next build (within the same Gradle
 * daemon).
 *
 * <p>Here, a "build" refers to the entire Gradle build. For composite builds, it means the whole
 * build that includes included builds.
 *
 * <p>This class enforces that within a build, only one version of the plugin is loaded. If the
 * plugin is loaded multiple times with the same version, there is still only one instance of {@link
 * BuildSessionSingleton} in the JVM.
 *
 * <p>The singleton object should be created immediately when the plugin is first applied to a
 * project by calling {@link BuildSessionHelper#startOnce(Project)}.
 *
 * <p>This class is thread-safe.
 */
@Immutable
public final class BuildSessionSingleton implements BuildSessionInterface {

    /** List of actions that will be executed last when the build is finished. */
    @NonNull private final List<Runnable> executeLastActions = new CopyOnWriteArrayList<>();

    /**
     * Creates the {@code BuildSessionSingleton} object.
     *
     * <p>IMPORTANT: This method must be called only by {@link
     * BuildSessionHelper#startOnce(Project)}.
     */
    BuildSessionSingleton(@NonNull Gradle gradle) {
        // In case of a composite build, we want to use the "root" Gradle object so that the
        // buildFinished event exposed by that object is really the end of the entire build
        while (gradle.getParent() != null) {
            gradle = gradle.getParent();
        }

        // Register a handler to execute at the end of the entire build (for a composite build, this
        // means the end of the composite build)
        gradle.addBuildListener(
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
                        BuildSessionSingleton.this.buildFinished();
                    }
                });
    }

    @Override
    public void executeLastWhenBuildFinished(@NonNull Runnable action) {
        // We don't need to synchronize this method since the list below is thread-safe
        executeLastActions.add(action);
    }

    /**
     * Executes the registered actions only once at the end of the entire build (for a composite
     * build, this means the end of the composite build).
     *
     * <p>There are several cases when this method may not work as expected:
     *
     * <ol>
     *   <li>This method was not registered to be executed, if the build failed or if the user hit
     *       Ctrl-C before this method could be registered. To reduce this risk, we need to make
     *       sure {@link BuildSessionHelper#startOnce(Project)} is called immediately when the
     *       plugin is first applied to a project and register this method early (see the {@link
     *       #BuildSessionSingleton(Gradle)} constructor).
     *   <li>This method was registered but was not executed. Although Gradle will always execute
     *       build-finished event handlers even if the build fails or if the user hits Ctrl-C, it
     *       could happen that one of the event handlers before this one failed or the user hit
     *       Ctrl-C while they were being executed. In that case, the remaining event handlers
     *       including this one will not get executed. To reduce this risk, we again need to make
     *       sure {@link BuildSessionHelper#startOnce(Project)} is called immediately when the
     *       plugin is first applied to a project.
     *   <li>This method was registered and was executed. However, it may not have finished
     *       properly, again possibly due to a failure or Ctrl-C.
     *   <li>This method was executed successfully. However, other build-finished event handlers may
     *       be executed after this handler is finished and may run on an invalid state (e.g., using
     *       a variable whose value has already been unset). To reduce this risk, our plugin should
     *       register build-finished event handlers using the {@link BuildSessionHelper} API instead
     *       of using the Gradle API directly.
     * </ol>
     *
     * <p>If these scenarios happen, there might be several consequences:
     *
     * <ol>
     *   <li>The contract that this singleton's lifetime is limited to a build is broken.
     *   <li>The JVM-wide variables that keep track of plugin versions are not unset at the end of a
     *       build, possibly resulting in false alarms.
     *   <li>Actions registered using the {@link BuildSessionHelper} API to be executed at the end
     *       of the build are not guaranteed to be executed or completed successfully.
     *   <li>Actions registered directly using the Gradle API to be executed at the end of the build
     *       are not guaranteed to be executed or completed successfully.
     * </ol>
     *
     * <p>If these issues happen, killing the Gradle daemon might resolve the situation. (If the
     * Gradle daemon was not enabled in the first place, then none of these issues apply.)
     *
     * <p>If we use this API properly (call {@link BuildSessionHelper#startOnce(Project)}
     * immediately when the plugin is first applied to a project and always register build-finished
     * event handlers using the {@link BuildSessionHelper} API), the chance of the above issues
     * happening is rather small (however, we should be aware of them).
     */
    @VisibleForTesting
    void buildFinished() {
        // We don't need to synchronize this method since it is executed only once in a build (and
        // therefore by only a single thread).
        // NOTE: If this method throws an exception or if the user hits Ctrl-C while this method is
        // being executed, there might be several consequences (see the above javadoc).
        for (Runnable action : executeLastActions) {
            action.run();
        }
    }
}
