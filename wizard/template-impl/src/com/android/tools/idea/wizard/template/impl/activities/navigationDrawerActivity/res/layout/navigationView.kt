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
package com.android.tools.idea.wizard.template.impl.activities.navigationDrawerActivity.res.layout

import com.android.tools.idea.wizard.template.getMaterialComponentName

fun navigationViewXml(
  appBarLayoutName: String,
  navHeaderLayoutName: String,
  drawerMenu: String,
  useAndroidX: Boolean
) = """
<?xml version="1.0" encoding="utf-8"?>
<${getMaterialComponentName("android.support.v4.widget.DrawerLayout", useAndroidX)}
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/drawer_layout"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:fitsSystemWindows="true"
    tools:openDrawer="start">

    <include
        android:id="@+id/${appBarLayoutName}"
        layout="@layout/${appBarLayoutName}"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <${getMaterialComponentName("android.support.design.widget.NavigationView", useAndroidX)}
        android:id="@+id/nav_view"
        android:layout_width="wrap_content"
        android:layout_height="match_parent"
        android:layout_gravity="start"
        android:fitsSystemWindows="true"
        app:headerLayout="@layout/${navHeaderLayoutName}"
        app:menu="@menu/${drawerMenu}" />
</${getMaterialComponentName("android.support.v4.widget.DrawerLayout", useAndroidX)}>
"""
