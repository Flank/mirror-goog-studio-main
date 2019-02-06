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

package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Detector;

@SuppressWarnings("javadoc")
public class LabelForDetectorTest extends AbstractCheckTest {

    @Override
    protected Detector getDetector() {
        return new LabelForDetector();
    }

    public void testWithHint() {
        lint().files(withHint).run().expectClean();
    }

    public void testWithHintBelow17() {
        lint().files(manifest().minSdk(16), withHint).run().expectClean();
    }

    public void testWithEmptyHint() {
        String expected =
                "res/layout/labelfororhint_empty_hint.xml:11: Warning: Empty android:hint attribute [LabelFor]\n"
                        + "            android:hint=\"\"\n"
                        + "            ~~~~~~~~~~~~~~~\n"
                        + "res/layout/labelfororhint_empty_hint.xml:21: Warning: Empty android:hint attribute [LabelFor]\n"
                        + "            android:hint=\"\"\n"
                        + "            ~~~~~~~~~~~~~~~\n"
                        + "res/layout/labelfororhint_empty_hint.xml:29: Warning: Empty android:hint attribute [LabelFor]\n"
                        + "            android:hint=\"\"\n"
                        + "            ~~~~~~~~~~~~~~~\n"
                        + "0 errors, 3 warnings";

        lint().files(
                        xml(
                                "res/layout/labelfororhint_empty_hint.xml",
                                "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "              android:layout_width=\"match_parent\"\n"
                                        + "              android:layout_height=\"match_parent\"\n"
                                        + "              android:orientation=\"vertical\">\n"
                                        + "\n"
                                        + "    <EditText\n"
                                        + "            android:id=\"@+id/editText1\"\n"
                                        + "            android:layout_width=\"match_parent\"\n"
                                        + "            android:layout_height=\"wrap_content\"\n"
                                        + "            android:ems=\"10\"\n"
                                        + "            android:hint=\"\"\n"
                                        + "            android:inputType=\"textPersonName\">\n"
                                        + "        <requestFocus/>\n"
                                        + "    </EditText>\n"
                                        + "\n"
                                        + "    <AutoCompleteTextView\n"
                                        + "            android:id=\"@+id/autoCompleteTextView1\"\n"
                                        + "            android:layout_width=\"match_parent\"\n"
                                        + "            android:layout_height=\"wrap_content\"\n"
                                        + "            android:ems=\"10\"\n"
                                        + "            android:hint=\"\"\n"
                                        + "            android:text=\"AutoCompleteTextView\"/>\n"
                                        + "\n"
                                        + "    <MultiAutoCompleteTextView\n"
                                        + "            android:id=\"@+id/multiAutoCompleteTextView1\"\n"
                                        + "            android:layout_width=\"match_parent\"\n"
                                        + "            android:layout_height=\"wrap_content\"\n"
                                        + "            android:ems=\"10\"\n"
                                        + "            android:hint=\"\"\n"
                                        + "            android:text=\"MultiAutoCompleteTextView\"/>\n"
                                        + "</LinearLayout>"))
                .run()
                .expect(expected);
    }

    public void testWithLabelFor() {
        lint().files(manifest().minSdk(17), withLabelFor).run().expectClean();
    }

    public void testWithLabelForBelow17() {
        String expected =
                ""
                        + "res/layout/labelfororhint_with_labelfor.xml:14: Warning: Missing accessibility label: where minSdk < 17, you should provide an android:hint [LabelFor]\n"
                        + "    <EditText\n"
                        + "     ~~~~~~~~\n"
                        + "res/layout/labelfororhint_with_labelfor.xml:31: Warning: Missing accessibility label: where minSdk < 17, you should provide an android:hint [LabelFor]\n"
                        + "    <AutoCompleteTextView\n"
                        + "     ~~~~~~~~~~~~~~~~~~~~\n"
                        + "res/layout/labelfororhint_with_labelfor.xml:46: Warning: Missing accessibility label: where minSdk < 17, you should provide an android:hint [LabelFor]\n"
                        + "    <MultiAutoCompleteTextView\n"
                        + "     ~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 3 warnings";
        lint().files(manifest().minSdk(16), withLabelFor).run().expect(expected);
    }

