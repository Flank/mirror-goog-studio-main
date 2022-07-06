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

import com.android.Version.ANDROID_GRADLE_PLUGIN_VERSION
import com.android.build.gradle.integration.common.fixture.DEFAULT_COMPILE_SDK_VERSION
import com.android.SdkConstants.NDK_DEFAULT_VERSION
import com.android.builder.core.ToolsRevisionUtils
import com.android.builder.model.v2.CustomSourceDirectory
import com.android.builder.model.v2.ModelSyncFile
import com.android.builder.model.v2.dsl.BaseConfig
import com.android.builder.model.v2.dsl.BuildType
import com.android.builder.model.v2.dsl.ClassField
import com.android.builder.model.v2.dsl.DependenciesInfo
import com.android.builder.model.v2.dsl.ProductFlavor
import com.android.builder.model.v2.dsl.SigningConfig
import com.android.builder.model.v2.ide.AaptOptions
import com.android.builder.model.v2.ide.AndroidArtifact
import com.android.builder.model.v2.ide.AndroidLibraryData
import com.android.builder.model.v2.ide.ApiVersion
import com.android.builder.model.v2.ide.ArtifactDependencies
import com.android.builder.model.v2.ide.AbstractArtifact
import com.android.builder.model.v2.ide.AndroidGradlePluginProjectFlags
import com.android.builder.model.v2.ide.BasicArtifact
import com.android.builder.model.v2.ide.BasicVariant
import com.android.builder.model.v2.ide.BundleInfo
import com.android.builder.model.v2.ide.ComponentInfo
import com.android.builder.model.v2.ide.GraphItem
import com.android.builder.model.v2.ide.JavaArtifact
import com.android.builder.model.v2.ide.JavaCompileOptions
import com.android.builder.model.v2.ide.Library
import com.android.builder.model.v2.ide.LibraryInfo
import com.android.builder.model.v2.ide.LintOptions
import com.android.builder.model.v2.ide.PrivacySandboxSdkInfo
import com.android.builder.model.v2.ide.ProjectInfo
import com.android.builder.model.v2.ide.SourceProvider
import com.android.builder.model.v2.ide.SourceSetContainer
import com.android.builder.model.v2.ide.TestInfo
import com.android.builder.model.v2.ide.TestedTargetVariant
import com.android.builder.model.v2.ide.UnresolvedDependency
import com.android.builder.model.v2.ide.Variant
import com.android.builder.model.v2.ide.VectorDrawablesOptions
import com.android.builder.model.v2.ide.ViewBindingOptions
import com.android.builder.model.v2.models.AndroidDsl
import com.android.builder.model.v2.models.AndroidProject
import com.android.builder.model.v2.models.BasicAndroidProject
import com.android.builder.model.v2.models.Versions
import com.android.builder.model.v2.models.Versions.Version
import com.android.builder.model.v2.models.VariantDependencies
import com.android.builder.model.v2.models.ndk.NativeAbi
import com.android.builder.model.v2.models.ndk.NativeModule
import com.android.builder.model.v2.models.ndk.NativeVariant

// --------------
// snapshot fixtures specific to each model class

internal fun ModelSnapshotter<Versions>.snapshotVersions() {
    convertedObjectList(
        name = "versions",
        propertyAction = { versions.entries },
        nameAction = { key },
        objectAction = { value },
        sortAction = { collection -> collection?.sortedBy { it.key } }
    ) {
        item("major", Version::major)
        item("minor", Version::minor)
    }

    item("agp", Versions::agp) { version ->
        version?.let { normalizeAgpVersion(it) }
    }
}

private fun normalizeAgpVersion(version: String): Any {
    if (version == ANDROID_GRADLE_PLUGIN_VERSION) {
        return PredefinedModelValues.DEFAULT_AGP_REVISION
    }
    return version
}

