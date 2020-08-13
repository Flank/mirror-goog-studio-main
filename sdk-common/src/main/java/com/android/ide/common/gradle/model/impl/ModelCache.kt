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
package com.android.ide.common.gradle.model.impl

import com.android.SdkConstants
import com.android.build.FilterData
import com.android.build.OutputFile
import com.android.build.VariantOutput
import com.android.builder.model.AaptOptions
import com.android.builder.model.AndroidArtifact
import com.android.builder.model.AndroidGradlePluginProjectFlags
import com.android.builder.model.AndroidLibrary
import com.android.builder.model.AndroidProject
import com.android.builder.model.ApiVersion
import com.android.builder.model.BaseArtifact
import com.android.builder.model.BuildType
import com.android.builder.model.BuildTypeContainer
import com.android.builder.model.ClassField
import com.android.builder.model.Dependencies
import com.android.builder.model.DependenciesInfo
import com.android.builder.model.JavaArtifact
import com.android.builder.model.JavaCompileOptions
import com.android.builder.model.JavaLibrary
import com.android.builder.model.Library
import com.android.builder.model.LintOptions
import com.android.builder.model.MavenCoordinates
import com.android.builder.model.NativeAndroidProject
import com.android.builder.model.NativeArtifact
import com.android.builder.model.NativeFile
import com.android.builder.model.NativeSettings
import com.android.builder.model.NativeToolchain
import com.android.builder.model.NativeVariantAbi
import com.android.builder.model.NativeVariantInfo
import com.android.builder.model.ProductFlavor
import com.android.builder.model.ProductFlavorContainer
import com.android.builder.model.SigningConfig
import com.android.builder.model.SourceProvider
import com.android.builder.model.SourceProviderContainer
import com.android.builder.model.SyncIssue
import com.android.builder.model.TestOptions
import com.android.builder.model.TestedTargetVariant
import com.android.builder.model.Variant
import com.android.builder.model.VariantBuildInformation
import com.android.builder.model.VectorDrawablesOptions
import com.android.builder.model.ViewBindingOptions
import com.android.builder.model.v2.models.ndk.NativeAbi
import com.android.builder.model.v2.models.ndk.NativeModule
import com.android.builder.model.v2.models.ndk.NativeVariant
import com.android.ide.common.gradle.model.IdeAaptOptions
import com.android.ide.common.gradle.model.IdeAndroidArtifactOutput
import com.android.ide.common.gradle.model.IdeAndroidGradlePluginProjectFlags
import com.android.ide.common.gradle.model.IdeAndroidProject
import com.android.ide.common.gradle.model.IdeBuildTypeContainer
import com.android.ide.common.gradle.model.IdeDependencies
import com.android.ide.common.gradle.model.IdeDependenciesInfo
import com.android.ide.common.gradle.model.IdeLibrary
import com.android.ide.common.gradle.model.IdeLintOptions
import com.android.ide.common.gradle.model.IdeMavenCoordinates
import com.android.ide.common.gradle.model.IdeProductFlavorContainer
import com.android.ide.common.gradle.model.IdeSigningConfig
import com.android.ide.common.gradle.model.IdeSyncIssue
import com.android.ide.common.gradle.model.IdeTestOptions
import com.android.ide.common.gradle.model.IdeVariant
import com.android.ide.common.gradle.model.IdeVariantBuildInformation
import com.android.ide.common.gradle.model.IdeVectorDrawablesOptions
import com.android.ide.common.gradle.model.IdeViewBindingOptions
import com.android.ide.common.gradle.model.impl.ndk.v1.IdeNativeAndroidProjectImpl
import com.android.ide.common.gradle.model.impl.ndk.v1.IdeNativeArtifactImpl
import com.android.ide.common.gradle.model.impl.ndk.v1.IdeNativeFileImpl
import com.android.ide.common.gradle.model.impl.ndk.v1.IdeNativeSettingsImpl
import com.android.ide.common.gradle.model.impl.ndk.v1.IdeNativeToolchainImpl
import com.android.ide.common.gradle.model.impl.ndk.v1.IdeNativeVariantAbiImpl
import com.android.ide.common.gradle.model.impl.ndk.v1.IdeNativeVariantInfoImpl
import com.android.ide.common.gradle.model.impl.ndk.v2.IdeNativeAbiImpl
import com.android.ide.common.gradle.model.impl.ndk.v2.IdeNativeModuleImpl
import com.android.ide.common.gradle.model.impl.ndk.v2.IdeNativeVariantImpl
import com.android.ide.common.gradle.model.ndk.v1.IdeNativeToolchain
import com.android.ide.common.gradle.model.ndk.v2.NativeBuildSystem
import com.android.ide.common.repository.GradleVersion
import com.android.utils.FileUtils
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.common.collect.ImmutableSortedSet
import com.google.common.collect.Iterables
import java.io.File
import java.util.HashMap

class ModelCache {
  private val strings: MutableMap<String, String> = HashMap()
  private val buildFolderPaths = BuildFolderPaths()
  private val androidLibraryCores = mutableMapOf<IdeAndroidLibraryCore, IdeAndroidLibraryCore>()
  private val javaLibraryCores = mutableMapOf<IdeJavaLibraryCore, IdeJavaLibraryCore>()
  private val moduleLibraryCores = mutableMapOf<IdeModuleLibraryCore, IdeModuleLibraryCore>()

  /**
   * Stores the [buildFolder] path for the given [moduleGradlePath] and [buildId].
   */
  fun addBuildFolderPath(buildId: String, moduleGradlePath: String, buildFolder: File) {
    buildFolderPaths.addBuildFolderMapping(buildId, moduleGradlePath, buildFolder)
  }

  /**
   * Set the build identifier of root project.
   */
  fun setRootBuildId(rootBuildId: String) {
    buildFolderPaths.setRootBuildId(rootBuildId)
  }

