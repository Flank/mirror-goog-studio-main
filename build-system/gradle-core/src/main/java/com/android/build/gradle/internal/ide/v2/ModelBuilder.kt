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

package com.android.build.gradle.internal.ide.v2

import com.android.SdkConstants
import com.android.Version
import com.android.build.api.component.impl.AndroidTestImpl
import com.android.build.api.component.impl.ComponentImpl
import com.android.build.api.dsl.ApkSigningConfig
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.BuildFeatures
import com.android.build.api.dsl.BuildType
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.DefaultConfig
import com.android.build.api.dsl.ProductFlavor
import com.android.build.api.dsl.TestExtension
import com.android.build.api.variant.AndroidTest
import com.android.build.api.variant.Component
import com.android.build.api.variant.HasAndroidTest
import com.android.build.api.variant.HasTestFixtures
import com.android.build.api.variant.UnitTest
import com.android.build.api.variant.impl.TestVariantImpl
import com.android.build.api.variant.impl.VariantImpl
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.ApplicationCreationConfig
import com.android.build.gradle.internal.component.ConsumableCreationConfig
import com.android.build.gradle.internal.component.DynamicFeatureCreationConfig
import com.android.build.gradle.internal.component.LibraryCreationConfig
import com.android.build.gradle.internal.component.TestComponentCreationConfig
import com.android.build.gradle.internal.component.TestVariantCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import com.android.build.gradle.internal.dsl.DynamicFeatureExtension
import com.android.build.gradle.internal.errors.SyncIssueReporter
import com.android.build.gradle.internal.errors.SyncIssueReporterImpl.GlobalSyncIssueService
import com.android.build.gradle.internal.ide.DependencyFailureHandler
import com.android.build.gradle.internal.ide.ModelBuilder
import com.android.build.gradle.internal.ide.dependencies.ArtifactCollectionsInputs
import com.android.build.gradle.internal.ide.dependencies.ArtifactCollectionsInputsImpl
import com.android.build.gradle.internal.ide.dependencies.BuildMapping
import com.android.build.gradle.internal.ide.dependencies.FullDependencyGraphBuilder
import com.android.build.gradle.internal.ide.dependencies.LibraryService
import com.android.build.gradle.internal.ide.dependencies.LibraryServiceImpl
import com.android.build.gradle.internal.ide.dependencies.computeBuildMapping
import com.android.build.gradle.internal.ide.dependencies.getVariantName
import com.android.build.gradle.internal.ide.verifyIDEIsNotOld
import com.android.build.gradle.internal.lint.getLocalCustomLintChecksForModel
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.PROVIDED_CLASSPATH
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.JAVAC
import com.android.build.gradle.internal.scope.InternalArtifactType.UNIT_TEST_CONFIG_DIRECTORY
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.AnchorTaskNames
import com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask
import com.android.build.gradle.internal.utils.toImmutableSet
import com.android.build.gradle.internal.variant.VariantModel
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptionService
import com.android.build.gradle.options.ProjectOptions
import com.android.build.gradle.tasks.sync.AbstractVariantModelTask
import com.android.builder.core.VariantTypeImpl
import com.android.builder.errors.IssueReporter
import com.android.builder.model.v2.ModelSyncFile
import com.android.builder.model.SyncIssue
import com.android.builder.model.v2.ide.AndroidGradlePluginProjectFlags.BooleanFlag
import com.android.builder.model.v2.ide.ArtifactDependencies
import com.android.builder.model.v2.ide.BasicArtifact
import com.android.builder.model.v2.ide.BundleInfo
import com.android.builder.model.v2.ide.CodeShrinker
import com.android.builder.model.v2.ide.JavaArtifact
import com.android.builder.model.v2.ide.ProjectType
import com.android.builder.model.v2.ide.SourceSetContainer
import com.android.builder.model.v2.ide.TestInfo
import com.android.builder.model.v2.ide.TestedTargetVariant
import com.android.builder.model.v2.models.AndroidDsl
import com.android.builder.model.v2.models.AndroidProject
import com.android.builder.model.v2.models.BasicAndroidProject
import com.android.builder.model.v2.models.BuildMap
import com.android.builder.model.v2.models.ModelBuilderParameter
import com.android.builder.model.v2.models.ProjectSyncIssues
import com.android.builder.model.v2.models.VariantDependencies
import com.android.builder.model.v2.models.Versions
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import org.gradle.api.Project
import org.gradle.api.invocation.Gradle
import org.gradle.tooling.provider.model.ParameterizedToolingModelBuilder
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import javax.xml.namespace.QName
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamException
import javax.xml.stream.events.EndElement

