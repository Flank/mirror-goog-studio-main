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

import static com.android.SdkConstants.FD_JNI;
import static com.android.SdkConstants.FN_CLASSES_JAR;
import static com.android.SdkConstants.FN_INTERMEDIATE_RES_JAR;
import static com.android.SdkConstants.FN_PUBLIC_TXT;
import static com.android.build.gradle.internal.scope.InternalArtifactType.JAVAC;

import android.databinding.tool.DataBindingBuilder;
import com.android.annotations.NonNull;
import com.android.build.api.transform.QualifiedContent.DefaultContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.Transform;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.pipeline.ExtendedContentType;
import com.android.build.gradle.internal.pipeline.OriginalStream;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.pipeline.TransformTask;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.TaskOutputHolder;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.MergeConsumerProguardFilesConfigAction;
import com.android.build.gradle.internal.tasks.PackageRenderscriptConfigAction;
import com.android.build.gradle.internal.transforms.LibraryAarJarsTransform;
import com.android.build.gradle.internal.transforms.LibraryBaseTransform;
import com.android.build.gradle.internal.transforms.LibraryIntermediateJarsTransform;
import com.android.build.gradle.internal.transforms.LibraryJniLibsTransform;
import com.android.build.gradle.internal.variant.VariantHelper;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.tasks.AndroidZip;
import com.android.build.gradle.tasks.BuildArtifactReportTask;
import com.android.build.gradle.tasks.ExtractAnnotations;
import com.android.build.gradle.tasks.MergeResources;
import com.android.build.gradle.tasks.MergeSourceSetFolders;
import com.android.build.gradle.tasks.VerifyLibraryResourcesTask;
import com.android.build.gradle.tasks.ZipMergingTask;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.errors.EvalIssueException;
import com.android.builder.errors.EvalIssueReporter.Type;
import com.android.builder.profile.Recorder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan.ExecutionType;
import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/** TaskManager for creating tasks in an Android library project. */
public class LibraryTaskManager extends TaskManager {

    private Task assembleDefault;