    public void testWithNoHintAndNoLabelFor() {
        String expected =
                ""
                        + "res/layout/labelfororhint_no_hint_and_no_labelfor.xml:5: Warning: Missing accessibility label: provide either a view with an android:labelFor that references this view or provide an android:hint [LabelFor]\n"
                        + "    <EditText\n"
                        + "     ~~~~~~~~\n"
                        + "res/layout/labelfororhint_no_hint_and_no_labelfor.xml:14: Warning: Missing accessibility label: provide either a view with an android:labelFor that references this view or provide an android:hint [LabelFor]\n"
                        + "    <AutoCompleteTextView\n"
                        + "     ~~~~~~~~~~~~~~~~~~~~\n"
                        + "res/layout/labelfororhint_no_hint_and_no_labelfor.xml:21: Warning: Missing accessibility label: provide either a view with an android:labelFor that references this view or provide an android:hint [LabelFor]\n"
                        + "    <MultiAutoCompleteTextView\n"
                        + "     ~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 3 warnings";

        lint().files(manifest().minSdk(17), noHintNoLabelFor).run().expect(expected);
    }

    public void testWithNoHintAndNoLabelForBelow17() {
        String expected =
                ""
                        + "res/layout/labelfororhint_no_hint_and_no_labelfor.xml:5: Warning: Missing accessibility label: where minSdk < 17, you should provide an android:hint [LabelFor]\n"
                        + "    <EditText\n"
                        + "     ~~~~~~~~\n"
                        + "res/layout/labelfororhint_no_hint_and_no_labelfor.xml:14: Warning: Missing accessibility label: where minSdk < 17, you should provide an android:hint [LabelFor]\n"
                        + "    <AutoCompleteTextView\n"
                        + "     ~~~~~~~~~~~~~~~~~~~~\n"
                        + "res/layout/labelfororhint_no_hint_and_no_labelfor.xml:21: Warning: Missing accessibility label: where minSdk < 17, you should provide an android:hint [LabelFor]\n"
                        + "    <MultiAutoCompleteTextView\n"
                        + "     ~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 3 warnings";

        lint().files(manifest().minSdk(16), noHintNoLabelFor).run().expect(expected);
    }

    public void testWithHintAndLabelFor() {
        String expected =
                ""
                        + "res/layout/labelfororhint_with_hint_and_labelfor.xml:14: Warning: Missing accessibility label: provide either a view with an android:labelFor that references this view or provide an android:hint, but not both [LabelFor]\n"
                        + "    <EditText\n"
                        + "     ~~~~~~~~\n"
                        + "res/layout/labelfororhint_with_hint_and_labelfor.xml:32: Warning: Missing accessibility label: provide either a view with an android:labelFor that references this view or provide an android:hint, but not both [LabelFor]\n"
                        + "    <AutoCompleteTextView\n"
                        + "     ~~~~~~~~~~~~~~~~~~~~\n"
                        + "res/layout/labelfororhint_with_hint_and_labelfor.xml:48: Warning: Missing accessibility label: provide either a view with an android:labelFor that references this view or provide an android:hint, but not both [LabelFor]\n"
                        + "    <MultiAutoCompleteTextView\n"
                        + "     ~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 3 warnings";

        lint().files(manifest().minSdk(17), hintAndLabelFor).run().expect(expected);
    }

    public void testWithHintAndLabelForBelow17() {
        lint().files(manifest().minSdk(16), hintAndLabelFor).run().expectClean();
    }

