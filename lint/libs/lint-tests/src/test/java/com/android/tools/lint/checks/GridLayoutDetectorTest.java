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

import static com.android.tools.lint.checks.GridLayoutDetector.getNewValue;
import static com.android.tools.lint.checks.GridLayoutDetector.getOldValue;
import static com.android.tools.lint.detector.api.TextFormat.TEXT;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Severity;

@SuppressWarnings("javadoc")
public class GridLayoutDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new GridLayoutDetector();
    }

    public void testGridLayout1() throws Exception {
        //noinspection all // Sample code
        assertEquals(""
                + "res/layout/gridlayout.xml:36: Error: Column attribute (3) exceeds declared grid column count (2) [GridLayout]\n"
                + "            android:layout_column=\"3\"\n"
                + "            ~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n",
            lintFiles(xml("res/layout/gridlayout.xml", ""
                            + "<!-- Copyright (C) 2010 The Android Open Source Project\n"
                            + "\n"
                            + "     Licensed under the Apache License, Version 2.0 (the \"License\");\n"
                            + "     you may not use this file except in compliance with the License.\n"
                            + "     You may obtain a copy of the License at\n"
                            + "\n"
                            + "          http://www.apache.org/licenses/LICENSE-2.0\n"
                            + "\n"
                            + "     Unless required by applicable law or agreed to in writing, software\n"
                            + "     distributed under the License is distributed on an \"AS IS\" BASIS,\n"
                            + "     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n"
                            + "     See the License for the specific language governing permissions and\n"
                            + "     limitations under the License.\n"
                            + "-->\n"
                            + "\n"
                            + "<GridLayout\n"
                            + "        xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "        android:layout_width=\"match_parent\"\n"
                            + "        android:layout_height=\"match_parent\"\n"
                            + "        android:columnCount=\"2\" >\n"
                            + "    <Space\n"
                            + "            android:layout_row=\"0\"\n"
                            + "            android:layout_column=\"0\"\n"
                            + "            android:layout_width=\"109dip\"\n"
                            + "            android:layout_height=\"108dip\"/>\n"
                            + "\n"
                            + "    <Button\n"
                            + "            android:text=\"Button 1\"\n"
                            + "            android:layout_row=\"0\"\n"
                            + "            android:layout_column=\"1\"\n"
                            + "            />\n"
                            + "\n"
                            + "    <Button\n"
                            + "            android:text=\"Button 2\"\n"
                            + "            android:layout_row=\"1\"\n"
                            + "            android:layout_column=\"3\"\n"
                            + "            />\n"
                            + "\n"
                            + "</GridLayout>\n"
                            + "\n"
                            + "\n")));
    }

    public void testGridLayout2() throws Exception {
        //noinspection all // Sample code
        assertEquals(""
                + "res/layout/layout.xml:9: Error: Wrong namespace; with v7 GridLayout you should use myns:orientation [GridLayout]\n"
                + "        android:orientation=\"horizontal\">\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "res/layout/layout.xml:14: Error: Wrong namespace; with v7 GridLayout you should use myns:layout_row [GridLayout]\n"
                + "            android:layout_row=\"2\"\n"
                + "            ~~~~~~~~~~~~~~~~~~~~~~\n"
                + "2 errors, 0 warnings\n",
                lintFiles(xml("res/layout/layout.xml", ""
                            + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "    xmlns:myns=\"http://schemas.android.com/apk/res-auto\"\n"
                            + "    android:layout_width=\"match_parent\"\n"
                            + "    android:layout_height=\"match_parent\">\n"
                            + "\n"
                            + "    <android.support.v7.widget.GridLayout\n"
                            + "        android:layout_width=\"match_parent\"\n"
                            + "        android:layout_height=\"match_parent\"\n"
                            + "        android:orientation=\"horizontal\">\n"
                            + "        <TextView\n"
                            + "            android:text=\"@string/hello_world\"\n"
                            + "            android:layout_width=\"wrap_content\"\n"
                            + "            android:layout_height=\"wrap_content\"\n"
                            + "            android:layout_row=\"2\"\n"
                            + "            myns:layout_column=\"1\" />\n"
                            + "    </android.support.v7.widget.GridLayout>\n"
                            + "</LinearLayout>\n")));
    }

    public void testGridLayout3() throws Exception {
        //noinspection all // Sample code
        assertEquals(""
                + "res/layout/layout.xml:12: Error: Wrong namespace; with v7 GridLayout you should use app:layout_row (and add xmlns:app=\"http://schemas.android.com/apk/res-auto\" to your root element.) [GridLayout]\n"
                + "            android:layout_row=\"2\" />\n"
                + "            ~~~~~~~~~~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n",
                lintFiles(xml("res/layout/layout.xml", ""
                            + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "    android:layout_width=\"match_parent\"\n"
                            + "    android:layout_height=\"match_parent\">\n"
                            + "\n"
                            + "    <android.support.v7.widget.GridLayout\n"
                            + "        android:layout_width=\"match_parent\"\n"
                            + "        android:layout_height=\"match_parent\">\n"
                            + "        <TextView\n"
                            + "            android:text=\"@string/hello_world\"\n"
                            + "            android:layout_width=\"wrap_content\"\n"
                            + "            android:layout_height=\"wrap_content\"\n"
                            + "            android:layout_row=\"2\" />\n"
                            + "    </android.support.v7.widget.GridLayout>\n"
                            + "</LinearLayout>\n")));
    }

    public void testGridLayout4() throws Exception {
        //noinspection all // Sample code
        assertEquals(""
                + "res/layout/layout.xml:6: Error: Wrong namespace; with v7 GridLayout you should use app:orientation (and add xmlns:app=\"http://schemas.android.com/apk/res-auto\" to your root element.) [GridLayout]\n"
                + "        android:orientation=\"horizontal\">\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "res/layout/layout.xml:12: Error: Wrong namespace; with v7 GridLayout you should use app:layout_columnWeight (and add xmlns:app=\"http://schemas.android.com/apk/res-auto\" to your root element.) [GridLayout]\n"
                + "            android:layout_columnWeight=\"2\"\n"
                + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "res/layout/layout.xml:13: Error: Wrong namespace; with v7 GridLayout you should use app:layout_gravity (and add xmlns:app=\"http://schemas.android.com/apk/res-auto\" to your root element.) [GridLayout]\n"
                + "            android:layout_gravity=\"fill\"\n"
                + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "res/layout/layout.xml:20: Error: Wrong namespace; with v7 GridLayout you should use app:layout_gravity (and add xmlns:app=\"http://schemas.android.com/apk/res-auto\" to your root element.) [GridLayout]\n"
                + "            android:layout_gravity=\"fill\"\n"
                + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "res/layout/layout.xml:22: Error: Wrong namespace; with v7 GridLayout you should use app:layout_columnWeight (and add xmlns:app=\"http://schemas.android.com/apk/res-auto\" to your root element.) [GridLayout]\n"
                + "            android:layout_columnWeight=\"1\"\n"
                + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "5 errors, 0 warnings\n",
                lintFiles(xml("res/layout/layout.xml", ""
                            + "<android.support.v7.widget.GridLayout\n"
                            + "        xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "        android:layout_width=\"match_parent\"\n"
                            + "        android:layout_height=\"wrap_content\"\n"
                            + "        android:layout_margin=\"10dp\" android:background=\"@color/white\"\n"
                            + "        android:orientation=\"horizontal\">\n"
                            + "\n"
                            + "    <!-- Need maxLines, otherwise height is unbounded -->\n"
                            + "    <TextView\n"
                            + "            android:layout_width=\"0dp\"\n"
                            + "            android:background=\"@color/accent_material_light\"\n"
                            + "            android:layout_columnWeight=\"2\"\n"
                            + "            android:layout_gravity=\"fill\"\n"
                            + "            android:maxLines=\"3\"\n"
                            + "            android:text=\"column 1, weight 2\"/>\n"
                            + "\n"
                            + "    <TextView\n"
                            + "            android:layout_width=\"0dp\"\n"
                            + "            android:background=\"@color/ripple_material_light\"\n"
                            + "            android:layout_gravity=\"fill\"\n"
                            + "            android:minLines=\"3\"\n"
                            + "            android:layout_columnWeight=\"1\"\n"
                            + "            android:text=\"column 2, weight 1\"/>\n"
                            + "</android.support.v7.widget.GridLayout>\n")));
    }

    public void testGetOldValue() {
        assertEquals("android:layout_row",
                getOldValue("Wrong namespace; with v7 GridLayout you should use app:layout_row",
                        TEXT));
        assertEquals("android:layout_row",
                getOldValue("Wrong namespace; with v7 GridLayout you should use app:layout_row " +
                                "(and add xmlns:app=\"http://schemas.android.com/apk/res-auto\" to " +
                                "your root element.)",
                        TEXT));
    }

    public void testGetNewValue() {
        assertNotNull("app:layout_row",
                getNewValue("Wrong namespace; with v7 GridLayout you should use app:layout_row",
                        TEXT));
        assertNotNull("app:layout_row",
                getNewValue("Wrong namespace; with v7 GridLayout you should use app:layout_row" +
                                "(and add xmlns:app=\"http://schemas.android.com/apk/res-auto\" to " +
                                "your root element.)",
                        TEXT));
    }

    @Override
    protected void checkReportedError(@NonNull Context context, @NonNull Issue issue,
            @NonNull Severity severity, @NonNull Location location, @NonNull String message) {
        if (message.contains("with v7 GridLayout")) {
            assertNotNull(message, getOldValue(message, TEXT));
            assertNotNull(message, getNewValue(message, TEXT));
        }
    }
}
