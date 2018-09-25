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

import com.android.SdkConstants.SUPPORT_ANNOTATIONS_PREFIX
import com.android.resources.ResourceType
import com.android.resources.ResourceType.COLOR
import com.android.resources.ResourceType.DRAWABLE
import com.android.resources.ResourceType.MIPMAP
import com.android.resources.ResourceType.STYLEABLE
import com.android.tools.lint.checks.AnnotationDetector.HALF_FLOAT_ANNOTATION
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.ResourceEvaluator
import com.android.tools.lint.detector.api.ResourceEvaluator.ANIMATOR_RES_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.ANIM_RES_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.ANY_RES_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.ARRAY_RES_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.ATTR_RES_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.BOOL_RES_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.COLOR_INT_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.COLOR_INT_MARKER_TYPE
import com.android.tools.lint.detector.api.ResourceEvaluator.COLOR_RES_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.DIMENSION_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.DIMENSION_MARKER_TYPE
import com.android.tools.lint.detector.api.ResourceEvaluator.DIMEN_RES_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.DRAWABLE_RES_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.FONT_RES_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.FRACTION_RES_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.ID_RES_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.INTEGER_RES_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.INTERPOLATOR_RES_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.LAYOUT_RES_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.MENU_RES_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.NAVIGATION_RES_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.PLURALS_RES_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.PX_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.RAW_RES_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.STRING_RES_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.STYLEABLE_RES_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.STYLE_RES_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.TRANSITION_RES_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.XML_RES_ANNOTATION
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.UastLintUtils
import com.google.common.base.Joiner
import com.google.common.collect.Sets
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.psi.PsiVariable
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBinaryExpressionWithType
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.util.isMethodCall
import org.jetbrains.uast.util.isNewArrayWithDimensions
import org.jetbrains.uast.util.isNewArrayWithInitializer
import org.jetbrains.uast.util.isTypeCast
import java.util.EnumSet

class ResourceTypeDetector : AbstractAnnotationDetector(), SourceCodeScanner {
    override fun applicableAnnotations(): List<String> = listOf(
        COLOR_INT_ANNOTATION.oldName(),
        COLOR_INT_ANNOTATION.newName(),
        DIMENSION_ANNOTATION.oldName(),
        DIMENSION_ANNOTATION.newName(),
        PX_ANNOTATION.oldName(),
        PX_ANNOTATION.newName(),
        HALF_FLOAT_ANNOTATION.oldName(),
        HALF_FLOAT_ANNOTATION.newName(),

        ANIMATOR_RES_ANNOTATION.oldName(),
        ANIMATOR_RES_ANNOTATION.newName(),
        ANIM_RES_ANNOTATION.oldName(),
        ANIM_RES_ANNOTATION.newName(),
        ANY_RES_ANNOTATION.oldName(),
        ANY_RES_ANNOTATION.newName(),
        ARRAY_RES_ANNOTATION.oldName(),
        ARRAY_RES_ANNOTATION.newName(),
        ATTR_RES_ANNOTATION.oldName(),
        ATTR_RES_ANNOTATION.newName(),
        BOOL_RES_ANNOTATION.oldName(),
        BOOL_RES_ANNOTATION.newName(),
        COLOR_RES_ANNOTATION.oldName(),
        COLOR_RES_ANNOTATION.newName(),
        FONT_RES_ANNOTATION.oldName(),
        FONT_RES_ANNOTATION.newName(),
        DIMEN_RES_ANNOTATION.oldName(),
        DIMEN_RES_ANNOTATION.newName(),
        DRAWABLE_RES_ANNOTATION.oldName(),
        DRAWABLE_RES_ANNOTATION.newName(),
        FRACTION_RES_ANNOTATION.oldName(),
        FRACTION_RES_ANNOTATION.newName(),
        ID_RES_ANNOTATION.oldName(),
        ID_RES_ANNOTATION.newName(),
        INTEGER_RES_ANNOTATION.oldName(),
        INTEGER_RES_ANNOTATION.newName(),
        INTERPOLATOR_RES_ANNOTATION.oldName(),
        INTERPOLATOR_RES_ANNOTATION.newName(),
        LAYOUT_RES_ANNOTATION.oldName(),
        LAYOUT_RES_ANNOTATION.newName(),
        MENU_RES_ANNOTATION.oldName(),
        MENU_RES_ANNOTATION.newName(),
        NAVIGATION_RES_ANNOTATION.oldName(),
        NAVIGATION_RES_ANNOTATION.newName(),
        PLURALS_RES_ANNOTATION.oldName(),
        PLURALS_RES_ANNOTATION.newName(),
        RAW_RES_ANNOTATION.oldName(),
        RAW_RES_ANNOTATION.newName(),
        STRING_RES_ANNOTATION.oldName(),
        STRING_RES_ANNOTATION.newName(),
        STYLEABLE_RES_ANNOTATION.oldName(),
        STYLEABLE_RES_ANNOTATION.newName(),
        STYLE_RES_ANNOTATION.oldName(),
        STYLE_RES_ANNOTATION.newName(),
        TRANSITION_RES_ANNOTATION.oldName(),
        TRANSITION_RES_ANNOTATION.newName(),
        XML_RES_ANNOTATION.oldName(),
        XML_RES_ANNOTATION.newName()
    )

