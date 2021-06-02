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

class LeanbackWifiUsageDetectorTest : AbstractCheckTest() {
    override fun getDetector() = LeanbackWifiUsageDetector()

    fun testUseWifiFeature() {
        lint().files(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg">
                    <uses-feature android:name="android.software.leanback"/>
                    <uses-feature android:name="android.hardware.wifi"/>
                    <application
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name" >
                    </application>
                </manifest>
                """
            ).indented()
        ).run().expect(
            """
            AndroidManifest.xml:4: Warning: Requiring android.hardware.wifi limits app availability on TVs that support only Ethernet [LeanbackUsesWifi]
                <uses-feature android:name="android.hardware.wifi"/>
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }

    fun testDocumentationExample() {
        lint().files(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg">
                    <uses-feature android:name="android.software.leanback"/>
                    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE"/>
                    <application
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name" >
                    </application>
                </manifest>
                """
            ).indented()
        ).run().expect(
            """
            AndroidManifest.xml:4: Warning: Requiring Wifi permissions limits app availability on TVs that support only Ethernet [LeanbackUsesWifi]
                <uses-permission android:name="android.permission.ACCESS_WIFI_STATE"/>
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }

    fun testUseWifiChangeStatePermission() {
        lint().files(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg">
                    <uses-feature android:name="android.software.leanback"/>
                    <uses-permission android:name="android.permission.CHANGE_WIFI_STATE"/>
                    <application
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name" >
                    </application>
                </manifest>
                """
            ).indented()
        ).run().expect(
            """
            AndroidManifest.xml:4: Warning: Requiring Wifi permissions limits app availability on TVs that support only Ethernet [LeanbackUsesWifi]
                <uses-permission android:name="android.permission.CHANGE_WIFI_STATE"/>
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }

    fun testUseWifiMulticastStatePermission() {
        lint().files(
            manifest(
                """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="test.pkg">
                <uses-feature android:name="android.software.leanback"/>
                <uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE"/>
                <application
                    android:icon="@drawable/ic_launcher"
                    android:label="@string/app_name" >
                </application>
            </manifest>
            """
            ).indented()
        ).run().expect(
            """
            AndroidManifest.xml:4: Warning: Requiring Wifi permissions limits app availability on TVs that support only Ethernet [LeanbackUsesWifi]
                <uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE"/>
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }

    fun testNoLeanbackNoWarning() {
        lint().files(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg">
                    <uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE"/>
                    <application
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name" >
                    </application>
                </manifest>
                """
            ).indented()
        ).run().expectClean()
    }

    fun testNotRequiredLeanbackWifiFeature() {
        lint().files(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg">
                    <uses-feature android:name="android.software.leanback"/>
                    <uses-feature android:name="android.hardware.wifi" android:required="false"/>
                    <application
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name" >
                    </application>
                </manifest>
                """
            ).indented()
        ).run().expectClean()
    }

    fun testNotRequiredLeanbackWifiFeatureAndPermission() {
        lint().files(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg">
                    <uses-feature android:name="android.software.leanback"/>
                    <uses-feature android:name="android.hardware.wifi" android:required="false"/>
                    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
                    <application
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name" >
                    </application>
                </manifest>
                """
            ).indented()
        ).run().expectClean()
    }

    fun testSuppress() {
        // Like testUseWifiFeature but with a tools:ignore on the error node, making
        // sure that local suppress directives are working
        lint().files(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    package="test.pkg">
                    <uses-feature android:name="android.software.leanback"/>
                    <uses-feature android:name="android.hardware.wifi" tools:ignore="LeanbackUsesWifi" />
                    <application
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name" >
                    </application>
                </manifest>
                """
            ).indented()
        ).run().expectClean()
    }

    fun testMultiProject() {
        // Like testUseWifiFeature but with the various manifest elements spread into
        // different modules
        val lib1 = project(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg.lib1">
                    <uses-feature android:name="android.software.leanback"/>
                </manifest>
                """
            ).indented()
        )
        val lib2 = project(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg.lib2">
                    <uses-feature android:name="android.hardware.wifi"/>
                </manifest>
                """
            ).indented()
        )
        val app = project(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg.app">
                    <application
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name" >
                    </application>
                </manifest>
                """
            ).indented()
        ).dependsOn(lib1).dependsOn(lib2)

        val expected =
            """
            ../lib2/AndroidManifest.xml:3: Warning: Requiring android.hardware.wifi limits app availability on TVs that support only Ethernet [LeanbackUsesWifi]
                <uses-feature android:name="android.hardware.wifi"/>
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """

        lint().projects(app).run().expect(expected)
    }
}
