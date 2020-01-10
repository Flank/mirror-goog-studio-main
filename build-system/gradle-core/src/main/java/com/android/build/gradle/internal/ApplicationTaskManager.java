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

import static com.android.build.api.transform.QualifiedContent.DefaultContentType.RESOURCES;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ARTIFACT_TYPE;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.APK;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.AAB_PUBLICATION;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.APK_PUBLICATION;
import static com.android.build.gradle.internal.scope.InternalArtifactType.JAVAC;

import android.databinding.tool.DataBindingBuilder;
import com.android.annotations.NonNull;
import com.android.build.api.component.impl.ComponentPropertiesImpl;
import com.android.build.api.component.impl.TestComponentPropertiesImpl;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.ScopeType;
import com.android.build.api.variant.impl.VariantPropertiesImpl;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.internal.core.VariantDslInfo;
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension;
import com.android.build.gradle.internal.feature.BundleAllClasses;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.tasks.ApkZipPackagingTask;
import com.android.build.gradle.internal.tasks.AppClasspathCheckTask;
import com.android.build.gradle.internal.tasks.AppPreBuildTask;
import com.android.build.gradle.internal.tasks.ApplicationIdWriterTask;
import com.android.build.gradle.internal.tasks.AssetPackPreBundleTask;
import com.android.build.gradle.internal.tasks.BundleReportDependenciesTask;
import com.android.build.gradle.internal.tasks.BundleToApkTask;
import com.android.build.gradle.internal.tasks.BundleToStandaloneApkTask;
import com.android.build.gradle.internal.tasks.CheckManifest;
import com.android.build.gradle.internal.tasks.CheckMultiApkLibrariesTask;
import com.android.build.gradle.internal.tasks.ExportConsumerProguardFilesTask;
import com.android.build.gradle.internal.tasks.ExtractApksTask;
import com.android.build.gradle.internal.tasks.FinalizeBundleTask;
import com.android.build.gradle.internal.tasks.InstallVariantViaBundleTask;
import com.android.build.gradle.internal.tasks.LinkManifestForAssetPackTask;
import com.android.build.gradle.internal.tasks.ModuleMetadataWriterTask;
import com.android.build.gradle.internal.tasks.PackageBundleTask;
import com.android.build.gradle.internal.tasks.ParseIntegrityConfigTask;
import com.android.build.gradle.internal.tasks.PerModuleBundleTask;
import com.android.build.gradle.internal.tasks.PerModuleReportDependenciesTask;
import com.android.build.gradle.internal.tasks.ProcessAssetPackManifestTask;
import com.android.build.gradle.internal.tasks.SdkDependencyDataGeneratorTask;
import com.android.build.gradle.internal.tasks.SigningConfigWriterTask;
import com.android.build.gradle.internal.tasks.StripDebugSymbolsTask;
import com.android.build.gradle.internal.tasks.TestPreBuildTask;
import com.android.build.gradle.internal.tasks.databinding.DataBindingExportFeatureApplicationIdsTask;
import com.android.build.gradle.internal.tasks.databinding.DataBindingExportFeatureInfoTask;
import com.android.build.gradle.internal.tasks.factory.TaskFactoryUtils;
import com.android.build.gradle.internal.tasks.featuresplit.FeatureNameWriterTask;
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSetMetadataWriterTask;
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSplitDeclarationWriterTask;
import com.android.build.gradle.internal.tasks.featuresplit.PackagedDependenciesWriterTask;
import com.android.build.gradle.internal.variant.VariantFactory;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.tasks.ExtractDeepLinksTask;
import com.android.build.gradle.tasks.MergeResources;
import com.android.builder.core.VariantType;
import com.android.builder.profile.Recorder;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.resources.TextResourceFactory;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/**
 * TaskManager for creating tasks in an Android application project.
 */
public class ApplicationTaskManager extends TaskManager {

    public ApplicationTaskManager(
            @NonNull GlobalScope globalScope,
            @NonNull Project project,
            @NonNull ProjectOptions projectOptions,
            @NonNull DataBindingBuilder dataBindingBuilder,
            @NonNull BaseExtension extension,
            @NonNull VariantFactory variantFactory,
            @NonNull ToolingModelBuilderRegistry toolingRegistry,
            @NonNull Recorder recorder) {
        super(
                globalScope,
                project,
                projectOptions,
                dataBindingBuilder,
                extension,
                variantFactory,
                toolingRegistry,
                recorder);
    }