    // Include all types, including equality and comparisons
    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean = true

    override fun visitAnnotationUsage(
        context: JavaContext,
        usage: UElement,
        type: AnnotationUsageType,
        annotation: UAnnotation,
        qualifiedName: String,
        method: PsiMethod?,
        referenced: PsiElement?,
        annotations: List<UAnnotation>,
        allMemberAnnotations: List<UAnnotation>,
        allClassAnnotations: List<UAnnotation>,
        allPackageAnnotations: List<UAnnotation>
    ) {
        when (qualifiedName) {
            COLOR_INT_ANNOTATION.oldName(), COLOR_INT_ANNOTATION.newName() -> checkColor(
                context,
                usage
            )
            HALF_FLOAT_ANNOTATION.oldName(), HALF_FLOAT_ANNOTATION.newName() -> checkHalfFloat(
                context,
                usage
            )
            DIMENSION_ANNOTATION.oldName(), DIMENSION_ANNOTATION.newName(), PX_ANNOTATION.oldName(), PX_ANNOTATION.newName() -> checkPx(
                context,
                usage
            )
            else -> {
                if (isResourceAnnotation(qualifiedName)) {
                    // Make sure it's the first one to avoid duplicate warnings since we check all
                    if (annotations.size > 1 &&
                        getFirstResourceAnnotation(annotations) !== annotation
                    ) {
                        return
                    }

                    val expression = usage.getParentOfType<UExpression>(
                        UExpression::class.java, true
                    )
                    // Crap - how do we avoid double-checking here, we can't limit ourselves
                    // to just left or right because what if the other one doesn't have
                    // data?
                    if (expression is UBinaryExpression) {
                        // Comparing resource types is suspicious
                        val operator = expression.operator
                        if (operator is UastBinaryOperator.ComparisonOperator &&
                            operator !== UastBinaryOperator.EQUALS &&
                            operator !== UastBinaryOperator.NOT_EQUALS &&
                            operator !== UastBinaryOperator.IDENTITY_EQUALS &&
                            operator !== UastBinaryOperator.IDENTITY_NOT_EQUALS
                        ) {
                            context.report(
                                RESOURCE_TYPE, expression,
                                context.getLocation(expression),
                                String.format(
                                    "Comparing resource types (`@%1\$s`) other " +
                                            "than equality is dangerous and usually " +
                                            "wrong;  some resource types set top bit " +
                                            "which turns the value negative",
                                    SUPPORT_ANNOTATIONS_PREFIX.removeFrom(qualifiedName)
                                )
                            )
                            return
                        }
                    }

                    val types = ResourceEvaluator.getTypesFromAnnotations(annotations)
                    if (types != null) {
                        if (types.contains(ResourceType.STYLEABLE) &&
                            type == AnnotationUsageType.ASSIGNMENT
                        ) {
                            // Allow assigning constants to R.styleable; this is done
                            // for example in the support library
                            return
                        }

                        checkResourceType(context, usage, types, method)
                    }
                }
            }
        }
    }