  fun androidProjectFrom(
          project: AndroidProject,
          variants: Collection<Variant>,
          cachedVariants: Collection<IdeVariant>,
          syncIssues: Collection<SyncIssue>
  ): IdeAndroidProjectImpl {
    // Old plugin versions do not return model version.
    val parsedModelVersion = GradleVersion.tryParse(project.modelVersion)

    val defaultConfigCopy: IdeProductFlavorContainer = copyModel(project.defaultConfig, ::productFlavorContainerFrom)
    val buildTypesCopy: Collection<IdeBuildTypeContainer> = copy(project::getBuildTypes, ::buildTypeContainerFrom)
    val productFlavorCopy: Collection<IdeProductFlavorContainer> = copy(project::getProductFlavors, ::productFlavorContainerFrom)
    val syncIssuesCopy: Collection<IdeSyncIssue> = syncIssues.map(::syncIssueFrom)
    val variantNames = variants.map { it.name }.toSet()
    val variantsCopy: Collection<IdeVariant> = ImmutableList.copyOf(
      Iterables.concat<IdeVariant>(
              variants.map { variant: Variant -> variantFrom(variant, parsedModelVersion) },
              cachedVariants.filter { !variantNames.contains(it.name) }
      ))
    val variantNamesCopy: Collection<String> =
      copyNewPropertyWithDefault(
        { ImmutableList.copyOf(project.variantNames) },
        { computeVariantNames(variantsCopy) })
    val defaultVariantCopy = copyNewPropertyWithDefault({ project.defaultVariant }, { getDefaultVariant(variantNamesCopy) })
    val flavorDimensionCopy: Collection<String> = copy(project::getFlavorDimensions, ::deduplicateString)
    val bootClasspathCopy: Collection<String> = ImmutableList.copyOf(project.bootClasspath)
    val nativeToolchainsCopy: Collection<IdeNativeToolchain> = copy(project::getNativeToolchains, ::nativeToolchainFrom)
    val signingConfigsCopy: Collection<IdeSigningConfig> = copy(project::getSigningConfigs, ::signingConfigFrom)
    val lintOptionsCopy: IdeLintOptions = copyModel(project.lintOptions, { lintOptionsFrom(it, parsedModelVersion) })
    val javaCompileOptionsCopy = copyModel(project.javaCompileOptions, ::javaCompileOptionsFrom)
    val aaptOptionsCopy = copyModel(project.aaptOptions, ::aaptOptionsFrom)
    val dynamicFeaturesCopy: Collection<String> = copy(project::getDynamicFeatures, ::deduplicateString)
    val variantBuildInformation = createVariantBuildInformation(project, parsedModelVersion)
    val viewBindingOptionsCopy: IdeViewBindingOptions? = copyNewModel(project::getViewBindingOptions, ::viewBindingOptionsFrom)
    val dependenciesInfoCopy: IdeDependenciesInfo? = copyNewModel(project::getDependenciesInfo, ::dependenciesInfoFrom)
    val buildToolsVersionCopy = copyNewProperty(project::getBuildToolsVersion)
    val ndkVersionCopy = copyNewProperty(project::getNdkVersion)
    val groupId = if (parsedModelVersion != null && parsedModelVersion.isAtLeast(3, 6, 0, "alpha", 5, false)) project.groupId else null
    val lintRuleJarsCopy: List<File>? = copy(project::getLintRuleJars, ::deduplicateFile)

    // AndroidProject#isBaseSplit is always non null.
    val isBaseSplit = copyNewProperty({ project.isBaseSplit }, false)
    val agpFlags: IdeAndroidGradlePluginProjectFlags = copyNewProperty(
      { androidGradlePluginProjectFlagsFrom(project.flags) },
      IdeAndroidGradlePluginProjectFlagsImpl()
    )
    return IdeAndroidProjectImpl(
      project.modelVersion,
      project.name,
      defaultConfigCopy,
      buildTypesCopy,
      productFlavorCopy,
      syncIssuesCopy,
      variantsCopy,
      variantNamesCopy,
      defaultVariantCopy,
      flavorDimensionCopy,
      project.compileTarget,
      bootClasspathCopy,
      nativeToolchainsCopy,
      signingConfigsCopy,
      lintOptionsCopy,
      lintRuleJarsCopy,
      javaCompileOptionsCopy,
      aaptOptionsCopy,
      project.buildFolder,
      dynamicFeaturesCopy,
      variantBuildInformation,
      viewBindingOptionsCopy,
      dependenciesInfoCopy,
      buildToolsVersionCopy,
      ndkVersionCopy,
      project.resourcePrefix,
      groupId,
      copyNewProperty(project::getPluginGeneration) != null,
      project.apiVersion,
      getProjectType(project, parsedModelVersion),
      isBaseSplit,
      agpFlags)
  }

  private fun createVariantBuildInformation(
    project: AndroidProject,
    agpVersion: GradleVersion?
  ): Collection<IdeVariantBuildInformation> {
    return if (agpVersion != null && agpVersion.compareIgnoringQualifiers("4.1.0") >= 0) {
      // make deep copy of VariantBuildInformation.
      project.variantsBuildInformation.map(::ideVariantBuildInformationFrom)
    }
    else emptyList()
    // VariantBuildInformation is not available.
  }

  private fun computeVariantNames(variants: Collection<IdeVariant>): List<String> = variants.map(IdeVariant::name)

  private fun getProjectType(project: AndroidProject, modelVersion: GradleVersion?): Int {
    if (modelVersion != null && modelVersion.isAtLeast(2, 3, 0)) {
      return project.projectType
    }
    // Support for old Android Gradle Plugins must be maintained.
    return if (project.isLibrary) IdeAndroidProject.PROJECT_TYPE_LIBRARY else IdeAndroidProject.PROJECT_TYPE_APP
  }

  /** For older AGP versions pick a variant name based on a heuristic  */
  @VisibleForTesting
  fun getDefaultVariant(variantNames: Collection<String>): String? {
    // Corner case of variant filter accidentally removing all variants.
    if (variantNames.isEmpty()) {
      return null
    }

    // Favor the debug variant
    if (variantNames.contains("debug")) {
      return "debug"
    }
    // Otherwise the first alphabetically that has debug as a build type.
    val sortedNames = ImmutableSortedSet.copyOf(variantNames)
    for (variantName in sortedNames) {
      if (variantName.endsWith("Debug")) {
        return variantName
      }
    }
    // Otherwise fall back to the first alphabetically
    return sortedNames.first()
  }

  fun aaptOptionsFrom(original: AaptOptions): IdeAaptOptionsImpl {
    return IdeAaptOptionsImpl(convertNamespacing(copyNewProperty({ original.namespacing }, AaptOptions.Namespacing.DISABLED)))
  }

  private fun convertNamespacing(namespacing: AaptOptions.Namespacing): IdeAaptOptions.Namespacing {
    return when (namespacing) {
      AaptOptions.Namespacing.DISABLED -> IdeAaptOptions.Namespacing.DISABLED
      AaptOptions.Namespacing.REQUIRED -> IdeAaptOptions.Namespacing.REQUIRED
      else -> throw IllegalStateException("Unknown namespacing option: $namespacing")
    }
  }

