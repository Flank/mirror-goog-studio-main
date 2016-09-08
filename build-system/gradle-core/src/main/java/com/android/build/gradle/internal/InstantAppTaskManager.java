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
import com.android.annotations.Nullable;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.scope.AndroidTask;
import com.android.build.gradle.internal.scope.AtomPackagingScope;
import com.android.build.gradle.internal.scope.BaseAtomPackagingScope;
import com.android.build.gradle.internal.scope.DefaultGradlePackagingScope;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.PackagingScope;
import com.android.build.gradle.internal.scope.VariantOutputScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.build.gradle.internal.variant.InstantAppVariantData;
import com.android.build.gradle.tasks.MergeDexAtomResClass;
import com.android.build.gradle.tasks.GenerateInstantAppMetadata;
import com.android.build.gradle.tasks.PackageAtom;
import com.android.build.gradle.tasks.PackageInstantApp;
import com.android.build.gradle.tasks.ProcessAndroidResources;
import com.android.build.gradle.tasks.ProcessInstantAppResources;
import com.android.build.gradle.tasks.factory.AndroidJavaCompile;
import com.android.build.gradle.tasks.factory.AtomResClassJavaCompileConfigAction;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.model.AndroidAtom;
import com.android.builder.profile.ProcessRecorder;
import com.android.builder.profile.Recorder;
import com.android.builder.profile.ThreadRecorder;
import com.android.utils.FileUtils;
import com.google.wireless.android.sdk.stats.AndroidStudioStats.GradleBuildProfileSpan.ExecutionType;

import org.gradle.api.Project;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

import android.databinding.tool.DataBindingBuilder;

