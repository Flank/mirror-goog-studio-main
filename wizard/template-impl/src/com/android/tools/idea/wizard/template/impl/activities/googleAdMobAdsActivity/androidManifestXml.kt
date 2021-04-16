/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.idea.wizard.template.impl.activities.googleAdMobAdsActivity

import com.android.tools.idea.wizard.template.activityToLayout
import com.android.tools.idea.wizard.template.impl.activities.common.commonActivityBody
import com.android.tools.idea.wizard.template.impl.activities.common.collapseEmptyActivityTags

fun androidManifestXml(
  activityClass: String,
  isLauncher: Boolean,
  isLibrary: Boolean,
  isNewModule: Boolean,
  packageName: String
): String {
 val labelBlock = if (isNewModule) "android:label=\"@string/app_name\""
 else "android:label=\"@string/title_${activityToLayout(activityClass)}\""
 val activityBody = commonActivityBody(isLauncher || isNewModule, isLibrary)

 return """
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Include required permissions for Google Mobile Ads to run. -->
    <uses-permission android:name="android.permission.INTERNET"/>
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>

    <application>
        <!-- You can find your app ID in the AdMob UI. For android:value,
            insert your own AdMob app ID in quotes, as shown below.
            Sample AdMob App ID: ca-app-pub-3940256099942544~3347511713 -->
        <meta-data
            android:name="com.google.android.gms.ads.APPLICATION_ID"
            android:value="ca-app-pub-3940256099942544~3347511713"/>

        <activity android:name="${packageName}.${activityClass}"
            android:exported="true"
            $labelBlock>
            $activityBody
        </activity>
        <!--Include the AdActivity configChanges and theme. -->
        <activity android:name="com.google.android.gms.ads.AdActivity"
            android:exported="false"
            android:configChanges="keyboard|keyboardHidden|orientation|screenLayout|uiMode|screenSize|smallestScreenSize"
            android:theme="@android:style/Theme.Translucent" />
    </application>

</manifest>
""".collapseEmptyActivityTags()
}
