/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.build.gradle;

import static com.android.builder.model.AndroidProject.FD_INTERMEDIATES;
import static com.google.common.base.Preconditions.checkState;
import static java.io.File.separator;

import android.databinding.tool.DataBindingBuilder;
import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.build.api.transform.Transform;
import com.android.build.gradle.api.AndroidBasePlugin;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.ApiObjectFactory;
import com.android.build.gradle.internal.BadPluginException;
import com.android.build.gradle.internal.BuildCacheUtils;
import com.android.build.gradle.internal.ExecutionConfigurationUtil;
import com.android.build.gradle.internal.ExtraModelInfo;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.NativeLibraryFactoryImpl;
import com.android.build.gradle.internal.NonFinalPluginExpiry;
import com.android.build.gradle.internal.SdkHandler;
import com.android.build.gradle.internal.TaskContainerAdaptor;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.ToolingRegistryProvider;
import com.android.build.gradle.internal.VariantManager;
import com.android.build.gradle.internal.coverage.JacocoPlugin;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.BuildTypeFactory;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.ProductFlavorFactory;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.dsl.SigningConfigFactory;
import com.android.build.gradle.internal.ide.ModelBuilder;
import com.android.build.gradle.internal.ide.NativeModelBuilder;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.build.gradle.internal.pipeline.TransformTask;
import com.android.build.gradle.internal.process.GradleJavaProcessExecutor;
import com.android.build.gradle.internal.process.GradleProcessExecutor;
import com.android.build.gradle.internal.profile.ProfilerInitializer;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.TaskInputHelper;
import com.android.build.gradle.internal.transforms.DexTransform;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.VariantFactory;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.IntegerOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.tasks.ExternalNativeBuildTaskUtils;
import com.android.build.gradle.tasks.ExternalNativeJsonGenerator;
import com.android.builder.Version;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.BuilderConstants;
import com.android.builder.internal.compiler.PreDexCache;
import com.android.builder.model.AndroidProject;
import com.android.builder.profile.ProcessProfileWriter;
import com.android.builder.profile.Recorder;
import com.android.builder.profile.ThreadRecorder;
import com.android.builder.sdk.SdkLibData;
import com.android.builder.sdk.TargetInfo;
import com.android.builder.utils.FileCache;
import com.android.dx.command.dexer.Main;
import com.android.ide.common.internal.ExecutorSingleton;
import com.android.ide.common.repository.GradleVersion;
import com.android.repository.api.Channel;
import com.android.repository.api.ConsoleProgressIndicator;
import com.android.repository.api.Downloader;
import com.android.repository.api.SettingsController;
import com.android.repository.impl.downloader.LocalFileAwareDownloader;
import com.android.repository.io.FileOpUtils;
import com.android.sdklib.repository.legacy.LegacyDownloader;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.google.common.base.CharMatcher;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan.ExecutionType;
import com.google.wireless.android.sdk.stats.GradleBuildProject;
import java.io.File;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.function.Supplier;
import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.StopExecutionException;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.tooling.UnsupportedVersionException;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/**
 * Base class for all Android plugins
 */
public abstract class BasePlugin implements ToolingRegistryProvider {

    @VisibleForTesting
    public static final GradleVersion GRADLE_MIN_VERSION =
            GradleVersion.parse(SdkConstants.GRADLE_MINIMUM_VERSION);

    private BaseExtension extension;

    private VariantManager variantManager;

    private TaskManager taskManager;

    private Project project;

    private ProjectOptions projectOptions;

    private SdkHandler sdkHandler;

    private NdkHandler ndkHandler;

    private AndroidBuilder androidBuilder;

    private DataBindingBuilder dataBindingBuilder;

    private Instantiator instantiator;

    private VariantFactory variantFactory;

    private ToolingModelBuilderRegistry registry;

    private LoggerWrapper loggerWrapper;

    private ExtraModelInfo extraModelInfo;

    private String creator;

    private Recorder threadRecorder;

    private boolean hasCreatedTasks = false;

