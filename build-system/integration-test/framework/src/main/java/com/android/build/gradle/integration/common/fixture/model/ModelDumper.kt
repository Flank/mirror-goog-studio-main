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

import com.android.builder.model.v2.dsl.BaseConfig
import com.android.builder.model.v2.dsl.BuildType
import com.android.builder.model.v2.dsl.ProductFlavor
import com.android.builder.model.v2.ide.BuildTypeContainer
import com.android.builder.model.v2.ide.LintOptions
import com.android.builder.model.v2.ide.ProductFlavorContainer
import com.android.builder.model.v2.ide.SourceProvider
import com.android.builder.model.v2.models.AndroidProject
import com.android.builder.model.v2.models.VariantDependencies
import java.io.File

interface FileNormalizer {
    fun normalize(file: File): String
}

/**
 * Entry point to dump an [AndroidProject]
 */
fun AndroidProject.dump(normalizer: FileNormalizer): String {
    return dump("AndroidProject", normalizer) {
        writeToBuilder(this)
    }.also {
        println("--------------------------------------------------")
        println("Dumped model:")
        println(it)
    }
}

/**
 * Entry point to dump an [VariantDependencies]
 */
fun VariantDependencies.dump(normalizer: FileNormalizer): String {
    return dump("VariantDependencies", normalizer) {
        // TODO
    }.also {
        println("--------------------------------------------------")
        println("Dumped model:")
        println(it)
    }
}

// --------------
// Internal dump fixtures specific to each model class

private fun AndroidProject.writeToBuilder(builder: DumpBuilder) {
    builder.apply {
        item("apiVersion", apiVersion)
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
        largeObject("DefaultConfigContainer", defaultConfig) {
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
                item("storeType", config.storeType)
                item("enableV1Signing", config.enableV1Signing)
                item("enableV2Signing", config.enableV2Signing)
                item("enableV3Signing", config.enableV3Signing)
                item("enableV4Signing", config.enableV4Signing)
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

private fun ProductFlavorContainer.writeToBuilder(builder: DumpBuilder) {
    builder.apply {
        largeObject("Flavor", productFlavor) {
            it.writeToBuilder(this)
        }
        largeObject("Prod-SourceProvider", sourceProvider) {
            it.writeToBuilder(this)
        }
        largeObject("AndroidTest-SourceProvider", androidTestSourceProvider) {
            it.writeToBuilder(this)
        }
        largeObject("UnitTest-SourceProvider", unitTestSourceProvider) {
            it.writeToBuilder(this)
        }
    }
}

private fun ProductFlavor.writeToBuilder(builder: DumpBuilder) {
    dumpBaseConfig(builder)
    builder.apply {
        item("dimension", dimension)
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
        largeObject("BuildType", buildType) {
            it.writeToBuilder(this)
        }
        largeObject("Prod-SourceProvider", sourceProvider) {
            it.writeToBuilder(this)
        }
        largeObject("AndroidTest-SourceProvider", androidTestSourceProvider) {
            it.writeToBuilder(this)
        }
        largeObject("UnitTest-SourceProvider", unitTestSourceProvider) {
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
        multiLineList("buildConfigFields", buildConfigFields.entries.sortedBy { it.key }) {
            struct("field", it.value) { field ->
                item("name", field.name)
                item("type", field.type)
                item("value", field.value)
                item("documentation", field.documentation)
                list("annotations", field.annotations.sorted())
            }
        }
        multiLineList("resValues", resValues.entries.sortedBy { it.key }) {
            struct("value", it.value) { field ->
                item("name", field.name)
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
        item("ManifestFile", manifestFile)
        multiLineList("javaDirectories", javaDirectories.sorted()) {
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
        multiLineList("cDirectories", cDirectories.sorted()) {
            value(it)
        }
        multiLineList("cppDirectories", cppDirectories.sorted()) {
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
        item("isCheckReleaseBuilds", isCheckReleaseBuilds)
        item("isCheckDependencies", isCheckDependencies)
        item("baselineFile", baselineFile)
        multiLineList("severityOverrides", severityOverrides?.entries?.sortedBy { it.key }) {
            entry(it.key, it.value)
        }
    }
}