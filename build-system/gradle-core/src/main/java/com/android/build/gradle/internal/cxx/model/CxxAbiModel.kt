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
import java.io.File

/**
 * Holds immutable ABI-level information for C/C++ build and sync, see README.md
 */
interface CxxAbiModel {
    /**  The target ABI */
    val abi: Abi
    /**  The final platform version for this ABI (ex 28) */
    val abiPlatformVersion: Int
    /**  The .cxx build folder (ex '$moduleRootFolder/.cxx/ndkBuild/debug/armeabi-v7a') */
    val cxxBuildFolder: File
    /**  The model json (ex '$moduleRootFolder/.cxx/ndkBuild/debug/armeabi-v7a/android_gradle_build.json') */
    val jsonFile: File
    /**  The gradle build output folder (ex '$moduleRootFolder/.cxx/cxx/debug/armeabi-v7a') */
    val gradleBuildOutputFolder: File
    /**  Folder for .o files (ex '$moduleRootFolder/build/intermediates/ndkBuild/debug/obj/local/armeabi-v7a') */
    val objFolder: File
    /**  The command that is executed to build or generate projects (ex '$moduleRootFolder/.cxx/ndkBuild/debug/armeabi-v7a/ndkBuild_build_command.txt') */
    val buildCommandFile: File
    /**  Output of the build (ex '$moduleRootFolder/.cxx/ndkBuild/debug/armeabi-v7a/ndkBuild_build_output.txt') */
    val buildOutputFile: File
    /**  Output file of the Cxx*Model structure */
    val modelOutputFile: File
    /**  CMake-specific settings for this ABI */
    val cmake: CxxCmakeAbiModel?
    /**  The variant for this ABI */
    val variant: CxxVariantModel
}
