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

import com.android.tools.lint.detector.api.Detector

class InternalInsetResourceDetectorTests : AbstractCheckTest() {
    override fun getDetector(): Detector = InternalInsetResourceDetector()

    fun testDocumentationExample() {
        val expected =
            """
        src/test/pkg/test.kt:6: Warning: Using internal inset dimension resource status_bar_height is not supported [InternalInsetResource]
            getIdentifier("status_bar_height", "dimen", "android")
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        0 errors, 1 warnings
        """

        lint().files(
            kotlin(
                """
                package test.pkg

                import android.content.res.Resources

                fun Resources.getStatusBarHeightIdentifier(): Int =
                    getIdentifier("status_bar_height", "dimen", "android")
                """
            ).indented()
        ).run()
            .expect(expected)
    }

    fun testJava() {
        val expected =
            """
        src/test/pkg/IncorrectInsetHelper.java:7: Warning: Using internal inset dimension resource status_bar_height is not supported [InternalInsetResource]
                return resources.getIdentifier("status_bar_height", "dimen", "android");
                       ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/IncorrectInsetHelper.java:11: Warning: Using internal inset dimension resource status_bar_height_portrait is not supported [InternalInsetResource]
                return resources.getIdentifier("status_bar_height_portrait", "dimen", "android");
                       ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/IncorrectInsetHelper.java:15: Warning: Using internal inset dimension resource status_bar_height_landscape is not supported [InternalInsetResource]
                return resources.getIdentifier("status_bar_height_landscape", "dimen", "android");
                       ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/IncorrectInsetHelper.java:19: Warning: Using internal inset dimension resource navigation_bar_height is not supported [InternalInsetResource]
                return resources.getIdentifier("navigation_bar_height", "dimen", "android");
                       ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/IncorrectInsetHelper.java:23: Warning: Using internal inset dimension resource navigation_bar_height_landscape is not supported [InternalInsetResource]
                return resources.getIdentifier("navigation_bar_height_landscape", "dimen", "android");
                       ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/IncorrectInsetHelper.java:27: Warning: Using internal inset dimension resource navigation_bar_width is not supported [InternalInsetResource]
                return resources.getIdentifier("navigation_bar_width", "dimen", "android");
                       ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        0 errors, 6 warnings
        """

        lint().files(
            java(
                """
                package test.pkg;

                import android.content.res.Resources;

                public class IncorrectInsetHelper {
                    public static int getStatusBarHeightIdentifier(Resources resources) {
                        return resources.getIdentifier("status_bar_height", "dimen", "android");
                    }

                    public static int getStatusBarHeightPortraitIdentifier(Resources resources) {
                        return resources.getIdentifier("status_bar_height_portrait", "dimen", "android");
                    }

                    public static int getStatusBarHeightLandscapeIdentifier(Resources resources) {
                        return resources.getIdentifier("status_bar_height_landscape", "dimen", "android");
                    }

                    public static int getNavigationBarHeightIdentifier(Resources resources) {
                        return resources.getIdentifier("navigation_bar_height", "dimen", "android");
                    }

                    public static int getNavigationBarHeightLandscapeIdentifier(Resources resources) {
                        return resources.getIdentifier("navigation_bar_height_landscape", "dimen", "android");
                    }

                    public static int getNavigationBarWidthIdentifier(Resources resources) {
                        return resources.getIdentifier("navigation_bar_width", "dimen", "android");
                    }
                }
                """
            ).indented()
        ).run()
            .expect(expected)
    }

    fun testKotlin() {
        val expected =
            """
        src/test/pkg/test.kt:6: Warning: Using internal inset dimension resource status_bar_height is not supported [InternalInsetResource]
            getIdentifier("status_bar_height", "dimen", "android")
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/test.kt:9: Warning: Using internal inset dimension resource status_bar_height_portrait is not supported [InternalInsetResource]
            getIdentifier("status_bar_height_portrait", "dimen", "android")
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/test.kt:12: Warning: Using internal inset dimension resource status_bar_height_landscape is not supported [InternalInsetResource]
            getIdentifier("status_bar_height_landscape", "dimen", "android")
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/test.kt:15: Warning: Using internal inset dimension resource navigation_bar_height is not supported [InternalInsetResource]
            getIdentifier("navigation_bar_height", "dimen", "android")
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/test.kt:18: Warning: Using internal inset dimension resource navigation_bar_height_landscape is not supported [InternalInsetResource]
            getIdentifier("navigation_bar_height_landscape", "dimen", "android")
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/test.kt:21: Warning: Using internal inset dimension resource navigation_bar_width is not supported [InternalInsetResource]
            getIdentifier("navigation_bar_width", "dimen", "android")
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        0 errors, 6 warnings
        """

        lint().files(
            kotlin(
                """
                package test.pkg

                import android.content.res.Resources

                fun Resources.getStatusBarHeightIdentifier(): Int =
                    getIdentifier("status_bar_height", "dimen", "android")

                fun Resources.getStatusBarHeightPortraitIdentifier(): Int =
                    getIdentifier("status_bar_height_portrait", "dimen", "android")

                fun Resources.getStatusBarHeightLandscapeIdentifier(): Int =
                    getIdentifier("status_bar_height_landscape", "dimen", "android")

                fun Resources.getNavigationBarHeightIdentifier(): Int =
                    getIdentifier("navigation_bar_height", "dimen", "android")

                fun Resources.getNavigationBarHeightLandscapeIdentifier(): Int =
                    getIdentifier("navigation_bar_height_landscape", "dimen", "android")

                fun Resources.getNavigationBarWidthIdentifier(): Int =
                    getIdentifier("navigation_bar_width", "dimen", "android")
                """
            ).indented()
        ).run()
            .expect(expected)
    }

    fun testCleanJava() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.content.res.Resources;

                public class NotLintedHelpers {
                    public static int getOtherNameIdentifier(Resources resources) {
                        return resources.getIdentifier("some_other_name", "dimen", "android");
                    }

                    public static int getOtherTypeIdentifier(Resources resources) {
                        return resources.getIdentifier("status_bar_height", "bool", "android");
                    }

                    public static int getOtherPackageIdentifier(Resources resources) {
                        return resources.getIdentifier("status_bar_height", "dimen", "notandroid");
                    }
                }
                """
            ).indented()
        ).run()
            .expectClean()
    }

    fun testCleanKotlin() {
        lint().files(
            kotlin(
                """
                package test.pkg

                import android.content.res.Resources

                fun Resources.getOtherName(): Int =
                    getIdentifier("some_other_name", "dimen", "android")

                fun Resources.getOtherType(): Int =
                    getIdentifier("status_bar_height", "bool", "android")

                fun Resources.getOtherPackage(): Int =
                    getIdentifier("status_bar_height", "dimen", "notandroid")
                """
            ).indented()
        ).run()
            .expectClean()
    }
}
