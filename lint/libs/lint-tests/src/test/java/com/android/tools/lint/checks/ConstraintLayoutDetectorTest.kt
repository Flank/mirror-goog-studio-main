/*
 * Copyright (C) 2016 - 2018 The Android Open Source Project
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

import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Severity
import junit.framework.TestCase

class ConstraintLayoutDetectorTest : AbstractCheckTest() {
    fun testMissingConstraints() {
        lint().files(
            xml(
                "res/layout/layout1.xml",
                """
                    <android.support.constraint.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
                        xmlns:app="http://schemas.android.com/apk/res-auto"
                        xmlns:tools="http://schemas.android.com/tools"
                        android:id="@+id/activity_main"
                        android:layout_width="match_parent"
                        android:layout_height="match_parent"
                        tools:layout_editor_absoluteX="0dp"
                        tools:layout_editor_absoluteY="81dp"
                        tools:context="com.example.tnorbye.myapplication.MainActivity"
                        tools:ignore="HardcodedText">
                        <TextView
                            android:id="@+id/textView"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Not constrained and no designtime positions" />
                        <TextView
                            android:id="@+id/textView2"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Not constrained"
                            tools:layout_editor_absoluteX="21dp"
                            tools:layout_editor_absoluteY="23dp" />
                        <TextView
                            android:id="@+id/textView3"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Constrained both"
                            app:layout_constraintBottom_creator="2"
                            app:layout_constraintBottom_toBottomOf="@+id/activity_main"
                            app:layout_constraintLeft_creator="2"
                            app:layout_constraintLeft_toLeftOf="@+id/activity_main"
                            app:layout_constraintRight_creator="2"
                            app:layout_constraintRight_toRightOf="@+id/activity_main"
                            app:layout_constraintTop_creator="2"
                            app:layout_constraintTop_toTopOf="@+id/activity_main"
                            tools:layout_editor_absoluteX="139dp"
                            tools:layout_editor_absoluteY="247dp" />
                        <TextView
                            android:id="@+id/textView4"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Constrained Horizontally"
                            app:layout_constraintLeft_creator="0"
                            app:layout_constraintLeft_toLeftOf="@+id/textView3"
                            tools:layout_editor_absoluteX="139dp"
                            tools:layout_editor_absoluteY="270dp" />
                        <TextView
                            android:id="@+id/textView5"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Constrained Vertically"
                            app:layout_constraintBaseline_creator="2"
                            app:layout_constraintBaseline_toBaselineOf="@+id/textView4"
                            tools:layout_editor_absoluteX="306dp"
                            tools:layout_editor_absoluteY="270dp" />
                        <android.support.constraint.Guideline
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:id="@+id/android.support.constraint.Guideline"
                            app:orientation="vertical"
                            tools:layout_editor_absoluteX="20dp"
                            tools:layout_editor_absoluteY="0dp"
                            app:relativeBegin="20dp" />
                        <requestFocus/>
                    </android.support.constraint.ConstraintLayout>
                """
            ).indented()
        )
            .checkMessage { context, issue, severity, location, message, fixData ->
                this.checkReportedError(context, issue, severity, location, message, fixData)
            }
            .run()
            .expect(
                """
                res/layout/layout1.xml:11: Error: This view is not constrained. It only has designtime positions, so it will jump to (0,0) at runtime unless you add the constraints [MissingConstraints]
                    <TextView
                     ~~~~~~~~
                res/layout/layout1.xml:16: Error: This view is not constrained. It only has designtime positions, so it will jump to (0,0) at runtime unless you add the constraints [MissingConstraints]
                    <TextView
                     ~~~~~~~~
                res/layout/layout1.xml:38: Error: This view is not constrained vertically: at runtime it will jump to the top unless you add a vertical constraint [MissingConstraints]
                    <TextView
                     ~~~~~~~~
                res/layout/layout1.xml:47: Error: This view is not constrained horizontally: at runtime it will jump to the left unless you add a horizontal constraint [MissingConstraints]
                    <TextView
                     ~~~~~~~~
                4 errors, 0 warnings
                """
            )
    }

    fun testBarrierMissingConstraint() {
        lint().files(
            xml(
                "res/layout/layout1.xml",
                """
                    <android.support.constraint.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
                        xmlns:app="http://schemas.android.com/apk/res-auto"
                        xmlns:tools="http://schemas.android.com/tools"
                        android:id="@+id/activity_main_barriers"
                        android:layout_width="match_parent"
                        android:layout_height="match_parent"
                        app:layout_editor_absoluteX="0dp"
                        app:layout_editor_absoluteY="80dp"
                        tools:layout_editor_absoluteX="0dp"
                        tools:layout_editor_absoluteY="80dp">
                        <TextView
                            android:id="@+id/settingsLabel"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:layout_marginBottom="254dp"
                            android:layout_marginTop="31dp"
                            android:labelFor="@+id/settings"
                            android:text="@string/settings"
                            app:layout_constraintBaseline_creator="1"
                            app:layout_constraintBottom_toBottomOf="parent"
                            app:layout_constraintLeft_creator="1"
                            app:layout_constraintLeft_toLeftOf="@+id/activity_main_barriers"
                            app:layout_constraintTop_toBottomOf="@+id/cameraLabel"
                            app:layout_constraintVertical_bias="0.65999997"
                            app:layout_editor_absoluteX="16dp"
                            app:layout_editor_absoluteY="238dp"
                            tools:layout_constraintBaseline_creator="0"
                            tools:layout_constraintLeft_creator="0" />
                        <TextView
                            android:id="@+id/cameraLabel"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:layout_marginBottom="31dp"
                            android:layout_marginTop="189dp"
                            android:labelFor="@+id/cameraType"
                            android:text="@string/camera"
                            app:layout_constraintBaseline_creator="1"
                            app:layout_constraintBottom_toTopOf="@+id/settingsLabel"
                            app:layout_constraintLeft_creator="1"
                            app:layout_constraintStart_toStartOf="parent"
                            app:layout_constraintTop_toTopOf="parent"
                            app:layout_editor_absoluteX="16dp"
                            app:layout_editor_absoluteY="189dp"
                            tools:layout_constraintBaseline_creator="0"
                            tools:layout_constraintLeft_creator="0" />
                        <android.support.constraint.Barrier
                            android:id="@+id/barrier2"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            app:constraint_referenced_ids="settingsLabel,cameraLabel"
                            tools:layout_editor_absoluteX="99dp" />
                    </android.support.constraint.ConstraintLayout>
                    """
            ).indented()
        )
            .checkMessage { context, issue, severity, location, message, fixData ->
                this.checkReportedError(context, issue, severity, location, message, fixData)
            }
            .run()
            .expect(
                """
                res/layout/layout1.xml:46: Error: This view is not constrained. It only has designtime positions, so it will jump to (0,0) at runtime unless you add the constraints [MissingConstraints]
                    <android.support.constraint.Barrier
                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """
            )
    }

    fun testBarrierHasConstraint() {
        lint().files(
            xml(
                "res/layout/layout1.xml",
                """
                    <android.support.constraint.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
                        xmlns:app="http://schemas.android.com/apk/res-auto"
                        xmlns:tools="http://schemas.android.com/tools"
                        android:id="@+id/activity_main_barriers"
                        android:layout_width="match_parent"
                        android:layout_height="match_parent"
                        app:layout_editor_absoluteX="0dp"
                        app:layout_editor_absoluteY="80dp"
                        tools:layout_editor_absoluteX="0dp"
                        tools:layout_editor_absoluteY="80dp">
                        <TextView
                            android:id="@+id/settingsLabel"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:layout_marginBottom="254dp"
                            android:layout_marginTop="31dp"
                            android:labelFor="@+id/settings"
                            android:text="@string/settings"
                            app:layout_constraintBaseline_creator="1"
                            app:layout_constraintBottom_toBottomOf="parent"
                            app:layout_constraintLeft_creator="1"
                            app:layout_constraintLeft_toLeftOf="@+id/activity_main_barriers"
                            app:layout_constraintTop_toBottomOf="@+id/cameraLabel"
                            app:layout_constraintVertical_bias="0.65999997"
                            app:layout_editor_absoluteX="16dp"
                            app:layout_editor_absoluteY="238dp"
                            tools:layout_constraintBaseline_creator="0"
                            tools:layout_constraintLeft_creator="0" />
                        <TextView
                            android:id="@+id/cameraLabel"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:layout_marginBottom="31dp"
                            android:layout_marginTop="189dp"
                            android:labelFor="@+id/cameraType"
                            android:text="@string/camera"
                            app:layout_constraintBaseline_creator="1"
                            app:layout_constraintBottom_toTopOf="@+id/settingsLabel"
                            app:layout_constraintLeft_creator="1"
                            app:layout_constraintStart_toStartOf="parent"
                            app:layout_constraintTop_toTopOf="parent"
                            app:layout_editor_absoluteX="16dp"
                            app:layout_editor_absoluteY="189dp"
                            tools:layout_constraintBaseline_creator="0"
                            tools:layout_constraintLeft_creator="0" />
                        <android.support.constraint.Barrier
                            android:id="@+id/barrier2"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            app:barrierDirection="end"
                            app:constraint_referenced_ids="settingsLabel,cameraLabel"
                            tools:layout_editor_absoluteX="99dp" />
                    </android.support.constraint.ConstraintLayout>
                    """
            ).indented()
        )
            .checkMessage { context, issue, severity, location, message, fixData ->
                this.checkReportedError(context, issue, severity, location, message, fixData)
            }
            .run()
            .expectClean()
    }

    fun testWidthHeightMatchParent() {
        lint().files(
            xml(
                "res/layout/layout1.xml",
                """
                    <android.support.constraint.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"   xmlns:app="http://schemas.android.com/apk/res-auto"   xmlns:tools="http://schemas.android.com/tools"   android:layout_width="match_parent"   android:layout_height="match_parent">
                        <Button
                            android:id="@+id/button"
                            android:layout_width="match_parent"
                            android:layout_height="match_parent"
                     />
                    </android.support.constraint.ConstraintLayout>
                    """
            ).indented()
        )
            .checkMessage { context, issue, severity, location, message, fixData ->
                this.checkReportedError(context, issue, severity, location, message, fixData)
            }
            .run()
            .expectClean()
    }

    fun testWidthMatchParentOnlyError() {
        lint().files(
            xml(
                "res/layout/layout1.xml",
                """
                    <android.support.constraint.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"   xmlns:app="http://schemas.android.com/apk/res-auto"   xmlns:tools="http://schemas.android.com/tools"   android:layout_width="match_parent"   android:layout_height="match_parent">
                        <Button
                            android:id="@+id/button"
                            android:layout_width="match_parent"
                            android:layout_height="100dp"
                     />
                    </android.support.constraint.ConstraintLayout>
                    """
            ).indented()
        )
            .checkMessage { context, issue, severity, location, message, fixData ->
                this.checkReportedError(context, issue, severity, location, message, fixData)
            }
            .run()
            .expect(
                """
                res/layout/layout1.xml:2: Error: This view is not constrained vertically: at runtime it will jump to the top unless you add a vertical constraint [MissingConstraints]
                    <Button
                     ~~~~~~
                1 errors, 0 warnings
                """
            )
    }

    fun testHeightMatchParentOnlyError() {
        lint().files(
            xml(
                "res/layout/layout1.xml",
                """
                    <android.support.constraint.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"   xmlns:app="http://schemas.android.com/apk/res-auto"   xmlns:tools="http://schemas.android.com/tools"   android:layout_width="match_parent"   android:layout_height="match_parent">
                        <Button
                            android:id="@+id/button"
                            android:layout_width="100dp"
                            android:layout_height="match_parent"
                     />
                    </android.support.constraint.ConstraintLayout>
                    """
            ).indented()
        )
            .checkMessage { context, issue, severity, location, message, fixData ->
                this.checkReportedError(context, issue, severity, location, message, fixData)
            }
            .run()
            .expect(
                """
                res/layout/layout1.xml:2: Error: This view is not constrained horizontally: at runtime it will jump to the left unless you add a horizontal constraint [MissingConstraints]
                    <Button
                     ~~~~~~
                1 errors, 0 warnings
                """
            )
    }

    fun testWidthMatchParentHeightConstraint() {
        lint().files(
            xml(
                "res/layout/layout1.xml",
                """
                    <android.support.constraint.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"   xmlns:app="http://schemas.android.com/apk/res-auto"   xmlns:tools="http://schemas.android.com/tools"   android:layout_width="match_parent"   android:layout_height="match_parent">
                        <Button
                            android:id="@+id/button"
                            android:layout_width="match_parent"
                            android:layout_height="100dp"
                             app:layout_constraintTop_toTopOf="parent" />
                    </android.support.constraint.ConstraintLayout>
                    """
            ).indented()
        )
            .checkMessage { context, issue, severity, location, message, fixData ->
                this.checkReportedError(context, issue, severity, location, message, fixData)
            }
            .run()
            .expectClean()
    }

    fun testHeightMatchParentWidthConstraint() {
        lint().files(
            xml(
                "res/layout/layout1.xml",
                """
                    <android.support.constraint.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"   xmlns:app="http://schemas.android.com/apk/res-auto"   xmlns:tools="http://schemas.android.com/tools"   android:layout_width="match_parent"   android:layout_height="match_parent">
                        <Button
                            android:id="@+id/button"
                            android:layout_width="100dp"
                            android:layout_height="match_parent"
                             app:layout_constraintEnd_toEndOf="parent" />
                    </android.support.constraint.ConstraintLayout>
                    """
            ).indented()
        )
            .checkMessage { context, issue, severity, location, message, fixData ->
                this.checkReportedError(context, issue, severity, location, message, fixData)
            }
            .run()
            .expectClean()
    }

    fun testIncludesOkay() {
        // No regression test for https://issuetracker.google.com/117204543
        lint().files(
            xml(
                "res/layout/layout1.xml",
                """
                    <android.support.constraint.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"   xmlns:app="http://schemas.android.com/apk/res-auto"   xmlns:tools="http://schemas.android.com/tools"   android:layout_width="match_parent"   android:layout_height="match_parent">
                     <android.support.constraint.Group
                                    android:id="@+id/first_run_page_quickrestore"
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    android:visibility="gone"
                                    app:constraint_referenced_ids="first_run_page3_text1,first_run_page3_text2" />
                     <androidx.constraintlayout.widget.Group
                                    android:id="@+id/first_run_page_quickrestore2"
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    android:visibility="gone"
                                    app:constraint_referenced_ids="first_run_page3_text1,first_run_page3_text2" />
                    <include layout="@layout/include_remote_control" />
                    </android.support.constraint.ConstraintLayout>
                    """
            ).indented()
        )
            .checkMessage { context, issue, severity, location, message, fixData ->
                this.checkReportedError(context, issue, severity, location, message, fixData)
            }
            .run()
            .expectClean()
    }

    fun testAndroidxAndGroups() {
        // Regression test for
        // 118709915: ConstraintLayout Group highlighted as MissingConstraints
        lint().files(
            xml(
                "res/layout/layotu1.xml",
                """
                    <androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
                        xmlns:app="http://schemas.android.com/apk/res-auto"
                        xmlns:tools="http://schemas.android.com/tools"
                        android:layout_width="match_parent"
                        android:layout_height="match_parent"
                        tools:context=".MainActivity">
                        <TextView
                            android:id="@+id/textView1"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:layout_marginTop="19dp"
                            android:text="Hello World!"
                            app:layout_constraintBottom_toTopOf="@+id/textView2"
                            app:layout_constraintLeft_toLeftOf="parent"
                            app:layout_constraintRight_toRightOf="parent"
                            app:layout_constraintTop_toTopOf="parent"
                            app:layout_constraintVertical_chainStyle="packed" />
                        <androidx.constraintlayout.widget.Group
                            android:id="@+id/group"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:visibility="visible"
                            app:constraint_referenced_ids="textView1" />
                        <androidx.constraintlayout.widget.Barrier
                            android:id="@+id/barrier"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            app:barrierDirection="right"
                            app:constraint_referenced_ids="textView1,textView2" />
                        <TextView
                            android:id="@+id/textView2"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:layout_marginStart="8dp"
                            android:layout_marginLeft="8dp"
                            android:text="Bye World"
                            app:layout_constraintBottom_toBottomOf="parent"
                            app:layout_constraintStart_toStartOf="@+id/textView1"
                            app:layout_constraintTop_toBottomOf="@+id/textView1" />
                    </androidx.constraintlayout.widget.ConstraintLayout>
                    """
            )
        )
            .checkMessage { context, issue, severity, location, message, fixData ->
                this.checkReportedError(context, issue, severity, location, message, fixData)
            }
            .run()
            .expectClean()
    }

    fun testMotionLayoutExternalConstraints() {
        // Regression test for
        // https://issuetracker.google.com/151409564
        // In MotionLayout, constraints can be specified externally
        lint().files(
            xml(
                "res/layout/supplier_search_fragment.xml",
                """
                    <layout xmlns:android="http://schemas.android.com/apk/res/android"
                        xmlns:app="http://schemas.android.com/apk/res-auto"
                        xmlns:tools="http://schemas.android.com/tools">
                        <data>
                            <variable
                                name="viewBinding"
                                type="com.wayfair.waystation.supplier.search.SupplierSearchViewBinding" />
                        </data>
                        <androidx.constraintlayout.motion.widget.MotionLayout
                            android:id="@+id/motionLayout"
                            android:layout_width="match_parent"
                            android:layout_height="match_parent"
                            android:background="@color/white"
                            app:layoutDescription="@xml/supplier_search_scene"
                            app:transitionListener="@{viewBinding.motionTransitionListener}">
                            <androidx.recyclerview.widget.RecyclerView
                                android:id="@+id/recyclerView"
                                android:layout_width="match_parent"
                                android:layout_height="match_parent"
                                android:layout_marginTop="?attr/actionBarSize"
                                android:clipToPadding="false"
                                android:paddingTop="@dimen/small_spacing"
                                android:visibility="@{viewBinding.recyclerViewVisibility}"
                                app:bindableAdapter="@{viewBinding.adapter}"
                                app:data="@{viewBinding.suppliers}"
                                app:layoutManager="androidx.recyclerview.widget.LinearLayoutManager"
                                app:swipeListener="@{viewBinding.swipeListener}" />
                            <cww.animation.LoopingAnimationView
                                android:id="@+id/loadingIndicator"
                                style="@style/Widget.Animation.Loading"
                                android:layout_width="@dimen/widget_loading_indicator_width"
                                android:layout_height="@dimen/widget_loading_indicator_width"
                                android:alpha="@{viewBinding.loadingIndicatorVisibility}"
                                app:layout_constraintBottom_toBottomOf="parent"
                                app:layout_constraintEnd_toEndOf="parent"
                                app:layout_constraintStart_toStartOf="parent"
                                app:layout_constraintTop_toTopOf="parent" />
                            <FrameLayout
                                android:id="@+id/backgroundImageContainer"
                                android:layout_width="match_parent"
                                android:layout_height="wrap_content"
                                android:background="@drawable/supplier_search_gradient">
                                <ImageView
                                    android:id="@+id/backgroundImage"
                                    android:layout_width="wrap_content"
                                    android:layout_height="match_parent"
                                    android:layout_gravity="center_horizontal"
                                    android:adjustViewBounds="true"
                                    android:contentDescription="@string/supplier_search_welcome_text"
                                    android:paddingHorizontal="@dimen/base_spacing"
                                    android:scaleType="fitEnd"
                                    android:src="@drawable/supplier_search_background"
                                    app:onClickListener="@{viewBinding.debugOptionsClickListener}" />
                            </FrameLayout>
                            <androidx.appcompat.widget.Toolbar
                                android:id="@+id/toolbar"
                                style="@style/SupplierSearchToolbarStyle"
                                android:layout_width="match_parent"
                                android:layout_height="wrap_content"
                                android:alpha="0"
                                tools:ignore="Overdraw, RawDimen">
                            </androidx.appcompat.widget.Toolbar>
                        </androidx.constraintlayout.motion.widget.MotionLayout>
                    </layout>
                    """
            )
        )
            .checkMessage { context, issue, severity, location, message, fixData ->
                this.checkReportedError(context, issue, severity, location, message, fixData)
            }
            .run()
            .expectClean()
    }

    override fun checkReportedError(
        context: Context,
        issue: Issue,
        severity: Severity,
        location: Location,
        message: String,
        fixData: LintFix?
    ) {
        if (issue === GradleDetector.DEPENDENCY) {
            // Check for AndroidLintGradleDependencyInspection
            TestCase.assertTrue(fixData is LintFix.DataMap)
            val map = fixData as LintFix.DataMap?
            TestCase.assertNotNull(map!![ConstraintLayoutDetector::class.java])
        }
    }

    override fun getDetector(): Detector {
        return ConstraintLayoutDetector()
    }
}
