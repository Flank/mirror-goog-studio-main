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

import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.PackageName
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.ThemeData
import com.android.tools.idea.wizard.template.ThemesData
import com.android.tools.idea.wizard.template.ViewBindingSupport
import com.android.tools.idea.wizard.template.getMaterialComponentName
import com.android.tools.idea.wizard.template.impl.activities.common.res.layout.simpleLayoutXml
import com.android.tools.idea.wizard.template.impl.activities.common.res.menu.simpleMenu
import com.android.tools.idea.wizard.template.impl.activities.common.res.values.manifestStrings
import com.android.tools.idea.wizard.template.impl.activities.common.res.values.themeStyles
import com.android.tools.idea.wizard.template.renderIf
import java.io.File

fun RecipeExecutor.generateSimpleMenu(packageName: PackageName, activityClass: String, resDir: File, menuName: String) {
  val simpleMenuStrings = """
    <resources>
        <string name="action_settings">Settings</string>
    </resources>
  """.trimIndent()

  save(simpleMenu(packageName, activityClass), resDir.resolve("menu/$menuName.xml"))
  mergeXml(simpleMenuStrings, resDir.resolve("values/strings.xml"))
}

fun RecipeExecutor.generateThemeStyles(
  themeData: ThemeData, useAndroidX: Boolean, resOut: File
) {
  addMaterialDependency(useAndroidX)
  if (!themeData.exists) {
    mergeXml(themeStyles(themeData.name, useAndroidX), resOut.resolve("values/themes.xml"))
  }
}

fun RecipeExecutor.generateManifest(
  moduleData: ModuleTemplateData,
  activityClass: String,
  packageName: String,
  isLauncher: Boolean,
  hasNoActionBar: Boolean,
  noActionBarTheme: ThemeData = moduleData.themesData.noActionBar,
  isNewModule: Boolean = moduleData.isNewModule,
  isLibrary: Boolean = moduleData.isLibrary,
  manifestOut: File = moduleData.manifestDir,
  baseFeatureResOut: File = moduleData.baseFeature?.resDir ?: moduleData.resDir,
  generateActivityTitle: Boolean,
  isResizeable: Boolean = false
) {
  generateManifestStrings(activityClass, baseFeatureResOut, isNewModule, generateActivityTitle)

  val manifest = androidManifestXml(
    isNewModule = isNewModule,
    hasNoActionBar = hasNoActionBar,
    packageName = packageName,
    activityClass = activityClass,
    isLauncher = isLauncher,
    isLibraryProject = isLibrary,
    hasNoActionBarTheme = noActionBarTheme,
    generateActivityTitle = generateActivityTitle,
    isResizeable = isResizeable
  )

  mergeXml(manifest, manifestOut.resolve("AndroidManifest.xml"))
}

fun RecipeExecutor.generateSimpleLayout(
  moduleData: ModuleTemplateData, activityClass: String, simpleLayoutName: String,
  openLayout: Boolean = true, includeCppSupport: Boolean = false
) {
  val projectData = moduleData.projectTemplateData
  val appCompatVersion = moduleData.apis.appCompatVersion
  val resOut = moduleData.resDir

  addDependency("com.android.support:appcompat-v7:$appCompatVersion.+")
  addDependency("com.android.support.constraint:constraint-layout:+")

  // TODO(qumeric): check if we sometimes need to pass appBarLayoutName
  val simpleLayout = simpleLayoutXml(
    moduleData.isNewModule, includeCppSupport, projectData.androidXSupport, moduleData.packageName, activityClass, null
  )

  val layoutFile = resOut.resolve("layout/$simpleLayoutName.xml")
  save(simpleLayout, layoutFile)

  if (openLayout) {
    open(layoutFile)
  }
}

