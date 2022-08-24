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

package com.android.tools.lint.checks

import com.android.testutils.TestUtils
import com.android.tools.lint.checks.ToastDetectorTest.Companion.snackbarStubs
import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestFiles.rClass
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintUtilsTest
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import junit.framework.TestCase
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.util.isConstructorCall
import org.jetbrains.uast.visitor.AbstractUastVisitor
import org.junit.Assert.assertNotEquals
import java.io.File

/**
 * Unit tests for the data flow analyzer. Note that there are
 * also a number of additional unit tests in CleanupDetectorTest,
 * ToastDetectorTest, SliceDetectorTest and WorkManagerDetectorTest, and
 * over time possibly others.
 */
class DataFlowAnalyzerTest : TestCase() {
    fun testJava() {
        val parsed = LintUtilsTest.parse(
            """
                package test.pkg;

                @SuppressWarnings("all")
                public class Test {
                    public void test() {
                        Test result = a().b().c().d().e().f();
                        Test copied1 = result;
                        Test copied2;
                        copied2 = copied1;
                        copied2.g();
                        copied2.toString().hashCode();
                    }

                    public Test a() { return this; }
                    public Test b() { return this; }
                    public Test c() { return this; }
                    public Test d() { return this; }
                    public Test e() { return this; }
                    public Test f() { return this; }
                    public Test g() { return this; }
                    public Test other() { return this; }
                }
            """,
            File("test/pkg/Test.java")
        )

        val target = findMethodCall(parsed, "d")

        val receivers = mutableListOf<String>()
        val analyzer = object : DataFlowAnalyzer(listOf(target)) {
            override fun receiver(call: UCallExpression) {
                val name = call.methodName ?: "?"
                assertNotEquals(name, "hashCode")
                receivers.add(name)
                super.receiver(call)
            }
        }
        val method = target.getParentOfType(UMethod::class.java)
        method?.accept(analyzer)

        assertEquals("e, f, g, toString", receivers.joinToString { it })

        Disposer.dispose(parsed.second)
    }

    fun testParameter() {
        val parsed = LintUtilsTest.parse(
            """
                package test.pkg;

                @SuppressWarnings("all")
                public class Test {
                    public void test(int a, int b) {
                        int c = 0;
                        int d = c;
                        int e = 0;
                        m(b); // should be flagged because we're tracking parameter b
                        m(c); // should be flagged because we're tracking variable c
                        m(d); // tracked because it flows from variable c
                        m(e); // NOT included
                    }

                    public void m(int x) { }
                }
            """,
            File("test/pkg/Test.java")
        )

        val variable = findVariableDeclaration(parsed, "c")
        val method = variable.getParentOfType(UMethod::class.java)!!
        val parameter = method.uastParameters.last()

        val arguments = mutableListOf<String>()
        val analyzer = object : DataFlowAnalyzer(listOf(parameter, variable)) {
            override fun argument(call: UCallExpression, reference: UElement) {
                val name = call.methodName ?: "?"
                arguments.add(name + "(" + reference.sourcePsi?.text + ")")
            }
        }
        method.accept(analyzer)

        assertEquals("m(b), m(c), m(d)", arguments.joinToString { it })

        Disposer.dispose(parsed.second)
    }

    fun testKotlin() {
        val parsed = LintUtilsTest.parseKotlin(
            """
                package test.pkg

                class Test {
                    fun test() {
                        val result = a().b().c().d().e().f()
                        val copied2: Test
                        copied2 = result
                        val copied3 = copied2.g()
                        copied2.toString().hashCode()

                        copied3.apply {
                            h()
                        }
                    }

                    fun a(): Test = this
                    fun b(): Test = this
                    fun c(): Test = this
                    fun d(): Test = this
                    fun e(): Test = this
                    fun f(): Test = this
                    fun g(): Test = this
                    fun h(): Test = this
                    fun other(): Test = this
                }
            """,
            File("test/pkg/Test.kt")
        )

        val target = findMethodCall(parsed, "d")

        val receivers = mutableListOf<String>()
        val analyzer = object : DataFlowAnalyzer(listOf(target)) {
            override fun receiver(call: UCallExpression) {
                val name = call.methodName ?: "?"
                assertNotEquals(name, "hashCode")
                receivers.add(name)
                super.receiver(call)
            }
        }
        val method = target.getParentOfType(UMethod::class.java)
        method?.accept(analyzer)

        assertEquals("e, f, g, toString, apply, h", receivers.joinToString { it })

        Disposer.dispose(parsed.second)
    }

