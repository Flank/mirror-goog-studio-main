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
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.scope.AndroidTask;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.AtomVariantData;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.tasks.AndroidJarTask;
import com.android.build.gradle.tasks.BundleAtom;
import com.android.build.gradle.tasks.ManifestProcessorTask;
import com.android.build.gradle.tasks.MergeManifests;
import com.android.build.gradle.tasks.MergeResources;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.model.SyncIssue;
import com.android.builder.profile.Recorder;
import com.android.manifmerger.ManifestMerger2;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan.ExecutionType;
import java.util.Set;
import org.gradle.api.Project;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/**
 * TaskManager for creating tasks in an Android Atom project.
 */
public class AtomTaskManager extends TaskManager {

    public AtomTaskManager(
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
        createAnchorTasks(tasks, variantScope);
        createCheckManifestTask(tasks, variantScope);

        // Create all current streams (dependencies mostly at this point)
        createDependencyStreams(tasks, variantScope);

        // Add a task to process the manifest(s)
        recorder.record(
                ExecutionType.ATOM_TASK_MANAGER_CREATE_MERGE_MANIFEST_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createMergeApkManifestsTask(tasks, variantScope));

        // Add a task to create the res values
        recorder.record(
                ExecutionType.ATOM_TASK_MANAGER_CREATE_GENERATE_RES_VALUES_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createGenerateResValuesTask(tasks, variantScope));

        // Add a task to compile renderscript files.
        recorder.record(
                ExecutionType.ATOM_TASK_MANAGER_CREATE_CREATE_RENDERSCRIPT_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createRenderscriptTask(tasks, variantScope));

        // Create a merge task to merge the resources from this atom and its dependencies. This
        // will get packaged in the atombundle.
        recorder.record(
                ExecutionType.ATOM_TASK_MANAGER_CREATE_MERGE_RESOURCES_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> {
                    AndroidTask<MergeResources> mergeResourcesTask =
                            createMergeResourcesTask(tasks, variantScope);

                    // Publish intermediate resources folder.
                    variantScope.publishIntermediateArtifact(
                            variantScope.getMergeResourcesOutputDir(),
                            mergeResourcesTask.getName(),
                            AndroidArtifacts.ArtifactType.ATOM_ANDROID_RES);
                });

        // Add a task to merge the assets folders
        recorder.record(
                ExecutionType.ATOM_TASK_MANAGER_CREATE_MERGE_ASSETS_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () ->
                        createMergeAssetsTask(
                                tasks,
                                variantScope,
                                (task, outputDir) ->
                                        variantScope.publishIntermediateArtifact(
                                                outputDir,
                                                task.getName(),
                                                AndroidArtifacts.ArtifactType.ATOM_ASSETS)));

        // Add a task to create the BuildConfig class
        recorder.record(
                ExecutionType.ATOM_TASK_MANAGER_CREATE_BUILD_CONFIG_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createBuildConfigTask(tasks, variantScope));

        recorder.record(
                ExecutionType.ATOM_TASK_MANAGER_CREATE_PROCESS_RES_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> {
                    createProcessResTask(
                            tasks,
                            variantScope,
                            () ->
                                    FileUtils.join(
                                            variantScope.getGlobalScope().getIntermediatesDir(),
                                            "symbols",
                                            variantScope
                                                    .getVariantData()
                                                    .getVariantConfiguration()
                                                    .getDirName()),
                            variantScope.getProcessResourcePackageOutputDirectory(),
                            MergeType.MERGE,
                            variantScope.getGlobalScope().getArchivesBaseName());

                    // process java resources
                    createProcessJavaResTask(tasks, variantScope);
                    createMergeJavaResTransform(tasks, variantScope);

                    variantScope.publishIntermediateArtifact(
                            variantScope.getProcessResourcePackageOutputDirectory(),
                            variantScope.getProcessResourcesTask().getName(),
                            AndroidArtifacts.ArtifactType.ATOM_RESOURCE_PKG);
                    variantScope.publishIntermediateArtifact(
                            variantScope.getLibInfoFile(),
                            variantScope.getProcessResourcesTask().getName(),
                            AndroidArtifacts.ArtifactType.ATOM_LIB_INFO);
                });

        recorder.record(
                ExecutionType.ATOM_TASK_MANAGER_CREATE_AIDL_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createAidlTask(tasks, variantScope));

        recorder.record(
                ExecutionType.ATOM_TASK_MANAGER_CREATE_SHADER_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createShaderTask(tasks, variantScope));

        // Add NDK tasks
        BaseVariantData variantData = variantScope.getVariantData();
        if (!isComponentModelPlugin()) {
            recorder.record(
                    ExecutionType.ATOM_TASK_MANAGER_CREATE_NDK_TASK,
                    project.getPath(),
                    variantScope.getFullVariantName(),
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
        recorder.record(
                ExecutionType.ATOM_TASK_MANAGER_CREATE_EXTERNAL_NATIVE_BUILD_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> {
                    createExternalNativeBuildJsonGenerators(variantScope);
                    createExternalNativeBuildTasks(tasks, variantScope);
                });

        // Add a task to merge the jni libs folders
        recorder.record(
                ExecutionType.ATOM_TASK_MANAGER_CREATE_MERGE_JNILIBS_FOLDERS_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createMergeJniLibFoldersTasks(tasks, variantScope));

        // Add a compile task
        recorder.record(
                ExecutionType.ATOM_TASK_MANAGER_CREATE_COMPILE_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> {
                    // create data binding merge task before the javac task so that it can
                    // parse jars before any consumer
                    createDataBindingMergeArtifactsTaskIfNecessary(tasks, variantScope);

                    // First, build the .class files with javac and compile the jar.
                    AndroidTask<? extends JavaCompile> javacTask =
                            createJavacTask(tasks, variantScope);

                    addJavacClassesStream(variantScope);

                    setJavaCompilerTask(javacTask, tasks, variantScope);

                    AndroidTask<AndroidJarTask> jarTask =
                            getAndroidTasks()
                                    .create(
                                            tasks,
                                            new AndroidJarTask.JarClassesConfigAction(
                                                    variantScope));

                    // Then, build the dex with jack if enabled.
                    // TODO: This means recompiling everything twice if jack is enabled.
                    if (variantScope.getVariantConfiguration().isJackEnabled()) {
                        createJackTask(tasks, variantScope);
                    } else {
                        // Prevent the use of java 1.8 without jack, which would otherwise cause an
                        // internal javac error.
                        if (variantScope
                                .getGlobalScope()
                                .getExtension()
                                .getCompileOptions()
                                .getTargetCompatibility()
                                .isJava8Compatible()) {
                            // Only warn for users of retrolambda and dexguard
                            if (project.getPlugins().hasPlugin("me.tatarka.retrolambda")
                                    || project.getPlugins().hasPlugin("dexguard")) {
                                getLogger()
                                        .warn(
                                                "Jack is disabled, but one of the plugins you "
                                                        + "are using supports Java 8 language features.");
                            } else {
                                androidBuilder
                                        .getErrorReporter()
                                        .handleSyncError(
                                                variantScope
                                                        .getVariantConfiguration()
                                                        .getFullName(),
                                                SyncIssue
                                                        .TYPE_JACK_REQUIRED_FOR_JAVA_8_LANGUAGE_FEATURES,
                                                "Jack is required to support java 8 language features. "
                                                        + "Either enable Jack or remove "
                                                        + "sourceCompatibility JavaVersion.VERSION_1_8.");
                            }
                        }
                        createPostCompilationTasks(tasks, variantScope);
                    }
                    // TODO: Publish an obfuscated JAR instead.
                    variantScope.publishIntermediateArtifact(
                            javacTask.get(tasks).getDestinationDir(),
                            jarTask.getName(),
                            AndroidArtifacts.ArtifactType.ATOM_CLASSES);
                });

        // Add data binding tasks if enabled
        createDataBindingTasksIfNecessary(tasks, variantScope);

        createStripNativeLibraryTask(tasks, variantScope);

        recorder.record(
                ExecutionType.ATOM_TASK_MANAGER_CREATE_BUNDLING_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createAtomBundlingTasks(tasks, variantScope));

        // create the lint tasks.
        recorder.record(
                ExecutionType.ATOM_TASK_MANAGER_CREATE_LINT_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createLintTasks(tasks, variantScope));
    }

    private void createAtomBundlingTasks(
            @NonNull TaskFactory tasks,
            @NonNull final VariantScope variantScope) {
        final AtomVariantData variantData = (AtomVariantData) variantScope.getVariantData();

        // Create the bundle task.
        AndroidTask<BundleAtom> bundleAtom =
                getAndroidTasks().create(tasks,
                        new BundleAtom.ConfigAction(variantScope));

        bundleAtom.dependsOn(tasks, variantScope.getMergeAssetsTask());
        bundleAtom.dependsOn(tasks, variantScope.getProcessResourcesTask());
        bundleAtom.dependsOn(tasks, variantData.binaryFileProviderTask);

        bundleAtom.optionalDependsOn(
                tasks,
                variantScope.getShrinkResourcesTask(),
                // TODO: When Jack is converted, add activeDexTask to VariantScope.
                variantScope.getJavaCompilerTask(),
                // TODO: Remove when Jack is converted to AndroidTask.
                variantData.javaCompilerTask);

        variantScope.getAssembleTask().dependsOn(tasks, bundleAtom);

        variantScope.publishIntermediateArtifact(
                bundleAtom.get(tasks).getDexBundleFolder(),
                bundleAtom.getName(),
                AndroidArtifacts.ArtifactType.ATOM_DEX);
        variantScope.publishIntermediateArtifact(
                bundleAtom.get(tasks).getLibBundleFolder(),
                bundleAtom.getName(),
                AndroidArtifacts.ArtifactType.ATOM_JNI);
        variantScope.publishIntermediateArtifact(
                bundleAtom.get(tasks).getJavaResBundleFolder(),
                bundleAtom.getName(),
                AndroidArtifacts.ArtifactType.ATOM_JAVA_RES);
    }

    /** Creates the merge manifests task. */
    @Override
    @NonNull
    protected AndroidTask<? extends ManifestProcessorTask> createMergeManifestTask(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope variantScope,
            @NonNull ImmutableList.Builder<ManifestMerger2.Invoker.Feature> optionalFeatures) {
        if (getIncrementalMode(variantScope.getVariantConfiguration()) != IncrementalMode.NONE) {
            optionalFeatures.add(ManifestMerger2.Invoker.Feature.INSTANT_RUN_REPLACEMENT);
        }

        AndroidTask<? extends ManifestProcessorTask> mergeManifestsAndroidTask;
        mergeManifestsAndroidTask =
                androidTasks.create(
                        tasks,
                        new MergeManifests.ConfigAction(variantScope, optionalFeatures.build()));

        variantScope.addTaskOutput(
                VariantScope.TaskOutputType.INSTANT_RUN_MERGED_MANIFESTS,
                variantScope.getInstantRunManifestOutputDirectory(),
                mergeManifestsAndroidTask.getName());

        variantScope.publishIntermediateArtifact(
                variantScope.getManifestOutputDirectory(),
                mergeManifestsAndroidTask.getName(),
                AndroidArtifacts.ArtifactType.ATOM_MANIFEST);

        return mergeManifestsAndroidTask;
    }

    @NonNull
    @Override
    protected Set<QualifiedContent.Scope> getResMergingScopes(@NonNull VariantScope variantScope) {
        return TransformManager.SCOPE_FULL_PROJECT;
    }
}
