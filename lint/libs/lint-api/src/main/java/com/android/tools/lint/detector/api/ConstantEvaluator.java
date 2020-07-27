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

import static com.android.tools.lint.client.api.JavaEvaluatorKt.TYPE_OBJECT;
import static com.android.tools.lint.client.api.JavaEvaluatorKt.TYPE_STRING;
import static org.jetbrains.uast.UastBinaryExpressionWithTypeKind.TYPE_CAST;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.collect.Lists;
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiArrayInitializerExpression;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiLiteral;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiParenthesizedExpression;
import com.intellij.psi.PsiPolyadicExpression;
import com.intellij.psi.PsiPrefixExpression;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.kotlin.asJava.elements.KtLightPsiLiteral;
import org.jetbrains.kotlin.psi.KtExpression;
import org.jetbrains.uast.UArrayAccessExpression;
import org.jetbrains.uast.UBinaryExpression;
import org.jetbrains.uast.UBinaryExpressionWithType;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UDeclarationsExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UIfExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UParenthesizedExpression;
import org.jetbrains.uast.UPolyadicExpression;
import org.jetbrains.uast.UPrefixExpression;
import org.jetbrains.uast.UQualifiedReferenceExpression;
import org.jetbrains.uast.UReferenceExpression;
import org.jetbrains.uast.UResolvable;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.jetbrains.uast.UVariable;
import org.jetbrains.uast.UastBinaryOperator;
import org.jetbrains.uast.UastFacade;
import org.jetbrains.uast.UastPrefixOperator;
import org.jetbrains.uast.UastUtils;
import org.jetbrains.uast.kotlin.KotlinStringTemplateUPolyadicExpression;
import org.jetbrains.uast.util.UastExpressionUtils;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

/** Evaluates constant expressions */
public class ConstantEvaluator {
    /**
     * When evaluating expressions that resolve to arrays, this is the largest array size we'll
     * initialize; for larger arrays we'll return a {@link ArrayReference} instead
     */
    private static final int LARGEST_LITERAL_ARRAY = 12;

    private boolean allowUnknown;
    private boolean allowFieldInitializers;

    /** Creates a new constant evaluator */
    public ConstantEvaluator() {}

    /**
     * Whether we allow computing values where some terms are unknown. For example, the expression
     * {@code "foo" + x + "bar"} would return {@code null} without and {@code "foobar"} with.
     *
     * @return this for constructor chaining
     */
    public ConstantEvaluator allowUnknowns() {
        allowUnknown = true;
        return this;
    }

    public ConstantEvaluator allowFieldInitializers() {
        allowFieldInitializers = true;
        return this;
    }

    /**
     * Evaluates the given node and returns the constant value it resolves to, if any
     *
     * @param node the node to compute the constant value for
     * @return the corresponding constant value - a String, an Integer, a Float, and so on
     */
    @Nullable
    public Object evaluate(@Nullable UElement node) {
        if (node == null) {
            return null;
        }

        if (node instanceof ULiteralExpression) {
            return ((ULiteralExpression) node).getValue();
        } else if (node instanceof UPrefixExpression) {
            UastPrefixOperator operator = ((UPrefixExpression) node).getOperator();
            Object operand = evaluate(((UPrefixExpression) node).getOperand());
            if (operand == null) {
                return null;
            }
            if (operator == UastPrefixOperator.LOGICAL_NOT) {
                if (operand instanceof Boolean) {
                    return !(Boolean) operand;
                }
            } else if (operator == UastPrefixOperator.UNARY_PLUS) {
                return operand;
            } else if (operator == UastPrefixOperator.BITWISE_NOT) {
                if (operand instanceof Integer) {
                    return ~(Integer) operand;
                } else if (operand instanceof Long) {
                    return ~(Long) operand;
                } else if (operand instanceof Short) {
                    return ~(Short) operand;
                } else if (operand instanceof Character) {
                    return ~(Character) operand;
                } else if (operand instanceof Byte) {
                    return ~(Byte) operand;
                }
            } else if (operator == UastPrefixOperator.UNARY_MINUS) {
                if (operand instanceof Integer) {
                    return -(Integer) operand;
                } else if (operand instanceof Long) {
                    return -(Long) operand;
                } else if (operand instanceof Double) {
                    return -(Double) operand;
                } else if (operand instanceof Float) {
                    return -(Float) operand;
                } else if (operand instanceof Short) {
                    return -(Short) operand;
                } else if (operand instanceof Character) {
                    return -(Character) operand;
                } else if (operand instanceof Byte) {
                    return -(Byte) operand;
                }
            }
        } else if (node instanceof UIfExpression
                && ((UIfExpression) node).getExpressionType() != null) {
            UIfExpression expression = (UIfExpression) node;
            Object known = evaluate(expression.getCondition());
            if (known == Boolean.TRUE && expression.getThenExpression() != null) {
                return evaluate(expression.getThenExpression());
            } else if (known == Boolean.FALSE && expression.getElseExpression() != null) {
                return evaluate(expression.getElseExpression());
            }
        } else if (node instanceof UParenthesizedExpression) {
            UParenthesizedExpression parenthesizedExpression = (UParenthesizedExpression) node;
            UExpression expression = parenthesizedExpression.getExpression();
            return evaluate(expression);
        } else if (node instanceof UPolyadicExpression) {
            UPolyadicExpression polyadicExpression = (UPolyadicExpression) node;
            UastBinaryOperator operator = polyadicExpression.getOperator();
            List<UExpression> operands = polyadicExpression.getOperands();
            if (operands.isEmpty()) {
                // For empty strings the Kotlin string template will return an empty operand list
                if (node instanceof KotlinStringTemplateUPolyadicExpression) {
                    return "";
                }
            }
            assert !operands.isEmpty();
            Object result = evaluate(operands.get(0));
            for (int i = 1, n = operands.size(); i < n; i++) {
                Object rhs = evaluate(operands.get(i));
                result = evaluateBinary(operator, result, rhs);
            }
            if (result != null) {
                return result;
            }
        } else if (node instanceof UBinaryExpressionWithType
                && ((UBinaryExpressionWithType) node).getOperationKind() == TYPE_CAST) {
            UBinaryExpressionWithType cast = (UBinaryExpressionWithType) node;
            Object operandValue = evaluate(cast.getOperand());
            if (operandValue instanceof Number) {
                Number number = (Number) operandValue;
                PsiType type = cast.getType();
                if (PsiType.FLOAT.equals(type)) {
                    return number.floatValue();
                } else if (PsiType.DOUBLE.equals(type)) {
                    return number.doubleValue();
                } else if (PsiType.INT.equals(type)) {
                    return number.intValue();
                } else if (PsiType.LONG.equals(type)) {
                    return number.longValue();
                } else if (PsiType.SHORT.equals(type)) {
                    return number.shortValue();
                } else if (PsiType.BYTE.equals(type)) {
                    return number.byteValue();
                }
            }
            return operandValue;
        } else if (node instanceof UReferenceExpression) {
            PsiElement resolved = ((UReferenceExpression) node).resolve();
            if (resolved instanceof PsiVariable) {

                // Handle fields specially: we can't look for last assignment
                // on fields since the modifications are often in different methods
                // Only take the field constant or initializer value if it's final,
                // or if allowFieldInitializers is true
                if (resolved instanceof PsiField) {
                    PsiField field = (PsiField) resolved;
                    if ("length".equals(field.getName())
                            && node instanceof UQualifiedReferenceExpression
                            && ((UQualifiedReferenceExpression) node)
                                            .getReceiver()
                                            .getExpressionType()
                                    instanceof PsiArrayType) {
                        // It's an array.length expression
                        Object array =
                                evaluate(((UQualifiedReferenceExpression) node).getReceiver());
                        int size = getArraySize(array);
                        if (size != -1) {
                            return size;
                        }
                        return null;
                    }
                    Object value = field.computeConstantValue();
                    if (value != null) {
                        return value;
                    }
                    if (field.getInitializer() != null
                            && (allowFieldInitializers
                                    || (field.hasModifierProperty(PsiModifier.STATIC)
                                            && field.hasModifierProperty(PsiModifier.FINAL)))) {
                        value = evaluate(field.getInitializer());
                        if (value != null) {
                            if (surroundedByVariableCheck(node, field)) {
                                return null;
                            }

                            return value;
                        }
                    }
                    return null;
                }

                PsiVariable variable = (PsiVariable) resolved;
                Object value = UastLintUtils.findLastValue(variable, node, this);

                // Special return value: the variable *was* assigned something but we don't know
                // the value. In that case we should not continue to look at the initializer
                // since the initial value is no longer relevant.
                if (value == LastAssignmentFinder.LAST_ASSIGNMENT_VALUE_UNKNOWN) {
                    return null;
                }

                if (value != null) {
                    if (surroundedByVariableCheck(node, variable)) {
                        return null;
                    }

                    return value;
                }
                if (variable.getInitializer() != null) {
                    Object initializedValue = evaluate(variable.getInitializer());
                    if (surroundedByVariableCheck(node, variable)) {
                        return null;
                    }

                    return initializedValue;
                }
                return null;
            }
            if (node instanceof UQualifiedReferenceExpression) {
                UQualifiedReferenceExpression expression = (UQualifiedReferenceExpression) node;
                UExpression selector = expression.getSelector();
                UExpression receiver = expression.getReceiver();

                if (receiver instanceof USimpleNameReferenceExpression) {
                    USimpleNameReferenceExpression name = (USimpleNameReferenceExpression) receiver;
                    // such as kotlin.IntArray(x)
                    if (name.getIdentifier().equals("kotlin")) {
                        return evaluate(selector);
                    }
                }

                if (selector instanceof USimpleNameReferenceExpression) {
                    USimpleNameReferenceExpression nameReferenceExpression =
                            (USimpleNameReferenceExpression) selector;
                    String identifier = nameReferenceExpression.getIdentifier();
                    // "kotlin.<N>Array".size ?
                    if (receiver instanceof UQualifiedReferenceExpression) {
                        UQualifiedReferenceExpression expression1 =
                                (UQualifiedReferenceExpression) receiver;
                        if (expression1.getReceiver() instanceof USimpleNameReferenceExpression
                                && ((USimpleNameReferenceExpression) expression1.getReceiver())
                                        .getIdentifier()
                                        .equals("kotlin")) {
                            receiver = expression1.getSelector();
                        }
                    }

                    // TODO: Handle listOf, arrayListOf etc as well!

                    if ("size".equals(identifier) && receiver instanceof UCallExpression) {
                        UCallExpression receiverCall = (UCallExpression) receiver;
                        String name = Lint.getMethodName(receiverCall);
                        if (name != null) {
                            if (name.endsWith("Array")
                                    && (name.equals("Array")
                                            || getKotlinPrimitiveArrayType(name) != null)) {
                                int size = getKotlinArrayConstructionSize(receiverCall);
                                if (size != -1) {
                                    return size;
                                }
                            } else if (name.endsWith("rrayOf")
                                    && (name.equals("arrayOf")
                                            || getKotlinPrimitiveArrayType(name) != null)) {
                                return receiverCall.getValueArgumentCount();
                            } else if ("arrayOfNulls".equals(name)) {
                                int size = getKotlinArrayConstructionSize(receiverCall);
                                if (size != -1) {
                                    return size;
                                }
                            }
                        }
                    }
                }
            }
        } else if (UastExpressionUtils.isNewArrayWithDimensions(node)) {
            UCallExpression call = (UCallExpression) node;
            PsiType arrayType = call.getExpressionType();
            if (arrayType instanceof PsiArrayType) {

                PsiType type = arrayType.getDeepComponentType();
                // Single-dimension array
                if (!(type instanceof PsiArrayType) && call.getValueArgumentCount() == 1) {
                    Object lengthObj = evaluate(call.getValueArguments().get(0));
                    if (lengthObj instanceof Number) {
                        int size = ((Number) lengthObj).intValue();
                        int dimensions = arrayType.getArrayDimensions();
                        return getArray(type, size, dimensions);
                    }
                }
            }
        } else if (UastExpressionUtils.isNewArrayWithInitializer(node)) {
            Object array = createInitializedArray((UCallExpression) node);
            if (array != null) {
                return array;
            }
        } else if (node instanceof UCallExpression) {
            UCallExpression call = (UCallExpression) node;

            String name = Lint.getMethodName(call);
            if (name != null) {
                if (name.endsWith("Array") && UastExpressionUtils.isConstructorCall(call)) {
                    int size = getKotlinArrayConstructionSize(call);
                    if (size != -1) {
                        if (name.equals("Array")) {
                            PsiType type = call.getExpressionType();
                            if (type instanceof PsiArrayType) {
                                int dimensions = type.getArrayDimensions();
                                PsiType componentType = type.getDeepComponentType();
                                return getArray(componentType, size, dimensions);
                            }
                        } else {
                            PsiType type = getKotlinPrimitiveArrayType(name);
                            if (type != null) {
                                int dimensions = 1;
                                return getArray(type, size, dimensions);
                            }
                        }
                    }
                } else if ("arrayOf".equals(name)
                        || name.endsWith("ArrayOf") && getKotlinPrimitiveArrayType(name) != null) {
                    Object array = createInitializedArray(call);
                    if (array != null) {
                        return array;
                    }
                } else if ("arrayOfNulls".equals(name)) {
                    PsiType type = call.getExpressionType();
                    if (type instanceof PsiArrayType) {
                        int size = getKotlinArrayConstructionSize(call);
                        if (size != -1) {
                            int dimensions = type.getArrayDimensions();
                            PsiType componentType = type.getDeepComponentType();
                            return getArray(componentType, size, dimensions);
                        }
                    }
                }
            }
        } else if (node instanceof UArrayAccessExpression) {
            UArrayAccessExpression expression = (UArrayAccessExpression) node;
            List<UExpression> indices = expression.getIndices();
            if (indices.size() == 1) {
                Object indexValue = evaluate(indices.get(0));
                if (indexValue instanceof Number) {
                    Object array = evaluate(expression.getReceiver());
                    if (array != null) {
                        int index = ((Number) indexValue).intValue();
                        if (array instanceof Object[]) {
                            Object[] objArray = (Object[]) array;
                            if (index >= 0 && index < objArray.length) {
                                return objArray[index];
                            }
                        } else if (array instanceof int[]) {
                            int[] intArray = (int[]) array;
                            if (index >= 0 && index < intArray.length) {
                                return intArray[index];
                            }
                        } else if (array instanceof boolean[]) {
                            boolean[] booleanArray = (boolean[]) array;
                            if (index >= 0 && index < booleanArray.length) {
                                return booleanArray[index];
                            }
                        } else if (array instanceof char[]) {
                            char[] charArray = (char[]) array;
                            if (index >= 0 && index < charArray.length) {
                                return charArray[index];
                            }
                        } else if (array instanceof long[]) {
                            long[] longArray = (long[]) array;
                            if (index >= 0 && index < longArray.length) {
                                return longArray[index];
                            }
                        } else if (array instanceof float[]) {
                            float[] floatArray = (float[]) array;
                            if (index >= 0 && index < floatArray.length) {
                                return floatArray[index];
                            }
                        } else if (array instanceof double[]) {
                            double[] doubleArray = (double[]) array;
                            if (index >= 0 && index < doubleArray.length) {
                                return doubleArray[index];
                            }
                        } else if (array instanceof byte[]) {
                            byte[] byteArray = (byte[]) array;
                            if (index >= 0 && index < byteArray.length) {
                                return byteArray[index];
                            }
                        } else if (array instanceof short[]) {
                            short[] shortArray = (short[]) array;
                            if (index >= 0 && index < shortArray.length) {
                                return shortArray[index];
                            }
                        }
                    }
                }
            }
        }

        if (node instanceof UExpression) {
            Object evaluated = ((UExpression) node).evaluate();
            if (evaluated != null) {
                return evaluated;
            }
        }

        // TODO: Check for MethodInvocation and perform some common operations -
        // Math.* methods, String utility methods like notNullize, etc

        return null;
    }

