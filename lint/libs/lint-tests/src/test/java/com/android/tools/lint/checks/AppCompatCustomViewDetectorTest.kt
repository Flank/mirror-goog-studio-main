/*
 * Copyright (C) 2016 The Android Open Source Project
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

class AppCompatCustomViewDetectorTest : AbstractCheckTest() {

    private val mTestClass = java(
        """
        package test.pkg;

        import android.content.Context;
        import android.util.AttributeSet;
        import android.widget.Button;

        public class MyButton extends Button implements Runnable {
            public MyButton(Context context, AttributeSet attrs, int defStyleAttr) {
                super(context, attrs, defStyleAttr);
            }

            @Override
            public void run() {
            }
        }
        """
    ).indented()

    private val appCompatJar = jar("libs/appcompat-v7-18.0.0.jar")

    override fun getDetector(): Detector {
        return AppCompatCustomViewDetector()
    }

    fun test() {
        val expected =
            """
            src/test/pkg/TestAppCompatSuperClasses.java:23: Error: This custom view should extend android.support.v7.widget.AppCompatButton instead [AppCompatCustomView]
                public class MyButton1 extends Button { // ERROR
                                               ~~~~~~
            src/test/pkg/TestAppCompatSuperClasses.java:28: Error: This custom view should extend android.support.v7.widget.AppCompatButton instead [AppCompatCustomView]
                public class MyButton2 extends Button implements Runnable { // ERROR
                                               ~~~~~~
            src/test/pkg/TestAppCompatSuperClasses.java:47: Error: This custom view should extend android.support.v7.widget.AppCompatEditText instead [AppCompatCustomView]
                public class MyEditText extends EditText { // ERROR
                                                ~~~~~~~~
            src/test/pkg/TestAppCompatSuperClasses.java:51: Error: This custom view should extend android.support.v7.widget.AppCompatTextView instead [AppCompatCustomView]
                public class MyTextView extends TextView { // ERROR
                                                ~~~~~~~~
            src/test/pkg/TestAppCompatSuperClasses.java:55: Error: This custom view should extend android.support.v7.widget.AppCompatCheckBox instead [AppCompatCustomView]
                public class MyCheckBox extends CheckBox { // ERROR
                                                ~~~~~~~~
            src/test/pkg/TestAppCompatSuperClasses.java:59: Error: This custom view should extend android.support.v7.widget.AppCompatCheckedTextView instead [AppCompatCustomView]
                public class MyCheckedTextView extends CheckedTextView { // ERROR
                                                       ~~~~~~~~~~~~~~~
            src/test/pkg/TestAppCompatSuperClasses.java:63: Error: This custom view should extend android.support.v7.widget.AppCompatImageButton instead [AppCompatCustomView]
                public class MyImageButton extends ImageButton { // ERROR
                                                   ~~~~~~~~~~~
            src/test/pkg/TestAppCompatSuperClasses.java:67: Error: This custom view should extend android.support.v7.widget.AppCompatImageView instead [AppCompatCustomView]
                public class MyImageView extends ImageView { // ERROR
                                                 ~~~~~~~~~
            src/test/pkg/TestAppCompatSuperClasses.java:71: Error: This custom view should extend android.support.v7.widget.AppCompatMultiAutoCompleteTextView instead [AppCompatCustomView]
                public class MyMultiAutoCompleteTextView extends MultiAutoCompleteTextView { // ERROR
                                                                 ~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/TestAppCompatSuperClasses.java:75: Error: This custom view should extend android.support.v7.widget.AppCompatAutoCompleteTextView instead [AppCompatCustomView]
                public class MyAutoCompleteTextView extends AutoCompleteTextView { // ERROR
                                                            ~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/TestAppCompatSuperClasses.java:79: Error: This custom view should extend android.support.v7.widget.AppCompatRadioButton instead [AppCompatCustomView]
                public class MyRadioButton extends RadioButton { // ERROR
                                                   ~~~~~~~~~~~
            src/test/pkg/TestAppCompatSuperClasses.java:83: Error: This custom view should extend android.support.v7.widget.AppCompatRatingBar instead [AppCompatCustomView]
                public class MyRatingBar extends RatingBar { // ERROR
                                                 ~~~~~~~~~
            src/test/pkg/TestAppCompatSuperClasses.java:87: Error: This custom view should extend android.support.v7.widget.AppCompatSeekBar instead [AppCompatCustomView]
                public class MySeekBar extends SeekBar { // ERROR
                                               ~~~~~~~
            src/test/pkg/TestAppCompatSuperClasses.java:91: Error: This custom view should extend android.support.v7.widget.AppCompatSpinner instead [AppCompatCustomView]
                public class MySpinner extends Spinner { // ERROR
                                               ~~~~~~~
            14 errors, 0 warnings
            """

        lint().files(
            java(
                """
                package test.pkg;

                import android.annotation.SuppressLint;
                import android.content.Context;
                import android.widget.AutoCompleteTextView;
                import android.widget.Button;
                import android.widget.CalendarView;
                import android.widget.CheckBox;
                import android.widget.CheckedTextView;
                import android.widget.Chronometer;
                import android.widget.EditText;
                import android.widget.ImageButton;
                import android.widget.ImageView;
                import android.widget.MultiAutoCompleteTextView;
                import android.widget.RadioButton;
                import android.widget.RatingBar;
                import android.widget.SeekBar;
                import android.widget.Spinner;
                import android.widget.TextView;

                @SuppressWarnings("unused")
                public class TestAppCompatSuperClasses {
                    public class MyButton1 extends Button { // ERROR
                        public MyButton1(Context context) { super(context); }
                    }

                    // Check extends+implements list
                    public class MyButton2 extends Button implements Runnable { // ERROR
                        public MyButton2(Context context) { super(context); }

                        @Override
                        public void run() {
                        }
                    }

                    public class MyButton3 extends MyButton1 { // Indirect: OK
                        public MyButton3(Context context) { super(context); }
                    }

                    @SuppressLint("AppCompatCustomView")
                    public class MyButton4 extends Button { // Suppressed: OK
                        public MyButton4(Context context) { super(context); }
                    }

                    // Other widgets

                    public class MyEditText extends EditText { // ERROR
                        public MyEditText(Context context) { super(context); }
                    }

                    public class MyTextView extends TextView { // ERROR
                        public MyTextView(Context context) { super(context); }
                    }

                    public class MyCheckBox extends CheckBox { // ERROR
                        public MyCheckBox(Context context) { super(context); }
                    }

                    public class MyCheckedTextView extends CheckedTextView { // ERROR
                        public MyCheckedTextView(Context context) { super(context); }
                    }

                    public class MyImageButton extends ImageButton { // ERROR
                        public MyImageButton(Context context) { super(context); }
                    }

                    public class MyImageView extends ImageView { // ERROR
                        public MyImageView(Context context) { super(context); }
                    }

                    public class MyMultiAutoCompleteTextView extends MultiAutoCompleteTextView { // ERROR
                        public MyMultiAutoCompleteTextView(Context context) { super(context); }
                    }

                    public class MyAutoCompleteTextView extends AutoCompleteTextView { // ERROR
                        public MyAutoCompleteTextView(Context context) { super(context); }
                    }

                    public class MyRadioButton extends RadioButton { // ERROR
                        public MyRadioButton(Context context) { super(context); }
                    }

                    public class MyRatingBar extends RatingBar { // ERROR
                        public MyRatingBar(Context context) { super(context); }
                    }

                    public class MySeekBar extends SeekBar { // ERROR
                        public MySeekBar(Context context) { super(context); }
                    }

                    public class MySpinner extends Spinner { // ERROR
                        public MySpinner(Context context) { super(context); }
                    }

                    // No current appcompat delegates

                    public class MyCalendarView extends CalendarView { // OK
                        public MyCalendarView(Context context) { super(context); }
                    }

                    public class MyChronometer extends Chronometer { // OK
                        public MyChronometer(Context context) { super(context); }
                    }
                }
                """
            ).indented(),
            appCompatJar
        ).run().expect(expected)
    }

    fun testNoWarningsWithoutAppCompatDependency() {
        lint().files(mTestClass).run().expectClean()
    }

    fun testWarningsForMinSdk20() {
        val expected =
            """
            src/test/pkg/MyButton.java:7: Error: This custom view should extend android.support.v7.widget.AppCompatButton instead [AppCompatCustomView]
            public class MyButton extends Button implements Runnable {
                                          ~~~~~~
            1 errors, 0 warnings
            """
        lint().files(mTestClass, appCompatJar, manifest().minSdk(20)).run()
            .expect(expected)
    }

    fun testWarningsForMinSdkVersion22() {
        // We're not applying a minSdkVersion filter yet/ever
        val expected =
            """
            src/test/pkg/MyButton.java:7: Error: This custom view should extend android.support.v7.widget.AppCompatButton instead [AppCompatCustomView]
            public class MyButton extends Button implements Runnable {
                                          ~~~~~~
            1 errors, 0 warnings
            """
        lint().files(mTestClass, appCompatJar, manifest().minSdk(20)).run()
            .expect(expected)
    }

    fun testQuickfix() {
        lint().files(mTestClass, appCompatJar, manifest().minSdk(20))
            .run()
            .expectFixDiffs(
                """
                Fix for src/test/pkg/MyButton.java line 6: Extend AppCompat widget instead:
                @@ -7 +7
                - public class MyButton extends Button implements Runnable {
                + public class MyButton extends android.support.v7.widget.AppCompatButton implements Runnable {
                """
            )
    }

    fun testAndroidX() {
        // Regression test for https://issuetracker.google.com/132668553
        val expected =
            """
            src/test/pkg/MyButton.java:7: Error: This custom view should extend androidx.appcompat.widget.AppCompatButton instead [AppCompatCustomView]
            public class MyButton extends Button implements Runnable {
                                          ~~~~~~
            1 errors, 0 warnings
            """
        lint().files(
            mTestClass,
            appCompatJar,
            manifest().minSdk(20),
            java(
                """
                package androidx.appcompat.widget;
                import android.content.Context;
                import android.util.AttributeSet;
                import android.widget.Button;
                @SuppressWarnings("AppCompatCustomView")
                public class AppCompatButton extends android.widget.Button {
                    public AppCompatButton(Context context, AttributeSet attrs, int defStyleAttr) {
                        super(context, null, null);
                    }
                }
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testKotlin() {
        lint().files(
            kotlin(
                """
                @file:Suppress("unused")

                package p1.p2

                import android.content.Context
                import android.util.AttributeSet
                import android.widget.Button

                class MyCustomView(context: Context, attrs: AttributeSet, def: Int) : Button(context, attrs, def) {
                }
                """
            ).indented(),
            appCompatJar,
            manifest().minSdk(20)
        ).run().expect(
            """
            src/p1/p2/MyCustomView.kt:9: Error: This custom view should extend android.support.v7.widget.AppCompatButton instead [AppCompatCustomView]
            class MyCustomView(context: Context, attrs: AttributeSet, def: Int) : Button(context, attrs, def) {
                                                                                  ~~~~~~
            1 errors, 0 warnings
            """
        )
    }
}
