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
import com.android.tools.lint.detector.api.ConstantEvaluator.ArrayReference
import com.android.tools.lint.detector.api.UastLintUtils.Companion.findLastAssignment
import com.android.tools.lint.detector.api.UastLintUtils.Companion.findLastValue
import com.intellij.psi.JavaTokenType
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiAssignmentExpression
import com.intellij.psi.PsiBlockStatement
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiConditionalExpression
import com.intellij.psi.PsiDeclarationStatement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiExpressionStatement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiIfStatement
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
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.experimental.inv

/** Evaluates constant expressions  */
class ConstantEvaluator {
    private var allowUnknown = false
    private var allowFieldInitializers = false

    /**
     * Whether we allow computing values where some terms are unknown. For example, the expression
     * `"foo" + x + "bar"` would return `null` without and `"foobar"` with.
     *
     * @return this for constructor chaining
     */
    fun allowUnknowns(): ConstantEvaluator = this.also { allowUnknown = true }
    fun allowFieldInitializers(): ConstantEvaluator = this.also { allowFieldInitializers = true }

    /**
     * Evaluates the given node and returns the constant value it resolves to, if any
     *
     * @param node the node to compute the constant value for
     * @return the corresponding constant value - a String, an Integer, a Float, and so on
     */
    fun evaluate(node: UElement?): Any? = when {
        node == null -> null
        node is ULiteralExpression -> node.value
        node is UPrefixExpression -> evaluate(node.operand)?.let { operand ->
            when (node.operator) {
                UastPrefixOperator.LOGICAL_NOT -> operand.tryOn(Boolean::not)
                UastPrefixOperator.UNARY_PLUS -> operand
                UastPrefixOperator.BITWISE_NOT -> operand.tryInv()
                UastPrefixOperator.UNARY_MINUS -> operand.tryUnaryMinus()
                else -> null
            }
        }
        node is UIfExpression -> when (node.getExpressionType()) {
            null -> null
            else -> when (evaluate(node.condition)) {
                true -> node.thenExpression?.let(::evaluate)
                false -> node.elseExpression?.let(::evaluate)
                else -> null
            }
        }
        node is UParenthesizedExpression -> evaluate(node.expression)
        node is UPolyadicExpression -> node.operands.map(::evaluate).let(::ArgList).let { operands ->
            when {
                // For empty strings the Kotlin string template will return an empty operand list
                operands.values.isEmpty() && node.sourcePsi is KtStringTemplateExpression -> ""
                else -> when (node.operator) {
                    UastBinaryOperator.LOGICAL_OR -> operands.logicalOr()
                    UastBinaryOperator.LOGICAL_AND -> operands.logicalAnd()
                    // TODO Wrong below. ConstantEvaluator can't be used to check referential equality.
                    UastBinaryOperator.IDENTITY_EQUALS, UastBinaryOperator.EQUALS -> operands.ifAll<Any>()?.isOrdered(Any::equals)
                    UastBinaryOperator.IDENTITY_NOT_EQUALS, UastBinaryOperator.NOT_EQUALS -> operands.tryOn(Any::notEquals)
                    UastBinaryOperator.BITWISE_OR -> operands.bitwiseOr()
                    UastBinaryOperator.BITWISE_XOR -> operands.bitwiseXor()
                    UastBinaryOperator.BITWISE_AND -> operands.bitwiseAnd()
                    UastBinaryOperator.GREATER -> operands.isOrdered(Double::gt, Long::gt)
                    UastBinaryOperator.GREATER_OR_EQUALS -> operands.isOrdered(Double::ge, Long::ge)
                    UastBinaryOperator.LESS -> operands.isOrdered(Double::lt, Long::lt)
                    UastBinaryOperator.LESS_OR_EQUALS -> operands.isOrdered(Double::le, Long::le)
                    UastBinaryOperator.SHIFT_LEFT -> operands.shl()
                    UastBinaryOperator.SHIFT_RIGHT -> operands.shr()
                    UastBinaryOperator.UNSIGNED_SHIFT_RIGHT -> operands.ushr()
                    UastBinaryOperator.PLUS -> operands.plus(allowUnknown)
                    UastBinaryOperator.MINUS -> operands.minus()
                    UastBinaryOperator.MULTIPLY -> operands.times()
                    UastBinaryOperator.DIV -> operands.div()
                    UastBinaryOperator.MOD -> operands.mod()
                    else -> null
                }
            }
        }
        node is UBinaryExpressionWithType -> when (node.operationKind) {
            UastBinaryExpressionWithTypeKind.TypeCast.INSTANCE -> evaluate(node.operand).tryToNum(node.type)
            else -> null
        }
        node is UReferenceExpression -> node.resolve().let { resolved ->
            when {
                resolved is PsiVariable -> {
                    when {
                        // Handle fields specially: we can't look for last assignment
                        // on fields since the modifications are often in different methods
                        // Only take the field constant or initializer value if it's final,
                        // or if allowFieldInitializers is true
                        resolved !is PsiField -> findLastValue(resolved, node, this).let { value ->
                            when {
                                // Special return value: the variable *was* assigned something but we don't know
                                // the value. In that case we should not continue to look at the initializer
                                // since the initial value is no longer relevant.
                                value == LastAssignmentFinder.LastAssignmentValueUnknown -> null
                                surroundedByVariableCheck(node, resolved) -> null
                                else -> value ?: resolved.initializer?.let(::evaluate)
                            }
                        }
                        resolved.name == "length" -> // It's an array.length expression
                            node.tryOn(UQualifiedReferenceExpression::receiver)
                                ?.takeIf(UExpression::getExpressionType then { it is PsiArrayType })
                                ?.let(::evaluate)
                                ?.let(::getArraySize)
                                ?.takeUnless { it == -1 }
                        else -> resolved.computeConstantValue()
                            ?: resolved.getAllowedInitializer()
                                ?.let(::evaluate)
                                ?.takeUnless { surroundedByVariableCheck(node, resolved) }
                            ?: (resolved as? KtLightField)?.let(::evaluate)
                    }
                }
                node is UQualifiedReferenceExpression -> {
                    fun UExpression.simpleRefId(): String? = tryOn(USimpleNameReferenceExpression::identifier)
                    val selector = node.selector
                    when {
                        node.receiver.simpleRefId() == "kotlin" -> evaluate(selector) // such as kotlin.IntArray(x)
                        selector is USimpleNameReferenceExpression -> {
                            val receiver = node.receiver.let { r ->
                                when {
                                    // "kotlin.<N>Array".size ?
                                    r is UQualifiedReferenceExpression && r.receiver.simpleRefId() == "kotlin" -> r.selector
                                    else -> r
                                }
                            }
                            // TODO: Handle listOf, arrayListOf etc as well!
                            when {
                                "size" == selector.identifier && receiver is UCallExpression ->
                                    getMethodName(receiver)?.let { name ->
                                        when (name) {
                                            "Array", in kotlinPrimArrayFixedArgConstructors, "arrayOfNulls" ->
                                                evaluateFirstArg(receiver).tryOn(Number::toInt)
                                            "arrayOf", in kotlinPrimArrayVarargConstructors ->
                                                receiver.valueArgumentCount
                                            else -> null
                                        }
                                    }
                                else -> null
                            }
                        }
                        selector is UCallExpression -> {
                            val receiver = node.receiver
                            when {
                                selector.methodName == "trimIndent" -> evaluate(receiver).tryOn(String::trimIndent)
                                selector.methodName == "trimMargin" -> evaluate(receiver)?.tryOn { s: String ->
                                    val prefix = when (selector.valueArguments.size) {
                                        1 -> evaluate(selector.valueArguments[0])?.toString() ?: "|"
                                        else -> "|"
                                    }
                                    s.trimMargin(prefix)
                                }
                                // In String#format attempt to pick out at least the formatting string.
                                // In theory we could also evaluate all the arguments and try passing them
                                // in but there's some risk of invalid formatting string combinations.
                                selector.methodName == "format" && allowUnknown && selector.valueArguments.size >= 2 -> {
                                    val (first, second) = selector.valueArguments
                                    evaluate(
                                        when (first.getExpressionType()?.canonicalText) {
                                            "java.util.Locale" -> second
                                            else -> first
                                        }
                                    )
                                }
                                else -> null
                            }
                        }
                        else -> null
                    }
                }
                else -> node.evaluate()
            }
        }
        node.isNewArrayWithDimensions() -> {
            val call = node as UCallExpression
            val arrayType = call.getExpressionType()
            (arrayType as? PsiArrayType)?.deepComponentType?.let { type ->
                // Single-dimension array
                if (type !is PsiArrayType && call.valueArgumentCount == 1) {
                    evaluate(call.valueArguments[0])?.tryOn(Number::toInt)?.let { length ->
                        freshArray(type, length, arrayType.getArrayDimensions())
                    }
                } else null
            }
        }
        node.isNewArrayWithInitializer() -> evalAsArray(node as UCallExpression)
        node is UCallExpression -> getMethodName(node)?.let { name ->
            fun <A> withFixedSize(k: (Int) -> A): A? = evaluateFirstArg(node)?.tryOn(Number::toInt)?.let(k)
            fun freshObjArray() = when (val type = node.getExpressionType()) {
                is PsiArrayType -> withFixedSize { freshArray(type.deepComponentType, it, type.arrayDimensions) }
                else -> null
            }
            when {
                name == "arrayOf" || name in kotlinPrimArrayVarargConstructors -> evalAsArray(node)
                name == "arrayOfNulls" -> freshObjArray()
                !node.isConstructorCall() -> null
                name == "Array" -> freshObjArray()
                name in kotlinPrimArrayFixedArgConstructors ->
                    withFixedSize { n -> freshArray(kotlinPrimArrayTypeByConstructor[name]!!, n, 1) }
                else -> null
            }
        }
        node is UArrayAccessExpression -> {
            val indices = node.indices
            if (indices.size == 1) {
                (evaluate(indices[0]) as? Number)?.toInt()?.let { index ->
                    evaluate(node.receiver)?.let { array ->
                        array.asArray(Array<*>::indices, Array<*>::get)
                            ?: array.asArray(IntArray::indices, IntArray::get)
                            ?: array.asArray(BooleanArray::indices, BooleanArray::get)
                            ?: array.asArray(CharArray::indices, CharArray::get)
                            ?: array.asArray(LongArray::indices, LongArray::get)
                            ?: array.asArray(FloatArray::indices, FloatArray::get)
                            ?: array.asArray(DoubleArray::indices, DoubleArray::get)
                            ?: array.asArray(ByteArray::indices, ByteArray::get)
                            ?: array.asArray(ShortArray::indices, ShortArray::get)
                    }?.invoke(index)
                }
            } else null
        }
        // TODO: Check for MethodInvocation and perform some common operations -
        // Math.* methods, String utility methods like notNullize, etc
        else -> null
    }

