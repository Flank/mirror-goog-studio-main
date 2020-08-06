/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.intellij.openapi.util.Disposer
import junit.framework.TestCase
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UFile
import org.jetbrains.uast.asRecursiveLogString
import org.jetbrains.uast.visitor.AbstractUastVisitor
import java.io.File

// Misc tests to verify type handling in the Kotlin UAST initialization.
class TypesTest : TestCase() {
    fun testPrimitiveKotlinTypes() {
        val pair = LintUtilsTest.parseKotlin(
            "" +
                "class Kotlin(val property1: String = \"Default Value\", arg2: Int) : Parent() {\n" +
                "    override fun method() = \"Hello World\"\n" +
                "    fun otherMethod(ok: Boolean, times: Int) {\n" +
                "    }\n" +
                "\n" +
                "    var property2: String? = null\n" +
                "\n" +
                "    private var someField = 42\n" +
                "    @JvmField\n" +
                "    var someField2 = 42\n" +
                "}\n" +
                "\n" +
                "open class Parent {\n" +
                "    open fun method(): String? = null\n" +
                "    open fun method2(value: Boolean, value: Boolean?): String? = null\n" +
                "    open fun method3(value: Int?, value2: Int): Int = null\n" +
                "}\n",
            File("src/test/pkg/Tor.kt")
        )

        val file = pair.first.uastFile
        assertEquals(
            "" +
                "UFile (package = ) [public final class Kotlin : Parent {...]\n" +
                "    UClass (name = Kotlin) [public final class Kotlin : Parent {...}]\n" +
                "        UField (name = property2) [@org.jetbrains.annotations.Nullable private var property2: java.lang.String = null]\n" +
                "            UAnnotation (fqName = org.jetbrains.annotations.Nullable) [@org.jetbrains.annotations.Nullable]\n" +
                "            ULiteralExpression (value = null) [null] : PsiType:Void\n" +
                "        UField (name = someField) [@org.jetbrains.annotations.NotNull private var someField: int = 42]\n" +
                "            UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]\n" +
                "            ULiteralExpression (value = 42) [42] : PsiType:int\n" +
                "        UField (name = someField2) [@org.jetbrains.annotations.NotNull @kotlin.jvm.JvmField public var someField2: int = 42]\n" +
                "            UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]\n" +
                "            UAnnotation (fqName = kotlin.jvm.JvmField) [@kotlin.jvm.JvmField]\n" +
                "            ULiteralExpression (value = 42) [42] : PsiType:int\n" +
                "        UField (name = property1) [@org.jetbrains.annotations.NotNull private final var property1: java.lang.String = \"Default Value\"]\n" +
                "            UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]\n" +
                "            ULiteralExpression (value = \"Default Value\") [\"Default Value\"] : PsiType:String\n" +
                "        UMethod (name = method) [public fun method() : java.lang.String {...}]\n" +
                "            UBlockExpression [{...}]\n" +
                "                UReturnExpression [return \"Hello World\"]\n" +
                "                    ULiteralExpression (value = \"Hello World\") [\"Hello World\"] : PsiType:String\n" +
                "        UMethod (name = otherMethod) [public final fun otherMethod(@org.jetbrains.annotations.NotNull ok: boolean, @org.jetbrains.annotations.NotNull times: int) : void {...}]\n" +
                "            UParameter (name = ok) [@org.jetbrains.annotations.NotNull var ok: boolean]\n" +
                "                UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]\n" +
                "            UParameter (name = times) [@org.jetbrains.annotations.NotNull var times: int]\n" +
                "                UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]\n" +
                "            UBlockExpression [{...}] : PsiType:void\n" +
                "        UMethod (name = getProperty2) [public final fun getProperty2() : java.lang.String = UastEmptyExpression]\n" +
                "        UMethod (name = setProperty2) [public final fun setProperty2(@org.jetbrains.annotations.Nullable p: java.lang.String) : void = UastEmptyExpression]\n" +
                "            UParameter (name = p) [@org.jetbrains.annotations.Nullable var p: java.lang.String]\n" +
                "                UAnnotation (fqName = org.jetbrains.annotations.Nullable) [@org.jetbrains.annotations.Nullable]\n" +
                "        UMethod (name = getProperty1) [public final fun getProperty1() : java.lang.String = UastEmptyExpression]\n" +
                "        UMethod (name = Kotlin) [public fun Kotlin(@org.jetbrains.annotations.NotNull property1: java.lang.String, @org.jetbrains.annotations.NotNull arg2: int) {...}]\n" +
                "            UParameter (name = property1) [@org.jetbrains.annotations.NotNull var property1: java.lang.String = \"Default Value\"]\n" +
                "                UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]\n" +
                "                ULiteralExpression (value = \"Default Value\") [\"Default Value\"] : PsiType:String\n" +
                "            UParameter (name = arg2) [@org.jetbrains.annotations.NotNull var arg2: int]\n" +
                "                UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]\n" +
                "            UBlockExpression [{...}]\n" +
                "                UCallExpression (kind = UastCallKind(name='constructor_call'), argCount = 0)) [<init>()]\n" +
                "                    UIdentifier (Identifier (Parent)) [UIdentifier (Identifier (Parent))]\n" +
                "                    USimpleNameReferenceExpression (identifier = <init>, resolvesTo = Parent) [<init>]\n" +
                "    UClass (name = Parent) [public class Parent {...}]\n" +
                "        UMethod (name = method) [public fun method() : java.lang.String {...}]\n" +
                "            UBlockExpression [{...}]\n" +
                "                UReturnExpression [return null]\n" +
                "                    ULiteralExpression (value = null) [null] : PsiType:Void\n" +
                "        UMethod (name = method2) [public fun method2(@org.jetbrains.annotations.NotNull value: boolean, @org.jetbrains.annotations.Nullable value: java.lang.Boolean) : java.lang.String {...}]\n" +
                "            UParameter (name = value) [@org.jetbrains.annotations.NotNull var value: boolean]\n" +
                "                UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]\n" +
                "            UParameter (name = value) [@org.jetbrains.annotations.Nullable var value: java.lang.Boolean]\n" +
                "                UAnnotation (fqName = org.jetbrains.annotations.Nullable) [@org.jetbrains.annotations.Nullable]\n" +
                "            UBlockExpression [{...}]\n" +
                "                UReturnExpression [return null]\n" +
                "                    ULiteralExpression (value = null) [null] : PsiType:Void\n" +
                "        UMethod (name = method3) [public fun method3(@org.jetbrains.annotations.Nullable value: java.lang.Integer, @org.jetbrains.annotations.NotNull value2: int) : int {...}]\n" +
                "            UParameter (name = value) [@org.jetbrains.annotations.Nullable var value: java.lang.Integer]\n" +
                "                UAnnotation (fqName = org.jetbrains.annotations.Nullable) [@org.jetbrains.annotations.Nullable]\n" +
                "            UParameter (name = value2) [@org.jetbrains.annotations.NotNull var value2: int]\n" +
                "                UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]\n" +
                "            UBlockExpression [{...}]\n" +
                "                UReturnExpression [return null]\n" +
                "                    ULiteralExpression (value = null) [null] : PsiType:Void\n" +
                "        UMethod (name = Parent) [public fun Parent() = UastEmptyExpression]\n",
            file?.asLogTypes()
        )

        assertEquals(
            """
            UFile (package = )
                UClass (name = Kotlin)
                    UField (name = property2)
                        UAnnotation (fqName = org.jetbrains.annotations.Nullable)
                        ULiteralExpression (value = null)
                    UField (name = someField)
                        UAnnotation (fqName = org.jetbrains.annotations.NotNull)
                        ULiteralExpression (value = 42)
                    UField (name = someField2)
                        UAnnotation (fqName = org.jetbrains.annotations.NotNull)
                        UAnnotation (fqName = kotlin.jvm.JvmField)
                        ULiteralExpression (value = 42)
                    UField (name = property1)
                        UAnnotation (fqName = org.jetbrains.annotations.NotNull)
                        ULiteralExpression (value = "Default Value")
                    UMethod (name = method)
                        UBlockExpression
                            UReturnExpression
                                ULiteralExpression (value = "Hello World")
                    UMethod (name = otherMethod)
                        UParameter (name = ok)
                            UAnnotation (fqName = org.jetbrains.annotations.NotNull)
                        UParameter (name = times)
                            UAnnotation (fqName = org.jetbrains.annotations.NotNull)
                        UBlockExpression
                    UMethod (name = getProperty2)
                    UMethod (name = setProperty2)
                        UParameter (name = p)
                            UAnnotation (fqName = org.jetbrains.annotations.Nullable)
                    UMethod (name = getProperty1)
                    UMethod (name = Kotlin)
                        UParameter (name = property1)
                            UAnnotation (fqName = org.jetbrains.annotations.NotNull)
                            ULiteralExpression (value = "Default Value")
                        UParameter (name = arg2)
                            UAnnotation (fqName = org.jetbrains.annotations.NotNull)
                        UBlockExpression
                            UCallExpression (kind = UastCallKind(name='constructor_call'), argCount = 0))
                                UIdentifier (Identifier (Parent))
                                USimpleNameReferenceExpression (identifier = <init>, resolvesTo = Parent)
                UClass (name = Parent)
                    UMethod (name = method)
                        UBlockExpression
                            UReturnExpression
                                ULiteralExpression (value = null)
                    UMethod (name = method2)
                        UParameter (name = value)
                            UAnnotation (fqName = org.jetbrains.annotations.NotNull)
                        UParameter (name = value)
                            UAnnotation (fqName = org.jetbrains.annotations.Nullable)
                        UBlockExpression
                            UReturnExpression
                                ULiteralExpression (value = null)
                    UMethod (name = method3)
                        UParameter (name = value)
                            UAnnotation (fqName = org.jetbrains.annotations.Nullable)
                        UParameter (name = value2)
                            UAnnotation (fqName = org.jetbrains.annotations.NotNull)
                        UBlockExpression
                            UReturnExpression
                                ULiteralExpression (value = null)
                    UMethod (name = Parent)

            """.trimIndent(),
            file?.asRecursiveLogString()?.replace("\r", "")
        )
        Disposer.dispose(pair.second)
    }

