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


import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.activityToLayout
import com.android.tools.idea.wizard.template.impl.activities.common.addAllKotlinDependencies
import com.android.tools.idea.wizard.template.impl.activities.googleMapsActivity.androidManifestXml
import com.android.tools.idea.wizard.template.impl.activities.googleMapsActivity.debugRes.values.googleMapsApiXml as debugGoogleMapsApiXml
import com.android.tools.idea.wizard.template.impl.activities.googleMapsActivity.releaseRes.values.googleMapsApiXml as releaseGoogleMapsApiXml
import com.android.tools.idea.wizard.template.impl.activities.googleMapsActivity.res.layout.activityMapXml
import com.android.tools.idea.wizard.template.impl.activities.googleMapsActivity.res.values.stringsXml
import com.android.tools.idea.wizard.template.impl.activities.googleMapsActivity.src.app_package.mapActivityJava
import com.android.tools.idea.wizard.template.impl.activities.googleMapsActivity.src.app_package.mapActivityKt
import java.io.File

fun RecipeExecutor.googleMapsActivityRecipe(
  moduleData: ModuleTemplateData,
  activityClass: String,
  activityTitle: String,
  isLauncher: Boolean,
  layoutName: String,
  packageName: String
) {
  val (projectData, srcOut, resOut, manifestOut) = moduleData
  val buildApi = moduleData.apis.buildApi
  val useAndroidX = moduleData.projectTemplateData.androidXSupport
  val ktOrJavaExt = projectData.language.extension
  val simpleName = activityToLayout(activityClass)
  addAllKotlinDependencies(moduleData)

  addDependency("com.google.android.gms:play-services-maps:+")
  addDependency("com.android.support:appcompat-v7:${buildApi}.+")

  mergeXml(androidManifestXml(activityClass, isLauncher, moduleData.isLibrary, packageName, simpleName),
           manifestOut.resolve("AndroidManifest.xml"))

  save(activityMapXml(activityClass, packageName), resOut.resolve("layout/${layoutName}.xml"))

  lateinit var finalResOut: File
  lateinit var finalDebugResOut: File
  lateinit var finalReleaseResOut: File
  if (moduleData.isDynamic) {
    finalResOut = moduleData.baseFeature!!.resDir
    finalDebugResOut = moduleData.baseFeature!!.dir.resolve("src/debug/res")
    finalReleaseResOut = moduleData.baseFeature!!.dir.resolve("src/release/res")
  }
  else {
    finalResOut = resOut
    finalDebugResOut = moduleData.rootDir.resolve("src/debug/res")
    finalReleaseResOut = moduleData.rootDir.resolve("src/release/res")
  }

  mergeXml(stringsXml(activityTitle, simpleName), finalResOut.resolve("values/strings.xml"))

  val mapActivity = when (projectData.language) {
    Language.Java -> mapActivityJava(activityClass, layoutName, packageName, useAndroidX)
    Language.Kotlin -> mapActivityKt(activityClass, layoutName, packageName, useAndroidX)
  }
  save(mapActivity, srcOut.resolve("${activityClass}.${ktOrJavaExt}"))

  mergeXml(debugGoogleMapsApiXml(projectData.debugKeystoreSha1!!, packageName),
           finalDebugResOut.resolve("values/google_maps_api.xml"))
  mergeXml(releaseGoogleMapsApiXml(), finalReleaseResOut.resolve("values/google_maps_api.xml"))
  open(srcOut.resolve("${activityClass}.${ktOrJavaExt}"))

  /* Display the API key instructions. */
  open(finalDebugResOut.resolve("values/google_maps_api.xml"))
}
