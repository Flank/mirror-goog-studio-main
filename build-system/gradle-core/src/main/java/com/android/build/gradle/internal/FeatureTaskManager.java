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

import static com.android.builder.model.AndroidProject.FD_INTERMEDIATES;

import android.databinding.tool.DataBindingBuilder;
import com.android.annotations.NonNull;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.internal.aapt.AaptGeneration;
import com.android.build.gradle.internal.feature.BundleFeatureClasses;
import com.android.build.gradle.internal.incremental.BuildInfoWriterTask;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.res.LinkApplicationAndroidResourcesTask;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.ApplicationId;
import com.android.build.gradle.internal.tasks.ApplicationIdWriterTask;
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSplitDeclaration;
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSplitDeclarationWriterTask;
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSplitPackageIds;
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSplitPackageIdsWriterTask;
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSplitTransitiveDepsWriterTask;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.FeatureVariantData;
import com.android.build.gradle.internal.variant.MultiOutputPolicy;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.tasks.MainApkListPersistence;
import com.android.build.gradle.tasks.ManifestProcessorTask;
import com.android.build.gradle.tasks.MergeManifests;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.errors.EvalIssueReporter.Type;
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
    public void createTasksForVariantScope(@NonNull final VariantScope variantScope) {
        // Ensure the compile SDK is at least 26 (O).
        final AndroidVersion androidVersion =
                AndroidTargetHash.getVersionFromHash(
                        variantScope.getGlobalScope().getExtension().getCompileSdkVersion());
        if (androidVersion == null
                || androidVersion.getApiLevel() < AndroidVersion.VersionCodes.O) {
            String message = "Feature modules require compileSdkVersion set to 26 or higher.";
            if (androidVersion != null) {
                message += " compileSdkVersion is set to " + androidVersion.getApiString();
            }
            androidBuilder.getIssueReporter().reportError(Type.GENERIC, message);
        }

        // Ensure we're not using aapt1.
        if (AaptGeneration.fromProjectOptions(projectOptions) == AaptGeneration.AAPT_V1
                && !extension.getBaseFeature()) {
            androidBuilder
                    .getIssueReporter()
                    .reportError(Type.GENERIC, "Non-base feature modules require AAPTv2 to build.");
        }

        BaseVariantData variantData = variantScope.getVariantData();
        assert variantData instanceof FeatureVariantData;

        // FIXME: This is currently disabled due to b/62301277.
        if (extension.getDataBinding().isEnabled() && !extension.getBaseFeature()) {
            if (projectOptions.get(BooleanOption.ENABLE_EXPERIMENTAL_FEATURE_DATABINDING)) {
                androidBuilder
                        .getIssueReporter()
                        .reportWarning(
                                Type.GENERIC,
                                "Data binding support for non-base features is experimental "
                                        + "and is not supported.");
            } else {
                androidBuilder
                        .getIssueReporter()
                        .reportError(
                                Type.GENERIC,
                                "Currently, data binding does not work for non-base features. "
                                        + "Move data binding code to the base feature module.\n"
                                        + "See https://issuetracker.google.com/63814741.\n"
                                        + "To enable data binding with non-base features, set the "
                                        + "android.enableExperimentalFeatureDatabinding property "
                                        + "to true.");
            }
        }

        createAnchorTasks(variantScope);
        createCheckManifestTask(variantScope);

        // Create all current streams (dependencies mostly at this point)
        createDependencyStreams(variantScope);

        taskFactory.create(new MainApkListPersistence.ConfigAction(variantScope));

        if (variantScope.isBaseFeature()) {
            // Base feature specific tasks.
            recorder.record(
                    ExecutionType.FEATURE_TASK_MANAGER_CREATE_BASE_TASKS,
                    project.getPath(),
                    variantScope.getFullVariantName(),
                    () -> {
                        createFeatureApplicationIdWriterTask(variantScope);
                        createFeatureIdsWriterTask(variantScope);
                    });
        } else {
            // Non-base feature specific task.
            recorder.record(
                    ExecutionType.FEATURE_TASK_MANAGER_CREATE_NON_BASE_TASKS,
                    project.getPath(),
                    variantScope.getFullVariantName(),
                    () -> createFeatureDeclarationTasks(variantScope));
        }

        createFeatureTransitiveDepsTask(variantScope);

        // Add a task to process the manifest(s)
        recorder.record(
                ExecutionType.FEATURE_TASK_MANAGER_CREATE_MERGE_MANIFEST_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createMergeApkManifestsTask(variantScope));

        // Add a task to create the res values
        recorder.record(
                ExecutionType.FEATURE_TASK_MANAGER_CREATE_GENERATE_RES_VALUES_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createGenerateResValuesTask(variantScope));

        // Add a task to compile renderscript files.
        recorder.record(
                ExecutionType.FEATURE_TASK_MANAGER_CREATE_CREATE_RENDERSCRIPT_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createRenderscriptTask(variantScope));

        // Add a task to merge the resource folders
        recorder.record(
                ExecutionType.FEATURE_TASK_MANAGER_CREATE_MERGE_RESOURCES_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createMergeResourcesTask(variantScope, true));

        // Add tasks to compile shader
        recorder.record(
                ExecutionType.FEATURE_TASK_MANAGER_CREATE_SHADER_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createShaderTask(variantScope));


        // Add a task to merge the asset folders
        recorder.record(
                ExecutionType.FEATURE_TASK_MANAGER_CREATE_MERGE_ASSETS_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createMergeAssetsTask(variantScope));

        // Add a task to create the BuildConfig class
        recorder.record(
                ExecutionType.FEATURE_TASK_MANAGER_CREATE_BUILD_CONFIG_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createBuildConfigTask(variantScope));

        // Add a task to process the Android Resources and generate source files
        recorder.record(
                ExecutionType.FEATURE_TASK_MANAGER_CREATE_PROCESS_RES_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> {
                    createProcessResTask(
                            variantScope,
                            FileUtils.join(
                                    globalScope.getIntermediatesDir(),
                                    "symbols",
                                    variantScope
                                            .getVariantData()
                                            .getVariantConfiguration()
                                            .getDirName()),
                            variantScope.getProcessResourcePackageOutputDirectory(),
                            InternalArtifactType.FEATURE_RESOURCE_PKG,
                            MergeType.MERGE,
                            variantScope.getGlobalScope().getProjectBaseName());

                    // Add a task to process the java resources
                    createProcessJavaResTask(variantScope);
                });

        recorder.record(
                ExecutionType.FEATURE_TASK_MANAGER_CREATE_AIDL_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createAidlTask(variantScope));

        // Add NDK tasks
        if (!isComponentModelPlugin()) {
            recorder.record(
                    ExecutionType.FEATURE_TASK_MANAGER_CREATE_NDK_TASK,
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
                ExecutionType.FEATURE_TASK_MANAGER_CREATE_EXTERNAL_NATIVE_BUILD_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> {
                    createExternalNativeBuildJsonGenerators(variantScope);
                    createExternalNativeBuildTasks(variantScope);
                });

        // Add a task to merge the jni libs folders
        recorder.record(
                ExecutionType.FEATURE_TASK_MANAGER_CREATE_MERGE_JNILIBS_FOLDERS_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createMergeJniLibFoldersTasks(variantScope));

        // Add data binding tasks if enabled
        createDataBindingTasksIfNecessary(variantScope, MergeType.MERGE);

        // Add a compile task
        recorder.record(
                ExecutionType.FEATURE_TASK_MANAGER_CREATE_COMPILE_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> addCompileTask(variantScope));

        recorder.record(
                ExecutionType.FEATURE_TASK_MANAGER_CREATE_STRIP_NATIVE_LIBRARY_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> createStripNativeLibraryTask(taskFactory, variantScope));

        if (variantScope.getVariantData().getMultiOutputPolicy().equals(MultiOutputPolicy.SPLITS)) {
            if (extension.getBuildToolsRevision().getMajor() < 21) {
                throw new RuntimeException(
                        "Pure splits can only be used with buildtools 21 and later");
            }

            recorder.record(
                    ExecutionType.FEATURE_TASK_MANAGER_CREATE_SPLIT_TASK,
                    project.getPath(),
                    variantScope.getFullVariantName(),
                    () -> createSplitTasks(variantScope));
        }

        recorder.record(
                ExecutionType.FEATURE_TASK_MANAGER_CREATE_PACKAGING_TASK,
                project.getPath(),
                variantScope.getFullVariantName(),
                () -> {
                    @NonNull
                    BuildInfoWriterTask buildInfoWriterTask =
                            taskFactory.create(
                                    new BuildInfoWriterTask.ConfigAction(
                                            variantScope, getLogger()));

                    // FIXME: Re-enable when we support instant run with feature splits.
                    //createInstantRunPackagingTasks(tasks, buildInfoWriterTask, variantScope);
                    createPackagingTask(variantScope, buildInfoWriterTask);
                });
    }

    /**
     * Creates feature declaration task. Task will produce artifacts consumed by the base feature.
     */
    private void createFeatureDeclarationTasks(@NonNull VariantScope variantScope) {

        File featureSplitDeclarationOutputDirectory =
                FileUtils.join(
                        globalScope.getIntermediatesDir(),
                        "feature-split",
                        "declaration",
                        variantScope.getVariantConfiguration().getDirName());

        FeatureSplitDeclarationWriterTask featureSplitWriterTaskAndroidTask =
                taskFactory.create(
                        new FeatureSplitDeclarationWriterTask.ConfigAction(
                                variantScope, featureSplitDeclarationOutputDirectory));

        variantScope.addTaskOutput(
                InternalArtifactType.METADATA_FEATURE_DECLARATION,
                FeatureSplitDeclaration.getOutputFile(featureSplitDeclarationOutputDirectory),
                featureSplitWriterTaskAndroidTask.getName());
    }

    private void createFeatureApplicationIdWriterTask(@NonNull VariantScope variantScope) {

        File applicationIdOutputDirectory =
                FileUtils.join(
                        globalScope.getIntermediatesDir(),
                        "feature-split",
                        "applicationId",
                        variantScope.getVariantConfiguration().getDirName());

        ApplicationIdWriterTask writeTask =
                taskFactory.create(
                        new ApplicationIdWriterTask.BaseFeatureConfigAction(
                                variantScope, applicationIdOutputDirectory));

        variantScope.addTaskOutput(
                InternalArtifactType.FEATURE_APPLICATION_ID_DECLARATION,
                ApplicationId.getOutputFile(applicationIdOutputDirectory),
                writeTask.getName());
    }

    private void createFeatureIdsWriterTask(@NonNull VariantScope variantScope) {
        File featureIdsOutputDirectory =
                FileUtils.join(
                        globalScope.getIntermediatesDir(),
                        "feature-split",
                        "ids",
                        variantScope.getVariantConfiguration().getDirName());

        FeatureSplitPackageIdsWriterTask writeTask =
                taskFactory.create(
                        new FeatureSplitPackageIdsWriterTask.ConfigAction(
                                variantScope, featureIdsOutputDirectory));

        variantScope.addTaskOutput(
                InternalArtifactType.FEATURE_IDS_DECLARATION,
                FeatureSplitPackageIds.getOutputFile(featureIdsOutputDirectory),
                writeTask.getName());
    }

    private void createFeatureTransitiveDepsTask(@NonNull VariantScope scope) {
        File textFile =
                new File(
                        FileUtils.join(
                                globalScope.getIntermediatesDir(),
                                "feature-split",
                                "transitive-deps",
                                scope.getVariantConfiguration().getDirName()),
                        "deps.txt");

        FeatureSplitTransitiveDepsWriterTask task =
                taskFactory.create(
                        new FeatureSplitTransitiveDepsWriterTask.ConfigAction(scope, textFile));

        scope.addTaskOutput(InternalArtifactType.FEATURE_TRANSITIVE_DEPS, textFile, task.getName());
    }

    /** Creates the merge manifests task. */
    @Override
    @NonNull
    protected ManifestProcessorTask createMergeManifestTask(
            @NonNull VariantScope variantScope,
            @NonNull ImmutableList.Builder<ManifestMerger2.Invoker.Feature> optionalFeatures) {
        if (variantScope.getVariantConfiguration().isInstantRunBuild(globalScope)) {
            optionalFeatures.add(ManifestMerger2.Invoker.Feature.INSTANT_RUN_REPLACEMENT);
        }

        optionalFeatures.add(ManifestMerger2.Invoker.Feature.TARGET_SANDBOX_VERSION);

        ManifestProcessorTask mergeManifestsAndroidTask;
        if (variantScope.isBaseFeature()) {
            // Base split. Merge all the dependent libraries and the other splits.
            mergeManifestsAndroidTask =
                    taskFactory.create(
                            new MergeManifests.BaseFeatureConfigAction(
                                    variantScope, optionalFeatures.build()));
        } else {
            // Non-base split. Publish the feature manifest.
            optionalFeatures.add(ManifestMerger2.Invoker.Feature.ADD_FEATURE_SPLIT_INFO);

            mergeManifestsAndroidTask =
                    taskFactory.create(
                            new MergeManifests.FeatureConfigAction(
                                    variantScope, optionalFeatures.build()));

            variantScope.addTaskOutput(
                    InternalArtifactType.METADADA_FEATURE_MANIFEST,
                    variantScope.getManifestOutputDirectory(),
                    mergeManifestsAndroidTask.getName());
        }

        variantScope.addTaskOutput(
                InternalArtifactType.INSTANT_RUN_MERGED_MANIFESTS,
                variantScope.getInstantRunManifestOutputDirectory(),
                mergeManifestsAndroidTask.getName());

        return mergeManifestsAndroidTask;
    }

    private void addCompileTask(@NonNull VariantScope variantScope) {
        JavaCompile javacTask = createJavacTask(variantScope);
        VariantScope.Java8LangSupport java8LangSupport = variantScope.getJava8LangSupportType();
        if (java8LangSupport == VariantScope.Java8LangSupport.INVALID) {
            return;
        }
        // Only warn for users of retrolambda
        String pluginName = null;
        if (java8LangSupport == VariantScope.Java8LangSupport.RETROLAMBDA) {
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

            androidBuilder.getIssueReporter().reportWarning(Type.GENERIC, warningMsg);
        }

        addJavacClassesStream(variantScope);
        setJavaCompilerTask(javacTask, variantScope);
        createPostCompilationTasks(variantScope);
    }

    @Override
    protected void postJavacCreation(@NonNull VariantScope scope) {
        // Create the classes artifact for use by dependent features.
        File classesJar =
                new File(
                        globalScope.getBuildDir(),
                        FileUtils.join(
                                FD_INTERMEDIATES,
                                "classes-jar",
                                scope.getVariantConfiguration().getDirName(),
                                "classes.jar"));

        BundleFeatureClasses task =
                taskFactory.create(new BundleFeatureClasses.ConfigAction(scope, classesJar));

        scope.addTaskOutput(InternalArtifactType.FEATURE_CLASSES, classesJar, task.getName());
    }

    @NonNull
    @Override
    protected Set<? super QualifiedContent.Scope> getResMergingScopes(
            @NonNull VariantScope variantScope) {
        return TransformManager.SCOPE_FULL_PROJECT;
    }

    @Override
    protected TaskConfigAction<LinkApplicationAndroidResourcesTask>
            createProcessAndroidResourcesConfigAction(
                    @NonNull VariantScope scope,
                    @NonNull Supplier<File> symbolLocation,
                    @NonNull File symbolsWithPackageName,
                    @NonNull File resPackageOutputFolder,
                    boolean useAaptToGenerateLegacyMultidexMainDexProguardRules,
                    @NonNull MergeType sourceArtifactType,
                    @NonNull String baseName) {
        if (scope.isBaseFeature()) {
            return super.createProcessAndroidResourcesConfigAction(
                    scope,
                    symbolLocation,
                    symbolsWithPackageName,
                    resPackageOutputFolder,
                    useAaptToGenerateLegacyMultidexMainDexProguardRules,
                    sourceArtifactType,
                    baseName);
        } else {
            return new LinkApplicationAndroidResourcesTask.FeatureSplitConfigAction(
                    scope,
                    symbolLocation,
                    symbolsWithPackageName,
                    resPackageOutputFolder,
                    useAaptToGenerateLegacyMultidexMainDexProguardRules,
                    sourceArtifactType,
                    baseName);
        }
    }

}
