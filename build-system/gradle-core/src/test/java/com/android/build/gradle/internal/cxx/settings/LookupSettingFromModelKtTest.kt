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
import com.android.build.gradle.internal.cxx.model.BasicCmakeMock
import com.android.build.gradle.internal.cxx.model.createCxxAbiModel
import com.android.build.gradle.internal.cxx.model.createCxxVariantModel
import com.android.build.gradle.internal.cxx.model.tryCreateCxxModuleModel
import com.android.build.gradle.internal.cxx.settings.Token.*
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LookupSettingFromModelKtTest {

    fun convertWindowsAspectsToLinux(example : String) : String {
        val result = example.replace("\\", "/")
        return result.substringBeforeLast(".exe")
    }


    @Test
    fun `ensure all macros have have a corresponding lookup`() {
        BasicCmakeMock().let {
            // Walk all vals in the model and invoke them
            val module = tryCreateCxxModuleModel(it.global, it.cmakeFinder)!!
            val variant = createCxxVariantModel(
                module,
                it.baseVariantData)
            val abi = createCxxAbiModel(
                variant,
                Abi.X86_64,
                it.global,
                it.baseVariantData)

            assertThat(abi.resolveMacroValue(Macro.PLATFORM_SYSTEM_VERSION))
                .isEqualTo("19")

            Macro.values().forEach { macro ->
                val resolved = abi.resolveMacroValue(macro)
                assertThat(resolved).isNotEmpty()
            }
        }
    }

    @Test
    fun `ensure all macros example values are accurate`() {
        BasicCmakeMock().let {
            // Walk all vals in the model and invoke them
            val module = tryCreateCxxModuleModel(it.global, it.cmakeFinder)!!
            val variant = createCxxVariantModel(
                module,
                it.baseVariantData)
            val abi = createCxxAbiModel(
                variant,
                Abi.X86_64,
                it.global,
                it.baseVariantData)

            assertThat(abi.resolveMacroValue(Macro.PLATFORM_SYSTEM_VERSION))
                .isEqualTo("19")

            Macro.values().forEach { macro ->
                val resolved = abi.resolveMacroValue(macro)
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
                            val tokenMacro = Macro.lookup(token.macro)
                            assertThat(tokenMacro)
                                .named("${token.macro} in ${macro.example}")
                                .isNotNull()
                            example.append(abi.resolveMacroValue(tokenMacro!!))
                        }
                    }
                }

                assertThat(convertWindowsAspectsToLinux(resolved))
                    .named(macro.ref)
                    .isEqualTo(convertWindowsAspectsToLinux(example.toString()))
            }
        }
    }
}