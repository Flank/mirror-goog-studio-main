/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.lint.checks.infrastructure

import com.android.AndroidProjectTypes
import com.android.AndroidProjectTypes.PROJECT_TYPE_APP
import com.android.AndroidProjectTypes.PROJECT_TYPE_FEATURE
import com.android.AndroidProjectTypes.PROJECT_TYPE_INSTANTAPP
import com.android.AndroidProjectTypes.PROJECT_TYPE_LIBRARY
import com.android.SdkConstants
import com.android.build.FilterData
import com.android.builder.model.AndroidProject
import com.android.builder.model.LintOptions
import com.android.ide.common.gradle.model.IdeAaptOptions
import com.android.ide.common.gradle.model.IdeAndroidArtifactOutput
import com.android.ide.common.gradle.model.IdeAndroidLibrary
import com.android.ide.common.gradle.model.IdeApiVersion
import com.android.ide.common.gradle.model.IdeBuildType
import com.android.ide.common.gradle.model.IdeClassField
import com.android.ide.common.gradle.model.IdeDependencies
import com.android.ide.common.gradle.model.IdeJavaLibrary
import com.android.ide.common.gradle.model.IdeLibrary
import com.android.ide.common.gradle.model.IdeModuleLibrary
import com.android.ide.common.gradle.model.IdeProductFlavor
import com.android.ide.common.gradle.model.IdeSourceProvider
import com.android.ide.common.gradle.model.IdeVectorDrawablesOptions
import com.android.ide.common.gradle.model.impl.IdeAaptOptionsImpl
import com.android.ide.common.gradle.model.impl.IdeAndroidArtifactImpl
import com.android.ide.common.gradle.model.impl.IdeAndroidArtifactOutputImpl
import com.android.ide.common.gradle.model.impl.IdeAndroidGradlePluginProjectFlagsImpl
import com.android.ide.common.gradle.model.impl.IdeAndroidLibraryCore
import com.android.ide.common.gradle.model.impl.IdeAndroidLibraryImpl
import com.android.ide.common.gradle.model.impl.IdeAndroidProjectImpl
import com.android.ide.common.gradle.model.impl.IdeApiVersionImpl
import com.android.ide.common.gradle.model.impl.IdeBuildTypeContainerImpl
import com.android.ide.common.gradle.model.impl.IdeBuildTypeImpl
import com.android.ide.common.gradle.model.impl.IdeDependenciesImpl
import com.android.ide.common.gradle.model.impl.IdeJavaArtifactImpl
import com.android.ide.common.gradle.model.impl.IdeJavaCompileOptionsImpl
import com.android.ide.common.gradle.model.impl.IdeJavaLibraryCore
import com.android.ide.common.gradle.model.impl.IdeJavaLibraryImpl
import com.android.ide.common.gradle.model.impl.IdeLintOptionsImpl
import com.android.ide.common.gradle.model.impl.IdeModuleLibraryCore
import com.android.ide.common.gradle.model.impl.IdeModuleLibraryImpl
import com.android.ide.common.gradle.model.impl.IdeProductFlavorContainerImpl
import com.android.ide.common.gradle.model.impl.IdeProductFlavorImpl
import com.android.ide.common.gradle.model.impl.IdeSourceProviderContainerImpl
import com.android.ide.common.gradle.model.impl.IdeSourceProviderImpl
import com.android.ide.common.gradle.model.impl.IdeVariantImpl
import com.android.ide.common.gradle.model.impl.IdeVectorDrawablesOptionsImpl
import com.android.ide.common.gradle.model.impl.IdeViewBindingOptionsImpl
import com.android.ide.common.repository.GradleCoordinate
import com.android.ide.common.repository.GradleVersion
import com.android.projectmodel.ARTIFACT_NAME_ANDROID_TEST
import com.android.projectmodel.ARTIFACT_NAME_MAIN
import com.android.projectmodel.ARTIFACT_NAME_UNIT_TEST
import com.android.sdklib.AndroidTargetHash
import com.android.sdklib.SdkVersionInfo
import com.android.tools.lint.LintCliFlags
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.model.LintModelFactory
import com.android.tools.lint.model.LintModelModule
import com.android.tools.lint.model.LintModelVariant
import com.android.utils.ILogger
import com.android.utils.appendCamelCase
import com.android.utils.appendCapitalized
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Charsets
import com.google.common.base.Splitter
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.ImmutableList
import com.google.common.collect.Maps
import com.google.common.collect.Multimap
import com.google.common.io.ByteStreams
import junit.framework.TestCase
import org.intellij.lang.annotations.Language
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.PrintStream
import java.util.ArrayDeque
import java.util.Deque
import java.util.Locale
import java.util.jar.Attributes
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.regex.Pattern
import java.util.zip.ZipEntry

/**
 * A utility class which builds mocks for the Gradle builder-model API, by loosely interpreting
 * .gradle files and building models based on recognizing common patterns there.
 *
 *
 * TODO: Clean way to configure whether build dep cache is enabled TODO: Handle scopes (test
 * dependencies etc)
 */

