/*
 * Copyright (C) 2018 The Android Open Source Project
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

class CanvasSizeDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return CanvasSizeDetector()
    }

    fun testBasic() {
        lint().files(
            java(
                """
                    package test.pkg;

                    import android.annotation.SuppressLint;
                    import android.content.Context;
                    import android.graphics.Canvas;
                    import android.os.Build;
                    import android.util.AttributeSet;
                    import android.view.View;

                    @SuppressWarnings({"unused", "ClassNameDiffersFromFileName","MethodMayBeStatic"})
                    @SuppressLint("ViewConstructor")
                    public class MyCustomView1 extends View {

                        public MyCustomView1(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
                            super(context, attrs, defStyleAttr, defStyleRes);
                        }

                        @Override
                        protected void onDraw(Canvas canvas) {
                            int width1 = getWidth(); // OK
                            View view = this;
                            int height1 = getHeight(); // OK
                            int width2 = this.getWidth(); // OK
                            int height2 = this.getHeight(); // OK
                            int width3 = view.getWidth(); // OK
                            int height3 = view.getHeight(); // OK
                            int width4 = canvas.getWidth(); // WARN
                            int height4 = canvas.getHeight(); // WARN
                        }

                        @Override
                        public void draw(Canvas canvas) {
                            super.draw(canvas);
                            int width4 = canvas.getWidth(); // WARN
                            int height4 = canvas.getHeight(); // WARN
                        }

                        public void someOtherMethod(Canvas canvas) {
                            int width4 = canvas.getWidth(); // OK
                            int height4 = canvas.getHeight(); // OK
                        }
                    }
                    """
            ).indented(),
            kotlin(
                """
                package test.pkg

                import android.annotation.SuppressLint
                import android.content.Context
                import android.graphics.Canvas
                import android.os.Build
                import android.util.AttributeSet
                import android.view.View

                @SuppressLint("ViewConstructor")
                class MyCustomView2(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : View(context, attrs, defStyleAttr, defStyleRes) {

                    override fun onDraw(canvas: Canvas) {
                        val view = this
                        val width3 = view.width // OK
                        val height3 = view.height // OK
                        val width4 = canvas.width // WARN
                        val height4 = canvas.height // WARN
                        val width5 = canvas.getWidth() // WARN
                        val height5 = canvas.getHeight() // WARN
                    }
                }
                """
            ).indented(),
            java(
                """
                package test.pkg;

                import android.graphics.Canvas;
                import android.graphics.drawable.Drawable;

                @SuppressWarnings({"unused", "ClassNameDiffersFromFileName","MethodMayBeStatic", "NullableProblems"})
                public abstract class MyDrawable extends Drawable {
                    @Override
                    public void draw(Canvas canvas) {
                        int width1 = getBounds().width(); // OK
                        int width2 = canvas.getWidth(); // WARN
                        int height2 = canvas.getHeight(); // WARN
                    }
                }
                """
            ).indented(),
            kotlin(
                """
                package test.pkg

                import android.graphics.Canvas
                import android.graphics.drawable.Drawable

                abstract class MyDrawable : Drawable() {
                    override fun draw(canvas: Canvas) {
                        val width1 = bounds.width() // OK
                        val width2 = canvas.width // WARN
                        val height2 = canvas.height // WARN
                    }
                }
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/MyCustomView1.java:27: Warning: Calling Canvas.getWidth() is usually wrong; you should be calling getWidth() instead [CanvasSize]
                    int width4 = canvas.getWidth(); // WARN
                                 ~~~~~~~~~~~~~~~~~
            src/test/pkg/MyCustomView1.java:28: Warning: Calling Canvas.getHeight() is usually wrong; you should be calling getHeight() instead [CanvasSize]
                    int height4 = canvas.getHeight(); // WARN
                                  ~~~~~~~~~~~~~~~~~~
            src/test/pkg/MyCustomView1.java:34: Warning: Calling Canvas.getWidth() is usually wrong; you should be calling getWidth() instead [CanvasSize]
                    int width4 = canvas.getWidth(); // WARN
                                 ~~~~~~~~~~~~~~~~~
            src/test/pkg/MyCustomView1.java:35: Warning: Calling Canvas.getHeight() is usually wrong; you should be calling getHeight() instead [CanvasSize]
                    int height4 = canvas.getHeight(); // WARN
                                  ~~~~~~~~~~~~~~~~~~
            src/test/pkg/MyCustomView2.kt:17: Warning: Referencing Canvas.width is usually wrong; you should be referencing width instead [CanvasSize]
                    val width4 = canvas.width // WARN
                                 ~~~~~~~~~~~~
            src/test/pkg/MyCustomView2.kt:18: Warning: Referencing Canvas.height is usually wrong; you should be referencing height instead [CanvasSize]
                    val height4 = canvas.height // WARN
                                  ~~~~~~~~~~~~~
            src/test/pkg/MyCustomView2.kt:19: Warning: Calling Canvas.getWidth() is usually wrong; you should be calling getWidth() instead [CanvasSize]
                    val width5 = canvas.getWidth() // WARN
                                 ~~~~~~~~~~~~~~~~~
            src/test/pkg/MyCustomView2.kt:20: Warning: Calling Canvas.getHeight() is usually wrong; you should be calling getHeight() instead [CanvasSize]
                    val height5 = canvas.getHeight() // WARN
                                  ~~~~~~~~~~~~~~~~~~
            src/test/pkg/MyDrawable.java:11: Warning: Calling Canvas.getWidth() is usually wrong; you should be calling getBounds().getWidth() instead [CanvasSize]
                    int width2 = canvas.getWidth(); // WARN
                                 ~~~~~~~~~~~~~~~~~
            src/test/pkg/MyDrawable.java:12: Warning: Calling Canvas.getHeight() is usually wrong; you should be calling getBounds().getHeight() instead [CanvasSize]
                    int height2 = canvas.getHeight(); // WARN
                                  ~~~~~~~~~~~~~~~~~~
            src/test/pkg/MyDrawable.kt:9: Warning: Referencing Canvas.width is usually wrong; you should be referencing bounds.width instead [CanvasSize]
                    val width2 = canvas.width // WARN
                                 ~~~~~~~~~~~~
            src/test/pkg/MyDrawable.kt:10: Warning: Referencing Canvas.height is usually wrong; you should be referencing bounds.height instead [CanvasSize]
                    val height2 = canvas.height // WARN
                                  ~~~~~~~~~~~~~
            0 errors, 12 warnings
            """
        ).expectFixDiffs(
            """
                Fix for src/test/pkg/MyCustomView1.java line 27: Call getWidth() instead:
                @@ -27 +27
                -         int width4 = canvas.getWidth(); // WARN
                +         int width4 = getWidth(); // WARN
                Fix for src/test/pkg/MyCustomView1.java line 28: Call getHeight() instead:
                @@ -28 +28
                -         int height4 = canvas.getHeight(); // WARN
                +         int height4 = getHeight(); // WARN
                Fix for src/test/pkg/MyCustomView1.java line 34: Call getWidth() instead:
                @@ -34 +34
                -         int width4 = canvas.getWidth(); // WARN
                +         int width4 = getWidth(); // WARN
                Fix for src/test/pkg/MyCustomView1.java line 35: Call getHeight() instead:
                @@ -35 +35
                -         int height4 = canvas.getHeight(); // WARN
                +         int height4 = getHeight(); // WARN
                Fix for src/test/pkg/MyCustomView2.kt line 17: Reference width instead:
                @@ -17 +17
                -         val width4 = canvas.width // WARN
                +         val width4 = width // WARN
                Fix for src/test/pkg/MyCustomView2.kt line 18: Reference height instead:
                @@ -18 +18
                -         val height4 = canvas.height // WARN
                +         val height4 = height // WARN
                Fix for src/test/pkg/MyCustomView2.kt line 19: Call getWidth() instead:
                @@ -19 +19
                -         val width5 = canvas.getWidth() // WARN
                +         val width5 = getWidth() // WARN
                Fix for src/test/pkg/MyCustomView2.kt line 20: Call getHeight() instead:
                @@ -20 +20
                -         val height5 = canvas.getHeight() // WARN
                +         val height5 = getHeight() // WARN
                Fix for src/test/pkg/MyDrawable.java line 11: Call getBounds().width() instead:
                @@ -11 +11
                -         int width2 = canvas.getWidth(); // WARN
                +         int width2 = getBounds().width(); // WARN
                Fix for src/test/pkg/MyDrawable.java line 12: Call getBounds().height() instead:
                @@ -12 +12
                -         int height2 = canvas.getHeight(); // WARN
                +         int height2 = getBounds().height(); // WARN
                Fix for src/test/pkg/MyDrawable.kt line 9: Call bounds.width() instead:
                @@ -9 +9
                -         val width2 = canvas.width // WARN
                +         val width2 = bounds.width() // WARN
                Fix for src/test/pkg/MyDrawable.kt line 10: Call bounds.height() instead:
                @@ -10 +10
                -         val height2 = canvas.height // WARN
                +         val height2 = bounds.height() // WARN
                """
        )
    }

    fun test78378459() {
        // Regression test for issue 78378459
        lint().files(
            kotlin(
                """
                package test.pkg

                import android.graphics.Canvas
                import android.graphics.LinearGradient
                import android.graphics.Paint
                import android.graphics.RectF
                import android.graphics.Shader.TileMode.CLAMP
                import android.graphics.drawable.ShapeDrawable
                import android.graphics.drawable.shapes.RectShape

                class GradientBackgroundDrawable(
                        private val padding: Int
                ) : ShapeDrawable(RectShape()) {
                    private val colors = IntArray(5)

                    override fun draw(canvas: Canvas) {
                        val rectF = RectF(0f, padding.toFloat(), shape.width, canvas.height - padding.toFloat())
                        val shader = LinearGradient(rectF.left, rectF.top, rectF.right, rectF.bottom, colors, null, CLAMP)
                        val paint = Paint()
                        paint.shader = shader
                        canvas.drawRect(rectF, paint)
                    }
                }
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/GradientBackgroundDrawable.kt:17: Warning: Referencing Canvas.height is usually wrong; you should be referencing bounds.height instead [CanvasSize]
                    val rectF = RectF(0f, padding.toFloat(), shape.width, canvas.height - padding.toFloat())
                                                                          ~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        ).expectFixDiffs(
            """
            Fix for src/test/pkg/GradientBackgroundDrawable.kt line 17: Call bounds.height() instead:
            @@ -17 +17
            -         val rectF = RectF(0f, padding.toFloat(), shape.width, canvas.height - padding.toFloat())
            +         val rectF = RectF(0f, padding.toFloat(), shape.width, bounds.height() - padding.toFloat())
            """
        )
    }
}
