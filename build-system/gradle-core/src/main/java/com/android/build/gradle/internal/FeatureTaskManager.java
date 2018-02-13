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
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.res.LinkApplicationAndroidResourcesTask;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSplitDeclaration;
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSplitDeclarationWriterTask;
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSplitPackageIds;
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSplitPackageIdsWriterTask;
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSplitTransitiveDepsWriterTask;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.errors.EvalIssueReporter.Type;
import com.android.builder.profile.Recorder;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.utils.FileUtils;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan.ExecutionType;
import java.io.File;
import java.util.Set;
import java.util.function.Supplier;
import org.gradle.api.Project;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/** TaskManager for creating tasks for feature variants in an Android feature project. */
public class FeatureTaskManager extends ApplicationTaskManager {

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
        super.createTasksForVariantScope(variantScope);

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

        if (variantScope.isBaseFeature()) {
            // Base feature specific tasks.
            recorder.record(
                    ExecutionType.FEATURE_TASK_MANAGER_CREATE_BASE_TASKS,
                    project.getPath(),
                    variantScope.getFullVariantName(),
                    () -> {
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
