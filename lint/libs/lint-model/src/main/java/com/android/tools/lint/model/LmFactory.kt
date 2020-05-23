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

package com.android.tools.lint.model

import com.android.AndroidProjectTypes
import com.android.builder.model.AaptOptions
import com.android.builder.model.AndroidArtifact
import com.android.builder.model.AndroidBundle
import com.android.builder.model.AndroidLibrary
import com.android.builder.model.AndroidProject
import com.android.builder.model.ApiVersion
import com.android.builder.model.BaseArtifact
import com.android.builder.model.BuildType
import com.android.builder.model.ClassField
import com.android.builder.model.JavaArtifact
import com.android.builder.model.JavaLibrary
import com.android.builder.model.Library
import com.android.builder.model.LintOptions
import com.android.builder.model.MavenCoordinates
import com.android.builder.model.ProductFlavor
import com.android.builder.model.SourceProvider
import com.android.builder.model.SourceProviderContainer
import com.android.builder.model.Variant
import com.android.builder.model.level2.DependencyGraphs
import com.android.builder.model.level2.GlobalLibraryMap
import com.android.builder.model.level2.GraphItem
import com.android.ide.common.gradle.model.IdeAndroidProject
import com.android.ide.common.gradle.model.IdeLintOptions
import com.android.ide.common.gradle.model.IdeMavenCoordinates
import com.android.ide.common.repository.GradleVersion
import com.android.sdklib.AndroidVersion
import com.android.utils.FileUtils
import java.io.File
import java.nio.file.Paths
import com.android.builder.model.level2.Library as LibraryL2

/**
 * Converter from the builder model library to lint's own model.
 */
class LmFactory : LmModuleLoader {
    init {
        // We're just copying by value so make sure our constants match
        assert(LmMavenName.LOCAL_AARS == IdeMavenCoordinates.LOCAL_AARS)
    }

    /**
     * Map from a builder model to the corresponding lint model library. This map helps
     * ensure that we don't keep creating the same deep copies over and over (since they
     * recursively include dependencies), since libraries are repeated quite a lot,
     * including across variants
     */
    private val libraryMap = LinkedHashMap<Library, LmLibrary>()

    private val libraryResolverMap = mutableMapOf<String, LmLibrary>()
    private val libraryResolver = DefaultLmLibraryResolver(libraryResolverMap)

    /**
     * Kotlin source folders to merge in for the main source set. This should <b>not</b>
     * be necessary but is here temporarily since it's missing from the builder model.
     * This should be removed ASAP.
     */
    var kotlinSourceFolderLookup: ((variantName: String) -> List<File>)? = null

    /**
     * Factory from an XML file to a [LmModule].
     * The file was previously saved by [LmSerialization.writeModule].
     */
    fun create(xmlFile: File): LmModule = LmSerialization.readModule(xmlFile)

    /**
     * Converter from the builder model library to lint's own model.
     * If [deep] is true, it will create a deep copy; otherwise, it will
     * create wrapper objects. The advantage of a shallow copy is that
     * some expensive fields are only computed lazily (such as all the
     * variant data, which may not be needed in the IDE when running
     * on the fly analysis). The advantage of a deep copy is that (at
     * least during testing) all fields are accessed so we can make sure
     * there are no inconvertible data, and when all the data is going
     * to be used anyway there's no benefit in the additional overhead
     * of lazy lookup.
     */
    fun create(project: IdeAndroidProject, dir: File, deep: Boolean = true): LmModule {
        val cached = project.getClientProperty(CACHE_KEY) as? LmModule
        if (cached != null) {
            return cached
        }

        val gradleVersion = getGradleVersion(project)

        return if (deep) {
            val variantList = mutableListOf<LmVariant>()
            val module = DefaultLmModule(
                loader = this,
                dir = dir,
                modulePath = project.name,
                type = getModuleType(project.projectType),
                mavenName = getMavenName(project),
                gradleVersion = gradleVersion,
                buildFolder = project.buildFolder,
                lintOptions = getLintOptions(project),
                lintRuleJars = project.getLintRuleJarsForAnyAgpVersion(),
                buildFeatures = getBuildFeatures(project, gradleVersion),
                resourcePrefix = project.resourcePrefix,
                dynamicFeatures = project.dynamicFeatures,
                bootClassPath = project.bootClasspath.map { File(it) },
                javaSourceLevel = project.javaCompileOptions.sourceCompatibility,
                compileTarget = project.compileTarget,
                neverShrinking = isNeverShrinking(project),
                variants = variantList,
                oldProject = project
            )

            for (variant in project.variants) {
                variantList.add(getVariant(module, project, variant))
            }

            module
        } else {
            LazyLmModule(
                loader = this,
                project = project,
                dir = dir,
                gradleVersion = gradleVersion
            )
        }.also { module ->
            project.putClientProperty(CACHE_KEY, module)
        }
    }

    /**
     * Returns the list of Lint Rule file, no matter what the AGP version is.
     */
    private fun IdeAndroidProject.getLintRuleJarsForAnyAgpVersion() = lintRuleJars ?: listOf(
        FileUtils.join(buildFolder, "intermediates", "lint", "lint.jar"),
        FileUtils.join(buildFolder, "intermediates", "lint_jar", "lint.jar"),
        FileUtils.join(
            buildFolder,
            "intermediates",
            "lint_jar",
            "global",
            "prepareLintJar",
            "lint.jar"
        )
    )