    private Object createInitializedArray(UCallExpression call) {
        PsiType arrayType = call.getExpressionType();
        if (arrayType instanceof PsiArrayType) {
            PsiType componentType = arrayType.getDeepComponentType();
            if (!(componentType instanceof PsiArrayType)) {
                int length = call.getValueArgumentCount();
                List<Object> evaluatedArgs = new ArrayList<>(length);
                int count = 0;
                for (UExpression arg : call.getValueArguments()) {
                    Object evaluatedArg = evaluate(arg);
                    if (!allowUnknown && evaluatedArg == null) {
                        // Inconclusive
                        return null;
                    }
                    evaluatedArgs.add(evaluatedArg);
                    count++;
                    if (count == 40) { // avoid large initializers
                        return getArray(componentType, length, 1);
                    }
                }

                if (componentType == PsiType.BOOLEAN) {
                    boolean[] arr = new boolean[length];
                    for (int i = 0; i < length; ++i) {
                        Object o = evaluatedArgs.get(i);
                        if (o instanceof Boolean) {
                            arr[i] = (Boolean) o;
                        }
                    }
                    return arr;
                } else if (isObjectType(componentType)) {
                    Object[] arr = new Object[length];
                    for (int i = 0; i < length; ++i) {
                        arr[i] = evaluatedArgs.get(i);
                    }
                    return arr;
                } else if (componentType.equals(PsiType.CHAR)) {
                    char[] arr = new char[length];
                    for (int i = 0; i < length; ++i) {
                        Object o = evaluatedArgs.get(i);
                        if (o instanceof Character) {
                            arr[i] = (Character) o;
                        }
                    }
                    return arr;
                } else if (componentType.equals(PsiType.BYTE)) {
                    byte[] arr = new byte[length];
                    for (int i = 0; i < length; ++i) {
                        Object o = evaluatedArgs.get(i);
                        if (o instanceof Byte) {
                            arr[i] = (Byte) o;
                        }
                    }
                    return arr;
                } else if (componentType.equals(PsiType.DOUBLE)) {
                    double[] arr = new double[length];
                    for (int i = 0; i < length; ++i) {
                        Object o = evaluatedArgs.get(i);
                        if (o instanceof Double) {
                            arr[i] = (Double) o;
                        }
                    }
                    return arr;
                } else if (componentType.equals(PsiType.FLOAT)) {
                    float[] arr = new float[length];
                    for (int i = 0; i < length; ++i) {
                        Object o = evaluatedArgs.get(i);
                        if (o instanceof Float) {
                            arr[i] = (Float) o;
                        }
                    }
                    return arr;
                } else if (componentType.equals(PsiType.INT)) {
                    int[] arr = new int[length];
                    for (int i = 0; i < length; ++i) {
                        Object o = evaluatedArgs.get(i);
                        if (o instanceof Integer) {
                            arr[i] = (Integer) o;
                        }
                    }
                    return arr;
                } else if (componentType.equals(PsiType.SHORT)) {
                    short[] arr = new short[length];
                    for (int i = 0; i < length; ++i) {
                        Object o = evaluatedArgs.get(i);
                        if (o instanceof Short) {
                            arr[i] = (Short) o;
                        }
                    }
                    return arr;
                } else if (componentType.equals(PsiType.LONG)) {
                    long[] arr = new long[length];
                    for (int i = 0; i < length; ++i) {
                        Object o = evaluatedArgs.get(i);
                        if (o instanceof Long) {
                            arr[i] = (Long) o;
                        }
                    }
                    return arr;
                } else if (isStringType(componentType)) {
                    String[] arr = new String[length];
                    for (int i = 0; i < length; ++i) {
                        Object o = evaluatedArgs.get(i);
                        if (o instanceof String) {
                            arr[i] = (String) o;
                        }
                    }
                    return arr;
                }

                // Try to instantiate base type
                if (!evaluatedArgs.isEmpty()) {
                    Object first = evaluatedArgs.get(0);
                    for (Object o : evaluatedArgs) {
                        if (o.getClass() != first.getClass()) {
                            return null;
                        }
                    }
                    return evaluatedArgs.toArray((Object[]) Array.newInstance(first.getClass(), 0));
                }
            }
        }

        return null;
    }

    private static PsiType getKotlinPrimitiveArrayType(@NonNull String constructorName) {
        switch (constructorName) {
            case "ByteArray":
            case "byteArrayOf":
                return PsiPrimitiveType.BYTE;
            case "CharArray":
            case "charArrayOf":
                return PsiPrimitiveType.CHAR;
            case "ShortArray":
            case "shortArrayOf":
                return PsiPrimitiveType.SHORT;
            case "IntArray":
            case "intArrayOf":
                return PsiPrimitiveType.INT;
            case "LongArray":
            case "longArrayOf":
                return PsiPrimitiveType.LONG;
            case "FloatArray":
            case "floatArrayOf":
                return PsiPrimitiveType.FLOAT;
            case "DoubleArray":
            case "doubleArrayOf":
                return PsiPrimitiveType.DOUBLE;
            case "BooleanArray":
            case "booleanArrayOf":
                return PsiPrimitiveType.BOOLEAN;
        }
        return null;
    }

