/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType.JAVAC;

import android.databinding.tool.DataBindingBuilder;
import com.android.annotations.NonNull;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.internal.incremental.BuildInfoWriterTask;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.scope.AndroidTask;
import com.android.build.gradle.internal.scope.BuildOutputs;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.TaskOutputHolder;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.ApplicationId;
import com.android.build.gradle.internal.tasks.ApplicationIdWriterTask;
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSplitDeclaration;
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSplitDeclarationWriterTask;
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSplitPackageIds;
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSplitPackageIdsWriterTask;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.FeatureVariantData;
import com.android.build.gradle.internal.variant.MultiOutputPolicy;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.tasks.ManifestProcessorTask;
import com.android.build.gradle.tasks.MergeManifests;
import com.android.build.gradle.tasks.ProcessAndroidResources;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.model.SyncIssue;
import com.android.builder.profile.Recorder;
import com.android.manifmerger.ManifestMerger2;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan.ExecutionType;
import java.io.File;
import java.util.Set;
import java.util.function.Supplier;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/** TaskManager for creating tasks for feature variants in an Android feature project. */
public class FeatureTaskManager extends TaskManager {

    public FeatureTaskManager(
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
    public void createTasksForVariantScope(
            @NonNull final TaskFactory tasks, @NonNull final VariantScope variantScope) {
        BaseVariantData variantData = variantScope.getVariantData();
        assert variantData instanceof FeatureVariantData;

        createAnchorTasks(tasks, variantScope);
        createCheckManifestTask(tasks, variantScope);

        // Create all current streams (dependencies mostly at this point)
        createDependencyStreams(tasks, variantScope);

        if (variantScope.isBaseFeature()) {
            // Base feature specific tasks.
            recorder.record(
                    ExecutionType.FEATURE_TASK_MANAGER_CREATE_BASE_TASKS,
                    project.getPath(),
                    variantScope.getFullVariantName(),
                    () -> {
                        createFeatureApplicationIdWriterTask(tasks, variantScope);
                        createFeatureIdsWriterTask(tasks, variantScope);
                    });
        } else {
            // Non-base feature specific task.
            recorder.record(
                    ExecutionType.FEATURE_TASK_MANAGER_CREATE_NON_BASE_TASKS,
                    project.getPath(),
                    variantScope.getFullVariantName(),
                    () -> createFeatureDeclarationTasks(tasks, variantScope));
        }

        // Add a task to process the manifest(s)
        recorder.record(
                ExecutionType.FEATURE_TASK_MANAGER_CREATE_MERGE_MANIFEST_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createMergeApkManifestsTask(tasks, variantScope));

        // Add a task to create the res values
        recorder.record(
                ExecutionType.FEATURE_TASK_MANAGER_CREATE_GENERATE_RES_VALUES_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createGenerateResValuesTask(tasks, variantScope));

        // Add a task to compile renderscript files.
        recorder.record(
                ExecutionType.FEATURE_TASK_MANAGER_CREATE_CREATE_RENDERSCRIPT_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createRenderscriptTask(tasks, variantScope));

        // Add a task to merge the resource folders
        recorder.record(
                ExecutionType.FEATURE_TASK_MANAGER_CREATE_MERGE_RESOURCES_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createMergeResourcesTask(tasks, variantScope));

        // Add a task to merge the asset folders
        recorder.record(
                ExecutionType.FEATURE_TASK_MANAGER_CREATE_MERGE_ASSETS_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createMergeAssetsTask(tasks, variantScope, null));

        // Add a task to create the BuildConfig class
        recorder.record(
                ExecutionType.FEATURE_TASK_MANAGER_CREATE_BUILD_CONFIG_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createBuildConfigTask(tasks, variantScope));

        // Add a task to process the Android Resources and generate source files
        recorder.record(
                ExecutionType.FEATURE_TASK_MANAGER_CREATE_PROCESS_RES_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> {
                    // Add a task to process the Android Resources and generate source files
                    // AndroidTask<ProcessAndroidResources> processAndroidResourcesTask =
                    AndroidTask<ProcessAndroidResources> processAndroidResourcesTask =
                            createProcessResTask(
                                    tasks,
                                    variantScope,
                                    () ->
                                            FileUtils.join(
                                                    globalScope.getIntermediatesDir(),
                                                    "symbols",
                                                    variantScope
                                                            .getVariantData()
                                                            .getVariantConfiguration()
                                                            .getDirName()),
                                    variantScope.getProcessResourcePackageOutputDirectory(),
                                    MergeType.MERGE,
                                    variantScope.getGlobalScope().getProjectBaseName());

                    variantScope.addTaskOutput(
                            TaskOutputHolder.TaskOutputType.FEATURE_RESOURCE_PKG,
                            variantScope.getProcessResourcePackageOutputDirectory(),
                            processAndroidResourcesTask.getName());

                    // Add a task to process the java resources
                    createProcessJavaResTask(tasks, variantScope);
                });

        recorder.record(
                ExecutionType.FEATURE_TASK_MANAGER_CREATE_AIDL_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createAidlTask(tasks, variantScope));

        recorder.record(
                ExecutionType.FEATURE_TASK_MANAGER_CREATE_SHADER_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createShaderTask(tasks, variantScope));

        // Add NDK tasks
        if (!isComponentModelPlugin()) {
            recorder.record(
                    ExecutionType.FEATURE_TASK_MANAGER_CREATE_NDK_TASK,
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
                ExecutionType.FEATURE_TASK_MANAGER_CREATE_EXTERNAL_NATIVE_BUILD_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> {
                    createExternalNativeBuildJsonGenerators(variantScope);
                    createExternalNativeBuildTasks(tasks, variantScope);
                });

        // Add a task to merge the jni libs folders
        recorder.record(
                ExecutionType.FEATURE_TASK_MANAGER_CREATE_MERGE_JNILIBS_FOLDERS_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createMergeJniLibFoldersTasks(tasks, variantScope));

        // Add data binding tasks if enabled
        createDataBindingTasksIfNecessary(tasks, variantScope);

        // Add a compile task
        recorder.record(
                ExecutionType.FEATURE_TASK_MANAGER_CREATE_COMPILE_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> addCompileTask(tasks, variantScope));

        recorder.record(
                ExecutionType.FEATURE_TASK_MANAGER_CREATE_STRIP_NATIVE_LIBRARY_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createStripNativeLibraryTask(tasks, variantScope));

        if (variantScope.getSplitScope().getMultiOutputPolicy().equals(MultiOutputPolicy.SPLITS)) {
            if (extension.getBuildToolsRevision().getMajor() < 21) {
                throw new RuntimeException(
                        "Pure splits can only be used with buildtools 21 and later");
            }

            recorder.record(
                    ExecutionType.FEATURE_TASK_MANAGER_CREATE_SPLIT_TASK,
                    project.getPath(),
                    variantScope.getFullVariantName(),
                    () -> createSplitTasks(tasks, variantScope));
        }

        recorder.record(
                ExecutionType.FEATURE_TASK_MANAGER_CREATE_PACKAGING_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> {
                    @NonNull
                    AndroidTask<BuildInfoWriterTask> buildInfoWriterTask =
                            getAndroidTasks()
                                    .create(
                                            tasks,
                                            new BuildInfoWriterTask.ConfigAction(
                                                    variantScope, getLogger()));

                    // FIXME: Re-enable when we support instant run with feature splits.
                    //createInstantRunPackagingTasks(tasks, buildInfoWriterTask, variantScope);
                    createPackagingTask(tasks, variantScope, buildInfoWriterTask);
                });

        // create the lint tasks.
        recorder.record(
                ExecutionType.FEATURE_TASK_MANAGER_CREATE_LINT_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createLintTasks(tasks, variantScope));
    }

    /**
     * Creates feature declaration task. Task will produce artifacts consumed by the base feature.
     */
    private void createFeatureDeclarationTasks(
            @NonNull TaskFactory tasks, @NonNull VariantScope variantScope) {

        File featureSplitDeclarationOutputDirectory =
                FileUtils.join(
                        globalScope.getIntermediatesDir(),
                        "feature-split",
                        "declaration",
                        variantScope.getVariantConfiguration().getDirName());

        AndroidTask<FeatureSplitDeclarationWriterTask> featureSplitWriterTaskAndroidTask =
                androidTasks.create(
                        tasks,
                        new FeatureSplitDeclarationWriterTask.ConfigAction(
                                variantScope, featureSplitDeclarationOutputDirectory));

        variantScope.addTaskOutput(
                TaskOutputHolder.TaskOutputType.METADATA_FEATURE_DECLARATION,
                FeatureSplitDeclaration.getOutputFile(featureSplitDeclarationOutputDirectory),
                featureSplitWriterTaskAndroidTask.getName());
    }

    private void createFeatureApplicationIdWriterTask(
            @NonNull TaskFactory tasks, @NonNull VariantScope variantScope) {

        File applicationIdOutputDirectory =
                FileUtils.join(
                        globalScope.getIntermediatesDir(),
                        "feature-split",
                        "applicationId",
                        variantScope.getVariantConfiguration().getDirName());

        AndroidTask<ApplicationIdWriterTask> writeTask =
                androidTasks.create(
                        tasks,
                        new ApplicationIdWriterTask.BaseFeatureConfigAction(
                                variantScope, applicationIdOutputDirectory));

        variantScope.addTaskOutput(
                TaskOutputHolder.TaskOutputType.FEATURE_APPLICATION_ID_DECLARATION,
                ApplicationId.getOutputFile(applicationIdOutputDirectory),
                writeTask.getName());
    }

    private void createFeatureIdsWriterTask(
            @NonNull TaskFactory tasks, @NonNull VariantScope variantScope) {
        File featureIdsOutputDirectory =
                FileUtils.join(
                        globalScope.getIntermediatesDir(),
                        "feature-split",
                        "ids",
                        variantScope.getVariantConfiguration().getDirName());

        AndroidTask<FeatureSplitPackageIdsWriterTask> writeTask =
                androidTasks.create(
                        tasks,
                        new FeatureSplitPackageIdsWriterTask.ConfigAction(
                                variantScope, featureIdsOutputDirectory));

        variantScope.addTaskOutput(
                TaskOutputHolder.TaskOutputType.FEATURE_IDS_DECLARATION,
                FeatureSplitPackageIds.getOutputFile(featureIdsOutputDirectory),
                writeTask.getName());
    }

    /** Creates the merge manifests task. */
    @Override
    @NonNull
    protected AndroidTask<? extends ManifestProcessorTask> createMergeManifestTask(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope variantScope,
            @NonNull ImmutableList.Builder<ManifestMerger2.Invoker.Feature> optionalFeatures) {
        if (variantScope.getVariantConfiguration().isInstantRunBuild(globalScope)) {
            optionalFeatures.add(ManifestMerger2.Invoker.Feature.INSTANT_RUN_REPLACEMENT);
        }

        // FIXME: This is temporary until we enforce usage of compile SDK 26 on features.
        final AndroidVersion androidVersion =
                AndroidTargetHash.getVersionFromHash(
                        variantScope.getGlobalScope().getExtension().getCompileSdkVersion());
        final boolean preO =
                androidVersion != null
                        && androidVersion.getApiLevel() < AndroidVersion.VersionCodes.O;

        if (!preO) {
            optionalFeatures.add(ManifestMerger2.Invoker.Feature.TARGET_SANDBOX_VERSION);
        }

        AndroidTask<? extends ManifestProcessorTask> mergeManifestsAndroidTask;
        if (variantScope.isBaseFeature()) {
            // Base split. Merge all the dependent libraries and the other splits.
            mergeManifestsAndroidTask =
                    androidTasks.create(
                            tasks,
                            new MergeManifests.BaseFeatureConfigAction(
                                    variantScope, optionalFeatures.build()));
        } else {
            // Non-base split. Publish the feature manifest.
            optionalFeatures.add(ManifestMerger2.Invoker.Feature.ADD_FEATURE_SPLIT_INFO);

            if (preO) {
                optionalFeatures.add(
                        ManifestMerger2.Invoker.Feature.TRANSITIONAL_FEATURE_SPLIT_ATTRIBUTES);
            }

            mergeManifestsAndroidTask =
                    androidTasks.create(
                            tasks,
                            new MergeManifests.FeatureConfigAction(
                                    variantScope, optionalFeatures.build()));

            variantScope.addTaskOutput(
                    TaskOutputHolder.TaskOutputType.METADADA_FEATURE_MANIFEST,
                    BuildOutputs.getMetadataFile(variantScope.getManifestOutputDirectory()),
                    mergeManifestsAndroidTask.getName());
        }

        variantScope.addTaskOutput(
                VariantScope.TaskOutputType.INSTANT_RUN_MERGED_MANIFESTS,
                variantScope.getInstantRunManifestOutputDirectory(),
                mergeManifestsAndroidTask.getName());

        return mergeManifestsAndroidTask;
    }

    private void addCompileTask(@NonNull TaskFactory tasks, @NonNull VariantScope variantScope) {
        // create data binding merge task before the javac task so that it can
        // parse jars before any consumer
        createDataBindingMergeArtifactsTaskIfNecessary(tasks, variantScope);
        AndroidTask<? extends JavaCompile> javacTask = createJavacTask(tasks, variantScope);
        VariantScope.Java8LangSupport java8LangSupport = variantScope.getJava8LangSupportType();
        if (java8LangSupport == VariantScope.Java8LangSupport.INVALID) {
            return;
        }
        // Only warn for users of retrolambda and dexguard
        String pluginName = null;
        if (java8LangSupport == VariantScope.Java8LangSupport.DEXGUARD) {
            pluginName = "dexguard";
        } else if (java8LangSupport == VariantScope.Java8LangSupport.RETROLAMBDA) {
            pluginName = "me.tatarka.retrolambda";
        }

        if (pluginName != null) {
            String warningMsg =
                    String.format(
                            "One of the plugins you are using supports Java 8 "
                                    + "language features. To try the support built into"
                                    + " the Android plugin, remove the following from "
                                    + "your build.gradle:\n"
                                    + "    apply plugin: '%s'\n"
                                    + "To learn more, go to https://d.android.com/r/"
                                    + "tools/java-8-support-message.html\n",
                            pluginName);

            androidBuilder
                    .getErrorReporter()
                    .handleSyncWarning(null, SyncIssue.TYPE_GENERIC, warningMsg);
        }

        addJavacClassesStream(variantScope);
        setJavaCompilerTask(javacTask, tasks, variantScope);
        createPostCompilationTasks(tasks, variantScope);
    }

    @Override
    protected void postJavacCreation(
            @NonNull final TaskFactory tasks, @NonNull VariantScope scope) {
        // create an anchor collection for usage inside the same module (unit tests basically)
        ConfigurableFileCollection fileCollection =
                scope.createAnchorOutput(TaskOutputHolder.AnchorOutputType.CLASSES_FOR_UNIT_TESTS);
        fileCollection.from(scope.getOutput(JAVAC));
        fileCollection.from(scope.getVariantData().getAllPreJavacGeneratedBytecode());
        fileCollection.from(scope.getVariantData().getAllPostJavacGeneratedBytecode());
    }

    @NonNull
    @Override
    protected Set<? super QualifiedContent.Scope> getResMergingScopes(
            @NonNull VariantScope variantScope) {
        return TransformManager.SCOPE_FULL_PROJECT;
    }

    @Override
    protected ProcessAndroidResources.ConfigAction createProcessAndroidResourcesConfigAction(
            @NonNull VariantScope scope,
            @NonNull Supplier<File> symbolLocation,
            @NonNull File resPackageOutputFolder,
            boolean useAaptToGenerateLegacyMultidexMainDexProguardRules,
            @NonNull MergeType sourceTaskOutputType,
            @NonNull String baseName) {
        // TODO: we need a better way to determine if we are dealing with a base split or not.
        if (scope.isBaseFeature()) {
            // Base feature split.
            return super.createProcessAndroidResourcesConfigAction(
                    scope,
                    symbolLocation,
                    resPackageOutputFolder,
                    useAaptToGenerateLegacyMultidexMainDexProguardRules,
                    sourceTaskOutputType,
                    baseName);
        } else {
            // Non-base feature split.
            return new ProcessAndroidResources.FeatureSplitConfigAction(
                    scope,
                    symbolLocation,
                    resPackageOutputFolder,
                    useAaptToGenerateLegacyMultidexMainDexProguardRules,
                    sourceTaskOutputType,
                    baseName);
        }
    }
}
