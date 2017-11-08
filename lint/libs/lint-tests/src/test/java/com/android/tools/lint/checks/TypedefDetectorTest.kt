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
                java("" +
                        "package test.pkg;\n" +
                        "\n" +
                        "import android.content.Context;\n" +
                        "import android.support.annotation.IntDef;\n" +
                        "import android.support.annotation.StringDef;\n" +
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
                        "}\n"),
                SUPPORT_ANNOTATIONS_CLASS_PATH,
                SUPPORT_ANNOTATIONS_JAR)
                .run()
                .expect(expected)
    }

    fun testTypeDef37324044() {
        // Regression test for issue 37324044
        lint().files(
                java("package test.pkg;\n" +
                        "\n" +
                        "import java.util.Calendar;\n" +
                        "\n" +
                        "public class IntDefTest {\n" +
                        "    public void test() {\n" +
                        "        Calendar.getInstance().get(Calendar.DAY_OF_MONTH);\n" +
                        "    }\n" +
                        "}\n"),
                SUPPORT_ANNOTATIONS_CLASS_PATH,
                SUPPORT_ANNOTATIONS_JAR)
                .run()
                .expectClean()
    }

    fun testCombinedIntDefAndIntRange() {
        val expected = "src/test/pkg/X.java:27: Error: Must be one of: X.LENGTH_INDEFINITE, X.LENGTH_SHORT, X.LENGTH_LONG [WrongConstant]\n" +
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
                java("src/test/pkg/X.java", ""
                        + "\n"
                        + "package test.pkg;\n"
                        + "\n"
                        + "import android.support.annotation.IntDef;\n"
                        + "import android.support.annotation.IntRange;\n"
                        + "\n"
                        + "import java.lang.annotation.Retention;\n"
                        + "import java.lang.annotation.RetentionPolicy;\n"
                        + "\n"
                        +
                        "@SuppressWarnings({\"UnusedParameters\", \"unused\", \"SpellCheckingInspection\"})\n"
                        + "public class X {\n"
                        + "\n"
                        + "    public static final int UNRELATED = 500;\n"
                        + "\n"
                        + "    @IntDef({LENGTH_INDEFINITE, LENGTH_SHORT, LENGTH_LONG})\n"
                        + "    @IntRange(from = 10)\n"
                        + "    @Retention(RetentionPolicy.SOURCE)\n"
                        + "    public @interface Duration {}\n"
                        + "\n"
                        + "    public static final int LENGTH_INDEFINITE = -2;\n"
                        + "    public static final int LENGTH_SHORT = -1;\n"
                        + "    public static final int LENGTH_LONG = 0;\n"
                        + "    public void setDuration(@Duration int duration) {\n"
                        + "    }\n"
                        + "\n"
                        + "    public void test() {\n"
                        + "        setDuration(UNRELATED); /// OK within range\n"
                        + "        setDuration(-5); // ERROR (not right int def or value\n"
                        + "        setDuration(8); // ERROR (not matching number range)\n"
                        + "        setDuration(8000); // OK (@IntRange applies)\n"
                        + "        setDuration(LENGTH_INDEFINITE); // OK (@IntDef)\n"
                        + "        setDuration(LENGTH_LONG); // OK (@IntDef)\n"
                        + "        setDuration(LENGTH_SHORT); // OK (@IntDef)\n"
                        + "    }\n"
                        + "}\n"),
                SUPPORT_ANNOTATIONS_CLASS_PATH,
                SUPPORT_ANNOTATIONS_JAR)
                .run()
                .expect(expected)
    }

    fun testMultipleProjects() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=182179
        // 182179: Lint gives erroneous @StringDef errors in androidTests
        val expected = "src/test/zpkg/SomeClassTest.java:10: Error: Must be one of: SomeClass.MY_CONSTANT [WrongConstant]\n" +
                "        SomeClass.doSomething(\"error\");\n" +
                "                              ~~~~~~~\n" +
                "1 errors, 0 warnings\n"
        lint().files(
                java("src/test/pkg/SomeClass.java", ""
                        + "package test.pkg;\n"
                        + "\n"
                        + "import android.support.annotation.StringDef;\n"
                        + "import android.util.Log;\n"
                        + "\n"
                        + "import java.lang.annotation.Documented;\n"
                        + "import java.lang.annotation.Retention;\n"
                        + "import java.lang.annotation.RetentionPolicy;\n"
                        + "\n"
                        + "public class SomeClass {\n"
                        + "\n"
                        + "    public static final String MY_CONSTANT = \"foo\";\n"
                        + "\n"
                        + "    public static void doSomething(@MyTypeDef final String myString) {\n"
                        + "        Log.v(\"tag\", myString);\n"
                        + "    }\n"
                        + "\n"
                        + "\n"
                        + "    /**\n"
                        + "     * Defines the possible values for state type.\n"
                        + "     */\n"
                        + "    @StringDef({MY_CONSTANT})\n"
                        + "    @Documented\n"
                        + "    @Retention(RetentionPolicy.SOURCE)\n"
                        + "    public @interface MyTypeDef {\n"
                        + "\n"
                        + "    }\n"
                        + "}"),
                // test.zpkg: alphabetically after test.pkg: We want to make sure
                // that the SomeClass source unit is disposed before we try to
                // process SomeClassTest and try to resolve its SomeClass.MY_CONSTANT
                // @IntDef reference
                java("src/test/zpkg/SomeClassTest.java", ""
                        + "package test.zpkg;\n"
                        + "\n"
                        + "import test.pkg.SomeClass;\n"
                        + "import junit.framework.TestCase;\n"
                        + "\n"
                        + "public class SomeClassTest extends TestCase {\n"
                        + "\n"
                        + "    public void testDoSomething() {\n"
                        + "        SomeClass.doSomething(SomeClass.MY_CONSTANT);\n"
                        + "        SomeClass.doSomething(\"error\");\n"
                        + "    }\n"
                        + "}"),
                SUPPORT_ANNOTATIONS_CLASS_PATH,
                SUPPORT_ANNOTATIONS_JAR)
                .run()
                .expect(expected)
    }

    /**
     * Test @IntDef when applied to multiple elements like arrays or varargs.
     */
    fun testIntDefMultiple() {
        val expected = "src/test/pkg/IntDefMultiple.java:24: Error: Must be one of: IntDefMultiple.VALUE_A, IntDefMultiple.VALUE_B [WrongConstant]\n" +
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
                java("src/test/pkg/IntDefMultiple.java", ""
                        + "package test.pkg;\n"
                        + "import android.support.annotation.IntDef;\n"
                        + "\n"
                        + "public class IntDefMultiple {\n"
                        + "    private static final int VALUE_A = 0;\n"
                        + "    private static final int VALUE_B = 1;\n"
                        + "\n"
                        + "    private static final int[] VALID_ARRAY = {VALUE_A, VALUE_B};\n"
                        + "    private static final int[] INVALID_ARRAY = {VALUE_A, 0, VALUE_B};\n"
                        + "    private static final int[] INVALID_ARRAY2 = {10};\n"
                        + "\n"
                        + "    @IntDef({VALUE_A, VALUE_B})\n"
                        + "    public @interface MyIntDef {}\n"
                        + "\n"
                        + "    @MyIntDef\n"
                        + "    public int a = 0;\n"
                        + "\n"
                        + "    @MyIntDef\n"
                        + "    public int[] b;\n"
                        + "\n"
                        + "    public void testCall() {\n"
                        + "        restrictedArray(new int[]{VALUE_A}); // OK\n"
                        + "        restrictedArray(new int[]{VALUE_A, VALUE_B}); // OK\n"
                        +
                        "        restrictedArray(/*Must be one of: X.VALUE_A, X.VALUE_B*/new int[]{VALUE_A, 0, VALUE_B}/**/); // ERROR;\n"
                        + "        restrictedArray(VALID_ARRAY); // OK\n"
                        +
                        "        restrictedArray(/*Must be one of: X.VALUE_A, X.VALUE_B*/INVALID_ARRAY/**/); // ERROR\n"
                        +
                        "        restrictedArray(/*Must be one of: X.VALUE_A, X.VALUE_B*/INVALID_ARRAY2/**/); // ERROR\n"
                        + "\n"
                        + "        restrictedEllipsis(VALUE_A); // OK\n"
                        + "        restrictedEllipsis(VALUE_A, VALUE_B); // OK\n"
                        +
                        "        restrictedEllipsis(VALUE_A, /*Must be one of: X.VALUE_A, X.VALUE_B*/0/**/, VALUE_B); // ERROR\n"
                        +
                        "        restrictedEllipsis(/*Must be one of: X.VALUE_A, X.VALUE_B*/0/**/); // ERROR\n"
                        + "        // Suppressed via older Android Studio inspection id:\n"
                        + "        //noinspection ResourceType\n"
                        + "        restrictedEllipsis(0); // SUPPRESSED\n"
                        + "    }\n"
                        + "\n"
                        + "    private void restrictedEllipsis(@MyIntDef int... test) {}\n"
                        + "\n"
                        + "    private void restrictedArray(@MyIntDef int[] test) {}\n"
                        + "}"),
                SUPPORT_ANNOTATIONS_CLASS_PATH,
                SUPPORT_ANNOTATIONS_JAR)
                .run()
                .expect(expected)
    }

    fun testIntDefInBuilder() {
        // Ensure that we only check constants, not instance fields, when passing
        // fields as arguments to typedef parameters.
        lint().files(
                java("src/test/pkg/Product.java", ""
                        + "package test.pkg;\n"
                        + "\n"
                        + "import android.support.annotation.IntDef;\n"
                        + "\n"
                        + "import java.lang.annotation.Retention;\n"
                        + "import java.lang.annotation.RetentionPolicy;\n"
                        + "\n"
                        + "public class Product {\n"
                        + "    @IntDef({\n"
                        + "         STATUS_AVAILABLE, STATUS_BACK_ORDER, STATUS_UNAVAILABLE\n"
                        + "    })\n"
                        + "    @Retention(RetentionPolicy.SOURCE)\n"
                        + "    public @interface Status {\n"
                        + "    }\n"
                        + "    public static final int STATUS_AVAILABLE = 1;\n"
                        + "    public static final int STATUS_BACK_ORDER = 2;\n"
                        + "    public static final int STATUS_UNAVAILABLE = 3;\n"
                        + "\n"
                        + "    @Status\n"
                        + "    private final int mStatus;\n"
                        + "    private final String mName;\n"
                        + "\n"
                        + "    private Product(String name, @Status int status) {\n"
                        + "        mName = name;\n"
                        + "        mStatus = status;\n"
                        + "    }\n"
                        + "    public static class Builder {\n"
                        + "        @Status\n"
                        + "        private int mStatus;\n"
                        + "        private final int mStatus2 = STATUS_AVAILABLE;\n"
                        +
                        "        @Status static final int DEFAULT_STATUS = Product.STATUS_UNAVAILABLE;\n"
                        + "        private String mName;\n"
                        + "\n"
                        + "        public Builder(String name, @Status int status) {\n"
                        + "            mName = name;\n"
                        + "            mStatus = status;\n"
                        + "        }\n"
                        + "\n"
                        + "        public Builder setStatus(@Status int status) {\n"
                        + "            mStatus = status;\n"
                        + "            return this;\n"
                        + "        }\n"
                        + "\n"
                        + "        public Product build() {\n"
                        + "            return new Product(mName, mStatus);\n"
                        + "        }\n"
                        + "\n"
                        + "        public Product build2() {\n"
                        + "            return new Product(mName, mStatus2);\n"
                        + "        }\n"
                        + "\n"
                        + "        public static Product build3() {\n"
                        + "            return new Product(\"\", DEFAULT_STATUS);\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n"),
                SUPPORT_ANNOTATIONS_CLASS_PATH,
                SUPPORT_ANNOTATIONS_JAR)
                .run()
                .expectClean()
    }

    fun testWrongConstant() {
        // Regression test for scenario found to be inconsistent between PSI and UAST
        lint().files(
                java("""
                    package test.pkg;

                    import android.support.annotation.NonNull;

                    @SuppressWarnings("ClassNameDiffersFromFileName")
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
                    }""").indented(),
                java("""
                    package test.pkg;

                    import android.support.annotation.IntDef;

                    import java.lang.annotation.Retention;
                    import java.lang.annotation.RetentionPolicy;
                    import java.util.Calendar;

                    @SuppressWarnings("ClassNameDiffersFromFileName")
                    @Retention(RetentionPolicy.SOURCE)
                    @IntDef({Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                            Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY})
                    public @interface CalendarDay {
                    }""").indented(),
                java("""
                    package test.pkg;

                    import android.support.annotation.IntDef;

                    import java.lang.annotation.Retention;
                    import java.lang.annotation.RetentionPolicy;
                    import java.util.Calendar;

                    @SuppressWarnings("ClassNameDiffersFromFileName")
                    @Retention(RetentionPolicy.SOURCE)
                    @IntDef({Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                            Calendar.THURSDAY, Calendar.FRIDAY})
                    public @interface WeekDay {
                    }""").indented(),
                SUPPORT_ANNOTATIONS_CLASS_PATH,
                SUPPORT_ANNOTATIONS_JAR)
                .run()
                .expectClean()
    }

    fun testIndirectTypedef() {
        // Regression test for b/36384014
        lint().files(
                java("package test.pkg;\n"
                        + "\n"
                        + "import android.support.annotation.IntDef;\n"
                        + "\n"
                        + "public class Lifecycle {\n"
                        + "    public static final int ON_CREATE = 1;\n"
                        + "    public static final int ON_START = 2;\n"
                        + "    public static final int ON_RESUME = 3;\n"
                        + "    public static final int ON_PAUSE = 4;\n"
                        + "    public static final int ON_STOP = 5;\n"
                        + "    public static final int ON_DESTROY = 6;\n"
                        + "    public static final int ANY = 7;\n"
                        + "\n"
                        +
                        "    @IntDef(value = {ON_CREATE, ON_START, ON_RESUME, ON_PAUSE, ON_STOP, ON_DESTROY, ANY},\n"
                        + "            flag = true)\n"
                        + "    public @interface Event {\n"
                        + "    }\n"
                        + "}"),
                java(""
                        + "package test.pkg;\n"
                        + "\n"
                        + "import java.lang.annotation.ElementType;\n"
                        + "import java.lang.annotation.Retention;\n"
                        + "import java.lang.annotation.RetentionPolicy;\n"
                        + "import java.lang.annotation.Target;\n"
                        + "\n"
                        + "@Retention(RetentionPolicy.RUNTIME)\n"
                        + "@Target(ElementType.METHOD)\n"
                        + "public @interface OnLifecycleEvent {\n"
                        + "    @Lifecycle.Event\n"
                        + "    int value();\n"
                        + "}\n"),
                java(""
                        + "package test.pkg;\n"
                        + "\n"
                        + "public interface Usage {\n"
                        + "    @OnLifecycleEvent(4494823) // this value is not valid\n"
                        + "    void addLocationListener();\n"
                        + "}\n"),
                SUPPORT_ANNOTATIONS_CLASS_PATH,
                SUPPORT_ANNOTATIONS_JAR)
                .run()
                .expect(""
                        +
                        "src/test/pkg/Usage.java:4: Error: Must be one or more of: Lifecycle.ON_CREATE, Lifecycle.ON_START, Lifecycle.ON_RESUME, Lifecycle.ON_PAUSE, Lifecycle.ON_STOP, Lifecycle.ON_DESTROY, Lifecycle.ANY [WrongConstant]\n"
                        + "    @OnLifecycleEvent(4494823) // this value is not valid\n"
                        + "                      ~~~~~~~\n"
                        + "1 errors, 0 warnings\n")
    }

    fun testCalendar() {
        // Regression test for
        // https://code.google.com/p/android/issues/detail?id=251256 and
        // http://youtrack.jetbrains.com/issue/IDEA-144891

        lint().files(
                java(""
                        + "package test.pkg;\n"
                        + "\n"
                        + "import java.util.Calendar;\n"
                        + "\n"
                        + "public class CalendarTest {\n"
                        + "    public void test() {\n"
                        + "        Calendar now = Calendar.getInstance();\n"
                        + "        now.get(Calendar.DAY_OF_MONTH);\n"
                        + "        now.get(Calendar.HOUR_OF_DAY);\n"
                        + "        now.get(Calendar.MINUTE);\n"
                        + "        if (now.get(Calendar.MONTH) == Calendar.JANUARY) {\n"
                        + "        }\n"
                        + "        now.set(Calendar.HOUR_OF_DAY, 50);\n"
                        + "        now.set(2017, 3, 29);\n"
                        + "    }\n"
                        + "}\n"))
                .run()
                .expectClean()
    }

    fun testIntDef() {

        lint().files(
                java(""
                        + "package test.pkg;\n"
                        + "import android.annotation.SuppressLint;\n"
                        + "import android.annotation.TargetApi;\n"
                        + "import android.app.Notification;\n"
                        + "import android.content.Context;\n"
                        + "import android.content.Intent;\n"
                        + "import android.content.ServiceConnection;\n"
                        + "import android.content.res.Resources;\n"
                        + "import android.os.Build;\n"
                        + "import android.support.annotation.DrawableRes;\n"
                        + "import android.view.View;\n"
                        + "\n"
                        + "import static android.content.Context.CONNECTIVITY_SERVICE;\n"
                        + "\n"
                        + "@SuppressWarnings(\"UnusedDeclaration\")\n"
                        + "public class X {\n"
                        + "\n"
                        + "    @TargetApi(Build.VERSION_CODES.KITKAT)\n"
                        + "    public void testStringDef(Context context, String unknown) {\n"
                        + "        Object ok1 = context.getSystemService(unknown);\n"
                        + "        Object ok2 = context.getSystemService(Context.CLIPBOARD_SERVICE);\n"
                        + "        Object ok3 = context.getSystemService(android.content.Context.WINDOW_SERVICE);\n"
                        + "        Object ok4 = context.getSystemService(CONNECTIVITY_SERVICE);\n"
                        + "    }\n"
                        + "\n"
                        + "    @SuppressLint(\"UseCheckPermission\")\n"
                        + "    @TargetApi(Build.VERSION_CODES.KITKAT)\n"
                        + "    public void testIntDef(Context context, int unknown, View view) {\n"
                        + "        view.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); // OK\n"
                        + "        view.setLayoutDirection(/*Must be one of: View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL, View.LAYOUT_DIRECTION_INHERIT, View.LAYOUT_DIRECTION_LOCALE*/View.TEXT_ALIGNMENT_TEXT_START/**/); // Error\n"
                        + "        view.setLayoutDirection(/*Flag not allowed here*/View.LAYOUT_DIRECTION_RTL | View.LAYOUT_DIRECTION_RTL/**/); // Error\n"
                        + "\n"
                        + "        // Regression test for http://b.android.com/197184\n"
                        + "        view.setLayoutDirection(View.LAYOUT_DIRECTION_RTL, View.LAYOUT_DIRECTION_LTR); // ERROR\n"
                        + "    }\n"
                        + "\n"
                        + "    @TargetApi(Build.VERSION_CODES.KITKAT)\n"
                        + "    public void testIntDefFlags(Context context, int unknown, Intent intent,\n"
                        + "                           ServiceConnection connection) {\n"
                        + "        // Flags\n"
                        + "        Object ok1 = context.bindService(intent, connection, 0);\n"
                        + "        Object ok2 = context.bindService(intent, connection, -1);\n"
                        + "        Object ok3 = context.bindService(intent, connection, Context.BIND_ABOVE_CLIENT);\n"
                        + "        Object ok4 = context.bindService(intent, connection, Context.BIND_ABOVE_CLIENT\n"
                        + "                | Context.BIND_AUTO_CREATE);\n"
                        + "        int flags1 = Context.BIND_ABOVE_CLIENT | Context.BIND_AUTO_CREATE;\n"
                        + "        Object ok5 = context.bindService(intent, connection, flags1);\n"
                        + "\n"
                        + "        Object error1 = context.bindService(intent, connection,\n"
                        + "                Context.BIND_ABOVE_CLIENT | /*Must be one or more of: Context.BIND_AUTO_CREATE, Context.BIND_DEBUG_UNBIND, Context.BIND_NOT_FOREGROUND, Context.BIND_ABOVE_CLIENT, Context.BIND_ALLOW_OOM_MANAGEMENT, Context.BIND_WAIVE_PRIORITY, Context.BIND_IMPORTANT, Context.BIND_ADJUST_WITH_ACTIVITY*/Context.CONTEXT_IGNORE_SECURITY/**/);\n"
                        + "        int flags2 = Context.BIND_ABOVE_CLIENT | Context.CONTEXT_IGNORE_SECURITY;\n"
                        + "        Object error2 = context.bindService(intent, connection, /*Must be one or more of: Context.BIND_AUTO_CREATE, Context.BIND_DEBUG_UNBIND, Context.BIND_NOT_FOREGROUND, Context.BIND_ABOVE_CLIENT, Context.BIND_ALLOW_OOM_MANAGEMENT, Context.BIND_WAIVE_PRIORITY, Context.BIND_IMPORTANT, Context.BIND_ADJUST_WITH_ACTIVITY*/flags2/**/);\n"
                        + "    }\n"
                        + "}\n"),
                SUPPORT_ANNOTATIONS_CLASS_PATH,
                SUPPORT_ANNOTATIONS_JAR)
                .allowCompilationErrors()
                .run()
                .expectInlinedMessages()
    }

    fun testStringDefOnEquals() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=186598

        lint().files(
                java(""
                        + "package test.pkg;\n"
                        + "\n"
                        + "import android.support.annotation.StringDef;\n"
                        + "\n"
                        + "import java.lang.annotation.Retention;\n"
                        + "\n"
                        + "@SuppressWarnings({\"unused\", \"StringEquality\"})\n"
                        + "public class X {\n"
                        + "    public static final String SUNDAY = \"a\";\n"
                        + "    public static final String MONDAY = \"b\";\n"
                        + "\n"
                        + "    @StringDef(value = {\n"
                        + "            SUNDAY,\n"
                        + "            MONDAY\n"
                        + "    })\n"
                        + "    @Retention(java.lang.annotation.RetentionPolicy.SOURCE)\n"
                        + "    public @interface Day {\n"
                        + "    }\n"
                        + "\n"
                        + "    @Day\n"
                        + "    public String getDay() {\n"
                        + "        return MONDAY;\n"
                        + "    }\n"
                        + "\n"
                        + "    public void test(Object object) {\n"
                        + "        boolean ok1 = this.getDay() == /*Must be one of: X.SUNDAY, X.MONDAY*/\"Any String\"/**/;\n"
                        + "        boolean ok2 = this.getDay().equals(MONDAY);\n"
                        + "        boolean wrong1 = this.getDay().equals(/*Must be one of: X.SUNDAY, X.MONDAY*/\"Any String\"/**/);\n"
                        + "    }\n"
                        + "}\n"),
                SUPPORT_ANNOTATIONS_CLASS_PATH,
                SUPPORT_ANNOTATIONS_JAR)
                .run()
                .expectInlinedMessages()
    }

    fun testAndingWithForeignMasks() {
        // False positive encountered in the support lib codebase (simplified):
        // Allow &'ing flags with masks without restrictions (necessary since in
        // many cases the masks are coming from unknown declarations (class fields
        // where we only have values, not references)
        lint().files(
                java("""
                    package test.pkg;

                    import android.support.annotation.IntDef;
                    import android.view.Gravity;
                    import android.view.View;

                    import java.lang.annotation.Retention;
                    import java.lang.annotation.RetentionPolicy;

                    @SuppressWarnings("ClassNameDiffersFromFileName")
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
                    """).indented(),
                SUPPORT_ANNOTATIONS_CLASS_PATH,
                SUPPORT_ANNOTATIONS_JAR)
                .run()
                .expectClean()
    }

    fun testVarIntDef() {
        // Regression test for b/37078720
        lint().files(
                java("""
                package test.pkg;

                import android.support.annotation.IntDef;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;

                @SuppressWarnings({"ClassNameDiffersFromFileName", "UnusedAssignment"})
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
                """).indented(),
                SUPPORT_ANNOTATIONS_CLASS_PATH,
                SUPPORT_ANNOTATIONS_JAR)
                .run()
                .expect("src/test/pkg/IntDefVarTest.java:26: Error: Must be one of: IntDefVarTest.TREE_PATH_ONE, IntDefVarTest.TREE_PATH_TWO, IntDefVarTest.TREE_PATH_THREE [WrongConstant]\n" +
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
                        "4 errors, 0 warnings\n")
    }

    // Temporarily disabled because PSQ does not seem to have tools/adt/idea
    //  9af9ae6ed2a4fe8d6a4a29726772568cb505b4ed applied. Hiding test for now to
    // unblock integrating bigger change; restore in separate CL.
    fun ignored_testCalendarGet() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=230099

        lint().files(
                java(""
                        + "package test.pkg;\n"
                        + "import java.util.Calendar;\n"
                        + "\n"
                        + "@SuppressWarnings({\"unused\", \"StatementWithEmptyBody\"})\n"
                        + "public class X  {\n"
                        + "    private void check(Calendar lhsCal, Calendar rhsCal) {\n"
                        + "        if( lhsCal.get(Calendar.DAY_OF_YEAR) == rhsCal.get(Calendar.DAY_OF_YEAR)+1){\n"
                        + "        }\n"
                        + "        if( lhsCal.get(Calendar.DAY_OF_YEAR) == 200){\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n"),
                SUPPORT_ANNOTATIONS_CLASS_PATH,
                SUPPORT_ANNOTATIONS_JAR)
                .run()
                .expectClean()
    }


}