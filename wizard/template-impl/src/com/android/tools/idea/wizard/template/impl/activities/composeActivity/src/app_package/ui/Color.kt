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

package com.android.tools.idea.wizard.template.impl.activities.composeActivity.src.app_package.ui

import com.android.tools.idea.wizard.template.MaterialColor
import com.android.tools.idea.wizard.template.escapeKotlinIdentifier

fun colorKt(
  packageName: String
) = """
package ${escapeKotlinIdentifier(packageName)}.ui

import androidx.compose.ui.graphics.Color

${MaterialColor.PURPLE_200.kotlinVal()}
${MaterialColor.PURPLE_500.kotlinVal()}
${MaterialColor.PURPLE_700.kotlinVal()}
${MaterialColor.TEAL_200.kotlinVal()}
"""