    private fun getDependencies(
        graph: DependencyGraphs,
        globalLibraryMap: GlobalLibraryMap
    ): LmDependencies {
        val provided = graph.providedLibraries.toSet()
        val skipped = graph.skippedLibraries.toSet()

        // Populate library resolver
        globalLibraryMap.libraries.mapValues {
            val address = it.key
            val library = getLibrary(
                library = it.value,
                provided = provided.contains(address),
                skipped = skipped.contains(address)
            )
            libraryResolverMap[library.artifactAddress] = library
        }

        val compileDependencies = getDependencies(
            items = graph.compileDependencies,
            graph = graph,
            libraryResolver = libraryResolver
        )

        val packageDependencies = getDependencies(
            items = graph.packageDependencies,
            graph = graph,
            libraryResolver = libraryResolver
        )

        return DefaultLmDependencies(
            compileDependencies = compileDependencies,
            packageDependencies = packageDependencies,
            libraryResolver = libraryResolver
        )
    }

    private fun getLibrary(library: LibraryL2, provided: Boolean, skipped: Boolean): LmLibrary {
        return when (library.type) {
            LibraryL2.LIBRARY_ANDROID -> {
                // TODO: Construct file objects lazily!
                DefaultLmAndroidLibrary(
                    artifactAddress = library.artifactAddress,
                    manifest = File(library.manifest),
                    // TODO - expose compile jar vs impl jar?
                    jarFiles = (library.localJars + library.jarFile).map { File(it) },
                    folder = library.folder, // Needed for workaround for b/66166521
                    resFolder = File(library.resFolder),
                    assetsFolder = File(library.assetsFolder),
                    lintJar = File(library.lintJar),
                    publicResources = File(library.publicResources),
                    symbolFile = File(library.symbolFile),
                    externalAnnotations = File(library.externalAnnotations),
                    project = library.projectPath,
                    provided = provided,
                    skipped = skipped,
                    resolvedCoordinates = getMavenName(library.artifactAddress),
                    proguardRules = File(library.proguardRules)
                )
            }
            LibraryL2.LIBRARY_JAVA -> {
                DefaultLmJavaLibrary(
                    artifactAddress = library.artifactAddress,
                    // TODO - expose compile jar vs impl jar?
                    jarFiles = (library.localJars + library.jarFile).map { File(it) },
                    project = library.projectPath,
                    provided = provided,
                    skipped = skipped,
                    resolvedCoordinates = getMavenName(library.artifactAddress)
                )
            }
            LibraryL2.LIBRARY_MODULE -> {
                error("Not yet implemented: library of type ${library.type}")
            }
            else -> {
                error("Unexpected library type ${library.type}")
            }
        }
    }

    private fun getDependencies(
        items: List<GraphItem>,
        graph: DependencyGraphs,
        libraryResolver: LmLibraryResolver
    ): LmDependencyGraph {
        val list = getGraphItems(items, graph, libraryResolver)
        return DefaultLmDependencyGraph(roots = list, libraryResolver = libraryResolver)
    }

    private fun getGraphItems(
        items: List<GraphItem>,
        graph: DependencyGraphs,
        libraryResolver: LmLibraryResolver
    ): List<LmDependency> {
        return when {
            items.isEmpty() -> emptyList()
            else -> items.map {
                getGraphItem(it, graph, libraryResolver)
            }
        }
    }

    private fun getGraphItem(
        graphItem: GraphItem,
        graph: DependencyGraphs,
        libraryResolver: LmLibraryResolver
    ): LmDependency {
        return DefaultLmDependency(
            artifactName = getArtifactName(graphItem),
            artifactAddress = graphItem.artifactAddress,
            requestedCoordinates = graphItem.requestedCoordinates,
            // Deep copy
            dependencies = getGraphItems(
                graphItem.dependencies,
                graph,
                libraryResolver
            ),
            libraryResolver = libraryResolver
        )
    }

    private fun getArtifactName(graphItem: GraphItem): String {
        val artifactAddress = graphItem.artifactAddress
        return getArtifactName(artifactAddress)
    }

    private fun getArtifactName(artifactAddress: String): String {
        val index = artifactAddress.indexOf(':')
        val index2 = artifactAddress.indexOf(':', index + 1)
        return if (index == -1) {
            // not a Maven library, e.g. a local project reference
            artifactAddress
        } else {
            artifactAddress.substring(0, index2)
        }
    }

    private fun getGraphItem(
        library: Library,
        skipProvided: Boolean
    ): LmDependency {
        val artifactAddress = getArtifactAddress(library.resolvedCoordinates)

        libraryResolverMap[artifactAddress] ?: run {
            libraryResolverMap[artifactAddress] = getLibrary(library)
        }

        return DefaultLmDependency(
            artifactName = getArtifactName(artifactAddress),
            artifactAddress = artifactAddress,
            requestedCoordinates = library.requestedCoordinates?.toString(),
            // Deep copy
            dependencies = getGraphItems(
                if (library is AndroidBundle) {
                    library.libraryDependencies + library.javaDependencies
                } else {
                    emptyList()
                },
                skipProvided = skipProvided
            ),
            libraryResolver = libraryResolver
        )
    }

