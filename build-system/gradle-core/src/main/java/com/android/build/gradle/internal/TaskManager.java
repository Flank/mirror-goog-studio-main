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
import static com.android.SdkConstants.FN_SPLIT_LIST;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.EXTERNAL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.MODULE;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.APK_CLASSES;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.CLASSES;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.DATA_BINDING;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.JAR;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.JAVA_RES;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.JNI;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.PROGUARD_RULES;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.ANNOTATION_PROCESSOR;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH;
import static com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType.JAVAC;
import static com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType.MERGED_ASSETS;
import static com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType.MOCKABLE_JAR;
import static com.android.builder.core.BuilderConstants.CONNECTED;
import static com.android.builder.core.BuilderConstants.DEVICE;
import static com.android.builder.core.VariantType.ANDROID_TEST;
import static com.android.builder.core.VariantType.LIBRARY;
import static com.android.builder.model.AndroidProject.FD_INTERMEDIATES;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verifyNotNull;

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
import com.android.build.gradle.AndroidGradleOptions;
import com.android.build.gradle.ProguardFiles;
import com.android.build.gradle.internal.aapt.AaptGeneration;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.coverage.JacocoPlugin;
import com.android.build.gradle.internal.coverage.JacocoReportTask;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.dsl.AbiSplitOptions;
import com.android.build.gradle.internal.dsl.AnnotationProcessorOptions;
import com.android.build.gradle.internal.dsl.CoreAnnotationProcessorOptions;
import com.android.build.gradle.internal.dsl.CoreJavaCompileOptions;
import com.android.build.gradle.internal.dsl.CoreSigningConfig;
import com.android.build.gradle.internal.dsl.PackagingOptions;
import com.android.build.gradle.internal.incremental.BuildInfoLoaderTask;
import com.android.build.gradle.internal.incremental.BuildInfoWriterTask;
import com.android.build.gradle.internal.incremental.InstantRunAnchorTaskConfigAction;
import com.android.build.gradle.internal.incremental.InstantRunPatchingPolicy;
import com.android.build.gradle.internal.model.CoreExternalNativeBuild;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.build.gradle.internal.pipeline.ExtendedContentType;
import com.android.build.gradle.internal.pipeline.OriginalStream;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.pipeline.TransformTask;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.scope.AndroidTask;
import com.android.build.gradle.internal.scope.AndroidTaskRegistry;
import com.android.build.gradle.internal.scope.BuildOutputs;
import com.android.build.gradle.internal.scope.CodeShrinker;
import com.android.build.gradle.internal.scope.DefaultGradlePackagingScope;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.PackagingScope;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.TaskOutputHolder;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.scope.VariantScope.Java8LangSupport;
import com.android.build.gradle.internal.tasks.AndroidReportTask;
import com.android.build.gradle.internal.tasks.CheckManifest;
import com.android.build.gradle.internal.tasks.DependencyReportTask;
import com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask;
import com.android.build.gradle.internal.tasks.ExtractJava8LangSupportJar;
import com.android.build.gradle.internal.tasks.ExtractProguardFiles;
import com.android.build.gradle.internal.tasks.FileSupplier;
import com.android.build.gradle.internal.tasks.GenerateApkDataTask;
import com.android.build.gradle.internal.tasks.InstallVariantTask;
import com.android.build.gradle.internal.tasks.JackJacocoReportTask;
import com.android.build.gradle.internal.tasks.LintCompile;
import com.android.build.gradle.internal.tasks.MockableAndroidJarTask;
import com.android.build.gradle.internal.tasks.SigningReportTask;
import com.android.build.gradle.internal.tasks.SourceSetsTask;
import com.android.build.gradle.internal.tasks.TaskInputHelper;
import com.android.build.gradle.internal.tasks.TestServerTask;
import com.android.build.gradle.internal.tasks.UninstallTask;
import com.android.build.gradle.internal.tasks.ValidateSigningTask;
import com.android.build.gradle.internal.tasks.databinding.DataBindingExportBuildInfoTask;
import com.android.build.gradle.internal.tasks.databinding.DataBindingMergeArtifactsTransform;
import com.android.build.gradle.internal.tasks.databinding.DataBindingProcessLayoutsTask;
import com.android.build.gradle.internal.test.AbstractTestDataImpl;
import com.android.build.gradle.internal.test.TestDataImpl;
import com.android.build.gradle.internal.transforms.BuiltInShrinkerTransform;
import com.android.build.gradle.internal.transforms.CustomClassTransform;
import com.android.build.gradle.internal.transforms.DesugarTransform;
import com.android.build.gradle.internal.transforms.DexArchiveBuilderTransform;
import com.android.build.gradle.internal.transforms.DexMergerTransform;
import com.android.build.gradle.internal.transforms.DexTransform;
import com.android.build.gradle.internal.transforms.ExtractJarsTransform;
import com.android.build.gradle.internal.transforms.JackCompileTransform;
import com.android.build.gradle.internal.transforms.JackGenerateDexTransform;
import com.android.build.gradle.internal.transforms.JackOptionsUtils;
import com.android.build.gradle.internal.transforms.JackPreDexTransform;
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
import com.android.build.gradle.internal.variant.SplitHandlingPolicy;
import com.android.build.gradle.internal.variant.TaskContainer;
import com.android.build.gradle.internal.variant.TestVariantData;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.options.StringOption;
import com.android.build.gradle.tasks.AidlCompile;
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
import com.android.build.gradle.tasks.JavaPreCompileTask;
import com.android.build.gradle.tasks.Lint;
import com.android.build.gradle.tasks.ManifestProcessorTask;
import com.android.build.gradle.tasks.MergeManifests;
import com.android.build.gradle.tasks.MergeResources;
import com.android.build.gradle.tasks.MergeSourceSetFolders;
import com.android.build.gradle.tasks.NdkCompile;
import com.android.build.gradle.tasks.PackageApplication;
import com.android.build.gradle.tasks.PackageSplitAbi;
import com.android.build.gradle.tasks.PackageSplitRes;
import com.android.build.gradle.tasks.PreColdSwapTask;
import com.android.build.gradle.tasks.ProcessAndroidResources;
import com.android.build.gradle.tasks.ProcessManifest;
import com.android.build.gradle.tasks.ProcessTestManifest;
import com.android.build.gradle.tasks.RenderscriptCompile;
import com.android.build.gradle.tasks.ShaderCompile;
import com.android.build.gradle.tasks.SplitsDiscovery;
import com.android.build.gradle.tasks.factory.AndroidUnitTest;
import com.android.build.gradle.tasks.factory.IncrementalSafeguard;
import com.android.build.gradle.tasks.factory.JacocoAgentConfigAction;
import com.android.build.gradle.tasks.factory.JavaCompileConfigAction;
import com.android.build.gradle.tasks.factory.ProcessJavaResConfigAction;
import com.android.build.gradle.tasks.factory.TestServerTaskConfigAction;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.DefaultApiVersion;
import com.android.builder.core.DefaultDexOptions;
import com.android.builder.core.VariantConfiguration;
import com.android.builder.core.VariantType;
import com.android.builder.dependency.level2.AndroidDependency;
import com.android.builder.dexing.DexingMode;
import com.android.builder.model.ApiVersion;
import com.android.builder.model.DataBindingOptions;
import com.android.builder.model.OptionalCompilationStep;
import com.android.builder.model.SyncIssue;
import com.android.builder.profile.Recorder;
import com.android.builder.testing.ConnectedDeviceProvider;
import com.android.builder.testing.api.DeviceProvider;
import com.android.builder.testing.api.TestServer;
import com.android.builder.utils.FileCache;
import com.android.ide.common.build.ApkData;
import com.android.manifmerger.ManifestMerger2;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.SdkVersionInfo;
import com.android.utils.FileUtils;
import com.android.utils.StringHelper;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import groovy.lang.Closure;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
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
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/**
 * Manages tasks creation.
 */
public abstract class TaskManager {

    public static final String DEFAULT_PROGUARD_CONFIG_FILE = "proguard-android.txt";
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
    public static final String LINT_COMPILE = "compileLint";
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
    @NonNull protected final AndroidTaskRegistry androidTasks;
    @NonNull private final DependencyManager dependencyManager;
    @NonNull private final Logger logger;
    @Nullable private final FileCache buildCache;

    // Tasks. TODO: remove the mutable state from here.
    private AndroidTask<Copy> jacocoAgentTask;
    public AndroidTask<MockableAndroidJarTask> createMockableJar;

    public TaskManager(
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
        this.globalScope = globalScope;
        this.project = project;
        this.projectOptions = projectOptions;
        this.androidBuilder = androidBuilder;
        this.dataBindingBuilder = dataBindingBuilder;
        this.sdkHandler = sdkHandler;
        this.extension = extension;
        this.toolingRegistry = toolingRegistry;
        this.dependencyManager = dependencyManager;
        this.recorder = recorder;
        this.logger = Logging.getLogger(this.getClass());
        this.androidTasks = new AndroidTaskRegistry();

        // It's too early to materialize the project-level cache, we'll need to get it from
        // globalScope later on.
        this.buildCache = globalScope.getBuildCache();
    }

    public boolean isComponentModelPlugin() {
        return false;
    }

    public DataBindingBuilder getDataBindingBuilder() {
        return dataBindingBuilder;
    }

    public DependencyManager getDependencyManager() {
        return dependencyManager;
    }

