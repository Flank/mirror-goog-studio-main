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

package com.android.tools.lint.client.api

import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.SUPPORT_ANNOTATIONS_PREFIX
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.google.common.collect.Multimap
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierListOwner
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UArrayAccessExpression
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UEnumConstant
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.getContainingUMethod
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.java.JavaUAnnotation
import org.jetbrains.uast.util.isAssignment
import org.jetbrains.uast.visitor.AbstractUastVisitor
import java.util.ArrayList
import java.util.HashSet

/**
 * Looks up annotations on method calls and enforces the various things they express.
 */
internal class AnnotationHandler(private val scanners: Multimap<String, SourceCodeScanner>) {

    val relevantAnnotations: Set<String> = HashSet<String>(scanners.keys())

    private fun checkContextAnnotations(
        context: JavaContext,
        method: PsiMethod?,
        origCall: UElement,
        allMethodAnnotations: List<UAnnotation>
    ) {
        var call = origCall
        // Handle typedefs and resource types: if you're comparing it, check that
        // it's being compared with something compatible
        var p = com.android.tools.lint.detector.api.skipParentheses(call.uastParent) ?: return

        if (p is UQualifiedReferenceExpression) {
            call = p
            p = p.uastParent ?: return
        }

        if (p is UBinaryExpression) {
            var check: UExpression? = null
            val binary = p
            if (call === binary.leftOperand) {
                check = binary.rightOperand
            } else if (call === binary.rightOperand) {
                check = binary.leftOperand
            }
            if (check != null) {
                checkAnnotations(
                    context = context,
                    argument = check,
                    type = when (p.operator) {
                        UastBinaryOperator.ASSIGN -> AnnotationUsageType.ASSIGNMENT
                        UastBinaryOperator.EQUALS,
                        UastBinaryOperator.NOT_EQUALS,
                        UastBinaryOperator.IDENTITY_EQUALS,
                        UastBinaryOperator.IDENTITY_NOT_EQUALS -> AnnotationUsageType.EQUALITY
                        else -> AnnotationUsageType.BINARY
                    },
                    method = method,
                    annotations = allMethodAnnotations
                )
            }
        } else if (p is UQualifiedReferenceExpression) {
            // Handle equals() as a special case: if you're invoking
            //   .equals on a method whose return value annotated with @StringDef
            //   we want to make sure that the equals parameter is compatible.
            // 186598: StringDef don't warn using a getter and equals
            val ref = p as UQualifiedReferenceExpression?
            if ("equals" == ref?.resolvedName) {
                val selector = ref.selector
                if (selector is UCallExpression) {
                    val arguments = selector.valueArguments
                    if (arguments.size == 1) {
                        checkAnnotations(
                            context = context,
                            argument = arguments[0],
                            type = AnnotationUsageType.EQUALITY,
                            method = method,
                            annotations = allMethodAnnotations
                        )
                    }
                }
            }
        } else if (p.isAssignment()) {
            val assignment = p as UBinaryExpression
            val rExpression = assignment.rightOperand
            checkAnnotations(
                context = context,
                argument = rExpression,
                type = AnnotationUsageType.ASSIGNMENT,
                method = method,
                annotations = allMethodAnnotations
            )
        } else if (call is UVariable) {
            val variable = call
            val variablePsi = call.psi
            // TODO: What about fields?
            call.getContainingUMethod()?.accept(object : AbstractUastVisitor() {
                override fun visitSimpleNameReferenceExpression(
                    node: USimpleNameReferenceExpression
                ): Boolean {
                    val resolved = node.resolve()
                    if (variable == resolved || variablePsi == resolved) {
                        val expression = node.getParentOfType<UExpression>(
                            UExpression::class.java,
                            true
                        )
                        if (expression != null) {
                            val inner = node.getParentOfType<UExpression>(
                                UExpression::class.java,
                                false
                            ) ?: return false
                            checkAnnotations(
                                context = context,
                                argument = inner,
                                type = AnnotationUsageType.VARIABLE_REFERENCE,
                                method = method,
                                annotations = allMethodAnnotations
                            )
                            return false
                        }

                        // TODO: if the reference is the LHS Of an assignment
                        //   UastExpressionUtils.isAssignment(expression)
                        // then assert the annotations on to the right hand side
                    }
                    return super.visitSimpleNameReferenceExpression(node)
                }
            })

            val initializer = variable.uastInitializer
            if (initializer != null) {
                checkAnnotations(
                    context,
                    initializer,
                    type = AnnotationUsageType.ASSIGNMENT,
                    method = null,
                    annotations = allMethodAnnotations
                )
            }
        }
    }

