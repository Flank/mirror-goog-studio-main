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

import com.android.SdkConstants
import com.android.ide.common.repository.GradleCoordinate
import com.android.ide.common.repository.GradleVersion
import com.android.sdklib.AndroidTargetHash
import com.android.sdklib.AndroidVersion
import com.android.sdklib.SdkVersionInfo
import com.android.tools.lint.LintCliFlags
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.model.DefaultLintModelDependency
import com.android.tools.lint.model.DefaultLintModelDependencyGraph
import com.android.tools.lint.model.DefaultLintModelLibraryResolver
import com.android.tools.lint.model.LintModelAndroidArtifact
import com.android.tools.lint.model.LintModelAndroidLibrary
import com.android.tools.lint.model.LintModelBuildFeatures
import com.android.tools.lint.model.LintModelDependencies
import com.android.tools.lint.model.LintModelDependency
import com.android.tools.lint.model.LintModelDependencyGraph
import com.android.tools.lint.model.LintModelJavaArtifact
import com.android.tools.lint.model.LintModelJavaLibrary
import com.android.tools.lint.model.LintModelLibrary
import com.android.tools.lint.model.LintModelLibraryResolver
import com.android.tools.lint.model.LintModelLintOptions
import com.android.tools.lint.model.LintModelMavenName
import com.android.tools.lint.model.LintModelModule
import com.android.tools.lint.model.LintModelModuleLibrary
import com.android.tools.lint.model.LintModelModuleLoader
import com.android.tools.lint.model.LintModelModuleType
import com.android.tools.lint.model.LintModelNamespacingMode
import com.android.tools.lint.model.LintModelResourceField
import com.android.tools.lint.model.LintModelSeverity
import com.android.tools.lint.model.LintModelSourceProvider
import com.android.tools.lint.model.LintModelVariant
import com.android.utils.ILogger
import com.android.utils.appendCamelCase
import com.android.utils.appendCapitalized
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Charsets
import com.google.common.base.Splitter
import com.google.common.collect.ArrayListMultimap
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
 * A utility class which builds mocks for the Gradle builder-model API,
 * by loosely interpreting .gradle files and building models based on
 * recognizing common patterns there.
 *
 * TODO: Clean way to configure whether build dep cache is enabled
 * TODO: Handle scopes (test dependencies etc)
 */

