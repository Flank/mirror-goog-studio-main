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

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.intellij.openapi.util.Disposer
import junit.framework.TestCase
import org.jetbrains.uast.UFile

// Misc tests to verify type handling in the Kotlin UAST initialization.
class UastTest : TestCase() {
    private fun check(source: TestFile, check: (UFile) -> Unit) {
        val pair = LintUtilsTest.parse(source)
        val uastFile = pair.first.uastFile
        assertNotNull(uastFile)
        check(uastFile!!)
        Disposer.dispose(pair.second)
    }

    fun testKt25298() {
        // Regression test for
        // 	KT-25298 UAST: NPE ClsFileImpl.getMirror during lambda inference session
        val source = java(
            """
            package test.pkg;
            import java.util.concurrent.Executors;
            import java.util.concurrent.ScheduledExecutorService;
            import java.util.concurrent.TimeUnit;
            public class MyTestCase
            {
                private final ScheduledExecutorService mExecutorService;
                public MyTestCase()
                {
                    mExecutorService = Executors.newSingleThreadScheduledExecutor();
                }
                public void foo()
                {
                    mExecutorService.schedule(this::initBar, 10, TimeUnit.SECONDS);
                }
                private boolean initBar()
                {
                    //...
                    return true;
                }
            }"""
        ).indented()

        check(source, { file ->
            assertEquals(
                "" +
                        "UFile (package = test.pkg) [package test.pkg...]\n" +
                        "    UImportStatement (isOnDemand = false) [import java.util.concurrent.Executors]\n" +
                        "    UImportStatement (isOnDemand = false) [import java.util.concurrent.ScheduledExecutorService]\n" +
                        "    UImportStatement (isOnDemand = false) [import java.util.concurrent.TimeUnit]\n" +
                        "    UClass (name = MyTestCase) [public class MyTestCase {...}]\n" +
                        "        UField (name = mExecutorService) [private final var mExecutorService: java.util.concurrent.ScheduledExecutorService]\n" +
                        "        UMethod (name = MyTestCase) [public fun MyTestCase() {...}]\n" +
                        "            UBlockExpression [{...}]\n" +
                        "                UBinaryExpression (operator = =) [mExecutorService = Executors.newSingleThreadScheduledExecutor()] : PsiType:ScheduledExecutorService\n" +
                        "                    USimpleNameReferenceExpression (identifier = mExecutorService) [mExecutorService] : PsiType:ScheduledExecutorService\n" +
                        "                    UQualifiedReferenceExpression [Executors.newSingleThreadScheduledExecutor()] : PsiType:ScheduledExecutorService\n" +
                        "                        USimpleNameReferenceExpression (identifier = Executors) [Executors]\n" +
                        "                        UCallExpression (kind = UastCallKind(name='method_call'), argCount = 0)) [newSingleThreadScheduledExecutor()] : PsiType:ScheduledExecutorService\n" +
                        "                            UIdentifier (Identifier (newSingleThreadScheduledExecutor)) [UIdentifier (Identifier (newSingleThreadScheduledExecutor))]\n" +
                        "        UMethod (name = foo) [public fun foo() : void {...}]\n" +
                        "            UBlockExpression [{...}]\n" +
                        "                UQualifiedReferenceExpression [mExecutorService.schedule(this::initBar, 10, TimeUnit.SECONDS)]\n" +
                        "                    USimpleNameReferenceExpression (identifier = mExecutorService) [mExecutorService] : PsiType:ScheduledExecutorService\n" +
                        "                    UCallExpression (kind = UastCallKind(name='method_call'), argCount = 3)) [schedule(this::initBar, 10, TimeUnit.SECONDS)]\n" +
                        "                        UIdentifier (Identifier (schedule)) [UIdentifier (Identifier (schedule))]\n" +
                        "                        UCallableReferenceExpression (name = initBar) [this::initBar] : PsiType:<method reference>\n" +
                        "                            UThisExpression (label = null) [this] : PsiType:MyTestCase\n" +
                        "                        ULiteralExpression (value = 10) [10] : PsiType:int\n" +
                        "                        UQualifiedReferenceExpression [TimeUnit.SECONDS] : PsiType:TimeUnit\n" +
                        "                            USimpleNameReferenceExpression (identifier = TimeUnit) [TimeUnit]\n" +
                        "                            USimpleNameReferenceExpression (identifier = SECONDS) [SECONDS]\n" +
                        "        UMethod (name = initBar) [private fun initBar() : boolean {...}]\n" +
                        "            UBlockExpression [{...}]\n" +
                        "                UReturnExpression [return true]\n" +
                        "                    ULiteralExpression (value = true) [true] : PsiType:boolean\n",
                file.asLogTypes()
            )
        })
    }

