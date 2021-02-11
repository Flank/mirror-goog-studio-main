/*
 * Copyright (C) 2020 The Android Open Source Project
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

fun activityMainXml(
  navHostFragmentId: String,
  detailNameFragmentLayout: String,
  mainNavigationGraphId: String,
  useAndroidX: Boolean
) = """
<?xml version="1.0" encoding="utf-8"?>
<${getMaterialComponentName("android.support.constraint.ConstraintLayout", useAndroidX)}
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/container"
    android:layout_width="match_parent"
    android:layout_height="match_parent" >

    <androidx.fragment.app.FragmentContainerView
        android:id="@+id/${navHostFragmentId}"
        android:name="androidx.navigation.fragment.NavHostFragment"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:defaultNavHost="true"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintLeft_toLeftOf="parent"
        app:layout_constraintRight_toRightOf="parent"
        app:layout_constraintTop_toTopOf="parent"
        app:navGraph="@navigation/${mainNavigationGraphId}"
        tools:layout="@layout/${detailNameFragmentLayout}" />

</${getMaterialComponentName("android.support.constraint.ConstraintLayout", useAndroidX)}>"""
