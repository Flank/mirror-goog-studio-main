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
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.build.gradle.internal.variant.InstantAppVariantData;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.tasks.AtomConfig;
import com.android.build.gradle.tasks.BundleInstantApp;
import com.android.build.gradle.tasks.JavaCompileAtomResClass;
import com.android.build.gradle.tasks.MergeDexAtomResClass;
import com.android.build.gradle.tasks.PackageAtom;
import com.android.build.gradle.tasks.ProcessAtomsResources;
import com.android.builder.core.AndroidBuilder;
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
            @NonNull ProjectOptions projectOptions,
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
                projectOptions,
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

        // TODO: Re-enable later.
        /*ProcessProfileWriter.getProject(projectPath)
        .setAtoms(
                variantData
                        .getVariantConfiguration()
                        .getFlatAndroidAtomsDependencies()
                        .size());*/

        createAnchorTasks(tasks, variantScope);
        createCheckManifestTask(tasks, variantScope);

        // Add tasks to package the atoms.
        AndroidTask<PackageAtom> packageAtoms =
                recorder.record(
                        ExecutionType.INSTANTAPP_TASK_MANAGER_CREATE_ATOM_PACKAGING_TASKS,
                        projectPath,
                        variantName,
                        () -> createAtomPackagingTasks(tasks, variantScope));

        // Sanity check.
        assert packageAtoms != null;

        recorder.record(
                ExecutionType.INSTANTAPP_TASK_MANAGER_CREATE_PACKAGING_TASK,
                projectPath,
                variantName,
                () -> {
                    createInstantAppPackagingTasks(tasks, variantScope, packageAtoms);
                    return null;
                });
    }

    @NonNull
    private AndroidTask<PackageAtom> createAtomPackagingTasks(
            @NonNull TaskFactory tasks, @NonNull VariantScope variantScope) {
        // Build the common configuration task.
        AndroidTask<AtomConfig> atomConfigTask =
                getAndroidTasks().create(tasks, new AtomConfig.ConfigAction(variantScope));
        atomConfigTask.dependsOn(tasks, variantScope.getPreBuildTask());

        // Generate the final resource packages.
        AndroidTask<ProcessAtomsResources> processAtomsResources =
                getAndroidTasks()
                        .create(tasks, new ProcessAtomsResources.ConfigAction(variantScope));
        processAtomsResources.dependsOn(tasks, atomConfigTask);

        // Compile the final R classes.
        AndroidTask<JavaCompileAtomResClass> javaCompileAtomsResClasses =
                getAndroidTasks()
                        .create(tasks, new JavaCompileAtomResClass.ConfigAction(variantScope));
        javaCompileAtomsResClasses.dependsOn(tasks, processAtomsResources);

        // Merge the atoms dex with their final R classes.
        AndroidTask<MergeDexAtomResClass> dexAtoms =
                getAndroidTasks()
                        .create(tasks, new MergeDexAtomResClass.ConfigAction(variantScope));
        dexAtoms.dependsOn(tasks, javaCompileAtomsResClasses);

        // Actually package the atoms.
        AndroidTask<PackageAtom> packageAtoms =
                getAndroidTasks().create(tasks, new PackageAtom.ConfigAction(variantScope));
        packageAtoms.dependsOn(tasks, dexAtoms);

        return packageAtoms;
    }

    private void createInstantAppPackagingTasks(
            @NonNull TaskFactory tasks,
            @NonNull final VariantScope variantScope,
            @NonNull AndroidTask<PackageAtom> packageAtoms) {
        AndroidTask<BundleInstantApp> bundle =
                getAndroidTasks().create(tasks, new BundleInstantApp.ConfigAction(variantScope));
        bundle.dependsOn(tasks, packageAtoms);
        variantScope.getAssembleTask().dependsOn(tasks, bundle);
    }

    @NonNull
    @Override
    protected Set<QualifiedContent.Scope> getResMergingScopes(@NonNull VariantScope variantScope) {
        return TransformManager.EMPTY_SCOPES;
    }

}