  private fun copyOutputs(
    artifact: AndroidArtifact,
    agpVersion: GradleVersion?
  ): List<IdeAndroidArtifactOutput> {
    // getOutputs is deprecated in AGP 4.0.0.
    if (agpVersion != null && agpVersion.compareIgnoringQualifiers("4.0.0") >= 0) {
      return emptyList()
    }
    return copy(artifact::getOutputs, ::androidArtifactOutputFrom)
  }

  fun androidArtifactFrom(
    artifact: AndroidArtifact,
    agpVersion: GradleVersion?
  ): IdeAndroidArtifactImpl {
    return IdeAndroidArtifactImpl(
      artifact.name,
      artifact.compileTaskName,
      artifact.assembleTaskName,
      copyNewProperty({ artifact.assembleTaskOutputListingFile }, ""),
      artifact.classesFolder,
      copyNewProperty(artifact::getJavaResourcesFolder),
      copyNewPropertyWithDefault(artifact::getIdeSetupTaskNames, defaultValue = { setOf(artifact.sourceGenTaskName) }).toList(),
      copy(artifact::getGeneratedSourceFolders, ::deduplicateFile).distinct(), // The source model can contain duplicates.
      copyNewModel(artifact::getVariantSourceProvider, ::sourceProviderFrom),
      copyNewModel(artifact::getMultiFlavorSourceProvider, ::sourceProviderFrom),
      copy(artifact::getAdditionalClassesFolders, ::deduplicateFile),
      dependenciesFrom(artifact),
      copyOutputs(artifact, agpVersion),
      artifact.applicationId,
      artifact.sourceGenTaskName,
      copy(artifact::getGeneratedResourceFolders, ::deduplicateFile).distinct(),
      artifact.signingConfigName,
      ImmutableSet.copyOf( // In AGP 4.0 and below abiFilters was nullable, normalize null to empty set.
                    artifact.abiFilters.orEmpty()),
      artifact.isSigned,
      copy(artifact::getAdditionalRuntimeApks, ::deduplicateFile),
      copyNewModel(artifact::getTestOptions, ::testOptionsFrom),
      copyNewModel(artifact::getInstrumentedTestTaskName, ::deduplicateString),
      copyNewModel(artifact::getBundleTaskName, ::deduplicateString),
      copyNewModel(artifact::getBundleTaskOutputListingFile, ::deduplicateString),
      copyNewModel(artifact::getApkFromBundleTaskName, ::deduplicateString),
      copyNewModel(artifact::getApkFromBundleTaskOutputListingFile, ::deduplicateString),
      copyNewProperty(artifact::getCodeShrinker)
    )
  }

  fun androidArtifactOutputFrom(output: OutputFile): IdeAndroidArtifactOutputImpl {
    return IdeAndroidArtifactOutputImpl(
      copyFilters(output),
      output.versionCode,
      copyNewProperty({ output.outputFile }, output.mainOutputFile.outputFile)
    )
  }

  fun apiVersionFrom(version: ApiVersion): IdeApiVersionImpl {
    return IdeApiVersionImpl(version.apiString, version.codename, version.apiLevel)
  }

  fun buildTypeContainerFrom(container: BuildTypeContainer): IdeBuildTypeContainerImpl {
    return IdeBuildTypeContainerImpl(
      copyModel(container.buildType, ::buildTypeFrom),
      copyModel(container.sourceProvider, ::sourceProviderFrom),
      copy(container::getExtraSourceProviders, ::sourceProviderContainerFrom)
    )
  }

  fun buildTypeFrom(buildType: BuildType): IdeBuildTypeImpl {
    return IdeBuildTypeImpl(
      buildType.name,
      copy(buildType::resValues, ::classFieldFrom),
      ImmutableList.copyOf(buildType.proguardFiles),
      ImmutableList.copyOf(buildType.consumerProguardFiles),
      // AGP may return internal Groovy GString implementation as a value in
      // manifestPlaceholders
      // map. It cannot be serialized
      // with IDEA's external system serialization. We convert values to String to
      // make them
      // usable as they are converted to String by
      // the manifest merger anyway.
      ImmutableMap.copyOf(buildType.manifestPlaceholders.entries.associate { it.key to it.value.toString() }),
      buildType.applicationIdSuffix,
      copyNewProperty(buildType::versionNameSuffix),
      copyNewProperty(buildType::multiDexEnabled),
      buildType.isDebuggable,
      buildType.isJniDebuggable,
      buildType.isRenderscriptDebuggable,
      buildType.renderscriptOptimLevel,
      buildType.isMinifyEnabled,
      buildType.isZipAlignEnabled
    )
  }

  fun classFieldFrom(classField: ClassField): IdeClassFieldImpl {
    return IdeClassFieldImpl(classField.name, classField.type, classField.value)
  }

  fun filterDataFrom(data: FilterData): IdeFilterDataImpl {
    return IdeFilterDataImpl(data.identifier, data.filterType)
  }

  fun javaArtifactFrom(artifact: JavaArtifact): IdeJavaArtifactImpl {
    return IdeJavaArtifactImpl(
      artifact.name,
      artifact.compileTaskName,
      artifact.assembleTaskName,
      copyNewProperty({ artifact.assembleTaskOutputListingFile }, ""),
      artifact.classesFolder,
      copyNewProperty(artifact::getJavaResourcesFolder),
      copy(artifact::getIdeSetupTaskNames, ::deduplicateString).toList(),
      copy(artifact::getGeneratedSourceFolders, ::deduplicateFile).distinct(),
      copyNewModel(artifact::getVariantSourceProvider, ::sourceProviderFrom),
      copyNewModel(artifact::getMultiFlavorSourceProvider, ::sourceProviderFrom),
      copy(artifact::getAdditionalClassesFolders, ::deduplicateFile),
      dependenciesFrom(artifact),
      copyNewProperty(artifact::getMockablePlatformJar)
    )
  }

  fun javaCompileOptionsFrom(options: JavaCompileOptions): IdeJavaCompileOptionsImpl {
    return IdeJavaCompileOptionsImpl(
      options.encoding,
      options.sourceCompatibility,
      options.targetCompatibility,
      copyNewProperty({ options.isCoreLibraryDesugaringEnabled }, false))
  }

  fun productFlavorContainerFrom(
    container: ProductFlavorContainer): IdeProductFlavorContainerImpl {
    return IdeProductFlavorContainerImpl(
      copyModel(container.productFlavor, ::productFlavorFrom),
      copyModel(container.sourceProvider, ::sourceProviderFrom),
      copy(container::getExtraSourceProviders, ::sourceProviderContainerFrom)
    )
  }