    BasePlugin(@NonNull Instantiator instantiator, @NonNull ToolingModelBuilderRegistry registry) {
        this.instantiator = instantiator;
        this.registry = registry;
        creator = "Android Gradle " + Version.ANDROID_GRADLE_PLUGIN_VERSION;
        NonFinalPluginExpiry.verifyRetirementAge();

        ModelBuilder.clearCaches();
    }


    @NonNull
    protected abstract BaseExtension createExtension(
            @NonNull Project project,
            @NonNull ProjectOptions projectOptions,
            @NonNull Instantiator instantiator,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull SdkHandler sdkHandler,
            @NonNull NamedDomainObjectContainer<BuildType> buildTypeContainer,
            @NonNull NamedDomainObjectContainer<ProductFlavor> productFlavorContainer,
            @NonNull NamedDomainObjectContainer<SigningConfig> signingConfigContainer,
            @NonNull NamedDomainObjectContainer<BaseVariantOutput> buildOutputs,
            @NonNull ExtraModelInfo extraModelInfo);

    @NonNull
    protected abstract GradleBuildProject.PluginType getAnalyticsPluginType();

    @NonNull
    protected abstract VariantFactory createVariantFactory(
            @NonNull GlobalScope globalScope,
            @NonNull Instantiator instantiator,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull AndroidConfig androidConfig);

    @NonNull
    protected abstract TaskManager createTaskManager(
            @NonNull GlobalScope globalScope,
            @NonNull Project project,
            @NonNull ProjectOptions projectOptions,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull DataBindingBuilder dataBindingBuilder,
            @NonNull AndroidConfig androidConfig,
            @NonNull SdkHandler sdkHandler,
            @NonNull NdkHandler ndkHandler,
            @NonNull ToolingModelBuilderRegistry toolingRegistry,
            @NonNull Recorder threadRecorder);

    protected abstract int getProjectType();

    @VisibleForTesting
    public VariantManager getVariantManager() {
        return variantManager;
    }

    @VisibleForTesting
    BaseExtension getExtension() {
        return extension;
    }

    @VisibleForTesting
    AndroidBuilder getAndroidBuilder() {
        return androidBuilder;
    }

    private ILogger getLogger() {
        if (loggerWrapper == null) {
            loggerWrapper = new LoggerWrapper(project.getLogger());
        }

        return loggerWrapper;
    }

    protected void apply(@NonNull Project project) {
        checkPluginVersion();
        project.getPluginManager().apply(AndroidBasePlugin.class);

        TaskInputHelper.enableBypass();

        this.project = project;
        this.projectOptions = new ProjectOptions(project);
        ExecutionConfigurationUtil.setThreadPoolSize(projectOptions);
        checkPathForErrors();
        checkModulesForErrors();

        ProfilerInitializer.init(project, projectOptions);
        threadRecorder = ThreadRecorder.get();

        ProcessProfileWriter.getProject(project.getPath())
                .setAndroidPluginVersion(Version.ANDROID_GRADLE_PLUGIN_VERSION)
                .setAndroidPlugin(getAnalyticsPluginType())
                .setPluginGeneration(GradleBuildProject.PluginGeneration.FIRST);

        threadRecorder.record(
                ExecutionType.BASE_PLUGIN_PROJECT_CONFIGURE,
                project.getPath(),
                null,
                this::configureProject);

        threadRecorder.record(
                ExecutionType.BASE_PLUGIN_PROJECT_BASE_EXTENSION_CREATION,
                project.getPath(),
                null,
                this::configureExtension);

        threadRecorder.record(
                ExecutionType.BASE_PLUGIN_PROJECT_TASKS_CREATION,
                project.getPath(),
                null,
                this::createTasks);
    }

