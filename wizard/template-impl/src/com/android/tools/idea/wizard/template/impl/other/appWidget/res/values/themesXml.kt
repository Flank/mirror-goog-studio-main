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
package com.android.tools.idea.wizard.template.impl.other.appWidget.res.values

import com.android.tools.idea.wizard.template.ThemesData

fun themesXml(themesData: ThemesData) =
  """<resources>
    <style name="${getParentAppWidgetTheme(themesData.main.name)}" parent="@android:style/Theme.DeviceDefault">
        <item name="appWidgetRadius">8dp</item>
        <item name="appWidgetPadding">4dp</item>
        <item name="appWidgetInnerRadius">4dp</item>
    </style>

    <style name="${getAppWidgetTheme(themesData.main.name)}"
        parent="${getParentAppWidgetTheme(themesData.main.name)}" />
</resources>
"""

fun getAppWidgetTheme(themeName: String) = "${themeName}.AppWidgetContainer"
fun getParentAppWidgetTheme(themeName: String) = "${themeName}.AppWidgetContainerParent"
