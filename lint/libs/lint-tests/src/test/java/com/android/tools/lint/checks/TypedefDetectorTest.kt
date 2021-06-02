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

class TypedefDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector = TypedefDetector()

    fun testDocumentationExample() {
        lint().files(
            kotlin(
                """
                import android.view.View
                import androidx.annotation.IntDef

                // Example of using Android API already annotated with @IntDef:

                fun setAlignment(view: View) {
                    view.layoutDirection = View.LAYOUT_DIRECTION_RTL // OK
                    view.layoutDirection = View.TEXT_ALIGNMENT_TEXT_START // ERROR - not one of the allowed values
                }

                // Custom example creating your own typedef:

                const val CONST_1 = -1
                const val CONST_2 = 2
                const val CONST_3 = 0
                const val UNRELATED = 1

                @IntDef(CONST_1, CONST_2, CONST_3)
                @Retention(AnnotationRetention.SOURCE)
                annotation class DetailInfoTab

                fun test(@DetailInfoTab tab: Int) {
                }

                fun test() {
                    test(CONST_1) // OK
                    test(UNRELATED) // ERROR - not part of the @DetailsInfoTab list
                }
                """
            ),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            """
            src/DetailInfoTab.kt:9: Error: Must be one of: View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL, View.LAYOUT_DIRECTION_INHERIT, View.LAYOUT_DIRECTION_LOCALE [WrongConstant]
                                view.layoutDirection = View.TEXT_ALIGNMENT_TEXT_START // ERROR - not one of the allowed values
                                                       ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/DetailInfoTab.kt:28: Error: Must be one of: DetailInfoTabKt.CONST_1, DetailInfoTabKt.CONST_2, DetailInfoTabKt.CONST_3 [WrongConstant]
                                test(UNRELATED) // ERROR - not part of the @DetailsInfoTab list
                                     ~~~~~~~~~
            2 errors, 0 warnings
            """.trimIndent()
        )
    }

    fun testTypeDef() {

        val expected = "" +
            "src/test/pkg/IntDefTest.java:31: Error: Must be one of: IntDefTest.STYLE_NORMAL, IntDefTest.STYLE_NO_TITLE, IntDefTest.STYLE_NO_FRAME, IntDefTest.STYLE_NO_INPUT [WrongConstant]\n" +
            "        setStyle(0, 0); // ERROR\n" +
            "                 ~\n" +
            "src/test/pkg/IntDefTest.java:32: Error: Must be one of: IntDefTest.STYLE_NORMAL, IntDefTest.STYLE_NO_TITLE, IntDefTest.STYLE_NO_FRAME, IntDefTest.STYLE_NO_INPUT [WrongConstant]\n" +
            "        setStyle(-1, 0); // ERROR\n" +
            "                 ~~\n" +
            "src/test/pkg/IntDefTest.java:33: Error: Must be one of: IntDefTest.STYLE_NORMAL, IntDefTest.STYLE_NO_TITLE, IntDefTest.STYLE_NO_FRAME, IntDefTest.STYLE_NO_INPUT [WrongConstant]\n" +
            "        setStyle(UNRELATED, 0); // ERROR\n" +
            "                 ~~~~~~~~~\n" +
            "src/test/pkg/IntDefTest.java:34: Error: Must be one of: IntDefTest.STYLE_NORMAL, IntDefTest.STYLE_NO_TITLE, IntDefTest.STYLE_NO_FRAME, IntDefTest.STYLE_NO_INPUT [WrongConstant]\n" +
            "        setStyle(IntDefTest.UNRELATED, 0); // ERROR\n" +
            "                 ~~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/IntDefTest.java:35: Error: Flag not allowed here [WrongConstant]\n" +
            "        setStyle(IntDefTest.STYLE_NORMAL|STYLE_NO_FRAME, 0); // ERROR: Not a flag\n" +
            "                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/IntDefTest.java:36: Error: Flag not allowed here [WrongConstant]\n" +
            "        setStyle(~STYLE_NO_FRAME, 0); // ERROR: Not a flag\n" +
            "                 ~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/IntDefTest.java:55: Error: Must be one or more of: IntDefTest.STYLE_NORMAL, IntDefTest.STYLE_NO_TITLE, IntDefTest.STYLE_NO_FRAME, IntDefTest.STYLE_NO_INPUT [WrongConstant]\n" +
            "        setFlags(\"\", UNRELATED); // ERROR\n" +
            "                     ~~~~~~~~~\n" +
            "src/test/pkg/IntDefTest.java:56: Error: Must be one or more of: IntDefTest.STYLE_NORMAL, IntDefTest.STYLE_NO_TITLE, IntDefTest.STYLE_NO_FRAME, IntDefTest.STYLE_NO_INPUT [WrongConstant]\n" +
            "        setFlags(\"\", UNRELATED|STYLE_NO_TITLE); // ERROR\n" +
            "                     ~~~~~~~~~\n" +
            "src/test/pkg/IntDefTest.java:57: Error: Must be one or more of: IntDefTest.STYLE_NORMAL, IntDefTest.STYLE_NO_TITLE, IntDefTest.STYLE_NO_FRAME, IntDefTest.STYLE_NO_INPUT [WrongConstant]\n" +
            "        setFlags(\"\", STYLE_NORMAL|STYLE_NO_TITLE|UNRELATED); // ERROR\n" +
            "                                                 ~~~~~~~~~\n" +
            "src/test/pkg/IntDefTest.java:58: Error: Must be one or more of: IntDefTest.STYLE_NORMAL, IntDefTest.STYLE_NO_TITLE, IntDefTest.STYLE_NO_FRAME, IntDefTest.STYLE_NO_INPUT [WrongConstant]\n" +
            "        setFlags(\"\", 1); // ERROR\n" +
            "                     ~\n" +
            "src/test/pkg/IntDefTest.java:59: Error: Must be one or more of: IntDefTest.STYLE_NORMAL, IntDefTest.STYLE_NO_TITLE, IntDefTest.STYLE_NO_FRAME, IntDefTest.STYLE_NO_INPUT [WrongConstant]\n" +
            "        setFlags(\"\", arg < 0 ? STYLE_NORMAL : UNRELATED); // ERROR\n" +
            "                                              ~~~~~~~~~\n" +
            "src/test/pkg/IntDefTest.java:60: Error: Must be one or more of: IntDefTest.STYLE_NORMAL, IntDefTest.STYLE_NO_TITLE, IntDefTest.STYLE_NO_FRAME, IntDefTest.STYLE_NO_INPUT [WrongConstant]\n" +
            "        setFlags(\"\", arg < 0 ? UNRELATED : STYLE_NORMAL); // ERROR\n" +
            "                               ~~~~~~~~~\n" +
            "src/test/pkg/IntDefTest.java:79: Error: Must be one of: IntDefTest.TYPE_1, IntDefTest.TYPE_2 [WrongConstant]\n" +
            "        setTitle(\"\", UNRELATED_TYPE); // ERROR\n" +
            "                     ~~~~~~~~~~~~~~\n" +
            "src/test/pkg/IntDefTest.java:80: Error: Must be one of: IntDefTest.TYPE_1, IntDefTest.TYPE_2 [WrongConstant]\n" +
            "        setTitle(\"\", \"type2\"); // ERROR\n" +
            "                     ~~~~~~~\n" +
            "src/test/pkg/IntDefTest.java:87: Error: Must be one of: IntDefTest.TYPE_1, IntDefTest.TYPE_2 [WrongConstant]\n" +
            "        setTitle(\"\", type); // ERROR\n" +
            "                     ~~~~\n" +
            "src/test/pkg/IntDefTest.java:92: Error: Must be one or more of: IntDefTest.STYLE_NORMAL, IntDefTest.STYLE_NO_TITLE, IntDefTest.STYLE_NO_FRAME, IntDefTest.STYLE_NO_INPUT [WrongConstant]\n" +
            "        setFlags(\"\", flag); // ERROR\n" +
            "                     ~~~~\n" +
            "src/test/pkg/IntDefTest.java:99: Error: Must be one of: View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL, View.LAYOUT_DIRECTION_INHERIT, View.LAYOUT_DIRECTION_LOCALE [WrongConstant]\n" +
            "        view.setLayoutDirection(View.TEXT_DIRECTION_LTR); // ERROR\n" +
            "                                ~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/IntDefTest.java:100: Error: Must be one of: View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL, View.LAYOUT_DIRECTION_INHERIT, View.LAYOUT_DIRECTION_LOCALE [WrongConstant]\n" +
            "        view.setLayoutDirection(0); // ERROR\n" +
            "                                ~\n" +
            "src/test/pkg/IntDefTest.java:101: Error: Flag not allowed here [WrongConstant]\n" +
            "        view.setLayoutDirection(View.LAYOUT_DIRECTION_LTR|View.LAYOUT_DIRECTION_RTL); // ERROR\n" +
            "                                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "19 errors, 0 warnings\n"
        lint().files(
            java(
                "" +
                    "package test.pkg;\n" +
                    "\n" +
                    "import android.content.Context;\n" +
                    "import androidx.annotation.IntDef;\n" +
                    "import androidx.annotation.StringDef;\n" +
                    "import android.view.View;\n" +
                    "\n" +
                    "import java.lang.annotation.Retention;\n" +
                    "import java.lang.annotation.RetentionPolicy;\n" +
                    "\n" +
                    "@SuppressWarnings(\"UnusedDeclaration\")\n" +
                    "public class IntDefTest {\n" +
                    "    @IntDef({STYLE_NORMAL, STYLE_NO_TITLE, STYLE_NO_FRAME, STYLE_NO_INPUT})\n" +
                    "    @Retention(RetentionPolicy.SOURCE)\n" +
                    "    private @interface DialogStyle {}\n" +
                    "\n" +
                    "    public static final int STYLE_NORMAL = 0;\n" +
                    "    public static final int STYLE_NO_TITLE = 1;\n" +
                    "    public static final int STYLE_NO_FRAME = 2;\n" +
                    "    public static final int STYLE_NO_INPUT = 3;\n" +
                    "    public static final int UNRELATED = 3;\n" +
                    "\n" +
                    "    public void setStyle(@DialogStyle int style, int theme) {\n" +
                    "    }\n" +
                    "\n" +
                    "    public void testIntDef(int arg) {\n" +
                    "        setStyle(STYLE_NORMAL, 0); // OK\n" +
                    "        setStyle(IntDefTest.STYLE_NORMAL, 0); // OK\n" +
                    "        setStyle(arg, 0); // OK (not sure)\n" +
                    "\n" +
                    "        setStyle(0, 0); // ERROR\n" +
                    "        setStyle(-1, 0); // ERROR\n" +
                    "        setStyle(UNRELATED, 0); // ERROR\n" +
                    "        setStyle(IntDefTest.UNRELATED, 0); // ERROR\n" +
                    "        setStyle(IntDefTest.STYLE_NORMAL|STYLE_NO_FRAME, 0); // ERROR: Not a flag\n" +
                    "        setStyle(~STYLE_NO_FRAME, 0); // ERROR: Not a flag\n" +
                    "    }\n" +
                    "    @IntDef(value = {STYLE_NORMAL, STYLE_NO_TITLE, STYLE_NO_FRAME, STYLE_NO_INPUT}, flag=true)\n" +
                    "    @Retention(RetentionPolicy.SOURCE)\n" +
                    "    private @interface DialogFlags {}\n" +
                    "\n" +
                    "    public void setFlags(Object first, @DialogFlags int flags) {\n" +
                    "    }\n" +
                    "\n" +
                    "    public void testFlags(int arg) {\n" +
                    "        setFlags(\"\", -1); // OK\n" +
                    "        setFlags(\"\", 0); // OK\n" +
                    "        setFlags(\"\", STYLE_NORMAL); // OK\n" +
                    "        setFlags(arg, 0); // OK (not sure)\n" +
                    "        setFlags(\"\", IntDefTest.STYLE_NORMAL); // OK\n" +
                    "        setFlags(\"\", STYLE_NORMAL|STYLE_NO_TITLE); // OK\n" +
                    "        setFlags(\"\", STYLE_NORMAL|STYLE_NO_TITLE|STYLE_NO_INPUT); // OK\n" +
                    "        setFlags(\"\", arg < 0 ? STYLE_NORMAL : STYLE_NO_TITLE); // OK\n" +
                    "\n" +
                    "        setFlags(\"\", UNRELATED); // ERROR\n" +
                    "        setFlags(\"\", UNRELATED|STYLE_NO_TITLE); // ERROR\n" +
                    "        setFlags(\"\", STYLE_NORMAL|STYLE_NO_TITLE|UNRELATED); // ERROR\n" +
                    "        setFlags(\"\", 1); // ERROR\n" +
                    "        setFlags(\"\", arg < 0 ? STYLE_NORMAL : UNRELATED); // ERROR\n" +
                    "        setFlags(\"\", arg < 0 ? UNRELATED : STYLE_NORMAL); // ERROR\n" +
                    "    }\n" +
                    "\n" +
                    "    public static final String TYPE_1 = \"type1\";\n" +
                    "    public static final String TYPE_2 = \"type2\";\n" +
                    "    public static final String UNRELATED_TYPE = \"other\";\n" +
                    "\n" +
                    "    @StringDef({TYPE_1, TYPE_2})\n" +
                    "    @Retention(RetentionPolicy.SOURCE)\n" +
                    "    private @interface DialogType {}\n" +
                    "\n" +
                    "    public void setTitle(String title, @DialogType String type) {\n" +
                    "    }\n" +
                    "\n" +
                    "    public void testStringDef(String typeArg) {\n" +
                    "        setTitle(\"\", TYPE_1); // OK\n" +
                    "        setTitle(\"\", TYPE_2); // OK\n" +
                    "        setTitle(\"\", null); // OK\n" +
                    "        setTitle(\"\", typeArg); // OK (unknown)\n" +
                    "        setTitle(\"\", UNRELATED_TYPE); // ERROR\n" +
                    "        setTitle(\"\", \"type2\"); // ERROR\n" +
                    "    }\n" +
                    "\n" +
                    "    public void testFlow() {\n" +
                    "        String type = TYPE_1;\n" +
                    "        setTitle(\"\", type); // OK\n" +
                    "        type = UNRELATED_TYPE;\n" +
                    "        setTitle(\"\", type); // ERROR\n" +
                    "        int flag = 0;\n" +
                    "        flag |= STYLE_NORMAL;\n" +
                    "        setFlags(\"\", flag); // OK\n" +
                    "        flag = UNRELATED;\n" +
                    "        setFlags(\"\", flag); // ERROR\n" +
                    "    }\n" +
                    "\n" +
                    "    public void testExternalAnnotations(View view, Context context) {\n" +
                    "        view.setLayoutDirection(View.LAYOUT_DIRECTION_LTR); // OK\n" +
                    "        context.getSystemService(Context.ALARM_SERVICE); // OK\n" +
                    "\n" +
                    "        view.setLayoutDirection(View.TEXT_DIRECTION_LTR); // ERROR\n" +
                    "        view.setLayoutDirection(0); // ERROR\n" +
                    "        view.setLayoutDirection(View.LAYOUT_DIRECTION_LTR|View.LAYOUT_DIRECTION_RTL); // ERROR\n" +
                    "        //context.getSystemService(TYPE_1); // ERROR\n" +
                    "    }\n" +
                    "}\n"
            ),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(expected)
    }

    fun testTypeDef37324044() {
        // Regression test for issue 37324044
        lint().files(
            java(
                "package test.pkg;\n" +
                    "\n" +
                    "import java.util.Calendar;\n" +
                    "\n" +
                    "public class IntDefTest {\n" +
                    "    public void test() {\n" +
                    "        Calendar.getInstance().get(Calendar.DAY_OF_MONTH);\n" +
                    "    }\n" +
                    "}\n"
            ),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun testCombinedIntDefAndIntRange() {
        val expected =
            "src/test/pkg/X.java:27: Error: Must be one of: X.LENGTH_INDEFINITE, X.LENGTH_SHORT, X.LENGTH_LONG [WrongConstant]\n" +
                "        setDuration(UNRELATED); /// OK within range\n" +
                "                    ~~~~~~~~~\n" +
                "src/test/pkg/X.java:28: Error: Must be one of: X.LENGTH_INDEFINITE, X.LENGTH_SHORT, X.LENGTH_LONG or value must be ≥ 10 (was -5) [WrongConstant]\n" +
                "        setDuration(-5); // ERROR (not right int def or value\n" +
                "                    ~~\n" +
                "src/test/pkg/X.java:29: Error: Must be one of: X.LENGTH_INDEFINITE, X.LENGTH_SHORT, X.LENGTH_LONG or value must be ≥ 10 (was 8) [WrongConstant]\n" +
                "        setDuration(8); // ERROR (not matching number range)\n" +
                "                    ~\n" +
                "3 errors, 0 warnings\n"
        lint().files(
            java(
                "src/test/pkg/X.java",
                "" +
                    "\n" +
                    "package test.pkg;\n" +
                    "\n" +
                    "import androidx.annotation.IntDef;\n" +
                    "import androidx.annotation.IntRange;\n" +
                    "\n" +
                    "import java.lang.annotation.Retention;\n" +
                    "import java.lang.annotation.RetentionPolicy;\n" +
                    "\n" +
                    "@SuppressWarnings({\"UnusedParameters\", \"unused\", \"SpellCheckingInspection\"})\n" +
                    "public class X {\n" +
                    "\n" +
                    "    public static final int UNRELATED = 500;\n" +
                    "\n" +
                    "    @IntDef({LENGTH_INDEFINITE, LENGTH_SHORT, LENGTH_LONG})\n" +
                    "    @IntRange(from = 10)\n" +
                    "    @Retention(RetentionPolicy.SOURCE)\n" +
                    "    public @interface Duration {}\n" +
                    "\n" +
                    "    public static final int LENGTH_INDEFINITE = -2;\n" +
                    "    public static final int LENGTH_SHORT = -1;\n" +
                    "    public static final int LENGTH_LONG = 0;\n" +
                    "    public void setDuration(@Duration int duration) {\n" +
                    "    }\n" +
                    "\n" +
                    "    public void test() {\n" +
                    "        setDuration(UNRELATED); /// OK within range\n" +
                    "        setDuration(-5); // ERROR (not right int def or value\n" +
                    "        setDuration(8); // ERROR (not matching number range)\n" +
                    "        setDuration(8000); // OK (@IntRange applies)\n" +
                    "        setDuration(LENGTH_INDEFINITE); // OK (@IntDef)\n" +
                    "        setDuration(LENGTH_LONG); // OK (@IntDef)\n" +
                    "        setDuration(LENGTH_SHORT); // OK (@IntDef)\n" +
                    "    }\n" +
                    "}\n"
            ),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(expected)
    }

    fun testMultipleProjects() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=182179
        // 182179: Lint gives erroneous @StringDef errors in androidTests
        val expected =
            "src/test/zpkg/SomeClassTest.java:10: Error: Must be one of: SomeClass.MY_CONSTANT [WrongConstant]\n" +
                "        SomeClass.doSomething(\"error\");\n" +
                "                              ~~~~~~~\n" +
                "1 errors, 0 warnings\n"
        lint().files(
            java(
                "src/test/pkg/SomeClass.java",
                "" +
                    "package test.pkg;\n" +
                    "\n" +
                    "import androidx.annotation.StringDef;\n" +
                    "import android.util.Log;\n" +
                    "\n" +
                    "import java.lang.annotation.Documented;\n" +
                    "import java.lang.annotation.Retention;\n" +
                    "import java.lang.annotation.RetentionPolicy;\n" +
                    "\n" +
                    "public class SomeClass {\n" +
                    "\n" +
                    "    public static final String MY_CONSTANT = \"foo\";\n" +
                    "\n" +
                    "    public static void doSomething(@MyTypeDef final String myString) {\n" +
                    "        Log.v(\"tag\", myString);\n" +
                    "    }\n" +
                    "\n" +
                    "\n" +
                    "    /**\n" +
                    "     * Defines the possible values for state type.\n" +
                    "     */\n" +
                    "    @StringDef({MY_CONSTANT})\n" +
                    "    @Documented\n" +
                    "    @Retention(RetentionPolicy.SOURCE)\n" +
                    "    public @interface MyTypeDef {\n" +
                    "\n" +
                    "    }\n" +
                    "}"
            ),
            // test.zpkg: alphabetically after test.pkg: We want to make sure
            // that the SomeClass source unit is disposed before we try to
            // process SomeClassTest and try to resolve its SomeClass.MY_CONSTANT
            // @IntDef reference
            java(
                "src/test/zpkg/SomeClassTest.java",
                "" +
                    "package test.zpkg;\n" +
                    "\n" +
                    "import test.pkg.SomeClass;\n" +
                    "import junit.framework.TestCase;\n" +
                    "\n" +
                    "public class SomeClassTest extends TestCase {\n" +
                    "\n" +
                    "    public void testDoSomething() {\n" +
                    "        SomeClass.doSomething(SomeClass.MY_CONSTANT);\n" +
                    "        SomeClass.doSomething(\"error\");\n" +
                    "    }\n" +
                    "}"
            ),
            // junit stub:
            java(
                """
                package junit.framework;
                public class TestCase {
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(expected)
    }

    /**
     * Test @IntDef when applied to multiple elements like arrays or
     * varargs.
     */
    fun testIntDefMultiple() {
        val expected =
            "src/test/pkg/IntDefMultiple.java:24: Error: Must be one of: IntDefMultiple.VALUE_A, IntDefMultiple.VALUE_B [WrongConstant]\n" +
                "        restrictedArray(/*Must be one of: X.VALUE_A, X.VALUE_B*/new int[]{VALUE_A, 0, VALUE_B}/**/); // ERROR;\n" +
                "                                                                                   ~\n" +
                "src/test/pkg/IntDefMultiple.java:26: Error: Must be one of: IntDefMultiple.VALUE_A, IntDefMultiple.VALUE_B [WrongConstant]\n" +
                "        restrictedArray(/*Must be one of: X.VALUE_A, X.VALUE_B*/INVALID_ARRAY/**/); // ERROR\n" +
                "                                                                ~~~~~~~~~~~~~\n" +
                "src/test/pkg/IntDefMultiple.java:27: Error: Must be one of: IntDefMultiple.VALUE_A, IntDefMultiple.VALUE_B [WrongConstant]\n" +
                "        restrictedArray(/*Must be one of: X.VALUE_A, X.VALUE_B*/INVALID_ARRAY2/**/); // ERROR\n" +
                "                                                                ~~~~~~~~~~~~~~\n" +
                "src/test/pkg/IntDefMultiple.java:31: Error: Must be one of: IntDefMultiple.VALUE_A, IntDefMultiple.VALUE_B [WrongConstant]\n" +
                "        restrictedEllipsis(VALUE_A, /*Must be one of: X.VALUE_A, X.VALUE_B*/0/**/, VALUE_B); // ERROR\n" +
                "                                                                            ~\n" +
                "src/test/pkg/IntDefMultiple.java:32: Error: Must be one of: IntDefMultiple.VALUE_A, IntDefMultiple.VALUE_B [WrongConstant]\n" +
                "        restrictedEllipsis(/*Must be one of: X.VALUE_A, X.VALUE_B*/0/**/); // ERROR\n" +
                "                                                                   ~\n" +
                "5 errors, 0 warnings\n"
        lint().files(
            java(
                "src/test/pkg/IntDefMultiple.java",
                "" +
                    "package test.pkg;\n" +
                    "import androidx.annotation.IntDef;\n" +
                    "\n" +
                    "public class IntDefMultiple {\n" +
                    "    private static final int VALUE_A = 0;\n" +
                    "    private static final int VALUE_B = 1;\n" +
                    "\n" +
                    "    private static final int[] VALID_ARRAY = {VALUE_A, VALUE_B};\n" +
                    "    private static final int[] INVALID_ARRAY = {VALUE_A, 0, VALUE_B};\n" +
                    "    private static final int[] INVALID_ARRAY2 = {10};\n" +
                    "\n" +
                    "    @IntDef({VALUE_A, VALUE_B})\n" +
                    "    public @interface MyIntDef {}\n" +
                    "\n" +
                    "    @MyIntDef\n" +
                    "    public int a = 0;\n" +
                    "\n" +
                    "    @MyIntDef\n" +
                    "    public int[] b;\n" +
                    "\n" +
                    "    public void testCall() {\n" +
                    "        restrictedArray(new int[]{VALUE_A}); // OK\n" +
                    "        restrictedArray(new int[]{VALUE_A, VALUE_B}); // OK\n" +
                    "        restrictedArray(/*Must be one of: X.VALUE_A, X.VALUE_B*/new int[]{VALUE_A, 0, VALUE_B}/**/); // ERROR;\n" +
                    "        restrictedArray(VALID_ARRAY); // OK\n" +
                    "        restrictedArray(/*Must be one of: X.VALUE_A, X.VALUE_B*/INVALID_ARRAY/**/); // ERROR\n" +
                    "        restrictedArray(/*Must be one of: X.VALUE_A, X.VALUE_B*/INVALID_ARRAY2/**/); // ERROR\n" +
                    "\n" +
                    "        restrictedEllipsis(VALUE_A); // OK\n" +
                    "        restrictedEllipsis(VALUE_A, VALUE_B); // OK\n" +
                    "        restrictedEllipsis(VALUE_A, /*Must be one of: X.VALUE_A, X.VALUE_B*/0/**/, VALUE_B); // ERROR\n" +
                    "        restrictedEllipsis(/*Must be one of: X.VALUE_A, X.VALUE_B*/0/**/); // ERROR\n" +
                    "        // Suppressed via older Android Studio inspection id:\n" +
                    "        //noinspection ResourceType\n" +
                    "        restrictedEllipsis(0); // SUPPRESSED\n" +
                    "    }\n" +
                    "\n" +
                    "    private void restrictedEllipsis(@MyIntDef int... test) {}\n" +
                    "\n" +
                    "    private void restrictedArray(@MyIntDef int[] test) {}\n" +
                    "}"
            ),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(expected)
    }

    fun testIntDefInBuilder() {
        // Ensure that we only check constants, not instance fields, when passing
        // fields as arguments to typedef parameters.
        lint().files(
            java(
                "src/test/pkg/Product.java",
                "" +
                    "package test.pkg;\n" +
                    "\n" +
                    "import androidx.annotation.IntDef;\n" +
                    "\n" +
                    "import java.lang.annotation.Retention;\n" +
                    "import java.lang.annotation.RetentionPolicy;\n" +
                    "\n" +
                    "public class Product {\n" +
                    "    @IntDef({\n" +
                    "         STATUS_AVAILABLE, STATUS_BACK_ORDER, STATUS_UNAVAILABLE\n" +
                    "    })\n" +
                    "    @Retention(RetentionPolicy.SOURCE)\n" +
                    "    public @interface Status {\n" +
                    "    }\n" +
                    "    public static final int STATUS_AVAILABLE = 1;\n" +
                    "    public static final int STATUS_BACK_ORDER = 2;\n" +
                    "    public static final int STATUS_UNAVAILABLE = 3;\n" +
                    "\n" +
                    "    @Status\n" +
                    "    private final int mStatus;\n" +
                    "    private final String mName;\n" +
                    "\n" +
                    "    private Product(String name, @Status int status) {\n" +
                    "        mName = name;\n" +
                    "        mStatus = status;\n" +
                    "    }\n" +
                    "    public static class Builder {\n" +
                    "        @Status\n" +
                    "        private int mStatus;\n" +
                    "        private final int mStatus2 = STATUS_AVAILABLE;\n" +
                    "        @Status static final int DEFAULT_STATUS = Product.STATUS_UNAVAILABLE;\n" +
                    "        private String mName;\n" +
                    "\n" +
                    "        public Builder(String name, @Status int status) {\n" +
                    "            mName = name;\n" +
                    "            mStatus = status;\n" +
                    "        }\n" +
                    "\n" +
                    "        public Builder setStatus(@Status int status) {\n" +
                    "            mStatus = status;\n" +
                    "            return this;\n" +
                    "        }\n" +
                    "\n" +
                    "        public Product build() {\n" +
                    "            return new Product(mName, mStatus);\n" +
                    "        }\n" +
                    "\n" +
                    "        public Product build2() {\n" +
                    "            return new Product(mName, mStatus2);\n" +
                    "        }\n" +
                    "\n" +
                    "        public static Product build3() {\n" +
                    "            return new Product(\"\", DEFAULT_STATUS);\n" +
                    "        }\n" +
                    "    }\n" +
                    "}\n"
            ),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun testWrongConstant() {
        // Regression test for scenario found to be inconsistent between PSI and UAST
        lint().files(
            java(
                """
                    package test.pkg;

                    import androidx.annotation.NonNull;

                    @SuppressWarnings({"ClassNameDiffersFromFileName","FieldCanBeLocal"})
                    public class ViewableDayInterval {
                        @CalendarDay
                        private int mDayCreatedFor;

                        public ViewableDayInterval(long startOffset, long duration, @NonNull @CalendarDay int... startDays) {
                            this(startDays[0], startOffset, duration, startDays);
                        }

                        public ViewableDayInterval(long start, @NonNull @WeekDay int... weekdays) {
                            this(weekdays[0], start, start, weekdays);
                        }

                        public ViewableDayInterval(long start, @NonNull @WeekDay int weekday) {
                            this(weekday, start, start, weekday);
                        }

                        public ViewableDayInterval(@CalendarDay int dayCreatedFor, long startOffset, long duration, @NonNull @CalendarDay int... startDays) {
                            mDayCreatedFor = dayCreatedFor;
                        }
                    }"""
            ).indented(),
            java(
                """
                    package test.pkg;

                    import androidx.annotation.IntDef;

                    import java.lang.annotation.Retention;
                    import java.lang.annotation.RetentionPolicy;
                    import java.util.Calendar;

                    @SuppressWarnings("ClassNameDiffersFromFileName")
                    @Retention(RetentionPolicy.SOURCE)
                    @IntDef({Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                            Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY})
                    public @interface CalendarDay {
                    }"""
            ).indented(),
            java(
                """
                    package test.pkg;

                    import androidx.annotation.IntDef;

                    import java.lang.annotation.Retention;
                    import java.lang.annotation.RetentionPolicy;
                    import java.util.Calendar;

                    @SuppressWarnings("ClassNameDiffersFromFileName")
                    @Retention(RetentionPolicy.SOURCE)
                    @IntDef({Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                            Calendar.THURSDAY, Calendar.FRIDAY})
                    public @interface WeekDay {
                    }"""
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun testIndirectTypedef() {
        // Regression test for b/36384014
        lint().files(
            java(
                "package test.pkg;\n" +
                    "\n" +
                    "import androidx.annotation.IntDef;\n" +
                    "\n" +
                    "public class Lifecycle {\n" +
                    "    public static final int ON_CREATE = 1;\n" +
                    "    public static final int ON_START = 2;\n" +
                    "    public static final int ON_RESUME = 3;\n" +
                    "    public static final int ON_PAUSE = 4;\n" +
                    "    public static final int ON_STOP = 5;\n" +
                    "    public static final int ON_DESTROY = 6;\n" +
                    "    public static final int ANY = 7;\n" +
                    "\n" +
                    "    @IntDef(value = {ON_CREATE, ON_START, ON_RESUME, ON_PAUSE, ON_STOP, ON_DESTROY, ANY},\n" +
                    "            flag = true)\n" +
                    "    public @interface Event {\n" +
                    "    }\n" +
                    "}"
            ),
            java(
                "" +
                    "package test.pkg;\n" +
                    "\n" +
                    "import java.lang.annotation.ElementType;\n" +
                    "import java.lang.annotation.Retention;\n" +
                    "import java.lang.annotation.RetentionPolicy;\n" +
                    "import java.lang.annotation.Target;\n" +
                    "\n" +
                    "@Retention(RetentionPolicy.RUNTIME)\n" +
                    "@Target(ElementType.METHOD)\n" +
                    "public @interface OnLifecycleEvent {\n" +
                    "    @Lifecycle.Event\n" +
                    "    int value();\n" +
                    "}\n"
            ),
            java(
                "" +
                    "package test.pkg;\n" +
                    "\n" +
                    "public interface Usage {\n" +
                    "    @OnLifecycleEvent(4494823) // this value is not valid\n" +
                    "    void addLocationListener();\n" +
                    "}\n"
            ),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            "" +
                "src/test/pkg/Usage.java:4: Error: Must be one or more of: Lifecycle.ON_CREATE, Lifecycle.ON_START, Lifecycle.ON_RESUME, Lifecycle.ON_PAUSE, Lifecycle.ON_STOP, Lifecycle.ON_DESTROY, Lifecycle.ANY [WrongConstant]\n" +
                "    @OnLifecycleEvent(4494823) // this value is not valid\n" +
                "                      ~~~~~~~\n" +
                "1 errors, 0 warnings\n"
        )
    }

    fun testCalendar() {
        // Regression test for
        // https://code.google.com/p/android/issues/detail?id=251256 and
        // http://youtrack.jetbrains.com/issue/IDEA-144891

        lint().files(
            java(
                "" +
                    "package test.pkg;\n" +
                    "\n" +
                    "import java.util.Calendar;\n" +
                    "\n" +
                    "public class CalendarTest {\n" +
                    "    public void test() {\n" +
                    "        Calendar now = Calendar.getInstance();\n" +
                    "        now.get(Calendar.DAY_OF_MONTH);\n" +
                    "        now.get(Calendar.HOUR_OF_DAY);\n" +
                    "        now.get(Calendar.MINUTE);\n" +
                    "        if (now.get(Calendar.MONTH) == Calendar.JANUARY) {\n" +
                    "        }\n" +
                    "        now.set(Calendar.HOUR_OF_DAY, 50);\n" +
                    "        now.set(2017, 3, 29);\n" +
                    "    }\n" +
                    "}\n"
            )
        ).run().expectClean()
    }

    fun testIntDef() {

        lint().files(
            java(
                "" +
                    "package test.pkg;\n" +
                    "import android.annotation.SuppressLint;\n" +
                    "import android.annotation.TargetApi;\n" +
                    "import android.app.Notification;\n" +
                    "import android.content.Context;\n" +
                    "import android.content.Intent;\n" +
                    "import android.content.ServiceConnection;\n" +
                    "import android.content.res.Resources;\n" +
                    "import android.os.Build;\n" +
                    "import androidx.annotation.DrawableRes;\n" +
                    "import android.view.View;\n" +
                    "\n" +
                    "import static android.content.Context.CONNECTIVITY_SERVICE;\n" +
                    "\n" +
                    "@SuppressWarnings(\"UnusedDeclaration\")\n" +
                    "public class X {\n" +
                    "\n" +
                    "    @TargetApi(Build.VERSION_CODES.KITKAT)\n" +
                    "    public void testStringDef(Context context, String unknown) {\n" +
                    "        Object ok1 = context.getSystemService(unknown);\n" +
                    "        Object ok2 = context.getSystemService(Context.CLIPBOARD_SERVICE);\n" +
                    "        Object ok3 = context.getSystemService(android.content.Context.WINDOW_SERVICE);\n" +
                    "        Object ok4 = context.getSystemService(CONNECTIVITY_SERVICE);\n" +
                    "    }\n" +
                    "\n" +
                    "    @SuppressLint(\"UseCheckPermission\")\n" +
                    "    @TargetApi(Build.VERSION_CODES.KITKAT)\n" +
                    "    public void testIntDef(Context context, int unknown, View view) {\n" +
                    "        view.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); // OK\n" +
                    "        view.setLayoutDirection(/*Must be one of: View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL, View.LAYOUT_DIRECTION_INHERIT, View.LAYOUT_DIRECTION_LOCALE*/View.TEXT_ALIGNMENT_TEXT_START/**/); // Error\n" +
                    "        view.setLayoutDirection(/*Flag not allowed here*/View.LAYOUT_DIRECTION_RTL | View.LAYOUT_DIRECTION_RTL/**/); // Error\n" +
                    "\n" +
                    "        // Regression test for http://b.android.com/197184\n" +
                    "        view.setLayoutDirection(View.LAYOUT_DIRECTION_RTL, View.LAYOUT_DIRECTION_LTR); // ERROR\n" +
                    "    }\n" +
                    "\n" +
                    "    @TargetApi(Build.VERSION_CODES.KITKAT)\n" +
                    "    public void testIntDefFlags(Context context, int unknown, Intent intent,\n" +
                    "                           ServiceConnection connection) {\n" +
                    "        // Flags\n" +
                    "        Object ok1 = context.bindService(intent, connection, 0);\n" +
                    "        Object ok2 = context.bindService(intent, connection, -1);\n" +
                    "        Object ok3 = context.bindService(intent, connection, Context.BIND_ABOVE_CLIENT);\n" +
                    "        Object ok4 = context.bindService(intent, connection, Context.BIND_ABOVE_CLIENT\n" +
                    "                | Context.BIND_AUTO_CREATE);\n" +
                    "        int flags1 = Context.BIND_ABOVE_CLIENT | Context.BIND_AUTO_CREATE;\n" +
                    "        Object ok5 = context.bindService(intent, connection, flags1);\n" +
                    "\n" +
                    "        Object error1 = context.bindService(intent, connection,\n" +
                    "                Context.BIND_ABOVE_CLIENT | /*Must be one or more of: Context.BIND_AUTO_CREATE, Context.BIND_DEBUG_UNBIND, Context.BIND_NOT_FOREGROUND, Context.BIND_ABOVE_CLIENT, Context.BIND_ALLOW_OOM_MANAGEMENT, Context.BIND_WAIVE_PRIORITY, Context.BIND_IMPORTANT, Context.BIND_ADJUST_WITH_ACTIVITY, Context.BIND_NOT_PERCEPTIBLE, Context.BIND_INCLUDE_CAPABILITIES*/Context.CONTEXT_IGNORE_SECURITY/**/);\n" +
                    "        int flags2 = Context.BIND_ABOVE_CLIENT | Context.CONTEXT_IGNORE_SECURITY;\n" +
                    "        Object error2 = context.bindService(intent, connection, /*Must be one or more of: Context.BIND_AUTO_CREATE, Context.BIND_DEBUG_UNBIND, Context.BIND_NOT_FOREGROUND, Context.BIND_ABOVE_CLIENT, Context.BIND_ALLOW_OOM_MANAGEMENT, Context.BIND_WAIVE_PRIORITY, Context.BIND_IMPORTANT, Context.BIND_ADJUST_WITH_ACTIVITY, Context.BIND_NOT_PERCEPTIBLE, Context.BIND_INCLUDE_CAPABILITIES*/flags2/**/);\n" +
                    "    }\n" +
                    "}\n"
            ),
            SUPPORT_ANNOTATIONS_JAR
        ).allowCompilationErrors().run().expectInlinedMessages()
    }

    fun testStringDefOnEquals() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=186598

        lint().files(
            java(
                "" +
                    "package test.pkg;\n" +
                    "\n" +
                    "import androidx.annotation.StringDef;\n" +
                    "\n" +
                    "import java.lang.annotation.Retention;\n" +
                    "\n" +
                    "@SuppressWarnings({\"unused\", \"StringEquality\"})\n" +
                    "public class X {\n" +
                    "    public static final String SUNDAY = \"a\";\n" +
                    "    public static final String MONDAY = \"b\";\n" +
                    "\n" +
                    "    @StringDef(value = {\n" +
                    "            SUNDAY,\n" +
                    "            MONDAY\n" +
                    "    })\n" +
                    "    @Retention(java.lang.annotation.RetentionPolicy.SOURCE)\n" +
                    "    public @interface Day {\n" +
                    "    }\n" +
                    "\n" +
                    "    @Day\n" +
                    "    public String getDay() {\n" +
                    "        return MONDAY;\n" +
                    "    }\n" +
                    "\n" +
                    "    public void test(Object object) {\n" +
                    "        boolean ok1 = this.getDay() == /*Must be one of: X.SUNDAY, X.MONDAY*/\"Any String\"/**/;\n" +
                    "        boolean ok2 = this.getDay().equals(MONDAY);\n" +
                    "        boolean wrong1 = this.getDay().equals(/*Must be one of: X.SUNDAY, X.MONDAY*/\"Any String\"/**/);\n" +
                    "    }\n" +
                    "}\n"
            ),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectInlinedMessages()
    }

    fun testAndingWithForeignMasks() {
        // False positive encountered in the support lib codebase (simplified):
        // Allow &'ing flags with masks without restrictions (necessary since in
        // many cases the masks are coming from unknown declarations (class fields
        // where we only have values, not references)
        lint().files(
            java(
                """
                    package test.pkg;

                    import androidx.annotation.IntDef;
                    import android.view.Gravity;
                    import android.view.View;

                    import java.lang.annotation.Retention;
                    import java.lang.annotation.RetentionPolicy;

                    @SuppressWarnings({"ClassNameDiffersFromFileName", "ConstantConditions", "RedundantIfStatement", "ConstantIfStatement"})
                    public class GravityTest {
                        @IntDef(value = {Gravity.LEFT, Gravity.RIGHT}, flag = true)
                        @Retention(RetentionPolicy.SOURCE)
                        private @interface EdgeGravity {}


                        public void usage(final View child) {
                            @EdgeGravity final int childGravity =
                                    getDrawerViewAbsoluteGravity(child) & Gravity.HORIZONTAL_GRAVITY_MASK;
                            if (true) {
                                throw new IllegalStateException("Child drawer has absolute gravity "
                                        + gravityToString(childGravity) + " but this tag already has a "
                                        + "drawer view along that edge");
                            }
                        }

                        int getDrawerViewAbsoluteGravity(View drawerView) {
                            return Gravity.LEFT; // Wrong
                        }

                        static String gravityToString(@EdgeGravity int gravity) {
                            if ((gravity & Gravity.LEFT) == Gravity.LEFT) {
                                return "LEFT";
                            }
                            if ((gravity & Gravity.RIGHT) == Gravity.RIGHT) {
                                return "RIGHT";
                            }
                            return Integer.toHexString(gravity);
                        }
                    }
                    """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun testVarIntDef() {
        // Regression test for b/37078720
        lint().files(
            java(
                """
                package test.pkg;

                import androidx.annotation.IntDef;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;

                @SuppressWarnings({"ClassNameDiffersFromFileName", "UnusedAssignment", "FieldCanBeLocal"})
                public class IntDefVarTest {
                    private static final int TREE_PATH_ONE = 1;
                    private static final int TREE_PATH_TWO = 2;
                    private static final int TREE_PATH_THREE = 3;

                    @IntDef(value = {
                            TREE_PATH_ONE,
                            TREE_PATH_TWO,
                            TREE_PATH_THREE
                    })
                    @Retention(RetentionPolicy.SOURCE)
                    private @interface Tree {
                    }

                    @Tree
                    private int mTreeField = TREE_PATH_ONE;

                    private void problem1() {
                        @Tree int treeInvalid = 12;
                        treeInvalid = 13;
                        // annotations for variables or fields
                        @Tree int treeValid = TREE_PATH_ONE;
                        problem2(mTreeField); // Falsely marked as an error. Lint does not track @IntDef annotations
                        // fields so it does not know the mTreeField is actually a @Tree
                    }

                    @Tree
                    private int mTreeField2 = 14;

                    private void problem2(@Tree int tree) {
                    }


                    @IntDef(value = {1, 2, 3})
                    @Retention(RetentionPolicy.SOURCE)
                    private @interface TestIntDef {
                    }

                    @TestIntDef
                    private int testVar = 4;
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            "" +
                "src/test/pkg/IntDefVarTest.java:26: Error: Must be one of: IntDefVarTest.TREE_PATH_ONE, IntDefVarTest.TREE_PATH_TWO, IntDefVarTest.TREE_PATH_THREE [WrongConstant]\n" +
                "        @Tree int treeInvalid = 12;\n" +
                "                                ~~\n" +
                "src/test/pkg/IntDefVarTest.java:27: Error: Must be one of: IntDefVarTest.TREE_PATH_ONE, IntDefVarTest.TREE_PATH_TWO, IntDefVarTest.TREE_PATH_THREE [WrongConstant]\n" +
                "        treeInvalid = 13;\n" +
                "        ~~~~~~~~~~~\n" +
                "src/test/pkg/IntDefVarTest.java:35: Error: Must be one of: IntDefVarTest.TREE_PATH_ONE, IntDefVarTest.TREE_PATH_TWO, IntDefVarTest.TREE_PATH_THREE [WrongConstant]\n" +
                "    private int mTreeField2 = 14;\n" +
                "                              ~~\n" +
                "src/test/pkg/IntDefVarTest.java:47: Error: Must be one of: 1, 2, 3 [WrongConstant]\n" +
                "    private int testVar = 4;\n" +
                "                          ~\n" +
                "4 errors, 0 warnings\n"
        )
    }

    // Temporarily disabled because PSQ does not seem to have tools/adt/idea
    //  9af9ae6ed2a4fe8d6a4a29726772568cb505b4ed applied. Hiding test for now to
    // unblock integrating bigger change; restore in separate CL.
    fun ignored_testCalendarGet() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=230099

        lint().files(
            java(
                "" +
                    "package test.pkg;\n" +
                    "import java.util.Calendar;\n" +
                    "\n" +
                    "@SuppressWarnings({\"unused\", \"StatementWithEmptyBody\"})\n" +
                    "public class X  {\n" +
                    "    private void check(Calendar lhsCal, Calendar rhsCal) {\n" +
                    "        if( lhsCal.get(Calendar.DAY_OF_YEAR) == rhsCal.get(Calendar.DAY_OF_YEAR)+1){\n" +
                    "        }\n" +
                    "        if( lhsCal.get(Calendar.DAY_OF_YEAR) == 200){\n" +
                    "        }\n" +
                    "    }\n" +
                    "}\n"
            ),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun testEnforceMethodReturnValueConstraints() {
        // Regression test for 69321287
        lint().files(
            java(
                """
                    package test.pkg;

                    import androidx.annotation.IntDef;

                    @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                    public class IntDefTest {
                        public void test() {
                            wantInt(100); // ERROR
                            wantInt(WrongType.NO); // ERROR
                            wantInt(giveRandomInt()); // ERROR
                            wantInt(giveWrongInt()); //ERROR
                            wantInt(giveWrongIntAnnotated()); //ERROR
                            wantInt(giveUnknownInt()); // OK (unknown)
                            wantInt(giveRightInt()); //OK
                        }

                        @IntDef({TestType.LOL})
                        public @interface TestType {
                            int LOL = 1;
                        }

                        @IntDef({WrongType.NO})
                        public @interface WrongType {
                            int NO = 2;
                        }

                        public void wantInt(@TestType int input) {}

                        public int giveRandomInt() {
                            return 100;
                        }

                        public int giveUnknownInt() {
                            return (int) (giveRandomInt() * System.currentTimeMillis());
                        }

                        public int giveWrongInt() {
                            return WrongType.NO;
                        }

                        public int giveRightInt() {
                            return TestType.LOL;
                        }

                        @WrongType public int giveWrongIntAnnotated() {
                            return WrongType.NO;
                        }
                    }
                    """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            "" +
                "src/test/pkg/IntDefTest.java:8: Error: Must be one of: TestType.LOL [WrongConstant]\n" +
                "        wantInt(100); // ERROR\n" +
                "                ~~~\n" +
                "src/test/pkg/IntDefTest.java:9: Error: Must be one of: TestType.LOL [WrongConstant]\n" +
                "        wantInt(WrongType.NO); // ERROR\n" +
                "                ~~~~~~~~~~~~\n" +
                "src/test/pkg/IntDefTest.java:10: Error: Must be one of: TestType.LOL [WrongConstant]\n" +
                "        wantInt(giveRandomInt()); // ERROR\n" +
                "                ~~~~~~~~~~~~~~~\n" +
                "src/test/pkg/IntDefTest.java:11: Error: Must be one of: TestType.LOL [WrongConstant]\n" +
                "        wantInt(giveWrongInt()); //ERROR\n" +
                "                ~~~~~~~~~~~~~~\n" +
                "src/test/pkg/IntDefTest.java:12: Error: Must be one of: TestType.LOL [WrongConstant]\n" +
                "        wantInt(giveWrongIntAnnotated()); //ERROR\n" +
                "                ~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "5 errors, 0 warnings"
        )
    }

    fun testEnforceMethodReturnValueConstraintsKotlin() {
        // Regression test for 69321287
        lint().files(
            kotlin(
                """
                    package test.pkg

                    @Suppress("UseExpressionBody")
                    class IntDefTest {
                        fun test() {
                            wantInt(100) // ERROR
                            wantInt(WrongType.NO) // ERROR
                            wantInt(giveRandomInt()) // ERROR
                            wantInt(giveWrongInt()) //ERROR
                            wantInt(giveWrongIntAnnotated()) //ERROR
                            wantInt(giveUnknownInt()) // OK (unknown)
                            wantInt(giveRightInt()) //OK
                        }

                        fun wantInt(@TestType input: Int) {}

                        fun giveRandomInt(): Int {
                            return 100
                        }

                        fun giveUnknownInt(): Int {
                            return (giveRandomInt() * System.currentTimeMillis()).toInt()
                        }

                        fun giveWrongInt(): Int {
                            return WrongType.NO
                        }

                        fun giveRightInt(): Int {
                            return TestType.LOL
                        }

                        @WrongType
                        fun giveWrongIntAnnotated(): Int {
                            return WrongType.NO
                        }
                    }
                """
            ).indented(),
            java(
                """
                    package test.pkg;

                    import androidx.annotation.IntDef;

                    @SuppressWarnings("ClassNameDiffersFromFileName")
                    @IntDef({WrongType.NO})
                    public @interface WrongType {
                        int NO = 2;
                    }
                    """
            ).indented(),
            java(
                """
                    package test.pkg;

                    import androidx.annotation.IntDef;

                    @SuppressWarnings("ClassNameDiffersFromFileName")
                    @IntDef({TestType.LOL})
                    public @interface TestType {
                        int LOL = 1;
                    }
                    """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            "" +
                "src/test/pkg/IntDefTest.kt:6: Error: Must be one of: TestType.LOL [WrongConstant]\n" +
                "        wantInt(100) // ERROR\n" +
                "                ~~~\n" +
                "src/test/pkg/IntDefTest.kt:7: Error: Must be one of: TestType.LOL [WrongConstant]\n" +
                "        wantInt(WrongType.NO) // ERROR\n" +
                "                ~~~~~~~~~~~~\n" +
                "src/test/pkg/IntDefTest.kt:8: Error: Must be one of: TestType.LOL [WrongConstant]\n" +
                "        wantInt(giveRandomInt()) // ERROR\n" +
                "                ~~~~~~~~~~~~~~~\n" +
                "src/test/pkg/IntDefTest.kt:9: Error: Must be one of: TestType.LOL [WrongConstant]\n" +
                "        wantInt(giveWrongInt()) //ERROR\n" +
                "                ~~~~~~~~~~~~~~\n" +
                "src/test/pkg/IntDefTest.kt:10: Error: Must be one of: TestType.LOL [WrongConstant]\n" +
                "        wantInt(giveWrongIntAnnotated()) //ERROR\n" +
                "                ~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "5 errors, 0 warnings"
        )
    }

    fun testStringDefInitialization() {
        // Regression test for https://issuetracker.google.com/72756166
        // 72756166: AGP 3.1-beta1 StringDef Lint Error
        lint().files(
            java(
                """
                    package test.pkg;

                    import androidx.annotation.StringDef;

                    import java.lang.annotation.Documented;
                    import java.lang.annotation.Retention;
                    import java.lang.annotation.RetentionPolicy;

                    @SuppressWarnings("ClassNameDiffersFromFileName")
                    public class StringDefTest {

                        @MyTypeDef
                        public static final String FOO = "foo";

                        @StringDef({FOO})
                        @Retention(RetentionPolicy.SOURCE)
                        @Documented
                        public @interface MyTypeDef {
                        }
                    }
                    """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun testOpenStringDef() {
        // Regression test for https://issuetracker.google.com/72756166
        // 117529548: MediaMetadataCompat.Builder does not support custom keys
        lint().files(
            java(
                """
                package test.pkg;

                import androidx.annotation.StringDef;

                import java.lang.annotation.Documented;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;

                @SuppressWarnings("ClassNameDiffersFromFileName")
                public class StringDefTest2 {

                    public void method(@MyTypeDef String param) {
                    }

                    public void test() {
                        method(FOO); // OK
                        method("bar"); // OK
                    }

                    @StringDef(value = {FOO}, open = true)
                    @Retention(RetentionPolicy.SOURCE)
                    @Documented
                    public @interface MyTypeDef {
                    }
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun testOpenStringDef2() {
        // 117529548: MediaMetadataCompat.Builder does not support custom keys
        lint().files(
            java(
                """
                package test.pkg;

                import android.support.v4.media.MediaMetadataCompat;

                @SuppressWarnings("ClassNameDiffersFromFileName")
                public class MediaBuilderTest {
                    public void test() {
                        MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder();
                        builder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, "something"); // OK
                        builder.putString("custom-key", "something"); // OK
                        builder.putLong("custom-key", 0L); // OK
                    }
                }

                """
            ).indented(),
            java(
                """
                package android.support.v4.media;
                import androidx.annotation.StringDef;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;

                @SuppressWarnings("ClassNameDiffersFromFileName")
                public final class MediaMetadataCompat {
                    public static final String METADATA_KEY_TITLE = "android.media.metadata.TITLE";
                    public static class Builder {
                        public void putString(@TextKey String key, String value) {
                        }
                        public void putLong(@TextKey String key, long value) {
                        }
                    }

                    @StringDef({METADATA_KEY_TITLE})
                    @Retention(RetentionPolicy.SOURCE)
                    public @interface TextKey {}

                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun test75993782() {
        // Regression test for https://issuetracker.google.com/75993782
        // Ensure that we handle finding typedef constants defined in Kotlin
        lint().files(
            kotlin(
                """
                package test.pkg

                import androidx.annotation.IntDef

                interface Foo {
                    fun bar(@DetailInfoTab tab: Int = CONST_1)

                    @IntDef(CONST_1, CONST_2, CONST_3)
                    @Retention(AnnotationRetention.SOURCE)
                    annotation class DetailInfoTab

                    companion object {
                        const val CONST_1 = -1
                        const val CONST_2 = 2
                        const val CONST_3 = 0

                        fun foobar(foo: Foo) {
                            foo.bar(CONST_1)
                        }
                    }
                }
                """
            ).indented(),

            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun test119753493() {
        // Regression test for
        // 119753493: False positive for WrongConstant after AndroidX Migration
        lint().files(
            java(
                """
                package test.pkg;

                import android.content.Context;

                import androidx.recyclerview.widget.LinearLayoutManager;

                public class WrongConstantTest {
                    static class MyLayoutManager extends LinearLayoutManager {

                        public MyLayoutManager(Context context) {
                            super(context);
                        }

                        public boolean isVertical() {
                            return getOrientation() == VERTICAL;
                        }
                    }
                }
                """
            ).indented(),

            // Binary version of class file. To reproduce
            // this bug we need to use bytecode, not source resolution
            // (since we want to verify what happens with field declarations
            // where the initializer is not present.)
            bytecode(
                "libs/recyclerview.jar",
                java(
                    """
                package androidx.recyclerview.widget;

                import android.content.Context;

                public class LinearLayoutManager {
                    // Simulate classfile presence of these constants, where we can't
                    // look at the right hand side (initialization)
                    public static final int HORIZONTAL = RecyclerView.HORIZONTAL;
                    public static final int VERTICAL = RecyclerView.VERTICAL;

                    public LinearLayoutManager(Context context) {
                    }

                    @RecyclerView.Orientation
                    int mOrientation = RecyclerView.DEFAULT_ORIENTATION;

                    @RecyclerView.Orientation
                    public int getOrientation() {
                        return mOrientation;
                    }
                }
                """
                ).indented(),
                "androidx/recyclerview/widget/LinearLayoutManager.class:" +
                    "H4sIAAAAAAAAAH1Qy0rDQBQ907SNTdOH1kfrY+Gu7cK4c6EIpSgWYgu1ZOFu" +
                    "mg5lSjqBOH39kxtBEVz4AX6UeBOKKIibe+Yc5txzOR+fb+8AznBgYsdCBrs5" +
                    "pLFnomqixmDd9Pqd+1530HIZWIeh0A7Vg+ZKezyYCYOsYNjwrvqDTrvlxpwx" +
                    "2NNeJIXSXMtQMWQvpJL6kuGw7nI1ikI5cvxQafrhtGNc6vOGx5BuhyPBUHKl" +
                    "Et3ZdCiiAR8GpBTHQv/aaNQbdIt1F84iX1zL+E81dvHI5atwpm+54mMRnUz4" +
                    "nDM01qFLJxL+yg9ENJdi4SzkiPY6/bXmkWajgH0bNkyG039df6QxlOM8J+Bq" +
                    "7PSGE+Hr5FIPx1RphqoxUEMWZtwYsRRyxK0fPE+TspM3VY0izRKxJvEUodV8" +
                    "RaqZfoHxlPRepllEXHqeEoq0zyZ1E1tr3xEhI8w0n2E8fluyiVihWUmitr8A" +
                    "fb+LPAUCAAA="
            ),
            bytecode(
                "libs/recyclerview.jar",
                java(
                    """
                package androidx.recyclerview.widget;

                import android.widget.LinearLayout;

                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;

                import androidx.annotation.IntDef;
                import androidx.annotation.RestrictTo;

                import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;

                public class RecyclerView {
                    /** @hide */
                    @RestrictTo(LIBRARY_GROUP)
                    @IntDef({HORIZONTAL, VERTICAL})
                    @Retention(RetentionPolicy.SOURCE)
                    public @interface Orientation {}

                    public static final int HORIZONTAL = LinearLayout.HORIZONTAL;
                    public static final int VERTICAL = LinearLayout.VERTICAL;

                    static final int DEFAULT_ORIENTATION = VERTICAL;
                }
                """
                ).indented(),
                "androidx/recyclerview/widget/RecyclerView.class:" +
                    "H4sIAAAAAAAAAI2QzU7CQBSFz/BXKCAg/oDGhYkx4sKujBtjQhBik9omWFm4" +
                    "MUOZkCF1mrQF5LFcmbjwAXwo422DCe7c3Lnnzpxvbs7X98cngCscatjTsK8j" +
                    "h5aGtoYDhrITSqFiHstAMVRMpUTY83kUiYhBv3OG5pNju12LgZkM1V6gopir" +
                    "eMT9ucgSFQzFUX/omr2ulWjG0LztD7qPlvtM5j55XdOxGQrXUsn4hiF71hkx" +
                    "5HrBRDDULKmEPX8Zi9DlY58m+kMwDz0xkIloDIW38nwRjqRYXsz4gtPGtoji" +
                    "e5FYaMVDriZhICfGUk6mIjYSHg8tvgrmMUNnfftqhGvQgkC/bzfhFdRQZ6gn" +
                    "fxg+V1PDGc+ER4zLfzNONrLEMTKUM5BFG0WUKCedVAZl0pUNXUULW380RZ0s" +
                    "Q7VBk6NUA/nzd2Tf0sC3qRbSYZXwzdS2g106czTNQ6NOT7sMCqelH1wLg3sA" +
                    "AgAA",
                "androidx/recyclerview/widget/RecyclerView\$Orientation.class:" +
                    "H4sIAAAAAAAAAJVQW0sCQRT+xrL1Wl66mUE9mBFR+xQ9+JJJF0FS1hSih1jX" +
                    "QUa2GdkdNf9aD/2AflR0FMkEIRs4cy7zne+c+T6/3j8AXGLfwKaBbQM7DJG6" +
                    "6nsOvxUuZ0ha3Bk5Lveagg/Pu/bAZshYfanFK28KX7RcXpRSaVsLJX2Gw8oY" +
                    "Y7q27Jj2z4Npcc3lOCowBAe22yfq/B/QmnKFM6KGtXq1YZVuGLLTwWU5WDD6" +
                    "uGLLtqdE2/T7vZ7y9Dyrrz3h6EdVMJChJeqO6tESsbKU3Cu5tu9z4jhbjiM3" +
                    "6abV4pXytVW0nl7urGqjxhB6IMy98rWBPYaLKdmb6U1VHJCK5lC0O1ybv5XN" +
                    "VT1Bv57MYYjOZYmZTNVWlzua4WChcjM1DGQZTv/xFYaTpXdlOFqKOc/AECBb" +
                    "wfgEsUp3gPwa+TjVDbIQRxgRRKmUoDQ2Lj2DccqSSE0a0tggn6Iojl2sX2UI" +
                    "k8ZWPvwNltmVBL4CAAA="
            ),
            jar(
                "annotations.zip",
                xml(
                    "androidx/recyclerview/widget/annotations.xml",
                    "" +
                        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<root>\n" +
                        "  <item name=\"androidx.recyclerview.widget.LinearLayoutManager int getOrientation()\">\n" +
                        "    <annotation name=\"androidx.annotation.IntDef\">\n" +
                        "      <val name=\"value\" val=\"{androidx.recyclerview.widget.RecyclerView.HORIZONTAL, androidx.recyclerview.widget.RecyclerView.VERTICAL}\" />\n" +
                        "    </annotation>\n" +
                        "  </item>\n" +
                        "  <item name=\"androidx.recyclerview.widget.LinearLayoutManager mOrientation\">\n" +
                        "    <annotation name=\"androidx.annotation.IntDef\">\n" +
                        "      <val name=\"value\" val=\"{androidx.recyclerview.widget.RecyclerView.HORIZONTAL, androidx.recyclerview.widget.RecyclerView.VERTICAL}\" />\n" +
                        "    </annotation>\n" +
                        "  </item>\n" +
                        "  <item name=\"androidx.recyclerview.widget.LinearLayoutManager void setOrientation(int) 0\">\n" +
                        "    <annotation name=\"androidx.annotation.IntDef\">\n" +
                        "      <val name=\"value\" val=\"{androidx.recyclerview.widget.RecyclerView.HORIZONTAL, androidx.recyclerview.widget.RecyclerView.VERTICAL}\" />\n" +
                        "    </annotation>\n" +
                        "  </item>\n" +
                        "</root>\n"
                )
            ),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun testReturnWithinLambda() {
        // Regression test for https://issuetracker.google.com/140626689
        lint().files(
            kotlin(
                """
                @file:Suppress("UNUSED_PARAMETER")
                package test.pkg
                import android.app.Service
                import android.content.Intent
                import android.os.Parcelable
                abstract class UpdateService : Service() {
                    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
                        val config = intent.getParcelableExtra<UpdateConfig>(KEY_CONFIG)
                        if (config == null) {
                            Timber.warn { "No config present in intent." }
                            return START_NOT_STICKY
                        }
                        return START_REDELIVER_INTENT
                    }
                }
                // Stubs
                abstract class UpdateConfig: Parcelable
                const val KEY_CONFIG = "config"
                class Timber {
                    companion object {
                        fun isLoggable(priority: Int, throwable: Throwable? = null): Boolean = false
                        fun rawLog(priority: Int, throwable: Throwable? = null, throwable2: Throwable? = null, message: String) {
                        }
                        inline fun warn(throwable: Throwable? = null, message: () -> String) {
                            log(0, throwable, message)
                        }
                        inline fun log(priority: Int, throwable: Throwable? = null, message: () -> String) {
                            if (isLoggable(priority, null)) {
                                rawLog(priority, null, throwable, message())
                            }
                        }
                    }
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        )
            .checkUInjectionHost(false)
            .run().expectClean()
    }

    fun test80166964() {
        // 80166964: IntDef annotation for variable not working anymore
        lint().files(
            java(
                """
                package test.pkg.myapplication;

                import androidx.annotation.IntDef;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;

                @SuppressWarnings({"WeakerAccess", "unused", "ClassNameDiffersFromFileName"})
                public class IntDefTest {

                    public static final int LINE = 0;
                    public static final int CORNER = 1;

                    @IntDef({LINE, CORNER})
                    @Retention(RetentionPolicy.SOURCE)
                    public @interface ShapeTypes {}

                    @ShapeTypes public int shapeType;

                    public void test(IntDefTest myClassObj) {
                        shapeType = 99;
                        myClassObj.shapeType = 99;
                    }
                }
                """
            ).indented(),

            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            """
            src/test/pkg/myapplication/IntDefTest.java:20: Error: Must be one of: IntDefTest.LINE, IntDefTest.CORNER [WrongConstant]
                    shapeType = 99;
                                ~~
            src/test/pkg/myapplication/IntDefTest.java:21: Error: Must be one of: IntDefTest.LINE, IntDefTest.CORNER [WrongConstant]
                    myClassObj.shapeType = 99;
                                           ~~
            2 errors, 0 warnings
            """
        )
    }

    fun testZeroAlias() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.app.PendingIntent;
                import android.content.Context;
                import android.content.Intent;

                public class Test {
                    public static final int UNRELATED = 0;
                    public void test(Intent intent, Context context) {
                        PendingIntent.getActivity(context, 0, intent,
                                PendingIntent.FLAG_IMMUTABLE |
                                Intent.FILL_IN_SELECTOR |
                                PendingIntent.FLAG_MUTABLE |
                                Test.UNRELATED, null);
                    }
                }
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/Test.java:14: Error: Must be one or more of: PendingIntent.FLAG_ONE_SHOT, PendingIntent.FLAG_NO_CREATE, PendingIntent.FLAG_CANCEL_CURRENT, PendingIntent.FLAG_UPDATE_CURRENT, PendingIntent.FLAG_IMMUTABLE, Intent.FILL_IN_ACTION, Intent.FILL_IN_DATA, Intent.FILL_IN_CATEGORIES, Intent.FILL_IN_COMPONENT, Intent.FILL_IN_PACKAGE, Intent.FILL_IN_SOURCE_BOUNDS, Intent.FILL_IN_SELECTOR, Intent.FILL_IN_CLIP_DATA [WrongConstant]
                            Test.UNRELATED, null);
                            ~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        )
    }

    fun test73783847() {
        lint().files(
            java(
                """
                package com.example.android.linttest;

                import androidx.annotation.StringDef;

                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;

                @Retention(RetentionPolicy.SOURCE)
                @StringDef(value = {
                    FragmentNames.HOME
                })
                public @interface FragmentName {}
                """
            ).indented(),
            java(
                """
                package com.example.android.linttest;

                public class FragmentNames {
                    public static final String HOME = "HOME";
                }
                """
            ).indented(),
            java(
                """
                package com.example.android.linttest;

                public class FragmentUtils {

                    public static String getSomeTextFromOtherClass() {
                        return "some text";
                    }
                }
                """
            ).indented(),
            java(
                """
                package com.example.android.linttest;

                import android.app.Activity;
                import android.os.Bundle;
                import android.widget.Toast;

                public class MainActivity extends Activity {

                    @Override
                    protected void onResume() {
                        super.onResume();

                        toastFragmentNameAndText(FragmentNames.HOME, getSomeTextFromThisClass()); // OK
                        toastFragmentNameAndText(FragmentNames.HOME, FragmentUtils.getSomeTextFromOtherClass()); // OK
                        toastFragmentNameAndText(getSomeTextFromThisClass(), FragmentNames.HOME); // ERROR
                        toastFragmentNameAndText(FragmentUtils.getSomeTextFromOtherClass(), FragmentNames.HOME); // ERROR
                    }

                    private void toastFragmentNameAndText(@FragmentName String fragmentTag, String text) {
                        Toast.makeText(this, fragmentTag, Toast.LENGTH_LONG).show();
                        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
                    }

                    public static String getSomeTextFromThisClass() {
                        return "some text";
                    }
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            """
            src/com/example/android/linttest/MainActivity.java:15: Error: Must be one of: FragmentNames.HOME [WrongConstant]
                    toastFragmentNameAndText(getSomeTextFromThisClass(), FragmentNames.HOME); // ERROR
                                             ~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/com/example/android/linttest/MainActivity.java:16: Error: Must be one of: FragmentNames.HOME [WrongConstant]
                    toastFragmentNameAndText(FragmentUtils.getSomeTextFromOtherClass(), FragmentNames.HOME); // ERROR
                                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            2 errors, 0 warnings
            """
        )
    }
}
