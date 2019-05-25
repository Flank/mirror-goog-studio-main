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

import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.CxxModuleModel
import com.android.build.gradle.internal.cxx.model.CxxProjectModel
import com.android.build.gradle.internal.cxx.model.CxxVariantModel
import com.android.utils.FileUtils.join
import com.google.common.base.Joiner
import java.lang.RuntimeException
import java.util.Locale

/**
 * Look up CMakeSettings.json [macro] equivalent value from the C++ build abi model.
 */
fun CxxAbiModel.resolveMacroValue(macro : Macro) : String {
    return when(macro) {
        Macro.ABI -> abi.tag
        Macro.ABI_BITNESS -> info.bitness.toString()
        Macro.ABI_IS_64_BITS -> cmakeBoolean(info.bitness == 64)
        Macro.ABI_IS_DEFAULT -> cmakeBoolean(info.isDefault)
        Macro.ABI_IS_DEPRECATED -> cmakeBoolean(info.isDeprecated)
        Macro.GRADLE_BUILD_ROOT -> cxxBuildFolder.absolutePath
        Macro.GRADLE_C_FLAGS -> Joiner.on(" ").join(variant.cFlagsList)
        Macro.GRADLE_CPP_FLAGS -> Joiner.on(" ").join(variant.cppFlagsList)
        Macro.GRADLE_LIBRARY_OUTPUT_DIRECTORY -> soFolder.absolutePath
        Macro.PLATFORM -> "android-$abiPlatformVersion"
        Macro.PLATFORM_CODE -> platformCode()
        Macro.PLATFORM_SYSTEM_VERSION -> "$abiPlatformVersion"
        else -> variant.resolveMacroValue(macro)
    }
}

/**
 * Convert boolean to "1" or "0" for CMake.
 */
private fun cmakeBoolean(bool : Boolean) = if (bool) "1" else "0"

/**
 * Get the platform codename (like 'Q')
 */
private fun CxxAbiModel.platformCode(): String {
    return variant.module.ndkMetaPlatforms?.let {
        it.aliases
            .toList()
            .filter { (_, code) -> code == abiPlatformVersion }
            .minBy { (alias, _) -> alias.length }
            ?.first
    } ?: ""
}

/**
 * Look up CMakeSettings.json [macro] equivalent value from the C++ build variant model.
 */
fun CxxVariantModel.resolveMacroValue(macro : Macro) : String {
    return when(macro) {
        Macro.GRADLE_CMAKE_BUILD_TYPE -> {
            val lower = variantName.toLowerCase(Locale.ROOT)
            when {
                lower.endsWith("release") -> "Release"
                lower.endsWith("debug") -> "Debug"
                lower.endsWith("relwithdebinfo") -> "RelWithDebInfo"
                lower.endsWith("minsizerel") -> "MinSizeRel"
                else ->
                    if (isDebuggableEnabled) {
                        "Debug"
                    } else {
                        "Release"
                    }
            }
        }
        Macro.GRADLE_VARIANT_NAME -> variantName
        else -> module.resolveMacroValue(macro)
    }
}

/**
 * Look up CMakeSettings.json [macro] equivalent value from the C++ build module model.
 */
fun CxxModuleModel.resolveMacroValue(macro : Macro) : String {
    return when(macro) {
        Macro.BUILT_IN_PROJECT_DIR -> moduleRootFolder.absolutePath
        Macro.BUILT_IN_THIS_FILE -> join(makeFile.parentFile, "CMakeSettings.json").absolutePath
        Macro.BUILT_IN_THIS_FILE_DIR -> makeFile.parentFile?.absolutePath ?: ""
        Macro.BUILT_IN_WORKSPACE_ROOT -> project.rootBuildGradleFolder.absolutePath
        Macro.CMAKE_EXE -> cmake?.cmakeExe?.absolutePath ?: ""
        Macro.GRADLE_MODULE_DIR -> moduleRootFolder.absolutePath
        Macro.GRADLE_MODULE_NAME -> gradleModulePathName.substringAfterLast(":")
        Macro.MAX_PLATFORM -> ndkMetaPlatforms?.max?.toString() ?: ""
        Macro.MIN_PLATFORM -> ndkMetaPlatforms?.min?.toString() ?: ""
        Macro.NDK_CMAKE_TOOLCHAIN -> cmakeToolchainFile.absolutePath
        Macro.NDK_DIR -> ndkFolder.absolutePath
        Macro.NDK_VERSION -> ndkVersion.toString()
        Macro.NDK_VERSION_MAJOR -> ndkVersion.major.toString()
        Macro.NDK_VERSION_MINOR -> ndkVersion.minor.toString()
        Macro.NINJA_EXE -> cmake?.ninjaExe?.absolutePath ?: ""
        Macro.SDK_DIR -> project.sdkFolder.absolutePath
        else -> project.resolveMacroValue(macro)
    }
}

/**
 * Look up CMakeSettings.json [macro] equivalent value from the C++ build project model.
 */
fun CxxProjectModel.resolveMacroValue(macro : Macro) : String {
    return when(macro) {
        Macro.BUILT_IN_WORKSPACE_ROOT -> rootBuildGradleFolder.absolutePath ?: ""
        Macro.GRADLE_IS_HOSTING -> cmakeBoolean(true)
        Macro.GRADLE_PROJECT_DIR -> rootBuildGradleFolder.absolutePath
        Macro.SDK_DIR -> sdkFolder.absolutePath
        else -> throw RuntimeException("The CMakeSettings macro '${macro.ref}' cannot" +
                " be inferred from C++ build model")
    }
}