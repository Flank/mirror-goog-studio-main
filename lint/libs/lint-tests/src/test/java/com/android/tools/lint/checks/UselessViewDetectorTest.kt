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

class UselessViewDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return UselessViewDetector()
    }

    fun testUseless() {
        val expected =
            """
            res/layout/useless.xml:85: Warning: This FrameLayout view is useless (no children, no background, no id, no style) [UselessLeaf]
                <FrameLayout
                 ~~~~~~~~~~~
            res/layout/useless.xml:13: Warning: This LinearLayout layout or its FrameLayout parent is useless [UselessParent]
                    <LinearLayout
                     ~~~~~~~~~~~~
            res/layout/useless.xml:47: Warning: This LinearLayout layout or its FrameLayout parent is useless; transfer the background attribute to the other view [UselessParent]
                    <LinearLayout
                     ~~~~~~~~~~~~
            res/layout/useless.xml:65: Warning: This LinearLayout layout or its FrameLayout parent is useless; transfer the background attribute to the other view [UselessParent]
                    <LinearLayout
                     ~~~~~~~~~~~~
            0 errors, 4 warnings
            """
        lint().files(
            xml(
                "res/layout/useless.xml",
                """

                <merge xmlns:android="http://schemas.android.com/apk/res/android"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent" >

                    <!-- Neither parent nor child define background: delete is okay -->

                    <FrameLayout
                        android:id="@+id/LinearLayout"
                        android:layout_width="match_parent"
                        android:layout_height="match_parent" >

                        <LinearLayout
                            android:layout_width="match_parent"
                            android:layout_height="match_parent" >

                            <TextView
                                android:layout_width="wrap_content"
                                android:layout_height="wrap_content" />
                        </LinearLayout>
                    </FrameLayout>

                    <!-- Both define background: cannot be deleted -->

                    <FrameLayout
                        android:layout_width="match_parent"
                        android:layout_height="match_parent"
                        android:background="@drawable/bg" >

                        <LinearLayout
                            android:layout_width="match_parent"
                            android:layout_height="match_parent"
                            android:background="@drawable/bg" >

                            <TextView
                                android:layout_width="wrap_content"
                                android:layout_height="wrap_content" />
                        </LinearLayout>
                    </FrameLayout>

                    <!-- Only child defines background: delete is okay -->

                    <FrameLayout
                        android:layout_width="match_parent"
                        android:layout_height="match_parent" >

                        <LinearLayout
                            android:layout_width="match_parent"
                            android:layout_height="match_parent"
                            android:background="@drawable/bg" >

                            <TextView
                                android:layout_width="wrap_content"
                                android:layout_height="wrap_content" />
                        </LinearLayout>
                    </FrameLayout>

                    <!-- Only parent defines background: delete is okay -->

                    <FrameLayout
                        android:layout_width="match_parent"
                        android:layout_height="match_parent"
                        android:background="@drawable/bg" >

                        <LinearLayout
                            android:layout_width="match_parent"
                            android:layout_height="match_parent" >

                            <TextView
                                android:layout_width="wrap_content"
                                android:layout_height="wrap_content" />
                        </LinearLayout>
                    </FrameLayout>

                    <!-- Leaf cannot be deleted because it has a background -->

                    <FrameLayout
                        android:layout_width="match_parent"
                        android:layout_height="match_parent"
                        android:background="@drawable/bg" >
                    </FrameLayout>

                    <!-- Useless leaf -->

                    <FrameLayout
                        android:layout_width="match_parent"
                        android:layout_height="match_parent" >
                    </FrameLayout>
                </merge>
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testTabHost() {
        lint().files(
            xml(
                "res/layout/useless2.xml",
                """
                <TabHost xmlns:android="http://schemas.android.com/apk/res/android"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent" >

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="match_parent"
                        android:orientation="vertical" >

                        <TabWidget
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content" />

                        <FrameLayout
                            android:layout_width="match_parent"
                            android:layout_height="0px"
                            android:layout_weight="1" >

                            <Button
                                android:layout_width="wrap_content"
                                android:layout_height="wrap_content" />
                        </FrameLayout>
                    </LinearLayout>

                </TabHost>
                """
            ).indented()
        ).run().expectClean()
    }

    fun testStyleAttribute() {
        lint().files(
            xml(
                "res/layout/useless3.xml",
                """
                <TableRow
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    style="@style/keyboard_table_row">
                </TableRow>
                """
            ).indented()
        ).run().expectClean()
    }

    fun testUselessLeafRoot() {
        lint().files(
            xml(
                "res/layout/breadcrumbs_in_fragment.xml",
                """
                <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    android:layout_width="0dip"
                    android:layout_height="0dip"
                    android:visibility="gone" />
                """
            ).indented()
        ).run().expectClean()
    }

    fun testUseless65519() {
        // https://code.google.com/p/android/issues/detail?id=65519
        lint().files(
            xml(
                "res/layout/useless4.xml",
                """
                <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
                             android:layout_width="match_parent"
                             android:layout_height="match_parent"
                             android:background="@drawable/detail_panel_counter_bg">

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:padding="5dp">

                        <View
                            android:layout_width="5dp"
                            android:layout_height="5dp" />

                        <View
                            android:layout_width="5dp"
                            android:layout_height="5dp" />
                    </LinearLayout>
                </FrameLayout>
                """
            ).indented()
        ).run().expectClean()
    }

    fun testUselessWithPaddingAttrs() {
        // https://code.google.com/p/android/issues/detail?id=205250
        val expected =
            """
            res/layout/useless5.xml:5: Warning: This RelativeLayout layout or its FrameLayout parent is useless [UselessParent]
                <RelativeLayout
                 ~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        lint().files(
            xml(
                "res/layout/useless5.xml",
                """
                <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
                             android:layout_width="match_parent"
                             android:layout_height="wrap_content">

                    <RelativeLayout
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:paddingBottom="16dp"
                            android:paddingLeft="16dp"
                            android:paddingRight="16dp"
                            android:paddingTop="16dp">

                        <TextView
                                android:layout_width="wrap_content"
                                android:layout_height="wrap_content"/>
                    </RelativeLayout>
                </FrameLayout>
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testUselessParentWithStyleAttribute() {
        lint().files(
            xml(
                "res/layout/my_layout.xml",
                """
                <LinearLayout
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    android:orientation="vertical"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:background="@color/header">
                  <!-- The FrameLayout acts as grey header border around the searchbox -->
                  <FrameLayout style="@style/Header.SearchBox">
                    <!-- This is an editable form of @layout/search_field_unedittable -->
                    <LinearLayout
                        android:orientation="horizontal"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        style="@style/SearchBox">
                      <TextView
                          android:id="@+id/search_prefix"
                          style="@style/SearchBoxText.Prefix"
                          tools:text="From:"/>
                      <EditText
                          android:id="@+id/search_query"
                          android:layout_width="match_parent"
                          android:layout_height="wrap_content"
                          android:singleLine="true"
                          style="@style/SearchBoxText"/>
                    </LinearLayout>
                  </FrameLayout>
                </LinearLayout>
                """
            ).indented()
        ).run().expectClean()
    }

    fun testDataBinding() {
        // Regression test for 37140356
        lint().files(
            xml(
                "res/layout/layout.xml",
                """
                <FrameLayout
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:app="http://schemas.android.com/apk/res/foo.bar.baz"
                             android:layout_width="match_parent"
                             android:layout_height="wrap_content">
                        <LinearLayout
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:orientation="vertical"
                            app:viewModel="@{viewModel}" />
                </FrameLayout>
                """
            ).indented()
        ).run().expectClean()
    }
}
