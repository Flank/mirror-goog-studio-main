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

package com.android.tools.idea.wizard.template.impl.other.service

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.impl.activities.common.addAllKotlinDependencies
import com.android.tools.idea.wizard.template.impl.other.service.src.app_package.serviceJava
import com.android.tools.idea.wizard.template.impl.other.service.src.app_package.serviceKt

fun RecipeExecutor.serviceRecipe(
  moduleData: ModuleTemplateData,
  className: String,
  isExported: Boolean,
  isEnabled: Boolean
) {
  val (projectData, srcOut, resOut, manifestOut) = moduleData
  val ktOrJavaExt = projectData.language.extension
  val packageName = moduleData.packageName
  addAllKotlinDependencies(moduleData)

  mergeXml(androidManifestXml(className, isEnabled, isExported, packageName), manifestOut.resolve("AndroidManifest.xml"))
  val service = when (projectData.language) {
    Language.Java -> serviceJava(className, packageName)
    Language.Kotlin -> serviceKt(className, packageName)
  }
  save(service, srcOut.resolve("${className}.${ktOrJavaExt}"))

  open(srcOut.resolve("${className}.${ktOrJavaExt}"))
}
