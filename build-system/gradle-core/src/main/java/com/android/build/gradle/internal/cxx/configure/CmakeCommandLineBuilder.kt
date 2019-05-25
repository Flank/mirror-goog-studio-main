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

package com.android.build.gradle.internal.cxx.configure

import com.android.build.gradle.internal.cxx.configure.CmakeProperty.ANDROID_ABI
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.ANDROID_NDK
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.ANDROID_PLATFORM
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_ANDROID_ARCH_ABI
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_ANDROID_NDK
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_BUILD_TYPE
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_C_FLAGS
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_CXX_FLAGS
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_EXPORT_COMPILE_COMMANDS
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_LIBRARY_OUTPUT_DIRECTORY
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_MAKE_PROGRAM
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_SYSTEM_NAME
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_SYSTEM_VERSION
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_TOOLCHAIN_FILE
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.settings.Macro.ABI
import com.android.build.gradle.internal.cxx.settings.Macro.GRADLE_C_FLAGS
import com.android.build.gradle.internal.cxx.settings.Macro.GRADLE_CMAKE_BUILD_TYPE
import com.android.build.gradle.internal.cxx.settings.Macro.GRADLE_CPP_FLAGS
import com.android.build.gradle.internal.cxx.settings.Macro.GRADLE_LIBRARY_OUTPUT_DIRECTORY
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_CMAKE_TOOLCHAIN
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_DIR
import com.android.build.gradle.internal.cxx.settings.Macro.NINJA_EXE
import com.android.build.gradle.internal.cxx.settings.Macro.PLATFORM
import com.android.build.gradle.internal.cxx.settings.Macro.PLATFORM_SYSTEM_VERSION
import com.android.build.gradle.internal.cxx.settings.resolveMacroValue
import com.android.utils.FileUtils.join
import java.io.File
import org.apache.commons.io.FileUtils

/**
 * Build the CMake command used to variables for [CxxAbiModel].
 */
fun CxxAbiModel.getCmakeCommandLineVariables() : List<CommandLineArgument> {

    val cmake = variant.module.cmake!!
    val variables = sortedMapOf<CmakeProperty, String>()
    variables[ANDROID_ABI] = resolveMacroValue(ABI)
    variables[ANDROID_PLATFORM] = resolveMacroValue(PLATFORM)
    variables[CMAKE_LIBRARY_OUTPUT_DIRECTORY] = resolveMacroValue(GRADLE_LIBRARY_OUTPUT_DIRECTORY)
    variables[CMAKE_BUILD_TYPE] = resolveMacroValue(GRADLE_CMAKE_BUILD_TYPE)
    variables[ANDROID_NDK] = resolveMacroValue(NDK_DIR)
    variables[CMAKE_C_FLAGS] = resolveMacroValue(GRADLE_C_FLAGS)
    variables[CMAKE_CXX_FLAGS] = resolveMacroValue(GRADLE_CPP_FLAGS)
    if (cmake.minimumCmakeVersion.isCmakeForkVersion()) {
        variables[CMAKE_TOOLCHAIN_FILE] = resolveMacroValue(NDK_CMAKE_TOOLCHAIN)
        variables[CMAKE_MAKE_PROGRAM] = resolveMacroValue(NINJA_EXE)
    } else {
        variables[CMAKE_SYSTEM_NAME] = "Android"
        variables[CMAKE_ANDROID_ARCH_ABI] = resolveMacroValue(ABI)
        variables[CMAKE_SYSTEM_VERSION] = resolveMacroValue(PLATFORM_SYSTEM_VERSION)
        variables[CMAKE_EXPORT_COMPILE_COMMANDS] = "ON"
        variables[CMAKE_ANDROID_NDK] = resolveMacroValue(NDK_DIR)
        variables[CMAKE_TOOLCHAIN_FILE] = getToolchainFile().absolutePath
        variables[CMAKE_MAKE_PROGRAM] = resolveMacroValue(NINJA_EXE)
    }

    // TODO Inject settings from CMakeSettings.json here

    val result = mutableListOf<CommandLineArgument>()
    result += "-G${this.cmake!!.generator}".toCmakeArgument()
    for((name,value) in variables) {
        result += "-D$name=$value".toCmakeArgument()
    }
    result += parseCmakeArguments(variant.buildSystemArgumentList)
    return result
}

/** Returns the toolchain file to be used.  */
private fun CxxAbiModel.getToolchainFile(): File {
    // NDK versions r15 and above have the fix in android.toolchain.cmake to work with CMake
    // version 3.7+, but if the user has NDK r14 or below, we add the (hacky) fix
    // programmatically.
    return if (variant.module.ndkVersion.major >= 15) {
        // Add our toolchain file.
        // Note: When setting this flag, Cmake's android toolchain would end up calling our
        // toolchain via ndk-cmake-hooks, but our toolchains will (ideally) be executed only
        // once.
        File(resolveMacroValue(NDK_CMAKE_TOOLCHAIN))
    } else {
        /**
         * Returns a pre-ndk-r15-wrapper android toolchain cmake file for NDK r14 and below that has a
         * fix to work with CMake versions 3.7+. Note: This is a hacky solution, ideally, the user
         * should install NDK r15+ so it works with CMake 3.7+.
         */
        val toolchainFile = join(cxxBuildFolder, "pre-ndk-r15-wrapper-android.toolchain.cmake")
        FileUtils.writeStringToFile(toolchainFile,
            """
            # This toolchain file was generated by Gradle to support NDK versions r14 and below.
            include("${resolveMacroValue(NDK_CMAKE_TOOLCHAIN).replace("\\", "/")}")
            set($CMAKE_SYSTEM_VERSION 1)
            """.trimIndent())
        toolchainFile
    }
}


