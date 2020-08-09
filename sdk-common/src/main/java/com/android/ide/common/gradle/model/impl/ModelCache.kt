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
import com.android.builder.model.AaptOptions
import com.android.builder.model.AndroidArtifact
import com.android.builder.model.AndroidArtifactOutput
import com.android.builder.model.AndroidProject
import com.android.builder.model.ApiVersion
import com.android.builder.model.BuildType
import com.android.builder.model.BuildTypeContainer
import com.android.builder.model.ClassField
import com.android.builder.model.JavaArtifact
import com.android.builder.model.JavaCompileOptions
import com.android.builder.model.MavenCoordinates
import com.android.builder.model.ProductFlavor
import com.android.builder.model.ProductFlavorContainer
import com.android.builder.model.SigningConfig
import com.android.builder.model.SourceProviderContainer
import com.android.builder.model.SyncIssue
import com.android.builder.model.TestOptions
import com.android.builder.model.TestedTargetVariant
import com.android.builder.model.Variant
import com.android.builder.model.VectorDrawablesOptions
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
import com.android.ide.common.gradle.model.impl.IdeAndroidGradlePluginProjectFlagsImpl.Companion.createFrom
import com.android.ide.common.gradle.model.impl.IdeDependenciesInfoImpl.Companion.createOrNull
import com.android.ide.common.gradle.model.impl.IdeLintOptionsImpl.Companion.createFrom
import com.android.ide.common.gradle.model.impl.IdeVariantBuildInformationImpl.Companion.createFrom
import com.android.ide.common.gradle.model.impl.IdeVariantImpl.Companion.createFrom
import com.android.ide.common.gradle.model.impl.IdeViewBindingOptionsImpl.Companion.createFrom
import com.android.ide.common.gradle.model.impl.ndk.v1.IdeNativeToolchainImpl
import com.android.ide.common.gradle.model.ndk.v1.IdeNativeToolchain
import com.android.ide.common.repository.GradleVersion
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.common.collect.ImmutableSortedSet
import com.google.common.collect.Iterables
import java.io.File
import java.util.ArrayList
import java.util.HashMap
import java.util.LinkedHashSet

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
    val buildTypesCopy: Collection<IdeBuildTypeContainer> = copy(project.buildTypes, ::buildTypeContainerFrom)
    val productFlavorCopy: Collection<IdeProductFlavorContainer> = copy(project.productFlavors, ::productFlavorContainerFrom)
    val syncIssuesCopy: Collection<IdeSyncIssue> = copy(syncIssues, ::syncIssueFrom)
    val variantNames = variants.map { it.name }.toSet()
    val variantsCopy: Collection<IdeVariant> = ImmutableList.copyOf(
      Iterables.concat<IdeVariant>(
        copy(variants) { variant: Variant -> createFrom(variant, this, dependenciesFactory, parsedModelVersion) },
        cachedVariants.filter { !variantNames.contains(it.name) }
      ))
    val variantNamesCopy: Collection<String> =
      copyNewPropertyWithDefault(
        { ImmutableList.copyOf(project.variantNames) },
        { computeVariantNames(variantsCopy) })
    val defaultVariantCopy = copyNewPropertyWithDefault({ project.defaultVariant }, { getDefaultVariant(variantNamesCopy) })
    val flavorDimensionCopy: Collection<String> = copyNewProperty({ ImmutableList.copyOf(project.flavorDimensions) }, emptyList())
    val bootClasspathCopy: Collection<String> = ImmutableList.copyOf(project.bootClasspath)
    val nativeToolchainsCopy: Collection<IdeNativeToolchain> = copy(project.nativeToolchains) { IdeNativeToolchainImpl(it) }
    val signingConfigsCopy: Collection<IdeSigningConfig> = copy(project.signingConfigs, ::signingConfigFrom)
    val lintOptionsCopy: IdeLintOptions = copyModel(project.lintOptions, { createFrom(it, parsedModelVersion) })

    // We need to use the unresolved dependencies to support older versions of the Android
    // Gradle Plugin.
    val unresolvedDependenciesCopy: Set<String> = ImmutableSet.copyOf(project.unresolvedDependencies)
    val javaCompileOptionsCopy = copyModel(project.javaCompileOptions, ::javaCompileOptionsFrom)
    val aaptOptionsCopy = copyModel(project.aaptOptions, ::aaptOptionsFrom)
    val dynamicFeaturesCopy: Collection<String> = ImmutableList.copyOf(copyNewProperty({ project.dynamicFeatures }, ImmutableList.of()))
    val variantBuildInformation = createVariantBuildInformation(project, parsedModelVersion)
    val viewBindingOptionsCopy: IdeViewBindingOptions? = copyNewProperty({ createFrom(project.viewBindingOptions) })
    val dependenciesInfoCopy: IdeDependenciesInfo? = copyNewProperty({ createOrNull(project.dependenciesInfo) })
    val buildToolsVersionCopy = copyNewProperty({ project.buildToolsVersion })
    val ndkVersionCopy = copyNewProperty({ project.ndkVersion })
    val groupId = if (parsedModelVersion != null && parsedModelVersion.isAtLeast(3, 6, 0, "alpha", 5, false)) project.groupId else null
    val lintRuleJarsCopy: List<File>? = copyNewProperty({ ImmutableList.copyOf(project.lintRuleJars) })

    // AndroidProject#isBaseSplit is always non null.
    val isBaseSplit = copyNewProperty({ project.isBaseSplit }, false)
    val agpFlags: IdeAndroidGradlePluginProjectFlags = copyNewProperty(
      { createFrom(project.flags) },
      IdeAndroidGradlePluginProjectFlagsImpl()
    )
    return IdeAndroidProjectImpl(
      project.modelVersion,
      parsedModelVersion,
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
      unresolvedDependenciesCopy,
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
      copyNewProperty({ project.pluginGeneration }) != null,
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
      project.variantsBuildInformation.map(::createFrom)
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
    val outputs: List<AndroidArtifactOutput>
    return try {
      outputs = ArrayList(artifact.outputs)
      copy(outputs, this@ModelCache::androidArtifactOutputFrom)
    }
    catch (e: RuntimeException) {
      System.err.println("Caught exception: $e")
      // See http://b/64305584
      emptyList()
    }
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
      copyNewProperty({ artifact.javaResourcesFolder }),
      ImmutableSet.copyOf(IdeBaseArtifactImpl.getIdeSetupTaskNames(artifact)),
      LinkedHashSet(IdeBaseArtifactImpl.getGeneratedSourceFolders(artifact)),
      IdeBaseArtifactImpl.createSourceProvider(this, artifact.variantSourceProvider),
      IdeBaseArtifactImpl.createSourceProvider(this, artifact.multiFlavorSourceProvider),
      copyNewProperty({ artifact.additionalClassesFolders }, emptySet()),
      dependenciesFactory.create(artifact),
      copyOutputs(artifact, agpVersion),
      artifact.applicationId,
      artifact.sourceGenTaskName,
      ImmutableList.copyOf(artifact.generatedResourceFolders),
      artifact.signingConfigName,
      ImmutableSet.copyOf( // In AGP 4.0 and below abiFilters was nullable, normalize null to empty set.
        artifact.abiFilters.orEmpty()),
      artifact.isSigned,
      copyNewProperty({ ArrayList(artifact.additionalRuntimeApks) }, emptyList()),
      copyNewProperty({ artifact.testOptions }, ::testOptionsFrom, null),
      copyNewProperty({ artifact.instrumentedTestTaskName }, ::deduplicateString, null),
      copyNewProperty({ artifact.bundleTaskName }, ::deduplicateString, null),
      copyNewProperty({ artifact.bundleTaskOutputListingFile }, ::deduplicateString, null),
      copyNewProperty({ artifact.apkFromBundleTaskName }, ::deduplicateString, null),
      copyNewProperty({ artifact.apkFromBundleTaskOutputListingFile }, ::deduplicateString, null),
      copyNewProperty({ artifact.codeShrinker })
    )
  }

  fun androidArtifactOutputFrom(output: OutputFile): IdeAndroidArtifactOutputImpl {
    return IdeAndroidArtifactOutputImpl(
      copy(output.outputs) { IdeOutputFileImpl(it, this) },
      copyNewProperty({ ImmutableList.copyOf(output.filterTypes) }, emptyList()),
      IdeVariantOutputImpl.copyFilters(output, this),
      copyNewProperty({ output.mainOutputFile }, { IdeOutputFileImpl(it, this) }, null),
      copyNewProperty({ output.outputType }),
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
      copyModel(container.sourceProvider, { IdeSourceProviderImpl.createFrom(it, ::deduplicateString) }),
      copy(container.extraSourceProviders, this@ModelCache::sourceProviderContainerFrom)
    )
  }

  fun buildTypeFrom(buildType: BuildType): IdeBuildTypeImpl {
    return IdeBuildTypeImpl(
      buildType.name,
      copy(buildType.resValues, this@ModelCache::classFieldFrom),
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
      copyNewProperty({ artifact.javaResourcesFolder }),
      ImmutableSet.copyOf(IdeBaseArtifactImpl.getIdeSetupTaskNames(artifact)),
      LinkedHashSet(IdeBaseArtifactImpl.getGeneratedSourceFolders(artifact)),
      IdeBaseArtifactImpl.createSourceProvider(this, artifact.variantSourceProvider),
      IdeBaseArtifactImpl.createSourceProvider(this, artifact.multiFlavorSourceProvider),
      copyNewProperty({ artifact.additionalClassesFolders }, emptySet()),
      dependenciesFactory.create(artifact),
      copyNewProperty({ artifact.mockablePlatformJar })
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
      copyModel(container.sourceProvider, { IdeSourceProviderImpl.createFrom(it, ::deduplicateString) }),
      copy(container.extraSourceProviders, ::sourceProviderContainerFrom)
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

  private fun copy(apiVersion: ApiVersion?): IdeApiVersionImpl? {
    return if (apiVersion != null) copyModel(apiVersion, ::apiVersionFrom) else null
  }

  private fun copy(signingConfig: SigningConfig?): IdeSigningConfig? {
    return if (signingConfig != null) copyModel(signingConfig, ::signingConfigFrom) else null
  }

  fun productFlavorFrom(flavor: ProductFlavor): IdeProductFlavorImpl {
    return IdeProductFlavorImpl(
      flavor.name,
      copy(flavor.resValues, this@ModelCache::classFieldFrom),
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
      copy(flavor.minSdkVersion),
      copy(flavor.targetSdkVersion),
      flavor.maxSdkVersion,
      flavor.testApplicationId,
      flavor.testInstrumentationRunner,
      flavor.testFunctionalTest,
      flavor.testHandleProfiling,
      copy(flavor.signingConfig)
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
      copyModel(container.sourceProvider, { IdeSourceProviderImpl.createFrom(it, ::deduplicateString) })
    )
  }

  fun syncIssueFrom(issue: SyncIssue): IdeSyncIssueImpl {
    return IdeSyncIssueImpl(
      issue.message,
      copyNewProperty(issue::multiLineMessage),
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

  fun <K, V> copy(original: Collection<K>, mapper: (K) -> V): List<V> = original.map(mapper)

  fun <K, V, R> copy(original: Map<K, V>, mapper: (V) -> R): Map<K, R> = original.mapValues { (_, v) -> mapper(v) }

  companion object {
    fun copy(original: Set<String>): Set<String> = original.toSet()

    fun <T> copyNewPropertyWithDefault(propertyInvoker: () -> T, defaultValue: () -> T): T {
      return try {
        propertyInvoker()
      }
      catch (ignored: UnsupportedOperationException) {
        defaultValue()
      }
    }

    @JvmStatic
    inline fun <T: Any> copyNewProperty(propertyInvoker: () -> T, defaultValue: T): T {
      return try {
        propertyInvoker()
      }
      catch (ignored: UnsupportedOperationException) {
        defaultValue
      }
    }

    @JvmStatic
    inline fun <T: Any?> copyNewProperty(propertyInvoker: () -> T?): T? {
      return try {
        propertyInvoker()
      }
      catch (ignored: UnsupportedOperationException) {
        null
      }
    }

    fun <K, V> copyNewProperty(
      keyCreator: () -> K?,
      mapper: (K) -> V,
      defaultValue: V
    ): V {
      return try {
        val key: K? = keyCreator()
        if (key != null) mapper(key) else defaultValue
      }
      catch (ignored: UnsupportedOperationException) {
        defaultValue
      }
    }

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

  fun <K, V> copyModel(key: K, mappingFunction: (K) -> V): V = mappingFunction(key)

  fun deduplicateString(s: String): String = myStrings.putIfAbsent(s, s) ?: s
}

