/*
 * Copyright (C) 2012 The Android Open Source Project
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

class HardcodedDebugModeDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return HardcodedDebugModeDetector()
    }

    fun test() {
        lint().files(
            manifest(
                """

                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="foo.bar2"
                    android:versionCode="1"
                    android:versionName="1.0" >

                    <uses-sdk android:minSdkVersion="14" />

                    <application
                        android:debuggable="true"
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name" >
                        <activity
                            android:label="@string/app_name"
                            android:name=".Foo2Activity" >
                            <intent-filter >
                                <action android:name="android.intent.action.MAIN" />

                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>
                    </application>

                </manifest>
                """
            ).indented()
        ).run().expect(
            """
            AndroidManifest.xml:10: Error: Avoid hardcoding the debug mode; leaving it out allows debug and release builds to automatically assign one [HardcodedDebugMode]
                    android:debuggable="true"
                    ~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        )
    }

    fun testOk() {
        lint().files(manifest().minSdk(14)).run().expectClean()
    }

    fun testNoNS() {
        //noinspection all // Sample code
        lint().files(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="foo.bar2"
                    android:versionCode="1"
                    android:versionName="1.0" >

                    <application
                        debuggable="true" >
                    </application>

                </manifest>
                """
            ).indented()
        ).run().expectClean()
    }
}
