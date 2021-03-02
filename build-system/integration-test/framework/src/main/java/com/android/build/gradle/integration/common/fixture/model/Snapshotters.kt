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

package com.android.build.gradle.integration.common.fixture.model

import com.android.build.gradle.internal.cxx.configure.ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION
import com.android.builder.core.ToolsRevisionUtils
import com.android.builder.model.v2.dsl.BaseConfig
import com.android.builder.model.v2.dsl.BuildType
import com.android.builder.model.v2.dsl.ClassField
import com.android.builder.model.v2.dsl.DependenciesInfo
import com.android.builder.model.v2.dsl.ProductFlavor
import com.android.builder.model.v2.dsl.SigningConfig
import com.android.builder.model.v2.ide.AaptOptions
import com.android.builder.model.v2.ide.AndroidArtifact
import com.android.builder.model.v2.ide.ApiVersion
import com.android.builder.model.v2.ide.ArtifactDependencies
import com.android.builder.model.v2.ide.BaseArtifact
import com.android.builder.model.v2.ide.BuildTypeContainer
import com.android.builder.model.v2.ide.BundleInfo
import com.android.builder.model.v2.ide.GraphItem
import com.android.builder.model.v2.ide.JavaArtifact
import com.android.builder.model.v2.ide.JavaCompileOptions
import com.android.builder.model.v2.ide.Library
import com.android.builder.model.v2.ide.LintOptions
import com.android.builder.model.v2.ide.ProductFlavorContainer
import com.android.builder.model.v2.ide.SourceProvider
import com.android.builder.model.v2.ide.TestInfo
import com.android.builder.model.v2.ide.TestedTargetVariant
import com.android.builder.model.v2.ide.Variant
import com.android.builder.model.v2.ide.VectorDrawablesOptions
import com.android.builder.model.v2.ide.ViewBindingOptions
import com.android.builder.model.v2.models.AndroidProject
import com.android.builder.model.v2.models.GlobalLibraryMap
import com.android.builder.model.v2.models.VariantDependencies
import com.android.builder.model.v2.models.ndk.NativeAbi
import com.android.builder.model.v2.models.ndk.NativeModule
import com.android.builder.model.v2.models.ndk.NativeVariant

// --------------
// snapshot fixtures specific to each model class