    private fun getFirstResourceAnnotation(annotations: List<UAnnotation>): UAnnotation? {
        for (annotation in annotations) {
            val qualifiedName = annotation.qualifiedName
            if (qualifiedName != null && isResourceAnnotation(qualifiedName)) {
                return annotation
            }
        }
        return null
    }

    private fun isResourceAnnotation(signature: String): Boolean {
        return ResourceEvaluator.getTypeFromAnnotationSignature(signature) != null ||
                ANY_RES_ANNOTATION.isEquals(signature)
    }

    private fun checkColor(context: JavaContext, argument: UElement) {
        if (argument is UIfExpression) {
            if (argument.thenExpression != null) {
                checkColor(context, argument.thenExpression!!)
            }
            if (argument.elseExpression != null) {
                checkColor(context, argument.elseExpression!!)
            }
            return
        }

        val types = ResourceEvaluator.getResourceTypes(
            context.evaluator,
            argument
        )

        if (types != null && types.contains(COLOR)) {
            val message = String.format(
                "Should pass resolved color instead of resource id here: " +
                        "`getResources().getColor(%1\$s)`",
                argument.asSourceString()
            )
            report(context, COLOR_USAGE, argument, context.getLocation(argument), message)
        }
    }

    /**
     * Checks for the following constraints regarding half float annotated shorts:
     *
     * (1) you're not passing literals; this is fraught with danger and there are
     * a lot of constants available in the android.util.Half class already
     *
     * (2) you're not performing arithmetic on these operands; there are utility methods
     * in android.util.Half that should be used instead
     *
     * (3) when you're operating on Half float variables, none of the operations are
     * accidentally widening the result to int
     */
    private fun checkHalfFloat(
        context: JavaContext,
        argument: UElement
    ) {
        if (argument is UIfExpression) {
            val thenExpression = argument.thenExpression
            if (thenExpression != null) {
                checkColor(context, thenExpression)
            }
            val elseExpression = argument.elseExpression
            if (elseExpression != null) {
                checkColor(context, elseExpression)
            }
            return
        }

        val types = ResourceEvaluator.getResourceTypes(
            context.evaluator,
            argument
        )

        if (types != null && !types.isEmpty()) {
            val type = when {
                types.contains(DIMENSION_MARKER_TYPE) -> "dimension"
                types.contains(COLOR_INT_MARKER_TYPE) -> "color"
                else -> "resource id"
            }
            val message = String.format("Expected a half float here, not a %1\$s", type)
            report(context, HALF_FLOAT, argument, context.getLocation(argument), message)
            return
        }

        // TODO: Visit all occurrences of this member to see if it's used incorrectly
        //    -- expression type -> int -- widening

        for (curr in getParentSequence(argument, UExpression::class.java)) {
            if (curr is UBinaryExpressionWithType) {
                // Explicit cast
                break
            }
            val expressionType = curr.getExpressionType()
            if (expressionType != null && PsiType.SHORT != expressionType) {
                if (PsiType.VOID == expressionType || PsiType.BOOLEAN == expressionType ||
                    PsiType.BYTE == expressionType
                ) {
                    break
                }
                if (expressionType.canonicalText == "android.util.Half") {
                    break
                }

                val message = String.format(
                    "Half-float type in expression widened to %1\$s",
                    expressionType.canonicalText
                )
                report(context, HALF_FLOAT, argument, context.getLocation(argument), message)
                break
            }
        }
    }

    // TODO: Move to utility extension method
    private fun <T : UElement> getParentSequence(
        element: UElement,
        clz: Class<out T>
    ): Sequence<T> {
        val seed: T? = element.getParentOfType(clz, false)
        val nextFunction: (T) -> T? = {
            it.getParentOfType(clz, true)
        }
        return generateSequence(seed, nextFunction)
    }

