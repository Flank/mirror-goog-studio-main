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

package com.android.tools.idea.wizard.template.impl.other.appWidget

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.camelCaseToUnderlines
import com.android.tools.idea.wizard.template.impl.activities.common.addAllKotlinDependencies
import com.android.tools.idea.wizard.template.impl.other.appWidget.res.layout.appwidgetConfigureXml
import com.android.tools.idea.wizard.template.impl.other.appWidget.res.layout.appwidgetXml
import com.android.tools.idea.wizard.template.impl.other.appWidget.res.values.dimensXml
import com.android.tools.idea.wizard.template.impl.other.appWidget.res.values.stringsXml
import com.android.tools.idea.wizard.template.impl.other.appWidget.res.xml.appwidgetInfoXml
import com.android.tools.idea.wizard.template.impl.other.appWidget.src.app_package.appWidgetConfigureActivityJava
import com.android.tools.idea.wizard.template.impl.other.appWidget.src.app_package.appWidgetConfigureActivityKt
import com.android.tools.idea.wizard.template.impl.other.appWidget.src.app_package.appWidgetJava
import com.android.tools.idea.wizard.template.impl.other.appWidget.src.app_package.appWidgetKt
import java.io.File

fun RecipeExecutor.appWidgetRecipe(
  moduleData: ModuleTemplateData,
  className: String,
  placement: Placement,
  resizable: Resizeable,
  minWidth: MinimumCells,
  minHeight: MinimumCells,
  configurable: Boolean
) {
  val (projectData, srcOut, resOut, manifestOut) = moduleData
  val ktOrJavaExt = projectData.language.extension
  val packageName = moduleData.packageName
  val layoutName = camelCaseToUnderlines(className)
  addAllKotlinDependencies(moduleData)

  mergeXml(androidManifestXml(className, configurable, layoutName, packageName), manifestOut.resolve("AndroidManifest.xml"))

  copy(File("example_appwidget_preview.png"), resOut.resolve("drawable-nodpi/example_appwidget_preview.png"))
  save(appwidgetXml(), resOut.resolve("layout/${layoutName}.xml"))

  if (configurable) {
    save(appwidgetConfigureXml(), resOut.resolve("layout/${layoutName}_configure.xml"))
  }

  val minWidthDp = -30 + 70 * minWidth.name.toInt()
  val minHeightDp = -30 + 70 * minHeight.name.toInt()
  save(appwidgetInfoXml(minHeightDp, minWidthDp, className, configurable, layoutName, packageName, placement, resizable),
       resOut.resolve("xml/${layoutName}_info.xml"))
  mergeXml(stringsXml(configurable), resOut.resolve("values/strings.xml"))
  mergeXml(dimensXml(), resOut.resolve("values/dimens.xml"))

  val appWidget = when (projectData.language) {
    Language.Java -> appWidgetJava(projectData.applicationPackage, className, configurable, layoutName, packageName)
    Language.Kotlin -> appWidgetKt(projectData.applicationPackage, className, configurable, layoutName, packageName)
  }
  save(appWidget, srcOut.resolve("${className}.${ktOrJavaExt}"))

  if (configurable) {
    val appWidgetConfigureActivity = when (projectData.language) {
      Language.Java -> appWidgetConfigureActivityJava(projectData.applicationPackage, className, layoutName, packageName)
      Language.Kotlin -> appWidgetConfigureActivityKt(projectData.applicationPackage, className, layoutName, packageName)
    }
    save(appWidgetConfigureActivity, srcOut.resolve("${className}ConfigureActivity.${ktOrJavaExt}"))
  }

  open(srcOut.resolve("${className}.${ktOrJavaExt}"))
}