    private fun checkAnnotations(
        context: JavaContext,
        argument: UElement,
        type: AnnotationUsageType,
        method: PsiMethod?,
        annotations: List<UAnnotation>,
        allMethodAnnotations: List<UAnnotation> = emptyList(),
        allClassAnnotations: List<UAnnotation> = emptyList(),
        packageAnnotations: List<UAnnotation> = emptyList()
    ) {

        for (annotation in annotations) {
            val signature = annotation.qualifiedName ?: continue
            val uastScanners = scanners.get(signature)
            if (uastScanners != null) {
                for (scanner in uastScanners) {
                    if (scanner.isApplicableAnnotationUsage(type)) {
                        scanner.visitAnnotationUsage(
                            context, argument, type, annotation,
                            signature, method, annotations, allMethodAnnotations,
                            allClassAnnotations, packageAnnotations
                        )
                    }
                }
            }
        }
    }

    // TODO: visitField too such that we can enforce initializer consistency with
    // declared constraints!

    fun visitMethod(context: JavaContext, method: UMethod) {
        val evaluator = context.evaluator
        val methodAnnotations = filterRelevantAnnotations(
            evaluator,
            evaluator.getAllAnnotations(method, true)
        )
        if (methodAnnotations.isNotEmpty()) {
            val annotations = JavaUAnnotation.wrap(methodAnnotations)

            // Check return values

            // Kotlin implicit method?
            val body = method.uastBody
            if (body != null && body !is UBlockExpression && body !is UReturnExpression) {
                checkAnnotations(
                    context = context,
                    argument = body,
                    type = AnnotationUsageType.METHOD_RETURN,
                    method = method,
                    annotations = annotations,
                    allMethodAnnotations = annotations
                )
            } else {
                method.accept(object : AbstractUastVisitor() {
                    override fun visitReturnExpression(node: UReturnExpression): Boolean {
                        val returnValue = node.returnExpression
                        if (returnValue != null) {
                            checkAnnotations(
                                context = context,
                                argument = returnValue,
                                type = AnnotationUsageType.METHOD_RETURN,
                                method = method,
                                annotations = annotations,
                                allMethodAnnotations = annotations
                            )
                        }
                        return super.visitReturnExpression(node)
                    }
                })
            }
        }
    }

    fun visitCallExpression(context: JavaContext, call: UCallExpression) {
        val method = call.resolve()
        if (method != null) {
            checkCall(context, method, call)
        }
    }

    fun visitAnnotation(context: JavaContext, annotation: UAnnotation) {
        // Check annotation references; these are a form of method call
        val qualifiedName = annotation.qualifiedName
        if (qualifiedName == null || qualifiedName.startsWith("java.") ||
            SUPPORT_ANNOTATIONS_PREFIX.isPrefix(qualifiedName)
        ) {
            return
        }

        val attributeValues = annotation.attributeValues
        if (attributeValues.isEmpty()) {
            return
        }

        val resolved = annotation.resolve() ?: return

        for (expression in attributeValues) {
            val name = expression.name ?: ATTR_VALUE
            val methods = resolved.findMethodsByName(name, false)
            if (methods.size == 1) {
                val method = methods[0]
                val evaluator = context.evaluator
                val methodAnnotations = filterRelevantAnnotations(
                    evaluator,
                    evaluator.getAllAnnotations(method, true)
                )
                if (methodAnnotations.isNotEmpty()) {
                    val value = expression.expression
                    val annotations = JavaUAnnotation.wrap(methodAnnotations)
                    checkAnnotations(
                        context = context,
                        argument = value,
                        type = AnnotationUsageType.ANNOTATION_REFERENCE,
                        method = method,
                        annotations = annotations,
                        allMethodAnnotations = annotations
                    )
                }
            }
        }
    }

    fun visitEnumConstant(context: JavaContext, constant: UEnumConstant) {
        val method = constant.resolveMethod()
        if (method != null) {
            checkCall(context, method, constant)
        }
    }

    fun visitArrayAccessExpression(
        context: JavaContext,
        expression: UArrayAccessExpression
    ) {
        val arrayExpression = expression.receiver
        if (arrayExpression is UReferenceExpression) {
            val resolved = arrayExpression.resolve()
            if (resolved is PsiModifierListOwner) {
                val evaluator = context.evaluator
                var methodAnnotations = evaluator.getAllAnnotations(resolved, true)
                methodAnnotations = filterRelevantAnnotations(evaluator, methodAnnotations)
                if (methodAnnotations.isNotEmpty()) {
                    checkContextAnnotations(
                        context, null, expression,
                        JavaUAnnotation.wrap(methodAnnotations)
                    )
                }
            }
        }
    }

    fun visitVariable(context: JavaContext, variable: UVariable) {
        val evaluator = context.evaluator
        val psi = variable.psi
        val methodAnnotations = filterRelevantAnnotations(
            evaluator,
            evaluator.getAllAnnotations(psi, true)
        )
        if (methodAnnotations.isNotEmpty()) {
            val annotations = JavaUAnnotation.wrap(methodAnnotations)
            checkContextAnnotations(context, null, variable, annotations)
        }
    }

