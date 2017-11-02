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

package com.android.tools.lint.checks

import com.android.SdkConstants.INT_DEF_ANNOTATION
import com.android.SdkConstants.STRING_DEF_ANNOTATION
import com.android.SdkConstants.TYPE_DEF_FLAG_ATTRIBUTE
import com.android.tools.lint.checks.AnnotationDetector.INT_RANGE_ANNOTATION
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.ExternalReferenceExpression
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.UastLintUtils
import com.android.tools.lint.detector.api.UastLintUtils.getAnnotationBooleanValue
import com.android.tools.lint.detector.api.UastLintUtils.getAnnotationValue
import com.android.tools.lint.detector.api.UastLintUtils.isMinusOne
import com.google.common.collect.Lists
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiType
import com.intellij.psi.PsiVariable
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.UPrefixExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.UastPrefixOperator
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.java.JavaUAnnotation
import org.jetbrains.uast.util.isArrayInitializer
import org.jetbrains.uast.util.isNewArrayWithInitializer

class TypedefDetector : AbstractAnnotationDetector(), Detector.UastScanner {
    override fun applicableAnnotations(): List<String> = listOf(
            INT_DEF_ANNOTATION,
            STRING_DEF_ANNOTATION,

            // Such that the annotation is considered relevant by the annotation handler
            // even if the range check itself is disabled
            INT_RANGE_ANNOTATION
    )

    override fun visitAnnotationUsage(
            context: JavaContext,
            argument: UElement,
            annotation: UAnnotation,
            qualifiedName: String,
            method: PsiMethod?,
            annotations: List<UAnnotation>,
            allMemberAnnotations: List<UAnnotation>,
            allClassAnnotations: List<UAnnotation>,
            allPackageAnnotations: List<UAnnotation>) {
        when (qualifiedName) {
            INT_DEF_ANNOTATION -> {
                val flagAttribute = getAnnotationBooleanValue(annotation, TYPE_DEF_FLAG_ATTRIBUTE)
                val flag = flagAttribute != null && flagAttribute
                checkTypeDefConstant(context, annotation, argument, null, flag,
                        annotations)
            }
            STRING_DEF_ANNOTATION -> {
                checkTypeDefConstant(context, annotation, argument, null, false,
                        annotations)
            }
            INT_RANGE_ANNOTATION -> {} // deliberate no-op
        }
    }

    private fun checkTypeDefConstant(
            context: JavaContext,
            annotation: UAnnotation,
            argument: UElement?,
            errorNode: UElement?,
            flag: Boolean,
            allAnnotations: List<UAnnotation>) {
        if (argument == null) {
            return
        }
        if (argument is ULiteralExpression) {
            val value = argument.value
            if (value == null) {
                // Accepted for @StringDef

                return
            } else if (value is String) {
                checkTypeDefConstant(context, annotation, argument, errorNode, false, value,
                        allAnnotations)
            } else if (value is Int || value is Long) {
                val v = value as? Long ?: (value as Int).toLong()
                if (flag && v == 0L) {
                    // Accepted for a flag @IntDef
                    return
                }

                checkTypeDefConstant(context, annotation, argument, errorNode, flag, value,
                        allAnnotations)
            }
        } else if (isMinusOne(argument)) {
            // -1 is accepted unconditionally for flags
            if (!flag) {
                reportTypeDef(context, annotation, argument, errorNode, allAnnotations)
            }
        } else if (argument is UPrefixExpression) {
            val expression = argument as UPrefixExpression?
            if (flag) {
                checkTypeDefConstant(context, annotation, expression!!.operand,
                        errorNode, true, allAnnotations)
            } else {
                val operator = expression!!.operator
                if (operator === UastPrefixOperator.BITWISE_NOT) {
                    report(context, TYPE_DEF, expression, context.getLocation(expression),
                            "Flag not allowed here")
                } else if (operator === UastPrefixOperator.UNARY_MINUS) {
                    reportTypeDef(context, annotation, argument, errorNode, allAnnotations)
                }
            }
        } else if (argument is UParenthesizedExpression) {
            val expression = argument.expression
            checkTypeDefConstant(context, annotation, expression, errorNode, flag, allAnnotations)
        } else if (argument is UIfExpression) {
            // If it's ?: then check both the if and else clauses
            val expression = argument as UIfExpression?
            if (expression!!.thenExpression != null) {
                checkTypeDefConstant(context,
                        annotation,
                        expression.thenExpression,
                        errorNode,
                        flag,
                        allAnnotations)
            }
            if (expression.elseExpression != null) {
                checkTypeDefConstant(context,
                        annotation,
                        expression.elseExpression,
                        errorNode,
                        flag,
                        allAnnotations)
            }

        } else if (argument is UPolyadicExpression) {
            if (flag) {
                // Allow &'ing with masks
                if (argument.operator === UastBinaryOperator.BITWISE_AND) {
                    for (operand in argument.operands) {
                        if (operand is UReferenceExpression) {
                            val resolvedName = operand.resolvedName
                            if (resolvedName != null && resolvedName.contains("mask", true)) {
                                return
                            }
                        }
                    }
                }

                for (operand in argument.operands) {
                    checkTypeDefConstant(context, annotation, operand, errorNode, true,
                            allAnnotations)
                }
            } else {
                val operator = argument.operator
                if (operator === UastBinaryOperator.BITWISE_AND
                        || operator === UastBinaryOperator.BITWISE_OR
                        || operator === UastBinaryOperator.BITWISE_XOR) {
                    report(context, TYPE_DEF, argument, context.getLocation(argument),
                            "Flag not allowed here")
                }
            }
        } else if (argument is UReferenceExpression) {
            val resolved = argument.resolve()
            if (resolved is PsiVariable) {
                val variable = resolved as PsiVariable?
                if (variable!!.type is PsiArrayType) {
                    // Allow checking the initializer here even if the field itself
                    // isn't final or static; check that the individual values are okay
                    checkTypeDefConstant(context, annotation, argument,
                            errorNode ?: argument,
                            flag, resolved, allAnnotations)
                    return
                }

                // If it's a constant (static/final) check that it's one of the allowed ones
                if (variable.hasModifierProperty(PsiModifier.STATIC) && variable.hasModifierProperty(
                        PsiModifier.FINAL)) {
                    checkTypeDefConstant(context, annotation, argument,
                            errorNode ?: argument,
                            flag, resolved, allAnnotations)
                } else {
                    val lastAssignment = UastLintUtils.findLastAssignment(variable, argument)

                    if (lastAssignment != null) {
                        checkTypeDefConstant(context, annotation,
                                lastAssignment,
                                errorNode ?: argument, flag,
                                allAnnotations)
                    }
                }
            }
        } else if (argument.isNewArrayWithInitializer() || argument.isArrayInitializer()) {
            val arrayInitializer = argument as UCallExpression?
            var type = arrayInitializer!!.getExpressionType()
            if (type != null) {
                type = type.deepComponentType
            }
            if (PsiType.INT == type || PsiType.LONG == type) {
                for (expression in arrayInitializer.valueArguments) {
                    checkTypeDefConstant(context, annotation, expression, errorNode, flag,
                            allAnnotations)
                }
            }
        }
    }

