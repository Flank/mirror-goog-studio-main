/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.lint.checks.RestrictToDetector.Companion.sameLibraryGroupPrefix
import com.android.tools.lint.checks.infrastructure.ProjectDescription
import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Project
import java.io.File

class RestrictToDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector = RestrictToDetector()

    fun testDocumentationExampleVisibleForTesting() {
        lint().files(
            kotlin(
                """
                import androidx.annotation.VisibleForTesting

                class ProductionCode {
                    fun compute() {
                        initialize() // OK
                    }

                    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
                    fun initialize() {
                    }
                }
                """
            ).indented(),
            kotlin(
                """
                class Code {
                    fun test() {
                        ProductionCode().initialize() // Not allowed; this method is intended to be private
                    }
                }
                """
            ),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            """
            src/Code.kt:4: Warning: This method should only be accessed from tests or within private scope [VisibleForTests]
                                    ProductionCode().initialize() // Not allowed; this method is intended to be private
                                                     ~~~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }

    fun testVisibleForTestingOnEnum() {
        lint().files(
            kotlin(
                """
                import com.google.common.annotations.VisibleForTesting

                class ProductionCode {
                    @VisibleForTesting
                    enum class COLOR {
                        RED,
                        GREEN,
                        BLUE
                    }

                    fun render() {
                        COLOR.values().forEach { println(it.name) } // OK
                    }
                }
                """
            ).indented(),
            kotlin(
                """
                class Code {
                    fun test() {
                        ProductionCode.COLOR.values().map { it.name to it.ordinal } // Not allowed
                    }
                }
                """
            ),
            guavaVisibleForTestingAnnotation
        ).run().expect(
            """
            src/Code.kt:4: Warning: This method should only be accessed from tests or within private scope [VisibleForTests]
                                    ProductionCode.COLOR.values().map { it.name to it.ordinal } // Not allowed
                                                         ~~~~~~
            0 errors, 1 warnings
            """
        )
    }

    fun testRestrictToSubClass() {
        val expected =
            """
            src/test/pkg/RestrictToSubclassTest.java:26: Error: Class1.onSomething can only be called from subclasses [RestrictedApi]
                        cls.onSomething();         // ERROR: Not from subclass
                            ~~~~~~~~~~~
            src/test/pkg/RestrictToSubclassTest.java:27: Error: Class1.counter can only be accessed from subclasses [RestrictedApi]
                        int counter = cls.counter; // ERROR: Not from subclass
                                          ~~~~~~~
            2 errors, 0 warnings
            """

        lint().files(
            java(
                """
                    package test.pkg;

                    import androidx.annotation.RestrictTo;

                    @SuppressWarnings("ClassNameDiffersFromFileName")
                    public class RestrictToSubclassTest {
                        public static class Class1 {
                            @RestrictTo(RestrictTo.Scope.SUBCLASSES)
                            public void onSomething() {
                            }

                            @RestrictTo(RestrictTo.Scope.SUBCLASSES)
                            public int counter;
                        }

                        public static class SubClass extends Class1 {
                            public void test1() {
                                onSomething(); // OK: Call from subclass
                                int counter = cls.counter; // OK: Reference from subclass
                            }
                        }

                        @SuppressWarnings("MethodMayBeStatic")
                        public static class NotSubClass {
                            public void test2(Class1 cls) {
                                cls.onSomething();         // ERROR: Not from subclass
                                int counter = cls.counter; // ERROR: Not from subclass
                            }
                        }
                    }
                    """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(expected)
    }

    fun testRestrictToGroupId() {
        val project = project().files(
            java(
                """
                package test.pkg;
                import library.pkg.internal.InternalClass;
                import library.pkg.Library;
                import library.pkg.PrivateClass;

                @SuppressWarnings("ClassNameDiffersFromFileName")
                public class TestLibrary {
                    public void test() {
                        Library.method(); // OK
                        Library.privateMethod(); // ERROR
                        PrivateClass.method(); // ERROR
                        InternalClass.method(); // ERROR
                    }

                    @Override
                    public method() {
                        super.method(); // ERROR
                    }
                }
                """
            ).indented(),

            java(
                "src/test/java/test/pkg/UnitTestLibrary.java",
                """
                package test.pkg;
                import library.pkg.PrivateClass;

                @SuppressWarnings("ClassNameDiffersFromFileName")
                public class UnitTestLibrary {
                    public void test() {
                        PrivateClass.method(); // Not enforced in tests
                    }
                }
                """
            ).indented(),

            base64gzip(
                "libs/exploded-aar/my.group.id/mylib/25.0.0-SNAPSHOT/jars/classes.jar",
                LIBRARY_BYTE_CODE
            ),
            gradle(
                """
                apply plugin: 'com.android.application'

                dependencies {
                    compile 'my.group.id:mylib:25.0.0-SNAPSHOT'
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        )
        lint().projects(project).run().expect(
            """
            src/main/java/test/pkg/TestLibrary.java:10: Error: Library.privateMethod can only be called from within the same library group (referenced groupId=my.group.id from groupId=<unknown>) [RestrictedApi]
                    Library.privateMethod(); // ERROR
                            ~~~~~~~~~~~~~
            src/main/java/test/pkg/TestLibrary.java:11: Error: PrivateClass.method can only be called from within the same library group (referenced groupId=my.group.id from groupId=<unknown>) [RestrictedApi]
                    PrivateClass.method(); // ERROR
                                 ~~~~~~
            src/main/java/test/pkg/TestLibrary.java:12: Error: InternalClass.method can only be called from within the same library group (referenced groupId=my.group.id from groupId=<unknown>) [RestrictedApi]
                    InternalClass.method(); // ERROR
                                  ~~~~~~
            3 errors, 0 warnings
            """
        )
    }

    fun testMissingRequiredAttributesForHidden() {
        lint()
            .issues(RestrictionsDetector.ISSUE)
            .files(
                xml(
                    "res/xml/app_restrictions.xml",
                    """
                <restrictions xmlns:android="http://schemas.android.com/apk/res/android">
                    <restriction
                        android:description="@string/description_number"
                        android:key="number"
                        android:restrictionType="hidden"
                        android:title="@string/title_number"/>
                </restrictions>
                """
                ).indented()
            ).run().expect(
                """
            res/xml/app_restrictions.xml:2: Error: Missing required attribute android:defaultValue [ValidRestrictions]
                <restriction
                 ~~~~~~~~~~~
            1 errors, 0 warnings
            """
            )
    }

    fun testRestrictToLibrary() {
        // 120087311: Enforce RestrictTo(LIBRARY) when the API is defined in another project
        val library = project().files(
            java(
                """
                package com.example.mylibrary;

                import androidx.annotation.RestrictTo;

                public class LibraryCode {
                    // No restriction: any access is fine.
                    public static int FIELD1;

                    // Scoped to same library: accessing from
                    // lib is okay, from app is not.
                    @RestrictTo(RestrictTo.Scope.LIBRARY)
                    public static int FIELD2;

                    // Scoped to same library group: whether accessing
                    // from app is okay depends on whether they are in
                    // the same library group (=groupId). In this test
                    // project we don't know what they are so there's
                    // no warning generated.
                    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
                    public static int FIELD3;

                    public static void method1() {
                    }

                    @RestrictTo(RestrictTo.Scope.LIBRARY)
                    public static void method2() {
                    }

                    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
                    public static void method3() {
                    }
                }
                """
            ).indented(),

            java(
                """
                package test.pkg;

                import com.example.mylibrary.LibraryCode;

                // Access within the same library -- all OK
                public class LibraryCode2 {
                    public void method() {
                        LibraryCode.method1(); // OK
                        LibraryCode.method2(); // OK
                        LibraryCode.method3(); // OK
                        int f1 =  LibraryCode.FIELD1; // OK
                        int f2 =  LibraryCode.FIELD2; // OK
                        int f3 =  LibraryCode.FIELD3; // OK
                    }
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).name("lib").type(ProjectDescription.Type.LIBRARY)

        val app = project().files(
            kotlin(
                """
                package com.example.myapplication

                import com.example.mylibrary.LibraryCode

                fun test() {
                    LibraryCode.method1()
                    LibraryCode.method2()
                    LibraryCode.method3()
                    val f1 = LibraryCode.FIELD1
                    val f2 = LibraryCode.FIELD2
                    val f3 = LibraryCode.FIELD3
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).dependsOn(library).name("app")

        lint().projects(library, app).run().expect(
            """
            src/com/example/myapplication/test.kt:7: Error: LibraryCode.method2 can only be called from within the same library (lib) [RestrictedApi]
                LibraryCode.method2()
                            ~~~~~~~
            src/com/example/myapplication/test.kt:10: Error: LibraryCode.FIELD2 can only be accessed from within the same library (lib) [RestrictedApi]
                val f2 = LibraryCode.FIELD2
                                     ~~~~~~
            2 errors, 0 warnings
            """
        )
    }

    fun testHierarchy() {
        val project = project().files(
            java(
                """
                package test.pkg;
                import library.pkg.PrivateClass;

                @SuppressWarnings("ClassNameDiffersFromFileName")
                public class TestLibrary1 extends PrivateClass {
                    @Override
                    public void method() {
                        super.method(); // ERROR
                    }
                }
                """
            ).indented(),

            java(
                """
                package test.pkg;
                import library.pkg.PrivateClass;

                @SuppressWarnings("ClassNameDiffersFromFileName")
                public class TestLibrary2 extends PrivateClass {
                }
                """
            ).indented(),

            java(
                """
                package test.pkg;

                @SuppressWarnings("ClassNameDiffersFromFileName")
                public class Inheriting1 extends TestLibrary1 {
                    public void test() {
                        method(); // OK -- overridden without annotation
                    }
                }
                """
            ).indented(),

            java(
                """
                package test.pkg;

                @SuppressWarnings("ClassNameDiffersFromFileName")
                public class Inheriting2 extends TestLibrary2 {
                    public void test() {
                        method(); // ERROR - not overridden, pointing into library
                    }
                }
                """
            ).indented(),

            base64gzip(
                "libs/exploded-aar/my.group.id/mylib/25.0.0-SNAPSHOT/jars/classes.jar",
                LIBRARY_BYTE_CODE
            ),
            gradle(
                """
                apply plugin: 'com.android.application'

                dependencies {
                    compile 'my.group.id:mylib:25.0.0-SNAPSHOT'
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        )
        lint().projects(project).run().expect(
            """
            src/main/java/test/pkg/Inheriting2.java:6: Error: PrivateClass.method can only be called from within the same library group (referenced groupId=my.group.id from groupId=<unknown>) [RestrictedApi]
                    method(); // ERROR - not overridden, pointing into library
                    ~~~~~~
            src/main/java/test/pkg/TestLibrary1.java:5: Error: PrivateClass can only be accessed from within the same library group (referenced groupId=my.group.id from groupId=<unknown>) [RestrictedApi]
            public class TestLibrary1 extends PrivateClass {
                                              ~~~~~~~~~~~~
            src/main/java/test/pkg/TestLibrary1.java:8: Error: PrivateClass.method can only be called from within the same library group (referenced groupId=my.group.id from groupId=<unknown>) [RestrictedApi]
                    super.method(); // ERROR
                          ~~~~~~
            src/main/java/test/pkg/TestLibrary2.java:5: Error: PrivateClass can only be accessed from within the same library group (referenced groupId=my.group.id from groupId=<unknown>) [RestrictedApi]
            public class TestLibrary2 extends PrivateClass {
                                              ~~~~~~~~~~~~
            4 errors, 0 warnings
            """
        )
    }

    // sample code with warnings
    fun testRestrictToTests() {
        val expected =
            """
            src/test/pkg/ProductionCode.java:9: Error: ProductionCode.testHelper2 can only be called from tests [RestrictedApi]
                    testHelper2(); // ERROR
                    ~~~~~~~~~~~
            1 errors, 0 warnings
            """
        lint().files(
            java(
                """
                package test.pkg;
                import androidx.annotation.RestrictTo;
                import androidx.annotation.VisibleForTesting;

                @SuppressWarnings({"InfiniteRecursion", "ClassNameDiffersFromFileName"})
                public class ProductionCode {
                    public void code() {
                        testHelper1(); // ERROR? (We currently don't flag @VisibleForTesting; it deals with *visibility*)
                        testHelper2(); // ERROR
                    }

                    @VisibleForTesting
                    public void testHelper1() {
                        testHelper1(); // OK
                        code(); // OK
                    }

                    @RestrictTo(RestrictTo.Scope.TESTS)
                    public void testHelper2() {
                        testHelper1(); // OK
                        code(); // OK
                    }
                }
                """
            ).indented(),
            // test/ prefix makes it a test folder entry:
            java(
                "test/test/pkg/UnitTest.java",
                """
                package test.pkg;
                @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class UnitTest {
                    public void test() {
                        new ProductionCode().code(); // OK
                        new ProductionCode().testHelper1(); // OK
                        new ProductionCode().testHelper2(); // OK

                    }
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(expected)
    }

    fun testVisibleForTesting() {
        val expected =
            """
            src/test/otherpkg/OtherPkg.java:11: Error: ProductionCode.testHelper6 can only be called from tests [RestrictedApi]
                    new ProductionCode().testHelper6(); // ERROR
                                         ~~~~~~~~~~~
            src/test/pkg/ProductionCode.java:27: Error: ProductionCode.testHelper6 can only be called from tests [RestrictedApi]
                        testHelper6(); // ERROR: should only be called from tests
                        ~~~~~~~~~~~
            src/test/otherpkg/OtherPkg.java:8: Warning: This method should only be accessed from tests or within protected scope [VisibleForTests]
                    new ProductionCode().testHelper3(); // ERROR
                                         ~~~~~~~~~~~
            src/test/otherpkg/OtherPkg.java:9: Warning: This method should only be accessed from tests or within private scope [VisibleForTests]
                    new ProductionCode().testHelper4(); // ERROR
                                         ~~~~~~~~~~~
            src/test/otherpkg/OtherPkg.java:10: Warning: This method should only be accessed from tests or within package private scope [VisibleForTests]
                    new ProductionCode().testHelper5(); // ERROR
                                         ~~~~~~~~~~~
            2 errors, 3 warnings
            """
        lint().files(
            java(
                """
                package test.pkg;
                import androidx.annotation.VisibleForTesting;

                @SuppressWarnings("ClassNameDiffersFromFileName")
                public class ProductionCode {
                    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
                    public void testHelper3() {
                    }

                    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
                    public void testHelper4() {
                    }

                    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
                    public void testHelper5() {
                    }

                    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
                    public void testHelper6() {
                    }

                    private class Local {
                        private void localProductionCode() {
                            testHelper3();
                            testHelper4();
                            testHelper5();
                            testHelper6(); // ERROR: should only be called from tests

                        }
                    }
                }
                """
            ).indented(),
            java(
                """
                package test.otherpkg;
                import androidx.annotation.VisibleForTesting;
                import test.pkg.ProductionCode;

                @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class OtherPkg {
                    public void test() {
                        new ProductionCode().testHelper3(); // ERROR
                        new ProductionCode().testHelper4(); // ERROR
                        new ProductionCode().testHelper5(); // ERROR
                        new ProductionCode().testHelper6(); // ERROR

                    }
                }
                """
            ).indented(),
            // test/ prefix makes it a test folder entry:
            java(
                "test/test/pkg/UnitTest.java",
                """
                package test.pkg;
                @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class UnitTest {
                    public void test() {
                        new ProductionCode().testHelper3(); // OK
                        new ProductionCode().testHelper4(); // OK
                        new ProductionCode().testHelper5(); // OK
                        new ProductionCode().testHelper6(); // OK

                    }
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(expected)
    }

    fun testVisibleForTestingIncrementally() {
        lint().files(
            java(
                """
                package test.pkg;
                import androidx.annotation.VisibleForTesting;

                @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class ProductionCode {
                    @VisibleForTesting
                    public void testHelper() {
                    }
                }
                """
            ).indented(),
            // test/ prefix makes it a test folder entry:
            java(
                "test/test/pkg/UnitTest.java",
                """
                package test.pkg;
                @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class UnitTest {
                    public void test() {
                        new ProductionCode().testHelper(); // OK

                    }
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        )
            .incremental("test/test/pkg/UnitTest.java")
            .run()
            .expectClean()
    }

    fun testVisibleForTestingSameCompilationUnit() {
        lint().files(
            java(
                """
                package test.pkg;
                import androidx.annotation.VisibleForTesting;

                @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class PrivTest {
                    private static CredentialsProvider sCredentialsProvider = new DefaultCredentialsProvider();
                    @SuppressWarnings("UnnecessaryInterfaceModifier")
                    static interface CredentialsProvider {
                        void test();
                    }
                    @VisibleForTesting
                    static class DefaultCredentialsProvider implements CredentialsProvider {
                        @Override
                        public void test() {
                        }
                    }
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun testCrossPackage() {
        // Regression test for http://b/190113936 AGP 7 VisibleForTests Lint check bug
        lint().files(
            bytecode(
                "libs/library.jar",
                java(
                    """
                    package com.example;

                    import androidx.annotation.VisibleForTesting;

                    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
                    public class Foo {
                      public void foo() { }
                    }
                    """
                ).indented(),
                0xd5543545,
                """
                com/example/Foo.class:
                H4sIAAAAAAAAAGVOTUvDQBScZ9qkTaut3kQ8ePLjYI4eLIIIgUJR0NL7Jlnr
                lmSfJJvav+VJ8OAP8EeJLxF68bAzb2dn3s73z+cXgCschPAwCjAOsE/wJ8Ya
                d0Pwzs4XhM4dZ5owmhmr7+si0eVcJbko3jMzIXziukx1bBqpFzNfrtRaEY4e
                a+tMoad2bSojgVtr2Sln2FaE05myWckm20Rqq0eLP2fM5VxXztjlNaHP7kWX
                b6bSnnT1huigK21SLiK9UcVrrqO46TFuvo1yZZfRQ7LSqcMJdtBkIExNTNCX
                27EwCXcvPkDvMhACQb8VA8He1nrYvuK/rSc7++3mEAPhgahDObtT7P0CP54v
                9VcBAAA=
                """
            ),
            manifest(),
            java(
                """
                package com.example;

                public class Bar {
                  public void useFoo(Foo foo) {
                    foo.foo(); // OK
                  }
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun testVisibleForTestingEqualsOperator() {
        lint().files(
            kotlin(
                """
                package test.pkg

                fun test(testRoot: TestRoot, other: TestRoot) {
                    if (testRoot == null) {
                        println("null")
                    }
                    if (testRoot != null) {
                        println("not null")
                    }
                    if (testRoot == other) {
                        println("same")
                    }
                }
                """
            ).indented(),
            kotlin(
                """
                package test.pkg

                import androidx.annotation.VisibleForTesting

                @VisibleForTesting
                interface TestRoot {
                    override fun equals(other: Any?): Boolean {
                        return super.equals(other)
                    }
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            """
            src/test/pkg/test.kt:10: Warning: This method should only be accessed from tests or within private scope [VisibleForTests]
                if (testRoot == other) {
                             ~~
            0 errors, 1 warnings
            """
        )
    }

    fun testVisibleForTestingInGoogle3() {
        // Regression test for
        //   117544702: com.google.common.annotations.VisibleForTesting.productionVisibility
        //              is not recognized
        val expected =
            """
            src/test/otherpkg/OtherPkg.java:11: Error: ProductionCode.testHelper6 can only be called from tests [RestrictedApi]
                    new ProductionCode().testHelper6(); // ERROR
                                         ~~~~~~~~~~~
            src/test/pkg/ProductionCode.java:27: Error: ProductionCode.testHelper6 can only be called from tests [RestrictedApi]
                        testHelper6(); // ERROR: should only be called from tests
                        ~~~~~~~~~~~
            src/test/otherpkg/OtherPkg.java:8: Warning: This method should only be accessed from tests or within protected scope [VisibleForTests]
                    new ProductionCode().testHelper3(); // ERROR
                                         ~~~~~~~~~~~
            src/test/otherpkg/OtherPkg.java:9: Warning: This method should only be accessed from tests or within private scope [VisibleForTests]
                    new ProductionCode().testHelper4(); // ERROR
                                         ~~~~~~~~~~~
            src/test/otherpkg/OtherPkg.java:10: Warning: This method should only be accessed from tests or within package private scope [VisibleForTests]
                    new ProductionCode().testHelper5(); // ERROR
                                         ~~~~~~~~~~~
            2 errors, 3 warnings
            """
        lint().files(
            java(
                """
                package test.pkg;
                import com.google.common.annotations.VisibleForTesting;

                @SuppressWarnings("ClassNameDiffersFromFileName")
                public class ProductionCode {
                    @VisibleForTesting(productionVisibility = VisibleForTesting.Visibility.PROTECTED)
                    public void testHelper3() {
                    }

                    @VisibleForTesting(productionVisibility = VisibleForTesting.Visibility.PRIVATE)
                    public void testHelper4() {
                    }

                    @VisibleForTesting(productionVisibility = VisibleForTesting.Visibility.PACKAGE_PRIVATE)
                    public void testHelper5() {
                    }

                    @VisibleForTesting(productionVisibility = VisibleForTesting.Visibility.NONE)
                    public void testHelper6() {
                    }

                    private class Local {
                        private void localProductionCode() {
                            testHelper3();
                            testHelper4();
                            testHelper5();
                            testHelper6(); // ERROR: should only be called from tests

                        }
                    }
                }
                """
            ).indented(),
            java(
                """
                package test.otherpkg;
                import androidx.annotation.VisibleForTesting;
                import test.pkg.ProductionCode;

                @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class OtherPkg {
                    public void test() {
                        new ProductionCode().testHelper3(); // ERROR
                        new ProductionCode().testHelper4(); // ERROR
                        new ProductionCode().testHelper5(); // ERROR
                        new ProductionCode().testHelper6(); // ERROR

                    }
                }
                """
            ).indented(),
            // test/ prefix makes it a test folder entry:
            java(
                "test/test/pkg/UnitTest.java",
                """
                package test.pkg;
                @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class UnitTest {
                    public void test() {
                        new ProductionCode().testHelper3(); // OK
                        new ProductionCode().testHelper4(); // OK
                        new ProductionCode().testHelper5(); // OK
                        new ProductionCode().testHelper6(); // OK

                    }
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR,
            // From Guava; also Apache licensed
            guavaVisibleForTestingAnnotation
        ).run().expect(expected)
    }

    fun testVisibleForTestingInAndroid() {
        // Regression test for
        //   247885568: com.android.internal.annotations.VisibleForTesting's visibility property
        //              is not recognized
        val expected =
            """
            src/production/otherpkg/OtherPkg.java:7: Warning: This method should only be accessed from tests or within protected scope [VisibleForTests]
                    new ProductionCode().testHelper3(); // ERROR
                                         ~~~~~~~~~~~
            src/production/otherpkg/OtherPkg.java:8: Warning: This method should only be accessed from tests or within private scope [VisibleForTests]
                    new ProductionCode().testHelper4(); // ERROR
                                         ~~~~~~~~~~~
            src/production/otherpkg/OtherPkg.java:9: Warning: This method should only be accessed from tests or within package private scope [VisibleForTests]
                    new ProductionCode().testHelper5(); // ERROR
                                         ~~~~~~~~~~~
            src/production/pkg/SamePkg.java:8: Warning: This method should only be accessed from tests or within private scope [VisibleForTests]
                    new ProductionCode().testHelper4(); // ERROR
                                         ~~~~~~~~~~~
            0 errors, 4 warnings
            """.trimIndent()
        lint().files(
            java(
                """
                package production.pkg;
                import com.android.internal.annotations.VisibleForTesting;

                @SuppressWarnings("ClassNameDiffersFromFileName")
                public class ProductionCode {
                    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PROTECTED)
                    public void testHelper3() {
                    }

                    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
                    public void testHelper4() {
                    }

                    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
                    public void testHelper5() {
                    }
                }
                """
            ).indented(),
            java(
                """
                package production.pkg;
                import production.pkg.ProductionCode;

                @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class SamePkg {
                    public void test() {
                        new ProductionCode().testHelper3(); // OK
                        new ProductionCode().testHelper4(); // ERROR
                        new ProductionCode().testHelper5(); // OK
                    }
                }
                """
            ).indented(),
            java(
                """
                package production.otherpkg;
                import production.pkg.ProductionCode;

                @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class OtherPkg {
                    public void test() {
                        new ProductionCode().testHelper3(); // ERROR
                        new ProductionCode().testHelper4(); // ERROR
                        new ProductionCode().testHelper5(); // ERROR
                    }
                }
                """
            ).indented(),
            // test/ prefix makes it a test folder entry:
            java(
                "test/test/pkg/UnitTest.java",
                """
                package test.pkg;
                @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class UnitTest {
                    public void test() {
                        new ProductionCode().testHelper3(); // OK
                        new ProductionCode().testHelper4(); // OK
                        new ProductionCode().testHelper5(); // OK
                    }
                }
                """
            ).indented(),
            androidVisibleForTestingAnnotation
        ).run().expect(expected)
    }

    fun testRestrictedInheritedAnnotation() {
        // Regression test for http://b.android.com/230387
        // Ensure that when we perform the @RestrictTo check, we don't incorrectly
        // inherit annotations from the base classes of AppCompatActivity and treat
        // those as @RestrictTo on the whole AppCompatActivity class itself.
        lint().files(
            /*
            Compiled version of these two classes:
                package test.pkg;
                import androidx.annotation.RestrictTo;
                @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
                public class RestrictedParent {
                }
            and
                package test.pkg;
                public class Parent extends RestrictedParent {
                    public void myMethod() {
                    }
                }
             */
            base64gzip(
                "libs/exploded-aar/my.group.id/mylib/25.0.0-SNAPSHOT/jars/classes.jar",
                "" +
                    "H4sIAAAAAAAAAAvwZmYRYeDg4GB4VzvRkwEJcDKwMPi6hjjqevq56f87xcDA" +
                    "zBDgzc4BkmKCKgnAqVkEiOGafR39PN1cg0P0fN0++5457eOtq3eR11tX69yZ" +
                    "85uDDK4YP3hapOflq+Ppe7F0FQtnxAvJI9JSUi/Flj5boia2XCujYuk0C1HV" +
                    "tGei2iKvRV8+zf5U9LGIEeyWNZtvhngBbfJCcYspmlvkgbgktbhEvyA7XT8I" +
                    "yCjKTC5JTQlILErNK9FLzkksLp4aGOvN5Chi+/j6tMxZqal2rK7xV+y+RLio" +
                    "iRyatGmWgO2RHdY3blgp7978b/28JrlfjH9XvMh66Cxwg6fY/tze73Mknz3+" +
                    "/Fb2gOaqSJXAbRvyEpsVi/WmmojznPzbrOe8al3twYCCJULbP25QP8T3nrVl" +
                    "iszbjwtOO1uerD8wpXKSoPNVQyWjby925u8WablkfCj/Y4BG8bEJua8tvhzZ" +
                    "OsdnSr35HJ4fM4RbpbWV2xctPGY0ySUu2Es6b0mYyobnBU/bo36VifS7WZmY" +
                    "zZ+aPknWN+mlIX9S4kKnxNuXlSedMZ0ilGj7IFCl43WF3bq5L00Mn809NjW6" +
                    "+L18/p1nsdrtIpd4ptrLnwmYs+cE345Xt8/ec6g4dkjs8EX7EMmy56+OmQl9" +
                    "mT75aMblsyfSNDYvt5xgV8NavVCBsTsnjSttg4PZ97sNrikn1TeavD2l6L/P" +
                    "Y2uqVSu7QWPomoUuGdMmKJltLIr8yQSKpPpfEa8iGBkYfJjwRZIociQhR01q" +
                    "n7//IQeBo/cv1AesjsiX2cmp9u1B4OOjLcGmbpzfl949oFRytszwY3Kl0cMD" +
                    "7B+cJZetzex5l3hvj/nn0+euf8/jf8BVyMGuzviL0Y/zX6/WlL2qFs8XSx7c" +
                    "e3mnypfg0BPtb9P0zoacuT5nzlIr4dczDVZ9sl+YPX2VypGVU5f6xsWLnVxs" +
                    "sGnD9ZZ3z/7G3Vp6jvPh5nuzfPxCWmVMpadrf1RT2vHhx2Z7k8QLav53JKZG" +
                    "zjQ35rn48PPq64yhNuHzYw95rbn3Q/hLYD/zujpZqxdFvbNYvwhs+qSpWxNY" +
                    "/Yd9b7zC1oSQfFl5cErewhTw/BEwCIIYQYHEyCTCgJqvYDkOlClRAUoWRdeK" +
                    "nEFEULTZ4sigyCaA4gg59uRRTDhJOFuhG4bsS1EUw/KYcER/gDcrG0gBCxDy" +
                    "ArVNZgbxABAMMsu2BAAA"
            ),
            java(
                "" +
                    "package test.pkg;\n" +
                    "\n" +
                    "public class Cls extends Parent {\n" +
                    "    @Override\n" +
                    "    public void myMethod() {\n" +
                    "        super.myMethod();\n" +
                    "    }\n" +
                    "}\n"
            ),
            gradle(
                "" +
                    "apply plugin: 'com.android.application'\n" +
                    "\n" +
                    "dependencies {\n" +
                    "    compile 'my.group.id:mylib:25.0.0-SNAPSHOT'\n" +
                    "}"
            ),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun testPrivateVisibilityWithDefaultConstructor() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=235661
        lint().files(
            java(
                "" +
                    "package test.pkg;\n" +
                    "\n" +
                    "import androidx.annotation.VisibleForTesting;\n" +
                    "\n" +
                    "public class LintBugExample {\n" +
                    "    public static Object demonstrateBug() {\n" +
                    "        return new InnerClass();\n" +
                    "    }\n" +
                    "\n" +
                    "    @VisibleForTesting\n" +
                    "    static class InnerClass {\n" +
                    "    }\n" +
                    "}"
            ),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun testKotlinVisibility() {
        // Regression test for https://issuetracker.google.com/67489310
        // Handle Kotlin compilation unit visibility (files, internal ,etc)
        lint().files(
            kotlin(
                "" +
                    "package test.pkg\n" +
                    "\n" +
                    "import androidx.annotation.VisibleForTesting\n" +
                    "\n" +
                    "fun foo() {\n" +
                    "    AndroidOSVersionChecker()\n" +
                    "}\n" +
                    "\n" +
                    "@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)\n" +
                    "internal class AndroidOSVersionChecker2 {\n" +
                    "}"
            ),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun testVisibleForTestingInternalKotlin() {
        lint().files(
            kotlin(
                """
                package test.pkg

                import android.os.Bundle
                import android.app.Activity
                import androidx.annotation.VisibleForTesting
                import android.util.Log

                class MainActivity : Activity() {

                    override fun onCreate(savedInstanceState: Bundle?) {
                        super.onCreate(savedInstanceState)

                        Log.d("MainActivity", createApi().getPrompt())
                        Log.d("MainActivity", createOtherApi().getPrompt())
                    }

                    interface SomeApi {
                        /**
                         * Get the prompt of the day. The server will choose a prompt that will be shown for 24 hours.
                         */
                        fun getPrompt(): String
                    }
                }

                @VisibleForTesting
                internal fun createApi(): MainActivity.SomeApi {
                    return object : MainActivity.SomeApi {
                        override fun getPrompt(): String {
                            return "Foo"
                        }
                    }
                }

                private fun createOtherApi() : MainActivity.SomeApi {
                    return object : MainActivity.SomeApi {
                        override fun getPrompt(): String {
                            return "Bar"
                        }
                    }
                }
                """
            ),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun testRestrictedClassOrInterfaceUsage() {
        lint().files(
            kotlin(
                """
                package test.pkg

                class MyClass : RestrictedClass()
                """
            ),
            java(
                """
                package test.pkg;

                @SuppressWarnings("ClassNameDiffersFromFileName")
                public class MyJavaClass extends RestrictedClass implements RestrictedInterface {
                }
                """
            ).indented(),

            java(
                "src/androidTest/java/test/pkg/MyTestJavaClass.java",
                """
                  package test.pkg;

                  @SuppressWarnings("ClassNameDiffersFromFileName")
                  public class MyTestJavaClass extends RestrictedClass {
                  }
                  """
            ).indented(),

            kotlin(
                """
                package test.pkg

                import androidx.annotation.RestrictTo

                @RestrictTo(RestrictTo.Scope.TESTS)
                open class RestrictedClass
                """
            ).indented(),
            kotlin(
                """
                package test.pkg

                import androidx.annotation.RestrictTo

                @RestrictTo(RestrictTo.Scope.TESTS)
                interface RestrictedInterface
                """
            ).indented(),
            gradle(
                """
                android {
                    lintOptions {
                        checkTestSources true
                    }
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).skipTestModes(TestMode.TYPE_ALIAS).run().expect(
            """
            src/main/kotlin/test/pkg/MyClass.kt:4: Error: RestrictedClass can only be called from tests [RestrictedApi]
                            class MyClass : RestrictedClass()
                                            ~~~~~~~~~~~~~~~
            src/main/java/test/pkg/MyJavaClass.java:4: Error: RestrictedClass can only be accessed from tests [RestrictedApi]
            public class MyJavaClass extends RestrictedClass implements RestrictedInterface {
                                             ~~~~~~~~~~~~~~~
            src/main/java/test/pkg/MyJavaClass.java:4: Error: RestrictedInterface can only be accessed from tests [RestrictedApi]
            public class MyJavaClass extends RestrictedClass implements RestrictedInterface {
                                                                        ~~~~~~~~~~~~~~~~~~~
            3 errors, 0 warnings
            """
        )
    }

    fun testPackagePrivateFromKotlin() {
        lint().files(
            kotlin(
                """
                package test.pkg
                import androidx.annotation.VisibleForTesting
                @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
                class RunnerFactoryKotlin {
                }
                """
            ),
            java(
                """
                package test.pkg;
                public class NotWorkingEngineJava {
                    public void test() {
                        final RunnerFactoryKotlin runnerFactory = new RunnerFactoryKotlin();
                    }
                }
                """
            ),
            kotlin(
                """
                package test.pkg
                class NotWorkingEngineKotlin {
                    fun test() {
                        val runnerFactory = RunnerFactoryKotlin()
                    }
                }
                """
            ),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun test123545341() {
        // Regression test for
        // 123545341: RestrictTo(TESTS) doesn't allow same class to use methods
        // (Note that that test asks for the following not to be an error, but this is
        // deliberate and we're testing the enforcement here)
        lint().files(
            java(
                """
                package test.pkg;

                import androidx.annotation.RestrictTo;
                import static androidx.annotation.RestrictTo.Scope.TESTS;

                class Outer {
                    private Inner innerInstance;

                    @RestrictTo(TESTS)
                    class Inner {
                        public void method() {
                        }
                    }

                    private void outerMethod() {
                        // This is marked as invalid
                        innerInstance.method();
                    }
                }
                """
            ),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            """
            src/test/pkg/Outer.java:18: Error: Inner.method can only be called from tests [RestrictedApi]
                                    innerInstance.method();
                                                  ~~~~~~
            1 errors, 0 warnings
            """
        )
    }

    fun test140642032() {
        // Regression test for
        // 140642032: Kotlin class property annotated with VisibleForTesting not generating
        // error/warning when called from other Kotlin code
        lint().files(
            kotlin(
                """
                package test.pkg

                import androidx.annotation.VisibleForTesting
                import androidx.annotation.VisibleForTesting.NONE

                open class Foo {
                    var hiddenProp: String = ""
                        @VisibleForTesting(otherwise = NONE) get() = field
                        @VisibleForTesting(otherwise = NONE) set(value) {
                            field = value
                        }

                    @VisibleForTesting(otherwise = NONE)
                    open fun hiddenFunc() {
                        // Do something
                    }
                }

                class Bar : Foo() {
                    override fun hiddenFunc() {
                        // Do something
                    }
                }

                class FooKtCaller {
                    fun func() {
                        val f = Foo()
                        // NO error/warning
                        f.hiddenProp
                        // NO error/warning
                        f.hiddenProp = ""
                        // Generates error/warning
                        f.hiddenFunc()

                        val b = Bar()
                        // NO error/warning
                        b.hiddenProp
                        // NO error/warning
                        b.hiddenProp = ""
                        // Generates error/warning
                        b.hiddenFunc()
                    }
                }
                """
            ).indented(),
            java(
                """
                package test.pkg;
                public class FooCaller {
                    public void method() {
                        final Foo f = new Foo();
                        // Generates error/warning
                        f.getHiddenProp();
                        // Generates error/warning
                        f.setHiddenProp("");
                        // Generates error/warning
                        f.hiddenFunc();

                        final Bar b = new Bar();
                        // Generates error/warning
                        b.getHiddenProp();
                        // Generates error/warning
                        b.setHiddenProp("");
                        // Generates error/warning
                        b.hiddenFunc();
                    }
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            """
            src/test/pkg/Foo.kt:29: Error: Foo.getHiddenProp can only be called from tests [RestrictedApi]
                    f.hiddenProp
                      ~~~~~~~~~~
            src/test/pkg/Foo.kt:31: Error: Foo.setHiddenProp can only be called from tests [RestrictedApi]
                    f.hiddenProp = ""
                      ~~~~~~~~~~
            src/test/pkg/Foo.kt:33: Error: Foo.hiddenFunc can only be called from tests [RestrictedApi]
                    f.hiddenFunc()
                      ~~~~~~~~~~
            src/test/pkg/Foo.kt:37: Error: Foo.getHiddenProp can only be called from tests [RestrictedApi]
                    b.hiddenProp
                      ~~~~~~~~~~
            src/test/pkg/Foo.kt:39: Error: Foo.setHiddenProp can only be called from tests [RestrictedApi]
                    b.hiddenProp = ""
                      ~~~~~~~~~~
            src/test/pkg/FooCaller.java:6: Error: Foo.getHiddenProp can only be called from tests [RestrictedApi]
                    f.getHiddenProp();
                      ~~~~~~~~~~~~~
            src/test/pkg/FooCaller.java:8: Error: Foo.setHiddenProp can only be called from tests [RestrictedApi]
                    f.setHiddenProp("");
                      ~~~~~~~~~~~~~
            src/test/pkg/FooCaller.java:10: Error: Foo.hiddenFunc can only be called from tests [RestrictedApi]
                    f.hiddenFunc();
                      ~~~~~~~~~~
            src/test/pkg/FooCaller.java:14: Error: Foo.getHiddenProp can only be called from tests [RestrictedApi]
                    b.getHiddenProp();
                      ~~~~~~~~~~~~~
            src/test/pkg/FooCaller.java:16: Error: Foo.setHiddenProp can only be called from tests [RestrictedApi]
                    b.setHiddenProp("");
                      ~~~~~~~~~~~~~
            10 errors, 0 warnings
            """
        )
    }

    fun test148905488() {
        // Regression test for https://issuetracker.google.com/148905488
        // Referencing a @VisibleForTesting-annotated property in Kotlin does not cause a Lint error
        lint().files(
            kotlin(
                """
                package test.pkg

                import androidx.annotation.VisibleForTesting

                class MyViewModel {
                    @get:VisibleForTesting
                    internal var currentNamespace: String? = null
                    // Without @get: the following annotation will default to apply to
                    // the *field*, not the getter and/or setter. However, lint will
                    // now search for these anyway since despite Kotlin's use site semantics
                    // this is probably intended to apply for accesses to the property.
                    @VisibleForTesting
                    internal var currentNamespace2: String? = null
                }
                """
            ).indented(),
            kotlin(
                """
                package test.pkg

                class MyActivity {
                    private val myViewModel = MyViewModel()
                    fun foo() {
                        val foo = myViewModel.currentNamespace.orEmpty()
                        val bar = myViewModel.currentNamespace2.orEmpty()
                    }
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            """
            src/test/pkg/MyActivity.kt:6: Warning: This method should only be accessed from tests or within private scope [VisibleForTests]
                    val foo = myViewModel.currentNamespace.orEmpty()
                                          ~~~~~~~~~~~~~~~~
            src/test/pkg/MyActivity.kt:7: Warning: This method should only be accessed from tests or within private scope [VisibleForTests]
                    val bar = myViewModel.currentNamespace2.orEmpty()
                                          ~~~~~~~~~~~~~~~~~
            0 errors, 2 warnings
            """
        )
    }

    fun test169255669() {
        // Regression test for 169255669: ClassCastException in RestrictToDetector.
        @Suppress("ConvertSecondaryConstructorToPrimary")
        lint().files(
            kotlin(
                """
                package test.pkg

                import androidx.annotation.RestrictTo

                class Foo {
                    @RestrictTo(RestrictTo.Scope.SUBCLASSES)
                    constructor()
                }

                @RestrictTo(RestrictTo.Scope.SUBCLASSES)
                val foo = Foo()
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            """
            src/test/pkg/Foo.kt:11: Error: Foo can only be called from subclasses [RestrictedApi]
            val foo = Foo()
                      ~~~
            1 errors, 0 warnings
            """
        )
    }

    fun test169610406() {
        // 169610406: Strange warning from RestrictToDetector for Kotlin property
        //            initialized by constructor call
        lint().files(
            kotlin(
                """
                package test.pkg

                import androidx.annotation.RestrictTo
                import androidx.annotation.RestrictTo.Scope.SUBCLASSES

                class Foo
                open class Bar {
                    // No use site target specified; Kotlin will take this to refer to the
                    // field only; lint will also interpret this as applying to getters and setters
                    @RestrictTo(SUBCLASSES)
                    val foo1: Foo = Foo()

                    // Field explicitly requested; lint only enforce this on field references, not getters/setters
                    @field:RestrictTo(SUBCLASSES)
                    val foo2: Foo = Foo()

                    // Setter only; don't enforce on getter
                    @set:RestrictTo(SUBCLASSES)
                    var foo3: Foo? = Foo()

                    // Getter only, don't enforce on setter
                    @get:RestrictTo(SUBCLASSES)
                    var foo4: Foo? = Foo()
                }
              """
            ).indented(),
            kotlin(
                """
                package test.pkg
                class Sub : Bar() {
                    fun test() {
                        val test = foo1 // OK 1
                        println(foo1)   // OK 2
                        println(foo2)   // OK 3
                        println(foo3)   // OK 4
                        println(foo5)   // OK 5
                    }
                }
                class NotSub(private val bar: Bar) {
                    fun test() {
                        val test = bar.foo1  // WARN 1
                        println(bar.foo1)    // WARN 2
                        val test2 = bar.foo2 // OK 6
                        println(bar.foo2)    // OK 7
                        val test3 = bar.foo3 // OK 8
                        println(bar.foo3)    // OK 9
                        bar.foo3 = null      // WARN 3
                        println(bar.foo4)    // WARN 4
                        bar.foo4 = null      // OK 10
                    }
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            """
            src/test/pkg/Sub.kt:13: Error: Bar.getFoo1 can only be called from subclasses [RestrictedApi]
                    val test = bar.foo1  // WARN 1
                                   ~~~~
            src/test/pkg/Sub.kt:14: Error: Bar.getFoo1 can only be called from subclasses [RestrictedApi]
                    println(bar.foo1)    // WARN 2
                                ~~~~
            src/test/pkg/Sub.kt:19: Error: Bar.setFoo3 can only be called from subclasses [RestrictedApi]
                    bar.foo3 = null      // WARN 3
                        ~~~~
            src/test/pkg/Sub.kt:20: Error: Bar.getFoo4 can only be called from subclasses [RestrictedApi]
                    println(bar.foo4)    // WARN 4
                                ~~~~
            4 errors, 0 warnings
            """
        )
    }

    fun testAssignment() {
        // Make sure we flag @VisibleForTesting assignment mismatches
        lint().files(
            java(
                "" +
                    "package test.pkg;\n" +
                    "\n" +
                    "import java.io.File;\n" +
                    "\n" +
                    "@SuppressWarnings({\"FieldCanBeLocal\", \"unused\"})\n" +
                    "public class LegacyLocalRepoLoader {\n" +
                    "    private final LocalSdk mLocalSdk;\n" +
                    "\n" +
                    "    public LegacyLocalRepoLoader(File root, String fop) {\n" +
                    "        mLocalSdk = new LocalSdk(fop);\n" +
                    "    }\n" +
                    "}"
            ).indented(),
            java(
                "" +
                    "package test.pkg;\n" +
                    "import androidx.annotation.VisibleForTesting;\n" +
                    "@Deprecated\n" +
                    "public class LocalSdk {\n" +
                    "    private final String mFileOp;\n" +
                    "    @VisibleForTesting\n" +
                    "    public LocalSdk(String fileOp) {\n" +
                    "        mFileOp = fileOp;\n" +
                    "    }\n" +
                    "}\n" +
                    ""
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            "" +
                "src/test/pkg/LegacyLocalRepoLoader.java:10: Warning: This method should only be accessed from tests or within private scope [VisibleForTests]\n" +
                "        mLocalSdk = new LocalSdk(fop);\n" +
                "                    ~~~~~~~~~~~~~~~~~\n" +
                "0 errors, 1 warnings"
        )
    }

    companion object {
        /*
                Compiled version of these 5 files (and the RestrictTo annotation);
                we need to use a compiled version of these to mimic the Gradle artifact
                layout (which is relevant for this lint check which enforces restrictions
                across library compilation units) :

                Library.java:
                    package library.pkg;

                    import androidx.annotation.RestrictTo;

                    public class Library {
                        public static void method() {
                        }

                        @RestrictTo(RestrictTo.Scope.GROUP_ID)
                        public static void privateMethod() {
                        }
                    }

                PrivateClass.java:
                    package library.pkg;

                    import androidx.annotation.RestrictTo;

                    @RestrictTo(RestrictTo.Scope.GROUP_ID)
                    public class PrivateClass {
                        public static void method() {
                        }
                    }

                package-info.java:
                    @RestrictTo(RestrictTo.Scope.GROUP_ID)
                    package library.pkg.internal;

                    import androidx.annotation.RestrictTo;

                InternalClass.java:
                    package library.pkg.internal;

                    public class InternalClass {
                        public static void method() {
                        }
                    }
                 */
        private const val LIBRARY_BYTE_CODE = "" +
            "H4sIAAAAAAAAAJVXB1RT2RYNxdBCMQSQXpQS6eWLCogBiXTpMhQNhCIQEghF" +
            "iqJIkSYd6dXQJnRHRIpSIjXggChSVFAQEAQElCb4wfF/Ez4w829Wsl5Wzt65" +
            "b5/zzrnbQIeKGgKgpaUFXDvLqwUgWXQAaoCehglMUksfLr3VCQBQAQx0aGh3" +
            "fqL8GWKwLxiy/f4vWA+mrwXXMDaR0oMv6RG7dHUkpXoZdSSP9xCf3TeS6Zcf" +
            "ncBKaetJaOn1ev1OTWc+xUlI54ZeWeUQFpnEHT85ycE2ziF8F5Dksoj9jKX4" +
            "sQnd2Nvu6tt/ce7nJugBgO2NWe/axM5eUU62WBusr/T+USCSKDcXxwMi2XZF" +
            "OqE97bFoG5T0Lx12Q8T2g2j9vFBH2Xh4SCF3PkMuVOq3yEDqB3B0jIHsJ+As" +
            "VDKjgYf788FG+UWgnDl95wdS6Fs8HOHPbNVsR6lWxQj3LQVTfFoy367NSdwI" +
            "aLgBqGmGhdJuQNehwOBx+MAQI13v4FoiS0xaqtAygzW+w8/0+IaATkunTa9J" +
            "n27+i4C3EZWWU3fdLI5YWzhdJjx/bzU1BLXUOdo63NLeXt3wodCsUzKNU7AO" +
            "87La6OFpyPdKa6mJ+8TD93ROrj1JklmgsLFT4bg6YWTK1Da1QLPpefZxuOyY" +
            "ngyrx8o7xmWuMuPWY2GHvY8s3aFQsSOqzG4Fx1SksuAuwzDt6oNvFHdyaGHH" +
            "7jqzLY8tBWkh7RZQdD8B3WyQLjaO9pJOaAfMX/olXVDRoYKxXMe3Rjr2R4YI" +
            "mN2yFXRn6FKIyOOhjYs0Y6+6ytkiMjNbWXd19PL9BZrVewRTmMR7UMu1+tSV" +
            "lJWYAUVA4Sv9ijiEsQDvcKupZz4j3bKLafeLRGBa9myH7gNtRPmVk1OOnY9e" +
            "XEoO7Ul1NYBZfqU8rxgh2vMxfFnP2pIDtx5iDztz0jpESQky9UenQqJA5ERN" +
            "lVfizARxXCpqMUtoACFbFjQ/0/6qQQq+8jBmLb/xcW++8ZzGy6Xifp72SrnF" +
            "eUXKwQHVPrGsTsNT944fm2Lg+a5uPOqW+4n3LIJOtZqHt9ISJvpBQggvG09D" +
            "23zaT/O9wp0EqeoK7+oNuh1J23U9i7e2Bcs7UFLOXZLq/nX9U0TDDu0dETNd" +
            "FaK5kotk/Vggip5MfGyGQtoIq7Cup0GeIneq9WtvZ2XdzJ7QWhilWTW01QMT" +
            "gjRRU79ffDHfUGx75szbNxSziMowCQde4qkiGchXXLx3tmBddUBLLH76LuLI" +
            "Sj7YUasOSmTDhPanLns1tnCj7SxW0k9tCCBDq/g7C8Y+DEZmzak8uX21o05y" +
            "NggtGwWHe9ewFW7WDmkmJrkaXuTl1Kk6baJtic43s3v40eOFnEV1/clwIdeS" +
            "tifZDaFhpXGBDX3Lgddu3w53MxD24ZwKg2NntOmMGXx5GBaHuIguj/AibbkJ" +
            "rWtfnlpvhuX6FLOyhiFT0R+/PhwKGOedE7qt3NIvbSmfaBTKgfWaz1Tk10Dw" +
            "SsigtpCg0KTAV+ZqoQH2flWIaV0Ig4lvIufEt1bxVwyBIJ6IkEus6pDXZ/jg" +
            "cYsy8MdP2Zb1L7YH0PLmPTrX9U2Pu0nsKXhMtvb9QwSlcYrIb4+UOkAiUI7p" +
            "0mBfXR6Txz+6Ws9dmWKf7dSlUh6UPr6900fSSJS+YAbOAIFAJJBCCugAvOQN" +
            "POMNDIADT6QT64lgJTjQgbFbBg5kn/yWaAIHBmQT602e9I3g+vNiCTienLDY" +
            "CwFYW31zW0ORMGY2O2hHu4SDXexiR4fucW2J3/ul2/UHVgIrMj3AkZGlnJw1" +
            "t5Z0IuONX8vBuKjjhL9UgbvAvkfMj5hzch5zS21sFwAYFWQnpCSXzWcnJqVZ" +
            "Nee0N1IvU9LKUAoaqMbaUS0uN10DAs1KP4GiuANBIkBvgQ4gu53DKCBemeUQ" +
            "KANkhfg0gjBV/qGOzrXuw33b9857YHHvVscA6+Rt42lPok6SYZc2lSxEpTLG" +
            "yeUB89tSbtjnkJ7mfCFtDShEsrTWXXBa4Y/i5zg7uUOqXUmjzcwL9whKxlA0" +
            "dUOsf0KCf0+HMH82Zh4QXZGlTtnFZMZTIoe7gIv/mihYed//WWzJdBlCTAEH" +
            "HhirOEqUnF8NTuQeer+loih3+kaQ1nAyhXr+WFf0doHzqnwZKYhDojW6J2Fu" +
            "ukB1m43arliXmT993hnBiX3Pyloo+D1CncVNzJg0rO/YC3SMWkFdHyVZxHV4" +
            "B+HLeqtmgGNXcHe81paHXMMbuaYHLfWii2VX1Mdy1IarC8/bJZeLC+uiRDRn" +
            "RjEW6bLpEPmLpel1H5CbQjHHjED4J91dBlEO7tfL/Ob0HAWUZ3I6K74zq4aW" +
            "qnohqeSXEwyJNzWY72UUJOgkGep9arjVQBhgmvAEFcTlZmQrCETyvJBwKBz/" +
            "l66QSBU/AeQ7gaw8RTSWBFvkmF4DsJoTwOy03YeNhOIWB4c4drI2uGr81Xk7" +
            "Y+6UpJO6Wvl/J7UN2g6LcbKT3j+KhSTKw8vNDYP1PCCae49oGzQa42nj6YRB" +
            "k8zs1/rcZEDZg4FG9h6eWCekpwnmmDES42b/s6qM5y88N4CoxHi5XKxlbDtH" +
            "s8rtSfVb6s00VJNeRqjdYXUaus9BvokRwq9n+ry+WcuBZbkYszfvRa0kaQiC" +
            "39eOv8m8Oq+yMjIwzK/65BpNRXChlpjYx5SW+gg6NobTfWJaSOf2KYiCfWte" +
            "qPt0Fxe2T9WokpDTMDTyym8JvXlIfaRqM7oFUcIwRu3CN++dKdWilhDZ0Hh1" +
            "hYguf/456M9xtVVBZuvLKen6iiArJdSavKFXyBULRry97NTLl0sPHF5XcVH3" +
            "p37y89DZEm/gjZd2b2/IglSZizFZ4kvW/BUMC6bpJ73KG2aoJR/P9IZzxaHr" +
            "HYs40mi+xabzcWwUaFDbPjHiqdKrnh4eGf4QKD5IIwlpGo6O2tS4IG9qPRaK" +
            "B7Eme16WRht7rTblpybcrZP2ecUQBjWLpSh19g965zjpzJ03UMYk7P9A6G3c" +
            "WwbUZL74IcctXOKxykA8NlMEJIHUeIoMXRNcZdQdCw8crkEKBJUEujth2+Vw" +
            "0p0FSZUql2NvWeX6oHF3XlO1MWlNzN54YG9+6kZq75aZlP9DjrqLzvbrR/qW" +
            "haEP6k3oY/n5VzOWkQmLInbA+JrQGUQ7a63OpTN+/LOscP3DG5phNvEooYh8" +
            "6A3650Hf7KPOFfqZpW5ErUIhqapYwe/A395LMCi6i0c8Z4tPAtkTpmiKPJae" +
            "i2rybN6zEvY9xruSl8F5JUXYX4SQCxs3/k5vL4pv1c/hPcpJYR3eeIU6yofa" +
            "KBwTbHvdopU3P8bQRfTN+c7TQKV1FcyJPKJAiGzzxYWbBAKwe541/vHZSayU" +
            "BVqZYC75nWHnScoYcKS/tP0UNVOT9r/dxSv+T4v3Z9kaDWu3nGXJ/CRpgS+9" +
            "ig8TsoA00a5/iBQssIjScjKDrQsMe0Kh02itEyFbacwBN/mF9Y+bB99al3Fk" +
            "uu8opVQ3z1+OAbhRKHGhnCvyYaZBrpS+z5LV9XP6WW6dd2h/l9J2hK9Spmlh" +
            "7bPWsEMed0cOvq3DTexovrwgzvZ4rBrms7lGYHmRcgakVshg2Q68Vs8XnHI5" +
            "i75nHUWgNbEMN2i6qXrI/U+6R5+tGermhovhzKlTm6i8Gk1Os/JNIadOmYU2" +
            "MWJ69ZgvtbejqAH7lxq1102zHuLjVCWabQlKSYxt7sUw7rIltex09sCRZHFl" +
            "8+WeBsEKRG30ecsYES/LV/VvKlBRznot9YpoQ5/lQmFwkW6hu9L1OXFKf3ma" +
            "DKnoxmnVS0Xwm2eBkpFvA8fd4Cnqd0N8CSCIDcWmcBXYWYEpsoJ6IrE6tzGX" +
            "sgtagqtIa7agn4GizlLIR4p1506U47N3Evcw00sLsd0CPX60QApKCIDcM/3H" +
            "Te0YLvJFZr92Q0k9EIQMpgLY23ztMNAD9vdKv1YzgNQ57Y8CkaGmALud1P5I" +
            "NjIkmGJfZ/XrpncoSE2DGBmFxn4Uezit3ayk52ZRMtZQyn9sP3aTkp7mOMlI" +
            "B6kOOIDvpiE99vCR0aQc+ruD4G4u0mFMzlUN/LtjE2ky9xrfvxaKlnSY749i" +
            "IUPF0+413PdHc5OhG/dAkw37X0LsdEzSXipLRrR2MNHew383PekTL06uM8P/" +
            "1Z4NdA4Bd2DM2y/p7b1iGHe+/RsdMTpPtBEAAA=="
    }

    fun testRestrictToLibraryViaGradleModel() {
        val library = project().files(
            java(
                """
                package com.example.mylibrary;

                import androidx.annotation.RestrictTo;

                public class LibraryCode {
                    // No restriction: any access is fine.
                    public static int FIELD1;

                    // Scoped to same library: accessing from
                    // lib is okay, from app is not.
                    @RestrictTo(RestrictTo.Scope.LIBRARY)
                    public static int FIELD2;

                    // Scoped to same library group: whether accessing
                    // from app is okay depends on whether they are in
                    // the same library group (=groupId). In this test
                    // project we don't know what they are so there's
                    // no warning generated.
                    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
                    public static int FIELD3;

                    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
                    public static int FIELD4;

                    public static void method1() {
                    }

                    @RestrictTo(RestrictTo.Scope.LIBRARY)
                    public static void method2() {
                    }

                    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
                    public static void method3() {
                    }

                    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
                    public static void method4() {
                    }
                }
                """
            ).indented(),
            java(
                """
                package test.pkg;

                import com.example.mylibrary.LibraryCode;

                // Access within the same library -- all OK
                public class LibraryCode2 {
                    public void method() {
                        LibraryCode.method1(); // OK
                        LibraryCode.method2(); // OK
                        LibraryCode.method3(); // OK
                        LibraryCode.method4(); // OK
                        int f1 =  LibraryCode.FIELD1; // OK
                        int f2 =  LibraryCode.FIELD2; // OK
                        int f3 =  LibraryCode.FIELD3; // OK
                        int f4 =  LibraryCode.FIELD4; // OK
                    }
                }
                """
            ).indented(),
            gradle(
                """
                    apply plugin: 'com.android.library'
                    group=test.pkg.library
                    """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).name("lib1")

        // Add library3 to test case when group doesn't contain any dots.
        val library3 = project().files(
            java(
                """
                package com.example.dotless;

                import androidx.annotation.RestrictTo;

                public class DotlessCode {
                    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
                    public static void method() {
                    }
                }
                """
            ).indented(),
            gradle(
                """
                    apply plugin: 'com.android.library'
                    group=dotless
                    """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).name("lib3")

        val library2 = project().files(
            kotlin(
                """
                package com.example.myapplication

                import com.example.mylibrary.LibraryCode
                import com.example.dotless.DotlessCode

                fun test() {
                    LibraryCode.method1() // OK
                    LibraryCode.method2() // ERROR
                    LibraryCode.method3() // ERROR
                    LibraryCode.method4() // ERROR
                    val f1 = LibraryCode.FIELD1 // OK
                    val f2 = LibraryCode.FIELD2 // ERROR
                    val f3 = LibraryCode.FIELD3 // ERROR
                    val f4 = LibraryCode.FIELD4 // ERROR
                    DotlessCode.method() // ERROR
                }
                """
            ).indented(),
            gradle(
                """
                    apply plugin: 'com.android.library'
                    group=other.app
                    """
            ).indented()
        ).name("lib2").dependsOn(library).dependsOn(library3)

        // Make sure projects are placed correctly on disk: to do this, record
        // project locations with a special client, then after the lint run make
        // sure the locations are correct.
        library2.under(library)
        library3.under(library)
        var libDir1: File? = null
        var libDir2: File? = null
        var libDir3: File? = null
        val factory: () -> com.android.tools.lint.checks.infrastructure.TestLintClient =
            {
                object : com.android.tools.lint.checks.infrastructure.TestLintClient() {
                    override fun registerProject(dir: File, project: Project) {
                        if (project.name == "lib1") {
                            libDir1 = dir
                        } else if (project.name == "lib2") {
                            libDir2 = dir
                        } else if (project.name == "lib3") {
                            libDir3 = dir
                        }
                        super.registerProject(dir, project)
                    }
                }
            }
        assertEquals("LIBRARY:lib1", library.toString())

        lint()
            .projects(library, library2, library3)
            .reportFrom(library2)
            .clientFactory(factory)
            .run()
            .expect(
                """
                src/main/kotlin/com/example/myapplication/test.kt:8: Error: LibraryCode.method2 can only be called from within the same library (test.pkg.library:test_project-lib1) [RestrictedApi]
                    LibraryCode.method2() // ERROR
                                ~~~~~~~
                src/main/kotlin/com/example/myapplication/test.kt:9: Error: LibraryCode.method3 can only be called from within the same library group (referenced groupId=test.pkg.library from groupId=other.app) [RestrictedApi]
                    LibraryCode.method3() // ERROR
                                ~~~~~~~
                src/main/kotlin/com/example/myapplication/test.kt:10: Error: LibraryCode.method4 can only be called from within the same library group prefix (referenced groupId=test.pkg.library with prefix test.pkg from groupId=other.app) [RestrictedApi]
                    LibraryCode.method4() // ERROR
                                ~~~~~~~
                src/main/kotlin/com/example/myapplication/test.kt:12: Error: LibraryCode.FIELD2 can only be accessed from within the same library (test.pkg.library:test_project-lib1) [RestrictedApi]
                    val f2 = LibraryCode.FIELD2 // ERROR
                                         ~~~~~~
                src/main/kotlin/com/example/myapplication/test.kt:13: Error: LibraryCode.FIELD3 can only be accessed from within the same library group (referenced groupId=test.pkg.library from groupId=other.app) [RestrictedApi]
                    val f3 = LibraryCode.FIELD3 // ERROR
                                         ~~~~~~
                src/main/kotlin/com/example/myapplication/test.kt:14: Error: LibraryCode.FIELD4 can only be accessed from within the same library group prefix (referenced groupId=test.pkg.library with prefix test.pkg from groupId=other.app) [RestrictedApi]
                    val f4 = LibraryCode.FIELD4 // ERROR
                                         ~~~~~~
                src/main/kotlin/com/example/myapplication/test.kt:15: Error: DotlessCode.method can only be called from within the same library group prefix (referenced groupId=dotless with prefix "" from groupId=other.app) [RestrictedApi]
                    DotlessCode.method() // ERROR
                                ~~~~~~
                7 errors, 0 warnings
                """
            )

        // Make sure project directories are laid out correctly
        assertTrue(libDir2!!.parentFile.path == libDir1!!.path)
        assertTrue(libDir3!!.parentFile.path == libDir1!!.path)
    }

    fun test183961872() {
        lint().files(
            java(
                """
                package test.pkg;

                import com.google.common.annotations.VisibleForTesting;
                import com.google.common.annotations.VisibleForTesting.Visibility;

                @SuppressWarnings("unused")
                public class VisibilityTest {
                    @VisibleForTesting(productionVisibility = Visibility.NONE)
                    static JobWriteFailure createError(DriveStatus driveStatus) {
                        return createError(PermanentFailureReason.UNKNOWN, driveStatus);
                    }

                    @VisibleForTesting(productionVisibility = Visibility.NONE)
                    static JobWriteFailure createError(PermanentFailureReason failure, DriveStatus driveStatus) {
                        return null;
                    }

                    private enum PermanentFailureReason { UNKNOWN }
                    private static class DriveStatus { }
                    private static class JobWriteFailure { }
                }
                """
            ).indented(),
            guavaVisibleForTestingAnnotation
        ).allowDuplicates().run().expectClean()
    }

    fun test197123294() {
        // 197123294: Lint is complaining about the wrong method when using += notation
        lint().files(
            kotlin(
                """
                package test.pkg

                class Navigator<D>
                class NavDestination
                class NavigatorProvider  {
                    fun addNavigator(navigator: Navigator<out NavDestination>) { }
                }

                operator fun NavigatorProvider.plusAssign(navigator: Navigator<out NavDestination>) {
                    addNavigator(navigator)
                }

                fun test1(navController: NavController, bottomSheetNavigator: Navigator<out NavDestination>) {
                    navController.navigatorProvider += bottomSheetNavigator
                }
                """
            ).indented(),
            java(
                """
                package test.pkg;

                import androidx.annotation.VisibleForTesting;

                public class NavController {
                    public NavigatorProvider getNavigatorProvider() {
                        return null;
                    }

                    @VisibleForTesting
                    public void setNavigatorProvider(NavigatorProvider navigatorProvider) {
                    }
                }
                """
            ),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun testNonAssignmentLhs() {
        // Similar to test197123294, but makes sure that we only filter out assignments.
        // (The "to" infix function for example is a UastBinaryExpression in the AST so
        // was getting picked up in the first version of the filter for 197123294.)
        lint().files(
            kotlin(
                """
                package test.pkg

                import test.pkg.AbstractAaptOutputParser.AAPT_TOOL_NAME

                private val toolNameToEnumMap = mapOf(
                    "Java compiler" to BuildErrorMessage.ErrorType.JAVA_COMPILER,
                    AAPT_TOOL_NAME to BuildErrorMessage.ErrorType.AAPT,
                    "D8" to BuildErrorMessage.ErrorType.D8
                )
                """
            ).indented(),
            java(
                """
                package test.pkg;
                public class BuildErrorMessage {
                    public enum ErrorType {
                        JAVA_COMPILER,
                        AAPT,
                        D8
                    }
                }
                """
            ).indented(),
            java(
                """
                package test.pkg;
                import androidx.annotation.VisibleForTesting;
                @VisibleForTesting
                public class AbstractAaptOutputParser {
                    public static final String AAPT_TOOL_NAME = "AAPT";
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            """
            src/test/pkg/test.kt:7: Warning: This method should only be accessed from tests or within private scope [VisibleForTests]
                AAPT_TOOL_NAME to BuildErrorMessage.ErrorType.AAPT,
                ~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }

    fun testVisibleForTestingOnConstructorProperty() {
        // This test used to check annotation handling, but we no longer flag
        // parameters in this way so now we just make sure we don't complain
        // here.
        lint().files(
            kotlin(
                """
                package test.pkg

                import androidx.annotation.VisibleForTesting

                class TestClass1(@VisibleForTesting val parameter: String) // defaults to @param: so same as 4
                class TestClass2(@field:VisibleForTesting val parameter: String)
                class TestClass3(@get:VisibleForTesting val parameter: String)
                class TestClass4(@param:VisibleForTesting val parameter: String)
                """
            ).indented(),
            kotlin(
                """
                package test.pkg
                fun test(foo: String) {
                    TestClass1(foo) // OK 1
                    TestClass2(foo) // OK 2
                    TestClass3(foo) // OK 3
                    TestClass4(foo) // OK 4
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun testVisibleForTestingOnClassProperty() {
        // Like testVisibleForTestingOnConstructorProperty but where the property
        // is a class member instead of a constructor one
        lint().files(
            kotlin(
                """
                package test.pkg

                import androidx.annotation.VisibleForTesting

                class TestClass5 { @VisibleForTesting var property: String = "" }
                class TestClass6 { @field:VisibleForTesting var property: String = "" }
                class TestClass7 { @get:VisibleForTesting var property: String = "" }
                class TestClass8 { @set:VisibleForTesting var property: String = "" }
                class TestClass9 { @property:VisibleForTesting var property: String = "" }
                """
            ).indented(),
            kotlin(
                """
                package test.pkg
                fun test(foo: String) {
                    val t5 = TestClass5().property // WARN 1
                    TestClass5().property = "" // WARN 2
                    TestClass6().property // OK 1
                    TestClass6().property = "" // OK 2
                    TestClass7().property // WARN 3
                    TestClass7().property = "" // OK 3
                    TestClass8().property // OK 4
                    TestClass8().property = "" // WARN 4
                    TestClass9().property // WARN 5
                    TestClass9().property = "" // WARN 6
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            """
            src/test/pkg/test.kt:3: Warning: This method should only be accessed from tests or within private scope [VisibleForTests]
                val t5 = TestClass5().property // WARN 1
                                      ~~~~~~~~
            src/test/pkg/test.kt:4: Warning: This method should only be accessed from tests or within private scope [VisibleForTests]
                TestClass5().property = "" // WARN 2
                             ~~~~~~~~
            src/test/pkg/test.kt:7: Warning: This method should only be accessed from tests or within private scope [VisibleForTests]
                TestClass7().property // WARN 3
                             ~~~~~~~~
            src/test/pkg/test.kt:10: Warning: This method should only be accessed from tests or within private scope [VisibleForTests]
                TestClass8().property = "" // WARN 4
                             ~~~~~~~~
            src/test/pkg/test.kt:11: Warning: This method should only be accessed from tests or within private scope [VisibleForTests]
                TestClass9().property // WARN 5
                             ~~~~~~~~
            src/test/pkg/test.kt:12: Warning: This method should only be accessed from tests or within private scope [VisibleForTests]
                TestClass9().property = "" // WARN 6
                             ~~~~~~~~
            0 errors, 6 warnings
            """
        )
    }

    fun testSingleAnnotationHandling() {
        lint().files(
            kotlin(
                """
                package test.pkg

                import androidx.annotation.VisibleForTesting

                open class Foo {
                    @VisibleForTesting
                    var updateCount = 0
                        protected set
                }
                """
            ).indented(),
            java(
                """
                package test.pkg;

                public class Bar extends Foo {
                    public void test() {
                        int count = getUpdateCount() + 1;
                        setUpdateCount(count);
                    }
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).testModes(TestMode.DEFAULT).run().expect(
            """
            src/test/pkg/Bar.java:5: Warning: This method should only be accessed from tests or within private scope [VisibleForTests]
                    int count = getUpdateCount() + 1;
                                ~~~~~~~~~~~~~~
            src/test/pkg/Bar.java:6: Warning: This method should only be accessed from tests or within private scope [VisibleForTests]
                    setUpdateCount(count);
                    ~~~~~~~~~~~~~~
            0 errors, 2 warnings
            """
        )
    }

    private val guavaVisibleForTestingAnnotation: TestFile = java(
        """
        package com.google.common.annotations;
        @SuppressWarnings("ClassNameDiffersFromFileName")
        public @interface VisibleForTesting {
            enum Visibility {
                NONE,
                PRIVATE,
                PACKAGE_PRIVATE,
                PROTECTED
            }
          Visibility productionVisibility() default Visibility.PRIVATE;
        }
        """
    ).indented()

    private val androidVisibleForTestingAnnotation: TestFile = java(
        """
        package com.android.internal.annotations;
        import java.lang.annotation.Retention;
        import java.lang.annotation.RetentionPolicy;
        @Retention(RetentionPolicy.CLASS)
        public @interface VisibleForTesting {
            enum Visibility {
                PROTECTED,
                PACKAGE,
                PRIVATE
            }
            Visibility visibility() default Visibility.PRIVATE;
        }
        """
    ).indented()

    fun testLibraryGroupPrefixMatches() {
        assertTrue(sameLibraryGroupPrefix("foo", "foo"))
        assertFalse(sameLibraryGroupPrefix("foo", "bar"))
        assertTrue(sameLibraryGroupPrefix("foo.bar", "foo.bar"))
        assertFalse(sameLibraryGroupPrefix("foo.bar", "bar"))
        assertFalse(sameLibraryGroupPrefix("foo.bar", "foo"))

        assertTrue(sameLibraryGroupPrefix("foo.bar", "foo.baz"))
        assertTrue(sameLibraryGroupPrefix("com.foo.bar", "com.foo.baz"))
        assertFalse(sameLibraryGroupPrefix("com.foo.bar", "com.bar.qux"))
    }

    fun testParameterAnnotation() {
        // https://www.reddit.com/r/androiddev/comments/sckryz/android_studio_bumblebee_202111_stable/hv0o4ii/
        lint().files(
            kotlin(
                """
                package test.pkg

                import androidx.annotation.VisibleForTesting

                class Thing1
                class Thing2
                class MyClass(
                    @VisibleForTesting val arg1: Thing1,
                    @VisibleForTesting var arg2: Thing2? = null)
                """
            ).indented(),
            kotlin(
                """
                package test.pkg
                fun test() {
                    MyClass(Thing1(), Thing2()) // OK
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun testTestOnly() {
        // Regression test for b/243197340
        lint().files(
            kotlin(
                """
                import androidx.annotation.VisibleForTesting

                class ProductionCode {
                    fun compute() {
                        initialize() // OK
                    }

                    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
                    fun initialize() {
                    }
                }
                """
            ).indented(),
            kotlin(
                """
                import org.jetbrains.annotations.TestOnly;
                class Code {
                    @TestOnly
                    fun test() {
                        ProductionCode().initialize() // Not allowed; this method is intended to be private
                    }
                }
                """
            ),
            java(
                """
                package org.jetbrains.annotations;
                import java.lang.annotation.*;
                @Target({ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.TYPE})
                public @interface TestOnly { }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }
}