    private fun checkCall(
        context: JavaContext,
        method: PsiMethod,
        call: UCallExpression
    ) {
        val evaluator = context.evaluator
        val allAnnotations = evaluator.getAllAnnotations(method, true)
        val methodAnnotations: List<UAnnotation> = JavaUAnnotation.wrap(
            filterRelevantAnnotations(evaluator, allAnnotations)
        )

        // Look for annotations on the class as well: these trickle
        // down to all the methods in the class
        val containingClass = method.containingClass
        val classAnnotations: List<UAnnotation>
        val pkgAnnotations: List<UAnnotation>
        if (containingClass != null) {
            val annotations = evaluator.getAllAnnotations(containingClass, true)
            classAnnotations = JavaUAnnotation
                .wrap(filterRelevantAnnotations(evaluator, annotations))

            val pkg = evaluator.getPackage(containingClass)
            pkgAnnotations = if (pkg != null) {
                val annotations2 = evaluator.getAllAnnotations(pkg, false)
                JavaUAnnotation.wrap(filterRelevantAnnotations(evaluator, annotations2))
            } else {
                emptyList()
            }
        } else {
            classAnnotations = emptyList()
            pkgAnnotations = emptyList()
        }

        if (!methodAnnotations.isEmpty()) {
            checkAnnotations(
                context, call, AnnotationUsageType.METHOD_CALL, method,
                methodAnnotations, methodAnnotations, classAnnotations, pkgAnnotations
            )

            checkContextAnnotations(context, method, call, methodAnnotations)
        }

        if (!classAnnotations.isEmpty()) {
            checkAnnotations(
                context, call, AnnotationUsageType.METHOD_CALL_CLASS, method,
                classAnnotations, methodAnnotations, classAnnotations, pkgAnnotations
            )
        }

        if (!pkgAnnotations.isEmpty()) {
            checkAnnotations(
                context, call, AnnotationUsageType.METHOD_CALL_PACKAGE, method,
                pkgAnnotations, methodAnnotations, classAnnotations, pkgAnnotations
            )
        }

        val mapping = evaluator.computeArgumentMapping(call, method)
        for ((argument, parameter) in mapping) {
            val allParameterAnnotations = evaluator.getAllAnnotations(parameter, true)
            val filtered = filterRelevantAnnotations(evaluator, allParameterAnnotations)
            if (filtered.isEmpty()) {
                continue
            }
            val annotations = JavaUAnnotation.wrap(filtered)
            checkAnnotations(
                context, argument, AnnotationUsageType.METHOD_CALL_PARAMETER, method,
                annotations, methodAnnotations, classAnnotations, pkgAnnotations
            )
        }
    }

    private fun filterRelevantAnnotations(
        evaluator: JavaEvaluator,
        annotations: Array<PsiAnnotation>
    ): Array<PsiAnnotation> {
        var result: MutableList<PsiAnnotation>? = null
        val length = annotations.size
        if (length == 0) {
            return annotations
        }
        for (annotation in annotations) {
            val signature = annotation.qualifiedName
            if (signature == null ||
                signature.startsWith("java.") && !relevantAnnotations.contains(signature)
            ) {
                // @Override, @SuppressWarnings etc. Ignore
                continue
            }

            if (relevantAnnotations.contains(signature)) {
                // Common case: there's just one annotation; no need to create a list copy
                if (length == 1) {
                    return annotations
                }
                if (result == null) {
                    result = ArrayList(2)
                }
                result.add(annotation)
                continue
            }

            // Special case @IntDef and @StringDef: These are used on annotations
            // themselves. For example, you create a new annotation named @foo.bar.Baz,
            // annotate it with @IntDef, and then use @foo.bar.Baz in your signatures.
            // Here we want to map from @foo.bar.Baz to the corresponding int def.
            // Don't need to compute this if performing @IntDef or @StringDef lookup
            val ref = annotation.nameReferenceElement ?: continue
            val cls = ref.resolve()
            if (cls !is PsiClass || !cls.isAnnotationType) {
                continue
            }
            val innerAnnotations = evaluator.getAllAnnotations(cls, false)
            for (j in innerAnnotations.indices) {
                val inner = innerAnnotations[j]
                val a = inner.qualifiedName
                if (a != null && relevantAnnotations.contains(a)) {
                    if (length == 1 && j == innerAnnotations.size - 1 && result == null) {
                        return innerAnnotations
                    }
                    if (result == null) {
                        result = ArrayList(2)
                    }
                    result.add(inner)
                }
            }
        }

        return if (result != null)
            result.toTypedArray()
        else PsiAnnotation.EMPTY_ARRAY
    }
}
