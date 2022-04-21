/*
 * Copyright (C) 2022 The Android Open Source Project
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
import org.junit.Test

class PermissionErrorDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector = PermissionErrorDetector()

    @Test
    fun testDocumentationExampleKnownPermissionError() {
        lint().files(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                  xmlns:tools="http://schemas.android.com/tools"
                  package="com.example.helloworld">
                  <application android:permission="true">
                    <activity android:permission="TRUE" />
                    <activity-alias android:permission="True" />
                    <receiver android:permission="true" />
                    <service android:permission="false" />
                    <provider android:permission="false" />
                  </application>
                </manifest>
                """
            ).indented()
        )
            .run()
            .expect(
                """
                AndroidManifest.xml:4: Error: true is not a valid permission value [KnownPermissionError]
                  <application android:permission="true">
                                                   ~~~~
                AndroidManifest.xml:5: Error: TRUE is not a valid permission value [KnownPermissionError]
                    <activity android:permission="TRUE" />
                                                  ~~~~
                AndroidManifest.xml:6: Error: True is not a valid permission value [KnownPermissionError]
                    <activity-alias android:permission="True" />
                                                        ~~~~
                AndroidManifest.xml:7: Error: true is not a valid permission value [KnownPermissionError]
                    <receiver android:permission="true" />
                                                  ~~~~
                AndroidManifest.xml:8: Error: false is not a valid permission value [KnownPermissionError]
                    <service android:permission="false" />
                                                 ~~~~~
                AndroidManifest.xml:9: Error: false is not a valid permission value [KnownPermissionError]
                    <provider android:permission="false" />
                                                  ~~~~~
                6 errors, 0 warnings
                """
            )
            .expectFixDiffs("")
    }

    @Test
    fun testKnownPermissionErrorOk() {
        lint().files(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                  xmlns:tools="http://schemas.android.com/tools"
                  package="com.example.helloworld">
                  <application>
                    <activity android:permission="android.permission.BIND" />
                  </application>
                </manifest>
                """
            ).indented()
        )
            .run()
            .expectClean()
    }

    @Test
    fun testDocumentationExampleReservedSystemPermission() {
        lint().files(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                  xmlns:tools="http://schemas.android.com/tools"
                  package="com.example.helloworld">
                  <permission android:name="android.permission.BIND_APPWIDGET" />
                  <application>
                    <service android:permission="android.permission.BIND_APPWIDGET" />
                  </application>
                </manifest>
                """
            ).indented()
        )
            .run()
            .expect(
                """
                AndroidManifest.xml:4: Warning: android.permission.BIND_APPWIDGET is a reserved permission for the system [ReservedSystemPermission]
                  <permission android:name="android.permission.BIND_APPWIDGET" />
                                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 1 warnings
                """.trimIndent()
            )
    }

    @Test
    fun testReservedSystemPermissionOk() {
        lint().files(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                  xmlns:tools="http://schemas.android.com/tools"
                  package="com.example.helloworld">
                  <permission android:name="com.example.BIND_APPWIDGET" />
                  <application>
                    <service android:permission="android.permission.BIND_APPWIDGET" />
                  </application>
                </manifest>
                """
            ).indented()
        )
            .run()
            .expectClean()
    }
}
