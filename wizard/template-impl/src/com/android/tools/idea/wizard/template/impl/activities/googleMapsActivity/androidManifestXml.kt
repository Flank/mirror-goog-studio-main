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

package com.android.tools.idea.wizard.template.impl.activities.googleMapsActivity

import com.android.tools.idea.wizard.template.impl.activities.common.commonActivityBody
import com.android.tools.idea.wizard.template.impl.activities.common.collapseEmptyActivityTags

fun geoApiKeyMetadataEntry() = """
        <!--
             TODO: Before you run your application, you need a Google Maps API key.

             To get one, follow the directions here:

                https://developers.google.com/maps/documentation/android-sdk/get-api-key

             Once you have your API key (it starts with "AIza"), define a new property in your
             project's local.properties file (e.g. MAPS_API_KEY=Aiza...), and replace the
             "YOUR_API_KEY" string in this file with "${'$'}{MAPS_API_KEY}".
         -->
        <meta-data android:name="com.google.android.geo.API_KEY" android:value="YOUR_API_KEY"/>
"""

fun androidManifestXml(
  activityClass: String,
  isLauncher: Boolean,
  isLibrary: Boolean,
  packageName: String,
  simpleName: String,
  isNewModule: Boolean
): String {
  // TODO: add activityLabel like in other activity templates
  val launcher = isLauncher || isNewModule
  val activityBody = commonActivityBody(launcher, isLibrary)

  return """
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>

        ${geoApiKeyMetadataEntry()}

        <activity android:name="${packageName}.${activityClass}"
            android:exported="$launcher"
            android:label="@string/title_${simpleName}">
            $activityBody
        </activity>
    </application>

</manifest>
""".collapseEmptyActivityTags()
}