    @Override
    public void createTasksForVariantScope(
            @NonNull VariantPropertiesImpl appVariantProperties,
            @NonNull List<VariantPropertiesImpl> allComponentsWithLint) {
        createAnchorTasks(appVariantProperties);

        taskFactory.register(new ExtractDeepLinksTask.CreationAction(appVariantProperties));

        handleMicroApp(appVariantProperties);

        // Create all current streams (dependencies mostly at this point)
        createDependencyStreams(appVariantProperties);

        // Add a task to publish the applicationId.
        createApplicationIdWriterTask(appVariantProperties);

        createBuildArtifactReportTask(appVariantProperties);

        // Add a task to check the manifest
        taskFactory.register(new CheckManifest.CreationAction(appVariantProperties));

        // Add a task to process the manifest(s)
        createMergeApkManifestsTask(appVariantProperties);

        // Add a task to create the res values
        createGenerateResValuesTask(appVariantProperties);

        // Add a task to compile renderscript files.
        createRenderscriptTask(appVariantProperties);

        // Add a task to merge the resource folders
        createMergeResourcesTasks(appVariantProperties);

        // Add tasks to compile shader
        createShaderTask(appVariantProperties);

        // Add a task to merge the asset folders
        createMergeAssetsTask(appVariantProperties);

        // Add a task to create the BuildConfig class
        createBuildConfigTask(appVariantProperties);

        // Add a task to process the Android Resources and generate source files
        createApkProcessResTask(appVariantProperties);

        registerRClassTransformStream(appVariantProperties);

        // Add a task to process the java resources
        createProcessJavaResTask(appVariantProperties);

        createAidlTask(appVariantProperties);

        // Add external native build tasks
        createExternalNativeBuildJsonGenerators(appVariantProperties);
        createExternalNativeBuildTasks(appVariantProperties);

        // Add a task to merge the jni libs folders
        createMergeJniLibFoldersTasks(appVariantProperties);

        // Add feature related tasks if necessary
        if (appVariantProperties.getVariantType().isBaseModule()) {
            // Base feature specific tasks.
            taskFactory.register(
                    new FeatureSetMetadataWriterTask.CreationAction(appVariantProperties));

            createValidateSigningTask(appVariantProperties);
            // Add a task to produce the signing config file.
            taskFactory.register(new SigningConfigWriterTask.CreationAction(appVariantProperties));

            if (!(((BaseAppModuleExtension) extension).getAssetPacks().isEmpty())) {
                createAssetPackTasks(appVariantProperties);
            }

            if (globalScope.getBuildFeatures().getDataBinding()) {
                // Create a task that will package the manifest ids(the R file packages) of all
                // features into a file. This file's path is passed into the Data Binding annotation
                // processor which uses it to known about all available features.
                //
                // <p>see: {@link TaskManager#setDataBindingAnnotationProcessorParams(VariantScope)}
                taskFactory.register(
                        new DataBindingExportFeatureApplicationIdsTask.CreationAction(
                                appVariantProperties));

            }
        } else {
            // Non-base feature specific task.
            // Task will produce artifacts consumed by the base feature
            taskFactory.register(
                    new FeatureSplitDeclarationWriterTask.CreationAction(appVariantProperties));
            if (globalScope.getBuildFeatures().getDataBinding()) {
                // Create a task that will package necessary information about the feature into a
                // file which is passed into the Data Binding annotation processor.
                taskFactory.register(
                        new DataBindingExportFeatureInfoTask.CreationAction(appVariantProperties));
            }
            taskFactory.register(
                    new ExportConsumerProguardFilesTask.CreationAction(appVariantProperties));
            taskFactory.register(new FeatureNameWriterTask.CreationAction(appVariantProperties));
        }

        // Add data binding tasks if enabled
        createDataBindingTasksIfNecessary(appVariantProperties);

        // Add a compile task
        createCompileTask(appVariantProperties);

        taskFactory.register(new StripDebugSymbolsTask.CreationAction(appVariantProperties));

        createPackagingTask(appVariantProperties);

        maybeCreateLintVitalTask(appVariantProperties, allComponentsWithLint);

        // Create the lint tasks, if enabled
        createLintTasks(appVariantProperties, allComponentsWithLint);

        taskFactory.register(
                new PackagedDependenciesWriterTask.CreationAction(appVariantProperties));

        createDynamicBundleTask(appVariantProperties);

        taskFactory.register(new ApkZipPackagingTask.CreationAction(appVariantProperties));

        // do not publish the APK(s) if there are dynamic feature.
        if (!appVariantProperties.getGlobalScope().hasDynamicFeatures()) {
            createSoftwareComponent(appVariantProperties, "_apk", APK_PUBLICATION);
        }
        createSoftwareComponent(appVariantProperties, "_aab", AAB_PUBLICATION);
    }

