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

package com.android.tools.idea.wizard.template.impl.activities.googlePayActivity

import com.android.tools.idea.wizard.template.ThemesData
import com.android.tools.idea.wizard.template.impl.activities.common.commonActivityBody
import com.android.tools.idea.wizard.template.impl.activities.common.collapseEmptyActivityTags

fun androidManifestXml(
  activityClass: String,
  isLauncher: Boolean,
  isLibrary: Boolean,
  activityPackage: String,
  simpleName: String,
  isNewModule: Boolean,
  themesData: ThemesData
): String {
  val activityBody = commonActivityBody(isLauncher || isNewModule, isLibrary)
  return """
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <activity android:name="$activityPackage.$activityClass"
            android:label="@string/title_$simpleName"
            android:theme="@style/${themesData.main.name}">
            $activityBody
        </activity>

         <!-- This element is required to enable Google Pay in your app. -->
        <meta-data
            android:name="com.google.android.gms.wallet.api.enabled"
            android:value="true" />

    </application>
</manifest>
""".collapseEmptyActivityTags()
}
