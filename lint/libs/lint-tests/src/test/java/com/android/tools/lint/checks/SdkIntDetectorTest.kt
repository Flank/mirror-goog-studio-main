/*
 * Copyright (C) 2021 The Android Open Source Project
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

/** Unit tests for [SdkIntDetector] */
class SdkIntDetectorTest : AbstractCheckTest() {
    fun testDocumentationExample() {
        lint().files(
            manifest().minSdk(4),
            projectProperties().library(true),
            kotlin(
                """
                package test.pkg

                import android.os.Build
                import android.os.Build.VERSION
                import android.os.Build.VERSION.SDK_INT
                import android.os.Build.VERSION_CODES

                fun isNougat(): Boolean {
                    return VERSION.SDK_INT >= VERSION_CODES.N
                }

                fun isAtLeast(api: Int): Boolean {
                    return VERSION.SDK_INT >= api
                }

                inline fun <T> T.applyForOreoOrAbove(block: T.() -> Unit): T {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        block()
                    }
                    return this
                }
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/test.kt:8: Warning: This method should be annotated with @ChecksSdkIntAtLeast(api=VERSION_CODES.N) [AnnotateVersionCheck]
            fun isNougat(): Boolean {
                ~~~~~~~~
            src/test/pkg/test.kt:12: Warning: This method should be annotated with @ChecksSdkIntAtLeast(parameter=0) [AnnotateVersionCheck]
            fun isAtLeast(api: Int): Boolean {
                ~~~~~~~~~
            src/test/pkg/test.kt:16: Warning: This method should be annotated with @ChecksSdkIntAtLeast(api=Build.VERSION_CODES.O, lambda=1) [AnnotateVersionCheck]
            inline fun <T> T.applyForOreoOrAbove(block: T.() -> Unit): T {
                             ~~~~~~~~~~~~~~~~~~~
            0 errors, 3 warnings
            """
        )
    }

    fun testChecksSdkIntAtLeast() {
        lint().files(
            manifest().minSdk(4),
            projectProperties().library(true),
            kotlin(
                """
                @file:Suppress("unused", "RemoveRedundantQualifierName")

                package test.pkg

                import android.os.Build
                import android.os.Build.VERSION
                import android.os.Build.VERSION.SDK_INT
                import android.os.Build.VERSION_CODES
                import androidx.annotation.ChecksSdkIntAtLeast
                import androidx.core.os.BuildCompat

                fun isNougat1(): Boolean = VERSION.SDK_INT >= VERSION_CODES.N // 1: Should be annotated

                fun isNougat2(): Boolean { // 2: Should be annotated
                    return VERSION.SDK_INT >= VERSION_CODES.N
                }

                fun isAtLeast2(api: Int): Boolean { // 3: Should be annotated
                    return VERSION.SDK_INT >= api
                }

                fun isAtLeast2g(api: Int): Boolean = VERSION.SDK_INT >= api  // 4: Should be annotated

                private object Utils {
                    val isIcs: Boolean // 5: Should be annotated
                        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH
                    val isGingerbread: Boolean // 6: Should be annotated
                        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD
                }

                inline fun <T> T.applyForOreoOrAbove(block: T.() -> Unit): T { // 7: Should be annotated
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        block()
                    }
                    return this
                }

                inline fun <T> T.applyForOreoOrAbove2(block: T.() -> Unit): Unit { // 8: Should be annotated
                    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        block()
                    } else {
                        error("Unexpected")
                    }
                }

                inline fun <T> sdk(level: Int, func: () -> T): T? { // 9: Should be annotated
                    return if (Build.VERSION.SDK_INT >= level) {
                        func()
                    } else {
                        null
                    }
                }

                inline fun <T> sdk2(level: Int, func: () -> T): T? = // 10: Should be annotated
                    if (Build.VERSION.SDK_INT >= level) {
                        func()
                    } else {
                        null
                    }

                inline fun fromApi(value: Int, action: () -> Unit) { // 11: Should be annotated
                    if (Build.VERSION.SDK_INT >= value) {
                        action()
                    }
                }

                fun fromApiNonInline(value: Int, action: () -> Unit) { // 12: Should be annotated
                    if (Build.VERSION.SDK_INT >= value) {
                        action()
                    }
                }

                inline fun notFromApi(value: Int, action: () -> Unit) { // 13: Suggest in the future?
                    if (Build.VERSION.SDK_INT < value) {
                        action()
                    }
                }

                fun isAfterNougat(): Boolean { // 14: Should be annotated
                    return VERSION.SDK_INT > VERSION_CODES.N
                }

                @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.N)
                fun isNougat3(): Boolean {  // Should NOT annotate (already annotated)
                    return VERSION.SDK_INT >= VERSION_CODES.N
                }

                private var unrelated: Boolean = false
                fun unrelated(): Boolean {
                    unrelated = SDK_INT > VERSION_CODES.N; return false; } // Should NOT annotate

                fun isAtLeastN(): Boolean { // 15: Could annotate in the future
                    return BuildCompat.isAtLeastN()
                }

                fun isAtLeastN2(): Boolean = BuildCompat.isAtLeastN() // 16: Could annotate in the future
                """
            ).indented(),
            java(
                """
                package test.pkg;
                import android.os.Build;
                import androidx.core.os.BuildCompat;
                import static android.os.Build.VERSION.SDK_INT;
                import static android.os.Build.VERSION_CODES.N;
                import androidx.annotation.ChecksSdkIntAtLeast;

                public class JavaVersionChecks {
                    // Data-binding adds this method:
                    public static int getBuildSdkInt() {
                        return SDK_INT;
                    }
                    public static boolean isNougat1() { // 1: Should annotate
                        return getBuildSdkInt() >= N;
                    }
                    public static boolean isNougat2() { // 2: Should annotate
                        return SDK_INT >= N;
                    }
                    public static boolean isAfterNougat() { // 3: Should annotate
                        return SDK_INT >= N + 1;
                    }
                    public static boolean isAtLeast(int api) { // 4: Should annotate
                        return SDK_INT >= api;
                    }
                    public static boolean isAfter(int api) { // 5: Should annotate
                        return SDK_INT > api;
                    }
                    public static boolean isAtLeastZ() { // 6: Should annotate
                        return SDK_INT >= 36;
                    }
                    public static final boolean SUPPORTS_LETTER_SPACING = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP; // 7: Should annotate
                    private boolean isLollipop() { // 8: Should annotate
                        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
                    }
                    private static final int STASHED_VERSION = Build.VERSION.SDK_INT;
                    public static boolean isIcs() { // 9: Should annotate
                        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH;
                    }
                    public static boolean isGingerbread() { // 10: Should annotate
                        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD;
                    }

                    public static void runOnNougat(Runnable runnable) { // 11: Should annotate
                        if (SDK_INT >= N) {
                            runnable.run();
                        }
                    }
                    public static void runOnAny(int api, Runnable runnable) { // 12: Should annotate
                        if (SDK_INT >= api) {
                            runnable.run();
                        }
                    }

                    public static boolean notJustAVersionCheck() { // Should NOT annotate
                        System.out.println("Side effect");
                        return SDK_INT >= N;
                    }

                    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.N)
                    public static boolean isNougat2() {  // Should NOT annotate (already annotated)
                        return SDK_INT >= N;
                    }

                    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.LOLLIPOP)
                    public static final boolean SUPPORTS_LETTER_SPACING2 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP; // Should NOT annotate

                    public static boolean isAtLeastN() { // 13: Could annotate in the future
                        return BuildCompat.isAtLeastN();
                    }
                }
                """
            ).indented(),
            java(
                """
                package test.pkg;
                import static android.os.Build.VERSION.SDK_INT;
                import static android.os.Build.VERSION_CODES.N;

                public class NotImported {
                    public static boolean isNougat2() { // Should annotate
                        return SDK_INT >= N;
                    }
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        )
            // Allow BuildCompat to not resolve since we recognize it by name
            .allowCompilationErrors()
            .run().expect(
                """
            src/test/pkg/JavaVersionChecks.java:13: Warning: This method should be annotated with @ChecksSdkIntAtLeast(api=N) [AnnotateVersionCheck]
                public static boolean isNougat1() { // 1: Should annotate
                                      ~~~~~~~~~
            src/test/pkg/JavaVersionChecks.java:16: Warning: This method should be annotated with @ChecksSdkIntAtLeast(api=N) [AnnotateVersionCheck]
                public static boolean isNougat2() { // 2: Should annotate
                                      ~~~~~~~~~
            src/test/pkg/JavaVersionChecks.java:19: Warning: This method should be annotated with @ChecksSdkIntAtLeast(api=android.os.Build.VERSION_CODES.N_MR1) [AnnotateVersionCheck]
                public static boolean isAfterNougat() { // 3: Should annotate
                                      ~~~~~~~~~~~~~
            src/test/pkg/JavaVersionChecks.java:22: Warning: This method should be annotated with @ChecksSdkIntAtLeast(parameter=0) [AnnotateVersionCheck]
                public static boolean isAtLeast(int api) { // 4: Should annotate
                                      ~~~~~~~~~
            src/test/pkg/JavaVersionChecks.java:25: Warning: This method should be annotated with @ChecksSdkIntAtLeast(parameter=0) [AnnotateVersionCheck]
                public static boolean isAfter(int api) { // 5: Should annotate
                                      ~~~~~~~
            src/test/pkg/JavaVersionChecks.java:28: Warning: This method should be annotated with @ChecksSdkIntAtLeast(api=36) [AnnotateVersionCheck]
                public static boolean isAtLeastZ() { // 6: Should annotate
                                      ~~~~~~~~~~
            src/test/pkg/JavaVersionChecks.java:31: Warning: This field should be annotated with ChecksSdkIntAtLeast(api=Build.VERSION_CODES.LOLLIPOP) [AnnotateVersionCheck]
                public static final boolean SUPPORTS_LETTER_SPACING = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP; // 7: Should annotate
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/JavaVersionChecks.java:32: Warning: This method should be annotated with @ChecksSdkIntAtLeast(api=Build.VERSION_CODES.LOLLIPOP) [AnnotateVersionCheck]
                private boolean isLollipop() { // 8: Should annotate
                                ~~~~~~~~~~
            src/test/pkg/JavaVersionChecks.java:36: Warning: This method should be annotated with @ChecksSdkIntAtLeast(api=Build.VERSION_CODES.ICE_CREAM_SANDWICH) [AnnotateVersionCheck]
                public static boolean isIcs() { // 9: Should annotate
                                      ~~~~~
            src/test/pkg/JavaVersionChecks.java:39: Warning: This method should be annotated with @ChecksSdkIntAtLeast(api=Build.VERSION_CODES.GINGERBREAD) [AnnotateVersionCheck]
                public static boolean isGingerbread() { // 10: Should annotate
                                      ~~~~~~~~~~~~~
            src/test/pkg/JavaVersionChecks.java:43: Warning: This method should be annotated with @ChecksSdkIntAtLeast(api=N, lambda=0) [AnnotateVersionCheck]
                public static void runOnNougat(Runnable runnable) { // 11: Should annotate
                                   ~~~~~~~~~~~
            src/test/pkg/JavaVersionChecks.java:48: Warning: This method should be annotated with @ChecksSdkIntAtLeast(parameter=0, lambda=1) [AnnotateVersionCheck]
                public static void runOnAny(int api, Runnable runnable) { // 12: Should annotate
                                   ~~~~~~~~
            src/test/pkg/NotImported.java:6: Warning: This method should be annotated with @ChecksSdkIntAtLeast(api=N) [AnnotateVersionCheck]
                public static boolean isNougat2() { // Should annotate
                                      ~~~~~~~~~
            src/test/pkg/Utils.kt:12: Warning: This method should be annotated with @ChecksSdkIntAtLeast(api=VERSION_CODES.N) [AnnotateVersionCheck]
            fun isNougat1(): Boolean = VERSION.SDK_INT >= VERSION_CODES.N // 1: Should be annotated
                ~~~~~~~~~
            src/test/pkg/Utils.kt:14: Warning: This method should be annotated with @ChecksSdkIntAtLeast(api=VERSION_CODES.N) [AnnotateVersionCheck]
            fun isNougat2(): Boolean { // 2: Should be annotated
                ~~~~~~~~~
            src/test/pkg/Utils.kt:18: Warning: This method should be annotated with @ChecksSdkIntAtLeast(parameter=0) [AnnotateVersionCheck]
            fun isAtLeast2(api: Int): Boolean { // 3: Should be annotated
                ~~~~~~~~~~
            src/test/pkg/Utils.kt:22: Warning: This method should be annotated with @ChecksSdkIntAtLeast(parameter=0) [AnnotateVersionCheck]
            fun isAtLeast2g(api: Int): Boolean = VERSION.SDK_INT >= api  // 4: Should be annotated
                ~~~~~~~~~~~
            src/test/pkg/Utils.kt:25: Warning: This method should be annotated with @ChecksSdkIntAtLeast(api=Build.VERSION_CODES.ICE_CREAM_SANDWICH) [AnnotateVersionCheck]
                val isIcs: Boolean // 5: Should be annotated
                    ~~~~~
            src/test/pkg/Utils.kt:27: Warning: This method should be annotated with @ChecksSdkIntAtLeast(api=Build.VERSION_CODES.GINGERBREAD) [AnnotateVersionCheck]
                val isGingerbread: Boolean // 6: Should be annotated
                    ~~~~~~~~~~~~~
            src/test/pkg/Utils.kt:31: Warning: This method should be annotated with @ChecksSdkIntAtLeast(api=Build.VERSION_CODES.O, lambda=1) [AnnotateVersionCheck]
            inline fun <T> T.applyForOreoOrAbove(block: T.() -> Unit): T { // 7: Should be annotated
                             ~~~~~~~~~~~~~~~~~~~
            src/test/pkg/Utils.kt:38: Warning: This method should be annotated with @ChecksSdkIntAtLeast(api=Build.VERSION_CODES.O, lambda=1) [AnnotateVersionCheck]
            inline fun <T> T.applyForOreoOrAbove2(block: T.() -> Unit): Unit { // 8: Should be annotated
                             ~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/Utils.kt:46: Warning: This method should be annotated with @ChecksSdkIntAtLeast(parameter=0, lambda=1) [AnnotateVersionCheck]
            inline fun <T> sdk(level: Int, func: () -> T): T? { // 9: Should be annotated
                           ~~~
            src/test/pkg/Utils.kt:54: Warning: This method should be annotated with @ChecksSdkIntAtLeast(parameter=0, lambda=1) [AnnotateVersionCheck]
            inline fun <T> sdk2(level: Int, func: () -> T): T? = // 10: Should be annotated
                           ~~~~
            src/test/pkg/Utils.kt:61: Warning: This method should be annotated with @ChecksSdkIntAtLeast(parameter=0, lambda=1) [AnnotateVersionCheck]
            inline fun fromApi(value: Int, action: () -> Unit) { // 11: Should be annotated
                       ~~~~~~~
            src/test/pkg/Utils.kt:67: Warning: This method should be annotated with @ChecksSdkIntAtLeast(parameter=0, lambda=1) [AnnotateVersionCheck]
            fun fromApiNonInline(value: Int, action: () -> Unit) { // 12: Should be annotated
                ~~~~~~~~~~~~~~~~
            src/test/pkg/Utils.kt:79: Warning: This method should be annotated with @ChecksSdkIntAtLeast(api=android.os.Build.VERSION_CODES.N_MR1) [AnnotateVersionCheck]
            fun isAfterNougat(): Boolean { // 14: Should be annotated
                ~~~~~~~~~~~~~
            0 errors, 26 warnings
            """
            ).expectFixDiffs(
                """
            Fix for src/test/pkg/JavaVersionChecks.java line 13: Annotate with @ChecksSdkIntAtLeast:
            @@ -13 +13
            +     @ChecksSdkIntAtLeast(api=N)
            Fix for src/test/pkg/JavaVersionChecks.java line 16: Annotate with @ChecksSdkIntAtLeast:
            @@ -16 +16
            +     @ChecksSdkIntAtLeast(api=N)
            Fix for src/test/pkg/JavaVersionChecks.java line 19: Annotate with @ChecksSdkIntAtLeast:
            @@ -19 +19
            +     @ChecksSdkIntAtLeast(api=Build.VERSION_CODES.N_MR1)
            Fix for src/test/pkg/JavaVersionChecks.java line 22: Annotate with @ChecksSdkIntAtLeast:
            @@ -22 +22
            +     @ChecksSdkIntAtLeast(parameter=0)
            Fix for src/test/pkg/JavaVersionChecks.java line 25: Annotate with @ChecksSdkIntAtLeast:
            @@ -25 +25
            +     @ChecksSdkIntAtLeast(parameter=0)
            Fix for src/test/pkg/JavaVersionChecks.java line 28: Annotate with @ChecksSdkIntAtLeast:
            @@ -28 +28
            +     @ChecksSdkIntAtLeast(api=36)
            Fix for src/test/pkg/JavaVersionChecks.java line 31: Annotate with @ChecksSdkIntAtLeast:
            @@ -31 +31
            +     @ChecksSdkIntAtLeast(api=Build.VERSION_CODES.LOLLIPOP)
            Fix for src/test/pkg/JavaVersionChecks.java line 32: Annotate with @ChecksSdkIntAtLeast:
            @@ -32 +32
            +     @ChecksSdkIntAtLeast(api=Build.VERSION_CODES.LOLLIPOP)
            Fix for src/test/pkg/JavaVersionChecks.java line 36: Annotate with @ChecksSdkIntAtLeast:
            @@ -36 +36
            +     @ChecksSdkIntAtLeast(api=Build.VERSION_CODES.ICE_CREAM_SANDWICH)
            Fix for src/test/pkg/JavaVersionChecks.java line 39: Annotate with @ChecksSdkIntAtLeast:
            @@ -39 +39
            +     @ChecksSdkIntAtLeast(api=Build.VERSION_CODES.GINGERBREAD)
            Fix for src/test/pkg/JavaVersionChecks.java line 43: Annotate with @ChecksSdkIntAtLeast:
            @@ -43 +43
            +     @ChecksSdkIntAtLeast(api=N, lambda=0)
            Fix for src/test/pkg/JavaVersionChecks.java line 48: Annotate with @ChecksSdkIntAtLeast:
            @@ -48 +48
            +     @ChecksSdkIntAtLeast(parameter=0, lambda=1)
            Fix for src/test/pkg/NotImported.java line 6: Annotate with @ChecksSdkIntAtLeast:
            @@ -6 +6
            +     @androidx.annotation.ChecksSdkIntAtLeast(api=N)
            Fix for src/test/pkg/Utils.kt line 12: Annotate with @ChecksSdkIntAtLeast:
            @@ -12 +12
            + @ChecksSdkIntAtLeast(api=VERSION_CODES.N)
            Fix for src/test/pkg/Utils.kt line 14: Annotate with @ChecksSdkIntAtLeast:
            @@ -14 +14
            + @ChecksSdkIntAtLeast(api=VERSION_CODES.N)
            Fix for src/test/pkg/Utils.kt line 18: Annotate with @ChecksSdkIntAtLeast:
            @@ -18 +18
            + @ChecksSdkIntAtLeast(parameter=0)
            Fix for src/test/pkg/Utils.kt line 22: Annotate with @ChecksSdkIntAtLeast:
            @@ -22 +22
            + @ChecksSdkIntAtLeast(parameter=0)
            Fix for src/test/pkg/Utils.kt line 25: Annotate with @ChecksSdkIntAtLeast:
            @@ -26 +26
            +         @ChecksSdkIntAtLeast(api=Build.VERSION_CODES.ICE_CREAM_SANDWICH)
            Fix for src/test/pkg/Utils.kt line 27: Annotate with @ChecksSdkIntAtLeast:
            @@ -28 +28
            +         @ChecksSdkIntAtLeast(api=Build.VERSION_CODES.GINGERBREAD)
            Fix for src/test/pkg/Utils.kt line 31: Annotate with @ChecksSdkIntAtLeast:
            @@ -31 +31
            + @ChecksSdkIntAtLeast(api=Build.VERSION_CODES.O, lambda=1)
            Fix for src/test/pkg/Utils.kt line 38: Annotate with @ChecksSdkIntAtLeast:
            @@ -38 +38
            + @ChecksSdkIntAtLeast(api=Build.VERSION_CODES.O, lambda=1)
            Fix for src/test/pkg/Utils.kt line 46: Annotate with @ChecksSdkIntAtLeast:
            @@ -46 +46
            + @ChecksSdkIntAtLeast(parameter=0, lambda=1)
            Fix for src/test/pkg/Utils.kt line 54: Annotate with @ChecksSdkIntAtLeast:
            @@ -54 +54
            + @ChecksSdkIntAtLeast(parameter=0, lambda=1)
            Fix for src/test/pkg/Utils.kt line 61: Annotate with @ChecksSdkIntAtLeast:
            @@ -61 +61
            + @ChecksSdkIntAtLeast(parameter=0, lambda=1)
            Fix for src/test/pkg/Utils.kt line 67: Annotate with @ChecksSdkIntAtLeast:
            @@ -67 +67
            + @ChecksSdkIntAtLeast(parameter=0, lambda=1)
            Fix for src/test/pkg/Utils.kt line 79: Annotate with @ChecksSdkIntAtLeast:
            @@ -79 +79
            + @ChecksSdkIntAtLeast(api=VERSION_CODES.N_MR1)
            """
            )
    }

    fun test189154435() {
        // Regression test for
        // 189154435: AnnotateVersionCheck considers "Context" a lambda
        lint().files(
            projectProperties().library(true),
            java(
                """
                package test.pkg;

                import android.content.Context;
                import android.content.Intent;
                import android.os.Build;

                public class AnnotateTest {
                    private static final String ACTION_ENABLE_WEAR_BATTERY_SAVER = "ACTION_ENABLE_WEAR_BATTERY_SAVER";
                    private static final String ACTION_ENTER_TWM = "ACTION_ENTER_TWM";

                    public void showToggleBatterySaverConfirmation(Context context) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            context.startActivity(
                                    new Intent(ACTION_ENABLE_WEAR_BATTERY_SAVER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        }
                    }
                }
                """
            ).indented()
        ).run().expectClean()
    }

    fun testFunctions() {
        lint().files(
            projectProperties().library(true),
            kotlin(
                """
                @file:Suppress("unused")

                package test.pkg

                import android.os.Build
                import java.util.function.Function

                open class MyJavaFunction : Function<String, Int> {
                    override fun apply(p0: String): Int {
                        return p0.length
                    }
                }

                open class MyKotlinFunction : Function1<String, Int> {
                    override fun invoke(p1: String): Int {
                        return p1.length
                    }
                }

                fun test1(function: MyKotlinFunction, arg: String) {
                    if (Build.VERSION.SDK_INT > 26) {
                        function.invoke(arg)
                    }
                }

                // TODO: What about androidx.arch.core.util ?
                """
            ).indented(),
            java(
                """
                package test.pkg;

                import android.os.Build;
                import java.util.function.Function;

                public class FunctionTest {
                    void test(Function<String, Integer> function, String arg) {
                        if (Build.VERSION.SDK_INT > 26) {
                            function.apply(arg);
                        }
                    }
                    void test2(MyJavaFunction function, String arg) {
                        if (Build.VERSION.SDK_INT > 26) {
                            function.apply(arg);
                        }
                    }
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            """
            src/test/pkg/FunctionTest.java:7: Warning: This method should be annotated with @ChecksSdkIntAtLeast(api=android.os.Build.VERSION_CODES.O_MR1, lambda=0) [AnnotateVersionCheck]
                void test(Function<String, Integer> function, String arg) {
                     ~~~~
            src/test/pkg/FunctionTest.java:12: Warning: This method should be annotated with @ChecksSdkIntAtLeast(api=android.os.Build.VERSION_CODES.O_MR1, lambda=0) [AnnotateVersionCheck]
                void test2(MyJavaFunction function, String arg) {
                     ~~~~~
            src/test/pkg/MyJavaFunction.kt:20: Warning: This method should be annotated with @ChecksSdkIntAtLeast(api=android.os.Build.VERSION_CODES.O_MR1, lambda=0) [AnnotateVersionCheck]
            fun test1(function: MyKotlinFunction, arg: String) {
                ~~~~~
            0 errors, 3 warnings
            """
        )
    }

    override fun getDetector(): Detector {
        return SdkIntDetector()
    }
}
