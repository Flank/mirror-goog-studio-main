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
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationOrigin
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LocationType
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
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
            src/test/pkg/JavaUsage.java:7: Error: METHOD_CALL usage associated with @MyJavaAnnotation on METHOD [_AnnotationIssue]
                    new JavaApi().method1();
                    ~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/JavaUsage.java:8: Error: METHOD_CALL usage associated with @MyKotlinAnnotation on METHOD [_AnnotationIssue]
                    new JavaApi().method2();
                    ~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/JavaUsage.java:9: Error: METHOD_CALL usage associated with @MyJavaAnnotation on METHOD [_AnnotationIssue]
                    new KotlinApi().method1();
                    ~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/JavaUsage.java:10: Error: METHOD_CALL usage associated with @MyKotlinAnnotation on METHOD [_AnnotationIssue]
                    new KotlinApi().method2();
                    ~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/KotlinUsage.kt:7: Error: METHOD_CALL usage associated with @MyJavaAnnotation on METHOD [_AnnotationIssue]
                    JavaApi().method1()
                    ~~~~~~~~~~~~~~~~~~~
            src/test/pkg/KotlinUsage.kt:8: Error: METHOD_CALL usage associated with @MyKotlinAnnotation on METHOD [_AnnotationIssue]
                    JavaApi().method2()
                    ~~~~~~~~~~~~~~~~~~~
            src/test/pkg/KotlinUsage.kt:9: Error: METHOD_CALL usage associated with @MyJavaAnnotation on METHOD [_AnnotationIssue]
                    KotlinApi().method1()
                    ~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/KotlinUsage.kt:10: Error: METHOD_CALL usage associated with @MyKotlinAnnotation on METHOD [_AnnotationIssue]
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
            src/test/usage/Usage.java:6: Error: FIELD_REFERENCE usage associated with @MyJavaAnnotation on CLASS [_AnnotationIssue]
                    use(api.field);      // ERROR 1A and 1B
                            ~~~~~
            src/test/usage/Usage.java:6: Error: FIELD_REFERENCE usage associated with @MyKotlinAnnotation on CLASS [_AnnotationIssue]
                    use(api.field);      // ERROR 1A and 1B
                            ~~~~~
            src/test/usage/Usage.java:7: Error: FIELD_REFERENCE usage associated with @MyJavaAnnotation on CLASS [_AnnotationIssue]
                    use(api.next.field); // ERROR 2A, 2B on next, 3A, 3B on field
                            ~~~~
            src/test/usage/Usage.java:7: Error: FIELD_REFERENCE usage associated with @MyJavaAnnotation on CLASS [_AnnotationIssue]
                    use(api.next.field); // ERROR 2A, 2B on next, 3A, 3B on field
                                 ~~~~~
            src/test/usage/Usage.java:7: Error: FIELD_REFERENCE usage associated with @MyKotlinAnnotation on CLASS [_AnnotationIssue]
                    use(api.next.field); // ERROR 2A, 2B on next, 3A, 3B on field
                            ~~~~
            src/test/usage/Usage.java:7: Error: FIELD_REFERENCE usage associated with @MyKotlinAnnotation on CLASS [_AnnotationIssue]
                    use(api.next.field); // ERROR 2A, 2B on next, 3A, 3B on field
                                 ~~~~~
            src/test/usage/test.kt:5: Error: FIELD_REFERENCE usage associated with @MyJavaAnnotation on CLASS [_AnnotationIssue]
                use(api.field)       // ERROR 4A and 4B
                        ~~~~~
            src/test/usage/test.kt:5: Error: FIELD_REFERENCE usage associated with @MyKotlinAnnotation on CLASS [_AnnotationIssue]
                use(api.field)       // ERROR 4A and 4B
                        ~~~~~
            src/test/usage/test.kt:6: Error: FIELD_REFERENCE usage associated with @MyJavaAnnotation on CLASS [_AnnotationIssue]
                use(api.next.field)  // ERROR 5A, 5B on next, 6A, 6B on field
                        ~~~~
            src/test/usage/test.kt:6: Error: FIELD_REFERENCE usage associated with @MyJavaAnnotation on CLASS [_AnnotationIssue]
                use(api.next.field)  // ERROR 5A, 5B on next, 6A, 6B on field
                             ~~~~~
            src/test/usage/test.kt:6: Error: FIELD_REFERENCE usage associated with @MyKotlinAnnotation on CLASS [_AnnotationIssue]
                use(api.next.field)  // ERROR 5A, 5B on next, 6A, 6B on field
                        ~~~~
            src/test/usage/test.kt:6: Error: FIELD_REFERENCE usage associated with @MyKotlinAnnotation on CLASS [_AnnotationIssue]
                use(api.next.field)  // ERROR 5A, 5B on next, 6A, 6B on field
                             ~~~~~
            12 errors, 0 warnings
            """
        )
    }

    @Test
    fun testFileLevelAnnotations() {
        lint().files(
            kotlin(
                """
                package pkg.kotlin
                @Target(AnnotationTarget.FILE)
                annotation class MyKotlinAnnotation
                """
            ).indented(),
            java(
                """
                    package test.pkg;

                    public class JavaUsage {
                        public void test() {
                            new KotlinApi().method();
                        }
                    }
                    """
            ).indented(),
            kotlin(
                """
                    package test.pkg

                    class KotlinUsage {
                        fun test() {
                            KotlinApi().method()
                            method2()
                        }
                    }
                    """
            ).indented(),
            kotlin(
                """
                    @file:MyKotlinAnnotation
                    package test.pkg
                    import pkg.kotlin.MyKotlinAnnotation
                    class KotlinApi {
                        fun method() {
                        }
                    }
                    fun method2() { }
                    """
            ).indented(),
        ).run().expect(
            """
            src/test/pkg/JavaUsage.java:5: Error: METHOD_CALL usage associated with @MyKotlinAnnotation on FILE [_AnnotationIssue]
                    new KotlinApi().method();
                    ~~~~~~~~~~~~~~~
            src/test/pkg/JavaUsage.java:5: Error: METHOD_CALL usage associated with @MyKotlinAnnotation on FILE [_AnnotationIssue]
                    new KotlinApi().method();
                    ~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/KotlinUsage.kt:5: Error: METHOD_CALL usage associated with @MyKotlinAnnotation on FILE [_AnnotationIssue]
                    KotlinApi().method()
                    ~~~~~~~~~~~
            src/test/pkg/KotlinUsage.kt:5: Error: METHOD_CALL usage associated with @MyKotlinAnnotation on FILE [_AnnotationIssue]
                    KotlinApi().method()
                    ~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/KotlinUsage.kt:6: Error: METHOD_CALL usage associated with @MyKotlinAnnotation on CLASS [_AnnotationIssue]
                    method2()
                    ~~~~~~~~~
            5 errors, 0 warnings
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
            src/test/usage/JavaUsage.java:10: Error: FIELD_REFERENCE usage associated with @MyJavaAnnotation on OUTER_CLASS [_AnnotationIssue]
                    use(InnerApi.CONSTANT); // ERROR 1A and 1B
                                 ~~~~~~~~
            src/test/usage/JavaUsage.java:10: Error: FIELD_REFERENCE usage associated with @MyKotlinAnnotation on OUTER_CLASS [_AnnotationIssue]
                    use(InnerApi.CONSTANT); // ERROR 1A and 1B
                                 ~~~~~~~~
            src/test/usage/JavaUsage.java:11: Error: FIELD_REFERENCE usage associated with @MyJavaAnnotation on OUTER_CLASS [_AnnotationIssue]
                    use(CONSTANT);          // ERROR 2A and 2B
                        ~~~~~~~~
            src/test/usage/JavaUsage.java:11: Error: FIELD_REFERENCE usage associated with @MyKotlinAnnotation on OUTER_CLASS [_AnnotationIssue]
                    use(CONSTANT);          // ERROR 2A and 2B
                        ~~~~~~~~
            src/test/usage/JavaUsage.java:12: Error: METHOD_CALL usage associated with @MyJavaAnnotation on OUTER_CLASS [_AnnotationIssue]
                    use(innerApi.method()); // ERROR 3A and 3B
                        ~~~~~~~~~~~~~~~~~
            src/test/usage/JavaUsage.java:12: Error: METHOD_CALL usage associated with @MyKotlinAnnotation on OUTER_CLASS [_AnnotationIssue]
                    use(innerApi.method()); // ERROR 3A and 3B
                        ~~~~~~~~~~~~~~~~~
            src/test/usage/JavaUsage.java:13: Error: METHOD_CALL usage associated with @MyJavaAnnotation on OUTER_CLASS [_AnnotationIssue]
                    use(method());          // ERROR 4A and 4B
                        ~~~~~~~~
            src/test/usage/JavaUsage.java:13: Error: METHOD_CALL usage associated with @MyKotlinAnnotation on OUTER_CLASS [_AnnotationIssue]
                    use(method());          // ERROR 4A and 4B
                        ~~~~~~~~
            src/test/usage/KotlinUsage.kt:10: Error: FIELD_REFERENCE usage associated with @MyJavaAnnotation on OUTER_CLASS [_AnnotationIssue]
                    use(InnerApi.CONSTANT)     // ERROR 5A and 5B
                                 ~~~~~~~~
            src/test/usage/KotlinUsage.kt:10: Error: FIELD_REFERENCE usage associated with @MyKotlinAnnotation on OUTER_CLASS [_AnnotationIssue]
                    use(InnerApi.CONSTANT)     // ERROR 5A and 5B
                                 ~~~~~~~~
            src/test/usage/KotlinUsage.kt:11: Error: FIELD_REFERENCE usage associated with @MyJavaAnnotation on OUTER_CLASS [_AnnotationIssue]
                    use(CONSTANT)              // ERROR 6A and 6B
                        ~~~~~~~~
            src/test/usage/KotlinUsage.kt:11: Error: FIELD_REFERENCE usage associated with @MyKotlinAnnotation on OUTER_CLASS [_AnnotationIssue]
                    use(CONSTANT)              // ERROR 6A and 6B
                        ~~~~~~~~
            src/test/usage/KotlinUsage.kt:12: Error: METHOD_CALL usage associated with @MyJavaAnnotation on OUTER_CLASS [_AnnotationIssue]
                    use(InnerApi.method())     // ERROR 7A and 7B
                        ~~~~~~~~~~~~~~~~~
            src/test/usage/KotlinUsage.kt:12: Error: METHOD_CALL usage associated with @MyKotlinAnnotation on OUTER_CLASS [_AnnotationIssue]
                    use(InnerApi.method())     // ERROR 7A and 7B
                        ~~~~~~~~~~~~~~~~~
            src/test/usage/KotlinUsage.kt:13: Error: METHOD_CALL usage associated with @MyJavaAnnotation on OUTER_CLASS [_AnnotationIssue]
                    use(method())              // ERROR 8A and 8B
                        ~~~~~~~~
            src/test/usage/KotlinUsage.kt:13: Error: METHOD_CALL usage associated with @MyKotlinAnnotation on OUTER_CLASS [_AnnotationIssue]
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
            src/test/usage/JavaUsage.java:7: Error: CLASS_REFERENCE usage associated with @MyJavaAnnotation on OUTER_CLASS [_AnnotationIssue]
                    use(InnerApi.class); // ERROR1
                        ~~~~~~~~~~~~~~
            src/test/usage/test.kt:6: Error: CLASS_REFERENCE usage associated with @MyJavaAnnotation on OUTER_CLASS [_AnnotationIssue]
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
            src/test/usage/JavaUsage.java:5: Error: METHOD_CALL usage associated with @MyJavaAnnotation on OUTER_CLASS [_AnnotationIssue]
                    new InnerApi(); // ERROR1
                    ~~~~~~~~~~~~~~
            src/test/usage/JavaUsage.java:6: Error: METHOD_CALL usage associated with @MyJavaAnnotation on OUTER_CLASS [_AnnotationIssue]
                    new InnerApi() { }; // ERROR2
                    ~~~~~~~~~~~~~~~~~~
            src/test/usage/test.kt:4: Error: METHOD_CALL usage associated with @MyJavaAnnotation on OUTER_CLASS [_AnnotationIssue]
                InnerApi() // ERROR3
                ~~~~~~~~~~
            src/test/usage/test.kt:5: Error: METHOD_CALL usage associated with @MyJavaAnnotation on OUTER_CLASS [_AnnotationIssue]
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
        ).run().expect(
            """
            src/test/usage/Usage.java:6: Error: FIELD_REFERENCE usage associated with @MyJavaAnnotation on PACKAGE [_AnnotationIssue]
                    use(api.field);
                            ~~~~~
            libs/packageinfoclass.jar!/test/api/package-info.class: Error: Incident reported on package annotation [_AnnotationIssue]
            2 errors, 0 warnings
            """
        )
    }

    @Test
    fun testOverride() {
        lint().files(
            java(
                """
                package test.api;
                import pkg.java.MyJavaAnnotation;
                import pkg.kotlin.MyKotlinAnnotation;

                public interface StableInterface {
                    @MyJavaAnnotation
                    @MyKotlinAnnotation
                    void experimentalMethod();
                }
                """
            ).indented(),
            java(
                """
                package test.api;
                class ConcreteStableInterface implements StableInterface {
                    @Override
                    public void experimentalMethod() {} // ERROR 1A and 1B
                }
                """
            ).indented(),
            kotlin(
                """
                package test.api
                class ConcreteStableInterface2 : StableInterface {
                    override fun experimentalMethod() {} // ERROR 2A and 2B
                }
                """
            ).indented(),
            kotlin(
                """
                package test.pkg
                import pkg.kotlin.MyKotlinAnnotation

                interface I {
                    fun m() {}
                }

                // Make sure outer annotations are inherited into the C override of m
                @MyKotlinAnnotation
                open class A {
                    open class B : I {
                        override fun m() {}
                    }
                }

                open class C : A.B() {
                    override fun m() {}
                }
                """
            ).indented(),
            javaAnnotation,
            kotlinAnnotation
        ).run().expect(
            """
            src/test/api/ConcreteStableInterface.java:4: Error: METHOD_OVERRIDE usage associated with @MyJavaAnnotation on METHOD [_AnnotationIssue]
                public void experimentalMethod() {} // ERROR 1A and 1B
                            ~~~~~~~~~~~~~~~~~~
            src/test/api/ConcreteStableInterface.java:4: Error: METHOD_OVERRIDE usage associated with @MyKotlinAnnotation on METHOD [_AnnotationIssue]
                public void experimentalMethod() {} // ERROR 1A and 1B
                            ~~~~~~~~~~~~~~~~~~
            src/test/api/ConcreteStableInterface2.kt:3: Error: METHOD_OVERRIDE usage associated with @MyJavaAnnotation on METHOD [_AnnotationIssue]
                override fun experimentalMethod() {} // ERROR 2A and 2B
                             ~~~~~~~~~~~~~~~~~~
            src/test/api/ConcreteStableInterface2.kt:3: Error: METHOD_OVERRIDE usage associated with @MyKotlinAnnotation on METHOD [_AnnotationIssue]
                override fun experimentalMethod() {} // ERROR 2A and 2B
                             ~~~~~~~~~~~~~~~~~~
            src/test/pkg/I.kt:16: Error: METHOD_CALL usage associated with @MyKotlinAnnotation on OUTER_CLASS [_AnnotationIssue]
            open class C : A.B() {
                           ~~~~~
            src/test/pkg/I.kt:17: Error: METHOD_OVERRIDE usage associated with @MyKotlinAnnotation on OUTER_CLASS [_AnnotationIssue]
                override fun m() {}
                             ~
            6 errors, 0 warnings
            """
        )
    }

    @Test
    fun test195014464() {
        @Suppress("MemberVisibilityCanBePrivate")
        lint().files(
            kotlin(
                """
                package test.usage
                import pkg.kotlin.MyKotlinAnnotation

                class FooBar {
                    infix fun infixFun(@MyKotlinAnnotation foo: Int) {  }
                    operator fun plus(@MyKotlinAnnotation foo: Int) {  }
                    infix fun String.extensionInfixFun(@MyKotlinAnnotation foo: Int) {  }
                    infix fun @receiver:MyKotlinAnnotation String.extensionInfixFun2(foo: Int) {  }
                    operator fun plusAssign(@MyKotlinAnnotation foo: Int) {  }

                    fun testBinary() {
                        val bar = ""
                        this infixFun 0 // visit 0
                        this + 0 // visit 0
                        bar extensionInfixFun 0 // visit 0
                        bar extensionInfixFun2 0 // visit bar
                        this += 0 // visit 0
                    }
                }
                """
            ).indented(),
            javaAnnotation,
            kotlinAnnotation
        ).run().expect(
            """
            src/test/usage/FooBar.kt:13: Error: METHOD_CALL_PARAMETER usage associated with @MyKotlinAnnotation on PARAMETER [_AnnotationIssue]
                    this infixFun 0 // visit 0
                                  ~
            src/test/usage/FooBar.kt:14: Error: METHOD_CALL_PARAMETER usage associated with @MyKotlinAnnotation on PARAMETER [_AnnotationIssue]
                    this + 0 // visit 0
                           ~
            src/test/usage/FooBar.kt:15: Error: METHOD_CALL_PARAMETER usage associated with @MyKotlinAnnotation on PARAMETER [_AnnotationIssue]
                    bar extensionInfixFun 0 // visit 0
                                          ~
            src/test/usage/FooBar.kt:16: Error: METHOD_CALL_PARAMETER usage associated with @MyKotlinAnnotation on PARAMETER [_AnnotationIssue]
                    bar extensionInfixFun2 0 // visit bar
                    ~~~
            src/test/usage/FooBar.kt:17: Error: METHOD_CALL_PARAMETER usage associated with @MyKotlinAnnotation on PARAMETER [_AnnotationIssue]
                    this += 0 // visit 0
                            ~
            5 errors, 0 warnings
            """
        )
    }

    @Test
    fun testBinaryOperators() {
        lint().files(
            kotlin(
                """
                package test.pkg
                import pkg.kotlin.MyKotlinAnnotation

                class Resource {
                    operator fun contains(@MyKotlinAnnotation id: Int): Boolean = false
                    operator fun times(@MyKotlinAnnotation id: Int): Int = 0
                    operator fun rangeTo(@MyKotlinAnnotation id: Int): Int = 0
                }
                class Resource2

                operator fun Resource2.contains(@MyKotlinAnnotation id: Int): Boolean = false
                operator fun Resource2.rangeTo(@MyKotlinAnnotation id: Int): Int = 0
                operator fun @receiver:MyKotlinAnnotation Resource2.times(id: Int): Int = 0

                fun testBinary(resource: Resource, resource2: Resource2, color: Int) {
                    // Here we should only be visiting the "color" argument, except for in the
                    // last multiplication where we've annotated the receiver instead
                    println(color in resource) // visit color
                    println(color !in resource) // visit color
                    println(resource * color) // visit color
                    println(resource..color) // visit color

                    println(color in resource2) // visit color
                    println(resource2..color) // visit color
                    println(resource2 * color) // visit *resource*
                }
                """
            ).indented(),
            javaAnnotation,
            kotlinAnnotation
        ).run().expect(
            """
            src/test/pkg/Resource.kt:18: Error: METHOD_CALL_PARAMETER usage associated with @MyKotlinAnnotation on PARAMETER [_AnnotationIssue]
                println(color in resource) // visit color
                        ~~~~~
            src/test/pkg/Resource.kt:19: Error: METHOD_CALL_PARAMETER usage associated with @MyKotlinAnnotation on PARAMETER [_AnnotationIssue]
                println(color !in resource) // visit color
                        ~~~~~
            src/test/pkg/Resource.kt:20: Error: METHOD_CALL_PARAMETER usage associated with @MyKotlinAnnotation on PARAMETER [_AnnotationIssue]
                println(resource * color) // visit color
                                   ~~~~~
            src/test/pkg/Resource.kt:21: Error: METHOD_CALL_PARAMETER usage associated with @MyKotlinAnnotation on PARAMETER [_AnnotationIssue]
                println(resource..color) // visit color
                                  ~~~~~
            src/test/pkg/Resource.kt:23: Error: METHOD_CALL_PARAMETER usage associated with @MyKotlinAnnotation on PARAMETER [_AnnotationIssue]
                println(color in resource2) // visit color
                        ~~~~~
            src/test/pkg/Resource.kt:24: Error: METHOD_CALL_PARAMETER usage associated with @MyKotlinAnnotation on PARAMETER [_AnnotationIssue]
                println(resource2..color) // visit color
                                   ~~~~~
            src/test/pkg/Resource.kt:25: Error: METHOD_CALL_PARAMETER usage associated with @MyKotlinAnnotation on PARAMETER [_AnnotationIssue]
                println(resource2 * color) // visit *resource*
                        ~~~~~~~~~
            7 errors, 0 warnings
            """
        )
    }

    @Test
    fun testArrayAccess() {
        lint().files(
            kotlin(
                """
                package test.pkg
                import pkg.kotlin.MyKotlinAnnotation

                class Resource {
                    operator fun get(@MyKotlinAnnotation key: Int): String = ""
                    operator fun set(@MyKotlinAnnotation key: Int, value: String) {}
                }
                class Resource2 {
                    operator fun get(@MyKotlinAnnotation key: Int): String = ""
                    operator fun set(key: Int, @MyKotlinAnnotation value: String) {}
                }
                class Resource3
                operator fun Resource3.get(@MyKotlinAnnotation id: Int): String = ""
                operator fun Resource3.set(@MyKotlinAnnotation id: Int, value: String) {}
                class Resource4
                operator fun Resource4.get(id0: Int, @MyKotlinAnnotation id: Int): String = ""
                operator fun Resource4.set(id0: Int, @MyKotlinAnnotation id: Int, value: String) {}

                fun testArray(resource: Resource, resource2: Resource2, resource3: Resource3, resource4: Resource4) {
                    val x = resource[5] // visit 5
                    resource[5] = x // visit 5
                    val y = resource2[5] // visit 5
                    resource2[5] = y // visit y
                    val z = resource3[5] // visit 5
                    resource3[5] = z // visit 5
                    val w = resource4[0, 5] // visit 5
                    resource4[0, 5] = w // visit 5
                }
                """
            ).indented(),
            javaAnnotation,
            kotlinAnnotation
        ).run().expect(
            """
            src/test/pkg/Resource.kt:20: Error: METHOD_CALL_PARAMETER usage associated with @MyKotlinAnnotation on PARAMETER [_AnnotationIssue]
                val x = resource[5] // visit 5
                                 ~
            src/test/pkg/Resource.kt:21: Error: METHOD_CALL_PARAMETER usage associated with @MyKotlinAnnotation on PARAMETER [_AnnotationIssue]
                resource[5] = x // visit 5
                         ~
            src/test/pkg/Resource.kt:22: Error: METHOD_CALL_PARAMETER usage associated with @MyKotlinAnnotation on PARAMETER [_AnnotationIssue]
                val y = resource2[5] // visit 5
                                  ~
            src/test/pkg/Resource.kt:23: Error: METHOD_CALL_PARAMETER usage associated with @MyKotlinAnnotation on PARAMETER [_AnnotationIssue]
                resource2[5] = y // visit y
                               ~
            src/test/pkg/Resource.kt:24: Error: METHOD_CALL_PARAMETER usage associated with @MyKotlinAnnotation on PARAMETER [_AnnotationIssue]
                val z = resource3[5] // visit 5
                                  ~
            src/test/pkg/Resource.kt:25: Error: METHOD_CALL_PARAMETER usage associated with @MyKotlinAnnotation on PARAMETER [_AnnotationIssue]
                resource3[5] = z // visit 5
                          ~
            src/test/pkg/Resource.kt:26: Error: METHOD_CALL_PARAMETER usage associated with @MyKotlinAnnotation on PARAMETER [_AnnotationIssue]
                val w = resource4[0, 5] // visit 5
                                     ~
            src/test/pkg/Resource.kt:27: Error: METHOD_CALL_PARAMETER usage associated with @MyKotlinAnnotation on PARAMETER [_AnnotationIssue]
                resource4[0, 5] = w // visit 5
                             ~
            8 errors, 0 warnings
            """
        )
    }

    @Test
    fun testResolveExtensionArrayAccessFunction() {
        lint().files(
            bytecode(
                "libs/library.jar",
                kotlin(
                    """
                    package test.pkg1
                    import pkg.kotlin.MyKotlinAnnotation
                    class Resource
                    operator fun Resource.get(@MyKotlinAnnotation id: Int): String = ""
                    operator fun Resource.set(@MyKotlinAnnotation id: Int, value: String) {}
                """
                ),
                0x96bee228,
                """
                META-INF/main.kotlin_module:
                H4sIAAAAAAAAAGNgYGBmYGBgBGJWKM3AJcTFUZJaXKJXkJ0uxBYCZHmXcIlz
                ccLEDIW4glKL80uLklO9S5QYtBgA5F2hGUUAAAA=
                """,
                """
                test/pkg1/Resource.class:
                H4sIAAAAAAAAAGVRwU4CMRScdmHRFWVBVFDjWT24QLxpTNTEhGTVBA0XTgUa
                LCxdQwvxyLf4B55MPBji0Y8yvl31ZA+TNzOvr/PSz6+3dwDH2GUoWWls8Dga
                1IOWNPF00pM5MAZ/KGYiiIQeBLfdoezZHBwG91RpZc8YnP2Ddh5ZuB4yyDFk
                7IMyDOXw/7gThmI4im2kdHAtregLK0jj45lDIVgCWQY2IulJJaxGVb/OsLeY
                ex6vcI/7VC3mlcW8wWvsIvvx7HKfJ10NRhOw8vfU0chSlMu4LxkKodLyZjru
                ysm96EaklMK4J6K2mKiE/4reXXrzSiWk2ppqq8ayrYwi91zr2AqrYm1QB6dN
                k0NZk8UJt4gFKacVDl+x9EIFR4XQTcUMqoT5nwYsw0v97RQ3sZP+AcUnL9+B
                08RqE2uEKCTgN1FEqQNmsI4y+QaewYaB+w30FRU7wAEAAA==
                """,
                """
                test/pkg1/ResourceKt.class:
                H4sIAAAAAAAAAG1Sz08TQRT+Zpe22/JrqUUoKCBUoRXZQryhJobEuKGgAcMF
                L9N2Uqbd7prdaaM3Tv49ejMeDPHoH2V8s1uKhc7hve+9+eZ9897Mn78/fwF4
                jl2GghKRcj51WrvOiYiCXtgQhyoDxmC3eZ87Hvdbzrt6WzQoazKYLaEYylu1
                u+f23XLt5sypCqXf2mfYqAVhy2kLVQ+59COH+36guJIB4eNAHfc8j1irNarl
                dALlSd85+nIYg9dDKjHSL9SFjF5ZyDKsDIjtfteRvhKhzz3H9bVkJBtRBpMM
                840L0egMFN7zkHcFERk2t2q3O9u/e+/y2RSmMZPDFGYZYGGOIVvSNyjFEyiM
                GwCDIZsMzKU5RZpVGT+ncXIMqT73esJCcagU18iPG+pc7XpUQvEmV1xrd/sm
                PSvTJkW36GhgUP6z1KhKqEkv/vHqcjF3dZkz7JmcsWgk0EqcjpYWbDJGlVWM
                qrFnWcw2KZp4+/ursbRqpwinR3f20nZG8zVDa+wxTF73utOhDiYOgqZgmK1J
                Xxz3unURfuB1T+jWggb3zngodTxILp/0fCW7wvX7MpKUuvkFEcP6YPcs2Ru+
                6wipdLvEeFruNL7jG6lli6OF/yNiFwYmoJeBIlJIU1Sm6CXFBvlsJZ/7ATuf
                /xZTKmTToA9L1KeE7yck3EMhLpLFPOUYtge8DPlnOq9pjL4asJOEyR5IdAGL
                MGPRQ6qmn3M6Ed3OL5H9PiI8RVYLryVELA+EpwfCGhXxgE5o2UlzKJsIz5pD
                4cQbcGK7hSr5A8o+pNZWzmG6WHWxRhaPXKxjw0UJj8/BIjzB5jmsCKkIcxEK
                EeZjsBDb4j8xfuP2ggQAAA==
                """
            ),
            kotlin(
                """
                package test.pkg
                import test.pkg1.Resource
                import test.pkg1.get
                import test.pkg1.set
                fun testArray(resource: Resource) {
                    val x = resource[5] // visit 5
                    resource[5] = x // visit 5
                }
                """
            ).indented(),
            kotlinAnnotation
        ).run().expect(
            """
            src/test/pkg/test.kt:6: Error: METHOD_CALL_PARAMETER usage associated with @MyKotlinAnnotation on PARAMETER [_AnnotationIssue]
                val x = resource[5] // visit 5
                                 ~
            src/test/pkg/test.kt:7: Error: METHOD_CALL_PARAMETER usage associated with @MyKotlinAnnotation on PARAMETER [_AnnotationIssue]
                resource[5] = x // visit 5
                         ~
            2 errors, 0 warnings
            """
        )
    }

    @Test
    fun testImplicitConstructor() {
        // Regression test for
        // 234779271: com.android.tools.lint.client.api.AnnotationHandler doesn't visit implicit constructor delegations
        lint().files(
            java(
                """
                package test.pkg;
                import pkg.kotlin.MyKotlinAnnotation;

                @SuppressWarnings({"InnerClassMayBeStatic", "unused"})
                public class Java {
                    class Parent {
                        @MyKotlinAnnotation
                        Parent() {
                        }

                        Parent(int i) {
                            this(); // (1) Invoked constructor is marked @MyKotlinAnnotation
                        }
                    }

                    class ChildDefaultConstructor extends Parent { // (2) Implicitly delegated constructor is marked @MyKotlinAnnotation
                    }

                    class ChildExplicitConstructor extends Parent {
                        ChildExplicitConstructor() { // (3) Implicitly invoked super constructor is marked @MyKotlinAnnotation, (4) Overrides annotated method
                        }

                        ChildExplicitConstructor(int a) {
                            super(); // (5) Invoked constructor is marked @MyKotlinAnnotation
                        }
                    }

                    class IndirectChildDefaultConstructor extends ChildDefaultConstructor { // (6) Implicitly invoked constructor is marked @MyKotlinAnnotation
                    }

                    class IndirectChildDefaultConstructor2 extends ChildDefaultConstructor {
                        IndirectChildDefaultConstructor2(int a) {
                            super(); // (7) Annotations on indirect implicit super constructor
                        }
                    }
                }
                """
            ).indented(),
            kotlin(
                """
                package test.pkg
                import pkg.kotlin.MyKotlinAnnotation;

                class Kotlin {
                    internal open inner class Parent @MyKotlinAnnotation constructor() {
                        constructor(i: Int) : this()  { // (8) Invoked constructor is marked @MyKotlinAnnotation
                        }
                    }

                    internal open inner class ChildDefaultConstructor : Parent() { // (9), (10) override and call of annotated constructor
                    }

                    internal inner class ChildExplicitConstructor : Parent {
                        constructor() { // (11), (12) Extending annotated constructor, and implicitly invoking it
                        }

                        constructor(a: Int) : super() { // (13) Invoked constructor is marked @MyKotlinAnnotation
                        }
                    }

                    internal inner class IndirectChildDefaultConstructor : ChildDefaultConstructor() { // (14) Implicitly invoked constructor is marked @MyKotlinAnnotation
                    }

                    internal inner class IndirectChildDefaultConstructor2(a: Int) : ChildDefaultConstructor() // (15) Annotations on indirect implicit super constructor
                }
                """
            ).indented(),
            kotlinAnnotation
        ).run().expect(
            """
            src/test/pkg/Java.java:12: Error: METHOD_CALL usage associated with @MyKotlinAnnotation on METHOD [_AnnotationIssue]
                        this(); // (1) Invoked constructor is marked @MyKotlinAnnotation
                        ~~~~~~
            src/test/pkg/Java.java:16: Error: IMPLICIT_CONSTRUCTOR usage associated with @MyKotlinAnnotation on METHOD [_AnnotationIssue]
                class ChildDefaultConstructor extends Parent { // (2) Implicitly delegated constructor is marked @MyKotlinAnnotation
                ^
            src/test/pkg/Java.java:20: Error: IMPLICIT_CONSTRUCTOR_CALL usage associated with @MyKotlinAnnotation on METHOD [_AnnotationIssue]
                    ChildExplicitConstructor() { // (3) Implicitly invoked super constructor is marked @MyKotlinAnnotation, (4) Overrides annotated method
                    ~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/Java.java:20: Error: METHOD_OVERRIDE usage associated with @MyKotlinAnnotation on METHOD [_AnnotationIssue]
                    ChildExplicitConstructor() { // (3) Implicitly invoked super constructor is marked @MyKotlinAnnotation, (4) Overrides annotated method
                    ~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/Java.java:24: Error: METHOD_CALL usage associated with @MyKotlinAnnotation on METHOD [_AnnotationIssue]
                        super(); // (5) Invoked constructor is marked @MyKotlinAnnotation
                        ~~~~~~~
            src/test/pkg/Java.java:28: Error: IMPLICIT_CONSTRUCTOR usage associated with @MyKotlinAnnotation on METHOD [_AnnotationIssue]
                class IndirectChildDefaultConstructor extends ChildDefaultConstructor { // (6) Implicitly invoked constructor is marked @MyKotlinAnnotation
                ^
            src/test/pkg/Java.java:33: Error: IMPLICIT_CONSTRUCTOR usage associated with @MyKotlinAnnotation on METHOD [_AnnotationIssue]
                        super(); // (7) Annotations on indirect implicit super constructor
                        ~~~~~~~
            src/test/pkg/Kotlin.kt:6: Error: METHOD_CALL usage associated with @MyKotlinAnnotation on METHOD [_AnnotationIssue]
                    constructor(i: Int) : this()  { // (8) Invoked constructor is marked @MyKotlinAnnotation
                                          ~~~~~~
            src/test/pkg/Kotlin.kt:10: Error: IMPLICIT_CONSTRUCTOR usage associated with @MyKotlinAnnotation on METHOD [_AnnotationIssue]
                internal open inner class ChildDefaultConstructor : Parent() { // (9), (10) override and call of annotated constructor
                ^
            src/test/pkg/Kotlin.kt:10: Error: METHOD_CALL usage associated with @MyKotlinAnnotation on METHOD [_AnnotationIssue]
                internal open inner class ChildDefaultConstructor : Parent() { // (9), (10) override and call of annotated constructor
                                                                    ~~~~~~~~
            src/test/pkg/Kotlin.kt:14: Error: METHOD_CALL usage associated with @MyKotlinAnnotation on METHOD [_AnnotationIssue]
                    constructor() { // (11), (12) Extending annotated constructor, and implicitly invoking it
                                 ^
            src/test/pkg/Kotlin.kt:14: Error: METHOD_OVERRIDE usage associated with @MyKotlinAnnotation on METHOD [_AnnotationIssue]
                    constructor() { // (11), (12) Extending annotated constructor, and implicitly invoking it
                    ~~~~~~~~~~~
            src/test/pkg/Kotlin.kt:17: Error: METHOD_CALL usage associated with @MyKotlinAnnotation on METHOD [_AnnotationIssue]
                    constructor(a: Int) : super() { // (13) Invoked constructor is marked @MyKotlinAnnotation
                                          ~~~~~~~
            src/test/pkg/Kotlin.kt:21: Error: IMPLICIT_CONSTRUCTOR usage associated with @MyKotlinAnnotation on METHOD [_AnnotationIssue]
                internal inner class IndirectChildDefaultConstructor : ChildDefaultConstructor() { // (14) Implicitly invoked constructor is marked @MyKotlinAnnotation
                ^
            src/test/pkg/Kotlin.kt:24: Error: IMPLICIT_CONSTRUCTOR usage associated with @MyKotlinAnnotation on METHOD [_AnnotationIssue]
                internal inner class IndirectChildDefaultConstructor2(a: Int) : ChildDefaultConstructor() // (15) Annotations on indirect implicit super constructor
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            15 errors, 0 warnings
            """
        )
    }

    // Simple detector which just flags annotation references
    @SuppressWarnings("ALL")
    class MyAnnotationDetector : Detector(), Detector.UastScanner {
        override fun applicableAnnotations(): List<String> {
            return listOf("pkg.java.MyJavaAnnotation", "pkg.kotlin.MyKotlinAnnotation")
        }

        override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean {
            return type != AnnotationUsageType.DEFINITION
        }

        override fun visitAnnotationUsage(
            context: JavaContext,
            element: UElement,
            annotationInfo: AnnotationInfo,
            usageInfo: AnnotationUsageInfo
        ) {
            if (annotationInfo.origin == AnnotationOrigin.PACKAGE) {
                val annotation = annotationInfo.annotation
                // Regression test for https://issuetracker.google.com/191286558: Make sure we can report
                // incidents on annotations from package info files without throwing an exception
                context.report(TEST_ISSUE, context.getLocation(annotation), "Incident reported on package annotation")
            }

            val name = annotationInfo.qualifiedName.substringAfterLast('.')
            val message = "`${usageInfo.type.name}` usage associated with `@$name` on ${annotationInfo.origin}"
            val locationType = if (element is UMethod) LocationType.NAME else LocationType.ALL
            val location = context.getLocation(element, locationType)
            context.report(TEST_ISSUE, element, location, message)
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
