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
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.TaskOutputHolder;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSplitDeclaration;
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSplitDeclarationWriterTask;
import com.android.build.gradle.options.ProjectOptions;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.profile.Recorder;
import com.android.utils.FileUtils;
import java.io.File;
import java.util.Set;
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
        AndroidTask<FeatureSplitDeclarationWriterTask> featureDeclarationTasks =
                createFeatureDeclarationTasks(tasks, variantScope);
        // TODO: remove once base feature starts consuming these artifacts.
        variantScope.getAssembleTask().dependsOn(tasks, featureDeclarationTasks);
    }

    /**
     * Creates feature declaration task. Task will produce artifacts consumed by the base feature.
     */
    @NonNull
    public AndroidTask<FeatureSplitDeclarationWriterTask> createFeatureDeclarationTasks(
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
                featureSplitDeclarationOutputDirectory,
                featureSplitWriterTaskAndroidTask.getName());

        variantScope.publishIntermediateArtifact(
                FeatureSplitDeclaration.getOutputFile(featureSplitDeclarationOutputDirectory),
                featureSplitWriterTaskAndroidTask.getName(),
                AndroidArtifacts.ArtifactType.FEATURE_SPLIT_DECLARATION);

        return featureSplitWriterTaskAndroidTask;
    }

    @NonNull
    @Override
    protected Set<? super QualifiedContent.Scope> getResMergingScopes(
            @NonNull VariantScope variantScope) {
        return TransformManager.SCOPE_FULL_PROJECT;
    }
}
