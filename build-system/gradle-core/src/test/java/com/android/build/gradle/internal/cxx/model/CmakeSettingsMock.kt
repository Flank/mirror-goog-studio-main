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
import com.android.build.gradle.internal.cxx.configure.CmakeProperty
import com.android.build.gradle.internal.cxx.settings.Macro
import com.android.build.gradle.internal.dsl.CoreExternalNativeCmakeOptions
import com.android.utils.FileUtils.join
import org.mockito.Mockito

const val DIFFERENT_MOCK_CMAKE_SETTINGS_CONFIGURATION = "different-mock-cmake-settings-configuration"

/**
 * Set up a basic environment that will result in a CMake [CxxModuleModel]
 */
class CmakeSettingsMock : BasicModuleModelMock() {
    val module by lazy { tryCreateCxxModuleModel(global, cmakeFinder)!! }
    val variant by lazy { createCxxVariantModel(module, baseVariantData) }
    val abi by lazy { createCxxAbiModel(variant, Abi.X86, global, baseVariantData) }

    private val coreExternalNativeCmakeOptions = Mockito.mock(
        CoreExternalNativeCmakeOptions::class.java,
        throwUnmocked
    )!!

    init {
        Mockito.doReturn(coreExternalNativeCmakeOptions).`when`(coreExternalNativeBuildOptions).externalNativeCmakeOptions
        Mockito.doReturn(setOf<String>()).`when`(coreExternalNativeCmakeOptions).abiFilters
        Mockito.doReturn(listOf("-DCMAKE_ARG=1")).`when`(coreExternalNativeCmakeOptions).arguments
        Mockito.doReturn(listOf("-DC_FLAG_DEFINED")).`when`(coreExternalNativeCmakeOptions).getcFlags()
        Mockito.doReturn(listOf("-DCPP_FLAG_DEFINED")).`when`(coreExternalNativeCmakeOptions).cppFlags
        Mockito.doReturn(setOf<String>()).`when`(coreExternalNativeCmakeOptions).targets
        val makefile = join(allPlatformsProjectRootDir, "CMakeLists.txt")
        val cmakeSettingsJson = join(allPlatformsProjectRootDir, "CMakeSettings.json")
        cmakeSettingsJson.parentFile.mkdirs()
        cmakeSettingsJson.writeText(
            """
            {
                "configurations": [{
                    "name": "$DIFFERENT_MOCK_CMAKE_SETTINGS_CONFIGURATION",
                    "inheritEnvironments": ["ndk"],
                    "generator": "some other generator",
                    "configurationType": "${Macro.GRADLE_CMAKE_BUILD_TYPE.ref}",
                    "buildRoot": "some other build root folder/${Macro.GRADLE_VARIANT_NAME.ref}/${Macro.ABI.ref}",
                    "cmakeExecutable": "my/path/to/cmake",
                    "cmakeToolchain": "my/path/to/toolchain",
                    "variables": [
                        {"name": "${CmakeProperty.ANDROID_ABI}", "value": "${Macro.ABI.ref}"},
                        {"name": "${CmakeProperty.ANDROID_PLATFORM}", "value": "${Macro.PLATFORM_SYSTEM_VERSION.ref}"},
                        {"name": "${CmakeProperty.CMAKE_LIBRARY_OUTPUT_DIRECTORY}", "value":
                           "${Macro.GRADLE_PROJECT_DIR.ref}/build/android/lib/${Macro.GRADLE_CMAKE_BUILD_TYPE.ref}/${Macro.ABI.ref}" },
                        {"name": "${CmakeProperty.ANDROID_NDK}", "value": "${Macro.NDK_DIR.ref}"},
                        {"name": "${CmakeProperty.CMAKE_SYSTEM_NAME}", "value": "Android"},
                        {"name": "${CmakeProperty.CMAKE_ANDROID_ARCH_ABI}", "value": "${Macro.ABI.ref}"},
                        {"name": "${CmakeProperty.CMAKE_BUILD_TYPE}", "value": "MyCustomBuildType"},
                        {"name": "${CmakeProperty.CMAKE_SYSTEM_VERSION}", "value": "${Macro.PLATFORM_SYSTEM_VERSION.ref}"},
                        {"name": "${CmakeProperty.CMAKE_EXPORT_COMPILE_COMMANDS}", "value": "ON"},
                        {"name": "${CmakeProperty.CMAKE_ANDROID_NDK}", "value": "${Macro.NDK_DIR.ref}"},
                        {"name": "${CmakeProperty.CMAKE_TOOLCHAIN_FILE}", "value": "${Macro.NDK_CMAKE_TOOLCHAIN.ref}"},
                        {"name": "${CmakeProperty.CMAKE_MAKE_PROGRAM}", "value": "${Macro.NINJA_EXE.ref}"},
                        {"name": "${CmakeProperty.CMAKE_C_FLAGS}", "value": "-DTEST_C_FLAG -DTEST_C_FLAG_2"},
                        {"name": "${CmakeProperty.CMAKE_CXX_FLAGS}", "value": "-DTEST_CPP_FLAG"},
                    ]
                }]
            }""".trimIndent())
        Mockito.doReturn(makefile).`when`(cmake).path
        projectRootDir.mkdirs()
        makefile.writeText("# written by ${BasicCmakeMock::class}")
    }


}