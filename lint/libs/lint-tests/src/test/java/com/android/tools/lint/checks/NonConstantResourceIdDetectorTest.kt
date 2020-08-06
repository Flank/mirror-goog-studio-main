/*
 * Copyright (C) 2020 The Android Open Source Project
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

/**
 * Tests for [NonConstantResourceIdDetector]
 */
class NonConstantResourceIdDetectorTest : AbstractCheckTest() {

    override fun getDetector(): Detector = NonConstantResourceIdDetector()

    fun `test java detects constant resource ids in switch block`() {
        lint().files(
            rClass,
            java(
                """
                package test.pkg;

                public class SwitchTest {

                    SwitchTest() {}

                    public int switchWithRClassReferences(int value) {
                        int someValue;
                        int styleableReference = R.styleable.FontFamily_fontProviderAuthority;
                        switch (value) {
                            // Warning: Case value is a resource and is not constant.
                            case R.styleable.FontFamilyFont_android_fontWeight: someValue = 1; break;
                            // No warning: Case value is not a resource and is constant.
                            case 1: someValue = 2; break;
                            // Warning: Case value is a resource and is constant.
                            case R.id.text: someValue = 3; break;
                            // The android.R class cannot be modified by the user.
                            case android.R.attr.fontFamily: someValue = 5; break;
                            default: someValue = 4; break;
                        }
                        return someValue;
                    }

                }"""
            )
        ).run().expect(
            """
            src/test/pkg/SwitchTest.java:13: Warning: Resource IDs will be non-final in Android Gradle Plugin version 5.0, avoid using them in switch case statements [NonConstantResourceId]
                                        case R.styleable.FontFamilyFont_android_fontWeight: someValue = 1; break;
                                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/SwitchTest.java:17: Warning: Resource IDs will be non-final in Android Gradle Plugin version 5.0, avoid using them in switch case statements [NonConstantResourceId]
                                        case R.id.text: someValue = 3; break;
                                             ~~~~~~~~~
            0 errors, 2 warnings
            """
        )
    }

    fun `test kotlin does not report resource id usages in when expressions`() {
        lint().files(
            rClass,
            kotlin(
                """package test.pkg

                    class WhenTest {

                        fun testSwitchWithRClassReferences(value : Int) {
                            var someValue: Int = -1
                            val styleableReference = R.id.text
                            when(value) {
                                R.styleable.FontFamilyFont_android_fontWeight -> { someValue = 0 }
                                1 -> {someValue = 1}
                                R.id.text -> {someValue = 2}
                                android.R.attr.fontFamily -> {someValue = 4}
                                else -> {someValue = 3}
                            }
                        }
                    }
                """
            )
        ).run().expectClean()
    }

    fun `test java detects non constant resource ids in annotations`() {
        lint().files(
            rClass,
            java(
                """
                    package test.pkg;
                    public @interface TestAnnotation {
                        public int resourceId() default null;
                    }
                """
            ),
            java(
                """
                package test.pkg;

                public class JavaAnnotationTest {

                    JavaAnnotationTest() {}

                    @TestAnnotation(resourceId = R.styleable.FontFamilyFont_android_fontWeight)
                    public int annotatedMethodWithNonConstantStyleableResourceId(int value) {
                        return 0;
                    }

                    @TestAnnotation(resourceId = R.id.text)
                    public int annotatedMethodWithNonConstantResourceId(int value) {
                        return 0;
                    }

                    @TestAnnotation(resourceId = 5)
                    public int annotatedMethodWithNonConstantValue(int value) {
                        return 0;
                    }
                }
                """
            )
        ).run().expect(
            """
            src/test/pkg/JavaAnnotationTest.java:8: Warning: Resource IDs will be non-final in Android Gradle Plugin version 5.0, avoid using them as annotation attributes [NonConstantResourceId]
                                @TestAnnotation(resourceId = R.styleable.FontFamilyFont_android_fontWeight)
                                                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/JavaAnnotationTest.java:13: Warning: Resource IDs will be non-final in Android Gradle Plugin version 5.0, avoid using them as annotation attributes [NonConstantResourceId]
                                @TestAnnotation(resourceId = R.id.text)
                                                             ~~~~~~~~~
            0 errors, 2 warnings
            """
        )
    }

    fun `test kotlin detects non constant resource ids in annotations`() {
        lint().files(
            rClass,
            kotlin(
                """
                    package test.pkg

                    annotation class TestAnnotation(val resourceId : Int)
                """
            ),
            kotlin(
                """
                package test.pkg

                class KtAnnotationTest {
                    @TestAnnotation(resourceId = R.styleable.FontFamilyFont_android_fontWeight)
                    fun annotatedMethodWithNonConstantStyleableResourceId(value : Int) : Int = 0

                    @TestAnnotation(resourceId = R.id.text)
                    fun annotatedMethodWithNonConstantResourceId(value : Int) : Int = 0;

                    @TestAnnotation(resourceId = 5)
                    fun annotatedMethodWithNonConstantResourceId(value : Int) : Int = 0;
                }
            """

            )
        ).run().expect(
            """
            src/test/pkg/KtAnnotationTest.kt:5: Warning: Resource IDs will be non-final in Android Gradle Plugin version 5.0, avoid using them as annotation attributes [NonConstantResourceId]
                                @TestAnnotation(resourceId = R.styleable.FontFamilyFont_android_fontWeight)
                                                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/KtAnnotationTest.kt:8: Warning: Resource IDs will be non-final in Android Gradle Plugin version 5.0, avoid using them as annotation attributes [NonConstantResourceId]
                                @TestAnnotation(resourceId = R.id.text)
                                                             ~~~~~~~~~
            0 errors, 2 warnings
        """
        )
    }

    val rClass = java(
        """
        package test.pkg;

        public final class R {

                public static final class id {
                    public static final int text = 7000;
                }
                public static final class styleable {
                    public static final int[] FontFamily = { 0x7f0400d2, 0x7f0400d3, 0x7f0400d4, 0x7f0400d5, 0x7f0400d6, 0x7f0400d7 };
                    public static final int FontFamily_fontProviderAuthority = 0;
                    public static final int FontFamily_fontProviderCerts = 1;
                    public static final int FontFamily_fontProviderFetchStrategy = 2;
                    public static final int FontFamily_fontProviderFetchTimeout = 3;
                    public static final int FontFamily_fontProviderPackage = 4;
                    public static final int FontFamily_fontProviderQuery = 5;
            }
        }"""
    ).indented()
}
