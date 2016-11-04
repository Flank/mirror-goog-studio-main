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

import static com.android.SdkConstants.CLASS_INTENT;
import static com.android.tools.lint.checks.SupportAnnotationDetector.PERMISSION_ANNOTATION;
import static com.android.tools.lint.checks.SupportAnnotationDetector.PERMISSION_ANNOTATION_READ;
import static com.android.tools.lint.checks.SupportAnnotationDetector.PERMISSION_ANNOTATION_WRITE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiLiteral;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiParenthesizedExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * Utility for locating permissions required by an intent or content resolver
 */
public class PermissionFinder {
    /**
     * Operation that has a permission requirement -- such as a method call,
     * a content resolver read or write operation, an intent, etc.
     */
    public enum Operation {
        CALL, ACTION, READ, WRITE;

        /** Prefix to use when describing a name with a permission requirement */
        public String prefix() {
            switch (this) {
                case ACTION:
                    return "by intent";
                case READ:
                    return "to read";
                case WRITE:
                    return "to write";
                case CALL:
                default:
                    return "by";
            }
        }
    }

    /** A permission requirement given a name and operation */
    public static class Result {
        @NonNull public final PermissionRequirement requirement;
        @NonNull public final String name;
        @NonNull public final Operation operation;

        public Result(
                @NonNull Operation operation,
                @NonNull PermissionRequirement requirement,
                @NonNull String name) {
            this.operation = operation;
            this.requirement = requirement;
            this.name = name;
        }
    }

    /**
     * Searches for a permission requirement for the given parameter in the given call
     *
     * @param operation the operation to look up
     * @param parameter the parameter which contains the value which implies the permission
     * @return the result with the permission requirement, or null if nothing is found
     */
    @Nullable
    public static Result findRequiredPermissions(
            @NonNull Operation operation,
            @NonNull PsiElement parameter) {

        // To find the permission required by an intent, we proceed in 3 steps:
        // (1) Locate the parameter in the start call that corresponds to
        //     the Intent
        //
        // (2) Find the place where the intent is initialized, and figure
        //     out the action name being passed to it.
        //
        // (3) Find the place where the action is defined, and look for permission
        //     annotations on that action declaration!

        return new PermissionFinder(operation).search(parameter);
    }

    private PermissionFinder(@NonNull Operation operation) {
        mOperation = operation;
    }

    @NonNull private final Operation mOperation;

