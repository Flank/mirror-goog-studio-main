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
package com.android.tools.idea.wizard.template.impl.other.appWidget.res.values

import com.android.tools.idea.wizard.template.ThemesData

fun stylesXml(themesData: ThemesData) =
  """<resources>
    <style name="${getAppWidgetContainerStyleName(themesData.appName)}" parent="android:Widget">
        <item name="android:id">@android:id/background</item>
        <item name="android:background">?android:attr/colorBackground</item>
    </style>

    <style name="${getAppWidgetInnerViewStyleName(themesData.appName)}" parent="android:Widget">
        <item name="android:background">?android:attr/colorBackground</item>
        <item name="android:textColor">?android:attr/textColorPrimary</item>
    </style>
</resources>
"""

fun getAppWidgetContainerStyleName(appName: String) = "Widget.${appName}.AppWidget.Container"
fun getAppWidgetInnerViewStyleName(appName: String) = "Widget.${appName}.AppWidget.InnerView"
