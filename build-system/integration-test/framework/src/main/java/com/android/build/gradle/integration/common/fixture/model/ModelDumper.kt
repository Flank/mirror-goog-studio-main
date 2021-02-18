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
import com.android.builder.model.v2.dsl.BaseConfig
import com.android.builder.model.v2.dsl.BuildType
import com.android.builder.model.v2.dsl.ProductFlavor
import com.android.builder.model.v2.ide.AndroidArtifact
import com.android.builder.model.v2.ide.ArtifactDependencies
import com.android.builder.model.v2.ide.BaseArtifact
import com.android.builder.model.v2.ide.BuildTypeContainer
import com.android.builder.model.v2.ide.JavaArtifact
import com.android.builder.model.v2.ide.LintOptions
import com.android.builder.model.v2.ide.ProductFlavorContainer
import com.android.builder.model.v2.ide.SourceProvider
import com.android.builder.model.v2.ide.Variant
import com.android.builder.model.v2.models.AndroidProject
import com.android.builder.model.v2.models.GlobalLibraryMap
import com.android.builder.model.v2.models.VariantDependencies
import com.android.builder.model.v2.models.ndk.NativeAbi
import com.android.builder.model.v2.models.ndk.NativeModule
import com.android.builder.model.v2.models.ndk.NativeVariant

// --------------
// dump fixtures specific to each model class

internal fun AndroidProject.writeToBuilder(builder: DumpBuilder) {
    builder.apply {
        item("projectType", projectType.name)
        item("path", path)
        item("groupId", groupId)
        item("defaultVariant", defaultVariant)
        list("flavorDimensions", flavorDimensions.sorted())
        item("compileTarget", compileTarget)
        item("buildFolder", buildFolder)
        item("resourcePrefix", resourcePrefix)
        item("buildToolsVersion", buildToolsVersion)
        list("dynamicFeatures", dynamicFeatures)
        multiLineList("bootClasspath", bootClasspath.sorted()) {
            value(it)
        }
        largeObject("defaultConfig", defaultConfig) {
            it.writeToBuilder(this)
        }
        multiLineList("buildTypes", buildTypes.sortedBy { it.buildType.name }) {
            largeObject("buildTypeContainer(${it.buildType.name})", it) { container ->
                container.writeToBuilder(this)
            }
        }
        multiLineList("productFlavors", productFlavors.sortedBy { it.productFlavor.name }) {
            largeObject("productFlavorContainer(${it.productFlavor.name})", it) { container ->
                container.writeToBuilder(this)
            }
        }
        multiLineList("signingConfigs", signingConfigs.sortedBy { it.name }) {
            largeObject("signingConfig(${it.name})", it) { config ->
                item("name", config.name)
                item("storeFile", config.storeFile)
                item("storePassword", config.storePassword)
                item("keyAlias", config.keyAlias)
                item("keyPassword", config.keyPassword)
                item("enableV1Signing", config.enableV1Signing)
                item("enableV2Signing", config.enableV2Signing)
                item("enableV3Signing", config.enableV3Signing)
                item("enableV4Signing", config.enableV4Signing)
                item("isSigningReady", config.isSigningReady)
            }
        }
        multiLineList("variants", variants.sortedBy { it.name }) {
            largeObject("variant(${it.name})", it) { variant ->
                variant.writeToBuilder(this)
            }
        }
        multiLineList("lintRuleJars", lintRuleJars.sorted()) {
            value(it)
        }
        struct("aaptOptions", aaptOptions) {
            item("namespacing", it.namespacing)
        }
        largeObject("lintOptions", lintOptions) {
            it.writeToBuilder(this)
        }
        struct("javaCompileOptions", javaCompileOptions) {
            item("encoding", it.encoding)
            item("sourceCompatibility", it.sourceCompatibility)
            item("targetCompatibility", it.targetCompatibility)
            item("isCoreLibraryDesugaringEnabled", it.isCoreLibraryDesugaringEnabled)
        }
        struct("viewBindingOptions", viewBindingOptions) {
            item("isEnabled", it.isEnabled)
        }
        struct("dependenciesInfo", dependenciesInfo) {
            item("includeInApk", it.includeInApk)
            item("includeInBundle", it.includeInBundle)
        }
        multiLineList("flags", flags.booleanFlagMap?.entries?.sortedBy { it.key.name }) {
            entry(it.key.name, it.value)
        }
    }
}

fun NativeModule.writeToBuilder(builder: DumpBuilder) {
    builder.apply {
        item("name", name)
        multiLineList("variants", variants.sortedBy { name }) { variant ->
            variant.writeToBuilder(this)
        }
        item("nativeBuildSystem", nativeBuildSystem)
        item(
            "ndkVersion",
            if (ndkVersion == ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION) "{DEFAULT_NDK_VERSION}" else ndkVersion
        )
        item(
            "defaultNdkVersion",
            if (defaultNdkVersion == ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION) "{DEFAULT_NDK_VERSION}" else defaultNdkVersion
        )
        item("externalNativeBuildFile", externalNativeBuildFile)
    }
}

