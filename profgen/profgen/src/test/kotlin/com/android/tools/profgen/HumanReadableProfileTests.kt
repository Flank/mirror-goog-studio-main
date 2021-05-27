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

import com.android.tools.profgen.MethodFlags.ALL
import com.android.tools.profgen.MethodFlags.POST_STARTUP
import com.android.tools.profgen.MethodFlags.STARTUP
import com.android.tools.profgen.MethodFlags.HOT
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class HumanReadableProfileTests {
    @Test
    fun testLineMatchesItself() = assertMatchesItself(
        "Lcom/google/Foo;->method(II)I",
        "Lcom/google/Foo;->method-name-with-hyphens(II)I",
    )

    @Test
    fun testSinglePartWildcardInPackageSegment() = forLine("HLcom/*/Foo;->method(II)I") {
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
    fun testMultipartWildcard() = forLine("HLcom/**/Foo;->method(II)I") {
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
        assertThat(hrp.match("Lcom/anything/can/go/here;")).isEqualTo(STARTUP)
        assertThat(hrp.match("LFoo;")).isEqualTo(0)
    }

    @Test
    fun testFuzzyClassMatch() {
        val hrp = HumanReadableProfile("Lcom/**")
        assertThat(hrp.match("Lcom/anything/can/go/here;")).isEqualTo(STARTUP)
        assertThat(hrp.match("LFoo;")).isEqualTo(0)
    }

    @Test
    fun testComments() {
        val hrp = HumanReadableProfile(
                "# Test Comment",
                "# Test Comment",
                "Lcom/anything/can/go/here;",
                "# Test Comment",
                "# Test Comment",
        )
        assertThat(hrp.match("Lcom/anything/can/go/here;")).isEqualTo(MethodFlags.STARTUP)
        assertThat(hrp.match("LFoo;")).isEqualTo(0)
    }

    @Test
    fun testWhiteSpaceAndBlankLines() {
        with(HumanReadableProfile(
                "",
                "   ",
                "\t   ",
                "Lcom/anything/can/go/here;",
                "Lcom/allow/whitespace/after/line;  ",
                "Lcom/allow/tab/after/line;\t  ",
                "HSLcom/allow/whitespace/after/line;->method()I  ",
                "HSLcom/allow/tab/after/line;->method()I\t  ",
        )) {
            assertThat(match("Lcom/anything/can/go/here;")).isEqualTo(STARTUP)
            assertThat(match("Lcom/allow/whitespace/after/line;")).isEqualTo(STARTUP)
            assertThat(match("Lcom/allow/tab/after/line;")).isEqualTo(STARTUP)
            assertMethodFlags("HSLcom/allow/whitespace/after/line;->method()I", HOT or STARTUP)
            assertMethodFlags("HSLcom/allow/tab/after/line;->method()I", HOT or STARTUP)
            assertThat(match("LFoo;")).isEqualTo(0)
        }
    }

    @Test
    fun testExactMethodMatch() {
        with(HumanReadableProfile("HSLcom/anything/can/go/here;->method()I")) {
            assertMethodFlags("Lcom/anything/can/go/here;->method()I", HOT or STARTUP)
            assertMethodFlags("Lcom/anything/can/go/here;->boo()I", 0)
        }
    }

    @Test
    fun testAnyMethodMatch() {
        with(HumanReadableProfile("PLcom/anything/can/go/here;->**(**)**")) {
            assertMethodFlags("Lcom/anything/can/go/here;->method()I", POST_STARTUP)
            assertMethodFlags("Lcom/anything/can/go/here;-><init>()V", POST_STARTUP)
            assertMethodFlags("Lcom/anything/can/go/here;-><clinit>()V", POST_STARTUP)
            assertMethodFlags("Lcom/anything/can/go/here;->m([Ljava/lang/Object;)V", POST_STARTUP)
            assertMethodFlags("Lcom/anything/can/go/here;->method(LFoo$1;)I", POST_STARTUP)
        }
    }

    @Test
    fun testMergedRules() {
        val hrp = HumanReadableProfile(
            "HLa/B;->**(**)**",
            "SLa/B;->foo*(II)**",
            "PLa/B;->fooExact(II)V",
            "PLa/*;->foo*Inexact(II)Z",
        )
        hrp.assertMethodFlags("La/B;->fooExact(II)V", ALL)
        hrp.assertMethodFlags("La/B;->foo(II)Z", HOT or STARTUP)
    }

    @Test
    fun testMatchFuzzyMethodParams() {
        with(HumanReadableProfile("SLcom/anything/can/go/here;->method([I*[J)**")) {
            assertMethodFlags("Lcom/anything/can/go/here;->method()I", 0)
            assertMethodFlags("Lcom/anything/can/go/here;->method([II[J)I", STARTUP)
        }
    }

    fun assertMatchesItself(vararg lines: String) {
        // to create a correct rule from a method a flag is added to the beginning
        for (line in lines) assertMatches("H$line", line)
    }

    internal fun forLine(hrpLine: String, test: LineTestScope.() -> Unit) = LineTestScope(hrpLine).test()

    fun assertMatches(hrpLine: String, vararg matches: String) {
        val line = parseRule(hrpLine) ?: error("Line didn't parse successfully")
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

internal fun HumanReadableProfile.assertMethodFlags(method: String, expectedFlags: Int) =
    assertThat(match(parseDexMethod(method))).isEqualTo(expectedFlags)
internal fun HumanReadableProfile.assertStartupClass(type: String) =
        assertThat(match(type)).isEqualTo(STARTUP)

internal class LineTestScope(private val hrpLine: String) {
    private val line = parseRule(hrpLine)

    fun assertMatches(vararg lines: String) {
        for (it in lines) {
            val method = parseDexMethod(it)
            assertTrue(
                line!!.matches(method),
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
                line!!.matches(method),
                """Expected not to match.
                Line : $hrpLine
                Match: $it
                """.trimIndent()
            )
        }
    }
}

private fun parseRule(
    line: String
) = parseRule(line, {_, message -> fail(message)}, RuleFragmentParser(line.length) )!!

internal fun parseDexMethod(line: String): DexMethod {
    // a bit of the hack, rules require to provide a flags for methods, H is added to beginning
    return parseRule("H$line").toDexMethod()
}

private fun HumanReadableProfile(vararg strings: String) : HumanReadableProfile {
    return HumanReadableProfile(*strings) {
       _, _, message -> fail(message)
    }!!
}
