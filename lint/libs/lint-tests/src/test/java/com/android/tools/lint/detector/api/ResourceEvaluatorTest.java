/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static com.android.tools.lint.checks.infrastructure.TestFiles.java;
import static com.android.tools.lint.checks.infrastructure.TestFiles.kotlin;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.lint.checks.infrastructure.TestFile;
import com.android.utils.Pair;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiRecursiveElementVisitor;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicReference;
import junit.framework.TestCase;
import org.intellij.lang.annotations.Language;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UFile;
import org.jetbrains.uast.UVariable;
import org.jetbrains.uast.visitor.AbstractUastVisitor;
import org.junit.Assert;

@SuppressWarnings("ClassNameDiffersFromFileName")
public class ResourceEvaluatorTest extends TestCase {

    @Language("JAVA")
    private static String getFullKotlinSource(String statementsSource) {
        @Language("kotlin")
        String s =
                ""
                        + "package test.pkg\n"
                        + "\n"
                        + "const val RED = R.color.red\n"
                        + "const val MY_COLOR = RED\n"
                        + "class Test {\n"
                        + "    fun test() {\n"
                        + "        "
                        + statementsSource.replace("\n", "\n        ")
                        + "\n"
                        + "    }\n"
                        + "    fun someMethod(@android.support.annotation.DrawableRes @android.support.annotation.StringRes param: Int) {c}\n"
                        + "}";
        return s;
    }

    @Language("JAVA")
    private static String getFullJavaSource(String statementsSource) {
        @Language("JAVA")
        String s =
                ""
                        + "package test.pkg;\n"
                        + "public class Test {\n"
                        + "    public void test() {\n"
                        + "        "
                        + statementsSource.replace("\n", "\n        ")
                        + "\n"
                        + "    }\n"
                        + "    public static final int RED = R.color.red;\n"
                        + "    public static final int MY_COLOR = RED;\n"
                        + "    public void someMethod(@android.support.annotation.DrawableRes @android.support.annotation.StringRes int param) { }\n"
                        + "}\n";
        return s;
    }

    private static final TestFile rClass =
            java(
                    ""
                            + ""
                            + "package test.pkg;\n"
                            + "public class R {\n"
                            + "    public static class string {\n"
                            + "        public static final int foo=0x7f050000;\n"
                            + "    }\n"
                            + "    public static class color {\n"
                            + "        public static final int red=0x7f060000;\n"
                            + "        public static final int green=0x7f060001;\n"
                            + "        public static final int blue=0x7f060002;\n"
                            + "    }\n"
                            + "}");

    interface ContextChecker {
        void check(@NonNull JavaContext context);
    }

    private static void checkUast(@NonNull ContextChecker checker, @NonNull TestFile... files) {
        Pair<JavaContext, Disposable> pair = LintUtilsTest.parse(files);
        JavaContext context = pair.getFirst();
        Disposable disposable = pair.getSecond();
        assertNotNull(context);
        UFile uFile = context.getUastFile();
        assertNotNull(uFile);
        assertNoErrors(uFile);
        checker.check(context);
        Disposer.dispose(disposable);
    }

    @Nullable
    private static UExpression findExpression(UFile uFile, String targetVariable) {
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

        return reference.get();
    }

    @Nullable
    private static PsiExpression findExpression(PsiFile javaFile, String targetVariable) {
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
        return reference.get();
    }

    private static void checkUast(
            String expected,
            final String targetVariable,
            boolean getSpecificType,
            boolean allowDereference,
            TestFile... testFiles) {
        checkUast(
                new ContextChecker() {
                    @Override
                    public void check(@NonNull JavaContext context) {
                        UExpression expression =
                                findExpression(context.getUastFile(), targetVariable);
                        ResourceEvaluator evaluator =
                                new ResourceEvaluator(context.getEvaluator())
                                        .allowDereference(allowDereference);

                        if (getSpecificType) {
                            ResourceUrl actual = evaluator.getResource(expression);
                            if (expected == null) {
                                assertNull(actual);
                            } else {
                                assertNotNull(
                                        "Couldn't compute resource for "
                                                + testFiles[0].contents
                                                + ", expected "
                                                + expected,
                                        actual);

                                assertEquals(expected, actual.toString());
                            }
                        } else {
                            EnumSet<ResourceType> types = evaluator.getResourceTypes(expression);
                            if (expected == null) {
                                assertNull(types);
                            } else {
                                assertNotNull(
                                        "Couldn't compute resource types for "
                                                + testFiles[0].contents
                                                + ", expected "
                                                + expected,
                                        types);

                                assertEquals(expected, types.toString());
                            }
                        }
                    }
                },
                testFiles);
    }

