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

package com.android.tools.idea.wizard.template.impl.activities.loginActivity

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.activityToLayout
import com.android.tools.idea.wizard.template.impl.activities.common.addAllKotlinDependencies
import com.android.tools.idea.wizard.template.impl.activities.common.addLifecycleDependencies
import com.android.tools.idea.wizard.template.impl.activities.common.addMaterialDependency
import com.android.tools.idea.wizard.template.impl.activities.common.addViewBindingSupport
import com.android.tools.idea.wizard.template.impl.activities.common.generateManifestStrings
import com.android.tools.idea.wizard.template.impl.activities.common.generateThemeStyles
import com.android.tools.idea.wizard.template.impl.activities.loginActivity.res.layout.activityLoginXml
import com.android.tools.idea.wizard.template.impl.activities.loginActivity.res.layout_w936dp.activityLoginXml as activityLoginXmlW936dp
import com.android.tools.idea.wizard.template.impl.activities.loginActivity.res.values.dimensXml
import com.android.tools.idea.wizard.template.impl.activities.loginActivity.res.values.stringsXml
import com.android.tools.idea.wizard.template.impl.activities.loginActivity.res.values.dimensXmlHorizontalMargin
import com.android.tools.idea.wizard.template.impl.activities.loginActivity.src.app_package.data.loginDataSourceJava
import com.android.tools.idea.wizard.template.impl.activities.loginActivity.src.app_package.data.loginDataSourceKt
import com.android.tools.idea.wizard.template.impl.activities.loginActivity.src.app_package.data.loginRepositoryJava
import com.android.tools.idea.wizard.template.impl.activities.loginActivity.src.app_package.data.loginRepositoryKt
import com.android.tools.idea.wizard.template.impl.activities.loginActivity.src.app_package.data.model.loggedInUserJava
import com.android.tools.idea.wizard.template.impl.activities.loginActivity.src.app_package.data.model.loggedInUserKt
import com.android.tools.idea.wizard.template.impl.activities.loginActivity.src.app_package.data.resultJava
import com.android.tools.idea.wizard.template.impl.activities.loginActivity.src.app_package.data.resultKt
import com.android.tools.idea.wizard.template.impl.activities.loginActivity.src.app_package.ui.login.loggedInUserViewJava
import com.android.tools.idea.wizard.template.impl.activities.loginActivity.src.app_package.ui.login.loggedInUserViewKt
import com.android.tools.idea.wizard.template.impl.activities.loginActivity.src.app_package.ui.login.loginActivityJava
import com.android.tools.idea.wizard.template.impl.activities.loginActivity.src.app_package.ui.login.loginActivityKt
import com.android.tools.idea.wizard.template.impl.activities.loginActivity.src.app_package.ui.login.loginFormStateJava
import com.android.tools.idea.wizard.template.impl.activities.loginActivity.src.app_package.ui.login.loginFormStateKt
import com.android.tools.idea.wizard.template.impl.activities.loginActivity.src.app_package.ui.login.loginResultJava
import com.android.tools.idea.wizard.template.impl.activities.loginActivity.src.app_package.ui.login.loginResultKt
import com.android.tools.idea.wizard.template.impl.activities.loginActivity.src.app_package.ui.login.loginViewModelFactoryJava
import com.android.tools.idea.wizard.template.impl.activities.loginActivity.src.app_package.ui.login.loginViewModelFactoryKt
import com.android.tools.idea.wizard.template.impl.activities.loginActivity.src.app_package.ui.login.loginViewModelJava
import com.android.tools.idea.wizard.template.impl.activities.loginActivity.src.app_package.ui.login.loginViewModelKt

