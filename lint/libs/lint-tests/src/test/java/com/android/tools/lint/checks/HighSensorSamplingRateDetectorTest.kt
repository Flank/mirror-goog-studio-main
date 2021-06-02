/*
 * Copyright (C) 2021 The Android Open Source Project
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

class HighSensorSamplingRateDetectorTest : AbstractCheckTest() {
    override fun getDetector() = HighSensorSamplingRateDetector()

    fun testDocumentationExample() {
        lint().files(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg">

                    <uses-permission android:name="android.permission.HIGH_SAMPLING_RATE_SENSORS"/>

                    <uses-sdk android:minSdkVersion="31" />

                    <application
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name" >
                        <receiver android:name="com.google.android.c2dm.C2DMBroadcastReceiver">
                        </receiver>
                    </application>
                </manifest>
            """
            ).indented()
        ).run().expect(
            """
            AndroidManifest.xml:4: Warning: Most apps don't need access to high sensor sampling rate. [HighSamplingRate]
                <uses-permission android:name="android.permission.HIGH_SAMPLING_RATE_SENSORS"/>
                                               ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }

    fun testNoHighSamplingRate() {
        lint().files(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg">

                    <application
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name" >
                        <receiver android:name="com.google.android.c2dm.C2DMBroadcastReceiver">
                        </receiver>
                    </application>
                </manifest>
            """
            ).indented()
        ).run().expectClean()
    }
}
