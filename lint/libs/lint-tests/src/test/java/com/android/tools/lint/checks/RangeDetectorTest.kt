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

import com.android.tools.lint.detector.api.Detector

class RangeDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector = RangeDetector()

    fun testRange() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.support.annotation.FloatRange;
                import android.support.annotation.IntRange;
                import android.support.annotation.Size;

                @SuppressWarnings({"UnusedDeclaration", "ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class RangeTest {
                    public void printExact(@Size(5) String arg) { System.out.println(arg); }
                    public void printMin(@Size(min=5) String arg) { }
                    public void printMax(@Size(max=8) String arg) { }
                    public void printRange(@Size(min=4,max=6) String arg) { }
                    public void printExact(@Size(5) int[] arg) { }
                    public void printMin(@Size(min=5) int[] arg) { }
                    public void printMax(@Size(max=8) int[] arg) { }
                    public void printRange(@Size(min=4,max=6) int[] arg) { }
                    public void printMultiple(@Size(multiple=3) int[] arg) { }
                    public void printMinMultiple(@Size(min=4,multiple=3) int[] arg) { }
                    public void printAtLeast(@IntRange(from=4) int arg) { }
                    public void printAtMost(@IntRange(to=7) int arg) { }
                    public void printBetween(@IntRange(from=4,to=7) int arg) { }
                    public void printAtLeastInclusive(@FloatRange(from=2.5) float arg) { }
                    public void printAtLeastExclusive(@FloatRange(from=2.5,fromInclusive=false) float arg) { }
                    public void printAtMostInclusive(@FloatRange(to=7) double arg) { }
                    public void printAtMostExclusive(@FloatRange(to=7,toInclusive=false) double arg) { }
                    public void printBetweenFromInclusiveToInclusive(@FloatRange(from=2.5,to=5.0) float arg) { }
                    public void printBetweenFromExclusiveToInclusive(@FloatRange(from=2.5,to=5.0,fromInclusive=false) float arg) { }
                    public void printBetweenFromInclusiveToExclusive(@FloatRange(from=2.5,to=5.0,toInclusive=false) float arg) { }
                    public void printBetweenFromExclusiveToExclusive(@FloatRange(from=2.5,to=5.0,fromInclusive=false,toInclusive=false) float arg) { }

                    public void testLength() {
                        printExact("1234"); // ERROR
                        printExact("12345"); // OK
                        printExact("123456"); // ERROR

                        printMin("1234"); // ERROR
                        printMin("12345"); // OK
                        printMin("123456"); // OK

                        printMax("123456"); // OK
                        printMax("1234567"); // OK
                        printMax("12345678"); // OK
                        printMax("123456789"); // ERROR

                        printRange("123"); // ERROR
                        printRange("1234"); // OK
                        printRange("12345"); // OK
                        printRange("123456"); // OK
                        printRange("1234567"); // ERROR
                    }

                    public void testSize() {
                        printExact(new int[]{1, 2, 3, 4}); // ERROR
                        printExact(new int[]{1, 2, 3, 4, 5}); // OK
                        printExact(new int[]{1, 2, 3, 4, 5, 6}); // ERROR

                        printMin(new int[]{1, 2, 3, 4}); // ERROR
                        printMin(new int[]{1, 2, 3, 4, 5}); // OK
                        printMin(new int[]{1, 2, 3, 4, 5, 6}); // OK

                        printMax(new int[]{1, 2, 3, 4, 5, 6}); // OK
                        printMax(new int[]{1, 2, 3, 4, 5, 6, 7}); // OK
                        printMax(new int[]{1, 2, 3, 4, 5, 6, 7, 8}); // OK
                        printMax(new int[]{1, 2, 3, 4, 5, 6, 7, 8}); // OK
                        printMax(new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9}); // ERROR

                        printRange(new int[] {1,2,3}); // ERROR
                        printRange(new int[] {1,2,3,4}); // OK
                        printRange(new int[] {1,2,3,4,5}); // OK
                        printRange(new int[] {1,2,3,4,5,6}); // OK
                        printRange(new int[] {1,2,3,4,5,6,7}); // ERROR

                        printMultiple(new int[] {1,2,3}); // OK
                        printMultiple(new int[] {1,2,3,4}); // ERROR
                        printMultiple(new int[] {1,2,3,4,5}); // ERROR
                        printMultiple(new int[] {1,2,3,4,5,6}); // OK
                        printMultiple(new int[] {1,2,3,4,5,6,7}); // ERROR

                        printMinMultiple(new int[] {1,2,3,4,5,6}); // OK
                        printMinMultiple(new int[]{1, 2, 3}); // ERROR
                    }

                    public void testIntRange() {
                        printAtLeast(3); // ERROR
                        printAtLeast(4); // OK
                        printAtLeast(5); // OK

                        printAtMost(5); // OK
                        printAtMost(6); // OK
                        printAtMost(7); // OK
                        printAtMost(8); // ERROR

                        printBetween(3); // ERROR
                        printBetween(4); // OK
                        printBetween(5); // OK
                        printBetween(6); // OK
                        printBetween(7); // OK
                        printBetween(8); // ERROR
                    }

                    public void testFloatRange() {
                        printAtLeastInclusive(2.49f); // ERROR
                        printAtLeastInclusive(2.5f); // OK
                        printAtLeastInclusive(2.6f); // OK

                        printAtLeastExclusive(2.49f); // ERROR
                        printAtLeastExclusive(2.5f); // ERROR
                        printAtLeastExclusive(2.501f); // OK

                        printAtMostInclusive(6.8f); // OK
                        printAtMostInclusive(6.9f); // OK
                        printAtMostInclusive(7.0f); // OK
                        printAtMostInclusive(7.1f); // ERROR

                        printAtMostExclusive(6.9f); // OK
                        printAtMostExclusive(6.99f); // OK
                        printAtMostExclusive(7.0f); // ERROR
                        printAtMostExclusive(7.1f); // ERROR

                        printBetweenFromInclusiveToInclusive(2.4f); // ERROR
                        printBetweenFromInclusiveToInclusive(2.5f); // OK
                        printBetweenFromInclusiveToInclusive(3f); // OK
                        printBetweenFromInclusiveToInclusive(5.0f); // OK
                        printBetweenFromInclusiveToInclusive(5.1f); // ERROR

                        printBetweenFromExclusiveToInclusive(2.4f); // ERROR
                        printBetweenFromExclusiveToInclusive(2.5f); // ERROR
                        printBetweenFromExclusiveToInclusive(5.0f); // OK
                        printBetweenFromExclusiveToInclusive(5.1f); // ERROR

                        printBetweenFromInclusiveToExclusive(2.4f); // ERROR
                        printBetweenFromInclusiveToExclusive(2.5f); // OK
                        printBetweenFromInclusiveToExclusive(3f); // OK
                        printBetweenFromInclusiveToExclusive(4.99f); // OK
                        printBetweenFromInclusiveToExclusive(5.0f); // ERROR

                        printBetweenFromExclusiveToExclusive(2.4f); // ERROR
                        printBetweenFromExclusiveToExclusive(2.5f); // ERROR
                        printBetweenFromExclusiveToExclusive(2.51f); // OK
                        printBetweenFromExclusiveToExclusive(4.99f); // OK
                        printBetweenFromExclusiveToExclusive(5.0f); // ERROR
                    }

                    public void testNegative() {
                        printBetween(-7); // ERROR
                        printAtLeastExclusive(-10.0f); // ERROR
                    }

                    public static final int MINIMUM = -1;
                    public static final int MAXIMUM = 42;
                    public void printIndirect(@IntRange(from = MINIMUM, to = MAXIMUM) int arg) { }
                    public static final int SIZE = 5;
                    public static void printIndirectSize(@Size(SIZE) String foo) { }

                    public void testIndirect() {
                        printIndirect(-2); // ERROR
                        printIndirect(43); // ERROR
                        printIndirectSize("1234567"); // ERROR
                    }
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_CLASS_PATH,
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            """
src/test/pkg/RangeTest.java:32: Error: Expected length 5 (was 4) [Range]
        printExact("1234"); // ERROR
                   ~~~~~~
src/test/pkg/RangeTest.java:34: Error: Expected length 5 (was 6) [Range]
        printExact("123456"); // ERROR
                   ~~~~~~~~
src/test/pkg/RangeTest.java:36: Error: Expected length ≥ 5 (was 4) [Range]
        printMin("1234"); // ERROR
                 ~~~~~~
src/test/pkg/RangeTest.java:43: Error: Expected length ≤ 8 (was 9) [Range]
        printMax("123456789"); // ERROR
                 ~~~~~~~~~~~
src/test/pkg/RangeTest.java:45: Error: Expected length ≥ 4 (was 3) [Range]
        printRange("123"); // ERROR
                   ~~~~~
src/test/pkg/RangeTest.java:49: Error: Expected length ≤ 6 (was 7) [Range]
        printRange("1234567"); // ERROR
                   ~~~~~~~~~
src/test/pkg/RangeTest.java:53: Error: Expected size 5 (was 4) [Range]
        printExact(new int[]{1, 2, 3, 4}); // ERROR
                   ~~~~~~~~~~~~~~~~~~~~~
src/test/pkg/RangeTest.java:55: Error: Expected size 5 (was 6) [Range]
        printExact(new int[]{1, 2, 3, 4, 5, 6}); // ERROR
                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~
src/test/pkg/RangeTest.java:57: Error: Expected size ≥ 5 (was 4) [Range]
        printMin(new int[]{1, 2, 3, 4}); // ERROR
                 ~~~~~~~~~~~~~~~~~~~~~
src/test/pkg/RangeTest.java:65: Error: Expected size ≤ 8 (was 9) [Range]
        printMax(new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9}); // ERROR
                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
src/test/pkg/RangeTest.java:67: Error: Expected size ≥ 4 (was 3) [Range]
        printRange(new int[] {1,2,3}); // ERROR
                   ~~~~~~~~~~~~~~~~~
src/test/pkg/RangeTest.java:71: Error: Expected size ≤ 6 (was 7) [Range]
        printRange(new int[] {1,2,3,4,5,6,7}); // ERROR
                   ~~~~~~~~~~~~~~~~~~~~~~~~~
src/test/pkg/RangeTest.java:74: Error: Expected size to be a multiple of 3 (was 4 and should be either 3 or 6) [Range]
        printMultiple(new int[] {1,2,3,4}); // ERROR
                      ~~~~~~~~~~~~~~~~~~~
src/test/pkg/RangeTest.java:75: Error: Expected size to be a multiple of 3 (was 5 and should be either 3 or 6) [Range]
        printMultiple(new int[] {1,2,3,4,5}); // ERROR
                      ~~~~~~~~~~~~~~~~~~~~~
src/test/pkg/RangeTest.java:77: Error: Expected size to be a multiple of 3 (was 7 and should be either 6 or 9) [Range]
        printMultiple(new int[] {1,2,3,4,5,6,7}); // ERROR
                      ~~~~~~~~~~~~~~~~~~~~~~~~~
src/test/pkg/RangeTest.java:80: Error: Expected size ≥ 4 (was 3) [Range]
        printMinMultiple(new int[]{1, 2, 3}); // ERROR
                         ~~~~~~~~~~~~~~~~~~
src/test/pkg/RangeTest.java:84: Error: Value must be ≥ 4 (was 3) [Range]
        printAtLeast(3); // ERROR
                     ~
src/test/pkg/RangeTest.java:91: Error: Value must be ≤ 7 (was 8) [Range]
        printAtMost(8); // ERROR
                    ~
src/test/pkg/RangeTest.java:93: Error: Value must be ≥ 4 (was 3) [Range]
        printBetween(3); // ERROR
                     ~
src/test/pkg/RangeTest.java:98: Error: Value must be ≤ 7 (was 8) [Range]
        printBetween(8); // ERROR
                     ~
src/test/pkg/RangeTest.java:102: Error: Value must be ≥ 2.5 (was 2.49) [Range]
        printAtLeastInclusive(2.49f); // ERROR
                              ~~~~~
src/test/pkg/RangeTest.java:106: Error: Value must be > 2.5 (was 2.49) [Range]
        printAtLeastExclusive(2.49f); // ERROR
                              ~~~~~
src/test/pkg/RangeTest.java:107: Error: Value must be > 2.5 (was 2.5) [Range]
        printAtLeastExclusive(2.5f); // ERROR
                              ~~~~
src/test/pkg/RangeTest.java:113: Error: Value must be ≤ 7.0 (was 7.1) [Range]
        printAtMostInclusive(7.1f); // ERROR
                             ~~~~
src/test/pkg/RangeTest.java:117: Error: Value must be < 7.0 (was 7.0) [Range]
        printAtMostExclusive(7.0f); // ERROR
                             ~~~~
src/test/pkg/RangeTest.java:118: Error: Value must be < 7.0 (was 7.1) [Range]
        printAtMostExclusive(7.1f); // ERROR
                             ~~~~
src/test/pkg/RangeTest.java:120: Error: Value must be ≥ 2.5 (was 2.4) [Range]
        printBetweenFromInclusiveToInclusive(2.4f); // ERROR
                                             ~~~~
src/test/pkg/RangeTest.java:124: Error: Value must be ≤ 5.0 (was 5.1) [Range]
        printBetweenFromInclusiveToInclusive(5.1f); // ERROR
                                             ~~~~
src/test/pkg/RangeTest.java:126: Error: Value must be > 2.5 (was 2.4) [Range]
        printBetweenFromExclusiveToInclusive(2.4f); // ERROR
                                             ~~~~
src/test/pkg/RangeTest.java:127: Error: Value must be > 2.5 (was 2.5) [Range]
        printBetweenFromExclusiveToInclusive(2.5f); // ERROR
                                             ~~~~
src/test/pkg/RangeTest.java:129: Error: Value must be ≤ 5.0 (was 5.1) [Range]
        printBetweenFromExclusiveToInclusive(5.1f); // ERROR
                                             ~~~~
src/test/pkg/RangeTest.java:131: Error: Value must be ≥ 2.5 (was 2.4) [Range]
        printBetweenFromInclusiveToExclusive(2.4f); // ERROR
                                             ~~~~
src/test/pkg/RangeTest.java:135: Error: Value must be < 5.0 (was 5.0) [Range]
        printBetweenFromInclusiveToExclusive(5.0f); // ERROR
                                             ~~~~
src/test/pkg/RangeTest.java:137: Error: Value must be > 2.5 (was 2.4) [Range]
        printBetweenFromExclusiveToExclusive(2.4f); // ERROR
                                             ~~~~
src/test/pkg/RangeTest.java:138: Error: Value must be > 2.5 (was 2.5) [Range]
        printBetweenFromExclusiveToExclusive(2.5f); // ERROR
                                             ~~~~
src/test/pkg/RangeTest.java:141: Error: Value must be < 5.0 (was 5.0) [Range]
        printBetweenFromExclusiveToExclusive(5.0f); // ERROR
                                             ~~~~
src/test/pkg/RangeTest.java:145: Error: Value must be ≥ 4 (was -7) [Range]
        printBetween(-7); // ERROR
                     ~~
src/test/pkg/RangeTest.java:146: Error: Value must be > 2.5 (was -10.0) [Range]
        printAtLeastExclusive(-10.0f); // ERROR
                              ~~~~~~
src/test/pkg/RangeTest.java:156: Error: Value must be ≥ -1 (was -2) [Range]
        printIndirect(-2); // ERROR
                      ~~
src/test/pkg/RangeTest.java:157: Error: Value must be ≤ 42 (was 43) [Range]
        printIndirect(43); // ERROR
                      ~~
src/test/pkg/RangeTest.java:158: Error: Expected length 5 (was 7) [Range]
        printIndirectSize("1234567"); // ERROR
                          ~~~~~~~~~
41 errors, 0 warnings
"""
        )
    }

    /**
     * Test @IntRange and @FloatRange support annotation applied to arrays and vargs.
     */
    fun testRangesMultiple() {
        lint().files(
            java(
                "src/test/pkg/RangesMultiple.java",
                """
                package test.pkg;
                import android.support.annotation.FloatRange;
                import android.support.annotation.IntRange;
                @SuppressWarnings("ClassNameDiffersFromFileName")
                public class RangesMultiple {
                    private static final float[] VALID_FLOAT_ARRAY = new float[] {10.0f, 12.0f, 15.0f};
                    private static final float[] INVALID_FLOAT_ARRAY = new float[] {10.0f, 12.0f, 5.0f};

                    private static final int[] VALID_INT_ARRAY = new int[] {15, 120, 500};
                    private static final int[] INVALID_INT_ARRAY = new int[] {15, 120, 5};

                    @FloatRange(from = 10.0, to = 15.0)
                    public float[] a;

                    @IntRange(from = 10, to = 500)
                    public int[] b;

                    public void testCall() {
                        a = new float[2];
                        a[0] = /*Value must be ≥ 10.0 and ≤ 15.0 (was 5f)*/5f/**/; // ERROR
                        a[1] = 14f; // OK
                        varargsFloat(15.0f, 10.0f, /*Value must be ≥ 10.0 and ≤ 15.0 (was 5.0f)*/5.0f/**/); // ERROR
                        restrictedFloatArray(VALID_FLOAT_ARRAY); // OK
                        restrictedFloatArray(/*Value must be ≥ 10.0 and ≤ 15.0*/INVALID_FLOAT_ARRAY/**/); // ERROR
                        restrictedFloatArray(new float[]{10.5f, 14.5f}); // OK
                        restrictedFloatArray(/*Value must be ≥ 10.0 and ≤ 15.0*/new float[]{12.0f, 500.0f}/**/); // ERROR


                        b = new int[2];
                        b[0] = /*Value must be ≥ 10 and ≤ 500 (was 5)*/5/**/; // ERROR
                        b[1] = 100; // OK
                        varargsInt(15, 10, /*Value must be ≥ 10 and ≤ 500 (was 510)*/510/**/); // ERROR
                        restrictedIntArray(VALID_INT_ARRAY); // OK
                        restrictedIntArray(/*Value must be ≥ 10 and ≤ 500*/INVALID_INT_ARRAY/**/); // ERROR
                        restrictedIntArray(new int[]{50, 500}); // OK
                        restrictedIntArray(/*Value must be ≥ 10 and ≤ 500*/new int[]{0, 500}/**/); // ERROR
                    }

                    public void restrictedIntArray(@IntRange(from = 10, to = 500) int[] a) {
                    }

                    public void varargsInt(@IntRange(from = 10, to = 500) int... a) {
                    }

                    public void varargsFloat(@FloatRange(from = 10.0, to = 15.0) float... a) {
                    }

                    public void restrictedFloatArray(@FloatRange(from = 10.0, to = 15.0) float[] a) {
                    }
                }

                """
            ).indented(),
            SUPPORT_ANNOTATIONS_CLASS_PATH,
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            """
src/test/pkg/RangesMultiple.java:20: Error: Value must be ≥ 10.0 (was 5) [Range]
        a[0] = /*Value must be ≥ 10.0 and ≤ 15.0 (was 5f)*/5f/**/; // ERROR
                                                           ~~
src/test/pkg/RangesMultiple.java:22: Error: Value must be ≥ 10.0 (was 5.0) [Range]
        varargsFloat(15.0f, 10.0f, /*Value must be ≥ 10.0 and ≤ 15.0 (was 5.0f)*/5.0f/**/); // ERROR
                                                                                 ~~~~
src/test/pkg/RangesMultiple.java:24: Error: Value must be ≥ 10.0 (was 5.0) [Range]
        restrictedFloatArray(/*Value must be ≥ 10.0 and ≤ 15.0*/INVALID_FLOAT_ARRAY/**/); // ERROR
                                                                ~~~~~~~~~~~~~~~~~~~
src/test/pkg/RangesMultiple.java:26: Error: Value must be ≤ 15.0 (was 500.0) [Range]
        restrictedFloatArray(/*Value must be ≥ 10.0 and ≤ 15.0*/new float[]{12.0f, 500.0f}/**/); // ERROR
                                                                ~~~~~~~~~~~~~~~~~~~~~~~~~~
src/test/pkg/RangesMultiple.java:30: Error: Value must be ≥ 10 (was 5) [Range]
        b[0] = /*Value must be ≥ 10 and ≤ 500 (was 5)*/5/**/; // ERROR
                                                       ~
src/test/pkg/RangesMultiple.java:32: Error: Value must be ≤ 500 (was 510) [Range]
        varargsInt(15, 10, /*Value must be ≥ 10 and ≤ 500 (was 510)*/510/**/); // ERROR
                                                                     ~~~
src/test/pkg/RangesMultiple.java:34: Error: Value must be ≥ 10 (was 5) [Range]
        restrictedIntArray(/*Value must be ≥ 10 and ≤ 500*/INVALID_INT_ARRAY/**/); // ERROR
                                                           ~~~~~~~~~~~~~~~~~
src/test/pkg/RangesMultiple.java:36: Error: Value must be ≥ 10 (was 0) [Range]
        restrictedIntArray(/*Value must be ≥ 10 and ≤ 500*/new int[]{0, 500}/**/); // ERROR
                                                           ~~~~~~~~~~~~~~~~~
8 errors, 0 warnings
        """
        )
    }

    fun testNegativeFloatRange() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=219246
        // Make sure we correctly handle negative ranges for floats
        lint().files(
            java(
                "src/test/pkg/FloatRangeTest.java",
                """
                package test.pkg;

                import android.support.annotation.FloatRange;

                @SuppressWarnings({"unused", "ClassNameDiffersFromFileName"})
                public class FloatRangeTest {
                    public void test() {
                        call(-150.0); // ERROR
                        call(-45.0); // OK
                        call(-3.0); // ERROR
                    }

                    private void call(@FloatRange(from=-90.0, to=-5.0) double arg) {
                    }
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_CLASS_PATH,
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            """
src/test/pkg/FloatRangeTest.java:8: Error: Value must be ≥ -90.0 (was -150.0) [Range]
        call(-150.0); // ERROR
             ~~~~~~
src/test/pkg/FloatRangeTest.java:10: Error: Value must be ≤ -5.0 (was -3.0) [Range]
        call(-3.0); // ERROR
             ~~~~
2 errors, 0 warnings
"""
        )
    }

    // TODO: http://b.android.com/220686
    fun ignore_testSnackbarDuration() {
        lint().files(
            java(
                "src/test/pkg/SnackbarTest.java",
                "" +
                    "package test.pkg;\n" +
                    "\n" +
                    "import android.support.design.widget.Snackbar;\n" +
                    "\n" +
                    "public class SnackbarTest {\n" +
                    "    public Snackbar makeSnackbar(@Snackbar.Duration int duration) {\n" +
                    "        return null;\n" +
                    "    }\n" +
                    "\n" +
                    "    public void test() {\n" +
                    "        makeSnackbar(Snackbar.LENGTH_LONG); // OK\n" +
                    "        makeSnackbar(100); // OK\n" +
                    "        makeSnackbar(-100); // ERROR\n" +
                    "    }\n" +
                    "}\n"
            ),
            java(
                "src/android/support/design/widget/Snackbar.java",
                "" +
                    "package android.support.design.widget;\n" +
                    "\n" +
                    "import android.support.annotation.IntDef;\n" +
                    "import android.support.annotation.IntRange;\n" +
                    "\n" +
                    "import java.lang.annotation.Retention;\n" +
                    "import java.lang.annotation.RetentionPolicy;\n" +
                    "\n" +
                    "public class Snackbar {\n" +
                    // In the real class definition, this annotation is there,
                    // but in the compiled design library, since it has source
                    // retention, the @IntDef is missing and only the @IntRange
                    // remains. Therefore, it's been extracted into the external
                    // database. We don't want to count it twice so don't repeat
                    // it here:
                    // "    @IntDef({LENGTH_INDEFINITE, LENGTH_SHORT, LENGTH_LONG})\n" +
                    "    @IntRange(from = 1)\n" +
                    "    @Retention(RetentionPolicy.SOURCE)\n" +
                    "    public @interface Duration {}\n" +
                    "\n" +
                    "    public static final int LENGTH_INDEFINITE = -2;\n" +
                    "    public static final int LENGTH_SHORT = -1;\n" +
                    "    public static final int LENGTH_LONG = 0;\n" +
                    "}\n"
            ),
            SUPPORT_ANNOTATIONS_CLASS_PATH,
            SUPPORT_ANNOTATIONS_JAR
        )
            .issues(RangeDetector.RANGE, TypedefDetector.TYPE_DEF)
            .run()
            .expect(
                """
src/test/pkg/SnackbarTest.java:13: Error: Must be one of: Snackbar.LENGTH_INDEFINITE, Snackbar.LENGTH_SHORT, Snackbar.LENGTH_LONG or value must be ≥ 1 (was -100) [WrongConstant]
        makeSnackbar(-100); // ERROR
                     ~~~~
1 errors, 0 warnings
        """
            )
    }

    fun testSizeAnnotations() {
        lint().files(
            java(
                "" +
                    "package pkg.my.myapplication;\n" +
                    "\n" +
                    "import android.support.annotation.NonNull;\n" +
                    "import android.support.annotation.Size;\n" +
                    "\n" +
                    "public class SizeTest2 {\n" +
                    "    @Size(3)\n" +
                    "    public float[] toLinear(float r, float g, float b) {\n" +
                    "        return toLinear(new float[] { r, g, b });\n" +
                    "    }\n" +
                    "\n" +
                    "    @NonNull\n" +
                    "    public float[] toLinear(@NonNull @Size(min = 3) float[] v) {\n" +
                    "        return v;\n" +
                    "    }\n" +
                    "}\n"
            ),
            SUPPORT_ANNOTATIONS_CLASS_PATH,
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun testOverlappingRanges() {

        lint().files(
            java(
                "" +
                    "\n" +
                    "package pkg.my.myapplication;\n" +
                    "\n" +
                    "import android.support.annotation.IntRange;\n" +
                    "import android.support.annotation.Size;\n" +
                    "\n" +
                    "@SuppressWarnings({\"WeakerAccess\", \"ConstantConditions\", \"UnusedParameters\", \"unused\"})\n" +
                    "public class X {\n" +
                    "    public void testSize(\n" +
                    "            @Size(4) int exactly4,\n" +
                    "            @Size(2) int exactly2,\n" +
                    "            @Size(min = 5) int atLeast5,\n" +
                    "            @Size(max = 5) int atMost5,\n" +
                    "            @Size(min = 2, max = 5) int between2and5,\n" +
                    "            @Size(multiple = 3) int triple,\n" +
                    "            @Size(min = 4, multiple = 3) int tripleFrom4,\n" +
                    "            @Size(min = 4, multiple = 4) int quadrupleFrom4) {\n" +
                    "        sizeMin3(/*Size must be at least 3*/exactly2/**/); // ERROR\n" +
                    "        sizeMin3(exactly4); // OK\n" +
                    "        sizeMin3(atLeast5); // OK\n" +
                    "        sizeMin3(/*Size must be at least 3*/atMost5/**/); // ERROR: delta\n" +
                    "        sizeMin3(/*Size must be at least 3*/between2and5/**/); // ERROR: 2 is not included\n" +
                    "        sizeMin3(/*Size must be at least 3*/triple/**/); // ERROR\n" +
                    "        sizeMin3(tripleFrom4); // OK\n" +
                    "\n" +
                    "        sizeMin3multiple2(/*Size must be at least 3 and a multiple of 2*/tripleFrom4/**/); // ERROR\n" +
                    "        sizeMin3multiple2(quadrupleFrom4); // OK\n" +
                    "\n" +
                    "        sizeMax10(exactly2);\n" +
                    "        sizeMax10(exactly4);\n" +
                    "        sizeMax10(/*Size must be at most 10*/atLeast5/**/); // ERROR: allows numbers outside the max\n" +
                    "        sizeMax10(between2and5); // OK\n" +
                    "        sizeMax10(/*Size must be at most 10*/triple/**/); // ERROR: allows numbers outside the max\n" +
                    "    }\n" +
                    "\n" +
                    "    public void testIntRange(\n" +
                    "            @IntRange(from = 5) int atLeast5,\n" +
                    "            @IntRange(to = 5) int atMost5,\n" +
                    "            @IntRange(from = 2, to = 5) int between2and5,\n" +
                    "            @IntRange(from = 4, to = 6) int between4and6) {\n" +
                    "        rangeMin3(atLeast5); // OK\n" +
                    "        rangeMin3(/*Value must be ≥ 3*/atMost5/**/); // ERROR: delta\n" +
                    "        rangeMin3(/*Value must be ≥ 3*/between2and5/**/); // ERROR: 2 is not included\n" +
                    "\n" +
                    "        range3to6(/*Value must be ≥ 3 and ≤ 6*/atLeast5/**/); // ERROR\n" +
                    "        range3to6(/*Value must be ≥ 3 and ≤ 6*/atMost5/**/); // ERROR\n" +
                    "        range3to6(/*Value must be ≥ 3 and ≤ 6*/between2and5/**/); // ERROR not overlapping\n" +
                    "        range3to6(between4and6); // OK\n" +
                    "\n" +
                    "        rangeMax10(/*Value must be ≤ 10*/atLeast5/**/); // ERROR: allows numbers outside the max\n" +
                    "        rangeMax10(between2and5); // OK\n" +
                    "        rangeMax10(atMost5); // OK\n" +
                    "    }\n" +
                    "    public void sizeMin3(@Size(min = 3) int size) {\n" +
                    "    }\n" +
                    "\n" +
                    "    public void sizeMin3multiple2(@Size(min = 3, multiple = 2) int size) {\n" +
                    "    }\n" +
                    "\n" +
                    "    public void sizeMax10(@Size(max = 10) int size) {\n" +
                    "    }\n" +
                    "\n" +
                    "    public void rangeMin3(@IntRange(from = 3) int range) {\n" +
                    "    }\n" +
                    "\n" +
                    "    public void range3to6(@IntRange(from = 3, to = 6) int range) {\n" +
                    "    }\n" +
                    "\n" +
                    "    public void rangeMax10(@IntRange(to = 10) int range) {\n" +
                    "    }\n" +
                    "}\n"
            ),
            SUPPORT_ANNOTATIONS_CLASS_PATH,
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectInlinedMessages()
    }

    fun testConstructor() {
        val expected =
            """
src/test/pkg/ConstructorTest.java:14: Error: Value must be ≥ 5 (was 3) [Range]
        new ConstructorTest(1, 3);
                               ~
1 errors, 0 warnings
"""
        lint().files(
            java(
                "src/test/pkg/ConstructorTest.java",
                """
                package test.pkg;
                import android.support.annotation.DrawableRes;
                import android.support.annotation.IntRange;
                import android.support.annotation.UiThread;
                import android.support.annotation.WorkerThread;

                @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic", "ResultOfObjectAllocationIgnored"})
                public class ConstructorTest {
                    @UiThread
                    ConstructorTest(@DrawableRes int iconResId, @IntRange(from = 5) int start) {
                    }

                    public void testParameters() {
                        new ConstructorTest(1, 3);
                    }

                    @WorkerThread
                    public void testMethod(int res, int range) {
                        new ConstructorTest(res, range);
                    }
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_CLASS_PATH,
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(expected)
    }

    fun testConstrainedIntRanges() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=188351

        lint().files(
            java(
                "" +
                    "package test.pkg;\n" +
                    "\n" +
                    "import android.support.annotation.IntRange;\n" +
                    "\n" +
                    "public class X {\n" +
                    "    public int forcedMeasureHeight = -1;\n" +
                    "\n" +
                    "    public void testVariable() {\n" +
                    "        int parameter = -1;\n" +
                    "        if (parameter >= 0) {\n" +
                    "            method(parameter); // OK\n" +
                    "        }\n" +
                    "    }\n" +
                    "\n" +
                    "    public void testOk1(boolean ok) {\n" +
                    "        if (forcedMeasureHeight >= 0) {\n" +
                    "            method(forcedMeasureHeight); // OK\n" +
                    "        }\n" +
                    "        if (ok && forcedMeasureHeight >= 0) {\n" +
                    "            method(forcedMeasureHeight); // OK\n" +
                    "        }\n" +
                    "    }\n" +
                    "\n" +
                    "    public void testError(boolean ok, int unrelated) {\n" +
                    // Disabled for now -- we're not passing allowFieldInitializers()
                    // into the ConstantEvaluator since it's risky to be complaining
                    // about invalid values for a non-final field which could easily
                    // have been reassigned anywhere to something compatible.
                    // "        method(/*Value must be ≥ 0 (was -1)*/forcedMeasureHeight/**/); // ERROR\n" +
                    // "        if (ok && unrelated >= 0) {\n" +
                    // "            method(/*Value must be ≥ 0 (was -1)*/forcedMeasureHeight/**/); // ERROR\n" +
                    // "        }\n" +
                    "        method(forcedMeasureHeight); // ERROR\n" +
                    "        if (ok && unrelated >= 0) {\n" +
                    "            method(forcedMeasureHeight); // ERROR\n" +
                    "        }\n" +
                    "    }\n" +
                    "\n" +
                    "    public void method(@IntRange(from=0) int parameter) {\n" +
                    "    }\n" +
                    "}\n"
            ),
            SUPPORT_ANNOTATIONS_CLASS_PATH,
            SUPPORT_ANNOTATIONS_JAR
        )
            .run().expectInlinedMessages()
    }

    fun testIntRangeOnTernaryOperators() {

        lint().files(
            java(
                "" +
                    "package test.pkg;\n" +
                    "\n" +
                    "import android.support.annotation.IntRange;\n" +
                    "\n" +
                    "import java.util.ArrayList;\n" +
                    "import java.util.List;\n" +
                    "\n" +
                    "public class X {\n" +
                    "    @SuppressWarnings(\"MismatchedQueryAndUpdateOfCollection\")\n" +
                    "    private List<String> mItems = new ArrayList<>();\n" +
                    "\n" +
                    "    @IntRange(from = 0)\n" +
                    "    public int test1() {\n" +
                    "        return mItems == null ? 0 : mItems.size(); // OK\n" +
                    "    }\n" +
                    "\n" +
                    "    @IntRange(from = 0)\n" +
                    "    public int test2() {\n" +
                    "        return 0; // OK\n" +
                    "    }\n" +
                    "\n" +
                    "    @IntRange(from = 0)\n" +
                    "    public int test3() {\n" +
                    "        return mItems.size(); // OK\n" +
                    "    }\n" +
                    "\n" +
                    "    @IntRange(from = 0)\n" +
                    "    public int test4() {\n" +
                    "        if (mItems == null) {\n" +
                    "            return 0;\n" +
                    "        } else {\n" +
                    "            return mItems.size();\n" +
                    "        }\n" +
                    "    }\n" +
                    "\n" +
                    "    @IntRange(from = 0)\n" +
                    "    public int testError() {\n" +
                    "        return mItems == null ? /*Value must be ≥ 0 (was -1)*/-1/**/ : mItems.size(); // ERROR\n" +
                    "\n" +
                    "    }\n" +
                    "}\n"
            ),
            SUPPORT_ANNOTATIONS_CLASS_PATH,
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectInlinedMessages()
    }

    fun testRangeInKotlin() {
        // Regression test for https://issuetracker.google.com/66892728

        lint().files(
            // TODO: Test @IntRange to make sure UAST doesn't replace it with @kotlin.IntRange
            kotlin(
                "" +
                    "package test.pkg\n" +
                    "\n" +
                    "import android.support.annotation.FloatRange\n" +
                    "import android.util.Log\n" +
                    "\n" +
                    "fun foo(@FloatRange(from = 0.0, to = 25.0) radius: Float) {\n" +
                    "    bar(radius)\n" +
                    "}\n" +
                    "\n" +
                    "fun bar(@FloatRange(from = 0.0, to = 25.0) radius: Float) {\n" +
                    "    Log.d(\"AppLog\", \"Radius:\" + radius)\n" +
                    "}"
            ),
            SUPPORT_ANNOTATIONS_CLASS_PATH,
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun testRangesFromKotlin() {
        lint().files(
            kotlin(
                "" +
                    "package test.pkg\n" +
                    "\n" +
                    "import android.support.annotation.FloatRange\n" +
                    "import android.support.annotation.IntRange\n" +
                    "\n" +
                    "fun check(@FloatRange(from = 0.0, to = 25.0) radius: Float) {\n" +
                    "}\n" +
                    "\n" +
                    "fun check(@IntRange(from = 0, to = 25) radius: Int) {\n" +
                    "}\n" +
                    "\n" +
                    "fun wrong() {\n" +
                    "    check(10) // OK\n" +
                    "    check(10.0f) // OK\n" +
                    "    check(100) // ERROR\n" +
                    "    check(100.0f) // ERROR\n" +
                    "}"
            ),
            SUPPORT_ANNOTATIONS_CLASS_PATH,
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            "" +
                "src/test/pkg/test.kt:15: Error: Value must be ≤ 25 (was 100) [Range]\n" +
                "    check(100) // ERROR\n" +
                "          ~~~\n" +
                "src/test/pkg/test.kt:16: Error: Value must be ≤ 25.0 (was 100.0) [Range]\n" +
                "    check(100.0f) // ERROR\n" +
                "          ~~~~~~\n" +
                "2 errors, 0 warnings"
        )
    }

    fun test69366129() {
        // Regression test for
        // 69366129: Range bug after upgrading Android Studio - Value must be ≤ 1.0 (was 100) less

        lint().files(
            java(
                """
                    package test.pkg;
                    import android.support.annotation.FloatRange;
                    import java.util.Random;

                    @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                    public class RangeTest {
                        void test(int luminance) {
                            double lum = random(luminance) * 100;
                        }

                        @FloatRange(from = 0.0, to = 1.0)
                        private static double random(int seed) {
                            return new Random(seed).nextDouble();
                        }
                    }
                    """
            ).indented(),
            SUPPORT_ANNOTATIONS_CLASS_PATH,
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun test79907796() {
        // Regression test for
        // 79907796: Range Check fails for arrays with size greater than 30
        lint().files(
            java(
                """
                    package test.pkg;
                    import android.support.annotation.Size;
                    import java.util.Random;

                    @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                    public class SizeTest {
                        public void doSomethingWithArrayOf40(@Size(40) int[] xValues) {
                        }

                        void test(int luminance) {
                            int[] fortyValues = new int[40];
                            doSomethingWithArrayOf40(fortyValues);
                        }
                    }
                    """
            ).indented(),
            SUPPORT_ANNOTATIONS_CLASS_PATH,
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun testKotlinArraySize() {
        intArrayOf()
        // Regression test for
        // 79907796: Range Check fails for arrays with size greater than 30
        lint().files(
            kotlin(
                """
                    package test.pkg
                    import android.support.annotation.Size

                    class SizeTest {
                        fun method(@Size(5) collection: IntArray) { }

                        fun test() {
                            val array1 = arrayOf(1,2,3,4,5)
                            method(array1) // OK

                            val array2 = arrayOf(1,2,3,4)
                            method(array2) // ERROR
                        }
                    }
                    """
            ).indented(),
            SUPPORT_ANNOTATIONS_CLASS_PATH,
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            """
            src/test/pkg/SizeTest.kt:12: Error: Expected size 5 (was 4) [Range]
                    method(array2) // ERROR
                           ~~~~~~
            1 errors, 0 warnings
            """
        )
    }

    fun testRangeAnnotationsInCompiledJars() {
        val libJarPath = "jar/jar/annotation_test.jar"
        val base64JarData = "" +
            "H4sIAAAAAAAAAAvwZmbhYmDgYAACRRsGJMDJwMLg6xriqOvp56b/7xQDQwCK" +
            "0h/FzxlqgSwQFgFiuFJfRz9PN9fgED1fN9/EvMy01OIS3bDUouLM/DwrBUM9" +
            "A14u56LUxJLUFF2nSiuFpMSq1BxerpDEovTUEl2fxKTUHCsFff3UioLUoszc" +
            "1LySxBz90mKgdv3EosTs1OIM/azEskQgUQTCVol5efkliSVAs+NzMvNK4nm5" +
            "FEqAFvJy8XIF4PQZCxCDDMCtggOqAqZKhIGDg4OBEU2VHJIqR7hDip1zEouL" +
            "9ZJBZGtgrH+Uo4Atd4XjFtZMA5HbJtsCMn33XDkxuXdilofBF+UbE74UPtTb" +
            "9WRT9je9b+VfTy7h/8v4b8W1m9oBqzJ3n3zz5sy5OcbV3349/y7P8O2DUbdK" +
            "3JJAz8WnHuyd6nWLuS3lbWi/YukS5oMTXnuo2vkcq020+1znMdPJnb/ixmO3" +
            "XRWzfG4WSxpOsVbOq0n3XbrlaViWqGyrTdc3Sa/DAcW/O6Xt6tTfhCuG7F3Z" +
            "ZP7Vauv1qL7JvCejeH5+PzTvtYKeaJiE07oS9VOnNmas9pb0fDQnXs/AMKd0" +
            "B1fPtEZ5Xz29LRvt5sf/1j1+/+krnwsmKxdeTOa/fHTChlvWf4y6ry3dZf4y" +
            "b/FDtdzjk702mTl/ja/JNU2NnnXxDN9+iU3Lq5LYFqnOrTm6u+htzw/VeFcu" +
            "7yW375uoLoyZKS/U+X+jvRBzt6/Ki+C7XpeMV8wv++4136ZYeM8+pt9u7SyX" +
            "tlQXXnJu+3b1NpOOsnedpPYUz2Nnb29U23F/RlxGSkVD+5NiviOsDvNyzn5c" +
            "Fxj+UKI/wOaYleP0Dz/qL8y0X3D6xg/e2LiWukcL/D+IHBW2WVxhfKyyjjnA" +
            "m53DTqBGahkjA8NTJlBCZ2TiYsCdK1ABWh5B1YqeSxBAG0eewWc5qtW34MkY" +
            "tw4OFB1/UJI1I5MIAyJhIweAHIouJUZCyTzAm5UNpJIVCA2BqkWYQTwAgU6w" +
            "1VsEAAA="

        val customLib = base64gzip(libJarPath, base64JarData)
        val classPath = classpath(SUPPORT_JAR_PATH, libJarPath)

        lint().files(
            java(
                "package test.pkg;\n" +
                    "\n" +
                    "import android.support.annotation.FloatRange;\n" +
                    "import android.support.annotation.IntRange;\n" +
                    "import jar.jar.AnnotationsClass;\n" +
                    "\n" +
                    "public class TestClass {\n" +
                    "  public static void callMethod() {\n" +
                    "    AnnotationsClass.floatParamBetween0And100(50); // Within Range\n" +
                    "    AnnotationsClass.floatParamBetween0And100(552); // Outside Range\n" +
                    "\n" +
                    "    AnnotationsClass.intParamBetween0And255(51); // Within Range\n" +
                    "    AnnotationsClass.intParamBetween0And255(551); // Outside Range\n" +
                    "\n" +
                    "    inClassIntParamFrom0To255(52); // Within Range\n" +
                    "    inClassIntParamFrom0To255(550); // Outside Range\n" +
                    "\n" +
                    "    inClassFloatParamFrom0To100(53); // Within Range\n" +
                    "    inClassFloatParamFrom0To100(549); // Outside Range\n" +
                    "  }\n" +
                    "\n" +
                    "  private static void inClassIntParamFrom0To255(@IntRange(from = 0, to = 255) int i) {}\n" +
                    "  private static void inClassFloatParamFrom0To100(@FloatRange(from = 0, to = 100) float f) {}\n" +
                    "}\n"
            ),
            classPath,
            SUPPORT_ANNOTATIONS_JAR,
            customLib
        )
            .run()
            .expect(
                "src/test/pkg/TestClass.java:10: Error: Value must be ≤ 100.0 (was 552) [Range]\n" +
                    "    AnnotationsClass.floatParamBetween0And100(552); // Outside Range\n" +
                    "                                              ~~~\n" +
                    "src/test/pkg/TestClass.java:13: Error: Value must be ≤ 255 (was 551) [Range]\n" +
                    "    AnnotationsClass.intParamBetween0And255(551); // Outside Range\n" +
                    "                                            ~~~\n" +
                    "src/test/pkg/TestClass.java:16: Error: Value must be ≤ 255 (was 550) [Range]\n" +
                    "    inClassIntParamFrom0To255(550); // Outside Range\n" +
                    "                              ~~~\n" +
                    "src/test/pkg/TestClass.java:19: Error: Value must be ≤ 100.0 (was 549) [Range]\n" +
                    "    inClassFloatParamFrom0To100(549); // Outside Range\n" +
                    "                                ~~~\n" +
                    "4 errors, 0 warnings"
            )
    }

    fun testFlow() {
        // Regression test for
        // https://issuetracker.google.com/37124951
        // Make sure that in the code snippet below, the flow analysis
        // doesn't look beyond the previous assignment to alpha
        // when trying to figure out the range
        lint().files(
            java(
                """
                package test.pkg;
                import android.graphics.drawable.Drawable;
                public class AlphaTest {
                    void test(Drawable d) {
                        int alpha = -1;
                        long l = System.currentTimeMillis();
                        if (l != 0) {
                            alpha = (int) (l % 256);
                            d.setAlpha(alpha);
                        }
                    }
                }
                """
            ).indented()
        ).run().expectClean()
    }

    fun testAndroidXBug() {
        // Regression test for bug uncovered by continuous androidx+studio integration build
        lint().files(
            kotlin(
                """
                package test.pkg
                import android.support.annotation.IntRange
                import android.support.annotation.Size
                import test.pkg.ColorSpace.Companion.MaxId
                import test.pkg.ColorSpace.Companion.MinId
                private fun isSrgb(
                        @Size(6) primaries: FloatArray,
                        OETF: (Double) -> Double,
                        EOTF: (Double) -> Double,
                        min: Float,
                        max: Float,
                        @IntRange(from = MinId.toLong(), to = MaxId.toLong()) id: Int
                ): Boolean {
                    if (id == 0) return true
                    return false
                }
                """
            ).indented(),
            kotlin(
                """
                package test.pkg
                abstract class ColorSpace {
                    internal companion object {
                        internal const val MinId = -1
                        internal const val MaxId = 63
                    }
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }
}
