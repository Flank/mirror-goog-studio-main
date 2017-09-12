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
import com.android.builder.model.Version;
import com.android.ide.common.util.JvmWideVariable;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.google.common.reflect.TypeToken;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.concurrent.ThreadSafe;
import org.gradle.api.Project;

/**
 * Helper class to perform a few initializations when the plugin is applied to a project.
 *
 * <p>To ensure proper usage, the {@link #initialize(Project)} method must be called immediately
 * whenever the plugin is applied to a project.
 */
@ThreadSafe
public final class PluginInitializer {

    /**
     * Map from a project instance to the plugin version that is applied to the project, used for
     * plugin version consistency check.
     *
     * <p>We use the project instance instead of the project path as the key because, within a
     * build, Gradle might apply the plugin multiple times to different project instances having the
     * same project path. Using project instances as keys helps us tracks this information better.
     *
     * <p>This map will be reset at the end of every build since the scope of the check is per
     * build.
     */
    @NonNull
    private static final ConcurrentMap<Object, String> projectToPluginVersionMap =
            Verify.verifyNotNull(
                    // IMPORTANT: This variable's group, name, and type must not be changed across
                    // plugin versions.
                    new JvmWideVariable<>(
                                    "PLUGIN_VERSION_CHECK",
                                    "PROJECT_TO_PLUGIN_VERSION",
                                    new TypeToken<ConcurrentMap<Object, String>>() {},
                                    ConcurrentHashMap::new)
                            .get());

    /**
     * Performs a few initializations when the plugin is applied to a project. This method must be
     * called immediately whenever the plugin is applied to a project.
     *
     * <p>Currently, the initialization includes checking plugin versions for consistency: Within a
     * build, different projects must apply the same version of the plugin.
     *
     * <p>Here, a build refers to the entire Gradle build, which includes included builds in the
     * case of composite builds. Note that the Gradle daemon never executes two builds at the same
     * time, although it may execute sub-builds (for sub-projects) or included builds in parallel.
     *
     * <p>The scope of the check is per build. It is okay for a project to apply different plugin
     * versions in different builds.
     *
     * @param project the project that the plugin is applied to
     * @throws IllegalStateException if the plugin version check failed
     */
    public static void initialize(@NonNull Project project) {
        BuildSessionImpl.getSingleton().initialize(project.getGradle());

        synchronized (projectToPluginVersionMap) {
            verifyPluginVersion(
                    projectToPluginVersionMap, project, Version.ANDROID_GRADLE_PLUGIN_VERSION);
        }

        // The scope of the plugin version consistency check is per build, so we need to reset the
        // plugin version map at the end of every build
        BuildSessionImpl.getSingleton()
                .executeOnceWhenBuildFinished(
                        PluginInitializer.class.getName(),
                        "resetPluginVersionMap",
                        projectToPluginVersionMap::clear);
    }

    /** Verifies that different projects apply the same version of the plugin. */
    @VisibleForTesting
    static void verifyPluginVersion(
            @NonNull ConcurrentMap<Object, String> projectToPluginVersionMap,
            @NonNull Project project,
            @NonNull String pluginVersion) {
        Preconditions.checkState(
                !projectToPluginVersionMap.containsKey(project),
                String.format(
                        "Android Gradle plugin %1$s must not be applied to project '%2$s'"
                                + " since version %3$s was already applied to this project",
                        pluginVersion,
                        project.getProjectDir().getAbsolutePath(),
                        projectToPluginVersionMap.get(project)));

        projectToPluginVersionMap.put(project, pluginVersion);

        if (projectToPluginVersionMap.values().stream().distinct().count() > 1) {
            StringBuilder errorMessage = new StringBuilder();
            errorMessage.append(
                    "Using multiple versions of the Android Gradle plugin in the same build"
                            + " is not allowed.");
            for (Map.Entry<Object, String> entry : projectToPluginVersionMap.entrySet()) {
                Project fromProject = (Project) entry.getKey();
                String toPluginVersion = entry.getValue();
                errorMessage.append(
                        String.format(
                                "\n\t'%1$s' is using version %2$s",
                                fromProject.getProjectDir().getAbsolutePath(), toPluginVersion));
            }
            throw new IllegalStateException(errorMessage.toString());
        }
    }
}
