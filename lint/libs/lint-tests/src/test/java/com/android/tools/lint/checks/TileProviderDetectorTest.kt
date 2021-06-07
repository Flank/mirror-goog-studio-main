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

class TileProviderDetectorTest : AbstractCheckTest() {
    override fun getDetector() = TileProviderDetector()

    fun testDocumentationExample() {
        // Missing permission
        lint().files(
            xml(
                "AndroidManifest.xml",
                """
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                        <application
                            android:icon="@drawable/ic_launcher"
                            android:label="@string/app_name" >
                        </application>
                        <service
                            android:name=".MyTileProvider">
                            <intent-filter>
                                <action android:name="androidx.wear.tiles.action.BIND_TILE_PROVIDER" />
                            </intent-filter>
                        </service>
                    </manifest>"""
            )
                .indented()
        ).run()
            .expect(
                """
                    AndroidManifest.xml:7: Warning: TileProvider does not specify BIND_TILE_PROVIDER permission [TileProviderPermissions]
                        <service
                         ~~~~~~~
                    0 errors, 1 warnings"""
            )
            .verifyFixes()
            .window(1)
            .expectFixDiffs(
                """
                    Fix for AndroidManifest.xml line 7: Add BIND_TILE_PROVIDER permission:
                    @@ -10 +10

                    -     <service android:name=".MyTileProvider" >
                    +     <service
                    +         android:name=".MyTileProvider"
                    +         android:permission="com.google.android.wearable.permission.BIND_TILE_PROVIDER" >
                              <intent-filter>"""
            )
    }

    fun testWrongPermission() {
        lint().files(
            xml(
                "AndroidManifest.xml",
                """
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                        <application
                            android:icon="@drawable/ic_launcher"
                            android:label="@string/app_name" >
                        </application>
                        <service
                            android:name=".MyTileProvider"
                            android:permission="not.the.right.PERMISSION">
                            <intent-filter>
                                <action android:name="androidx.wear.tiles.action.BIND_TILE_PROVIDER" />
                            </intent-filter>
                        </service>
                    </manifest>"""
            )
                .indented()
        ).run()
            .expect(
                """
                    AndroidManifest.xml:7: Warning: TileProvider does not specify BIND_TILE_PROVIDER permission [TileProviderPermissions]
                        <service
                         ~~~~~~~
                    0 errors, 1 warnings"""
            )
            .verifyFixes()
            .window(1)
            .expectFixDiffs(
                """
                    Fix for AndroidManifest.xml line 7: Change permission to BIND_TILE_PROVIDER:
                    @@ -12 +12
                              android:name=".MyTileProvider"
                    -         android:permission="not.the.right.PERMISSION" >
                    +         android:permission="com.google.android.wearable.permission.BIND_TILE_PROVIDER" >
                              <intent-filter>"""
            )
    }

    fun testCorrectPermission() {
        lint().files(
            xml(
                "AndroidManifest.xml",
                """
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                        <application
                            android:icon="@drawable/ic_launcher"
                            android:label="@string/app_name" >
                        </application>
                        <service
                            android:name=".MyTileProvider"
                            android:permission="com.google.android.wearable.permission.BIND_TILE_PROVIDER">
                            <intent-filter>
                                <action android:name="androidx.wear.tiles.action.BIND_TILE_PROVIDER" />
                            </intent-filter>
                        </service>
                    </manifest>"""
            )
                .indented()
        ).run().expectClean()
    }
}
