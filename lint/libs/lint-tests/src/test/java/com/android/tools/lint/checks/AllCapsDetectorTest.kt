/*
 * Copyright (C) 2016 The Android Open Source Project
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

class AllCapsDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return AllCapsDetector()
    }

    fun testAllCaps() {
        lint().files(
            xml(
                "res/layout/constraint.xml",
                """
                <?xml version="1.0" encoding="utf-8"?>
                <merge xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    xmlns:app="http://schemas.android.com/apk/res-auto">
                    <Button
                        android:text="@string/plain"
                        android:textAllCaps="true"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content" />
                    <Button
                        android:text="@string/has_markup"
                        android:textAllCaps="true"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content" />
                    <Button
                        android:text="@string/has_markup"
                        android:textAllCaps="false"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content" />
                    <Button
                        android:text="@string/has_markup"
                        android:textAllCaps="true"
                        tools:ignore="AllCaps"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content" />
                </merge>
                """
            ).indented(),
            xml(
                "res/values/strings.xml",
                """
                <resources>
                    <string name="plain">Home Sample</string>
                    <string name="has_markup">This is <b>bold</b></string>
                </resources>
                """
            ).indented()
        )
            .incremental("res/layout/constraint.xml")
            .run().expect(
                """
                res/layout/constraint.xml:12: Warning: Using textAllCaps with a string (has_markup) that contains markup; the markup will be dropped by the caps conversion [AllCaps]
                        android:textAllCaps="true"
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 1 warnings
                """
            )
    }
}
