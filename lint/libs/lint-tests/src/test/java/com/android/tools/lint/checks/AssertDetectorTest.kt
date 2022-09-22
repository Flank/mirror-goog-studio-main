/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Platform

class AssertDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return AssertDetector()
    }

    fun testNotExpensive() {
        lint().files(
            kotlin(
                """
                import org.w3c.dom.Node
                fun test(override: String, offset: Int, textNode: Node) {
                    assert(parentOf[override] == null) // OK 1
                    assert(parentOf.isNotEmpty()) // OK 2
                    assert(parentOf.size == 1) // OK 3
                    assert(!override.contains(",")) // OK 4
                    assert(override[offset] != ']') // OK 5
                    assert(textNode.nodeType == Node.TEXT_NODE || textNode.nodeType == Node.COMMENT_NODE) // OK 6
                }

                private val parentOf: MutableMap<String, String> = HashMap()
                """
            ).indented()
        )
            .issues(AssertDetector.EXPENSIVE)
            .platforms(Platform.JDK_SET)
            .run().expectClean()
    }

    fun testExpensiveKotlinCalls() {
        // This lint check also applies outside of Android
        lint().files(
            kotlinTestFile,
            kotlin(
                """
                fun testExpensive() {
                    assert(expensive()) // no suggestion to surround with javaClass from toplevel
                    assert(cheap())
                    assert(cheap2(0))
                    assert(cheap3())
                }
                private fun expensive(): Boolean {
                    Thread.sleep(500)
                    return true
                }

                const val DEBUGGING = false
                private fun cheap(): Boolean {
                    return DEBUGGING
                }
                private fun cheap2(x: Int): Boolean = x < 10
                private fun cheap3() = test.pkg.Utils.isDiagnosing()

                fun castOkay(foo: Any) {
                    assert(foo is String) // OK
                }
                """
            ).indented(),
            java(
                """
                package test.pkg;
                public class Utils {
                    public static final boolean DIAGNOSE = false;
                    public static boolean isDiagnosing() {
                        return DIAGNOSE;
                    }
                }
                """
            ).indented(),
            kotlinAssertionRuntime
        )
            .skipTestModes(TestMode.PARENTHESIZED)
            .issues(AssertDetector.EXPENSIVE)
            .platforms(Platform.JDK_SET)
            .run()
            .expect(
                """
            src/test/pkg/AssertTest.kt:18: Warning: Kotlin assertion arguments are always evaluated, even when assertions are off. Consider surrounding assertion with if (javaClass.desiredAssertionStatus()) { assert(...) } [ExpensiveAssertion]
                    assert(expensive()) // WARN
                           ~~~~~~~~~~~
            src/test.kt:2: Warning: Kotlin assertion arguments are always evaluated, even when assertions are off [ExpensiveAssertion]
                assert(expensive()) // no suggestion to surround with javaClass from toplevel
                       ~~~~~~~~~~~
            0 errors, 2 warnings
            """
            ).expectFixDiffs(
                """
            Fix for src/test/pkg/AssertTest.kt line 18: Surround with desiredAssertionStatus() check:
            @@ -18 +18
            -         assert(expensive()) // WARN
            +         if (javaClass.desiredAssertionStatus()) { assert(expensive()) } // WARN
            """
            )
    }

    fun testSideEffects() {
        // This lint check also applies outside of Android
        lint().files(
            kotlin(
                """
                var x: Int = 0
                fun test(file: java.io.File, list: java.util.List<String>) {
                    var i = 0
                    assert(i++ < 5) // WARN 1
                    assert(method1() > 5) // WARN 2
                    assert(method2() > 5) // WARN 3
                    assert(method3()) // WARN 4
                    assert(method4()) // OK 1
                    assert(file.delete()) // WARN 5
                    assert(file.mkdirs()) // WARN 6
                    assert(list.add("test")) // WARN 7
                    assert(file.setExecutable(true)) // WARN 8
                    assert(list.contains("test")) // OK 2
                    assert(method5()) // WARN 9
                    assert(method6()) // WARN 10
                    assert(method7()) // WARN 11
                }

                fun method1(): Int = x++ // side effect
                fun method2(): Int = method1() // indirect side effect
                fun method3(): Boolean {
                    x = 0 // side effect
                    return true
                }
                fun method4(): Boolean {
                    val x: Int
                    x = 0 // not a side effect
                    x++ // not a side effect
                    return true
                }
                fun method5(v: Int): Boolean {
                    if (v > 5) { } else { x++ }
                    return true
                }
                fun method6(v: Int): Boolean {
                    for (i in 0 until v) x++
                    return true
                }
                fun method7(v: Int): Boolean {
                    try { println(v) } finally { x++ }
                    return true
                }
                """
            ).indented(),
            java(
                """
                package test.pkg;
                public class Utils {
                    public void test() {
                        int i = 0;
                        assert i++ < 5;
                    }
                }
                """
            ).indented(),
            kotlinAssertionRuntime
        )
            .issues(AssertDetector.SIDE_EFFECT)
            .platforms(Platform.JDK_SET)
            .testModes(TestMode.DEFAULT)
            .run()
            .expect(
                """
                src/test/pkg/Utils.java:5: Warning: Assertion condition has a side effect: i++ [AssertionSideEffect]
                        assert i++ < 5;
                               ~~~
                src/test.kt:4: Warning: Assertion condition has a side effect: i++ [AssertionSideEffect]
                    assert(i++ < 5) // WARN 1
                           ~~~
                src/test.kt:5: Warning: Assertion condition has a side effect: x++ [AssertionSideEffect]
                    assert(method1() > 5) // WARN 2
                           ~~~~~~~~~
                src/test.kt:6: Warning: Assertion condition has a side effect: x++ [AssertionSideEffect]
                    assert(method2() > 5) // WARN 3
                           ~~~~~~~~~
                src/test.kt:7: Warning: Assertion condition has a side effect: x = 0 [AssertionSideEffect]
                    assert(method3()) // WARN 4
                           ~~~~~~~~~
                src/test.kt:9: Warning: Assertion condition has a side effect: delete() [AssertionSideEffect]
                    assert(file.delete()) // WARN 5
                           ~~~~~~~~~~~~~
                src/test.kt:10: Warning: Assertion condition has a side effect: mkdirs() [AssertionSideEffect]
                    assert(file.mkdirs()) // WARN 6
                           ~~~~~~~~~~~~~
                src/test.kt:11: Warning: Assertion condition has a side effect: add("test") [AssertionSideEffect]
                    assert(list.add("test")) // WARN 7
                           ~~~~~~~~~~~~~~~~
                src/test.kt:12: Warning: Assertion condition has a side effect: setExecutable(true) [AssertionSideEffect]
                    assert(file.setExecutable(true)) // WARN 8
                           ~~~~~~~~~~~~~~~~~~~~~~~~
                src/test.kt:14: Warning: Assertion condition has a side effect: x++ [AssertionSideEffect]
                    assert(method5()) // WARN 9
                           ~~~~~~~~~
                src/test.kt:15: Warning: Assertion condition has a side effect: x++ [AssertionSideEffect]
                    assert(method6()) // WARN 10
                           ~~~~~~~~~
                src/test.kt:16: Warning: Assertion condition has a side effect: x++ [AssertionSideEffect]
                    assert(method7()) // WARN 11
                           ~~~~~~~~~
                0 errors, 12 warnings
                """
            )
    }

    fun testSideEffect() {
        lint().files(
            java(
                """
                package test.pkg;

                public class SideEffectTest {
                    public void test(int x) {
                        assert something(x);
                    }

                    private boolean something(int x) {
                        if (x < 5) {
                            return true;
                        }

                        x = align(x);
                        return x == 40;
                    }

                    private int align(int x) {
                        return x % 2;
                    }
                }
                """
            )
        )
            .issues(AssertDetector.SIDE_EFFECT)
            .platforms(Platform.JDK_SET)
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }

    private val kotlinTestFile = kotlin(
        """
                package test.pkg

                class AssertTest {
                    fun test() {
                        assert(true) // Error on Android, not elsewhere
                        assert(false) // Error on Android, not elsewhere
                        assert(true, { "My lazy message" }) // Error on Android, not elsewhere
                        assert(true) { "My lazy message" } // Error on Android, not elsewhere
                    }

                    fun testNotExpensive(int: Int, bool1: Boolean, bool2: Boolean) {
                        assert(int < 5)
                        assert(!bool1 || bool2 == bool1)
                        assert(int != bool1) { "This is ＄int and x2=＄bool2" } // ERROR
                    }

                    fun testExpensive() {
                        assert(expensive()) // WARN
                    }

                    fun testOk() {
                        if (AssertTest::class.java.desiredAssertionStatus()) {
                            assert(expensive()) // Error on Android, ok elsewhere
                        }
                        if (javaClass.desiredAssertionStatus()) {
                            assert(expensive()) // Error on Android, ok elsewhere
                        }
                    }

                    private fun expensive(): Boolean {
                        Thread.sleep(500)
                        return (System.currentTimeMillis().rem(1L)) == 0L
                    }

                    fun foo(x1: Boolean, x2: Boolean, x3: Number?) {
                        assert(x1 == x2)  // ERROR
                        assert(x3 != null) // ERROR
                    }
                }
                """
    ).indented()

    private val kotlinAssertionRuntime = kotlin(
        """
        @file:kotlin.jvm.JvmName("PreconditionsKt")
        package kotlin

        fun assert(value: Boolean) {
            @Suppress("Assert", "KotlinAssert")
            assert(value) { "Assertion failed" }
        }

        fun assert(value: Boolean, lazyMessage: () -> Any) {
        }
        """
    ).indented()
}
