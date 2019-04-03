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

package com.android.build.gradle.internal.cxx.model

import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.tasks.NativeBuildSystem
import com.android.repository.Revision
import java.io.File

/**
 * Holds immutable module-level information for C/C++ build and sync, see README.md
 */
interface CxxModuleModel {
    /** Folder of project-level build.gradle file (ex source-root/)*/
    val rootBuildGradleFolder: File
    /** Install folder of SDK (ex sdk.dir=/path/to/sdk) */
    val sdkFolder: File
    /** Whether compiler settings cache is enabled (default -pandroid.enableNativeCompilerSettingsCache=false) */
    val isNativeCompilerSettingsCacheEnabled: Boolean
    /** Whether to build a single ABI for IDE (default -pandroid.buildOnlyTargetAbi=true) */
    val isBuildOnlyTargetAbiEnabled: Boolean
    /**  The single ABI to build for IDE (example -pandroid.injected.build.abi="x86") */
    val ideBuildTargetAbi: String?
    /**  The abiFilters from build.gradle (ex android.splits.abiFilters 'x86', 'x86_64') */
    val splitsAbiFilterSet: Set<String>
    /**  Folder for intermediates (ex source-root/Source/Android/app/build/intermediates) */
    val intermediatesFolder: File
    /**  The colon-delimited gradle path to this module
     *   (ex ':app' in ./gradlew :app:externalNativeBuildDebug) */
    val gradleModulePathName: String
    /**  Dir of the project (ex source-root/Source/Android/app) */
    val moduleRootFolder: File
    /**  The makefile (ex android.externalNativeBuild.cmake.path 'CMakeLists.txt') */
    val makeFile: File
    /**  The type of native build system (ex CMAKE) */
    val buildSystem: NativeBuildSystem
    /**  Location of project-wide compiler settings cache (ex $projectRoot/.cxx) */
    val compilerSettingsCacheFolder: File
    /**  The module level .cxx folder (ex $moduleRootFolder/.cxx) */
    val cxxFolder: File
    /**  Folder path to the NDK (ex /Android/sdk/ndk/20.0.5344622) */
    val ndkFolder: File
    /** The version of the NDK (ex 20.0.5344622-rc1) */
    val ndkVersion: Revision
    /** ABIs supported by this NDK (ex x86, x86_64) */
    val ndkSupportedAbiList: List<Abi>
    /** ABIS that are default for this NDK (ex x86_64) */
    val ndkDefaultAbiList: List<Abi>
    /** Information relevant only to CMake. Null if not CMake build. */
    val cmake: CxxCmakeModuleModel?
}

