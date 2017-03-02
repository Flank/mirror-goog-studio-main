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
package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.ConstantEvaluator;
import com.android.tools.lint.detector.api.JavaContext;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiLiteral;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiPrefixExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.uast.UAnnotation;
import org.jetbrains.uast.UExpression;

public abstract class RangeConstraint {

    @NonNull
    public String describe(@Nullable UExpression argument) {
        assert false;
        return "";
    }

    @Nullable
    public static RangeConstraint create(@NonNull PsiAnnotation annotation) {
        String qualifiedName = annotation.getQualifiedName();
        if (qualifiedName == null) {
            return null;
        }
        switch (qualifiedName) {
            case SupportAnnotationDetector.INT_RANGE_ANNOTATION:
                return IntRangeConstraint.create(annotation);
            case SupportAnnotationDetector.FLOAT_RANGE_ANNOTATION:
                return FloatRangeConstraint.create(annotation);
            case SupportAnnotationDetector.SIZE_ANNOTATION:
                return SizeConstraint.create(annotation);
            default:
                return null;
        }
    }

    @Nullable
    public static RangeConstraint create(
            @NonNull JavaContext context,
            @NonNull UAnnotation annotation) {
        String qualifiedName = annotation.getQualifiedName();
        if (qualifiedName == null) {
            return null;
        }
        switch (qualifiedName) {
            case SupportAnnotationDetector.INT_RANGE_ANNOTATION:
                return IntRangeConstraint.create(annotation);
            case SupportAnnotationDetector.FLOAT_RANGE_ANNOTATION:
                return FloatRangeConstraint.create(annotation);
            case SupportAnnotationDetector.SIZE_ANNOTATION:
                return SizeConstraint.create(annotation);
            default:
                return null;
        }
    }

    @Nullable
    public static RangeConstraint create(@NonNull PsiModifierListOwner owner) {
        PsiModifierList modifierList = owner.getModifierList();
        if (modifierList != null) {
            for (PsiAnnotation annotation : modifierList.getAnnotations()) {
                RangeConstraint constraint = create(annotation);
                // Pick first; they're mutually exclusive
                if (constraint != null) {
                    return constraint;
                }
            }
        }

        return null;
    }

    @Nullable
    public Boolean isValid(@NonNull UExpression argument) {
        return null;
    }

    @Nullable
    protected Number guessSize(@NonNull UExpression argument) {
        System.out.println("IMPLEMENT CONSTRAINT GUESS SIZE");
        //if (argument instanceof PsiLiteral) {
        //    PsiLiteral literal = (PsiLiteral) argument;
        //    Object v = literal.getValue();
        //    if (v instanceof Number) {
        //        return (Number) v;
        //    }
        //} else if (argument instanceof PsiBinaryExpression) {
        //    // For PSI conversion, use this instead:
        //    //Object v = JavaConstantExpressionEvaluator.computeConstantExpression(argument, false);
        //    Object v = ConstantEvaluator.evaluate(null, argument);
        //    if (v instanceof Number) {
        //        return (Number) v;
        //    }
        //} else if (argument instanceof PsiReferenceExpression) {
        //    PsiReferenceExpression ref = (PsiReferenceExpression) argument;
        //    PsiElement resolved = ref.resolve();
        //    if (resolved instanceof PsiField) {
        //        PsiField field = (PsiField) resolved;
        //        PsiExpression initializer = field.getInitializer();
        //        if (initializer != null) {
        //            Number number = guessSize(initializer);
        //            //noinspection VariableNotUsedInsideIf
        //            if (number != null) {
        //                // If we're surrounded by an if check involving the variable, then don't validate
        //                // based on the initial value since it might be clipped to a valid range
        //                PsiIfStatement ifStatement = PsiTreeUtil
        //                        .getParentOfType(argument, PsiIfStatement.class, true);
        //                if (ifStatement != null) {
        //                    PsiExpression condition = ifStatement.getCondition();
        //                    if (comparesReference(resolved, condition)) {
        //                        return null;
        //                    }
        //                }
        //            }
        //            return number;
        //        }
        //    } else if (resolved instanceof PsiLocalVariable) {
        //        PsiLocalVariable variable = (PsiLocalVariable) resolved;
        //        PsiExpression initializer = variable.getInitializer();
        //        if (initializer != null) {
        //            Number number = guessSize(initializer);
        //            //noinspection VariableNotUsedInsideIf
        //            if (number != null) {
        //                // If we're surrounded by an if check involving the variable, then don't validate
        //                // based on the initial value since it might be clipped to a valid range
        //                PsiIfStatement ifStatement = PsiTreeUtil
        //                        .getParentOfType(argument, PsiIfStatement.class, true);
        //                if (ifStatement != null) {
        //                    PsiExpression condition = ifStatement.getCondition();
        //                    if (comparesReference(resolved, condition)) {
        //                        return null;
        //                    }
        //                }
        //            }
        //            return number;
        //        }
        //    }
        //} else if (argument instanceof PsiPrefixExpression) {
        //    PsiPrefixExpression prefix = (PsiPrefixExpression) argument;
        //    if (prefix.getOperationTokenType() == JavaTokenType.MINUS) {
        //        PsiExpression operand = prefix.getOperand();
        //        if (operand != null) {
        //            Number number = guessSize(operand);
        //            if (number != null) {
        //                if (number instanceof Long) {
        //                    return -number.longValue();
        //                } else if (number instanceof Integer) {
        //                    return -number.intValue();
        //                } else if (number instanceof Double) {
        //                    return -number.doubleValue();
        //                } else if (number instanceof Float) {
        //                    return -number.floatValue();
        //                } else if (number instanceof Short) {
        //                    return -number.shortValue();
        //                } else if (number instanceof Byte) {
        //                    return -number.byteValue();
        //                }
        //            }
        //        }
        //    }
        //}
        return null;
    }