    public void testWithLabelForNoTextNoContentDescription() {
        String expected =
                ""
                        + "res/layout/labelfororhint_no_text_no_contentdescription.xml:9: Warning: Missing accessibility label: when using android:labelFor, you must also define an android:text or an android:contentDescription [LabelFor]\n"
                        + "            android:labelFor=\"@+id/editText1\"\n"
                        + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "res/layout/labelfororhint_no_text_no_contentdescription.xml:25: Warning: Missing accessibility label: when using android:labelFor, you must also define an android:text or an android:contentDescription [LabelFor]\n"
                        + "            android:labelFor=\"@+id/autoCompleteTextView1\"\n"
                        + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "res/layout/labelfororhint_no_text_no_contentdescription.xml:39: Warning: Missing accessibility label: when using android:labelFor, you must also define an android:text or an android:contentDescription [LabelFor]\n"
                        + "            android:labelFor=\"@+id/multiAutoCompleteTextView1\"\n"
                        + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 3 warnings";
        lint().files(
                        manifest().minSdk(17),
                        xml(
                                "res/layout/labelfororhint_no_text_no_contentdescription.xml",
                                "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "              android:layout_width=\"match_parent\"\n"
                                        + "              android:layout_height=\"match_parent\"\n"
                                        + "              android:orientation=\"vertical\">\n"
                                        + "    <TextView\n"
                                        + "            android:id=\"@+id/textView1\"\n"
                                        + "            android:layout_width=\"wrap_content\"\n"
                                        + "            android:layout_height=\"wrap_content\"\n"
                                        + "            android:labelFor=\"@+id/editText1\"\n"
                                        + "            android:textAppearance=\"?android:attr/textAppearanceMedium\"/>\n"
                                        + "\n"
                                        + "    <EditText\n"
                                        + "            android:id=\"@+id/editText1\"\n"
                                        + "            android:layout_width=\"match_parent\"\n"
                                        + "            android:layout_height=\"wrap_content\"\n"
                                        + "            android:ems=\"10\"\n"
                                        + "            android:inputType=\"textPersonName\">\n"
                                        + "        <requestFocus/>\n"
                                        + "    </EditText>\n"
                                        + "\n"
                                        + "    <TextView\n"
                                        + "            android:id=\"@+id/textView2\"\n"
                                        + "            android:layout_width=\"wrap_content\"\n"
                                        + "            android:layout_height=\"wrap_content\"\n"
                                        + "            android:labelFor=\"@+id/autoCompleteTextView1\"\n"
                                        + "            android:textAppearance=\"?android:attr/textAppearanceMedium\"/>\n"
                                        + "\n"
                                        + "    <AutoCompleteTextView\n"
                                        + "            android:id=\"@id/autoCompleteTextView1\"\n"
                                        + "            android:layout_width=\"match_parent\"\n"
                                        + "            android:layout_height=\"wrap_content\"\n"
                                        + "            android:ems=\"10\"\n"
                                        + "            android:text=\"AutoCompleteTextView\"/>\n"
                                        + "\n"
                                        + "    <TextView\n"
                                        + "            android:id=\"@+id/textView3\"\n"
                                        + "            android:layout_width=\"wrap_content\"\n"
                                        + "            android:layout_height=\"wrap_content\"\n"
                                        + "            android:labelFor=\"@+id/multiAutoCompleteTextView1\"\n"
                                        + "            android:textAppearance=\"?android:attr/textAppearanceMedium\"/>\n"
                                        + "\n"
                                        + "    <MultiAutoCompleteTextView\n"
                                        + "            android:id=\"@id/multiAutoCompleteTextView1\"\n"
                                        + "            android:layout_width=\"match_parent\"\n"
                                        + "            android:layout_height=\"wrap_content\"\n"
                                        + "            android:ems=\"10\"\n"
                                        + "            android:text=\"MultiAutoCompleteTextView\"/>\n"
                                        + "</LinearLayout>"))
                .run()
                .expect(expected)
                .verifyFixes()
                .window(2)
                .expectFixDiffs(
                        ""
                                + "Fix for res/layout/labelfororhint_no_text_no_contentdescription.xml line 9: Set text:\n"
                                + "@@ -12 +12\n"
                                + "          android:layout_height=\"wrap_content\"\n"
                                + "          android:labelFor=\"@+id/editText1\"\n"
                                + "+         android:text=\"[TODO]|\"\n"
                                + "          android:textAppearance=\"?android:attr/textAppearanceMedium\" />\n"
                                + "  \n"
                                + "Fix for res/layout/labelfororhint_no_text_no_contentdescription.xml line 9: Set contentDescription:\n"
                                + "@@ -11 +11\n"
                                + "          android:layout_width=\"wrap_content\"\n"
                                + "          android:layout_height=\"wrap_content\"\n"
                                + "+         android:contentDescription=\"[TODO]|\"\n"
                                + "          android:labelFor=\"@+id/editText1\"\n"
                                + "          android:textAppearance=\"?android:attr/textAppearanceMedium\" />\n"
                                + "Fix for res/layout/labelfororhint_no_text_no_contentdescription.xml line 25: Set text:\n"
                                + "@@ -29 +29\n"
                                + "          android:layout_height=\"wrap_content\"\n"
                                + "          android:labelFor=\"@+id/autoCompleteTextView1\"\n"
                                + "+         android:text=\"[TODO]|\"\n"
                                + "          android:textAppearance=\"?android:attr/textAppearanceMedium\" />\n"
                                + "  \n"
                                + "Fix for res/layout/labelfororhint_no_text_no_contentdescription.xml line 25: Set contentDescription:\n"
                                + "@@ -28 +28\n"
                                + "          android:layout_width=\"wrap_content\"\n"
                                + "          android:layout_height=\"wrap_content\"\n"
                                + "+         android:contentDescription=\"[TODO]|\"\n"
                                + "          android:labelFor=\"@+id/autoCompleteTextView1\"\n"
                                + "          android:textAppearance=\"?android:attr/textAppearanceMedium\" />\n"
                                + "Fix for res/layout/labelfororhint_no_text_no_contentdescription.xml line 39: Set text:\n"
                                + "@@ -43 +43\n"
                                + "          android:layout_height=\"wrap_content\"\n"
                                + "          android:labelFor=\"@+id/multiAutoCompleteTextView1\"\n"
                                + "+         android:text=\"[TODO]|\"\n"
                                + "          android:textAppearance=\"?android:attr/textAppearanceMedium\" />\n"
                                + "  \n"
                                + "Fix for res/layout/labelfororhint_no_text_no_contentdescription.xml line 39: Set contentDescription:\n"
                                + "@@ -42 +42\n"
                                + "          android:layout_width=\"wrap_content\"\n"
                                + "          android:layout_height=\"wrap_content\"\n"
                                + "+         android:contentDescription=\"[TODO]|\"\n"
                                + "          android:labelFor=\"@+id/multiAutoCompleteTextView1\"\n"
                                + "          android:textAppearance=\"?android:attr/textAppearanceMedium\" />");
    }

