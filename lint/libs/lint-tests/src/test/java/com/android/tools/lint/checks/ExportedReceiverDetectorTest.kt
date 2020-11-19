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

class ExportedReceiverDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return ExportedReceiverDetector()
    }

    fun testNoExport() {
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
            AndroidManifest.xml:8: Warning: When using intent filters, please specify android:exported as well [IntentFilterExportedReceiver]
                      <intent-filter>
                       ~~~~~~~~~~~~~
            0 errors, 1 warnings
            """.trimIndent()
        )
    }

    fun testExport() {
        lint().files(
            xml(
                "AndroidManifest.xml",
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
        ).run().expect("No warnings.")
    }
}
