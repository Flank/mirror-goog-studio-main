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

class RelativeOverlapDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return RelativeOverlapDetector()
    }

    fun testOneOverlap() {
        lint().files(
            xml(
                "res/layout/relative_overlap.xml",
                """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    android:id="@+id/container"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical">
                    <RelativeLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content">
                        <TextView
                            android:id="@+id/label1"
                            android:layout_alignParentLeft="true"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="@string/label1_text"
                            android:ellipsize="end" />
                        <TextView
                            android:id="@+id/label2"
                            android:layout_alignParentRight="true"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="@string/label2_text"
                            android:ellipsize="end" />
                        <TextView
                            android:id="@+id/circular1"
                            android:layout_alignParentBottom="true"
                            android:layout_toRightOf="@+id/circular2"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="@string/label1_text"
                            android:ellipsize="end" />
                        <TextView
                            android:id="@id/circular2"
                            android:layout_alignParentBottom="true"
                            android:layout_toRightOf="@id/circular1"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="@string/label2_text"
                            android:ellipsize="end" />
                        <TextView
                            android:id="@id/circular3"
                            android:layout_alignParentBottom="true"
                            android:layout_toRightOf="@id/circular1"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="@string/label2_text"
                            android:ellipsize="end" />
                    </RelativeLayout>
                    <RelativeLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content">
                        <TextView
                            android:id="@+id/label3"
                            android:layout_alignParentLeft="true"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:layout_toStartOf="@+id/label4"
                            android:gravity="start"
                            android:text="@string/label3_text"
                            android:ellipsize="end" />
                        <TextView
                            android:id="@id/label4"
                            android:layout_alignParentRight="true"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="@string/label3_text"
                            android:ellipsize="end" />
                    </RelativeLayout>
                    <RelativeLayout
                            android:layout_width="match_parent"
                            android:layout_height="match_parent">
                        <ImageView android:id="@+id/image"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content" />
                        <TextView android:id="@+id/text"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:layout_toRightOf="@id/image" />
                    </RelativeLayout>
                    <RelativeLayout
                            android:layout_width="match_parent"
                            android:layout_height="match_parent">
                        <ImageView android:id="@+id/image"
                            android:layout_alignParentRight="true"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content" />
                        <TextView android:id="@+id/text"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:layout_toLeftOf="@id/image" />
                    </RelativeLayout>
                </LinearLayout>
                """
            ).indented()
        ).run().expect(
            """
            res/layout/relative_overlap.xml:16: Warning: @id/label2 can overlap @id/label1 if @string/label1_text, @string/label2_text grow due to localized text expansion [RelativeOverlap]
                    <TextView
                     ~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }

    fun testOneOverlapPercent() {
        lint().files(
            xml(
                "res/layout/relative_percent_overlap.xml",
                """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    android:id="@+id/container"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical">
                    <android.support.percent.PercentRelativeLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content">
                        <TextView
                            android:id="@+id/label1"
                            android:layout_alignParentLeft="true"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="@string/label1_text"
                            android:ellipsize="end" />
                        <TextView
                            android:id="@+id/label2"
                            android:layout_alignParentRight="true"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="@string/label2_text"
                            android:ellipsize="end" />
                        <TextView
                            android:id="@+id/circular1"
                            android:layout_alignParentBottom="true"
                            android:layout_toRightOf="@+id/circular2"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="@string/label1_text"
                            android:ellipsize="end" />
                        <TextView
                            android:id="@id/circular2"
                            android:layout_alignParentBottom="true"
                            android:layout_toRightOf="@id/circular1"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="@string/label2_text"
                            android:ellipsize="end" />
                        <TextView
                            android:id="@id/circular3"
                            android:layout_alignParentBottom="true"
                            android:layout_toRightOf="@id/circular1"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="@string/label2_text"
                            android:ellipsize="end" />
                    </android.support.percent.PercentRelativeLayout>
                </LinearLayout>
                """
            ).indented()
        ).run().expect(
            """
            res/layout/relative_percent_overlap.xml:16: Warning: @id/label2 can overlap @id/label1 if @string/label1_text, @string/label2_text grow due to localized text expansion [RelativeOverlap]
                    <TextView
                     ~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }
}
