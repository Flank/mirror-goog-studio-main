/*
 * Copyright (C) 2014 The Android Open Source Project
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

class WebViewDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return WebViewDetector()
    }

    fun testMatchParentWidth() {
        lint().files(
            xml(
                "res/layout/webview.xml",
                """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    android:orientation="vertical"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent">

                    <!-- OK -->
                    <WebView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content" />

                    <LinearLayout
                        android:orientation="vertical"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content">

                        <!-- Report error that parent height is wrap_content -->
                        <WebView
                            android:layout_width="match_parent"
                            android:layout_height="match_parent" />

                        <!-- Suppressed -->
                        <WebView
                            android:layout_width="match_parent"
                            android:layout_height="match_parent"
                            tools:ignore="WebViewLayout" />
                    </LinearLayout>

                </LinearLayout>
                """
            ).indented()
        ).run().expect(
            """
            res/layout/webview.xml:18: Error: Placing a <WebView> in a parent element that uses a wrap_content layout_height can lead to subtle bugs; use match_parent instead [WebViewLayout]
                    <WebView
                     ~~~~~~~
                res/layout/webview.xml:15: wrap_content here may not work well with WebView below
            1 errors, 0 warnings
            """
        )
    }

    fun testMatchParentHeight() {
        lint().files(
            xml(
                "res/layout/webview2.xml",
                """
                <!-- Like webview.xml, but with a wrap on the height instead -->
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                              xmlns:tools="http://schemas.android.com/tools"
                              android:orientation="vertical"
                              android:layout_width="match_parent"
                              android:layout_height="match_parent">

                    <!-- OK -->
                    <WebView
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"/>

                    <LinearLayout
                            android:orientation="vertical"
                            android:layout_width="wrap_content"
                            android:layout_height="fill_parent">

                        <!-- Report error that parent height is wrap_content -->
                        <WebView
                                android:layout_width="match_parent"
                                android:layout_height="match_parent"/>

                        <!-- Suppressed -->
                        <WebView
                                android:layout_width="match_parent"
                                android:layout_height="match_parent"
                                tools:ignore="WebViewLayout"/>
                    </LinearLayout>

                </LinearLayout>
                """
            ).indented()
        ).run().expect(
            """
            res/layout/webview2.xml:19: Error: Placing a <WebView> in a parent element that uses a wrap_content layout_width can lead to subtle bugs; use match_parent instead [WebViewLayout]
                    <WebView
                     ~~~~~~~
                res/layout/webview2.xml:15: wrap_content here may not work well with WebView below
            1 errors, 0 warnings
            """
        )
    }

    fun testMissingLayoutHeight() {
        // Regression test for
        //   https://code.google.com/p/android/issues/detail?id=74646
        lint().files(
            xml(
                "res/layout/webview3.xml",
                """
                <!-- Note the lack of explicit 'layout_height' on root layout; it comes from the app's theme -->
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                  android:layout_width="match_parent"
                  android:orientation="vertical">

                    <!-- other views can go here -->

                    <WebView
                      android:id="@+id/webview"
                      android:layout_width="match_parent"
                      android:layout_height="match_parent" />

                </LinearLayout>
                """
            ).indented()
        ).run().expectClean()
    }
}