    fun testPrimitiveKotlinTypes2() {
        val pair = LintUtilsTest.parseKotlin(
            "" +
                "package test.pkg\n" +
                "\n" +
                "fun calc(@java.lang.Override x: Int, y: Int?, z: String?): Int = x * 2",
            File("src/test/pkg/test.kt")
        )

        val file = pair.first.uastFile
        assertEquals(
            "" +
                "UFile (package = test.pkg) [package test.pkg...]\n" +
                "    UClass (name = TestKt) [public final class TestKt {...}]\n" +
                "        UMethod (name = calc) [public static final fun calc(@org.jetbrains.annotations.NotNull @java.lang.Override x: int, @org.jetbrains.annotations.Nullable y: java.lang.Integer, @org.jetbrains.annotations.Nullable z: java.lang.String) : int {...}]\n" +
                "            UParameter (name = x) [@org.jetbrains.annotations.NotNull @java.lang.Override var x: int]\n" +
                "                UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]\n" +
                "                UAnnotation (fqName = java.lang.Override) [@java.lang.Override]\n" +
                "            UParameter (name = y) [@org.jetbrains.annotations.Nullable var y: java.lang.Integer]\n" +
                "                UAnnotation (fqName = org.jetbrains.annotations.Nullable) [@org.jetbrains.annotations.Nullable]\n" +
                "            UParameter (name = z) [@org.jetbrains.annotations.Nullable var z: java.lang.String]\n" +
                "                UAnnotation (fqName = org.jetbrains.annotations.Nullable) [@org.jetbrains.annotations.Nullable]\n" +
                "            UBlockExpression [{...}]\n" +
                "                UReturnExpression [return x * 2]\n" +
                "                    UBinaryExpression (operator = *) [x * 2] : PsiType:int\n" +
                "                        USimpleNameReferenceExpression (identifier = x) [x] : PsiType:int\n" +
                "                        ULiteralExpression (value = 2) [2] : PsiType:int\n",
            file?.asLogTypes()
        )
        Disposer.dispose(pair.second)
    }

