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
package com.android.tools.idea.wizard.template.impl.common.navigation

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.RecipeExecutor
import java.io.File

// TODO(qumeric): move it and make it work
fun underscoreToCamelCase(
  string: String
): String = string // CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, string)

// TODO(qumeric)
fun RecipeExecutor.saveFragmentAndViewModel(
  fragmentPrefix: String,
  withSafeArgs: Boolean = false,
  resOut: File,
  srcOut: File,
  language: Language
) {
  val navFragmentPrefix = fragmentPrefix
  val firstFragmentClass = "${underscoreToCamelCase(fragmentPrefix)}Fragment"
  val navViewModelClass = "${underscoreToCamelCase(navFragmentPrefix)}ViewModel"

  val inputDir = File("root://activities/common/navigation/src")

  if (withSafeArgs) {
    val secondFragmentPrefix = "${fragmentPrefix}_second"
    val secondFragmentClass = "${underscoreToCamelCase(secondFragmentPrefix)}Fragment"
    val secondFragmentLayoutName = "fragment_${secondFragmentPrefix}"

    //save(fragment_second, resOut.resolve("layout/${secondFragmentLayoutName}.xml"))
    //save(SecondFragment, srcOut.resolve("ui/${fragmentPrefix}/${secondFragmentClass}.${language.extension}"))
    //save(fragment_first_with_safeargs, resOut.resolve("layout/fragment_${fragmentPrefix}.xml"))
    //save(FirstFragmentWithSafeArgs, srcOut.resolve("ui/${fragmentPrefix}/${underscoreToCamelCase(fragmentPrefix)}Fragment.${language.extension}"))
  //} else {
    //save(fragment_first, resOut.resolve("layout/fragment_${fragmentPrefix}.xml"))
    //save(FirstFragment, srcOut.resolve("ui/${fragmentPrefix}/${underscoreToCamelCase(fragmentPrefix)}Fragment.${language.extension}"))
  }

  //save(ViewModel, srcOut.resolve("ui/${fragmentPrefix}/${underscoreToCamelCase(fragmentPrefix)}ViewModel.${language.extension}"))
  //mergeXml(File("values/strings.xml", resOut.resolve("values/strings.xml"))
}

fun RecipeExecutor.navigationDependencies(
  generateKotlin: Boolean,
  useAndroidX: Boolean,
  buildApi: Int
) {
  addDependency("android.arch.navigation:navigation-fragment:+")
  addDependency("android.arch.navigation:navigation-ui:+")
  addDependency("android.arch.lifecycle:extensions:+")
  if (generateKotlin) {
    addDependency("android.arch.navigation:navigation-fragment-ktx:+")
    addDependency("android.arch.navigation:navigation-ui-ktx:+")
  }
  /*
  navigation-ui depends on the stable version of design library. This is to remove the
  lint warning for the generated project may not use the same version of the support
  library.
  */
  if (!useAndroidX) {
    addDependency("com.android.support:design:${buildApi}.+")
  }
}

fun RecipeExecutor.addSafeArgsPlugin(generateKotlin: Boolean, projectOut: File) {
  /*
  Only use the Java version of the plugin to avoid Java and Kotlin version of the plugins are
  added in the same project.
  */
  applyPlugin("androidx.navigation.safeargs")
  if (generateKotlin) {
    mergeGradleFile(navigationKotlinBuildGradle, projectOut.resolve("build.gradle"))
  }
}

fun RecipeExecutor.addSafeArgsPluginToClasspath(useAndroidX: Boolean) {
  if (useAndroidX) {
    addClasspathDependency("androidx.navigation:navigation-safe-args-gradle-plugin:+")
  }
  else {
    addClasspathDependency("android.arch.navigation:navigation-safe-args-gradle-plugin:+")
  }
}