    /**
     * Checks whether the given range is compatible with this one.
     * We err on the side of caution. E.g. if we have
     * <pre>
     *    method(x)
     * </pre>
     * and the parameter declaration says that x is between 0 and 10,
     * and then we have a parameter which is known to be in the range 5 to 15,
     * here we consider this a compatible range; we don't flag this as
     * an error. If however, the ranges don't overlap, *then* we complain.
     */
    @Nullable
    public Boolean contains(@NonNull RangeConstraint other) {
        return null;
    }

    private static boolean comparesReference(@NonNull PsiElement reference,
            @Nullable PsiExpression expression) {
        if (expression instanceof PsiBinaryExpression) {
            PsiBinaryExpression binary = (PsiBinaryExpression) expression;
            IElementType tokenType = binary.getOperationTokenType();
            if (tokenType == JavaTokenType.GE ||
                    tokenType == JavaTokenType.GT ||
                    tokenType == JavaTokenType.LT ||
                    tokenType == JavaTokenType.LE ||
                    tokenType == JavaTokenType.EQ) {
                PsiExpression lOperand = binary.getLOperand();
                PsiExpression rOperand = binary.getROperand();
                if (lOperand instanceof PsiReferenceExpression) {
                    return reference.equals(((PsiReferenceExpression) lOperand).resolve());
                }
                if (rOperand instanceof PsiReferenceExpression) {
                    return reference.equals(((PsiReferenceExpression) rOperand).resolve());
                }
            } else if (tokenType == JavaTokenType.ANDAND || tokenType == JavaTokenType.OROR) {
                return comparesReference(reference, binary.getLOperand()) || comparesReference(
                        reference, binary.getROperand());
            }
        }

        return false;
    }

    static long getLongValue(@Nullable PsiElement value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        } else if (value instanceof PsiLiteral) {
            Object o = ((PsiLiteral) value).getValue();
            if (o instanceof Number) {
                return ((Number) o).longValue();
            }
        } else if (value instanceof PsiPrefixExpression) {
            // negative number
            PsiPrefixExpression exp = (PsiPrefixExpression) value;
            if (exp.getOperationTokenType() == JavaTokenType.MINUS) {
                PsiExpression operand = exp.getOperand();
                if (operand instanceof PsiLiteral) {
                    Object o = ((PsiLiteral) operand).getValue();
                    if (o instanceof Number) {
                        return -((Number) o).longValue();
                    }
                }
            }
        } else if (value instanceof PsiReferenceExpression) {
            PsiElement resolved = ((PsiReferenceExpression) value).resolve();
            if (resolved instanceof PsiField) {
                return getLongValue(((PsiField) resolved).getInitializer(), defaultValue);
            }
        }

        if (value instanceof PsiExpression) {
            // For PSI conversion, use this instead:
            //Object o = JavaConstantExpressionEvaluator.computeConstantExpression((PsiExpression) value, false);
            Object o = ConstantEvaluator.evaluate(null, value);
            if (o instanceof Number) {
                return ((Number) o).longValue();
            }
        }

        return defaultValue;
    }

    static double getDoubleValue(@Nullable PsiAnnotationMemberValue value, double defaultValue) {
        if (value == null) {
            return defaultValue;
        } else if (value instanceof PsiLiteral) {
            Object o = ((PsiLiteral) value).getValue();
            if (o instanceof Number) {
                return ((Number) o).doubleValue();
            }
        } else if (value instanceof PsiPrefixExpression) {
            // negative number
            PsiPrefixExpression exp = (PsiPrefixExpression) value;
            if (exp.getOperationTokenType() == JavaTokenType.MINUS) {
                PsiExpression operand = exp.getOperand();
                if (operand instanceof PsiLiteral) {
                    Object o = ((PsiLiteral) operand).getValue();
                    if (o instanceof Number) {
                        return -((Number) o).doubleValue();
                    }
                }
            }
        } else if (value instanceof PsiReferenceExpression) {
            PsiElement resolved = ((PsiReferenceExpression) value).resolve();
            if (resolved instanceof PsiField) {
                return getDoubleValue(((PsiField) resolved).getInitializer(), defaultValue);
            }
        }

        if (value instanceof PsiExpression) {
            // For PSI conversion, use this instead:
            //Object o = JavaConstantExpressionEvaluator.computeConstantExpression((PsiExpression) value, false);
            Object o = ConstantEvaluator.evaluate(null, value);
            if (o instanceof Number) {
                return ((Number) o).doubleValue();
            }
        }

        return defaultValue;
    }

    static boolean getBooleanValue(@Nullable PsiAnnotationMemberValue value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        } else if (value instanceof PsiLiteral) {
            Object o = ((PsiLiteral) value).getValue();
            if (o instanceof Boolean) {
                return (Boolean) o;
            }
        }

        return defaultValue;
    }
}