    fun testSecondaryConstructorBodies() {
        // Regression test for https://youtrack.jetbrains.com/issue/KT-21575
        val pair = LintUtilsTest.parseKotlin(
            "" +
                "class Foo() {\n" +
                "    constructor(number: Int) : this() {\n" +
                "        sideeffect(number)\n" +
                "    }\n" +
                "}\n" +
                "\n" +
                "fun sideeffect(number: Int) {\n" +
                "    println(number)\n" +
                "}\n" +
                "\n" +
                "\n" +
                "fun main(args: Array<String>) {\n" +
                "    Foo()\n" +
                "    Foo(5)\n" +
                "}",
            File("src/test/pkg/test.kt")
        )

        val file = pair.first.uastFile

        assertEquals(
            "" +
                "public final class TestKt {\n" +
                "    public static final fun sideeffect(@org.jetbrains.annotations.NotNull number: int) : void {\n" +
                "        println(number)\n" +
                "    }\n" +
                "    public static final fun main(@org.jetbrains.annotations.NotNull args: java.lang.String[]) : void {\n" +
                "        <init>()\n" +
                "        <init>(5)\n" +
                "    }\n" +
                "}\n" +
                "\n" +
                "public final class Foo {\n" +
                "    public fun Foo() = UastEmptyExpression\n" +
                "    public fun Foo(@org.jetbrains.annotations.NotNull number: int) {\n" +
                "        <init>()\n" +
                "        sideeffect(number)\n" +
                "    }\n" +
                "}\n",
            file?.asRenderString()?.replace("\r", "")
        )
        Disposer.dispose(pair.second)
    }