    private fun checkPx(context: JavaContext, argument: UElement) {
        if (argument is UIfExpression) {
            if (argument.thenExpression != null) {
                checkPx(context, argument.thenExpression!!)
            }
            if (argument.elseExpression != null) {
                checkPx(context, argument.elseExpression!!)
            }
            return
        }

        val types = ResourceEvaluator.getResourceTypes(
            context.evaluator,
            argument
        )

        if (types != null && types.contains(ResourceType.DIMEN)) {
            val message = String.format(
                "Should pass resolved pixel dimension instead of resource id here: " +
                        "`getResources().getDimension*(%1\$s)`",
                argument.asSourceString()
            )
            report(
                context, COLOR_USAGE, argument,
                context.getLocation(argument), message
            )
        }
    }

    private fun checkResourceType(
        context: JavaContext,
        argument: UElement,
        expectedTypes: EnumSet<ResourceType>,
        calledMethod: PsiMethod?
    ) {
        val actual = ResourceEvaluator.getResourceTypes(context.evaluator, argument)

        if (actual == null && (!UastLintUtils.isNumber(argument) || UastLintUtils.isZero(argument) || UastLintUtils.isMinusOne(
                argument
            ))
        ) {
            return
        } else if (actual != null && (!Sets.intersection(
                actual,
                expectedTypes
            ).isEmpty() || expectedTypes.contains(DRAWABLE) && (actual.contains(COLOR) || actual.contains(
                MIPMAP
            )))
        ) {
            return
        }

        if (expectedTypes.contains(STYLEABLE) && expectedTypes.size == 1 &&
            calledMethod != null &&
            context.evaluator.isMemberInClass(
                calledMethod,
                "android.content.res.TypedArray"
            )
        ) {
            val call = argument.getParentOfType<UExpression>(UCallExpression::class.java, false)
            if (call is UCallExpression &&
                typeArrayFromArrayLiteral(call.receiver, context)
            ) {
                // You're generally supposed to provide a styleable to the TypedArray methods,
                // but you're also allowed to supply an integer array
                return
            }
        }

        val message: String
        if (actual != null && actual.size == 1 && actual.contains(
                ResourceEvaluator.COLOR_INT_MARKER_TYPE
            )
        ) {
            message = "Expected a color resource id (`R.color.`) but received an RGB integer"
        } else if (expectedTypes.contains(ResourceEvaluator.COLOR_INT_MARKER_TYPE)) {
            message = String.format(
                "Should pass resolved color instead of resource id here: `getResources().getColor(%1\$s)`",
                argument.asSourceString()
            )
        } else if (actual != null && actual.size == 1 && actual.contains(
                ResourceEvaluator.DIMENSION_MARKER_TYPE
            )
        ) {
            message = "Expected a dimension resource id (`R.color.`) but received a pixel integer"
        } else if (expectedTypes.contains(ResourceEvaluator.DIMENSION_MARKER_TYPE)) {
            message = String.format(
                "Should pass resolved pixel size instead of resource id here: `getResources().getDimension*(%1\$s)`",
                argument.asSourceString()
            )
        } else if (expectedTypes.size < ResourceType.values().size - 2) { // -2: marker types
            message = String.format(
                "Expected resource of type %1\$s",
                Joiner.on(" or ").join(expectedTypes)
            )
        } else {
            message = "Expected resource identifier (`R`.type.`name`)"
        }
        report(context, RESOURCE_TYPE, argument, context.getLocation(argument), message)
    }

