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
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiTypesUtil
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UArrayAccessExpression
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UEnumConstant
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULambdaExpression
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
import org.jetbrains.uast.util.isConstructorCall
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
        referenced: PsiElement?,
        origCall: UElement,
        allMethodAnnotations: List<UAnnotation>,
        annotated: PsiElement
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
                    referenced = referenced,
                    annotations = allMethodAnnotations,
                    annotated = annotated
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
                            referenced = referenced,
                            annotations = allMethodAnnotations,
                            annotated = annotated
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
                referenced = referenced,
                annotations = allMethodAnnotations,
                annotated = annotated
            )
        } else if (call is UVariable) {
            val variable = call
            val variablePsi = call.psi
            // TODO: What about fields?
            call.getContainingUMethod()?.accept(object : AbstractUastVisitor() {
                override fun visitSimpleNameReferenceExpression(
                    node: USimpleNameReferenceExpression
                ): Boolean {
                    val referencedVariable = node.resolve()
                    if (variable == referencedVariable || variablePsi == referencedVariable) {
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
                                referenced = referenced,
                                annotations = allMethodAnnotations,
                                annotated = annotated
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
                    referenced = referenced,
                    annotations = allMethodAnnotations,
                    annotated = annotated
                )
            }
        }
    }

    private fun checkAnnotations(
        context: JavaContext,
        argument: UElement,
        type: AnnotationUsageType,
        method: PsiMethod?,
        referenced: PsiElement?,
        annotations: List<UAnnotation>,
        allMethodAnnotations: List<UAnnotation> = emptyList(),
        allClassAnnotations: List<UAnnotation> = emptyList(),
        packageAnnotations: List<UAnnotation> = emptyList(),
        annotated: PsiElement?
    ) {
        for (annotation in annotations) {
            val signature = annotation.qualifiedName ?: continue
            val uastScanners = scanners.get(signature)
            if (uastScanners != null) {
                var uAnnotations: List<UAnnotation>? = null
                var psiAnnotations: Array<out PsiAnnotation>? = null

                for (scanner in uastScanners) {
                    if (scanner.isApplicableAnnotationUsage(type)) {

                        // Some annotations should not be treated as inherited though
                        // the hierarchy: if that's the case for this annotation in
                        // this scanner, check whether it's inherited and if so, skip it
                        if (annotated != null && !scanner.inheritAnnotation(signature)) {
                            // First try to look by directly checking the owner element of
                            // the annotation.
                            val annotationOwner = (annotation.sourcePsi as? PsiAnnotation)?.owner
                            val owner =
                                if (annotationOwner is PsiElement) {
                                    PsiTreeUtil.getParentOfType(
                                        annotationOwner,
                                        PsiModifierListOwner::class.java
                                    )
                                } else {
                                    null
                                }
                            if (owner != null) {
                                val annotatedPsi = (annotated as? UElement)?.sourcePsi ?: annotated
                                if (owner != annotatedPsi) {
                                    continue
                                }
                            } else {
                                // Figure out if this is an inherited annotation: it would be
                                // if it's not annotated on the element
                                if (annotated is UAnnotated) {
                                    var found = false
                                    for (
                                        uAnnotation in uAnnotations ?: run {
                                            val list = context.evaluator.getAllAnnotations(
                                                annotated,
                                                inHierarchy = false
                                            )
                                            uAnnotations = list
                                            list
                                        }
                                    ) {
                                        val qualifiedName = uAnnotation.qualifiedName
                                        if (qualifiedName == signature) {
                                            found = true
                                            break
                                        }
                                    }
                                    if (!found) {
                                        continue
                                    }
                                }
                                if (annotated is PsiModifierListOwner) {
                                    var found = false

                                    for (
                                        psiAnnotation in psiAnnotations ?: run {
                                            val array =
                                                context.evaluator.getAllAnnotations(annotated, false)
                                            psiAnnotations = array
                                            array
                                        }
                                    ) {
                                        val qualifiedName = psiAnnotation.qualifiedName
                                        if (qualifiedName == signature) {
                                            found = true
                                            break
                                        }
                                    }
                                    if (!found) {
                                        continue
                                    }
                                }
                            }
                        }

                        scanner.visitAnnotationUsage(
                            context, argument, type, annotation,
                            signature, method, referenced, annotations, allMethodAnnotations,
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
            evaluator.getAllAnnotations(method as UAnnotated, inHierarchy = true)
        )
        if (methodAnnotations.isNotEmpty()) {
            // Check return values

            // Kotlin implicit method?
            val body = method.uastBody
            if (body != null && body !is UBlockExpression && body !is UReturnExpression) {
                checkAnnotations(
                    context = context,
                    argument = body,
                    type = AnnotationUsageType.METHOD_RETURN,
                    method = method,
                    referenced = method,
                    annotations = methodAnnotations,
                    allMethodAnnotations = methodAnnotations,
                    annotated = method
                )
            } else {
                method.accept(object : AbstractUastVisitor() {
                    // Don't visit inner classes
                    override fun visitClass(node: UClass): Boolean {
                        return true
                    }

                    override fun visitLambdaExpression(node: ULambdaExpression): Boolean {
                        // Return statements inside the lambda may not refer to this method;
                        // see for example 140626689
                        return true
                    }

                    override fun visitReturnExpression(node: UReturnExpression): Boolean {
                        val returnValue = node.returnExpression
                        if (returnValue != null) {
                            checkAnnotations(
                                context = context,
                                argument = returnValue,
                                type = AnnotationUsageType.METHOD_RETURN,
                                method = method,
                                referenced = method,
                                annotations = methodAnnotations,
                                allMethodAnnotations = methodAnnotations,
                                annotated = method
                            )
                        }
                        return super.visitReturnExpression(node)
                    }
                })
            }
        }
    }

    fun visitClass(
        context: JavaContext,
        node: UClass
    ) {
        for (superType in node.uastSuperTypes) {
            // superType.tryResolve() does not work because the type reference
            // expressions are not implementing UResolvable
            val type = superType.type
            val resolved = PsiTypesUtil.getPsiClass(type) ?: continue
            val evaluator = context.evaluator
            val annotations = filterRelevantAnnotations(
                evaluator,
                evaluator.getAllAnnotations(resolved, inHierarchy = true)
            )
            checkAnnotations(
                context = context,
                argument = superType,
                type = AnnotationUsageType.EXTENDS,
                method = null,
                referenced = resolved,
                annotations = annotations,
                annotated = resolved
            )
        }
    }

    fun visitSimpleNameReferenceExpression(
        context: JavaContext,
        node: USimpleNameReferenceExpression
    ) {
        /* Pending:
        // In a qualified expression like x.y.z, only do field reference checks on z?
        val parent = node.uastParent
        if (parent is UQualifiedReferenceExpression && parent.selector != node) {
            return
        }
        */

        val field = node.resolve()
        if (field is PsiField || field is PsiMethod) {
            val evaluator = context.evaluator
            val annotations = filterRelevantAnnotations(
                evaluator,
                evaluator.getAllAnnotations(field as PsiMember, inHierarchy = true)
            )
            checkAnnotations(
                context = context,
                argument = node,
                type = AnnotationUsageType.FIELD_REFERENCE,
                method = null,
                referenced = field,
                annotations = annotations,
                annotated = field
            )
        }
    }

    fun visitCallExpression(context: JavaContext, call: UCallExpression) {
        val method = call.resolve()
        // Implicit, generated, default constructors resolve to null, but we still want to check
        // this case using the class's annotations. So thus, check a null if it represents
        // a constructor call.
        if (method == null) {
            if (call.isConstructorCall()) {
                checkCallUnresolved(context, call)
            }
        } else {
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
                    evaluator.getAllAnnotations(method, inHierarchy = true)
                )
                if (methodAnnotations.isNotEmpty()) {
                    val value = expression.expression
                    checkAnnotations(
                        context = context,
                        argument = value,
                        type = AnnotationUsageType.ANNOTATION_REFERENCE,
                        method = method,
                        referenced = method,
                        annotations = methodAnnotations,
                        allMethodAnnotations = methodAnnotations,
                        annotated = method
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
                val allAnnotations = evaluator.getAllAnnotations(resolved, inHierarchy = true)
                val methodAnnotations =
                    filterRelevantAnnotations(evaluator, allAnnotations, expression)
                if (methodAnnotations.isNotEmpty()) {
                    checkContextAnnotations(
                        context,
                        null,
                        resolved,
                        expression,
                        methodAnnotations,
                        resolved
                    )
                }
            }
        }
    }

    fun visitVariable(context: JavaContext, variable: UVariable) {
        val evaluator = context.evaluator
        val methodAnnotations = filterRelevantAnnotations(
            evaluator,
            evaluator.getAllAnnotations(variable as UAnnotated, inHierarchy = true)
        )
        if (methodAnnotations.isNotEmpty()) {
            checkContextAnnotations(context, null, null, variable, methodAnnotations, variable)
        }
    }

    /**
     * Extract the relevant annotations from the call and run the checks on the annotations.
     */
    private fun checkCall(
        context: JavaContext,
        method: PsiMethod,
        call: UCallExpression
    ) {
        val evaluator = context.evaluator
        val allAnnotations = evaluator.getAllAnnotations(method, inHierarchy = true)
        val methodAnnotations: List<UAnnotation> =
            filterRelevantAnnotations(evaluator, allAnnotations, call)

        // Look for annotations on the class as well: these trickle
        // down to all the methods in the class
        val containingClass: PsiClass? = method.containingClass
        val (classAnnotations, pkgAnnotations) = getClassAndPkgAnnotations(
            containingClass, evaluator, call
        )

        doCheckCall(
            context,
            method,
            methodAnnotations,
            classAnnotations,
            pkgAnnotations,
            call,
            containingClass
        )
    }

    /**
     * Extract the relevant annotations from the call and run the checks on the annotations.<p>
     *
     * This method is used in cases where UCallExpression.resolve() returns null. One important
     * case this happens is when an implicit, generated, default constructor (a class with no
     * explicit constructor in the source code) is called.
     */
    private fun checkCallUnresolved(
        context: JavaContext,
        call: UCallExpression
    ) {
        val evaluator = context.evaluator

        val allAnnotations = evaluator.getAllAnnotations(call, inHierarchy = true)
        val methodAnnotations: List<UAnnotation> =
            filterRelevantAnnotations(evaluator, allAnnotations)

        val containingClass = call.classReference?.resolve() as PsiClass?
        val (classAnnotations, pkgAnnotations) = getClassAndPkgAnnotations(
            containingClass, evaluator, call
        )
        doCheckCall(
            context,
            null,
            methodAnnotations,
            classAnnotations,
            pkgAnnotations,
            call,
            containingClass
        )
    }

    private fun getClassAndPkgAnnotations(
        containingClass: PsiClass?,
        evaluator: JavaEvaluator,
        call: UCallExpression
    ): Pair<List<UAnnotation>, List<UAnnotation>> {

        // Yes, returning a pair is ugly. But we are initializing two lists, and splitting this
        // into two methods takes more lines of code then it saves over copying this block into
        // two methods.
        // Plus, destructuring assignment makes using the results less verbose.
        val classAnnotations: List<UAnnotation>
        val pkgAnnotations: List<UAnnotation>

        if (containingClass != null) {
            val annotations = evaluator.getAllAnnotations(containingClass, inHierarchy = true)
            classAnnotations = filterRelevantAnnotations(evaluator, annotations, call)

            val pkg = evaluator.getPackage(containingClass)
            pkgAnnotations = if (pkg != null) {
                val annotations2 = evaluator.getAllAnnotations(pkg, inHierarchy = false)
                filterRelevantAnnotations(evaluator, annotations2)
            } else {
                emptyList()
            }
        } else {
            classAnnotations = emptyList()
            pkgAnnotations = emptyList()
        }

        return Pair(classAnnotations, pkgAnnotations)
    }

    /**
     * Do the checks of a call based on the method, class, and package annotations given.
     */
    private fun doCheckCall(
        context: JavaContext,
        method: PsiMethod?,
        methodAnnotations: List<UAnnotation>,
        classAnnotations: List<UAnnotation>,
        pkgAnnotations: List<UAnnotation>,
        call: UCallExpression,
        containingClass: PsiClass?
    ) {
        if (method != null && methodAnnotations.isNotEmpty()) {
            checkAnnotations(
                context,
                call,
                AnnotationUsageType.METHOD_CALL,
                method,
                method,
                methodAnnotations,
                methodAnnotations,
                classAnnotations,
                pkgAnnotations,
                method
            )

            checkContextAnnotations(context, method, method, call, methodAnnotations, method)
        }

        if (containingClass != null && !classAnnotations.isEmpty()) {
            checkAnnotations(
                context,
                call,
                AnnotationUsageType.METHOD_CALL_CLASS,
                method,
                method,
                classAnnotations,
                methodAnnotations,
                classAnnotations,
                pkgAnnotations,
                containingClass
            )
        }

        if (!pkgAnnotations.isEmpty()) {
            checkAnnotations(
                context,
                call,
                AnnotationUsageType.METHOD_CALL_PACKAGE,
                method,
                method,
                pkgAnnotations,
                methodAnnotations,
                classAnnotations,
                pkgAnnotations,
                null
            )
        }

        // Sadly, a null method means no parameter checks at this time.
        // Right now, computing the argument mapping expects a method object and cannot work based
        // off a raw UExpression.
        if (method != null) {
            val evaluator = context.evaluator
            val mapping = evaluator.computeArgumentMapping(call, method)
            for ((argument, parameter) in mapping) {
                val allParameterAnnotations =
                    evaluator.getAllAnnotations(parameter, inHierarchy = true)
                val filtered = filterRelevantAnnotations(evaluator, allParameterAnnotations, call)
                if (filtered.isEmpty()) {
                    continue
                }

                checkAnnotations(
                    context, argument, AnnotationUsageType.METHOD_CALL_PARAMETER, method,
                    method, filtered, methodAnnotations, classAnnotations, pkgAnnotations, method
                )
            }
        }
    }

    private fun filterRelevantAnnotations(
        evaluator: JavaEvaluator,
        annotations: Array<PsiAnnotation>,
        context: UElement? = null
    ): List<UAnnotation> {
        var result: MutableList<UAnnotation>? = null
        val length = annotations.size
        if (length == 0) {
            return emptyList()
        }
        for (annotation in annotations) {
            val signature = annotation.qualifiedName
            if (signature == null ||
                (
                    signature.startsWith("kotlin.") ||
                        signature.startsWith("java.")
                    ) && !relevantAnnotations.contains(signature)
            ) {
                // @Override, @SuppressWarnings etc. Ignore
                continue
            }

            if (relevantAnnotations.contains(signature)) {
                val uAnnotation = JavaUAnnotation.wrap(annotation)

                // Common case: there's just one annotation; no need to create a list copy
                if (length == 1) {
                    return listOf(uAnnotation)
                }
                if (result == null) {
                    result = ArrayList(2)
                }
                result.add(uAnnotation)
                continue
            }

            // Special case @IntDef and @StringDef: These are used on annotations
            // themselves. For example, you create a new annotation named @foo.bar.Baz,
            // annotate it with @IntDef, and then use @foo.bar.Baz in your signatures.
            // Here we want to map from @foo.bar.Baz to the corresponding int def.
            // Don't need to compute this if performing @IntDef or @StringDef lookup

            val cls = annotation.nameReferenceElement?.resolve() ?: run {
                val project = annotation.project
                JavaPsiFacade.getInstance(project).findClass(
                    signature,
                    GlobalSearchScope.projectScope(project)
                )
            } ?: continue
            if (cls !is PsiClass || !cls.isAnnotationType) {
                continue
            }
            val innerAnnotations = evaluator.getAllAnnotations(cls, inHierarchy = false)
            for (j in innerAnnotations.indices) {
                val inner = innerAnnotations[j]
                val a = inner.qualifiedName
                if (a != null && relevantAnnotations.contains(a)) {
                    if (result == null) {
                        result = ArrayList(2)
                    }
                    val innerU = annotationLookup.findRealAnnotation(inner, cls, context)
                    result.add(innerU)
                }
            }
        }

        return result ?: emptyList()
    }

    private fun filterRelevantAnnotations(
        evaluator: JavaEvaluator,
        annotations: List<UAnnotation>
    ): List<UAnnotation> {
        var result: MutableList<UAnnotation>? = null
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
            val cls = annotation.resolve()
            if (cls == null || !cls.isAnnotationType) {
                continue
            }
            val innerAnnotations = evaluator.getAllAnnotations(cls, inHierarchy = false)
            for (j in innerAnnotations.indices) {
                val inner = innerAnnotations[j]
                val a = inner.qualifiedName
                if (a != null && relevantAnnotations.contains(a)) {
                    if (result == null) {
                        result = ArrayList(2)
                    }
                    val innerU = annotationLookup.findRealAnnotation(inner, cls)
                    result.add(innerU)
                }
            }
        }

        return result ?: emptyList()
    }

    private val annotationLookup = AnnotationLookup()
}
