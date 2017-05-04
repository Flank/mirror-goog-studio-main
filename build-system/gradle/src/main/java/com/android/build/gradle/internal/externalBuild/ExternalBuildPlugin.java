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

package com.android.build.gradle.internal.externalBuild;

import com.android.build.gradle.internal.BuildCacheUtils;
import com.android.build.gradle.internal.profile.ProfilerInitializer;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.builder.profile.ThreadRecorder;
import com.android.builder.utils.FileCache;
import com.google.common.collect.ImmutableMap;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * Gradle plugin for supporting InstantRun with external build system..
 *
 * <p>This plugin is private to the android build system and is not intended to be used directly in
 * end users projects. Interfaces, DSL and tasks are subject to change without any notification.
 */
public class ExternalBuildPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        final ExternalBuildExtension externalBuildExtension =
                project.getExtensions().create("externalBuild", ExternalBuildExtension.class);
        ProjectOptions projectOptions =
                new ProjectOptions(
                        project,
                        ImmutableMap.of(
                                // Always run external plugin with AAPT V1.
                                BooleanOption.ENABLE_AAPT2.getPropertyName(), false));

        ProfilerInitializer.init(project, projectOptions);

        FileCache buildCache = BuildCacheUtils.createBuildCacheIfEnabled(project, projectOptions);

        ExternalBuildGlobalScope globalScope =
                new ExternalBuildGlobalScope(project, projectOptions, buildCache);

        ExternalBuildTaskManager taskManager =
                new ExternalBuildTaskManager(globalScope, project, ThreadRecorder.get());

        project.afterEvaluate(project1 -> {
            try {
                taskManager.createTasks(externalBuildExtension);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