fun RecipeExecutor.generateNoActionBarStyles(baseFeatureResOut: File?, resDir: File, themesData: ThemesData) {
  val implicitParentTheme = true // TODO(qumeric)
  val parentBlock = renderIf(!implicitParentTheme) { """parent="${themesData.main.name}"""" }

  val noActionBarBlock = renderIf(!themesData.noActionBar.exists) {
    """
    <style name="${themesData.noActionBar.name}" $parentBlock>
      <item name="windowActionBar">false</item>
      <item name="windowNoTitle">true</item>
    </style>
    """
  }

  val appBarOverlayBlock = renderIf(!themesData.appBarOverlay.exists) {
    """<style name="${themesData.appBarOverlay.name}" parent="ThemeOverlay.AppCompat.Dark.ActionBar" />"""
  }

  val popupOverlayBlock = renderIf(!themesData.popupOverlay.exists) {
    """<style name="${themesData.popupOverlay.name}" parent="ThemeOverlay.AppCompat.Light" />"""
  }

  val noActionBarStylesContent = """
    <resources>
      $noActionBarBlock
      $appBarOverlayBlock
      $popupOverlayBlock
    </resources>
  """.trimIndent()

  if (baseFeatureResOut != null) {
    mergeXml(noActionBarStylesContent, baseFeatureResOut.resolve("values/themes.xml"))
  }
  else {
    mergeXml(noActionBarStylesContent, resDir.resolve("values/themes.xml"))
  }
}

fun RecipeExecutor.generateManifestStrings(
  activityClass: String,
  baseFeatureResOut: File,
  isNewModule: Boolean,
  generateActivityTitle: Boolean
) {
  mergeXml(manifestStrings(activityClass, isNewModule, generateActivityTitle), baseFeatureResOut.resolve("values/strings.xml"))
}

fun RecipeExecutor.generateAppBar(
  moduleData: ModuleTemplateData,
  activityClass: String,
  packageName: String,
  simpleLayoutName: String,
  appBarLayoutName: String,
  appCompatVersion: Int = moduleData.apis.appCompatVersion,
  resDir: File = moduleData.resDir,
  baseFeatureResOut: File? = moduleData.baseFeature?.resDir,
  themesData: ThemesData = moduleData.themesData,
  useAndroidX: Boolean
) {
  val coordinatorLayout = getMaterialComponentName("android.support.design.widget.CoordinatorLayout", useAndroidX)
  val layoutTag = getMaterialComponentName("android.support.design.widget.AppBarLayout", useAndroidX)

  val appBarLayout = """
    <?xml version="1.0" encoding="utf-8"?>
    <$coordinatorLayout
        xmlns:android="http://schemas.android.com/apk/res/android"
        xmlns:app="http://schemas.android.com/apk/res-auto"
        xmlns:tools="http://schemas.android.com/tools"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        tools:context="$packageName.$activityClass">

        <$layoutTag
            android:layout_height="wrap_content"
            android:layout_width="match_parent"
            android:theme="@style/${themesData.appBarOverlay.name}">

            <${getMaterialComponentName("android.support.v7.widget.Toolbar", useAndroidX)}
                android:id="@+id/toolbar"
                android:layout_width="match_parent"
                android:layout_height="?attr/actionBarSize"
                android:background="?attr/colorPrimary"
                app:popupTheme="@style/${themesData.popupOverlay.name}" />

        </$layoutTag>

        <include layout="@layout/$simpleLayoutName"/>

        <${getMaterialComponentName("android.support.design.widget.FloatingActionButton", useAndroidX)}
            android:id="@+id/fab"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="bottom|end"
            android:layout_marginEnd="@dimen/fab_margin"
            android:layout_marginBottom="16dp"
            app:srcCompat="@android:drawable/ic_dialog_email" />

    </$coordinatorLayout>
  """.trimIndent()

  addDependency("com.android.support:appcompat-v7:$appCompatVersion.+")
  addDependency("com.android.support:design:$appCompatVersion.+")

  save(appBarLayout, resDir.resolve("layout/$appBarLayoutName.xml"))

  mergeXml(appBarDimens(16), resDir.resolve("values/dimens.xml"))
  mergeXml(appBarDimens(48), resDir.resolve("values-land/dimens.xml"))
  mergeXml(appBarDimens(48), resDir.resolve("values-w600dp/dimens.xml"))
  mergeXml(appBarDimens(200), resDir.resolve("values-w1240dp/dimens.xml"))

  generateNoActionBarStyles(baseFeatureResOut, resDir, themesData)
}

private fun appBarDimens(fabMargin: Int) =
"""<resources>
        <dimen name="fab_margin">${fabMargin}dp</dimen>
    </resources>
"""

fun RecipeExecutor.addLifecycleDependencies(useAndroidX: Boolean) {
  if (useAndroidX) {
    addDependency("androidx.lifecycle:lifecycle-livedata-ktx:+")
    addDependency("androidx.lifecycle:lifecycle-viewmodel-ktx:+")
  } else {
    addDependency("android.arch.lifecycle:livedata:+")
    addDependency("android.arch.lifecycle:viewmodel:+")
  }
}

fun RecipeExecutor.addViewBindingSupport(viewBindingSupport: ViewBindingSupport, value: Boolean) {
  when (viewBindingSupport) {
    ViewBindingSupport.SUPPORTED_3_6 -> {
      setViewBinding(value)
    }
    ViewBindingSupport.SUPPORTED_4_0_MORE -> {
      setBuildFeature("viewBinding", value)
    }
    else -> {}
  }
}