    /**
     * Returns true if the node is pointing to a TypedArray whose value was obtained
     * from an array literal
     */
    private fun typeArrayFromArrayLiteral(node: UElement?, context: JavaContext): Boolean {
        if (node == null) {
            return false
        }
        val expression = getMethodCall(node)
        if (expression != null) {
            val name = expression.methodName
            if (name != null && "obtainStyledAttributes" == name) {
                val expressions = expression.valueArguments
                if (!expressions.isEmpty()) {
                    var arg: Int
                    if (expressions.size == 1) {
                        // obtainStyledAttributes(int[] attrs)
                        arg = 0
                    } else if (expressions.size == 2) {
                        // obtainStyledAttributes(AttributeSet set, int[] attrs)
                        // obtainStyledAttributes(int resid, int[] attrs)
                        arg = 0
                        while (arg < expressions.size) {
                            val type = expressions[arg].getExpressionType()
                            if (type is PsiArrayType) {
                                break
                            }
                            arg++
                        }
                        if (arg == expressions.size) {
                            return false
                        }
                    } else if (expressions.size == 4) {
                        // obtainStyledAttributes(AttributeSet set, int[] attrs, int defStyleAttr, int defStyleRes)
                        arg = 1
                    } else {
                        return false
                    }

                    return ConstantEvaluator.isArrayLiteral(
                        expressions[arg]
                    )
                }
            }
            return false
        } else if (node is UReferenceExpression) {
            val resolved = node.resolve()
            if (resolved is PsiVariable) {
                val variable = resolved as PsiVariable?
                val lastAssignment = UastLintUtils.findLastAssignment(variable!!, node)

                if (lastAssignment != null) {
                    return typeArrayFromArrayLiteral(lastAssignment, context)
                }
            }
        } else if (node.isNewArrayWithInitializer()) {
            return true
        } else if (node.isNewArrayWithDimensions()) {
            return true
        } else if (node is UParenthesizedExpression) {
            val parenthesizedExpression = node as UParenthesizedExpression?
            val operand = parenthesizedExpression!!.expression
            return typeArrayFromArrayLiteral(operand, context)
        } else if (node.isTypeCast()) {
            val castExpression = (node as UBinaryExpressionWithType?)!!
            val operand = castExpression.operand
            return typeArrayFromArrayLiteral(operand, context)
        }

        return false
    }

    private fun getMethodCall(node: UElement?): UCallExpression? {
        if (node is UQualifiedReferenceExpression) {
            val last = getLastInQualifiedChain((node as UQualifiedReferenceExpression?)!!)
            if (last.isMethodCall()) {
                return last as UCallExpression
            }
        }

        return if (node != null && node.isMethodCall()) {
            node as UCallExpression?
        } else null
    }

    private fun getLastInQualifiedChain(node: UQualifiedReferenceExpression): UExpression {
        var last = node.selector
        while (last is UQualifiedReferenceExpression) {
            last = last.selector
        }
        return last
    }

    companion object {
        private val IMPLEMENTATION = Implementation(
            ResourceTypeDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )

        /**
         * Attempting pass the wrong type of resource
         */
        @JvmField
        val RESOURCE_TYPE = Issue.create(
            id = "ResourceType",
            briefDescription = "Wrong Resource Type",
            explanation = """
                Ensures that resource id's passed to APIs are of the right type; for \
                example, calling `Resources.getColor(R.string.name)` is wrong.""",
            category = Category.CORRECTNESS,
            priority = 7,
            severity = Severity.ERROR,
            androidSpecific = true,
            implementation = IMPLEMENTATION
        )

        /** Attempting to set a resource id as a color */
        @JvmField
        val COLOR_USAGE = Issue.create(
            id = "ResourceAsColor",
            briefDescription = "Should pass resolved color instead of resource id",
            explanation = """
                Methods that take a color in the form of an integer should be passed an \
                RGB triple, not the actual color resource id. You must call \
                `getResources().getColor(resource)` to resolve the actual color value first.

                Similarly, methods that take a dimension integer should be passed an \
                actual dimension (call `getResources().getDimension(resource)`""",
            category = Category.CORRECTNESS,
            priority = 7,
            severity = Severity.ERROR,
            androidSpecific = true,
            implementation = IMPLEMENTATION
        )

        /** Incorrect usage of half floats */
        @JvmField
        val HALF_FLOAT = Issue.create(
            id = "HalfFloat",
            briefDescription = "Incorrect Half Float",
            explanation = """
                Half-precision floating point are stored in a short data type, and should be \
                manipulated using the `android.util.Half` class. This check flags usages \
                where it appears that these values are used incorrectly.""",
            category = Category.CORRECTNESS,
            priority = 7,
            severity = Severity.ERROR,
            androidSpecific = true,
            implementation = IMPLEMENTATION
        )
    }
}
