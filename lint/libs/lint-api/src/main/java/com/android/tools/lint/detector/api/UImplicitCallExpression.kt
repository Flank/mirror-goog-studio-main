/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.intellij.lang.Language
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiType
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UArrayAccessExpression
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UComment
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UIdentifier
import org.jetbrains.uast.UPostfixExpression
import org.jetbrains.uast.UPrefixExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UUnaryExpression
import org.jetbrains.uast.UastCallKind
import org.jetbrains.uast.util.isAssignment
import org.jetbrains.uast.visitor.AbstractUastVisitor
import org.jetbrains.uast.visitor.UastTypedVisitor
import org.jetbrains.uast.visitor.UastVisitor

/**
 * A [UImplicitCallExpression] represents an overloaded function call
 * which is not represented as a call in the AST; for example, a
 * [UArrayAccessExpression] for `[]` which calls `get` (or inside an
 * assignment, `set`) methods, or a [UBinaryExpression] for `<` which
 * calls `compareTo`, or a [UUnaryExpression] for '++' calling `inc` and
 * so on.
 *
 * This makes it easier to handle calls in a uniform way. You can
 * use [UElement.asCall] to convert a random element to a call if
 * applicable, or one of the dedicated [UBinaryExpression.asCall],
 * [UUnaryExpression.asCall], or [UArrayAccessExpression] directly.
 *
 * If you just want to visit calls, consider using the
 * [UastCallVisitor].
 */
abstract class UImplicitCallExpression(
    /**
     * The original expression that is implicitly making a call to an
     * overloaded function.
     */
    val expression: UExpression,
    /** The function being called. */
    val operator: PsiMethod
) : UCallExpression {
    abstract override val receiver: UExpression?
    abstract override val receiverType: PsiType?
    abstract override val valueArguments: List<UExpression>
    override val sourcePsi: PsiElement? get() = expression.sourcePsi
    override val javaPsi: PsiElement? get() = expression.javaPsi
    override val comments: List<UComment> get() = expression.comments
    override val isPsiValid: Boolean get() = expression.isPsiValid
    override val lang: Language get() = expression.lang
    override fun getExpressionType(): PsiType? = expression.getExpressionType()
    override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R = expression.accept(visitor, data)
    override fun accept(visitor: UastVisitor) = expression.accept(visitor)
    override fun evaluate(): Any? = expression.evaluate()
    override fun findAnnotation(fqName: String): UAnnotation? = expression.findAnnotation(fqName)
    override fun asRenderString(): String = expression.asRenderString()
    override fun asLogString(): String = expression.asLogString()
    override fun asSourceString(): String = expression.asSourceString()
    override fun equals(other: Any?): Boolean = expression == other
    override fun hashCode(): Int = expression.hashCode()
    override fun toString(): String = expression.toString()
    abstract fun getArgumentMapping(): Map<UExpression, PsiParameter>

    override val uAnnotations: List<UAnnotation>
        @Suppress("ExternalAnnotations")
        get() = expression.uAnnotations

    @Suppress("OverridingDeprecatedMember", "DEPRECATION")
    override val psi: PsiElement?
        get() = expression.psi

    override val classReference: UReferenceExpression? = null
    override val kind: UastCallKind get() = UastCallKind.METHOD_CALL
    override val methodIdentifier: UIdentifier? = null
    override val methodName: String get() = operator.name
    override val returnType: PsiType? get() = operator.returnType
    override val typeArgumentCount: Int get() = 0
    override val typeArguments: List<PsiType> get() = emptyList()
    override val uastParent: UElement? get() = expression.uastParent
    override val valueArgumentCount: Int get() = valueArguments.size
    override fun getArgumentForParameter(i: Int): UExpression {
        val parameter = operator.parameterList.parameters[i]
        val argumentMapping = getArgumentMapping()
        for ((argument, p) in argumentMapping) {
            if (parameter == p) {
                return argument
            }
        }
        return valueArguments[i]
    }
    override fun resolve(): PsiMethod = operator
}

/**
 * If this [UElement] references an overloaded function call, returns a
 * [UCallExpression] which presents this element as a call
 */
