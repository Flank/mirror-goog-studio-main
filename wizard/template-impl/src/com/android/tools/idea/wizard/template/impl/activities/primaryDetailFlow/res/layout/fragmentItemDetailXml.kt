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

package com.android.tools.idea.wizard.template.impl.activities.primaryDetailFlow.res.layout

import com.android.tools.idea.wizard.template.getMaterialComponentName

fun fragmentItemDetailXml(
  detailName: String,
  detailNameLayout: String,
  packageName: String,
  useAndroidX: Boolean
) = """
<!-- Adding the same root's ID for view binding as other layout configurations -->
<${getMaterialComponentName("android.support.design.widget.CoordinatorLayout",
                            useAndroidX)} xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/${detailNameLayout}_container"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    tools:context="${packageName}.${detailName}HostActivity"
    tools:ignore="MergeRootFrame">

    <${getMaterialComponentName("android.support.design.widget.AppBarLayout", useAndroidX)}
        android:id="@+id/app_bar"
        android:layout_width="match_parent"
        android:layout_height="@dimen/app_bar_height"
        android:theme="@style/ThemeOverlay.AppCompat.Dark.ActionBar">

        <${getMaterialComponentName("android.support.design.widget.CollapsingToolbarLayout", useAndroidX)}
            android:id="@+id/toolbar_layout"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            app:contentScrim="?attr/colorPrimary"
            app:layout_scrollFlags="scroll|exitUntilCollapsed"
            app:toolbarId="@+id/toolbar">

            <${getMaterialComponentName("android.support.v7.widget.Toolbar", useAndroidX)}
                android:id="@+id/detail_toolbar"
                android:layout_width="match_parent"
                android:layout_height="?attr/actionBarSize"
                app:layout_collapseMode="pin"
                app:popupTheme="@style/ThemeOverlay.AppCompat.Light" />

        </${getMaterialComponentName("android.support.design.widget.CollapsingToolbarLayout", useAndroidX)}>

    </${getMaterialComponentName("android.support.design.widget.AppBarLayout", useAndroidX)}>

    <${getMaterialComponentName("android.support.v4.widget.NestedScrollView", useAndroidX)}
        android:id="@+id/${detailNameLayout}_scroll_view"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:layout_behavior="@string/appbar_scrolling_view_behavior">

        <TextView
            android:id="@+id/${detailNameLayout}"
            style="?android:attr/textAppearanceLarge"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:paddingTop="16dp"
            android:paddingBottom="16dp"
            android:paddingStart="@dimen/container_horizontal_margin"
            android:paddingEnd="@dimen/container_horizontal_margin"
            android:textIsSelectable="true"
            tools:context="${packageName}.${detailName}Fragment" />

    </${getMaterialComponentName("android.support.v4.widget.NestedScrollView", useAndroidX)}>

    <${getMaterialComponentName("android.support.design.widget.FloatingActionButton", useAndroidX)}
        android:id="@+id/fab"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center_vertical|start"
        android:layout_marginBottom="16dp"
        android:layout_marginEnd="@dimen/fab_margin"
        app:srcCompat="@android:drawable/stat_notify_chat"
        app:layout_anchor="@+id/${detailNameLayout}_scroll_view"
        app:layout_anchorGravity="top|end" />

</${getMaterialComponentName("android.support.design.widget.CoordinatorLayout", useAndroidX)}>
"""
