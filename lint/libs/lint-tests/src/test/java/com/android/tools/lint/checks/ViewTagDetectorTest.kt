/*
 * Copyright (C) 2012 The Android Open Source Project
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

class ViewTagDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return ViewTagDetector()
    }

    private val viewTagTest = java(
        """
        package test.pkg;

        import android.annotation.SuppressLint;
        import android.content.Context;
        import android.database.Cursor;
        import android.database.MatrixCursor;
        import android.view.LayoutInflater;
        import android.view.View;
        import android.view.ViewGroup;
        import android.widget.CursorAdapter;
        import android.widget.ImageView;
        import android.widget.TextView;

        @SuppressWarnings("unused")
        public abstract class ViewTagTest {
            public View newView(Context context, ViewGroup group, Cursor cursor1,
                    MatrixCursor cursor2) {
                LayoutInflater inflater = LayoutInflater.from(context);
                View view = inflater.inflate(android.R.layout.activity_list_item, null);
                view.setTag(android.R.id.background, "Some random tag"); // OK
                view.setTag(android.R.id.button1, group); // ERROR
                view.setTag(android.R.id.icon, view.findViewById(android.R.id.icon)); // ERROR
                view.setTag(android.R.id.icon1, cursor1); // ERROR
                view.setTag(android.R.id.icon2, cursor2); // ERROR
                view.setTag(android.R.id.copy, new MyViewHolder()); // ERROR
                return view;
            }

            @SuppressLint("ViewTag")
            public static void checkSuppress(Context context, View view) {
                view.setTag(android.R.id.icon, view.findViewById(android.R.id.icon));
            }

            private class MyViewHolder {
                View view;
            }
        }
        """
    ).indented()

    fun test() {
        lint().files(
            viewTagTest
        ).run().expect(
            """
            src/test/pkg/ViewTagTest.java:21: Warning: Avoid setting views as values for setTag: Can lead to memory leaks in versions older than Android 4.0 [ViewTag]
                    view.setTag(android.R.id.button1, group); // ERROR
                                                      ~~~~~
            src/test/pkg/ViewTagTest.java:22: Warning: Avoid setting views as values for setTag: Can lead to memory leaks in versions older than Android 4.0 [ViewTag]
                    view.setTag(android.R.id.icon, view.findViewById(android.R.id.icon)); // ERROR
                                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/ViewTagTest.java:23: Warning: Avoid setting cursors as values for setTag: Can lead to memory leaks in versions older than Android 4.0 [ViewTag]
                    view.setTag(android.R.id.icon1, cursor1); // ERROR
                                                    ~~~~~~~
            src/test/pkg/ViewTagTest.java:24: Warning: Avoid setting cursors as values for setTag: Can lead to memory leaks in versions older than Android 4.0 [ViewTag]
                    view.setTag(android.R.id.icon2, cursor2); // ERROR
                                                    ~~~~~~~
            src/test/pkg/ViewTagTest.java:25: Warning: Avoid setting view holders as values for setTag: Can lead to memory leaks in versions older than Android 4.0 [ViewTag]
                    view.setTag(android.R.id.copy, new MyViewHolder()); // ERROR
                                                   ~~~~~~~~~~~~~~~~~~
            0 errors, 5 warnings
            """
        )
    }
    fun testICS() {
        lint().files(
            viewTagTest,
            manifest().minSdk(14)
        ).run().expectClean()
    }
}
