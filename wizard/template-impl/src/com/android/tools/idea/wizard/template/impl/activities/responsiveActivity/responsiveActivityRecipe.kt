/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.wizard.template.impl.activities.responsiveActivity

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.PackageName
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.impl.activities.common.addAllKotlinDependencies
import com.android.tools.idea.wizard.template.impl.activities.common.addMaterialDependency
import com.android.tools.idea.wizard.template.impl.activities.common.addViewBindingSupport
import com.android.tools.idea.wizard.template.impl.activities.common.generateManifest
import com.android.tools.idea.wizard.template.impl.activities.common.generateNoActionBarStyles
import com.android.tools.idea.wizard.template.impl.activities.common.navigation.navigationDependencies
import com.android.tools.idea.wizard.template.impl.activities.common.navigation.saveFragmentAndViewModel
import com.android.tools.idea.wizard.template.impl.activities.responsiveActivity.res.layout.activityMainXml
import com.android.tools.idea.wizard.template.impl.activities.responsiveActivity.res.layout_w600dp.activityMainXml as activityMainXmlW600dp
import com.android.tools.idea.wizard.template.impl.activities.responsiveActivity.res.layout_w1240dp.activityMainXml as activityMainXmlW1240dp
import com.android.tools.idea.wizard.template.impl.activities.responsiveActivity.res.layout.appBarMainXml
import com.android.tools.idea.wizard.template.impl.activities.responsiveActivity.res.layout_w600dp.appBarMainXml as appBarMainXmlW600dp
import com.android.tools.idea.wizard.template.impl.activities.responsiveActivity.res.layout_w1240dp.appBarMainXml as appBarMainXmlW1240dp
import com.android.tools.idea.wizard.template.impl.activities.responsiveActivity.res.layout.contentMainXml
import com.android.tools.idea.wizard.template.impl.activities.responsiveActivity.res.layout.fragmentTransformXml
import com.android.tools.idea.wizard.template.impl.activities.responsiveActivity.res.layout.itemTransformXml
import com.android.tools.idea.wizard.template.impl.activities.responsiveActivity.res.layout.navigationDrawerHeaderXml
import com.android.tools.idea.wizard.template.impl.activities.responsiveActivity.res.layout_w600dp.itemTransformXml as itemTransformXmlW600dp
import com.android.tools.idea.wizard.template.impl.activities.responsiveActivity.res.layout_w600dp.fragmentTransformXml as fragmentTransformXmlW600dp
import com.android.tools.idea.wizard.template.impl.activities.responsiveActivity.res.layout_w600dp.contentMainXml as contentMainXmlW600dp
import com.android.tools.idea.wizard.template.impl.activities.responsiveActivity.res.layout_w1240dp.contentMainXml as contentMainXmlW1240dp
import com.android.tools.idea.wizard.template.impl.activities.responsiveActivity.res.menu.bottomNavigationMenu
import com.android.tools.idea.wizard.template.impl.activities.responsiveActivity.res.menu.navigationDrawerMenu
import com.android.tools.idea.wizard.template.impl.activities.responsiveActivity.res.menu.overflowMenu
import com.android.tools.idea.wizard.template.impl.activities.responsiveActivity.res.navigation.mobileNavigation
import com.android.tools.idea.wizard.template.impl.activities.responsiveActivity.res.values.dimens
import com.android.tools.idea.wizard.template.impl.activities.responsiveActivity.res.values_w600dp.dimens as dimensW600dp
import com.android.tools.idea.wizard.template.impl.activities.responsiveActivity.res.values_w936dp.dimens as dimensW936dp
import com.android.tools.idea.wizard.template.impl.activities.responsiveActivity.res.values.strings
import com.android.tools.idea.wizard.template.impl.activities.responsiveActivity.src.mainActivityJava
import com.android.tools.idea.wizard.template.impl.activities.responsiveActivity.src.mainActivityKt
import com.android.tools.idea.wizard.template.impl.activities.responsiveActivity.src.ui.transform.transformFragmentJava
import com.android.tools.idea.wizard.template.impl.activities.responsiveActivity.src.ui.transform.transformFragmentKt
import com.android.tools.idea.wizard.template.impl.activities.responsiveActivity.src.ui.transform.transformViewModelJava
import com.android.tools.idea.wizard.template.impl.activities.responsiveActivity.src.ui.transform.transformViewModelKt
import java.io.File

