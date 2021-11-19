/*
 * Copyright (C) 2020 The Android Open Source Project
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
@file:JvmName("AndroidLintInputs")

package com.android.build.gradle.internal.lint

import com.android.SdkConstants
import com.android.Version
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.component.impl.ComponentImpl
import com.android.build.api.component.impl.UnitTestImpl
import com.android.build.api.variant.ResValue
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.ConsumableCreationConfig
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import com.android.build.gradle.internal.dsl.LintOptions
import com.android.build.gradle.internal.ide.ModelBuilder
import com.android.build.gradle.internal.ide.dependencies.ArtifactCollectionsInputs
import com.android.build.gradle.internal.ide.dependencies.ArtifactCollectionsInputsImpl
import com.android.build.gradle.internal.ide.dependencies.ArtifactHandler
import com.android.build.gradle.internal.ide.dependencies.LibraryDependencyCacheBuildService
import com.android.build.gradle.internal.ide.dependencies.MavenCoordinatesCacheBuildService
import com.android.build.gradle.internal.ide.dependencies.computeBuildMapping
import com.android.build.gradle.internal.ide.dependencies.currentBuild
import com.android.build.gradle.internal.ide.dependencies.getDependencyGraphBuilder
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.LintClassLoaderBuildService
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptions
import com.android.build.gradle.options.StringOption
import com.android.builder.core.VariantType
import com.android.builder.core.VariantTypeImpl
import com.android.builder.errors.EvalIssueException
import com.android.builder.errors.IssueReporter
import com.android.builder.model.ApiVersion
import com.android.builder.model.SourceProvider
import com.android.ide.common.repository.GradleVersion
import com.android.sdklib.AndroidVersion
import com.android.tools.lint.model.DefaultLintModelAndroidArtifact
import com.android.tools.lint.model.DefaultLintModelBuildFeatures
import com.android.tools.lint.model.DefaultLintModelJavaArtifact
import com.android.tools.lint.model.DefaultLintModelLibraryResolver
import com.android.tools.lint.model.DefaultLintModelLintOptions
import com.android.tools.lint.model.DefaultLintModelMavenName
import com.android.tools.lint.model.DefaultLintModelModule
import com.android.tools.lint.model.DefaultLintModelResourceField
import com.android.tools.lint.model.DefaultLintModelSourceProvider
import com.android.tools.lint.model.DefaultLintModelVariant
import com.android.tools.lint.model.LintModelAndroidArtifact
import com.android.tools.lint.model.LintModelBuildFeatures
import com.android.tools.lint.model.LintModelDependencies
import com.android.tools.lint.model.LintModelJavaArtifact
import com.android.tools.lint.model.LintModelLibrary
import com.android.tools.lint.model.LintModelLintOptions
import com.android.tools.lint.model.LintModelModule
import com.android.tools.lint.model.LintModelModuleType
import com.android.tools.lint.model.LintModelNamespacingMode
import com.android.tools.lint.model.LintModelSeverity
import com.android.tools.lint.model.LintModelSourceProvider
import com.android.tools.lint.model.LintModelVariant
import com.android.utils.PathUtils
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

abstract class LintTool {

    /** Lint itself */
    @get:Classpath
    abstract val classpath: ConfigurableFileCollection

    /**
     * The identity of lint used as keys for caches
     *
     * Used both for the [lintCacheDirectory] and for the classloader cache in [AndroidLintWorkAction]
     *
     * For published versions it will include the version of lint from maven e.g. `30.2.0-alpha05`
     * and for -dev versions, also a hash of the jars: `30.2.0-dev_920ff9cabfbb40d0318735f9fe403b9/`
     */
    @get:Input
    abstract val versionKey: Property<String>

    @get:Input
    abstract val runInProcess: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val workerHeapSize: Property<String>

    /**
     * The lint cache parent dir for artifacts recomputable by lint that save analysis time
     */
    @get:Internal
    abstract val lintCacheDirectory: DirectoryProperty

    /**
     * Computes the lint cache dir, cleaning up if lint version has changed
     *
     * This is passed to lint invocations using --cache-dir
     *
     * The lint cache is neither an input nor an output to the lint tasks, so it needs some manual
     * handling to avoid lint trying to load cache items written by a different version of lint.
     *
     * A marker file of lint-cache-version is used, for published versions it will include the
     * version of lint, e.g. `30.2.0-alpha05`
     *
     * And for -dev versions, also a hash of the jars, the same as the classloader hash
     * 30.2.0-dev_920ff9cabfbb40d0318735f9fe403b9
     *
     * Returns the arguments to add to the lint invocation.
     */
    fun initializeLintCacheDir(): List<String> {
        val directory = lintCacheDirectory.get().asFile.toPath()
        val lintVersionMarkerFile = directory.resolve("lint-cache-version.txt")
        val currentVersion = "Cache for Android Lint" + versionKey.get()
        val previousVersion = lintVersionMarkerFile.takeIf { Files.exists(it) }?.let { Files.readAllLines(it).singleOrNull() }
        if (previousVersion != currentVersion) {
            PathUtils.deleteRecursivelyIfExists(directory)
            Files.createDirectories(directory)
            Files.write(lintVersionMarkerFile, listOf(currentVersion))
        }
        return listOf("--cache-dir", directory.toString())
    }

    @get:Internal
    abstract val lintClassLoaderBuildService: Property<LintClassLoaderBuildService>

    fun initialize(taskCreationServices: TaskCreationServices) {
        classpath.fromDisallowChanges(taskCreationServices.lintFromMaven.files)
        lintClassLoaderBuildService.setDisallowChanges(getBuildService(taskCreationServices.buildServiceRegistry))
        versionKey.setDisallowChanges(deriveVersionKey(taskCreationServices, lintClassLoaderBuildService))
        val projectOptions = taskCreationServices.projectOptions
        runInProcess.setDisallowChanges(projectOptions.getProvider(BooleanOption.RUN_LINT_IN_PROCESS))
        workerHeapSize.setDisallowChanges(projectOptions.getProvider(StringOption.LINT_HEAP_SIZE))
        lintCacheDirectory.setDisallowChanges(
            taskCreationServices.projectInfo.buildDirectory.dir("${SdkConstants.FD_INTERMEDIATES}/lint-cache")
        )
    }

    private fun deriveVersionKey(
        taskCreationServices: TaskCreationServices,
        lintClassLoaderBuildService: Provider<LintClassLoaderBuildService>
    ): Provider<String> {
        val lintVersion =
            getLintMavenArtifactVersion(
                taskCreationServices.projectOptions[StringOption.LINT_VERSION_OVERRIDE]?.trim(),
                null
            )
        val versionProvider = taskCreationServices.provider { lintVersion }
        // When using development versions also hash the jar contents to avoid reusing
        // the classloader when the jars might change
        return when {
            lintVersion.endsWith("-dev") || lintVersion.endsWith("SNAPSHOT") -> {
                val jarsHash = lintClassLoaderBuildService.zip(classpath.elements, LintClassLoaderBuildService::hashJars)
                versionProvider.zip(jarsHash) { version, hash -> "${version}_$hash" }
            }
            else -> versionProvider
        }
    }

    fun submit(workerExecutor: WorkerExecutor, mainClass: String, arguments: List<String>) {
        submit(
            workerExecutor,
            mainClass,
            arguments,
            android = true,
            fatalOnly = false,
            await = false
        )
    }

    fun submit(
        workerExecutor: WorkerExecutor,
        mainClass: String,
        arguments: List<String>,
        android: Boolean,
        fatalOnly: Boolean,
        await: Boolean,
        returnValueOutputFile: File? = null
    ) {
        val workQueue = if (runInProcess.get()) {
            workerExecutor.noIsolation()
        } else {
            workerExecutor.processIsolation {
                it.classpath.from(classpath)
                // Default to using the main Gradle daemon heap size to smooth the transition
                // for build authors.
                it.forkOptions.maxHeapSize =
                    workerHeapSize.orNull ?: "${Runtime.getRuntime().maxMemory() / 1024 / 1024}m"
            }
        }
        workQueue.submit(AndroidLintWorkAction::class.java) { parameters ->
            parameters.mainClass.set(mainClass)
            parameters.arguments.set(arguments)
            parameters.classpath.from(classpath)
            parameters.versionKey.set(versionKey)
            parameters.android.set(android)
            parameters.fatalOnly.set(fatalOnly)
            parameters.runInProcess.set(runInProcess.get())
            parameters.returnValueOutputFile.set(returnValueOutputFile)
        }
        if (await) {
            workQueue.await()
        }
    }

}

