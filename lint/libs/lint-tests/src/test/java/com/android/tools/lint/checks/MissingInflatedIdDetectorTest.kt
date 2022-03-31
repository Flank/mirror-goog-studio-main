/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.lint.checks.infrastructure.TestFiles.rClass
import com.android.tools.lint.detector.api.Detector

class MissingInflatedIdDetectorTest : AbstractCheckTest() {
    fun testDocumentationExample() {
        lint().files(
            kotlin(
                """
                package test.pkg

                import android.app.Activity
                import android.graphics.ColorFilter
                import android.os.Bundle
                import android.view.View
                import android.widget.EditText
                import android.widget.ImageView

                class MyActivity : Activity() {
                    override fun onCreate(savedInstanceState: Bundle?) {
                        super.onCreate(savedInstanceState)
                        setContentView(R.layout.activity_main)
                        // Error: @id/text_field is not in @layout/activity_main
                        requireViewById<EditText>(R.id.text_field).isEnabled = false
                    }

                    fun createListItem(filter: ColorFilter): View {
                        val rootView = layoutInflater.inflate(R.layout.list_item, null)
                        // Error: @id/image_view is not in @layout/list_item
                        val imgView = rootView.findViewById<ImageView>(R.id.image_view)
                        imgView.colorFilter = filter
                        return rootView
                    }
                }
                """
            ).indented(),
            xml(
                "res/layout/activity_main.xml",
                """
                <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    android:layout_width="match_parent" android:layout_height="match_parent" />
                """
            ).indented(),
            xml(
                "res/layout/list_item.xml",
                """
                <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    android:layout_width="match_parent" android:layout_height="match_parent" />
                """
            ).indented(),
            rClass(
                "test.pkg",
                "@layout/activity_main",
                "@layout/list_item",
                "@id/image_view",
                "@id/text_field",
            )
        ).run().expect(
            """
            src/test/pkg/MyActivity.kt:15: Error: @layout/activity_main does not contain a declaration with id text_field [MissingInflatedId]
                    requireViewById<EditText>(R.id.text_field).isEnabled = false
                                              ~~~~~~~~~~~~~~~
            src/test/pkg/MyActivity.kt:21: Error: @layout/list_item does not contain a declaration with id image_view [MissingInflatedId]
                    val imgView = rootView.findViewById<ImageView>(R.id.image_view)
                                                                   ~~~~~~~~~~~~~~~
            2 errors, 0 warnings
            """
        )
    }

    fun testInflatingOnView() {
        lint().files(
            kotlin(
                """
                package test.pkg

                import android.view.LayoutInflater
                import android.widget.ImageView

                fun testId(layoutInflater: LayoutInflater) {
                    val rootView = layoutInflater.inflate(R.layout.some_layout, null)
                    val imgView1 = rootView.findViewById<ImageView>(R.id.an_id_in_another_viewgroup)    // ERROR
                    val imgView2 = rootView.requireViewById<ImageView>(R.id.an_id_in_another_viewgroup) // ERROR
                    val view1 = rootView.requireViewById<ImageView>(R.id.navigation_host_fragment)      // OK
                    val view2 = rootView.findViewById<ImageView>(R.id.navigation_host_fragment)         // OK
                    val view3 = rootView.findViewById<ImageView>(R.id.frame_layout)                     // OK
                }
                """
            ),
            xml(
                "res/layout/some_layout.xml",
                """
                <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:app="http://schemas.android.com/apk/res-auto"
                    android:id="@+id/frame_layout"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent">

                    <androidx.fragment.app.FragmentContainerView
                        android:id="@+id/navigation_host_fragment"
                        android:name="androidx.navigation.fragment.NavHostFragment"
                        android:layout_width="match_parent"
                        android:layout_height="match_parent"
                        android:tag="tagNavHostFragment"
                        app:defaultNavHost="true"
                        app:navGraph="@navigation/nav_graph" />
                </FrameLayout>
                """
            ).indented(),
            xml(
                "res/layout/unrelated_layout.xml",
                """
                <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    android:id="@+id/an_id_in_another_viewgroup"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"/>
                """
            ).indented(),
            rClass(
                "test.pkg",
                "@layout/some_layout",
                "@layout/unrelated_layout",
                "@id/an_id_in_another_viewgroup",
                "@id/navigation_host_fragment",
                "@id/frame_layout"
            )
        ).run().expect(
            """
            src/test/pkg/test.kt:9: Error: @layout/some_layout does not contain a declaration with id an_id_in_another_viewgroup [MissingInflatedId]
                                val imgView1 = rootView.findViewById<ImageView>(R.id.an_id_in_another_viewgroup)    // ERROR
                                                                                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/test.kt:10: Error: @layout/some_layout does not contain a declaration with id an_id_in_another_viewgroup [MissingInflatedId]
                                val imgView2 = rootView.requireViewById<ImageView>(R.id.an_id_in_another_viewgroup) // ERROR
                                                                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            2 errors, 0 warnings
            """
        )
    }

