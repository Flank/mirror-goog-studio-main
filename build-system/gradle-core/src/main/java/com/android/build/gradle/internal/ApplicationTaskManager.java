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
import static com.android.builder.packaging.JarMerger.MODULE_PATH;

import android.databinding.tool.DataBindingBuilder;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.internal.aapt.AaptGeneration;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension;
import com.android.build.gradle.internal.dsl.DslAdaptersKt;
import com.android.build.gradle.internal.incremental.BuildInfoWriterTask;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.pipeline.TransformTask;
import com.android.build.gradle.internal.res.Aapt2MavenUtils;
import com.android.build.gradle.internal.scope.AnchorOutputType;
import com.android.build.gradle.internal.scope.BuildArtifactsHolder;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.AppPreBuildTask;
import com.android.build.gradle.internal.tasks.ApplicationIdWriterTask;
import com.android.build.gradle.internal.tasks.BundleTask;
import com.android.build.gradle.internal.tasks.BundleToApkTask;
import com.android.build.gradle.internal.tasks.ExtractApksTask;
import com.android.build.gradle.internal.tasks.InstallVariantViaBundleTask;
import com.android.build.gradle.internal.tasks.PerModuleBundleTask;
import com.android.build.gradle.internal.tasks.TestPreBuildTask;
import com.android.build.gradle.internal.tasks.databinding.DataBindingExportFeatureApplicationIdsTask;
import com.android.build.gradle.internal.tasks.databinding.DataBindingExportFeatureInfoTask;
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSetMetadataWriterTask;
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSplitDeclarationWriterTask;
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSplitTransitiveDepsWriterTask;
import com.android.build.gradle.internal.transforms.InstantRunDependenciesApkBuilder;
import com.android.build.gradle.internal.transforms.InstantRunSliceSplitApkBuilder;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.MultiOutputPolicy;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.tasks.MainApkListPersistence;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.VariantType;
import com.android.builder.profile.Recorder;
import com.google.common.collect.ImmutableMap;
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

        createAnchorTasks(variantScope);
        createCheckManifestTask(variantScope);

        handleMicroApp(variantScope);

        // Create all current streams (dependencies mostly at this point)
        createDependencyStreams(variantScope);

        // Add a task to publish the applicationId.
        createApplicationIdWriterTask(variantScope);

        taskFactory.create(new MainApkListPersistence.ConfigAction(variantScope));
        createBuildArtifactReportTask(variantScope);

        // Add a task to process the manifest(s)
        createMergeApkManifestsTask(variantScope);

        // Add a task to create the res values
        createGenerateResValuesTask(variantScope);

        // Add a task to compile renderscript files.
        createRenderscriptTask(variantScope);

        // Add a task to merge the resource folders
        createMergeResourcesTask(variantScope, true);

        // Add tasks to compile shader
        createShaderTask(variantScope);

        // Add a task to merge the asset folders
        createMergeAssetsTask(variantScope);

        // Add a task to create the BuildConfig class
        createBuildConfigTask(variantScope);

        // Add a task to process the Android Resources and generate source files
        createApkProcessResTask(variantScope);

        // Add a task to process the java resources
        createProcessJavaResTask(variantScope);

        createAidlTask(variantScope);

        // Add NDK tasks
        if (!isComponentModelPlugin()) {
            createNdkTasks(variantScope);
        } else {
            if (variantData.compileTask != null) {
                variantData.compileTask.dependsOn(getNdkBuildable(variantData));
            } else {
                variantScope.getCompileTask().dependsOn(getNdkBuildable(variantData));
            }
        }
        variantScope.setNdkBuildable(getNdkBuildable(variantData));

        // Add external native build tasks
        createExternalNativeBuildJsonGenerators(variantScope);
        createExternalNativeBuildTasks(variantScope);

        // Add a task to merge the jni libs folders
        createMergeJniLibFoldersTasks(variantScope);

        // Add feature related tasks if necessary
        if (variantScope.getType().isBaseModule()) {
            // Base feature specific tasks.
            taskFactory.create(new FeatureSetMetadataWriterTask.ConfigAction(variantScope));

            if (extension.getDataBinding().isEnabled()) {
                // Create a task that will package the manifest ids(the R file packages) of all
                // features into a file. This file's path is passed into the Data Binding annotation
                // processor which uses it to known about all available features.
                //
                // <p>see: {@link TaskManager#setDataBindingAnnotationProcessorParams(VariantScope)}
                taskFactory.create(
                        new DataBindingExportFeatureApplicationIdsTask.ConfigAction(variantScope));

            }
        } else {
            // Non-base feature specific task.
            // Task will produce artifacts consumed by the base feature
            taskFactory.create(new FeatureSplitDeclarationWriterTask.ConfigAction(variantScope));
            if (extension.getDataBinding().isEnabled()) {
                // Create a task that will package necessary information about the feature into a
                // file which is passed into the Data Binding annotation processor.
                taskFactory.create(new DataBindingExportFeatureInfoTask.ConfigAction(variantScope));
            }
        }

        // Add data binding tasks if enabled
        createDataBindingTasksIfNecessary(variantScope, MergeType.MERGE);

        // Add a compile task
        createCompileTask(variantScope);

        createStripNativeLibraryTask(taskFactory, variantScope);


        if (variantScope.getVariantData().getMultiOutputPolicy().equals(MultiOutputPolicy.SPLITS)) {
            if (extension.getBuildToolsRevision().getMajor() < 21) {
                throw new RuntimeException(
                        "Pure splits can only be used with buildtools 21 and later");
            }

            createSplitTasks(variantScope);
        }


        BuildInfoWriterTask buildInfoWriterTask = createInstantRunPackagingTasks(variantScope);
        createPackagingTask(variantScope, buildInfoWriterTask);

        // Create the lint tasks, if enabled
        createLintTasks(variantScope);

        taskFactory.create(new FeatureSplitTransitiveDepsWriterTask.ConfigAction(variantScope));

        createDynamicBundleTask(variantScope);
    }

    @Override
    protected void createInstallTask(VariantScope variantScope) {
        GradleVariantConfiguration variantConfiguration = variantScope.getVariantConfiguration();
        final VariantType variantType = variantConfiguration.getType();

        // feature split or AIA modules do not have their own install tasks
        if (variantType.isFeatureSplit() || variantType.isHybrid()) {
            return;
        }

        // if test app,
        // or not a base module (unlikely but better to test),
        // or no dynamic features are present,
        // then use the default install task
        if (variantType.isForTesting()
                || !(extension instanceof BaseAppModuleExtension)
                || AppModelBuilder.getDynamicFeatures(
                                (BaseAppModuleExtension) extension, variantScope.getGlobalScope())
                        .isEmpty()) {
            super.createInstallTask(variantScope);

        } else {
            // use the install task that uses the App Bundle
            taskFactory.create(new InstallVariantViaBundleTask.ConfigAction(variantScope));
        }
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

        BuildArtifactsHolder artifacts = variantScope.getArtifacts();

        // create the transforms that will create the dependencies apk.
        InstantRunDependenciesApkBuilder dependenciesApkBuilder =
                new InstantRunDependenciesApkBuilder(
                        getLogger(),
                        project,
                        variantScope.getInstantRunBuildContext(),
                        variantScope.getGlobalScope().getAndroidBuilder(),
                        Aapt2MavenUtils.getAapt2FromMavenIfEnabled(globalScope),
                        variantScope.getVariantConfiguration()::getApplicationId,
                        variantScope.getVariantConfiguration().getSigningConfig(),
                        AaptGeneration.fromProjectOptions(projectOptions),
                        DslAdaptersKt.convert(globalScope.getExtension().getAaptOptions()),
                        new File(variantScope.getInstantRunSplitApkOutputFolder(), "dep"),
                        new File(
                                variantScope.getIncrementalDir("ir_dep"),
                                variantScope.getDirName()),
                        artifacts.getFinalArtifactFiles(InternalArtifactType.PROCESSED_RES),
                        artifacts.getFinalArtifactFiles(resourcesWithMainManifest),
                        artifacts.getFinalArtifactFiles(InternalArtifactType.APK_LIST),
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
                        Aapt2MavenUtils.getAapt2FromMavenIfEnabled(globalScope),
                        variantScope.getVariantConfiguration()::getApplicationId,
                        variantScope.getVariantConfiguration().getSigningConfig(),
                        AaptGeneration.fromProjectOptions(projectOptions),
                        DslAdaptersKt.convert(globalScope.getExtension().getAaptOptions()),
                        new File(variantScope.getInstantRunSplitApkOutputFolder(), "slices"),
                        getIncrementalFolder(variantScope, "ir_slices"),
                        artifacts.getFinalArtifactFiles(InternalArtifactType.PROCESSED_RES),
                        artifacts.getFinalArtifactFiles(resourcesWithMainManifest),
                        artifacts.getFinalArtifactFiles(InternalArtifactType.APK_LIST),
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
        final BuildableArtifact javacOutput = scope.getArtifacts().getArtifactFiles(JAVAC);
        final FileCollection preJavacGeneratedBytecode =
                scope.getVariantData().getAllPreJavacGeneratedBytecode();
        final FileCollection postJavacGeneratedBytecode =
                scope.getVariantData().getAllPostJavacGeneratedBytecode();

        // Create the classes artifact for uses by external test modules.
        taskFactory.create(
                new TaskConfigAction<Jar>() {
                    @NonNull
                    @Override
                    public String getName() {
                        return scope.getTaskName("packageAppClasses");
                    }

                    @NonNull
                    @Override
                    public Class<Jar> getType() {
                        return Jar.class;
                    }

                    @Override
                    public void execute(@NonNull Jar task) {
                        File outputFile =
                                scope.getArtifacts()
                                        .appendArtifact(
                                                InternalArtifactType.APP_CLASSES,
                                                task,
                                                "classes.jar");
                        task.from(javacOutput);
                        task.from(preJavacGeneratedBytecode);
                        task.from(postJavacGeneratedBytecode);
                        task.setDestinationDir(outputFile.getParentFile());
                        task.setArchiveName(outputFile.getName());
                        task.manifest(
                                it -> {
                                    it.attributes(ImmutableMap.of(MODULE_PATH, project.getPath()));
                                });
                    }
                });

        // create a lighter weight version for usage inside the same module (unit tests basically)
        ConfigurableFileCollection files =
                scope.getGlobalScope()
                        .getProject()
                        .files(javacOutput, preJavacGeneratedBytecode, postJavacGeneratedBytecode);
        scope.getArtifacts().appendArtifact(AnchorOutputType.ALL_CLASSES, files);
    }

    @Override
    protected DefaultTask createVariantPreBuildTask(@NonNull VariantScope scope) {
        final VariantType variantType = scope.getVariantConfiguration().getType();

        if (variantType.isApk()) {
            if (variantType.isTestComponent()) { // ANDROID_TEST
                return taskFactory.create(new TestPreBuildTask.ConfigAction(scope));
            }

            return taskFactory.create(new AppPreBuildTask.ConfigAction(scope));
        }

        return super.createVariantPreBuildTask(scope);
    }

    @NonNull
    @Override
    protected Set<? super Scope> getResMergingScopes(@NonNull VariantScope variantScope) {
        if (variantScope.consumesFeatureJars()) {
            return TransformManager.SCOPE_FULL_WITH_FEATURES;
        }
        return TransformManager.SCOPE_FULL_PROJECT;
    }

    /** Configure variantData to generate embedded wear application. */
    private void handleMicroApp(@NonNull VariantScope scope) {
        BaseVariantData variantData = scope.getVariantData();
        GradleVariantConfiguration variantConfiguration = variantData.getVariantConfiguration();
        final VariantType variantType = variantConfiguration.getType();

        if (!variantType.isHybrid() && variantType.isBaseModule()) {
            Boolean unbundledWearApp = variantConfiguration.getMergedFlavor().getWearAppUnbundled();

            if (!Boolean.TRUE.equals(unbundledWearApp)
                    && variantConfiguration.getBuildType().isEmbedMicroApp()) {
                Configuration wearApp =
                        variantData.getVariantDependency().getWearAppConfiguration();
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
    }

    private void createApplicationIdWriterTask(@NonNull VariantScope variantScope) {
        if (variantScope.getType().isBaseModule()) {
            taskFactory.create(new ApplicationIdWriterTask.ConfigAction(variantScope));
        }
    }

    private static File getIncrementalFolder(VariantScope variantScope, String taskName) {
        return new File(variantScope.getIncrementalDir(taskName), variantScope.getDirName());
    }

    private void createDynamicBundleTask(@NonNull VariantScope scope) {

        // If namespaced resources are enabled, LINKED_RES_FOR_BUNDLE is not generated,
        // and the bundle can't be created. For now, just don't add the bundle task.
        if (Boolean.TRUE.equals(
                scope.getGlobalScope().getExtension().getAaptOptions().getNamespaced())) {
            return;
        }

        if (!scope.getType().isHybrid()) {
            taskFactory.create(new PerModuleBundleTask.ConfigAction(scope));

            if (scope.getType().isBaseModule()) {
                BundleTask bundleTask = taskFactory.create(new BundleTask.ConfigAction(scope));
                scope.getBundleTask().dependsOn(bundleTask);

                BundleToApkTask task = taskFactory.create(new BundleToApkTask.ConfigAction(scope));
                // make the task depend on the validate signing task to ensure that the keystore
                // is created if it's a debug one.
                if (scope.getVariantConfiguration().getSigningConfig() != null) {
                    task.dependsOn(getValidateSigningTask(scope));
                    bundleTask.dependsOn(getValidateSigningTask(scope));
                }

                taskFactory.create(new ExtractApksTask.ConfigAction(scope));
            }
        }
    }
}