private fun NativeVariant.writeToBuilder(builder: DumpBuilder) {
    builder.struct("NativeVariant", this) {
        item("name", name)
        multiLineList("abis", abis.sortedBy { name }) { abi ->
            abi.writeToBuilder(this)
        }
    }
}

private fun NativeAbi.writeToBuilder(builder: DumpBuilder) {
    builder.struct("NativeAbi", this) {
        item("name", name)
        item("sourceFlagsFile", sourceFlagsFile)
        item("symbolFolderIndexFile", symbolFolderIndexFile)
        item("buildFileIndexFile", buildFileIndexFile)
        item("additionalProjectFilesIndexFile", additionalProjectFilesIndexFile)
    }
}

private fun ProductFlavorContainer.writeToBuilder(builder: DumpBuilder) {
    builder.apply {
        largeObject("productFlavor", productFlavor) {
            it.writeToBuilder(this)
        }
        largeObject("sourceProvider", sourceProvider) {
            it.writeToBuilder(this)
        }
        largeObject("androidTestSourceProvider", androidTestSourceProvider) {
            it.writeToBuilder(this)
        }
        largeObject("unitTestSourceProvider", unitTestSourceProvider) {
            it.writeToBuilder(this)
        }
    }
}

private fun ProductFlavor.writeToBuilder(builder: DumpBuilder) {
    dumpBaseConfig(builder)
    builder.apply {
        item("dimension", dimension)
        item("applicationId", applicationId)
        item("versionCode", versionCode)
        item("versionName", versionName)
        struct("minSdkVersion", minSdkVersion) {
            item("apiLevel", it.apiLevel)
            item("codename", it.codename)
        }
        struct("targetSdkVersion", targetSdkVersion) {
            item("apiLevel", it.apiLevel)
            item("codename", it.codename)
        }
        item("maxSdkVersion", maxSdkVersion)
        item("renderscriptTargetApi", renderscriptTargetApi)
        item("renderscriptSupportModeEnabled", renderscriptSupportModeEnabled)
        item("renderscriptSupportModeBlasEnabled", renderscriptSupportModeBlasEnabled)
        item("renderscriptNdkModeEnabled", renderscriptNdkModeEnabled)
        item("testApplicationId", testApplicationId)
        item("testInstrumentationRunner", testInstrumentationRunner)
        multiLineList(
            "testInstrumentationRunnerArguments",
            testInstrumentationRunnerArguments.entries.sortedBy { it.key }) {
            entry(it.key, it.value)
        }
        item("testHandleProfiling", testHandleProfiling)
        item("testFunctionalTest", testFunctionalTest)
        list("resourceConfigurations", resourceConfigurations.sorted())
        item("signingConfig", signingConfig)
        item("wearAppUnbundled", wearAppUnbundled)
        struct("vectorDrawables", vectorDrawables) {
            list("generatedDensities", it.generatedDensities?.sorted())
            item("useSupportLibrary", it.useSupportLibrary)
        }
    }
}

private fun BuildTypeContainer.writeToBuilder(builder: DumpBuilder) {
    builder.apply {
        largeObject("buildType", buildType) {
            it.writeToBuilder(this)
        }
        largeObject("sourceProvider", sourceProvider) {
            it.writeToBuilder(this)
        }
        largeObject("androidTestSourceProvider", androidTestSourceProvider) {
            it.writeToBuilder(this)
        }
        largeObject("unitTestSourceProvider", unitTestSourceProvider) {
            it.writeToBuilder(this)
        }
    }
}

private fun BuildType.writeToBuilder(builder: DumpBuilder) {
    dumpBaseConfig(builder)
    builder.apply {
        item("isDebuggable", isDebuggable)
        item("isTestCoverageEnabled", isTestCoverageEnabled)
        item("isPseudoLocalesEnabled", isPseudoLocalesEnabled)
        item("isJniDebuggable", isJniDebuggable)
        item("isRenderscriptDebuggable", isRenderscriptDebuggable)
        item("renderscriptOptimLevel", renderscriptOptimLevel)
        item("isMinifyEnabled", isMinifyEnabled)
        item("isZipAlignEnabled", isZipAlignEnabled)
        item("isEmbedMicroApp", isEmbedMicroApp)
        item("signingConfig", signingConfig)
    }
}

