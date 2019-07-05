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
package com.android.tools.idea.wizard.template.impl.basicActivity

import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.PackageName
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.impl.common.navigation.navigationKotlinBuildGradle
import com.android.tools.idea.wizard.template.impl.basicActivity.res.layout.fragmentSimpleXml
import com.android.tools.idea.wizard.template.impl.basicActivity.res.values.stringsXml
import com.android.tools.idea.wizard.template.impl.basicActivity.src.basicActivityKt
import com.android.tools.idea.wizard.template.impl.common.addAllKotlinDependencies
import com.android.tools.idea.wizard.template.impl.basicActivity.res.layout.fragmentFirstLayout
import com.android.tools.idea.wizard.template.impl.basicActivity.res.layout.fragmentSecondLayout
import com.android.tools.idea.wizard.template.impl.basicActivity.res.navigation.navGraphXml
import com.android.tools.idea.wizard.template.impl.common.navigation.addSafeArgsPlugin
import com.android.tools.idea.wizard.template.impl.common.navigation.addSafeArgsPluginToClasspath
import com.android.tools.idea.wizard.template.impl.common.navigation.src.ui.firstFragmentKt
import com.android.tools.idea.wizard.template.impl.common.navigation.src.ui.secondFragmentKt
import com.android.tools.idea.wizard.template.impl.common.recipeAppBar
import com.android.tools.idea.wizard.template.impl.common.recipeManifest
import com.android.tools.idea.wizard.template.impl.common.recipeSimpleMenu
import com.android.tools.idea.wizard.template.layoutToFragment

fun RecipeExecutor.basicActivityRecipe(
  data: ModuleTemplateData,
  activityClass: String,
  layoutName: String,
  simpleLayoutName: String,
  packageName: PackageName,
  menuName: String,
  activityTitle: String,
  isLauncher: Boolean,
  firstFragmentLayoutName: String,
  secondFragmentLayoutName: String
) {
  val (projectData, srcOut, resOut, manifestOut) = data
  val buildApi = data.projectTemplateData.buildApi
  val useAndroidX = data.projectTemplateData.androidXSupport
  val useMaterial2 = useAndroidX || hasDependency("com.google.android.material:material")
  if (useAndroidX) {
    addClasspathDependency("androidx.navigation:navigation-safe-args-gradle-plugin:+")
  }
  else {
    addClasspathDependency("android.arch.navigation:navigation-safe-args-gradle-plugin:+")
  }
  addSafeArgsPluginToClasspath(useAndroidX)
  addAllKotlinDependencies(data)
  recipeManifest(data.isNew, true, data.packageName, activityClass, activityTitle,
                 isLauncher, data.isLibrary, data.themesData.main, data.themesData.noActionBar, manifestOut,
                 data.resDir, requireTheme = true, generateActivityTitle = true, useMaterial2 = useMaterial2)
  recipeAppBar(buildApi, data.baseFeature?.resDir, data.resDir, layoutName, data.themesData, useAndroidX, useMaterial2, packageName,
               activityClass, simpleLayoutName)

  addDependency("com.android.support:appcompat-v7:$buildApi.+")
  addDependency("com.android.support.constraint:constraint-layout:+")
  applyPlugin("androidx.navigation.safeargs")
  save(fragmentSimpleXml(projectData.androidXSupport, data.isNew), data.resDir.resolve("layout/$simpleLayoutName.xml"), true, true)
  if (data.isNew) {
    recipeSimpleMenu(packageName, activityClass, data.resDir, menuName)
  }

  val ktOrJavaExt = projectData.language.extension
  val simpleActivityPath = srcOut.resolve("$activityClass.$ktOrJavaExt")

  val simpleActivity = basicActivityKt(
    data.isNew, projectData.applicationPackage, data.packageName, useMaterial2, useAndroidX, activityClass, layoutName, menuName
  )

  save(simpleActivity, simpleActivityPath)

  val firstFragmentClass = layoutToFragment(firstFragmentLayoutName)
  val secondFragmentClass = layoutToFragment(secondFragmentLayoutName)
  val firstFragmentClassContent = firstFragmentKt(
    packageName, useAndroidX, firstFragmentClass, secondFragmentClass, firstFragmentLayoutName
  )
  val secondFragmentClassContent = secondFragmentKt(
    packageName, useAndroidX, firstFragmentClass, secondFragmentClass, secondFragmentLayoutName
  )
  val firstFragmentLayoutContent = fragmentFirstLayout(useAndroidX, firstFragmentClass)
  val secondFragmentLayoutContent = fragmentSecondLayout(useAndroidX, secondFragmentClass)
  save(firstFragmentClassContent, srcOut.resolve("$firstFragmentClass.$ktOrJavaExt"))
  save(secondFragmentClassContent, srcOut.resolve("$secondFragmentClass.$ktOrJavaExt"))
  save(firstFragmentLayoutContent, resOut.resolve("layout/$firstFragmentLayoutName.xml"))
  save(secondFragmentLayoutContent, resOut.resolve("layout/$secondFragmentLayoutName.xml"))

  val navGraphContent = navGraphXml(
    packageName, firstFragmentClass, secondFragmentClass, firstFragmentLayoutName, secondFragmentLayoutName
  )
  mergeXml(navGraphContent, resOut.resolve("navigation/nav_graph.xml"))
  mergeXml(stringsXml, resOut.resolve("values/strings.xml"))
  mergeGradleFile(navigationKotlinBuildGradle, data.rootDir.resolve(data.name + "/build.gradle"))

  // TODO(b/142690180)
  val generateKotlin = true
  if (generateKotlin) {
    addDependency("android.arch.navigation:navigation-fragment-ktx:+")
    addDependency("android.arch.navigation:navigation-ui-ktx:+")
  } else {
    addDependency("android.arch.navigation:navigation-fragment:+")
    addDependency("android.arch.navigation:navigation-ui:+")
  }
  addSafeArgsPlugin(true, projectData.rootDir)

  open(simpleActivityPath)

  open(resOut.resolve("layout/$simpleLayoutName"))
  open(srcOut.resolve("$activityClass.$ktOrJavaExt"))
}
