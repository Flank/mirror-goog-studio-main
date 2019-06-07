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

import com.android.build.gradle.internal.cxx.cmake.cmakeBoolean
import com.android.build.gradle.internal.cxx.configure.CmakeProperty
import com.android.build.gradle.internal.cxx.logging.infoln
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.CxxModuleModel
import com.android.build.gradle.internal.cxx.model.CxxProjectModel
import com.android.build.gradle.internal.cxx.model.CxxVariantModel
import com.android.build.gradle.internal.cxx.model.soFolder
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
        Macro.NDK_CMAKE_TOOLCHAIN -> getToolchainFile()
        Macro.PLATFORM -> "android-$abiPlatformVersion"
        Macro.PLATFORM_CODE -> platformCode()
        Macro.PLATFORM_SYSTEM_VERSION -> "$abiPlatformVersion"
        else -> variant.resolveMacroValue(macro)
    }
}

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
    // True configuration hash values need to be computed after macros are resolved.
    // This is a placeholder hash that is used until the real hash is known.
    // It's also good for example values.
    val placeHolderHash = "1m6w461rf3l272y5d5d5c2m651a4i4j1c3n69zm476ys1g403j69363k4519"
    return when(macro) {
        Macro.BUILT_IN_PROJECT_DIR -> moduleRootFolder.absolutePath
        Macro.BUILT_IN_THIS_FILE -> join(makeFile.parentFile, "CMakeSettings.json").absolutePath
        Macro.BUILT_IN_THIS_FILE_DIR -> makeFile.parentFile?.absolutePath ?: ""
        Macro.BUILT_IN_WORKSPACE_ROOT -> project.rootBuildGradleFolder.absolutePath
        Macro.CMAKE_EXE -> cmake?.cmakeExe?.absolutePath ?: ""
        Macro.GRADLE_CONFIGURATION_HASH -> placeHolderHash
        Macro.GRADLE_MODULE_DIR -> moduleRootFolder.absolutePath
        Macro.GRADLE_MODULE_NAME -> gradleModulePathName.substringAfterLast(":")
        Macro.GRADLE_SHORT_CONFIGURATION_HASH -> placeHolderHash.substring(0, 8)
        Macro.MAX_PLATFORM -> ndkMetaPlatforms?.max?.toString() ?: ""
        Macro.MIN_PLATFORM -> ndkMetaPlatforms?.min?.toString() ?: ""
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

/** Returns the toolchain file to be used.  */
private fun CxxAbiModel.getToolchainFile(): String {
    // NDK versions r15 and above have the fix in android.toolchain.cmake to work with CMake
    // version 3.7+, but if the user has NDK r14 or below, we add the (hacky) fix
    // programmatically.
    val originalToolchain = variant.module.originalCmakeToolchainFile.absolutePath
    return if (variant.module.ndkVersion.major >= 15) {
        // Add our toolchain file.
        // Note: When setting this flag, Cmake's android toolchain would end up calling our
        // toolchain via ndk-cmake-hooks, but our toolchains will (ideally) be executed only
        // once.
        originalToolchain
    } else {
        /**
         * Returns a pre-ndk-r15-wrapper android toolchain cmake file for NDK r14 and below that has a
         * fix to work with CMake versions 3.7+. Note: This is a hacky solution, ideally, the user
         * should install NDK r15+ so it works with CMake 3.7+.
         */
        val toolchainFile = join(cxxBuildFolder, "pre-ndk-r15-wrapper-android.toolchain.cmake")
        cxxBuildFolder.mkdirs()
        toolchainFile.writeText(
            """
            # This toolchain file was generated by Gradle to support NDK versions r14 and below.
            include("${originalToolchain.replace("\\", "/")}")
            set(${CmakeProperty.CMAKE_SYSTEM_VERSION} 1)
            """.trimIndent())
        infoln("Replacing toolchain '$originalToolchain' with wrapper '$toolchainFile'")
        toolchainFile.absolutePath
    }
}