    private fun evaluateFirstArg(call: UCallExpression) = call.valueArguments.firstOrNull()?.let(::evaluate)

    private fun evalAsArray(call: UCallExpression): Any? =
        (call.getExpressionType() as? PsiArrayType)
            ?.deepComponentType
            ?.takeUnless { it is PsiArrayType }
            ?.let { componentType -> evalAsArray(call.valueArguments, componentType, ::evaluate) }

    private fun <E> evalAsArray(elems: List<E>, elemType: PsiType, eval: (E) -> Any?): Any? = when {
        // avoid large initializers
        elems.size > 40 -> freshArray(elemType, elems.size, 1)
        else -> elems.map { arg ->
            eval(arg).also { if (!allowUnknown && it == null) return null /* Inconclusive */ }
        }.reifiedAsArray(elemType)
    }

    class LastAssignmentFinder(
        private val variable: PsiVariable,
        private val endAt: UElement,
        private val constantEvaluator: ConstantEvaluator?,
        private var variableLevel: Int
    ) : AbstractUastVisitor() {
        private var isDone = false
        private var currentLevel = 0
        var lastAssignment: UElement? = UastFacade.getInitializerBody(variable)
            private set
        var currentValue: Any? = lastAssignment?.let { constantEvaluator?.evaluate(it) }
            private set

        override fun visitElement(node: UElement): Boolean {
            if (node.hasLevel()) {
                currentLevel++
            }
            if (node == endAt) {
                isDone = true
            }
            return isDone || super.visitElement(node)
        }

        override fun visitVariable(node: UVariable): Boolean {
            if (variableLevel < 0 && node.psi.isEquivalentTo(variable)) {
                variableLevel = currentLevel
            }
            return super.visitVariable(node)
        }

        override fun afterVisitBinaryExpression(node: UBinaryExpression) {
            if (!isDone && node.operator is UastBinaryOperator.AssignOperator && variableLevel >= 0) {
                if (variable != (node.leftOperand as? UResolvable)?.resolve()) {
                    return
                }
                lastAssignment = node.rightOperand

                // Last assigned value cannot be determined if we see an assignment inside
                // some conditional or loop statement.
                if (currentLevel >= variableLevel + 1) {
                    currentValue = null
                    return
                } else {
                    currentValue = constantEvaluator?.evaluate(node.rightOperand)
                }
            }
            super.afterVisitBinaryExpression(node)
        }

        override fun afterVisitElement(node: UElement) {
            if (node.hasLevel()) {
                currentLevel--
            }
            super.afterVisitElement(node)
        }

        /**
         * Special marker value from [findLastValue] to indicate that a node was assigned to, but
         * the value is unknown
         */
        internal object LastAssignmentValueUnknown : Any()

        private fun UElement.hasLevel() =
            this !is UBlockExpression && this !is UDeclarationsExpression && this !is UParenthesizedExpression
    }

