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

package com.android.tools.idea.wizard.template.impl.activities.primaryDetailFlow

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.extractLetters
import com.android.tools.idea.wizard.template.impl.activities.common.addAllKotlinDependencies
import com.android.tools.idea.wizard.template.impl.activities.common.addMaterialDependency
import com.android.tools.idea.wizard.template.impl.activities.common.addViewBindingSupport
import com.android.tools.idea.wizard.template.impl.activities.common.generateManifest
import com.android.tools.idea.wizard.template.impl.activities.common.generateNoActionBarStyles
import com.android.tools.idea.wizard.template.impl.activities.common.generateThemeStyles
import com.android.tools.idea.wizard.template.impl.activities.common.src.app_package.placeholder.placeholderContentJava
import com.android.tools.idea.wizard.template.impl.activities.common.src.app_package.placeholder.placeholderContentKt
import com.android.tools.idea.wizard.template.impl.activities.primaryDetailFlow.res.layout.fragmentItemDetailXml
import com.android.tools.idea.wizard.template.impl.activities.primaryDetailFlow.res.layout.fragmentItemDetailTwoPaneXml
import com.android.tools.idea.wizard.template.impl.activities.primaryDetailFlow.res.layout.fragmentItemListTwoPaneXml
import com.android.tools.idea.wizard.template.impl.activities.primaryDetailFlow.res.layout.fragmentItemListXml
import com.android.tools.idea.wizard.template.impl.activities.primaryDetailFlow.res.layout.itemListContentXml
import com.android.tools.idea.wizard.template.impl.activities.primaryDetailFlow.res.values.dimensXml
import com.android.tools.idea.wizard.template.impl.activities.primaryDetailFlow.res.values_land.dimensXml as dimensXmlLand
import com.android.tools.idea.wizard.template.impl.activities.primaryDetailFlow.res.values_w600dp.dimensXml as dimensXmlW600dp
import com.android.tools.idea.wizard.template.impl.activities.primaryDetailFlow.res.values.stringsXml
import com.android.tools.idea.wizard.template.impl.activities.primaryDetailFlow.src.app_package.contentDetailFragmentJava
import com.android.tools.idea.wizard.template.impl.activities.primaryDetailFlow.src.app_package.contentDetailFragmentKt
import com.android.tools.idea.wizard.template.impl.activities.primaryDetailFlow.src.app_package.contentListFragmentJava
import com.android.tools.idea.wizard.template.impl.activities.primaryDetailFlow.src.app_package.contentListFragmentKt
import com.android.tools.idea.wizard.template.impl.activities.primaryDetailFlow.res.layout.activityMainXml
import com.android.tools.idea.wizard.template.impl.activities.primaryDetailFlow.res.navigation.mobileNavigationXml
import com.android.tools.idea.wizard.template.impl.activities.primaryDetailFlow.res.navigation.tabletDetailsNavigationXml
import com.android.tools.idea.wizard.template.impl.activities.primaryDetailFlow.src.app_package.contentListDetailHostActivityKt
import com.android.tools.idea.wizard.template.impl.activities.common.navigation.navigationDependencies
import com.android.tools.idea.wizard.template.impl.activities.primaryDetailFlow.src.app_package.contentListDetailHostActivityJava

