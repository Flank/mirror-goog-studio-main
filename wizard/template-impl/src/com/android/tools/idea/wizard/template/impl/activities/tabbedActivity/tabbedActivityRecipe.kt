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

package com.android.tools.idea.wizard.template.impl.activities.tabbedActivity

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.impl.activities.common.addAllKotlinDependencies
import com.android.tools.idea.wizard.template.impl.activities.common.addLifecycleDependencies
import com.android.tools.idea.wizard.template.impl.activities.common.addMaterialDependency
import com.android.tools.idea.wizard.template.impl.activities.common.addViewBindingSupport
import com.android.tools.idea.wizard.template.impl.activities.common.generateManifest
import com.android.tools.idea.wizard.template.impl.activities.common.generateNoActionBarStyles
import com.android.tools.idea.wizard.template.impl.activities.tabbedActivity.res.layout.appBarActivityXml
import com.android.tools.idea.wizard.template.impl.activities.tabbedActivity.res.layout.fragmentSimpleXml
import com.android.tools.idea.wizard.template.impl.activities.tabbedActivity.res.values.dimensXml
import com.android.tools.idea.wizard.template.impl.activities.tabbedActivity.res.values_land.dimensXml as dimensXmlLand
import com.android.tools.idea.wizard.template.impl.activities.tabbedActivity.res.values_w1240dp.dimensXml as dimensXmlW1240dp
import com.android.tools.idea.wizard.template.impl.activities.tabbedActivity.res.values_w600dp.dimensXml as dimensXmlW600dp
import com.android.tools.idea.wizard.template.impl.activities.tabbedActivity.res.values_w820dp.dimensXml as dimensXmlW820dp
import com.android.tools.idea.wizard.template.impl.activities.tabbedActivity.res.values.stringsXml
import com.android.tools.idea.wizard.template.impl.activities.tabbedActivity.src.app_package.tabsActivityJava
import com.android.tools.idea.wizard.template.impl.activities.tabbedActivity.src.app_package.tabsActivityKt
import com.android.tools.idea.wizard.template.impl.activities.tabbedActivity.src.app_package.ui.main.pageViewModelJava
import com.android.tools.idea.wizard.template.impl.activities.tabbedActivity.src.app_package.ui.main.pageViewModelKt
import com.android.tools.idea.wizard.template.impl.activities.tabbedActivity.src.app_package.ui.main.placeholderFragmentJava
import com.android.tools.idea.wizard.template.impl.activities.tabbedActivity.src.app_package.ui.main.placeholderFragmentKt
import com.android.tools.idea.wizard.template.impl.activities.tabbedActivity.src.app_package.ui.main.sectionsPagerAdapterJava
import com.android.tools.idea.wizard.template.impl.activities.tabbedActivity.src.app_package.ui.main.sectionsPagerAdapterKt

fun RecipeExecutor.tabbedActivityRecipe(
  moduleData: ModuleTemplateData,
  activityClass: String,
  layoutName: String,
  fragmentLayoutName: String,
  isLauncher: Boolean,
  packageName: String
) {
  val (projectData, srcOut, resOut) = moduleData
  val apis = moduleData.apis
  val appCompatVersion = apis.appCompatVersion
  val useAndroidX = moduleData.projectTemplateData.androidXSupport
  addAllKotlinDependencies(moduleData)
  addDependency("com.android.support:appcompat-v7:${appCompatVersion}.+")
  addDependency("com.android.support:design:${appCompatVersion}.+")
  addDependency("com.android.support.constraint:constraint-layout:+")
  addLifecycleDependencies(useAndroidX)
  addMaterialDependency(useAndroidX)
  addViewBindingSupport(moduleData.viewBindingSupport, true)

  generateManifest(
    moduleData, activityClass, packageName, isLauncher, true,
    generateActivityTitle = true
  )
  generateNoActionBarStyles(moduleData.baseFeature?.resDir, resOut, moduleData.themesData)

  mergeXml(stringsXml(), resOut.resolve("values/strings.xml"))
  mergeXml(dimensXml(), resOut.resolve("values/dimens.xml"))
  mergeXml(dimensXmlW820dp(), resOut.resolve("values-w820dp/dimens.xml"))
  mergeXml(dimensXmlLand(), resOut.resolve("values-land/dimens.xml"))
  mergeXml(dimensXmlW600dp(), resOut.resolve("values-w600dp/dimens.xml"))
  mergeXml(dimensXmlW1240dp(), resOut.resolve("values-w1240dp/dimens.xml"))

  val appBarActivityLayoutXml = appBarActivityXml(
    activityClass,
    packageName,
    moduleData.themesData.appBarOverlay.name,
    useAndroidX)
  save(appBarActivityLayoutXml, resOut.resolve("layout/${layoutName}.xml"))
  val fragmentLayoutXml = fragmentSimpleXml(packageName, useAndroidX)
  save(fragmentLayoutXml, resOut.resolve("layout/${fragmentLayoutName}.xml"))

  val ktOrJavaExt = projectData.language.extension
  val isViewBindingSupported = moduleData.viewBindingSupport.isViewBindingSupported()
  val tabsActivity = when (projectData.language) {
    Language.Java -> tabsActivityJava(
      activityClass = activityClass,
      layoutName = layoutName,
      packageName = packageName,
      useAndroidX = useAndroidX,
      isViewBindingSupported = isViewBindingSupported
    )
    Language.Kotlin -> tabsActivityKt(
      activityClass = activityClass,
      layoutName = layoutName,
      packageName = packageName,
      useAndroidX = useAndroidX,
      isViewBindingSupported = isViewBindingSupported
    )
  }
  save(tabsActivity, srcOut.resolve("${activityClass}.${ktOrJavaExt}"))

  val pageViewModel = when (projectData.language) {
    Language.Java -> pageViewModelJava(packageName, useAndroidX)
    Language.Kotlin -> pageViewModelKt(packageName, useAndroidX)
  }
  save(pageViewModel, srcOut.resolve("ui/main/PageViewModel.${ktOrJavaExt}"))

  val placeholderFragment = when (projectData.language) {
    Language.Java -> placeholderFragmentJava(
      fragmentLayoutName = fragmentLayoutName,
      packageName = packageName,
      useAndroidX = useAndroidX,
      isViewBindingSupported = isViewBindingSupported
    )
    Language.Kotlin -> placeholderFragmentKt(
      fragmentLayoutName = fragmentLayoutName,
      packageName = packageName,
      useAndroidX = useAndroidX,
      isViewBindingSupported = isViewBindingSupported
    )
  }
  save(placeholderFragment, srcOut.resolve("ui/main/PlaceholderFragment.${ktOrJavaExt}"))

  val sectionsPagerAdapter = when (projectData.language) {
    Language.Java -> sectionsPagerAdapterJava(packageName, useAndroidX)
    Language.Kotlin -> sectionsPagerAdapterKt(packageName, useAndroidX)
  }
  save(sectionsPagerAdapter, srcOut.resolve("ui/main/SectionsPagerAdapter.${ktOrJavaExt}"))

  open(srcOut.resolve("${activityClass}.${ktOrJavaExt}"))
  open(resOut.resolve("layout/${fragmentLayoutName}.xml"))
}
