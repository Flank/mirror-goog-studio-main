/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Detector

class ExtraTextDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector = ExtraTextDetector()

    fun testDocumentationExample() {
        lint().files(
            manifest(
                """
                <manifest
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.android.adservices.api">

                    <uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />
                    <application>
                        android:label="Android AdServices"
                        android:forceQueryable="true"
                        android:directBootAware="true">
                    </application>
                    `
                </manifest>
                """
            ).indented(),
            xml(
                "res/drawable/icon.xml",
                """
                <shape>>
                  <item></item>>
                </shape>
                """
            ).indented(),
            xml(
                "res/values/strings.xml",
                """
                <resources>
                    <string name="test">Test</string> <!-- Text is allowed in value resource files -->
                </resources>
                """
            ).indented(),
            xml(
                "res/xml/myfile.xml",
                """
                <foo>
                    Test <!-- Text is allowed in xml and raw folder files -->
                </foo>
                """
            ).indented()
        ).run().expect(
            """
            AndroidManifest.xml:7: Error: Unexpected text found in manifest file: "android:label="Android AdServices" android:forceQueryable="true" android:directBootAware="true">" [ExtraText]
                    android:label="Android AdServices"
                    ^
            res/drawable/icon.xml:1: Warning: Unexpected text found in drawable file: ">" [ExtraText]
            <shape>>
                   ~
            1 errors, 1 warnings
            """
        )
    }

    fun testBrokenLayout() {
        val expected =
            """
            res/layout/broken.xml:5: Error: Unexpected text found in layout file: "ImageButton android:id="@+id/android_logo2" android:layout_width="wrap_content" android:layout_heigh..." [ExtraText]
                ImageButton android:id="@+id/android_logo2" android:layout_width="wrap_content" android:layout_height="wrap_content" android:src="@drawable/android_button" android:focusable="false" android:clickable="false" android:layout_weight="1.0" />
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """

        lint().files(
            xml(
                "res/layout/broken.xml",
                """
                <?xml version="1.0" encoding="utf-8"?>
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android" android:id="@+id/newlinear" android:orientation="vertical" android:layout_width="match_parent" android:layout_height="match_parent">
                    <Button android:text="Button" android:id="@+id/button1" android:layout_width="wrap_content" android:layout_height="wrap_content"></Button>
                    <ImageView android:id="@+id/android_logo" android:layout_width="wrap_content" android:layout_height="wrap_content" android:src="@drawable/android_button" android:focusable="false" android:clickable="false" android:layout_weight="1.0" />
                    ImageButton android:id="@+id/android_logo2" android:layout_width="wrap_content" android:layout_height="wrap_content" android:src="@drawable/android_button" android:focusable="false" android:clickable="false" android:layout_weight="1.0" />
                    <Button android:text="Button" android:id="@+id/button2" android:layout_width="wrap_content" android:layout_height="wrap_content"></Button>
                    <Button android:id="@+android:id/summary" android:contentDescription="@string/label" />
                </LinearLayout>
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testManifest() {
        val expected =
            """
            AndroidManifest.xml:6: Error: Unexpected text found in manifest file: "permission android:name="com.android.vending.BILLING" android:label="@string/perm_billing_label" and..." [ExtraText]
                permission android:name="com.android.vending.BILLING"
                ^
            1 errors, 0 warnings
            """

        lint().files(
            manifest(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                          xmlns:tools="http://schemas.android.com/tools">
                    <uses-feature android:name="android.software.leanback"/>

                    permission android:name="com.android.vending.BILLING"
                        android:label="@string/perm_billing_label"
                        android:description="@string/perm_billing_desc"
                        android:permissionGroup="android.permission-group.NETWORK"
                        android:protectionLevel="normal" />

                    <application android:banner="@drawable/banner">
                        <activity>
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN"/>
                                <category android:name="android.intent.category.LEANBACK_LAUNCHER"/>
                            </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testValuesOk() {
        lint().files(
            xml(
                "res/values/strings.xml",
                """
                <?xml version="1.0" encoding="utf-8"?>
                <resources>
                    <string name="foo">Foo</string>
                </resources>
                """
            ).indented()
        ).run().expectClean()
    }

    fun testAaptOkay() {
        lint().files(
            xml(
                "res/layout/layout.xml",
                """
                <merge xmlns:android="http://schemas.android.com/apk/res/android"
                       xmlns:aapt="http://schemas.android.com/aapt">
                    <include layout="@layout/message">
                        <aapt:attr name="android:theme">
                            <style>
                                <item name="android:layout_gravity">end</item>
                                <item name="bubbleBackground">@color/bubble_self</item>
                            </style>
                        </aapt:attr>
                    </include>
                    <TextView
                        android:layout_width="match_parent"
                        android:layout_height="match_parent">
                        <aapt:attr name="android:text">
                            <string>Text</string>
                        </aapt:attr>
                    </TextView>
                </merge>
                """
            ).indented()
        ).run().expectClean()
    }

    fun testItemOkay() {
        lint().files(
            xml(
                "res/drawable/drawable.xml",
                """
                <shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="oval">
                    <solid android:color="@color/tv_volume_dialog_accent" />
                    <size android:width="@dimen/tv_volume_seek_bar_thumb_diameter"
                          android:height="@dimen/tv_volume_seek_bar_thumb_diameter" />
                    <stroke android:width="@dimen/tv_volume_seek_bar_thumb_focus_ring_width"
                            android:color="@color/tv_volume_dialog_seek_thumb_focus_ring"/>
                    <item name="android:shadowColor">@color/tv_volume_dialog_seek_thumb_shadow</item>
                    <item name="android:shadowRadius">@dimen/tv_volume_seek_bar_thumb_shadow_radius</item>
                    <item name="android:shadowDy">@dimen/tv_volume_seek_bar_thumb_shadow_dy</item>
                </shape>
                """
            ).indented()
        ).run().expectClean()
    }
}