  private fun copyVectorDrawables(flavor: ProductFlavor): IdeVectorDrawablesOptions? {
    val vectorDrawables: VectorDrawablesOptions = try {
      flavor.vectorDrawables
    }
    catch (e: UnsupportedOperationException) {
      return null
    }
    return copyModel(vectorDrawables, ::vectorDrawablesOptionsFrom)
  }

  fun productFlavorFrom(flavor: ProductFlavor): IdeProductFlavorImpl {
    return IdeProductFlavorImpl(
            flavor.name,
            copy(flavor::resValues, ::classFieldFrom),
            ImmutableList.copyOf(flavor.proguardFiles),
            ImmutableList.copyOf(flavor.consumerProguardFiles),
      // AGP may return internal Groovy GString implementation as a value in
      // manifestPlaceholders
      // map. It cannot be serialized
      // with IDEA's external system serialization. We convert values to String to
      // make them
      // usable as they are converted to String by
      // the manifest merger anyway.
            ImmutableMap.copyOf(flavor.manifestPlaceholders.entries.associate { it.key to it.value.toString() }),
            flavor.applicationIdSuffix,
            copyNewProperty(flavor::versionNameSuffix),
            copyNewProperty(flavor::multiDexEnabled),
            ImmutableMap.copyOf(flavor.testInstrumentationRunnerArguments),
            ImmutableList.copyOf(flavor.resourceConfigurations),
            copyVectorDrawables(flavor),
            flavor.dimension,
            flavor.applicationId,
            flavor.versionCode,
            flavor.versionName,
            copyModel(flavor.minSdkVersion, ::apiVersionFrom),
            copyModel(flavor.targetSdkVersion, ::apiVersionFrom),
            flavor.maxSdkVersion,
            flavor.testApplicationId,
            flavor.testInstrumentationRunner,
            flavor.testFunctionalTest,
            flavor.testHandleProfiling,
            copyModel(flavor.signingConfig, ::signingConfigFrom)
    )
  }

  fun signingConfigFrom(config: SigningConfig): IdeSigningConfigImpl {
    return IdeSigningConfigImpl(
      config.getName(),
      config.storeFile,
      config.storePassword,
      config.keyAlias,
      copyNewProperty(config::isV1SigningEnabled)
    )
  }

  fun sourceProviderContainerFrom(container: SourceProviderContainer): IdeSourceProviderContainerImpl {
    return IdeSourceProviderContainerImpl(
      container.artifactName,
      copyModel(container.sourceProvider, ::sourceProviderFrom)
    )
  }

  fun syncIssueFrom(issue: SyncIssue): IdeSyncIssueImpl {

    return IdeSyncIssueImpl(
      issue.message,
      copy(fun(): List<String>? = issue.multiLineMessage?.filterNotNull(), ::deduplicateString),
      issue.data,
      issue.severity,
      issue.type
    )
  }

  fun testedTargetVariantFrom(variant: TestedTargetVariant): IdeTestedTargetVariantImpl {
    return IdeTestedTargetVariantImpl(variant.targetProjectPath, variant.targetVariant)
  }

  fun convertExecution(execution: TestOptions.Execution?): IdeTestOptions.Execution? {
    return if (execution == null) null
    else when (execution) {
      TestOptions.Execution.HOST -> IdeTestOptions.Execution.HOST
      TestOptions.Execution.ANDROID_TEST_ORCHESTRATOR -> IdeTestOptions.Execution.ANDROID_TEST_ORCHESTRATOR
      TestOptions.Execution.ANDROIDX_TEST_ORCHESTRATOR -> IdeTestOptions.Execution.ANDROIDX_TEST_ORCHESTRATOR
      else -> throw IllegalStateException("Unknown execution option: $execution")
    }
  }

  fun testOptionsFrom(testOptions: TestOptions): IdeTestOptionsImpl {
    return IdeTestOptionsImpl(testOptions.animationsDisabled, convertExecution(testOptions.execution))
  }

  fun vectorDrawablesOptionsFrom(options: VectorDrawablesOptions): IdeVectorDrawablesOptionsImpl {
    return IdeVectorDrawablesOptionsImpl(options.useSupportLibrary)
  }

  fun viewBindingOptionsFrom(model: ViewBindingOptions): IdeViewBindingOptionsImpl {
    return IdeViewBindingOptionsImpl(enabled = model.isEnabled)
  }

  fun variantFrom(
    variant: Variant,
    modelVersion: GradleVersion?
  ): IdeVariantImpl =
    IdeVariantImpl(
            name = variant.name,
            displayName = variant.displayName,
            mainArtifact = copyModel(variant.mainArtifact) { androidArtifactFrom(it, modelVersion) },
            extraAndroidArtifacts = copy(variant::getExtraAndroidArtifacts) { androidArtifactFrom(it, modelVersion) },
            extraJavaArtifacts = copy(variant::getExtraJavaArtifacts) { javaArtifactFrom(it) },
            buildType = variant.buildType,
            productFlavors = ImmutableList.copyOf(variant.productFlavors),
            mergedFlavor = copyModel(variant.mergedFlavor, ::productFlavorFrom),
            testedTargetVariants = getTestedTargetVariants(variant),
            instantAppCompatible = (modelVersion != null &&
                                    modelVersion.isAtLeast(3, 3, 0, "alpha", 10, true) &&
                                    variant.isInstantAppCompatible),
            desugaredMethods = copy(variant::getDesugaredMethods, ::deduplicateString)
    )

  private fun getTestedTargetVariants(variant: Variant): List<IdeTestedTargetVariantImpl> {
    return try {
      copy(variant::getTestedTargetVariants) { targetVariant: TestedTargetVariant ->
        IdeTestedTargetVariantImpl(targetVariant.targetProjectPath, targetVariant.targetVariant)
      }
    }
    catch (e: UnsupportedOperationException) {
      emptyList()
    }
  }

  fun ideVariantBuildInformationFrom(model: VariantBuildInformation): IdeVariantBuildInformation = IdeVariantBuildInformationImpl(
    variantName = model.variantName,
    assembleTaskName = model.assembleTaskName,
    assembleTaskOutputListingFile = model.assembleTaskOutputListingFile,
    bundleTaskName = model.bundleTaskName,
    bundleTaskOutputListingFile = model.bundleTaskOutputListingFile,
    apkFromBundleTaskName = model.apkFromBundleTaskName,
    apkFromBundleTaskOutputListingFile = model.apkFromBundleTaskOutputListingFile
  )

