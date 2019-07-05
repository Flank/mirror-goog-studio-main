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
package com.android.tools.idea.wizard.template.impl.common

import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.ThemeData
import com.android.tools.idea.wizard.template.impl.common.res.values.themeStyles
import java.io.File

fun RecipeExecutor.recipeTheme(
  themeData: ThemeData, isDynamicFeature: Boolean, useMaterial2: Boolean, resOut: File, baseFeatureResOut: File
) {
  if (!themeData.exists) {
    if (isDynamicFeature) {
      mergeXml(themeStyles(themeData.name, useMaterial2), baseFeatureResOut.resolve("values/styles.xml"))
    }
    else {
      mergeXml(themeStyles(themeData.name, useMaterial2), resOut.resolve("values/styles.xml"))
    }
  }
}
