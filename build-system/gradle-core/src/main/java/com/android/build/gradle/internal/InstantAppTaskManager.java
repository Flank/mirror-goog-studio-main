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

import com.android.annotations.NonNull;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.scope.AndroidTask;
import com.android.build.gradle.internal.scope.DefaultGradlePackagingScope;
import com.android.build.gradle.internal.scope.VariantOutputScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.build.gradle.internal.variant.InstantAppVariantData;
import com.android.build.gradle.internal.variant.InstantAppVariantOutputData;
import com.android.build.gradle.tasks.GenerateInstantAppMetadata;
import com.android.build.gradle.tasks.MergeAtoms;
import com.android.build.gradle.tasks.PackageInstantApp;
import com.android.build.gradle.tasks.ProcessInstantAppResources;
import com.android.builder.core.AndroidBuilder;

import org.gradle.api.Project;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

import android.databinding.tool.DataBindingBuilder;

import java.util.Set;

/**
 * TaskManager for creating tasks in an Android InstantApp project.
 */
public class InstantAppTaskManager extends TaskManager {

    public InstantAppTaskManager(
            @NonNull Project project,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull DataBindingBuilder dataBindingBuilder,
            @NonNull AndroidConfig extension,
            @NonNull SdkHandler sdkHandler,
            @NonNull NdkHandler ndkHandler,
            @NonNull DependencyManager dependencyManager,
            @NonNull ToolingModelBuilderRegistry toolingRegistry) {
        super(
                project,
                androidBuilder,
                dataBindingBuilder,
                extension,
                sdkHandler,
                ndkHandler,
                dependencyManager,
                toolingRegistry);
    }

    @Override
    public void createTasksForVariantData(
            @NonNull final TaskFactory tasks,
            @NonNull final BaseVariantData<? extends BaseVariantOutputData> variantData) {
        assert variantData instanceof InstantAppVariantData;

        final VariantScope variantScope = variantData.getScope();

        createAnchorTasks(tasks, variantScope);
        createCheckManifestTask(tasks, variantScope);

        // Add a task to merge the asset folders.
        createMergeAtomsTask(tasks, variantScope);

        // Add a task to get the AndroidManifest from the atom.
        createInstantAppProcessResTask(tasks, variantScope);

        createInstantAppPackagingTasks(tasks, variantScope);
    }

    private void createMergeAtomsTask(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope variantScope) {
        AndroidTask<MergeAtoms> mergeAtomsTask = getAndroidTasks().create(tasks,
                new MergeAtoms.ConfigAction(variantScope));
        variantScope.setMergeAtomsTask(mergeAtomsTask);
        mergeAtomsTask.dependsOn(tasks, variantScope.getPrepareDependenciesTask());
    }

    private void createInstantAppProcessResTask(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope variantScope) {
        final InstantAppVariantData variantData =
                (InstantAppVariantData) variantScope.getVariantData();

        for (InstantAppVariantOutputData variantOutputData : variantData.getOutputs()) {
            final VariantOutputScope variantOutputScope = variantOutputData.getScope();

            AndroidTask<ProcessInstantAppResources> processInstantAppResourcesTask =
                getAndroidTasks().create(tasks,
                    new ProcessInstantAppResources.ConfigAction(variantOutputScope));
            variantOutputScope.setProcessInstantAppResourcesTask(processInstantAppResourcesTask);
            processInstantAppResourcesTask.dependsOn(tasks, variantScope.getPrepareDependenciesTask());
        }

    }

    private void createInstantAppPackagingTasks(
            @NonNull TaskFactory tasks,
            @NonNull final VariantScope variantScope) {
        final InstantAppVariantData variantData = (InstantAppVariantData) variantScope.getVariantData();

        // loop on all outputs. The only difference will be the name of the task, and location
        // of the generated data.
        for (InstantAppVariantOutputData variantOutputData : variantData.getOutputs()) {
            final VariantOutputScope variantOutputScope = variantOutputData.getScope();

            AndroidTask<GenerateInstantAppMetadata> generateInstantAppMetadataTask =
                    getAndroidTasks().create(tasks,
                            new GenerateInstantAppMetadata.ConfigAction(variantOutputScope));
            generateInstantAppMetadataTask.dependsOn(tasks, variantScope.getPrepareDependenciesTask());

            DefaultGradlePackagingScope packagingScope =
                    new DefaultGradlePackagingScope(variantOutputScope);
            AndroidTask<PackageInstantApp> packageInstantApp =
                    getAndroidTasks().create(tasks,
                            new PackageInstantApp.ConfigAction(packagingScope));

            packageInstantApp.configure(
                    tasks,
                    task -> variantOutputData.packageAndroidArtifactTask = task);

            packageInstantApp.dependsOn(tasks,
                    generateInstantAppMetadataTask,
                    variantScope.getMergeAtomsTask(),
                    variantOutputScope.getProcessInstantAppResourcesTask());
            variantScope.getAssembleTask().dependsOn(tasks, packageInstantApp);
        }
    }

    @NonNull
    @Override
    protected Set<QualifiedContent.Scope> getResMergingScopes(@NonNull VariantScope variantScope) {
        return TransformManager.EMPTY_SCOPES;
    }

}
