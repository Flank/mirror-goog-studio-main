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
import com.android.build.gradle.internal.dsl.CoreJackOptions;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.build.gradle.internal.pipeline.StreamFilter;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.pipeline.TransformTask;
import com.android.build.gradle.internal.profile.SpanRecorders;
import com.android.build.gradle.internal.publishing.AtomPublishArtifact;
import com.android.build.gradle.internal.scope.AndroidTask;
import com.android.build.gradle.internal.scope.VariantOutputScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.AtomVariantData;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.build.gradle.internal.variant.VariantHelper;
import com.android.build.gradle.tasks.AndroidJarTask;
import com.android.build.gradle.tasks.BundleAtom;
import com.android.build.gradle.tasks.GenerateAtomMetadata;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.VariantConfiguration;
import com.android.builder.model.SyncIssue;
import com.google.wireless.android.sdk.stats.AndroidStudioStats.GradleBuildProfileSpan.ExecutionType;

import org.gradle.api.Project;
import org.gradle.api.Task;
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

    public AtomTaskManager(
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
        final File variantBundleDir = variantScope.getBaseBundleDir();

        createAnchorTasks(tasks, variantScope);
        createCheckManifestTask(tasks, variantScope);

        // Create all current streams (dependencies mostly at this point)
        createDependencyStreams(tasks, variantScope);

        // Add a task to process the manifest(s)
        SpanRecorders.record(
                variantScope,
                ExecutionType.ATOM_TASK_MANAGER_CREATE_MERGE_MANIFEST_TASK,
                () -> createMergeLibManifestsTask(tasks, variantScope));

        // Add a task to create the res values
        SpanRecorders.record(
                variantScope,
                ExecutionType.ATOM_TASK_MANAGER_CREATE_GENERATE_RES_VALUES_TASK,
                () -> createGenerateResValuesTask(tasks, variantScope));

        // Add a task to compile renderscript files.
        SpanRecorders.record(
                variantScope,
                ExecutionType.ATOM_TASK_MANAGER_CREATE_CREATE_RENDERSCRIPT_TASK,
                () -> createRenderscriptTask(tasks, variantScope));

        // Create a merge task to merge the resources from this atom and its dependencies. This
        // will get packaged in the atombundle.
        SpanRecorders.record(
                variantScope,
                ExecutionType.ATOM_TASK_MANAGER_CREATE_MERGE_RESOURCES_TASK,
                () -> createMergeResourcesTask(tasks, variantScope));

        // Add a task to merge the assets folders
        SpanRecorders.record(
                variantScope,
                ExecutionType.ATOM_TASK_MANAGER_CREATE_MERGE_ASSETS_TASK,
                () -> createMergeAssetsTask(tasks, variantScope));

        // Add a task to create the BuildConfig class
        SpanRecorders.record(
                variantScope,
                ExecutionType.ATOM_TASK_MANAGER_CREATE_BUILD_CONFIG_TASK,
                () -> createBuildConfigTask(tasks, variantScope));

        SpanRecorders.record(
                variantScope,
                ExecutionType.ATOM_TASK_MANAGER_CREATE_PROCESS_RES_TASK,
                () -> {
                    if (variantScope.getVariantConfiguration().getPackageDependencies()
                            .getBaseAtom() == null) {
                        // If this is the base atom, compile the .ap_ that will get packaged in the atombundle.
                        createApkProcessResTask(tasks, variantScope);
                    } else {
                        // If this is not the base atom, add a task to generate the resource source files,
                        // directing the location of the r.txt file to be directly in the atombundle.
                        createProcessResTask(tasks, variantScope, variantBundleDir,
                                false /*generateResourcePackage*/);
                    }

                    // process java resources
                    createProcessJavaResTasks(tasks, variantScope);
                });

        SpanRecorders.record(
                variantScope,
                ExecutionType.ATOM_TASK_MANAGER_CREATE_AIDL_TASK,
                () -> createAidlTask(tasks, variantScope));

        SpanRecorders.record(
                variantScope,
                ExecutionType.ATOM_TASK_MANAGER_CREATE_SHADER_TASK,
                () -> createShaderTask(tasks, variantScope));

        // Add NDK tasks
        if (!isComponentModelPlugin) {
            SpanRecorders.record(
                    variantScope,
                    ExecutionType.ATOM_TASK_MANAGER_CREATE_NDK_TASK,
                    () -> createNdkTasks(tasks, variantScope));
        } else {
            if (variantData.compileTask != null) {
                variantData.compileTask.dependsOn(getNdkBuildable(variantData));
            } else {
                variantScope.getCompileTask().dependsOn(tasks, getNdkBuildable(variantData));
            }
        }
        variantScope.setNdkBuildable(getNdkBuildable(variantData));

        // Add external native build tasks
        SpanRecorders.record(
                variantScope,
                ExecutionType.ATOM_TASK_MANAGER_CREATE_EXTERNAL_NATIVE_BUILD_TASK,
                () -> {
                    createExternalNativeBuildJsonGenerators(variantScope);
                    createExternalNativeBuildTasks(tasks, variantScope);
                });

        // Add a task to merge the jni libs folders
        SpanRecorders.record(
                variantScope,
                ExecutionType.ATOM_TASK_MANAGER_CREATE_MERGE_JNILIBS_FOLDERS_TASK,
                () -> createMergeJniLibFoldersTasks(tasks, variantScope));

        // Add a compile task
        SpanRecorders.record(
                variantScope,
                ExecutionType.ATOM_TASK_MANAGER_CREATE_COMPILE_TASK,
                () -> {
                    // First, build the .class files with javac and compile the jar.
                    AndroidTask<? extends JavaCompile> javacTask =
                            createJavacTask(tasks, variantScope);

                    addJavacClassesStream(variantScope);

                    setJavaCompilerTask(javacTask, tasks, variantScope);

                    getAndroidTasks().create(tasks,
                            new AndroidJarTask.JarClassesConfigAction(variantScope));

                    // Then, build the dex with jack if enabled.
                    // TODO: This means recompiling everything twice if jack is enabled.
                    CoreJackOptions jackOptions =
                            variantData.getVariantConfiguration().getJackOptions();
                    if (jackOptions.isEnabled()) {
                        AndroidTask<TransformTask> jackTask =
                                createJackTask(tasks, variantScope, true /*compileJavaSource*/);
                        setJavaCompilerTask(jackTask, tasks, variantScope);
                    } else {
                        // Prevent the use of java 1.8 without jack, which would otherwise cause an
                        // internal javac error.
                        if (variantScope.getGlobalScope().getExtension().getCompileOptions()
                                .getTargetCompatibility().isJava8Compatible()) {
                            // Only warn for users of retrolambda and dexguard
                            if (project.getPlugins().hasPlugin("me.tatarka.retrolambda")
                                    || project.getPlugins().hasPlugin("dexguard")) {
                                getLogger().warn("Jack is disabled, but one of the plugins you "
                                        + "are using supports Java 8 language features.");
                            } else {
                                androidBuilder.getErrorReporter().handleSyncError(
                                        variantScope.getVariantConfiguration().getFullName(),
                                        SyncIssue.TYPE_JACK_REQUIRED_FOR_JAVA_8_LANGUAGE_FEATURES,
                                        "Jack is required to support java 8 language features. "
                                                + "Either enable Jack or remove "
                                                + "sourceCompatibility JavaVersion.VERSION_1_8.");
                            }
                        }
                        createPostCompilationTasks(tasks, variantScope);
                    }
                });

        // Add data binding tasks if enabled
        createDataBindingTasksIfNecessary(tasks, variantScope);

        createStripNativeLibraryTask(tasks, variantScope);

        SpanRecorders.record(
                variantScope,
                ExecutionType.ATOM_TASK_MANAGER_CREATE_BUNDLING_TASK,
                () -> createAtomBundlingTasks(tasks, variantScope));

        // create the lint tasks.
        SpanRecorders.record(
                variantScope,
                ExecutionType.ATOM_TASK_MANAGER_CREATE_LINT_TASK,
                () -> createLintTasks(tasks, variantScope));
    }

    private void createAtomBundlingTasks(
            @NonNull TaskFactory tasks,
            @NonNull final VariantScope variantScope) {
        final AtomVariantData variantData = (AtomVariantData) variantScope.getVariantData();
        final TransformManager transformManager = variantScope.getTransformManager();

        // Get the single output.
        final VariantOutputScope variantOutputScope = variantData.getOutputs().get(0).getScope();
        variantOutputScope.setAssembleTask(variantScope.getAssembleTask());

        // Create the task to generate the atom metadata.
        AndroidTask<GenerateAtomMetadata> generateAtomMetadata =
                getAndroidTasks().create(tasks,
                        new GenerateAtomMetadata.ConfigAction(variantOutputScope));

        // Create the bundle task.
        AndroidTask<BundleAtom> bundleAtom =
                getAndroidTasks().create(tasks,
                        new BundleAtom.ConfigAction(variantScope));
        variantOutputScope.getVariantOutputData().bundleAtomTask = bundleAtom.get(tasks);

        bundleAtom.dependsOn(tasks, generateAtomMetadata);
        bundleAtom.dependsOn(tasks, variantScope.getMergeAssetsTask());
        bundleAtom.dependsOn(tasks, variantOutputScope.getProcessResourcesTask());
        bundleAtom.dependsOn(tasks, variantData.binaryFileProviderTask);

        bundleAtom.optionalDependsOn(
                tasks,
                variantOutputScope.getShrinkResourcesTask(),
                // TODO: When Jack is converted, add activeDexTask to VariantScope.
                variantOutputScope.getVariantScope().getJavaCompilerTask(),
                // TODO: Remove when Jack is converted to AndroidTask.
                variantData.javaCompilerTask);

        // TODO Optimize to avoid creating too many actions
        transformManager.getStreams(StreamFilter.RESOURCES)
                .forEach(stream -> bundleAtom.dependsOn(tasks, stream.getDependencies()));
        // TODO Optimize to avoid creating too many actions
        transformManager.getStreams(StreamFilter.DEX)
                .forEach(stream -> bundleAtom.dependsOn(tasks, stream.getDependencies()));
        // TODO Optimize to avoid creating too many actions
        transformManager.getStreams(StreamFilter.NATIVE_LIBS)
                .forEach(stream -> bundleAtom.dependsOn(tasks, stream.getDependencies()));

        variantScope.getAssembleTask().dependsOn(tasks, bundleAtom);

        VariantConfiguration variantConfig = variantScope.getVariantConfiguration();
        if (getExtension().getDefaultPublishConfig().equals(variantConfig.getFullName())) {
            VariantHelper.setupDefaultConfig(project,
                    variantData.getVariantDependency().getPackageConfiguration());

            // add the artifact that will be published
            bundleAtom.configure(tasks, packageTask -> project.getArtifacts().add("default",
                    new AtomPublishArtifact(
                            getGlobalScope().getProjectBaseName(),
                            null,
                            packageTask)));

            getAssembleDefault().dependsOn(variantScope.getAssembleTask().getName());
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
