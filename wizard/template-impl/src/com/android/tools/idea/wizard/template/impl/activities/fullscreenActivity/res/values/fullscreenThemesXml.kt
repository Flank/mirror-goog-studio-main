/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.wizard.template.impl.activities.fullscreenActivity.res.values

import com.android.tools.idea.wizard.template.impl.MaterialColor.*

fun fullscreenThemes(themeName: String) =
  """<resources>
   <style name="${getFullscreenTheme(themeName)}" parent="${themeName}">
        <item name="android:actionBarStyle">@style/${getFullscreenActionBarStyle(themeName)}</item>
        <item name="android:windowActionBarOverlay">true</item>
        <item name="android:windowBackground">@null</item>
    </style>

    <style name="${getFullscreenContainerThemeOverlay(themeName)}" parent="">
        <item name="fullscreenBackgroundColor">@color/${LIGHT_BLUE_600.colorName}</item>
        <item name="fullscreenTextColor">@color/${LIGHT_BLUE_A200.colorName}</item>
    </style>
</resources>
"""

fun getFullscreenTheme(themeName: String) = "${themeName}.Fullscreen"
fun getFullscreenContainerThemeOverlay(themeName: String) = "ThemeOverlay.${themeName}.FullscreenContainer"
