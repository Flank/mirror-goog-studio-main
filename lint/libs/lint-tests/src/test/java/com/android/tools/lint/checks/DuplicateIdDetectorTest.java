/*
 * Copyright (C) 2011 The Android Open Source Project
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

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Detector;

@SuppressWarnings("javadoc")
public class DuplicateIdDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new DuplicateIdDetector();
    }

    public void testDuplicate() throws Exception {
        //noinspection all // Sample code
        assertEquals(""
                + "res/layout/duplicate.xml:5: Error: Duplicate id @+id/android_logo, already defined earlier in this layout [DuplicateIds]\n"
                + "    <ImageButton android:id=\"@+id/android_logo\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\" android:src=\"@drawable/android_button\" android:focusable=\"false\" android:clickable=\"false\" android:layout_weight=\"1.0\" />\n"
                + "                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "    res/layout/duplicate.xml:4: Duplicate id @+id/android_logo originally defined here\n"
                + "1 errors, 0 warnings\n",
                lintFiles(xml("res/layout/duplicate.xml", ""
                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\" android:id=\"@+id/newlinear\" android:orientation=\"vertical\" android:layout_width=\"match_parent\" android:layout_height=\"match_parent\">\n"
                            + "    <Button android:text=\"Button\" android:id=\"@+id/button1\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\"></Button>\n"
                            + "    <ImageView android:id=\"@+id/android_logo\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\" android:src=\"@drawable/android_button\" android:focusable=\"false\" android:clickable=\"false\" android:layout_weight=\"1.0\" />\n"
                            + "    <ImageButton android:id=\"@+id/android_logo\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\" android:src=\"@drawable/android_button\" android:focusable=\"false\" android:clickable=\"false\" android:layout_weight=\"1.0\" />\n"
                            + "    <Button android:text=\"Button\" android:id=\"@+id/button2\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\"></Button>\n"
                            + "</LinearLayout>\n"
                            + "\n")));
    }

    public void testDuplicateChains() throws Exception {
        if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS) {
            return;
        }

        //noinspection all // Sample code
        assertEquals(""
                + "res/layout/layout1.xml:7: Warning: Duplicate id @+id/button1, defined or included multiple times in layout/layout1.xml: [layout/layout1.xml defines @+id/button1, layout/layout1.xml => layout/layout2.xml => layout/layout3.xml defines @+id/button1, layout/layout1.xml => layout/layout2.xml => layout/layout4.xml defines @+id/button1] [DuplicateIncludedIds]\n"
                + "    <include\n"
                + "    ^\n"
                + "    res/layout/layout1.xml:13: Defined here\n"
                + "    res/layout/layout3.xml:8: Defined here, included via layout/layout1.xml => layout/layout2.xml => layout/layout3.xml defines @+id/button1\n"
                + "    res/layout/layout4.xml:8: Defined here, included via layout/layout1.xml => layout/layout2.xml => layout/layout4.xml defines @+id/button1\n"
                + "res/layout/layout1.xml:7: Warning: Duplicate id @+id/button2, defined or included multiple times in layout/layout1.xml: [layout/layout1.xml defines @+id/button2, layout/layout1.xml => layout/layout2.xml => layout/layout4.xml defines @+id/button2] [DuplicateIncludedIds]\n"
                + "    <include\n"
                + "    ^\n"
                + "    res/layout/layout1.xml:19: Defined here\n"
                + "    res/layout/layout4.xml:14: Defined here, included via layout/layout1.xml => layout/layout2.xml => layout/layout4.xml defines @+id/button2\n"
                + "res/layout/layout2.xml:18: Warning: Duplicate id @+id/button1, defined or included multiple times in layout/layout2.xml: [layout/layout2.xml => layout/layout3.xml defines @+id/button1, layout/layout2.xml => layout/layout4.xml defines @+id/button1] [DuplicateIncludedIds]\n"
                + "    <include\n"
                + "    ^\n"
                + "    res/layout/layout3.xml:8: Defined here, included via layout/layout2.xml => layout/layout3.xml defines @+id/button1\n"
                + "    res/layout/layout4.xml:8: Defined here, included via layout/layout2.xml => layout/layout4.xml defines @+id/button1\n"
                + "0 errors, 3 warnings\n",

            // layout1: defines @+id/button1, button2
            // layout3: defines @+id/button1
            // layout4: defines @+id/button1, button2
            // layout1 include layout2
            // layout2 includes layout3 and layout4

            // Therefore, layout3 and layout4 have no problems
            // In layout2, there's a duplicate definition of button1 (coming from 3 and 4)
            // In layout1, there's a duplicate definition of button1 (coming from layout1, 3 and 4)
            // In layout1, there'sa duplicate definition of button2 (coming from 1 and 4)

            lintProject(xml("res/layout/layout1.xml", ""
                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "    android:layout_width=\"match_parent\"\n"
                            + "    android:layout_height=\"match_parent\"\n"
                            + "    android:orientation=\"vertical\" >\n"
                            + "\n"
                            + "    <include\n"
                            + "        android:layout_width=\"wrap_content\"\n"
                            + "        android:layout_height=\"wrap_content\"\n"
                            + "        layout=\"@layout/layout2\" />\n"
                            + "\n"
                            + "    <Button\n"
                            + "        android:id=\"@+id/button1\"\n"
                            + "        android:layout_width=\"wrap_content\"\n"
                            + "        android:layout_height=\"wrap_content\"\n"
                            + "        android:text=\"Button\" />\n"
                            + "\n"
                            + "    <Button\n"
                            + "        android:id=\"@+id/button2\"\n"
                            + "        android:layout_width=\"wrap_content\"\n"
                            + "        android:layout_height=\"wrap_content\"\n"
                            + "        android:text=\"Button\" />\n"
                            + "\n"
                            + "</LinearLayout>\n"), mLayout2,
                        mLayout3, mLayout4));
    }

    public void testSuppress() throws Exception {
        if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS) {
            return;
        }
        //noinspection all // Sample code
        assertEquals(""
                + "res/layout/layout2.xml:18: Warning: Duplicate id @+id/button1, defined or included multiple times in layout/layout2.xml: [layout/layout2.xml => layout/layout3.xml defines @+id/button1, layout/layout2.xml => layout/layout4.xml defines @+id/button1] [DuplicateIncludedIds]\n"
                + "    <include\n"
                + "    ^\n"
                + "    res/layout/layout3.xml:8: Defined here, included via layout/layout2.xml => layout/layout3.xml defines @+id/button1\n"
                + "    res/layout/layout4.xml:8: Defined here, included via layout/layout2.xml => layout/layout4.xml defines @+id/button1\n"
                + "0 errors, 1 warnings\n",

            lintProject(
                    xml("res/layout/layout1.xml", ""
                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                            + "    android:layout_width=\"match_parent\"\n"
                            + "    android:layout_height=\"match_parent\"\n"
                            + "    android:orientation=\"vertical\" >\n"
                            + "\n"
                            + "    <include\n"
                            + "        android:layout_width=\"wrap_content\"\n"
                            + "        android:layout_height=\"wrap_content\"\n"
                            + "        layout=\"@layout/layout2\"\n"
                            + "        tools:ignore=\"DuplicateIncludedIds\" />\n"
                            + "\n"
                            + "    <Button\n"
                            + "        android:id=\"@+id/button1\"\n"
                            + "        android:layout_width=\"wrap_content\"\n"
                            + "        android:layout_height=\"wrap_content\"\n"
                            + "        android:text=\"Button\" />\n"
                            + "\n"
                            + "    <Button\n"
                            + "        android:id=\"@+id/button2\"\n"
                            + "        android:layout_width=\"wrap_content\"\n"
                            + "        android:layout_height=\"wrap_content\"\n"
                            + "        android:text=\"Button\" />\n"
                            + "\n"
                            + "</LinearLayout>\n"),
                    mLayout2,
                    mLayout3,
                    mLayout4));
    }

    public void testSuppressForEmbeddedTags() throws Exception {
        //noinspection all // Sample code
        assertEquals("No warnings.",
            lintProject(
                    xml("res/layout/layout1.xml",""
                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<android.support.constraint.ConstraintLayout"
                            + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                            + "    android:layout_width=\"match_parent\"\n"
                            + "    android:layout_height=\"match_parent\"\n"
                            + "    xmlns:app=\"http://schemas.android.com/apk/res-auto\">\n"
                            + "\n"
                            + "    <Button\n"
                            + "        android:id=\"@+id/button1\"\n"
                            + "        android:text=\"A\"\n"
                            + "        android:layout_width=\"0dp\"\n"
                            + "        android:layout_height=\"wrap_content\"/>\n"
                            + "\n"
                            + "    <Button\n"
                            + "        android:id=\"@+id/button2\"\n"
                            + "        android:text=\"B\"\n"
                            + "        android:layout_width=\"0dp\"\n"
                            + "        android:layout_height=\"wrap_content\"\n"
                            + "        tools:layout_editor_absoluteY=\"70dp\"\n"
                            + "        tools:layout_editor_absoluteX=\"171dp\" />\n"
                            + "\n"
                            + "    <Button\n"
                            + "        android:id=\"@+id/button3\"\n"
                            + "        android:text=\"C\"\n"
                            + "        android:layout_width=\"0dp\"\n"
                            + "        android:layout_height=\"wrap_content\"\n"
                            + "        tools:layout_editor_absoluteY=\"145dp\"\n"
                            + "        tools:layout_editor_absoluteX=\"259dp\" />\n"
                            + "\n"
                            + "    <android.support.constraint.Chain\n"
                            + "        app:layout_constraintLeft_toLeftOf=\"@+id/guideline\"\n"
                            + "        app:layout_constraintRight_toRightOf=\"@+id/guideline2\"\n"
                            + "        android:layout_width=\"0dp\"\n"
                            + "        android:layout_height=\"0dp\">\n"
                            + "        <tag android:id=\"@+id/button1\" android:value=\"true\" />\n"
                            + "        <tag android:id=\"@+id/button2\" android:value=\"true\" />\n"
                            + "        <tag android:id=\"@+id/button3\" android:value=\"true\" />\n"
                            + "    </android.support.constraint.Chain>\n"
                            + "\n"
                            + "    <android.support.constraint.Guideline\n"
                            + "        android:layout_width=\"wrap_content\"\n"
                            + "        android:layout_height=\"wrap_content\"\n"
                            + "        android:id=\"@+id/guideline\"\n"
                            + "        app:layout_constraintGuide_begin=\"79dp\"\n"
                            + "        android:orientation=\"vertical\" />\n"
                            + "\n"
                            + "    <android.support.constraint.Guideline\n"
                            + "        android:layout_width=\"wrap_content\"\n"
                            + "        android:layout_height=\"wrap_content\"\n"
                            + "        android:id=\"@+id/guideline2\"\n"
                            + "        android:orientation=\"vertical\"\n"
                            + "        app:layout_constraintGuide_end=\"43dp\" />\n"
                            + "\n"
                            + "</android.support.constraint.ConstraintLayout>\n")));
    }

    @SuppressWarnings("all") // Sample code
    private TestFile mLayout2 = xml("res/layout/layout2.xml", ""
            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
            + "    android:layout_width=\"match_parent\"\n"
            + "    android:layout_height=\"match_parent\"\n"
            + "    android:orientation=\"vertical\" >\n"
            + "\n"
            + "    <RadioButton\n"
            + "        android:id=\"@+id/radioButton1\"\n"
            + "        android:layout_width=\"wrap_content\"\n"
            + "        android:layout_height=\"wrap_content\"\n"
            + "        android:text=\"RadioButton\" />\n"
            + "\n"
            + "    <include\n"
            + "        android:layout_width=\"wrap_content\"\n"
            + "        android:layout_height=\"wrap_content\"\n"
            + "        layout=\"@layout/layout3\" />\n"
            + "\n"
            + "    <include\n"
            + "        android:layout_width=\"wrap_content\"\n"
            + "        android:layout_height=\"wrap_content\"\n"
            + "        layout=\"@layout/layout4\" />\n"
            + "\n"
            + "</LinearLayout>\n");

    @SuppressWarnings("all") // Sample code
    private TestFile mLayout3 = xml("res/layout/layout3.xml", ""
            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
            + "    android:layout_width=\"match_parent\"\n"
            + "    android:layout_height=\"match_parent\"\n"
            + "    android:orientation=\"vertical\" >\n"
            + "\n"
            + "    <Button\n"
            + "        android:id=\"@+id/button1\"\n"
            + "        android:layout_width=\"wrap_content\"\n"
            + "        android:layout_height=\"wrap_content\"\n"
            + "        android:text=\"Button\" />\n"
            + "\n"
            + "    <CheckBox\n"
            + "        android:id=\"@+id/checkBox1\"\n"
            + "        android:layout_width=\"wrap_content\"\n"
            + "        android:layout_height=\"wrap_content\"\n"
            + "        android:text=\"CheckBox\" />\n"
            + "\n"
            + "</LinearLayout>\n");

    @SuppressWarnings("all") // Sample code
    private TestFile mLayout4 = xml("res/layout/layout4.xml", ""
            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
            + "    android:layout_width=\"match_parent\"\n"
            + "    android:layout_height=\"match_parent\"\n"
            + "    android:orientation=\"vertical\" >\n"
            + "\n"
            + "    <Button\n"
            + "        android:id=\"@+id/button1\"\n"
            + "        android:layout_width=\"wrap_content\"\n"
            + "        android:layout_height=\"wrap_content\"\n"
            + "        android:text=\"Button\" />\n"
            + "\n"
            + "    <Button\n"
            + "        android:id=\"@+id/button2\"\n"
            + "        android:layout_width=\"wrap_content\"\n"
            + "        android:layout_height=\"wrap_content\"\n"
            + "        android:text=\"Button\" />\n"
            + "\n"
            + "</LinearLayout>\n");
}