    public void testWithLabelForEmptyText() {
        String expected =
                ""
                        + "res/layout/labelfororhint_empty_text.xml:10: Warning: Missing accessibility label: when using android:labelFor, you must also define an android:text or an android:contentDescription [LabelFor]\n"
                        + "            android:labelFor=\"@+id/editText1\"\n"
                        + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "res/layout/labelfororhint_empty_text.xml:27: Warning: Missing accessibility label: when using android:labelFor, you must also define an android:text or an android:contentDescription [LabelFor]\n"
                        + "            android:labelFor=\"@+id/autoCompleteTextView1\"\n"
                        + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "res/layout/labelfororhint_empty_text.xml:42: Warning: Missing accessibility label: when using android:labelFor, you must also define an android:text or an android:contentDescription [LabelFor]\n"
                        + "            android:labelFor=\"@+id/multiAutoCompleteTextView1\"\n"
                        + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 3 warnings";

        lint().files(
                        manifest().minSdk(17),
                        xml(
                                "res/layout/labelfororhint_empty_text.xml",
                                "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "              android:layout_width=\"match_parent\"\n"
                                        + "              android:layout_height=\"match_parent\"\n"
                                        + "              android:orientation=\"vertical\">\n"
                                        + "\n"
                                        + "    <TextView\n"
                                        + "            android:id=\"@+id/textView1\"\n"
                                        + "            android:layout_width=\"wrap_content\"\n"
                                        + "            android:layout_height=\"wrap_content\"\n"
                                        + "            android:labelFor=\"@+id/editText1\"\n"
                                        + "            android:text=\"\"\n"
                                        + "            android:textAppearance=\"?android:attr/textAppearanceMedium\"/>\n"
                                        + "\n"
                                        + "    <EditText\n"
                                        + "            android:id=\"@+id/editText1\"\n"
                                        + "            android:layout_width=\"match_parent\"\n"
                                        + "            android:layout_height=\"wrap_content\"\n"
                                        + "            android:ems=\"10\"\n"
                                        + "            android:inputType=\"textPersonName\">\n"
                                        + "        <requestFocus/>\n"
                                        + "    </EditText>\n"
                                        + "\n"
                                        + "    <TextView\n"
                                        + "            android:id=\"@+id/textView2\"\n"
                                        + "            android:layout_width=\"wrap_content\"\n"
                                        + "            android:layout_height=\"wrap_content\"\n"
                                        + "            android:labelFor=\"@+id/autoCompleteTextView1\"\n"
                                        + "            android:text=\"\"\n"
                                        + "            android:textAppearance=\"?android:attr/textAppearanceMedium\"/>\n"
                                        + "\n"
                                        + "    <AutoCompleteTextView\n"
                                        + "            android:id=\"@id/autoCompleteTextView1\"\n"
                                        + "            android:layout_width=\"match_parent\"\n"
                                        + "            android:layout_height=\"wrap_content\"\n"
                                        + "            android:ems=\"10\"\n"
                                        + "            android:text=\"AutoCompleteTextView\"/>\n"
                                        + "\n"
                                        + "    <TextView\n"
                                        + "            android:id=\"@+id/textView3\"\n"
                                        + "            android:layout_width=\"wrap_content\"\n"
                                        + "            android:layout_height=\"wrap_content\"\n"
                                        + "            android:labelFor=\"@+id/multiAutoCompleteTextView1\"\n"
                                        + "            android:text=\"\"\n"
                                        + "            android:textAppearance=\"?android:attr/textAppearanceMedium\"/>\n"
                                        + "\n"
                                        + "    <MultiAutoCompleteTextView\n"
                                        + "            android:id=\"@id/multiAutoCompleteTextView1\"\n"
                                        + "            android:layout_width=\"match_parent\"\n"
                                        + "            android:layout_height=\"wrap_content\"\n"
                                        + "            android:ems=\"10\"\n"
                                        + "            android:text=\"MultiAutoCompleteTextView\"/>\n"
                                        + "</LinearLayout>"))
                .run()
                .expect(expected);
    }