    private fun getGraphItems(
        items: List<Library>,
        skipProvided: Boolean
    ): List<LmDependency> {
        return if (items.isEmpty()) {
            emptyList()
        } else {
            items.mapNotNull {
                if (!skipProvided || !it.isProvided)
                    getGraphItem(it, skipProvided = skipProvided)
                else
                    null
            }
        }
    }

    private fun getDependencies(
        artifact: BaseArtifact
    ): LmDependencies {
        val compileItems = ArrayList<LmDependency>()
        val packagedItems = ArrayList<LmDependency>()
        val dependencies = artifact.dependencies
        for (dependency in dependencies.libraries) {
            if (dependency.isValid()) {
                compileItems.add(getGraphItem(dependency, skipProvided = false))
                if (!dependency.isProvided) {
                    packagedItems.add(getGraphItem(dependency, skipProvided = true))
                }
            }
        }
        for (dependency in dependencies.javaLibraries) {
            if (dependency.isValid()) {
                compileItems.add(getGraphItem(dependency, skipProvided = false))
                if (!dependency.isProvided) {
                    packagedItems.add(getGraphItem(dependency, skipProvided = true))
                }
            }
        }

        // TODO: Module dependencies, but this is only available in 3.1
        // for (module in dependencies.javaModules) {
        // }

        val compileDependencies = DefaultLmDependencyGraph(compileItems, libraryResolver)
        val packageDependencies = DefaultLmDependencyGraph(packagedItems, libraryResolver)

        return DefaultLmDependencies(
            compileDependencies = compileDependencies,
            packageDependencies = packageDependencies,
            libraryResolver = libraryResolver
        )
    }

    private fun getLibrary(library: JavaLibrary): LmLibrary {
        libraryMap[library]?.let { return it }
        val dependencies = ArrayList<LmLibrary>()
        for (javaLibrary in library.dependencies) {
            dependencies.add(getLibrary(javaLibrary))
        }
        val new = DefaultLmJavaLibrary(
            artifactAddress = getArtifactAddress(library.resolvedCoordinates),
            project = library.project,
            jarFiles = listOf(library.jarFile),
            provided = isProvided(library),
            skipped = isSkipped(library),
            resolvedCoordinates = getMavenName(library.resolvedCoordinates)
        )
        libraryMap[library] = new
        return new
    }

    @Suppress("SENSELESS_COMPARISON") // See https://issuetracker.google.com/37124607
    private fun Library.isValid(): Boolean {
        return resolvedCoordinates != null
    }

    private fun getLibrary(library: Library): LmLibrary {
        libraryMap[library]?.let { return it }
        return when (library) {
            is AndroidLibrary -> getLibrary(library)
            is JavaLibrary -> getLibrary(library)
            else -> error("Unexpected builder model library type ${library.javaClass}")
        }
    }

    private fun getLibrary(library: AndroidLibrary): LmLibrary {
        libraryMap[library]?.let { return it }

        @Suppress("DEPRECATION")
        val new = DefaultLmAndroidLibrary(
            manifest = library.manifest,
            jarFiles = library.localJars + library.jarFile,
            folder = library.folder, // Needed for workaround for b/66166521
            resFolder = library.resFolder,
            assetsFolder = library.assetsFolder,
            lintJar = library.lintJar,
            publicResources = library.publicResources,
            symbolFile = library.symbolFile,
            externalAnnotations = library.externalAnnotations,
            project = library.project,
            provided = isProvided(library),
            skipped = isSkipped(library),
            resolvedCoordinates = getMavenName(library.resolvedCoordinates),
            proguardRules = library.proguardRules,
            artifactAddress = getArtifactAddress(library.resolvedCoordinates)
        )

        libraryMap[library] = new

        return new
    }

    private fun isProvided(library: JavaLibrary) =
        try {
            library.isProvided
        } catch (e: Throwable) {
            false
        }

    private fun isProvided(library: AndroidLibrary) =
        try {
            library.isProvided
        } catch (e: Throwable) {
            false
        }

    private fun isSkipped(library: JavaLibrary) =
        try {
            library.isSkipped
        } catch (e: Throwable) {
            false
        }

    private fun isSkipped(library: AndroidLibrary) =
        try {
            library.isSkipped
        } catch (e: Throwable) {
            false
        }

    private fun getArtifact(
        artifact: AndroidArtifact
    ): LmAndroidArtifact {
        return DefaultLmAndroidArtifact(
            applicationId = artifact.applicationId,
            dependencies = getDependencies(artifact),
            generatedSourceFolders = artifact.generatedSourceFolders,
            generatedResourceFolders = artifact.generatedResourceFolders,
            classOutputs = artifact.getClassFolders()
        )
    }

    private fun getArtifact(
        artifact: JavaArtifact
    ): LmJavaArtifact {
        return DefaultLmJavaArtifact(
            dependencies = getDependencies(artifact),
            classFolders = artifact.getClassFolders()
        )
    }