    private fun checkTypeDefConstant(context: JavaContext,
            annotation: UAnnotation, argument: UElement,
            errorNode: UElement?, flag: Boolean, value: Any,
            allAnnotations: List<UAnnotation>) {
        val rangeAnnotation = RangeDetector.findIntRange(allAnnotations)
        if (rangeAnnotation != null && value !is PsiField) {
            // Allow @IntRange on this number, but only if it's a literal, not if it's some
            // other (unrelated) constant)
            if (RangeDetector.getIntRangeError(context, rangeAnnotation, argument) == null) {
                return
            }
        }

        val allowed = getAnnotationValue(annotation) ?: return

        if (allowed.isArrayInitializer()) {
            // See if we're passing in a variable which itself has been annotated with
            // a typedef annotation; if so, make sure that the typedef constants are the
            // same, or a subset of the allowed constants
            if (argument is UReferenceExpression) {
                val resolvedArgument = argument.resolve()
                if (resolvedArgument is PsiModifierListOwner) {
                    val evaluator = context.evaluator
                    val annotations = evaluator.getAllAnnotations(resolvedArgument, true)
                    for (a in evaluator.filterRelevantAnnotations(annotations)) {
                        val qualifiedName = a.qualifiedName
                        if (INT_DEF_ANNOTATION == qualifiedName ||
                                STRING_DEF_ANNOTATION == qualifiedName) {
                            val paramValues = getAnnotationValue(JavaUAnnotation.wrap(a))
                            if (paramValues != null) {
                                if (paramValues == allowed) {
                                    return
                                }

                                // Superset?
                                val param = getResolvedValues(paramValues, argument)
                                val all = getResolvedValues(allowed, argument)
                                if (all.containsAll(param)) {
                                    return
                                }
                            }
                        }
                    }
                }
            }

            val initializerExpression = allowed as UCallExpression
            val initializers = initializerExpression.valueArguments
            var psiValue: PsiElement? = null
            if (value is PsiElement) {
                psiValue = value
            }

            for (expression in initializers) {
                if (expression is ULiteralExpression) {
                    if (value == expression.value) {
                        return
                    }
                } else if (psiValue == null) {
                    // We're checking here such that we can assume psiValue is not null
                    // below

                    continue
                } else if (expression is ExternalReferenceExpression) {
                    val resolved = UastLintUtils.resolve(
                            expression as ExternalReferenceExpression, argument)
                    if (resolved != null && resolved.isEquivalentTo(psiValue)) {
                        return
                    }
                } else if (expression is UReferenceExpression) {
                    val resolved = expression.resolve()
                    if (resolved != null && resolved.isEquivalentTo(psiValue)) {
                        return
                    }
                }
            }

            // Check field initializers provided it's not a class field, in which case
            // we'd be reading out literal values which we don't want to do)
            if (value is PsiField && rangeAnnotation == null) {
                val initializer = context.uastContext.getInitializerBody(value)
                if (initializer != null) {
                    checkTypeDefConstant(context, annotation, initializer, errorNode,
                            flag, allAnnotations)
                    return
                }
            }

            if (allowed is PsiCompiledElement || annotation.psi is PsiCompiledElement) {
                // If we for some reason have a compiled annotation, don't flag the error
                // since we can't represent IntDef data on these annotations
                return
            }

            reportTypeDef(context, argument, errorNode, flag,
                    initializers, allAnnotations)
        }
    }

