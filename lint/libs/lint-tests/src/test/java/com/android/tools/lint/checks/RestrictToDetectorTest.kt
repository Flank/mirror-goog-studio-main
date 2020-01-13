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
import com.android.tools.lint.detector.api.Detector

class RestrictToDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector = RestrictToDetector()

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

                    import android.support.annotation.RestrictTo;

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
            SUPPORT_ANNOTATIONS_CLASS_PATH,
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
            src/main/java/test/pkg/TestLibrary.java:10: Error: Library.privateMethod can only be called from within the same library group (groupId=my.group.id) [RestrictedApi]
                    Library.privateMethod(); // ERROR
                            ~~~~~~~~~~~~~
            src/main/java/test/pkg/TestLibrary.java:11: Error: PrivateClass can only be called from within the same library group (groupId=my.group.id) [RestrictedApi]
                    PrivateClass.method(); // ERROR
                    ~~~~~~~~~~~~
            src/main/java/test/pkg/TestLibrary.java:12: Error: InternalClass.method can only be called from within the same library group (groupId=my.group.id) [RestrictedApi]
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

                import android.support.annotation.RestrictTo;

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
        ).name("lib")

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
        ).dependsOn(library)

        lint().projects(library, app).run().expect(
            """
            project1/src/com/example/myapplication/test.kt:7: Error: LibraryCode.method2 can only be called from within the same library (lib) [RestrictedApi]
                LibraryCode.method2()
                            ~~~~~~~
            project1/src/com/example/myapplication/test.kt:10: Error: LibraryCode.FIELD2 can only be accessed from within the same library (lib) [RestrictedApi]
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
            src/main/java/test/pkg/Inheriting2.java:6: Error: PrivateClass.method can only be called from within the same library group (groupId=my.group.id) [RestrictedApi]
                    method(); // ERROR - not overridden, pointing into library
                    ~~~~~~
            src/main/java/test/pkg/TestLibrary1.java:5: Error: PrivateClass can only be accessed from within the same library group (groupId=my.group.id) [RestrictedApi]
            public class TestLibrary1 extends PrivateClass {
                                              ~~~~~~~~~~~~
            src/main/java/test/pkg/TestLibrary1.java:8: Error: PrivateClass.method can only be called from within the same library group (groupId=my.group.id) [RestrictedApi]
                    super.method(); // ERROR
                          ~~~~~~
            src/main/java/test/pkg/TestLibrary2.java:5: Error: PrivateClass can only be accessed from within the same library group (groupId=my.group.id) [RestrictedApi]
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
                import android.support.annotation.RestrictTo;
                import android.support.annotation.VisibleForTesting;

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
            SUPPORT_ANNOTATIONS_CLASS_PATH,
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
                import android.support.annotation.VisibleForTesting;

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
                import android.support.annotation.VisibleForTesting;
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
            SUPPORT_ANNOTATIONS_CLASS_PATH,
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(expected)
    }

    fun testVisibleForTestingIncrementally() {
        lint().files(
            java(
                """
                package test.pkg;
                import android.support.annotation.VisibleForTesting;

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
            SUPPORT_ANNOTATIONS_CLASS_PATH,
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
                import android.support.annotation.VisibleForTesting;

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
            SUPPORT_ANNOTATIONS_CLASS_PATH,
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun testGmsHide() {
        // Tests that the @Hide works, and that when applied to classes, it is applied to
        // constructors and to static methods.
        lint().files(
            java(
                "" +
                    "package test.pkg;\n" +
                    "\n" +
                    "import test.pkg.internal.HiddenInPackage;\n" +
                    "\n" +
                    "public class HideTest {\n" +
                    "    public void test() {\n" +
                    "        HiddenInPackage hp = new HiddenInPackage(); // Error\n" +
                    "        HiddenInPackage.test(); // Error!\n" +
                    "        HiddenClass hc = new HiddenClass(); // Error\n" +
                    "        HiddenClass.test(); // Error!\n" +
                    "        PublicClass pc = new PublicClass();  // OK!\n" +
                    "        PublicClass.hiddenMethod(); // Error\n" +
                    "        PublicClass.normalMethod(); // OK!\n" +
                    "    }\n" +
                    "}\n"
            ),
            java(
                "" +
                    // Access from within the GMS codebase should not flag errors
                    "package com.google.android.gms.foo.bar;\n" +
                    "\n" +
                    "import test.pkg.internal.HiddenInPackage;\n" +
                    "\n" +
                    "public class HideTest {\n" +
                    "    public void test() {\n" +
                    "        HiddenInPackage hp = new HiddenInPackage();!\n" +
                    "        HiddenInPackage.test();\n" +
                    "        HiddenClass hc = new HiddenClass();\n" +
                    "        HiddenClass.test();\n" +
                    "        PublicClass pc = new PublicClass();\n" +
                    "        PublicClass.hiddenMethod();\n" +
                    "        PublicClass.normalMethod();\n" +
                    "    }\n" +
                    "}\n"
            ),
            java(
                "" +
                    // Access from within granular SDKs should not flag errors
                    "package com.google.mlkit.foo.bar;\n" +
                    "\n" +
                    "import test.pkg.internal.HiddenInPackage;\n" +
                    "\n" +
                    "public class HideTest {\n" +
                    "    public void test() {\n" +
                    "        HiddenInPackage hp = new HiddenInPackage();!\n" +
                    "        HiddenInPackage.test();\n" +
                    "        HiddenClass hc = new HiddenClass();\n" +
                    "        HiddenClass.test();\n" +
                    "        PublicClass pc = new PublicClass();\n" +
                    "        PublicClass.hiddenMethod();\n" +
                    "        PublicClass.normalMethod();\n" +
                    "    }\n" +
                    "}\n"
            ),
            java(
                "" +
                    "package test.pkg.internal;\n" +
                    "\n" +
                    "public class HiddenInPackage {\n" +
                    "    public static void test() {\n" +
                    "    }\n" +
                    "}\n"
            ),
            java(
                "" +
                    "package test.pkg;\n" +
                    "\n" +
                    "import com.google.android.gms.common.internal.Hide;\n" +
                    "\n" +
                    "@Hide\n" +
                    "public class HiddenClass {\n" +
                    "    public static void test() {\n" +
                    "    }\n" +
                    "}\n"
            ),
            java(
                "" +
                    "package test.pkg;\n" +
                    "\n" +
                    "import com.google.android.gms.common.internal.Hide;\n" +
                    "\n" +
                    "public class PublicClass {\n" +
                    "    public static void normalMethod() {\n" +
                    "    }\n" +
                    "\n" +
                    "    @Hide\n" +
                    "    public static void hiddenMethod() {\n" +
                    "    }\n" +
                    "}\n"
            ),
            java(
                "" +
                    "package com.google.android.gms.common.internal;\n" +
                    "\n" +
                    "import java.lang.annotation.Documented;\n" +
                    "import java.lang.annotation.ElementType;\n" +
                    "import java.lang.annotation.Retention;\n" +
                    "import java.lang.annotation.RetentionPolicy;\n" +
                    "import java.lang.annotation.Target;\n" +
                    "import java.lang.annotation.Target;\n" +
                    "import static java.lang.annotation.ElementType.*;\n" +
                    "@Target({TYPE,FIELD,METHOD,CONSTRUCTOR,PACKAGE})\n" +
                    "@Retention(RetentionPolicy.CLASS)\n" +
                    "public @interface Hide {}"
            ),
            java(
                "src/test/pkg/package-info.java",
                "" +
                    "@Hide\n" +
                    "package test.pkg.internal;\n" +
                    "\n" +
                    "import com.google.android.gms.common.internal.Hide;\n"
            ),
            // Also register the compiled version of the above package-info jar file;
            // without this we don't resolve package annotations
            base64gzip(
                "libs/packageinfoclass.jar",
                "" +
                    "H4sIAAAAAAAAAAvwZmYRYeDg4GC4tYDfmwEJcDKwMPi6hjjqevq56f87xcDA" +
                    "zBDgzc4BkmKCKgnAqVkEiOGafR39PN1cg0P0fN0++5457eOtq3eR11tX69yZ" +
                    "85uDDK4YP3hapOflq+Ppe7F0FQtnxAvJI9KzpF6KLX22RE1suVZGxdJpFqKq" +
                    "ac9EtUVei758mv2p6GMRI9gtbSuDVb2ANnmhuEVhPqpbVIC4JLW4RL8gO10/" +
                    "M68ktSgvMUe/IDE5OzE9VTczLy1fLzknsbjYt9cw75CDgOt/oQOKoRmXXB6x" +
                    "pc0qWZmhpKSoqKoe8SbRNM22+c1WfveDjBYih1RcP3X/X/q/q3znvHMM9wxO" +
                    "T0itKKn4tW2d5g9nJesz/fssfhzY+eLetKnv9x5+Hb7cM+vflbiom65xK6M+" +
                    "efpEt9cER/ge1HFRW5+aHBS0Ilrq3a0pLsLmr5TXLn1S3u76yOziR4F/J+qX" +
                    "H/581+ti9oK36x4p7WXgU/6T1tI+Xy7Z6E2JQvADNlAAHM4XN1kP9N5VcAAw" +
                    "MokwoEYHLKJAcYkKUGIWXStyuIqgaLPFEa/IJoDCH9lhKigmnCQyNgK8WdlA" +
                    "6pmB8DyQPsUI4gEAH9csuq8CAAA="
            )
        ).run().expect(
            "" +
                "src/test/pkg/HideTest.java:7: Error: HiddenInPackage constructor is marked as internal and should not be accessed from apps [RestrictedApi]\n" +
                "        HiddenInPackage hp = new HiddenInPackage(); // Error\n" +
                "                             ~~~~~~~~~~~~~~~~~~~~~\n" +
                "src/test/pkg/HideTest.java:8: Error: HiddenInPackage.test is marked as internal and should not be accessed from apps [RestrictedApi]\n" +
                "        HiddenInPackage.test(); // Error!\n" +
                "                        ~~~~\n" +
                "src/test/pkg/HideTest.java:9: Error: HiddenClass constructor is marked as internal and should not be accessed from apps [RestrictedApi]\n" +
                "        HiddenClass hc = new HiddenClass(); // Error\n" +
                "                         ~~~~~~~~~~~~~~~~~\n" +
                "src/test/pkg/HideTest.java:10: Error: HiddenClass is marked as internal and should not be accessed from apps [RestrictedApi]\n" +
                "        HiddenClass.test(); // Error!\n" +
                "        ~~~~~~~~~~~\n" +
                "src/test/pkg/HideTest.java:12: Error: PublicClass.hiddenMethod is marked as internal and should not be accessed from apps [RestrictedApi]\n" +
                "        PublicClass.hiddenMethod(); // Error\n" +
                "                    ~~~~~~~~~~~~\n" +
                "5 errors, 0 warnings"
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
                import android.support.annotation.VisibleForTesting;
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
            // From Guava; also Apache licensed
            java(
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
                import android.support.annotation.RestrictTo;
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

    fun testGmsHideInheritedAnnotation() {
        // Ensure that @Hide on a superclass doesn't automatically mean that all members
        // are hidden too. It is valid for methods to be inherited non-hidden from hidden
        // superclasses (e.g. an abstract superclass). Only the static methods and constructors
        // should automatically follow the @Hide of their containing class.
        lint().files(
            java(
                "src/test/pkg/HideTest.java",
                "" +
                    "package test.pkg;\n" +
                    "\n" +
                    "import com.google.android.gms.foo.bar.HiddenClass;\n" +
                    "import com.google.android.gms.foo.bar.SubclassHiddenClass;\n" +
                    "import com.google.android.gms.foo.bar.SubclassHiddenInPackage;\n" +
                    "\n" +
                    "public class HideTest {\n" +
                    "    public void test() {\n" +
                    "        HiddenClass h = new HiddenClass(); // Error\n" +
                    "        h.hidden();  // Error\n" +
                    "        h.overrideNotHidden();  // Error\n" +
                    "        h.notHidden(); // OK!\n" +
                    "        SubclassHiddenClass s1 = new SubclassHiddenClass();\n" +
                    "        s1.hidden(); // Error\n" +
                    "        s1.overrideNotHidden(); // OK!\n" +
                    "        s1.notHidden(); // OK!\n" +
                    "        SubclassHiddenInPackage s2 = new SubclassHiddenInPackage();\n" +
                    "        s2.hidden(); // Error\n" +
                    "        s2.overrideNotHidden(); // OK!\n" +
                    "        s2.notHidden(); // OK!\n" +
                    "    }\n" +
                    "}\n"
            ),
            java(
                "" +
                    "package com.google.android.gms.foo.bar;\n" +
                    "\n" +
                    "import test.pkg.HiddenClass;\n" +
                    "\n" +
                    "public class SubclassHiddenClass extends HiddenClass {\n" +
                    "    @Override\n" +
                    "    public void overrideNotHidden(){\n" +
                    "    }\n" +
                    "}\n"
            ),
            java(
                "" +
                    "package com.google.android.gms.foo.bar;\n" +
                    "\n" +
                    "import test.pkg.internal.HiddenInPackage;\n" +
                    "\n" +
                    "public class SubclassHiddenInPackage extends HiddenInPackage {\n" +
                    "    @Override\n" +
                    "    public void overrideNotHidden(){\n" +
                    "    }\n" +
                    "}\n"
            ),
            java(
                "" +
                    "package test.pkg.internal;\n" +
                    "\n" +
                    "import com.google.android.gms.common.internal.Hide;\n" +
                    "\n" +
                    "public class HiddenInPackage {\n" +
                    "    @Hide\n" +
                    "    public void hidden() {\n" +
                    "    }\n" +
                    "    @Hide\n" +
                    "    public void overrideNotHidden() {\n" +
                    "    }\n" +
                    "    public void notHidden() {\n" +
                    "    }\n" +
                    "}\n"
            ),
            java(
                "" +
                    "package test.pkg;\n" +
                    "\n" +
                    "import com.google.android.gms.common.internal.Hide;\n" +
                    "\n" +
                    "@Hide\n" +
                    "public class HiddenClass {\n" +
                    "    public HiddenClass() {\n" +
                    "    }\n" +
                    "    @Hide\n" +
                    "    public void hidden() {\n" +
                    "    }\n" +
                    "    @Hide\n" +
                    "    public void overrideNotHidden() {\n" +
                    "    }\n" +
                    "    public void notHidden() {\n" +
                    "    }\n" +
                    "}\n"
            ),
            java(
                "" +
                    "package com.google.android.gms.common.internal;\n" +
                    "\n" +
                    "import java.lang.annotation.Documented;\n" +
                    "import java.lang.annotation.ElementType;\n" +
                    "import java.lang.annotation.Retention;\n" +
                    "import java.lang.annotation.RetentionPolicy;\n" +
                    "import java.lang.annotation.Target;\n" +
                    "import static java.lang.annotation.ElementType.*;\n" +
                    "@Target({TYPE,FIELD,METHOD,CONSTRUCTOR,PACKAGE})\n" +
                    "@Retention(RetentionPolicy.CLASS)\n" +
                    "public @interface Hide {}"
            ),
            java(
                "src/test/pkg/internal/package-info.java",
                "" +
                    "@Hide\n" +
                    "package test.pkg.internal;\n" +
                    "\n" +
                    "import com.google.android.gms.common.internal.Hide;\n"
            ),
            // Also register the compiled version of the above package-info jar file;
            // without this we don't resolve package annotations
            base64gzip(
                "libs/packageinfoclass.jar",
                "" +
                    "H4sIAAAAAAAAAAvwZmYRYeDg4GC4tYDfmwEJcDKwMPi6hjjqevq56f87xcDA" +
                    "zBDgzc4BkmKCKgnAqVkEiOGafR39PN1cg0P0fN0++5457eOtq3eR11tX69yZ" +
                    "85uDDK4YP3hapOflq+Ppe7F0FQtnxAvJI9KzpF6KLX22RE1suVZGxdJpFqKq" +
                    "ac9EtUVei758mv2p6GMRI9gtbSuDVb2ANnmhuEVhPqpbVIC4JLW4RL8gO10/" +
                    "M68ktSgvMUe/IDE5OzE9VTczLy1fLzknsbjYt9cw75CDgOt/oQOKoRmXXB6x" +
                    "pc0qWZmhpKSoqKoe8SbRNM22+c1WfveDjBYih1RcP3X/X/q/q3znvHMM9wxO" +
                    "T0itKKn4tW2d5g9nJesz/fssfhzY+eLetKnv9x5+Hb7cM+vflbiom65xK6M+" +
                    "efpEt9cER/ge1HFRW5+aHBS0Ilrq3a0pLsLmr5TXLn1S3u76yOziR4F/J+qX" +
                    "H/581+ti9oK36x4p7WXgU/6T1tI+Xy7Z6E2JQvADNlAAHM4XN1kP9N5VcAAw" +
                    "MokwoEYHLKJAcYkKUGIWXStyuIqgaLPFEa/IJoDCH9lhKigmnCQyNgK8WdlA" +
                    "6pmB8DyQPsUI4gEAH9csuq8CAAA="
            )
        ).run().expect(
            "" +
                "src/test/pkg/HideTest.java:9: Error: HiddenClass is marked as internal and should not be accessed from apps [RestrictedApi]\n" +
                "        HiddenClass h = new HiddenClass(); // Error\n" +
                "                        ~~~~~~~~~~~~~~~~~\n" +
                "src/test/pkg/HideTest.java:10: Error: HiddenClass.hidden is marked as internal and should not be accessed from apps [RestrictedApi]\n" +
                "        h.hidden();  // Error\n" +
                "          ~~~~~~\n" +
                "src/test/pkg/HideTest.java:11: Error: HiddenClass.overrideNotHidden is marked as internal and should not be accessed from apps [RestrictedApi]\n" +
                "        h.overrideNotHidden();  // Error\n" +
                "          ~~~~~~~~~~~~~~~~~\n" +
                "src/test/pkg/HideTest.java:14: Error: HiddenClass.hidden is marked as internal and should not be accessed from apps [RestrictedApi]\n" +
                "        s1.hidden(); // Error\n" +
                "           ~~~~~~\n" +
                "src/test/pkg/HideTest.java:18: Error: HiddenInPackage.hidden is marked as internal and should not be accessed from apps [RestrictedApi]\n" +
                "        s2.hidden(); // Error\n" +
                "           ~~~~~~\n" +
                "5 errors, 0 warnings"
        )
    }

    fun testPrivateVisibilityWithDefaultConstructor() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=235661
        lint().files(
            java(
                "" +
                    "package test.pkg;\n" +
                    "\n" +
                    "import android.support.annotation.VisibleForTesting;\n" +
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
            SUPPORT_ANNOTATIONS_CLASS_PATH,
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
                    "import android.support.annotation.VisibleForTesting\n" +
                    "\n" +
                    "fun foo() {\n" +
                    "    AndroidOSVersionChecker()\n" +
                    "}\n" +
                    "\n" +
                    "@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)\n" +
                    "internal class AndroidOSVersionChecker2 {\n" +
                    "}"
            ),
            SUPPORT_ANNOTATIONS_CLASS_PATH,
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun testVisibleForTestingInternalKotlin() {
        lint().files(
            kotlin(
                """
                package test.pkg

                import android.os.Bundle
                import android.app.Acativity
                import android.support.annotation.VisibleForTesting
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
            SUPPORT_ANNOTATIONS_CLASS_PATH,
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

                import android.support.annotation.RestrictTo

                @RestrictTo(RestrictTo.Scope.TESTS)
                open class RestrictedClass
                """
            ).indented(),
            kotlin(
                """
                package test.pkg

                import android.support.annotation.RestrictTo

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
            SUPPORT_ANNOTATIONS_CLASS_PATH,
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
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
                import android.support.annotation.VisibleForTesting
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
            SUPPORT_ANNOTATIONS_CLASS_PATH,
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

                import android.support.annotation.RestrictTo;
                import static android.support.annotation.RestrictTo.Scope.TESTS;

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
            SUPPORT_ANNOTATIONS_CLASS_PATH,
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

                import android.support.annotation.VisibleForTesting
                import android.support.annotation.VisibleForTesting.NONE

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
            SUPPORT_ANNOTATIONS_CLASS_PATH,
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            """
            src/test/pkg/Foo.kt:29: Error: Foo.getHiddenProp can only be called from tests [RestrictedApi]
                    f.hiddenProp
                      ~~~~~~~~~~
            src/test/pkg/Foo.kt:31: Error: Foo.getHiddenProp can only be called from tests [RestrictedApi]
                    f.hiddenProp = ""
                      ~~~~~~~~~~
            src/test/pkg/Foo.kt:33: Error: Foo.hiddenFunc can only be called from tests [RestrictedApi]
                    f.hiddenFunc()
                      ~~~~~~~~~~
            src/test/pkg/Foo.kt:37: Error: Foo.getHiddenProp can only be called from tests [RestrictedApi]
                    b.hiddenProp
                      ~~~~~~~~~~
            src/test/pkg/Foo.kt:39: Error: Foo.getHiddenProp can only be called from tests [RestrictedApi]
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

                import android.support.annotation.VisibleForTesting

                class MyViewModel {
                    @get:VisibleForTesting
                    internal var currentNamespace: String? = null
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
            SUPPORT_ANNOTATIONS_CLASS_PATH,
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            """
            src/test/pkg/MyActivity.kt:6: Warning: This method should only be accessed from tests or within private scope [VisibleForTests]
                    val foo = myViewModel.currentNamespace.orEmpty()
                                          ~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
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

                    import android.support.annotation.RestrictTo;

                    public class Library {
                        public static void method() {
                        }

                        @RestrictTo(RestrictTo.Scope.GROUP_ID)
                        public static void privateMethod() {
                        }
                    }

                PrivateClass.java:
                    package library.pkg;

                    import android.support.annotation.RestrictTo;

                    @RestrictTo(RestrictTo.Scope.GROUP_ID)
                    public class PrivateClass {
                        public static void method() {
                        }
                    }

                package-info.java:
                    @RestrictTo(RestrictTo.Scope.GROUP_ID)
                    package library.pkg.internal;

                    import android.support.annotation.RestrictTo;

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
            restrictToAnnotation
        ).name("lib1")

        val library2 = project().files(
            kotlin(
                """
                package com.example.myapplication

                import com.example.mylibrary.LibraryCode

                fun test() {
                    LibraryCode.method1()
                    LibraryCode.method2()
                    LibraryCode.method3()
                    LibraryCode.method4()
                    val f1 = LibraryCode.FIELD1
                    val f2 = LibraryCode.FIELD2
                    val f3 = LibraryCode.FIELD3
                    val f4 = LibraryCode.FIELD4
                }
                """
            ).indented(),
            gradle(
                """
                    apply plugin: 'com.android.library'
                    group=other.app
                    """
            ).indented()
        ).name("lib2").dependsOn(library)

        lint().projects(library, library2).run().expect(
            """
            lib2/src/main/kotlin/com/example/myapplication/test.kt:8: Error: LibraryCode.method3 can only be called from within the same library group (groupId=test.pkg.library) [RestrictedApi]
                LibraryCode.method3()
                            ~~~~~~~
            lib2/src/main/kotlin/com/example/myapplication/test.kt:9: Error: LibraryCode.method4 can only be called from within the same library group prefix (referenced groupId=test.pkg.library with prefix test.pkg from groupId=other.app) [RestrictedApi]
                LibraryCode.method4()
                            ~~~~~~~
            lib2/src/main/kotlin/com/example/myapplication/test.kt:12: Error: LibraryCode.FIELD3 can only be accessed from within the same library group (groupId=test.pkg.library) [RestrictedApi]
                val f3 = LibraryCode.FIELD3
                                     ~~~~~~
            lib2/src/main/kotlin/com/example/myapplication/test.kt:13: Error: LibraryCode.FIELD4 can only be accessed from within the same library group prefix (referenced groupId=test.pkg.library with prefix test.pkg from groupId=other.app) [RestrictedApi]
                val f4 = LibraryCode.FIELD4
                                     ~~~~~~
            4 errors, 0 warnings
            """
        )
    }

    val restrictToAnnotation = java(
        """
        package androidx.annotation;

        import static java.lang.annotation.ElementType.*;
        import static java.lang.annotation.RetentionPolicy.CLASS;
        import java.lang.annotation.*;
        @Retention(CLASS)
        @Target({ANNOTATION_TYPE, TYPE, METHOD, CONSTRUCTOR, FIELD, PACKAGE})
        public @interface RestrictTo {
            Scope[] value();
            enum Scope {
                LIBRARY,
                LIBRARY_GROUP,
                LIBRARY_GROUP_PREFIX,
                @Deprecated
                GROUP_ID,
                TESTS,
                SUBCLASSES,
            }
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
}
