/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.settings

import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.cmake.cmakeBoolean
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.ANDROID_ABI
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.ANDROID_NDK
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.ANDROID_PLATFORM
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_ANDROID_ARCH_ABI
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_ANDROID_NDK
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_C_FLAGS
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_CXX_FLAGS
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_EXPORT_COMPILE_COMMANDS
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_LIBRARY_OUTPUT_DIRECTORY
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_MAKE_PROGRAM
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_SYSTEM_NAME
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_SYSTEM_VERSION
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_TOOLCHAIN_FILE
import com.android.build.gradle.internal.cxx.configure.NdkMetaPlatforms
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.CxxModuleModel
import com.android.build.gradle.internal.cxx.settings.Environment.NDK
import com.android.build.gradle.internal.cxx.settings.Environment.NDK_ABI
import com.android.build.gradle.internal.cxx.settings.Environment.NDK_PLATFORM
import com.android.build.gradle.internal.cxx.settings.Macro.ABI
import com.android.build.gradle.internal.cxx.settings.Macro.ABI_BITNESS
import com.android.build.gradle.internal.cxx.settings.Macro.ABI_IS_64_BITS
import com.android.build.gradle.internal.cxx.settings.Macro.ABI_IS_DEFAULT
import com.android.build.gradle.internal.cxx.settings.Macro.ABI_IS_DEPRECATED
import com.android.build.gradle.internal.cxx.settings.Macro.BUILT_IN_PROJECT_DIR
import com.android.build.gradle.internal.cxx.settings.Macro.BUILT_IN_THIS_FILE
import com.android.build.gradle.internal.cxx.settings.Macro.BUILT_IN_THIS_FILE_DIR
import com.android.build.gradle.internal.cxx.settings.Macro.BUILT_IN_WORKSPACE_ROOT
import com.android.build.gradle.internal.cxx.settings.Macro.CMAKE_EXE
import com.android.build.gradle.internal.cxx.settings.Macro.GRADLE_BUILD_ROOT
import com.android.build.gradle.internal.cxx.settings.Macro.GRADLE_C_FLAGS
import com.android.build.gradle.internal.cxx.settings.Macro.GRADLE_CMAKE_BUILD_TYPE
import com.android.build.gradle.internal.cxx.settings.Macro.GRADLE_CPP_FLAGS
import com.android.build.gradle.internal.cxx.settings.Macro.GRADLE_IS_HOSTING
import com.android.build.gradle.internal.cxx.settings.Macro.GRADLE_LIBRARY_OUTPUT_DIRECTORY
import com.android.build.gradle.internal.cxx.settings.Macro.GRADLE_MODULE_DIR
import com.android.build.gradle.internal.cxx.settings.Macro.GRADLE_MODULE_NAME
import com.android.build.gradle.internal.cxx.settings.Macro.GRADLE_PROJECT_DIR
import com.android.build.gradle.internal.cxx.settings.Macro.GRADLE_VARIANT_NAME
import com.android.build.gradle.internal.cxx.settings.Macro.MAX_PLATFORM
import com.android.build.gradle.internal.cxx.settings.Macro.MIN_PLATFORM
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_CMAKE_TOOLCHAIN
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_DIR
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_VERSION
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_VERSION_MAJOR
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_VERSION_MINOR
import com.android.build.gradle.internal.cxx.settings.Macro.NINJA_EXE
import com.android.build.gradle.internal.cxx.settings.Macro.PLATFORM
import com.android.build.gradle.internal.cxx.settings.Macro.PLATFORM_CODE
import com.android.build.gradle.internal.cxx.settings.Macro.PLATFORM_SYSTEM_VERSION
import com.android.build.gradle.internal.cxx.settings.Macro.SDK_DIR
import com.android.build.gradle.internal.cxx.settings.PropertyValue.LookupPropertyValue
import com.android.build.gradle.internal.cxx.settings.PropertyValue.StringPropertyValue
import com.android.utils.FileUtils.join

const val TRADITIONAL_CONFIGURATION_NAME = "traditional-android-studio-cmake-environment"

/**
 * This is a CMakeSettings.json file that is equivalent to the environment CMakeServerJsonGenerator
 * traditionally has run.
 */