import java.util.ArrayList;
import java.util.List;
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

        final String projectPath = project.getPath();
        final String variantName = variantData.getName();

        final VariantScope variantScope = variantData.getScope();

        ProcessRecorder.getProject(projectPath).setAtoms(
                variantData.getVariantConfiguration().getFlatAndroidAtomsDependencies().size());

        createAnchorTasks(tasks, variantScope);
        createCheckManifestTask(tasks, variantScope);

        // Add a task to process the manifests.
        ThreadRecorder.get().record(ExecutionType.INSTANTAPP_TASK_MANAGER_CREATE_MERGE_MANIFEST_TASK,
                projectPath, variantName, () -> {
                    createMergeAppManifestsTask(tasks, variantScope);
                    return null;
                });

        // Add tasks to package the atoms.
        AndroidTask<PackageAtom> lastPackageAtom =
                ThreadRecorder.get().record(ExecutionType.INSTANTAPP_TASK_MANAGER_CREATE_ATOM_PACKAGING_TASKS,
                        projectPath,
                        variantName,
                        () -> createAtomPackagingTasks(tasks, variantScope));

        // Sanity check.
        assert lastPackageAtom != null;

        // Add a task to process the resources and generate the instantApp manifest.
        ThreadRecorder.get().record(ExecutionType.INSTANTAPP_TASK_MANAGER_CREATE_PROCESS_RES_TASK,
                projectPath, variantName, () -> {
                    createInstantAppProcessResTask(tasks, variantScope, lastPackageAtom);
                    return null;
                });

        ThreadRecorder.get().record(ExecutionType.INSTANTAPP_TASK_MANAGER_CREATE_PACKAGING_TASK,
                projectPath, variantName, () -> {
                    createInstantAppPackagingTasks(tasks, variantScope);
                    return null;
                });
    }

    private AndroidTask<PackageAtom> createAtomPackagingTasks(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope variantScope) {
        // Get the single output.
        final VariantOutputScope variantOutputScope =
                variantScope.getVariantData().getOutputs().get(0).getScope();
        List<AndroidAtom> previousAtoms = new ArrayList<>();

        List<AndroidAtom> androidAtoms = variantScope
                .getVariantConfiguration()
                .getPackageDependencies()
                .getAtomDependencies();

        AndroidTask<PackageAtom> previousPackagingTask = null;
        for (AndroidAtom atom : androidAtoms) {
            previousPackagingTask = createAtomPackagingTasks(
                    tasks, variantOutputScope, atom, previousAtoms, previousPackagingTask);
        }
        return previousPackagingTask;
    }

    /**
     * Create the packaging tasks for <code>androidAtom</code> and its dependencies.
     *
     * @param tasks the taskFactory.
     * @param variantOutputScope the variantOutputScope for this instantApp.
     * @param androidAtom the atom that needs to be packaged.
     * @param previousAtoms the previously packaged atom files, in order.
     * @param previousPackagingTask the previous packaging task.
     * @return the packaging task.
     */
    private AndroidTask<PackageAtom> createAtomPackagingTasks(
            @NonNull TaskFactory tasks,
            @NonNull VariantOutputScope variantOutputScope,
            @NonNull AndroidAtom androidAtom,
            @NonNull List<AndroidAtom> previousAtoms,
            @Nullable AndroidTask<PackageAtom> previousPackagingTask) {
        final VariantScope variantScope = variantOutputScope.getVariantScope();
        final BaseVariantData variantData = variantScope.getVariantData();
        final GlobalScope globalScope = variantScope.getGlobalScope();

        // If this is a common atom dependency that was previously handled, just return.
        if (previousAtoms.contains(androidAtom))
            return previousPackagingTask;


        // Create dependent atom tasks first.
        List<? extends AndroidAtom> androidAtoms = androidAtom.getAtomDependencies();
        for (AndroidAtom atom : androidAtoms) {
            previousPackagingTask = createAtomPackagingTasks(
                    tasks, variantOutputScope, atom, previousAtoms, previousPackagingTask);
        }

        // This is the base atom, it only needs to be packaged.
        if (previousPackagingTask == null) {
            PackagingScope packagingScope =
                    new BaseAtomPackagingScope(variantOutputScope, androidAtom);
            AndroidTask<PackageAtom> packageAtom = getAndroidTasks().create(tasks,
                    new PackageAtom.ConfigAction(packagingScope));
            packageAtom.dependsOn(tasks,
                    variantScope.getPrepareDependenciesTask());
            previousAtoms.add(androidAtom);
            return packageAtom;
        }

        // This is another atom, first, package the resources.
        variantData.calculateFilters(globalScope.getExtension().getSplits());
        AndroidTask<ProcessAndroidResources> processAtomResources = getAndroidTasks().create(tasks,
                new ProcessAndroidResources.AtomConfigAction(
                        variantOutputScope,
                        FileUtils.join(globalScope.getIntermediatesDir(),
                                "symbols",
                                androidAtom.getAtomName(),
                                variantData.getVariantConfiguration().getDirName()),
                        androidAtom,
                        previousAtoms));
        processAtomResources.dependsOn(tasks, previousPackagingTask);

        // Then, compile the final R class.
        AndroidTask<AndroidJavaCompile> javaCompile = getAndroidTasks().create(tasks,
                new AtomResClassJavaCompileConfigAction(variantScope, androidAtom));
        javaCompile.dependsOn(tasks, processAtomResources);

        // Merge the atom dex with the final R class.
        AndroidTask<MergeDexAtomResClass> dexAtom = getAndroidTasks().create(tasks,
                new MergeDexAtomResClass.ConfigAction(variantScope, androidAtom));
        dexAtom.dependsOn(tasks, javaCompile);

        // Finally, package the atom.
        PackagingScope packagingScope =
                new AtomPackagingScope(variantOutputScope, androidAtom);
        AndroidTask<PackageAtom> packageAtom = getAndroidTasks().create(tasks,
                new PackageAtom.ConfigAction(packagingScope));
        packageAtom.dependsOn(tasks, dexAtom);

        previousAtoms.add(androidAtom);
        return packageAtom;
    }

    private void createInstantAppProcessResTask(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope variantScope,
            @NonNull AndroidTask<PackageAtom> lastPackageAtom) {
        // Get the single output.
        final VariantOutputScope variantOutputScope =
                variantScope.getVariantData().getOutputs().get(0).getScope();

        AndroidTask<ProcessInstantAppResources> processInstantAppResourcesTask =
                getAndroidTasks().create(tasks,
                        new ProcessInstantAppResources.ConfigAction(variantOutputScope));
        variantOutputScope.setProcessInstantAppResourcesTask(processInstantAppResourcesTask);
        processInstantAppResourcesTask.dependsOn(tasks, lastPackageAtom);
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
