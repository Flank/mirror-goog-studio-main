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

import com.android.tools.lint.checks.PermissionErrorDetector.Companion.findAlmostSystemPermission
import com.android.tools.lint.checks.PermissionErrorDetector.Companion.permissionToPrefixAndSuffix
import com.android.tools.lint.checks.SystemPermissionsDetector.SYSTEM_PERMISSIONS
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
                    <activity android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE" />
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

    @Test
    fun testDocumentationExampleSystemPermissionTypo() {
        lint().files(
            manifest(
                """
                <manifest
                  xmlns:android="http://schemas.android.com/apk/res/android"
                  xmlns:tools="http://schemas.android.com/tools"
                >
                  <uses-permission android:name="android.permission.BIND_EIUCC_SERVICE" />
                  <application android:name="App" android:permission="android.permission.BIND_EIUCC_SERVICE">
                    <activity />
                    <activity android:permission="android.permission.BIND_EUICC_SERVICE" />
                    <activity android:permission="android.permission.BIND_EIUCC_SERVICE" />
                    <activity-alias android:permission="android.permission.BIND_EIUCC_SERVICE" />
                    <receiver android:permission="android.permission.BIND_EIUCC_SERVICE" />
                    <service android:permission="android.permission.BIND_EIUCC_SERVICE" />
                    <provider android:permission="android.permission.BIND_EIUCC_SERVICE" />
                    </application>
                  </manifest>
                  """
            ).indented()
        )
            .run()
            .expect(
                """
                AndroidManifest.xml:5: Warning: Did you mean android.permission.BIND_EUICC_SERVICE? [SystemPermissionTypo]
                  <uses-permission android:name="android.permission.BIND_EIUCC_SERVICE" />
                                                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                AndroidManifest.xml:6: Warning: Did you mean android.permission.BIND_EUICC_SERVICE? [SystemPermissionTypo]
                  <application android:name="App" android:permission="android.permission.BIND_EIUCC_SERVICE">
                                                                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                AndroidManifest.xml:9: Warning: Did you mean android.permission.BIND_EUICC_SERVICE? [SystemPermissionTypo]
                    <activity android:permission="android.permission.BIND_EIUCC_SERVICE" />
                                                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                AndroidManifest.xml:10: Warning: Did you mean android.permission.BIND_EUICC_SERVICE? [SystemPermissionTypo]
                    <activity-alias android:permission="android.permission.BIND_EIUCC_SERVICE" />
                                                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                AndroidManifest.xml:11: Warning: Did you mean android.permission.BIND_EUICC_SERVICE? [SystemPermissionTypo]
                    <receiver android:permission="android.permission.BIND_EIUCC_SERVICE" />
                                                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                AndroidManifest.xml:12: Warning: Did you mean android.permission.BIND_EUICC_SERVICE? [SystemPermissionTypo]
                    <service android:permission="android.permission.BIND_EIUCC_SERVICE" />
                                                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                AndroidManifest.xml:13: Warning: Did you mean android.permission.BIND_EUICC_SERVICE? [SystemPermissionTypo]
                    <provider android:permission="android.permission.BIND_EIUCC_SERVICE" />
                                                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 7 warnings
                """
            )
            .expectFixDiffs(
                """
                Fix for AndroidManifest.xml line 5: Replace with android.permission.BIND_EUICC_SERVICE:
                @@ -5 +5
                -   <uses-permission android:name="android.permission.BIND_EIUCC_SERVICE" />
                +   <uses-permission android:name="android.permission.BIND_EUICC_SERVICE" />
                Fix for AndroidManifest.xml line 6: Replace with android.permission.BIND_EUICC_SERVICE:
                @@ -6 +6
                -   <application android:name="App" android:permission="android.permission.BIND_EIUCC_SERVICE">
                +   <application android:name="App" android:permission="android.permission.BIND_EUICC_SERVICE">
                Fix for AndroidManifest.xml line 9: Replace with android.permission.BIND_EUICC_SERVICE:
                @@ -9 +9
                -     <activity android:permission="android.permission.BIND_EIUCC_SERVICE" />
                +     <activity android:permission="android.permission.BIND_EUICC_SERVICE" />
                Fix for AndroidManifest.xml line 10: Replace with android.permission.BIND_EUICC_SERVICE:
                @@ -10 +10
                -     <activity-alias android:permission="android.permission.BIND_EIUCC_SERVICE" />
                +     <activity-alias android:permission="android.permission.BIND_EUICC_SERVICE" />
                Fix for AndroidManifest.xml line 11: Replace with android.permission.BIND_EUICC_SERVICE:
                @@ -11 +11
                -     <receiver android:permission="android.permission.BIND_EIUCC_SERVICE" />
                +     <receiver android:permission="android.permission.BIND_EUICC_SERVICE" />
                Fix for AndroidManifest.xml line 12: Replace with android.permission.BIND_EUICC_SERVICE:
                @@ -12 +12
                -     <service android:permission="android.permission.BIND_EIUCC_SERVICE" />
                +     <service android:permission="android.permission.BIND_EUICC_SERVICE" />
                Fix for AndroidManifest.xml line 13: Replace with android.permission.BIND_EUICC_SERVICE:
                @@ -13 +13
                -     <provider android:permission="android.permission.BIND_EIUCC_SERVICE" />
                +     <provider android:permission="android.permission.BIND_EUICC_SERVICE" />
                """
            )
    }

    @Test
    fun testSystemPermissionTypoPrefix() {
        lint().files(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                  xmlns:tools="http://schemas.android.com/tools">
                  <application>
                    <activity />
                    <service android:permission="android.Manifest.permission.BIND_EUICC_SERVICE" />
                  </application>
                </manifest>
                """
            ).indented()
        )
            .run()
            .expect(
                """
                AndroidManifest.xml:5: Warning: Did you mean android.permission.BIND_EUICC_SERVICE? [SystemPermissionTypo]
                    <service android:permission="android.Manifest.permission.BIND_EUICC_SERVICE" />
                                                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 1 warnings
                """
            )
            .expectFixDiffs(
                """
                Fix for AndroidManifest.xml line 5: Replace with android.permission.BIND_EUICC_SERVICE:
                @@ -5 +5
                -     <service android:permission="android.Manifest.permission.BIND_EUICC_SERVICE" />
                +     <service android:permission="android.permission.BIND_EUICC_SERVICE" />
                """
            )
    }

    @Test
    fun testSystemPermissionTypoOk() {
        lint().files(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                  xmlns:tools="http://schemas.android.com/tools">
                  <application>
                    <activity />
                    <service android:permission="android.permission.ACCESS_AMBIENT_LIGHT_STATS" />
                  </application>
                </manifest>
                """
            ).indented()
        )
            .run()
            .expectClean()
    }

    @Test
    fun testFindAlmostSystemPermission() {
        // well-known cases are handled
        assertEquals(
            findAlmostSystemPermission("android.permission.BIND_EIUCC_SERVICE"),
            "android.permission.BIND_EUICC_SERVICE"
        )
        assertEquals(
            findAlmostSystemPermission("android.Manifest.permission.BIND_EIUCC_SERVICE"),
            "android.permission.BIND_EUICC_SERVICE"
        )
        assertEquals(
            findAlmostSystemPermission("android.permission.bind_eiucc_service"),
            "android.permission.BIND_EUICC_SERVICE"
        )
        assertEquals(
            findAlmostSystemPermission("android.permission\n      .BIND_EIUCC_@@--~~SERVICE"),
            "android.permission.BIND_EUICC_SERVICE"
        )
        assertEquals(
            findAlmostSystemPermission(
                """
                android.permission.BIND_JOB_SERVICE |
                android.permission.SYSTEM_ALERT_WINDOW |
                android.permission.BLUETOOTH_PRIVILEGED
                """.trimMargin()
            ),
            "android.permission.BIND_JOB_SERVICE"
        )
        // as of now these cases would not be handled
        assertNull(findAlmostSystemPermission("@ndr\$oid@.BIND_EIUCC_SERVICE"))
        assertNull(findAlmostSystemPermission("\${MY_SUBSTITUTION}.BIND_EIUCC_SERVICE"))
        assertNull(findAlmostSystemPermission("android.BIND_EIUCC_SERVICE"))
    }

    @Test
    fun testFindAlmostSystemPermission_noFalsePositives() {
        for (systemPermission in SYSTEM_PERMISSIONS) {
            assertNull(findAlmostSystemPermission(systemPermission))
        }
    }

    @Test
    fun testPermissionToPrefixAndSuffix() {
        val (prefix1, suffix1) = permissionToPrefixAndSuffix("foo")
        assertEquals(prefix1, "")
        assertEquals(suffix1, "foo")
        val (prefix2, suffix2) = permissionToPrefixAndSuffix("android.permission.BIND_EUICC_SERVICE")
        assertEquals(prefix2, "android.permission")
        assertEquals(suffix2, "BIND_EUICC_SERVICE")
    }
}