fun RecipeExecutor.loginActivityRecipe(
  moduleData: ModuleTemplateData,
  activityClass: String,
  layoutName: String,
  packageName: String
) {
  val (projectData, srcOut, resOut, manifestOut) = moduleData
  val apis = moduleData.apis
  val appCompatVersion = apis.appCompatVersion
  val useAndroidX = moduleData.projectTemplateData.androidXSupport
  val ktOrJavaExt = projectData.language.extension
  addAllKotlinDependencies(moduleData)

  addDependency("com.android.support:appcompat-v7:${appCompatVersion}.+")
  addDependency("com.android.support:design:${appCompatVersion}.+")
  addDependency("com.android.support:support-annotations:${appCompatVersion}.+")
  addDependency("com.android.support.constraint:constraint-layout:+")
  addLifecycleDependencies(useAndroidX)
  addMaterialDependency(useAndroidX)
  addViewBindingSupport(moduleData.viewBindingSupport, true)

  val baseFeatureResOut = moduleData.baseFeature?.resDir
  val simpleName = activityToLayout(activityClass)
  generateThemeStyles(moduleData.themesData.main, useAndroidX, baseFeatureResOut ?: resOut)
  generateManifestStrings(activityClass, baseFeatureResOut ?: resOut, moduleData.isNewModule, generateActivityTitle = true)
  mergeXml(androidManifestXml(activityClass, simpleName,
                              isLauncher = moduleData.isNewModule, isLibrary = moduleData.isLibrary, isNewModule = moduleData.isNewModule),
           manifestOut.resolve("AndroidManifest.xml"))
  mergeXml(dimensXml(), resOut.resolve("values/dimens.xml"))
  mergeXml(dimensXmlHorizontalMargin(48), resOut.resolve("values-land/dimens.xml"))
  mergeXml(dimensXmlHorizontalMargin(48), resOut.resolve("values-w600dp/dimens.xml"))
  mergeXml(dimensXmlHorizontalMargin(200), resOut.resolve("values-w1240dp/dimens.xml"))
  mergeXml(stringsXml(simpleName, activityClass, moduleData.isNewModule), resOut.resolve("values/strings.xml"))
  save(activityLoginXml(activityClass, packageName, useAndroidX, apis.minApi.api), resOut.resolve("layout/${layoutName}.xml"))
  // We can use the same layout in the layout and layout-w1240dp directories
  save(activityLoginXml(activityClass, packageName, useAndroidX, apis.minApi.api), resOut.resolve("layout-w1240dp/${layoutName}.xml"))
  save(activityLoginXmlW936dp(activityClass, packageName, useAndroidX, apis.minApi.api), resOut.resolve("layout-w936dp/${layoutName}.xml"))

  val isViewBindingSupported = moduleData.viewBindingSupport.isViewBindingSupported()
  val loginActivity = when (projectData.language) {
    Language.Java -> loginActivityJava(
      layoutName = layoutName,
      packageName = packageName,
      useAndroidX = useAndroidX,
      isViewBindingSupported = isViewBindingSupported
    )
    Language.Kotlin -> loginActivityKt(
      activityClass = activityClass,
      layoutName = layoutName,
      packageName = packageName,
      useAndroidX = useAndroidX,
      isViewBindingSupported = isViewBindingSupported
    )
  }
  save(loginActivity, srcOut.resolve("ui/login/${activityClass}.${ktOrJavaExt}"))

  val loginViewModel = when (projectData.language) {
    Language.Java -> loginViewModelJava(packageName, useAndroidX)
    Language.Kotlin -> loginViewModelKt(packageName, useAndroidX)
  }
  save(loginViewModel, srcOut.resolve("ui/login/LoginViewModel.${ktOrJavaExt}"))

  val loginViewModelFactory = when (projectData.language) {
    Language.Java -> loginViewModelFactoryJava(packageName, useAndroidX)
    Language.Kotlin -> loginViewModelFactoryKt(packageName, useAndroidX)
  }
  save(loginViewModelFactory, srcOut.resolve("ui/login/LoginViewModelFactory.${ktOrJavaExt}"))

  val loggedInUser = when (projectData.language) {
    Language.Java -> loggedInUserJava(packageName)
    Language.Kotlin -> loggedInUserKt(packageName)
  }
  save(loggedInUser, srcOut.resolve("data/model/LoggedInUser.${ktOrJavaExt}"))

  val loginDataSource = when (projectData.language) {
    Language.Java -> loginDataSourceJava(packageName)
    Language.Kotlin -> loginDataSourceKt(packageName)
  }
  save(loginDataSource, srcOut.resolve("data/LoginDataSource.${ktOrJavaExt}"))

  val loginRepository = when (projectData.language) {
    Language.Java -> loginRepositoryJava(packageName)
    Language.Kotlin -> loginRepositoryKt(packageName)
  }
  save(loginRepository, srcOut.resolve("data/LoginRepository.${ktOrJavaExt}"))

  val result = when (projectData.language) {
    Language.Java -> resultJava(packageName)
    Language.Kotlin -> resultKt(packageName)
  }
  save(result, srcOut.resolve("data/Result.${ktOrJavaExt}"))

  val loginFormState = when (projectData.language) {
    Language.Java -> loginFormStateJava(packageName, useAndroidX)
    Language.Kotlin -> loginFormStateKt(packageName)
  }
  save(loginFormState, srcOut.resolve("ui/login/LoginFormState.${ktOrJavaExt}"))

  val loginResult = when (projectData.language) {
    Language.Java -> loginResultJava(packageName, useAndroidX)
    Language.Kotlin -> loginResultKt(packageName)
  }
  save(loginResult, srcOut.resolve("ui/login/LoginResult.${ktOrJavaExt}"))

  val loggedInUserView = when (projectData.language) {
    Language.Java -> loggedInUserViewJava(packageName)
    Language.Kotlin -> loggedInUserViewKt(packageName)
  }
  save(loggedInUserView, srcOut.resolve("ui/login/LoggedInUserView.${ktOrJavaExt}"))

  open(srcOut.resolve("${activityClass}.${ktOrJavaExt}"))
}
