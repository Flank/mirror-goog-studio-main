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

class MergeRootFrameLayoutDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return MergeRootFrameLayoutDetector()
    }

    fun testMergeRefFromJava() {
        val expected = """
               res/layout/simple.xml:1: Warning: This <FrameLayout> can be replaced with a <merge> tag [MergeRootFrame]
               <FrameLayout
               ^
               0 errors, 1 warnings
               """
        lint().files(
            simple,
            java(
                """
                package test.pkg;

                import android.app.Activity;
                import android.os.Bundle;

                public class ImportFrameActivity extends Activity {
                    @Override
                    public void onCreate(Bundle savedInstanceState) {
                        super.onCreate(savedInstanceState);
                        setContentView(R.layout.simple);
                    }
                }
                """
            ).indented(),
            java(
                """
                package test.pkg;

                public final class R {
                    public static final class layout {
                        public static final int simple = 0x7f0a0000;
                    }
                }
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testMergeRefFromInclude() {
        val expected = """
               res/layout/simple.xml:1: Warning: This <FrameLayout> can be replaced with a <merge> tag [MergeRootFrame]
               <FrameLayout
               ^
               0 errors, 1 warnings
               """
        lint().files(simple, simpleInclude).run().expect(expected)
    }

    fun testMergeRefFromIncludeSuppressed() {
        lint().files(
            xml(
                "res/layout/simple.xml",
                """
                <FrameLayout
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    tools:ignore="MergeRootFrame" />
                """
            ).indented(),
            simpleInclude
        ).run().expectClean()
    }

    fun testNotIncluded() {
        lint().files(simple).run().expectClean()
    }

    fun testFitsSystemWindow() {
        lint().files(
            xml(
                "res/layout/simple.xml",
                """
                <FrameLayout
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:fitsSystemWindows="true"
                    android:keepScreenOn="true">
                    <View />
                </FrameLayout>
                """
            ).indented(),
            simpleInclude
        ).run().expectClean()
    }

    fun testFitsSystemWindowViaTheme() {
        lint().files(
            xml(
                "res/layout/simple.xml",
                """
                <FrameLayout
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    style="@style/Widget.MaterialComponents.NavigationView">
                    <View />
                </FrameLayout>
                """
            ).indented(),
            simpleInclude,
            xml(
                "res/values/styles.xml",
                """
                <resources>
                    <style name="Widget.Design.NavigationView" parent="">
                        <item name="android:background">?android:windowBackground</item>
                        <item name="android:fitsSystemWindows">true</item>
                    </style>
                    <style name="Widget.MaterialComponents.NavigationView" parent="@style/Widget.Design.NavigationView">
                    </style>
                </resources>
                """
            ).indented()
        ).run().expectClean()
    }

    fun testFitsSystemWindowViaManifestTheme() {
        lint().files(
            xml(
                "res/layout/simple.xml",
                """
                <FrameLayout
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    style="@style/Widget.MaterialComponents.NavigationView">
                    <View />
                </FrameLayout>
                """
            ).indented(),
            simpleInclude,
            xml(
                "res/values/styles.xml",
                """
                <resources>
                    <style name="Widget.Design.NavigationView" parent="">
                        <item name="android:background">?android:windowBackground</item>
                        <item name="android:fitsSystemWindows">true</item>
                    </style>
                    <style name="Widget.MaterialComponents.NavigationView" parent="@style/Widget.Design.NavigationView">
                    </style>
                </resources>
                """
            ).indented()
        ).run().expectClean()
    }

    private val simple = xml(
        "res/layout/simple.xml",
        """
        <FrameLayout
            xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="match_parent"
            android:layout_height="match_parent" />
        """
    ).indented()

    private val simpleInclude = xml(
        "res/layout/simpleinclude.xml",
        """
        <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:orientation="vertical" >

            <include
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                layout="@layout/simple" />

            <Button
                android:id="@+id/button1"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="Button" />

            <Button
                android:id="@+id/button2"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="Button" />

        </LinearLayout>
        """
    ).indented()
}
