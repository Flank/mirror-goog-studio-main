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
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.ThemeData
import com.android.tools.idea.wizard.template.impl.activities.common.res.menu.simpleMenu
import com.android.tools.idea.wizard.template.impl.activities.common.res.values.themeStyles
import java.io.File
import com.android.tools.idea.wizard.template.PackageName
import com.android.tools.idea.wizard.template.ThemesData
import com.android.tools.idea.wizard.template.getMaterialComponentName
import com.android.tools.idea.wizard.template.impl.activities.common.res.layout.simpleLayoutXml
import com.android.tools.idea.wizard.template.impl.activities.common.res.values.manifestStrings
import com.android.tools.idea.wizard.template.renderIf

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
  themeData: ThemeData, isDynamicFeature: Boolean, useMaterial2: Boolean, resOut: File, baseFeatureResOut: File
) {
  if (!themeData.exists) {
    if (isDynamicFeature) {
      mergeXml(themeStyles(themeData.name, useMaterial2), baseFeatureResOut.resolve("values/styles.xml"))
    }
    else {
      mergeXml(themeStyles(themeData.name, useMaterial2), resOut.resolve("values/styles.xml"))
    }
  }
}

fun RecipeExecutor.generateManifest(
  moduleData: ModuleTemplateData,
  activityClass: String,
  activityTitle: String,
  packageName: String,
  isLauncher: Boolean,
  hasNoActionBar: Boolean,
  noActionBarTheme: ThemeData = moduleData.themesData.noActionBar,
  isNewModule: Boolean = moduleData.isNewModule,
  isLibrary: Boolean = moduleData.isLibrary,
  mainTheme: ThemeData = moduleData.themesData.main,
  manifestOut: File = moduleData.manifestDir,
  resOut: File = moduleData.resDir,
  requireTheme: Boolean,
  generateActivityTitle: Boolean,
  useMaterial2: Boolean,
  isDynamicFeature: Boolean = false
) {
  if (requireTheme) {
    generateThemeStyles(
      themeData = mainTheme,
      isDynamicFeature = isDynamicFeature,
      useMaterial2 = useMaterial2,
      resOut = resOut,
      baseFeatureResOut = resOut
    )
  }

  generateManifestStrings(activityClass, activityTitle, resOut, resOut, isNewModule, generateActivityTitle, isDynamicFeature)

  val manifest = androidManifestXml(
    isNewModule, hasNoActionBar, packageName, activityClass, isLauncher, isLibrary, mainTheme, noActionBarTheme, generateActivityTitle
  )

  mergeXml(manifest, manifestOut.resolve("AndroidManifest.xml"))
}

fun RecipeExecutor.generateSimpleLayout(
  moduleData: ModuleTemplateData, activityClass: String, simpleLayoutName: String, excludeMenu: Boolean, packageName: PackageName,
  menuName: String? = null, openLayout: Boolean = true, includeCppSupport: Boolean = false
) {
  if (menuName == null) {
    require(!moduleData.isNewModule || excludeMenu)
  }
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

  if (moduleData.isNewModule && !excludeMenu) {
    generateSimpleMenu(packageName, activityClass, moduleData.resDir, menuName!!)
  }

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
    mergeXml(noActionBarStylesContent, baseFeatureResOut.resolve("values/styles.xml"))
  }
  else {
    mergeXml(noActionBarStylesContent, resDir.resolve("values/styles.xml"))
  }
}

fun RecipeExecutor.generateManifestStrings(
  activityClass: String,
  activityTitle: String,
  resOut: File,
  baseFeatureResOut: File,
  isNewModule: Boolean,
  generateActivityTitle: Boolean,
  isDynamicFeature: Boolean = false
) {
  val stringsContent = manifestStrings(activityClass, activityTitle, isNewModule, generateActivityTitle)
  if (isDynamicFeature) {
    mergeXml(stringsContent, baseFeatureResOut.resolve("values/strings.xml"))
  }
  else {
    mergeXml(manifestStrings(activityClass, activityTitle, isNewModule, generateActivityTitle), resOut.resolve("values/strings.xml"))
  }
}

fun RecipeExecutor.generateAppBar(
  moduleData: ModuleTemplateData,
  activityClass: String,
  packageName: String,
  simpleLayoutName: String,
  appBarLayoutName: String,
  buildApi: Int = moduleData.apis.appCompatVersion,
  resDir: File = moduleData.resDir,
  baseFeatureResOut: File? = moduleData.baseFeature?.resDir,
  themesData: ThemesData = moduleData.themesData,
  useAndroidX: Boolean,
  useMaterial2: Boolean
) {
  val appBarDimens = """
    <resources>
        <dimen name="fab_margin">16dp</dimen>
    </resources>
  """.trimIndent()

  val coordinatorLayout = getMaterialComponentName("android.support.design.widget.CoordinatorLayout", useAndroidX)
  val layoutTag = getMaterialComponentName("android.support.design.widget.AppBarLayout", useMaterial2)

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

        <${getMaterialComponentName("android.support.design.widget.FloatingActionButton", useMaterial2)}
            android:id="@+id/fab"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="bottom|end"
            android:layout_margin="@dimen/fab_margin"
            app:srcCompat="@android:drawable/ic_dialog_email" />

    </$coordinatorLayout>
  """.trimIndent()

  addDependency("com.android.support:appcompat-v7:$buildApi.+")
  addDependency("com.android.support:design:$buildApi.+")

  save(appBarLayout, resDir.resolve("layout/$appBarLayoutName.xml"))

  mergeXml(appBarDimens, resDir.resolve("values/dimens.xml"))

  generateNoActionBarStyles(baseFeatureResOut, resDir, themesData)
}

fun RecipeExecutor.addLifecycleDependencies(useAndroidX: Boolean) {
  if (useAndroidX) {
    addDependency("androidx.lifecycle:lifecycle-livedata-ktx:+")
    addDependency("androidx.lifecycle:lifecycle-viewmodel-ktx:+")
  } else {
    addDependency("android.arch.lifecycle:livedata:+")
    addDependency("android.arch.lifecycle:viewmodel:+")
  }
}