private fun BaseConfig.dumpBaseConfig(builder: DumpBuilder) {
    builder.apply {
        // BaseConfig
        item("name", name)
        item("applicationIdSuffix", applicationIdSuffix)
        item("versionNameSuffix", versionNameSuffix)
        multiLineList("buildConfigFields", buildConfigFields?.entries?.sortedBy { it.key }) {
            struct("field", it.value) { field ->
                item("name", field.name)
                item("type", field.type)
                item("value", field.value)
                item("documentation", field.documentation)
                list("annotations", field.annotations.sorted())
            }
        }
        multiLineList("resValues", resValues?.entries?.sortedBy { it.key }) {
            struct("value", it.value) { field ->
                item("name", field.name)
                item("type", field.type)
                item("value", field.value)
                item("documentation", field.documentation)
                list("annotations", field.annotations.sorted())
            }
        }
        multiLineList("proguardFiles", proguardFiles.sorted()) {
            value(it)
        }
        multiLineList("consumerProguardFiles", consumerProguardFiles.sorted()) {
            value(it)
        }
        multiLineList("testProguardFiles", testProguardFiles.sorted()) {
            value(it)
        }
        multiLineList("manifestPlaceholders", manifestPlaceholders.entries.sortedBy { it.key }) {
            entry(it.key, it.value)
        }
        item("multiDexEnabled", multiDexEnabled)
        item("multiDexKeepFile", multiDexKeepFile)
        item("multiDexKeepProguard", multiDexKeepProguard)
    }
}

private fun SourceProvider.writeToBuilder(builder: DumpBuilder) {
    builder.apply {
        item("name", name)
        item("manifestFile", manifestFile)
        multiLineList("javaDirectories", javaDirectories.sorted()) {
            value(it)
        }
        multiLineList("kotlinDirectories", kotlinDirectories.sorted()) {
            value(it)
        }
        multiLineList("resourcesDirectories", resourcesDirectories.sorted()) {
            value(it)
        }
        multiLineList("aidlDirectories", aidlDirectories?.sorted()) {
            value(it)
        }
        multiLineList("renderscriptDirectories", renderscriptDirectories?.sorted()) {
            value(it)
        }
        multiLineList("resDirectories", resDirectories?.sorted()) {
            value(it)
        }
        multiLineList("assetsDirectories", assetsDirectories?.sorted()) {
            value(it)
        }
        multiLineList("jniLibsDirectories", jniLibsDirectories.sorted()) {
            value(it)
        }
        multiLineList("shadersDirectories", shadersDirectories?.sorted()) {
            value(it)
        }
        multiLineList("mlModelsDirectories", mlModelsDirectories?.sorted()) {
            value(it)
        }
    }
}

internal fun Variant.writeToBuilder(builder: DumpBuilder) {
    builder.apply {
        item("name", name)
        item("displayName", name)
        item("buildType", buildType)
        item("productFlavors", productFlavors)
        item("isInstantAppCompatible", isInstantAppCompatible)
        item("desugaredMethods", desugaredMethods)
        largeObject("mainArtifact", mainArtifact) {
            it.writeToBuilder(this)
        }
        largeObject("androidTestArtifact", androidTestArtifact) {
            it.writeToBuilder(this)
        }
        largeObject("unitTestArtifact", unitTestArtifact) {
            it.writeToBuilder(this)
        }
        struct("testedTargetVariant", testedTargetVariant) {
            item("targetProjectPath", it.targetProjectPath)
            item("targetVariant", it.targetVariant)
        }
    }
}

private fun AndroidArtifact.writeToBuilder(builder: DumpBuilder) {
    writeBaseArtifactToBuilder(builder)
    builder.apply {
        item("isSigned", isSigned)
        item("signingConfigName", signingConfigName)
        item("sourceGenTaskName", sourceGenTaskName)
        item("generatedResourceFolders", generatedResourceFolders.sorted())
        item("abiFilters", abiFilters?.sorted())
        item("assembleTaskOutputListingFile", assembleTaskOutputListingFile)
        struct("testInfo", testInfo) {
            item("animationsDisabled", it.animationsDisabled)
            item("execution", it.execution)
            item("additionalRuntimeApks", it.additionalRuntimeApks)
            item("instrumentedTestTaskName", it.instrumentedTestTaskName)
        }
        struct("bundleInfo", bundleInfo) {
            item("bundleTaskName", it.bundleTaskName)
            item("bundleTaskOutputListingFile", it.bundleTaskOutputListingFile)
            item("apkFromBundleTaskName", it.apkFromBundleTaskName)
            item("apkFromBundleTaskOutputListingFile", it.apkFromBundleTaskOutputListingFile)
        }
        item("codeShrinker", codeShrinker)
    }
}

private fun JavaArtifact.writeToBuilder(builder: DumpBuilder) {
    writeBaseArtifactToBuilder(builder)
    builder.apply {
        item("mockablePlatformJar", mockablePlatformJar)
        item("runtimeResourceFolder", runtimeResourceFolder)
    }
}