class GradleModelMocker @JvmOverloads constructor(
    @field:Language("Groovy") @param:Language("Groovy") private val gradle: String,
    @VisibleForTesting val projectDir: File = File("")
) : LintModelLibraryResolver {

    private class DepConf {
        val androidLibraries: MutableSet<TestLintModelAndroidLibrary> = mutableSetOf()
        val javaLibraries: MutableSet<TestLintModelJavaLibrary> = mutableSetOf()
        val moduleLibraries: MutableSet<TestLintModelModuleLibrary> = mutableSetOf()
    }

    private var moduleModel: TestLintModelModule = TestLintModelModule(
        dir = projectDir,
        compileTarget = "android-" + SdkVersionInfo.HIGHEST_KNOWN_API,
        gradleVersion = GradleVersion.parse("2.2.2"),
        javaSourceLevel = "1.7",
        modulePath = "test_project"
    )
    private var buildFeatures: TestLintModelBuildFeatures = TestLintModelBuildFeatures()
    private var defaultConfig: TestProductFlavor = TestProductFlavor(
        name = "defaultConfig",
        mainSourceProvider = createSourceProvider(projectDir, "main"),
        unitTestSourceProvider = createSourceProvider(projectDir, "test", isUnitTest = true),
        instrumentationTestSourceProvider = createSourceProvider(projectDir, "androidTest", isInstrumentationTest = true)
    )
    private var flavorDimensions: List<String> = emptyList()
    private var buildTypes: List<TestBuildType> = emptyList()
    private var productFlavors: List<TestProductFlavor> = emptyList()
    private var variants: MutableList<TestLintModelVariant> = mutableListOf()
    private var defaultVariantName: String = ""

    private val libraryLintJars: MutableMap<String, String> = HashMap()
    private val libraryPublicResourcesFiles: MutableMap<String, String> = HashMap()
    private val librarySymbolFiles: MutableMap<String, String> = HashMap()

    private val androidLibraryInstances: MutableMap<TestLintModelAndroidLibrary, TestLintModelAndroidLibrary> = mutableMapOf()
    private val javaLibraryInstances: MutableMap<TestLintModelJavaLibrary, TestLintModelJavaLibrary> = mutableMapOf()
    private val moduleLibraryInstances: MutableMap<TestLintModelModuleLibrary, TestLintModelModuleLibrary> = mutableMapOf()
    private val libraryTable: MutableMap<String, LintModelLibrary> = mutableMapOf()

    private val main = DepConf()
    private val test = DepConf()
    private val androidTest = DepConf()

    private val severityOverrides = HashMap<String, LintModelSeverity>()
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
        updateModule {
            it.copy(lintRuleJars = it.lintRuleJars + File(lintRuleJarPath))
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
     * If true, model a full/deep dependency graph in [ ]; the default
     * is flat. (This is normally controlled by sync/model builder flag
     * `AndroidProject.PROPERTY_BUILD_MODEL_FEATURE_FULL_DEPENDENCIES`.)
     */
    @Suppress("unused")
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

    private var hasJavaOrJavaLibraryPlugin: Boolean = false
    private var _isLibrary: Boolean = false

    /**
     * Whether the Gradle file applied the java or java-library plugin.
     */
    fun hasJavaOrJavaLibraryPlugin(): Boolean {
        ensureInitialized()
        return hasJavaOrJavaLibraryPlugin
    }

    val isLibrary: Boolean
        get() {
            ensureInitialized()
            return _isLibrary
        }

    val buildTargetHash: String?
        get() {
            ensureInitialized()
            return moduleModel.compileTarget.takeUnless { it.isEmpty() }
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
            return moduleModel.variants.single { it.name == defaultVariantName }.mainArtifact.generatedSourceFolders
        }

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
        return moduleModel
    }

    private fun initialize() {
        // built-in build-types
        updateBuildType("debug", true, { it })
        updateBuildType("release", true, { it })

        addLocalLibs(File(projectDir, "libs"))

        scan(gradle, "")

        // Artifacts
        updateDefaultConfig { it.copy(applicationId = it.applicationId ?: "test.pkg") }

        updateModule { it.copy(buildFolder = File(projectDir, "build")) }

        val dependencies = createDependencies(main)
        val androidTestDependencies = createDependencies(androidTest)
        val testDependencies = createDependencies(test)

        val variantCoordinates = generateVariants()
        defaultVariantName = variantCoordinates.first().let { buildVariantName(it.second, it.first) }

        variantCoordinates.forEach { (buildType, productFlavors) ->
            val variantName = buildVariantName(productFlavors, buildType)
            val productFlavorsInConfigOrder = productFlavors.sortedBy { this.productFlavors.indexOf(it) }
            val sourceProviders: List<TestLintModelSourceProvider> =
                listOfNotNull(defaultConfig.mainSourceProvider) +
                    productFlavorsInConfigOrder.mapNotNull { it.mainSourceProvider } +
                    listOfNotNull(
                        if (productFlavorsInConfigOrder.size > 1) createSourceProvider(
                            projectDir,
                            buildVariantName(productFlavors)
                        ) else null
                    ) +
                    listOfNotNull(buildType.mainSourceProvider) +
                    listOfNotNull(
                        if (productFlavorsInConfigOrder.isNotEmpty())
                            createSourceProvider(projectDir, variantName, isDebugOnly = buildType.isDebuggable)
                        else null
                    )
            val testSourceProviders =
                listOfNotNull(defaultConfig.instrumentationTestSourceProvider, defaultConfig.unitTestSourceProvider) +
                    productFlavorsInConfigOrder.flatMap { listOfNotNull(it.instrumentationTestSourceProvider, it.unitTestSourceProvider) } +
                    listOfNotNull(buildType.instrumentationTestSourceProvider, buildType.unitTestSourceProvider)
            val generated = File(projectDir, "generated")
            val mergedFlavorsAndBuildType = merge(defaultConfig, productFlavors, buildType)
            variants.add(
                TestLintModelVariant(
                    _module = { moduleModel },
                    name = variantName,
                    useSupportLibraryVectorDrawables = mergedFlavorsAndBuildType.useSupportLibrary ?: false,
                    mainArtifact = TestLintModelAndroidArtifact(
                        applicationId = mergedFlavorsAndBuildType.applicationId.orEmpty(),
                        dependencies = dependencies,
                        generatedSourceFolders =
                        listOfNotNull(File(generated, "java").takeIf { it.exists() }),
                        generatedResourceFolders =
                        listOfNotNull(File(generated, "res").takeIf { it.exists() }),
                        classOutputs = listOf(
                            File(projectDir, "build/intermediates/javac/$variantName/classes"),
                            File(projectDir, "build/tmp/kotlin-classes/$variantName")
                        ),
                    ),
                    testArtifact = TestLintModelJavaArtifact(
                        dependencies = testDependencies,
                        classOutputs = listOf(File(projectDir, "test-classes")),
                    ),
                    androidTestArtifact = TestLintModelAndroidArtifact(
                        applicationId = mergedFlavorsAndBuildType.applicationId.orEmpty(),
                        dependencies = androidTestDependencies,
                        generatedSourceFolders = emptyList(),
                        generatedResourceFolders = emptyList(),
                        classOutputs = listOf(File(projectDir, "instrumentation-classes")),
                    ),
                    mergedManifest = null, // Injected elsewhere by the legacy Android Gradle Plugin lint runner
                    manifestMergeReport = null, // Injected elsewhere by the legacy Android Gradle Plugin lint runner
                    `package` = null, // Injected elsewhere by the legacy Android Gradle Plugin lint runner
                    minSdkVersion = mergedFlavorsAndBuildType.minSdkVersion,
                    targetSdkVersion = mergedFlavorsAndBuildType.targetSdkVersion,
                    resValues = mergedFlavorsAndBuildType.resValues,
                    manifestPlaceholders = mergedFlavorsAndBuildType.manifestPlaceholders,
                    resourceConfigurations = mergedFlavorsAndBuildType.resourceConfigurations,
                    proguardFiles = emptySet(), // not supported by the mocker.
                    consumerProguardFiles = emptySet(), // not supported by the mocker.
                    sourceProviders = sourceProviders,
                    testSourceProviders = testSourceProviders,
                    debuggable = buildType.isDebuggable,
                    shrinkable = buildType.isMinifyEnabled,
                    buildFeatures = buildFeatures,
                    libraryResolver = this,
                    partialResultsDir = null

                )
            )
        }
        updateModule { it.copy(variants = variants, neverShrinking = variants.none { it.shrinkable }) }
    }

    private fun generateVariants(): List<Pair<TestBuildType, List<TestProductFlavor>>> {
        val dimensions = flavorDimensions.takeUnless { it.isEmpty() }?.toList()
            ?: if (productFlavors.isNotEmpty()) listOf(null) else emptyList()
        val dimensionFlavors = dimensions.map { dimensionNameOrNull ->
            productFlavors.filter { it.dimension == dimensionNameOrNull }
        }
        return dimensionFlavors
            .fold<List<TestProductFlavor>, Sequence<Pair<TestBuildType, List<TestProductFlavor>>>>(
                buildTypes.asSequence().map { it to emptyList() }
            ) { acc, dimension ->
                acc.flatMap { prefix -> dimension.asSequence().map { prefix.first to prefix.second + it } }
            }
            .toList()
    }

    private fun buildVariantName(
        productFlavors: List<TestProductFlavor>,
        buildType: TestBuildType? = null
    ): String {
        return buildString {
            productFlavors.forEach { appendCamelCase(it.name) }
            if (buildType != null) {
                appendCamelCase(buildType.name)
            }
        }
    }

    private fun merge(
        defaultConfig: TestProductFlavor,
        productFlavors: List<TestProductFlavor>,
        buildType: TestBuildType
    ): TestProductFlavor {

        fun <T> combineValues(
            combine: (T?, T) -> T,
            f: TestProductFlavor.() -> T,
            b: (TestBuildType.() -> T)? = null,
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

        return TestProductFlavor(
            dimension = null,
            name = buildVariantName(productFlavors, buildType),
            applicationIdSuffix = combineValues(::combineSuffixes, { applicationIdSuffix }, { null }, reverseFlavors = false),
            versionNameSuffix = combineValues(::combineSuffixes, { versionNameSuffix }, { null }, reverseFlavors = false),
            resValues = combineValues(::combineMaps, { resValues }, { resValues }),
            manifestPlaceholders = combineValues(::combineMaps, { manifestPlaceholders }, { manifestPlaceholders }),
            applicationId = combineValues(::combineNullables, { applicationId }, { null }),
            versionCode = combineValues(::combineNullables, { versionCode }),
            versionName = combineValues(::combineNullables, { versionName }),
            minSdkVersion = combineValues(::combineNullables, { minSdkVersion }),
            targetSdkVersion = combineValues(::combineNullables, { targetSdkVersion }),
            resourceConfigurations = combineValues(::combineSets, { resourceConfigurations }),
            useSupportLibrary = combineValues(::combineNullables, { useSupportLibrary }),
            mainSourceProvider = null,
            unitTestSourceProvider = null,
            instrumentationTestSourceProvider = null,
        )
    }

    private fun createDependencies(dep: DepConf? = null): TestLintModelDependencies {

        fun <T : LintModelLibrary> Collection<T>.resolveConflicts(): Collection<T> {
            return groupBy { getMavenName(it.artifactAddress).let { it.groupId to it.artifactId } }
                .mapValues { (_, libs) ->
                    libs.maxBy { GradleVersion.tryParse(getMavenName(it.artifactAddress).version) ?: GradleVersion(0, 0) }
                }
                .values
                .mapNotNull { it }
        }

        val deps =
            listOfNotNull(
                dep?.androidLibraries?.resolveConflicts()?.map {
                    DefaultLintModelDependency(
                        artifactName = getMavenName(it.artifactAddress).let { mavenName -> "${mavenName.groupId}:${mavenName.artifactId}" },
                        artifactAddress = it.artifactAddress,
                        requestedCoordinates = null,
                        dependencies = emptyList(),
                        this
                    ) to it.provided
                },
                dep?.javaLibraries?.resolveConflicts()?.map {
                    DefaultLintModelDependency(
                        artifactName = getMavenName(it.artifactAddress).let { mavenName -> "${mavenName.groupId}:${mavenName.artifactId}" },
                        artifactAddress = it.artifactAddress,
                        requestedCoordinates = null,
                        dependencies = emptyList(),
                        this
                    ) to it.provided
                },
                dep?.moduleLibraries?.map {
                    DefaultLintModelDependency(
                        artifactName = "artifacts:${it.projectPath}",
                        artifactAddress = it.artifactAddress,
                        requestedCoordinates = null,
                        dependencies = emptyList(),
                        this
                    ) to it.provided
                }
            ).flatten()

        return TestLintModelDependencies(
            DefaultLintModelDependencyGraph(deps.map { it.first }, this),
            DefaultLintModelDependencyGraph(deps.filter { !it.second }.map { it.first }, this)
        ) { this }
    }

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

        fun updateProjectType(
            moduleType: LintModelModuleType,
            hasJavaOrJavaLibraryPlugin: Boolean,
            isLibrary: Boolean
        ): Boolean {
            this.hasJavaOrJavaLibraryPlugin = hasJavaOrJavaLibraryPlugin
            _isLibrary = isLibrary
            updateModule { it.copy(type = moduleType) }
            return true
        }

        if (when (line) {
            "apply plugin: 'com.android.library'",
            "apply plugin: 'android-library'" ->
                updateProjectType(
                        LintModelModuleType.LIBRARY,
                        hasJavaOrJavaLibraryPlugin = false,
                        isLibrary = true
                    )
            "apply plugin: 'com.android.application'",
            "apply plugin: 'android'" ->
                updateProjectType(
                        LintModelModuleType.APP,
                        hasJavaOrJavaLibraryPlugin = false,
                        isLibrary = false
                    )
            "apply plugin: 'com.android.feature'" ->
                updateProjectType(
                        LintModelModuleType.FEATURE,
                        hasJavaOrJavaLibraryPlugin = false,
                        isLibrary = false
                    )
            "apply plugin: 'com.android.instantapp'" ->
                updateProjectType(
                        LintModelModuleType.INSTANT_APP,
                        hasJavaOrJavaLibraryPlugin = false,
                        isLibrary = false
                    )
            "apply plugin: 'java'" ->
                updateProjectType(
                        LintModelModuleType.JAVA_LIBRARY,
                        hasJavaOrJavaLibraryPlugin = true,
                        isLibrary = false
                    )
            "apply plugin: 'java-library'" ->
                updateProjectType(
                        LintModelModuleType.LIBRARY,
                        hasJavaOrJavaLibraryPlugin = true,
                        isLibrary = true
                    )
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
                updateFlavorFromContext(context) { it.copy(minSdkVersion = createAndroidVersion(key)) }
            }
            line.startsWith("targetSdkVersion ") -> {
                updateFlavorFromContext(context) { it.copy(targetSdkVersion = createAndroidVersion(key)) }
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
                updateModule { it.copy(resourcePrefix = getUnquotedValue(key)) }
            }
            key.startsWith("group=") -> {
                updateModuleMavenName { it.copy(groupId = getUnquotedValue(key)) }
            }
            key.startsWith("android.buildToolsVersion ") -> {
                // Not used.
            }
            line.startsWith("minifyEnabled ") && key.startsWith("android.buildTypes.") -> {
                updateBuildTypeFromContext(context) { it.copy(isMinifyEnabled = SdkConstants.VALUE_TRUE == getUnquotedValue(line)) }
            }
            key.startsWith("android.compileSdkVersion ") -> {
                val value = getUnquotedValue(key)
                updateModule { it.copy(compileTarget = if (Character.isDigit(value[0])) "android-$value" else value) }
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
                    updateVectorDrawableOptionsUseSupportLibrary { true }
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
                // Not used.
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
                flavorDimensions = (
                    flavorDimensions.toSet() +
                        Splitter.on(',').omitEmptyStrings()
                            .trimResults().split(value)
                            .map { getUnquotedValue(it) }
                    ).toList()
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

                fun updateResValues(resValues: MutableMap<String, TestLintModelResourceField>) {
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
                    val field = TestLintModelResourceField(
                        type = type!!,
                        name = fieldName!!,
                        value = value!!
                    )
                    resValues[fieldName] = field
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
                    updateNamespacing(LintModelNamespacingMode.REQUIRED)
                }
            }
            key.startsWith("groupId ") -> {
                updateModuleMavenName { it.copy(groupId = getUnquotedValue(key)) }
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
                    "viewBinding" -> {
                        updateBuildFeatures { it.copy(viewBinding = toBoolean(arg)) }
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
        val severityValue: LintModelSeverity = when (severity) {
            Severity.FATAL -> LintModelSeverity.FATAL
            Severity.ERROR -> LintModelSeverity.ERROR
            Severity.WARNING -> LintModelSeverity.WARNING
            Severity.INFORMATIONAL -> LintModelSeverity.INFORMATIONAL
            Severity.IGNORE -> LintModelSeverity.IGNORE
            else -> LintModelSeverity.DEFAULT_ENABLED
        }
        flags.severityOverrides[id] = severityValue
        severityOverrides[id] = severityValue
        updateLintOptions(null, null, severityOverrides, null, null)
    }

    private fun updateLintOptions(
        baseline: File?,
        lintConfig: File?,
        severities: Map<String, LintModelSeverity>?,
        tests: Boolean?,
        dependencies: Boolean?
    ) {
        updateModule {
            it.copy(
                lintOptions = it.lintOptions.copy(
                    baselineFile = baseline ?: it.lintOptions.baselineFile,
                    lintConfig = lintConfig ?: it.lintOptions.lintConfig,
                    severityOverrides = severities ?: severityOverrides,
                    checkTestSources = tests ?: it.lintOptions.checkTestSources,
                    checkDependencies = dependencies
                        ?: it.lintOptions.checkDependencies, // TODO: Allow these to be customized by model mocker
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

    private fun file(gradle: String, @Suppress("SameParameterValue") reportError: Boolean): File {
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

    private fun stripQuotes(string: String, @Suppress("SameParameterValue") reportError: Boolean): String {
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

    private fun updateModule(f: (TestLintModelModule) -> TestLintModelModule) {
        moduleModel = f(moduleModel)
    }

    private fun updateBuildFeatures(f: (TestLintModelBuildFeatures) -> TestLintModelBuildFeatures) {
        buildFeatures = f(buildFeatures)
    }

    private fun updateModuleMavenName(f: (TestLintModelMavenName) -> TestLintModelMavenName) {
        updateModule { it.copy(mavenName = f(it.mavenName ?: TestLintModelMavenName(artifactId = moduleModel.modulePath))) }
    }

    private fun updateFlavorFromContext(
        context: String,
        defaultToDefault: Boolean = false,
        f: (TestProductFlavor) -> TestProductFlavor
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

    private fun updateVectorDrawableOptionsUseSupportLibrary(f: (Boolean?) -> Boolean?) {
        updateDefaultConfig { it.copy(useSupportLibrary = f(it.useSupportLibrary)) }
    }

    private fun updateBuildType(
        name: String,
        create: Boolean,
        f: (TestBuildType) -> TestBuildType
    ) {
        val index = buildTypes.indexOfFirst { it.name == name }
        if (index >= 0) {
            val list = buildTypes.toMutableList()
            list[index] = f(list[index])
            buildTypes = list
        }
        if (index < 0) {
            if (create) {
                val isDebuggable = name.startsWith("debug")
                buildTypes = buildTypes + f(
                    TestBuildType(
                        name = name,
                        isDebuggable = isDebuggable,
                        mainSourceProvider = createSourceProvider(projectDir, name, isDebugOnly = isDebuggable),
                        unitTestSourceProvider = null,
                        instrumentationTestSourceProvider = null
                    )
                )
            } else {
                error("Couldn't find flavor $name")
            }
        }
    }

    private fun updateBuildTypeFromContext(
        context: String,
        f: (TestBuildType) -> TestBuildType
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

    private fun updateSourceCompatibility(level: String) {
        updateModule { it.copy(javaSourceLevel = level) }
    }

    private fun updateNamespacing(namespacingMode: LintModelNamespacingMode) {
        updateBuildFeatures {
            it.copy(namespacingMode = namespacingMode)
        }
    }

    private fun updateModelVersion(modelVersion: String) {
        updateModule {
            it.copy(gradleVersion = GradleVersion.parse(modelVersion))
        }
    }

    private fun block(
        name: String,
        @Language("Groovy") blockBody: String,
        context: String
    ) {
        if ("android.productFlavors" == context && productFlavors
            .none { flavor -> flavor.name == name }
        ) {
            // Defining new product flavors
            updateProductFlavor(name, true) { it }
        }
        if ("android.buildTypes" == context && buildTypes
            .none { buildType -> buildType.name == name }
        ) {
            // Defining new build types
            updateBuildType(name, true) { it }
        }
        scan(blockBody, if (context.isEmpty()) name else "$context.$name")
    }

    private fun updateProductFlavor(
        name: String,
        create: Boolean,
        f: (TestProductFlavor) -> TestProductFlavor
    ) {
        val index = productFlavors.indexOfFirst { it.name == name }
        if (index >= 0) {
            val list = productFlavors.toMutableList()
            list[index] = f(list[index])
            productFlavors = list
        }
        if (index < 0 && create) {
            productFlavors = productFlavors + f(
                TestProductFlavor(
                    name = name,
                    mainSourceProvider = createSourceProvider(projectDir, name),
                    unitTestSourceProvider = createSourceProvider(
                        projectDir,
                        "test".appendCapitalized(name),
                        isUnitTest = true
                    ),
                    instrumentationTestSourceProvider = createSourceProvider(
                        projectDir,
                        "androidTest".appendCapitalized(name),
                        isInstrumentationTest = true
                    )
                )
            )
        }
    }

    private fun updateDefaultConfig(f: (TestProductFlavor) -> TestProductFlavor) {
        defaultConfig = f(defaultConfig)
    }

    private fun createAndroidVersion(value: String): AndroidVersion {
        val s = value.substring(value.indexOf(' ') + 1)
        if (s.startsWith("'")) {
            val codeName = getUnquotedValue(s)
            val sdkVersion = SdkVersionInfo.getVersion(codeName, null)
            if (sdkVersion != null) return sdkVersion
        } else {
            return AndroidVersion(s.toInt(), null)
        }
        return AndroidVersion(0, "")
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
                .substringBefore("@") // @jar/aar is not part of the version and should not be applied to other dependencies.
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
                is TestLintModelAndroidLibrary -> artifactDeps.androidLibraries.add(library)
                is TestLintModelJavaLibrary -> artifactDeps.javaLibraries.add(library)
                is TestLintModelModuleLibrary -> artifactDeps.moduleLibraries.add(library)
            }
        }
    }

    private fun createAndroidLibrary(coordinateString: String, isProvided: Boolean): TestLintModelAndroidLibrary {
        return createAndroidLibrary(coordinateString, null, isProvided, null)
    }

    private fun createAndroidLibrary(
        coordinateString: String,
        promotedTo: String?,
        isProvided: Boolean,
        jar: File?
    ): TestLintModelAndroidLibrary {
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
            jar = dir.resolve(File("jars/" + SdkConstants.FN_CLASSES_JAR))
        }
        if (!jar.exists()) {
            createEmptyJar(jar)
        }
        return deduplicateLibrary(
            TestLintModelAndroidLibrary(
                artifactAddress = coordinateString.substringBefore("@"),
                lintJar = dir.resolve(File(libraryLintJars.getOrDefault(coordinateString, "lint.jar"))),
                jarFiles = listOf(jar),
                resolvedCoordinates = getMavenName(coordinateString),
                folder = dir,
                manifest = dir.resolve(File(SdkConstants.FN_ANDROID_MANIFEST_XML)),
                resFolder = dir.resolve(File("res")),
                assetsFolder = dir.resolve(File("assets")),
                publicResources =
                dir.resolve(File(libraryPublicResourcesFiles.getOrDefault(coordinateString, "public.txt"))),
                symbolFile = dir.resolve(File(librarySymbolFiles.getOrDefault(coordinateString, "R.txt"))),
                proguardRules = dir.resolve(File("proguard.pro")),
                externalAnnotations = dir.resolve(File(SdkConstants.FN_ANNOTATIONS_ZIP)),
                provided = isProvided
            )
        )
    }

    private fun createJavaLibrary(coordinateString: String, isProvided: Boolean): TestLintModelJavaLibrary {
        return createJavaLibrary(coordinateString, null, isProvided, null)
    }

    private fun createJavaLibrary(
        coordinateString: String,
        promotedTo: String?,
        isProvided: Boolean,
        jar: File?
    ): TestLintModelJavaLibrary {
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
            TestLintModelJavaLibrary(
                provided = isProvided,
                artifactAddress = coordinateString.substringBefore("@"),
                lintJar = null,
                jarFiles = listOf(jar),
                resolvedCoordinates = getMavenName(coordinateString)
            )
        )
    }

    private fun createModuleLibrary(name: String): TestLintModelModuleLibrary {
        return deduplicateLibrary(
            TestLintModelModuleLibrary(
                provided = false,
                artifactAddress = "artifacts:$name",
                lintJar = null,
                projectPath = name
            )
        )
    }

    private fun deduplicateLibrary(library: TestLintModelAndroidLibrary): TestLintModelAndroidLibrary {
        return androidLibraryInstances.computeIfAbsent(library) { it: TestLintModelAndroidLibrary? ->
            library.also {
                registerInLibraryTable(it)
            }
        }
    }

    private fun deduplicateLibrary(library: TestLintModelJavaLibrary): TestLintModelJavaLibrary {
        return javaLibraryInstances.computeIfAbsent(library) { it: TestLintModelJavaLibrary? ->
            library.also {
                registerInLibraryTable(it)
            }
        }
    }

    private fun deduplicateLibrary(library: TestLintModelModuleLibrary): TestLintModelModuleLibrary {
        return moduleLibraryInstances.computeIfAbsent(library) { it: TestLintModelModuleLibrary? ->
            library.also {
                registerInLibraryTable(it)
            }
        }
    }

    private fun registerInLibraryTable(it: LintModelLibrary) {
        // Prefer the first instance as if it is different it may have come from locally pre-configured libraries.
        libraryTable.getOrPut(it.artifactAddress) { it }
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
     * Given a dependency graph, returns a populated [Dependencies]
     * object. You can generate Gradle dependency graphs by running for
     * example:
     * <pre>
     * $ ./gradlew :app:dependencies
     * </pre> *
     *
     * Sample graph:
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
     * </pre> *
     *
     * @param graph the graph
     * @return the corresponding dependencies
     */
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

    /** Dependency graph node. */
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

        fun createLibrary(): Collection<LintModelLibrary> {
            return if (isJavaLibrary) {
                createJavaLibrary()
            } else {
                createAndroidLibrary()
            }
        }

        fun createAndroidLibrary(): Collection<LintModelLibrary> {
            val result: MutableCollection<LintModelLibrary> = LinkedHashSet()
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

        private fun createJavaLibrary(): Collection<LintModelLibrary> {
            val result: MutableCollection<LintModelLibrary> = LinkedHashSet()
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

        private fun createSourceProvider(
            root: File,
            name: String,
            isUnitTest: Boolean = false,
            isInstrumentationTest: Boolean = false,
            isDebugOnly: Boolean = false
        ): TestLintModelSourceProvider {
            return TestLintModelSourceProvider(
                name = name,
                _isUnitTest = isUnitTest,
                _isInstrumentationTest = isInstrumentationTest,
                _isDebugOnly = isDebugOnly,
                manifestFile = File(root, "src/" + name + "/" + SdkConstants.ANDROID_MANIFEST_XML),
                javaDirectories = listOf(
                    File(root, "src/$name/java"),
                    File(root, "src/$name/kotlin")
                ),
                resDirectories = listOf(
                    File(root, "src/$name/res")
                ),
                assetsDirectories = listOf(
                    File(root, "src/$name/assets")
                )
            )
        }

        /**
         * Returns whether a library declaration is a plain Java library
         * instead of an Android library. There is no way to tell from
         * the Gradle description; it involves looking at the actual
         * Maven artifacts. For mocking purposes we have a hardcoded
         * list.
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

    override fun getAllLibraries(): Collection<LintModelLibrary> {
        // Join collections instead of using _libraryTable just to maintain the order of libraries in the resolver
        // as expected by tests (though any order is correct).
        return androidLibraryInstances.values + javaLibraryInstances.values + moduleLibraryInstances.values
    }

    override fun getLibrary(artifactAddress: String): LintModelLibrary? {
        return libraryTable[artifactAddress]
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

private data class TestLintModelModule(
    override val dir: File = File(""),
    override val modulePath: String = "",
    override val type: LintModelModuleType = LintModelModuleType.APP,
    override val mavenName: TestLintModelMavenName? = null,
    override val gradleVersion: GradleVersion? = null,
    override val buildFolder: File = File(""),
    override val lintOptions: TestLintModelLintOptions = TestLintModelLintOptions(),
    override val lintRuleJars: List<File> = emptyList(),
    override val resourcePrefix: String? = null,
    override val dynamicFeatures: Collection<String> = emptySet(),
    override val bootClassPath: List<File> = emptyList(),
    override val javaSourceLevel: String = "",
    override val compileTarget: String = "",
    override val variants: List<LintModelVariant> = emptyList(),
    val neverShrinking: Boolean = false
) : LintModelModule {
    override val loader: LintModelModuleLoader get() = TestLintModelModuleLoader
    override fun neverShrinking(): Boolean = neverShrinking
}

private object TestLintModelModuleLoader : LintModelModuleLoader

private data class TestLintModelLintOptions(
    override val disable: Set<String> = emptySet(),
    override val enable: Set<String> = emptySet(),
    override val check: Set<String>? = null,
    override val abortOnError: Boolean = true,
    override val absolutePaths: Boolean = true,
    override val noLines: Boolean = false,
    override val quiet: Boolean = false,
    override val checkAllWarnings: Boolean = false,
    override val ignoreWarnings: Boolean = false,
    override val warningsAsErrors: Boolean = false,
    override val checkTestSources: Boolean = false,
    override val ignoreTestSources: Boolean = false,
    override val checkGeneratedSources: Boolean = false,
    override val explainIssues: Boolean = true,
    override val showAll: Boolean = false,
    override val lintConfig: File? = null,
    override val textReport: Boolean = false,
    override val textOutput: File? = null,
    override val htmlReport: Boolean = true,
    override val htmlOutput: File? = null,
    override val xmlReport: Boolean = true,
    override val xmlOutput: File? = null,
    override val sarifReport: Boolean = false,
    override val sarifOutput: File? = null,
    override val checkReleaseBuilds: Boolean = true,
    override val checkDependencies: Boolean = false,
    override val baselineFile: File? = null,
    override val severityOverrides: Map<String, LintModelSeverity>? = null
) : LintModelLintOptions

private data class TestLintModelVariant(
    val _module: () -> LintModelModule,
    override val name: String,
    override val useSupportLibraryVectorDrawables: Boolean,
    override val mainArtifact: LintModelAndroidArtifact,
    override val testArtifact: LintModelJavaArtifact?,
    override val androidTestArtifact: LintModelAndroidArtifact?,
    override val mergedManifest: File?,
    override val manifestMergeReport: File?,
    override val `package`: String?,
    override val minSdkVersion: AndroidVersion?,
    override val targetSdkVersion: AndroidVersion?,
    override val resValues: Map<String, LintModelResourceField>,
    override val manifestPlaceholders: Map<String, String>,
    override val resourceConfigurations: Collection<String>,
    override val proguardFiles: Collection<File>,
    override val consumerProguardFiles: Collection<File>,
    override val sourceProviders: List<LintModelSourceProvider>,
    override val testSourceProviders: List<LintModelSourceProvider>,
    override val debuggable: Boolean,
    override val shrinkable: Boolean,
    override val buildFeatures: LintModelBuildFeatures,
    override val libraryResolver: LintModelLibraryResolver,
    override val partialResultsDir: File?

) : LintModelVariant {
    override val module: LintModelModule get() = _module()
}

private val emptyLibraryResolver = DefaultLintModelLibraryResolver(emptyMap())
private val emptyLibraryResolverProvider: () -> LintModelLibraryResolver = { emptyLibraryResolver }

private data class TestLintModelAndroidArtifact(
    override val dependencies: LintModelDependencies = TestLintModelDependencies(libraryResolver = emptyLibraryResolverProvider),
    override val classOutputs: List<File>,
    override val applicationId: String,
    override val generatedResourceFolders: Collection<File>,
    override val generatedSourceFolders: Collection<File>
) : LintModelAndroidArtifact

private data class TestLintModelJavaArtifact(
    override val dependencies: LintModelDependencies = TestLintModelDependencies(libraryResolver = emptyLibraryResolverProvider),
    override val classOutputs: List<File>
) : LintModelJavaArtifact

private data class TestLintModelDependencies(
    override val compileDependencies: LintModelDependencyGraph = EmptyLintModelDependencyGraph,
    override val packageDependencies: LintModelDependencyGraph = EmptyLintModelDependencyGraph,
    val libraryResolver: () -> LintModelLibraryResolver,
) : LintModelDependencies {
    override fun getLibraryResolver(): LintModelLibraryResolver = libraryResolver()
}

object EmptyLintModelDependencyGraph : LintModelDependencyGraph {
    override val roots: List<LintModelDependency> = emptyList()
    override fun findLibrary(mavenName: String, direct: Boolean): LintModelLibrary? = null
    override fun getAllGraphItems(): List<LintModelDependency> = emptyList()
    override fun getAllLibraries(): List<LintModelLibrary> = emptyList()
}

private data class TestLintModelMavenName(
    override val groupId: String = "",
    override val artifactId: String = "",
    override val version: String = ""
) : LintModelMavenName {
    override fun toString(): String = "$groupId:$artifactId:$version"
}

private data class TestLintModelBuildFeatures(
    override val viewBinding: Boolean = false,
    override val coreLibraryDesugaringEnabled: Boolean = false,
    override val namespacingMode: LintModelNamespacingMode = LintModelNamespacingMode.DISABLED
) : LintModelBuildFeatures

private data class TestProductFlavor(
    val name: String,
    val dimension: String? = null,
    val applicationId: String? = null,
    val minSdkVersion: AndroidVersion? = null,
    val targetSdkVersion: AndroidVersion? = null,
    val versionCode: Int? = null,
    val versionName: String? = null,
    val versionNameSuffix: String? = null,
    val applicationIdSuffix: String? = null,
    val resourceConfigurations: Collection<String> = emptySet(),
    val manifestPlaceholders: Map<String, String> = emptyMap(),
    val resValues: Map<String, TestLintModelResourceField> = emptyMap(),
    val useSupportLibrary: Boolean? = null,
    val mainSourceProvider: TestLintModelSourceProvider?,
    val unitTestSourceProvider: TestLintModelSourceProvider?,
    val instrumentationTestSourceProvider: TestLintModelSourceProvider?,
)

private data class TestBuildType(
    val name: String,
    val isDebuggable: Boolean,
    val isMinifyEnabled: Boolean = false,
    val manifestPlaceholders: Map<String, String> = emptyMap(),
    val resValues: Map<String, TestLintModelResourceField> = emptyMap(),
    val mainSourceProvider: TestLintModelSourceProvider,
    val unitTestSourceProvider: TestLintModelSourceProvider?,
    val instrumentationTestSourceProvider: TestLintModelSourceProvider?,
)

private data class TestLintModelResourceField(
    override val type: String,
    override val name: String,
    override val value: String
) : LintModelResourceField

private data class TestLintModelSourceProvider(
    val name: String,
    val _isUnitTest: Boolean,
    val _isInstrumentationTest: Boolean,
    val _isDebugOnly: Boolean,
    override val manifestFile: File,
    override val javaDirectories: Collection<File>,
    override val resDirectories: Collection<File>,
    override val assetsDirectories: Collection<File>
) : LintModelSourceProvider {
    override fun isUnitTest(): Boolean = _isUnitTest

    override fun isInstrumentationTest(): Boolean = _isInstrumentationTest

    override fun isDebugOnly(): Boolean = _isDebugOnly
}

private data class TestLintModelAndroidLibrary(
    override val provided: Boolean,
    override val artifactAddress: String,
    override val lintJar: File?,
    override val jarFiles: List<File>,
    override val resolvedCoordinates: LintModelMavenName,
    override val folder: File,
    override val manifest: File,
    override val resFolder: File,
    override val assetsFolder: File,
    override val publicResources: File,
    override val symbolFile: File,
    override val externalAnnotations: File,
    override val proguardRules: File
) : LintModelAndroidLibrary

private data class TestLintModelJavaLibrary(
    override val provided: Boolean,
    override val artifactAddress: String,
    override val lintJar: File?,
    override val jarFiles: List<File>,
    override val resolvedCoordinates: LintModelMavenName
) : LintModelJavaLibrary

private data class TestLintModelModuleLibrary(
    override val provided: Boolean,
    override val artifactAddress: String,
    override val lintJar: File?,
    override val projectPath: String
) : LintModelModuleLibrary

private fun getMavenName(artifactAddress: String): LintModelMavenName {
    fun Int.nextDelimiterIndex(vararg delimiters: Char): Int {
        return delimiters.asSequence()
            .map {
                val index = artifactAddress.indexOf(it, startIndex = this + 1)
                if (index == -1) artifactAddress.length else index
            }.min() ?: artifactAddress.length
    }

    val lastDelimiterIndex = 0
        .nextDelimiterIndex(':')
        .nextDelimiterIndex(':')
        .nextDelimiterIndex(':', '@')

    // Currently [LintModelMavenName] supports group:name:version format only.
    return LintModelMavenName.parse(artifactAddress.substring(0, lastDelimiterIndex))
        ?: error("Cannot parse '$artifactAddress'")
}
