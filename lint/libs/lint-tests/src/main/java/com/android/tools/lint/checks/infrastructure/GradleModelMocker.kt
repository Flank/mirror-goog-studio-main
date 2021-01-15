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
import com.android.SdkConstants
import com.android.build.FilterData
import com.android.builder.model.*
import com.android.builder.model.level2.GraphItem
import com.android.builder.model.level2.Library
import com.android.ide.common.gradle.model.IdeAaptOptions
import com.android.ide.common.gradle.model.IdeAndroidArtifact
import com.android.ide.common.gradle.model.IdeAndroidArtifactOutput
import com.android.ide.common.gradle.model.IdeAndroidLibrary
import com.android.ide.common.gradle.model.IdeAndroidProject
import com.android.ide.common.gradle.model.IdeApiVersion
import com.android.ide.common.gradle.model.IdeBuildType
import com.android.ide.common.gradle.model.IdeBuildTypeContainer
import com.android.ide.common.gradle.model.IdeClassField
import com.android.ide.common.gradle.model.IdeDependencies
import com.android.ide.common.gradle.model.IdeJavaArtifact
import com.android.ide.common.gradle.model.IdeJavaCompileOptions
import com.android.ide.common.gradle.model.IdeJavaLibrary
import com.android.ide.common.gradle.model.IdeLibrary
import com.android.ide.common.gradle.model.IdeLintOptions
import com.android.ide.common.gradle.model.IdeModuleLibrary
import com.android.ide.common.gradle.model.IdeProductFlavor
import com.android.ide.common.gradle.model.IdeProductFlavorContainer
import com.android.ide.common.gradle.model.IdeSourceProvider
import com.android.ide.common.gradle.model.IdeSourceProviderContainer
import com.android.ide.common.gradle.model.IdeVariant
import com.android.ide.common.gradle.model.IdeVectorDrawablesOptions
import com.android.ide.common.gradle.model.IdeViewBindingOptions
import com.android.ide.common.gradle.model.impl.IdeAndroidLibraryCore
import com.android.ide.common.gradle.model.impl.IdeAndroidLibraryImpl
import com.android.ide.common.gradle.model.impl.IdeDependenciesImpl
import com.android.ide.common.gradle.model.impl.IdeJavaLibraryCore
import com.android.ide.common.gradle.model.impl.IdeJavaLibraryImpl
import com.android.ide.common.gradle.model.impl.IdeLintOptionsImpl
import com.android.ide.common.gradle.model.impl.IdeModuleLibraryCore
import com.android.ide.common.gradle.model.impl.IdeModuleLibraryImpl
import com.android.ide.common.repository.GradleCoordinate
import com.android.ide.common.repository.GradleVersion
import com.android.projectmodel.ARTIFACT_NAME_ANDROID_TEST
import com.android.projectmodel.ARTIFACT_NAME_MAIN
import com.android.projectmodel.ARTIFACT_NAME_UNIT_TEST
import com.android.sdklib.SdkVersionInfo
import com.android.tools.lint.LintCliFlags
import com.android.tools.lint.detector.api.Severity
import com.android.utils.FileUtils
import com.android.utils.ILogger
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Charsets
import com.google.common.base.Splitter
import com.google.common.collect.*
import com.google.common.hash.Hashing
import com.google.common.io.ByteStreams
import junit.framework.TestCase
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.Contract
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock
import java.io.*
import java.util.*
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
class GradleModelMocker(@field:Language("Groovy") @param:Language("Groovy") private val gradle: String) {
    private lateinit var _project: IdeAndroidProject
    private lateinit var _variant: IdeVariant
    private val _variants: MutableList<IdeVariant> = ArrayList()
    private val lintRuleJars: MutableList<File> = ArrayList()
    private val libraryLintJars: MutableMap<String, String> = HashMap()
    private val libraryPublicResourcesFiles: MutableMap<String, String> = HashMap()
    private val librarySymbolFiles: MutableMap<String, String> = HashMap()
    private val androidLibraryInstances: MutableMap<IdeAndroidLibrary, IdeAndroidLibrary> = HashMap()
    private val javaLibraryInstances: MutableMap<IdeJavaLibrary, IdeJavaLibrary> = HashMap()
    private val moduleLibraryInstances: MutableMap<IdeModuleLibrary, IdeModuleLibrary> = HashMap()
    private val buildTypes: MutableList<IdeBuildType> = mutableListOf()
    private val androidLibraries: MutableList<IdeAndroidLibrary> = mutableListOf()
    private val javaLibraries: MutableList<IdeJavaLibrary> = mutableListOf()
    private val moduleLibraries: MutableList<IdeModuleLibrary> = mutableListOf()
    private val testAndroidLibraries: MutableList<IdeAndroidLibrary> = mutableListOf()
    private val testJavaLibraries: MutableList<IdeJavaLibrary> = mutableListOf()
    private val testModuleLibraries: MutableList<IdeModuleLibrary> = mutableListOf()
    private val androidTestAndroidLibraries: MutableList<IdeAndroidLibrary> = mutableListOf()
    private val androidTestJavaLibraries: MutableList<IdeJavaLibrary> = mutableListOf()
    private val androidTestModuleLibraries: MutableList<IdeModuleLibrary> = mutableListOf()
    private var mergedFlavor: IdeProductFlavor? = null
    private var defaultFlavor: IdeProductFlavor? = null
    private var lintOptions: IdeLintOptions? = null
    private val severityOverrides = HashMap<String, Int>()
    private val flags = LintCliFlags()
    var primary = true
    var projectDir = File("")
        private set
    private val productFlavors: MutableList<IdeProductFlavor?> = mutableListOf()
    private val splits: Multimap<String, String> = ArrayListMultimap.create()
    private var logger: ILogger? = null
    private var initialized = false
    private val ext: MutableMap<String, String> = HashMap()
    private var modelVersion = GradleVersion.parse("2.2.2")
    private val graphs: MutableMap<String, Dep> = Maps.newHashMap()
    private var useBuildCache = false
    private var vectorDrawablesOptions: IdeVectorDrawablesOptions? = null
    private var aaptOptions: IdeAaptOptions? = null
    private var allowUnrecognizedConstructs = false
    private var fullDependencies = false
    private lateinit var compileOptions: IdeJavaCompileOptions
    private var javaPlugin = false
    private var javaLibraryPlugin = false
    fun withLogger(logger: ILogger?): GradleModelMocker {
        this.logger = logger
        return this
    }

    fun withModelVersion(modelVersion: String): GradleModelMocker {
        this.modelVersion = GradleVersion.parse(modelVersion)
        return this
    }

    fun withProjectDir(projectDir: File): GradleModelMocker {
        this.projectDir = projectDir
        return this
    }

    fun withDependencyGraph(graph: String): GradleModelMocker {
        parseDependencyGraph(graph, graphs)
        return this
    }

    fun withLintRuleJar(lintRuleJarPath: String): GradleModelMocker {
        lintRuleJars.add(File(lintRuleJarPath))
        return this
    }

    fun withLibraryLintJar(
        library: String, lintJarPath: String
    ): GradleModelMocker {
        libraryLintJars[library] = lintJarPath
        return this
    }

    fun withLibraryPublicResourcesFile(
        library: String, publicResourcesPath: String
    ): GradleModelMocker {
        libraryPublicResourcesFiles[library] = publicResourcesPath
        return this
    }

    fun withLibrarySymbolFile(
        library: String, symbolFilePath: String
    ): GradleModelMocker {
        librarySymbolFiles[library] = symbolFilePath
        return this
    }

    fun allowUnrecognizedConstructs(): GradleModelMocker {
        allowUnrecognizedConstructs = true
        return this
    }