  fun sourceProviderFrom(provider: SourceProvider): IdeSourceProviderImpl {
    val folder: File? = provider.manifestFile.parentFile

    fun File.makeRelativeAndDeduplicate(): String = (if (folder != null) relativeToOrSelf(folder) else this).path.deduplicate()
    fun Collection<File>.makeRelativeAndDeduplicate(): Collection<String> = map { it.makeRelativeAndDeduplicate() }

    return IdeSourceProviderImpl(
      myName = provider.name,
      myFolder = folder,
      myManifestFile = provider.manifestFile.makeRelativeAndDeduplicate(),
      myJavaDirectories = provider.javaDirectories.makeRelativeAndDeduplicate(),
      myResourcesDirectories = provider.resourcesDirectories.makeRelativeAndDeduplicate(),
      myAidlDirectories = provider.aidlDirectories.makeRelativeAndDeduplicate(),
      myRenderscriptDirectories = provider.renderscriptDirectories.makeRelativeAndDeduplicate(),
      myCDirectories = provider.cDirectories.makeRelativeAndDeduplicate(),
      myCppDirectories = provider.cppDirectories.makeRelativeAndDeduplicate(),
      myResDirectories = provider.resDirectories.makeRelativeAndDeduplicate(),
      myAssetsDirectories = provider.assetsDirectories.makeRelativeAndDeduplicate(),
      myJniLibsDirectories = provider.jniLibsDirectories.makeRelativeAndDeduplicate(),
      myShadersDirectories = copy(provider::getShadersDirectories, mapper = { it }).makeRelativeAndDeduplicate(),
      myMlModelsDirectories = copy(provider::getMlModelsDirectories, mapper = { it }).makeRelativeAndDeduplicate()
    )
  }

  fun lintOptionsFrom(options: LintOptions, modelVersion: GradleVersion?): IdeLintOptionsImpl = IdeLintOptionsImpl(
    baselineFile = if (modelVersion != null && modelVersion.isAtLeast(2, 3, 0, "beta", 2, true))
      options.baselineFile
    else
      null,
    lintConfig = copyNewProperty(options::getLintConfig),
    severityOverrides = options.severityOverrides?.let { ImmutableMap.copyOf(it) },
    isCheckTestSources = modelVersion != null &&
                         modelVersion.isAtLeast(2, 4, 0) &&
                         options.isCheckTestSources,
    isCheckDependencies = copyNewProperty({ options.isCheckDependencies }, false),
    disable = copy(options::getDisable, ::deduplicateString),
    enable = copy(options::getEnable, ::deduplicateString),
    check = options.check?.let { ImmutableSet.copyOf(it) },
    isAbortOnError = copyNewProperty({ options.isAbortOnError }, true),
    isAbsolutePaths = copyNewProperty({ options.isAbsolutePaths }, true),
    isNoLines = copyNewProperty({ options.isNoLines }, false),
    isQuiet = copyNewProperty({ options.isQuiet }, false),
    isCheckAllWarnings = copyNewProperty({ options.isCheckAllWarnings }, false),
    isIgnoreWarnings = copyNewProperty({ options.isIgnoreWarnings }, false),
    isWarningsAsErrors = copyNewProperty({ options.isWarningsAsErrors }, false),
    isIgnoreTestSources = copyNewProperty({ options.isIgnoreTestSources }, false),
    isCheckGeneratedSources = copyNewProperty({ options.isCheckGeneratedSources }, false),
    isExplainIssues = copyNewProperty({ options.isExplainIssues }, true),
    isShowAll = copyNewProperty({ options.isShowAll }, false),
    textReport = copyNewProperty({ options.textReport }, false),
    textOutput = copyNewProperty(options::getTextOutput),
    htmlReport = copyNewProperty({ options.htmlReport }, true),
    htmlOutput = copyNewProperty(options::getHtmlOutput),
    xmlReport = copyNewProperty({ options.xmlReport }, true),
    xmlOutput = copyNewProperty(options::getXmlOutput),
    isCheckReleaseBuilds = copyNewProperty({ options.isCheckReleaseBuilds }, true)
  )

  fun dependenciesInfoFrom(model: DependenciesInfo) = IdeDependenciesInfoImpl(
    includeInApk = model.includeInApk,
    includeInBundle = model.includeInBundle
  )

  fun nativeAndroidProjectFrom(project: NativeAndroidProject): IdeNativeAndroidProjectImpl {
    return IdeNativeAndroidProjectImpl(
            project.modelVersion,
            project.apiVersion,
            project.name,
            copy(project::getBuildFiles, ::deduplicateFile),
            copy(project::getVariantInfos, ::nativeVariantFrom),
            copy(project::getArtifacts, ::nativeArtifactFrom),
            copy(project::getToolChains, ::nativeToolchainFrom),
            copy(project::getSettings, ::nativeSettingsFrom),
            copy(project::getFileExtensions, ::deduplicateString),
            copyNewProperty(project::getDefaultNdkVersion, ""),
            copy(project::getBuildSystems, ::deduplicateString)
    )
  }

  fun nativeToolchainFrom(toolchain: NativeToolchain): IdeNativeToolchainImpl {
    return IdeNativeToolchainImpl(
            toolchain.name,
            toolchain.cCompilerExecutable,
            toolchain.cppCompilerExecutable
    )
  }

  fun nativeVariantFrom(variantInfo: NativeVariantInfo): IdeNativeVariantInfoImpl {
    return IdeNativeVariantInfoImpl(
      copy(variantInfo::getAbiNames, ::deduplicateString),
      copy(variantInfo::getBuildRootFolderMap, ::deduplicateFile))
  }

  fun nativeArtifactFrom(artifact: NativeArtifact): IdeNativeArtifactImpl {
    return IdeNativeArtifactImpl(
            artifact.name,
            artifact.toolChain,
            artifact.groupName,
            copy(artifact::getSourceFiles, ::nativeFileFrom),
            copy(artifact::getExportedHeaders, ::deduplicateFile),
            copyNewProperty(artifact::getAbi, ""),
            copyNewProperty(artifact::getTargetName, ""),
            artifact.outputFile
    )
  }

  fun nativeFileFrom(file: NativeFile): IdeNativeFileImpl {
    return IdeNativeFileImpl(file.filePath, file.settingsName, file.workingDirectory)
  }

  fun nativeSettingsFrom(settings: NativeSettings): IdeNativeSettingsImpl {
    return IdeNativeSettingsImpl(
            settings.name,
            copy(settings::getCompilerFlags, ::deduplicateString)
    )
  }

  fun nativeVariantAbiFrom(variantAbi: NativeVariantAbi): IdeNativeVariantAbiImpl {
    return IdeNativeVariantAbiImpl(
      copy(variantAbi::getBuildFiles, ::deduplicateFile),
      copy(variantAbi::getArtifacts, ::nativeArtifactFrom),
      copy(variantAbi::getToolChains, ::nativeToolchainFrom),
      copy(variantAbi::getSettings, ::nativeSettingsFrom),
      copy(variantAbi::getFileExtensions, ::deduplicateString),
      variantAbi.variantName,
      variantAbi.abi
    )
  }

