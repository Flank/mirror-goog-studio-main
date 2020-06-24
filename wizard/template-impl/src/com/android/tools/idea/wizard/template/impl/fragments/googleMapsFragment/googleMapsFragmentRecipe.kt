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

package com.android.tools.idea.wizard.template.impl.fragments.googleMapsFragment

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.impl.activities.common.addAllKotlinDependencies
import com.android.tools.idea.wizard.template.impl.activities.googleMapsActivity.debugRes.values.googleMapsApiXml as debugGoogleMapsApiXml
import com.android.tools.idea.wizard.template.impl.fragments.googleMapsFragment.res.layout.fragmentMapXml
import com.android.tools.idea.wizard.template.impl.activities.googleMapsActivity.releaseRes.values.googleMapsApiXml as releaseGoogleMapsApiXml
import com.android.tools.idea.wizard.template.impl.fragments.googleMapsFragment.src.app_package.mapFragmentJava
import com.android.tools.idea.wizard.template.impl.fragments.googleMapsFragment.src.app_package.mapFragmentKt

fun RecipeExecutor.googleMapsFragmentRecipe(
  moduleData: ModuleTemplateData,
  fragmentClass: String,
  layoutName: String,
  packageName: String
) {
  val (projectData, srcOut, resOut, manifestOut) = moduleData
  val appCompatVersion = moduleData.apis.appCompatVersion
  val useAndroidX = moduleData.projectTemplateData.androidXSupport
  val ktOrJavaExt = projectData.language.extension
  addAllKotlinDependencies(moduleData)

  addDependency("com.google.android.gms:play-services-maps:+", toBase = moduleData.isDynamic)
  addDependency("com.android.support:appcompat-v7:${appCompatVersion}.+")

  mergeXml(androidManifestXml(), manifestOut.resolve("AndroidManifest.xml"))

  val debugResOut = moduleData.rootDir.resolve("src/debug/res")
  val releaseResOut = moduleData.rootDir.resolve("src/release/res")

  save(fragmentMapXml(fragmentClass, packageName), resOut.resolve("layout/${layoutName}.xml"))
  val mapFragment = when (projectData.language) {
    Language.Java -> mapFragmentJava(fragmentClass, layoutName, packageName, useAndroidX)
    Language.Kotlin -> mapFragmentKt(fragmentClass, layoutName, packageName, useAndroidX)
  }
  save(mapFragment, srcOut.resolve("${fragmentClass}.${ktOrJavaExt}"))

  mergeXml(debugGoogleMapsApiXml(projectData.debugKeystoreSha1!!, packageName), debugResOut.resolve("values/google_maps_api.xml"))
  mergeXml(releaseGoogleMapsApiXml(), releaseResOut.resolve("values/google_maps_api.xml"))

  open(srcOut.resolve("${fragmentClass}.${ktOrJavaExt}"))
  open(resOut.resolve("layout/${layoutName}.xml"))

  /* Display the API key instructions. */
  open(debugResOut.resolve("values/google_maps_api.xml"))
}