    public LibraryTaskManager(
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
        final GradleVariantConfiguration variantConfig = variantScope.getVariantConfiguration();

        GlobalScope globalScope = variantScope.getGlobalScope();

        final String projectPath = project.getPath();
        final String variantName = variantScope.getFullVariantName();

        createAnchorTasks(variantScope);

        // Create all current streams (dependencies mostly at this point)
        createDependencyStreams(variantScope);

        createCheckManifestTask(variantScope);

        taskFactory.create(
                new BuildArtifactReportTask.BuildArtifactReportConfigAction(variantScope));

        // Add a task to create the res values
        recorder.record(
                ExecutionType.LIB_TASK_MANAGER_CREATE_GENERATE_RES_VALUES_TASK,
                projectPath,
                variantName,
                () -> createGenerateResValuesTask(variantScope));

        // Add a task to process the manifest(s)
        recorder.record(
                ExecutionType.LIB_TASK_MANAGER_CREATE_MERGE_MANIFEST_TASK,
                projectPath,
                variantName,
                () -> createMergeLibManifestsTask(variantScope));

        // Add a task to compile renderscript files.
        recorder.record(
                ExecutionType.LIB_TASK_MANAGER_CREATE_CREATE_RENDERSCRIPT_TASK,
                projectPath,
                variantName,
                () -> createRenderscriptTask(variantScope));

        recorder.record(
                ExecutionType.LIB_TASK_MANAGER_CREATE_MERGE_RESOURCES_TASK,
                projectPath,
                variantName,
                () -> createMergeResourcesTask(variantScope));

        // Add tasks to compile shader
        recorder.record(
                ExecutionType.LIB_TASK_MANAGER_CREATE_SHADER_TASK,
                projectPath,
                variantName,
                () -> createShaderTask(variantScope));

        // Add a task to merge the assets folders
        recorder.record(
                ExecutionType.LIB_TASK_MANAGER_CREATE_MERGE_ASSETS_TASK,
                projectPath,
                variantName,
                () -> {
                    createMergeAssetsTask(variantScope);
                    createLibraryAssetsTask(variantScope);
                });

        // Add a task to create the BuildConfig class
        recorder.record(
                ExecutionType.LIB_TASK_MANAGER_CREATE_BUILD_CONFIG_TASK,
                projectPath,
                variantName,
                () -> createBuildConfigTask(variantScope));

        recorder.record(
                ExecutionType.LIB_TASK_MANAGER_CREATE_PROCESS_RES_TASK,
                projectPath,
                variantName,
                () -> {
                    // Add a task to generate resource source files, directing the location
                    // of the r.txt file to be directly in the bundle.
                    createProcessResTask(
                            variantScope,
                            new File(
                                    globalScope.getIntermediatesDir(),
                                    "symbols/"
                                            + variantScope
                                                    .getVariantData()
                                                    .getVariantConfiguration()
                                                    .getDirName()),
                            variantScope.getProcessResourcePackageOutputDirectory(),
                            null,
                            // Switch to package where possible so we stop merging resources in
                            // libraries
                            MergeType.PACKAGE,
                            globalScope.getProjectBaseName());

                    // Only verify resources if in Release and not namespaced.
                    if (!variantScope.getVariantConfiguration().getBuildType().isDebuggable()
                            && !Boolean.TRUE.equals(
                                    variantScope
                                            .getGlobalScope()
                                            .getExtension()
                                            .getAaptOptions()
                                            .getNamespaced())) {
                        createVerifyLibraryResTask(variantScope, MergeType.MERGE);
                    }

                    // process java resources only, the merge is setup after
                    // the task to generate intermediate jars for project to project publishing.
                    createProcessJavaResTask(variantScope);
                });

        recorder.record(
                ExecutionType.LIB_TASK_MANAGER_CREATE_AIDL_TASK,
                projectPath,
                variantName,
                () -> createAidlTask(variantScope));

        // Add a compile task
        recorder.record(
                ExecutionType.LIB_TASK_MANAGER_CREATE_COMPILE_TASK,
                projectPath,
                variantName,
                () -> {
                    // Add data binding tasks if enabled
                    createDataBindingTasksIfNecessary(variantScope, MergeType.PACKAGE);

                    JavaCompile javacTask = createJavacTask(variantScope);
                    addJavacClassesStream(variantScope);
                    TaskManager.setJavaCompilerTask(javacTask, variantScope);
                });

        // Add dependencies on NDK tasks if NDK plugin is applied.
        if (!isComponentModelPlugin()) {
            // Add NDK tasks
            recorder.record(
                    ExecutionType.LIB_TASK_MANAGER_CREATE_NDK_TASK,
                    projectPath,
                    variantName,
                    () -> createNdkTasks(variantScope));
        }
        variantScope.setNdkBuildable(getNdkBuildable(variantScope.getVariantData()));

        // External native build
        recorder.record(
                ExecutionType.LIB_TASK_MANAGER_CREATE_EXTERNAL_NATIVE_BUILD_TASK,
                projectPath,
                variantName,
                () -> {
                    createExternalNativeBuildJsonGenerators(variantScope);
                    createExternalNativeBuildTasks(variantScope);
                });

        // TODO not sure what to do about this...
        createMergeJniLibFoldersTasks(variantScope);
        createStripNativeLibraryTask(taskFactory, variantScope);

        taskFactory.create(new PackageRenderscriptConfigAction(variantScope));

        // merge consumer proguard files from different build types and flavors
        recorder.record(
                ExecutionType.LIB_TASK_MANAGER_CREATE_MERGE_PROGUARD_FILE_TASK,
                projectPath,
                variantName,
                () -> taskFactory.create(new MergeConsumerProguardFilesConfigAction(variantScope)));

        // Some versions of retrolambda remove the actions from the extract annotations task.
        // TODO: remove this hack once tests are moved to a version that doesn't do this
        // b/37564303
        if (projectOptions.get(BooleanOption.ENABLE_EXTRACT_ANNOTATIONS)) {
            taskFactory.create(new ExtractAnnotations.ConfigAction(extension, variantScope));
        }

        final boolean instrumented =
                variantConfig.getBuildType().isTestCoverageEnabled()
                        && !variantScope.getInstantRunBuildContext().isInInstantRunMode();

        recorder.record(
                ExecutionType.LIB_TASK_MANAGER_CREATE_POST_COMPILATION_TASK,
                projectPath,
                variantName,
                new Recorder.Block<Void>() {
                    @Override
                    public Void call() throws Exception {
                        TransformManager transformManager = variantScope.getTransformManager();

                        // ----- Code Coverage first -----
                        if (instrumented) {
                            createJacocoTransform(variantScope);
                        }

                        // ----- External Transforms -----
                        // apply all the external transforms.
                        List<Transform> customTransforms = extension.getTransforms();
                        List<List<Object>> customTransformsDependencies =
                                extension.getTransformsDependencies();

                        for (int i = 0, count = customTransforms.size(); i < count; i++) {
                            Transform transform = customTransforms.get(i);

                            // Check the transform only applies to supported scopes for libraries:
                            // We cannot transform scopes that are not packaged in the library
                            // itself.
                            Sets.SetView<? super Scope> difference =
                                    Sets.difference(
                                            transform.getScopes(), TransformManager.PROJECT_ONLY);
                            if (!difference.isEmpty()) {
                                String scopes = difference.toString();
                                androidBuilder
                                        .getIssueReporter()
                                        .reportError(
                                                Type.GENERIC,
                                                new EvalIssueException(
                                                        String.format(
                                                                "Transforms with scopes '%s' cannot be applied to library projects.",
                                                                scopes)));
                            }

                            List<Object> deps = customTransformsDependencies.get(i);
                            transformManager
                                    .addTransform(taskFactory, variantScope, transform)
                                    .ifPresent(
                                            t -> {
                                                if (!deps.isEmpty()) {
                                                    t.dependsOn(deps);
                                                }

                                                // if the task is a no-op then we make assemble task
                                                // depend on it.
                                                if (transform.getScopes().isEmpty()) {
                                                    variantScope.getAssembleTask().dependsOn(t);
                                                }
                                            });
                        }

                        String packageName = variantConfig.getPackageFromManifest();

                        // Now add transforms for intermediate publishing (projects to projects).
                        File jarOutputFolder = variantScope.getIntermediateJarOutputFolder();
                        File mainClassJar = new File(jarOutputFolder, FN_CLASSES_JAR);
                        File mainResJar = new File(jarOutputFolder, FN_INTERMEDIATE_RES_JAR);
                        LibraryIntermediateJarsTransform intermediateTransform =
                                new LibraryIntermediateJarsTransform(
                                        mainClassJar,
                                        mainResJar,
                                        null,
                                        packageName,
                                        extension.getPackageBuildConfig());
                        excludeDataBindingClassesIfNecessary(variantScope, intermediateTransform);

                        Optional<TransformTask> intermediateTransformTask =
                                transformManager.addTransform(
                                        taskFactory, variantScope, intermediateTransform);

                        intermediateTransformTask.ifPresent(
                                t -> {
                                    // publish the intermediate classes.jar
                                    variantScope
                                            .getBuildArtifactsHolder()
                                            .appendArtifact(
                                                    InternalArtifactType.LIBRARY_CLASSES,
                                                    ImmutableList.of(mainClassJar),
                                                    t);
                                    // publish the res jar
                                    variantScope
                                            .getBuildArtifactsHolder()
                                            .appendArtifact(
                                                    InternalArtifactType.LIBRARY_JAVA_RES,
                                                    ImmutableList.of(mainResJar),
                                                    t);
                                });

                        // Create a jar with both classes and java resources.  This artifact is not
                        // used by the Android application plugin and the task usually don't need to
                        // be executed.  The artifact is useful for other Gradle users who needs the
                        // 'jar' artifact as API dependency.
                        taskFactory.create(new ZipMergingTask.ConfigAction(variantScope));

                        // now add a transform that will take all the native libs and package
                        // them into an intermediary folder. This processes only the PROJECT
                        // scope.
                        final File intermediateJniLibsFolder = new File(jarOutputFolder, FD_JNI);

                        LibraryJniLibsTransform intermediateJniTransform =
                                new LibraryJniLibsTransform(
                                        "intermediateJniLibs",
                                        intermediateJniLibsFolder,
                                        TransformManager.PROJECT_ONLY);
                        Optional<TransformTask> task =
                                transformManager.addTransform(
                                        taskFactory, variantScope, intermediateJniTransform);
                        task.ifPresent(
                                t -> {
                                    // publish the jni folder as intermediate
                                    variantScope
                                            .getBuildArtifactsHolder()
                                            .appendArtifact(
                                                    InternalArtifactType.LIBRARY_JNI,
                                                    ImmutableList.of(intermediateJniLibsFolder),
                                                    t);
                                });

                        // Now go back to fill the pipeline with transforms used when
                        // publishing the AAR

                        // first merge the resources. This takes the PROJECT and LOCAL_DEPS
                        // and merges them together.
                        createMergeJavaResTransform(variantScope);

                        // ----- Minify next -----
                        maybeCreateJavaCodeShrinkerTransform(variantScope);
                        maybeCreateResourcesShrinkerTransform(variantScope);

                        // now add a transform that will take all the class/res and package them
                        // into the main and secondary jar files that goes in the AAR.
                        // This transform technically does not use its transform output, but that's
                        // ok. We use the transform mechanism to get incremental data from
                        // the streams.
                        // This is used for building the AAR.

                        File classesJar = variantScope.getAarClassesJar();
                        File libsDirectory = variantScope.getAarLibsDirectory();

                        LibraryAarJarsTransform transform =
                                new LibraryAarJarsTransform(
                                        classesJar,
                                        libsDirectory,
                                        variantScope.hasOutput(
                                                        InternalArtifactType
                                                                .ANNOTATIONS_TYPEDEF_FILE)
                                                ? variantScope.getOutput(
                                                        InternalArtifactType
                                                                .ANNOTATIONS_TYPEDEF_FILE)
                                                : null,
                                        packageName,
                                        extension.getPackageBuildConfig());

                        excludeDataBindingClassesIfNecessary(variantScope, transform);

                        Optional<TransformTask> libraryJarTransformTask =
                                transformManager.addTransform(taskFactory, variantScope, transform);
                        libraryJarTransformTask.ifPresent(
                                t -> {
                                    variantScope
                                            .getBuildArtifactsHolder()
                                            .appendArtifact(
                                                    InternalArtifactType.AAR_MAIN_JAR,
                                                    ImmutableList.of(classesJar),
                                                    t);
                                    variantScope
                                            .getBuildArtifactsHolder()
                                            .appendArtifact(
                                                    InternalArtifactType.AAR_LIBS_DIRECTORY,
                                                    ImmutableList.of(libsDirectory),
                                                    t);
                                });

                        // now add a transform that will take all the native libs and package
                        // them into the libs folder of the bundle. This processes both the PROJECT
                        // and the LOCAL_PROJECT scopes
                        final File jniLibsFolder =
                                variantScope.getIntermediateDir(
                                        InternalArtifactType.LIBRARY_AND_LOCAL_JARS_JNI);
                        LibraryJniLibsTransform jniTransform =
                                new LibraryJniLibsTransform(
                                        "syncJniLibs",
                                        jniLibsFolder,
                                        TransformManager.SCOPE_FULL_LIBRARY_WITH_LOCAL_JARS);
                        Optional<TransformTask> jniPackagingTask =
                                transformManager.addTransform(
                                        taskFactory, variantScope, jniTransform);
                        jniPackagingTask.ifPresent(
                                t ->
                                        variantScope
                                                .getBuildArtifactsHolder()
                                                .appendArtifact(
                                                        InternalArtifactType
                                                                .LIBRARY_AND_LOCAL_JARS_JNI,
                                                        ImmutableList.of(jniLibsFolder),
                                                        t));
                        return null;
                    }
                });
        recorder.record(
                ExecutionType.LIB_TASK_MANAGER_CREATE_LINT_TASK,
                projectPath,
                variantName,
                () -> createLintTasks(variantScope));
        createBundleTask(variantScope);
    }

