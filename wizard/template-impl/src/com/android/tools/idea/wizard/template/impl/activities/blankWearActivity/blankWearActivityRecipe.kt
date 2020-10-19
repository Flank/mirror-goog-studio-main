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

package com.android.tools.idea.wizard.template.impl.activities.blankWearActivity

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.impl.activities.blankWearActivity.res.layout.blankActivityXml
import com.android.tools.idea.wizard.template.impl.activities.blankWearActivity.res.values.dimensXml
import com.android.tools.idea.wizard.template.impl.activities.blankWearActivity.res.values.stringsXml
import com.android.tools.idea.wizard.template.impl.activities.blankWearActivity.res.values_round.stringsXml as stringsXmlRound
import com.android.tools.idea.wizard.template.impl.activities.blankWearActivity.src.app_package.blankActivityJava
import com.android.tools.idea.wizard.template.impl.activities.blankWearActivity.src.app_package.blankActivityKt
import com.android.tools.idea.wizard.template.impl.activities.common.addAllKotlinDependencies
import com.android.tools.idea.wizard.template.impl.activities.common.addViewBindingSupport

fun RecipeExecutor.blankWearActivityRecipe(
  moduleData: ModuleTemplateData,
  activityClass: String,
  layoutName: String,
  isLauncher: Boolean,
  packageName: String
) {
  val (projectData, srcOut, resOut, manifestOut) = moduleData
  val useAndroidX = moduleData.projectTemplateData.androidXSupport
  val ktOrJavaExt = projectData.language.extension
  addAllKotlinDependencies(moduleData)
  addViewBindingSupport(moduleData.viewBindingSupport, true)

  mergeXml(androidManifestXml(activityClass, isLauncher, moduleData.isLibrary, moduleData.isNewModule, packageName),
           manifestOut.resolve("AndroidManifest.xml"))
  mergeXml(androidManifestPermissionsXml(), manifestOut.resolve("AndroidManifest.xml"))

  mergeXml(stringsXml(activityClass, moduleData.isNewModule), resOut.resolve("values/strings.xml"))
  mergeXml(dimensXml(), resOut.resolve("values/dimens.xml"))
  mergeXml(stringsXmlRound(), resOut.resolve("values-round/strings.xml"))
  save(blankActivityXml(activityClass, packageName, useAndroidX), resOut.resolve("layout/${layoutName}.xml"))
  addDependency("com.android.support:wear:+", minRev = if (useAndroidX) "1.1.0" else null)

  val isViewBindingSupported = moduleData.viewBindingSupport.isViewBindingSupported()
  val blankActivity = when (projectData.language) {
    Language.Java -> blankActivityJava(
      activityClass = activityClass,
      layoutName = layoutName,
      packageName = packageName,
      isViewBindingSupported = isViewBindingSupported
    )
    Language.Kotlin -> blankActivityKt(
      activityClass = activityClass,
      layoutName = layoutName,
      packageName = packageName,
      isViewBindingSupported = isViewBindingSupported
    )
  }
  save(blankActivity, srcOut.resolve("${activityClass}.${ktOrJavaExt}"))

  open(srcOut.resolve("${activityClass}.${ktOrJavaExt}"))
  open(resOut.resolve("layout/${layoutName}.xml"))
}