internal fun ModelSnapshotter<BasicAndroidProject>.snapshotBasicAndroidProject() {
    item("projectType", BasicAndroidProject::projectType)
    item("path", BasicAndroidProject::path)
    item("buildName", BasicAndroidProject::buildName)
    item("buildFolder", BasicAndroidProject::buildFolder)
    valueList("bootClasspath", BasicAndroidProject::bootClasspath)
    dataObject("defaultConfig", BasicAndroidProject::mainSourceSet) {
        snapshotSourceSetContainer()
    }
    objectList(
        name = "buildTypes",
        propertyAction = BasicAndroidProject::buildTypeSourceSets,
        nameAction = { sourceProvider.name },
        sortAction = { collection -> collection?.sortedBy { it.sourceProvider.name } }
    ) {
        snapshotSourceSetContainer()
    }
    objectList(
        name = "productFlavors",
        propertyAction = BasicAndroidProject::productFlavorSourceSets,
        nameAction = { sourceProvider.name },
        sortAction = { collection -> collection?.sortedBy { it.sourceProvider.name } }
    ) {
        snapshotSourceSetContainer()
    }
    objectList(
        name = "variants",
        propertyAction = BasicAndroidProject::variants,
        nameAction = { name },
        sortAction = { collection -> collection?.sortedBy { it.name } }
    ) {
        snapshotBasicVariant()
    }
}