    private int getKotlinArrayConstructionSize(@NonNull UCallExpression call) {
        List<UExpression> valueArguments = call.getValueArguments();
        if (!valueArguments.isEmpty()) {
            Object lengthObj = evaluate(call.getValueArguments().get(0));
            if (lengthObj instanceof Number) {
                return ((Number) lengthObj).intValue();
            }
        }

        return -1;
    }

    public static int getArraySize(@Nullable Object array) {
        // This is kinda repetitive but there is no subtyping relationship between
        // primitive arrays; int[] is not a subtype of Object[] etc.
        if (array instanceof ArrayReference) {
            return ((ArrayReference) array).size;
        }
        if (array instanceof int[]) {
            return ((int[]) array).length;
        }
        if (array instanceof long[]) {
            return ((long[]) array).length;
        }
        if (array instanceof float[]) {
            return ((float[]) array).length;
        }
        if (array instanceof double[]) {
            return ((double[]) array).length;
        }
        if (array instanceof char[]) {
            return ((char[]) array).length;
        }
        if (array instanceof byte[]) {
            return ((byte[]) array).length;
        }
        if (array instanceof short[]) {
            return ((short[]) array).length;
        }
        if (array instanceof Object[]) {
            return ((Object[]) array).length;
        }
        return -1;
    }

    @Nullable
    private Object evaluateBinary(
            @NonNull UastBinaryOperator operator,
            @Nullable Object operandLeft,
            @Nullable Object operandRight) {
        if (operandLeft == null || operandRight == null) {
            if (allowUnknown) {
                if (operandLeft == null) {
                    return operandRight;
                } else {
                    return operandLeft;
                }
            }
            return null;
        }
        if (operandLeft instanceof String && operandRight instanceof String) {
            if (operator == UastBinaryOperator.PLUS) {
                return operandLeft.toString() + operandRight.toString();
            }
            return null;
        } else if (operandLeft instanceof Boolean && operandRight instanceof Boolean) {
            boolean left = (Boolean) operandLeft;
            boolean right = (Boolean) operandRight;
            if (operator == UastBinaryOperator.LOGICAL_OR) {
                return left || right;
            } else if (operator == UastBinaryOperator.LOGICAL_AND) {
                return left && right;
            } else if (operator == UastBinaryOperator.BITWISE_OR) {
                return left | right;
            } else if (operator == UastBinaryOperator.BITWISE_XOR) {
                return left ^ right;
            } else if (operator == UastBinaryOperator.BITWISE_AND) {
                return left & right;
            } else if (operator == UastBinaryOperator.IDENTITY_EQUALS
                    || operator == UastBinaryOperator.EQUALS) {
                return left == right;
            } else if (operator == UastBinaryOperator.IDENTITY_NOT_EQUALS
                    || operator == UastBinaryOperator.NOT_EQUALS) {
                return left != right;
            }
        } else if (operandLeft instanceof Number && operandRight instanceof Number) {
            Number left = (Number) operandLeft;
            Number right = (Number) operandRight;
            boolean isInteger =
                    !(left instanceof Float
                            || left instanceof Double
                            || right instanceof Float
                            || right instanceof Double);
            boolean isWide =
                    isInteger
                            ? (left instanceof Long || right instanceof Long)
                            : (left instanceof Double || right instanceof Double);

            if (operator == UastBinaryOperator.BITWISE_OR) {
                if (isWide) {
                    return left.longValue() | right.longValue();
                } else {
                    return left.intValue() | right.intValue();
                }
            } else if (operator == UastBinaryOperator.BITWISE_XOR) {
                if (isWide) {
                    return left.longValue() ^ right.longValue();
                } else {
                    return left.intValue() ^ right.intValue();
                }
            } else if (operator == UastBinaryOperator.BITWISE_AND) {
                if (isWide) {
                    return left.longValue() & right.longValue();
                } else {
                    return left.intValue() & right.intValue();
                }
            } else if (operator == UastBinaryOperator.EQUALS
                    || operator == UastBinaryOperator.IDENTITY_EQUALS) {
                if (isInteger) {
                    return left.longValue() == right.longValue();
                } else {
                    return left.doubleValue() == right.doubleValue();
                }
            } else if (operator == UastBinaryOperator.NOT_EQUALS
                    || operator == UastBinaryOperator.IDENTITY_NOT_EQUALS) {
                if (isInteger) {
                    return left.longValue() != right.longValue();
                } else {
                    return left.doubleValue() != right.doubleValue();
                }
            } else if (operator == UastBinaryOperator.GREATER) {
                if (isInteger) {
                    return left.longValue() > right.longValue();
                } else {
                    return left.doubleValue() > right.doubleValue();
                }
            } else if (operator == UastBinaryOperator.GREATER_OR_EQUALS) {
                if (isInteger) {
                    return left.longValue() >= right.longValue();
                } else {
                    return left.doubleValue() >= right.doubleValue();
                }
            } else if (operator == UastBinaryOperator.LESS) {
                if (isInteger) {
                    return left.longValue() < right.longValue();
                } else {
                    return left.doubleValue() < right.doubleValue();
                }
            } else if (operator == UastBinaryOperator.LESS_OR_EQUALS) {
                if (isInteger) {
                    return left.longValue() <= right.longValue();
                } else {
                    return left.doubleValue() <= right.doubleValue();
                }
            } else if (operator == UastBinaryOperator.SHIFT_LEFT) {
                if (isWide) {
                    return left.longValue() << right.intValue();
                } else {
                    return left.intValue() << right.intValue();
                }
            } else if (operator == UastBinaryOperator.SHIFT_RIGHT) {
                if (isWide) {
                    return left.longValue() >> right.intValue();
                } else {
                    return left.intValue() >> right.intValue();
                }
            } else if (operator == UastBinaryOperator.UNSIGNED_SHIFT_RIGHT) {
                if (isWide) {
                    return left.longValue() >>> right.intValue();
                } else {
                    return left.intValue() >>> right.intValue();
                }
            } else if (operator == UastBinaryOperator.PLUS) {
                if (isInteger) {
                    if (isWide) {
                        return left.longValue() + right.longValue();
                    } else {
                        return left.intValue() + right.intValue();
                    }
                } else {
                    if (isWide) {
                        return left.doubleValue() + right.doubleValue();
                    } else {
                        return left.floatValue() + right.floatValue();
                    }
                }
            } else if (operator == UastBinaryOperator.MINUS) {
                if (isInteger) {
                    if (isWide) {
                        return left.longValue() - right.longValue();
                    } else {
                        return left.intValue() - right.intValue();
                    }
                } else {
                    if (isWide) {
                        return left.doubleValue() - right.doubleValue();
                    } else {
                        return left.floatValue() - right.floatValue();
                    }
                }
            } else if (operator == UastBinaryOperator.MULTIPLY) {
                if (isInteger) {
                    if (isWide) {
                        return left.longValue() * right.longValue();
                    } else {
                        return left.intValue() * right.intValue();
                    }
                } else {
                    if (isWide) {
                        return left.doubleValue() * right.doubleValue();
                    } else {
                        return left.floatValue() * right.floatValue();
                    }
                }
            } else if (operator == UastBinaryOperator.DIV) {
                if (isInteger) {
                    if (isWide) {
                        return left.longValue() / right.longValue();
                    } else {
                        return left.intValue() / right.intValue();
                    }
                } else {
                    if (isWide) {
                        return left.doubleValue() / right.doubleValue();
                    } else {
                        return left.floatValue() / right.floatValue();
                    }
                }
            } else if (operator == UastBinaryOperator.MOD) {
                if (isInteger) {
                    if (isWide) {
                        return left.longValue() % right.longValue();
                    } else {
                        return left.intValue() % right.intValue();
                    }
                } else {
                    if (isWide) {
                        return left.doubleValue() % right.doubleValue();
                    } else {
                        return left.floatValue() % right.floatValue();
                    }
                }
            } else {
                return null;
            }
        }

        return null;
    }

    private static boolean surroundedByVariableCheck(
            @Nullable UElement node, @NonNull PsiVariable variable) {
        if (node == null) {
            return false;
        }

        // See if it looks like the value has been clamped locally, e.g.
        UIfExpression curr = UastUtils.getParentOfType(node, UIfExpression.class);
        while (curr != null) {
            if (references(curr.getCondition(), variable)) {
                // variable is referenced surrounding this reference; don't
                // take the variable initializer since the value may have been
                // value checked for some other later assigned value
                // ...but only if it's not the condition!
                UExpression condition = curr.getCondition();
                if (!UastUtils.isUastChildOf(node, condition, false)) {
                    return true;
                }
            }
            curr = UastUtils.getParentOfType(curr, UIfExpression.class);
        }
        return false;
    }

    private static boolean isStringType(PsiType type) {
        if (!(type instanceof PsiClassType)) {
            return false;
        }

        PsiClass resolvedClass = ((PsiClassType) type).resolve();
        return resolvedClass != null && TYPE_STRING.equals(resolvedClass.getQualifiedName());
    }

    private static boolean isObjectType(PsiType type) {
        if (!(type instanceof PsiClassType)) {
            return false;
        }

        PsiClass resolvedClass = ((PsiClassType) type).resolve();
        return resolvedClass != null && TYPE_OBJECT.equals(resolvedClass.getQualifiedName());
    }

    public static class LastAssignmentFinder extends AbstractUastVisitor {
        /**
         * Special marker value from [findLastValue] to indicate that a node was assigned to, but
         * the value is unknown
         */
        @SuppressWarnings("StringOperationCanBeSimplified") // prevent interning; no aliases
        public static final Object LAST_ASSIGNMENT_VALUE_UNKNOWN = new String("<unknown>");

        private final PsiVariable mVariable;
        private final UElement mEndAt;

        private final ConstantEvaluator mConstantEvaluator;

        private boolean mDone = false;
        private int mCurrentLevel = 0;
        private int mVariableLevel = -1;
        private Object mCurrentValue;
        private UElement mLastAssignment;

        public LastAssignmentFinder(
                @NonNull PsiVariable variable,
                @NonNull UElement endAt,
                @Nullable ConstantEvaluator constantEvaluator,
                int variableLevel) {
            mVariable = variable;
            mEndAt = endAt;
            UExpression initializer = UastFacade.INSTANCE.getInitializerBody(variable);
            mLastAssignment = initializer;
            mConstantEvaluator = constantEvaluator;
            if (initializer != null && constantEvaluator != null) {
                mCurrentValue = constantEvaluator.evaluate(initializer);
            }
            mVariableLevel = variableLevel;
        }

