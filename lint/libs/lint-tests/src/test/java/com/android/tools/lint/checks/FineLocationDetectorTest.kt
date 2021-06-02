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

class FineLocationDetectorTest : AbstractCheckTest() {
    override fun getDetector() = FineLocationDetector()

    fun testDocumentationExample() {
        lint().files(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg">
                    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
                    <uses-sdk android:targetSdkVersion="31"/>
                    <application
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name" >
                    </application>
                </manifest>
                """
            ).indented()
        ).run().expect(
            """
            AndroidManifest.xml:3: Error: If you need access to FINE location, you must request both ACCESS_FINE_LOCATION and ACCESS_COARSE_LOCATION [CoarseFineLocation]
                <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        )
    }

    fun testFineNoCoarsePermissionPreS() {
        lint().files(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg">
                    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
                    <uses-sdk android:targetSdkVersion="30"/>
                    <application
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name" >
                    </application>
                </manifest>
                """
            ).indented()
        ).run().expectClean()
    }

    fun testOnlyCoarsePermission() {
        lint().files(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg">
                    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION"/>

                    <application
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name" >
                    </application>
                </manifest>
                """
            ).indented()
        ).run().expectClean()
    }

    fun testCoarseAndFinePermission() {
        lint().files(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg">
                    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
                    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION"/>
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
