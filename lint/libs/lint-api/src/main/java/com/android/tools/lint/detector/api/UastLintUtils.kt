/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.SdkConstants.ATTR_VALUE
import com.android.tools.lint.client.api.ResourceReference
import com.android.tools.lint.detector.api.ConstantEvaluator.LastAssignmentFinder.LAST_ASSIGNMENT_VALUE_UNKNOWN
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiVariable
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UDeclaration
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UFile
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UPrefixExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UResolvable
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UUnaryExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.UastPrefixOperator
import org.jetbrains.uast.getContainingUMethod
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.getUastContext
import org.jetbrains.uast.toUElementOfType

class UastLintUtils {
    companion object {
        @JvmStatic
        fun UElement.tryResolveUDeclaration(): UDeclaration? {
            return (this as? UResolvable)?.resolve().toUElementOfType()
        }

        /** Returns the containing file for the given element  */
        @JvmStatic
        fun getContainingFile(
            context: JavaContext,
            element: PsiElement?
        ): PsiFile? {
            if (element == null) {
                return null
            }

            val containingFile = element.containingFile
            return if (containingFile != context.psiFile) {
                getContainingFile(element)
            } else containingFile
        }

        /** Returns the containing file for the given element  */
        @JvmStatic
        fun getPsiFile(file: UFile?): PsiFile? {
            return file?.let { getContainingFile(it.psi) }
        }

        /** Returns the containing file for the given element  */
        @JvmStatic
        fun getContainingFile(element: PsiElement?): PsiFile? {
            if (element == null) {
                return null
            }

            val containingFile = element as? PsiFile ?: element.containingFile

            // In Kotlin files identifiers are sometimes using LightElements that are hosted in
            // a placeholder file, these do not have the right PsiFile as containing elements
            val cls = containingFile.javaClass
            val name = cls.name
            if (name.startsWith(
                "org.jetbrains.kotlin.asJava.classes.KtLightClassForSourceDeclaration"
            )
            ) {
                try {
                    val declaredField = cls.superclass.getDeclaredField("ktFile")
                    declaredField.isAccessible = true
                    val o = declaredField.get(containingFile)
                    if (o is PsiFile) {
                        return o
                    }
                } catch (ignore: Throwable) {
                }
            } else if (name == "org.jetbrains.kotlin.asJava.elements.FakeFileForLightClass") {
                try {
                    val declaredField = cls.getDeclaredField("ktFile")
                    declaredField.isAccessible = true
                    val o = declaredField.get(containingFile)
                    if (o is PsiFile) {
                        return o
                    }
                } catch (ignore: Throwable) {
                }
            }
            return containingFile
        }

        @JvmStatic
        fun getQualifiedName(element: PsiElement): String? = when (element) {
            is PsiClass -> element.qualifiedName
            is PsiMethod ->
                element.containingClass?.let { getQualifiedName(it) }
                    ?.let { "$it.${element.name}" }
            is PsiField ->
                element.containingClass?.let { getQualifiedName(it) }
                    ?.let { "$it.${element.name}" }
            else -> null
        }

        @JvmStatic
        fun resolve(expression: ExternalReferenceExpression, context: UElement): PsiElement? {
            val declaration =
                context.getParentOfType<UDeclaration>(UDeclaration::class.java) ?: return null

            return expression.resolve(declaration.psi)
        }

        @JvmStatic
        fun getClassName(type: PsiClassType): String {
            val psiClass = type.resolve()
            return psiClass?.let { getClassName(it) } ?: type.className
        }

        @JvmStatic
        fun getClassName(psiClass: PsiClass): String {
            val stringBuilder = StringBuilder()
            stringBuilder.append(psiClass.name)

            var currPsiClass = psiClass.containingClass
            while (currPsiClass != null) {
                stringBuilder.insert(0, currPsiClass.name + ".")
                currPsiClass = currPsiClass.containingClass
            }
            return stringBuilder.toString()
        }

        @JvmStatic
        fun findLastAssignment(
            variable: PsiVariable,
            call: UElement
        ): UExpression? {
            var currVariable = variable
            var lastAssignment: UElement? = null

            if (currVariable is UVariable) {
                currVariable = currVariable.psi
            }

            if (!currVariable.hasModifierProperty(PsiModifier.FINAL) && (currVariable is PsiLocalVariable || currVariable is PsiParameter)) {
                val containingFunction = call.getContainingUMethod()
                if (containingFunction != null) {
                    val finder = ConstantEvaluator.LastAssignmentFinder(
                        currVariable, call, null, -1
                    )
                    containingFunction.accept(finder)
                    lastAssignment = finder.lastAssignment
                }
            } else {
                val context = call.getUastContext()
                lastAssignment = context.getInitializerBody(currVariable)
            }

            return if (lastAssignment is UExpression) lastAssignment
            else null
        }

        @JvmStatic
        fun getReferenceName(expression: UReferenceExpression): String? {
            if (expression is USimpleNameReferenceExpression) {
                return expression.identifier
            } else if (expression is UQualifiedReferenceExpression) {
                val selector = expression.selector
                if (selector is USimpleNameReferenceExpression) {
                    return selector.identifier
                }
            }

            return null
        }

        @JvmStatic
        fun findLastValue(
            variable: PsiVariable,
            call: UElement,
            evaluator: ConstantEvaluator
        ): Any? {
            var value: Any? = null

            if (!variable.hasModifierProperty(PsiModifier.FINAL) && (variable is PsiLocalVariable || variable is PsiParameter)) {
                val containingFunction = call.getContainingUMethod()
                if (containingFunction != null) {
                    val body = containingFunction.uastBody
                    if (body != null) {
                        val finder = ConstantEvaluator.LastAssignmentFinder(
                            variable, call, evaluator, 1
                        )
                        body.accept(finder)
                        value = finder.currentValue

                        if (value == null && finder.lastAssignment != null) {
                            // Special return value: variable was assigned but we don't know
                            // the value
                            return LAST_ASSIGNMENT_VALUE_UNKNOWN
                        }
                    }
                }
            } else {
                val initializer = UastFacade.getInitializerBody(variable)
                if (initializer != null) {
                    value = initializer.evaluate()
                }
            }

            return value
        }

        @JvmStatic
        fun toAndroidReferenceViaResolve(element: UElement): ResourceReference? {
            return ResourceReference.get(element)
        }

        @JvmStatic
        fun areIdentifiersEqual(first: UExpression, second: UExpression): Boolean {
            val firstIdentifier = getIdentifier(first)
            val secondIdentifier = getIdentifier(second)
            return (
                firstIdentifier != null &&
                    secondIdentifier != null &&
                    firstIdentifier == secondIdentifier
                )
        }

        @JvmStatic
        fun getIdentifier(expression: UExpression): String? =
            when (expression) {
                is ULiteralExpression -> expression.asRenderString()
                is UQualifiedReferenceExpression -> {
                    val receiverIdentifier = getIdentifier(expression.receiver)
                    val selectorIdentifier = getIdentifier(expression.selector)
                    if (receiverIdentifier == null || selectorIdentifier == null) {
                        null
                    } else "$receiverIdentifier.$selectorIdentifier"
                }
                else -> null
            }

        @JvmStatic
        fun isNumber(argument: UElement): Boolean =
            when (argument) {
                is ULiteralExpression -> argument.value is Number
                is UPrefixExpression -> isNumber(argument.operand)
                else -> false
            }

        @JvmStatic
        fun isZero(argument: UElement): Boolean {
            if (argument is ULiteralExpression) {
                val value = argument.value
                return value is Number && value.toInt() == 0
            }
            return false
        }

        @JvmStatic
        fun isMinusOne(argument: UElement): Boolean {
            return if (argument is UUnaryExpression) {
                val operand = argument.operand
                if (operand is ULiteralExpression && argument.operator === UastPrefixOperator.UNARY_MINUS) {
                    val value = operand.value
                    value is Number && value.toInt() == 1
                } else {
                    false
                }
            } else {
                false
            }
        }

        @JvmStatic
        fun getAnnotationValue(annotation: UAnnotation): UExpression? {
            return annotation.findDeclaredAttributeValue(ATTR_VALUE)
        }

        @JvmStatic
        fun getLongAttribute(
            context: JavaContext,
            annotation: UAnnotation,
            name: String,
            defaultValue: Long
        ): Long {
            return getAnnotationLongValue(annotation, name, defaultValue)
        }

        @JvmStatic
        fun getDoubleAttribute(
            context: JavaContext,
            annotation: UAnnotation,
            name: String,
            defaultValue: Double
        ): Double {
            return getAnnotationDoubleValue(annotation, name, defaultValue)
        }

        @JvmStatic
        fun getBoolean(
            context: JavaContext,
            annotation: UAnnotation,
            name: String,
            defaultValue: Boolean
        ): Boolean {
            return getAnnotationBooleanValue(annotation, name, defaultValue)
        }

        @JvmStatic
        fun getAnnotationBooleanValue(
            annotation: UAnnotation?,
            name: String
        ): Boolean? {
            return AnnotationValuesExtractor
                .getAnnotationValuesExtractor(annotation)
                .getAnnotationBooleanValue(annotation, name)
        }

        @JvmStatic
        fun getAnnotationBooleanValue(
            annotation: UAnnotation?,
            name: String,
            defaultValue: Boolean
        ): Boolean {
            val value = getAnnotationBooleanValue(annotation, name)
            return value ?: defaultValue
        }

        @JvmStatic
        fun getAnnotationLongValue(
            annotation: UAnnotation?,
            name: String
        ): Long? {
            return AnnotationValuesExtractor
                .getAnnotationValuesExtractor(annotation)
                .getAnnotationLongValue(annotation, name)
        }

        @JvmStatic
        fun getAnnotationLongValue(
            annotation: UAnnotation?,
            name: String,
            defaultValue: Long
        ): Long {
            val value = getAnnotationLongValue(annotation, name)
            return value ?: defaultValue
        }

        @JvmStatic
        fun getAnnotationDoubleValue(
            annotation: UAnnotation?,
            name: String
        ): Double? {
            return AnnotationValuesExtractor
                .getAnnotationValuesExtractor(annotation)
                .getAnnotationDoubleValue(annotation, name)
        }

        @JvmStatic
        fun getAnnotationDoubleValue(
            annotation: UAnnotation?,
            name: String,
            defaultValue: Double
        ): Double {
            val value = getAnnotationDoubleValue(annotation, name)
            return value ?: defaultValue
        }

        @JvmStatic
        fun getAnnotationStringValue(
            annotation: UAnnotation?,
            name: String
        ): String? {
            return AnnotationValuesExtractor
                .getAnnotationValuesExtractor(annotation)
                .getAnnotationStringValue(annotation, name)
        }

        @JvmStatic
        fun getAnnotationStringValues(
            annotation: UAnnotation?,
            name: String
        ): Array<String>? {
            return AnnotationValuesExtractor
                .getAnnotationValuesExtractor(annotation)
                .getAnnotationStringValues(annotation, name)
        }

        @JvmStatic
        fun containsAnnotation(list: List<UAnnotation>, annotation: UAnnotation): Boolean =
            list.stream().anyMatch { e -> e === annotation }

        @JvmStatic
        fun containsAnnotation(
            list: List<UAnnotation>,
            qualifiedName: String
        ): Boolean = list.stream().anyMatch { e -> e.qualifiedName == qualifiedName }
    }
}
