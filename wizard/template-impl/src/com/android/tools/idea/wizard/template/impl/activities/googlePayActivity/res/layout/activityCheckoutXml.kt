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

package com.android.tools.idea.wizard.template.impl.activities.googlePayActivity.res.layout

fun activityCheckoutXml(
  activityClass: String,
  activityPackage: String
): String {

  return """
<?xml version="1.0" encoding="utf-8"?>
<ScrollView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:fillViewport="true"
    tools:context="$activityPackage.$activityClass">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:layout_marginStart="20dp"
        android:layout_marginEnd="20dp">

        <ImageView
            android:id="@+id/detailImage"
            android:layout_width="match_parent"
            android:layout_height="350dp"
            android:layout_marginTop="20dp"
            android:src="@drawable/polo_shirt_image" />

        <TextView
            android:id="@+id/detailTitle"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="15dp"
            android:text="Sample Item Title"
            android:textColor="?android:textColorPrimary"
            android:textSize="20sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/detailPrice"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="5dp"
            android:textColor="?android:textColorSecondary"
            android:text="${'$'} Sample price"/>

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="15dp"
            android:text="@string/checkout_item_description"
            android:textColor="?android:textColorPrimary"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/detailDescription"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="5dp"
            android:layout_marginBottom="15dp"
            android:textColor="?android:textColorSecondary"
            android:text="Sample description text..."/>

        <!--
            TODO Check out Google Pay's brand guidelines to discover all button types and styles:
            https://developers.google.com/pay/api/android/guides/brand-guidelines#assets
        -->
        <include
            android:id="@+id/googlePayButton"
            layout="@layout/buy_with_googlepay_button"
            android:layout_width="match_parent"
            android:layout_height="@dimen/buy_button_height"
            android:layout_marginBottom="20dp"
            android:visibility="gone"/>

    </LinearLayout>
</ScrollView>
"""
}