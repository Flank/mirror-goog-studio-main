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
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementVisitor
import junit.framework.TestCase
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UFile
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.util.isAssignment
import org.jetbrains.uast.visitor.AbstractUastVisitor

// Misc tests to verify type handling in the Kotlin UAST initialization.
class UastTest : TestCase() {
    private fun check(source: TestFile, check: (UFile) -> Unit) {
        check(sources = *arrayOf(source), check = check)
    }

    private fun check(
        vararg sources: TestFile,
        android: Boolean = true,
        library: Boolean = false,
        javaLanguageLevel: LanguageLevel? = null,
        kotlinLanguageLevel: LanguageVersionSettings? = null,
        check: (UFile) -> Unit = {}
    ) {
        val pair = LintUtilsTest.parse(
            testFiles = *sources, javaLanguageLevel = javaLanguageLevel,
            kotlinLanguageLevel = kotlinLanguageLevel, android = android, library = library
        )
        val uastFile = pair.first.uastFile
        assertNotNull(uastFile)
        check(uastFile!!)

        // Validity check: everything should be convertible
        pair.first.psiFile?.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                try {
                    element.toUElement()
                } catch (e: Throwable) {
                    System.err.println("Converting element " + element + " of class " + element.javaClass + ":")
                    throw e
                }
                super.visitElement(element)
            }
        })

        Disposer.dispose(pair.second)
    }

    fun test126439418() {
        // Regression test for https://issuetracker.google.com/126439418 /
        //  https://youtrack.jetbrains.com/issue/KT-35801
        val source = kotlin(
            """
                private val variable: Any = Object()

                fun foo1() {

                    @Suppress("MoveLambdaOutsideParentheses")
                    foo2({ variable.hashCode() })
                }

                fun foo2(function: () -> Int) {}
            """
        ).indented()

        check(
            source,
            { file ->
                assertEquals(
                    "" +
                        "public final class TestKt {\n" +
                        "    @org.jetbrains.annotations.NotNull private static final var variable: java.lang.Object = <init>()\n" +
                        "    public static final fun foo1() : void {\n" +
                        // Using plain string literal such that we can have our trailing space
                        // here without IntelliJ removing it every time we save this file:
                        "        foo2({ \n" +
                        "            return variable.hashCode()\n" +
                        "        })\n" +
                        "    }\n" +
                        "    public static final fun foo2(@org.jetbrains.annotations.NotNull function: kotlin.jvm.functions.Function0<java.lang.Integer>) : void {\n" +
                        "    }\n" +
                        "}",
                    file.asSourceString().trim()
                )
            }
        )
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

        check(
            source,
            { file ->
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
            }
        )
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

        check(
            source,
            { file ->
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

                    """.trimIndent(),
                    file.asSourceString()
                )

                assertEquals(
                    """
                UFile (package = ) [public final class BaseKt {...]
                    UClass (name = BaseKt) [public final class BaseKt {...}]
                        UMethod (name = createBase) [public static final fun createBase(@org.jetbrains.annotations.NotNull i: int) : Base {...}]
                            UParameter (name = i) [@org.jetbrains.annotations.NotNull var i: int]
                                UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]
                            UBlockExpression [{...}] : PsiType:Void
                                UReturnExpression [return <init>(i)] : PsiType:Void
                                    UCallExpression (kind = UastCallKind(name='constructor_call'), argCount = 1)) [<init>(i)] : PsiType:BaseImpl
                                        UIdentifier (Identifier (BaseImpl)) [UIdentifier (Identifier (BaseImpl))]
                                        USimpleNameReferenceExpression (identifier = <init>, resolvesTo = BaseImpl) [<init>] : PsiType:BaseImpl
                                        USimpleNameReferenceExpression (identifier = i) [i] : PsiType:int
                    UClass (name = Base) [public abstract interface Base {...}]
                        UMethod (name = print) [public abstract fun print() : void = UastEmptyExpression]
                    UClass (name = BaseImpl) [public final class BaseImpl : Base {...}]
                        UField (name = x) [@org.jetbrains.annotations.NotNull private final var x: int]
                            UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]
                        UMethod (name = print) [public fun print() : void {...}]
                            UBlockExpression [{...}] : PsiType:void
                                UCallExpression (kind = UastCallKind(name='method_call'), argCount = 1)) [println(x)] : PsiType:void
                                    UIdentifier (Identifier (println)) [UIdentifier (Identifier (println))]
                                    USimpleNameReferenceExpression (identifier = println, resolvesTo = null) [println] : PsiType:void
                                    USimpleNameReferenceExpression (identifier = x) [x] : PsiType:int
                        UMethod (name = getX) [public final fun getX() : int = UastEmptyExpression]
                        UMethod (name = BaseImpl) [public fun BaseImpl(@org.jetbrains.annotations.NotNull x: int) = UastEmptyExpression]
                            UParameter (name = x) [@org.jetbrains.annotations.NotNull var x: int]
                                UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]
                    UClass (name = Derived) [public final class Derived : Base {...}]
                        UExpressionList (super_delegation) [super_delegation Base : createBase(10)]
                            UTypeReferenceExpression (name = Base) [Base]
                            UCallExpression (kind = UastCallKind(name='method_call'), argCount = 1)) [createBase(10)] : PsiType:Base
                                UIdentifier (Identifier (createBase)) [UIdentifier (Identifier (createBase))]
                                USimpleNameReferenceExpression (identifier = createBase, resolvesTo = null) [createBase] : PsiType:Base
                                ULiteralExpression (value = 10) [10] : PsiType:int
                        UMethod (name = Derived) [public fun Derived(@org.jetbrains.annotations.NotNull b: Base) = UastEmptyExpression]
                            UParameter (name = b) [@org.jetbrains.annotations.NotNull var b: Base]
                                UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]

                    """.trimIndent(),
                    file.asLogTypes()
                )
            }
        )
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
                            UMethod (name = test) [public static final fun test(@org.jetbrains.annotations.NotNull s: java.lang.String) : java.lang.Object {...}]
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
                                                        UYieldExpression [yield println(something)]
                                                            UCallExpression (kind = UastCallKind(name='method_call'), argCount = 1)) [println(something)] : PsiType:void
                                                                UIdentifier (Identifier (println)) [UIdentifier (Identifier (println))]
                                                                USimpleNameReferenceExpression (identifier = println, resolvesTo = null) [println] : PsiType:void
                                                                USimpleNameReferenceExpression (identifier = something) [something] : PsiType:int
                                                USwitchClauseExpressionWithBody [ -> {...]
                                                    UExpressionList (when_entry) [{...]
                                                        UYieldExpression [yield ""]
                                                            ULiteralExpression (value = "") [""] : PsiType:String
                            UMethod (name = getUint) [public static final fun getUint() : int = UastEmptyExpression]
                            UMethod (name = getUlong) [public static final fun getUlong() : long = UastEmptyExpression]
                            UMethod (name = getUbyte) [public static final fun getUbyte() : byte = UastEmptyExpression]
                        UClass (name = FooInterface) [public abstract interface FooInterface {...}]
                            UField (name = answer) [@org.jetbrains.annotations.NotNull @kotlin.jvm.JvmField public static final var answer: int = 42]
                                UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]
                                UAnnotation (fqName = kotlin.jvm.JvmField) [@kotlin.jvm.JvmField]
                                ULiteralExpression (value = 42) [42] : PsiType:int
                            UField (name = Companion) [@null public static final var Companion: test.pkg.FooInterface.Companion]
                                UAnnotation (fqName = null) [@null]
                            UMethod (name = sayHello) [@kotlin.jvm.JvmStatic...}]
                                UAnnotation (fqName = kotlin.jvm.JvmStatic) [@kotlin.jvm.JvmStatic]
                                UBlockExpression [{...}] : PsiType:void
                                    UCallExpression (kind = UastCallKind(name='method_call'), argCount = 1)) [println("Hello, world!")] : PsiType:void
                                        UIdentifier (Identifier (println)) [UIdentifier (Identifier (println))]
                                        USimpleNameReferenceExpression (identifier = println, resolvesTo = null) [println] : PsiType:void
                                        ULiteralExpression (value = "Hello, world!") ["Hello, world!"] : PsiType:String
                            UClass (name = Companion) [public static final class Companion {...}]
                                UMethod (name = sayHello) [@kotlin.jvm.JvmStatic...}]
                                    UAnnotation (fqName = kotlin.jvm.JvmStatic) [@kotlin.jvm.JvmStatic]
                                    UBlockExpression [{...}] : PsiType:void
                                        UCallExpression (kind = UastCallKind(name='method_call'), argCount = 1)) [println("Hello, world!")] : PsiType:void
                                            UIdentifier (Identifier (println)) [UIdentifier (Identifier (println))]
                                            USimpleNameReferenceExpression (identifier = println, resolvesTo = null) [println] : PsiType:void
                                            ULiteralExpression (value = "Hello, world!") ["Hello, world!"] : PsiType:String
                                UMethod (name = Companion) [private fun Companion() = UastEmptyExpression]
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
                                UMethod (name = Direction) [private fun Direction() = UastEmptyExpression]
                                UMethod (name = values) [public static fun values() = UastEmptyExpression]
                                UMethod (name = valueOf) [public static fun valueOf(@null name: java.lang.String) = UastEmptyExpression]
                                    UParameter (name = name) [@null var name: java.lang.String]
                                        UAnnotation (fqName = null) [@null]
                            UClass (name = Bar) [public static abstract annotation Bar {...}]
                            UClass (name = Companion) [public static final class Companion {...}]
                                UField (name = bar) [@org.jetbrains.annotations.NotNull private static final var bar: int = 42]
                                    UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]
                                    ULiteralExpression (value = 42) [42] : PsiType:int
                                UMethod (name = foo) [public final fun foo() : int {...}]
                                    UBlockExpression [{...}]
                                        UReturnExpression [return 42]
                                            ULiteralExpression (value = 42) [42] : PsiType:int
                                UMethod (name = getBar) [public final fun getBar() : int = UastEmptyExpression]
                                UMethod (name = Companion) [private fun Companion() = UastEmptyExpression]
                        UClass (name = Name) [public final class Name {...}]
                            UField (name = s) [@org.jetbrains.annotations.NotNull private final var s: java.lang.String]
                                UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]
                            UMethod (name = getS) [public final fun getS() : java.lang.String = UastEmptyExpression]
                            UMethod (name = constructor-impl) [public static fun constructor-impl(@org.jetbrains.annotations.NotNull s: java.lang.String) : java.lang.String = UastEmptyExpression]
                                UParameter (name = s) [@org.jetbrains.annotations.NotNull var s: java.lang.String]
                                    UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]
                            UMethod (name = toString-impl) [public static fun toString-impl(@null p: java.lang.String) : java.lang.String = UastEmptyExpression]
                                UParameter (name = p) [@null var p: java.lang.String]
                                    UAnnotation (fqName = null) [@null]
                            UMethod (name = hashCode-impl) [public static fun hashCode-impl(@null p: java.lang.String) : int = UastEmptyExpression]
                                UParameter (name = p) [@null var p: java.lang.String]
                                    UAnnotation (fqName = null) [@null]
                            UMethod (name = equals-impl) [public static fun equals-impl(@null p: java.lang.String, @org.jetbrains.annotations.Nullable p1: java.lang.Object) : boolean = UastEmptyExpression]
                                UParameter (name = p) [@null var p: java.lang.String]
                                    UAnnotation (fqName = null) [@null]
                                UParameter (name = p1) [@org.jetbrains.annotations.Nullable var p1: java.lang.Object]
                                    UAnnotation (fqName = org.jetbrains.annotations.Nullable) [@org.jetbrains.annotations.Nullable]
                            UMethod (name = equals-impl0) [public static final fun equals-impl0(@org.jetbrains.annotations.NotNull p1: java.lang.String, @org.jetbrains.annotations.NotNull p2: java.lang.String) : boolean = UastEmptyExpression]
                                UParameter (name = p1) [@org.jetbrains.annotations.NotNull var p1: java.lang.String]
                                    UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]
                                UParameter (name = p2) [@org.jetbrains.annotations.NotNull var p2: java.lang.String]
                                    UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]
                            UMethod (name = toString) [public fun toString() : java.lang.String = UastEmptyExpression]
                            UMethod (name = hashCode) [public fun hashCode() : int = UastEmptyExpression]
                            UMethod (name = equals) [public fun equals(@null p: java.lang.Object) : boolean = UastEmptyExpression]
                                UParameter (name = p) [@null var p: java.lang.Object]
                                    UAnnotation (fqName = null) [@null]
                        UClass (name = FooInterface2) [public abstract interface FooInterface2 {...}]
                            UMethod (name = foo) [@kotlin.jvm.JvmDefault...}]
                                UAnnotation (fqName = kotlin.jvm.JvmDefault) [@kotlin.jvm.JvmDefault]
                                UBlockExpression [{...}]
                                    UReturnExpression [return 42]
                                        ULiteralExpression (value = 42) [42] : PsiType:int

                    """.trimIndent(),
                    file.asLogTypes()
                )
            }
        )
    }

    fun testSuspend() {
        // Regression test for
        // https://youtrack.jetbrains.com/issue/KT-32031:
        // UAST: Method body missing for suspend functions
        val source = kotlin(
            """
            package test.pkg
            import android.widget.TextView
            class Test : android.app.Activity {
                private suspend fun setUi(x: Int, y: Int) {
                    val z = x + y
                }
            }
            """
        ).indented()

        check(
            source,
            { file ->
                assertEquals(
                    """
                package test.pkg

                import android.widget.TextView

                public final class Test : android.app.Activity {
                    public fun Test() = UastEmptyExpression
                    fun setUi(@org.jetbrains.annotations.NotNull x: int, @org.jetbrains.annotations.NotNull y: int) : void {
                        var z: int = x + y
                    }
                }

                    """.trimIndent(),
                    file.asSourceString()
                )

                assertEquals(
                    """
                UFile (package = test.pkg) [package test.pkg...]
                    UImportStatement (isOnDemand = false) [import android.widget.TextView]
                    UClass (name = Test) [public final class Test : android.app.Activity {...}]
                        UMethod (name = Test) [public fun Test() = UastEmptyExpression]
                        UMethod (name = setUi) [fun setUi(@org.jetbrains.annotations.NotNull x: int, @org.jetbrains.annotations.NotNull y: int) : void {...}]
                            UParameter (name = x) [@org.jetbrains.annotations.NotNull var x: int]
                                UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]
                            UParameter (name = y) [@org.jetbrains.annotations.NotNull var y: int]
                                UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]
                            UBlockExpression [{...}] : PsiType:void
                                UDeclarationsExpression [var z: int = x + y]
                                    ULocalVariable (name = z) [var z: int = x + y]
                                        UBinaryExpression (operator = +) [x + y] : PsiType:int
                                            USimpleNameReferenceExpression (identifier = x) [x] : PsiType:int
                                            USimpleNameReferenceExpression (identifier = y) [y] : PsiType:int

                    """.trimIndent(),
                    file.asLogTypes()
                )
            }
        )
    }

    fun testReifiedTypes() {
        // Regression test for
        // https://youtrack.jetbrains.com/issue/KT-35610:
        // UAST: Some reified methods nave null returnType
        val source = kotlin(
            """
            package test.pkg
            inline fun <T> function1(t: T) { }                  // return type void (PsiPrimitiveType)
            inline fun <T> function2(t: T): T = t               // return type T (PsiClassReferenceType)
            inline fun <reified T> function3(t: T) { }          // return type null
            inline fun <reified T> function4(t: T): T = t       // return type null
            inline fun <reified T> function5(t: T): Int = 42    // return type null
            // Other variations that also have a null return type
            inline fun <reified T : Activity> T.function6(t: T): T = t
            inline fun <reified T> function7(t: T): T = t
            private inline fun <reified T> function8(t: T): T = t
            internal inline fun <reified T> function9(t: T): T = t
            public inline fun <reified T> function10(t: T): T = t
            inline fun <reified T> T.function11(t: T): T = t
            """
        ).indented()

        check(
            source,
            { file ->
                assertEquals(
                    """
                package test.pkg

                public final class TestKt {
                    public static final fun function1(@org.jetbrains.annotations.Nullable t: T) : void {
                    }
                    public static final fun function2(@org.jetbrains.annotations.Nullable t: T) : T {
                        return t
                    }
                    static fun function3(@org.jetbrains.annotations.Nullable t: T) : void {
                    }
                    static fun function4(@org.jetbrains.annotations.Nullable t: T) : T {
                        return t
                    }
                    static fun function5(@org.jetbrains.annotations.Nullable t: T) : int {
                        return 42
                    }
                    static fun function6(@org.jetbrains.annotations.Nullable ${"$"}this${"$"}function6: T, @org.jetbrains.annotations.Nullable t: T) : T {
                        return t
                    }
                    static fun function7(@org.jetbrains.annotations.Nullable t: T) : T {
                        return t
                    }
                    static fun function8(@org.jetbrains.annotations.Nullable t: T) : T {
                        return t
                    }
                    static fun function9(@org.jetbrains.annotations.Nullable t: T) : T {
                        return t
                    }
                    static fun function10(@org.jetbrains.annotations.Nullable t: T) : T {
                        return t
                    }
                    static fun function11(@org.jetbrains.annotations.Nullable ${"$"}this${"$"}function11: T, @org.jetbrains.annotations.Nullable t: T) : T {
                        return t
                    }
                }

                    """.trimIndent(),
                    file.asSourceString()
                )
            }
        )
    }

    fun testKtParameters() {
        // Regression test for
        // https://issuetracker.google.com/134093981
        val source = kotlin(
            """
            package test.pkg
            inline class GraphVariables(val set: MutableSet<GraphVariable<*>>) {
                fun <T> variable(name: String, graphType: String, value: T) {
                    this.set.add(GraphVariable(name, graphType, value))
                }
            }
            class GraphVariable<T>(name: String, graphType: String, value: T) {
            }
            """
        ).indented()

        check(
            source,
            { file ->
                assertEquals(
                    """
                package test.pkg

                public final class GraphVariables {
                    @org.jetbrains.annotations.NotNull private final var set: java.util.Set<test.pkg.GraphVariable<?>>
                    public final fun getSet() : java.util.Set<test.pkg.GraphVariable<?>> = UastEmptyExpression
                    public static final fun variable-impl(@org.jetbrains.annotations.NotNull ${"$"}this: java.util.Set<test.pkg.GraphVariable<?>>, @org.jetbrains.annotations.NotNull name: java.lang.String, @org.jetbrains.annotations.Nullable graphType: java.lang.String, @null value: T) : void {
                        this.set.add(<init>(name, graphType, value))
                    }
                    public static fun constructor-impl(@org.jetbrains.annotations.NotNull set: java.util.Set<test.pkg.GraphVariable<?>>) : java.util.Set = UastEmptyExpression
                    public static fun toString-impl(@null p: java.util.Set) : java.lang.String = UastEmptyExpression
                    public static fun hashCode-impl(@null p: java.util.Set) : int = UastEmptyExpression
                    public static fun equals-impl(@null p: java.util.Set, @org.jetbrains.annotations.Nullable p1: java.lang.Object) : boolean = UastEmptyExpression
                    public static final fun equals-impl0(@org.jetbrains.annotations.NotNull p1: java.util.Set, @org.jetbrains.annotations.NotNull p2: java.util.Set) : boolean = UastEmptyExpression
                    public fun toString() : java.lang.String = UastEmptyExpression
                    public fun hashCode() : int = UastEmptyExpression
                    public fun equals(@null p: java.lang.Object) : boolean = UastEmptyExpression
                }

                public final class GraphVariable {
                    public fun GraphVariable(@org.jetbrains.annotations.NotNull name: java.lang.String, @org.jetbrains.annotations.NotNull graphType: java.lang.String, @org.jetbrains.annotations.Nullable value: T) = UastEmptyExpression
                }

                    """.trimIndent(),
                    file.asSourceString()
                )
            }
        )
    }

    fun testCatchClausesKotlin() {
        // Regression test for
        // https://issuetracker.google.com/140154274
        // and https://youtrack.jetbrains.com/issue/KT-35804
        val source = kotlin(
            """
            package test.pkg

            class TryCatchKotlin {
                @java.lang.SuppressWarnings("Something")
                fun catches() {
                    try {
                        catches()
                    } catch (@java.lang.SuppressWarnings("Something") e: Throwable) {
                    }
                }

                fun throws() {
                }
            }
            """
        ).indented()

        check(
            source,
            { file ->
                assertEquals(
                    """
                package test.pkg

                public final class TryCatchKotlin {
                    @java.lang.SuppressWarnings(value = "Something")
                    public final fun catches() : void {
                        try {
                            catches()
                        }
                        catch (@org.jetbrains.annotations.NotNull @java.lang.SuppressWarnings(value = "Something") var e: java.lang.Throwable) {
                        }
                    }
                    public final fun throws() : void {
                    }
                    public fun TryCatchKotlin() = UastEmptyExpression
                }
                    """.trimIndent().trim(),
                    file.asSourceString().trim().replace("\n        \n", "\n")
                )
            }
        )

        // Java is OK:
        val javaSource = java(
            """
            public class TryCatchJava {
                @SuppressWarnings("Something")
                public void test() {
                    try {
                        canThrow();
                    } catch(@SuppressWarnings("Something") Throwable t) {
                    }
                }
                public void canThrow() {
                }
            }
            """
        ).indented()

        check(
            javaSource,
            { file ->
                assertEquals(
                    // The annotations work in Java, as checked by
                    // ApiDetectorTest#testConditionalAroundExceptionSuppress
                    // However, in pretty printing catch clause parameters are not
                    // visited, as described in https://youtrack.jetbrains.com/issue/KT-35803
                    """
                public class TryCatchJava {
                    @java.lang.SuppressWarnings(null = "Something")
                    public fun test() : void {
                        try {
                            canThrow()
                        }
                        catch (e) {
                        }
                    }
                    public fun canThrow() : void {
                    }
                }
                    """.trimIndent().trim(),
                    file.asSourceString().trim().replace("\n        \n", "\n")
                )
            }
        )
    }

    fun testSamAst() { // See KT-28272
        val source = kotlin(
            """
            //@file:Suppress("RedundantSamConstructor", "MoveLambdaOutsideParentheses", "unused", "UNUSED_VARIABLE")

            package test.pkg

            fun test1() {
                val thread1 = Thread({ println("hello") })
            }

            fun test2() {
                val thread2 = Thread(Runnable { println("hello") })
            }
            """
        ).indented()

        check(
            source,
            { file ->
                assertEquals(
                    """
                UFile (package = test.pkg) [package test.pkg...]
                  UClass (name = TestKt) [public final class TestKt {...}]
                    UMethod (name = test1) [public static final fun test1() : void {...}]
                      UBlockExpression [{...}] : PsiType:void
                        UDeclarationsExpression [var thread1: java.lang.Thread = <init>({ ...})]
                          ULocalVariable (name = thread1) [var thread1: java.lang.Thread = <init>({ ...})]
                            UCallExpression (kind = UastCallKind(name='constructor_call'), argCount = 1)) [<init>({ ...})] : PsiType:Thread
                              UIdentifier (Identifier (Thread)) [UIdentifier (Identifier (Thread))]
                              USimpleNameReferenceExpression (identifier = <init>, resolvesTo = Thread) [<init>] : PsiType:Thread
                              ULambdaExpression [{ ...}] : PsiType:Function0<? extends Unit>
                                UBlockExpression [{...}]
                                  UCallExpression (kind = UastCallKind(name='method_call'), argCount = 1)) [println("hello")] : PsiType:void
                                    UIdentifier (Identifier (println)) [UIdentifier (Identifier (println))]
                                    USimpleNameReferenceExpression (identifier = println, resolvesTo = null) [println] : PsiType:void
                                    ULiteralExpression (value = "hello") ["hello"] : PsiType:String
                    UMethod (name = test2) [public static final fun test2() : void {...}]
                      UBlockExpression [{...}] : PsiType:void
                        UDeclarationsExpression [var thread2: java.lang.Thread = <init>(Runnable({ ...}))]
                          ULocalVariable (name = thread2) [var thread2: java.lang.Thread = <init>(Runnable({ ...}))]
                            UCallExpression (kind = UastCallKind(name='constructor_call'), argCount = 1)) [<init>(Runnable({ ...}))] : PsiType:Thread
                              UIdentifier (Identifier (Thread)) [UIdentifier (Identifier (Thread))]
                              USimpleNameReferenceExpression (identifier = <init>, resolvesTo = Thread) [<init>] : PsiType:Thread
                              UCallExpression (kind = UastCallKind(name='method_call'), argCount = 1)) [Runnable({ ...})] : PsiType:Runnable
                                UIdentifier (Identifier (Runnable)) [UIdentifier (Identifier (Runnable))]
                                USimpleNameReferenceExpression (identifier = Runnable, resolvesTo = Runnable) [Runnable] : PsiType:Runnable
                                ULambdaExpression [{ ...}] : PsiType:Function0<? extends Unit>
                                  UBlockExpression [{...}]
                                    UCallExpression (kind = UastCallKind(name='method_call'), argCount = 1)) [println("hello")] : PsiType:void
                                      UIdentifier (Identifier (println)) [UIdentifier (Identifier (println))]
                                      USimpleNameReferenceExpression (identifier = println, resolvesTo = null) [println] : PsiType:void
                                      ULiteralExpression (value = "hello") ["hello"] : PsiType:String
                    """.trimIndent(),
                    file.asLogTypes(indent = "  ").trim()
                )

                try {
                    file.accept(object : AbstractUastVisitor() {
                        override fun visitCallExpression(node: UCallExpression): Boolean {
                            val resolved = node.resolve()
                            if (resolved == null) {
                                throw IllegalStateException("Could not resolve this call: ${node.asSourceString()}")
                            }
                            return super.visitCallExpression(node)
                        }
                    })
                    fail("Expected unresolved error: see KT-28272")
                } catch (failure: IllegalStateException) {
                    assertEquals(
                        "Could not resolve this call: Runnable({ \n    println(\"hello\")\n})",
                        failure.message
                    )
                }
            }
        )
    }

    fun testJava11() {
        val source = java(
            """
            package test.pkg;
            import java.util.function.IntFunction;
            public class Java11Test {
                // Private methods in interfaces
                public interface MyInterface {
                    private String getHello() {
                        return "hello";
                    }
                    default String getMessage() {
                        return getHello();
                    }
                }
                // Var keyword
                public void varStuff() {
                    var name = "Name";
                    var meaning = 42;
                    for (var line : name.split("\n")) {
                        System.out.println(line);
                    }
                }
                // Lambda vars
                IntFunction<Integer> doubler = (var x) -> x * 2;
            }
            """
        ).indented()

        check(
            source, javaLanguageLevel = LanguageLevel.JDK_11, android = false,
            check = { file ->
                assertEquals(
                    """
                    UFile (package = test.pkg) [package test.pkg...]
                      UImportStatement (isOnDemand = false) [import java.util.function.IntFunction]
                      UClass (name = Java11Test) [public class Java11Test {...}]
                        UField (name = doubler) [var doubler: java.util.function.IntFunction<java.lang.Integer> = { var x: int ->...}]
                          ULambdaExpression [{ var x: int ->...}] : PsiType:<lambda expression>
                            UParameter (name = x) [var x: int]
                            UBlockExpression [{...}]
                              UReturnExpression [return x * 2]
                                UBinaryExpression (operator = *) [x * 2] : PsiType:int
                                  USimpleNameReferenceExpression (identifier = x) [x] : PsiType:int
                                  ULiteralExpression (value = 2) [2] : PsiType:int
                        UMethod (name = varStuff) [public fun varStuff() : void {...}]
                          UBlockExpression [{...}]
                            UDeclarationsExpression [var name: java.lang.String = "Name"]
                              ULocalVariable (name = name) [var name: java.lang.String = "Name"]
                                ULiteralExpression (value = "Name") ["Name"] : PsiType:String
                            UDeclarationsExpression [var meaning: int = 42]
                              ULocalVariable (name = meaning) [var meaning: int = 42]
                                ULiteralExpression (value = 42) [42] : PsiType:int
                            UForEachExpression [for (line : name.split("\n")) {...}]
                              UQualifiedReferenceExpression [name.split("\n")] : PsiType:String[]
                                USimpleNameReferenceExpression (identifier = name) [name] : PsiType:String
                                UCallExpression (kind = UastCallKind(name='method_call'), argCount = 1)) [split("\n")] : PsiType:String[]
                                  UIdentifier (Identifier (split)) [UIdentifier (Identifier (split))]
                                  ULiteralExpression (value = "\n") ["\n"] : PsiType:String
                              UBlockExpression [{...}]
                                UQualifiedReferenceExpression [System.out.println(line)] : PsiType:void
                                  UQualifiedReferenceExpression [System.out] : PsiType:PrintStream
                                    USimpleNameReferenceExpression (identifier = System) [System]
                                    USimpleNameReferenceExpression (identifier = out) [out]
                                  UCallExpression (kind = UastCallKind(name='method_call'), argCount = 1)) [println(line)] : PsiType:void
                                    UIdentifier (Identifier (println)) [UIdentifier (Identifier (println))]
                                    USimpleNameReferenceExpression (identifier = line) [line] : PsiType:String
                        UClass (name = MyInterface) [public static abstract interface MyInterface {...}]
                          UMethod (name = getHello) [private fun getHello() : java.lang.String {...}]
                            UBlockExpression [{...}]
                              UReturnExpression [return "hello"]
                                ULiteralExpression (value = "hello") ["hello"] : PsiType:String
                          UMethod (name = getMessage) [public default fun getMessage() : java.lang.String {...}]
                            UBlockExpression [{...}]
                              UReturnExpression [return getHello()]
                                UCallExpression (kind = UastCallKind(name='method_call'), argCount = 0)) [getHello()] : PsiType:String
                                  UIdentifier (Identifier (getHello)) [UIdentifier (Identifier (getHello))]
                    """.trimIndent(),
                    file.asLogTypes(indent = "  ").trim()
                )

                // Make sure that all calls correctly resolve
                file.accept(object : AbstractUastVisitor() {
                    override fun visitCallExpression(node: UCallExpression): Boolean {
                        val resolved = node.resolve()
                        assertNotNull(resolved)
                        return super.visitCallExpression(node)
                    }
                })
            }
        )
    }

    fun test125138962() {
        // Regression test for https://issuetracker.google.com/125138962
        val source = kotlin(
            """
            package test.pkg

            class SimpleClass() {
                var foo: Int
                init {
                    @Suppress("foo")
                    foo = android.R.layout.activity_list_item
                }
            }
            """
        ).indented()

        check(
            source,
            check = { file ->
                assertEquals(
                    """
                package test.pkg

                public final class SimpleClass {
                    @org.jetbrains.annotations.NotNull private var foo: int
                    public final fun getFoo() : int = UastEmptyExpression
                    public final fun setFoo(@null p: int) : void = UastEmptyExpression
                    public fun SimpleClass() {
                        {
                            foo = android.R.layout.activity_list_item
                        }
                    }
                }
                    """.trimIndent(),
                    file.asSourceString().trim()
                )
            }
        )
    }

    fun testIdea234484() {
        // Regression test for https://youtrack.jetbrains.com/issue/KT-37200
        val source = kotlin(
            """
            package test.pkg

            inline fun <reified F> ViewModelContext.viewModelFactory(): F {
                return activity as? F ?: throw IllegalStateException("Boo!")
            }

            sealed class ViewModelContext {
                abstract val activity: Number
            }
            """
        ).indented()

        check(
            source,
            check = { file ->
                val newFile = file.sourcePsi.toUElement()
                newFile?.accept(object : AbstractUastVisitor() {
                    override fun visitLocalVariable(node: ULocalVariable): Boolean {
                        val initializerType = node.uastInitializer?.getExpressionType()
                        val interfaceType = node.type
                        @Suppress("UNUSED_VARIABLE")
                        val equals = initializerType == interfaceType // Stack overflow!

                        return super.visitLocalVariable(node)
                    }
                })
            }
        )
    }

    fun testKt27935() {
        // Regression test for https://youtrack.jetbrains.com/issue/KT-27935
        val source = kotlin(
            """
            package test.pkg

            typealias IndexedDistance = Pair<Int, Double>
            typealias Window = (Array<IndexedDistance>) -> List<IndexedDistance>
            class Core(
                    val window: Window
            )
            """
        ).indented()

        check(source, check = { })
    }

    fun testKt36275() {
        // Regression test for https://youtrack.jetbrains.com/issue/KT-36275
        val source = kotlin(
            """
            package test.pkg

             fun foo() {
                fun bar() {
                }
                bar()
            }
            """
        ).indented()

        check(
            source,
            check = { file ->
                // Make sure that all calls correctly resolve
                file.accept(object : AbstractUastVisitor() {
                    override fun visitCallExpression(node: UCallExpression): Boolean {
                        val resolved = node.resolve()
                        assertNotNull(resolved)
                        return super.visitCallExpression(node)
                    }
                })
            }
        )
    }

    fun testKt34187() {
        // Regression test for https://youtrack.jetbrains.com/issue/KT-34187
        val source = kotlin(
            """
            package test.pkg

            class Publisher { }

            fun usedAsArrayElement() {
                val a = arrayOfNulls<Publisher<*>>(10)
                a[0] = Publisher()
            }
            """
        ).indented()

        check(
            source,
            check = { file ->
                // Make sure that all calls correctly resolve
                file.accept(object : AbstractUastVisitor() {
                    override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
                        if (node.isAssignment()) {
                            val type = node.leftOperand.getExpressionType()
                            assertNotNull("type of ${node.leftOperand.sourcePsi?.text} is null", type)
                        }
                        return super.visitBinaryExpression(node)
                    }
                })
            }
        )
    }
}
