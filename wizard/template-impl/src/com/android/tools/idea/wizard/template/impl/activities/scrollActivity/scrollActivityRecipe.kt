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
import com.android.tools.idea.wizard.template.impl.activities.common.addAllKotlinDependencies
import com.android.tools.idea.wizard.template.impl.activities.common.generateManifest
import com.android.tools.idea.wizard.template.impl.activities.common.generateNoActionBarStyles
import com.android.tools.idea.wizard.template.impl.activities.common.generateSimpleMenu
import com.android.tools.idea.wizard.template.impl.activities.scrollActivity.res.layout.appBarXml
import com.android.tools.idea.wizard.template.impl.activities.scrollActivity.res.layout.simpleXml
import com.android.tools.idea.wizard.template.impl.activities.scrollActivity.res.values.dimensXml
import com.android.tools.idea.wizard.template.impl.activities.scrollActivity.res.values.stringsXml
import com.android.tools.idea.wizard.template.impl.activities.scrollActivity.src.app_package.scrollActivityJava
import com.android.tools.idea.wizard.template.impl.activities.scrollActivity.src.app_package.scrollActivityKt

fun RecipeExecutor.scrollActivityRecipe(
  moduleData: ModuleTemplateData, activityClass: String,
  layoutName: String,
  activityTitle: String,
  contentLayoutName: String,
  menuName: String,
  isLauncher: Boolean,
  packageName: String) {

  val (projectData, srcOut, resOut, _) = moduleData
  val apis = moduleData.apis
  val buildApi = apis.buildApi!!
  val useAndroidX = moduleData.projectTemplateData.androidXSupport
  val useMaterial2 = useAndroidX || hasDependency("com.google.android.material:material")
  val ktOrJavaExt = projectData.language.extension
  addAllKotlinDependencies(moduleData)

  addDependency("com.android.support:appcompat-v7:${buildApi}.+")
  addDependency("com.android.support:design:${buildApi}.+")

  generateManifest(
    moduleData, activityClass, activityTitle, packageName, isLauncher, true,
    requireTheme = true, generateActivityTitle = true, useMaterial2 = useMaterial2
  )
  mergeXml(stringsXml(), resOut.resolve("values/strings.xml"))
  mergeXml(dimensXml(), resOut.resolve("values/dimens.xml"))
  generateNoActionBarStyles(moduleData.baseFeature?.resDir, resOut, moduleData.themesData)
  generateSimpleMenu(packageName, activityClass, resOut, menuName)
  save(appBarXml(activityClass, packageName, contentLayoutName,
                 moduleData.themesData.appBarOverlay.name, moduleData.themesData.popupOverlay.name, useAndroidX, useMaterial2),
       resOut.resolve("layout/${layoutName}.xml"))
  save(simpleXml(activityClass, layoutName, packageName, useAndroidX), resOut.resolve("layout/${contentLayoutName}.xml"))

  open(resOut.resolve("layout/${contentLayoutName}.xml"))

  val scrollActivity = when (projectData.language) {
    Language.Java -> scrollActivityJava(activityClass, moduleData.projectTemplateData.applicationPackage, moduleData.isNew,
                                        layoutName, menuName, packageName, useAndroidX, useMaterial2)
    Language.Kotlin -> scrollActivityKt(activityClass, moduleData.isNew, layoutName, menuName, packageName, useAndroidX, useMaterial2)
  }
  save(scrollActivity, srcOut.resolve("${activityClass}.${ktOrJavaExt}"))

  open(srcOut.resolve("${activityClass}.${ktOrJavaExt}"))
}
