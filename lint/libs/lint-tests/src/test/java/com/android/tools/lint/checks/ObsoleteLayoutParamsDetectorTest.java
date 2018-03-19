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
public class ObsoleteLayoutParamsDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new ObsoleteLayoutParamsDetector();
    }

    public void test() throws Exception {
        //noinspection all // Sample code
        assertEquals(
                ""
                        + "res/layout/wrongparams.xml:11: Warning: Invalid layout param in a FrameLayout: layout_weight [ObsoleteLayoutParam]\n"
                        + "        android:layout_weight=\"1\"\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "res/layout/wrongparams.xml:23: Warning: Invalid layout param in a LinearLayout: layout_alignParentLeft [ObsoleteLayoutParam]\n"
                        + "            android:layout_alignParentLeft=\"true\"\n"
                        + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "res/layout/wrongparams.xml:24: Warning: Invalid layout param in a LinearLayout: layout_alignParentTop [ObsoleteLayoutParam]\n"
                        + "            android:layout_alignParentTop=\"true\"\n"
                        + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "res/layout/wrongparams.xml:33: Warning: Invalid layout param in a LinearLayout: layout_alignBottom [ObsoleteLayoutParam]\n"
                        + "            android:layout_alignBottom=\"@+id/button1\"\n"
                        + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "res/layout/wrongparams.xml:34: Warning: Invalid layout param in a LinearLayout: layout_toRightOf [ObsoleteLayoutParam]\n"
                        + "            android:layout_toRightOf=\"@+id/button1\"\n"
                        + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "res/layout/wrongparams.xml:42: Warning: Invalid layout param in a LinearLayout: layout_alignLeft [ObsoleteLayoutParam]\n"
                        + "            android:layout_alignLeft=\"@+id/button1\"\n"
                        + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "res/layout/wrongparams.xml:43: Warning: Invalid layout param in a LinearLayout: layout_below [ObsoleteLayoutParam]\n"
                        + "            android:layout_below=\"@+id/button1\"\n"
                        + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 7 warnings\n",
                lintProject(
                        xml(
                                "res/layout/wrongparams.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<FrameLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    android:layout_width=\"match_parent\"\n"
                                        + "    android:layout_height=\"match_parent\"\n"
                                        + "    android:orientation=\"vertical\" >\n"
                                        + "\n"
                                        + "    <Button\n"
                                        + "        android:id=\"@+id/button2\"\n"
                                        + "        android:layout_width=\"wrap_content\"\n"
                                        + "        android:layout_height=\"wrap_content\"\n"
                                        + "        android:layout_weight=\"1\"\n"
                                        + "        android:text=\"Button\" />\n"
                                        + "\n"
                                        + "    <LinearLayout\n"
                                        + "        android:id=\"@+id/relativeLayout1\"\n"
                                        + "        android:layout_width=\"match_parent\"\n"
                                        + "        android:layout_height=\"wrap_content\" >\n"
                                        + "\n"
                                        + "        <Button\n"
                                        + "            android:id=\"@+id/button1\"\n"
                                        + "            android:layout_width=\"wrap_content\"\n"
                                        + "            android:layout_height=\"wrap_content\"\n"
                                        + "            android:layout_alignParentLeft=\"true\"\n"
                                        + "            android:layout_alignParentTop=\"true\"\n"
                                        + "            android:layout_marginLeft=\"17dp\"\n"
                                        + "            android:layout_marginTop=\"16dp\"\n"
                                        + "            android:text=\"Button\" />\n"
                                        + "\n"
                                        + "        <TextView\n"
                                        + "            android:id=\"@+id/textView1\"\n"
                                        + "            android:layout_width=\"wrap_content\"\n"
                                        + "            android:layout_height=\"wrap_content\"\n"
                                        + "            android:layout_alignBottom=\"@+id/button1\"\n"
                                        + "            android:layout_toRightOf=\"@+id/button1\"\n"
                                        + "            android:text=\"Medium Text\"\n"
                                        + "            android:textAppearance=\"?android:attr/textAppearanceMedium\" />\n"
                                        + "\n"
                                        + "        <RadioButton\n"
                                        + "            android:id=\"@+id/radioButton1\"\n"
                                        + "            android:layout_width=\"wrap_content\"\n"
                                        + "            android:layout_height=\"wrap_content\"\n"
                                        + "            android:layout_alignLeft=\"@+id/button1\"\n"
                                        + "            android:layout_below=\"@+id/button1\"\n"
                                        + "            android:text=\"RadioButton\" />\n"
                                        + "    </LinearLayout>\n"
                                        + "    <TableLayout\n"
                                        + "        android:id=\"@+id/tableLayout1\"\n"
                                        + "        android:layout_width=\"match_parent\"\n"
                                        + "        android:layout_height=\"match_parent\" >\n"
                                        + "\n"
                                        + "        <TableRow\n"
                                        + "            android:id=\"@+id/tableRow1\"\n"
                                        + "            android:layout_width=\"wrap_content\"\n"
                                        + "            android:layout_height=\"wrap_content\" >\n"
                                        + "\n"
                                        + "            <Button\n"
                                        + "                android:id=\"@+id/button3\"\n"
                                        + "                android:layout_column=\"0\"\n"
                                        + "                android:layout_span=\"1\"\n"
                                        + "                android:layout_width=\"wrap_content\"\n"
                                        + "                android:layout_height=\"wrap_content\"\n"
                                        + "                android:layout_weight=\"1\"\n"
                                        + "                android:text=\"Button\" />\n"
                                        + "        </TableRow>\n"
                                        + "\n"
                                        + "        <Button\n"
                                        + "            android:id=\"@+id/button4\"\n"
                                        + "            android:layout_width=\"wrap_content\"\n"
                                        + "            android:layout_height=\"wrap_content\"\n"
                                        + "            android:layout_weight=\"1\"\n"
                                        + "            android:text=\"Button\" />\n"
                                        + "    </TableLayout>\n"
                                        + "\n"
                                        + "    <GridLayout\n"
                                        + "        android:id=\"@+id/gridLayout1\"\n"
                                        + "        android:layout_width=\"match_parent\"\n"
                                        + "        android:layout_height=\"match_parent\" >\n"
                                        + "\n"
                                        + "        <Button\n"
                                        + "            android:id=\"@+id/button10\"\n"
                                        + "            android:layout_column=\"0\"\n"
                                        + "            android:layout_row=\"0\"\n"
                                        + "            android:layout_width=\"wrap_content\"\n"
                                        + "            android:layout_height=\"wrap_content\" />\n"
                                        + "    </GridLayout>\n"
                                        + "\n"
                                        + "</FrameLayout>\n"
                                        + "\n")));
    }

    public void test2() throws Exception {
        // Test <merge> and custom view handling

        //noinspection all // Sample code
        assertEquals(
                "No warnings.",
                lintProject(
                        xml(
                                "res/layout/wrongparams2.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<merge xmlns:android=\"http://schemas.android.com/apk/res/android\" >\n"
                                        + "\n"
                                        + "    <foo.bar.ActionBarHost\n"
                                        + "        android:layout_width=\"fill_parent\"\n"
                                        + "        android:layout_height=\"fill_parent\"\n"
                                        + "        android:orientation=\"vertical\" >\n"
                                        + "\n"
                                        + "        <FrameLayout\n"
                                        + "            android:layout_width=\"fill_parent\"\n"
                                        + "            android:layout_height=\"0dp\"\n"
                                        + "            android:layout_weight=\"1\" />\n"
                                        + "    </foo.bar.ActionBarHost>\n"
                                        + "\n"
                                        + "</merge>\n")));
    }

    public void test3() throws Exception {
        // Test includes across files (wrong layout param on root element)
        //noinspection all // Sample code
        assertEquals(
                ""
                        + "res/layout/wrongparams3.xml:5: Warning: Invalid layout param 'layout_alignParentTop' (included from within a LinearLayout in layout/wrongparams4.xml) [ObsoleteLayoutParam]\n"
                        + "    android:layout_alignParentTop=\"true\" >\n"
                        + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 1 warnings\n",
                lintProject(
                        xml(
                                "res/layout/wrongparams4.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    android:layout_width=\"match_parent\"\n"
                                        + "    android:layout_height=\"match_parent\"\n"
                                        + "    android:orientation=\"vertical\" >\n"
                                        + "\n"
                                        + "    <include\n"
                                        + "        android:id=\"@+id/include1\"\n"
                                        + "        android:layout_width=\"wrap_content\"\n"
                                        + "        android:layout_height=\"wrap_content\"\n"
                                        + "        layout=\"@layout/wrongparams3\" />\n"
                                        + "\n"
                                        + "</LinearLayout>\n"),
                        xml(
                                "res/layout/wrongparams3.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    android:layout_width=\"match_parent\"\n"
                                        + "    android:layout_height=\"match_parent\"\n"
                                        + "    android:layout_alignParentTop=\"true\" >\n"
                                        + "\n"
                                        + "    <Button\n"
                                        + "        android:id=\"@+id/button1\"\n"
                                        + "        android:layout_width=\"wrap_content\"\n"
                                        + "        android:layout_height=\"wrap_content\"\n"
                                        + "        android:text=\"Button\" />\n"
                                        + "\n"
                                        + "</LinearLayout>\n")));
    }

    public void test4() throws Exception {
        // Test includes with a <merge> (wrong layout param on child of root merge element)
        //noinspection all // Sample code
        assertEquals(
                ""
                        + "res/layout/wrongparams5.xml:8: Warning: Invalid layout param 'layout_alignParentTop' (included from within a LinearLayout in layout/wrongparams6.xml) [ObsoleteLayoutParam]\n"
                        + "        android:layout_alignParentTop=\"true\"\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "res/layout/wrongparams5.xml:15: Warning: Invalid layout param 'layout_alignParentLeft' (included from within a LinearLayout in layout/wrongparams6.xml) [ObsoleteLayoutParam]\n"
                        + "        android:layout_alignParentLeft=\"true\"\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "res/layout/wrongparams6.xml:16: Warning: Invalid layout param in a LinearLayout: layout_alignStart [ObsoleteLayoutParam]\n"
                        + "            android:layout_alignStart=\"@+id/include1\"\n"
                        + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "res/layout/wrongparams6.xml:17: Warning: Invalid layout param in a LinearLayout: layout_toEndOf [ObsoleteLayoutParam]\n"
                        + "            android:layout_toEndOf=\"@+id/include1\" />\n"
                        + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 4 warnings\n",
                lintProject(
                        xml(
                                "res/layout/wrongparams5.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<merge xmlns:android=\"http://schemas.android.com/apk/res/android\" >\n"
                                        + "\n"
                                        + "    <Button\n"
                                        + "        android:id=\"@+id/button1\"\n"
                                        + "        android:layout_width=\"wrap_content\"\n"
                                        + "        android:layout_height=\"wrap_content\"\n"
                                        + "        android:layout_alignParentTop=\"true\"\n"
                                        + "        android:text=\"Button\" />\n"
                                        + "\n"
                                        + "    <Button\n"
                                        + "        android:id=\"@+id/button2\"\n"
                                        + "        android:layout_width=\"wrap_content\"\n"
                                        + "        android:layout_height=\"wrap_content\"\n"
                                        + "        android:layout_alignParentLeft=\"true\"\n"
                                        + "        android:text=\"Button\" />\n"
                                        + "\n"
                                        + "</merge>\n"),
                        xml(
                                "res/layout/wrongparams6.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    android:layout_width=\"match_parent\"\n"
                                        + "    android:layout_height=\"match_parent\"\n"
                                        + "    android:orientation=\"vertical\" >\n"
                                        + "\n"
                                        + "    <include\n"
                                        + "        android:id=\"@+id/include1\"\n"
                                        + "        android:layout_width=\"wrap_content\"\n"
                                        + "        android:layout_height=\"wrap_content\"\n"
                                        + "        layout=\"@layout/wrongparams5\" />\n"
                                        + "\n"
                                        + "    <Button\n"
                                        + "            android:layout_width=\"wrap_content\"\n"
                                        + "            android:layout_height=\"wrap_content\"\n"
                                        + "            android:layout_alignStart=\"@+id/include1\"\n"
                                        + "            android:layout_toEndOf=\"@+id/include1\" />\n"
                                        + "</LinearLayout>\n")));
    }

    public void testIgnore() throws Exception {
        //noinspection all // Sample code
        assertEquals(
                ""
                        + "res/layout/wrongparams.xml:12: Warning: Invalid layout param in a FrameLayout: layout_weight [ObsoleteLayoutParam]\n"
                        + "        android:layout_weight=\"1\"\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 1 warnings\n",
                lintProject(
                        xml(
                                "res/layout/wrongparams.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<FrameLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                                        + "    android:layout_width=\"match_parent\"\n"
                                        + "    android:layout_height=\"match_parent\"\n"
                                        + "    android:orientation=\"vertical\" >\n"
                                        + "\n"
                                        + "    <Button\n"
                                        + "        android:id=\"@+id/button2\"\n"
                                        + "        android:layout_width=\"wrap_content\"\n"
                                        + "        android:layout_height=\"wrap_content\"\n"
                                        + "        android:layout_weight=\"1\"\n"
                                        + "        android:text=\"Button\" />\n"
                                        + "\n"
                                        + "    <LinearLayout\n"
                                        + "        android:id=\"@+id/relativeLayout1\"\n"
                                        + "        android:layout_width=\"match_parent\"\n"
                                        + "        android:layout_height=\"wrap_content\" >\n"
                                        + "\n"
                                        + "        <Button\n"
                                        + "            android:id=\"@+id/button1\"\n"
                                        + "            android:layout_width=\"wrap_content\"\n"
                                        + "            android:layout_height=\"wrap_content\"\n"
                                        + "            android:layout_alignParentLeft=\"true\"\n"
                                        + "            android:layout_alignParentTop=\"true\"\n"
                                        + "            android:layout_marginLeft=\"17dp\"\n"
                                        + "            android:layout_marginTop=\"16dp\"\n"
                                        + "            android:text=\"Button\" \n"
                                        + "            tools:ignore=\"all\" />\n"
                                        + "\n"
                                        + "        <TextView\n"
                                        + "            android:id=\"@+id/textView1\"\n"
                                        + "            android:layout_width=\"wrap_content\"\n"
                                        + "            android:layout_height=\"wrap_content\"\n"
                                        + "            android:layout_alignBottom=\"@+id/button1\"\n"
                                        + "            android:layout_toRightOf=\"@+id/button1\"\n"
                                        + "            android:text=\"Medium Text\"\n"
                                        + "            android:textAppearance=\"?android:attr/textAppearanceMedium\" \n"
                                        + "            tools:ignore=\"ObsoleteLayoutParam\" />\n"
                                        + "\n"
                                        + "        <RadioButton\n"
                                        + "            android:id=\"@+id/radioButton1\"\n"
                                        + "            android:layout_width=\"wrap_content\"\n"
                                        + "            android:layout_height=\"wrap_content\"\n"
                                        + "            android:layout_alignLeft=\"@+id/button1\"\n"
                                        + "            android:layout_below=\"@+id/button1\"\n"
                                        + "            android:text=\"RadioButton\" \n"
                                        + "            tools:ignore=\"ObsoleteLayoutParam\" />\n"
                                        + "    </LinearLayout>\n"
                                        + "    <TableLayout\n"
                                        + "        android:id=\"@+id/tableLayout1\"\n"
                                        + "        android:layout_width=\"match_parent\"\n"
                                        + "        android:layout_height=\"match_parent\" >\n"
                                        + "\n"
                                        + "        <TableRow\n"
                                        + "            android:id=\"@+id/tableRow1\"\n"
                                        + "            android:layout_width=\"wrap_content\"\n"
                                        + "            android:layout_height=\"wrap_content\" >\n"
                                        + "\n"
                                        + "            <Button\n"
                                        + "                android:id=\"@+id/button3\"\n"
                                        + "                android:layout_column=\"0\"\n"
                                        + "                android:layout_span=\"1\"\n"
                                        + "                android:layout_width=\"wrap_content\"\n"
                                        + "                android:layout_height=\"wrap_content\"\n"
                                        + "                android:layout_weight=\"1\"\n"
                                        + "                android:text=\"Button\" />\n"
                                        + "        </TableRow>\n"
                                        + "\n"
                                        + "        <Button\n"
                                        + "            android:id=\"@+id/button4\"\n"
                                        + "            android:layout_width=\"wrap_content\"\n"
                                        + "            android:layout_height=\"wrap_content\"\n"
                                        + "            android:layout_weight=\"1\"\n"
                                        + "            android:text=\"Button\" />\n"
                                        + "    </TableLayout>\n"
                                        + "\n"
                                        + "    <GridLayout\n"
                                        + "        android:id=\"@+id/gridLayout1\"\n"
                                        + "        android:layout_width=\"match_parent\"\n"
                                        + "        android:layout_height=\"match_parent\" >\n"
                                        + "\n"
                                        + "        <Button\n"
                                        + "            android:id=\"@+id/button10\"\n"
                                        + "            android:layout_column=\"0\"\n"
                                        + "            android:layout_row=\"0\"\n"
                                        + "            android:layout_width=\"wrap_content\"\n"
                                        + "            android:layout_height=\"wrap_content\" />\n"
                                        + "    </GridLayout>\n"
                                        + "\n"
                                        + "</FrameLayout>\n"
                                        + "\n")));
    }
}
