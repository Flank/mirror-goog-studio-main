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
import com.android.tools.idea.wizard.template.impl.activities.common.addMaterialDependency
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
  val appCompatVersion = moduleData.apis.appCompatVersion
  val useAndroidX = moduleData.projectTemplateData.androidXSupport
  val ktOrJavaExt = projectData.language.extension
  addAllKotlinDependencies(moduleData)

  mergeXml(androidManifestXml(activityClass, moduleData.isLibrary, moduleData.isNewModule, isThingsLauncher || moduleData.isNewModule, packageName),
           manifestOut.resolve("AndroidManifest.xml"))

  if (generateLayout) {
    generateSimpleLayout(moduleData, activityClass, layoutName)
  }
  addDependency("com.android.support:appcompat-v7:${appCompatVersion}.+")

  val simpleActivity = when (projectData.language) {
    Language.Java -> simpleActivityJava(activityClass, generateLayout, layoutName, packageName, useAndroidX)
    Language.Kotlin -> simpleActivityKt(activityClass, generateLayout, layoutName, packageName, useAndroidX)
  }
  save(simpleActivity, srcOut.resolve("${activityClass}.${ktOrJavaExt}"))

  open(srcOut.resolve("${activityClass}.${ktOrJavaExt}"))
}