    private fun BaseArtifact.getClassFolders(): List<File> {
        return if (additionalClassesFolders.isEmpty()) {
            listOf(classesFolder)
        } else {
            val folders = ArrayList<File>(additionalClassesFolders.size + 1)
            folders.add(classesFolder)
            folders.addAll(additionalClassesFolders)
            folders
        }
    }

    private fun getBuildType(project: IdeAndroidProject, variant: Variant): BuildType {
        val buildTypeName = variant.buildType
        return project.buildTypes.first { it.buildType.name == buildTypeName }.buildType
    }

    private fun getVariant(
        module: LmModule,
        project: IdeAndroidProject,
        variant: Variant
    ): LmVariant {
        val buildType = getBuildType(project, variant)
        return DefaultLmVariant(
            module = module,
            name = variant.name,
            useSupportLibraryVectorDrawables = useSupportLibraryVectorDrawables(variant),
            mainArtifact = getArtifact(variant.mainArtifact),
            testArtifact = getTestArtifact(variant),
            androidTestArtifact = getAndroidTestArtifact(variant),
            oldVariant = variant,
            `package` = null, // not in the old builder model
            minSdkVersion = variant.mergedFlavor.minSdkVersion?.toAndroidVersion(),
            targetSdkVersion = variant.mergedFlavor.targetSdkVersion?.toAndroidVersion(),
            resValues = getResValues(variant.mergedFlavor, buildType),
            manifestPlaceholders = getPlaceholders(variant.mergedFlavor, buildType),
            resourceConfigurations = getMergedResourceConfigurations(variant),
            proguardFiles = variant.mergedFlavor.proguardFiles + buildType.proguardFiles,
            consumerProguardFiles = variant.mergedFlavor.consumerProguardFiles + buildType.proguardFiles,
            sourceProviders = computeSourceProviders(project, variant),
            testSourceProviders = computeTestSourceProviders(project, variant),
            debuggable = buildType.isDebuggable,
            shrinkable = buildType.isMinifyEnabled,
            libraryResolver = libraryResolver
        )
    }

    private fun getMergedResourceConfigurations(variant: Variant): Collection<String> {
        // Are there any splits that specify densities?
        /* Hotfix for b/148602190
        if (relevantDensities.isEmpty()) {
            AndroidArtifact mainArtifact = variant.getMainArtifact();
            Collection<AndroidArtifactOutput> outputs = mainArtifact.getOutputs();
            for (AndroidArtifactOutput output : outputs) {
                final String DENSITY_NAME = VariantOutput.FilterType.DENSITY.name();
                if (output.getFilterTypes().contains(DENSITY_NAME)) {
                    for (FilterData data : output.getFilters()) {
                        if (DENSITY_NAME.equals(data.getFilterType())) {
                            relevantDensities.add(data.getIdentifier());
                        }
                    }
                }
            }
        }
        */
        return variant.mergedFlavor.resourceConfigurations
    }

    private fun getAndroidTestArtifact(variant: Variant): LmAndroidArtifact? {
        val artifact = variant.extraAndroidArtifacts.firstOrNull {
            it.name == AndroidProject.ARTIFACT_ANDROID_TEST
        } ?: return null
        return getArtifact(artifact)
    }

    private fun getTestArtifact(variant: Variant): LmJavaArtifact? {
        val artifact = variant.extraJavaArtifacts.firstOrNull {
            it.name == AndroidProject.ARTIFACT_UNIT_TEST
        } ?: return null
        return getArtifact(artifact)
    }

    private fun computeSourceProviders(
        project: IdeAndroidProject,
        variant: Variant
    ): List<LmSourceProvider> {
        val providers = mutableListOf<LmSourceProvider>()

        // Instead of just
        //  providers.add(getSourceProvider(project.defaultConfig.sourceProvider))
        // we need to merge in any Kotlin source folders for now
        var mainProvider: LmSourceProvider? = null
        val kotlinSourceFolders = kotlinSourceFolderLookup?.invoke(variant.name)
            ?: emptyList()
        if (kotlinSourceFolders.isNotEmpty()) {
            val provider = project.defaultConfig.sourceProvider
            if (!provider.javaDirectories.containsAll(kotlinSourceFolders)) {
                val extra = kotlinSourceFolders.toMutableList()
                extra.removeAll(provider.javaDirectories)
                if (extra.isNotEmpty()) {
                    mainProvider = DefaultLmSourceProvider(
                        manifestFile = provider.manifestFile,
                        javaDirectories = provider.javaDirectories + extra,
                        resDirectories = provider.resDirectories,
                        assetsDirectories = provider.assetsDirectories,
                        unitTestOnly = false,
                        instrumentationTestOnly = false,
                        debugOnly = false
                    )
                }
            }
        }
        if (mainProvider == null) {
            mainProvider = getSourceProvider(project.defaultConfig.sourceProvider)
        }
        providers.add(mainProvider)

        for (flavorContainer in project.productFlavors) {
            if (variant.productFlavors.contains(flavorContainer.productFlavor.name)) {
                providers.add(getSourceProvider(flavorContainer.sourceProvider))
            }
        }

        val mainArtifact = variant.mainArtifact
        mainArtifact.multiFlavorSourceProvider?.let { sourceProvider ->
            providers.add(getSourceProvider(sourceProvider))
        }

        var debugVariant = false
        for (buildTypeContainer in project.buildTypes) {
            if (variant.buildType == buildTypeContainer.buildType.name) {
                debugVariant = buildTypeContainer.buildType.isDebuggable
                providers.add(
                    getSourceProvider(
                        provider = buildTypeContainer.sourceProvider,
                        debugOnly = debugVariant
                    )
                )
            }
        }

        mainArtifact.variantSourceProvider?.let { sourceProvider ->
            providers.add(
                getSourceProvider(
                    provider = sourceProvider,
                    debugOnly = debugVariant
                )
            )
        }
        return providers
    }