    public void testWithLabelForEmptyContentDescription() {
        String expected =
                ""
                        + "res/layout/labelfororhint_empty_contentdescription.xml:9: Warning: Missing accessibility label: when using android:labelFor, you must also define an android:text or an android:contentDescription [LabelFor]\n"
                        + "            android:labelFor=\"@+id/editText1\"\n"
                        + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "res/layout/labelfororhint_empty_contentdescription.xml:26: Warning: Missing accessibility label: when using android:labelFor, you must also define an android:text or an android:contentDescription [LabelFor]\n"
                        + "            android:labelFor=\"@+id/autoCompleteTextView1\"\n"
                        + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "res/layout/labelfororhint_empty_contentdescription.xml:41: Warning: Missing accessibility label: when using android:labelFor, you must also define an android:text or an android:contentDescription [LabelFor]\n"
                        + "            android:labelFor=\"@+id/multiAutoCompleteTextView1\"\n"
                        + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 3 warnings";

        lint().files(
                        manifest().minSdk(17),
                        xml(
                                "res/layout/labelfororhint_empty_contentdescription.xml",
                                "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "              android:layout_width=\"match_parent\"\n"
                                        + "              android:layout_height=\"match_parent\"\n"
                                        + "              android:orientation=\"vertical\">\n"
                                        + "    <TextView\n"
                                        + "            android:id=\"@+id/textView1\"\n"
                                        + "            android:layout_width=\"wrap_content\"\n"
                                        + "            android:layout_height=\"wrap_content\"\n"
                                        + "            android:labelFor=\"@+id/editText1\"\n"
                                        + "            android:contentDescription=\"\"\n"
                                        + "            android:textAppearance=\"?android:attr/textAppearanceMedium\"/>\n"
                                        + "\n"
                                        + "    <EditText\n"
                                        + "            android:id=\"@+id/editText1\"\n"
                                        + "            android:layout_width=\"match_parent\"\n"
                                        + "            android:layout_height=\"wrap_content\"\n"
                                        + "            android:ems=\"10\"\n"
                                        + "            android:inputType=\"textPersonName\">\n"
                                        + "        <requestFocus/>\n"
                                        + "    </EditText>\n"
                                        + "\n"
                                        + "    <TextView\n"
                                        + "            android:id=\"@+id/textView2\"\n"
                                        + "            android:layout_width=\"wrap_content\"\n"
                                        + "            android:layout_height=\"wrap_content\"\n"
                                        + "            android:labelFor=\"@+id/autoCompleteTextView1\"\n"
                                        + "            android:contentDescription=\"\"\n"
                                        + "            android:textAppearance=\"?android:attr/textAppearanceMedium\"/>\n"
                                        + "\n"
                                        + "    <AutoCompleteTextView\n"
                                        + "            android:id=\"@id/autoCompleteTextView1\"\n"
                                        + "            android:layout_width=\"match_parent\"\n"
                                        + "            android:layout_height=\"wrap_content\"\n"
                                        + "            android:ems=\"10\"\n"
                                        + "            android:text=\"AutoCompleteTextView\"/>\n"
                                        + "\n"
                                        + "    <TextView\n"
                                        + "            android:id=\"@+id/textView3\"\n"
                                        + "            android:layout_width=\"wrap_content\"\n"
                                        + "            android:layout_height=\"wrap_content\"\n"
                                        + "            android:labelFor=\"@+id/multiAutoCompleteTextView1\"\n"
                                        + "            android:contentDescription=\"\"\n"
                                        + "            android:textAppearance=\"?android:attr/textAppearanceMedium\"/>\n"
                                        + "\n"
                                        + "    <MultiAutoCompleteTextView\n"
                                        + "            android:id=\"@id/multiAutoCompleteTextView1\"\n"
                                        + "            android:layout_width=\"match_parent\"\n"
                                        + "            android:layout_height=\"wrap_content\"\n"
                                        + "            android:ems=\"10\"\n"
                                        + "            android:text=\"MultiAutoCompleteTextView\"/>\n"
                                        + "</LinearLayout>"))
                .run()
                .expect(expected);
    }

