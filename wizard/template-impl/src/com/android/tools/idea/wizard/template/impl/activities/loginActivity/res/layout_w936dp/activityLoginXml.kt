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

package com.android.tools.idea.wizard.template.impl.activities.loginActivity.res.layout_w936dp

import com.android.tools.idea.wizard.template.getMaterialComponentName
import com.android.tools.idea.wizard.template.impl.activities.loginActivity.res.layout.activityLoginXmlContent

fun activityLoginXml(
  activityClass: String,
  packageName: String,
  useAndroidX: Boolean,
  minApiLevel: Int): String {

  return """<?xml version="1.0" encoding="utf-8"?>
<${getMaterialComponentName("android.support.constraint.ConstraintLayout", useAndroidX)}
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/container"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:paddingBottom="@dimen/activity_vertical_margin"
    android:paddingLeft="@dimen/activity_horizontal_margin"
    android:paddingRight="@dimen/activity_horizontal_margin"
    android:paddingTop="@dimen/activity_vertical_margin"
    tools:context="${packageName}.ui.login.${activityClass}">

    <${getMaterialComponentName("android.support.constraint.ConstraintLayout", useAndroidX)}
        android:layout_width="840dp"
        android:layout_height="match_parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent">

        ${activityLoginXmlContent(minApiLevel)}
    </${getMaterialComponentName("android.support.constraint.ConstraintLayout", useAndroidX)}>
</${getMaterialComponentName("android.support.constraint.ConstraintLayout", useAndroidX)}>
"""
}
