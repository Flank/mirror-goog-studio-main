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

package com.android.tools.idea.wizard.template.impl.activities.masterDetailFlow

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.extractLetters
import com.android.tools.idea.wizard.template.impl.activities.common.addAllKotlinDependencies
import com.android.tools.idea.wizard.template.impl.activities.common.generateNoActionBarStyles
import com.android.tools.idea.wizard.template.impl.activities.common.generateThemeStyles
import com.android.tools.idea.wizard.template.impl.activities.common.src.app_package.dummy.dummyContentJava
import com.android.tools.idea.wizard.template.impl.activities.common.src.app_package.dummy.dummyContentKt
import com.android.tools.idea.wizard.template.impl.activities.masterDetailFlow.res.layout.activityItemDetailXml
import com.android.tools.idea.wizard.template.impl.activities.masterDetailFlow.res.layout.activityItemListAppBarXml
import com.android.tools.idea.wizard.template.impl.activities.masterDetailFlow.res.layout.fragmentItemDetailXml
import com.android.tools.idea.wizard.template.impl.activities.masterDetailFlow.res.layout.fragmentItemListTwopaneXml
import com.android.tools.idea.wizard.template.impl.activities.masterDetailFlow.res.layout.fragmentItemListXml
import com.android.tools.idea.wizard.template.impl.activities.masterDetailFlow.res.layout.itemListContentXml
import com.android.tools.idea.wizard.template.impl.activities.masterDetailFlow.res.values.dimensXml
import com.android.tools.idea.wizard.template.impl.activities.masterDetailFlow.res.values.stringsXml
import com.android.tools.idea.wizard.template.impl.activities.masterDetailFlow.src.app_package.contentDetailActivityJava
import com.android.tools.idea.wizard.template.impl.activities.masterDetailFlow.src.app_package.contentDetailActivityKt
import com.android.tools.idea.wizard.template.impl.activities.masterDetailFlow.src.app_package.contentDetailFragmentJava
import com.android.tools.idea.wizard.template.impl.activities.masterDetailFlow.src.app_package.contentDetailFragmentKt
import com.android.tools.idea.wizard.template.impl.activities.masterDetailFlow.src.app_package.contentListActivityJava
import com.android.tools.idea.wizard.template.impl.activities.masterDetailFlow.src.app_package.contentListActivityKt

