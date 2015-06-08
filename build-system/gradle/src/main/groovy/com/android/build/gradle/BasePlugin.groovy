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

package com.android.build.gradle

import com.android.annotations.Nullable
import com.android.annotations.VisibleForTesting
import com.android.build.gradle.internal.ApiObjectFactory
import com.android.build.gradle.internal.BadPluginException
import com.android.build.gradle.internal.DependencyManager
import com.android.build.gradle.internal.ExtraModelInfo
import com.android.build.gradle.internal.LibraryCache
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.SdkHandler
import com.android.build.gradle.internal.TaskContainerAdaptor
import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.internal.VariantManager
import com.android.build.gradle.internal.coverage.JacocoPlugin
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.dsl.BuildTypeFactory
import com.android.build.gradle.internal.dsl.ProductFlavor
import com.android.build.gradle.internal.dsl.ProductFlavorFactory
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.build.gradle.internal.dsl.SigningConfigFactory
import com.android.build.gradle.internal.NativeLibraryFactoryImpl
import com.android.build.gradle.internal.model.ModelBuilder
import com.android.build.gradle.internal.process.GradleJavaProcessExecutor
import com.android.build.gradle.internal.process.GradleProcessExecutor
import com.android.build.gradle.internal.profile.RecordingBuildListener
import com.android.build.gradle.internal.profile.SpanRecorders
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.VariantFactory
import com.android.build.gradle.internal.NdkHandler
import com.android.build.gradle.tasks.JillTask
import com.android.build.gradle.tasks.PreDex
import com.android.builder.Version
import com.android.builder.core.AndroidBuilder
import com.android.builder.core.BuilderConstants
import com.android.builder.internal.compiler.JackConversionCache
import com.android.builder.internal.compiler.PreDexCache
import com.android.builder.profile.ExecutionType
import com.android.builder.profile.ProcessRecorderFactory
import com.android.builder.profile.ThreadRecorder
import com.android.builder.sdk.TargetInfo
import com.android.ide.common.blame.output.BlameAwareLoggedProcessOutputHandler
import com.android.ide.common.internal.ExecutorSingleton
import com.android.utils.ILogger
import com.google.common.base.CharMatcher
import groovy.transform.CompileStatic
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.logging.LogLevel
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.StopExecutionException
import org.gradle.internal.reflect.Instantiator
import org.gradle.tooling.BuildException
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry

import java.security.MessageDigest
import java.util.jar.Manifest
import java.util.regex.Pattern

import static com.android.builder.model.AndroidProject.FD_INTERMEDIATES
import static com.google.common.base.Preconditions.checkState
import static java.io.File.separator

/**
 * Base class for all Android plugins
 */
@CompileStatic
public abstract class BasePlugin {

    private static final String GRADLE_MIN_VERSION = "2.2"
    public static final Pattern GRADLE_ACCEPTABLE_VERSIONS = Pattern.compile("2\\.[2-9].*")
    private static final String GRADLE_VERSION_CHECK_OVERRIDE_PROPERTY =
            "com.android.build.gradle.overrideVersionCheck"
    private static final String SKIP_PATH_CHECK_PROPERTY =
            "com.android.build.gradle.overridePathCheck"
    /** default retirement age in days since its inception date for RC or beta versions. */
    private static final int DEFAULT_RETIREMENT_AGE_FOR_NON_RELEASE = 40


    protected BaseExtension extension

    protected VariantManager variantManager

    protected TaskManager taskManager

    protected Project project

    protected SdkHandler sdkHandler

    private NdkHandler ndkHandler

    protected AndroidBuilder androidBuilder

    protected Instantiator instantiator

    protected VariantFactory variantFactory

    private ToolingModelBuilderRegistry registry

    private JacocoPlugin jacocoPlugin

    private LoggerWrapper loggerWrapper

    private ExtraModelInfo extraModelInfo

    private String creator

    private boolean hasCreatedTasks = false

    protected BasePlugin(Instantiator instantiator, ToolingModelBuilderRegistry registry) {
        this.instantiator = instantiator
        this.registry = registry
        creator = "Android Gradle " + Version.ANDROID_GRADLE_PLUGIN_VERSION
        verifyRetirementAge()

        ModelBuilder.clearCaches();
    }

