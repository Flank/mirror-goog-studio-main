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
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.CoreJackOptions;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.build.gradle.internal.pipeline.StreamFilter;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.pipeline.TransformStream;
import com.android.build.gradle.internal.pipeline.TransformTask;
import com.android.build.gradle.internal.scope.AndroidTask;
import com.android.build.gradle.internal.scope.DefaultGradlePackagingScope;
import com.android.build.gradle.internal.scope.VariantOutputScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.AtomVariantData;
import com.android.build.gradle.internal.variant.AtomVariantOutputData;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.build.gradle.internal.variant.VariantHelper;
import com.android.build.gradle.tasks.AndroidJarTask;
import com.android.build.gradle.tasks.GenerateAtomMetadata;
import com.android.build.gradle.tasks.PackageAtom;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.BuilderConstants;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

import android.databinding.tool.DataBindingBuilder;

import java.io.File;
import java.util.Set;

/**
 * TaskManager for creating tasks in an Android Atom project.
 */
public class AtomTaskManager extends TaskManager {

    private Task assembleDefault;

    public AtomTaskManager (
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
        assert variantData instanceof AtomVariantData;

        final VariantScope variantScope = variantData.getScope();
        final GradleVariantConfiguration variantConfig = variantData.getVariantConfiguration();
        final File variantBundleDir = variantScope.getBaseBundleDir();

        createAnchorTasks(tasks, variantScope);
        createCheckManifestTask(tasks, variantScope);

        // Create all current streams (dependencies mostly at this point)
        createDependencyStreams(tasks, variantScope);

        // Add a task to process the manifest(s)
        createMergeAppManifestsTask(tasks, variantScope);
        // Add a task to create the res values
        createGenerateResValuesTask(tasks, variantScope);

        // Add a task to compile renderscript files.
        createRenderscriptTask(tasks, variantScope);

        basicCreateMergeResourcesTask(
                tasks,
                variantScope,
                "mergeAtom",
                null,
                true /*includeDependencies*/,
                true /*process9patch*/);

        // Add a task to merge the assets folders
        createMergeAssetsTask(tasks, variantScope);

        // Add a task to create the BuildConfig class
        createBuildConfigTask(tasks, variantScope);
        // Add a task to generate resource source files, directing the location
        // of the r.txt file to be directly in the bundle.
        createApkProcessResTask(tasks, variantScope);

        // process java resources
        createProcessJavaResTasks(tasks, variantScope);
        createAidlTask(tasks, variantScope);

        createShaderTask(tasks, variantScope);
        // Add NDK tasks
        if (!isComponentModelPlugin) {
            createNdkTasks(variantScope);
        } else {
            if (variantData.compileTask != null) {
                variantData.compileTask.dependsOn(getNdkBuildable(variantData));
            } else {
                variantScope.getCompileTask().dependsOn(tasks, getNdkBuildable(variantData));
            }
        }
        variantScope.setNdkBuildable(getNdkBuildable(variantData));

        // Add a task to merge the jni libs folders
        createMergeJniLibFoldersTasks(tasks, variantScope);

        // Add a compile task
        CoreJackOptions jackOptions =
                variantData.getVariantConfiguration().getJackOptions();
        AndroidTask<? extends JavaCompile> javacTask =
                createJavacTask(tasks, variantScope);

        if (jackOptions.isEnabled()) {
            AndroidTask<TransformTask> jackTask =
                    createJackTask(tasks, variantScope, true /*compileJavaSource*/);
            setJavaCompilerTask(jackTask, tasks, variantScope);
        } else {
            addJavacClassesStream(variantScope);
            setJavaCompilerTask(javacTask, tasks, variantScope);
            getAndroidTasks().create(tasks,
                    new AndroidJarTask.JarClassesConfigAction(variantScope));
            createPostCompilationTasks(tasks, variantScope);
        }

        // Add data binding tasks if enabled
        if (extension.getDataBinding().isEnabled()) {
            createDataBindingTasks(tasks, variantScope);
        }

        createAtomPackagingTasks(tasks, variantScope);

        createLintTasks(tasks, variantScope);

        final Zip bundle = project.getTasks().create(
                variantScope.getTaskName("bundle", "Atom"), Zip.class);
        for (final BaseVariantOutputData variantOutputData : variantData.getOutputs()) {
            bundle.dependsOn(variantOutputData.packageAndroidArtifactTask);
            variantOutputData.getScope().setAssembleTask(variantScope.getAssembleTask());
        }

        bundle.setDescription("Assembles a bundle containing the atom in " +
                variantConfig.getBaseName() + ".");
        bundle.setDestinationDir(
                new File(getGlobalScope().getOutputsDir(), BuilderConstants.EXT_ATOMBUNDLE_ARCHIVE));
        bundle.setArchiveName(getGlobalScope().getProjectBaseName()
                + "-" + variantConfig.getBaseName()
                + "." + BuilderConstants.EXT_ATOMBUNDLE_ARCHIVE);
        bundle.setExtension(BuilderConstants.EXT_ATOMBUNDLE_ARCHIVE);
        bundle.from(variantBundleDir);

        variantScope.getAssembleTask().dependsOn(tasks, bundle);

        if (getExtension().getDefaultPublishConfig().equals(variantConfig.getFullName())) {
            VariantHelper.setupDefaultConfig(project,
                    variantData.getVariantDependency().getPackageConfiguration());

            // add the artifact that will be published
            project.getArtifacts().add("default", bundle);

            getAssembleDefault().dependsOn(variantScope.getAssembleTask().getName());
        }

    }

