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

import static com.android.SdkConstants.ATTR_VALUE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.ResourceReference;
import com.google.common.collect.Lists;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiVariable;
import java.lang.reflect.Field;
import java.util.List;
import org.jetbrains.uast.UAnnotation;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UDeclaration;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UFile;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UPrefixExpression;
import org.jetbrains.uast.UQualifiedReferenceExpression;
import org.jetbrains.uast.UReferenceExpression;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.jetbrains.uast.UUnaryExpression;
import org.jetbrains.uast.UVariable;
import org.jetbrains.uast.UastContext;
import org.jetbrains.uast.UastPrefixOperator;
import org.jetbrains.uast.UastUtils;
import org.jetbrains.uast.util.UastExpressionUtils;

public class UastLintUtils {

    /** Returns the containing file for the given element */
    @Nullable
    public static PsiFile getContainingFile(
            @NonNull JavaContext context,
            @Nullable PsiElement element) {
        if (element == null) {
            return null;
        }

        PsiFile containingFile = element.getContainingFile();
        if (!containingFile.equals(context.getPsiFile())) {
            return getContainingFile(element);
        }

        return containingFile;
    }

    /** Returns the containing file for the given element */
    @Nullable
    public static PsiFile getPsiFile(@Nullable UFile file) {
        if (file == null) {
            return null;
        }

        return getContainingFile(file.getPsi());
    }

    /** Returns the containing file for the given element */
    @Nullable
    public static PsiFile getContainingFile(@Nullable PsiElement element) {
        if (element == null) {
            return null;
        }

        PsiFile containingFile =
                element instanceof PsiFile ? (PsiFile) element : element.getContainingFile();

        // In Kotlin files identifiers are sometimes using LightElements that are hosted in
        // a dummy file, these do not have the right PsiFile as containing elements
        Class<?> cls = containingFile.getClass();
        String name = cls.getName();
        if (name.startsWith(
                "org.jetbrains.kotlin.asJava.classes.KtLightClassForSourceDeclaration")) {
            try {
                Field declaredField = cls.getSuperclass().getDeclaredField("ktFile");
                declaredField.setAccessible(true);
                Object o = declaredField.get(containingFile);
                if (o instanceof PsiFile) {
                    return (PsiFile) o;
                }
            } catch (Throwable ignore) {
            }
        }

        return containingFile;
    }

    @Nullable
    public static String getQualifiedName(PsiElement element) {
        if (element instanceof PsiClass) {
            return ((PsiClass) element).getQualifiedName();
        } else if (element instanceof PsiMethod) {
            PsiClass containingClass = ((PsiMethod) element).getContainingClass();
            if (containingClass == null) {
                return null;
            }
            String containingClassFqName = getQualifiedName(containingClass);
            if (containingClassFqName == null) {
                return null;
            }
            return containingClassFqName + "." + ((PsiMethod) element).getName();
        } else if (element instanceof PsiField) {
            PsiClass containingClass = ((PsiField) element).getContainingClass();
            if (containingClass == null) {
                return null;
            }
            String containingClassFqName = getQualifiedName(containingClass);
            if (containingClassFqName == null) {
                return null;
            }
            return containingClassFqName + "." + ((PsiField) element).getName();
        } else {
            return null;
        }
    }

    @Nullable
    public static PsiElement resolve(ExternalReferenceExpression expression, UElement context) {
        UDeclaration declaration = UastUtils.getParentOfType(context, UDeclaration.class);
        if (declaration == null) {
            return null;
        }

        return expression.resolve(declaration.getPsi());
    }

    @NonNull
    public static String getClassName(PsiClassType type) {
        PsiClass psiClass = type.resolve();
        if (psiClass == null) {
            return type.getClassName();
        } else {
            return getClassName(psiClass);
        }
    }

