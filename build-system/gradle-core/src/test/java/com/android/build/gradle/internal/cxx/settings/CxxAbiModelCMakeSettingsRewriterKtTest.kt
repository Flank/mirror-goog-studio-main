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

import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.RandomInstanceGenerator
import com.android.build.gradle.internal.cxx.configure.CmakeProperty
import com.android.build.gradle.internal.cxx.configure.getBuildRootFolder
import com.android.build.gradle.internal.cxx.configure.getCmakeProperty
import com.android.build.gradle.internal.cxx.configure.getGenerator
import com.android.build.gradle.internal.cxx.model.BasicCmakeMock
import com.android.build.gradle.internal.cxx.model.CmakeSettingsMock
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.CxxVariantModel
import com.android.build.gradle.internal.cxx.model.DIFFERENT_MOCK_CMAKE_SETTINGS_CONFIGURATION
import com.android.build.gradle.internal.cxx.model.buildCommandFile
import com.android.build.gradle.internal.cxx.model.buildOutputFile
import com.android.build.gradle.internal.cxx.model.createCxxAbiModel
import com.android.build.gradle.internal.cxx.model.createCxxVariantModel
import com.android.build.gradle.internal.cxx.model.jsonGenerationLoggingRecordFile
import com.android.build.gradle.internal.cxx.model.modelOutputFile
import com.android.build.gradle.internal.cxx.model.soFolder
import com.android.build.gradle.internal.cxx.model.toJsonString
import com.android.build.gradle.internal.cxx.settings.Macro.*
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat

import org.junit.Test
import org.mockito.Mockito
import java.io.File

class CxxAbiModelCMakeSettingsRewriterKtTest {

    @Test
    fun `ensure that Android Gradle Plugin per-abi file names are invariant`() {
        CmakeSettingsMock().apply {
            RandomInstanceGenerator().cmakeSettingsJsons().forEach { settingsJson ->
                val cmakeSettingsFile = File(abi.resolveMacroValue(ENV_THIS_FILE))
                cmakeSettingsFile.writeText(settingsJson)
                abi.toJsonString() // Force lazy fields to evaluate
                val rewritten = abi.rewriteCxxAbiModelWithCMakeSettings()
                rewritten.toJsonString() // Force lazy fields to evaluate

                // All of these files should not change during rewrite because they need to
                // be in a predictable location that can't be altered by CMakeSettings.json.
                assertThat(abi.jsonGenerationLoggingRecordFile).isEqualTo(rewritten.jsonGenerationLoggingRecordFile)
                assertThat(abi.modelOutputFile).isEqualTo(rewritten.modelOutputFile)
                assertThat(abi.buildCommandFile).isEqualTo(rewritten.buildCommandFile)
                assertThat(abi.buildOutputFile).isEqualTo(rewritten.buildOutputFile)
                assertThat(abi.cmake!!.cmakeServerLogFile).isEqualTo(rewritten.cmake!!.cmakeServerLogFile)
            }
        }
    }

    @Test
    fun `ensure traditional environment rewrite above ABI is nop`() {
        BasicCmakeMock().apply {
            val rewritten = abi.rewriteCxxAbiModelWithCMakeSettings()
            assertThat(abi.variant.toJsonString()).isEqualTo(rewritten.variant.toJsonString())
        }
    }

    @Test
    fun `check rewrite with CMakeSettings json`() {
        CmakeSettingsMock().apply {
            val variant = object : CxxVariantModel by variant {
                override val cmakeSettingsConfiguration = DIFFERENT_MOCK_CMAKE_SETTINGS_CONFIGURATION
            }
            val abi = createCxxAbiModel(
                variant,
                Abi.X86,
                global,
                baseVariantData)
            val rewritten = abi.rewriteCxxAbiModelWithCMakeSettings()
            assertThat(rewritten.cmake!!.effectiveConfiguration.generator).isEqualTo("some other generator")
            assertThat(rewritten.cxxBuildFolder.path).contains("some other build root folder")
            assertThat(rewritten.variant.module.cmake!!.cmakeExe.path
                .replace('\\', '/')).isEqualTo("my/path/to/cmake")
            assertThat(rewritten.variant.module.cmakeToolchainFile.path
                .replace('\\', '/')).isEqualTo("my/path/to/toolchain")
            assertThat(rewritten.getBuildCommandArguments()).isEqualTo("-j 100")
        }
    }

