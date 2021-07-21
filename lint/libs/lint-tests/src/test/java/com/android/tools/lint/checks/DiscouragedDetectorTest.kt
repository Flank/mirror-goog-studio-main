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

import com.android.tools.lint.detector.api.Detector

class DiscouragedDetectorTest : AbstractCheckTest() {

    private val discouragedAnnotationStub = java(
        "src/androidx/annotation/Discouraged.java",
        """
            package androidx.annotation;
            import static java.lang.annotation.ElementType.METHOD;
            import static java.lang.annotation.RetentionPolicy.SOURCE;

            import java.lang.annotation.Retention;
            import java.lang.annotation.Target;

            // Stub annotation for unit test.
            @Retention(SOURCE)
            @Target({METHOD})
            public @interface Discouraged {
                String message() default "";
            }
        """
    ).indented()

    private val resourcesStub = java(
        "src/android/content/res/Resources.java",
        """
            package android.content.res;

            import android.util.TypedValue;
            import androidx.annotation.Discouraged;

            public class Resources {

                @Discouraged(message="Use of this function is discouraged. It is more efficient "
                                   + "to retrieve resources by identifier than by name.\n"
                                   + "See `getValue(int id, TypedValue outValue, boolean "
                                   + "resolveRefs)`.")
                public int getValue(String name, TypedValue outValue, boolean resolveRefs) { }

                public int getValue(int id, TypedValue outValue, boolean resolveRefs) { }
            }
        """
    ).indented()

    fun testDiscouraged() {
        val expected =
            """
            src/test/pkg/Test1.java:9: Warning: Use of this function is discouraged. It is more efficient to retrieve resources by identifier than by name.
            See getValue(int id, TypedValue outValue, boolean resolveRefs). [DiscouragedApi]
                    Resources.getValue("name", testValue, false);
                              ~~~~~~~~
            0 errors, 1 warnings
            """
        lint().files(
            java(
                """
                package test.pkg;

                import android.content.res.Resources;
                import android.util.TypedValue;

                public class Test1 {
                    public void setValue() {
                        TypedValue testValue;
                        Resources.getValue("name", testValue, false);
                        Resources.getValue(0, testValue, false);
                    }
                }
                """
            ).indented(),
            resourcesStub,
            discouragedAnnotationStub
        ).run().expect(expected)
    }

    override fun getDetector(): Detector {
        return DiscouragedDetector()
    }
}
