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

class ChildCountDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return ChildCountDetector()
    }

    fun testChildCount() {
        lint().files(
            xml(
                "res/layout/has_children.xml",
                """
                <ListView
                    xmlns:android="http://schemas.android.com/apk/res/android"

                    android:layout_width="match_parent"
                    android:layout_height="match_parent">

                    <ListView
                        android:layout_width="match_parent"
                        android:layout_height="match_parent" />

                </ListView>
                """
            ).indented()
        ).run().expect(
            """
            res/layout/has_children.xml:1: Warning: A list/grid should have no children declared in XML [AdapterViewChildren]
            <ListView
             ~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }

    fun testChildCountRequestFocus() {
        // A <requestFocus/> tag is okay.
        lint().files(
            xml(
                "res/layout/has_children2.xml",
                """
                <ListView
                    xmlns:android="http://schemas.android.com/apk/res/android"

                    android:layout_width="match_parent"
                    android:layout_height="match_parent">

                        <requestFocus/>

                </ListView>
                """
            ).indented()
        ).run().expectClean()
    }

    fun testAapt77836768() {
        // Regression test for https://issuetracker.google.com/77836768
        lint().files(
            xml(
                "res/layout/aapt.xml",
                """
                <ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
                  xmlns:aapt="http://schemas.android.com/aapt"
                  xmlns:app="http://schemas.android.com/apk/res-auto"
                  xmlns:tools="http://schemas.android.com/tools"
                  android:layout_width="wrap_content"
                  android:layout_height="wrap_content"
                  tools:layout_gravity="center">

                  <aapt:attr name="android:background">
                    <shape android:shape="rectangle">
                      <solid android:color="@android:color/white" />
                      <corners android:radius="8dp" />
                    </shape>
                  </aapt:attr>

                  <android.support.constraint.ConstraintLayout
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    tools:layout_gravity="center">

                  </android.support.constraint.ConstraintLayout>
                </ScrollView>
                """
            ).indented()
        ).run().expectClean()
    }
}
