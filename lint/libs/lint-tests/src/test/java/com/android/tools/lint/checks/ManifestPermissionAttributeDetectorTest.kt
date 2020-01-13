/*
 * Copyright (C) 2016 The Android Open Source Project
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

class ManifestPermissionAttributeDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return ManifestPermissionAttributeDetector()
    }

    fun testWrongTagPermissions1() {
        lint().files(
            manifest(
                """

                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                          package="foo.bar2"
                          android:versionCode="1"
                          android:versionName="1.0">

                    <uses-sdk android:minSdkVersion="14"/>

                    <application
                            android:icon="@drawable/ic_launcher"
                            android:label="@string/app_name"
                            android:permission="android.permission.READ_CONTACTS">
                        <activity
                                android:label="@string/app_name"
                                android:name="com.sample.service.serviceClass"
                                android:permission="android.permission.CAMERA">
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN"
                                        android:permission="android.permission.READ_CONTACTS"/>

                                <category android:name="android.intent.category.LAUNCHER"
                                          android:permission="android.permission.SET_WALLPAPER"/>
                            </intent-filter>
                        </activity>
                    </application>

                </manifest>

                """
            ).indented()
        ).run().expect(
            """
            AndroidManifest.xml:19: Error: Protecting an unsupported element with a permission is a no-op and potentially dangerous [InvalidPermission]
                                    android:permission="android.permission.READ_CONTACTS"/>
                                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            AndroidManifest.xml:22: Error: Protecting an unsupported element with a permission is a no-op and potentially dangerous [InvalidPermission]
                                      android:permission="android.permission.SET_WALLPAPER"/>
                                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            2 errors, 0 warnings
            """
        )
    }

    fun testWrongTagPermissions2() {
        lint().files(
            manifest(
                """

                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                          package="foo.bar2"
                          android:versionCode="1"
                          android:versionName="1.0">

                    <uses-sdk android:minSdkVersion="11"/>

                    <uses-permission
                            android:name="android.permission.WRITE_EXTERNAL_STORAGE"
                            android:maxSdkVersion="18"/>

                    <application
                            android:icon="@drawable/ic_launcher"
                            android:label="@string/app_name">
                        <activity
                                android:label="@string/app_name"
                                android:name="com.sample.service.serviceClass"
                                android:permission="android.permission.CAMERA">
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN"/>

                                <category android:name="android.intent.category.LAUNCHER"/>
                            </intent-filter>
                        </activity>
                    </application>

                </manifest>

                """
            ).indented()
        ).run().expectClean()
    }
}
