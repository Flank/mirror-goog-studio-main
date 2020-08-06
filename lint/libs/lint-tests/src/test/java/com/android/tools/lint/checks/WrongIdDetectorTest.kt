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

class WrongIdDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return WrongIdDetector()
    }

    fun testBasic() {
        lint().files(
            mLayout1, mLayout2, mIds
        ).run().expect(
            """
            res/layout/layout1.xml:14: Error: The id "button5" is not defined anywhere. Did you mean one of {button1, button2, button3, button4} ? [UnknownId]
                    android:layout_alignBottom="@+id/button5"
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            res/layout/layout1.xml:17: Error: The id "my_id3" is not defined anywhere. Did you mean one of {my_id0, my_id1, my_id2} ? [UnknownId]
                    android:layout_alignRight="@+id/my_id3"
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            res/layout/layout1.xml:18: Error: @id/my_id1 is not a sibling in the same RelativeLayout [NotSibling]
                    android:layout_alignTop="@id/my_id1"
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            res/layout/layout1.xml:15: Warning: The id "my_id2" is not referring to any views in this layout [UnknownIdInLayout]
                    android:layout_alignLeft="@+id/my_id2"
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            3 errors, 1 warnings
            """
        )
    }

    fun testSingleFile() {
        val expected =
            """
            res/layout/layout1.xml:18: Error: @id/my_id1 is not a sibling in the same RelativeLayout [NotSibling]
                    android:layout_alignTop="@id/my_id1"
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            res/layout/layout1.xml:14: Warning: The id "button5" is not referring to any views in this layout [UnknownIdInLayout]
                    android:layout_alignBottom="@+id/button5"
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            res/layout/layout1.xml:15: Warning: The id "my_id2" is not referring to any views in this layout [UnknownIdInLayout]
                    android:layout_alignLeft="@+id/my_id2"
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            res/layout/layout1.xml:17: Warning: The id "my_id3" is not referring to any views in this layout [UnknownIdInLayout]
                    android:layout_alignRight="@+id/my_id3"
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 3 warnings
            """
        lint()
            .files(mLayout1)
            .supportResourceRepository(false)
            .incremental(mLayout1.targetRelativePath)
            .run()
            .expect(expected)
    }

    fun testSuppressed() {
        lint().files(
            mIgnorelayout1, mLayout2, mIds
        ).run().expectClean()
    }

    fun testSuppressedSingleFile() {
        lint().files(mIgnorelayout1).run().expectClean()
    }

    fun testNewIdPrefix() {
        lint().files(
            xml(
                "res/layout/default_item_badges.xml",
                """

                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    android:id="@+id/video_badges"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content" />
                """
            ).indented(),
            xml(
                "res/layout/detailed_item.xml",
                """

                <RelativeLayout
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content" >

                    <FrameLayout
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_below="@id/video_badges" />

                </RelativeLayout>
                """
            ).indented()
        ).run().expect(
            """
            res/layout/detailed_item.xml:10: Error: @id/video_badges is not a sibling in the same RelativeLayout [NotSibling]
                    android:layout_below="@id/video_badges" />
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        )
    }

    fun testSiblings() {
        lint().files(
            xml(
                "res/layout/siblings.xml",
                """

                <!--
                  ~ Copyright (C) 2013 The Android Open Source Project
                  ~
                  ~ Licensed under the Apache License, Version 2.0 (the "License");
                  ~ you may not use this file except in compliance with the License.
                  ~ You may obtain a copy of the License at
                  ~
                  ~      http://www.apache.org/licenses/LICENSE-2.0
                  ~
                  ~ Unless required by applicable law or agreed to in writing, software
                  ~ distributed under the License is distributed on an "AS IS" BASIS,
                  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
                  ~ See the License for the specific language governing permissions and
                  ~ limitations under the License.
                  -->
                <RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent" >

                    <Button
                        android:id="@+id/button4"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_alignParentLeft="true"
                        android:layout_alignParentTop="true"
                        android:text="Button" />

                    <LinearLayout
                        android:id="@+id/linearLayout1"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_below="@+id/button4"
                        android:layout_toRightOf="@+id/button4"
                        android:orientation="vertical" >

                        <Button
                            android:id="@+id/button5"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Button" />

                        <Button
                            android:id="@id/button6"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Button" />
                    </LinearLayout>

                    <Button
                        android:id="@+id/button7"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_alignTop="@id/button5"
                        android:layout_toRightOf="@id/button6"
                        android:text="Button" />

                    <Button
                        android:id="@+id/button8"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_alignTop="@+id/button5"
                        android:layout_toRightOf="@+id/button6"
                        android:text="Button" />

                </RelativeLayout>
                """
            ).indented()
        ).run().expect(
            """
            res/layout/siblings.xml:55: Error: @id/button5 is not a sibling in the same RelativeLayout [NotSibling]
                    android:layout_alignTop="@id/button5"
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            res/layout/siblings.xml:56: Error: @id/button6 is not a sibling in the same RelativeLayout [NotSibling]
                    android:layout_toRightOf="@id/button6"
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            res/layout/siblings.xml:63: Error: @+id/button5 is not a sibling in the same RelativeLayout [NotSibling]
                    android:layout_alignTop="@+id/button5"
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            res/layout/siblings.xml:64: Error: @+id/button6 is not a sibling in the same RelativeLayout [NotSibling]
                    android:layout_toRightOf="@+id/button6"
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            4 errors, 0 warnings
            """
        )
    }

    fun testSiblingsInConstraintLayout() {
        lint().files(
            xml(
                "res/layout/constraint.xml",
                """

                <android.support.constraint.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:app="http://schemas.android.com/apk/res-auto"
                    android:layout_width="800dp" android:layout_height="1143dp"
                    android:id="@+id/com.google.tnt.sherpa.ConstraintLayout">


                    <Button
                        android:text="Button"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        app:layout_constraintRight_toRightOf="@+id/button5"
                        android:id="@+id/button1" />

                    <LinearLayout
                        android:id="@+id/linearLayout1"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:orientation="vertical" >

                        <Button
                            android:id="@+id/button5"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Button" />
                    </LinearLayout>

                </android.support.constraint.ConstraintLayout>
                """
            ).indented()
        ).run().expect(
            """
            res/layout/constraint.xml:12: Error: @+id/button5 is not a sibling in the same ConstraintLayout [NotSibling]
                    app:layout_constraintRight_toRightOf="@+id/button5"
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        )
    }

    fun testInvalidIds1() {
        // See https://code.google.com/p/android/issues/detail?id=56029
        lint().files(
            xml(
                "res/layout/invalid_ids.xml",
                """

                <!--
                  ~ Copyright (C) 2013 The Android Open Source Project
                  ~
                  ~ Licensed under the Apache License, Version 2.0 (the "License");
                  ~ you may not use this file except in compliance with the License.
                  ~ You may obtain a copy of the License at
                  ~
                  ~      http://www.apache.org/licenses/LICENSE-2.0
                  ~
                  ~ Unless required by applicable law or agreed to in writing, software
                  ~ distributed under the License is distributed on an "AS IS" BASIS,
                  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
                  ~ See the License for the specific language governing permissions and
                  ~ limitations under the License.
                  -->
                <RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent" >

                    <Button
                        android:id="@+menu/Reload"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_alignParentLeft="true"
                        android:layout_alignParentTop="true"
                        android:text="Button" />

                    <LinearLayout
                        android:id="@+/id_foo"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:orientation="vertical" >

                        <Button
                            android:id="@+myid/button5"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Button" />

                        <Button
                            android:id="@+string/whatevs"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Button" />
                    </LinearLayout>

                </RelativeLayout>
                """
            ).indented()
        ).run().expect(
            """
            res/layout/invalid_ids.xml:23: Error: ID definitions must be of the form @+id/name; try using @+id/menu_Reload [InvalidId]
                    android:id="@+menu/Reload"
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~
            res/layout/invalid_ids.xml:31: Error: ID definitions must be of the form @+id/name; try using @+id/_id_foo [InvalidId]
                    android:id="@+/id_foo"
                    ~~~~~~~~~~~~~~~~~~~~~~
            res/layout/invalid_ids.xml:37: Error: ID definitions must be of the form @+id/name; try using @+id/myid_button5 [InvalidId]
                        android:id="@+myid/button5"
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~
            res/layout/invalid_ids.xml:43: Error: ID definitions must be of the form @+id/name; try using @+id/string_whatevs [InvalidId]
                        android:id="@+string/whatevs"
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            4 errors, 0 warnings
            """
        )
    }

    fun testInvalidIds2() {
        // https://code.google.com/p/android/issues/detail?id=65244
        val expected =
            """
            res/layout/invalid_ids2.xml:8: Error: ID definitions must be of the form @+id/name; try using @+id/btn_skip [InvalidId]
                    android:id="@+id/btn/skip"
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~
            res/layout/invalid_ids2.xml:16: Error: Invalid id: missing value [InvalidId]
                    android:id="@+id/"
                    ~~~~~~~~~~~~~~~~~~
            2 errors, 0 warnings
            """
        lint().files(
            xml(
                "res/layout/invalid_ids2.xml",
                """

                <RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent" >

                    <Button
                        android:id="@+id/btn/skip"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_alignParentLeft="true"
                        android:layout_alignParentTop="true"
                        android:text="Button" />

                    <Button
                        android:id="@+id/"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Button" />

                </RelativeLayout>
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testIncremental() {
        lint().files(
            mLayout1, mLayout2, mIds
        )
            .incremental("res/layout/layout1.xml")
            .run().expect(
                """
                res/layout/layout1.xml:14: Error: The id "button5" is not defined anywhere. Did you mean one of {button1, button2, button3, button4} ? [UnknownId]
                        android:layout_alignBottom="@+id/button5"
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                res/layout/layout1.xml:17: Error: The id "my_id3" is not defined anywhere. Did you mean one of {my_id0, my_id1, my_id2} ? [UnknownId]
                        android:layout_alignRight="@+id/my_id3"
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                res/layout/layout1.xml:18: Error: The id "my_id1" is defined but not assigned to any views. Did you mean one of {my_id0, my_id2, my_id3} ? [UnknownId]
                        android:layout_alignTop="@id/my_id1"
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                res/layout/layout1.xml:15: Warning: The id "my_id2" is not referring to any views in this layout [UnknownIdInLayout]
                        android:layout_alignLeft="@+id/my_id2"
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                3 errors, 1 warnings
                """
            )
    }

    fun testMissingNamespace() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=227687
        // Make sure we properly handle a missing namespace
        lint().files(
            xml(
                "res/layout/layout3.xml",
                """
                <RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools" android:layout_width="match_parent"
                    android:layout_height="match_parent" android:id="@+id/tv_portfolio_title">

                    <TextView

                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        layout_below="@+id/tv_portfolio_title"/>
                </RelativeLayout>
            """
            ).indented()
        ).run().expectClean()
    }

    fun testSelfReference() {
        // Make sure we highlight direct references to self
        // Regression test for https://code.google.com/p/android/issues/detail?id=136103
        lint().files(
            xml(
                "res/layout/layout3.xml",
                """
                <RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools" android:layout_width="match_parent"
                    android:layout_height="match_parent" android:paddingLeft="@dimen/activity_horizontal_margin">

                    <TextView
                        android:id="@+id/tv_portfolio_title"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_below="@+id/tv_portfolio_title"/>
                </RelativeLayout>
                """
            ).indented()
        ).run().expect(
            """
            res/layout/layout3.xml:9: Error: Cannot be relative to self: id=tv_portfolio_title, layout_below=tv_portfolio_title [NotSibling]
                    android:layout_below="@+id/tv_portfolio_title"/>
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        )
    }

    fun testPercent() {
        lint().files(
            xml(
                "res/layout/test.xml",
                """
                <android.support.percent.PercentRelativeLayout      xmlns:android="http://schemas.android.com/apk/res/android"     xmlns:app="http://schemas.android.com/apk/res-auto"
                     android:layout_width="match_parent"
                     android:layout_height="match_parent">
                        <View
                            android:id="@+id/textview1"
                            android:layout_gravity="center"
                            app:layout_widthPercent="50%"
                            app:layout_heightPercent="50%"/>
                        <View
                            android:id="@+id/textview2"
                            android:layout_below="@id/textview1"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            app:layout_marginStartPercent="25%"
                            app:layout_marginEndPercent="25%"/>
                        <View
                            android:layout_height="wrap_content"
                            android:layout_below="@id/textView1"
                            app:layout_widthPercent="60%"/>

                </android.support.percent.PercentRelativeLayout>
                """
            ).indented()
        ).run().expect(
            """
            res/layout/test.xml:18: Error: The id "textView1" is not defined anywhere. Did you mean textview1 ? [UnknownId]
                        android:layout_below="@id/textView1"
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        )
    }

    fun testConstraintLayoutCycle() {
        lint().files(
            xml(
                "res/layout/constraint.xml",
                """

                <android.support.constraint.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:app="http://schemas.android.com/apk/res-auto"
                    android:layout_width="800dp" android:layout_height="1143dp"
                    android:id="@+id/com.google.tnt.sherpa.ConstraintLayout">


                    <Button
                        android:text="Button"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        app:layout_constraintRight_toRightOf="@+id/button4"
                        app:layout_editor_absoluteX="24dp"
                        app:layout_editor_absoluteY="26dp"
                        android:id="@+id/button4" />

                    <Button
                        android:text="Button"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        app:layout_constraintRight_toLeftOf="@+id/typo"
                        app:layout_editor_absoluteX="150dp"
                        app:layout_editor_absoluteY="94dp"
                        android:id="@+id/button5" />
                </android.support.constraint.ConstraintLayout>
                """
            ).indented()
        ).run().expect(
            """
            res/layout/constraint.xml:21: Error: The id "typo" is not defined anywhere. [UnknownId]
                    app:layout_constraintRight_toLeftOf="@+id/typo"
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            res/layout/constraint.xml:12: Error: Cannot be relative to self: id=button4, layout_constraintRight_toRightOf=button4 [NotSibling]
                    app:layout_constraintRight_toRightOf="@+id/button4"
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            2 errors, 0 warnings
            """
        )
    }

    fun testConstraintReferencedIds() {
        // Validate id lists in <Barrier> elements
        //   text1 is a sibling defined in the current layout
        //   text2 is not defined anywhere
        //   text3 is not a sibling but defined in the current layout
        //   my_id1 is a sibling but defined in a values file
        //   my_id0 is not a sibling and defined in a values file
        lint().files(
            mIds,
            xml(
                "res/layout/layout3.xml",
                """

                <android.support.constraint.ConstraintLayout     xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:app="http://schemas.android.com/apk/res-auto"
                    xmlns:tools="http://schemas.android.com/tools"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent">

                    <android.support.constraint.Barrier
                        android:id="@+id/barrier"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        app:barrierDirection="end"
                        app:constraint_referenced_ids="text1,text2,text3,my_id0,my_id1" />

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Hello World!"
                        android:id="@+id/text1"
                        app:layout_constraintBottom_toBottomOf="parent"
                        app:layout_constraintLeft_toLeftOf="parent"
                        app:layout_constraintRight_toRightOf="parent"
                        app:layout_constraintTop_toTopOf="parent" />

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Happy New Year!"
                        android:id="@id/my_id1"
                        app:layout_constraintBottom_toBottomOf="parent"
                        app:layout_constraintLeft_toLeftOf="parent"
                        app:layout_constraintRight_toRightOf="parent"
                        app:layout_constraintTop_toTopOf="parent" />

                    <LinearLayout
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        app:layout_constraintBottom_toBottomOf="parent"
                        app:layout_constraintLeft_toLeftOf="parent"
                        app:layout_constraintRight_toRightOf="parent"
                        app:layout_constraintTop_toTopOf="parent">

                        <TextView
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Happy New Year!"
                            android:id="@+id/text3" />

                        <TextView
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Happy New Year!"
                            android:id="@id/my_id0" />
                    </LinearLayout>
                </android.support.constraint.ConstraintLayout>
                """
            ).indented()
        ).run().expect(
            """
            res/layout/layout3.xml:13: Error: The id "text2" is not defined anywhere. Did you mean one of {text1, text3} ? [UnknownId]
                    app:constraint_referenced_ids="text1,text2,text3,my_id0,my_id1" />
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            res/layout/layout3.xml:13: Error: my_id0 is not a sibling in the same ConstraintLayout [NotSibling]
                    app:constraint_referenced_ids="text1,text2,text3,my_id0,my_id1" />
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            res/layout/layout3.xml:13: Error: text3 is not a sibling in the same ConstraintLayout [NotSibling]
                    app:constraint_referenced_ids="text1,text2,text3,my_id0,my_id1" />
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            3 errors, 0 warnings
            """
        )
    }

    fun testConstraintReferencedIdsMissingValuesFile() {
        // Validate id lists in <Barrier> elements
        lint().files(
            xml(
                "res/layout/layout3.xml",
                """

                <android.support.constraint.ConstraintLayout     xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:app="http://schemas.android.com/apk/res-auto"
                    xmlns:tools="http://schemas.android.com/tools"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent">

                    <android.support.constraint.Barrier
                        android:id="@+id/barrier"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        app:barrierDirection="end"
                        app:constraint_referenced_ids="text1,text2,text3,my_id0,my_id1" />

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Hello World!"
                        android:id="@+id/text1"
                        app:layout_constraintBottom_toBottomOf="parent"
                        app:layout_constraintLeft_toLeftOf="parent"
                        app:layout_constraintRight_toRightOf="parent"
                        app:layout_constraintTop_toTopOf="parent" />

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Happy New Year!"
                        android:id="@id/my_id1"
                        app:layout_constraintBottom_toBottomOf="parent"
                        app:layout_constraintLeft_toLeftOf="parent"
                        app:layout_constraintRight_toRightOf="parent"
                        app:layout_constraintTop_toTopOf="parent" />

                    <LinearLayout
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        app:layout_constraintBottom_toBottomOf="parent"
                        app:layout_constraintLeft_toLeftOf="parent"
                        app:layout_constraintRight_toRightOf="parent"
                        app:layout_constraintTop_toTopOf="parent">

                        <TextView
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Happy New Year!"
                            android:id="@+id/text3" />

                        <TextView
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Happy New Year!"
                            android:id="@id/my_id0" />
                    </LinearLayout>
                </android.support.constraint.ConstraintLayout>
                """
            ).indented()
        ).run().expect(
            """
            res/layout/layout3.xml:13: Error: The id "my_id0" is not defined anywhere. [UnknownId]
                    app:constraint_referenced_ids="text1,text2,text3,my_id0,my_id1" />
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            res/layout/layout3.xml:13: Error: The id "my_id1" is not defined anywhere. [UnknownId]
                    app:constraint_referenced_ids="text1,text2,text3,my_id0,my_id1" />
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            res/layout/layout3.xml:13: Error: The id "text2" is not defined anywhere. Did you mean one of {text1, text3} ? [UnknownId]
                    app:constraint_referenced_ids="text1,text2,text3,my_id0,my_id1" />
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            res/layout/layout3.xml:13: Error: text3 is not a sibling in the same ConstraintLayout [NotSibling]
                    app:constraint_referenced_ids="text1,text2,text3,my_id0,my_id1" />
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            4 errors, 0 warnings
            """
        )
    }

    fun testConstraintReferencedIdsSingleFile() {
        // Validate id lists in <Barrier> elements
        val expected =
            """
                res/layout/layout3.xml:13: Error: my_id0 is not a sibling in the same ConstraintLayout [NotSibling]
                        app:constraint_referenced_ids="text1,text2,text3,my_id0,my_id1" />
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                res/layout/layout3.xml:13: Error: text2 is not a sibling in the same ConstraintLayout [NotSibling]
                        app:constraint_referenced_ids="text1,text2,text3,my_id0,my_id1" />
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                res/layout/layout3.xml:13: Error: text3 is not a sibling in the same ConstraintLayout [NotSibling]
                        app:constraint_referenced_ids="text1,text2,text3,my_id0,my_id1" />
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                3 errors, 0 warnings
                """
        lint().files(
            xml(
                "res/layout/layout3.xml",
                """
                <android.support.constraint.ConstraintLayout
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:app="http://schemas.android.com/apk/res-auto"
                    xmlns:tools="http://schemas.android.com/tools"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent">

                    <android.support.constraint.Barrier
                        android:id="@+id/barrier"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        app:barrierDirection="end"
                        app:constraint_referenced_ids="text1,text2,text3,my_id0,my_id1" />

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Hello World!"
                        android:id="@+id/text1"
                        app:layout_constraintBottom_toBottomOf="parent"
                        app:layout_constraintLeft_toLeftOf="parent"
                        app:layout_constraintRight_toRightOf="parent"
                        app:layout_constraintTop_toTopOf="parent" />

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Happy New Year!"
                        android:id="@id/my_id1"
                        app:layout_constraintBottom_toBottomOf="parent"
                        app:layout_constraintLeft_toLeftOf="parent"
                        app:layout_constraintRight_toRightOf="parent"
                        app:layout_constraintTop_toTopOf="parent" />

                    <LinearLayout
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        app:layout_constraintBottom_toBottomOf="parent"
                        app:layout_constraintLeft_toLeftOf="parent"
                        app:layout_constraintRight_toRightOf="parent"
                        app:layout_constraintTop_toTopOf="parent">

                        <TextView
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Happy New Year!"
                            android:id="@+id/text3" />

                        <TextView
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Happy New Year!"
                            android:id="@id/my_id0" />
                    </LinearLayout>
                </android.support.constraint.ConstraintLayout>
                """
            ).indented()
        )
            .incremental("res/layout/layout3.xml")
            .supportResourceRepository(false)
            .run().expect(expected)
    }

    fun testIncludes() {
        lint().files(
            xml(
                "res/layout/layout4.xml",
                """
                <RelativeLayout
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:app="http://schemas.android.com/apk/res-auto"
                    xmlns:tools="http://schemas.android.com/tools"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent">

                    <View
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Hello World!"
                        android:id="@+id/text1"
                        android:layout_alignBottom="@+id/id1"
                        android:layout_alignLeft="@+id/id2"
                    />
                    <include layout="@layout/included1"/>
                    <include layout="@layout/included2"/>
                </RelativeLayout>
                """
            ).indented(),
            xml(
                "res/layout/included1.xml",
                """
                <LinearLayout
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:app="http://schemas.android.com/apk/res-auto"
                    xmlns:tools="http://schemas.android.com/tools"
                    android:id="@+id/id1"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"/>
                """
            ).indented(),
            xml(
                "res/layout/included2.xml",
                """
                <merge
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:app="http://schemas.android.com/apk/res-auto"
                    xmlns:tools="http://schemas.android.com/tools"
                    android:id="@+id/my_merge"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent">
                    <View
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Hello World!"
                        android:id="@+id/id2"
                    />
                </merge>
                """
            ).indented()
        )
            .incremental("res/layout/layout4.xml").run().expectClean()
    }

    fun testUnknownIncludes() {
        lint().files(
            xml(
                "res/layout/layout4.xml",
                """
                <RelativeLayout
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:app="http://schemas.android.com/apk/res-auto"
                    xmlns:tools="http://schemas.android.com/tools"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent">

                    <View
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Hello World!"
                        android:id="@+id/text1"
                        android:layout_alignBottom="@+id/id1"
                        android:layout_alignLeft="@+id/id2"
                    />
                    <include layout="@layout/included1"/>
                    <include layout="@layout/included2"/>
                </RelativeLayout>
                """
            ).indented()
        ).run().expectClean()
    }

    // Sample code
    private val mIds = xml(
        "res/values/ids.xml",
        """

            <resources>

                <item name="my_id0" type="id"/>
                <item name="my_id1" type="id"/>

            </resources>
            """
    ).indented()

    // Sample code
    private val mIgnorelayout1 = xml(
        "res/layout/layout1.xml",
        """

            <RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:tools="http://schemas.android.com/tools"
                android:id="@+id/RelativeLayout1"
                android:layout_width="fill_parent"
                android:layout_height="fill_parent"
                android:orientation="vertical" >

                <!-- my_id1 is defined in ids.xml, my_id2 is defined in main2, my_id3 does not exist -->

                <Button
                    android:id="@+id/button1"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_alignBottom="@+id/button5"
                    android:layout_alignLeft="@+id/my_id2"
                    android:layout_alignParentTop="true"
                    android:layout_alignRight="@+id/my_id3"
                    android:layout_alignTop="@id/my_id1"
                    android:text="Button"
                    tools:ignore="UnknownIdInLayout,UnknownId,NotSibling" />

                <Button
                    android:id="@+id/button2"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_alignParentLeft="true"
                    android:layout_below="@+id/button1"
                    android:text="Button" />

                <Button
                    android:id="@+id/button3"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_alignParentLeft="true"
                    android:layout_below="@+id/button2"
                    android:text="Button" />

                <Button
                    android:id="@+id/button4"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_alignParentLeft="true"
                    android:layout_below="@+id/button3"
                    android:text="Button" />

            </RelativeLayout>
            """
    ).indented()

    // Sample code
    private val mLayout1 = xml(
        "res/layout/layout1.xml",
        """

        <RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
            android:id="@+id/RelativeLayout1"
            android:layout_width="fill_parent"
            android:layout_height="fill_parent"
            android:orientation="vertical" >

            <!-- my_id1 is defined in ids.xml, my_id2 is defined in main2, my_id3 does not exist -->

            <Button
                android:id="@+id/button1"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_alignBottom="@+id/button5"
                android:layout_alignLeft="@+id/my_id2"
                android:layout_alignParentTop="true"
                android:layout_alignRight="@+id/my_id3"
                android:layout_alignTop="@id/my_id1"
                android:text="Button" />

            <Button
                android:id="@+id/button2"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_alignParentLeft="true"
                android:layout_below="@+id/button1"
                android:text="Button" />

            <Button
                android:id="@+id/button3"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_alignParentLeft="true"
                android:layout_below="@+id/button2"
                android:text="Button" />

            <Button
                android:id="@+id/button4"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_alignParentLeft="true"
                android:layout_below="@+id/button3"
                android:text="Button" />

        </RelativeLayout>
        """
    ).indented()

    // Sample code
    private val mLayout2 = xml(
        "res/layout/layout2.xml",
        """
        <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:orientation="vertical" >

            <Button
                android:id="@+id/my_id2"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="Button" />

        </LinearLayout>
        """
    ).indented()
}
