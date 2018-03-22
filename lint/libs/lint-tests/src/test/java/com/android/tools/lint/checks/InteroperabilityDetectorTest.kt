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

class InteroperabilityDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return InteroperabilityDetector()
    }

    fun testKeywords() {
        lint().files(
            java(
                """
                package test.pkg;

                @SuppressWarnings("ClassNameDiffersFromFileName")
                public class Test {
                    public void fun() { }
                    public void foo(int fun, int internalName) { }
                    public Object object = null;
                }
                """
            ).indented(),
            java(
                """
                package test.pkg;

                import org.json.JSONException;
                import org.json.JSONStringer;

                @SuppressWarnings("ClassNameDiffersFromFileName")
                 public class Keywords extends JSONStringer {
                    // Using Kotlin hard keyword, but can't be helped; overrides library name
                    @Override
                    public JSONStringer object() throws JSONException {
                        return super.object();
                    }
                }
                """
            ).indented()
        ).issues(InteroperabilityDetector.NO_HARD_KOTLIN_KEYWORDS).run().expect(
            """
            src/test/pkg/Test.java:5: Warning: Avoid method names that are Kotlin hard keywords ("fun"); see https://android.github.io/kotlin-guides/interop.html#no-hard-keywords [NoHardKeywords]
                public void fun() { }
                            ~~~
            src/test/pkg/Test.java:7: Warning: Avoid field names that are Kotlin hard keywords ("object"); see https://android.github.io/kotlin-guides/interop.html#no-hard-keywords [NoHardKeywords]
                public Object object = null;
                              ~~~~~~
            0 errors, 2 warnings
            """
        )
    }

    fun testLambdaLast() {
        lint().files(
            java(
                """
                package test.pkg;
                @SuppressWarnings("ClassNameDiffersFromFileName")
                public class Test {
                    public void ok1() { }
                    public void ok1(int x) { }
                    public void ok2(int x, int y) { }
                    public void ok3(Runnable run) { }
                    public void ok4(int x, Runnable run) { }
                    public void ok5(Runnable run1, Runnable run2) { }
                    public void ok6(java.util.List list, boolean b) { }
                    public void error1(Runnable run, int x) { }
                    public void error2(SamInterface sam, int x) { }

                    public interface SamInterface {
                        void samMethod();
                        @Override String toString();
                        default void other() {  }
                    }
                }
                """
            ).indented(),
            kotlin(
                """
                    package test.pkg

                    fun ok1(bar: (Int) -> Int) { }
                    fun ok2(foo: Int) { }
                    fun ok3(foo: Int, bar: (Int) -> Int) { }
                    fun ok4(foo: Int, bar: (Int) -> Int, baz: (Int) -> Int) { }
                    // Lamda not last, but we're not flagging issues in Kotlin files for the
                    // interoperability issue
                    fun error(bar: (Int) -> Int, foo: Int) { }
                """
            ).indented()

        ).issues(InteroperabilityDetector.LAMBDA_LAST).run().expect(
            """
            src/test/pkg/Test.java:11: Warning: Functional interface parameters (such as parameter 1, "run", in test.pkg.Test.error1) should be last to improve Kotlin interoperability; see https://kotlinlang.org/docs/reference/java-interop.html#sam-conversions [LambdaLast]
                public void error1(Runnable run, int x) { }
                                                 ~~~~~
            src/test/pkg/Test.java:12: Warning: Functional interface parameters (such as parameter 1, "sam", in test.pkg.Test.error2) should be last to improve Kotlin interoperability; see https://kotlinlang.org/docs/reference/java-interop.html#sam-conversions [LambdaLast]
                public void error2(SamInterface sam, int x) { }
                                                     ~~~~~
            0 errors, 2 warnings
            """
        )
    }

    fun testNullness() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.support.annotation.NonNull;
                import android.support.annotation.Nullable;import com.android.annotations.NonNull;
                @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class Test {
                    public void ok(int x, float y, boolean z) { }
                    @Nullable public Object ok2(@NonNull Integer i, @NonNull int[] array) { return null; }
                    private Object ok3(Integer i) { return null; }
                    public Object error1(Integer error2, int[] error3) { return null; }
                    @NonNull public Float ok4 = 5;
                    @NonNull protected Float ok5 = 5;
                    private Float ok6 = 5;
                    public Float error4;
                    /** Field comment */
                    public Float error5;
                    /** Method comment */
                    public Object error6() { return null; }
                    protected Float error7;

                    // Don't flag public methods and fields in non-public classes or
                    // in anonymous inner classes
                    @SuppressWarnings("ResultOfObjectAllocationIgnored")
                    class Inner {
                        public void ok(Integer i) {
                            new Runnable() {
                                @Override public void run() {
                                }
                                public void ok2(Integer i) {  }
                            };
                        }
                    }
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_CLASS_PATH,
            SUPPORT_ANNOTATIONS_JAR
        ).issues(InteroperabilityDetector.PLATFORM_NULLNESS).run().expect(
            """
            src/test/pkg/Test.java:10: Warning: Unknown nullability; explicitly declare as @Nullable or @NonNull to improve Kotlin interoperability; see https://android.github.io/kotlin-guides/interop.html#nullability-annotations [UnknownNullness]
                public Object error1(Integer error2, int[] error3) { return null; }
                       ~~~~~~
            src/test/pkg/Test.java:10: Warning: Unknown nullability; explicitly declare as @Nullable or @NonNull to improve Kotlin interoperability; see https://android.github.io/kotlin-guides/interop.html#nullability-annotations [UnknownNullness]
                public Object error1(Integer error2, int[] error3) { return null; }
                                     ~~~~~~~
            src/test/pkg/Test.java:10: Warning: Unknown nullability; explicitly declare as @Nullable or @NonNull to improve Kotlin interoperability; see https://android.github.io/kotlin-guides/interop.html#nullability-annotations [UnknownNullness]
                public Object error1(Integer error2, int[] error3) { return null; }
                                                     ~~~~~
            src/test/pkg/Test.java:14: Warning: Unknown nullability; explicitly declare as @Nullable or @NonNull to improve Kotlin interoperability; see https://android.github.io/kotlin-guides/interop.html#nullability-annotations [UnknownNullness]
                public Float error4;
                       ~~~~~
            src/test/pkg/Test.java:16: Warning: Unknown nullability; explicitly declare as @Nullable or @NonNull to improve Kotlin interoperability; see https://android.github.io/kotlin-guides/interop.html#nullability-annotations [UnknownNullness]
                public Float error5;
                       ~~~~~
            src/test/pkg/Test.java:18: Warning: Unknown nullability; explicitly declare as @Nullable or @NonNull to improve Kotlin interoperability; see https://android.github.io/kotlin-guides/interop.html#nullability-annotations [UnknownNullness]
                public Object error6() { return null; }
                       ~~~~~~
            src/test/pkg/Test.java:19: Warning: Unknown nullability; explicitly declare as @Nullable or @NonNull to improve Kotlin interoperability; see https://android.github.io/kotlin-guides/interop.html#nullability-annotations [UnknownNullness]
                protected Float error7;
                          ~~~~~
            0 errors, 7 warnings
            """
        ).expectFixDiffs(
            // The unit testing infrastructure doesn't support shortening identifiers so
            // here we see fully qualified annotations inserted; in the IDE, the annotations
            // would get imported at the top of the compilation unit and shortened names
            // used here
            """
            Fix for src/test/pkg/Test.java line 9: Annotate @NonNull:
            @@ -10 +10
            -     public Object error1(Integer error2, int[] error3) { return null; }
            +     @android.support.annotation.NonNull public Object error1(Integer error2, int[] error3) { return null; }
            Fix for src/test/pkg/Test.java line 9: Annotate @Nullable:
            @@ -10 +10
            -     public Object error1(Integer error2, int[] error3) { return null; }
            +     @android.support.annotation.Nullable public Object error1(Integer error2, int[] error3) { return null; }
            Fix for src/test/pkg/Test.java line 9: Annotate @NonNull:
            @@ -10 +10
            -     public Object error1(Integer error2, int[] error3) { return null; }
            +     public Object error1(@android.support.annotation.NonNull Integer error2, int[] error3) { return null; }
            Fix for src/test/pkg/Test.java line 9: Annotate @Nullable:
            @@ -10 +10
            -     public Object error1(Integer error2, int[] error3) { return null; }
            +     public Object error1(@android.support.annotation.Nullable Integer error2, int[] error3) { return null; }
            Fix for src/test/pkg/Test.java line 9: Annotate @NonNull:
            @@ -10 +10
            -     public Object error1(Integer error2, int[] error3) { return null; }
            +     public Object error1(Integer error2, @android.support.annotation.NonNull int[] error3) { return null; }
            Fix for src/test/pkg/Test.java line 9: Annotate @Nullable:
            @@ -10 +10
            -     public Object error1(Integer error2, int[] error3) { return null; }
            +     public Object error1(Integer error2, @android.support.annotation.Nullable int[] error3) { return null; }
            Fix for src/test/pkg/Test.java line 13: Annotate @NonNull:
            @@ -14 +14
            -     public Float error4;
            +     @android.support.annotation.NonNull public Float error4;
            Fix for src/test/pkg/Test.java line 13: Annotate @Nullable:
            @@ -14 +14
            -     public Float error4;
            +     @android.support.annotation.Nullable public Float error4;
            Fix for src/test/pkg/Test.java line 15: Annotate @NonNull:
            @@ -16 +16
            -     public Float error5;
            +     @android.support.annotation.NonNull public Float error5;
            Fix for src/test/pkg/Test.java line 15: Annotate @Nullable:
            @@ -16 +16
            -     public Float error5;
            +     @android.support.annotation.Nullable public Float error5;
            Fix for src/test/pkg/Test.java line 17: Annotate @NonNull:
            @@ -18 +18
            -     public Object error6() { return null; }
            +     @android.support.annotation.NonNull public Object error6() { return null; }
            Fix for src/test/pkg/Test.java line 17: Annotate @Nullable:
            @@ -18 +18
            -     public Object error6() { return null; }
            +     @android.support.annotation.Nullable public Object error6() { return null; }
            Fix for src/test/pkg/Test.java line 18: Annotate @NonNull:
            @@ -19 +19
            -     protected Float error7;
            +     @android.support.annotation.NonNull protected Float error7;
            Fix for src/test/pkg/Test.java line 18: Annotate @Nullable:
            @@ -19 +19
            -     protected Float error7;
            +     @android.support.annotation.Nullable protected Float error7;
                """
        )
    }

    fun testPropertyAccess() {
        lint().files(
            java(
                """
                package test.pkg;

                @SuppressWarnings({"ClassNameDiffersFromFileName", "unused", "MethodMayBeStatic", "NonBooleanMethodNameMayNotStartWithQuestion"})
                public class GetterSetter {
                    // Correct Java Bean - get-prefix
                    public void setOk1(String s) { }
                    public String getOk1() { return ""; }

                    // Correct Java Bean - is-prefix
                    public void setOk2(String s) {}
                    public String isOk2() { return ""; }

                    // This is a read-only bean but we don't interpret these
                    public String getOk2() { return ""; }

                    // This is *potentially* an incorrectly named read-only Java Bean but we don't flag these
                    public String hasOk3() { return ""; }

                    // This is a write-only Java Bean we but we don't flag these
                    public void setOk4(String s) { }

                    // Using "wrong" return type on the setter is fine, Kotlin doesn't care
                    public String setOk5(String s) { return s; }
                    public String getOk5() { return ""; }

                    // Now the errors

                    // Using "has" instead of is
                    public void setError1(String s) { }
                    public String hasError1() { return ""; }

                    // Using property name itself
                    public void setError2(String s) { }
                    public String error2() { return ""; }

                    // Using some other suffix
                    public void setError3(String s) { }
                    public String hazzError3() { return ""; }

                    // Mismatched getter and setter types
                    public void setError4(String s) { }
                    public Integer getError4() { return 0; }

                    // Wrong access modifier
                    public void setError5(String s) { }
                    protected String getError5() { return ""; }

                    // Wrong static
                    public void setError6(String s) { }
                    public static String getError6() { return ""; }

                    private class NonApi {
                        // Not valid java bean but we don't flag stuff in private classes
                        public String setOk1(String s) { return ""; }
                        public String getOk1() { return ""; }
                    }

                    public static class SuperClass {
                        public Number getNumber1() { return 0.0; }
                        public void setNumber1(Number number) { }
                        public Number getNumber2() { return 0.0; }
                        public void setNumber2(Number number) { }
                        public Number getNumber3() { return 0.0; }
                        public void setNumber3(Number number) { }
                    }

                    public static class SubClass extends SuperClass {
                        @Override public Float getNumber1() { return 0.0f; } // OK
                        @Override public void setNumber2(Number number) { } // OK
                        @Override public Float getNumber3() { return 0.0f; } // ERROR (even though we have corresponding setter)
                        public void setNumber3(Float number) { } // OK
                        public Float getNumber4() { return 0.0f; } // OK
                        public void setNumber4(Float number) { } // OK
                    }
                }
                """
            ).indented()
        ).issues(InteroperabilityDetector.KOTLIN_PROPERTY).run().expect(
            """
            src/test/pkg/GetterSetter.java:30: Warning: This method should be called getError1 such that error1 can be accessed as a property from Kotlin; see https://android.github.io/kotlin-guides/interop.html#property-prefixes [KotlinPropertyAccess]
                public String hasError1() { return ""; }
                              ~~~~~~~~~
            src/test/pkg/GetterSetter.java:34: Warning: This method should be called getError2 such that error2 can be accessed as a property from Kotlin; see https://android.github.io/kotlin-guides/interop.html#property-prefixes [KotlinPropertyAccess]
                public String error2() { return ""; }
                              ~~~~~~
            src/test/pkg/GetterSetter.java:38: Warning: This method should be called getError3 such that error3 can be accessed as a property from Kotlin; see https://android.github.io/kotlin-guides/interop.html#property-prefixes [KotlinPropertyAccess]
                public String hazzError3() { return ""; }
                              ~~~~~~~~~~
            src/test/pkg/GetterSetter.java:42: Warning: The getter return type (Integer) and setter parameter type (String) getter and setter methods for property error4 should have exactly the same type to allow be accessed as a property from Kotlin; see https://android.github.io/kotlin-guides/interop.html#property-prefixes [KotlinPropertyAccess]
                public Integer getError4() { return 0; }
                               ~~~~~~~~~
                src/test/pkg/GetterSetter.java:41: Setter here
            src/test/pkg/GetterSetter.java:46: Warning: This getter should be public such that error5 can be accessed as a property from Kotlin; see https://android.github.io/kotlin-guides/interop.html#property-prefixes [KotlinPropertyAccess]
                protected String getError5() { return ""; }
                                 ~~~~~~~~~
            src/test/pkg/GetterSetter.java:50: Warning: This getter should not be static such that error6 can be accessed as a property from Kotlin; see https://android.github.io/kotlin-guides/interop.html#property-prefixes [KotlinPropertyAccess]
                public static String getError6() { return ""; }
                       ~~~~~~
            src/test/pkg/GetterSetter.java:70: Warning: The getter return type (Float) is not the same as the setter return type (Number); they should have exactly the same type to allow number3 be accessed as a property from Kotlin; see https://android.github.io/kotlin-guides/interop.html#property-prefixes [KotlinPropertyAccess]
                    @Override public Float getNumber3() { return 0.0f; } // ERROR (even though we have corresponding setter)
                                           ~~~~~~~~~~
                src/test/pkg/GetterSetter.java:71: Setter here
            0 errors, 7 warnings
            """
        )
    }
}