    /**
     * Evaluates the given node and returns the constant value it resolves to, if any
     *
     * @param node the node to compute the constant value for
     * @return the corresponding constant value - a String, an Integer, a Float, and so on
     */
    fun evaluate(node: PsiElement?): Any? = when (node) {
        null -> null
        is PsiLiteral ->
            node.value
                ?: (node as? KtLightPsiLiteral)?.kotlinOrigin?.let { origin ->
                (convertElement(origin, null, UExpression::class.java) as? UExpression)?.evaluate()
            }
        is PsiPrefixExpression -> evaluate(node.operand)?.let { operand ->
            when (node.operationTokenType) {
                JavaTokenType.EXCL -> operand.tryOn(Boolean::not)
                JavaTokenType.PLUS -> operand
                JavaTokenType.TILDE -> operand.tryInv()
                JavaTokenType.MINUS -> operand.tryUnaryMinus()
                else -> null
            }
        }
        is PsiConditionalExpression -> when (evaluate(node.condition)) {
            true -> node.thenExpression?.let(::evaluate)
            false -> node.elseExpression?.let(::evaluate)
            else -> null
        }
        is PsiParenthesizedExpression -> node.expression?.let(::evaluate)
        is PsiPolyadicExpression -> ArgList(node.operands.map(::evaluate)).let { operands ->
            when (node.operationTokenType) {
                JavaTokenType.OROR -> operands.logicalOr()
                JavaTokenType.ANDAND -> operands.logicalAnd()
                JavaTokenType.EQEQ -> operands.ifAll<Any>()?.isOrdered(Any::equals)
                JavaTokenType.NE -> operands.tryOn(Any::notEquals)
                JavaTokenType.OR -> operands.bitwiseOr()
                JavaTokenType.XOR -> operands.bitwiseXor()
                JavaTokenType.AND -> operands.bitwiseAnd()
                JavaTokenType.GT -> operands.isOrdered(Double::gt, Long::gt)
                JavaTokenType.GE -> operands.isOrdered(Double::ge, Long::ge)
                JavaTokenType.LT -> operands.isOrdered(Double::lt, Long::lt)
                JavaTokenType.LE -> operands.isOrdered(Double::le, Long::le)
                JavaTokenType.LTLT -> operands.shl()
                JavaTokenType.GTGT -> operands.shr()
                JavaTokenType.GTGTGT -> operands.ushr()
                JavaTokenType.PLUS -> operands.plus(allowUnknown)
                JavaTokenType.MINUS -> operands.minus()
                JavaTokenType.ASTERISK -> operands.times()
                JavaTokenType.DIV -> operands.div()
                JavaTokenType.PERC -> operands.mod()
                else -> null
            }
        }
        is PsiTypeCastExpression -> evaluate(node.operand).let { v -> node.castType?.type?.let(v::tryToNum) ?: v }
        is PsiReference -> when (val resolved = (node as PsiReference).resolve()) {
            is PsiField -> when (resolved.name) {
                // It's an array.length expression
                "length" -> node.tryOn(PsiReferenceExpression::getQualifierExpression)
                    ?.takeIf(PsiExpression::getType then { it is PsiArrayType })
                    ?.let(::evaluate)
                    ?.let(::getArraySize)
                    ?.takeUnless { it == -1 }
                else -> resolved.computeConstantValue()
                    ?: resolved.getAllowedInitializer()?.let(::evaluate)
            }
            // TODO: Clamp value as is done for UAST?
            is PsiLocalVariable -> findLastAssignment(node, resolved)?.let(::evaluate)
            else -> null
        }
        is PsiNewExpression -> when (val type = node.type) {
            is PsiArrayType -> when (val initializer = node.arrayInitializer) {
                null -> node.arrayDimensions.firstOrNull()?.let(::evaluate)?.tryOn(Number::toInt)?.let { size ->
                    // something like "new byte[3]" but with no initializer.
                    // Look up the size and only if small, use it. E.g. if it was byte[3]
                    // we return a byte[3] array, but if it's say byte[1024*1024] we don't
                    // want to do that.
                    freshArray(type.deepComponentType, size, type.getArrayDimensions())
                }
                else -> evalAsArray(initializer.initializers.asList(), type.deepComponentType, ::evaluate)
            }
            else -> null
        }
        is KtLiteralStringTemplateEntry -> node.getText()
        is KtStringTemplateExpression -> node.entries.map { entry ->
            when (entry) {
                is KtLiteralStringTemplateEntry -> entry.text
                else -> (evaluate(entry.expression) as? String)
            }
        }.takeIf { it.isNotEmpty() }?.joinToString(separator = "")
        // If we resolve to a "val" in Kotlin, if it's not a const val but in reality is a val
        // (because it has a constant expression and no getters and setters
        // and is not a var), then compute its value anyway.
        is KtLightMethod -> valueFromProperty(node.kotlinOrigin)
        is KtLightField -> valueFromProperty(node.kotlinOrigin)
        is KtProperty -> valueFromProperty(node)
        else -> null
        // TODO: Check for MethodInvocation and perform some common operations -
        // Math.* methods, String utility methods like notNullize, etc
    }

