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

package com.android.tools.idea.wizard.template.impl.activities.googleMapsWearActivity

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.impl.activities.common.addAllKotlinDependencies
import com.android.tools.idea.wizard.template.impl.activities.common.addViewBindingSupport
import com.android.tools.idea.wizard.template.impl.activities.googleMapsActivity.debugRes.values.googleMapsApiXml as debugGoogleMapsApiXml
import com.android.tools.idea.wizard.template.impl.activities.googleMapsActivity.releaseRes.values.googleMapsApiXml as releaseGoogleMapsApiXml
import com.android.tools.idea.wizard.template.impl.activities.googleMapsWearActivity.res.layout.activityMapXml
import com.android.tools.idea.wizard.template.impl.activities.googleMapsWearActivity.res.values.stringsXml
import com.android.tools.idea.wizard.template.impl.activities.googleMapsWearActivity.src.app_package.mapActivityJava
import com.android.tools.idea.wizard.template.impl.activities.googleMapsWearActivity.src.app_package.mapActivityKt

fun RecipeExecutor.googleMapsWearActivityRecipe(
  moduleData: ModuleTemplateData, activityClass: String,
  layoutName: String,
  isLauncher: Boolean,
  packageName: String
) {
  val (projectData, srcOut, resOut, manifestOut) = moduleData
  val useAndroidX = moduleData.projectTemplateData.androidXSupport
  val ktOrJavaExt = projectData.language.extension
  addAllKotlinDependencies(moduleData)
  addViewBindingSupport(moduleData.viewBindingSupport, true)

  addDependency("com.google.android.gms:play-services-wearable:+")
  addDependency("com.google.android.gms:play-services-maps:+", toBase = moduleData.isDynamic)
  addDependency("com.android.support:wear:+")

  mergeXml(androidManifestXml(activityClass, isLauncher, moduleData.isLibrary, moduleData.isNewModule, packageName),
           manifestOut.resolve("AndroidManifest.xml"))
  mergeXml(androidManifestPermissionsXml(), manifestOut.resolve("AndroidManifest.xml"))

  save(activityMapXml(activityClass, packageName, useAndroidX), resOut.resolve("layout/${layoutName}.xml"))
  mergeXml(stringsXml(activityClass, moduleData.isNewModule), resOut.resolve("values/strings.xml"))
  save(activityMapXml(activityClass, packageName, useAndroidX), resOut.resolve("layout/${layoutName}.xml"))

  val isViewBindingSupported = moduleData.viewBindingSupport.isViewBindingSupported()
  val mapActivity = when (projectData.language) {
    Language.Java -> mapActivityJava(
      activityClass = activityClass,
      layoutName = layoutName,
      packageName = packageName,
      useAndroidX = useAndroidX,
      isViewBindingSupported = isViewBindingSupported
    )
    Language.Kotlin -> mapActivityKt(
      activityClass = activityClass,
      layoutName = layoutName,
      packageName = packageName,
      useAndroidX = useAndroidX,
      isViewBindingSupported = isViewBindingSupported
    )
  }
  save(mapActivity, srcOut.resolve("${activityClass}.${ktOrJavaExt}"))

  val debugResOut = moduleData.rootDir.resolve("src/debug/res")
  val releaseResOut = moduleData.rootDir.resolve("src/release/res")
  open(srcOut.resolve("${activityClass}.${ktOrJavaExt}"))
  mergeXml(debugGoogleMapsApiXml(projectData.debugKeystoreSha1!!, packageName), debugResOut.resolve("values/google_maps_api.xml"))
  mergeXml(releaseGoogleMapsApiXml(), releaseResOut.resolve("values/google_maps_api.xml"))
  /* Display the API key instructions. */
  open(debugResOut.resolve("values/google_maps_api.xml"))
}
