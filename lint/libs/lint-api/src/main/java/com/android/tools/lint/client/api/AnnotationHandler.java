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

package com.android.tools.lint.client.api;

import static com.android.SdkConstants.ATTR_VALUE;
import static com.android.SdkConstants.SUPPORT_ANNOTATIONS_PREFIX;
import static com.android.tools.lint.detector.api.LintUtils.skipParentheses;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Detector.UastScanner;
import com.android.tools.lint.detector.api.JavaContext;
import com.google.common.collect.Multimap;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiVariable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UAnnotation;
import org.jetbrains.uast.UArrayAccessExpression;
import org.jetbrains.uast.UBinaryExpression;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UEnumConstant;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UNamedExpression;
import org.jetbrains.uast.UQualifiedReferenceExpression;
import org.jetbrains.uast.UReferenceExpression;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.jetbrains.uast.UVariable;
import org.jetbrains.uast.UastUtils;
import org.jetbrains.uast.java.JavaUAnnotation;
import org.jetbrains.uast.util.UastExpressionUtils;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

/**
 * Looks up annotations on method calls and enforces the various things they express.
 */
class AnnotationHandler {
    private Multimap<String, UastScanner> scanners;

    private Set<String> relevantAnnotations = new HashSet<>(50);

    public AnnotationHandler(Multimap<String, UastScanner> scanners) {
        this.scanners = scanners;
        relevantAnnotations.addAll(scanners.keys());
    }

    @NonNull
    Set<String> getRelevantAnnotations() {
        return relevantAnnotations;
    }

    private void checkContextAnnotations(@NonNull JavaContext context, @Nullable PsiMethod method,
            @NonNull UElement call,
            @NonNull List<UAnnotation> allMethodAnnotations) {
        // Handle typedefs and resource types: if you're comparing it, check that
        // it's being compared with something compatible
        UElement p = skipParentheses(call.getUastParent());

        if (p instanceof UQualifiedReferenceExpression) {
            call = p;
            p = p.getUastParent();
        }

        if (p instanceof UBinaryExpression) {
            UExpression check = null;
            UBinaryExpression binary = (UBinaryExpression) p;
            if (call == binary.getLeftOperand()) {
                check = binary.getRightOperand();
            } else if (call == binary.getRightOperand()) {
                check = binary.getLeftOperand();
            }
            if (check != null) {
                checkAnnotations(context, check, method, allMethodAnnotations,
                        Collections.emptyList(), Collections.emptyList());
            }
        } else if (p instanceof UQualifiedReferenceExpression) {
            // Handle equals() as a special case: if you're invoking
            //   .equals on a method whose return value annotated with @StringDef
            //   we want to make sure that the equals parameter is compatible.
            // 186598: StringDef don't warn using a getter and equals
            UQualifiedReferenceExpression ref = (UQualifiedReferenceExpression) p;
            if ("equals".equals(ref.getResolvedName())) {
                UExpression selector = ref.getSelector();
                if (selector instanceof UCallExpression) {
                    List<UExpression> arguments = ((UCallExpression) selector).getValueArguments();
                    if (arguments.size() == 1) {
                        checkAnnotations(context, arguments.get(0), method,
                                allMethodAnnotations,
                                Collections.emptyList(), Collections.emptyList());
                    }
                }
            }
        } else if (UastExpressionUtils.isAssignment(p)) {
            //noinspection ConstantConditions
            UBinaryExpression assignment = (UBinaryExpression) p;
            //noinspection ConstantConditions
            UExpression rExpression = assignment.getRightOperand();
            checkAnnotations(context, rExpression, method,
                    allMethodAnnotations, Collections.emptyList(), Collections.emptyList());
        } else if (call instanceof UVariable) {
            UVariable variable = (UVariable) call;
            PsiElement variablePsi = call.getPsi();
            UMethod containingMethod = UastUtils.getContainingUMethod(call);
            if (containingMethod != null) { // TODO: What about fields
                containingMethod.accept(new AbstractUastVisitor() {
                    @Override
                    public boolean visitSimpleNameReferenceExpression(
                            @NonNull USimpleNameReferenceExpression node) {
                        PsiElement resolved = node.resolve();
                        if (variable.equals(resolved) ||
                                variablePsi != null && variablePsi.equals(resolved)) {
                            UExpression expression = UastUtils
                                    .getParentOfType(node, UExpression.class, true);
                            //noinspection VariableNotUsedInsideIf
                            if (expression != null) {
                                UExpression inner = UastUtils.getParentOfType(node, UExpression.class, false);
                                checkAnnotations(context, inner, method,
                                        allMethodAnnotations, Collections.emptyList(),
                                        Collections.emptyList());
                                return false;
                            }

                            // TODO: if the reference is the LHS Of an assignment
                            //   UastExpressionUtils.isAssignment(expression)
                            // then assert the annotations on to the right hand side
                        }
                        return super.visitSimpleNameReferenceExpression(node);
                    }
                });
            }

            UExpression initializer = variable.getUastInitializer();
            if (initializer != null) {
                checkAnnotations(context, initializer, null,
                        allMethodAnnotations, Collections.emptyList(),
                        Collections.emptyList());
            }
        }
    }