    private void createBundleTask(@NonNull VariantScope variantScope) {
        final AndroidZip bundle =
                taskFactory.create(new AndroidZip.ConfigAction(extension, variantScope));

        variantScope.getAssembleTask().dependsOn(bundle);

        // if the variant is the default published, then publish the aar
        // FIXME: only generate the tasks if this is the default published variant?
        if (extension
                .getDefaultPublishConfig()
                .equals(variantScope.getVariantConfiguration().getFullName())) {
            VariantHelper.setupArchivesConfig(
                    project, variantScope.getVariantDependencies().getRuntimeClasspath());

            // add the artifact that will be published.
            // it must be default so that it can be found by other library modules during
            // publishing to a maven repo. Adding it to "archives" only allows the current
            // module to be published by not to be found by consumer who are themselves published
            // (leading to their pom not containing dependencies).
            project.getArtifacts().add("default", bundle);
        }
    }

    @Override
    protected void createDependencyStreams(@NonNull VariantScope variantScope) {
        super.createDependencyStreams(variantScope);

        // add the same jars twice in the same stream as the EXTERNAL_LIB in the task manager
        // so that filtering of duplicates in proguard can work.
        variantScope
                .getTransformManager()
                .addStream(
                        OriginalStream.builder(project, "local-deps-classes")
                                .addContentTypes(TransformManager.CONTENT_CLASS)
                                .addScope(InternalScope.LOCAL_DEPS)
                                .setFileCollection(variantScope.getLocalPackagedJars())
                                .build());

        variantScope
                .getTransformManager()
                .addStream(
                        OriginalStream.builder(project, "local-deps-native")
                                .addContentTypes(
                                        DefaultContentType.RESOURCES,
                                        ExtendedContentType.NATIVE_LIBS)
                                .addScope(InternalScope.LOCAL_DEPS)
                                .setFileCollection(variantScope.getLocalPackagedJars())
                                .build());
    }

