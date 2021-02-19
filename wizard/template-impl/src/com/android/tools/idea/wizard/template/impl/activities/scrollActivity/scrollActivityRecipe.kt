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

package com.android.tools.idea.wizard.template.impl.activities.scrollActivity

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.impl.activities.common.addAllKotlinDependencies
import com.android.tools.idea.wizard.template.impl.activities.common.addMaterialDependency
import com.android.tools.idea.wizard.template.impl.activities.common.addViewBindingSupport
import com.android.tools.idea.wizard.template.impl.activities.common.generateManifest
import com.android.tools.idea.wizard.template.impl.activities.common.generateNoActionBarStyles
import com.android.tools.idea.wizard.template.impl.activities.common.generateSimpleMenu
import com.android.tools.idea.wizard.template.impl.activities.scrollActivity.res.layout.appBarXml
import com.android.tools.idea.wizard.template.impl.activities.scrollActivity.res.layout.contentScrollingXml
import com.android.tools.idea.wizard.template.impl.activities.scrollActivity.res.layout_w1240dp.contentScrollingXml as contentScrollingXmlW1240dp
import com.android.tools.idea.wizard.template.impl.activities.scrollActivity.res.layout_w936dp.contentScrollingXml as contentScrollingXmlW936dp
import com.android.tools.idea.wizard.template.impl.activities.scrollActivity.res.values.dimensXml
import com.android.tools.idea.wizard.template.impl.activities.scrollActivity.res.values_land.dimensXml as dimensXmlLand
import com.android.tools.idea.wizard.template.impl.activities.scrollActivity.res.values_w1240dp.dimensXml as dimensXmlW1240dp
import com.android.tools.idea.wizard.template.impl.activities.scrollActivity.res.values_w600dp.dimensXml as dimensXmlW600dp
import com.android.tools.idea.wizard.template.impl.activities.scrollActivity.res.values.stringsXml
import com.android.tools.idea.wizard.template.impl.activities.scrollActivity.src.app_package.scrollActivityJava
import com.android.tools.idea.wizard.template.impl.activities.scrollActivity.src.app_package.scrollActivityKt

fun RecipeExecutor.scrollActivityRecipe(
  moduleData: ModuleTemplateData,
  activityClass: String,
  layoutName: String,
  contentLayoutName: String,
  menuName: String,
  isLauncher: Boolean,
  packageName: String
) {
  val (projectData, srcOut, resOut, _) = moduleData
  val apis = moduleData.apis
  val appCompatVersion = apis.appCompatVersion
  val useAndroidX = moduleData.projectTemplateData.androidXSupport
  val ktOrJavaExt = projectData.language.extension
  addAllKotlinDependencies(moduleData)

  addDependency("com.android.support:appcompat-v7:${appCompatVersion}.+")
  addDependency("com.android.support:design:${appCompatVersion}.+")
  addDependency("com.android.support.constraint:constraint-layout:+")
  addMaterialDependency(useAndroidX)
  addViewBindingSupport(moduleData.viewBindingSupport, true)

  generateManifest(
    moduleData, activityClass, packageName, isLauncher, true,
    generateActivityTitle = true
  )
  mergeXml(stringsXml(), resOut.resolve("values/strings.xml"))
  mergeXml(dimensXml(), resOut.resolve("values/dimens.xml"))
  mergeXml(dimensXmlLand(), resOut.resolve("values-land/dimens.xml"))
  mergeXml(dimensXmlW1240dp(), resOut.resolve("values-w1240dp/dimens.xml"))
  mergeXml(dimensXmlW600dp(), resOut.resolve("values-w600dp/dimens.xml"))
  generateNoActionBarStyles(moduleData.baseFeature?.resDir, resOut, moduleData.themesData)
  generateSimpleMenu(packageName, activityClass, resOut, menuName)
  save(
    appBarXml(activityClass, packageName, contentLayoutName,
                moduleData.themesData.appBarOverlay.name, moduleData.themesData.popupOverlay.name, useAndroidX),
    resOut.resolve("layout/${layoutName}.xml")
  )
  save(contentScrollingXml(activityClass, layoutName, packageName, useAndroidX), resOut.resolve("layout/${contentLayoutName}.xml"))
  save(contentScrollingXmlW1240dp(activityClass, layoutName, packageName, useAndroidX), resOut.resolve("layout-w1240dp/${contentLayoutName}.xml"))
  save(contentScrollingXmlW936dp(activityClass, layoutName, packageName, useAndroidX), resOut.resolve("layout-w936dp/${contentLayoutName}.xml"))

  open(resOut.resolve("layout/${contentLayoutName}.xml"))

  val isViewBindingSupported = moduleData.viewBindingSupport.isViewBindingSupported()
  val scrollActivity = when (projectData.language) {
    Language.Java -> scrollActivityJava(
      activityClass = activityClass,
      applicationPackage = moduleData.projectTemplateData.applicationPackage,
      isNewModule = moduleData.isNewModule,
      layoutName = layoutName,
      menuName = menuName,
      packageName = packageName,
      useAndroidX = useAndroidX,
      isViewBindingSupported = isViewBindingSupported
    )
    Language.Kotlin -> scrollActivityKt(
      activityClass = activityClass,
      isNewModule = moduleData.isNewModule,
      layoutName = layoutName,
      menuName = menuName,
      packageName = packageName,
      useAndroidX = useAndroidX,
      isViewBindingSupported = isViewBindingSupported
    )
  }
  save(scrollActivity, srcOut.resolve("${activityClass}.${ktOrJavaExt}"))

  open(srcOut.resolve("${activityClass}.${ktOrJavaExt}"))
}
