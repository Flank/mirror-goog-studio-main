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
public class DeprecationDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new DeprecationDetector();
    }

    public void testApi1() throws Exception {
        //noinspection all // Sample code
        assertEquals(
                ""
                        + "res/layout/deprecation.xml:2: Warning: AbsoluteLayout is deprecated [Deprecated]\n"
                        + "<AbsoluteLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + " ~~~~~~~~~~~~~~\n"
                        + "res/layout/deprecation.xml:18: Warning: android:editable is deprecated: Use an <EditText> to make it editable [Deprecated]\n"
                        + "        android:editable=\"true\"\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "res/layout/deprecation.xml:26: Warning: android:editable is deprecated: <EditText> is already editable [Deprecated]\n"
                        + "    <EditText android:editable=\"true\" />\n"
                        + "              ~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "res/layout/deprecation.xml:27: Warning: android:editable is deprecated: Use inputType instead [Deprecated]\n"
                        + "    <EditText android:editable=\"false\" />\n"
                        + "              ~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 4 warnings\n",
                lintProject(manifest().minSdk(1), mDeprecation));
    }

    public void testApi4() throws Exception {
        //noinspection all // Sample code
        assertEquals(
                ""
                        + "res/layout/deprecation.xml:2: Warning: AbsoluteLayout is deprecated [Deprecated]\n"
                        + "<AbsoluteLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + " ~~~~~~~~~~~~~~\n"
                        + "res/layout/deprecation.xml:16: Warning: android:autoText is deprecated: Use inputType instead [Deprecated]\n"
                        + "        android:autoText=\"true\"\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "res/layout/deprecation.xml:17: Warning: android:capitalize is deprecated: Use inputType instead [Deprecated]\n"
                        + "        android:capitalize=\"true\"\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "res/layout/deprecation.xml:18: Warning: android:editable is deprecated: Use an <EditText> to make it editable [Deprecated]\n"
                        + "        android:editable=\"true\"\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "res/layout/deprecation.xml:20: Warning: android:inputMethod is deprecated: Use inputType instead [Deprecated]\n"
                        + "        android:inputMethod=\"@+id/foo\"\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "res/layout/deprecation.xml:21: Warning: android:numeric is deprecated: Use inputType instead [Deprecated]\n"
                        + "        android:numeric=\"true\"\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "res/layout/deprecation.xml:22: Warning: android:password is deprecated: Use inputType instead [Deprecated]\n"
                        + "        android:password=\"true\"\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "res/layout/deprecation.xml:23: Warning: android:phoneNumber is deprecated: Use inputType instead [Deprecated]\n"
                        + "        android:phoneNumber=\"true\"\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "res/layout/deprecation.xml:26: Warning: android:editable is deprecated: <EditText> is already editable [Deprecated]\n"
                        + "    <EditText android:editable=\"true\" />\n"
                        + "              ~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "res/layout/deprecation.xml:27: Warning: android:editable is deprecated: Use inputType instead [Deprecated]\n"
                        + "    <EditText android:editable=\"false\" />\n"
                        + "              ~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 10 warnings\n",
                lintProject(manifest().minSdk(4), mDeprecation));
    }

    public void testUsesSdkM() throws Exception {
        assertEquals(
                ""
                        + "AndroidManifest.xml:8: Warning: uses-permission-sdk-m is deprecated: Use `uses-permission-sdk-23 instead [Deprecated]\n"
                        + "    <uses-permission-sdk-m android:name=\"foo.bar.BAZ\" />\n"
                        + "     ~~~~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 1 warnings\n",
                lintProject(
                        xml(
                                "AndroidManifest.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    package=\"test.pkg\">\n"
                                        + "\n"
                                        + "    <uses-sdk android:minSdkVersion=\"4\" />\n"
                                        + "    <uses-permission android:name=\"foo.bar.BAZ\" />\n"
                                        + "    <uses-permission-sdk-23 android:name=\"foo.bar.BAZ\" />\n"
                                        + "    <uses-permission-sdk-m android:name=\"foo.bar.BAZ\" />\n"
                                        + "\n"
                                        + "    <application\n"
                                        + "        android:icon=\"@drawable/ic_launcher\"\n"
                                        + "        android:label=\"@string/app_name\" >\n"
                                        + "        <activity\n"
                                        + "            android:name=\".BytecodeTestsActivity\"\n"
                                        + "            android:label=\"@string/app_name\" >\n"
                                        + "            <intent-filter>\n"
                                        + "                <action android:name=\"android.intent.action.MAIN\" />\n"
                                        + "\n"
                                        + "                <category android:name=\"android.intent.category.LAUNCHER\" />\n"
                                        + "            </intent-filter>\n"
                                        + "        </activity>\n"
                                        + "    </application>\n"
                                        + "\n"
                                        + "</manifest>\n")));
    }

    @SuppressWarnings("all") // Sample code
    private TestFile mDeprecation =
            xml(
                    "res/layout/deprecation.xml",
                    ""
                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<AbsoluteLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "    android:layout_width=\"match_parent\"\n"
                            + "    android:layout_height=\"match_parent\" >\n"
                            + "\n"
                            + "    <Button\n"
                            + "        android:id=\"@+id/button1\"\n"
                            + "        android:layout_width=\"wrap_content\"\n"
                            + "        android:layout_height=\"wrap_content\"\n"
                            + "        android:layout_x=\"5dp\"\n"
                            + "        android:layout_y=\"100dp\"\n"
                            + "        android:text=\"Button\" />\n"
                            + "\n"
                            + "    <!--  Deprecated attributes -->\n"
                            + "    <TextView\n"
                            + "        android:autoText=\"true\"\n"
                            + "        android:capitalize=\"true\"\n"
                            + "        android:editable=\"true\"\n"
                            + "        android:enabled=\"true\"\n"
                            + "        android:inputMethod=\"@+id/foo\"\n"
                            + "        android:numeric=\"true\"\n"
                            + "        android:password=\"true\"\n"
                            + "        android:phoneNumber=\"true\"\n"
                            + "        android:singleLine=\"true\" />\n"
                            + "\n"
                            + "    <EditText android:editable=\"true\" />\n"
                            + "    <EditText android:editable=\"false\" />\n"
                            + "\n"
                            + "</AbsoluteLayout>\n");

    public void testAndroidX() {
        lint().files(
                        xml(
                                        "res/xml/preferences.xml",
                                        "<androidx.preference.PreferenceScreen\n"
                                                + "                        xmlns:app=\"http://schemas.android.com/apk/res-auto\">\n"
                                                + "\n"
                                                + "                        <SwitchPreferenceCompat\n"
                                                + "                            app:key=\"notifications\"\n"
                                                + "                            app:title=\"Enable message notifications\"/>\n"
                                                + "\n"
                                                + "                        <Preference\n"
                                                + "                            app:key=\"feedback\"\n"
                                                + "                            app:title=\"Send feedback\"\n"
                                                + "                            app:summary=\"Report technical issues or suggest new features\"/>\n"
                                                + "\n"
                                                + "                    </androidx.preference.PreferenceScreen>")
                                .indented())
                .run()
                .expectClean();
    }

    public void testFrameworkOldProject() {
        lint().files(
                        xml(
                                        "res/xml/preferences.xml",
                                        "<android.preference.PreferenceScreen xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                                + "                    <CheckBoxPreference>\n"
                                                + "                    </CheckBoxPreference>\n"
                                                + "                </android.preference.PreferenceScreen>")
                                .indented(),
                        manifest().targetSdk(29))
                .run()
                .expect(
                        "res/xml/preferences.xml:1: Warning: The android.preference library is deprecated, it is recommended that you migrate to the AndroidX Preference library instead. [Deprecated]\n"
                                + "<android.preference.PreferenceScreen xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                + " ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                                + "0 errors, 1 warnings");
    }

    public void testFramworkNewProject() {
        lint().files(
                        xml(
                                        "res/xml/preferences.xml",
                                        "<android.preference.PreferenceScreen xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                                + "                    <CheckBoxPreference>\n"
                                                + "                    </CheckBoxPreference>\n"
                                                + "                </android.preference.PreferenceScreen>")
                                .indented(),
                        manifest().targetSdk(30))
                .run()
                .expect(
                        "res/xml/preferences.xml:1: Warning: The android.preference library is deprecated, it is recommended that you migrate to the AndroidX Preference library instead. [Deprecated]\n"
                                + "<android.preference.PreferenceScreen xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                + " ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                                + "0 errors, 1 warnings");
    }

    public void testCustomFrameworkPreference() {
        lint().files(
                        java(
                                "package android.preference;\n"
                                        + "\n"
                                        + "public class Preference {\n"
                                        + "    public CustomOldPreference() {}"
                                        + "}"),
                        java(
                                "package com.example.myapplication;\n"
                                        + "\n"
                                        + "public class CustomOldPreference extends android.preference.Preference {\n"
                                        + "    public CustomOldPreference() {\n"
                                        + "        super();\n"
                                        + "    }\n"
                                        + "}"),
                        xml(
                                        "res/xml/preferences.xml",
                                        "<com.example.myapplication.CustomOldPreference xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                                + "                    <CheckBoxPreference>\n"
                                                + "                    </CheckBoxPreference>\n"
                                                + "                </com.example.myapplication.CustomOldPreference>")
                                .indented(),
                        manifest().targetSdk(30))
                .run()
                .expect(
                        "res/xml/preferences.xml:1: Warning: com.example.myapplication.CustomOldPreference inherits from android.preference.Preference which is now deprecated, it is recommended that you migrate to the AndroidX Preference library. [Deprecated]\n"
                                + "<com.example.myapplication.CustomOldPreference xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                + " ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                                + "0 errors, 1 warnings");
    }
}