abstract class ProjectInputs {

    @get:Internal
    abstract val projectDirectoryPath: Property<String>

    // projectDirectoryPathInput should either be (1) unset if the project directory is not an input
    // to the associated task, or (2) set to the same value as projectDirectoryPath otherwise.
    @get:Input
    @get:Optional
    abstract val projectDirectoryPathInput: Property<String>

    @get:Input
    abstract val projectGradlePath: Property<String>

    @get:Input
    abstract val projectType: Property<LintModelModuleType>

    @get:Input
    abstract val mavenGroupId: Property<String>

    @get:Input
    abstract val mavenArtifactId: Property<String>

    @get:Internal
    abstract val buildDirectoryPath: Property<String>

    // buildDirectoryPathInput should either be (1) unset if the build directory is not an input to
    // the associated task, or (2) set to the same value as buildDirectoryPath otherwise.
    @get:Input
    @get:Optional
    abstract val buildDirectoryPathInput: Property<String>

    @get:Nested
    abstract val lintOptions: LintOptionsInput

    @get:Input
    @get:Optional
    abstract val resourcePrefix: Property<String>

    @get:Input
    abstract val dynamicFeatures: ListProperty<String>

    @get:Classpath
    abstract val bootClasspath: ConfigurableFileCollection

    @get:Input
    abstract val javaSourceLevel: Property<JavaVersion>

    @get:Input
    abstract val compileTarget: Property<String>

    /**
     * True if none of the build types used by this module have enabled shrinking,
     * or false if at least one variant's build type is known to use shrinking.
     */
    @get:Input
    abstract val neverShrinking: Property<Boolean>

    internal fun initialize(variant: VariantWithTests, isForAnalysis: Boolean) {
        val creationConfig = variant.main
        val extension = creationConfig.globalScope.extension
        initializeFromProject(creationConfig.services.projectInfo.getProject(), isForAnalysis)
        projectType.setDisallowChanges(creationConfig.variantType.toLintModelModuleType())

        lintOptions.initialize(extension.lintOptions)
        resourcePrefix.setDisallowChanges(extension.resourcePrefix)

        if (extension is BaseAppModuleExtension) {
            dynamicFeatures.setDisallowChanges(extension.dynamicFeatures)
        }
        dynamicFeatures.disallowChanges()

        bootClasspath.fromDisallowChanges(creationConfig.sdkComponents.bootClasspath)
        javaSourceLevel.setDisallowChanges(creationConfig.compileOptions.sourceCompatibility)
        compileTarget.setDisallowChanges(extension.compileSdkVersion)
        // `neverShrinking` is about all variants, so look back to the DSL
        neverShrinking.setDisallowChanges(extension.buildTypes.none { it.isMinifyEnabled })
    }

    internal fun initializeForStandalone(
        project: Project,
        javaConvention: JavaPluginConvention,
        dslLintOptions: LintOptions,
        isForAnalysis: Boolean
    ) {
        initializeFromProject(project, isForAnalysis)
        projectType.setDisallowChanges(LintModelModuleType.JAVA_LIBRARY)
        lintOptions.initialize(dslLintOptions)
        resourcePrefix.setDisallowChanges("")
        dynamicFeatures.setDisallowChanges(setOf())
        val mainSourceSet = javaConvention.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
        val javaCompileTask = project.tasks.named(
            mainSourceSet.compileJavaTaskName,
            JavaCompile::class.java
        )
        bootClasspath.fromDisallowChanges(javaCompileTask.map { it.options.bootstrapClasspath ?: project.files() })
        javaSourceLevel.setDisallowChanges(javaConvention.sourceCompatibility)
        compileTarget.setDisallowChanges("")
        neverShrinking.setDisallowChanges(true)
    }

    private fun initializeFromProject(project: Project, isForAnalysis: Boolean) {
        projectDirectoryPath.setDisallowChanges(project.projectDir.absolutePath)
        projectGradlePath.setDisallowChanges(project.path)
        mavenGroupId.setDisallowChanges(project.group.toString())
        mavenArtifactId.setDisallowChanges(project.name)
        buildDirectoryPath.setDisallowChanges(project.layout.buildDirectory.map { it.asFile.absolutePath })
        if (!isForAnalysis) {
            projectDirectoryPathInput.set(projectDirectoryPath)
            buildDirectoryPathInput.set(buildDirectoryPath)
        }
        projectDirectoryPathInput.disallowChanges()
        buildDirectoryPathInput.disallowChanges()
    }

    internal fun convertToLintModelModule(): LintModelModule {
        return DefaultLintModelModule(
            loader = null,
            dir = File(projectDirectoryPath.get()),
            modulePath = projectGradlePath.get(),
            type = projectType.get(),
            mavenName = DefaultLintModelMavenName(
                mavenGroupId.get(),
                mavenArtifactId.get()
            ),
            gradleVersion = GradleVersion.tryParse(Version.ANDROID_GRADLE_PLUGIN_VERSION),
            buildFolder = File(buildDirectoryPath.get()),
            lintOptions = lintOptions.toLintModel(),
            lintRuleJars = listOf(),
            resourcePrefix = resourcePrefix.orNull,
            dynamicFeatures = dynamicFeatures.get(),
            bootClassPath = bootClasspath.files.toList(),
            javaSourceLevel = javaSourceLevel.get().toString(),
            compileTarget = compileTarget.get(),
            variants = listOf(),
            neverShrinking = neverShrinking.get()
        )
    }
}

internal fun VariantType.toLintModelModuleType(): LintModelModuleType {
    return when (this) {
        // FIXME add other types
        VariantTypeImpl.BASE_APK -> LintModelModuleType.APP
        VariantTypeImpl.LIBRARY -> LintModelModuleType.LIBRARY
        VariantTypeImpl.OPTIONAL_APK -> LintModelModuleType.DYNAMIC_FEATURE
        VariantTypeImpl.TEST_APK -> LintModelModuleType.TEST
        else -> throw RuntimeException("Unsupported VariantTypeImpl value")
    }
}

abstract class LintOptionsInput {
    @get:Input
    abstract val disable: SetProperty<String>
    @get:Input
    abstract val enable: SetProperty<String>
    @get:Input
    abstract val checkOnly: SetProperty<String>
    @get:Input
    abstract val abortOnError: Property<Boolean>
    @get:Input
    abstract val absolutePaths: Property<Boolean>
    @get:Input
    abstract val noLines: Property<Boolean>
    @get:Input
    abstract val quiet: Property<Boolean>
    @get:Input
    abstract val checkAllWarnings: Property<Boolean>
    @get:Input
    abstract val ignoreWarnings: Property<Boolean>
    @get:Input
    abstract val warningsAsErrors: Property<Boolean>
    @get:Input
    abstract val checkTestSources: Property<Boolean>
    @get:Input
    abstract val checkGeneratedSources: Property<Boolean>
    @get:Input
    abstract val explainIssues: Property<Boolean>
    @get:Input
    abstract val showAll: Property<Boolean>
    @get:Input
    abstract val checkDependencies: Property<Boolean>
    @get:Optional
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val lintConfig: RegularFileProperty
    @get:Optional
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val baselineFile: RegularFileProperty
    @get:Input
    abstract val severityOverrides: MapProperty<String, LintModelSeverity>

    fun initialize(lintOptions: LintOptions) {
        disable.setDisallowChanges(lintOptions.disable)
        enable.setDisallowChanges(lintOptions.enable)
        checkOnly.setDisallowChanges(lintOptions.checkOnly)
        abortOnError.setDisallowChanges(lintOptions.isAbortOnError)
        absolutePaths.setDisallowChanges(lintOptions.isAbsolutePaths)
        noLines.setDisallowChanges(lintOptions.isNoLines)
        quiet.setDisallowChanges(lintOptions.isQuiet)
        checkAllWarnings.setDisallowChanges(lintOptions.isCheckAllWarnings)
        ignoreWarnings.setDisallowChanges(lintOptions.isIgnoreWarnings)
        warningsAsErrors.setDisallowChanges(lintOptions.isWarningsAsErrors)
        checkTestSources.setDisallowChanges(lintOptions.isCheckTestSources)
        checkGeneratedSources.setDisallowChanges(lintOptions.isCheckGeneratedSources)
        explainIssues.setDisallowChanges(lintOptions.isExplainIssues)
        showAll.setDisallowChanges(lintOptions.isShowAll)
        checkDependencies.setDisallowChanges(lintOptions.isCheckDependencies)
        lintOptions.lintConfig?.let { lintConfig.set(it) }
        lintConfig.disallowChanges()
        lintOptions.baselineFile?.let { baselineFile.set(it) }
        baselineFile.disallowChanges()
        severityOverrides.setDisallowChanges(lintOptions.severityOverridesMap)
    }

