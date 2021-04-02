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
package com.android.tools.idea.wizard.template.impl.activities.responsiveActivity.res.layout

import com.android.tools.idea.wizard.template.ThemesData

fun appBarMainXml(
  activityClass: String,
  contentMainLayoutName: String,
  packageName: String,
  themesData: ThemesData
) = """
<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/coordinator_layout"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    tools:context="$packageName.$activityClass">

    <com.google.android.material.appbar.AppBarLayout
        android:layout_height="wrap_content"
        android:layout_width="match_parent"
        android:theme="@style/${themesData.appBarOverlay.name}">

        <androidx.appcompat.widget.Toolbar
            android:id="@+id/toolbar"
            android:layout_width="match_parent"
            android:layout_height="?attr/actionBarSize"
            android:background="?attr/colorPrimary"
            app:popupTheme="@style/${themesData.popupOverlay.name}" />

    </com.google.android.material.appbar.AppBarLayout>

    <include
        android:id="@+id/$contentMainLayoutName"
        layout="@layout/$contentMainLayoutName"/>

    <!-- This CoordinatorLayout is to dodge the BottomNavigationView when the Snackbar is shown -->
    <androidx.coordinatorlayout.widget.CoordinatorLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:layout_marginBottom="56dp"
        android:background="@android:color/transparent">

        <com.google.android.material.floatingactionbutton.FloatingActionButton
            android:id="@+id/fab"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="bottom|end"
            android:layout_margin="@dimen/fab_margin"
            app:srcCompat="@android:drawable/ic_dialog_email" />
    </androidx.coordinatorlayout.widget.CoordinatorLayout>
</androidx.coordinatorlayout.widget.CoordinatorLayout>
"""