    fun withBuildCache(useBuildCache: Boolean): GradleModelMocker {
        this.useBuildCache = useBuildCache
        return this
    }

    /**
     * If true, model a full/deep dependency graph in [ ]; the default is flat. (This is normally
     * controlled by sync/model builder flag [ ][AndroidProject.PROPERTY_BUILD_MODEL_FEATURE_FULL_DEPENDENCIES].)
     */
    fun withFullDependencies(fullDependencies: Boolean): GradleModelMocker {
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

    /** Whether the Gradle file applied the java plugin  */
    fun hasJavaPlugin(): Boolean {
        return javaPlugin
    }

    /** Whether the Gradle file applied the java-library plugin  */
    fun hasJavaLibraryPlugin(): Boolean {
        return javaLibraryPlugin
    }

    val isLibrary: Boolean
        get() = (project.projectType == AndroidProjectTypes.PROJECT_TYPE_LIBRARY
                || project.projectType == PROJECT_TYPE_JAVA_LIBRARY)

    /** Whether the Gradle file applied the java-library plugin  */
    fun hasAndroidLibraryPlugin(): Boolean {
        return javaLibraryPlugin
    }

    val project: IdeAndroidProject get() {
        ensureInitialized()
        return _project
    }

    val variant: IdeVariant get() {
        ensureInitialized()
        return _variant
    }

    val variants: Collection<IdeVariant> get() {
        ensureInitialized()
        return _variants
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

    private fun initialize() {
        _project = Mockito.mock(IdeAndroidProject::class.java)
        val clientProperties: MutableMap<String, Any> = HashMap()
        Mockito.`when`(project.getClientProperty(ArgumentMatchers.anyString()))
            .thenAnswer { invocation: InvocationOnMock ->
                val key = invocation.getArgument<String>(0)
                clientProperties[key]
            }
        Mockito.`when`(project.putClientProperty(ArgumentMatchers.anyString(), ArgumentMatchers.any()))
            .thenAnswer { invocation: InvocationOnMock ->
                val key = invocation.getArgument<String>(0)
                val value = invocation.getArgument<Any>(1)
                clientProperties[key] = value
                null
            }
        Mockito.`when`(project.modelVersion).thenReturn(modelVersion.toString())
        val apiVersion = if (modelVersion.major >= 2) 3 else 2
        Mockito.`when`(project.apiVersion).thenReturn(apiVersion)
        Mockito.`when`(project.flavorDimensions).thenReturn(mutableListOf())
        Mockito.`when`(project.name).thenReturn("test_project")
        Mockito.`when`(project.compileTarget).thenReturn("android-" + SdkVersionInfo.HIGHEST_KNOWN_API)
        _variant = Mockito.mock(IdeVariant::class.java)
        lintOptions = IdeLintOptionsImpl()
        Mockito.`when`(project.lintOptions).thenAnswer { invocation: InvocationOnMock? -> lintOptions }
        Mockito.`when`(project.lintRuleJars).thenAnswer { invocation: InvocationOnMock? -> lintRuleJars }
        compileOptions = Mockito.mock(IdeJavaCompileOptions::class.java)
        Mockito.`when`(compileOptions.sourceCompatibility).thenReturn("1.7")
        Mockito.`when`(compileOptions.targetCompatibility).thenReturn("1.7")
        Mockito.`when`(compileOptions.encoding).thenReturn("UTF-8")
        Mockito.`when`(project.javaCompileOptions).thenReturn(compileOptions)

        // built-in build-types
        getBuildType("debug", true)
        getBuildType("release", true)
        defaultFlavor = getProductFlavor("defaultConfig", true)
        Mockito.`when`(defaultFlavor!!.versionCode)
            .thenReturn(null) // don't default to Integer.valueOf(0) !
        var dependencies = Mockito.mock(IdeDependencies::class.java)
        val testDependencies = Mockito.mock(IdeDependencies::class.java)
        val androidTestDependencies = Mockito.mock(IdeDependencies::class.java)
        Mockito.`when`(dependencies.androidLibraries).thenReturn(androidLibraries)
        addLocalLibs(File(projectDir, "libs"))
        Mockito.`when`(testDependencies.androidLibraries).thenReturn(testAndroidLibraries)
        Mockito.`when`(androidTestDependencies.androidLibraries).thenReturn(androidTestAndroidLibraries)
        Mockito.`when`(dependencies.javaLibraries).thenReturn(javaLibraries)
        Mockito.`when`(testDependencies.javaLibraries).thenReturn(testJavaLibraries)
        Mockito.`when`(androidTestDependencies.javaLibraries).thenReturn(androidTestJavaLibraries)
        Mockito.`when`(dependencies.moduleDependencies).thenReturn(moduleLibraries)
        Mockito.`when`(testDependencies.moduleDependencies).thenReturn(testModuleLibraries)
        Mockito.`when`(androidTestDependencies.moduleDependencies)
            .thenReturn(androidTestModuleLibraries)
        mergedFlavor = getProductFlavor("mergedFlavor", true)
        productFlavors.remove(mergedFlavor) // create mock but don't store as a separate flavor
        Mockito.`when`(variant.mergedFlavor).thenReturn(mergedFlavor)
        vectorDrawableOptions // ensure initialized
        getAaptOptions() // ensure initialized
        scan(gradle, "")
        val containers: MutableList<IdeBuildTypeContainer> = mutableListOf()
        for (buildType in buildTypes) {
            val container = Mockito.mock(
                IdeBuildTypeContainer::class.java
            )
            Mockito.`when`(container.buildType).thenReturn(buildType)
            containers.add(container)
            val provider = createSourceProvider(projectDir, buildType.name)
            Mockito.`when`(container.sourceProvider).thenReturn(provider)
        }
        Mockito.`when`(project.buildTypes).thenReturn(containers)
        val defaultContainer = Mockito.mock(
            IdeProductFlavorContainer::class.java
        )
        Mockito.`when`(defaultContainer.productFlavor).thenReturn(defaultFlavor)
        Mockito.`when`(defaultContainer.toString()).thenReturn("defaultConfig")
        Mockito.`when`(project.defaultConfig).thenReturn(defaultContainer)
        val mainProvider = createSourceProvider(projectDir, "main")
        Mockito.`when`(defaultContainer.sourceProvider).thenReturn(mainProvider)
        val androidTestProvider = Mockito.mock(
            IdeSourceProviderContainer::class.java
        )
        Mockito.`when`(androidTestProvider.artifactName)
            .thenReturn(AndroidProject.ARTIFACT_ANDROID_TEST)
        val androidSourceProvider = createSourceProvider(projectDir, "androidTest")
        Mockito.`when`(androidTestProvider.sourceProvider).thenReturn(androidSourceProvider)
        val unitTestProvider = Mockito.mock(
            IdeSourceProviderContainer::class.java
        )
        Mockito.`when`(unitTestProvider.artifactName).thenReturn(AndroidProject.ARTIFACT_UNIT_TEST)
        val unitSourceProvider = createSourceProvider(projectDir, "test")
        Mockito.`when`(unitTestProvider.sourceProvider).thenReturn(unitSourceProvider)
        val extraProviders: List<IdeSourceProviderContainer> = Lists.newArrayList(androidTestProvider, unitTestProvider)
        Mockito.`when`(defaultContainer.extraSourceProviders).thenReturn(extraProviders)
        val flavorContainers: MutableList<IdeProductFlavorContainer> = mutableListOf()
        flavorContainers.add(defaultContainer)
        for (flavor in productFlavors) {
            if (flavor === defaultFlavor) {
                continue
            }
            val container = Mockito.mock(
                IdeProductFlavorContainer::class.java
            )
            val flavorName = flavor!!.name
            val flavorSourceProvider = createSourceProvider(projectDir, flavorName)
            Mockito.`when`(container.sourceProvider).thenReturn(flavorSourceProvider)
            Mockito.`when`(container.productFlavor).thenReturn(flavor)
            Mockito.`when`(container.toString()).thenReturn(flavorName)
            flavorContainers.add(container)
        }
        Mockito.`when`(project.productFlavors).thenReturn(flavorContainers)

        // Artifacts
        var artifact = Mockito.mock(IdeAndroidArtifact::class.java)
        val testArtifact = Mockito.mock(IdeJavaArtifact::class.java)
        val androidTestArtifact = Mockito.mock(IdeAndroidArtifact::class.java)
        var applicationId = project.defaultConfig.productFlavor.applicationId
        if (applicationId == null) {
            applicationId = "test.pkg"
        }
        Mockito.`when`(artifact.applicationId).thenReturn(applicationId)
        Mockito.`when`(androidTestArtifact.applicationId).thenReturn(applicationId)
        Mockito.`when`(artifact.level2Dependencies).thenReturn(dependencies)
        Mockito.`when`(testArtifact.level2Dependencies).thenReturn(testDependencies)
        Mockito.`when`(androidTestArtifact.level2Dependencies).thenReturn(androidTestDependencies)
        Mockito.`when`(variant.mainArtifact).thenReturn(artifact)
        Mockito.`when`(variant.unitTestArtifact).thenReturn(testArtifact)
        Mockito.`when`(variant.androidTestArtifact).thenReturn(androidTestArtifact)

        /*
        if (modelVersion.isAtLeast(2, 5, 0, "alpha", 1, false)) {
            DependencyGraphs graphs = createDependencyGraphs();
            when(artifact.getDependencyGraphs()).thenReturn(graphs);
        } else {
            // Should really throw org.gradle.tooling.model.UnsupportedMethodException here!
            when(artifact.getDependencyGraphs()).thenThrow(new RuntimeException());
        }
        */Mockito.`when`(project.buildFolder).thenReturn(File(projectDir, "build"))
        val outputs: MutableList<IdeAndroidArtifactOutput> = mutableListOf()
        outputs.add(createAndroidArtifactOutput("", ""))
        for ((key, value) in splits.entries()) {
            outputs.add(createAndroidArtifactOutput(key, value))
        }
        // outputs.add(createAndroidArtifactOutput("DENSITY", "mdpi"));
        // outputs.add(createAndroidArtifactOutput("DENSITY", "hdpi"));
        Mockito.`when`(artifact.outputs).thenReturn(outputs)
        val seenDimensions: MutableSet<String> = Sets.newHashSet()
        val defaultBuildType = buildTypes[0]
        val defaultBuildTypeName = defaultBuildType.name
        val variantNameSb = StringBuilder()
        val flavorDimensions = project.flavorDimensions
        for (dimension in flavorDimensions) {
            for (flavor in productFlavors) {
                if (flavor !== defaultFlavor && dimension == flavor!!.dimension) {
                    if (seenDimensions.contains(dimension)) {
                        continue
                    }
                    seenDimensions.add(dimension)
                    val name = flavor.name
                    if (variantNameSb.length == 0) {
                        variantNameSb.append(name)
                    } else {
                        variantNameSb.append(name.capitalize())
                    }
                }
            }
        }
        for (flavor in productFlavors) {
            if (flavor !== defaultFlavor && flavor!!.dimension == null) {
                val name = flavor.name
                if (variantNameSb.length == 0) {
                    variantNameSb.append(name)
                } else {
                    variantNameSb.append(name.capitalize())
                }
                break
            }
        }
        if (flavorContainers.size >= 2) {
            val multiVariantSourceSet = createSourceProvider(projectDir, variantNameSb.toString())
            Mockito.`when`(artifact.multiFlavorSourceProvider).thenReturn(multiVariantSourceSet)
        }
        if (variantNameSb.length == 0) {
            variantNameSb.append(defaultBuildTypeName)
        } else {
            variantNameSb.append(defaultBuildTypeName.capitalize())
        }
        val defaultVariantName = variantNameSb.toString()
        if (productFlavors.isEmpty()) {
            val variantSourceSet = createSourceProvider(projectDir, defaultVariantName)
            Mockito.`when`(artifact.variantSourceProvider).thenReturn(variantSourceSet)
        }
        setVariantName(defaultVariantName)
        Mockito.`when`(artifact.name).thenReturn(ARTIFACT_NAME_MAIN)
        Mockito.`when`(testArtifact.name).thenReturn(ARTIFACT_NAME_UNIT_TEST)
        Mockito.`when`(androidTestArtifact.name).thenReturn(ARTIFACT_NAME_ANDROID_TEST)
        Mockito.`when`(artifact.classesFolder)
            .thenReturn(
                File(
                    projectDir,
                    "build/intermediates/javac/$defaultVariantName/classes"
                )
            )
        Mockito.`when`(artifact.additionalClassesFolders)
            .thenReturn(
                setOf(
                    File(
                        projectDir,
                        "build/tmp/kotlin-classes/$defaultVariantName"
                    )
                )
            )
        Mockito.`when`(testArtifact.classesFolder).thenReturn(File(projectDir, "test-classes"))
        Mockito.`when`(androidTestArtifact.classesFolder)
            .thenReturn(File(projectDir, "instrumentation-classes"))

        // Generated sources: Special test support under folder "generated" instead of "src"
        val generated = File(projectDir, "generated")
        if (generated.exists()) {
            val generatedRes = File(generated, "res")
            if (generatedRes.exists()) {
                val generatedResources = listOf(generatedRes)
                Mockito.`when`(artifact.generatedResourceFolders).thenReturn(generatedResources)
            }
            val generatedJava = File(generated, "java")
            if (generatedJava.exists()) {
                val generatedSources = listOf(generatedJava)
                Mockito.`when`(artifact.generatedSourceFolders).thenReturn(generatedSources)
            }
        }

        // Merge values into mergedFlavor
        var minSdkVersion = defaultFlavor!!.minSdkVersion
        var targetSdkVersion = defaultFlavor!!.targetSdkVersion
        val versionCode = defaultFlavor!!.versionCode
        val versionName = defaultFlavor!!.versionName
        val manifestPlaceholders: MutableMap<String, String> = HashMap(
            defaultFlavor!!.manifestPlaceholders
        )
        val resValues: MutableMap<String, IdeClassField> = HashMap(
            defaultFlavor!!.resValues
        )
        val resourceConfigurations: MutableCollection<String> = HashSet(
            defaultFlavor!!.resourceConfigurations
        )
        for (container in flavorContainers) {
            val flavor = container.productFlavor
            manifestPlaceholders.putAll(flavor.manifestPlaceholders)
            resValues.putAll(flavor.resValues)
            resourceConfigurations.addAll(flavor.resourceConfigurations)
        }
        manifestPlaceholders.putAll(defaultBuildType.manifestPlaceholders)
        resValues.putAll(defaultBuildType.resValues)
        Mockito.`when`(mergedFlavor!!.minSdkVersion).thenReturn(minSdkVersion)
        Mockito.`when`(mergedFlavor!!.targetSdkVersion).thenReturn(targetSdkVersion)
        Mockito.`when`(mergedFlavor!!.applicationId).thenReturn(applicationId)
        Mockito.`when`(mergedFlavor!!.versionCode).thenReturn(versionCode)
        Mockito.`when`(mergedFlavor!!.versionName).thenReturn(versionName)
        Mockito.`when`(mergedFlavor!!.manifestPlaceholders).thenReturn(manifestPlaceholders)
        Mockito.`when`(mergedFlavor!!.resValues).thenReturn(resValues)
        Mockito.`when`(mergedFlavor!!.resourceConfigurations).thenReturn(resourceConfigurations)

        // Attempt to make additional variants?
        _variants.add(variant)
        for (buildType in buildTypes) {
            val buildTypeName = buildType.name
            for (flavor in productFlavors) {
                if (flavor === defaultFlavor) {
                    continue
                }
                val variantName = flavor!!.name + buildType.name.capitalize()
                println()
                if (variantName != variant.name) {
                    val newVariant = Mockito.mock(IdeVariant::class.java, Mockito.RETURNS_SMART_NULLS)
                    Mockito.`when`(newVariant.name).thenReturn(variantName)
                    Mockito.`when`(newVariant.buildType).thenReturn(buildTypeName)
                    val productFlavorNames = listOf(flavor.name)
                    Mockito.`when`(newVariant.productFlavors).thenReturn(productFlavorNames)
                    Mockito.`when`(mergedFlavor!!.applicationId).thenReturn(applicationId)
                    minSdkVersion = mergedFlavor!!.minSdkVersion
                    targetSdkVersion = mergedFlavor!!.targetSdkVersion
                    val flavorName = mergedFlavor!!.name
                    val vectorDrawables = mergedFlavor!!.vectorDrawables
                    val variantFlavor = Mockito.mock(IdeProductFlavor::class.java)
                    Mockito.`when`(variantFlavor.minSdkVersion).thenReturn(minSdkVersion)
                    Mockito.`when`(variantFlavor.targetSdkVersion).thenReturn(targetSdkVersion)
                    Mockito.`when`(variantFlavor.name).thenReturn(flavorName)
                    Mockito.`when`(variantFlavor.vectorDrawables).thenReturn(vectorDrawables)
                    Mockito.`when`(variantFlavor.resourceConfigurations)
                        .thenReturn(emptyList())
                    Mockito.`when`(variantFlavor.resValues).thenReturn(emptyMap())
                    Mockito.`when`(variantFlavor.manifestPlaceholders)
                        .thenReturn(emptyMap())
                    Mockito.`when`(newVariant.mergedFlavor).thenReturn(variantFlavor)
                    val mainArtifact = variant.mainArtifact
                    Mockito.`when`(newVariant.mainArtifact).thenReturn(mainArtifact)

                    // Customize artifacts instead of just pointing to the main one
                    // to avoid really redundant long dependency lists
                    artifact = Mockito.mock(IdeAndroidArtifact::class.java)
                    Mockito.`when`(artifact.name).thenReturn(ARTIFACT_NAME_MAIN)
                    Mockito.`when`(artifact.classesFolder)
                        .thenReturn(
                            File(
                                projectDir,
                                "build/intermediates/javac/"
                                        + variantName
                                        + "/classes"
                            )
                        )
                    Mockito.`when`(artifact.additionalClassesFolders)
                        .thenReturn(
                            setOf(
                                File(
                                    projectDir,
                                    "build/tmp/kotlin-classes/$variantName"
                                )
                            )
                        )
                    Mockito.`when`(artifact.applicationId).thenReturn(applicationId)
                    dependencies = Mockito.mock(IdeDependencies::class.java)
                    Mockito.`when`(dependencies.androidLibraries).thenReturn(emptyList())
                    Mockito.`when`(artifact.level2Dependencies).thenReturn(dependencies)
                    Mockito.`when`(newVariant.mainArtifact).thenReturn(artifact)
                    Mockito.`when`(newVariant.unitTestArtifact).thenReturn(null)
                    Mockito.`when`(newVariant.androidTestArtifact).thenReturn(null)
                    _variants.add(newVariant)
                }
            }
        }
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
                                androidLibraries.add(library)
                                return
                            }
                        }
                        index = path.indexOf(".aar/")
                        if (index == -1) {
                            index = path.indexOf(".aar\\")
                        }
                        if (index != -1) {
                            val library = createAndroidLibrary(coordinateString, null, false, lib)
                            androidLibraries.add(library)
                            return
                        }
                        val library = createJavaLibrary(coordinateString, null, false, lib)
                        javaLibraries.add(library)
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
    private fun addGraphItems(
        result: MutableList<GraphItem>,
        globalMap: MutableMap<String, Library>,
        seen: MutableSet<String>,
        libraries: Collection<com.android.builder.model.Library>
    ) {
        for (library in libraries) {
            val coordinates = library.resolvedCoordinates
            val name = (coordinates.groupId
                    + ':'
                    + coordinates.artifactId
                    + ':'
                    + coordinates.version
                    + '@'
                    + coordinates.packaging)
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

    private fun createLevel2Library(library: com.android.builder.model.Library): Library {
        val lib = Mockito.mock(
            Library::class.java
        )
        val coordinates = library.resolvedCoordinates
        val name = (coordinates.groupId
                + ':'
                + coordinates.artifactId
                + ':'
                + coordinates.version
                + '@'
                + coordinates.packaging)
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
        if (line == "apply plugin: 'com.android.library'" || line == "apply plugin: 'android-library'") {
            Mockito.`when`(project.projectType).thenReturn(AndroidProjectTypes.PROJECT_TYPE_LIBRARY)
            return
        } else if (line == "apply plugin: 'com.android.application'" || line == "apply plugin: 'android'") {
            Mockito.`when`(project.projectType).thenReturn(AndroidProjectTypes.PROJECT_TYPE_APP)
            return
        } else if (line == "apply plugin: 'com.android.feature'") {
            Mockito.`when`(project.projectType).thenReturn(AndroidProjectTypes.PROJECT_TYPE_FEATURE)
            return
        } else if (line == "apply plugin: 'com.android.instantapp'") {
            Mockito.`when`(project.projectType).thenReturn(AndroidProjectTypes.PROJECT_TYPE_INSTANTAPP)
            return
        } else if (line == "apply plugin: 'java'") {
            Mockito.`when`(project.projectType).thenReturn(PROJECT_TYPE_JAVA_LIBRARY)
            javaPlugin = true
            return
        } else if (line == "apply plugin: 'java-library'") {
            Mockito.`when`(project.projectType).thenReturn(PROJECT_TYPE_JAVA_LIBRARY)
            javaLibraryPlugin = true
            return
        } else if (context == "buildscript.repositories" || context == "allprojects.repositories") {
            // Plugins not modeled in the builder model
            return
        } else if (line.startsWith("apply plugin: ")) {
            // Some other plugin not relevant to the builder-model
            return
        }
        var key = if (context.isEmpty()) line else "$context.$line"
        val m = configurationPattern.matcher(key)
        if (key.startsWith("ext.")) {
            val name = key.substring(4, key.indexOf(' '))
            ext[name] = getUnquotedValue(key)
        } else if (m.matches()) {
            val artifactName = m.group(1)
            var declaration = getUnquotedValue(key)
            if (GradleCoordinate.parseCoordinateString(declaration) != null) {
                addDependency(declaration, artifactName, false)
                return
            } else {
                // Group/artifact/version syntax?
                if (line.contains("group:")
                    && line.contains("name:")
                    && line.contains("version:")
                ) {
                    var group: String? = null
                    var artifact: String? = null
                    var version: String? = null
                    for (part in Splitter.on(',')
                        .trimResults()
                        .omitEmptyStrings()
                        .split(line.substring(line.indexOf(' ') + 1))) {
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
        } else if (key.startsWith("dependencies.provided '") && key.endsWith("'")) {
            val declaration = getUnquotedValue(key)
            addDependency(declaration, null, true)
        } else if (line.startsWith("applicationId ") || line.startsWith("packageName ")) {
            val id = getUnquotedValue(key)
            val flavor = getFlavorFromContext(context)
            if (flavor != null) {
                Mockito.`when`(flavor.applicationId).thenReturn(id)
            } else {
                error("Unexpected flavor context $context")
            }
        } else if (line.startsWith("minSdkVersion ")) {
            val apiVersion = createApiVersion(key)
            val flavor = getFlavorFromContext(context)
            if (flavor != null) {
                Mockito.`when`(flavor.minSdkVersion).thenReturn(apiVersion)
            } else {
                error("Unexpected flavor context $context")
            }
        } else if (line.startsWith("targetSdkVersion ")) {
            val version = createApiVersion(key)
            val flavor = getFlavorFromContext(context)
            if (flavor != null) {
                Mockito.`when`(flavor.targetSdkVersion).thenReturn(version)
            } else {
                error("Unexpected flavor context $context")
            }
        } else if (line.startsWith("versionCode ")) {
            val value = key.substring(key.indexOf(' ') + 1).trim { it <= ' ' }
            if (Character.isDigit(value[0])) {
                val number = Integer.decode(value)
                val flavor = getFlavorFromContext(context)
                if (flavor != null) {
                    Mockito.`when`(flavor.versionCode).thenReturn(number)
                } else {
                    error("Unexpected flavor context $context")
                }
            } else {
                warn("Ignoring unrecognized versionCode token: $value")
            }
        } else if (line.startsWith("versionName ")) {
            val name = getUnquotedValue(key)
            val flavor = getFlavorFromContext(context)
            if (flavor != null) {
                Mockito.`when`(flavor.versionName).thenReturn(name)
            } else {
                error("Unexpected flavor context $context")
            }
        } else if (line.startsWith("versionNameSuffix ")) {
            val name = getUnquotedValue(key)
            val flavor = getFlavorFromContext(context)
            if (flavor != null) {
                Mockito.`when`(flavor.versionNameSuffix).thenReturn(name)
            } else {
                error("Unexpected flavor context $context")
            }
        } else if (line.startsWith("applicationIdSuffix ")) {
            val name = getUnquotedValue(key)
            val flavor = getFlavorFromContext(context)
            if (flavor != null) {
                Mockito.`when`(flavor.applicationIdSuffix).thenReturn(name)
            } else {
                error("Unexpected flavor context $context")
            }
        } else if (key.startsWith("android.resourcePrefix ")) {
            val value = getUnquotedValue(key)
            Mockito.`when`(project.resourcePrefix).thenReturn(value)
        } else if (key.startsWith("group=")) {
            val value = getUnquotedValue(key)
            Mockito.`when`(project.groupId).thenReturn(value)
        } else if (key.startsWith("android.buildToolsVersion ")) {
            val value = getUnquotedValue(key)
            Mockito.`when`(project.buildToolsVersion).thenReturn(value)
        } else if (line.startsWith("minifyEnabled ") && key.startsWith("android.buildTypes.")) {
            val name = key.substring("android.buildTypes.".length, key.indexOf(".minifyEnabled"))
            val buildType = getBuildType(name, true)
            val value = getUnquotedValue(line)
            Mockito.`when`(buildType!!.isMinifyEnabled).thenReturn(SdkConstants.VALUE_TRUE == value)
        } else if (key.startsWith("android.compileSdkVersion ")) {
            val value = getUnquotedValue(key)
            Mockito.`when`(project.compileTarget)
                .thenReturn(if (Character.isDigit(value[0])) "android-$value" else value)
        } else if (line.startsWith("resConfig")) { // and resConfigs
            val flavor: IdeProductFlavor?
            flavor = if (context.startsWith("android.productFlavors.")) {
                val flavorName = context.substring("android.productFlavors.".length)
                getProductFlavor(flavorName, true)
            } else if (context == "android.defaultConfig") {
                defaultFlavor
            } else {
                error("Unexpected flavor $context")
                return
            }
            val configs = flavor!!.resourceConfigurations as MutableCollection<String>
            for (s in Splitter.on(",").trimResults().split(line.substring(line.indexOf(' ') + 1))) {
                if (!configs.contains(s)) {
                    configs.add(getUnquotedValue(s))
                }
            }
        } else if (key.startsWith("android.defaultConfig.vectorDrawables.useSupportLibrary ")) {
            val value = getUnquotedValue(key)
            if (SdkConstants.VALUE_TRUE == value) {
                val options = vectorDrawableOptions
                Mockito.`when`(options!!.useSupportLibrary).thenReturn(true)
            }
        } else if (key.startsWith(
                "android.compileOptions.sourceCompatibility JavaVersion.VERSION_"
            )
        ) {
            val s = key.substring(key.indexOf("VERSION_") + "VERSION_".length).replace('_', '.')
            Mockito.`when`(compileOptions.sourceCompatibility).thenReturn(s)
        } else if (key.startsWith(
                "android.compileOptions.targetCompatibility JavaVersion.VERSION_"
            )
        ) {
            val s = key.substring(key.indexOf("VERSION_") + "VERSION_".length).replace('_', '.')
            Mockito.`when`(compileOptions.targetCompatibility).thenReturn(s)
        } else if (key.startsWith("buildscript.dependencies.classpath ")) {
            if (key.contains("'com.android.tools.build:gradle:")) {
                val value = getUnquotedValue(key)
                val gc = GradleCoordinate.parseCoordinateString(value)
                if (gc != null) {
                    modelVersion = GradleVersion.parse(gc.revision)
                    Mockito.`when`(project.modelVersion).thenReturn(gc.revision)
                }
            } // else ignore other class paths
        } else if (key.startsWith("android.defaultConfig.testInstrumentationRunner ")
            || key.contains(".proguardFiles ")
            || key == "dependencies.compile fileTree(dir: 'libs', include: ['*.jar'])" || key.startsWith("dependencies.androidTestCompile('")
        ) {
            // Ignored for now
        } else if (line.startsWith("manifestPlaceholders [")
            && key.startsWith("android.")
            && line.endsWith("]")
        ) {
            // Example:
            // android.defaultConfig.manifestPlaceholders [
            // localApplicationId:'com.example.manifest_merger_example']
            val manifestPlaceholders: MutableMap<String, String>
            manifestPlaceholders = if (context.startsWith("android.buildTypes.")) {
                val name = context.substring("android.buildTypes.".length)
                val buildType = getBuildType(name, false)
                if (buildType != null) {
                    buildType.manifestPlaceholders as MutableMap<String, String>
                } else {
                    error("Couldn't find flavor $name; ignoring $key")
                    return
                }
            } else if (context.startsWith("android.productFlavors.")) {
                val name = context.substring("android.productFlavors.".length)
                val flavor = getProductFlavor(name, false)
                if (flavor != null) {
                    flavor.manifestPlaceholders as MutableMap<String, String>
                } else {
                    error("Couldn't find flavor $name; ignoring $key")
                    return
                }
            } else {
                defaultFlavor!!.manifestPlaceholders as MutableMap<String, String>
            }
            val mapString = key.substring(key.indexOf('[') + 1, key.indexOf(']')).trim { it <= ' ' }

            // TODO: Support one than one more entry in the map? Comma separated list
            val index = mapString.indexOf(':')
            assert(index != -1) { mapString }
            var mapKey = mapString.substring(0, index).trim { it <= ' ' }
            mapKey = getUnquotedValue(mapKey)
            var mapValue = mapString.substring(index + 1).trim { it <= ' ' }
            mapValue = getUnquotedValue(mapValue)
            manifestPlaceholders.put(mapKey, mapValue)
        } else if (key.startsWith("android.flavorDimensions ")) {
            val value = key.substring("android.flavorDimensions ".length)
            val flavorDimensions = project.flavorDimensions as MutableCollection<String>
            for (s in Splitter.on(',').omitEmptyStrings().trimResults().split(value)) {
                val dimension = getUnquotedValue(s)
                if (!flavorDimensions.contains(dimension)) {
                    flavorDimensions.add(dimension)
                }
            }
        } else if (line.startsWith("dimension ") && key.startsWith("android.productFlavors.")) {
            val name = key.substring("android.productFlavors.".length, key.indexOf(".dimension"))
            val productFlavor = getProductFlavor(name, true)
            val dimension = getUnquotedValue(line)
            Mockito.`when`(productFlavor!!.dimension).thenReturn(dimension)
        } else if (key.startsWith("android.") && line.startsWith("resValue ")) {
            // Example:
            // android.defaultConfig.resValue 'string', 'defaultConfigName', 'Some DefaultConfig
            // Data'
            val index = key.indexOf(".resValue ")
            var name = key.substring("android.".length, index)
            val resValues: MutableMap<String, IdeClassField>
            if (name.startsWith("buildTypes.")) {
                name = name.substring("buildTypes.".length)
                val buildType = getBuildType(name, false)
                if (buildType != null) {
                    resValues = buildType.resValues as MutableMap<String, IdeClassField>
                } else {
                    error("Couldn't find flavor $name; ignoring $key")
                    return
                }
            } else if (name.startsWith("productFlavors.")) {
                name = name.substring("productFlavors.".length)
                val flavor = getProductFlavor(name, false)
                if (flavor != null) {
                    resValues = flavor.resValues as MutableMap<String, IdeClassField>
                } else {
                    error("Couldn't find flavor $name; ignoring $key")
                    return
                }
            } else {
                assert(name.indexOf('.') == -1) { name }
                resValues = defaultFlavor!!.resValues as MutableMap<String, IdeClassField>
            }
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
            val field = Mockito.mock(IdeClassField::class.java)
            Mockito.`when`(field.name).thenReturn(fieldName)
            Mockito.`when`(field.type).thenReturn(type)
            Mockito.`when`(field.value).thenReturn(value)
            resValues.put(fieldName!!, field)
        } else if (context.startsWith("android.splits.")
            && context.indexOf('.', "android.splits.".length) == -1
        ) {
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
        } else if (key.startsWith("android.aaptOptions.namespaced ")) {
            val value = getUnquotedValue(key)
            if (SdkConstants.VALUE_TRUE == value) {
                val options = getAaptOptions()
                Mockito.`when`(options!!.namespacing).thenReturn(IdeAaptOptions.Namespacing.REQUIRED)
            }
        } else if (key.startsWith("groupId ")) {
            val groupId = getUnquotedValue(key)
            Mockito.`when`(project.groupId).thenReturn(groupId)
        } else if (key.startsWith("android.lintOptions.")) {
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
        } else if (key.startsWith("android.buildFeatures.")) {
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
                    val viewBindingOptions = Mockito.mock(
                        IdeViewBindingOptions::class.java
                    )
                    Mockito.`when`(viewBindingOptions.enabled).thenReturn(toBoolean(arg))
                    Mockito.`when`(project.viewBindingOptions).thenReturn(viewBindingOptions)
                }
            }
        } else {
            warn("ignored line: $line, context=$context")
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
        Mockito.`when`(project.lintOptions).thenReturn(lintOptions)
    }

    private fun updateLintOptions(
        baseline: File?,
        lintConfig: File?,
        severities: Map<String, Int>?,
        tests: Boolean?,
        dependencies: Boolean?
    ) {
        // No mocking IdeLintOptions; it's final
        lintOptions = IdeLintOptionsImpl(
            baseline ?: lintOptions!!.baselineFile,
            lintConfig ?: lintOptions!!.lintConfig,
            severities ?: severityOverrides,
            tests ?: lintOptions!!.isCheckTestSources,
            dependencies ?: lintOptions!!.isCheckDependencies,  // TODO: Allow these to be customized by model mocker
            lintOptions!!.enable,
            lintOptions!!.disable,
            lintOptions!!.check,
            lintOptions!!.isAbortOnError,
            lintOptions!!.isAbsolutePaths,
            lintOptions!!.isNoLines,
            lintOptions!!.isQuiet,
            lintOptions!!.isCheckAllWarnings,
            lintOptions!!.isIgnoreWarnings,
            lintOptions!!.isWarningsAsErrors,
            lintOptions!!.isIgnoreTestSources,
            lintOptions!!.isCheckGeneratedSources,
            lintOptions!!.isCheckReleaseBuilds,
            lintOptions!!.isExplainIssues,
            lintOptions!!.isShowAll,
            lintOptions!!.textReport,
            lintOptions!!.textOutput,
            lintOptions!!.htmlReport,
            lintOptions!!.htmlOutput,
            lintOptions!!.xmlReport,
            lintOptions!!.xmlOutput,
            lintOptions!!.sarifReport,
            lintOptions!!.sarifOutput
        )
        Mockito.`when`(project.lintOptions).thenReturn(lintOptions)
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
        if (gradle.startsWith("file(\"") && gradle.endsWith("\")")
            || gradle.startsWith("file('") && gradle.endsWith("')")
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

    private fun getFlavorFromContext(context: String): IdeProductFlavor? {
        return if (context == "android.defaultConfig") {
            defaultFlavor
        } else if (context.startsWith("android.productFlavors.")) {
            val name = context.substring("android.productFlavors.".length)
            getProductFlavor(name, true)
        } else {
            null
        }
    }

    private val vectorDrawableOptions: IdeVectorDrawablesOptions?
        get() {
            if (vectorDrawablesOptions == null) {
                vectorDrawablesOptions = Mockito.mock(IdeVectorDrawablesOptions::class.java)
                Mockito.`when`(mergedFlavor!!.vectorDrawables).thenReturn(vectorDrawablesOptions)
            }
            return vectorDrawablesOptions
        }

    private fun getAaptOptions(): IdeAaptOptions? {
        if (aaptOptions == null) {
            aaptOptions = Mockito.mock(IdeAaptOptions::class.java)
            Mockito.`when`(project.aaptOptions).thenReturn(aaptOptions)
            Mockito.`when`(aaptOptions!!.namespacing).thenReturn(IdeAaptOptions.Namespacing.DISABLED)
        }
        return aaptOptions
    }

    @Contract("_,true -> !null")
    private fun getBuildType(name: String, create: Boolean): IdeBuildType? {
        for (type in buildTypes) {
            if (type.name == name) {
                return type
            }
        }
        return if (create) {
            createBuildType(name)
        } else null
    }

    private fun createBuildType(name: String): IdeBuildType {
        val buildType = Mockito.mock(IdeBuildType::class.java)
        Mockito.`when`(buildType.name).thenReturn(name)
        Mockito.`when`(buildType.toString()).thenReturn(name)
        Mockito.`when`(buildType.isDebuggable).thenReturn(name.startsWith("debug"))
        buildTypes.add(buildType)
        // Creating mutable map here which we can add to later
        Mockito.`when`(buildType.resValues).thenReturn(Maps.newHashMap())
        Mockito.`when`(buildType.manifestPlaceholders).thenReturn(Maps.newHashMap())
        return buildType
    }

    private fun block(
        name: String,
        @Language("Groovy") blockBody: String,
        context: String
    ) {
        if ("android.productFlavors" == context && buildTypes.stream()
                .noneMatch { flavor: IdeBuildType -> flavor.name == name }
        ) {
            // Defining new product flavors
            createProductFlavor(name)
        }
        if ("android.buildTypes" == context && buildTypes.stream()
                .noneMatch { buildType: IdeBuildType -> buildType.name == name }
        ) {
            // Defining new build types
            createBuildType(name)
        }
        scan(blockBody, if (context.isEmpty()) name else "$context.$name")
    }

    @Contract("_,true -> !null")
    private fun getProductFlavor(name: String, create: Boolean): IdeProductFlavor? {
        for (flavor in productFlavors) {
            if (flavor!!.name == name) {
                return flavor
            }
        }
        return if (create) {
            createProductFlavor(name)
        } else null
    }

    private fun createProductFlavor(name: String): IdeProductFlavor {
        val flavor = Mockito.mock(IdeProductFlavor::class.java)
        Mockito.`when`(flavor.name).thenReturn(name)
        Mockito.`when`(flavor.toString()).thenReturn(name)
        // Creating mutable map here which we can add to later
        Mockito.`when`(flavor.resValues).thenReturn(Maps.newHashMap())
        Mockito.`when`(flavor.manifestPlaceholders).thenReturn(Maps.newHashMap())
        // Creating mutable list here which we can add to later
        Mockito.`when`(flavor.resourceConfigurations).thenReturn(mutableListOf())
        productFlavors.add(flavor)
        return flavor
    }

    private fun createApiVersion(value: String): IdeApiVersion {
        val version = Mockito.mock(IdeApiVersion::class.java)
        val s = value.substring(value.indexOf(' ') + 1)
        if (s.startsWith("'")) {
            val codeName = getUnquotedValue(s)
            val sdkVersion = SdkVersionInfo.getVersion(codeName, null)
            if (sdkVersion != null) {
                Mockito.`when`(version.codename).thenReturn(sdkVersion.codename)
                Mockito.`when`(version.apiString).thenReturn(sdkVersion.apiString)
                Mockito.`when`(version.apiLevel).thenReturn(sdkVersion.apiLevel)
            }
        } else {
            Mockito.`when`(version.apiString).thenReturn(s)
            Mockito.`when`(version.codename).thenReturn(null)
            Mockito.`when`(version.apiLevel).thenReturn(s.toInt())
        }
        return version
    }

    private fun addDependency(declaration: String, artifact: String?, isProvided: Boolean) {
        // If it's one of the common libraries, built up the full dependency graph
        // that we know will actually be used
        //
        // To compute these graphs, put the dependency you're interested into
        // a test project and then run ./gradlew app:dependencies
        if (declaration.startsWith("com.android.support:appcompat-v7:")) {
            val version = declaration.substring("com.android.support:appcompat-v7:".length)
            addTransitiveLibrary(
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
                    .replace("VERSION", version),
                artifact
            )
        } else if (declaration.startsWith("com.android.support:support-v4:")) {
            val version = declaration.substring("com.android.support:support-v4:".length)
            addTransitiveLibrary(
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
                    .replace("VERSION", version),
                artifact
            )
        } else if (declaration.startsWith("com.android.support.constraint:constraint-layout:")) {
            val version = declaration.substring(
                "com.android.support.constraint:constraint-layout:".length
            )
            addTransitiveLibrary(
                """
+--- com.android.support.constraint:constraint-layout:VERSION
     \--- com.android.support.constraint:constraint-layout-solver:VERSION
"""
                    .replace("VERSION", version),
                artifact
            )
        } else if (declaration.startsWith("com.firebase:firebase-client-android:")) {
            val version = declaration.substring("com.firebase:firebase-client-android:".length)
            addTransitiveLibrary(
                """
\--- com.firebase:firebase-client-android:VERSION
     \--- com.firebase:firebase-client-jvm:VERSION
          +--- com.fasterxml.jackson.core:jackson-databind:2.2.2
          |    +--- com.fasterxml.jackson.core:jackson-annotations:2.2.2
          |    \--- com.fasterxml.jackson.core:jackson-core:2.2.2
          \--- com.firebase:tubesock:0.0.12"""
                    .replace("VERSION", version),
                artifact
            )
        } else if (declaration.startsWith("com.android.support:design:")) {
            // Design library
            val version = declaration.substring("com.android.support:design:".length)
            addTransitiveLibrary(
                """
+--- com.android.support:design:VERSION
|    +--- com.android.support:recyclerview-v7:VERSION
|    |    +--- com.android.support:support-annotations:VERSION
|    |    \--- com.android.support:support-v4:VERSION (*)
|    +--- com.android.support:appcompat-v7:VERSION (*)
|    \--- com.android.support:support-v4:VERSION (*)"""
                    .replace("VERSION", version),
                artifact
            )
        } else if (declaration.startsWith("com.google.android.gms:play-services-analytics:")) {
            // Analytics
            val version = declaration.substring(
                "com.google.android.gms:play-services-analytics:".length
            )
            addTransitiveLibrary(
                """
+--- com.google.android.gms:play-services-analytics:VERSION
|    \--- com.google.android.gms:play-services-basement:VERSION
|         \--- com.android.support:support-v4:23.0.0 -> 23.4.0
|              \--- com.android.support:support-annotations:23.4.0"""
                    .replace("VERSION", version),
                artifact
            )
        } else if (declaration.startsWith("com.google.android.gms:play-services-gcm:")) {
            // GMS
            val version = declaration.substring("com.google.android.gms:play-services-gcm:".length)
            addTransitiveLibrary(
                """
+--- com.google.android.gms:play-services-gcm:VERSION
|    +--- com.google.android.gms:play-services-base:VERSION (*)
|    \--- com.google.android.gms:play-services-measurement:VERSION
|         \--- com.google.android.gms:play-services-basement:VERSION (*)"""
                    .replace("VERSION", version),
                artifact
            )
        } else if (declaration.startsWith("com.google.android.gms:play-services-appindexing:")) {
            // App Indexing
            val version = declaration.substring(
                "com.google.android.gms:play-services-appindexing:".length
            )
            addTransitiveLibrary(
                """
+--- com.google.android.gms:play-services-appindexing:VERSION
|    \--- com.google.android.gms:play-services-base:VERSION
|         \--- com.google.android.gms:play-services-basement:VERSION (*)"""
                    .replace("VERSION", version),
                artifact
            )
        } else if (declaration.startsWith("org.jetbrains.kotlin:kotlin-stdlib-jdk7:")) {
            // Kotlin
            val version = declaration.substring("org.jetbrains.kotlin:kotlin-stdlib-jdk7:".length)
            addTransitiveLibrary(
                """
+--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:VERSION
|    \--- org.jetbrains.kotlin:kotlin-stdlib:VERSION
|         +--- org.jetbrains.kotlin:kotlin-stdlib-common:VERSION
|         \--- org.jetbrains:annotations:13.0
+--- org.jetbrains.kotlin:kotlin-stdlib:VERSION (*)
+--- org.jetbrains.kotlin:kotlin-stdlib-common:VERSION"""
                    .replace("VERSION", version),
                artifact
            )
        } else if (declaration.startsWith("org.jetbrains.kotlin:kotlin-stdlib-jdk8:")) {
            // Kotlin
            val version = declaration.substring("org.jetbrains.kotlin:kotlin-stdlib-jdk8:".length)
            addTransitiveLibrary(
                """
+--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:VERSION
|    +--- org.jetbrains.kotlin:kotlin-stdlib:VERSION
|    |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:VERSION
|    |    \--- org.jetbrains:annotations:13.0
|    \--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:VERSION
|         \--- org.jetbrains.kotlin:kotlin-stdlib:VERSION (*)"""
                    .replace("VERSION", version),
                artifact
            )
        } else {
            // Look for the library in the dependency graph provided
            val dep = graphs[declaration]
            dep?.let { addLibrary(it, artifact) }
                ?: if (isJavaLibrary(declaration)) {
                    // Not found in dependency graphs: create a single Java library
                    val library = createJavaLibrary(declaration, isProvided)
                    if (artifact == null || artifact.isEmpty()) {
                        javaLibraries.add(library)
                    } else if (artifact == "test") {
                        testJavaLibraries.add(library)
                    } else if (artifact == "androidTest") {
                        androidTestJavaLibraries.add(library)
                    } else {
                        error("Unrecognized artifact name: $artifact")
                    }
                } else {
                    // Not found in dependency graphs: create a single Android library
                    val library = createAndroidLibrary(declaration, isProvided)
                    if (artifact == null || artifact.isEmpty()) {
                        androidLibraries.add(library)
                    } else if (artifact == "test") {
                        testAndroidLibraries.add(library)
                    } else if (artifact == "androidTest") {
                        androidTestAndroidLibraries.add(library)
                    } else {
                        error("Unrecognized artifact name: $artifact")
                    }
                }
        }
    }

    private fun addTransitiveLibrary(graph: String, artifact: String?) {
        for (dep in parseDependencyGraph(graph)) {
            addLibrary(dep, artifact)
        }
    }

    private fun addLibrary(dep: Dep, artifact: String?) {
        val androidLibraries: MutableList<IdeAndroidLibrary>
        val javaLibraries: MutableList<IdeJavaLibrary>
        val moduleLibraries: MutableList<IdeModuleLibrary>
        if (artifact == null || artifact.isEmpty()) {
            androidLibraries = this.androidLibraries
            javaLibraries = this.javaLibraries
            moduleLibraries = this.moduleLibraries
        } else if (artifact == "test") {
            androidLibraries = testAndroidLibraries
            javaLibraries = testJavaLibraries
            moduleLibraries = testModuleLibraries
        } else if (artifact == "androidTest") {
            androidLibraries = androidTestAndroidLibraries
            javaLibraries = androidTestJavaLibraries
            moduleLibraries = androidTestModuleLibraries
        } else {
            error("Unrecognized artifact name: $artifact")
            return
        }
        val libraries = dep.createLibrary()
        for (library in libraries) {
            if (library is IdeAndroidLibrary) {
                val androidLibrary = library
                if (!androidLibraries.contains(androidLibrary)) {
                    androidLibraries.add(androidLibrary)
                }
            } else if (library is IdeJavaLibrary) {
                val javaLibrary = library
                if (!javaLibraries.contains(javaLibrary)) {
                    javaLibraries.add(javaLibrary)
                }
            } else {
                val moduleLibrary = library as IdeModuleLibrary
                if (!moduleLibraries.contains(moduleLibrary)) {
                    moduleLibraries.add(moduleLibrary)
                }
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
        val dir: File
        dir = if (useBuildCache) {
            // Not what build cache uses, but we just want something stable and unique
            // for tests
            val hash = Hashing.sha1()
                .hashString(coordinate.toString(), Charsets.UTF_8).toString()
            File(
                FileUtils.join(
                    System.getProperty("user.home"),
                    ".android",
                    "build-cache",
                    hash,
                    "output"
                )
            )
        } else {
            File(
                projectDir,
                "build/intermediates/exploded-aar/"
                        + coordinate.groupId
                        + "/"
                        + coordinate.artifactId
                        + "/"
                        + coordinate.revision
            )
        }
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
                    jar.path,  // non relative path is fine here too.
                    jar.path,  // non relative path is fine here too.
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
                "caches/modules-2/files-2.1/"
                        + coordinate.groupId
                        + "/"
                        + coordinate.artifactId
                        + "/"
                        + coordinate.revision
                        +  // Usually some hex string here, but keep same to keep test
                        // behavior stable
                        "9c6ef172e8de35fd8d4d8783e4821e57cdef7445/"
                        + coordinate.artifactId
                        + "-"
                        + coordinate.revision
                        + SdkConstants.DOT_JAR
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
        ensureInitialized()

        // For something like debugFreeSubscription, set the variant's build type
        // to "debug", and the flavor set to ["free", "subscription"]
        Mockito.`when`(variant!!.name).thenReturn(variantName)
        val splitter = Splitter.on('_')
        val flavors: MutableList<String> = mutableListOf()
        for (s in splitter.split(SdkVersionInfo.camelCaseToUnderlines(variantName))) {
            val buildType = getBuildType(s, false)
            if (buildType != null) {
                Mockito.`when`(variant!!.buildType).thenReturn(s)
            } else {
                val flavor = getProductFlavor(s, false)
                if (flavor != null) {
                    flavors.add(s)
                }
            }
        }
        Mockito.`when`(variant!!.productFlavors).thenReturn(flavors)
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
    @VisibleForTesting
    fun createDependencies(graph: String): IdeDependencies {
        val deps = parseDependencyGraph(graph)
        return createDependencies(deps)
    }

    @JvmOverloads
    fun parseDependencyGraph(graph: String, map: MutableMap<String, Dep> = Maps.newHashMap()): List<Dep> {
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
            filterType: String, identifier: String
        ): IdeAndroidArtifactOutput {
            val artifactOutput = Mockito.mock(
                IdeAndroidArtifactOutput::class.java
            )
            if (filterType.isEmpty()) {
                Mockito.`when`(artifactOutput.filters).thenReturn(emptyList())
            } else {
                val filters: MutableList<FilterData?> = mutableListOf()
                val filter = Mockito.mock(FilterData::class.java)
                Mockito.`when`(filter.filterType).thenReturn(filterType)
                Mockito.`when`(filter.identifier).thenReturn(identifier)
                filters.add(filter)
                Mockito.`when`(artifactOutput.filters).thenReturn(filters)
            }
            return artifactOutput
        }

        private fun createSourceProvider(
            root: File, name: String
        ): IdeSourceProvider {
            val provider = Mockito.mock(IdeSourceProvider::class.java)
            Mockito.`when`(provider.name).thenReturn(name)
            Mockito.`when`(provider.manifestFile)
                .thenReturn(File(root, "src/" + name + "/" + SdkConstants.ANDROID_MANIFEST_XML))
            val resDirectories: MutableList<File> = Lists.newArrayListWithCapacity(2)
            val javaDirectories: MutableList<File> = Lists.newArrayListWithCapacity(2)
            val assetsDirectories: MutableList<File> = Lists.newArrayListWithCapacity(1)
            resDirectories.add(File(root, "src/$name/res"))
            javaDirectories.add(File(root, "src/$name/java"))
            javaDirectories.add(File(root, "src/$name/kotlin"))
            assetsDirectories.add(File(root, "src/$name/assets"))
            Mockito.`when`(provider.resDirectories).thenReturn(resDirectories)
            Mockito.`when`(provider.javaDirectories).thenReturn(javaDirectories)
            Mockito.`when`(provider.assetsDirectories).thenReturn(assetsDirectories)

            // TODO: other file types
            return provider
        }

        /**
         * Returns whether a library declaration is a plain Java library instead of an Android library.
         * There is no way to tell from the Gradle description; it involves looking at the actual Maven
         * artifacts. For mocking purposes we have a hardcoded list.
         */
        private fun isJavaLibrary(declaration: String): Boolean {
            if (declaration.startsWith("com.android.support:support-annotations:")) {
                return true
            } else if (declaration.startsWith("com.android.support:support-v4:")
                || declaration.startsWith("com.android.support:support-v13:")
            ) {
                // Jar prior to to v20
                return (declaration.contains(":13")
                        || declaration.contains(":18")
                        || declaration.contains(":19"))
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
            } else if (declaration.startsWith("org.jetbrains.kotlin:kotlin-")
                || declaration.startsWith("org.jetbrains:annotations")
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