    fun toLintModel(): LintModelLintOptions {
        return DefaultLintModelLintOptions(
            disable=disable.get(),
            enable=enable.get(),
            check=checkOnly.get(),
            abortOnError=abortOnError.get(),
            absolutePaths=absolutePaths.get(),
            noLines=noLines.get(),
            quiet=quiet.get(),
            checkAllWarnings=checkAllWarnings.get(),
            ignoreWarnings=ignoreWarnings.get(),
            warningsAsErrors=warningsAsErrors.get(),
            checkTestSources=checkTestSources.get(),
            ignoreTestSources=false, // Handled in LintTaskManager
            ignoreTestFixturesSources=false, // Handled in LintTaskManager
            checkGeneratedSources=checkGeneratedSources.get(),
            explainIssues=explainIssues.get(),
            showAll=showAll.get(),
            lintConfig=lintConfig.orNull?.asFile,
            // Report setup is handled in the invocation
            textReport=false,
            textOutput=null,
            htmlReport=false,
            htmlOutput=null,
            xmlReport=false,
            xmlOutput=null,
            sarifReport=false,
            sarifOutput=null,
            checkReleaseBuilds=true, // Handled in LintTaskManager & LintPlugin
            checkDependencies=checkDependencies.get(),
            baselineFile=baselineFile.orNull?.asFile,
            severityOverrides=severityOverrides.get(),
        )
    }
}

/**
 * System properties which can affect lint's behavior.
 */
abstract class SystemPropertyInputs {

    @get:Input
    @get:Optional
    abstract val androidLintLogJarProblems: Property<String>

    // Use @get:Internal because javaVendor and javaVersion act as proxy inputs for javaHome
    @get:Internal
    abstract val javaHome: Property<String>

    @get:Input
    @get:Optional
    abstract val javaVendor: Property<String>

    @get:Input
    @get:Optional
    abstract val javaVersion: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:Optional
    abstract val lintApiDatabase: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val lintAutofix: Property<String>

    @get:Input
    @get:Optional
    abstract val lintBaselinesContinue: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:Optional
    abstract val lintConfigurationOverride: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val lintHtmlPrefs: Property<String>

    @get:Input
    @get:Optional
    abstract val lintNullnessIgnoreDeprecated: Property<String>

    @get:Input
    @get:Optional
    abstract val lintUnusedResourcesExcludeTests: Property<String>

    @get:Input
    @get:Optional
    abstract val lintUnusedResourcesIncludeTests: Property<String>

    @get:Input
    @get:Optional
    abstract val userHome: Property<String>

    fun initialize(providerFactory: ProviderFactory, isForAnalysis: Boolean) {
        if (isForAnalysis) {
            lintAutofix.disallowChanges()
            lintBaselinesContinue.disallowChanges()
            lintHtmlPrefs.disallowChanges()
            userHome.disallowChanges()
        } else {
            lintAutofix.setDisallowChanges(providerFactory.systemProperty("lint.autofix"))
            lintBaselinesContinue.setDisallowChanges(
                providerFactory.systemProperty("lint.baselines.continue")
            )
            lintHtmlPrefs.setDisallowChanges(providerFactory.systemProperty("lint.html.prefs"))
            userHome.setDisallowChanges(providerFactory.systemProperty("user.home"))
        }
        androidLintLogJarProblems.setDisallowChanges(
            providerFactory.systemProperty("android.lint.log-jar-problems")
        )
        javaHome.setDisallowChanges(providerFactory.systemProperty("java.home"))
        javaVendor.setDisallowChanges(providerFactory.systemProperty("java.vendor"))
        javaVersion.setDisallowChanges(providerFactory.systemProperty("java.version"))
        lintApiDatabase.fileProvider(
            providerFactory.systemProperty("LINT_API_DATABASE").map {
                // Suppress the warning because the Gradle docs say "May return null"
                @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
                File(it).takeIf { file -> file.isFile }
            }
        )
        lintApiDatabase.disallowChanges()
        lintConfigurationOverride.fileProvider(
            providerFactory.systemProperty("lint.configuration.override").map {
                // Suppress the warning because the Gradle docs say "May return null"
                @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
                File(it).takeIf { file -> file.isFile }
            }
        )
        lintConfigurationOverride.disallowChanges()
        lintNullnessIgnoreDeprecated.setDisallowChanges(
            providerFactory.systemProperty("lint.nullness.ignore-deprecated")
        )
        lintUnusedResourcesExcludeTests.setDisallowChanges(
            providerFactory.systemProperty("lint.unused-resources.exclude-tests")
        )
        lintUnusedResourcesIncludeTests.setDisallowChanges(
            providerFactory.systemProperty("lint.unused-resources.include-tests")
        )
    }
}

/**
 * Environment variables which can affect lint's behavior.
 */
abstract class EnvironmentVariableInputs {

    @get:Input
    @get:Optional
    abstract val androidLintIncludeLdpi: Property<String>

    @get:Input
    @get:Optional
    abstract val androidLintMaxDepth: Property<String>

    @get:Input
    @get:Optional
    abstract val androidLintMaxViewCount: Property<String>

    @get:Input
    @get:Optional
    abstract val androidLintNullnessIgnoreDeprecated: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:Optional
    abstract val lintApiDatabase: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val lintHtmlPrefs: Property<String>

    @get:Input
    @get:Optional
    abstract val lintXmlRoot: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:Optional
    abstract val lintOverrideConfiguration: RegularFileProperty

    fun initialize(providerFactory: ProviderFactory, isForAnalysis: Boolean) {
        if (isForAnalysis) {
            lintHtmlPrefs.disallowChanges()
            lintXmlRoot.disallowChanges()
        } else {
            lintHtmlPrefs.setDisallowChanges(providerFactory.environmentVariable("LINT_HTML_PREFS"))
            lintXmlRoot.setDisallowChanges(providerFactory.environmentVariable("LINT_XML_ROOT"))
        }
        androidLintIncludeLdpi.setDisallowChanges(
            providerFactory.environmentVariable("ANDROID_LINT_INCLUDE_LDPI")
        )
        androidLintMaxDepth.setDisallowChanges(
            providerFactory.environmentVariable("ANDROID_LINT_MAX_DEPTH")
        )
        androidLintMaxViewCount.setDisallowChanges(
            providerFactory.environmentVariable("ANDROID_LINT_MAX_VIEW_COUNT")
        )
        androidLintNullnessIgnoreDeprecated.setDisallowChanges(
            providerFactory.environmentVariable("ANDROID_LINT_NULLNESS_IGNORE_DEPRECATED")
        )
        lintApiDatabase.fileProvider(
            providerFactory.environmentVariable("LINT_API_DATABASE").map {
                // Suppress the warning because the Gradle docs say "May return null"
                @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
                File(it).takeIf { file -> file.isFile }
            }
        )
        lintApiDatabase.disallowChanges()
        lintOverrideConfiguration.fileProvider(
            providerFactory.environmentVariable("LINT_OVERRIDE_CONFIGURATION").map {
                // Suppress the warning because the Gradle docs say "May return null"
                @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
                File(it).takeIf { file -> file.isFile }
            }
        )
        lintOverrideConfiguration.disallowChanges()
    }
}

/**
 * Inputs for the variant.
 */
abstract class VariantInputs {

    @get:Input
    abstract val name: Property<String>

    @get:Input
    abstract val checkDependencies: Property<Boolean>

    @get:Input
    abstract val minifiedEnabled: Property<Boolean>

    @get:Nested
    abstract val mainArtifact: AndroidArtifactInput

    @get:Nested
    @get:Optional
    abstract val testArtifact: Property<JavaArtifactInput>