fun UElement.asCall(): UCallExpression? {
    return when (this) {
        is UCallExpression -> this
        is UBinaryExpression -> asCall()
        is UArrayAccessExpression -> asCall()
        is UUnaryExpression -> asCall()
        else -> null
    }
}

/**
 * If this [UBinaryExpression] references an overloaded function call,
 * returns a [UCallExpression] which represents the call of that binary
 * function.
 */
fun UBinaryExpression.asCall(): UCallExpression? {
    val operator = resolveOperator() ?: return null
    return asCall(operator)
}

/**
 * When this [UBinaryExpression] is calling the given [operator] method,
 * returns a [UCallExpression] which represents the call of that binary
 * function.
 */
fun UBinaryExpression.asCall(operator: PsiMethod): UCallExpression {
    return BinaryExpressionAsCallExpression(this, operator)
}

/**
 * If this [UArrayAccessExpression] references an overloaded function
 * call, returns a [UCallExpression] which represents the array access.
 */
fun UArrayAccessExpression.asCall(): UCallExpression? {
    val operator = resolveOperator() ?: return null
    return asCall(operator)
}

/**
 * When this [UArrayAccessExpression] is calling the given [operator]
 * method, returns a [UCallExpression] which represents the array
 * access.
 */
fun UArrayAccessExpression.asCall(operator: PsiMethod): UCallExpression {
    val parent = this.uastParent as? UBinaryExpression
    val setter = if (parent != null && parent.isAssignment()) {
        parent.rightOperand
    } else {
        null
    }
    return ArrayAccessAsCallExpression(this, setter, operator)
}

/**
 * If this [UUnaryExpression] references an overloaded function call,
 * returns a [UCallExpression] which represents the call of that unary
 * function.
 */
fun UUnaryExpression.asCall(): UCallExpression? {
    val operator = resolveOperator() ?: return null
    return asCall(operator)
}

/**
 * When this [UUnaryExpression] is calling the given [operator] method,
 * returns a [UCallExpression] which represents the call of that unary
 * function.
 */
fun UUnaryExpression.asCall(operator: PsiMethod): UCallExpression {
    return UnaryExpressionAsCallExpression(this, operator)
}

/**
 * Class which wraps an [UUnaryExpression] as a [UCallExpression].
 *
 * See [UUnaryExpression.asCall].
 */
private class UnaryExpressionAsCallExpression(
    private val unary: UUnaryExpression,
    operator: PsiMethod
) : UImplicitCallExpression(unary, operator) {
    override val receiver: UExpression
        get() = unary.operand
    override val receiverType: PsiType?
        get() = receiver.getExpressionType()
    override val valueArguments: List<UExpression>
        get() = emptyList()
    override val methodIdentifier: UIdentifier?
        get() = unary.operatorIdentifier
    override fun getArgumentMapping(): Map<UExpression, PsiParameter> = emptyMap()
}

/**
 * Class which wraps an [UBinaryExpression] as a [UCallExpression].
 *
 * See [UBinaryExpression.asCall].
 */
private class BinaryExpressionAsCallExpression(
    private val binary: UBinaryExpression,
    operator: PsiMethod
) : UImplicitCallExpression(binary, operator) {
    // See https://kotlinlang.org/docs/operator-overloading.html#binary-operations
    // All the operators are "a.something(b)" except for the containment operators
    // which are "b.contains(a)"
    private val isReversed: Boolean = binary.operator.text.let { text -> text == "in" || text == "!in" }

    /** Infix or extension function? */
    private val isSingleParameter: Boolean = operator.parameterList.parameters.size == 1

    override val methodIdentifier: UIdentifier?
        get() = binary.operatorIdentifier
    override val receiver: UExpression?
        get() = if (isSingleParameter) if (isReversed) binary.rightOperand else binary.leftOperand else null
    override val receiverType: PsiType?
        get() = receiver?.getExpressionType()
    override val valueArguments: List<UExpression>
        get() {
            return if (isReversed) {
                if (isSingleParameter)
                // extension function, second parameter is receiver
                    listOf(binary.leftOperand)
                else {
                    listOf(binary.rightOperand, binary.leftOperand)
                }
            } else {
                if (isSingleParameter) {
                    // extension function, first parameter is receiver
                    listOf(binary.rightOperand)
                } else {
                    listOf(binary.leftOperand, binary.rightOperand)
                }
            }
        }

    override fun resolve(): PsiMethod {
        return operator
    }

    override fun getArgumentMapping(): Map<UExpression, PsiParameter> {
        val method = resolve()
        val parameters = method.parameterList.parameters
        val arguments = this.valueArguments
        val argumentCount = arguments.size
        val start = when (parameters.size) {
            argumentCount -> 0
            argumentCount + 1 -> 1
            else -> return emptyMap()
        }
        return arguments.mapIndexed { index, value -> value to parameters[index + start] }.toMap()
    }
}

