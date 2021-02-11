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

fun fragmentItemListTwoPaneXml(
  collectionName: String,
  itemListLayout: String,
  detailName: String,
  detailNameLayout: String,
  itemListContentLayout: String,
  childNavGraphFileId: String,
  packageName: String,
  useAndroidX: Boolean
) = """
  <${getMaterialComponentName("android.support.constraint.ConstraintLayout", useAndroidX)}
    android:id="@+id/${itemListLayout}_container"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools" >

    <${getMaterialComponentName("android.support.v7.widget.RecyclerView", useAndroidX)}
        android:id="@+id/${itemListLayout}"
        android:name="${packageName}.${collectionName}Fragment"
        android:layout_width="0dp"
        android:layout_height="match_parent"
        android:layout_marginStart="@dimen/container_margin"
        android:layout_marginEnd="@dimen/container_margin"
        app:layoutManager="LinearLayoutManager"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toStartOf="@id/guideline"
        app:layout_constraintTop_toTopOf="parent"
        tools:context="${packageName}.${detailName}HostActivity"
        tools:listitem="@layout/${itemListContentLayout}" />

    <${getMaterialComponentName("android.support.constraint.Guideline", useAndroidX)}
        android:id="@+id/guideline"
        android:layout_width="0dp"
        android:layout_height="match_parent"
        app:layout_constraintGuide_begin="@dimen/item_width"
        android:orientation="vertical"/>

    <fragment
        android:id="@+id/${detailNameLayout}_nav_container"
        android:name="androidx.navigation.fragment.NavHostFragment"
        android:layout_width="0dp"
        android:layout_height="match_parent"
        android:layout_marginStart="@dimen/container_margin"
        app:defaultNavHost="false"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toEndOf="@id/guideline"
        app:layout_constraintTop_toTopOf="parent"
        app:navGraph="@navigation/${childNavGraphFileId}" />

</${getMaterialComponentName("android.support.constraint.ConstraintLayout", useAndroidX)}>
"""
