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

import static com.android.SdkConstants.FN_SPLIT_LIST;
import static com.android.build.OutputFile.DENSITY;
import static com.android.builder.core.BuilderConstants.CONNECTED;
import static com.android.builder.core.BuilderConstants.DEVICE;
import static com.android.builder.core.VariantType.ANDROID_TEST;
import static com.android.builder.core.VariantType.LIBRARY;
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
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.coverage.JacocoPlugin;
import com.android.build.gradle.internal.coverage.JacocoReportTask;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.dsl.AbiSplitOptions;
import com.android.build.gradle.internal.dsl.AnnotationProcessorOptions;
import com.android.build.gradle.internal.dsl.CoreAnnotationProcessorOptions;
import com.android.build.gradle.internal.dsl.CoreBuildType;
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
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.DefaultGradlePackagingScope;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.PackagingScope;
import com.android.build.gradle.internal.scope.SplitList;
import com.android.build.gradle.internal.scope.SupplierTask;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantOutputScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.AndroidReportTask;
import com.android.build.gradle.internal.tasks.CheckManifest;
import com.android.build.gradle.internal.tasks.DependencyReportTask;
import com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask;
import com.android.build.gradle.internal.tasks.ExtractProguardFiles;
import com.android.build.gradle.internal.tasks.FileSupplier;
import com.android.build.gradle.internal.tasks.GenerateApkDataTask;
import com.android.build.gradle.internal.tasks.InstallVariantTask;
import com.android.build.gradle.internal.tasks.JackJacocoReportTask;
import com.android.build.gradle.internal.tasks.LintCompile;
import com.android.build.gradle.internal.tasks.MockableAndroidJarTask;
import com.android.build.gradle.internal.tasks.ResolveDependenciesTask;
import com.android.build.gradle.internal.tasks.SigningReportTask;
import com.android.build.gradle.internal.tasks.SourceSetsTask;
import com.android.build.gradle.internal.tasks.TestServerTask;
import com.android.build.gradle.internal.tasks.UninstallTask;
import com.android.build.gradle.internal.tasks.ValidateSigningTask;
import com.android.build.gradle.internal.tasks.databinding.DataBindingExportBuildInfoTask;
import com.android.build.gradle.internal.tasks.databinding.DataBindingMergeArtifactsTransform;
import com.android.build.gradle.internal.tasks.databinding.DataBindingProcessLayoutsTask;
import com.android.build.gradle.internal.test.AbstractTestDataImpl;
import com.android.build.gradle.internal.test.TestDataImpl;
import com.android.build.gradle.internal.transforms.CustomClassTransform;
import com.android.build.gradle.internal.transforms.DexTransform;
import com.android.build.gradle.internal.transforms.DexingMode;
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
import com.android.build.gradle.internal.transforms.NewShrinkerTransform;
import com.android.build.gradle.internal.transforms.PreDexTransform;
import com.android.build.gradle.internal.transforms.ProGuardTransform;
import com.android.build.gradle.internal.transforms.ProguardConfigurable;
import com.android.build.gradle.internal.transforms.ShrinkResourcesTransform;
import com.android.build.gradle.internal.transforms.StripDebugSymbolTransform;
import com.android.build.gradle.internal.variant.AndroidArtifactVariantData;
import com.android.build.gradle.internal.variant.ApkVariantData;
import com.android.build.gradle.internal.variant.ApkVariantOutputData;
import com.android.build.gradle.internal.variant.ApplicationVariantData;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.build.gradle.internal.variant.SplitHandlingPolicy;
import com.android.build.gradle.internal.variant.TestVariantData;
import com.android.build.gradle.tasks.AidlCompile;
import com.android.build.gradle.tasks.CleanBuildCache;
import com.android.build.gradle.tasks.CompatibleScreensManifest;
import com.android.build.gradle.tasks.ExternalNativeBuildJsonTask;
import com.android.build.gradle.tasks.ExternalNativeBuildTask;
import com.android.build.gradle.tasks.ExternalNativeBuildTaskUtils;
import com.android.build.gradle.tasks.ExternalNativeCleanTask;
import com.android.build.gradle.tasks.ExternalNativeJsonGenerator;
import com.android.build.gradle.tasks.GenerateBuildConfig;
import com.android.build.gradle.tasks.GenerateResValues;
import com.android.build.gradle.tasks.GenerateSplitAbiRes;
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
import com.android.build.gradle.tasks.SplitZipAlign;
import com.android.build.gradle.tasks.SplitsDiscovery;
import com.android.build.gradle.tasks.ZipAlign;
import com.android.build.gradle.tasks.factory.AndroidUnitTest;
import com.android.build.gradle.tasks.factory.IncrementalSafeguard;
import com.android.build.gradle.tasks.factory.JacocoAgentConfigAction;
import com.android.build.gradle.tasks.factory.JavaCompileConfigAction;
import com.android.build.gradle.tasks.factory.ProcessJavaResConfigAction;
import com.android.build.gradle.tasks.factory.TestServerTaskConfigAction;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.DefaultDexOptions;
import com.android.builder.core.VariantConfiguration;
import com.android.builder.core.VariantType;
import com.android.builder.dependency.level2.AndroidDependency;
import com.android.builder.model.DataBindingOptions;
import com.android.builder.model.OptionalCompilationStep;
import com.android.builder.model.SyncIssue;
import com.android.builder.profile.Recorder;
import com.android.builder.testing.ConnectedDeviceProvider;
import com.android.builder.testing.api.DeviceProvider;
import com.android.builder.testing.api.TestServer;
import com.android.builder.utils.FileCache;
import com.android.ide.common.build.SplitOutputMatcher;
import com.android.manifmerger.ManifestMerger2;
import com.android.resources.Density;
import com.android.sdklib.AndroidVersion;
import com.android.utils.StringHelper;
import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import groovy.lang.Closure;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
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
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/**
 * Manages tasks creation.
 */
public abstract class TaskManager {

    public static final String DEFAULT_PROGUARD_CONFIG_FILE = "proguard-android.txt";

    public static final String DIR_BUNDLES = "bundles";

    public static final String DIR_ATOMBUNDLES = "atombundles";

    public static final String INSTALL_GROUP = "Install";

    public static final String BUILD_GROUP = BasePlugin.BUILD_GROUP;

    public static final String ANDROID_GROUP = "Android";

    protected Project project;

    protected AndroidBuilder androidBuilder;

    protected DataBindingBuilder dataBindingBuilder;

    private DependencyManager dependencyManager;

    protected SdkHandler sdkHandler;

    protected AndroidConfig extension;

    protected ToolingModelBuilderRegistry toolingRegistry;

    private final GlobalScope globalScope;

    private final AndroidTaskRegistry androidTasks = new AndroidTaskRegistry();

    private Logger logger;

    protected final Recorder recorder;

    protected boolean isComponentModelPlugin = false;

    // Task names
    // These cannot be AndroidTasks as in the component model world there is nothing to force
    // generateTasksBeforeEvaluate to happen before the variant tasks are created.
    private static final String MAIN_PREBUILD = "preBuild";

    private static final String UNINSTALL_ALL = "uninstallAll";

    private static final String DEVICE_CHECK = "deviceCheck";

    private static final String DEVICE_ANDROID_TEST = DEVICE + ANDROID_TEST.getSuffix();

    protected static final String CONNECTED_CHECK = "connectedCheck";

    private static final String CONNECTED_ANDROID_TEST = CONNECTED + ANDROID_TEST.getSuffix();

    private static final String ASSEMBLE_ANDROID_TEST = "assembleAndroidTest";

    private static final String SOURCE_SETS = "sourceSets";

    public static final String LINT = "lint";

    protected static final String LINT_COMPILE = "compileLint";

    private static final String EXTRACT_PROGUARD_FILES = "extractProguardFiles";

    public static final String ATOM_SUFFIX = "Atom";

    // Tasks
    private AndroidTask<Copy> jacocoAgentTask;

    public AndroidTask<MockableAndroidJarTask> createMockableJar;

    public TaskManager(
            @NonNull Project project,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull DataBindingBuilder dataBindingBuilder,
            @NonNull AndroidConfig extension,
            @NonNull SdkHandler sdkHandler,
            @NonNull NdkHandler ndkHandler,
            @NonNull DependencyManager dependencyManager,
            @NonNull ToolingModelBuilderRegistry toolingRegistry,
            @NonNull Recorder recorder) {
        this.project = project;
        this.androidBuilder = androidBuilder;
        this.dataBindingBuilder = dataBindingBuilder;
        this.sdkHandler = sdkHandler;
        this.extension = extension;
        this.toolingRegistry = toolingRegistry;
        this.dependencyManager = dependencyManager;
        this.recorder = recorder;
        logger = Logging.getLogger(this.getClass());

        globalScope = new GlobalScope(
                project,
                androidBuilder,
                extension,
                sdkHandler,
                ndkHandler,
                toolingRegistry);
    }

    private boolean isDebugLog() {
        return project.getLogger().isEnabled(LogLevel.DEBUG);
    }

    private boolean isInfoLog() {
        return project.getLogger().isEnabled(LogLevel.INFO);
    }

    public DataBindingBuilder getDataBindingBuilder() {
        return dataBindingBuilder;
    }

    public DependencyManager getDependencyManager() {
        return dependencyManager;
    }

    /**
     * Creates the tasks for a given BaseVariantData.
     */
    public abstract void createTasksForVariantData(
            @NonNull TaskFactory tasks,
            @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData);

    public GlobalScope getGlobalScope() {
        return globalScope;
    }

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

    protected AndroidConfig getExtension() {
        return extension;
    }