    fun testInflatingOnCreate() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.app.Activity;
                import android.os.Bundle;
                import android.widget.*;

                public class MyActivity extends Activity {
                    @Override
                    public void onCreate(Bundle savedInstanceState) {
                        super.onCreate(savedInstanceState);
                        setContentView(R.layout.casts);
                        Button button = (Button) findViewById(R.id.button);                    // OK
                        ToggleButton toggleButton = (ToggleButton) findViewById(R.id.button);  // OK
                        TextView textView = (TextView) findViewById(R.id.edittext);            // OK
                        TextView textView = (TextView) findViewById(R.id.unknown);             // ERROR
                    }
                }
                """
            ).indented(),
            xml(
                "res/layout/casts.xml",
                """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:orientation="vertical" >

                    <view class="Button"
                        android:id="@+id/button"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Button" />

                    <EditText
                        android:id="@+id/edittext"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="EditText" />
                </LinearLayout>
                """
            ).indented(),
            rClass(
                "test.pkg",
                "@layout/casts",
                "@id/unknown",
                "@id/button",
                "@id/edittext"
            )
        ).run().expect(
            """
            src/test/pkg/MyActivity.java:15: Error: @layout/casts does not contain a declaration with id unknown [MissingInflatedId]
                    TextView textView = (TextView) findViewById(R.id.unknown);             // ERROR
                                                                ~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        )
    }

    fun testFrameworkIds() {
        lint().files(
            kotlin(
                """
                package test.pkg

                import android.view.LayoutInflater
                import android.widget.TextView

                fun testFrameworkLayout(layoutInflater: LayoutInflater) {
                    val rootView = layoutInflater.inflate(android.R.layout.simple_list_item_1, null)
                    val view1 = rootView.findViewById<TextView>(android.R.id.text1) // OK
                    val imgView2 = rootView.requireViewById<TextView>(R.id.my_id) // OK: not there, but not checking framework layouts
                }

                fun testFrameworkId(layoutInflater: LayoutInflater) {
                    val rootView = layoutInflater.inflate(R.layout.my_layout, null)
                    val view1 = rootView.findViewById<TextView>(android.R.id.text1) // OK: not there but not looking at android ids
                }
                """
            ),
            xml("res/layout/my_layout.xml", "<merge/>"),
            rClass("test.pkg", "@layout/my_layout", "@id/my_id")
        ).run().expectClean()
    }

    fun testIncludes() {
        // If there are <include> tags in the layout, don't draw any conclusions about which id's are present
        lint().files(
            java(
                """
                package test.pkg;

                import android.app.Activity;
                import android.os.Bundle;
                import android.widget.*;

                public class MyActivity extends Activity {
                    @Override
                    public void onCreate(Bundle savedInstanceState) {
                        super.onCreate(savedInstanceState);
                        setContentView(R.layout.casts);
                        Button button = (Button) findViewById(R.id.button); // OK
                    }
                }
                """
            ).indented(),
            xml(
                "res/layout/casts.xml",
                """
                <merge>
                    <include layout="@layout/content_main" />
                </merge>
                """
            ).indented(),
            xml("res/layout/content_main.xml", "<LinearLayout/>").indented(),
            rClass("test.pkg", "@layout/casts", "@layout/content_main", "@id/button")
        ).run().expectClean()
    }

    override fun getDetector(): Detector = MissingInflatedIdDetector()
}