    private void checkAnnotations(
            @NonNull JavaContext context,
            @NonNull UElement argument,
            @Nullable PsiMethod method,
            @NonNull List<UAnnotation> annotations,
            @NonNull List<UAnnotation> allMethodAnnotations,
            @NonNull List<UAnnotation> allClassAnnotations) {

        for (UAnnotation annotation : annotations) {
            String signature = annotation.getQualifiedName();
            if (signature == null) {
                continue;
            }

            Collection<UastScanner> uastScanners = scanners.get(signature);
            if (uastScanners != null) {
                List<UAnnotation> packageAnnotations = Collections.emptyList();
                for (UastScanner scanner : uastScanners) {
                    scanner.visitAnnotationUsage(context, argument, annotation, signature,
                            method, annotations, allMethodAnnotations, allClassAnnotations,
                            packageAnnotations);
                }
            }
        }
    }

    // TODO: visitField too such that we can enforce initializer consistency with
    // declared constraints!

    void visitMethod(@NonNull JavaContext context, @NonNull UMethod method) {
        JavaEvaluator evaluator = context.getEvaluator();
        PsiAnnotation[] methodAnnotations =
                filterRelevantAnnotations(evaluator, evaluator.getAllAnnotations(method, true));
        if (methodAnnotations.length > 0) {
            List<UAnnotation> annotations = JavaUAnnotation.wrap(methodAnnotations);

            // Check return values
            method.accept(new AbstractUastVisitor() {
                @Override
                public boolean visitReturnExpression(UReturnExpression node) {
                    UExpression returnValue = node.getReturnExpression();
                    if (returnValue != null) {
                        checkAnnotations(context, returnValue, method,
                                annotations, annotations, Collections.emptyList());
                    }
                    return super.visitReturnExpression(node);
                }
            });
        }
    }

