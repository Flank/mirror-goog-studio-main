/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.tasks.NativeBuildSystem
import java.io.File

/**
 * Holds immutable per-ABI configuration information needed for JSON generation.
 * When a file or folder name is exposed, it should always contain the related ABI as a subfolder.
 * Non-ABI folders should go into JsonGenerationVariantConfiguration instead.
 */
data class JsonGenerationAbiConfiguration(
        val abi: Abi,
        val abiName : String,
        val abiPlatformVersion: Int,
        val externalNativeBuildFolder : File,
        val jsonFile : File,
        val gradleBuildOutputFolder : File,
        val objFolder : File,
        val buildCommandFile : File,
        val buildOutputFile : File,
        val cmake : Cmake?) {

    /**
     * Per-ABI information about the CMake JSON generation. Not valid for ndk-build builds.
     */
    data class Cmake(
        /**
         * Used by CMake compiler settings cache. This is the generated CMakeLists.txt file that
         * calls back to the user's CMakeLists.txt. The wrapping of CMakeLists.txt allows us
         * to insert additional functionality like save compiler settings to a file.
         */
        val cmakeListsThunkingFile : File,
        /**
         * Used by CMake compiler settings cache. This is the generated toolchain file that
         * calls back to the user's original toolchain file. The wrapping of toolchain allows us
         * to insert additional functionality such as looking for pre-existing cached compiler
         * settings and using them.
         */
        val toolchainThunkingFile : File,
        /**
         * Each of the user's CMake properties are written to a file so that they can be
         * introspected after the configuration. For example, this is how we get the user's
         * compiler settings.
         */
        val buildVariablesFile : File,
        /**
         * Compiler settings cache key. This will have content from the most recent configuration
         * like:
         *   -DANDROID_ABI=x86
         *   -DANDROID_PLATFORM=android-19
         *   -DCMAKE_ANDROID_ARCH_ABI=x86
         *   -DCMAKE_SYSTEM_VERSION=19
         * It should contain everything that describes how compiler settings are chosen.
         */
        val cacheKeyFile : File,
        /**
         * This contains the hash of the cache-key.txt file above.
         */
        val cacheHashFile : File)
}

fun createJsonGenerationAbiConfiguration(
    abi: Abi,
    variantName: String,
    externalNativeBuildBaseFolder: File,
    objBaseFolder: File,
    nativeBuildSystem: NativeBuildSystem,
    abiPlatformVersion: Int) : JsonGenerationAbiConfiguration {

    // Be *careful* don't use Enum.name when you mean Enum.getName(). The former is Kotlin's
    // definition of name in Enum.kt, the later is specific to the enum type itself.
    val abiName = abi.getName()
    val buildSystemPresentationName = nativeBuildSystem.getName()
    val objFolder = File(objBaseFolder, abiName)

    // Build up .externalNativeBuild/cmake/debug/x86
    val buildSystemFolder =
        File(externalNativeBuildBaseFolder, buildSystemPresentationName)
    val variantFolder = File(buildSystemFolder, variantName)
    val externalNativeBuildFolder = File(variantFolder, abiName)
    val jsonFile = File(externalNativeBuildFolder, "android_gradle_build.json")
    val buildCommandFile =
        File(externalNativeBuildFolder,"${buildSystemPresentationName}_build_command.txt")
    val buildOutputFile =
        File(externalNativeBuildFolder,"${buildSystemPresentationName}_build_output.txt")

    // Build up .externalNativeBuild/gradle/debug/x86
    val externalNativeBuildGradleFolder =
        File(externalNativeBuildBaseFolder, "gradle")
    val externalNativeBuildGradleVariantFolder =
        File(externalNativeBuildGradleFolder, variantName)
    val gradleBuildOutput = File(externalNativeBuildGradleVariantFolder, abiName)

    val cmake : JsonGenerationAbiConfiguration.Cmake? = if (nativeBuildSystem == NativeBuildSystem.CMAKE) {
        JsonGenerationAbiConfiguration.Cmake(
            File(gradleBuildOutput, "CMakeLists.txt"),
            File(gradleBuildOutput,"android_gradle_build.toolchain.cmake"),
            File(gradleBuildOutput, "build-variables.txt"),
            File(gradleBuildOutput, "cache-key.txt"),
            File(gradleBuildOutput, "cache-hash.txt")
        )
    } else {
        null
    }

    return JsonGenerationAbiConfiguration(
        abi,
        abiName,
        abiPlatformVersion,
        externalNativeBuildFolder,
        jsonFile,
        gradleBuildOutput,
        objFolder,
        buildCommandFile,
        buildOutputFile,
        cmake)

}