    private void createSoftwareComponent(
            @NonNull VariantPropertiesImpl variantProperties,
            @NonNull String suffix,
            @NonNull AndroidArtifacts.PublishedConfigType publication) {
        AdhocComponentWithVariants component =
                globalScope.getComponentFactory().adhoc(variantProperties.getName() + suffix);

        final Configuration config =
                variantProperties.getVariantDependencies().getElements(publication);

        component.addVariantsFromConfiguration(config, details -> {});

        project.getComponents().add(component);
    }

    @Override
    protected void createInstallTask(@NonNull ComponentPropertiesImpl componentProperties) {
        final VariantType variantType = componentProperties.getVariantType();

        // dynamic feature modules do not have their own install tasks
        if (variantType.isDynamicFeature()) {
            return;
        }

        // if test app,
        // or not a base module (unlikely but better to test),
        // or no dynamic features are present,
        // then use the default install task
        if (variantType.isForTesting()
                || !(extension instanceof BaseAppModuleExtension)
                || ((BaseAppModuleExtension) extension).getDynamicFeatures().isEmpty()) {
            super.createInstallTask(componentProperties);

        } else {
            // use the install task that uses the App Bundle
            taskFactory.register(
                    new InstallVariantViaBundleTask.CreationAction(componentProperties));
        }
    }

    @Override
    protected void postJavacCreation(@NonNull ComponentPropertiesImpl componentProperties) {
        final Provider<Directory> javacOutput =
                componentProperties.getArtifacts().getFinalProduct(JAVAC.INSTANCE);
        final FileCollection preJavacGeneratedBytecode =
                componentProperties.getVariantData().getAllPreJavacGeneratedBytecode();
        final FileCollection postJavacGeneratedBytecode =
                componentProperties.getVariantData().getAllPostJavacGeneratedBytecode();

        taskFactory.register(new BundleAllClasses.CreationAction(componentProperties));

        // create a lighter weight version for usage inside the same module (unit tests basically)
        ConfigurableFileCollection files =
                componentProperties
                        .getGlobalScope()
                        .getProject()
                        .files(javacOutput, preJavacGeneratedBytecode, postJavacGeneratedBytecode);
        componentProperties.getArtifacts().appendToAllClasses(files);
    }

    @Override
    protected void createVariantPreBuildTask(@NonNull ComponentPropertiesImpl componentProperties) {
        final VariantType variantType = componentProperties.getVariantType();

        if (variantType.isApk()) {
            boolean useDependencyConstraints =
                    componentProperties
                            .getGlobalScope()
                            .getProjectOptions()
                            .get(BooleanOption.USE_DEPENDENCY_CONSTRAINTS);

            TaskProvider<? extends Task> task;

            if (variantType.isTestComponent()) {
                task =
                        taskFactory.register(
                                new TestPreBuildTask.CreationAction(
                                        (TestComponentPropertiesImpl) componentProperties));
                if (useDependencyConstraints) {
                    task.configure(t -> t.setEnabled(false));
                }
            } else {
                //noinspection unchecked
                task = taskFactory.register(AppPreBuildTask.getCreationAction(componentProperties));
            }

            if (!useDependencyConstraints) {
                TaskProvider<AppClasspathCheckTask> classpathCheck =
                        taskFactory.register(
                                new AppClasspathCheckTask.CreationAction(componentProperties));
                TaskFactoryUtils.dependsOn(task, classpathCheck);
            }

            if (variantType.isBaseModule() && globalScope.hasDynamicFeatures()) {
                TaskProvider<CheckMultiApkLibrariesTask> checkMultiApkLibrariesTask =
                        taskFactory.register(
                                new CheckMultiApkLibrariesTask.CreationAction(componentProperties));

                TaskFactoryUtils.dependsOn(task, checkMultiApkLibrariesTask);
            }
            return;
        }

        super.createVariantPreBuildTask(componentProperties);
    }

