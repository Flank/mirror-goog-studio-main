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
package com.android.tools.idea.wizard.template.impl.other.appWidget.res.values_v29

import com.android.tools.idea.wizard.template.ThemesData
import com.android.tools.idea.wizard.template.impl.other.appWidget.res.values.getParentAppWidgetThemeOverlay

fun themesXml(themesData: ThemesData) =
  """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Having themes.xml for v29 variant because @android:style/Theme.DeviceDefault.DayNight requires API level 29 -->
    <style name="${getParentAppWidgetThemeOverlay(themesData.overlay.name)}" parent="@android:style/Theme.DeviceDefault.DayNight">
        <item name="appWidgetRadius">16dp</item>
        <item name="appWidgetPadding">16dp</item>
        <item name="appWidgetInnerRadius">8dp</item>
    </style>
</resources>
"""