    fun testPrimitiveKotlinTypes3() {
        val pair = LintUtilsTest.parseKotlin(
            "" +
                "open class Parent(val number: Int) {\n" +
                "  fun test(): Int = 6" +
                "}\n" +
                "\n" +
                "class Five : Parent(5)",
            File("src/test/pkg/test.kt")
        )

        val file = pair.first.uastFile

        assertEquals(
            "" +
                "UFile (package = ) [public class Parent {...]\n" +
                "    UClass (name = Parent) [public class Parent {...}]\n" +
                "        UField (name = number) [@org.jetbrains.annotations.NotNull private final var number: int]\n" +
                "            UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]\n" +
                "        UMethod (name = test) [public final fun test() : int {...}]\n" +
                "            UBlockExpression [{...}]\n" +
                "                UReturnExpression [return 6]\n" +
                "                    ULiteralExpression (value = 6) [6] : PsiType:int\n" +
                "        UMethod (name = getNumber) [public final fun getNumber() : int = UastEmptyExpression]\n" +
                "        UMethod (name = Parent) [public fun Parent(@org.jetbrains.annotations.NotNull number: int) = UastEmptyExpression]\n" +
                "            UParameter (name = number) [@org.jetbrains.annotations.NotNull var number: int]\n" +
                "                UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]\n" +
                "    UClass (name = Five) [public final class Five : Parent {...}]\n" +
                "        UMethod (name = Five) [public fun Five() {...}]\n" +
                "            UBlockExpression [{...}]\n" +
                "                UCallExpression (kind = UastCallKind(name='constructor_call'), argCount = 1)) [<init>(5)]\n" +
                "                    UIdentifier (Identifier (Parent)) [UIdentifier (Identifier (Parent))]\n" +
                "                    USimpleNameReferenceExpression (identifier = <init>, resolvesTo = Parent) [<init>]\n" +
                "                    ULiteralExpression (value = 5) [5] : PsiType:int\n",
            file?.asLogTypes()
        )
        Disposer.dispose(pair.second)
    }