    private void createMergeResourcesTask(@NonNull VariantScope variantScope) {
        ImmutableSet<MergeResources.Flag> flags;
        if (Boolean.TRUE.equals(
                variantScope.getGlobalScope().getExtension().getAaptOptions().getNamespaced())) {
            flags = Sets.immutableEnumSet(MergeResources.Flag.REMOVE_RESOURCE_NAMESPACES);
        } else {
            flags = ImmutableSet.of();
        }

        // Create a merge task to only merge the resources from this library and not
        // the dependencies. This is what gets packaged in the aar.
        MergeResources mergeResourceTask =
                basicCreateMergeResourcesTask(
                        variantScope,
                        MergeType.PACKAGE,
                        variantScope.getIntermediateDir(InternalArtifactType.PACKAGED_RES),
                        false,
                        false,
                        false,
                        flags);

        // Add a task to merge the resource folders, including the libraries, in order to
        // generate the R.txt file with all the symbols, including the ones from
        // the dependencies.
        createMergeResourcesTask(variantScope, false /*processResources*/);

        mergeResourceTask.setPublicFile(
                variantScope
                        .getBuildArtifactsHolder()
                        .appendArtifact(
                                InternalArtifactType.PUBLIC_RES, mergeResourceTask, FN_PUBLIC_TXT));
    }

