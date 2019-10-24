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

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor

// Macro used to add the necessary dependencies to support kotlin to an app build.gradle

/*fun addKotlinPlugins() {
  if (generateKotlin)
    //<#compress>
      apply plugin : 'kotlin-android'
      apply plugin : 'kotlin-android-extensions'
    //</#compress>
  }
}

fun addKotlinDependencies(): String? =
  if (generateKotlin) {
    """${getConfigurationName("compile")} "org.jetbrains.kotlin:kotlin-stdlib-jdk7:$kotlin_version""""
  } else {
    null
  }



fun addKotlinToBaseProject() {
  if (language == "Kotlin") {
    merge("root://activities/common/kotlin.gradle.ftl", "$topOut/build.gradle")
  }
}*/

// TODO: <apply plugin /> Is adding the dependencies at the *end* of build.gradle
// TODO: The two macros above, addKotlinPlugins and addKotlinDependencies, are duplicating the work of addAllKotlinDependencies, when
//       creating a new project (isNewProject == true). The only reason is the above bug on <apply plugin />
fun RecipeExecutor.addAllKotlinDependencies(data: ModuleTemplateData) {
  val projectData = data.projectTemplateData
  if (!data.isNew && projectData.language == Language.Kotlin) {
    applyPlugin("kotlin-android")
    applyPlugin("kotlin-android-extensions")
    if (!hasDependency("org.jetbrains.kotlin:kotlin-stdlib")) {
      addDependency("org.jetbrains.kotlin:kotlin-stdlib-jdk7:\$kotlin_version")
      mergeGradleFile(kotlinGradle(), projectData.rootDir.resolve(BUILD_GRADLE))
    }
  }
}