  fun nativeModuleFrom(nativeModule: NativeModule): IdeNativeModuleImpl {
    return IdeNativeModuleImpl(
      nativeModule.name,
      copy(nativeModule::variants, ::nativeVariantFrom),
      when (nativeModule.nativeBuildSystem) {
        com.android.builder.model.v2.models.ndk.NativeBuildSystem.NDK_BUILD -> NativeBuildSystem.NDK_BUILD
        com.android.builder.model.v2.models.ndk.NativeBuildSystem.CMAKE -> NativeBuildSystem.CMAKE
        // No forward compatibility. Old Studio cannot open projects with newer AGP.
        else -> error("Unknown native build system: ${nativeModule.nativeBuildSystem}")
      },
      nativeModule.ndkVersion,
      nativeModule.defaultNdkVersion,
      nativeModule.externalNativeBuildFile
    )
  }

  fun nativeVariantFrom(nativeVariant: NativeVariant): IdeNativeVariantImpl {
    return IdeNativeVariantImpl(
      nativeVariant.name,
      copy(nativeVariant::abis, ::nativeAbiFrom)
    )
  }

  fun nativeAbiFrom(nativeAbi: NativeAbi): IdeNativeAbiImpl {
    return IdeNativeAbiImpl(
            nativeAbi.name,
            nativeAbi.sourceFlagsFile,
            nativeAbi.symbolFolderIndexFile,
            nativeAbi.buildFileIndexFile
    )
  }

  fun androidGradlePluginProjectFlagsFrom(flags: AndroidGradlePluginProjectFlags): IdeAndroidGradlePluginProjectFlagsImpl =
          IdeAndroidGradlePluginProjectFlagsImpl(flags.booleanFlagMap)

  fun copyFilters(output: VariantOutput): Collection<FilterData> {
    return copy(
      fun(): Collection<FilterData> =
        try {
          output.filters
        }
        catch (ignored: UnsupportedOperationException) {
          output.outputs.flatMap(OutputFile::getFilters)
        },
      ::filterDataFrom
    )
  }

  /**
   * Create [IdeDependencies] from [BaseArtifact].
   */
  fun dependenciesFrom(artifact: BaseArtifact): IdeDependencies {
    return createFromDependencies(artifact.dependencies)
  }


  /**
   * @param androidLibrary Instance of [AndroidLibrary] returned by android plugin.
   * @param moduleBuildDirs Instance of [BuildFolderPaths] that contains map from project
   * path to build directory for all modules.
   * @return Instance of [Library] based on dependency type.
   */
  fun libraryFrom(androidLibrary: AndroidLibrary): IdeLibrary {
    // If the dependency is a sub-module that wraps local aar, it should be considered as external dependency, i.e. type LIBRARY_ANDROID.
    // In AndroidLibrary, getProject() of such dependency returns non-null project name, but they should be converted to IdeLevel2AndroidLibrary.
    // Identify such case with the location of aar bundle.
    // If the aar bundle is inside of build directory of sub-module, then it's regular library module dependency, otherwise it's a wrapped aar module.
    return if (androidLibrary.project != null && !isLocalAarModule(androidLibrary)) {
      createIdeModuleLibrary(androidLibrary, computeAddress(androidLibrary))
    }
    else {
      val core = IdeAndroidLibraryCore.create(
              artifactAddress = computeAddress(androidLibrary),
              folder = androidLibrary.folder,
              manifest = androidLibrary.manifest.path,
              jarFile = androidLibrary.jarFile.path,
              compileJarFile = (copyNewProperty(androidLibrary::getCompileJarFile) ?: androidLibrary.jarFile).path,
              resFolder = androidLibrary.resFolder.path,
              resStaticLibrary = copyNewProperty(androidLibrary::getResStaticLibrary),
              assetsFolder = androidLibrary.assetsFolder.path,
              localJars = androidLibrary.localJars.map { it.path },
              jniFolder = androidLibrary.jniFolder.path,
              aidlFolder = androidLibrary.aidlFolder.path,
              renderscriptFolder = androidLibrary.renderscriptFolder.path,
              proguardRules = androidLibrary.proguardRules.path,
              lintJar = androidLibrary.lintJar.path,
              externalAnnotations = androidLibrary.externalAnnotations.path,
              publicResources = androidLibrary.publicResources.path,
              artifact = androidLibrary.bundle,
              symbolFile = getSymbolFilePath(
                      androidLibrary),
              deduplicate = { strings.getOrPut(this) { this } }
      )
      val isProvided = copyNewProperty(androidLibrary::isProvided, false)
      IdeAndroidLibrary(androidLibraryCores.internCore(core), isProvided)
    }
  }

  /**
   * @param javaLibrary Instance of [JavaLibrary] returned by android plugin.
   * @return Instance of [Library] based on dependency type.
   */
  fun libraryFrom(javaLibrary: JavaLibrary): IdeLibrary {
    val project = copyNewProperty(javaLibrary::getProject)
    return if (project != null) {
      // Java modules don't have variant.
      createIdeModuleLibrary(javaLibrary, computeAddress(javaLibrary))
    }
    else {
      val core = IdeJavaLibraryCore(
              artifactAddress = computeAddress(javaLibrary),
              artifact = javaLibrary.jarFile
      )
      val isProvided = copyNewProperty(javaLibrary::isProvided, false)
      IdeJavaLibrary(javaLibraryCores.internCore(core), isProvided)
    }
  }

  private fun createIdeModuleLibrary(library: AndroidLibrary, artifactAddress: String): IdeLibrary {
    val core = IdeModuleLibraryCore(
            artifactAddress = artifactAddress,
            buildId = copyNewProperty(library::getBuildId),
            projectPath = copyNewProperty(library::getProject),
            variant = copyNewProperty(library::getProjectVariant),
            folder = copyNewProperty(library::getFolder),
            lintJar = copyNewProperty(library::getLintJar)?.path
    )
    val isProvided = copyNewProperty(library::isProvided, false)
    return IdeModuleLibrary(moduleLibraryCores.internCore(core), isProvided)
  }

  private fun createIdeModuleLibrary(library: JavaLibrary, artifactAddress: String): IdeLibrary {
    val core = IdeModuleLibraryCore(
            artifactAddress = artifactAddress,
            buildId = copyNewProperty(library::getBuildId),
            projectPath = copyNewProperty(library::getProject),
            variant = null,
            folder = null,
            lintJar = null
    )
    val isProvided = copyNewProperty(library::isProvided, false)
    return IdeModuleLibrary(moduleLibraryCores.internCore(core), isProvided)
  }

