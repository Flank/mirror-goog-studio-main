/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.lint.client.api

import com.android.testutils.TestUtils
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UElement
import org.junit.Test

class AnnotationHandlerTest {
    @Test
    fun testReferenceKotlinAnnotation() {
        TestLintTask.lint().sdkHome(TestUtils.getSdk()).files(
            java(
                """
                    package test.pkg;
                    import java.MyJavaAnnotation;
                    import kotlin.MyKotlinAnnotation;

                    public class JavaUsage {
                        public void test() {
                            new JavaApi().method1();
                            new JavaApi().method2();
                            new KotlinApi().method1();
                            new KotlinApi().method2();
                        }
                    }
                    """
            ).indented(),
            kotlin(
                """
                    package test.pkg
                    import java.MyJavaAnnotation
                    import kotlin.MyKotlinAnnotation

                    class KotlinUsage {
                        fun test() {
                            JavaApi().method1()
                            JavaApi().method2()
                            KotlinApi().method1()
                            KotlinApi().method2()
                        }

                        @Suppress("_AnnotationIssue")
                        fun suppressedId1() {
                            JavaApi().method1()
                        }

                        fun suppressedId2() {
                            //noinspection _AnnotationIssue
                            KotlinApi().method1()
                        }

                        @Suppress("Correctness:Test Category")
                        fun suppressedCategory1() {
                            JavaApi().method1()
                        }

                        fun suppressedCategory2() {
                            //noinspection Correctness
                            KotlinApi().method1()
                        }

                        @Suppress("Correctness")
                        fun suppressedCategory3() {
                            JavaApi().method1()
                        }

                        fun suppressedCategory4() {
                            //noinspection Correctness:Test Category
                            KotlinApi().method1()
                        }
                    }
                    """
            ).indented(),
            java(
                """
                    package test.pkg;
                    import java.MyJavaAnnotation;
                    import kotlin.MyKotlinAnnotation;

                    public class JavaApi {
                        @MyJavaAnnotation
                        public void method1() {
                        }

                        @MyKotlinAnnotation
                        public void method2() {
                        }
                    }
                    """
            ).indented(),
            kotlin(
                """
                    package test.pkg
                    import java.MyJavaAnnotation
                    import kotlin.MyKotlinAnnotation

                    class KotlinApi {
                        @MyJavaAnnotation
                        fun method1() {
                        }

                        @MyKotlinAnnotation
                        fun method2() {
                        }
                    }
                    """
            ).indented(),
            java(
                """
                    package java;
                    public @interface MyJavaAnnotation {
                    }
                    """
            ).indented(),
            kotlin(
                """
                package kotlin
                annotation class MyKotlinAnnotation {
                }
                """
            ).indented()
        ).issues(MyAnnotationDetector.TEST_ISSUE).run().expect(
            """
            src/test/pkg/JavaUsage.java:7: Error: Forbidden annotation java.MyJavaAnnotation [_AnnotationIssue]
                    new JavaApi().method1();
                    ~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/JavaUsage.java:8: Error: Forbidden annotation kotlin.MyKotlinAnnotation [_AnnotationIssue]
                    new JavaApi().method2();
                    ~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/JavaUsage.java:9: Error: Forbidden annotation java.MyJavaAnnotation [_AnnotationIssue]
                    new KotlinApi().method1();
                    ~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/JavaUsage.java:10: Error: Forbidden annotation kotlin.MyKotlinAnnotation [_AnnotationIssue]
                    new KotlinApi().method2();
                    ~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/KotlinUsage.kt:7: Error: Forbidden annotation java.MyJavaAnnotation [_AnnotationIssue]
                    JavaApi().method1()
                    ~~~~~~~~~~~~~~~~~~~
            src/test/pkg/KotlinUsage.kt:8: Error: Forbidden annotation kotlin.MyKotlinAnnotation [_AnnotationIssue]
                    JavaApi().method2()
                    ~~~~~~~~~~~~~~~~~~~
            src/test/pkg/KotlinUsage.kt:9: Error: Forbidden annotation java.MyJavaAnnotation [_AnnotationIssue]
                    KotlinApi().method1()
                    ~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/KotlinUsage.kt:10: Error: Forbidden annotation kotlin.MyKotlinAnnotation [_AnnotationIssue]
                    KotlinApi().method2()
                    ~~~~~~~~~~~~~~~~~~~~~
            8 errors, 0 warnings
            """
        )
    }

    // Simple detector which just flags certain annotation references
    @SuppressWarnings("ALL")
    class MyAnnotationDetector : Detector(), Detector.UastScanner {
        override fun applicableAnnotations(): List<String>? {
            return listOf("java.MyJavaAnnotation", "kotlin.MyKotlinAnnotation")
        }

        override fun visitAnnotationUsage(
            context: JavaContext,
            usage: UElement,
            type: AnnotationUsageType,
            annotation: UAnnotation,
            qualifiedName: String,
            method: PsiMethod?,
            annotations: List<UAnnotation>,
            allMemberAnnotations: List<UAnnotation>,
            allClassAnnotations: List<UAnnotation>,
            allPackageAnnotations: List<UAnnotation>
        ) {
            val message = "Forbidden annotation $qualifiedName"
            val location = context.getLocation(usage)
            context.report(TEST_ISSUE, usage, location, message)
        }

        companion object {
            @JvmField
            val TEST_CATEGORY = Category.create(Category.CORRECTNESS, "Test Category", 0)

            @Suppress("SpellCheckingInspection")
            @JvmField
            val TEST_ISSUE = Issue.create(
                id = "_AnnotationIssue",
                briefDescription = "Blahblah",
                explanation = "Blahdiblah",
                category = TEST_CATEGORY,
                priority = 10,
                severity = Severity.ERROR,
                implementation = Implementation(
                    MyAnnotationDetector::class.java,
                    Scope.JAVA_FILE_SCOPE
                )
            )
        }
    }
}