internal fun ModelSnapshotter<AndroidProject>.snapshotAndroidProject() {
    item("namespace", AndroidProject::namespace)
    item("androidTestNamespace", AndroidProject::androidTestNamespace)
    item("testFixturesNamespace", AndroidProject::testFixturesNamespace)
    item("resourcePrefix", AndroidProject::resourcePrefix)
    list("dynamicFeatures", AndroidProject::dynamicFeatures)
    objectList(
        name = "variants",
        propertyAction = AndroidProject::variants,
        nameAction = { name },
        sortAction = { collection -> collection?.sortedBy { it.name } }
    ) {
        snapshotVariant()
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
    valueList(
        name = "flags",
        propertyAction = { AndroidGradlePluginProjectFlags.BooleanFlag.values().associateWith { it.getValue(flags) }.entries },
        formatAction = { "${key.name} -> $value" }
    ) { collection ->
        collection?.sortedBy { it.key.name }
    }
    valueList("lintChecksJars", AndroidProject::lintChecksJars) { it?.sorted() }
    objectList(
        name = "modelSyncFiles",
        propertyAction = AndroidProject::modelSyncFiles,
        nameAction = { modelSyncType.name },
        sortAction = { collection -> collection?.sortedBy { it.modelSyncType.name } }
    ) {
        snapshotNativeSyncFile()
    }
}
internal fun ModelSnapshotter<AndroidDsl>.snapshotAndroidDsl() {
    item("groupId", AndroidDsl::groupId)
    item("compileTarget", AndroidDsl::compileTarget, ::normaliseCompileTarget)
    item("buildToolsVersion", AndroidDsl::buildToolsVersion) { version ->
        version?.let { normalizeBuildToolsVersion(it) }
    }
    dataObject("defaultConfig", AndroidDsl::defaultConfig) {
        snapshotProductFlavor()
    }
    objectList(
        name = "buildTypes",
        propertyAction = AndroidDsl::buildTypes,
        nameAction = { name },
        sortAction = { collection -> collection?.sortedBy { it.name } }
    ) {
        snapshotBuildType()
    }
    list("flavorDimensions", AndroidDsl::flavorDimensions)
    objectList(
        name = "productFlavors",
        propertyAction = AndroidDsl::productFlavors,
        nameAction = { name },
        sortAction = { collection -> collection?.sortedBy { it.name } }
    ) {
        snapshotProductFlavor()
    }
    objectList(
        name = "signingConfigs",
        propertyAction = AndroidDsl::signingConfigs,
        nameAction = { name },
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
    dataObject("aaptOptions", AndroidDsl::aaptOptions) {
        item("namespacing", AaptOptions::namespacing)
    }
    dataObject("lintOptions", AndroidDsl::lintOptions) {
        snapshotLintOptions()
    }
    dataObject("dependenciesInfo", AndroidDsl::dependenciesInfo) {
        item("includeInApk", DependenciesInfo::includeInApk)
        item("includeInBundle", DependenciesInfo::includeInBundle)
    }
}

internal fun ModelSnapshotter<NativeModule>.snapshotNativeModule() {
    item("name", NativeModule::name)
    objectList(
        "variants",
        NativeModule::variants,
        nameAction =  { name },
        sortAction =  { collection -> collection?.sortedBy { it.name } }
    ) {
        snapshotNativeVariant()
    }
    item("nativeBuildSystem", NativeModule::nativeBuildSystem)
    item("ndkVersion", NativeModule::ndkVersion) {
        if (it == NDK_DEFAULT_VERSION) "{DEFAULT_NDK_VERSION}" else it
    }
    item("defaultNdkVersion", NativeModule::defaultNdkVersion) {
        if (it == NDK_DEFAULT_VERSION) "{DEFAULT_NDK_VERSION}" else it
    }
    item("externalNativeBuildFile", NativeModule::externalNativeBuildFile)
}

internal fun ModelSnapshotter<ModelSyncFile>.snapshotNativeSyncFile() {
    item("syncFile", ModelSyncFile::syncFile)
}

private fun ModelSnapshotter<NativeVariant>.snapshotNativeVariant() {
    objectList(
        name = "abis",
        propertyAction = NativeVariant::abis,
        nameAction = { name },
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

private fun ModelSnapshotter<SourceSetContainer>.snapshotSourceSetContainer() {
    dataObject("sourceProvider", SourceSetContainer::sourceProvider) {
        snapshotSourceProvider()
    }
    dataObject("androidTestSourceProvider", SourceSetContainer::androidTestSourceProvider) {
        snapshotSourceProvider()
    }
    dataObject("unitTestSourceProvider", SourceSetContainer::unitTestSourceProvider) {
        snapshotSourceProvider()
    }
    dataObject("testFixturesSourceProvider", SourceSetContainer::testFixturesSourceProvider) {
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
    list("resourceConfigurations", ProductFlavor::resourceConfigurations)
    item("signingConfig", ProductFlavor::signingConfig)
    item("wearAppUnbundled", ProductFlavor::wearAppUnbundled)
    dataObject("vectorDrawables", ProductFlavor::vectorDrawables) {
        list("generatedDensities", VectorDrawablesOptions::generatedDensities)
        item("useSupportLibrary", VectorDrawablesOptions::useSupportLibrary)
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
        nameAction = { key },
        objectAction = { value },
        sortAction = { collection -> collection?.sortedBy { it.key } }
    ) {
        item("name", ClassField::name)
        item("type", ClassField::type)
        item("value", ClassField::value)
        item("documentation", ClassField::documentation)
        list("annotations", ClassField::annotations)
    }
    convertedObjectList(
        name = "resValues",
        propertyAction = { resValues?.entries },
        nameAction = { "${value.type}/${value.name}" },
        objectAction = { value },
        sortAction = { collection -> collection?.sortedBy { it.key } }
    ) {
        item("name", ClassField::name)
        item("type", ClassField::type)
        item("value", ClassField::value)
        item("documentation", ClassField::documentation)
        list("annotations", ClassField::annotations)
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
    convertedObjectList(name = "custom",
        propertyAction = SourceProvider::customDirectories,
        nameAction = { sourceTypeName },
        objectAction = { this },
        sortAction = { collection -> collection?.sortedBy { it.sourceTypeName } }
    ) {
        item("sourceTypeName", CustomSourceDirectory::sourceTypeName)
        item("directory", CustomSourceDirectory::directory)
    }
}

private fun ModelSnapshotter<BasicVariant>.snapshotBasicVariant() {
    item("name", BasicVariant::name)
    item("displayName", BasicVariant::name)
    item("buildType", BasicVariant::buildType)
    list("productFlavors", BasicVariant::productFlavors)
    dataObject("mainArtifact", BasicVariant::mainArtifact) {
        snapshotBasicArtifact()
    }
    dataObject("androidTestArtifact", BasicVariant::androidTestArtifact) {
        snapshotBasicArtifact()
    }
    dataObject("unitTestArtifact", BasicVariant::unitTestArtifact) {
        snapshotBasicArtifact()
    }
    dataObject("testFixturesArtifact", BasicVariant::testFixturesArtifact) {
        snapshotBasicArtifact()
    }
}

private fun ModelSnapshotter<Variant>.snapshotVariant() {
    item("name", Variant::name)
    item("displayName", Variant::name)
    item("isInstantAppCompatible", Variant::isInstantAppCompatible)
    dataObject("mainArtifact", Variant::mainArtifact) {
        snapshotAndroidArtifact()
    }
    dataObject("androidTestArtifact", Variant::androidTestArtifact) {
        snapshotAndroidArtifact()
    }
    dataObject("unitTestArtifact", Variant::unitTestArtifact) {
        snapshotJavaArtifact()
    }
    dataObject("testFixturesArtifact", Variant::testFixturesArtifact) {
        snapshotAndroidArtifact()
    }
    dataObject("testedTargetVariant", Variant::testedTargetVariant) {
        item("targetProjectPath", TestedTargetVariant::targetProjectPath)
        item("targetVariant", TestedTargetVariant::targetVariant)
    }
    list("desugaredMethods", Variant::desugaredMethods)
}

private fun ModelSnapshotter<BasicArtifact>.snapshotBasicArtifact() {
    dataObject("variantSourceProvider", BasicArtifact::variantSourceProvider) {
        snapshotSourceProvider()
    }
    dataObject("multiFlavorSourceProvider", BasicArtifact::multiFlavorSourceProvider) {
        snapshotSourceProvider()
    }
}

private fun ModelSnapshotter<AndroidArtifact>.snapshotAndroidArtifact() {
    snapshotBaseArtifact()
    dataObject("minSdkVersion", AndroidArtifact::minSdkVersion) {
        item("apiLevel", ApiVersion::apiLevel)
        item("codename", ApiVersion::codename)
    }
    dataObject("targetSdkVersionOverride", AndroidArtifact::targetSdkVersionOverride) {
        item("apiLevel", ApiVersion::apiLevel)
        item("codename", ApiVersion::codename)
    }
    item("maxSdkVersion", AndroidArtifact::maxSdkVersion)
    item("isSigned", AndroidArtifact::isSigned)
    item("signingConfigName", AndroidArtifact::signingConfigName)
    item("applicationId", AndroidArtifact::applicationId)
    item("sourceGenTaskName", AndroidArtifact::sourceGenTaskName)
    item("resGenTaskName", AndroidArtifact::resGenTaskName)
    valueList("generatedResourceFolders", AndroidArtifact::generatedResourceFolders) { it?.sorted() }
    list("abiFilters", AndroidArtifact::abiFilters)
    item("assembleTaskOutputListingFile", AndroidArtifact::assembleTaskOutputListingFile)
    dataObject("testInfo", AndroidArtifact::testInfo) {
        item("animationsDisabled", TestInfo::animationsDisabled)
        item("execution", TestInfo::execution)
        valueList("additionalRuntimeApks", TestInfo::additionalRuntimeApks) { it?.sorted() }
        item("instrumentedTestTaskName", TestInfo::instrumentedTestTaskName)
    }
    dataObject("bundleInfo", AndroidArtifact::bundleInfo) {
        item("bundleTaskName", BundleInfo::bundleTaskName)
        item("bundleTaskOutputListingFile", BundleInfo::bundleTaskOutputListingFile)
        item("apkFromBundleTaskName", BundleInfo::apkFromBundleTaskName)
        item("apkFromBundleTaskOutputListingFile", BundleInfo::apkFromBundleTaskOutputListingFile)
    }
    item("codeShrinker", AndroidArtifact::codeShrinker)
    dataObject("privacySandboxSdkInfo", AndroidArtifact::privacySandboxSdkInfo) {
        item("task", PrivacySandboxSdkInfo::task)
        item("outputListingFile", PrivacySandboxSdkInfo::outputListingFile)
    }
}

private fun ModelSnapshotter<JavaArtifact>.snapshotJavaArtifact() {
    snapshotBaseArtifact()
    item("mockablePlatformJar", JavaArtifact::mockablePlatformJar)
    item("runtimeResourceFolder", JavaArtifact::runtimeResourceFolder)
}

private fun ModelSnapshotter<out AbstractArtifact>.snapshotBaseArtifact() {
    item("compileTaskName", AbstractArtifact::compileTaskName)
    item("assembleTaskName", AbstractArtifact::assembleTaskName)
    valueList("classesFolders", AbstractArtifact::classesFolders) { it?.sorted() }
    list("ideSetupTaskNames", AbstractArtifact::ideSetupTaskNames)
    valueList("generatedSourceFolders", AbstractArtifact::generatedSourceFolders) { it?.sorted() }
}

private fun ModelSnapshotter<LintOptions>.snapshotLintOptions() {
    list("disable", LintOptions::disable)
    list("enable", LintOptions::enable)
    list("informational", LintOptions::informational)
    list("warning", LintOptions::warning)
    list("error", LintOptions::error)
    list("fatal", LintOptions::fatal)
    list("checkOnly", LintOptions::checkOnly)
    item("abortOnError", LintOptions::abortOnError)
    item("absolutePaths", LintOptions::absolutePaths)
    item("noLines", LintOptions::noLines)
    item("quiet", LintOptions::quiet)
    item("checkAllWarnings", LintOptions::checkAllWarnings)
    item("ignoreWarnings", LintOptions::ignoreWarnings)
    item("warningsAsErrors", LintOptions::warningsAsErrors)
    item("checkTestSources", LintOptions::checkTestSources)
    item("ignoreTestSources", LintOptions::ignoreTestSources)
    item("ignoreTestFixturesSources", LintOptions::ignoreTestFixturesSources)
    item("checkGeneratedSources", LintOptions::checkGeneratedSources)
    item("explainIssues", LintOptions::explainIssues)
    item("showAll", LintOptions::showAll)
    item("lintConfig", LintOptions::lintConfig)
    item("textReport", LintOptions::textReport)
    item("textOutput", LintOptions::textOutput)
    item("htmlReport", LintOptions::htmlReport)
    item("htmlOutput", LintOptions::htmlOutput)
    item("xmlReport", LintOptions::xmlReport)
    item("xmlOutput", LintOptions::xmlOutput)
    item("sarifReport", LintOptions::sarifReport)
    item("sarifOutput", LintOptions::sarifOutput)
    item("checkReleaseBuilds", LintOptions::checkReleaseBuilds)
    item("checkDependencies", LintOptions::checkDependencies)
    item("baseline", LintOptions::baseline)
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
    dataObject("testFixturesArtifact", VariantDependencies::testFixturesArtifact) {
        snapshotArtifactDependencies()
    }

    objectList(
        name = "libraries",
        propertyAction = { libraries.values },
        nameAction = { it.normalizeArtifactAddress(key) },
        sortAction = { collection -> collection?.sortedBy {it.key} }
    ) {
        item("type", Library::type)
        item("artifact", Library::artifact)
        dataObject("projectInfo", Library::projectInfo) {
            item("buildId", ProjectInfo::buildId)
            item("projectPath", ProjectInfo::projectPath)
            item("isTestFixtures", ComponentInfo::isTestFixtures)
            item("buildType", ComponentInfo::buildType)
            valueList(
                name = "productFlavors",
                propertyAction = { productFlavors.entries },
                formatAction = { "$key -> $value" }
            ) { collection ->
                collection?.sortedBy { it.key }
            }
            valueList(
                name = "attributes",
                propertyAction = { attributes.entries },
                formatAction = { "$key -> $value" }
            ) { collection ->
                collection?.sortedBy { it.key }
            }
            valueList(
                name = "capabilities",
                propertyAction = ComponentInfo::capabilities
            ) { collection ->
                collection?.sortedBy { it }
            }
        }
        dataObject("libraryInfo", Library::libraryInfo) {
            item("group", LibraryInfo::group)
            pathAsAString(name = "name", onlyIfPresent = true, propertyAction = LibraryInfo::name)
            item("version", LibraryInfo::version)
            item("isTestFixtures", ComponentInfo::isTestFixtures)
            item("buildType", ComponentInfo::buildType)
            valueList(
                name = "productFlavors",
                propertyAction = { productFlavors.entries },
                formatAction = { "$key -> $value" }
            ) { collection ->
                collection?.sortedBy { it.key }
            }
            valueList(
                name = "attributes",
                propertyAction = { attributes.entries },
                formatAction = { "$key -> $value" }
            ) { collection ->
                collection?.sortedBy { it.key }
            }
            valueList(
                name = "capabilities",
                propertyAction = ComponentInfo::capabilities
            ) { collection ->
                collection?.sortedBy { it }
            }
        }
        item("lintJar", Library::lintJar)
        dataObject("androidLibraryData", Library::androidLibraryData) {
            list("compileJarFiles", AndroidLibraryData::compileJarFiles)
            list("runtimeJarFiles", AndroidLibraryData::runtimeJarFiles)
            item("manifest", AndroidLibraryData::manifest)
            item("resFolder", AndroidLibraryData::resFolder)
            item("resStaticLibrary", AndroidLibraryData::resStaticLibrary)
            item("assetsFolder", AndroidLibraryData::assetsFolder)
            item("jniFolder", AndroidLibraryData::jniFolder)
            item("aidlFolder", AndroidLibraryData::aidlFolder)
            item("renderscriptFolder", AndroidLibraryData::renderscriptFolder)
            item("proguardRules", AndroidLibraryData::proguardRules)
            item("externalAnnotations", AndroidLibraryData::externalAnnotations)
            item("publicResources", AndroidLibraryData::publicResources)
            item("symbolFile", AndroidLibraryData::symbolFile)
        }
    }
}

private fun ModelSnapshotter<ArtifactDependencies>.snapshotArtifactDependencies() {
    val visited: MutableSet<String> = mutableSetOf()
    objectList(
        name = "compileDependencies",
        propertyAction = ArtifactDependencies::compileDependencies,
        nameAction =  { it.normalizeArtifactAddress(key) },
    ) {
        snapshotGraphItem(visited)
    }

    objectList(
        name = "runtimeDependencies",
        propertyAction = ArtifactDependencies::runtimeDependencies,
        nameAction =  { it.normalizeArtifactAddress(key) },
    ) {
        snapshotGraphItem(visited)
    }

    objectList(
        name = "unresolvedDependencies",
        propertyAction = ArtifactDependencies::unresolvedDependencies,
        nameAction =  { name },
    ) {
        item("cause", UnresolvedDependency::cause)
    }
}

private fun ModelSnapshotter<GraphItem>.snapshotGraphItem(visited: MutableSet<String>) {
    val localKey = normalizeArtifactAddress(GraphItem::key)

    if (visited.contains(localKey)) {
        item("dependencies", { "*visited*" })
    } else {
        visited.add(localKey)
        item("requestedCoordinates", GraphItem::requestedCoordinates)
        objectList(
            name = "dependencies",
            propertyAction = GraphItem::dependencies,
            nameAction =  { it.normalizeArtifactAddress(key) }
        ) {
            snapshotGraphItem(visited)
        }
    }
}

private fun normalizeBuildToolsVersion(version: String): Any {
    if (version == ToolsRevisionUtils.DEFAULT_BUILD_TOOLS_REVISION.toString()) {
        return PredefinedModelValues.DEFAULT_BUILD_TOOLS_REVISION
    }
    return version
}

private fun normaliseCompileTarget(target: String?): Any? {
    if (target == "android-$DEFAULT_COMPILE_SDK_VERSION") {
        return PredefinedModelValues.DEFAULT_COMPILE_SDK_VERSION
    }
    return target
}

internal enum class PredefinedModelValues {
    DEFAULT_BUILD_TOOLS_REVISION,
    DEFAULT_AGP_REVISION,
    DEFAULT_COMPILE_SDK_VERSION,
}