fun getCmakeServerDefaultEnvironment() : CMakeSettings {
    val result = CMakeSettingsConfigurationBuilder()
        .initialize(createCmakeSettingsJsonFromString("""
            {
                "configurations": [{
                    "name": "$TRADITIONAL_CONFIGURATION_NAME",
                    "description": "Configuration generated by Android Gradle Plugin",
                    "inheritEnvironments": ["ndk"],
                    "generator": "Ninja",
                    "configurationType": "${GRADLE_CMAKE_BUILD_TYPE.ref}",
                    "buildRoot": "${GRADLE_BUILD_ROOT.ref}",
                    "cmakeExecutable": "${CMAKE_EXE.ref}",
                    "cmakeToolchain": "${NDK_CMAKE_TOOLCHAIN.ref}",
                    "variables": [
                        {"name": "$ANDROID_ABI", "value": "${ABI.ref}"},
                        {"name": "$ANDROID_NDK", "value": "${NDK_DIR.ref}"},
                        {"name": "$ANDROID_PLATFORM", "value": "${PLATFORM_SYSTEM_VERSION.ref}"},
                        {"name": "$CMAKE_ANDROID_ARCH_ABI", "value": "${ABI.ref}"},
                        {"name": "$CMAKE_ANDROID_NDK", "value": "${NDK_DIR.ref}"},
                        {"name": "$CMAKE_C_FLAGS", "value": "${GRADLE_C_FLAGS.ref}"},
                        {"name": "$CMAKE_CXX_FLAGS", "value": "${GRADLE_CPP_FLAGS.ref}"},
                        {"name": "$CMAKE_EXPORT_COMPILE_COMMANDS", "value": "ON"},
                        {"name": "$CMAKE_LIBRARY_OUTPUT_DIRECTORY", "value": "${GRADLE_LIBRARY_OUTPUT_DIRECTORY.ref}"},
                        {"name": "$CMAKE_MAKE_PROGRAM", "value": "${NINJA_EXE.ref}"},
                        {"name": "$CMAKE_SYSTEM_NAME", "value": "Android"},
                        {"name": "$CMAKE_SYSTEM_VERSION", "value": "${PLATFORM_SYSTEM_VERSION.ref}"},
                        {"name": "$CMAKE_TOOLCHAIN_FILE", "value": "${NDK_CMAKE_TOOLCHAIN.ref}"}
                    ]
                }]
            }""".trimIndent()).configurations[0])
    return CMakeSettings(configurations = listOf(result.build()))
}

/**
 * Information that would naturally come from the NDK.
 */
fun CxxModuleModel.getNdkMetaCmakeSettingsJson() : CMakeSettings {
    val environments =
        mutableMapOf<String, Map<Macro, PropertyValue>>()
    val nameTable = mutableMapOf<Macro, PropertyValue>()
    environments[NDK.environment] = nameTable

    nameTable[MIN_PLATFORM] = LookupPropertyValue { resolveMacroValue(MIN_PLATFORM) }
    nameTable[MAX_PLATFORM] = LookupPropertyValue { resolveMacroValue(MAX_PLATFORM) }
    nameTable[NDK_CMAKE_TOOLCHAIN] = LookupPropertyValue { resolveMacroValue(NDK_CMAKE_TOOLCHAIN) }

    // Per-ABI environments
    for(abiValue in Abi.values()) {
        val abiInfo by lazy {
            ndkMetaAbiList.singleOrNull {
                it.abi == abiValue
            }
        }
        val abiNameTable = mutableMapOf<Macro, PropertyValue>()
        environments[NDK_ABI.environment.replace(ABI.ref, abiValue.tag)] = abiNameTable
        abiNameTable[ABI_BITNESS] = LookupPropertyValue {
            abiInfo?.bitness?.toString() ?: "$abiValue" }
        abiNameTable[ABI_IS_64_BITS] = LookupPropertyValue {
            if (abiInfo != null) cmakeBoolean(abiInfo?.bitness == 64) else ""
        }
        abiNameTable[ABI_IS_DEPRECATED] = LookupPropertyValue {
            if (abiInfo != null) cmakeBoolean(abiInfo!!.isDeprecated) else ""
        }
        abiNameTable[ABI_IS_DEFAULT] = LookupPropertyValue {
            if (abiInfo != null) cmakeBoolean(abiInfo!!.isDefault) else ""
        }
    }

    // Per-platform environments. In order to be lazy, promise future platform versions and return
    // blank for PLATFORM_CODE when they are evaluated and don't exist.
    for(potentialPlatform in NdkMetaPlatforms.potentialPlatforms) {
        val platformNameTable = mutableMapOf<Macro, PropertyValue>()
        val environmentName =
            NDK_PLATFORM.environment.replace(PLATFORM_SYSTEM_VERSION.ref, potentialPlatform.toString())
        environments[environmentName] = platformNameTable
        platformNameTable[PLATFORM_SYSTEM_VERSION] = StringPropertyValue("$potentialPlatform")
        platformNameTable[PLATFORM] = StringPropertyValue("android-$potentialPlatform")
        platformNameTable[PLATFORM_CODE] = LookupPropertyValue {
            ndkMetaPlatforms!!.aliases.toList().lastOrNull {
                (_, platform) -> platform == potentialPlatform
            } ?.first ?: ""
        }
    }

    val settingsEnvironments =
        environments.map { (name, properties) ->
        val environment = properties.map { it.key.environment }.toSet().single()
        CMakeSettingsEnvironment(
            namespace = environment.namespace,
            environment = name,
            inheritEnvironments = environment.inheritEnvironments.map { it.environment },
            properties = properties
                .map { (macro, property) -> Pair(macro.tag, property) }
                .toMap()
        )
    }
    return CMakeSettings(
        environments = settingsEnvironments,
        configurations = listOf()
    )
}