fun RecipeExecutor.generateResponsiveActivity(
  moduleTemplateData: ModuleTemplateData,
  activityClass: String,
  activityMainLayoutName: String,
  appBarMainLayoutName: String,
  isLauncher: Boolean,
  packageName: PackageName,
  navHeaderLayoutName: String,
  contentLayoutName: String,
  navGraphName: String
) {
  val (projectTemplateData, srcOut, resOut) = moduleTemplateData
  val apis = moduleTemplateData.apis
  val language = projectTemplateData.language

  addAllKotlinDependencies(moduleTemplateData)
  addDependency("com.android.support:appcompat-v7:${apis.appCompatVersion}.+")
  addDependency("com.android.support:recyclerview-v7:${apis.appCompatVersion}.+")
  addDependency("com.android.support.constraint:constraint-layout:+")
  addMaterialDependency(true)
  addViewBindingSupport(moduleTemplateData.viewBindingSupport, true)

  generateManifest(
    moduleData = moduleTemplateData,
    activityClass = activityClass,
    packageName = packageName,
    isLauncher = isLauncher,
    hasNoActionBar = true,
    generateActivityTitle = true,
    isResizeable = true
  )
  generateNoActionBarStyles(
    baseFeatureResOut = moduleTemplateData.baseFeature?.resDir,
    resDir = moduleTemplateData.resDir,
    themesData = moduleTemplateData.themesData
  )

  (1..16).map {
    copy(File("avatar_$it.xml"), resOut.resolve("drawable/avatar_$it.xml"))
  }
  copy(File("drawable/side_nav_bar.xml"), resOut.resolve("drawable/side_nav_bar.xml"))
  copy(File("ic_camera_black_24dp.xml"), resOut.resolve("drawable/ic_camera_black_24dp.xml"))
  copy(File("ic_gallery_black_24dp.xml"), resOut.resolve("drawable/ic_gallery_black_24dp.xml"))
  copy(File("ic_slideshow_black_24dp.xml"), resOut.resolve("drawable/ic_slideshow_black_24dp.xml"))
  copy(File("ic_settings_black_24dp.xml"), resOut.resolve("drawable/ic_settings_black_24dp.xml"))
  mergeXml(strings(), resOut.resolve("values/strings.xml"))
  mergeXml(dimens(), resOut.resolve("values/dimens.xml"))
  mergeXml(dimensW600dp(), resOut.resolve("values-w600dp/dimens.xml"))
  mergeXml(dimensW936dp(), resOut.resolve("values-w936dp/dimens.xml"))
  mergeXml(bottomNavigationMenu(), resOut.resolve("menu/bottom_navigation.xml"))
  mergeXml(navigationDrawerMenu(), resOut.resolve("menu/navigation_drawer.xml"))
  mergeXml(overflowMenu(), resOut.resolve("menu/overflow.xml"))

  mergeXml(activityMainXml(appBarMainLayoutName), resOut.resolve("layout/$activityMainLayoutName.xml"))
  mergeXml(activityMainXmlW600dp(
    appBarMainName = appBarMainLayoutName,
    navigationHeaderLayoutName = navHeaderLayoutName
  ), resOut.resolve("layout-w600dp/$activityMainLayoutName.xml"))
  mergeXml(activityMainXmlW1240dp(appBarMainLayoutName), resOut.resolve("layout-w1240dp/$activityMainLayoutName.xml"))
  mergeXml(appBarMainXml(
    activityClass = activityClass,
    contentMainLayoutName = contentLayoutName,
    packageName = packageName,
    themesData = moduleTemplateData.themesData
  ), resOut.resolve("layout/$appBarMainLayoutName.xml"))
  mergeXml(appBarMainXmlW600dp(
    activityClass = activityClass,
    contentMainLayoutName = contentLayoutName,
    packageName = packageName,
    themesData = moduleTemplateData.themesData
  ), resOut.resolve("layout-w600dp/$appBarMainLayoutName.xml"))
  mergeXml(appBarMainXmlW1240dp(
    activityClass = activityClass,
    contentMainLayoutName = contentLayoutName,
    packageName = packageName,
    themesData = moduleTemplateData.themesData
  ), resOut.resolve("layout-w1240dp/$appBarMainLayoutName.xml"))

  // navHostFragmentId needs to be unique, thus appending contentLayoutName since it's
  // guaranteed to be unique
  val navHostFragmentId = "nav_host_fragment_${contentLayoutName}"
  mergeXml(contentMainXml(
    appBarMainLayoutName = appBarMainLayoutName,
    navHostFragmentId = navHostFragmentId,
    navGraphName = navGraphName
  ), resOut.resolve("layout/$contentLayoutName.xml"))
  mergeXml(contentMainXmlW600dp(
    appBarMainLayoutName = appBarMainLayoutName,
    navHostFragmentId = navHostFragmentId,
    navGraphName = navGraphName
  ), resOut.resolve("layout-w600dp/$contentLayoutName.xml"))
  mergeXml(contentMainXmlW1240dp(
    appBarMainLayoutName = appBarMainLayoutName,
    navHostFragmentId = navHostFragmentId,
    navHeaderLayoutName = navHeaderLayoutName
  ), resOut.resolve("layout-w1240dp/$contentLayoutName.xml"))
  mergeXml(fragmentTransformXml("TransformFragment"), resOut.resolve("layout/fragment_transform.xml"))
  mergeXml(fragmentTransformXmlW600dp("TransformFragment"), resOut.resolve("layout-w600dp/fragment_transform.xml"))
  mergeXml(itemTransformXml(), resOut.resolve("layout/item_transform.xml"))
  mergeXml(itemTransformXmlW600dp(), resOut.resolve("layout-w600dp/item_transform.xml"))
  mergeXml(
    navigationDrawerHeaderXml(
      appCompatVersion = apis.appCompatVersion,
      targetApi = apis.targetApi.api,
      isLibraryProject = moduleTemplateData.isLibrary
    ),
    resOut.resolve("layout/${navHeaderLayoutName}.xml")
  )

  val isViewBindingSupported = moduleTemplateData.viewBindingSupport.isViewBindingSupported()

  val ktOrJavaExt = projectTemplateData.language.extension
  save(
    when (projectTemplateData.language) {
      Language.Java -> mainActivityJava(
        packageName = packageName,
        activityClass = activityClass,
        appBarLayoutName = appBarMainLayoutName,
        contentMainLayoutName = contentLayoutName,
        layoutName = activityMainLayoutName,
        navHostFragmentId = navHostFragmentId,
        isViewBindingSupported = isViewBindingSupported
      )
      Language.Kotlin -> mainActivityKt(
        packageName = packageName,
        activityClass = activityClass,
        appBarLayoutName = appBarMainLayoutName,
        contentMainLayoutName = contentLayoutName,
        layoutName = activityMainLayoutName,
        navHostFragmentId = navHostFragmentId,
        isViewBindingSupported = isViewBindingSupported
      )
    }, srcOut.resolve("${activityClass}.${ktOrJavaExt}")
  )

  save(
    when (projectTemplateData.language) {
      Language.Java -> transformFragmentJava(
        packageName = packageName,
        fragmentClassName = "TransformFragment",
        navFragmentPrefix = "transform",
        navViewModelClass = "TransformViewModel",
        isViewBindingSupported = isViewBindingSupported
      )
      Language.Kotlin -> transformFragmentKt(
        packageName = packageName,
        fragmentClassName = "TransformFragment",
        navFragmentPrefix = "transform",
        navViewModelClass = "TransformViewModel",
        isViewBindingSupported = isViewBindingSupported
      )
    }, srcOut.resolve("ui/transform/TransformFragment.${ktOrJavaExt}")
  )

  save(
    when (projectTemplateData.language) {
      Language.Java -> transformViewModelJava(
        packageName = packageName,
        navFragmentPrefix = "transform",
        navViewModelClass = "TransformViewModel"
      )
      Language.Kotlin ->
        transformViewModelKt(
          packageName = packageName,
          navFragmentPrefix = "transform",
          navViewModelClass = "TransformViewModel"
        )
    }, srcOut.resolve("ui/transform/TransformViewModel.${ktOrJavaExt}")
  )

  saveFragmentAndViewModel(
    resOut = resOut,
    srcOut = srcOut,
    language = language,
    packageName = packageName,
    fragmentPrefix = "reflow",
    useAndroidX = true,
    isViewBindingSupported = isViewBindingSupported)
  saveFragmentAndViewModel(
    resOut = resOut,
    srcOut = srcOut,
    language = language,
    packageName = packageName,
    fragmentPrefix = "slideshow",
    useAndroidX = true,
    isViewBindingSupported = isViewBindingSupported)
  saveFragmentAndViewModel(
    resOut = resOut,
    srcOut = srcOut,
    language = language,
    packageName = packageName,
    fragmentPrefix = "settings",
    useAndroidX = true,
    isViewBindingSupported = isViewBindingSupported)
  if (language == Language.Kotlin) {
    requireJavaVersion("1.8", true)
  }
  val generateKotlin = language == Language.Kotlin
  navigationDependencies(generateKotlin, true, apis.appCompatVersion)

  save(
      mobileNavigation(navGraphName, packageName),
      resOut.resolve("navigation/${navGraphName}.xml")
  )
  open(resOut.resolve("navigation/${navGraphName}.xml"))
  open(srcOut.resolve("${activityClass}.${language.extension}"))
  open(resOut.resolve("layout/${contentLayoutName}.xml"))
}
