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

package com.android.build.gradle.internal.cxx.settings

import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.cmake.cmakeBoolean
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.ANDROID_ABI
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.ANDROID_NDK
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.ANDROID_PLATFORM
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_ANDROID_ARCH_ABI
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_ANDROID_NDK
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_CXX_FLAGS
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_C_FLAGS
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_EXPORT_COMPILE_COMMANDS
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_FIND_ROOT_PATH
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_LIBRARY_OUTPUT_DIRECTORY
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_MAKE_PROGRAM
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_RUNTIME_OUTPUT_DIRECTORY
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_SYSTEM_NAME
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_SYSTEM_VERSION
import com.android.build.gradle.internal.cxx.configure.NdkMetaPlatforms
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.ifCMake
import com.android.build.gradle.internal.cxx.model.shouldGeneratePrefabPackages
import com.android.build.gradle.internal.cxx.settings.Environment.GRADLE
import com.android.build.gradle.internal.cxx.settings.Environment.MICROSOFT_BUILT_IN
import com.android.build.gradle.internal.cxx.settings.Environment.NDK
import com.android.build.gradle.internal.cxx.settings.Environment.NDK_EXPOSED_BY_HOST
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_ABI
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_ABI_BITNESS
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_ABI_IS_64_BITS
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_ABI_IS_DEFAULT
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_ABI_IS_DEPRECATED
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_BUILD_ROOT
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_CMAKE_TOOLCHAIN
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_MAX_PLATFORM
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_MIN_PLATFORM
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_MODULE_BUILD_INTERMEDIATES_DIR
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_MODULE_BUILD_ROOT
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_MODULE_CMAKE_EXECUTABLE
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_MODULE_NDK_DIR
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_MODULE_NINJA_EXECUTABLE
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_PLATFORM
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_PLATFORM_CODE
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_PLATFORM_SYSTEM_VERSION
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_PREFAB_PATH
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_SO_OUTPUT_DIR
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_VARIANT_BUILD_INTERMEDIATES_DIR
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_VARIANT_BUILD_ROOT
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_VARIANT_CPP_FLAGS
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_VARIANT_C_FLAGS
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_VARIANT_NAME
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_VARIANT_OPTIMIZATION_TAG
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_VARIANT_SO_OUTPUT_DIR
import com.android.utils.FileUtils.join

const val TRADITIONAL_CONFIGURATION_NAME = "traditional-android-studio-cmake-environment"

/**
 * This is a CMakeSettings.json file that is equivalent to the environment CMakeServerJsonGenerator
 * traditionally has run.
 */
fun CxxAbiModel.getCmakeServerDefaultEnvironment(): Settings {
    val variables = mutableListOf(
            SettingsConfigurationVariable(ANDROID_ABI.name, NDK_ABI.ref),
            SettingsConfigurationVariable(ANDROID_NDK.name, NDK_MODULE_NDK_DIR.ref),
            SettingsConfigurationVariable(ANDROID_PLATFORM.name, NDK_PLATFORM.ref),
            SettingsConfigurationVariable(CMAKE_ANDROID_ARCH_ABI.name, NDK_ABI.ref),
            SettingsConfigurationVariable(CMAKE_ANDROID_NDK.name, NDK_MODULE_NDK_DIR.ref),
            SettingsConfigurationVariable(CMAKE_C_FLAGS.name, NDK_VARIANT_C_FLAGS.ref),
            SettingsConfigurationVariable(CMAKE_CXX_FLAGS.name, NDK_VARIANT_CPP_FLAGS.ref),
            SettingsConfigurationVariable(CMAKE_EXPORT_COMPILE_COMMANDS.name, "ON"),
            SettingsConfigurationVariable(
                    CMAKE_LIBRARY_OUTPUT_DIRECTORY.name,
                    NDK_SO_OUTPUT_DIR.ref
            ),
            SettingsConfigurationVariable(
                    CMAKE_RUNTIME_OUTPUT_DIRECTORY.name,
                    NDK_SO_OUTPUT_DIR.ref
            ),
            SettingsConfigurationVariable(CMAKE_MAKE_PROGRAM.name, NDK_MODULE_NINJA_EXECUTABLE.ref),
            SettingsConfigurationVariable(CMAKE_SYSTEM_NAME.name, "Android"),
            SettingsConfigurationVariable(CMAKE_SYSTEM_VERSION.name, NDK_PLATFORM_SYSTEM_VERSION.ref)
    )

    if (shouldGeneratePrefabPackages()) {
        variables.add(SettingsConfigurationVariable(CMAKE_FIND_ROOT_PATH.name, join(NDK_PREFAB_PATH.ref, "prefab")))
    }

    return Settings(
            configurations = listOf(
                    SettingsConfiguration(
                            name = TRADITIONAL_CONFIGURATION_NAME,
                            description = "Configuration generated by Android Gradle Plugin",
                            inheritEnvironments = listOf("ndk"),
                            generator = "Ninja",
                            buildRoot = NDK_BUILD_ROOT.ref,
                            cmakeExecutable = NDK_MODULE_CMAKE_EXECUTABLE.ref,
                            cmakeToolchain = NDK_CMAKE_TOOLCHAIN.ref,
                            configurationType = NDK_VARIANT_OPTIMIZATION_TAG.ref,
                            variables = variables
                    )
            )
    )
}

/**
 * Information that would naturally come from the NDK.
 */
