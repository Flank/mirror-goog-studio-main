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

package com.android.tools.lint.checks;

import static com.android.tools.lint.client.api.JavaParser.TYPE_STRING;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ConstantEvaluator;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Detector.JavaPsiScanner;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiLiteral;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiReferenceExpression;

import java.util.Arrays;
import java.util.List;

/**
 * Detector for finding inefficiencies and errors in logging calls.
 */
public class LogDetector extends Detector implements JavaPsiScanner {
    private static final Implementation IMPLEMENTATION = new Implementation(
          LogDetector.class, Scope.JAVA_FILE_SCOPE);


    /** Log call missing surrounding if */
    public static final Issue CONDITIONAL = Issue.create(
            "LogConditional", //$NON-NLS-1$
            "Unconditional Logging Calls",
            "The BuildConfig class (available in Tools 17) provides a constant, \"DEBUG\", " +
            "which indicates whether the code is being built in release mode or in debug " +
            "mode. In release mode, you typically want to strip out all the logging calls. " +
            "Since the compiler will automatically remove all code which is inside a " +
            "\"if (false)\" check, surrounding your logging calls with a check for " +
            "BuildConfig.DEBUG is a good idea.\n" +
            "\n" +
            "If you *really* intend for the logging to be present in release mode, you can " +
            "suppress this warning with a @SuppressLint annotation for the intentional " +
            "logging calls.",

            Category.PERFORMANCE,
            5,
            Severity.WARNING,
            IMPLEMENTATION).setEnabledByDefault(false);

    /** Mismatched tags between isLogging and log calls within it */
    public static final Issue WRONG_TAG = Issue.create(
            "LogTagMismatch", //$NON-NLS-1$
            "Mismatched Log Tags",
            "When guarding a `Log.v(tag, ...)` call with `Log.isLoggable(tag)`, the " +
            "tag passed to both calls should be the same. Similarly, the level passed " +
            "in to `Log.isLoggable` should typically match the type of `Log` call, e.g. " +
            "if checking level `Log.DEBUG`, the corresponding `Log` call should be `Log.d`, " +
            "not `Log.i`.",

            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            IMPLEMENTATION);

    /** Log tag is too long */
    public static final Issue LONG_TAG = Issue.create(
            "LongLogTag", //$NON-NLS-1$
            "Too Long Log Tags",
            "Log tags are only allowed to be at most 23 tag characters long.",

            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            IMPLEMENTATION);

    @SuppressWarnings("SpellCheckingInspection")
    private static final String IS_LOGGABLE = "isLoggable";       //$NON-NLS-1$
    public static final String LOG_CLS = "android.util.Log";     //$NON-NLS-1$
    private static final String PRINTLN = "println";              //$NON-NLS-1$

