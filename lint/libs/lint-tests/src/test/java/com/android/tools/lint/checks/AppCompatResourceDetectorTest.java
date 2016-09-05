/*
 * Copyright (C) 2014 The Android Open Source Project
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

@SuppressWarnings("SpellCheckingInspection")
public class AppCompatResourceDetectorTest extends AbstractCheckTest {
    public void testNotGradleProject() throws Exception {
        assertEquals("No warnings.",
                lintProject(mShowAction1));
    }

    public void testNoAppCompat() throws Exception {
        assertEquals(""
                + "res/menu/showAction1.xml:6: Error: Should use android:showAsAction when not using the appcompat library [AppCompatResource]\n"
                + "        app:showAsAction=\"never\" />\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n",
            lintProject(
                    mShowAction1,
                    mLibrary)); // dummy; only name counts
    }

    public void testCorrectAppCompat() throws Exception {
        assertEquals("No warnings.",
                lintProject(
                        mShowAction1,
                        mAppCompatJar,
                        mLibrary)); // dummy; only name counts
    }

    public void testWrongAppCompat() throws Exception {
        assertEquals(""
                + "res/menu/showAction2.xml:5: Error: Should use app:showAsAction with the appcompat library with xmlns:app=\"http://schemas.android.com/apk/res-auto\" [AppCompatResource]\n"
                + "        android:showAsAction=\"never\" />\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n",
        lintProject(
                mShowAction2,
                mAppCompatJar,
                mLibrary)); // dummy; only name counts
    }

    public void testAppCompatV14() throws Exception {
        assertEquals("No warnings.",
                lintProject(
                        mShowAction2_class,
                        mAppCompatJar,
                        mLibrary)); // dummy; only name counts
    }

    @Override
    protected Detector getDetector() {
        return new AppCompatResourceDetector();
    }

    // Dummy file
    private final TestFile mAppCompatJar = base64gzip("libs/appcompat-v7-18.0.0.jar", "");

    @SuppressWarnings("all") // Sample code
    private TestFile mLibrary = source("build.gradle", "");

    @SuppressWarnings("all") // Sample code
    private TestFile mShowAction1 = xml("res/menu/showAction1.xml", ""
            + "<menu xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
            + "    xmlns:app=\"http://schemas.android.com/apk/res-auto\">\n"
            + "    <item android:id=\"@+id/action_settings\"\n"
            + "        android:title=\"@string/action_settings\"\n"
            + "        android:orderInCategory=\"100\"\n"
            + "        app:showAsAction=\"never\" />\n"
            + "</menu>\n"
            + "\n");

    @SuppressWarnings("all") // Sample code
    private TestFile mShowAction2 = xml("res/menu/showAction2.xml", ""
            + "<menu xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
            + "    <item android:id=\"@+id/action_settings\"\n"
            + "        android:title=\"@string/action_settings\"\n"
            + "        android:orderInCategory=\"100\"\n"
            + "        android:showAsAction=\"never\" />\n"
            + "</menu>\n"
            + "\n");

    @SuppressWarnings("all") // Sample code
    private TestFile mShowAction2_class = xml("res/menu-v14/showAction2.xml", ""
            + "<menu xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
            + "    <item android:id=\"@+id/action_settings\"\n"
            + "        android:title=\"@string/action_settings\"\n"
            + "        android:orderInCategory=\"100\"\n"
            + "        android:showAsAction=\"never\" />\n"
            + "</menu>\n"
            + "\n");
}
