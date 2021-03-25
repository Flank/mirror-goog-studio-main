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

import com.android.tools.idea.wizard.template.renderIf
import com.android.tools.idea.wizard.template.withoutSkipLines

fun commonActivityBody(isLauncher: Boolean, isLibraryProject: Boolean = false) =
  renderIf(isLauncher && !isLibraryProject) {
    """
    <intent-filter>
      <action android:name="android.intent.action.MAIN" />
      <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
    """
  }

fun String.collapseEmptyActivityTags():String {
    // Tags with empty body show a lint warning. We only handle <activity>
    return withoutSkipLines()
        .replace("(<activity[^>]*)>\\s*</activity>".toRegex()) { "${it.groupValues[1]} />" }
}