    // ---- Implements Detector.JavaScanner ----

    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList(
                "d",           //$NON-NLS-1$
                "e",           //$NON-NLS-1$
                "i",           //$NON-NLS-1$
                "v",           //$NON-NLS-1$
                "w",           //$NON-NLS-1$
                PRINTLN,
                IS_LOGGABLE);
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @Nullable JavaElementVisitor visitor,
            @NonNull PsiMethodCallExpression node, @NonNull PsiMethod method) {
        JavaEvaluator evaluator = context.getEvaluator();
        if (!evaluator.isMemberInClass(method, LOG_CLS)) {
            return;
        }

        String name = method.getName();
        boolean withinConditional = IS_LOGGABLE.equals(name) ||
                checkWithinConditional(context, node.getParent(), node);

        // See if it's surrounded by an if statement (and it's one of the non-error, spammy
        // log methods (info, verbose, etc))
        if (("i".equals(name) || "d".equals(name) || "v".equals(name) || PRINTLN.equals(name))
                && !withinConditional
                && performsWork(context, node)
                && context.isEnabled(CONDITIONAL)) {
            String message = String.format("The log call Log.%1$s(...) should be " +
                            "conditional: surround with `if (Log.isLoggable(...))` or " +
                            "`if (BuildConfig.DEBUG) { ... }`",
                    node.getMethodExpression().getReferenceName());
            context.report(CONDITIONAL, node, context.getLocation(node), message);
        }

        // Check tag length
        if (context.isEnabled(LONG_TAG)) {
            int tagArgumentIndex = PRINTLN.equals(name) ? 1 : 0;
            PsiParameterList parameterList = method.getParameterList();
            PsiExpressionList argumentList = node.getArgumentList();
            if (evaluator.parameterHasType(method, tagArgumentIndex, TYPE_STRING)
                    && parameterList.getParametersCount() == argumentList.getExpressions().length) {
                PsiExpression argument = argumentList.getExpressions()[tagArgumentIndex];
                String tag = ConstantEvaluator.evaluateString(context, argument, true);
                if (tag != null && tag.length() > 23) {
                    String message = String.format(
                            "The logging tag can be at most 23 characters, was %1$d (%2$s)",
                            tag.length(), tag);
                    context.report(LONG_TAG, node, context.getLocation(node), message);
                }
            }
        }
    }

    /** Returns true if the given logging call performs "work" to compute the message */
    private static boolean performsWork(
            @NonNull JavaContext context,
            @NonNull PsiMethodCallExpression node) {
        String referenceName = node.getMethodExpression().getReferenceName();
        if (referenceName == null) {
            return false;
        }
        int messageArgumentIndex = PRINTLN.equals(referenceName) ? 2 : 1;
        PsiExpression[] arguments = node.getArgumentList().getExpressions();
        if (arguments.length > messageArgumentIndex) {
            PsiExpression argument = arguments[messageArgumentIndex];
            if (argument == null) {
                return false;
            }
            if (argument instanceof PsiLiteral) {
                return false;
            }
            if (argument instanceof PsiBinaryExpression) {
                String string = ConstantEvaluator.evaluateString(context, argument, false);
                //noinspection VariableNotUsedInsideIf
                if (string != null) { // does it resolve to a constant?
                    return false;
                }
            } else if (argument instanceof PsiReferenceExpression) {
                if (((PsiReferenceExpression) argument).getQualifier() == null) {
                    // Just a simple local variable/field reference
                    return false;
                }
                String string = ConstantEvaluator.evaluateString(context, argument, false);
                //noinspection VariableNotUsedInsideIf
                if (string != null) {
                    return false;
                }
                PsiElement resolved = context.getEvaluator().resolve(argument);
                if (resolved instanceof PsiField || resolved instanceof PsiLocalVariable ||
                        resolved instanceof PsiParameter) {
                    // Just a reference to a field, parameter or variable
                    return false;
                }
            }

            // Method invocations etc
            return true;
        }

        return false;
    }

    private static boolean checkWithinConditional(
            @NonNull JavaContext context,
            @Nullable PsiElement curr,
            @NonNull PsiMethodCallExpression logCall) {
        while (curr != null) {
            if (curr instanceof PsiIfStatement) {
                PsiIfStatement ifNode = (PsiIfStatement) curr;
                if (ifNode.getCondition() instanceof PsiMethodCallExpression) {
                    PsiMethodCallExpression call = (PsiMethodCallExpression) ifNode.getCondition();
                    if (IS_LOGGABLE.equals(call.getMethodExpression().getReferenceName())) {
                        checkTagConsistent(context, logCall, call);
                    }
                }

                return true;
            } else if (curr instanceof PsiMethodCallExpression
                    || curr instanceof PsiMethod
                    || curr instanceof PsiClass) { // static block
                break;
            }
            curr = curr.getParent();
        }
        return false;
    }

    /** Checks that the tag passed to Log.s and Log.isLoggable match */
    private static void checkTagConsistent(JavaContext context, PsiMethodCallExpression logCall,
            PsiMethodCallExpression isLoggableCall) {
        PsiExpression[] isLoggableArguments = isLoggableCall.getArgumentList().getExpressions();
        PsiExpression[] logArguments = logCall.getArgumentList().getExpressions();
        if (isLoggableArguments.length == 0 || logArguments.length == 0) {
            return;
        }
        PsiExpression isLoggableTag = isLoggableArguments[0];
        PsiExpression logTag = logArguments[0];

        String logCallName = logCall.getMethodExpression().getReferenceName();
        if (logCallName == null) {
            return;
        }
        boolean isPrintln = PRINTLN.equals(logCallName);
        if (isPrintln && logArguments.length > 1) {
            logTag = logArguments[1];
        }

        if (logTag != null) {
            if (!isLoggableTag.getText().equals(logTag.getText())) {
                PsiElement resolved1 = context.getEvaluator().resolve(isLoggableTag);
                PsiElement resolved2 = context.getEvaluator().resolve(logTag);
                if ((resolved1 == null || resolved2 == null || !resolved1.equals(resolved2))
                        && context.isEnabled(WRONG_TAG)) {
                    Location location = context.getLocation(logTag);
                    Location alternate = context.getLocation(isLoggableTag);
                    alternate.setMessage("Conflicting tag");
                    location.setSecondary(alternate);
                    String isLoggableDescription = resolved1 instanceof PsiMethod
                            ? ((PsiMethod)resolved1).getName()
                            : isLoggableTag.getText();
                    String logCallDescription = resolved2 instanceof PsiMethod
                            ? ((PsiMethod)resolved2).getName()
                            : logTag.getText();
                    String message = String.format(
                            "Mismatched tags: the `%1$s()` and `isLoggable()` calls typically " +
                                    "should pass the same tag: `%2$s` versus `%3$s`",
                            logCallName,
                            isLoggableDescription,
                            logCallDescription);
                    context.report(WRONG_TAG, isLoggableCall, location, message);
                }
            }
        }

        // Check log level versus the actual log call type (e.g. flag
        //    if (Log.isLoggable(TAG, Log.DEBUG) Log.info(TAG, "something")

        if (logCallName.length() != 1 || isLoggableArguments.length < 2) { // e.g. println
            return;
        }
        PsiExpression isLoggableLevel = isLoggableArguments[1];
        if (isLoggableLevel == null) {
            return;
        }
        String levelString = isLoggableLevel.getText();
        if (isLoggableLevel instanceof PsiReferenceExpression) {
            levelString = ((PsiReferenceExpression)isLoggableLevel).getReferenceName();
        }
        if (levelString == null || levelString.isEmpty()) {
            return;
        }
        char levelChar = Character.toLowerCase(levelString.charAt(0));
        if (logCallName.charAt(0) == levelChar || !context.isEnabled(WRONG_TAG)) {
            return;
        }
        switch (levelChar) {
            case 'd':
            case 'e':
            case 'i':
            case 'v':
            case 'w':
                break;
            default:
                // Some other char; e.g. user passed in a literal value or some
                // local constant or variable alias
                return;
        }
        String expectedCall = String.valueOf(levelChar);
        String message = String.format(
                "Mismatched logging levels: when checking `isLoggable` level `%1$s`, the " +
                "corresponding log call should be `Log.%2$s`, not `Log.%3$s`",
                levelString, expectedCall, logCallName);
        Location location = context.getNameLocation(logCall);
        Location alternate = context.getLocation(isLoggableLevel);
        alternate.setMessage("Conflicting tag");
        location.setSecondary(alternate);
        context.report(WRONG_TAG, isLoggableCall, location, message);
    }
}
