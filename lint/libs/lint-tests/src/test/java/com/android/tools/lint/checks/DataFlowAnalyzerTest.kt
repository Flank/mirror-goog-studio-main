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
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintUtilsTest
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import junit.framework.TestCase
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.visitor.AbstractUastVisitor
import org.junit.Assert.assertNotEquals
import java.io.File

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
        target.getParentOfType<UMethod>(UMethod::class.java)?.accept(analyzer)

        assertEquals("e, f, g, toString", receivers.joinToString { it })

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
        target.getParentOfType<UMethod>(UMethod::class.java)?.accept(analyzer)

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
                }
                return super.visitCallExpression(node)
            }
        })
        assertNotNull(target)
        return target!!
    }

    fun testKotlinStandardFunctions() {
        // Makes sure the semantics of let, apply, also, with and run are handled correctly.
        // Regression test for https://issuetracker.google.com/187437289.
        TestLintTask.lint().sdkHome(TestUtils.getSdk().toFile()).files(
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
            java(
                """
                package test.pkg;
                public class R {
                    public static final class string {
                        public static final int app_name = 0x7f0a0000;
                    }
                }
                """
            ).indented()
        ).testModes(TestMode.DEFAULT).issues(ToastDetector.ISSUE).run().expect(
            """
            src/test/pkg/StandardTest.kt:16: Warning: Toast created but not shown: did you forget to call show() ? [ShowToast]
                    val toast = Toast.makeText(context, R.string.app_name, Toast.LENGTH_LONG) // ERROR 1
                                ~~~~~~~~~~~~~~
            src/test/pkg/StandardTest.kt:44: Warning: Toast created but not shown: did you forget to call show() ? [ShowToast]
                    val toast = Toast.makeText(context, R.string.app_name, Toast.LENGTH_LONG) // ERROR 2
                                ~~~~~~~~~~~~~~
            src/test/pkg/StandardTest.kt:55: Warning: Toast created but not shown: did you forget to call show() ? [ShowToast]
                    val toast = Toast.makeText(context, R.string.app_name, Toast.LENGTH_LONG) // ERROR 3
                                ~~~~~~~~~~~~~~
            src/test/pkg/StandardTest.kt:76: Warning: Toast created but not shown: did you forget to call show() ? [ShowToast]
                    val toast = Toast.makeText(context, R.string.app_name, Toast.LENGTH_LONG) // ERROR 4
                                ~~~~~~~~~~~~~~
            src/test/pkg/StandardTest.kt:90: Warning: Toast created but not shown: did you forget to call show() ? [ShowToast]
                    val toast = Toast.makeText(context, R.string.app_name, Toast.LENGTH_LONG) // ERROR 5
                                ~~~~~~~~~~~~~~
            0 errors, 5 warnings
            """
        )
    }
}
