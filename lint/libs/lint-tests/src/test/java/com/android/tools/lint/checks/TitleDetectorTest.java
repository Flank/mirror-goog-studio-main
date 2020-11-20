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
public class TitleDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new TitleDetector();
    }

    public void testBasic() {
        String expected =
                ""
                        + "res/menu/titles.xml:3: Error: Menu items should specify a title [MenuTitle]\n"
                        + "    <item android:id=\"@+id/action_bar_progress_spinner\"\n"
                        + "     ~~~~\n"
                        + "res/menu/titles.xml:12: Error: Menu items should specify a title [MenuTitle]\n"
                        + "    <item android:id=\"@+id/menu_plus_one\"\n"
                        + "     ~~~~\n"
                        + "2 errors, 0 warnings";
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(14),
                        xml(
                                "res/menu/titles.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<menu xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                        + "    <item android:id=\"@+id/action_bar_progress_spinner\"\n"
                                        + "        android:showAsAction=\"always\"\n"
                                        + "        android:background=\"@null\"\n"
                                        + "        android:selectableItemBackground=\"@null\"\n"
                                        + "        android:actionLayout=\"@layout/action_bar_progress_spinner_layout\"/>\n"
                                        + "    <item android:id=\"@+id/refresh\"\n"
                                        + "        android:title=\"@string/menu_refresh\"\n"
                                        + "        android:showAsAction=\"always\"\n"
                                        + "        android:icon=\"@drawable/ic_menu_refresh\"/>\n"
                                        + "    <item android:id=\"@+id/menu_plus_one\"\n"
                                        + "        android:showAsAction=\"always\"\n"
                                        + "        android:icon=\"@drawable/ic_menu_plus1\"/>\n"
                                        + "</menu>\n"))
                .run()
                .expect(expected)
                .verifyFixes()
                .window(1)
                .expectFixDiffs(
                        ""
                                + "Fix for res/menu/titles.xml line 3: Set title:\n"
                                + "@@ -9 +9\n"
                                + "          android:selectableItemBackground=\"@null\"\n"
                                + "-         android:showAsAction=\"always\"/>\n"
                                + "+         android:showAsAction=\"always\"\n"
                                + "+         android:title=\"[TODO]|\"/>\n"
                                + "      <item\n"
                                + "Fix for res/menu/titles.xml line 12: Set title:\n"
                                + "@@ -18 +18\n"
                                + "          android:icon=\"@drawable/ic_menu_plus1\"\n"
                                + "-         android:showAsAction=\"always\"/>\n"
                                + "+         android:showAsAction=\"always\"\n"
                                + "+         android:title=\"[TODO]|\"/>\n"
                                + "  \n");
    }
}
