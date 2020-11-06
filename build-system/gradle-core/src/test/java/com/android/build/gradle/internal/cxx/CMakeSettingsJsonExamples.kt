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

package com.android.build.gradle.internal.cxx

import com.android.build.gradle.internal.cxx.configure.CmakeProperty.ANDROID_ABI
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.ANDROID_NDK
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.ANDROID_PLATFORM
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_ANDROID_ARCH_ABI
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_ANDROID_NDK
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_BUILD_TYPE
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_CXX_FLAGS
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_C_FLAGS
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_EXPORT_COMPILE_COMMANDS
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_LIBRARY_OUTPUT_DIRECTORY
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_MAKE_PROGRAM
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_SYSTEM_NAME
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_SYSTEM_VERSION
import com.android.build.gradle.internal.cxx.model.DIFFERENT_MOCK_CMAKE_SETTINGS_CONFIGURATION
import com.android.build.gradle.internal.cxx.model.NO_ABI_IN_BUILD_ROOT_MOCK_CMAKE_SETTINGS_CONFIGURATION
import com.android.build.gradle.internal.cxx.model.NO_VARIANT_IN_BUILD_ROOT_MOCK_CMAKE_SETTINGS_CONFIGURATION
import com.android.build.gradle.internal.cxx.settings.Macro
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_ABI
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_DEFAULT_BUILD_TYPE
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_DIR
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_NINJA_EXECUTABLE
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_PROJECT_DIR
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_SYSTEM_VERSION
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_VARIANT_NAME

/**
 * Examples of CMakeSettings.json files that are parsable.
 */
val PARSABLE_CMAKE_SETTINGS_JSON_DOMAIN = listOf(
    // Simple GOMA configuration
    """{
      "configurations": [{
        "name": "android-gradle-plugin-predetermined-name",
        "description": "Remote Build",
        "inheritEnvironments": ["ndk"],
        "buildCommandArgs": "-j 100",
        "variables": [{
          "name": "CMAKE_CXX_COMPILER_LAUNCHER",
          "value": "gomacc"
        }]
      }]
    }""".trimIndent(),
    // cmakeToolchain disagrees with CMAKE_TOOLCHAIN_FILE
    """{
          "configurations": [{
            "name": "android-gradle-plugin-predetermined-name",
            "inheritEnvironments": ["ndk"],
            "cmakeToolchain": "toolchain1.cmake",
            "variables": [
              {"name": "CMAKE_TOOLCHAIN_FILE", "value": "toolchain2.cmake"}
            ]
          }]
        }
    """.trimIndent(),
    // configurationType disagrees with CMAKE_BUILD_TYPE
    """{
          "configurations": [{
            "name": "android-gradle-plugin-predetermined-name",
            "inheritEnvironments": ["ndk"],
            "configurationType": "Debug",
            "variables": [
              {"name": "CMAKE_BUILD_TYPE", "value": "Release"}
            ]
          }]
        }
    """.trimIndent(),
    """{
          "environments": [{
            "environment": "ndk-setup",
            "namespace": "ndkSetup",
            "inheritEnvironments": ["ndk"],
            "outputRoot": "${Macro.ENV_WORKSPACE_ROOT.ref}/.cxx/cmake/build",
            "hashAbi": "${'$'}{ndk.configurationHash}/${'$'}{ndk.abi}"
          }],
          "configurations": [{
            "name": "android-gradle-plugin-predetermined-name",
            "description": "Configuration generated by Android Gradle Plugin",
            "inheritEnvironments": ["ndk-setup"],
            "buildRoot": "${'$'}{ndkSetup.outputRoot}/build/${'$'}{ndkSetup.hashAbi}",
            "variables": [
              {"name": "CMAKE_LIBRARY_OUTPUT_DIRECTORY", "value": "${'$'}{ndkSetup.outputRoot}/lib/${'$'}{ndkSetup.hashAbi}"}
            ]
          }]
        }
    """.trimIndent(),
    """
        {
            "configurations": [{
                "name": "$DIFFERENT_MOCK_CMAKE_SETTINGS_CONFIGURATION",
                "inheritEnvironments": ["ndk"],
                "generator": "some other generator",
                "buildRoot": "some other build root folder/${NDK_VARIANT_NAME.ref}/${NDK_ABI.ref}",
                "cmakeExecutable": "my/path/to/cmake",
                "cmakeToolchain": "my/path/to/toolchain",
                "variables": [
                    {"name": "$ANDROID_ABI", "value": "${NDK_ABI.ref}"},
                    {"name": "$ANDROID_PLATFORM", "value": "${NDK_SYSTEM_VERSION.ref}"},
                    {"name": "$CMAKE_LIBRARY_OUTPUT_DIRECTORY", "value":
                       "${NDK_PROJECT_DIR.ref}/build/android/lib/${NDK_DEFAULT_BUILD_TYPE.ref}/${NDK_ABI.ref}" },
                    {"name": "$ANDROID_NDK", "value": "${NDK_DIR.ref}"},
                    {"name": "$CMAKE_SYSTEM_NAME", "value": "Android"},
                    {"name": "$CMAKE_ANDROID_ARCH_ABI", "value": "${NDK_ABI.ref}"},
                    {"name": "$CMAKE_BUILD_TYPE", "value": "MyCustomBuildType"},
                    {"name": "$CMAKE_SYSTEM_VERSION", "value": "${NDK_SYSTEM_VERSION.ref}"},
                    {"name": "$CMAKE_EXPORT_COMPILE_COMMANDS", "value": "ON"},
                    {"name": "$CMAKE_ANDROID_NDK", "value": "${NDK_DIR.ref}"},
                    {"name": "$CMAKE_MAKE_PROGRAM", "value": "${NDK_NINJA_EXECUTABLE.ref}"},
                    {"name": "$CMAKE_C_FLAGS", "value": "-DTEST_C_FLAG -DTEST_C_FLAG_2"},
                    {"name": "$CMAKE_CXX_FLAGS", "value": "-DTEST_CPP_FLAG"},
                ]
            }, {
                "name": "$NO_ABI_IN_BUILD_ROOT_MOCK_CMAKE_SETTINGS_CONFIGURATION",
                "inheritEnvironments": ["ndk"],
                "buildRoot": "project-build-root/${NDK_VARIANT_NAME.ref}"
            }, {
                "name": "$NO_VARIANT_IN_BUILD_ROOT_MOCK_CMAKE_SETTINGS_CONFIGURATION",
                "inheritEnvironments": ["ndk"],
                "buildRoot": "project-build-root/${NDK_ABI.ref}"
            } ]
        }""".trimIndent()
)