fun RecipeExecutor.primaryDetailFlowRecipe(
  moduleData: ModuleTemplateData,
  objectKind: String,
  objectKindPlural: String,
  isLauncher: Boolean,
  mainNavGraphFile: String,
  childNavGraphFile: String,
  detailNameFragmentLayout: String,
  packageName: String
) {
  val (projectData, srcOut, resOut) = moduleData
  val appCompatVersion = moduleData.apis.appCompatVersion
  val useAndroidX = moduleData.projectTemplateData.androidXSupport
  val ktOrJavaExt = projectData.language.extension
  val collection = extractLetters(objectKind)
  val itemListLayout = collection.toLowerCase() + "_list"
  val collectionName = "${collection}List"
  val detailName = "${collection}Detail"
  val detailNameLayout = collection.toLowerCase() + "_detail"
  val itemListContentLayout = "${itemListLayout}_content"
  val applicationPackage = projectData.applicationPackage
  val generateKotlin = projectData.language == Language.Kotlin
  val isViewBindingSupported = moduleData.viewBindingSupport.isViewBindingSupported()
  addAllKotlinDependencies(moduleData)

  addDependency("com.android.support:appcompat-v7:${appCompatVersion}.+")
  addDependency("com.android.support:recyclerview-v7:${appCompatVersion}.+")
  addDependency("com.android.support:design:${appCompatVersion}.+")
  addDependency("com.android.support.constraint:constraint-layout:+")
  addMaterialDependency(useAndroidX)
  addViewBindingSupport(moduleData.viewBindingSupport, true)

  generateManifest(
    moduleData,
    "${collection}DetailHostActivity",
    packageName,
    isLauncher,
    hasNoActionBar = false,
    generateActivityTitle = true,
    isResizeable = true
  )

  navigationDependencies(generateKotlin, useAndroidX, moduleData.apis.appCompatVersion)
  if (generateKotlin) {
   requireJavaVersion("1.8", true)
  }

  generateThemeStyles(moduleData.themesData.main, useAndroidX, moduleData.baseFeature?.resDir ?: resOut)

  val stringsXml = stringsXml(itemListLayout, detailNameLayout, moduleData.isNewModule, objectKind, objectKindPlural)
  if (moduleData.isDynamic) {
    mergeXml(stringsXml, moduleData.baseFeature?.resDir!!.resolve("values/strings.xml"))
  }
  else {
    mergeXml(stringsXml, resOut.resolve("values/strings.xml"))
  }

  mergeXml(dimensXml(), resOut.resolve("values/dimens.xml"))
  mergeXml(dimensXmlLand(), resOut.resolve("values-land/dimens.xml"))
  mergeXml(dimensXmlW600dp(), resOut.resolve("values-w600dp/dimens.xml"))
  generateNoActionBarStyles(moduleData.baseFeature?.resDir, resOut, moduleData.themesData)

  // navHostFragmentId needs to be unique, thus appending detailNameLayout since it's
  // guaranteed to be unique
  val navHostFragmentId = "nav_host_${detailNameFragmentLayout}"

  save(
    fragmentItemDetailXml(detailName, detailNameLayout, packageName, useAndroidX),
    resOut.resolve("layout/${detailNameFragmentLayout}.xml")
  )
  save(
    fragmentItemDetailTwoPaneXml(detailName, detailNameLayout, packageName, useAndroidX),
    resOut.resolve("layout-sw600dp/${detailNameFragmentLayout}.xml")
  )
  save(
    fragmentItemListXml(collectionName, detailName, itemListLayout, itemListContentLayout, packageName, useAndroidX),
    resOut.resolve("layout/fragment_${itemListLayout}.xml")
  )
  save(
    fragmentItemListTwoPaneXml(collectionName, itemListLayout, detailName, detailNameLayout, itemListContentLayout, childNavGraphFile, packageName,
                                 useAndroidX), resOut.resolve("layout-sw600dp/fragment_${itemListLayout}.xml")
  )
  save(itemListContentXml(), resOut.resolve("layout/${itemListContentLayout}.xml"))
  save(
    activityMainXml(navHostFragmentId, detailNameFragmentLayout, mainNavGraphFile, useAndroidX),
    resOut.resolve("layout/activity_${detailNameLayout}.xml")
  )

  val mainActivity = when (projectData.language) {
    Language.Java -> contentListDetailHostActivityJava(
      packageName = packageName,
      collection = collection,
      activityLayout = detailNameLayout,
      navHostFragmentId = navHostFragmentId,
      useAndroidX = useAndroidX,
      isViewBindingSupported = isViewBindingSupported
    )
    Language.Kotlin -> contentListDetailHostActivityKt(
      packageName = packageName,
      collection = collection,
      activityLayout = detailNameLayout,
      navHostFragmentId = navHostFragmentId,
      useAndroidX = useAndroidX,
      isViewBindingSupported = isViewBindingSupported
    )
  }
  save(mainActivity, srcOut.resolve("${collection}DetailHostActivity.${ktOrJavaExt}"))

  val contentDetailFragment = when (projectData.language) {
    Language.Java -> contentDetailFragmentJava(
      collection = collection,
      collectionName = collectionName,
      applicationPackage = applicationPackage,
      detailNameLayout = detailNameLayout,
      objectKind = objectKind,
      packageName = packageName,
      useAndroidX = useAndroidX,
      isViewBindingSupported = isViewBindingSupported
    )
    Language.Kotlin -> contentDetailFragmentKt(
      collectionName = collectionName,
      detailName = detailName,
      applicationPackage = applicationPackage,
      detailNameLayout = detailNameLayout,
      objectKind = objectKind,
      packageName = packageName,
      useAndroidX = useAndroidX,
      isViewBindingSupported = isViewBindingSupported
    )
  }
  save(contentDetailFragment, srcOut.resolve("${detailName}Fragment.${ktOrJavaExt}"))

  val contentListFragment = when (projectData.language) {
    Language.Java -> contentListFragmentJava(
      collectionName = collectionName,
      detailName = detailName,
      applicationPackage = applicationPackage,
      detailNameLayout = detailNameLayout,
      itemListContentLayout = itemListContentLayout,
      itemListLayout = itemListLayout,
      objectKindPlural = objectKindPlural,
      packageName = packageName,
      useAndroidX = useAndroidX,
      isViewBindingSupported = isViewBindingSupported
    )
    Language.Kotlin -> contentListFragmentKt(
      collectionName = collectionName,
      detailName = detailName,
      applicationPackage = applicationPackage,
      detailNameLayout = detailNameLayout,
      itemListContentLayout = itemListContentLayout,
      itemListLayout = itemListLayout,
      packageName = packageName,
      useAndroidX = useAndroidX,
      isViewBindingSupported = isViewBindingSupported
    )
  }
  save(contentListFragment, srcOut.resolve("${collectionName}Fragment.${ktOrJavaExt}"))

  val placeholderContent = when (projectData.language) {
    Language.Java -> placeholderContentJava(packageName)
    Language.Kotlin -> placeholderContentKt(packageName)
  }
  save(placeholderContent, srcOut.resolve("placeholder/PlaceholderContent.${ktOrJavaExt}"))

  save(
    mobileNavigationXml(packageName,
                          itemListLayout,
                          collectionName,
                          detailName,
                          detailNameLayout),
    resOut.resolve("navigation/${mainNavGraphFile}.xml")
  )
  save(
    tabletDetailsNavigationXml(packageName,
                                 detailName,
                                 detailNameLayout),
    resOut.resolve("navigation/${childNavGraphFile}.xml")
  )

  open(srcOut.resolve("${detailName}Fragment.${ktOrJavaExt}"))
  open(resOut.resolve("layout/fragment_${detailNameLayout}.xml"))
}