    /**
     * Verify that this plugin execution is within its public time range.
     */
    private void verifyRetirementAge() {

        Manifest manifest;
        URLClassLoader cl = (URLClassLoader) getClass().getClassLoader();
        try {
            URL url = cl.findResource("META-INF/MANIFEST.MF");
            manifest = new Manifest(url.openStream());
        } catch (IOException ignore) {
            return;
        }

        String inceptionDateAttr = manifest.mainAttributes.getValue("Inception-Date")
        // when running in unit tests, etc... the manifest entries are absent.
        if (inceptionDateAttr == null) {
            return;
        }
        def items = inceptionDateAttr.split(':')
        GregorianCalendar inceptionDate = new GregorianCalendar(Integer.parseInt(items[0]),
                Integer.parseInt(items[1]), Integer.parseInt(items[2]));

        int retirementAge = getRetirementAge(manifest.mainAttributes.getValue("Plugin-Version"))

        if (retirementAge == -1) {
            return;
        }
        Calendar now = GregorianCalendar.getInstance()
        int days = now.minus(inceptionDate)
        if (days > retirementAge) {
            // this plugin is too old.
            String dailyOverride = System.getenv("ANDROID_DAILY_OVERRIDE")
            MessageDigest cript = MessageDigest.getInstance("SHA-1")
            cript.reset()
            // encode the day, not the current time.
            cript.update(
                    "${now.get(Calendar.YEAR)}:${now.get(Calendar.MONTH)}:${now.get(Calendar.DATE)}"
                            .getBytes("utf8"))
            String overrideValue = new BigInteger(1, cript.digest()).toString(16)
            if (dailyOverride == null) {
                String message = """
                    Plugin is too old, please update to a more recent version,
                    or set ANDROID_DAILY_OVERRIDE environment variable to
                    \"${overrideValue}\""""
                System.err.println(message)
                throw new RuntimeException(message)
            } else {
                if (!dailyOverride.equals(overrideValue)) {
                    String message = """
                    Plugin is too old and ANDROID_DAILY_OVERRIDE value is
                    also outdated, please use new value :
                    \"${overrideValue}\""""
                    System.err.println(message)
                    throw new RuntimeException(message)
                }
            }
        }
    }

    private static int getRetirementAge(@Nullable String version) {
        if (version == null || version.contains("rc") || version.contains("beta")
                || version.contains("alpha")) {
            return DEFAULT_RETIREMENT_AGE_FOR_NON_RELEASE
        }
        return -1;
    }

    protected abstract Class<? extends BaseExtension> getExtensionClass()
    protected abstract VariantFactory createVariantFactory()
    protected abstract TaskManager createTaskManager(
            Project project,
            AndroidBuilder androidBuilder,
            AndroidConfig extension,
            SdkHandler sdkHandler,
            DependencyManager dependencyManager,
            ToolingModelBuilderRegistry toolingRegistry)

    /**
     * Return whether this plugin creates Android library.  Should be overridden if true.
     */
    protected boolean isLibrary() {
        return false;
    }

    @VisibleForTesting
    VariantManager getVariantManager() {
        return variantManager
    }

    protected ILogger getLogger() {
        if (loggerWrapper == null) {
            loggerWrapper = new LoggerWrapper(project.logger)
        }

        return loggerWrapper
    }


    protected void apply(Project project) {
        this.project = project

        checkPathForErrors()
        checkModulesForErrors()

        ProcessRecorderFactory.initialize(logger, project.rootProject.
                file("profiler" + System.currentTimeMillis() + ".json"))
        project.gradle.addListener(new RecordingBuildListener(ThreadRecorder.get()));

        SpanRecorders.record(project, ExecutionType.BASE_PLUGIN_PROJECT_CONFIGURE) {
            configureProject()
        }

        SpanRecorders.record(project, ExecutionType.BASE_PLUGIN_PROJECT_BASE_EXTENSTION_CREATION) {
            createExtension()
        }

        SpanRecorders.record(project, ExecutionType.BASE_PLUGIN_PROJECT_TASKS_CREATION) {
            createTasks()
        }
    }

    protected void configureProject() {
        checkGradleVersion()
        extraModelInfo = new ExtraModelInfo(project, isLibrary())
        sdkHandler = new SdkHandler(project, logger)
        androidBuilder = new AndroidBuilder(
                project == project.rootProject ? project.name : project.path,
                creator,
                new GradleProcessExecutor(project),
                new GradleJavaProcessExecutor(project),
                new BlameAwareLoggedProcessOutputHandler(getLogger(),
                        extraModelInfo.getErrorFormatMode()),
                extraModelInfo,
                logger,
                verbose)

        project.apply plugin: JavaBasePlugin

        project.apply plugin: JacocoPlugin
        jacocoPlugin = project.plugins.getPlugin(JacocoPlugin)

        project.tasks.getByName("assemble").description =
                "Assembles all variants of all applications and secondary packages."

        // call back on execution. This is called after the whole build is done (not
        // after the current project is done).
        // This is will be called for each (android) projects though, so this should support
        // being called 2+ times.
        project.gradle.buildFinished {
            ExecutorSingleton.shutdown()
            sdkHandler.unload()
            SpanRecorders.record(project, ExecutionType.BASE_PLUGIN_BUILD_FINISHED) {
                PreDexCache.getCache().clear(
                        project.rootProject.file(
                                "${project.rootProject.buildDir}/${FD_INTERMEDIATES}/dex-cache/cache.xml"),
                        logger)
                JackConversionCache.getCache().clear(
                        project.rootProject.file(
                                "${project.rootProject.buildDir}/${FD_INTERMEDIATES}/jack-cache/cache.xml"),
                        logger)
                LibraryCache.getCache().unload()
            }
            ProcessRecorderFactory.shutdown();
        }

        project.gradle.taskGraph.whenReady { TaskExecutionGraph taskGraph ->
            for (Task task : taskGraph.allTasks) {
                if (task instanceof PreDex) {
                    PreDexCache.getCache().load(
                            project.rootProject.file(
                                    "${project.rootProject.buildDir}/${FD_INTERMEDIATES}/dex-cache/cache.xml"))
                    break;
                } else if (task instanceof JillTask) {
                    JackConversionCache.getCache().load(
                            project.rootProject.file(
                                    "${project.rootProject.buildDir}/${FD_INTERMEDIATES}/jack-cache/cache.xml"))
                    break;
                }
            }
        }
    }

