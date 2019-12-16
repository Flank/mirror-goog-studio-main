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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiType;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReferenceExpression;
import org.jetbrains.uast.UVariable;
import org.jetbrains.uast.util.UastExpressionUtils;

/**
 * Evaluates the types of nodes. It analyzes the flow and for example figures out that if you ask
 * for the type of {@code var} in this code snippet:
 *
 * <pre>
 *     Object o = new StringBuilder();
 *     Object var = o;
 * </pre>
 *
 * it will return "java.lang.StringBuilder".
 *
 * <p><b>NOTE:</b> This type evaluator does not (yet) compute the correct types when involving
 * implicit type conversions, so be careful if using this for primitives; e.g. for "int * long" it
 * might return the type "int".
 */
public class TypeEvaluator {
    private final JavaContext context;

    /**
     * Creates a new constant evaluator
     *
     * @param context the context to use to resolve field references, if any
     */
    public TypeEvaluator(@Nullable JavaContext context) {
        this.context = context;
    }

    /** Returns the inferred type of the given node */
    @Nullable
    public PsiType evaluate(@Nullable PsiElement node) {
        if (node == null) {
            return null;
        }

        PsiElement resolved = null;
        if (node instanceof PsiReference) {
            resolved = ((PsiReference) node).resolve();
        }
        if (resolved instanceof PsiMethod) {
            PsiMethod method = (PsiMethod) resolved;
            if (method.isConstructor()) {
                PsiClass containingClass = method.getContainingClass();
                if (containingClass != null && context != null) {
                    return context.getEvaluator().getClassType(containingClass);
                }
            } else {
                return method.getReturnType();
            }
        }

        if (resolved instanceof PsiField) {
            PsiField field = (PsiField) resolved;
            if (field.getInitializer() != null) {
                PsiType type = evaluate(field.getInitializer());
                if (type != null) {
                    return type;
                }
            }
            return field.getType();
        } else if (resolved instanceof PsiLocalVariable) {
            PsiLocalVariable variable = (PsiLocalVariable) resolved;
            PsiExpression last = ConstantEvaluator.findLastAssignment(node, variable);
            if (last != null) {
                return evaluate(last);
            }

            return variable.getType();
        } else if (node instanceof PsiExpression) {
            PsiExpression expression = (PsiExpression) node;
            return expression.getType();
        }

        return null;
    }

    @Nullable
    public static PsiType evaluate(@Nullable UElement node) {
        if (node == null) {
            return null;
        }

        UElement resolved = node;
        if (resolved instanceof UReferenceExpression) {
            resolved = UastLintUtils.tryResolveUDeclaration(resolved);
        }

        if (resolved instanceof UMethod) {
            return ((UMethod) resolved).getPsi().getReturnType();
        } else if (resolved instanceof UVariable) {
            UVariable variable = (UVariable) resolved;
            UElement lastAssignment = UastLintUtils.findLastAssignment(variable, node);
            if (lastAssignment != null) {
                return evaluate(lastAssignment);
            }
            return variable.getType();
        } else if (resolved instanceof UCallExpression) {
            if (UastExpressionUtils.isMethodCall(resolved)) {
                PsiMethod resolvedMethod = ((UCallExpression) resolved).resolve();
                return resolvedMethod != null ? resolvedMethod.getReturnType() : null;
            } else {
                return ((UCallExpression) resolved).getExpressionType();
            }
        } else if (resolved instanceof UExpression) {
            return ((UExpression) resolved).getExpressionType();
        }

        return null;
    }

    /**
     * Evaluates the given node and returns the likely type of the instance. Convenience wrapper
     * which creates a new {@linkplain TypeEvaluator}, evaluates the node and returns the result.
     *
     * @param context the context to use to resolve field references, if any
     * @param node the node to compute the type for
     * @return the corresponding type descriptor, if found
     */
    @Nullable
    public static PsiType evaluate(@NonNull JavaContext context, @NonNull PsiElement node) {
        return new TypeEvaluator(context).evaluate(node);
    }
}
