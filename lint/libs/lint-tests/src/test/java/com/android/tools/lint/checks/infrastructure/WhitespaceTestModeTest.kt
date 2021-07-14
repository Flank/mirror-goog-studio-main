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

class WhitespaceTestModeTest {
    private fun addSpacesKotlin(@Language("kotlin") source: String): String {
        return addSpaces(kotlin(source))
    }

    private fun addSpacesJava(@Language("java") source: String): String {
        return addSpaces(java(source))
    }

    private fun addSpaces(testFile: TestFile): String {
        val sdkHome = TestUtils.getSdk().toFile()
        var source = testFile.contents
        WhitespaceTestMode().processTestFiles(listOf(testFile), sdkHome) { _, s -> source = s }
        return source
    }

    @Test
    fun testBasic() {
        @Language("kotlin")
        val kotlin = """
            @file:Suppress("ALL")
            import android.util.List
            /** {@link Test} and [test] */
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
        val expected = "" +
            " @file:Suppress(\"ALL\") \n" +
            " import android.util.List \n" +
            " /** {@link Test} and [test] */ \n" +
            " fun   test ( i1 :   Int ,   i2 :   Int ,   s1 :   String ,   s2 :   String ,   a :   Any ,   b1 :   Boolean ,   b2 :   Boolean )   { \n" +
            "     val   x   =   i1   +   i2   +   s1 . length   +   s2 . length \n" +
            "     var   y   =   0 \n" +
            "     y ++ \n" +
            "     val   z2   =   ! b2 \n" +
            "     val   z   =   ( b2   &&   ! b1   &&   ++ y   !=   5 ) \n" +
            "     if   ( x   >   1 )   { \n" +
            "         val   y   =   a   as?   String \n" +
            "     } \n" +
            "     val   t :   Any   =   \"test\" \n" +
            "     val   t2 :   Any   =   \"\"\"test\"\"\" \n" +
            "     ( t   as?   String ) ?. plus ( \"other\" ) ?. get ( 0 ) ?. dec ( ) ?. inc ( ) \n" +
            "     \"foo\" . chars ( ) . allMatch   {   it . dec ( )   >   0   } . toString ( ) \n" +
            " } "

        val modified = addSpacesKotlin(kotlin)
        assertEquals(expected, modified)
    }

    @Test
    fun testStrings() {
        @Language("kotlin")
        val kotlin = """
            @file:Suppress("ALL")
            val test = "test" + ""${'"'}test""${'"'}
        """.trimIndent().trim()

        @Suppress("MayBeConstant")
        @Language("kotlin")
        val expected = "" +
            " @file:Suppress(\"ALL\") \n" +
            " val   test   =   \"test\"   +   \"\"\"test\"\"\" "

        val modified = addSpacesKotlin(kotlin)
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
                    /** {@link Test} */
                    String s="test"+'test';
                    boolean x=i>5?!true:i%2==0;
                }
            }
        """.trimIndent().trim()

        @Suppress("DanglingJavadoc", "PointlessBooleanExpression", "ConstantConditions")
        @Language("java")
        val expected = "" +
            " package test.pkg; \n" +
            " import android.util.List; \n" +
            " @SuppressWarnings(\"ALL\") \n" +
            " public   class   Test   { \n" +
            "     void   test ( int   i )   { \n" +
            "         /** {@link Test} */ \n" +
            "         String   s = \"test\" + 'test' ; \n" +
            "         boolean   x = i > 5 ? ! true : i % 2 == 0 ; \n" +
            "     } \n" +
            " } "
        val modified = addSpacesJava(java)
        assertEquals(expected, modified)
    }
}
