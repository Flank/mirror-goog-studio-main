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
import static com.android.build.gradle.internal.scope.InternalArtifactType.JAVAC;

import android.databinding.tool.DataBindingBuilder;
import com.android.annotations.NonNull;
import com.android.build.api.component.impl.ComponentPropertiesImpl;
import com.android.build.api.component.impl.TestComponentPropertiesImpl;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.ScopeType;
import com.android.build.api.variant.impl.VariantPropertiesImpl;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.internal.feature.BundleAllClasses;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.tasks.ApkZipPackagingTask;
import com.android.build.gradle.internal.tasks.AppClasspathCheckTask;
import com.android.build.gradle.internal.tasks.AppPreBuildTask;
import com.android.build.gradle.internal.tasks.ApplicationIdWriterTask;
import com.android.build.gradle.internal.tasks.CheckManifest;
import com.android.build.gradle.internal.tasks.CheckMultiApkLibrariesTask;
import com.android.build.gradle.internal.tasks.ModuleMetadataWriterTask;
import com.android.build.gradle.internal.tasks.StripDebugSymbolsTask;
import com.android.build.gradle.internal.tasks.TestPreBuildTask;
import com.android.build.gradle.internal.tasks.factory.TaskFactoryUtils;
import com.android.build.gradle.internal.tasks.featuresplit.PackagedDependenciesWriterTask;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.tasks.ExtractDeepLinksTask;
import com.android.build.gradle.tasks.MergeResources;
import com.android.builder.core.VariantType;
import com.android.builder.profile.Recorder;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.Set;
import org.gradle.api.Task;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.resources.TextResourceFactory;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/** TaskManager for creating tasks in an Android application project. */
public abstract class AbstractAppTaskManager<VariantPropertiesT extends VariantPropertiesImpl>
        extends TaskManager<VariantPropertiesT> {

    protected AbstractAppTaskManager(
            @NonNull GlobalScope globalScope,
            @NonNull DataBindingBuilder dataBindingBuilder,
            @NonNull BaseExtension extension,
            @NonNull ToolingModelBuilderRegistry toolingRegistry,
            @NonNull Recorder recorder) {
        super(
                globalScope,
                dataBindingBuilder,
                extension,
                toolingRegistry,
                recorder);
    }

    protected void createCommonTasks(
            @NonNull VariantPropertiesT appVariantProperties,
            @NonNull List<VariantPropertiesT> allComponentsWithLint) {
        createAnchorTasks(appVariantProperties);

        taskFactory.register(new ExtractDeepLinksTask.CreationAction(appVariantProperties));

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

        taskFactory.register(new ApkZipPackagingTask.CreationAction(appVariantProperties));
    }

    protected void createCompileTask(@NonNull VariantPropertiesImpl variantProperties) {
        TaskProvider<? extends JavaCompile> javacTask = createJavacTask(variantProperties);
        addJavacClassesStream(variantProperties);
        setJavaCompilerTask(javacTask, variantProperties);
        createPostCompilationTasks(variantProperties);
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
}