    private void createAtomPackagingTasks(
            @NonNull TaskFactory tasks,
            @NonNull final VariantScope variantScope) {
        final AtomVariantData variantData = (AtomVariantData) variantScope.getVariantData();

        // loop on all outputs. The only difference will be the name of the task, and location
        // of the generated data.
        for (final AtomVariantOutputData variantOutputData : variantData.getOutputs()) {
            final VariantOutputScope variantOutputScope = variantOutputData.getScope();

            AndroidTask<GenerateAtomMetadata> generateAtomMetadata =
                    getAndroidTasks().create(tasks,
                            new GenerateAtomMetadata.ConfigAction(variantOutputScope));

            DefaultGradlePackagingScope packagingScope =
                    new DefaultGradlePackagingScope(variantOutputScope);
            AndroidTask<PackageAtom> packageAtom =
                    getAndroidTasks().create(tasks,
                            new PackageAtom.ConfigAction(packagingScope));

            packageAtom.configure(
                    tasks,
                    task -> variantOutputData.packageAndroidArtifactTask = task);

            TransformManager transformManager = variantScope.getTransformManager();

            for (TransformStream stream : transformManager.getStreams(StreamFilter.DEX)) {
                // TODO Optimize to avoid creating too many actions
                packageAtom.dependsOn(tasks, stream.getDependencies());
            }

            for (TransformStream stream : transformManager.getStreams(StreamFilter.RESOURCES)) {
                // TODO Optimize to avoid creating too many actions
                packageAtom.dependsOn(tasks, stream.getDependencies());
            }
            for (TransformStream stream : transformManager.getStreams(StreamFilter.NATIVE_LIBS)) {
                // TODO Optimize to avoid creating too many actions
                packageAtom.dependsOn(tasks, stream.getDependencies());
            }
            packageAtom.dependsOn(tasks, generateAtomMetadata);
        }
    }

    @NonNull
    @Override
    protected Set<QualifiedContent.Scope> getResMergingScopes(@NonNull VariantScope variantScope) {
        return TransformManager.SCOPE_FULL_PROJECT;
    }

    private Task getAssembleDefault() {
        if (assembleDefault == null) {
            assembleDefault = project.getTasks().findByName("assembleDefaultAtom");
        }
        return assembleDefault;
    }
}
