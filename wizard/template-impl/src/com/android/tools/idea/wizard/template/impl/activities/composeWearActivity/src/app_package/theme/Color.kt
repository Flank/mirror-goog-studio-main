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

package com.android.tools.idea.wizard.template.impl.activities.composeWearActivity.src.app_package.theme

import com.android.tools.idea.wizard.template.MaterialColor
import com.android.tools.idea.wizard.template.escapeKotlinIdentifier

fun colorKt(
  packageName: String
) = """
package ${escapeKotlinIdentifier(packageName)}.presentation.theme

import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors

${MaterialColor.PURPLE_200.kotlinComposeVal()}
${MaterialColor.PURPLE_500.kotlinComposeVal()}
${MaterialColor.PURPLE_700.kotlinComposeVal()}
${MaterialColor.TEAL_200.kotlinComposeVal()}
${MaterialColor.RED_400.kotlinComposeVal()}

internal val wearColorPalette: Colors = Colors(
    primary = Purple200,
    primaryVariant = Purple700,
    secondary = Teal200,
    secondaryVariant = Teal200,
    error = Red400,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onError = Color.Black
)
"""
