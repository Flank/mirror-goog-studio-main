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

class TooLintFixGroupViewsDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return TooManyViewsDetector()
    }

    fun testTooMany() {
        lint().files(
            xml(
                "res/layout/too_many.xml",
                """
                <FrameLayout
                    xmlns:android="http://schemas.android.com/apk/res/android"

                    android:layout_width="match_parent"
                    android:layout_height="match_parent">

                    <Button
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Ok" />

                    <Button
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Ok" />

                    <Button
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Ok" />

                    <Button
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Ok" />

                    <Button
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Ok" />

                    <Button
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Ok" />

                    <Button
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Ok" />

                    <Button
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Ok" />

                    <Button
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Ok" />

                    <Button
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Ok" />

                    <Button
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Ok" />

                    <Button
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Ok" />

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="match_parent">

                        <Button
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Ok" />

                        <Button
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Ok" />

                        <Button
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Ok" />

                        <Button
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Ok" />

                        <Button
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Ok" />

                        <Button
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Ok" />

                        <Button
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Ok" />

                        <Button
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Ok" />

                        <Button
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Ok" />

                        <Button
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Ok" />

                        <Button
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Ok" />

                        <Button
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Ok" />

                        <Button
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Ok" />

                        <Button
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Ok" />

                        <Button
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Ok" />

                        <Button
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Ok" />

                            <LinearLayout
                                android:layout_width="match_parent"
                                android:layout_height="match_parent">

                                <Button
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    android:text="Ok" />

                                <Button
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    android:text="Ok" />

                                <Button
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    android:text="Ok" />

                                <Button
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    android:text="Ok" />

                                <Button
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    android:text="Ok" />

                                <Button
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    android:text="Ok" />

                                <Button
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    android:text="Ok" />

                                <Button
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    android:text="Ok" />

                                <Button
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    android:text="Ok" />

                                <Button
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    android:text="Ok" />

                                <Button
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    android:text="Ok" />

                                <Button
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    android:text="Ok" />

                                <Button
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    android:text="Ok" />

                                <Button
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    android:text="Ok" />

                                <Button
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    android:text="Ok" />

                                <Button
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    android:text="Ok" />

                            </LinearLayout>

                    </LinearLayout>

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="match_parent">

                        <Button
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Ok" />

                        <Button
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Ok" />

                        <Button
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Ok" />

                        <Button
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Ok" />

                        <Button
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Ok" />

                        <Button
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Ok" />

                        <Button
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Ok" />

                        <Button
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Ok" />

                        <Button
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Ok" />

                        <Button
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Ok" />

                        <Button
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Ok" />

                        <Button
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Ok" />

                        <Button
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Ok" />

                        <Button
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Ok" />

                        <Button
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Ok" />

                        <Button
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Ok" />

                            <LinearLayout
                                android:layout_width="match_parent"
                                android:layout_height="match_parent">

                                <Button
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    android:text="Ok" />

                                <Button
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    android:text="Ok" />

                                <Button
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    android:text="Ok" />

                                <Button
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    android:text="Ok" />

                                <Button
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    android:text="Ok" />

                                <Button
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    android:text="Ok" />

                                <Button
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    android:text="Ok" />

                                <Button
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    android:text="Ok" />

                                <Button
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    android:text="Ok" />

                                <Button
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    android:text="Ok" />

                                <Button
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    android:text="Ok" />

                                <Button
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    android:text="Ok" />

                                <Button
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    android:text="Ok" />

                                <Button
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    android:text="Ok" />

                                <Button
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    android:text="Ok" />

                                <Button
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    android:text="Ok" />

                            </LinearLayout>

                    </LinearLayout>

                </FrameLayout>
                """
            ).indented()
        ).run().expect(
            """
            res/layout/too_many.xml:397: Warning: too_many.xml has more than 80 views, bad for performance [TooManyViews]
                            <Button
                             ~~~~~~
            0 errors, 1 warnings
            """
        )
    }

    fun testTooDeep() {
        lint().files(
            xml(
                "res/layout/too_deep.xml",
                """
                <LinearLayout
                    xmlns:android="http://schemas.android.com/apk/res/android"

                    android:layout_width="match_parent"
                    android:layout_height="match_parent">

                    <Button
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Ok" />

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="match_parent">

                        <LinearLayout
                            android:layout_width="match_parent"
                            android:layout_height="match_parent">

                            <LinearLayout
                                android:layout_width="match_parent"
                                android:layout_height="match_parent">

                                <LinearLayout
                                    android:layout_width="match_parent"
                                    android:layout_height="match_parent">

                                    <LinearLayout
                                        android:layout_width="match_parent"
                                        android:layout_height="match_parent">

                                        <LinearLayout
                                            android:layout_width="match_parent"
                                            android:layout_height="match_parent">

                                            <LinearLayout
                                                android:layout_width="match_parent"
                                                android:layout_height="match_parent">

                                                <LinearLayout
                                                    android:layout_width="match_parent"
                                                    android:layout_height="match_parent">

                                                    <LinearLayout
                                                        android:layout_width="match_parent"
                                                        android:layout_height="match_parent">

                                                        <LinearLayout
                                                            android:layout_width="match_parent"
                                                            android:layout_height="match_parent">

                                                            <LinearLayout
                                                                android:layout_width="match_parent"
                                                                android:layout_height="match_parent">

                                                                <Button
                                                                    android:layout_width="wrap_content"
                                                                    android:layout_height="wrap_content"
                                                                    android:text="Ok" />

                                                            </LinearLayout>
                                                        </LinearLayout>
                                                    </LinearLayout>
                                                </LinearLayout>
                                            </LinearLayout>
                                        </LinearLayout>
                                    </LinearLayout>
                                </LinearLayout>
                            </LinearLayout>
                        </LinearLayout>
                    </LinearLayout>
                </LinearLayout>
                """
            ).indented()
        ).run().expect(
            """
            res/layout/too_deep.xml:44: Warning: too_deep.xml has more than 10 levels, bad for performance [TooDeepLayout]
                                                <LinearLayout
                                                 ~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }
}