    private fun SourceProviderContainer.isTest(): Boolean {
        return isUnitTest() || isInstrumentationTest()
    }

    private fun SourceProviderContainer.isUnitTest(): Boolean {
        return AndroidProject.ARTIFACT_UNIT_TEST == artifactName
    }

    private fun SourceProviderContainer.isInstrumentationTest(): Boolean {
        return AndroidProject.ARTIFACT_ANDROID_TEST == artifactName
    }

    /**
     * TODO: This is not correct; this method simultaneously returns both the
     * unit test and instrumentation test folders. These two are not normally
     * combined in the build system (they can contain conflicting definitions of
     * the class for example). Lint uses this method in a couple of different
     * ways: (1) to find all the source files it must analyze in turn (for that
     * purpose, this method is okay), and (2) to set up the class path in the
     * CLI setup for PSI. This is problematic, but solving it properly is going
     * to take more work (e.g. we need to do separate handling for each test
     * target), and since this is the way lint has always worked we're leaving
     * this brokenness here for now until we address this with the dependency
     * graph rewrite.
     */
    private fun computeTestSourceProviders(
        project: IdeAndroidProject,
        variant: Variant
    ): List<LmSourceProvider> {
        val providers = mutableListOf<LmSourceProvider>()

        for (extra in project.defaultConfig.extraSourceProviders) {
            if (extra.isTest()) {
                providers.add(getSourceProvider(extra))
            }
        }

        for (flavorContainer in project.productFlavors) {
            if (variant.productFlavors.contains(flavorContainer.productFlavor.name)) {
                for (extra in flavorContainer.extraSourceProviders) {
                    if (extra.isTest()) {
                        providers.add(getSourceProvider(extra))
                    }
                }
            }
        }

        for (buildTypeContainer in project.buildTypes) {
            if (variant.buildType == buildTypeContainer.buildType.name) {
                for (extra in buildTypeContainer.extraSourceProviders) {
                    if (extra.isTest()) {
                        providers.add(
                            getSourceProvider(
                                providerContainer = extra,
                                debugOnly = buildTypeContainer.buildType.isDebuggable
                            )
                        )
                    }
                }
            }
        }

        return providers
    }

    /** Merges place holders from the merged product flavor and the build type */
    private fun getPlaceholders(
        mergedFlavor: ProductFlavor,
        buildType: BuildType
    ): Map<String, String> {
        return if (mergedFlavor.manifestPlaceholders.isEmpty()) {
            if (buildType.manifestPlaceholders.isEmpty()) {
                emptyMap()
            } else {
                buildType.manifestPlaceholders.mapValues { it.value.toString() }
            }
        } else if (buildType.manifestPlaceholders.isEmpty()) {
            mergedFlavor.manifestPlaceholders.mapValues { it.value.toString() }
        } else {
            val map = mutableMapOf<String, String>()
            mergedFlavor.manifestPlaceholders.forEach { map[it.key] = it.value.toString() }
            buildType.manifestPlaceholders.forEach { map[it.key] = it.value.toString() }
            map
        }
    }

    private fun getSourceProvider(
        providerContainer: SourceProviderContainer,
        debugOnly: Boolean = false
    ): LmSourceProvider {
        val provider = providerContainer.sourceProvider
        return DefaultLmSourceProvider(
            manifestFile = provider.manifestFile,
            javaDirectories = provider.javaDirectories,
            resDirectories = provider.resDirectories,
            assetsDirectories = provider.assetsDirectories,
            unitTestOnly = providerContainer.isUnitTest(),
            instrumentationTestOnly = providerContainer.isInstrumentationTest(),
            debugOnly = debugOnly
        )
    }

    private fun getSourceProvider(
        provider: SourceProvider,
        unitTestOnly: Boolean = false,
        instrumentationTestOnly: Boolean = false,
        debugOnly: Boolean = false
    ): LmSourceProvider {
        return DefaultLmSourceProvider(
            manifestFile = provider.manifestFile,
            javaDirectories = provider.javaDirectories,
            resDirectories = provider.resDirectories,
            assetsDirectories = provider.assetsDirectories,
            unitTestOnly = unitTestOnly,
            instrumentationTestOnly = instrumentationTestOnly,
            debugOnly = debugOnly
        )
    }

    private fun ClassField.toResourceField(): LmResourceField {
        return DefaultLmResourceField(
            type = type,
            name = name,
            value = value
        )
    }

