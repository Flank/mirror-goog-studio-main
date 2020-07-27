/*
 * Copyright (C) 2018 The Android Open Source Project
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

class TranslucentViewDetectorTest : AbstractCheckTest() {
    fun testNoWarningPreO() {
        lint().files(
            manifestWithActivityThemeNoTargetSdk,
            themeFile
        ).run().expectClean()
    }

    // TODO: Test inherited themes
    // (activity uses theme where the translucency is from a parent style)

    fun testThemeInActivityManifest() {
        lint().files(
            manifestWithActivityTheme,
            themeFile
        ).run().expect(
            "res/values/styles.xml:7: Warning: Should not specify screen orientation with translucent or floating theme [TranslucentOrientation]\n" +
                "        <item name=\"android:windowIsFloating\">true</item>\n" +
                "                    ~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "    AndroidManifest.xml:9: <No location-specific message\n" +
                "0 errors, 1 warnings"
        )
    }

    fun testThemeInApplicationManifest() {
        lint().files(
            manifestWithApplicationTheme,
            themeFile
        ).run().expect(
            "res/values/styles.xml:7: Warning: Should not specify screen orientation with translucent or floating theme [TranslucentOrientation]\n" +
                "        <item name=\"android:windowIsFloating\">true</item>\n" +
                "                    ~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "    AndroidManifest.xml:10: <No location-specific message\n" +
                "0 errors, 1 warnings"
        )
    }

    // TODO(142070838): re-enable this
    fun failingTestThemeFromActivity() {
        // Like previous test, but instead of specifying theme in manifest, specifies it
        // via code in the activity
        lint().files(
            manifestWithoutTheme,
            themeFile,
            java(
                "" +
                    "package test.pkg;\n" +
                    "\n" +
                    "import android.os.Bundle;\n" +
                    "import android.app.Activity;\n" +
                    "\n" +
                    "@SuppressWarnings(\"unused\")\n" +
                    "public class MainActivity extends Activity {\n" +
                    "    @Override\n" +
                    "    protected void onCreate(Bundle savedInstanceState) {\n" +
                    "        super.onCreate(savedInstanceState);\n" +
                    "        setTheme(R.style.AppTheme);\n" +
                    "        setContentView(R.layout.activity_main);\n" +
                    "    }\n" +
                    "}\n"
            )
        ).run().expect(
            "res/values/styles.xml:7: Warning: Should not specify screen orientation with translucent or floating theme [TranslucentOrientation]\n" +
                "        <item name=\"android:windowIsFloating\">true</item>\n" +
                "                    ~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "    AndroidManifest.xml:9: <No location-specific message\n" +
                "0 errors, 1 warnings"
        )
    }

    override fun getDetector(): Detector = TranslucentViewDetector()

    private val themeFile = xml(
        "res/values/styles.xml",
        "" +
            "<resources>\n" +
            "\n" +
            "    <style name=\"AppTheme\" parent=\"Theme.AppCompat.Light.DarkActionBar\">\n" +
            "        <item name=\"colorPrimary\">@color/colorPrimary</item>\n" +
            "        <item name=\"colorPrimaryDark\">@color/colorPrimaryDark</item>\n" +
            "        <item name=\"colorAccent\">@color/colorAccent</item>\n" +
            "        <item name=\"android:windowIsFloating\">true</item>\n" +
            "        <item name=\"android:windowIsTranslucent\">true</item>\n" +
            "    </style>\n" +
            "\n" +
            "    <style name=\"SubTheme\" parent=\"AppTheme\">\n" +
            "    </style>\n" +
            "\n" +
            "</resources>\n"
    )

    private val manifestWithActivityThemeNoTargetSdk = manifest(
        "" +
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
            "    package=\"test.pkg\">\n" +
            "\n" +
            "    <application\n" +
            "        android:icon=\"@mipmap/ic_launcher\"\n" +
            "        android:label=\"@string/app_name\">\n" +
            "        <activity android:name=\".MainActivity\"\n" +
            "            android:screenOrientation=\"landscape\"\n" +
            "            android:theme=\"@style/AppTheme\" />\n" +
            "    </application>\n" +
            "</manifest>"
    )

    private val manifestWithActivityTheme = manifest(
        "" +
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
            "    package=\"test.pkg\">\n" +
            "     <uses-sdk android:minSdkVersion=\"16\" android:targetSdkVersion=\"26\" />\n" +
            "    <application\n" +
            "        android:icon=\"@mipmap/ic_launcher\"\n" +
            "        android:label=\"@string/app_name\">\n" +
            "        <activity android:name=\".MainActivity\"\n" +
            "            android:screenOrientation=\"landscape\"\n" +
            "            android:theme=\"@style/AppTheme\" />\n" +
            "    </application>\n" +
            "</manifest>"
    )

    private val manifestWithoutTheme = manifest(
        "" +
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
            "    package=\"test.pkg\">\n" +
            "     <uses-sdk android:minSdkVersion=\"16\" android:targetSdkVersion=\"26\" />\n" +
            "    <application\n" +
            "        android:icon=\"@mipmap/ic_launcher\"\n" +
            "        android:label=\"@string/app_name\">\n" +
            "        <activity android:name=\".MainActivity\"\n" +
            "            android:screenOrientation=\"landscape\" />\n" +
            "    </application>\n" +
            "</manifest>"
    )

    private val manifestWithApplicationTheme = manifest(
        "" +
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
            "    package=\"test.pkg\">\n" +
            "     <uses-sdk android:minSdkVersion=\"16\" android:targetSdkVersion=\"26\" />\n" +
            "    <application\n" +
            "        android:icon=\"@mipmap/ic_launcher\"\n" +
            "        android:label=\"@string/app_name\"\n" +
            "        android:theme=\"@style/AppTheme\">\n" +
            "        <activity android:name=\".MainActivity\"\n" +
            "            android:screenOrientation=\"landscape\" />\n" +
            "    </application>\n" +
            "</manifest>"
    )
}
