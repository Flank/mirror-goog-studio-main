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
package com.android.tools.idea.wizard.template.impl.common

import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.PackageName
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.impl.common.res.layout.simpleLayoutXml

fun RecipeExecutor.recipeSimple(
  data : ModuleTemplateData, activityClass: String, simpleLayoutName: String, excludeMenu: Boolean,
  packageName: PackageName, menuName: String
) {
  val projectData = data.projectTemplateData
  val buildApi = projectData.buildApi
  val resOut = data.resDir
  addDependency("com.android.support:appcompat-v7:$buildApi.+")
  addDependency("com.android.support.constraint:constraint-layout:+")

  val simpleLayout = simpleLayoutXml(
    data.isNew, false, projectData.androidXSupport, data.packageName, activityClass, "appBarLayout"
  )

  save(simpleLayout, resOut.resolve("layout/$simpleLayoutName.xml"), true, true)

  if (data.isNew && !excludeMenu) {
    recipeSimpleMenu( packageName, activityClass, data.resDir, menuName)
  }
}