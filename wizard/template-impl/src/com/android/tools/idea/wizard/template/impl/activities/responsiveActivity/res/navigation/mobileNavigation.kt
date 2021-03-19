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
package com.android.tools.idea.wizard.template.impl.activities.responsiveActivity.res.navigation

fun mobileNavigation(
  navGraphName: String,
  packageName: String
) = """
<?xml version="1.0" encoding="utf-8"?>
<navigation xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/${navGraphName}"
    app:startDestination="@+id/nav_transform">

    <fragment
        android:id="@+id/nav_transform"
        android:name="$packageName.ui.transform.TransformFragment"
        android:label="@string/menu_transform"
        tools:layout="@layout/fragment_transform" />

    <fragment
        android:id="@+id/nav_reflow"
        android:name="$packageName.ui.reflow.ReflowFragment"
        android:label="@string/menu_reflow"
        tools:layout="@layout/fragment_reflow" />

    <fragment
        android:id="@+id/nav_slideshow"
        android:name="$packageName.ui.slideshow.SlideshowFragment"
        android:label="@string/menu_slideshow"
        tools:layout="@layout/fragment_slideshow" />

    <fragment
        android:id="@+id/nav_settings"
        android:name="$packageName.ui.settings.SettingsFragment"
        android:label="@string/menu_settings"
        tools:layout="@layout/fragment_settings" />
</navigation>
"""
