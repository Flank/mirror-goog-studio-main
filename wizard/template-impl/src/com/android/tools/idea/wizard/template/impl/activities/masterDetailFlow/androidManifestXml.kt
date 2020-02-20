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

package com.android.tools.idea.wizard.template.impl.activities.masterDetailFlow

import com.android.tools.idea.wizard.template.impl.activities.common.commonActivityBody

fun androidManifestXml(
  collectionName: String,
  detailName: String,
  collection_name: String,
  detailNameLayout: String,
  isLauncher: Boolean,
  isLibrary: Boolean,
  isNewModule: Boolean,
  packageName: String,
  themeNameNoActionBar: String
): String {
  val labelBlock = if (isNewModule) {"android:label=\"@string/app_name\""} else {"android:label=\"@string/title_${collection_name}\""}
  val activityBody = commonActivityBody(isLauncher || isNewModule, isLibrary)
  return """
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application>
        <activity
            android:name="${packageName}.${collectionName}Activity"
            $labelBlock
            android:theme="@style/${themeNameNoActionBar}">
            $activityBody
        </activity>

        <activity android:name="${packageName}.${detailName}Activity"
            android:label="@string/title_${detailNameLayout}"
            android:theme="@style/${themeNameNoActionBar}"
            android:parentActivityName="${packageName}.${collectionName}Activity">
            <meta-data android:name="android.support.PARENT_ACTIVITY"
                android:value="${packageName}.${collectionName}Activity" />
        </activity>
    </application>

</manifest>
"""
}
