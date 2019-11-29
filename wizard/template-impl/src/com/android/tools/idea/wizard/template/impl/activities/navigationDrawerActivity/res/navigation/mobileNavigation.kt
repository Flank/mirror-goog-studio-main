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
package com.android.tools.idea.wizard.template.impl.activities.navigationDrawerActivity.res.navigation

fun mobileNavigation(packageName: String) = """
<?xml version="1.0" encoding="utf-8"?>
<navigation xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/mobile_navigation"
    app:startDestination="@+id/nav_home">

    <fragment
        android:id="@+id/nav_home"
        android:name="${packageName}.ui.home.HomeFragment"
        android:label="@string/menu_home"
        tools:layout="@layout/fragment_home" >

        <action
            android:id="@+id/action_HomeFragment_to_HomeSecondFragment"
            app:destination="@id/nav_home_second" />
    </fragment>
    <fragment
        android:id="@+id/nav_home_second"
        android:name="${packageName}.ui.home.HomeSecondFragment"
        android:label="@string/home_second"
        tools:layout="@layout/fragment_home_second">
        <action
            android:id="@+id/action_HomeSecondFragment_to_HomeFragment"
            app:destination="@id/nav_home" />

        <argument
            android:name="myArg"
            app:argType="string" />
    </fragment>

    <fragment
        android:id="@+id/nav_gallery"
        android:name="${packageName}.ui.gallery.GalleryFragment"
        android:label="@string/menu_gallery"
        tools:layout="@layout/fragment_gallery" />

    <fragment
        android:id="@+id/nav_slideshow"
        android:name="${packageName}.ui.slideshow.SlideshowFragment"
        android:label="@string/menu_slideshow"
        tools:layout="@layout/fragment_slideshow" />
</navigation>
"""