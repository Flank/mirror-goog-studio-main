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

    fun testJavaAssertions() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.annotation.SuppressLint;

                public class Assert {
                    public Assert(int param, Object param2, Object param3) {
                        assert false;                              // ERROR
                        assert param > 5 : "My description";       // ERROR
                        assert param2 == param3;                   // ERROR
                        assert param2 != null && param3 == param2; // ERROR
                        assert true;                               // OK
                        assert param2 == null;                     // OK
                        assert param2 != null && param3 == null;   // OK
                        assert param2 == null && param3 != null;   // OK
                        assert param2 != null && param3 != null;   // OK
                        assert null != param2;                     // OK
                        assert param2 != null;                     // OK
                        assert param2 != null : "My description";  // OK
                        assert checkSuppressed(5) != null;         // OK
                        assert (param2 != null);                   // OK
                        assert param != null && param2 != null && param3 != null;   // OK
                        assert param != param2 : "This is " + param2 + " and param3=" + param3;
                        assert param2 instanceof String;           // OK
                    }

                    @SuppressLint("Assert")
                    public static Object checkSuppressed(int param) {
                        assert param > 5 : "My description";
                        return null;
                    }
                }
                """
            ).indented()
        ).run().expect(
            """
                src/test/pkg/Assert.java:7: Warning: Assertions are never enabled in Android. Use BuildConfig.DEBUG conditional checks instead [Assert]
                        assert false;                              // ERROR
                        ~~~~~~
                src/test/pkg/Assert.java:8: Warning: Assertions are never enabled in Android. Use BuildConfig.DEBUG conditional checks instead [Assert]
                        assert param > 5 : "My description";       // ERROR
                        ~~~~~~
                src/test/pkg/Assert.java:9: Warning: Assertions are never enabled in Android. Use BuildConfig.DEBUG conditional checks instead [Assert]
                        assert param2 == param3;                   // ERROR
                        ~~~~~~
                src/test/pkg/Assert.java:10: Warning: Assertions are never enabled in Android. Use BuildConfig.DEBUG conditional checks instead [Assert]
                        assert param2 != null && param3 == param2; // ERROR
                        ~~~~~~
                src/test/pkg/Assert.java:22: Warning: Assertions are never enabled in Android. Use BuildConfig.DEBUG conditional checks instead [Assert]
                        assert param != param2 : "This is " + param2 + " and param3=" + param3;
                        ~~~~~~
                0 errors, 5 warnings
                """
        ).expectFixDiffs(
            """
            Fix for src/test/pkg/Assert.java line 7: Replace with BuildConfig.DEBUG check:
            @@ -7 +7
            -         assert false;                              // ERROR
            +         if (lint.BuildConfig.DEBUG) { throw new AssertionError("Assertion failed"); }
            Fix for src/test/pkg/Assert.java line 8: Replace with BuildConfig.DEBUG check:
            @@ -8 +8
            -         assert param > 5 : "My description";       // ERROR
            +         if (lint.BuildConfig.DEBUG && param <= 5) { throw new AssertionError("My description"); }
            Fix for src/test/pkg/Assert.java line 9: Replace with BuildConfig.DEBUG check:
            @@ -9 +9
            -         assert param2 == param3;                   // ERROR
            +         if (lint.BuildConfig.DEBUG && param2 != param3) { throw new AssertionError("Assertion failed"); }
            Fix for src/test/pkg/Assert.java line 10: Replace with BuildConfig.DEBUG check:
            @@ -10 +10
            -         assert param2 != null && param3 == param2; // ERROR
            +         if (lint.BuildConfig.DEBUG && !(param2 != null && param3 == param2)) { throw new AssertionError("Assertion failed"); }
            Fix for src/test/pkg/Assert.java line 22: Replace with BuildConfig.DEBUG check:
            @@ -22 +22
            -         assert param != param2 : "This is " + param2 + " and param3=" + param3;
            +         if (lint.BuildConfig.DEBUG && param == param2) { throw new AssertionError("This is " + param2 + " and param3=" + param3); }
            """
        )
    }

    fun testKotlin() {
        lint().files(
            kotlinTestFile,
            kotlinAssertionRuntime,
            manifest().pkg("test.pkg")
        ).issues(AssertDetector.DISABLED).run().expect(
            """
            src/test/pkg/AssertTest.kt:5: Warning: Assertions are never enabled in Android. Use BuildConfig.DEBUG conditional checks instead [Assert]
                    assert(true) // Error on Android, not elsewhere
                    ~~~~~~~~~~~~
            src/test/pkg/AssertTest.kt:6: Warning: Assertions are never enabled in Android. Use BuildConfig.DEBUG conditional checks instead [Assert]
                    assert(false) // Error on Android, not elsewhere
                    ~~~~~~~~~~~~~
            src/test/pkg/AssertTest.kt:7: Warning: Assertions are never enabled in Android. Use BuildConfig.DEBUG conditional checks instead [Assert]
                    assert(true, { "My lazy message" }) // Error on Android, not elsewhere
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/AssertTest.kt:8: Warning: Assertions are never enabled in Android. Use BuildConfig.DEBUG conditional checks instead [Assert]
                    assert(true) { "My lazy message" } // Error on Android, not elsewhere
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/AssertTest.kt:12: Warning: Assertions are never enabled in Android. Use BuildConfig.DEBUG conditional checks instead [Assert]
                    assert(int < 5)
                    ~~~~~~~~~~~~~~~
            src/test/pkg/AssertTest.kt:13: Warning: Assertions are never enabled in Android. Use BuildConfig.DEBUG conditional checks instead [Assert]
                    assert(!bool1 || bool2 == bool1)
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/AssertTest.kt:14: Warning: Assertions are never enabled in Android. Use BuildConfig.DEBUG conditional checks instead [Assert]
                    assert(int != bool1) { "This is ＄int and x2=＄bool2" } // ERROR
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/AssertTest.kt:18: Warning: Assertions are never enabled in Android. Use BuildConfig.DEBUG conditional checks instead [Assert]
                    assert(expensive()) // WARN
                    ~~~~~~~~~~~~~~~~~~~
            src/test/pkg/AssertTest.kt:23: Warning: Assertions are never enabled in Android. Use BuildConfig.DEBUG conditional checks instead [Assert]
                        assert(expensive()) // Error on Android, ok elsewhere
                        ~~~~~~~~~~~~~~~~~~~
            src/test/pkg/AssertTest.kt:26: Warning: Assertions are never enabled in Android. Use BuildConfig.DEBUG conditional checks instead [Assert]
                        assert(expensive()) // Error on Android, ok elsewhere
                        ~~~~~~~~~~~~~~~~~~~
            src/test/pkg/AssertTest.kt:36: Warning: Assertions are never enabled in Android. Use BuildConfig.DEBUG conditional checks instead [Assert]
                    assert(x1 == x2)  // ERROR
                    ~~~~~~~~~~~~~~~~
            src/test/pkg/AssertTest.kt:37: Warning: Assertions are never enabled in Android. Use BuildConfig.DEBUG conditional checks instead [Assert]
                    assert(x3 != null) // ERROR
                    ~~~~~~~~~~~~~~~~~~
            0 errors, 12 warnings
            """
        ).expectFixDiffs(
            """
            Fix for src/test/pkg/AssertTest.kt line 6: Replace with BuildConfig.DEBUG check:
            @@ -6 +6
            -         assert(false) // Error on Android, not elsewhere
            +         if (BuildConfig.DEBUG) { error("Assertion failed") } // Error on Android, not elsewhere
            Fix for src/test/pkg/AssertTest.kt line 12: Replace with BuildConfig.DEBUG check:
            @@ -12 +12
            -         assert(int < 5)
            +         if (BuildConfig.DEBUG && int >= 5) { error("Assertion failed") }
            Fix for src/test/pkg/AssertTest.kt line 13: Replace with BuildConfig.DEBUG check:
            @@ -13 +13
            -         assert(!bool1 || bool2 == bool1)
            +         if (BuildConfig.DEBUG && !(!bool1 || bool2 == bool1)) { error("Assertion failed") }
            Fix for src/test/pkg/AssertTest.kt line 14: Replace with BuildConfig.DEBUG check:
            @@ -14 +14
            -         assert(int != bool1) { "This is ＄int and x2=＄bool2" } // ERROR
            +         if (BuildConfig.DEBUG && int == bool1) { error("This is ＄int and x2=＄bool2") } // ERROR
            Fix for src/test/pkg/AssertTest.kt line 18: Replace with BuildConfig.DEBUG check:
            @@ -18 +18
            -         assert(expensive()) // WARN
            +         if (BuildConfig.DEBUG && !expensive()) { error("Assertion failed") } // WARN
            Fix for src/test/pkg/AssertTest.kt line 23: Replace with BuildConfig.DEBUG check:
            @@ -23 +23
            -             assert(expensive()) // Error on Android, ok elsewhere
            +             if (BuildConfig.DEBUG && !expensive()) { error("Assertion failed") } // Error on Android, ok elsewhere
            Fix for src/test/pkg/AssertTest.kt line 26: Replace with BuildConfig.DEBUG check:
            @@ -26 +26
            -             assert(expensive()) // Error on Android, ok elsewhere
            +             if (BuildConfig.DEBUG && !expensive()) { error("Assertion failed") } // Error on Android, ok elsewhere
            Fix for src/test/pkg/AssertTest.kt line 36: Replace with BuildConfig.DEBUG check:
            @@ -36 +36
            -         assert(x1 == x2)  // ERROR
            +         if (BuildConfig.DEBUG && x1 != x2) { error("Assertion failed") }  // ERROR
            Fix for src/test/pkg/AssertTest.kt line 37: Replace with BuildConfig.DEBUG check:
            @@ -37 +37
            -         assert(x3 != null) // ERROR
            +         if (BuildConfig.DEBUG && x3 == null) { error("Assertion failed") } // ERROR
            """
        )
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
