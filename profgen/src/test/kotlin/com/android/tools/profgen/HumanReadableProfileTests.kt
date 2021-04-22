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
import java.io.ByteArrayInputStream
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HumanReadableProfileTests {
    @Test
    fun testLineMatchesItself() = assertMatchesItself(
        "Lcom/google/Foo;->method(II)I",
        "Lcom/google/Foo;->method-name-with-hyphens(II)I",
    )

    @Test
    fun testSinglePartWildcardInPackageSegment() = forLine("Lcom/*/Foo;->method(II)I") {
        assertMatches(
            "Lcom/anything/Foo;->method(II)I",
            "Lcom/can/Foo;->method(II)I",
            "Lcom/go/Foo;->method(II)I",
            "Lcom/here/Foo;->method(II)I",
        )

        assertDoesNotMatch(
            "Lcom/more/than/one/namespace/Foo;->method(II)I",
            "Lcom/something/Foo;->otherMethodName(II)I",
            "Lcom/something/Foo;->method(Ldifferent/Type;)I",
        )
    }

    @Test
    fun testMultipartWildcard() = forLine("Lcom/**/Foo;->method(II)I") {
        assertMatches("Lcom/anything/can/go/here/Foo;->method(II)I")
        assertDoesNotMatch(
            "Lcom/anything/can/go/here;->method(II)I",
            "Lcom/anything/can/go/here/Foo;->someOtherMethod(II)I",
            "Lcom/Foo;->method(II)I",
        )
    }

    @Test
    fun testExactClassMath() {
        val hrp = HumanReadableProfile("Lcom/anything/can/go/here;")
        assertThat(hrp.match("Lcom/anything/can/go/here;")).isEqualTo(MethodFlags.STARTUP)
        assertThat(hrp.match("LFoo;")).isEqualTo(0)
    }

    @Test
    fun testExactMethodMatch() {
        val hrp = HumanReadableProfile("HSLcom/anything/can/go/here;->method()I")
        val matchingMethod = parseDexMethod("Lcom/anything/can/go/here;->method()I")
        val nonMatchingMethod = parseDexMethod("Lcom/anything/can/go/here;->boo()I")
        assertThat(hrp.match(matchingMethod)).isEqualTo(MethodFlags.HOT or MethodFlags.STARTUP)
        assertThat(hrp.match(nonMatchingMethod)).isEqualTo(0)
    }

    fun assertMatchesItself(vararg lines: String) {
        for (line in lines) assertMatches(line, line)
    }

    internal fun forLine(hrpLine: String, test: LineTestScope.() -> Unit) = LineTestScope(hrpLine).test()

    fun assertMatches(hrpLine: String, vararg matches: String) {
        val line = parseRule(hrpLine)
        for (it in matches) {
            val method = parseDexMethod(it)
            assertTrue(
                line.matches(method),
                """Expected match.
                Line : $line
                Match: $it
                """.trimIndent()
            )
        }
    }
}

internal class LineTestScope(private val hrpLine: String) {
    private val line = parseRule(hrpLine)

    fun assertMatches(vararg lines: String) {
        for (it in lines) {
            val method = parseDexMethod(it)
            assertTrue(
                line.matches(method),
                """Expected match.
                Line : $hrpLine
                Match: $it
                """.trimIndent()
            )
        }
    }
    fun assertDoesNotMatch(vararg lines: String) {
        for (it in lines) {
            val method = parseDexMethod(it)
            assertFalse(
                line.matches(method),
                """Expected not to match.
                Line : $hrpLine
                Match: $it
                """.trimIndent()
            )
        }
    }
}

internal fun parseDexMethod(line: String): DexMethod {
    return parseRule(line).toDexMethod()
}

internal fun HumanReadableProfile(vararg strings: String) : HumanReadableProfile {
    val text = strings.joinToString("\n")
    return HumanReadableProfile(ByteArrayInputStream(text.toByteArray()).reader())
}
