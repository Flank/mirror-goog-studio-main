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
package com.android.tools.lint.detector.api

import com.android.tools.lint.client.api.TYPE_OBJECT
import com.android.tools.lint.client.api.TYPE_STRING
import com.android.tools.lint.detector.api.UastLintUtils.Companion.findLastAssignment
import com.android.tools.lint.detector.api.UastLintUtils.Companion.findLastValue
import com.google.common.collect.Lists
import com.intellij.psi.JavaRecursiveElementVisitor
import com.intellij.psi.JavaTokenType
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiAssignmentExpression
import com.intellij.psi.PsiBinaryExpression
import com.intellij.psi.PsiBlockStatement
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiConditionalExpression
import com.intellij.psi.PsiDeclarationStatement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiExpressionStatement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiIfStatement
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.PsiLiteral
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiParenthesizedExpression
import com.intellij.psi.PsiPolyadicExpression
import com.intellij.psi.PsiPrefixExpression
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiStatement
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeCastExpression
import com.intellij.psi.PsiVariable
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.asJava.elements.KtLightField
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.asJava.elements.KtLightPsiLiteral
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.uast.UArrayAccessExpression
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBinaryExpressionWithType
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UDeclarationsExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.UPrefixExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UResolvable
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UastBinaryExpressionWithTypeKind
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.UastFacade.convertElement
import org.jetbrains.uast.UastPrefixOperator
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.isUastChildOf
import org.jetbrains.uast.util.isConstructorCall
import org.jetbrains.uast.util.isNewArrayWithDimensions
import org.jetbrains.uast.util.isNewArrayWithInitializer
import org.jetbrains.uast.util.isTypeCast
import org.jetbrains.uast.visitor.AbstractUastVisitor
import java.util.Objects
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.experimental.inv
import kotlin.text.trimIndent
import kotlin.text.trimMargin