    @get:Nested
    @get:Optional
    abstract val androidTestArtifact: Property<AndroidArtifactInput>

    @get:Nested
    @get:Optional
    abstract val testFixturesArtifact: Property<AndroidArtifactInput>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    @get:Optional
    abstract val mergedManifest: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    @get:Optional
    abstract val manifestMergeReport: RegularFileProperty

    @get:Input
    abstract val namespace: Property<String>

    @get:Nested
    @get:Optional
    abstract val minSdkVersion: SdkVersionInput

    @get:Nested
    @get:Optional
    abstract val targetSdkVersion: SdkVersionInput

    @get:Input
    abstract val resValues: MapProperty<ResValue.Key, ResValue>

    @get:Input
    abstract val manifestPlaceholders: MapProperty<String, String>

    @get:Input
    abstract val resourceConfigurations: ListProperty<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:Optional
    abstract val proguardFiles: ListProperty<RegularFile>

    // the extracted proguard files are probably also part of the proguardFiles but we need to set
    // the dependency explicitly so Gradle can track it properly.
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:Optional
    abstract val extractedProguardFiles: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:Optional
    abstract val consumerProguardFiles: ListProperty<File>

    @get:Nested
    abstract val sourceProviders: ListProperty<SourceProviderInput>

    @get:Nested
    abstract val testSourceProviders: ListProperty<SourceProviderInput>

    @get:Nested
    @get:Optional
    abstract val testFixturesSourceProviders: ListProperty<SourceProviderInput>

    @get:Input
    abstract val debuggable: Property<Boolean>

    @get:Nested
    abstract val buildFeatures: BuildFeaturesInput

    @get:Internal
    abstract val libraryDependencyCacheBuildService: Property<LibraryDependencyCacheBuildService>

    @get:Internal
    abstract val mavenCoordinatesCache: Property<MavenCoordinatesCacheBuildService>

    /**
     * Initializes the variant inputs
     *
     * @param variantWithTests the [VariantWithTests].
     * @param checkDependencies whether the module dependencies should be modeled as module
     *     dependencies (instead of modeled as external libraries).
     * @param warnIfProjectTreatedAsExternalDependency whether to warn the user if the standalone
     *     plugin is not applied to a java module dependency when checkDependencies is true.
     * @param isForAnalysis whether the inputs are for lint analysis (as opposed to lint reporting
     *     or lint model writing).
     * @param addBaseModuleLintModel whether the base app module should be modeled as a module
     *     dependency if checkDependencies is false. This Boolean only affects dynamic feature
     *     modules, and it has no effect if checkDependencies is true.
     */
    fun initialize(
        variantWithTests: VariantWithTests,
        checkDependencies: Boolean,
        warnIfProjectTreatedAsExternalDependency: Boolean,
        isForAnalysis: Boolean,
        addBaseModuleLintModel: Boolean = false
    ) {
        val creationConfig = variantWithTests.main
        name.setDisallowChanges(creationConfig.name)
        this.checkDependencies.setDisallowChanges(checkDependencies)
        minifiedEnabled.setDisallowChanges(creationConfig.minifiedEnabled)
        mainArtifact.initialize(
            creationConfig as ComponentImpl,
            checkDependencies,
            addBaseModuleLintModel,
            warnIfProjectTreatedAsExternalDependency
        )

        testArtifact.setDisallowChanges(
            variantWithTests.unitTest?.let { unitTest ->
                creationConfig.services.newInstance(JavaArtifactInput::class.java)
                    .initialize(
                        unitTest as UnitTestImpl,
                        checkDependencies = false,
                        addBaseModuleLintModel,
                        warnIfProjectTreatedAsExternalDependency,
                        // analyzing test bytecode is expensive, without much benefit
                        includeClassesOutputDirectories = false
                    )
            }
        )

        androidTestArtifact.setDisallowChanges(
            variantWithTests.androidTest?.let { androidTest ->
                creationConfig.services.newInstance(AndroidArtifactInput::class.java)
                    .initialize(
                        androidTest as ComponentImpl,
                        checkDependencies = false,
                        addBaseModuleLintModel,
                        warnIfProjectTreatedAsExternalDependency,
                        // analyzing test bytecode is expensive, without much benefit
                        includeClassesOutputDirectories = false,
                        // analyzing test generated sources is expensive, without much benefit
                        includeGeneratedSourceFolders = false
                    )
        })

        testFixturesArtifact.setDisallowChanges(
            variantWithTests.testFixtures?.let { testFixtures ->
                creationConfig.services.newInstance(AndroidArtifactInput::class.java)
                    .initialize(
                        testFixtures,
                        checkDependencies = false,
                        addBaseModuleLintModel,
                        warnIfProjectTreatedAsExternalDependency
                    )
            }
        )
        mergedManifest.setDisallowChanges(
            creationConfig.artifacts.get(SingleArtifact.MERGED_MANIFEST)
        )
        // The manifest merge report contains absolute paths, so it's not compatible with the lint
        // analysis task being cacheable.
        if (!isForAnalysis) {
            manifestMergeReport.set(
                creationConfig.artifacts.get(InternalArtifactType.MANIFEST_MERGE_REPORT)
            )
        }
        manifestMergeReport.disallowChanges()
        namespace.setDisallowChanges(creationConfig.namespace)

        minSdkVersion.initialize(creationConfig.minSdkVersion)

        targetSdkVersion.initialize(creationConfig.targetSdkVersion)

        resValues.setDisallowChanges(creationConfig.resValues)

        if (creationConfig is ApkCreationConfig) {
            manifestPlaceholders.setDisallowChanges(
                creationConfig.manifestPlaceholders
            )
        }

        resourceConfigurations.setDisallowChanges(creationConfig.resourceConfigurations)

        sourceProviders.setDisallowChanges(creationConfig.variantSources.sortedSourceProviders.map { sourceProvider ->
            creationConfig.services
                .newInstance(SourceProviderInput::class.java)
                .initialize(sourceProvider, isForAnalysis)
        })

        proguardFiles.setDisallowChanges(creationConfig.proguardFiles)
        extractedProguardFiles.setDisallowChanges(
            creationConfig.globalScope
                .globalArtifacts
                .get(InternalArtifactType.DEFAULT_PROGUARD_FILES)
        )
        consumerProguardFiles.setDisallowChanges(creationConfig.variantScope.consumerProguardFiles)

        val testSourceProviderList: MutableList<SourceProviderInput> = mutableListOf()
        variantWithTests.unitTest?.let { unitTestCreationConfig ->
            testSourceProviderList.addAll(
                unitTestCreationConfig.variantSources.sortedSourceProviders.map { sourceProvider ->
                    creationConfig.services
                        .newInstance(SourceProviderInput::class.java)
                        .initialize(sourceProvider, isForAnalysis, unitTestOnly = true)
                }
            )
        }
        variantWithTests.androidTest?.let { androidTestCreationConfig ->
            testSourceProviderList.addAll(
                androidTestCreationConfig.variantSources.sortedSourceProviders.map { sourceProvider ->
                    creationConfig.services
                        .newInstance(SourceProviderInput::class.java)
                        .initialize(sourceProvider, isForAnalysis, instrumentationTestOnly = true)
                }
            )
        }
        variantWithTests.testFixtures?.let { testFixturesCreationConfig ->
            testFixturesSourceProviders.setDisallowChanges(
                testFixturesCreationConfig.variantSources.sortedSourceProviders.map { sourceProvider ->
                    creationConfig.services
                        .newInstance(SourceProviderInput::class.java)
                        .initialize(
                            sourceProvider,
                            isForAnalysis
                        )
                })
        }
        testSourceProviders.setDisallowChanges(testSourceProviderList.toList())
        debuggable.setDisallowChanges(
            if (creationConfig is ApkCreationConfig) {
                creationConfig.debuggable
            } else true
        )
        buildFeatures.initialize(creationConfig)
        libraryDependencyCacheBuildService.setDisallowChanges(getBuildService(creationConfig.services.buildServiceRegistry))
        mavenCoordinatesCache.setDisallowChanges(getBuildService(creationConfig.services.buildServiceRegistry))
    }

