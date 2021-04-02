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

fun activityMainXml(
  appBarMainName: String
) = """
<?xml version="1.0" encoding="utf-8"?>
<!--
Wrap the DrawerLayout with FrameLayout to use the same View type for the same view ID
across the layout configurations
-->
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/activity_container"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <androidx.drawerlayout.widget.DrawerLayout
        android:id="@+id/drawer_layout"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:fitsSystemWindows="true"
        tools:openDrawer="start">

        <include
            android:id="@+id/${appBarMainName}"
            layout="@layout/${appBarMainName}"
            android:layout_width="match_parent"
            android:layout_height="match_parent" />

    </androidx.drawerlayout.widget.DrawerLayout>
</FrameLayout>
"""
