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
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MacroDefinitionsTest {
    @Test
    fun `macro lookup checks`() {
        Truth.assertThat(Macro.lookup("thisFile")).isEqualTo(Macro.ENV_THIS_FILE)
        Truth.assertThat(Macro.lookup("env.thisFile")).isEqualTo(Macro.ENV_THIS_FILE)
        Truth.assertThat(Macro.lookup("ndk.version")).isEqualTo(Macro.NDK_VERSION)
    }

    @Test
    fun `descriptions must end in period`() {
        Macro.values().forEach { macro->
            Truth.assertThat(macro.description)
                .endsWith(".")
        }
    }

    @Test
    fun `only allow forward slashes in example`() {
        Macro.values().forEach { macro->
            Truth.assertThat(macro.description)
                .doesNotContain("\\")
        }
    }

    @Test
    fun `ensure all qualified names are distinct`() {
        val seen = mutableSetOf<String>()
        Macro.values().map { macro->
            if (seen.contains(macro.tag)) {
                throw RuntimeException("Tag ${macro.qualifiedName} seen twice")
            }
            seen += macro.qualifiedName
        }
    }

    @Test
    fun `ensure kotlin enum names match environment names`() {
        Macro.values().map { macro->
            println("$macro=${macro.qualifiedName}")

            val sb = StringBuilder()
            var lastWasDigit = false
            for (c in macro.qualifiedName) {
                when {
                    c.isDigit() -> {
                        if (!lastWasDigit) {
                            sb.append("_")
                        }
                        sb.append(c)
                    }
                    c.isUpperCase() -> sb.append("_$c")
                    c == '.' -> sb.append("_")
                    else -> sb.append(c.toUpperCase())
                }
                lastWasDigit = c.isDigit()
            }
            assertThat(macro.toString()).isEqualTo(sb.toString())
        }
    }

    @Test
    fun `ensure examples are accurate`() {
        BasicCmakeMock().let {
            // Walk all vals in the model and invoke them
            val module = tryCreateCxxModuleModel(
                it.global, it.cmakeFinder
            )!!
            val variant = createCxxVariantModel(
                module,
                it.variantScope
            )
            val abi = createCxxAbiModel(
                variant,
                Abi.X86_64,
                it.global,
                it.baseVariantData
            )
            Macro.values()
                .toList()
                .sortedBy { macro -> macro.qualifiedName }
                .forEach { macro ->
                    val resolved = abi.resolveMacroValue(macro)

                    // Every macro must be resolvable
                    Truth.assertThat(resolved).named(macro.ref).isNotEmpty()

                    // Example string, when expanded, must equal the true value from the model
                    val example = StringBuilder()
                    tokenizeMacroString(macro.example) { token ->
                        when (token) {
                            is Token.LiteralToken -> {
                                val expanded = token.literal
                                    .replace("\$HOME", it.home.path)
                                    .replace("\$PROJECTS", it.projects.path)
                                example.append(expanded)
                            }
                            is Token.MacroToken -> {
                                val tokenMacro = Macro.lookup(token.macro)
                                Truth.assertThat(tokenMacro)
                                    .named("${token.macro} in ${macro.example}")
                                    .isNotNull()
                                example.append(abi.resolveMacroValue(tokenMacro!!))
                            }
                        }
                    }

                    Truth.assertThat(resolved.replace('\\', '/').replace(".exe", ""))
                        .named(macro.ref)
                        .isEqualTo(example.toString().replace('\\', '/').replace(".exe", ""))
                }
        }
    }
}
