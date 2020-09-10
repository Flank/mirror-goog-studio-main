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

package com.android.tools.idea.wizard.template.impl.activities.googleAdMobAdsActivity.res.layout

import com.android.tools.idea.wizard.template.impl.fragments.googleAdMobAdsFragment.AdFormat
import com.android.tools.idea.wizard.template.renderIf

fun activitySimpleXml(
  activityClass: String,
  adFormat: AdFormat,
  packageName: String
): String {
  val formatSpecificLayout = when (adFormat) {
    AdFormat.Banner -> """
    <TextView
        android:text="@string/hello_world"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content" />

    <!-- view for AdMob Banner Ad -->
    <com.google.android.gms.ads.AdView
        android:id="@+id/ad_view"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_centerHorizontal="true"
        android:layout_alignParentBottom="true"
        ads:adSize="BANNER"
        ads:adUnitId="@string/banner_ad_unit_id" />
    """
  AdFormat.Interstitial -> """
    <!-- view for AdMob Interstitial Ad -->
    <TextView
        android:id="@+id/app_title"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_centerHorizontal="true"
        android:layout_marginTop="50dp"
        android:text="@string/interstitial_ad_sample"
        android:textAppearance="?android:attr/textAppearanceLarge" />

    <TextView
        android:id="@+id/level"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_below="@+id/app_title"
        android:layout_centerHorizontal="true"
        android:text="@string/start_level"
        android:textAppearance="?android:attr/textAppearanceLarge" />

    <Button
        android:id="@+id/next_level_button"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_centerHorizontal="true"
        android:layout_centerVertical="true"
        android:text="@string/next_level" />
  """}

  return """
<RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
    ${renderIf(adFormat == AdFormat.Banner) { "xmlns:ads=\"http://schemas.android.com/apk/res-auto\"" }}
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    tools:context="${packageName}.${activityClass}">

$formatSpecificLayout
</RelativeLayout>
"""
}