    fun test13Features() {
        check(
            kotlin(
                """
                package test.pkg

                // Assignment in when
                fun test(s: String) =
                        when (val something = s.hashCode()) {
                            is Int -> println(something)
                            else -> ""
                        }

                interface FooInterface {
                    companion object {
                        @JvmField
                        val answer: Int = 42

                        @JvmStatic
                        fun sayHello() {
                            println("Hello, world!")
                        }
                    }
                }

                // Nested declarations in annotation classes
                annotation class FooAnnotation {
                    enum class Direction { UP, DOWN, LEFT, RIGHT }

                    annotation class Bar

                    companion object {
                        fun foo(): Int = 42
                        val bar: Int = 42
                    }
                }

                // Inline classes
                inline class Name(val s: String)

                // Unsigned
                // You can define unsigned types using literal suffixes
                val uint = 42u
                val ulong = 42uL
                val ubyte: UByte = 255u


                // @JvmDefault
                interface FooInterface2 {
                    // Will be generated as 'default' method
                    @JvmDefault
                    fun foo(): Int = 42
                }
                """
            ).indented(),
            { file ->
                assertEquals(
                    "" +
                            "UFile (package = test.pkg) [package test.pkg...]\n" +
                            "    UClass (name = FooInterfaceKt) [public final class FooInterfaceKt {...}]\n" +
                            "        UField (name = uint) [@org.jetbrains.annotations.NotNull private static final var uint: int = 42]\n" +
                            "            UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]\n" +
                            "            ULiteralExpression (value = 42) [42] : PsiType:int\n" +
                            "        UField (name = ulong) [@org.jetbrains.annotations.NotNull private static final var ulong: long = 42]\n" +
                            "            UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]\n" +
                            "            ULiteralExpression (value = 42) [42] : PsiType:long\n" +
                            "        UField (name = ubyte) [@org.jetbrains.annotations.NotNull private static final var ubyte: byte = -1]\n" +
                            "            UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]\n" +
                            "            ULiteralExpression (value = -1) [-1] : PsiType:byte\n" +
                            "        UAnnotationMethod (name = test) [public static final fun test(@org.jetbrains.annotations.NotNull s: java.lang.String) : java.lang.Object {...}]\n" +
                            "            UParameter (name = s) [@org.jetbrains.annotations.NotNull var s: java.lang.String]\n" +
                            "                UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]\n" +
                            "            UBlockExpression [{...}]\n" +
                            "                UReturnExpression [return switch (var something: int = s.hashCode())  {...]\n" +
                            "                    USwitchExpression [switch (var something: int = s.hashCode())  {...] : PsiType:Object\n" +
                            "                        UDeclarationsExpression [var something: int = s.hashCode()]\n" +
                            "                            ULocalVariable (name = something) [var something: int = s.hashCode()]\n" +
                            "                                UQualifiedReferenceExpression [s.hashCode()] : PsiType:int\n" +
                            "                                    USimpleNameReferenceExpression (identifier = s) [s] : PsiType:String\n" +
                            "                                    UCallExpression (kind = UastCallKind(name='method_call'), argCount = 0)) [hashCode()] : PsiType:int\n" +
                            "                                        UIdentifier (Identifier (hashCode)) [UIdentifier (Identifier (hashCode))]\n" +
                            "                                        USimpleNameReferenceExpression (identifier = hashCode) [hashCode] : PsiType:int\n" +
                            "                        UExpressionList (when) [    it is java.lang.Integer -> {...    ] : PsiType:Object\n" +
                            "                            USwitchClauseExpressionWithBody [it is java.lang.Integer -> {...]\n" +
                            "                                UBinaryExpressionWithType [it is java.lang.Integer]\n" +
                            "                                    USimpleNameReferenceExpression (identifier = it) [it]\n" +
                            "                                    UTypeReferenceExpression (name = java.lang.Integer) [java.lang.Integer]\n" +
                            "                                UExpressionList (when_entry) [{...]\n" +
                            "                                    UCallExpression (kind = UastCallKind(name='method_call'), argCount = 1)) [println(something)] : PsiType:void\n" +
                            "                                        UIdentifier (Identifier (println)) [UIdentifier (Identifier (println))]\n" +
                            "                                        USimpleNameReferenceExpression (identifier = println) [println] : PsiType:void\n" +
                            "                                        USimpleNameReferenceExpression (identifier = something) [something] : PsiType:int\n" +
                            "                                    UBreakExpression (label = null) [break]\n" +
                            "                            USwitchClauseExpressionWithBody [ -> {...]\n" +
                            "                                UExpressionList (when_entry) [{...]\n" +
                            "                                    ULiteralExpression (value = \"\") [\"\"] : PsiType:String\n" +
                            "                                    UBreakExpression (label = null) [break]\n" +
                            "        UAnnotationMethod (name = getUint) [public static final fun getUint() : int = UastEmptyExpression]\n" +
                            "        UAnnotationMethod (name = getUlong) [public static final fun getUlong() : long = UastEmptyExpression]\n" +
                            "        UAnnotationMethod (name = getUbyte) [public static final fun getUbyte() : byte = UastEmptyExpression]\n" +
                            "    UClass (name = FooInterface) [public abstract interface FooInterface {...}]\n" +
                            "        UField (name = answer) [@org.jetbrains.annotations.NotNull @kotlin.jvm.JvmField public static final var answer: int = 42]\n" +
                            "            UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]\n" +
                            "            UAnnotation (fqName = kotlin.jvm.JvmField) [@kotlin.jvm.JvmField]\n" +
                            "            ULiteralExpression (value = 42) [42] : PsiType:int\n" +
                            "        UField (name = Companion) [@null public static final var Companion: test.pkg.FooInterface.Companion]\n" +
                            "            UAnnotation (fqName = null) [@null]\n" +
                            "        UAnnotationMethod (name = sayHello) [@kotlin.jvm.JvmStatic...}]\n" +
                            "            UAnnotation (fqName = kotlin.jvm.JvmStatic) [@kotlin.jvm.JvmStatic]\n" +
                            "            UBlockExpression [{...}] : PsiType:void\n" +
                            "                UCallExpression (kind = UastCallKind(name='method_call'), argCount = 1)) [println(\"Hello, world!\")] : PsiType:void\n" +
                            "                    UIdentifier (Identifier (println)) [UIdentifier (Identifier (println))]\n" +
                            "                    USimpleNameReferenceExpression (identifier = println) [println] : PsiType:void\n" +
                            "                    ULiteralExpression (value = \"Hello, world!\") [\"Hello, world!\"] : PsiType:String\n" +
                            "        UClass (name = Companion) [public static final class Companion {...}]\n" +
                            "            UAnnotationMethod (name = sayHello) [@kotlin.jvm.JvmStatic...}]\n" +
                            "                UAnnotation (fqName = kotlin.jvm.JvmStatic) [@kotlin.jvm.JvmStatic]\n" +
                            "                UBlockExpression [{...}] : PsiType:void\n" +
                            "                    UCallExpression (kind = UastCallKind(name='method_call'), argCount = 1)) [println(\"Hello, world!\")] : PsiType:void\n" +
                            "                        UIdentifier (Identifier (println)) [UIdentifier (Identifier (println))]\n" +
                            "                        USimpleNameReferenceExpression (identifier = println) [println] : PsiType:void\n" +
                            "                        ULiteralExpression (value = \"Hello, world!\") [\"Hello, world!\"] : PsiType:String\n" +
                            "            UAnnotationMethod (name = Companion) [private fun Companion() = UastEmptyExpression]\n" +
                            "    UClass (name = FooAnnotation) [public abstract annotation FooAnnotation {...}]\n" +
                            "        UField (name = Companion) [@null public static final var Companion: test.pkg.FooAnnotation.Companion]\n" +
                            "            UAnnotation (fqName = null) [@null]\n" +
                            "        UClass (name = Direction) [public static enum Direction {...}]\n" +
                            "            UEnumConstant (name = UP) [@null UP]\n" +
                            "                UAnnotation (fqName = null) [@null]\n" +
                            "                USimpleNameReferenceExpression (identifier = Direction) [Direction]\n" +
                            "            UEnumConstant (name = DOWN) [@null DOWN]\n" +
                            "                UAnnotation (fqName = null) [@null]\n" +
                            "                USimpleNameReferenceExpression (identifier = Direction) [Direction]\n" +
                            "            UEnumConstant (name = LEFT) [@null LEFT]\n" +
                            "                UAnnotation (fqName = null) [@null]\n" +
                            "                USimpleNameReferenceExpression (identifier = Direction) [Direction]\n" +
                            "            UEnumConstant (name = RIGHT) [@null RIGHT]\n" +
                            "                UAnnotation (fqName = null) [@null]\n" +
                            "                USimpleNameReferenceExpression (identifier = Direction) [Direction]\n" +
                            "            UAnnotationMethod (name = Direction) [private fun Direction() = UastEmptyExpression]\n" +
                            "        UClass (name = Bar) [public static abstract annotation Bar {...}]\n" +
                            "        UClass (name = Companion) [public static final class Companion {...}]\n" +
                            "            UField (name = bar) [@org.jetbrains.annotations.NotNull private static final var bar: int = 42]\n" +
                            "                UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]\n" +
                            "                ULiteralExpression (value = 42) [42] : PsiType:int\n" +
                            "            UAnnotationMethod (name = foo) [public final fun foo() : int {...}]\n" +
                            "                UBlockExpression [{...}]\n" +
                            "                    UReturnExpression [return 42]\n" +
                            "                        ULiteralExpression (value = 42) [42] : PsiType:int\n" +
                            "            UAnnotationMethod (name = getBar) [public final fun getBar() : int = UastEmptyExpression]\n" +
                            "            UAnnotationMethod (name = Companion) [private fun Companion() = UastEmptyExpression]\n" +
                            "    UClass (name = Name) [public final class Name {...}]\n" +
                            "        UField (name = s) [@org.jetbrains.annotations.NotNull private final var s: java.lang.String]\n" +
                            "            UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]\n" +
                            "        UAnnotationMethod (name = getS) [public final fun getS() : java.lang.String = UastEmptyExpression]\n" +
                            "        UAnnotationMethod (name = constructor-impl) [public static fun constructor-impl(@org.jetbrains.annotations.NotNull s: java.lang.String) : java.lang.String = UastEmptyExpression]\n" +
                            "            UParameter (name = s) [@org.jetbrains.annotations.NotNull var s: java.lang.String]\n" +
                            "                UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]\n" +
                            "        UAnnotationMethod (name = toString-impl) [public static fun toString-impl(@null p: java.lang.String) : java.lang.String = UastEmptyExpression]\n" +
                            "            UParameter (name = p) [@null var p: java.lang.String]\n" +
                            "                UAnnotation (fqName = null) [@null]\n" +
                            "        UAnnotationMethod (name = hashCode-impl) [public static fun hashCode-impl(@null p: java.lang.String) : int = UastEmptyExpression]\n" +
                            "            UParameter (name = p) [@null var p: java.lang.String]\n" +
                            "                UAnnotation (fqName = null) [@null]\n" +
                            "        UAnnotationMethod (name = equals-impl) [public static fun equals-impl(@null p: java.lang.String, @org.jetbrains.annotations.Nullable p1: java.lang.Object) : boolean = UastEmptyExpression]\n" +
                            "            UParameter (name = p) [@null var p: java.lang.String]\n" +
                            "                UAnnotation (fqName = null) [@null]\n" +
                            "            UParameter (name = p1) [@org.jetbrains.annotations.Nullable var p1: java.lang.Object]\n" +
                            "                UAnnotation (fqName = org.jetbrains.annotations.Nullable) [@org.jetbrains.annotations.Nullable]\n" +
                            "        UAnnotationMethod (name = equals-impl0) [public static final fun equals-impl0(@org.jetbrains.annotations.NotNull p1: java.lang.String, @org.jetbrains.annotations.NotNull p2: java.lang.String) : boolean = UastEmptyExpression]\n" +
                            "            UParameter (name = p1) [@org.jetbrains.annotations.NotNull var p1: java.lang.String]\n" +
                            "                UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]\n" +
                            "            UParameter (name = p2) [@org.jetbrains.annotations.NotNull var p2: java.lang.String]\n" +
                            "                UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]\n" +
                            "        UAnnotationMethod (name = toString) [public fun toString() : java.lang.String = UastEmptyExpression]\n" +
                            "        UAnnotationMethod (name = hashCode) [public fun hashCode() : int = UastEmptyExpression]\n" +
                            "        UAnnotationMethod (name = equals) [public fun equals(@null p: java.lang.Object) : boolean = UastEmptyExpression]\n" +
                            "            UParameter (name = p) [@null var p: java.lang.Object]\n" +
                            "                UAnnotation (fqName = null) [@null]\n" +
                            "    UClass (name = FooInterface2) [public abstract interface FooInterface2 {...}]\n" +
                            "        UAnnotationMethod (name = foo) [@kotlin.jvm.JvmDefault...}]\n" +
                            "            UAnnotation (fqName = kotlin.jvm.JvmDefault) [@kotlin.jvm.JvmDefault]\n" +
                            "            UBlockExpression [{...}]\n" +
                            "                UReturnExpression [return 42]\n" +
                            "                    ULiteralExpression (value = 42) [42] : PsiType:int\n",
                    file.asLogTypes()
                )
            })
    }
}