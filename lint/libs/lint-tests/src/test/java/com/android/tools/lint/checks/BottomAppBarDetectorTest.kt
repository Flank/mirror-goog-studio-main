/*
 * Copyright (C) 2018 The Android Open Source Project
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

class BottomAppBarDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return BottomAppBarDetector()
    }

    fun testBasic() {
        lint().files(
            xml(
                "res/layout/ok.xml",
                """
                <android.support.design.widget.CoordinatorLayout
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:app="http://schemas.android.com/apk/res-auto"
                    xmlns:tools="http://schemas.android.com/tools"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:background="#eeeeee">

                  <android.support.design.bottomappbar.BottomAppBar
                      android:id="@+id/bottom_app_bar"
                      style="@style/Widget.MaterialComponents.BottomAppBar"
                      android:layout_width="match_parent"
                      android:layout_height="wrap_content"
                      android:layout_gravity="bottom"
                      app:navigationIcon="@drawable/ic_menu_black_24dp"/>

                  <android.support.design.widget.FloatingActionButton
                      android:id="@+id/fab"
                      android:layout_width="wrap_content"
                      android:layout_height="wrap_content"
                      android:tint="@android:color/white"
                      app:layout_anchor="@id/bottom_app_bar"
                      app:srcCompat="@drawable/ic_add_black_24dp"
                      tools:ignore="RtlHardcoded"/>
                </android.support.design.widget.CoordinatorLayout>
            """
            ).indented(),
            xml(
                "res/layout/wrong1.xml",
                """
                <LinearLayout
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:app="http://schemas.android.com/apk/res-auto"
                    xmlns:tools="http://schemas.android.com/tools"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:background="#eeeeee">

                  <android.support.design.bottomappbar.BottomAppBar
                      android:id="@+id/bottom_app_bar"
                      style="@style/Widget.MaterialComponents.BottomAppBar"
                      android:layout_width="match_parent"
                      android:layout_height="wrap_content"
                      android:layout_gravity="bottom"
                      app:navigationIcon="@drawable/ic_menu_black_24dp"/>

                </LinearLayout>
            """
            ).indented(),
            xml(
                "res/layout/wrong2.xml",
                """
                <com.google.android.material.bottomappbar.BottomAppBar
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:app="http://schemas.android.com/apk/res-auto"
                    android:id="@+id/bottom_app_bar"
                    style="@style/Widget.MaterialComponents.BottomAppBar"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_gravity="bottom"
app:navigationIcon="@drawable/ic_menu_black_24dp"/>
            """
            ).indented()
        ).run().expect(
            """
            res/layout/wrong1.xml:9: Error: This BottomAppBar must be wrapped in a CoordinatorLayout (android.support.design.widget.CoordinatorLayout) [BottomAppBar]
              <android.support.design.bottomappbar.BottomAppBar
               ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            res/layout/wrong2.xml:1: Error: This BottomAppBar must be wrapped in a CoordinatorLayout (androidx.coordinatorlayout.widget.CoordinatorLayout) [BottomAppBar]
                            <com.google.android.material.bottomappbar.BottomAppBar
                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            2 errors, 0 warnings
            """
        )
    }
}
