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
package com.android.tools.idea.wizard.template.impl.activities.common.res.layout

import com.android.tools.idea.wizard.template.getMaterialComponentName
import com.android.tools.idea.wizard.template.renderIf

fun simpleLayoutXml(
  isNewModule: Boolean,
  includeCppSupport: Boolean,
  useAndroidX: Boolean,
  packageName: String,
  activityClass: String,
  appBarLayoutName: String?
): String {
  val layout = getMaterialComponentName("android.support.constraint.ConstraintLayout", useAndroidX)
  val appBarLayoutNameBlock = renderIf(appBarLayoutName != null) {
    """
    app:layout_behavior="@string/appbar_scrolling_view_behavior"
    tools:showIn="@layout/${appBarLayoutName}"
    """
  }

  val includeCppSupportBlock = renderIf(includeCppSupport) { """android:id="@+id/sample_text"""" }

  val isNewBlock = renderIf(isNewModule) {
    """<TextView
      $includeCppSupportBlock
      android:layout_width="wrap_content"
      android:layout_height="wrap_content"
      android:text="Hello World!"
      app:layout_constraintBottom_toBottomOf="parent"
      app:layout_constraintLeft_toLeftOf="parent"
      app:layout_constraintRight_toRightOf="parent"
      app:layout_constraintTop_toTopOf="parent" />
    """
  }


  return """
  <?xml version="1.0" encoding="utf-8"?>
  <$layout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    $appBarLayoutNameBlock
    tools:context="$packageName.$activityClass">
    $isNewBlock

  </$layout>
  """
}