    private fun getResValues(
        mergedFlavor: ProductFlavor,
        buildType: BuildType
    ): Map<String, LmResourceField> {
        return if (mergedFlavor.resValues.isEmpty()) {
            if (buildType.resValues.isEmpty()) {
                emptyMap()
            } else {
                buildType.resValues.mapValues { it.value.toResourceField() }
            }
        } else if (buildType.resValues.isEmpty()) {
            mergedFlavor.resValues.mapValues { it.value.toResourceField() }
        } else {
            val map = mutableMapOf<String, LmResourceField>()
            mergedFlavor.resValues.forEach { map[it.key] = it.value.toResourceField() }
            buildType.resValues.forEach { map[it.key] = it.value.toResourceField() }
            map
        }
    }

    private fun getBuildFeatures(
        project: IdeAndroidProject,
        gradleVersion: GradleVersion?
    ): LmBuildFeatures {
        return DefaultLmBuildFeatures(
            viewBinding = usesViewBinding(project, gradleVersion),
            coreLibraryDesugaringEnabled = project.javaCompileOptions.isCoreLibraryDesugaringEnabled,
            namespacingMode = getNamespacingMode(project)

        )
    }

    private fun usesViewBinding(
        project: IdeAndroidProject,
        gradleVersion: GradleVersion?
    ): Boolean {
        return if (gradleVersion != null && gradleVersion.isAtLeast(3, 6, 0)) {
            project.viewBindingOptions?.isEnabled == true
        } else {
            false
        }
    }

    private fun isNeverShrinking(project: IdeAndroidProject): Boolean {
        return project.buildTypes.none { it.buildType.isMinifyEnabled }
    }

    private fun useSupportLibraryVectorDrawables(variant: Variant): Boolean {
        return try {
            variant.mergedFlavor.vectorDrawables.useSupportLibrary == true
        } catch (e: Throwable) {
            false
        }
    }

    private fun getGradleVersion(project: IdeAndroidProject): GradleVersion? {
        return GradleVersion.tryParse(project.modelVersion)
    }

    private fun getNamespacingMode(project: IdeAndroidProject): LmNamespacingMode {
        return when (project.aaptOptions.namespacing) {
            AaptOptions.Namespacing.DISABLED -> LmNamespacingMode.DISABLED
            AaptOptions.Namespacing.REQUIRED -> LmNamespacingMode.REQUIRED
        }
    }

    private fun getMavenName(androidProject: IdeAndroidProject): LmMavenName? {
        val groupId = androidProject.groupId ?: return null
        return DefaultLmMavenName(groupId, androidProject.name, "")
    }

    private fun getMavenName(c: MavenCoordinates): LmMavenName {
        @Suppress("USELESS_ELVIS") // See https://issuetracker.google.com/37124607
        val groupId = c.groupId ?: ""
        return DefaultLmMavenName(groupId, c.artifactId, c.version)
    }

    private fun getMavenName(artifactAddress: String): LmMavenName {
        val artifactIndex = artifactAddress.indexOf(':')
        val versionIndex = artifactAddress.indexOf(':', artifactIndex + 1)
        val versionEnd = artifactAddress.indexOf(':', versionIndex + 1).let {
            if (it == -1) artifactAddress.length else it
        }
        val group = artifactAddress.substring(0, artifactIndex)
        val artifact = artifactAddress.substring(artifactIndex + 1, versionIndex)
        val version = artifactAddress.substring(versionIndex + 1, versionEnd)
        return DefaultLmMavenName(group, artifact, version)
    }

    private fun getArtifactAddress(c: MavenCoordinates): String {
        return "${c.groupId}:${c.artifactId}:${c.version}"
    }

    private fun getLintOptions(project: IdeAndroidProject): LmLintOptions =
        getLintOptions(project.lintOptions)

    private fun getLintOptions(options: IdeLintOptions): LmLintOptions {
        val severityOverrides = options.severityOverrides?.let { source ->
            val map = LinkedHashMap<String, LmSeverity>()
            for ((id, severityInt) in source.entries) {
                map[id] = getSeverity(severityInt)
            }
            map
        }

        return DefaultLmLintOptions(
            // Not all DSL LintOptions; only some are actually accessed from outside
            // the Gradle/CLI configuration currently
            baselineFile = options.baselineFile,
            lintConfig = options.lintConfig,
            severityOverrides = severityOverrides,
            checkTestSources = options.isCheckTestSources,
            checkDependencies = options.isCheckDependencies,
            disable = options.disable,
            enable = options.enable,
            check = options.check,
            abortOnError = options.isAbortOnError,
            absolutePaths = options.isAbsolutePaths,
            noLines = options.isNoLines,
            quiet = options.isQuiet,
            checkAllWarnings = options.isCheckAllWarnings,
            ignoreWarnings = options.isIgnoreWarnings,
            warningsAsErrors = options.isWarningsAsErrors,
            ignoreTestSources = options.isIgnoreTestSources,
            checkGeneratedSources = options.isCheckGeneratedSources,
            explainIssues = options.isExplainIssues,
            showAll = options.isShowAll,
            textReport = options.textReport,
            textOutput = options.textOutput,
            htmlReport = options.htmlReport,
            htmlOutput = options.htmlOutput,
            xmlReport = options.xmlReport,
            xmlOutput = options.xmlOutput,
            checkReleaseBuilds = options.isCheckReleaseBuilds
        )
    }