    public void testWithLabelForNoTextWithContentDescription() {
        lint().files(
                        manifest().minSdk(17),
                        xml(
                                "res/layout/labelfororhint_with_contentdescription.xml",
                                "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "              android:layout_width=\"match_parent\"\n"
                                        + "              android:layout_height=\"match_parent\"\n"
                                        + "              android:orientation=\"vertical\">\n"
                                        + "    <TextView\n"
                                        + "            android:id=\"@+id/textView1\"\n"
                                        + "            android:layout_width=\"wrap_content\"\n"
                                        + "            android:layout_height=\"wrap_content\"\n"
                                        + "            android:labelFor=\"@+id/editText1\"\n"
                                        + "            android:contentDescription=\"Medium Text\"\n"
                                        + "            android:textAppearance=\"?android:attr/textAppearanceMedium\"/>\n"
                                        + "\n"
                                        + "    <EditText\n"
                                        + "            android:id=\"@+id/editText1\"\n"
                                        + "            android:layout_width=\"match_parent\"\n"
                                        + "            android:layout_height=\"wrap_content\"\n"
                                        + "            android:ems=\"10\"\n"
                                        + "            android:inputType=\"textPersonName\">\n"
                                        + "        <requestFocus/>\n"
                                        + "    </EditText>\n"
                                        + "\n"
                                        + "    <TextView\n"
                                        + "            android:id=\"@+id/textView2\"\n"
                                        + "            android:layout_width=\"wrap_content\"\n"
                                        + "            android:layout_height=\"wrap_content\"\n"
                                        + "            android:labelFor=\"@+id/autoCompleteTextView1\"\n"
                                        + "            android:contentDescription=\"Medium Text\"\n"
                                        + "            android:textAppearance=\"?android:attr/textAppearanceMedium\"/>\n"
                                        + "\n"
                                        + "    <AutoCompleteTextView\n"
                                        + "            android:id=\"@id/autoCompleteTextView1\"\n"
                                        + "            android:layout_width=\"match_parent\"\n"
                                        + "            android:layout_height=\"wrap_content\"\n"
                                        + "            android:ems=\"10\"\n"
                                        + "            android:text=\"AutoCompleteTextView\"/>\n"
                                        + "\n"
                                        + "    <TextView\n"
                                        + "            android:id=\"@+id/textView3\"\n"
                                        + "            android:layout_width=\"wrap_content\"\n"
                                        + "            android:layout_height=\"wrap_content\"\n"
                                        + "            android:labelFor=\"@+id/multiAutoCompleteTextView1\"\n"
                                        + "            android:contentDescription=\"Medium Text\"\n"
                                        + "            android:textAppearance=\"?android:attr/textAppearanceMedium\"/>\n"
                                        + "\n"
                                        + "    <MultiAutoCompleteTextView\n"
                                        + "            android:id=\"@id/multiAutoCompleteTextView1\"\n"
                                        + "            android:layout_width=\"match_parent\"\n"
                                        + "            android:layout_height=\"wrap_content\"\n"
                                        + "            android:ems=\"10\"\n"
                                        + "            android:text=\"MultiAutoCompleteTextView\"/>\n"
                                        + "</LinearLayout>"))
                .run()
                .expectClean();
    }

    public void testLabelForCustomViews() {
        // Regression test for issue 78661918
        lint().files(
                        manifest().minSdk(17),
                        xml(
                                        "res/layout/main.xml",
                                        ""
                                                + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                                + "              android:layout_width=\"match_parent\"\n"
                                                + "              android:layout_height=\"match_parent\"\n"
                                                + "              android:orientation=\"vertical\">\n"
                                                + "        <com.pany.ui.widget.TypefacesTextView\n"
                                                + "                android:id=\"@+id/debug_description\"\n"
                                                + "                android:labelFor=\"@+id/debug_config\"\n"
                                                + "                android:layout_width=\"match_parent\"\n"
                                                + "                android:layout_height=\"wrap_content\"\n"
                                                + "                android:layout_marginStart=\"40dp\"/>\n"
                                                + "        <EditText android:id=\"@+id/debug_config\"\n"
                                                + "                  android:layout_height=\"60sp\"\n"
                                                + "                  android:layout_width=\"match_parent\"\n"
                                                + "                  android:layout_marginStart=\"40dp\"\n"
                                                + "                  android:inputType=\"textUri\"\n"
                                                + "                  android:enabled=\"false\"/>"
                                                + "</LinearLayout>")
                                .indented())
                .run()
                .expectClean();
    }