        @Nullable
        public Object getCurrentValue() {
            return mCurrentValue;
        }

        @Nullable
        public UElement getLastAssignment() {
            return mLastAssignment;
        }

        @Override
        public boolean visitElement(@NonNull UElement node) {
            if (elementHasLevel(node)) {
                mCurrentLevel++;
            }
            if (node.equals(mEndAt)) {
                mDone = true;
            }
            return mDone || super.visitElement(node);
        }

        @Override
        public boolean visitVariable(@NonNull UVariable node) {
            if (mVariableLevel < 0 && node.getPsi().isEquivalentTo(mVariable)) {
                mVariableLevel = mCurrentLevel;
            }

            return super.visitVariable(node);
        }

        @Override
        public void afterVisitBinaryExpression(@NonNull UBinaryExpression node) {
            if (!mDone
                    && node.getOperator() instanceof UastBinaryOperator.AssignOperator
                    && mVariableLevel >= 0) {
                UExpression leftOperand = node.getLeftOperand();
                UastBinaryOperator operator = node.getOperator();

                if (!(operator instanceof UastBinaryOperator.AssignOperator)
                        || !(leftOperand instanceof UResolvable)) {
                    return;
                }

                PsiElement resolved = ((UResolvable) leftOperand).resolve();
                if (!mVariable.equals(resolved)) {
                    return;
                }

                // Last assignment is unknown if we see an assignment inside
                // some conditional or loop statement.
                if (mCurrentLevel > mVariableLevel + 1) {
                    mLastAssignment = null;
                    mCurrentValue = null;
                    return;
                }

                UExpression rightOperand = node.getRightOperand();
                ConstantEvaluator constantEvaluator = mConstantEvaluator;

                mCurrentValue =
                        (constantEvaluator != null)
                                ? constantEvaluator.evaluate(rightOperand)
                                : null;
                mLastAssignment = rightOperand;
            }

            super.afterVisitBinaryExpression(node);
        }

        @Override
        public void afterVisitElement(@NonNull UElement node) {
            if (elementHasLevel(node)) {
                mCurrentLevel--;
            }
            super.afterVisitElement(node);
        }

        private static boolean elementHasLevel(UElement node) {
            return !(node instanceof UBlockExpression || node instanceof UDeclarationsExpression);
        }
    }

