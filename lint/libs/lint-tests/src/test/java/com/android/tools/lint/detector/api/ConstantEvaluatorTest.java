/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tools.lint.detector.api;

import com.android.tools.lint.UastEnvironment;
import com.android.utils.Pair;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLocalVariable;
import java.io.File;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import junit.framework.TestCase;
import org.intellij.lang.annotations.Language;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UFile;
import org.jetbrains.uast.UVariable;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

@SuppressWarnings("ClassNameDiffersFromFileName")
public class ConstantEvaluatorTest extends TestCase {
    private static void checkJavaUast(
            Object expected, @Language("JAVA") String source, final String targetVariable) {
        Pair<JavaContext, Disposable> pair =
                LintUtilsTest.parse(source, new File("src/test/pkg/Test.java"));
        checkUast(expected, pair, source, targetVariable);
    }

    private static void checkKotlinUast(
            Object expected, @Language("Kt") String source, final String targetVariable) {
        Pair<JavaContext, Disposable> pair =
                LintUtilsTest.parseKotlin(source, new File("src/test/pkg/Test.kt"));
        checkUast(expected, pair, source, targetVariable);
    }

    private static void checkUast(
            Object expected,
            Pair<JavaContext, Disposable> pair,
            String source,
            final String targetVariable) {
        JavaContext context = pair.getFirst();
        Disposable disposable = pair.getSecond();
        assertNotNull(context);
        UFile uFile = context.getUastFile();
        assertNotNull(uFile);

        // Find the expression
        final AtomicReference<UExpression> reference = new AtomicReference<>();
        uFile.accept(
                new AbstractUastVisitor() {
                    @Override
                    public boolean visitVariable(UVariable variable) {
                        String name = variable.getName();
                        if (name != null && name.equals(targetVariable)) {
                            reference.set(variable.getUastInitializer());
                        }

                        return super.visitVariable(variable);
                    }
                });

        UExpression expression = reference.get();
        Object actual = ConstantEvaluator.evaluate(context, expression);
        if (expected == null) {
            assertNull(actual);
        } else {
            assertNotNull(
                    "Couldn't compute value for "
                            + source
                            + ", expected "
                            + expected
                            + " but was "
                            + actual,
                    actual);
            assertSame(expected.getClass(), actual.getClass());
            if (expected instanceof Object[] && actual instanceof Object[]) {
                assertEquals(
                        Arrays.toString((Object[]) expected), Arrays.toString((Object[]) actual));
                assertTrue(Arrays.equals((Object[]) expected, (Object[]) actual));
            } else if (expected instanceof int[] && actual instanceof int[]) {
                assertEquals(Arrays.toString((int[]) expected), Arrays.toString((int[]) actual));
            } else if (expected instanceof boolean[] && actual instanceof boolean[]) {
                assertEquals(
                        Arrays.toString((boolean[]) expected), Arrays.toString((boolean[]) actual));
            } else if (expected instanceof byte[] && actual instanceof byte[]) {
                assertEquals(Arrays.toString((byte[]) expected), Arrays.toString((byte[]) actual));
            } else {
                assertEquals(expected.toString(), actual.toString());
                assertEquals(expected, actual);
            }
        }
        if (expected instanceof String) {
            assertEquals(expected, ConstantEvaluator.evaluateString(context, expression, false));
        }
        Disposer.dispose(disposable);
    }

    private static void checkPsi(
            Object expected, @Language("JAVA") String source, final String targetVariable) {
        Pair<JavaContext, Disposable> pair =
                LintUtilsTest.parse(source, new File("src/test/pkg/Test.java"));
        JavaContext context = pair.getFirst();
        Disposable disposable = pair.getSecond();
        assertNotNull(context);
        PsiFile javaFile = context.getPsiFile();
        assertNotNull(javaFile);

        // Find the expression
        final AtomicReference<PsiExpression> reference = new AtomicReference<>();
        javaFile.accept(
                new JavaRecursiveElementVisitor() {
                    @Override
                    public void visitLocalVariable(PsiLocalVariable variable) {
                        super.visitLocalVariable(variable);
                        String name = variable.getName();
                        if (name != null && name.equals(targetVariable)) {
                            reference.set(variable.getInitializer());
                        }
                    }
                });
        PsiExpression expression = reference.get();
        Object actual = ConstantEvaluator.evaluate(context, expression);
        if (expected == null) {
            assertNull(actual);
        } else {
            assertNotNull(
                    "Couldn't compute value for "
                            + source
                            + ", expected "
                            + expected
                            + " but was "
                            + actual,
                    actual);
            assertSame(expected.getClass(), actual.getClass());
            if (expected instanceof Object[] && actual instanceof Object[]) {
                assertEquals(
                        Arrays.toString((Object[]) expected), Arrays.toString((Object[]) actual));
                assertTrue(Arrays.equals((Object[]) expected, (Object[]) actual));
            } else if (expected instanceof int[] && actual instanceof int[]) {
                assertEquals(Arrays.toString((int[]) expected), Arrays.toString((int[]) actual));
            } else if (expected instanceof boolean[] && actual instanceof boolean[]) {
                assertEquals(
                        Arrays.toString((boolean[]) expected), Arrays.toString((boolean[]) actual));
            } else if (expected instanceof byte[] && actual instanceof byte[]) {
                assertEquals(Arrays.toString((byte[]) expected), Arrays.toString((byte[]) actual));
            } else {
                assertEquals(expected.toString(), actual.toString());
                assertEquals(expected, actual);
            }
        }
        if (expected instanceof String) {
            assertEquals(expected, ConstantEvaluator.evaluateString(context, expression, false));
        }
        Disposer.dispose(disposable);
    }

