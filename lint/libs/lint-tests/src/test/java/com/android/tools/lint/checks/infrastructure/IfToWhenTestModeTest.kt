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

package com.android.tools.lint.checks.infrastructure

import com.android.testutils.TestUtils
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import junit.framework.TestCase.assertEquals
import org.intellij.lang.annotations.Language
import org.junit.Test

class IfToWhenTestModeTest {
    private fun convertToWhen(@Language("kotlin") source: String): String {
        return convert(kotlin(source))
    }

    private fun convertToSwitch(@Language("java") source: String): String {
        return convert(java(source))
    }

    private fun convert(testFile: TestFile): String {
        val sdkHome = TestUtils.getSdk().toFile()
        var source = testFile.contents
        IfToWhenTestMode().processTestFiles(listOf(testFile), sdkHome) { _, s -> source = s }
        return source
    }

    @Test
    fun testBasic() {
        @Language("kotlin")
        val kotlin = """
            @file:Suppress("ALL")
            fun test(owner: String, name: String, desc: String): Int {
                if (owner == "foo") {
                    println("here")
                } else if (name == "bar") {
                    println("here2")
                    println("here3")
                } else {
                    while (true) {
                        if (owner == "baz") { // only if statements not within other if statements get nested
                            println("here4")
                            break
                        }
                    }
                    println("fallback")
                }
                return if (owner.length < 5) {
                    0
                } else {
                    owner.length
                }
            }
        """.trimIndent().trim()

        @Language("kotlin")
        val expected = """
            @file:Suppress("ALL")
            fun test(owner: String, name: String, desc: String): Int {
                when {
                owner == "foo" -> {
                    println("here")
                }
                name == "bar" -> {
                    println("here2")
                    println("here3")
                }
                else -> {
                    while (true) {
                        if (owner == "baz") { // only if statements not within other if statements get nested
                            println("here4")
                            break
                        }
                    }
                    println("fallback")
                }
                }
                return when {
                owner.length < 5 -> {
                    0
                }
                else -> {
                    owner.length
                }
                }
            }
        """.trimIndent().trim()

        val modified = convertToWhen(kotlin)
        assertEquals(expected, modified)
    }

    @Test
    fun testJava() {
        @Language("java")
        val java = """
            package test.pkg;
            import android.util.List;
            @SuppressWarnings("ALL")
            public class Test {
                void test(int i) {
                    if (i == 5) {
                        System.out.println("case 1");
                    } else if (i == 6) {
                        System.out.println("case 2");
                    } else {
                        System.out.println("case 3");
                    }
                }
            }
        """.trimIndent().trim()

        @Suppress("DanglingJavadoc", "PointlessBooleanExpression", "ConstantConditions")
        @Language("java")
        val expected = """
            package test.pkg;
            import android.util.List;
            @SuppressWarnings("ALL")
            public class Test {
                void test(int i) {
                    if (i == 5) {
                        System.out.println("case 1");
                    } else if (i == 6) {
                        System.out.println("case 2");
                    } else {
                        System.out.println("case 3");
                    }
                }
            }
        """.trimIndent().trim()
        val modified = convertToSwitch(java)
        assertEquals(expected, modified)
    }

    @Test
    fun testKotlin2() {
        @Language("kotlin")
        val kotlin =
            """
            @file:Suppress("ALL")
            package test.pkg

            import android.os.Build

            inline fun <T> T.applyForOreoOrAbove2(block: T.() -> Unit) {
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    block()
                } else {
                    error("Unexpected")
                }
            }
            """.trimIndent().trim()

        @Language("kotlin")
        val expected = """
            @file:Suppress("ALL")
            package test.pkg

            import android.os.Build

            inline fun <T> T.applyForOreoOrAbove2(block: T.() -> Unit) {
                return when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                    block()
                }
                else -> {
                    error("Unexpected")
                }
                }
            }
        """.trimIndent().trim()

        val modified = convertToWhen(kotlin)
        assertEquals(expected, modified)
    }
}
