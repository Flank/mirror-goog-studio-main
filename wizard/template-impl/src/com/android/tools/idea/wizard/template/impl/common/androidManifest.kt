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
package com.android.tools.idea.wizard.template.impl.common

import com.android.tools.idea.wizard.template.ThemeData
import com.android.tools.idea.wizard.template.activityToLayout
import com.android.tools.idea.wizard.template.renderIf

fun androidManifestXml(
  isNewProject: Boolean,
  hasNoActionBar: Boolean,
  packageName: String,
  activityClass: String,
  isLauncher: Boolean,
  isLibraryProject: Boolean,
  mainTheme: ThemeData,
  hasNoActionBarTheme: ThemeData,
  generateActivityTitle: Boolean = true,
  // TODO(qumeric): actually pass values to following booleans
  requireTheme: Boolean = false,
  hasApplicationTheme: Boolean = false
): String {

  val appName = if (isNewProject) "app_name" else "title_" + activityToLayout(activityClass)

  val generateActivityTitleBlock = renderIf(generateActivityTitle) { "android:label = \"@string/$appName\"" }

    val hasActionBarBlock = when {
      hasNoActionBar -> """android:theme = "@style/${hasNoActionBarTheme.name}""""
      requireTheme && !hasApplicationTheme -> """android:theme = "@style/${mainTheme.name}""""
      else -> ""
    }

    return """
    <manifest xmlns:android ="http://schemas.android.com/apk/res/android">
    <application>
    <activity android:name ="${packageName}.${activityClass}"
    $generateActivityTitleBlock
    $hasActionBarBlock>
    ${commonActivityBody(isLauncher, isLibraryProject)}
    </activity>
    </application>
    </manifest>
    """
}