    private static void check(
            Object expected, @Language("JAVA") String source, final String targetVariable) {
        checkJavaUast(expected, source, targetVariable);
        checkPsi(expected, source, targetVariable);
        UastEnvironment.disposeApplicationEnvironment();
    }

    private static void checkStatements(
            Object expected, String statementsSource, final String targetVariable) {
        @Language("JAVA")
        String source =
                ""
                        + "package test.pkg;\n"
                        + "public class Test {\n"
                        + "    public void test() {\n"
                        + "        "
                        + statementsSource
                        + "\n"
                        + "    }\n"
                        + "    public static final int MY_INT_FIELD = 5;\n"
                        + "    public static final boolean MY_BOOLEAN_FIELD = true;\n"
                        + "    public static final String MY_STRING_FIELD = \"test\";\n"
                        + "}\n";

        check(expected, source, targetVariable);
    }

    private static void checkExpression(Object expected, String expressionSource) {
        @Language("JAVA")
        String source =
                ""
                        + "package test.pkg;\n"
                        + "public class Test {\n"
                        + "    public void test() {\n"
                        + "        Object expression = "
                        + expressionSource
                        + ";\n"
                        + "    }\n"
                        + "    public static final int MY_INT_FIELD = 5;\n"
                        + "    public static final boolean MY_BOOLEAN_FIELD = true;\n"
                        + "    public static final String MY_STRING_FIELD = \"test\";\n"
                        + "}\n";

        check(expected, source, "expression");
    }

    private static void checkKotlinExpression(Object expected, String expressionSource) {
        @Language("Kt")
        String source =
                ""
                        + "package test.pkg\n"
                        + "class Test {\n"
                        + "    fun test() {\n"
                        + "        val expression = "
                        + expressionSource
                        + "\n"
                        + "    }\n"
                        + "    const val MY_INT_FIELD = 5;\n"
                        + "    const val MY_BOOLEAN_FIELD = true;\n"
                        + "    const val MY_STRING_FIELD = \"test\";\n"
                        + "}\n";

        checkKotlinUast(expected, source, "expression");
        UastEnvironment.disposeApplicationEnvironment();
    }

    public void testStrings() {
        checkExpression(null, "null");
        checkExpression("hello", "\"hello\"");
        checkExpression("abcd", "\"ab\" + \"cd\"");
    }

    public void testArrays() {
        checkExpression(new int[] {1, 2, 3}, "new int[] { 1,2,3] }");
        checkExpression(new int[0], "new int[0]");
        checkExpression(new byte[0], "new byte[0]");
    }

    public void testLargeArrays() {
        checkExpression(new ConstantEvaluator.ArrayReference(Byte.TYPE, 100, 2), "new byte[100][]");
        checkExpression(new ConstantEvaluator.ArrayReference(Byte.TYPE, 100, 1), "new byte[100]");
        checkExpression(
                new ConstantEvaluator.ArrayReference("java.lang.Integer", 100, 1),
                "new Integer[100]");
        checkExpression(100, "(new byte[100]).length");
        checkExpression(100, "(new Integer[100]).length");
    }

    public void testKotlin() {
        checkKotlinExpression(
                new ConstantEvaluator.ArrayReference(Integer.TYPE, 100, 1), "IntArray(100)");
        checkKotlinExpression(100, "IntArray(100).size");
        checkKotlinExpression(1000, "kotlin.Array<String>(1000).size");
        checkKotlinExpression(
                new ConstantEvaluator.ArrayReference(String.class, 1000, 1), "Array<String>(1000)");
        checkKotlinExpression(
                new ConstantEvaluator.ArrayReference(String.class, 1000, 1),
                "kotlin.Array<String>(1000)");
        checkKotlinExpression(new Integer[] {1, 2, 3, 4}, "arrayOf(1,2,3,4)");
        checkKotlinExpression(3, "arrayOf(1,2,3,4)[2]");
        checkKotlinExpression(4, "arrayOf(1,2,3,4).size");
        checkKotlinExpression(
                new ConstantEvaluator.ArrayReference(String.class, 1000, 1),
                "arrayOfNulls<String>(1000)");
        checkKotlinExpression(1000, "arrayOfNulls<String>(1000).size");
    }