    private fun ApiVersion.toAndroidVersion(): AndroidVersion? {
        return AndroidVersion(apiLevel, codename)
    }

    /**
     * An [LmModule] which holds on to the underlying builder-model and lazily constructs
     * parts of the model less likely to be needed (such as all the variants). This is particularly
     * useful when lint is running on a subset of checks on the fly in the editor in the IDE
     * for example.
     */
    inner class LazyLmModule(
        override val loader: LmModuleLoader,
        private val project: IdeAndroidProject,
        override val dir: File,
        override val gradleVersion: GradleVersion?
    ) : LmModule {
        override val modulePath: String
            get() = project.name
        override val type: LmModuleType
            get() = getModuleType(project.projectType)
        override val mavenName: LmMavenName?
            get() = getMavenName(project)
        override val buildFolder: File
            get() = project.buildFolder
        override val resourcePrefix: String?
            get() = project.resourcePrefix
        override val dynamicFeatures: Collection<String>
            get() = project.dynamicFeatures
        override val bootClassPath: List<File>
            get() = project.bootClasspath.map { File(it) }
        override val javaSourceLevel: String
            get() = project.javaCompileOptions.sourceCompatibility
        override val compileTarget: String
            get() = project.compileTarget
        override val oldProject: IdeAndroidProject?
            get() = project
        override val lintRuleJars: List<File> = project.getLintRuleJarsForAnyAgpVersion()

        override fun neverShrinking(): Boolean {
            return isNeverShrinking(project)
        }

        // Lazy properties

        private var _lintOptions: LmLintOptions? = null
        override val lintOptions: LmLintOptions
            get() = _lintOptions ?: getLintOptions(project).also { _lintOptions = it }

        private var _buildFeatures: LmBuildFeatures? = null
        override val buildFeatures: LmBuildFeatures
            get() = _buildFeatures
                ?: getBuildFeatures(project, gradleVersion).also { _buildFeatures = it }

        private var _variants: List<LmVariant>? = null
        override val variants: List<LmVariant>
            // Lazily initialize the _variants property, reusing any already
            // looked up variants from the [variantMap] and also populating that map
            // for latest retrieval
            get() = _variants
                ?: project.variants.map { variant ->
                    // (Not just using findVariant since that searches linearly
                    // through variant list to match by name)
                    variantMap[variant.name]
                        ?: LazyLmVariant(this, project, variant, libraryResolver).also {
                            variantMap[it.name] = it
                        }
                }.also {
                    _variants = it
                }

        /** Map from variant name to variant */
        private val variantMap = mutableMapOf<String, LmVariant>()

        override fun findVariant(name: String): LmVariant? = variantMap[name] ?: run {
            val buildVariant = project.variants.firstOrNull { it.name == name }
            buildVariant?.let {
                LazyLmVariant(this, project, it, libraryResolver)
            }?.also {
                variantMap[name] = it
            }
        }

        override fun defaultVariant(): LmVariant? {
            return project.variants.firstOrNull()?.let { findVariant(it.name) }
        }
    }

    inner class LazyLmVariant(
        override val module: LmModule,
        private val project: IdeAndroidProject,
        private val variant: Variant,
        override val libraryResolver: LmLibraryResolver
    ) : LmVariant {
        private val buildType = getBuildType(project, variant)

        override val name: String
            get() = variant.name
        override val useSupportLibraryVectorDrawables: Boolean
            get() = useSupportLibraryVectorDrawables(variant)
        override val oldVariant: Variant?
            get() = variant
        override val `package`: String?
            get() = null // no in the old builder model
        override val minSdkVersion: AndroidVersion?
            get() = variant.mergedFlavor.minSdkVersion?.toAndroidVersion()
        override val targetSdkVersion: AndroidVersion?
            get() = variant.mergedFlavor.targetSdkVersion?.toAndroidVersion()
        override val resourceConfigurations: Collection<String>
            get() = variant.mergedFlavor.resourceConfigurations
        override val debuggable: Boolean
            get() = buildType.isDebuggable
        override val shrinkable: Boolean
            get() = buildType.isMinifyEnabled

        // Lazy properties

        private var _sourceProviders: List<LmSourceProvider>? = null
        override val sourceProviders: List<LmSourceProvider>
            get() = _sourceProviders ?: computeSourceProviders(
                project,
                variant
            ).also { _sourceProviders = it }

        private var _testSourceProviders: List<LmSourceProvider>? = null
        override val testSourceProviders: List<LmSourceProvider>
            get() = _testSourceProviders ?: computeTestSourceProviders(
                project,
                variant
            ).also { _testSourceProviders = it }

        private var _resValues: Map<String, LmResourceField>? = null
        override val resValues: Map<String, LmResourceField>
            get() = _resValues
                ?: getResValues(variant.mergedFlavor, buildType).also { _resValues = it }

        private var _manifestPlaceholders: Map<String, String>? = null
        override val manifestPlaceholders: Map<String, String>
            get() = _manifestPlaceholders
                ?: getPlaceholders(variant.mergedFlavor, buildType).also {
                    _manifestPlaceholders = it
                }

        private var _mainArtifact: LmAndroidArtifact? = null
        override val mainArtifact: LmAndroidArtifact
            get() = _mainArtifact
                ?: getArtifact(variant.mainArtifact).also { _mainArtifact = it }

        private var _testArtifact: LmJavaArtifact? = null
        override val testArtifact: LmJavaArtifact?
            get() = _testArtifact ?: getTestArtifact(variant).also { _testArtifact = it }

        private var _androidTestArtifact: LmAndroidArtifact? = null
        override val androidTestArtifact: LmAndroidArtifact?
            get() = _androidTestArtifact
                ?: getAndroidTestArtifact(variant).also { _androidTestArtifact = it }

        private var _proguardFiles: Collection<File>? = null
        override val proguardFiles: Collection<File>
            get() = _proguardFiles
                ?: (variant.mergedFlavor.proguardFiles + buildType.proguardFiles).also {
                    _proguardFiles = it
                }

        private var _consumerProguardFiles: Collection<File>? = null
        override val consumerProguardFiles: Collection<File>
            get() = _consumerProguardFiles
                ?: (variant.mergedFlavor.consumerProguardFiles + buildType.consumerProguardFiles).also {
                    _consumerProguardFiles = it
                }
    }

