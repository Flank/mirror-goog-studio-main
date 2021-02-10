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
package com.android.tools.idea.wizard.template.impl.activities.navigationDrawerActivity

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.PackageName
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.classToResource
import com.android.tools.idea.wizard.template.impl.activities.common.addAllKotlinDependencies
import com.android.tools.idea.wizard.template.impl.activities.common.addMaterialDependency
import com.android.tools.idea.wizard.template.impl.activities.common.addViewBindingSupport
import com.android.tools.idea.wizard.template.impl.activities.common.generateAppBar
import com.android.tools.idea.wizard.template.impl.activities.common.generateManifest
import com.android.tools.idea.wizard.template.impl.activities.common.generateSimpleMenu
import com.android.tools.idea.wizard.template.impl.activities.common.navigation.navigationDependencies
import com.android.tools.idea.wizard.template.impl.activities.common.navigation.saveFragmentAndViewModel
import com.android.tools.idea.wizard.template.impl.activities.navigationDrawerActivity.res.layout.navigationContentMain
import com.android.tools.idea.wizard.template.impl.activities.navigationDrawerActivity.res.layout.navigationHeaderXml
import com.android.tools.idea.wizard.template.impl.activities.navigationDrawerActivity.res.layout.navigationViewXml
import com.android.tools.idea.wizard.template.impl.activities.navigationDrawerActivity.res.menu.drawer
import com.android.tools.idea.wizard.template.impl.activities.navigationDrawerActivity.res.menu.navigationDrawerMain
import com.android.tools.idea.wizard.template.impl.activities.navigationDrawerActivity.res.navigation.mobileNavigation
import com.android.tools.idea.wizard.template.impl.activities.navigationDrawerActivity.res.values.dimens
import com.android.tools.idea.wizard.template.impl.activities.navigationDrawerActivity.res.values.navigationDrawerDrawables
import com.android.tools.idea.wizard.template.impl.activities.navigationDrawerActivity.res.values.strings
import com.android.tools.idea.wizard.template.impl.activities.navigationDrawerActivity.src.drawerActivityJava
import com.android.tools.idea.wizard.template.impl.activities.navigationDrawerActivity.src.drawerActivityKt
import java.io.File

fun RecipeExecutor.generateNavigationDrawer(
  data: ModuleTemplateData,
  activityClass: String,
  layoutName: String,
  isLauncher: Boolean,
  packageName: PackageName,
  appBarLayoutName: String,
  navHeaderLayoutName: String,
  drawerMenu: String,
  contentLayoutName: String,
  navGraphName: String
) {
  val excludeMenu = false
  val menuName = classToResource(activityClass)

  val (projectTemplateData, srcOut, resOut, _, _, _, _, _, isNewModule) = data
  val apis = data.apis
  val (_, targetApi, minApi, appCompatVersion) = apis
  val includeImageDrawables = minApi.api < 21
  val language = projectTemplateData.language
  val useAndroidX = projectTemplateData.androidXSupport

  addAllKotlinDependencies(data)
  addDependency("com.android.support:appcompat-v7:${appCompatVersion}.+")
  addMaterialDependency(useAndroidX)
  addViewBindingSupport(data.viewBindingSupport, true)

  generateManifest(
    data,
    activityClass,
    packageName,
    isLauncher,
    hasNoActionBar = true,
    generateActivityTitle = true
  )

  mergeXml(strings(), resOut.resolve("values/strings.xml"))
  mergeXml(dimens(), resOut.resolve("values/dimens.xml"))
  save(navigationDrawerMain(), resOut.resolve("menu/${menuName}.xml"))

  copy(File("drawable"), resOut.resolve("drawable"))
  val drawable = if (includeImageDrawables) "drawable-v21" else "drawable"
  copy(File("drawable-v21"), resOut.resolve(drawable))

  if (includeImageDrawables) {
    save(navigationDrawerDrawables(), resOut.resolve("values/drawables.xml"))
  }

  addDependency("com.android.support:design:${appCompatVersion}.+")
  addDependency("com.android.support:appcompat-v7:${appCompatVersion}.+")
  addDependency("com.android.support.constraint:constraint-layout:+")

  // navHostFragmentId needs to be unique, thus appending contentLayoutName since it's
  // guaranteed to be unique
  val navHostFragmentId = "nav_host_fragment_${contentLayoutName}"

  save(
    navigationContentMain(appBarLayoutName, navGraphName, navHostFragmentId, useAndroidX),
    resOut.resolve("layout/${contentLayoutName}.xml")
  )

  if (isNewModule && !excludeMenu) {
    generateSimpleMenu(packageName, activityClass, resOut, menuName)
  }

  val isViewBindingSupported = data.viewBindingSupport.isViewBindingSupported()
  saveFragmentAndViewModel(
    resOut = resOut,
    srcOut = srcOut,
    language = language,
    packageName = packageName,
    fragmentPrefix = "home",
    useAndroidX = useAndroidX,
    isViewBindingSupported = isViewBindingSupported)
  saveFragmentAndViewModel(
    resOut = resOut,
    srcOut = srcOut,
    language = language,
    packageName = packageName,
    fragmentPrefix = "gallery",
    useAndroidX = useAndroidX,
    isViewBindingSupported = isViewBindingSupported)
  saveFragmentAndViewModel(
    resOut = resOut,
    srcOut = srcOut,
    language = language,
    packageName = packageName,
    fragmentPrefix = "slideshow",
    useAndroidX = useAndroidX,
    isViewBindingSupported = isViewBindingSupported)
  if (language == Language.Kotlin) {
    requireJavaVersion("1.8", true)
  }
  val generateKotlin = language == Language.Kotlin
  navigationDependencies(generateKotlin, useAndroidX, appCompatVersion)

  save(
    mobileNavigation(navGraphName, packageName),
    resOut.resolve("navigation/${navGraphName}.xml")
  )
  open(resOut.resolve("navigation/${navGraphName}.xml"))

  generateAppBar(
    data,
    activityClass,
    packageName,
    contentLayoutName,
    appBarLayoutName,
    useAndroidX = useAndroidX
  )

  save(drawer(), resOut.resolve("menu/${drawerMenu}.xml"))

  save(
    navigationViewXml(appBarLayoutName, navHeaderLayoutName, drawerMenu, useAndroidX),
    resOut.resolve("layout/${layoutName}.xml")
  )
  save(
    navigationHeaderXml(appCompatVersion, targetApi.api, data.isLibrary),
    resOut.resolve("layout/${navHeaderLayoutName}.xml")
  )
  save(
    if (generateKotlin)
      drawerActivityKt(
        packageName = packageName,
        activityClass = activityClass,
        appBarLayoutName = appBarLayoutName,
        layoutName = layoutName,
        menuName = menuName,
        navHostFragmentId = navHostFragmentId,
        useAndroidX = useAndroidX,
        isViewBindingSupported = isViewBindingSupported
      )
    else
      drawerActivityJava(
        packageName = packageName,
        activityClass = activityClass,
        appBarLayoutName = appBarLayoutName,
        layoutName = layoutName,
        menuName = menuName,
        navHostFragmentId = navHostFragmentId,
        useAndroidX = useAndroidX,
        isViewBindingSupported = isViewBindingSupported
      ),
    srcOut.resolve("${activityClass}.${language.extension}")
  )

  open(srcOut.resolve("${activityClass}.${language.extension}"))
  open(resOut.resolve("layout/${contentLayoutName}.xml"))
}
