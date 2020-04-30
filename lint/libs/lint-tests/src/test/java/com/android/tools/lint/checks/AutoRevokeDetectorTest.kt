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

class AutoRevokeDetectorTest : AbstractCheckTest() {

    override fun getDetector(): Detector = AutoRevokeDetector()

    fun testClean() {
        lint().files(
            manifest(
                """<?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.pkg">
                    <uses-sdk android:targetSdkVersion="30"/>
                    <application android:autoRevokePermissions="discouraged"/><!-- OK -->
                </manifest>
                """
            ).indented()
        )
            .run()
            .expectClean()
    }

    fun testTargetSdkTooLow() {
        lint().files(
            manifest(
                """<?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.pkg">
                    <uses-sdk android:targetSdkVersion="29"/>
                    <application/><!-- OK -->
                </manifest>
                """
            ).indented()
        )
            .run()
            .expectClean()
    }

    fun testMissingToleranceDeclaration() {
        lint().files(
            manifest(
                """<?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.pkg">
                    <uses-sdk android:targetSdkVersion="30"/>
                    <application/><!-- ERROR -->
                </manifest>
                """
            )
        )
            .run()
            .expect(
                """
                AndroidManifest.xml:4: Warning: Missing required attribute: autoRevokePermissions [MissingAutoRevokeTolerance]
                                    <application/><!-- ERROR -->
                                     ~~~~~~~~~~~
                0 errors, 1 warnings
                """
            )
            .expectFixDiffs(
                """
                Fix for AndroidManifest.xml line 4: Set autoRevokePermissions="allowed":
                @@ -7 +7
                -     <application /> <!-- ERROR -->
                +     <application android:autoRevokePermissions="allowed" /> <!-- ERROR -->
                Fix for AndroidManifest.xml line 4: Set autoRevokePermissions="discouraged":
                @@ -7 +7
                -     <application /> <!-- ERROR -->
                +     <application android:autoRevokePermissions="discouraged" /> <!-- ERROR -->
                """
            )
    }

    fun testDisallowingAutoRevoke() {
        lint().files(
            manifest(
                """<?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.pkg">
                    <uses-sdk android:targetSdkVersion="30"/>
                    <application android:autoRevokePermissions="disallowed"/><!-- ERROR -->
                </manifest>
                """
            )
        )
            .run()
            .expect(
                """
                AndroidManifest.xml:4: Warning: Most apps should not require autoRevokePermissions="disallowed" [DisabledAutoRevoke]
                                    <application android:autoRevokePermissions="disallowed"/><!-- ERROR -->
                                                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 1 warnings
                """
            )
            .expectFixDiffs(
                """
                Fix for AndroidManifest.xml line 4: Set autoRevokePermissions="discouraged":
                @@ -7 +7
                -     <application android:autoRevokePermissions="disallowed" /> <!-- ERROR -->
                +     <application android:autoRevokePermissions="discouraged" /> <!-- ERROR -->
                Fix for AndroidManifest.xml line 4: Set autoRevokePermissions="allowed":
                @@ -7 +7
                -     <application android:autoRevokePermissions="disallowed" /> <!-- ERROR -->
                +     <application android:autoRevokePermissions="allowed" /> <!-- ERROR -->
                """
            )
    }
}