    //noinspection all // Sample code
    private TestFile withHint =
            xml(
                    "res/layout/labelfororhint_with_hint.xml",
                    "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "              android:layout_width=\"match_parent\"\n"
                            + "              android:layout_height=\"match_parent\"\n"
                            + "              android:orientation=\"vertical\">\n"
                            + "\n"
                            + "    <EditText\n"
                            + "            android:id=\"@+id/editText1\"\n"
                            + "            android:layout_width=\"match_parent\"\n"
                            + "            android:layout_height=\"wrap_content\"\n"
                            + "            android:ems=\"10\"\n"
                            + "            android:hint=\"hint\"\n"
                            + "            android:inputType=\"textPersonName\">\n"
                            + "        <requestFocus/>\n"
                            + "    </EditText>\n"
                            + "\n"
                            + "    <AutoCompleteTextView\n"
                            + "            android:id=\"@+id/autoCompleteTextView1\"\n"
                            + "            android:layout_width=\"match_parent\"\n"
                            + "            android:layout_height=\"wrap_content\"\n"
                            + "            android:ems=\"10\"\n"
                            + "            android:hint=\"hint\"\n"
                            + "            android:text=\"AutoCompleteTextView\"/>\n"
                            + "\n"
                            + "    <MultiAutoCompleteTextView\n"
                            + "            android:id=\"@+id/multiAutoCompleteTextView1\"\n"
                            + "            android:layout_width=\"match_parent\"\n"
                            + "            android:layout_height=\"wrap_content\"\n"
                            + "            android:ems=\"10\"\n"
                            + "            android:hint=\"hint\"\n"
                            + "            android:text=\"MultiAutoCompleteTextView\"/>\n"
                            + "</LinearLayout>");

    //noinspection all // Sample code
    private TestFile withLabelFor =
            xml(
                    "res/layout/labelfororhint_with_labelfor.xml",
                    "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "              android:layout_width=\"match_parent\"\n"
                            + "              android:layout_height=\"match_parent\"\n"
                            + "              android:orientation=\"vertical\">\n"
                            + "\n"
                            + "    <TextView\n"
                            + "            android:id=\"@+id/textView1\"\n"
                            + "            android:layout_width=\"wrap_content\"\n"
                            + "            android:layout_height=\"wrap_content\"\n"
                            + "            android:labelFor=\"@+id/editText1\"\n"
                            + "            android:text=\"Medium Text\"\n"
                            + "            android:textAppearance=\"?android:attr/textAppearanceMedium\"/>\n"
                            + "\n"
                            + "    <EditText\n"
                            + "            android:id=\"@+id/editText1\"\n"
                            + "            android:layout_width=\"match_parent\"\n"
                            + "            android:layout_height=\"wrap_content\"\n"
                            + "            android:ems=\"10\"\n"
                            + "            android:inputType=\"textPersonName\">\n"
                            + "        <requestFocus/>\n"
                            + "    </EditText>\n"
                            + "\n"
                            + "    <TextView\n"
                            + "            android:id=\"@+id/textView2\"\n"
                            + "            android:layout_width=\"wrap_content\"\n"
                            + "            android:layout_height=\"wrap_content\"\n"
                            + "            android:labelFor=\"@+id/autoCompleteTextView1\"\n"
                            + "            android:text=\"Medium Text\"\n"
                            + "            android:textAppearance=\"?android:attr/textAppearanceMedium\"/>\n"
                            + "\n"
                            + "    <AutoCompleteTextView\n"
                            + "            android:id=\"@id/autoCompleteTextView1\"\n"
                            + "            android:layout_width=\"match_parent\"\n"
                            + "            android:layout_height=\"wrap_content\"\n"
                            + "            android:ems=\"10\"\n"
                            + "            android:text=\"AutoCompleteTextView\"/>\n"
                            + "\n"
                            + "    <TextView\n"
                            + "            android:id=\"@+id/textView3\"\n"
                            + "            android:layout_width=\"wrap_content\"\n"
                            + "            android:layout_height=\"wrap_content\"\n"
                            + "            android:labelFor=\"@+id/multiAutoCompleteTextView1\"\n"
                            + "            android:text=\"Medium Text\"\n"
                            + "            android:textAppearance=\"?android:attr/textAppearanceMedium\"/>\n"
                            + "\n"
                            + "    <MultiAutoCompleteTextView\n"
                            + "            android:id=\"@id/multiAutoCompleteTextView1\"\n"
                            + "            android:layout_width=\"match_parent\"\n"
                            + "            android:layout_height=\"wrap_content\"\n"
                            + "            android:ems=\"10\"\n"
                            + "            android:text=\"MultiAutoCompleteTextView\"/>\n"
                            + "</LinearLayout>");

