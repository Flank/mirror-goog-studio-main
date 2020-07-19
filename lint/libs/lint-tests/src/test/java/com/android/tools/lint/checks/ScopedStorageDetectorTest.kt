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

class ScopedStorageDetectorTest : AbstractCheckTest() {

    override fun getDetector(): Detector = ScopedStorageDetector()

    fun testWriteExternalStorage() {
        lint().files(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.pkg">
                    <uses-sdk android:targetSdkVersion="29"/>
                    <uses-permission/><!-- Test for NPEs -->
                    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/><!-- ERROR -->
                </manifest>
                """
            ).indented()
        )
            .run()
            .expect(
                """
                    AndroidManifest.xml:4: Warning: WRITE_EXTERNAL_STORAGE no longer provides write access when targeting Android 10, unless you use requestLegacyExternalStorage [ScopedStorage]
                        <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/><!-- ERROR -->
                                                       ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                    0 errors, 1 warnings
                """
            )
    }

    fun testManageExternalStorage() {
        lint().files(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.pkg">
                    <uses-sdk android:targetSdkVersion="29"/>
                    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/><!-- OK; permission below takes priority. -->
                    <uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE"/><!-- ERROR -->
                </manifest>
                """
            ).indented()
        )
            .run()
            .expect(
                """
                    AndroidManifest.xml:4: Warning: Most apps are not allowed to use MANAGE_EXTERNAL_STORAGE [ScopedStorage]
                        <uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE"/><!-- ERROR -->
                                                       ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                    0 errors, 1 warnings
                """
            )
    }

    fun testAndroid11() {
        lint().files(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.pkg">
                    <uses-sdk android:targetSdkVersion="30"/>
                    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/><!-- ERROR -->
                </manifest>
                """
            ).indented()
        )
            .run()
            .expect(
                """
                    AndroidManifest.xml:3: Warning: WRITE_EXTERNAL_STORAGE no longer provides write access when targeting Android 10+ [ScopedStorage]
                        <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/><!-- ERROR -->
                                                       ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                    0 errors, 1 warnings
                """
            )
    }

    fun testAndroid11Legacy() {
        lint().files(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.pkg">
                    <uses-sdk android:targetSdkVersion="30"/>
                    <application android:requestLegacyExternalStorage="true"/>
                    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/><!-- ERROR -->
                </manifest>
                """
            ).indented()
        )
            .run()
            .expect(
                """
                    AndroidManifest.xml:4: Warning: WRITE_EXTERNAL_STORAGE no longer provides write access when targeting Android 11+, even when using requestLegacyExternalStorage [ScopedStorage]
                        <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/><!-- ERROR -->
                                                       ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                    0 errors, 1 warnings
                """
            )
    }

    fun testAndroid10Legacy() {
        lint().files(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.pkg">
                    <uses-sdk android:targetSdkVersion="29"/>
                    <application android:requestLegacyExternalStorage="true"/>
                    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/><!-- OK -->
                </manifest>
                """
            ).indented()
        )
            .run()
            .expectClean()
    }

    fun testLowSdk() {
        lint().files(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.pkg">
                    <uses-sdk android:targetSdkVersion="28"/>
                    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/><!-- OK -->
                </manifest>
                """
            ).indented()
        )
            .run()
            .expectClean()
    }
}
