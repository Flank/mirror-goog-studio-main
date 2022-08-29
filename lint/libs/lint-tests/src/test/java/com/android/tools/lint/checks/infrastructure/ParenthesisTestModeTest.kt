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

class ParenthesisTestModeTest {
    private fun parenthesizeKotlin(@Language("kotlin") source: String, includeUnlikely: Boolean = false): String {
        return parenthesize(kotlin(source), includeUnlikely)
    }

    private fun parenthesizeJava(@Language("java") source: String, includeUnlikely: Boolean = false): String {
        return parenthesize(java(source), includeUnlikely)
    }

    private fun parenthesize(testFile: TestFile, includeUnlikely: Boolean = false): String {
        val sdkHome = TestUtils.getSdk().toFile()
        var source = testFile.contents
        ParenthesisTestMode(includeUnlikely).processTestFiles(listOf(testFile), sdkHome) { _, s -> source = s }
        return source
    }

    @Test
    fun testBasic() {
        @Language("kotlin")
        val kotlin = """
            @file:Suppress("ALL")
            fun test(i1: Int, i2: Int, s1: String, s2: String, a: Any, b1: Boolean, b2: Boolean) {
                val x = i1 + i2 + s1.length + s2.length
                var y = 0
                y++
                val z2 = !b2
                val z = (b2 && !b1 && ++y != 5)
                if (x > 1) {
                    val y = a as? String
                }
                val t: Any = "test"
                val t2: Any = ${'"'}""test""${'"'}
                (t as? String)?.plus("other")?.get(0)?.dec()?.inc()
                "foo".chars().allMatch { it.dec() > 0 }.toString()
            }
        """.trimIndent().trim()

        @Language("kotlin")
        val expected = """
            @file:Suppress("ALL")
            fun test(i1: Int, i2: Int, s1: String, s2: String, a: Any, b1: Boolean, b2: Boolean) {
                val x = (((i1 + i2) + s1.length) + s2.length)
                var y = 0
                y++
                val z2 = !(b2)
                val z = (((b2 && !(b1)) && (++y != 5)))
                if ((x > 1)) {
                    val y = (a as? String)
                }
                val t: Any = "test"
                val t2: Any = ""${'"'}test""${'"'}
                (((((t as? String))?.plus("other"))?.get(0))?.dec())?.inc()
                (("foo".chars()).allMatch { ((it).dec() > 0) }).toString()
            }
        """.trimIndent().trim()

        val parenthesized = parenthesizeKotlin(kotlin)
        assertEquals(expected, parenthesized)

        @Language("kotlin")
        val expectedWithUnlikelyParens = """
            @file:Suppress(("ALL"))
            fun test(i1: Int, i2: Int, s1: String, s2: String, a: Any, b1: Boolean, b2: Boolean) {
                val x = (((i1 + i2) + s1.length) + s2.length)
                var y = (0)
                y++
                val z2 = !(b2)
                val z = (((b2 && !(b1)) && (++y != (5))))
                if ((x > (1))) {
                    val y = (a as? String)
                }
                val t: Any = ("test")
                val t2: Any = (${'"'}""test""${'"'})
                ((((((t as? String)))?.plus(("other")))?.get((0)))?.dec())?.inc()
                ((("foo").chars()).allMatch { ((it).dec() > (0)) }).toString()
            }
        """.trimIndent().trim()

        val parenthesized2 = parenthesizeKotlin(kotlin, includeUnlikely = true)
        assertEquals(expectedWithUnlikelyParens, parenthesized2)
    }

    @Test
    fun testNoParensOnFqn() {
        @Language("kotlin")
        val kotlin = """
            @file:Suppress("ALL")
            @Suppress("RemoveRedundantQualifierName")
            fun test() {
                val max = Integer.MAX_VALUE
                System.loadLibrary("test")
                java.lang.System.loadLibrary("test")
            }

        """.trimIndent().trim()

        @Language("kotlin")
        val expected = """
            @file:Suppress("ALL")
            @Suppress("RemoveRedundantQualifierName")
            fun test() {
                val max = Integer.MAX_VALUE
                System.loadLibrary("test")
                java.lang.System.loadLibrary("test")
            }
        """.trimIndent().trim()

        val parenthesized = parenthesizeKotlin(kotlin)
        assertEquals(expected, parenthesized)
    }

    @Test
    fun testEndWithParen() {
        @Language("kotlin")
        val kotlin = """
            @CheckResult fun checkBoolean(): Boolean = true
        """.trimIndent().trim()

        @Language("kotlin")
        val expected = """
            @CheckResult fun checkBoolean(): Boolean = (true)
        """.trimIndent().trim()
        val parenthesized = parenthesizeKotlin(kotlin, includeUnlikely = true)
        assertEquals(expected, parenthesized)
    }

    @Test
    fun testStringTemplate() {
        @Language("kotlin")
        val kotlin = """
            val t: Any = "test"
            fun label(a: Any): String = "value: ${'$'}a"
        """.trimIndent().trim()

        @Language("kotlin")
        val expected = """
            val t: Any = ("test")
            fun label(a: Any): String = "value: ${'$'}a"
        """.trimIndent().trim()
        val parenthesized = parenthesizeKotlin(kotlin, includeUnlikely = true)
        assertEquals(expected, parenthesized)
    }

