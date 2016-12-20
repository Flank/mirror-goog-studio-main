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
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.scope.AndroidTask;
import com.android.build.gradle.internal.scope.VariantOutputScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.AtomVariantData;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.build.gradle.tasks.AndroidJarTask;
import com.android.build.gradle.tasks.BundleAtom;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.VariantConfiguration;
import com.android.builder.model.SyncIssue;
import com.android.builder.profile.Recorder;
import com.android.utils.FileUtils;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan.ExecutionType;
import java.io.File;
import java.util.Set;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

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
        assert variantData instanceof AtomVariantData;

        final VariantScope variantScope = variantData.getScope();
        final File variantBundleDir = variantScope.getBaseBundleDir();

        createAnchorTasks(tasks, variantScope);
        createCheckManifestTask(tasks, variantScope);

        // Create all current streams (dependencies mostly at this point)
        createDependencyStreams(tasks, variantScope);

        // Add a task to process the manifest(s)
        recorder.record(
                ExecutionType.ATOM_TASK_MANAGER_CREATE_MERGE_MANIFEST_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createMergeAppManifestsTask(tasks, variantScope));

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
                (Recorder.VoidBlock) () -> createMergeResourcesTask(tasks, variantScope));

        // Add a task to merge the assets folders
        recorder.record(
                ExecutionType.ATOM_TASK_MANAGER_CREATE_MERGE_ASSETS_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createMergeAssetsTask(tasks, variantScope));

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
                    // If this is the base atom, compile the .ap_ that will get packaged in the
                    // atombundle.
                    // If this is not the base atom, add a task to generate the resource source
                    // files, directing the location of the r.txt file to be directly in the
                    // atombundle.
                    createProcessResTask(
                            tasks,
                            variantScope,
                            () -> {
                                boolean isBaseAtom = variantScope
                                        .getVariantConfiguration()
                                        .getPackageDependencies()
                                        .getBaseAtom() == null;
                                return isBaseAtom
                                        ? FileUtils.join(
                                                variantScope.getGlobalScope().getIntermediatesDir(),
                                                "symbols",
                                                variantScope.getVariantData().getVariantConfiguration().getDirName())
                                        : variantBundleDir;
                            },
                            (vod) -> {
                                boolean isBaseAtom = variantScope
                                        .getVariantConfiguration()
                                        .getPackageDependencies()
                                        .getBaseAtom() == null;
                                return isBaseAtom
                                        ? vod.getProcessResourcePackageOutputFile()
                                        : null;
                            });

                    // process java resources
                    createProcessJavaResTasks(tasks, variantScope);
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
        if (!isComponentModelPlugin) {
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

                    getAndroidTasks()
                            .create(tasks, new AndroidJarTask.JarClassesConfigAction(variantScope));

                    // Then, build the dex with jack if enabled.
                    // TODO: This means recompiling everything twice if jack is enabled.
                    if (variantData.getVariantConfiguration().isJackEnabled()) {
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
        final TransformManager transformManager = variantScope.getTransformManager();

        // Get the single output.
        final VariantOutputScope variantOutputScope = variantData.getOutputs().get(0).getScope();
        variantOutputScope.setAssembleTask(variantScope.getAssembleTask());

        // Create the bundle task.
        AndroidTask<BundleAtom> bundleAtom =
                getAndroidTasks().create(tasks,
                        new BundleAtom.ConfigAction(variantScope));
        variantOutputScope.getVariantOutputData().bundleAtomTask = bundleAtom.get(tasks);

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

        variantScope.getAssembleTask().dependsOn(tasks, bundleAtom);

        String classifier =
                variantData.getVariantDependency().getPublishConfiguration().getName();
        bundleAtom.configure(tasks, packageTask -> project.getArtifacts().add(classifier,
                AndroidArtifacts.buildAtomArtifact(
                        getGlobalScope().getProjectBaseName(),
                        classifier,
                        packageTask)));
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
