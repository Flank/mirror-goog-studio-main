/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.lint.detector.api

import com.android.tools.lint.checks.infrastructure.TestFiles.base64gzip
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.intellij.openapi.util.Disposer
import junit.framework.TestCase
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UResolvable
import org.jetbrains.uast.visitor.AbstractUastVisitor
import org.junit.Rule
import org.junit.rules.TemporaryFolder

// Misc tests to verify resolve-handling in the Kotlin UAST initialization.
class ResolveTest : TestCase() {
    @get:Rule
    val folder = TemporaryFolder()

    fun testKotlinReferencesIntoJavaSource() {
        val tests = arrayOf(
            java(
                "pkg/Foo.java",
                """
                package pkg;

                public class Foo {
                    public static void test() {
                        System.out.println("hello");
                    }
                }
            """
            ).indented(),
            kotlin(
                "test.kt",
                """
                package pkg

                fun test() {
                    Foo.test()
                }
            """
            ).indented()
        )

        val pair = LintUtilsTest.parseAll(*tests)

        val file = pair.first.find { it.file.name == "test.kt" }?.uastFile
        assertEquals(
            """
            UFile (package = pkg) [package pkg...]
                UClass (name = TestKt) [public final class TestKt {...}]
                    UMethod (name = test) [public static final fun test() : void {...}]
                        UBlockExpression [{...}]
                            UQualifiedReferenceExpression [Foo.test()] => PsiMethod:test
                                USimpleNameReferenceExpression (identifier = Foo) [Foo] => PsiClass:Foo
                                UCallExpression (kind = UastCallKind(name='method_call'), argCount = 0)) [test()] => PsiMethod:test
                                    UIdentifier (Identifier (test)) [UIdentifier (Identifier (test))]
                                    USimpleNameReferenceExpression (identifier = test, resolvesTo = null) [test] => <FAILED>
            """.trimIndent().trim(),
            file?.asResolveString()?.trim()
        )
        Disposer.dispose(pair.second)
    }

    fun testKotlinPropertyAccess() {
        val tests = arrayOf(
            kotlin(
                """
                import kotlin.reflect.full.declaredMemberFunctions

                class KotlinTest {
                    fun test() {
                        this::class.members
                        this::class.declaredMemberFunctions
                    }
                }
            """
            ).indented()
        )

        val pair = LintUtilsTest.parse(*tests)
        val file = pair.first.uastFile
        assertEquals(
            """
            UFile (package = ) [import kotlin.reflect.full.declaredMemberFunctions...]
                UImportStatement (isOnDemand = false) [import kotlin.reflect.full.declaredMemberFunctions] => <FAILED>
                UClass (name = KotlinTest) [public final class KotlinTest {...}]
                    UMethod (name = test) [public final fun test() : void {...}]
                        UBlockExpression [{...}]
                            UQualifiedReferenceExpression [KotlinTest.members] => <FAILED>
                                UClassLiteralExpression [KotlinTest]
                                USimpleNameReferenceExpression (identifier = members) [members] => PsiMethod:getMembers
                            UQualifiedReferenceExpression [KotlinTest.declaredMemberFunctions] => <FAILED>
                                UClassLiteralExpression [KotlinTest]
                                USimpleNameReferenceExpression (identifier = declaredMemberFunctions) [declaredMemberFunctions] => PsiMethod:getDeclaredMemberFunctions
                    UMethod (name = KotlinTest) [public fun KotlinTest() = UastEmptyExpression]
            """.trimIndent().trim(),
            file?.asResolveString()?.trim()
        )
        Disposer.dispose(pair.second)
    }

    fun testKotlinReferencesIntoBytecode() {
        val tests = arrayOf(
            kotlin(
                """
                package pkg
                import lib.Bar
                import lib.Bar2

                fun test() {
                    val bar = Bar("hello1")
                    val bar2 = Bar2("hello2")
                }
            """
            ).indented(),
            libBar
        )

        val pair = LintUtilsTest.parse(*tests)

        val file = pair.first.uastFile
        assertEquals(
            """
            UFile (package = pkg) [package pkg...]
                UImportStatement (isOnDemand = false) [import lib.Bar] => <FAILED>
                UImportStatement (isOnDemand = false) [import lib.Bar2] => PsiClass:Bar2
                UClass (name = TestKt) [public final class TestKt {...}]
                    UMethod (name = test) [public static final fun test() : void {...}]
                        UBlockExpression [{...}]
                            UDeclarationsExpression [var bar: lib.Bar = <init>("hello1")]
                                ULocalVariable (name = bar) [var bar: lib.Bar = <init>("hello1")]
                                    UCallExpression (kind = UastCallKind(name='constructor_call'), argCount = 1)) [<init>("hello1")] => PsiMethod:Bar
                                        UIdentifier (Identifier (Bar)) [UIdentifier (Identifier (Bar))]
                                        USimpleNameReferenceExpression (identifier = <init>, resolvesTo = Bar) [<init>] => PsiClass:Bar
                                        ULiteralExpression (value = "hello1") ["hello1"]
                            UDeclarationsExpression [var bar2: lib.Bar2 = <init>("hello2")]
                                ULocalVariable (name = bar2) [var bar2: lib.Bar2 = <init>("hello2")]
                                    UCallExpression (kind = UastCallKind(name='constructor_call'), argCount = 1)) [<init>("hello2")] => PsiMethod:Bar2
                                        UIdentifier (Identifier (Bar2)) [UIdentifier (Identifier (Bar2))]
                                        USimpleNameReferenceExpression (identifier = <init>, resolvesTo = Bar2) [<init>] => PsiClass:Bar2
                                        ULiteralExpression (value = "hello2") ["hello2"]
            """.trimIndent().trim(),
            file?.asResolveString()?.trim()
        )
        Disposer.dispose(pair.second)
    }

