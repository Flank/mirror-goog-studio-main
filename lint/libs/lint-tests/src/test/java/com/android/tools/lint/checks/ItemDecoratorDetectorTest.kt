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

class ItemDecoratorDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return ItemDecoratorDetector()
    }

    fun test() {
        lint().files(
            java(
                """
                package com.example.android.supportv7.widget.decorator;

                import android.content.Context;
                import android.content.res.TypedArray;
                import android.graphics.Canvas;
                import android.graphics.Rect;
                import android.graphics.drawable.Drawable;
                import android.support.v7.widget.RecyclerView;
                import android.view.View;

                public abstract class DividerItemDecoration extends RecyclerView.ItemDecoration {

                    private static final int[] ATTRS = new int[]{
                            android.R.attr.listDivider
                    };

                    public static int HORIZONTAL_LIST;

                    public static int VERTICAL_LIST;

                    private Drawable mDivider;

                    private int mOrientation;
                }
                """
            ).indented(),
            java(
                """
                package android.support.v7.widget;
                public class RecyclerView {
                    public abstract static class ItemDecoration {
                    }

                }
                """
            ).indented()
        ).run().expect(
            """
            src/com/example/android/supportv7/widget/decorator/DividerItemDecoration.java:11: Warning: Replace with android.support.v7.widget.DividerItemDecoration? [DuplicateDivider]
            public abstract class DividerItemDecoration extends RecyclerView.ItemDecoration {
                                  ~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }
}
