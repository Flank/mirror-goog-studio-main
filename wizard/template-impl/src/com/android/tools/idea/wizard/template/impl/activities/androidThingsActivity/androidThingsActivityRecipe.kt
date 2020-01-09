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

package com.android.tools.idea.wizard.template.impl.activities.androidThingsActivity

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.impl.activities.androidThingsActivity.src.app_package.simpleActivityJava
import com.android.tools.idea.wizard.template.impl.activities.androidThingsActivity.src.app_package.simpleActivityKt
import com.android.tools.idea.wizard.template.impl.activities.common.addAllKotlinDependencies
import com.android.tools.idea.wizard.template.impl.activities.common.generateSimpleLayout

fun RecipeExecutor.androidThingsActivityRecipe(
  moduleData: ModuleTemplateData,
  activityClass: String,
  isThingsLauncher: Boolean,
  generateLayout: Boolean,
  layoutName: String,
  packageName: String
) {
  val (projectData, srcOut, resOut, manifestOut) = moduleData
  val buildApi = moduleData.apis.buildApi
  val useAndroidX = moduleData.projectTemplateData.androidXSupport
  val useMaterial2 = useAndroidX || hasDependency("com.google.android.material:material")
  val ktOrJavaExt = projectData.language.extension
  addAllKotlinDependencies(moduleData)

  mergeXml(androidManifestXml(activityClass, moduleData.isLibrary, moduleData.isNew, isThingsLauncher, packageName),
           manifestOut.resolve("AndroidManifest.xml"))

  if (generateLayout) {
    generateSimpleLayout(moduleData, activityClass, layoutName, true, packageName)
    open(resOut.resolve("layout/${layoutName}.xml"))
  }
  addDependency("com.android.support:appcompat-v7:${buildApi}.+")

  val simpleActivity = when (projectData.language) {
    Language.Java -> simpleActivityJava(activityClass, generateLayout, layoutName, packageName, useMaterial2)
    Language.Kotlin -> simpleActivityKt(activityClass, generateLayout, layoutName, packageName, useMaterial2)
  }
  save(simpleActivity, srcOut.resolve("${activityClass}.${ktOrJavaExt}"))

  open(srcOut.resolve("${activityClass}.${ktOrJavaExt}"))
}
