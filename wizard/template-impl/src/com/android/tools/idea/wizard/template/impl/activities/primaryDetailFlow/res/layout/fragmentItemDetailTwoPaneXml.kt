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


fun fragmentItemDetailTwoPaneXml(
  detailName: String,
  detailNameLayout: String,
  packageName: String,
  useAndroidX: Boolean
) = """
<!-- Adding the same root's ID for view binding as other layout configurations -->
<${getMaterialComponentName("android.support.design.widget.CoordinatorLayout", useAndroidX)}
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/${detailNameLayout}_container"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <TextView
        android:id="@+id/${detailNameLayout}"
        style="?android:attr/textAppearanceLarge"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:padding="16dp"
        android:textIsSelectable="true"
        tools:context="${packageName}.${detailName}Fragment" />
</${getMaterialComponentName("android.support.design.widget.CoordinatorLayout", useAndroidX)}>
"""
