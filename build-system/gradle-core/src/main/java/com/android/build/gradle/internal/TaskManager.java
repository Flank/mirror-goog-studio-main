/*
 * Copyright (C) 2014 The Android Open Source Project
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

import static com.android.SdkConstants.FD_ASSETS;
import static com.android.SdkConstants.FD_RES;
import static com.android.SdkConstants.FN_ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.FN_LINT_JAR;
import static com.android.SdkConstants.FN_RESOURCE_TEXT;
import static com.android.SdkConstants.FN_SPLIT_LIST;
import static com.android.build.gradle.internal.dependency.VariantDependencies.CONFIG_NAME_LINTCHECKS;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.EXTERNAL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.MODULE;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.CLASSES;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.DATA_BINDING_ARTIFACT;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.DATA_BINDING_BASE_CLASS_LOG_ARTIFACT;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.JAVA_RES;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.JNI;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.PROGUARD_RULES;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH;
import static com.android.build.gradle.internal.scope.InternalArtifactType.AAPT_FRIENDLY_MERGED_MANIFESTS;
import static com.android.build.gradle.internal.scope.InternalArtifactType.ANNOTATION_PROCESSOR_LIST;
import static com.android.build.gradle.internal.scope.InternalArtifactType.APK_MAPPING;
import static com.android.build.gradle.internal.scope.InternalArtifactType.DATA_BINDING_BASE_CLASS_LOGS_DEPENDENCY_ARTIFACTS;
import static com.android.build.gradle.internal.scope.InternalArtifactType.DATA_BINDING_BASE_CLASS_SOURCE_OUT;
import static com.android.build.gradle.internal.scope.InternalArtifactType.DATA_BINDING_DEPENDENCY_ARTIFACTS;
import static com.android.build.gradle.internal.scope.InternalArtifactType.INSTANT_RUN_MAIN_APK_RESOURCES;
import static com.android.build.gradle.internal.scope.InternalArtifactType.INSTANT_RUN_MERGED_MANIFESTS;
import static com.android.build.gradle.internal.scope.InternalArtifactType.JAVAC;
import static com.android.build.gradle.internal.scope.InternalArtifactType.LIBRARY_MANIFEST;
import static com.android.build.gradle.internal.scope.InternalArtifactType.LINKED_RES_FOR_BUNDLE;
import static com.android.build.gradle.internal.scope.InternalArtifactType.LINT_JAR;
import static com.android.build.gradle.internal.scope.InternalArtifactType.MANIFEST_MERGE_REPORT;
import static com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_ASSETS;
import static com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_MANIFESTS;
import static com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_NOT_COMPILED_RES;
import static com.android.build.gradle.internal.scope.InternalArtifactType.MOCKABLE_JAR;
import static com.android.build.gradle.internal.scope.InternalArtifactType.PLATFORM_R_TXT;
import static com.android.build.gradle.internal.scope.InternalArtifactType.PUBLISHED_DEX;
import static com.android.build.gradle.internal.scope.InternalArtifactType.PUBLISHED_JAVA_RES;
import static com.android.build.gradle.internal.scope.InternalArtifactType.PUBLISHED_NATIVE_LIBS;
import static com.android.builder.core.BuilderConstants.CONNECTED;
import static com.android.builder.core.BuilderConstants.DEVICE;
import static com.android.builder.core.VariantType.ANDROID_TEST;
import static com.android.builder.core.VariantType.FEATURE;
import static com.android.builder.core.VariantType.LIBRARY;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import android.databinding.tool.DataBindingBuilder;
import android.databinding.tool.DataBindingCompilerArgs;
import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.OutputFile;
import com.android.build.api.transform.QualifiedContent.DefaultContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.Transform;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.api.AndroidSourceSet;
import com.android.build.gradle.api.AnnotationProcessorOptions;
import com.android.build.gradle.api.JavaCompileOptions;
import com.android.build.gradle.internal.aapt.AaptGeneration;
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet;
import com.android.build.gradle.internal.api.artifact.BuildableArtifactImpl;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.coverage.JacocoConfigurations;
import com.android.build.gradle.internal.coverage.JacocoReportTask;
import com.android.build.gradle.internal.dsl.AbiSplitOptions;
import com.android.build.gradle.internal.dsl.CoreSigningConfig;
import com.android.build.gradle.internal.dsl.PackagingOptions;
import com.android.build.gradle.internal.incremental.BuildInfoLoaderTask;
import com.android.build.gradle.internal.incremental.BuildInfoWriterTask;
import com.android.build.gradle.internal.incremental.InstantRunAnchorTaskConfigAction;
import com.android.build.gradle.internal.model.CoreExternalNativeBuild;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.build.gradle.internal.packaging.GradleKeystoreHelper;
import com.android.build.gradle.internal.pipeline.ExtendedContentType;
import com.android.build.gradle.internal.pipeline.OriginalStream;
import com.android.build.gradle.internal.pipeline.StreamFilter;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.pipeline.TransformTask;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.publishing.PublishingSpecs;
import com.android.build.gradle.internal.res.GenerateLibraryRFileTask;
import com.android.build.gradle.internal.res.LinkAndroidResForBundleTask;
import com.android.build.gradle.internal.res.LinkApplicationAndroidResourcesTask;
import com.android.build.gradle.internal.res.namespaced.NamespacedResourcesTaskManager;
import com.android.build.gradle.internal.scope.CodeShrinker;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.scope.VariantScope.Java8LangSupport;
import com.android.build.gradle.internal.tasks.AndroidReportTask;
import com.android.build.gradle.internal.tasks.CheckManifest;
import com.android.build.gradle.internal.tasks.CheckProguardFiles;
import com.android.build.gradle.internal.tasks.DependencyReportTask;
import com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask;
import com.android.build.gradle.internal.tasks.ExtractProguardFiles;
import com.android.build.gradle.internal.tasks.ExtractTryWithResourcesSupportJar;
import com.android.build.gradle.internal.tasks.GenerateApkDataTask;
import com.android.build.gradle.internal.tasks.InstallVariantTask;
import com.android.build.gradle.internal.tasks.LintCompile;
import com.android.build.gradle.internal.tasks.MockableAndroidJarTask;
import com.android.build.gradle.internal.tasks.PipelineToPublicationTask;
import com.android.build.gradle.internal.tasks.PlatformAttrExtractorTask;
import com.android.build.gradle.internal.tasks.PrepareLintJar;
import com.android.build.gradle.internal.tasks.SigningReportTask;
import com.android.build.gradle.internal.tasks.SourceSetsTask;
import com.android.build.gradle.internal.tasks.TaskInputHelper;
import com.android.build.gradle.internal.tasks.TestServerTask;
import com.android.build.gradle.internal.tasks.UninstallTask;
import com.android.build.gradle.internal.tasks.ValidateSigningTask;
import com.android.build.gradle.internal.tasks.databinding.DataBindingExportBuildInfoTask;
import com.android.build.gradle.internal.tasks.databinding.DataBindingGenBaseClassesTask;
import com.android.build.gradle.internal.tasks.databinding.DataBindingMergeArtifactsTransform;
import com.android.build.gradle.internal.tasks.databinding.DataBindingMergeGenClassLogTransform;
import com.android.build.gradle.internal.test.AbstractTestDataImpl;
import com.android.build.gradle.internal.test.TestDataImpl;
import com.android.build.gradle.internal.transforms.BuiltInShrinkerTransform;
import com.android.build.gradle.internal.transforms.CustomClassTransform;
import com.android.build.gradle.internal.transforms.D8MainDexListTransform;
import com.android.build.gradle.internal.transforms.DesugarTransform;
import com.android.build.gradle.internal.transforms.DexArchiveBuilderTransform;
import com.android.build.gradle.internal.transforms.DexArchiveBuilderTransformBuilder;
import com.android.build.gradle.internal.transforms.DexMergerTransform;
import com.android.build.gradle.internal.transforms.DexMergerTransformCallable;
import com.android.build.gradle.internal.transforms.DexTransform;
import com.android.build.gradle.internal.transforms.ExternalLibsMergerTransform;
import com.android.build.gradle.internal.transforms.ExtractJarsTransform;
import com.android.build.gradle.internal.transforms.FixStackFramesTransform;
import com.android.build.gradle.internal.transforms.JacocoTransform;
import com.android.build.gradle.internal.transforms.JarMergingTransform;
import com.android.build.gradle.internal.transforms.MainDexListTransform;
import com.android.build.gradle.internal.transforms.MergeJavaResourcesTransform;
import com.android.build.gradle.internal.transforms.MultiDexTransform;
import com.android.build.gradle.internal.transforms.PreDexTransform;
import com.android.build.gradle.internal.transforms.ProGuardTransform;
import com.android.build.gradle.internal.transforms.ProguardConfigurable;
import com.android.build.gradle.internal.transforms.ShrinkResourcesTransform;
import com.android.build.gradle.internal.transforms.StripDebugSymbolTransform;
import com.android.build.gradle.internal.variant.AndroidArtifactVariantData;
import com.android.build.gradle.internal.variant.ApkVariantData;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.MultiOutputPolicy;
import com.android.build.gradle.internal.variant.TaskContainer;
import com.android.build.gradle.internal.variant.TestVariantData;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.IntegerOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.options.StringOption;
import com.android.build.gradle.tasks.AidlCompile;
import com.android.build.gradle.tasks.BuildArtifactReportTask;
import com.android.build.gradle.tasks.CleanBuildCache;
import com.android.build.gradle.tasks.CompatibleScreensManifest;
import com.android.build.gradle.tasks.CopyOutputs;
import com.android.build.gradle.tasks.ExternalNativeBuildJsonTask;
import com.android.build.gradle.tasks.ExternalNativeBuildTask;
import com.android.build.gradle.tasks.ExternalNativeBuildTaskUtils;
import com.android.build.gradle.tasks.ExternalNativeCleanTask;
import com.android.build.gradle.tasks.ExternalNativeJsonGenerator;
import com.android.build.gradle.tasks.GenerateBuildConfig;
import com.android.build.gradle.tasks.GenerateResValues;
import com.android.build.gradle.tasks.GenerateSplitAbiRes;
import com.android.build.gradle.tasks.GenerateTestConfig;
import com.android.build.gradle.tasks.InstantRunResourcesApkBuilder;
import com.android.build.gradle.tasks.JavaPreCompileTask;
import com.android.build.gradle.tasks.LintGlobalTask;
import com.android.build.gradle.tasks.LintPerVariantTask;
import com.android.build.gradle.tasks.MainApkListPersistence;
import com.android.build.gradle.tasks.ManifestProcessorTask;
import com.android.build.gradle.tasks.MergeManifests;
import com.android.build.gradle.tasks.MergeResources;
import com.android.build.gradle.tasks.MergeSourceSetFolders;
import com.android.build.gradle.tasks.NdkCompile;
import com.android.build.gradle.tasks.PackageApplication;
import com.android.build.gradle.tasks.PackageSplitAbi;
import com.android.build.gradle.tasks.PackageSplitRes;
import com.android.build.gradle.tasks.PreColdSwapTask;
import com.android.build.gradle.tasks.ProcessManifest;
import com.android.build.gradle.tasks.ProcessTestManifest;
import com.android.build.gradle.tasks.RenderscriptCompile;
import com.android.build.gradle.tasks.ShaderCompile;
import com.android.build.gradle.tasks.SplitsDiscovery;
import com.android.build.gradle.tasks.factory.AndroidUnitTest;
import com.android.build.gradle.tasks.factory.JavaCompileConfigAction;
import com.android.build.gradle.tasks.factory.ProcessJavaResConfigAction;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.DefaultDexOptions;
import com.android.builder.core.DesugarProcessArgs;
import com.android.builder.core.VariantType;
import com.android.builder.dexing.DexerTool;
import com.android.builder.dexing.DexingType;
import com.android.builder.errors.EvalIssueReporter;
import com.android.builder.errors.EvalIssueReporter.Type;
import com.android.builder.model.DataBindingOptions;
import com.android.builder.model.SyncIssue;
import com.android.builder.profile.Recorder;
import com.android.builder.testing.ConnectedDeviceProvider;
import com.android.builder.testing.api.DeviceProvider;
import com.android.builder.testing.api.TestServer;
import com.android.builder.utils.FileCache;
import com.android.ide.common.build.ApkData;
import com.android.sdklib.AndroidVersion;
import com.android.utils.FileUtils;
import com.android.utils.StringHelper;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationVariant;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/** Manages tasks creation. */
public abstract class TaskManager {

    public static final String DIR_BUNDLES = "bundles";
    public static final String INSTALL_GROUP = "Install";
    public static final String BUILD_GROUP = BasePlugin.BUILD_GROUP;
    public static final String ANDROID_GROUP = "Android";
    public static final String FEATURE_SUFFIX = "Feature";

    // Task names. These cannot be AndroidTasks as in the component model world there is nothing to
    // force generateTasksBeforeEvaluate to happen before the variant tasks are created.
    public static final String MAIN_PREBUILD = "preBuild";
    public static final String UNINSTALL_ALL = "uninstallAll";
    public static final String DEVICE_CHECK = "deviceCheck";
    public static final String DEVICE_ANDROID_TEST = DEVICE + ANDROID_TEST.getSuffix();
    public static final String CONNECTED_CHECK = "connectedCheck";
    public static final String CONNECTED_ANDROID_TEST = CONNECTED + ANDROID_TEST.getSuffix();
    public static final String ASSEMBLE_ANDROID_TEST = "assembleAndroidTest";
    public static final String LINT = "lint";
    public static final String EXTRACT_PROGUARD_FILES = "extractProguardFiles";

    @NonNull protected final Project project;
    @NonNull protected final ProjectOptions projectOptions;
    @NonNull protected final AndroidBuilder androidBuilder;
    @NonNull protected final DataBindingBuilder dataBindingBuilder;
    @NonNull protected final SdkHandler sdkHandler;
    @NonNull protected final AndroidConfig extension;
    @NonNull protected final ToolingModelBuilderRegistry toolingRegistry;
    @NonNull protected final GlobalScope globalScope;
    @NonNull protected final Recorder recorder;
    @NonNull private final Logger logger;
    @Nullable private final FileCache buildCache;
    @NonNull protected final TaskFactory taskFactory;

    // Tasks. TODO: remove the mutable state from here.
    public MockableAndroidJarTask createMockableJar;

    public TaskManager(
            @NonNull GlobalScope globalScope,
            @NonNull Project project,
            @NonNull ProjectOptions projectOptions,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull DataBindingBuilder dataBindingBuilder,
            @NonNull AndroidConfig extension,
            @NonNull SdkHandler sdkHandler,
            @NonNull ToolingModelBuilderRegistry toolingRegistry,
            @NonNull Recorder recorder) {
        this.globalScope = globalScope;
        this.project = project;
        this.projectOptions = projectOptions;
        this.androidBuilder = androidBuilder;
        this.dataBindingBuilder = dataBindingBuilder;
        this.sdkHandler = sdkHandler;
        this.extension = extension;
        this.toolingRegistry = toolingRegistry;
        this.recorder = recorder;
        this.logger = Logging.getLogger(this.getClass());

        // It's too early to materialize the project-level cache, we'll need to get it from
        // globalScope later on.
        this.buildCache = globalScope.getBuildCache();

        taskFactory = new TaskFactoryImpl(project.getTasks());
    }

    public boolean isComponentModelPlugin() {
        return false;
    }

    @NonNull
    public TaskFactory getTaskFactory() {
        return taskFactory;
    }

    @NonNull
    public DataBindingBuilder getDataBindingBuilder() {
        return dataBindingBuilder;
    }

    /** Creates the tasks for a given BaseVariantData. */
    public abstract void createTasksForVariantScope(@NonNull VariantScope variantScope);

    /**
     * Returns a collection of buildables that creates native object.
     *
     * A buildable is considered to be any object that can be used as the argument to
     * Task.dependsOn.  This could be a Task or a BuildableModelElement (e.g. BinarySpec).
     */
    protected Collection<Object> getNdkBuildable(BaseVariantData variantData) {
        if (variantData.ndkCompileTask== null) {
            return Collections.emptyList();
        }
        return Collections.singleton(variantData.ndkCompileTask);
    }

    /**
     * Override to configure NDK data in the scope.
     */
    public void configureScopeForNdk(@NonNull VariantScope scope) {
        final BaseVariantData variantData = scope.getVariantData();
        scope.setNdkSoFolder(Collections.singleton(new File(
                scope.getGlobalScope().getIntermediatesDir(),
                "ndk/" + variantData.getVariantConfiguration().getDirName() + "/lib")));
        File objFolder = new File(scope.getGlobalScope().getIntermediatesDir(),
                "ndk/" + variantData.getVariantConfiguration().getDirName() + "/obj");
        for (Abi abi : NdkHandler.getAbiList()) {
            scope.addNdkDebuggableLibraryFolders(abi,
                    new File(objFolder, "local/" + abi.getName()));
        }

    }

