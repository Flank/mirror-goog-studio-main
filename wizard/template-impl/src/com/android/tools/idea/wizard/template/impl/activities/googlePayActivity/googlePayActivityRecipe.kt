/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.idea.wizard.template.impl.activities.googlePayActivity

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.activityToLayout
import com.android.tools.idea.wizard.template.impl.activities.common.addAllKotlinDependencies
import com.android.tools.idea.wizard.template.impl.activities.common.addViewBindingSupport
import com.android.tools.idea.wizard.template.impl.activities.googlePayActivity.app_package.checkoutActivityJava
import com.android.tools.idea.wizard.template.impl.activities.googlePayActivity.app_package.checkoutActivityKt
import com.android.tools.idea.wizard.template.impl.activities.googlePayActivity.app_package.checkoutViewModelJava
import com.android.tools.idea.wizard.template.impl.activities.googlePayActivity.app_package.checkoutViewModelKt
import com.android.tools.idea.wizard.template.impl.activities.googlePayActivity.app_package.constantsJava
import com.android.tools.idea.wizard.template.impl.activities.googlePayActivity.app_package.constantsKotlin
import com.android.tools.idea.wizard.template.impl.activities.googlePayActivity.app_package.paymentsUtilJava
import com.android.tools.idea.wizard.template.impl.activities.googlePayActivity.app_package.paymentsUtilKotlin
import com.android.tools.idea.wizard.template.impl.activities.googlePayActivity.res.layout.activityCheckoutXml
import com.android.tools.idea.wizard.template.impl.activities.googlePayActivity.res.values.stringsXml
import java.io.File

fun RecipeExecutor.googlePayActivityRecipe(
  moduleData: ModuleTemplateData,
  activityClass: String,
  viewModelClass: String,
  layoutName: String,
  isLauncher: Boolean,
  packageName: String
) {
  val (projectData, srcOut, resOut, manifestOut) = moduleData

  // Dependencies and config
  addAllKotlinDependencies(moduleData)
  addViewBindingSupport(moduleData.viewBindingSupport, true)
  addDependency("com.google.android.gms:play-services-wallet:+")
  addDependency("com.android.support:appcompat-v7:${moduleData.apis.appCompatVersion}.+")
  addDependency("androidx.lifecycle:lifecycle-viewmodel-ktx:+")
  addDependency("androidx.lifecycle:lifecycle-livedata-ktx:+")
  if (projectData.language == Language.Kotlin) {
    addDependency("androidx.activity:activity-ktx:+")
  }

  // Create manifest
  val simpleName = activityToLayout(activityClass)
  mergeXml(
    androidManifestXml(
        activityClass, isLauncher, moduleData.isLibrary, packageName, simpleName, moduleData.isNewModule, moduleData.themesData),
    manifestOut.resolve("AndroidManifest.xml"))

  // Copy static resources
  val resLocation: File = if (moduleData.isDynamic) moduleData.baseFeature!!.resDir else resOut
  copy(File("google-pay-activity"), resLocation)

  // Generated resources
  mergeXml(stringsXml(activityClass, simpleName), resLocation.resolve("values/strings.xml"))
  save(activityCheckoutXml(activityClass, packageName), resLocation.resolve("layout/$layoutName.xml"))

  // Generate Constants class
  val ktOrJavaExt = projectData.language.extension
  val constants = when (projectData.language) {
    Language.Java -> constantsJava(packageName)
    Language.Kotlin -> constantsKotlin(packageName)
  }
  val constantsOut = srcOut.resolve("Constants.$ktOrJavaExt")
  save(constants, constantsOut)

  // Generate payments utility class
  val paymentsUtil = when (projectData.language) {
    Language.Java -> paymentsUtilJava(packageName)
    Language.Kotlin -> paymentsUtilKotlin(packageName)
  }
  val paymentsUtilOut = srcOut.resolve("util/PaymentsUtil.$ktOrJavaExt")
  save(paymentsUtil, paymentsUtilOut)

  // Add view model class
  val checkoutViewModel = when (projectData.language) {
    Language.Java -> checkoutViewModelJava(
      viewModelClass = viewModelClass,
      packageName = packageName)
    Language.Kotlin -> checkoutViewModelKt(
      viewModelClass = viewModelClass,
      packageName = packageName)
  }

  val viewModelOut = srcOut.resolve("viewmodel")
  save(checkoutViewModel, viewModelOut.resolve("$viewModelClass.$ktOrJavaExt"))

  // Add activity class
  val isViewBindingSupported = moduleData.viewBindingSupport.isViewBindingSupported()
  val checkoutActivity = when (projectData.language) {
    Language.Java -> checkoutActivityJava(
      activityClass = activityClass,
      viewModelClass = viewModelClass,
      layoutName = layoutName,
      packageName = packageName,
      applicationPackage = projectData.applicationPackage,
      isViewBindingSupported = isViewBindingSupported)
    Language.Kotlin -> checkoutActivityKt(
      activityClass = activityClass,
      viewModelClass = viewModelClass,
      layoutName = layoutName,
      packageName = packageName,
      applicationPackage = projectData.applicationPackage,
      isViewBindingSupported = isViewBindingSupported)
  }

  save(checkoutActivity, srcOut.resolve("$activityClass.$ktOrJavaExt"))
  open(srcOut.resolve("$activityClass.$ktOrJavaExt"))
}
