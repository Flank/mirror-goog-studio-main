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

package com.android.tools.idea.wizard.template.impl.activities.googleAdMobAdsActivity.res.values

import com.android.tools.idea.wizard.template.impl.fragments.googleAdMobAdsFragment.AdFormat

fun stringsXml(
  adFormat: AdFormat
): String {
 val formatSpecificBlock = when (adFormat) {
  AdFormat.Banner -> """
    <string name="hello_world">Hello world!</string>
    <!-- -
        This is an ad unit ID for a banner test ad. Replace with your own banner ad unit id.
        For more information, see https://support.google.com/admob/answer/3052638
    <!- -->
    <string name="banner_ad_unit_id">ca-app-pub-3940256099942544/6300978111</string>
  """
  AdFormat.Interstitial -> """
    <string name="interstitial_ad_sample">Interstitial Ad Sample</string>
    <string name="start_level">Level 1</string>
    <string name="next_level">Next Level</string>
    <!-- -
        This is an ad unit ID for an interstitial test ad. Replace with your own interstitial ad unit id.
        For more information, see https://support.google.com/admob/answer/3052638
    <!- -->
    <string name="interstitial_ad_unit_id">ca-app-pub-3940256099942544/1033173712</string>
  """
 }
 return """
<resources>
    <string name="action_settings">Settings</string>

$formatSpecificBlock

</resources>
"""
}