    /**
     * Create tasks before the evaluation (on plugin apply). This is useful for tasks that could be
     * referenced by custom build logic.
     */
    public void createTasksBeforeEvaluate() {
        taskFactory.create(
                UNINSTALL_ALL,
                uninstallAllTask -> {
                    uninstallAllTask.setDescription("Uninstall all applications.");
                    uninstallAllTask.setGroup(INSTALL_GROUP);
                });

        taskFactory.create(
                DEVICE_CHECK,
                deviceCheckTask -> {
                    deviceCheckTask.setDescription(
                            "Runs all device checks using Device Providers and Test Servers.");
                    deviceCheckTask.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
                });

        taskFactory.create(
                CONNECTED_CHECK,
                connectedCheckTask -> {
                    connectedCheckTask.setDescription(
                            "Runs all device checks on currently connected devices.");
                    connectedCheckTask.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
                });

        taskFactory.create(MAIN_PREBUILD, task -> {});

        ExtractProguardFiles extractProguardFiles =
                taskFactory.create(EXTRACT_PROGUARD_FILES, ExtractProguardFiles.class, task -> {});
        // Make sure MAIN_PREBUILD runs first:
        extractProguardFiles.dependsOn(MAIN_PREBUILD);

        taskFactory.create(new SourceSetsTask.ConfigAction(extension));

        taskFactory.create(
                ASSEMBLE_ANDROID_TEST,
                assembleAndroidTestTask -> {
                    assembleAndroidTestTask.setGroup(BasePlugin.BUILD_GROUP);
                    assembleAndroidTestTask.setDescription("Assembles all the Test applications.");
                });

        taskFactory.create(new LintCompile.ConfigAction(globalScope));

        // Lint task is configured in afterEvaluate, but created upfront as it is used as an
        // anchor task.
        createGlobalLintTask();
        configureCustomLintChecksConfig();

        if (buildCache != null) {
            taskFactory.create(new CleanBuildCache.ConfigAction(globalScope));
        }

        // for testing only.
        taskFactory.create(
                new TaskConfigAction<ConfigAttrTask>() {
                    @NonNull
                    @Override
                    public String getName() {
                        return "resolveConfigAttr";
                    }

                    @NonNull
                    @Override
                    public Class<ConfigAttrTask> getType() {
                        return ConfigAttrTask.class;
                    }

                    @Override
                    public void execute(@NonNull ConfigAttrTask task) {
                        task.resolvable = true;
                    }
                });

        taskFactory.create(
                new TaskConfigAction<ConfigAttrTask>() {
                    @NonNull
                    @Override
                    public String getName() {
                        return "consumeConfigAttr";
                    }

                    @NonNull
                    @Override
                    public Class<ConfigAttrTask> getType() {
                        return ConfigAttrTask.class;
                    }

                    @Override
                    public void execute(@NonNull ConfigAttrTask task) {
                        task.consumable = true;
                    }
                });
    }

    private void configureCustomLintChecksConfig() {
        // create a single configuration to point to a project or a local file that contains
        // the lint.jar for this project.
        // This is not the configuration that consumes lint.jar artifacts from normal dependencies,
        // or publishes lint.jar to consumers. These are handled at the variant level.
        globalScope.setLintChecks(createCustomLintChecksConfig(project));
    }

    @NonNull
    public static Configuration createCustomLintChecksConfig(@NonNull Project project) {
        Configuration lintChecks = project.getConfigurations().maybeCreate(CONFIG_NAME_LINTCHECKS);
        lintChecks.setVisible(false);
        lintChecks.setDescription("Configuration to apply external lint check jar");
        lintChecks.setCanBeConsumed(false);
        return lintChecks;
    }

    // this is call before all the variants are created since they are all going to depend
    // on the global LINT_JAR task output
    public void configureCustomLintChecks() {
        // setup the task that reads the config and put the lint jar in the intermediate folder
        // so that the bundle tasks can copy it, and the inter-project publishing can publish it
        File lintJar = FileUtils.join(globalScope.getIntermediatesDir(), "lint", FN_LINT_JAR);

        PrepareLintJar copyLintTask =
                taskFactory.create(new PrepareLintJar.ConfigAction(globalScope, lintJar));

        // publish the lint intermediate file to the global tasks
        globalScope.addTaskOutput(LINT_JAR, lintJar, copyLintTask.getName());
    }

    public void createGlobalLintTask() {
        taskFactory.create(LINT, LintGlobalTask.class, task -> {});
        taskFactory.configure(JavaBasePlugin.CHECK_TASK_NAME, it -> it.dependsOn(LINT));
    }

    // this is run after all the variants are created.
    public void configureGlobalLintTask(@NonNull final Collection<VariantScope> variants) {
        // we only care about non testing and non feature variants
        List<VariantScope> filteredVariants =
                variants.stream().filter(TaskManager::isLintVariant).collect(Collectors.toList());

        if (filteredVariants.isEmpty()) {
            return;
        }

        // configure the global lint task.
        new LintGlobalTask.GlobalConfigAction(globalScope, filteredVariants)
                .execute((LintGlobalTask) taskFactory.findByName(LINT));

        // publish the local lint.jar to all the variants. This is not for the task output itself
        // but for the artifact publishing.
        FileCollection lintJarCollection = globalScope.getOutput(LINT_JAR);

        // FIXME we don't to do this during config, but there's no way right now as we want to publish it.
        // A work around would be to keep hold of the file in the global scope as well as the
        // file collection in TaskOutput but then this is dangerous as one could use it without
        // the task dependency.
        File lintJar = lintJarCollection.getSingleFile();
        for (VariantScope scope : variants) {
            scope.addTaskOutput(LINT_JAR, lintJar, PrepareLintJar.NAME);
        }
    }

    // This is for config attribute debugging
    public static class ConfigAttrTask extends DefaultTask {
        boolean consumable = false;
        boolean resolvable = false;
        @TaskAction
        public void run() {
            for (Configuration config : getProject().getConfigurations()) {
                AttributeContainer attributes = config.getAttributes();
                if ((consumable && config.isCanBeConsumed())
                        || (resolvable && config.isCanBeResolved())) {
                    System.out.println(config.getName());
                    System.out.println("\tcanBeResolved: " + config.isCanBeResolved());
                    System.out.println("\tcanBeConsumed: " + config.isCanBeConsumed());
                    for (Attribute<?> attr : attributes.keySet()) {
                        System.out.println(
                                "\t" + attr.getName() + ": " + attributes.getAttribute(attr));
                    }
                    if (consumable && config.isCanBeConsumed()) {
                        for (PublishArtifact artifact : config.getArtifacts()) {
                            System.out.println("\tArtifact: " + artifact.getName() + " (" + artifact.getFile().getName() + ")");
                        }
                        for (ConfigurationVariant cv : config.getOutgoing().getVariants()) {
                            System.out.println("\tConfigurationVariant: " + cv.getName());
                            for (PublishArtifact pa : cv.getArtifacts()) {
                                System.out.println("\t\tArtifact: " + pa.getFile());
                                System.out.println("\t\tType:" + pa.getType());
                            }
                        }
                    }
                }
            }
        }
    }

    public void createMockableJarTask() {
        File mockableJar = globalScope.getMockableAndroidJarFile();
        createMockableJar =
                taskFactory.create(
                        new MockableAndroidJarTask.ConfigAction(globalScope, mockableJar));

        globalScope.addTaskOutput(MOCKABLE_JAR, mockableJar, createMockableJar.getName());
    }

    public void createAttrFromAndroidJarTask() {
        File platformRtxt = FileUtils.join(globalScope.getIntermediatesDir(), "attr", "R.txt");

        PlatformAttrExtractorTask task =
                taskFactory.create(
                        new PlatformAttrExtractorTask.ConfigAction(globalScope, platformRtxt));

        globalScope.addTaskOutput(PLATFORM_R_TXT, platformRtxt, task.getName());
    }

    protected void createDependencyStreams(@NonNull final VariantScope variantScope) {
        // Since it's going to chance the configurations, we need to do it before
        // we start doing queries to fill the streams.
        handleJacocoDependencies(variantScope);

        TransformManager transformManager = variantScope.getTransformManager();

        transformManager.addStream(
                OriginalStream.builder(project, "ext-libs-classes")
                        .addContentTypes(TransformManager.CONTENT_CLASS)
                        .addScope(Scope.EXTERNAL_LIBRARIES)
                        .setArtifactCollection(
                                variantScope.getArtifactCollection(
                                        RUNTIME_CLASSPATH, EXTERNAL, CLASSES))
                        .build());

        transformManager.addStream(
                OriginalStream.builder(project, "ext-libs-res-plus-native")
                        .addContentTypes(
                                DefaultContentType.RESOURCES, ExtendedContentType.NATIVE_LIBS)
                        .addScope(Scope.EXTERNAL_LIBRARIES)
                        .setArtifactCollection(
                                variantScope.getArtifactCollection(
                                        RUNTIME_CLASSPATH, EXTERNAL, JAVA_RES))
                        .build());

        // and the android AAR also have a specific jni folder
        transformManager.addStream(
                OriginalStream.builder(project, "ext-libs-native")
                        .addContentTypes(TransformManager.CONTENT_NATIVE_LIBS)
                        .addScope(Scope.EXTERNAL_LIBRARIES)
                        .setArtifactCollection(
                                variantScope.getArtifactCollection(
                                        RUNTIME_CLASSPATH, EXTERNAL, JNI))
                        .build());

        // data binding related artifacts for external libs
        if (extension.getDataBinding().isEnabled()) {
            transformManager.addStream(
                    OriginalStream.builder(project, "sub-project-data-binding")
                            .addContentTypes(TransformManager.DATA_BINDING_ARTIFACT)
                            .addScope(Scope.SUB_PROJECTS)
                            .setArtifactCollection(
                                    variantScope.getArtifactCollection(
                                            COMPILE_CLASSPATH, MODULE, DATA_BINDING_ARTIFACT))
                            .build());
            transformManager.addStream(
                    OriginalStream.builder(project, "ext-libs-data-binding")
                            .addContentTypes(TransformManager.DATA_BINDING_ARTIFACT)
                            .addScope(Scope.EXTERNAL_LIBRARIES)
                            .setArtifactCollection(
                                    variantScope.getArtifactCollection(
                                            COMPILE_CLASSPATH, EXTERNAL, DATA_BINDING_ARTIFACT))
                            .build());
            transformManager.addStream(
                    OriginalStream.builder(project, "sub-project-data-binding-base-classes")
                            .addContentTypes(TransformManager.DATA_BINDING_BASE_CLASS_LOG_ARTIFACT)
                            .addScope(Scope.SUB_PROJECTS)
                            .setArtifactCollection(
                                    variantScope.getArtifactCollection(
                                            COMPILE_CLASSPATH,
                                            MODULE,
                                            DATA_BINDING_BASE_CLASS_LOG_ARTIFACT))
                            .build());
            transformManager.addStream(
                    OriginalStream.builder(project, "ext-libs-data-binding-base-classes")
                            .addContentTypes(TransformManager.DATA_BINDING_BASE_CLASS_LOG_ARTIFACT)
                            .addScope(Scope.EXTERNAL_LIBRARIES)
                            .setArtifactCollection(
                                    variantScope.getArtifactCollection(
                                            COMPILE_CLASSPATH,
                                            EXTERNAL,
                                            DATA_BINDING_BASE_CLASS_LOG_ARTIFACT))
                            .build());
        }

        // for the sub modules, new intermediary classes artifact has its own stream
        transformManager.addStream(
                OriginalStream.builder(project, "sub-projects-classes")
                        .addContentTypes(TransformManager.CONTENT_CLASS)
                        .addScope(Scope.SUB_PROJECTS)
                        .setArtifactCollection(
                                variantScope.getArtifactCollection(
                                        RUNTIME_CLASSPATH, MODULE, CLASSES))
                        .build());

        // same for the resources which can be java-res or jni
        transformManager.addStream(
                OriginalStream.builder(project, "sub-projects-res-plus-native")
                        .addContentTypes(
                                DefaultContentType.RESOURCES, ExtendedContentType.NATIVE_LIBS)
                        .addScope(Scope.SUB_PROJECTS)
                        .setArtifactCollection(
                                variantScope.getArtifactCollection(
                                        RUNTIME_CLASSPATH, MODULE, JAVA_RES))
                        .build());

        // and the android library sub-modules also have a specific jni folder
        transformManager.addStream(
                OriginalStream.builder(project, "sub-projects-native")
                        .addContentTypes(TransformManager.CONTENT_NATIVE_LIBS)
                        .addScope(Scope.SUB_PROJECTS)
                        .setArtifactCollection(
                                variantScope.getArtifactCollection(RUNTIME_CLASSPATH, MODULE, JNI))
                        .build());

        // provided only scopes.
        transformManager.addStream(
                OriginalStream.builder(project, "provided-classes")
                        .addContentTypes(TransformManager.CONTENT_CLASS)
                        .addScope(Scope.PROVIDED_ONLY)
                        .setFileCollection(variantScope.getProvidedOnlyClasspath())
                        .build());

        if (variantScope.getTestedVariantData() != null) {
            final BaseVariantData testedVariantData = variantScope.getTestedVariantData();

            VariantScope testedVariantScope = testedVariantData.getScope();

            PublishingSpecs.VariantSpec testedSpec =
                    testedVariantScope
                            .getPublishingSpec()
                            .getTestingSpec(variantScope.getVariantConfiguration().getType());

            // get the OutputPublishingSpec from the ArtifactType for this particular variant spec
            PublishingSpecs.OutputSpec taskOutputSpec =
                    testedSpec.getSpec(AndroidArtifacts.ArtifactType.CLASSES);
            // now get the output type
            com.android.build.api.artifact.ArtifactType testedOutputType =
                    taskOutputSpec.getOutputType();

            // create two streams of different types.
            transformManager.addStream(
                    OriginalStream.builder(project, "tested-code-classes")
                            .addContentTypes(DefaultContentType.CLASSES)
                            .addScope(Scope.TESTED_CODE)
                            .setFileCollection(testedVariantScope.getOutput(testedOutputType))
                            .build());

            transformManager.addStream(
                    OriginalStream.builder(project, "tested-code-deps")
                            .addContentTypes(DefaultContentType.CLASSES)
                            .addScope(Scope.TESTED_CODE)
                            .setArtifactCollection(
                                    testedVariantScope.getArtifactCollection(
                                            RUNTIME_CLASSPATH, ALL, CLASSES))
                            .build());
        }
    }

    public void createBuildArtifactReportTask(@NonNull VariantScope scope) {
        taskFactory.create(new BuildArtifactReportTask.BuildArtifactReportConfigAction(scope));
    }

    public void createSourceSetArtifactReportTask(@NonNull GlobalScope scope) {
        for (AndroidSourceSet sourceSet : scope.getExtension().getSourceSets()) {
            if (sourceSet instanceof DefaultAndroidSourceSet) {
                taskFactory.create(
                        new BuildArtifactReportTask.SourceSetReportConfigAction(
                                scope, (DefaultAndroidSourceSet) sourceSet));
            }
        }
    }

    public void createMergeApkManifestsTask(@NonNull VariantScope variantScope) {
        AndroidArtifactVariantData androidArtifactVariantData =
                (AndroidArtifactVariantData) variantScope.getVariantData();
        Set<String> screenSizes = androidArtifactVariantData.getCompatibleScreens();

        taskFactory.create(new CompatibleScreensManifest.ConfigAction(variantScope, screenSizes));

        ManifestProcessorTask processManifestTask = createMergeManifestTask(variantScope);

        processManifestTask.dependsOn(variantScope.getCheckManifestTask());

        if (variantScope.getMicroApkTask() != null) {
            processManifestTask.dependsOn(variantScope.getMicroApkTask());
        }
    }

    @NonNull
    private static List<String> getAdvancedProfilingTransforms(@NonNull ProjectOptions options) {
        String string = options.get(StringOption.IDE_ANDROID_CUSTOM_CLASS_TRANSFORMS);
        if (string == null) {
            return ImmutableList.of();
        }
        return Splitter.on(',').splitToList(string);
    }

    @NonNull
    private static File computeManifestReportFile(@NonNull VariantScope variantScope) {
        return FileUtils.join(
                variantScope.getGlobalScope().getOutputsDir(),
                "logs",
                "manifest-merger-"
                        + variantScope.getVariantConfiguration().getBaseName()
                        + "-report.txt");
    }

    /** Creates the merge manifests task. */
    @NonNull
    protected ManifestProcessorTask createMergeManifestTask(@NonNull VariantScope variantScope) {

        final File reportFile = computeManifestReportFile(variantScope);
        return taskFactory.create(
                new MergeManifests.ConfigAction(
                        variantScope,
                        !getAdvancedProfilingTransforms(projectOptions).isEmpty(),
                        reportFile));
    }

    public ProcessManifest createMergeLibManifestsTask(@NonNull VariantScope scope) {

        // for library, there is only one manifest (no split).
        File libraryProcessedManifest =
                new File(scope.getManifestOutputDirectory(), FN_ANDROID_MANIFEST_XML);

        final File reportFile = computeManifestReportFile(scope);
        ProcessManifest processManifest =
                taskFactory.create(
                        new ProcessManifest.ConfigAction(
                                scope, libraryProcessedManifest, reportFile));

        final String taskName = processManifest.getName();

        scope.addTaskOutput(MERGED_MANIFESTS, scope.getManifestOutputDirectory(), taskName);

        scope.addTaskOutput(
                AAPT_FRIENDLY_MERGED_MANIFESTS,
                scope.getAaptFriendlyManifestOutputDirectory(),
                taskName);

        scope.addTaskOutput(LIBRARY_MANIFEST, libraryProcessedManifest, taskName);
        scope.addTaskOutput(MANIFEST_MERGE_REPORT, reportFile, taskName);

        processManifest.dependsOn(scope.getCheckManifestTask());

        scope.getVariantData().addTask(TaskContainer.TaskKind.PROCESS_MANIFEST, processManifest);

        return processManifest;
    }

    protected void createProcessTestManifestTask(
            @NonNull VariantScope scope,
            @NonNull VariantScope testedScope) {

        ProcessTestManifest processTestManifestTask =
                taskFactory.create(
                        new ProcessTestManifest.ConfigAction(
                                scope, testedScope.getOutput(MERGED_MANIFESTS)));

        if (scope.getCheckManifestTask() != null) {
            processTestManifestTask.dependsOn(scope.getCheckManifestTask());
        }

        scope.getVariantData()
                .addTask(TaskContainer.TaskKind.PROCESS_MANIFEST, processTestManifestTask);
    }