class GradleModelMocker @JvmOverloads constructor(
    @field:Language("Groovy") @param:Language("Groovy") private val gradle: String,
    @VisibleForTesting val projectDir: File = File("")
) {

    private class DepConf {
        val androidLibraries: MutableSet<IdeAndroidLibrary> = mutableSetOf()
        val javaLibraries: MutableSet<IdeJavaLibrary> = mutableSetOf()
        val moduleLibraries: MutableSet<IdeModuleLibrary> = mutableSetOf()
    }

    private var project: IdeAndroidProjectImpl = createAndroidProject()
    private var defaultVariantName: String = ""

    private val variants: MutableList<IdeVariantImpl> = ArrayList()
    private val libraryLintJars: MutableMap<String, String> = HashMap()
    private val libraryPublicResourcesFiles: MutableMap<String, String> = HashMap()
    private val librarySymbolFiles: MutableMap<String, String> = HashMap()

    private val androidLibraryInstances: MutableMap<IdeAndroidLibrary, IdeAndroidLibrary> = HashMap()
    private val javaLibraryInstances: MutableMap<IdeJavaLibrary, IdeJavaLibrary> = HashMap()
    private val moduleLibraryInstances: MutableMap<IdeModuleLibrary, IdeModuleLibrary> = HashMap()

    private val main = DepConf()
    private val test = DepConf()
    private val androidTest = DepConf()

    private val severityOverrides = HashMap<String, Int>()
    private val flags = LintCliFlags()
    var primary = true

    private val splits: Multimap<String, String> = ArrayListMultimap.create()
    private var logger: ILogger? = null
    private var initialized = false
    private val ext: MutableMap<String, String> = HashMap()
    private val graphs: MutableMap<String, Dep> = Maps.newHashMap()
    private var allowUnrecognizedConstructs = false
    private var fullDependencies = false

    fun withLogger(logger: ILogger?): GradleModelMocker {
        ensureNotInitialized()
        this.logger = logger
        return this
    }

    fun withModelVersion(modelVersion: String): GradleModelMocker {
        updateModelVersion(modelVersion)
        return this
    }

    fun withDependencyGraph(graph: String): GradleModelMocker {
        ensureNotInitialized()
        parseDependencyGraph(graph, graphs)
        return this
    }

    fun withLintRuleJar(lintRuleJarPath: String): GradleModelMocker {
        updateProject {
            it.copy(lintRuleJars = it.lintRuleJars.orEmpty() + File(lintRuleJarPath))
        }
        return this
    }

    fun withLibraryLintJar(
        library: String,
        lintJarPath: String
    ): GradleModelMocker {
        ensureNotInitialized()
        libraryLintJars[library] = lintJarPath
        return this
    }

    fun withLibraryPublicResourcesFile(
        library: String,
        publicResourcesPath: String
    ): GradleModelMocker {
        ensureNotInitialized()
        libraryPublicResourcesFiles[library] = publicResourcesPath
        return this
    }

    fun withLibrarySymbolFile(
        library: String,
        symbolFilePath: String
    ): GradleModelMocker {
        ensureNotInitialized()
        librarySymbolFiles[library] = symbolFilePath
        return this
    }

    fun allowUnrecognizedConstructs(): GradleModelMocker {
        allowUnrecognizedConstructs = true
        return this
    }

    /**
     * If true, model a full/deep dependency graph in [ ]; the default is flat. (This is normally
     * controlled by sync/model builder flag [ ][AndroidProject.PROPERTY_BUILD_MODEL_FEATURE_FULL_DEPENDENCIES].)
     */
    fun withFullDependencies(fullDependencies: Boolean): GradleModelMocker {
        ensureNotInitialized()
        this.fullDependencies = fullDependencies
        return this
    }

    private fun warn(message: String) {
        if (!allowUnrecognizedConstructs) {
            error(message)
            return
        }
        if (logger != null) {
            logger!!.warning(message)
        } else {
            System.err.println(message)
        }
    }

    private fun error(message: String?) {
        if (logger != null) {
            logger!!.error(null, message)
        } else {
            System.err.println(message)
        }
    }

    private fun ensureInitialized() {
        if (!initialized) {
            initialized = true
            initialize()
        }
    }

    private fun ensureNotInitialized() {
        if (initialized) error("GradleModelMocker has been already initialized.")
    }

    private var _hasJavaOrJavaLibraryPlugin: Boolean = false
    private var _isLibrary: Boolean = false

    /** Whether the Gradle file applied the java or java-library plugin  */
    fun hasJavaOrJavaLibraryPlugin(): Boolean {
        ensureInitialized()
        return _hasJavaOrJavaLibraryPlugin
    }

    val isLibrary: Boolean
        get() {
            ensureInitialized()
            return _isLibrary
        }

    val buildTargetHash: String?
        get() {
            ensureInitialized()
            return project
                .compileTarget
                .takeUnless { it.isEmpty() }
        }

    val buildSdk: Int?
        get() {
            ensureInitialized()
            return buildTargetHash
                ?.let { AndroidTargetHash.getPlatformVersion(it) }
                ?.apiLevel
        }

    val generatedSourceFolders: Collection<File>
        get() {
            ensureInitialized()
            return variant.mainArtifact.generatedSourceFolders
        }

    private val buildTypes get() = project.buildTypes.map { it.buildType as IdeBuildTypeImpl }
    private val productFlavors get() = project.productFlavors.map { it.productFlavor as IdeProductFlavorImpl }
    private val flavorDimensions get() = project.flavorDimensions

    private val variant
        get() = variants.single { it.name == defaultVariantName }

    fun syncFlagsTo(to: LintCliFlags) {
        ensureInitialized()
        to.suppressedIds.clear()
        to.suppressedIds.addAll(flags.suppressedIds)
        to.enabledIds.clear()
        to.enabledIds.addAll(flags.enabledIds)
        to.exactCheckedIds = flags.exactCheckedIds
        to.isSetExitCode = flags.isSetExitCode
        to.isFullPath = flags.isFullPath
        to.isShowSourceLines = flags.isShowSourceLines
        to.isQuiet = flags.isQuiet
        to.isCheckAllWarnings = flags.isCheckAllWarnings
        to.isIgnoreWarnings = flags.isIgnoreWarnings
        to.isWarningsAsErrors = flags.isWarningsAsErrors
        to.isCheckTestSources = flags.isCheckTestSources
        to.isCheckDependencies = flags.isCheckDependencies
        to.isCheckGeneratedSources = flags.isCheckGeneratedSources
        to.isShowEverything = flags.isShowEverything
        to.lintConfig = flags.lintConfig
        to.isExplainIssues = flags.isExplainIssues
        to.baselineFile = flags.baselineFile
    }

    fun getLintVariant(): LintModelVariant? {
        ensureInitialized()
        return getLintModule().findVariant(defaultVariantName)
    }

    fun getLintModule(): LintModelModule {
        ensureInitialized()
        return LintModelFactory().create(project, variants, projectDir, true)
    }

    private fun initialize() {
        // built-in build-types
        updateBuildType("debug", true) { it }
        updateBuildType("release", true) { it }

        addLocalLibs(File(projectDir, "libs"))

        scan(gradle, "")

        // Artifacts
        updateDefaultConfig { it.copy(applicationId = it.applicationId ?: "test.pkg") }

        updateProject { it.copy(buildFolder = File(projectDir, "build")) }

        val outputs: MutableList<IdeAndroidArtifactOutput> = mutableListOf()
        outputs.add(createAndroidArtifactOutput("", ""))
        for ((key, value) in splits.entries()) {
            outputs.add(createAndroidArtifactOutput(key, value))
        }
        // outputs.add(createAndroidArtifactOutput("DENSITY", "mdpi"));
        // outputs.add(createAndroidArtifactOutput("DENSITY", "hdpi"));

        val dependencies = createDependencies(main)
        val androidTestDependencies = createDependencies(androidTest)
        val testDependencies = createDependencies(test)

        val variantCoordinates = generateVariants()
        defaultVariantName = variantCoordinates.first().let { buildVariantName(it.second, it.first) }
        variantCoordinates.forEach { (buildType, productFlavors) ->
            val variantName = buildVariantName(productFlavors, buildType)

            val generated = File(projectDir, "generated")
            val mergedFlavorsAndBuildType = merge(defaultConfig, productFlavors, buildType)
            variants.add(
                IdeVariantImpl(
                    name = variantName,
                    displayName = "",
                    mainArtifact = createAndroidArtifact(ARTIFACT_NAME_MAIN)
                        .copy(
                            name = variantName,
                            applicationId = mergedFlavorsAndBuildType.applicationId!!,
                            outputs = outputs,
                            level2Dependencies = dependencies,
                            classesFolder = File(projectDir, "build/intermediates/javac/$variantName/classes"),
                            additionalClassesFolders = setOf(File(projectDir, "build/tmp/kotlin-classes/$variantName")),
                            mutableGeneratedSourceFolders =
                                listOfNotNull(File(generated, "java").takeIf { it.exists() }).toMutableList(),
                            generatedResourceFolders =
                                listOfNotNull(File(generated, "res").takeIf { it.exists() }),
                            multiFlavorSourceProvider = let {
                                if (productFlavors.size >= 2)
                                    createSourceProvider(projectDir, buildVariantName(productFlavors))
                                else null
                            },
                            variantSourceProvider = let {
                                if (productFlavors.isNotEmpty())
                                    createSourceProvider(projectDir, variantName)
                                else null
                            },
                        ),
                    unitTestArtifact = createJavaArtifact(ARTIFACT_NAME_UNIT_TEST)
                        .copy(
                            level2Dependencies = testDependencies,
                            classesFolder = File(projectDir, "test-classes"),
                        ),
                    androidTestArtifact = createAndroidArtifact(ARTIFACT_NAME_ANDROID_TEST)
                        .copy(
                            applicationId = mergedFlavorsAndBuildType.applicationId!!,
                            level2Dependencies = androidTestDependencies,
                            classesFolder = File(projectDir, "instrumentation-classes"),
                        ),
                    buildType = buildType.name,
                    productFlavors = productFlavors.map { it.name },
                    minSdkVersion = mergedFlavorsAndBuildType.minSdkVersion,
                    targetSdkVersion = mergedFlavorsAndBuildType.targetSdkVersion,
                    maxSdkVersion = mergedFlavorsAndBuildType.maxSdkVersion,
                    instantAppCompatible = false,
                    vectorDrawablesUseSupportLibrary = mergedFlavorsAndBuildType.vectorDrawables?.useSupportLibrary ?: false,
                    resourceConfigurations = mergedFlavorsAndBuildType.resourceConfigurations,
                    resValues = mergedFlavorsAndBuildType.resValues,
                    proguardFiles = mergedFlavorsAndBuildType.proguardFiles,
                    consumerProguardFiles = mergedFlavorsAndBuildType.consumerProguardFiles,
                    manifestPlaceholders = mergedFlavorsAndBuildType.manifestPlaceholders,
                    testApplicationId = mergedFlavorsAndBuildType.testApplicationId,
                    testInstrumentationRunner = mergedFlavorsAndBuildType.testInstrumentationRunner,
                    testInstrumentationRunnerArguments = mergedFlavorsAndBuildType.testInstrumentationRunnerArguments,
                    testedTargetVariants = emptyList(),
                    versionCode = mergedFlavorsAndBuildType.versionCode,
                    versionNameWithSuffix =
                        mergedFlavorsAndBuildType.versionName?.let { it + mergedFlavorsAndBuildType.versionNameSuffix.orEmpty() },
                    versionNameSuffix = mergedFlavorsAndBuildType.versionNameSuffix,
                    deprecatedPreMergedApplicationId = mergedFlavorsAndBuildType.applicationId
                )
            )
        }
    }

    private fun generateVariants(): List<Pair<IdeBuildTypeImpl, List<IdeProductFlavorImpl>>> {
        val dimensions = flavorDimensions.takeUnless { it.isEmpty() }?.toList()
            ?: if (productFlavors.isNotEmpty()) listOf(null) else emptyList()
        val dimensionFlavors = dimensions.map { dimensionNameOrNull ->
            productFlavors.filter { it.dimension == dimensionNameOrNull }
        }
        return dimensionFlavors
            .fold<List<IdeProductFlavorImpl>, Sequence<Pair<IdeBuildTypeImpl, List<IdeProductFlavorImpl>>>>(
                buildTypes.asSequence().map { it to emptyList() }
            ) { acc, dimension ->
                acc.flatMap { prefix -> dimension.asSequence().map { prefix.first to prefix.second + it } }
            }
            .toList()
    }

    private fun buildVariantName(
        productFlavors: List<IdeProductFlavorImpl>,
        buildType: IdeBuildTypeImpl? = null
    ): String {
        return buildString {
            productFlavors.forEach { appendCamelCase(it.name) }
            if (buildType != null) {
                appendCamelCase(buildType.name)
            }
        }
    }

    private fun merge(
        defaultConfig: IdeProductFlavorImpl,
        productFlavors: List<IdeProductFlavorImpl>,
        buildType: IdeBuildTypeImpl
    ): IdeProductFlavorImpl {

        fun <T> combineValues(
            combine: (T?, T) -> T,
            f: IdeProductFlavorImpl.() -> T,
            b: (IdeBuildTypeImpl.() -> T)? = null,
            reverseFlavors: Boolean = true
        ): T {
            return combine(
                productFlavors
                    .let {
                        when (reverseFlavors) {
                            true -> it // combineFunctions are designed to handle this by default.
                            false -> it.reversed() // special case for suffix like properties.
                        }
                    }
                    .map { it.f() } // second
                    .fold(
                        if (b != null) buildType.b() else null, // first
                        combine
                    ),
                defaultConfig.f() // third
            )
        }

        fun <T> combineNullables(u: T?, v: T) = u ?: v
        fun combineSuffixes(u: String?, v: String?) = if (u != null || v != null) u.orEmpty() + v.orEmpty() else null
        fun <T> combineSets(u: Collection<T>?, v: Collection<T>) = u.orEmpty().toSet() + v
        fun <T> combineMaps(u: Map<String, T>?, v: Map<String, T>) = v + (u ?: emptyMap())

        return IdeProductFlavorImpl(
            dimension = null,
            name = buildVariantName(productFlavors, buildType),
            applicationIdSuffix = combineValues(::combineSuffixes, { applicationIdSuffix }, { applicationIdSuffix }, reverseFlavors = false),
            versionNameSuffix = combineValues(::combineSuffixes, { versionNameSuffix }, { versionNameSuffix }, reverseFlavors = false),
            resValues = combineValues(::combineMaps, { resValues }, { resValues }),
            proguardFiles = combineValues(::combineSets, { proguardFiles }, { proguardFiles }),
            consumerProguardFiles = combineValues(::combineSets, { consumerProguardFiles }, { consumerProguardFiles }),
            manifestPlaceholders = combineValues(::combineMaps, { manifestPlaceholders }, { manifestPlaceholders }),
            multiDexEnabled = combineValues(::combineNullables, { multiDexEnabled }, { multiDexEnabled }),
            applicationId = combineValues(::combineNullables, { applicationId }, { null }),
            versionCode = combineValues(::combineNullables, { versionCode }),
            versionName = combineValues(::combineNullables, { versionName }),
            minSdkVersion = combineValues(::combineNullables, { minSdkVersion }),
            targetSdkVersion = combineValues(::combineNullables, { targetSdkVersion }),
            maxSdkVersion = combineValues(::combineNullables, { maxSdkVersion }),
            testApplicationId = combineValues(::combineNullables, { testApplicationId }),
            testInstrumentationRunner = combineValues(::combineNullables, { testInstrumentationRunner }),
            testInstrumentationRunnerArguments = combineValues(::combineMaps, { testInstrumentationRunnerArguments }),
            testHandleProfiling = combineValues(::combineNullables, { testHandleProfiling }),
            testFunctionalTest = combineValues(::combineNullables, { testFunctionalTest }),
            resourceConfigurations = combineValues(::combineSets, { resourceConfigurations }),
            vectorDrawables = combineValues(::combineNullables, { vectorDrawables }),
        )
    }

    private fun createAndroidProject() = IdeAndroidProjectImpl(
        modelVersion = "2.2.2",
        name = "test_project",
        projectType = 0,
        defaultConfig =
            IdeProductFlavorContainerImpl(
                productFlavor = createProductFlavor("defaultConfig"),
                sourceProvider = createSourceProvider(projectDir, "main"),
                extraSourceProviders = listOf(
                    IdeSourceProviderContainerImpl(
                        AndroidProject.ARTIFACT_ANDROID_TEST,
                        createSourceProvider(projectDir, "androidTest")
                    ),
                    IdeSourceProviderContainerImpl(
                        AndroidProject.ARTIFACT_UNIT_TEST,
                        createSourceProvider(projectDir, "test")
                    )
                )
            ),
        buildTypes = emptyList(),
        productFlavors = emptyList(),
        variantNames = emptyList(),
        flavorDimensions = emptyList(),
        compileTarget = "android-" + SdkVersionInfo.HIGHEST_KNOWN_API,
        bootClasspath = emptyList(),
        signingConfigs = emptyList(),
        aaptOptions = IdeAaptOptionsImpl(namespacing = IdeAaptOptions.Namespacing.DISABLED),
        lintOptions = IdeLintOptionsImpl(),
        javaCompileOptions = IdeJavaCompileOptionsImpl(
            encoding = "UTF-8",
            sourceCompatibility = "1.7",
            targetCompatibility = "1.7",
            isCoreLibraryDesugaringEnabled = false
        ),
        buildFolder = File(""),
        resourcePrefix = null,
        buildToolsVersion = null,
        ndkVersion = null,
        isBaseSplit = false,
        dynamicFeatures = emptyList(),
        viewBindingOptions = null,
        dependenciesInfo = null,
        groupId = null,
        agpFlags = IdeAndroidGradlePluginProjectFlagsImpl(),
        variantsBuildInformation = emptyList(),
        lintRuleJars = emptyList(),
    )

    private fun createAndroidArtifact(artifact: String) = IdeAndroidArtifactImpl(
        name = artifact,
        compileTaskName = "",
        assembleTaskName = "",
        assembleTaskOutputListingFile = "",
        classesFolder = File(""),
        additionalClassesFolders = emptyList(),
        javaResourcesFolder = null,
        variantSourceProvider = null,
        multiFlavorSourceProvider = null,
        ideSetupTaskNames = emptyList(),
        mutableGeneratedSourceFolders = mutableListOf(),
        isTestArtifact = false,
        level2Dependencies = createDependencies(),
        applicationId = "",
        signingConfigName = null,
        outputs = emptyList(),
        isSigned = false,
        generatedResourceFolders = emptyList(),
        additionalRuntimeApks = emptyList(),
        testOptions = null,
        abiFilters = emptySet(),
        bundleTaskName = null,
        bundleTaskOutputListingFile = null,
        apkFromBundleTaskName = null,
        apkFromBundleTaskOutputListingFile = null,
        codeShrinker = null
    )

    private fun createJavaArtifact(artifact: String) = IdeJavaArtifactImpl(
        name = artifact,
        compileTaskName = "",
        assembleTaskName = "",
        assembleTaskOutputListingFile = "",
        classesFolder = File(""),
        additionalClassesFolders = emptyList(),
        javaResourcesFolder = null,
        variantSourceProvider = null,
        multiFlavorSourceProvider = null,
        ideSetupTaskNames = emptyList(),
        mutableGeneratedSourceFolders = mutableListOf(),
        isTestArtifact = false,
        level2Dependencies = createDependencies(),
        mockablePlatformJar = null,
    )

    private fun createDependencies(dep: DepConf? = null) =
        if (dep != null)
            IdeDependenciesImpl(
                androidLibraries = dep.androidLibraries,
                javaLibraries = dep.javaLibraries,
                moduleDependencies = dep.moduleLibraries,
                emptyList()
            )
        else IdeDependenciesImpl(emptyList(), emptyList(), emptyList(), emptyList())

    private fun addLocalLibs(libsDir: File) {
        val libs = libsDir.listFiles()
        if (libs != null) {
            for (lib in libs) {
                if (lib.isDirectory) {
                    addLocalLibs(lib)
                } else {
                    val path = lib.path
                    if (path.endsWith(SdkConstants.DOT_JAR)) {
                        val name = lib.name.removeSuffix(SdkConstants.DOT_JAR)
                        var coordinateString = "locallibs:" + name + ":" + libraryVersion++

                        // See if this might be an Android library instead of a Java library
                        var index = path.indexOf("exploded-aar")
                        if (index != -1) {
                            val jars = path.indexOf("jars")
                            if (jars != -1) {
                                coordinateString = path.substring(index + 13, jars - 1)
                                    .replace("/", ":")
                                    .replace("\\", ":")
                                val library = createAndroidLibrary(coordinateString, null, false, lib)
                                main.androidLibraries.add(library)
                                return
                            }
                        }
                        index = path.indexOf(".aar/")
                        if (index == -1) {
                            index = path.indexOf(".aar\\")
                        }
                        if (index != -1) {
                            val library = createAndroidLibrary(coordinateString, null, false, lib)
                            main.androidLibraries.add(library)
                            return
                        }
                        val library = createJavaLibrary(coordinateString, null, false, lib)
                        main.javaLibraries.add(library)
                    }
                }
            }
        }
    }

    /*

    @NonNull
    private DependencyGraphs createDependencyGraphs() {
        DependencyGraphs graphs = mock(DependencyGraphs.class);
        List<GraphItem> compileItems = mutableListOf();
        Map<String, com.android.builder.model.level2.Library> globalMap = Maps.newHashMap();

        when(graphs.getCompileDependencies()).thenReturn(compileItems);
        when(graphs.getPackageDependencies()).thenReturn(compileItems);
        when(graphs.getProvidedLibraries()).thenReturn(Collections.emptyList());
        when(graphs.getSkippedLibraries()).thenReturn(Collections.emptyList());

        HashSet<String> seen = Sets.newHashSet();
        addGraphItems(compileItems, globalMap, seen, androidLibraries);
        addGraphItems(compileItems, globalMap, seen, javaLibraries);

        // Java libraries aren't available from the AndroidLibraries themselves;
        // stored in a separate global map during initialization
        for (JavaLibrary library : allJavaLibraries) {
            com.android.builder.model.level2.Library lib = createLevel2Library(library);
            globalMap.put(lib.getArtifactAddress(), lib);
        }

        globalLibraryMap = mock(GlobalLibraryMap.class);
        when(globalLibraryMap.getLibraries()).thenReturn(globalMap);

        return graphs;
    }
    */
/*
    private fun addGraphItems(
        result: MutableList<GraphItem>,
        globalMap: MutableMap<String, Library>,
        seen: MutableSet<String>,
        libraries: Collection<com.android.builder.model.Library>
    ) {
        for (library in libraries) {
            val coordinates = library.resolvedCoordinates
            val name = (
                coordinates.groupId +
                    ':' +
                    coordinates.artifactId +
                    ':' +
                    coordinates.version +
                    '@' +
                    coordinates.packaging
                )
            if (fullDependencies || !seen.contains(name)) {
                seen.add(name)
                val item = Mockito.mock(
                    GraphItem::class.java
                )
                result.add(item)
                Mockito.`when`(item.artifactAddress).thenReturn(name)
                Mockito.`when`(item.requestedCoordinates).thenReturn(name)
                Mockito.`when`(item.dependencies).thenReturn(mutableListOf())
                if (library is AndroidLibrary) {
                    addGraphItems(
                        if (fullDependencies) item.dependencies else result,
                        globalMap,
                        seen,
                        library.libraryDependencies
                    )
                } else if (library is JavaLibrary) {
                    addGraphItems(
                        if (fullDependencies) item.dependencies else result,
                        globalMap,
                        seen,
                        library.dependencies
                    )
                }
            }
            globalMap[name] = createLevel2Library(library)
        }
    }
*/

    /*
        private fun createLevel2Library(library: com.android.builder.model.Library): Library {
            val lib = Mockito.mock(
                Library::class.java
            )
            val coordinates = library.resolvedCoordinates
            val name = (
                coordinates.groupId +
                    ':' +
                    coordinates.artifactId +
                    ':' +
                    coordinates.version +
                    '@' +
                    coordinates.packaging
                )
            Mockito.`when`(lib.artifactAddress).thenReturn(name)
            if (library is AndroidLibrary) {
                val folder = library.folder
                Mockito.`when`(lib.type)
                    .thenReturn(Library.LIBRARY_ANDROID)
                Mockito.`when`(lib.folder).thenReturn(folder)
                Mockito.`when`(lib.lintJar).thenReturn("lint.jar")
                Mockito.`when`(lib.localJars).thenReturn(emptyList())
                Mockito.`when`(lib.externalAnnotations).thenReturn(SdkConstants.FN_ANNOTATIONS_ZIP)
                Mockito.`when`(lib.jarFile).thenReturn("jars/" + SdkConstants.FN_CLASSES_JAR)
                val jar = File(folder, "jars/" + SdkConstants.FN_CLASSES_JAR)
                if (!jar.exists()) {
                    createEmptyJar(jar)
                }
                // when(l2.isProvided).thenReturn(androidLibrary.isProvided());
            } else if (library is JavaLibrary) {
                Mockito.`when`(lib.type).thenReturn(Library.LIBRARY_JAVA)
                val jars: List<String> = mutableListOf()
                Mockito.`when`(lib.localJars).thenReturn(jars)
                val jarFile = library.jarFile
                Mockito.`when`(lib.artifact).thenReturn(jarFile)
                Mockito.`when`(lib.folder).thenThrow(UnsupportedOperationException())
            }
            return lib
        }

    */
    private fun createEmptyJar(jar: File) {
        if (!jar.exists()) {
            val parentFile = jar.parentFile
            if (parentFile != null && !parentFile.isDirectory) {
                parentFile.mkdirs()
            }
            val manifest = Manifest()
            manifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            try {
                JarOutputStream(
                    BufferedOutputStream(FileOutputStream(jar)), manifest
                ).use { jarOutputStream ->
                    jarOutputStream.putNextEntry(ZipEntry("sample.txt"))
                    ByteStreams.copy(
                        ByteArrayInputStream("Sample".toByteArray(Charsets.UTF_8)),
                        jarOutputStream
                    )
                    jarOutputStream.closeEntry()
                }
            } catch (e: IOException) {
                error(e.message)
            }
        }
    }

    private fun scan(@Language("Groovy") gradle: String, context: String) {
        var start = 0
        val end = gradle.length
        while (start < end) {
            // Iterate line by line, but as soon as a line has an imbalance of {}'s
            // then report the block instead
            var lineEnd = gradle.indexOf('\n', start)

            // Join comma statements
            while (true) {
                if (findNonSpaceCharacterBackwards(gradle, lineEnd) == ',') {
                    lineEnd = gradle.indexOf('\n', lineEnd + 1)
                } else {
                    if (lineEnd == -1) {
                        lineEnd = end
                    }
                    break
                }
            }
            var balance = 0
            for (i in start until lineEnd) {
                val c = gradle[i]
                if (c == '{') {
                    balance++
                } else if (c == '}') {
                    balance--
                }
            }
            if (balance == 0) {
                val line = gradle.substring(start, lineEnd).trim { it <= ' ' }
                val index = line.indexOf('{')
                if (line.endsWith("}") && index != -1) {
                    // Single line block?
                    val name = line.substring(0, index).trim { it <= ' ' }
                    @Language("Groovy") val blockBody = line.substring(index + 1, line.length - 1)
                    block(name, blockBody, context)
                } else {
                    line(line, context)
                }
                start = lineEnd + 1
            } else {
                // Find end of block
                val nameEnd = gradle.indexOf('{', start)
                val name = gradle.substring(start, nameEnd).trim { it <= ' ' }
                start = lineEnd + 1
                for (i in lineEnd until end) {
                    val c = gradle[i]
                    if (c == '{') {
                        balance++
                    } else if (c == '}') {
                        balance--
                        if (balance == 0) {
                            // Found the end
                            @Language("Groovy") val block = gradle.substring(nameEnd + 1, i)
                            block(name, block, context)
                            start = i + 1
                            break
                        }
                    }
                }
            }
        }
    }

    private fun getUnquotedValue(key: String): String {
        var value = key
        var index = key.indexOf('\'')
        if (index != -1) {
            value = key.substring(index + 1, key.indexOf('\'', index + 1))
        } else if (key.indexOf('"').also { index = it } != -1) {
            value = key.substring(index + 1, key.indexOf('"', index + 1))
        } else if (key.indexOf('=').also { index = it } != -1) {
            value = key.substring(index + 1)
        } else if (key.indexOf(' ').also { index = it } != -1) {
            value = key.substring(index + 1)
        }
        return if (value.indexOf('$') == -1) value else doInterpolations(value)
    }

    private fun doInterpolations(value: String): String {
        val sb = StringBuilder()
        var lastIndex = 0
        var index: Int
        while (value.indexOf('$', lastIndex).also { index = it } != -1) {
            sb.append(value, lastIndex, index)
            var end = value.indexOf(' ', index)
            if (end == -1) end = value.length
            val name = value.substring(index + 1, end)
            if (ext.containsKey(name)) {
                sb.append(ext[name])
            } else {
                sb.append("$$name")
            }
            lastIndex = end
        }
        sb.append(value, lastIndex, value.length)
        return sb.toString()
    }

    private fun line(line: String, context: String) {
        var line = line
        line = normalize(line)
        if (line.isEmpty()) {
            return
        }

        fun updateProjectType(type: Int, hasJavaOrJavaLibraryPlugin: Boolean, isLibrary: Boolean): Boolean {
            _hasJavaOrJavaLibraryPlugin = hasJavaOrJavaLibraryPlugin
            _isLibrary = isLibrary
            updateProject { it.copy(projectType = type) }
            return true
        }

        if (when (line) {
            "apply plugin: 'com.android.library'", "apply plugin: 'android-library'" ->
                updateProjectType(PROJECT_TYPE_LIBRARY, hasJavaOrJavaLibraryPlugin = false, isLibrary = true)
            "apply plugin: 'com.android.application'", "apply plugin: 'android'" ->
                updateProjectType(PROJECT_TYPE_APP, hasJavaOrJavaLibraryPlugin = false, isLibrary = false)
            "apply plugin: 'com.android.feature'" ->
                updateProjectType(PROJECT_TYPE_FEATURE, hasJavaOrJavaLibraryPlugin = false, isLibrary = false)
            "apply plugin: 'com.android.instantapp'" ->
                updateProjectType(PROJECT_TYPE_INSTANTAPP, hasJavaOrJavaLibraryPlugin = false, isLibrary = false)
            "apply plugin: 'java'" ->
                updateProjectType(PROJECT_TYPE_JAVA, hasJavaOrJavaLibraryPlugin = true, isLibrary = false)
            "apply plugin: 'java-library'" ->
                updateProjectType(PROJECT_TYPE_JAVA_LIBRARY, hasJavaOrJavaLibraryPlugin = true, isLibrary = true)
            else -> when {
                context == "buildscript.repositories" || context == "allprojects.repositories" -> {
                    // Plugins not modeled in the builder model
                    true
                }
                line.startsWith("apply plugin: ") -> {
                    // Some other plugin not relevant to the builder-model
                    true
                }
                else -> false
            }
        }
        ) return

        var key = if (context.isEmpty()) line else "$context.$line"
        val m = configurationPattern.matcher(key)
        when {
            key.startsWith("ext.") -> {
                val name = key.substring(4, key.indexOf(' '))
                ext[name] = getUnquotedValue(key)
            }
            m.matches() -> {
                val artifactName = m.group(1)
                var declaration = getUnquotedValue(key)
                if (GradleCoordinate.parseCoordinateString(declaration) != null) {
                    addDependency(declaration, artifactName, false)
                    return
                } else {
                    // Group/artifact/version syntax?
                    if (line.contains("group:") &&
                        line.contains("name:") &&
                        line.contains("version:")
                    ) {
                        var group: String? = null
                        var artifact: String? = null
                        var version: String? = null
                        for (
                            part in Splitter.on(',')
                                .trimResults()
                                .omitEmptyStrings()
                                .split(line.substring(line.indexOf(' ') + 1))
                        ) {
                            if (part.startsWith("group:")) {
                                group = getUnquotedValue(part)
                            } else if (part.startsWith("name:")) {
                                artifact = getUnquotedValue(part)
                            } else if (part.startsWith("version:")) {
                                version = getUnquotedValue(part)
                            }
                        }
                        if (group != null && artifact != null && version != null) {
                            declaration = "$group:$artifact:$version"
                            addDependency(declaration, artifactName, false)
                            return
                        }
                    }
                }
                warn("Ignored unrecognized dependency $line")
            }
            key.startsWith("dependencies.provided '") && key.endsWith("'") -> {
                addDependency(getUnquotedValue(key), null, true)
            }
            line.startsWith("applicationId ") || line.startsWith("packageName ") -> {
                updateFlavorFromContext(context) { it.copy(applicationId = getUnquotedValue(key)) }
            }
            line.startsWith("minSdkVersion ") -> {
                updateFlavorFromContext(context) { it.copy(minSdkVersion = createApiVersion(key)) }
            }
            line.startsWith("targetSdkVersion ") -> {
                updateFlavorFromContext(context) { it.copy(targetSdkVersion = createApiVersion(key)) }
            }
            line.startsWith("versionCode ") -> {
                val value = key.substring(key.indexOf(' ') + 1).trim { it <= ' ' }
                if (Character.isDigit(value[0])) {
                    val number = Integer.decode(value)
                    updateFlavorFromContext(context) { it.copy(versionCode = number) }
                } else {
                    warn("Ignoring unrecognized versionCode token: $value")
                }
            }
            line.startsWith("versionName ") -> {
                updateFlavorFromContext(context) { it.copy(versionName = getUnquotedValue(key)) }
            }
            line.startsWith("versionNameSuffix ") -> {
                updateFlavorFromContext(context) { it.copy(versionNameSuffix = getUnquotedValue(key)) }
            }
            line.startsWith("applicationIdSuffix ") -> {
                updateFlavorFromContext(context) { it.copy(applicationIdSuffix = getUnquotedValue(key)) }
            }
            key.startsWith("android.resourcePrefix ") -> {
                updateProject { it.copy(resourcePrefix = getUnquotedValue(key)) }
            }
            key.startsWith("group=") -> {
                updateProject { it.copy(groupId = getUnquotedValue(key)) }
            }
            key.startsWith("android.buildToolsVersion ") -> {
                updateProject { it.copy(buildToolsVersion = getUnquotedValue(key)) }
            }
            line.startsWith("minifyEnabled ") && key.startsWith("android.buildTypes.") -> {
                updateBuildTypeFromContext(context) {
                    it.copy(isMinifyEnabled = SdkConstants.VALUE_TRUE == getUnquotedValue(line))
                }
            }
            key.startsWith("android.compileSdkVersion ") -> {
                val value = getUnquotedValue(key)
                updateProject { it.copy(compileTarget = if (Character.isDigit(value[0])) "android-$value" else value) }
            }
            line.startsWith("resConfig") -> { // and resConfigs
                updateFlavorFromContext(context) {
                    val configs = it.resourceConfigurations.toMutableSet()
                    for (s in Splitter.on(",").trimResults().split(line.substring(line.indexOf(' ') + 1))) {
                        if (!configs.contains(s)) {
                            configs.add(getUnquotedValue(s))
                        }
                    }
                    it.copy(resourceConfigurations = configs)
                }
            }
            key.startsWith("android.defaultConfig.vectorDrawables.useSupportLibrary ") -> {
                val value = getUnquotedValue(key)
                if (SdkConstants.VALUE_TRUE == value) {
                    updateVectorDrawableOptions {
                        it.copy(useSupportLibrary = true)
                    }
                }
            }
            key.startsWith(
                "android.compileOptions.sourceCompatibility JavaVersion.VERSION_"
            ) -> {
                updateSourceCompatibility(key.substring(key.indexOf("VERSION_") + "VERSION_".length).replace('_', '.'))
            }
            key.startsWith(
                "android.compileOptions.targetCompatibility JavaVersion.VERSION_"
            ) -> {
                updateTargetCompatibility(key.substring(key.indexOf("VERSION_") + "VERSION_".length).replace('_', '.'))
            }
            key.startsWith("buildscript.dependencies.classpath ") -> {
                if (key.contains("'com.android.tools.build:gradle:")) {
                    val value = getUnquotedValue(key)
                    val gc = GradleCoordinate.parseCoordinateString(value)
                    if (gc != null) {
                        updateModelVersion(gc.revision)
                    }
                } // else ignore other class paths
            }
            key.startsWith("android.defaultConfig.testInstrumentationRunner ") || key.contains(".proguardFiles ") || key == "dependencies.compile fileTree(dir: 'libs', include: ['*.jar'])" || key.startsWith("dependencies.androidTestCompile('") -> {
                // Ignored for now
            }
            line.startsWith("manifestPlaceholders [") &&
                key.startsWith("android.") &&
                line.endsWith("]") -> {
                fun updateManifestPlaceholders(manifestPlaceholders: MutableMap<String, String>) {
                    val mapString = key.substring(key.indexOf('[') + 1, key.indexOf(']')).trim { it <= ' ' }

                    // TODO: Support one than one more entry in the map? Comma separated list
                    val index = mapString.indexOf(':')
                    assert(index != -1) { mapString }
                    var mapKey = mapString.substring(0, index).trim { it <= ' ' }
                    mapKey = getUnquotedValue(mapKey)
                    var mapValue = mapString.substring(index + 1).trim { it <= ' ' }
                    mapValue = getUnquotedValue(mapValue)
                    manifestPlaceholders.put(mapKey, mapValue)
                }

                // Example:
                // android.defaultConfig.manifestPlaceholders [
                // localApplicationId:'com.example.manifest_merger_example']

                if (context.startsWith("android.buildTypes.")) {
                    updateBuildTypeFromContext(context) {
                        val manifestPlaceholders = it.manifestPlaceholders.toMutableMap()
                        updateManifestPlaceholders(manifestPlaceholders)
                        it.copy(manifestPlaceholders = manifestPlaceholders)
                    }
                } else updateFlavorFromContext(context) {
                    val manifestPlaceholders = it.manifestPlaceholders.toMutableMap()
                    updateManifestPlaceholders(manifestPlaceholders)
                    it.copy(manifestPlaceholders = manifestPlaceholders)
                }
            }
            key.startsWith("android.flavorDimensions ") -> {
                val value = key.substring("android.flavorDimensions ".length)
                updateProject {
                    it.copy(
                        flavorDimensions = it.flavorDimensions.toSet() +
                            Splitter.on(',').omitEmptyStrings()
                                .trimResults().split(value)
                                .map { getUnquotedValue(it) }
                    )
                }
            }
            line.startsWith("dimension ") && key.startsWith("android.productFlavors.") -> {
                val name = key.substring("android.productFlavors.".length, key.indexOf(".dimension"))
                updateProductFlavor(name, true) {
                    val dimension = getUnquotedValue(line)
                    it.copy(dimension = dimension)
                }
            }
            key.startsWith("android.") && line.startsWith("resValue ") -> {
                // Example:
                // android.defaultConfig.resValue 'string', 'defaultConfigName', 'Some DefaultConfig
                // Data'
                val index = key.indexOf(".resValue ")
                val name = key.substring("android.".length, index)

                fun updateResValues(resValues: MutableMap<String, IdeClassField>) {
                    var fieldName: String? = null
                    var value: String? = null
                    var type: String? = null
                    val declaration = key.substring(index + ".resValue ".length)
                    val splitter = Splitter.on(',').trimResults().omitEmptyStrings()
                    var resIndex = 0
                    for (component in splitter.split(declaration)) {
                        val component = getUnquotedValue(component)
                        when (resIndex) {
                            0 -> type = component
                            1 -> fieldName = component
                            2 -> value = component
                        }
                        resIndex++
                    }
                    val field = object : IdeClassField {
                        override val type: String = type!!
                        override val name: String = fieldName!!
                        override val value: String = value!!
                    }
                    resValues[fieldName!!] = field
                }

                if (name.startsWith("buildTypes.")) {
                    updateBuildTypeFromContext(context) {
                        val resValues = it.resValues.toMutableMap()
                        updateResValues(resValues)
                        it.copy(resValues = resValues)
                    }
                } else updateFlavorFromContext(context, defaultToDefault = true) {
                    val resValues = it.resValues.toMutableMap()
                    updateResValues(resValues)
                    it.copy(resValues = resValues)
                }
            }
            context.startsWith("android.splits.") &&
                context.indexOf('.', "android.splits.".length) == -1 -> {
                val type = context.substring("android.splits.".length).toUpperCase(Locale.ROOT)
                if (line == "reset") {
                    splits.removeAll(type)
                } else if (line.startsWith("include ")) {
                    val value = line.substring("include ".length)
                    for (s in Splitter.on(',').trimResults().omitEmptyStrings().split(value)) {
                        splits.put(type, getUnquotedValue(s))
                    }
                } else if (line.startsWith("exclude ")) {
                    warn("Warning: Split exclude not supported for mocked builder model yet")
                }
            }
            key.startsWith("android.aaptOptions.namespaced ") -> {
                val value = getUnquotedValue(key)
                if (SdkConstants.VALUE_TRUE == value) {
                    updateAaptOptions {
                        it.copy(namespacing = IdeAaptOptions.Namespacing.REQUIRED)
                    }
                }
            }
            key.startsWith("groupId ") -> {
                updateProject { it.copy(groupId = getUnquotedValue(key)) }
            }
            key.startsWith("android.lintOptions.") -> {
                key = key.substring("android.lintOptions.".length)
                val argIndex = key.indexOf(' ')
                if (argIndex == -1) {
                    error("No value supplied for lint option $key")
                    return
                }
                val arg = key.substring(argIndex).trim { it <= ' ' }
                key = key.substring(0, argIndex)
                when (key) {
                    "quiet" -> flags.isQuiet = toBoolean(arg)
                    "abortOnError" -> flags.isSetExitCode = toBoolean(arg)
                    "checkReleaseBuilds" -> error("Test framework doesn't support lint DSL flag checkReleaseBuilds")
                    "ignoreWarnings" -> flags.isIgnoreWarnings = toBoolean(arg)
                    "absolutePaths" -> flags.isFullPath = toBoolean(arg)
                    "checkAllWarnings" -> flags.isCheckAllWarnings = toBoolean(arg)
                    "warningsAsErrors" -> flags.isWarningsAsErrors = toBoolean(arg)
                    "noLines" -> flags.isShowSourceLines = !toBoolean(arg)
                    "showAll" -> flags.isShowEverything = toBoolean(arg)
                    "explainIssues" -> flags.isExplainIssues = toBoolean("explainIssues")
                    "textReport" -> error("Test framework doesn't support lint DSL flag textReport")
                    "xmlReport" -> error("Test framework doesn't support lint DSL flag xmlReport")
                    "htmlReport" -> error("Test framework doesn't support lint DSL flag htmlReport")
                    "sarifReport" -> error("Test framework doesn't support lint DSL flag sarifReport")
                    "checkTestSources" -> {
                        val checkTests = toBoolean(arg)
                        flags.isCheckTestSources = checkTests
                        updateLintOptions(null, null, null, checkTests, null)
                    }
                    "checkDependencies" -> {
                        val checkDependencies = toBoolean(arg)
                        flags.isCheckDependencies = checkDependencies
                        updateLintOptions(null, null, null, null, checkDependencies)
                    }
                    "checkGeneratedSources" -> flags.isCheckGeneratedSources = toBoolean(arg)
                    "enable" -> {
                        val ids = parseListDsl(arg)
                        flags.enabledIds.addAll(ids)
                        setLintSeverity(ids, Severity.WARNING)
                    }
                    "disable" -> {
                        val ids = parseListDsl(arg)
                        flags.suppressedIds.addAll(ids)
                        setLintSeverity(ids, Severity.IGNORE)
                    }
                    "check" -> flags.exactCheckedIds = parseListDsl(arg)
                    "fatal" -> parseSeverityOverrideDsl(Severity.FATAL, arg)
                    "error" -> parseSeverityOverrideDsl(Severity.ERROR, arg)
                    "warning" -> parseSeverityOverrideDsl(Severity.WARNING, arg)
                    "informational" -> parseSeverityOverrideDsl(Severity.INFORMATIONAL, arg)
                    "ignore" -> parseSeverityOverrideDsl(Severity.IGNORE, arg)
                    "lintConfig" -> {
                        val file = file(arg, true)
                        flags.lintConfig = file
                        updateLintOptions(null, file, null, null, null)
                    }
                    "textOutput" -> error("Test framework doesn't support lint DSL flag textOutput")
                    "xmlOutput" -> error("Test framework doesn't support lint DSL flag xmlOutput")
                    "htmlOutput" -> error("Test framework doesn't support lint DSL flag htmlOutput")
                    "saraifOutput" -> error("Test framework doesn't support lint DSL flag sarifOutput")
                    "baseline" -> {
                        val file = file(arg, true)
                        flags.baselineFile = file
                        updateLintOptions(file, null, null, null, null)
                    }
                }
            }
            key.startsWith("android.buildFeatures.") -> {
                key = key.substring("android.buildFeatures.".length)
                val argIndex = key.indexOf(' ')
                if (argIndex == -1) {
                    error("No value supplied for build feature: $key")
                    return
                }
                val arg = key.substring(argIndex).trim { it <= ' ' }
                key = key.substring(0, argIndex)
                when (key) {
                    "viewBinding" -> updateProject {
                        it.copy(viewBindingOptions = IdeViewBindingOptionsImpl(enabled = toBoolean(arg)))
                    }
                }
            }
            else -> {
                warn("ignored line: $line, context=$context")
            }
        }
    }

    private fun parseSeverityOverrideDsl(severity: Severity, dsl: String) {
        for (s in Splitter.on(',').trimResults().omitEmptyStrings().split(dsl)) {
            val id = stripQuotes(s, true)
            setLintSeverity(id, severity)
        }
    }

    private fun setLintSeverity(ids: Set<String>, severity: Severity) {
        for (id in ids) {
            setLintSeverity(id, severity)
        }
    }

    private fun setLintSeverity(id: String, severity: Severity) {
        flags.severityOverrides[id] = severity
        val severityValue: Int
        severityValue = when (severity) {
            Severity.FATAL -> LintOptions.SEVERITY_FATAL
            Severity.ERROR -> LintOptions.SEVERITY_ERROR
            Severity.WARNING -> LintOptions.SEVERITY_WARNING
            Severity.INFORMATIONAL -> LintOptions.SEVERITY_INFORMATIONAL
            Severity.IGNORE -> LintOptions.SEVERITY_IGNORE
            else -> LintOptions.SEVERITY_DEFAULT_ENABLED
        }
        severityOverrides[id] = severityValue
        updateLintOptions(null, null, severityOverrides, null, null)
    }

    private fun updateLintOptions(
        baseline: File?,
        lintConfig: File?,
        severities: Map<String, Int>?,
        tests: Boolean?,
        dependencies: Boolean?
    ) {
        updateProject {
            val lintOptions = it.lintOptions as IdeLintOptionsImpl
            it.copy(
                // No mocking IdeLintOptions; it's final
                lintOptions = lintOptions.copy(
                    baselineFile = baseline ?: lintOptions.baselineFile,
                    lintConfig = lintConfig ?: lintOptions.lintConfig,
                    severityOverrides = severities ?: severityOverrides,
                    isCheckTestSources = tests ?: lintOptions.isCheckTestSources,
                    isCheckDependencies = dependencies
                        ?: lintOptions.isCheckDependencies, // TODO: Allow these to be customized by model mocker
                )
            )
        }
    }

    private fun parseListDsl(dsl: String): Set<String> {
        val updates: MutableSet<String> = LinkedHashSet()
        for (s in Splitter.on(',').trimResults().omitEmptyStrings().split(dsl)) {
            updates.add(stripQuotes(s, true))
        }
        return updates
    }

    private fun file(gradle: String, reportError: Boolean): File {
        var gradle = gradle
        if (gradle.startsWith("file(\"") && gradle.endsWith("\")") ||
            gradle.startsWith("file('") && gradle.endsWith("')")
        ) {
            val path = gradle.substring(6, gradle.length - 2)
            return File(projectDir, path)
        }
        gradle = stripQuotes(gradle, true)
        if (gradle == "stdout" || gradle == "stderr") {
            return File(gradle)
        }
        if (reportError) {
            error("Only support file(\"\") paths in gradle mocker")
        }
        return File(gradle)
    }

    private fun stripQuotes(string: String, reportError: Boolean): String {
        if (string.startsWith("'") && string.endsWith("'") && string.length >= 2) {
            return string.substring(1, string.length - 1)
        }
        if (string.startsWith("\"") && string.endsWith("\"") && string.length >= 2) {
            return string.substring(1, string.length - 1)
        }
        if (reportError) {
            error("Expected quotes around $string")
        }
        return string
    }

    private fun updateProject(f: (IdeAndroidProjectImpl) -> IdeAndroidProjectImpl) {
        project = f(project)
    }

    private fun updateFlavorFromContext(
        context: String,
        defaultToDefault: Boolean = false,
        f: (IdeProductFlavorImpl) -> IdeProductFlavorImpl
    ) {
        when {
            context == "android.defaultConfig" -> {
                updateDefaultConfig(f)
            }
            context.startsWith("android.productFlavors.") -> {
                val name = context.substring("android.productFlavors.".length)
                updateProductFlavor(name, true, f)
            }
            else -> {
                if (defaultToDefault) {
                    updateDefaultConfig(f)
                } else {
                    error("Unexpected flavor context $context")
                }
            }
        }
    }

    private fun updateVectorDrawableOptions(f: (IdeVectorDrawablesOptionsImpl) -> IdeVectorDrawablesOptions) {
        updateDefaultConfig {
            it.copy(
                vectorDrawables =
                    f(
                        (it.vectorDrawables as? IdeVectorDrawablesOptionsImpl)
                            ?: IdeVectorDrawablesOptionsImpl(useSupportLibrary = null)
                    )
            )
        }
    }

    private fun updateBuildType(name: String, create: Boolean, f: (IdeBuildTypeImpl) -> IdeBuildTypeImpl) {
        val index = buildTypes.indexOfFirst { it.name == name }
        if (index >= 0) {
            updateProject {
                it.copy(
                    buildTypes = run {
                        val list = it.buildTypes.toMutableList()
                        list[index] = (list[index] as IdeBuildTypeContainerImpl).let { buildType ->
                            buildType.copy(buildType = f(buildType.buildType as IdeBuildTypeImpl))
                        }
                        list
                    }
                )
            }
        }
        if (index < 0) {
            if (create) {
                updateProject {
                    it.copy(
                        buildTypes = it.buildTypes + IdeBuildTypeContainerImpl(
                            buildType = f(createBuildType(name)),
                            sourceProvider = createSourceProvider(projectDir, name),
                            extraSourceProviders = emptyList()
                        )
                    )
                }
            } else {
                error("Couldn't find flavor $name")
            }
        }
    }

    private fun updateBuildTypeFromContext(
        context: String,
        f: (IdeBuildTypeImpl) -> IdeBuildTypeImpl
    ) {
        when {
            context.startsWith("android.buildTypes.") -> {
                val name = context.substring("android.buildTypes.".length)
                updateBuildType(name, true, f)
            }
            else -> {
                error("Unexpected build type context $context")
            }
        }
    }

    private fun createBuildType(name: String): IdeBuildTypeImpl {
        return IdeBuildTypeImpl(
            name = name,
            applicationIdSuffix = null,
            versionNameSuffix = null,
            resValues = emptyMap(),
            proguardFiles = emptyList(),
            consumerProguardFiles = emptyList(),
            manifestPlaceholders = emptyMap(),
            multiDexEnabled = null,
            isDebuggable = name.startsWith("debug"),
            isJniDebuggable = false,
            isRenderscriptDebuggable = false,
            renderscriptOptimLevel = 0,
            isMinifyEnabled = false,
            isZipAlignEnabled = false
        )
    }

    private fun updateSourceCompatibility(level: String) {
        updateCompileOptions { it.copy(sourceCompatibility = level) }
    }

    private fun updateTargetCompatibility(level: String) {
        updateCompileOptions { it.copy(targetCompatibility = level) }
    }

    private fun updateCompileOptions(f: (IdeJavaCompileOptionsImpl) -> IdeJavaCompileOptionsImpl) {
        updateProject {
            it.copy(
                javaCompileOptions = f(it.javaCompileOptions as IdeJavaCompileOptionsImpl)
            )
        }
    }

    private fun updateAaptOptions(f: (IdeAaptOptionsImpl) -> IdeAaptOptionsImpl) {
        updateProject {
            it.copy(
                aaptOptions = f(it.aaptOptions as IdeAaptOptionsImpl)
            )
        }
    }

    private fun updateModelVersion(modelVersion: String) {
        updateProject {
            it.copy(
                modelVersion = modelVersion
            )
        }
    }

    private fun block(
        name: String,
        @Language("Groovy") blockBody: String,
        context: String
    ) {
        if ("android.productFlavors" == context && productFlavors
            .none { flavor: IdeProductFlavor -> flavor.name == name }
        ) {
            // Defining new product flavors
            updateProductFlavor(name, true) { it }
        }
        if ("android.buildTypes" == context && buildTypes
            .none { buildType: IdeBuildType -> buildType.name == name }
        ) {
            // Defining new build types
            updateBuildType(name, true) { it }
        }
        scan(blockBody, if (context.isEmpty()) name else "$context.$name")
    }

    private fun updateProductFlavor(name: String, create: Boolean, f: (IdeProductFlavorImpl) -> IdeProductFlavorImpl) {
        val index = productFlavors.indexOfFirst { it.name == name }
        if (index >= 0) {
            updateProject {
                it.copy(
                    productFlavors = run {
                        val list = it.productFlavors.toMutableList()
                        list[index] = (list[index] as IdeProductFlavorContainerImpl).let { productFlavor ->
                            productFlavor.copy(productFlavor = f(productFlavor.productFlavor as IdeProductFlavorImpl))
                        }
                        list
                    }
                )
            }
        }
        if (index < 0 && create) {
            updateProject {
                it.copy(
                    productFlavors = it.productFlavors + IdeProductFlavorContainerImpl(
                        productFlavor = f(createProductFlavor(name)),
                        sourceProvider = createSourceProvider(projectDir, name),
                        extraSourceProviders = listOf(
                            IdeSourceProviderContainerImpl(
                                ARTIFACT_NAME_ANDROID_TEST,
                                createSourceProvider(projectDir, "androidTest".appendCapitalized(name))
                            ),
                            IdeSourceProviderContainerImpl(
                                ARTIFACT_NAME_UNIT_TEST,
                                createSourceProvider(projectDir, "test".appendCapitalized(name))
                            ),
                        )
                    )
                )
            }
        }
    }

    private fun updateDefaultConfig(f: (IdeProductFlavorImpl) -> IdeProductFlavorImpl) {
        updateProject {
            val flavorContainerImpl = it.defaultConfig as IdeProductFlavorContainerImpl
            it.copy(
                defaultConfig = flavorContainerImpl.copy(
                    productFlavor = f(flavorContainerImpl.productFlavor as IdeProductFlavorImpl)
                )
            )
        }
    }

    private val defaultConfig get() = project.defaultConfig.productFlavor as IdeProductFlavorImpl

    private fun createProductFlavor(name: String): IdeProductFlavorImpl {
        return IdeProductFlavorImpl(
            name = name,
            applicationIdSuffix = null,
            versionNameSuffix = null,
            resValues = emptyMap(),
            proguardFiles = emptyList(),
            consumerProguardFiles = emptyList(),
            manifestPlaceholders = emptyMap(),
            multiDexEnabled = null,
            dimension = null,
            applicationId = null,
            versionCode = null,
            versionName = null,
            minSdkVersion = null,
            targetSdkVersion = null,
            maxSdkVersion = null,
            testApplicationId = null,
            testInstrumentationRunner = null,
            testInstrumentationRunnerArguments = emptyMap(),
            testHandleProfiling = null,
            testFunctionalTest = null,
            resourceConfigurations = emptySet(),
            vectorDrawables = null
        )
    }

    private fun createApiVersion(value: String): IdeApiVersion {
        val s = value.substring(value.indexOf(' ') + 1)
        if (s.startsWith("'")) {
            val codeName = getUnquotedValue(s)
            val sdkVersion = SdkVersionInfo.getVersion(codeName, null)
            if (sdkVersion != null) {
                return IdeApiVersionImpl(
                    codename = sdkVersion.codename,
                    apiString = sdkVersion.apiString,
                    apiLevel = sdkVersion.apiLevel,
                )
            }
        } else {
            return IdeApiVersionImpl(
                apiString = s,
                codename = null,
                apiLevel = s.toInt(),
            )
        }
        return IdeApiVersionImpl(
            apiString = "",
            codename = null,
            apiLevel = 0,
        )
    }

    private fun addDependency(declaration: String, artifact: String?, isProvided: Boolean) {
        // If it's one of the common libraries, built up the full dependency graph
        // that we know will actually be used
        //
        // To compute these graphs, put the dependency you're interested into
        // a test project and then run ./gradlew app:dependencies
        val wellKnownLibrary = wellKnownLibraries.firstOrNull { declaration.startsWith(it.groupAndName + ":") }
        if (wellKnownLibrary != null) {
            val version = declaration
                .substring(wellKnownLibrary.groupAndName.length + 1)
                .substringBefore("@") //@jar/aar is not part of the version and should not be applied to other dependencies.
            addTransitiveLibrary(
                wellKnownLibrary.dependencies.replace("VERSION", version),
                artifact
            )
        } else {
            // Look for the library in the dependency graph provided
            val dep = graphs[declaration]
            if (dep != null) {
                addLibrary(dep, artifact)
            } else {
                val artifactDeps = artifactByName(artifact)
                if (isJavaLibrary(declaration)) {
                    // Not found in dependency graphs: create a single Java library
                    val library = createJavaLibrary(declaration, isProvided)
                    artifactDeps?.javaLibraries?.add(library)
                } else {
                    // Not found in dependency graphs: create a single Android library
                    val library = createAndroidLibrary(declaration, isProvided)
                    artifactDeps?.androidLibraries?.add(library)
                }
            }
        }
    }

    private fun artifactByName(artifact: String?) = when (artifact) {
        null, "" -> main
        "test" -> test
        "androidTest" -> androidTest
        else -> {
            error("Unrecognized artifact name: $artifact")
            null
        }
    }

    private fun addTransitiveLibrary(graph: String, artifact: String?) {
        for (dep in parseDependencyGraph(graph)) {
            addLibrary(dep, artifact)
        }
    }

    private fun addLibrary(dep: Dep, artifact: String?) {
        val artifactDeps = artifactByName(artifact) ?: return
        val libraries = dep.createLibrary()
        for (library in libraries) {
            when (library) {
                is IdeAndroidLibrary -> artifactDeps.androidLibraries.add(library)
                is IdeJavaLibrary -> artifactDeps.javaLibraries.add(library)
                is IdeModuleLibrary -> artifactDeps.moduleLibraries.add(library)
            }
        }
    }

    private fun createAndroidLibrary(coordinateString: String, isProvided: Boolean): IdeAndroidLibrary {
        return createAndroidLibrary(coordinateString, null, isProvided, null)
    }

    private fun createAndroidLibrary(
        coordinateString: String,
        promotedTo: String?,
        isProvided: Boolean,
        jar: File?
    ): IdeAndroidLibrary {
        var jar = jar
        val coordinate = getCoordinate(coordinateString, promotedTo, GradleCoordinate.ArtifactType.AAR)
        val dir = File(
            projectDir,
            "build/intermediates/exploded-aar/" +
                coordinate.groupId +
                "/" +
                coordinate.artifactId +
                "/" +
                coordinate.revision
        )
        if (jar == null) {
            jar = File(dir, "jars/" + SdkConstants.FN_CLASSES_JAR)
        }
        if (!jar.exists()) {
            createEmptyJar(jar)
        }
        return deduplicateLibrary(
            IdeAndroidLibraryImpl(
                IdeAndroidLibraryCore(
                    coordinate.toString(),
                    dir,
                    SdkConstants.FN_ANDROID_MANIFEST_XML,
                    jar.path, // non relative path is fine here too.
                    jar.path, // non relative path is fine here too.
                    "res",
                    null,
                    "assets", emptyList(),
                    "jni",
                    "aidl",
                    "rs",
                    "proguard.pro",
                    libraryLintJars.getOrDefault(coordinateString, "lint.jar"),
                    SdkConstants.FN_ANNOTATIONS_ZIP,
                    libraryPublicResourcesFiles.getOrDefault(
                        coordinateString, "public.txt"
                    ),
                    "../lib.aar",
                    librarySymbolFiles.getOrDefault(coordinateString, "R.txt")
                ),
                isProvided
            )
        )
    }

    private fun createJavaLibrary(coordinateString: String, isProvided: Boolean): IdeJavaLibrary {
        return createJavaLibrary(coordinateString, null, isProvided, null)
    }

    private fun createJavaLibrary(
        coordinateString: String,
        promotedTo: String?,
        isProvided: Boolean,
        jar: File?
    ): IdeJavaLibrary {
        var jar = jar
        val coordinate = getCoordinate(coordinateString, promotedTo, GradleCoordinate.ArtifactType.JAR)
        if (jar == null) {
            jar = File(
                projectDir,
                "caches/modules-2/files-2.1/" +
                    coordinate.groupId +
                    "/" +
                    coordinate.artifactId +
                    "/" +
                    coordinate.revision +
                    // Usually some hex string here, but keep same to keep test
                    // behavior stable
                    "9c6ef172e8de35fd8d4d8783e4821e57cdef7445/" +
                    coordinate.artifactId +
                    "-" +
                    coordinate.revision +
                    SdkConstants.DOT_JAR
            )
            if (!jar.exists()) {
                createEmptyJar(jar)
            }
        }
        return deduplicateLibrary(
            IdeJavaLibraryImpl(
                IdeJavaLibraryCore(coordinate.toString(), jar), isProvided
            )
        )
    }

    private fun createModuleLibrary(name: String): IdeModuleLibrary {
        return deduplicateLibrary(
            IdeModuleLibraryImpl(
                IdeModuleLibraryCore(name, "artifacts:$name", null), false
            )
        )
    }

    private fun deduplicateLibrary(library: IdeAndroidLibrary): IdeAndroidLibrary {
        return androidLibraryInstances.computeIfAbsent(library) { it: IdeAndroidLibrary? -> library }
    }

    private fun deduplicateLibrary(library: IdeJavaLibrary): IdeJavaLibrary {
        return javaLibraryInstances.computeIfAbsent(library) { it: IdeJavaLibrary? -> library }
    }

    private fun deduplicateLibrary(library: IdeModuleLibrary): IdeModuleLibrary {
        return moduleLibraryInstances.computeIfAbsent(library) { it: IdeModuleLibrary? -> library }
    }

    private fun getCoordinate(
        coordinateString: String,
        promotedTo: String?,
        type: GradleCoordinate.ArtifactType
    ): GradleCoordinate {
        var coordinateString = coordinateString
        var coordinate = GradleCoordinate.parseCoordinateString(coordinateString)
        coordinate = GradleCoordinate(
            coordinate!!.groupId,
            coordinate.artifactId,
            GradleCoordinate.parseRevisionNumber(
                promotedTo ?: coordinate.revision
            ),
            if (coordinate.artifactType != null) coordinate.artifactType else type
        )
        coordinateString = coordinate.toString()
        TestCase.assertNotNull(coordinateString, coordinate)
        return coordinate
    }

    fun setVariantName(variantName: String) {
        defaultVariantName = variantName
    }

    /**
     * Given a dependency graph, returns a populated [Dependencies] object. You can generate
     * Gradle dependency graphs by running for example:
     *
     * <pre>
     * $ ./gradlew :app:dependencies
     </pre> *
     *
     *
     * Sample graph:
     *
     * <pre>
     * \--- com.android.support.test.espresso:espresso-core:2.2.2
     * +--- com.squareup:javawriter:2.1.1
     * +--- com.android.support.test:rules:0.5
     * |    \--- com.android.support.test:runner:0.5
     * |         +--- junit:junit:4.12
     * |         |    \--- org.hamcrest:hamcrest-core:1.3
     * |         \--- com.android.support.test:exposed-instrumentation-api-publish:0.5
     * +--- com.android.support.test:runner:0.5 (*)
     * +--- javax.inject:javax.inject:1
     * +--- org.hamcrest:hamcrest-library:1.3
     * |    \--- org.hamcrest:hamcrest-core:1.3
     * +--- com.android.support.test.espresso:espresso-idling-resource:2.2.2
     * +--- org.hamcrest:hamcrest-integration:1.3
     * |    \--- org.hamcrest:hamcrest-library:1.3 (*)
     * +--- com.google.code.findbugs:jsr305:2.0.1
     * \--- javax.annotation:javax.annotation-api:1.2
     </pre> *
     *
     * @param graph the graph
     * @return the corresponding dependencies
     */
    private fun createDependencies(graph: String): IdeDependencies {
        val deps = parseDependencyGraph(graph)
        return createDependencies(deps)
    }

    private fun parseDependencyGraph(graph: String, map: MutableMap<String, Dep> = Maps.newHashMap()): List<Dep> {
        val lines = graph.split("\n").filter { it.isNotBlank() }.toTypedArray()
        // TODO: Check that it's using the expected graph format - e.g. indented to levels
        // that are multiples of 5
        if (lines.size == 0) {
            return emptyList()
        }
        val root: Dep = Dep("", 0)
        val stack: Deque<Dep> = ArrayDeque()
        stack.push(root)
        var parent = root
        for (line in lines) {
            val depth = getDepth(line)
            val dep: Dep = Dep(line.substring(getIndent(line)), depth)
            map[dep.coordinateString] = dep
            if (depth == parent.depth + 1) {
                // Just to append to parent
                parent.add(dep)
            } else if (depth == parent.depth + 2) {
                val lastChild: Dep? = parent.lastChild
                if (lastChild != null) {
                    lastChild.add(dep)
                    stack.push(lastChild)
                    parent = lastChild
                } else {
                    parent.add(dep)
                }
            } else {
                while (true) {
                    stack.pop()
                    parent = stack.peek()
                    if (parent.depth == depth - 1) {
                        parent.add(dep)
                        break
                    }
                }
            }
        }
        return root.children
    }

    private fun createDependencies(deps: List<Dep>): IdeDependencies {
        val result: MutableCollection<IdeLibrary> = LinkedHashSet()
        for (dep in deps) {
            val androidLibrary = dep.createAndroidLibrary()
            result.addAll(androidLibrary)
        }
        return IdeDependenciesImpl(
            result.filterIsInstance<IdeAndroidLibrary>(),
            result.filterIsInstance<IdeJavaLibrary>(),
            result.filterIsInstance<IdeModuleLibrary>(),
            ImmutableList.of()
        )
    }

    /** Dependency graph node  */
    inner class Dep(coordinateString: String, depth: Int) {
        val coordinate: GradleCoordinate?
        val coordinateString: String
        val promotedTo: String?
        val children: MutableList<Dep> = mutableListOf()
        val depth: Int
        private fun trimStars(coordinateString: String): String {
            var coordinateString = coordinateString
            if (coordinateString.endsWith(" (*)")) {
                coordinateString = coordinateString.substring(0, coordinateString.length - " (*)".length)
            }
            return coordinateString
        }

        fun add(child: Dep) {
            children.add(child)
        }

        val isJavaLibrary: Boolean
            get() = isJavaLibrary(coordinateString)
        val isProject: Boolean
            get() = coordinate == null && coordinateString.startsWith("project ")

        fun createLibrary(): Collection<IdeLibrary> {
            return if (isJavaLibrary) {
                createJavaLibrary()
            } else {
                createAndroidLibrary()
            }
        }

        fun createAndroidLibrary(): Collection<IdeLibrary> {
            val result: MutableCollection<IdeLibrary> = LinkedHashSet()
            if (isProject) {
                val name = coordinateString.substring("project ".length)
                result.add(createModuleLibrary(name))
            } else {
                result.add(
                    this@GradleModelMocker.createAndroidLibrary(
                        coordinateString, promotedTo, false, null
                    )
                )
            }
            if (!children.isEmpty()) {
                for (dep in children) {
                    result.addAll(dep.createLibrary())
                }
            }
            return result
        }

        private fun createJavaLibrary(): Collection<IdeLibrary> {
            val result: MutableCollection<IdeLibrary> = LinkedHashSet()
            if (isProject) {
                val name = coordinateString.substring("project ".length)
                result.add(createModuleLibrary(name))
            } else {
                result.add(
                    this@GradleModelMocker.createJavaLibrary(
                        coordinateString, promotedTo, false, null
                    )
                )
            }
            if (!children.isEmpty()) {
                for (dep in children) {
                    result.addAll(dep.createLibrary())
                }
            }
            return result
        }

        val lastChild: Dep?
            get() = if (children.isEmpty()) null else children[children.size - 1]

        override fun toString(): String {
            return coordinate.toString() + ":" + depth
        }

        // For debugging
        fun printTree(indent: Int, writer: PrintStream) {
            for (i in 0 until indent) {
                writer.print("    ")
            }
            writer.println(coordinate)
            for (child in children) {
                child.printTree(indent + 1, writer)
            }
        }

        init {
            var coordinateString = coordinateString
            val promoted = coordinateString.indexOf(" -> ")
            var aPromotedTo: String?
            if (promoted != -1) {
                aPromotedTo = coordinateString.substring(promoted + 4)
                coordinateString = coordinateString.substring(0, promoted)
            } else {
                aPromotedTo = null
            }
            coordinateString = trimStars(coordinateString)
            if (aPromotedTo != null) {
                aPromotedTo = trimStars(aPromotedTo)
            }
            promotedTo = aPromotedTo
            this.coordinateString = coordinateString
            coordinate =
                if (!coordinateString.isEmpty()) GradleCoordinate.parseCoordinateString(coordinateString) else null
            this.depth = depth
        }
    }

    companion object {
        /**
         * Extension to [AndroidProjectTypes] for non-Android project types, consumed in [ ]
         */
        const val PROJECT_TYPE_JAVA_LIBRARY = 999
        const val PROJECT_TYPE_JAVA = 998

        private val configurationPattern = Pattern.compile(
            "^dependencies\\.(|test|androidTest)([Cc]ompile|[Ii]mplementation)[ (].*"
        )
        private var libraryVersion = 0
        private fun normalize(line: String): String {
            var line = line
            line = line.trim { it <= ' ' }
            var commentIndex = line.indexOf("//")
            if (commentIndex != -1) {
                line = line.substring(0, commentIndex).trim { it <= ' ' }
            }
            while (true) {
                // Strip out embedded comment markers, if any (there could be multiple)
                commentIndex = line.indexOf("/*")
                if (commentIndex == -1) {
                    break
                }
                val commentEnd = line.indexOf("*/", commentIndex + 2)
                if (commentEnd == -1) {
                    break
                }
                line = line.substring(0, commentIndex) + line.substring(commentEnd + 2)
            }
            return line.replace("\\s+".toRegex(), " ").replace('"', '\'').replace(" = ", " ")
        }

        private fun findNonSpaceCharacterBackwards(s: String, index: Int): Char {
            var curr = index
            while (curr > 0) {
                val c = s[curr]
                if (!Character.isWhitespace(c)) {
                    return c
                }
                curr--
            }
            return 0.toChar()
        }

        private fun toBoolean(string: String): Boolean {
            if (string.equals("true", ignoreCase = true)) {
                return true
            }
            if (string.equals("false", ignoreCase = true)) {
                return false
            }
            throw IllegalArgumentException("String $string should be 'true' or 'false'")
        }

        private fun createAndroidArtifactOutput(
            filterType: String,
            identifier: String
        ): IdeAndroidArtifactOutput {
            return IdeAndroidArtifactOutputImpl(
                filters =
                    if (filterType.isEmpty()) emptyList()
                    else listOf(
                        object : FilterData {
                            override fun getIdentifier() = identifier
                            override fun getFilterType() = filterType
                        }
                    ),
                versionCode = 0,
                outputFile = File("")
            )
        }

        private fun createSourceProvider(
            root: File,
            name: String
        ): IdeSourceProvider {
            return IdeSourceProviderImpl(
                myName = name,
                myFolder = root,
                myManifestFile = File(root, "src/" + name + "/" + SdkConstants.ANDROID_MANIFEST_XML).path,
                myJavaDirectories = listOf(
                    File(root, "src/$name/java").path,
                    File(root, "src/$name/kotlin").path
                ),
                myKotlinDirectories = listOf(
                    File(root, "src/$name/java").path,
                    File(root, "src/$name/kotlin").path
                ),
                myResDirectories = listOf(
                    File(root, "src/$name/res").path
                ),
                myAssetsDirectories = listOf(
                    File(root, "src/$name/assets").path
                ),
                myResourcesDirectories = emptyList(),
                myAidlDirectories = emptyList(),
                myRenderscriptDirectories = emptyList(),
                myJniLibsDirectories = emptyList(),
                myShadersDirectories = emptyList(),
                myMlModelsDirectories = emptyList()
            )
        }

        /**
         * Returns whether a library declaration is a plain Java library instead of an Android library.
         * There is no way to tell from the Gradle description; it involves looking at the actual Maven
         * artifacts. For mocking purposes we have a hardcoded list.
         */
        private fun isJavaLibrary(declaration: String): Boolean {
            return _isJavaLibrary(declaration).also {
                when (it) {
                    true -> assert(!declaration.contains("@aar"))
                    false -> assert(!declaration.contains("@jar"))
                }
            }
        }

        private fun _isJavaLibrary(declaration: String): Boolean {
            if (declaration.startsWith("com.android.support:support-annotations:")) {
                return true
            } else if (declaration.startsWith("com.android.support:support-v4:") ||
                declaration.startsWith("com.android.support:support-v13:")
            ) {
                // Jar prior to to v20
                return (
                    declaration.contains(":13") ||
                        declaration.contains(":18") ||
                        declaration.contains(":19")
                    )
            } else if (declaration.startsWith("com.google.guava:guava:")) {
                return true
            } else if (declaration.startsWith("com.google.android.wearable:wearable:")) {
                return true
            } else if (declaration.startsWith(
                "com.android.support.constraint:constraint-layout-solver:"
            )
            ) {
                return true
            } else if (declaration.startsWith("junit:junit:")) {
                return true
            } else if (declaration.startsWith("org.jetbrains.kotlin:kotlin-") ||
                declaration.startsWith("org.jetbrains:annotations")
            ) {
                return true
            }
            return false
        }

        private fun getDepth(line: String): Int {
            return getIndent(line) / 5
        }

        private fun getIndent(line: String): Int {
            for (i in line.indices) {
                val c = line[i]
                if (Character.isLetter(c)) {
                    return i
                }
            }
            return line.length
        }
    }

    init {
        flags.severityOverrides = HashMap()
    }
}

