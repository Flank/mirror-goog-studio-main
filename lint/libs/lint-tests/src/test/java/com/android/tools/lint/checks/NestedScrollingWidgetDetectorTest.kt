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

class NestedScrollingWidgetDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return NestedScrollingWidgetDetector()
    }

    fun testNested() {
        lint().files(
            xml(
                "res/layout/scrolling.xml",
                """
                <ScrollView
                    xmlns:android="http://schemas.android.com/apk/res/android"

                    android:layout_width="match_parent"
                    android:layout_height="match_parent">

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="match_parent">

                        <ListView
                            android:layout_width="match_parent"
                            android:layout_height="match_parent" />

                    </LinearLayout>

                </ScrollView>
                """
            ).indented()
        ).run().expect(
            """
            res/layout/scrolling.xml:11: Warning: The vertically scrolling ScrollView should not contain another vertically scrolling widget (ListView) [NestedScrolling]
                    <ListView
                     ~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }
}