    public void createRenderscriptTask(
            @NonNull VariantScope scope) {
        scope.setRenderscriptCompileTask(
                taskFactory.create(new RenderscriptCompile.ConfigAction(scope)));

        GradleVariantConfiguration config = scope.getVariantConfiguration();

        if (config.getType().isForTesting()) {
            scope.getRenderscriptCompileTask()
                    .dependsOn(
                            scope.getVariantData()
                                    .getTaskByKind(TaskContainer.TaskKind.PROCESS_MANIFEST));
        } else {
            scope.getRenderscriptCompileTask().dependsOn(scope.getPreBuildTask());
        }

        scope.getResourceGenTask().dependsOn(scope.getRenderscriptCompileTask());
        // only put this dependency if rs will generate Java code
        if (!config.getRenderscriptNdkModeEnabled()) {
            scope.getSourceGenTask().dependsOn(scope.getRenderscriptCompileTask());
        }

    }

    public MergeResources createMergeResourcesTask(
            @NonNull VariantScope scope, boolean processResources) {

        boolean alsoOutputNotCompiledResources =
                scope.useResourceShrinker()
                        || (scope.getTestedVariantData() == null
                                && globalScope
                                        .getExtension()
                                        .getTestOptions()
                                        .getUnitTests()
                                        .isIncludeAndroidResources());

        return basicCreateMergeResourcesTask(
                scope,
                MergeType.MERGE,
                null /*outputLocation*/,
                true /*includeDependencies*/,
                processResources,
                alsoOutputNotCompiledResources,
                ImmutableSet.of());
    }

    /** Defines the merge type for {@link #basicCreateMergeResourcesTask} */
    public enum MergeType {
        /**
         * Merge all resources with all the dependencies resources.
         */
        MERGE {
            @Override
            public InternalArtifactType getOutputType() {
                return InternalArtifactType.MERGED_RES;
            }
        },
        /**
         * Merge all resources without the dependencies resources for an aar.
         */
        PACKAGE {
            @Override
            public InternalArtifactType getOutputType() {
                return InternalArtifactType.PACKAGED_RES;
            }
        };

        public abstract InternalArtifactType getOutputType();
    }

    public MergeResources basicCreateMergeResourcesTask(
            @NonNull VariantScope scope,
            @NonNull MergeType mergeType,
            @Nullable File outputLocation,
            final boolean includeDependencies,
            final boolean processResources,
            boolean alsoOutputNotCompiledResources,
            @NonNull ImmutableSet<MergeResources.Flag> flags) {

        File mergedOutputDir = MoreObjects
                .firstNonNull(outputLocation, scope.getDefaultMergeResourcesOutputDir());

        String taskNamePrefix = mergeType.name().toLowerCase(Locale.ENGLISH);

        File mergedNotCompiledDir =
                alsoOutputNotCompiledResources
                        ? new File(
                                globalScope.getIntermediatesDir()
                                        + "/merged-not-compiled-resources/"
                                        + scope.getVariantConfiguration().getDirName())
                        : null;

        MergeResources mergeResourcesTask =
                taskFactory.create(
                        new MergeResources.ConfigAction(
                                scope,
                                taskNamePrefix,
                                mergedOutputDir,
                                mergedNotCompiledDir,
                                includeDependencies,
                                processResources,
                                true,
                                flags));

        scope.addTaskOutput(
                mergeType.getOutputType(), mergedOutputDir, mergeResourcesTask.getName());

        if (alsoOutputNotCompiledResources) {
            scope.addTaskOutput(
                    MERGED_NOT_COMPILED_RES, mergedNotCompiledDir, mergeResourcesTask.getName());
        }

        mergeResourcesTask.dependsOn(

                scope.getResourceGenTask());

        if (extension.getTestOptions().getUnitTests().isIncludeAndroidResources()) {
            scope.getCompileTask().dependsOn(mergeResourcesTask);
        }

        scope.setResourceOutputDir(mergedOutputDir);
        return mergeResourcesTask;
    }

    public MergeSourceSetFolders createMergeAssetsTask(@NonNull VariantScope scope) {
        final GradleVariantConfiguration variantConfiguration = scope.getVariantConfiguration();
        File outputDir =
                FileUtils.join(
                        globalScope.getIntermediatesDir(),
                        FD_ASSETS,
                        variantConfiguration.getDirName());

        MergeSourceSetFolders mergeAssetsTask =
                taskFactory.create(
                        new MergeSourceSetFolders.MergeAppAssetConfigAction(scope, outputDir));

        // register the output
        scope.addTaskOutput(MERGED_ASSETS, outputDir, mergeAssetsTask.getName());

        mergeAssetsTask.dependsOn(scope.getAssetGenTask());
        scope.setMergeAssetsTask(mergeAssetsTask);

        return mergeAssetsTask;
    }

    @NonNull
    public Optional<TransformTask> createMergeJniLibFoldersTasks(
            @NonNull final VariantScope variantScope) {
        // merge the source folders together using the proper priority.
        MergeSourceSetFolders mergeJniLibFoldersTask =
                taskFactory.create(
                        new MergeSourceSetFolders.MergeJniLibFoldersConfigAction(variantScope));
        mergeJniLibFoldersTask.dependsOn(variantScope.getAssetGenTask());

        // create the stream generated from this task
        variantScope
                .getTransformManager()
                .addStream(
                        OriginalStream.builder(project, "mergedJniFolder")
                                .addContentType(ExtendedContentType.NATIVE_LIBS)
                                .addScope(Scope.PROJECT)
                                .setFolder(variantScope.getMergeNativeLibsOutputDir())
                                .setDependency(mergeJniLibFoldersTask.getName())
                                .build());

        // create a stream that contains the content of the local NDK build
        variantScope
                .getTransformManager()
                .addStream(
                        OriginalStream.builder(project, "local-ndk-build")
                                .addContentType(ExtendedContentType.NATIVE_LIBS)
                                .addScope(Scope.PROJECT)
                                .setFolders(variantScope::getNdkSoFolder)
                                .setDependency(getNdkBuildable(variantScope.getVariantData()))
                                .build());

        // create a stream that contains the content of the local external native build
        if (variantScope.getExternalNativeJsonGenerator() != null) {
            variantScope
                    .getTransformManager()
                    .addStream(
                            OriginalStream.builder(project, "external-native-build")
                                    .addContentType(ExtendedContentType.NATIVE_LIBS)
                                    .addScope(Scope.PROJECT)
                                    .setFolder(
                                            variantScope
                                                    .getExternalNativeJsonGenerator()
                                                    .getObjFolder())
                                    .setDependency(
                                            checkNotNull(variantScope.getExternalNativeBuildTask())
                                                    .getName())
                                    .build());
        }

        // create a stream containing the content of the renderscript compilation output
        // if support mode is enabled.
        if (variantScope.getVariantConfiguration().getRenderscriptSupportModeEnabled()) {
            final Supplier<Collection<File>> supplier =
                    () -> {
                        ImmutableList.Builder<File> builder = ImmutableList.builder();

                        if (variantScope.getRenderscriptLibOutputDir().isDirectory()) {
                            builder.add(variantScope.getRenderscriptLibOutputDir());
                        }

                        File rsLibs =
                                variantScope
                                        .getGlobalScope()
                                        .getAndroidBuilder()
                                        .getSupportNativeLibFolder();
                        if (rsLibs != null && rsLibs.isDirectory()) {
                            builder.add(rsLibs);
                        }
                        if (variantScope
                                .getVariantConfiguration()
                                .getRenderscriptSupportModeBlasEnabled()) {
                            File rsBlasLib =
                                    variantScope
                                            .getGlobalScope()
                                            .getAndroidBuilder()
                                            .getSupportBlasLibFolder();

                            if (rsBlasLib == null || !rsBlasLib.isDirectory()) {
                                throw new GradleException(
                                        "Renderscript BLAS support mode is not supported "
                                                + "in BuildTools"
                                                + rsBlasLib);
                            } else {
                                builder.add(rsBlasLib);
                            }
                        }
                        return builder.build();
                    };

            variantScope
                    .getTransformManager()
                    .addStream(
                            OriginalStream.builder(project, "rs-support-mode-output")
                                    .addContentType(ExtendedContentType.NATIVE_LIBS)
                                    .addScope(Scope.PROJECT)
                                    .setFolders(supplier)
                                    .setDependency(
                                            variantScope.getRenderscriptCompileTask().getName())
                                    .build());
        }

        // compute the scopes that need to be merged.
        Set<? super Scope> mergeScopes = getResMergingScopes(variantScope);
        // Create the merge transform
        MergeJavaResourcesTransform mergeTransform = new MergeJavaResourcesTransform(
                variantScope.getGlobalScope().getExtension().getPackagingOptions(),
                mergeScopes, ExtendedContentType.NATIVE_LIBS, "mergeJniLibs", variantScope);

        return variantScope
                .getTransformManager()
                .addTransform(taskFactory, variantScope, mergeTransform);
    }

    public void createBuildConfigTask(@NonNull VariantScope scope) {
        GenerateBuildConfig generateBuildConfigTask =
                taskFactory.create(new GenerateBuildConfig.ConfigAction(scope));
        scope.setGenerateBuildConfigTask(generateBuildConfigTask);
        scope.getSourceGenTask().dependsOn(generateBuildConfigTask.getName());
        if (scope.getVariantConfiguration().getType().isForTesting()) {
            // in case of a test project, the manifest is generated so we need to depend
            // on its creation.

            generateBuildConfigTask.dependsOn(
                    scope.getVariantData().getTaskByKind(TaskContainer.TaskKind.PROCESS_MANIFEST));
        } else {
            generateBuildConfigTask.dependsOn(scope.getCheckManifestTask());
        }
    }

    public void createGenerateResValuesTask(
            @NonNull VariantScope scope) {
        GenerateResValues generateResValuesTask =
                taskFactory.create(new GenerateResValues.ConfigAction(scope));
        scope.getResourceGenTask().dependsOn(generateResValuesTask);
    }

    public void createApkProcessResTask(

            @NonNull VariantScope scope) {

        createProcessResTask(

                scope,
                        new File(
                                globalScope.getIntermediatesDir(),
                                "symbols/"
                                        + scope.getVariantData()
                                                .getVariantConfiguration()
                                                .getDirName()),
                scope.getProcessResourcePackageOutputDirectory(),
                null,
                MergeType.MERGE,
                scope.getGlobalScope().getProjectBaseName());
    }

    protected boolean isLibrary() {
        return false;
    }

    public void createProcessResTask(
            @NonNull VariantScope scope,
            @NonNull File symbolLocation,
            @NonNull File resPackageOutputFolder,
            @Nullable InternalArtifactType packageOutputType,
            @NonNull MergeType mergeType,
            @NonNull String baseName) {
        BaseVariantData variantData = scope.getVariantData();

        variantData.calculateFilters(scope.getGlobalScope().getExtension().getSplits());

        createSplitsDiscovery(scope);

        boolean useAaptToGenerateLegacyMultidexMainDexProguardRules =
                scope.getDexingType() == DexingType.LEGACY_MULTIDEX;

        if (Boolean.TRUE.equals(
                scope.getGlobalScope().getExtension().getAaptOptions().getNamespaced())) {
            new NamespacedResourcesTaskManager(globalScope, taskFactory, scope)
                    .createNamespacedResourceTasks(
                            resPackageOutputFolder,
                            packageOutputType,
                            baseName,
                            useAaptToGenerateLegacyMultidexMainDexProguardRules);
            return;
        }
        createNonNamespacedResourceTasks(
                scope,
                symbolLocation,
                resPackageOutputFolder,
                packageOutputType,
                mergeType,
                baseName,
                useAaptToGenerateLegacyMultidexMainDexProguardRules);
    }

    private void createNonNamespacedResourceTasks(
            @NonNull VariantScope scope,
            @NonNull File symbolDirectory,
            @NonNull File resPackageOutputFolder,
            InternalArtifactType packageOutputType,
            @NonNull MergeType mergeType,
            @NonNull String baseName,
            boolean useAaptToGenerateLegacyMultidexMainDexProguardRules) {
        File symbolTableWithPackageName =
                FileUtils.join(
                        globalScope.getIntermediatesDir(),
                        FD_RES,
                        "symbol-table-with-package",
                        scope.getVariantConfiguration().getDirName(),
                        "package-aware-r.txt");
        final String taskName;

        File symbolFile = new File(symbolDirectory, FN_RESOURCE_TEXT);

        if (mergeType == MergeType.PACKAGE) {
            // Simply generate the R class for a library
            GenerateLibraryRFileTask task =
                    taskFactory.create(
                            new GenerateLibraryRFileTask.ConfigAction(
                                    scope, symbolFile, symbolTableWithPackageName));
            taskName = task.getName();
            scope.setProcessResourcesTask(task);

        } else {
            LinkApplicationAndroidResourcesTask processAndroidResources =
                    taskFactory.create(
                            createProcessAndroidResourcesConfigAction(
                                    scope,
                                    () -> symbolDirectory,
                                    symbolTableWithPackageName,
                                    resPackageOutputFolder,
                                    useAaptToGenerateLegacyMultidexMainDexProguardRules,
                                    mergeType,
                                    baseName));

            taskName = processAndroidResources.getName();
            scope.addTaskOutput(
                    InternalArtifactType.PROCESSED_RES, resPackageOutputFolder, taskName);
            if (packageOutputType != null) {
                scope.addTaskOutput(
                        packageOutputType,
                        resPackageOutputFolder,
                        processAndroidResources.getName());
            }

            scope.setProcessResourcesTask(processAndroidResources);

            // create the task that creates the aapt output for the bundle.
            File resourcesAsProtosFile =
                    FileUtils.join(
                            globalScope.getIntermediatesDir(),
                            "res-bundle",
                            scope.getVariantConfiguration().getDirName(),
                            "bundled-res.ap_");

            LinkAndroidResForBundleTask linkResForBundle =
                    taskFactory.create(
                            new LinkAndroidResForBundleTask.ConfigAction(
                                    scope, resourcesAsProtosFile));

            // publish it.
            scope.addTaskOutput(
                    LINKED_RES_FOR_BUNDLE, resourcesAsProtosFile, linkResForBundle.getName());
        }
        scope.addTaskOutput(InternalArtifactType.SYMBOL_LIST, symbolFile, taskName);

        // Needed for the IDE
        scope.getSourceGenTask().dependsOn(taskName);

        // Synthetic output for AARs (see SymbolTableWithPackageNameTransform), and created in
        // process resources for local subprojects.
        scope.addTaskOutput(
                InternalArtifactType.SYMBOL_LIST_WITH_PACKAGE_NAME,
                symbolTableWithPackageName,
                taskName);

        // Needed for the IDE
        Task t = project.getTasks().findByName(taskName);
        if (t != null) {
            scope.getSourceGenTask().dependsOn(taskName);
        }
    }

    protected TaskConfigAction<LinkApplicationAndroidResourcesTask>
            createProcessAndroidResourcesConfigAction(
                    @NonNull VariantScope scope,
                    @NonNull Supplier<File> symbolLocation,
                    @NonNull File symbolWithPackageName,
                    @NonNull File resPackageOutputFolder,
                    boolean useAaptToGenerateLegacyMultidexMainDexProguardRules,
                    @NonNull MergeType sourceArtifactType,
                    @NonNull String baseName) {
        checkState(
                globalScope.getExtension().getAaptOptions().getPrivateRDotJavaPackage() == null,
                "Private R.java generation is currently only allowed with resource namespacing"
                        + " enabled.");

        return new LinkApplicationAndroidResourcesTask.ConfigAction(
                scope,
                symbolLocation,
                symbolWithPackageName,
                resPackageOutputFolder,
                useAaptToGenerateLegacyMultidexMainDexProguardRules,
                sourceArtifactType,
                baseName,
                isLibrary());
    }

    /**
     * Creates the split resources packages task if necessary. AAPT will produce split packages for
     * all --split provided parameters. These split packages should be signed and moved unchanged to
     * the FULL_APK build output directory.
     */
    @NonNull
    public PackageSplitRes createSplitResourcesTasks(@NonNull VariantScope scope) {
        BaseVariantData variantData = scope.getVariantData();

        checkState(
                variantData.getMultiOutputPolicy().equals(MultiOutputPolicy.SPLITS),
                "Can only create split resources tasks for pure splits.");

        File densityOrLanguagesPackages = scope.getSplitDensityOrLanguagesPackagesOutputDirectory();
        PackageSplitRes packageSplitRes =
                taskFactory.create(
                        new PackageSplitRes.ConfigAction(scope, densityOrLanguagesPackages));
        variantData.packageSplitResourcesTask = packageSplitRes;
        scope.addTaskOutput(
                InternalArtifactType.DENSITY_OR_LANGUAGE_PACKAGED_SPLIT,
                densityOrLanguagesPackages,
                packageSplitRes.getName());

        if (scope.getVariantConfiguration().getSigningConfig() != null) {
            packageSplitRes.dependsOn(getValidateSigningTask(scope));
        }

        return packageSplitRes;
    }