    private fun valueFromProperty(origin: KtDeclaration?): Any? = when {
        (origin is KtProperty) &&
            (
                allowFieldInitializers ||
                    // Property with no custom getter or setter? If it has an initializer
                    // it might be
                    // No setter: might be a constant not declared as such
                    (!origin.isVar && origin.getter == null && origin.setter == null)
                ) ->
            origin.initializer?.let(::evaluate)
        else -> null
    }

    private fun PsiVariable.getAllowedInitializer() = initializer?.takeIf {
        allowFieldInitializers ||
            (hasModifierProperty(PsiModifier.STATIC) && hasModifierProperty(PsiModifier.FINAL))
    }

    sealed class ArrayReference {
        abstract val size: Int
        abstract val dimensions: Int
        protected abstract val className: String
        private data class ByClass(private val type: Class<*>, override val size: Int, override val dimensions: Int) : ArrayReference() {
            override val className get() = type.toString()
        }
        private data class ByName(override val className: String, override val size: Int, override val dimensions: Int) : ArrayReference()

        override fun toString(): String = StringBuilder("Array Reference: $className").let { sb ->
            repeat(dimensions) { sb.append("[]") }
            sb.append("[$size]")
            sb.toString()
        }

        companion object {
            @JvmStatic
            fun of(klass: Class<*>, size: Int, dimensions: Int): ArrayReference = ByClass(klass, size, dimensions)
            @JvmStatic
            fun of(name: String, size: Int, dimensions: Int): ArrayReference = ByName(name, size, dimensions)
        }
    }

