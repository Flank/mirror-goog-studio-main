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
package com.android.tools.idea.wizard.template.impl.activities.common

import com.android.tools.idea.wizard.template.activityToLayout
import com.android.tools.idea.wizard.template.renderIf

fun androidManifestXml(
  isNewModule: Boolean,
  hasNoActionBar: Boolean,
  packageName: String,
  activityClass: String,
  isLauncher: Boolean,
  isLibraryProject: Boolean,
  activityThemeName: String,
  generateActivityTitle: Boolean = true,
  isResizeable: Boolean = false
): String {
  val appName = if (isNewModule) "app_name" else "title_" + activityToLayout(activityClass)

  val generateActivityTitleBlock = renderIf(generateActivityTitle) { "android:label = \"@string/$appName\"" }

  val themeBlock = when {
    activityThemeName.startsWith("@android:style/") -> """android:theme = "$activityThemeName""""
    hasNoActionBar -> """android:theme = "@style/${activityThemeName}""""
    else -> ""
  }

  val isResizeableBlock = renderIf(isResizeable) {
    """android:resizeableActivity="true"
     tools:targetApi="24"
    """
  }

  val toolsNameSpace = renderIf(isResizeable) {"xmlns:tools=\"http://schemas.android.com/tools\""}

  val launcher = isLauncher || isNewModule
  return """
    <manifest xmlns:android ="http://schemas.android.com/apk/res/android"
    $toolsNameSpace>
    <application>
    <activity android:name ="${packageName}.${activityClass}"
    android:exported="$launcher"
    $generateActivityTitleBlock
    $themeBlock
    $isResizeableBlock>
    ${commonActivityBody(launcher, isLibraryProject)}
    </activity>
    </application>
    </manifest>
    """.collapseEmptyActivityTags()
}
