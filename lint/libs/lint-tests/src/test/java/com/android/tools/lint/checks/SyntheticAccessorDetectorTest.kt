/*
 * Copyright (C) 2018 The Android Open Source Project
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

class SyntheticAccessorDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return SyntheticAccessorDetector()
    }

    fun testBasicJava() {
        lint().files(
            java(
                """
                package test.pkg;

                @SuppressWarnings({"unused", "WeakerAccess", "FieldCanBeLocal", "ClassNameDiffersFromFileName"})
                public class AccessTest {

                    private int field1;
                    int field2;
                    public int field3;
                    private final int field4 = 100;
                    private final Inner[] field5 = new Inner[100];


                    private AccessTest() {
                    }

                    AccessTest(int x) {
                    }

                    private void method1() {
                        int f = field1; // OK - private but same class
                    }

                    void method2() {
                        method1(); // OK - private but same class
                    }

                    public void method3() {
                    }

                    class Inner {
                        @SuppressWarnings("ResultOfObjectAllocationIgnored")
                        private void innerMethod() {
                            new AccessTest(); // ERROR
                            new AccessTest(42); // OK - package private

                            int f1 = field1; // ERROR
                            int f2 = field2; // OK - package private
                            int f3 = field3; // OK - public
                            int f4 = field4; // OK (constants inlined)
                            Inner[] f5 = field5; // ERROR

                            method1(); // ERROR
                            method2(); // OK - package private
                            method3(); // OK - public
                        }

                        private void testSuppress() {
                            //noinspection SyntheticAccessor
                            method1(); // OK - suppressed
                            //noinspection PrivateMemberAccessBetweenOuterAndInnerClass
                            method1(); // OK - suppressed with IntelliJ similar inspection id
                            //noinspection SyntheticAccessorCall
                            method1(); // OK - suppressed with IntelliJ similar inspection id
                        }
                    }

                    @SuppressWarnings("ResultOfObjectAllocationIgnored")
                    public void viaAnonymousInner() {
                        Object btn = new Object() {
                            public void method4() {
                                new AccessTest(); // ERROR
                                new AccessTest(42); // OK - package private

                                int f1 = field1; // ERROR
                                int f2 = field2; // OK - package private
                                int f3 = field3; // OK - public
                                int f4 = field4; // OK (constants inlined)
                                Inner[] f5 = field5; // ERROR

                                method1(); // ERROR
                                method2(); // OK - package private
                                method3(); // OK - public
                            }
                        };
                    }
                }

                @SuppressWarnings("ClassNameDiffersFromFileName")
                class Outer {
                    void method(AccessTest o) {
                        // TODO: Shouldn't flag this: compiler won't accept it anyway because it's a private reference
                        //   int f = o.field1;
                    }
                }
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/AccessTest.java:33: Warning: Access to private constructor of class AccessTest requires synthetic accessor [SyntheticAccessor]
                        new AccessTest(); // ERROR
                        ~~~~~~~~~~~~~~~~
            src/test/pkg/AccessTest.java:36: Warning: Access to private field field1 of class AccessTest requires synthetic accessor [SyntheticAccessor]
                        int f1 = field1; // ERROR
                                 ~~~~~~
            src/test/pkg/AccessTest.java:40: Warning: Access to private field field5 of class AccessTest requires synthetic accessor [SyntheticAccessor]
                        Inner[] f5 = field5; // ERROR
                                     ~~~~~~
            src/test/pkg/AccessTest.java:42: Warning: Access to private method method1 of class AccessTest requires synthetic accessor [SyntheticAccessor]
                        method1(); // ERROR
                        ~~~~~~~
            src/test/pkg/AccessTest.java:61: Warning: Access to private constructor of class AccessTest requires synthetic accessor [SyntheticAccessor]
                            new AccessTest(); // ERROR
                            ~~~~~~~~~~~~~~~~
            src/test/pkg/AccessTest.java:64: Warning: Access to private field field1 of class AccessTest requires synthetic accessor [SyntheticAccessor]
                            int f1 = field1; // ERROR
                                     ~~~~~~
            src/test/pkg/AccessTest.java:68: Warning: Access to private field field5 of class AccessTest requires synthetic accessor [SyntheticAccessor]
                            Inner[] f5 = field5; // ERROR
                                         ~~~~~~
            src/test/pkg/AccessTest.java:70: Warning: Access to private method method1 of class AccessTest requires synthetic accessor [SyntheticAccessor]
                            method1(); // ERROR
                            ~~~~~~~
            0 errors, 8 warnings
            """
        ).expectFixDiffs(
            """
                Fix for src/test/pkg/AccessTest.java line 33: Make package protected:
                @@ -13 +13
                -     private AccessTest() {
                +     AccessTest() {
                Fix for src/test/pkg/AccessTest.java line 36: Make package protected:
                @@ -6 +6
                -     private int field1;
                +     int field1;
                Fix for src/test/pkg/AccessTest.java line 40: Make package protected:
                @@ -10 +10
                -     private final Inner[] field5 = new Inner[100];
                +     final Inner[] field5 = new Inner[100];
                Fix for src/test/pkg/AccessTest.java line 42: Make package protected:
                @@ -19 +19
                -     private void method1() {
                +     void method1() {
                Fix for src/test/pkg/AccessTest.java line 61: Make package protected:
                @@ -13 +13
                -     private AccessTest() {
                +     AccessTest() {
                Fix for src/test/pkg/AccessTest.java line 64: Make package protected:
                @@ -6 +6
                -     private int field1;
                +     int field1;
                Fix for src/test/pkg/AccessTest.java line 68: Make package protected:
                @@ -10 +10
                -     private final Inner[] field5 = new Inner[100];
                +     final Inner[] field5 = new Inner[100];
                Fix for src/test/pkg/AccessTest.java line 70: Make package protected:
                @@ -19 +19
                -     private void method1() {
                +     void method1() {
                """
        )
    }

    fun testBasicKotlin() {
        lint().files(
            kotlin(
                """
                package test.pkg

                @Suppress("UNUSED_PARAMETER", "unused", "UNUSED_VARIABLE")
                class AccessTest2 {

                    private val field1: Int = 0
                    internal var field2: Int = 0
                    var field3: Int = 0
                    private val field4 = 100
                    private val field5 = arrayOfNulls<Inner>(100)


                    private constructor()

                    internal constructor(x: Int)

                    private fun method1() {
                        val f = field1 // OK - private but same class
                    }

                    internal fun method2() {
                        method1() // OK - private but same class
                    }

                    fun method3() {}

                    internal inner class Inner {
                        private fun innerMethod() {
                            AccessTest2()   // ERROR
                            AccessTest2(42) // OK - package private

                            val f1 = field1 // ERROR
                            val f2 = field2 // OK - package private
                            val f3 = field3 // OK - public
                            val f4 = field4 // OK (constants inlined)
                            val f5 = field5 // ERROR

                            method1() // ERROR
                            method2() // OK - package private
                            method3() // OK - public
                        }

                        private fun testSuppress() {
                            //noinspection SyntheticAccessor
                            method1() // OK - suppressed
                            //noinspection PrivateMemberAccessBetweenOuterAndInnerClass
                            method1() // OK - suppressed with IntelliJ similar inspection id
                            //noinspection SyntheticAccessorCall
                            method1() // OK - suppressed with IntelliJ similar inspection id
                        }
                    }

                    fun viaAnonymousInner() {
                        val btn = object : Any() {
                            fun method4() {
                                AccessTest() // ERROR
                                AccessTest(42) // OK - package private

                                val f1 = field1 // ERROR
                                val f2 = field2 // OK - package private
                                val f3 = field3 // OK - public
                                val f4 = field4 // OK (constants inlined)
                                val f5 = field5 // ERROR

                                method1() // ERROR
                                method2() // OK - package private
                                method3() // OK - public
                            }
                        }
                    }
                }
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/AccessTest2.kt:29: Warning: Access to private constructor of class AccessTest2 requires synthetic accessor [SyntheticAccessor]
                        AccessTest2()   // ERROR
                        ~~~~~~~~~~~
            src/test/pkg/AccessTest2.kt:36: Warning: Access to private field field5 of class AccessTest2 requires synthetic accessor [SyntheticAccessor]
                        val f5 = field5 // ERROR
                                 ~~~~~~
            src/test/pkg/AccessTest2.kt:38: Warning: Access to private method method1 of class AccessTest2 requires synthetic accessor [SyntheticAccessor]
                        method1() // ERROR
                        ~~~~~~~
            src/test/pkg/AccessTest2.kt:63: Warning: Access to private field field5 of class AccessTest2 requires synthetic accessor [SyntheticAccessor]
                            val f5 = field5 // ERROR
                                     ~~~~~~
            src/test/pkg/AccessTest2.kt:65: Warning: Access to private method method1 of class AccessTest2 requires synthetic accessor [SyntheticAccessor]
                            method1() // ERROR
                            ~~~~~~~
            0 errors, 5 warnings
            """
        ).expectFixDiffs(
            """
                Fix for src/test/pkg/AccessTest2.kt line 29: Make internal:
                @@ -13 +13
                -     private constructor()
                +     internal constructor()
                Fix for src/test/pkg/AccessTest2.kt line 36: Make internal:
                @@ -10 +10
                -     private val field5 = arrayOfNulls<Inner>(100)
                +     internal val field5 = arrayOfNulls<Inner>(100)
                Fix for src/test/pkg/AccessTest2.kt line 38: Make internal:
                @@ -17 +17
                -     private fun method1() {
                +     internal fun method1() {
                Fix for src/test/pkg/AccessTest2.kt line 63: Make internal:
                @@ -10 +10
                -     private val field5 = arrayOfNulls<Inner>(100)
                +     internal val field5 = arrayOfNulls<Inner>(100)
                Fix for src/test/pkg/AccessTest2.kt line 65: Make internal:
                @@ -17 +17
                -     private fun method1() {
                +     internal fun method1() {
                """
        )
    }

    fun testScenario() {
        lint().files(
            java(
                """
                package test.pkg;

                @SuppressWarnings({"unused", "WeakerAccess", "FieldCanBeLocal", "ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                class AccessTest3 {
                    void test() {
                        Hidden hidden1 = new Hidden(42); // OK
                        Hidden2 hidden2 = new Hidden2(); // ERROR
                        int f = hidden1.field; // ERROR
                        new HiddenAccess().method2(hidden1); // OK
                        HiddenAccess.method1(hidden1); // OK
                    }

                    private static class Hidden {
                        private final int field;

                        Hidden(int value) {
                            this.field = value;
                        }
                    }

                    private static class Hidden2 { // synthetic constructor
                    }

                    private static class HiddenAccess {
                        HiddenAccess() { // no synthetic constructor
                        }

                        static void method1(Hidden hidden) {
                            int f = hidden.field; // ERROR
                        }

                        void method2(Hidden hidden) {
                            int f = hidden.field; // ERROR
                        }
                    }
                }
                """
            ).indented()
        ).run().expect(
            """
                src/test/pkg/AccessTest3.java:7: Warning: Access to private member of class Hidden2 requires synthetic accessor [SyntheticAccessor]
                        Hidden2 hidden2 = new Hidden2(); // ERROR
                                          ~~~~~~~~~~~~~
                src/test/pkg/AccessTest3.java:8: Warning: Access to private field field of class Hidden requires synthetic accessor [SyntheticAccessor]
                        int f = hidden1.field; // ERROR
                                        ~~~~~
                src/test/pkg/AccessTest3.java:29: Warning: Access to private field field of class Hidden requires synthetic accessor [SyntheticAccessor]
                            int f = hidden.field; // ERROR
                                           ~~~~~
                src/test/pkg/AccessTest3.java:33: Warning: Access to private field field of class Hidden requires synthetic accessor [SyntheticAccessor]
                            int f = hidden.field; // ERROR
                                           ~~~~~
                0 errors, 4 warnings
                """
        ).expectFixDiffs(
            // TODO: Here I shouldn't make the private class public, I should add a new package private constructor!
            """
                Fix for src/test/pkg/AccessTest3.java line 7: Make package protected:
                @@ -21 +21
                -     private static class Hidden2 { // synthetic constructor
                +     static class Hidden2 { // synthetic constructor
                Fix for src/test/pkg/AccessTest3.java line 8: Make package protected:
                @@ -14 +14
                -         private final int field;
                +         final int field;
                Fix for src/test/pkg/AccessTest3.java line 29: Make package protected:
                @@ -14 +14
                -         private final int field;
                +         final int field;
                Fix for src/test/pkg/AccessTest3.java line 33: Make package protected:
                @@ -14 +14
                -         private final int field;
                +         final int field;
                """
        )
    }

    fun testArrays() {
        lint().files(
            java(
                """
                package test.pkg;

                @SuppressWarnings("ClassNameDiffersFromFileName")
                final class Outer { // IDEA-153599
                    private static class Inner { }
                    public static void main(String[] args) {
                        Inner[] inners = new Inner[5];
                    }
                }

                """
            ).indented()
        ).run().expectClean()
    }

    fun testSealed() {
        // Regression test for
        // 78144888: SyntheticAccessor Kotlin false positive
        lint().files(
            kotlin(
                """
                package test.pkg

                private sealed class LoaderEvent {
                    object ForceSync : LoaderEvent()
                    data class LoadResult(val listing: String, val success: Boolean) : LoaderEvent()
                }
                """
            ).indented()
        ).run().expectClean()
    }

    fun testStdlib() {
        // Some inline stdlib methods are marked as "private" in the bytecode; don't flag these
        lint().files(
            kotlin(
                """
                package test.pkg

                class Foo {
                    fun foo(scheme: String) {
                        require(scheme == "file") {
                            "Uri lacks 'file' scheme: " + this
                        }
                    }
                }
                """
            ).indented()
        ).run().expectClean()
    }

    fun testCompanion() {
        // Regression test for https://issuetracker.google.com/113119778
        lint().files(
            kotlin(
                """
                package test.pkg

                class Foo private constructor() {
                    companion object {
                        fun gimme() = Foo()
                    }
                }
                """
            ).indented()
        ).run().expectClean()
    }

    fun testSyntheticKotlin() {
        // Regression test for
        // 118790640: Invalid synthetic accessor check for sealed classes
        lint().files(
            kotlin(
                """
                package test.pkg

                private sealed class SettingsConsentAdapterItem(val id: String) {
                    class Header(id: String, val name: String) : SettingsConsentAdapterItem(id)
                    class Item(val groupId: String, id: String, val name: String, val checked: Boolean) : SettingsConsentAdapterItem(id)
                }
                """
            ).indented()
        ).run().expectClean()
    }
}
