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
package com.android.tools.idea.wizard.template.impl.activities.navigationDrawerActivity.res.values21

import com.android.tools.idea.wizard.template.ThemeData
import com.android.tools.idea.wizard.template.renderIf

fun noActionBarStylesV21(
  themeNoActionBar: ThemeData,
  themeName: String
): String {
  val implicitParentTheme = themeNoActionBar.name.startsWith("$themeName.")
  val parentBlock = renderIf(!implicitParentTheme) { " parent=$themeName" }
  val styleBlock = renderIf(!themeNoActionBar.exists) {
    """
        <style name="${themeNoActionBar.name}"$parentBlock>
            <item name="windowActionBar">false</item>
            <item name="windowNoTitle">true</item>
            <item name="android:statusBarColor">@android:color/transparent</item>
        </style>
  """
  }
  return """
<resources>
$styleBlock
</resources>
"""
}
