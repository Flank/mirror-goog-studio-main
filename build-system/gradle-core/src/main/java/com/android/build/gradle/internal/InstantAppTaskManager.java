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

package com.android.build.gradle.internal;

import android.databinding.tool.DataBindingBuilder;
import com.android.annotations.NonNull;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.scope.AndroidTask;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.TaskOutputHolder;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.tasks.BundleInstantApp;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.profile.Recorder;
import com.android.utils.FileUtils;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan;
import java.io.File;
import java.util.Set;
import org.gradle.api.Project;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/** TaskManager for creating tasks in an Android InstantApp project. */
public class InstantAppTaskManager extends TaskManager {

    public InstantAppTaskManager(
            @NonNull GlobalScope globalScope,
            @NonNull Project project,
            @NonNull ProjectOptions projectOptions,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull DataBindingBuilder dataBindingBuilder,
            @NonNull AndroidConfig extension,
            @NonNull SdkHandler sdkHandler,
            @NonNull DependencyManager dependencyManager,
            @NonNull ToolingModelBuilderRegistry toolingRegistry,
            @NonNull Recorder threadRecorder) {
        super(
                globalScope,
                project,
                projectOptions,
                androidBuilder,
                dataBindingBuilder,
                extension,
                sdkHandler,
                dependencyManager,
                toolingRegistry,
                threadRecorder);
    }

    @Override
    public void createTasksForVariantScope(
            @NonNull final TaskFactory tasks, @NonNull final VariantScope variantScope) {
        // Create the bundling task.
        recorder.record(
                GradleBuildProfileSpan.ExecutionType.INSTANTAPP_TASK_MANAGER_CREATE_PACKAGING_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> {
                    File bundleDir =
                            FileUtils.join(
                                    globalScope.getApkLocation(),
                                    variantScope.getVariantConfiguration().getDirName());
                    AndroidTask<BundleInstantApp> bundleTask =
                            getAndroidTasks()
                                    .create(
                                            tasks,
                                            new BundleInstantApp.ConfigAction(
                                                    variantScope, bundleDir));
                    variantScope.getAssembleTask().dependsOn(tasks, bundleTask);

                    variantScope.addTaskOutput(
                            TaskOutputHolder.TaskOutputType.INSTANTAPP_BUNDLE,
                            bundleDir,
                            bundleTask.getName());
                });

        // FIXME: Stop creating dummy tasks just to make the IDE sync shut up.
        tasks.create(variantScope.getTaskName("generate", "Sources"));
        tasks.create(variantScope.getTaskName("compile", "Sources"));
    }

    @NonNull
    @Override
    protected Set<QualifiedContent.Scope> getResMergingScopes(@NonNull VariantScope variantScope) {
        return TransformManager.EMPTY_SCOPES;
    }

    @Override
    protected void postJavacCreation(@NonNull TaskFactory tasks, @NonNull VariantScope scope) {
        // do nothing.
    }
}
