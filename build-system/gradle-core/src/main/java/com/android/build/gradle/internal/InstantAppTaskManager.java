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
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.scope.AndroidTask;
import com.android.build.gradle.internal.scope.DefaultGradlePackagingScope;
import com.android.build.gradle.internal.scope.VariantOutputScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.build.gradle.internal.variant.InstantAppVariantData;
import com.android.build.gradle.tasks.GenerateInstantAppMetadata;
import com.android.build.gradle.tasks.JavaCompileAtomResClass;
import com.android.build.gradle.tasks.MergeDexAtomResClass;
import com.android.build.gradle.tasks.PackageAtom;
import com.android.build.gradle.tasks.PackageInstantApp;
import com.android.build.gradle.tasks.ProcessAtomsResources;
import com.android.build.gradle.tasks.ProcessInstantAppResources;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.profile.ProcessProfileWriter;
import com.android.builder.profile.Recorder;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan.ExecutionType;
import java.util.Set;
import org.gradle.api.Project;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

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
            @NonNull ToolingModelBuilderRegistry toolingRegistry,
            @NonNull Recorder threadRecorder) {
        super(
                project,
                androidBuilder,
                dataBindingBuilder,
                extension,
                sdkHandler,
                ndkHandler,
                dependencyManager,
                toolingRegistry,
                threadRecorder);
    }

    @Override
    public void createTasksForVariantData(
            @NonNull final TaskFactory tasks,
            @NonNull final BaseVariantData<? extends BaseVariantOutputData> variantData) {
        assert variantData instanceof InstantAppVariantData;

        final String projectPath = project.getPath();
        final String variantName = variantData.getName();

        final VariantScope variantScope = variantData.getScope();

        ProcessProfileWriter.getProject(projectPath)
                .setAtoms(
                        variantData
                                .getVariantConfiguration()
                                .getFlatAndroidAtomsDependencies()
                                .size());

        createAnchorTasks(tasks, variantScope);
        createCheckManifestTask(tasks, variantScope);

        // Add a task to process the manifests.
        recorder.record(
                ExecutionType.INSTANTAPP_TASK_MANAGER_CREATE_MERGE_MANIFEST_TASK,
                projectPath,
                variantName,
                () -> createMergeAppManifestsTask(tasks, variantScope));

        // Add tasks to package the atoms.
        AndroidTask<PackageAtom> packageAtoms =
                recorder.record(
                        ExecutionType.INSTANTAPP_TASK_MANAGER_CREATE_ATOM_PACKAGING_TASKS,
                        projectPath,
                        variantName,
                        () -> createAtomPackagingTasks(tasks, variantScope));

        // Sanity check.
        assert packageAtoms != null;

        // Add a task to process the resources and generate the instantApp manifest.
        recorder.record(
                ExecutionType.INSTANTAPP_TASK_MANAGER_CREATE_PROCESS_RES_TASK,
                projectPath,
                variantName,
                () -> {
                    createInstantAppProcessResTask(tasks, variantScope, packageAtoms);
                    return null;
                });

        recorder.record(
                ExecutionType.INSTANTAPP_TASK_MANAGER_CREATE_PACKAGING_TASK,
                projectPath,
                variantName,
                () -> {
                    createInstantAppPackagingTasks(tasks, variantScope);
                    return null;
                });
    }

    @NonNull
    private AndroidTask<PackageAtom> createAtomPackagingTasks(
            @NonNull TaskFactory tasks, @NonNull VariantScope variantScope) {
        // Get the single output.
        final VariantOutputScope variantOutputScope =
                variantScope.getVariantData().getOutputs().get(0).getScope();

        // Generate the final resource packages first.
        AndroidTask<ProcessAtomsResources> processAtomsResources =
                getAndroidTasks()
                        .create(tasks, new ProcessAtomsResources.ConfigAction(variantOutputScope));
        processAtomsResources.dependsOn(tasks, variantScope.getPrepareDependenciesTask());

        // Compile the final R classes.
        AndroidTask<JavaCompileAtomResClass> javaCompileAtomsResClasses =
                getAndroidTasks()
                        .create(
                                tasks,
                                new JavaCompileAtomResClass.ConfigAction(variantOutputScope));
        javaCompileAtomsResClasses.dependsOn(tasks, processAtomsResources);

        // Merge the atoms dex with their final R classes.
        AndroidTask<MergeDexAtomResClass> dexAtoms =
                getAndroidTasks()
                        .create(tasks, new MergeDexAtomResClass.ConfigAction(variantScope));
        dexAtoms.dependsOn(tasks, javaCompileAtomsResClasses);

        // Actually package the atoms.
        AndroidTask<PackageAtom> packageAtoms =
                getAndroidTasks().create(tasks, new PackageAtom.ConfigAction(variantOutputScope));
        packageAtoms.dependsOn(tasks, dexAtoms);

        return packageAtoms;
    }

    private void createInstantAppProcessResTask(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope variantScope,
            @NonNull AndroidTask<PackageAtom> packageAtoms) {
        // Get the single output.
        final VariantOutputScope variantOutputScope =
                variantScope.getVariantData().getOutputs().get(0).getScope();

        AndroidTask<ProcessInstantAppResources> processInstantAppResourcesTask =
                getAndroidTasks().create(tasks,
                        new ProcessInstantAppResources.ConfigAction(variantOutputScope));
        variantOutputScope.setProcessInstantAppResourcesTask(processInstantAppResourcesTask);
        processInstantAppResourcesTask.dependsOn(tasks, packageAtoms);
        processInstantAppResourcesTask.dependsOn(tasks, variantOutputScope.getManifestProcessorTask());
    }

    private void createInstantAppPackagingTasks(
            @NonNull TaskFactory tasks,
            @NonNull final VariantScope variantScope) {
        // Get the single output.
        final BaseVariantOutputData variantOutputData =
                variantScope.getVariantData().getOutputs().get(0);
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
                variantOutputScope.getProcessInstantAppResourcesTask());
        variantScope.getAssembleTask().dependsOn(tasks, packageInstantApp);
    }

    @NonNull
    @Override
    protected Set<QualifiedContent.Scope> getResMergingScopes(@NonNull VariantScope variantScope) {
        return TransformManager.EMPTY_SCOPES;
    }

}
