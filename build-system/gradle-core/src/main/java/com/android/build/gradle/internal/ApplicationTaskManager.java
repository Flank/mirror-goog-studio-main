/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ARTIFACT_TYPE;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.APK;
import static com.android.build.gradle.internal.scope.InternalArtifactType.JAVAC;
import static com.android.builder.model.AndroidProject.FD_INTERMEDIATES;

import android.databinding.tool.DataBindingBuilder;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.internal.aapt.AaptGeneration;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.DslAdaptersKt;
import com.android.build.gradle.internal.incremental.BuildInfoWriterTask;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.pipeline.TransformTask;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.TaskOutputHolder;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.AppPreBuildTask;
import com.android.build.gradle.internal.tasks.ApplicationId;
import com.android.build.gradle.internal.tasks.ApplicationIdWriterTask;
import com.android.build.gradle.internal.tasks.TestPreBuildTask;
import com.android.build.gradle.internal.transforms.InstantRunDependenciesApkBuilder;
import com.android.build.gradle.internal.transforms.InstantRunSliceSplitApkBuilder;
import com.android.build.gradle.internal.variant.ApplicationVariantData;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.MultiOutputPolicy;
import com.android.build.gradle.options.OptionalBooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.tasks.BuildArtifactReportTask;
import com.android.build.gradle.tasks.MainApkListPersistence;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.profile.Recorder;
import com.android.utils.FileUtils;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan.ExecutionType;
import java.io.File;
import java.util.Optional;
import java.util.Set;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/**
 * TaskManager for creating tasks in an Android application project.
 */
public class ApplicationTaskManager extends TaskManager {

    public ApplicationTaskManager(
            @NonNull GlobalScope globalScope,
            @NonNull Project project,
            @NonNull ProjectOptions projectOptions,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull DataBindingBuilder dataBindingBuilder,
            @NonNull AndroidConfig extension,
            @NonNull SdkHandler sdkHandler,
            @NonNull ToolingModelBuilderRegistry toolingRegistry,
            @NonNull Recorder recorder) {
        super(
                globalScope,
                project,
                projectOptions,
                androidBuilder,
                dataBindingBuilder,
                extension,
                sdkHandler,
                toolingRegistry,
                recorder);
    }

