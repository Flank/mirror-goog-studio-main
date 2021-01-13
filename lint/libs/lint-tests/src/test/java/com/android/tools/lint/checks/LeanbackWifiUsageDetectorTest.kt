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
            xml(
                "AndroidManifest.xml",
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
            """.trimIndent()
        )
    }

    fun testUseWifiAccessStatePermission() {
        lint().files(
            xml(
                "AndroidManifest.xml",
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
            """.trimIndent()
        )
    }

    fun testUseWifiChangeStatePermission() {
        lint().files(
            xml(
                "AndroidManifest.xml",
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
            """.trimIndent()
        )
    }

    fun testUseWifiMulticastStatePermission() {
        lint().files(
            xml(
                "AndroidManifest.xml",
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
            """.trimIndent()
        )
    }

    fun testNoLeanbackNoWarning() {
        lint().files(
            xml(
                "AndroidManifest.xml",
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
            xml(
                "AndroidManifest.xml",
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
            xml(
                "AndroidManifest.xml",
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
}