    companion object {
        private data class PrimArrayType(val constructorName: String, val varargConstructorName: String, val type: PsiPrimitiveType)
        private val kotlinPrimArrayTypes = listOf(
            PrimArrayType("ByteArray", "byteArrayOf", PsiPrimitiveType.BYTE),
            PrimArrayType("CharArray", "charArrayOf", PsiPrimitiveType.CHAR),
            PrimArrayType("ShortArray", "shortArrayOf", PsiPrimitiveType.SHORT),
            PrimArrayType("IntArray", "intArrayOf", PsiPrimitiveType.INT),
            PrimArrayType("LongArray", "longArrayOf", PsiPrimitiveType.LONG),
            PrimArrayType("FloatArray", "floatArrayOf", PsiPrimitiveType.FLOAT),
            PrimArrayType("DoubleArray", "doubleArrayOf", PsiPrimitiveType.DOUBLE),
            PrimArrayType("BooleanArray", "booleanArrayOf", PsiPrimitiveType.BOOLEAN)
        )
        private val kotlinPrimArrayFixedArgConstructors = kotlinPrimArrayTypes.map(PrimArrayType::constructorName)
        private val kotlinPrimArrayVarargConstructors = kotlinPrimArrayTypes.map(PrimArrayType::varargConstructorName)
        private val kotlinPrimArrayTypeByConstructor =
            kotlinPrimArrayTypes.associate { (k, _, t) -> k to t } +
                kotlinPrimArrayTypes.associate { (_, k, t) -> k to t }

        fun getArraySize(array: Any?): Int = when (array) {
            is ArrayReference -> array.size
            is IntArray -> array.size
            is LongArray -> array.size
            is FloatArray -> array.size
            is DoubleArray -> array.size
            is CharArray -> array.size
            is ByteArray -> array.size
            is ShortArray -> array.size
            is Array<*> -> array.size
            else -> -1
        }

        private fun surroundedByVariableCheck(node: UElement?, variable: PsiVariable): Boolean {
            // See if it looks like the value has been clamped locally, e.g.
            tailrec fun check(curr: UIfExpression?): Boolean = when {
                curr == null -> false
                // variable is referenced surrounding this reference; don't
                // take the variable initializer since the value may have been
                // value checked for some other later assigned value
                // ...but only if it's not the condition!
                references(curr.condition, variable) && !node!!.isUastChildOf(curr.condition, false) -> true
                else -> check(curr.getParentOfType(UIfExpression::class.java))
            }
            return check(node?.getParentOfType(UIfExpression::class.java))
        }

        /** Returns true if the given variable is referenced from within the given element  */
        private fun references(element: UExpression, variable: PsiVariable): Boolean {
            val found = AtomicBoolean()
            element.accept(
                object : AbstractUastVisitor() {
                    override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression): Boolean {
                        if (variable == node.resolve()) {
                            found.set(true)
                        }
                        return super.visitSimpleNameReferenceExpression(node)
                    }
                })
            return found.get()
        }

        /** Returns true if the node is pointing to an array literal  */
        tailrec fun isArrayLiteral(node: PsiElement?): Boolean = when (node) {
            is PsiReference -> when (val resolved = (node as PsiReference).resolve()) {
                is PsiField -> isArrayLiteral(resolved.initializer)
                is PsiLocalVariable -> isArrayLiteral(findLastAssignment(node, resolved))
                else -> false
            }
            is PsiNewExpression -> node.arrayInitializer != null || node.type is PsiArrayType
            is PsiParenthesizedExpression -> isArrayLiteral(node.expression)
            is PsiTypeCastExpression -> isArrayLiteral(node.operand)
            else -> false
        }