    private fun getResolvedValues(allowed: UExpression, context: UElement): List<Any> {
        val result = Lists.newArrayList<Any>()
        if (allowed.isArrayInitializer()) {
            val initializerExpression = allowed as UCallExpression
            val initializers = initializerExpression.valueArguments
            for (expression in initializers) {
                if (expression is ULiteralExpression) {
                    val value = expression.value
                    if (value != null) {
                        result.add(value)
                    }
                } else if (expression is ExternalReferenceExpression) {
                    val resolved = UastLintUtils.resolve(
                            expression as ExternalReferenceExpression, context)
                    if (resolved != null) {
                        result.add(resolved)
                    }
                } else if (expression is UReferenceExpression) {
                    val resolved = expression.resolve()
                    if (resolved != null) {
                        result.add(resolved)
                    }
                }
            }
        }
        // TODO -- worry about other types?

        return result
    }

    private fun reportTypeDef(
            context: JavaContext,
            annotation: UAnnotation,
            argument: UElement,
            errorNode: UElement?,
            allAnnotations: List<UAnnotation>) {
        val allowed = getAnnotationValue(annotation)
        if (allowed != null && allowed.isArrayInitializer()) {
            val initializerExpression = allowed as UCallExpression
            val initializers = initializerExpression.valueArguments
            reportTypeDef(context, argument, errorNode, false, initializers, allAnnotations)
        }
    }

    private fun reportTypeDef(
            context: JavaContext,
            node: UElement,
            errorNode: UElement?, flag: Boolean,
            allowedValues: List<UExpression>,
            allAnnotations: List<UAnnotation>) {
        // Allow "0" as initial value in variable expressions
        if (UastLintUtils.isZero(node)) {
            val declaration = node.getParentOfType<UVariable>(UVariable::class.java, true)
            if (declaration != null && node == declaration.uastInitializer) {
                return
            }
        }

        val values = listAllowedValues(node, allowedValues)
        var message = if (flag) {
            "Must be one or more of: " + values
        } else {
            "Must be one of: " + values
        }

        val rangeAnnotation = RangeDetector.findIntRange(allAnnotations)
        if (rangeAnnotation != null) {
            // Allow @IntRange on this number
            val rangeError = RangeDetector.getIntRangeError(context, rangeAnnotation, node)
            if (rangeError != null && !rangeError.isEmpty()) {
                message += " or " + Character.toLowerCase(rangeError[0]) + rangeError.substring(1)
            }
        }

        val locationNode = errorNode ?: node
        report(context, TYPE_DEF, locationNode, context.getLocation(locationNode), message)
    }

    private fun listAllowedValues(context: UElement,
            allowedValues: List<UExpression>): String {
        val sb = StringBuilder()
        for (allowedValue in allowedValues) {
            var s: String? = null
            var resolved: PsiElement? = null
            if (allowedValue is ExternalReferenceExpression) {
                resolved = UastLintUtils.resolve(
                        allowedValue as ExternalReferenceExpression, context)
            } else if (allowedValue is UReferenceExpression) {
                resolved = allowedValue.resolve()
            }

            if (resolved is PsiField) {
                val field = resolved as PsiField?
                val containingClassName = (if (field!!.containingClass != null)
                    field.containingClass!!.name
                else
                    null) ?: continue
                s = containingClassName + "." + field.name
            }
            if (s == null) {
                s = allowedValue.asSourceString()
            }
            if (sb.isNotEmpty()) {
                sb.append(", ")
            }
            sb.append(s)
        }
        return sb.toString()
    }

    companion object {
        private val IMPLEMENTATION = Implementation(TypedefDetector::class.java,
                Scope.JAVA_FILE_SCOPE)

        /** Passing the wrong constant to an int or String method  */
        @JvmField
        val TYPE_DEF = Issue.create(
                "WrongConstant",
                "Incorrect constant",

                "Ensures that when parameter in a method only allows a specific set " +
                        "of constants, calls obey those rules.",

                Category.SECURITY,
                6,
                Severity.ERROR,
                IMPLEMENTATION)

        fun findIntDef(annotations: List<UAnnotation>): UAnnotation? {
            for (annotation in annotations) {
                if (INT_DEF_ANNOTATION == annotation.qualifiedName) {
                    return annotation
                }
            }

            return null
        }
    }
}