/** Evaluates constant expressions  */
class ConstantEvaluator
/** Creates a new constant evaluator  */ {
    private var allowUnknown = false
    private var allowFieldInitializers = false

    /**
     * Whether we allow computing values where some terms are unknown. For example, the expression
     * `"foo" + x + "bar"` would return `null` without and `"foobar"` with.
     *
     * @return this for constructor chaining
     */
    fun allowUnknowns(): ConstantEvaluator {
        allowUnknown = true
        return this
    }

    fun allowFieldInitializers(): ConstantEvaluator {
        allowFieldInitializers = true
        return this
    }

    /**
     * Evaluates the given node and returns the constant value it resolves to, if any
     *
     * @param node the node to compute the constant value for
     * @return the corresponding constant value - a String, an Integer, a Float, and so on
     */
    fun evaluate(node: UElement?): Any? {
        if (node == null) {
            return null
        }
        if (node is ULiteralExpression) {
            return node.value
        } else if (node is UPrefixExpression) {
            val operator = node.operator
            val operand = evaluate(node.operand) ?: return null
            if (operator == UastPrefixOperator.LOGICAL_NOT) {
                if (operand is Boolean) {
                    return !operand
                }
            } else if (operator == UastPrefixOperator.UNARY_PLUS) {
                return operand
            } else if (operator == UastPrefixOperator.BITWISE_NOT) {
                if (operand is Int) {
                    return operand.toInt().inv()
                } else if (operand is Long) {
                    return operand.toLong().inv()
                } else if (operand is Short) {
                    return operand.toShort().inv().toInt()
                } else if (operand is Char) {
                    return operand.toChar().inv()
                } else if (operand is Byte) {
                    return operand.toByte().inv().toInt()
                }
            } else if (operator == UastPrefixOperator.UNARY_MINUS) {
                if (operand is Int) {
                    return -operand
                } else if (operand is Long) {
                    return -operand
                } else if (operand is Double) {
                    return -operand
                } else if (operand is Float) {
                    return -operand
                } else if (operand is Short) {
                    return -operand.toShort()
                } else if (operand is Char) {
                    return -operand.toChar()
                } else if (operand is Byte) {
                    return -operand.toByte()
                }
            }
        } else if (node is UIfExpression &&
            node.getExpressionType() != null
        ) {
            val expression = node
            val known = evaluate(expression.condition)
            if (known == java.lang.Boolean.TRUE && expression.thenExpression != null) {
                return evaluate(expression.thenExpression)
            } else if (known == java.lang.Boolean.FALSE && expression.elseExpression != null) {
                return evaluate(expression.elseExpression)
            }
        } else if (node is UParenthesizedExpression) {
            val expression = node.expression
            return evaluate(expression)
        } else if (node is UPolyadicExpression) {
            val polyadicExpression = node
            val operator = polyadicExpression.operator
            val operands = polyadicExpression.operands
            if (operands.isEmpty()) {
                // For empty strings the Kotlin string template will return an empty operand list
                if (node.sourcePsi is KtStringTemplateExpression) {
                    return ""
                }
            }
            assert(!operands.isEmpty())
            var result = evaluate(operands[0])
            var i = 1
            val n = operands.size
            while (i < n) {
                val rhs = evaluate(operands[i])
                result = evaluateBinary(operator, result, rhs)
                i++
            }
            if (result != null) {
                return result
            }
        } else if (node is UBinaryExpressionWithType &&
            node.operationKind === UastBinaryExpressionWithTypeKind.TypeCast.INSTANCE
        ) {
            val cast = node
            val operandValue = evaluate(cast.operand)
            if (operandValue is Number) {
                val number = operandValue
                val type = cast.type
                if (PsiType.FLOAT == type) {
                    return number.toFloat()
                } else if (PsiType.DOUBLE == type) {
                    return number.toDouble()
                } else if (PsiType.INT == type) {
                    return number.toInt()
                } else if (PsiType.LONG == type) {
                    return number.toLong()
                } else if (PsiType.SHORT == type) {
                    return number.toShort()
                } else if (PsiType.BYTE == type) {
                    return number.toByte()
                }
            }
            return operandValue
        } else if (node is UReferenceExpression) {
            val resolved = node.resolve()
            if (resolved is PsiVariable) {

                // Handle fields specially: we can't look for last assignment
                // on fields since the modifications are often in different methods
                // Only take the field constant or initializer value if it's final,
                // or if allowFieldInitializers is true
                if (resolved is PsiField) {
                    val field = resolved
                    if ("length" == field.name && node is UQualifiedReferenceExpression &&
                        node
                            .receiver
                            .getExpressionType() is PsiArrayType
                    ) {
                        // It's an array.length expression
                        val array = evaluate(node.receiver)
                        val size = getArraySize(array)
                        return if (size != -1) {
                            size
                        } else null
                    }
                    var value = field.computeConstantValue()
                    if (value != null) {
                        return value
                    }
                    if (field.initializer != null &&
                        (
                                allowFieldInitializers ||
                                        (
                                                field.hasModifierProperty(PsiModifier.STATIC) &&
                                                        field.hasModifierProperty(PsiModifier.FINAL)
                                                )
                                )
                    ) {
                        value = evaluate(field.initializer)
                        if (value != null) {
                            return if (surroundedByVariableCheck(node, field)) {
                                null
                            } else value
                        }
                    }
                    if (field is KtLightField) {
                        val fieldValue = evaluate(field)
                        if (fieldValue != null) {
                            return fieldValue
                        }
                    }
                    return null
                }
                val variable = resolved
                val value = findLastValue(variable, node, this)

                // Special return value: the variable *was* assigned something but we don't know
                // the value. In that case we should not continue to look at the initializer
                // since the initial value is no longer relevant.
                if (value == LastAssignmentFinder.LastAssignmentValueUnknown) {
                    return null
                }
                if (value != null) {
                    return if (surroundedByVariableCheck(node, variable)) {
                        null
                    } else value
                }
                if (variable.initializer != null) {
                    val initializedValue = evaluate(variable.initializer)
                    return if (surroundedByVariableCheck(node, variable)) {
                        null
                    } else initializedValue
                }
                return null
            }
            if (node is UQualifiedReferenceExpression) {
                val expression = node
                val selector = expression.selector
                var receiver = expression.receiver
                if (receiver is USimpleNameReferenceExpression) {
                    // such as kotlin.IntArray(x)
                    if (receiver.identifier == "kotlin") {
                        return evaluate(selector)
                    }
                }
                if (selector is USimpleNameReferenceExpression) {
                    val identifier = selector.identifier
                    // "kotlin.<N>Array".size ?
                    if (receiver is UQualifiedReferenceExpression) {
                        val expression1 = receiver
                        if (expression1.receiver is USimpleNameReferenceExpression &&
                            (
                                    (expression1.receiver as USimpleNameReferenceExpression)
                                        .identifier
                                            == "kotlin"
                                    )
                        ) {
                            receiver = expression1.selector
                        }
                    }

                    // TODO: Handle listOf, arrayListOf etc as well!
                    if ("size" == identifier && receiver is UCallExpression) {
                        val receiverCall = receiver
                        val name = getMethodName(receiverCall)
                        if (name != null) {
                            if (name.endsWith("Array") &&
                                (name == "Array" || getKotlinPrimitiveArrayType(name) != null)
                            ) {
                                val size = getKotlinArrayConstructionSize(receiverCall)
                                if (size != -1) {
                                    return size
                                }
                            } else if (name.endsWith("rrayOf") &&
                                (name == "arrayOf" || getKotlinPrimitiveArrayType(name) != null)
                            ) {
                                return receiverCall.valueArgumentCount
                            } else if ("arrayOfNulls" == name) {
                                val size = getKotlinArrayConstructionSize(receiverCall)
                                if (size != -1) {
                                    return size
                                }
                            }
                        }
                    }
                }
                if (receiver != null && selector is UCallExpression) {
                    val call = selector
                    val methodName = call.methodName
                    if ("trimIndent" == methodName) {
                        val s = evaluate(receiver)
                        if (s is String) {
                            return s.trimIndent()
                        }
                    } else if ("trimMargin" == methodName) {
                        val s = evaluate(receiver)
                        if (s is String) {
                            var prefix = "|"
                            val valueArguments = call.valueArguments
                            if (valueArguments.size == 1) {
                                val arg = evaluate(valueArguments[0])
                                if (arg != null) {
                                    prefix = arg.toString()
                                }
                            }
                            return s.trimMargin(prefix)
                        }
                    } else if (allowUnknown && "format" == methodName) {
                        // In String#format attempt to pick out at least the formatting string.
                        // In theory we could also evaluate all the arguments and try passing them
                        // in but there's some risk of invalid formatting string combinations.
                        val arguments = call.valueArguments
                        if (arguments.size >= 2) {
                            val first = arguments[0]
                            val second = arguments[1]
                            val expressionType = first.getExpressionType()
                            return if (expressionType != null &&
                                (
                                        "java.util.Locale"
                                                == expressionType.canonicalText
                                        )
                            ) {
                                evaluate(second)
                            } else {
                                evaluate(first)
                            }
                        }
                    }
                }
            }
        } else if (node.isNewArrayWithDimensions()) {
            val call = node as UCallExpression
            val arrayType = call.getExpressionType()
            if (arrayType is PsiArrayType) {
                val type = arrayType.getDeepComponentType()
                // Single-dimension array
                if (type !is PsiArrayType && call.valueArgumentCount == 1) {
                    val lengthObj = evaluate(call.valueArguments[0])
                    if (lengthObj is Number) {
                        val size = lengthObj.toInt()
                        val dimensions = arrayType.getArrayDimensions()
                        return getArray(type, size, dimensions)
                    }
                }
            }
        } else if (node.isNewArrayWithInitializer()) {
            val array = createInitializedArray(node as UCallExpression)
            if (array != null) {
                return array
            }
        } else if (node is UCallExpression) {
            val call = node
            val name = getMethodName(call)
            if (name != null) {
                if (name.endsWith("Array") && call.isConstructorCall()) {
                    val size = getKotlinArrayConstructionSize(call)
                    if (size != -1) {
                        if (name == "Array") {
                            val type = call.getExpressionType()
                            if (type is PsiArrayType) {
                                val dimensions = type.getArrayDimensions()
                                val componentType = type.getDeepComponentType()
                                return getArray(componentType, size, dimensions)
                            }
                        } else {
                            val type = getKotlinPrimitiveArrayType(name)
                            if (type != null) {
                                val dimensions = 1
                                return getArray(type, size, dimensions)
                            }
                        }
                    }
                } else if ("arrayOf" == name || name.endsWith("ArrayOf") && getKotlinPrimitiveArrayType(name) != null) {
                    val array = createInitializedArray(call)
                    if (array != null) {
                        return array
                    }
                } else if ("arrayOfNulls" == name) {
                    val type = call.getExpressionType()
                    if (type is PsiArrayType) {
                        val size = getKotlinArrayConstructionSize(call)
                        if (size != -1) {
                            val dimensions = type.getArrayDimensions()
                            val componentType = type.getDeepComponentType()
                            return getArray(componentType, size, dimensions)
                        }
                    }
                }
            }
        } else if (node is UArrayAccessExpression) {
            val expression = node
            val indices = expression.indices
            if (indices.size == 1) {
                val indexValue = evaluate(indices[0])
                if (indexValue is Number) {
                    val array = evaluate(expression.receiver)
                    if (array != null) {
                        val index = indexValue.toInt()
                        if (array is Array<*>) {
                            val objArray = array
                            if (index >= 0 && index < objArray.size) {
                                return objArray[index]
                            }
                        } else if (array is IntArray) {
                            val intArray = array
                            if (index >= 0 && index < intArray.size) {
                                return intArray[index]
                            }
                        } else if (array is BooleanArray) {
                            val booleanArray = array
                            if (index >= 0 && index < booleanArray.size) {
                                return booleanArray[index]
                            }
                        } else if (array is CharArray) {
                            val charArray = array
                            if (index >= 0 && index < charArray.size) {
                                return charArray[index]
                            }
                        } else if (array is LongArray) {
                            val longArray = array
                            if (index >= 0 && index < longArray.size) {
                                return longArray[index]
                            }
                        } else if (array is FloatArray) {
                            val floatArray = array
                            if (index >= 0 && index < floatArray.size) {
                                return floatArray[index]
                            }
                        } else if (array is DoubleArray) {
                            val doubleArray = array
                            if (index >= 0 && index < doubleArray.size) {
                                return doubleArray[index]
                            }
                        } else if (array is ByteArray) {
                            val byteArray = array
                            if (index >= 0 && index < byteArray.size) {
                                return byteArray[index]
                            }
                        } else if (array is ShortArray) {
                            val shortArray = array
                            if (index >= 0 && index < shortArray.size) {
                                return shortArray[index]
                            }
                        }
                    }
                }
            }
        }
        if (node is UExpression) {
            val evaluated = node.evaluate()
            if (evaluated != null) {
                return evaluated
            }
        }

        // TODO: Check for MethodInvocation and perform some common operations -
        // Math.* methods, String utility methods like notNullize, etc
        return null
    }

    private fun createInitializedArray(call: UCallExpression): Any? {
        val arrayType = call.getExpressionType()
        if (arrayType is PsiArrayType) {
            val componentType = arrayType.getDeepComponentType()
            if (componentType !is PsiArrayType) {
                val length = call.valueArgumentCount
                val evaluatedArgs: MutableList<Any?> = ArrayList(length)
                var count = 0
                for (arg in call.valueArguments) {
                    val evaluatedArg = evaluate(arg)
                    if (!allowUnknown && evaluatedArg == null) {
                        // Inconclusive
                        return null
                    }
                    evaluatedArgs.add(evaluatedArg)
                    count++
                    if (count == 40) { // avoid large initializers
                        return getArray(componentType, length, 1)
                    }
                }
                if (componentType === PsiType.BOOLEAN) {
                    val arr = BooleanArray(length)
                    for (i in 0 until length) {
                        val o = evaluatedArgs[i]
                        if (o is Boolean) {
                            arr[i] = o
                        }
                    }
                    return arr
                } else if (isObjectType(componentType)) {
                    val arr = arrayOfNulls<Any>(length)
                    for (i in 0 until length) {
                        arr[i] = evaluatedArgs[i]
                    }
                    return arr
                } else if (componentType == PsiType.CHAR) {
                    val arr = CharArray(length)
                    for (i in 0 until length) {
                        val o = evaluatedArgs[i]
                        if (o is Char) {
                            arr[i] = o
                        }
                    }
                    return arr
                } else if (componentType == PsiType.BYTE) {
                    val arr = ByteArray(length)
                    for (i in 0 until length) {
                        val o = evaluatedArgs[i]
                        if (o is Byte) {
                            arr[i] = o
                        }
                    }
                    return arr
                } else if (componentType == PsiType.DOUBLE) {
                    val arr = DoubleArray(length)
                    for (i in 0 until length) {
                        val o = evaluatedArgs[i]
                        if (o is Double) {
                            arr[i] = o
                        }
                    }
                    return arr
                } else if (componentType == PsiType.FLOAT) {
                    val arr = FloatArray(length)
                    for (i in 0 until length) {
                        val o = evaluatedArgs[i]
                        if (o is Float) {
                            arr[i] = o
                        }
                    }
                    return arr
                } else if (componentType == PsiType.INT) {
                    val arr = IntArray(length)
                    for (i in 0 until length) {
                        val o = evaluatedArgs[i]
                        if (o is Int) {
                            arr[i] = o
                        }
                    }
                    return arr
                } else if (componentType == PsiType.SHORT) {
                    val arr = ShortArray(length)
                    for (i in 0 until length) {
                        val o = evaluatedArgs[i]
                        if (o is Short) {
                            arr[i] = o
                        }
                    }
                    return arr
                } else if (componentType == PsiType.LONG) {
                    val arr = LongArray(length)
                    for (i in 0 until length) {
                        val o = evaluatedArgs[i]
                        if (o is Long) {
                            arr[i] = o
                        }
                    }
                    return arr
                } else if (isStringType(componentType)) {
                    val arr = arrayOfNulls<String>(length)
                    for (i in 0 until length) {
                        val o = evaluatedArgs[i]
                        if (o is String) {
                            arr[i] = o as String?
                        }
                    }
                    return arr
                }

                // Try to instantiate base type
                if (!evaluatedArgs.isEmpty()) {
                    val first = evaluatedArgs[0]
                    for (o in evaluatedArgs) {
                        if (o!!.javaClass != first!!.javaClass) {
                            return null
                        }
                    }
                    return evaluatedArgs.toArray<Any> { n -> java.lang.reflect.Array.newInstance(first!!.javaClass, n) as Array<*> }
                }
            }
        }
        return null
    }

    private fun getKotlinArrayConstructionSize(call: UCallExpression): Int {
        val valueArguments = call.valueArguments
        if (!valueArguments.isEmpty()) {
            val lengthObj = evaluate(call.valueArguments[0])
            if (lengthObj is Number) {
                return lengthObj.toInt()
            }
        }
        return -1
    }

    private fun evaluateBinary(
        operator: UastBinaryOperator,
        operandLeft: Any?,
        operandRight: Any?
    ): Any? {
        if (operandLeft == null || operandRight == null) {
            return if (allowUnknown) {
                operandLeft ?: operandRight
            } else null
        }
        if (operandLeft is String &&
            (operandRight is String || operandRight is Char) ||
            operandRight is String && operandLeft is Char
        ) {
            return if (operator === UastBinaryOperator.PLUS) {
                operandLeft.toString() + operandRight.toString()
            } else null
        } else if (operandLeft is Boolean && operandRight is Boolean) {
            val left = operandLeft
            val right = operandRight
            if (operator === UastBinaryOperator.LOGICAL_OR) {
                return left || right
            } else if (operator === UastBinaryOperator.LOGICAL_AND) {
                return left && right
            } else if (operator === UastBinaryOperator.BITWISE_OR) {
                return left or right
            } else if (operator === UastBinaryOperator.BITWISE_XOR) {
                return left xor right
            } else if (operator === UastBinaryOperator.BITWISE_AND) {
                return left and right
            } else if (operator === UastBinaryOperator.IDENTITY_EQUALS ||
                operator === UastBinaryOperator.EQUALS
            ) {
                return left == right
            } else if (operator === UastBinaryOperator.IDENTITY_NOT_EQUALS ||
                operator === UastBinaryOperator.NOT_EQUALS
            ) {
                return left != right
            }
        } else if (operandLeft is Number && operandRight is Number) {
            val left = operandLeft
            val right = operandRight
            val isInteger = !(
                    left is Float ||
                            left is Double ||
                            right is Float ||
                            right is Double
                    )
            val isWide = if (isInteger) left is Long || right is Long else left is Double || right is Double
            return if (operator === UastBinaryOperator.BITWISE_OR) {
                if (isWide) {
                    left.toLong() or right.toLong()
                } else {
                    left.toInt() or right.toInt()
                }
            } else if (operator === UastBinaryOperator.BITWISE_XOR) {
                if (isWide) {
                    left.toLong() xor right.toLong()
                } else {
                    left.toInt() xor right.toInt()
                }
            } else if (operator === UastBinaryOperator.BITWISE_AND) {
                if (isWide) {
                    left.toLong() and right.toLong()
                } else {
                    left.toInt() and right.toInt()
                }
            } else if (operator === UastBinaryOperator.EQUALS ||
                operator === UastBinaryOperator.IDENTITY_EQUALS
            ) {
                if (isInteger) {
                    left.toLong() == right.toLong()
                } else {
                    left.toDouble() == right.toDouble()
                }
            } else if (operator === UastBinaryOperator.NOT_EQUALS ||
                operator === UastBinaryOperator.IDENTITY_NOT_EQUALS
            ) {
                if (isInteger) {
                    left.toLong() != right.toLong()
                } else {
                    left.toDouble() != right.toDouble()
                }
            } else if (operator === UastBinaryOperator.GREATER) {
                if (isInteger) {
                    left.toLong() > right.toLong()
                } else {
                    left.toDouble() > right.toDouble()
                }
            } else if (operator === UastBinaryOperator.GREATER_OR_EQUALS) {
                if (isInteger) {
                    left.toLong() >= right.toLong()
                } else {
                    left.toDouble() >= right.toDouble()
                }
            } else if (operator === UastBinaryOperator.LESS) {
                if (isInteger) {
                    left.toLong() < right.toLong()
                } else {
                    left.toDouble() < right.toDouble()
                }
            } else if (operator === UastBinaryOperator.LESS_OR_EQUALS) {
                if (isInteger) {
                    left.toLong() <= right.toLong()
                } else {
                    left.toDouble() <= right.toDouble()
                }
            } else if (operator === UastBinaryOperator.SHIFT_LEFT) {
                if (isWide) {
                    left.toLong() shl right.toInt()
                } else {
                    left.toInt() shl right.toInt()
                }
            } else if (operator === UastBinaryOperator.SHIFT_RIGHT) {
                if (isWide) {
                    left.toLong() shr right.toInt()
                } else {
                    left.toInt() shr right.toInt()
                }
            } else if (operator === UastBinaryOperator.UNSIGNED_SHIFT_RIGHT) {
                if (isWide) {
                    left.toLong() ushr right.toInt()
                } else {
                    left.toInt() ushr right.toInt()
                }
            } else if (operator === UastBinaryOperator.PLUS) {
                if (isInteger) {
                    if (isWide) {
                        left.toLong() + right.toLong()
                    } else {
                        left.toInt() + right.toInt()
                    }
                } else {
                    if (isWide) {
                        left.toDouble() + right.toDouble()
                    } else {
                        left.toFloat() + right.toFloat()
                    }
                }
            } else if (operator === UastBinaryOperator.MINUS) {
                if (isInteger) {
                    if (isWide) {
                        left.toLong() - right.toLong()
                    } else {
                        left.toInt() - right.toInt()
                    }
                } else {
                    if (isWide) {
                        left.toDouble() - right.toDouble()
                    } else {
                        left.toFloat() - right.toFloat()
                    }
                }
            } else if (operator === UastBinaryOperator.MULTIPLY) {
                if (isInteger) {
                    if (isWide) {
                        left.toLong() * right.toLong()
                    } else {
                        left.toInt() * right.toInt()
                    }
                } else {
                    if (isWide) {
                        left.toDouble() * right.toDouble()
                    } else {
                        left.toFloat() * right.toFloat()
                    }
                }
            } else if (operator === UastBinaryOperator.DIV) {
                if (isInteger) {
                    if (right.toLong() == 0L) {
                        null
                    } else if (isWide) {
                        left.toLong() / right.toLong()
                    } else {
                        left.toInt() / right.toInt()
                    }
                } else {
                    if (isWide) {
                        left.toDouble() / right.toDouble()
                    } else {
                        left.toFloat() / right.toFloat()
                    }
                }
            } else if (operator === UastBinaryOperator.MOD) {
                if (isInteger) {
                    if (right.toLong() == 0L) {
                        null
                    } else if (isWide) {
                        left.toLong() % right.toLong()
                    } else {
                        left.toInt() % right.toInt()
                    }
                } else {
                    if (isWide) {
                        left.toDouble() % right.toDouble()
                    } else {
                        left.toFloat() % right.toFloat()
                    }
                }
            } else {
                null
            }
        }
        return null
    }

    class LastAssignmentFinder(
        private val mVariable: PsiVariable,
        private val mEndAt: UElement,
        constantEvaluator: ConstantEvaluator?,
        variableLevel: Int
    ) : AbstractUastVisitor() {
        private val mConstantEvaluator: ConstantEvaluator?
        private var mDone = false
        private var mCurrentLevel = 0
        private var mVariableLevel = -1
        var currentValue: Any? = null
            private set
        var lastAssignment: UElement?
            private set

        init {
            val initializer = UastFacade.getInitializerBody(mVariable)
            lastAssignment = initializer
            mConstantEvaluator = constantEvaluator
            if (initializer != null && constantEvaluator != null) {
                currentValue = constantEvaluator.evaluate(initializer)
            }
            mVariableLevel = variableLevel
        }

        override fun visitElement(node: UElement): Boolean {
            if (elementHasLevel(node)) {
                mCurrentLevel++
            }
            if (node == mEndAt) {
                mDone = true
            }
            return mDone || super.visitElement(node)
        }

        override fun visitVariable(node: UVariable): Boolean {
            if (mVariableLevel < 0 && node.psi.isEquivalentTo(mVariable)) {
                mVariableLevel = mCurrentLevel
            }
            return super.visitVariable(node)
        }

        override fun afterVisitBinaryExpression(node: UBinaryExpression) {
            if ((
                        !mDone &&
                                node.operator is UastBinaryOperator.AssignOperator
                        ) && mVariableLevel >= 0
            ) {
                val leftOperand = node.leftOperand
                val operator = node.operator
                if (operator !is UastBinaryOperator.AssignOperator ||
                    leftOperand !is UResolvable
                ) {
                    return
                }
                val resolved = (leftOperand as UResolvable).resolve()
                if (mVariable != resolved) {
                    return
                }
                val rightOperand = node.rightOperand
                val constantEvaluator = mConstantEvaluator
                lastAssignment = rightOperand

                // Last assigned value cannot be determined if we see an assignment inside
                // some conditional or loop statement.
                if (mCurrentLevel >= mVariableLevel + 1) {
                    currentValue = null
                    return
                } else {
                    currentValue = constantEvaluator?.evaluate(rightOperand)
                }
            }
            super.afterVisitBinaryExpression(node)
        }

        override fun afterVisitElement(node: UElement) {
            if (elementHasLevel(node)) {
                mCurrentLevel--
            }
            super.afterVisitElement(node)
        }

        /**
         * Special marker value from [findLastValue] to indicate that a node was assigned to, but
         * the value is unknown
         */
        internal object LastAssignmentValueUnknown: Any()

        companion object {
            private fun elementHasLevel(node: UElement): Boolean {
                return !(
                        node is UBlockExpression ||
                                node is UDeclarationsExpression ||
                                node is UParenthesizedExpression
                        )
            }
        }
    }

    /**
     * Evaluates the given node and returns the constant value it resolves to, if any
     *
     * @param node the node to compute the constant value for
     * @return the corresponding constant value - a String, an Integer, a Float, and so on
     */
    fun evaluate(node: PsiElement?): Any? {
        if (node == null) {
            return null
        }
        if (node is PsiLiteral) {
            var value = node.value
            if (value == null && node is KtLightPsiLiteral) {
                val origin = node.kotlinOrigin
                val uastExpression = convertElement(origin, null, UExpression::class.java) as UExpression?
                if (uastExpression != null) {
                    value = uastExpression.evaluate()
                }
            }
            return value
        } else if (node is PsiPrefixExpression) {
            val operator = node.operationTokenType
            val operand = evaluate(node.operand) ?: return null
            if (operator === JavaTokenType.EXCL) {
                if (operand is Boolean) {
                    return !operand
                }
            } else if (operator === JavaTokenType.PLUS) {
                return operand
            } else if (operator === JavaTokenType.TILDE) {
                if (operand is Int) {
                    return operand.toInt().inv()
                } else if (operand is Long) {
                    return operand.toLong().inv()
                } else if (operand is Short) {
                    return operand.toShort().inv().toInt()
                } else if (operand is Char) {
                    return operand.toChar().inv()
                } else if (operand is Byte) {
                    return operand.toByte().inv().toInt()
                }
            } else if (operator === JavaTokenType.MINUS) {
                if (operand is Int) {
                    return -operand
                } else if (operand is Long) {
                    return -operand
                } else if (operand is Double) {
                    return -operand
                } else if (operand is Float) {
                    return -operand
                } else if (operand is Short) {
                    return -operand.toShort()
                } else if (operand is Char) {
                    return -operand as Char?
                } else if (operand is Byte) {
                    return -operand.toByte()
                }
            }
        } else if (node is PsiConditionalExpression) {
            val expression = node
            val known = evaluate(expression.condition)
            if (known == java.lang.Boolean.TRUE && expression.thenExpression != null) {
                return evaluate(expression.thenExpression)
            } else if (known == java.lang.Boolean.FALSE && expression.elseExpression != null) {
                return evaluate(expression.elseExpression)
            }
        } else if (node is PsiParenthesizedExpression) {
            val expression = node.expression
            if (expression != null) {
                return evaluate(expression)
            }
        } else if (node is PsiBinaryExpression) {
            val expression = node
            val operator = expression.operationTokenType
            val operandLeft = evaluate(expression.lOperand)
            val operandRight = evaluate(expression.rOperand)
            if (operandLeft == null || operandRight == null) {
                return if (allowUnknown) {
                    operandLeft ?: operandRight
                } else null
            }
            if (operandLeft is String &&
                (operandRight is String || operandRight is Char) ||
                operandRight is String && operandLeft is Char
            ) {
                return if (operator === JavaTokenType.PLUS) {
                    operandLeft.toString() + operandRight.toString()
                } else null
            } else if (operandLeft is Boolean && operandRight is Boolean) {
                val left = operandLeft
                val right = operandRight
                if (operator === JavaTokenType.OROR) {
                    return left || right
                } else if (operator === JavaTokenType.ANDAND) {
                    return left && right
                } else if (operator === JavaTokenType.OR) {
                    return left or right
                } else if (operator === JavaTokenType.XOR) {
                    return left xor right
                } else if (operator === JavaTokenType.AND) {
                    return left and right
                } else if (operator === JavaTokenType.EQEQ) {
                    return left == right
                } else if (operator === JavaTokenType.NE) {
                    return left != right
                }
            } else if (operandLeft is Number && operandRight is Number) {
                val left = operandLeft
                val right = operandRight
                val isInteger = !(
                        left is Float ||
                                left is Double ||
                                right is Float ||
                                right is Double
                        )
                val isWide = if (isInteger) left is Long || right is Long else left is Double || right is Double
                return if (operator === JavaTokenType.OR) {
                    if (isWide) {
                        left.toLong() or right.toLong()
                    } else {
                        left.toInt() or right.toInt()
                    }
                } else if (operator === JavaTokenType.XOR) {
                    if (isWide) {
                        left.toLong() xor right.toLong()
                    } else {
                        left.toInt() xor right.toInt()
                    }
                } else if (operator === JavaTokenType.AND) {
                    if (isWide) {
                        left.toLong() and right.toLong()
                    } else {
                        left.toInt() and right.toInt()
                    }
                } else if (operator === JavaTokenType.EQEQ) {
                    if (isInteger) {
                        left.toLong() == right.toLong()
                    } else {
                        left.toDouble() == right.toDouble()
                    }
                } else if (operator === JavaTokenType.NE) {
                    if (isInteger) {
                        left.toLong() != right.toLong()
                    } else {
                        left.toDouble() != right.toDouble()
                    }
                } else if (operator === JavaTokenType.GT) {
                    if (isInteger) {
                        left.toLong() > right.toLong()
                    } else {
                        left.toDouble() > right.toDouble()
                    }
                } else if (operator === JavaTokenType.GE) {
                    if (isInteger) {
                        left.toLong() >= right.toLong()
                    } else {
                        left.toDouble() >= right.toDouble()
                    }
                } else if (operator === JavaTokenType.LT) {
                    if (isInteger) {
                        left.toLong() < right.toLong()
                    } else {
                        left.toDouble() < right.toDouble()
                    }
                } else if (operator === JavaTokenType.LE) {
                    if (isInteger) {
                        left.toLong() <= right.toLong()
                    } else {
                        left.toDouble() <= right.toDouble()
                    }
                } else if (operator === JavaTokenType.LTLT) {
                    if (isWide) {
                        left.toLong() shl right.toInt()
                    } else {
                        left.toInt() shl right.toInt()
                    }
                } else if (operator === JavaTokenType.GTGT) {
                    if (isWide) {
                        left.toLong() shr right.toInt()
                    } else {
                        left.toInt() shr right.toInt()
                    }
                } else if (operator === JavaTokenType.GTGTGT) {
                    if (isWide) {
                        left.toLong() ushr right.toInt()
                    } else {
                        left.toInt() ushr right.toInt()
                    }
                } else if (operator === JavaTokenType.PLUS) {
                    if (isInteger) {
                        if (isWide) {
                            left.toLong() + right.toLong()
                        } else {
                            left.toInt() + right.toInt()
                        }
                    } else {
                        if (isWide) {
                            left.toDouble() + right.toDouble()
                        } else {
                            left.toFloat() + right.toFloat()
                        }
                    }
                } else if (operator === JavaTokenType.MINUS) {
                    if (isInteger) {
                        if (isWide) {
                            left.toLong() - right.toLong()
                        } else {
                            left.toInt() - right.toInt()
                        }
                    } else {
                        if (isWide) {
                            left.toDouble() - right.toDouble()
                        } else {
                            left.toFloat() - right.toFloat()
                        }
                    }
                } else if (operator === JavaTokenType.ASTERISK) {
                    if (isInteger) {
                        if (isWide) {
                            left.toLong() * right.toLong()
                        } else {
                            left.toInt() * right.toInt()
                        }
                    } else {
                        if (isWide) {
                            left.toDouble() * right.toDouble()
                        } else {
                            left.toFloat() * right.toFloat()
                        }
                    }
                } else if (operator === JavaTokenType.DIV) {
                    if (isInteger) {
                        if (right.toLong() == 0L) {
                            null
                        } else if (isWide) {
                            left.toLong() / right.toLong()
                        } else {
                            left.toInt() / right.toInt()
                        }
                    } else {
                        if (isWide) {
                            left.toDouble() / right.toDouble()
                        } else {
                            left.toFloat() / right.toFloat()
                        }
                    }
                } else if (operator === JavaTokenType.PERC) {
                    if (isInteger) {
                        if (right.toLong() == 0L) {
                            null
                        } else if (isWide) {
                            left.toLong() % right.toLong()
                        } else {
                            left.toInt() % right.toInt()
                        }
                    } else {
                        if (isWide) {
                            left.toDouble() % right.toDouble()
                        } else {
                            left.toFloat() % right.toFloat()
                        }
                    }
                } else {
                    null
                }
            }
        } else if (node is PsiPolyadicExpression) {
            val expression = node
            val operator = expression.operationTokenType
            val operands = expression.operands
            val values: MutableList<Any> = ArrayList(operands.size)
            var hasString = false
            var hasBoolean = false
            var hasNumber = false
            var isFloat = false
            var isWide = false
            for (operand in operands) {
                val value = evaluate(operand)
                if (value != null) {
                    values.add(value)
                    if (value is String) {
                        hasString = true
                    } else if (value is Boolean) {
                        hasBoolean = true
                    } else if (value is Number) {
                        if (value is Float) {
                            isFloat = true
                        } else if (value is Double) {
                            isFloat = true
                            isWide = true
                        } else if (value is Long) {
                            isWide = true
                        }
                        hasNumber = true
                    }
                }
            }
            if (values.isEmpty()) {
                return null
            }
            if (hasString) {
                if (operator === JavaTokenType.PLUS) {
                    // String concatenation
                    val sb = StringBuilder()
                    for (value in values) {
                        sb.append(value.toString())
                    }
                    return sb.toString()
                }
                return null
            }
            if (!allowUnknown && operands.size != values.size) {
                return null
            }
            if (hasBoolean) {
                if (operator === JavaTokenType.OROR) {
                    var result = false
                    for (value in values) {
                        if (value is Boolean) {
                            result = result || value
                        }
                    }
                    return result
                } else if (operator === JavaTokenType.ANDAND) {
                    var result = true
                    for (value in values) {
                        if (value is Boolean) {
                            result = result && value
                        }
                    }
                    return result
                } else if (operator === JavaTokenType.OR) {
                    var result = false
                    for (value in values) {
                        if (value is Boolean) {
                            result = result or value
                        }
                    }
                    return result
                } else if (operator === JavaTokenType.XOR) {
                    var result = false
                    for (value in values) {
                        if (value is Boolean) {
                            result = result xor value
                        }
                    }
                    return result
                } else if (operator === JavaTokenType.AND) {
                    var result = true
                    for (value in values) {
                        if (value is Boolean) {
                            result = result and value
                        }
                    }
                    return result
                } else if (operator === JavaTokenType.EQEQ) {
                    var result = false
                    for (i in values.indices) {
                        val value = values[i]
                        if (value is Boolean) {
                            val b = value
                            result = if (i == 0) {
                                b
                            } else {
                                result == b
                            }
                        }
                    }
                    return result
                } else if (operator === JavaTokenType.NE) {
                    var result = false
                    for (i in values.indices) {
                        val value = values[i]
                        if (value is Boolean) {
                            val b = value
                            result = if (i == 0) {
                                b
                            } else {
                                result != b
                            }
                        }
                    }
                    return result
                }
                return null
            }
            if (hasNumber) {
                // TODO: This is super redundant. Switch to lambdas!
                return if (operator === JavaTokenType.OR) {
                    if (isWide) {
                        var result = 0L
                        for (i in values.indices) {
                            val value = values[i]
                            if (value is Number) {
                                val l = value.toLong()
                                result = if (i == 0) {
                                    l
                                } else {
                                    result or l
                                }
                            }
                        }
                        result
                    } else {
                        var result = 0
                        for (i in values.indices) {
                            val value = values[i]
                            if (value is Number) {
                                val l = value.toInt()
                                result = if (i == 0) {
                                    l
                                } else {
                                    result or l
                                }
                            }
                        }
                        result
                    }
                } else if (operator === JavaTokenType.XOR) {
                    if (isWide) {
                        var result = 0L
                        for (i in values.indices) {
                            val value = values[i]
                            if (value is Number) {
                                val l = value.toLong()
                                result = if (i == 0) {
                                    l
                                } else {
                                    result xor l
                                }
                            }
                        }
                        result
                    } else {
                        var result = 0
                        for (i in values.indices) {
                            val value = values[i]
                            if (value is Number) {
                                val l = value.toInt()
                                result = if (i == 0) {
                                    l
                                } else {
                                    result xor l
                                }
                            }
                        }
                        result
                    }
                } else if (operator === JavaTokenType.AND) {
                    if (isWide) {
                        var result = 0L
                        for (i in values.indices) {
                            val value = values[i]
                            if (value is Number) {
                                val l = value.toLong()
                                result = if (i == 0) {
                                    l
                                } else {
                                    result and l
                                }
                            }
                        }
                        result
                    } else {
                        var result = 0
                        for (i in values.indices) {
                            val value = values[i]
                            if (value is Number) {
                                val l = value.toInt()
                                result = if (i == 0) {
                                    l
                                } else {
                                    result and l
                                }
                            }
                        }
                        result
                    }
                } else if (operator === JavaTokenType.EQEQ) {
                    if (isWide) {
                        var result = false
                        var prev: Long = 0
                        for (i in values.indices) {
                            val value = values[i]
                            if (value is Number) {
                                val l = value.toLong()
                                if (i != 0) {
                                    result = prev == l
                                }
                                prev = l
                            }
                        }
                        result
                    } else {
                        var result = false
                        var prev = 0
                        for (i in values.indices) {
                            val value = values[i]
                            if (value is Number) {
                                val l = value.toInt()
                                if (i != 0) {
                                    result = prev == l
                                }
                                prev = l
                            }
                        }
                        result
                    }
                } else if (operator === JavaTokenType.NE) {
                    if (isWide) {
                        var result = false
                        var prev: Long = 0
                        for (i in values.indices) {
                            val value = values[i]
                            if (value is Number) {
                                val l = value.toLong()
                                if (i != 0) {
                                    result = prev != l
                                }
                                prev = l
                            }
                        }
                        result
                    } else {
                        var result = false
                        var prev = 0
                        for (i in values.indices) {
                            val value = values[i]
                            if (value is Number) {
                                val l = value.toInt()
                                if (i != 0) {
                                    result = prev != l
                                }
                                prev = l
                            }
                        }
                        result
                    }
                } else if (operator === JavaTokenType.GT) {
                    if (isWide) {
                        var result = false
                        var prev: Long = 0
                        for (i in values.indices) {
                            val value = values[i]
                            if (value is Number) {
                                val l = value.toLong()
                                if (i != 0) {
                                    result = prev > l
                                }
                                prev = l
                            }
                        }
                        result
                    } else {
                        var result = false
                        var prev = 0
                        for (i in values.indices) {
                            val value = values[i]
                            if (value is Number) {
                                val l = value.toInt()
                                if (i != 0) {
                                    result = prev > l
                                }
                                prev = l
                            }
                        }
                        result
                    }
                } else if (operator === JavaTokenType.GE) {
                    if (isWide) {
                        var result = false
                        var prev: Long = 0
                        for (i in values.indices) {
                            val value = values[i]
                            if (value is Number) {
                                val l = value.toLong()
                                if (i != 0) {
                                    result = prev >= l
                                }
                                prev = l
                            }
                        }
                        result
                    } else {
                        var result = false
                        var prev = 0
                        for (i in values.indices) {
                            val value = values[i]
                            if (value is Number) {
                                val l = value.toInt()
                                if (i != 0) {
                                    result = prev >= l
                                }
                                prev = l
                            }
                        }
                        result
                    }
                } else if (operator === JavaTokenType.LT) {
                    if (isWide) {
                        var result = false
                        var prev: Long = 0
                        for (i in values.indices) {
                            val value = values[i]
                            if (value is Number) {
                                val l = value.toLong()
                                if (i != 0) {
                                    result = prev < l
                                }
                                prev = l
                            }
                        }
                        result
                    } else {
                        var result = false
                        var prev = 0
                        for (i in values.indices) {
                            val value = values[i]
                            if (value is Number) {
                                val l = value.toInt()
                                if (i != 0) {
                                    result = prev < l
                                }
                                prev = l
                            }
                        }
                        result
                    }
                } else if (operator === JavaTokenType.LE) {
                    if (isWide) {
                        var result = false
                        var prev: Long = 0
                        for (i in values.indices) {
                            val value = values[i]
                            if (value is Number) {
                                val l = value.toLong()
                                if (i != 0) {
                                    result = prev <= l
                                }
                                prev = l
                            }
                        }
                        result
                    } else {
                        var result = false
                        var prev = 0
                        for (i in values.indices) {
                            val value = values[i]
                            if (value is Number) {
                                val l = value.toInt()
                                if (i != 0) {
                                    result = prev <= l
                                }
                                prev = l
                            }
                        }
                        result
                    }
                } else if (operator === JavaTokenType.LTLT) {
                    if (isWide) {
                        var result = 0L
                        for (i in values.indices) {
                            val value = values[i]
                            if (value is Number) {
                                val l = value.toLong()
                                result = if (i == 0) {
                                    l
                                } else {
                                    result shl l.toInt()
                                }
                            }
                        }
                        result
                    } else {
                        var result = 0
                        for (i in values.indices) {
                            val value = values[i]
                            if (value is Number) {
                                val l = value.toInt()
                                result = if (i == 0) {
                                    l
                                } else {
                                    result shl l
                                }
                            }
                        }
                        result
                    }
                } else if (operator === JavaTokenType.GTGT) {
                    if (isWide) {
                        var result = 0L
                        for (i in values.indices) {
                            val value = values[i]
                            if (value is Number) {
                                val l = value.toLong()
                                result = if (i == 0) {
                                    l
                                } else {
                                    result shr l.toInt()
                                }
                            }
                        }
                        result
                    } else {
                        var result = 0
                        for (i in values.indices) {
                            val value = values[i]
                            if (value is Number) {
                                val l = value.toInt()
                                result = if (i == 0) {
                                    l
                                } else {
                                    result shr l
                                }
                            }
                        }
                        result
                    }
                } else if (operator === JavaTokenType.GTGTGT) {
                    if (isWide) {
                        var result = 0L
                        for (i in values.indices) {
                            val value = values[i]
                            if (value is Number) {
                                val l = value.toLong()
                                result = if (i == 0) {
                                    l
                                } else {
                                    result ushr l.toInt()
                                }
                            }
                        }
                        result
                    } else {
                        var result = 0
                        for (i in values.indices) {
                            val value = values[i]
                            if (value is Number) {
                                val l = value.toInt()
                                result = if (i == 0) {
                                    l
                                } else {
                                    result ushr l
                                }
                            }
                        }
                        result
                    }
                } else if (operator === JavaTokenType.PLUS) {
                    if (isFloat) {
                        if (isWide) {
                            var result = 0.0
                            for (i in values.indices) {
                                val value = values[i]
                                if (value is Number) {
                                    val l = value.toDouble()
                                    result = if (i == 0) {
                                        l
                                    } else {
                                        result + l
                                    }
                                }
                            }
                            result
                        } else {
                            var result = 0f
                            for (i in values.indices) {
                                val value = values[i]
                                if (value is Number) {
                                    val l = value.toFloat()
                                    result = if (i == 0) {
                                        l
                                    } else {
                                        result + l
                                    }
                                }
                            }
                            result
                        }
                    } else {
                        if (isWide) {
                            var result = 0L
                            for (i in values.indices) {
                                val value = values[i]
                                if (value is Number) {
                                    val l = value.toLong()
                                    result = if (i == 0) {
                                        l
                                    } else {
                                        result + l
                                    }
                                }
                            }
                            result
                        } else {
                            var result = 0
                            for (i in values.indices) {
                                val value = values[i]
                                if (value is Number) {
                                    val l = value.toInt()
                                    result = if (i == 0) {
                                        l
                                    } else {
                                        result + l
                                    }
                                }
                            }
                            result
                        }
                    }
                } else if (operator === JavaTokenType.MINUS) {
                    if (isFloat) {
                        if (isWide) {
                            var result = 0.0
                            for (i in values.indices) {
                                val value = values[i]
                                if (value is Number) {
                                    val l = value.toDouble()
                                    result = if (i == 0) {
                                        l
                                    } else {
                                        result - l
                                    }
                                }
                            }
                            result
                        } else {
                            var result = 0f
                            for (i in values.indices) {
                                val value = values[i]
                                if (value is Number) {
                                    val l = value.toFloat()
                                    result = if (i == 0) {
                                        l
                                    } else {
                                        result - l
                                    }
                                }
                            }
                            result
                        }
                    } else {
                        if (isWide) {
                            var result = 0L
                            for (i in values.indices) {
                                val value = values[i]
                                if (value is Number) {
                                    val l = value.toLong()
                                    result = if (i == 0) {
                                        l
                                    } else {
                                        result - l
                                    }
                                }
                            }
                            result
                        } else {
                            var result = 0
                            for (i in values.indices) {
                                val value = values[i]
                                if (value is Number) {
                                    val l = value.toInt()
                                    result = if (i == 0) {
                                        l
                                    } else {
                                        result - l
                                    }
                                }
                            }
                            result
                        }
                    }
                } else if (operator === JavaTokenType.ASTERISK) {
                    if (isFloat) {
                        if (isWide) {
                            var result = 0.0
                            for (i in values.indices) {
                                val value = values[i]
                                if (value is Number) {
                                    val l = value.toDouble()
                                    result = if (i == 0) {
                                        l
                                    } else {
                                        result * l
                                    }
                                }
                            }
                            result
                        } else {
                            var result = 0f
                            for (i in values.indices) {
                                val value = values[i]
                                if (value is Number) {
                                    val l = value.toFloat()
                                    result = if (i == 0) {
                                        l
                                    } else {
                                        result * l
                                    }
                                }
                            }
                            result
                        }
                    } else {
                        if (isWide) {
                            var result = 0L
                            for (i in values.indices) {
                                val value = values[i]
                                if (value is Number) {
                                    val l = value.toLong()
                                    result = if (i == 0) {
                                        l
                                    } else {
                                        result * l
                                    }
                                }
                            }
                            result
                        } else {
                            var result = 0
                            for (i in values.indices) {
                                val value = values[i]
                                if (value is Number) {
                                    val l = value.toInt()
                                    result = if (i == 0) {
                                        l
                                    } else {
                                        result * l
                                    }
                                }
                            }
                            result
                        }
                    }
                } else if (operator === JavaTokenType.DIV) {
                    if (isFloat) {
                        if (isWide) {
                            var result = 0.0
                            for (i in values.indices) {
                                val value = values[i]
                                if (value is Number) {
                                    val l = value.toDouble()
                                    result = if (i == 0) {
                                        l
                                    } else {
                                        result / l
                                    }
                                }
                            }
                            result
                        } else {
                            var result = 0f
                            for (i in values.indices) {
                                val value = values[i]
                                if (value is Number) {
                                    val l = value.toFloat()
                                    result = if (i == 0) {
                                        l
                                    } else {
                                        result / l
                                    }
                                }
                            }
                            result
                        }
                    } else {
                        if (isWide) {
                            var result = 0L
                            for (i in values.indices) {
                                val value = values[i]
                                if (value is Number) {
                                    val l = value.toLong()
                                    result = if (i == 0) {
                                        l
                                    } else if (l == 0L) {
                                        return null
                                    } else {
                                        result / l
                                    }
                                }
                            }
                            result
                        } else {
                            var result = 0
                            for (i in values.indices) {
                                val value = values[i]
                                if (value is Number) {
                                    val l = value.toInt()
                                    result = if (i == 0) {
                                        l
                                    } else if (l == 0) {
                                        return null
                                    } else {
                                        result / l
                                    }
                                }
                            }
                            result
                        }
                    }
                } else if (operator === JavaTokenType.PERC) {
                    if (isFloat) {
                        if (isWide) {
                            var result = 0.0
                            for (i in values.indices) {
                                val value = values[i]
                                if (value is Number) {
                                    val l = value.toDouble()
                                    result = if (i == 0) {
                                        l
                                    } else {
                                        result % l
                                    }
                                }
                            }
                            result
                        } else {
                            var result = 0f
                            for (i in values.indices) {
                                val value = values[i]
                                if (value is Number) {
                                    val l = value.toFloat()
                                    result = if (i == 0) {
                                        l
                                    } else {
                                        result % l
                                    }
                                }
                            }
                            result
                        }
                    } else {
                        if (isWide) {
                            var result = 0L
                            for (i in values.indices) {
                                val value = values[i]
                                if (value is Number) {
                                    val l = value.toLong()
                                    result = if (i == 0) {
                                        l
                                    } else if (l == 0L) {
                                        return null
                                    } else {
                                        result % l
                                    }
                                }
                            }
                            result
                        } else {
                            var result = 0
                            for (i in values.indices) {
                                val value = values[i]
                                if (value is Number) {
                                    val l = value.toInt()
                                    result = if (i == 0) {
                                        l
                                    } else if (l == 0) {
                                        return null
                                    } else {
                                        result % l
                                    }
                                }
                            }
                            result
                        }
                    }
                } else {
                    null
                }
            }
        } else if (node is PsiTypeCastExpression) {
            val cast = node
            val operandValue = evaluate(cast.operand)
            if (operandValue is Number) {
                val number = operandValue
                val typeElement = cast.castType
                if (typeElement != null) {
                    val type = typeElement.type
                    if (PsiType.FLOAT == type) {
                        return number.toFloat()
                    } else if (PsiType.DOUBLE == type) {
                        return number.toDouble()
                    } else if (PsiType.INT == type) {
                        return number.toInt()
                    } else if (PsiType.LONG == type) {
                        return number.toLong()
                    } else if (PsiType.SHORT == type) {
                        return number.toShort()
                    } else if (PsiType.BYTE == type) {
                        return number.toByte()
                    }
                }
            }
            return operandValue
        } else if (node is PsiReference) {
            val resolved = (node as PsiReference).resolve()
            if (resolved is PsiField) {
                val field = resolved

                // array.length expression?
                if ("length" == field.name && node is PsiReferenceExpression) {
                    val expression = node.qualifierExpression
                    if (expression != null && expression.type is PsiArrayType) {
                        // It's an array.length expression
                        val array = evaluate(expression)
                        val size = getArraySize(array)
                        return if (size != -1) {
                            size
                        } else null
                    }
                }
                var value = field.computeConstantValue()
                if (value != null) {
                    return value
                }
                if (field.initializer != null &&
                    (
                            allowFieldInitializers ||
                                    (
                                            field.hasModifierProperty(PsiModifier.STATIC) &&
                                                    field.hasModifierProperty(PsiModifier.FINAL)
                                            )
                            )
                ) {
                    value = evaluate(field.initializer)
                    if (value != null) {
                        // See if it looks like the value has been clamped locally
                        var curr = PsiTreeUtil.getParentOfType(node, PsiIfStatement::class.java)
                        while (curr != null) {
                            if (curr.condition != null
                                && references(curr.condition!!, field)
                            ) {
                                // Field is referenced surrounding this reference; don't
                                // take the field initializer since the value may have been
                                // value checked for some other later assigned value
                                // ...but only if it's not the condition!
                                val condition = curr.condition
                                if (!PsiTreeUtil.isAncestor(condition, node, true)) {
                                    return value
                                }
                            }
                            curr = PsiTreeUtil.getParentOfType(curr, PsiIfStatement::class.java, true)
                        }
                        return value
                    }
                }
                return null
            } else if (resolved is PsiLocalVariable) {
                val last = findLastAssignment(node, resolved)
                if (last != null) {
                    // TODO: Clamp value as is done for UAST?
                    return evaluate(last)
                }
            }
        } else if (node is PsiNewExpression) {
            val creation = node
            val initializer = creation.arrayInitializer
            var type = creation.type
            if (type is PsiArrayType) {
                return if (initializer != null) {
                    val initializers = initializer.initializers
                    var commonType: Class<*>? = null
                    val values: MutableList<Any> = Lists.newArrayListWithExpectedSize(initializers.size)
                    var count = 0
                    for (expression in initializers) {
                        val value = evaluate(expression)
                        if (value != null) {
                            values.add(value)
                            if (commonType == null) {
                                commonType = value.javaClass
                            } else {
                                while (!commonType!!.isAssignableFrom(value.javaClass)) {
                                    commonType = commonType.superclass
                                }
                            }
                        } else if (!allowUnknown) {
                            // Inconclusive
                            return null
                        }
                        count++
                        if (count == 40) { // avoid large initializers
                            return getArray(type.getDeepComponentType(), initializers.size, 1)
                        }
                    }
                    type = type.getDeepComponentType()
                    if (type === PsiType.INT) {
                        if (!values.isEmpty()) {
                            val array = IntArray(values.size)
                            for (i in values.indices) {
                                val o = values[i]
                                if (o is Int) {
                                    array[i] = o
                                }
                            }
                            return array
                        }
                        IntArray(0)
                    } else if (type === PsiType.BOOLEAN) {
                        if (!values.isEmpty()) {
                            val array = BooleanArray(values.size)
                            for (i in values.indices) {
                                val o = values[i]
                                if (o is Boolean) {
                                    array[i] = o
                                }
                            }
                            return array
                        }
                        BooleanArray(0)
                    } else if (type === PsiType.DOUBLE) {
                        if (!values.isEmpty()) {
                            val array = DoubleArray(values.size)
                            for (i in values.indices) {
                                val o = values[i]
                                if (o is Double) {
                                    array[i] = o
                                }
                            }
                            return array
                        }
                        DoubleArray(0)
                    } else if (type === PsiType.LONG) {
                        if (!values.isEmpty()) {
                            val array = LongArray(values.size)
                            for (i in values.indices) {
                                val o = values[i]
                                if (o is Long) {
                                    array[i] = o
                                }
                            }
                            return array
                        }
                        LongArray(0)
                    } else if (type === PsiType.FLOAT) {
                        if (!values.isEmpty()) {
                            val array = FloatArray(values.size)
                            for (i in values.indices) {
                                val o = values[i]
                                if (o is Float) {
                                    array[i] = o
                                }
                            }
                            return array
                        }
                        FloatArray(0)
                    } else if (type === PsiType.CHAR) {
                        if (!values.isEmpty()) {
                            val array = CharArray(values.size)
                            for (i in values.indices) {
                                val o = values[i]
                                if (o is Char) {
                                    array[i] = o
                                }
                            }
                            return array
                        }
                        CharArray(0)
                    } else if (type === PsiType.BYTE) {
                        if (!values.isEmpty()) {
                            val array = ByteArray(values.size)
                            for (i in values.indices) {
                                val o = values[i]
                                if (o is Byte) {
                                    array[i] = o
                                }
                            }
                            return array
                        }
                        ByteArray(0)
                    } else if (type === PsiType.SHORT) {
                        if (!values.isEmpty()) {
                            val array = ShortArray(values.size)
                            for (i in values.indices) {
                                val o = values[i]
                                if (o is Short) {
                                    array[i] = o
                                }
                            }
                            return array
                        }
                        ShortArray(0)
                    } else {
                        if (!values.isEmpty()) {
                            return values.toArray<Any> { n -> java.lang.reflect.Array.newInstance(commonType, n) as Array<*> }
                        }
                        null
                    }
                } else {
                    // something like "new byte[3]" but with no initializer.
                    // Look up the size and only if small, use it. E.g. if it was byte[3]
                    // we return a byte[3] array, but if it's say byte[1024*1024] we don't
                    // want to do that.
                    val arrayDimensions = creation.arrayDimensions
                    var size = 0
                    if (arrayDimensions.size > 0) {
                        val fixedSize = evaluate(arrayDimensions[0])
                        if (fixedSize is Number) {
                            size = fixedSize.toInt()
                        }
                    }
                    val dimensions = type.getArrayDimensions()
                    type = type.getDeepComponentType()
                    getArray(type, size, dimensions)
                }
            }
        } else if (node is KtLiteralStringTemplateEntry) {
            return node.getText()
        } else if (node is KtStringTemplateExpression) {
            val sb = StringBuilder()
            var parts = false
            for (entry in node.entries) {
                if (entry is KtLiteralStringTemplateEntry) {
                    sb.append(entry.getText())
                    parts = true
                    continue
                }
                val expression = entry.expression
                val part = evaluate(expression)
                if (part is String) {
                    sb.append(part)
                    parts = true
                }
            }
            if (parts) {
                return sb.toString()
            }
        } else {
            // If we resolve to a "val" in Kotlin, if it's not a const val but in reality is a val
            // (because
            // it has a constant expression and no getters and setters (and is not a var), then
            // compute
            // its value anyway.
            if (node is KtLightMethod) {
                return valueFromProperty(node.kotlinOrigin)
            }
            if (node is KtLightField) {
                return valueFromProperty(node.kotlinOrigin)
            }
            if (node is KtProperty) {
                return valueFromProperty(node)
            }
        }

        // TODO: Check for MethodInvocation and perform some common operations -
        // Math.* methods, String utility methods like notNullize, etc
        return null
    }

    private fun valueFromProperty(origin: KtDeclaration?): Any? {
        if (origin is KtProperty) {
            val property = origin
            if (allowFieldInitializers) {
                val initializer = property.initializer
                if (initializer != null) {
                    return evaluate(initializer)
                }
            } else if (!property.isVar && property.getter == null && property.setter == null) {
                // Property with no custom getter or setter? If it has an initializer
                // it might be
                // No setter: might be a constant not declared as such
                val initializer = property.initializer
                if (initializer != null) {
                    return evaluate(initializer)
                }
            }
        }
        return null
    }

    class ArrayReference {
        val type: Class<*>?
        val className: String?
        val size: Int
        val dimensions: Int

        constructor(type: Class<*>?, size: Int, dimensions: Int) {
            this.type = type
            className = null
            this.size = size
            this.dimensions = dimensions
        }

        constructor(className: String?, size: Int, dimensions: Int) {
            this.className = className
            type = null
            this.size = size
            this.dimensions = dimensions
        }

        override fun equals(o: Any?): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }
            val that = o as ArrayReference
            return size == that.size && dimensions == that.dimensions && type == that.type && className == that.className
        }

        override fun hashCode(): Int {
            return Objects.hash(type, className, size, dimensions)
        }

        override fun toString(): String {
            val sb = StringBuilder()
            sb.append("Array Reference: ")
            if (type != null) {
                sb.append(type.toString())
            } else if (className != null) {
                sb.append(className)
            }
            for (i in 0 until dimensions - 1) {
                sb.append("[]")
            }
            sb.append("[")
            sb.append(Integer.toString(size))
            sb.append("]")
            return sb.toString()
        }
    }

    companion object {
        /**
         * When evaluating expressions that resolve to arrays, this is the largest array size we'll
         * initialize; for larger arrays we'll return a [ArrayReference] instead
         */
        private const val LARGEST_LITERAL_ARRAY = 12
        private fun getKotlinPrimitiveArrayType(constructorName: String): PsiType? {
            when (constructorName) {
                "ByteArray", "byteArrayOf" -> return PsiPrimitiveType.BYTE
                "CharArray", "charArrayOf" -> return PsiPrimitiveType.CHAR
                "ShortArray", "shortArrayOf" -> return PsiPrimitiveType.SHORT
                "IntArray", "intArrayOf" -> return PsiPrimitiveType.INT
                "LongArray", "longArrayOf" -> return PsiPrimitiveType.LONG
                "FloatArray", "floatArrayOf" -> return PsiPrimitiveType.FLOAT
                "DoubleArray", "doubleArrayOf" -> return PsiPrimitiveType.DOUBLE
                "BooleanArray", "booleanArrayOf" -> return PsiPrimitiveType.BOOLEAN
            }
            return null
        }

        fun getArraySize(array: Any?): Int {
            // This is kinda repetitive but there is no subtyping relationship between
            // primitive arrays; int[] is not a subtype of Object[] etc.
            if (array is ArrayReference) {
                return array.size
            }
            if (array is IntArray) {
                return array.size
            }
            if (array is LongArray) {
                return array.size
            }
            if (array is FloatArray) {
                return array.size
            }
            if (array is DoubleArray) {
                return array.size
            }
            if (array is CharArray) {
                return array.size
            }
            if (array is ByteArray) {
                return array.size
            }
            if (array is ShortArray) {
                return array.size
            }
            return if (array is Array<*>) {
                array.size
            } else -1
        }

        private fun surroundedByVariableCheck(
            node: UElement?,
            variable: PsiVariable
        ): Boolean {
            if (node == null) {
                return false
            }

            // See if it looks like the value has been clamped locally, e.g.
            var curr = node.getParentOfType(UIfExpression::class.java)
            while (curr != null) {
                if (references(curr.condition, variable)) {
                    // variable is referenced surrounding this reference; don't
                    // take the variable initializer since the value may have been
                    // value checked for some other later assigned value
                    // ...but only if it's not the condition!
                    val condition = curr.condition
                    if (!node.isUastChildOf(condition, false)) {
                        return true
                    }
                }
                curr = curr.getParentOfType(UIfExpression::class.java)
            }
            return false
        }

        private fun isStringType(type: PsiType): Boolean {
            if (type !is PsiClassType) {
                return false
            }
            val resolvedClass = type.resolve()
            return resolvedClass != null && TYPE_STRING == resolvedClass.qualifiedName
        }

        private fun isObjectType(type: PsiType): Boolean {
            if (type !is PsiClassType) {
                return false
            }
            val resolvedClass = type.resolve()
            return resolvedClass != null && TYPE_OBJECT == resolvedClass.qualifiedName
        }

        private fun getArray(type: PsiType, size: Int, dimensions: Int): Any? {
            if (type is PsiPrimitiveType) {
                if (size <= LARGEST_LITERAL_ARRAY) {
                    if (PsiType.BYTE == type) {
                        return ByteArray(size)
                    }
                    if (PsiType.BOOLEAN == type) {
                        return BooleanArray(size)
                    }
                    if (PsiType.INT == type) {
                        return IntArray(size)
                    }
                    if (PsiType.LONG == type) {
                        return LongArray(size)
                    }
                    if (PsiType.CHAR == type) {
                        return CharArray(size)
                    }
                    if (PsiType.FLOAT == type) {
                        return FloatArray(size)
                    }
                    if (PsiType.DOUBLE == type) {
                        return DoubleArray(size)
                    }
                    if (PsiType.SHORT == type) {
                        return ShortArray(size)
                    }
                } else {
                    if (PsiType.BYTE == type) {
                        return ArrayReference(java.lang.Byte.TYPE, size, dimensions)
                    }
                    if (PsiType.BOOLEAN == type) {
                        return ArrayReference(java.lang.Boolean.TYPE, size, dimensions)
                    }
                    if (PsiType.INT == type) {
                        return ArrayReference(Integer.TYPE, size, dimensions)
                    }
                    if (PsiType.LONG == type) {
                        return ArrayReference(java.lang.Long.TYPE, size, dimensions)
                    }
                    if (PsiType.CHAR == type) {
                        return ArrayReference(Character.TYPE, size, dimensions)
                    }
                    if (PsiType.FLOAT == type) {
                        return ArrayReference(java.lang.Float.TYPE, size, dimensions)
                    }
                    if (PsiType.DOUBLE == type) {
                        return ArrayReference(java.lang.Double.TYPE, size, dimensions)
                    }
                    if (PsiType.SHORT == type) {
                        return ArrayReference(java.lang.Short.TYPE, size, dimensions)
                    }
                }
            } else if (type is PsiClassType) {
                val className = type.getCanonicalText()
                return if (TYPE_STRING == className) {
                    if (size < LARGEST_LITERAL_ARRAY) {
                        arrayOfNulls<String>(size)
                    } else {
                        ArrayReference(String::class.java, size, dimensions)
                    }
                } else if (TYPE_OBJECT == className) {
                    if (size < LARGEST_LITERAL_ARRAY) {
                        arrayOfNulls<Any>(size)
                    } else {
                        ArrayReference(Any::class.java, size, dimensions)
                    }
                } else {
                    ArrayReference(className, size, dimensions)
                }
            }
            return null
        }

        /** Returns true if the given variable is referenced from within the given element  */
        private fun references(
            element: PsiExpression,
            variable: PsiVariable
        ): Boolean {
            val found = AtomicBoolean()
            element.accept(
                object : JavaRecursiveElementVisitor() {
                    override fun visitReferenceElement(reference: PsiJavaCodeReferenceElement) {
                        val refersTo = reference.resolve()
                        if (variable == refersTo) {
                            found.set(true)
                        }
                        super.visitReferenceElement(reference)
                    }
                })
            return found.get()
        }

        /** Returns true if the given variable is referenced from within the given element  */
        private fun references(element: UExpression, variable: PsiVariable): Boolean {
            val found = AtomicBoolean()
            element.accept(
                object : AbstractUastVisitor() {
                    override fun visitSimpleNameReferenceExpression(
                        node: USimpleNameReferenceExpression
                    ): Boolean {
                        val refersTo = node.resolve()
                        if (variable == refersTo) {
                            found.set(true)
                        }
                        return super.visitSimpleNameReferenceExpression(node)
                    }
                })
            return found.get()
        }

        /** Returns true if the node is pointing to a an array literal  */
        fun isArrayLiteral(node: PsiElement?): Boolean {
            if (node is PsiReference) {
                val resolved = (node as PsiReference).resolve()
                if (resolved is PsiField) {
                    val field = resolved
                    if (field.initializer != null) {
                        return isArrayLiteral(field.initializer)
                    }
                } else if (resolved is PsiLocalVariable) {
                    val last = findLastAssignment(node, resolved)
                    if (last != null) {
                        return isArrayLiteral(last)
                    }
                }
            } else if (node is PsiNewExpression) {
                val creation = node
                if (creation.arrayInitializer != null) {
                    return true
                }
                val type = creation.type
                if (type is PsiArrayType) {
                    return true
                }
            } else if (node is PsiParenthesizedExpression) {
                val expression = node.expression
                if (expression != null) {
                    return isArrayLiteral(expression)
                }
            } else if (node is PsiTypeCastExpression) {
                val operand = node.operand
                if (operand != null) {
                    return isArrayLiteral(operand)
                }
            }
            return false
        }

        /** Returns true if the node is pointing to a an array literal  */
        fun isArrayLiteral(node: UElement?): Boolean {
            if (node is UReferenceExpression) {
                val resolved = node.resolve()
                if (resolved is PsiVariable) {
                    val lastAssignment = findLastAssignment(resolved, node)
                    if (lastAssignment != null) {
                        return isArrayLiteral(lastAssignment)
                    }
                }
            } else if (node!!.isNewArrayWithDimensions()) {
                return true
            } else if (node.isNewArrayWithInitializer()) {
                return true
            } else if (node is UParenthesizedExpression) {
                val expression = node.expression
                return isArrayLiteral(expression)
            } else if (node.isTypeCast()) {
                val castExpression = (node as UBinaryExpressionWithType?)!!
                val operand = castExpression.operand
                return isArrayLiteral(operand)
            }
            return false
        }

        /**
         * Evaluates the given node and returns the constant value it resolves to, if any. Convenience
         * wrapper which creates a new [ConstantEvaluator], evaluates the node and returns
         * the result.
         *
         * @param context the context to use to resolve field references, if any
         * @param node the node to compute the constant value for
         * @return the corresponding constant value - a String, an Integer, a Float, and so on
         */
        @JvmStatic
        fun evaluate(context: JavaContext?, node: PsiElement): Any? {
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
              */return ConstantEvaluator().evaluate(node)
        }

        /**
         * Evaluates the given node and returns the constant value it resolves to, if any. Convenience
         * wrapper which creates a new [ConstantEvaluator], evaluates the node and returns
         * the result.
         *
         * @param context the context to use to resolve field references, if any
         * @param element the node to compute the constant value for
         * @return the corresponding constant value - a String, an Integer, a Float, and so on
         */
        @JvmStatic
        fun evaluate(context: JavaContext?, element: UElement): Any? {
            return if (element is ULiteralExpression) {
                element.value
            } else ConstantEvaluator()
                .evaluate(element)
        }

        /**
         * Evaluates the given node and returns the constant string it resolves to, if any. Convenience
         * wrapper which creates a new [ConstantEvaluator], evaluates the node and returns
         * the result if the result is a string.
         *
         * @param context the context to use to resolve field references, if any
         * @param node the node to compute the constant value for
         * @param allowUnknown whether we should construct the string even if some parts of it are
         * unknown
         * @return the corresponding string, if any
         */
        @JvmStatic
        fun evaluateString(
            context: JavaContext?,
            node: PsiElement,
            allowUnknown: Boolean
        ): String? {
            val evaluator = ConstantEvaluator()
            if (allowUnknown) {
                evaluator.allowUnknowns()
            }
            val value = evaluator.evaluate(node)
            return if (value is String) value else null
        }

        /**
         * Computes the last assignment to a given variable counting backwards from the given context
         * element
         *
         * @param usage the usage site to search backwards from
         * @param variable the variable
         * @return the last assignment or null
         */
        @JvmStatic
        fun findLastAssignment(
            usage: PsiElement,
            variable: PsiVariable
        ): PsiExpression? {
            return findLastAssignment(usage, variable, false)
        }

        /**
         * Computes the last assignment to a given variable counting backwards from the given context
         * element
         *
         * @param usage the usage site to search backwards from
         * @param variable the variable
         * @param allowNonConst If set to true and the returned assignment is non-null, this means that
         * the last assignment is inside an if/else block, whose execution may not be statically
         * determinable.
         * @return the last assignment or null
         */
        private fun findLastAssignment(
            usage: PsiElement,
            variable: PsiVariable,
            allowNonConst: Boolean
        ): PsiExpression? {
            // Walk backwards through assignments to find the most recent initialization
            // of this variable
            val statement = PsiTreeUtil.getParentOfType(usage, PsiStatement::class.java, false)
            if (statement != null) {
                var prev = if (allowNonConst) // If allowNonConst is true, it means the search starts from the last
                // statement in an if/else
                // block, so don't skip the passed-in statement
                    statement else PsiTreeUtil.getPrevSiblingOfType(statement, PsiStatement::class.java)
                val targetName = variable.name ?: return null
                while (prev != null) {
                    if (prev is PsiDeclarationStatement) {
                        for (element in prev.declaredElements) {
                            if (variable == element) {
                                return variable.initializer
                            }
                        }
                    } else if (prev is PsiExpressionStatement) {
                        val expression = prev.expression
                        if (expression is PsiAssignmentExpression) {
                            val assign = expression
                            val lhs = assign.lExpression
                            if (lhs is PsiReferenceExpression) {
                                val reference = lhs
                                if (targetName == reference.referenceName && reference.qualifier == null) {
                                    return assign.rExpression
                                }
                            }
                        }
                    } else if (prev is PsiIfStatement) {
                        val thenBranch = prev.thenBranch
                        if (thenBranch is PsiBlockStatement) {
                            val thenStatements = thenBranch.codeBlock.statements
                            val assignmentInIf = if (thenStatements.size > 0) findLastAssignment(
                                thenStatements[thenStatements.size - 1],
                                variable,
                                true
                            ) else null
                            if (assignmentInIf != null) {
                                return if (allowNonConst) assignmentInIf else null
                            }
                        }
                        val elseBranch = prev.elseBranch
                        if (elseBranch is PsiBlockStatement) {
                            val elseStatements = elseBranch.codeBlock.statements
                            val assignmentInElse = if (elseStatements.size > 0) findLastAssignment(
                                elseStatements[elseStatements.size - 1],
                                variable,
                                true
                            ) else null
                            if (assignmentInElse != null) {
                                return if (allowNonConst) assignmentInElse else null
                            }
                        }
                    }
                    prev = PsiTreeUtil.getPrevSiblingOfType(prev, PsiStatement::class.java)
                }
            }
            return null
        }

        /**
         * Evaluates the given node and returns the constant string it resolves to, if any. Convenience
         * wrapper which creates a new [ConstantEvaluator], evaluates the node and returns
         * the result if the result is a string.
         *
         * @param context the context to use to resolve field references, if any
         * @param element the node to compute the constant value for
         * @param allowUnknown whether we should construct the string even if some parts of it are
         * unknown
         * @return the corresponding string, if any
         */
        @JvmStatic
        fun evaluateString(
            context: JavaContext?,
            element: UElement,
            allowUnknown: Boolean
        ): String? {
            val evaluator = ConstantEvaluator()
            if (allowUnknown) {
                evaluator.allowUnknowns()
            }
            val value = evaluator.evaluate(element)
            return if (value is String) value else null
        }
    }
}

private fun Char.inv() = code.inv()
private operator fun Char.unaryMinus() = -code