    internal fun initializeForStandalone(
        project: Project,
        javaConvention: JavaPluginConvention,
        projectOptions: ProjectOptions,
        fatalOnly: Boolean,
        checkDependencies: Boolean,
        isForAnalysis: Boolean
    ) {
        val mainSourceSet = javaConvention.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
        val testSourceSet = javaConvention.sourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME)

        name.setDisallowChanges(mainSourceSet.name)
        this.checkDependencies.setDisallowChanges(checkDependencies)
        mainArtifact.initializeForStandalone(project, projectOptions, mainSourceSet, checkDependencies)
        testArtifact.setDisallowChanges(project.objects.newInstance(JavaArtifactInput::class.java).initializeForStandalone(
            project,
            projectOptions,
            testSourceSet,
            checkDependencies,
            // analyzing test bytecode is expensive, without much benefit
            includeClassesOutputDirectories = false
        ))
        androidTestArtifact.disallowChanges()
        testFixturesArtifact.disallowChanges()
        namespace.setDisallowChanges("")
        minSdkVersion.initializeEmpty()
        targetSdkVersion.initializeEmpty()
        manifestPlaceholders.disallowChanges()
        resourceConfigurations.disallowChanges()
        debuggable.setDisallowChanges(true)
        mergedManifest.setDisallowChanges(null)
        manifestMergeReport.setDisallowChanges(null)
        minifiedEnabled.setDisallowChanges(false)
        sourceProviders.setDisallowChanges(listOf(
            project.objects.newInstance(SourceProviderInput::class.java)
                .initializeForStandalone(
                    project,
                    mainSourceSet,
                    isForAnalysis,
                    unitTestOnly = false
                )
        ))
        if (fatalOnly) {
            testSourceProviders.setDisallowChanges(listOf())
        } else {
            testSourceProviders.setDisallowChanges(
                listOf(
                    project.objects.newInstance(SourceProviderInput::class.java)
                        .initializeForStandalone(
                            project,
                            testSourceSet,
                            isForAnalysis,
                            unitTestOnly = true
                        )
                )
            )
        }
        testFixturesSourceProviders.disallowChanges()
        buildFeatures.initializeForStandalone()
        libraryDependencyCacheBuildService.setDisallowChanges(getBuildService(project.gradle.sharedServices))
        mavenCoordinatesCache.setDisallowChanges(getBuildService(project.gradle.sharedServices))
        proguardFiles.setDisallowChanges(null)
        extractedProguardFiles.setDisallowChanges(null)
        consumerProguardFiles.setDisallowChanges(null)
        resValues.disallowChanges()
    }

    fun toLintModel(module: LintModelModule, partialResultsDir: File? = null): LintModelVariant {
        val dependencyCaches = DependencyCaches(
            libraryDependencyCacheBuildService.get().localJarCache,
            mavenCoordinatesCache.get())

        return DefaultLintModelVariant(
            module,
            name.get(),
            useSupportLibraryVectorDrawables = mainArtifact.useSupportLibraryVectorDrawables.get(),
            mainArtifact = mainArtifact.toLintModel(dependencyCaches),
            testArtifact = testArtifact.orNull?.toLintModel(dependencyCaches),
            androidTestArtifact = androidTestArtifact.orNull?.toLintModel(dependencyCaches),
            testFixturesArtifact = testFixturesArtifact.orNull?.toLintModel(dependencyCaches),
            mergedManifest = mergedManifest.orNull?.asFile,
            manifestMergeReport = manifestMergeReport.orNull?.asFile,
            `package` = namespace.get(),
            minSdkVersion = minSdkVersion.toLintModel(),
            targetSdkVersion = targetSdkVersion.toLintModel(),
            resValues =
                resValues.get().map {
                    DefaultLintModelResourceField(
                        it.key.type,
                        it.key.name,
                        it.value.value
                    )
                }.associateBy { it.name },
            manifestPlaceholders = manifestPlaceholders.get(),
            resourceConfigurations = resourceConfigurations.get(),
            proguardFiles = proguardFiles.orNull?.map { it.asFile } ?: listOf(),
            consumerProguardFiles = consumerProguardFiles.orNull ?: listOf(),
            sourceProviders = sourceProviders.get().map { it.toLintModel() },
            testSourceProviders = testSourceProviders.get().map { it.toLintModel() },
            testFixturesSourceProviders = testFixturesSourceProviders.getOrElse(emptyList()).map {
                it.toLintModel()
            },
            debuggable = debuggable.get(),
            shrinkable = mainArtifact.shrinkable.get(),
            buildFeatures = buildFeatures.toLintModel(),
            libraryResolver = DefaultLintModelLibraryResolver(dependencyCaches.libraryMap),
            partialResultsDir = partialResultsDir
        )
    }

}

abstract class BuildFeaturesInput {
    @get:Input
    abstract val viewBinding: Property<Boolean>

    @get:Input
    abstract val coreLibraryDesugaringEnabled: Property<Boolean>

    @get:Input
    abstract val namespacingMode: Property<LintModelNamespacingMode>

    fun initialize(creationConfig: ConsumableCreationConfig) {
        viewBinding.setDisallowChanges(creationConfig.buildFeatures.viewBinding)
        coreLibraryDesugaringEnabled.setDisallowChanges(creationConfig.isCoreLibraryDesugaringEnabled)
        namespacingMode.setDisallowChanges(
            if (creationConfig.namespacedAndroidResources) {
                LintModelNamespacingMode.DISABLED
            } else {
                LintModelNamespacingMode.REQUIRED
            }
        )
    }
    fun initializeForStandalone() {
        viewBinding.setDisallowChanges(false)
        coreLibraryDesugaringEnabled.setDisallowChanges(false)
        namespacingMode.setDisallowChanges(LintModelNamespacingMode.DISABLED)
    }

    fun toLintModel(): LintModelBuildFeatures {
        return DefaultLintModelBuildFeatures(
            viewBinding.get(),
            coreLibraryDesugaringEnabled.get(),
            namespacingMode.get(),
        )
    }
}

abstract class SourceProviderInput {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val manifestFile: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val javaDirectories: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resDirectories: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val assetsDirectories: ConfigurableFileCollection

    // Without javaDirectoriesClasspath, the lint analysis task would be UP-TO-DATE after a change
    // in the *order* of java source directories, which would be incorrect. We can't get rid of
    // javaDirectories entirely because without javaDirectories, the lint analysis task would be
    // UP-TO-DATE after the addition or removal of a non-existent java source directory. We need to
    // set javaDirectoriesClasspath only for lint analysis tasks because other lint tasks set
    // javaDirectoryPaths.
    @get:Classpath
    @get:Optional
    abstract val javaDirectoriesClasspath: ConfigurableFileCollection

    // See comment for javaDirectoriesClasspath
    @get:Classpath
    @get:Optional
    abstract val resDirectoriesClasspath: ConfigurableFileCollection

    // See comment for javaDirectoriesClasspath
    @get:Classpath
    @get:Optional
    abstract val assetsDirectoriesClasspath: ConfigurableFileCollection

    @get:Input
    @get:Optional
    abstract val manifestFilePath: Property<String>

    @get:Input
    @get:Optional
    abstract val javaDirectoryPaths: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val resDirectoryPaths: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val assetsDirectoryPaths: ListProperty<String>

    @get:Input
    abstract val debugOnly: Property<Boolean>

    @get:Input
    abstract val unitTestOnly: Property<Boolean>

    @get:Input
    abstract val instrumentationTestOnly: Property<Boolean>

    @get:Input
    abstract val name: Property<String>