        /** Returns true if the node is pointing to an array literal  */
        tailrec fun isArrayLiteral(node: UElement?): Boolean = when {
            node == null -> false
            node is UReferenceExpression -> when (val resolved = node.resolve()) {
                is PsiVariable -> isArrayLiteral(findLastAssignment(resolved, node))
                else -> false
            }
            node.isNewArrayWithDimensions() -> true
            node.isNewArrayWithInitializer() -> true
            node is UParenthesizedExpression -> isArrayLiteral(node.expression)
            node.isTypeCast() -> isArrayLiteral((node as UBinaryExpressionWithType).operand)
            else -> false
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
              */
            return ConstantEvaluator().evaluate(node)
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
        fun evaluate(context: JavaContext?, element: UElement): Any? = when (element) {
            is ULiteralExpression -> element.value
            else -> ConstantEvaluator().evaluate(element)
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
        ): String? = runEvaluator(allowUnknown, node, ConstantEvaluator::evaluate)

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
        @JvmStatic
        @JvmOverloads
        fun findLastAssignment(
            usage: PsiElement,
            variable: PsiVariable,
            allowNonConst: Boolean = false
        ): PsiExpression? = variable.name?.let { targetName ->
            data class Exact<T>(val value: T) // `Exact(null)` means definitely don't know, and stop looking further

            fun check(stm: PsiStatement): Exact<PsiExpression?>? = when (stm) {
                is PsiDeclarationStatement -> variable.initializer.takeIf { variable in stm.declaredElements }?.let(::Exact)
                is PsiExpressionStatement -> (stm.expression as? PsiAssignmentExpression)?.let { expression ->
                    (expression.lExpression as? PsiReferenceExpression)?.let { lhs ->
                        expression.rExpression.takeIf { targetName == lhs.referenceName && lhs.qualifier == null }
                    }
                }?.let(::Exact)
                is PsiIfStatement -> {
                    fun find(stm: PsiBlockStatement): Exact<PsiExpression?>? = stm.codeBlock.statements.lastOrNull()?.let { last ->
                        findLastAssignment(last, variable, true)?.let { asn -> Exact(asn.takeIf { allowNonConst }) }
                    }
                    stm.thenBranch.tryOn(::find) ?: stm.elseBranch.tryOn(::find)
                }
                else -> null
            }

            val statement = PsiTreeUtil.getParentOfType(usage, PsiStatement::class.java, false)
            val start = when {
                // If allowNonConst is true, it means the search starts from the last
                // statement in an if/else
                // block, so don't skip the passed-in statement
                allowNonConst -> statement
                else -> PsiTreeUtil.getPrevSiblingOfType(statement, PsiStatement::class.java)
            }

            // Walk backwards through assignments to find the most recent initialization
            // of this variable
            generateSequence(start) { PsiTreeUtil.getPrevSiblingOfType(it, PsiStatement::class.java) }
                .map(::check)
                .filterIsInstance<Exact<PsiExpression?>>()
                .firstOrNull()?.value
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
        ): String? = runEvaluator(allowUnknown, element, ConstantEvaluator::evaluate)

        private inline fun <X, reified T> runEvaluator(
            allowUnknown: Boolean,
            expr: X,
            eval: ConstantEvaluator.(X) -> Any?
        ): T? =
            with(ConstantEvaluator().apply { if (allowUnknown) allowUnknowns() }) {
                eval(expr) as? T
            }
    }
}

private fun <T : Comparable<T>> T.gt(that: T) = this > that
private fun <T : Comparable<T>> T.ge(that: T) = this >= that
private fun <T : Comparable<T>> T.lt(that: T) = this < that
private fun <T : Comparable<T>> T.le(that: T) = this <= that
private fun <T> T.notEquals(that: T) = this != that

private inline fun <X, reified T : X, A : Any> X.tryOn(crossinline f: (T) -> A?): A? = (this as? T)?.let(f)

@JvmInline
private value class ArgList<out X>(val values: List<X>) {
    inline fun <reified T0, reified T1, A : Any> tryOn(f: (T0, T1) -> A?): A? = when (values.size) {
        2 -> (values[0] as? T0)?.let { x0 -> (values[1] as? T1)?.let { x1 -> f(x0, x1) } }
        else -> null
    }

    inline fun <reified T> ifAny(): ArgList<X>? = takeIf { values.any { it is T } }
    inline fun <reified T> ifAll(): ArgList<T>? = (takeIf { values.all { it is T } })?.let { this as ArgList<T> }
    inline fun ifAny(p: (X) -> Boolean): ArgList<X>? = takeIf { values.any(p) }
    inline fun ifAll(p: (X) -> Boolean): ArgList<X>? = takeIf { values.all(p) }
    inline fun ifFirst(p: (X) -> Boolean): ArgList<X>? = takeIf { values.firstOrNull()?.let(p) == true }
    inline fun <T> split(onSplit: (X, ArgList<X>) -> T): T? = when {
        values.isEmpty() -> null
        else -> onSplit(values[0], ArgList(values.subList(1, values.size)))
    }
    inline fun <A> join(f: (List<X>) -> A): A = f(values)
    fun <T : Any> reduceOn(onElem: (X) -> T, f: (T, T) -> T): T? = values.asSequence().map(onElem).reduceOrNull(f)
    fun <R, T> foldOn(onElem: (X) -> T, init: R, op: (R, T) -> R): R = values.asSequence().map(onElem).fold(init, op)
    inline fun isOrdered(ordered: (X, X) -> Boolean) =
        values.asSequence().zipWithNext().all { (l, r) -> ordered(l, r) }
    inline fun <T> isOrderedOn(noinline prop: (X) -> T, ordered: (T, T) -> Boolean) =
        values.asSequence().map(prop).zipWithNext().all { (l, r) -> ordered(l, r) }
    fun <T> const(x: T): T = x
}

