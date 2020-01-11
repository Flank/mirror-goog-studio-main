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

class C2dmDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return C2dmDetector()
    }

    fun testPattern1() {
        // See b/112195797
        lint().files(
            xml(
                "AndroidManifest.xml",
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg">

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
                AndroidManifest.xml:7: Error: The C2DM library does not work on Android P or newer devices; you should migrate to Firebase Cloud Messaging to ensure reliable message delivery [UsingC2DM]
                        <receiver android:name="com.google.android.c2dm.C2DMBroadcastReceiver">
                                                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
            """
        )
    }

    fun testPattern2() {
        lint().files(
            xml(
                "AndroidManifest.xml",
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg">

                    <application
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name" >
                        <receiver android:name="com.google.android.gcm.GCMBroadcastReceiver">
                          <intent-filter>
                            <action android:name="com.google.android.c2dm.intent.RECEIVE"/>
                          </intent-filter>
                          <intent-filter>
                            <action android:name="com.google.android.c2dm.intent.REGISTRATION"/>
                          </intent-filter>
                        </receiver>
                    </application>

                </manifest>
                """
            ).indented()
        ).run().expect(
            """
            AndroidManifest.xml:7: Error: The C2DM library does not work on Android P or newer devices; you should migrate to Firebase Cloud Messaging to ensure reliable message delivery [UsingC2DM]
                    <receiver android:name="com.google.android.gcm.GCMBroadcastReceiver">
                                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        )
    }

    fun testSuppress() {
        lint().files(
            xml(
                "AndroidManifest.xml",
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    package="test.pkg">

                    <application
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name" >
                        <receiver android:name="com.google.android.c2dm.C2DMBroadcastReceiver"
                          tools:ignore="UsingC2DM">
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

    fun testMissingAction() {
        lint().files(
            xml(
                "AndroidManifest.xml",
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg">

                    <application
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name" >
                        <receiver android:name="com.google.android.gcm.GCMBroadcastReceiver">
                          <intent-filter>
                            <action android:name="com.google.android.c2dm.intent.RECEIVE"/>
                          </intent-filter>
                        </receiver>
                    </application>

                </manifest>
                """
            ).indented()
        ).run().expectClean()
    }
}