    //noinspection all // Sample code
    private TestFile noHintNoLabelFor =
            xml(
                    "res/layout/labelfororhint_no_hint_and_no_labelfor.xml",
                    "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "              android:layout_width=\"match_parent\"\n"
                            + "              android:layout_height=\"match_parent\"\n"
                            + "              android:orientation=\"vertical\">\n"
                            + "    <EditText\n"
                            + "            android:id=\"@+id/editText1\"\n"
                            + "            android:layout_width=\"match_parent\"\n"
                            + "            android:layout_height=\"wrap_content\"\n"
                            + "            android:ems=\"10\"\n"
                            + "            android:inputType=\"textPersonName\">\n"
                            + "        <requestFocus/>\n"
                            + "    </EditText>\n"
                            + "\n"
                            + "    <AutoCompleteTextView\n"
                            + "            android:id=\"@+id/autoCompleteTextView1\"\n"
                            + "            android:layout_width=\"match_parent\"\n"
                            + "            android:layout_height=\"wrap_content\"\n"
                            + "            android:ems=\"10\"\n"
                            + "            android:text=\"AutoCompleteTextView\"/>\n"
                            + "\n"
                            + "    <MultiAutoCompleteTextView\n"
                            + "            android:id=\"@+id/multiAutoCompleteTextView1\"\n"
                            + "            android:layout_width=\"match_parent\"\n"
                            + "            android:layout_height=\"wrap_content\"\n"
                            + "            android:ems=\"10\"\n"
                            + "            android:text=\"MultiAutoCompleteTextView\"/>\n"
                            + "</LinearLayout>");

    //noinspection all // Sample code
    private TestFile hintAndLabelFor =
            xml(
                    "res/layout/labelfororhint_with_hint_and_labelfor.xml",
                    "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "              android:layout_width=\"match_parent\"\n"
                            + "              android:layout_height=\"match_parent\"\n"
                            + "              android:orientation=\"vertical\">\n"
                            + "\n"
                            + "    <TextView\n"
                            + "            android:id=\"@+id/textView1\"\n"
                            + "            android:layout_width=\"wrap_content\"\n"
                            + "            android:layout_height=\"wrap_content\"\n"
                            + "            android:labelFor=\"@+id/editText1\"\n"
                            + "            android:text=\"Medium Text\"\n"
                            + "            android:textAppearance=\"?android:attr/textAppearanceMedium\"/>\n"
                            + "\n"
                            + "    <EditText\n"
                            + "            android:id=\"@+id/editText1\"\n"
                            + "            android:layout_width=\"match_parent\"\n"
                            + "            android:layout_height=\"wrap_content\"\n"
                            + "            android:ems=\"10\"\n"
                            + "            android:hint=\"hint\"\n"
                            + "            android:inputType=\"textPersonName\">\n"
                            + "        <requestFocus/>\n"
                            + "    </EditText>\n"
                            + "\n"
                            + "    <TextView\n"
                            + "            android:id=\"@+id/textView2\"\n"
                            + "            android:layout_width=\"wrap_content\"\n"
                            + "            android:layout_height=\"wrap_content\"\n"
                            + "            android:labelFor=\"@+id/autoCompleteTextView1\"\n"
                            + "            android:text=\"Medium Text\"\n"
                            + "            android:textAppearance=\"?android:attr/textAppearanceMedium\"/>\n"
                            + "\n"
                            + "    <AutoCompleteTextView\n"
                            + "            android:id=\"@id/autoCompleteTextView1\"\n"
                            + "            android:layout_width=\"match_parent\"\n"
                            + "            android:layout_height=\"wrap_content\"\n"
                            + "            android:ems=\"10\"\n"
                            + "            android:hint=\"hint\"\n"
                            + "            android:text=\"AutoCompleteTextView\"/>\n"
                            + "\n"
                            + "    <TextView\n"
                            + "            android:id=\"@+id/textView3\"\n"
                            + "            android:layout_width=\"wrap_content\"\n"
                            + "            android:layout_height=\"wrap_content\"\n"
                            + "            android:labelFor=\"@+id/multiAutoCompleteTextView1\"\n"
                            + "            android:text=\"Medium Text\"\n"
                            + "            android:textAppearance=\"?android:attr/textAppearanceMedium\"/>\n"
                            + "\n"
                            + "    <MultiAutoCompleteTextView\n"
                            + "            android:id=\"@id/multiAutoCompleteTextView1\"\n"
                            + "            android:layout_width=\"match_parent\"\n"
                            + "            android:layout_height=\"wrap_content\"\n"
                            + "            android:ems=\"10\"\n"
                            + "            android:hint=\"hint\"\n"
                            + "            android:text=\"MultiAutoCompleteTextView\"/>\n"
                            + "</LinearLayout>");
}
