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
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.ScopeType;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension;
import com.android.build.gradle.internal.feature.BundleAllClasses;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.ApkZipPackagingTask;
import com.android.build.gradle.internal.tasks.AppClasspathCheckTask;
import com.android.build.gradle.internal.tasks.AppPreBuildTask;
import com.android.build.gradle.internal.tasks.ApplicationIdWriterTask;
import com.android.build.gradle.internal.tasks.BundleReportDependenciesTask;
import com.android.build.gradle.internal.tasks.BundleToApkTask;
import com.android.build.gradle.internal.tasks.BundleToStandaloneApkTask;
import com.android.build.gradle.internal.tasks.CheckManifest;
import com.android.build.gradle.internal.tasks.CheckMultiApkLibrariesTask;
import com.android.build.gradle.internal.tasks.ExportConsumerProguardFilesTask;
import com.android.build.gradle.internal.tasks.ExtractApksTask;
import com.android.build.gradle.internal.tasks.FinalizeBundleTask;
import com.android.build.gradle.internal.tasks.InstallVariantViaBundleTask;
import com.android.build.gradle.internal.tasks.ModuleMetadataWriterTask;
import com.android.build.gradle.internal.tasks.PackageBundleTask;
import com.android.build.gradle.internal.tasks.ParseIntegrityConfigTask;
import com.android.build.gradle.internal.tasks.PerModuleBundleTask;
import com.android.build.gradle.internal.tasks.PerModuleReportDependenciesTask;
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
import com.android.build.gradle.internal.variant.ApkVariantData;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.MultiOutputPolicy;
import com.android.build.gradle.internal.variant.VariantFactory;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.tasks.ExtractDeepLinksTask;
import com.android.build.gradle.tasks.MainApkListPersistence;
import com.android.build.gradle.tasks.MergeResources;
import com.android.builder.core.VariantType;
import com.android.builder.profile.Recorder;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.io.File;
import java.util.List;
import java.util.Set;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
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
            @NonNull final VariantScope variantScope,
            @NonNull List<VariantScope> variantScopesForLint) {
        createAnchorTasks(variantScope);

        taskFactory.register(new ExtractDeepLinksTask.CreationAction(variantScope));

        handleMicroApp(variantScope);

        // Create all current streams (dependencies mostly at this point)
        createDependencyStreams(variantScope);

        // Add a task to publish the applicationId.
        createApplicationIdWriterTask(variantScope);

        taskFactory.register(new MainApkListPersistence.CreationAction(variantScope));
        createBuildArtifactReportTask(variantScope);

        // Add a task to check the manifest
        taskFactory.register(new CheckManifest.CreationAction(variantScope));

        // Add a task to process the manifest(s)
        createMergeApkManifestsTask(variantScope);

        // Add a task to create the res values
        createGenerateResValuesTask(variantScope);

        // Add a task to compile renderscript files.
        createRenderscriptTask(variantScope);

        // Add a task to merge the resource folders
        createMergeResourcesTasks(variantScope);

        // Add tasks to compile shader
        createShaderTask(variantScope);

        // Add a task to merge the asset folders
        createMergeAssetsTask(variantScope);

        // Add a task to create the BuildConfig class
        createBuildConfigTask(variantScope);

        // Add a task to process the Android Resources and generate source files
        createApkProcessResTask(variantScope);

        registerRClassTransformStream(variantScope);

        // Add a task to process the java resources
        createProcessJavaResTask(variantScope);

        createAidlTask(variantScope);

        // Add external native build tasks
        createExternalNativeBuildJsonGenerators(variantScope);
        createExternalNativeBuildTasks(variantScope);

        // Add a task to merge the jni libs folders
        createMergeJniLibFoldersTasks(variantScope);

        // Add feature related tasks if necessary
        if (variantScope.getType().isBaseModule()) {
            // Base feature specific tasks.
            taskFactory.register(new FeatureSetMetadataWriterTask.CreationAction(variantScope));

            createValidateSigningTask(variantScope);
            // Add a task to produce the signing config file.
            taskFactory.register(new SigningConfigWriterTask.CreationAction(variantScope));

            if (extension.getDataBinding().isEnabled()) {
                // Create a task that will package the manifest ids(the R file packages) of all
                // features into a file. This file's path is passed into the Data Binding annotation
                // processor which uses it to known about all available features.
                //
                // <p>see: {@link TaskManager#setDataBindingAnnotationProcessorParams(VariantScope)}
                taskFactory.register(
                        new DataBindingExportFeatureApplicationIdsTask.CreationAction(
                                variantScope));

            }
        } else {
            // Non-base feature specific task.
            // Task will produce artifacts consumed by the base feature
            taskFactory.register(
                    new FeatureSplitDeclarationWriterTask.CreationAction(variantScope));
            if (extension.getDataBinding().isEnabled()) {
                // Create a task that will package necessary information about the feature into a
                // file which is passed into the Data Binding annotation processor.
                taskFactory.register(
                        new DataBindingExportFeatureInfoTask.CreationAction(variantScope));
            }
            taskFactory.register(new ExportConsumerProguardFilesTask.CreationAction(variantScope));
            taskFactory.register(new FeatureNameWriterTask.CreationAction(variantScope));
        }

        // Add data binding tasks if enabled
        createDataBindingTasksIfNecessary(variantScope);

        // Add a compile task
        createCompileTask(variantScope);

        taskFactory.register(new StripDebugSymbolsTask.CreationAction(variantScope));

        if (variantScope.getVariantData().getMultiOutputPolicy().equals(MultiOutputPolicy.SPLITS)) {
            if (extension.getBuildToolsRevision().getMajor() < 21) {
                throw new RuntimeException(
                        "Pure splits can only be used with buildtools 21 and later");
            }

            createSplitTasks(variantScope);
        }

        createPackagingTask(variantScope);

        maybeCreateLintVitalTask(
                (ApkVariantData) variantScope.getVariantData(), variantScopesForLint);

        // Create the lint tasks, if enabled
        createLintTasks(variantScope, variantScopesForLint);

        taskFactory.register(new PackagedDependenciesWriterTask.CreationAction(variantScope));

        createDynamicBundleTask(variantScope);

        taskFactory.register(new ApkZipPackagingTask.CreationAction(variantScope));

        // do not publish the APK(s) if there are dynamic feature.
        if (!variantScope.getGlobalScope().hasDynamicFeatures()) {
            createSoftwareComponent(variantScope, "_apk", APK_PUBLICATION);
        }
        createSoftwareComponent(variantScope, "_aab", AAB_PUBLICATION);
    }

    private void createSoftwareComponent(
            @NonNull VariantScope variantScope,
            @NonNull String suffix,
            @NonNull AndroidArtifacts.PublishedConfigType publication) {
        AdhocComponentWithVariants component =
                globalScope.getComponentFactory().adhoc(variantScope.getFullVariantName() + suffix);

        final Configuration config = variantScope.getVariantDependencies().getElements(publication);

        component.addVariantsFromConfiguration(config, details -> {});

        project.getComponents().add(component);
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
                || ((BaseAppModuleExtension) extension).getDynamicFeatures().isEmpty()) {
            super.createInstallTask(variantScope);

        } else {
            // use the install task that uses the App Bundle
            taskFactory.register(new InstallVariantViaBundleTask.CreationAction(variantScope));
        }
    }

    @Override
    protected void postJavacCreation(@NonNull VariantScope scope) {
        final Provider<Directory> javacOutput =
                scope.getArtifacts().getFinalProduct(JAVAC.INSTANCE);
        final FileCollection preJavacGeneratedBytecode =
                scope.getVariantData().getAllPreJavacGeneratedBytecode();
        final FileCollection postJavacGeneratedBytecode =
                scope.getVariantData().getAllPostJavacGeneratedBytecode();

        taskFactory.register(new BundleAllClasses.CreationAction(scope));

        // create a lighter weight version for usage inside the same module (unit tests basically)
        ConfigurableFileCollection files =
                scope.getGlobalScope()
                        .getProject()
                        .files(javacOutput, preJavacGeneratedBytecode, postJavacGeneratedBytecode);
        scope.getArtifacts().appendToAllClasses(files);
    }

    @Override
    protected void createVariantPreBuildTask(@NonNull VariantScope scope) {
        final VariantType variantType = scope.getVariantConfiguration().getType();

        if (variantType.isApk()) {
            boolean useDependencyConstraints =
                    scope.getGlobalScope()
                            .getProjectOptions()
                            .get(BooleanOption.USE_DEPENDENCY_CONSTRAINTS);

            TaskProvider<? extends Task> task;

            if (variantType.isTestComponent()) {
                task = taskFactory.register(new TestPreBuildTask.CreationAction(scope));
                if (useDependencyConstraints) {
                    task.configure(t -> t.setEnabled(false));
                }
            } else {
                //noinspection unchecked
                task = taskFactory.register(AppPreBuildTask.getCreationAction(scope));
            }

            if (!useDependencyConstraints) {
                TaskProvider<AppClasspathCheckTask> classpathCheck =
                        taskFactory.register(new AppClasspathCheckTask.CreationAction(scope));
                TaskFactoryUtils.dependsOn(task, classpathCheck);
            }

            if (variantType.isBaseModule() && globalScope.hasDynamicFeatures()) {
                TaskProvider<CheckMultiApkLibrariesTask> checkMultiApkLibrariesTask =
                        taskFactory.register(new CheckMultiApkLibrariesTask.CreationAction(scope));

                TaskFactoryUtils.dependsOn(task, checkMultiApkLibrariesTask);
            }
            return;
        }

        super.createVariantPreBuildTask(scope);
    }

    @NonNull
    @Override
    protected Set<ScopeType> getJavaResMergingScopes(
            @NonNull VariantScope variantScope, @NonNull QualifiedContent.ContentType contentType) {
        if (variantScope.consumesFeatureJars() && contentType == RESOURCES) {
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
            taskFactory.register(new ModuleMetadataWriterTask.CreationAction(variantScope));
        }

        TaskProvider<? extends Task> applicationIdWriterTask =
                taskFactory.register(new ApplicationIdWriterTask.CreationAction(variantScope));

        TextResourceFactory resources = project.getResources().getText();
        // this builds the dependencies from the task, and its output is the textResource.
        variantScope.getVariantData().applicationIdTextResource =
                resources.fromFile(applicationIdWriterTask);
    }

    private static File getIncrementalFolder(VariantScope variantScope, String taskName) {
        return new File(variantScope.getIncrementalDir(taskName), variantScope.getDirName());
    }

    private void createDynamicBundleTask(@NonNull VariantScope scope) {

        // If namespaced resources are enabled, LINKED_RES_FOR_BUNDLE is not generated,
        // and the bundle can't be created. For now, just don't add the bundle task.
        // TODO(b/111168382): Remove this
        if (scope.getGlobalScope().getExtension().getAaptOptions().getNamespaced()) {
            return;
        }

        taskFactory.register(
                new PerModuleBundleTask.CreationAction(
                        scope, packagesCustomClassDependencies(scope, projectOptions)));
        if (addBundleDependenciesTask(scope)) {
            taskFactory.register(new PerModuleReportDependenciesTask.CreationAction(scope));
        }

        if (scope.getType().isBaseModule()) {
            taskFactory.register(new ParseIntegrityConfigTask.CreationAction(scope));
            taskFactory.register(new PackageBundleTask.CreationAction(scope));
            taskFactory.register(new FinalizeBundleTask.CreationAction(scope));
            if (addBundleDependenciesTask(scope)) {
                taskFactory.register(new BundleReportDependenciesTask.CreationAction(scope));
            }

            taskFactory.register(new BundleToApkTask.CreationAction(scope));
            taskFactory.register(new BundleToStandaloneApkTask.CreationAction(scope));

            taskFactory.register(new ExtractApksTask.CreationAction(scope));
        }
    }

    private void createMergeResourcesTasks(@NonNull VariantScope variantScope) {
        // The "big merge" of all resources, will merge and compile resources that will later
        // be used for linking.
        createMergeResourcesTask(
                variantScope,
                true,
                Sets.immutableEnumSet(MergeResources.Flag.PROCESS_VECTOR_DRAWABLES));

        // TODO(b/138780301): Also use it in android tests.
        if (projectOptions.get(BooleanOption.ENABLE_APP_COMPILE_TIME_R_CLASS)
                && !variantScope.getType().isForTesting()
                && !variantScope.getGlobalScope().getExtension().getAaptOptions().getNamespaced()) {
            // The "small merge" of only the app's local resources (can be multiple source-sets, but
            // most of the time it's just one). This is used by the Process for generating the local
            // R-def.txt file containing a list of resources defined in this module.
            basicCreateMergeResourcesTask(
                    variantScope,
                    MergeType.PACKAGE,
                    variantScope.getIntermediateDir(InternalArtifactType.PACKAGED_RES.INSTANCE),
                    false,
                    false,
                    false,
                    ImmutableSet.of(),
                    null);
        }
    }

    private static boolean addBundleDependenciesTask(@NonNull VariantScope scope) {
        return !scope.getVariantConfiguration().getBuildType().isDebuggable();
    }
}