    @Nullable
    public PackageSplitAbi createSplitAbiTasks(@NonNull VariantScope scope) {
        BaseVariantData variantData = scope.getVariantData();

        checkState(
                variantData.getMultiOutputPolicy().equals(MultiOutputPolicy.SPLITS),
                "split ABI tasks are only compatible with pure splits.");

        Set<String> filters = AbiSplitOptions.getAbiFilters(extension.getSplits().getAbiFilters());
        if (filters.isEmpty()) {
            return null;
        }

        List<ApkData> fullApkDatas =
                variantData.getOutputScope().getSplitsByType(OutputFile.OutputType.FULL_SPLIT);
        if (!fullApkDatas.isEmpty()) {
            throw new RuntimeException(
                    "In release 21 and later, there cannot be full splits and pure splits, "
                            + "found "
                            + Joiner.on(",").join(fullApkDatas)
                            + " and abi filters "
                            + Joiner.on(",").join(filters));
        }

        File generateSplitAbiResOutputDirectory = scope.getGenerateSplitAbiResOutputDirectory();
        // first create the ABI specific split FULL_APK resources.
        GenerateSplitAbiRes generateSplitAbiRes =
                taskFactory.create(
                        new GenerateSplitAbiRes.ConfigAction(
                                scope, generateSplitAbiResOutputDirectory));
        scope.addTaskOutput(
                InternalArtifactType.ABI_PROCESSED_SPLIT_RES,
                generateSplitAbiResOutputDirectory,
                generateSplitAbiRes.getName());

        // then package those resources with the appropriate JNI libraries.
        File generateSplitAbiPackagesOutputDirectory = scope.getSplitAbiPackagesOutputDirectory();
        PackageSplitAbi packageSplitAbiTask =
                taskFactory.create(
                        new PackageSplitAbi.ConfigAction(
                                scope,
                                generateSplitAbiPackagesOutputDirectory,
                                scope.getOutput(InternalArtifactType.ABI_PROCESSED_SPLIT_RES)));
        scope.addTaskOutput(
                InternalArtifactType.ABI_PACKAGED_SPLIT,
                generateSplitAbiPackagesOutputDirectory,
                packageSplitAbiTask.getName());
        variantData.packageSplitAbiTask = packageSplitAbiTask;

        packageSplitAbiTask.dependsOn(scope.getNdkBuildable());

        if (variantData.getVariantConfiguration().getSigningConfig() != null) {
            packageSplitAbiTask.dependsOn(getValidateSigningTask(variantData.getScope()));
        }

        if (scope.getExternalNativeBuildTask() != null) {
            packageSplitAbiTask.dependsOn(scope.getExternalNativeBuildTask());
        }

        return packageSplitAbiTask;
    }

    public void createSplitTasks(@NonNull VariantScope variantScope) {
        createSplitResourcesTasks(variantScope);
        createSplitAbiTasks(variantScope);
    }

    /**
     * Returns the scopes for which the java resources should be merged.
     *
     * @param variantScope the scope of the variant being processed.
     * @return the list of scopes for which to merge the java resources.
     */
    @NonNull
    protected abstract Set<? super Scope> getResMergingScopes(@NonNull VariantScope variantScope);

    /**
     * Creates the java resources processing tasks.
     *
     * <p>The java processing will happen in two steps:
     *
     * <ul>
     *   <li>{@link Sync} task configured with {@link ProcessJavaResConfigAction} will sync all
     *       source folders into a single folder identified by {@link
     *       VariantScope#getSourceFoldersJavaResDestinationDir()}
     *   <li>{@link MergeJavaResourcesTransform} will take the output of this merge plus the
     *       dependencies and will create a single merge with the {@link PackagingOptions} settings
     *       applied.
     * </ul>
     *
     * This sets up only the Sync part. The transform is setup via {@link
     * #createMergeJavaResTransform(VariantScope)}
     *
     * @param variantScope the variant scope we are operating under.
     */
    public void createProcessJavaResTask(@NonNull VariantScope variantScope) {
        // Copy the source folders java resources into the temporary location, mainly to
        // maintain the PluginDsl COPY semantics.

        // TODO: move this file computation completely out of VariantScope.
        File destinationDir = variantScope.getSourceFoldersJavaResDestinationDir();

        Sync processJavaResourcesTask =
                taskFactory.create(new ProcessJavaResConfigAction(variantScope, destinationDir));
        variantScope.setProcessJavaResourcesTask(processJavaResourcesTask);
        variantScope.getVariantData().processJavaResourcesTask = processJavaResourcesTask;

        processJavaResourcesTask.dependsOn(variantScope.getPreBuildTask());

        // create the task outputs for others to consume
        FileCollection collection =
                variantScope.addTaskOutput(
                        InternalArtifactType.JAVA_RES,
                        destinationDir,
                        processJavaResourcesTask.getName());

        // create the stream generated from this task
        variantScope
                .getTransformManager()
                .addStream(
                        OriginalStream.builder(project, "processed-java-res")
                                .addContentType(DefaultContentType.RESOURCES)
                                .addScope(Scope.PROJECT)
                                .setFileCollection(collection)
                                .build());
    }

    /**
     * Sets up the Merge Java Res transform.
     *
     * @param variantScope the variant scope we are operating under.
     * @see #createProcessJavaResTask(VariantScope)
     */
    public void createMergeJavaResTransform(@NonNull VariantScope variantScope) {
        TransformManager transformManager = variantScope.getTransformManager();

        // Compute the scopes that need to be merged.
        Set<? super Scope> mergeScopes = getResMergingScopes(variantScope);

        // Create the merge transform.
        MergeJavaResourcesTransform mergeTransform =
                new MergeJavaResourcesTransform(
                        variantScope.getGlobalScope().getExtension().getPackagingOptions(),
                        mergeScopes,
                        DefaultContentType.RESOURCES,
                        "mergeJavaRes",
                        variantScope);
        variantScope.setMergeJavaResourcesTask(
                transformManager
                        .addTransform(taskFactory, variantScope, mergeTransform)
                        .orElse(null));
    }

    public AidlCompile createAidlTask(@NonNull VariantScope scope) {
        AidlCompile aidlCompileTask = taskFactory.create(new AidlCompile.ConfigAction(scope));
        scope.setAidlCompileTask(aidlCompileTask);
        scope.getSourceGenTask().dependsOn(aidlCompileTask);
        aidlCompileTask.dependsOn(scope.getPreBuildTask());

        return aidlCompileTask;
    }

    public void createShaderTask(@NonNull VariantScope scope) {
        // merge the shader folders together using the proper priority.
        MergeSourceSetFolders mergeShadersTask =
                taskFactory.create(
                        new MergeSourceSetFolders.MergeShaderSourceFoldersConfigAction(scope));
        // TODO do we support non compiled shaders in aars?
        //mergeShadersTask.dependsOn( scope.getVariantData().prepareDependenciesTask);

        File outputDir = scope.getGeneratedAssetsDir("shaders");

        // compile the shaders
        ShaderCompile shaderCompileTask =
                taskFactory.create(new ShaderCompile.ConfigAction(scope, outputDir));
        scope.addTaskOutput(
                InternalArtifactType.SHADER_ASSETS, outputDir, shaderCompileTask.getName());
        shaderCompileTask.dependsOn(mergeShadersTask);

        scope.getAssetGenTask().dependsOn(shaderCompileTask);
    }

    protected abstract void postJavacCreation(@NonNull final VariantScope scope);

    /**
     * Creates the task for creating *.class files using javac. These tasks are created regardless
     * of whether Jack is used or not, but assemble will not depend on them if it is. They are
     * always used when running unit tests.
     */
    public JavaCompile createJavacTask(@NonNull final VariantScope scope) {
        File processorListFile =
                FileUtils.join(
                        globalScope.getIntermediatesDir(),
                        "javaPrecompile",
                        scope.getDirName(),
                        "annotationProcessors.json");

        JavaPreCompileTask preCompileTask =
                taskFactory.create(new JavaPreCompileTask.ConfigAction(scope, processorListFile));
        preCompileTask.dependsOn(scope.getPreBuildTask());
        scope.addTaskOutput(ANNOTATION_PROCESSOR_LIST, processorListFile, preCompileTask.getName());

        final JavaCompile javacTask = taskFactory.create(new JavaCompileConfigAction(scope));
        scope.setJavacTask(javacTask);

        setupCompileTaskDependencies(scope, javacTask);

        postJavacCreation(scope);

        return javacTask;
    }

    /**
     * Add stream of classes compiled by javac to transform manager.
     *
     * This should not be called for classes that will also be compiled from source by jack.
     */
    public void addJavacClassesStream(VariantScope scope) {
        FileCollection javaOutputs =
                ((BuildableArtifactImpl) scope.getBuildArtifactsHolder().getArtifactFiles(JAVAC))
                        .getFileCollection();
        Preconditions.checkNotNull(javaOutputs);
        // create separate streams for the output of JAVAC and for the pre/post javac
        // bytecode hooks
        scope.getTransformManager()
                .addStream(
                        OriginalStream.builder(project, "javac-output")
                                // Need both classes and resources because some annotation
                                // processors generate resources
                                .addContentTypes(
                                        DefaultContentType.CLASSES, DefaultContentType.RESOURCES)
                                .addScope(Scope.PROJECT)
                                .setFileCollection(javaOutputs)
                                .build());

        scope.getTransformManager()
                .addStream(
                        OriginalStream.builder(project, "pre-javac-generated-bytecode")
                                .addContentTypes(
                                        DefaultContentType.CLASSES, DefaultContentType.RESOURCES)
                                .addScope(Scope.PROJECT)
                                .setFileCollection(
                                        scope.getVariantData().getAllPreJavacGeneratedBytecode())
                                .build());

        scope.getTransformManager()
                .addStream(
                        OriginalStream.builder(project, "post-javac-generated-bytecode")
                                .addContentTypes(
                                        DefaultContentType.CLASSES, DefaultContentType.RESOURCES)
                                .addScope(Scope.PROJECT)
                                .setFileCollection(
                                        scope.getVariantData().getAllPostJavacGeneratedBytecode())
                                .build());

        if (scope.hasOutput(InternalArtifactType.RUNTIME_R_CLASS_CLASSES)) {
            scope.getTransformManager()
                    .addStream(
                            OriginalStream.builder(project, "final-r-classes")
                                    .addContentTypes(
                                            DefaultContentType.CLASSES,
                                            DefaultContentType.RESOURCES)
                                    .addScope(Scope.PROJECT)
                                    .setFileCollection(
                                            scope.getOutput(
                                                    InternalArtifactType.RUNTIME_R_CLASS_CLASSES))
                                    .build());
        }
    }

    private static void setupCompileTaskDependencies(
            @NonNull VariantScope scope, @NonNull Task compileTask) {

        if (scope.getSourceGenTask() != null) {
            compileTask.dependsOn(scope.getSourceGenTask());
        }
    }

    protected void createCompileTask(@NonNull VariantScope variantScope) {
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

            androidBuilder
                    .getIssueReporter()
                    .reportWarning(EvalIssueReporter.Type.GENERIC, warningMsg);
        }

