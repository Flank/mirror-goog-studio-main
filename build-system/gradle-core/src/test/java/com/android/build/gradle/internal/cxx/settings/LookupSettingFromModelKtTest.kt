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
import com.android.build.gradle.internal.cxx.configure.createInitialCxxModel
import com.android.build.gradle.internal.cxx.model.BasicCmakeMock
import com.android.build.gradle.internal.cxx.model.BasicNdkBuildMock
import com.android.build.gradle.internal.cxx.model.createCxxAbiModel
import com.android.build.gradle.internal.cxx.model.createCxxModuleModel
import com.android.build.gradle.internal.cxx.model.createCxxVariantModel
import com.android.build.gradle.internal.cxx.settings.Token.LiteralToken
import com.android.build.gradle.internal.cxx.settings.Token.MacroToken
import com.android.build.gradle.tasks.NativeBuildSystem
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LookupSettingFromModelKtTest {

    private fun convertWindowsAspectsToLinux(example : String) : String {
        val result = example
                .replace("\\", "/")
                .replace("darwin", "linux")
                .replace("windows", "linux")
        return result.substringBeforeLast(".exe")
    }

    @Test
    fun `ensure all CMake macros have have a corresponding lookup`() {
        BasicCmakeMock().let {
            // Walk all vals in the model and invoke them
            val module = createCxxModuleModel(
                it.sdkComponents,
                it.androidLocationProvider,
                it.configurationParameters,
                it.cmakeFinder)
            val variant = createCxxVariantModel(
                it.configurationParameters,
                module)
            val abi = createCxxAbiModel(
                it.sdkComponents,
                it.configurationParameters,
                variant,
                Abi.X86_64)

            assertThat(abi.resolveMacroValue(Macro.NDK_PLATFORM_SYSTEM_VERSION))
                .isEqualTo("19")

            Macro.values()
                    .forEach { macro ->
                abi.resolveMacroValue(macro)
            }
        }
    }

    @Test
    fun `ensure all ndkBuild macros have have a corresponding lookup`() {
        BasicCmakeMock().apply {
            val parameters = configurationParameters.copy(
                    buildSystem = NativeBuildSystem.NDK_BUILD)
            val abi = createInitialCxxModel(
                    sdkComponents,
                    androidLocationProvider,
                    listOf(parameters))
                    .single { abi -> abi.abi == Abi.X86_64 }

            Macro.values()
                    .forEach { macro ->
                        abi.resolveMacroValue(macro)
                    }
        }
    }

    @Test
    fun `ensure all CMake macros example values are accurate`() {
        BasicCmakeMock().let {
            val configurationParameters = it.configurationParameters.copy(
                    nativeVariantConfig = it.configurationParameters.nativeVariantConfig.copy(
                            arguments = listOf("-DANDROID_STL=c++_shared")
                    )
            )
            val allAbis = createInitialCxxModel(
                it.sdkComponents, it.androidLocationProvider, listOf(configurationParameters)
            )
            val abi = allAbis.single { abi -> abi.abi == Abi.X86_64 }

            Macro.values().forEach { macro ->
                val resolved = abi.resolveMacroValue(macro)
                        .replace(Macro.NDK_ABI.ref, abi.abi.tag)
                val example = StringBuilder()
                tokenizeMacroString(macro.example) { token ->
                    when(token) {
                        is LiteralToken -> {
                            val expanded = token.literal
                                .replace("\$HOME", it.home.path)
                                .replace("\$PROJECTS", it.projects.path)
                            example.append(expanded)
                        }
                        is MacroToken -> {
                            val tokenMacro = Macro.lookup(token.macro)!!
                            assertThat(tokenMacro)
                                .named("${token.macro} in ${macro.example}")
                                .isNotNull()
                            example.append(abi.resolveMacroValue(tokenMacro))
                        }
                    }
                }
                if (macro != Macro.NDK_CONFIGURATION_HASH && macro != Macro.NDK_FULL_CONFIGURATION_HASH) {
                    assertThat(convertWindowsAspectsToLinux(resolved))
                            .named(macro.ref)
                            .isEqualTo(convertWindowsAspectsToLinux(example.toString()))
                }
            }
        }
    }

    @Test
    fun `ensure all ndkBuild macros example values are accurate`() {
        BasicNdkBuildMock().let {
            val allAbis =
                    createInitialCxxModel(
                        it.sdkComponents,
                        it.androidLocationProvider,
                        listOf(it.configurationParameters)
                    )
            val abi = allAbis.single { abi -> abi.abi == Abi.X86_64 }

            Macro.values().forEach { macro ->
                val resolved = abi.resolveMacroValue(macro)
                        .replace(Macro.NDK_ABI.ref, abi.abi.tag)
                val example = StringBuilder()
                assertThat(macro.ndkBuildExample)
                        .named("ndk-build and CMake examples differ needlessly: ${macro.ref}")
                        .isNotEqualTo(macro.example)
                tokenizeMacroString(macro.ndkBuildExample ?: macro.example) { token ->
                    when(token) {
                        is LiteralToken -> {
                            val expanded = token.literal
                                    .replace("\$HOME", it.home.path)
                                    .replace("\$PROJECTS", it.projects.path)
                            example.append(expanded)
                        }
                        is MacroToken -> {
                            val tokenMacro = Macro.lookup(token.macro)!!
                            assertThat(tokenMacro)
                                    .named("${token.macro} in ${macro.example}")
                                    .isNotNull()
                            example.append(abi.resolveMacroValue(tokenMacro))
                        }
                    }
                }
                if (macro != Macro.NDK_CONFIGURATION_HASH
                        && macro != Macro.NDK_FULL_CONFIGURATION_HASH) {
                    val actual = convertWindowsAspectsToLinux(resolved)
                    val expected = example.toString()
                    assertThat(actual)
                            .named(macro.name)
                            .isEqualTo(convertWindowsAspectsToLinux(expected))
                }
            }
        }
    }
}
