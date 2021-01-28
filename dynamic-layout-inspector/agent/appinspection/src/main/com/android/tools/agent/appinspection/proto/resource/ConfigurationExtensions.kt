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

package com.android.tools.agent.appinspection.proto.resource

import com.android.tools.agent.appinspection.proto.StringTable
import com.android.tools.agent.appinspection.proto.convert
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Configuration
import android.content.res.Configuration as AndroidResConfiguration

fun AndroidResConfiguration.convert(stringTable: StringTable): Configuration {
    val self = this
    val mainLocale = if (!locales.isEmpty) locales[0] else null
    return Configuration.newBuilder().apply {
        fontScale = self.fontScale
        countryCode = self.mcc
        networkCode = self.mnc
        screenLayout = self.screenLayout
        colorMode = self.colorMode
        touchScreen = self.touchscreen
        keyboard = self.keyboard
        keyboardHidden = self.keyboardHidden
        hardKeyboardHidden = self.hardKeyboardHidden
        navigation = self.navigation
        navigationHidden = self.navigationHidden
        uiMode = self.uiMode
        smallestScreenWidth = self.smallestScreenWidthDp
        density = self.densityDpi
        orientation = self.orientation
        screenWidth = self.screenWidthDp
        screenHeight = self.screenHeightDp

        if (mainLocale != null) {
            locale = mainLocale.convert(stringTable)
        }
    }.build()
}