    private void configureProject() {
        extraModelInfo = new ExtraModelInfo(projectOptions, project.getLogger());
        checkGradleVersion();

        sdkHandler = new SdkHandler(project, getLogger());

        if (!project.getGradle().getStartParameter().isOffline()
                && projectOptions.get(BooleanOption.ENABLE_SDK_DOWNLOAD)
                && !projectOptions.get(BooleanOption.IDE_INVOKED_FROM_IDE)) {
            SdkLibData sdkLibData = SdkLibData.download(getDownloader(), getSettingsController());
            sdkHandler.setSdkLibData(sdkLibData);
        }

        androidBuilder = new AndroidBuilder(
                project == project.getRootProject() ? project.getName() : project.getPath(),
                creator,
                new GradleProcessExecutor(project),
                new GradleJavaProcessExecutor(project),
                extraModelInfo,
                getLogger(),
                isVerbose());
        dataBindingBuilder = new DataBindingBuilder();
        dataBindingBuilder.setPrintMachineReadableOutput(
                extraModelInfo.getErrorFormatMode() ==
                        ExtraModelInfo.ErrorFormatMode.MACHINE_PARSABLE);

        // Apply the Java and Jacoco plugins.
        project.getPlugins().apply(JavaBasePlugin.class);
        project.getPlugins().apply(JacocoPlugin.class);

        project.getTasks()
                .getByName("assemble")
                .setDescription(
                        "Assembles all variants of all applications and secondary packages.");

        // call back on execution. This is called after the whole build is done (not
        // after the current project is done).
        // This is will be called for each (android) projects though, so this should support
        // being called 2+ times.
        project.getGradle()
                .addBuildListener(
                        new BuildListener() {
                            @Override
                            public void buildStarted(Gradle gradle) {
                                TaskInputHelper.enableBypass();
                            }

                            @Override
                            public void settingsEvaluated(Settings settings) {}

                            @Override
                            public void projectsLoaded(Gradle gradle) {}

                            @Override
                            public void projectsEvaluated(Gradle gradle) {}

                            @Override
                            public void buildFinished(BuildResult buildResult) {
                                // Do not run buildFinished for included project in composite build.
                                if (buildResult.getGradle().getParent() != null) {
                                    return;
                                }
                                ExecutorSingleton.shutdown();
                                sdkHandler.unload();
                                threadRecorder.record(
                                        ExecutionType.BASE_PLUGIN_BUILD_FINISHED,
                                        project.getPath(),
                                        null,
                                        () -> {
                                            PreDexCache.getCache()
                                                    .clear(
                                                            FileUtils.join(
                                                                    project.getRootProject()
                                                                            .getBuildDir(),
                                                                    FD_INTERMEDIATES,
                                                                    "dex-cache",
                                                                    "cache.xml"),
                                                            getLogger());
                                            Main.clearInternTables();
                                        });
                            }
                        });

        project.getGradle()
                .getTaskGraph()
                .addTaskExecutionGraphListener(
                        taskGraph -> {
                            TaskInputHelper.disableBypass();

                            for (Task task : taskGraph.getAllTasks()) {
                                if (task instanceof TransformTask) {
                                    Transform transform = ((TransformTask) task).getTransform();
                                    if (transform instanceof DexTransform) {
                                        PreDexCache.getCache()
                                                .load(
                                                        FileUtils.join(
                                                                project.getRootProject()
                                                                        .getBuildDir(),
                                                                FD_INTERMEDIATES,
                                                                "dex-cache",
                                                                "cache.xml"));
                                        break;
                                    }
                                }
                            }
                        });
    }

