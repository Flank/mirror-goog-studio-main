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
package com.android.tools.idea.wizard.template.impl.activities.common

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor


fun RecipeExecutor.addAllKotlinDependencies(data: ModuleTemplateData) {
  val projectData = data.projectTemplateData
  if (!data.isNewModule && projectData.language == Language.Kotlin) {
    applyPlugin("kotlin-android")
    addClasspathDependency("org.jetbrains.kotlin:kotlin-gradle-plugin:${projectData.kotlinVersion}")
  }
}

fun RecipeExecutor.addMaterialDependency(useAndroidX: Boolean) {
  if (useAndroidX) {
    addDependency("com.google.android.material:material:+")
  }
}

fun RecipeExecutor.addSupportWearableDependency() {
  addDependency("com.google.android.support:wearable:+", minRev = "2.8.1")
  // This is needed for the com.google.android.support:wearable as a provided dependency otherwise it's warned by lint
  addDependency("com.google.android.wearable:wearable:+", "provided", minRev = "2.8.1")
}