    fun testJavaReferencesIntoBytecode() {
        val tests = arrayOf(
            java(
                """
                package pkg;
                import lib.Bar;
                import lib.Bar2;
                @SuppressWarnings("ALL")
                public class Foo2 {
                    public void test() {
                        new Bar("hello1");
                        new Bar2("hello2");
                    }
                }
                """
            ),
            libBar
        )

        val pair = LintUtilsTest.parse(*tests)

        val file = pair.first.uastFile
        assertEquals(
            """
            UFile (package = pkg) [package pkg...]
                UImportStatement (isOnDemand = false) [import lib.Bar] => PsiClass:Bar
                UImportStatement (isOnDemand = false) [import lib.Bar2] => PsiClass:Bar2
                UClass (name = Foo2) [public class Foo2 {...}]
                    UAnnotation (fqName = java.lang.SuppressWarnings) [@java.lang.SuppressWarnings(null = "ALL")] => PsiClass:SuppressWarnings
                        UNamedExpression (name = null) [null = "ALL"]
                            ULiteralExpression (value = "ALL") ["ALL"]
                    UMethod (name = test) [public fun test() : void {...}]
                        UBlockExpression [{...}]
                            UCallExpression (kind = UastCallKind(name='constructor_call'), argCount = 1)) [Bar("hello1")] => PsiMethod:Bar
                                USimpleNameReferenceExpression (identifier = Bar) [Bar] => PsiClass:Bar
                                ULiteralExpression (value = "hello1") ["hello1"]
                            UCallExpression (kind = UastCallKind(name='constructor_call'), argCount = 1)) [Bar2("hello2")] => PsiMethod:Bar2
                                USimpleNameReferenceExpression (identifier = Bar2) [Bar2] => PsiClass:Bar2
                                ULiteralExpression (value = "hello2") ["hello2"]
            """.trimIndent().trim(),
            file?.asResolveString()?.trim()
        )
        Disposer.dispose(pair.second)
    }