    private void configureExtension() {
        final NamedDomainObjectContainer<BuildType> buildTypeContainer =
                project.container(
                        BuildType.class,
                        new BuildTypeFactory(instantiator, project, extraModelInfo));
        final NamedDomainObjectContainer<ProductFlavor> productFlavorContainer =
                project.container(
                        ProductFlavor.class,
                        new ProductFlavorFactory(
                                instantiator, project, project.getLogger(), extraModelInfo));
        final NamedDomainObjectContainer<SigningConfig> signingConfigContainer =
                project.container(SigningConfig.class, new SigningConfigFactory(instantiator));

        final NamedDomainObjectContainer<BaseVariantOutput> buildOutputs =
                project.container(BaseVariantOutput.class);

        project.getExtensions().add("buildOutputs", buildOutputs);

        extension =
                createExtension(
                        project,
                        projectOptions,
                        instantiator,
                        androidBuilder,
                        sdkHandler,
                        buildTypeContainer,
                        productFlavorContainer,
                        signingConfigContainer,
                        buildOutputs,
                        extraModelInfo);

        ndkHandler =
                new NdkHandler(
                        project.getRootDir(),
                        null, /* compileSkdVersion, this will be set in afterEvaluate */
                        "gcc",
                        "" /*toolchainVersion*/,
                        false /* useUnifiedHeaders */);


        @Nullable
        FileCache buildCache = BuildCacheUtils.createBuildCacheIfEnabled(project, projectOptions);

        // This needs to be lazy, because rootProject.buildDir may be changed after the plugin is applied.
        Supplier<FileCache> projectLevelCache =
                () -> BuildCacheUtils.createProjectLevelCache(project);

        GlobalScope globalScope =
                new GlobalScope(
                        project,
                        projectOptions,
                        androidBuilder,
                        extension,
                        sdkHandler,
                        ndkHandler,
                        registry,
                        buildCache,
                        projectLevelCache);

        variantFactory = createVariantFactory(globalScope, instantiator, androidBuilder, extension);

        taskManager =
                createTaskManager(
                        globalScope,
                        project,
                        projectOptions,
                        androidBuilder,
                        dataBindingBuilder,
                        extension,
                        sdkHandler,
                        ndkHandler,
                        registry,
                        threadRecorder);

        variantManager =
                new VariantManager(
                        globalScope,
                        project,
                        projectOptions,
                        androidBuilder,
                        extension,
                        variantFactory,
                        taskManager,
                        threadRecorder);

        // Register a builder for the custom tooling model
        ModelBuilder modelBuilder =
                new ModelBuilder(
                        globalScope,
                        androidBuilder,
                        variantManager,
                        taskManager,
                        extension,
                        extraModelInfo,
                        ndkHandler,
                        new NativeLibraryFactoryImpl(ndkHandler),
                        getProjectType(),
                        AndroidProject.GENERATION_ORIGINAL);
        registry.register(modelBuilder);

        // Register a builder for the native tooling model
        NativeModelBuilder nativeModelBuilder = new NativeModelBuilder(variantManager);
        registry.register(nativeModelBuilder);

        // map the whenObjectAdded callbacks on the containers.
        signingConfigContainer.whenObjectAdded(variantManager::addSigningConfig);

        buildTypeContainer.whenObjectAdded(
                buildType -> {
                    SigningConfig signingConfig =
                            signingConfigContainer.findByName(BuilderConstants.DEBUG);
                    buildType.init(signingConfig);
                    variantManager.addBuildType(buildType);
                });

        productFlavorContainer.whenObjectAdded(variantManager::addProductFlavor);

        // map whenObjectRemoved on the containers to throw an exception.
        signingConfigContainer.whenObjectRemoved(
                new UnsupportedAction("Removing signingConfigs is not supported."));
        buildTypeContainer.whenObjectRemoved(
                new UnsupportedAction("Removing build types is not supported."));
        productFlavorContainer.whenObjectRemoved(
                new UnsupportedAction("Removing product flavors is not supported."));

        // create default Objects, signingConfig first as its used by the BuildTypes.
        variantFactory.createDefaultComponents(
                buildTypeContainer, productFlavorContainer, signingConfigContainer);
    }

    private static class UnsupportedAction implements Action<Object> {

        private final String message;

        UnsupportedAction(String message) {
            this.message = message;
        }

        @Override
        public void execute(Object o) {
            throw new UnsupportedOperationException(message);
        }
    }

    private void createTasks() {
        threadRecorder.record(
                ExecutionType.TASK_MANAGER_CREATE_TASKS,
                project.getPath(),
                null,
                () ->
                        taskManager.createTasksBeforeEvaluate(
                                new TaskContainerAdaptor(project.getTasks())));

        project.afterEvaluate(
                project ->
                        threadRecorder.record(
                                ExecutionType.BASE_PLUGIN_CREATE_ANDROID_TASKS,
                                project.getPath(),
                                null,
                                () -> createAndroidTasks(false)));
    }

