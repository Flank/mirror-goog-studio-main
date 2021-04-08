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

package com.android.tools.idea.wizard.template.impl.activities.tabbedActivity.res.layout

import com.android.tools.idea.wizard.template.getMaterialComponentName

fun appBarActivityXml(
  activityClass: String,
  packageName: String,
  themeNameAppBarOverlay: String,
  useAndroidX: Boolean) =

  """<?xml version="1.0" encoding="utf-8"?>
<${getMaterialComponentName("android.support.design.widget.CoordinatorLayout", useAndroidX)}
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    tools:context="${packageName}.${activityClass}">

    <${getMaterialComponentName("android.support.design.widget.AppBarLayout", useAndroidX)}
        android:layout_height="wrap_content"
        android:layout_width="match_parent"
        android:theme="@style/${themeNameAppBarOverlay}">

        <TextView
            android:id="@+id/title"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:gravity="center"
            android:minHeight="?actionBarSize"
            android:padding="@dimen/appbar_padding"
            android:text="@string/app_name"
            android:textAppearance="@style/TextAppearance.Widget.AppCompat.Toolbar.Title"/>

        <${getMaterialComponentName("android.support.design.widget.TabLayout", useAndroidX)}
            android:id="@+id/tabs"
            android:layout_width="match_parent"
            android:layout_height="wrap_content" />
    </${getMaterialComponentName("android.support.design.widget.AppBarLayout", useAndroidX)}>

    <${getMaterialComponentName("android.support.v4.view.ViewPager", useAndroidX)}
        android:id="@+id/view_pager"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:layout_behavior="@string/appbar_scrolling_view_behavior"/>

    <${getMaterialComponentName("android.support.design.widget.FloatingActionButton", useAndroidX)}
        android:id="@+id/fab"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom|end"
        android:layout_marginEnd="@dimen/fab_margin"
        android:layout_marginBottom="16dp"
        app:srcCompat="@android:drawable/ic_dialog_email" />
</${getMaterialComponentName("android.support.design.widget.CoordinatorLayout", useAndroidX)}>"""