    private void createExtension() {
        def buildTypeContainer = project.container(BuildType,
                new BuildTypeFactory(instantiator, project, project.getLogger()))
        def productFlavorContainer = project.container(ProductFlavor,
                new ProductFlavorFactory(instantiator, project, project.getLogger()))
        def signingConfigContainer = project.container(SigningConfig,
                new SigningConfigFactory(instantiator))

        extension = project.extensions.create('android', getExtensionClass(),
                (ProjectInternal) project, instantiator, androidBuilder, sdkHandler,
                buildTypeContainer, productFlavorContainer, signingConfigContainer,
                extraModelInfo, isLibrary())

        // create the default mapping configuration.
        project.configurations.create("default-mapping").description = "Configuration for default mapping artifacts."
        project.configurations.create("default-metadata").description = "Metadata for the produced APKs."

        DependencyManager dependencyManager = new DependencyManager(project, extraModelInfo)
        taskManager = createTaskManager(
                project,
                androidBuilder,
                extension,
                sdkHandler,
                dependencyManager,
                registry)

        variantFactory = createVariantFactory()
        variantManager = new VariantManager(
                project,
                androidBuilder,
                extension,
                variantFactory,
                taskManager,
                instantiator)

        ndkHandler = new NdkHandler(
                project.rootDir,
                null, /* compileSkdVersion, this will be set in afterEvaluate */
                "gcc",
                "" /*toolchainVersion*/);

        // Register a builder for the custom tooling model
        ModelBuilder modelBuilder = new ModelBuilder(
                androidBuilder,
                variantManager,
                taskManager,
                extension,
                extraModelInfo,
                ndkHandler,
                new NativeLibraryFactoryImpl(ndkHandler),
                isLibrary())
        registry.register(modelBuilder);

        // map the whenObjectAdded callbacks on the containers.
        signingConfigContainer.whenObjectAdded { SigningConfig signingConfig ->
            variantManager.addSigningConfig(signingConfig)
        }

        buildTypeContainer.whenObjectAdded { BuildType buildType ->
            SigningConfig signingConfig = signingConfigContainer.findByName(BuilderConstants.DEBUG)
            buildType.init(signingConfig)
            variantManager.addBuildType(buildType)
        }

        productFlavorContainer.whenObjectAdded { ProductFlavor productFlavor ->
            variantManager.addProductFlavor(productFlavor)
        }

        // map whenObjectRemoved on the containers to throw an exception.
        signingConfigContainer.whenObjectRemoved {
            throw new UnsupportedOperationException("Removing signingConfigs is not supported.")
        }
        buildTypeContainer.whenObjectRemoved {
            throw new UnsupportedOperationException("Removing build types is not supported.")
        }
        productFlavorContainer.whenObjectRemoved {
            throw new UnsupportedOperationException("Removing product flavors is not supported.")
        }

        // create default Objects, signingConfig first as its used by the BuildTypes.
        variantFactory.createDefaultComponents(buildTypeContainer, productFlavorContainer, signingConfigContainer)
    }

    private void createTasks() {
        SpanRecorders.record(project, ExecutionType.TASK_MANAGER_CREATE_TASKS) {
            taskManager.createTasksBeforeEvaluate(new TaskContainerAdaptor(project.getTasks()))
        }

        project.afterEvaluate {
            SpanRecorders.record(project, ExecutionType.BASE_PLUGIN_CREATE_ANDROID_TASKS) {
                createAndroidTasks(false)
            }
        }
    }

