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
package com.android.tools.idea.wizard.template.impl.activities.emptyActivity

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.PackageName
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.impl.activities.common.addAllKotlinDependencies
import com.android.tools.idea.wizard.template.impl.activities.common.addMaterialDependency
import com.android.tools.idea.wizard.template.impl.activities.common.generateManifest
import com.android.tools.idea.wizard.template.impl.activities.common.generateSimpleLayout
import com.android.tools.idea.wizard.template.impl.activities.emptyActivity.src.emptyActivityJava
import com.android.tools.idea.wizard.template.impl.activities.emptyActivity.src.emptyActivityKt

fun RecipeExecutor.generateEmptyActivity(
  moduleData: ModuleTemplateData,
  activityClass: String,
  generateLayout: Boolean,
  layoutName: String,
  isLauncher: Boolean,
  packageName: PackageName
) {
  val (projectData, srcOut) = moduleData
  val useAndroidX = projectData.androidXSupport
  val ktOrJavaExt = projectData.language.extension

  addDependency("com.android.support:appcompat-v7:${moduleData.apis.appCompatVersion}.+")
  addMaterialDependency(useAndroidX)

  generateManifest(
    moduleData, activityClass, packageName, isLauncher, false,
    generateActivityTitle = false
  )

  addAllKotlinDependencies(moduleData)

  if (generateLayout) {
    generateSimpleLayout(moduleData, activityClass, layoutName)
  }

  val simpleActivity = when (projectData.language) {
    Language.Kotlin -> emptyActivityKt(packageName, activityClass, layoutName, generateLayout, useAndroidX)
    Language.Java -> emptyActivityJava(packageName, activityClass, layoutName, generateLayout, useAndroidX)
  }

  val simpleActivityPath = srcOut.resolve("$activityClass.$ktOrJavaExt")
  save(simpleActivity, simpleActivityPath)
  open(simpleActivityPath)
}