    private fun findMethodCall(
        parsed: com.android.utils.Pair<JavaContext, Disposable>,
        targetName: String
    ): UCallExpression {
        var target: UCallExpression? = null
        val file = parsed.first.uastFile!!
        file.accept(object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                if (node.methodName == targetName) {
                    target = node
                } else if (node.isConstructorCall() && node.classReference?.resolvedName == targetName) {
                    target = node
                }
                return super.visitCallExpression(node)
            }
        })
        assertNotNull(target)
        return target!!
    }

    private fun findVariableDeclaration(
        parsed: com.android.utils.Pair<JavaContext, Disposable>,
        targetName: String
    ): UVariable {
        var target: UVariable? = null
        val file = parsed.first.uastFile!!
        file.accept(object : AbstractUastVisitor() {
            override fun visitVariable(node: UVariable): Boolean {
                if (node.name == targetName) {
                    target = node
                }
                return super.visitVariable(node)
            }
        })
        assertNotNull(target)
        return target!!
    }

    fun testKotlinStandardFunctions() {
        // Makes sure the semantics of let, apply, also, with and run are handled correctly.
        // Regression test for https://issuetracker.google.com/187437289.
        lint().files(
            kotlin(
                """
                @file:Suppress("unused")

                package test.pkg

                import android.content.Context
                import android.widget.Toast

                class StandardTest {
                    class Unrelated {
                        // unrelated show
                        fun show() {}
                    }

                    @Suppress("SimpleRedundantLet")
                    fun testLetError(context: Context, unrelated: Unrelated?) {
                        val toast = Toast.makeText(context, R.string.app_name, Toast.LENGTH_LONG) // ERROR 1
                        unrelated?.let { it.show() }
                    }

                    fun testLetOk1(context: Context, unrelated: Unrelated?) {
                        val toast = Toast.makeText(context, R.string.app_name, Toast.LENGTH_LONG) // OK 1
                        toast.let { it.show() }
                    }

                    fun testLetOk2(context: Context, unrelated: Unrelated?) {
                        val toast = Toast.makeText(context, R.string.app_name, Toast.LENGTH_LONG) // OK 2
                        // Explicit lambda variable; handled differently in UAST
                        toast.let { t -> t.show() }
                    }

                    fun testLetOk3(context: Context, unrelated: Unrelated?) {
                        val toast = Toast.makeText(context, R.string.app_name, Toast.LENGTH_LONG) // OK 3
                        // Nested lambdas to test iteration
                        toast.let {
                            unrelated?.let { x ->
                                println(x)
                                it.show()
                            }
                        }
                    }

                    @Suppress("SimpleRedundantLet")
                    fun testLetNested(context: Context, unrelated: Unrelated) {
                        val toast = Toast.makeText(context, R.string.app_name, Toast.LENGTH_LONG) // ERROR 2
                        toast.apply {
                            unrelated.let {
                                unrelated.let {
                                    it.show()
                                }
                            }
                        }
                    }

                    fun testWithError(context: Context, unrelated: Unrelated?) {
                        val toast = Toast.makeText(context, R.string.app_name, Toast.LENGTH_LONG) // ERROR 3
                        with(unrelated!!) {
                            show()
                        }
                    }

                    fun testWithOk(context: Context, unrelated: Unrelated?) {
                        val toast = Toast.makeText(context, R.string.app_name, Toast.LENGTH_LONG) // OK 4
                        with(toast) {
                            show()
                        }
                    }

                    fun testApplyOk(context: Context, unrelated: Unrelated?) {
                        val toast = Toast.makeText(context, R.string.app_name, Toast.LENGTH_LONG) // OK 5
                        toast.apply {
                            show()
                        }
                    }

                    fun testApplyError(context: Context, unrelated: Unrelated) {
                        val toast = Toast.makeText(context, R.string.app_name, Toast.LENGTH_LONG) // ERROR 4
                        unrelated.apply {
                            show()
                        }
                    }

                    fun testAlsoOk(context: Context, unrelated: Unrelated?) {
                        val toast = Toast.makeText(context, R.string.app_name, Toast.LENGTH_LONG) // OK 6
                        toast.also {
                            it.show()
                        }
                    }

                    fun testAlsoBroken(context: Context, unrelated: Unrelated) {
                        val toast = Toast.makeText(context, R.string.app_name, Toast.LENGTH_LONG) // ERROR 5
                        toast.also {
                            unrelated.show()
                        }
                    }

                    fun testRunOk(context: Context, unrelated: Unrelated?) {
                        val toast = Toast.makeText(context, R.string.app_name, Toast.LENGTH_LONG) // OK 7
                        toast.run {
                            show()
                        }
                    }

                    @Suppress("RedundantWith")
                    fun testWithReturn(context: Context, unrelated: Unrelated?) =
                        with (Toast.makeText(context, R.string.app_name, Toast.LENGTH_LONG)) { // OK 8
                            show()
                    }

                    fun testApplyReturn(context: Context, unrelated: Unrelated?) =
                        Toast.makeText(context, R.string.app_name, Toast.LENGTH_LONG).apply { // OK 9
                            show()
                        }
                }
                """
            ).indented(),
            rClass
        ).testModes(TestMode.DEFAULT).issues(ToastDetector.ISSUE).run().expect(
            """
            src/test/pkg/StandardTest.kt:16: Warning: Toast created but not shown: did you forget to call show()? [ShowToast]
                    val toast = Toast.makeText(context, R.string.app_name, Toast.LENGTH_LONG) // ERROR 1
                                ~~~~~~~~~~~~~~
            src/test/pkg/StandardTest.kt:44: Warning: Toast created but not shown: did you forget to call show()? [ShowToast]
                    val toast = Toast.makeText(context, R.string.app_name, Toast.LENGTH_LONG) // ERROR 2
                                ~~~~~~~~~~~~~~
            src/test/pkg/StandardTest.kt:55: Warning: Toast created but not shown: did you forget to call show()? [ShowToast]
                    val toast = Toast.makeText(context, R.string.app_name, Toast.LENGTH_LONG) // ERROR 3
                                ~~~~~~~~~~~~~~
            src/test/pkg/StandardTest.kt:76: Warning: Toast created but not shown: did you forget to call show()? [ShowToast]
                    val toast = Toast.makeText(context, R.string.app_name, Toast.LENGTH_LONG) // ERROR 4
                                ~~~~~~~~~~~~~~
            src/test/pkg/StandardTest.kt:90: Warning: Toast created but not shown: did you forget to call show()? [ShowToast]
                    val toast = Toast.makeText(context, R.string.app_name, Toast.LENGTH_LONG) // ERROR 5
                                ~~~~~~~~~~~~~~
            0 errors, 5 warnings
            """
        )
    }

    fun testNestedExtensionMethods() {
        lint().files(
            kotlin(
                """
                @file:Suppress("unused")

                package test.pkg

                import android.content.Context
                import android.widget.Toast

                class ExtensionAndNesting {
                    fun test1(context: Context) {
                        Toast.makeText(context, R.string.app_name, Toast.LENGTH_LONG) // OK 1
                            .extension().show()
                    }

                    fun test2(context: Context) {
                        Toast.makeText(context, R.string.app_name, Toast.LENGTH_LONG) // OK 2
                            .extension().extension().show()
                    }

                    fun test3(context: Context) {
                        Toast.makeText(context, R.string.app_name, Toast.LENGTH_LONG) // OK 3
                            .apply {
                                show()
                            }
                    }

                    fun test4(context: Context) {
                        Toast.makeText(context, R.string.app_name, Toast.LENGTH_LONG) // OK 4
                            .apply {
                                extension().show()
                            }
                    }

                    fun test5(context: Context) {
                        Toast.makeText(context, R.string.app_name, Toast.LENGTH_LONG) // OK 5
                            .apply {
                                extension().apply {
                                    show()
                                }
                            }
                    }

                    fun test6(context: Context) {
                        Toast.makeText(context, R.string.app_name, Toast.LENGTH_LONG) // OK 6
                            .extension().also {
                                it.show()
                            }
                    }

                    fun test7(context: Context) {
                        Toast.makeText(context, R.string.app_name, Toast.LENGTH_LONG) // OK 7
                            .apply {
                                extension().apply {
                                    extension().also {
                                        it.show()
                                    }
                                }
                            }
                    }
                }
                private fun Toast.extension(): Toast = this
                """
            ).indented(),
            rClass
        ).testModes(TestMode.DEFAULT).issues(ToastDetector.ISSUE).run().expectClean()
    }

    fun testBlocksAndReturns() {
        lint().files(
            kotlin(
                """
                @file:Suppress("unused")

                package test.pkg

                import android.content.Context
                import android.widget.Toast

                class ExtensionAndNesting {
                    fun testRun(context: Context) =
                        Toast.makeText(context, R.string.app_name, Toast.LENGTH_LONG).run { // OK 1
                            this.toString()
                            show()
                        }

                    fun testWith(context: Context) =
                        with(Toast.makeText(context, R.string.app_name, Toast.LENGTH_LONG)) { // OK 2
                            show()
                            this.toString()
                        }

                    fun test(c: Context, r: Int, d: Int) {
                        with(Toast.makeText(c, r, d)) { show() } // OK 3

                        // Returning context object
                        Toast.makeText(c, r, d).also { println("it") }.show()  // OK 4
                        Toast.makeText(c, r, d).apply { println("it") }.show() // OK 5
                        // Returning lambda result
                        with("hello") { Toast.makeText(c, r, d) }.show() // OK 6
                        "hello".let { Toast.makeText(c, r, d) }.show() // OK 7
                        "hello".run { Toast.makeText(c, r, d) }.show() // OK 8
                    }

                    fun testContextReturns(c: Context, r: Int, d: Int) {
                        val toast1 = Toast.makeText(c, r, d) // OK
                        toast1.apply {
                            Toast.makeText(c, r, d) // ERROR 1
                        }.show() // applies to toast1, not lambda result

                        Toast.makeText(c, r, d).also { // OK
                            Toast.makeText(c, r, d) // ERROR 2
                        }.show() // Applies to context object, not lambda result
                    }

                    fun testReturns(c: Context, r: Int, d: Int) {
                        return Toast.makeText(c, r, d).show()  // OK 9
                    }

                    fun testReturns2(c: Context, r: Int, d: Int) = Toast.makeText(c, r, d).show() // OK 10
                }

                private fun Toast.extension(): Toast = this
                """
            ).indented(),
            rClass
        ).testModes(TestMode.DEFAULT).issues(ToastDetector.ISSUE).run().expect(
            """
            src/test/pkg/ExtensionAndNesting.kt:36: Warning: Toast created but not shown: did you forget to call show()? [ShowToast]
                        Toast.makeText(c, r, d) // ERROR 1
                        ~~~~~~~~~~~~~~
            src/test/pkg/ExtensionAndNesting.kt:40: Warning: Toast created but not shown: did you forget to call show()? [ShowToast]
                        Toast.makeText(c, r, d) // ERROR 2
                        ~~~~~~~~~~~~~~
            0 errors, 2 warnings
            """
        )
    }

    fun testNonTopLevelReferences() {
        // References to this are not direct children inside the lambda
        lint().files(
            kotlin(
                """
                import android.view.View
                import com.google.android.material.snackbar.Snackbar

                fun test(parent: View, msg: String, duration: Int) {
                    Snackbar.make(parent, msg, duration).apply { // OK 1
                        if (true) {
                            show()
                        }
                    }
                }
                """
            ).indented(),
            *snackbarStubs
        ).issues(ToastDetector.ISSUE).run().expectClean()
    }

    fun testNestedLambdas() {
        lint().files(
            kotlin(
                """
                import android.view.View
                import com.google.android.material.snackbar.BaseTransientBottomBar
                import com.google.android.material.snackbar.Snackbar

                fun test(parent: View, msg: String, duration: Int, bar: BaseTransientBottomBar<Snackbar>) {
                    Snackbar.make(parent, msg, duration).apply { // ERROR 1
                        Snackbar.make(parent, msg, duration).apply { // OK 1
                            show()
                        }
                    }

                    Snackbar.make(parent, msg, duration).apply { // ERROR 2
                        bar.apply {
                            show()
                        }
                    }

                    Snackbar.make(parent, msg, duration).apply { // OK 2
                        setActionTextColor(0).show()
                    }

                    Snackbar.make(parent, msg, duration).apply { // OK 3
                        // Here, setAnchorView is a Snackbar method, but it's
                        // inherited, so its type is not == Snackbar.
                        setAnchorView(parent).show()
                    }

                    Snackbar.make(parent, msg, duration).apply { // OK 4
                        "hello".apply {
                            show()
                        }
                    }

                    Snackbar.make(parent, msg, duration).apply { // OK 5
                        // Checking explicit this reference, since it has a different AST node
                        this.show()
                    }
                }

                """
            ).indented(),
            *snackbarStubs
        ).issues(ToastDetector.ISSUE).run().expect(
            """
            src/test.kt:6: Warning: Snackbar created but not shown: did you forget to call show()? [ShowToast]
                Snackbar.make(parent, msg, duration).apply { // ERROR 1
                ~~~~~~~~~~~~~
            src/test.kt:12: Warning: Snackbar created but not shown: did you forget to call show()? [ShowToast]
                Snackbar.make(parent, msg, duration).apply { // ERROR 2
                ~~~~~~~~~~~~~
            0 errors, 2 warnings
            """
        )
    }

    fun testNestedScopes() {
        lint().files(
            kotlin(
                """
                @file:Suppress("unused")

                package test.pkg

                import android.content.Context
                import android.view.View
                import android.widget.Toast
                import com.google.android.material.snackbar.Snackbar

                fun test(context: Context, unrelated: Toast) {
                    val toast = Toast.makeText(context, R.string.app_name, Toast.LENGTH_LONG) // ERROR 1
                    toast.apply {
                        unrelated.show()
                    }
                }

                fun test2(context: Context) {
                    Toast.makeText(context, R.string.app_name, Toast.LENGTH_LONG) // OK 1
                        .apply {
                            extension().also {
                                // This would bind to the current also block
                                //it.show()
                                // but this binds to the outer apply block because
                                // there's no show()
                                show()
                            }
                        }
                }

                fun test3(context: Context) {
                    Toast.makeText(context, R.string.app_name, Toast.LENGTH_LONG) // OK 2
                        .apply {
                            "hello".apply {
                                show()
                            }
                        }
                }

                fun test4(parent: View, msg: String, duration: Int) {
                    Snackbar.make(parent, msg, duration).apply { // ERROR 2
                        fun show() { }
                        show()
                    }
                }

                private fun Toast.extension(): Toast = this
                """
            ).indented(),
            rClass,
            *snackbarStubs
        ).testModes(TestMode.DEFAULT).issues(ToastDetector.ISSUE).run()
            .expect(
                """
                src/test/pkg/test.kt:11: Warning: Toast created but not shown: did you forget to call show()? [ShowToast]
                    val toast = Toast.makeText(context, R.string.app_name, Toast.LENGTH_LONG) // ERROR 1
                                ~~~~~~~~~~~~~~
                src/test/pkg/test.kt:40: Warning: Snackbar created but not shown: did you forget to call show()? [ShowToast]
                    Snackbar.make(parent, msg, duration).apply { // ERROR 2
                    ~~~~~~~~~~~~~
                0 errors, 2 warnings
                """
            )
    }

    fun testIgnoredArguments() {
        lint().files(
            kotlin(
                """
                import android.content.Context
                import android.util.Log
                import android.widget.Toast

                fun test(c: Context, r: Int, d: Int) {
                    val toast = Toast.makeText(c, r, d) // ERROR
                    // Both of these calls should be ignored via ignoreArgument so shouldn't be considered an escape
                    println(toast)
                    Log.d("tag", toast.toString())
                }
                """
            ).indented(),
            *snackbarStubs
        ).issues(ToastDetector.ISSUE).run().expect(
            """
            src/test.kt:6: Warning: Toast created but not shown: did you forget to call show()? [ShowToast]
                val toast = Toast.makeText(c, r, d) // ERROR
                            ~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }

    fun testClone() {
        // Methods that are named clone (& similar) should not be treated as transferring
        // the value even though their types match the expectation
        lint().files(
            kotlin(
                """
                import android.view.View
                import com.google.android.material.snackbar.Snackbar

                fun test(parent: View, msg: String, duration: Int) {
                    Snackbar.make(parent, msg, duration).clone().toDebug().show() // ERROR
                }
                """
            ).indented(),
            // Note: using a different stub here since we're adding methods that don't exist in a real snackbar
            // to simulate this scenario
            java(
                """
                package com.google.android.material.snackbar;
                import android.view.View;
                public class Snackbar {
                    public void show() { }
                    public static Snackbar make(View view, int resId, int duration) {
                       return null;
                    }
                    public Snackbar clone() { return new Snackbar(); }
                    public Snackbar toDebug() { return new Snackbar(); }
                }
                """
            )
        ).issues(ToastDetector.ISSUE).run().expect(
            """
            src/test.kt:5: Warning: Snackbar created but not shown: did you forget to call show()? [ShowToast]
                Snackbar.make(parent, msg, duration).clone().toDebug().show() // ERROR
                ~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }

    fun testCasts() {
        lint().files(
            kotlin(
                """
                import android.content.Context
                import android.widget.Toast

                class MyToast(context: Context) : Toast(context) {
                    fun intermediate(): MyToast {
                        return this
                    }
                }

                fun test1(context: Context, s: Int, d: Int) {
                    val toast = Toast.makeText(context, s, d) as Toast // OK 1
                    toast.show()
                }

                fun test2(context: Context, s: Int, d: Int) {
                    val toast = Toast.makeText(context, s, d) as MyToast // OK 2
                    toast.show()
                }

                fun test3(context: Context, s: Int, d: Int) {
                    val toast = Toast.makeText(context, s, d) as MyToast // OK 3
                    toast.intermediate().show()
                }
                """
            ).indented(),
        ).issues(ToastDetector.ISSUE).run().expectClean()
    }

    fun testArgumentCalls() {
        // Make sure we visit the registerReceiver call exactly once
        val parsed = LintUtilsTest.parse(
            kotlin(
                """
                package test.pkg

                import android.content.BroadcastReceiver
                import android.content.Context
                import android.content.IntentFilter

                class Test {
                    fun testNew(context: Context, receiver: BroadcastReceiver) {
                        context.registerReceiver(receiver, IntentFilter("ppp").apply { addAction("ooo") })
                    }
                }
                """,
            ).indented()
        )

        val target = findMethodCall(parsed, "IntentFilter")

        val calls = mutableListOf<UCallExpression>()
        val method = target.getParentOfType(UMethod::class.java)
        method?.accept(object : DataFlowAnalyzer(listOf(target)) {
            override fun argument(call: UCallExpression, reference: UElement) {
                assertTrue(calls.add(call))
            }
        })
        assertEquals(1, calls.size)
        assertSame(calls[0], target.getParentOfType(UCallExpression::class.java, strict = true))
        Disposer.dispose(parsed.second)
    }

    private fun lint() = TestLintTask.lint().sdkHome(TestUtils.getSdk().toFile())

    private val rClass: TestFile = rClass("test.pkg", "@string/app_name")
}