    /**
     * Evaluates the given node and returns the constant value it resolves to, if any
     *
     * @param node the node to compute the constant value for
     * @return the corresponding constant value - a String, an Integer, a Float, and so on
     */
    @Nullable
    public Object evaluate(@Nullable PsiElement node) {
        if (node == null) {
            return null;
        }
        if (node instanceof PsiLiteral) {
            Object value = ((PsiLiteral) node).getValue();
            if (value == null && node instanceof KtLightPsiLiteral) {
                KtExpression origin = ((KtLightPsiLiteral) node).getKotlinOrigin();
                UExpression uastExpression =
                        (UExpression)
                                UastFacade.INSTANCE.convertElement(origin, null, UExpression.class);
                if (uastExpression != null) {
                    value = uastExpression.evaluate();
                }
            }
            return value;
        } else if (node instanceof PsiPrefixExpression) {
            IElementType operator = ((PsiPrefixExpression) node).getOperationTokenType();
            Object operand = evaluate(((PsiPrefixExpression) node).getOperand());
            if (operand == null) {
                return null;
            }
            if (operator == JavaTokenType.EXCL) {
                if (operand instanceof Boolean) {
                    return !(Boolean) operand;
                }
            } else if (operator == JavaTokenType.PLUS) {
                return operand;
            } else if (operator == JavaTokenType.TILDE) {
                if (operand instanceof Integer) {
                    return ~(Integer) operand;
                } else if (operand instanceof Long) {
                    return ~(Long) operand;
                } else if (operand instanceof Short) {
                    return ~(Short) operand;
                } else if (operand instanceof Character) {
                    return ~(Character) operand;
                } else if (operand instanceof Byte) {
                    return ~(Byte) operand;
                }
            } else if (operator == JavaTokenType.MINUS) {
                if (operand instanceof Integer) {
                    return -(Integer) operand;
                } else if (operand instanceof Long) {
                    return -(Long) operand;
                } else if (operand instanceof Double) {
                    return -(Double) operand;
                } else if (operand instanceof Float) {
                    return -(Float) operand;
                } else if (operand instanceof Short) {
                    return -(Short) operand;
                } else if (operand instanceof Character) {
                    return -(Character) operand;
                } else if (operand instanceof Byte) {
                    return -(Byte) operand;
                }
            }
        } else if (node instanceof PsiConditionalExpression) {
            PsiConditionalExpression expression = (PsiConditionalExpression) node;
            Object known = evaluate(expression.getCondition());
            if (known == Boolean.TRUE && expression.getThenExpression() != null) {
                return evaluate(expression.getThenExpression());
            } else if (known == Boolean.FALSE && expression.getElseExpression() != null) {
                return evaluate(expression.getElseExpression());
            }
        } else if (node instanceof PsiParenthesizedExpression) {
            PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression) node;
            PsiExpression expression = parenthesizedExpression.getExpression();
            if (expression != null) {
                return evaluate(expression);
            }
        } else if (node instanceof PsiBinaryExpression) {
            PsiBinaryExpression expression = (PsiBinaryExpression) node;
            IElementType operator = expression.getOperationTokenType();
            Object operandLeft = evaluate(expression.getLOperand());
            Object operandRight = evaluate(expression.getROperand());
            if (operandLeft == null || operandRight == null) {
                if (allowUnknown) {
                    if (operandLeft == null) {
                        return operandRight;
                    } else {
                        return operandLeft;
                    }
                }
                return null;
            }
            if (operandLeft instanceof String && operandRight instanceof String) {
                if (operator == JavaTokenType.PLUS) {
                    return operandLeft.toString() + operandRight.toString();
                }
                return null;
            } else if (operandLeft instanceof Boolean && operandRight instanceof Boolean) {
                boolean left = (Boolean) operandLeft;
                boolean right = (Boolean) operandRight;
                if (operator == JavaTokenType.OROR) {
                    return left || right;
                } else if (operator == JavaTokenType.ANDAND) {
                    return left && right;
                } else if (operator == JavaTokenType.OR) {
                    return left | right;
                } else if (operator == JavaTokenType.XOR) {
                    return left ^ right;
                } else if (operator == JavaTokenType.AND) {
                    return left & right;
                } else if (operator == JavaTokenType.EQEQ) {
                    return left == right;
                } else if (operator == JavaTokenType.NE) {
                    return left != right;
                }
            } else if (operandLeft instanceof Number && operandRight instanceof Number) {
                Number left = (Number) operandLeft;
                Number right = (Number) operandRight;
                boolean isInteger =
                        !(left instanceof Float
                                || left instanceof Double
                                || right instanceof Float
                                || right instanceof Double);
                boolean isWide =
                        isInteger
                                ? (left instanceof Long || right instanceof Long)
                                : (left instanceof Double || right instanceof Double);

                if (operator == JavaTokenType.OR) {
                    if (isWide) {
                        return left.longValue() | right.longValue();
                    } else {
                        return left.intValue() | right.intValue();
                    }
                } else if (operator == JavaTokenType.XOR) {
                    if (isWide) {
                        return left.longValue() ^ right.longValue();
                    } else {
                        return left.intValue() ^ right.intValue();
                    }
                } else if (operator == JavaTokenType.AND) {
                    if (isWide) {
                        return left.longValue() & right.longValue();
                    } else {
                        return left.intValue() & right.intValue();
                    }
                } else if (operator == JavaTokenType.EQEQ) {
                    if (isInteger) {
                        return left.longValue() == right.longValue();
                    } else {
                        return left.doubleValue() == right.doubleValue();
                    }
                } else if (operator == JavaTokenType.NE) {
                    if (isInteger) {
                        return left.longValue() != right.longValue();
                    } else {
                        return left.doubleValue() != right.doubleValue();
                    }
                } else if (operator == JavaTokenType.GT) {
                    if (isInteger) {
                        return left.longValue() > right.longValue();
                    } else {
                        return left.doubleValue() > right.doubleValue();
                    }
                } else if (operator == JavaTokenType.GE) {
                    if (isInteger) {
                        return left.longValue() >= right.longValue();
                    } else {
                        return left.doubleValue() >= right.doubleValue();
                    }
                } else if (operator == JavaTokenType.LT) {
                    if (isInteger) {
                        return left.longValue() < right.longValue();
                    } else {
                        return left.doubleValue() < right.doubleValue();
                    }
                } else if (operator == JavaTokenType.LE) {
                    if (isInteger) {
                        return left.longValue() <= right.longValue();
                    } else {
                        return left.doubleValue() <= right.doubleValue();
                    }
                } else if (operator == JavaTokenType.LTLT) {
                    if (isWide) {
                        return left.longValue() << right.intValue();
                    } else {
                        return left.intValue() << right.intValue();
                    }
                } else if (operator == JavaTokenType.GTGT) {
                    if (isWide) {
                        return left.longValue() >> right.intValue();
                    } else {
                        return left.intValue() >> right.intValue();
                    }
                } else if (operator == JavaTokenType.GTGTGT) {
                    if (isWide) {
                        return left.longValue() >>> right.intValue();
                    } else {
                        return left.intValue() >>> right.intValue();
                    }
                } else if (operator == JavaTokenType.PLUS) {
                    if (isInteger) {
                        if (isWide) {
                            return left.longValue() + right.longValue();
                        } else {
                            return left.intValue() + right.intValue();
                        }
                    } else {
                        if (isWide) {
                            return left.doubleValue() + right.doubleValue();
                        } else {
                            return left.floatValue() + right.floatValue();
                        }
                    }
                } else if (operator == JavaTokenType.MINUS) {
                    if (isInteger) {
                        if (isWide) {
                            return left.longValue() - right.longValue();
                        } else {
                            return left.intValue() - right.intValue();
                        }
                    } else {
                        if (isWide) {
                            return left.doubleValue() - right.doubleValue();
                        } else {
                            return left.floatValue() - right.floatValue();
                        }
                    }
                } else if (operator == JavaTokenType.ASTERISK) {
                    if (isInteger) {
                        if (isWide) {
                            return left.longValue() * right.longValue();
                        } else {
                            return left.intValue() * right.intValue();
                        }
                    } else {
                        if (isWide) {
                            return left.doubleValue() * right.doubleValue();
                        } else {
                            return left.floatValue() * right.floatValue();
                        }
                    }
                } else if (operator == JavaTokenType.DIV) {
                    if (isInteger) {
                        if (isWide) {
                            return left.longValue() / right.longValue();
                        } else {
                            return left.intValue() / right.intValue();
                        }
                    } else {
                        if (isWide) {
                            return left.doubleValue() / right.doubleValue();
                        } else {
                            return left.floatValue() / right.floatValue();
                        }
                    }
                } else if (operator == JavaTokenType.PERC) {
                    if (isInteger) {
                        if (isWide) {
                            return left.longValue() % right.longValue();
                        } else {
                            return left.intValue() % right.intValue();
                        }
                    } else {
                        if (isWide) {
                            return left.doubleValue() % right.doubleValue();
                        } else {
                            return left.floatValue() % right.floatValue();
                        }
                    }
                } else {
                    return null;
                }
            }
        } else if (node instanceof PsiPolyadicExpression) {
            PsiPolyadicExpression expression = (PsiPolyadicExpression) node;
            IElementType operator = expression.getOperationTokenType();
            PsiExpression[] operands = expression.getOperands();
            List<Object> values = new ArrayList<>(operands.length);

            boolean hasString = false;
            boolean hasBoolean = false;
            boolean hasNumber = false;

            boolean isFloat = false;
            boolean isWide = false;

            for (PsiExpression operand : operands) {
                Object value = evaluate(operand);
                if (value != null) {
                    values.add(value);

                    if (value instanceof String) {
                        hasString = true;
                    } else if (value instanceof Boolean) {
                        hasBoolean = true;
                    } else if (value instanceof Number) {
                        if (value instanceof Float) {
                            isFloat = true;
                        } else if (value instanceof Double) {
                            isFloat = true;
                            isWide = true;
                        } else if (value instanceof Long) {
                            isWide = true;
                        }
                        hasNumber = true;
                    }
                }
            }

            if (values.isEmpty()) {
                return null;
            }

            if (hasString) {
                if (operator == JavaTokenType.PLUS) {
                    // String concatenation
                    StringBuilder sb = new StringBuilder();
                    for (Object value : values) {
                        sb.append(value.toString());
                    }
                    return sb.toString();
                }
                return null;
            }

            if (!allowUnknown && operands.length != values.size()) {
                return null;
            }

            if (hasBoolean) {
                if (operator == JavaTokenType.OROR) {
                    boolean result = false;
                    for (Object value : values) {
                        if (value instanceof Boolean) {
                            result = result || (Boolean) value;
                        }
                    }
                    return result;
                } else if (operator == JavaTokenType.ANDAND) {
                    boolean result = true;
                    for (Object value : values) {
                        if (value instanceof Boolean) {
                            result = result && (Boolean) value;
                        }
                    }
                    return result;
                } else if (operator == JavaTokenType.OR) {
                    boolean result = false;
                    for (Object value : values) {
                        if (value instanceof Boolean) {
                            result = result | (Boolean) value;
                        }
                    }
                    return result;
                } else if (operator == JavaTokenType.XOR) {
                    boolean result = false;
                    for (Object value : values) {
                        if (value instanceof Boolean) {
                            result = result ^ (Boolean) value;
                        }
                    }
                    return result;
                } else if (operator == JavaTokenType.AND) {
                    boolean result = true;
                    for (Object value : values) {
                        if (value instanceof Boolean) {
                            result = result & (Boolean) value;
                        }
                    }
                    return result;
                } else if (operator == JavaTokenType.EQEQ) {
                    boolean result = false;
                    for (int i = 0; i < values.size(); i++) {
                        Object value = values.get(i);
                        if (value instanceof Boolean) {
                            boolean b = (boolean) value;
                            if (i == 0) {
                                result = b;
                            } else {
                                result = result == b;
                            }
                        }
                    }
                    return result;
                } else if (operator == JavaTokenType.NE) {
                    boolean result = false;
                    for (int i = 0; i < values.size(); i++) {
                        Object value = values.get(i);
                        if (value instanceof Boolean) {
                            boolean b = (boolean) value;
                            if (i == 0) {
                                result = b;
                            } else {
                                result = result != b;
                            }
                        }
                    }
                    return result;
                }
                return null;
            }

            if (hasNumber) {
                // TODO: This is super redundant. Switch to lambdas!
                if (operator == JavaTokenType.OR) {
                    if (isWide) {
                        long result = 0L;
                        for (int i = 0; i < values.size(); i++) {
                            Object value = values.get(i);
                            if (value instanceof Number) {
                                long l = ((Number) value).longValue();
                                if (i == 0) {
                                    result = l;
                                } else {
                                    result = result | l;
                                }
                            }
                        }
                        return result;
                    } else {
                        int result = 0;
                        for (int i = 0; i < values.size(); i++) {
                            Object value = values.get(i);
                            if (value instanceof Number) {
                                int l = ((Number) value).intValue();
                                if (i == 0) {
                                    result = l;
                                } else {
                                    result = result | l;
                                }
                            }
                        }
                        return result;
                    }
                } else if (operator == JavaTokenType.XOR) {
                    if (isWide) {
                        long result = 0L;
                        for (int i = 0; i < values.size(); i++) {
                            Object value = values.get(i);
                            if (value instanceof Number) {
                                long l = ((Number) value).longValue();
                                if (i == 0) {
                                    result = l;
                                } else {
                                    result = result ^ l;
                                }
                            }
                        }
                        return result;
                    } else {
                        int result = 0;
                        for (int i = 0; i < values.size(); i++) {
                            Object value = values.get(i);
                            if (value instanceof Number) {
                                int l = ((Number) value).intValue();
                                if (i == 0) {
                                    result = l;
                                } else {
                                    result = result ^ l;
                                }
                            }
                        }
                        return result;
                    }
                } else if (operator == JavaTokenType.AND) {
                    if (isWide) {
                        long result = 0L;
                        for (int i = 0; i < values.size(); i++) {
                            Object value = values.get(i);
                            if (value instanceof Number) {
                                long l = ((Number) value).longValue();
                                if (i == 0) {
                                    result = l;
                                } else {
                                    result = result & l;
                                }
                            }
                        }
                        return result;
                    } else {
                        int result = 0;
                        for (int i = 0; i < values.size(); i++) {
                            Object value = values.get(i);
                            if (value instanceof Number) {
                                int l = ((Number) value).intValue();
                                if (i == 0) {
                                    result = l;
                                } else {
                                    result = result & l;
                                }
                            }
                        }
                        return result;
                    }
                } else if (operator == JavaTokenType.EQEQ) {
                    if (isWide) {
                        boolean result = false;
                        long prev = 0;
                        for (int i = 0; i < values.size(); i++) {
                            Object value = values.get(i);
                            if (value instanceof Number) {
                                long l = ((Number) value).longValue();
                                if (i != 0) {
                                    result = prev == l;
                                }
                                prev = l;
                            }
                        }
                        return result;
                    } else {
                        boolean result = false;
                        int prev = 0;
                        for (int i = 0; i < values.size(); i++) {
                            Object value = values.get(i);
                            if (value instanceof Number) {
                                int l = ((Number) value).intValue();
                                if (i != 0) {
                                    result = prev == l;
                                }
                                prev = l;
                            }
                        }
                        return result;
                    }
                } else if (operator == JavaTokenType.NE) {
                    if (isWide) {
                        boolean result = false;
                        long prev = 0;
                        for (int i = 0; i < values.size(); i++) {
                            Object value = values.get(i);
                            if (value instanceof Number) {
                                long l = ((Number) value).longValue();
                                if (i != 0) {
                                    result = prev != l;
                                }
                                prev = l;
                            }
                        }
                        return result;
                    } else {
                        boolean result = false;
                        int prev = 0;
                        for (int i = 0; i < values.size(); i++) {
                            Object value = values.get(i);
                            if (value instanceof Number) {
                                int l = ((Number) value).intValue();
                                if (i != 0) {
                                    result = prev != l;
                                }
                                prev = l;
                            }
                        }
                        return result;
                    }
                } else if (operator == JavaTokenType.GT) {
                    if (isWide) {
                        boolean result = false;
                        long prev = 0;
                        for (int i = 0; i < values.size(); i++) {
                            Object value = values.get(i);
                            if (value instanceof Number) {
                                long l = ((Number) value).longValue();
                                if (i != 0) {
                                    result = prev > l;
                                }
                                prev = l;
                            }
                        }
                        return result;
                    } else {
                        boolean result = false;
                        int prev = 0;
                        for (int i = 0; i < values.size(); i++) {
                            Object value = values.get(i);
                            if (value instanceof Number) {
                                int l = ((Number) value).intValue();
                                if (i != 0) {
                                    result = prev > l;
                                }
                                prev = l;
                            }
                        }
                        return result;
                    }
                } else if (operator == JavaTokenType.GE) {
                    if (isWide) {
                        boolean result = false;
                        long prev = 0;
                        for (int i = 0; i < values.size(); i++) {
                            Object value = values.get(i);
                            if (value instanceof Number) {
                                long l = ((Number) value).longValue();
                                if (i != 0) {
                                    result = prev >= l;
                                }
                                prev = l;
                            }
                        }
                        return result;
                    } else {
                        boolean result = false;
                        int prev = 0;
                        for (int i = 0; i < values.size(); i++) {
                            Object value = values.get(i);
                            if (value instanceof Number) {
                                int l = ((Number) value).intValue();
                                if (i != 0) {
                                    result = prev >= l;
                                }
                                prev = l;
                            }
                        }
                        return result;
                    }
                } else if (operator == JavaTokenType.LT) {
                    if (isWide) {
                        boolean result = false;
                        long prev = 0;
                        for (int i = 0; i < values.size(); i++) {
                            Object value = values.get(i);
                            if (value instanceof Number) {
                                long l = ((Number) value).longValue();
                                if (i != 0) {
                                    result = prev < l;
                                }
                                prev = l;
                            }
                        }
                        return result;
                    } else {
                        boolean result = false;
                        int prev = 0;
                        for (int i = 0; i < values.size(); i++) {
                            Object value = values.get(i);
                            if (value instanceof Number) {
                                int l = ((Number) value).intValue();
                                if (i != 0) {
                                    result = prev < l;
                                }
                                prev = l;
                            }
                        }
                        return result;
                    }
                } else if (operator == JavaTokenType.LE) {
                    if (isWide) {
                        boolean result = false;
                        long prev = 0;
                        for (int i = 0; i < values.size(); i++) {
                            Object value = values.get(i);
                            if (value instanceof Number) {
                                long l = ((Number) value).longValue();
                                if (i != 0) {
                                    result = prev <= l;
                                }
                                prev = l;
                            }
                        }
                        return result;
                    } else {
                        boolean result = false;
                        int prev = 0;
                        for (int i = 0; i < values.size(); i++) {
                            Object value = values.get(i);
                            if (value instanceof Number) {
                                int l = ((Number) value).intValue();
                                if (i != 0) {
                                    result = prev <= l;
                                }
                                prev = l;
                            }
                        }
                        return result;
                    }
                } else if (operator == JavaTokenType.LTLT) {
                    if (isWide) {
                        long result = 0L;
                        for (int i = 0; i < values.size(); i++) {
                            Object value = values.get(i);
                            if (value instanceof Number) {
                                long l = ((Number) value).longValue();
                                if (i == 0) {
                                    result = l;
                                } else {
                                    result = result << l;
                                }
                            }
                        }
                        return result;
                    } else {
                        int result = 0;
                        for (int i = 0; i < values.size(); i++) {
                            Object value = values.get(i);
                            if (value instanceof Number) {
                                int l = ((Number) value).intValue();
                                if (i == 0) {
                                    result = l;
                                } else {
                                    result = result << l;
                                }
                            }
                        }
                        return result;
                    }
                } else if (operator == JavaTokenType.GTGT) {
                    if (isWide) {
                        long result = 0L;
                        for (int i = 0; i < values.size(); i++) {
                            Object value = values.get(i);
                            if (value instanceof Number) {
                                long l = ((Number) value).longValue();
                                if (i == 0) {
                                    result = l;
                                } else {
                                    result = result >> l;
                                }
                            }
                        }
                        return result;
                    } else {
                        int result = 0;
                        for (int i = 0; i < values.size(); i++) {
                            Object value = values.get(i);
                            if (value instanceof Number) {
                                int l = ((Number) value).intValue();
                                if (i == 0) {
                                    result = l;
                                } else {
                                    result = result >> l;
                                }
                            }
                        }
                        return result;
                    }
                } else if (operator == JavaTokenType.GTGTGT) {
                    if (isWide) {
                        long result = 0L;
                        for (int i = 0; i < values.size(); i++) {
                            Object value = values.get(i);
                            if (value instanceof Number) {
                                long l = ((Number) value).longValue();
                                if (i == 0) {
                                    result = l;
                                } else {
                                    result = result >>> l;
                                }
                            }
                        }
                        return result;
                    } else {
                        int result = 0;
                        for (int i = 0; i < values.size(); i++) {
                            Object value = values.get(i);
                            if (value instanceof Number) {
                                int l = ((Number) value).intValue();
                                if (i == 0) {
                                    result = l;
                                } else {
                                    result = result >>> l;
                                }
                            }
                        }
                        return result;
                    }
                } else if (operator == JavaTokenType.PLUS) {
                    if (isFloat) {
                        if (isWide) {
                            double result = 0;
                            for (int i = 0; i < values.size(); i++) {
                                Object value = values.get(i);
                                if (value instanceof Number) {
                                    double l = ((Number) value).doubleValue();
                                    if (i == 0) {
                                        result = l;
                                    } else {
                                        result = result + l;
                                    }
                                }
                            }
                            return result;
                        } else {
                            float result = 0f;
                            for (int i = 0; i < values.size(); i++) {
                                Object value = values.get(i);
                                if (value instanceof Number) {
                                    float l = ((Number) value).floatValue();
                                    if (i == 0) {
                                        result = l;
                                    } else {
                                        result = result + l;
                                    }
                                }
                            }
                            return result;
                        }
                    } else {
                        if (isWide) {
                            long result = 0L;
                            for (int i = 0; i < values.size(); i++) {
                                Object value = values.get(i);
                                if (value instanceof Number) {
                                    long l = ((Number) value).longValue();
                                    if (i == 0) {
                                        result = l;
                                    } else {
                                        result = result + l;
                                    }
                                }
                            }
                            return result;
                        } else {
                            int result = 0;
                            for (int i = 0; i < values.size(); i++) {
                                Object value = values.get(i);
                                if (value instanceof Number) {
                                    int l = ((Number) value).intValue();
                                    if (i == 0) {
                                        result = l;
                                    } else {
                                        result = result + l;
                                    }
                                }
                            }
                            return result;
                        }
                    }
                } else if (operator == JavaTokenType.MINUS) {
                    if (isFloat) {
                        if (isWide) {
                            double result = 0;
                            for (int i = 0; i < values.size(); i++) {
                                Object value = values.get(i);
                                if (value instanceof Number) {
                                    double l = ((Number) value).doubleValue();
                                    if (i == 0) {
                                        result = l;
                                    } else {
                                        result = result - l;
                                    }
                                }
                            }
                            return result;
                        } else {
                            float result = 0f;
                            for (int i = 0; i < values.size(); i++) {
                                Object value = values.get(i);
                                if (value instanceof Number) {
                                    float l = ((Number) value).floatValue();
                                    if (i == 0) {
                                        result = l;
                                    } else {
                                        result = result - l;
                                    }
                                }
                            }
                            return result;
                        }
                    } else {
                        if (isWide) {
                            long result = 0L;
                            for (int i = 0; i < values.size(); i++) {
                                Object value = values.get(i);
                                if (value instanceof Number) {
                                    long l = ((Number) value).longValue();
                                    if (i == 0) {
                                        result = l;
                                    } else {
                                        result = result - l;
                                    }
                                }
                            }
                            return result;
                        } else {
                            int result = 0;
                            for (int i = 0; i < values.size(); i++) {
                                Object value = values.get(i);
                                if (value instanceof Number) {
                                    int l = ((Number) value).intValue();
                                    if (i == 0) {
                                        result = l;
                                    } else {
                                        result = result - l;
                                    }
                                }
                            }
                            return result;
                        }
                    }
                } else if (operator == JavaTokenType.ASTERISK) {
                    if (isFloat) {
                        if (isWide) {
                            double result = 0;
                            for (int i = 0; i < values.size(); i++) {
                                Object value = values.get(i);
                                if (value instanceof Number) {
                                    double l = ((Number) value).doubleValue();
                                    if (i == 0) {
                                        result = l;
                                    } else {
                                        result = result * l;
                                    }
                                }
                            }
                            return result;
                        } else {
                            float result = 0f;
                            for (int i = 0; i < values.size(); i++) {
                                Object value = values.get(i);
                                if (value instanceof Number) {
                                    float l = ((Number) value).floatValue();
                                    if (i == 0) {
                                        result = l;
                                    } else {
                                        result = result * l;
                                    }
                                }
                            }
                            return result;
                        }
                    } else {
                        if (isWide) {
                            long result = 0L;
                            for (int i = 0; i < values.size(); i++) {
                                Object value = values.get(i);
                                if (value instanceof Number) {
                                    long l = ((Number) value).longValue();
                                    if (i == 0) {
                                        result = l;
                                    } else {
                                        result = result * l;
                                    }
                                }
                            }
                            return result;
                        } else {
                            int result = 0;
                            for (int i = 0; i < values.size(); i++) {
                                Object value = values.get(i);
                                if (value instanceof Number) {
                                    int l = ((Number) value).intValue();
                                    if (i == 0) {
                                        result = l;
                                    } else {
                                        result = result * l;
                                    }
                                }
                            }
                            return result;
                        }
                    }
                } else if (operator == JavaTokenType.DIV) {
                    if (isFloat) {
                        if (isWide) {
                            double result = 0;
                            for (int i = 0; i < values.size(); i++) {
                                Object value = values.get(i);
                                if (value instanceof Number) {
                                    double l = ((Number) value).doubleValue();
                                    if (i == 0) {
                                        result = l;
                                    } else {
                                        result = result / l;
                                    }
                                }
                            }
                            return result;
                        } else {
                            float result = 0f;
                            for (int i = 0; i < values.size(); i++) {
                                Object value = values.get(i);
                                if (value instanceof Number) {
                                    float l = ((Number) value).floatValue();
                                    if (i == 0) {
                                        result = l;
                                    } else {
                                        result = result / l;
                                    }
                                }
                            }
                            return result;
                        }
                    } else {
                        if (isWide) {
                            long result = 0L;
                            for (int i = 0; i < values.size(); i++) {
                                Object value = values.get(i);
                                if (value instanceof Number) {
                                    long l = ((Number) value).longValue();
                                    if (i == 0) {
                                        result = l;
                                    } else {
                                        result = result / l;
                                    }
                                }
                            }
                            return result;
                        } else {
                            int result = 0;
                            for (int i = 0; i < values.size(); i++) {
                                Object value = values.get(i);
                                if (value instanceof Number) {
                                    int l = ((Number) value).intValue();
                                    if (i == 0) {
                                        result = l;
                                    } else {
                                        result = result / l;
                                    }
                                }
                            }
                            return result;
                        }
                    }
                } else if (operator == JavaTokenType.PERC) {
                    if (isFloat) {
                        if (isWide) {
                            double result = 0;
                            for (int i = 0; i < values.size(); i++) {
                                Object value = values.get(i);
                                if (value instanceof Number) {
                                    double l = ((Number) value).doubleValue();
                                    if (i == 0) {
                                        result = l;
                                    } else {
                                        result = result % l;
                                    }
                                }
                            }
                            return result;
                        } else {
                            float result = 0f;
                            for (int i = 0; i < values.size(); i++) {
                                Object value = values.get(i);
                                if (value instanceof Number) {
                                    float l = ((Number) value).floatValue();
                                    if (i == 0) {
                                        result = l;
                                    } else {
                                        result = result % l;
                                    }
                                }
                            }
                            return result;
                        }
                    } else {
                        if (isWide) {
                            long result = 0L;
                            for (int i = 0; i < values.size(); i++) {
                                Object value = values.get(i);
                                if (value instanceof Number) {
                                    long l = ((Number) value).longValue();
                                    if (i == 0) {
                                        result = l;
                                    } else {
                                        result = result % l;
                                    }
                                }
                            }
                            return result;
                        } else {
                            int result = 0;
                            for (int i = 0; i < values.size(); i++) {
                                Object value = values.get(i);
                                if (value instanceof Number) {
                                    int l = ((Number) value).intValue();
                                    if (i == 0) {
                                        result = l;
                                    } else {
                                        result = result % l;
                                    }
                                }
                            }
                            return result;
                        }
                    }
                } else {
                    return null;
                }
            }
        } else if (node instanceof PsiTypeCastExpression) {
            PsiTypeCastExpression cast = (PsiTypeCastExpression) node;
            Object operandValue = evaluate(cast.getOperand());
            if (operandValue instanceof Number) {
                Number number = (Number) operandValue;
                PsiTypeElement typeElement = cast.getCastType();
                if (typeElement != null) {
                    PsiType type = typeElement.getType();
                    if (PsiType.FLOAT.equals(type)) {
                        return number.floatValue();
                    } else if (PsiType.DOUBLE.equals(type)) {
                        return number.doubleValue();
                    } else if (PsiType.INT.equals(type)) {
                        return number.intValue();
                    } else if (PsiType.LONG.equals(type)) {
                        return number.longValue();
                    } else if (PsiType.SHORT.equals(type)) {
                        return number.shortValue();
                    } else if (PsiType.BYTE.equals(type)) {
                        return number.byteValue();
                    }
                }
            }
            return operandValue;
        } else if (node instanceof PsiReference) {
            PsiElement resolved = ((PsiReference) node).resolve();
            if (resolved instanceof PsiField) {
                PsiField field = (PsiField) resolved;

                // array.length expression?
                if ("length".equals(field.getName()) && node instanceof PsiReferenceExpression) {
                    PsiExpression expression =
                            ((PsiReferenceExpression) node).getQualifierExpression();
                    if (expression != null && expression.getType() instanceof PsiArrayType) {
                        // It's an array.length expression
                        Object array = evaluate(expression);
                        int size = getArraySize(array);
                        if (size != -1) {
                            return size;
                        }
                        return null;
                    }
                }

                Object value = field.computeConstantValue();
                if (value != null) {
                    return value;
                }
                if (field.getInitializer() != null
                        && (allowFieldInitializers
                                || (field.hasModifierProperty(PsiModifier.STATIC)
                                        && field.hasModifierProperty(PsiModifier.FINAL)))) {
                    value = evaluate(field.getInitializer());
                    if (value != null) {
                        // See if it looks like the value has been clamped locally
                        PsiIfStatement curr =
                                PsiTreeUtil.getParentOfType(node, PsiIfStatement.class);
                        while (curr != null) {
                            if (curr.getCondition() != null
                                    && references(curr.getCondition(), field)) {
                                // Field is referenced surrounding this reference; don't
                                // take the field initializer since the value may have been
                                // value checked for some other later assigned value
                                // ...but only if it's not the condition!
                                PsiExpression condition = curr.getCondition();
                                if (!PsiTreeUtil.isAncestor(condition, node, true)) {
                                    return value;
                                }
                            }
                            curr = PsiTreeUtil.getParentOfType(curr, PsiIfStatement.class, true);
                        }

                        return value;
                    }
                }
                return null;
            } else if (resolved instanceof PsiLocalVariable) {
                PsiLocalVariable variable = (PsiLocalVariable) resolved;
                PsiExpression last = findLastAssignment(node, variable);
                if (last != null) {
                    // TODO: Clamp value as is done for UAST?
                    return evaluate(last);
                }
            }
        } else if (node instanceof PsiNewExpression) {
            PsiNewExpression creation = (PsiNewExpression) node;
            PsiArrayInitializerExpression initializer = creation.getArrayInitializer();
            PsiType type = creation.getType();
            if (type instanceof PsiArrayType) {
                if (initializer != null) {
                    PsiExpression[] initializers = initializer.getInitializers();
                    Class<?> commonType = null;
                    List<Object> values = Lists.newArrayListWithExpectedSize(initializers.length);
                    int count = 0;
                    for (PsiExpression expression : initializers) {
                        Object value = evaluate(expression);
                        if (value != null) {
                            values.add(value);
                            if (commonType == null) {
                                commonType = value.getClass();
                            } else {
                                while (!commonType.isAssignableFrom(value.getClass())) {
                                    commonType = commonType.getSuperclass();
                                }
                            }
                        } else if (!allowUnknown) {
                            // Inconclusive
                            return null;
                        }
                        count++;
                        if (count == 40) { // avoid large initializers
                            return getArray(type.getDeepComponentType(), initializers.length, 1);
                        }
                    }
                    type = type.getDeepComponentType();
                    if (type == PsiType.INT) {
                        if (!values.isEmpty()) {
                            int[] array = new int[values.size()];
                            for (int i = 0; i < values.size(); i++) {
                                Object o = values.get(i);
                                if (o instanceof Integer) {
                                    array[i] = (Integer) o;
                                }
                            }
                            return array;
                        }
                        return new int[0];
                    } else if (type == PsiType.BOOLEAN) {
                        if (!values.isEmpty()) {
                            boolean[] array = new boolean[values.size()];
                            for (int i = 0; i < values.size(); i++) {
                                Object o = values.get(i);
                                if (o instanceof Boolean) {
                                    array[i] = (Boolean) o;
                                }
                            }
                            return array;
                        }
                        return new boolean[0];
                    } else if (type == PsiType.DOUBLE) {
                        if (!values.isEmpty()) {
                            double[] array = new double[values.size()];
                            for (int i = 0; i < values.size(); i++) {
                                Object o = values.get(i);
                                if (o instanceof Double) {
                                    array[i] = (Double) o;
                                }
                            }
                            return array;
                        }
                        return new double[0];
                    } else if (type == PsiType.LONG) {
                        if (!values.isEmpty()) {
                            long[] array = new long[values.size()];
                            for (int i = 0; i < values.size(); i++) {
                                Object o = values.get(i);
                                if (o instanceof Long) {
                                    array[i] = (Long) o;
                                }
                            }
                            return array;
                        }
                        return new long[0];
                    } else if (type == PsiType.FLOAT) {
                        if (!values.isEmpty()) {
                            float[] array = new float[values.size()];
                            for (int i = 0; i < values.size(); i++) {
                                Object o = values.get(i);
                                if (o instanceof Float) {
                                    array[i] = (Float) o;
                                }
                            }
                            return array;
                        }
                        return new float[0];
                    } else if (type == PsiType.CHAR) {
                        if (!values.isEmpty()) {
                            char[] array = new char[values.size()];
                            for (int i = 0; i < values.size(); i++) {
                                Object o = values.get(i);
                                if (o instanceof Character) {
                                    array[i] = (Character) o;
                                }
                            }
                            return array;
                        }
                        return new char[0];
                    } else if (type == PsiType.BYTE) {
                        if (!values.isEmpty()) {
                            byte[] array = new byte[values.size()];
                            for (int i = 0; i < values.size(); i++) {
                                Object o = values.get(i);
                                if (o instanceof Byte) {
                                    array[i] = (Byte) o;
                                }
                            }
                            return array;
                        }
                        return new byte[0];
                    } else if (type == PsiType.SHORT) {
                        if (!values.isEmpty()) {
                            short[] array = new short[values.size()];
                            for (int i = 0; i < values.size(); i++) {
                                Object o = values.get(i);
                                if (o instanceof Short) {
                                    array[i] = (Short) o;
                                }
                            }
                            return array;
                        }
                        return new short[0];
                    } else {
                        if (!values.isEmpty()) {
                            Object o = Array.newInstance(commonType, values.size());
                            return values.toArray((Object[]) o);
                        }
                        return null;
                    }
                } else {
                    // something like "new byte[3]" but with no initializer.
                    // Look up the size and only if small, use it. E.g. if it was byte[3]
                    // we return a byte[3] array, but if it's say byte[1024*1024] we don't
                    // want to do that.
                    PsiExpression[] arrayDimensions = creation.getArrayDimensions();

                    int size = 0;
                    if (arrayDimensions.length > 0) {
                        Object fixedSize = evaluate(arrayDimensions[0]);
                        if (fixedSize instanceof Number) {
                            size = ((Number) fixedSize).intValue();
                        }
                    }
                    int dimensions = type.getArrayDimensions();
                    type = type.getDeepComponentType();
                    return getArray(type, size, dimensions);
                }
            }
        }

