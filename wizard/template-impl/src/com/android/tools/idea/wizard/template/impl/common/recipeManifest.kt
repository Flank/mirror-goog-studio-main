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

import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.ThemeData
import java.io.File

fun RecipeExecutor.recipeManifest(
  moduleData: ModuleTemplateData,
  activityClass: String,
  activityTitle: String,
  packageName: String,
  isLauncher: Boolean,
  hasNoActionBar: Boolean,
  noActionBarTheme: ThemeData = moduleData.themesData.noActionBar,
  isNew: Boolean = moduleData.isNew,
  isLibrary: Boolean = moduleData.isLibrary,
  mainTheme: ThemeData = moduleData.themesData.main,
  manifestOut: File = moduleData.manifestDir,
  resOut: File = moduleData.resDir,
  requireTheme: Boolean,
  generateActivityTitle: Boolean,
  useMaterial2: Boolean,
  isDynamicFeature: Boolean = false
) {
  if (requireTheme) {
    recipeTheme(
      themeData = mainTheme,
      isDynamicFeature = isDynamicFeature,
      useMaterial2 = useMaterial2,
      resOut = resOut,
      baseFeatureResOut = resOut
    )
  }

  recipeManifestStrings(activityClass, activityTitle, resOut, resOut, isNew, generateActivityTitle, isDynamicFeature)

  val manifest = androidManifestXml(isNew, hasNoActionBar, packageName, activityClass, isLauncher, isLibrary, mainTheme, noActionBarTheme, generateActivityTitle)

  mergeXml(manifest, manifestOut.resolve("AndroidManifest.xml"))
}