    public void resolveDependencies(
            @NonNull VariantDependencies variantDeps,
            @Nullable String testedProjectPath) {
        Set<AndroidDependency> libsToExplode =
                dependencyManager.resolveDependencies(
                        variantDeps, testedProjectPath, getGlobalScope().getBuildCache());
        dependencyManager.processLibraries(libsToExplode, getGlobalScope().getBuildCache());
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

        tasks.named(JavaBasePlugin.CHECK_TASK_NAME, it -> it.dependsOn(LINT));

        androidTasks.create(tasks, new LintCompile.ConfigAction(globalScope));

        if (getGlobalScope().getBuildCache() != null) {
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
        createMockableJar = androidTasks.create(tasks, new MockableAndroidJarTask.ConfigAction(globalScope));
    }

    protected void createDependencyStreams(
            @NonNull TaskFactory tasks,
            @NonNull final VariantScope variantScope) {
        BaseVariantData<? extends BaseVariantOutputData> variantData = variantScope.getVariantData();

        TransformManager transformManager = variantScope.getTransformManager();

        transformManager.addStream(OriginalStream.builder(project)
                .addContentTypes(TransformManager.CONTENT_FULL_JAR)
                .addScope(Scope.PROJECT_LOCAL_DEPS)
                .setJars(variantScope.getLocalPackagedJars())
                .build());

        transformManager.addStream(OriginalStream.builder(project)
                .addContentTypes(TransformManager.CONTENT_CLASS)
                .addScope(Scope.EXTERNAL_LIBRARIES)
                .setFileCollection(variantScope.getArtifactFileCollection(
                        AndroidArtifacts.ConfigType.PACKAGE,
                        AndroidArtifacts.ArtifactScope.EXTERNAL,
                        AndroidArtifacts.ArtifactType.CLASSES))
                .build());

        transformManager.addStream(OriginalStream.builder(project)
                .addContentTypes(DefaultContentType.RESOURCES, ExtendedContentType.NATIVE_LIBS)
                .addScope(Scope.EXTERNAL_LIBRARIES)
                .setFileCollection(variantScope.getArtifactFileCollection(
                        AndroidArtifacts.ConfigType.PACKAGE,
                        AndroidArtifacts.ArtifactScope.EXTERNAL,
                        AndroidArtifacts.ArtifactType.JAVA_RES))
                .build());

        // and the android AAR also have a specific jni folder
        transformManager.addStream(OriginalStream.builder(project)
                .addContentTypes(TransformManager.CONTENT_NATIVE_LIBS)
                .addScope(Scope.EXTERNAL_LIBRARIES)
                .setFileCollection(variantScope.getArtifactFileCollection(
                        AndroidArtifacts.ConfigType.PACKAGE,
                        AndroidArtifacts.ArtifactScope.EXTERNAL,
                        AndroidArtifacts.ArtifactType.JNI))
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
                    variantData.getVariantDependency().getPackageConfiguration());

            transformManager.addStream(
                    OriginalStream.builder(project)
                            .addContentTypes(ExtendedContentType.JAVA_SOURCES)
                            .addScope(Scope.PROJECT)
                            .setFileCollection(sourcesCollection)
                            .build());
        }

        // data binding related artifacts for external libs
        if (extension.getDataBinding().isEnabled()) {
            transformManager.addStream(OriginalStream.builder(project)
                    .addContentTypes(TransformManager.DATA_BINDING_ARTIFACT)
                    .addScope(Scope.SUB_PROJECTS)
                    .setFileCollection(variantScope.getArtifactFileCollection(
                            AndroidArtifacts.ConfigType.COMPILE,
                            AndroidArtifacts.ArtifactScope.MODULE,
                            AndroidArtifacts.ArtifactType.DATA_BINDING))
                    .build()
            );
            transformManager.addStream(OriginalStream.builder(project)
                    .addContentTypes(TransformManager.DATA_BINDING_ARTIFACT)
                    .addScope(Scope.EXTERNAL_LIBRARIES)
                    .setFileCollection(variantScope.getArtifactFileCollection(
                            AndroidArtifacts.ConfigType.COMPILE,
                            AndroidArtifacts.ArtifactScope.EXTERNAL,
                            AndroidArtifacts.ArtifactType.DATA_BINDING))
                    .build()
            );
        }

        // for the sub modules, new intermediary classes artifact has its own stream
        transformManager.addStream(OriginalStream.builder(project)
                .addContentTypes(TransformManager.CONTENT_CLASS)
                .addScope(Scope.SUB_PROJECTS)
                .setFileCollection(variantScope.getArtifactFileCollection(
                        AndroidArtifacts.ConfigType.PACKAGE,
                        AndroidArtifacts.ArtifactScope.MODULE,
                        AndroidArtifacts.ArtifactType.CLASSES))
                .build());

        // same for the resources which can be java-res or jni
        transformManager.addStream(OriginalStream.builder(project)
                .addContentTypes(DefaultContentType.RESOURCES, ExtendedContentType.NATIVE_LIBS)
                .addScope(Scope.SUB_PROJECTS)
                .setFileCollection(variantScope.getArtifactFileCollection(
                        AndroidArtifacts.ConfigType.PACKAGE,
                        AndroidArtifacts.ArtifactScope.MODULE,
                        AndroidArtifacts.ArtifactType.JAVA_RES))
                .build());

        // and the android library sub-modules also have a specific jni folder
        transformManager.addStream(OriginalStream.builder(project)
                .addContentTypes(TransformManager.CONTENT_NATIVE_LIBS)
                .addScope(Scope.SUB_PROJECTS)
                .setFileCollection(variantScope.getArtifactFileCollection(
                        AndroidArtifacts.ConfigType.PACKAGE,
                        AndroidArtifacts.ArtifactScope.MODULE,
                        AndroidArtifacts.ArtifactType.JNI))
                .build());

        // provided only scopes.
        transformManager.addStream(OriginalStream.builder(project)
                .addContentTypes(TransformManager.CONTENT_CLASS)
                .addScope(Scope.PROVIDED_ONLY)
                .setFileCollection(variantScope.getProvidedOnlyClasspath())
                .build());

        if (variantScope.getTestedVariantData() != null) {
            final BaseVariantData testedVariantData = variantScope.getTestedVariantData();

            VariantScope testedVariantScope = testedVariantData.getScope();

            if (!variantScope.isJackEnabled()) {
                // create two streams of different types.
                transformManager.addStream(OriginalStream.builder(project)
                        .addContentTypes(DefaultContentType.CLASSES)
                        .addScope(Scope.TESTED_CODE)
                        .setFolders(() -> ImmutableList.of(testedVariantScope.getJavaOutputDir()))
                        .setDependency(testedVariantScope.getJavacTask().getName())
                        .build());
            } else {
                FileCollection testedAppJack = testedVariantData.getJackCompilationOutput();
                checkNotNull(testedAppJack, "Tested variant has no jack output specified.");
                transformManager.addStream(
                        OriginalStream.builder(project)
                                .addContentType(ExtendedContentType.JACK)
                                .addScope(Scope.TESTED_CODE)
                                .setFileCollection(testedAppJack)
                                .build());
            }

            transformManager.addStream(
                    OriginalStream.builder(project)
                            .addContentTypes(DefaultContentType.CLASSES)
                            .addScope(Scope.TESTED_CODE)
                            .setFileCollection(testedVariantScope.getArtifactFileCollection(
                                    AndroidArtifacts.ConfigType.PACKAGE,
                                    AndroidArtifacts.ArtifactScope.ALL,
                                    AndroidArtifacts.ArtifactType.CLASSES))
                            .build());
        }

        handleJacocoDependencies(tasks, variantScope);
    }

    public void createMergeAtomManifestsTask(
            @NonNull TaskFactory tasks, @NonNull VariantScope variantScope) {
        AndroidArtifactVariantData<?> androidArtifactVariantData =
                (AndroidArtifactVariantData) variantScope.getVariantData();
        Set<String> screenSizes = androidArtifactVariantData.getCompatibleScreens();

        // Get the single output.
        final BaseVariantOutputData variantOutputData =
                androidArtifactVariantData.getOutputs().get(0);
        VariantOutputScope variantOutputScope = variantOutputData.getScope();

        AndroidTask<CompatibleScreensManifest> csmTask = null;
        if (variantOutputData.getMainOutputFile().getFilter(DENSITY) != null) {
            csmTask =
                    androidTasks.create(
                            tasks,
                            new CompatibleScreensManifest.ConfigAction(
                                    variantOutputScope, screenSizes));
            variantOutputScope.setCompatibleScreensManifestTask(csmTask);
        }

        ImmutableList.Builder<ManifestMerger2.Invoker.Feature> optionalFeatures =
                ImmutableList.builder();
        if (getIncrementalMode(variantScope.getVariantConfiguration()) != IncrementalMode.NONE) {
            optionalFeatures.add(ManifestMerger2.Invoker.Feature.INSTANT_RUN_REPLACEMENT);
        }
        if (AndroidGradleOptions.getTestOnly(project)) {
            optionalFeatures.add(ManifestMerger2.Invoker.Feature.TEST_ONLY);
        }

        AndroidTask<? extends ManifestProcessorTask> processManifestTask =
                androidTasks.create(
                        tasks,
                        getMergeManifestConfig(variantOutputScope, optionalFeatures.build()));
        variantOutputScope.setManifestProcessorTask(processManifestTask);

        processManifestTask.dependsOn(tasks, variantScope.getCheckManifestTask());

        if (variantScope.getMicroApkTask() != null) {
            processManifestTask.dependsOn(tasks, variantScope.getMicroApkTask());
        }

        if (csmTask != null) {
            processManifestTask.dependsOn(tasks, csmTask);
        }

        variantScope.publishIntermediateArtifact(
                variantOutputScope.getManifestOutputFile(),
                processManifestTask.getName(),
                AndroidArtifacts.TYPE_ATOM_MANIFEST);
    }