private fun <X> ArgList<X>.reduce(op: (X, X) -> X): X? = values.reduceOrNull(op)

// Assumption: due to Java's infix syntax, 0-arg polyadic expression is not a thing,
// so we don't need to worry about each operation's identity element
private fun ArgList<Any?>.reduceAsNumbers(
    opDouble: (Double, Double) -> Double,
    opFloat: (Float, Float) -> Float,
    opLong: (Long, Long) -> Long,
    opInt: (Int, Int) -> Int
): Number? =
    ifAll<Number>()?.let {
        it.ifAny<Double>()?.reduceOn(Number::toDouble, opDouble)
            ?: it.ifAny<Float>()?.reduceOn(Number::toFloat, opFloat)
            ?: it.ifAny<Long>()?.reduceOn(Number::toLong, opLong)
            ?: it.ifAny<Int>()?.reduceOn(Number::toInt, opInt)
    }

private fun ArgList<Any?>.reduceAsInts(
    opLong: (Long, Long) -> Long,
    opInt: (Int, Int) -> Int
): Number? =
    ifAll<Number>()?.let {
        it.ifAny<Long>()?.reduceOn(Number::toLong, opLong)
            ?: it.ifAny<Int>()?.reduceOn(Number::toInt, opInt)
    }

private inline fun ArgList<Any?>.isOrdered(
    onDouble: (Double, Double) -> Boolean,
    onLong: (Long, Long) -> Boolean
) =
    ifAll<Number>()?.let {
        (it.ifAny<Float>() ?: it.ifAny<Double>())?.isOrderedOn(Number::toDouble, onDouble)
            ?: (it.ifAny<Int>() ?: it.ifAny<Long>())?.isOrderedOn(Number::toLong, onLong)
    }

private fun ArgList<Any?>.logicalOr() = ifAll<Boolean>()?.reduce(Boolean::or) ?: ifAny(true::equals)?.const(true)
private fun ArgList<Any?>.logicalAnd() = ifAll<Boolean>()?.reduce(Boolean::and) ?: ifAny(false::equals)?.const(false)

private fun Any?.tryInv() = when (this) {
    is Int -> inv()
    is Long -> inv()
    is Short -> inv().toInt()
    is Char -> code.inv()
    is Byte -> inv().toInt()
    else -> null
}

private fun Any?.tryUnaryMinus() = when (this) {
    is Int -> -this
    is Long -> -this
    is Double -> -this
    is Float -> -this
    is Short -> -this
    is Char -> -this.code
    is Byte -> -this
    else -> null
}

private fun Any?.tryToNum(type: PsiType) = (this as? Number)?.let { n ->
    when (type) {
        PsiType.FLOAT -> n.toFloat()
        PsiType.DOUBLE -> n.toDouble()
        PsiType.INT -> n.toInt()
        PsiType.LONG -> n.toLong()
        PsiType.SHORT -> n.toShort()
        PsiType.BYTE -> n.toByte()
        else -> this
    }
} ?: this

private fun ArgList<Any?>.plus(allowUnknown: Boolean) =
    reduceAsNumbers(Double::plus, Float::plus, Long::plus, Int::plus)
        ?: ifAny { it is String }?.let { args ->
        when {
            allowUnknown -> args.join { it.asSequence().filterNotNull().joinToString(separator = "") }
            else -> args.ifAll { it is String || it is Char }?.join { it.joinToString(separator = "") }
        }
    }
private fun ArgList<Any?>.times() = reduceAsNumbers(Double::times, Float::times, Long::times, Int::times)
private fun ArgList<Any?>.minus() = reduceAsNumbers(Double::minus, Float::minus, Long::minus, Int::minus)
private fun ArgList<Any?>.bitwiseOr() = logicalOr() ?: reduceAsInts(Long::or, Int::or)
private fun ArgList<Any?>.bitwiseAnd() = logicalAnd() ?: reduceAsInts(Long::and, Int::and)
private fun ArgList<Any?>.bitwiseXor() =
    ifAll<Boolean>()?.reduce(Boolean::xor)
        ?: reduceAsInts(Long::xor, Int::xor)
private fun ArgList<Any?>.div() =
    ifFirst { it == 0 }?.const(0)
        ?: ifFirst { it == 0L }?.const(0L)
        ?: ifAll { it != 0 && it != 0L }?.reduceAsNumbers(Double::div, Float::div, Long::div, Int::div)
private fun ArgList<Any?>.mod() =
    ifFirst { it == 0 }?.const(0)
        ?: ifFirst { it == 0L }?.const(0L)
        ?: ifAll { it != 0 && it != 0L }?.reduceAsNumbers(Double::mod, Float::mod, Long::mod, Int::mod)