fun RecipeExecutor.masterDetailFlowRecipe(
  moduleData: ModuleTemplateData,
  objectKind: String,
  objectKindPlural: String,
  isLauncher: Boolean,
  packageName: String
) {
  val (projectData, srcOut, resOut, manifestOut) = moduleData
  val buildApi = moduleData.apis.buildApi
  val useAndroidX = moduleData.projectTemplateData.androidXSupport
  val useMaterial2 = useAndroidX || hasDependency("com.google.android.material:material")
  val ktOrJavaExt = projectData.language.extension
  val collection = extractLetters(objectKind)
  val itemListLayout = collection.toLowerCase() + "_list"
  val collectionName = "${collection}List"
  val detailName = "${collection}Detail"
  val detailNameLayout = collection.toLowerCase() + "_detail"
  val itemListContentLayout = "${itemListLayout}_content"
  val applicationPackage = projectData.applicationPackage
  addAllKotlinDependencies(moduleData)

  addDependency("com.android.support:support-v4:${buildApi}.+")
  addDependency("com.android.support:recyclerview-v7:${buildApi}.+")
  addDependency("com.android.support:design:${buildApi}.+")

  val baseFeatureResOut = moduleData.baseFeature?.resDir ?: resOut
  generateThemeStyles(moduleData.themesData.main, moduleData.isDynamic, useMaterial2, resOut, baseFeatureResOut)
  mergeXml(androidManifestXml(
    collectionName, detailName, itemListLayout, detailNameLayout, isLauncher, moduleData.isLibrary, moduleData.isNewModule,
    packageName, moduleData.themesData.noActionBar.name),
           manifestOut.resolve("AndroidManifest.xml"))

  val stringsXml = stringsXml(itemListLayout, detailNameLayout, moduleData.isNewModule, objectKind, objectKindPlural)
  if (moduleData.isDynamic) {
    mergeXml(stringsXml, moduleData.baseFeature?.resDir!!.resolve("values/strings.xml"))
  }
  else {
    mergeXml(stringsXml, resOut.resolve("values/strings.xml"))
  }

  mergeXml(dimensXml(), resOut.resolve("values/dimens.xml"))
  generateNoActionBarStyles(moduleData.baseFeature?.resDir, resOut, moduleData.themesData)

  save(activityItemDetailXml(detailName, detailNameLayout, packageName, useAndroidX, useMaterial2),
       resOut.resolve("layout/activity_${detailNameLayout}.xml"))
  save(fragmentItemListXml(collectionName, itemListLayout, itemListContentLayout, packageName, useAndroidX),
       resOut.resolve("layout/${itemListLayout}.xml"))
  save(fragmentItemListTwopaneXml(collectionName, itemListLayout, detailNameLayout, itemListContentLayout, objectKindPlural, packageName,
                                  useAndroidX), resOut.resolve("layout-w900dp/${itemListLayout}.xml"))
  save(itemListContentXml(), resOut.resolve("layout/${itemListContentLayout}.xml"))
  save(fragmentItemDetailXml(detailName, detailNameLayout, packageName), resOut.resolve("layout/${detailNameLayout}.xml"))
  save(activityItemListAppBarXml(
    collectionName, itemListLayout, packageName, moduleData.themesData.appBarOverlay.name, moduleData.themesData.popupOverlay.name,
    useAndroidX, useMaterial2),
       resOut.resolve("layout/activity_${itemListLayout}.xml"))

  val contentDetailActivity = when (projectData.language) {
    Language.Java -> contentDetailActivityJava(collectionName, detailName, applicationPackage, detailNameLayout,
                                               objectKind, packageName, useAndroidX, useMaterial2)
    Language.Kotlin -> contentDetailActivityKt(collectionName, detailName, detailNameLayout, objectKind,
                                               packageName, useAndroidX, useMaterial2)
  }
  save(contentDetailActivity, srcOut.resolve("${detailName}Activity.${ktOrJavaExt}"))

  val contentDetailFragment = when (projectData.language) {
    Language.Java -> contentDetailFragmentJava(collectionName, detailName, applicationPackage, detailNameLayout, objectKind, packageName,
                                               useAndroidX, useMaterial2)
    Language.Kotlin -> contentDetailFragmentKt(collectionName, detailName, applicationPackage, detailNameLayout, objectKind, packageName,
                                               useAndroidX)
  }
  save(contentDetailFragment, srcOut.resolve("${detailName}Fragment.${ktOrJavaExt}"))

  val contentListActivity = when (projectData.language) {
    Language.Java -> contentListActivityJava(collectionName, detailName, applicationPackage, itemListLayout, detailNameLayout,
                                             itemListContentLayout, itemListLayout, objectKindPlural, packageName, useAndroidX,
                                             useMaterial2)
    Language.Kotlin -> contentListActivityKt(collectionName, detailName, applicationPackage, itemListLayout, detailNameLayout,
                                             itemListContentLayout, itemListLayout, packageName, useAndroidX, useMaterial2)
  }
  save(contentListActivity, srcOut.resolve("${collectionName}Activity.${ktOrJavaExt}"))

  val dummyContent = when (projectData.language) {
    Language.Java -> dummyContentJava(packageName)
    Language.Kotlin -> dummyContentKt(packageName)
  }
  save(dummyContent, srcOut.resolve("dummy/DummyContent.${ktOrJavaExt}"))

  open(srcOut.resolve("${detailName}Fragment.${ktOrJavaExt}"))
  open(resOut.resolve("layout/fragment_${detailNameLayout}.xml"))
}
