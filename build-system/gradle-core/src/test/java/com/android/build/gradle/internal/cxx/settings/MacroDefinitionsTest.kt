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

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MacroDefinitionsTest {
    @Test
    fun `macro lookup checks`() {
        assertThat(Macro.lookup("thisFile")).isEqualTo(Macro.ENV_THIS_FILE)
        assertThat(Macro.lookup("env.thisFile")).isEqualTo(Macro.ENV_THIS_FILE)
        assertThat(Macro.lookup("ndk.version")).isEqualTo(Macro.NDK_VERSION)
    }

    @Test
    fun `descriptions must end in period`() {
        Macro.values().forEach { macro->
            assertThat(macro.description)
                .endsWith(".")
        }
    }

    @Test
    fun `only allow forward slashes in example`() {
        Macro.values().forEach { macro->
            assertThat(macro.description)
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
}