    @NonNull
    @Override
    protected Set<ScopeType> getJavaResMergingScopes(
            @NonNull ComponentPropertiesImpl componentProperties,
            @NonNull QualifiedContent.ContentType contentType) {
        if (componentProperties.getVariantScope().consumesFeatureJars()
                && contentType == RESOURCES) {
            return TransformManager.SCOPE_FULL_WITH_FEATURES;
        }
        return TransformManager.SCOPE_FULL_PROJECT;
    }

    /** Configure variantData to generate embedded wear application. */
    private void handleMicroApp(@NonNull VariantPropertiesImpl variantProperties) {
        VariantDslInfo variantDslInfo = variantProperties.getVariantDslInfo();
        final VariantType variantType = variantProperties.getVariantType();

        if (variantType.isBaseModule()) {
            Boolean unbundledWearApp = variantDslInfo.isWearAppUnbundled();

            if (!Boolean.TRUE.equals(unbundledWearApp) && variantDslInfo.isEmbedMicroApp()) {
                Configuration wearApp =
                        variantProperties.getVariantDependencies().getWearAppConfiguration();
                assert wearApp != null : "Wear app with no wearApp configuration";
                if (!wearApp.getAllDependencies().isEmpty()) {
                    Action<AttributeContainer> setApkArtifact =
                            container -> container.attribute(ARTIFACT_TYPE, APK.getType());
                    FileCollection files =
                            wearApp.getIncoming()
                                    .artifactView(config -> config.attributes(setApkArtifact))
                                    .getFiles();
                    createGenerateMicroApkDataTask(variantProperties, files);
                }
            } else {
                if (Boolean.TRUE.equals(unbundledWearApp)) {
                    createGenerateMicroApkDataTask(variantProperties, null);
                }
            }
        }
    }

    private void createApplicationIdWriterTask(@NonNull VariantPropertiesImpl variantProperties) {
        if (variantProperties.getVariantType().isBaseModule()) {
            taskFactory.register(new ModuleMetadataWriterTask.CreationAction(variantProperties));
        }

        // TODO b/141650037 - Only the base App should create this task.
        TaskProvider<? extends Task> applicationIdWriterTask =
                taskFactory.register(new ApplicationIdWriterTask.CreationAction(variantProperties));

        TextResourceFactory resources = project.getResources().getText();
        // this builds the dependencies from the task, and its output is the textResource.
        variantProperties.getVariantData().applicationIdTextResource =
                resources.fromFile(applicationIdWriterTask);
    }

    private void createDynamicBundleTask(@NonNull VariantPropertiesImpl variantProperties) {

        // If namespaced resources are enabled, LINKED_RES_FOR_BUNDLE is not generated,
        // and the bundle can't be created. For now, just don't add the bundle task.
        // TODO(b/111168382): Remove this
        if (variantProperties.getGlobalScope().getExtension().getAaptOptions().getNamespaced()) {
            return;
        }

        taskFactory.register(
                new PerModuleBundleTask.CreationAction(
                        variantProperties,
                        packagesCustomClassDependencies(variantProperties, projectOptions)));
        if (addBundleDependenciesTask(variantProperties)) {
            taskFactory.register(
                    new PerModuleReportDependenciesTask.CreationAction(variantProperties));
        }

        if (variantProperties.getVariantType().isBaseModule()) {
            taskFactory.register(new ParseIntegrityConfigTask.CreationAction(variantProperties));
            taskFactory.register(new PackageBundleTask.CreationAction(variantProperties));
            taskFactory.register(new FinalizeBundleTask.CreationAction(variantProperties));
            if (addBundleDependenciesTask(variantProperties)) {
                taskFactory.register(new BundleReportDependenciesTask.CreationAction(variantProperties));
                if (variantProperties.getGlobalScope()
                        .getProjectOptions()
                        .get(BooleanOption.INCLUDE_DEPENDENCY_INFO_IN_APKS)) {
                    taskFactory.register(new SdkDependencyDataGeneratorTask.CreationAction(variantProperties));
                }
            }

            taskFactory.register(new BundleToApkTask.CreationAction(variantProperties));
            taskFactory.register(new BundleToStandaloneApkTask.CreationAction(variantProperties));

            taskFactory.register(new ExtractApksTask.CreationAction(variantProperties));
        }
    }

