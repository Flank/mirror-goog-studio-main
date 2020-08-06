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
import org.intellij.lang.annotations.Language

class PackageVisibilityDetectorTest : AbstractCheckTest() {

    override fun getDetector(): Detector = PackageVisibilityDetector()

    fun testCannotQueryPackages() {
        lint().files(
            manifest().targetSdk(30),
            kotlin(ACTIVITY_WITH_APP_QUERIES).indented()
        )
            .run()
            .expect(
                """
                src/test/pkg/MainActivity.kt:14: Warning: As of Android 11, this method no longer returns information about all apps; see https://g.co/dev/packagevisibility for details [QueryPermissionsNeeded]
                        pm.getInstalledPackages(0) // ERROR
                           ~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/MainActivity.kt:15: Warning: As of Android 11, this method no longer returns information about all apps; see https://g.co/dev/packagevisibility for details [QueryPermissionsNeeded]
                        pm.getInstalledApplications(0) // ERROR
                           ~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/MainActivity.kt:17: Warning: Consider adding a <queries> declaration to your manifest when calling this method; see https://g.co/dev/packagevisibility for details [QueryPermissionsNeeded]
                        pm.queryBroadcastReceivers(Intent(), 0) // ERROR
                           ~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/MainActivity.kt:18: Warning: Consider adding a <queries> declaration to your manifest when calling this method; see https://g.co/dev/packagevisibility for details [QueryPermissionsNeeded]
                        pm.queryContentProviders("", 0, 0) // ERROR
                           ~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/MainActivity.kt:19: Warning: Consider adding a <queries> declaration to your manifest when calling this method; see https://g.co/dev/packagevisibility for details [QueryPermissionsNeeded]
                        pm.queryIntentServices(Intent(), 0) // ERROR
                           ~~~~~~~~~~~~~~~~~~~
                src/test/pkg/MainActivity.kt:20: Warning: Consider adding a <queries> declaration to your manifest when calling this method; see https://g.co/dev/packagevisibility for details [QueryPermissionsNeeded]
                        pm.queryIntentActivities(Intent(), 0) // ERROR
                           ~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/MainActivity.kt:22: Warning: Consider adding a <queries> declaration to your manifest when calling this method; see https://g.co/dev/packagevisibility for details [QueryPermissionsNeeded]
                        Intent().resolveActivity(pm) // ERROR
                                 ~~~~~~~~~~~~~~~
                src/test/pkg/MainActivity.kt:23: Warning: Consider adding a <queries> declaration to your manifest when calling this method; see https://g.co/dev/packagevisibility for details [QueryPermissionsNeeded]
                        Intent().resolveActivityInfo(pm, 0) // ERROR
                                 ~~~~~~~~~~~~~~~~~~~
                0 errors, 8 warnings
                """
            )
    }

    fun testCanQuerySomePackages() {
        lint().files(
            manifest(
                """<?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.pkg">
                    <uses-sdk android:targetSdkVersion="30"/>
                    <queries>
                        <package android:name="com.example.app" />
                    </queries>
                </manifest>
                """
            ),
            kotlin(ACTIVITY_WITH_APP_QUERIES).indented()
        )
            .run()
            .expect(
                """
                src/test/pkg/MainActivity.kt:14: Warning: As of Android 11, this method no longer returns information about all apps; see https://g.co/dev/packagevisibility for details [QueryPermissionsNeeded]
                        pm.getInstalledPackages(0) // ERROR
                           ~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/MainActivity.kt:15: Warning: As of Android 11, this method no longer returns information about all apps; see https://g.co/dev/packagevisibility for details [QueryPermissionsNeeded]
                        pm.getInstalledApplications(0) // ERROR
                           ~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 2 warnings
                """
            )
    }

    fun testCanQueryAllPackages() {
        lint().files(
            manifest(
                """<?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.pkg">
                    <uses-sdk android:targetSdkVersion="30"/>
                    <uses-permission/><!-- Test for NPEs -->
                    <uses-permission android:name="android.permission.CAMERA"/><!-- OK -->
                    <uses-permission android:name="android.permission.QUERY_ALL_PACKAGES"/><!-- ERROR -->
                </manifest>
                """
            ),
            kotlin(ACTIVITY_WITH_APP_QUERIES).indented()
        )
            .run()
            .expect(
                """
                AndroidManifest.xml:6: Error: A <queries> declaration should generally be used instead of QUERY_ALL_PACKAGES; see https://g.co/dev/packagevisibility for details [QueryAllPackagesPermission]
                                    <uses-permission android:name="android.permission.QUERY_ALL_PACKAGES"/><!-- ERROR -->
                                                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """
            )
    }

    fun testTargetSdkTooLow() {
        // App visibility restrictions did not go into effect until Android R (API 30).
        lint().files(
            manifest().targetSdk(29),
            kotlin(ACTIVITY_WITH_APP_QUERIES).indented()
        )
            .run()
            .expectClean()
    }

    @Language("kotlin")
    private val ACTIVITY_WITH_APP_QUERIES =
        """
        package test.pkg

        import android.app.Activity
        import android.content.Intent
        import android.os.Bundle

        class MainActivity : Activity() {

            override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                val context = applicationContext
                val pm = context.packageManager

                pm.getInstalledPackages(0) // ERROR
                pm.getInstalledApplications(0) // ERROR

                pm.queryBroadcastReceivers(Intent(), 0) // ERROR
                pm.queryContentProviders("", 0, 0) // ERROR
                pm.queryIntentServices(Intent(), 0) // ERROR
                pm.queryIntentActivities(Intent(), 0) // ERROR

                Intent().resolveActivity(pm) // ERROR
                Intent().resolveActivityInfo(pm, 0) // ERROR

                this.getInstalledPackages() // OK
                this.resolveActivity() // OK
            }

            private fun getInstalledPackages() = Unit
            private fun resolveActivity() = Unit
        }
    """
}