    @Override
    public void createTasksForVariantScope(@NonNull final VariantScope variantScope) {
        BaseVariantData variantData = variantScope.getVariantData();
        assert variantData instanceof ApplicationVariantData;

        createAnchorTasks(variantScope);
        createCheckManifestTask(variantScope);

        handleMicroApp(variantScope);

        // Create all current streams (dependencies mostly at this point)
        createDependencyStreams(variantScope);

        // Add a task to publish the applicationId.
        createApplicationIdWriterTask(variantScope);

        taskFactory.create(new MainApkListPersistence.ConfigAction(variantScope));
        taskFactory.create(new BuildArtifactReportTask.ConfigAction(variantScope));

        // Add a task to process the manifest(s)
        recorder.record(
                ExecutionType.APP_TASK_MANAGER_CREATE_MERGE_MANIFEST_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createMergeApkManifestsTask(variantScope));

        // Add a task to create the res values
        recorder.record(
                ExecutionType.APP_TASK_MANAGER_CREATE_GENERATE_RES_VALUES_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createGenerateResValuesTask(variantScope));

        // Add a task to compile renderscript files.
        recorder.record(
                ExecutionType.APP_TASK_MANAGER_CREATE_CREATE_RENDERSCRIPT_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createRenderscriptTask(variantScope));

        // Add a task to merge the resource folders
        recorder.record(
                ExecutionType.APP_TASK_MANAGER_CREATE_MERGE_RESOURCES_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                (Recorder.VoidBlock) () -> createMergeResourcesTask(variantScope, true));

        // Add tasks to compile shader
        recorder.record(
                ExecutionType.APP_TASK_MANAGER_CREATE_SHADER_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createShaderTask(variantScope));


        // Add a task to merge the asset folders
        recorder.record(
                ExecutionType.APP_TASK_MANAGER_CREATE_MERGE_ASSETS_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> {
                    createMergeAssetsTask(variantScope);
                });

        // Add a task to create the BuildConfig class
        recorder.record(
                ExecutionType.APP_TASK_MANAGER_CREATE_BUILD_CONFIG_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createBuildConfigTask(variantScope));

        recorder.record(
                ExecutionType.APP_TASK_MANAGER_CREATE_PROCESS_RES_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> {
                    // Add a task to process the Android Resources and generate source files
                    createApkProcessResTask(variantScope);

                    // Add a task to process the java resources
                    createProcessJavaResTask(variantScope);
                });

        recorder.record(
                ExecutionType.APP_TASK_MANAGER_CREATE_AIDL_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createAidlTask(variantScope));

        // Add NDK tasks
        if (!isComponentModelPlugin()) {
            recorder.record(
                    ExecutionType.APP_TASK_MANAGER_CREATE_NDK_TASK,
                    project.getPath(),
                    variantScope.getFullVariantName(),
                    () -> createNdkTasks(variantScope));
        } else {
            if (variantData.compileTask != null) {
                variantData.compileTask.dependsOn(getNdkBuildable(variantData));
            } else {
                variantScope.getCompileTask().dependsOn(getNdkBuildable(variantData));
            }
        }
        variantScope.setNdkBuildable(getNdkBuildable(variantData));

        // Add external native build tasks

        recorder.record(
                ExecutionType.APP_TASK_MANAGER_CREATE_EXTERNAL_NATIVE_BUILD_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> {
                    createExternalNativeBuildJsonGenerators(variantScope);
                    createExternalNativeBuildTasks(variantScope);
                });

        // Add a task to merge the jni libs folders
        recorder.record(
                ExecutionType.APP_TASK_MANAGER_CREATE_MERGE_JNILIBS_FOLDERS_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createMergeJniLibFoldersTasks(variantScope));

        // Add data binding tasks if enabled
        createDataBindingTasksIfNecessary(variantScope, MergeType.MERGE);

        // Add a compile task
        recorder.record(
                ExecutionType.APP_TASK_MANAGER_CREATE_COMPILE_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createCompileTask(variantScope));

        createStripNativeLibraryTask(taskFactory, variantScope);

        if (variantScope.getVariantData().getMultiOutputPolicy().equals(MultiOutputPolicy.SPLITS)) {
            if (extension.getBuildToolsRevision().getMajor() < 21) {
                throw new RuntimeException(
                        "Pure splits can only be used with buildtools 21 and later");
            }

            recorder.record(
                    ExecutionType.APP_TASK_MANAGER_CREATE_SPLIT_TASK,
                    project.getPath(),
                    variantScope.getFullVariantName(),
                    () -> createSplitTasks(variantScope));
        }

        recorder.record(
                ExecutionType.APP_TASK_MANAGER_CREATE_PACKAGING_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> {
                    BuildInfoWriterTask buildInfoWriterTask =
                            createInstantRunPackagingTasks(variantScope);
                    createPackagingTask(variantScope, buildInfoWriterTask);
                });

        // create the lint tasks.
        recorder.record(
                ExecutionType.APP_TASK_MANAGER_CREATE_LINT_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createLintTasks(variantScope));
    }