    @Test
    fun testJavaTernary() {
        @Language("java")
        val java = """
            @SuppressWarnings("ALL")
            public class Test {
                void test(int i) {
                    boolean x = i > 5 ? !true : i % 2 == 0;
                }
            }
        """.trimIndent().trim()

        @Language("java")
        val expected = """
            @SuppressWarnings("ALL")
            public class Test {
                void test(int i) {
                    boolean x = ((i > 5)) ? (!(true)) : (((i % 2) == 0));
                }
            }
        """.trimIndent().trim()
        val parenthesized = parenthesizeJava(java)
        assertEquals(expected, parenthesized)
    }

    @Test
    fun testSuperClass() {
        @Language("kotlin")
        val kotlin = """
            @Suppress("RemoveEmptyClassBody")
            abstract class MyNumber : Number() {
            }
        """.trimIndent().trim()

        @Language("kotlin")
        val expected = """
            @Suppress("RemoveEmptyClassBody")
            abstract class MyNumber : Number() {
            }
        """.trimIndent().trim()
        val parenthesized = parenthesizeKotlin(kotlin)
        assertEquals(expected, parenthesized)
    }

    @Test
    fun testThisKotlin() {
        @Language("kotlin")
        val kotlin = """
            class Foo(val foo: String) {
                constructor(b: Int) : this("")
                fun test() {
                    this.toString()
                    super.toString()
                }
            }
        """.trimIndent().trim()

        @Language("kotlin")
        val expected = """
            class Foo(val foo: String) {
                constructor(b: Int) : this((""))
                fun test() {
                    (this).toString()
                    (super).toString()
                }
            }
        """.trimIndent().trim()
        val parenthesized = parenthesizeKotlin(kotlin, includeUnlikely = true)
        assertEquals(expected, parenthesized)
    }

    @Test
    fun testThisJava() {
        @Language("java")
        val java = """
            public class CheckResultTest1 {
                CheckResultTest1() {
                    this(null);
                }

                CheckResultTest1(String foo) {
                }

                public class SubClass extends CheckResultTest1 {
                    SubClass(String foo) {
                        super(null);
                    }
                }
            }
        """.trimIndent().trim()

        @Language("java")
        val expected = """
            public class CheckResultTest1 {
                CheckResultTest1() {
                    this(null);
                }

                CheckResultTest1(String foo) {
                }

                public class SubClass extends CheckResultTest1 {
                    SubClass(String foo) {
                        super(null);
                    }
                }
            }
        """.trimIndent().trim()
        val parenthesized = parenthesizeJava(java)
        assertEquals(expected, parenthesized)
    }

    @Test
    fun testAssertionsAndAnnotations() {
        @Language("java")
        val java = """
            import android.view.View;
            import android.net.Uri;
            import androidx.annotation.RequiresPermission;
            @SuppressWarnings("ALL")
            public class Test {
                 public static final String WRITE_HISTORY_BOOKMARKS="com.android.browser.permission.WRITE_HISTORY_BOOKMARKS";
                @RequiresPermission.Write(@RequiresPermission(WRITE_HISTORY_BOOKMARKS))
                public static final Uri BOOKMARKS_URI = Uri.parse("content://browser/bookmarks");

                void test(View view) {
                    assert (view != null);
                    String s = new String("test");
                }
            }
        """.trimIndent().trim()

        @Language("java")
        val expected = """
            import android.view.View;
            import android.net.Uri;
            import androidx.annotation.RequiresPermission;
            @SuppressWarnings("ALL")
            public class Test {
                 public static final String WRITE_HISTORY_BOOKMARKS="com.android.browser.permission.WRITE_HISTORY_BOOKMARKS";
                @RequiresPermission.Write(@RequiresPermission(WRITE_HISTORY_BOOKMARKS))
                public static final Uri BOOKMARKS_URI = Uri.parse("content://browser/bookmarks");

                void test(View view) {
                    assert ((view != null));
                    String s = (new String("test"));
                }
            }
        """.trimIndent().trim()
        val parenthesized = parenthesizeJava(java)
        assertEquals(expected, parenthesized)
    }

    @Test
    fun testSkipCase() {
        @Language("kotlin")
        val kotlin = """
            @file:Suppress("ALL")
            package test.pkg
            fun testWhen(sdk: Int) {
                when (sdk) {
                    in 1..15 -> { return }
                }
                loop@ for (i in 1..100) { }
            }
        """.trimIndent().trim()

        @Language("kotlin")
        val expected = """
            @file:Suppress("ALL")
            package test.pkg
            fun testWhen(sdk: Int) {
                when (sdk) {
                    in (1..15) -> { return }
                }
                loop@ for (i in (1..100)) { }
            }
        """.trimIndent().trim()

        val parenthesized = parenthesizeKotlin(kotlin)
        assertEquals(expected, parenthesized)
    }

    @Test
    fun testArrays() {
        @Language("java")
        val java = """
            public class Test {
                private static final int VALUE_A = 5;
                private static final int VALUE_B = 5;
                private static final int[] VALID_ARRAY = {VALUE_A, VALUE_B};
                private static final int[] VALID_ARRAY2 = new int[] {VALUE_A, VALUE_B};
                private static final int[] VALID_ARRAY3 = new int[5];
            }
        """.trimIndent()

        @Language("java")
        val expected = """
            public class Test {
                private static final int VALUE_A = 5;
                private static final int VALUE_B = 5;
                private static final int[] VALID_ARRAY = {VALUE_A, VALUE_B};
                private static final int[] VALID_ARRAY2 = (new int[] {VALUE_A, VALUE_B});
                private static final int[] VALID_ARRAY3 = (new int[5]);
            }
        """.trimIndent()
        val parenthesized = parenthesizeJava(java)
        assertEquals(expected, parenthesized)
    }
}