    private void checkGradleVersion() {
        String currentVersion = project.getGradle().getGradleVersion();
        if (GRADLE_MIN_VERSION.compareTo(currentVersion) > 0) {
            File file = new File("gradle" + separator + "wrapper" + separator +
                    "gradle-wrapper.properties");
            String errorMessage =
                    String.format(
                            "Minimum supported Gradle version is %s. Current version is %s. "
                                    + "If using the gradle wrapper, try editing the distributionUrl in %s "
                                    + "to gradle-%s-all.zip",
                            GRADLE_MIN_VERSION,
                            currentVersion,
                            file.getAbsolutePath(),
                            GRADLE_MIN_VERSION);
            if (projectOptions.get(BooleanOption.VERSION_CHECK_OVERRIDE_PROPERTY)
                    || projectOptions.get(BooleanOption.VERSION_CHECK_OVERRIDE_PROPERTY_OLD)) {
                getLogger().warning(errorMessage);
                getLogger()
                        .warning(
                                "As %s is set, continuing anyway.",
                                BooleanOption.VERSION_CHECK_OVERRIDE_PROPERTY.getPropertyName());
            } else {
                throw new RuntimeException(errorMessage);
            }
        }
    }

    @VisibleForTesting
    final void createAndroidTasks(boolean force) {
        // Make sure unit tests set the required fields.
        checkState(extension.getBuildToolsRevision() != null,
                "buildToolsVersion is not specified.");
        checkState(extension.getCompileSdkVersion() != null, "compileSdkVersion is not specified.");

        ndkHandler.setCompileSdkVersion(extension.getCompileSdkVersion());

        // get current plugins and look for the default Java plugin.
        if (project.getPlugins().hasPlugin(JavaPlugin.class)) {
            throw new BadPluginException(
                    "The 'java' plugin has been applied, but it is not compatible with the Android plugins.");
        }

        ensureTargetSetup();

        // don't do anything if the project was not initialized.
        // Unless TEST_SDK_DIR is set in which case this is unit tests and we don't return.
        // This is because project don't get evaluated in the unit test setup.
        // See AppPluginDslTest
        if (!force
                && (!project.getState().getExecuted() || project.getState().getFailure() != null)
                && SdkHandler.sTestSdkFolder == null) {
            return;
        }

        if (hasCreatedTasks) {
            return;
        }
        hasCreatedTasks = true;

        extension.disableWrite();

        ProcessProfileWriter.getProject(project.getPath())
                .setCompileSdk(extension.getCompileSdkVersion())
                .setBuildToolsVersion(extension.getBuildToolsRevision().toString());

        // setup SDK repositories.
        sdkHandler.addLocalRepositories(project);

        taskManager.addDataBindingDependenciesIfNecessary(extension.getDataBinding());
        threadRecorder.record(
                ExecutionType.VARIANT_MANAGER_CREATE_ANDROID_TASKS,
                project.getPath(),
                null,
                () -> {
                    variantManager.createAndroidTasks();
                    ApiObjectFactory apiObjectFactory =
                            new ApiObjectFactory(
                                    androidBuilder, extension, variantFactory, instantiator);
                    for (VariantScope variantScope : variantManager.getVariantScopes()) {
                        BaseVariantData variantData = variantScope.getVariantData();
                        apiObjectFactory.create(variantData);
                    }
                });

        // Create and read external native build JSON files depending on what's happening right
        // now.
        //
        // CREATE PHASE:
        // Creates JSONs by shelling out to external build system when:
        //   - Any one of AndroidProject.PROPERTY_INVOKED_FROM_IDE,
        //      AndroidProject.PROPERTY_BUILD_MODEL_ONLY_ADVANCED,
        //      AndroidProject.PROPERTY_BUILD_MODEL_ONLY,
        //      AndroidProject.PROPERTY_REFRESH_EXTERNAL_NATIVE_MODEL are set.
        //   - *and* AndroidProject.PROPERTY_REFRESH_EXTERNAL_NATIVE_MODEL is set
        //      or JSON files don't exist or are out-of-date.
        // Create phase may cause ProcessException (from cmake.exe for example)
        //
        // READ PHASE:
        // Reads and deserializes JSONs when:
        //   - Any one of AndroidProject.PROPERTY_INVOKED_FROM_IDE,
        //      AndroidProject.PROPERTY_BUILD_MODEL_ONLY_ADVANCED,
        //      AndroidProject.PROPERTY_BUILD_MODEL_ONLY,
        //      AndroidProject.PROPERTY_REFRESH_EXTERNAL_NATIVE_MODEL are set.
        // Read phase may produce IOException if the file can't be read for standard IO reasons.
        // Read phase may produce JsonSyntaxException in the case that the content of the file is
        // corrupt.
        boolean forceRegeneration =
                projectOptions.get(BooleanOption.IDE_REFRESH_EXTERNAL_NATIVE_MODEL);

        if (ExternalNativeBuildTaskUtils.shouldRegenerateOutOfDateJsons(projectOptions)) {
            threadRecorder.record(
                    ExecutionType.VARIANT_MANAGER_EXTERNAL_NATIVE_CONFIG_VALUES,
                    project.getPath(),
                    null,
                    () -> {
                        for (VariantScope variantScope : variantManager.getVariantScopes()) {
                            ExternalNativeJsonGenerator generator =
                                    variantScope.getExternalNativeJsonGenerator();
                            if (generator != null) {
                                // This will generate any out-of-date or non-existent JSONs.
                                // When refreshExternalNativeModel() is true it will also
                                // force update all JSONs.
                                generator.build(forceRegeneration);

                                variantScope.addExternalNativeBuildConfigValues(
                                        generator.readExistingNativeBuildConfigurations());
                            }
                        }
                    });
        }
    }

