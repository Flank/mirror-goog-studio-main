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

package com.android.tools.idea.wizard.template.impl.other.watchFaceService

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.impl.activities.common.addAllKotlinDependencies
import com.android.tools.idea.wizard.template.impl.activities.common.addSupportWearableDependency
import com.android.tools.idea.wizard.template.impl.other.watchFaceService.res.values.colorsXml
import com.android.tools.idea.wizard.template.impl.other.watchFaceService.res.values.dimensXml
import com.android.tools.idea.wizard.template.impl.other.watchFaceService.res.values.stringsXml
import com.android.tools.idea.wizard.template.impl.other.watchFaceService.res.xml.watchFaceXml
import com.android.tools.idea.wizard.template.impl.other.watchFaceService.src.app_package.myAnalogWatchFaceServiceJava
import com.android.tools.idea.wizard.template.impl.other.watchFaceService.src.app_package.myAnalogWatchFaceServiceKt
import com.android.tools.idea.wizard.template.impl.other.watchFaceService.src.app_package.myDigitalWatchFaceServiceJava
import com.android.tools.idea.wizard.template.impl.other.watchFaceService.src.app_package.myDigitalWatchFaceServiceKt
import java.io.File

fun RecipeExecutor.watchFaceServiceRecipe(
  moduleData: ModuleTemplateData,
  serviceClass: String,
  watchFaceStyle: WatchFaceStyle,
  isInteractive: Boolean,
  packageName: String
) {
  val (projectData, srcOut, resOut, manifestOut) = moduleData
  val useAndroidX = moduleData.projectTemplateData.androidXSupport
  val ktOrJavaExt = projectData.language.extension
  addAllKotlinDependencies(moduleData)

  addSupportWearableDependency()
  addDependency("com.google.android.gms:play-services-base:+")

  mergeXml(androidManifestXml(packageName, serviceClass, watchFaceStyle), manifestOut.resolve("AndroidManifest.xml"))
  mergeXml(androidManifestPermissionsXml(), manifestOut.resolve("AndroidManifest.xml"))
  mergeXml(stringsXml(watchFaceStyle), resOut.resolve("values/strings.xml"))
  mergeXml(watchFaceXml(), resOut.resolve("xml/watch_face.xml"))

  if (watchFaceStyle == WatchFaceStyle.Analog) {
    when (projectData.language) {
      Language.Java -> addDependency("androidx.palette:palette:+")
      Language.Kotlin -> addDependency("androidx.palette:palette-ktx:+")
    }
    copy(File("preview_analog.png"), resOut.resolve("drawable-nodpi/preview_analog.png"))
    copy(File("watchface_service_bg.png"), resOut.resolve("drawable-nodpi/watchface_service_bg.png"))
  }
  else if (watchFaceStyle == WatchFaceStyle.Digital) {
    mergeXml(dimensXml(), resOut.resolve("values/dimens.xml"))
    mergeXml(colorsXml(), resOut.resolve("values/colors.xml"))
    copy(File("preview_digital.png"), resOut.resolve("drawable-nodpi/preview_digital.png"))
    copy(File("preview_digital_circular.png"), resOut.resolve("drawable-nodpi/preview_digital_circular.png"))
  }

  if (watchFaceStyle == WatchFaceStyle.Analog) {
    val myAnalogWatchFaceService = when (projectData.language) {
      Language.Java -> myAnalogWatchFaceServiceJava(isInteractive, packageName, serviceClass, useAndroidX)
      Language.Kotlin -> myAnalogWatchFaceServiceKt(isInteractive, packageName, serviceClass, useAndroidX)
    }
    save(myAnalogWatchFaceService, srcOut.resolve("${serviceClass}.${ktOrJavaExt}"))
    open(srcOut.resolve("${serviceClass}.${ktOrJavaExt}"))
  }
  else if (watchFaceStyle == WatchFaceStyle.Digital) {
    val myDigitalWatchFaceService = when (projectData.language) {
      Language.Java -> myDigitalWatchFaceServiceJava(isInteractive, packageName, serviceClass, useAndroidX)
      Language.Kotlin -> myDigitalWatchFaceServiceKt(isInteractive, packageName, serviceClass, useAndroidX)
    }
    save(myDigitalWatchFaceService, srcOut.resolve("${serviceClass}.${ktOrJavaExt}"))
    open(srcOut.resolve("${serviceClass}.${ktOrJavaExt}"))
  }
}