    private void createMergeResourcesTasks(@NonNull VariantPropertiesImpl variantProperties) {
        // The "big merge" of all resources, will merge and compile resources that will later
        // be used for linking.
        createMergeResourcesTask(
                variantProperties,
                true,
                Sets.immutableEnumSet(MergeResources.Flag.PROCESS_VECTOR_DRAWABLES));

        // TODO(b/138780301): Also use it in android tests.
        if (projectOptions.get(BooleanOption.ENABLE_APP_COMPILE_TIME_R_CLASS)
                && !variantProperties.getVariantType().isForTesting()
                && !variantProperties
                        .getGlobalScope()
                        .getExtension()
                        .getAaptOptions()
                        .getNamespaced()) {
            // The "small merge" of only the app's local resources (can be multiple source-sets, but
            // most of the time it's just one). This is used by the Process for generating the local
            // R-def.txt file containing a list of resources defined in this module.
            basicCreateMergeResourcesTask(
                    variantProperties,
                    MergeType.PACKAGE,
                    variantProperties
                            .getPaths()
                            .getIntermediateDir(InternalArtifactType.PACKAGED_RES.INSTANCE),
                    false,
                    false,
                    false,
                    ImmutableSet.of(),
                    null);
        }
    }

    private static boolean addBundleDependenciesTask(
            @NonNull VariantPropertiesImpl variantProperties) {
        return !variantProperties.getVariantDslInfo().isDebuggable();
    }

    private void createAssetPackTasks(@NonNull VariantPropertiesImpl variantProperties) {
        DependencyHandler depHandler = project.getDependencies();
        List<String> notFound = new ArrayList<>();
        Configuration assetPackFilesConfiguration =
                project.getConfigurations().maybeCreate("assetPackFiles");
        Configuration assetPackManifestConfiguration =
                project.getConfigurations().maybeCreate("assetPackManifest");
        boolean needToRegisterAssetPackTasks = false;
        Set<String> assetPacks = ((BaseAppModuleExtension) extension).getAssetPacks();

        for (String assetPack : assetPacks) {
            if (project.findProject(assetPack) != null) {
                Map<String, String> filesDependency =
                        ImmutableMap.of("path", assetPack, "configuration", "packElements");
                depHandler.add("assetPackFiles", depHandler.project(filesDependency));

                Map<String, String> manifestDependency =
                        ImmutableMap.of("path", assetPack, "configuration", "manifestElements");
                depHandler.add("assetPackManifest", depHandler.project(manifestDependency));

                needToRegisterAssetPackTasks = true;
            } else {
                notFound.add(assetPack);
            }
        }

        if (needToRegisterAssetPackTasks) {
            FileCollection assetPackManifest =
                    assetPackManifestConfiguration.getIncoming().getFiles();
            FileCollection assetFiles = assetPackFilesConfiguration.getIncoming().getFiles();

            taskFactory.register(
                    new ProcessAssetPackManifestTask.CreationAction(
                            variantProperties,
                            assetPackManifest,
                            assetPacks
                                    .stream()
                                    .map(
                                            assetPackName ->
                                                    assetPackName.replace(":", File.separator))
                                    .collect(Collectors.toSet())));
            taskFactory.register(
                    new LinkManifestForAssetPackTask.CreationAction(variantProperties));
            taskFactory.register(
                    new AssetPackPreBundleTask.CreationAction(variantProperties, assetFiles));
        }

        if (!notFound.isEmpty()) {
            getLogger().error("Unable to find matching projects for Asset Packs: " + notFound);
        }
    }
}