    /** Creates the tasks for a given BaseVariantData. */
    public abstract void createTasksForVariantScope(
            @NonNull TaskFactory tasks, @NonNull VariantScope variantScope);

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
        scope.setNdkObjFolder(objFolder);
        for (Abi abi : NdkHandler.getAbiList()) {
            scope.addNdkDebuggableLibraryFolders(abi,
                    new File(objFolder, "local/" + abi.getName()));
        }

    }

    public void resolveDependencies(
            @NonNull VariantDependencies variantDeps,
            @Nullable String testedProjectPath) {
        Set<AndroidDependency> libsToExplode =
                dependencyManager.resolveDependencies(
                        variantDeps,
                        testedProjectPath,
                        buildCache,
                        globalScope.getProjectLevelCache());
        dependencyManager.processLibraries(libsToExplode, buildCache);
    }

    /**
     * Create tasks before the evaluation (on plugin apply). This is useful for tasks that
     * could be referenced by custom build logic.
     */
    public void createTasksBeforeEvaluate(@NonNull TaskFactory tasks) {
        androidTasks.create(tasks, UNINSTALL_ALL, uninstallAllTask -> {
            uninstallAllTask.setDescription("Uninstall all applications.");
            uninstallAllTask.setGroup(INSTALL_GROUP);
        });

        androidTasks.create(tasks, DEVICE_CHECK, deviceCheckTask -> {
            deviceCheckTask.setDescription(
                    "Runs all device checks using Device Providers and Test Servers.");
            deviceCheckTask.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
        });

        androidTasks.create(tasks, CONNECTED_CHECK, connectedCheckTask -> {
            connectedCheckTask.setDescription(
                    "Runs all device checks on currently connected devices.");
            connectedCheckTask.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
        });

        androidTasks.create(tasks, MAIN_PREBUILD, task -> {});

        AndroidTask<ExtractProguardFiles> extractProguardFiles =
                androidTasks.create(
                        tasks, EXTRACT_PROGUARD_FILES, ExtractProguardFiles.class, task -> {});
        // Make sure MAIN_PREBUILD runs first:
        extractProguardFiles.dependsOn(tasks, MAIN_PREBUILD);

        androidTasks.create(tasks, new SourceSetsTask.ConfigAction(extension));

        androidTasks.create(
                tasks,
                ASSEMBLE_ANDROID_TEST,
                assembleAndroidTestTask -> {
                    assembleAndroidTestTask.setGroup(BasePlugin.BUILD_GROUP);
                    assembleAndroidTestTask.setDescription("Assembles all the Test applications.");
                });

        AndroidTask<Lint> globalLintTask = androidTasks.create(tasks,
                new Lint.GlobalConfigAction(globalScope));
        globalLintTask.dependsOn(tasks, "assemble");

        tasks.named(JavaBasePlugin.CHECK_TASK_NAME, it -> it.dependsOn(LINT));

        androidTasks.create(tasks, new LintCompile.ConfigAction(globalScope));

        if (buildCache != null) {
            androidTasks.create(tasks, new CleanBuildCache.ConfigAction(globalScope));
        }

        // for testing only.
        androidTasks.create(tasks, new TaskConfigAction<ConfigAttrTask>() {
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

        androidTasks.create(tasks, new TaskConfigAction<ConfigAttrTask>() {
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

        ExtractJava8LangSupportJar.ConfigAction extractConfig =
                new ExtractJava8LangSupportJar.ConfigAction(
                        globalScope.getJava8LangSupportJar(), ExtractJava8LangSupportJar.TASK_NAME);
        androidTasks.create(tasks, extractConfig);
        globalScope.getJava8LangSupportJar().builtBy(ExtractJava8LangSupportJar.TASK_NAME);
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

    public void createMockableJarTask(TaskFactory tasks) {
        File mockableJar = globalScope.getMockableAndroidJarFile();
        createMockableJar = androidTasks
                .create(tasks, new MockableAndroidJarTask.ConfigAction(globalScope, mockableJar));

        globalScope.addTaskOutput(MOCKABLE_JAR, mockableJar, createMockableJar.getName());
    }

    protected void createDependencyStreams(
            @NonNull TaskFactory tasks,
            @NonNull final VariantScope variantScope) {
        BaseVariantData variantData = variantScope.getVariantData();

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

        if (variantScope.isJackEnabled()) {
            // add project sources for the Jack pipeline
            Callable<List<File>> javaSources =
                    () -> {
                        List<File> sources = Lists.newArrayList();
                        sources.addAll(
                                variantScope
                                        .getVariantData()
                                        .getUserJavaSources()
                                        .stream()
                                        .map(FileCollection::getFiles)
                                        .flatMap(Set::stream)
                                        .collect(Collectors.toList()));
                        sources.addAll(
                                variantScope
                                        .getVariantData()
                                        .getGeneratedJavaSources()
                                        .stream()
                                        .map(FileCollection::getFiles)
                                        .flatMap(Set::stream)
                                        .collect(Collectors.toList()));

                        return sources;
                    };

            FileCollection sourcesCollection = project.files(javaSources).builtBy(
                    variantData.getVariantDependency().getCompileClasspath());

            transformManager.addStream(
                    OriginalStream.builder(project, "project-java-sources")
                            .addContentTypes(ExtendedContentType.JAVA_SOURCES)
                            .addScope(Scope.PROJECT)
                            .setFileCollection(sourcesCollection)
                            .build());
        }

        // data binding related artifacts for external libs
        if (extension.getDataBinding().isEnabled()) {
            transformManager.addStream(
                    OriginalStream.builder(project, "ext-libs-data-binding")
                            .addContentTypes(TransformManager.DATA_BINDING_ARTIFACT)
                            .addScope(Scope.SUB_PROJECTS)
                            .setArtifactCollection(
                                    variantScope.getArtifactCollection(
                                            COMPILE_CLASSPATH, MODULE, DATA_BINDING))
                            .build());
            transformManager.addStream(
                    OriginalStream.builder(project, "ext-libs-databinding")
                            .addContentTypes(TransformManager.DATA_BINDING_ARTIFACT)
                            .addScope(Scope.EXTERNAL_LIBRARIES)
                            .setArtifactCollection(
                                    variantScope.getArtifactCollection(
                                            COMPILE_CLASSPATH, EXTERNAL, DATA_BINDING))
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

            if (!variantScope.isJackEnabled()) {
                // create two streams of different types.
                transformManager.addStream(
                        OriginalStream.builder(project, "tested-code-javac-out")
                                .addContentTypes(DefaultContentType.CLASSES)
                                .addScope(Scope.TESTED_CODE)
                                .setFolders(
                                        () ->
                                                ImmutableList.of(
                                                        testedVariantScope.getJavaOutputDir()))
                                .setDependency(testedVariantScope.getJavacTask().getName())
                                .build());
            } else {
                FileCollection testedAppJack = testedVariantData.getJackCompilationOutput();
                checkNotNull(testedAppJack, "Tested variant has no jack output specified.");
                transformManager.addStream(
                        OriginalStream.builder(project, "tested-code-jack-out")
                                .addContentType(ExtendedContentType.JACK)
                                .addScope(Scope.TESTED_CODE)
                                .setFileCollection(testedAppJack)
                                .build());
            }

            transformManager.addStream(
                    OriginalStream.builder(project, "tested-code-deps")
                            .addContentTypes(DefaultContentType.CLASSES)
                            .addScope(Scope.TESTED_CODE)
                            .setArtifactCollection(
                                    testedVariantScope.getArtifactCollection(
                                            RUNTIME_CLASSPATH, ALL, CLASSES))
                            .build());
        }

        handleJacocoDependencies(tasks, variantScope);
    }

    public void createMergeApkManifestsTask(
            @NonNull TaskFactory tasks, @NonNull VariantScope variantScope) {
        AndroidArtifactVariantData androidArtifactVariantData =
                (AndroidArtifactVariantData) variantScope.getVariantData();
        Set<String> screenSizes = androidArtifactVariantData.getCompatibleScreens();

        AndroidTask<CompatibleScreensManifest> csmTask =
                androidTasks.create(
                        tasks,
                        new CompatibleScreensManifest.ConfigAction(variantScope, screenSizes));
        variantScope.addTaskOutput(
                TaskOutputHolder.TaskOutputType.COMPATIBLE_SCREEN_MANIFEST,
                variantScope.getCompatibleScreensManifestDirectory(),
                csmTask.getName());

        ImmutableList.Builder<ManifestMerger2.Invoker.Feature> optionalFeatures =
                ImmutableList.builder();

        if (variantScope.isTestOnly()) {
            optionalFeatures.add(ManifestMerger2.Invoker.Feature.TEST_ONLY);
        }

        if (AndroidGradleOptions.getAdvancedProfilingTransforms(project).length > 0
                && variantScope.getVariantConfiguration().getBuildType().isDebuggable()) {
            optionalFeatures.add(ManifestMerger2.Invoker.Feature.ADVANCED_PROFILING);
        }

        AndroidTask<? extends ManifestProcessorTask> processManifestTask =
                createMergeManifestTask(tasks, variantScope, optionalFeatures);

        variantScope.addTaskOutput(
                TaskOutputHolder.TaskOutputType.MERGED_MANIFESTS,
                variantScope.getManifestOutputDirectory(),
                processManifestTask.getName());

        variantScope.publishIntermediateArtifact(
                BuildOutputs.getMetadataFile(variantScope.getManifestOutputDirectory()),
                processManifestTask.getName(),
                AndroidArtifacts.ArtifactType.MANIFEST_METADATA);

        // TODO: use FileCollection
        variantScope.setManifestProcessorTask(processManifestTask);

        processManifestTask.dependsOn(tasks, variantScope.getCheckManifestTask());

        if (variantScope.getMicroApkTask() != null) {
            processManifestTask.dependsOn(tasks, variantScope.getMicroApkTask());
        }
    }

    /** Creates the merge manifests task. */
    @NonNull
    protected AndroidTask<? extends ManifestProcessorTask> createMergeManifestTask(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope variantScope,
            @NonNull ImmutableList.Builder<ManifestMerger2.Invoker.Feature> optionalFeatures) {
        if (getIncrementalMode(variantScope.getVariantConfiguration()) != IncrementalMode.NONE) {
            optionalFeatures.add(ManifestMerger2.Invoker.Feature.INSTANT_RUN_REPLACEMENT);
        }

        AndroidTask<MergeManifests> mergeManifestsAndroidTask =
                androidTasks.create(
                        tasks,
                        new MergeManifests.ConfigAction(variantScope, optionalFeatures.build()));

        variantScope.addTaskOutput(
                VariantScope.TaskOutputType.INSTANT_RUN_MERGED_MANIFESTS,
                variantScope.getInstantRunManifestOutputDirectory(),
                mergeManifestsAndroidTask.getName());

        return mergeManifestsAndroidTask;
    }

    public AndroidTask<ProcessManifest> createMergeLibManifestsTask(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope scope) {

        AndroidTask<ProcessManifest> processManifest = androidTasks.create(tasks,
                new ProcessManifest.ConfigAction(scope));

        scope.addTaskOutput(
                TaskOutputHolder.TaskOutputType.MERGED_MANIFESTS,
                scope.getManifestOutputDirectory(),
                processManifest.getName());

        scope.addTaskOutput(
                TaskOutputHolder.TaskOutputType.AAPT_FRIENDLY_MERGED_MANIFESTS,
                scope.getAaptFriendlyManifestOutputDirectory(),
                processManifest.getName());

        processManifest.dependsOn(tasks, scope.getCheckManifestTask());

        scope.setManifestProcessorTask(processManifest);

        return processManifest;
    }

    protected void createProcessTestManifestTask(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope scope,
            @NonNull VariantScope testedScope) {

        AndroidTask<ProcessTestManifest> processTestManifestTask =
                androidTasks.create(
                        tasks,
                        new ProcessTestManifest.ConfigAction(
                                scope,
                                testedScope.getOutputs(
                                        TaskOutputHolder.TaskOutputType.MERGED_MANIFESTS)));

        scope.addTaskOutput(
                TaskOutputHolder.TaskOutputType.MERGED_MANIFESTS,
                scope.getManifestOutputDirectory(),
                processTestManifestTask.getName());

        processTestManifestTask.optionalDependsOn(tasks, scope.getCheckManifestTask());

        scope.setManifestProcessorTask(processTestManifestTask);
    }

    public void createRenderscriptTask(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope scope) {
        scope.setRenderscriptCompileTask(
                androidTasks.create(tasks, new RenderscriptCompile.ConfigAction(scope)));

        GradleVariantConfiguration config = scope.getVariantConfiguration();

        if (config.getType().isForTesting()) {
            scope.getRenderscriptCompileTask().dependsOn(tasks, scope.getManifestProcessorTask());
        } else {
            scope.getRenderscriptCompileTask().dependsOn(tasks, scope.getPreBuildTask());
        }

        scope.getResourceGenTask().dependsOn(tasks, scope.getRenderscriptCompileTask());
        // only put this dependency if rs will generate Java code
        if (!config.getRenderscriptNdkModeEnabled()) {
            scope.getSourceGenTask().dependsOn(tasks, scope.getRenderscriptCompileTask());
        }

    }

    public AndroidTask<MergeResources> createMergeResourcesTask(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope scope) {
        return createMergeResourcesTask(tasks, scope, true /*processResources*/);
    }

    public AndroidTask<MergeResources> createMergeResourcesTask(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope scope,
            boolean processResources) {
        return basicCreateMergeResourcesTask(
                tasks,
                scope,
                MergeType.MERGE,
                null /*outputLocation*/,
                true /*includeDependencies*/,
                processResources);
    }

    /** Defines the merge type for {@link #basicCreateMergeResourcesTask} */
    public enum MergeType {
        /**
         * Merge all resources with all the dependencies resources.
         */
        MERGE {
            @Override
            public VariantScope.TaskOutputType getOutputType() {
                return VariantScope.TaskOutputType.MERGED_RES;
            }
        },
        /**
         * Merge all resources without the dependencies resources for an aar.
         */
        PACKAGE {
            @Override
            public VariantScope.TaskOutputType getOutputType() {
                return VariantScope.TaskOutputType.PACKAGED_RES;
            }
        };

        public abstract VariantScope.TaskOutputType getOutputType();
    }

    public AndroidTask<MergeResources> basicCreateMergeResourcesTask(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope scope,
            @NonNull MergeType mergeType,
            @Nullable File outputLocation,
            final boolean includeDependencies,
            final boolean processResources) {

        File mergedOutputDir = MoreObjects
                .firstNonNull(outputLocation, scope.getDefaultMergeResourcesOutputDir());

        String taskNamePrefix = mergeType.name().toLowerCase(Locale.ENGLISH);

        AndroidTask<MergeResources> mergeResourcesTask = androidTasks.create(tasks,
                new MergeResources.ConfigAction(
                        scope,
                        taskNamePrefix,
                        mergedOutputDir,
                        includeDependencies,
                        processResources));

        scope.addTaskOutput(
                mergeType.getOutputType(), mergedOutputDir, mergeResourcesTask.getName());

        mergeResourcesTask.dependsOn(
                tasks,
                scope.getResourceGenTask());
        scope.setMergeResourcesTask(mergeResourcesTask);
        scope.setResourceOutputDir(mergedOutputDir);
        scope.setMergeResourceOutputDir(outputLocation);
        return scope.getMergeResourcesTask();
    }

    public AndroidTask<MergeSourceSetFolders> createMergeAssetsTask(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope scope,
            @Nullable BiConsumer<AndroidTask<MergeSourceSetFolders>, File> consumer) {
        final GradleVariantConfiguration variantConfiguration = scope.getVariantConfiguration();
        File outputDir =
                variantConfiguration.isBundled()
                        ? new File(scope.getBaseBundleDir(), FD_ASSETS)
                        : FileUtils.join(
                                globalScope.getIntermediatesDir(),
                                FD_ASSETS,
                                variantConfiguration.getDirName());

        AndroidTask<MergeSourceSetFolders> mergeAssetsTask =
                androidTasks.create(
                        tasks, new MergeSourceSetFolders.MergeAssetConfigAction(scope, outputDir));

        // register the output
        scope.addTaskOutput(MERGED_ASSETS, outputDir, mergeAssetsTask.getName());

        if (consumer != null) {
            consumer.accept(mergeAssetsTask, outputDir);
        }

        mergeAssetsTask.dependsOn(tasks,
                scope.getAssetGenTask());
        scope.setMergeAssetsTask(mergeAssetsTask);

        return mergeAssetsTask;
    }

    public Optional<AndroidTask<TransformTask>> createMergeJniLibFoldersTasks(
            @NonNull TaskFactory tasks,
            @NonNull final VariantScope variantScope) {
        // merge the source folders together using the proper priority.
        AndroidTask<MergeSourceSetFolders> mergeJniLibFoldersTask = androidTasks.create(tasks,
                new MergeSourceSetFolders.MergeJniLibFoldersConfigAction(variantScope));
        mergeJniLibFoldersTask.dependsOn(tasks,
                variantScope.getAssetGenTask());
        variantScope.setMergeJniLibFoldersTask(mergeJniLibFoldersTask);

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
                                            variantScope.getExternalNativeBuildTask().getName())
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
        Optional<AndroidTask<TransformTask>> transformTask = variantScope.getTransformManager()
                .addTransform(tasks, variantScope, mergeTransform);

        return transformTask;
    }

    public void createBuildConfigTask(@NonNull TaskFactory tasks, @NonNull VariantScope scope) {
        AndroidTask<GenerateBuildConfig> generateBuildConfigTask =
                androidTasks.create(tasks, new GenerateBuildConfig.ConfigAction(scope));
        scope.setGenerateBuildConfigTask(generateBuildConfigTask);
        scope.getSourceGenTask().dependsOn(tasks, generateBuildConfigTask.getName());
        if (scope.getVariantConfiguration().getType().isForTesting()) {
            // in case of a test project, the manifest is generated so we need to depend
            // on its creation.

            generateBuildConfigTask.dependsOn(tasks, scope.getManifestProcessorTask());
        } else {
            generateBuildConfigTask.dependsOn(tasks, scope.getCheckManifestTask());
        }
    }

    public void createGenerateResValuesTask(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope scope) {
        AndroidTask<GenerateResValues> generateResValuesTask = androidTasks.create(
                tasks, new GenerateResValues.ConfigAction(scope));
        scope.getResourceGenTask().dependsOn(tasks, generateResValuesTask);
    }

    public void createApkProcessResTask(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope scope) {

        createProcessResTask(
                tasks,
                scope,
                () ->
                        new File(
                                globalScope.getIntermediatesDir(),
                                "symbols/"
                                        + scope.getVariantData()
                                                .getVariantConfiguration()
                                                .getDirName()),
                scope.getProcessResourcePackageOutputDirectory(),
                MergeType.MERGE,
                scope.getGlobalScope().getProjectBaseName());
    }

    public AndroidTask<ProcessAndroidResources> createProcessResTask(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope scope,
            @NonNull Supplier<File> symbolLocation,
            @NonNull File resPackageOutputFolder,
            @NonNull MergeType mergeType,
            @NonNull String baseName) {
        BaseVariantData variantData = scope.getVariantData();

        variantData.calculateFilters(scope.getGlobalScope().getExtension().getSplits());

        boolean useAaptToGenerateLegacyMultidexMainDexProguardRules =
                scope.getDexingMode() == DexingMode.LEGACY_MULTIDEX;

        // split list calculation and save to this file.
        File splitListOutputFile = new File(scope.getSplitSupportDirectory(), FN_SPLIT_LIST);
        AndroidTask<SplitsDiscovery> splitsDiscoveryAndroidTask = androidTasks
                .create(tasks, new SplitsDiscovery.ConfigAction(scope, splitListOutputFile));

        scope.addTaskOutput(
                TaskOutputHolder.TaskOutputType.SPLIT_LIST,
                splitListOutputFile,
                splitsDiscoveryAndroidTask.getName());

        AndroidTask<ProcessAndroidResources> processAndroidResources =
                androidTasks.create(
                        tasks,
                        createProcessAndroidResourcesConfigAction(
                                scope,
                                symbolLocation,
                                resPackageOutputFolder,
                                useAaptToGenerateLegacyMultidexMainDexProguardRules,
                                mergeType,
                                baseName));

        scope.addTaskOutput(
                VariantScope.TaskOutputType.PROCESSED_RES,
                resPackageOutputFolder,
                processAndroidResources.getName());
        scope.setProcessResourcesTask(processAndroidResources);

        // FIX ME : replace with FileCollection
        processAndroidResources.dependsOn(tasks, scope.getMergeResourcesTask());
        if (scope.getDataBindingProcessLayoutsTask() != null) {
            processAndroidResources.dependsOn(
                    tasks, scope.getDataBindingProcessLayoutsTask().getName());
        }
        scope.getSourceGenTask().optionalDependsOn(tasks, processAndroidResources);
        return processAndroidResources;
    }

    protected ProcessAndroidResources.ConfigAction createProcessAndroidResourcesConfigAction(
            @NonNull VariantScope scope,
            @NonNull Supplier<File> symbolLocation,
            @NonNull File resPackageOutputFolder,
            boolean useAaptToGenerateLegacyMultidexMainDexProguardRules,
            @NonNull MergeType mergeType,
            @NonNull String baseName) {
        return new ProcessAndroidResources.ConfigAction(
                scope,
                symbolLocation,
                resPackageOutputFolder,
                useAaptToGenerateLegacyMultidexMainDexProguardRules,
                mergeType,
                baseName);
    }

    /**
     * Creates the split resources packages task if necessary. AAPT will produce split packages for
     * all --split provided parameters. These split packages should be signed and moved unchanged to
     * the FULL_APK build output directory.
     */
    @NonNull
    public AndroidTask<PackageSplitRes> createSplitResourcesTasks(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope scope,
            @NonNull PackagingScope packagingScope) {
        BaseVariantData variantData = scope.getVariantData();

        checkState(
                variantData
                        .getSplitScope()
                        .getSplitHandlingPolicy()
                        .equals(SplitHandlingPolicy.RELEASE_21_AND_AFTER_POLICY),
                "Can only create split resources tasks for pure splits.");

        File densityOrLanguagesPackages = scope.getSplitDensityOrLanguagesPackagesOutputDirectory();
        AndroidTask<PackageSplitRes> packageSplitRes =
                androidTasks.create(
                        tasks, new PackageSplitRes.ConfigAction(scope, densityOrLanguagesPackages));
        variantData.packageSplitResourcesTask = packageSplitRes;
        scope.addTaskOutput(
                VariantScope.TaskOutputType.DENSITY_OR_LANGUAGE_PACKAGED_SPLIT,
                densityOrLanguagesPackages,
                packageSplitRes.getName());

        if (packagingScope.getSigningConfig() != null) {
            packageSplitRes.dependsOn(tasks, getValidateSigningTask(tasks, packagingScope));
        }

        return packageSplitRes;
    }

    @Nullable
    public AndroidTask<PackageSplitAbi> createSplitAbiTasks(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope scope,
            @NonNull PackagingScope packagingScope) {
        BaseVariantData variantData = scope.getVariantData();

        checkState(
                variantData
                        .getSplitScope()
                        .getSplitHandlingPolicy()
                        .equals(SplitHandlingPolicy.RELEASE_21_AND_AFTER_POLICY),
                "split ABI tasks are only compatible with pure splits.");

        Set<String> filters = AbiSplitOptions.getAbiFilters(extension.getSplits().getAbiFilters());
        if (filters.isEmpty()) {
            return null;
        }

        List<ApkData> fullApkDatas =
                variantData.getSplitScope().getSplitsByType(OutputFile.OutputType.FULL_SPLIT);
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
        AndroidTask<GenerateSplitAbiRes> generateSplitAbiRes =
                androidTasks.create(
                        tasks,
                        new GenerateSplitAbiRes.ConfigAction(
                                scope, generateSplitAbiResOutputDirectory));
        scope.addTaskOutput(
                VariantScope.TaskOutputType.ABI_PROCESSED_SPLIT_RES,
                generateSplitAbiResOutputDirectory,
                generateSplitAbiRes.getName());

        // then package those resources with the appropriate JNI libraries.
        File generateSplitAbiPackagesOutputDirectory = scope.getSplitAbiPackagesOutputDirectory();
        AndroidTask<PackageSplitAbi> packageSplitAbiTask =
                androidTasks.create(
                        tasks,
                        new PackageSplitAbi.ConfigAction(
                                scope,
                                generateSplitAbiPackagesOutputDirectory,
                                scope.getOutputs(
                                        VariantScope.TaskOutputType.ABI_PROCESSED_SPLIT_RES)));
        scope.addTaskOutput(
                VariantScope.TaskOutputType.ABI_PACKAGED_SPLIT,
                generateSplitAbiPackagesOutputDirectory,
                packageSplitAbiTask.getName());
        variantData.packageSplitAbiTask = packageSplitAbiTask;

        packageSplitAbiTask.dependsOn(tasks, scope.getNdkBuildable());

        if (packagingScope.getSigningConfig() != null) {
            packageSplitAbiTask.dependsOn(tasks, getValidateSigningTask(tasks, packagingScope));
        }

        if (scope.getExternalNativeBuildTask() != null) {
            packageSplitAbiTask.dependsOn(tasks, scope.getExternalNativeBuildTask());
        }

        return packageSplitAbiTask;
    }

    public void createSplitTasks(@NonNull TaskFactory tasks, @NonNull VariantScope variantScope) {
        PackagingScope packagingScope = new DefaultGradlePackagingScope(variantScope);

        createSplitResourcesTasks(tasks, variantScope, packagingScope);
        createSplitAbiTasks(tasks, variantScope, packagingScope);
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
     * #createMergeJavaResTransform(TaskFactory, VariantScope)}
     *
     * @param tasks tasks factory to create tasks.
     * @param variantScope the variant scope we are operating under.
     */
    public void createProcessJavaResTask(
            @NonNull TaskFactory tasks, @NonNull VariantScope variantScope) {
        // Copy the source folders java resources into the temporary location, mainly to
        // maintain the PluginDsl COPY semantics.

        // TODO: move this file computation completely out of VariantScope.
        File destinationDir = variantScope.getSourceFoldersJavaResDestinationDir();

        AndroidTask<Sync> processJavaResourcesTask =
                androidTasks.create(tasks, new ProcessJavaResConfigAction(variantScope, destinationDir));
        variantScope.setProcessJavaResourcesTask(processJavaResourcesTask);
        processJavaResourcesTask.configure(
                tasks, t -> variantScope.getVariantData().processJavaResourcesTask = t);

        processJavaResourcesTask.dependsOn(tasks, variantScope.getPreBuildTask());

        // create the task outputs for others to consume
        variantScope.addTaskOutput(
                VariantScope.TaskOutputType.JAVA_RES,
                destinationDir,
                processJavaResourcesTask.getName());

        // create the stream generated from this task
        variantScope
                .getTransformManager()
                .addStream(
                        OriginalStream.builder(project, "processed-java-res")
                                .addContentType(DefaultContentType.RESOURCES)
                                .addScope(Scope.PROJECT)
                                .setFolder(destinationDir)
                                .setDependency(processJavaResourcesTask.getName())
                                .build());
    }

    /**
     * Sets up the Merge Java Res transform.
     *
     *
     * @param tasks tasks factory to create tasks.
     * @param variantScope the variant scope we are operating under.
     *
     * @see #createProcessJavaResTask(TaskFactory, VariantScope)
     */
    public void createMergeJavaResTransform(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope variantScope) {
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
                transformManager.addTransform(tasks, variantScope, mergeTransform).orElse(null));
    }

    public AndroidTask<AidlCompile> createAidlTask(@NonNull TaskFactory tasks, @NonNull VariantScope scope) {
        AndroidTask<AidlCompile> aidlCompileTask = androidTasks
                .create(tasks, new AidlCompile.ConfigAction(scope));
        scope.setAidlCompileTask(aidlCompileTask);
        scope.getSourceGenTask().dependsOn(tasks, aidlCompileTask);
        aidlCompileTask.dependsOn(tasks, scope.getPreBuildTask());

        return aidlCompileTask;
    }

    public void createShaderTask(@NonNull TaskFactory tasks, @NonNull VariantScope scope) {
        // merge the shader folders together using the proper priority.
        AndroidTask<MergeSourceSetFolders> mergeShadersTask = androidTasks.create(tasks,
                new MergeSourceSetFolders.MergeShaderSourceFoldersConfigAction(scope));
        // TODO do we support non compiled shaders in aars?
        //mergeShadersTask.dependsOn(tasks, scope.getVariantData().prepareDependenciesTask);

        // compile the shaders
        AndroidTask<ShaderCompile> shaderCompileTask = androidTasks.create(
                tasks, new ShaderCompile.ConfigAction(scope));
        scope.setShaderCompileTask(shaderCompileTask);
        shaderCompileTask.dependsOn(tasks, mergeShadersTask);

        scope.getAssetGenTask().dependsOn(tasks, shaderCompileTask);
    }

    /**
     * Creates the task for creating *.class files using javac. These tasks are created regardless
     * of whether Jack is used or not, but assemble will not depend on them if it is. They are
     * always used when running unit tests.
     */
    public AndroidTask<? extends JavaCompile> createJavacTask(
            @NonNull final TaskFactory tasks,
            @NonNull final VariantScope scope) {
        final BaseVariantData variantData = scope.getVariantData();

        AndroidTask<IncrementalSafeguard> javacIncrementalSafeguard = androidTasks.create(tasks,
                new IncrementalSafeguard.ConfigAction(scope));

        AndroidTask<JavaPreCompileTask> preCompileTask = androidTasks.create(tasks,
                new JavaPreCompileTask.ConfigAction(scope));
        preCompileTask.dependsOn(tasks, scope.getPreBuildTask());

        final AndroidTask<? extends JavaCompile> javacTask =
                androidTasks.create(tasks, new JavaCompileConfigAction(scope));
        scope.setJavacTask(javacTask);
        javacTask.dependsOn(tasks, javacIncrementalSafeguard, preCompileTask);

        setupCompileTaskDependencies(tasks, scope, javacTask);

        ConfigurableFileCollection fileCollection =
                scope.addTaskOutput(JAVAC, scope.getJavaOutputDir(), javacTask.getName());

        // Create the classes artifact for uses by external test modules.
        if (variantData.getVariantConfiguration().getType() == VariantType.DEFAULT) {
            File dest =
                    new File(
                            globalScope.getBuildDir(),
                            FileUtils.join(
                                    FD_INTERMEDIATES,
                                    "classes-jar",
                                    scope.getVariantConfiguration().getDirName()));

            AndroidTask<Jar> task =
                    androidTasks.create(
                            tasks,
                            new TaskConfigAction<Jar>() {
                                @NonNull
                                @Override
                                public String getName() {
                                    return scope.getTaskName("bundleAppClasses");
                                }

                                @NonNull
                                @Override
                                public Class<Jar> getType() {
                                    return Jar.class;
                                }

                                @Override
                                public void execute(@NonNull Jar task) {
                                    task.from(fileCollection);
                                    task.from(
                                            scope.getVariantData()
                                                    .getGeneratedBytecodeCollection());
                                    task.setDestinationDir(dest);
                                    task.setArchiveName("classes.jar");
                                }
                            });
            scope.publishIntermediateArtifact(
                    new File(dest, "classes.jar"), task.getName(), APK_CLASSES);
        }

        return javacTask;
    }

    /**
     * Add stream of classes compiled by javac to transform manager.
     *
     * This should not be called for classes that will also be compiled from source by jack.
     */
    public void addJavacClassesStream(VariantScope scope) {
        checkNotNull(scope.getJavacTask());
        // create the output stream from this task
        scope.getTransformManager()
                .addStream(
                        OriginalStream.builder(project, "javac-output")
                                .addContentType(DefaultContentType.CLASSES)
                                .addScope(Scope.PROJECT)
                                .setFolder(scope.getJavaOutputDir())
                                .setDependency(scope.getJavacTask().getName())
                                .build());

        scope.getTransformManager()
                .addStream(
                        OriginalStream.builder(project, "generated-bytecode")
                                .addContentType(DefaultContentType.CLASSES)
                                .addScope(Scope.PROJECT)
                                .setFileCollection(
                                        scope.getVariantData().getGeneratedBytecodeCollection())
                                .build());
    }

    private static void setupCompileTaskDependencies(
            @NonNull TaskFactory tasks, @NonNull VariantScope scope, AndroidTask<?> compileTask) {

        compileTask.optionalDependsOn(tasks, scope.getSourceGenTask());
    }

    /**
     * Makes the given task the one used by top-level "compile" task.
     */
    public static void setJavaCompilerTask(
            @NonNull AndroidTask<? extends Task> javaCompilerTask,
            @NonNull TaskFactory tasks,
            @NonNull VariantScope scope) {
        scope.getCompileTask().dependsOn(tasks, javaCompilerTask);
        scope.setJavaCompilerTask(javaCompilerTask);

        // TODO: Get rid of it once we stop keeping tasks in variant data.
        //noinspection VariableNotUsedInsideIf
        if (scope.getVariantData().javacTask != null) {
            // This is not the experimental plugin, let's update variant data, so Variants API
            // keeps working.
            scope.getVariantData().javaCompilerTask = tasks.named(javaCompilerTask.getName());
        }
    }

    /**
     * Creates the task that will handle micro apk.
     *
     * New in 2.2, it now supports the unbundled mode, in which the apk is not bundled
     * anymore, but we still have an XML resource packaged, and a custom entry in the manifest.
     * This is triggered by passing a null {@link Configuration} object.
     *
     * @param tasks the task factory
     * @param scope the variant scope
     * @param config an optional Configuration object. if non null, this will embed the micro apk,
     *               if null this will trigger the unbundled mode.
     */
    public void createGenerateMicroApkDataTask(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope scope,
            @Nullable FileCollection config) {
        AndroidTask<GenerateApkDataTask> generateMicroApkTask = androidTasks.create(tasks,
                new GenerateApkDataTask.ConfigAction(scope, config));
        scope.setMicroApkTask(generateMicroApkTask);

        // the merge res task will need to run after this one.
        scope.getResourceGenTask().dependsOn(tasks, generateMicroApkTask);
    }

    public void createExternalNativeBuildJsonGenerators(@NonNull VariantScope scope) {

        CoreExternalNativeBuild externalNativeBuild = extension.getExternalNativeBuild();
        ExternalNativeBuildTaskUtils.ExternalNativeBuildProjectPathResolution pathResolution =
                ExternalNativeBuildTaskUtils.getProjectPath(externalNativeBuild);
        if (pathResolution.errorText != null) {
            androidBuilder.getErrorReporter().handleSyncError(
                    scope.getVariantConfiguration().getFullName(),
                    SyncIssue.TYPE_EXTERNAL_NATIVE_BUILD_CONFIGURATION,
                    pathResolution.errorText);
            return;
        }

        if (pathResolution.makeFile == null) {
            // No project
            return;
        }

        scope.setExternalNativeJsonGenerator(ExternalNativeJsonGenerator.create(
                project.getProjectDir(),
                pathResolution.buildSystem,
                pathResolution.makeFile,
                androidBuilder,
                sdkHandler,
                scope
        ));
    }

    public void createExternalNativeBuildTasks(TaskFactory tasks, @NonNull VariantScope scope) {
        ExternalNativeJsonGenerator generator = scope.getExternalNativeJsonGenerator();
        if (generator == null) {
            return;
        }

        // Set up JSON generation tasks
        AndroidTask<?> generateTask = androidTasks.create(tasks,
                ExternalNativeBuildJsonTask.createTaskConfigAction(
                        generator, scope));

        generateTask.dependsOn(tasks, scope.getPreBuildTask());

        ProjectOptions projectOptions = globalScope.getProjectOptions();

        String targetAbi =
                projectOptions.get(BooleanOption.BUILD_ONLY_TARGET_ABI)
                        ? projectOptions.get(StringOption.IDE_BUILD_TARGET_ABI)
                        : null;

        // Set up build tasks
        AndroidTask<ExternalNativeBuildTask> buildTask =
                androidTasks.create(
                        tasks,
                        new ExternalNativeBuildTask.ConfigAction(
                                targetAbi, generator, scope, androidBuilder));

        buildTask.dependsOn(tasks, generateTask);
        scope.setExternalNativeBuildTask(buildTask);
        scope.getCompileTask().dependsOn(tasks, buildTask);

        // Set up clean tasks
        Task cleanTask = checkNotNull(tasks.named("clean"));
        cleanTask.dependsOn(androidTasks.create(tasks, new ExternalNativeCleanTask.ConfigAction(
                generator, scope, androidBuilder)).getName());
    }

    public void createNdkTasks(@NonNull TaskFactory tasks, @NonNull VariantScope scope) {
        if (ExternalNativeBuildTaskUtils.isExternalNativeBuildEnabled(
                extension.getExternalNativeBuild())) {
            return;
        }

        AndroidTask<NdkCompile> ndkCompileTask =
                androidTasks.create(tasks, new NdkCompile.ConfigAction(scope));

        ndkCompileTask.dependsOn(tasks, scope.getPreBuildTask());
        if (Boolean.TRUE.equals(
                scope.getVariantData()
                        .getVariantConfiguration()
                        .getMergedFlavor()
                        .getRenderscriptNdkModeEnabled())) {
            ndkCompileTask.dependsOn(tasks, scope.getRenderscriptCompileTask());
        }
        scope.getCompileTask().dependsOn(tasks, ndkCompileTask);
    }

    /**
     * Create transform for stripping debug symbols from native libraries before deploying.
     */
    public static void createStripNativeLibraryTask(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope scope) {
        if (!scope.getGlobalScope().getNdkHandler().isConfigured()) {
            // We don't know where the NDK is, so we won't be stripping the debug symbols from
            // native libraries.
            return;
        }
        TransformManager transformManager = scope.getTransformManager();
        GlobalScope globalScope = scope.getGlobalScope();
        transformManager.addTransform(
                tasks,
                scope,
                new StripDebugSymbolTransform(
                        globalScope.getProject(),
                        globalScope.getNdkHandler(),
                        globalScope.getExtension().getPackagingOptions().getDoNotStrip(),
                        scope.getVariantConfiguration().getType() == VariantType.LIBRARY));
    }

    /**
     * Creates the tasks to build unit tests.
     */
    public void createUnitTestVariantTasks(
            @NonNull TaskFactory tasks,
            @NonNull TestVariantData variantData) {
        VariantScope variantScope = variantData.getScope();
        BaseVariantData testedVariantData =
                checkNotNull(variantScope.getTestedVariantData(), "Not a unit test variant");
        VariantScope testedVariantScope = testedVariantData.getScope();

        createPreBuildTasks(tasks, variantScope);

        // Create all current streams (dependencies mostly at this point)
        createDependencyStreams(tasks, variantScope);

        createProcessJavaResTask(tasks, variantScope);
        createMergeJavaResTransform(tasks, variantScope);
        createCompileAnchorTask(tasks, variantScope);

        if (extension.getTestOptions().getUnitTests().isIncludeAndroidResources()) {
            AndroidTask<GenerateTestConfig> generateTestConfig =
                    androidTasks.create(tasks, new GenerateTestConfig.ConfigAction(variantScope));

            variantScope.getProcessJavaResourcesTask().dependsOn(tasks, generateTestConfig);
        }

        // :app:compileDebugUnitTestSources should be enough for running tests from AS, so add
        // dependencies on tasks that prepare necessary data files.
        AndroidTask<Task> compileTask = variantScope.getCompileTask();
        compileTask.dependsOn(
                tasks,
                variantScope.getProcessJavaResourcesTask(),
                testedVariantScope.getProcessJavaResourcesTask());
        if (extension.getTestOptions().getUnitTests().isIncludeAndroidResources()) {
            compileTask.dependsOn(tasks, testedVariantScope.getMergeResourcesTask());
            compileTask.dependsOn(tasks, testedVariantScope.getMergeAssetsTask());
            compileTask.dependsOn(tasks, testedVariantScope.getManifestProcessorTask());
        }

        AndroidTask<? extends JavaCompile> javacTask = createJavacTask(tasks, variantScope);
        addJavacClassesStream(variantScope);
        setJavaCompilerTask(javacTask, tasks, variantScope);
        javacTask.dependsOn(tasks, testedVariantScope.getJavacTask());

        createRunUnitTestTask(tasks, variantScope);

        AndroidTask<DefaultTask> assembleUnitTests = variantScope.getAssembleTask();
        assembleUnitTests.dependsOn(tasks, createMockableJar);

        // This hides the assemble unit test task from the task list.
        assembleUnitTests.configure(tasks, task -> task.setGroup(null));
    }

    /**
     * Creates the tasks to build android tests.
     */
    public void createAndroidTestVariantTasks(@NonNull TaskFactory tasks,
            @NonNull TestVariantData variantData) {
        VariantScope variantScope = variantData.getScope();

        final BaseVariantData testedVariantData =
                (BaseVariantData) variantData.getTestedVariantData();

        createAnchorTasks(tasks, variantScope);

        // Create all current streams (dependencies mostly at this point)
        createDependencyStreams(tasks, variantScope);

        // Add a task to process the manifest
        createProcessTestManifestTask(tasks, variantScope,
                variantScope.getTestedVariantData().getScope());

        // Add a task to create the res values
        createGenerateResValuesTask(tasks, variantScope);

        // Add a task to compile renderscript files.
        createRenderscriptTask(tasks, variantScope);

        // Add a task to merge the resource folders
        createMergeResourcesTask(tasks, variantScope);

        // Add a task to merge the assets folders
        createMergeAssetsTask(tasks, variantScope, null);

        // Add a task to create the BuildConfig class
        createBuildConfigTask(tasks, variantScope);

        // Add a task to generate resource source files
        createApkProcessResTask(tasks, variantScope);

        // process java resources
        createProcessJavaResTask(tasks, variantScope);
        createMergeJavaResTransform(tasks, variantScope);

        createAidlTask(tasks, variantScope);

        createShaderTask(tasks, variantScope);

        // Add NDK tasks
        if (!isComponentModelPlugin()) {
            createNdkTasks(tasks, variantScope);
        }
        variantScope.setNdkBuildable(getNdkBuildable(variantData));

        // add tasks to merge jni libs.
        createMergeJniLibFoldersTasks(tasks, variantScope);
        // create data binding merge task before the javac task so that it can
        // parse jars before any consumer
        createDataBindingMergeArtifactsTaskIfNecessary(tasks, variantScope);
        // Add a task to compile the test application
        if (variantData.getVariantConfiguration().isJackEnabled()) {
            createJackTask(tasks, variantScope);
        } else {
            AndroidTask<? extends JavaCompile> javacTask = createJavacTask(tasks, variantScope);
            addJavacClassesStream(variantScope);
            setJavaCompilerTask(javacTask, tasks, variantScope);
            createPostCompilationTasks(tasks, variantScope);
        }
        checkNotNull(variantScope.getJavaCompilerTask());
        variantScope.getJavaCompilerTask().dependsOn(
                tasks,
                testedVariantData.getScope().getJavaCompilerTask());

        // Add data binding tasks if enabled
        createDataBindingTasksIfNecessary(tasks, variantScope);

        createPackagingTask(tasks, variantScope, false /*publishApk*/,
                null /* buildInfoGeneratorTask */);

        tasks.named(
                ASSEMBLE_ANDROID_TEST,
                assembleTest ->
                        assembleTest.dependsOn(variantData.getScope().getAssembleTask().getName()));

        createConnectedTestForVariant(tasks, variantScope);
    }


    protected enum IncrementalMode {
        NONE,
        FULL,
    }

    /**
     * Returns the incremental mode for this variant.
     *
     * @param config the variant's configuration
     * @return the {@link IncrementalMode} for this variant.
     */
    protected IncrementalMode getIncrementalMode(@NonNull GradleVariantConfiguration config) {
        if (config.isInstantRunSupported()
                && targetDeviceSupportsInstantRun(config, project)
                && globalScope.isActive(OptionalCompilationStep.INSTANT_DEV)) {
            return IncrementalMode.FULL;
        }
        return IncrementalMode.NONE;
    }

    private static boolean targetDeviceSupportsInstantRun(
            @NonNull GradleVariantConfiguration config,
            @NonNull Project project) {
        if (config.isLegacyMultiDexMode()) {
            // We don't support legacy multi-dex on Dalvik.
            return AndroidGradleOptions.getTargetAndroidVersion(project).getFeatureLevel()
                    >= AndroidVersion.ART_RUNTIME.getFeatureLevel();
        }

        return true;
    }

    /** Is the given variant relevant for lint? */
    private static boolean isLintVariant(@NonNull BaseVariantData baseVariantData) {
        // Only create lint targets for variants like debug and release, not debugTest
        VariantConfiguration config = baseVariantData.getVariantConfiguration();
        return !config.getType().isForTesting();
    }

    /**
     * Add tasks for running lint on individual variants. We've already added a
     * lint task earlier which runs on all variants.
     */
    public void createLintTasks(TaskFactory tasks, final VariantScope scope) {
        final BaseVariantData baseVariantData = scope.getVariantData();
        if (!isLintVariant(baseVariantData)) {
            return;
        }

        // wire the main lint task dependency.
        tasks.named(LINT, lint -> lint.dependsOn(scope.getJavacTask().getName()));

        AndroidTask<Lint> variantLintCheck = androidTasks.create(
                tasks, new Lint.ConfigAction(scope));
        variantLintCheck.dependsOn(tasks, LINT_COMPILE, scope.getJavacTask());
    }

    private void maybeCreateLintVitalTask(
            @NonNull TaskFactory tasks, @NonNull ApkVariantData variantData) {
        // TODO: re-enable with Jack when possible
        if (variantData.getVariantConfiguration().getBuildType().isDebuggable()
                || variantData.getVariantConfiguration().isJackEnabled()
                || !extension.getLintOptions().isCheckReleaseBuilds()) {
            return;
        }

        AndroidTask<Lint> lintReleaseCheck =
                androidTasks.create(tasks, new Lint.VitalConfigAction(variantData.getScope()));
        lintReleaseCheck.optionalDependsOn(tasks, variantData.javacTask);

        variantData.getScope().getAssembleTask().dependsOn(tasks, lintReleaseCheck);

        // If lint is being run, we do not need to run lint vital.
        project.getGradle()
                .getTaskGraph()
                .whenReady(
                        taskGraph -> {
                            if (taskGraph.hasTask(LINT)) {
                                project.getTasks()
                                        .getByName(lintReleaseCheck.getName())
                                        .setEnabled(false);
                            }
                        });
    }

    private void createRunUnitTestTask(
            @NonNull TaskFactory tasks,
            @NonNull final VariantScope variantScope) {
        final AndroidTask<AndroidUnitTest> runTestsTask =
                androidTasks.create(tasks, new AndroidUnitTest.ConfigAction(variantScope));

        tasks.named(JavaPlugin.TEST_TASK_NAME, test -> test.dependsOn(runTestsTask.getName()));
    }

    public void createTopLevelTestTasks(final TaskFactory tasks, boolean hasFlavors) {
        createMockableJarTask(tasks);

        final List<String> reportTasks = Lists.newArrayListWithExpectedSize(2);

        List<DeviceProvider> providers = extension.getDeviceProviders();

        // If more than one flavor, create a report aggregator task and make this the parent
        // task for all new connected tasks.  Otherwise, create a top level connectedAndroidTest
        // DefaultTask.

        AndroidTask<? extends DefaultTask> connectedAndroidTestTask;
        if (hasFlavors) {
            connectedAndroidTestTask = androidTasks.create(tasks,
                    new AndroidReportTask.ConfigAction(
                            globalScope,
                            AndroidReportTask.ConfigAction.TaskKind.CONNECTED));
            reportTasks.add(connectedAndroidTestTask.getName());
        } else {
            connectedAndroidTestTask =
                    androidTasks.create(
                            tasks,
                            CONNECTED_ANDROID_TEST,
                            connectedTask -> {
                                connectedTask.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
                                connectedTask.setDescription(
                                        "Installs and runs instrumentation tests "
                                                + "for all flavors on connected devices.");
                            });
        }

        tasks.named(CONNECTED_CHECK, check -> check.dependsOn(connectedAndroidTestTask.getName()));

        AndroidTask<? extends DefaultTask> deviceAndroidTestTask;
        // if more than one provider tasks, either because of several flavors, or because of
        // more than one providers, then create an aggregate report tasks for all of them.
        if (providers.size() > 1 || hasFlavors) {
            deviceAndroidTestTask = androidTasks.create(tasks,
                    new AndroidReportTask.ConfigAction(
                            globalScope,
                            AndroidReportTask.ConfigAction.TaskKind.DEVICE_PROVIDER));
            reportTasks.add(deviceAndroidTestTask.getName());
        } else {
            deviceAndroidTestTask = androidTasks.create(tasks,
                    DEVICE_ANDROID_TEST,
                    providerTask -> {
                        providerTask.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
                        providerTask.setDescription("Installs and runs instrumentation tests "
                                + "using all Device Providers.");
                    });
        }

        tasks.named(DEVICE_CHECK, check -> check.dependsOn(deviceAndroidTestTask.getName()));

        // Create top level unit test tasks.

        androidTasks.create(tasks, JavaPlugin.TEST_TASK_NAME, unitTestTask -> {
            unitTestTask.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
            unitTestTask.setDescription("Run unit tests for all variants.");
        });
        tasks.named(JavaBasePlugin.CHECK_TASK_NAME,
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
            project.getGradle().getTaskGraph().whenReady(new Closure<Void>(this, this) {
                public void doCall(TaskExecutionGraph taskGraph) {
                    for (String reportTask : reportTasks) {
                        if (taskGraph.hasTask(reportTask)) {
                            tasks.named(reportTask, new Action<Task>() {
                                @Override
                                public void execute(Task task) {
                                    ((AndroidReportTask) task).setWillRun();
                                }
                            });
                        }
                    }
                }
            });
        }
    }

    protected void createConnectedTestForVariant(
            @NonNull TaskFactory tasks,
            @NonNull final VariantScope variantScope) {
        final BaseVariantData baseVariantData = variantScope.getTestedVariantData();
        final TestVariantData testVariantData = (TestVariantData) variantScope.getVariantData();

        TestDataImpl testData = new TestDataImpl(testVariantData);
        testData.setExtraInstrumentationTestRunnerArgs(
                projectOptions.getExtraInstrumentationTestRunnerArgs());

        configureTestData(testData);

        // create the check tasks for this test
        // first the connected one.
        ImmutableList<AndroidTask<DefaultTask>> artifactsTasks =
                ImmutableList.of(
                        variantScope.getAssembleTask(),
                        testVariantData.getTestedVariantData().getScope().getAssembleTask());

        AndroidTask<DeviceProviderInstrumentTestTask> connectedTask =
                androidTasks.create(
                        tasks,
                        new DeviceProviderInstrumentTestTask.ConfigAction(
                                testVariantData.getScope(),
                                new ConnectedDeviceProvider(
                                        sdkHandler.getSdkInfo().getAdb(),
                                        extension.getAdbOptions().getTimeOutInMs(),
                                        new LoggerWrapper(logger)),
                                testData,
                                project.files() /* testTargetMetadata */));

        connectedTask.dependsOn(tasks, artifactsTasks.toArray());

        tasks.named(CONNECTED_ANDROID_TEST,
                connectedAndroidTest -> connectedAndroidTest.dependsOn(connectedTask.getName()));

        if (baseVariantData.getVariantConfiguration().getBuildType().isTestCoverageEnabled()) {
            final AndroidTask reportTask;
            if (baseVariantData.getVariantConfiguration().isJackEnabled()) {
                reportTask = androidTasks.create(
                        tasks,
                        new JackJacocoReportTask.ConfigAction(variantScope));
            } else {
                reportTask = androidTasks.create(
                        tasks,
                        new JacocoReportTask.ConfigAction(variantScope));
                reportTask.dependsOn(tasks, project.getConfigurations().getAt(JacocoPlugin.ANT_CONFIGURATION_NAME));
            }
            reportTask.dependsOn(tasks, connectedTask.getName());

            variantScope.setCoverageReportTask(reportTask);
            baseVariantData.getScope().getCoverageReportTask().dependsOn(tasks, reportTask);

            tasks.named(CONNECTED_ANDROID_TEST,
                    connectedAndroidTest -> connectedAndroidTest.dependsOn(reportTask.getName()));
        }

        List<DeviceProvider> providers = extension.getDeviceProviders();

        // now the providers.
        for (DeviceProvider deviceProvider : providers) {

            final AndroidTask<DeviceProviderInstrumentTestTask> providerTask = androidTasks
                    .create(tasks, new DeviceProviderInstrumentTestTask.ConfigAction(
                            testVariantData.getScope(), deviceProvider, testData,
                            project.files() /* testTargetMetadata */));

            providerTask.dependsOn(tasks, artifactsTasks.toArray());
            tasks.named(DEVICE_ANDROID_TEST,
                    deviceAndroidTest -> deviceAndroidTest.dependsOn(providerTask.getName()));
        }

        // now the test servers
        List<TestServer> servers = extension.getTestServers();
        for (final TestServer testServer : servers) {
            final AndroidTask<TestServerTask> serverTask = androidTasks.create(
                    tasks,
                    new TestServerTaskConfigAction(variantScope, testServer));
            serverTask.dependsOn(tasks, variantScope.getAssembleTask());

            tasks.named(DEVICE_CHECK,
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
            @NonNull TaskFactory tasks,
            @NonNull final VariantScope variantScope) {

        checkNotNull(variantScope.getJavacTask());

        variantScope
                .getInstantRunBuildContext()
                .setInstantRunMode(
                        getIncrementalMode(variantScope.getVariantConfiguration())
                                != IncrementalMode.NONE);

        final BaseVariantData variantData = variantScope.getVariantData();
        final GradleVariantConfiguration config = variantData.getVariantConfiguration();

        TransformManager transformManager = variantScope.getTransformManager();

        // ---- Code Coverage first -----
        boolean isTestCoverageEnabled = config.getBuildType().isTestCoverageEnabled() &&
                !config.getType().isForTesting() &&
                getIncrementalMode(variantScope.getVariantConfiguration()) == IncrementalMode.NONE;
        if (isTestCoverageEnabled) {
            createJacocoTransform(tasks, variantScope);
        }

        maybeCreateDesugarTask(tasks, variantScope, config.getMinSdkVersion(), transformManager);

        AndroidConfig extension = variantScope.getGlobalScope().getExtension();

        // ----- External Transforms -----
        // apply all the external transforms.
        List<Transform> customTransforms = extension.getTransforms();
        List<List<Object>> customTransformsDependencies = extension.getTransformsDependencies();

        for (int i = 0, count = customTransforms.size(); i < count; i++) {
            Transform transform = customTransforms.get(i);

            List<Object> deps = customTransformsDependencies.get(i);
            transformManager
                    .addTransform(tasks, variantScope, transform)
                    .ifPresent(t -> {
                        if (!deps.isEmpty()) {
                            t.dependsOn(tasks, deps);
                        }

                        // if the task is a no-op then we make assemble task depend on it.
                        if (transform.getScopes().isEmpty()) {
                            variantScope.getAssembleTask().dependsOn(tasks, t);
                        }
                    });
        }

        // ----- Android studio profiling transforms
        for (String jar : AndroidGradleOptions.getAdvancedProfilingTransforms(project)) {
            if (variantScope.getVariantConfiguration().getBuildType().isDebuggable()
                    && variantData.getType().equals(VariantType.DEFAULT)
                    && jar != null) {
                transformManager.addTransform(tasks, variantScope, new CustomClassTransform(jar));
            }
        }

        // ----- Minify next -----
        maybeCreateJavaCodeShrinkerTransform(tasks, variantScope);

        maybeCreateResourcesShrinkerTransform(tasks, variantScope);

        // ----- 10x support

        AndroidTask<PreColdSwapTask> preColdSwapTask = null;
        if (variantScope.getInstantRunBuildContext().isInInstantRunMode()) {

            AndroidTask<DefaultTask> allActionsAnchorTask =
                    createInstantRunAllActionsTasks(tasks, variantScope);
            assert variantScope.getInstantRunTaskManager() != null;
            preColdSwapTask = variantScope.getInstantRunTaskManager()
                    .createPreColdswapTask(project);
            preColdSwapTask.dependsOn(tasks, allActionsAnchorTask);

            if (InstantRunPatchingPolicy.PRE_LOLLIPOP
                    != variantScope.getInstantRunBuildContext().getPatchingPolicy()) {
                // force pre-dexing to be true as we rely on individual slices to be packaged
                // separately.
                extension.getDexOptions().setPreDexLibraries(true);
                variantScope.getInstantRunTaskManager().createSlicerTask();
            }

            extension.getDexOptions().setJumboMode(true);
        }
        // ----- Multi-Dex support

        DexingMode dexingMode = variantScope.getDexingMode();
        Optional<AndroidTask<TransformTask>> multiDexClassListTask;

        if (dexingMode == DexingMode.LEGACY_MULTIDEX) {
            boolean proguardInPipeline = variantScope.getCodeShrinker() == CodeShrinker.PROGUARD;

            // If ProGuard will be used, we'll end up with a "fat" jar anyway. If we're using the
            // new dexing pipeline, we'll use the new MainDexListTransform below, so there's no need
            // for merging all classes into a single jar.
            if (!proguardInPipeline && !projectOptions.get(BooleanOption.ENABLE_DEX_ARCHIVE)) {
                // Create a transform to jar the inputs into a single jar. Merge the classes only,
                // no need to package the resources since they are not used during the computation.
                JarMergingTransform jarMergingTransform =
                        new JarMergingTransform(TransformManager.SCOPE_FULL_PROJECT);
                transformManager
                        .addTransform(tasks, variantScope, jarMergingTransform)
                        .ifPresent(variantScope::addColdSwapBuildTask);
            }

            // ---------
            // create the transform that's going to take the code and the proguard keep list
            // from above and compute the main class list.
            Transform multiDexTransform;
            if (projectOptions.get(BooleanOption.ENABLE_DEX_ARCHIVE)) {
                multiDexTransform = new MainDexListTransform(
                        variantScope,
                        extension.getDexOptions());
            } else {
                multiDexTransform = new MultiDexTransform(variantScope, extension.getDexOptions());
            }
            multiDexClassListTask =
                    transformManager.addTransform(tasks, variantScope, multiDexTransform);
            multiDexClassListTask.ifPresent(variantScope::addColdSwapBuildTask);
        } else {
            multiDexClassListTask = Optional.empty();
        }


        if (projectOptions.get(BooleanOption.ENABLE_DEX_ARCHIVE)
                && variantScope.getVariantConfiguration().getBuildType().isDebuggable()) {
            createNewDexTasks(tasks, variantScope, multiDexClassListTask.orElse(null), dexingMode);
        } else {
            createDexTasks(tasks, variantScope, multiDexClassListTask.orElse(null), dexingMode);
        }

        if (preColdSwapTask != null) {
            for (AndroidTask<? extends DefaultTask> task : variantScope.getColdSwapBuildTasks()) {
                task.dependsOn(tasks, preColdSwapTask);
            }
        }
    }

    private void maybeCreateDesugarTask(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope variantScope,
            @NonNull ApiVersion minSdk,
            @NonNull TransformManager transformManager) {
        if (globalScope
                        .getExtension()
                        .getCompileOptions()
                        .getTargetCompatibility()
                        .isJava8Compatible()
                && variantScope.getJava8LangSupportType() == Java8LangSupport.DESUGAR) {
            FileCache userCache =
                    globalScope
                                    .getProjectOptions()
                                    .get(BooleanOption.ENABLE_INTERMEDIATE_ARTIFACTS_CACHE)
                            ? globalScope.getBuildCache()
                            : null;

            int minSdkVersion;
            if (DefaultApiVersion.isPreview(minSdk)) {
                //noinspection ConstantConditions - preview always has a codename
                minSdkVersion = SdkVersionInfo.getApiByPreviewName(minSdk.getCodename(), true);
            } else {
                minSdkVersion = minSdk.getApiLevel();
            }
            DesugarTransform desugarTransform =
                    new DesugarTransform(
                            () -> androidBuilder.getBootClasspath(true),
                            System.getProperty("sun.boot.class.path"),
                            userCache,
                            globalScope.getProjectLevelCache(),
                            minSdkVersion,
                            androidBuilder.getJavaProcessExecutor(),
                            globalScope.getJava8LangSupportJar(),
                            project.getLogger().isEnabled(LogLevel.INFO));
            transformManager.addTransform(tasks, variantScope, desugarTransform);
        }
    }

    /**
     * Creates tasks used for DEX generation. This will use a new pipeline that uses dex archives in
     * order to enable incremental dexing support.
     */
    private void createNewDexTasks(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope variantScope,
            @Nullable AndroidTask<TransformTask> multiDexClassListTask,
            @NonNull DexingMode dexingMode) {
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
        FileCache userLevelCache = getUserLevelCache(minified, dexOptions.getPreDexLibraries());
        FileCache projectLevelCache =
                getProjectLevelCache(minified, dexOptions.getPreDexLibraries());
        DexArchiveBuilderTransform preDexTransform =
                new DexArchiveBuilderTransform(
                        dexOptions,
                        variantScope.getGlobalScope().getAndroidBuilder().getErrorReporter(),
                        userLevelCache,
                        projectLevelCache,
                        variantScope.getInstantRunBuildContext().isInInstantRunMode(),
                        variantScope.getVariantConfiguration().getMinSdkVersionValue());
        transformManager
                .addTransform(tasks, variantScope, preDexTransform)
                .ifPresent(variantScope::addColdSwapBuildTask);

        DexMergerTransform dexTransform =
                new DexMergerTransform(
                        dexingMode,
                        dexingMode == DexingMode.LEGACY_MULTIDEX
                                ? project.files(variantScope.getMainDexListFile())
                                : null,
                        variantScope.getGlobalScope().getAndroidBuilder().getErrorReporter());
        Optional<AndroidTask<TransformTask>> dexTask =
                transformManager.addTransform(tasks, variantScope, dexTransform);
        // need to manually make dex task depend on MultiDexTransform since there's no stream
        // consumption making this automatic
        dexTask.ifPresent(
                t -> {
                    t.optionalDependsOn(tasks, multiDexClassListTask);
                    variantScope.addColdSwapBuildTask(t);
                });
    }

    @Nullable
    private FileCache getUserLevelCache(boolean isMinifiedEnabled, boolean preDexLibraries) {
        if (preDexLibraries
                && !isMinifiedEnabled
                && projectOptions.get(BooleanOption.ENABLE_INTERMEDIATE_ARTIFACTS_CACHE)) {
            return buildCache;
        } else {
            return null;
        }
    }

    @Nullable
    private FileCache getProjectLevelCache(boolean isMinifiedEnabled, boolean preDexLibraries) {
        if (preDexLibraries && !isMinifiedEnabled) {
            return globalScope.getProjectLevelCache();
        } else {
            return null;
        }
    }

    /** Creates the pre-dexing task if needed, and task for producing the final DEX file(s). */
    private void createDexTasks(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope variantScope,
            @Nullable AndroidTask<TransformTask> multiDexClassListTask,
            @NonNull DexingMode dexingMode) {
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
                dexingMode.isPreDex()
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
                            dexingMode,
                            variantScope.getInstantRunBuildContext().isInInstantRunMode(),
                            variantScope.getMinSdkForDx());
            transformManager.addTransform(tasks, variantScope, preDexTransform)
                    .ifPresent(variantScope::addColdSwapBuildTask);
        }

        if (!preDexEnabled || dexingMode != DexingMode.NATIVE_MULTIDEX) {
            // run if non native multidex or no pre-dexing
            DexTransform dexTransform =
                    new DexTransform(
                            dexOptions,
                            dexingMode,
                            preDexEnabled,
                            project.files(variantScope.getMainDexListFile()),
                            verifyNotNull(androidBuilder.getTargetInfo(), "Target Info not set."),
                            androidBuilder.getDexByteCodeConverter(),
                            androidBuilder.getErrorReporter(),
                            variantScope.getMinSdkForDx());
            Optional<AndroidTask<TransformTask>> dexTask =
                    transformManager.addTransform(tasks, variantScope, dexTransform);
            // need to manually make dex task depend on MultiDexTransform since there's no stream
            // consumption making this automatic
            dexTask.ifPresent(
                    t -> {
                        t.optionalDependsOn(tasks, multiDexClassListTask);
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

    /**
     * Create InstantRun related tasks that should be ran right after the java compilation task.
     */
    @NonNull
    private AndroidTask<DefaultTask> createInstantRunAllActionsTasks(
            @NonNull TaskFactory tasks, @NonNull VariantScope variantScope) {

        AndroidTask<DefaultTask> allActionAnchorTask = getAndroidTasks().create(tasks,
                new InstantRunAnchorTaskConfigAction(variantScope));

        TransformManager transformManager = variantScope.getTransformManager();

        ExtractJarsTransform extractJarsTransform =
                new ExtractJarsTransform(
                        ImmutableSet.of(DefaultContentType.CLASSES),
                        ImmutableSet.of(Scope.SUB_PROJECTS));
        Optional<AndroidTask<TransformTask>> extractJarsTask =
                transformManager
                        .addTransform(tasks, variantScope, extractJarsTransform);

        InstantRunTaskManager instantRunTaskManager =
                new InstantRunTaskManager(
                        getLogger(),
                        variantScope,
                        variantScope.getTransformManager(),
                        androidTasks,
                        tasks,
                        recorder);

        FileCollection instantRunMergedManifests =
                variantScope.getOutputs(VariantScope.TaskOutputType.INSTANT_RUN_MERGED_MANIFESTS);

        FileCollection processedResources =
                variantScope.getOutputs(VariantScope.TaskOutputType.PROCESSED_RES);

        variantScope.setInstantRunTaskManager(instantRunTaskManager);
        AndroidTask<BuildInfoLoaderTask> buildInfoLoaderTask =
                instantRunTaskManager.createInstantRunAllTasks(
                        variantScope.getGlobalScope().getExtension().getDexOptions(),
                        androidBuilder::getDexByteCodeConverter,
                        extractJarsTask.orElse(null),
                        allActionAnchorTask,
                        getResMergingScopes(variantScope),
                        instantRunMergedManifests,
                        processedResources,
                        true /* addResourceVerifier */,
                        variantScope.getMinSdkForDx());

        if (variantScope.getSourceGenTask() != null) {
            variantScope.getSourceGenTask().dependsOn(tasks, buildInfoLoaderTask);
        }

        return allActionAnchorTask;
    }

    protected void handleJacocoDependencies(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope variantScope) {
        GradleVariantConfiguration config = variantScope.getVariantConfiguration();
        // we add the jacoco jar if coverage is enabled, but we don't add it
        // for test apps as it's already part of the tested app.
        // For library project, since we cannot use the local jars of the library,
        // we add it as well.
        boolean isTestCoverageEnabled =
                config.getBuildType().isTestCoverageEnabled()
                        && getIncrementalMode(variantScope.getVariantConfiguration())
                                == IncrementalMode.NONE
                        && (!config.getType().isForTesting()
                                || (config.getTestedConfig() != null
                                        && config.getTestedConfig().getType()
                                                == VariantType.LIBRARY));
        if (isTestCoverageEnabled) {
            AndroidTask<Copy> agentTask = getJacocoAgentTask(tasks);

            // also add a new stream for the jacoco agent Jar
            variantScope
                    .getTransformManager()
                    .addStream(
                            OriginalStream.builder(project, "jacoco-agent")
                                    .addContentTypes(TransformManager.CONTENT_JARS)
                                    .addScope(Scope.EXTERNAL_LIBRARIES)
                                    .setJar(globalScope.getJacocoAgent())
                                    .setDependency(agentTask.getName())
                                    .build());
        }
    }

    public void createJacocoTransform(
            @NonNull TaskFactory taskFactory,
            @NonNull final VariantScope variantScope) {

        JacocoTransform jacocoTransform = new JacocoTransform(variantScope.getGlobalScope().getJacocoAgent());
        Optional<AndroidTask<TransformTask>> task =
                variantScope
                        .getTransformManager()
                        .addTransform(taskFactory, variantScope, jacocoTransform);

        AndroidTask<Copy> agentTask = getJacocoAgentTask(taskFactory);
        task.ifPresent(t -> t.dependsOn(taskFactory, agentTask));
    }

    public void createJackTask(
            @NonNull final TaskFactory tasks, @NonNull final VariantScope scope) {
        JackOptionsUtils.executeJackChecks(scope.getVariantConfiguration().getJackOptions());

        processPackagedLibsWithJack(tasks, scope);
        processRuntimeLibsWithJack(tasks, scope);

        JackCompileTransform jackCompileTransform = createSourcesJackTransform(scope);
        AndroidTask<TransformTask> jackTask =
                compileSourcesWithJack(tasks, scope, jackCompileTransform);
        if (jackTask == null) {
            // Error adding JackCompileTransform. A SyncIssue was already emitted at this point.
            getLogger().error("Could not create jack transform.", new Throwable());
            return;
        }

        if (jackCompileTransform.getOutputTypes().contains(ExtendedContentType.JACK)) {
            // the sources transform emitted JACK files, we need the final step to generate DEX
            generateDexWithJack(tasks, scope, jackTask);
        }
    }

    private void processRuntimeLibsWithJack(
            @NonNull TaskFactory tasks, @NonNull VariantScope scope) {
        VariantDependencies variantDependency = scope.getVariantDependencies();
        JackPreDexTransform preDexRuntimeTransform =
                new JackPreDexTransform(
                        JackOptionsUtils.forPreDexing(scope),
                        () -> androidBuilder.getBootClasspath(true),
                        androidBuilder::getBuildToolInfo,
                        androidBuilder.getErrorReporter(),
                        androidBuilder.getJavaProcessExecutor(),
                        JackPreDexTransform.InputType.CLASSPATH_LIBRARY,
                        variantDependency.getJackPluginConfiguration());

        scope.getTransformManager().addTransform(
                tasks,
                scope,
                preDexRuntimeTransform,
                (t, task) -> task.dependsOn(variantDependency.getJackPluginConfiguration()));
    }

    private void processPackagedLibsWithJack(
            @NonNull TaskFactory tasks, @NonNull VariantScope scope) {
        VariantDependencies variantDependency = scope.getVariantDependencies();
        JackPreDexTransform preDexPackagedTransform =
                new JackPreDexTransform(
                        JackOptionsUtils.forPreDexing(scope),
                        ImmutableList::of,
                        androidBuilder::getBuildToolInfo,
                        androidBuilder.getErrorReporter(),
                        androidBuilder.getJavaProcessExecutor(),
                        JackPreDexTransform.InputType.PACKAGED_LIBRARY,
                        variantDependency.getJackPluginConfiguration());
        scope.getTransformManager().addTransform(
                tasks,
                scope,
                preDexPackagedTransform,
                (t, task) -> task.dependsOn(variantDependency.getJackPluginConfiguration()));
    }

    /** Creates the transform that will compile the sources with Jack. */
    private JackCompileTransform createSourcesJackTransform(@NonNull VariantScope scope) {
        VariantDependencies variantDependency = scope.getVariantDependencies();
        Boolean includeCompileClasspaths = scope.getVariantConfiguration()
                .getJavaCompileOptions()
                .getAnnotationProcessorOptions()
                .getIncludeCompileClasspath();
        FileCollection processorPaths =
                Boolean.TRUE.equals(includeCompileClasspaths)
                        ? scope.getArtifactFileCollection(COMPILE_CLASSPATH, ALL, CLASSES)
                        : project.files();
        processorPaths =
                processorPaths.plus(
                        scope.getArtifactFileCollection(ANNOTATION_PROCESSOR, ALL, JAR));
        JackCompileTransform jackCompileTransform =
                new JackCompileTransform(
                        JackOptionsUtils.forSourceCompilation(scope),
                        androidBuilder::getBuildToolInfo,
                        androidBuilder.getErrorReporter(),
                        androidBuilder.getJavaProcessExecutor(),
                        processorPaths,
                        variantDependency.getJackPluginConfiguration());
        scope.getVariantData().jackCompileTransform = jackCompileTransform;
        return jackCompileTransform;
    }

    /** Adds {@link JackCompileTransform} transform to the pipeline stream and creates the task. */
    private AndroidTask<TransformTask> compileSourcesWithJack(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope scope,
            @NonNull JackCompileTransform jackCompileTransform) {
        BaseVariantData variantData = scope.getVariantData();
        variantData.setJackCompilationOutput(
                project.files(jackCompileTransform.getJackCompilationOutput()));
        final AndroidTask<TransformTask> jackTask =
                scope.getTransformManager()
                        .addTransform(
                                tasks,
                                scope,
                                jackCompileTransform,
                                (transform, task) -> {
                                    variantData.javaCompilerTask = task;
                                    assert variantData.getJackCompilationOutput() != null;
                                    variantData.getJackCompilationOutput().builtBy(task);
                                })
                        .orElse(null);

        if (jackTask == null) {
            return null;
        }

        // Jack is compiling and also providing the binary and mapping files.
        setJavaCompilerTask(jackTask, tasks, scope);
        setupCompileTaskDependencies(tasks, scope, jackTask);

        jackTask.optionalDependsOn(tasks, scope.getMergeJavaResourcesTask());
        jackTask.dependsOn(tasks, scope.getSourceGenTask());
        jackTask.dependsOn(
                tasks,
                variantData.getVariantDependency().getJackPluginConfiguration(),
                variantData.getVariantDependency().getAnnotationProcessorConfiguration());

        return jackTask;
    }

    /** Creates transform that will consume all JACK files, and produce the final DEX file(s). */
    @NonNull
    private AndroidTask<TransformTask> generateDexWithJack(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope scope,
            @NonNull AndroidTask<TransformTask> jackTask) {
        checkNotNull(scope.getVariantData().getJackCompilationOutput(), "Should be set beforehand");
        checkNotNull(
                scope.getVariantConfiguration().getJackOptions().isJackInProcess(),
                "JackInProcess has a default value.");
        VariantDependencies variantDependency = scope.getVariantDependencies();
        JackGenerateDexTransform jackGenerateDexTransform =
                new JackGenerateDexTransform(
                        JackOptionsUtils.forDexGeneration(scope),
                        scope.getVariantData().getJackCompilationOutput(),
                        androidBuilder::getBuildToolInfo,
                        androidBuilder.getErrorReporter(),
                        androidBuilder.getJavaProcessExecutor(),
                        variantDependency.getJackPluginConfiguration());
        TransformTask.ConfigActionCallback<JackGenerateDexTransform> jackDexTransformCallback =
                (transform, task) -> {
                    scope.getVariantData().mappingFileProviderTask =
                            new FileSupplier() {
                                @NonNull
                                @Override
                                public Task getTask() {
                                    return task;
                                }

                                @Override
                                public File get() {
                                    return transform.getMappingFile();
                                }
                            };
                    task.dependsOn(variantDependency.getJackPluginConfiguration());
                };
        final AndroidTask<TransformTask> jackDexTransformTask =
                scope.getTransformManager()
                        .addTransform(
                                tasks, scope, jackGenerateDexTransform, jackDexTransformCallback)
                        .orElse(null);
        jackDexTransformTask.dependsOn(tasks, jackTask);

        return jackDexTransformTask;
    }

    /**
     * Must be called before the javac task is created so that we it can be earlier in the transform
     * pipeline.
     */
    protected void createDataBindingMergeArtifactsTaskIfNecessary(
            @NonNull TaskFactory tasks, @NonNull VariantScope variantScope) {
        if (!extension.getDataBinding().isEnabled()) {
            return;
        }
        final BaseVariantData variantData = variantScope.getVariantData();
        VariantType type = variantData.getType();
        boolean isTest = type == VariantType.ANDROID_TEST || type == VariantType.UNIT_TEST;
        if (isTest && !extension.getDataBinding().isEnabledForTests()) {
            BaseVariantData testedVariantData = variantScope.getTestedVariantData();
            if (testedVariantData.getType() != LIBRARY) {
                return;
            }
        }
        setDataBindingAnnotationProcessorParams(variantScope);
        AndroidTask<TransformTask> existing = variantScope
                .getDataBindingMergeArtifactsTask();
        if (existing != null) {
            return;
        }
        Optional<AndroidTask<TransformTask>> dataBindingMergeTask;
        dataBindingMergeTask = variantScope
                .getTransformManager()
                .addTransform(tasks, variantScope,
                        new DataBindingMergeArtifactsTransform(getLogger(), variantScope));
        if (dataBindingMergeTask.isPresent()) {
            final AndroidTask<TransformTask> task = dataBindingMergeTask.get();
            variantScope.setDataBindingMergeArtifactsTask(task);

            if (type == VariantType.LIBRARY) {
                // need to publish
                variantScope.publishIntermediateArtifact(
                        variantScope.getBundleFolderForDataBinding(),
                        task.getName(),
                        DATA_BINDING);
            }
        }
    }

    protected void createDataBindingTasksIfNecessary(@NonNull TaskFactory tasks,
            @NonNull VariantScope scope) {
        if (!extension.getDataBinding().isEnabled()) {
            return;
        }
        VariantType type = scope.getVariantData().getType();
        boolean isTest = type == VariantType.ANDROID_TEST || type == VariantType.UNIT_TEST;
        if (isTest && !extension.getDataBinding().isEnabledForTests()) {
            BaseVariantData testedVariantData = scope.getTestedVariantData();
            if (testedVariantData.getType() != LIBRARY) {
                return;
            }
        }

        boolean isJack = scope.getVariantConfiguration().isJackEnabled();
        if (isJack) {
            getLogger().warn("Using Data Binding with Jack compiler is an experimental feature.");
        }
        dataBindingBuilder.setDebugLogEnabled(getLogger().isDebugEnabled());
        AndroidTask<DataBindingProcessLayoutsTask> processLayoutsTask = androidTasks
                .create(tasks, new DataBindingProcessLayoutsTask.ConfigAction(scope));
        scope.setDataBindingProcessLayoutsTask(processLayoutsTask);

        scope.getProcessResourcesTask().dependsOn(tasks, processLayoutsTask);
        processLayoutsTask.dependsOn(tasks, scope.getMergeResourcesTask());

        AndroidTask<DataBindingExportBuildInfoTask> exportBuildInfo = androidTasks
                .create(tasks, new DataBindingExportBuildInfoTask.ConfigAction(scope));

        exportBuildInfo.dependsOn(tasks, processLayoutsTask);
        exportBuildInfo.dependsOn(tasks, scope.getSourceGenTask());

        AndroidTask<? extends Task> javaCompilerTask = scope.getJavaCompilerTask();
        if (javaCompilerTask != null) {
            javaCompilerTask.dependsOn(tasks, exportBuildInfo);
            javaCompilerTask.dependsOn(tasks, scope.getDataBindingMergeArtifactsTask());
        }
    }

    private void setDataBindingAnnotationProcessorParams(@NonNull VariantScope scope) {
        BaseVariantData variantData = scope.getVariantData();
        GradleVariantConfiguration variantConfiguration = variantData.getVariantConfiguration();
        CoreJavaCompileOptions javaCompileOptions = variantConfiguration
                .getJavaCompileOptions();
        CoreAnnotationProcessorOptions processorOptions = javaCompileOptions
                .getAnnotationProcessorOptions();
        if (processorOptions instanceof AnnotationProcessorOptions) {
            AnnotationProcessorOptions ots = (AnnotationProcessorOptions) processorOptions;
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
                artifactVariantData = scope.getTestedVariantData();
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
            DataBindingCompilerArgs args =
                    DataBindingCompilerArgs.builder()
                            .bundleFolder(scope.getBundleFolderForDataBinding())
                            .enabledForTests(extension.getDataBinding().isEnabledForTests())
                            .enableDebugLogs(getLogger().isDebugEnabled())
                            .buildFolder(scope.getBuildFolderForDataBindingCompiler())
                            .sdkDir(scope.getGlobalScope().getSdkHandler().getSdkFolder())
                            .xmlOutDir(scope.getLayoutInfoOutputForDataBinding())
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
     * @param publishApk if true the generated FULL_APK gets published.
     * @param fullBuildInfoGeneratorTask task that generates the build-info.xml for full build.
     */
    public void createPackagingTask(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope variantScope,
            boolean publishApk,
            @Nullable AndroidTask<BuildInfoWriterTask> fullBuildInfoGeneratorTask) {
        ApkVariantData variantData = (ApkVariantData) variantScope.getVariantData();

        boolean signedApk = variantData.isSigned();

        GradleVariantConfiguration variantConfiguration = variantScope.getVariantConfiguration();
        /**
         * PrePackaging step class that will look if the packaging of the main FULL_APK split is
         * necessary when running in InstantRun mode. In InstantRun mode targeting an api 23 or
         * above device, resources are packaged in the main split FULL_APK. However when a warm swap
         * is possible, it is not necessary to produce immediately the new main SPLIT since the
         * runtime use the resources.ap_ file directly. However, as soon as an incompatible change
         * forcing a cold swap is triggered, the main FULL_APK must be rebuilt (even if the
         * resources were changed in a previous build).
         */
        IncrementalMode incrementalMode = getIncrementalMode(variantConfiguration);

        InstantRunPatchingPolicy patchingPolicy =
                variantScope.getInstantRunBuildContext().getPatchingPolicy();

        DefaultGradlePackagingScope packagingScope = new DefaultGradlePackagingScope(variantScope);

        VariantScope.TaskOutputType manifestType =
                variantScope.getInstantRunBuildContext().isInInstantRunMode()
                        ? VariantScope.TaskOutputType.INSTANT_RUN_MERGED_MANIFESTS
                        : VariantScope.TaskOutputType.MERGED_MANIFESTS;

        boolean splitsArePossible =
                variantScope.getSplitScope().getSplitHandlingPolicy()
                        == SplitHandlingPolicy.RELEASE_21_AND_AFTER_POLICY;

        FileCollection manifests = variantScope.getOutputs(manifestType);
        // this is where the final APKs will be located.
        File finalApkLocation =
                new File(globalScope.getApkLocation(), variantConfiguration.getDirName());
        // if we are not dealing with possible splits, we can generate in the final folder
        // directly.
        File outputDirectory =
                splitsArePossible
                        ? variantScope.getFullApkPackagesOutputDirectory()
                        : finalApkLocation;

        TaskOutputHolder.TaskOutputType taskOutputType =
                splitsArePossible
                        ? TaskOutputHolder.TaskOutputType.FULL_APK
                        : TaskOutputHolder.TaskOutputType.APK;

        FileUtils.mkdirs(outputDirectory);
        ConfigurableFileCollection apks = project.files(outputDirectory);
        VariantScope.TaskOutputType resourceFilesInputType =
                variantScope.useResourceShrinker()
                        ? VariantScope.TaskOutputType.SHRUNK_PROCESSED_RES
                        : VariantScope.TaskOutputType.PROCESSED_RES;

        AndroidTask<PackageApplication> packageApp =
                androidTasks.create(
                        tasks,
                        new PackageApplication.StandardConfigAction(
                                packagingScope,
                                outputDirectory,
                                patchingPolicy,
                                resourceFilesInputType,
                                variantScope.getOutputs(resourceFilesInputType),
                                manifests,
                                manifestType,
                                variantScope.getSplitScope(),
                                taskOutputType));
        apks.builtBy(packageApp.getName());
        variantScope.addTaskOutput(taskOutputType, apks);

        AndroidTask<PackageApplication> packageInstantRunResources = null;

        if (variantScope.getInstantRunBuildContext().isInInstantRunMode()) {
            packageInstantRunResources =
                    androidTasks.create(
                            tasks,
                            new PackageApplication.InstantRunResourcesConfigAction(
                                    // FIX ME : this seems incorrect, we only use one per variant instead
                                    // of one per full split.
                                    variantScope.getInstantRunResourcesFile(),
                                    packagingScope,
                                    patchingPolicy,
                                    resourceFilesInputType,
                                    variantScope.getOutputs(resourceFilesInputType),
                                    manifests,
                                    VariantScope.TaskOutputType.INSTANT_RUN_MERGED_MANIFESTS,
                                    variantScope.getSplitScope()));

            // Make sure the MAIN artifact is registered after the RESOURCES one.
            packageApp.dependsOn(tasks, packageInstantRunResources);
        }

        // Common code for both packaging tasks.
        Consumer<AndroidTask<PackageApplication>> configureResourcesAndAssetsDependencies =
                task -> {
                    task.dependsOn(tasks, variantScope.getMergeAssetsTask());
                    task.dependsOn(tasks, variantScope.getProcessResourcesTask());
                };

        configureResourcesAndAssetsDependencies.accept(packageApp);
        if (packageInstantRunResources != null) {
            configureResourcesAndAssetsDependencies.accept(packageInstantRunResources);
        }

        CoreSigningConfig signingConfig = packagingScope.getSigningConfig();

        //noinspection VariableNotUsedInsideIf - we use the whole packaging scope below.
        if (signingConfig != null) {
            packageApp.dependsOn(tasks, getValidateSigningTask(tasks, packagingScope));
        }

        packageApp.optionalDependsOn(
                tasks,
                // FIX ME : Reinstate once ShrinkResourcesTransform is converted.
                // variantOutputScope.getShrinkResourcesTask(),
                // TODO: When Jack is converted, add activeDexTask to VariantScope.
                variantScope.getJavaCompilerTask(),
                // TODO: Remove when Jack is converted to AndroidTask.
                variantData.javaCompilerTask,
                variantData.packageSplitResourcesTask,
                variantData.packageSplitAbiTask);

        variantScope.setPackageApplicationTask(packageApp);
        variantScope.getAssembleTask().dependsOn(tasks, packageApp.getName());

            checkState(variantScope.getAssembleTask() != null);
            if (fullBuildInfoGeneratorTask != null) {
                AndroidTask<PackageApplication> finalPackageInstantRunResources =
                        packageInstantRunResources;
            fullBuildInfoGeneratorTask.configure(
                    tasks,
                    task -> {
                        task.mustRunAfter(packageApp.getName());
                        if (finalPackageInstantRunResources != null) {
                            task.mustRunAfter(finalPackageInstantRunResources.getName());
                        }
                    });
                variantScope.getAssembleTask().dependsOn(
                        tasks, fullBuildInfoGeneratorTask.getName());
            }

        if (splitsArePossible) {

            AndroidTask<CopyOutputs> copyOutputsTask =
                    androidTasks.create(
                            tasks,
                            new CopyOutputs.ConfigAction(
                                    new DefaultGradlePackagingScope(variantScope),
                                    finalApkLocation));
            apks =
                    variantScope.addTaskOutput(
                            TaskOutputHolder.TaskOutputType.APK,
                            finalApkLocation,
                            copyOutputsTask.getName());
            variantScope.getAssembleTask().dependsOn(tasks, copyOutputsTask);
        }

        if (publishApk) {
            variantScope.publishIntermediateArtifact(
                    finalApkLocation,
                    Joiner.on(",").join(apks.getBuiltBy().stream().collect(Collectors.toList())),
                    AndroidArtifacts.ArtifactType.APK);

            final FileSupplier mappingFileProvider = variantData.getMappingFileProvider();
            if (mappingFileProvider != null) {
                final File mappingFile = mappingFileProvider.get();
                if (mappingFile != null) {
                    variantScope.publishIntermediateArtifact(
                            mappingFile,
                            mappingFileProvider.getTask().getName(),
                            AndroidArtifacts.ArtifactType.APK_MAPPING);
                }
            }
        }

        // create install task for the variant Data. This will deal with finding the
        // right output if there are more than one.
        // Add a task to install the application package
        if (signedApk) {
            AndroidTask<InstallVariantTask> installTask = androidTasks.create(
                    tasks, new InstallVariantTask.ConfigAction(variantScope));
            installTask.dependsOn(tasks, variantScope.getAssembleTask());
        }

        if (incrementalMode == IncrementalMode.NONE) {
            maybeCreateLintVitalTask(tasks, variantData);
        }

        // add an uninstall task
        final AndroidTask<UninstallTask> uninstallTask = androidTasks.create(
                tasks, new UninstallTask.ConfigAction(variantScope));

        tasks.named(UNINSTALL_ALL, uninstallAll -> uninstallAll.dependsOn(uninstallTask.getName()));
    }

    protected AndroidTask<?> getValidateSigningTask(
            @NonNull TaskFactory tasks, @NonNull PackagingScope packagingScope) {
        ValidateSigningTask.ConfigAction configAction =
                new ValidateSigningTask.ConfigAction(packagingScope);

        AndroidTask<?> validateSigningTask = androidTasks.get(configAction.getName());
        if (validateSigningTask == null) {
            validateSigningTask = androidTasks.create(tasks, configAction);
        }
        return validateSigningTask;
    }

    public AndroidTask<DefaultTask> createAssembleTask(
            @NonNull TaskFactory tasks, @NonNull final BaseVariantData variantData) {
        return androidTasks.create(
                tasks,
                variantData.getScope().getTaskName("assemble"),
                task -> {
                    variantData.addTask(TaskContainer.TaskKind.ASSEMBLE, task);
                });
    }

    @NonNull
    public AndroidTask<DefaultTask> createAssembleTask(
            @NonNull TaskFactory tasks,
            @NonNull VariantDimensionData dimensionData) {
        final String sourceSetName =
                StringHelper.capitalize(dimensionData.getSourceSet().getName());
        return androidTasks.create(
                tasks,
                "assemble" + sourceSetName,
                assembleTask -> {
                    assembleTask.setDescription("Assembles all " + sourceSetName + " builds.");
                    assembleTask.setGroup(BasePlugin.BUILD_GROUP);
                });
    }

    public AndroidTask<Copy> getJacocoAgentTask(TaskFactory tasks) {
        if (jacocoAgentTask == null) {
            jacocoAgentTask = androidTasks.create(tasks, new JacocoAgentConfigAction(globalScope));
        }
        return jacocoAgentTask;
    }

    protected void maybeCreateJavaCodeShrinkerTransform(
            @NonNull TaskFactory taskFactory, @NonNull final VariantScope variantScope) {
        CodeShrinker codeShrinker = variantScope.getCodeShrinker();

        if (codeShrinker != null) {
            doCreateJavaCodeShrinkerTransform(
                    taskFactory,
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
            @NonNull TaskFactory taskFactory,
            @NonNull final VariantScope variantScope,
            @NonNull CodeShrinker codeShrinker,
            @Nullable FileCollection mappingFileCollection) {
        switch (codeShrinker) {
            case PROGUARD:
                createProguardTransform(taskFactory, variantScope, mappingFileCollection);
                break;
            case ANDROID_GRADLE:
                // Since the built-in class shrinker does not obfuscate, there's no point running
                // it on the test FULL_APK (it also doesn't have a -dontshrink mode).
                if (variantScope.getTestedVariantData() == null) {
                    createBuiltInShrinkerTransform(variantScope, taskFactory);
                }
                break;
            default:
                throw new AssertionError("Unknown value " + codeShrinker);
        }
    }

    private void createBuiltInShrinkerTransform(VariantScope scope, TaskFactory taskFactory) {
        BuiltInShrinkerTransform transform = new BuiltInShrinkerTransform(scope);
        applyProguardConfig(transform, scope);

        if (getIncrementalMode(scope.getVariantConfiguration()) != IncrementalMode.NONE) {
            //TODO: This is currently overly broad, as finding the actual application class
            //      requires manually parsing the manifest, see
            //      aapt -D (getMainDexListProguardOutputFile)
            transform.keep("class ** extends android.app.Application {*;}");
            transform.keep("class com.android.tools.fd.** {*;}");
        }

        scope.getTransformManager().addTransform(taskFactory, scope, transform);
    }

    private void createProguardTransform(
            @NonNull TaskFactory taskFactory,
            @NonNull VariantScope variantScope,
            @Nullable FileCollection mappingFileCollection) {
        if (getIncrementalMode(variantScope.getVariantConfiguration()) != IncrementalMode.NONE) {
            logger.warn(
                    "ProGuard is disabled for variant {} because it is not compatible with Instant Run. See "
                            + "http://d.android.com/r/studio-ui/shrink-code-with-ir.html "
                            + "for details on how to enable a code shrinker that's compatible with Instant Run.",
                    variantScope.getVariantConfiguration().getFullName());
            return;
        }

        final BaseVariantData variantData = variantScope.getVariantData();
        final GradleVariantConfiguration variantConfig = variantData.getVariantConfiguration();
        final BaseVariantData testedVariantData = variantScope.getTestedVariantData();

        ProGuardTransform transform = new ProGuardTransform(variantScope);

        if (testedVariantData != null) {
            applyProguardDefaultsForTest(transform);
            // All -dontwarn rules for test dependencies should go in here:
            transform.setConfigurationFiles(
                    project.files(
                            TaskInputHelper.bypassFileCallable(
                                    testedVariantData.getVariantConfiguration()
                                            ::getTestProguardFiles)));

            // register the mapping file which may or may not exists (only exist if obfuscation)
            // is enabled.
            transform.applyTestedMapping(testedVariantData.getMappingFile());
        } else if (isTestedAppObfuscated(variantScope)) {
            applyProguardDefaultsForTest(transform);
            // All -dontwarn rules for test dependencies should go in here:
            transform.setConfigurationFiles(
                    project.files(
                            TaskInputHelper.bypassFileCallable(
                                    variantConfig::getTestProguardFiles)));
            transform.applyTestedMapping(mappingFileCollection);
        } else {
            applyProguardConfig(transform, variantScope);

            if (mappingFileCollection != null) {
                transform.applyTestedMapping(mappingFileCollection);
            }
        }

        TransformTask.ConfigActionCallback<ProGuardTransform> proGuardTransformCallback =
                (proGuardTransform, proGuardTask) ->
                        variantData.mappingFileProviderTask =
                                new FileSupplier() {
                                    @NonNull
                                    @Override
                                    public Task getTask() {
                                        return proGuardTask;
                                    }

                                    @Override
                                    public File get() {
                                        return proGuardTransform.getMappingFile();
                                    }
                                };
        Optional<AndroidTask<TransformTask>> task =
                variantScope
                        .getTransformManager()
                        .addTransform(
                                taskFactory,
                                variantScope,
                                transform,
                                proGuardTransformCallback);

        // FIXME remove once the transform support secondary file as a FileCollection.
        task.ifPresent(
                t -> {
                    t.optionalDependsOn(taskFactory, mappingFileCollection);

                    if (testedVariantData != null) {
                        // We need the mapping file for the app code to exist by the time we run.
                        t.dependsOn(taskFactory, testedVariantData.getScope().getAssembleTask());
                    }
                });
    }

    private static void applyProguardDefaultsForTest(ProGuardTransform transform) {
        // Don't remove any code in tested app.
        transform.dontshrink();
        transform.dontoptimize();

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
    protected void maybeCreateResourcesShrinkerTransform(
            @NonNull TaskFactory taskFactory, @NonNull VariantScope scope) {
        if (!scope.useResourceShrinker()) {
            return;
        }

        // if resources are shrink, insert a no-op transform per variant output
        // to transform the res package into a stripped res package

        ShrinkResourcesTransform shrinkResTransform =
                new ShrinkResourcesTransform(
                        scope.getVariantData(),
                        scope.getOutputs(TaskOutputHolder.TaskOutputType.PROCESSED_RES),
                        scope.getShrunkProcessedResourcesOutputDirectory(),
                        androidBuilder,
                        AaptGeneration.fromProjectOptions(projectOptions),
                        scope.getOutputs(TaskOutputHolder.TaskOutputType.SPLIT_LIST),
                        logger);

        Optional<AndroidTask<TransformTask>> shrinkTask =
                scope.getTransformManager().addTransform(taskFactory, scope, shrinkResTransform);

        if (shrinkTask.isPresent()) {
            scope.addTaskOutput(
                    TaskOutputHolder.TaskOutputType.SHRUNK_PROCESSED_RES,
                    scope.getShrunkProcessedResourcesOutputDirectory(),
                    shrinkTask.get().getName());
        } else {
            androidBuilder
                    .getErrorReporter()
                    .handleSyncError(
                            null,
                            SyncIssue.TYPE_GENERIC,
                            "Internal error, could not add the ShrinkResourcesTransform");
        }
    }

    private void applyProguardConfig(
            ProguardConfigurable transform,
            VariantScope scope) {
        final BaseVariantData variantData = scope.getVariantData();
        final GradleVariantConfiguration variantConfig = scope.getVariantConfiguration();
        transform.setConfigurationFiles(
                project.files(
                        TaskInputHelper.bypassFileCallable(
                                () -> {
                                    Set<File> proguardFiles =
                                            variantConfig.getProguardFiles(
                                                    Collections.singletonList(
                                                            ProguardFiles.getDefaultProguardFile(
                                                                    TaskManager
                                                                            .DEFAULT_PROGUARD_CONFIG_FILE,
                                                                    project)));

                                    // use the first output when looking for the proguard rule output of
                                    // the aapt task. The different outputs are not different in a way that
                                    // makes this rule file different per output.
                                    proguardFiles.add(
                                            variantData
                                                    .getScope()
                                                    .getProcessAndroidResourcesProguardOutputFile());
                                    return proguardFiles;
                                }),
                        scope.getArtifactFileCollection(RUNTIME_CLASSPATH, ALL, PROGUARD_RULES)));

        if (variantData.getType() == LIBRARY) {
            transform.keep("class **.R");
            transform.keep("class **.R$*");
        }

        if (variantData.getVariantConfiguration().isTestCoverageEnabled()) {
            // when collecting coverage, don't remove the JaCoCo runtime
            transform.keep("class com.vladium.** {*;}");
            transform.keep("class org.jacoco.** {*;}");
            transform.keep("interface org.jacoco.** {*;}");
            transform.dontwarn("org.jacoco.**");
        }
    }

    public void createReportTasks(TaskFactory tasks, final List<VariantScope> variantScopes) {
        AndroidTask<DependencyReportTask> dependencyReportTask =
                androidTasks.create(
                        tasks,
                        "androidDependencies",
                        DependencyReportTask.class,
                        task -> {
                            task.setDescription(
                                    "Displays the Android dependencies of the project.");
                            task.setVariants(variantScopes);
                            task.setGroup(ANDROID_GROUP);
                        });

        androidTasks.create(
                tasks,
                "signingReport",
                SigningReportTask.class,
                task -> {
                    task.setDescription("Displays the signing info for each variant.");
                    task.setVariants(variantScopes);
                    task.setGroup(ANDROID_GROUP);
                });
    }

    public void createAnchorTasks(@NonNull TaskFactory tasks, @NonNull VariantScope scope) {
        createPreBuildTasks(tasks, scope);

        // also create sourceGenTask
        final BaseVariantData variantData = scope.getVariantData();
        scope.setSourceGenTask(androidTasks.create(tasks,
                scope.getTaskName("generate", "Sources"),
                Task.class,
                task -> {
                    variantData.sourceGenTask = task;
                }));
        // and resGenTask
        scope.setResourceGenTask(androidTasks.create(tasks,
                scope.getTaskName("generate", "Resources"),
                Task.class,
                task -> {
                    variantData.resourceGenTask = task;
                }));

        scope.setAssetGenTask(androidTasks.create(tasks,
                scope.getTaskName("generate", "Assets"),
                Task.class,
                task -> {
                    variantData.assetGenTask = task;
                }));

        if (!variantData.getType().isForTesting()
                && variantData.getVariantConfiguration().getBuildType().isTestCoverageEnabled()) {
            scope.setCoverageReportTask(androidTasks.create(tasks,
                    scope.getTaskName("create", "CoverageReport"),
                    Task.class,
                    task -> {
                        task.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
                        task.setDescription(String.format(
                                "Creates test coverage reports for the %s variant.",
                                variantData.getName()));
                    }));
        }

        // and compile task
        createCompileAnchorTask(tasks, scope);
    }

    protected AndroidTask<? extends DefaultTask> createVariantPreBuildTask(
            @NonNull TaskFactory tasks, @NonNull VariantScope scope) {
        // default pre-built task.
        return createDefaultPreBuildTask(tasks, scope);
    }

    protected AndroidTask<? extends DefaultTask> createDefaultPreBuildTask(
            @NonNull TaskFactory tasks, @NonNull VariantScope scope) {
        return getAndroidTasks()
                .create(
                        tasks,
                        scope.getTaskName("pre", "Build"),
                        task -> {
                            scope.getVariantData().preBuildTask = task;
                        });
    }

    private void createPreBuildTasks(@NonNull TaskFactory tasks, @NonNull VariantScope scope) {
        scope.setPreBuildTask(createVariantPreBuildTask(tasks, scope));

        scope.getPreBuildTask().dependsOn(tasks, MAIN_PREBUILD);

        if (runJavaCodeShrinker(scope)) {
            scope.getPreBuildTask().dependsOn(tasks, EXTRACT_PROGUARD_FILES);
        }
    }

    private void createCompileAnchorTask(
            @NonNull TaskFactory tasks, @NonNull final VariantScope scope) {
        final BaseVariantData variantData = scope.getVariantData();
        scope.setCompileTask(androidTasks.create(tasks, new TaskConfigAction<Task>() {
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
        scope.getAssembleTask().dependsOn(tasks, scope.getCompileTask());
    }

    public void createCheckManifestTask(@NonNull TaskFactory tasks, @NonNull VariantScope scope) {
        scope.setCheckManifestTask(
                androidTasks.create(tasks, getCheckManifestConfig(scope)));
        scope.getCheckManifestTask().dependsOn(tasks, scope.getPreBuildTask());
        // Does
    }

    protected CheckManifest.ConfigAction getCheckManifestConfig(@NonNull VariantScope scope) {
        return new CheckManifest.ConfigAction(scope, false);
    }

    @NonNull
    protected Logger getLogger() {
        return logger;
    }

    @NonNull
    public AndroidTaskRegistry getAndroidTasks() {
        return androidTasks;
    }

    public void addDataBindingDependenciesIfNecessary(DataBindingOptions options) {
        if (!options.isEnabled()) {
            return;
        }

        String version = MoreObjects.firstNonNull(options.getVersion(),
                dataBindingBuilder.getCompilerVersion());
        project.getDependencies().add("compile", SdkConstants.DATA_BINDING_LIB_ARTIFACT + ":"
                + dataBindingBuilder.getLibraryVersion(version));
        project.getDependencies().add("compile", SdkConstants.DATA_BINDING_BASELIB_ARTIFACT + ":"
                + dataBindingBuilder.getBaseLibraryVersion(version));

        // TODO load config name from source sets
        project.getDependencies()
                .add(
                        "annotationProcessor",
                        SdkConstants.DATA_BINDING_ANNOTATION_PROCESSOR_ARTIFACT + ":" + version);
        if (options.isEnabledForTests()) {
            project.getDependencies().add("androidTestAnnotationProcessor",
                    SdkConstants.DATA_BINDING_ANNOTATION_PROCESSOR_ARTIFACT + ":" +
                            version);
        }
        if (options.getAddDefaultAdapters()) {
            project.getDependencies()
                    .add(
                            "compile",
                            SdkConstants.DATA_BINDING_ADAPTER_LIB_ARTIFACT
                                    + ":"
                                    + dataBindingBuilder.getBaseAdaptersVersion(version));
        }
    }

    protected void configureTestData(AbstractTestDataImpl testData) {
        testData.setAnimationsDisabled(extension.getTestOptions().getAnimationsDisabled());
    }
}
