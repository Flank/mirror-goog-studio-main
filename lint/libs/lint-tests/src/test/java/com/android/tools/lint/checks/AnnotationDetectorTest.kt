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
                import android.support.annotation.IntDef;
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

                }
                """
            ).indented(),
            Companion.SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            """
                src/test/pkg/IntDefTest.java:9: Error: Constants STYLE_NO_INPUT and STYLE_NO_FRAME specify the same exact value (2); this is usually a cut & paste or merge error [UniqueConstants]
                    @IntDef({STYLE_NORMAL, STYLE_NO_TITLE, STYLE_NO_FRAME, STYLE_NO_INPUT})
                                                                           ~~~~~~~~~~~~~~
                    src/test/pkg/IntDefTest.java:9: Previous same value
                src/test/pkg/IntDefTest.java:28: Error: Constants FLAG3 and FLAG2 specify the same exact value (562949953421312); this is usually a cut & paste or merge error [UniqueConstants]
                    @IntDef({FLAG2, FLAG3, FLAG1})
                                    ~~~~~
                    src/test/pkg/IntDefTest.java:28: Previous same value
                2 errors, 0 warnings
                """
        )
    }

    fun testFlagStyle() {
        lint().files(
            java(
                "src/test/pkg/IntDefTest.java",
                """
                package test.pkg;
                import android.support.annotation.IntDef;

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

                    @IntDef(flag = true, value={FLAG1, FLAG2})
                    @Retention(RetentionPolicy.SOURCE)
                    private @interface Flags2 {}

                    @IntDef(flag = true, value={FLAG9, FLAG10, FLAG11})
                    @Retention(RetentionPolicy.SOURCE)
                    private @interface Flags3 {}

                    @IntDef(flag = true, value={FLAG1, FLAG3, FLAG4})
                    @Retention(RetentionPolicy.SOURCE)
                    private @interface Flags4 {}

                    @IntDef(flag = true, value={FLAG5, FLAG6, FLAG7, FLAG8})
                    @Retention(RetentionPolicy.SOURCE)
                    private @interface Flags5 {}

                    @IntDef(flag = true, value={FLAG5, FLAG6, FLAG7, FLAG8})
                    @Retention(RetentionPolicy.SOURCE)
                    private @interface Flags6 {}

                    @IntDef(flag = true, value={FLAG12, FLAG13, FLAG14})
                    @Retention(RetentionPolicy.SOURCE)
                    private @interface Flags7 {}
                }"""
            ).indented(),
            Companion.SUPPORT_ANNOTATIONS_JAR
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
                Fix for src/test/pkg/IntDefTest.java line 12: Replace with 1L << 44:
                @@ -13 +13
                -     public static final long FLAG5 = 0x100000000000L;
                +     public static final long FLAG5 = 1L << 44;
                Fix for src/test/pkg/IntDefTest.java line 13: Replace with 1L << 49:
                @@ -14 +14
                -     public static final long FLAG6 = 0x0002000000000000L;
                +     public static final long FLAG6 = 1L << 49;
                Fix for src/test/pkg/IntDefTest.java line 14: Replace with 1L << 3:
                @@ -15 +15
                -     public static final long FLAG7 = 8L;
                +     public static final long FLAG7 = 1L << 3;
                Fix for src/test/pkg/IntDefTest.java line 19: Replace with 1 << 4:
                @@ -20 +20
                -     public static final int  FLAG12 = 0x10;
                +     public static final int  FLAG12 = 1 << 4;

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
                import android.support.annotation.IntDef;

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
            Companion.SUPPORT_ANNOTATIONS_JAR
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

                import android.support.annotation.IntDef;

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
            Companion.SUPPORT_ANNOTATIONS_JAR
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
                import android.support.annotation.IntDef;
                import android.support.annotation.IntRange;
                import android.support.annotation.FloatRange;
                import android.support.annotation.CheckResult;
                import android.support.annotation.ColorInt;
                import android.support.annotation.DrawableRes;
                import android.support.annotation.Size;
                import android.support.annotation.RequiresPermission;
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
                }
                """
            ).indented(),
            Companion.SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            """
                src/test/pkg/WrongUsages.java:34: Error: This annotation does not apply for type String; expected int or long [SupportAnnotationUsage]
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
                13 errors, 0 warnings
                """
        )
    }

    fun testWrongUsagesInKotlin() {
        lint().files(
            kotlin(
                """
                package test.pkg

                import android.support.annotation.LayoutRes
                import android.view.View
                import android.view.ViewGroup
                import android.widget.Button

                fun ViewGroup.inflate(@LayoutRes layoutRes: Int, attachToRoot: Boolean = false): View {
                    return Button(null, null, 5)
                }
                """
            ),
            Companion.SUPPORT_ANNOTATIONS_JAR
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
                import android.support.annotation.IntDef;

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
            Companion.SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun testWarnEnumMethod() {
        // Regression test for https://issuetracker.google.com/116747166
        lint().files(
            kotlin(
                """
                package test.pkg

                import android.support.annotation.DrawableRes;

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
            Companion.SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun testWarnHalfFloat() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.support.annotation.HalfFloat;

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
            Companion.SUPPORT_ANNOTATIONS_JAR
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

                import android.support.annotation.RestrictTo

                @RestrictTo
                class RestrictTest {
                }
                """
            ).indented(),
            kotlin(
                """
                    package test.pkg

                    import android.support.annotation.RestrictTo

                    @RestrictTo(RestrictTo.Scope.SUBCLASSES)
                    class RestrictTest2 {
                    }
                """
            ).indented(),
            Companion.SUPPORT_ANNOTATIONS_JAR
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

                import android.support.annotation.DrawableRes
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
            Companion.SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun testPxOnFloats() {
        // Regression test for
        //  133205958: @Px annotation should support float
        lint().files(
            java(
                """
                package test.pkg;

                import android.support.annotation.Px;

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
            Companion.SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            """
            src/test/pkg/PxTest.java:10: Error: This annotation does not apply for type char; expected int, long, float, or double [SupportAnnotationUsage]
                public boolean wrongPx(@Px char c) { // ERROR
                                       ~~~
            1 errors, 0 warnings
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

        // Snapshot of support library: support-annotations-26.0.0-SNAPSHOT.jar
        @JvmField
        val SUPPORT_ANNOTATIONS_JAR_BASE64_GZIP = (
            "" +
                "H4sIAAAAAAAAAN18dVTVW9c10iVIh3R3d3d3iyDdBzh0SEi30tKd0p3SDSIgIt0pCIKEiOB34AZ6nvtc" +
                "9f3G+8f34fiN4XWcM3/jzr1Ze+255loq8lDQiBAQ8PAQGtEJchCgH0iIP34QQI+ipIYoo6ySFDMUhMoP" +
                "H6z2hdy/D/oAHujB/P6DiqJKslKS6hpMilKfFEeGFeQZmcbvyjPSjY68rlFjmWRf3ljdgAQD+/6t8KDH" +
                "yM7U0d7K9D9e+v3n7n33OScXINDe0flfP3//Hz5vZGdn72zkbGVv9x9fLVxbRyK7AwGxDnpo/v2rYvb2" +
                "ADUzJyYTgJGTU1C0vDIk673+QvhSErI2FKLIZ1DSp8vj6n6UsaV+e3aN7ixmydGxE7ELTAgGrwKIO1DN" +
                "0vDJibiqZL+xp5+/Ir/87PUY7glc4/HySIATKhMuh4m6Ke3TPDHYsjZWuq7WBvSHnct0dLUJNmmDeqNY" +
                "jmVRWFr46eyNSIRr9NnP2z4rfhaPw7aZxMZ5hmw3nAzPiLaOpjaHqDm2vi+8PUmTvK/XSbLuMLLc3EqC" +
                "NoPO6HC2cS6S/ZB5xjY664tTtIBiMDK+OpGDJzSaIm5UQLTxByDb8spupLTJIiqUNe0xGbvjylavuQnb" +
                "RcPB637W7b1+pU16BcaA1AnF0/YZs44ssTOI+WKRk4c2KtRzuUXSp9FHdwOpCESHm5hVJHmC6+MwqAmp" +
                "Cw1+5PmVG3MgJYjjPdDD8O88SzgauRkZA8xuuVZRvOY6nazSP/yzy0fDrzxAU1QiQU0dMtm7pqR6AknV" +
                "/KSU8i0zH4w/2xB9vXNFszYZcD9OETDB/cbDnq3i0/nJ1R2IM+BKZzGcBVEau+VrhRhJHjUEtm0U9nOq" +
                "5wjBSWfvHOnx9IKD6q0DkBLoyRqn+uRKIkfx5hELDzGKtdu+Un7NDgu/ax1xCBWQaq0YmogZH5NXlSAr" +
                "+ubxN5lCKtXxUU5k6MxXuHqa3CNm9WLyzS3WQjJjM6kHahSqn3iThEF866oTOXpCk4L4hhKte5qE3Oiu" +
                "/xE4QiwpQpT/lD3XWJh5YtL09HX78MY43fB43AY++fBy84Ri0zgRFCxn8+NlpbvvhaefUPUKaQqGX/Q0" +
                "3JsFQoQwefvxdaE5ExDwb/PxnMP+SDd1fQm7LohqZtCvBe2/0y1r56xmZGdh9ifXqvLKXSL3MmDkoGKe" +
                "22vkjAdu9xIJeOpm98TKyVrJRMJVd4jJyT2PZ0SgvtS/IduFS2JFZJWCn//TK+5nI14+L30hOgg1nHnG" +
                "VzRC2CGLVSkKoGPfcStCUuIHi7HtRFSOSjOg5J3C0pNGR4s9vnyE4AeQ7hl/wv31CcbAg2znBzybDRj1" +
                "htBDdgRyvkMtV/MHcF76LkrLXVoE5CLNnVeQwcOUqAkuyiIPK0n6qt/6FtmifQ7IlyuaqX1DqjKTsVxl" +
                "OUJ5xNTQ/+qJclVOQ5oDRX6mwsF4McZAbbH8OKv+3lilwv2q1g+TRQu7au9yUOtls0QeMvbnZmzUei46" +
                "3Y+hx0YhX0GN8hBIDZOxc/2ygOG36oleKLwCJbvstQvc3dAMa4Zso1gkj+ma8wt+hP/SFMMZmi1VBLuJ" +
                "PxyBd2DNPkuzAxgjjuBqpJzMnOUwHQKVj5LAp/Zep/gI9ccFKmmp/SQOWiAh0AKR//sCqbj/uTQxw0pd" +
                "IpjB31SzIMPdn+OumXXGMuk1v1AxjsNPEZtuZBFPjiY1kxuK7yhp+njHi2Yt2ZKCKoppM9Np04t6i+2A" +
                "uMIeYn+sEmi9MtLFK56faWpF0cyGjaxVaa14HsvO0aVgxYsh3hSdq8MjR5lik1wE0x1lj8u/aW38KfPJ" +
                "gmwOgCoee+1x8K4XFWYKfDU8LZNHmur4prBldA+bbmpg/cHxBfLzcwSoDKVAqn0u4f2+rlpundcwovnU" +
                "bh+nd1nioaidBxzEX4yd6GUuWvtUfPjMLE59nAa3ZNb4+VFIYSIvktUXvag3Ek8/SHJlUhdf1WHLqUdU" +
                "xCAlx3qmXj0Zx/Rdh6YP/mIeumhJR/YKmMD/riCBCqobm1AdMg9mghFTNadFsk3yDKV3GBJLo8pmxAIT" +
                "+lSFvdo+z24dIqRHC4Enx2lvZR3jxzWgTMcr+MXYL2pnZftd7B9WgmQFLQQZi3+o+8NGlkfYDMkw8tpY" +
                "wTJssGz3uJgU8PDV2GpNC8S7/lyJYtZVY/TUY+HCLa+tWB+vtpdQ26sT3ckhTqg4yCdhg290UDTI8PD1" +
                "SlwoakblADDdmvE2JVOOBWycyhTvZnWMEsbxuvW3cYXRPHcilgKXuB3aEmeHeqG6bVkxZOWhPQI16skG" +
                "rRwviJ0B6FgHeqLM63EI3S4OnRp58lZhLcyjcGHOu4pu92VVTniThKK+INexPuMR9A+lQg4Wlaky53l/" +
                "+NGlL5fUF/km+HNar1icm5uo/RH8a09NpD8lRDJ2K65QXSwB2Fnit4k60tGHlnRFEwAhOUQz4kPvKN7z" +
                "iHU3MdPIE+TurPfIDg/u4PzIc/G93d5f5dnZ2fF/nef5WY7YBxaU9f4rdEN1mja86skp1LE2tqWVQ9uR" +
                "K0zOkZnwL4977bsOUnkM5G03Vv1W6pPC8ij9BcToHsasczRdovJxhYW7lWfhHA0GrjQ3GtJN5Nf2LOBs" +
                "BUg0nxS0RFEUfSFQyJTyDp5Nkk4jEpXAgO0mydNhJzhx/8y3Nh4tHHynDtMzBr/uY41XDWvx5dzZ1hqu" +
                "7kVV7rlcX+JKATDu0r4eD1fOGXVZCWHDvixLrl5c9dke6YYNugtB9so8Tjzl03Fnx9W8jfVjpB95DrLL" +
                "COYEcSwHiim8/86zmpmDi5WjmZOKmaOtlZMT6J8otB2tnP88BBJU9eUhRTEFc8tTzfO9ZUWh0dRQ6wKf" +
                "vaRoijWkSZ0UV85dNDVNr0vWz26fEhfOIsZaCBogMfBq3BpNf9x4lpb8uuIMFcJFQ19Db695GgO4yaYg" +
                "s/x+r5X8ZEDCen3GlCtCYU+2LFenj27ikUhFGJvpY9OCId22ZuwiDh8ERc8YWVwkd3plWiGJMDw1+SCG" +
                "3C+ZjrwxTrTAexR8WuqeGlp3xXRL6yGl0+PXgtSefSvflWcvSNJOYzi4IFWU7b9swGMdtKq7VFIn8T04" +
                "O2j2ojnGFw1AEgZWMasYDE7vxKmy3nf0cSwu40yeU9GVL21svTdJqGA5wOzWqrHWVdjRK4doDQmJ95TB" +
                "t3kAYX0ZmLA11OQ3TBvB2YeMSOMotL7JJ72P90AM4T4toKAyiHAfJwaFwaRF6zPYAWyQ5AC4Aq1BLWg9" +
                "6P59LcSNAAB1F6CZ41+7vhO06+8FfxM1wsIaT4ULwO8eF3Q/loBYuxNgxP1IIGEBgZb2Q8Fc7EdrIncI" +
                "4qHpE4jrvJI/7ry9nT254uA14xNpPSmeZ2NRQFFL6r65yMx8YzELmng5r85TI9opfyDnp9lBrPZhtVEs" +
                "1FouJR4rZ9nGdA2F0sBHPcbUPeRx51g2/ecYKwUZyVVIEgOQE3pIG+IHV8LbqapscxrBK6bRFIG7nuJ0" +
                "e/76y/vvT6pWrVud1YFPdCvWjs34yOaR0h+Sf2o8ljQqvbCZsQ0aW/jGm87M6xT4yDb18sUXaDHyeSiN" +
                "Qr/upYYwrdCXKlFiAohfWHOjMNK0duBCHYJHwU7JgKXDcHYQgzKgHc3z2ztazczI9L9taGPUOo2/NvTx" +
                "xJsibrwHFrYWWtsVJ2Oo29KSbghsAzjE+01OW0T7vG6bn3YvoTo4XzDRMbnq7yHwOOFRUQAfCO/KCIaT" +
                "cx4psKVKVp3kTcUkaEnOIm1zqrImE/WqmyEbLMBr4LcHUr0coKiFXBxSD3+mio5anC9Wr3qp3JQ00ITJ" +
                "A49u+zY+I141IBp7Qmq5KM6xVeX9oUFTW2S/6YNVU48lVCu52dMl8x6Kwren/Ip+XwOW2g+OssdsoO8y" +
                "fe3obet4KxeRDkshDdmUqWFto3ioTxNFevrw0fQOChXHuhv/o/fAqvyOc7kvnHf8kKUT2/XWpxEIyo/0" +
                "eBuAvGZVfUmidyRHQ+b1tgYEyeVpjMiDXmHWV3Zfkk34mQHeIS79uBAdexe5+KCFePPz7azu7GhlZyFh" +
                "Zv5XQjksByV6r3TUhtOaKBwOTVXC/9F8gLSyKDl87N2WEhLeAQqr7sBBwPQI7BEoe4cQltujoeCFFlBQ" +
                "5tR823vs5e3rBdnY6ReSEtIc4i3mUKDQF9g9XLL/RuZVvH084ZcY+TrCRfT3ijYYhWlKe4LtJSM8Yrxy" +
                "1bWvUWdDq46osY48jagJgpJdPEeYPV88Dta+ol0BMIoxIgib9rv6UVQePsuLw13IjKjEhHm9oh8BrJ5B" +
                "K3EeUArcSj1PXKmKjNzCwFatm7GDc2D3QZ61P7SR7ntEwy+DIfixcMY0Ns9uocWLPbsen+hYav75ZZrd" +
                "yEPGHFGS9fX1Df4iEZosGn3ZZi9tOZRF+4Wu+vl+/yExHFz+6d5FSFfRSMQXwj8yPLiDTCMEYhcR8qeH" +
                "pJK9nZILAPBXuHgFygrv9VuoFpFA+vQHqPICa+GYqTK0aORKbdXx0df8GMzE5RIXCgCy7pyoQk9AF1FO" +
                "KQEOh7jz13sj9mxx5zc30fs8dcsD/iZISvCTwXSvjReVoalVzuKPHUnLtwC98GlBTZOD/s9izdjQ0uSK" +
                "Od6ExcpDapcbATrXR8zbYVoKV7068zff8EM+71UY4ETHJVV7xBqztj4qbJklgV6hKP4uv5vU79NqcKgZ" +
                "e3mMDc47ws5XnuN8G/CaGenb4wdoRbAceZ8dxNLGiHmYNvHLl/L33SW677x0I4RUfO/hI5HrdxCV+xyC" +
                "XUukRfn4Ti5X3BsFSvXHyYXFQ8dLycLSlGOzpevKmwJT0EyIDfN6zeJPRBBskUboO08bErP9r7BrsL8Q" +
                "lmrzThrivKfyCkIMEbTEtcK3wpvhdjRc/wh2gA4AxMxwQGswfuentyZNKw1Lx7/jS1C0ojwUKGYvHdvU" +
                "1ekKMxH1MNO4O4DuqAn1q9nNZtxYPerN/JPxttWAmqArTtQrCN/4JHhdVCTYo9FoD/NFFuJM+wMIO+C6" +
                "0VK3X4h4sHlehEABfzK6+GOycxslsqaMiARDHhQks3X/jHU1JfX3tPlRLGollN3nA6w+dJMjOO0wvllr" +
                "xqJDKo7DQK3RQFMGI/aISW0yxlKXM2LnUnW8Cn1x38kIsuC4I1imIzy8Yf34dyG0TQeHLlLY1gLI594s" +
                "hpYnAf2hIXFCqGLB+L5EqHhMQXKOjBi+D0yTR18t8QqnGm8DvPx30oy7gbrWLx8YPPfLNrbH2tdQuZji" +
                "w+d6NKP9uo/x4RIVx3jXU3kDFcraql3T17JbYOwmyJYIUYCYXQU91P/Oro7t90rLK6U/lZYnZD6EArjC" +
                "KAQzoACClShTDVvdQkpFpPCAKi+v1rRBvqvE4yYN1MA4rAxT/JK5M2LP9+YmDewgTHsMXL0WAJBTqmir" +
                "MUPUSAOmPslJJp0lGpPj6kkE1TsGqScPJ4uV11V1yeU6wXJnm3hRHnfhtCP4Gk4YB82ufs3odOHFyL5O" +
                "A63vZyNaz1/CpeNSvimj3w4ReIqnl4hDQfYwi3K/XoCYxnmNco4TXcMXMbb/8ImoSRe5UghJDkq36j21" +
                "UsKnFwJe6UeWPcRdTzIYjgXYHfe3ej1t2S7OFjN9TcZmXAhfJybpwmrnB+wJ1Qw/EQ06JWrU9HvfMJWl" +
                "VC8NSgOFNu7DK1p2+jkZZIUD4W2ppZX4lJ62g2la9Rtfl1BAJDf+PFhLuxg5mpqZinn8xbWwMqTovd6l" +
                "NLjColBffCRy1mDLNaxEraIsKlxkt6oq3UaEB9V7MSbPAivMV+A+iuNtMTxZRY9I9545f8k9c351/hUS" +
                "GLEGEQURcucpfEIQGy+GUnn5Y4w6lWxVVwIppjeuXtJFklYZUY0lUSwnhXpmCNRnw6mQlGkojrvH8Usn" +
                "eqh3G8oU0x356py6ZqTSdDm7dMxMXGO9VBcPcDOqXQ4d+yA1m5nTF8mzv8yGZV6QpWyU3+FnDUNX3iVe" +
                "Z+T0rGXoIvb8QEOmR5dqWqzU5SGUmp8851zmo2BZanLRjfCMYKXVecLzLiNkZ1wN5WPqzOVcsiHxkwrY" +
                "6cQWWmtp3G9QRLnBCzl7Ru3uxpMO4MGBofrZ4HVwmAM9TP/OqoajkZ2T1fVfv9vFite6SvASTLa/Qeij" +
                "610swXy6PI4Rg67IQpnveVhbi1GdtxfrKt9l8GkZ9aM0noVgKJXivKmFuUXGjBvbHxHCYW9lTPxdd4zI" +
                "Y+MhgezsIZms+1tqOEN0ueR0dpuOlDBKOWoP67JwNIuspLzLohJQaKGZpJ4eshdbtH2l/QwVF480kTjB" +
                "7/fJmopsGFo6kkOLjf5B+xnoOpMsNzU/lhb4etybBKi7gDbsJZXYOfzeMzomlfkR8+DlKWYP6opIFue+" +
                "tLcPNNprDi9kqbSWWL5XUXKXOXpMinbfBr4+ei5ikYultBWv41cjd/4K5TEkJGebS6N691SDbdbgVUoN" +
                "0YVteQO87Z7hu6CKIozTyoMRP/Z05h8p/hge++A6QrwHPfQ/Va3MLMwc/5NfySx/xBuRUGZdBpVAUJMm" +
                "Jg9WDR4H1ZwWxK+zbAvlSoXHR7jPuZE1RD3iGNwzbiB+zwd8rmUr0F2xMznACQ4H8iRs9E0oLB2V3yYL" +
                "a20eR15pTexDWiDWZpiqTUqxwiuTo1iJ+1bxSmx+LR1sdgxx+fS+GHYG0ucpjeOrqsv1SQiDLJ1E5All" +
                "kU5Ei1dwhLs6kvwPx5BaByWWr2+LbyjxNvcjPvkouL7csRNH1jT4uvOpiFnl/uPsflThdYSAbGJgKVqk" +
                "56OPXJ+6ss8wP0Oisj3I02WcZ1gCOFtfnS58SxexEfbgMFA1hbZDfD7ja/lQJKvnDOI0XuVqHPEFgJ/A" +
                "ZZrIsU2qaA2+f8pbMhnoH5xQkJCY8Mb+R7KZ2aVm6EBEH4Melp+T7Qi0Bxg52zv+U1zOIqMjuonLApVw" +
                "zzLoGGJMX8UGAQ8Nh9hIKfMX9lz/zjw875n8JYFLfzk8P0WBOAPu38iyzrgc1AUD6ARyQch85Las8lHo" +
                "U6asmy8TpuFhcRyyy/TWFDYdy6OvL+kT22mditu4BgigS7pyF3FSng65y0MBlkYmC+NaiWhpGVtyDdH7" +
                "zt9E+6UGTXcRv5BmKuCCorOCZL2E/Lyth5DK9lSqnTpF0RXojp5zJ6AvqR90R1fACM4F3dHpI7+AovOh" +
                "ew9x95PO9LGIkYGgTBwra7aLloNMVNOxxahnm/QKDAElE1Gu7bPXQrgb6vdCuGvmjRDekX0TnaEZqfuV" +
                "ZpWU2sCic2D687ckIM6XQQ/VT3g3/U4D51fuZrkn9Lw+G0bO4y5yaP9YFR5uskJquJxocWwREpRjnPoG" +
                "XV1uT/Fke18H6rI06SghjTgFw3lL/1n6gZfBEnEmxEd4bZQ0fuBKB2WAXV4JWWfYSLS/Fb4xr15YlCGQ" +
                "UGtogHPUjBYHzzG/Fk3KWpOt8ays71WgbvPqc2A/X/FUEXKBMvL+e05SfLVo6dgpKVOsE8Kv5Ok7ClQP" +
                "H7IceWCyNhYzh0pWSYg3h3guDo89xbF3olC9eBth4M8aMBoZkgbJnIcSV7ylXr4FR77f/tqDZ+N1m/QS" +
                "Gh1jrcJC9ZCTflL7wf5UX1a7PT6DVhIubEOd054br2FHlugZxJ6ViFMIo2UcodoU6WMZL7hsJGkgYIHb" +
                "kixN5HVXcVBbUA3xjxxzWq11XSfTyD+Xv68z6etqw20i1w0KIxYltoA6Yp810a/+RK86I5atbNUZ+yjy" +
                "skmIhrj0hxI/FFCkin1LgfN9IiyXhlVLBMm0x19zINCNfHGzqbmBgE4/mBBsArSiu7ECUR7KMkpZ+xtq" +
                "1VXr79jrTaGVuvSsWLfEYxCci9XCzHUp6MiBK/h9iQqKtqcPK1tk0143TXg5Si+3JiE0SUGySwzJUzkR" +
                "LFzB3UWnXU2w2m50ZgEF6fl7Vc5DnI7Pe12kHWse1eoCA+leKx1vuyFoQOGrefGQKm2fEOK8DN51K+LH" +
                "n6hUPtAPEYnzzCAeLzp4nLtNKdKPaLkVJmTo2jtJodjTM3rUSyZ7Asqm2+Wy6hYHiV+7lyKkYZU3Xb3n" +
                "ugNJ2XrfUAmBq70OGSuyYVsXt6Hy0YZKgO006w69Bs9ktlGeVZ6Z6buBjJhLsGj+PETc8BdlPyl7O+f/" +
                "ddkPKtf2/0t59SHtUSU5iOPNn99aJKxszX7ISWyulaYlHZeSsikfQk+aS8hn+50mysXlGtM5pav5Iejv" +
                "SGJsyVoedLZPifuK+Grs5MXLGWX7tbIne1CxD/ZvzfE/YRoVTHPnOQH2qWQmCURtiFDGZo9rV6hJs+2W" +
                "9kix6Ek01WNeH5kYg3VcWjpmzn1ADL4+5cBHX1a5V84U094QajHt45VHJMvokg2Q51WjySqVzX3LMokr" +
                "ct9F9LyO3UwRSAVk+nKg2O0gJJN3375ZX97/hXLnscOkRVGUR8/OPYlJIxZksXDObtVnIczsRHfPxXFZ" +
                "5hFeykTpfn7Q9ih18srj7PVr1lpX7z+Y1lyhaTm4ZtryoLPsO6a9b5gGxe5M1UTLrJ31ARDTh2BMU5GM" +
                "XFH8GtOijo5GHj9kJzdMk1X6+99kJ3I81X8elX+WMBVkBm+qxb6vAoQ7iM0AipioqdN5BwoWGTsKm2uf" +
                "PhB2CP7NNPGuQFQxM4ax5UcOrLnQ6lE1AHS3ZDw9uTZHQnIaZU/ChBRuseafTOt+vmbaOa1CBi9FmEUP" +
                "hx0NT17nNVlClIzjXPMlKt9EWGRDedb9o8OklepGQ7IJGLzhBeytgMB597gPKQwa3ihUyt1E4rY95HbE" +
                "oqb8x3CiliavgNMbK7vx0lBXuLDIbmKv1Bm3fBIr3vFx+n67nCEQt3nnxvNQDYBbjig9U1bzqkOl2g11" +
                "H5ScTCG+mHIFTNa4Lm3aBPKhALfnceIR7nKkE/Ba8fJeQf5IdTAHJjPlr6WCKgAXRyOA0z+S/VcqOEDE" +
                "4/miUiKWM/omL5mIpsynbNFHv7oJ4UM8JyTXEupS8qjAm+SRrXcXcE+YmATTPHjOgJ1/bGsS1dhsXi0P" +
                "Og3swdIa9IedQKyNWSobXvlq0/KbEIKRyx7cMsbahv5DCCH/LoSIbUy2XlcO6ML+oXIARbng/mZBCl3D" +
                "G6Hgda6QuO0IudIz0J0xwAh0Z0R76inglXpoOZDZ9WRFdFvSOjYocyM1SvbDku9Zi4nlB35QVoL3MDi1" +
                "y+gUyfbvECL8YwjpqnIyUE3kEe2WfXMdQrh/ZJsMUmkbF8R0589vjDJGAHMpgL2R8w+1eW9JQxisw7XV" +
                "TsvIleVOQeBx7aodyYMa0wdN7iwwdLS0jA+aKb7+IXw4vL8jED1tuqjwZbHVamv3Yg1D5P4WkZJAmieP" +
                "SRGqQswzSYoEQ0vnJs9n8SunPn3aaStkjLUJTqzx8rxqZGOzOnb54/LBu9NWXr0nk1v3u5SDsGY1UZET" +
                "sLRhzuuvYk6jYtc4OTXgBuVnEfCY3NxMBTIR5btSF7yKetIYak7EooumUCeWeUe64tNo3eKov5ikOft/" +
                "tsXNe7nMWqCaUITTJzHvKDgYlfZ52LiKpelyt2UGSdyJnwfvVU9Seblrm1lLKTaUeJjxzYbuRXwRf1B0" +
                "FDqHU5T9FEq7LYcBGEoOdxeuHyUFTNyz1qenxwNx+/bnu1nRyMruR2lJ8DrxCzY4ox+DZCRGgloVz0FH" +
                "wuNj07RFE5d/0UNCSsXNi6VdTJivlH/FCesDIYxoxK4SLKNz9Jh6yelM9+za/ZAVgoEaQvRUgICdg1x/" +
                "nXVhOB53FoMUy3dYlqIBqQobudAoO1JvokZv8IGc9032V2fcSMDfpxyUcrHasIzqoscgiye3j8s0lBwd" +
                "SQkwj/mimxfEeXAVoORCtxZlgxt5odoD1FrskBSiNmkqOfpCQ3km2DY4IBY1GCoMLR48ReIe6f6ZY1l0" +
                "QGWN+Q1ad52KbLnZakU4w5umc36LFvrg0SnaJEYaLuSt/M05GFc8XLpTrRUlmDrislyjtSjvE8B81Ym+" +
                "GAbjXtMxlmaXEEpabbpz250fGQY8sF/2AbHrA/lThm927w+Whzk5UM6X/oGyU373Uq1uUWQc59NyrrJN" +
                "qjEJF+VdmN2AUoeHZHMAWW6YzK/MxF8h2vuYVSyQ6rEQmUzZtkaSZ2aSn1U0uEE0BpJjnCrysKxEi5TH" +
                "DRD57c14JHQgRpkUJ4Wb6m0QJiKaefl2bd8boLFqfaoqusXrQDMhyrGeC/mh9Ok9HRVHOpaleCv0I12K" +
                "ewxvA02HLByeGwjUkJKrHL3RkRRcDIcdz12Og4WFRtEvq4IeYuRC0RHSFphaxPL7Gh2GiZR5RW4rgxbE" +
                "zJe9OmEeJ+LhKtYRy6RdxJ9B2ABzYNS7nP9a1XR+tjMqJCDVRiWTrNK7NXtzmslR8l2Jylm2pHfIoxYu" +
                "oYyp3UciUP0lwtBud02VdL7GD2wP4uddtilMHivGyaLpZByvCgp9UkHjSZpZNPUqlKhAFn4Zr4eCc9dD" +
                "AjYHgKVAsVkQ5kGBnf1RvjY7tGeCGLKEMTfVD0untz1oET/4HS+ggwpgdUZ0RFCfnffqKVfL7J6V+hXY" +
                "MvZ5Zp8OgZYwFAYCgvVnBR8nZ0crE2cNewp1E3vgX6VLdSNlfNF73tRwbsuk4di9xraM9VmatW+nEDXv" +
                "WYmL2XSTIhwG8K7Ive4g5EX5JvNNpjePy3b2c5Pwi509OUz1N/HkyTP9I63e7C0Xu2cNmRAZpLtBoYVC" +
                "umWySQkf50NIHrHUkJrMS3feTTxU66P4lMpWkKQ8FMqSGcva/ZUe4SFknphPYRKtFnTj2xdvJ2BoPN5M" +
                "fgCI2zSc1D/J9zefAgwXUc6maw1XpweapyTm8gabUYV7zlVFvVCvzmnlo+1YIhfwUp+f82UW2bxiaafv" +
                "aChpALx8weupKNI4zUqYW/w8qt5hcoxdO6/WoghYmm4ZFmw+hfGOU2KfaWMyVk7h/ZVSfbTqW4I7tLqj" +
                "mKcz1ueGIlUZGGXjRvrlPasBU+EPSqTWEDGR1X3fWRHtuMrmSsmgUi9ckJBg7NpIUELWZESiNipWGmrW" +
                "ST2oudu3OCfGUVcwauw7rq5M281BwDc7xtiytWb2wlUIf7JGm/N9waYzwNixV0dESfP+y9UPbbNpM1YF" +
                "sLEvE0wtTF32XlzNGvpDvaMdjtTpFHR1scACkDOSkhs6QPjAqO7RhJqIdvKIIVIlJxtTdY0gi0FK2XGJ" +
                "PC+crVSYYEOLOONscHMu1Sac4P9mAAldevYsQThu3bbQy+nM1DMhQy96sYKa0DzlzlGcoPIQayDLNPcr" +
                "R5JlpmcSnnjsI8+UaopV9QJxzg6fVG/Cv4qxyKxAWkEKZlr+oiDT0U2EUSl7rjpydvTOkvm0gpmGySvo" +
                "ApJsmLg55ltoqWNyUH/oIU9UKoOJTlCEz5PaSlzekK1j4g107GRngRVe+egqhVO1AWrrQO+Tg3v0ooMu" +
                "KOfIj1C8yOC6CWjsUL4SRLrzwANZ6BxYqL6RrL+fSCgP1UcuwXLD8alb3SHL0spj6ptkhy/UVk+mys5K" +
                "3ZJ+OmVfLJ5Q4PgEWdbtJI+qsuuSbv8j7VhNJx/VxArYCVITFHXnF09ocXuAvaOCvZ3F9yc0ZtqNe87h" +
                "0JCjD/hxGZQO6b6jjC19UF0KSj4h74JO6JkPsR/fCXyG+qrap4267NdDlc49OJL6rq3i4JkyhH7aY55D" +
                "4PuVyGxeDjMhNMFiaHF6jqH1qvGaC14Dh+CI2kktph4ZbMdSiay6qnm5XCTQCe0BOqFHtu53Xp/QVXDB" +
                "s+ElsY7833TgpAYHU/Dfd69z3iUPHl4gxHtt0RV7aN5ARFuVNQB4iWlqjezP4R5WTZNv+uL1IMYlq1IX" +
                "iQ8xjJWwe6SVRtm7Z8bodk0Zm1JKf0qPm3anXWOLKEp9TqxVyNr8S9X6WiF+2WKglFND5bIX/73GQahH" +
                "3gckjrEev1z7IkSC1QHR4c6qgFOwSjN8Xy072a8VJ0Tt/jGxh7z1JhJcexMH8QfErm2gVj8m9mnWZLe5" +
                "5ul1Yi+C85xoVCDtcaNhkbJCRp9jFvrg2LInzm2i+aIu7YcbFM73N6iK/HrHww7gwxGJCXRRItKEMhlH" +
                "6/krOMJUiV5+7TGkc9MuoL4eC9aOmu7qfsQnUbK/Ek2UAuU/E82QvxJNvOtEMx2UaBJ3Xxcnbu+qN8UJ" +
                "yw8tT/9MNEVPM2fMDWnGhZ48pIxvr/Onm0GRFKwOia+QnRzviHEyUEnkkXozGBaXGPcGTBN4xBbgS/Fr" +
                "NlApRyMTMAn9pRIoE+p/nW9r6afWBgvb8981MG2T5TvuND3Jfq6hgZTfDrzO+Jd22tqEhe4so5UT8Lgt" +
                "d/nJwy7IaEv6YySTBlzl3QuL0Q3SQdG+rgWFJLA5Rjk9GJIM1qO1DZSUgz1rM5tXKMqnv0Lf16F+i5/Q" +
                "eRzWqS+IkUPqLyCmez9Gc9L7Iyrfm2SF+nrD9U80xkB1g3sRxmHBjSFfWp9nEzIvbsqofCeDET2SIaRS" +
                "f12o8xpVat438xNwKNP7mTnmrQx26XuWKF0yX4eCTa+AHDA3a+V6n5DFcCzrMcQpR9Z5yY0MNkn6WOYc" +
                "RQzn6fJ0s/2YBI9fRoI6vTe9BVjQmOY336cFMb30800ta+d8W7KPfiUPKYrbl3loI09fxxwaKoSSg3M2" +
                "VhUX74dWvo2dF4J74oKHZ2GWjvX1Hd8x3LG05AG6GVx2N8tB/4FHsIeQ0NLiHb216U6/AHE4aciZ/ElW" +
                "BNMXdZ0BIk1GCpb5mtlNuWGRdRtvJYZFSPTLTt6Uhoi04fSNh+4nmJtlm4GS9GI9Ob84O0BpyniNblF2" +
                "Qd2C2PIqq5Eeo+Ly0FLQHKU8VbEdNN03knSVkvHwTr3Xj98vcsGMctF74cGY4rXOttpbp1Tlt6dgiPdK" +
                "tXw4v9u+UWdw+Rxl7pEc+1MxTlecQy8iqdOK04tuyPy6Gu60idAavqXP03xB4cuf1bxiKdTxUHAe7DvO" +
                "58NBoBIALpG/TJtzZUbTGIYcqJe7eqST7mWHqqmhkMCb7fmC5S6tq18UjUBMP4b86d7+y6wiCrT6Kwe9" +
                "KV+k50/SScC0o4X1SeQQ7XXmUndJlxYn1r7tcdc1JJUfqq9/+PeVtUSKh6NxcGR07zG/vetNHIEozzbv" +
                "yL6jCfUUflsdPy/yiauPERoNdyA8ZYIAJiNXnhUJZ6sovMaChoyQUWLSUeAZJBc1FfQogA7WYQDOPFZK" +
                "FVc+7qqzgDJPvjd04d4A6howp/t11wv8KB4/f5wCh0wX9yfe8XqnKUdJlFGhBlE78ehFX62FzWgyp8NN" +
                "YGkqi3bYBgPDVWqvUPPvw7YKi+cs1t7PkM5K9O4sPAyVXU83X/vwRLEyRziiLfO9sNsctNb+3FXZGzIV" +
                "AB9qxoEQl3e/ZPHTt/WHtW2mdkgBHVUjlOmLSSeUfmsjTJtvijFIosXVDZKGTeSzTNr8k8397pTgYqI9" +
                "EqefF0XD2x3GC7Ut0q1EoJxnZqUjj7eohJmrzO3CSfjQleF4MURIo30Opi54HVhhdYOWSgQKAoL9d31F" +
                "fyaaaqrKGKL3rhLr02A0RO6pokkAZuFeShB5N8eTcxW/3NyflCEemUB5Ffvh0cuX5DsGnzqJl5+xjVIZ" +
                "J3CwCszv7Y2cvfLi5hf28blAWcZlFiRwd99fGetG8pst1MOiqJxiDT6qT5yKPQFSqETJPsUFUroMPikz" +
                "L3lMW5FXkY93IRtDz+5DX4mGG4dVrZLMm4304L0YxEisrjQO5NyrKUO0MqpXIxvaIcl4tP2JulkTgHRN" +
                "HUocHdlqNuKJ4on5x9QFFflDg6e0mTQ2eFezWFjTUfLqRQjeGlrpNikRVZBwvpyJvug+CjB4W1WRfb4k" +
                "lwolKyM7tm4BFGjTDxVkzIQK/B1CIj5lFyBoK1ioG49dZr2LmCD5IPGSeWfLh6byq5DCkiS1wDPXSfmJ" +
                "z+L8Ucb7nrMILQRlZwE8hjIDVdoTkUcKUV4PlQofPPpacrGZBD215Z/zSR7/6GKPeZInM73jsCyF4HIx" +
                "m7vBpOnSGt5nl/Jqvka75GuJzsoc3RIdtg1BY8Tjd353xN09uaF2pxtR0ozi6AUN3RAeTm++sTggLdwN" +
                "JBToKneCK0IjWgtjV06fcdpMZr/QMZ+ginthF3Hfxd1r0WoS/Nc3dt5MRgK0J1BAv75s/74ntKycrIwB" +
                "ZlL2jhqgS4jV35mW6h9ayIY4tGQHKqbgdpil8fry+BvzO+IUlQ+49BT9wilt5F1bcL/9+TtMKSHDcchi" +
                "tdTKXZH+h2mygWd4hSJEF6UQmff1AXJ6Vm8HH7Kkdsk00CxYQEEiZZI2OHarEXXUe9eBsm6HQ3zx04Xo" +
                "8F1dxdQBFlPPy8XYEfTiVzWhj8wYcCeea5vqO8RPBN8v1lQXOKTgQA7yU8qpshyONn2bHblBg2lLCEPG" +
                "paZob+7ViWftkxPphUbv8wGDXc+5rADNkdP2I3HOChJ5/MsFVlOcepzgL8JqyiGOX82R5svZcVn0nDjD" +
                "atM4Vk2F3sXMlH450Q4+mm0TTgljPXG72orYhX3P4ZJHMzGQJRWTf5GYzmcpPL3Y4dEVdLdV3N0NCqfT" +
                "EuYxjWoBAGeSNAKTTt6RGM45FjoEUxpSk8AX6sc1uZTiF/1FwVXd2eP7lpHvipMk16aRdvSnoHhaiChP" +
                "8QBqhvIPwVXWirtFH/3RqwDUZXG8QqrwEEXAxDcFC583ySPX0jbEfHcTHDVRmiewLwsvlUweSIPFZtnr" +
                "WsJy3Z9zv3OZ7D/7cxD+7s+BisO87s9BRS7/uz8HXlNld194u4cmGaAvKnzdn1PdakhXTam7ajf8yU+M" +
                "e9E5lUz0ZMvwCwcRi6WCe88OmsSkEQ+iWHhdt0NmSAU7UdRnE1yWRgzPGLymj5/IFBXNv11d1PDCpusv" +
                "HXoFhZkBNfmG7NL1YZHpnM860hE12utgXaoFwz1Nz2iXRCy77hDwncG8gGe05Od1nHBw/ArzI9Uo2Rbr" +
                "1w6/GdDD+JNGKCs7UzPH/yZSHY1DIkGtmtAYk0KRq1tF4CIWowcEJs2va+jlu8bzJXx5h/v5zleawFci" +
                "/BJdsUv85xkt75pv7hjcPEdjh8DllehsvB2yZzuPCnrGmuLostPatyhid5N1EIPXKlWkRnckmdYp8jPU" +
                "ZuHJ63RZ0p4RrBWKKXofan+E42PazEOmbEC22FCQ6UPnqpHxpp3omnX7JtrPl3A4xHks/ZLGmIfWfpks" +
                "M7ZqXtLDOyn2gsjAzDS7by1aGBoqWEvYPdz98/zWtUpVsUbanX+jUjFfq1QzaTVNjMGns/RJIzRcyIX5" +
                "p7tYtyqVwbVKlXCtUlWf2F6rVI3HdzW7mE7T9vitvoHVfgdbTAuxQQS3/Xw/39ziQFnZ35c4eSjQJW6y" +
                "3qak7GRtVRQziEigMYRIsLe5NyiBsUWemYhiITg2lsoslfH4D4NDj8SYpw5LtIe4+WLrx5ucDBm4uTLd" +
                "HRai7u/6Yphsi5yUV9SiKJ9JzGwfG9ANPxqnhle1jlGFrT5YrDbLQl6ri6EnObomX7ApdIH1NfB+OUEL" +
                "tSUgicEW8I14V0c+xl6/myXHSg6ouhBdZTwadaKvveu3Lp+aZKs9n6JrkjK2RYKE7l9QpnxILsodHsLh" +
                "mT5GkYeqaij9tIukuuuueo1YRHI7kza9flJ7k/BD0u0Pm31vYF5/eGO2oe1a8h7j3uTeQgA/52R7lV9B" +
                "WUOih9bclmV2FJS0gcoWj0S/6LvOmIBFsOrMoT+rMe6veVRBV7j/tn3FaG801qK/NVZSTePoe3GpaeEl" +
                "k0/VCtW+4UMJPyEOqsTT/U5jPbk8RPgnjdW7bvOAN2zdlUnLf4WDIT4GyTNGnlexuCKeq0umLBH5uUn/" +
                "52IVdgMA4pVKf69/fPb605WcNIRBnS5z7OIKCV2dA1C8kChke9QS3NlILAna2Xf11tnZElvoKp6pOS6t" +
                "7UYyTLoTHIYYDozhQyujoWbCCcDjWX91YTNuxKYbrFP4ahZjxsftuwXQ6h2bwYd5w9sbvNI6+bilnQlK" +
                "vHeRbr4467TEhQPQWlWREcVf3yZvmRMk+LwtW25aem7QLw7cjK2WxDdJBeJ2/+cR4iYY/9cevuX+zoDD" +
                "tSfc1/2SQTf9kuhMf5r4Ur+7KHveQ0mdzrMv+LR4rLh5XSeAIFqZCGBHHRVsNByLVIyh5IlHxHM2btGk" +
                "2UQkH3lMX4WGXJ4j9rCpL3mEEk3PxiQoQWoLeV+s8DBhTN7gK9pVB0Uvfl1vL0bAnKNi4E0PXz3aC5KL" +
                "feGJMFW2meZgn4G1pE4Xj24NU/navBbmLaggu9YdLkn0twYBO8PuHVmsK2iF0vdiuoGYgRo2QWtCqEsW" +
                "AviHWyyGH0n+6JmUTEkZ2f/W0i4I+yH9zL2tq4etkZbQomGCCQoWqxluWQnmRbtOAF8vkaZgeEgP970U" +
                "IHQ/k3cAXRdCLUEanyX/9lfwaKEVgX9dA9v+VdP1P5bA/pAlQlcGiASuS2BxnNH6Xb0BtMOB8Qzfaz5T" +
                "UO7TPVTeFnyfFCzYbuwiRMCVlcnrKnqweWkEQ7afQlg2VqrCCw8JDzfSKf8VNrr4GPmUAT0l9VU5K/LI" +
                "STqLxruEoGD8yrtK8bN3Oqa8WrD5M2TuDQWJPvSpGhlbxomu0/2vAU+d6eKi6LeDBJ7ilQ8h9ZGlxlXN" +
                "63sIyQxGcS+WDoidvDP8KrDHziElsHqKJlHfvUwJb1uKhfJYwCvz43aQcNCd7pcqNQrDyZk4JQmjI0u+" +
                "DSUmlgf4qIVIcsjdLjsMLd6uuKDAvN+ZjviiXVd8YbY8ynNqf84rYKznDkr6RTDdCvyfthwfMLKpPwXs" +
                "/WKqcROaf6+2Kzvze7VdLFaOZt0djpzSGsz7nUC0jbAY+pQhhVHMQ4pxKdziuMgVKufIConqj9dtSjs8" +
                "BqrGllcdQP0RCVMGI8EIDS6xjXqXC2LnvIR/qu1CSdq1DroqYr8QgosrNHqWzbWK1vCMpJrwSwBJbZU5" +
                "z97RR5eh3Du+yFDB/DEj/xu1XSjmnh6KX9vXCkYe9i7/bA0Jv7GGGOuwISWW0yHHmCKbkgD0ktjSomOz" +
                "KdkJQWld0HVa9xBn+Q9riOBS0/pNM8H28lB3UwgS6nPkk6rRN0lf2EiDpWjq+bjRzD7kT8MH79JoS5Vb" +
                "0rHzyofS39isI1eErlvC6j72Mi3/VUdnncf6vo7ecvJHHZ0p+4brAh6jG64FMftv6+h3qZh/rKNPO+3i" +
                "unp8FtoYfyIccqc7V+WTxnBT5sZundKrpeaWt6wcZ1EhNZSgiF0PTLwUeoiMTPn4piXMoCzL2nbuynOK" +
                "aNY9SqUf7SloW7OtQN+lLlRKURoF39Yy7SgBv2jCUTSzc/lHP2Xon8Jm3vWujpdRg6QNjICriUd4oFaf" +
                "t/B3AHlg8r2f8tqE496RH8AOt4XCBP0+jkKZ/H1JEjS++ISHzAMLyga1ZbqRur/clLVKkRx048kd+oZ4" +
                "3ImeO0NLgXPyOV6Ks2NgbsqMa2GTuirctT7rvntcWMe1CadAnm1kAWfrMmnP881CMO4LlMudT0XUf7sp" +
                "L/2IgaV4kc2PPvJ96vI7w/w8r/ZeMqUoKHUjVTE2pd3bV0gpq46YP6WChg2SOyh1z7eGkURl9THEYzka" +
                "XxsYzXrCp3wAVF4D8kmHwGAXAxUqglyrjR654SFwE07Oc5Jn127KOz8Xf8QtzUxsQES7AP5K7mIEr13X" +
                "wUsJz9S/QkpiIAXo3rNt7ZoIh+iDDFgJjXrrGLm1WiVv5yb72Qb7poRejhYuz8DFXvNppDXVrd1XqB1i" +
                "foWjw+GJ+RMMv44vFKS70CnvuAOS9YZYEvgQyJ/Tps8jcfDp3qdOZ2uWtp8ojBTLwsbL7D3OUkMvclui" +
                "vcKN2di3bRNfBqzXqTt5qmopqukyV0E+v893VIF2qrC3lUeo+OA0JwkvxsSfXYpOPtlTy/5bgFCqRm+J" +
                "Vxb/tkRMh8dhh445Au9mefjFZfzRVpQ2knHRl0liYZTHDMwnHOzW+LrtOkkjtk8Bn7uYyhpQqNnDz19o" +
                "0tRylTBOv2cUorziUcWmye3STCiJlewos1VqqAzA78c2aSE4w52hJli+dyLgHUTCAQTvdwz3KuH/xZkC" +
                "1/27YO7V/yUxWUjbkTGSSYwh5CYf6ZbDa+prYtggEy+/yfn+X1aTG9JdZA1BVDv9vMR+W5v9Syz7s8TO" +
                "qFtS5lYSSqaL2Qn/ZTOCtEA3UtZKS/QLyZwzLe2OnSxX0FUKqs8TYkolOp1A/y8sFig1Fkz8LTe968A7" +
                "/PgA68p8Uc0AW0iP18/FlbIm7/lLmw+sJvXjElWxdH48P5SdM8+5P5hV0j8IpCHPZyfNM6aLFrM/1JH0" +
                "qygSSMNsJlM5NkU/byUKTDLIQBz9AuiB13gYptL5RBjGYQyh6VAfqWV/rlgKNXn7EpDTKIOnVXFJZjXE" +
                "8rGfZiS1fsUD2tWCWgX7pFFsoXPPiX4dqlSmP44/4W6/Q7Ho/fJPYpmp2H7zz+kFdI5HX5JWGjY/lX74" +
                "jMrl4XTrYiUg0lqxu5XbTtX9uJASvUih0IHfe58e8jE7XBrT044d4UdFUk9EYBkjlvzWgVJJ4olBHj3I" +
                "mEZ3Limr0a05UCIqoTfi67M7siGHaUvzKlO6dBF3aQEid9gjaF5lb1SUZP64UDNNjxNCQQsVB/nT8/XG" +
                "EHgragap6cuviWAKNliVEdtcTn348pKh+sKY2OvYCkB2P1RRRlzVK7oqPUHGVS0lbPkPSRP9AF43yD+w" +
                "ifcszYmd221z53wFrQPtdHe62KThqaBYbxT8OsbmXBNT5GQBdY0aLwC6ZLxJsqfo/gMNUg35dHz/Txeo" +
                "2owIZU7kiik22pdxZn1DAG5maW+XFPPwMeCbb3I4pcSLQ/yyln35XCMnE2QvRycb20j24KbeDOW1WQ26" +
                "ZW9sXsYKG08uJ1Vyz2DQd6NIAYoXZgxICLyMoo4YPizN+buSNOt3na77Szy1izdaej3Y/Jx27pGjsAYg" +
                "he9TbHu2fFKYBAPPofHiyuQZ5Ca+6DR2p6ZyTxsPgkCX/7ZzlwSFz3STHBOZuY+sIO7GTpZmfLeREFsm" +
                "1SGXQtdqY8LHL1onHh9bMFruK39+shdz5PjZKiccf4328YsiJ6FL0swVr1NxIbgX6G1GGcmw7E9fJyJi" +
                "P+qVZHi5SA83/PWR0fCLLvypKqMVSmvEpfHalZC7qVyAmcpLsHzJM6dz8jrOTYIeyn9fT3kzM+DthatL" +
                "BDNNMguS7nOyOws+KsGHO/YZKbrxTxittDHWOhk+iiXG19P+XVFoLAlzJ3GIS0/eSz1zPboRDJyBxitj" +
                "XTFBJsGe8djnSc1sVMG6mTQtKWH5hjx3IyQGeQUHgwh61ylkXwTGWlUbNuLiPiOMMLWOWPInjM0BrXHw" +
                "kFpPENHMVT+fggnvo2RYt2UJtO6ZTVimI1raDy3zemfGKbthXgkxXA/E+z/wuxGJJwujcnISLqE2dj/2" +
                "+/bl0wQQSnh1JXc4IDr7y/LhxbJW//Zpq9+mU4zCkYbSnmSMmVX51ZVZfc+Y7ha8FDs+/UIt66yniy1s" +
                "cGzbQ5bCwCNuLZW3XmUJ+TZzgfjvLXHP/emM4FpgT5E57tYi793n+QzGcyG9tbgdiGNzyJ/yrG7l+Zd7" +
                "KOb1df9ln4WfGindZwMNaN57L6cDpJXp4+GL4+ODe9xJkEpNZa3uW4a5j28dLUN9VnkP0Nj26xEcGfVa" +
                "snfjqrnp7AHCUGIh0mFq8pBnjSRhOEIefoqA9WN83/yoA3KlSfNqQIxapc0Hh8xkha7iY43pYEb886AW" +
                "LindzyjaV/CQO/U4Sl9aqQj2oq0jLHx4jdmlSCyC7Ej6WskLcrY+mr2j9cshxXtltK9igiRe496hn6yP" +
                "QfMtoFZA+ngLng4roH4O+qgEdRrAtn7HMf9t2/5H1kXL195PVV5IMnoWzjVLGr5hoZ/cdi3SLOQOViIJ" +
                "6yt70JtY12y3nJH9qsBKvcWOq+XUGykG0gkOQxm51mr7YpEi1DW2hjRav61rL8pduEOcnrieQCd5Wbtn" +
                "3W15AAMBEkGlUs4Yy/5JuBgdXZ6qzjPE+zSmOWLY9nN9HLD8lREtwTNEswgoF0CIEDZviCBxlKemPIkr" +
                "EsGtyIVgXcm65nn+v1i0VzNy+7WOwomFPxtX5K+V+r/EYQIKKtCN7Vvy6Fcq9vQbtdL9iTzqqADP2bK/" +
                "KDubZOKySv5AFuV6S0drQ/z9wWU5zNkXWEqa5ea0jmVRCVp4f8xuKlRj9jpP/QhHRatVxDhKjDefyA75" +
                "hzY8UJvVt+A7FiarWe0CcxktjRawe+SvY45U27PwfCsAc8H90DUK+q2B/2cPAcOxJGDnDhr8exYCGNLe" +
                "2ZXG190NtDjkV5iwx2bFW8mqTsLMUzZxM8IB+6d9WTUHfBmMrFXIdnJwM8w3DYX7dxrV/d4r/TC7qXZ5" +
                "oKniu9lNYAe/B2+D4i9Kw9r2jja/Ig2LSxe8YDSmyZNDhmdtGixuzHPY+DdpmJDAw9KD5yNwQOWZRrlJ" +
                "cwIta21Ggg/1qyWOsHVPukT/FZ4RdnKLYwocxzQ17uF4NKkXgE7g9bUtHLAa0e6/pO0glDui4tgN1Br5" +
                "oztW4znZqzLv6yuyGl6Fnrj9dBRZcNwqLNNxYOQGU/huQPz82WdrJewXC6if9ccsLI/9Q0JDngkSBSJj" +
                "XbHhs+iFM4zLUV2RZb5O4Ep14mroHdN7DLNGC7qr1Y75vOzighIfXGSbp80+KtGcBLRWV2REPaxvUyzK" +
                "9SN83pajMN2bEedN/YpYRf4OJCbUf5/J9tf8sw+iEN8PXgP/GviEtr9+8vxo/8u8tn97MfwPL3b9brX/" +
                "7Vv3fvhWxj/skX/79v0fvj36E7c3GBD4ALfb/32EO78+zg0cFXxc2S2qA+TvDS8DRwafzHWLvAz1G3O6" +
                "wGHB50ndwvrB/NJ0KXBA8OFIt4AwcL8+KgkcFXwU0C2qLfyvDwYCRwUffHOL+hbh/2IMDvhrwGe63L7G" +
                "COl3JryA44JPObnFLUT+n888AX8L+AiP27ewoPzOQA9wXPDBFbe4sai/PsYCHBV8FMMtKjbabwxmAIcF" +
                "n0FwC+uM/ssTCcBBwfvtb0EXMH6n+x4cF7zj/BaXHet3+8/BscFbrW+xE7F/q/EaHBi8rfgWGAr395uM" +
                "wdHBm2dv0R/j/WorLTgmeLPoLeYS/m+0joLDgrc/3sKaEvx6MyQ4Kniz3y3qCOFvtP6Bw4J3tt3CchH/" +
                "Rp8bOCx4F9ctbDbJb/V0gQODNyzdAqOS/U77EjgueKPOLa49+W+17YADg/en3AKPU/xWtwo4MHjHxC1w" +
                "EtX/oH8CHB7cd34LX0X7Oy50cFxwv/UtLhr9L7uvwUHB3cW3oO4Mv+c1BkcGd9LeIm8y/rKvFhwU3DB6" +
                "C6rB/Hv2UXBkcH/jLfI3lv+R2xH8BeBmudsXYLD/T6xz4Pjgxq9b/EKO37CBgcOCm5xuYdG5ftPy9B9p" +
                "EJi15xbajfs3jD7gsOCmllvYSZ7fsbiA44KbOW5xOfh+09rxH0yA2RZuofP5f8fE8B83J7AK/XeLJ/gb" +
                "9XpwWPBq9C2st9Dv1KbBccErr7e4O8K/XocFRwWvM96iqor+XtURHBm8mnaL3Cv2e7U1cGTwwtF3e03i" +
                "t8pI4MDghY5b4AXJ3yl7gOOCC+63uGXSvyi//4dMAaYt30Jiyf6i0gwOCa5+3kL2yf2yFgoOCq713YIy" +
                "Kvym8qciDwN7/VUe0J9l0O0qVvH6v/4PSKCfCIhgAAA="
            )
        private val SUPPORT_ANNOTATIONS_JAR: TestFile =
            base64gzip(
                SUPPORT_JAR_PATH,
                SUPPORT_ANNOTATIONS_JAR_BASE64_GZIP
            )
        private val SUPPORT_ANNOTATIONS_CLASS_PATH =
            classpath(SUPPORT_JAR_PATH)
    }
}
