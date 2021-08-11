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

import com.android.testutils.TestUtils.getSdk
import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles.bytecode
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UElement
import org.junit.Test

class AnnotationHandlerTest {
    private fun lint() = TestLintTask.lint().sdkHome(getSdk().toFile()).issues(MyAnnotationDetector.TEST_ISSUE)

    private val javaAnnotation: TestFile = java(
        """
        package pkg.java;
        public @interface MyJavaAnnotation {
        }
        """
    ).indented()

    private val kotlinAnnotation: TestFile = kotlin(
        """
        package pkg.kotlin
        annotation class MyKotlinAnnotation
        """
    ).indented()

    @Test
    fun testReferenceKotlinAnnotation() {
        lint().files(
            java(
                """
                    package test.pkg;
                    import pkg.java.MyJavaAnnotation;
                    import pkg.kotlin.MyKotlinAnnotation;

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
                    import pkg.java.MyJavaAnnotation
                    import pkg.kotlin.MyKotlinAnnotation

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
                    import pkg.java.MyJavaAnnotation;
                    import pkg.kotlin.MyKotlinAnnotation;

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
                    import pkg.java.MyJavaAnnotation
                    import pkg.kotlin.MyKotlinAnnotation

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
            javaAnnotation,
            kotlinAnnotation
        ).run().expect(
            """
            src/test/pkg/JavaUsage.java:7: Error: METHOD_CALL usage of annotated element (@MyJavaAnnotation)  [_AnnotationIssue]
                    new JavaApi().method1();
                    ~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/JavaUsage.java:8: Error: METHOD_CALL usage of annotated element (@MyKotlinAnnotation)  [_AnnotationIssue]
                    new JavaApi().method2();
                    ~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/JavaUsage.java:9: Error: METHOD_CALL usage of annotated element (@MyJavaAnnotation)  [_AnnotationIssue]
                    new KotlinApi().method1();
                    ~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/JavaUsage.java:10: Error: METHOD_CALL usage of annotated element (@MyKotlinAnnotation)  [_AnnotationIssue]
                    new KotlinApi().method2();
                    ~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/KotlinUsage.kt:7: Error: METHOD_CALL usage of annotated element (@MyJavaAnnotation)  [_AnnotationIssue]
                    JavaApi().method1()
                    ~~~~~~~~~~~~~~~~~~~
            src/test/pkg/KotlinUsage.kt:8: Error: METHOD_CALL usage of annotated element (@MyKotlinAnnotation)  [_AnnotationIssue]
                    JavaApi().method2()
                    ~~~~~~~~~~~~~~~~~~~
            src/test/pkg/KotlinUsage.kt:9: Error: METHOD_CALL usage of annotated element (@MyJavaAnnotation)  [_AnnotationIssue]
                    KotlinApi().method1()
                    ~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/KotlinUsage.kt:10: Error: METHOD_CALL usage of annotated element (@MyKotlinAnnotation)  [_AnnotationIssue]
                    KotlinApi().method2()
                    ~~~~~~~~~~~~~~~~~~~~~
            8 errors, 0 warnings
            """
        )
    }

    @Test
    fun testFieldReferences() {
        lint().files(
            java(
                """
                package test.api;
                import pkg.java.MyJavaAnnotation;
                import pkg.kotlin.MyKotlinAnnotation;

                @MyJavaAnnotation
                @MyKotlinAnnotation
                public class Api {
                    public Api next = null;
                    public Object field = null;
                }
                """
            ).indented(),
            java(
                """
                package test.usage;
                import test.api.Api;
                public class Usage {
                    private void use(Object o) { }
                    public void test(Api api) {
                        use(api.field);      // ERROR 1A and 1B
                        use(api.next.field); // ERROR 2A, 2B on next, 3A, 3B on field
                    }
                }
                """
            ).indented(),
            kotlin(
                """
                package test.usage
                import test.api.Api
                private fun use(o: Any?) { }
                fun test(api: Api) {
                    use(api.field)       // ERROR 4A and 4B
                    use(api.next.field)  // ERROR 5A, 5B on next, 6A, 6B on field
                }
                """
            ).indented(),
            javaAnnotation,
            kotlinAnnotation
        ).run().expect(
            """
            src/test/usage/Usage.java:6: Error: FIELD_REFERENCE_CLASS usage of annotated element (@MyJavaAnnotation)  [_AnnotationIssue]
                    use(api.field);      // ERROR 1A and 1B
                            ~~~~~
            src/test/usage/Usage.java:6: Error: FIELD_REFERENCE_CLASS usage of annotated element (@MyKotlinAnnotation)  [_AnnotationIssue]
                    use(api.field);      // ERROR 1A and 1B
                            ~~~~~
            src/test/usage/Usage.java:7: Error: FIELD_REFERENCE_CLASS usage of annotated element (@MyJavaAnnotation)  [_AnnotationIssue]
                    use(api.next.field); // ERROR 2A, 2B on next, 3A, 3B on field
                            ~~~~
            src/test/usage/Usage.java:7: Error: FIELD_REFERENCE_CLASS usage of annotated element (@MyJavaAnnotation)  [_AnnotationIssue]
                    use(api.next.field); // ERROR 2A, 2B on next, 3A, 3B on field
                                 ~~~~~
            src/test/usage/Usage.java:7: Error: FIELD_REFERENCE_CLASS usage of annotated element (@MyKotlinAnnotation)  [_AnnotationIssue]
                    use(api.next.field); // ERROR 2A, 2B on next, 3A, 3B on field
                            ~~~~
            src/test/usage/Usage.java:7: Error: FIELD_REFERENCE_CLASS usage of annotated element (@MyKotlinAnnotation)  [_AnnotationIssue]
                    use(api.next.field); // ERROR 2A, 2B on next, 3A, 3B on field
                                 ~~~~~
            src/test/usage/test.kt:5: Error: FIELD_REFERENCE_CLASS usage of annotated element (@MyJavaAnnotation)  [_AnnotationIssue]
                use(api.field)       // ERROR 4A and 4B
                        ~~~~~
            src/test/usage/test.kt:5: Error: FIELD_REFERENCE_CLASS usage of annotated element (@MyKotlinAnnotation)  [_AnnotationIssue]
                use(api.field)       // ERROR 4A and 4B
                        ~~~~~
            src/test/usage/test.kt:6: Error: FIELD_REFERENCE_CLASS usage of annotated element (@MyJavaAnnotation)  [_AnnotationIssue]
                use(api.next.field)  // ERROR 5A, 5B on next, 6A, 6B on field
                        ~~~~
            src/test/usage/test.kt:6: Error: FIELD_REFERENCE_CLASS usage of annotated element (@MyJavaAnnotation)  [_AnnotationIssue]
                use(api.next.field)  // ERROR 5A, 5B on next, 6A, 6B on field
                             ~~~~~
            src/test/usage/test.kt:6: Error: FIELD_REFERENCE_CLASS usage of annotated element (@MyKotlinAnnotation)  [_AnnotationIssue]
                use(api.next.field)  // ERROR 5A, 5B on next, 6A, 6B on field
                        ~~~~
            src/test/usage/test.kt:6: Error: FIELD_REFERENCE_CLASS usage of annotated element (@MyKotlinAnnotation)  [_AnnotationIssue]
                use(api.next.field)  // ERROR 5A, 5B on next, 6A, 6B on field
                             ~~~~~
            12 errors, 0 warnings
            """
        )
    }

    @Test
    fun testOuterClassReferences() {
        lint().files(
            java(
                """
                package test.api;
                import pkg.java.MyJavaAnnotation;
                import pkg.kotlin.MyKotlinAnnotation;

                @MyJavaAnnotation
                @MyKotlinAnnotation
                public class Api {
                    public static class InnerApi {
                        public static String method() { return ""; }
                        public static final String CONSTANT = "";
                    }
                }
                """
            ).indented(),
            java(
                """
                package test.usage;
                import test.api.Api;
                import test.api.Api.InnerApi;
                import static test.api.Api.InnerApi.CONSTANT;
                import static test.api.Api.InnerApi.method;

                public class JavaUsage {
                    private void use(Object o) { }
                    public void test(InnerApi innerApi) {
                        use(InnerApi.CONSTANT); // ERROR 1A and 1B
                        use(CONSTANT);          // ERROR 2A and 2B
                        use(innerApi.method()); // ERROR 3A and 3B
                        use(method());          // ERROR 4A and 4B
                    }
                }
                """
            ).indented(),
            kotlin(
                """
                package test.usage

                import test.api.Api.InnerApi
                import test.api.Api.InnerApi.CONSTANT
                import test.api.Api.InnerApi.method

                class KotlinUsage {
                    private fun use(o: Any) {}
                    fun test(innerApi: InnerApi?) {
                        use(InnerApi.CONSTANT)     // ERROR 5A and 5B
                        use(CONSTANT)              // ERROR 6A and 6B
                        use(InnerApi.method())     // ERROR 7A and 7B
                        use(method())              // ERROR 8A and 8B
                    }
                }
                """
            ).indented(),
            javaAnnotation,
            kotlinAnnotation
        ).run().expect(
            """
            src/test/usage/JavaUsage.java:10: Error: FIELD_REFERENCE_OUTER_CLASS usage of annotated element (@MyJavaAnnotation)  [_AnnotationIssue]
                    use(InnerApi.CONSTANT); // ERROR 1A and 1B
                                 ~~~~~~~~
            src/test/usage/JavaUsage.java:10: Error: FIELD_REFERENCE_OUTER_CLASS usage of annotated element (@MyKotlinAnnotation)  [_AnnotationIssue]
                    use(InnerApi.CONSTANT); // ERROR 1A and 1B
                                 ~~~~~~~~
            src/test/usage/JavaUsage.java:11: Error: FIELD_REFERENCE_OUTER_CLASS usage of annotated element (@MyJavaAnnotation)  [_AnnotationIssue]
                    use(CONSTANT);          // ERROR 2A and 2B
                        ~~~~~~~~
            src/test/usage/JavaUsage.java:11: Error: FIELD_REFERENCE_OUTER_CLASS usage of annotated element (@MyKotlinAnnotation)  [_AnnotationIssue]
                    use(CONSTANT);          // ERROR 2A and 2B
                        ~~~~~~~~
            src/test/usage/JavaUsage.java:12: Error: METHOD_CALL_OUTER_CLASS usage of annotated element (@MyJavaAnnotation)  [_AnnotationIssue]
                    use(innerApi.method()); // ERROR 3A and 3B
                        ~~~~~~~~~~~~~~~~~
            src/test/usage/JavaUsage.java:12: Error: METHOD_CALL_OUTER_CLASS usage of annotated element (@MyKotlinAnnotation)  [_AnnotationIssue]
                    use(innerApi.method()); // ERROR 3A and 3B
                        ~~~~~~~~~~~~~~~~~
            src/test/usage/JavaUsage.java:13: Error: METHOD_CALL_OUTER_CLASS usage of annotated element (@MyJavaAnnotation)  [_AnnotationIssue]
                    use(method());          // ERROR 4A and 4B
                        ~~~~~~~~
            src/test/usage/JavaUsage.java:13: Error: METHOD_CALL_OUTER_CLASS usage of annotated element (@MyKotlinAnnotation)  [_AnnotationIssue]
                    use(method());          // ERROR 4A and 4B
                        ~~~~~~~~
            src/test/usage/KotlinUsage.kt:10: Error: FIELD_REFERENCE_OUTER_CLASS usage of annotated element (@MyJavaAnnotation)  [_AnnotationIssue]
                    use(InnerApi.CONSTANT)     // ERROR 5A and 5B
                                 ~~~~~~~~
            src/test/usage/KotlinUsage.kt:10: Error: FIELD_REFERENCE_OUTER_CLASS usage of annotated element (@MyKotlinAnnotation)  [_AnnotationIssue]
                    use(InnerApi.CONSTANT)     // ERROR 5A and 5B
                                 ~~~~~~~~
            src/test/usage/KotlinUsage.kt:11: Error: FIELD_REFERENCE_OUTER_CLASS usage of annotated element (@MyJavaAnnotation)  [_AnnotationIssue]
                    use(CONSTANT)              // ERROR 6A and 6B
                        ~~~~~~~~
            src/test/usage/KotlinUsage.kt:11: Error: FIELD_REFERENCE_OUTER_CLASS usage of annotated element (@MyKotlinAnnotation)  [_AnnotationIssue]
                    use(CONSTANT)              // ERROR 6A and 6B
                        ~~~~~~~~
            src/test/usage/KotlinUsage.kt:12: Error: METHOD_CALL_OUTER_CLASS usage of annotated element (@MyJavaAnnotation)  [_AnnotationIssue]
                    use(InnerApi.method())     // ERROR 7A and 7B
                        ~~~~~~~~~~~~~~~~~
            src/test/usage/KotlinUsage.kt:12: Error: METHOD_CALL_OUTER_CLASS usage of annotated element (@MyKotlinAnnotation)  [_AnnotationIssue]
                    use(InnerApi.method())     // ERROR 7A and 7B
                        ~~~~~~~~~~~~~~~~~
            src/test/usage/KotlinUsage.kt:13: Error: METHOD_CALL_OUTER_CLASS usage of annotated element (@MyJavaAnnotation)  [_AnnotationIssue]
                    use(method())              // ERROR 8A and 8B
                        ~~~~~~~~
            src/test/usage/KotlinUsage.kt:13: Error: METHOD_CALL_OUTER_CLASS usage of annotated element (@MyKotlinAnnotation)  [_AnnotationIssue]
                    use(method())              // ERROR 8A and 8B
                        ~~~~~~~~
            16 errors, 0 warnings
            """
        )
    }

    @Test
    fun testClassReference() {
        lint().files(
            java(
                """
                package test.api;
                import pkg.java.MyJavaAnnotation;

                @MyJavaAnnotation
                public class Api {
                    public static class InnerApi {
                    }
                }
                """
            ).indented(),
            java(
                """
                package test.usage;
                import test.api.Api.InnerApi;

                public class JavaUsage {
                    private void use(Object o) { }
                    public void test() {
                        use(InnerApi.class); // ERROR1
                    }
                }
                """
            ).indented(),
            kotlin(
                """
                package test.usage
                import test.api.Api.InnerApi

                private fun use(o: Any) {}
                fun test() {
                    use(InnerApi::class.java)  // ERROR2
                }
                """
            ).indented(),
            javaAnnotation
        ).run().expect(
            """
            src/test/usage/JavaUsage.java:7: Error: CLASS_REFERENCE usage of annotated element (@MyJavaAnnotation)  [_AnnotationIssue]
                    use(InnerApi.class); // ERROR1
                        ~~~~~~~~~~~~~~
            src/test/usage/test.kt:6: Error: CLASS_REFERENCE usage of annotated element (@MyJavaAnnotation)  [_AnnotationIssue]
                use(InnerApi::class.java)  // ERROR2
                    ~~~~~~~~~~~~~~~
            2 errors, 0 warnings
            """
        )
    }

    @Test
    fun testObjectLiteral() {
        lint().files(
            java(
                """
                package test.api;
                import pkg.java.MyJavaAnnotation;

                @MyJavaAnnotation
                public class Api {
                    public static class InnerApi {
                    }
                }
                """
            ).indented(),
            java(
                """
                package test.usage;
                import test.api.Api.InnerApi;
                public class JavaUsage {
                    public void test() {
                        new InnerApi(); // ERROR1
                        new InnerApi() { }; // ERROR2
                    }
                }
                """
            ).indented(),
            kotlin(
                """
                package test.usage
                import test.api.Api.InnerApi
                fun test() {
                    InnerApi() // ERROR3
                    object : InnerApi() { } // ERROR4
                }
                """
            ).indented(),
            javaAnnotation
        ).run().expect(
            """
            src/test/usage/JavaUsage.java:5: Error: METHOD_CALL_OUTER_CLASS usage of annotated element (@MyJavaAnnotation)  [_AnnotationIssue]
                    new InnerApi(); // ERROR1
                    ~~~~~~~~~~~~~~
            src/test/usage/JavaUsage.java:6: Error: METHOD_CALL_OUTER_CLASS usage of annotated element (@MyJavaAnnotation)  [_AnnotationIssue]
                    new InnerApi() { }; // ERROR2
                    ~~~~~~~~~~~~~~~~~~
            src/test/usage/test.kt:4: Error: METHOD_CALL_OUTER_CLASS usage of annotated element (@MyJavaAnnotation)  [_AnnotationIssue]
                InnerApi() // ERROR3
                ~~~~~~~~~~
            src/test/usage/test.kt:5: Error: METHOD_CALL_OUTER_CLASS usage of annotated element (@MyJavaAnnotation)  [_AnnotationIssue]
                object : InnerApi() { } // ERROR4
                ~~~~~~~~~~~~~~~~~~~~~~~
            4 errors, 0 warnings
            """
        )
    }

    @Test
    fun testFieldPackageReference() {
        lint().files(
            java(
                """
                package test.api;
                public class Api {
                    public Object field = null;
                }
                """
            ).indented(),
            bytecode(
                "libs/packageinfoclass.jar",
                java(
                    "src/test/api/package-info.java",
                    """
                    @MyJavaAnnotation
                    package test.api;
                    import pkg.java.MyJavaAnnotation;
                    """
                ).indented(),
                0x1373820f,
                """
                test/api/package-info.class:
                H4sIAAAAAAAAADv1b9c+BgYGcwZOdgZ2dgYORgau4PzSouRUt8ycVEYGwYLE
                5OzE9FTdzLy0fL2sxLJERgbpoNK8kszcVM+8sszizKScVMe8vPySxJLM/Lxi
                oKxPQXa6Pkilvm+lF5BCyFozMoiWpBaX6CcWZOojG8zIIADWkJOYl67vn5SV
                mlwixsDAyMDEAAFMDMxgkoWBFUizAWXYGBgAA/vgtboAAAA=
                """
            ),
            java(
                """
                package test.usage;
                import test.api.Api;
                public class Usage {
                    private void use(Object o) { }
                    public void test(Api api) {
                        use(api.field);
                    }
                }
                """
            ).indented(),
            javaAnnotation
        ).testModes(TestMode.DEFAULT).run().expect(
            """
            src/test/usage/Usage.java:6: Error: FIELD_REFERENCE_PACKAGE usage of annotated element (@MyJavaAnnotation)  [_AnnotationIssue]
                    use(api.field);
                            ~~~~~
            1 errors, 0 warnings
            """
        )
    }

    // Simple detector which just flags annotation references
    @SuppressWarnings("ALL")
    class MyAnnotationDetector : Detector(), Detector.UastScanner {
        override fun applicableAnnotations(): List<String> {
            return listOf("pkg.java.MyJavaAnnotation", "pkg.kotlin.MyKotlinAnnotation")
        }

        override fun visitAnnotationUsage(
            context: JavaContext,
            usage: UElement,
            type: AnnotationUsageType,
            annotation: UAnnotation,
            qualifiedName: String,
            method: PsiMethod?,
            referenced: PsiElement?,
            annotations: List<UAnnotation>,
            allMemberAnnotations: List<UAnnotation>,
            allClassAnnotations: List<UAnnotation>,
            allPackageAnnotations: List<UAnnotation>
        ) {
            val name = qualifiedName.substring((qualifiedName.lastIndexOf('.') + 1))
            val message = "`${type.name}` usage of annotated element (`@$name`) "
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
