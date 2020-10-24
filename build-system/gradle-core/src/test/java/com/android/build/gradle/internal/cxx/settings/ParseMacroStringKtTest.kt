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

import com.android.build.gradle.internal.cxx.RandomInstanceGenerator
import com.android.build.gradle.internal.cxx.model.BasicCmakeMock
import com.google.common.truth.Truth.*
import com.android.build.gradle.internal.cxx.settings.Token.*
import org.junit.Test


class ParseMacroStringKtTest {

    @Test
    fun `simple macro`() {
        val tokens = mutableListOf<Token>()
        tokenizeMacroString("\${macro}") { tokens += it }
        assertThat(tokens).hasSize(1)
        assertThat(tokens[0] is MacroToken).isTrue()
        assertThat(tokens[0].toString()).isEqualTo("macro")
    }

    @Test
    fun `simple literal`() {
        val tokens = mutableListOf<Token>()
        tokenizeMacroString("literal") { tokens += it }
        assertThat(tokens).hasSize(1)
        assertThat(tokens[0] is LiteralToken).isTrue()
        assertThat(tokens[0].toString()).isEqualTo("literal")
    }

    @Test
    fun `literal macro`() {
        val tokens = mutableListOf<Token>()
        tokenizeMacroString("literal\${macro}") { tokens += it }
        assertThat(tokens).hasSize(2)
        assertThat(tokens[0] is LiteralToken).isTrue()
        assertThat(tokens[0].toString()).isEqualTo("literal")
        assertThat(tokens[1] is MacroToken).isTrue()
        assertThat(tokens[1].toString()).isEqualTo("macro")

    }

    @Test
    fun `macro literal`() {
        val tokens = mutableListOf<Token>()
        tokenizeMacroString("\${macro}literal") { tokens += it }
        assertThat(tokens).hasSize(2)
        assertThat(tokens[0] is MacroToken).isTrue()
        assertThat(tokens[0].toString()).isEqualTo("macro")
        assertThat(tokens[1] is LiteralToken).isTrue()
        assertThat(tokens[1].toString()).isEqualTo("literal")
    }

    @Test
    fun `dollar dollar`() {
        val tokens = mutableListOf<Token>()
        tokenizeMacroString("\$\$") { tokens += it }
        assertThat(tokens).hasSize(1)
        assertThat(tokens[0] is LiteralToken).isTrue()
        assertThat(tokens[0].toString()).isEqualTo("\$\$")
    }

    @Test
    fun `dollar x`() {
        val tokens = mutableListOf<Token>()
        tokenizeMacroString("\$x") { tokens += it }
        assertThat(tokens).hasSize(1)
        assertThat(tokens[0] is LiteralToken).isTrue()
        assertThat(tokens[0].toString()).isEqualTo("\$x")
    }

    @Test
    fun `dollar curly x`() {
        val tokens = mutableListOf<Token>()
        tokenizeMacroString("\${x") { tokens += it }
        assertThat(tokens).hasSize(1)
        assertThat(tokens[0] is LiteralToken).isTrue()
        assertThat(tokens[0].toString()).isEqualTo("\${x")
    }

    @Test
    fun `dollar (found by fuzz)`() {
        val text = "$"
        val reconstructed = roundTrip(text)
        assertThat(reconstructed.toString()).isEqualTo(text)
    }

    @Test
    fun `trailing dollar (found by fuzz)`() {
        val text = "trailing$"
        val reconstructed = roundTrip(text)
        assertThat(reconstructed.toString()).isEqualTo(text)
    }

    @Test
    fun `trailing dollar curly (found by fuzz)`() {
        val text = "trailing\${"
        val reconstructed = roundTrip(text)
        assertThat(reconstructed.toString()).isEqualTo(text)
    }

    @Test
    fun `trailing dollar curly curly (found by fuzz)`() {
        val text = "trailing\${}"
        val reconstructed = roundTrip(text)
        assertThat(reconstructed.toString()).isEqualTo(text)
    }

    @Test
    fun fuzz() {
        RandomInstanceGenerator()
            .strings()
            .forEach { text ->
                val reconstructed = roundTrip(text)
                assertThat(reconstructed.toString())
                    .named("Parsing <<$text>>")
                    .isEqualTo(text)
            }
    }

    private fun roundTrip(text: String): StringBuilder {
        val reconstructed = StringBuilder()
        tokenizeMacroString(text) {
            when (it) {
                is LiteralToken -> {
                    assertThat(it.literal).isNotEmpty()
                    reconstructed.append(it.literal)
                }
                is MacroToken -> reconstructed.append("\${${it.macro}}")
            }
        }
        return reconstructed
    }
}
