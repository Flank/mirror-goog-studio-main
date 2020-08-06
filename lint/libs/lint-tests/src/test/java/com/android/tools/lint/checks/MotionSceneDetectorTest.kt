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

class MotionSceneDetectorTest : AbstractCheckTest() {

    override fun getDetector() = MotionSceneDetector()

    fun testMissingCustomAttributeName() {
        lint().files(
            xml(
                "res/xml/missing_custom_attribute_name.xml",
                """
                <MotionScene
                        xmlns:android="http://schemas.android.com/apk/res/android"
                        xmlns:app="http://schemas.android.com/apk/res-auto">
                    <ConstraintSet android:id="@+id/start">
                        <Constraint
                            android:id="@id/widget">
                             <CustomAttribute app:customPixelDimension="2sp"/>
                        </Constraint>
                    </ConstraintSet>
                </MotionScene>
                """
            ).indented()
        ).run().expect(
            """
                res/xml/missing_custom_attribute_name.xml:7: Error: attributeName should be defined [MotionSceneFileValidationError]
                             <CustomAttribute app:customPixelDimension="2sp"/>
                              ~~~~~~~~~~~~~~~
                1 errors, 0 warnings
            """
        ).expectFixDiffs(
            """
                Fix for res/xml/missing_custom_attribute_name.xml line 7: Set attributeName:
                @@ -9 +9
                -             <CustomAttribute app:customPixelDimension="2sp" />
                +             <CustomAttribute
                +                 app:attributeName="[TODO]|"
                +                 app:customPixelDimension="2sp" />
            """
        )
    }

    fun testDuplicateCustomAttributeName() {
        lint().files(
            xml(
                "res/xml/duplicate_custom_attribute_name.xml",
                """
                <MotionScene
                        xmlns:android="http://schemas.android.com/apk/res/android"
                        xmlns:app="http://schemas.android.com/apk/res-auto">
                    <ConstraintSet android:id="@+id/start">
                        <Constraint
                            android:id="@id/widget">
                             <CustomAttribute
                                app:attributeName="textSize"
                                app:customPixelDimension="2sp"/>
                             <CustomAttribute
                                app:attributeName="textSize"
                                app:customPixelDimension="4sp"/>
                        </Constraint>
                    </ConstraintSet>
                </MotionScene>
                """
            ).indented()
        ).run()
            .expect(
                """
                res/xml/duplicate_custom_attribute_name.xml:10: Error: The custom attribute textSize was specified multiple times [MotionSceneFileValidationError]
                             <CustomAttribute
                              ~~~~~~~~~~~~~~~
                1 errors, 0 warnings
            """
            )
            .expectFixDiffs(
                """
                Fix for res/xml/duplicate_custom_attribute_name.xml line 10: Delete this custom attribute:
                @@ -10 +10
                -              <CustomAttribute
                -                 app:attributeName="textSize"
                -                 app:customPixelDimension="4sp"/>
            """
            )
    }

    fun testMultipleOnClickInTransition() {
        lint().files(
            xml(
                "res/xml/multiple_onclick_in_transition.xml",
                """
                <MotionScene
                        xmlns:android="http://schemas.android.com/apk/res/android"
                        xmlns:motion="http://schemas.android.com/apk/res-auto">
                    <Transition>
                        <OnClick motion:clickAction="toggle"  />
                        <OnClick motion:clickAction="transitionToEnd"  />
                    </Transition>
                </MotionScene>
                """
            ).indented()
        ).run()
            .expect(
                """
                res/xml/multiple_onclick_in_transition.xml:6: Error: Can only have one OnClick per Transition [MotionSceneFileValidationError]
                        <OnClick motion:clickAction="transitionToEnd"  />
                         ~~~~~~~
                1 errors, 0 warnings
            """
            )
            .expectFixDiffs(
                """
                Fix for res/xml/multiple_onclick_in_transition.xml line 6: Delete additional OnClick:
                @@ -6 +6
                -         <OnClick motion:clickAction="transitionToEnd"  />
            """
            )
    }
}
