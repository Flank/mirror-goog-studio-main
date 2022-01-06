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

class WatchFaceForAndroidXDetectorTest : AbstractCheckTest() {
    override fun getDetector() = WatchFaceForAndroidXDetector()

    fun testDocumentationExample() {
        lint().files(
            gradle(
                """
                apply plugin: 'com.android.application'

                dependencies {
                    implementation "androidx.wear.watchface:watchface:1.2.3"
                }
                """
            ).indented(),
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="androidx.wear.watchface.samples.minimal.complications">

                    <meta-data
                        android:name="com.google.android.wearable.watchface.wearableConfigurationAction"
                        android:value="androidx.wear.watchface.editor.action.SOME_OTHER_EDITOR"
                    />
                </manifest>
                          """
            ).indented()
        ).run().expect(
            """
            src/main/AndroidManifest.xml:6: Warning: Watch face configuration action must be set to WATCH_FACE_EDITOR for an AndroidX watch face [WatchFaceForAndroidX]
                    android:value="androidx.wear.watchface.editor.action.SOME_OTHER_EDITOR"
                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
        """
        ).verifyFixes()
            .expectFixDiffs(
                """
                Fix for src/main/AndroidManifest.xml line 6: Set value="androidx.wear.watchface.editor.action.WATCH_FACE_EDITOR":
                @@ -7 +7
                -         android:value="androidx.wear.watchface.editor.action.SOME_OTHER_EDITOR" />
                +         android:value="androidx.wear.watchface.editor.action.WATCH_FACE_EDITOR" />
                """
            )
    }

    fun testNoAndroidXDependency() {
        // Wrong launch mode
        lint().files(
            gradle(
                """
                apply plugin: 'com.android.application'

                dependencies {
                    implementation "androidx.compose:compose-compiler"
                }
                """
            ).indented(),
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="androidx.wear.watchface.samples.minimal.complications">

                    <meta-data
                        android:name="com.google.android.wearable.watchface.wearableConfigurationAction"
                        android:value="androidx.wear.watchface.editor.action.SOME_OTHER_EDITOR"
                    />
                </manifest>
                          """
            ).indented()
        ).run().expectClean()
    }

    fun testMissingAttribute() {
        lint().files(
            gradle(
                """
                apply plugin: 'com.android.application'

                dependencies {
                    implementation "androidx.wear.watchface:watchface:1.0.0-alpha22"
                }
                """
            ).indented(),
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="androidx.wear.watchface.samples.minimal.complications">
                  <intent-filter>
                    <action android:name="androidx.wear.watchface.editor.action.WATCH_FACE_EDITOR" />
                    <!-- NO CATEGORY -->
                    <category android:name="android.intent.category.DEFAULT" />
                  </intent-filter>

                    <service
                        android:name=".WatchFaceService"
                        android:directBootAware="true"
                        android:exported="true"
                        android:label="@string/app_name"
                        android:permission="android.permission.BIND_WALLPAPER">

                      <intent-filter>
                        <action android:name="android.service.wallpaper.WallpaperService" />
                        <category android:name="com.google.android.wearable.watchface.category.WATCH_FACE" />
                      </intent-filter>

                      <meta-data
                          android:name="com.google.android.wearable.watchface.wearableConfigurationAction"
                        />

                    </service>
                </manifest>
                          """
            ).indented()
        ).run().expect(
            """
            src/main/AndroidManifest.xml:21: Warning: Watch face configuration action must be set to WATCH_FACE_EDITOR for an AndroidX watch face [WatchFaceForAndroidX]
                  <meta-data
                  ^
            0 errors, 1 warnings
            """
        ).verifyFixes()
            .expectFixDiffs(
                """
                Fix for src/main/AndroidManifest.xml line 21: Set value="androidx.wear.watchface.editor.action.WATCH_FACE_EDITOR":
                @@ -23 +23
                -         <meta-data android:name="com.google.android.wearable.watchface.wearableConfigurationAction" />
                +         <meta-data
                +             android:name="com.google.android.wearable.watchface.wearableConfigurationAction"
                +             android:value="androidx.wear.watchface.editor.action.WATCH_FACE_EDITOR" />
                """
            )
    }

    fun testNoIssues() {
        lint().files(
            gradle(
                """
                apply plugin: 'com.android.application'

                dependencies {
                    implementation "androidx.wear.watchface:watchface:1.0.0-alpha22"
                }
                """
            ).indented(),
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="androidx.wear.watchface.samples.minimal.complications">

                  <uses-feature android:name="android.hardware.type.watch" />

                  <uses-permission android:name="android.permission.WAKE_LOCK" />
                  <uses-permission
                      android:name="com.google.android.wearable.permission.RECEIVE_COMPLICATION_DATA" />

                  <application
                      android:allowBackup="true"
                      android:icon="@mipmap/ic_launcher"
                      android:label="@string/app_name"
                      android:supportsRtl="true"
                      android:theme="@android:style/Theme.DeviceDefault"
                      android:fullBackupContent="false">

                    <activity
                        android:name=".ConfigActivity"
                        android:label="@string/configuration_title">
                      <intent-filter>
                        <action android:name="androidx.wear.watchface.editor.action.WATCH_FACE_EDITOR" />

                        <category
                            android:name="com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION" />
                        <category android:name="android.intent.category.DEFAULT" />
                      </intent-filter>
                    </activity>

                    <service
                        android:name=".WatchFaceService"
                        android:directBootAware="true"
                        android:exported="true"
                        android:label="@string/app_name"
                        android:permission="android.permission.BIND_WALLPAPER">

                      <intent-filter>
                        <action android:name="android.service.wallpaper.WallpaperService" />
                        <category android:name="com.google.android.wearable.watchface.category.WATCH_FACE" />
                      </intent-filter>

                      <meta-data
                          android:name="com.google.android.wearable.watchface.preview"
                          android:resource="@drawable/preview" />

                      <meta-data
                          android:name="android.service.wallpaper"
                          android:resource="@xml/watch_face" />

                      <meta-data
                          android:name="com.google.android.wearable.watchface.wearableConfigurationAction"
                          android:value="androidx.wear.watchface.editor.action.WATCH_FACE_EDITOR" />

                      <meta-data
                          android:name="com.google.android.wearable.watchface.companionBuiltinConfigurationEnabled"
                          android:value="true" />

                    </service>

                  </application>

                </manifest>
                """
            ).indented()
        ).run().expectClean()
    }
}
