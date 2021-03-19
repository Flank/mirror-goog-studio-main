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
package com.android.tools.idea.wizard.template.impl.activities.responsiveActivity.res.menu

fun bottomNavigationMenu() = """
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    tools:showIn="navigation_view">

    <group android:checkableBehavior="single">
        <item
            android:id="@+id/nav_transform"
            android:icon="@drawable/ic_camera_black_24dp"
            android:title="@string/menu_transform" />
        <item
            android:id="@+id/nav_reflow"
            android:icon="@drawable/ic_gallery_black_24dp"
            android:title="@string/menu_reflow" />
        <item
            android:id="@+id/nav_slideshow"
            android:icon="@drawable/ic_slideshow_black_24dp"
            android:title="@string/menu_slideshow" />
    </group>
</menu>
"""
