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
package com.android.tools.idea.wizard.template.impl.activities.cppEmptyActivity

import com.android.tools.idea.wizard.template.DEFAULT_CMAKE_VERSION
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.PackageName
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.cMakeListsTxt
import com.android.tools.idea.wizard.template.deriveNativeLibraryName
import com.android.tools.idea.wizard.template.impl.activities.common.addAllKotlinDependencies
import com.android.tools.idea.wizard.template.impl.activities.common.addViewBindingSupport
import com.android.tools.idea.wizard.template.impl.activities.common.generateManifest
import com.android.tools.idea.wizard.template.impl.activities.common.generateSimpleLayout
import com.android.tools.idea.wizard.template.impl.activities.cppEmptyActivity.src.cppEmptyActivityJava
import com.android.tools.idea.wizard.template.impl.activities.cppEmptyActivity.src.cppEmptyActivityKt
import com.android.tools.idea.wizard.template.impl.activities.cppEmptyActivity.src.nativeLibCpp

fun RecipeExecutor.generateCppEmptyActivity(
  moduleData: ModuleTemplateData,
  activityClass: String,
  layoutName: String,
  isLauncher: Boolean,
  packageName: PackageName,
  cppFlags: String
) {
  val (projectData, srcOut) = moduleData
  val useAndroidX = projectData.androidXSupport
  val ktOrJavaExt = projectData.language.extension

  addDependency("com.android.support:appcompat-v7:${moduleData.apis.appCompatVersion}.+")
  setCppOptions(cppFlags = cppFlags, cppPath = "src/main/cpp/CMakeLists.txt", cppVersion = DEFAULT_CMAKE_VERSION)

  generateManifest(
    moduleData , activityClass, packageName, isLauncher, false,
    generateActivityTitle = false
  )

  addAllKotlinDependencies(moduleData)
  addViewBindingSupport(moduleData.viewBindingSupport, true)

  generateSimpleLayout(moduleData, activityClass, layoutName, includeCppSupport = true)

  val simpleActivityPath = srcOut.resolve("$activityClass.$ktOrJavaExt")

  val isViewBindingSupported = moduleData.viewBindingSupport.isViewBindingSupported()
  val libraryName = packageName.deriveNativeLibraryName()
  val simpleActivity = when (projectData.language) {
    Language.Kotlin -> cppEmptyActivityKt(
      packageName = packageName,
      activityClass = activityClass,
      layoutName = layoutName,
      useAndroidX = useAndroidX,
      isViewBindingSupported = isViewBindingSupported,
      libraryName = libraryName,
    )
    Language.Java -> cppEmptyActivityJava(
      packageName = packageName,
      activityClass = activityClass,
      layoutName = layoutName,
      useAndroidX = useAndroidX,
      isViewBindingSupported = isViewBindingSupported,
      libraryName = libraryName,
    )
  }
  save(simpleActivity, simpleActivityPath)

  val nativeSrcOut = moduleData.rootDir.resolve("src/main/cpp")
  val nativeLibCpp = "native-lib.cpp"
  save(nativeLibCpp(packageName, activityClass), nativeSrcOut.resolve(nativeLibCpp))
    save(cMakeListsTxt(nativeLibCpp, libraryName), nativeSrcOut.resolve("CMakeLists.txt"))

  open(simpleActivityPath)
}