    public void createMergeAppManifestsTask(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope variantScope) {
        AndroidArtifactVariantData<?> androidArtifactVariantData =
                (AndroidArtifactVariantData) variantScope.getVariantData();
        Set<String> screenSizes = androidArtifactVariantData.getCompatibleScreens();

        // loop on all outputs. The only difference will be the name of the task, and location
        // of the generated manifest
        for (final BaseVariantOutputData vod : androidArtifactVariantData.getOutputs()) {
            VariantOutputScope scope = vod.getScope();

            AndroidTask<CompatibleScreensManifest> csmTask = null;
            if (vod.getMainOutputFile().getFilter(DENSITY) != null) {
                csmTask = androidTasks.create(tasks,
                        new CompatibleScreensManifest.ConfigAction(scope, screenSizes));
                scope.setCompatibleScreensManifestTask(csmTask);
            }

            ImmutableList.Builder<ManifestMerger2.Invoker.Feature> optionalFeatures =
                    ImmutableList.builder();
            if (getIncrementalMode(
                    variantScope.getVariantConfiguration()) != IncrementalMode.NONE) {
                optionalFeatures.add(ManifestMerger2.Invoker.Feature.INSTANT_RUN_REPLACEMENT);
            }
            if (AndroidGradleOptions.getTestOnly(project)) {
                optionalFeatures.add(ManifestMerger2.Invoker.Feature.TEST_ONLY);
            }

            AndroidTask<? extends ManifestProcessorTask> processManifestTask =
                    androidTasks.create(
                            tasks,
                            getMergeManifestConfig(scope, optionalFeatures.build()));
            scope.setManifestProcessorTask(processManifestTask);

            processManifestTask.dependsOn(tasks, variantScope.getCheckManifestTask());

            if (variantScope.getMicroApkTask() != null) {
                processManifestTask.dependsOn(tasks, variantScope.getMicroApkTask());
            }

            if (csmTask != null) {
                processManifestTask.dependsOn(tasks, csmTask);
            }
        }

    }

    /** Creates configuration action for the merge manifests task. */
    @NonNull
    protected TaskConfigAction<? extends ManifestProcessorTask> getMergeManifestConfig(
            @NonNull VariantOutputScope scope,
            @NonNull List<ManifestMerger2.Invoker.Feature> optionalFeatures) {
        return new MergeManifests.ConfigAction(scope, optionalFeatures);
    }

    public AndroidTask<ProcessManifest> createMergeLibManifestsTask(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope scope) {

        AndroidTask<ProcessManifest> processManifest = androidTasks.create(tasks,
                new ProcessManifest.ConfigAction(scope));

        processManifest.dependsOn(tasks, scope.getCheckManifestTask());

        BaseVariantOutputData variantOutputData = scope.getVariantData().getMainOutput();
        variantOutputData.getScope().setManifestProcessorTask(processManifest);

        return processManifest;
    }

    protected void createProcessTestManifestTask(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope scope,
            @NonNull VariantScope testedScope) {

        FileCollection testedMetadata = null;
        if (testedScope.hasOutput(VariantScope.TaskOutputType.APK_METADATA)) {
            testedMetadata = testedScope.getOutputs(VariantScope.TaskOutputType.APK_METADATA);
        }
        AndroidTask<ProcessTestManifest> processTestManifestTask = androidTasks.create(tasks,
                new ProcessTestManifest.ConfigAction(scope, testedMetadata));

        processTestManifestTask.optionalDependsOn(tasks, scope.getCheckManifestTask());

        BaseVariantOutputData variantOutputData = scope.getVariantData().getMainOutput();
        variantOutputData.getScope().setManifestProcessorTask(processTestManifestTask);
    }

