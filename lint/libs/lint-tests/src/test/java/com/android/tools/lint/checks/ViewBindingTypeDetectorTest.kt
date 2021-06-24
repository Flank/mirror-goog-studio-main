/*
 * Copyright (C) 2021 The Android Open Source Project
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

class ViewBindingTypeDetectorTest : AbstractCheckTest() {
    override fun getDetector() = ViewBindingTypeDetector()

    // This example will be extracted into issue documentation
    fun testDocumentationExample() {
        lint().files(
            xml(
                "res/layout/db.xml",
                """
                <layout
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    tools:keep="@layout/db">
                    <EditText android:id="@+id/test_view" tools:viewBindingType="TextView" />
                </layout>
                """
            ).indented(),
            xml(
                "res/layout/vb.xml",
                """
                <LinearLayout
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    tools:keep="@layout/vb">
                    <EditText tools:viewBindingType="TextView" /> <!-- missing id -->
                    <include android:id="@+id/included" layout="@layout/included" tools:viewBindingType="TextView" />
                    <EditText android:id="@+id/inconsistent" tools:viewBindingType="TextView" />
                </LinearLayout>
                """
            ).indented(),
            xml(
                "res/layout-land/vb.xml",
                """
                <LinearLayout
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    tools:keep="@layout/vb">
                    <SurfaceView android:id="@+id/inconsistent" />
                </LinearLayout>
                """
            ).indented()
        ).run().expect(
            """
            res/layout/db.xml:5: Error: tools:viewBindingType is not applicable in data binding layouts. [ViewBindingType]
                <EditText android:id="@+id/test_view" tools:viewBindingType="TextView" />
                                                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            res/layout/vb.xml:5: Error: tools:viewBindingType should be defined on a tag that also defines an android:id. Otherwise, its value won't have any effect. [ViewBindingType]
                <EditText tools:viewBindingType="TextView" /> <!-- missing id -->
                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            res/layout/vb.xml:6: Error: tools:viewBindingType is not applicable on <include> tags. [ViewBindingType]
                <include android:id="@+id/included" layout="@layout/included" tools:viewBindingType="TextView" />
                                                                              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            res/layout/vb.xml:7: Error: tools:viewBindingType is not defined consistently, with the following types resolved across layouts: android.view.SurfaceView, android.widget.TextView [ViewBindingType]
                <EditText android:id="@+id/inconsistent" tools:viewBindingType="TextView" />
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                res/layout-land/vb.xml: Using viewBindingType SurfaceView here
            4 errors, 0 warnings
            """
        )
    }

    fun testViewBindingTypeHappyPath() {
        lint().files(
            xml(
                "res/layout/vb.xml",
                """
                <LinearLayout
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    tools:keep="@layout/vb">
                    <EditText android:id="@+id/text_view" tools:viewBindingType="TextView" />
                    <EditText android:id="@+id/text_view2" tools:viewBindingType="TextView" />
                    <view android:id="@+id/text_view3" class="EditText" tools:viewBindingType="TextView" />
                    <view android:id="@+id/text_view4" class="EditText" tools:viewBindingType="TextView" />
                    <!-- Redundant definitions but legal -->
                    <TextView android:id="@+id/text_view5" tools:viewBindingType="TextView" />
                    <view android:id="@+id/text_view6" class="TextView" tools:viewBindingType="TextView" />
                    <TextView android:id="@+id/text_view7" tools:viewBindingType="android.widget.TextView" />
                </LinearLayout>
                """
            ).indented(),
            xml(
                "res/layout-land/vb.xml",
                """
                <LinearLayout
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    tools:keep="@layout/vb">
                    <CheckedTextView android:id="@+id/text_view" tools:viewBindingType="TextView" />
                    <TextView android:id="@+id/text_view2" />
                    <view android:id="@+id/text_view2" class="CheckedTextView" tools:viewBindingType="TextView" />
                    <view android:id="@+id/text_view2" class="TextView"/>
                </LinearLayout>
                """
            ).indented(),
        ).run().expectClean()
    }

    fun testViewBindingTypeNotApplicableInDataBindingLayouts() {
        lint().files(
            xml(
                "res/layout/db.xml",
                """
                <layout
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    tools:keep="@layout/db">
                    <EditText android:id="@+id/test_view" tools:viewBindingType="TextView" />
                </layout>
                """
            ).indented()
        ).run().expect(
            """
            res/layout/db.xml:5: Error: tools:viewBindingType is not applicable in data binding layouts. [ViewBindingType]
                <EditText android:id="@+id/test_view" tools:viewBindingType="TextView" />
                                                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        )
    }

    fun testViewBindingTypeNotApplicableOnIncludeTags() {
        lint().files(
            xml(
                "res/layout/included.xml",
                """
                <merge
                    xmlns:tools="http://schemas.android.com/tools"
                    tools:keep="@layout/included">
                    <Button />
                </merge>
                """
            ).indented(),
            xml(
                "res/layout/vb.xml",
                """
                <LinearLayout
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    tools:keep="@layout/vb">
                    <include android:id="@+id/included" layout="@layout/included" tools:viewBindingType="TextView" />
                </LinearLayout>
                """
            ).indented()
        ).run().expect(
            """
            res/layout/vb.xml:5: Error: tools:viewBindingType is not applicable on <include> tags. [ViewBindingType]
                <include android:id="@+id/included" layout="@layout/included" tools:viewBindingType="TextView" />
                                                                              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        )
    }

    fun testViewBindingTypeRequiresIdBeSet() {
        lint().files(
            xml(
                "res/layout/vb.xml",
                """
                <LinearLayout
                    xmlns:tools="http://schemas.android.com/tools"
                    tools:keep="@layout/vb">
                    <EditText tools:viewBindingType="TextView" />
                </LinearLayout>
                """
            ).indented()
        ).run().expect(
            """
            res/layout/vb.xml:4: Error: tools:viewBindingType should be defined on a tag that also defines an android:id. Otherwise, its value won't have any effect. [ViewBindingType]
                <EditText tools:viewBindingType="TextView" />
                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        )
    }

    fun testViewBindingTypeMustBeDefinedConsistentlyAcrossLayouts() {
        lint().files(
            xml(
                "res/layout/vb.xml",
                """
                <LinearLayout
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    tools:keep="@layout/vb">
                    <EditText android:id="@+id/inconsistent" tools:viewBindingType="TextView" />
                    <EditText android:id="@+id/inconsistent2" tools:viewBindingType="TextView" />
                </LinearLayout>
                """
            ).indented(),
            xml(
                "res/layout-land/vb.xml",
                """
                <LinearLayout
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    tools:keep="@layout/vb">
                    <ExtractEditText android:id="@+id/inconsistent" tools:viewBindingType="EditText" />
                    <EditText android:id="@+id/inconsistent2" />
                </LinearLayout>
                """
            ).indented(),
            // Make sure IDs don't leak across layouts - these should be reported as separate errors
            xml(
                "res/layout/vb_alt.xml",
                """
                <LinearLayout
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    tools:keep="@layout/vb_alt">
                    <CheckBox android:id="@+id/inconsistent" tools:viewBindingType="Button" />
                </LinearLayout>
                """
            ).indented(),
            xml(
                "res/layout-land/vb_alt.xml",
                """
                <LinearLayout
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    tools:keep="@layout/vb_alt">
                    <RadioButton android:id="@+id/inconsistent" tools:viewBindingType="CompoundButton" />
                </LinearLayout>
                """
            ).indented(),
        ).run().expect(
            """
            res/layout-land/vb.xml:5: Error: tools:viewBindingType is not defined consistently, with the following types resolved across layouts: android.widget.EditText, android.widget.TextView [ViewBindingType]
                <ExtractEditText android:id="@+id/inconsistent" tools:viewBindingType="EditText" />
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                res/layout/vb.xml: Using viewBindingType TextView here
            res/layout/vb.xml:5: Error: tools:viewBindingType is not defined consistently, with the following types resolved across layouts: android.widget.EditText, android.widget.TextView [ViewBindingType]
                <EditText android:id="@+id/inconsistent" tools:viewBindingType="TextView" />
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                res/layout-land/vb.xml: Using viewBindingType EditText here
            res/layout/vb.xml:6: Error: tools:viewBindingType is not defined consistently, with the following types resolved across layouts: android.widget.EditText, android.widget.TextView [ViewBindingType]
                <EditText android:id="@+id/inconsistent2" tools:viewBindingType="TextView" />
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                res/layout-land/vb.xml: Using viewBindingType EditText here
            res/layout-land/vb_alt.xml:5: Error: tools:viewBindingType is not defined consistently, with the following types resolved across layouts: android.widget.Button, android.widget.CompoundButton [ViewBindingType]
                <RadioButton android:id="@+id/inconsistent" tools:viewBindingType="CompoundButton" />
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                res/layout/vb_alt.xml: Using viewBindingType Button here
            res/layout/vb_alt.xml:5: Error: tools:viewBindingType is not defined consistently, with the following types resolved across layouts: android.widget.Button, android.widget.CompoundButton [ViewBindingType]
                <CheckBox android:id="@+id/inconsistent" tools:viewBindingType="Button" />
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                res/layout-land/vb_alt.xml: Using viewBindingType CompoundButton here
            5 errors, 0 warnings
            """
        )
    }

    fun testConsistencyWhenIsolated() {
        lint().files(
            xml(
                "res/layout/vb.xml",
                """
                <LinearLayout
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    tools:keep="@layout/vb">
                    <EditText android:id="@+id/inconsistent" tools:viewBindingType="TextView" />
                    <EditText android:id="@+id/inconsistent2" tools:viewBindingType="TextView" />
                </LinearLayout>
                """
            ).indented(),
            xml(
                "res/layout-land/vb.xml",
                """
                <LinearLayout
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    tools:keep="@layout/vb">
                    <ExtractEditText android:id="@+id/inconsistent" tools:viewBindingType="EditText" />
                    <SurfaceView android:id="@+id/inconsistent2" />
                </LinearLayout>
                """
            ).indented()
        ).isolated("res/layout/vb.xml").run().expect(
            """
            res/layout/vb.xml:5: Error: tools:viewBindingType is not defined consistently, with the following types resolved across layouts: android.widget.EditText, android.widget.TextView [ViewBindingType]
                <EditText android:id="@+id/inconsistent" tools:viewBindingType="TextView" />
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            res/layout/vb.xml:6: Error: tools:viewBindingType is not defined consistently, with the following types resolved across layouts: android.view.SurfaceView, android.widget.TextView [ViewBindingType]
                <EditText android:id="@+id/inconsistent2" tools:viewBindingType="TextView" />
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            2 errors, 0 warnings
            """
        )
    }

    fun testViewBindingTypeMustMatchTag() {
        lint().files(
            xml(
                "res/layout/vb.xml",
                """
                <LinearLayout
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    tools:keep="@layout/vb">
                    <EditText android:id="@+id/incompatible" tools:viewBindingType="Button" />
                    <view class="EditText" android:id="@+id/incompatible2" tools:viewBindingType="Button" />
                    <EditText android:id="@+id/compatible" tools:viewBindingType="TextView" />
                    <TextView android:id="@+id/incompatible3" tools:viewBindingType="EditText" />
                </LinearLayout>
                """
            ).indented(),
        ).run().expect(
            """
            res/layout/vb.xml:5: Error: tools:viewBindingType (Button) is not compatible (i.e. a match or superclass) with its tag (EditText). [ViewBindingType]
                <EditText android:id="@+id/incompatible" tools:viewBindingType="Button" />
                                                         ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            res/layout/vb.xml:6: Error: tools:viewBindingType (Button) is not compatible (i.e. a match or superclass) with its tag (EditText). [ViewBindingType]
                <view class="EditText" android:id="@+id/incompatible2" tools:viewBindingType="Button" />
                                                                       ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            res/layout/vb.xml:8: Error: tools:viewBindingType (EditText) is not compatible (i.e. a match or superclass) with its tag (TextView). [ViewBindingType]
                <TextView android:id="@+id/incompatible3" tools:viewBindingType="EditText" />
                                                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            3 errors, 0 warnings
            """
        )
    }

    fun testViewBindingTypeMustSubclassView() {
        lint().files(
            kotlin(
                """
                package test.pkg

                import android.content.Context
                import android.view.View

                class CustomViewBase(ctx: Context) : View(ctx)
                """
            ).indented(),
            kotlin(
                """
                package test.pkg

                import android.content.Context

                class CustomViewChild(ctx: Context) : CustomViewBase(ctx)
                """
            ).indented(),
            kotlin(
                """
                package test.pkg

                class NonView
                """
            ).indented(),
            xml(
                "res/layout/vb.xml",
                """
                <LinearLayout
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    tools:keep="@layout/vb">
                    <CustomViewChild android:id="@+id/bad_type" tools:viewBindingType="String" />
                    <CustomViewChild android:id="@+id/bad_type2" tools:viewBindingType="test.pkg.NonView" />
                    <CustomViewChild android:id="@+id/good_type" tools:viewBindingType="test.pkg.CustomViewBase" />
                    <CustomViewChild android:id="@+id/good_type2" tools:viewBindingType="android.view.View" />
                </LinearLayout>
                """
            ).indented(),
        ).run().expect(
            """
            res/layout/vb.xml:5: Error: tools:viewBindingType (String) must refer to a class that inherits from android.view.View [ViewBindingType]
                <CustomViewChild android:id="@+id/bad_type" tools:viewBindingType="String" />
                                                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            res/layout/vb.xml:6: Error: tools:viewBindingType (test.pkg.NonView) must refer to a class that inherits from android.view.View [ViewBindingType]
                <CustomViewChild android:id="@+id/bad_type2" tools:viewBindingType="test.pkg.NonView" />
                                                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            2 errors, 0 warnings
            """
        )
    }
}
