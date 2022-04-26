/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.configure

import com.android.build.gradle.internal.component.VariantCreationConfig
import java.io.File

/**
 * Enable Ninja build system through experimental flags in Android Gradle Plugin.
 * Mimics the final DSL.
 *
 * Example usage in build.gradle:
 *         externalNativeBuild {
 *           experimentalProperties["ninja.abiFilters"] = ["x86", "arm64-v8a" ]
 *           experimentalProperties["ninja.path"] = "../Teapot.sln"
 *           experimentalProperties["ninja.configure"] = "msbuild"
 *           experimentalProperties["ninja.arguments"] = [
 *             "\${ndk.moduleMakeFile}",
 *            "-p:Configuration=\${ndk.variantName}",
 *            "-p:Platform=Android-\${ndk.abi}",
 *            "-p:NinjaBuildLocation=\${ndk.buildRoot}",
 *            "-p:NinjaProject=GameApplication",
 *            "-t:GenerateBuildNinja"
 *           ]
 *         }
 */

/**
 * Per-module Ninja information.
 * Planned as class, of same name, in com.android.build.api.dsl.
 */
data class Ninja(
    /**
     * The path to a build file, like ../Teapot.sln
     */
    val path : File?,
    /**
     * The path to a configuration script to call with [arguments]
     */
    val configure : File,
    /**
     * A user-specified alternate build staging location.
     */
    val buildStagingDirectory : File?
)

/**
 * Per-variant Ninja information.
 * Planned as class, of same name, in com.android.build.gradle.internal.dsl
 */
data class CoreExternalNativeNinjaOptions(
    val abiFilters : Set<String>,
    val arguments : List<String>,
    val cFlags : List<String>,
    val cppFlags : List<String>,
    val targets : Set<String>,
)

/**
 * Planned as field in  com.android.build.api.dsl.ExternalNativeBuild
 */
val VariantCreationConfig.ninja : Ninja get() = externalNativeExperimentalProperties.ninja

val Map<String, Any>.ninja : Ninja get() = Ninja(
    path = propertyAsFile("ninja.path"),
    configure = propertyAsFile("ninja.configure") ?: File(""),
    buildStagingDirectory = propertyAsFile("ninja.buildStagingDirectory")
)

/**
 * Planned as field in com.android.build.gradle.internal.dsl.CoreExternalNativeBuildOptions
 */
val VariantCreationConfig.externalNativeNinjaOptions get() = CoreExternalNativeNinjaOptions(
    abiFilters = externalNativeExperimentalProperties.propertyAsSet("ninja.abiFilters"),
    arguments = externalNativeExperimentalProperties.propertyAsList("ninja.arguments"),
    cFlags = externalNativeExperimentalProperties.propertyAsList("ninja.cFlags"),
    cppFlags = externalNativeExperimentalProperties.propertyAsList("ninja.cppFlags"),
    targets = externalNativeExperimentalProperties.propertyAsSet("ninja.targets"),
)

/**
 * Convert [name] from [externalNativeNinjaOptions] to [Set<String>].
 */
private fun Map<String, Any>.propertyAsFile(name : String) : File? {
    val value = get(name) ?: return null
    return when(value) {
        is String -> File(value)
        is File -> value
        else -> error("${value.javaClass}")
    }
}

/**
 * Convert [name] from [externalNativeNinjaOptions] to [Set<String>].
 */
private fun Map<String, Any>.propertyAsSet(name : String) : Set<String> {
    val value = get(name) ?: return setOf()
    return when (value) {
        is List<*> -> value.map { "$it" }.toSet()
        is Set<*> -> value.map { "$it" }.toSet()
        else -> error("${value.javaClass}")
    }
}

/**
 * Convert [name] from [externalNativeNinjaOptions] to [List<String>].
 */
private fun Map<String, Any>.propertyAsList(name : String) : List<String> {
    val value = get(name) ?: return listOf()
    return when (value) {
        is List<*> -> value.map { "$it" }
        is Set<*> -> value.map { "$it" }
        else -> error("${value.javaClass}")
    }
}
