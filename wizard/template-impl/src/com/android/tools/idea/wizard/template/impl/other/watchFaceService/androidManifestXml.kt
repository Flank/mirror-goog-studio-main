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

package com.android.tools.idea.wizard.template.impl.other.watchFaceService


fun androidManifestXml(
  packageName: String,
  serviceClass: String,
  watchFaceStyle: WatchFaceStyle
): String {
  val labelBlock = when (watchFaceStyle) {
    WatchFaceStyle.Analog -> "android:label=\"@string/my_analog_name\""
    WatchFaceStyle.Digital -> "android:label=\"@string/my_digital_name\""
  }
  val previewBlock = when (watchFaceStyle) {
    WatchFaceStyle.Analog -> "android:resource=\"@drawable/preview_analog\" />"
    WatchFaceStyle.Digital -> "android:resource=\"@drawable/preview_digital\" />"
  }
  val previewCircularBlock = when (watchFaceStyle) {
    WatchFaceStyle.Analog -> "android:resource=\"@drawable/preview_analog\" />"
    WatchFaceStyle.Digital -> "android:resource=\"@drawable/preview_digital_circular\" />"
  }

  return """
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-feature android:name="android.hardware.type.watch" />

    <application>

	<meta-data android:name="com.google.android.wearable.standalone" android:value="true"/>

        <service
            android:name="${packageName}.${serviceClass}"
            $labelBlock
            android:permission="android.permission.BIND_WALLPAPER" >
            <!--
            By default, Watchfaces on rectangular devices will be run in a emulation mode where they
            are provided a square surface to draw the watchface (allows watchfaces built for
            circular and square devices to work well).

            For this watchface, we explicitly enable rectangular devices, so we get the complete
            surface.
            -->
            <meta-data
                android:name="android.service.wallpaper.square_mode"
                android:value="false" />
            <meta-data
                android:name="android.service.wallpaper"
                android:resource="@xml/watch_face" />
            <meta-data
                android:name="com.google.android.wearable.watchface.preview"
                $previewBlock
            <meta-data
                android:name="com.google.android.wearable.watchface.preview_circular"
                $previewCircularBlock
            <intent-filter>
                <action android:name="android.service.wallpaper.WallpaperService" />
                <category android:name="com.google.android.wearable.watchface.category.WATCH_FACE" />
            </intent-filter>
        </service>

        <meta-data
            android:name="com.google.android.gms.version"
            android:value="@integer/google_play_services_version" />
    </application>
</manifest>
"""
}
