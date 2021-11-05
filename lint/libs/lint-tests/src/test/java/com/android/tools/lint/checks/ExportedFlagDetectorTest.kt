/*
 * Copyright (C) 2020 The Android Open Source Project
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

class ExportedFlagDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return ExportedFlagDetector()
    }

    fun testNoExportReceiver() {
        lint().files(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg">
                    <uses-sdk android:minSdkVersion="30"/>
                    <application
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name" >
                        <receiver android:name="com.google.android.c2dm.C2DMBroadcastReceiver">
                          <intent-filter>
                            <action android:name="com.google.android.c2dm.intent.RECEIVE"/>
                            <action android:name="com.google.android.c2dm.intent.REGISTRATION"/>
                        </intent-filter>
                        </receiver>
                    </application>
                </manifest>
                """
            ).indented()
        ).run().expect(
            """
            AndroidManifest.xml:7: Warning: As of Android 12, android:exported must be set; use true to make the activity available to other apps, and false otherwise. [IntentFilterExportedReceiver]
                    <receiver android:name="com.google.android.c2dm.C2DMBroadcastReceiver">
                     ~~~~~~~~
            0 errors, 1 warnings
            """
        ).expectFixDiffs(
            """
            Fix for AndroidManifest.xml line 7: Set exported="true":
            @@ -10 +10
            -         <receiver android:name="com.google.android.c2dm.C2DMBroadcastReceiver" >
            +         <receiver
            +             android:name="com.google.android.c2dm.C2DMBroadcastReceiver"
            +             android:exported="true" >
            Fix for AndroidManifest.xml line 7: Set exported="false":
            @@ -10 +10
            -         <receiver android:name="com.google.android.c2dm.C2DMBroadcastReceiver" >
            +         <receiver
            +             android:name="com.google.android.c2dm.C2DMBroadcastReceiver"
            +             android:exported="false" >
            """
        )
    }

    fun testNoExportActivityPreS() {
        lint().files(
            xml(
                "AndroidManifest.xml",
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg">
                    <uses-sdk android:minSdkVersion="30"/>
                    <application
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name" >
                        <activity android:name="com.example.MainActivity">
                          <intent-filter>
                            <action android:name="com.google.android.c2dm.intent.RECEIVE"/>
                            <action android:name="com.google.android.c2dm.intent.REGISTRATION"/>
                        </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """
            ).indented()
        ).run().expect(
            """
            AndroidManifest.xml:7: Warning: As of Android 12, android:exported must be set; use true to make the activity available to other apps, and false otherwise. [IntentFilterExportedReceiver]
                    <activity android:name="com.example.MainActivity">
                     ~~~~~~~~
            0 errors, 1 warnings
            """
        ).expectFixDiffs(
            """
            Fix for AndroidManifest.xml line 7: Set exported="true":
            @@ -10 +10
            -         <activity android:name="com.example.MainActivity" >
            +         <activity
            +             android:name="com.example.MainActivity"
            +             android:exported="true" >
            Fix for AndroidManifest.xml line 7: Set exported="false":
            @@ -10 +10
            -         <activity android:name="com.example.MainActivity" >
            +         <activity
            +             android:name="com.example.MainActivity"
            +             android:exported="false" >
            """
        )
    }

    fun testNoExportActivityPostS() {
        lint().files(
            xml(
                "AndroidManifest.xml",
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg">

                    <uses-sdk android:minSdkVersion="31"/>
                    <application
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name" >
                        <activity android:name="com.example.MainActivity">
                          <intent-filter>
                            <action android:name="com.google.android.c2dm.intent.RECEIVE"/>
                            <action android:name="com.google.android.c2dm.intent.REGISTRATION"/>
                        </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """
            ).indented()
        ).run().expect(
            """
            AndroidManifest.xml:8: Error: As of Android 12, android:exported must be set; use true to make the activity available to other apps, and false otherwise. [IntentFilterExportedReceiver]
                    <activity android:name="com.example.MainActivity">
                     ~~~~~~~~
            1 errors, 0 warnings
            """
        ).expectFixDiffs(
            """
            Fix for AndroidManifest.xml line 8: Set exported="true":
            @@ -10 +10
            -         <activity android:name="com.example.MainActivity" >
            +         <activity
            +             android:name="com.example.MainActivity"
            +             android:exported="true" >
            Fix for AndroidManifest.xml line 8: Set exported="false":
            @@ -10 +10
            -         <activity android:name="com.example.MainActivity" >
            +         <activity
            +             android:name="com.example.MainActivity"
            +             android:exported="false" >
            """
        )
    }

    fun testExport() {
        lint().files(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg">

                    <application
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name" >
                        <receiver android:name="com.google.android.c2dm.C2DMBroadcastReceiver"
                                  android:exported="true">
                            <intent-filter>
                                <action android:name="com.google.android.c2dm.intent.RECEIVE"/>
                                <action android:name="com.google.android.c2dm.intent.REGISTRATION"/>
                            </intent-filter>
                        </receiver>
                    </application>
                </manifest>
                """
            ).indented()
        ).run().expectClean()
    }

    fun testNonExportedActivityPostS() {
        lint().files(
            xml(
                "AndroidManifest.xml",
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg">

                    <uses-sdk android:minSdkVersion="31"/>
                    <application
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name" >
                        <activity
                            android:name="MyActivity">
                            <nav-graph android:value="@navigation/navigation_root" />
                        </activity>
                    </application>
                </manifest>
                """
            ).indented()
        ).run().expect(
            """
            AndroidManifest.xml:8: Error: As of Android 12, android:exported must be set; use true to make the activity available to other apps, and false otherwise. [IntentFilterExportedReceiver]
                    <activity
                     ~~~~~~~~
            1 errors, 0 warnings
            """
        ).expectFixDiffs(
            """
            Fix for AndroidManifest.xml line 8: Set exported="true":
            @@ -10 +10
            -         <activity android:name="MyActivity" >
            +         <activity
            +             android:name="MyActivity"
            +             android:exported="true" >
            Fix for AndroidManifest.xml line 8: Set exported="false":
            @@ -10 +10
            -         <activity android:name="MyActivity" >
            +         <activity
            +             android:name="MyActivity"
            +             android:exported="false" >
            """
        )
    }

    fun testExportedActivity() {
        lint().files(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg">

                    <application
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name" >
                        <activity
                            android:name="MyActivity"
                            android:exported="true">
                            <nav-graph android:value="@navigation/navigation_root" />
                        </activity>
                    </application>
                </manifest>
                """
            ).indented()
        ).run().expectClean()
    }

    fun testProvider() {
        lint().files(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg">

                    <application
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name" >
                        <provider
                            android:name="MyProvider"
                            android:exported="true">
                            <intent-filter>
                                <action android:name="foo"/>
                            </intent-filter>
                        </provider>
                    </application>
                </manifest>
                """
            ).indented()
        ).run().expectClean()
    }

    fun testLauncherActivity() {
        lint().files(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg">

                    <application
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name" >
                        <activity android:name="MyActivity">
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />
                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """
            )
        ).run().expect(
            """
        AndroidManifest.xml:8: Warning: As of Android 12, android:exported must be set; use true to make the activity available to other apps, and false otherwise. For launcher activities, this should be set to true. [IntentFilterExportedReceiver]
                                <activity android:name="MyActivity">
                                 ~~~~~~~~
        0 errors, 1 warnings
        """
        )
            .expectFixDiffs(
                """
                Fix for AndroidManifest.xml line 8: Set exported="true":
                @@ -8 +8
                -         <activity android:name="MyActivity" >
                +         <activity
                +             android:name="MyActivity"
                +             android:exported="true" >
            """
            )
    }

    fun testNonExportedLauncherActivity() {
        lint().files(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg">

                    <application
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name">
                        <activity
                            android:name="MyActivity"
                            android:exported="false">
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />
                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """
            )
        ).run().expect(
            """
            AndroidManifest.xml:10: Error: A launchable activity must be exported as of Android 12, which also makes it available to other apps. [IntentFilterExportedReceiver]
                                        android:exported="false">
                                        ~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        ).expectFixDiffs(
            """
            Fix for AndroidManifest.xml line 10: Set exported="true":
            @@ -10 +10
            -             android:exported="false" >
            +             android:exported="true" >
        """
        )
    }
}