internal fun ModelSnapshotter<AndroidProject>.snapshotAndroidProject() {
    item("projectType", AndroidProject::projectType)
    item("path", AndroidProject::path)
    item("groupId", AndroidProject::groupId)
    item("flavorDimensions", AndroidProject::flavorDimensions)
    item("compileTarget", AndroidProject::compileTarget)
    item("buildFolder", AndroidProject::buildFolder)
    item("resourcePrefix", AndroidProject::resourcePrefix)
    item("buildToolsVersion", AndroidProject::buildToolsVersion) { version ->
        version?.let { normalizeBuildToolsVersion(it) }
    }
    item("dynamicFeatures", AndroidProject::dynamicFeatures)
    valueList("bootClasspath", AndroidProject::bootClasspath)
    dataObject("defaultConfig", AndroidProject::defaultConfig) {
        snapshotProductFlavorContainer()
    }
    objectList(
        name = "buildTypes",
        propertyAction = AndroidProject::buildTypes,
        nameAction = { "buildTypeContainer(${buildType.name})" },
        idAction = { buildType.name },
        sortAction = { collection -> collection?.sortedBy { it.buildType.name } }
    ) {
        snapshotBuildTypeContainer()
    }
    objectList(
        name = "productFlavors",
        propertyAction = AndroidProject::productFlavors,
        nameAction = { "productFlavorContainer(${productFlavor.name})" },
        idAction = { productFlavor.name },
        sortAction = { collection -> collection?.sortedBy { it.productFlavor.name } }
    ) {
        snapshotProductFlavorContainer()
    }
    objectList(
        name = "signingConfigs",
        propertyAction = AndroidProject::signingConfigs,
        nameAction = { "signingConfig($name)" },
        idAction = { name },
        sortAction = { collection -> collection?.sortedBy { it.name } }
    ) {
        item("name", SigningConfig::name)
        item("storeFile", SigningConfig::storeFile)
        item("storePassword", SigningConfig::storePassword)
        item("keyAlias", SigningConfig::keyAlias)
        item("keyPassword", SigningConfig::keyPassword)
        item("enableV1Signing", SigningConfig::enableV1Signing)
        item("enableV2Signing", SigningConfig::enableV2Signing)
        item("enableV3Signing", SigningConfig::enableV3Signing)
        item("enableV4Signing", SigningConfig::enableV4Signing)
        item("isSigningReady", SigningConfig::isSigningReady)
    }
    objectList(
        name = "variants",
        propertyAction = AndroidProject::variants,
        nameAction = { "variant(${name})" },
        idAction = { name },
        sortAction = { collection -> collection?.sortedBy { it.name } }
    ) {
        snapshotVariant()
    }
    valueList("lintRuleJars", AndroidProject::lintRuleJars) { it?.sorted() }
    dataObject("aaptOptions", AndroidProject::aaptOptions) {
        item("namespacing", AaptOptions::namespacing)
    }
    dataObject("lintOptions", AndroidProject::lintOptions) {
        snapshotLintOptions()
    }
    dataObject("javaCompileOptions", AndroidProject::javaCompileOptions) {
        item("encoding", JavaCompileOptions::encoding)
        item("sourceCompatibility", JavaCompileOptions::sourceCompatibility)
        item("targetCompatibility", JavaCompileOptions::targetCompatibility)
        item(
            "isCoreLibraryDesugaringEnabled",
            JavaCompileOptions::isCoreLibraryDesugaringEnabled
        )
    }
    dataObject("viewBindingOptions", AndroidProject::viewBindingOptions) {
        item("isEnabled", ViewBindingOptions::isEnabled)
    }
    dataObject("dependenciesInfo", AndroidProject::dependenciesInfo) {
        item("includeInApk", DependenciesInfo::includeInApk)
        item("includeInBundle", DependenciesInfo::includeInBundle)
    }

    valueList(
        name = "flags",
        propertyAction = { flags.booleanFlagMap?.entries },
        formatAction = { "${key.name} -> $value" }
    ) { collection ->
        collection?.sortedBy { it.key.name }
    }
}

internal fun ModelSnapshotter<NativeModule>.snapshotNativeModule() {
    item("name", NativeModule::name)
    objectList(
        "variants",
        NativeModule::variants,
        nameAction =  { name },
        idAction = { name },
        sortAction =  { collection -> collection?.sortedBy { it.name } }
    ) {
        snapshotNativeVariant()
    }
    item("nativeBuildSystem", NativeModule::nativeBuildSystem)
    item("ndkVersion", NativeModule::ndkVersion) {
        if (it == ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION) "{DEFAULT_NDK_VERSION}" else it
    }
    item("defaultNdkVersion", NativeModule::defaultNdkVersion) {
        if (it == ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION) "{DEFAULT_NDK_VERSION}" else it
    }
    item("externalNativeBuildFile", NativeModule::externalNativeBuildFile)
}

private fun ModelSnapshotter<NativeVariant>.snapshotNativeVariant() {
    objectList(
        name = "abis",
        propertyAction = NativeVariant::abis,
        nameAction = { name },
        idAction = { name },
        sortAction =  { collection -> collection?.sortedBy { it.name } }
    ) {
        snapshotNativeAbi()
    }
}

private fun ModelSnapshotter<NativeAbi>.snapshotNativeAbi() {
    item("sourceFlagsFile", NativeAbi::sourceFlagsFile)
    item("symbolFolderIndexFile", NativeAbi::symbolFolderIndexFile)
    item("buildFileIndexFile", NativeAbi::buildFileIndexFile)
    item("additionalProjectFilesIndexFile", NativeAbi::additionalProjectFilesIndexFile)
}

