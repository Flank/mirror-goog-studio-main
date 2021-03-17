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

package com.android.tools.idea.wizard.template.impl.activities.fullscreenActivity

import com.android.tools.idea.wizard.template.impl.activities.common.commonActivityBody
import com.android.tools.idea.wizard.template.impl.activities.common.collapseEmptyActivityTags
import com.android.tools.idea.wizard.template.impl.activities.fullscreenActivity.res.values.getFullscreenTheme

fun androidManifestXml(
  activityClass: String,
  packageName: String,
  simpleName: String,
  isLauncher: Boolean,
  isLibrary: Boolean,
  isNewModule: Boolean,
  themeName: String
): String {
  val activityLabel = if (isNewModule) """android:label="@string/app_name"""" else """android:label="@string/title_${simpleName}""""
  val activityBody = commonActivityBody(isLauncher || isNewModule, isLibrary)

  return """<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application>
        <activity android:name="${packageName}.${activityClass}"
            android:exported="true"
            android:configChanges="orientation|keyboardHidden|screenSize"
            $activityLabel
            android:theme="@style/${getFullscreenTheme(themeName)}">
            $activityBody
        </activity>
    </application>

</manifest>
""".collapseEmptyActivityTags()
}
