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
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.Configuration
import android.content.res.Configuration as AndroidResConfiguration

fun AndroidResConfiguration.convert(stringTable: StringTable): Configuration =
    Configuration.newBuilder().apply(this, stringTable).build()

private fun Configuration.Builder.apply(
    config: AndroidResConfiguration,
    stringTable: StringTable
): Configuration.Builder {
    fontScale = config.fontScale
    countryCode = config.mcc
    networkCode = config.mnc
    screenLayout = config.screenLayout
    colorMode = config.colorMode
    touchScreen = config.touchscreen
    keyboard = config.keyboard
    keyboardHidden = config.keyboardHidden
    hardKeyboardHidden = config.hardKeyboardHidden
    navigation = config.navigation
    navigationHidden = config.navigationHidden
    uiMode = config.uiMode
    smallestScreenWidthDp = config.smallestScreenWidthDp
    density = config.densityDpi
    orientation = config.orientation
    screenWidthDp = config.screenWidthDp
    screenHeightDp = config.screenHeightDp

    if (!config.locales.isEmpty) {
        locale = config.locales[0].convert(stringTable)
    }
    return this
}
