/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http:/** www.apache.org/licenses/LICENSE-2.0 */
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.build.gradle.internal.cxx.model

import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.services.CxxServiceRegistry
import com.android.build.gradle.internal.ndk.AbiInfo
import com.android.build.gradle.tasks.NativeBuildSystem.NDK_BUILD
import com.android.build.gradle.tasks.NativeBuildSystem.CMAKE
import com.android.utils.FileUtils.join
import java.io.File

/**
 * Holds immutable ABI-level information for C/C++ build and sync, see README.md
 */
interface CxxAbiModel {
    /**
     * The target ABI
     */
    val abi: Abi

    /**
     * Metadata about the ABI
     */
    val info: AbiInfo

    /**
     * The final platform version for this ABI (ex 28)
     */
    val abiPlatformVersion: Int

    /**
     * The .cxx build folder
     *   ex, $moduleRootFolder/.cxx/ndkBuild/debug/armeabi-v7a
     */
    val cxxBuildFolder: File
        get() = join(variant.jsonFolder, abi.tag)

    /**
     * The model json
     *   ex, $moduleRootFolder/.cxx/ndkBuild/debug/armeabi-v7a/android_gradle_build.json
     */
    val jsonFile: File
        get() = join(cxxBuildFolder,"android_gradle_build.json")

    /**
     * The ninja log file
     *   ex, $moduleRootFolder/.cxx/cmake/debug/x86/.ninja_log
     */
    val ninjaLogFile: File
        get() = join(cxxBuildFolder, ".ninja_log")

    /**
     * Folder for .o files
     *   ex, $moduleRootFolder/build/intermediates/ndkBuild/debug/obj/local/armeabi-v7a
     */
    val objFolder: File
        get() = when(variant.module.buildSystem) {
            CMAKE -> join(cxxBuildFolder, "CMakeFiles")
            NDK_BUILD -> join(variant.objFolder, abi.tag)
            else -> throw RuntimeException(variant.module.buildSystem.toString())
        }

    /**
     * Folder for .so files
     *   ex, $moduleRootFolder/build/intermediates/ndkBuild/debug/obj/local/armeabi-v7a
     */
    val soFolder: File
        get() = join(variant.objFolder, abi.tag)

    /**
     * The command that is executed to build or generate projects
     *   ex, $moduleRootFolder/.cxx/ndkBuild/debug/armeabi-v7a/build_command.txt
     */
    val buildCommandFile: File
        get() = join(cxxBuildFolder, "build_command.txt")

    /**
     * Output of the build
     *   ex $moduleRootFolder/.cxx/ndkBuild/debug/armeabi-v7a/build_output.txt
     */
    val buildOutputFile: File
        get() = join(cxxBuildFolder, "build_output.txt")

    /**
     * Output file of the Cxx*Model structure
     *   ex, $moduleRootFolder/.cxx/ndkBuild/debug/armeabi-v7a/build_model.json
     */
    val modelOutputFile: File
        get() = join(cxxBuildFolder, "build_model.json")

    /**
     * Json Generation logging record
     */
    val jsonGenerationLoggingRecordFile: File
        get() = join(cxxBuildFolder,"json_generation_record.json")

    /**
     * CMake-specific settings for this ABI. Return null if this isn't CMake.
     */
    val cmake: CxxCmakeAbiModel?

    /**
     * The variant for this ABI
     */
    val variant: CxxVariantModel

    /**
     * Service provider entry for abi-level services. These are services naturally
     * scoped at the module level.
     */
    val services: CxxServiceRegistry
}
