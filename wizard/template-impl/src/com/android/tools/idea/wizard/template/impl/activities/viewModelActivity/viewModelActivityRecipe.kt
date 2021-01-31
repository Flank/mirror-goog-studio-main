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

package com.android.tools.idea.wizard.template.impl.activities.viewModelActivity

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.getMaterialComponentName
import com.android.tools.idea.wizard.template.impl.activities.common.addAllKotlinDependencies
import com.android.tools.idea.wizard.template.impl.activities.common.addLifecycleDependencies
import com.android.tools.idea.wizard.template.impl.activities.common.addMaterialDependency
import com.android.tools.idea.wizard.template.impl.activities.common.generateManifest
import com.android.tools.idea.wizard.template.impl.activities.viewModelActivity.res.layout.activityXml
import com.android.tools.idea.wizard.template.impl.activities.viewModelActivity.res.layout.fragmentXml
import com.android.tools.idea.wizard.template.impl.activities.viewModelActivity.src.app_package.activityJava
import com.android.tools.idea.wizard.template.impl.activities.viewModelActivity.src.app_package.activityKt
import com.android.tools.idea.wizard.template.impl.activities.viewModelActivity.src.app_package.fragmentJava
import com.android.tools.idea.wizard.template.impl.activities.viewModelActivity.src.app_package.fragmentKt
import com.android.tools.idea.wizard.template.impl.activities.viewModelActivity.src.app_package.viewModelJava
import com.android.tools.idea.wizard.template.impl.activities.viewModelActivity.src.app_package.viewModelKt

fun RecipeExecutor.viewModelActivityRecipe(
  moduleData: ModuleTemplateData,
  activityClass: String,
  activityLayout: String,
  fragmentClass: String,
  fragmentLayout: String,
  viewModelClass: String,
  isLauncher: Boolean,
  packageName: String,
  fragmentPackage: String
) {
  val (projectData, srcOut, resOut) = moduleData
  val apis = moduleData.apis
  val appCompatVersion = apis.appCompatVersion
  val useAndroidX = moduleData.projectTemplateData.androidXSupport
  val ktOrJavaExt = projectData.language.extension
  val generateKotlin = projectData.language == Language.Kotlin
  val superClassFqcn = getMaterialComponentName("android.support.v7.app.AppCompatActivity", useAndroidX)
  addAllKotlinDependencies(moduleData)

  // TODO: Old templates doesn't set requireTheme as true, check if it's not needed
  generateManifest(
    moduleData, activityClass, packageName, isLauncher, false,
    generateActivityTitle = false
  )

  addDependency("com.android.support:appcompat-v7:${appCompatVersion}.+")
  addDependency("com.android.support.constraint:constraint-layout:+")
  addLifecycleDependencies(useAndroidX)
  addMaterialDependency(useAndroidX)
  if (generateKotlin && useAndroidX) {
    addDependency("androidx.lifecycle:lifecycle-viewmodel-ktx:+")
  }

  mergeXml(activityXml(activityClass), resOut.resolve("layout/${activityLayout}.xml"))
  mergeXml(fragmentXml(fragmentClass, fragmentPackage, useAndroidX), resOut.resolve("layout/${fragmentLayout}.xml"))
  open(resOut.resolve("layout/${fragmentLayout}.xml"))

  val activity = when (projectData.language) {
    Language.Java -> activityJava(activityClass, activityLayout, fragmentClass, fragmentPackage, packageName, superClassFqcn)
    Language.Kotlin -> activityKt(activityClass, activityLayout, fragmentClass, fragmentPackage, packageName, superClassFqcn)
  }
  save(activity, srcOut.resolve("${activityClass}.${ktOrJavaExt}"))

  val fragmentPath = fragmentPackage.replace(".", "/")
  val fragment = when (projectData.language) {
    Language.Java -> fragmentJava(fragmentClass, fragmentLayout, fragmentPackage, packageName, useAndroidX, viewModelClass)
    Language.Kotlin -> fragmentKt(fragmentClass, fragmentLayout, fragmentPackage, packageName, useAndroidX, viewModelClass)
  }
  save(fragment, srcOut.resolve("${fragmentPath}/${fragmentClass}.${ktOrJavaExt}"))

  open(srcOut.resolve("${fragmentPath}/${fragmentClass}.${ktOrJavaExt}"))
  val viewModel = when (projectData.language) {
    Language.Java -> viewModelJava(fragmentPackage, packageName, useAndroidX, viewModelClass)
    Language.Kotlin -> viewModelKt(fragmentPackage, packageName, useAndroidX, viewModelClass)
  }
  save(viewModel, srcOut.resolve("${fragmentPath}/${viewModelClass}.${ktOrJavaExt}"))

  open(srcOut.resolve("${fragmentPath}/${viewModelClass}.${ktOrJavaExt}"))
}
