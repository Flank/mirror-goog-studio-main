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

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.impl.common.addAllKotlinDependencies
import com.android.tools.idea.wizard.template.impl.common.generateManifest
import com.android.tools.idea.wizard.template.impl.tabbedActivity.res.layout.appBarActivityXml
import com.android.tools.idea.wizard.template.impl.tabbedActivity.res.layout.fragmentSimpleXml
import com.android.tools.idea.wizard.template.impl.tabbedActivity.res.values.dimensXml
import com.android.tools.idea.wizard.template.impl.tabbedActivity.res.values_w820dp.dimensXml as dimensXmlW820dp
import com.android.tools.idea.wizard.template.impl.tabbedActivity.res.values.stringsXml
import com.android.tools.idea.wizard.template.impl.tabbedActivity.res.values.stylesXml
import com.android.tools.idea.wizard.template.impl.tabbedActivity.src.app_package.tabsActivityJava
import com.android.tools.idea.wizard.template.impl.tabbedActivity.src.app_package.tabsActivityKt
import com.android.tools.idea.wizard.template.impl.tabbedActivity.src.app_package.ui.main.pageViewModelJava
import com.android.tools.idea.wizard.template.impl.tabbedActivity.src.app_package.ui.main.pageViewModelKt
import com.android.tools.idea.wizard.template.impl.tabbedActivity.src.app_package.ui.main.placeholderFragmentJava
import com.android.tools.idea.wizard.template.impl.tabbedActivity.src.app_package.ui.main.placeholderFragmentKt
import com.android.tools.idea.wizard.template.impl.tabbedActivity.src.app_package.ui.main.sectionsPagerAdapterJava
import com.android.tools.idea.wizard.template.impl.tabbedActivity.src.app_package.ui.main.sectionsPagerAdapterKt

fun RecipeExecutor.tabbedActivityRecipe(
  moduleData: ModuleTemplateData,
  activityClass: String,
  layoutName: String,
  fragmentLayoutName: String,
  isLauncher: Boolean,
  packageName: String
) {

  val (projectData, srcOut, resOut, _) = moduleData
  val apis = moduleData.apis
  val buildApi = apis.buildApi!!
  val useAndroidX = moduleData.projectTemplateData.androidXSupport
  val useMaterial2 = useAndroidX || hasDependency("com.google.android.material:material")
  addAllKotlinDependencies(moduleData)
  addDependency("com.android.support:appcompat-v7:${buildApi}.+")
  addDependency("com.android.support:design:${buildApi}.+")
  addDependency("com.android.support.constraint:constraint-layout:+")
  addDependency("android.arch.lifecycle:extensions:+")

  generateManifest(
    moduleData, activityClass, activityClass, packageName, isLauncher, true,
    requireTheme = true, generateActivityTitle = true, useMaterial2 = useMaterial2
  )

  mergeXml(stringsXml(), resOut.resolve("values/strings.xml"))
  mergeXml(stylesXml(), resOut.resolve("values/styles.xml"))
  mergeXml(dimensXml(), resOut.resolve("values/dimens.xml"))
  mergeXml(dimensXmlW820dp(), resOut.resolve("values-w820dp/dimens.xml"))

  val appBarActivityLayoutXml = appBarActivityXml(
    activityClass,
    packageName,
    "AppTheme.AppBarOverlay",
    useAndroidX,
    useMaterial2)
  save(appBarActivityLayoutXml, resOut.resolve("layout/${layoutName}.xml"))
  val fragmentLayoutXml = fragmentSimpleXml(packageName, useAndroidX)
  save(fragmentLayoutXml, resOut.resolve("layout/${fragmentLayoutName}.xml"))

  val ktOrJavaExt = projectData.language.extension
  val tabsActivity = when (projectData.language) {
    Language.Java -> tabsActivityJava(activityClass, layoutName, packageName, useAndroidX, useMaterial2)
    Language.Kotlin -> tabsActivityKt(activityClass, layoutName, packageName, useAndroidX, useMaterial2)
  }
  save(tabsActivity, srcOut.resolve("${activityClass}.${ktOrJavaExt}"))

  val pageViewModel = when (projectData.language) {
    Language.Java -> pageViewModelJava(packageName, useAndroidX)
    Language.Kotlin -> pageViewModelKt(packageName, useAndroidX)
  }
  save(pageViewModel, srcOut.resolve("ui/main/PageViewModel.${ktOrJavaExt}"))

  val placeholderFragment = when (projectData.language) {
    Language.Java -> placeholderFragmentJava(packageName, useAndroidX)
    Language.Kotlin -> placeholderFragmentKt(packageName, useAndroidX)
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
