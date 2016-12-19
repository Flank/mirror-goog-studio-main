/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.tools.lint.checks;

import static com.android.tools.lint.detector.api.LintUtils.isNullLiteral;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Detector.JavaPsiScanner;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAssertStatement;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiLiteral;
import com.intellij.psi.PsiParenthesizedExpression;
import java.util.Collections;
import java.util.List;

/**
 * Looks for assertion usages.
 */
public class AssertDetector extends Detector implements JavaPsiScanner {
    /** Using assertions */
    public static final Issue ISSUE = Issue.create(
            "Assert",
            "Assertions",

            "Assertions are not checked at runtime. There are ways to request that they be used " +
            "by Dalvik (`adb shell setprop debug.assert 1`), but note that this is not " +
            "implemented in ART (the newer runtime), and even in Dalvik the property is ignored " +
            "in many places and can not be relied upon. Instead, perform conditional checking " +
            "inside `if (BuildConfig.DEBUG) { }` blocks. That constant is a static final boolean " +
            "which is true in debug builds and false in release builds, and the Java compiler " +
            "completely removes all code inside the if-body from the app.\n" +
            "\n" +
            "For example, you can replace `assert speed > 0` with " +
            "`if (BuildConfig.DEBUG && !(speed > 0)) { throw new AssertionError() }`.\n" +
            "\n" +
            "(Note: This lint check does not flag assertions purely asserting nullness or " +
            "non-nullness; these are typically more intended for tools usage than runtime " +
            "checks.)",

            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    AssertDetector.class,
                    Scope.JAVA_FILE_SCOPE))
            .addMoreInfo(
            "https://code.google.com/p/android/issues/detail?id=65183");

    /** Constructs a new {@link AssertDetector} check */
    public AssertDetector() {
    }

    // ---- Implements JavaScanner ----


    @Override
    public List<Class<? extends PsiElement>> getApplicablePsiTypes() {
        return Collections.singletonList(PsiAssertStatement.class);
    }

    @Nullable
    @Override
    public JavaElementVisitor createPsiVisitor(@NonNull final JavaContext context) {
        if (!context.getMainProject().isAndroidProject()) {
            return null;
        }

        return new JavaElementVisitor() {
            @Override
            public void visitAssertStatement(PsiAssertStatement node) {
                PsiExpression assertion = node.getAssertCondition();
                // Allow "assert true"; it's basically a no-op
                if (assertion instanceof PsiLiteral) {
                    Object value = ((PsiLiteral)assertion).getValue();
                    if (Boolean.TRUE.equals(value)) {
                        return;
                    }
                } else {
                    // Allow assertions of the form "assert foo != null" because they are often used
                    // to make statements to tools about known nullness properties. For example,
                    // findViewById() may technically return null in some cases, but a developer
                    // may know that it won't be when it's called correctly, so the assertion helps
                    // to clear nullness warnings.
                    if (isNullCheck(assertion)) {
                        return;
                    }
                }

                // Tracking bug for ART: b/18833580
                String message = "Assertions are unreliable in Dalvik and unimplemented in ART. Use `BuildConfig.DEBUG` conditional checks instead.";
                PsiElement locationNode = node;
                if (node.getFirstChild() instanceof PsiKeyword
                        && PsiKeyword.ASSERT.equals(node.getFirstChild().getText())) {
                    locationNode = locationNode.getFirstChild();
                }

                context.report(ISSUE, node, context.getLocation(locationNode), message);
            }
        };
    }

    /**
     * Checks whether the given expression is purely a non-null check, e.g. it will return
     * true for expressions like "a != null" and "a != null && b != null" and
     * "b == null || c != null".
     */
    private static boolean isNullCheck(PsiExpression expression) {
        while (expression instanceof PsiParenthesizedExpression) {
            expression = ((PsiParenthesizedExpression) expression).getExpression();
        }
        if (expression instanceof PsiBinaryExpression) {
            PsiBinaryExpression binExp = (PsiBinaryExpression) expression;
            PsiExpression lOperand = binExp.getLOperand();
            PsiExpression rOperand = binExp.getROperand();
            if (isNullLiteral(lOperand) || isNullLiteral(rOperand)) {
                return true;
            } else {
                return isNullCheck(lOperand) && isNullCheck(rOperand);
            }
        } else {
            return false;
        }
    }
}