    /** Create tasks related to creating pure split APKs containing sharded dex files. */
    @Nullable
    private BuildInfoWriterTask createInstantRunPackagingTasks(@NonNull VariantScope variantScope) {

        if (!variantScope.getInstantRunBuildContext().isInInstantRunMode()
                || variantScope.getInstantRunTaskManager() == null) {
            return null;
        }

        BuildInfoWriterTask buildInfoGeneratorTask =
                taskFactory.create(new BuildInfoWriterTask.ConfigAction(variantScope, getLogger()));

        variantScope.getInstantRunTaskManager()
                        .configureBuildInfoWriterTask(buildInfoGeneratorTask);

        InternalArtifactType resourcesWithMainManifest =
                variantScope.getInstantRunBuildContext().useSeparateApkForResources()
                        ? InternalArtifactType.INSTANT_RUN_MAIN_APK_RESOURCES
                        : InternalArtifactType.PROCESSED_RES;

        // create the transforms that will create the dependencies apk.
        InstantRunDependenciesApkBuilder dependenciesApkBuilder =
                new InstantRunDependenciesApkBuilder(
                        getLogger(),
                        project,
                        variantScope.getInstantRunBuildContext(),
                        variantScope.getGlobalScope().getAndroidBuilder(),
                        variantScope.getVariantConfiguration().getApplicationId(),
                        variantScope.getVariantConfiguration().getSigningConfig(),
                        AaptGeneration.fromProjectOptions(projectOptions),
                        DslAdaptersKt.convert(globalScope.getExtension().getAaptOptions()),
                        new File(variantScope.getInstantRunSplitApkOutputFolder(), "dep"),
                        new File(
                                variantScope.getIncrementalDir("ir_dep"),
                                variantScope.getDirName()),
                        new File(
                                getIncrementalFolder(
                                        variantScope, "InstantRunDependenciesApkBuilder"),
                                "aapt-temp"),
                        variantScope.getOutput(InternalArtifactType.PROCESSED_RES),
                        variantScope.getOutput(resourcesWithMainManifest),
                        variantScope.getOutput(InternalArtifactType.APK_LIST),
                        variantScope.getOutputScope().getMainSplit());

        Optional<TransformTask> dependenciesApkBuilderTask =
                variantScope
                        .getTransformManager()
                        .addTransform(taskFactory, variantScope, dependenciesApkBuilder);

        dependenciesApkBuilderTask.ifPresent(
                task -> task.dependsOn(getValidateSigningTask(variantScope)));

        // and now the transform that will create a split FULL_APK for each slice.
        InstantRunSliceSplitApkBuilder slicesApkBuilder =
                new InstantRunSliceSplitApkBuilder(
                        getLogger(),
                        project,
                        variantScope.getInstantRunBuildContext(),
                        variantScope.getGlobalScope().getAndroidBuilder(),
                        variantScope.getVariantConfiguration().getApplicationId(),
                        variantScope.getVariantConfiguration().getSigningConfig(),
                        AaptGeneration.fromProjectOptions(projectOptions),
                        DslAdaptersKt.convert(globalScope.getExtension().getAaptOptions()),
                        new File(variantScope.getInstantRunSplitApkOutputFolder(), "slices"),
                        getIncrementalFolder(variantScope, "ir_slices"),
                        new File(
                                getIncrementalFolder(
                                        variantScope, "InstantRunSliceSplitApkBuilder"),
                                "aapt-temp"),
                        globalScope.getProjectOptions().get(OptionalBooleanOption.SERIAL_AAPT2),
                        variantScope.getOutput(InternalArtifactType.PROCESSED_RES),
                        variantScope.getOutput(resourcesWithMainManifest),
                        variantScope.getOutput(InternalArtifactType.APK_LIST),
                        variantScope.getOutputScope().getMainSplit());

        Optional<TransformTask> transformTaskAndroidTask =
                variantScope
                        .getTransformManager()
                        .addTransform(taskFactory, variantScope, slicesApkBuilder);

        if (transformTaskAndroidTask.isPresent()) {
            TransformTask splitApk = transformTaskAndroidTask.get();
            splitApk.dependsOn(getValidateSigningTask(variantScope));
            variantScope.getAssembleTask().dependsOn(splitApk);
            buildInfoGeneratorTask.mustRunAfter(splitApk.getName());
        }

        // if the assembleVariant task run, make sure it also runs the task to generate
        // the build-info.xml.
        variantScope.getAssembleTask().dependsOn(buildInfoGeneratorTask);
        return buildInfoGeneratorTask;
    }