  fun libraryFrom(projectPath: String, artifactAddress: String, buildId: String?): IdeLibrary {
    val core = IdeModuleLibraryCore(projectPath, artifactAddress, buildId)
    return IdeModuleLibrary(moduleLibraryCores.internCore(core), isProvided = false)
  }

  companion object {
    const val LOCAL_AARS = "__local_aars__"
  }

  private fun getSymbolFilePath(androidLibrary: AndroidLibrary): String {
    return try {
      androidLibrary.symbolFile.path
    }
    catch (e: UnsupportedOperationException) {
      File(androidLibrary.folder, SdkConstants.FN_RESOURCE_TEXT).path
    }
  }

  /**
   * @param library Instance of level 1 Library.
   * @return The artifact address that can be used as unique identifier in global library map.
   */
  fun computeAddress(library: Library): String {
    // If the library is an android module dependency, use projectId:projectPath::variant as unique identifier.
    // MavenCoordinates cannot be used because it doesn't contain variant information, which results
    // in the same MavenCoordinates for different variants of the same module.
    try {
      if (library.project != null && library is AndroidLibrary) {
        return ((copyNewProperty(library::getBuildId)).orEmpty()
                + library.getProject()
                + "::"
                + library.projectVariant)
      }
    }
    catch (ex: UnsupportedOperationException) {
      // getProject() isn't available for pre-2.0 plugins. Proceed with MavenCoordinates.
      // Anyway pre-2.0 plugins don't have variant information for module dependency.
    }
    val coordinate: IdeMavenCoordinates = computeResolvedCoordinate(library)
    var artifactId = coordinate.artifactId
    if (artifactId.startsWith(":")) {
      artifactId = artifactId.substring(1)
    }
    artifactId = artifactId.replace(':', '.')
    var address = coordinate.groupId + ":" + artifactId + ":" + coordinate.version
    val classifier = coordinate.classifier
    if (classifier != null) {
      address = "$address:$classifier"
    }
    val packaging = coordinate.packaging
    address = "$address@$packaging"
    return address
  }

  /**
   * @param projectIdentifier Instance of ProjectIdentifier.
   * @return The artifact address that can be used as unique identifier in global library map.
   */
  fun computeAddress(projectIdentifier: Dependencies.ProjectIdentifier): String {
    return projectIdentifier.buildId + "@@" + projectIdentifier.projectPath
  }

  /** Indicates whether the given library is a module wrapping an AAR file.  */
  fun isLocalAarModule(androidLibrary: AndroidLibrary): Boolean {
    val projectPath = androidLibrary.project ?: return false
    val buildFolderPath = buildFolderPaths.findBuildFolderPath(
      projectPath,
      copyNewProperty(androidLibrary::getBuildId)
    )
    // If the aar bundle is inside of build directory, then it's a regular library module dependency, otherwise it's a wrapped aar module.
    return (buildFolderPath != null
            && !FileUtils.isFileInDirectory(androidLibrary.bundle, buildFolderPath))
  }

  private fun computeResolvedCoordinate(library: Library): IdeMavenCoordinatesImpl {
    // Although getResolvedCoordinates is annotated with @NonNull, it can return null for plugin 1.5,
    // when the library dependency is from local jar.
    return if (library.resolvedCoordinates != null) {
      mavenCoordinatesFrom(library.resolvedCoordinates)
    }
    else {
      val jarFile: File =
              if (library is JavaLibrary) {
                library.jarFile
              }
              else {
                (library as AndroidLibrary).bundle
              }
      mavenCoordinatesFrom(jarFile)
    }
  }


  /** Call this method on level 1 Dependencies model.  */
  private fun createFromDependencies(dependencies: Dependencies): IdeDependencies {
    val worker = Worker(dependencies)
    return worker.createInstance()
  }

  private inner class Worker(private val dependencies: Dependencies) {
    // Map from unique artifact address to level2 library instance. The library instances are
    // supposed to be shared by all artifacts. When creating IdeLevel2Dependencies, check if current library is available in this map,
    // if it's available, don't create new one, simple add reference to it. If it's not available, create new instance and save
    // to this map, so it can be reused the next time when the same library is added.
    private val librariesById = mutableMapOf<String, IdeLibrary>()

    fun createInstance(): IdeDependencies {
      val visited = mutableSetOf<String>()
      populateAndroidLibraries(dependencies.libraries, visited)
      populateJavaLibraries(dependencies.javaLibraries, visited)
      populateModuleDependencies(dependencies, visited)
      val jars: Collection<File> = try {
        dependencies.runtimeOnlyClasses
      }
      catch (e: UnsupportedOperationException) {
        // Gradle older than 3.4.
        emptyList()
      }
      return createInstance(visited, jars)
    }

    private fun populateModuleDependencies(dependencies: Dependencies, visited: MutableSet<String>) {
      try {
        for (identifier in dependencies.javaModules) {
          createModuleLibrary(
                  visited,
                  identifier.projectPath,
                  computeAddress(identifier),
                  identifier.buildId)
        }
      }
      catch (ignored: UnsupportedOperationException) {
        // Dependencies::getJavaModules is available for AGP 3.1+. Use
        // Dependencies::getProjects for the old plugins.
        for (projectPath in dependencies.projects) {
          createModuleLibrary(visited, projectPath, projectPath, null)
        }
      }
    }

    private fun createModuleLibrary(
            visited: MutableSet<String>,
            projectPath: String,
            artifactAddress: String,
            buildId: String?
    ) {
      if (!visited.contains(artifactAddress)) {
        visited.add(artifactAddress)
        librariesById.computeIfAbsent(artifactAddress) { libraryFrom(projectPath, artifactAddress, buildId) }
      }
    }

    private fun populateAndroidLibraries(
            androidLibraries: Collection<AndroidLibrary>,
            visited: MutableSet<String>
    ) {
      for (androidLibrary in androidLibraries) {
        val address = computeAddress(androidLibrary)
        if (!visited.contains(address)) {
          visited.add(address)
          librariesById.computeIfAbsent(address) { libraryFrom(androidLibrary) }
          populateAndroidLibraries(androidLibrary.libraryDependencies, visited)
          populateJavaLibraries(getJavaDependencies(androidLibrary), visited)
        }
      }
    }

    private fun getJavaDependencies(androidLibrary: AndroidLibrary): Collection<JavaLibrary> {
      return try {
        androidLibrary.javaDependencies
      }
      catch (e: UnsupportedOperationException) {
        emptyList()
      }
    }

    private fun populateJavaLibraries(
            javaLibraries: Collection<JavaLibrary>,
            visited: MutableSet<String>) {
      for (javaLibrary in javaLibraries) {
        val address = computeAddress(javaLibrary)
        if (!visited.contains(address)) {
          visited.add(address)
          librariesById.computeIfAbsent(address) { libraryFrom(javaLibrary) }
          populateJavaLibraries(javaLibrary.dependencies, visited)
        }
      }
    }

    private fun createInstance(
            artifactAddresses: Collection<String>,
            runtimeOnlyJars: Collection<File>
    ): IdeDependencies {
      val androidLibraries = ImmutableList.builder<IdeLibrary?>()
      val javaLibraries = ImmutableList.builder<IdeLibrary?>()
      val moduleDependencies = ImmutableList.builder<IdeLibrary?>()
      for (address in artifactAddresses) {
        val library = librariesById[address]!!
        when (library.type) {
          IdeLibrary.LibraryType.LIBRARY_ANDROID -> androidLibraries.add(library)
          IdeLibrary.LibraryType.LIBRARY_JAVA -> javaLibraries.add(library)
          IdeLibrary.LibraryType.LIBRARY_MODULE -> moduleDependencies.add(library)
          else -> throw UnsupportedOperationException("Unknown library type " + library.type)
        }
      }
      return IdeDependenciesImpl(
              androidLibraries.build(),
              javaLibraries.build(),
              moduleDependencies.build(),
              ImmutableList.copyOf(runtimeOnlyJars))
    }
  }