    @NonNull
    public static String getClassName(PsiClass psiClass) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(psiClass.getName());
        psiClass = psiClass.getContainingClass();
        while (psiClass != null) {
            stringBuilder.insert(0, psiClass.getName() + ".");
            psiClass = psiClass.getContainingClass();
        }
        return stringBuilder.toString();
    }

    @Nullable
    public static UExpression findLastAssignment(
            @NonNull PsiVariable variable,
            @NonNull UElement call) {
        UElement lastAssignment = null;

        if (variable instanceof UVariable) {
            variable = ((UVariable) variable).getPsi();
        }

        if (!variable.hasModifierProperty(PsiModifier.FINAL) &&
                (variable instanceof PsiLocalVariable || variable instanceof PsiParameter)) {
            UMethod containingFunction = UastUtils.getContainingUMethod(call);
            if (containingFunction != null) {
                UastContext context = UastUtils.getUastContext(call);
                ConstantEvaluator.LastAssignmentFinder finder =
                        new ConstantEvaluator.LastAssignmentFinder(variable, call, context, null, -1);
                containingFunction.accept(finder);
                lastAssignment = finder.getLastAssignment();
            }
        } else {
            UastContext context = UastUtils.getUastContext(call);
            lastAssignment = context.getInitializerBody(variable);
        }

        if (lastAssignment instanceof UExpression) {
            return (UExpression) lastAssignment;
        }

        return null;
    }

    @Nullable
    public static String getReferenceName(UReferenceExpression expression) {
        if (expression instanceof USimpleNameReferenceExpression) {
            return ((USimpleNameReferenceExpression) expression).getIdentifier();
        } else if (expression instanceof UQualifiedReferenceExpression) {
            UExpression selector = ((UQualifiedReferenceExpression) expression).getSelector();
            if (selector instanceof USimpleNameReferenceExpression) {
                return ((USimpleNameReferenceExpression) selector).getIdentifier();
            }
        }

        return null;
    }

    @Nullable
    public static Object findLastValue(
            @NonNull PsiVariable variable,
            @NonNull UElement call,
            @NonNull UastContext context,
            @NonNull ConstantEvaluator evaluator) {
        Object value = null;

        if (!variable.hasModifierProperty(PsiModifier.FINAL) &&
                (variable instanceof PsiLocalVariable || variable instanceof PsiParameter)) {
            UMethod containingFunction = UastUtils.getContainingUMethod(call);
            if (containingFunction != null) {
                ConstantEvaluator.LastAssignmentFinder
                        finder = new ConstantEvaluator.LastAssignmentFinder(
                        variable, call, context, evaluator, 1);
                containingFunction.getUastBody().accept(finder);
                value = finder.getCurrentValue();
            }
        } else {
            UExpression initializer = context.getInitializerBody(variable);
            if (initializer != null) {
                value = initializer.evaluate();
            }
        }

        return value;
    }

    @Nullable
    public static ResourceReference toAndroidReferenceViaResolve(UElement element) {
        return ResourceReference.Companion.get(element);
    }

    public static boolean areIdentifiersEqual(UExpression first, UExpression second) {
        String firstIdentifier = getIdentifier(first);
        String secondIdentifier = getIdentifier(second);
        return firstIdentifier != null && secondIdentifier != null
                && firstIdentifier.equals(secondIdentifier);
    }

    @Nullable
    public static String getIdentifier(UExpression expression) {
        if (expression instanceof ULiteralExpression) {
            expression.asRenderString();
        } else if (expression instanceof USimpleNameReferenceExpression) {
            return ((USimpleNameReferenceExpression) expression).getIdentifier();
        } else if (expression instanceof UQualifiedReferenceExpression) {
            UQualifiedReferenceExpression qualified = (UQualifiedReferenceExpression) expression;
            String receiverIdentifier = getIdentifier(qualified.getReceiver());
            String selectorIdentifier = getIdentifier(qualified.getSelector());
            if (receiverIdentifier == null || selectorIdentifier == null) {
                return null;
            }
            return receiverIdentifier + "." + selectorIdentifier;
        }

        return null;
    }

    public static boolean isNumber(@NonNull UElement argument) {
        if (argument instanceof ULiteralExpression) {
            Object value = ((ULiteralExpression) argument).getValue();
            return value instanceof Number;
        } else if (argument instanceof UPrefixExpression) {
            UPrefixExpression expression = (UPrefixExpression) argument;
            UExpression operand = expression.getOperand();
            return isNumber(operand);
        } else {
            return false;
        }
    }

    public static boolean isZero(@NonNull UElement argument) {
        if (argument instanceof ULiteralExpression) {
            Object value = ((ULiteralExpression) argument).getValue();
            return value instanceof Number && ((Number)value).intValue() == 0;
        }
        return false;
    }

    public static boolean isMinusOne(@NonNull UElement argument) {
        if (argument instanceof UUnaryExpression) {
            UUnaryExpression expression = (UUnaryExpression) argument;
            UExpression operand = expression.getOperand();
            if (operand instanceof ULiteralExpression &&
                    expression.getOperator() == UastPrefixOperator.UNARY_MINUS) {
                Object value = ((ULiteralExpression) operand).getValue();
                return value instanceof Number && ((Number) value).intValue() == 1;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    @Nullable
    public static UExpression getAnnotationValue(@NonNull UAnnotation annotation) {
        UExpression value = annotation.findDeclaredAttributeValue(ATTR_VALUE);
        if (value == null) {
            value = annotation.findDeclaredAttributeValue(null);
        }

        return value;
    }

    public static long getLongAttribute(@NonNull JavaContext context,
            @NonNull UAnnotation annotation,
            @NonNull String name, long defaultValue) {
        Long value = getAnnotationLongValue(annotation, name);
        if (value != null) {
            return value;
        }

        return defaultValue;
    }

    public static double getDoubleAttribute(@NonNull JavaContext context,
            @NonNull UAnnotation annotation,
            @NonNull String name, double defaultValue) {
        Double value = getAnnotationDoubleValue(annotation, name);
        if (value != null) {
            return value;
        }

        return defaultValue;
    }

    public static boolean getBoolean(@NonNull JavaContext context,
            @NonNull UAnnotation annotation,
            @NonNull String name, boolean defaultValue) {
        Boolean value = getAnnotationBooleanValue(annotation, name);
        if (value != null) {
            return value;
        }

        return defaultValue;
    }

    @Nullable
    public static Boolean getAnnotationBooleanValue(
            @Nullable UAnnotation annotation,
            @NonNull String name) {
        if (annotation != null) {
            UExpression attributeValue = annotation.findDeclaredAttributeValue(name);
            if (attributeValue == null && ATTR_VALUE.equals(name)) {
                attributeValue = annotation.findDeclaredAttributeValue(null);
            }
            // Use constant evaluator since we want to resolve field references as well
            if (attributeValue != null) {
                Object o = ConstantEvaluator.evaluate(null, attributeValue);
                if (o instanceof Boolean) {
                    return (Boolean) o;
                }
            }
        }

        return null;
    }

    public static boolean getAnnotationBooleanValue(
            @Nullable UAnnotation annotation,
            @NonNull String name,
            boolean defaultValue) {
        Boolean value = getAnnotationBooleanValue(annotation, name);
        if (value != null) {
            return value;
        }
        return defaultValue;
    }

    @Nullable
    public static Long getAnnotationLongValue(
            @Nullable UAnnotation annotation,
            @NonNull String name) {
        if (annotation != null) {
            UExpression attributeValue = annotation.findDeclaredAttributeValue(name);
            if (attributeValue == null && ATTR_VALUE.equals(name)) {
                attributeValue = annotation.findDeclaredAttributeValue(null);
            }
            // Use constant evaluator since we want to resolve field references as well
            if (attributeValue != null) {
                Object o = ConstantEvaluator.evaluate(null, attributeValue);
                if (o instanceof Number) {
                    return ((Number)o).longValue();
                }
            }
        }

        return null;
    }

    public static long getAnnotationLongValue(
            @Nullable UAnnotation annotation,
            @NonNull String name,
            long defaultValue) {
        Long value = getAnnotationLongValue(annotation, name);
        if (value != null) {
            return value;
        }
        return defaultValue;
    }

    @Nullable
    public static Double getAnnotationDoubleValue(
            @Nullable UAnnotation annotation,
            @NonNull String name) {
        if (annotation != null) {
            UExpression attributeValue = annotation.findDeclaredAttributeValue(name);
            if (attributeValue == null && ATTR_VALUE.equals(name)) {
                attributeValue = annotation.findDeclaredAttributeValue(null);
            }
            // Use constant evaluator since we want to resolve field references as well
            if (attributeValue != null) {
                Object o = ConstantEvaluator.evaluate(null, attributeValue);
                if (o instanceof Number) {
                    return ((Number)o).doubleValue();
                }
            }
        }

        return null;
    }

    public static double getAnnotationDoubleValue(
            @Nullable UAnnotation annotation,
            @NonNull String name,
            double defaultValue) {
        Double value = getAnnotationDoubleValue(annotation, name);
        if (value != null) {
            return value;
        }
        return defaultValue;
    }

    @Nullable
    public static String getAnnotationStringValue(
            @Nullable UAnnotation annotation,
            @NonNull String name) {
        if (annotation != null) {
            UExpression attributeValue = annotation.findDeclaredAttributeValue(name);
            if (attributeValue == null && ATTR_VALUE.equals(name)) {
                attributeValue = annotation.findDeclaredAttributeValue(null);
            }
            // Use constant evaluator since we want to resolve field references as well
            if (attributeValue != null) {
                Object o = ConstantEvaluator.evaluate(null, attributeValue);
                if (o instanceof String) {
                    return (String) o;
                }
            }
        }

        return null;
    }

    @Nullable
    public static String[] getAnnotationStringValues(
            @Nullable UAnnotation annotation,
            @NonNull String name) {
        if (annotation != null) {
            UExpression attributeValue = annotation.findDeclaredAttributeValue(name);
            if (attributeValue == null && ATTR_VALUE.equals(name)) {
                attributeValue = annotation.findDeclaredAttributeValue(null);
            }
            if (attributeValue == null) {
                return null;
            }
            if (UastExpressionUtils.isArrayInitializer(attributeValue)) {
                List<UExpression> initializers =
                        ((UCallExpression) attributeValue).getValueArguments();
                List<String> result = Lists.newArrayListWithCapacity(initializers.size());
                ConstantEvaluator constantEvaluator = new ConstantEvaluator(null);
                for (UExpression element : initializers) {
                    Object o = constantEvaluator.evaluate(element);
                    if (o instanceof String) {
                        result.add((String)o);
                    }
                }
                if (result.isEmpty()) {
                    return null;
                } else {
                    return result.toArray(new String[0]);
                }
            } else {
                // Use constant evaluator since we want to resolve field references as well
                Object o = ConstantEvaluator.evaluate(null, attributeValue);
                if (o instanceof String) {
                    return new String[]{(String) o};
                } else if (o instanceof String[]) {
                    return (String[])o;
                } else if (o instanceof Object[]) {
                    Object[] array = (Object[]) o;
                    List<String> strings = Lists.newArrayListWithCapacity(array.length);
                    for (Object element : array) {
                        if (element instanceof String) {
                            strings.add((String) element);
                        }
                    }
                    return strings.toArray(new String[0]);
                }
            }
        }

        return null;
    }

    public static boolean containsAnnotation(
            @NonNull List<UAnnotation> list,
            @NonNull UAnnotation annotation) {
        for (UAnnotation a : list) {
            if (a == annotation) {
                return true;
            }
        }

        return false;
    }

    public static boolean containsAnnotation(
            @NonNull List<UAnnotation> list,
            @NonNull String qualifiedName) {
        for (UAnnotation annotation : list) {
            if (qualifiedName.equals(annotation.getQualifiedName())) {
                return true;
            }
        }

        return false;
    }
}