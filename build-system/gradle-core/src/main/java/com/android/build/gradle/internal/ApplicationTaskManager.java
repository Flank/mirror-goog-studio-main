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

import android.databinding.tool.DataBindingBuilder;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.CoreJackOptions;
import com.android.build.gradle.internal.incremental.BuildInfoWriterTask;
import com.android.build.gradle.internal.incremental.InstantRunPatchingPolicy;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.pipeline.TransformTask;
import com.android.build.gradle.internal.scope.AndroidTask;
import com.android.build.gradle.internal.scope.DefaultGradlePackagingScope;
import com.android.build.gradle.internal.scope.PackagingScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.transforms.InstantRunDependenciesApkBuilder;
import com.android.build.gradle.internal.transforms.InstantRunSliceSplitApkBuilder;
import com.android.build.gradle.internal.variant.ApplicationVariantData;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.build.gradle.internal.variant.SplitHandlingPolicy;
import com.android.build.gradle.tasks.AndroidJarTask;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.model.SyncIssue;
import com.android.builder.profile.Recorder;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan.ExecutionType;
import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/**
 * TaskManager for creating tasks in an Android application project.
 */
public class ApplicationTaskManager extends TaskManager {

    public ApplicationTaskManager(
            @NonNull Project project,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull DataBindingBuilder dataBindingBuilder,
            @NonNull AndroidConfig extension,
            @NonNull SdkHandler sdkHandler,
            @NonNull NdkHandler ndkHandler,
            @NonNull DependencyManager dependencyManager,
            @NonNull ToolingModelBuilderRegistry toolingRegistry,
            @NonNull Recorder recorder) {
        super(
                project,
                androidBuilder,
                dataBindingBuilder,
                extension,
                sdkHandler,
                ndkHandler,
                dependencyManager,
                toolingRegistry,
                recorder);
    }