  inline fun <K, V> copy(original: () -> Collection<K>, mapper: (K) -> V): List<V> =
          try {
            original()
          }
          catch (ignored: UnsupportedOperationException) {
            listOf<K>()
          }.map(mapper)

  inline fun <K, V> copy(original: () -> Set<K>, mapper: (K) -> V): Set<V> =
    try {
      original()
    }
    catch (ignored: UnsupportedOperationException) {
      setOf<K>()
    }
      .map(mapper)
      .toSet()

  @JvmName("copyNullableCollection")
  inline fun <K, V> copy(original: () -> Collection<K>?, mapper: (K) -> V): List<V>? =
    try {
      original()
    }
    catch (ignored: UnsupportedOperationException) {
      null
    }?.map(mapper)

  inline fun <K, V, R> copy(original: () -> Map<K, V>, mapper: (V) -> R): Map<K, R> =
    try {
      original()
    }
    catch (ignored: UnsupportedOperationException) {
      mapOf<K, V>()
    }.mapValues { (_, v) -> mapper(v) }

    fun <T> copyNewPropertyWithDefault(propertyInvoker: () -> T, defaultValue: () -> T): T {
      return try {
        propertyInvoker()
      }
      catch (ignored: UnsupportedOperationException) {
        defaultValue()
      }
    }

    /**
     * NOTE: Multiple overloads are intentionally ambiguous to prevent lambdas from being used directly.
     *       Please use function references or anonymous functions which seeds type inference.
     **/
    inline fun <T : Any> copyNewProperty(propertyInvoker: () -> T, defaultValue: T): T {
      return try {
        propertyInvoker()
      }
      catch (ignored: UnsupportedOperationException) {
        defaultValue
      }
    }

    /**
     * NOTE: Multiple overloads are intentionally ambiguous to prevent lambdas from being used directly.
     *       Please use function references or anonymous functions which seeds type inference.
     **/
    inline fun <T : Any?> copyNewProperty(propertyInvoker: () -> T?): T? {
      return try {
        propertyInvoker()
      }
      catch (ignored: UnsupportedOperationException) {
        null
      }
    }

    /**
     * NOTE: Multiple overloads are intentionally ambiguous to prevent lambdas from being used directly.
     *       Please use function references or anonymous functions which seeds type inference.
     **/
    inline fun <T : Collection<*>> copyNewProperty(propertyInvoker: () -> T, defaultValue: T): Unit = error(
      "Cannot be called. Use copy() method.")

    /**
     * NOTE: Multiple overloads are intentionally ambiguous to prevent lambdas from being used directly.
     *       Please use function references or anonymous functions which seeds type inference.
     **/
    inline fun <T : Map<*, *>> copyNewProperty(propertyInvoker: () -> T, defaultValue: T): Unit = error(
      "Cannot be called. Use copy() method.")

    /**
     * NOTE: Multiple overloads are intentionally ambiguous to prevent lambdas from being used directly.
     *       Please use function references or anonymous functions which seeds type inference.
     **/
    @JvmName("impossibleCopyNewCollectionProperty")
    inline fun <T : Collection<*>?> copyNewProperty(propertyInvoker: () -> T): Unit = error("Cannot be called. Use copy() method.")

    /**
     * NOTE: Multiple overloads are intentionally ambiguous to prevent lambdas from being used directly.
     *       Please use function references or anonymous functions which seeds type inference.
     **/
    @JvmName("impossibleCopyNewMapProperty")
    inline fun <T : Map<*, *>?> copyNewProperty(propertyInvoker: () -> T): Unit = error("Cannot be called. Use copy() method.")

    fun mavenCoordinatesFrom(coordinates: MavenCoordinates): IdeMavenCoordinatesImpl {
      return IdeMavenCoordinatesImpl(
        coordinates.groupId,
        coordinates.artifactId,
        coordinates.version,
        coordinates.packaging,
        coordinates.classifier
      )
    }

    fun mavenCoordinatesFrom(localJar: File): IdeMavenCoordinatesImpl {
      return IdeMavenCoordinatesImpl(LOCAL_AARS, localJar.path, "unspecified", "jar", null)
    }

  inline fun <K : Any, V> copyModel(key: K, mappingFunction: (K) -> V): V = mappingFunction(key)

  @JvmName("copyModelNullable")
  inline fun <K : Any, V> copyModel(key: K?, mappingFunction: (K) -> V): V? = key?.let(mappingFunction)

  inline fun <K, V : Any> copyNewModel(
    getter: () -> K?,
    mapper: (K) -> V
  ): V? {
    return try {
      val key: K? = getter()
      if (key != null) mapper(key) else null
    }
    catch (ignored: UnsupportedOperationException) {
      null
    }
  }

  fun deduplicateString(s: String): String = strings.putIfAbsent(s, s) ?: s
  fun deduplicateFile(f: File): File = File(f.path.deduplicate())
  fun String.deduplicate() = deduplicateString(this)
}

private fun <T> MutableMap<T, T>.internCore(core: T): T = getOrPut(core) { core }

