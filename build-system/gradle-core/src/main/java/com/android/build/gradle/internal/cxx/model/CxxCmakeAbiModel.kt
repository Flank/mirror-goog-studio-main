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

package com.android.build.gradle.internal.cxx.model

import com.android.build.gradle.internal.cxx.settings.CMakeSettingsConfiguration
import com.android.utils.FileUtils.join
import java.io.File

/**
 * Holds immutable per-ABI configuration specific to CMake needed for JSON generation.
 */
data class CxxCmakeAbiModel(
    /**
     * The base output folder for CMake-generated build artifacts.
     * ex, $moduleRootFolder/.cxx/cmake/debug/armeabi-v7a
     */
    val cmakeArtifactsBaseFolder: File,

    /**
     * The effective CMakeSettings
     */
    val effectiveConfiguration : CMakeSettingsConfiguration,

    /**
     * Log of the conversation with CMake server.
     */
    val cmakeServerLogFile: File
)

/**
 * The location of compile_commands.json if it is generated by CMake.
 *   ex, $moduleRootFolder/.cxx/cmake/debug/armeabi-v7a/compile_commands.json
 */
val CxxCmakeAbiModel.compileCommandsJsonFile: File
    get() = join(cmakeArtifactsBaseFolder,"compile_commands.json")

/**
 * The CMake file API query folder.
 *   ex, $moduleRootFolder/.cxx/cmake/debug/armeabi-v7a/.cmake/api/v1/query/client-agp
 */
val CxxCmakeAbiModel.clientQueryFolder: File
    get() = join(cmakeArtifactsBaseFolder,".cmake/api/v1/query/client-agp")

/**
 * The CMake file API reply folder.
 *   ex, $moduleRootFolder/.cxx/cmake/debug/armeabi-v7a/.cmake/api/v1/reply
 */
val CxxCmakeAbiModel.clientReplyFolder: File
    get() = join(cmakeArtifactsBaseFolder,".cmake/api/v1/reply")