    private static void assertNoErrors(UFile uFile) {
        assertNoErrors(uFile.getPsi());
    }

    private static void assertNoErrors(PsiFile psiFile) {
        psiFile.accept(
                new PsiRecursiveElementVisitor() {
                    @Override
                    public void visitErrorElement(PsiErrorElement element) {
                        Assert.fail(
                                "Found error element (\""
                                        + element.getText()
                                        + "\") in parsed source "
                                        + psiFile.getText());
                        super.visitErrorElement(element);
                    }
                });
    }

    private static void checkUastKotlin(
            String expected,
            String statementsSource,
            final String targetVariable,
            boolean getSpecificType,
            boolean allowDereference) {
        @Language("kotlin")
        String source = getFullKotlinSource(statementsSource);
        checkUast(
                expected,
                targetVariable,
                getSpecificType,
                allowDereference,
                kotlin(source),
                rClass);
    }

    private static void checkUastJava(
            String expected,
            String statementsSource,
            final String targetVariable,
            boolean getSpecificType,
            boolean allowDereference) {
        @Language("JAVA")
        String source = getFullJavaSource(statementsSource);
        checkUast(
                expected, targetVariable, getSpecificType, allowDereference, java(source), rClass);
    }

    private static void checkPsiJava(
            String expected,
            String statementsSource,
            final String targetVariable,
            boolean getSpecificType,
            boolean allowDereference) {
        @Language("JAVA")
        String source = getFullJavaSource(statementsSource);

        checkUast(
                new ContextChecker() {
                    @Override
                    public void check(@NonNull JavaContext context) {
                        PsiExpression expression =
                                findExpression(context.getPsiFile(), targetVariable);
                        ResourceEvaluator evaluator =
                                new ResourceEvaluator(context.getEvaluator())
                                        .allowDereference(allowDereference);

                        if (getSpecificType) {
                            ResourceUrl actual = evaluator.getResource(expression);
                            if (expected == null) {
                                assertNull(actual);
                            } else {
                                assertNotNull(
                                        "Couldn't compute resource for "
                                                + source
                                                + ", expected "
                                                + expected,
                                        actual);

                                assertEquals(expected, actual.toString());
                            }
                        } else {
                            EnumSet<ResourceType> types = evaluator.getResourceTypes(expression);
                            if (expected == null) {
                                assertNull(types);
                            } else {
                                assertNotNull(
                                        "Couldn't compute resource types for "
                                                + source
                                                + ", expected "
                                                + expected,
                                        types);

                                assertEquals(expected, types.toString());
                            }
                        }
                    }
                },
                java(source),
                rClass);
    }

    private static void check(
            String expected,
            @NonNull String javaStatementsSource,
            @NonNull String kotlinStatementsSource,
            final String targetVariable,
            boolean getSpecificType,
            boolean allowDereference) {
        checkUastJava(
                expected, javaStatementsSource, targetVariable, getSpecificType, allowDereference);
        checkPsiJava(
                expected, javaStatementsSource, targetVariable, getSpecificType, allowDereference);
        checkUastKotlin(
                expected,
                kotlinStatementsSource,
                targetVariable,
                getSpecificType,
                allowDereference);
    }

    private static void checkType(
            String expected,
            String statementsSource,
            String kotlinStatementsSource,
            final String targetVariable) {
        check(expected, statementsSource, kotlinStatementsSource, targetVariable, true, true);
    }