    @Nullable
    public Result search(@NonNull PsiElement node) {
        if (node instanceof PsiLiteral && "null".equals(node.getText())) {
            return null;
        } else if (node instanceof PsiConditionalExpression) {
            PsiConditionalExpression expression = (PsiConditionalExpression) node;
            if (expression.getThenExpression() != null) {
                Result result = search(expression.getThenExpression());
                if (result != null) {
                    return result;
                }
            }
            if (expression.getElseExpression() != null) {
                Result result = search(expression.getElseExpression());
                if (result != null) {
                    return result;
                }
            }
        } else if (node instanceof PsiTypeCastExpression) {
            PsiTypeCastExpression cast = (PsiTypeCastExpression) node;
            PsiExpression operand = cast.getOperand();
            if (operand != null) {
                return search(operand);
            }
        } else if (node instanceof PsiParenthesizedExpression) {
            PsiParenthesizedExpression parens = (PsiParenthesizedExpression) node;
            PsiExpression expression = parens.getExpression();
            if (expression != null) {
                return search(expression);
            }
        } else if (node instanceof PsiNewExpression && mOperation == Operation.ACTION) {
            // Identifies "new Intent(argument)" calls and, if found, continues
            // resolving the argument instead looking for the action definition
            PsiNewExpression call = (PsiNewExpression) node;
            PsiJavaCodeReferenceElement classReference = call.getClassReference();
            String type = classReference != null ? classReference.getQualifiedName() : null;
            if (CLASS_INTENT.equals(type)) {
                PsiExpressionList argumentList = call.getArgumentList();
                if (argumentList != null) {
                    PsiExpression[] expressions = argumentList.getExpressions();
                    if (expressions.length > 0) {
                        PsiExpression action = expressions[0];
                        if (action != null) {
                            return search(action);
                        }
                    }
                }
            }
            return null;
        } else if (node instanceof PsiReferenceExpression) {
            PsiElement resolved = ((PsiReferenceExpression) node).resolve();
            if (resolved instanceof PsiField) {
                PsiField field = (PsiField) resolved;
                if (mOperation == Operation.ACTION) {
                    PsiModifierList modifierList = field.getModifierList();
                    PsiAnnotation annotation = modifierList != null
                            ? modifierList.findAnnotation(PERMISSION_ANNOTATION) : null;
                    if (annotation != null) {
                        return getPermissionRequirement(field, annotation);
                    }
                } else if (mOperation == Operation.READ || mOperation == Operation.WRITE) {
                    String fqn = mOperation == Operation.READ
                            ? PERMISSION_ANNOTATION_READ : PERMISSION_ANNOTATION_WRITE;
                    PsiModifierList modifierList = field.getModifierList();
                    PsiAnnotation annotation = modifierList != null
                            ? modifierList.findAnnotation(fqn) : null;
                    if (annotation != null) {
                        PsiNameValuePair[] attributes = annotation.getParameterList().getAttributes();
                        PsiNameValuePair o = attributes.length == 1 ? attributes[0] : null;
                        if (o != null && o.getValue() instanceof PsiAnnotation) {
                            annotation = (PsiAnnotation) o.getValue();
                            if (PERMISSION_ANNOTATION.equals(annotation.getQualifiedName())) {
                                return getPermissionRequirement(field, annotation);
                            }
                        } else {
                            // The complex annotations used for read/write cannot be
                            // expressed in the external annotations format, so they're inlined.
                            // (See Extractor.AnnotationData#write).
                            //
                            // Instead we've inlined the fields of the annotation on the
                            // outer one:
                            return getPermissionRequirement(field, annotation);
                        }
                    }
                } else {
                    assert false : mOperation;
                }
            } else if (resolved instanceof PsiLocalVariable) {
                PsiLocalVariable variable = (PsiLocalVariable) resolved;
                String targetName = variable.getName();
                PsiStatement statement = PsiTreeUtil.getParentOfType(node, PsiStatement.class, false);
                if (statement != null && targetName != null) {
                    PsiStatement prev = PsiTreeUtil.getPrevSiblingOfType(statement,
                            PsiStatement.class);

                    while (prev != null) {
                        if (prev instanceof PsiDeclarationStatement) {
                            for (PsiElement element : ((PsiDeclarationStatement) prev)
                                    .getDeclaredElements()) {
                                if (variable.equals(element)) {
                                    if (variable.getInitializer() != null) {
                                        return search(variable.getInitializer());
                                    } else {
                                        break;
                                    }
                                }
                            }
                        } else if (prev instanceof PsiExpressionStatement) {
                            PsiExpression expression = ((PsiExpressionStatement) prev)
                                    .getExpression();
                            if (expression instanceof PsiAssignmentExpression) {
                                PsiAssignmentExpression assign
                                        = (PsiAssignmentExpression) expression;
                                PsiExpression lhs = assign.getLExpression();
                                if (lhs instanceof PsiReferenceExpression) {
                                    PsiReferenceExpression reference = (PsiReferenceExpression) lhs;
                                    if (targetName.equals(reference.getReferenceName()) &&
                                            reference.getQualifier() == null) {
                                        if (assign.getRExpression() != null) {
                                            return search(assign.getRExpression());
                                        } else {
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                        prev = PsiTreeUtil.getPrevSiblingOfType(prev,
                                PsiStatement.class);
                    }
                }
            }
        }

        return null;
    }

    @NonNull
    private Result getPermissionRequirement(
            @NonNull PsiField field,
            @NonNull PsiAnnotation annotation) {
        PermissionRequirement requirement = PermissionRequirement.create(annotation);
        PsiClass containingClass = field.getContainingClass();
        String name = containingClass != null
                ? containingClass.getName() + "." + field.getName()
                : field.getName();
        assert name != null;
        return new Result(mOperation, requirement, name);
    }
}
