/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.tools.lint.detector.api.LintFix

class MotionLayoutDetectorTest : AbstractCheckTest() {

    override fun getDetector() = MotionLayoutDetector()

    fun testExistingMotionSceneFile() {
        lint().files(
            xml("res/xml/motion_scene.xml", "<MotionScene/>"),
            xml(
                "res/layout/motion_test.xml",
                """
                <android.support.constraint.motion.MotionLayout
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:app="http://schemas.android.com/apk/res-auto"
                    android:id="@+id/motionLayout"
                    app:layoutDescription="@xml/motion_scene"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent">
                </android.support.constraint.motion.MotionLayout>
                """
            ).indented()
        ).run().expectClean()
    }

    fun testMissingMotionSceneFile() {
        lint().files(
            xml(
                "res/layout/motion_test.xml",
                """
                <android.support.constraint.motion.MotionLayout
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:app="http://schemas.android.com/apk/res-auto"
                    android:id="@+id/motionLayout"
                    app:layoutDescription="@xml/motion_scene"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent">
                </android.support.constraint.motion.MotionLayout>
                """
            ).indented()
        )
            .checkMessage({ _, _, _, _, _, data -> checkData("@xml/motion_scene", data) })
            .run().expect(
                """
                res/layout/motion_test.xml:5: Error: The motion scene file: @xml/motion_scene doesn't exist [MotionLayoutInvalidSceneFileReference]
                    app:layoutDescription="@xml/motion_scene"
                                           ~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
            """
            )
    }

    fun testMissingMotionSceneFileDisabledInIncremental() {
        lint().files(
            xml(
                "res/layout/motion_test.xml",
                """
                <android.support.constraint.motion.MotionLayout
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:app="http://schemas.android.com/apk/res-auto"
                    android:id="@+id/motionLayout"
                    app:layoutDescription="@xml/motion_scene"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent">
                </android.support.constraint.motion.MotionLayout>
                """
            ).indented()
        ).incremental("res/layout/motion_test.xml").run().expectClean()
    }

    fun testInvalidLayoutDescription() {
        lint().files(
            xml(
                "res/layout/motion_test.xml",
                """
                <android.support.constraint.motion.MotionLayout
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:app="http://schemas.android.com/apk/res-auto"
                    android:id="@+id/motionLayout"
                    app:layoutDescription="5678"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent">
                </android.support.constraint.motion.MotionLayout>
                """
            ).indented()
        )
            .checkMessage({ _, _, _, _, _, data -> checkData("@xml/motion_test_scene", data) })
            .run().expect(
                """
                res/layout/motion_test.xml:5: Error: 5678 is an invalid value for layoutDescription [MotionLayoutInvalidSceneFileReference]
                    app:layoutDescription="5678"
                                           ~~~~
                1 errors, 0 warnings
            """
            )
    }

    fun testMissingLayoutDescription() {
        lint().files(
            xml(
                "res/layout/motion_test.xml",
                """
                <android.support.constraint.motion.MotionLayout
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:app="http://schemas.android.com/apk/res-auto"
                    android:id="@+id/motionLayout"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent">
                </android.support.constraint.motion.MotionLayout>
                """
            ).indented()
        )
            .checkMessage({ _, _, _, _, _, data -> checkData("@xml/motion_test_scene", data) })
            .run().expect(
                """
                res/layout/motion_test.xml:1: Error: The attribute: layoutDescription is missing [MotionLayoutInvalidSceneFileReference]
                <android.support.constraint.motion.MotionLayout
                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
            """
            )
    }

    private fun checkData(expected: String, fixData: LintFix?) {
        val map = fixData as? LintFix.DataMap ?: error("Expected a DataMap")
        assertEquals(expected, map.get(String::class.java))
    }
}
