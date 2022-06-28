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

import com.android.testutils.TestUtils
import com.android.tools.lint.checks.PermissionErrorDetector.Companion.CUSTOM_PERMISSION_TYPO
import com.android.tools.lint.checks.PermissionErrorDetector.Companion.KNOWN_PERMISSION_ERROR
import com.android.tools.lint.checks.PermissionErrorDetector.Companion.PERMISSION_NAMING_CONVENTION
import com.android.tools.lint.checks.PermissionErrorDetector.Companion.RESERVED_SYSTEM_PERMISSION
import com.android.tools.lint.checks.PermissionErrorDetector.Companion.SYSTEM_PERMISSION_TYPO
import com.android.tools.lint.checks.PermissionErrorDetector.Companion.findAlmostCustomPermission
import com.android.tools.lint.checks.PermissionErrorDetector.Companion.findAlmostPlatformPermission
import com.android.tools.lint.checks.PermissionErrorDetector.Companion.permissionToPrefixAndSuffix
import com.android.tools.lint.checks.SystemPermissionsDetector.SYSTEM_PERMISSIONS
import com.android.tools.lint.checks.infrastructure.ProjectDescription
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.client.api.LintDriver
import com.android.tools.lint.client.api.LintListener
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Project
import com.google.common.io.Files
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PermissionErrorDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector = PermissionErrorDetector()

    override fun lint(): TestLintTask {
        return super.lint()
            // When switching to merging, clear out the platform cache (to simulate running lint where the analysis
            // tasks have been cached so have not run in the current process. It would be better if the lint testing
            // infrastructure did this automatically (e.g. loading everything into separate class loaders to enforce
            // true separation) but that's hard to set up now.
            .listener(object : LintListener {
                private var mode: LintDriver.DriverMode? = null
                override fun update(driver: LintDriver, type: LintListener.EventType, project: Project?, context: Context?) {
                    if (driver.mode != mode) {
                        PermissionErrorDetector.clearPlatformPermissions()
                    }
                    mode = driver.mode
                }
            })
    }

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
                  <permission android:name="android.permission.FOOBAR" />
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
                AndroidManifest.xml:4: Error: android.permission.BIND_APPWIDGET is a reserved permission [ReservedSystemPermission]
                  <permission android:name="android.permission.BIND_APPWIDGET" />
                                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                AndroidManifest.xml:5: Error: android.permission.FOOBAR is using the reserved system prefix android. [ReservedSystemPermission]
                  <permission android:name="android.permission.FOOBAR" />
                                            ~~~~~~~~~~~~~~~~~~~~~~~~~
                2 errors, 0 warnings
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
                  <uses-permission android:name="android.permission.BIND_NCF_SERVICE" />
                  <application android:name="App" android:permission="android.permission.BIND_NCF_SERVICE">
                    <activity />
                    <activity android:permission="android.permission.BIND_NFC_SERVICE" />
                    <activity android:permission="android.permission.BIND_NCF_SERVICE" />
                    <activity-alias android:permission="android.permission.BIND_NCF_SERVICE" />
                    <receiver android:permission="android.permission.BIND_NCF_SERVICE" />
                    <service android:permission="android.permission.BIND_NCF_SERVICE" />
                    <provider android:permission="android.permission.BIND_NCF_SERVICE" />
                    </application>
                  </manifest>
                  """
            ).indented()
        )
            .issues(SYSTEM_PERMISSION_TYPO)
            .run()
            .expect(
                """
                AndroidManifest.xml:5: Warning: Did you mean android.permission.BIND_NFC_SERVICE? [SystemPermissionTypo]
                  <uses-permission android:name="android.permission.BIND_NCF_SERVICE" />
                                                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                AndroidManifest.xml:6: Warning: Did you mean android.permission.BIND_NFC_SERVICE? [SystemPermissionTypo]
                  <application android:name="App" android:permission="android.permission.BIND_NCF_SERVICE">
                                                                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                AndroidManifest.xml:9: Warning: Did you mean android.permission.BIND_NFC_SERVICE? [SystemPermissionTypo]
                    <activity android:permission="android.permission.BIND_NCF_SERVICE" />
                                                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                AndroidManifest.xml:10: Warning: Did you mean android.permission.BIND_NFC_SERVICE? [SystemPermissionTypo]
                    <activity-alias android:permission="android.permission.BIND_NCF_SERVICE" />
                                                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                AndroidManifest.xml:11: Warning: Did you mean android.permission.BIND_NFC_SERVICE? [SystemPermissionTypo]
                    <receiver android:permission="android.permission.BIND_NCF_SERVICE" />
                                                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                AndroidManifest.xml:12: Warning: Did you mean android.permission.BIND_NFC_SERVICE? [SystemPermissionTypo]
                    <service android:permission="android.permission.BIND_NCF_SERVICE" />
                                                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                AndroidManifest.xml:13: Warning: Did you mean android.permission.BIND_NFC_SERVICE? [SystemPermissionTypo]
                    <provider android:permission="android.permission.BIND_NCF_SERVICE" />
                                                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 7 warnings
                """
            )
            .expectFixDiffs(
                """
                Fix for AndroidManifest.xml line 5: Replace with android.permission.BIND_NFC_SERVICE:
                @@ -5 +5
                -   <uses-permission android:name="android.permission.BIND_NCF_SERVICE" />
                +   <uses-permission android:name="android.permission.BIND_NFC_SERVICE" />
                Fix for AndroidManifest.xml line 6: Replace with android.permission.BIND_NFC_SERVICE:
                @@ -6 +6
                -   <application android:name="App" android:permission="android.permission.BIND_NCF_SERVICE">
                +   <application android:name="App" android:permission="android.permission.BIND_NFC_SERVICE">
                Fix for AndroidManifest.xml line 9: Replace with android.permission.BIND_NFC_SERVICE:
                @@ -9 +9
                -     <activity android:permission="android.permission.BIND_NCF_SERVICE" />
                +     <activity android:permission="android.permission.BIND_NFC_SERVICE" />
                Fix for AndroidManifest.xml line 10: Replace with android.permission.BIND_NFC_SERVICE:
                @@ -10 +10
                -     <activity-alias android:permission="android.permission.BIND_NCF_SERVICE" />
                +     <activity-alias android:permission="android.permission.BIND_NFC_SERVICE" />
                Fix for AndroidManifest.xml line 11: Replace with android.permission.BIND_NFC_SERVICE:
                @@ -11 +11
                -     <receiver android:permission="android.permission.BIND_NCF_SERVICE" />
                +     <receiver android:permission="android.permission.BIND_NFC_SERVICE" />
                Fix for AndroidManifest.xml line 12: Replace with android.permission.BIND_NFC_SERVICE:
                @@ -12 +12
                -     <service android:permission="android.permission.BIND_NCF_SERVICE" />
                +     <service android:permission="android.permission.BIND_NFC_SERVICE" />
                Fix for AndroidManifest.xml line 13: Replace with android.permission.BIND_NFC_SERVICE:
                @@ -13 +13
                -     <provider android:permission="android.permission.BIND_NCF_SERVICE" />
                +     <provider android:permission="android.permission.BIND_NFC_SERVICE" />
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
                    <service android:permission="android.Manifest.permission.BIND_NFC_SERVICE" />
                    <service android:permission="android.permission.WAKE_LOCK" />
                  </application>
                </manifest>
                """
            ).indented()
        )
            .issues(SYSTEM_PERMISSION_TYPO)
            .run()
            .expect(
                """
                AndroidManifest.xml:6: Warning: Did you mean android.permission.BIND_NFC_SERVICE? [SystemPermissionTypo]
                    <service android:permission="android.Manifest.permission.BIND_NFC_SERVICE" />
                                                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 1 warnings
                """
            )
            .expectFixDiffs(
                """
                Fix for AndroidManifest.xml line 5: Replace with android.permission.BIND_NFC_SERVICE:
                @@ -6 +6
                -     <service android:permission="android.Manifest.permission.BIND_NFC_SERVICE" />
                +     <service android:permission="android.permission.BIND_NFC_SERVICE" />
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
        // Set up a simple compilation environment such that we can grab a project out of it to use for unit testing
        // the findAlmostSystemPermission (which needs access to the java evaluator)
        val temp = Files.createTempDir()
        val temporaryFolder = TemporaryFolder(temp)
        temporaryFolder.create()
        val parsed = com.android.tools.lint.checks.infrastructure.parseFirst(
            sdkHome = TestUtils.getSdk().toFile(),
            temporaryFolder = temporaryFolder,
            testFiles = arrayOf(
                java(
                    "class Test { }"
                )
            )
        )
        val disposable = Disposable {
            Disposer.dispose(parsed.second)
            temp.deleteRecursively()
        }

        val context = parsed.first
        val project = context.project

        // well-known cases are handled
        assertEquals(
            "android.permission.BIND_NFC_SERVICE",
            findAlmostPlatformPermission(project, "android.permission.BIND_NCF_SERVICE")
        )
        assertEquals(
            "android.permission.BIND_NFC_SERVICE",
            findAlmostPlatformPermission(project, "android.Manifest.permission.BIND_NCF_SERVICE")
        )
        assertEquals(
            "android.permission.BIND_NFC_SERVICE",
            findAlmostPlatformPermission(project, "android.permission.bind_ncf_service")
        )
        assertEquals(
            "android.permission.BIND_NFC_SERVICE",
            findAlmostPlatformPermission(project, "android.permission\n      .BIND_NCF_@@--~~SERVICE")
        )
        assertEquals(
            "android.permission.BLUETOOTH_PRIVILEGED",
            findAlmostPlatformPermission(
                project,
                """
                android.permission.BIND_NFC_SERVICE |
                android.permission.SYSTEM_ALERT_WINDOW |
                android.permission.BLUETOOTH_PRIVILEGED
                """.trimMargin()
            )
        )

        // Matching based on just the name part
        assertEquals(
            "android.permission.BIND_NFC_SERVICE",
            findAlmostPlatformPermission(project, "@ndr\$oid@.BIND_NCF_SERVICE")
        )
        assertEquals(
            "android.permission.BIND_NFC_SERVICE",
            findAlmostPlatformPermission(project, "\${MY_SUBSTITUTION}.BIND_NCF_SERVICE")
        )
        assertEquals(
            "android.permission.BIND_NFC_SERVICE",
            findAlmostPlatformPermission(project, "android.BIND_NCF_SERVICE")
        )
        assertEquals(
            "android.permission.BIND_NFC_SERVICE",
            findAlmostPlatformPermission(project, "adroid.prmission.BIND_NCF_SERVICE") // typos in package name
        )

        //  assure we don't match one valid permission against another
        for (systemPermission in SYSTEM_PERMISSIONS) {
            assertNull(findAlmostPlatformPermission(project, systemPermission))
        }

        //  assure we don't match against a clearly custom package
        assertNull(
            findAlmostPlatformPermission(project, "my.custom.package.CAMERA")
        )
        assertNull(
            findAlmostPlatformPermission(project, "my.custom.package.CMERA")
        )

        // assure the edit distance logic behaves as expected per the MAX_EDIT_DISTANCE const
        assertEquals(
            "android.permission.BIND_NFC_SERVICE",
            findAlmostPlatformPermission(project, "android.permission.BIND_NFC_SERVZZZ")
        )
        assertNull(
            findAlmostPlatformPermission(project, "android.permission.BIND_NFC_SERZZZZ")
        )

        Disposer.dispose(disposable)
    }

    @Test
    fun testPermissionToPrefixAndSuffix() {
        val (prefix1, suffix1) = permissionToPrefixAndSuffix("foo")
        assertEquals(prefix1, "")
        assertEquals(suffix1, "foo")
        val (prefix2, suffix2) = permissionToPrefixAndSuffix("android.permission.BIND_NFC_SERVICE")
        assertEquals(prefix2, "android.permission")
        assertEquals(suffix2, "BIND_NFC_SERVICE")
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
                    <activity android:permission="my.custom.permission.WAKE_LOCK" />
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

    fun testDemonstrateMultipleIssuesAtSameLocation() {
        val main = project(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                  xmlns:tools="http://schemas.android.com/tools"
                  package="com.example.helloworld">
                  <permission android:name="android.permission.BIND_APPWIDGET" />
                  <application>
                    <service android:permission="android.permission.BINDAPPWIDGET" />
                  </application>
                </manifest>
                """
            ).indented()
        )
        lint().projects(main)
            .run()
            .expect(
                """
                AndroidManifest.xml:6: Warning: Did you mean android.permission.BIND_APPWIDGET? [CustomPermissionTypo]
                    <service android:permission="android.permission.BINDAPPWIDGET" />
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                AndroidManifest.xml:4: Warning: android.permission.BIND_APPWIDGET does not follow recommended naming convention [PermissionNamingConvention]
                  <permission android:name="android.permission.BIND_APPWIDGET" />
                                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                AndroidManifest.xml:4: Error: android.permission.BIND_APPWIDGET is a reserved permission [ReservedSystemPermission]
                  <permission android:name="android.permission.BIND_APPWIDGET" />
                                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                AndroidManifest.xml:6: Warning: Did you mean android.permission.BIND_APPWIDGET? [SystemPermissionTypo]
                    <service android:permission="android.permission.BINDAPPWIDGET" />
                                                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 3 warnings
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
            findAlmostCustomPermission("my.custom.permission.BAZQUXX", customPermissions),
            "my.custom.permission.BAZ_QUXX"
        )
        assertEquals(
            findAlmostCustomPermission("my.custom.permission.BAZ_QZZZ", customPermissions),
            "my.custom.permission.BAZ_QUXX"
        )
        assertNull(
            findAlmostCustomPermission("my.custom.permission.BAZ_ZZZZ", customPermissions),
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
