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

package com.android.tools.profgen

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HumanReadableProfileErrorsTests {
    @Test
    fun testIllegalRules() {
        assertThat(parseRule("HSPLA;->foo**")).isEqualTo(
            13 to unexpectedEnd('(')
        )
        assertThat(parseRule("HSPLA;bla")).isEqualTo(
             6 to unexpectedChar('-', 'b')
        )
        assertThat(parseRule("pack.age.Foo")).isEqualTo(
            0 to illegalTokenMessage('p')
        )
        assertThat(parseRule("HSPLA;->foo()LA;bla")).isEqualTo(
            16 to unexpectedTextAfterRule("bla")
        )
    }

    @Test
    fun testIncorrectFlags() {
        assertThat(parseRule("HSPLFoo;")).isEqualTo(
            0 to flagsForClassRuleMessage("HSP")
        )
        assertThat(parseRule("LFoo;->method()V")).isEqualTo(
            0 to emptyFlagsForMethodRuleMessage()
        )
    }

    @Test
    fun testErrorsInMultipleRules() {
        val errors = mutableListOf<String>()
        val diagnostics = Diagnostics { error -> errors.add(error) }
        val name = "incorrect-composer-hrp.txt"
        val hrp = HumanReadableProfile(testData(name), diagnostics)
        assertThat(hrp).isNull()
        assertThat(errors).containsExactly(
            "$name:1:47 error: ${
                illegalTokenMessage(';').withSnippet(
                    "HSPLandroidx/compose/runtime/ComposerImp;->foo;**", 46
                )
            }",
            "$name:3:64 error: ${
                unexpectedEnd('(').withSnippet(
                    "HSPLandroidx/compose/runtime/ComposerImpl;->startMovableGroup**", 63
                )
            }"
        )
    }

    @Test
    fun testWithSnippet() {
        assertThat("message".withSnippet("rule", 2)).isEqualTo(
            """
                message
                rule
                  ^
            """.trimIndent()
        )
    }
}

private fun parseRule(
    line: String
): Pair<Int, String> {
    var result : Pair<Int, String>? = null
    val onError: (Int, String) -> Unit = { index, error -> result = index to error }
    val rule = parseRule(line, onError, RuleFragmentParser(line.length))
    assertThat(rule).isNull()
    return result!!
}