class ModelBuilder<
        BuildFeaturesT : BuildFeatures,
        BuildTypeT : BuildType,
        DefaultConfigT : DefaultConfig,
        ProductFlavorT : ProductFlavor,
        SigningConfigT : ApkSigningConfig,
        ExtensionT : CommonExtension<
                BuildFeaturesT,
                BuildTypeT,
                DefaultConfigT,
                ProductFlavorT>>(
    private val project: Project,
    private val globalScope: GlobalScope,
    private val projectOptions: ProjectOptions,
    private val variantModel: VariantModel,
    private val extension: ExtensionT,
    private val syncIssueReporter: SyncIssueReporter,
    private val projectType: ProjectType
) : ParameterizedToolingModelBuilder<ModelBuilderParameter> {

    override fun getParameterType(): Class<ModelBuilderParameter> {
        return ModelBuilderParameter::class.java
    }

    override fun canBuild(className: String): Boolean {
        return className == Versions::class.java.name
                || className == BuildMap::class.java.name
                || className == BasicAndroidProject::class.java.name
                || className == AndroidProject::class.java.name
                || className == AndroidDsl::class.java.name
                || className == VariantDependencies::class.java.name
                || className == ProjectSyncIssues::class.java.name
    }

    /**
     * Non-parameterized model query. Valid for all but the VariantDependencies model
     */
    override fun buildAll(className: String, project: Project): Any = when (className) {
        Versions::class.java.name -> buildModelVersions()
        BuildMap::class.java.name -> buildBuildMap(project)
        BasicAndroidProject::class.java.name -> buildBasicAndroidProjectModel(project)
        AndroidProject::class.java.name -> buildAndroidProjectModel(project)
        AndroidDsl::class.java.name -> buildAndroidDslModel(project)
        ProjectSyncIssues::class.java.name -> buildProjectSyncIssueModel(project)
        VariantDependencies::class.java.name -> throw RuntimeException(
            "Please use parameterized Tooling API to obtain VariantDependencies model."
        )
        else -> throw RuntimeException("Does not support model '$className'")
    }

    /**
     * Non-parameterized model query. Valid only for the VariantDependencies model
     */
    override fun buildAll(
        className: String,
        parameter: ModelBuilderParameter,
        project: Project
    ): Any? = when (className) {
        VariantDependencies::class.java.name -> buildVariantDependenciesModel(project, parameter)
        Versions::class.java.name,
        BuildMap::class.java.name,
        AndroidProject::class.java.name,
        AndroidDsl::class.java.name,
        ProjectSyncIssues::class.java.name -> throw RuntimeException(
            "Please use non-parameterized Tooling API to obtain $className model."
        )
        else -> throw RuntimeException("Does not support model '$className'")
    }

    private fun buildModelVersions(): Versions {
        return VersionsImpl(
            basicAndroidProject = VersionImpl(0, 1),
            androidProject = VersionImpl(0, 1),
            androidDsl = VersionImpl(0, 1),
            variantDependencies = VersionImpl(0, 1),
            nativeModule = VersionImpl(0, 1),
            agp = Version.ANDROID_GRADLE_PLUGIN_VERSION
        )
    }

    private fun buildBuildMap(project: Project): BuildMap = BuildMapImpl(getBuildMap(project))

    /**
     * Indicates the dimensions used for a variant
     */
    private data class DimensionInformation(
        val buildTypes: Set<String>,
        val flavors: Set<Pair<String, String>>
    ) {
        fun isNotEmpty(): Boolean = buildTypes.isNotEmpty() || flavors.isNotEmpty()

        companion object {
            fun createFrom(components: Collection<Component>): DimensionInformation {
                val buildTypes = mutableSetOf<String>()
                val flavors = mutableSetOf<Pair<String, String>>()

                for (component in components) {
                    component.buildType?.let { buildTypes.add(it) }
                    flavors.addAll(component.productFlavors)
                }

                return DimensionInformation(buildTypes, flavors)
            }
        }
    }

    private fun buildBasicAndroidProjectModel(project: Project): BasicAndroidProject {
        // Cannot be injected, as the project might not be the same as the project used to construct
        // the model builder e.g. when lint explicitly builds the model.
        val projectOptions = getBuildService<ProjectOptionService>(project.gradle.sharedServices)
            .get().projectOptions

        // FIXME: remove?
        verifyIDEIsNotOld(projectOptions)

        val sdkSetupCorrectly = globalScope.versionedSdkLoader.get().sdkSetupCorrectly.get()

        // Get the boot classpath. This will ensure the target is configured.
        val bootClasspath = if (sdkSetupCorrectly) {
            globalScope.filteredBootClasspath.get().map { it.asFile }
        } else {
            // SDK not set up, error will be reported as a sync issue.
            emptyList()
        }

        val variantInputs = variantModel.inputs

        val variants = variantModel.variants

        // compute for each main, androidTest, unitTest and testFixtures which buildType and flavors
        // they applied to. This will allow excluding from the model sourcesets that are not
        // used by any of them.
        // Not doing this is confusing to users as they see folders marked as source that aren't
        // used by anything.
        val variantDimensionInfo = DimensionInformation.createFrom(variants)
        val androidTests = DimensionInformation.createFrom(variantModel.testComponents.filterIsInstance<AndroidTest>())
        val unitTests = DimensionInformation.createFrom(variantModel.testComponents.filterIsInstance<UnitTest>())
        val testFixtures = DimensionInformation.createFrom(variants.mapNotNull { it.testFixturesComponent })

        // for now grab the first buildFeatureValues as they cannot be different.
        val buildFeatures = variantModel.buildFeatures

        // gather the default config
        val defaultConfigData = variantInputs.defaultConfigData
        val defaultConfig = if (variantDimensionInfo.isNotEmpty()) {
            SourceSetContainerImpl(
                sourceProvider = defaultConfigData.sourceSet.convert(buildFeatures),
                androidTestSourceProvider = defaultConfigData.getTestSourceSet(VariantTypeImpl.ANDROID_TEST)
                    ?.takeIf { androidTests.isNotEmpty() }
                    ?.convert(buildFeatures),
                unitTestSourceProvider = defaultConfigData.getTestSourceSet(VariantTypeImpl.UNIT_TEST)
                    ?.takeIf { unitTests.isNotEmpty() }
                    ?.convert(buildFeatures),
                testFixturesSourceProvider = defaultConfigData.testFixturesSourceSet
                    ?.takeIf { testFixtures.isNotEmpty() }
                    ?.convert(buildFeatures)
            )
        } else null

        // gather all the build types
        val buildTypes = mutableListOf<SourceSetContainer>()
        for (buildType in variantInputs.buildTypes.values) {
            val buildTypeName = buildType.buildType.name

            if (variantDimensionInfo.buildTypes.contains(buildTypeName)) {
                buildTypes.add(
                    SourceSetContainerImpl(
                        sourceProvider = buildType.sourceSet.convert(buildFeatures),
                        androidTestSourceProvider = buildType.getTestSourceSet(VariantTypeImpl.ANDROID_TEST)
                            ?.takeIf { androidTests.buildTypes.contains(buildTypeName) }
                            ?.convert(buildFeatures),
                        unitTestSourceProvider = buildType.getTestSourceSet(VariantTypeImpl.UNIT_TEST)
                            ?.takeIf { unitTests.buildTypes.contains(buildTypeName) }
                            ?.convert(buildFeatures),
                        testFixturesSourceProvider = buildType.testFixturesSourceSet
                            ?.takeIf { testFixtures.buildTypes.contains(buildTypeName) }
                            ?.convert(buildFeatures)
                    )
                )
            }
        }

        // gather product flavors
        val productFlavors = mutableListOf<SourceSetContainer>()
        for (flavor in variantInputs.productFlavors.values) {
            val flavorDimensionName = flavor.productFlavor.dimension to flavor.productFlavor.name

            if (variantDimensionInfo.flavors.contains(flavorDimensionName)) {
                productFlavors.add(
                    SourceSetContainerImpl(
                        sourceProvider = flavor.sourceSet.convert(buildFeatures),
                        androidTestSourceProvider = flavor.getTestSourceSet(VariantTypeImpl.ANDROID_TEST)
                            ?.takeIf { androidTests.flavors.contains(flavorDimensionName) }
                            ?.convert(buildFeatures),
                        unitTestSourceProvider = flavor.getTestSourceSet(VariantTypeImpl.UNIT_TEST)
                            ?.takeIf { unitTests.flavors.contains(flavorDimensionName) }
                            ?.convert(buildFeatures),
                        testFixturesSourceProvider = flavor.testFixturesSourceSet
                            ?.takeIf { testFixtures.flavors.contains(flavorDimensionName) }
                            ?.convert(buildFeatures)
                    )
                )
            }
        }

        // gather variants
        val variantList = variants.map {
            createBasicVariant(it, buildFeatures)
        }

        return BasicAndroidProjectImpl(
            path = project.path,
            buildName = getBuildName(project),
            buildFolder = project.layout.buildDirectory.get().asFile,

            projectType = projectType,

            mainSourceSet = defaultConfig,
            buildTypeSourceSets = buildTypes,
            productFlavorSourceSets = productFlavors,

            variants = variantList,

            bootClasspath = bootClasspath,
        )
    }

    private fun buildAndroidProjectModel(project: Project): AndroidProject {
        val variants = variantModel.variants

        // Keep track of the result of parsing each manifest for instant app value.
        // This prevents having to reparse the
        val instantAppResultMap = mutableMapOf<File, Boolean>()

        // gather variants
        var namespace: String? = null
        var androidTestNamespace: String? = null
        var testFixturesNamespace: String? = null
        val variantList = variants.map {
            namespace = it.namespace.get()
            if (androidTestNamespace == null && it is HasAndroidTest) {
                (it.androidTest as? TestComponentCreationConfig)?.let { androidTest ->
                    // TODO(b/176931684) Use AndroidTest.namespace instead after we stop
                    //  supporting using applicationId to namespace the test component R class.
                    androidTestNamespace = androidTest.namespaceForR.get()
                }
            }
            if (testFixturesNamespace == null && it is HasTestFixtures) {
                testFixturesNamespace = it.testFixtures?.namespace?.get()
            }

            createVariant(it, instantAppResultMap)
        }

        return AndroidProjectImpl(
            namespace = namespace ?: "",
            androidTestNamespace = androidTestNamespace,
            testFixturesNamespace = testFixturesNamespace,

            variants = variantList,

            javaCompileOptions = extension.compileOptions.convert(),
            resourcePrefix = extension.resourcePrefix,
            dynamicFeatures = (extension as? BaseAppModuleExtension)?.dynamicFeatures?.toImmutableSet(),
            viewBindingOptions = ViewBindingOptionsImpl(
                variantModel.variants.any { it.buildFeatures.viewBinding }
            ),

            flags = getFlags(),
            lintChecksJars = getLocalCustomLintChecksForModel(project, syncIssueReporter),
        )
    }
    /**
     * Returns the current build name
     */
    private fun getBuildName(project: Project): String {
        val currentGradle = project.gradle
        val parentGradle = currentGradle.parent

        return if (parentGradle != null) {
            // search for the parent included builds for the current gradle, matching by the
            // root dir
            parentGradle.includedBuilds.singleOrNull {
                // these values already canonicalized
                //noinspection FileComparisons
                it.projectDir == currentGradle.rootProject.projectDir
            }?.name
                ?: throw RuntimeException("Failed to get Gradle name for ${project.path}")
        } else {
            // this is top gradle so name is ":"
            ":"
        }
    }

    /**
     * Returns the build map and the current name
     */
    private fun getBuildMap(project: Project): Map<String, File> {
        var rootGradle = project.gradle
        while (rootGradle.parent != null) {
            rootGradle = rootGradle.parent!!
        }

        return mutableMapOf<String, File>().also { map ->
            map[":"] = rootGradle.rootProject.projectDir
            getBuildMap(rootGradle, map)
        }
    }

    private fun getBuildMap(gradle: Gradle, map: MutableMap<String, File>) {
        for (build in gradle.includedBuilds) {
            map[build.name] = build.projectDir
        }
    }

    private fun buildAndroidDslModel(project: Project): AndroidDsl {

        val variantInputs = variantModel.inputs
        val variants = variantModel.variants

        // for now grab the first buildFeatureValues as they cannot be different.
        val buildFeatures = variantModel.buildFeatures

        // gather the default config
        val defaultConfig = variantInputs.defaultConfigData.defaultConfig.convert(buildFeatures)

        // gather all the build types
        val buildTypes = mutableListOf<com.android.builder.model.v2.dsl.BuildType>()
        for (buildType in variantInputs.buildTypes.values) {
            buildTypes.add(buildType.buildType.convert(buildFeatures))
        }

        // gather product flavors
        val productFlavors = mutableListOf<com.android.builder.model.v2.dsl.ProductFlavor>()
        for (flavor in variantInputs.productFlavors.values) {
            productFlavors.add(flavor.productFlavor.convert(buildFeatures))
        }

        val dependenciesInfo =
                if (extension is ApplicationExtension) {
                    DependenciesInfoImpl(
                        extension.dependenciesInfo.includeInApk,
                        extension.dependenciesInfo.includeInBundle
                    )
                } else null


        val compileSdkVersion = when (extension) {
            is BaseAppModuleExtension -> extension.compileSdkVersion
            is LibraryExtension -> extension.compileSdkVersion
            is com.android.build.gradle.TestExtension -> extension.compileSdkVersion
            is DynamicFeatureExtension -> extension.compileSdkVersion
            else -> null
        } ?: "unknown"

        return AndroidDslImpl(
            buildToolsVersion = extension.buildToolsVersion,

            groupId = project.group.toString(),
            compileTarget = compileSdkVersion,

            defaultConfig = defaultConfig,
            buildTypes = buildTypes,
            flavorDimensions = ImmutableList.copyOf(extension.flavorDimensions),
            productFlavors = productFlavors,

            signingConfigs = extension.signingConfigs.map { it.convert() },
            aaptOptions = extension.androidResources.convert(),
            lintOptions = extension.lint.convert(),

            dependenciesInfo = dependenciesInfo,
            )
    }

    private fun buildProjectSyncIssueModel(project: Project): ProjectSyncIssues {
        syncIssueReporter.lockHandler()

        val allIssues = ImmutableSet.builder<SyncIssue>()
        allIssues.addAll(syncIssueReporter.syncIssues)
        allIssues.addAll(
            getBuildService(project.gradle.sharedServices, GlobalSyncIssueService::class.java)
                .get()
                .getAllIssuesAndClear()
        )

        // For now we have to convert from the v1 to the v2 model.
        // FIXME: separate the internal-AGP and builder-model version of the SyncIssue classes
        val issues = allIssues.build().map {
            SyncIssueImpl(
                it.severity,
                it.type,
                it.data,
                it.message,
                it.multiLineMessage
            )
        }

        return ProjectSyncIssuesImpl(issues)
    }

    private fun buildVariantDependenciesModel(
        project: Project,
        parameter: ModelBuilderParameter
    ): VariantDependencies? {
        // get the variant to return the dependencies for
        val variantName = parameter.variantName
        val variant = variantModel.variants.singleOrNull { it.name == variantName }
            ?: return null

        val buildMapping = project.gradle.computeBuildMapping()

        val globalLibraryBuildService =
            getBuildService(
                project.gradle.sharedServices,
                GlobalSyncService::class.java
            ).get()

        val libraryService = LibraryServiceImpl(
            globalLibraryBuildService.stringCache,
            globalLibraryBuildService.localJarCache
        )

        return VariantDependenciesImpl(
            name = variantName,
            mainArtifact = createDependencies(variant, buildMapping, libraryService,),
            androidTestArtifact = variant.testComponents[VariantTypeImpl.ANDROID_TEST]?.let {
                createDependencies(it, buildMapping, libraryService,)
            },
            unitTestArtifact = variant.testComponents[VariantTypeImpl.UNIT_TEST]?.let {
                createDependencies(it, buildMapping, libraryService,)
            },
            testFixturesArtifact = variant.testFixturesComponent?.let {
                createDependencies(it, buildMapping, libraryService,)
            },
            libraryService.getAllLibraries().associateBy { it.key }
        )
    }

    private fun createBasicVariant(
        variant: VariantImpl,
        features: BuildFeatureValues
    ): BasicVariantImpl {
        return BasicVariantImpl(
            name = variant.name,
            mainArtifact = createBasicArtifact(variant, features),
            androidTestArtifact = variant.testComponents[VariantTypeImpl.ANDROID_TEST]?.let {
                createBasicArtifact(it, features)
            },
            unitTestArtifact = variant.testComponents[VariantTypeImpl.UNIT_TEST]?.let {
                createBasicArtifact(it, features)
            },
            testFixturesArtifact = variant.testFixturesComponent?.let {
                createBasicArtifact(it, features)
            },
            buildType = variant.buildType,
            productFlavors = variant.productFlavors.map { it.second },
        )
    }

    private fun createBasicArtifact(
        component: ComponentImpl,
        features: BuildFeatureValues
    ): BasicArtifact {
        val sourceProviders = component.variantSources

        return BasicArtifactImpl(
            variantSourceProvider = sourceProviders.variantSourceProvider?.convert(features, component.sources),
            multiFlavorSourceProvider = sourceProviders.multiFlavorSourceProvider?.convert(
                features
            ),
        )
    }

    private fun createVariant(
        variant: VariantImpl,
        instantAppResultMap: MutableMap<File, Boolean>
    ): com.android.build.gradle.internal.ide.v2.VariantImpl {
        return VariantImpl(
            name = variant.name,
            displayName = variant.baseName,
            mainArtifact = createAndroidArtifact(variant),
            androidTestArtifact = variant.testComponents[VariantTypeImpl.ANDROID_TEST]?.let {
                createAndroidArtifact(it)
            },
            unitTestArtifact = variant.testComponents[VariantTypeImpl.UNIT_TEST]?.let {
                createJavaArtifact(it)
            },
            testFixturesArtifact = variant.testFixturesComponent?.let {
                createAndroidArtifact(it)
            },
            testedTargetVariant = getTestTargetVariant(variant),
            isInstantAppCompatible = inspectManifestForInstantTag(variant, instantAppResultMap),
            desugaredMethods = listOf()
        )
    }

    private fun createAndroidArtifact(component: ComponentImpl): AndroidArtifactImpl {
        val variantData = component.variantData
        val variantDslInfo = component.variantDslInfo
        // FIXME need to find a better way for this.
        val taskContainer: MutableTaskContainer = component.taskContainer

        // The class folders. This is supposed to be the output of the compilation steps + other
        // steps that create bytecode
        // For now, until Module Per SourceSet lands, we need to separate the main compilation
        // output from the rest, so we use 2 properties.
        // Long term, we'll move everything to classesFolders.
        val classesFolders = setOf(component.artifacts.get(JAVAC).get().asFile)

        val additionalClassesFolders = mutableSetOf<File>()
        component.getCompiledRClassArtifact()?.get()?.asFile?.let {
            additionalClassesFolders.add(it)
        }
        additionalClassesFolders.addAll(variantData.allPreJavacGeneratedBytecode.files)
        additionalClassesFolders.addAll(variantData.allPostJavacGeneratedBytecode.files)

        val testInfo: TestInfo? = when(component) {
            is TestVariantImpl, is AndroidTestImpl -> {
                val runtimeApks: Collection<File> = project
                    .configurations
                    .findByName(SdkConstants.GRADLE_ANDROID_TEST_UTIL_CONFIGURATION)?.files
                    ?: listOf()

                DeviceProviderInstrumentTestTask.checkForNonApks(runtimeApks) { message ->
                    syncIssueReporter.reportError(IssueReporter.Type.GENERIC, message)
                }

                val testOptionsDsl = globalScope.extension.testOptions

                val testTaskName = taskContainer.connectedTestTask?.name ?: "".also {
                    syncIssueReporter.reportError(
                        IssueReporter.Type.GENERIC,
                        "unable to find connectedCheck task name for ${component.name}"
                    )
                }

                TestInfoImpl(
                    animationsDisabled = testOptionsDsl.animationsDisabled,
                    execution = testOptionsDsl.getExecutionEnum().convert(),
                    additionalRuntimeApks = runtimeApks,
                    instrumentedTestTaskName = testTaskName
                )
            }
            else -> null
        }

        val signingConfig = if (component is ApkCreationConfig)
            component.signingConfig else null

        val minSdkVersion =
                ApiVersionImpl(component.minSdkVersion.apiLevel, component.minSdkVersion.codename)
        val targetSdkVersionOverride = component.targetSdkVersionOverride?.let {
            ApiVersionImpl(it.apiLevel, it.codename)
        }
        val maxSdkVersion =
                if (component is VariantCreationConfig) component.maxSdkVersion else null

        val modelSyncFiles = if (component is ApplicationCreationConfig || component is LibraryCreationConfig || component is TestVariantCreationConfig || component is DynamicFeatureCreationConfig) {
            listOf(
                ModelSyncFileImpl(
                    ModelSyncFile.ModelSyncType.BASIC,
                    AbstractVariantModelTask.getTaskName(component),
                    component.artifacts.get(InternalArtifactType.VARIANT_MODEL).get().asFile
                )
            )
        } else {
            listOf()
        }

        return AndroidArtifactImpl(
            minSdkVersion = minSdkVersion,
            targetSdkVersionOverride = targetSdkVersionOverride,
            maxSdkVersion = maxSdkVersion,

            signingConfigName = signingConfig?.name,
            isSigned = signingConfig != null,

            abiFilters = variantDslInfo.supportedAbis,
            testInfo = testInfo,
            bundleInfo = getBundleInfo(component),
            codeShrinker = CodeShrinker.R8.takeIf {
                component is ConsumableCreationConfig && component.minifiedEnabled
            },

            assembleTaskName = taskContainer.assembleTask.name,
            compileTaskName = taskContainer.compileTask.name,
            sourceGenTaskName = taskContainer.sourceGenTask.name,
            resGenTaskName = if (component.buildFeatures.androidResources) taskContainer.resourceGenTask.name else null,
            ideSetupTaskNames = setOf(taskContainer.sourceGenTask.name),

            generatedSourceFolders = ModelBuilder.getGeneratedSourceFolders(component),
            generatedResourceFolders = ModelBuilder.getGeneratedResourceFolders(component),
            classesFolders = classesFolders,
            additionalClassesFolders = additionalClassesFolders,
            assembleTaskOutputListingFile = if (component.variantType.isApk)
                component.artifacts.get(InternalArtifactType.APK_IDE_REDIRECT_FILE).get().asFile
            else
                null,
            modelSyncFiles = modelSyncFiles,
        )
    }

    private fun createJavaArtifact(component: ComponentImpl): JavaArtifact {
        val variantData = component.variantData

        // FIXME need to find a better way for this.
        val taskContainer: MutableTaskContainer = component.taskContainer

        // The class folders. This is supposed to be the output of the compilation steps + other
        // steps that create bytecode
        // For now, until Module Per SourceSet lands, we need to separate the main compilation
        // output from the rest, so we use 2 properties.
        // Long term, we'll move everything to classesFolders.
        val classesFolders = setOf(component.artifacts.get(JAVAC).get().asFile)

        val additionalClassesFolders = mutableSetOf<File>()

        // TODO(b/111168382): When namespaced resources is on, then the provider returns null, so let's skip for now and revisit later
        if (!extension.androidResources.namespaced) {
            component.getCompiledRClassArtifact()?.get()?.asFile?.let {
                additionalClassesFolders.add(it)
            }
        }
        additionalClassesFolders.addAll(variantData.allPreJavacGeneratedBytecode.files)
        additionalClassesFolders.addAll(variantData.allPostJavacGeneratedBytecode.files)
        if (extension.testOptions.unitTests.isIncludeAndroidResources) {
            additionalClassesFolders.add(component.artifacts.get(UNIT_TEST_CONFIG_DIRECTORY).get().asFile)
        }

        return JavaArtifactImpl(
            assembleTaskName = taskContainer.assembleTask.name,
            compileTaskName = taskContainer.compileTask.name,
            ideSetupTaskNames = setOf(TaskManager.CREATE_MOCKABLE_JAR_TASK_NAME),

            classesFolders = classesFolders,
            additionalClassesFolders = additionalClassesFolders,
            generatedSourceFolders = ModelBuilder.getGeneratedSourceFoldersForUnitTests(component),
            runtimeResourceFolder = component.variantData.javaResourcesForUnitTesting,

            mockablePlatformJar = globalScope.mockableJarArtifact.files.singleOrNull(),
            modelSyncFiles = listOf(),
        )
    }

    private fun createDependencies(
        component: ComponentImpl,
        buildMapping: BuildMapping,
        libraryService: LibraryService,
    ): ArtifactDependencies {

        val inputs = ArtifactCollectionsInputsImpl(
            variantDependencies = component.variantDependencies,
            projectPath = project.path,
            variantName = component.name,
            runtimeType = ArtifactCollectionsInputs.RuntimeType.FULL,
            buildMapping = buildMapping
        )

        return FullDependencyGraphBuilder(
            inputs,
            component.variantDependencies,
            libraryService
        ).build()
    }

    private fun getFlags(): AndroidGradlePluginProjectFlagsImpl {
        val flags =
            ImmutableMap.builder<BooleanFlag, Boolean>()

        val finalResIds = !projectOptions[BooleanOption.USE_NON_FINAL_RES_IDS]

        flags.put(BooleanFlag.APPLICATION_R_CLASS_CONSTANT_IDS, finalResIds)
        flags.put(BooleanFlag.TEST_R_CLASS_CONSTANT_IDS, finalResIds)
        flags.put(
            BooleanFlag.JETPACK_COMPOSE,
            variantModel.variants.any { it.buildFeatures.compose }
        )
        flags.put(
            BooleanFlag.ML_MODEL_BINDING,
            variantModel.variants.any { it.buildFeatures.mlModelBinding }
        )
        flags.put(
            BooleanFlag.TRANSITIVE_R_CLASS,
            !projectOptions[BooleanOption.NON_TRANSITIVE_R_CLASS]
        )
        flags.put(
            BooleanFlag.UNIFIED_TEST_PLATFORM,
            projectOptions[BooleanOption.ANDROID_TEST_USES_UNIFIED_TEST_PLATFORM]
        )

        return AndroidGradlePluginProjectFlagsImpl(flags.build())
    }

    private fun getBundleInfo(
        component: ComponentImpl
    ): BundleInfo? {
        if (!component.variantType.isBaseModule) {
            return null
        }

        // TODO(b/111168382): Remove when bundle can build apps with namespaced turned on.
        if (extension.androidResources.namespaced) {
            return null
        }

        // FIXME need to find a better way for this.
        val taskContainer: MutableTaskContainer = component.taskContainer
        val artifacts = component.artifacts

        return BundleInfoImpl(
            bundleTaskName = taskContainer.bundleTask?.name ?: error("failed to find bundle task name for ${component.name}"),
            bundleTaskOutputListingFile = artifacts.get(InternalArtifactType.BUNDLE_IDE_REDIRECT_FILE).get().asFile,
            apkFromBundleTaskName = AnchorTaskNames.getExtractApksAnchorTaskName(component),
            apkFromBundleTaskOutputListingFile = artifacts.get(InternalArtifactType.APK_FROM_BUNDLE_IDE_REDIRECT_FILE).get().asFile
        )
    }

    // FIXME this is coming from the v1 Model Builder and this needs to be rethought. b/160970116
    private fun inspectManifestForInstantTag(
        component: ComponentImpl,
        instantAppResultMap: MutableMap<File, Boolean>
    ): Boolean {
        if (!component.variantType.isBaseModule && !component.variantType.isDynamicFeature) {
            return false
        }

        val variantSources = component.variantSources

        // get the manifest in descending order of priority. First one to return
        val manifests = mutableListOf<File>()
        manifests.addAll(variantSources.manifestOverlays)
        variantSources.mainManifestIfExists?.let { manifests.add(it) }

        if (manifests.isEmpty()) {
            return false
        }
        for (manifest in manifests) {
            // check if the manifest was already parsed. If so, just pull the information
            // from the map
            val parseResult = instantAppResultMap[manifest]
            if (parseResult != null) {
                if (parseResult) {
                    return true
                }
                continue
            }

            try {
                FileInputStream(manifest).use { inputStream ->
                    val factory = XMLInputFactory.newInstance()
                    val eventReader = factory.createXMLEventReader(inputStream)
                    while (eventReader.hasNext() && !eventReader.peek().isEndDocument) {
                        val event = eventReader.nextTag()
                        if (event.isStartElement) {
                            val startElement = event.asStartElement()
                            if (startElement.name.namespaceURI == SdkConstants.DIST_URI
                                && startElement.name.localPart.equals("module", ignoreCase = true)
                            ) {
                                val instant = startElement.getAttributeByName(
                                    QName(SdkConstants.DIST_URI, "instant")
                                )
                                if (instant != null
                                    && (
                                            instant.value == SdkConstants.VALUE_TRUE
                                                    || instant.value == SdkConstants.VALUE_1)
                                ) {
                                    eventReader.close()
                                    instantAppResultMap[manifest] = true
                                    return true
                                }
                            }
                        } else if (event.isEndElement
                            && (event as EndElement).name.localPart.equals("manifest", ignoreCase = true)
                        ) {
                            break
                        }
                    }
                    eventReader.close()
                }
            } catch (e: XMLStreamException) {
                syncIssueReporter.reportError(
                    IssueReporter.Type.GENERIC,
                    """
                        Failed to parse XML in ${manifest.path}
                        ${e.message}
                        """.trimIndent()
                )
            } catch (e: IOException) {
                syncIssueReporter.reportError(
                    IssueReporter.Type.GENERIC,
                    """
                        Failed to parse XML in ${manifest.path}
                        ${e.message}
                        """.trimIndent()
                )
            } finally {
                // check that we have not yet put a true in there
                instantAppResultMap.putIfAbsent(manifest, false)
            }
        }
        return false
    }

    private fun getTestTargetVariant(
        component: ComponentImpl
    ): TestedTargetVariant? {
        if (extension is TestExtension) {
            val targetPath = extension.targetProjectPath ?: return null

            // to get the target variant we need to get the result of the dependency resolution
            val apkArtifacts = component
                .variantDependencies
                .getArtifactCollection(
                    PROVIDED_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.ALL,
                    AndroidArtifacts.ArtifactType.APK
                )

            // while there should be a single result, the list may be empty if the variant
            // matching is broken
            if (apkArtifacts.artifacts.size == 1) {
                val result = apkArtifacts.artifacts.single()
                // if the name of the variant is missing, then just return null, but this
                // should not happen
                val variantName = result.getVariantName() ?: return null
                return TestedTargetVariantImpl(targetPath, variantName)

            } else if (!apkArtifacts.failures.isEmpty()) {
                // probably there was an error...
                DependencyFailureHandler()
                    .addErrors(
                        "${project.path}@${component.name}/testTarget",
                        apkArtifacts.failures
                    )
                    .registerIssues(syncIssueReporter)
            }
        }
        return null
    }
}
