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


import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.impl.activities.common.addAllKotlinDependencies
import com.android.tools.idea.wizard.template.impl.activities.common.generateManifest
import com.android.tools.idea.wizard.template.impl.activities.common.generateNoActionBarStyles
import com.android.tools.idea.wizard.template.impl.activities.composeActivity.src.app_package.mainActivityKt

fun RecipeExecutor.composeActivityRecipe(
  moduleData: ModuleTemplateData,
  activityClass: String,
  activityTitle: String,
  packageName: String,
  isLauncher: Boolean,
  greeting: String,
  defaultPreview: String
) {

  val (_, srcOut, resOut, _) = moduleData
  addAllKotlinDependencies(moduleData)

  addDependency(mavenCoordinate = "androidx.ui:ui-framework:+", minRev = "0.1.0-dev03")
  addDependency(mavenCoordinate = "androidx.ui:ui-layout:+", minRev = "0.1.0-dev03")
  addDependency(mavenCoordinate = "androidx.ui:ui-material:+", minRev = "0.1.0-dev03")
  addDependency(mavenCoordinate = "androidx.ui:ui-tooling:+", minRev = "0.1.0-dev03")
  generateManifest(
    moduleData, activityClass, activityTitle, packageName, isLauncher, true,
    requireTheme = false, generateActivityTitle = true, useMaterial2 = true
  )
  generateNoActionBarStyles(moduleData.baseFeature?.resDir, resOut, moduleData.themesData)
  save(mainActivityKt(activityClass, defaultPreview, greeting, packageName), srcOut.resolve("${activityClass}.kt"))

  requireJavaVersion("1.8", true)
  setBuildFeature("compose", true)

  open(srcOut.resolve("${activityClass}.kt"))
}
