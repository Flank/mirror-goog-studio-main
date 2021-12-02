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

class WearableConfigurationActionDetectorTest : AbstractCheckTest() {
    override fun getDetector() = WearableConfigurationActionDetector()

    companion object {
        private val GRADLE_WATCHFACE_DEPENDENCY = gradle(
            """
                apply plugin: 'com.android.application'

                dependencies {
                    implementation "androidx.wear.watchface:watchface:1.2.3"
                }
                """
        ).indented()
    }

    fun testDocumentationExample() {
        lint().files(
            GRADLE_WATCHFACE_DEPENDENCY,
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
                          android:value="androidx.wear.watchface.editor.action.WATCH_FACE_EDITOR" />

                    </service>
                </manifest>
                """
            ).indented()
        ).run().expect(
            """
            src/main/AndroidManifest.xml:4: Warning: Watch face configuration tag is required [WearableConfigurationAction]
                <action android:name="androidx.wear.watchface.editor.action.WATCH_FACE_EDITOR" />
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }

    fun testNoAndroidXDependency() {
        lint().files(
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
                          android:value="androidx.wear.watchface.editor.action.WATCH_FACE_EDITOR" />

                    </service>
                </manifest>
                """
            ).indented()
        ).run().expectClean()
    }

    fun testMetaDataMissing() {
        lint().files(
            GRADLE_WATCHFACE_DEPENDENCY,
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="androidx.wear.watchface.samples.minimal.complications">
                  <uses-sdk android:minSdkVersion="29"/>

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

                      <!-- NO CATEGORY -->
                      <meta-data
                          android:name="com.google.android.wearable.watchface.wearableSomethingElse"
                          android:value="androidx.wear.watchface.editor.action.WATCH_FACE_EDITOR" />

                    </service>

                  </application>

                </manifest>                    """
            ).indented()
        ).run().expect(
            """
            src/main/AndroidManifest.xml:17: Warning: wearableConfigurationAction metadata is missing [WearableConfigurationAction]
                    <action android:name="androidx.wear.watchface.editor.action.WATCH_FACE_EDITOR" />
                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }

    fun testMinSdk30() {
        lint().files(
            GRADLE_WATCHFACE_DEPENDENCY,
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="androidx.wear.watchface.samples.minimal.complications">
                  <uses-sdk android:minSdkVersion="30"/>

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

                      <!-- NO CATEGORY -->
                      <meta-data
                          android:name="com.google.android.wearable.watchface.wearableSomethingElse"
                          android:value="androidx.wear.watchface.editor.action.WATCH_FACE_EDITOR" />

                    </service>

                  </application>

                </manifest>                    """
            ).indented()
        ).run().expectClean()
    }

    fun testActionMissing() {
        lint().files(
            GRADLE_WATCHFACE_DEPENDENCY,
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="androidx.wear.watchface.samples.minimal.complications">

                    <meta-data
                      android:name="com.google.android.wearable.watchface.wearableConfigurationAction"
                      android:value="androidx.wear.watchface.editor.action.WATCH_FACE_EDITOR" />

                  <intent-filter>
                    <!-- NO ACTION -->
                    <category
                        android:name="com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION" />
                    <category android:name="android.intent.category.DEFAULT" />
                  </intent-filter>
                </manifest>
                """
            ).indented()
        ).run().expect(
            """
            src/main/AndroidManifest.xml:5: Warning: Watch face configuration activity is missing [WearableConfigurationAction]
                  android:name="com.google.android.wearable.watchface.wearableConfigurationAction"
                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }

    fun testNoWatchFaceDependency() {
        lint().files(
            // no GRADLE_WATCHFACE_DEPENDENCY
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="androidx.wear.watchface.samples.minimal.complications">

                    <meta-data
                      android:name="com.google.android.wearable.watchface.wearableConfigurationAction"
                      android:value="androidx.wear.watchface.editor.action.WATCH_FACE_EDITOR" />

                  <intent-filter>
                    <!-- NO ACTION -->
                    <category
                        android:name="com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION" />
                    <category android:name="android.intent.category.DEFAULT" />
                  </intent-filter>
                </manifest>
                """
            ).indented()
        ).run().expectClean()
    }

    fun testMultiProject() {
        val lib1 = project(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg.app">
                  <meta-data
                      android:name="com.google.android.wearable.watchface.wearableConfigurationAction"
                      android:value="androidx.wear.watchface.editor.action.WATCH_FACE_EDITOR" />
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

                    </application>
                </manifest>
                """
            ).indented(),
            GRADLE_WATCHFACE_DEPENDENCY
        ).dependsOn(lib1)
        lint().projects(app).run().expectClean()
    }

    fun testMultiProjectWithIssues() {
        val lib1 = project(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg.app">
                  <meta-data
                      android:name="com.google.android.wearable.watchface.wearableConfigurationAction"
                      android:value="androidx.wear.watchface.editor.action.WATCH_FACE_EDITOR" />
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
            ).indented(),
            GRADLE_WATCHFACE_DEPENDENCY,
        ).dependsOn(lib1)
        lint().projects(app).run().expect(
            """
            ../lib/AndroidManifest.xml:4: Warning: Watch face configuration activity is missing [WearableConfigurationAction]
                  android:name="com.google.android.wearable.watchface.wearableConfigurationAction"
                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }

    fun testCorrectWearableConfiguration() {
        lint().files(
            GRADLE_WATCHFACE_DEPENDENCY,
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="androidx.wear.watchface.samples.minimal.style">

                  <uses-feature android:name="android.hardware.type.watch" />

                  <uses-permission android:name="android.permission.WAKE_LOCK" />

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