private data class WellKnownLib(
    val groupAndName: String,
    val dependencies: String,
)

private val wellKnownLibraries = listOf(
    WellKnownLib(
        "com.android.support:appcompat-v7",
        """
+--- com.android.support:appcompat-v7:VERSION
|    +--- com.android.support:support-v4:VERSION
|    |    +--- com.android.support:support-compat:VERSION
|    |    |    \--- com.android.support:support-annotations:VERSION
|    |    +--- com.android.support:support-media-compat:VERSION
|    |    |    \--- com.android.support:support-compat:VERSION (*)
|    |    +--- com.android.support:support-core-utils:VERSION
|    |    |    \--- com.android.support:support-compat:VERSION (*)
|    |    +--- com.android.support:support-core-ui:VERSION
|    |    |    \--- com.android.support:support-compat:VERSION (*)
|    |    \--- com.android.support:support-fragment:VERSION
|    |         +--- com.android.support:support-compat:VERSION (*)
|    |         +--- com.android.support:support-media-compat:VERSION (*)
|    |         +--- com.android.support:support-core-ui:VERSION (*)
|    |         \--- com.android.support:support-core-utils:VERSION (*)
|    +--- com.android.support:support-vector-drawable:VERSION
|    |    \--- com.android.support:support-compat:VERSION (*)
|    \--- com.android.support:animated-vector-drawable:VERSION
|         \--- com.android.support:support-vector-drawable:VERSION (*)
"""
    ),
    WellKnownLib(
        "com.android.support:support-v4",
        """
+--- com.android.support:support-v4:VERSION
|    +--- com.android.support:support-compat:VERSION
|    |    \--- com.android.support:support-annotations:VERSION
|    +--- com.android.support:support-media-compat:VERSION
|    |    \--- com.android.support:support-compat:VERSION (*)
|    +--- com.android.support:support-core-utils:VERSION
|    |    \--- com.android.support:support-compat:VERSION (*)
|    +--- com.android.support:support-core-ui:VERSION
|    |    \--- com.android.support:support-compat:VERSION (*)
|    \--- com.android.support:support-fragment:VERSION
|         +--- com.android.support:support-compat:VERSION (*)
|         +--- com.android.support:support-media-compat:VERSION (*)
|         +--- com.android.support:support-core-ui:VERSION (*)
|         \--- com.android.support:support-core-utils:VERSION (*)
"""
    ),
    WellKnownLib(
        "com.android.support.constraint:constraint-layout",
        """
+--- com.android.support.constraint:constraint-layout:VERSION
     \--- com.android.support.constraint:constraint-layout-solver:VERSION
"""
    ),
    WellKnownLib(
        "com.firebase:firebase-client-android",
        """
\--- com.firebase:firebase-client-android:VERSION
     \--- com.firebase:firebase-client-jvm:VERSION
          +--- com.fasterxml.jackson.core:jackson-databind:2.2.2
          |    +--- com.fasterxml.jackson.core:jackson-annotations:2.2.2
          |    \--- com.fasterxml.jackson.core:jackson-core:2.2.2
          \--- com.firebase:tubesock:0.0.12"""
    ),
    WellKnownLib(
        "com.android.support:design",
        """
+--- com.android.support:design:VERSION
|    +--- com.android.support:recyclerview-v7:VERSION
|    |    +--- com.android.support:support-annotations:VERSION
|    |    \--- com.android.support:support-v4:VERSION (*)
|    +--- com.android.support:appcompat-v7:VERSION (*)
|    \--- com.android.support:support-v4:VERSION (*)"""
    ),
    WellKnownLib(
        "com.google.android.gms:play-services-analytics",
        """
+--- com.google.android.gms:play-services-analytics:VERSION
|    \--- com.google.android.gms:play-services-basement:VERSION
|         \--- com.android.support:support-v4:23.0.0 -> 23.4.0
|              \--- com.android.support:support-annotations:23.4.0"""
    ),
    WellKnownLib(
        "com.google.android.gms:play-services-gcm",
        """
+--- com.google.android.gms:play-services-gcm:VERSION
|    +--- com.google.android.gms:play-services-base:VERSION (*)
|    \--- com.google.android.gms:play-services-measurement:VERSION
|         \--- com.google.android.gms:play-services-basement:VERSION (*)"""
    ),
    WellKnownLib(
        "com.google.android.gms:play-services-appindexing",
        """
+--- com.google.android.gms:play-services-appindexing:VERSION
|    \--- com.google.android.gms:play-services-base:VERSION
|         \--- com.google.android.gms:play-services-basement:VERSION (*)"""
    ),
    WellKnownLib(
        "org.jetbrains.kotlin:kotlin-stdlib-jdk7",
        """
+--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:VERSION
|    \--- org.jetbrains.kotlin:kotlin-stdlib:VERSION
|         +--- org.jetbrains.kotlin:kotlin-stdlib-common:VERSION
|         \--- org.jetbrains:annotations:13.0
+--- org.jetbrains.kotlin:kotlin-stdlib:VERSION (*)
+--- org.jetbrains.kotlin:kotlin-stdlib-common:VERSION"""
    ),
    WellKnownLib(
        "org.jetbrains.kotlin:kotlin-stdlib-jdk8",
        """
+--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:VERSION
|    +--- org.jetbrains.kotlin:kotlin-stdlib:VERSION
|    |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:VERSION
|    |    \--- org.jetbrains:annotations:13.0
|    \--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:VERSION
|         \--- org.jetbrains.kotlin:kotlin-stdlib:VERSION (*)"""
    ),
)
