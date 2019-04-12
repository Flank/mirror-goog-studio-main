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

    fun test123923544() {
        // Regression test for
        //  https://youtrack.jetbrains.com/issue/KT-30033
        // 	https://issuetracker.google.com/123923544
        val source = kotlin(
            """
            interface Base {
                fun print()
            }

            class BaseImpl(val x: Int) : Base {
                override fun print() { println(x) }
            }

            fun createBase(i: Int): Base {
                return BaseImpl(i)
            }

            class Derived(b: Base) : Base by createBase(10)
            """
        ).indented()

        check(source, { file ->
            assertEquals(
                """
                public final class BaseKt {
                    public static final fun createBase(@org.jetbrains.annotations.NotNull i: int) : Base {
                        return <init>(i)
                    }
                }

                public abstract interface Base {
                    public abstract fun print() : void = UastEmptyExpression
                }

                public final class BaseImpl : Base {
                    @org.jetbrains.annotations.NotNull private final var x: int
                    public fun print() : void {
                        println(x)
                    }
                    public final fun getX() : int = UastEmptyExpression
                    public fun BaseImpl(@org.jetbrains.annotations.NotNull x: int) = UastEmptyExpression
                }

                public final class Derived : Base {
                    public fun Derived(@org.jetbrains.annotations.NotNull b: Base) = UastEmptyExpression
                }

                """.trimIndent(), file.asSourceString())

            assertEquals(
                """
                UFile (package = ) [public final class BaseKt {...]
                    UClass (name = BaseKt) [public final class BaseKt {...}]
                        UAnnotationMethod (name = createBase) [public static final fun createBase(@org.jetbrains.annotations.NotNull i: int) : Base {...}]
                            UParameter (name = i) [@org.jetbrains.annotations.NotNull var i: int]
                                UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]
                            UBlockExpression [{...}] : PsiType:Void
                                UReturnExpression [return <init>(i)] : PsiType:Void
                                    UCallExpression (kind = UastCallKind(name='constructor_call'), argCount = 1)) [<init>(i)] : PsiType:BaseImpl
                                        UIdentifier (Identifier (BaseImpl)) [UIdentifier (Identifier (BaseImpl))]
                                        USimpleNameReferenceExpression (identifier = <init>, resolvesTo = BaseImpl) [<init>] : PsiType:BaseImpl
                                        USimpleNameReferenceExpression (identifier = i) [i] : PsiType:int
                    UClass (name = Base) [public abstract interface Base {...}]
                        UAnnotationMethod (name = print) [public abstract fun print() : void = UastEmptyExpression]
                    UClass (name = BaseImpl) [public final class BaseImpl : Base {...}]
                        UField (name = x) [@org.jetbrains.annotations.NotNull private final var x: int]
                            UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]
                        UAnnotationMethod (name = print) [public fun print() : void {...}]
                            UBlockExpression [{...}] : PsiType:void
                                UCallExpression (kind = UastCallKind(name='method_call'), argCount = 1)) [println(x)] : PsiType:void
                                    UIdentifier (Identifier (println)) [UIdentifier (Identifier (println))]
                                    USimpleNameReferenceExpression (identifier = println, resolvesTo = null) [println] : PsiType:void
                                    USimpleNameReferenceExpression (identifier = x) [x] : PsiType:int
                        UAnnotationMethod (name = getX) [public final fun getX() : int = UastEmptyExpression]
                        UAnnotationMethod (name = BaseImpl) [public fun BaseImpl(@org.jetbrains.annotations.NotNull x: int) = UastEmptyExpression]
                            UParameter (name = x) [@org.jetbrains.annotations.NotNull var x: int]
                                UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]
                    UClass (name = Derived) [public final class Derived : Base {...}]
                        UExpressionList (super_delegation) [super_delegation Base : createBase(10)]
                            UTypeReferenceExpression (name = Base) [Base]
                            UCallExpression (kind = UastCallKind(name='method_call'), argCount = 1)) [createBase(10)] : PsiType:Base
                                UIdentifier (Identifier (createBase)) [UIdentifier (Identifier (createBase))]
                                USimpleNameReferenceExpression (identifier = createBase, resolvesTo = null) [createBase] : PsiType:Base
                                ULiteralExpression (value = 10) [10] : PsiType:int
                        UAnnotationMethod (name = Derived) [public fun Derived(@org.jetbrains.annotations.NotNull b: Base) = UastEmptyExpression]
                            UParameter (name = b) [@org.jetbrains.annotations.NotNull var b: Base]
                                UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]

                """.trimIndent(),
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
                    """
                    UFile (package = test.pkg) [package test.pkg...]
                        UClass (name = FooInterfaceKt) [public final class FooInterfaceKt {...}]
                            UField (name = uint) [@org.jetbrains.annotations.NotNull private static final var uint: int = 42]
                                UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]
                                ULiteralExpression (value = 42) [42] : PsiType:int
                            UField (name = ulong) [@org.jetbrains.annotations.NotNull private static final var ulong: long = 42]
                                UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]
                                ULiteralExpression (value = 42) [42] : PsiType:long
                            UField (name = ubyte) [@org.jetbrains.annotations.NotNull private static final var ubyte: byte = -1]
                                UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]
                                ULiteralExpression (value = -1) [-1] : PsiType:byte
                            UAnnotationMethod (name = test) [public static final fun test(@org.jetbrains.annotations.NotNull s: java.lang.String) : java.lang.Object {...}]
                                UParameter (name = s) [@org.jetbrains.annotations.NotNull var s: java.lang.String]
                                    UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]
                                UBlockExpression [{...}]
                                    UReturnExpression [return switch (var something: int = s.hashCode())  {...]
                                        USwitchExpression [switch (var something: int = s.hashCode())  {...] : PsiType:Object
                                            UDeclarationsExpression [var something: int = s.hashCode()]
                                                ULocalVariable (name = something) [var something: int = s.hashCode()]
                                                    UQualifiedReferenceExpression [s.hashCode()] : PsiType:int
                                                        USimpleNameReferenceExpression (identifier = s) [s] : PsiType:String
                                                        UCallExpression (kind = UastCallKind(name='method_call'), argCount = 0)) [hashCode()] : PsiType:int
                                                            UIdentifier (Identifier (hashCode)) [UIdentifier (Identifier (hashCode))]
                                                            USimpleNameReferenceExpression (identifier = hashCode, resolvesTo = null) [hashCode] : PsiType:int
                                            UExpressionList (when) [    it is java.lang.Integer -> {...    ] : PsiType:Object
                                                USwitchClauseExpressionWithBody [it is java.lang.Integer -> {...]
                                                    UBinaryExpressionWithType [it is java.lang.Integer]
                                                        USimpleNameReferenceExpression (identifier = it) [it]
                                                        UTypeReferenceExpression (name = java.lang.Integer) [java.lang.Integer]
                                                    UExpressionList (when_entry) [{...]
                                                        UCallExpression (kind = UastCallKind(name='method_call'), argCount = 1)) [println(something)] : PsiType:void
                                                            UIdentifier (Identifier (println)) [UIdentifier (Identifier (println))]
                                                            USimpleNameReferenceExpression (identifier = println, resolvesTo = null) [println] : PsiType:void
                                                            USimpleNameReferenceExpression (identifier = something) [something] : PsiType:int
                                                        UBreakExpression (label = null) [break]
                                                USwitchClauseExpressionWithBody [ -> {...]
                                                    UExpressionList (when_entry) [{...]
                                                        ULiteralExpression (value = "") [""] : PsiType:String
                                                        UBreakExpression (label = null) [break]
                            UAnnotationMethod (name = getUint) [public static final fun getUint() : int = UastEmptyExpression]
                            UAnnotationMethod (name = getUlong) [public static final fun getUlong() : long = UastEmptyExpression]
                            UAnnotationMethod (name = getUbyte) [public static final fun getUbyte() : byte = UastEmptyExpression]
                        UClass (name = FooInterface) [public abstract interface FooInterface {...}]
                            UField (name = answer) [@org.jetbrains.annotations.NotNull @kotlin.jvm.JvmField public static final var answer: int = 42]
                                UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]
                                UAnnotation (fqName = kotlin.jvm.JvmField) [@kotlin.jvm.JvmField]
                                ULiteralExpression (value = 42) [42] : PsiType:int
                            UField (name = Companion) [@null public static final var Companion: test.pkg.FooInterface.Companion]
                                UAnnotation (fqName = null) [@null]
                            UAnnotationMethod (name = sayHello) [@kotlin.jvm.JvmStatic...}]
                                UAnnotation (fqName = kotlin.jvm.JvmStatic) [@kotlin.jvm.JvmStatic]
                                UBlockExpression [{...}] : PsiType:void
                                    UCallExpression (kind = UastCallKind(name='method_call'), argCount = 1)) [println("Hello, world!")] : PsiType:void
                                        UIdentifier (Identifier (println)) [UIdentifier (Identifier (println))]
                                        USimpleNameReferenceExpression (identifier = println, resolvesTo = null) [println] : PsiType:void
                                        ULiteralExpression (value = "Hello, world!") ["Hello, world!"] : PsiType:String
                            UClass (name = Companion) [public static final class Companion {...}]
                                UAnnotationMethod (name = sayHello) [@kotlin.jvm.JvmStatic...}]
                                    UAnnotation (fqName = kotlin.jvm.JvmStatic) [@kotlin.jvm.JvmStatic]
                                    UBlockExpression [{...}] : PsiType:void
                                        UCallExpression (kind = UastCallKind(name='method_call'), argCount = 1)) [println("Hello, world!")] : PsiType:void
                                            UIdentifier (Identifier (println)) [UIdentifier (Identifier (println))]
                                            USimpleNameReferenceExpression (identifier = println, resolvesTo = null) [println] : PsiType:void
                                            ULiteralExpression (value = "Hello, world!") ["Hello, world!"] : PsiType:String
                                UAnnotationMethod (name = Companion) [private fun Companion() = UastEmptyExpression]
                        UClass (name = FooAnnotation) [public abstract annotation FooAnnotation {...}]
                            UField (name = Companion) [@null public static final var Companion: test.pkg.FooAnnotation.Companion]
                                UAnnotation (fqName = null) [@null]
                            UClass (name = Direction) [public static enum Direction {...}]
                                UEnumConstant (name = UP) [@null UP]
                                    UAnnotation (fqName = null) [@null]
                                    USimpleNameReferenceExpression (identifier = Direction) [Direction]
                                UEnumConstant (name = DOWN) [@null DOWN]
                                    UAnnotation (fqName = null) [@null]
                                    USimpleNameReferenceExpression (identifier = Direction) [Direction]
                                UEnumConstant (name = LEFT) [@null LEFT]
                                    UAnnotation (fqName = null) [@null]
                                    USimpleNameReferenceExpression (identifier = Direction) [Direction]
                                UEnumConstant (name = RIGHT) [@null RIGHT]
                                    UAnnotation (fqName = null) [@null]
                                    USimpleNameReferenceExpression (identifier = Direction) [Direction]
                                UAnnotationMethod (name = Direction) [private fun Direction() = UastEmptyExpression]
                            UClass (name = Bar) [public static abstract annotation Bar {...}]
                            UClass (name = Companion) [public static final class Companion {...}]
                                UField (name = bar) [@org.jetbrains.annotations.NotNull private static final var bar: int = 42]
                                    UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]
                                    ULiteralExpression (value = 42) [42] : PsiType:int
                                UAnnotationMethod (name = foo) [public final fun foo() : int {...}]
                                    UBlockExpression [{...}]
                                        UReturnExpression [return 42]
                                            ULiteralExpression (value = 42) [42] : PsiType:int
                                UAnnotationMethod (name = getBar) [public final fun getBar() : int = UastEmptyExpression]
                                UAnnotationMethod (name = Companion) [private fun Companion() = UastEmptyExpression]
                        UClass (name = Name) [public final class Name {...}]
                            UField (name = s) [@org.jetbrains.annotations.NotNull private final var s: java.lang.String]
                                UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]
                            UAnnotationMethod (name = getS) [public final fun getS() : java.lang.String = UastEmptyExpression]
                            UAnnotationMethod (name = constructor-impl) [public static fun constructor-impl(@org.jetbrains.annotations.NotNull s: java.lang.String) : java.lang.String = UastEmptyExpression]
                                UParameter (name = s) [@org.jetbrains.annotations.NotNull var s: java.lang.String]
                                    UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]
                            UAnnotationMethod (name = toString-impl) [public static fun toString-impl(@null p: java.lang.String) : java.lang.String = UastEmptyExpression]
                                UParameter (name = p) [@null var p: java.lang.String]
                                    UAnnotation (fqName = null) [@null]
                            UAnnotationMethod (name = hashCode-impl) [public static fun hashCode-impl(@null p: java.lang.String) : int = UastEmptyExpression]
                                UParameter (name = p) [@null var p: java.lang.String]
                                    UAnnotation (fqName = null) [@null]
                            UAnnotationMethod (name = equals-impl) [public static fun equals-impl(@null p: java.lang.String, @org.jetbrains.annotations.Nullable p1: java.lang.Object) : boolean = UastEmptyExpression]
                                UParameter (name = p) [@null var p: java.lang.String]
                                    UAnnotation (fqName = null) [@null]
                                UParameter (name = p1) [@org.jetbrains.annotations.Nullable var p1: java.lang.Object]
                                    UAnnotation (fqName = org.jetbrains.annotations.Nullable) [@org.jetbrains.annotations.Nullable]
                            UAnnotationMethod (name = equals-impl0) [public static final fun equals-impl0(@org.jetbrains.annotations.NotNull p1: java.lang.String, @org.jetbrains.annotations.NotNull p2: java.lang.String) : boolean = UastEmptyExpression]
                                UParameter (name = p1) [@org.jetbrains.annotations.NotNull var p1: java.lang.String]
                                    UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]
                                UParameter (name = p2) [@org.jetbrains.annotations.NotNull var p2: java.lang.String]
                                    UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]
                            UAnnotationMethod (name = toString) [public fun toString() : java.lang.String = UastEmptyExpression]
                            UAnnotationMethod (name = hashCode) [public fun hashCode() : int = UastEmptyExpression]
                            UAnnotationMethod (name = equals) [public fun equals(@null p: java.lang.Object) : boolean = UastEmptyExpression]
                                UParameter (name = p) [@null var p: java.lang.Object]
                                    UAnnotation (fqName = null) [@null]
                        UClass (name = FooInterface2) [public abstract interface FooInterface2 {...}]
                            UAnnotationMethod (name = foo) [@kotlin.jvm.JvmDefault...}]
                                UAnnotation (fqName = kotlin.jvm.JvmDefault) [@kotlin.jvm.JvmDefault]
                                UBlockExpression [{...}]
                                    UReturnExpression [return 42]
                                        ULiteralExpression (value = 42) [42] : PsiType:int

                    """.trimIndent(),
                    file.asLogTypes()
                )
            })
    }
}