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

fun fragmentItemListXml(
  collectionName: String,
  detailName: String,
  itemListLayout: String,
  itemListContentLayout: String,
  packageName: String,
  useAndroidX: Boolean
) = """
<?xml version="1.0" encoding="utf-8"?>
<!-- Adding the same root's ID for view binding as other layout configurations -->
<${getMaterialComponentName("android.support.constraint.ConstraintLayout", useAndroidX)}
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/${itemListLayout}_container"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:layout_marginStart="@dimen/container_horizontal_margin"
    android:layout_marginEnd="@dimen/container_horizontal_margin">

    <${getMaterialComponentName("android.support.v7.widget.RecyclerView", useAndroidX)}
        android:id="@+id/${itemListLayout}"
        android:name="${packageName}.${collectionName}Fragment"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:layout_marginLeft="16dp"
        android:layout_marginRight="16dp"
        app:layoutManager="LinearLayoutManager"
        tools:context="${packageName}.${detailName}HostActivity"
        tools:listitem="@layout/${itemListContentLayout}" />
</${getMaterialComponentName("android.support.constraint.ConstraintLayout", useAndroidX)}>
"""
