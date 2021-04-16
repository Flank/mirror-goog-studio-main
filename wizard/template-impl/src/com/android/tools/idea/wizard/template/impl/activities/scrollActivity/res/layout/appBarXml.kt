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

package com.android.tools.idea.wizard.template.impl.activities.scrollActivity.res.layout

import com.android.tools.idea.wizard.template.getMaterialComponentName
import com.android.tools.idea.wizard.template.renderIf

fun appBarXml(
  activityClass: String,
  packageName: String,
  simpleLayoutName: String,
  themeNameAppBarOverlay: String,
  themeNamePopupOverlay: String,
  useAndroidX: Boolean) =
  """<?xml version="1.0" encoding="utf-8"?>
<${getMaterialComponentName("android.support.design.widget.CoordinatorLayout", useAndroidX)}
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:fitsSystemWindows="true"
    tools:context="${packageName}.${activityClass}">

    <${getMaterialComponentName("android.support.design.widget.AppBarLayout", useAndroidX)}
        android:id="@+id/app_bar"
        android:fitsSystemWindows="true"
        android:layout_height="@dimen/app_bar_height"
        android:layout_width="match_parent"
        android:theme="@style/${themeNameAppBarOverlay}">

        <${getMaterialComponentName("android.support.design.widget.CollapsingToolbarLayout", useAndroidX)}
            android:id="@+id/toolbar_layout"
            ${renderIf(useAndroidX) {"""style="@style/Widget.MaterialComponents.Toolbar.Primary""""}}
            android:fitsSystemWindows="true"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            app:toolbarId="@+id/toolbar"
            app:layout_scrollFlags="scroll|exitUntilCollapsed"
            app:contentScrim="?attr/colorPrimary">

            <${getMaterialComponentName("android.support.v7.widget.Toolbar", useAndroidX)}
                android:id="@+id/toolbar"
                android:layout_height="?attr/actionBarSize"
                android:layout_width="match_parent"
                app:layout_collapseMode="pin"
                app:popupTheme="@style/${themeNamePopupOverlay}" />

        </${getMaterialComponentName("android.support.design.widget.CollapsingToolbarLayout", useAndroidX)}>
    </${getMaterialComponentName("android.support.design.widget.AppBarLayout", useAndroidX)}>

    <include layout="@layout/${simpleLayoutName}" />

    <${getMaterialComponentName("android.support.design.widget.FloatingActionButton", useAndroidX)}
        android:id="@+id/fab"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginEnd="@dimen/fab_margin"
        app:layout_anchor="@id/app_bar"
        app:layout_anchorGravity="bottom|end"
        app:srcCompat="@android:drawable/ic_dialog_email" />

</${getMaterialComponentName("android.support.design.widget.CoordinatorLayout", useAndroidX)}>
"""
