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

class ViewConstructorDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return ViewConstructorDetector()
    }

    fun testBasic() {
        lint().files(
            classpath(),
            manifest().minSdk(10),
            java(
                """
                package test.bytecode;

                import android.view.View;

                public class CustomView1 extends View {
                	public CustomView1() {
                		super(null);
                	}
                }
                """
            ).indented(),
            java(
                """
                package test.bytecode;

                import android.content.Context;
                import android.util.AttributeSet;
                import android.widget.Button;

                public class CustomView2 extends Button {
                	public CustomView2(boolean foo,
                			Context context, AttributeSet attrs, int defStyle) {
                		super(context, attrs, defStyle);
                	}
                }
                """
            ).indented(),
            java(
                """
                package test.bytecode;

                import android.content.Context;
                import android.util.AttributeSet;
                import android.view.View;

                public class CustomView3 extends View {

                	public CustomView3(Context context, AttributeSet attrs, int defStyle) {
                		super(context, attrs, defStyle);
                	}

                }
                """
            ).indented()
        ).run().expect(
            """
            src/test/bytecode/CustomView1.java:5: Warning: Custom view CustomView1 is missing constructor used by tools: (Context) or (Context,AttributeSet) or (Context,AttributeSet,int) [ViewConstructor]
            public class CustomView1 extends View {
                         ~~~~~~~~~~~
            src/test/bytecode/CustomView2.java:7: Warning: Custom view CustomView2 is missing constructor used by tools: (Context) or (Context,AttributeSet) or (Context,AttributeSet,int) [ViewConstructor]
            public class CustomView2 extends Button {
                         ~~~~~~~~~~~
            0 errors, 2 warnings
            """
        )
    }

    fun testInheritLocal() {
        lint().files(
            classpath(),
            manifest().minSdk(10),
            java(
                """
                package test.pkg;

                import android.app.Activity;
                import android.widget.Button;

                /** Local activity */
                public abstract class Intermediate extends Activity {

                	/** Local Custom view */
                	public abstract static class IntermediateCustomV extends Button {
                		public IntermediateCustomV() {
                			super(null);
                		}
                	}
                }
                """
            ).indented(),
            java(
                """
                package test.pkg;

                import test.pkg.Intermediate.IntermediateCustomV;

                public class CustomViewTest extends IntermediateCustomV {
                }
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/CustomViewTest.java:5: Warning: Custom view CustomViewTest is missing constructor used by tools: (Context) or (Context,AttributeSet) or (Context,AttributeSet,int) [ViewConstructor]
            public class CustomViewTest extends IntermediateCustomV {
                         ~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }
    fun testAbstract() {
        //noinspection all // Sample code
        lint().files(
            classpath(),
            manifest().minSdk(10),
            java(
                """
                package test.pkg;

                import android.view.View;

                public abstract class AbstractCustomView extends View {
                    public AbstractCustomView() {
                        super(null);
                    }
                }
                """
            ).indented()
        ).run().expectClean()
    }

    fun testPrivate() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.view.View;

                public class Test {
                    private static class PrivateCustomView extends View {
                        public PrivateCustomView() {
                            super(null);
                        }
                    }
                }
                """
            ).indented()
        ).run().expectClean()
    }
}
