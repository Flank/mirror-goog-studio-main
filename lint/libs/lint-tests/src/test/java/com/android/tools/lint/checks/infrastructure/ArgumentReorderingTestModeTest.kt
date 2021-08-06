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

@file:Suppress(
    "CastCanBeRemovedNarrowingVariableType", "RemoveRedundantQualifierName", "RemoveExplicitTypeArguments",
    "HasPlatformType", "ConstantConditions", "MemberVisibilityCanBePrivate"
)
package com.android.tools.lint.checks.infrastructure

import com.android.testutils.TestUtils
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import junit.framework.TestCase.assertEquals
import org.intellij.lang.annotations.Language
import org.junit.Test

class ArgumentReorderingTestModeTest {
    private fun reorder(@Language("kotlin") source: String): String {
        val testFile = kotlin(source)
        val sdkHome = TestUtils.getSdk().toFile()
        var modified = testFile.contents
        ArgumentReorderingTestMode().processTestFiles(listOf(testFile), sdkHome = sdkHome) { _, s -> modified = s }
        return modified
    }

    @Test
    fun testBasic() {
        @Language("kotlin")
        val kotlin = """
            @file:Suppress("MoveLambdaOutsideParentheses", "UNUSED_PARAMETER", "unused",
                "UNUSED_ANONYMOUS_PARAMETER", "BooleanLiteralArgument"
            )

            package test.pkg

            fun test1(s: String, n: Number, z: Boolean) { }
            fun test2(enabled: Boolean, `class`: String, list: List<String> = emptyList(), vararg ages: Int = IntArray(0)) {}
            fun String.test3(z: Boolean, y: Boolean) { }
            fun test4(s: String, t: Int, run: (String, Boolean, String) -> Map<String, String> = { _,_,_ -> mutableMapOf()}) { }

            fun calls() {
                test1("test", 5, true)
                test1(
                    "test",
                    5,
                    true
                )
                test1(s = "test", n = 5, z = true)

                val test = true
                test2(true, "this is a test")
                test2(true, "this is " +
                        "a test")
                test2(true, "this is ${'$'}test")
                test2(true, ""${'"'}\nTest\n${'$'}{test and true}""${'"'})
                test2(true, "hello", listOf("world"), 1, 2, 3, 4)
                val array = intArrayOf(1,2,3)
                test2(true, "hello", listOf("world"), *array)
                "test".test3(true, false)
                test4("hello", 42, { x,y,z -> error(x)})
                test4("hello", 42, {
                        x,_,_ -> error(x)
                })
                test4("hello", 42) { x, y, z ->
                    mutableMapOf()
                }
            }
        """.trimIndent().trim()

        @Language("kotlin")
        val expected = """
            @file:Suppress("MoveLambdaOutsideParentheses", "UNUSED_PARAMETER", "unused",
                "UNUSED_ANONYMOUS_PARAMETER", "BooleanLiteralArgument"
            )

            package test.pkg

            fun test1(s: String, n: Number, z: Boolean) { }
            fun test2(enabled: Boolean, `class`: String, list: List<String> = emptyList(), vararg ages: Int = IntArray(0)) {}
            fun String.test3(z: Boolean, y: Boolean) { }
            fun test4(s: String, t: Int, run: (String, Boolean, String) -> Map<String, String> = { _,_,_ -> mutableMapOf()}) { }

            fun calls() {
                test1(n = 5, z = true, s = "test")
                test1(
                    n = 5,
                    z = true,
                    s = "test"
                )
                test1(n = 5, z = true, s = "test")

                val test = true
                test2(`class` = "this is a test", enabled = true)
                test2(`class` = "this is " +
                        "a test", enabled = true)
                test2(`class` = "this is ${'$'}test", enabled = true)
                test2(`class` = ""${'"'}\nTest\n${'$'}{test and true}""${'"'}, enabled = true)
                test2(true, "hello", listOf("world"), 1, 2, 3, 4)
                val array = intArrayOf(1,2,3)
                test2(true, "hello", listOf("world"), *array)
                "test".test3(y = false, z = true)
                test4(t = 42, run = { x,y,z -> error(x)}, s = "hello")
                test4(t = 42, run = {
                        x,_,_ -> error(x)
                }, s = "hello")
                test4(t = 42, run = { x, y, z ->
                    mutableMapOf()
                }, s = "hello")
            }
        """.trimIndent().trim()

        val expanded = reorder(kotlin)
        assertEquals(expected, expanded)
    }
}
