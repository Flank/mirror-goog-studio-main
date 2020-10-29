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

import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Platform

class AssertDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return AssertDetector()
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
        ).issues(AssertDetector.EXPENSIVE).platforms(Platform.JDK_SET).run().expect(
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