    void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression call) {
        PsiMethod method = call.resolve();
        if (method != null) {
            checkCall(context, method, call);
        }
    }

    void visitAnnotation(@NonNull JavaContext context, @NonNull UAnnotation annotation) {
        // Check annotation references; these are a form of method call
        String qualifiedName = annotation.getQualifiedName();
        if (qualifiedName == null || qualifiedName.startsWith("java.") ||
                qualifiedName.startsWith(SUPPORT_ANNOTATIONS_PREFIX)) {
            return;
        }

        List<UNamedExpression> attributeValues = annotation.getAttributeValues();
        if (attributeValues.isEmpty()) {
            return;
        }

        PsiClass resolved = annotation.resolve();
        if (resolved == null) {
            return;
        }

        for (UNamedExpression expression : attributeValues) {
            String name = expression.getName();
            if (name == null) {
                name = ATTR_VALUE;
            }
            PsiMethod[] methods = resolved.findMethodsByName(name, false);
            if (methods.length == 1) {
                PsiMethod method = methods[0];
                JavaEvaluator evaluator = context.getEvaluator();
                PsiAnnotation[] methodAnnotations =
                        filterRelevantAnnotations(evaluator,
                                evaluator.getAllAnnotations(method, true));
                if (methodAnnotations.length > 0) {
                    UExpression value = expression.getExpression();
                    List<UAnnotation> annotations = JavaUAnnotation.wrap(methodAnnotations);
                    checkAnnotations(context, value, method,
                            annotations, annotations, Collections.emptyList());
                }
            }
        }
    }

    void visitEnumConstant(@NonNull JavaContext context, @NonNull UEnumConstant constant) {
        PsiMethod method = constant.resolveMethod();
        if (method != null) {
            checkCall(context, method, constant);
        }
    }

    void visitArrayAccessExpression(@NonNull JavaContext context,
            @NonNull UArrayAccessExpression expression) {
        UExpression arrayExpression = expression.getReceiver();
        if (arrayExpression instanceof UReferenceExpression) {
            PsiElement resolved = ((UReferenceExpression) arrayExpression).resolve();
            if (resolved instanceof PsiModifierListOwner) {
                JavaEvaluator evaluator = context.getEvaluator();
                PsiAnnotation[] methodAnnotations =
                        evaluator.getAllAnnotations((PsiModifierListOwner) resolved, true);
                methodAnnotations = filterRelevantAnnotations(evaluator, methodAnnotations);
                if (methodAnnotations.length > 0) {
                    checkContextAnnotations(context, null, expression,
                            JavaUAnnotation.wrap(methodAnnotations));
                }
            }
        }
    }

    void visitVariable(@NonNull JavaContext context, @NotNull UVariable variable) {
        JavaEvaluator evaluator = context.getEvaluator();
        PsiAnnotation[] methodAnnotations;
        PsiVariable psi = variable.getPsi();
        if (psi != null) {
            methodAnnotations = filterRelevantAnnotations(evaluator,
                    evaluator.getAllAnnotations(psi, true));
        } else {
            methodAnnotations = filterRelevantAnnotations(evaluator,
                    evaluator.getAllAnnotations(variable, true));
        }
        if (methodAnnotations.length > 0) {
            List<UAnnotation> annotations = JavaUAnnotation.wrap(methodAnnotations);
            checkContextAnnotations(context, null, variable, annotations);
        }
    }

    private void checkCall(
            @NonNull JavaContext context,
            @NonNull PsiMethod method,
            @NonNull UCallExpression call) {
        JavaEvaluator evaluator = context.getEvaluator();
        List<UAnnotation> methodAnnotations;
        {
            PsiAnnotation[] annotations = evaluator.getAllAnnotations(method, true);
            methodAnnotations = JavaUAnnotation
                    .wrap(filterRelevantAnnotations(evaluator, annotations));
        }

        // Look for annotations on the class as well: these trickle
        // down to all the methods in the class
        PsiClass containingClass = method.getContainingClass();
        List<UAnnotation> classAnnotations;
        List<UAnnotation> pkgAnnotations;
        if (containingClass != null) {
            PsiAnnotation[] annotations = evaluator.getAllAnnotations(containingClass, true);
            classAnnotations = JavaUAnnotation
                    .wrap(filterRelevantAnnotations(evaluator, annotations));

            PsiPackage pkg = evaluator.getPackage(containingClass);
            if (pkg != null) {
                PsiAnnotation[] annotations2 = evaluator.getAllAnnotations(pkg, false);
                pkgAnnotations = JavaUAnnotation
                        .wrap(filterRelevantAnnotations(evaluator, annotations2));
            } else {
                pkgAnnotations = Collections.emptyList();
            }
        } else {
            classAnnotations = Collections.emptyList();
            pkgAnnotations = Collections.emptyList();
        }

        if (!methodAnnotations.isEmpty()) {
            checkAnnotations(context, call, method, methodAnnotations,
                    methodAnnotations, classAnnotations);

            checkContextAnnotations(context, method, call, methodAnnotations);
        }

        if (!classAnnotations.isEmpty()) {
            checkAnnotations(context, call, method, classAnnotations,
                    methodAnnotations, classAnnotations);
        }

        if (!pkgAnnotations.isEmpty()) {
            checkAnnotations(context, call, method, pkgAnnotations,
                    methodAnnotations, classAnnotations);
        }

        List<UExpression> arguments = call.getValueArguments();
        PsiParameterList parameterList = method.getParameterList();
        PsiParameter[] parameters = parameterList.getParameters();

        List<UAnnotation> annotations = null;
        int j = 0;
        if (parameters.length > 0 && "$receiver".equals(parameters[0].getName())) {
            // Kotlin extension method.
            // TODO: Find out if there's a better way to look this up!
            // (and more importantly, handle named parameters, *args, etc.
            j++;
        }
        for (int i = 0, n = Math.min(parameters.length, arguments.size());
                j < n;
                i++, j++) {
            UExpression argument = arguments.get(i);
            PsiParameter parameter = parameters[j];
            PsiAnnotation[] allAnnotations = evaluator.getAllAnnotations(parameter, true);
            PsiAnnotation[] filtered = filterRelevantAnnotations(evaluator, allAnnotations);
            if (filtered.length == 0) {
                continue;
            }
            annotations = JavaUAnnotation.wrap(filtered);
            checkAnnotations(context, argument, method, annotations,
                    methodAnnotations, classAnnotations);
        }
        if (annotations != null) {
            // last parameter is varargs (same parameter annotations)
            for (int i = parameters.length; i < arguments.size(); i++) {
                UExpression argument = arguments.get(i);
                checkAnnotations(context, argument, method, annotations,
                        methodAnnotations, classAnnotations);
            }
        }
    }

    @NonNull
    PsiAnnotation[] filterRelevantAnnotations(
            @NonNull JavaEvaluator evaluator, @NonNull PsiAnnotation[] annotations) {
        List<PsiAnnotation> result = null;
        int length = annotations.length;
        if (length == 0) {
            return annotations;
        }
        for (PsiAnnotation annotation : annotations) {
            String signature = annotation.getQualifiedName();
            if (signature == null ||
                    signature.startsWith("java.") && !relevantAnnotations.contains(signature)) {
                // @Override, @SuppressWarnings etc. Ignore
                continue;
            }

            if (relevantAnnotations.contains(signature)) {
                // Common case: there's just one annotation; no need to create a list copy
                if (length == 1) {
                    return annotations;
                }
                if (result == null) {
                    result = new ArrayList<>(2);
                }
                result.add(annotation);
                continue;
            }

            // Special case @IntDef and @StringDef: These are used on annotations
            // themselves. For example, you create a new annotation named @foo.bar.Baz,
            // annotate it with @IntDef, and then use @foo.bar.Baz in your signatures.
            // Here we want to map from @foo.bar.Baz to the corresponding int def.
            // Don't need to compute this if performing @IntDef or @StringDef lookup
            PsiJavaCodeReferenceElement ref = annotation.getNameReferenceElement();
            if (ref == null) {
                continue;
            }
            PsiElement resolved = ref.resolve();
            if (!(resolved instanceof PsiClass) || !((PsiClass) resolved).isAnnotationType()) {
                continue;
            }
            PsiClass cls = (PsiClass) resolved;
            PsiAnnotation[] innerAnnotations = evaluator.getAllAnnotations(cls, false);
            for (int j = 0; j < innerAnnotations.length; j++) {
                PsiAnnotation inner = innerAnnotations[j];
                String a = inner.getQualifiedName();
                if (a != null && relevantAnnotations.contains(a)) {
                    if (length == 1 && j == innerAnnotations.length - 1 && result == null) {
                        return innerAnnotations;
                    }
                    if (result == null) {
                        result = new ArrayList<>(2);
                    }
                    result.add(inner);
                }
            }
        }

        return result != null
                ? result.toArray(PsiAnnotation.EMPTY_ARRAY) : PsiAnnotation.EMPTY_ARRAY;
    }
}
