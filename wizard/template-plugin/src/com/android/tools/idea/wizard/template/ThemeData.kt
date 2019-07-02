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
package com.android.tools.idea.wizard.template

/**
 * Information about an Android theme.
 */
data class ThemeData(
  val name: String,
  val exists: Boolean
)

/**
 * Information about project themes.
 */
data class ThemesData(
  val main: ThemeData = ThemeData("AppTheme", false),
  val noActionBar: ThemeData = ThemeData("AppTheme.NoActionBar", false),
  val appBarOverlay: ThemeData = ThemeData("AppTheme.AppBarOverlay", false),
  val popupOverlay: ThemeData = ThemeData("AppTheme.PopupOverlay", false)
)