private fun ModelSnapshotter<ProductFlavorContainer>.snapshotProductFlavorContainer() {
    dataObject("productFlavor", ProductFlavorContainer::productFlavor) {
        snapshotProductFlavor()
    }
    dataObject("sourceProvider", ProductFlavorContainer::sourceProvider) {
        snapshotSourceProvider()
    }
    dataObject("androidTestSourceProvider", ProductFlavorContainer::androidTestSourceProvider) {
        snapshotSourceProvider()
    }
    dataObject("unitTestSourceProvider", ProductFlavorContainer::unitTestSourceProvider) {
        snapshotSourceProvider()
    }
}

private fun ModelSnapshotter<ProductFlavor>.snapshotProductFlavor() {
    snapshotBaseConfig()
    item("dimension", ProductFlavor::dimension)
    item("applicationId", ProductFlavor::applicationId)
    item("versionCode", ProductFlavor::versionCode)
    item("versionName", ProductFlavor::versionName)
    dataObject("minSdkVersion", ProductFlavor::minSdkVersion) {
        item("apiLevel", ApiVersion::apiLevel)
        item("codename", ApiVersion::codename)
    }
    dataObject("targetSdkVersion", ProductFlavor::targetSdkVersion) {
        item("apiLevel", ApiVersion::apiLevel)
        item("codename", ApiVersion::codename)
    }
    item("maxSdkVersion", ProductFlavor::maxSdkVersion)
    item("renderscriptTargetApi", ProductFlavor::renderscriptTargetApi)
    item("renderscriptSupportModeEnabled", ProductFlavor::renderscriptSupportModeEnabled)
    item(
        "renderscriptSupportModeBlasEnabled",
        ProductFlavor::renderscriptSupportModeBlasEnabled
    )
    item("renderscriptNdkModeEnabled", ProductFlavor::renderscriptNdkModeEnabled)
    item("testApplicationId", ProductFlavor::testApplicationId)
    item("testInstrumentationRunner", ProductFlavor::testInstrumentationRunner)

    valueList(
        name = "testInstrumentationRunnerArguments",
        propertyAction = { testInstrumentationRunnerArguments.entries },
        formatAction = { "$key -> $value" }
    ) { collection ->
        collection?.sortedBy { it.key }
    }
    item("testHandleProfiling", ProductFlavor::testHandleProfiling)
    item("testFunctionalTest", ProductFlavor::testFunctionalTest)
    item("resourceConfigurations", ProductFlavor::resourceConfigurations) { it?.sorted() }
    item("signingConfig", ProductFlavor::signingConfig)
    item("wearAppUnbundled", ProductFlavor::wearAppUnbundled)
    dataObject("vectorDrawables", ProductFlavor::vectorDrawables) {
        item("generatedDensities", VectorDrawablesOptions::generatedDensities) { it?.sorted() }
        item("useSupportLibrary", VectorDrawablesOptions::useSupportLibrary)
    }
}

private fun ModelSnapshotter<BuildTypeContainer>.snapshotBuildTypeContainer() {
    dataObject("buildType", BuildTypeContainer::buildType) {
        snapshotBuildType()
    }
    dataObject("sourceProvider", BuildTypeContainer::sourceProvider) {
        snapshotSourceProvider()
    }
    dataObject("androidTestSourceProvider", BuildTypeContainer::androidTestSourceProvider) {
        snapshotSourceProvider()
    }
    dataObject("unitTestSourceProvider", BuildTypeContainer::unitTestSourceProvider) {
        snapshotSourceProvider()
    }
}

private fun ModelSnapshotter<BuildType>.snapshotBuildType() {
    snapshotBaseConfig()
    item("isDebuggable", BuildType::isDebuggable)
    item("isTestCoverageEnabled", BuildType::isTestCoverageEnabled)
    item("isPseudoLocalesEnabled", BuildType::isPseudoLocalesEnabled)
    item("isJniDebuggable", BuildType::isJniDebuggable)
    item("isRenderscriptDebuggable", BuildType::isRenderscriptDebuggable)
    item("renderscriptOptimLevel", BuildType::renderscriptOptimLevel)
    item("isMinifyEnabled", BuildType::isMinifyEnabled)
    item("isZipAlignEnabled", BuildType::isZipAlignEnabled)
    item("isEmbedMicroApp", BuildType::isEmbedMicroApp)
    item("signingConfig", BuildType::signingConfig)
}