    @Override
    protected void postJavacCreation(@NonNull VariantScope scope) {
        // create an anchor collection for usage inside the same module (unit tests basically)
        ConfigurableFileCollection fileCollection =
                scope.createAnchorOutput(TaskOutputHolder.AnchorOutputType.ALL_CLASSES);
        fileCollection.from(scope.getBuildArtifactsHolder().getArtifactFiles(JAVAC));
        fileCollection.from(scope.getVariantData().getAllPreJavacGeneratedBytecode());
        fileCollection.from(scope.getVariantData().getAllPostJavacGeneratedBytecode());
    }

    private void excludeDataBindingClassesIfNecessary(
            @NonNull VariantScope variantScope, @NonNull LibraryBaseTransform transform) {
        if (!extension.getDataBinding().isEnabled()) {
            return;
        }
        transform.addExcludeListProvider(
                () -> {
                    File excludeFile =
                            variantScope.getVariantData().getType().isExportDataBindingClassList()
                                    ? variantScope.getGeneratedClassListOutputFileForDataBinding()
                                    : null;
                    File dataBindingFolder = variantScope.getBuildFolderForDataBindingCompiler();
                    return dataBindingBuilder.getJarExcludeList(
                            variantScope.getVariantData().getLayoutXmlProcessor(),
                            excludeFile,
                            dataBindingFolder);
                });
    }

    public void createLibraryAssetsTask(@NonNull VariantScope scope) {

        MergeSourceSetFolders mergeAssetsTask =
                taskFactory.create(new MergeSourceSetFolders.LibraryAssetConfigAction(scope));

        mergeAssetsTask.dependsOn(scope.getAssetGenTask());
        scope.setMergeAssetsTask(mergeAssetsTask);
    }

    @NonNull
    @Override
    protected Set<? super Scope> getResMergingScopes(@NonNull VariantScope variantScope) {
        if (variantScope.getTestedVariantData() != null) {
            return TransformManager.SCOPE_FULL_PROJECT;
        }
        return TransformManager.PROJECT_ONLY;
    }

    @Override
    protected boolean isLibrary() {
        return true;
    }

    private Task getAssembleDefault() {
        if (assembleDefault == null) {
            assembleDefault = project.getTasks().findByName("assembleDefault");
        }
        return assembleDefault;
    }

    public void createVerifyLibraryResTask(
            @NonNull VariantScope scope, @NonNull MergeType mergeType) {
        VerifyLibraryResourcesTask verifyLibraryResources =
                taskFactory.create(new VerifyLibraryResourcesTask.ConfigAction(scope, mergeType));

        scope.getAssembleTask().dependsOn(verifyLibraryResources);
    }
}
