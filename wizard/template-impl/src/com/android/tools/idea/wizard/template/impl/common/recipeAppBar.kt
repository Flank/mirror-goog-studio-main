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

import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.ThemesData
import com.android.tools.idea.wizard.template.impl.common.res.layout.appBarLayoutXml
import com.android.tools.idea.wizard.template.impl.common.res.values.appBarDimens
import java.io.File

fun RecipeExecutor.recipeAppBar(
  buildApi: Int, baseFeatureResOut: File?, resDir: File, appBarLayoutName: String, themesData: ThemesData, useAndroidX: Boolean,
  useMaterial2: Boolean, packageName: String, activityClass: String, simpleLayoutName: String
) {
    addDependency("com.android.support:appcompat-v7:$buildApi.+")

    addDependency("com.android.support:design:$buildApi.+")

    val layout = appBarLayoutXml(useAndroidX, useMaterial2, packageName, activityClass, themesData, simpleLayoutName)

    save(layout, resDir.resolve("layout/$appBarLayoutName.xml"), true, true)

    mergeXml(appBarDimens, resDir.resolve("values/dimens.xml"))

    recipeNoActionBar(baseFeatureResOut, resDir, themesData)
}