        // TODO: Check for MethodInvocation and perform some common operations -
        // Math.* methods, String utility methods like notNullize, etc

        return null;
    }

    @Nullable
    private static Object getArray(PsiType type, int size, int dimensions) {
        if (type instanceof PsiPrimitiveType) {
            if (size <= LARGEST_LITERAL_ARRAY) {
                if (PsiType.BYTE.equals(type)) {
                    return new byte[size];
                }
                if (PsiType.BOOLEAN.equals(type)) {
                    return new boolean[size];
                }
                if (PsiType.INT.equals(type)) {
                    return new int[size];
                }
                if (PsiType.LONG.equals(type)) {
                    return new long[size];
                }
                if (PsiType.CHAR.equals(type)) {
                    return new char[size];
                }
                if (PsiType.FLOAT.equals(type)) {
                    return new float[size];
                }
                if (PsiType.DOUBLE.equals(type)) {
                    return new double[size];
                }
                if (PsiType.SHORT.equals(type)) {
                    return new short[size];
                }
            } else {
                if (PsiType.BYTE.equals(type)) {
                    return new ArrayReference(Byte.TYPE, size, dimensions);
                }
                if (PsiType.BOOLEAN.equals(type)) {
                    return new ArrayReference(Boolean.TYPE, size, dimensions);
                }
                if (PsiType.INT.equals(type)) {
                    return new ArrayReference(Integer.TYPE, size, dimensions);
                }
                if (PsiType.LONG.equals(type)) {
                    return new ArrayReference(Long.TYPE, size, dimensions);
                }
                if (PsiType.CHAR.equals(type)) {
                    return new ArrayReference(Character.TYPE, size, dimensions);
                }
                if (PsiType.FLOAT.equals(type)) {
                    return new ArrayReference(Float.TYPE, size, dimensions);
                }
                if (PsiType.DOUBLE.equals(type)) {
                    return new ArrayReference(Double.TYPE, size, dimensions);
                }
                if (PsiType.SHORT.equals(type)) {
                    return new ArrayReference(Short.TYPE, size, dimensions);
                }
            }
        } else if (type instanceof PsiClassType) {
            String className = type.getCanonicalText();
            if (TYPE_STRING.equals(className)) {
                if (size < LARGEST_LITERAL_ARRAY) {
                    //noinspection SSBasedInspection
                    return new String[size];
                } else {
                    return new ArrayReference(String.class, size, dimensions);
                }
            } else if (TYPE_OBJECT.equals(className)) {
                if (size < LARGEST_LITERAL_ARRAY) {
                    //noinspection SSBasedInspection
                    return new Object[size];
                } else {
                    return new ArrayReference(Object.class, size, dimensions);
                }
            } else {
                return new ArrayReference(className, size, dimensions);
            }
        }
        return null;
    }

    public static class ArrayReference {
        @Nullable public final Class<?> type;
        @Nullable public final String className;
        public final int size;
        public final int dimensions;

        public ArrayReference(@Nullable Class<?> type, int size, int dimensions) {
            this.type = type;
            this.className = null;
            this.size = size;
            this.dimensions = dimensions;
        }

        public ArrayReference(@Nullable String className, int size, int dimensions) {
            this.className = className;
            this.type = null;
            this.size = size;
            this.dimensions = dimensions;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ArrayReference that = (ArrayReference) o;
            return size == that.size
                    && dimensions == that.dimensions
                    && Objects.equals(type, that.type)
                    && Objects.equals(className, that.className);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, className, size, dimensions);
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Array Reference: ");
            if (type != null) {
                sb.append(type.toString());
            } else if (className != null) {
                sb.append(className);
            }
            for (int i = 0; i < dimensions - 1; i++) {
                sb.append("[]");
            }
            sb.append("[");
            sb.append(Integer.toString(size));
            sb.append("]");
            return sb.toString();
        }
    }

    /** Returns true if the given variable is referenced from within the given element */
    private static boolean references(
            @NonNull PsiExpression element, @NonNull PsiVariable variable) {
        AtomicBoolean found = new AtomicBoolean();
        element.accept(
                new JavaRecursiveElementVisitor() {
                    @Override
                    public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
                        PsiElement refersTo = reference.resolve();
                        if (variable.equals(refersTo)) {
                            found.set(true);
                        }
                        super.visitReferenceElement(reference);
                    }
                });

        return found.get();
    }

    /** Returns true if the given variable is referenced from within the given element */
    private static boolean references(@NonNull UExpression element, @NonNull PsiVariable variable) {
        AtomicBoolean found = new AtomicBoolean();
        element.accept(
                new AbstractUastVisitor() {
                    @Override
                    public boolean visitSimpleNameReferenceExpression(
                            USimpleNameReferenceExpression node) {
                        PsiElement refersTo = node.resolve();
                        if (variable.equals(refersTo)) {
                            found.set(true);
                        }

                        return super.visitSimpleNameReferenceExpression(node);
                    }

                    @Override
                    public boolean visitQualifiedReferenceExpression(
                            UQualifiedReferenceExpression node) {
                        return super.visitQualifiedReferenceExpression(node);
                    }
                });

        return found.get();
    }

    /** Returns true if the node is pointing to a an array literal */
    public static boolean isArrayLiteral(@Nullable PsiElement node) {
        if (node instanceof PsiReference) {
            PsiElement resolved = ((PsiReference) node).resolve();
            if (resolved instanceof PsiField) {
                PsiField field = (PsiField) resolved;
                if (field.getInitializer() != null) {
                    return isArrayLiteral(field.getInitializer());
                }
            } else if (resolved instanceof PsiLocalVariable) {
                PsiLocalVariable variable = (PsiLocalVariable) resolved;
                PsiExpression last = findLastAssignment(node, variable);
                if (last != null) {
                    return isArrayLiteral(last);
                }
            }
        } else if (node instanceof PsiNewExpression) {
            PsiNewExpression creation = (PsiNewExpression) node;
            if (creation.getArrayInitializer() != null) {
                return true;
            }
            PsiType type = creation.getType();
            if (type instanceof PsiArrayType) {
                return true;
            }
        } else if (node instanceof PsiParenthesizedExpression) {
            PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression) node;
            PsiExpression expression = parenthesizedExpression.getExpression();
            if (expression != null) {
                return isArrayLiteral(expression);
            }
        } else if (node instanceof PsiTypeCastExpression) {
            PsiTypeCastExpression castExpression = (PsiTypeCastExpression) node;
            PsiExpression operand = castExpression.getOperand();
            if (operand != null) {
                return isArrayLiteral(operand);
            }
        }

        return false;
    }

    /** Returns true if the node is pointing to a an array literal */
    public static boolean isArrayLiteral(@Nullable UElement node) {
        if (node instanceof UReferenceExpression) {
            PsiElement resolved = ((UReferenceExpression) node).resolve();
            if (resolved instanceof PsiVariable) {
                PsiVariable variable = (PsiVariable) resolved;
                UExpression lastAssignment = UastLintUtils.findLastAssignment(variable, node);

                if (lastAssignment != null) {
                    return isArrayLiteral(lastAssignment);
                }
            }
        } else if (UastExpressionUtils.isNewArrayWithDimensions(node)) {
            return true;
        } else if (UastExpressionUtils.isNewArrayWithInitializer(node)) {
            return true;
        } else if (node instanceof UParenthesizedExpression) {
            UParenthesizedExpression parenthesizedExpression = (UParenthesizedExpression) node;
            UExpression expression = parenthesizedExpression.getExpression();
            return isArrayLiteral(expression);
        } else if (UastExpressionUtils.isTypeCast(node)) {
            UBinaryExpressionWithType castExpression = (UBinaryExpressionWithType) node;
            assert castExpression != null;
            UExpression operand = castExpression.getOperand();
            return isArrayLiteral(operand);
        }

        return false;
    }

    /**
     * Evaluates the given node and returns the constant value it resolves to, if any. Convenience
     * wrapper which creates a new {@linkplain ConstantEvaluator}, evaluates the node and returns
     * the result.
     *
     * @param context the context to use to resolve field references, if any
     * @param node the node to compute the constant value for
     * @return the corresponding constant value - a String, an Integer, a Float, and so on
     */
    @Nullable
    public static Object evaluate(@Nullable JavaContext context, @NonNull PsiElement node) {
        Object evaluate = new ConstantEvaluator().evaluate(node);
        /* TODO: Switch to JavaConstantExpressionEvaluator (or actually, more accurately
          psiFacade.getConstantEvaluationHelper().computeConstantExpression(expressionToEvaluate);
          However, there are a few gaps; in particular, lint's evaluator will do more with arrays
          and array sizes. Transfer that or keep *just* that portion and get rid of the number
          and boolean evaluation logic. (There's also the "allowUnknown" behavior, which is
          particularly important for Strings.
        if (node instanceof PsiExpression) {
            Object o = JavaConstantExpressionEvaluator
                    .computeConstantExpression((PsiExpression) node, false);
            // For comparison purposes switch from int to long and float to double
            if (o instanceof Float) {
                o = ((Float)o).doubleValue();
            }
            if (o instanceof Integer) {
                o = ((Integer)o).longValue();
            }
            if (evaluate instanceof Float) {
                evaluate = ((Float)evaluate).doubleValue();
            }
            if (evaluate instanceof Integer) {
                evaluate = ((Integer)evaluate).longValue();
            }

            if (!Objects.equals(o, evaluate)) {
                // Allow Integer vs Long etc


                System.out.println("Different:\nLint produced " + evaluate + "\nIdea produced " + o);
                System.out.println();
            }
        }
        */
        return evaluate;
    }

    /**
     * Evaluates the given node and returns the constant value it resolves to, if any. Convenience
     * wrapper which creates a new {@linkplain ConstantEvaluator}, evaluates the node and returns
     * the result.
     *
     * @param context the context to use to resolve field references, if any
     * @param element the node to compute the constant value for
     * @return the corresponding constant value - a String, an Integer, a Float, and so on
     */
    @Nullable
    public static Object evaluate(@Nullable JavaContext context, @NonNull UElement element) {
        if (element instanceof ULiteralExpression) {
            return ((ULiteralExpression) element).getValue();
        }
        return new ConstantEvaluator().evaluate(element);
    }

    /**
     * Evaluates the given node and returns the constant string it resolves to, if any. Convenience
     * wrapper which creates a new {@linkplain ConstantEvaluator}, evaluates the node and returns
     * the result if the result is a string.
     *
     * @param context the context to use to resolve field references, if any
     * @param node the node to compute the constant value for
     * @param allowUnknown whether we should construct the string even if some parts of it are
     *     unknown
     * @return the corresponding string, if any
     */
    @Nullable
    public static String evaluateString(
            @Nullable JavaContext context, @NonNull PsiElement node, boolean allowUnknown) {
        ConstantEvaluator evaluator = new ConstantEvaluator();
        if (allowUnknown) {
            evaluator.allowUnknowns();
        }
        Object value = evaluator.evaluate(node);
        return value instanceof String ? (String) value : null;
    }

    /**
     * Computes the last assignment to a given variable counting backwards from the given context
     * element
     *
     * @param usage the usage site to search backwards from
     * @param variable the variable
     * @return the last assignment or null
     */
    @Nullable
    public static PsiExpression findLastAssignment(
            @NonNull PsiElement usage, @NonNull PsiVariable variable) {
        // Walk backwards through assignments to find the most recent initialization
        // of this variable
        PsiStatement statement = PsiTreeUtil.getParentOfType(usage, PsiStatement.class, false);
        if (statement != null) {
            PsiStatement prev = PsiTreeUtil.getPrevSiblingOfType(statement, PsiStatement.class);
            String targetName = variable.getName();
            if (targetName == null) {
                return null;
            }
            while (prev != null) {
                if (prev instanceof PsiDeclarationStatement) {
                    for (PsiElement element :
                            ((PsiDeclarationStatement) prev).getDeclaredElements()) {
                        if (variable.equals(element)) {
                            return variable.getInitializer();
                        }
                    }
                } else if (prev instanceof PsiExpressionStatement) {
                    PsiExpression expression = ((PsiExpressionStatement) prev).getExpression();
                    if (expression instanceof PsiAssignmentExpression) {
                        PsiAssignmentExpression assign = (PsiAssignmentExpression) expression;
                        PsiExpression lhs = assign.getLExpression();
                        if (lhs instanceof PsiReferenceExpression) {
                            PsiReferenceExpression reference = (PsiReferenceExpression) lhs;
                            if (targetName.equals(reference.getReferenceName())
                                    && reference.getQualifier() == null) {
                                return assign.getRExpression();
                            }
                        }
                    }
                }
                prev = PsiTreeUtil.getPrevSiblingOfType(prev, PsiStatement.class);
            }
        }

        return null;
    }

    /**
     * Evaluates the given node and returns the constant string it resolves to, if any. Convenience
     * wrapper which creates a new {@linkplain ConstantEvaluator}, evaluates the node and returns
     * the result if the result is a string.
     *
     * @param context the context to use to resolve field references, if any
     * @param element the node to compute the constant value for
     * @param allowUnknown whether we should construct the string even if some parts of it are
     *     unknown
     * @return the corresponding string, if any
     */
    @Nullable
    public static String evaluateString(
            @Nullable JavaContext context, @NonNull UElement element, boolean allowUnknown) {
        ConstantEvaluator evaluator = new ConstantEvaluator();
        if (allowUnknown) {
            evaluator.allowUnknowns();
        }
        Object value = evaluator.evaluate(element);
        return value instanceof String ? (String) value : null;
    }
}