private fun ModelSnapshotter<out BaseConfig>.snapshotBaseConfig() {
    item("name", BaseConfig::name)
    item("applicationIdSuffix", BaseConfig::applicationIdSuffix)
    item("versionNameSuffix", BaseConfig::versionNameSuffix)

    convertedObjectList(
        name = "buildConfigFields",
        propertyAction = { buildConfigFields?.entries },
        nameAction = { "field(${value.name})" },
        objectAction = { value },
        idAction = { key },
        sortAction = { collection -> collection?.sortedBy { it.key } }
    ) {
        item("name", ClassField::name)
        item("type", ClassField::type)
        item("value", ClassField::value)
        item("documentation", ClassField::documentation)
        item("annotations", ClassField::annotations) { it?.sorted() }
    }
    convertedObjectList(
        name = "resValues",
        propertyAction = { resValues?.entries },
        nameAction = { "value(${value.name})" },
        objectAction = { value },
        idAction = { key },
        sortAction = { collection -> collection?.sortedBy { it.key } }
    ) {
        item("name", ClassField::name)
        item("type", ClassField::type)
        item("value", ClassField::value)
        item("documentation", ClassField::documentation)
        item("annotations", ClassField::annotations) { it?.sorted() }
    }
    valueList("proguardFiles", BaseConfig::proguardFiles) { it?.sorted() }
    valueList("consumerProguardFiles", BaseConfig::consumerProguardFiles) { it?.sorted() }
    valueList("testProguardFiles", BaseConfig::testProguardFiles) { it?.sorted() }
    valueList(
        name = "manifestPlaceholders",
        propertyAction = { manifestPlaceholders.entries },
        formatAction = { "$key -> $value" },
    ) { collection ->
        collection?.sortedBy { it.key }
    }
    item("multiDexEnabled", BaseConfig::multiDexEnabled)
    item("multiDexKeepFile", BaseConfig::multiDexKeepFile)
    item("multiDexKeepProguard", BaseConfig::multiDexKeepProguard)
    item("isDefault", BaseConfig::isDefault)
}

private fun ModelSnapshotter<SourceProvider>.snapshotSourceProvider() {
    item("name", SourceProvider::name)
    item("manifestFile", SourceProvider::manifestFile)
    valueList("javaDirectories", SourceProvider::javaDirectories) { it?.sorted() }
    valueList("kotlinDirectories", SourceProvider::kotlinDirectories) { it?.sorted() }
    valueList("resourcesDirectories", SourceProvider::resourcesDirectories) { it?.sorted() }
    valueList("aidlDirectories", SourceProvider::aidlDirectories) { it?.sorted() }
    valueList("renderscriptDirectories", SourceProvider::renderscriptDirectories) { it?.sorted() }
    valueList("resDirectories", SourceProvider::resDirectories) { it?.sorted() }
    valueList("assetsDirectories", SourceProvider::assetsDirectories) { it?.sorted() }
    valueList("jniLibsDirectories", SourceProvider::jniLibsDirectories) { it?.sorted() }
    valueList("shadersDirectories", SourceProvider::shadersDirectories) { it?.sorted() }
    valueList("mlModelsDirectories", SourceProvider::mlModelsDirectories) { it?.sorted() }
}

private fun ModelSnapshotter<Variant>.snapshotVariant() {
    item("name", Variant::name)
    item("displayName", Variant::name)
    item("buildType", Variant::buildType)
    item("productFlavors", Variant::productFlavors)
    item("isInstantAppCompatible", Variant::isInstantAppCompatible)
    item("desugaredMethods", Variant::desugaredMethods)
    dataObject("mainArtifact", Variant::mainArtifact) {
        snapshotAndroidArtifact()
    }
    dataObject("androidTestArtifact", Variant::androidTestArtifact) {
        snapshotAndroidArtifact()
    }
    dataObject("unitTestArtifact", Variant::unitTestArtifact) {
        snapshotJavaArtifact()
    }
    dataObject("testedTargetVariant", Variant::testedTargetVariant) {
        item("targetProjectPath", TestedTargetVariant::targetProjectPath)
        item("targetVariant", TestedTargetVariant::targetVariant)
    }
}

