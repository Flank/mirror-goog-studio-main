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

package com.android.tools.idea.wizard.template.impl.fragments.viewModelFragment

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.impl.activities.common.addAllKotlinDependencies
import com.android.tools.idea.wizard.template.impl.fragments.viewModelFragment.res.layout.blankFragmentXml
import com.android.tools.idea.wizard.template.impl.fragments.viewModelFragment.src.app_package.blankFragmentJava
import com.android.tools.idea.wizard.template.impl.fragments.viewModelFragment.src.app_package.blankFragmentKt
import com.android.tools.idea.wizard.template.impl.fragments.viewModelFragment.src.app_package.blankViewModelJava
import com.android.tools.idea.wizard.template.impl.fragments.viewModelFragment.src.app_package.blankViewModelKt

fun RecipeExecutor.viewModelFragmentRecipe(
  moduleData: ModuleTemplateData, fragmentClass: String,
  layoutName: String,
  viewModelName: String
) {
  val (projectData, srcOut, resOut, manifestOut) = moduleData
  val buildApi = moduleData.apis.buildApi
  val useAndroidX = moduleData.projectTemplateData.androidXSupport
  val ktOrJavaExt = projectData.language.extension
  addAllKotlinDependencies(moduleData)
  val packageName = moduleData.packageName
  val applicationPackage = projectData.applicationPackage

  addDependency("com.android.support:support-v4:${buildApi}.+")
  addDependency("android.arch.lifecycle:extensions:+")

  if (projectData.language == Language.Kotlin && useAndroidX) {
    addDependency("androidx.lifecycle:lifecycle-viewmodel-ktx:+")
  }

  save(blankFragmentXml(fragmentClass, packageName), resOut.resolve("layout/${layoutName}.xml"))

  open(resOut.resolve("layout/${layoutName}.xml"))

  val blankFragment = when (projectData.language) {
    Language.Java -> blankFragmentJava(applicationPackage, fragmentClass, layoutName, packageName, useAndroidX, viewModelName)
    Language.Kotlin -> blankFragmentKt(applicationPackage, fragmentClass, layoutName, packageName, useAndroidX, viewModelName)
  }
  save(blankFragment, srcOut.resolve("${fragmentClass}.${ktOrJavaExt}"))

  open(srcOut.resolve("${fragmentClass}.${ktOrJavaExt}"))

  val blankViewModel = when (projectData.language) {
    Language.Java -> blankViewModelJava(packageName, useAndroidX, viewModelName)
    Language.Kotlin -> blankViewModelKt(packageName, useAndroidX, viewModelName)
  }
  save(blankViewModel, srcOut.resolve("${viewModelName}.${ktOrJavaExt}"))
}