    private boolean isVerbose() {
        return project.getLogger().isEnabled(LogLevel.INFO);
    }

    private void ensureTargetSetup() {
        // check if the target has been set.
        TargetInfo targetInfo = androidBuilder.getTargetInfo();
        if (targetInfo == null) {
            if (extension.getCompileOptions() == null) {
                throw new GradleException("Calling getBootClasspath before compileSdkVersion");
            }

            sdkHandler.initTarget(
                    extension.getCompileSdkVersion(),
                    extension.getBuildToolsRevision(),
                    extension.getLibraryRequests(),
                    androidBuilder,
                    SdkHandler.useCachedSdk(projectOptions));

            sdkHandler.ensurePlatformToolsIsInstalled(extraModelInfo);
        }
    }

    /**
     * Check the sub-projects structure :
     * So far, checks that 2 modules do not have the same identification (group+name).
     */
    private void checkModulesForErrors() {
        Project rootProject = project.getRootProject();
        Map<String, Project> subProjectsById = new HashMap<>();
        for (Project subProject : rootProject.getAllprojects()) {
            String id = subProject.getGroup().toString() + ":" + subProject.getName();
            if (subProjectsById.containsKey(id)) {
                String message = String.format(
                        "Your project contains 2 or more modules with the same " +
                                "identification %1$s\n" +
                                "at \"%2$s\" and \"%3$s\".\n" +
                                "You must use different identification (either name or group) for " +
                                "each modules.",
                        id,
                        subProjectsById.get(id).getPath(),
                        subProject.getPath() );
                throw new StopExecutionException(message);
            } else {
                subProjectsById.put(id, subProject);
            }
        }
    }

    /**
     * Verify the plugin version.  If a newer version of gradle-experimental plugin is applied, then
     * builder.jar module will be resolved to a different version than the one this gradle plugin is
     * compiled with.  Throw an error and suggest to update this plugin.
     */
    private static void checkPluginVersion() {
        String actualGradlePluginVersion = Version.ANDROID_GRADLE_PLUGIN_VERSION;
        if(!actualGradlePluginVersion.equals(
                com.android.build.gradle.internal.Version.ANDROID_GRADLE_PLUGIN_VERSION)) {
            throw new UnsupportedVersionException(
                    String.format(
                            "Plugin version mismatch.  "
                                    + "'com.android.tools.build:gradle-experimental:%s' was applied, and it "
                                    + "requires 'com.android.tools.build:gradle:%s'.  Current version is '%s'.  "
                                    + "Please update to version '%s'.",
                            Version.ANDROID_GRADLE_COMPONENT_PLUGIN_VERSION,
                            Version.ANDROID_GRADLE_PLUGIN_VERSION,
                            com.android.build.gradle.internal.Version.ANDROID_GRADLE_PLUGIN_VERSION,
                            Version.ANDROID_GRADLE_PLUGIN_VERSION));
        }
    }