    fun testJavaEnums() {
        // Regression test for https://youtrack.jetbrains.com/oauth?state=%2Fissue%2FKT-23456
        val pair = LintUtilsTest.parse(
            "" +
                "package test.pkg;\n" +
                "public enum JavaEnum {\n" +
                "    FOO(1), BAR(2), BAZ(3);\n" +
                "\n" +
                "    JavaEnum(int arg) {\n" +
                "    }\n" +
                "}",
            File("src/test/pkg/JavaEnum.java")
        )

        val file = pair.first.uastFile

        assertEquals(
            "" +
                "UFile (package = test.pkg) [package test.pkg...]\n" +
                "    UClass (name = JavaEnum) [public final enum JavaEnum {...}]\n" +
                "        UEnumConstant (name = FOO) [FOO(1)]\n" +
                "            USimpleNameReferenceExpression (identifier = JavaEnum) [JavaEnum]\n" +
                "            ULiteralExpression (value = 1) [1] : PsiType:int\n" +
                "        UEnumConstant (name = BAR) [BAR(2)]\n" +
                "            USimpleNameReferenceExpression (identifier = JavaEnum) [JavaEnum]\n" +
                "            ULiteralExpression (value = 2) [2] : PsiType:int\n" +
                "        UEnumConstant (name = BAZ) [BAZ(3)]\n" +
                "            USimpleNameReferenceExpression (identifier = JavaEnum) [JavaEnum]\n" +
                "            ULiteralExpression (value = 3) [3] : PsiType:int\n" +
                "        UMethod (name = JavaEnum) [private fun JavaEnum(arg: int) {...}]\n" +
                "            UParameter (name = arg) [var arg: int]\n" +
                "            UBlockExpression [{...}]\n" +
                "        UMethod (name = values) [public static fun values() : test.pkg.JavaEnum[] {...}]\n" +
                "            UBlockExpression [{...}]\n" +
                "        UMethod (name = valueOf) [public static fun valueOf(name: java.lang.String) : test.pkg.JavaEnum {...}]\n" +
                "            UParameter (name = name) [var name: java.lang.String]\n" +
                "            UBlockExpression [{...}]\n",
            file?.asLogTypes()
        )
        Disposer.dispose(pair.second)
    }

    fun testKotlinEnums() {
        // Regression test for https://youtrack.jetbrains.com/oauth?state=%2Fissue%2FKT-23456
        val pair = LintUtilsTest.parseKotlin(
            "" +
                "package test.pkg\n" +
                "enum class KotlinEnum(val resId: Int) {\n" +
                " FOO(1), BAR(2), BAZ(3) \n" +
                "}",
            File("src/test/pkg/KotlinEnum.kt")
        )

        val file = pair.first.uastFile

        assertEquals(
            "" +
                "UFile (package = test.pkg) [package test.pkg...]\n" +
                "    UClass (name = KotlinEnum) [public enum KotlinEnum {...}]\n" +
                "        UEnumConstant (name = FOO) [@null FOO(1)]\n" +
                "            UAnnotation (fqName = null) [@null]\n" +
                "            USimpleNameReferenceExpression (identifier = KotlinEnum) [KotlinEnum]\n" +
                "            ULiteralExpression (value = 1) [1] : PsiType:int\n" +
                "        UEnumConstant (name = BAR) [@null BAR(2)]\n" +
                "            UAnnotation (fqName = null) [@null]\n" +
                "            USimpleNameReferenceExpression (identifier = KotlinEnum) [KotlinEnum]\n" +
                "            ULiteralExpression (value = 2) [2] : PsiType:int\n" +
                "        UEnumConstant (name = BAZ) [@null BAZ(3)]\n" +
                "            UAnnotation (fqName = null) [@null]\n" +
                "            USimpleNameReferenceExpression (identifier = KotlinEnum) [KotlinEnum]\n" +
                "            ULiteralExpression (value = 3) [3] : PsiType:int\n" +
                "        UField (name = resId) [@org.jetbrains.annotations.NotNull private final var resId: int]\n" +
                "            UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]\n" +
                "        UMethod (name = getResId) [public final fun getResId() : int = UastEmptyExpression]\n" +
                "        UMethod (name = KotlinEnum) [private fun KotlinEnum(@org.jetbrains.annotations.NotNull resId: int) = UastEmptyExpression]\n" +
                "            UParameter (name = resId) [@org.jetbrains.annotations.NotNull var resId: int]\n" +
                "                UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]\n" +
                "        UMethod (name = values) [public static fun values() = UastEmptyExpression]\n" +
                "        UMethod (name = valueOf) [public static fun valueOf(@null name: java.lang.String) = UastEmptyExpression]\n" +
                "            UParameter (name = name) [@null var name: java.lang.String]\n" +
                "                UAnnotation (fqName = null) [@null]\n",
            file?.asLogTypes()
        )
        Disposer.dispose(pair.second)
    }
}

// From Kotlin's UAST unit test support, TypesTestBase
fun UFile.asLogTypes(indent: String = "    ") = TypesLogger(indent).apply {
    this@asLogTypes.accept(this)
}.toString()

class TypesLogger(private val indent: String) : AbstractUastVisitor() {

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

        (1..level).forEach { builder.append(indent) }
        builder.append(initialLine)
        if (node is UExpression) {
            val value = node.getExpressionType()
            value?.let { builder.append(" : ").append(it) }
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