    internal fun initialize(
        sourceProvider: SourceProvider,
        isForAnalysis: Boolean,
        unitTestOnly: Boolean = false,
        instrumentationTestOnly: Boolean = false
    ): SourceProviderInput {
        this.manifestFile.set(sourceProvider.manifestFile)
        val javaDirectories = sourceProvider.javaDirectories + sourceProvider.kotlinDirectories
        this.javaDirectories.fromDisallowChanges(javaDirectories)
        this.resDirectories.fromDisallowChanges(sourceProvider.resDirectories)
        this.assetsDirectories.fromDisallowChanges(sourceProvider.assetsDirectories)
        if (isForAnalysis) {
            this.javaDirectoriesClasspath.from(javaDirectories)
            this.resDirectoriesClasspath.from(sourceProvider.resDirectories)
            this.assetsDirectoriesClasspath.from(sourceProvider.assetsDirectories)
        } else {
            this.manifestFilePath.set(sourceProvider.manifestFile.absolutePath)
            this.javaDirectoryPaths.set(javaDirectories.map { it.absolutePath })
            this.resDirectoryPaths.set(sourceProvider.resDirectories.map { it.absolutePath })
            this.assetsDirectoryPaths.set(sourceProvider.assetsDirectories.map { it.absolutePath })
        }
        this.javaDirectoriesClasspath.disallowChanges()
        this.resDirectoriesClasspath.disallowChanges()
        this.assetsDirectoriesClasspath.disallowChanges()
        this.manifestFilePath.disallowChanges()
        this.javaDirectoryPaths.disallowChanges()
        this.resDirectoryPaths.disallowChanges()
        this.assetsDirectoryPaths.disallowChanges()
        this.debugOnly.setDisallowChanges(false) //TODO
        this.unitTestOnly.setDisallowChanges(unitTestOnly)
        this.instrumentationTestOnly.setDisallowChanges(instrumentationTestOnly)
        this.name.setDisallowChanges(sourceProvider.name)
        return this
    }

    internal fun initializeForStandalone(
        project: Project,
        sourceSet: SourceSet,
        isForAnalysis: Boolean,
        unitTestOnly: Boolean
    ): SourceProviderInput {
        val fakeManifestFile =
            project.layout.buildDirectory.file("fakeAndroidManifest/${sourceSet.name}/AndroidManifest.xml")
        this.manifestFile.setDisallowChanges(fakeManifestFile)
        this.javaDirectories.fromDisallowChanges(project.provider { sourceSet.allSource.srcDirs })
        this.resDirectories.disallowChanges()
        this.assetsDirectories.disallowChanges()
        if (isForAnalysis) {
            this.javaDirectoriesClasspath.from(project.provider { sourceSet.allSource.srcDirs })
        } else {
            this.javaDirectoryPaths.set(sourceSet.allSource.srcDirs.map { it.absolutePath })
        }
        this.javaDirectoriesClasspath.disallowChanges()
        this.resDirectoriesClasspath.disallowChanges()
        this.assetsDirectoriesClasspath.disallowChanges()
        this.manifestFilePath.disallowChanges()
        this.javaDirectoryPaths.disallowChanges()
        this.resDirectoryPaths.disallowChanges()
        this.assetsDirectoryPaths.disallowChanges()
        this.debugOnly.setDisallowChanges(false)
        this.unitTestOnly.setDisallowChanges(unitTestOnly)
        this.instrumentationTestOnly.setDisallowChanges(false)
        this.name.setDisallowChanges(sourceSet.name)
        return this
    }

    internal fun toLintModel(): LintModelSourceProvider {
        return DefaultLintModelSourceProvider(
            manifestFile = manifestFile.get().asFile,
            javaDirectories = javaDirectories.files.toList(),
            resDirectories = resDirectories.files.toList(),
            assetsDirectories = assetsDirectories.files.toList(),
            debugOnly = debugOnly.get(),
            unitTestOnly = unitTestOnly.get(),
            instrumentationTestOnly = instrumentationTestOnly.get(),
        )
    }
}

/**
 * Inputs for an SdkVersion. This is used by [VariantInputs] for min/target SDK Version
 */
abstract class SdkVersionInput {

    @get:Input
    abstract val apiLevel: Property<Int>

    @get:Input
    @get:Optional
    abstract val codeName: Property<String?>

    internal fun initialize(version: ApiVersion) {
        apiLevel.setDisallowChanges(version.apiLevel)
        codeName.setDisallowChanges(version.codename)
    }

    internal fun initialize(version: com.android.build.api.variant.AndroidVersion) {
        apiLevel.setDisallowChanges(version.apiLevel)
        codeName.setDisallowChanges(version.codename)
    }

    internal fun initializeEmpty() {
        apiLevel.setDisallowChanges(-1)
        codeName.setDisallowChanges("")
    }

    internal fun toLintModel(): AndroidVersion? {
        val api = apiLevel.get()
        if (api <= 0) {
            return null
        }
        return AndroidVersion(api, codeName.orNull)
    }
}

/**
 * Inputs for an Android Artifact. This is used by [VariantInputs] for the main and AndroidTest
 * artifacts.
 */
abstract class AndroidArtifactInput : ArtifactInput() {

    @get:Input
    abstract val applicationId: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val generatedSourceFolders: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val generatedResourceFolders: ConfigurableFileCollection

    @get:Input
    abstract val shrinkable: Property<Boolean>

    @get:Input
    abstract val useSupportLibraryVectorDrawables: Property<Boolean>

    fun initialize(
        componentImpl: ComponentImpl,
        checkDependencies: Boolean,
        addBaseModuleLintModel: Boolean,
        warnIfProjectTreatedAsExternalDependency: Boolean,
        includeClassesOutputDirectories: Boolean = true,
        includeGeneratedSourceFolders: Boolean = true
    ): AndroidArtifactInput {
        applicationId.setDisallowChanges(componentImpl.applicationId)
        if (includeGeneratedSourceFolders) {
            generatedSourceFolders.from(
                ModelBuilder.getGeneratedSourceFoldersFileCollection(componentImpl)
            )
        }
        generatedSourceFolders.disallowChanges()
        generatedResourceFolders.fromDisallowChanges(
            ModelBuilder.getGeneratedResourceFoldersFileCollection(componentImpl)
        )
        shrinkable.setDisallowChanges(
            componentImpl is ConsumableCreationConfig && componentImpl.minifiedEnabled
        )
        useSupportLibraryVectorDrawables.setDisallowChanges(
            componentImpl.variantDslInfo.vectorDrawables.useSupportLibrary ?: false
        )
        if (includeClassesOutputDirectories) {
            classesOutputDirectories.from(componentImpl.artifacts.get(InternalArtifactType.JAVAC))

            classesOutputDirectories.from(
                componentImpl.variantData.allPreJavacGeneratedBytecode
            )
            classesOutputDirectories.from(componentImpl.variantData.allPostJavacGeneratedBytecode)
            classesOutputDirectories.from(
                componentImpl
                    .getCompiledRClasses(
                        AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH
                    )
            )
        }
        classesOutputDirectories.disallowChanges()
        this.warnIfProjectTreatedAsExternalDependency.setDisallowChanges(warnIfProjectTreatedAsExternalDependency)
        initializeProjectDependencyLintArtifacts(
            checkDependencies,
            componentImpl.variantDependencies
        )
        if (!checkDependencies) {
            if (addBaseModuleLintModel) {
                initializeBaseModuleLintModel(componentImpl.variantDependencies)
            }
            projectRuntimeExplodedAars =
                componentImpl.variantDependencies.getArtifactCollectionForToolingModel(
                    AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.PROJECT,
                    AndroidArtifacts.ArtifactType.LOCAL_EXPLODED_AAR_FOR_LINT
                )
            projectCompileExplodedAars =
                componentImpl.variantDependencies.getArtifactCollectionForToolingModel(
                    AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.PROJECT,
                    AndroidArtifacts.ArtifactType.LOCAL_EXPLODED_AAR_FOR_LINT
                )
        }

        artifactCollectionsInputs.setDisallowChanges(
            ArtifactCollectionsInputsImpl(
                variantDependencies = componentImpl.variantDependencies,
                projectPath = componentImpl.services.projectInfo.path,
                variantName = componentImpl.name,
                runtimeType = ArtifactCollectionsInputs.RuntimeType.FULL,
                buildMapping = componentImpl.services.projectInfo.getProject().gradle.computeBuildMapping(),
            )
        )
        return this
    }

