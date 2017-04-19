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
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSplitApplicationId;
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSplitApplicationIdWriterTask;
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSplitDeclaration;
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSplitDeclarationWriterTask;
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSplitPackageIds;
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSplitPackageIdsWriterTask;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.FeatureVariantData;
import com.android.build.gradle.internal.variant.SplitHandlingPolicy;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.tasks.AndroidJarTask;
import com.android.build.gradle.tasks.ManifestProcessorTask;
import com.android.build.gradle.tasks.MergeManifests;
import com.android.build.gradle.tasks.ProcessAndroidResources;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.model.SyncIssue;
import com.android.builder.profile.Recorder;
import com.android.manifmerger.ManifestMerger2;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
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
            @NonNull DependencyManager dependencyManager,
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
                dependencyManager,
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

        if (variantScope.isBaseSplit()) {
            // Base feature specific tasks.
            createFeatureApplicationIdWriterTask(tasks, variantScope);
            createFeatureIdsWriterTask(tasks, variantScope);
        } else {
            // Non-base feature specific tasks.
            createFeatureDeclarationTasks(tasks, variantScope);
        }

        // Add a task to process the manifest(s)
        createMergeApkManifestsTask(tasks, variantScope);

        // Add a task to create the res values
        createGenerateResValuesTask(tasks, variantScope);

        // Add a task to compile renderscript files.
        createRenderscriptTask(tasks, variantScope);

        // Add a task to merge the resource folders
        createMergeResourcesTask(tasks, variantScope);

        // Add a task to merge the asset folders
        createMergeAssetsTask(tasks, variantScope, null);

        // Add a task to create the BuildConfig class
        createBuildConfigTask(tasks, variantScope);

        // Add a task to process the Android Resources and generate source files
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
        createMergeJavaResTransform(tasks, variantScope);

        createAidlTask(tasks, variantScope);

        createShaderTask(tasks, variantScope);

        // Add NDK tasks
        if (!isComponentModelPlugin()) {
            createNdkTasks(tasks, variantScope);
        } else {
            if (variantData.compileTask != null) {
                variantData.compileTask.dependsOn(getNdkBuildable(variantData));
            } else {
                variantScope.getCompileTask().dependsOn(tasks, getNdkBuildable(variantData));
            }
        }
        variantScope.setNdkBuildable(getNdkBuildable(variantData));

        // Add external native build tasks
        createExternalNativeBuildJsonGenerators(variantScope);
        createExternalNativeBuildTasks(tasks, variantScope);

        // Add a task to merge the jni libs folders
        createMergeJniLibFoldersTasks(tasks, variantScope);

        // Add a compile task
        addCompileTask(tasks, variantScope);

        // Add data binding tasks if enabled
        createDataBindingTasksIfNecessary(tasks, variantScope);

        createStripNativeLibraryTask(tasks, variantScope);

        if (variantScope
                .getSplitScope()
                .getSplitHandlingPolicy()
                .equals(SplitHandlingPolicy.RELEASE_21_AND_AFTER_POLICY)) {
            if (extension.getBuildToolsRevision().getMajor() < 21) {
                throw new RuntimeException(
                        "Pure splits can only be used with buildtools 21 and later");
            }
            createSplitTasks(tasks, variantScope);
        }

        @NonNull
        AndroidTask<BuildInfoWriterTask> buildInfoWriterTask =
                getAndroidTasks()
                        .create(
                                tasks,
                                new BuildInfoWriterTask.ConfigAction(variantScope, getLogger()));

        //createInstantRunPackagingTasks(tasks, buildInfoWriterTask, variantScope);
        createPackagingTask(tasks, variantScope, buildInfoWriterTask);

        // create the lint tasks.
        createLintTasks(tasks, variantScope);
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
                TaskOutputHolder.TaskOutputType.FEATURE_SPLIT_DECLARATION,
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

        AndroidTask<FeatureSplitApplicationIdWriterTask> writeTask =
                androidTasks.create(
                        tasks,
                        new FeatureSplitApplicationIdWriterTask.ConfigAction(
                                variantScope, applicationIdOutputDirectory));

        variantScope.addTaskOutput(
                TaskOutputHolder.TaskOutputType.FEATURE_APPLICATION_ID_DECLARATION,
                FeatureSplitApplicationId.getOutputFile(applicationIdOutputDirectory),
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
        if (getIncrementalMode(variantScope.getVariantConfiguration()) != IncrementalMode.NONE) {
            optionalFeatures.add(ManifestMerger2.Invoker.Feature.INSTANT_RUN_REPLACEMENT);
        }

        AndroidTask<? extends ManifestProcessorTask> mergeManifestsAndroidTask;
        if (variantScope.isBaseSplit()) {
            // Base split. Merge all the dependent libraries and the other splits.
            mergeManifestsAndroidTask =
                    androidTasks.create(
                            tasks,
                            new MergeManifests.BaseFeatureConfigAction(
                                    variantScope, optionalFeatures.build()));
        } else {
            // Non-base split. Publish the feature manifest.
            optionalFeatures.add(ManifestMerger2.Invoker.Feature.ADD_FEATURE_SPLIT_INFO);
            if (variantScope
                    .getGlobalScope()
                    .getProjectOptions()
                    .get(BooleanOption.ENABLE_FEATURE_SPLIT_TRANSITIONAL_ATTRIBUTES)) {
                optionalFeatures.add(
                        ManifestMerger2.Invoker.Feature.TRANSITIONAL_FEATURE_SPLIT_ATTRIBUTES);
            }

            mergeManifestsAndroidTask =
                    androidTasks.create(
                            tasks,
                            new MergeManifests.FeatureConfigAction(
                                    variantScope, optionalFeatures.build()));

            variantScope.addTaskOutput(
                    TaskOutputHolder.TaskOutputType.FEATURE_SPLIT_MANIFEST,
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

        if (variantScope
                .getGlobalScope()
                .getExtension()
                .getCompileOptions()
                .getTargetCompatibility()
                .isJava8Compatible()) {

            if (java8LangSupport != VariantScope.Java8LangSupport.DESUGAR) {
                // Only warn for users of retrolambda and dexguard
                if (java8LangSupport == VariantScope.Java8LangSupport.EXTERNAL_PLUGIN) {
                    androidBuilder
                            .getErrorReporter()
                            .handleSyncWarning(
                                    null,
                                    SyncIssue.TYPE_GENERIC,
                                    "One of the plugins you are using supports Java 8 "
                                            + "language features. To try the support built into"
                                            + " the Android plugin, remove the following from "
                                            + "your build.gradle:\n"
                                            + "    apply plugin: '<plugin_name>'\n"
                                            + "or\n"
                                            + "    plugin {\n"
                                            + "        id '<plugin_name>' version '<version>'\n"
                                            + "    }\n\n"
                                            + "To learn more, go to https://d.android.com/r/"
                                            + "tools/java-8-support-message.html\n");
                } else {
                    androidBuilder
                            .getErrorReporter()
                            .handleSyncError(
                                    variantScope.getVariantConfiguration().getFullName(),
                                    SyncIssue.TYPE_GENERIC,
                                    "Please add 'android.enableDesugar=true' to your "
                                            + "gradle.properties file to enable Java 8 "
                                            + "language support.");
                }
            }
        }

        addJavacClassesStream(variantScope);
        setJavaCompilerTask(javacTask, tasks, variantScope);
        getAndroidTasks().create(tasks, new AndroidJarTask.JarClassesConfigAction(variantScope));
        createPostCompilationTasks(tasks, variantScope);
    }

    @Override
    protected void postJavacCreation(
            @NonNull final TaskFactory tasks, @NonNull VariantScope scope) {
        // create an anchor collection for usage inside the same module (unit tests basically)
        ConfigurableFileCollection fileCollection =
                scope.createAnchorOutput(TaskOutputHolder.AnchorOutputType.CLASSES_FOR_UNIT_TESTS);
        fileCollection.from(scope.getOutput(JAVAC));
        fileCollection.from(scope.getVariantData().getAllGeneratedBytecode());
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
        if (scope.isBaseSplit()) {
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
