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
import com.android.tools.idea.wizard.template.impl.activities.emptyActivity.src.emptyActivityJava
import com.android.tools.idea.wizard.template.impl.activities.common.generateManifest
import com.android.tools.idea.wizard.template.impl.activities.common.generateSimpleLayout
import com.android.tools.idea.wizard.template.impl.activities.emptyActivity.src.emptyActivityKt
import com.android.tools.idea.wizard.template.impl.activities.emptyActivity.src.emptyActivityWithCppSupportJava
import com.android.tools.idea.wizard.template.impl.activities.emptyActivity.src.emptyActivityWithCppSupportKt
import com.android.tools.idea.wizard.template.impl.activities.emptyActivity.src.nativeLibCpp

fun RecipeExecutor.generateEmptyActivity(
  moduleData: ModuleTemplateData,
  activityClass: String,
  generateLayout: Boolean,
  layoutName: String,
  isLauncher: Boolean,
  packageName: PackageName,
  includeCppSupport: Boolean = false
) {
  val (projectData, srcOut) = moduleData
  val useAndroidX = projectData.androidXSupport
  val useMaterial2 = useAndroidX || hasDependency("com.google.android.material:material")
  val ktOrJavaExt = projectData.language.extension

  generateManifest(
    moduleData , activityClass, "", packageName, isLauncher, false,
    requireTheme = false, generateActivityTitle = false, useMaterial2 = useMaterial2
  )

  addAllKotlinDependencies(moduleData)

  if (generateLayout || includeCppSupport) {
    generateSimpleLayout(moduleData, activityClass, layoutName, true, packageName)
  }

  val simpleActivityPath = srcOut.resolve("$activityClass.$ktOrJavaExt")
  if (includeCppSupport) {
    val nativeSrcOut = moduleData.rootDir.resolve("src/main/cpp")
    val simpleActivity = when (projectData.language) {
      Language.Kotlin -> emptyActivityWithCppSupportKt(packageName, activityClass, layoutName, useAndroidX)
      Language.Java -> emptyActivityWithCppSupportJava(packageName, activityClass, layoutName, useAndroidX)
    }
    save(simpleActivity, simpleActivityPath)
    save(nativeLibCpp(packageName, activityClass), nativeSrcOut.resolve("native-lib.cpp"))
  } else {
    val simpleActivity = when (projectData.language) {
      Language.Kotlin -> emptyActivityKt(packageName, activityClass, layoutName, generateLayout, useAndroidX)
      Language.Java -> emptyActivityJava(packageName, activityClass, layoutName, generateLayout, useAndroidX)
    }
    save(simpleActivity, simpleActivityPath)
  }

  open(simpleActivityPath)
}