fun CxxAbiModel.getNdkMetaCmakeSettingsJson() : Settings {
    val environments =
            mutableMapOf<String, Map<Macro, String>>()
    val nameTable = mutableMapOf<Macro, String>()
    environments[NDK.environment] = nameTable

    nameTable[NDK_MIN_PLATFORM] = resolveMacroValue(NDK_MIN_PLATFORM)
    nameTable[NDK_MAX_PLATFORM] = resolveMacroValue(NDK_MAX_PLATFORM)
    nameTable[NDK_CMAKE_TOOLCHAIN] = resolveMacroValue(NDK_CMAKE_TOOLCHAIN)

    // Per-ABI environments
    for(abiValue in Abi.values()) {
        val abiInfo by lazy {
            variant.module.ndkMetaAbiList.singleOrNull {
                it.abi == abiValue
            }
        }
        val abiNameTable = mutableMapOf<Macro, String>()
        environments[Environment.NDK_ABI.environment.replace(NDK_ABI.ref, abiValue.tag)] = abiNameTable
        abiNameTable[NDK_ABI_BITNESS] = abiInfo?.bitness?.toString() ?: "$abiValue"
        abiNameTable[NDK_ABI_IS_64_BITS] =
            if (abiInfo != null) cmakeBoolean(abiInfo?.bitness == 64) else ""
        abiNameTable[NDK_ABI_IS_DEPRECATED] =
            if (abiInfo != null) cmakeBoolean(abiInfo!!.isDeprecated) else ""
        abiNameTable[NDK_ABI_IS_DEFAULT] =
            if (abiInfo != null) cmakeBoolean(abiInfo!!.isDefault) else ""
    }

    // Per-platform environments. In order to be lazy, promise future platform versions and return
    // blank for PLATFORM_CODE when they are evaluated and don't exist.
    val metaPlatformAliases = variant.module.ndkMetaPlatforms?.aliases?.toList()
    for (potentialPlatform in NdkMetaPlatforms.potentialPlatforms) {
        val platformNameTable = mutableMapOf<Macro, String>()
        val environmentName =
                Environment.NDK_PLATFORM.environment.replace(NDK_PLATFORM_SYSTEM_VERSION.ref,
                        potentialPlatform.toString())
        environments[environmentName] = platformNameTable
        platformNameTable[NDK_PLATFORM_SYSTEM_VERSION] = "$potentialPlatform"
        platformNameTable[NDK_PLATFORM] = "android-$potentialPlatform"
        platformNameTable[NDK_PLATFORM_CODE] = metaPlatformAliases?.lastOrNull { (_, platform) ->
            platform == potentialPlatform
        }?.first ?: ""
    }

    val settingsEnvironments =
            environments.map { (name, properties) ->
                val environment = properties.map { it.key.environment }.toSet().single()
                SettingsEnvironment(
                        namespace = environment.namespace,
                        environment = name,
                        inheritEnvironments = environment.inheritEnvironments.map { it.environment },
                        properties = properties
                                .map { (macro, property) -> Pair(macro.tag, property) }
                                .toMap()
                )
            }
    return Settings(
            environments = settingsEnvironments,
            configurations = listOf()
    )
}

/**
 * Builds the default android hosting environment.
 */
fun CxxAbiModel.getAndroidGradleCmakeSettings() : Settings {
    val nameTable = NameTable()
    nameTable.addAll(
            Macro.values()
                    .filter { it.environment == GRADLE ||
                              it.environment == MICROSOFT_BUILT_IN ||
                              it.environment == NDK_EXPOSED_BY_HOST
                    }
                    .map { macro -> macro to resolveMacroValue(macro) }
    )

    val configurationSegment = join(variant.module.buildSystem.tag, NDK_VARIANT_NAME.ref)

    nameTable.addAll(
            NDK_VARIANT_BUILD_ROOT to join(NDK_MODULE_BUILD_ROOT.ref, configurationSegment),
            NDK_VARIANT_BUILD_INTERMEDIATES_DIR to join(NDK_MODULE_BUILD_INTERMEDIATES_DIR.ref, configurationSegment),
            NDK_PREFAB_PATH to join(NDK_VARIANT_BUILD_ROOT.ref, "prefab", NDK_ABI.ref),
            NDK_BUILD_ROOT to join(NDK_VARIANT_BUILD_ROOT.ref, NDK_ABI.ref),
            NDK_VARIANT_SO_OUTPUT_DIR to join(NDK_VARIANT_BUILD_INTERMEDIATES_DIR.ref, ifCMake { "obj" } ?: "obj/local"),
            NDK_SO_OUTPUT_DIR to join(NDK_VARIANT_SO_OUTPUT_DIR.ref, NDK_ABI.ref),
    )

    return Settings(
            environments = nameTable.environments(),
            configurations = listOf()
    )
}


/**
 * Gather CMake settings from different locations.
 */
fun CxxAbiModel.gatherCMakeSettingsFromAllLocations() : Settings {
    val settings = mutableListOf<Settings>()

    // Load the user's CMakeSettings.json if there is one.
    val userSettings = join(variant.module.makeFile.parentFile, "CMakeSettings.json")
    if (userSettings.isFile) {
        settings += createSettingsFromJsonFile(userSettings)
    }

    // TODO this needs to include environment variables as well.

    // Add the synthetic traditional environment.
    settings += getCmakeServerDefaultEnvironment()

    // Construct settings for gradle hosting environment.
    settings += getAndroidGradleCmakeSettings()

    // Construct synthetic settings for the NDK
    settings += getNdkMetaCmakeSettingsJson()

    return mergeSettings(*settings.toTypedArray())
}