private fun BaseArtifact.writeBaseArtifactToBuilder(builder: DumpBuilder) {
    builder.apply {
        item("compileTaskName", compileTaskName)
        item("assembleTaskName", assembleTaskName)
        multiLineList("classesFolders", classesFolders.sorted()) {
            value(it)
        }
        item("ideSetupTaskNames", ideSetupTaskNames.sorted())
        multiLineList("generatedSourceFolders", generatedSourceFolders.sorted()) {
            value(it)
        }
        largeObject("variantSourceProvider", variantSourceProvider) {
            it.writeToBuilder(this)
        }
        largeObject("multiFlavorSourceProvider", multiFlavorSourceProvider) {
            it.writeToBuilder(this)
        }
    }
}

private fun LintOptions.writeToBuilder(builder: DumpBuilder) {
    builder.apply {
        list("disable", disable.sorted())
        list("enable", enable.sorted())
        list("check", check?.sorted())
        item("isAbortOnError", isAbortOnError)
        item("isAbsolutePaths", isAbsolutePaths)
        item("isNoLines", isNoLines)
        item("isQuiet", isQuiet)
        item("isCheckAllWarnings", isCheckAllWarnings)
        item("isIgnoreWarnings", isIgnoreWarnings)
        item("isWarningsAsErrors", isWarningsAsErrors)
        item("isCheckTestSources", isCheckTestSources)
        item("isIgnoreTestSources", isIgnoreTestSources)
        item("isCheckGeneratedSources", isCheckGeneratedSources)
        item("isExplainIssues", isExplainIssues)
        item("isShowAll", isShowAll)
        item("lintConfig", lintConfig)
        item("textReport", textReport)
        item("textOutput", textOutput)
        item("htmlReport", htmlReport)
        item("htmlOutput", htmlOutput)
        item("xmlReport", xmlReport)
        item("xmlOutput", xmlOutput)
        item("sarifReport", sarifReport)
        item("sarifOutput", sarifOutput)
        item("isCheckReleaseBuilds", isCheckReleaseBuilds)
        item("isCheckDependencies", isCheckDependencies)
        item("baselineFile", baselineFile)
        multiLineList("severityOverrides", severityOverrides?.entries?.sortedBy { it.key }) {
            entry(it.key, it.value)
        }
    }
}

internal fun VariantDependencies.writeToBuilder(builder: DumpBuilder) {
    builder.apply {
        item("name", name)
        largeObject("mainArtifact", mainArtifact) {
            it.writeToBuilder(this)
        }
        largeObject("androidTestArtifact", androidTestArtifact) {
            it.writeToBuilder(this)
        }
        largeObject("unitTestArtifact", unitTestArtifact) {
            it.writeToBuilder(this)
        }
    }
}

private fun ArtifactDependencies.writeToBuilder(builder: DumpBuilder) {
    builder.apply {
        multiLineList("compileDependencies", compileDependencies) {
            struct("GraphItem", it) { item->
                artifactAddress("artifactAddress", item.artifactAddress)
                item("requestedCoordinates", item.requestedCoordinates)
                item("dependencies", item.dependencies)
            }
        }
        multiLineList("runtimeDependencies", runtimeDependencies) {
            struct("GraphItem", it) { item->
                artifactAddress("artifactAddress", item.artifactAddress)
                item("requestedCoordinates", item.requestedCoordinates)
                item("dependencies", item.dependencies)
            }
        }
    }
}

internal fun GlobalLibraryMap.writeToBuilder(builder: DumpBuilder) {
    builder.apply {
        multiLineList("libraries", libraries.values.sortedBy { it.artifactAddress }) {
            largeObject("library", it) { library ->
                item("type", library.type)
                artifactAddress("artifactAddress", library.artifactAddress)
                item("artifact", library.artifact)
                buildId("buildId", library.buildId)
                item("projectPath", library.projectPath)
                item("variant", library.variant)
                item("compileJarFiles", library.compileJarFiles)
                item("runtimeJarFiles", library.runtimeJarFiles)
                item("manifest", library.manifest)
                item("resFolder", library.resFolder)
                item("resStaticLibrary", library.resStaticLibrary)
                item("assetsFolder", library.assetsFolder)
                item("jniFolder", library.jniFolder)
                item("aidlFolder", library.aidlFolder)
                item("renderscriptFolder", library.renderscriptFolder)
                item("proguardRules", library.proguardRules)
                item("lintJar", library.lintJar)
                item("externalAnnotations", library.externalAnnotations)
                item("publicResources", library.publicResources)
                item("symbolFile", library.symbolFile)
            }
        }
    }
}
