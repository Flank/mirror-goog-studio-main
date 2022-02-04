/*
 * Copyright (C) 2012 The Android Open Source Project
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
import com.android.tools.lint.detector.api.Issue

class AnnotationDetectorTest : AbstractCheckTest() {
    fun testBasic() {
        lint().files(
            java(
                """

                package test.pkg;

                import android.annotation.SuppressLint;
                import android.view.View;

                public class WrongAnnotation {

                    @SuppressLint("NewApi") // Valid: class-file check on method
                    public static void foobar(View view, @SuppressLint("NewApi") int foo) { // $ Invalid: class-file check
                        @SuppressLint("NewApi") // Invalid
                        boolean a;
                        @SuppressLint({"SdCardPath", "NewApi"}) // Invalid: class-file based check on local variable
                        boolean b;
                        @android.annotation.SuppressLint({"SdCardPath", "NewApi"}) // Invalid (FQN)
                        boolean c;
                        @SuppressLint("SdCardPath") // Valid: AST-based check
                        boolean d;
                    }

                    @SuppressLint("NewApi")
                    private int field1;

                    @SuppressLint("NewApi")
                    private int field2 = 5;

                    static {
                        // Local variable outside method: invalid
                        @SuppressLint("NewApi")
                        int localvar = 5;
                    }

                    private static void test() {
                        @SuppressLint("NewApi") // Invalid
                        int a = View.MEASURED_STATE_MASK;
                    }
                }
                """
            ).indented()
        ).run().expect(
            """
                src/test/pkg/WrongAnnotation.java:10: Error: The @SuppressLint annotation cannot be used on a local variable with the lint check 'NewApi': move out to the surrounding method [LocalSuppress]
                    public static void foobar(View view, @SuppressLint("NewApi") int foo) { // $ Invalid: class-file check
                                                         ~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/WrongAnnotation.java:11: Error: The @SuppressLint annotation cannot be used on a local variable with the lint check 'NewApi': move out to the surrounding method [LocalSuppress]
                        @SuppressLint("NewApi") // Invalid
                        ~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/WrongAnnotation.java:13: Error: The @SuppressLint annotation cannot be used on a local variable with the lint check 'NewApi': move out to the surrounding method [LocalSuppress]
                        @SuppressLint({"SdCardPath", "NewApi"}) // Invalid: class-file based check on local variable
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/WrongAnnotation.java:15: Error: The @SuppressLint annotation cannot be used on a local variable with the lint check 'NewApi': move out to the surrounding method [LocalSuppress]
                        @android.annotation.SuppressLint({"SdCardPath", "NewApi"}) // Invalid (FQN)
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/WrongAnnotation.java:29: Error: The @SuppressLint annotation cannot be used on a local variable with the lint check 'NewApi': move out to the surrounding method [LocalSuppress]
                        @SuppressLint("NewApi")
                        ~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/WrongAnnotation.java:34: Error: The @SuppressLint annotation cannot be used on a local variable with the lint check 'NewApi': move out to the surrounding method [LocalSuppress]
                        @SuppressLint("NewApi") // Invalid
                        ~~~~~~~~~~~~~~~~~~~~~~~
                6 errors, 0 warnings
                """
        )
    }

    fun testUniqueValues() {
        lint().files(
            java(
                """
                package test.pkg;
                import androidx.annotation.IntDef;
                import android.annotation.SuppressLint;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;

                @SuppressLint("UnusedDeclaration")
                public class IntDefTest {
                    @IntDef({STYLE_NORMAL, STYLE_NO_TITLE, STYLE_NO_FRAME, STYLE_NO_INPUT})
                    @Retention(RetentionPolicy.SOURCE)
                    private @interface DialogStyle {}

                    public static final int STYLE_NORMAL = 0;
                    public static final int STYLE_NO_TITLE = 1;
                    public static final int STYLE_NO_FRAME = 2;
                    public static final int STYLE_NO_INPUT = 2;

                    @IntDef({STYLE_NORMAL, STYLE_NO_TITLE, STYLE_NO_FRAME, STYLE_NO_INPUT})
                    @SuppressWarnings("UniqueConstants")
                    @Retention(RetentionPolicy.SOURCE)
                    private @interface SuppressedDialogStyle {}


                    public static final long FLAG1 = 0x100000000000L;
                    public static final long FLAG2 = 0x0002000000000000L;
                    public static final long FLAG3 = 0x2000000000000L;

                    @IntDef({FLAG2, FLAG3, FLAG1})
                    @Retention(RetentionPolicy.SOURCE)
                    private @interface Flags {}

                    @IntDef({FLAG1, FLAG2, FLAG1})
                    @Retention(RetentionPolicy.SOURCE)
                    private @interface Flags1 {}

                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            """
                src/test/pkg/IntDefTest.java:9: Error: Constants STYLE_NO_INPUT and STYLE_NO_FRAME specify the same exact value (2); this is usually a cut & paste or merge error [UniqueConstants]
                    @IntDef({STYLE_NORMAL, STYLE_NO_TITLE, STYLE_NO_FRAME, STYLE_NO_INPUT})
                                                                           ~~~~~~~~~~~~~~
                    src/test/pkg/IntDefTest.java:9: Previous same value
                    @IntDef({STYLE_NORMAL, STYLE_NO_TITLE, STYLE_NO_FRAME, STYLE_NO_INPUT})
                                                           ~~~~~~~~~~~~~~
                src/test/pkg/IntDefTest.java:28: Error: Constants FLAG3 and FLAG2 specify the same exact value (0x2000000000000L); this is usually a cut & paste or merge error [UniqueConstants]
                    @IntDef({FLAG2, FLAG3, FLAG1})
                                    ~~~~~
                    src/test/pkg/IntDefTest.java:28: Previous same value
                    @IntDef({FLAG2, FLAG3, FLAG1})
                             ~~~~~
                src/test/pkg/IntDefTest.java:32: Error: Constant FLAG1 has already been included [UniqueConstants]
                    @IntDef({FLAG1, FLAG2, FLAG1})
                                           ~~~~~
                    src/test/pkg/IntDefTest.java:32: Previous occurrence
                    @IntDef({FLAG1, FLAG2, FLAG1})
                             ~~~~~
                3 errors, 0 warnings
                """
        )
    }

    fun testAnnotationTarget() {
        // 207151948: Lint check for accidentally importing java.lang.annotation.Target
        lint().files(
            kotlin(
                """
                package test.pkg

                import java.lang.annotation.ElementType

                // Correct: can be used on any element from Kotlin and Java
                annotation class Annotation1() // OK 1

                // Correct: can be used on parameters only both in Kotlin and Java
                @Target(AnnotationTarget.VALUE_PARAMETER) // OK 2
                annotation class Annotation2()

                // Incorrect: can be used on any element (not just parameters) in
                // Kotlin, and cannot be used on any elements (including parameters) in Java
                @java.lang.annotation.Target(ElementType.PARAMETER) // ERROR 1
                annotation class Annotation3()

                // Incorrect: works fine from Kotlin (can be used only on parameters),
                // but cannot be used on any elements from Java
                @java.lang.annotation.Target(ElementType.PARAMETER) // ERROR 2
                @Target(AnnotationTarget.VALUE_PARAMETER)
                annotation class Annotation4()
                """
            ).indented(),
            java(
                """
                package test.pkg;
                import java.lang.annotation.ElementType;
                @Target({ElementType.PARAMETER}) // OK 3
                public @interface Annotation5 { }
                """
            ).indented(),
            java(
                """
                package test.pkg;
                import java.lang.annotation.ElementType;
                @java.lang.annotation.Target({ElementType.PARAMETER}) // OK 4
                public @interface Annotation6 { }
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/Annotation1.kt:14: Error: Use @kotlin.annotation.Target, not @java.lang.annotation.Target here; these targets will be ignored from Kotlin and the annotation will not be allowed on any element types from Java [SupportAnnotationUsage]
            @java.lang.annotation.Target(ElementType.PARAMETER) // ERROR 1
                                  ~~~~~~
            src/test/pkg/Annotation1.kt:19: Error: Do not use @java.lang.annotation.Target here; it will cause the annotation to not be allowed on any element types from Java [SupportAnnotationUsage]
            @java.lang.annotation.Target(ElementType.PARAMETER) // ERROR 2
                                  ~~~~~~
            2 errors, 0 warnings
            """
        ).expectFixDiffs(
            """
            Fix for src/test/pkg/Annotation1.kt line 14: Replace with Target:
            @@ -14 +14
            - @java.lang.annotation.Target(ElementType.PARAMETER) // ERROR 1
            + @Target(ElementType.PARAMETER) // ERROR 1
            Fix for src/test/pkg/Annotation1.kt line 19: Delete:
            @@ -19 +19
            - @java.lang.annotation.Target(ElementType.PARAMETER) // ERROR 2
            +  // ERROR 2
            """
        )
    }

    fun testFlagStyle() {
        lint().files(
            java(
                "src/test/pkg/IntDefTest.java",
                """
                package test.pkg;
                import androidx.annotation.IntDef;

                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;

                @SuppressWarnings("unused")
                public class IntDefTest {
                    public static final long FLAG1 = 1;
                    public static final long FLAG2 = 2;
                    public static final long FLAG3 = 1 << 2;
                    public static final long FLAG4 = 1 << 3;
                    public static final long FLAG5 = 0x100000000000L;
                    public static final long FLAG6 = 0x0002000000000000L;
                    public static final long FLAG7 = 8L;
                    public static final long FLAG8 = 9L;
                    public static final long FLAG9 = 0;
                    public static final long FLAG10 = 1;
                    public static final long FLAG11 = -1;
                    public static final int  FLAG12 = 0x10;
                    public static final int  FLAG13 = 1 << 1;
                    public static final int  FLAG14 = 1 << 2;

                    @IntDef({FLAG1, FLAG2, FLAG3})
                    @Retention(RetentionPolicy.SOURCE)
                    private @interface Flags1 {}

                    @IntDef(flag = true, value={FLAG1,FLAG9,FLAG3}) private @interface Flags1 {}
                    @IntDef(flag = true, value={FLAG1,FLAG9,FLAG4}) private @interface Flags4 {}
                    @IntDef(flag = true, value={FLAG1,FLAG9,FLAG5}) private @interface Flags5 {}
                    @IntDef(flag = true, value={FLAG1,FLAG9,FLAG6}) private @interface Flags6 {}
                    @IntDef(flag = true, value={FLAG1,FLAG9,FLAG7}) private @interface Flags7 {}
                    @IntDef(flag = true, value={FLAG1,FLAG9,FLAG8}) private @interface Flags8 {}
                    @IntDef(flag = true, value={FLAG11,FLAG9,FLAG10}) private @interface Flags10 {}
                    @IntDef(flag = true, value={FLAG1,FLAG9,FLAG11}) private @interface Flags11 {}
                    @IntDef(flag = true, value={FLAG1,FLAG9,FLAG12}) private @interface Flags12 {}
                    @IntDef(flag = true, value={FLAG1,FLAG9,FLAG13}) private @interface Flags13 {}
                    @IntDef(flag = true, value={FLAG1,FLAG9,FLAG14}) private @interface Flags14 {}
                }"""
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            """
                src/test/pkg/IntDefTest.java:13: Warning: Consider declaring this constant using 1 << 44 instead [ShiftFlags]
                    public static final long FLAG5 = 0x100000000000L;
                                                     ~~~~~~~~~~~~~~~
                src/test/pkg/IntDefTest.java:14: Warning: Consider declaring this constant using 1 << 49 instead [ShiftFlags]
                    public static final long FLAG6 = 0x0002000000000000L;
                                                     ~~~~~~~~~~~~~~~~~~~
                src/test/pkg/IntDefTest.java:15: Warning: Consider declaring this constant using 1 << 3 instead [ShiftFlags]
                    public static final long FLAG7 = 8L;
                                                     ~~
                src/test/pkg/IntDefTest.java:20: Warning: Consider declaring this constant using 1 << 4 instead [ShiftFlags]
                    public static final int  FLAG12 = 0x10;
                                                      ~~~~
                0 errors, 4 warnings
                """
        )
            .expectFixDiffs(
                """
                Autofix for src/test/pkg/IntDefTest.java line 13: Replace with 1L << 44:
                @@ -13 +13
                -     public static final long FLAG5 = 0x100000000000L;
                +     public static final long FLAG5 = 1L << 44;
                Autofix for src/test/pkg/IntDefTest.java line 14: Replace with 1L << 49:
                @@ -14 +14
                -     public static final long FLAG6 = 0x0002000000000000L;
                +     public static final long FLAG6 = 1L << 49;
                Autofix for src/test/pkg/IntDefTest.java line 15: Replace with 1L << 3:
                @@ -15 +15
                -     public static final long FLAG7 = 8L;
                +     public static final long FLAG7 = 1L << 3;
                Autofix for src/test/pkg/IntDefTest.java line 20: Replace with 1 << 4:
                @@ -20 +20
                -     public static final int  FLAG12 = 0x10;
                +     public static final int  FLAG12 = 1 << 4;
                """
            )
    }

    fun testFlagStyleShl() {
        lint().files(
            kotlin(
                """
                package test.pkg
                import androidx.annotation.IntDef

                import java.lang.annotation.Retention
                import java.lang.annotation.RetentionPolicy

                @IntDef(DIVIDER_NONE, DIVIDER_TOP, DIVIDER_BOTTOM, flag = true)
                @Retention(AnnotationRetention.SOURCE)
                annotation class DividerFlags
                const val DIVIDER_NONE: Int = 0
                const val DIVIDER_TOP: Int = 1
                const val DIVIDER_BOTTOM: Int = 2
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            """
                src/test/pkg/DividerFlags.kt:12: Warning: Consider declaring this constant using 1 shl 1 instead [ShiftFlags]
                const val DIVIDER_BOTTOM: Int = 2
                                                ~
                0 errors, 1 warnings
                """
        )
            .expectFixDiffs(
                """
                Autofix for src/test/pkg/DividerFlags.kt line 12: Replace with 1 shl 1:
                @@ -12 +12
                - const val DIVIDER_BOTTOM: Int = 2
                @@ -13 +12
                + const val DIVIDER_BOTTOM: Int = 1 shl 1
                """
            )
    }

    fun testMissingIntDefSwitchConstants() {
        lint().files(
            java(
                "src/test/pkg/X.java",
                """
                package test.pkg;

                import android.annotation.SuppressLint;
                import androidx.annotation.IntDef;

                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;

                @SuppressWarnings({"UnusedParameters", "unused", "SpellCheckingInspection", "RedundantCast"})
                public class X {
                    @IntDef({LENGTH_INDEFINITE, LENGTH_SHORT, LENGTH_LONG})
                    @Retention(RetentionPolicy.SOURCE)
                    public @interface Duration {
                    }

                    public static final int LENGTH_INDEFINITE = -2;
                    public static final int LENGTH_SHORT = -1;
                    public static final int LENGTH_LONG = 0;

                    public void setDuration(@Duration int duration) {
                    }

                    @Duration
                    public static int getDuration() {
                        return LENGTH_INDEFINITE;
                    }

                    public static void testOk(@Duration int duration) {
                        switch (duration) {
                            case LENGTH_SHORT:
                            case LENGTH_LONG:
                            case LENGTH_INDEFINITE:
                                break;
                        }
                    }

                    public static void testLiteral(@Duration int duration) {
                        switch (duration) {
                            case LENGTH_SHORT:
                            case 5:
                            case LENGTH_INDEFINITE:
                                break;
                        }
                    }

                    public static void testParameter(@Duration int duration) {
                        switch (duration) {
                            case LENGTH_SHORT:
                            case LENGTH_INDEFINITE:
                                break;
                        }
                    }

                    public static void testMissingAll(@Duration int duration) {
                        // We don't flag these; let the IDE's normal "empty switch" check flag it
                        switch (duration) {
                        }
                    }

                    @SuppressWarnings("UnnecessaryLocalVariable")
                    public static void testLocalVariableFlow() {
                        int intermediate = getDuration();
                        int duration = intermediate;

                        // Missing LENGTH_SHORT
                        switch (duration) {
                            case LENGTH_LONG:
                            case LENGTH_INDEFINITE:
                                break;
                        }
                    }

                    public static void testMethodCall() {
                        // Missing LENGTH_SHORT
                        switch ((int)getDuration()) {
                            case LENGTH_LONG:
                            case LENGTH_INDEFINITE:
                                break;
                        }
                    }

                    @SuppressWarnings("ConstantConditionalExpression")
                    public static void testInline() {
                        // Missing LENGTH_SHORT
                        switch (true ? getDuration() : 0) {
                            case LENGTH_LONG:
                            case LENGTH_INDEFINITE:
                                break;
                        }
                    }

                    private static class SomeOtherClass {
                        private void method() {
                            // Missing LENGTH_SHORT
                            switch (X.getDuration()) {
                                case LENGTH_LONG:
                                case LENGTH_INDEFINITE:
                                    break;
                            }
                        }
                    }

                    public static void testMissingWithDefault(@Duration int duration) {
                        switch (duration) {
                            case LENGTH_SHORT:
                            case LENGTH_LONG:
                            default:
                                break;
                        }
                    }

                    @SuppressLint("SwitchIntDef")
                    public static void testSuppressAnnotation(@Duration int duration) {
                        switch (duration) {
                            case LENGTH_SHORT:
                            case LENGTH_INDEFINITE:
                                break;
                        }
                    }

                    public static void testSuppressComment(@Duration int duration) {
                        //noinspection AndroidLintSwitchIntDef
                        switch (duration) {
                            case LENGTH_SHORT:
                            case LENGTH_INDEFINITE:
                                break;
                        }
                    }
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            """
            src/test/pkg/X.java:40: Warning: Don't use a constant here; expected one of: LENGTH_INDEFINITE, LENGTH_LONG, LENGTH_SHORT [SwitchIntDef]
                        case 5:
                             ~
            src/test/pkg/X.java:47: Warning: Switch statement on an int with known associated constant missing case LENGTH_LONG [SwitchIntDef]
                    switch (duration) {
                    ~~~~~~
            src/test/pkg/X.java:56: Warning: Switch statement on an int with known associated constant missing case LENGTH_INDEFINITE, LENGTH_LONG, LENGTH_SHORT [SwitchIntDef]
                    switch (duration) {
                    ~~~~~~
            src/test/pkg/X.java:66: Warning: Switch statement on an int with known associated constant missing case LENGTH_SHORT [SwitchIntDef]
                    switch (duration) {
                    ~~~~~~
            src/test/pkg/X.java:75: Warning: Switch statement on an int with known associated constant missing case LENGTH_SHORT [SwitchIntDef]
                    switch ((int)getDuration()) {
                    ~~~~~~
            src/test/pkg/X.java:85: Warning: Switch statement on an int with known associated constant missing case LENGTH_SHORT [SwitchIntDef]
                    switch (true ? getDuration() : 0) {
                    ~~~~~~
            src/test/pkg/X.java:95: Warning: Switch statement on an int with known associated constant missing case X.LENGTH_SHORT [SwitchIntDef]
                        switch (X.getDuration()) {
                        ~~~~~~
            src/test/pkg/X.java:104: Warning: Switch statement on an int with known associated constant missing case LENGTH_INDEFINITE [SwitchIntDef]
                    switch (duration) {
                    ~~~~~~
            0 errors, 8 warnings
            """
        )
    }

    fun testMissingSwitchFailingIntDef() {
        lint().files(
            java(
                """

                package test.pkg;

                import android.view.View;
                public class X {

                    public void measure(int mode) {
                        int val = View.MeasureSpec.getMode(mode);
                        switch (val) {
                            case View.MeasureSpec.AT_MOST:
                                break;
                        }
                    }
                }
                """
            ).indented()
        ).run().expect(
            """
                src/test/pkg/X.java:9: Warning: Switch statement on an int with known associated constant missing case MeasureSpec.EXACTLY, MeasureSpec.UNSPECIFIED [SwitchIntDef]
                        switch (val) {
                        ~~~~~~
                0 errors, 1 warnings
                """
        )
    }

    fun testMissingSwitchFailingIntDefKotlin() {
        lint().files(
            kotlin(
                """

                package test.pkg;

                import android.view.View

                class X {
                    fun measure(mode: Int) {
                        val `val` = View.MeasureSpec.getMode(mode)
                        when (`val`) {
                            View.MeasureSpec.AT_MOST -> {
                                // something
                            }
                        }
                    }
                }
                """
            ).indented()
        ).run().expect(
            """
                src/test/pkg/X.kt:9: Warning: Switch statement on an int with known associated constant missing case MeasureSpec.EXACTLY, MeasureSpec.UNSPECIFIED [SwitchIntDef]
                        when (`val`) {
                        ~~~~
                0 errors, 1 warnings
                """
        )
    }

    fun testUnexpectedSwitchConstant() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=204326
        // 	The switch check should look for unexpected constants in case statements
        lint().files(
            java(
                "src/test/pkg/X.java",
                """
                package test.pkg;

                import android.view.View;
                public class X {
                    private static final int MY_CONSTANT = 5;
                    private static final int MY_CONSTANT_2 = View.MeasureSpec.AT_MOST;
                    public void measure(int mode) {
                        int val = View.MeasureSpec.getMode(mode);
                        switch (val) {
                            case MY_CONSTANT: // ERROR
                            case MY_CONSTANT_2: // OK (alias)
                            case View.MeasureSpec.UNSPECIFIED: // OK
                                break;
                        }
                    }
                }
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/X.java:9: Warning: Switch statement on an int with known associated constant missing case MeasureSpec.EXACTLY [SwitchIntDef]
                    switch (val) {
                    ~~~~~~
            src/test/pkg/X.java:10: Warning: Unexpected constant; expected one of: MeasureSpec.AT_MOST, MeasureSpec.EXACTLY, MeasureSpec.UNSPECIFIED [SwitchIntDef]
                        case MY_CONSTANT: // ERROR
                             ~~~~~~~~~~~
            0 errors, 2 warnings
            """
        )
    }

    fun testUnexpectedSwitchConstantInOpenTypedef() {
        // Regression test for 216746694
        // Don't flag unexpected constants in open typedefs
        // 	The switch check should look for unexpected constants in case statements
        lint().files(
            java(
                """
                package test.pkg;

                public class Test {
                    private static final int MY_CONSTANT = 5;
                    public void measure(@PlaybackStateCompat.State int mode) {
                        switch (mode) {
                            case MY_CONSTANT: // OK
                            case 42: // OK
                                break;
                        }
                    }
                }
                """
            ).indented(),
            java(
                """
                package test.pkg;

                import androidx.annotation.IntDef;

                public class PlaybackStateCompat {
                    @IntDef(open = true, value = {STATE_NONE, STATE_STOPPED})
                    public @interface State { }
                    public static final int STATE_NONE = 0;
                    public static final int STATE_STOPPED = 1;
                }"""
            ),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun testMissingSwitchConstantsWithElse() {
        // Regression test for
        // 117854168: Wrong lint warning used for PlaybackStateCompat.STATE_* constants
        lint().files(
            kotlin(
                """
                @file:Suppress("unused")

                package test.pkg

                class PlayConstantsTest {
                    fun test(@PlaybackStateCompat.State playState: Int) {
                        when (playState) {
                            PlaybackStateCompat.STATE_PAUSED -> {
                                println("paused")
                            }
                            PlaybackStateCompat.STATE_STOPPED -> {
                                println("paused")
                            }
                            else -> {
                                println("Something else")
                            }
                        }
                    }
                }
                """
            ),
            java(
                """
                package test.pkg;

                import androidx.annotation.IntDef;

                public class PlaybackStateCompat {
                    @IntDef({STATE_NONE, STATE_STOPPED, STATE_PAUSED, STATE_PLAYING, STATE_FAST_FORWARDING})
                    public @interface State {
                    }

                    public static final int STATE_NONE = 0;
                    public static final int STATE_STOPPED = 1;
                    public static final int STATE_PAUSED = 2;
                    public static final int STATE_PLAYING = 3;
                    public static final int STATE_FAST_FORWARDING = 4;
                }"""
            ),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun testMatchEcjAndExternalFieldNames() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.net.wifi.WifiManager;

                public class MissingEnum {
                    private WifiManager mWifiManager;

                    private void updateAccessPoints() {
                        final int wifiState = mWifiManager.getWifiState();
                        switch (wifiState) {
                            case WifiManager.WIFI_STATE_ENABLING:
                                break;
                            case WifiManager.WIFI_STATE_ENABLED:
                                break;
                            case WifiManager.WIFI_STATE_DISABLING:
                                break;
                            case WifiManager.WIFI_STATE_DISABLED:
                                break;
                            case WifiManager.WIFI_STATE_UNKNOWN:
                                break;
                        }
                    }
                }
                """
            ).indented()
        ).run().expectClean()
    }

    fun testWrongUsages() {
        lint().files(
            java(
                """

                package test.pkg;
                import androidx.annotation.IntDef;
                import androidx.annotation.IntRange;
                import androidx.annotation.FloatRange;
                import androidx.annotation.CheckResult;
                import androidx.annotation.ColorInt;
                import androidx.annotation.DrawableRes;
                import androidx.annotation.Size;
                import androidx.annotation.RequiresPermission;
                import android.annotation.SuppressLint;
                import java.lang.annotation.*;
                import java.util.List;
                @SuppressLint("UnusedDeclaration")
                public class WrongUsages {
                    @IntDef({STYLE_NORMAL, STYLE_NO_TITLE, STYLE_NO_FRAME, STYLE_NO_INPUT})
                    @Retention(RetentionPolicy.SOURCE)
                    private @interface DialogStyle {}
                    public static final int STYLE_NORMAL = 0;
                    public static final int STYLE_NO_TITLE = 1;
                    public static final int STYLE_NO_FRAME = 2;
                    public static final int STYLE_NO_INPUT = 3;

                    @DialogStyle
                    public int okay1() {
                        return 0;
                    }

                    @DialogStyle
                    public long okay2() {
                        return 0;
                    }

                    @DialogStyle
                    public String wrong() {
                        return null;
                    }

                    @IntRange(from = 1, to = 0)
                    @Size(min=10, max = 8)
                    public String wrongIntRange() {
                        return null;
                    }

                    @FloatRange(from = 1.0, to = 0.0)
                    @ColorInt
                    @Size(multiple=0)
                    @DrawableRes
                    public String wrongFloatRange() {
                        return null;
                    }

                    @Size(-5)
                    public int[] wrongSize() {
                        return null;
                    }

                    @RequiresPermission
                    public void wrongPermission(
                        @RequiresPermission int allowed) { // OK
                    }

                    @RequiresPermission(allOf = {"my.permission.PERM1","my.permission.PERM2"},anyOf = {"my.permission.PERM1","my.permission.PERM2"})
                    @CheckResult // Error on void methods
                    public void wrongPermission2() {
                    }

                    public void autoBoxing(@DrawableRes Integer param1) { }
                    public void array(@DrawableRes int[] param1) { }
                    public void varargs(@DrawableRes int... param1) { }
                    public void varargs(@DrawableRes List<Integer> param1) { }


                    @Size(min=1)
                    public int[] okSize() {
                        return null;
                    }

                    @DialogStyle public Pair<Integer, Integer> getFlags1() { return null; } // OK
                    @DialogStyle public List<Integer> getFlags2() { return null; } // OK
                    @DialogStyle public java.util.Map<Integer, String> getFlags2() { return null; } // OK
                    @DialogStyle public List<String> getFlags2() { return null; } // ERROR

                    private class Pair<S,T> { }


                    @androidx.annotation.LongDef({1L,2L,3L})
                    @Retention(RetentionPolicy.SOURCE)
                    private @interface LongDialogStyle {}

                    @LongDialogStyle // OK
                    public int okWithLong() {
                        return 0;
                    }
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            """
            src/test/pkg/WrongUsages.java:34: Error: This annotation does not apply for type String; expected int. Should @DialogStyle be annotated with @StringDef instead? [SupportAnnotationUsage]
                @DialogStyle
                ~~~~~~~~~~~~
            src/test/pkg/WrongUsages.java:39: Error: Invalid range: the from attribute must be less than the to attribute [SupportAnnotationUsage]
                @IntRange(from = 1, to = 0)
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/WrongUsages.java:39: Error: This annotation does not apply for type String; expected int or long [SupportAnnotationUsage]
                @IntRange(from = 1, to = 0)
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/WrongUsages.java:40: Error: Invalid size range: the min attribute must be less than the max attribute [SupportAnnotationUsage]
                @Size(min=10, max = 8)
                ~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/WrongUsages.java:45: Error: Invalid range: the from attribute must be less than the to attribute [SupportAnnotationUsage]
                @FloatRange(from = 1.0, to = 0.0)
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/WrongUsages.java:45: Error: This annotation does not apply for type String; expected float or double [SupportAnnotationUsage]
                @FloatRange(from = 1.0, to = 0.0)
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/WrongUsages.java:46: Error: This annotation does not apply for type String; expected int or long [SupportAnnotationUsage]
                @ColorInt
                ~~~~~~~~~
            src/test/pkg/WrongUsages.java:47: Error: The size multiple must be at least 1 [SupportAnnotationUsage]
                @Size(multiple=0)
                ~~~~~~~~~~~~~~~~~
            src/test/pkg/WrongUsages.java:48: Error: This annotation does not apply for type String; expected int or long [SupportAnnotationUsage]
                @DrawableRes
                ~~~~~~~~~~~~
            src/test/pkg/WrongUsages.java:53: Error: The size can't be negative [SupportAnnotationUsage]
                @Size(-5)
                ~~~~~~~~~
            src/test/pkg/WrongUsages.java:58: Error: For methods, permission annotation should specify one of value, anyOf or allOf [SupportAnnotationUsage]
                @RequiresPermission
                ~~~~~~~~~~~~~~~~~~~
            src/test/pkg/WrongUsages.java:63: Error: Only specify one of value, anyOf or allOf [SupportAnnotationUsage]
                @RequiresPermission(allOf = {"my.permission.PERM1","my.permission.PERM2"},anyOf = {"my.permission.PERM1","my.permission.PERM2"})
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/WrongUsages.java:64: Error: @CheckResult should not be specified on void methods [SupportAnnotationUsage]
                @CheckResult // Error on void methods
                ~~~~~~~~~~~~
            src/test/pkg/WrongUsages.java:82: Error: This annotation does not apply for type java.util.List<java.lang.String>; expected int [SupportAnnotationUsage]
                @DialogStyle public List<String> getFlags2() { return null; } // ERROR
                ~~~~~~~~~~~~
            14 errors, 0 warnings
                """
        )
    }

    fun testValidateRequiresApi() {
        lint().files(
            manifest().minSdk(15),
            java(
                """
                package test.pkg;
                import android.os.Build;
                import androidx.annotation.RequiresApi;

                public class WrongUsages {
                    @RequiresApi // ERROR 1: Misses API level
                    public void testApi1() { }

                    @RequiresApi(14) // ERROR 2: Already known to be at least 15 from minSdkVersion
                    public void testApi2() { }

                    @RequiresApi(15) // ERROR 3: Already known to be at least 15 from minSdkVersion
                    public void testApi3() { }

                    @RequiresApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH) // ERROR 3: Already known to be at least 15 from minSdkVersion
                    public void testApi4() { }

                    @RequiresApi(20) // OK 1
                    public class Test {
                        @RequiresApi(15) // ERROR 4: Already known to be at least 20 from outer annotation
                        public void testApi5() { }
                    }
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).issues(AnnotationDetector.ANNOTATION_USAGE, ApiDetector.OBSOLETE_SDK).skipTestModes(TestMode.PARTIAL).run().expect(
            """
            src/test/pkg/WrongUsages.java:6: Error: Must specify an API level [SupportAnnotationUsage]
                @RequiresApi // ERROR 1: Misses API level
                ~~~~~~~~~~~~
            src/test/pkg/WrongUsages.java:9: Warning: Unnecessary; SDK_INT is always >= 14 [ObsoleteSdkInt]
                @RequiresApi(14) // ERROR 2: Already known to be at least 15 from minSdkVersion
                ~~~~~~~~~~~~~~~~
            src/test/pkg/WrongUsages.java:12: Warning: Unnecessary; SDK_INT is always >= 15 [ObsoleteSdkInt]
                @RequiresApi(15) // ERROR 3: Already known to be at least 15 from minSdkVersion
                ~~~~~~~~~~~~~~~~
            src/test/pkg/WrongUsages.java:15: Warning: Unnecessary; SDK_INT is always >= 14 [ObsoleteSdkInt]
                @RequiresApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH) // ERROR 3: Already known to be at least 15 from minSdkVersion
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/WrongUsages.java:20: Warning: Unnecessary; SDK_INT is always >= 20 from outer annotation (@RequiresApi(20)) [ObsoleteSdkInt]
                    @RequiresApi(15) // ERROR 4: Already known to be at least 20 from outer annotation
                    ~~~~~~~~~~~~~~~~
            1 errors, 4 warnings
            """
        ).expectFixDiffs(
            """
            Fix for src/test/pkg/WrongUsages.java line 6: Specify API level:
            @@ -6 +6
            -     @RequiresApi // ERROR 1: Misses API level
            +     @RequiresApi([TODO]|) // ERROR 1: Misses API level
            Fix for src/test/pkg/WrongUsages.java line 9: Delete @RequiresApi:
            @@ -9 +9
            -     @RequiresApi(14) // ERROR 2: Already known to be at least 15 from minSdkVersion
            +      // ERROR 2: Already known to be at least 15 from minSdkVersion
            Fix for src/test/pkg/WrongUsages.java line 12: Delete @RequiresApi:
            @@ -12 +12
            -     @RequiresApi(15) // ERROR 3: Already known to be at least 15 from minSdkVersion
            +      // ERROR 3: Already known to be at least 15 from minSdkVersion
            Fix for src/test/pkg/WrongUsages.java line 15: Delete @RequiresApi:
            @@ -15 +15
            -     @RequiresApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH) // ERROR 3: Already known to be at least 15 from minSdkVersion
            +      // ERROR 3: Already known to be at least 15 from minSdkVersion
            Fix for src/test/pkg/WrongUsages.java line 20: Delete @RequiresApi:
            @@ -20 +20
            -         @RequiresApi(15) // ERROR 4: Already known to be at least 20 from outer annotation
            +          // ERROR 4: Already known to be at least 20 from outer annotation
            """
        )
    }

    fun testAdditionalFlagScenarios() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.util.SparseIntArray;
                import androidx.annotation.IntDef;
                import androidx.annotation.LongDef;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.util.function.Consumer;

                @SuppressWarnings("DeprecatedIsStillUsed")
                public class TypedefWarnings {
                    @IntDef({STYLE_NORMAL, STYLE_NO_TITLE, STYLE_NO_FRAME, STYLE_NO_INPUT})
                    @Retention(RetentionPolicy.SOURCE)
                    private @interface DialogStyle {}
                    public static final int STYLE_NORMAL = 0;
                    public static final int STYLE_NO_TITLE = 1;
                    public static final int STYLE_NO_FRAME = 2;
                    public static final int STYLE_NO_INPUT = 3;
                    @Deprecated public static final int STYLE_NO_INPUT_OLD = 3;

                    // Allow collections holding typedefs, similar to resource type convention
                    public void test(@DialogStyle Consumer<Integer> consumer) { } // OK 2
                    public void test(@DialogStyle SparseIntArray array) { } // OK 3
                    public void test(@DialogStyle byte id) { } // OK 4
                    private static final @DialogStyle byte[] sAppOpsToNote = new byte[5]; // OK 4
                    private static final @DialogStyle short[] sAppOpsToNote2 = new short[5]; // OK 5

                    // Repeated values are okay if exactly one of them is deprecated
                    @IntDef({STYLE_NORMAL, STYLE_NO_INPUT, STYLE_NO_INPUT_OLD}) // OK 6
                    @Retention(RetentionPolicy.SOURCE)
                    private @interface DialogStyle2 {}

                    // Repeated values are okay if scoped in different classes and same name
                    class Atsc3FrontendSettings {
                        public static final int MODULATION_UNDEFINED = 512;
                    }
                    class AtscFrontendSettings {
                        public static final int MODULATION_UNDEFINED = 512;
                    }
                    @IntDef({Atsc3FrontendSettings.MODULATION_UNDEFINED, AtscFrontendSettings.MODULATION_UNDEFINED}) // OK 7
                    @Retention(RetentionPolicy.SOURCE)
                    private @interface DialogStyle3 {}

                    // Allow ints holding long typedef
                    @LongDef(flag = true, value = {STYLE_NORMAL, STYLE_NO_TITLE, STYLE_NO_FRAME, STYLE_NO_INPUT})
                    @Retention(RetentionPolicy.SOURCE)
                    private @interface LongDialogStyle {}
                    @LongDialogStyle private int mBearerBitmask; // OK 8

                    // Error; message should ask if you meant to use @StringDef?
                    public static @DialogStyle String EXTRA_AUDIO_CODEC; // ERROR 1

                    // Make sure constant value is printed in source format (e.g. hex 0x840 instead of 2112)
                    public static final int VALUE_1 = 0x840;
                    public static final int VALUE_2 = 0x840;
                    @IntDef({VALUE_1, VALUE_2}) // ERROR 2
                    @Retention(RetentionPolicy.SOURCE)
                    private @interface DialogStyle4 {}

                    // Allow explicit aliasing
                    public static final int VALUE_3 = 0x840;
                    public static final int VALUE_4 = VALUE_3;
                    @IntDef({VALUE_3, VALUE_4}) // OK 9
                    @Retention(RetentionPolicy.SOURCE)
                    private @interface DialogStyle5 {}
                }
                """
            ).indented(),
            kotlin(
                """
                package test.pkg

                import android.util.SparseIntArray
                import androidx.annotation.IntDef
                import androidx.annotation.LongDef
                import java.util.function.Consumer

                const val STYLE_NORMAL = 0
                const val STYLE_NO_TITLE = 1
                const val STYLE_NO_FRAME = 2
                const val STYLE_NO_INPUT = 3
                @Deprecated("blah blah") const val STYLE_NO_INPUT_OLD = 3

                // Make sure constant value is printed in source format (e.g. hex 0x840 instead of 2112)
                const val VALUE_1 = 0x840
                const val VALUE_2 = 0x840

                // Allow explicit aliasing
                const val VALUE_3 = 0x840
                const val VALUE_4 = VALUE_3

                class TypedefWarnings {
                    @IntDef(STYLE_NORMAL, STYLE_NO_TITLE, STYLE_NO_FRAME, STYLE_NO_INPUT)
                    @Retention(AnnotationRetention.SOURCE)
                    private annotation class DialogStyle

                    // Allow collections holding typedefs, similar to resource type convention
                    fun test(@DialogStyle consumer: Consumer<Int?>?) {} // OK 10
                    fun test(@DialogStyle array: SparseIntArray?) {} // OK 11
                    fun test(@DialogStyle id: Byte) {} // OK 12

                    // Repeated values are okay if exactly one of them is deprecated
                    @IntDef(STYLE_NORMAL, STYLE_NO_INPUT, STYLE_NO_INPUT_OLD) // OK 13
                    @Retention(AnnotationRetention.SOURCE)
                    private annotation class DialogStyle2

                    // Repeated values are okay if scoped in different classes and same name
                    internal object Atsc3FrontendSettings {
                        const val MODULATION_UNDEFINED = 512
                    }

                    internal object AtscFrontendSettings {
                        const val MODULATION_UNDEFINED = 512
                    }

                    @IntDef(Atsc3FrontendSettings.MODULATION_UNDEFINED, AtscFrontendSettings.MODULATION_UNDEFINED) // OK 14
                    @Retention(AnnotationRetention.SOURCE)
                    private annotation class DialogStyle3

                    // Allow ints holding long typedef
                    @LongDef(
                        flag = true,
                        value = [STYLE_NORMAL.toLong(), STYLE_NO_TITLE.toLong()]
                    )
                    @Retention(AnnotationRetention.SOURCE)
                    private annotation class LongDialogStyle

                    @LongDialogStyle private val mBearerBitmask = 0 // OK 15

                    @IntDef(VALUE_1, VALUE_2) // ERROR 3
                    @Retention(AnnotationRetention.SOURCE)
                    private annotation class DialogStyle4

                    @IntDef(VALUE_3, VALUE_4) // OK 16
                    @Retention(AnnotationRetention.SOURCE)
                    private annotation class DialogStyle5

                    @DialogStyle
                    private val sAppOpsToNote = ByteArray(5) // OK 17

                    @DialogStyle
                    private val sAppOpsToNote2 = ShortArray(5) // OK 18

                    // Error; message should ask if you meant to use @StringDef?
                    @DialogStyle var EXTRA_AUDIO_CODEC : String? = null // ERROR 4
                }
                """
            ),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            """
            src/test/pkg/TypedefWarnings.java:56: Error: Constants VALUE_2 and VALUE_1 specify the same exact value (0x840); this is usually a cut & paste or merge error [UniqueConstants]
                @IntDef({VALUE_1, VALUE_2}) // ERROR 2
                                  ~~~~~~~
                src/test/pkg/TypedefWarnings.java:56: Previous same value
                @IntDef({VALUE_1, VALUE_2}) // ERROR 2
                         ~~~~~~~
            src/test/pkg/TypedefWarnings.kt:61: Error: Constants VALUE_2 and VALUE_1 specify the same exact value (0x840); this is usually a cut & paste or merge error [UniqueConstants]
                                @IntDef(VALUE_1, VALUE_2) // ERROR 3
                                                 ~~~~~~~
                src/test/pkg/TypedefWarnings.kt:61: Previous same value
                                @IntDef(VALUE_1, VALUE_2) // ERROR 3
                                        ~~~~~~~
            src/test/pkg/TypedefWarnings.java:51: Error: This annotation does not apply for type String; expected int. Should @DialogStyle be annotated with @StringDef instead? [SupportAnnotationUsage]
                public static @DialogStyle String EXTRA_AUDIO_CODEC; // ERROR 1
                              ~~~~~~~~~~~~
            src/test/pkg/TypedefWarnings.kt:76: Error: This annotation does not apply for type String; expected int. Should @test.pkg.TypedefWarnings.DialogStyle be annotated with @StringDef instead? [SupportAnnotationUsage]
                                @DialogStyle var EXTRA_AUDIO_CODEC : String? = null // ERROR 4
                                ~~~~~~~~~~~~
            4 errors, 0 warnings
            """
        )
    }

    fun testWrongUsagesInKotlin() {
        lint().files(
            kotlin(
                """
                package test.pkg

                import androidx.annotation.LayoutRes
                import android.view.View
                import android.view.ViewGroup
                import android.widget.Button

                fun ViewGroup.inflate(@LayoutRes layoutRes: Int, attachToRoot: Boolean = false): View {
                    return Button(null, null, 5)
                }
                """
            ),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun testOverlappingConstants() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=214161
        // Ensure that we don't flag a missing constant if there is an existing constant
        // with the same value already present.
        lint().files(
            java(
                "src/test/pkg/IntDefSwitchTest.java",
                """
                package test.pkg;

                import android.annotation.SuppressLint;
                import androidx.annotation.IntDef;

                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;

                public class IntDefSwitchTest {
                    @SuppressLint("UniqueConstants")
                    @IntDef(value = {CONST1, CONST2})
                    @Retention(RetentionPolicy.SOURCE)
                    public @interface Const {
                    }

                    private static final int CONST1 = 0;
                    private static final int CONST2 = CONST1;

                    void f(@Const int constant) {
                        switch (constant) {
                            case CONST1:
                                break;
                        }
                    }
                }
                """
            ),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun testWarnEnumMethod() {
        // Regression test for https://issuetracker.google.com/116747166
        lint().files(
            kotlin(
                """
                package test.pkg

                import androidx.annotation.DrawableRes;

                enum class MyEnum {
                    A, B, C, X
                }

                const val drawable_a: Int = 1
                const val drawable_b: Int = 2
                val drawable_c: Int = 3

                @DrawableRes
                private fun MyEnum.getDrawableRes() = when (this) {
                    MyEnum.A -> R.drawable.a
                    MyEnum.B -> R.drawable.b
                    MyEnum.C -> R.drawable.c
                    MyEnum.X -> throw IllegalArgumentException("Invalid")
                }"""
            ),
            java(
                """
                package test.pkg;

                public final class R {
                    public static final class drawable {
                        public static final int a = 0x7f0a0000;
                        public static final int b = 0x7f0a0001;
                        public static final int c = 0x7f0a0002;
                    }
                }
                """
            ),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun testWarnHalfFloat() {
        lint().files(
            java(
                """
                package test.pkg;

                import androidx.annotation.HalfFloat;

                public class HalfFloatWarnings {
                    @HalfFloat
                    public int wrongType(@HalfFloat int wrongType) { // ERROR
                        return 0;
                    }

                    @HalfFloat
                    public short rightType(@HalfFloat short rightType) { // ERROR
                        return 0;
                    }
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            """
            src/test/pkg/HalfFloatWarnings.java:6: Error: This annotation does not apply for type int; expected short [SupportAnnotationUsage]
                @HalfFloat
                ~~~~~~~~~~
            src/test/pkg/HalfFloatWarnings.java:7: Error: This annotation does not apply for type int; expected short [SupportAnnotationUsage]
                public int wrongType(@HalfFloat int wrongType) { // ERROR
                                     ~~~~~~~~~~
            2 errors, 0 warnings
            """
        )
    }

    fun testRestrictToArgument() {
        lint().files(
            kotlin(
                """
                package test.pkg

                import androidx.annotation.RestrictTo

                @RestrictTo
                class RestrictTest {
                }
                """
            ).indented(),
            kotlin(
                """
                    package test.pkg

                    import androidx.annotation.RestrictTo

                    @RestrictTo(RestrictTo.Scope.SUBCLASSES)
                    class RestrictTest2 {
                    }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            """
                src/test/pkg/RestrictTest.kt:5: Error: Restrict to what? Expected at least one RestrictTo.Scope arguments. [SupportAnnotationUsage]
                @RestrictTo
                ~~~~~~~~~~~
                src/test/pkg/RestrictTest2.kt:5: Error: RestrictTo.Scope.SUBCLASSES should only be specified on methods and fields [SupportAnnotationUsage]
                @RestrictTo(RestrictTo.Scope.SUBCLASSES)
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                2 errors, 0 warnings
                """
        )
    }

    fun testUnknownTypes() {
        // Regression test for
        // 133280834: False positive with support annotations and kotlin when operator
        // Can happen in editor before all when clauses are entered.
        lint().files(
            kotlin(
                """
                package test.pkg

                import androidx.annotation.DrawableRes
                import test.pkg.R // unresolved for now

                enum class Foo {
                    NOT_STARTED, IN_PROGRESS, PENDING, IN_ERROR, ACCEPTED, REJECTED
                }

                fun test2(state: Foo) {
                    @DrawableRes val imageId: Int = when (state) {
                        Foo.NOT_STARTED -> R.drawable.ic_launcher_foreground
                        Foo.IN_PROGRESS -> R.drawable.ic_launcher_foreground
                        Foo.PENDING -> R.drawable.ic_launcher_foreground
                        Foo.IN_ERROR -> R.drawable.ic_launcher_foreground
                        Foo.ACCEPTED -> R.drawable.ic_launcher_foreground
                        Foo.REJECTED -> R.drawable.ic_launcher_foreground
                    }
                }"""
            ),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun testPxOnFloats() {
        // Regression test for
        //  133205958: @Px annotation should support float
        lint().files(
            java(
                """
                package test.pkg;

                import androidx.annotation.Px;

                public class PxTest {
                    public boolean fakeDragBy(@Px float offsetPxFloat) { // OK
                    }
                    public boolean fakeDragBy2(@Dimension(unit = Dimension.PX) float offset) { // OK
                    }
                    public boolean wrongPx(@Px char c) { // ERROR
                    }
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            """
            src/test/pkg/PxTest.java:10: Error: This annotation does not apply for type char; expected int, long, float, or double [SupportAnnotationUsage]
                public boolean wrongPx(@Px char c) { // ERROR
                                       ~~~
            1 errors, 0 warnings
            """
        )
    }

    fun testDelegates() {
        // Regression test for 132782238
        lint().files(
            kotlin(
                """
                package test.pkg
                import androidx.annotation.ColorRes
                import androidx.annotation.FloatRange
                import kotlin.properties.Delegates

                @delegate:ColorRes // OK 1
                var textColor: Int by Delegates.observable(0) { _, _, newValue ->
                }

                @delegate:FloatRange(from=1.0, to=2.0) // OK 2
                var textColor2: Double by Delegates.observable(0.0) { _, _, newValue ->
                }

                @delegate:FloatRange(from=1.0, to=2.0) // ERROR
                var textColor3: String by Delegates.observable("") { _, _, newValue ->
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            """
            src/test/pkg/test.kt:14: Error: This annotation does not apply for type String; expected float or double [SupportAnnotationUsage]
            @delegate:FloatRange(from=1.0, to=2.0) // ERROR
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        )
    }

    fun testConstructorTarget() {
        lint().files(
            kotlin(
                """
                package test.pkg

                import androidx.annotation.VisibleForTesting

                class TestClass(
                    @VisibleForTesting val p1: String, // ERROR
                    @param:VisibleForTesting val p2: String, // ERROR
                    @get:VisibleForTesting val p3: String, // OK
                    @get:VisibleForTesting var p4: String, // OK
                    @get:[VisibleForTesting] val p5: String, // OK
                )
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            """
            src/test/pkg/TestClass.kt:6: Error: Did you mean @get:VisibleForTesting? Without get: this annotates the constructor parameter itself instead of the associated getter. [SupportAnnotationUsage]
                @VisibleForTesting val p1: String, // ERROR
                ~~~~~~~~~~~~~~~~~~
            src/test/pkg/TestClass.kt:7: Error: Did you mean @get:VisibleForTesting? Without get: this annotates the constructor parameter itself instead of the associated getter. [SupportAnnotationUsage]
                @param:VisibleForTesting val p2: String, // ERROR
                ~~~~~~~~~~~~~~~~~~~~~~~~
            2 errors, 0 warnings
            """
        ).expectFixDiffs(
            """
            Fix for src/test/pkg/TestClass.kt line 6: Change to `@get:`:
            @@ -6 +6
            -     @VisibleForTesting val p1: String, // ERROR
            +     @get:VisibleForTesting val p1: String, // ERROR
            """
        )
    }

    fun testPermissions() {
        lint().files(
            kotlin(
                """
                package test.pkg

                import android.Manifest.permission.ACCESS_COARSE_LOCATION
                import android.Manifest.permission.ACCESS_FINE_LOCATION
                import androidx.annotation.RequiresPermission

                @RequiresPermission.Read(RequiresPermission(allOf = [ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION]))
                @RequiresPermission.Write(RequiresPermission(anyOf = [ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION]))
                fun read() {
                }

                @RequiresPermission(allOf = [ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION])
                fun allOf() {
                }

                @RequiresPermission(anyOf = [ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION])
                fun anyOf() {
                }

                @RequiresPermission(anyOf = [ACCESS_COARSE_LOCATION])
                fun single() {
                }

                @RequiresPermission(anyOf = [ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION], allOf = [ACCESS_COARSE_LOCATION])
                fun tooMany() {
                }

                @RequiresPermission
                fun missing() {
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            """
            src/test/pkg/test.kt:24: Error: Only specify one of value, anyOf or allOf [SupportAnnotationUsage]
            @RequiresPermission(anyOf = [ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION], allOf = [ACCESS_COARSE_LOCATION])
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/test.kt:28: Error: For methods, permission annotation should specify one of value, anyOf or allOf [SupportAnnotationUsage]
            @RequiresPermission
            ~~~~~~~~~~~~~~~~~~~
            2 errors, 0 warnings
            """
        )
    }

    fun testOpenForTesting() {
        lint().files(
            java(
                """
                import androidx.annotation.OpenForTesting;

                @OpenForTesting // WARN
                public class Test {
                }
                """
            ).indented(),
            kotlin(
                """
                import androidx.annotation.OpenForTesting

                @OpenForTesting // OK
                class KotlinTest
                """
            ).indented(),
            openForTestingStub
        ).run().expect(
            """
            src/Test.java:3: Error: @OpenForTesting only applies to Kotlin APIs [SupportAnnotationUsage]
            @OpenForTesting // WARN
            ~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        )
    }

    fun testEmptySuper() {
        lint().files(
            java(
                """
                import androidx.annotation.EmptySuper;

                public class TestJava {
                  @EmptySuper // WARN 1
                  public final void method() { }
                  @EmptySuper // OK
                  public void ok() { }
                }
                """
            ).indented(),
            kotlin(
                """
                import androidx.annotation.EmptySuper
                open class TestKotlin {
                  @EmptySuper // WARN 2
                  fun method() { }
                  @EmptySuper // OK
                  open fun ok() { }
                }
                """
            ).indented(),
            emptySuperStub
        ).run().expect(
            """
            src/TestJava.java:4: Error: @EmptySuper is pointless on a final method [SupportAnnotationUsage]
              @EmptySuper // WARN 1
              ~~~~~~~~~~~
            src/TestKotlin.kt:3: Error: @EmptySuper is pointless on a final method [SupportAnnotationUsage]
              @EmptySuper // WARN 2
              ~~~~~~~~~~~
            2 errors, 0 warnings
            """
        )
    }

    fun testReturnThis() {
        lint().files(
            java(
                """
                import androidx.annotation.ReturnThis;

                public class JavaTest {
                    @ReturnThis // WARN 1
                    public void voidMethod() { }
                    @ReturnThis // WARN 2
                    public int primitiveMethod() { }
                    @ReturnThis // OK
                    public Integer okMethod() { }
                }
                """
            ).indented(),
            kotlin(
                """
                import androidx.annotation.ReturnThis

                class KotlinTest {
                    @ReturnThis // WARN 3
                    fun voidMethod() { }
                    @ReturnThis // WARN 4
                    fun primitiveMethod(): Int { }
                    @ReturnThis // OK
                    fun okMethod(): Any? { }
                }
                """
            ).indented(),
            returnThisStub
        ).run().expect(
            """
            src/JavaTest.java:4: Error: @ReturnThis should not be specified on void or primitive methods [SupportAnnotationUsage]
                @ReturnThis // WARN 1
                ~~~~~~~~~~~
            src/JavaTest.java:6: Error: @ReturnThis should not be specified on void or primitive methods [SupportAnnotationUsage]
                @ReturnThis // WARN 2
                ~~~~~~~~~~~
            src/KotlinTest.kt:4: Error: @ReturnThis should not be specified on void or primitive methods [SupportAnnotationUsage]
                @ReturnThis // WARN 3
                ~~~~~~~~~~~
            src/KotlinTest.kt:6: Error: @ReturnThis should not be specified on void or primitive methods [SupportAnnotationUsage]
                @ReturnThis // WARN 4
                ~~~~~~~~~~~
            4 errors, 0 warnings
            """
        )
    }

    override fun getDetector(): Detector {
        return AnnotationDetector()
    }

    override fun getIssues(): List<Issue> {
        val issues =
            super.getIssues()

        // Need these issues on to be found by the registry as well to look up scope
        // in id references (these ids are referenced in the unit test java file below)
        issues.add(ApiDetector.UNSUPPORTED)
        issues.add(SdCardDetector.ISSUE)
        return issues
    }

    companion object {
        const val SUPPORT_JAR_PATH = "libs/support-annotations.jar"
    }
}
