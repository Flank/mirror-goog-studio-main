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

class LabelForDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return LabelForDetector()
    }

    fun testWithHint() {
        lint().files(withHint).run().expectClean()
    }

    fun testWithHintBelow17() {
        lint().files(manifest().minSdk(16), withHint).run().expectClean()
    }

    fun testWithEmptyHint() {
        val expected = """
            res/layout/labelfororhint_empty_hint.xml:11: Warning: Empty android:hint attribute [LabelFor]
                        android:hint=""
                        ~~~~~~~~~~~~~~~
            res/layout/labelfororhint_empty_hint.xml:21: Warning: Empty android:hint attribute [LabelFor]
                        android:hint=""
                        ~~~~~~~~~~~~~~~
            res/layout/labelfororhint_empty_hint.xml:29: Warning: Empty android:hint attribute [LabelFor]
                        android:hint=""
                        ~~~~~~~~~~~~~~~
            0 errors, 3 warnings
            """
        lint().files(
            xml(
                "res/layout/labelfororhint_empty_hint.xml",
                """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                              android:layout_width="match_parent"
                              android:layout_height="match_parent"
                              android:orientation="vertical">

                    <EditText
                            android:id="@+id/editText1"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:ems="10"
                            android:hint=""
                            android:inputType="textPersonName">
                        <requestFocus/>
                    </EditText>

                    <AutoCompleteTextView
                            android:id="@+id/autoCompleteTextView1"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:ems="10"
                            android:hint=""
                            android:text="AutoCompleteTextView"/>

                    <MultiAutoCompleteTextView
                            android:id="@+id/multiAutoCompleteTextView1"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:ems="10"
                            android:hint=""
                            android:text="MultiAutoCompleteTextView"/>
                </LinearLayout>
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testWithLabelFor() {
        lint().files(manifest().minSdk(17), withLabelFor).run().expectClean()
    }

    fun testWithLabelForBelow17() {
        val expected =
            """
            res/layout/labelfororhint_with_labelfor.xml:14: Warning: Missing accessibility label: where minSdk < 17, you should provide an android:hint [LabelFor]
                <EditText
                 ~~~~~~~~
            res/layout/labelfororhint_with_labelfor.xml:31: Warning: Missing accessibility label: where minSdk < 17, you should provide an android:hint [LabelFor]
                <AutoCompleteTextView
                 ~~~~~~~~~~~~~~~~~~~~
            res/layout/labelfororhint_with_labelfor.xml:46: Warning: Missing accessibility label: where minSdk < 17, you should provide an android:hint [LabelFor]
                <MultiAutoCompleteTextView
                 ~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 3 warnings
            """
        lint().files(manifest().minSdk(16), withLabelFor).run().expect(expected)
    }

    fun testWithNoHintAndNoLabelFor() {
        val expected =
            """
            res/layout/labelfororhint_no_hint_and_no_labelfor.xml:5: Warning: Missing accessibility label: provide either a view with an android:labelFor that references this view or provide an android:hint [LabelFor]
                <EditText
                 ~~~~~~~~
            res/layout/labelfororhint_no_hint_and_no_labelfor.xml:14: Warning: Missing accessibility label: provide either a view with an android:labelFor that references this view or provide an android:hint [LabelFor]
                <AutoCompleteTextView
                 ~~~~~~~~~~~~~~~~~~~~
            res/layout/labelfororhint_no_hint_and_no_labelfor.xml:21: Warning: Missing accessibility label: provide either a view with an android:labelFor that references this view or provide an android:hint [LabelFor]
                <MultiAutoCompleteTextView
                 ~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 3 warnings
            """
        lint().files(manifest().minSdk(17), noHintNoLabelFor).run().expect(expected)
    }

    fun testWithNoHintAndNoLabelForBelow17() {
        val expected =
            """
            res/layout/labelfororhint_no_hint_and_no_labelfor.xml:5: Warning: Missing accessibility label: where minSdk < 17, you should provide an android:hint [LabelFor]
                <EditText
                 ~~~~~~~~
            res/layout/labelfororhint_no_hint_and_no_labelfor.xml:14: Warning: Missing accessibility label: where minSdk < 17, you should provide an android:hint [LabelFor]
                <AutoCompleteTextView
                 ~~~~~~~~~~~~~~~~~~~~
            res/layout/labelfororhint_no_hint_and_no_labelfor.xml:21: Warning: Missing accessibility label: where minSdk < 17, you should provide an android:hint [LabelFor]
                <MultiAutoCompleteTextView
                 ~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 3 warnings
            """
        lint().files(manifest().minSdk(16), noHintNoLabelFor).run().expect(expected)
    }

    fun testWithHintAndLabelFor() {
        val expected =
            """
            res/layout/labelfororhint_with_hint_and_labelfor.xml:14: Warning: Missing accessibility label: provide either a view with an android:labelFor that references this view or provide an android:hint, but not both [LabelFor]
                <EditText
                 ~~~~~~~~
            res/layout/labelfororhint_with_hint_and_labelfor.xml:32: Warning: Missing accessibility label: provide either a view with an android:labelFor that references this view or provide an android:hint, but not both [LabelFor]
                <AutoCompleteTextView
                 ~~~~~~~~~~~~~~~~~~~~
            res/layout/labelfororhint_with_hint_and_labelfor.xml:48: Warning: Missing accessibility label: provide either a view with an android:labelFor that references this view or provide an android:hint, but not both [LabelFor]
                <MultiAutoCompleteTextView
                 ~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 3 warnings
            """
        lint().files(manifest().minSdk(17), hintAndLabelFor).run().expect(expected)
    }

    fun testWithHintAndLabelForBelow17() {
        lint().files(manifest().minSdk(16), hintAndLabelFor).run().expectClean()
    }

    fun testWithLabelForNoTextNoContentDescription() {
        val expected =
            """
            res/layout/labelfororhint_no_text_no_contentdescription.xml:9: Warning: Missing accessibility label: when using android:labelFor, you must also define an android:text or an android:contentDescription [LabelFor]
                        android:labelFor="@+id/editText1"
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            res/layout/labelfororhint_no_text_no_contentdescription.xml:25: Warning: Missing accessibility label: when using android:labelFor, you must also define an android:text or an android:contentDescription [LabelFor]
                        android:labelFor="@+id/autoCompleteTextView1"
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            res/layout/labelfororhint_no_text_no_contentdescription.xml:39: Warning: Missing accessibility label: when using android:labelFor, you must also define an android:text or an android:contentDescription [LabelFor]
                        android:labelFor="@+id/multiAutoCompleteTextView1"
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 3 warnings
            """
        lint().files(
            manifest().minSdk(17),
            xml(
                "res/layout/labelfororhint_no_text_no_contentdescription.xml",
                """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                              android:layout_width="match_parent"
                              android:layout_height="match_parent"
                              android:orientation="vertical">
                    <TextView
                            android:id="@+id/textView1"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:labelFor="@+id/editText1"
                            android:textAppearance="?android:attr/textAppearanceMedium"/>

                    <EditText
                            android:id="@+id/editText1"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:ems="10"
                            android:inputType="textPersonName">
                        <requestFocus/>
                    </EditText>

                    <TextView
                            android:id="@+id/textView2"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:labelFor="@+id/autoCompleteTextView1"
                            android:textAppearance="?android:attr/textAppearanceMedium"/>

                    <AutoCompleteTextView
                            android:id="@id/autoCompleteTextView1"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:ems="10"
                            android:text="AutoCompleteTextView"/>

                    <TextView
                            android:id="@+id/textView3"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:labelFor="@+id/multiAutoCompleteTextView1"
                            android:textAppearance="?android:attr/textAppearanceMedium"/>

                    <MultiAutoCompleteTextView
                            android:id="@id/multiAutoCompleteTextView1"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:ems="10"
                            android:text="MultiAutoCompleteTextView"/>
                </LinearLayout>
                """
            ).indented()
        )
            .run()
            .expect(expected)
            .verifyFixes()
            .window(2)
            .expectFixDiffs(
                """
                Fix for res/layout/labelfororhint_no_text_no_contentdescription.xml line 9: Set text:
                @@ -12 +12
                          android:layout_height="wrap_content"
                          android:labelFor="@+id/editText1"
                +         android:text="[TODO]|"
                          android:textAppearance="?android:attr/textAppearanceMedium" />

                Fix for res/layout/labelfororhint_no_text_no_contentdescription.xml line 9: Set contentDescription:
                @@ -11 +11
                          android:layout_width="wrap_content"
                          android:layout_height="wrap_content"
                +         android:contentDescription="[TODO]|"
                          android:labelFor="@+id/editText1"
                          android:textAppearance="?android:attr/textAppearanceMedium" />
                Fix for res/layout/labelfororhint_no_text_no_contentdescription.xml line 25: Set text:
                @@ -29 +29
                          android:layout_height="wrap_content"
                          android:labelFor="@+id/autoCompleteTextView1"
                +         android:text="[TODO]|"
                          android:textAppearance="?android:attr/textAppearanceMedium" />

                Fix for res/layout/labelfororhint_no_text_no_contentdescription.xml line 25: Set contentDescription:
                @@ -28 +28
                          android:layout_width="wrap_content"
                          android:layout_height="wrap_content"
                +         android:contentDescription="[TODO]|"
                          android:labelFor="@+id/autoCompleteTextView1"
                          android:textAppearance="?android:attr/textAppearanceMedium" />
                Fix for res/layout/labelfororhint_no_text_no_contentdescription.xml line 39: Set text:
                @@ -43 +43
                          android:layout_height="wrap_content"
                          android:labelFor="@+id/multiAutoCompleteTextView1"
                +         android:text="[TODO]|"
                          android:textAppearance="?android:attr/textAppearanceMedium" />

                Fix for res/layout/labelfororhint_no_text_no_contentdescription.xml line 39: Set contentDescription:
                @@ -42 +42
                          android:layout_width="wrap_content"
                          android:layout_height="wrap_content"
                +         android:contentDescription="[TODO]|"
                          android:labelFor="@+id/multiAutoCompleteTextView1"
                          android:textAppearance="?android:attr/textAppearanceMedium" />
                """
            )
    }

    fun testWithLabelForEmptyText() {
        val expected =
            """
            res/layout/labelfororhint_empty_text.xml:10: Warning: Missing accessibility label: when using android:labelFor, you must also define an android:text or an android:contentDescription [LabelFor]
                        android:labelFor="@+id/editText1"
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            res/layout/labelfororhint_empty_text.xml:27: Warning: Missing accessibility label: when using android:labelFor, you must also define an android:text or an android:contentDescription [LabelFor]
                        android:labelFor="@+id/autoCompleteTextView1"
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            res/layout/labelfororhint_empty_text.xml:42: Warning: Missing accessibility label: when using android:labelFor, you must also define an android:text or an android:contentDescription [LabelFor]
                        android:labelFor="@+id/multiAutoCompleteTextView1"
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 3 warnings
            """
        lint().files(
            manifest().minSdk(17),
            xml(
                "res/layout/labelfororhint_empty_text.xml",
                """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                              android:layout_width="match_parent"
                              android:layout_height="match_parent"
                              android:orientation="vertical">

                    <TextView
                            android:id="@+id/textView1"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:labelFor="@+id/editText1"
                            android:text=""
                            android:textAppearance="?android:attr/textAppearanceMedium"/>

                    <EditText
                            android:id="@+id/editText1"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:ems="10"
                            android:inputType="textPersonName">
                        <requestFocus/>
                    </EditText>

                    <TextView
                            android:id="@+id/textView2"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:labelFor="@+id/autoCompleteTextView1"
                            android:text=""
                            android:textAppearance="?android:attr/textAppearanceMedium"/>

                    <AutoCompleteTextView
                            android:id="@id/autoCompleteTextView1"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:ems="10"
                            android:text="AutoCompleteTextView"/>

                    <TextView
                            android:id="@+id/textView3"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:labelFor="@+id/multiAutoCompleteTextView1"
                            android:text=""
                            android:textAppearance="?android:attr/textAppearanceMedium"/>

                    <MultiAutoCompleteTextView
                            android:id="@id/multiAutoCompleteTextView1"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:ems="10"
                            android:text="MultiAutoCompleteTextView"/>
                </LinearLayout>
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testWithLabelForEmptyContentDescription() {
        val expected =
            """
            res/layout/labelfororhint_empty_contentdescription.xml:9: Warning: Missing accessibility label: when using android:labelFor, you must also define an android:text or an android:contentDescription [LabelFor]
                        android:labelFor="@+id/editText1"
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            res/layout/labelfororhint_empty_contentdescription.xml:26: Warning: Missing accessibility label: when using android:labelFor, you must also define an android:text or an android:contentDescription [LabelFor]
                        android:labelFor="@+id/autoCompleteTextView1"
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            res/layout/labelfororhint_empty_contentdescription.xml:41: Warning: Missing accessibility label: when using android:labelFor, you must also define an android:text or an android:contentDescription [LabelFor]
                        android:labelFor="@+id/multiAutoCompleteTextView1"
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 3 warnings
            """
        lint().files(
            manifest().minSdk(17),
            xml(
                "res/layout/labelfororhint_empty_contentdescription.xml",
                """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                              android:layout_width="match_parent"
                              android:layout_height="match_parent"
                              android:orientation="vertical">
                    <TextView
                            android:id="@+id/textView1"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:labelFor="@+id/editText1"
                            android:contentDescription=""
                            android:textAppearance="?android:attr/textAppearanceMedium"/>

                    <EditText
                            android:id="@+id/editText1"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:ems="10"
                            android:inputType="textPersonName">
                        <requestFocus/>
                    </EditText>

                    <TextView
                            android:id="@+id/textView2"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:labelFor="@+id/autoCompleteTextView1"
                            android:contentDescription=""
                            android:textAppearance="?android:attr/textAppearanceMedium"/>

                    <AutoCompleteTextView
                            android:id="@id/autoCompleteTextView1"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:ems="10"
                            android:text="AutoCompleteTextView"/>

                    <TextView
                            android:id="@+id/textView3"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:labelFor="@+id/multiAutoCompleteTextView1"
                            android:contentDescription=""
                            android:textAppearance="?android:attr/textAppearanceMedium"/>

                    <MultiAutoCompleteTextView
                            android:id="@id/multiAutoCompleteTextView1"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:ems="10"
                            android:text="MultiAutoCompleteTextView"/>
                </LinearLayout>
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testWithLabelForNoTextWithContentDescription() {
        lint().files(
            manifest().minSdk(17),
            xml(
                "res/layout/labelfororhint_with_contentdescription.xml",
                """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                              android:layout_width="match_parent"
                              android:layout_height="match_parent"
                              android:orientation="vertical">
                    <TextView
                            android:id="@+id/textView1"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:labelFor="@+id/editText1"
                            android:contentDescription="Medium Text"
                            android:textAppearance="?android:attr/textAppearanceMedium"/>

                    <EditText
                            android:id="@+id/editText1"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:ems="10"
                            android:inputType="textPersonName">
                        <requestFocus/>
                    </EditText>

                    <TextView
                            android:id="@+id/textView2"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:labelFor="@+id/autoCompleteTextView1"
                            android:contentDescription="Medium Text"
                            android:textAppearance="?android:attr/textAppearanceMedium"/>

                    <AutoCompleteTextView
                            android:id="@id/autoCompleteTextView1"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:ems="10"
                            android:text="AutoCompleteTextView"/>

                    <TextView
                            android:id="@+id/textView3"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:labelFor="@+id/multiAutoCompleteTextView1"
                            android:contentDescription="Medium Text"
                            android:textAppearance="?android:attr/textAppearanceMedium"/>

                    <MultiAutoCompleteTextView
                            android:id="@id/multiAutoCompleteTextView1"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:ems="10"
                            android:text="MultiAutoCompleteTextView"/>
                </LinearLayout>
                """
            ).indented()
        ).run().expectClean()
    }

    fun testLabelForCustomViews() {
        // Regression test for issue 78661918
        lint().files(
            manifest().minSdk(17),
            xml(
                "res/layout/main.xml",
                """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                              android:layout_width="match_parent"
                              android:layout_height="match_parent"
                              android:orientation="vertical">
                        <com.pany.ui.widget.TypefacesTextView
                                android:id="@+id/debug_description"
                                android:labelFor="@+id/debug_config"
                                android:layout_width="match_parent"
                                android:layout_height="wrap_content"
                                android:layout_marginStart="40dp"/>
                        <EditText android:id="@+id/debug_config"
                                  android:layout_height="60sp"
                                  android:layout_width="match_parent"
                                  android:layout_marginStart="40dp"
                                  android:inputType="textUri"
                                  android:enabled="false"/></LinearLayout>
                """
            ).indented()
        ).run().expectClean()
    }

    fun test183705436() {
        val expected = """
            res/layout/file2.xml:9: Warning: Missing accessibility label: when using android:labelFor, you must also define an android:text or an android:contentDescription [LabelFor]
                        android:labelFor="@+id/editText1"
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            res/layout/file2.xml:12: Warning: Missing accessibility label: where minSdk < 17, you should provide an android:hint [LabelFor]
                <EditText
                 ~~~~~~~~
            0 errors, 2 warnings
            """
        lint().files(
            xml(
                "res/layout/file1.xml",
                """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                              android:layout_width="match_parent"
                              android:layout_height="match_parent"
                              android:orientation="vertical">

                    <EditText
                            android:id="@+id/editText1"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:ems="10"
                            android:hint="My hint"/>
                </LinearLayout>
                """
            ).indented(),
            xml(
                "res/layout/file2.xml",
                """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                              android:layout_width="match_parent"
                              android:layout_height="match_parent"
                              android:orientation="vertical">
                    <TextView
                            android:id="@+id/textView1"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:labelFor="@+id/editText1"
                            android:textAppearance="?android:attr/textAppearanceMedium"/>

                    <EditText
                            android:id="@+id/editText1"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:ems="10"
                            android:inputType="textPersonName">
                        <requestFocus/>
                    </EditText>
                </LinearLayout>
                """
            ).indented()
        ).run().expect(expected)
    }

    private val withHint = xml(
        "res/layout/labelfororhint_with_hint.xml",
        """
        <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                      android:layout_width="match_parent"
                      android:layout_height="match_parent"
                      android:orientation="vertical">

            <EditText
                    android:id="@+id/editText1"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:ems="10"
                    android:hint="hint"
                    android:inputType="textPersonName">
                <requestFocus/>
            </EditText>

            <AutoCompleteTextView
                    android:id="@+id/autoCompleteTextView1"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:ems="10"
                    android:hint="hint"
                    android:text="AutoCompleteTextView"/>

            <MultiAutoCompleteTextView
                    android:id="@+id/multiAutoCompleteTextView1"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:ems="10"
                    android:hint="hint"
                    android:text="MultiAutoCompleteTextView"/>
        </LinearLayout>
        """
    ).indented()

    private val withLabelFor = xml(
        "res/layout/labelfororhint_with_labelfor.xml",
        """
        <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                      android:layout_width="match_parent"
                      android:layout_height="match_parent"
                      android:orientation="vertical">

            <TextView
                    android:id="@+id/textView1"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:labelFor="@+id/editText1"
                    android:text="Medium Text"
                    android:textAppearance="?android:attr/textAppearanceMedium"/>

            <EditText
                    android:id="@+id/editText1"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:ems="10"
                    android:inputType="textPersonName">
                <requestFocus/>
            </EditText>

            <TextView
                    android:id="@+id/textView2"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:labelFor="@+id/autoCompleteTextView1"
                    android:text="Medium Text"
                    android:textAppearance="?android:attr/textAppearanceMedium"/>

            <AutoCompleteTextView
                    android:id="@id/autoCompleteTextView1"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:ems="10"
                    android:text="AutoCompleteTextView"/>

            <TextView
                    android:id="@+id/textView3"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:labelFor="@+id/multiAutoCompleteTextView1"
                    android:text="Medium Text"
                    android:textAppearance="?android:attr/textAppearanceMedium"/>

            <MultiAutoCompleteTextView
                    android:id="@id/multiAutoCompleteTextView1"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:ems="10"
                    android:text="MultiAutoCompleteTextView"/>
        </LinearLayout>
        """
    ).indented()

    private val noHintNoLabelFor = xml(
        "res/layout/labelfororhint_no_hint_and_no_labelfor.xml",
        """
        <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                      android:layout_width="match_parent"
                      android:layout_height="match_parent"
                      android:orientation="vertical">
            <EditText
                    android:id="@+id/editText1"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:ems="10"
                    android:inputType="textPersonName">
                <requestFocus/>
            </EditText>

            <AutoCompleteTextView
                    android:id="@+id/autoCompleteTextView1"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:ems="10"
                    android:text="AutoCompleteTextView"/>

            <MultiAutoCompleteTextView
                    android:id="@+id/multiAutoCompleteTextView1"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:ems="10"
                    android:text="MultiAutoCompleteTextView"/>
        </LinearLayout>
        """
    ).indented()

    private val hintAndLabelFor = xml(
        "res/layout/labelfororhint_with_hint_and_labelfor.xml",
        """
        <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                      android:layout_width="match_parent"
                      android:layout_height="match_parent"
                      android:orientation="vertical">

            <TextView
                    android:id="@+id/textView1"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:labelFor="@+id/editText1"
                    android:text="Medium Text"
                    android:textAppearance="?android:attr/textAppearanceMedium"/>

            <EditText
                    android:id="@+id/editText1"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:ems="10"
                    android:hint="hint"
                    android:inputType="textPersonName">
                <requestFocus/>
            </EditText>

            <TextView
                    android:id="@+id/textView2"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:labelFor="@+id/autoCompleteTextView1"
                    android:text="Medium Text"
                    android:textAppearance="?android:attr/textAppearanceMedium"/>

            <AutoCompleteTextView
                    android:id="@id/autoCompleteTextView1"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:ems="10"
                    android:hint="hint"
                    android:text="AutoCompleteTextView"/>

            <TextView
                    android:id="@+id/textView3"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:labelFor="@+id/multiAutoCompleteTextView1"
                    android:text="Medium Text"
                    android:textAppearance="?android:attr/textAppearanceMedium"/>

            <MultiAutoCompleteTextView
                    android:id="@id/multiAutoCompleteTextView1"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:ems="10"
                    android:hint="hint"
                    android:text="MultiAutoCompleteTextView"/>
        </LinearLayout>
        """
    ).indented()
}