        addJavacClassesStream(variantScope);
        setJavaCompilerTask(javacTask, variantScope);
        createPostCompilationTasks(variantScope);
    }


    /** Makes the given task the one used by top-level "compile" task. */
    public static void setJavaCompilerTask(
            @NonNull Task javaCompilerTask, @NonNull VariantScope scope) {

        scope.getCompileTask().dependsOn(javaCompilerTask);
    }

    /**
     * Creates the task that will handle micro apk.
     *
     * New in 2.2, it now supports the unbundled mode, in which the apk is not bundled
     * anymore, but we still have an XML resource packaged, and a custom entry in the manifest.
     * This is triggered by passing a null {@link Configuration} object.
     *
     * @param scope the variant scope
     * @param config an optional Configuration object. if non null, this will embed the micro apk,
     *               if null this will trigger the unbundled mode.
     */
    public void createGenerateMicroApkDataTask(
            @NonNull VariantScope scope,
            @Nullable FileCollection config) {
        GenerateApkDataTask generateMicroApkTask =
                taskFactory.create(new GenerateApkDataTask.ConfigAction(scope, config));
        scope.setMicroApkTask(generateMicroApkTask);

        // the merge res task will need to run after this one.
        scope.getResourceGenTask().dependsOn(generateMicroApkTask);
    }

    public void createExternalNativeBuildJsonGenerators(@NonNull VariantScope scope) {

        CoreExternalNativeBuild externalNativeBuild = extension.getExternalNativeBuild();
        ExternalNativeBuildTaskUtils.ExternalNativeBuildProjectPathResolution pathResolution =
                ExternalNativeBuildTaskUtils.getProjectPath(externalNativeBuild);
        if (pathResolution.errorText != null) {
            androidBuilder
                    .getIssueReporter()
                    .reportError(
                            Type.EXTERNAL_NATIVE_BUILD_CONFIGURATION,
                            pathResolution.errorText,
                            scope.getVariantConfiguration().getFullName());
            return;
        }

        if (pathResolution.makeFile == null) {
            // No project
            return;
        }

        scope.setExternalNativeJsonGenerator(
                ExternalNativeJsonGenerator.create(
                        project.getPath(),
                        project.getProjectDir(),
                        project.getBuildDir(),
                        pathResolution.externalNativeBuildDir,
                        checkNotNull(pathResolution.buildSystem),
                        pathResolution.makeFile,
                        androidBuilder,
                        sdkHandler,
                        scope));
    }

    public void createExternalNativeBuildTasks(@NonNull VariantScope scope) {
        ExternalNativeJsonGenerator generator = scope.getExternalNativeJsonGenerator();
        if (generator == null) {
            return;
        }

        // Set up JSON generation tasks
        Task generateTask =
                taskFactory.create(
                        ExternalNativeBuildJsonTask.createTaskConfigAction(generator, scope));

        generateTask.dependsOn(scope.getPreBuildTask());

        ProjectOptions projectOptions = globalScope.getProjectOptions();

        String targetAbi =
                projectOptions.get(BooleanOption.BUILD_ONLY_TARGET_ABI)
                        ? projectOptions.get(StringOption.IDE_BUILD_TARGET_ABI)
                        : null;

        // Set up build tasks
        ExternalNativeBuildTask buildTask =
                taskFactory.create(
                        new ExternalNativeBuildTask.ConfigAction(
                                targetAbi, generator, scope, androidBuilder));

        buildTask.dependsOn(
                generateTask, scope.getArtifactFileCollection(RUNTIME_CLASSPATH, ALL, JNI));
        scope.setExternalNativeBuildTask(buildTask);
        scope.getCompileTask().dependsOn(buildTask);

        // Set up clean tasks
        Task cleanTask = checkNotNull(taskFactory.findByName("clean"));
        cleanTask.dependsOn(
                taskFactory
                        .create(
                                new ExternalNativeCleanTask.ConfigAction(
                                        generator, scope, androidBuilder))
                        .getName());
    }

    public void createNdkTasks(@NonNull VariantScope scope) {
        if (ExternalNativeBuildTaskUtils.isExternalNativeBuildEnabled(
                extension.getExternalNativeBuild())) {
            return;
        }

        NdkCompile ndkCompileTask = taskFactory.create(new NdkCompile.ConfigAction(scope));

        ndkCompileTask.dependsOn(scope.getPreBuildTask());
        if (Boolean.TRUE.equals(
                scope.getVariantData()
                        .getVariantConfiguration()
                        .getMergedFlavor()
                        .getRenderscriptNdkModeEnabled())) {
            ndkCompileTask.dependsOn(scope.getRenderscriptCompileTask());
        }
        scope.getCompileTask().dependsOn(ndkCompileTask);
    }

    /** Create transform for stripping debug symbols from native libraries before deploying. */
    public static void createStripNativeLibraryTask(
            @NonNull TaskFactory taskFactory, @NonNull VariantScope scope) {
        if (!scope.getGlobalScope().getNdkHandler().isConfigured()) {
            // We don't know where the NDK is, so we won't be stripping the debug symbols from
            // native libraries.
            return;
        }
        TransformManager transformManager = scope.getTransformManager();
        GlobalScope globalScope = scope.getGlobalScope();
        transformManager.addTransform(
                taskFactory,
                scope,
                new StripDebugSymbolTransform(
                        globalScope.getProject(),
                        globalScope.getNdkHandler(),
                        globalScope.getExtension().getPackagingOptions().getDoNotStrip(),
                        scope.getVariantConfiguration().getType() == VariantType.LIBRARY));
    }

    /** Creates the tasks to build unit tests. */
    public void createUnitTestVariantTasks(@NonNull TestVariantData variantData) {
        VariantScope variantScope = variantData.getScope();
        BaseVariantData testedVariantData =
                checkNotNull(variantScope.getTestedVariantData(), "Not a unit test variant");
        VariantScope testedVariantScope = testedVariantData.getScope();

        createPreBuildTasks(variantScope);

        // Create all current streams (dependencies mostly at this point)
        createDependencyStreams(variantScope);

        createProcessJavaResTask(variantScope);
        createCompileAnchorTask(variantScope);

        if (extension.getTestOptions().getUnitTests().isIncludeAndroidResources()) {
            File unitTestConfigDir =
                    new File(
                            globalScope.getIntermediatesDir(),
                            "unitTestConfig/" + variantData.getVariantConfiguration().getDirName());
            GenerateTestConfig generateTestConfig =
                    taskFactory.create(
                            new GenerateTestConfig.ConfigAction(variantScope, unitTestConfigDir));
            variantScope.addTaskOutput(
                    InternalArtifactType.UNIT_TEST_CONFIG_DIRECTORY,
                    unitTestConfigDir,
                    generateTestConfig.getName());
            variantScope.getCompileTask().dependsOn(generateTestConfig);
        }

        // :app:compileDebugUnitTestSources should be enough for running tests from AS, so add
        // dependencies on tasks that prepare necessary data files.
        Task compileTask = variantScope.getCompileTask();
        compileTask.dependsOn(

                variantScope.getProcessJavaResourcesTask(),
                testedVariantScope.getProcessJavaResourcesTask());
        if (extension.getTestOptions().getUnitTests().isIncludeAndroidResources()) {
            compileTask.dependsOn(testedVariantScope.getMergeAssetsTask());
            compileTask.dependsOn(
                    testedVariantScope
                            .getVariantData()
                            .getTaskByKind(TaskContainer.TaskKind.PROCESS_MANIFEST));
        }

        // Empty R class jar. TODO: Resources support for unit tests?
        variantScope.addTaskOutput(
                InternalArtifactType.COMPILE_ONLY_NAMESPACED_R_CLASS_JAR, project.files(), null);

        JavaCompile javacTask = createJavacTask(variantScope);
        addJavacClassesStream(variantScope);
        setJavaCompilerTask(javacTask, variantScope);
        javacTask.dependsOn(testedVariantScope.getJavacTask());

        createMergeJavaResTransform(variantScope);

        createRunUnitTestTask(variantScope);

        DefaultTask assembleUnitTests = variantScope.getAssembleTask();
        assembleUnitTests.dependsOn(createMockableJar);

        // This hides the assemble unit test task from the task list.
        assembleUnitTests.setGroup(null);
    }

    protected void createSplitsDiscovery(VariantScope variantScope) {
        if (variantScope.getVariantData().getType().getCanHaveSplits()) {
            // split list calculation and save to this file.
            File splitListOutputFile =
                    new File(
                            new File(variantScope.getSplitSupportDirectory(), "split-list"),
                            FN_SPLIT_LIST);
            SplitsDiscovery splitsDiscoveryAndroidTask =
                    taskFactory.create(
                            new SplitsDiscovery.ConfigAction(variantScope, splitListOutputFile));

            variantScope.addTaskOutput(
                    InternalArtifactType.SPLIT_LIST,
                    splitListOutputFile,
                    splitsDiscoveryAndroidTask.getName());
        }
    }

    /** Creates the tasks to build android tests. */
    public void createAndroidTestVariantTasks(@NonNull TestVariantData variantData) {
        VariantScope variantScope = variantData.getScope();

        createAnchorTasks(variantScope);

        // Create all current streams (dependencies mostly at this point)
        createDependencyStreams(variantScope);

        // persist variant's output
        taskFactory.create(new MainApkListPersistence.ConfigAction(variantScope));

        // Add a task to process the manifest
        createProcessTestManifestTask(
                variantScope, checkNotNull(variantScope.getTestedVariantData()).getScope());

        // Add a task to create the res values
        createGenerateResValuesTask(variantScope);

        // Add a task to compile renderscript files.
        createRenderscriptTask(variantScope);

        // Add a task to merge the resource folders
        createMergeResourcesTask(variantScope, true);

        // Add tasks to compile shader
        createShaderTask(variantScope);

        // Add a task to merge the assets folders
        createMergeAssetsTask(variantScope);

        // Add a task to create the BuildConfig class
        createBuildConfigTask(variantScope);

        // Add a task to generate resource source files
        createApkProcessResTask(variantScope);

        // process java resources
        createProcessJavaResTask(variantScope);

        createAidlTask(variantScope);

        // Add NDK tasks
        if (!isComponentModelPlugin()) {
            createNdkTasks(variantScope);
        }
        variantScope.setNdkBuildable(getNdkBuildable(variantData));

        // add tasks to merge jni libs.
        createMergeJniLibFoldersTasks(variantScope);

        // Add data binding tasks if enabled
        createDataBindingTasksIfNecessary(variantScope, MergeType.MERGE);

        // Add a task to compile the test application
        JavaCompile javacTask = createJavacTask(variantScope);
        addJavacClassesStream(variantScope);
        setJavaCompilerTask(javacTask, variantScope);
        createPostCompilationTasks(variantScope);

        createPackagingTask(variantScope, null /* buildInfoGeneratorTask */);

        taskFactory.configure(
                ASSEMBLE_ANDROID_TEST,
                assembleTest ->
                        assembleTest.dependsOn(variantData.getScope().getAssembleTask().getName()));

        createConnectedTestForVariant(variantScope);
    }

    /** Is the given variant relevant for lint? */
    private static boolean isLintVariant(@NonNull VariantScope variantScope) {
        // Only create lint targets for variants like debug and release, not debugTest
        final VariantType variantType = variantScope.getVariantConfiguration().getType();
        return !variantType.isForTesting() && variantType != FEATURE;
    }

    /**
     * Add tasks for running lint on individual variants. We've already added a lint task earlier
     * which runs on all variants.
     */
    public void createLintTasks(final VariantScope scope) {
        if (!isLintVariant(scope)) {
            return;
        }

        taskFactory.create(new LintPerVariantTask.ConfigAction(scope));
    }

    /** Returns the full path of a task given its name. */
    private String getTaskPath(String taskName) {
        return project.getRootProject() == project
                ? ':' + taskName
                : project.getPath() + ':' + taskName;
    }

    private void maybeCreateLintVitalTask(@NonNull ApkVariantData variantData) {
        VariantScope variantScope = variantData.getScope();
        GradleVariantConfiguration variantConfig = variantData.getVariantConfiguration();

        if (!isLintVariant(variantScope)
                || variantScope.getInstantRunBuildContext().isInInstantRunMode()
                || variantConfig.getBuildType().isDebuggable()
                || !extension.getLintOptions().isCheckReleaseBuilds()) {
            return;
        }

        LintPerVariantTask lintReleaseCheck =
                taskFactory.create(new LintPerVariantTask.VitalConfigAction(variantScope));
        if (variantData.javacTask != null) {
            lintReleaseCheck.dependsOn(variantData.javacTask);
        }

        variantScope.getAssembleTask().dependsOn(lintReleaseCheck);

        // If lint is being run, we do not need to run lint vital.
        project.getGradle()
                .getTaskGraph()
                .whenReady(
                        taskGraph -> {
                            if (taskGraph.hasTask(getTaskPath(LINT))) {
                                project.getTasks()
                                        .getByName(lintReleaseCheck.getName())
                                        .setEnabled(false);
                            }
                        });
    }

    private void createRunUnitTestTask(
            @NonNull final VariantScope variantScope) {
        final AndroidUnitTest runTestsTask =
                taskFactory.create(new AndroidUnitTest.ConfigAction(variantScope));

        taskFactory.configure(
                JavaPlugin.TEST_TASK_NAME, test -> test.dependsOn(runTestsTask.getName()));
    }

    public void createTopLevelTestTasks(boolean hasFlavors) {
        createMockableJarTask();
        createAttrFromAndroidJarTask();

        final List<String> reportTasks = Lists.newArrayListWithExpectedSize(2);

        List<DeviceProvider> providers = extension.getDeviceProviders();

        // If more than one flavor, create a report aggregator task and make this the parent
        // task for all new connected tasks.  Otherwise, create a top level connectedAndroidTest
        // DefaultTask.

        DefaultTask connectedAndroidTestTask;
        if (hasFlavors) {
            connectedAndroidTestTask =
                    taskFactory.create(
                            new AndroidReportTask.ConfigAction(
                                    globalScope,
                                    AndroidReportTask.ConfigAction.TaskKind.CONNECTED));
            reportTasks.add(connectedAndroidTestTask.getName());
        } else {
            connectedAndroidTestTask =
                    taskFactory.create(
                            CONNECTED_ANDROID_TEST,
                            connectedTask -> {
                                connectedTask.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
                                connectedTask.setDescription(
                                        "Installs and runs instrumentation tests "
                                                + "for all flavors on connected devices.");
                            });
        }

        taskFactory.configure(
                CONNECTED_CHECK, check -> check.dependsOn(connectedAndroidTestTask.getName()));

        DefaultTask deviceAndroidTestTask;
        // if more than one provider tasks, either because of several flavors, or because of
        // more than one providers, then create an aggregate report tasks for all of them.
        if (providers.size() > 1 || hasFlavors) {
            deviceAndroidTestTask =
                    taskFactory.create(
                            new AndroidReportTask.ConfigAction(
                                    globalScope,
                                    AndroidReportTask.ConfigAction.TaskKind.DEVICE_PROVIDER));
            reportTasks.add(deviceAndroidTestTask.getName());
        } else {
            deviceAndroidTestTask =
                    taskFactory.create(
                            DEVICE_ANDROID_TEST,
                            providerTask -> {
                                providerTask.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
                                providerTask.setDescription(
                                        "Installs and runs instrumentation tests "
                                                + "using all Device Providers.");
                            });
        }

        taskFactory.configure(
                DEVICE_CHECK, check -> check.dependsOn(deviceAndroidTestTask.getName()));

        // Create top level unit test tasks.

        taskFactory.create(
                JavaPlugin.TEST_TASK_NAME,
                unitTestTask -> {
                    unitTestTask.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
                    unitTestTask.setDescription("Run unit tests for all variants.");
                });
        taskFactory.configure(
                JavaBasePlugin.CHECK_TASK_NAME,
                check -> check.dependsOn(JavaPlugin.TEST_TASK_NAME));

        // If gradle is launched with --continue, we want to run all tests and generate an
        // aggregate report (to help with the fact that we may have several build variants, or
        // or several device providers).
        // To do that, the report tasks must run even if one of their dependent tasks (flavor
        // or specific provider tasks) fails, when --continue is used, and the report task is
        // meant to run (== is in the task graph).
        // To do this, we make the children tasks ignore their errors (ie they won't fail and
        // stop the build).
        //TODO: move to mustRunAfter once is stable.
        if (!reportTasks.isEmpty() && project.getGradle().getStartParameter()
                .isContinueOnFailure()) {
            project.getGradle()
                    .getTaskGraph()
                    .whenReady(
                            taskGraph -> {
                                for (String reportTask : reportTasks) {
                                    if (taskGraph.hasTask(getTaskPath(reportTask))) {
                                        taskFactory.configure(
                                                reportTask,
                                                task -> ((AndroidReportTask) task).setWillRun());
                                    }
                                }
                            });
        }
    }

    protected void createConnectedTestForVariant(
            @NonNull final VariantScope variantScope) {
        final BaseVariantData baseVariantData = checkNotNull(variantScope.getTestedVariantData());
        final TestVariantData testVariantData = (TestVariantData) variantScope.getVariantData();

        boolean isLibrary =
                baseVariantData.getVariantConfiguration().getType() == VariantType.LIBRARY;

        TestDataImpl testData =
                new TestDataImpl(
                        testVariantData,
                        variantScope.getOutput(InternalArtifactType.APK),
                        isLibrary
                                ? null
                                : testVariantData
                                        .getTestedVariantData()
                                        .getScope()
                                        .getOutput(InternalArtifactType.APK));
        testData.setExtraInstrumentationTestRunnerArgs(
                projectOptions.getExtraInstrumentationTestRunnerArgs());

        configureTestData(testData);

        // create the check tasks for this test
        // first the connected one.
        ImmutableList<DefaultTask> artifactsTasks =
                ImmutableList.of(
                        variantScope.getAssembleTask(),
                        testVariantData.getTestedVariantData().getScope().getAssembleTask());

        DeviceProviderInstrumentTestTask connectedTask =
                taskFactory.create(
                        new DeviceProviderInstrumentTestTask.ConfigAction(
                                testVariantData.getScope(),
                                new ConnectedDeviceProvider(
                                        sdkHandler.getSdkInfo().getAdb(),
                                        extension.getAdbOptions().getTimeOutInMs(),
                                        new LoggerWrapper(logger)),
                                testData,
                                project.files() /* testTargetMetadata */));

        connectedTask.dependsOn(artifactsTasks.toArray());

        variantScope.setConnectedTask(connectedTask);

        taskFactory.configure(
                CONNECTED_ANDROID_TEST,
                connectedAndroidTest -> connectedAndroidTest.dependsOn(connectedTask.getName()));

        if (baseVariantData.getVariantConfiguration().getBuildType().isTestCoverageEnabled()) {

            Configuration jacocoAntConfiguration =
                    JacocoConfigurations.getJacocoAntTaskConfiguration(
                            project, getJacocoVersion(variantScope));
            JacocoReportTask reportTask =
                    taskFactory.create(
                            new JacocoReportTask.ConfigAction(
                                    variantScope, jacocoAntConfiguration));

            reportTask.dependsOn(connectedTask.getName());

            variantScope.setCoverageReportTask(reportTask);
            baseVariantData.getScope().getCoverageReportTask().dependsOn(reportTask);

            taskFactory.configure(
                    CONNECTED_ANDROID_TEST,
                    connectedAndroidTest -> connectedAndroidTest.dependsOn(reportTask.getName()));
        }

        List<DeviceProvider> providers = extension.getDeviceProviders();

        // now the providers.
        for (DeviceProvider deviceProvider : providers) {

            final DeviceProviderInstrumentTestTask providerTask =
                    taskFactory.create(
                            new DeviceProviderInstrumentTestTask.ConfigAction(
                                    testVariantData.getScope(),
                                    deviceProvider,
                                    testData,
                                    project.files() /* testTargetMetadata */));

            providerTask.dependsOn(artifactsTasks.toArray());
            taskFactory.configure(
                    DEVICE_ANDROID_TEST,
                    deviceAndroidTest -> deviceAndroidTest.dependsOn(providerTask.getName()));
        }

        // now the test servers
        List<TestServer> servers = extension.getTestServers();
        for (final TestServer testServer : servers) {
            final TestServerTask serverTask =
                    taskFactory.create(
                            new TestServerTask.TestServerTaskConfigAction(
                                    variantScope, testServer));
            serverTask.dependsOn(variantScope.getAssembleTask());

            taskFactory.configure(
                    DEVICE_CHECK,
                    deviceAndroidTest -> deviceAndroidTest.dependsOn(serverTask.getName()));
        }
    }

    /**
     * Creates the post-compilation tasks for the given Variant.
     *
     * These tasks create the dex file from the .class files, plus optional intermediary steps like
     * proguard and jacoco
     */
    public void createPostCompilationTasks(

            @NonNull final VariantScope variantScope) {

        checkNotNull(variantScope.getJavacTask());

        final BaseVariantData variantData = variantScope.getVariantData();
        final GradleVariantConfiguration config = variantData.getVariantConfiguration();

        TransformManager transformManager = variantScope.getTransformManager();

        // ---- Code Coverage first -----
        boolean isTestCoverageEnabled =
                config.getBuildType().isTestCoverageEnabled()
                        && !config.getType().isForTesting()
                        && !variantScope.getInstantRunBuildContext().isInInstantRunMode();
        if (isTestCoverageEnabled) {
            createJacocoTransform(variantScope);
        }

        maybeCreateDesugarTask(variantScope, config.getMinSdkVersion(), transformManager);

        AndroidConfig extension = variantScope.getGlobalScope().getExtension();

        // Merge Java Resources.
        createMergeJavaResTransform(variantScope);

        // ----- External Transforms -----
        // apply all the external transforms.
        List<Transform> customTransforms = extension.getTransforms();
        List<List<Object>> customTransformsDependencies = extension.getTransformsDependencies();

        for (int i = 0, count = customTransforms.size(); i < count; i++) {
            Transform transform = customTransforms.get(i);

            List<Object> deps = customTransformsDependencies.get(i);
            transformManager
                    .addTransform(taskFactory, variantScope, transform)
                    .ifPresent(
                            t -> {
                                if (!deps.isEmpty()) {
                                    t.dependsOn(deps);
                                }

                                // if the task is a no-op then we make assemble task depend on it.
                                if (transform.getScopes().isEmpty()) {
                                    variantScope.getAssembleTask().dependsOn(t);
                                }
                            });
        }

        // ----- Android studio profiling transforms
        for (String jar : getAdvancedProfilingTransforms(projectOptions)) {
            if (variantScope.getVariantConfiguration().getBuildType().isDebuggable()
                    && variantData.getType().equals(VariantType.APK)
                    && jar != null) {
                transformManager.addTransform(
                        taskFactory, variantScope, new CustomClassTransform(jar));
            }
        }

        // ----- Minify next -----
        maybeCreateJavaCodeShrinkerTransform(variantScope);

        maybeCreateResourcesShrinkerTransform(variantScope);

        // ----- 10x support

        PreColdSwapTask preColdSwapTask = null;
        if (variantScope.getInstantRunBuildContext().isInInstantRunMode()) {

            DefaultTask allActionsAnchorTask = createInstantRunAllActionsTasks(variantScope);
            assert variantScope.getInstantRunTaskManager() != null;
            preColdSwapTask =
                    variantScope.getInstantRunTaskManager().createPreColdswapTask(projectOptions);
            preColdSwapTask.dependsOn(allActionsAnchorTask);

            // force pre-dexing to be true as we rely on individual slices to be packaged
            // separately.
            extension.getDexOptions().setPreDexLibraries(true);
            variantScope.getInstantRunTaskManager().createSlicerTask();

            extension.getDexOptions().setJumboMode(true);
        }
        // ----- Multi-Dex support

        DexingType dexingType = variantScope.getDexingType();

        // Upgrade from legacy multi-dex to native multi-dex if possible when using with a device
        if (dexingType == DexingType.LEGACY_MULTIDEX) {
            if (variantScope.getVariantConfiguration().isMultiDexEnabled()
                    && variantScope
                                    .getVariantConfiguration()
                                    .getMinSdkVersionWithTargetDeviceApi()
                                    .getFeatureLevel()
                            >= 21) {
                dexingType = DexingType.NATIVE_MULTIDEX;
            }
        }

        Optional<TransformTask> multiDexClassListTask;

        if (dexingType == DexingType.LEGACY_MULTIDEX) {
            boolean proguardInPipeline = variantScope.getCodeShrinker() == CodeShrinker.PROGUARD;

            // If ProGuard will be used, we'll end up with a "fat" jar anyway. If we're using the
            // new dexing pipeline, we'll use the new MainDexListTransform below, so there's no need
            // for merging all classes into a single jar.
            if (!proguardInPipeline && !usingIncrementalDexing(variantScope)) {
                // Create a transform to jar the inputs into a single jar. Merge the classes only,
                // no need to package the resources since they are not used during the computation.
                JarMergingTransform jarMergingTransform =
                        new JarMergingTransform(TransformManager.SCOPE_FULL_PROJECT);
                transformManager
                        .addTransform(taskFactory, variantScope, jarMergingTransform)
                        .ifPresent(variantScope::addColdSwapBuildTask);
            }

            // ---------
            // create the transform that's going to take the code and the proguard keep list
            // from above and compute the main class list.
            Transform multiDexTransform;
            if (usingIncrementalDexing(variantScope)) {
                if (projectOptions.get(BooleanOption.ENABLE_D8_MAIN_DEX_LIST)) {
                    multiDexTransform = new D8MainDexListTransform(variantScope);
                } else {
                    multiDexTransform =
                            new MainDexListTransform(variantScope, extension.getDexOptions());
                }
            } else {
                multiDexTransform = new MultiDexTransform(variantScope, extension.getDexOptions());
            }
            multiDexClassListTask =
                    transformManager.addTransform(taskFactory, variantScope, multiDexTransform);
            multiDexClassListTask.ifPresent(variantScope::addColdSwapBuildTask);
        } else {
            multiDexClassListTask = Optional.empty();
        }


        if (usingIncrementalDexing(variantScope)) {
            createNewDexTasks(variantScope, multiDexClassListTask.orElse(null), dexingType);
        } else {
            createDexTasks(variantScope, multiDexClassListTask.orElse(null), dexingType);
        }

        if (preColdSwapTask != null) {
            for (DefaultTask task : variantScope.getColdSwapBuildTasks()) {
                task.dependsOn(preColdSwapTask);
            }
        }

        // ---- Create tasks to publish the pipeline output as needed.

        final File intermediatesDir = variantScope.getGlobalScope().getIntermediatesDir();
        createPipelineToPublishTask(
                variantScope,
                transformManager.getPipelineOutputAsFileCollection(StreamFilter.DEX),
                FileUtils.join(intermediatesDir, "bundling", "dex"),
                PUBLISHED_DEX);

        createPipelineToPublishTask(
                variantScope,
                transformManager.getPipelineOutputAsFileCollection(StreamFilter.RESOURCES),
                FileUtils.join(intermediatesDir, "bundling", "java-res"),
                PUBLISHED_JAVA_RES);

        createPipelineToPublishTask(
                variantScope,
                transformManager.getPipelineOutputAsFileCollection(StreamFilter.NATIVE_LIBS),
                FileUtils.join(intermediatesDir, "bundling", "native-libs"),
                PUBLISHED_NATIVE_LIBS);
    }

    private void createPipelineToPublishTask(
            @NonNull VariantScope variantScope,
            @NonNull FileCollection fileCollection,
            @NonNull File outputFile,
            @NonNull InternalArtifactType outputType) {
        PipelineToPublicationTask task =
                taskFactory.create(
                        new PipelineToPublicationTask.ConfigAction(
                                variantScope, fileCollection, outputFile, outputType));

        variantScope.addTaskOutput(outputType, outputFile, task.getName());
    }

    private void maybeCreateDesugarTask(
            @NonNull VariantScope variantScope,
            @NonNull AndroidVersion minSdk,
            @NonNull TransformManager transformManager) {
        if (variantScope.getJava8LangSupportType() == Java8LangSupport.DESUGAR) {
            FileCache userCache = getUserIntermediatesCache();

            FixStackFramesTransform fixFrames =
                    new FixStackFramesTransform(
                            () -> androidBuilder.getBootClasspath(true),
                            System.getProperty("sun.boot.class.path"),
                            userCache);
            transformManager.addTransform(taskFactory, variantScope, fixFrames);

            DesugarTransform desugarTransform =
                    new DesugarTransform(
                            () -> androidBuilder.getBootClasspath(true),
                            System.getProperty("sun.boot.class.path"),
                            userCache,
                            minSdk.getFeatureLevel(),
                            androidBuilder.getJavaProcessExecutor(),
                            project.getLogger().isEnabled(LogLevel.INFO),
                            projectOptions.get(BooleanOption.ENABLE_GRADLE_WORKERS),
                            variantScope.getGlobalScope().getTmpFolder().toPath(),
                            getProjectVariantId(variantScope),
                            projectOptions.get(BooleanOption.ENABLE_INCREMENTAL_DESUGARING));
            transformManager.addTransform(taskFactory, variantScope, desugarTransform);

            if (minSdk.getFeatureLevel()
                    >= DesugarProcessArgs.MIN_SUPPORTED_API_TRY_WITH_RESOURCES) {
                return;
            }

            if (variantScope.getVariantConfiguration().getType().isForTesting()) {
                BaseVariantData testedVariant =
                        Objects.requireNonNull(variantScope.getTestedVariantData());
                if (testedVariant.getType() != VariantType.LIBRARY) {
                    // test variants, except for library, should not package try-with-resources jar
                    // as the tested variant already contains it
                    return;
                }
            }

            // add runtime classes for try-with-resources support
            String taskName = variantScope.getTaskName(ExtractTryWithResourcesSupportJar.TASK_NAME);
            ExtractTryWithResourcesSupportJar extractTryWithResources =
                    taskFactory.create(
                            new ExtractTryWithResourcesSupportJar.ConfigAction(
                                    variantScope.getTryWithResourceRuntimeSupportJar(),
                                    taskName,
                                    variantScope.getFullVariantName()));
            variantScope.getTryWithResourceRuntimeSupportJar().builtBy(extractTryWithResources);
            transformManager.addStream(
                    OriginalStream.builder(project, "runtime-deps-try-with-resources")
                            .addContentTypes(TransformManager.CONTENT_CLASS)
                            .addScope(Scope.EXTERNAL_LIBRARIES)
                            .setFileCollection(variantScope.getTryWithResourceRuntimeSupportJar())
                            .build());
        }
    }

    /**
     * Creates tasks used for DEX generation. This will use a new pipeline that uses dex archives in
     * order to enable incremental dexing support.
     */
    private void createNewDexTasks(
            @NonNull VariantScope variantScope,
            @Nullable TransformTask multiDexClassListTask,
            @NonNull DexingType dexingType) {
        TransformManager transformManager = variantScope.getTransformManager();

        DefaultDexOptions dexOptions;
        if (variantScope.getVariantData().getType().isForTesting()) {
            // Don't use custom dx flags when compiling the test FULL_APK. They can break the test FULL_APK,
            // like --minimal-main-dex.
            dexOptions = DefaultDexOptions.copyOf(extension.getDexOptions());
            dexOptions.setAdditionalParameters(ImmutableList.of());
        } else {
            dexOptions = extension.getDexOptions();
        }

        boolean minified = runJavaCodeShrinker(variantScope);
        FileCache userLevelCache = getUserDexCache(minified, dexOptions.getPreDexLibraries());
        DexArchiveBuilderTransform preDexTransform =
                new DexArchiveBuilderTransformBuilder()
                        .setAndroidJarClasspath(
                                () ->
                                        variantScope
                                                .getGlobalScope()
                                                .getAndroidBuilder()
                                                .getBootClasspath(false))
                        .setDexOptions(dexOptions)
                        .setMessageReceiver(variantScope.getGlobalScope().getMessageReceiver())
                        .setUserLevelCache(userLevelCache)
                        .setMinSdkVersion(variantScope.getMinSdkVersion().getFeatureLevel())
                        .setDexer(variantScope.getDexer())
                        .setUseGradleWorkers(
                                projectOptions.get(BooleanOption.ENABLE_GRADLE_WORKERS))
                        .setInBufferSize(projectOptions.get(IntegerOption.DEXING_READ_BUFFER_SIZE))
                        .setOutBufferSize(
                                projectOptions.get(IntegerOption.DEXING_WRITE_BUFFER_SIZE))
                        .setIsDebuggable(
                                variantScope
                                        .getVariantConfiguration()
                                        .getBuildType()
                                        .isDebuggable())
                        .setJava8LangSupportType(variantScope.getJava8LangSupportType())
                        .setEnableIncrementalDesugaring(
                                projectOptions.get(BooleanOption.ENABLE_INCREMENTAL_DESUGARING))
                        .setProjectVariant(getProjectVariantId(variantScope))
                        .createDexArchiveBuilderTransform();
        transformManager
                .addTransform(taskFactory, variantScope, preDexTransform)
                .ifPresent(variantScope::addColdSwapBuildTask);

        boolean isDebuggable = variantScope.getVariantConfiguration().getBuildType().isDebuggable();
        if (dexingType != DexingType.LEGACY_MULTIDEX
                && variantScope.getCodeShrinker() == null
                && extension.getTransforms().isEmpty()) {
            ExternalLibsMergerTransform externalLibsMergerTransform =
                    new ExternalLibsMergerTransform(
                            dexingType,
                            variantScope.getDexMerger(),
                            variantScope.getMinSdkVersion().getFeatureLevel(),
                            isDebuggable,
                            variantScope.getGlobalScope().getMessageReceiver(),
                            DexMergerTransformCallable::new);

            transformManager.addTransform(taskFactory, variantScope, externalLibsMergerTransform);
        }

        DexMergerTransform dexTransform =
                new DexMergerTransform(
                        dexingType,
                        dexingType == DexingType.LEGACY_MULTIDEX
                                ? project.files(variantScope.getMainDexListFile())
                                : null,
                        variantScope.getGlobalScope().getMessageReceiver(),
                        variantScope.getDexMerger(),
                        variantScope.getMinSdkVersion().getFeatureLevel(),
                        isDebuggable);
        Optional<TransformTask> dexTask =
                transformManager.addTransform(taskFactory, variantScope, dexTransform);
        // need to manually make dex task depend on MultiDexTransform since there's no stream
        // consumption making this automatic
        dexTask.ifPresent(
                t -> {
                    if (multiDexClassListTask != null) {
                        t.dependsOn(multiDexClassListTask);
                    }
                    variantScope.addColdSwapBuildTask(t);
                });
    }

    @NonNull
    private static String getProjectVariantId(@NonNull VariantScope variantScope) {
        return variantScope.getGlobalScope().getProject().getName()
                + ":"
                + variantScope.getFullVariantName();
    }

    private boolean usingIncrementalDexing(@NonNull VariantScope variantScope) {
        if (!projectOptions.get(BooleanOption.ENABLE_DEX_ARCHIVE)) {
            return false;
        }
        if (variantScope.getVariantConfiguration().getBuildType().isDebuggable()) {
            return true;
        }

        // In release builds only D8 can be used. See b/37140568 for details.
        return projectOptions.get(BooleanOption.ENABLE_D8);
    }

    @Nullable
    private FileCache getUserDexCache(boolean isMinifiedEnabled, boolean preDexLibraries) {
        if (!preDexLibraries || isMinifiedEnabled) {
            return null;
        }

        return getUserIntermediatesCache();
    }

    @Nullable
    private FileCache getUserIntermediatesCache() {
        if (globalScope
                .getProjectOptions()
                .get(BooleanOption.ENABLE_INTERMEDIATE_ARTIFACTS_CACHE)) {
            return globalScope.getBuildCache();
        } else {
            return null;
        }
    }

    /** Creates the pre-dexing task if needed, and task for producing the final DEX file(s). */
    private void createDexTasks(
            @NonNull VariantScope variantScope,
            @Nullable TransformTask multiDexClassListTask,
            @NonNull DexingType dexingType) {
        TransformManager transformManager = variantScope.getTransformManager();
        AndroidBuilder androidBuilder = variantScope.getGlobalScope().getAndroidBuilder();

        DefaultDexOptions dexOptions;
        if (variantScope.getVariantData().getType().isForTesting()) {
            // Don't use custom dx flags when compiling the test FULL_APK. They can break the test FULL_APK,
            // like --minimal-main-dex.
            dexOptions = DefaultDexOptions.copyOf(extension.getDexOptions());
            dexOptions.setAdditionalParameters(ImmutableList.of());
        } else {
            dexOptions = extension.getDexOptions();
        }

        boolean cachePreDex =
                dexingType.isPreDex()
                        && dexOptions.getPreDexLibraries()
                        && !runJavaCodeShrinker(variantScope);
        boolean preDexEnabled =
                variantScope.getInstantRunBuildContext().isInInstantRunMode() || cachePreDex;
        if (preDexEnabled) {
            FileCache buildCache;
            if (cachePreDex
                    && projectOptions.get(BooleanOption.ENABLE_INTERMEDIATE_ARTIFACTS_CACHE)) {
                buildCache = this.buildCache;
            } else {
                buildCache = null;
            }

            PreDexTransform preDexTransform =
                    new PreDexTransform(
                            dexOptions,
                            androidBuilder,
                            buildCache,
                            dexingType,
                            variantScope.getMinSdkVersion().getFeatureLevel());
            transformManager
                    .addTransform(taskFactory, variantScope, preDexTransform)
                    .ifPresent(variantScope::addColdSwapBuildTask);
        }

        if (!preDexEnabled || dexingType != DexingType.NATIVE_MULTIDEX) {
            // run if non native multidex or no pre-dexing
            DexTransform dexTransform =
                    new DexTransform(
                            dexOptions,
                            dexingType,
                            preDexEnabled,
                            project.files(variantScope.getMainDexListFile()),
                            checkNotNull(androidBuilder.getTargetInfo(), "Target Info not set."),
                            androidBuilder.getDexByteCodeConverter(),
                            variantScope.getGlobalScope().getMessageReceiver(),
                            variantScope.getMinSdkVersion().getFeatureLevel());
            Optional<TransformTask> dexTask =
                    transformManager.addTransform(taskFactory, variantScope, dexTransform);
            // need to manually make dex task depend on MultiDexTransform since there's no stream
            // consumption making this automatic
            dexTask.ifPresent(
                    t -> {
                        if (multiDexClassListTask != null) {
                            t.dependsOn(multiDexClassListTask);
                        }
                        variantScope.addColdSwapBuildTask(t);
                    });
        }
    }

    private boolean runJavaCodeShrinker(VariantScope variantScope) {
        return variantScope.getCodeShrinker() != null || isTestedAppObfuscated(variantScope);
    }

    /**
     * Default values if {@code false}, only {@link TestApplicationTaskManager} overrides this,
     * because tested applications might be obfuscated.
     *
     * @return if the tested application is obfuscated
     */
    protected boolean isTestedAppObfuscated(@NonNull VariantScope variantScope) {
        return false;
    }

    /** Create InstantRun related tasks that should be ran right after the java compilation task. */
    @NonNull
    private DefaultTask createInstantRunAllActionsTasks(@NonNull VariantScope variantScope) {

        DefaultTask allActionAnchorTask =
                taskFactory.create(new InstantRunAnchorTaskConfigAction(variantScope));

        TransformManager transformManager = variantScope.getTransformManager();

        ExtractJarsTransform extractJarsTransform =
                new ExtractJarsTransform(
                        ImmutableSet.of(DefaultContentType.CLASSES),
                        ImmutableSet.of(Scope.SUB_PROJECTS));
        Optional<TransformTask> extractJarsTask =
                transformManager.addTransform(taskFactory, variantScope, extractJarsTransform);

        InstantRunTaskManager instantRunTaskManager =
                new InstantRunTaskManager(
                        getLogger(),
                        variantScope,
                        variantScope.getTransformManager(),
                        taskFactory,
                        recorder);

        FileCollection instantRunMergedManifests =
                variantScope.getOutput(INSTANT_RUN_MERGED_MANIFESTS);

        variantScope.setInstantRunTaskManager(instantRunTaskManager);
        AndroidVersion minSdkForDx = variantScope.getMinSdkVersion();
        BuildInfoLoaderTask buildInfoLoaderTask =
                instantRunTaskManager.createInstantRunAllTasks(
                        variantScope.getGlobalScope().getExtension().getDexOptions(),
                        androidBuilder::getDexByteCodeConverter,
                        extractJarsTask.orElse(null),
                        allActionAnchorTask,
                        getResMergingScopes(variantScope),
                        instantRunMergedManifests,
                        true /* addResourceVerifier */,
                        minSdkForDx.getFeatureLevel());

        if (variantScope.getSourceGenTask() != null) {
            variantScope.getSourceGenTask().dependsOn(buildInfoLoaderTask);
        }

        return allActionAnchorTask;
    }

    protected void handleJacocoDependencies(@NonNull VariantScope variantScope) {
        GradleVariantConfiguration config = variantScope.getVariantConfiguration();
        // we add the jacoco jar if coverage is enabled, but we don't add it
        // for test apps as it's already part of the tested app.
        // For library project, since we cannot use the local jars of the library,
        // we add it as well.
        boolean isTestCoverageEnabled =
                config.getBuildType().isTestCoverageEnabled()
                        && !variantScope.getInstantRunBuildContext().isInInstantRunMode()
                        && (!config.getType().isForTesting()
                                || (config.getTestedConfig() != null
                                        && config.getTestedConfig().getType()
                                                == VariantType.LIBRARY));
        if (isTestCoverageEnabled) {
            if (variantScope.getDexer() == DexerTool.DX) {
                androidBuilder
                        .getIssueReporter()
                        .reportWarning(
                                Type.GENERIC,
                                String.format(
                                        "Jacoco version is downgraded to %s because dx is used. "
                                                + "This is due to -P%s=false flag. See "
                                                + "https://issuetracker.google.com/37116789 for "
                                                + "more details.",
                                        JacocoConfigurations.VERSION_FOR_DX,
                                        BooleanOption.ENABLE_D8.getPropertyName()));
            }

            String jacocoAgentRuntimeDependency =
                    JacocoConfigurations.getAgentRuntimeDependency(getJacocoVersion(variantScope));
            project.getDependencies()
                    .add(
                            variantScope.getVariantDependencies().getRuntimeClasspath().getName(),
                            jacocoAgentRuntimeDependency);

            // we need to force the same version of Jacoco we use for instrumentation
            variantScope
                    .getVariantDependencies()
                    .getRuntimeClasspath()
                    .resolutionStrategy(r -> r.force(jacocoAgentRuntimeDependency));
        }
    }

    @NonNull
    public String getJacocoVersion(@NonNull VariantScope scope) {
        if (scope.getDexer() == DexerTool.DX) {
            return JacocoConfigurations.VERSION_FOR_DX;
        } else {
            return extension.getJacoco().getVersion();
        }
    }

    public void createJacocoTransform(
            @NonNull final VariantScope variantScope) {
        JacocoTransform jacocoTransform =
                new JacocoTransform(
                        JacocoConfigurations.getJacocoAntTaskConfiguration(
                                project, getJacocoVersion(variantScope)));

        variantScope.getTransformManager().addTransform(taskFactory, variantScope, jacocoTransform);
    }

    /**
     * Must be called before the javac task is created so that we it can be earlier in the transform
     * pipeline.
     */
    private void createDataBindingMergeArtifactsTask(@NonNull VariantScope variantScope) {
        if (!extension.getDataBinding().isEnabled()) {
            return;
        }
        final BaseVariantData variantData = variantScope.getVariantData();
        VariantType type = variantData.getType();
        boolean isTest = type == VariantType.ANDROID_TEST || type == VariantType.UNIT_TEST;
        if (isTest && !extension.getDataBinding().isEnabledForTests()) {
            BaseVariantData testedVariantData = checkNotNull(variantScope.getTestedVariantData());
            if (testedVariantData.getType() != LIBRARY) {
                return;
            }
        }
        File outFolder =
                new File(
                        variantScope.getBuildFolderForDataBindingCompiler(),
                        DataBindingBuilder.ARTIFACT_FILES_DIR_FROM_LIBS);


        Optional<TransformTask> dataBindingMergeTask;
        dataBindingMergeTask =
                variantScope
                        .getTransformManager()
                        .addTransform(
                                taskFactory,
                                variantScope,
                                new DataBindingMergeArtifactsTransform(getLogger(), outFolder));

        dataBindingMergeTask.ifPresent(
                task ->
                        variantScope.addTaskOutput(
                                InternalArtifactType.DATA_BINDING_DEPENDENCY_ARTIFACTS,
                                outFolder,
                                task.getName()));
    }

    private void createDataBindingMergeBaseClassesTask(@NonNull VariantScope variantScope) {
        final BaseVariantData variantData = variantScope.getVariantData();
        VariantType type = variantData.getType();
        boolean isTest = type == VariantType.ANDROID_TEST || type == VariantType.UNIT_TEST;
        if (isTest && !extension.getDataBinding().isEnabledForTests()) {
            BaseVariantData testedVariantData = checkNotNull(variantScope.getTestedVariantData());
            if (testedVariantData.getType() != LIBRARY) {
                return;
            }
        }
        File outFolder =
                variantScope.getIntermediateDir(DATA_BINDING_BASE_CLASS_LOGS_DEPENDENCY_ARTIFACTS);

        Optional<TransformTask> mergeBaseClassesTask;
        mergeBaseClassesTask =
                variantScope
                        .getTransformManager()
                        .addTransform(
                                taskFactory,
                                variantScope,
                                new DataBindingMergeGenClassLogTransform(getLogger(), outFolder));

        mergeBaseClassesTask.ifPresent(
                task ->
                        variantScope.addTaskOutput(
                                DATA_BINDING_BASE_CLASS_LOGS_DEPENDENCY_ARTIFACTS,
                                outFolder,
                                task.getName()));
    }

    protected void createDataBindingTasksIfNecessary(
            @NonNull VariantScope scope, @NonNull MergeType mergeType) {
        if (!extension.getDataBinding().isEnabled()) {
            return;
        }
        createDataBindingMergeBaseClassesTask(scope);
        createDataBindingMergeArtifactsTask(scope);


        VariantType type = scope.getVariantData().getType();
        boolean isTest = type == VariantType.ANDROID_TEST || type == VariantType.UNIT_TEST;
        if (isTest && !extension.getDataBinding().isEnabledForTests()) {
            BaseVariantData testedVariantData = checkNotNull(scope.getTestedVariantData());
            if (testedVariantData.getType() != LIBRARY) {
                return;
            }
        }

        dataBindingBuilder.setDebugLogEnabled(getLogger().isDebugEnabled());

        DataBindingExportBuildInfoTask exportBuildInfo =
                taskFactory.create(
                        new DataBindingExportBuildInfoTask.ConfigAction(scope, mergeType));

        exportBuildInfo.dependsOn(scope.getSourceGenTask());

        scope.setDataBindingExportBuildInfoTask(exportBuildInfo);

        // setup generate base class task
        File baseClassOutFolder =
                new File(
                        globalScope.getGeneratedDir(),
                        "source/dataBinding/baseClasses/"
                                + scope.getVariantConfiguration().getDirName());
        File baseClassLogFolder =
                scope.getIntermediateDir(InternalArtifactType.DATA_BINDING_BASE_CLASS_LOG_ARTIFACT);

        DataBindingGenBaseClassesTask.ConfigAction genBaseClassConfigAction =
                new DataBindingGenBaseClassesTask.ConfigAction(
                        scope, baseClassOutFolder, baseClassLogFolder);

        DataBindingGenBaseClassesTask generateBaseClasses =
                taskFactory.create(genBaseClassConfigAction);
        scope.addTaskOutput(
                DATA_BINDING_BASE_CLASS_SOURCE_OUT,
                baseClassOutFolder,
                generateBaseClasses.getName());
        scope.addTaskOutput(
                InternalArtifactType.DATA_BINDING_BASE_CLASS_LOG_ARTIFACT,
                baseClassLogFolder,
                generateBaseClasses.getName());
        generateBaseClasses.dependsOn(scope.getVariantData().mergeResourcesTask);

        setDataBindingAnnotationProcessorParams(scope);
    }

    private void setDataBindingAnnotationProcessorParams(@NonNull VariantScope scope) {
        BaseVariantData variantData = scope.getVariantData();
        GradleVariantConfiguration variantConfiguration = variantData.getVariantConfiguration();
        JavaCompileOptions javaCompileOptions = variantConfiguration.getJavaCompileOptions();
        AnnotationProcessorOptions processorOptions =
                javaCompileOptions.getAnnotationProcessorOptions();
        if (processorOptions
                instanceof com.android.build.gradle.internal.dsl.AnnotationProcessorOptions) {
            com.android.build.gradle.internal.dsl.AnnotationProcessorOptions ots =
                    (com.android.build.gradle.internal.dsl.AnnotationProcessorOptions)
                            processorOptions;
            // Specify data binding only if another class is specified. Doing so disables discovery
            // so we must explicitly list data binding.
            if (!ots.getClassNames().isEmpty()
                    && !ots.getClassNames().contains(DataBindingBuilder.PROCESSOR_NAME)) {
                ots.className(DataBindingBuilder.PROCESSOR_NAME);
            }
            String packageName = variantConfiguration.getOriginalApplicationId();

            final DataBindingCompilerArgs.Type type;

            final BaseVariantData artifactVariantData;
            final boolean isTest;
            if (variantData.getType() == VariantType.ANDROID_TEST) {
                artifactVariantData = checkNotNull(scope.getTestedVariantData());
                isTest = true;
            } else {
                artifactVariantData = variantData;
                isTest = false;
            }
            if (artifactVariantData.getType() == VariantType.LIBRARY) {
                type = DataBindingCompilerArgs.Type.LIBRARY;
            } else {
                type = DataBindingCompilerArgs.Type.APPLICATION;
            }
            int minApi = variantConfiguration.getMinSdkVersion().getApiLevel();
            File classLogDir =
                    scope.getOutput(InternalArtifactType.DATA_BINDING_BASE_CLASS_LOG_ARTIFACT)
                            .getSingleFile();
            DataBindingCompilerArgs args =
                    DataBindingCompilerArgs.builder()
                            .bundleFolder(scope.getBundleArtifactFolderForDataBinding())
                            .enabledForTests(extension.getDataBinding().isEnabledForTests())
                            .enableDebugLogs(getLogger().isDebugEnabled())
                            .buildFolder(scope.getBuildFolderForDataBindingCompiler())
                            .sdkDir(scope.getGlobalScope().getSdkHandler().getSdkFolder())
                            .xmlOutDir(scope.getLayoutInfoOutputForDataBinding())
                            .classLogDir(classLogDir)
                            .exportClassListTo(
                                    variantData.getType().isExportDataBindingClassList()
                                            ? scope.getGeneratedClassListOutputFileForDataBinding()
                                            : null)
                            .printEncodedErrorLogs(
                                    dataBindingBuilder.getPrintMachineReadableOutput())
                            .modulePackage(packageName)
                            .minApi(minApi)
                            .testVariant(isTest)
                            .type(type)
                            .enableV2(
                                    scope.getGlobalScope()
                                            .getProjectOptions()
                                            .get(BooleanOption.ENABLE_DATA_BINDING_V2))
                            .build();
            ots.arguments(args.toMap());
        } else {
            getLogger().error("Cannot setup data binding for %s because java compiler options"
                    + " is not an instance of AnnotationProcessorOptions", processorOptions);
        }
    }

    /**
     * Creates the final packaging task, and optionally the zipalign task (if the variant is signed)
     *
     * @param fullBuildInfoGeneratorTask task that generates the build-info.xml for full build.
     */
    public void createPackagingTask(
            @NonNull VariantScope variantScope,
            @Nullable BuildInfoWriterTask fullBuildInfoGeneratorTask) {
        ApkVariantData variantData = (ApkVariantData) variantScope.getVariantData();

        boolean signedApk = variantData.isSigned();

        /*
         * PrePackaging step class that will look if the packaging of the main FULL_APK split is
         * necessary when running in InstantRun mode. In InstantRun mode targeting an api 23 or
         * above device, resources are packaged in the main split FULL_APK. However when a warm swap
         * is possible, it is not necessary to produce immediately the new main SPLIT since the
         * runtime use the resources.ap_ file directly. However, as soon as an incompatible change
         * forcing a cold swap is triggered, the main FULL_APK must be rebuilt (even if the
         * resources were changed in a previous build).
         */
        InternalArtifactType manifestType =
                variantScope.getInstantRunBuildContext().isInInstantRunMode()
                        ? INSTANT_RUN_MERGED_MANIFESTS
                        : MERGED_MANIFESTS;

        final boolean splitsArePossible =
                variantScope.getVariantData().getMultiOutputPolicy() == MultiOutputPolicy.SPLITS;

        FileCollection manifests = variantScope.getOutput(manifestType);
        // this is where the final APKs will be located.
        File finalApkLocation = variantScope.getApkLocation();
        // if we are not dealing with possible splits, we can generate in the final folder
        // directly.
        File outputDirectory =
                splitsArePossible
                        ? variantScope.getFullApkPackagesOutputDirectory()
                        : finalApkLocation;

        InternalArtifactType taskOutputType =
                splitsArePossible ? InternalArtifactType.FULL_APK : InternalArtifactType.APK;

        boolean useSeparateApkForResources =
                variantScope.getInstantRunBuildContext().useSeparateApkForResources();

        InternalArtifactType resourceFilesInputType =
                variantScope.useResourceShrinker()
                        ? InternalArtifactType.SHRUNK_PROCESSED_RES
                        : InternalArtifactType.PROCESSED_RES;

        PackageApplication packageApp =
                taskFactory.create(
                        new PackageApplication.StandardConfigAction(
                                variantScope,
                                outputDirectory,
                                useSeparateApkForResources
                                        ? INSTANT_RUN_MAIN_APK_RESOURCES
                                        : resourceFilesInputType,
                                manifests,
                                manifestType,
                                variantScope.getOutputScope(),
                                globalScope.getBuildCache(),
                                taskOutputType));
        variantScope.addTaskOutput(taskOutputType, outputDirectory, packageApp.getName());

        Task packageInstantRunResources = null;

        if (variantScope.getInstantRunBuildContext().isInInstantRunMode()) {
            packageInstantRunResources =
                    taskFactory.create(
                            new InstantRunResourcesApkBuilder.ConfigAction(
                                    resourceFilesInputType,
                                    variantScope.getOutput(resourceFilesInputType),
                                    variantScope));
            packageInstantRunResources.dependsOn(getValidateSigningTask(variantScope));
            // make sure the task run even if none of the files we consume are available,
            // this is necessary so we can clean up output.
            packageApp.dependsOn(packageInstantRunResources);

            if (!useSeparateApkForResources) {
                // in instantRunMode, there is no user configured splits, only one apk.
                packageInstantRunResources =
                        taskFactory.create(
                                new PackageApplication.InstantRunResourcesConfigAction(
                                        variantScope.getInstantRunResourcesFile(),
                                        variantScope,
                                        resourceFilesInputType,
                                        manifests,
                                        INSTANT_RUN_MERGED_MANIFESTS,
                                        globalScope.getBuildCache(),
                                        variantScope.getOutputScope()));
            }

            // Make sure the MAIN artifact is registered after the RESOURCES one.
            packageApp.dependsOn(packageInstantRunResources);
        }

        // Common code for both packaging tasks.
        Consumer<Task> configureResourcesAndAssetsDependencies =
                task -> {
                    task.dependsOn(variantScope.getMergeAssetsTask());
                    if (variantScope.getProcessResourcesTask() != null) {
                        task.dependsOn(variantScope.getProcessResourcesTask());
                    }
                };

        configureResourcesAndAssetsDependencies.accept(packageApp);
        if (packageInstantRunResources != null) {
            configureResourcesAndAssetsDependencies.accept(packageInstantRunResources);
        }

        CoreSigningConfig signingConfig = variantScope.getVariantConfiguration().getSigningConfig();

        //noinspection VariableNotUsedInsideIf - we use the whole packaging scope below.
        if (signingConfig != null) {
            packageApp.dependsOn(getValidateSigningTask(variantScope));
        }

        if (variantScope.getJavacTask() != null) {
            packageApp.dependsOn(variantScope.getJavacTask());
        }
        if (variantData.packageSplitResourcesTask != null) {
            packageApp.dependsOn(variantData.packageSplitResourcesTask);
        }
        if (variantData.packageSplitAbiTask != null) {
            packageApp.dependsOn(variantData.packageSplitAbiTask);
        }
        if (variantScope.getJavacTask() != null) {
            packageApp.dependsOn(variantScope.getJavacTask());
        }
        // FIX ME : Reinstate once ShrinkResourcesTransform is converted.
        //if ( variantOutputScope.getShrinkResourcesTask() != null) {
        //    packageApp.dependsOn( variantOutputScope.getShrinkResourcesTask());
        //}

        variantScope.setPackageApplicationTask(packageApp);
        variantScope.getAssembleTask().dependsOn(packageApp.getName());

        checkState(variantScope.getAssembleTask() != null);
        if (fullBuildInfoGeneratorTask != null) {

            fullBuildInfoGeneratorTask.mustRunAfter(packageApp.getName());
            if (packageInstantRunResources != null) {
                fullBuildInfoGeneratorTask.mustRunAfter(packageInstantRunResources);
            }
            variantScope.getAssembleTask().dependsOn(fullBuildInfoGeneratorTask.getName());
        }

        if (splitsArePossible) {

            CopyOutputs copyOutputsTask =
                    taskFactory.create(
                            new CopyOutputs.ConfigAction(variantScope, finalApkLocation));
            variantScope.addTaskOutput(
                    InternalArtifactType.APK, finalApkLocation, copyOutputsTask.getName());
            variantScope.getAssembleTask().dependsOn(copyOutputsTask);
        }

        // create install task for the variant Data. This will deal with finding the
        // right output if there are more than one.
        // Add a task to install the application package
        if (signedApk) {
            InstallVariantTask installTask =
                    taskFactory.create(new InstallVariantTask.ConfigAction(variantScope));
            installTask.dependsOn(variantScope.getAssembleTask());
        }

        maybeCreateLintVitalTask(variantData);

        // add an uninstall task
        final UninstallTask uninstallTask =
                taskFactory.create(new UninstallTask.ConfigAction(variantScope));

        taskFactory.configure(
                UNINSTALL_ALL, uninstallAll -> uninstallAll.dependsOn(uninstallTask.getName()));
    }

    protected Task getValidateSigningTask(@NonNull VariantScope variantScope) {
        File defaultDebugKeystoreLocation = GradleKeystoreHelper.getDefaultDebugKeystoreLocation();
        ValidateSigningTask.ConfigAction configAction =
                new ValidateSigningTask.ConfigAction(variantScope, defaultDebugKeystoreLocation);

        Task validateSigningTask = taskFactory.findByName(configAction.getName());
        if (validateSigningTask == null) {
            validateSigningTask = taskFactory.create(configAction);
        }
        return validateSigningTask;
    }

    public DefaultTask createAssembleTask(@NonNull final BaseVariantData variantData) {
        return taskFactory.create(
                variantData.getScope().getTaskName("assemble"),
                task -> {
                    variantData.addTask(TaskContainer.TaskKind.ASSEMBLE, task);
                });
    }

    @NonNull
    public DefaultTask createAssembleTask(@NonNull VariantDimensionData dimensionData) {
        final String sourceSetName =
                StringHelper.capitalize(dimensionData.getSourceSet().getName());
        return taskFactory.create(
                StringHelper.appendCapitalized("assemble", sourceSetName),
                assembleTask -> {
                    assembleTask.setDescription("Assembles all " + sourceSetName + " builds.");
                    assembleTask.setGroup(BasePlugin.BUILD_GROUP);
                });
    }

    protected void maybeCreateJavaCodeShrinkerTransform(@NonNull final VariantScope variantScope) {
        CodeShrinker codeShrinker = variantScope.getCodeShrinker();

        if (codeShrinker != null) {
            doCreateJavaCodeShrinkerTransform(
                    variantScope,
                    // No mapping in non-test modules.
                    codeShrinker,
                    null);
        }
    }

    /**
     * Actually creates the minify transform, using the given mapping configuration. The mapping is
     * only used by test-only modules.
     */
    protected final void doCreateJavaCodeShrinkerTransform(
            @NonNull final VariantScope variantScope,
            @NonNull CodeShrinker codeShrinker,
            @Nullable FileCollection mappingFileCollection) {
        Optional<TransformTask> transformTask;
        switch (codeShrinker) {
            case PROGUARD:
                transformTask = createProguardTransform(variantScope, mappingFileCollection);
                break;
            case ANDROID_GRADLE:
                transformTask = createBuiltInShrinkerTransform(variantScope);
                break;
            default:
                throw new AssertionError("Unknown value " + codeShrinker);
        }

        if (variantScope.getPostprocessingFeatures() != null && transformTask.isPresent()) {
            CheckProguardFiles checkFilesTask =
                    taskFactory.create(new CheckProguardFiles.ConfigAction(variantScope));

            transformTask.get().dependsOn(checkFilesTask);
        }
    }

    @NonNull
    private Optional<TransformTask> createBuiltInShrinkerTransform(@NonNull VariantScope scope) {
        BuiltInShrinkerTransform transform = new BuiltInShrinkerTransform(scope);
        applyProguardConfig(transform, scope);

        if (scope.getInstantRunBuildContext().isInInstantRunMode()) {
            //TODO: This is currently overly broad, as finding the actual application class
            //      requires manually parsing the manifest, see
            //      aapt -D (getMainDexListProguardOutputFile)
            transform.keep("class ** extends android.app.Application {*;}");
            transform.keep("class com.android.tools.ir.** {*;}");
        }

        return scope.getTransformManager().addTransform(taskFactory, scope, transform);
    }

    @NonNull
    private Optional<TransformTask> createProguardTransform(
            @NonNull VariantScope variantScope, @Nullable FileCollection mappingFileCollection) {
        if (variantScope.getInstantRunBuildContext().isInInstantRunMode()) {
            logger.warn(
                    "ProGuard is disabled for variant {} because it is not compatible with Instant Run. See "
                            + "http://d.android.com/r/studio-ui/shrink-code-with-ir.html "
                            + "for details on how to enable a code shrinker that's compatible with Instant Run.",
                    variantScope.getVariantConfiguration().getFullName());
            return Optional.empty();
        }

        final BaseVariantData testedVariantData = variantScope.getTestedVariantData();

        ProGuardTransform transform = new ProGuardTransform(variantScope);

        if (testedVariantData != null) {
            // This is an androidTest variant inside an app/library.
            applyProguardDefaultsForTest(transform);

            // All -dontwarn rules for test dependencies should go in here:
            transform.setConfigurationFiles(
                    project.files(
                            TaskInputHelper.bypassFileCallable(
                                    testedVariantData.getScope()::getTestProguardFiles),
                            variantScope.getArtifactFileCollection(
                                    RUNTIME_CLASSPATH, ALL, PROGUARD_RULES)));

            // Register the mapping file which may or may not exists (only exist if obfuscation)
            // is enabled.
            final VariantScope testedScope = testedVariantData.getScope();
            transform.applyTestedMapping(
                    testedScope.hasOutput(APK_MAPPING) ? testedScope.getOutput(APK_MAPPING) : null);
        } else if (isTestedAppObfuscated(variantScope)) {
            // This is a test-only module and the app being tested was obfuscated with ProGuard.
            applyProguardDefaultsForTest(transform);

            // All -dontwarn rules for test dependencies should go in here:
            transform.setConfigurationFiles(
                    project.files(
                            TaskInputHelper.bypassFileCallable(variantScope::getTestProguardFiles),
                            variantScope.getArtifactFileCollection(
                                    RUNTIME_CLASSPATH, ALL, PROGUARD_RULES)));

            transform.applyTestedMapping(mappingFileCollection);
        } else {
            // This is a "normal" variant in an app/library.
            applyProguardConfig(transform, variantScope);

            if (mappingFileCollection != null) {
                transform.applyTestedMapping(mappingFileCollection);
            }
        }

        Optional<TransformTask> task =
                variantScope
                        .getTransformManager()
                        .addTransform(taskFactory, variantScope, transform);

        // FIXME remove once the transform support secondary file as a FileCollection.
        task.ifPresent(
                t -> {
                    variantScope.addTaskOutput(
                            InternalArtifactType.APK_MAPPING,
                            checkNotNull(transform.getMappingFile()),
                            t.getName());

                    if (mappingFileCollection != null) {
                        t.dependsOn(mappingFileCollection);
                    }

                    if (testedVariantData != null) {
                        // We need the mapping file for the app code to exist by the time we run.
                        t.dependsOn(testedVariantData.getScope().getAssembleTask());
                    }
                });

        return task;
    }

    private static void applyProguardDefaultsForTest(ProGuardTransform transform) {
        // Don't remove any code in tested app.
        transform.setActions(new PostprocessingFeatures(false, true, false));

        // We can't call dontobfuscate, since that would make ProGuard ignore the mapping file.
        transform.keep("class * {*;}");
        transform.keep("interface * {*;}");
        transform.keep("enum * {*;}");
        transform.keepattributes();
    }

    /**
     * Checks if {@link ShrinkResourcesTransform} should be added to the build pipeline and either
     * adds it or registers a {@link SyncIssue} with the reason why it was skipped.
     */
    protected void maybeCreateResourcesShrinkerTransform(@NonNull VariantScope scope) {
        if (!scope.useResourceShrinker()) {
            return;
        }

        // if resources are shrink, insert a no-op transform per variant output
        // to transform the res package into a stripped res package
        File shrinkerOutput =
                FileUtils.join(
                        globalScope.getIntermediatesDir(),
                        "res_stripped",
                        scope.getVariantConfiguration().getDirName());

        ShrinkResourcesTransform shrinkResTransform =
                new ShrinkResourcesTransform(
                        scope.getVariantData(),
                        scope.getOutput(InternalArtifactType.PROCESSED_RES),
                        shrinkerOutput,
                        AaptGeneration.fromProjectOptions(projectOptions),
                        logger);

        Optional<TransformTask> shrinkTask =
                scope.getTransformManager().addTransform(taskFactory, scope, shrinkResTransform);

        if (shrinkTask.isPresent()) {
            scope.addTaskOutput(
                    InternalArtifactType.SHRUNK_PROCESSED_RES,
                    shrinkerOutput,
                    shrinkTask.get().getName());
        } else {
            androidBuilder
                    .getIssueReporter()
                    .reportError(
                            Type.GENERIC,
                            "Internal error, could not add the ShrinkResourcesTransform");
        }
    }

    private void applyProguardConfig(
            ProguardConfigurable transform,
            VariantScope scope) {
        GradleVariantConfiguration variantConfig = scope.getVariantConfiguration();

        PostprocessingFeatures postprocessingFeatures = scope.getPostprocessingFeatures();
        if (postprocessingFeatures != null) {
            transform.setActions(postprocessingFeatures);
        }

        Supplier<Collection<File>> proguardConfigFiles =
                () -> {
                    List<File> proguardFiles = new ArrayList<>(scope.getProguardFiles());

                    // Use the first output when looking for the proguard rule output of
                    // the aapt task. The different outputs are not different in a way that
                    // makes this rule file different per output.
                    proguardFiles.add(scope.getProcessAndroidResourcesProguardOutputFile());
                    return proguardFiles;
                };

        transform.setConfigurationFiles(
                project.files(
                        TaskInputHelper.bypassFileCallable(proguardConfigFiles),
                        scope.getArtifactFileCollection(RUNTIME_CLASSPATH, ALL, PROGUARD_RULES)));

        if (scope.getVariantData().getType() == LIBRARY) {
            transform.keep("class **.R");
            transform.keep("class **.R$*");
        }

        if (variantConfig.isTestCoverageEnabled()) {
            // when collecting coverage, don't remove the JaCoCo runtime
            transform.keep("class com.vladium.** {*;}");
            transform.keep("class org.jacoco.** {*;}");
            transform.keep("interface org.jacoco.** {*;}");
            transform.dontwarn("org.jacoco.**");
        }
    }

    public void createReportTasks(final List<VariantScope> variantScopes) {
        taskFactory.create(
                "androidDependencies",
                DependencyReportTask.class,
                task -> {
                    task.setDescription("Displays the Android dependencies of the project.");
                    task.setVariants(variantScopes);
                    task.setGroup(ANDROID_GROUP);
                });

        taskFactory.create(
                "signingReport",
                SigningReportTask.class,
                task -> {
                    task.setDescription("Displays the signing info for each variant.");
                    task.setVariants(variantScopes);
                    task.setGroup(ANDROID_GROUP);
                });
    }

    public void createAnchorTasks(@NonNull VariantScope scope) {
        createPreBuildTasks(scope);

        // also create sourceGenTask
        final BaseVariantData variantData = scope.getVariantData();
        scope.setSourceGenTask(
                taskFactory.create(
                        scope.getTaskName("generate", "Sources"),
                        Task.class,
                        task -> {
                            variantData.sourceGenTask = task;
                            task.dependsOn(PrepareLintJar.NAME);
                        }));
        // and resGenTask
        scope.setResourceGenTask(
                taskFactory.create(
                        scope.getTaskName("generate", "Resources"),
                        Task.class,
                        task -> {
                            variantData.resourceGenTask = task;
                        }));

        scope.setAssetGenTask(
                taskFactory.create(
                        scope.getTaskName("generate", "Assets"),
                        Task.class,
                        task -> {
                            variantData.assetGenTask = task;
                        }));

        if (!variantData.getType().isForTesting()
                && variantData.getVariantConfiguration().getBuildType().isTestCoverageEnabled()) {
            scope.setCoverageReportTask(
                    taskFactory.create(
                            scope.getTaskName("create", "CoverageReport"),
                            Task.class,
                            task -> {
                                task.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
                                task.setDescription(
                                        String.format(
                                                "Creates test coverage reports for the %s variant.",
                                                variantData.getName()));
                            }));
        }

        // and compile task
        createCompileAnchorTask(scope);
    }

    protected DefaultTask createVariantPreBuildTask(@NonNull VariantScope scope) {
        // default pre-built task.
        return createDefaultPreBuildTask(scope);
    }

    protected DefaultTask createDefaultPreBuildTask(@NonNull VariantScope scope) {
        return taskFactory.create(
                scope.getTaskName("pre", "Build"),
                task -> scope.getVariantData().preBuildTask = task);
    }

    private void createPreBuildTasks(@NonNull VariantScope scope) {
        scope.setPreBuildTask(createVariantPreBuildTask(scope));

        scope.getPreBuildTask().dependsOn(MAIN_PREBUILD);

        if (runJavaCodeShrinker(scope)) {
            scope.getPreBuildTask().dependsOn(EXTRACT_PROGUARD_FILES);
        }
    }

    private void createCompileAnchorTask(@NonNull final VariantScope scope) {
        final BaseVariantData variantData = scope.getVariantData();
        scope.setCompileTask(
                taskFactory.create(
                        new TaskConfigAction<Task>() {
                            @NonNull
                            @Override
                            public String getName() {
                                return scope.getTaskName("compile", "Sources");
                            }

                            @NonNull
                            @Override
                            public Class<Task> getType() {
                                return Task.class;
                            }

                            @Override
                            public void execute(@NonNull Task task) {
                                variantData.compileTask = task;
                                variantData.compileTask.setGroup(BUILD_GROUP);
                            }
                        }));
        scope.getAssembleTask().dependsOn(scope.getCompileTask());
    }

    public void createCheckManifestTask(@NonNull VariantScope scope) {
        scope.setCheckManifestTask(taskFactory.create(getCheckManifestConfig(scope)));
        scope.getCheckManifestTask().dependsOn(scope.getPreBuildTask());
        // Does
    }

    protected CheckManifest.ConfigAction getCheckManifestConfig(@NonNull VariantScope scope) {
        return new CheckManifest.ConfigAction(scope, false);
    }

    @NonNull
    protected Logger getLogger() {
        return logger;
    }

    public void addDataBindingDependenciesIfNecessary(
            DataBindingOptions options, List<VariantScope> variantScopes) {
        if (!options.isEnabled()) {
            return;
        }

        String version = MoreObjects.firstNonNull(options.getVersion(),
                dataBindingBuilder.getCompilerVersion());
        project.getDependencies()
                .add(
                        "api",
                        SdkConstants.DATA_BINDING_BASELIB_ARTIFACT
                                + ":"
                                + dataBindingBuilder.getBaseLibraryVersion(version));

        // TODO load config name from source sets
        project.getDependencies()
                .add(
                        "annotationProcessor",
                        SdkConstants.DATA_BINDING_ANNOTATION_PROCESSOR_ARTIFACT + ":" + version);
        if (options.isEnabledForTests() || this instanceof LibraryTaskManager) {
            project.getDependencies().add("androidTestAnnotationProcessor",
                    SdkConstants.DATA_BINDING_ANNOTATION_PROCESSOR_ARTIFACT + ":" +
                            version);
        }
        if (options.getAddDefaultAdapters()) {
            project.getDependencies()
                    .add(
                            "api",
                            SdkConstants.DATA_BINDING_LIB_ARTIFACT
                                    + ":"
                                    + dataBindingBuilder.getLibraryVersion(version));
            project.getDependencies()
                    .add(
                            "api",
                            SdkConstants.DATA_BINDING_ADAPTER_LIB_ARTIFACT
                                    + ":"
                                    + dataBindingBuilder.getBaseAdaptersVersion(version));
        }
        project.getPluginManager()
                .withPlugin(
                        "org.jetbrains.kotlin.kapt",
                        appliedPlugin -> {
                            configureKotlinKaptTasksForDataBinding(project, variantScopes, version);
                        });
    }

    private void configureKotlinKaptTasksForDataBinding(
            Project project, List<VariantScope> variantScopes, String version) {
        DependencySet kaptDeps = project.getConfigurations().getByName("kapt").getAllDependencies();
        kaptDeps.forEach((Dependency dependency) -> {
            // if it is a data binding compiler dependency w/ a different version, report error
            if (Objects.equals(dependency.getGroup() + ":" + dependency.getName(),
                    SdkConstants.DATA_BINDING_ANNOTATION_PROCESSOR_ARTIFACT)
                    && !Objects.equals(dependency.getVersion(), version)) {
                String depString = dependency.getGroup()
                        + ":" + dependency.getName()
                        + ":" + dependency.getVersion();
                androidBuilder.getIssueReporter().reportError(Type.GENERIC,
                        "Data Binding annotation processor version needs to match the" +
                                " Android Gradle Plugin version. You can remove the kapt" +
                                " dependency " + depString +
                                " and Android Gradle Plugin will inject" +
                                " the right version.");
            }
        });
        project.getDependencies()
                .add(
                        "kapt",
                        SdkConstants.DATA_BINDING_ANNOTATION_PROCESSOR_ARTIFACT + ":" + version);
        Class<? extends Task> kaptTaskClass = null;
        try {
            //noinspection unchecked
            kaptTaskClass =
                    (Class<? extends Task>)
                            Class.forName("org.jetbrains.kotlin.gradle.internal.KaptTask");
        } catch (ClassNotFoundException e) {
            logger.error(
                    "Kotlin plugin is applied to the project "
                            + project.getPath()
                            + " but we cannot find the KaptTask. Make sure you apply the"
                            + " kotlin-kapt plugin because it is necessary to use kotlin"
                            + " with data binding.");
        }
        if (kaptTaskClass == null) {
            return;
        }
        // create a map from kapt task name to variant scope
        Map<String, VariantScope> kaptTaskLookup =
                variantScopes
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        variantScope ->
                                                variantScope
                                                        .getVariantData()
                                                        .getTaskName("kapt", "Kotlin"),
                                        variantScope -> variantScope));
        project.getTasks()
                .withType(
                        kaptTaskClass,
                        (Action<Task>)
                                kaptTask -> {
                                    // find matching scope.
                                    VariantScope matchingScope =
                                            kaptTaskLookup.get(kaptTask.getName());
                                    if (matchingScope != null) {
                                        configureKaptTaskInScope(matchingScope, kaptTask);
                                    }
                                });
    }

    private static void configureKaptTaskInScope(VariantScope scope, Task kaptTask) {
        if (scope.hasOutput(DATA_BINDING_DEPENDENCY_ARTIFACTS)) {
            // if data binding is enabled and this variant has merged dependency artifacts, then
            // make the compilation task depend on them. (test variants don't do the merge so they
            // could not have the artifacts)
            kaptTask.getInputs()
                    .files(scope.getOutput(DATA_BINDING_DEPENDENCY_ARTIFACTS))
                    .withPathSensitivity(PathSensitivity.RELATIVE)
                    .withPropertyName("dataBindingDependencyArtifacts");
        }
        if (scope.hasOutput(InternalArtifactType.DATA_BINDING_BASE_CLASS_LOG_ARTIFACT)) {
            kaptTask.getInputs()
                    .files(
                            scope.getOutput(
                                    InternalArtifactType.DATA_BINDING_BASE_CLASS_LOG_ARTIFACT))
                    .withPathSensitivity(PathSensitivity.RELATIVE)
                    .withPropertyName("dataBindingClassLogDir");
        }
        // the data binding artifact is created by the annotation processor, so we register this
        // task output (which also publishes it) with javac as the generating task.
        kaptTask.getOutputs()
                .files(scope.getBundleArtifactFolderForDataBinding())
                .withPropertyName("dataBindingArtifactOutputDir");
        if (!scope.hasOutput(InternalArtifactType.DATA_BINDING_ARTIFACT)) {
            scope.addTaskOutput(
                    InternalArtifactType.DATA_BINDING_ARTIFACT,
                    scope.getBundleArtifactFolderForDataBinding(),
                    kaptTask.getName());
        }
    }

    protected void configureTestData(AbstractTestDataImpl testData) {
        testData.setAnimationsDisabled(extension.getTestOptions().getAnimationsDisabled());
    }
}