    public void createRenderscriptTask(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope scope) {
        scope.setRenderscriptCompileTask(
                androidTasks.create(tasks, new RenderscriptCompile.ConfigAction(scope)));

        BaseVariantData<? extends BaseVariantOutputData> variantData = scope.getVariantData();
        GradleVariantConfiguration config = variantData.getVariantConfiguration();

        // get single output for now.
        BaseVariantOutputData variantOutputData = variantData.getMainOutput();

        if (config.getType().isForTesting()) {
            scope.getRenderscriptCompileTask().dependsOn(tasks,
                    variantOutputData.getScope().getManifestProcessorTask());
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

    /**
     * Defines the merge type for {@link #basicCreateMergeResourcesTask}
     */
    enum MergeType {
        /**
         * Merge all resources with all the dependencies resources.
         */
        MERGE {
            @Override
            VariantScope.TaskOutputType getOutputType() {
                return VariantScope.TaskOutputType.MERGED_RES;
            }
        },
        /**
         * Merge all resources without the dependencies resources for an aar.
         */
        PACKAGE {
            @Override
            VariantScope.TaskOutputType getOutputType() {
                return VariantScope.TaskOutputType.PACKAGED_RES;
            }
        };

        abstract VariantScope.TaskOutputType getOutputType();
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

    public AndroidTask<MergeSourceSetFolders> createMergeAssetsTask(TaskFactory tasks, VariantScope scope) {
        AndroidTask<MergeSourceSetFolders> mergeAssetsTask = androidTasks.create(tasks,
                new MergeSourceSetFolders.MergeAssetConfigAction(scope));
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
        variantScope.getTransformManager().addStream(OriginalStream.builder(project)
                .addContentType(ExtendedContentType.NATIVE_LIBS)
                .addScope(Scope.PROJECT)
                .setFolder(variantScope.getMergeNativeLibsOutputDir())
                .setDependency(mergeJniLibFoldersTask.getName())
                .build());

        // create a stream that contains the content of the local NDK build
        variantScope.getTransformManager().addStream(OriginalStream.builder(project)
                .addContentType(ExtendedContentType.NATIVE_LIBS)
                .addScope(Scope.PROJECT)
                .setFolders(variantScope::getNdkSoFolder)
                .setDependency(getNdkBuildable(variantScope.getVariantData()))
                .build());

        // create a stream that contains the content of the local external native build
        if (variantScope.getExternalNativeJsonGenerator() != null) {
            variantScope.getTransformManager().addStream(OriginalStream.builder(project)
                    .addContentType(ExtendedContentType.NATIVE_LIBS)
                    .addScope(Scope.PROJECT)
                    .setFolder(variantScope.getExternalNativeJsonGenerator().getObjFolder())
                    .setDependency(variantScope.getExternalNativeBuildTask().getName())
                    .build());
        }

        // create a stream containing the content of the renderscript compilation output
        // if support mode is enabled.
        if (variantScope.getVariantConfiguration().getRenderscriptSupportModeEnabled()) {
            variantScope.getTransformManager().addStream(OriginalStream.builder(project)
                    .addContentType(ExtendedContentType.NATIVE_LIBS)
                    .addScope(Scope.PROJECT)
                    .setFolders(() -> {
                        ImmutableList.Builder<File> builder = ImmutableList.builder();

                        if (variantScope.getRenderscriptLibOutputDir().isDirectory()) {
                            builder.add(variantScope.getRenderscriptLibOutputDir());
                        }

                        File rsLibs = variantScope.getGlobalScope().getAndroidBuilder()
                                .getSupportNativeLibFolder();
                        if (rsLibs != null && rsLibs.isDirectory()) {
                            builder.add(rsLibs);
                        }
                        if (variantScope.getVariantConfiguration()
                                .getRenderscriptSupportModeBlasEnabled()) {
                            File rsBlasLib = variantScope.getGlobalScope().getAndroidBuilder()
                                    .getSupportBlasLibFolder();

                            if (rsBlasLib == null || !rsBlasLib.isDirectory()) {
                                throw new GradleException(
                                        "Renderscript BLAS support mode is not supported "
                                                + "in BuildTools" + rsBlasLib);
                            } else {
                                builder.add(rsBlasLib);
                            }
                        }
                        return builder.build();
                    })
                    .setDependency(variantScope.getRenderscriptCompileTask().getName())
                    .build());
        }

        // compute the scopes that need to be merged.
        Set<Scope> mergeScopes = getResMergingScopes(variantScope);
        // Create the merge transform
        MergeJavaResourcesTransform mergeTransform = new MergeJavaResourcesTransform(
                variantScope.getGlobalScope().getExtension().getPackagingOptions(),
                mergeScopes, ExtendedContentType.NATIVE_LIBS, "mergeJniLibs");
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

            // For test apps there should be a single output, so we get it.
            BaseVariantOutputData variantOutputData = scope.getVariantData().getMainOutput();

            generateBuildConfigTask.dependsOn(
                    tasks, variantOutputData.getScope().getManifestProcessorTask());
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
                () -> new File(globalScope.getIntermediatesDir(),
                        "symbols/" + scope.getVariantData().getVariantConfiguration().getDirName()),
                BaseVariantOutputData::getProcessResourcePackageOutputFile);
    }

    public void createProcessResTask(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope scope,
            @NonNull Supplier<File> symbolLocation,
            @NonNull Function<BaseVariantOutputData, File> packageOutputSupplier) {
        BaseVariantData<? extends BaseVariantOutputData> variantData = scope.getVariantData();

        variantData.calculateFilters(scope.getGlobalScope().getExtension().getSplits());
        boolean useAaptToGenerateLegacyMultidexMainDexProguardRules =
                isLegacyMultidexMode(scope);

        // split list calculation and save to this file.
        File splitListOutputFile = new File(scope.getSplitSupportDirectory(), FN_SPLIT_LIST);
        ConfigurableFileCollection splitList = project.files(splitListOutputFile);
        scope.setSplitList(new SplitList(splitList));
        AndroidTask<SplitsDiscovery> splitsDiscoveryAndroidTask = androidTasks
                .create(tasks, new SplitsDiscovery.ConfigAction(scope, splitListOutputFile));
        splitList.builtBy(splitsDiscoveryAndroidTask.getName());

        // loop on all outputs. The only difference will be the name of the task, and location
        // of the generated data.
        for (BaseVariantOutputData vod : variantData.getOutputs()) {
            final VariantOutputScope variantOutputScope = vod.getScope();

            variantOutputScope.setProcessResourcesTask(androidTasks.create(tasks,
                    new ProcessAndroidResources.ConfigAction(
                            variantOutputScope,
                            symbolLocation,
                            () -> packageOutputSupplier.apply(vod),
                            useAaptToGenerateLegacyMultidexMainDexProguardRules)));

            // always depend on merge res,
            variantOutputScope.getProcessResourcesTask().dependsOn(tasks,
                    scope.getMergeResourcesTask());
            if (scope.getDataBindingProcessLayoutsTask() != null) {
                variantOutputScope.getProcessResourcesTask().dependsOn(tasks,
                        scope.getDataBindingProcessLayoutsTask().getName());
            }
            variantOutputScope
                    .getProcessResourcesTask()
                    .dependsOn(tasks, variantOutputScope.getManifestProcessorTask());

            if (vod.getMainOutputFile().getFilter(DENSITY) == null) {
                scope.setGenerateRClassTask(variantOutputScope.getProcessResourcesTask());
                scope.getSourceGenTask().optionalDependsOn(
                        tasks,
                        variantOutputScope.getProcessResourcesTask());
            }

        }
    }

    /**
     * Creates the split resources packages task if necessary. AAPT will produce split packages for
     * all --split provided parameters. These split packages should be signed and moved unchanged to
     * the APK build output directory.
     */
    @NonNull
    public AndroidTask<PackageSplitRes> createSplitResourcesTasks(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope scope,
            @NonNull PackagingScope packagingScope) {
        BaseVariantData<? extends BaseVariantOutputData> variantData = scope.getVariantData();

        checkState(
                variantData
                        .getSplitHandlingPolicy()
                        .equals(SplitHandlingPolicy.RELEASE_21_AND_AFTER_POLICY),
                "Can only create split resources tasks for pure splits.");

        List<? extends BaseVariantOutputData> outputs = variantData.getOutputs();
        final BaseVariantOutputData variantOutputData = variantData.getMainOutput();
        if (outputs.size() != 1) {
            throw new RuntimeException(
                    "In release 21 and later, there can be only one main APK, " +
                            "found " + outputs.size());
        }

        VariantOutputScope variantOutputScope = variantOutputData.getScope();
        AndroidTask<PackageSplitRes> packageSplitRes =
                androidTasks.create(tasks, new PackageSplitRes.ConfigAction(scope));
        packageSplitRes.dependsOn(tasks, variantOutputScope.getProcessResourcesTask().getName());

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
        ApplicationVariantData variantData = (ApplicationVariantData) scope.getVariantData();

        checkState(variantData.getSplitHandlingPolicy().equals(
                SplitHandlingPolicy.RELEASE_21_AND_AFTER_POLICY),
                "split ABI tasks are only compatible with pure splits.");

        Set<String> filters = AbiSplitOptions.getAbiFilters(
                getExtension().getSplits().getAbiFilters());
        if (filters.isEmpty()) {
            return null;
        }

        List<ApkVariantOutputData> outputs = variantData.getOutputs();
        if (outputs.size() != 1) {
            throw new RuntimeException(
                    "In release 21 and later, there can be only one main APK, " +
                            "found " + outputs.size());
        }

        BaseVariantOutputData variantOutputData = variantData.getMainOutput();

        // first create the split APK resources.
        AndroidTask<GenerateSplitAbiRes> generateSplitAbiRes =
                androidTasks.create(tasks, new GenerateSplitAbiRes.ConfigAction(scope));
        generateSplitAbiRes.dependsOn(tasks,
                variantOutputData.getScope().getProcessResourcesTask().getName());

        // then package those resources with the appropriate JNI libraries.
        AndroidTask<PackageSplitAbi> packageSplitAbiTask =
                androidTasks.create(tasks, new PackageSplitAbi.ConfigAction(scope));

        packageSplitAbiTask.dependsOn(tasks, generateSplitAbiRes);
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
        VariantOutputScope outputScope = variantScope.getVariantData().getMainOutput().getScope();
        PackagingScope packagingScope = new DefaultGradlePackagingScope(outputScope);

        AndroidTask<PackageSplitRes> packageSplitResourcesTask =
                createSplitResourcesTasks(tasks, variantScope, packagingScope);
        final AndroidTask<PackageSplitAbi> packageSplitAbiTask =
                createSplitAbiTasks(tasks, variantScope, packagingScope);

        AndroidTask<SplitZipAlign> zipAlign =
                androidTasks.create(tasks, new SplitZipAlign.ConfigAction(variantScope));
        //noinspection VariableNotUsedInsideIf - only need to check if task exist.
        if (packageSplitAbiTask != null) {
            zipAlign.configure(
                    tasks,
                    task ->
                            task.getAbiInputFiles()
                                    .addAll(variantScope.getPackageSplitAbiOutputFiles()));
        }
        zipAlign.dependsOn(tasks, packageSplitResourcesTask);
        zipAlign.optionalDependsOn(tasks, packageSplitAbiTask);

        outputScope.setSplitZipAlignTask(zipAlign);
    }

    /**
     * Returns the scopes for which the java resources should be merged.
     * @param variantScope the scope of the variant being processed.
     * @return the list of scopes for which to merge the java resources.
     */
    @NonNull
    protected abstract Set<Scope> getResMergingScopes(@NonNull VariantScope variantScope);

    /**
     * Creates the java resources processing tasks.
     *
     * The java processing will happen in two steps:
     * <ul>
     * <li>{@link Sync} task configured with {@link ProcessJavaResConfigAction} will sync all source
     * folders into a single folder identified by
     * {@link VariantScope#getSourceFoldersJavaResDestinationDir()}</li>
     * <li>{@link MergeJavaResourcesTransform} will take the output of this merge plus the
     * dependencies and will create a single merge with the {@link PackagingOptions} settings
     * applied.</li>
     * </ul>
     *
     * This sets up only the Sync part. The transform is setup via
     * {@link #createMergeJavaResTransform(TaskFactory, VariantScope)}
     *
     * @param tasks tasks factory to create tasks.
     * @param variantScope the variant scope we are operating under.
     */
    public void createProcessJavaResTask(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope variantScope) {
        // Copy the source folders java resources into the temporary location, mainly to
        // maintain the PluginDsl COPY semantics.
        AndroidTask<Sync> processJavaResourcesTask =
                androidTasks.create(tasks, new ProcessJavaResConfigAction(variantScope));
        variantScope.setProcessJavaResourcesTask(processJavaResourcesTask);
        processJavaResourcesTask.dependsOn(tasks, variantScope.getPreBuildTask());

        // create the stream generated from this task
        variantScope.getTransformManager().addStream(OriginalStream.builder(project)
                .addContentType(DefaultContentType.RESOURCES)
                .addScope(Scope.PROJECT)
                .setFolder(variantScope.getSourceFoldersJavaResDestinationDir())
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

        // compute the scopes that need to be merged.
        Set<Scope> mergeScopes = getResMergingScopes(variantScope);

        // Create the merge transform
        MergeJavaResourcesTransform mergeTransform = new MergeJavaResourcesTransform(
                variantScope.getGlobalScope().getExtension().getPackagingOptions(),
                mergeScopes, DefaultContentType.RESOURCES, "mergeJavaRes");
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
        final BaseVariantData<? extends BaseVariantOutputData> variantData = scope.getVariantData();

        AndroidTask<IncrementalSafeguard> javacIncrementalSafeguard = androidTasks.create(tasks,
                new IncrementalSafeguard.ConfigAction(scope));

        AndroidTask<JavaPreCompileTask> preCompileTask = androidTasks.create(tasks,
                new JavaPreCompileTask.ConfigAction(scope));

        final AndroidTask<? extends JavaCompile> javacTask =
                androidTasks.create(tasks, new JavaCompileConfigAction(scope));
        scope.setJavacTask(javacTask);
        javacTask.dependsOn(tasks, javacIncrementalSafeguard, preCompileTask);

        setupCompileTaskDependencies(tasks, scope, javacTask);

        // Create the classes artifact for uses by external modules.
        if (variantData.getVariantConfiguration().getType() == VariantType.DEFAULT) {
            scope.publishIntermediateArtifact(
                    scope.getJavaOutputDir(),
                    javacTask.getName(),
                    JavaPlugin.CLASS_DIRECTORY);
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
        scope.getTransformManager().addStream(OriginalStream.builder(project)
                .addContentType(DefaultContentType.CLASSES)
                .addScope(Scope.PROJECT)
                .setFolder(scope.getJavaOutputDir())
                .setDependency(scope.getJavacTask().getName())
                .build());
    }

    private static void setupCompileTaskDependencies(
            @NonNull TaskFactory tasks, @NonNull VariantScope scope, AndroidTask<?> compileTask) {

        compileTask.optionalDependsOn(tasks, scope.getSourceGenTask());
        // TODO - dependency information for the compile classpath is being lost.
        // Add a temporary approximation
        compileTask.dependsOn(
                tasks,
                scope.getVariantData()
                        .getVariantDependency()
                        .getCompileConfiguration());
        compileTask.dependsOn(
                tasks,
                scope.getVariantData()
                        .getVariantDependency()
                        .getAnnotationProcessorConfiguration());
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
            @Nullable Configuration config) {
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

        boolean buildOnlyTargetAbi = AndroidGradleOptions.isBuildOnlyTargetAbiEnabled(project);
        // Set up build tasks
        AndroidTask<ExternalNativeBuildTask> buildTask = androidTasks.create(
                tasks,
                new ExternalNativeBuildTask.ConfigAction(
                        buildOnlyTargetAbi ? AndroidGradleOptions.getBuildTargetAbi(project) : null,
                        generator, scope, androidBuilder));

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
        BaseVariantData testedVariantData = variantScope.getTestedVariantData();
        checkState(testedVariantData != null);

        createPreBuildTasks(tasks, variantScope);

        // Create all current streams (dependencies mostly at this point)
        createDependencyStreams(tasks, variantScope);

        createProcessJavaResTask(tasks, variantScope);
        createMergeJavaResTransform(tasks, variantScope);
        createCompileAnchorTask(tasks, variantScope);

        // :app:compileDebugUnitTestSources should be enough for running tests from AS, so add an
        // explicit dependency on resource copying tasks.
        variantScope.getCompileTask().dependsOn(
                tasks,
                variantScope.getProcessJavaResourcesTask(),
                testedVariantData.getScope().getProcessJavaResourcesTask());

        AndroidTask<? extends JavaCompile> javacTask = createJavacTask(tasks, variantScope);
        addJavacClassesStream(variantScope);
        setJavaCompilerTask(javacTask, tasks, variantScope);
        javacTask.dependsOn(tasks, testedVariantData.getScope().getJavacTask());

        createRunUnitTestTask(tasks, variantScope);

        variantScope.getAssembleTask().dependsOn(tasks, createMockableJar);
        // This hides the assemble unit test task from the task list.
        variantScope.getAssembleTask().configure(tasks, task -> task.setGroup(null));
    }

    /**
     * Creates the tasks to build android tests.
     */
    public void createAndroidTestVariantTasks(@NonNull TaskFactory tasks,
            @NonNull TestVariantData variantData) {
        VariantScope variantScope = variantData.getScope();

        // get single output for now (though this may always be the case for tests).
        final BaseVariantOutputData variantOutputData = variantData.getMainOutput();

        final BaseVariantData<BaseVariantOutputData> testedVariantData =
                (BaseVariantData<BaseVariantOutputData>) variantData.getTestedVariantData();
        final BaseVariantOutputData testedVariantOutputData = testedVariantData.getMainOutput();

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
        createMergeAssetsTask(tasks, variantScope);

        if (variantData.getTestedVariantData().getVariantConfiguration().getType().equals(
                VariantType.LIBRARY)) {
            // in this case the tested library must be fully built before test can be built!
            if (testedVariantOutputData.getScope().getAssembleTask() != null) {
                String bundle =
                        testedVariantOutputData.getScope().getVariantScope().getTaskName("bundle");
                variantOutputData.getScope().getManifestProcessorTask().dependsOn(tasks, bundle);
                variantScope.getMergeResourcesTask().dependsOn(tasks, bundle);
            }
        }

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
        if (!isComponentModelPlugin) {
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

        tasks.named(ASSEMBLE_ANDROID_TEST, assembleTest ->
                assembleTest.dependsOn(variantOutputData.getScope().getAssembleTask().getName()));

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
            return AndroidGradleOptions.getTargetFeatureLevel(project) >=
                    AndroidVersion.ART_RUNTIME.getFeatureLevel();
        }

        return true;
    }

    /**
     * Is the given variant relevant for lint?
     */
    private static boolean isLintVariant(
            @NonNull BaseVariantData<? extends BaseVariantOutputData> baseVariantData) {
        // Only create lint targets for variants like debug and release, not debugTest
        VariantConfiguration config = baseVariantData.getVariantConfiguration();
        return !config.getType().isForTesting();
    }

    /**
     * Add tasks for running lint on individual variants. We've already added a
     * lint task earlier which runs on all variants.
     */
    public void createLintTasks(TaskFactory tasks, final VariantScope scope) {
        final BaseVariantData<? extends BaseVariantOutputData> baseVariantData =
                scope.getVariantData();
        if (!isLintVariant(baseVariantData)) {
            return;
        }

        // wire the main lint task dependency.
        tasks.named(LINT, lint -> lint.dependsOn(scope.getJavacTask().getName()));

        AndroidTask<Lint> variantLintCheck = androidTasks.create(
                tasks, new Lint.ConfigAction(scope));
        variantLintCheck.dependsOn(tasks, LINT_COMPILE, scope.getJavacTask());
    }

    private void createLintVitalTask(
            @NonNull TaskFactory tasks,
            @NonNull ApkVariantData variantData) {
        checkState(getExtension().getLintOptions().isCheckReleaseBuilds());
        // TODO: re-enable with Jack when possible
        if (!variantData.getVariantConfiguration().getBuildType().isDebuggable()
                && !variantData.getVariantConfiguration().isJackEnabled()) {
            final AndroidTask<Lint> lintReleaseCheck = androidTasks.create(
                    tasks,
                    new Lint.VitalConfigAction(variantData.getScope()));
            lintReleaseCheck.optionalDependsOn(tasks, variantData.javacTask);

            variantData.getScope().getAssembleTask().dependsOn(tasks, lintReleaseCheck);

            // If lint is being run, we do not need to run lint vital.
            // TODO: Find a better way to do this.
            project.getGradle().getTaskGraph().whenReady(new Closure<Void>(this, this) {
                public void doCall(TaskExecutionGraph taskGraph) {
                    if (taskGraph.hasTask(LINT)) {
                        project.getTasks().getByName(lintReleaseCheck.getName()).setEnabled(false);
                    }
                }
            });
        }
    }

    private void createRunUnitTestTask(
            @NonNull TaskFactory tasks,
            @NonNull final VariantScope variantScope) {
        final AndroidTask<AndroidUnitTest> runTestsTask =
                androidTasks.create(tasks, new AndroidUnitTest.ConfigAction(variantScope));
        runTestsTask.dependsOn(tasks, variantScope.getAssembleTask());

        tasks.named(JavaPlugin.TEST_TASK_NAME, test -> test.dependsOn(runTestsTask.getName()));
    }

    public void createTopLevelTestTasks(final TaskFactory tasks, boolean hasFlavors) {
        createMockableJarTask(tasks);

        final List<String> reportTasks = Lists.newArrayListWithExpectedSize(2);

        List<DeviceProvider> providers = getExtension().getDeviceProviders();

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
        final BaseVariantData<? extends BaseVariantOutputData> baseVariantData =
                variantScope.getTestedVariantData();
        final TestVariantData testVariantData = (TestVariantData) variantScope.getVariantData();

        // get single output for now
        final BaseVariantOutputData variantOutputData = baseVariantData.getMainOutput();
        final BaseVariantOutputData testVariantOutputData = testVariantData.getMainOutput();

        TestDataImpl testData = new TestDataImpl(testVariantData);
        testData.setExtraInstrumentationTestRunnerArgs(
                AndroidGradleOptions.getExtraInstrumentationTestRunnerArgs(project));

        configureTestData(testData);

        // create the check tasks for this test
        // first the connected one.
        ImmutableList<AndroidTask<DefaultTask>> artifactsTasks =
                ImmutableList.of(
                        testVariantData.getMainOutput().getScope().getAssembleTask(),
                        baseVariantData.getScope().getAssembleTask());

        final AndroidTask<DeviceProviderInstrumentTestTask> connectedTask = androidTasks.create(
                tasks,
                new DeviceProviderInstrumentTestTask.ConfigAction(
                        testVariantData.getScope(),
                        new ConnectedDeviceProvider(sdkHandler.getSdkInfo().getAdb(),
                                globalScope.getExtension().getAdbOptions().getTimeOutInMs(),
                                new LoggerWrapper(logger)), testData, null /* testTargetMetadata */));

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

        List<DeviceProvider> providers = getExtension().getDeviceProviders();

        boolean hasFlavors = baseVariantData.getVariantConfiguration().hasFlavors();

        // now the providers.
        for (DeviceProvider deviceProvider : providers) {

            final AndroidTask<DeviceProviderInstrumentTestTask> providerTask = androidTasks
                    .create(tasks, new DeviceProviderInstrumentTestTask.ConfigAction(
                            testVariantData.getScope(), deviceProvider, testData,
                            null /* testTargetMetadata */));

            providerTask.dependsOn(tasks, artifactsTasks.toArray());
            tasks.named(DEVICE_ANDROID_TEST,
                    deviceAndroidTest -> deviceAndroidTest.dependsOn(providerTask.getName()));
        }

        // now the test servers
        List<TestServer> servers = getExtension().getTestServers();
        for (final TestServer testServer : servers) {
            final AndroidTask<TestServerTask> serverTask = androidTasks.create(
                    tasks,
                    new TestServerTaskConfigAction(variantScope, testServer));
            serverTask.dependsOn(
                    tasks,
                    testVariantOutputData.getScope().getAssembleTask(),
                    variantOutputData.getScope().getAssembleTask());

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

        variantScope.getBuildContext().setInstantRunMode(
                getIncrementalMode(variantScope.getVariantConfiguration()) != IncrementalMode.NONE);

        final BaseVariantData<? extends BaseVariantOutputData> variantData =
                variantScope.getVariantData();
        final GradleVariantConfiguration config = variantData.getVariantConfiguration();

        TransformManager transformManager = variantScope.getTransformManager();

        // ---- Code Coverage first -----
        boolean isTestCoverageEnabled = config.getBuildType().isTestCoverageEnabled() &&
                !config.getType().isForTesting() &&
                getIncrementalMode(variantScope.getVariantConfiguration()) == IncrementalMode.NONE;
        if (isTestCoverageEnabled) {
            createJacocoTransform(tasks, variantScope);
        }

        boolean isMinifyEnabled = isMinifyEnabled(variantScope);
        boolean isMultiDexEnabled = config.isMultiDexEnabled();
        // Switch to native multidex if possible when using instant run.
        boolean isLegacyMultiDexMode = isLegacyMultidexMode(variantScope);

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

        // ----- User transform support (e.g. Studio profilers) -----

        for (String jar : AndroidGradleOptions.getCustomClassTransforms(project)) {
            if (variantScope.getVariantConfiguration().getBuildType().isDebuggable()
                    && variantData.getType().equals(VariantType.DEFAULT)
                    && jar != null) {
                transformManager.addTransform(tasks, variantScope, new CustomClassTransform(jar));
            }
        }

        // ----- Minify next -----

        if (isMinifyEnabled) {
            boolean outputToJarFile = isMultiDexEnabled && isLegacyMultiDexMode;
            createMinifyTransform(tasks, variantScope, outputToJarFile);
        }

        maybeCreateShrinkResourcesTransform(tasks, variantScope);

        // ----- 10x support

        AndroidTask<PreColdSwapTask> preColdSwapTask = null;
        if (variantScope.getBuildContext().isInInstantRunMode()) {

            AndroidTask<DefaultTask> allActionsAnchorTask =
                    createInstantRunAllActionsTasks(tasks, variantScope);
            assert variantScope.getInstantRunTaskManager() != null;
            preColdSwapTask = variantScope.getInstantRunTaskManager()
                    .createPreColdswapTask(project);
            preColdSwapTask.dependsOn(tasks, allActionsAnchorTask);

            // when dealing with platforms that can handle multi dexes natively, automatically
            // turn on multi dexing so shards are packaged as individual dex files.
            if (InstantRunPatchingPolicy.PRE_LOLLIPOP !=
                    variantScope.getBuildContext().getPatchingPolicy()) {
                isMultiDexEnabled = true;
                // force pre-dexing to be true as we rely on individual slices to be packaged
                // separately.
                extension.getDexOptions().setPreDexLibraries(true);
                variantScope.getInstantRunTaskManager().createSlicerTask();
            }

            extension.getDexOptions().setJumboMode(true);
        }
        // ----- Multi-Dex support

        Optional<AndroidTask<TransformTask>> multiDexClassListTask;
        // non Library test are running as native multi-dex
        if (isMultiDexEnabled && isLegacyMultiDexMode) {
            if (!variantData.getVariantConfiguration().getBuildType().isUseProguard()) {
                throw new IllegalStateException(
                        "Build-in class shrinker and multidex are not supported yet.");
            }

            // ----------
            // create a transform to jar the inputs into a single jar.
            if (!isMinifyEnabled) {
                // merge the classes only, no need to package the resources since they are
                // not used during the computation.
                JarMergingTransform jarMergingTransform = new JarMergingTransform(
                        TransformManager.SCOPE_FULL_PROJECT);
                transformManager
                        .addTransform(tasks, variantScope, jarMergingTransform)
                        .ifPresent(variantScope::addColdSwapBuildTask);
            }

            // ---------
            // create the transform that's going to take the code and the proguard keep list
            // from above and compute the main class list.
            Transform multiDexTransform;
            if (globalScope.getAndroidGradleOptions().useDexArchive()) {
                multiDexTransform = new MainDexListTransform(
                        variantScope,
                        extension.getDexOptions());
            } else {
                multiDexTransform = new MultiDexTransform(
                        variantScope,
                        extension.getDexOptions());
            }
            multiDexClassListTask =
                    transformManager.addTransform(tasks, variantScope, multiDexTransform);
            multiDexClassListTask.ifPresent(variantScope::addColdSwapBuildTask);
        } else {
            multiDexClassListTask = Optional.empty();
        }

        final DexingMode dexingMode;
        if (isLegacyMultiDexMode) {
            dexingMode = DexingMode.LEGACY_MULTIDEX;
        } else if (!isMultiDexEnabled) {
            dexingMode = DexingMode.MONO_DEX;
        } else {
            dexingMode = DexingMode.NATIVE_MULTIDEX;
        }

        createDexTasks(tasks, variantScope, multiDexClassListTask.orElse(null), dexingMode);

        if (preColdSwapTask != null) {
            for (AndroidTask<? extends DefaultTask> task : variantScope.getColdSwapBuildTasks()) {
                task.dependsOn(tasks, preColdSwapTask);
            }
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
            // Don't use custom dx flags when compiling the test APK. They can break the test APK,
            // like --minimal-main-dex.
            dexOptions = DefaultDexOptions.copyOf(extension.getDexOptions());
            dexOptions.setAdditionalParameters(ImmutableList.of());
        } else {
            dexOptions = extension.getDexOptions();
        }

        boolean cachePreDex =
                dexingMode.preDex
                        && dexOptions.getPreDexLibraries()
                        && !isMinifyEnabled(variantScope);
        boolean preDexEnabled =
                variantScope.getBuildContext().isInInstantRunMode() || cachePreDex;
        if (preDexEnabled) {
            FileCache buildCache;
            if (cachePreDex && AndroidGradleOptions.isPreDexBuildCacheEnabled(project)) {
                buildCache = globalScope.getBuildCache();
            } else {
                buildCache = null;
            }

            PreDexTransform preDexTransform =
                    new PreDexTransform(
                            dexOptions,
                            androidBuilder,
                            buildCache,
                            dexingMode,
                            variantScope.getBuildContext().isInInstantRunMode());
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
                            androidBuilder.getErrorReporter());
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

    private boolean isLegacyMultidexMode(@NonNull VariantScope variantScope) {
        return variantScope.getVariantData().getVariantConfiguration().isLegacyMultiDexMode() &&
                (getIncrementalMode(variantScope.getVariantConfiguration()) == IncrementalMode.NONE
                        || variantScope.getBuildContext().getPatchingPolicy() ==
                        InstantRunPatchingPolicy.PRE_LOLLIPOP);

    }

    private boolean isMinifyEnabled(VariantScope variantScope) {
        return variantScope.getVariantConfiguration().isMinifyEnabled()
                || isTestedAppMinified(variantScope);
    }

    /**
     * Default values if {@code false}, only {@link TestApplicationTaskManager} overrides this,
     * because tested applications might be minified.
     *
     * @return if the tested application is minified
     */
    protected boolean isTestedAppMinified(@NonNull VariantScope variantScope) {
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

        variantScope.setInstantRunTaskManager(instantRunTaskManager);
        AndroidTask<BuildInfoLoaderTask> buildInfoLoaderTask =
                instantRunTaskManager.createInstantRunAllTasks(
                        variantScope.getGlobalScope().getExtension().getDexOptions(),
                        androidBuilder::getDexByteCodeConverter,
                        extractJarsTask.orElse(null),
                        allActionAnchorTask,
                        getResMergingScopes(variantScope),
                        new SupplierTask<File>() {
                            private final VariantOutputScope variantOutputScope =
                                    variantScope.getVariantData().getMainOutput().getScope();

                            @Nullable
                            @Override
                            public AndroidTask<?> getBuilderTask() {
                                return variantOutputScope.getManifestProcessorTask();
                            }

                            @Override
                            public File get() {
                                return variantOutputScope
                                        .getVariantScope()
                                        .getInstantRunManifestOutputFile();
                            }
                        },
                        new SupplierTask<File>() {
                            private final VariantOutputScope variantOutputScope =
                                    variantScope.getVariantData().getMainOutput().getScope();

                            @Nullable
                            @Override
                            public AndroidTask<?> getBuilderTask() {
                                return variantOutputScope.getProcessResourcesTask();
                            }

                            @Override
                            public File get() {
                                return variantOutputScope.getProcessResourcePackageOutputFile();
                            }
                        },
                        true /* addResourceVerifier */);

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
            variantScope.getTransformManager().addStream(OriginalStream.builder(project)
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
        VariantDependencies variantDependency = scope.getVariantData().getVariantDependency();
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
        VariantDependencies variantDependency = scope.getVariantData().getVariantDependency();
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
        VariantDependencies variantDependency = scope.getVariantData().getVariantDependency();
        JackCompileTransform jackCompileTransform =
                new JackCompileTransform(
                        JackOptionsUtils.forSourceCompilation(scope),
                        androidBuilder::getBuildToolInfo,
                        androidBuilder.getErrorReporter(),
                        androidBuilder.getJavaProcessExecutor(),
                        variantDependency.getAnnotationProcessorConfiguration(),
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
        VariantDependencies variantDependency = scope.getVariantData().getVariantDependency();
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
        final BaseVariantData<? extends BaseVariantOutputData> variantData = variantScope
                .getVariantData();
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
                        AndroidArtifacts.TYPE_DATA_BINDING);
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

        scope.getGenerateRClassTask().dependsOn(tasks, processLayoutsTask);
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

        // support for split apk
        for (BaseVariantOutputData baseVariantOutputData : scope.getVariantData().getOutputs()) {
            final ProcessAndroidResources processResTask =
                    baseVariantOutputData.processResourcesTask;
            if (processResTask != null) {
                processResTask.dependsOn(processLayoutsTask.getName());
            }
        }
    }

    private void setDataBindingAnnotationProcessorParams(@NonNull VariantScope scope) {
        BaseVariantData<? extends BaseVariantOutputData> variantData = scope.getVariantData();
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
     * @param publishApk if true the generated APK gets published.
     * @param fullBuildInfoGeneratorTask task that generates the build-info.xml for full build.
     */
    public void createPackagingTask(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope variantScope,
            boolean publishApk,
            @Nullable AndroidTask<BuildInfoWriterTask> fullBuildInfoGeneratorTask) {
        GlobalScope globalScope = variantScope.getGlobalScope();
        ApkVariantData variantData = (ApkVariantData) variantScope.getVariantData();

        boolean signedApk = variantData.isSigned();
        boolean multiOutput = variantData.getOutputs().size() > 1;
        boolean abiSpecified =
                !Strings.isNullOrEmpty(AndroidGradleOptions.getBuildTargetAbi(project));

        GradleVariantConfiguration variantConfiguration = variantScope.getVariantConfiguration();
        /**
         * PrePackaging step class that will look if the packaging of the main APK split is
         * necessary when running in InstantRun mode. In InstantRun mode targeting an api 23 or
         * above device, resources are packaged in the main split APK. However when a warm swap is
         * possible, it is not necessary to produce immediately the new main SPLIT since the runtime
         * use the resources.ap_ file directly. However, as soon as an incompatible change forcing a
         * cold swap is triggered, the main APK must be rebuilt (even if the resources were changed
         * in a previous build).
         */
        IncrementalMode incrementalMode = getIncrementalMode(variantConfiguration);

        List<ApkVariantOutputData> outputDataList = variantData.getOutputs();

        // Because we can only publish a single apk to the intermediate artifact, we cannot
        // publish all the multi-apk. In order to keep some of the tests working (especially
        // the test for the separate test module) we do want to publish one.
        // FIXME we need to fix publication of multi-apks.
        boolean hasPublishedApk = false;

        // loop on all outputs. The only difference will be the name of the task, and location
        // of the generated data.
        for (final ApkVariantOutputData variantOutputData : outputDataList) {
            final VariantOutputScope variantOutputScope = variantOutputData.getScope();

            final String outputName = variantOutputData.getFullName();
            InstantRunPatchingPolicy patchingPolicy =
                    variantScope.getBuildContext().getPatchingPolicy();

            DefaultGradlePackagingScope packagingScope =
                    new DefaultGradlePackagingScope(variantOutputScope);

            AndroidTask<PackageApplication> packageApp =
                    androidTasks.create(
                            tasks,
                            new PackageApplication.StandardConfigAction(
                                    project, packagingScope, patchingPolicy));

            AndroidTask<PackageApplication> packageInstantRunResources = null;

            if (variantScope.getBuildContext().isInInstantRunMode()) {
                packageInstantRunResources = androidTasks.create(
                        tasks,
                        new PackageApplication.InstantRunResourcesConfigAction(
                                project,
                                variantScope.getInstantRunResourcesFile(),
                                packagingScope,
                                patchingPolicy));

                // Make sure the MAIN artifact is registered after the RESOURCES one.
                packageApp.dependsOn(tasks, packageInstantRunResources);
            }

            packageApp.configure(
                    tasks,
                    task -> variantOutputData.packageAndroidArtifactTask = task);

            TransformManager transformManager = variantScope.getTransformManager();

            // Common code for both packaging tasks.
            Consumer<AndroidTask<PackageApplication>> configureResourcesAndAssetsDependencies =
                    task -> {
                        task.dependsOn(tasks, variantScope.getMergeAssetsTask());
                        task.dependsOn(tasks, variantOutputScope.getProcessResourcesTask());
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
                    variantOutputScope.getShrinkResourcesTask(),
                    // TODO: When Jack is converted, add activeDexTask to VariantScope.
                    variantOutputScope.getVariantScope().getJavaCompilerTask(),
                    // TODO: Remove when Jack is converted to AndroidTask.
                    variantData.javaCompilerTask,
                    variantOutputData.packageSplitResourcesTask,
                    variantOutputData.packageSplitAbiTask);

            variantScope.setPackageApplicationTask(packageApp);

            AndroidTask<?> appTask = packageApp;

            if (signedApk) {
                /*
                 * There may be a zip align task in the variant output scope, even if we don't
                 * need one for this.
                 */
                if (variantData.getZipAlignEnabled()
                        && variantOutputScope.getSplitZipAlignTask() != null) {
                    appTask.dependsOn(tasks, variantOutputScope.getSplitZipAlignTask());
                }
            }

            checkState(variantScope.getAssembleTask() != null);
            if (fullBuildInfoGeneratorTask != null) {
                AndroidTask<PackageApplication> finalPackageInstantRunResources =
                        packageInstantRunResources;
                fullBuildInfoGeneratorTask.configure(tasks, task -> {
                    task.mustRunAfter(appTask.getName());
                    if (finalPackageInstantRunResources != null) {
                        task.mustRunAfter(finalPackageInstantRunResources.getName());
                    }
                });
                variantScope.getAssembleTask().dependsOn(
                        tasks, fullBuildInfoGeneratorTask.getName());
            }

            // Add an assemble task
            if (multiOutput) {
                // create a task for this output
                variantOutputScope.setAssembleTask(createAssembleTask(tasks, variantOutputData));

                // If ABI is specified, the variant assemble task depends on the output assemble
                // task for the given ABI. Otherwise, it depends on all output assemble tasks.
                if (!abiSpecified
                        || abiSpecified && variantOutputData == variantData.getMainOutput()) {
                    variantScope
                            .getAssembleTask()
                            .dependsOn(tasks, variantOutputScope.getAssembleTask());
                }
            } else {
                // single output
                variantOutputScope.setAssembleTask(variantScope.getAssembleTask());
                variantOutputData.assembleTask = variantData.assembleVariantTask;
            }

            if (!signedApk && variantOutputData.packageSplitResourcesTask != null) {
                // in case we are not signing the resulting APKs and we have some pure splits
                // we should manually copy them from the intermediate location to the final
                // apk location unmodified.
                final String appTaskName = appTask.getName();
                AndroidTask<Copy> copySplitTask = androidTasks.create(
                        tasks,
                        variantOutputScope.getTaskName("copySplit"),
                        Copy.class,
                        copyTask -> {
                            copyTask.setDestinationDir(getGlobalScope().getApkLocation());
                            copyTask.from(
                                    variantOutputData
                                            .packageSplitResourcesTask
                                            .getOutputDirectory());
                            copyTask.mustRunAfter(appTaskName);
                        });
                variantOutputScope.getAssembleTask().dependsOn(tasks, copySplitTask);
            }
            variantOutputScope.getAssembleTask().dependsOn(tasks, appTask);

            if (publishApk && !hasPublishedApk) {
                hasPublishedApk = true;
                appTask.configure(tasks, packageTask -> {
                    FileSupplier apkSupplier = (FileSupplier) packageTask;
                    variantScope.publishIntermediateArtifact(
                            apkSupplier.get(),
                            apkSupplier.getTask().getName(),
                            AndroidArtifacts.TYPE_APK);
                });

                // FIXME need to support split, by publishing the splits folder instead of the files like the local jars from an AAR.
                //for (FileSupplier supplier : variantOutputData.getSplitOutputFileSuppliers()) {
                //    AndroidArtifacts.publishIntermediateArtifact(
                //            project,
                //            publishConfigName,
                //            supplier.get(),
                //            supplier.getTask().getName(),
                //            AndroidArtifacts.TYPE_APK);
                //}

                FileCollection metadataFile = variantScope.getOutputs(
                        VariantScope.TaskOutputType.APK_METADATA);
                variantScope.publishIntermediateArtifact(
                        metadataFile.getSingleFile(),
                        metadataFile.getBuildDependencies().getDependencies(null)
                            .stream()
                                .map(Task::getName)
                                .collect(Collectors.joining(",")),
                        AndroidArtifacts.TYPE_METADATA);

                final FileSupplier mappingFileProvider = variantData.getMappingFileProvider();
                if (mappingFileProvider != null) {
                    final File mappingFile = mappingFileProvider.get();
                    if (mappingFile != null) {
                        variantScope.publishIntermediateArtifact(
                                mappingFile,
                                mappingFileProvider.getTask().getName(),
                                AndroidArtifacts.TYPE_MAPPING);
                    }
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

        if (getExtension().getLintOptions().isCheckReleaseBuilds()
                && (incrementalMode == IncrementalMode.NONE)) {
            createLintVitalTask(tasks, variantData);
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
            @NonNull TaskFactory tasks,
            @NonNull final BaseVariantOutputData variantOutputData) {
        return androidTasks.create(
                tasks,
                variantOutputData.getScope().getTaskName("assemble"),
                task -> {
                    variantOutputData.assembleTask = task;
                });
    }

    public AndroidTask<DefaultTask> createAssembleTask(
            @NonNull TaskFactory tasks,
            @NonNull final BaseVariantData<? extends BaseVariantOutputData> variantData) {
        return androidTasks.create(
                tasks,
                variantData.getScope().getTaskName("assemble"),
                task -> {
                    variantData.assembleVariantTask = task;
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

    /**
     * Creates a zip align task that aligns a provided APK.
     *
     * @param name the name of the task
     * @param inputFile the file to align
     * @param outputFile where to put the aligned file
     * @param variantOutputScope the variant output scope required to find build tools
     */
    @NonNull
    public ZipAlign createZipAlignTask(
            @NonNull String name,
            @NonNull File inputFile,
            @NonNull File outputFile,
            @NonNull VariantOutputScope variantOutputScope) {

        // Add a task to zip align application package
        ZipAlign zipAlignTask =
                project
                        .getTasks()
                        .create(
                                name,
                                ZipAlign.class,
                                new ZipAlign.ConfigAction(variantOutputScope));
        ConventionMappingHelper.map(zipAlignTask, "inputFile", () -> inputFile);
        ConventionMappingHelper.map(zipAlignTask, "outputFile", () -> outputFile);

        /*
         * We need to make sure we have the manifest available so, we need a dependency.
         */
        zipAlignTask.dependsOn(
                variantOutputScope.getVariantScope().getPackageApplicationTask().getName());

        return zipAlignTask;
    }

    protected void createMinifyTransform(
            @NonNull TaskFactory taskFactory,
            @NonNull final VariantScope variantScope,
            boolean createJarFile) {
        doCreateMinifyTransform(
                taskFactory,
                variantScope,
                // No mapping in non-test modules.
                null);
    }

    /**
     * Actually creates the minify transform, using the given mapping configuration. The mapping is
     * only used by test-only modules.
     */
    protected final void doCreateMinifyTransform(
            @NonNull TaskFactory taskFactory,
            @NonNull final VariantScope variantScope,
            @Nullable FileCollection mappingFileCollection) {
        if (variantScope
                .getVariantData()
                .getVariantConfiguration()
                .getBuildType()
                .isUseProguard()) {
            createProguardTransform(taskFactory, variantScope, mappingFileCollection);
        } else {
            // Since the built-in class shrinker does not obfuscate, there's no point running
            // it on the test APK (it also doesn't have a -dontshrink mode).
            if (variantScope.getTestedVariantData() == null) {
                createNewShrinkerTransform(variantScope, taskFactory);
            }
        }
    }

    private void createNewShrinkerTransform(VariantScope scope, TaskFactory taskFactory) {
        NewShrinkerTransform transform = new NewShrinkerTransform(scope);
        applyProguardConfig(transform, scope.getVariantData());

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

        final BaseVariantData<? extends BaseVariantOutputData> variantData = variantScope
                .getVariantData();
        final GradleVariantConfiguration variantConfig = variantData.getVariantConfiguration();
        final BaseVariantData testedVariantData = variantScope.getTestedVariantData();

        ProGuardTransform transform = new ProGuardTransform(variantScope);

        if (testedVariantData != null) {
            applyProguardDefaultsForTest(transform);
            // All -dontwarn rules for test dependencies should go in here:
            transform.setConfigurationFiles(
                    testedVariantData.getVariantConfiguration()::getTestProguardFiles);

            // register the mapping file which may or may not exists (only exist if obfuscation)
            // is enabled.
            transform.applyTestedMapping(testedVariantData.getMappingFile());
        } else if (isTestedAppMinified(variantScope)) {
            applyProguardDefaultsForTest(transform);
            // All -dontwarn rules for test dependencies should go in here:
            transform.setConfigurationFiles(variantConfig::getTestProguardFiles);
            transform.applyTestedMapping(mappingFileCollection);
        } else {
            applyProguardConfig(transform, variantData);

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
    protected void maybeCreateShrinkResourcesTransform(
            @NonNull TaskFactory taskFactory, @NonNull VariantScope scope) {
        CoreBuildType buildType = scope.getVariantConfiguration().getBuildType();

        if (!buildType.isShrinkResources()) {
            // The user didn't enable resource shrinking, silently move on.
            return;
        }

        if (!scope.useResourceShrinker()) {
            // The user enabled resource shrinking, but we disabled it for some reason. Try to
            // explain.

            if (getIncrementalMode(scope.getVariantConfiguration()) != IncrementalMode.NONE) {
                logger.warn(
                        "Instant Run: Resource shrinker automatically disabled for {}.",
                        scope.getVariantConfiguration().getFullName());
                return;
            }

            if (!buildType.isMinifyEnabled()) {
                androidBuilder
                        .getErrorReporter()
                        .handleSyncError(
                                null,
                                SyncIssue.TYPE_GENERIC,
                                "shrinkResources requires minifyEnabled to be turned on. See "
                                        + "http://d.android.com/r/tools/shrink-resources.html "
                                        + "for more information.");
                return;
            }

            if (buildType.isMinifyEnabled() && !buildType.isUseProguard()) {
                androidBuilder
                        .getErrorReporter()
                        .handleSyncError(
                                null,
                                SyncIssue.TYPE_GENERIC,
                                "Built-in class shrinker and resource shrinking are not supported yet.");
                return;
            }

            return;
        }

        // if resources are shrink, insert a no-op transform per variant output
        // to transform the res package into a stripped res package
        for (final BaseVariantOutputData variantOutputData : scope.getVariantData().getOutputs()) {
            VariantOutputScope variantOutputScope = variantOutputData.getScope();

            ShrinkResourcesTransform shrinkResTransform =
                    new ShrinkResourcesTransform(
                            variantOutputData,
                            variantOutputScope.getProcessResourcePackageOutputFile(),
                            variantOutputScope.getShrinkedResourcesFile(),
                            androidBuilder,
                            logger);
            AndroidTask<TransformTask> shrinkTask =
                    scope.getTransformManager()
                            .addTransform(taskFactory, variantOutputScope, shrinkResTransform)
                            .orElse(null);
            // need to record this task since the package task will not depend
            // on it through the transform manager.
            variantOutputScope.setShrinkResourcesTask(shrinkTask);
        }
    }

    private void applyProguardConfig(
            ProguardConfigurable transform,
            final BaseVariantData<? extends BaseVariantOutputData> variantData) {
        final GradleVariantConfiguration variantConfig = variantData.getVariantConfiguration();
        transform.setConfigurationFiles(
                () -> {
                    Set<File> proguardFiles =
                            variantConfig.getProguardFiles(
                                    true,
                                    Collections.singletonList(
                                            ProguardFiles.getDefaultProguardFile(
                                                    TaskManager.DEFAULT_PROGUARD_CONFIG_FILE,
                                                    project)));

                    // use the first output when looking for the proguard rule output of
                    // the aapt task. The different outputs are not different in a way that
                    // makes this rule file different per output.
                    BaseVariantOutputData outputData = variantData.getMainOutput();
                    proguardFiles.add(outputData.processResourcesTask.getProguardOutputFile());
                    return proguardFiles;
                });

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

    public void createReportTasks(
            TaskFactory tasks,
            final List<BaseVariantData<? extends BaseVariantOutputData>> variantDataList) {
        AndroidTask<DependencyReportTask> dependencyReportTask = androidTasks.create(
                tasks,
                "androidDependencies",
                DependencyReportTask.class,
                task -> {
                    task.setDescription("Displays the Android dependencies of the project.");
                    task.setVariants(variantDataList);
                    task.setGroup(ANDROID_GROUP);
                });

        // TODO remove this completely when unnecessary
        if (AndroidGradleOptions.isImprovedDependencyResolutionEnabled(project)) {
            for (BaseVariantData<? extends BaseVariantOutputData> variantData : variantDataList) {
                VariantScope scope = variantData.getScope();
                scope.setResolveDependenciesTask(
                        androidTasks.create(
                                tasks,
                                new ResolveDependenciesTask.ConfigAction(
                                        scope,
                                        dependencyManager)));
                if (scope.getTestedVariantData() != null) {
                    scope.getResolveDependenciesTask().dependsOn(tasks, scope.getTestedVariantData().getScope().getResolveDependenciesTask());
                }
                dependencyReportTask.dependsOn(
                        tasks,
                        scope.getResolveDependenciesTask());
            }
        }

        androidTasks.create(tasks, "signingReport", SigningReportTask.class,
                task -> {
                    task.setDescription("Displays the signing info for each variant.");
                    task.setVariants(variantDataList);
                    task.setGroup(ANDROID_GROUP);
                });
    }

    public void createAnchorTasks(@NonNull TaskFactory tasks, @NonNull VariantScope scope) {
        createPreBuildTasks(tasks, scope);

        // also create sourceGenTask
        final BaseVariantData<? extends BaseVariantOutputData> variantData = scope.getVariantData();
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

    private void createPreBuildTasks(@NonNull TaskFactory tasks, @NonNull VariantScope scope) {
        final BaseVariantData<? extends BaseVariantOutputData> variantData = scope.getVariantData();
        scope.setPreBuildTask(androidTasks.create(tasks,
                scope.getTaskName("pre", "Build"), task -> {
                    variantData.preBuildTask = task;
                }));
        scope.getPreBuildTask().dependsOn(tasks, MAIN_PREBUILD);

        if (isMinifyEnabled(scope)) {
            scope.getPreBuildTask().dependsOn(tasks, EXTRACT_PROGUARD_FILES);
        }

        // for all libraries required by the configurations of this variant, make this task
        // depend on all the tasks preparing these libraries.
//        VariantDependencies variantDependencies = variantData.getVariantDependency();

        // TODO we dont want the prepare dependencies task anymore.
        //AndroidTask<PrepareDependenciesTask> prepareDependenciesTask = androidTasks.create(tasks,
        //        new PrepareDependenciesTask.ConfigAction(scope, variantDependencies));
        //scope.setPrepareDependenciesTask(prepareDependenciesTask);
        //
        //prepareDependenciesTask.dependsOn(tasks, scope.getPreBuildTask());
        //prepareDependenciesTask.dependsOn(tasks, variantDependencies.getCompileConfiguration());
        //prepareDependenciesTask.dependsOn(tasks, variantDependencies.getPackageConfiguration());

        // TODO completely remove this when it's not needed anymore.
        //if (AndroidGradleOptions.isImprovedDependencyResolutionEnabled(project)) {
        //    AndroidTask<ResolveDependenciesTask> resolveDependenciesTask =
        //            androidTasks.create(
        //                    tasks,
        //                    new ResolveDependenciesTask.ConfigAction(scope, dependencyManager));
        //    scope.setResolveDependenciesTask(resolveDependenciesTask);
        //    scope.getPreBuildTask().dependsOn(tasks, resolveDependenciesTask);
        //    resolveDependenciesTask.dependsOn(
        //            tasks,
        //            variantDependencies.getCompileConfiguration(),
        //            variantDependencies.getPackageConfiguration());
        //
        //    // Dependency of the tested variant must be resolved before test variant.
        //    if (variantData instanceof TestVariantData) {
        //        BaseVariantData testedVariantData =
        //                (BaseVariantData) ((TestVariantData) variantData).getTestedVariantData();
        //        VariantScope testedScope = testedVariantData.getScope();
        //        resolveDependenciesTask.dependsOn(tasks, testedScope.getResolveDependenciesTask());
        //    }
        //} else {
        //    dependencyManager.addDependenciesToPrepareTask(
        //            tasks,
        //            variantData,
        //            prepareDependenciesTask);
        //}
    }

    private void createCompileAnchorTask(
            @NonNull TaskFactory tasks, @NonNull final VariantScope scope) {
        final BaseVariantData<? extends BaseVariantOutputData> variantData = scope.getVariantData();
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
        testData.setAnimationsDisabled(
                getGlobalScope().getExtension().getTestOptions().getAnimationsDisabled());
    }
}
