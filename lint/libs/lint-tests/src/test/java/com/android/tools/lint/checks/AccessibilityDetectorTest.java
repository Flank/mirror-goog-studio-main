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

import com.android.tools.lint.detector.api.Detector;

@SuppressWarnings("javadoc")
public class AccessibilityDetectorTest extends AbstractCheckTest {
    public void testAccessibility() throws Exception {
        String expected = ""
                + "res/layout/accessibility.xml:4: Warning: [Accessibility] Missing contentDescription attribute on image [ContentDescription]\n"
                + "    <ImageView android:id=\"@+id/android_logo\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\" android:src=\"@drawable/android_button\" android:focusable=\"false\" android:clickable=\"false\" android:layout_weight=\"1.0\" />\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "res/layout/accessibility.xml:5: Warning: [Accessibility] Missing contentDescription attribute on image [ContentDescription]\n"
                + "    <ImageButton android:importantForAccessibility=\"yes\" android:id=\"@+id/android_logo2\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\" android:src=\"@drawable/android_button\" android:focusable=\"false\" android:clickable=\"false\" android:layout_weight=\"1.0\" />\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "res/layout/accessibility.xml:9: Warning: Do not set both contentDescription and hint: the contentDescription will mask the hint [ContentDescription]\n"
                + "    <EditText android:hint=\"@string/label\" android:id=\"@+android:id/summary\" android:contentDescription=\"@string/label\" />\n"
                + "                                                                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "res/layout/accessibility.xml:12: Warning: [Accessibility] Empty contentDescription attribute on image [ContentDescription]\n"
                + "    <ImageButton android:id=\"@+android:id/summary\" android:contentDescription=\"TODO\" />\n"
                + "                                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "res/layout/accessibility.xml:13: Warning: [Accessibility] Empty contentDescription attribute on image [ContentDescription]\n"
                + "    <ImageButton android:id=\"@+id/summary2\" android:contentDescription=\"\" />\n"
                + "                                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "res/layout/accessibility.xml:14: Warning: [Accessibility] Empty contentDescription attribute on image [ContentDescription]\n"
                + "    <ImageButton android:id=\"@+id/summary3\" android:contentDescription=\"TODO\" />\n"
                + "                                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 6 warnings\n";

        lint().files(
                xml("res/layout/accessibility.xml", ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\" xmlns:tools=\"http://schemas.android.com/tools\" android:id=\"@+id/newlinear\" android:orientation=\"vertical\" android:layout_width=\"match_parent\" android:layout_height=\"match_parent\">\n"
                        + "    <Button android:text=\"Button\" android:id=\"@+id/button1\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\"></Button>\n"
                        + "    <ImageView android:id=\"@+id/android_logo\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\" android:src=\"@drawable/android_button\" android:focusable=\"false\" android:clickable=\"false\" android:layout_weight=\"1.0\" />\n"
                        + "    <ImageButton android:importantForAccessibility=\"yes\" android:id=\"@+id/android_logo2\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\" android:src=\"@drawable/android_button\" android:focusable=\"false\" android:clickable=\"false\" android:layout_weight=\"1.0\" />\n"
                        + "    <Button android:text=\"Button\" android:id=\"@+id/button2\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\"></Button>\n"
                        + "    <Button android:id=\"@+android:id/summary\" android:contentDescription=\"@string/label\" />\n"
                        + "    <ImageButton android:importantForAccessibility=\"no\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\" android:src=\"@drawable/android_button\" android:focusable=\"false\" android:clickable=\"false\" android:layout_weight=\"1.0\" />\n"
                        + "    <EditText android:hint=\"@string/label\" android:id=\"@+android:id/summary\" android:contentDescription=\"@string/label\" />\n"
                        + "    <EditText android:id=\"@+android:id/summary\" android:contentDescription=\"@string/label\" />\n"
                        + "    <EditText tools:ignore=\"ContentDescription\" android:hint=\"@string/label\" android:id=\"@+android:id/summary\" android:contentDescription=\"@string/label\" />\n"
                        + "    <ImageButton android:id=\"@+android:id/summary\" android:contentDescription=\"TODO\" />\n"
                        + "    <ImageButton android:id=\"@+id/summary2\" android:contentDescription=\"\" />\n"
                        + "    <ImageButton android:id=\"@+id/summary3\" android:contentDescription=\"TODO\" />\n"
                        + "</LinearLayout>\n"))
                .run()
                .expect(expected);
    }

    @Override
    protected Detector getDetector() {
        return new AccessibilityDetector();
    }
}
