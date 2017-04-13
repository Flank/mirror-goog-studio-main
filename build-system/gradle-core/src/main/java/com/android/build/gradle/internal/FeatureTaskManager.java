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

import android.databinding.tool.DataBindingBuilder;
import com.android.annotations.NonNull;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.scope.AndroidTask;
import com.android.build.gradle.internal.scope.BuildOutputs;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSplitApplicationId;
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSplitApplicationIdWriterTask;
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSplitDeclaration;
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSplitDeclarationWriterTask;
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSplitPackageIds;
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSplitPackageIdsWriterTask;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.FeatureVariantData;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.tasks.ManifestProcessorTask;
import com.android.build.gradle.tasks.MergeManifests;
import com.android.build.gradle.tasks.ProcessAndroidResources;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.profile.Recorder;
import com.android.manifmerger.ManifestMerger2;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.Set;
import java.util.function.Supplier;
import org.gradle.api.Project;
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

        // TODO: we need a better way to determine if we are dealing with a base split or not.
        if (variantScope.getVariantDependencies().getManifestSplitConfiguration() != null) {
            // Non-base feature specific tasks.
            createFeatureDeclarationTasks(tasks, variantScope);
        } else {
            // Base feature specific tasks.
            createFeatureApplicationIdWriterTask(tasks, variantScope);
            createFeatureIdsWriterTask(tasks, variantScope);
        }

        // Add a task to process the manifest(s)
        createMergeApkManifestsTask(tasks, variantScope);

        // Add a task to create the res values
        createGenerateResValuesTask(tasks, variantScope);

        // Add a task to merge the resource folders
        createMergeResourcesTask(tasks, variantScope);

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

        variantScope.publishIntermediateArtifact(
                variantScope.getProcessResourcePackageOutputDirectory(),
                processAndroidResourcesTask.getName(),
                AndroidArtifacts.ArtifactType.FEATURE_RESOURCE_PKG);

        // TODO: remove once we generate APKs.
        variantScope.getAssembleTask().dependsOn(tasks, processAndroidResourcesTask);
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

        variantScope.publishIntermediateArtifact(
                FeatureSplitDeclaration.getOutputFile(featureSplitDeclarationOutputDirectory),
                featureSplitWriterTaskAndroidTask.getName(),
                AndroidArtifacts.ArtifactType.FEATURE_SPLIT_DECLARATION);
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

        variantScope.publishIntermediateArtifact(
                FeatureSplitApplicationId.getOutputFile(applicationIdOutputDirectory),
                writeTask.getName(),
                AndroidArtifacts.ArtifactType.FEATURE_APPLICATION_ID_DECLARATION);
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

        variantScope.publishIntermediateArtifact(
                FeatureSplitPackageIds.getOutputFile(featureIdsOutputDirectory),
                writeTask.getName(),
                AndroidArtifacts.ArtifactType.FEATURE_IDS_DECLARATION);
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
        // TODO: we need a better way to determine if we are dealing with a base split or not.
        if (variantScope.getVariantDependencies().getManifestSplitConfiguration() != null) {
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

            variantScope.publishIntermediateArtifact(
                    BuildOutputs.getMetadataFile(variantScope.getManifestOutputDirectory()),
                    mergeManifestsAndroidTask.getName(),
                    AndroidArtifacts.ArtifactType.FEATURE_SPLIT_MANIFEST);
        } else {
            // Base split. Merge all the dependent libraries and the other splits.
            mergeManifestsAndroidTask =
                    androidTasks.create(
                            tasks,
                            new MergeManifests.BaseFeatureConfigAction(
                                    variantScope, optionalFeatures.build()));
        }

        variantScope.addTaskOutput(
                VariantScope.TaskOutputType.INSTANT_RUN_MERGED_MANIFESTS,
                variantScope.getInstantRunManifestOutputDirectory(),
                mergeManifestsAndroidTask.getName());

        return mergeManifestsAndroidTask;
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
            @NonNull MergeType mergeType,
            @NonNull String baseName) {
        // TODO: we need a better way to determine if we are dealing with a base split or not.
        if (scope.getVariantDependencies().getManifestSplitConfiguration() != null) {
            // Non-base feature split.
            return new ProcessAndroidResources.FeatureSplitConfigAction(
                    scope,
                    symbolLocation,
                    resPackageOutputFolder,
                    useAaptToGenerateLegacyMultidexMainDexProguardRules,
                    mergeType,
                    baseName);
        } else {
            // Base feature split.
            return super.createProcessAndroidResourcesConfigAction(
                    scope,
                    symbolLocation,
                    resPackageOutputFolder,
                    useAaptToGenerateLegacyMultidexMainDexProguardRules,
                    mergeType,
                    baseName);
        }
    }
}
