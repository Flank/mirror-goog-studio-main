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

import com.android.build.FilterData
import com.android.build.OutputFile
import com.android.build.VariantOutput
import com.android.builder.model.AaptOptions
import com.android.builder.model.AndroidArtifact
import com.android.builder.model.AndroidGradlePluginProjectFlags
import com.android.builder.model.AndroidProject
import com.android.builder.model.ApiVersion
import com.android.builder.model.BuildType
import com.android.builder.model.BuildTypeContainer
import com.android.builder.model.ClassField
import com.android.builder.model.DependenciesInfo
import com.android.builder.model.JavaArtifact
import com.android.builder.model.JavaCompileOptions
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
import com.android.ide.common.gradle.model.IdeDependenciesInfo
import com.android.ide.common.gradle.model.IdeLintOptions
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
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.common.collect.ImmutableSortedSet
import com.google.common.collect.Iterables
import java.io.File
import java.util.HashMap

class ModelCache @JvmOverloads constructor(private val myStrings: MutableMap<String, String> = HashMap()) {

  fun androidProjectFrom(
    project: AndroidProject,
    dependenciesFactory: IdeDependenciesFactory,
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
        variants.map { variant: Variant -> variantFrom(variant, dependenciesFactory, parsedModelVersion) },
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
    dependenciesFactory: IdeDependenciesFactory,
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
            dependenciesFactory.create(artifact),
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

  fun javaArtifactFrom(
    artifact: JavaArtifact,
    dependenciesFactory: IdeDependenciesFactory
  ): IdeJavaArtifactImpl {
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
      dependenciesFactory.create(artifact),
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
    dependenciesFactory: IdeDependenciesFactory,
    modelVersion: GradleVersion?
  ): IdeVariantImpl =
    IdeVariantImpl(
      name = variant.name,
      displayName = variant.displayName,
      mainArtifact = copyModel(variant.mainArtifact) { androidArtifactFrom(it, dependenciesFactory, modelVersion) },
      extraAndroidArtifacts = copy(variant::getExtraAndroidArtifacts) { androidArtifactFrom(it, dependenciesFactory, modelVersion) },
      extraJavaArtifacts = copy(variant::getExtraJavaArtifacts) { javaArtifactFrom(it, dependenciesFactory) },
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

  companion object {

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
    @JvmStatic
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
    @JvmStatic
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
    @JvmStatic
    inline fun <T : Collection<*>> copyNewProperty(propertyInvoker: () -> T, defaultValue: T): Unit = error(
      "Cannot be called. Use copy() method.")

    /**
     * NOTE: Multiple overloads are intentionally ambiguous to prevent lambdas from being used directly.
     *       Please use function references or anonymous functions which seeds type inference.
     **/
    @JvmStatic
    inline fun <T : Map<*, *>> copyNewProperty(propertyInvoker: () -> T, defaultValue: T): Unit = error(
      "Cannot be called. Use copy() method.")

    /**
     * NOTE: Multiple overloads are intentionally ambiguous to prevent lambdas from being used directly.
     *       Please use function references or anonymous functions which seeds type inference.
     **/
    @JvmStatic
    @JvmName("impossibleCopyNewCollectionProperty")
    inline fun <T : Collection<*>?> copyNewProperty(propertyInvoker: () -> T): Unit = error("Cannot be called. Use copy() method.")

    /**
     * NOTE: Multiple overloads are intentionally ambiguous to prevent lambdas from being used directly.
     *       Please use function references or anonymous functions which seeds type inference.
     **/
    @JvmStatic
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

    const val LOCAL_AARS = "__local_aars__"
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

  fun deduplicateString(s: String): String = myStrings.putIfAbsent(s, s) ?: s
  fun deduplicateFile(f: File): File = File(f.path.deduplicate())
  fun String.deduplicate() = deduplicateString(this)
}