    fun initializeForStandalone(project: Project, projectOptions: ProjectOptions, sourceSet: SourceSet, checkDependencies: Boolean) {
        applicationId.setDisallowChanges("")
        generatedSourceFolders.disallowChanges()
        generatedResourceFolders.disallowChanges()
        classesOutputDirectories.fromDisallowChanges(sourceSet.output.classesDirs)
        warnIfProjectTreatedAsExternalDependency.setDisallowChanges(false)
        shrinkable.setDisallowChanges(false)
        useSupportLibraryVectorDrawables.setDisallowChanges(false)
        val variantDependencies = VariantDependencies(
            variantName = sourceSet.name,
            variantType = VariantTypeImpl.JAVA_LIBRARY,
            compileClasspath = project.configurations.getByName(sourceSet.compileClasspathConfigurationName),
            runtimeClasspath = project.configurations.getByName(sourceSet.runtimeClasspathConfigurationName),
            sourceSetRuntimeConfigurations = listOf(),
            sourceSetImplementationConfigurations = listOf(),
            elements = mapOf(),
            providedClasspath = project.configurations.getByName(sourceSet.compileOnlyConfigurationName),
            annotationProcessorConfiguration = project.configurations.getByName(sourceSet.annotationProcessorConfigurationName),
            reverseMetadataValuesConfiguration = null,
            wearAppConfiguration = null,
            testedVariant = null,
            project = project,
            projectOptions = projectOptions,
            isSelfInstrumenting = false,
        )
        artifactCollectionsInputs.setDisallowChanges(
            ArtifactCollectionsInputsImpl(
                variantDependencies = variantDependencies,
                projectPath = project.path,
                variantName = sourceSet.name,
                runtimeType = ArtifactCollectionsInputs.RuntimeType.FULL,
                buildMapping = project.gradle.computeBuildMapping(),
            )
        )
        initializeProjectDependencyLintArtifacts(checkDependencies, variantDependencies)
    }

    internal fun toLintModel(dependencyCaches: DependencyCaches): LintModelAndroidArtifact {
        return DefaultLintModelAndroidArtifact(
            applicationId.get(),
            generatedResourceFolders.toList(),
            generatedSourceFolders.toList(),
            classesOutputDirectories.files.toList(),
            computeDependencies(dependencyCaches)
        )
    }
}

/**
 * Inputs for a Java Artifact. This is used by [VariantInputs] for the unit test artifact.
 */
abstract class JavaArtifactInput : ArtifactInput() {

    fun initialize(
        unitTestImpl: UnitTestImpl,
        checkDependencies: Boolean,
        addBaseModuleLintModel: Boolean,
        warnIfProjectTreatedAsExternalDependency: Boolean,
        includeClassesOutputDirectories: Boolean
    ): JavaArtifactInput {
        if (includeClassesOutputDirectories) {
            classesOutputDirectories.from(
                unitTestImpl.artifacts.get(InternalArtifactType.JAVAC)
            )
            classesOutputDirectories.from(
                unitTestImpl.variantData.allPreJavacGeneratedBytecode
            )
            classesOutputDirectories.from(unitTestImpl.variantData.allPostJavacGeneratedBytecode)
            classesOutputDirectories.from(
                unitTestImpl
                    .getCompiledRClasses(
                        AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH
                    )
            )
        }
        classesOutputDirectories.disallowChanges()
        this.warnIfProjectTreatedAsExternalDependency.setDisallowChanges(warnIfProjectTreatedAsExternalDependency)
        initializeProjectDependencyLintArtifacts(
            checkDependencies,
            unitTestImpl.variantDependencies
        )
        if (!checkDependencies) {
            if (addBaseModuleLintModel) {
                initializeBaseModuleLintModel(unitTestImpl.variantDependencies)
            }
            projectRuntimeExplodedAars =
                unitTestImpl.variantDependencies.getArtifactCollectionForToolingModel(
                    AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.PROJECT,
                    AndroidArtifacts.ArtifactType.LOCAL_EXPLODED_AAR_FOR_LINT
                )
            projectCompileExplodedAars =
                unitTestImpl.variantDependencies.getArtifactCollectionForToolingModel(
                    AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.PROJECT,
                    AndroidArtifacts.ArtifactType.LOCAL_EXPLODED_AAR_FOR_LINT
                )
        }
        artifactCollectionsInputs.setDisallowChanges(
            ArtifactCollectionsInputsImpl(
                variantDependencies = unitTestImpl.variantDependencies,
                projectPath = unitTestImpl.services.projectInfo.path,
                variantName = unitTestImpl.name,
                runtimeType = ArtifactCollectionsInputs.RuntimeType.FULL,
                buildMapping = unitTestImpl.services.projectInfo.getProject().gradle.computeBuildMapping(),
            )
        )
        return this
    }

    fun initializeForStandalone(
        project: Project,
        projectOptions: ProjectOptions,
        sourceSet: SourceSet,
        checkDependencies: Boolean,
        includeClassesOutputDirectories: Boolean
    ): JavaArtifactInput {
        if (includeClassesOutputDirectories) {
            classesOutputDirectories.from(sourceSet.output.classesDirs)
        }
        classesOutputDirectories.disallowChanges()
        // Only ever used within the model builder in the standalone plugin
        warnIfProjectTreatedAsExternalDependency.setDisallowChanges(false)
        val variantDependencies = VariantDependencies(
            variantName = sourceSet.name,
            variantType = VariantTypeImpl.JAVA_LIBRARY,
            compileClasspath = project.configurations.getByName(sourceSet.compileClasspathConfigurationName),
            runtimeClasspath = project.configurations.getByName(sourceSet.runtimeClasspathConfigurationName),
            sourceSetRuntimeConfigurations = listOf(),
            sourceSetImplementationConfigurations = listOf(),
            elements = mapOf(),
            providedClasspath = project.configurations.getByName(sourceSet.compileOnlyConfigurationName),
            annotationProcessorConfiguration = project.configurations.getByName(sourceSet.annotationProcessorConfigurationName),
            reverseMetadataValuesConfiguration = null,
            wearAppConfiguration = null,
            testedVariant = null,
            project = project,
            projectOptions = projectOptions,
            isSelfInstrumenting = false,
        )
        artifactCollectionsInputs.setDisallowChanges(
            ArtifactCollectionsInputsImpl(
                variantDependencies = variantDependencies,
                projectPath = project.path,
                variantName = sourceSet.name,
                runtimeType = ArtifactCollectionsInputs.RuntimeType.FULL,
                buildMapping = project.gradle.computeBuildMapping(),
            )
        )
        initializeProjectDependencyLintArtifacts(checkDependencies, variantDependencies)
        return this
    }


    internal fun toLintModel(dependencyCaches: DependencyCaches): LintModelJavaArtifact {
        return DefaultLintModelJavaArtifact(
            classesOutputDirectories.files.toList(),
            computeDependencies(dependencyCaches)
        )
    }
}

/**
 * Base Inputs for Android/Java artifacts
 */
abstract class ArtifactInput {

    @get:Classpath
    abstract val classesOutputDirectories: ConfigurableFileCollection

    @get:Nested
    abstract val artifactCollectionsInputs: Property<ArtifactCollectionsInputs>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    val projectRuntimeExplodedAarsFileCollection: FileCollection?
        get() = projectRuntimeExplodedAars?.artifactFiles

    @get:Internal
    var projectRuntimeExplodedAars: ArtifactCollection? = null

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    val projectCompileExplodedAarsFileCollection: FileCollection?
        get() = projectCompileExplodedAars?.artifactFiles

    @get:Internal
    var projectCompileExplodedAars: ArtifactCollection? = null

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val projectRuntimeLintModelsFileCollection: ConfigurableFileCollection

    @get:Internal
    abstract val projectRuntimeLintModels: Property<ArtifactCollection>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val projectCompileLintModelsFileCollection: ConfigurableFileCollection

    @get:Internal
    abstract val projectCompileLintModels: Property<ArtifactCollection>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val baseModuleLintModelFileCollection: ConfigurableFileCollection

    @get:Internal
    abstract val baseModuleLintModel: Property<ArtifactCollection>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    @get:Optional
    abstract val runtimeLintModelMetadataFileCollection: ConfigurableFileCollection

    @get:Internal
    abstract val runtimeLintModelMetadata: Property<ArtifactCollection>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    @get:Optional
    abstract val compileLintModelMetadataFileCollection: ConfigurableFileCollection

    @get:Internal
    abstract val compileLintModelMetadata: Property<ArtifactCollection>

    @get:Internal
    abstract val warnIfProjectTreatedAsExternalDependency: Property<Boolean>