private fun ModelSnapshotter<AndroidArtifact>.snapshotAndroidArtifact() {
    snapshotBaseArtifact()
    item("isSigned", AndroidArtifact::isSigned)
    item("signingConfigName", AndroidArtifact::signingConfigName)
    item("sourceGenTaskName", AndroidArtifact::sourceGenTaskName)
    item("generatedResourceFolders", AndroidArtifact::generatedResourceFolders) { it?.sorted() }
    item("abiFilters", AndroidArtifact::abiFilters) { it?.sorted() }
    item("assembleTaskOutputListingFile", AndroidArtifact::assembleTaskOutputListingFile)
    dataObject("testInfo", AndroidArtifact::testInfo) {
        item("animationsDisabled", TestInfo::animationsDisabled)
        item("execution", TestInfo::execution)
        item("additionalRuntimeApks", TestInfo::additionalRuntimeApks)
        item("instrumentedTestTaskName", TestInfo::instrumentedTestTaskName)
    }
    dataObject("bundleInfo", AndroidArtifact::bundleInfo) {
        item("bundleTaskName", BundleInfo::bundleTaskName)
        item("bundleTaskOutputListingFile", BundleInfo::bundleTaskOutputListingFile)
        item("apkFromBundleTaskName", BundleInfo::apkFromBundleTaskName)
        item("apkFromBundleTaskOutputListingFile", BundleInfo::apkFromBundleTaskOutputListingFile)
    }
    item("codeShrinker", AndroidArtifact::codeShrinker)
}

private fun ModelSnapshotter<JavaArtifact>.snapshotJavaArtifact() {
    snapshotBaseArtifact()
    item("mockablePlatformJar", JavaArtifact::mockablePlatformJar)
    item("runtimeResourceFolder", JavaArtifact::runtimeResourceFolder)
}

private fun ModelSnapshotter<out BaseArtifact>.snapshotBaseArtifact() {
    item("compileTaskName", BaseArtifact::compileTaskName)
    item("assembleTaskName", BaseArtifact::assembleTaskName)
    valueList("classesFolders", BaseArtifact::classesFolders) { it?.sorted() }
    item("ideSetupTaskNames", BaseArtifact::ideSetupTaskNames) { it?.sorted() }
    valueList("generatedSourceFolders", BaseArtifact::generatedSourceFolders) { it?.sorted() }
    dataObject("variantSourceProvider", BaseArtifact::variantSourceProvider) {
        snapshotSourceProvider()
    }
    dataObject("multiFlavorSourceProvider", BaseArtifact::multiFlavorSourceProvider) {
        snapshotSourceProvider()
    }
}

private fun ModelSnapshotter<LintOptions>.snapshotLintOptions() {
    item("disable", LintOptions::disable) { it?.sorted() }
    item("enable", LintOptions::enable) { it?.sorted() }
    item("check", LintOptions::check) { it?.sorted() }
    item("isAbortOnError", LintOptions::isAbortOnError)
    item("isAbsolutePaths", LintOptions::isAbsolutePaths)
    item("isNoLines", LintOptions::isNoLines)
    item("isQuiet", LintOptions::isQuiet)
    item("isCheckAllWarnings", LintOptions::isCheckAllWarnings)
    item("isIgnoreWarnings", LintOptions::isIgnoreWarnings)
    item("isWarningsAsErrors", LintOptions::isWarningsAsErrors)
    item("isCheckTestSources", LintOptions::isCheckTestSources)
    item("isIgnoreTestSources", LintOptions::isIgnoreTestSources)
    item("isCheckGeneratedSources", LintOptions::isCheckGeneratedSources)
    item("isExplainIssues", LintOptions::isExplainIssues)
    item("isShowAll", LintOptions::isShowAll)
    item("lintConfig", LintOptions::lintConfig)
    item("textReport", LintOptions::textReport)
    item("textOutput", LintOptions::textOutput)
    item("htmlReport", LintOptions::htmlReport)
    item("htmlOutput", LintOptions::htmlOutput)
    item("xmlReport", LintOptions::xmlReport)
    item("xmlOutput", LintOptions::xmlOutput)
    item("sarifReport", LintOptions::sarifReport)
    item("sarifOutput", LintOptions::sarifOutput)
    item("isCheckReleaseBuilds", LintOptions::isCheckReleaseBuilds)
    item("isCheckDependencies", LintOptions::isCheckDependencies)
    item("baselineFile", LintOptions::baselineFile)
    valueList(
        name = "severityOverrides",
        propertyAction = { severityOverrides?.entries },
        formatAction = { "$key -> $value" }
    ) { collection ->
            collection?.sortedBy { it.key }
        }
}