    private void checkPathForErrors() {
        // See if we're on Windows:
        if (!System.getProperty("os.name").toLowerCase(Locale.US).contains("windows")) {
            return;
        }

        // See if the user disabled the check:
        if (projectOptions.get(BooleanOption.OVERRIDE_PATH_CHECK_PROPERTY)
                || projectOptions.get(BooleanOption.OVERRIDE_PATH_CHECK_PROPERTY_OLD)) {
            return;
        }

        // See if the path contains non-ASCII characters.
        if (CharMatcher.ASCII.matchesAllOf(project.getRootDir().getAbsolutePath())) {
            return;
        }

        String message =
                "Your project path contains non-ASCII characters. This will most likely "
                        + "cause the build to fail on Windows. Please move your project to a different "
                        + "directory. See http://b.android.com/95744 for details. "
                        + "This warning can be disabled by adding the line '"
                        + BooleanOption.OVERRIDE_PATH_CHECK_PROPERTY.getPropertyName()
                        + "=true' to gradle.properties file in the project directory.";

        throw new StopExecutionException(message);
    }

    @NonNull
    @Override
    public ToolingModelBuilderRegistry getModelBuilderRegistry() {
        return registry;
    }

    private SettingsController getSettingsController() {
        Proxy proxy = createProxy(System.getProperties(), getLogger());
        return new SettingsController() {
            @Override
            public boolean getForceHttp() {
                return false;
            }

            @Override
            public void setForceHttp(boolean force) {
                // Default, doesn't allow to set force HTTP.
            }

            @Nullable
            @Override
            public Channel getChannel() {
                Integer channel = projectOptions.get(IntegerOption.ANDROID_SDK_CHANNEL);
                if (channel != null) {
                    return Channel.create(channel);
                } else {
                    return Channel.DEFAULT;
                }
            }

            @NonNull
            @Override
            public Proxy getProxy() {
                return proxy;
            }
        };
    }

    @VisibleForTesting
    static Proxy createProxy(@NonNull Properties properties, @NonNull ILogger logger) {
        String host = properties.getProperty("https.proxyHost");
        int port = 443;
        if (host != null) {
            String maybePort = properties.getProperty("https.proxyPort");
            if (maybePort != null) {
                try {
                    port = Integer.parseInt(maybePort);
                } catch (NumberFormatException e) {
                    logger.info("Invalid https.proxyPort '" + maybePort + "', using default 443");
                }
            }
        }
        else {
            host = properties.getProperty("http.proxyHost");
            //noinspection VariableNotUsedInsideIf
            if (host != null) {
                port = 80;
                String maybePort = properties.getProperty("http.proxyPort");
                if (maybePort != null) {
                    try {
                        port = Integer.parseInt(maybePort);
                    } catch (NumberFormatException e) {
                        logger.info("Invalid http.proxyPort '" + maybePort + "', using default 80");
                    }
                }
            }
        }
        if (host != null) {
            InetSocketAddress proxyAddr = createAddress(host, port);
            if (proxyAddr != null) {
                return new Proxy(Proxy.Type.HTTP, proxyAddr);
            }
        }
        return Proxy.NO_PROXY;

    }

    private static InetSocketAddress createAddress(String proxyHost, int proxyPort) {
        try {
            InetAddress address = InetAddress.getByName(proxyHost);
            return new InetSocketAddress(address, proxyPort);
        } catch (UnknownHostException e) {
            new ConsoleProgressIndicator().logWarning("Failed to parse host " + proxyHost);
            return null;
        }
    }

    private Downloader getDownloader() {
        return new LocalFileAwareDownloader(
                new LegacyDownloader(FileOpUtils.create(), getSettingsController()));
    }
}
