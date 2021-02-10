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

package com.android.tools.idea.wizard.template.impl.activities.fullscreenActivity

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.activityToLayout
import com.android.tools.idea.wizard.template.getMaterialComponentName
import com.android.tools.idea.wizard.template.impl.activities.common.addAllKotlinDependencies
import com.android.tools.idea.wizard.template.impl.activities.common.addMaterialDependency
import com.android.tools.idea.wizard.template.impl.activities.common.addViewBindingSupport
import com.android.tools.idea.wizard.template.impl.activities.common.generateThemeStyles
import com.android.tools.idea.wizard.template.impl.activities.fullscreenActivity.res.layout.activityFullscreenXml
import com.android.tools.idea.wizard.template.impl.activities.fullscreenActivity.res.values.fullscreenAttrs
import com.android.tools.idea.wizard.template.impl.activities.fullscreenActivity.res.values.fullscreenColors
import com.android.tools.idea.wizard.template.impl.activities.fullscreenActivity.res.values.fullscreenStyles
import com.android.tools.idea.wizard.template.impl.activities.fullscreenActivity.res.values.fullscreenThemes
import com.android.tools.idea.wizard.template.impl.activities.fullscreenActivity.res.values.stringsXml
import com.android.tools.idea.wizard.template.impl.activities.fullscreenActivity.res.values_night.fullscreenThemes as fullscreenThemesNight
import com.android.tools.idea.wizard.template.impl.activities.fullscreenActivity.src.app_package.fullscreenActivityJava
import com.android.tools.idea.wizard.template.impl.activities.fullscreenActivity.src.app_package.fullscreenActivityKt

fun RecipeExecutor.fullscreenActivityRecipe(
  moduleData: ModuleTemplateData,
  activityClass: String,
  isLauncher: Boolean,
  layoutName: String,
  packageName: String
) {
  val (projectData, srcOut, resOut, manifestOut) = moduleData
  val apis = moduleData.apis
  val appCompatVersion = apis.appCompatVersion
  val useAndroidX = moduleData.projectTemplateData.androidXSupport
  val ktOrJavaExt = projectData.language.extension
  addAllKotlinDependencies(moduleData)

  addDependency("com.android.support:appcompat-v7:${appCompatVersion}.+")
  addMaterialDependency(useAndroidX)
  addViewBindingSupport(moduleData.viewBindingSupport, true)

  val simpleName = activityToLayout(activityClass)
  val superClassFqcn = getMaterialComponentName("android.support.v7.app.AppCompatActivity", useAndroidX)
  val themeName = moduleData.themesData.main.name
  mergeXml(androidManifestXml(activityClass, packageName, simpleName, isLauncher, moduleData.isLibrary, moduleData.isNewModule, themeName),
           manifestOut.resolve("AndroidManifest.xml"))

  val finalResOut = moduleData.baseFeature?.resDir ?: resOut
  generateThemeStyles(moduleData.themesData.main, useAndroidX, finalResOut)

  mergeXml(fullscreenAttrs(), finalResOut.resolve("values/attrs.xml"))
  mergeXml(fullscreenColors(), finalResOut.resolve("values/colors.xml"))
  mergeXml(fullscreenStyles(moduleData.themesData), finalResOut.resolve("values/styles.xml"))
  mergeXml(fullscreenThemes(moduleData.themesData), finalResOut.resolve("values/themes.xml"))
  mergeXml(fullscreenThemesNight(moduleData.themesData), finalResOut.resolve("values-night/themes.xml"))

  save(activityFullscreenXml(activityClass, packageName, moduleData.themesData), resOut.resolve("layout/${layoutName}.xml"))
  mergeXml(stringsXml(activityClass, moduleData.isNewModule, simpleName), finalResOut.resolve("values/strings.xml"))

  val actionBarClassFqcn = getMaterialComponentName("android.support.v7.app.ActionBar", useAndroidX)
  val isViewBindingSupported = moduleData.viewBindingSupport.isViewBindingSupported()
  val fullscreenActivity = when (projectData.language) {
    Language.Java -> fullscreenActivityJava(
      actionBarClassFqcn = actionBarClassFqcn,
      activityClass = activityClass,
      applicationPackage = projectData.applicationPackage,
      layoutName = layoutName,
      packageName = packageName,
      superClassFqcn = superClassFqcn,
      isViewBindingSupported = isViewBindingSupported
    )
    Language.Kotlin -> fullscreenActivityKt(
      activityClass = activityClass,
      applicationPackage = projectData.applicationPackage,
      layoutName = layoutName,
      packageName = packageName,
      superClassFqcn = superClassFqcn,
      isViewBindingSupported = isViewBindingSupported
    )
  }
  save(fullscreenActivity, srcOut.resolve("${activityClass}.${ktOrJavaExt}"))

  open(srcOut.resolve("${activityClass}.${ktOrJavaExt}"))
  open(finalResOut.resolve("layout/${layoutName}.xml"))
}