    @Override
    protected void postJavacCreation(@NonNull VariantScope scope) {
        final FileCollection javacOutput = scope.getOutput(JAVAC);
        final FileCollection preJavacGeneratedBytecode =
                scope.getVariantData().getAllPreJavacGeneratedBytecode();
        final FileCollection postJavacGeneratedBytecode =
                scope.getVariantData().getAllPostJavacGeneratedBytecode();

        // Create the classes artifact for uses by external test modules.
        File dest =
                new File(
                        globalScope.getBuildDir(),
                        FileUtils.join(
                                FD_INTERMEDIATES,
                                "classes-jar",
                                scope.getVariantConfiguration().getDirName()));

        Jar task =
                taskFactory.create(
                        new TaskConfigAction<Jar>() {
                            @NonNull
                            @Override
                            public String getName() {
                                return scope.getTaskName("bundleAppClasses");
                            }

                            @NonNull
                            @Override
                            public Class<Jar> getType() {
                                return Jar.class;
                            }

                            @Override
                            public void execute(@NonNull Jar task) {
                                task.from(javacOutput);
                                task.from(preJavacGeneratedBytecode);
                                task.from(postJavacGeneratedBytecode);
                                task.setDestinationDir(dest);
                                task.setArchiveName("classes.jar");
                            }
                        });

        scope.addTaskOutput(
                InternalArtifactType.APP_CLASSES, new File(dest, "classes.jar"), task.getName());

        // create a lighter weight version for usage inside the same module (unit tests basically)
        ConfigurableFileCollection fileCollection =
                scope.createAnchorOutput(TaskOutputHolder.AnchorOutputType.ALL_CLASSES);
        fileCollection.from(javacOutput);
        fileCollection.from(preJavacGeneratedBytecode);
        fileCollection.from(postJavacGeneratedBytecode);
    }

    @Override
    protected DefaultTask createVariantPreBuildTask(@NonNull VariantScope scope) {
        switch (scope.getVariantConfiguration().getType()) {
            case APK:
                return taskFactory.create(new AppPreBuildTask.ConfigAction(scope));
            case ANDROID_TEST:
                return taskFactory.create(new TestPreBuildTask.ConfigAction(scope));
            default:
                return super.createVariantPreBuildTask(scope);
        }
    }

    @NonNull
    @Override
    protected Set<Scope> getResMergingScopes(@NonNull VariantScope variantScope) {
        return TransformManager.SCOPE_FULL_PROJECT;
    }

    /** Configure variantData to generate embedded wear application. */
    private void handleMicroApp(@NonNull VariantScope scope) {
        BaseVariantData variantData = scope.getVariantData();
        GradleVariantConfiguration variantConfiguration = variantData.getVariantConfiguration();
        Boolean unbundledWearApp = variantConfiguration.getMergedFlavor().getWearAppUnbundled();

        if (!Boolean.TRUE.equals(unbundledWearApp)
                && variantConfiguration.getBuildType().isEmbedMicroApp()) {
            Configuration wearApp = variantData.getVariantDependency().getWearAppConfiguration();
            assert wearApp != null : "Wear app with no wearApp configuration";
            if (!wearApp.getAllDependencies().isEmpty()) {
                Action<AttributeContainer> setApkArtifact =
                        container -> container.attribute(ARTIFACT_TYPE, APK.getType());
                FileCollection files =
                        wearApp.getIncoming()
                                .artifactView(config -> config.attributes(setApkArtifact))
                                .getFiles();
                createGenerateMicroApkDataTask(scope, files);
            }
        } else {
            if (Boolean.TRUE.equals(unbundledWearApp)) {
                createGenerateMicroApkDataTask(scope, null);
            }
        }
    }

    private void createApplicationIdWriterTask(@NonNull VariantScope variantScope) {

        File applicationIdOutputDirectory =
                FileUtils.join(
                        globalScope.getIntermediatesDir(),
                        "applicationId",
                        variantScope.getVariantConfiguration().getDirName());

        ApplicationIdWriterTask writeTask =
                taskFactory.create(
                        new ApplicationIdWriterTask.ConfigAction(
                                variantScope, applicationIdOutputDirectory));

        variantScope.addTaskOutput(
                InternalArtifactType.METADATA_APP_ID_DECLARATION,
                ApplicationId.getOutputFile(applicationIdOutputDirectory),
                writeTask.getName());
    }

    private static File getIncrementalFolder(VariantScope variantScope, String taskName) {
        return new File(variantScope.getIncrementalDir(taskName), variantScope.getDirName());
    }
}