    protected fun initializeProjectDependencyLintArtifacts(
        checkDependencies: Boolean,
        variantDependencies: VariantDependencies
    ) {
        if (checkDependencies) {
            val runtimeArtifacts = variantDependencies.getArtifactCollectionForToolingModel(
                AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                AndroidArtifacts.ArtifactScope.PROJECT,
                AndroidArtifacts.ArtifactType.LINT_MODEL
            )
            projectRuntimeLintModels.setDisallowChanges(runtimeArtifacts)
            projectRuntimeLintModelsFileCollection.fromDisallowChanges(runtimeArtifacts.artifactFiles)
            val compileArtifacts = variantDependencies.getArtifactCollectionForToolingModel(
                AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                AndroidArtifacts.ArtifactScope.PROJECT,
                AndroidArtifacts.ArtifactType.LINT_MODEL
            )
            projectCompileLintModels.setDisallowChanges(compileArtifacts)
            projectCompileLintModelsFileCollection.fromDisallowChanges(compileArtifacts.artifactFiles)
        } else {
            val runtimeArtifacts = variantDependencies.getArtifactCollectionForToolingModel(
                AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                AndroidArtifacts.ArtifactScope.PROJECT,
                AndroidArtifacts.ArtifactType.LINT_MODEL_METADATA
            )
            runtimeLintModelMetadata.setDisallowChanges(runtimeArtifacts)
            runtimeLintModelMetadataFileCollection.fromDisallowChanges(runtimeArtifacts.artifactFiles)
            val compileArtifacts = variantDependencies.getArtifactCollectionForToolingModel(
                AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                AndroidArtifacts.ArtifactScope.PROJECT,
                AndroidArtifacts.ArtifactType.LINT_MODEL_METADATA
            )
            compileLintModelMetadata.setDisallowChanges(compileArtifacts)
            compileLintModelMetadataFileCollection.fromDisallowChanges(compileArtifacts.artifactFiles)
        }
    }

    protected fun initializeBaseModuleLintModel(variantDependencies: VariantDependencies) {
        val artifactCollection = variantDependencies.getArtifactCollectionForToolingModel(
            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
            AndroidArtifacts.ArtifactScope.PROJECT,
            AndroidArtifacts.ArtifactType.BASE_MODULE_LINT_MODEL
        )
        baseModuleLintModel.setDisallowChanges(artifactCollection)
        baseModuleLintModelFileCollection.fromDisallowChanges(artifactCollection.artifactFiles)
    }

    internal fun computeDependencies(dependencyCaches: DependencyCaches): LintModelDependencies {

        val artifactCollectionsInputs = artifactCollectionsInputs.get()

        val artifactHandler: ArtifactHandler<LintModelLibrary> =
            if (projectRuntimeLintModels.isPresent) {
                val thisProject =
                    ProjectKey(
                        artifactCollectionsInputs.buildMapping.currentBuild,
                        artifactCollectionsInputs.projectPath,
                        artifactCollectionsInputs.variantName
                    )
                CheckDependenciesLintModelArtifactHandler(
                    dependencyCaches,
                    thisProject,
                    projectRuntimeLintModels.get(),
                    projectCompileLintModels.get(),
                    artifactCollectionsInputs.compileClasspath.projectJars,
                    artifactCollectionsInputs.runtimeClasspath!!.projectJars,
                    artifactCollectionsInputs.buildMapping,
                    warnIfProjectTreatedAsExternalDependency.get())
            } else {
                // When not checking dependencies, treat all dependencies as external, with the
                // possible exception of the base module dependency. (When writing a dynamic feature
                // lint model for publication, we want to model the base module dependency as a
                // module dependency, not as an external dependency.)
                ExternalLintModelArtifactHandler.create(
                    dependencyCaches,
                    projectRuntimeExplodedAars,
                    projectCompileExplodedAars,
                    null,
                    artifactCollectionsInputs.compileClasspath.projectJars,
                    artifactCollectionsInputs.runtimeClasspath!!.projectJars,
                    baseModuleLintModel.orNull,
                    runtimeLintModelMetadata.get(),
                    compileLintModelMetadata.get(),
                    buildMapping = artifactCollectionsInputs.buildMapping
                )
            }
        val modelBuilder = LintDependencyModelBuilder(
            artifactHandler = artifactHandler,
            libraryMap = dependencyCaches.libraryMap,
            mavenCoordinatesCache = dependencyCaches.mavenCoordinatesCache
        )

        val graph = getDependencyGraphBuilder()
        val issueReporter = object : IssueReporter() {
            override fun reportIssue(type: Type, severity: Severity, exception: EvalIssueException) {
                if (severity == Severity.ERROR) {
                    throw exception
                }
            }

            override fun hasIssue(type: Type) = false
        }

        graph.createDependencies(
            modelBuilder = modelBuilder,
            artifactCollectionsProvider = artifactCollectionsInputs,
            withFullDependency = true,
            issueReporter = issueReporter
        )

        return modelBuilder.createModel()
    }
}

class LintFromMaven(val files: FileCollection, val version: String) {

    companion object {
        @JvmStatic
        fun from(
            project: Project,
            projectOptions: ProjectOptions,
            issueReporter: IssueReporter,
        ): LintFromMaven {
            val lintVersion =
                getLintMavenArtifactVersion(
                    projectOptions[StringOption.LINT_VERSION_OVERRIDE]?.trim(),
                    issueReporter
                )
            val config =  project.configurations.detachedConfiguration(
                project.dependencies.create(
                    mapOf(
                        "group" to "com.android.tools.lint",
                        "name" to "lint-gradle",
                        "version" to lintVersion,
                    )
                )
            )
            config.isTransitive = true
            config.isCanBeResolved = true
            return LintFromMaven(config, lintVersion)
        }
    }
}


/**
 * The lint binary uses the same version numbers as AGP (see LintCliClient#getClientRevision()
 * which is called when you run lint --version, as well as in release notes, etc etc).
 *
 * However, for historical reasons, the maven artifacts for its various libraries used in AGP are
 * using the older tools-base version numbers, which are 23 higher, so lint 7.0.0 is published
 * at com.android.tools.lint:lint-gradle:30.0.0
 *
 * This function maps from the user-oriented lint version specified by the user to the maven lint
 * library version number for the artifact to load.
 *
 * Returns the actual lint version to use, the given [versionOverride] if valid, otherwise the default,
 * reporting any issues as a side effect.
 */

internal fun getLintMavenArtifactVersion(
    versionOverride: String?,
    reporter: IssueReporter?,
    defaultVersion: String = Version.ANDROID_TOOLS_BASE_VERSION,
    agpVersion: String = Version.ANDROID_GRADLE_PLUGIN_VERSION
): String {
    if (versionOverride == null) {
        return defaultVersion
    }
    // Only verify versions that parse. If it is not valid, it will fail later anyway.
    val parsed = GradleVersion.tryParseAndroidGradlePluginVersion(versionOverride)
    if (parsed == null) {
        reporter?.reportError(
            IssueReporter.Type.GENERIC,
            """
                    Could not parse lint version override '$versionOverride'
                    Recommendation: Remove or update the gradle property ${StringOption.LINT_VERSION_OVERRIDE.propertyName} to be at least $agpVersion
                    """.trimIndent()
        )
        return defaultVersion
    }

    val default = GradleVersion.parseAndroidGradlePluginVersion(defaultVersion)

    // Heuristic when given an AGP version, find the corresponding lint version (that's 23 higher)
    val normalizedOverride: String = (parsed.major + 23).toString() + versionOverride.removePrefix(parsed.major.toString())
    val normalizedParsed = GradleVersion.tryParseAndroidGradlePluginVersion(normalizedOverride) ?: error("Unexpected parse error")

    // Only fail if the major version is outdated.
    // e.g. if the default lint version is 31.1.0 (as will be for AGP 8.1.0), fail is specifying
    // lint 30.2.0, but only warn if specifying lint 31.0.0
    if (normalizedParsed.major < default.major) {
        reporter?.reportError(
            IssueReporter.Type.GENERIC,
            """
                    Lint must be at least version ${agpVersion.substringBefore(".")}.0.0, and is recommended to be at least $agpVersion
                    Recommendation: Remove or update the gradle property ${StringOption.LINT_VERSION_OVERRIDE.propertyName} to be at least $agpVersion
                    """.trimIndent()
        )
        return defaultVersion
    }
    if (normalizedParsed < default) {
        reporter?.reportWarning(
            IssueReporter.Type.GENERIC,
            """
                    The build will use lint version $versionOverride which is older than the default.
                    Recommendation: Remove or update the gradle property ${StringOption.LINT_VERSION_OVERRIDE.propertyName} to be at least $agpVersion
                    """.trimIndent()
        )
    }
    return normalizedOverride
}
