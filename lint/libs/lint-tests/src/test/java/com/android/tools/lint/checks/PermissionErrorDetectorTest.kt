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

import com.android.tools.lint.checks.PermissionErrorDetector.Companion.CUSTOM_PERMISSION_TYPO
import com.android.tools.lint.checks.PermissionErrorDetector.Companion.KNOWN_PERMISSION_ERROR
import com.android.tools.lint.checks.PermissionErrorDetector.Companion.PERMISSION_NAMING_CONVENTION
import com.android.tools.lint.checks.PermissionErrorDetector.Companion.RESERVED_SYSTEM_PERMISSION
import com.android.tools.lint.checks.PermissionErrorDetector.Companion.SYSTEM_PERMISSION_TYPO
import com.android.tools.lint.checks.PermissionErrorDetector.Companion.findAlmostCustomPermission
import com.android.tools.lint.checks.PermissionErrorDetector.Companion.findAlmostSystemPermission
import com.android.tools.lint.checks.PermissionErrorDetector.Companion.permissionToPrefixAndSuffix
import com.android.tools.lint.checks.SystemPermissionsDetector.SYSTEM_PERMISSIONS
import com.android.tools.lint.checks.infrastructure.ProjectDescription
import com.android.tools.lint.detector.api.Detector
import org.junit.Test

class PermissionErrorDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector = PermissionErrorDetector()

    @Test
    fun testDocumentationExamplePermissionNamingConvention() {
        lint().files(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                  xmlns:tools="http://schemas.android.com/tools"
                  package="com.example.helloworld">
                  <permission android:name="com.example.helloworld.permission.FOO_BAR" />
                  <permission android:name="com.example.helloworld.specific.path.permission.FOO_BAR" />
                  <permission android:name="com.example.helloworld.permission.FOO_BAR_123" />
                  <permission android:name="com.example.helloworld.FOO_BAR" />
                  <permission android:name="com.example.helloworld.permission.FOO-BAR" />
                  <permission android:name="com.example.helloworld.permission.foo_bar" />
                  <permission android:name="android.permission.FOO_BAR" />
                  <permission android:name="FOO_BAR" />
                </manifest>
                """
            ).indented()
        )
            .issues(PERMISSION_NAMING_CONVENTION)
            .run()
            .expect(
                """
                AndroidManifest.xml:7: Warning: com.example.helloworld.FOO_BAR does not follow recommended naming convention [PermissionNamingConvention]
                  <permission android:name="com.example.helloworld.FOO_BAR" />
                                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                AndroidManifest.xml:8: Warning: com.example.helloworld.permission.FOO-BAR does not follow recommended naming convention [PermissionNamingConvention]
                  <permission android:name="com.example.helloworld.permission.FOO-BAR" />
                                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                AndroidManifest.xml:9: Warning: com.example.helloworld.permission.foo_bar does not follow recommended naming convention [PermissionNamingConvention]
                  <permission android:name="com.example.helloworld.permission.foo_bar" />
                                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                AndroidManifest.xml:10: Warning: android.permission.FOO_BAR does not follow recommended naming convention [PermissionNamingConvention]
                  <permission android:name="android.permission.FOO_BAR" />
                                            ~~~~~~~~~~~~~~~~~~~~~~~~~~
                AndroidManifest.xml:11: Warning: FOO_BAR does not follow recommended naming convention [PermissionNamingConvention]
                  <permission android:name="FOO_BAR" />
                                            ~~~~~~~
                0 errors, 5 warnings
                """
            )
            .expectFixDiffs("")
    }

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
            .issues(KNOWN_PERMISSION_ERROR)
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
            .issues(KNOWN_PERMISSION_ERROR)
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
            .issues(RESERVED_SYSTEM_PERMISSION)
            .run()
            .expect(
                """
                AndroidManifest.xml:4: Error: android.permission.BIND_APPWIDGET is a reserved permission for the system [ReservedSystemPermission]
                  <permission android:name="android.permission.BIND_APPWIDGET" />
                                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """
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
            .issues(RESERVED_SYSTEM_PERMISSION)
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
                  package="com.example.helloworld">
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
            .issues(SYSTEM_PERMISSION_TYPO)
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
                  xmlns:tools="http://schemas.android.com/tools"
                  package="com.example.helloworld">
                  <application>
                    <activity />
                    <service android:permission="android.Manifest.permission.BIND_EUICC_SERVICE" />
                  </application>
                </manifest>
                """
            ).indented()
        )
            .issues(SYSTEM_PERMISSION_TYPO)
            .run()
            .expect(
                """
                AndroidManifest.xml:6: Warning: Did you mean android.permission.BIND_EUICC_SERVICE? [SystemPermissionTypo]
                    <service android:permission="android.Manifest.permission.BIND_EUICC_SERVICE" />
                                                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 1 warnings
                """
            )
            .expectFixDiffs(
                """
                Fix for AndroidManifest.xml line 5: Replace with android.permission.BIND_EUICC_SERVICE:
                @@ -6 +6
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
                  xmlns:tools="http://schemas.android.com/tools"
                  package="com.example.helloworld">
                  <application>
                    <activity />
                    <service android:permission="android.permission.ACCESS_AMBIENT_LIGHT_STATS" />
                  </application>
                </manifest>
                """
            ).indented()
        )
            .issues(SYSTEM_PERMISSION_TYPO)
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

    @Test
    fun testDocumentationExampleCustomPermissionTypo() {
        lint().files(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                  xmlns:tools="http://schemas.android.com/tools"
                  package="com.example.helloworld">
                  <permission android:name="my.custom.permission.FOOBAR" />
                  <permission android:name="my.custom.permission.FOOBAB" />
                  <permission android:name="my.custom.permission.BAZQUXX" />
                  <permission android:name="my.custom.permission.BAZQUZZ" />
                  <application>
                    <service android:permission="my.custom.permission.FOOBOB" />
                    <service android:permission="my.custom.permission.FOOBAB" />
                    <activity android:permission="my.custom.permission.BAZQXX" />
                    <activity android:permission="my.custom.permission.BAZQUZZ" />
                  </application>
                </manifest>
                """
            ).indented()
        )
            .issues(CUSTOM_PERMISSION_TYPO)
            .run()
            .expect(
                """
                AndroidManifest.xml:9: Warning: Did you mean my.custom.permission.FOOBAR? [CustomPermissionTypo]
                    <service android:permission="my.custom.permission.FOOBOB" />
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                AndroidManifest.xml:11: Warning: Did you mean my.custom.permission.BAZQUXX? [CustomPermissionTypo]
                    <activity android:permission="my.custom.permission.BAZQXX" />
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 2 warnings
                """
            )
            .expectFixDiffs(
                """
                Fix for AndroidManifest.xml line 8: Replace with my.custom.permission.FOOBAR:
                @@ -9 +9
                -     <service android:permission="my.custom.permission.FOOBOB" />
                +     <service android:permission="my.custom.permission.FOOBAR" />
                Fix for AndroidManifest.xml line 10: Replace with my.custom.permission.BAZQUXX:
                @@ -11 +11
                -     <activity android:permission="my.custom.permission.BAZQXX" />
                +     <activity android:permission="my.custom.permission.BAZQUXX" />
                """
            )
    }

    @Test
    fun testCustomPermissionTypoOk() {
        lint().files(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                  xmlns:tools="http://schemas.android.com/tools"
                  package="com.example.helloworld">
                  <permission android:name="my.custom.permission.FOOBAR" />
                  <permission android:name="my.custom.permission.BAZQUXX" />
                  <application>
                    <service android:permission="my.custom.permission.FOOBAR" />
                    <activity android:permission="my.custom.permission.BAZQUXX" />
                  </application>
                </manifest>
                """
            ).indented()
        )
            .issues(CUSTOM_PERMISSION_TYPO)
            .run().expectClean()
    }

    @Test
    fun testCustomPermissionTypoWithMergedManifest() {
        val library = project(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld.lib"
                    android:versionCode="1"
                    android:versionName="1.0" >
                    <uses-sdk android:minSdkVersion="14" />
                    <permission android:name="my.custom.permission.FOOBAR"
                        android:label="@string/foo"
                        android:description="@string/foo" />
                    <application
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name" >
                    </application>
                </manifest>
                """
            ).indented()
        ).type(ProjectDescription.Type.LIBRARY).name("Library")
        val main = project(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld.app"
                    android:versionCode="1"
                    android:versionName="1.0" >
                    <uses-sdk android:minSdkVersion="14" />
                    <uses-permission android:name="my.custom.permission.FOOBOB" />
                    <application
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name" >
                    </application>
                </manifest>
                """
            ).indented()
        ).name("App").dependsOn(library)

        lint().projects(main, library)
            .issues(CUSTOM_PERMISSION_TYPO)
            .run()
            .expect(
                """
                AndroidManifest.xml:6: Warning: Did you mean my.custom.permission.FOOBAR? [CustomPermissionTypo]
                    <uses-permission android:name="my.custom.permission.FOOBOB" />
                                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 1 warnings
                """
            )
            .expectFixDiffs(
                """
                Fix for AndroidManifest.xml line 6: Replace with my.custom.permission.FOOBAR:
                @@ -6 +6
                -     <uses-permission android:name="my.custom.permission.FOOBOB" />
                +     <uses-permission android:name="my.custom.permission.FOOBAR" />
                """
            )
    }

    @Test
    fun testFindAlmostCustomPermission() {
        val customPermissions = listOf("my.custom.permission.FOO_BAR", "my.custom.permission.BAZ_QUXX")
        assertEquals(
            findAlmostCustomPermission("my.custom.permission.FOOB", customPermissions),
            "my.custom.permission.FOO_BAR"
        )
        assertEquals(
            findAlmostCustomPermission("my.custom.permission.QUXX", customPermissions),
            "my.custom.permission.BAZ_QUXX"
        )
    }

    @Test
    fun testFindAlmostCustomPermission_noFalsePositives() {
        val customPermissions = listOf("my.custom.permission.FOO_BAR", "my.custom.permission.FOO_BAZ")
        customPermissions.forEach {
            assertNull(findAlmostCustomPermission(it, customPermissions))
        }
    }
}
