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
import com.android.SdkConstants.LONG_DEF_ANNOTATION
import com.android.tools.lint.checks.AnnotationDetector.FLOAT_RANGE_ANNOTATION
import com.android.tools.lint.checks.AnnotationDetector.INT_RANGE_ANNOTATION
import com.android.tools.lint.checks.AnnotationDetector.SIZE_ANNOTATION
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierListOwner
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.UResolvable
import org.jetbrains.uast.util.isNewArrayWithInitializer

class RangeDetector : AbstractAnnotationDetector(), SourceCodeScanner {
    override fun applicableAnnotations(): List<String> = listOf(
        INT_RANGE_ANNOTATION.oldName(),
        INT_RANGE_ANNOTATION.newName(),
        FLOAT_RANGE_ANNOTATION.oldName(),
        FLOAT_RANGE_ANNOTATION.newName(),
        SIZE_ANNOTATION.oldName(),
        SIZE_ANNOTATION.newName(),

        // Such that the annotation is considered relevant by the annotation handler
        // even if the typedef check itself is disabled
        INT_DEF_ANNOTATION.oldName(),
        INT_DEF_ANNOTATION.newName(),

        LONG_DEF_ANNOTATION.oldName(),
        LONG_DEF_ANNOTATION.newName()

        // Consider including org.jetbrains.annotations.Range here, but be careful
        // such that we don't end up with a double set of warnings in the IDE (one
        // from Lint, one from IntelliJ's support for this annotation)
    )

    override fun visitAnnotationUsage(
        context: JavaContext,
        usage: UElement,
        type: AnnotationUsageType,
        annotation: UAnnotation,
        qualifiedName: String,
        method: PsiMethod?,
        annotations: List<UAnnotation>,
        allMemberAnnotations: List<UAnnotation>,
        allClassAnnotations: List<UAnnotation>,
        allPackageAnnotations: List<UAnnotation>
    ) {
        when (qualifiedName) {
            INT_RANGE_ANNOTATION.oldName(), INT_RANGE_ANNOTATION.newName() -> {
                checkIntRange(context, annotation, usage, annotations)
            }

            FLOAT_RANGE_ANNOTATION.oldName(), FLOAT_RANGE_ANNOTATION.newName() -> {
                checkFloatRange(context, annotation, usage)
            }
            SIZE_ANNOTATION.oldName(), SIZE_ANNOTATION.newName() -> {
                checkSize(context, annotation, usage)
            }

            INT_DEF_ANNOTATION.oldName(), INT_DEF_ANNOTATION.newName(),
            LONG_DEF_ANNOTATION.oldName(), LONG_DEF_ANNOTATION.newName() -> {
            }
        }
    }

    private fun checkIntRange(
        context: JavaContext,
        annotation: UAnnotation,
        argument: UElement,
        allAnnotations: List<UAnnotation>
    ) {
        if (argument is UIfExpression) {
            if (argument.thenExpression != null) {
                checkIntRange(context, annotation, argument.thenExpression!!, allAnnotations)
            }
            if (argument.elseExpression != null) {
                checkIntRange(context, annotation, argument.elseExpression!!, allAnnotations)
            }
            return
        }

        val message = getIntRangeError(context, annotation, argument)
        if (message != null) {
            if (TypedefDetector.findIntDef(allAnnotations) != null) {
                // Don't flag int range errors if there is an int def annotation there too;
                // there could be a valid @IntDef constant. (The @IntDef check will
                // perform range validation by calling getIntRange.)
                return
            }

            report(context, RANGE, argument, context.getLocation(argument), message)
        }
    }

    private fun checkFloatRange(
        context: JavaContext,
        annotation: UAnnotation,
        argument: UElement
    ) {
        if (argument is UIfExpression) {
            if (argument.thenExpression != null) {
                checkFloatRange(context, annotation, argument.thenExpression!!)
            }
            if (argument.elseExpression != null) {
                checkFloatRange(context, annotation, argument.elseExpression!!)
            }
            return
        }

        val constraint = FloatRangeConstraint.create(annotation)

        val constant = ConstantEvaluator.evaluate(context, argument)
        if (constant !is Number) {
            // Number arrays
            if (constant is FloatArray ||
                constant is DoubleArray ||
                constant is IntArray ||
                constant is LongArray
            ) {
                if (constant is FloatArray) {
                    for (value in (constant as FloatArray?)!!) {
                        if (!constraint.isValid(value.toDouble())) {
                            val message = constraint.describe(value.toDouble())
                            report(
                                context, RANGE, argument, context.getLocation(argument),
                                message
                            )
                            return
                        }
                    }
                }
                // Kinda repetitive but primitive arrays are not related by subtyping
                if (constant is DoubleArray) {
                    for (value in (constant as DoubleArray?)!!) {
                        if (!constraint.isValid(value)) {
                            val message = constraint.describe(value)
                            report(
                                context, RANGE, argument, context.getLocation(argument),
                                message
                            )
                            return
                        }
                    }
                }
                if (constant is IntArray) {
                    for (value in (constant as IntArray?)!!) {
                        if (!constraint.isValid(value.toDouble())) {
                            val message = constraint.describe(value.toDouble())
                            report(
                                context, RANGE, argument, context.getLocation(argument),
                                message
                            )
                            return
                        }
                    }
                }
                if (constant is LongArray) {
                    for (value in (constant as LongArray?)!!) {
                        if (!constraint.isValid(value.toDouble())) {
                            val message = constraint.describe(value.toDouble())
                            report(
                                context, RANGE, argument, context.getLocation(argument),
                                message
                            )
                            return
                        }
                    }
                }
            }

            // Try to resolve it; see if there's an annotation on the variable/parameter/field
            if (argument is UResolvable) {
                val resolved = (argument as UResolvable).resolve()
                // TODO: What about parameters or local variables here?
                // UAST-wise we could look for UDeclaration but it turns out
                // UDeclaration also extends PsiModifierListOwner!
                if (resolved is PsiModifierListOwner) {
                    val referenceConstraint =
                        RangeConstraint.create((resolved as PsiModifierListOwner?)!!)
                    val here = RangeConstraint.create(annotation)
                    if (here != null && referenceConstraint != null) {
                        val contains = here.contains(referenceConstraint)
                        if (contains != null && !contains) {
                            val message = here.toString()
                            report(context, RANGE, argument, context.getLocation(argument), message)
                        }
                    }
                }
            }

            return
        }

        val value = constant.toDouble()
        if (!constraint.isValid(value)) {
            val message = constraint.describe(
                argument as? UExpression, value
            )
            report(context, RANGE, argument, context.getLocation(argument), message)
        }
    }