    @Override
    public void createTasksForVariantData(
            @NonNull final TaskFactory tasks,
            @NonNull final BaseVariantData<? extends BaseVariantOutputData> variantData) {
        assert variantData instanceof ApplicationVariantData;

        final VariantScope variantScope = variantData.getScope();

        createAnchorTasks(tasks, variantScope);
        createCheckManifestTask(tasks, variantScope);

        handleMicroApp(tasks, variantScope);

        // Create all current streams (dependencies mostly at this point)
        createDependencyStreams(tasks, variantScope);

        // Add a task to process the manifest(s)
        recorder.record(
                ExecutionType.APP_TASK_MANAGER_CREATE_MERGE_MANIFEST_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createMergeAppManifestsTask(tasks, variantScope));

        // Add a task to create the res values
        recorder.record(
                ExecutionType.APP_TASK_MANAGER_CREATE_GENERATE_RES_VALUES_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createGenerateResValuesTask(tasks, variantScope));

        // Add a task to compile renderscript files.
        recorder.record(
                ExecutionType.APP_TASK_MANAGER_CREATE_CREATE_RENDERSCRIPT_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createRenderscriptTask(tasks, variantScope));

        // Add a task to merge the resource folders
        recorder.record(
                ExecutionType.APP_TASK_MANAGER_CREATE_MERGE_RESOURCES_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                (Recorder.VoidBlock) () -> createMergeResourcesTask(tasks, variantScope));

        // Add a task to merge the asset folders
        recorder.record(
                ExecutionType.APP_TASK_MANAGER_CREATE_MERGE_ASSETS_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createMergeAssetsTask(tasks, variantScope));

        // Add a task to create the BuildConfig class
        recorder.record(
                ExecutionType.APP_TASK_MANAGER_CREATE_BUILD_CONFIG_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createBuildConfigTask(tasks, variantScope));

        recorder.record(
                ExecutionType.APP_TASK_MANAGER_CREATE_PROCESS_RES_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> {
                    // Add a task to process the Android Resources and generate source files
                    createApkProcessResTask(tasks, variantScope);

                    // Add a task to process the java resources
                    createProcessJavaResTasks(tasks, variantScope);
                });

        recorder.record(
                ExecutionType.APP_TASK_MANAGER_CREATE_AIDL_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createAidlTask(tasks, variantScope));

        recorder.record(
                ExecutionType.APP_TASK_MANAGER_CREATE_SHADER_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createShaderTask(tasks, variantScope));

        // Add NDK tasks
        if (!isComponentModelPlugin) {
            recorder.record(
                    ExecutionType.APP_TASK_MANAGER_CREATE_NDK_TASK,
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
                ExecutionType.APP_TASK_MANAGER_CREATE_EXTERNAL_NATIVE_BUILD_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> {
                    createExternalNativeBuildJsonGenerators(variantScope);
                    createExternalNativeBuildTasks(tasks, variantScope);
                });

        // Add a task to merge the jni libs folders
        recorder.record(
                ExecutionType.APP_TASK_MANAGER_CREATE_MERGE_JNILIBS_FOLDERS_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createMergeJniLibFoldersTasks(tasks, variantScope));

        // Add a compile task
        recorder.record(
                ExecutionType.APP_TASK_MANAGER_CREATE_COMPILE_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> {
                    CoreJackOptions jackOptions =
                            variantData.getVariantConfiguration().getJackOptions();
                    // create data binding merge task before the javac task so that it can
                    // parse jars before any consumer
                    createDataBindingMergeArtifactsTaskIfNecessary(tasks, variantScope);
                    AndroidTask<? extends JavaCompile> javacTask =
                            createJavacTask(tasks, variantScope);
                    if (jackOptions.isEnabled()) {
                        AndroidTask<TransformTask> jackTask =
                                createJackTask(tasks, variantScope, true /*compileJavaSource*/);
                        setJavaCompilerTask(jackTask, tasks, variantScope);
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
                                                        + "are using supports Java 8 language "
                                                        + "features.");
                            } else {
                                androidBuilder
                                        .getErrorReporter()
                                        .handleSyncError(
                                                variantScope
                                                        .getVariantConfiguration()
                                                        .getFullName(),
                                                SyncIssue
                                                        .TYPE_JACK_REQUIRED_FOR_JAVA_8_LANGUAGE_FEATURES,
                                                "Jack is required to support java 8 language "
                                                        + "features. Either enable Jack or remove "
                                                        + "sourceCompatibility "
                                                        + "JavaVersion.VERSION_1_8.");
                            }
                        }
                        addJavacClassesStream(variantScope);
                        setJavaCompilerTask(javacTask, tasks, variantScope);
                        getAndroidTasks()
                                .create(
                                        tasks,
                                        new AndroidJarTask.JarClassesConfigAction(variantScope));
                        createPostCompilationTasks(tasks, variantScope);
                    }
                });

        // Add data binding tasks if enabled
        createDataBindingTasksIfNecessary(tasks, variantScope);

        createStripNativeLibraryTask(tasks, variantScope);

        if (variantData
                .getSplitHandlingPolicy()
                .equals(SplitHandlingPolicy.RELEASE_21_AND_AFTER_POLICY)) {
            if (getExtension().getBuildToolsRevision().getMajor() < 21) {
                throw new RuntimeException(
                        "Pure splits can only be used with buildtools 21 and later");
            }

            recorder.record(
                    ExecutionType.APP_TASK_MANAGER_CREATE_SPLIT_TASK,
                    project.getPath(),
                    variantScope.getFullVariantName(),
                    () -> createSplitTasks(tasks, variantScope));
        }

        recorder.record(
                ExecutionType.APP_TASK_MANAGER_CREATE_PACKAGING_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> {
                    @Nullable
                    AndroidTask<BuildInfoWriterTask> fullBuildInfoGeneratorTask =
                            createInstantRunPackagingTasks(tasks, variantScope);
                    createPackagingTask(
                            tasks, variantScope, true /*publishApk*/, fullBuildInfoGeneratorTask);
                });

        // create the lint tasks.
        recorder.record(
                ExecutionType.APP_TASK_MANAGER_CREATE_LINT_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createLintTasks(tasks, variantScope));
    }

    /**
     * Create tasks related to creating pure split APKs containing sharded dex files.
     */
    @Nullable
    protected AndroidTask<BuildInfoWriterTask> createInstantRunPackagingTasks(
            @NonNull TaskFactory tasks, @NonNull VariantScope variantScope) {

        if (getIncrementalMode(variantScope.getVariantConfiguration()) == IncrementalMode.NONE) {
            return null;
        }

        AndroidTask<BuildInfoWriterTask> buildInfoGeneratorTask =
                variantScope.getInstantRunTaskManager().createBuildInfoWriterTask();

        InstantRunPatchingPolicy patchingPolicy =
                variantScope.getInstantRunBuildContext().getPatchingPolicy();

        if (patchingPolicy == InstantRunPatchingPolicy.MULTI_APK) {

            BaseVariantOutputData outputData = variantScope.getVariantData().getMainOutput();
            PackagingScope packagingScope = new DefaultGradlePackagingScope(outputData.getScope());

            // create the transforms that will create the dependencies apk.
            InstantRunDependenciesApkBuilder dependenciesApkBuilder =
                    new InstantRunDependenciesApkBuilder(
                            getLogger(),
                            project,
                            variantScope.getInstantRunBuildContext(),
                            variantScope.getGlobalScope().getAndroidBuilder(),
                            packagingScope,
                            packagingScope.getSigningConfig(),
                            packagingScope.getAaptOptions(),
                            new File(packagingScope.getInstantRunSplitApkOutputFolder(), "dep"),
                            packagingScope.getInstantRunSupportDir());

            Optional<AndroidTask<TransformTask>> dependenciesApkBuilderTask =
                    variantScope
                            .getTransformManager()
                            .addTransform(tasks, variantScope, dependenciesApkBuilder);

            dependenciesApkBuilderTask.ifPresent(
                    task -> task.dependsOn(tasks, getValidateSigningTask(tasks, packagingScope)));

            // and now the transform that will create a split APK for each slice.
            InstantRunSliceSplitApkBuilder slicesApkBuilder =
                    new InstantRunSliceSplitApkBuilder(
                            getLogger(),
                            project,
                            variantScope.getInstantRunBuildContext(),
                            variantScope.getGlobalScope().getAndroidBuilder(),
                            packagingScope,
                            packagingScope.getSigningConfig(),
                            packagingScope.getAaptOptions(),
                            new File(packagingScope.getInstantRunSplitApkOutputFolder(), "slices"),
                            packagingScope.getInstantRunSupportDir());

            Optional<AndroidTask<TransformTask>> transformTaskAndroidTask = variantScope
                    .getTransformManager().addTransform(tasks, variantScope, slicesApkBuilder);

            if (transformTaskAndroidTask.isPresent()) {
                AndroidTask<TransformTask> splitApk = transformTaskAndroidTask.get();
                variantScope.getAssembleTask().dependsOn(tasks, splitApk);
                buildInfoGeneratorTask
                        .configure(tasks, task -> task.mustRunAfter(splitApk.getName()));
            }

            // if the assembleVariant task run, make sure it also runs the task to generate
            // the build-info.xml.
            variantScope.getAssembleTask().dependsOn(tasks, buildInfoGeneratorTask);
        }
        return buildInfoGeneratorTask;
    }

    @NonNull
    @Override
    protected Set<Scope> getResMergingScopes(@NonNull VariantScope variantScope) {
        return TransformManager.SCOPE_FULL_PROJECT;
    }

    /**
     * Configure variantData to generate embedded wear application.
     */
    private void handleMicroApp(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope scope) {
        BaseVariantData<? extends BaseVariantOutputData> variantData = scope.getVariantData();
        GradleVariantConfiguration variantConfiguration = variantData.getVariantConfiguration();
        Boolean unbundledWearApp = variantConfiguration.getMergedFlavor().getWearAppUnbundled();
        if (Boolean.TRUE.equals(unbundledWearApp)) {
            if (hasWearAppDependency(variantData)) {
                androidBuilder.getErrorReporter().handleSyncError(
                        scope.getFullVariantName(),
                        SyncIssue.TYPE_DEPENDENCY_WEAR_APK_WITH_UNBUNDLED,
                        String.format(
                                "Wear app unbundling is turned on but a dependency "
                                        + "on a wear App has been found for variant %s",
                                scope.getFullVariantName()));
            } else {
                createGenerateMicroApkDataTask(tasks, scope, null);
            }
        } else if (variantConfiguration.getBuildType().isEmbedMicroApp()) {
            // get all possible configurations for the variant. We'll take the highest priority
            // of them that have a file.
            List<String> wearConfigNames = variantData.getWearConfigNames();

            for (String configName : wearConfigNames) {
                Configuration config = project.getConfigurations().findByName(configName);
                // this shouldn't happen, but better safe.
                if (config == null) {
                    continue;
                }

                Set<File> file = config.getFiles();

                int count = file.size();
                if (count == 1) {
                    createGenerateMicroApkDataTask(tasks, scope, config);
                    // found one, bail out.
                    return;
                } else if (count > 1) {
                    androidBuilder.getErrorReporter().handleSyncError(
                            configName,
                            SyncIssue.TYPE_DEPENDENCY_WEAR_APK_TOO_MANY,
                            String.format(
                                    "Configuration '%1$s' resolves to more than one apk.",
                                    configName));

                }
            }
        }
    }

    private boolean hasWearAppDependency(
            BaseVariantData<? extends BaseVariantOutputData> variantData) {
        // get all possible configurations for the variant. We'll take the highest priority
        // of them that have a file.
        List<String> wearConfigNames = variantData.getWearConfigNames();

        for (String configName : wearConfigNames) {
            Configuration config = project.getConfigurations().findByName(configName);
            // this shouldn't happen, but better safe.
            if (config == null) {
                continue;
            }

            if (!config.getFiles().isEmpty()) {
                return true;
            }
        }

        return false;
    }
}
