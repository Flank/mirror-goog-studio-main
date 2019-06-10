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
public class UseCompoundDrawableDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new UseCompoundDrawableDetector();
    }

    public void testCompound() throws Exception {
        //noinspection all // Sample code
        String expected =
                ""
                        + "res/layout/compound.xml:3: Warning: This tag and its children can be replaced by one <TextView/> and a compound drawable [UseCompoundDrawables]\n"
                        + "<LinearLayout\n"
                        + " ~~~~~~~~~~~~\n"
                        + "0 errors, 1 warnings\n";
        lint().files(
                        xml(
                                "res/layout/compound.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "\n"
                                        + "<LinearLayout\n"
                                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "\n"
                                        + "    android:layout_width=\"match_parent\"\n"
                                        + "    android:layout_height=\"match_parent\">\n"
                                        + "\n"
                                        + "    <ImageView\n"
                                        + "        android:layout_width=\"wrap_content\"\n"
                                        + "        android:layout_height=\"wrap_content\" />\n"
                                        + "\n"
                                        + "    <TextView\n"
                                        + "        android:layout_width=\"wrap_content\"\n"
                                        + "        android:layout_height=\"wrap_content\" />\n"
                                        + "\n"
                                        + "</LinearLayout>\n"))
                .run()
                .expect(expected);
    }

    public void testCompound2() throws Exception {
        // Ignore layouts that set a custom background
        lint().files(
                        xml(
                                "res/layout/compound2.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "\n"
                                        + "<LinearLayout\n"
                                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    android:background=\"@android:drawable/ic_dialog_alert\"\n"
                                        + "    android:layout_width=\"match_parent\"\n"
                                        + "    android:layout_height=\"match_parent\">\n"
                                        + "\n"
                                        + "    <ImageView\n"
                                        + "        android:layout_width=\"wrap_content\"\n"
                                        + "        android:layout_height=\"wrap_content\" />\n"
                                        + "\n"
                                        + "    <TextView\n"
                                        + "        android:layout_width=\"wrap_content\"\n"
                                        + "        android:layout_height=\"wrap_content\" />\n"
                                        + "\n"
                                        + "</LinearLayout>\n"))
                .run()
                .expectClean();
    }

    public void testCompound3() throws Exception {
        // Ignore layouts that set an image scale type
        lint().files(
                        xml(
                                "res/layout/compound3.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "\n"
                                        + "<LinearLayout\n"
                                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "\n"
                                        + "    android:layout_width=\"match_parent\"\n"
                                        + "    android:layout_height=\"match_parent\">\n"
                                        + "\n"
                                        + "    <ImageView\n"
                                        + "        android:layout_width=\"wrap_content\"\n"
                                        + "        android:layout_height=\"wrap_content\"\n"
                                        + "        android:scaleType=\"fitStart\" />\n"
                                        + "\n"
                                        + "    <TextView\n"
                                        + "        android:layout_width=\"wrap_content\"\n"
                                        + "        android:layout_height=\"wrap_content\" />\n"
                                        + "\n"
                                        + "</LinearLayout>\n"))
                .run()
                .expectClean();
    }

    public void testSkipClickable() throws Exception {
        // Regression test for issue 133864395
        //noinspection all // Sample code
        lint().files(
                        xml(
                                "res/layout/clickable.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                                        + "    android:layout_width=\"match_parent\"\n"
                                        + "    android:layout_height=\"wrap_content\">\n"
                                        + "\n"
                                        + "    <ImageView\n"
                                        + "        android:id=\"@+id/icon\"\n"
                                        + "        android:clickable=\"true\"\n"
                                        + "        android:focusable=\"true\"\n"
                                        + "        android:layout_width=\"wrap_content\"\n"
                                        + "        android:layout_height=\"wrap_content\"\n"
                                        + "        android:contentDescription=\"@string/selectButton\"/>\n"
                                        + "\n"
                                        + "    <TextView\n"
                                        + "        android:id=\"@+id/text\"\n"
                                        + "        android:layout_width=\"match_parent\"\n"
                                        + "        android:layout_height=\"wrap_content\"\n"
                                        + "        android:layout_marginStart=\"@dimen/toolbarHomeIconLeftMargin\"\n"
                                        + "        android:drawableStart=\"@drawable/ic_path_folder_24dp\"\n"
                                        + "        android:drawablePadding=\"32dp\"\n"
                                        + "        android:ellipsize=\"end\"\n"
                                        + "        android:padding=\"16dp\"\n"
                                        + "        android:singleLine=\"true\"\n"
                                        + "        android:textAppearance=\"@style/TextAppearance.AppCompat.Medium\"\n"
                                        + "        android:textColor=\"?android:attr/textColorPrimary\"\n"
                                        + "        tools:text=\"Testing\" />\n"
                                        + "</LinearLayout>"))
                .run()
                .expectClean();
    }
}
