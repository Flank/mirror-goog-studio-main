/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.ninja

import com.android.build.gradle.internal.cxx.RandomInstanceGenerator
import com.android.build.gradle.internal.cxx.ninja.NinjaUnescapeTokenType.LiteralType
import com.android.build.gradle.internal.cxx.ninja.NinjaUnescapeTokenType.EscapedColonType
import com.android.build.gradle.internal.cxx.ninja.NinjaUnescapeTokenType.EscapedDollarType
import com.android.build.gradle.internal.cxx.ninja.NinjaUnescapeTokenType.EscapedSpaceType
import com.android.build.gradle.internal.cxx.ninja.NinjaUnescapeTokenType.VariableType
import com.android.build.gradle.internal.cxx.ninja.NinjaUnescapeTokenType.VariableWithCurliesType
import com.android.build.gradle.internal.cxx.ninja.NinjaUnescapeTokenType.CommentType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.StringReader

class StreamUnescapedNinjaTest {

    private fun check(input : String, expected : String? = null) {
        val sb = StringBuilder()
        StringReader(input).streamUnescapedNinja { type, value ->
            when (type) {
                LiteralType -> sb.append(value)
                EscapedColonType -> sb.append("[esc:colon]")
                EscapedDollarType -> sb.append("[esc:dollar]")
                EscapedSpaceType -> sb.append("[esc:space]")
                VariableType -> sb.append("[$value]")
                VariableWithCurliesType -> sb.append("[$value]")
                CommentType -> sb.append("/*$value*/")
                else -> error("$type")
            }
        }
        if (expected != null) {
            assertThat(sb.toString()).isEqualTo(expected)
        }
    }

    @Test
    fun basics() {
        check("", "")
        check("abc", "abc")
        check("\$abc", "[abc]")
        check("\${abc}", "[abc]")
        check("\$ ", "[esc:space]")
        check("\$:", "[esc:colon]")
        check("\$\$", "[esc:dollar]")
        check("ab\$c", "ab[c]")
        check("ab\${c}", "ab[c]")
        check("ab\${c}d", "ab[c]d")
        check("\$a b", "[a] b")
    }

    @Test
    fun `fuzz repro with escaped space`() {
        check("abc =\$ lmn def", "abc =[esc:space]lmn def")
    }

    @Test
    fun `hash inside command`() {
        check("""
                rule my_rule
                    command = ${'$'}
                            if grep -v '^#' file.txt | a${'$'}
                               b c
            """.trimIndent(),
            """
                rule my_rule
                    command = if grep -v '^#' file.txt | ab c
            """.trimIndent(),
        )
    }

    @Test
    fun `indented comment after rule`() {
        check("rule cat\n" +
                "  #command = a",
            """
              rule cat
                /*command = a*/
            """.trimIndent())
    }

    @Test
    fun `variables may be enclosed in curlies`() {
        check("\${abc}", "[abc]")
    }

    @Test
    fun `variables may not contain EOL`() {
        check("\$a\n\$b", "[a]\n[b]")
    }

    @Test
    fun `hash after non-whitespace is not comment`() {
        check("my line # non-comment", "my line # non-comment")
        check("my line # non-comment\nline2", "my line # non-comment\nline2")
    }

    @Test
    fun `build with variable input`() {
        check("build \$x: foo y\n", "build [x]: foo y\n")
    }

    @Test
    fun `dollar variable then special character`() {
        check("\$a\$b", "[a][b]")
        check("\$a\$", "[a]")
        check("\$a:", "[a]:")
        check("\$a#", "[a]")
        check("\$a ", "[a] ")
    }

    @Test
    fun `EOL can be escaped`() {
        check("\$\r", "")
        check("\$\r\n", "")
        check("\r", "\r")
        check("\r\n", "\r\n")
    }

    @Test
    fun `colon inside`() {
        check("build build.ninja: RERUN_CMAKE C\$:/abc", "build build.ninja: RERUN_CMAKE C[esc:colon]/abc")
    }

    /**
     * https://ninja-build.org/manual.html
     * "Whitespace at the beginning of a line after a line continuation is also stripped."
     */
    @Test
    fun `whitespace after line continuation`() {
        check("""
            # Comment
            Non-$
              comment $
                indented
        """.trimIndent(),
            "/* Comment*/\nNon-comment indented")
    }

    @Test
    fun `fuzz crash repros`() {
        // These are odd cases that shouldn't happen but nevertheless shouldn't crash if they do.
        check("\$")
        check("\${")
        check("\${a")
    }

    @Test
    fun `build with spaces in file names`() {
        check("build a$ b|c$ d:ru$ le e$ f|g$ h||i$ j", "build a[esc:space]b|c[esc:space]d:ru[esc:space]le e[esc:space]f|g[esc:space]h||i[esc:space]j")
    }

    @Test
    fun fuzz() {
        RandomInstanceGenerator().strings(10000).forEach { text ->
            try {
                StringReader(text).streamUnescapedNinja { _, _  -> }
            } catch (e : Throwable) {
                println("\'$text\'")
                throw e
            }
        }
    }
}