internal fun ModelSnapshotter<VariantDependencies>.snapshotVariantDependencies() {
    item("name", VariantDependencies::name)
    dataObject("mainArtifact", VariantDependencies::mainArtifact) {
        snapshotArtifactDependencies()
    }
    dataObject("androidTestArtifact", VariantDependencies::androidTestArtifact) {
        snapshotArtifactDependencies()
    }
    dataObject("unitTestArtifact", VariantDependencies::unitTestArtifact) {
        snapshotArtifactDependencies()
    }
}

private fun ModelSnapshotter<ArtifactDependencies>.snapshotArtifactDependencies() {
    val graphItemAction: ModelSnapshotter<GraphItem>.() -> Unit = {
        artifactAddress("artifactAddress", GraphItem::artifactAddress)
        item("requestedCoordinates", GraphItem::requestedCoordinates)
        item("dependencies", GraphItem::dependencies)
    }

    objectList(
        name = "compileDependencies",
        propertyAction = ArtifactDependencies::compileDependencies,
        nameAction =  { "GraphItem" },
        idAction = { this.artifactAddress },
        action = graphItemAction
    )

    objectList(
        name = "runtimeDependencies",
        propertyAction = ArtifactDependencies::runtimeDependencies,
        nameAction =  { "GraphItem" },
        idAction = { this.artifactAddress },
        action = graphItemAction
    )
}

internal fun ModelSnapshotter<GlobalLibraryMap>.snapshotGlobalLibraryMap() {
    objectList(
        name = "libraries",
        propertyAction = { libraries.values },
        nameAction = { "library" },
        idAction = { this.artifactAddress },
        sortAction = { collection -> collection?.sortedBy {it.artifactAddress} }
    ) {
        item("type", Library::type)
        artifactAddress("artifactAddress", Library::artifactAddress)
        item("artifact", Library::artifact)
        buildId("buildId", Library::buildId)
        item("projectPath", Library::projectPath)
        item("variant", Library::variant)
        item("compileJarFiles", Library::compileJarFiles)
        item("runtimeJarFiles", Library::runtimeJarFiles)
        item("manifest", Library::manifest)
        item("resFolder", Library::resFolder)
        item("resStaticLibrary", Library::resStaticLibrary)
        item("assetsFolder", Library::assetsFolder)
        item("jniFolder", Library::jniFolder)
        item("aidlFolder", Library::aidlFolder)
        item("renderscriptFolder", Library::renderscriptFolder)
        item("proguardRules", Library::proguardRules)
        item("lintJar", Library::lintJar)
        item("externalAnnotations", Library::externalAnnotations)
        item("publicResources", Library::publicResources)
        item("symbolFile", Library::symbolFile)
    }
}

private fun normalizeBuildToolsVersion(version: String): Any {
    if (version == ToolsRevisionUtils.DEFAULT_BUILD_TOOLS_REVISION.toString()) {
        return PredefinedModelValues.DEFAULT_BUILD_TOOLS_REVISION
    }
    return version
}

internal enum class PredefinedModelValues {
    DEFAULT_BUILD_TOOLS_REVISION;
}
