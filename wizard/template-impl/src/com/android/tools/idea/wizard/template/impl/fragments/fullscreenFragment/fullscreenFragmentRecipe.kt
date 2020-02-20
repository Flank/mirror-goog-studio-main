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

package com.android.tools.idea.wizard.template.impl.fragments.fullscreenFragment

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.impl.activities.common.addAllKotlinDependencies
import com.android.tools.idea.wizard.template.impl.fragments.fullscreenFragment.res.layout.fragmentFullscreenXml
import com.android.tools.idea.wizard.template.impl.fragments.fullscreenFragment.res.values.fullScreenColorsXml
import com.android.tools.idea.wizard.template.impl.fragments.fullscreenFragment.res.values.stringsXml
import com.android.tools.idea.wizard.template.impl.fragments.fullscreenFragment.src.app_package.fullscreenFragmentJava
import com.android.tools.idea.wizard.template.impl.fragments.fullscreenFragment.src.app_package.fullscreenFragmentKt

fun RecipeExecutor.fullscreenFragmentRecipe(
  moduleData: ModuleTemplateData,
  fragmentClass: String,
  layoutName: String,
  packageName: String
) {
  val (projectData, srcOut, resOut, manifestOut) = moduleData
  val useAndroidX = moduleData.projectTemplateData.androidXSupport
  val ktOrJavaExt = projectData.language.extension
  addAllKotlinDependencies(moduleData)

  mergeXml(stringsXml(), resOut.resolve("values/strings.xml"))
  mergeXml(fullScreenColorsXml(), resOut.resolve("values/colors.xml"))
  save(fragmentFullscreenXml(fragmentClass, packageName), resOut.resolve("layout/${layoutName}.xml"))

  val fullscreenFragment = when (projectData.language) {
    Language.Java -> fullscreenFragmentJava(fragmentClass, layoutName, packageName, useAndroidX)
    Language.Kotlin -> fullscreenFragmentKt(fragmentClass, layoutName, packageName, useAndroidX)
  }
  save(fullscreenFragment, srcOut.resolve("${fragmentClass}.${ktOrJavaExt}"))


  open(resOut.resolve("layout/${layoutName}.xml"))
  open(srcOut.resolve("${fragmentClass}.${ktOrJavaExt}"))
}
