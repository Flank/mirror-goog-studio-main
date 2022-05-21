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

import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector

class CheckResultDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector = CheckResultDetector()

    fun testDocumentationExample() {
        lint().files(
            kotlin(
                """
                package test.pkg

                import androidx.annotation.CheckResult
                import java.math.BigDecimal

                @CheckResult
                fun BigDecimal.double() = this + this

                fun test(score: BigDecimal): BigDecimal {
                    score.double()
                    return score
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            """
            src/test/pkg/test.kt:10: Warning: The result of double is not used [CheckResult]
                score.double()
                ~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }

    fun test191378558() {
        // Regression test for https://issuetracker.google.com/191378558
        lint().files(
            kotlin(
                """
                package test.pkg

                import androidx.annotation.CheckResult

                fun test() {
                    if (checkBoolean()) {
                        println()
                    }
                }

                @CheckResult
                fun checkBoolean(): Boolean = true
                """
            ).indented(),
            java(
                """
                package test.pkg;

                import androidx.annotation.CheckResult;

                public class CheckResultTest {
                    @CheckResult
                    private boolean checkBoolean() {
                        return true;
                    }

                    public void test() {
                        if (checkBoolean()) {
                            System.out.println();
                        }
                    }
                }
                """
            ).indented(),
            kotlin(
                """
                package test.pkg

                import android.Manifest
                import android.content.Context
                import android.os.Build

                fun test(context: Context) {
                    if (context.packageManager.isPermissionRevokedByPolicy(
                            Manifest.permission.ACCESS_FINE_LOCATION, context.packageName)) {
                        println()
                    }
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun testCheckResult() {
        val expected =
            """
            src/test/pkg/CheckPermissions.java:22: Warning: The result of extractAlpha is not used [CheckResult]
                    bitmap.extractAlpha(); // WARNING
                    ~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/Intersect.java:7: Warning: The result of intersect is not used. If the rectangles do not intersect, no change is made and the original rectangle is not modified. These methods return false to indicate that this has happened. [CheckResult]
                rect.intersect(aLeft, aTop, aRight, aBottom);
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/CheckPermissions.java:10: Warning: The result of checkCallingOrSelfPermission is not used; did you mean to call #enforceCallingOrSelfPermission(String,String)? [UseCheckPermission]
                    context.checkCallingOrSelfPermission(Manifest.permission.INTERNET); // WRONG
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/CheckPermissions.java:11: Warning: The result of checkPermission is not used; did you mean to call #enforcePermission(String,int,int,String)? [UseCheckPermission]
                    context.checkPermission(Manifest.permission.INTERNET, 1, 1);
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 4 warnings
            """
        lint().files(
            java(
                """
                package test.pkg;
                import android.Manifest;
                import android.content.Context;
                import android.content.pm.PackageManager;
                import android.graphics.Bitmap;

                @SuppressWarnings("ClassNameDiffersFromFileName")
                public class CheckPermissions {
                    private void foo(Context context) {
                        context.checkCallingOrSelfPermission(Manifest.permission.INTERNET); // WRONG
                        context.checkPermission(Manifest.permission.INTERNET, 1, 1);
                        check(context.checkCallingOrSelfPermission(Manifest.permission.INTERNET)); // OK
                        int check = context.checkCallingOrSelfPermission(Manifest.permission.INTERNET); // OK
                        if (context.checkCallingOrSelfPermission(Manifest.permission.INTERNET) // OK
                                != PackageManager.PERMISSION_GRANTED) {
                            showAlert(context, "Error",
                                    "Application requires permission to access the Internet");
                        }
                    }

                    private Bitmap checkResult(Bitmap bitmap) {
                        bitmap.extractAlpha(); // WARNING
                        Bitmap bitmap2 = bitmap.extractAlpha(); // OK
                        call(bitmap.extractAlpha()); // OK
                        return bitmap.extractAlpha(); // OK
                    }

                    private void showAlert(Context context, String error, String s) {
                    }

                    private void check(int i) {
                    }
                    private void call(Bitmap bitmap) {
                    }
                }"""
            ).indented(),

            java(
                "src/test/pkg/Intersect.java",
                """
                package test.pkg;
                import android.graphics.Rect;

                @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class Intersect {
                  void check(Rect rect, int aLeft, int aTop, int aRight, int aBottom) {
                    rect.intersect(aLeft, aTop, aRight, aBottom);
                  }
                }"""
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        )
            .issues(CheckResultDetector.CHECK_RESULT, CheckResultDetector.CHECK_PERMISSION)
            .run()
            .expect(expected)
    }

    fun testSubtract() {
        // Regression test for https://issuetracker.google.com/69344103:
        // @CanIgnoreReturnValue should let you *undo* a @CheckReturnValue on a class/package
        lint().files(
            java(
                """
                    package test.pkg;
                    import com.google.errorprone.annotations.CanIgnoreReturnValue;
                    import javax.annotation.CheckReturnValue;

                    @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic", "ResultOfMethodCallIgnored"})
                    @CheckReturnValue
                    public class IgnoreTest {
                        public String method1() {
                            return "";
                        }

                        public void method2() {
                        }

                        @CanIgnoreReturnValue
                        public String method3() {
                            return "";
                        }

                        public void test() {
                            method1(); // ERROR: should check
                            method2(); // OK: void return value
                            method3(); // OK: Specifically allowed
                        }
                    }
                """
            ).indented(),
            errorProneCanIgnoreReturnValueSource,
            javaxCheckReturnValueSource,
            SUPPORT_ANNOTATIONS_JAR
        )
            .issues(CheckResultDetector.CHECK_RESULT, CheckResultDetector.CHECK_PERMISSION)
            .run()
            .expect(
                """
                src/test/pkg/IgnoreTest.java:21: Warning: The result of method1 is not used [CheckResult]
                        method1(); // ERROR: should check
                        ~~~~~~~~~
                0 errors, 1 warnings
                """
            )
    }

    fun testSubtract2() {
        // Regression test for 196984792: Incomplete handling of @CheckReturnValue annotations on scopes
        // Make sure we properly handle scopes on @CheckReturnValue and @CanIgnoreReturnValue; the closest
        // one should win (and should be inherited all the way down from package annotations)
        lint().files(
            java(
                """
                package test.pkg;
                import static test.pkg.IgnoreTest.MyClass.MyClass1.ignoredFromOuterClassAnnotation;
                import static test.pkg.IgnoreTest.MyClass.MyClass2.checkedFromOuterClassAnnotation;
                import static test.pkg.IgnoreTest.MyClass.checkedFromMethodAnnotation;
                import static test.pkg.IgnoreTest.MyClass.ignoredFromClassAnnotation;
                import com.google.errorprone.annotations.CanIgnoreReturnValue;
                import javax.annotation.CheckReturnValue;

                @SuppressWarnings({"ClassNameDiffersFromFileName"})
                public class IgnoreTest {
                    public void test() {
                        checkedFromPackageAnnotation(); // WARN 1
                        checkedFromMethodAnnotation(); // WARN 2
                        checkedFromOuterClassAnnotation(); // WARN 3
                        ignoredFromMethodAnnotation(); // OK 1
                        ignoredFromClassAnnotation(); // OK 2
                        ignoredFromOuterClassAnnotation(); // OK 3
                    }

                    // Inherit @CheckReturnValue from package
                    public static String checkedFromPackageAnnotation() {
                        return "";
                    }

                    @CanIgnoreReturnValue
                    public String ignoredFromMethodAnnotation() { return ""; }

                    @CanIgnoreReturnValue
                    static class MyClass {
                        public static String ignoredFromClassAnnotation() { return ""; }

                        static class MyClass1 {
                            public static String ignoredFromOuterClassAnnotation() { return ""; }
                        }

                        @CheckReturnValue
                        public static String checkedFromMethodAnnotation() { return ""; }

                        @CheckReturnValue
                        static class MyClass2 {
                            public static String checkedFromOuterClassAnnotation() { return ""; }
                        }
                    }
                }
                """
            ).indented(),
            java(
                "" +
                    "@CheckReturnValue\n" +
                    "package test.pkg;\n" +
                    "import javax.annotation.CheckReturnValue;\n"
            ),
            errorProneCanIgnoreReturnValueSource,
            javaxCheckReturnValueSource,
            SUPPORT_ANNOTATIONS_JAR
        )
            .issues(CheckResultDetector.CHECK_RESULT, CheckResultDetector.CHECK_PERMISSION)
            .allowDuplicates()
            .run()
            .expect(
                """
                src/test/pkg/IgnoreTest.java:12: Warning: The result of checkedFromPackageAnnotation is not used [CheckResult]
                        checkedFromPackageAnnotation(); // WARN 1
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/IgnoreTest.java:13: Warning: The result of checkedFromMethodAnnotation is not used [CheckResult]
                        checkedFromMethodAnnotation(); // WARN 2
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/IgnoreTest.java:14: Warning: The result of checkedFromOuterClassAnnotation is not used [CheckResult]
                        checkedFromOuterClassAnnotation(); // WARN 3
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 3 warnings
                """
            )
    }

    fun testCheckResultInTests() {
        // Previously, this required turning on checkTestSources true (see b/148841320);
        // now it's always on for tests (see b/196985792). checkTestSources behavior for
        // checks in general is checked by for example SdCardDetectorTest#testMatchInTestIfEnabled.
        lint().files(
            kotlin(
                "src/test/java/test/pkg/misc.kt",
                """
                package test.pkg
                import androidx.annotation.CheckResult

                fun something(): Boolean = TODO()
                @CheckResult fun assertThat(subject: Boolean): Any = TODO()

                fun test() {
                    assertThat(something()) // ERROR 1
                }
                """
            ).indented(),
            java(
                "src/test/java/Foo.java",
                """
                @javax.annotation.CheckReturnValue
                public class Foo {
                  public int f() {
                    return 42;
                  }
                }
                """
            ).indented(),
            java(
                "src/test/java/Lib.java",
                """
                @javax.annotation.CheckReturnValue
                public class Lib {
                  public static void consume(Object o) {}
                }
                """
            ).indented(),
            // From CheckReturnValueTest#ignoreInTests
            java(
                "src/test/java/Test.java",
                """
                class Test {
                  void f(Foo foo) {
                    try {
                      foo.f(); // OK 1
                      org.junit.Assert.fail();
                    } catch (Exception expected) {}
                    try {
                      foo.f(); // OK 2
                      junit.framework.Assert.fail();
                    } catch (Exception expected) {}
                    try {
                      foo.f(); // OK 3
                      junit.framework.TestCase.fail();
                    } catch (Exception expected) {}
                  }
                }
                """
            ).indented(),
            // From CheckReturnValueTest#ignoreInTestsWithRule
            java(
                "src/test/java/Test2.java",
                """
                class Test2 {
                  private org.junit.rules.ExpectedException exception;
                  void f(Foo foo) {
                    exception.expect(IllegalArgumentException.class);
                    foo.f(); // OK 4
                  }
                }
                """
            ).indented(),
            // From CheckReturnValueTest#ignoreInTestsWithFailureMessage
            java(
                "src/test/java/Test3.java",
                """
                class Test3 {
                  void f(Foo foo) {
                    try {
                      foo.f(); // OK 5
                      org.junit.Assert.fail("message");
                    } catch (Exception expected) {}
                    try {
                      foo.f(); // OK 6
                      junit.framework.Assert.fail("message");
                    } catch (Exception expected) {}
                    try {
                      foo.f(); // OK 7
                      junit.framework.TestCase.fail("message");
                    } catch (Exception expected) {}
                  }
                }
                """
            ).indented(),
            // From CheckReturnValueTest#ignoreInTestsWithRule
            java(
                "src/test/java/Test4.java",
                """
                class Test4 {
                  private org.junit.rules.ExpectedException exception;
                  void f(Foo foo) {
                    exception.expect(IllegalArgumentException.class);
                    foo.f(); // OK 8
                  }
                }
                """
            ).indented(),
            // From CheckReturnValueTest#ignoreInTestsWithFailureMessage
            java(
                "src/test/java/Test5.java",
                """
                class Test5 {
                  void f(Foo foo) {
                    try {
                      foo.f(); // OK 9
                      org.junit.Assert.fail("message");
                    } catch (Exception expected) {}
                    try {
                      foo.f(); // OK 10
                      junit.framework.Assert.fail("message");
                    } catch (Exception expected) {}
                    try {
                      foo.f(); // OK 11
                      junit.framework.TestCase.fail("message");
                    } catch (Exception expected) {}
                  }
                }
                """
            ).indented(),
            // From CheckReturnValueTest#ignoreInThrowingRunnables
            java(
                "src/test/java/Test6.java",
                """
                class Test6 {
                  void f(Foo foo) {
                   org.junit.Assert.assertThrows(IllegalStateException.class,
                     new org.junit.function.ThrowingRunnable() {
                       @Override
                       public void run() throws Throwable {
                         foo.f(); // OK 12
                       }
                     });
                   org.junit.Assert.assertThrows(IllegalStateException.class, () -> foo.f()); // OK 13
                   org.junit.Assert.assertThrows(IllegalStateException.class, foo::f); // OK 14
                   org.junit.Assert.assertThrows(IllegalStateException.class, () -> {
                      int bah = foo.f(); // OK 15
                      foo.f(); // OK 16
                   });
                   org.junit.Assert.assertThrows(IllegalStateException.class, () -> {
                     // BUG: Diagnostic contains: Ignored return value
                     foo.f();  // ERROR 2
                     foo.f();  // OK 17
                   });
                   bar(() -> foo.f()); // OK 18
                   // TODO: Stub this?
                   //org.assertj.core.api.Assertions.assertThatExceptionOfType(IllegalStateException.class)
                   //   .isThrownBy(() -> foo.f()); // OK 19
                  }
                  void bar(org.junit.function.ThrowingRunnable r) {}
                }
                """
            ).indented(),
            // From CheckReturnValueTest#ignoreTruthFailure
            java(
                "src/test/java/Test7.java",
                """
                import static com.google.common.truth.Truth.assert_;
                class Test7 {
                  void f(Foo foo) {
                    try {
                      foo.f(); // OK 20
                      assert_().fail();
                    } catch (Exception expected) {}
                  }
                }
                """
            ).indented(),
            // From CheckReturnValueTest#onlyIgnoreWithEnclosingTryCatch
            java(
                "src/test/java/Test8.java",
                """
                import static org.junit.Assert.fail;
                class Test8 {
                  void f(Foo foo) {
                    foo.f(); // OK 23
                    org.junit.Assert.fail();
                    foo.f(); // OK 24
                    junit.framework.Assert.fail();
                    foo.f(); // OK 25
                    junit.framework.TestCase.fail();
                  }
                }
                """
            ).indented(),
            // From CheckReturnValueTest#ignoreInOrderVerification
            java(
                "src/test/java/Test9.java",
                """
                import static org.mockito.Mockito.inOrder;
                class Test9 {
                  void m() {
                    inOrder().verify(new Foo()).f(); // OK 21
                  }
                }
                """
            ).indented(),
            // From CheckReturnValueTest#ignoreVoidReturningMethodReferences
            java(
                "src/test/java/TestA.java",
                """
                class TestB {
                  void m(java.util.List<Object> xs) {
                    xs.forEach(Lib::consume); // OK 22
                  }
                }
                """
            ).indented(),
            // From CheckReturnValueTest#testIgnoreCRVOnMockito() {
            java(
                "src/test/java/TestB.java",
                """
                import static org.mockito.Mockito.verify;
                import static org.mockito.Mockito.doReturn;
                import org.mockito.Mockito;
                class TestB {
                  void m() {
                    Foo t = new Foo();
                    Mockito.verify(t).f(); // OK 23
                    verify(t).f(); // OK 24
                    doReturn(1).when(t).f(); // OK 25
                    Mockito.doReturn(1).when(t).f(); // OK 26
                  }
                }
                """
            ).indented(),
            java(
                "src/test/java/TestC.java",
                """
                import org.junit.Test;
                class TestC {
                  @Test(expected = IllegalArgumentException.class)
                  void test() {
                    Foo foo = new Foo();
                    foo.f(); // OK 22
                  }
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR,
            gradle("android { }"),

            // Stubs

            java(
                """
                package org.junit;
                public @interface Test {
                    Class<? extends Throwable> expected() default None.class;
                    long timeout() default 0L;
                }
                """
            ).indented(),
            java(
                """
                package org.junit;

                import org.junit.function.ThrowingRunnable;

                public class Assert {
                    public static void fail() { }
                    public static void fail(String message) { }
                    public static <T extends Throwable> T assertThrows(Class<T> expectedThrowable, ThrowingRunnable runnable) { return null; }
                }
                """
            ).indented(),
            java(
                """
                package junit.framework;
                public class Assert {
                    public static void fail() { }
                    public static void fail(String message) { }
                }
                """
            ).indented(),
            java(
                """
                package junit.framework;
                public class TestCase {
                    public static void fail() { }
                    public static void fail(String message) { }
                }
                """
            ).indented(),
            java(
                """
                package org.junit.rules;
                public class ExpectedException {
                    public void expect(Class<? extends Throwable> type) { }
                }
                """
            ).indented(),
            java(
                """
                package org.junit.function;
                public interface ThrowingRunnable {
                    void run() throws Throwable;
                }
                """
            ).indented(),
            java(
                """
                package com.google.common.truth;
                public class Truth {
                    public static StandardSubjectBuilder assert_() { return null; }
                }
                """
            ).indented(),
            java(
                """
                package com.google.common.truth;
                public class StandardSubjectBuilder {
                    public void fail() { }
                }
                """
            ).indented(),
            java(
                """
                package org.mockito;
                import org.mockito.stubbing.Stubber;
                public class Mockito {
                    public static InOrder inOrder(Object... mocks) { return null; }
                    public static <T> T verify(T mock) { return null; }
                    public static Stubber doReturn(Object toBeReturned) { return null; }
                }
                """
            ).indented(),
            java(
                """
                package org.mockito;
                public interface InOrder {
                    <T> T verify(T mock);
                }
                """
            ).indented(),
            java(
                """
                package org.mockito.stubbing;
                public interface Stubber {
                    <T> T when(T mock);
                }
                """
            ).indented(),
        )
            .issues(CheckResultDetector.CHECK_RESULT, CheckResultDetector.CHECK_PERMISSION)
            .testModes(TestMode.DEFAULT)
            .run()
            .expect(
                """
                src/test/java/Test6.java:18: Warning: The result of f is not used [CheckResult]
                     foo.f();  // ERROR 2
                     ~~~~~~~
                src/test/java/test/pkg/misc.kt:8: Warning: The result of assertThat is not used [CheckResult]
                    assertThat(something()) // ERROR 1
                    ~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 2 warnings
                """
            )
    }

    fun testNotIgnoredInBlock() {
        // Regression test for
        // 69534608: False positive for "The result of <method_name> is not used"
        lint().files(
            kotlin(
                """
                    package test.pkg

                    import androidx.annotation.CheckResult

                    fun something(list: List<String>) {
                        list.map { fromNullable(it) }
                    }

                    @CheckResult
                    fun fromNullable(a: Any?): Any? = a"""
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        )
            .issues(CheckResultDetector.CHECK_RESULT, CheckResultDetector.CHECK_PERMISSION)
            .run()
            .expectClean()
    }

    fun testNotIgnoredInBlock2() {
        // Regression test for
        // 69534608: False positive for "The result of <method_name> is not used"
        lint().files(
            kotlin(
                """
                    package test.pkg

                    import androidx.annotation.CheckResult
                    fun test() {
                        val list = listOf(1, 2, 3)

                        val x1 = list.map {
                            label(it)
                        }
                        val x2 = list.map {
                            SomeClass.label(it)
                        }
                        val x3 = list.map {
                            SomeClass.create().label(it)
                        }
                        val x4: List<Any?> = list.map {
                            assert(it < 5)
                            SomeClass.label(it)
                        }
                    }

                    class SomeClass {
                        @CheckResult
                        fun label(a: Any): String = "value: ＄a"

                        companion object {
                            @CheckResult
                            fun label(a: Any): String = "value: ＄a"

                            fun create(): SomeClass {
                                return SomeClass()
                            }
                        }
                    }

                    @CheckResult
                    fun label(a: Any?): Any? = a"""
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        )
            .issues(CheckResultDetector.CHECK_RESULT, CheckResultDetector.CHECK_PERMISSION)
            .run()
            .expectClean()
    }

    fun testCheckResultIf() {
        // Regression test for
        // 72258872: Lint is wrongly detecting "CheckResult" in Kotlin code
        lint().files(
            kotlin(
                """
                    package test.pkg

                    import androidx.annotation.CheckResult

                    fun testIsUnused(): Int {
                        if (3 > 2) {
                            foo() // Unused
                        } else {
                            1
                        }
                        return 0
                    }

                    fun testReturn(): Int {
                        return if (3 > 2) {
                            foo() // OK
                        } else {
                            1
                        }
                    }

                    fun testExpressionBodyReturn(): Int =
                        return if (3 > 2) {
                            foo() // OK
                        } else {
                            1
                        }

                    fun testAssignment(): Int {
                        val result = if (3 > 2) {
                            foo() // OK
                        } else {
                            1
                        }
                        return result
                    }

                    fun testNesting(): Int {
                        return if (3 > 2) {
                            if (4 > 2) {
                                foo() // OK
                            } else {
                                1
                            }
                        } else {
                            1
                        }
                    }

                    @CheckResult
                    fun foo(): Int {
                        return 42
                    }
                """
            ),
            SUPPORT_ANNOTATIONS_JAR
        )
            .issues(CheckResultDetector.CHECK_RESULT, CheckResultDetector.CHECK_PERMISSION)
            .run()
            .expect(
                """
                src/test/pkg/test.kt:8: Warning: The result of foo is not used [CheckResult]
                                            foo() // Unused
                                            ~~~~~
                0 errors, 1 warnings
                """
            )
    }

    fun test73563032() {
        // Regression test for
        //   https://issuetracker.google.com/73563032
        //   73563032: Lint is detecting a "CheckResult" issue when using lambdas in Kotlin
        lint().files(
            kotlin(
                """
                @file:Suppress("unused", "RemoveExplicitTypeArguments", "UNUSED_PARAMETER", "ConstantConditionIf")

                package test.pkg

                import androidx.annotation.CheckResult

                fun lambda1(): () -> Single<Int> = {
                    if (true) {
                        Single.just(3)
                    } else {
                        Single.just(5)
                    }
                }

                fun lambda2(): () -> Single<Int> = {
                    if (true)
                        Single.just(3)
                    else
                        Single.just(5)
                }

                class Single<T> {
                    companion object {
                        @CheckResult
                        fun just(int: Int): Single<Int> {
                            return Single<Int>()
                        }
                    }
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        )
            .issues(CheckResultDetector.CHECK_RESULT, CheckResultDetector.CHECK_PERMISSION)
            .run()
            .expectClean()
    }

    fun testChainedCalls() {
        lint().files(
            java(
                """
                package test.pkg;

                import androidx.annotation.CheckResult;

                @SuppressWarnings({"WeakerAccess", "ClassNameDiffersFromFileName"})
                public class CheckResultTest {
                    public void test() {
                        myMethod(); // WARN
                        this.myMethod(); // WARN
                        myMethod().print(); // OK
                    }

                    @CheckResult
                    public MyClass myMethod() {
                        return new MyClass();
                    }

                    class MyClass {
                        void print() {
                            System.out.println("World");
                        }
                    }
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        )
            .issues(CheckResultDetector.CHECK_RESULT, CheckResultDetector.CHECK_PERMISSION)
            .run()
            .expect(
                """
                src/test/pkg/CheckResultTest.java:8: Warning: The result of myMethod is not used [CheckResult]
                        myMethod(); // WARN
                        ~~~~~~~~~~
                src/test/pkg/CheckResultTest.java:9: Warning: The result of myMethod is not used [CheckResult]
                        this.myMethod(); // WARN
                        ~~~~~~~~~~~~~~~
                0 errors, 2 warnings
                """
            )
    }

    fun test80234958() {
        // 80234958: Lint check misses CheckResult inside kotlin class init blocks
        lint().files(
            kotlin(
                """
                package com.example

                import io.reactivex.Observable

                @Suppress("ConvertSecondaryConstructorToPrimary","RemoveExplicitTypeArguments")
                class Foo {
                  private val someObservable = Observable.create<Int> { }

                  init {
                    someObservable.subscribe { }
                  }

                  constructor() {
                    someObservable.subscribe { }
                  }

                  fun method() {
                    someObservable.subscribe { }
                  }
                }
                """
            ).indented(),
            java(
                """
                    // Stub
                    package io.reactivex;

                    import androidx.annotation.CheckResult;
                    import java.util.function.Consumer;

                    @SuppressWarnings("ClassNameDiffersFromFileName")
                    public abstract class Observable<T> {
                        @CheckResult
                        public static <T> Observable<T> create(Object source) {
                            return null;
                        }

                        @CheckResult
                        public final Object subscribe(Consumer<? super T> onNext) {
                            return null;
                        }
                    }

                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        )
            .issues(CheckResultDetector.CHECK_RESULT, CheckResultDetector.CHECK_PERMISSION)
            .run()
            .expect(
                """
                src/com/example/Foo.kt:10: Warning: The result of subscribe is not used [CheckResult]
                    someObservable.subscribe { }
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/com/example/Foo.kt:14: Warning: The result of subscribe is not used [CheckResult]
                    someObservable.subscribe { }
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/com/example/Foo.kt:18: Warning: The result of subscribe is not used [CheckResult]
                    someObservable.subscribe { }
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 3 warnings
                """
            )
    }

    fun test112602230() {
        // Regression test for
        // 112602230: Spurious lint error for unused result from AndroidFluentLogger#log
        lint().files(
            java(
                """
                package test.pkg;

                import com.google.errorprone.annotations.CheckReturnValue;

                @CheckReturnValue
                @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public interface LoggingApi {
                    void log(String msg, Object p1);
                }
                """
            ),
            java(
                """
                package test.pkg;

                @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class LoggingApiTest {
                    public void test(LoggingApi api) {
                        api.log("log", null);
                    }
                }
                """
            ),
            kotlin(
                """
                package test.pkg

                import com.google.errorprone.annotations.CheckReturnValue

                @Suppress("RedundantUnitReturnType")
                @CheckReturnValue
                interface LoggingApiKotlin {
                    fun log(msg: String, p1: Any): Unit
                }
                """
            ),
            kotlin(
                """
                package test.pkg

                class LoggingApiTestKotlin {
                    fun test(api: LoggingApiKotlin) {
                        api.log("log", "")
                    }
                }
                """
            ),
            errorProneCheckReturnValueSource
        ).run().expectClean()
    }

    fun test119270148() {
        // Regression test for 119270148
        lint().files(
            java(
                """
                package test.pkg;

                public class Test {
                    public Completable clear(KeyValueStore keyValueStore) {
                        return Completable.create(subscriber -> {
                            keyValueStore.clear();
                        });
                    }
                }
                """
            ),
            java(
                """
                package test.pkg;

                public interface Other {
                    void something(Object o);
                }
                """
            ),
            java(
                """
                package test.pkg;

                public class Completable {
                    public static Completable create(Other o) {
                        return null;
                    }
                }
                """
            ),
            kotlin(
                """
                package test.pkg

                import androidx.annotation.CheckResult

                interface Clearable {
                    @CheckResult
                    fun clear(): Completable
                }

                interface KeyValueStore : Clearable {
                    override fun clear(): Completable
                }
                """
            ),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            """
            src/test/pkg/Test.java:7: Warning: The result of clear is not used [CheckResult]
                                        keyValueStore.clear();
                                        ~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }

    fun testIgnoreThisAndSuper() {
        // Regression test for b/140616532: Lint was flagging this() and super() constructor
        // calls
        lint().files(
            java(
                """
                package test.pkg;

                import com.google.errorprone.annotations.CheckReturnValue;

                @CheckReturnValue
                public class CheckResultTest1 {
                    CheckResultTest1() {
                        this(null);
                    }

                    CheckResultTest1(String foo) {
                    }

                    public class SubClass extends CheckResultTest1 {
                        SubClass(String foo) {
                            super(null);
                        }
                    }
                }
                """
            ),
            kotlin(
                """
                package test.pkg

                import com.google.errorprone.annotations.CheckReturnValue

                @CheckReturnValue
                open class CheckResultTest2 @JvmOverloads internal constructor(foo: String? = null) {
                    constructor(s: String, s2: String) : this(s)
                    inner class SubClass internal constructor(foo: String?) : CheckResultTest2(null)
                }
                """
            ),
            errorProneCheckReturnValueSource
        ).run().expectClean()
    }

    fun testCheckResultInLambda() {
        // 188436943: False negative in CheckResultDetector in Kotlin lambdas
        lint().files(
            kotlin(
                """
                import androidx.annotation.CheckResult
                import kotlin.random.Random

                @CheckResult
                fun checkReturn(): String {
                    return Random.nextInt().toString()
                }

                val unitLambda: () -> Unit = {
                    // Should flag: we know based on the lambda's type the return value is unused
                    checkReturn()
                }

                val valueLambda: () -> String = {
                    // Should flag: is not the last expression in the lambda, so value is unused
                    checkReturn()
                    "foo"
                }

                // 188855906: @CheckResult doesn't work inside lambda expressions

                @CheckResult fun x(): String = "Hello"

                fun y() {
                    x() // Correctly flagged

                    val x1 = run {
                        x() // Not flagged
                        ""
                    }

                    val x2 = "".also {
                        x() // Not flagged (the `also` block returns Unit)
                    }
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            """
            src/test.kt:11: Warning: The result of checkReturn is not used [CheckResult]
                checkReturn()
                ~~~~~~~~~~~~~
            src/test.kt:16: Warning: The result of checkReturn is not used [CheckResult]
                checkReturn()
                ~~~~~~~~~~~~~
            src/test.kt:25: Warning: The result of x is not used [CheckResult]
                x() // Correctly flagged
                ~~~
            src/test.kt:28: Warning: The result of x is not used [CheckResult]
                    x() // Not flagged
                    ~~~
            src/test.kt:33: Warning: The result of x is not used [CheckResult]
                    x() // Not flagged (the `also` block returns Unit)
                    ~~~
            0 errors, 5 warnings
            """
        )
    }

    fun testIndirectSuperCallCompiled2() {
        lint().files(
            kotlin(
                """
                package test.pkg.sub

                fun test() {
                    test.pkg.test()
                }
                """
            ).indented(),
            compiled(
                "libs/lib.jar",
                kotlin(
                    """
                    package test.pkg

                    import androidx.annotation.CheckResult

                    @CheckResult
                    fun test(): String = "hello"
                    """
                ).indented(),
                0x49daf5cf,
                """
                test/pkg/TestKt.class:
                H4sIAAAAAAAAAGWQO0/DMBSFj9OWlvDoA8qjwIBYYMEtYmNCSIiIUCSoWDq5
                jVXcpDZKnKpjfxIzA+rMj0JcIySQ8HDu43y27vXH59s7gHMcMFStzCx/iUe8
                R8mtLYMx1MZiKngi9IjfD8ZySN0CQ9GhDJvHJ+Gv/2hTpUcXDIeh0FFqVDTj
                QmtjhVVG86tnOYwfZJYnlpij0KQjPpZ2kAqlsz9gxrvGdvMkIar0LJPEVLDM
                UA9jYxOl+Z20IhJWkO1NpgWanjkpMbDYJR71Z8plbcqiDkNzMV/yF3Pfq623
                KrXFvOW12U3ZmWeMbqPstjmNaaHilYkk/USotOzmk4FMe2KQUGfvIddWTWSg
                pypT1Lr8nZfBfzR5OpTXyqG7P+jTPxAdeCjCHcJQwhLVO1TtUe1OoeG//pgg
                k31ri9QnsEyx4vYj3Ok29il2iFqhp1b7KARYC7BOimqAGuoBGtjog2XYRLMP
                L0Mpw9YXEogmIfMBAAA=
                """,
                """
                META-INF/main.kotlin_module:
                H4sIAAAAAAAAAGNgYGBmYGBgBGJWKM3AJcTFUZJaXKJXkJ0uxBYCZHmXcIlx
                8cDE9IpLk2DiSgxaDACCij4oRAAAAA==
                """
            ),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            """
            src/test/pkg/sub/test.kt:4: Warning: The result of test is not used [CheckResult]
                test.pkg.test()
                ~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }

    fun testBrackets() {
        // Regression test for b/189970773
        lint().files(
            kotlin(
                """
                @file:Suppress(
                    "ConstantConditionIf", "ControlFlowWithEmptyBody",
                    "IMPLICIT_CAST_TO_ANY", "IntroduceWhenSubject"
                )

                package test.pkg

                fun test() {
                    if (true) checkResult()     // ERROR 1
                    if (true) { checkResult() } // ERROR 2
                    if (true) { } else { checkResult() } // ERROR 3
                    if (checkResult() != null) { } // OK

                    try { checkResult() } catch (e: Exception) { } // ERROR 4
                    val ok = try { checkResult() } catch (e: Exception) { } // OK

                    when (checkResult()) { } // OK
                    when (ok) { true -> checkResult() } // ERROR 5
                    when (ok) { true -> { checkResult() } } // ERROR 6
                    val ok2 = when (ok) { true -> checkResult(); else -> { }} // OK
                    val ok3 = when (ok) { true -> { checkResult() }; else -> { }} // OK

                    when { ok == true -> checkResult(); else -> { }} // ERROR 7
                    val ok4 = when { ok == true -> checkResult(); else -> { }} // OK

                    // b/189978180
                    when {
                        condition1 -> checkReturn()     // ERROR 8
                        condition2 -> { checkReturn() } // ERROR 9
                    }
                }
                """
            ),
            kotlin(
                """
                @file:Suppress("RedundantNullableReturnType")
                package test.pkg

                import androidx.annotation.CheckResult

                @CheckResult
                fun checkResult(): Number? = 42

                @CheckResult
                fun checkReturn(): Any = "test"
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            """
            src/test/pkg/test.kt:10: Warning: The result of checkResult is not used [CheckResult]
                                if (true) checkResult()     // ERROR 1
                                          ~~~~~~~~~~~~~
            src/test/pkg/test.kt:11: Warning: The result of checkResult is not used [CheckResult]
                                if (true) { checkResult() } // ERROR 2
                                            ~~~~~~~~~~~~~
            src/test/pkg/test.kt:12: Warning: The result of checkResult is not used [CheckResult]
                                if (true) { } else { checkResult() } // ERROR 3
                                                     ~~~~~~~~~~~~~
            src/test/pkg/test.kt:15: Warning: The result of checkResult is not used [CheckResult]
                                try { checkResult() } catch (e: Exception) { } // ERROR 4
                                      ~~~~~~~~~~~~~
            src/test/pkg/test.kt:19: Warning: The result of checkResult is not used [CheckResult]
                                when (ok) { true -> checkResult() } // ERROR 5
                                                    ~~~~~~~~~~~~~
            src/test/pkg/test.kt:20: Warning: The result of checkResult is not used [CheckResult]
                                when (ok) { true -> { checkResult() } } // ERROR 6
                                                      ~~~~~~~~~~~~~
            src/test/pkg/test.kt:24: Warning: The result of checkResult is not used [CheckResult]
                                when { ok == true -> checkResult(); else -> { }} // ERROR 7
                                                     ~~~~~~~~~~~~~
            src/test/pkg/test.kt:29: Warning: The result of checkReturn is not used [CheckResult]
                                    condition1 -> checkReturn()     // ERROR 8
                                                  ~~~~~~~~~~~~~
            src/test/pkg/test.kt:30: Warning: The result of checkReturn is not used [CheckResult]
                                    condition2 -> { checkReturn() } // ERROR 9
                                                    ~~~~~~~~~~~~~
            0 errors, 9 warnings
            """
        )
    }

    fun test214582872() {
        lint().files(
            kotlin(
                "src/test.kt",
                """
                import androidx.annotation.CheckResult

                fun foo() {}

                // in a real scenario this would be an inherited package annotation not intended for this Unit element
                @CheckResult
                val baz = foo()

                @CheckResult
                val bar: Unit = TODO()

                fun test() {
                    bar
                    baz
                }
                """
            ).indented(),
            java(
                """
                public class Foo {
                    public void test() {
                        TestKt.getBaz();
                    }
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).allowDuplicates().run().expectClean()
    }

    private val javaxCheckReturnValueSource = java(
        """
        package javax.annotation;
        import static java.lang.annotation.RetentionPolicy.CLASS;
        import java.lang.annotation.Retention;
        @SuppressWarnings("ClassNameDiffersFromFileName")
        @Retention(CLASS)
        public @interface CheckReturnValue {
        }
        """
    ).indented()

    private val errorProneCheckReturnValueSource = java(
        """
        package com.google.errorprone.annotations;

        import java.lang.annotation.Documented;
        import java.lang.annotation.Retention;
        import java.lang.annotation.Target;
        import static java.lang.annotation.ElementType.CONSTRUCTOR;
        import static java.lang.annotation.ElementType.METHOD;
        import static java.lang.annotation.ElementType.PACKAGE;
        import static java.lang.annotation.ElementType.TYPE;
        import static java.lang.annotation.RetentionPolicy.RUNTIME;

        @SuppressWarnings("ClassNameDiffersFromFileName")
        @Documented
        @Target({METHOD, CONSTRUCTOR, TYPE, PACKAGE})
        @Retention(RUNTIME)
        public @interface CheckReturnValue {
        }
        """
    ).indented()

    private val errorProneCanIgnoreReturnValueSource = java(
        """
        package com.google.errorprone.annotations;
        import java.lang.annotation.Retention;
        import static java.lang.annotation.RetentionPolicy.CLASS;
        @SuppressWarnings("ClassNameDiffersFromFileName")
        @Retention(CLASS)
        public @interface CanIgnoreReturnValue {}
        """
    ).indented()

    fun testTernary() {
        // Regression test for b/191788196
        lint().files(
            java(
                """
                package test.pkg;

                import androidx.annotation.CheckResult;

                public class JavaIgnore {
                    public void test(boolean x) {
                        String t1 = x ? test1() : test2(); // OK
                        String t2 = x ? ( test1() ) : ( test2() ); // OK
                        String t3 = (x ? test1() : test2()); // OK
                    }

                    @CheckResult public String test1() { return ""; }
                    @CheckResult public String test2() { return ""; }
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun testReturnIfKotlin() {
        // Regression test for b/189970773
        lint().files(
            kotlin(
                """
                package test.pkg

                import androidx.annotation.CheckResult

                class KotlinIgnore {
                    fun test(x: Boolean): String {
                        if (x) test1() else test2() // ERROR 1
                        if (x) (test1()) else (test2()) // ERROR 2
                        val t1 = if (x) test1() else (test2()) // OK 1
                        var t2: String = ""
                        t2 = if (x) test1() else (test2()) // OK 2
                        return if (x) {
                            test1() // OK 3
                        } else {
                            test2() // OK 4
                        }
                    }

                    @Suppress("ConstantConditionIf")
                    fun test(): String {
                        return if(true) test1() else test2() // OK 5
                    }

                    @CheckResult fun test1(): String = ""
                    @CheckResult fun test2(): String = ""
                }
                """
            ),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            """
            src/test/pkg/KotlinIgnore.kt:8: Warning: The result of test1 is not used [CheckResult]
                                    if (x) test1() else test2() // ERROR 1
                                           ~~~~~~~
            src/test/pkg/KotlinIgnore.kt:8: Warning: The result of test2 is not used [CheckResult]
                                    if (x) test1() else test2() // ERROR 1
                                                        ~~~~~~~
            src/test/pkg/KotlinIgnore.kt:9: Warning: The result of test1 is not used [CheckResult]
                                    if (x) (test1()) else (test2()) // ERROR 2
                                            ~~~~~~~
            src/test/pkg/KotlinIgnore.kt:9: Warning: The result of test2 is not used [CheckResult]
                                    if (x) (test1()) else (test2()) // ERROR 2
                                                           ~~~~~~~
            0 errors, 4 warnings
            """
        )
    }

    fun testOperatorOverloads() {
        lint().files(
            kotlin(
                """
                package test.pkg

                import androidx.annotation.CheckResult
                import kotlin.random.Random

                data class Point(val x: Int, val y: Int) {
                    @CheckResult operator fun unaryMinus() = Point(-x, -y)
                    @CheckResult operator fun inc() = Point(x + 1, y + 1)
                }

                @CheckResult
                operator fun Point.unaryPlus() = Point(+x, +y)

                fun testUnary() {
                    var point = Point(10, 20)
                    -point // ERROR 1
                    +point // ERROR 2
                    point++ // ERROR 3
                    println(-point)  // OK 1
                }

                data class Counter(val dayIndex: Int) {
                    @CheckResult
                    operator fun plus(increment: Int): Counter {
                        return Counter(dayIndex + increment)
                    }

                    @CheckResult
                    operator fun plus(other: Counter): Counter {
                        return Counter(dayIndex + other.dayIndex)
                    }
                }

                fun testBinary() {
                    val counter = Counter(5)
                    counter + 5 // ERROR 4
                    val x = counter + 5 // OK 2
                }

                fun number(): Int = Random(0).nextInt()

                fun testPolyadic() {
                    val test = number() + number() + number() + number()
                    val counter = Counter(1)
                    val counter2 = Counter(2)
                    val counter3 = Counter(3)
                    counter + counter2 + counter3 // ERROR 5
                    val counter4 = counter + counter2 + counter3 // OK 3
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            """
            src/test/pkg/Point.kt:16: Warning: The result of unaryMinus is not used [CheckResult]
                -point // ERROR 1
                ~~~~~~
            src/test/pkg/Point.kt:17: Warning: The result of unaryPlus is not used [CheckResult]
                +point // ERROR 2
                ~~~~~~
            src/test/pkg/Point.kt:18: Warning: The result of inc is not used [CheckResult]
                point++ // ERROR 3
                ~~~~~~~
            src/test/pkg/Point.kt:36: Warning: The result of plus is not used [CheckResult]
                counter + 5 // ERROR 4
                ~~~~~~~~~~~
            src/test/pkg/Point.kt:47: Warning: The result of plus is not used [CheckResult]
                counter + counter2 + counter3 // ERROR 5
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 5 warnings
            """
        )
    }

    fun testOperatorOverloads2() {
        // Complicated resolve scenario; there's an extension function instead; our resolve utility
        // won't find this

        lint().files(
            kotlin(
                """
                package test.pkg.other

                import androidx.annotation.CheckResult

                abstract class DataRepository<K, V> {
                    @CheckResult operator fun get(key: K): V = error("Not implemented")
                }
                operator fun <K1, K2, K3, V> DataRepository<Triple<K1, K2, K3>, V>.get(k1: K1, k2: K2, k3: K3): V =
                    get(Triple(k1, k2, k3))
                """
            ),
            kotlin(
                """
                package test.pkg

                import test.pkg.other.DataRepository
                import test.pkg.other.get

                class AppOpLiveData {
                    companion object : DataRepository<Triple<String, String, Int>, AppOpLiveData>()
                }

                fun test(pkg: String, op: String, id: Int) {
                    val installPackagesAppOpMode2 = AppOpLiveData[pkg, op, id] // OK
                    // Not reported yet because we don't have resolve of extension methods
                    // from array access expressions in UAST
                    AppOpLiveData[pkg, op, id] // ERROR
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean() // until KTIJ-18765 is fixed
    }

    fun testSynchronized() {
        lint().files(
            java(
                """
                import androidx.annotation.CheckResult;
                public class Api {
                    @CheckResult
                    public Object getLock() { return Api.class; }
                    public void test() {
                        synchronized (getLock()) {
                            println("Test");
                        }
                    }
                }
                """
            ).indented(),
            kotlin(
                """
                class Api2 : Api() {
                    fun test2() {
                        synchronized(getLock()) { println("test") }
                    }
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun testKotlinTest() {
        lint().files(
            kotlin(
                "src/test/java/test/pkg/misc.kt",
                """
                package test.pkg

                import androidx.annotation.CheckResult
                import java.io.File
                import java.io.FileNotFoundException
                import kotlin.reflect.KClass
                class ExampleUnitTest {
                    @CheckResult
                    private fun createFile(): File {
                        throw FileNotFoundException()
                    }

                    fun test() {
                        assertFailsWith<FileNotFoundException> {
                            createFile() // OK 1
                        }
                        assertFails("blahblah") {
                            createFile() // OK 2
                        }
                        assertFailsWith(FileNotFoundException::class) {
                            createFile() // OK 3
                        }
                    }
                }

                // Stubs
                inline fun <T : Throwable> assertFailsWith(exceptionClass: KClass<T>, block: () -> Unit): T = TODO()
                inline fun <reified T : Throwable> assertFailsWith(message: String? = null, block: () -> Unit): T = TODO()
                @JvmName("assertFailsInline")
                inline fun assertFails(message: String?, block: () -> Unit): Throwable = TODO()
                """
            ).indented(),
            gradle("android { }"),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun testCoroutines() {
        // Regression test for
        // 204595183: CheckResult lint false positives on Kotlin functions that
        //            return Unit in a package with @CheckReturnValue
        // as well as b/214582872
        lint().files(
            compiled(
                "libs/my.jar",
                kotlin(
                    """
                    @file:Suppress("RedundantSuspendModifier", "RedundantUnitReturnType", "UNUSED_PARAMETER", "unused")
                    package test.pkg
                    import javax.annotation.CheckReturnValue

                    @CheckReturnValue
                    class TechFileCoroutineClient {
                        @CheckReturnValue
                        suspend fun createNew(path: String) {
                        }

                        suspend fun method2(ch: Char): Int {
                            return 5
                        }
                        suspend fun method3(ch: Char, s: String): Unit {
                        }
                        suspend fun method4(): Nothing {
                            TODO()
                        }
                    }
                    """
                ).indented(),
                0x81cab355,
                """
                META-INF/main.kotlin_module:
                H4sIAAAAAAAAAGNgYGBmYGBgBGI2BijgEuLiKEktLtEryE4XYgsBsrxLlBi0
                GAAv8Yb6LAAAAA==
                """,
                """
                test/pkg/TechFileCoroutineClient.class:
                H4sIAAAAAAAAAK1V21IbRxA9s1rdVgIknGAuToJtbAscs9wcJ4FggwBbRFZS
                hpBK8TRIE7FotUvtjgiPVB6S/8gXJHmxK6lKKPzmj0qld7QS4hZsKqrSTE93
                nz7dO729b/754y8AM3jKMCyFL83dWtVcF+XtFcsWeddzG9JyRN62hCPjYAyZ
                Hb7HTZs7VfOrrR1RJm2EITZnOZacZ4jkRjfSiCJmQEecQZfbls9wq3hZ8FmG
                ZNkTXIqS+IFhJVc8JlqTnuVUZ4s1V9qWY5ZbSN/Muw4JDS4t15kdLZ7OjYJu
                vHuguQcth2+oqNn58wPfVsp9kzuOKxXOzG+Lcu2FkA3P2eB2Q5DXSNH1quaO
                kFsetxy/w9s3Sw3b5lu2UMH+w82VgSd5pTrSiiPLkCiU1tYXSvllhq4TOadx
                De8l0Yv36Qp2udxm6D37HCjiSNmt79oiYKIOuOwJM8TrQm67lSmGh7n8lS7k
                y0tx9PyPcQVHiqrwLrqFO2dD7ezVTYtQnsNtc9Hdp0rj+JB6dMvdp2gMfbnC
                6DkMaQzjpoGPcItBK9MTY/l2vdMMTynv/6clv71CpLfrSea3M55hmMldKb1H
                l8Hmxi6gHwxx1LGFoKvq9F6LyrLnuV4cDxiWznkXCy2yE9e2JL7nDVsSqy+9
                Rlm63nPu1eiSmtPFNDCOCYZsC/xcSF7hklMSWn0vQiONBUssWEDPpUb6fSs4
                EUqrTDK8OTwYN7R+zdAyhweGlggEzQjETHd4TDSPaTpGaWd0TMT7Dw+mtAm2
                GD36JUaI1YFMZFCb0KdimSjtsWdHPz95/YodHihznMwJUifJbNCeenb0U4c5
                vXoz0xWilZn27maUTree1WwmQ+rsSXXv0Y+aTrkNBBXRC0l1Dl4wXMdr1PZ6
                3q0Ihp4iqUuN+pbw1oPpE0wGt8ztDe5ZwTlUJtesqsNplJE89KJBt18XBWfP
                8i0yLxyPJxpxp61fc49TEwrvhJux5ja8sgiyYxgIMRtn4mESGn06gl+UKqIv
                Ca2P6TQd3GOgHXuJxG8kaHhCq0F74KqT4wJJ6aYTkmQBskiRRlcBFskSdID+
                O/p+beNjyj+psMNNe4gNpOvoV3YdAxgkxKLCdSEfMnfT3kP/Jfp3aeSaUf2W
                UdRDuBFS50PqaOIVbp/mTndwR9vcUYzgTlhLJ/cHijtAtniTWgfnXdwLOYuk
                0y8ot0dRjjXtHeW2KHWMUuFau/BISJ47Q94TaZO3UhjD/TCFx5R7ECT1J8a/
                Yzp7icm/T2WSVZn0Nd3amaQUKwtZPz7DGmchWwTLymUeK7RXSDtFnNObiBQw
                U8DDAj7BIxLxaQGf4fNNmpCYxdwmrvkwfHzhI+YjpYTrak37GFLCiFpv+Ljr
                Y9THPR9jSnO/nQzd/b+7Id9YPwkAAA==
                """
            ),
            kotlin(
                """
                @file:Suppress("RedundantSuspendModifier", "RedundantUnitReturnType", "UNUSED_PARAMETER", "unused")
                package test.pkg

                suspend fun readPresentFile(client: TechFileCoroutineClient) = run {
                    client.createNew("/file") // OK 1
                    client.method2('x') // ERROR 1
                    val x = client.method2('x') // OK 2
                    client.method3('x', "") // OK 3
                    if (x > 100) {
                        client.method4() // OK 4
                    }
                    assert(true)
                }
                """
            ).indented(),
            javaxCheckReturnValueSource
        ).run().expect(
            """
            src/test/pkg/test.kt:6: Warning: The result of method2 is not used [CheckResult]
                client.method2('x') // ERROR 1
                ~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }

    fun test216101161() {
        // Regression test for an overloaded operator expression referencing an annotated
        // call
        lint().files(
            kotlin(
                """
                package test.pkg

                import androidx.annotation.CheckResult

                fun isSingleDigit(num: Int?): Boolean {
                    num?.let { if (it in Range.closedOpen(1, 10)) return true }
                    return false
                }

                class Range<C : Comparable<*>?> {
                    @CheckResult
                    operator fun contains(value: C): Boolean = TODO()

                    companion object {
                        @CheckResult
                        fun <C : Comparable<*>?> closedOpen(lower: C, upper: C): Range<C> = TODO()
                    }
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun testCapitalVoid() {
        // Regression test for issue 225204162
        lint().files(
            java(
                """
                package test.pkg;

                import androidx.annotation.CheckResult;

                public class VoidTest {
                    public void test(Function<String, Void> callback) {
                        callback.apply("");
                    }

                    public interface Function<T, R> {
                        @CheckResult
                        R apply(T var1);
                    }
                }
                """
            ).indented(),
            kotlin(
                """
                package test.pkg

                import androidx.annotation.CheckResult

                interface Function<T, R> {
                    @CheckResult
                    fun apply(var1: T): R
                }

                fun test(callback: Function<String?, Void?>) {
                    callback.apply("")
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun testInheritance() {
        lint().files(
            kotlin(
                """
                package test.pkg

                import javax.annotation.CheckReturnValue;
                import androidx.annotation.CheckResult

                @CheckReturnValue
                interface MyInterface {
                    @CheckResult fun method1(): String
                    fun method2(): String
                }

                class MyClass : MyInterface {
                    override fun method1(): String = TODO()
                    override fun method2(): String = TODO()
                }

                fun test(myClass: MyClass) {
                    myClass.method1() // WARN - annotation on method
                    myClass.method2() // OK - inherited annotation from outer context
                }
                """
            ).indented(),
            javaxCheckReturnValueSource,
            SUPPORT_ANNOTATIONS_JAR
        ).allowDuplicates().run().expect(
            """
            src/test/pkg/MyInterface.kt:18: Warning: The result of method1 is not used [CheckResult]
                myClass.method1() // WARN - annotation on method
                ~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }

    fun testOtherAliases() {
        // We now match ANY annotation named "CheckReturnValue" or "CheckResult"
        lint().files(
            java(
                """
                package test.pkg;

                class JavaTest {
                    void test() {
                        TestKt.test1(); // ERROR 1
                        TestKt.test2(); // ERROR 2
                        TestKt.test3(); // ERROR 3
                        TestKt.test4(); // ERROR 4
                    }
                    void test(Foo foo) {
                        foo.foo1(); // ERROR 5
                        foo.foo2(); // OK
                    }
                }
                """
            ).indented(),
            kotlin(
                """
                package test.pkg

                class KotlinTest {
                    fun test() {
                        test1() // ERROR 6
                        test2() // ERROR 7
                        test3() // ERROR 8
                        test4() // ERROR 9
                    }
                    fun test(foo: Foo) {
                        foo.foo1() // ERROR 10
                        foo.foo2() // OK
                    }
                }
                """
            ).indented(),
            kotlin(
                """
                package test.pkg
                import test.pkg.CanIgnoreReturnValue
                import com.google.protobuf.CheckReturnValue

                @CheckReturnValue
                class Foo {
                  fun foo1(): String = "Hello world"
                  @CanIgnoreReturnValue
                  fun foo2(): String = foo1()
                }
                """
            ).indented(),
            kotlin(
                "src/test/pkg/test.kt",
                """
                package test.pkg
                @io.reactivex.annotations.CheckReturnValue fun test1(): String = "test1"
                @io.reactivex.rxjava3.annotations.CheckReturnValue fun test2(): String = "test2"
                @com.google.protobuf.CheckReturnValue fun test3(): String = "test3"
                @org.mockito.CheckReturnValue fun test4(): String = "test4"
                """
            ).indented(),
            java(
                """
                package io.reactivex.annotations;
                public @interface CheckReturnValue {
                }
                """
            ).indented(),
            java(
                """
                package io.reactivex.rxjava3.annotations;
                public @interface CheckReturnValue {
                }
                """
            ).indented(),
            java(
                """
                package com.google.protobuf;
                import static java.lang.annotation.ElementType.CONSTRUCTOR;
                import static java.lang.annotation.ElementType.METHOD;
                import static java.lang.annotation.ElementType.PACKAGE;
                import static java.lang.annotation.ElementType.TYPE;
                import static java.lang.annotation.RetentionPolicy.RUNTIME;
                import java.lang.annotation.Documented;
                import java.lang.annotation.Retention;
                import java.lang.annotation.Target;
                @Documented
                @Target({METHOD, CONSTRUCTOR, TYPE, PACKAGE})
                @Retention(RUNTIME)
                public @interface CheckReturnValue {}
                """
            ).indented(),
            java(
                """
                package org.mockito;
                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;
                @Target({ElementType.CONSTRUCTOR, ElementType.METHOD, ElementType.PACKAGE, ElementType.TYPE})
                @Retention(RetentionPolicy.CLASS)
                public @interface CheckReturnValue {}
                """
            ).indented(),
            java(
                """
                package test.pkg;
                public @interface CanIgnoreReturnValue {}
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/JavaTest.java:5: Warning: The result of test1 is not used [CheckResult]
                    TestKt.test1(); // ERROR 1
                    ~~~~~~~~~~~~~~
            src/test/pkg/JavaTest.java:6: Warning: The result of test2 is not used [CheckResult]
                    TestKt.test2(); // ERROR 2
                    ~~~~~~~~~~~~~~
            src/test/pkg/JavaTest.java:7: Warning: The result of test3 is not used [CheckResult]
                    TestKt.test3(); // ERROR 3
                    ~~~~~~~~~~~~~~
            src/test/pkg/JavaTest.java:8: Warning: The result of test4 is not used [CheckResult]
                    TestKt.test4(); // ERROR 4
                    ~~~~~~~~~~~~~~
            src/test/pkg/JavaTest.java:11: Warning: The result of foo1 is not used [CheckResult]
                    foo.foo1(); // ERROR 5
                    ~~~~~~~~~~
            src/test/pkg/KotlinTest.kt:5: Warning: The result of test1 is not used [CheckResult]
                    test1() // ERROR 6
                    ~~~~~~~
            src/test/pkg/KotlinTest.kt:6: Warning: The result of test2 is not used [CheckResult]
                    test2() // ERROR 7
                    ~~~~~~~
            src/test/pkg/KotlinTest.kt:7: Warning: The result of test3 is not used [CheckResult]
                    test3() // ERROR 8
                    ~~~~~~~
            src/test/pkg/KotlinTest.kt:8: Warning: The result of test4 is not used [CheckResult]
                    test4() // ERROR 9
                    ~~~~~~~
            src/test/pkg/KotlinTest.kt:11: Warning: The result of foo1 is not used [CheckResult]
                    foo.foo1() // ERROR 10
                    ~~~~~~~~~~
            0 errors, 10 warnings
            """
        )
    }

    fun testExactNameMatch() {
        // While we match on "@x.CheckResult", don't match "@xCheckResult" or "@CheckResultX"
        lint().files(
            kotlin(
                """
                package test.pkg

                class KotlinTest {
                    fun test() {
                        test1() // OK
                        test2() // OK
                    }
                }
                """
            ).indented(),
            kotlin(
                "src/test/pkg/test.kt",
                """
                package test.pkg
                import foo.bar.*;
                @XCheckResult fun test1(): String = "test1"
                @CheckResultX fun test2(): String = "test2"
                """
            ).indented(),
            java(
                """
                package foo.bar;
                public @interface XCheckResult {
                }
                """
            ).indented(),
            java(
                """
                package foo.bar;
                public @interface CheckResultX {
                }
                """
            ).indented()
        ).run().expectClean()
    }
}
