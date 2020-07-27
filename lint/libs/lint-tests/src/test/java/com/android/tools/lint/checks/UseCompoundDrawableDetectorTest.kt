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

class UseCompoundDrawableDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return UseCompoundDrawableDetector()
    }

    fun testCompound() {

        val expected =
            """
            res/layout/compound.xml:1: Warning: This tag and its children can be replaced by one <TextView/> and a compound drawable [UseCompoundDrawables]
            <LinearLayout
             ~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        lint().files(
            xml(
                "res/layout/compound.xml",
                """
                    <LinearLayout
                        xmlns:android="http://schemas.android.com/apk/res/android"

                        android:layout_width="match_parent"
                        android:layout_height="match_parent">

                        <ImageView
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content" />

                        <TextView
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content" />

                    </LinearLayout>
                    """
            ).indented()
        ).run().expect(expected)
    }

    fun testCompound2() {
        // Ignore layouts that set a custom background
        lint().files(
            xml(
                "res/layout/compound2.xml",
                """
                <LinearLayout
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    android:background="@android:drawable/ic_dialog_alert"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent">

                    <ImageView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content" />

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content" />

                </LinearLayout>
                """
            ).indented()
        ).run().expectClean()
    }

    fun testCompound3() {
        // Ignore layouts that set an image scale type
        lint().files(
            xml(
                "res/layout/compound3.xml",
                """
                    <LinearLayout
                        xmlns:android="http://schemas.android.com/apk/res/android"

                        android:layout_width="match_parent"
                        android:layout_height="match_parent">

                        <ImageView
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:scaleType="fitStart" />

                        <TextView
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content" />

                    </LinearLayout>
                    """
            ).indented()
        ).run().expectClean()
    }

    fun testSkipClickable() {
        // Regression test for issue 133864395

        lint().files(
            xml(
                "res/layout/clickable.xml",
                """
                    <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                        xmlns:tools="http://schemas.android.com/tools"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content">

                        <ImageView
                            android:id="@+id/icon"
                            android:clickable="true"
                            android:focusable="true"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:contentDescription="@string/selectButton"/>

                        <TextView
                            android:id="@+id/text"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:layout_marginStart="@dimen/toolbarHomeIconLeftMargin"
                            android:drawableStart="@drawable/ic_path_folder_24dp"
                            android:drawablePadding="32dp"
                            android:ellipsize="end"
                            android:padding="16dp"
                            android:singleLine="true"
                            android:textAppearance="@style/TextAppearance.AppCompat.Medium"
                            android:textColor="?android:attr/textColorPrimary"
                            tools:text="Testing" />
                    </LinearLayout>
                    """
            ).indented()
        ).run().expectClean()
    }
}