    companion object {
        private const val CACHE_KEY = "lint-model"

        /**
         * Returns the [LmModuleType] for the given type ID. Type ids must be one of the values defined by
         * AndroidProjectTypes.PROJECT_TYPE_*.
         */
        @JvmStatic
        fun getModuleType(typeId: Int): LmModuleType {
            return when (typeId) {
                AndroidProjectTypes.PROJECT_TYPE_APP -> LmModuleType.APP
                AndroidProjectTypes.PROJECT_TYPE_LIBRARY -> LmModuleType.LIBRARY
                AndroidProjectTypes.PROJECT_TYPE_TEST -> LmModuleType.TEST
                AndroidProjectTypes.PROJECT_TYPE_INSTANTAPP -> LmModuleType.INSTANT_APP
                AndroidProjectTypes.PROJECT_TYPE_FEATURE -> LmModuleType.FEATURE
                AndroidProjectTypes.PROJECT_TYPE_DYNAMIC_FEATURE -> LmModuleType.DYNAMIC_FEATURE
                // 999: Special value defined in GradleModelMocker#PROJECT_TYPE_JAVA_LIBRARY
                999 -> LmModuleType.JAVA_LIBRARY
                else -> throw IllegalArgumentException("The value $typeId is not a valid project type ID")
            }
        }

        private fun getSeverity(severity: Int): LmSeverity =
            when (severity) {
                LintOptions.SEVERITY_FATAL -> LmSeverity.FATAL
                LintOptions.SEVERITY_ERROR -> LmSeverity.ERROR
                LintOptions.SEVERITY_WARNING -> LmSeverity.WARNING
                LintOptions.SEVERITY_INFORMATIONAL -> LmSeverity.INFORMATIONAL
                LintOptions.SEVERITY_IGNORE -> LmSeverity.IGNORE
                LintOptions.SEVERITY_DEFAULT_ENABLED -> LmSeverity.WARNING
                else -> LmSeverity.IGNORE
            }

        @Suppress("unused") // Used from the lint-gradle module in AGP
        @JvmStatic
        fun getLintOptions(options: LintOptions): LmLintOptions {
            val severityOverrides = options.severityOverrides?.let { source ->
                val map = LinkedHashMap<String, LmSeverity>()
                for ((id, severityInt) in source.entries) {
                    map[id] = getSeverity(severityInt)
                }
                map
            }

            return DefaultLmLintOptions(
                // Not all DSL LintOptions; only some are actually accessed from outside
                // the Gradle/CLI configuration currently
                baselineFile = options.baselineFile,
                lintConfig = options.lintConfig,
                severityOverrides = severityOverrides,
                checkTestSources = options.isCheckTestSources,
                checkDependencies = options.isCheckDependencies,
                disable = options.disable,
                enable = options.enable,
                check = options.check,
                abortOnError = options.isAbortOnError,
                absolutePaths = options.isAbsolutePaths,
                noLines = options.isNoLines,
                quiet = options.isQuiet,
                checkAllWarnings = options.isCheckAllWarnings,
                ignoreWarnings = options.isIgnoreWarnings,
                warningsAsErrors = options.isWarningsAsErrors,
                ignoreTestSources = options.isIgnoreTestSources,
                checkGeneratedSources = options.isCheckGeneratedSources,
                explainIssues = options.isExplainIssues,
                showAll = options.isShowAll,
                textReport = options.textReport,
                textOutput = options.textOutput,
                htmlReport = options.htmlReport,
                htmlOutput = options.htmlOutput,
                xmlReport = options.xmlReport,
                xmlOutput = options.xmlOutput,
                checkReleaseBuilds = options.isCheckReleaseBuilds
            )
        }
    }
}