/**
 * Class which wraps an [UArrayAccessExpression] as a [UCallExpression].
 *
 * See [UArrayAccessExpression.asCall].
 */
private class ArrayAccessAsCallExpression(
    private val accessExpression: UArrayAccessExpression,
    private val setter: UExpression?,
    operator: PsiMethod
) : UImplicitCallExpression(accessExpression, operator) {
    override val receiver: UExpression get() = accessExpression.receiver
    override val receiverType: PsiType? get() = accessExpression.getExpressionType()
    override val methodIdentifier: UIdentifier?
        get() {
            var bracket = accessExpression.indices.firstOrNull()?.sourcePsi?.prevSibling
            while (bracket is PsiWhiteSpace || bracket is PsiComment) {
                bracket = bracket.prevSibling
            }
            if (bracket is LeafPsiElement && bracket.text == "[") {
                return UIdentifier(bracket, null)
            }
            return null
        }

    private var _arguments: List<UExpression>? = null
    override val valueArguments: List<UExpression>
        get() {
            val arguments = _arguments
            if (arguments != null) {
                return arguments
            }
            val newArguments: List<UExpression> = accessExpression.indices + listOfNotNull(setter)
            _arguments = newArguments
            return newArguments
        }

    override fun getArgumentMapping(): Map<UExpression, PsiParameter> {
        val arguments = valueArguments
        val parameters = operator.parameterList.parameters
        if (parameters.isEmpty()) {
            return emptyMap()
        }

        val argumentCount = arguments.size
        val parameterCount = parameters.size
        val start = if (parameters[0].isReceiver()) 1 else 0
        val indices = arguments.asSequence().mapIndexed { index, value -> value to parameters[start + index] }
        if (parameterCount - start == argumentCount) {
            return indices.toMap()
        }

        // Should not happen but technically possible since we're doing
        // our own method resolve for UArrayAccessExpression until UAST supports it
        // and we could have pointed to the wrong method
        return emptyMap()
    }
}

/**
 * Visitor which visits all the calls in the target context -- including
 * overloaded operators
 */
abstract class UastCallVisitor : AbstractUastVisitor() {
    abstract fun visitCall(node: UCallExpression): Boolean

    /**
     * Don't override or call this method directly; instead, override
     * [visitCall]
     */
    final override fun visitCallExpression(node: UCallExpression): Boolean {
        if (visitCall(node)) {
            return true
        }
        return super.visitCallExpression(node)
    }

    override fun visitPrefixExpression(node: UPrefixExpression): Boolean {
        node.asCall()?.let(::visitCallExpression)?.let { if (it) return true }
        return super.visitPrefixExpression(node)
    }

    override fun visitPostfixExpression(node: UPostfixExpression): Boolean {
        node.asCall()?.let(::visitCallExpression)?.let { if (it) return true }
        return super.visitPostfixExpression(node)
    }

    final override fun visitUnaryExpression(node: UUnaryExpression): Boolean {
        node.asCall()?.let(::visitCallExpression)?.let { if (it) return true }
        return super.visitUnaryExpression(node)
    }

    final override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
        node.asCall()?.let(::visitCallExpression)?.let { if (it) return true }
        return super.visitBinaryExpression(node)
    }

    final override fun visitArrayAccessExpression(node: UArrayAccessExpression): Boolean {
        node.asCall()?.let(::visitCallExpression)?.let { if (it) return true }
        return super.visitArrayAccessExpression(node)
    }
}
