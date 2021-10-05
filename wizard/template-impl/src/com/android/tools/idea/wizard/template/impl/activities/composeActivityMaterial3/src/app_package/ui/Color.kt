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

package com.android.tools.idea.wizard.template.impl.activities.composeActivityMaterial3.src.app_package.ui

import com.android.tools.idea.wizard.template.MaterialColor
import com.android.tools.idea.wizard.template.escapeKotlinIdentifier

fun colorKt(
  packageName: String
) = """
package ${escapeKotlinIdentifier(packageName)}.ui.theme

import androidx.compose.ui.graphics.Color

${MaterialColor.PURPLE_80.kotlinComposeVal()}
${MaterialColor.PURPLE_GREY_80.kotlinComposeVal()}
${MaterialColor.PINK_80.kotlinComposeVal()}

${MaterialColor.PURPLE_40.kotlinComposeVal()}
${MaterialColor.PURPLE_GREY_40.kotlinComposeVal()}
${MaterialColor.PINK_40.kotlinComposeVal()}
"""
