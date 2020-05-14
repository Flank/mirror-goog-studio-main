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

package com.android.tools.idea.wizard.template.impl.activities.composeActivity

import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.impl.activities.common.addAllKotlinDependencies
import com.android.tools.idea.wizard.template.impl.activities.common.generateManifest
import com.android.tools.idea.wizard.template.impl.activities.common.generateNoActionBarStyles
import com.android.tools.idea.wizard.template.impl.activities.composeActivity.src.app_package.mainActivityKt
import com.android.tools.idea.wizard.template.impl.activities.composeActivity.src.app_package.ui.colorKt
import com.android.tools.idea.wizard.template.impl.activities.composeActivity.src.app_package.ui.shapeKt
import com.android.tools.idea.wizard.template.impl.activities.composeActivity.src.app_package.ui.themeKt
import com.android.tools.idea.wizard.template.impl.activities.composeActivity.src.app_package.ui.typeKt

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
  addDependency("com.android.support:appcompat-v7:${moduleData.apis.appCompatVersion}.+")

  val composeVersionVarName = getDependencyVarName("androidx.ui:ui-framework", "compose_version")
  setExtVar(composeVersionVarName, "0.1.0-dev09")

  addDependency(mavenCoordinate = "androidx.ui:ui-framework:\${$composeVersionVarName}")
  addDependency(mavenCoordinate = "androidx.ui:ui-layout:\${$composeVersionVarName}")
  addDependency(mavenCoordinate = "androidx.ui:ui-material:\${$composeVersionVarName}")
  addDependency(mavenCoordinate = "androidx.ui:ui-tooling:\${$composeVersionVarName}")
  generateManifest(
    moduleData, activityClass, activityTitle, packageName, isLauncher, true,
    generateActivityTitle = true
  )
  generateNoActionBarStyles(moduleData.baseFeature?.resDir, resOut, moduleData.themesData)
  val themeName = "${moduleData.themesData.appName}Theme"
  save(mainActivityKt(activityClass, defaultPreview, greeting, packageName, themeName), srcOut.resolve("${activityClass}.kt"))
  save(colorKt(packageName), srcOut.resolve("ui/Color.kt"))
  save(shapeKt(packageName), srcOut.resolve("ui/Shape.kt"))
  save(themeKt(packageName, themeName), srcOut.resolve("ui/Theme.kt"))
  save(typeKt(packageName), srcOut.resolve("ui/Type.kt"))

  requireJavaVersion("1.8", true)
  setBuildFeature("compose", true)

  open(srcOut.resolve("${activityClass}.kt"))
}