    /*
    Compiled from
        $ cat lib/Bar.kt lib/Bar2.java
            package lib
            data class Bar(val bar: String)
        $ cat lib/Bar.kt lib/Bar2.java
            package lib;

            public class Bar2 {
                public Bar2(String msg) {
                }
            }
    */
    private val libBar = base64gzip(
        "libs/libBar.jar",
        "" +
            "H4sIAAAAAAAAAHWVeTTUaxjHx/YzUybbMLYuhRExJkuiayyZyTZmaC5J90RZ" +
            "skW/KGSbbHUrjCsiSzQhS91IItlJGmSMrZCKGSqKLEPo0t2Me3rf85z3vOd5" +
            "v8/7fP/5PCQrPn4EBAqFQuayJq0hGxYMwg8h4MgmGhY2eM3VNgiED0KyEoSu" +
            "p3j/fkL6oRixFv+KCSY2FnjcITKagP9CoD+3ttJAd8GtNNQ66J1ldhim9sgY" +
            "iLYkqFsQugIL+WGHx6WbZNNl1U5ykMooNk1tHxspMYpUToH0eM+A0yDP9y4Q" +
            "Lt4aZmt/4Li6+LSpi21r4eN5XNPUBdRCn/BxOXNm/5xfHxYAAHEAiwbcAfRZ" +
            "4NRZ4Cc8sPcGvZouth8P2MKzi5tKzVYymqoDVkaScunVAd7tGDwgyV5OJuOB" +
            "8Gx6NbmWMUhj5iY20eRuQh38wz1twUIn1MXGieTDrjRBrcg4SgwllUpRVNG2" +
            "kFdqbVWIxPQZqe7WrYymUhwPaF8xoVJg42IizvymkoC9wjNgVJXXE6XQXlie" +
            "STmteDh9uzlIEXC8uUU/0gcF2Hw3K6bU9On+mpVeLrOsTWbh/5n9y2sM2Yko" +
            "6yCy2jHt++R60OQgNFVJQ2nPWTHxkBydVElQNS+WRrK1zYqf1FAvPjcxJXNH" +
            "U4K4CKMGlZDHlIXvME2Z5hIEGnb24C+Z0+gWnkLL+TT9M+41X9rT0la+Zr/+" +
            "BsHE4yjJvj1t3W1WJlCpD15W13zHilPk+3rbQxJNrRXsD2UZ14QEPZIKSikL" +
            "cXJ+9D6kaCxQuyC4DVwuBeX0SJSxJOwn4IvGK9S90UJpa+2WtIbx08lCYuZ5" +
            "O3K2NkXpPkeaOyjay4U+BYtHsuoGGqb7u5ThMMpl2VPOTjbBYUWODzPqZuXc" +
            "Undv+8qxHyLuUPGvqXHv1cvaRdQlzBRwRI0o3kN1LsMVxCq1qO59txQTymZR" +
            "AsPZDcHbT8/CxgMrKicM8sQHc9XfT7GEeQ+oiE65tnQ5ZjJ+Emv1rzNvXLYp" +
            "/2jsXPpi2iCepG/zUDYAEiW0lBUp+cgFOhaOcKtyG4D//hs2992WL327qowD" +
            "a4reyDypfDmokxlbd1X8EEdYpyoMgQPUYRdt4XpaUvh0uFuVC2HRe2EodiC8" +
            "2hKowHQkLNTv90Xy5n0WpWJq/VRfGdlxopJrlc3G0oQH2BFW/bA2Z7FsEHbn" +
            "PthT4M2iG+uSb7aibjjtSReq31p+q7i8YgRnZyPr6kWiZH/yO9brls28fb+2" +
            "pJsokx9wR9liPtH1Md59sv1QiV/E+aPxlyaGQxmJK6D6PCufpvO2zFOx+XFa" +
            "BfFEWT78agDI1JE2oDom6hOqsHelDVemsXtQ7sPYy7bOscVPaFsZxJjhzibT" +
            "u3ydDgtPy6OprlXvLsyYNMB7rdPrlHoWWcBcf53pc1hxnuPph+qvCwdYzDfj" +
            "doJxMW0OQ1WlqckeqjOcdCTFpwekL9qXuYdQ3UZ32Xu0DfNM/ipEDjth44t3" +
            "Wcp9NfOepXhZD7nzyu0TPgW2SnMwDyqbKPBM0/OpFs7uQPzq8koB4661vaXU" +
            "pbf3xoOfuTXKlaS9MC9NHQ1FdOQPkS4c1OINMbZL0rEMfifRIbHgn3N4CW5Y" +
            "G4DpjGGLorx3VPLNKmUaoQyVd3Lklvb/HJcwg1D5/JGV8u5ZHn50BJYUem7v" +
            "6LwMWPkg7nIcZke5T9yR+ehzH/Lcyf6wVEUvh1vsDnhLxCClhb+omYEeFp7O" +
            "fsA2mZUL1pcJOKJfYzyd0TzifdRxuLEn1AXZJx1jtorSeCx1fiBPmSMrgruW" +
            "WyB3Hv0tZhaNDY1v0ruPzHTSbV1EYLOOM8Q5SlsyaMZZnW+OT3gIXVuNHjQ5" +
            "5ikPxlPvdn+TmPqoIBrRJSARV23WSLn3siejmdcC53FuImeqRMG+3mPyNfu6" +
            "QT/BwV9e2Ly5UQIqn/C5eUspNiSMzjF/4PDSC8vALoUJca6flN3NrPd14xxb" +
            "R0mhgJS4CD8EEiS4jhIeXgSEm9//kH0d/tyLaxRslm7EMYJLZviDQbCxwjq2" +
            "NzJuG1eFxv9BfKN2nYIbTcG5tH/wbGIiyUoAWE/wr+3ZtfOrwPrtTxRMPJAF" +
            "BwAA"
    )
}

private fun UFile.asResolveString() = ResolveLogger().apply {
    this@asResolveString.accept(this)
}.toString()

class ResolveLogger : AbstractUastVisitor() {

    val builder = StringBuilder()

    var level = 0

    override fun visitElement(node: UElement): Boolean {
        val initialLine = node.asLogString() + " [" + run {
            val renderString = node.asRenderString().lines()
            if (renderString.size == 1) {
                renderString.single()
            } else {
                renderString.first() + "..." + renderString.last()
            }
        } + "]"

        (1..level).forEach { builder.append("    ") }
        builder.append(initialLine)
        if (node is UResolvable) {
            val resolved = node.resolve()
            builder.append(" => ")
            if (resolved != null) {
                builder.append(resolved)
            } else {
                builder.append("<FAILED>")
            }
        }
        builder.appendln()
        level++
        return false
    }

    override fun afterVisitElement(node: UElement) {
        level--
    }

    override fun toString() = builder.toString().replace("\r", "")
}