    @Test
    fun `basic check`() {
        BasicCmakeMock().apply {
            val variables = abi
                .rewriteCxxAbiModelWithCMakeSettings()
                .getFinalCmakeCommandLineArguments()
            println(variables.joinToString("\n") { it.sourceArgument })
            assertThat(variables.getGenerator()).isEqualTo("Ninja")
            assertThat(variables.getCmakeProperty(CmakeProperty.CMAKE_LIBRARY_OUTPUT_DIRECTORY)).isEqualTo(abi.soFolder.absolutePath)
            assertThat(variables.getCmakeProperty(CmakeProperty.CMAKE_CXX_FLAGS)).isEqualTo("-DCPP_FLAG_DEFINED")
            assertThat(variables.getCmakeProperty(CmakeProperty.CMAKE_BUILD_TYPE)).isEqualTo("Debug")
        }
    }

    @Test
    fun `alternate check`() {
        CmakeSettingsMock().apply {
            val variant = object : CxxVariantModel by variant {
                override val cmakeSettingsConfiguration = DIFFERENT_MOCK_CMAKE_SETTINGS_CONFIGURATION
            }
            val abi = createCxxAbiModel(
                variant,
                Abi.X86,
                global,
                baseVariantData).rewriteCxxAbiModelWithCMakeSettings()
            val variables = abi.getFinalCmakeCommandLineArguments()
            println(variables.joinToString("\n") { it.sourceArgument })
            assertThat(variables.getGenerator()).isEqualTo("some other generator")
            assertThat(variables.getCmakeProperty(CmakeProperty.CMAKE_LIBRARY_OUTPUT_DIRECTORY)?.replace('\\', '/'))
                .endsWith("MyProject/Source/Android/build/android/lib/Debug/x86")
            assertThat(variables.getCmakeProperty(CmakeProperty.CMAKE_CXX_FLAGS)).isEqualTo("-DTEST_CPP_FLAG")
            assertThat(variables.getCmakeProperty(CmakeProperty.CMAKE_BUILD_TYPE)).isEqualTo("MyCustomBuildType")
        }
    }

    @Test
    fun `map CMAKE_BUILD_TYPE to MinSizeRel`() {
        CmakeSettingsMock().apply {
            val variant = object : CxxVariantModel by variant {
                override val variantName = "myMinSizeRel" // Should cause CMAKE_BUILD_TYPE to be MinSizeRel
            }
            val abi = createCxxAbiModel(
                variant,
                Abi.X86,
                global,
                baseVariantData).rewriteCxxAbiModelWithCMakeSettings()
            val variables = abi.getFinalCmakeCommandLineArguments()
            println(variables.joinToString("\n") { it.sourceArgument })
            assertThat(variables.getCmakeProperty(CmakeProperty.CMAKE_BUILD_TYPE)).isEqualTo("MinSizeRel")
        }
    }

    @Test
    fun `user build args take precedence over default configuration`() {
        CmakeSettingsMock().apply {
            val variant = object : CxxVariantModel by variant {
                override val buildSystemArgumentList =
                    listOf("-GPrecedenceCheckingGenerator",
                        "-D${CmakeProperty.CMAKE_BUILD_TYPE}=PrecedenceCheckingBuildType",
                        "-D${CmakeProperty.CMAKE_TOOLCHAIN_FILE}=PrecedenceCheckingToolchainFile")
            }
            val abi = createCxxAbiModel(
                variant,
                Abi.X86,
                global,
                baseVariantData).rewriteCxxAbiModelWithCMakeSettings()
            val variables = abi.getFinalCmakeCommandLineArguments()
            println(variables.joinToString("\n") { it.sourceArgument })
            assertThat(variables.getCmakeProperty(CmakeProperty.CMAKE_BUILD_TYPE)).isEqualTo("PrecedenceCheckingBuildType")
            assertThat(variables.getGenerator()).isEqualTo("PrecedenceCheckingGenerator")
            assertThat(variables.getCmakeProperty(CmakeProperty.CMAKE_TOOLCHAIN_FILE)).isEqualTo("PrecedenceCheckingToolchainFile")
        }
    }

    @Test
    fun `ABI does not contribute to hash`() {
        val (abi1, abi2) = abisOf(Abi.X86, Abi.X86_64)

        val commands1 = abi1.getFinalCmakeCommandLineArguments()
        val commands2 = abi2.getFinalCmakeCommandLineArguments()

        assertThat(commands1.getBuildRootFolder()).isNotNull()
        assertThat(commands2.getBuildRootFolder()).isNotNull()

        assertThat(File(commands1.getBuildRootFolder())).isNotEqualTo(File(commands2.getBuildRootFolder()))
        assertThat(File(commands1.getBuildRootFolder()).parentFile).isEqualTo(File(commands2.getBuildRootFolder()).parentFile)
    }