private fun ArgList<Any?>.shl() = shift(Long::shl, Int::shl)
private fun ArgList<Any?>.shr() = shift(Long::shr, Int::shr)
private fun ArgList<Any?>.ushr() = shift(Long::ushr, Int::ushr)
private fun ArgList<Any?>.shift(onLong: (Long, Int) -> Long, onInt: (Int, Int) -> Int) =
    ifAll<Number>()?.split { n, ns ->
        when (n) {
            is Long -> ns.foldOn(Number::toInt, n, onLong)
            is Int -> ns.foldOn(Number::toInt, n, onInt)
            else -> null
        }
    }

private fun isType(type: PsiType, name: String) = (type as? PsiClassType)?.resolve()?.qualifiedName == name

private fun List<Any?>.reifiedAsArray(elemType: PsiType): Any? = when {
    elemType == PsiType.BOOLEAN -> BooleanArray(size) { this[it] as? Boolean ?: false }
    elemType == PsiType.CHAR -> CharArray(size) { this[it] as? Char ?: 0.toChar() }
    elemType == PsiType.BYTE -> ByteArray(size) { this[it] as? Byte ?: 0.toByte() }
    elemType == PsiType.DOUBLE -> DoubleArray(size) { this[it] as? Double ?: 0.0 }
    elemType == PsiType.FLOAT -> FloatArray(size) { this[it] as? Float ?: 0F }
    elemType == PsiType.INT -> IntArray(size) { this[it] as? Int ?: 0 }
    elemType == PsiType.SHORT -> ShortArray(size) { this[it] as? Short ?: 0.toShort() }
    elemType == PsiType.LONG -> LongArray(size) { this[it] as? Long ?: 0L }
    isType(elemType, TYPE_OBJECT) -> Array(size) { this[it] }
    isType(elemType, TYPE_STRING) -> Array(size) { this[it] as? String }
    else -> {
        tailrec fun widen(src: Class<*>, tgt: Class<*>): Class<*> =
            if (src.isAssignableFrom(tgt)) src else widen(src.superclass, tgt)
        asSequence().mapNotNull { it?.javaClass }.reduceOrNull(::widen)?.let(::reifiedAsArray)
    }
}

private fun List<Any?>.reifiedAsArray(klass: Class<*>): Array<*> =
    toArray { n -> java.lang.reflect.Array.newInstance(klass, n) as Array<*> }

private inline fun <reified A, X> Any.asArray(crossinline indices: (A) -> IntRange, crossinline get: (A, Int) -> X): ((Int) -> X?)? =
    (this as? A)?.let { { if (it in indices(this)) get(this, it) else null } }

/**
 * When evaluating expressions that resolve to arrays, this is the largest array size we'll
 * initialize; for larger arrays we'll return a [ArrayReference] instead
 */
private const val LARGEST_LITERAL_ARRAY = 12

private fun freshArray(type: PsiType, size: Int, dimensions: Int): Any = when {
    size <= LARGEST_LITERAL_ARRAY -> when (type) {
        PsiType.BYTE -> ByteArray(size)
        PsiType.BOOLEAN -> BooleanArray(size)
        PsiType.INT -> IntArray(size)
        PsiType.LONG -> LongArray(size)
        PsiType.CHAR -> CharArray(size)
        PsiType.FLOAT -> FloatArray(size)
        PsiType.DOUBLE -> DoubleArray(size)
        PsiType.SHORT -> ShortArray(size)
        else -> when (val className = type.canonicalText) {
            TYPE_STRING -> arrayOfNulls<String>(size)
            TYPE_OBJECT -> arrayOfNulls<Any>(size)
            else -> ArrayReference.of(className, size, dimensions)
        }
    }
    else -> when (type) {
        PsiType.BYTE -> ArrayReference.of(java.lang.Byte.TYPE, size, dimensions)
        PsiType.BOOLEAN -> ArrayReference.of(java.lang.Boolean.TYPE, size, dimensions)
        PsiType.INT -> ArrayReference.of(Integer.TYPE, size, dimensions)
        PsiType.LONG -> ArrayReference.of(java.lang.Long.TYPE, size, dimensions)
        PsiType.CHAR -> ArrayReference.of(Character.TYPE, size, dimensions)
        PsiType.FLOAT -> ArrayReference.of(java.lang.Float.TYPE, size, dimensions)
        PsiType.DOUBLE -> ArrayReference.of(java.lang.Double.TYPE, size, dimensions)
        PsiType.SHORT -> ArrayReference.of(java.lang.Short.TYPE, size, dimensions)
        else -> when (val className = type.canonicalText) {
            TYPE_STRING -> ArrayReference.of(String::class.java, size, dimensions)
            TYPE_OBJECT -> ArrayReference.of(Any::class.java, size, dimensions)
            else -> ArrayReference.of(className, size, dimensions)
        }
    }
}

private infix fun <A, B, C> ((A) -> B).then(that: (B) -> C): (A) -> C = { that(this(it)) }
