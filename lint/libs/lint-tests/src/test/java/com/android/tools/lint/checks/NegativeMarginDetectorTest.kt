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

class NegativeMarginDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return NegativeMarginDetector()
    }

    fun testLayoutWithoutRepositorySupport() {
        lint().files(mNegative_margins).run().expect(
            """
            res/layout/negative_margins.xml:11: Warning: Margin values should not be negative [NegativeMargin]
                <TextView android:layout_marginTop="-1dp"/> <!-- WARNING -->
                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }

    fun testIncrementalInLayout() {
        lint().files(mNegative_margins2, mNegative_margins)
            .incremental("res/layout/negative_margins.xml").run().expect(
                """
                res/layout/negative_margins.xml:11: Warning: Margin values should not be negative [NegativeMargin]
                    <TextView android:layout_marginTop="-1dp"/> <!-- WARNING -->
                              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                res/layout/negative_margins.xml:13: Warning: Margin values should not be negative (@dimen/negative is defined as -16dp in values/negative_margins.xml [NegativeMargin]
                    <TextView android:layout_marginTop="@dimen/negative"/> <!-- WARNING -->
                              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 2 warnings
                """
            )
    }

    fun testValuesWithoutRepositorySupport() {
        lint()
            .files(mNegative_margins2)
            .supportResourceRepository(false)
            .run()
            .expect(
                """
                res/values/negative_margins.xml:11: Warning: Margin values should not be negative [NegativeMargin]
                        <item name="android:layout_marginBottom">-5dp</item> <!-- WARNING -->
                                                                 ^
                0 errors, 1 warnings
                """
            )
    }

    fun testIncrementalInValues() {
        lint().files(mNegative_margins2, mNegative_margins)
            .incremental("res/values/negative_margins.xml").run().expect(
                """
                res/values/negative_margins.xml:10: Warning: Margin values should not be negative (@dimen/negative is defined as -16dp in values/negative_margins.xml [NegativeMargin]
                        <item name="android:layout_marginTop">@dimen/negative</item> <!-- WARNING -->
                                                              ^
                res/values/negative_margins.xml:11: Warning: Margin values should not be negative [NegativeMargin]
                        <item name="android:layout_marginBottom">-5dp</item> <!-- WARNING -->
                                                                 ^
                0 errors, 2 warnings
                """
            )
    }

    fun testBatch() {
        lint()
            .files(mNegative_margins2, mNegative_margins)
            .supportResourceRepository(false)
            .run()
            .expect(
                """
                res/layout/negative_margins.xml:11: Warning: Margin values should not be negative [NegativeMargin]
                    <TextView android:layout_marginTop="-1dp"/> <!-- WARNING -->
                              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                res/values/negative_margins.xml:11: Warning: Margin values should not be negative [NegativeMargin]
                        <item name="android:layout_marginBottom">-5dp</item> <!-- WARNING -->
                                                                 ^
                res/layout/negative_margins.xml:13: Warning: Margin values should not be negative [NegativeMargin]
                    <TextView android:layout_marginTop="@dimen/negative"/> <!-- WARNING -->
                              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 3 warnings
                """
            )
    }

    // Sample code
    private val mNegative_margins = xml(
        "res/layout/negative_margins.xml",
        """

        <GridLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:orientation="vertical">

            <TextView android:layout_margin="1dp"/> <!-- OK -->
            <TextView android:layout_marginLeft="1dp"/> <!-- OK -->
            <TextView android:layout_marginLeft="0dp"/> <!-- OK -->
            <TextView android:layout_marginTop="-1dp"/> <!-- WARNING -->
            <TextView android:layout_marginTop="@dimen/positive"/> <!-- OK -->
            <TextView android:layout_marginTop="@dimen/negative"/> <!-- WARNING -->
            <TextView android:paddingLeft="-1dp"/> <!-- OK -->
            <TextView android:layout_marginTop="-1dp" tools:ignore="NegativeMargin"/> <!-- SUPPRESSED -->

        </GridLayout>
        """
    ).indented()

    // Sample code
    private val mNegative_margins2 = xml(
        "res/values/negative_margins.xml",
        """

        <resources>
            <dimen name="activity_horizontal_margin">16dp</dimen>
            <dimen name="positive">16dp</dimen>
            <dimen name="negative">-16dp</dimen>

            <style name="MyStyle">
                <item name="android:layout_margin">5dp</item> <!-- OK -->
                <item name="android:layout_marginLeft">@dimen/positive</item> <!-- OK -->
                <item name="android:layout_marginTop">@dimen/negative</item> <!-- WARNING -->
                <item name="android:layout_marginBottom">-5dp</item> <!-- WARNING -->
            </style>
        </resources>
        """
    ).indented()
}