    private void checkGradleVersion() {
        if (!GRADLE_ACCEPTABLE_VERSIONS.matcher(project.getGradle().gradleVersion).matches()) {
            boolean allowNonMatching = Boolean.getBoolean(GRADLE_VERSION_CHECK_OVERRIDE_PROPERTY)
            File file = new File("gradle" + separator + "wrapper" + separator +
                    "gradle-wrapper.properties");
            String errorMessage = String.format(
                "Gradle version %s is required. Current version is %s. " +
                "If using the gradle wrapper, try editing the distributionUrl in %s " +
                "to gradle-%s-all.zip",
                GRADLE_MIN_VERSION, project.getGradle().gradleVersion, file.getAbsolutePath(),
                GRADLE_MIN_VERSION);
            if (allowNonMatching) {
                getLogger().warning(errorMessage)
                getLogger().warning("As %s is set, continuing anyways.",
                        GRADLE_VERSION_CHECK_OVERRIDE_PROPERTY)
            } else {
                throw new BuildException(errorMessage, null)
            }
        }
    }

    @VisibleForTesting
    final void createAndroidTasks(boolean force) {
        // Make sure unit tests set the required fields.
        checkState(extension.getBuildToolsRevision() != null, "buildToolsVersion is not specified.")
        checkState(extension.getCompileSdkVersion() != null, "compileSdkVersion is not specified.")

        ndkHandler.compileSdkVersion = extension.compileSdkVersion

        // get current plugins and look for the default Java plugin.
        if (project.plugins.hasPlugin(JavaPlugin.class)) {
            throw new BadPluginException(
                    "The 'java' plugin has been applied, but it is not compatible with the Android plugins.")
        }

        ensureTargetSetup()

        // don't do anything if the project was not initialized.
        // Unless TEST_SDK_DIR is set in which case this is unit tests and we don't return.
        // This is because project don't get evaluated in the unit test setup.
        // See AppPluginDslTest
        if (!force
                && (!project.state.executed || project.state.failure != null)
                && SdkHandler.sTestSdkFolder == null) {
            return
        }

        if (hasCreatedTasks) {
            return
        }
        hasCreatedTasks = true

        extension.disableWrite()

        // setup SDK repositories.
        for (File file : sdkHandler.sdkLoader.repositories) {
            project.repositories.maven { MavenArtifactRepository repo ->
                repo.url = file.toURI()
            }
        }

        taskManager.createMockableJarTask()
        SpanRecorders.record(project, ExecutionType.VARIANT_MANAGER_CREATE_ANDROID_TASKS) {
            variantManager.createAndroidTasks()
            ApiObjectFactory apiObjectFactory =
                    new ApiObjectFactory(androidBuilder, extension, variantFactory, instantiator)
            for (BaseVariantData variantData : variantManager.getVariantDataList())  {
                apiObjectFactory.create(variantData)
            }
        }
    }

    private boolean isVerbose() {
        return project.logger.isEnabled(LogLevel.INFO)
    }

    private void ensureTargetSetup() {
        // check if the target has been set.
        TargetInfo targetInfo = androidBuilder.getTargetInfo()
        if (targetInfo == null) {
            if (extension.getCompileOptions() == null) {
                throw new GradleException("Calling getBootClasspath before compileSdkVersion")
            }

            sdkHandler.initTarget(
                    extension.getCompileSdkVersion(),
                    extension.buildToolsRevision,
                    extension.getLibraryRequests(),
                    androidBuilder)
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
                String message = """
Your project contains 2 or more modules with the same identification ${id}
at "${subProjectsById.get(id).getPath()}" and "${subProject.getPath()}".
You must use different identification (either name or group) for each modules.
""".trim();
                throw new StopExecutionException(message)
            } else {
                subProjectsById.put(id, subProject);
            }
        }
    }

    private void checkPathForErrors() {
        // See if the user disabled the check:
        if (Boolean.getBoolean(SKIP_PATH_CHECK_PROPERTY)) {
            return
        }

        if (project.hasProperty(SKIP_PATH_CHECK_PROPERTY)
                && project.property(SKIP_PATH_CHECK_PROPERTY) instanceof String
                && Boolean.valueOf((String) project.property(SKIP_PATH_CHECK_PROPERTY))) {
            return
        }

        // See if we're on Windows:
        if (!System.getProperty('os.name').toLowerCase().contains('windows')) {
            return
        }

        // See if the path contains non-ASCII characters.
        if (CharMatcher.ASCII.matchesAllOf(project.rootDir.absolutePath)) {
            return
        }

        String message = """
Your project path contains non-ASCII characters. This will most likely
cause the build to fail on Windows. Please move your project to a different
directory. See http://b.android.com/95744 for details.

This warning can be disabled by using the command line flag
-D${SKIP_PATH_CHECK_PROPERTY}=true, or adding the line
'${SKIP_PATH_CHECK_PROPERTY}=true' to gradle.properties file
in the project directory.""".trim()

        throw new StopExecutionException(message)
    }
}
