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

package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ClassContext;
import com.android.tools.lint.detector.api.ConstantEvaluator;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.TypeEvaluator;
import com.android.tools.lint.detector.api.UastLintUtils;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClassLiteralExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULocalVariable;
import org.jetbrains.uast.UQualifiedReferenceExpression;
import org.jetbrains.uast.UReferenceExpression;
import org.jetbrains.uast.UTypeReferenceExpression;

/**
 * Checks that the code is not using reflection to access hidden Android APIs
 */
public class PrivateApiDetector extends Detector implements Detector.UastScanner {
    public static final String LOAD_CLASS = "loadClass";
    public static final String FOR_NAME = "forName";
    public static final String GET_CLASS = "getClass";
    public static final String GET_DECLARED_METHOD = "getDeclaredMethod";

    private static final Implementation IMPLEMENTATION = new Implementation(
            PrivateApiDetector.class,
            Scope.JAVA_FILE_SCOPE);

    /** Using hidden/private APIs */
    public static final Issue ISSUE = Issue.create(
            "PrivateApi",
            "Using Private APIs",

            ""
                    + "Using reflection to access hidden/private Android APIs is not safe; it "
                    + "will often not work on devices from other vendors, and it may suddenly "
                    + "stop working (if the API is removed) or crash spectacularly (if the API "
                    + "behavior changes, since there are no guarantees for compatibility.)",

            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            IMPLEMENTATION);

    /** Constructs a new {@link PrivateApiDetector} check */
    public PrivateApiDetector() {
    }

    // ---- Implements UastScanner ----

    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList(FOR_NAME, LOAD_CLASS, GET_DECLARED_METHOD);
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        JavaEvaluator evaluator = context.getEvaluator();
        if (LOAD_CLASS.equals(method.getName())) {
            if (evaluator.isMemberInClass(method, "java.lang.ClassLoader")
                    || evaluator.isMemberInClass(method, "dalvik.system.DexFile")) {
                checkLoadClass(context, node);
            }
        } else {
            if (!evaluator.isMemberInClass(method, "java.lang.Class")) {
                return;
            }
            if (GET_DECLARED_METHOD.equals(method.getName())) {
                checkGetDeclaredMethod(context, node);
            } else {
                checkLoadClass(context, node);
            }
        }
    }

    private static void checkGetDeclaredMethod(@NonNull JavaContext context,
            @NonNull UCallExpression call) {
        String cls = getClassFromMemberLookup(call);
        if (cls == null) {
            return;
        }

        if (!(cls.startsWith("com.android.") || cls.startsWith("android."))) {
            return;
        }

        List<UExpression> arguments = call.getValueArguments();
        if (arguments.isEmpty()) {
            return;
        }
        String methodName = ConstantEvaluator.evaluateString(context, arguments.get(0), false);

        PsiClass aClass = context.getEvaluator().findClass(cls);
        if (aClass == null) {
            return;
        }

        // TODO: Fields?
        PsiMethod[] methodsByName = aClass.findMethodsByName(methodName, true);
        if (methodsByName.length == 0) {
            Location location = context.getLocation(call);
            context.report(ISSUE, call, location, getErrorMessage());
        }
    }

    @NonNull
    private static String getErrorMessage() {
        return "Accessing internal APIs via reflection is not supported and may "
                        + "not work on all devices or in the future";
    }

    private static void checkLoadClass(@NonNull JavaContext context,
            @NonNull UCallExpression call) {
        List<UExpression> arguments = call.getValueArguments();
        if (arguments.isEmpty()) {
            return;
        }
        Object value = ConstantEvaluator.evaluate(context, arguments.get(0));
        if (!(value instanceof String)) {
            return;
        }
        String cls = (String) value;

        boolean isInternal = false;
        if (cls.startsWith("com.android.internal.")) {
            isInternal = true;
        } else if (cls.startsWith("com.android.")
                || cls.startsWith("android.") && !cls.startsWith("android.support.")) {
            // Attempting to access internal API? Look in two places:
            //  (1) SDK class
            //  (2) API database
            PsiClass aClass = context.getEvaluator().findClass(cls);
            //noinspection VariableNotUsedInsideIf
            if (aClass != null) { // Found in SDK: not internal
                return;
            }
            String owner = ClassContext.getInternalName(cls);
            ApiLookup apiLookup = ApiLookup.get(context.getClient(),
                    context.getMainProject().getBuildTarget());
            if (apiLookup == null) {
                return;
            }
            isInternal = !apiLookup.containsClass(owner);
        }

        if (isInternal) {
            Location location = context.getLocation(call);
            context.report(ISSUE, call, location, getErrorMessage());
        }
    }

    /**
     * Given a Class#getMethodDeclaration or getFieldDeclaration etc call,
     * figure out the corresponding class name the method is being invoked on
     *
     * @param call the {@link Class#getDeclaredMethod(String, Class[])} or {@link
     *             Class#getDeclaredField(String)} call
     * @return the fully qualified name of the class, if found
     */
    @Nullable
    public static String getClassFromMemberLookup(@NonNull UCallExpression call) {
        return findReflectionClass(call.getReceiver());
    }

    @Nullable
    private static String findReflectionClass(@Nullable UElement element) {
        if (element instanceof UQualifiedReferenceExpression &&
                ((UQualifiedReferenceExpression) element)
                        .getSelector() instanceof UCallExpression) {
            return findReflectionClass(((UQualifiedReferenceExpression) element).getSelector());
        }

        if (element instanceof UCallExpression) {
            // Inlined class lookup?
            //   foo.getClass()
            //   Class.forName(cls)
            //   loader.loadClass()

            //PsiMethodCallExpression call = (PsiMethodCallExpression) element;
            //PsiReferenceExpression methodExpression = call.getMethodExpression();
            //String name = methodExpression.getReferenceName();
            UCallExpression call = (UCallExpression) element;
            String name = call.getMethodName();

            if (FOR_NAME.equals(name) || LOAD_CLASS.equals(name)) {
                List<UExpression> arguments = call.getValueArguments();
                if (!arguments.isEmpty()) {
                    return ConstantEvaluator.evaluateString(null, arguments.get(0), false);
                }
            } else if (GET_CLASS.equals(name)) {
                UExpression qualifier = call.getReceiver();
                PsiType qualifierType = TypeEvaluator.evaluate(qualifier);
                if (qualifierType instanceof PsiClassType) {
                    // Called getClass(): return the internal class mapping to the public class?
                    return qualifierType.getCanonicalText();
                }
            }

            // TODO: Are there any other common reflection utility methods (from reflection
            // libraries etc) ?
        } else if (element instanceof UReferenceExpression) {
            // Variable (local, parameter or field) reference
            //   myClass.getDeclaredMethod()
            PsiElement resolved = ((UReferenceExpression) element).resolve();
            if (resolved instanceof ULocalVariable) {
                UExpression expression = UastLintUtils.findLastAssignment((PsiVariable)resolved,
                        element);
                return findReflectionClass(expression);
            }
        } else if (element instanceof UClassLiteralExpression) {
            // Class literal, e.g.
            //   MyClass.class
            UExpression expression = ((UClassLiteralExpression) element).getExpression();
            if (expression instanceof UTypeReferenceExpression) {
                return ((UTypeReferenceExpression) expression).getQualifiedName();
            }
            PsiType type = ((UClassLiteralExpression)element).getExpressionType();
            if (type instanceof PsiClassType) {
                return type.getCanonicalText();
            }
        }

        return null;
    }
}