    @Test
    fun `configuration type build name does contribute to hash`() {
        val (abi1, abi2) = abisOf { mock, abi ->
            when(abi) {
                1 -> Mockito.doReturn("debug").`when`(mock.baseVariantData).name
                2 -> Mockito.doReturn("release").`when`(mock.baseVariantData).name
            }
        }

        val commands1 = abi1.getFinalCmakeCommandLineArguments()
        val commands2 = abi2.getFinalCmakeCommandLineArguments()
        val buildRoot1 = commands1.getBuildRootFolder()
        val buildRoot2 = commands2.getBuildRootFolder()

        assertThat(buildRoot1).isNotNull()
        assertThat(buildRoot2).isNotNull()

        assertThat(buildRoot1).isNotEqualTo(buildRoot2)
        assertThat(File(buildRoot1).parentFile).isNotEqualTo(File(buildRoot2).parentFile)
    }

    @Test
    fun `build settings macros are expanded`() {
        CmakeSettingsMock().apply {
            val buildSettingsJson = FileUtils.join(allPlatformsProjectRootDir, "BuildSettings.json")
            buildSettingsJson.writeText(
                """
                {
                    "environmentVariables": [
                        {
                            "name": "NDK_ABI",
                            "value": "${'$'}{ndk.abi}"
                        },
                        {
                            "name": "NDK_DIR",
                            "value": "${'$'}{ndk.dir}"
                        }
                    ]
                }
                """.trimIndent()
            )

            val rewritten = abi.rewriteCxxAbiModelWithCMakeSettings()

            assertThat(abi.buildSettings.environmentVariables).isEqualTo(
                listOf(
                    EnvironmentVariable("NDK_ABI", "\${ndk.abi}"),
                    EnvironmentVariable("NDK_DIR", "\${ndk.dir}")
                )
            )

            assertThat(rewritten.buildSettings.environmentVariables).isEqualTo(
                listOf(
                    EnvironmentVariable("NDK_ABI", abi.abi.tag),
                    EnvironmentVariable("NDK_DIR", abi.variant.module.ndkFolder.path)
                )
            )
        }
    }

    private fun abisOf(
        abi1 : Abi = Abi.X86,
        abi2 : Abi = Abi.X86,
        setup : (CmakeSettingsMock, Int) -> Unit = { _, _ -> }
    ) : Pair<CxxAbiModel, CxxAbiModel> {
        CmakeSettingsMock().apply {
            val moduleFolder = mockModule("app")
            Mockito.doReturn(FileUtils.join(moduleFolder, "CMakeLists.txt")).`when`(cmake).path
            val settings = FileUtils.join(moduleFolder, "CMakeSettings.json")
            settings.parentFile.mkdirs()
            settings.writeText(
                """{
                "configurations": [{
                    "name": "android-gradle-plugin-predetermined-name",
                    "description": "Configuration generated by Android Gradle Plugin",
                    "inheritEnvironments": ["ndk"],
                    "buildRoot": "${NDK_PROJECT_DIR.ref}/.cxx/cmake/build/${NDK_CONFIGURATION_HASH.ref}/${NDK_ABI.ref}",
                    "cmakeCommandArgs": "-DFULL_HASH=${NDK_FULL_CONFIGURATION_HASH.ref}",
                    "variables": [
                        {"name": "${CmakeProperty.CMAKE_LIBRARY_OUTPUT_DIRECTORY}",
                        "value": "${NDK_PROJECT_DIR.ref}/.cxx/cmake/lib/${NDK_CONFIGURATION_HASH.ref}/${NDK_ABI.ref}"}
                    ]
                }]
                }""".trimIndent()
            )
            setup(this, 1)

            val variant1 = createCxxVariantModel(module, variantScope)
            val result1 = createCxxAbiModel(variant1, abi1, global, baseVariantData).rewriteCxxAbiModelWithCMakeSettings()
            result1.toJsonString() // Force all lazy values

            setup(this, 2)
            val variant2 = createCxxVariantModel(module, variantScope)
            val result2 = createCxxAbiModel(variant2, abi2, global, baseVariantData).rewriteCxxAbiModelWithCMakeSettings()
            result2.toJsonString() // Force all lazy values

            return Pair(result1, result2)
        }
    }
}
