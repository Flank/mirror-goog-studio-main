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

class BodyRemovalTestModeTest {
    private fun convertKotlin(@Language("kotlin") source: String): String {
        return convert(kotlin(source))
    }

    private fun convertJava(@Language("java") source: String): String {
        return convert(java(source))
    }

    private fun convert(testFile: TestFile): String {
        val sdkHome = TestUtils.getSdk().toFile()
        var source = testFile.contents
        BodyRemovalTestMode().processTestFiles(listOf(testFile), sdkHome) { _, s -> source = s }
        return source
    }

    @Test
    fun testBracesKotlin() {
        @Language("kotlin")
        val kotlin = """
            fun test(x: Int): Int {
                if (x < -5) { test(x+1) } else { test(x+2) }
                return if (x > 0) test(x-1) else 0
            }
        """.trimIndent().trim()

        @Language("kotlin")
        val expected = """
            fun test(x: Int): Int {
                if (x < -5) test(x+1) else test(x+2)
                return if (x > 0) { test(x-1) } else { 0 }
            }
        """.trimIndent().trim()
        val converted = convertKotlin(kotlin)
        assertEquals(expected, converted)
    }

    @Test
    fun testBracesJava() {
        @Language("java")
        val java = """
            @SuppressWarnings("ALL")
            class Test {
                void test(int x) {
                    if (x < -5) { test(x+1); } else { test(x+2); }
                    if (x > 0)
                        test(x-1);
                    else if (x == -1)
                        test(x+2) ;
                    else
                        return;
                }
            }
        """.trimIndent().trim()

        @Language("java")
        val expected = """
            @SuppressWarnings("ALL")
            class Test {
                void test(int x) {
                    if (x < -5) test(x+1); else test(x+2);
                    if (x > 0)
                        { test(x-1); }
                    else if (x == -1)
                        { test(x+2) ; }
                    else
                        { return; }
                }
            }
        """.trimIndent().trim()
        val converted = convertJava(java)
        assertEquals(expected, converted)
    }

    @Test
    fun testExpressionBody() {
        @Language("kotlin")
        val kotlin = """
            fun test1(): Int {
                // My comment
                return 5
            }
            fun test2() {
                return
            }
        """.trimIndent().trim()

        @Language("kotlin")
        val expected = """
            fun test1(): Int =
                // My comment
                5

            fun test2() {
                return
            }
        """.trimIndent().trim()
        val converted = convertKotlin(kotlin)
        assertEquals(expected, converted)
    }

    @Test
    fun test196881523() {
        @Language("kotlin")
        val kotlin = """
            package test.pkg
            class TimeProviderKt {
                internal companion object {
                    @JvmStatic
                    fun getTimeStatically(): Int {
                        return -1
                    }
                }
            }
        """.trimIndent().trim()

        @Language("kotlin")
        val expected = "" +
            "package test.pkg\n" +
            "class TimeProviderKt {\n" +
            "    internal companion object {\n" +
            "        @JvmStatic\n" +
            "        fun getTimeStatically(): Int =\n" +
            "            -1\n" +
            "        \n" +
            "    }\n" +
            "}"
        val converted = convertKotlin(kotlin)
        assertEquals(expected, converted)
    }

    @Test
    fun testElse() {
        @Language("kotlin")
        val kotlin = """
            import android.os.Build
            import androidx.annotation.RequiresApi

            fun methodWithReflection() {
                // We can't remove the braces here, because it would associate our outer else
                // with the inner if statement!
                if (false) {
                    if (Build.VERSION.SDK_INT < 28) return
                } else {println("test")}
            }
        """.trimIndent()

        @Language("kotlin")
        val expected = """
            import android.os.Build
            import androidx.annotation.RequiresApi

            fun methodWithReflection() {
                // We can't remove the braces here, because it would associate our outer else
                // with the inner if statement!
                if (false) {
                    if (Build.VERSION.SDK_INT < 28) { return }
                } else println("test")
            }
        """.trimIndent()

        val converted = convertKotlin(kotlin)
        assertEquals(expected, converted)
    }
}