    private static void checkTypes(
            String expected,
            String statementsSource,
            String kotlinStatementsSource,
            final String targetVariable) {
        check(expected, statementsSource, kotlinStatementsSource, targetVariable, false, true);
    }

    private static void checkTypes(
            String expected,
            String statementsSource,
            String kotlinStatementsSource,
            final String targetVariable,
            boolean allowDereference) {
        check(
                expected,
                statementsSource,
                kotlinStatementsSource,
                targetVariable,
                false,
                allowDereference);
    }

    public void testBasic() {
        checkType("@string/foo", "int x = R.string.foo;", "val x = R.string.foo", "x");
    }

    public void testIndirectFieldReference() {
        checkType(
                "@color/red",
                "int z = RED;\nint w = true ? z : 0;",
                "val z = RED\nval w = if (true) z else 0",
                "w");
    }

    public void testMethodCall() {
        checkType(
                "@color/green",
                ""
                        + "android.app.Activity context = null;\n"
                        + "int w = context.getResources().getColor(R.color.green);",
                "val context: android.app.Activity = null\n"
                        + "val w = context.resources.getColor(R.color.green)",
                "w");
    }

    public void testMethodCallNoDereference() {
        check(
                null,
                ""
                        + "android.app.Activity context = null;"
                        + "int w = context.getResources().getColor(R.color.green);",
                ""
                        + "val context: android.app.Activity = null\n"
                        + "val w = context.resources.getColor(R.color.green)",
                "w",
                true,
                false);
    }

    public void testReassignment() {
        checkType(
                "@string/foo",
                ""
                        + "int x = R.string.foo;\n"
                        + "int y = x;\n"
                        + "int w;\n"
                        + "w = -1;\n"
                        + "int z = y;\n",
                ""
                        + "val x = R.string.foo\n"
                        + "val y = x\n"
                        + "var w: Int = 0\n"
                        + "w = -1\n"
                        + "val z = y\n",
                "z");
    }

    // Resource Types

    public void testReassignmentType() {
        checkTypes(
                "[string]",
                ""
                        + "int x = R.string.foo;\n"
                        + "int y = x;\n"
                        + "int w;\n"
                        + "w = -1;\n"
                        + "int z = y;\n",
                ""
                        + "val x = R.string.foo\n"
                        + "val y = x\n"
                        + "var w: Int = 0\n"
                        + "w = -1\n"
                        + "val z = y\n",
                "z");
    }

    public void testMethodCallTypes() {
        // public=color int marker
        checkTypes(
                "[public]",
                ""
                        + "android.app.Activity context = null;"
                        + "int w = context.getResources().getColor(R.color.green);",
                ""
                        + "val context: android.app.Activity = null\n"
                        + "val w = context.resources.getColor(R.color.green)",
                "w");
    }

    public void testConditionalTypes() {
        // Constant expression: we know exactly which branch to take
        checkTypes(
                "[color]",
                "int z = RED;\nint w = true ? z : R.string.foo;",
                "val z = RED\nval w = if (true) z else R.string.foo",
                "w");
    }

    public void testConditionalTypesUnknownCondition() {
        // Constant expression: we know exactly which branch to take
        checkTypes(
                "[color, string]",
                "int z = RED;\nint w = toString().indexOf('x') > 2 ? z : R.string.foo;",
                "val z = RED\nval w = if (toString().indexOf('x') > 2) z else R.string.foo",
                "w");
    }

    public void testResourceTypes() {
        assertEquals(
                ResourceType.ANIM,
                ResourceEvaluator.getTypeFromAnnotationSignature(
                        ResourceEvaluator.ANIM_RES_ANNOTATION.defaultName()));
        assertEquals(
                ResourceType.STRING,
                ResourceEvaluator.getTypeFromAnnotationSignature(
                        ResourceEvaluator.STRING_RES_ANNOTATION.defaultName()));
        assertEquals(
                ResourceType.LAYOUT,
                ResourceEvaluator.getTypeFromAnnotationSignature(
                        ResourceEvaluator.LAYOUT_RES_ANNOTATION.defaultName()));
    }
}
