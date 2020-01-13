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

class GetContentDescriptionOverrideTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return GetContentDescriptionOverrideDetector()
    }

    fun testGetContentDescriptionOverrideExtendingView() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.content.Context;
                import android.view.View;

                public class MyView extends View {

                    public MyView(Context context) {
                        super(context);
                    }

                    @Override
                    public CharSequence getContentDescription() {
                        return "";
                    }
                }
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/MyView.java:13: Error: Overriding getContentDescription() on a View is not recommended [GetContentDescriptionOverride]
                public CharSequence getContentDescription() {
                                    ~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        )
    }

    fun testGetContentDescriptionOverrideViewHierarchy() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.content.Context;
                import android.view.View;

                public class ParentView extends View {

                    public ParentView(Context context) {
                        super(context);
                    }

                    public CharSequence getContentDescription() {
                        return "";
                    }
                }
                """
            ).indented(),
            java(
                """
                package test.pkg;

                import android.content.Context;
                import android.view.View;

                public class ChildView extends ParentView {

                    public ChildView(Context context) {
                        super(context);
                    }
                }
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/ParentView.java:12: Error: Overriding getContentDescription() on a View is not recommended [GetContentDescriptionOverride]
                public CharSequence getContentDescription() {
                                    ~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        )
    }

    fun testGetContentDescriptionOverrideExtendingViewWithArg() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.content.Context;
                import android.view.View;

                public class MyView extends View {

                    public MyView(Context context) {
                        super(context);
                    }

                    public CharSequence getContentDescription(String arg) {
                        return "";
                    }
                }
                """
            ).indented()
        ).run().expectClean()
    }

    fun testGetContentDescriptionOverrideNotExtendingView() {
        lint().files(
            java(
                """
                package test.pkg;

                public class MyPojo {
                    public CharSequence getContentDescription() {
                        return "";
                    }
                }
                """
            ).indented()
        ).run().expectClean()
    }
}