/**
 * Builds the default android hosting environment.
 */
fun CxxAbiModel.getAndroidGradleCmakeSettings() : CMakeSettings {
    val nameTable = mutableMapOf<Macro, PropertyValue>()
    nameTable[ABI] = LookupPropertyValue { abi.tag }
    nameTable[SDK_DIR] = LookupPropertyValue { resolveMacroValue(SDK_DIR) }
    nameTable[NDK_DIR] = LookupPropertyValue { this.resolveMacroValue(NDK_DIR) }
    nameTable[CMAKE_EXE] = LookupPropertyValue { this.resolveMacroValue(CMAKE_EXE) }
    nameTable[NINJA_EXE] = LookupPropertyValue { this.resolveMacroValue(NINJA_EXE) }
    nameTable[NDK_VERSION] = LookupPropertyValue { this.resolveMacroValue(NDK_VERSION) }
    nameTable[NDK_VERSION_MAJOR] = LookupPropertyValue {
        variant.module.ndkVersion.major.toString()
    }
    nameTable[NDK_VERSION_MINOR] = LookupPropertyValue {
        variant.module.ndkVersion.minor.toString()
    }
    nameTable[GRADLE_PROJECT_DIR] = LookupPropertyValue { this.resolveMacroValue(GRADLE_PROJECT_DIR) }
    nameTable[GRADLE_MODULE_DIR] = LookupPropertyValue { this.resolveMacroValue(GRADLE_MODULE_DIR) }
    nameTable[GRADLE_VARIANT_NAME] = LookupPropertyValue { variant.variantName }
    nameTable[GRADLE_MODULE_NAME] = LookupPropertyValue {
        variant.module.gradleModulePathName
    }
    nameTable[GRADLE_BUILD_ROOT] = LookupPropertyValue { this.resolveMacroValue(GRADLE_BUILD_ROOT) }
    nameTable[GRADLE_LIBRARY_OUTPUT_DIRECTORY] = LookupPropertyValue {
        this.resolveMacroValue(GRADLE_LIBRARY_OUTPUT_DIRECTORY)
    }
    nameTable[GRADLE_CMAKE_BUILD_TYPE] = LookupPropertyValue { resolveMacroValue(GRADLE_CMAKE_BUILD_TYPE) }
    nameTable[BUILT_IN_THIS_FILE_DIR] = LookupPropertyValue { this.resolveMacroValue(
        BUILT_IN_THIS_FILE_DIR
    ) }
    nameTable[BUILT_IN_THIS_FILE] = LookupPropertyValue { this.resolveMacroValue(BUILT_IN_THIS_FILE) }
    nameTable[GRADLE_IS_HOSTING] = StringPropertyValue("1")
    nameTable[BUILT_IN_PROJECT_DIR] = LookupPropertyValue { this.resolveMacroValue(
        BUILT_IN_PROJECT_DIR
    ) }
    nameTable[BUILT_IN_WORKSPACE_ROOT] = LookupPropertyValue { resolveMacroValue(BUILT_IN_WORKSPACE_ROOT) }
    val environments = nameTable
        .toList()
        .groupBy { (macro,_) -> macro.environment }
        .map { (environment, properties) ->
            CMakeSettingsEnvironment(
                namespace = environment.namespace,
                environment = environment.environment,
                inheritEnvironments = environment.inheritEnvironments.map { it.environment },
                properties = properties
                    .map { (macro, property) -> Pair(macro.tag, property) }
                    .toMap()
            )
        }

    return CMakeSettings(
        environments = environments,
        configurations = listOf()
    )
}

/**
 * Gather CMake settings from different locations.
 */
fun CxxAbiModel.gatherCMakeSettingsFromAllLocations() : CMakeSettings {
    val settings = mutableListOf<CMakeSettings>()

    // Load the user's CMakeSettings.json if there is one.
    val userSettings = join(variant.module.makeFile.parentFile, "CMakeSettings.json")
    if (userSettings.isFile) {
        settings += createCmakeSettingsJsonFromFile(userSettings)
    }

    // TODO this needs to include environment variables as well.

    // Add the synthetic traditional environment.
    settings += getCmakeServerDefaultEnvironment()

    // Construct settings for gradle hosting environment.
    settings += getAndroidGradleCmakeSettings()

    // Construct synthetic settings for the NDK
    settings += variant.module.getNdkMetaCmakeSettingsJson()
    
    return mergeCMakeSettings(*settings.toTypedArray())
}