    private fun checkSize(
        context: JavaContext,
        annotation: UAnnotation,
        argument: UElement
    ) {
        val actual: Long
        var isString = false

        // TODO: Collections syntax, e.g. Arrays.asList ⇒ param count, emptyList=0, singleton=1, etc
        // TODO: Flow analysis

        if (argument.isNewArrayWithInitializer()) {
            actual = (argument as UCallExpression).valueArgumentCount.toLong()
        } else if (argument is UIfExpression) {
            if (argument.thenExpression != null) {
                checkSize(context, annotation, argument.thenExpression!!)
            }
            if (argument.elseExpression != null) {
                checkSize(context, annotation, argument.elseExpression!!)
            }
            return
        } else {
            val `object` = ConstantEvaluator.evaluate(context, argument)
            // Check string length
            if (`object` is String) {
                actual = `object`.length.toLong()
                isString = true
            } else {
                actual = ConstantEvaluator.getArraySize(`object`).toLong()
                if (actual == -1L) {
                    // Try to resolve it; see if there's an annotation on the variable/parameter/field
                    if (argument is UResolvable) {
                        val resolved = (argument as UResolvable).resolve()
                        if (resolved is PsiModifierListOwner) {
                            val constraint = RangeConstraint
                                .create((resolved as PsiModifierListOwner?)!!)
                            val here = RangeConstraint.create(annotation)
                            if (here != null && constraint != null) {
                                val contains = here.contains(constraint)
                                if (contains != null && !contains) {
                                    val message = here.toString()
                                    report(
                                        context, RANGE, argument, context.getLocation(argument),
                                        message
                                    )
                                }
                            }
                        }
                    }

                    return
                }
            }
        }

        val constraint = SizeConstraint.create(annotation)
        if (!constraint.isValid(actual)) {
            val unit = if (isString) {
                "length"
            } else {
                "size"
            }

            val message = constraint.describe(
                argument as? UExpression,
                unit, actual
            )
            report(context, RANGE, argument, context.getLocation(argument), message)
        }
    }

    companion object {
        private val IMPLEMENTATION = Implementation(
            RangeDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )

        fun findIntRange(annotations: List<UAnnotation>): UAnnotation? {
            for (annotation in annotations) {
                if (INT_RANGE_ANNOTATION.isEquals(annotation.qualifiedName)) {
                    return annotation
                }
            }

            return null
        }

        fun getIntRangeError(
            context: JavaContext,
            annotation: UAnnotation,
            argument: UElement
        ): String? {
            if (argument.isNewArrayWithInitializer()) {
                val newExpression = argument as UCallExpression
                for (expression in newExpression.valueArguments) {
                    val error = getIntRangeError(context, annotation, expression)
                    if (error != null) {
                        return error
                    }
                }
            }

            val constraint = IntRangeConstraint.create(annotation)

            val o = ConstantEvaluator.evaluate(context, argument)
            if (o !is Number) {
                // Number arrays
                if (o is IntArray || o is LongArray) {
                    if (o is IntArray) {
                        for (value in (o as IntArray?)!!) {
                            if (!constraint.isValid(value.toLong())) {
                                return constraint.describe(value.toLong())
                            }
                        }
                    }
                    if (o is LongArray) {
                        for (value in (o as LongArray?)!!) {
                            if (!constraint.isValid(value)) {
                                return constraint.describe(value)
                            }
                        }
                    }
                }

                // Try to resolve it; see if there's an annotation on the variable/parameter/field
                if (argument is UResolvable) {
                    val resolved = (argument as UResolvable).resolve()
                    // TODO: What about parameters or local variables here?
                    // UAST-wise we could look for UDeclaration but it turns out
                    // UDeclaration also extends PsiModifierListOwner!
                    if (resolved is PsiModifierListOwner) {
                        val referenceConstraint =
                            RangeConstraint.create((resolved as PsiModifierListOwner?)!!)
                        val here = RangeConstraint.create(annotation)
                        if (here != null && referenceConstraint != null) {
                            val contains = here.contains(referenceConstraint)
                            if (contains != null && !contains) {
                                return here.toString()
                            }
                        }
                    }
                }

                return null
            }

            val value = o.toLong()
            return if (!constraint.isValid(value)) {
                constraint.describe(value)
            } else null
        }

        /** Makes sure values are within the allowed range */
        @JvmField
        val RANGE = Issue.create(
            id = "Range",
            briefDescription = "Outside Range",
            explanation = """
                Some parameters are required to in a particular numerical range; this check \
                makes sure that arguments passed fall within the range. For arrays, Strings \
                and collections this refers to the size or length.""",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION
        )
    }
}
