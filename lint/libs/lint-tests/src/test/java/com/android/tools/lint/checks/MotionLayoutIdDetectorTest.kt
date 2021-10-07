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

class MotionLayoutIdDetectorTest : AbstractCheckTest() {
    override fun getDetector() = MotionLayoutIdDetector()

    /** User has id inside motion layout thus does not trigger lint. */
    fun testHasId() {
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

                    <View
                        android:id="@+id/test"
                        android:layout_width="64dp"
                        android:layout_height="64dp"
                        android:background="@color/black"
                        android:text="Button" />

                </android.support.constraint.motion.MotionLayout>
                """
            ).indented()
        ).run().expectClean()
    }

    /** User has  a view without id inside motion layout thus triggers lint. */
    fun testDocumentationExample() {
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

                    <View
                        android:layout_width="64dp"
                        android:layout_height="64dp"
                        android:background="@color/black"
                        android:text="Button" />

                </android.support.constraint.motion.MotionLayout>
                """
            ).indented()
        ).run().expect("""
            res/layout/motion_test.xml:9: Error: Views inside MotionLayout requires an id [MotionLayoutMissingId]
                <View
                 ~~~~
            1 errors, 0 warnings
        """.trimIndent())
    }

    /** User has  a view without id inside motion layout thus triggers lint. */
    fun testViewInheritorMissingId() {
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

                    <View
                        android:id="@+id/id1"
                        android:layout_width="64dp"
                        android:layout_height="64dp"
                        android:background="@color/black"
                        android:text="Button" />

                    <View
                        android:id="@+id/id2"
                        android:layout_width="64dp"
                        android:layout_height="64dp"
                        android:background="@color/black"
                        android:text="Button" />

                    <TextView
                        android:layout_width="64dp"
                        android:layout_height="64dp"
                        android:background="@color/black"
                        android:text="Button" />

                </android.support.constraint.motion.MotionLayout>
                """
            ).indented()
        ).run().expect("""
            res/layout/motion_test.xml:23: Error: Views inside MotionLayout requires an id [MotionLayoutMissingId]
                <TextView
                 ~~~~~~~~
            1 errors, 0 warnings
        """.trimIndent())
    }

    /** Lint does not check element that's not a view. */
    fun testSkipsNonView() {
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

                    <include
                        android:layout="@layout/test" />

                    <tag></tag>
                    <merge></merge>
                    <requestFocus></requestFocus>
                    <data></data>
                    <layout></layout>
                    <variable></variable>
                    <import></import>
                    <madeUpTag></madeUpTag>

                </android.support.constraint.motion.MotionLayout>
                """
            ).indented()
        ).run().expectClean()
    }
}