    public void testBooleans() {
        checkExpression(true, "true");
        checkExpression(false, "false");
        checkExpression(false, "false && true");
        checkExpression(true, "false || true");
        checkExpression(true, "!false");
    }

    public void testPolyadicBooleans() {
        checkExpression(false, "false && true && true");
        checkExpression(true, "false || false || true");
        checkExpression(true, "false ^ false ^ true");
    }

    public void testChars() {
        checkExpression('a', "'a'");
        checkExpression('\007', "'\007'");
    }

    public void testCasts() {
        checkExpression(1, "(int)1");
        checkExpression(1L, "(long)1");
        checkExpression(1, "(int)1.1f");
        checkExpression((short) 65537, "(short)65537");
        checkExpression((byte) 1023, "(byte)1023");
        checkExpression(1.5, "(double)1.5f");
        checkExpression(-5.0, "(double)-5");
    }

    public void testArithmetic() {
        checkExpression(1, "1");
        checkExpression(1L, "1L");
        checkExpression(4, "1 + 3");
        checkExpression(-2, "1 - 3");
        checkExpression(10, "2 * 5");
        checkExpression(2, "10 / 5");
        checkExpression(1, "11 % 5");
        checkExpression(8, "1 << 3");
        checkExpression(16, "32 >> 1");
        checkExpression(16, "32 >>> 1");
        checkExpression(5, "5 | 1");
        checkExpression(1, "5 & 1");
        checkExpression(~5, "~5");
        checkExpression(~(long) 5, "~(long)5");
        checkExpression(~(short) 5, "~(short)5");
        checkExpression(~(byte) 5, "~(byte)5");
        checkExpression(-(long) 5, "-(long)5");
        checkExpression(-(short) 5, "-(short)5");
        checkExpression(-(byte) 5, "-(byte)5");
        checkExpression(-(double) 5, "-(double)5");
        checkExpression(-(float) 5, "-(float)5");
        checkExpression(-2, "1 + -3");

        checkExpression(false, "11 == 5");
        checkExpression(true, "11 == 11");
        checkExpression(true, "11 != 5");
        checkExpression(false, "11 != 11");
        checkExpression(true, "11 > 5");
        checkExpression(false, "5 > 11");
        checkExpression(false, "11 < 5");
        checkExpression(true, "5 < 11");
        checkExpression(true, "11 >= 5");
        checkExpression(false, "5 >= 11");
        checkExpression(false, "11 <= 5");
        checkExpression(true, "5 <= 11");

        checkExpression(3.5f, "1.0f + 2.5f");
    }

    public void testPolyadicArithmetic() {
        checkExpression(9, "1 + 3 + 5");
        checkExpression(94, "100 - 3 - 3");
        checkExpression(100, "2 * 5 * 10");
        checkExpression(1, "10 / 5 / 2");
        checkExpression(16, "1 << 3 << 1");
        checkExpression(8, "32 >> 1 >> 1");
        checkExpression(8, "32 >>> 1 >>> 1");
        checkExpression(5, "5 | 1 | 1");
        checkExpression(1, "5 & 1 & 1");
        checkExpression(true, "true && true && true");
    }

    public void testFieldReferences() {
        checkExpression(5, "MY_INT_FIELD");
        checkExpression("test", "MY_STRING_FIELD");
        checkExpression("prefix-test-postfix", "\"prefix-\" + MY_STRING_FIELD + \"-postfix\"");
        checkExpression(-4, "3 - (MY_INT_FIELD + 2)");
    }

    public void testStatements() {
        checkStatements(
                9,
                ""
                        + "int x = +5;\n"
                        + "int y = x;\n"
                        + "int w;\n"
                        + "w = -1;\n"
                        + "int z = x + 5 + w;\n",
                "z");
        checkStatements(
                "hello world",
                ""
                        + "String initial = \"hello\";\n"
                        + "String other;\n"
                        + "other = \" world\";\n"
                        + "String finalString = initial + other;\n",
                "finalString");
    }

    public void testConditionals() {
        checkStatements(
                -5,
                ""
                        + "boolean condition = false;\n"
                        + "condition = !condition;\n"
                        + "int z = condition ? -5 : 4;\n",
                "z");
        checkStatements(
                -4, "boolean condition = true && false;\nint z = condition ? 5 : -4;\n", "z");
    }
}
