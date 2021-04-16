/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.idea.wizard.template.impl.activities.googleAdMobAdsActivity

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.getMaterialComponentName
import com.android.tools.idea.wizard.template.impl.activities.common.addAllKotlinDependencies
import com.android.tools.idea.wizard.template.impl.activities.common.addViewBindingSupport
import com.android.tools.idea.wizard.template.impl.activities.common.generateManifestStrings
import com.android.tools.idea.wizard.template.impl.activities.googleAdMobAdsActivity.res.layout.activitySimpleXml
import com.android.tools.idea.wizard.template.impl.activities.googleAdMobAdsActivity.res.menu.mainXml
import com.android.tools.idea.wizard.template.impl.activities.googleAdMobAdsActivity.res.values.dimensXml
import com.android.tools.idea.wizard.template.impl.activities.googleAdMobAdsActivity.res.values_w820dp.dimensXml as dimensXmlW820dp
import com.android.tools.idea.wizard.template.impl.activities.googleAdMobAdsActivity.res.values.stringsXml
import com.android.tools.idea.wizard.template.impl.activities.googleAdMobAdsActivity.src.app_package.simpleActivityJava
import com.android.tools.idea.wizard.template.impl.activities.googleAdMobAdsActivity.src.app_package.simpleActivityKt
import com.android.tools.idea.wizard.template.impl.fragments.googleAdMobAdsFragment.AdFormat

fun RecipeExecutor.googleAdMobAdsActivityRecipe(
  moduleData: ModuleTemplateData,
  activityClass: String,
  layoutName: String,
  menuName: String,
  adFormat: AdFormat,
  isLauncher: Boolean,
  packageName: String
) {
  val (projectData, srcOut, resOut, manifestOut) = moduleData
  val useAndroidX = moduleData.projectTemplateData.androidXSupport
  val ktOrJavaExt = projectData.language.extension
  addAllKotlinDependencies(moduleData)
  addViewBindingSupport(moduleData.viewBindingSupport, true)

  generateManifestStrings(activityClass, moduleData.baseFeature?.resDir ?: resOut, moduleData.isNewModule, true)
  addDependency("com.android.support:appcompat-v7:${moduleData.apis.appCompatVersion}.+")
  addDependency("com.google.android.gms:play-services-ads:+", toBase = moduleData.isDynamic)

  mergeXml(androidManifestXml(activityClass, isLauncher, moduleData.isLibrary, moduleData.isNewModule, packageName),
           manifestOut.resolve("AndroidManifest.xml"))

  save(mainXml(activityClass, packageName), resOut.resolve("menu/${menuName}.xml"))

  mergeXml(stringsXml(adFormat), resOut.resolve("values/strings.xml"))
  mergeXml(dimensXml(), resOut.resolve("values/dimens.xml"))
  mergeXml(dimensXmlW820dp(), resOut.resolve("values-w820dp/dimens.xml"))

  save(
      activitySimpleXml(activityClass, adFormat, packageName),
      resOut.resolve("layout/${layoutName}.xml")
  )

  val superClassFqcn = getMaterialComponentName("android.support.v7.app.AppCompatActivity", useAndroidX)
  val isViewBindingSupported = moduleData.viewBindingSupport.isViewBindingSupported()
  val simpleActivity = when (projectData.language) {
    Language.Java ->
      simpleActivityJava(
        activityClass = activityClass,
        adFormat = adFormat,
        applicationPackage = projectData.applicationPackage,
        layoutName = layoutName,
        menuName = menuName,
        packageName = packageName,
        superClassFqcn = superClassFqcn,
        isViewBindingSupported = isViewBindingSupported
      )
    Language.Kotlin ->
      simpleActivityKt(
        activityClass = activityClass,
        adFormat = adFormat,
        applicationPackage = projectData.applicationPackage,
        layoutName = layoutName,
        menuName = menuName,
        packageName = packageName,
        superClassFqcn = superClassFqcn,
        isViewBindingSupported = isViewBindingSupported
      )
  }
  save(simpleActivity, srcOut.resolve("${activityClass}.${ktOrJavaExt}"))

  open(srcOut.resolve("${activityClass}.${ktOrJavaExt}"))
  open(resOut.resolve("layout/${layoutName}.xml"))
}
