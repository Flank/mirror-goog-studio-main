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
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.ANDROID_PLATFORM
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_LIBRARY_OUTPUT_DIRECTORY
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_BUILD_TYPE
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.ANDROID_NDK
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_C_FLAGS
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_CXX_FLAGS
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_TOOLCHAIN_FILE
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_MAKE_PROGRAM
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_SYSTEM_NAME
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_ANDROID_ARCH_ABI
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_SYSTEM_VERSION
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_EXPORT_COMPILE_COMMANDS
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_ANDROID_NDK
import com.android.build.gradle.internal.cxx.configure.CommandLineArgument.DefineProperty.Companion.from
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.utils.FileUtils.join
import com.google.common.base.Joiner
import org.apache.commons.io.FileUtils
import java.io.File

/**
 * Build the CMake command used to variables for [CxxAbiModel].
 */
fun CxxAbiModel.getCmakeCommandLineVariables() : List<CommandLineArgument> {
    val result = mutableListOf<CommandLineArgument>()
    val cmake = variant.module.cmake!!
    result += if (cmake.minimumCmakeVersion.isCmakeForkVersion()) {
            "-GAndroid Gradle - Ninja"
        } else {
            "-GNinja"
        }.toCmakeArgument()
    result += from(ANDROID_ABI, abi.tag)
    result += from(ANDROID_PLATFORM,"android-$abiPlatformVersion")
    result += from(CMAKE_LIBRARY_OUTPUT_DIRECTORY, join(variant.objFolder, abi.tag).absolutePath)
    result += from(CMAKE_BUILD_TYPE, if (variant.isDebuggableEnabled) "Debug" else "Release")
    result += from(ANDROID_NDK, variant.module.ndkFolder.absolutePath)
    if (variant.cFlagsList.isNotEmpty()) {
        result += from(CMAKE_C_FLAGS, Joiner.on(" ").join(variant.cFlagsList))
    }

    if (variant.cppFlagsList.isNotEmpty()) {
        result += from(CMAKE_CXX_FLAGS, Joiner.on(" ").join(variant.cppFlagsList))
    }

    if (cmake.minimumCmakeVersion.isCmakeForkVersion()) {
        result += from(CMAKE_TOOLCHAIN_FILE, variant.module.cmakeToolchainFile.absolutePath)
        result += from(CMAKE_MAKE_PROGRAM, cmake.ninjaExe.absolutePath)
    } else {
        result += from(CMAKE_SYSTEM_NAME, "Android")
        result += from(CMAKE_ANDROID_ARCH_ABI, abi.tag)
        result += from(CMAKE_SYSTEM_VERSION, "$abiPlatformVersion")
        result += from(CMAKE_EXPORT_COMPILE_COMMANDS, "ON")
        result += from(CMAKE_ANDROID_NDK, variant.module.ndkFolder.absolutePath)
        result += from(CMAKE_TOOLCHAIN_FILE, getToolchainFile().absolutePath)
        if (variant.module.cmake!!.ninjaExe.isFile) {
            result += from(CMAKE_MAKE_PROGRAM, variant.module.cmake!!.ninjaExe.absolutePath)
        }
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
        variant.module.cmakeToolchainFile
    } else {
        /**
         * Returns a pre-ndk-r15-wrapper android toolchain cmake file for NDK r14 and below that has a
         * fix to work with CMake versions 3.7+. Note: This is a hacky solution, ideally, the user
         * should install NDK r15+ so it works with CMake 3.7+.
         */
        val toolchainFile = join(cxxBuildFolder, "pre-ndk-r15-wrapper-android.toolchain.cmake")
        FileUtils.writeStringToFile(toolchainFile,
            """
            # This toolchain file was generated by Android Gradle Plugin to support NDK versions r14 and below.
            include("${variant.module.cmakeToolchainFile.path.replace('\\', '/')}")
            set($CMAKE_SYSTEM_VERSION 1)
            """.trimIndent())
        toolchainFile
    }
}


