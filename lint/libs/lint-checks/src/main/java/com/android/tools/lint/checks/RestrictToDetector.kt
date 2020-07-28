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

import com.android.SdkConstants.ATTR_VALUE
import com.android.tools.lint.checks.AnnotationDetector.GMS_HIDE_ANNOTATION
import com.android.tools.lint.checks.AnnotationDetector.GUAVA_VISIBLE_FOR_TESTING
import com.android.tools.lint.checks.AnnotationDetector.RESTRICT_TO_ANNOTATION
import com.android.tools.lint.checks.AnnotationDetector.VISIBLE_FOR_TESTING_ANNOTATION
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.UastLintUtils
import com.android.tools.lint.detector.api.UastLintUtils.Companion.containsAnnotation
import com.android.tools.lint.detector.api.isKotlin
import com.intellij.lang.jvm.annotation.JvmAnnotationConstantValue
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.impl.compiled.ClsAnnotationImpl
import com.intellij.psi.util.PsiTypesUtil
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UTypeReferenceExpression
import org.jetbrains.uast.UastEmptyExpression
import org.jetbrains.uast.getContainingUFile
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.util.isArrayInitializer

class RestrictToDetector : AbstractAnnotationDetector(), SourceCodeScanner {
    override fun applicableAnnotations(): List<String> = listOf(
        RESTRICT_TO_ANNOTATION.oldName(),
        RESTRICT_TO_ANNOTATION.newName(),
        GMS_HIDE_ANNOTATION,
        VISIBLE_FOR_TESTING_ANNOTATION.oldName(),
        VISIBLE_FOR_TESTING_ANNOTATION.newName(),
        GUAVA_VISIBLE_FOR_TESTING
    )

    override fun inheritAnnotation(annotation: String): Boolean {
        // Require restriction annotations to be annotated everywhere
        return false
    }

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
        if (type == AnnotationUsageType.EXTENDS && usage is UTypeReferenceExpression) {
            // If it's a constructor reference we don't need to also check the type
            // reference. Ideally we'd do a "parent is KtConstructorCalleeExpression"
            // here, but that points to impl classes in its hierarchy which leads to
            // class loading trouble.
            val sourcePsi = usage.sourcePsi
            if (isKotlin(sourcePsi) && sourcePsi?.parent?.toString() == "CONSTRUCTOR_CALLEE") {
                return
            }
        }

        val member = method ?: referenced as? PsiMember
        when (qualifiedName) {
            RESTRICT_TO_ANNOTATION.oldName(), RESTRICT_TO_ANNOTATION.newName() -> {
                checkRestrictTo(
                    context, usage, member, annotation, allMemberAnnotations,
                    allClassAnnotations, true
                )
            }
            GMS_HIDE_ANNOTATION -> {
                val isConstructor = method == null || method.isConstructor
                val isStatic = if (method == null) false else context.evaluator.isStatic(method)
                checkRestrictTo(
                    context, usage, method, annotation, allMemberAnnotations,
                    allClassAnnotations, isConstructor || isStatic
                )
            }
            VISIBLE_FOR_TESTING_ANNOTATION.oldName(), VISIBLE_FOR_TESTING_ANNOTATION.newName(),
            GUAVA_VISIBLE_FOR_TESTING -> {
                if (member != null) {
                    checkVisibleForTesting(
                        context,
                        usage,
                        member,
                        annotation,
                        allMemberAnnotations,
                        allClassAnnotations
                    )
                }
            }
        }
    }

    // Checks whether the client code is in the GMS codebase; if so, allow @Hide calls
    // there
    private fun isGmsContext(
        context: JavaContext,
        element: UElement
    ): Boolean {
        val evaluator = context.evaluator
        val pkg = evaluator.getPackage(element) ?: return false

        val qualifiedName = pkg.qualifiedName
        if (!qualifiedName.startsWith("com.google.")) {
            return false
        }

        return qualifiedName.startsWith("com.google.firebase") ||
            qualifiedName.startsWith("com.google.android.gms") ||
            qualifiedName.startsWith("com.google.ads") ||
            qualifiedName.startsWith("com.google.mlkit")
    }

    private fun isTestContext(
        context: JavaContext,
        element: UElement
    ): Boolean {
        var current = element
        // (1) Is this compilation unit in a test source path?
        if (context.isTestSource) {
            return true
        }

        // (2) Is this AST node surrounded by a test-only annotation?
        while (true) {
            val owner = current.getParentOfType<UAnnotated>(true) ?: break

            //noinspection AndroidLintExternalAnnotations
            for (annotation in owner.uAnnotations) {
                val name = annotation.qualifiedName
                if (RESTRICT_TO_ANNOTATION.isEquals(name)) {
                    val restrictionScope = getRestrictionScope(annotation)
                    if (restrictionScope and RESTRICT_TO_TESTS != 0) {
                        return true
                    }
                } else if (VISIBLE_FOR_TESTING_ANNOTATION.isEquals(name)) {
                    return true
                }
            }

            current = owner
        }

        return false
    }

    private fun checkVisibleForTesting(
        context: JavaContext,
        node: UElement,
        method: PsiMember,
        annotation: UAnnotation,
        allMethodAnnotations: List<UAnnotation>,
        allClassAnnotations: List<UAnnotation>
    ) {

        val visibility = getVisibilityForTesting(annotation)
        if (visibility == VISIBILITY_NONE) { // not the default
            checkRestrictTo(
                context, node, method, annotation, allMethodAnnotations,
                allClassAnnotations, RESTRICT_TO_TESTS
            )
        } else {
            // Check that the target method is available
            // (1) private is available in the same compilation unit
            // (2) package private is available in the same package
            // (3) protected is available either from subclasses or in same package

            val uFile = node.getContainingUFile()
            val containingFile1 = UastLintUtils.getPsiFile(uFile)
            val containingFile2 = UastLintUtils.getContainingFile(method)
            if (containingFile1 == containingFile2 || containingFile2 == null) {
                // Same compilation unit
                return
            }

            // Validity check (since Kotlin UAST creates several light classes around
            // PSI files that sometimes fail equality tests)
            if (containingFile1?.virtualFile == containingFile2.virtualFile) {
                return
            }

            if (visibility == VISIBILITY_PRIVATE) {
                if (!isTestContext(context, node)) {
                    reportVisibilityError(context, node, "private")
                }
                return
            }

            val evaluator = context.evaluator
            val pkg = evaluator.getPackage(node)
            val methodPackage = evaluator.getPackage(method)
            // can't compare pkg == methodPackage because PsiPackageImpl#equals only returns
            // true for other instances of the exact same class
            if (pkg?.qualifiedName == methodPackage?.qualifiedName) {
                // Same package
                return
            }
            if (visibility == VISIBILITY_PACKAGE_PRIVATE) {
                if (!isTestContext(context, node)) {
                    reportVisibilityError(context, node, "package private")
                }
                return
            }

            assert(visibility == VISIBILITY_PROTECTED)

            val methodClass = method.containingClass
            val thisClass = node.getParentOfType<UClass>(UClass::class.java, true)
            if (thisClass == null || methodClass == null) {
                return
            }
            val qualifiedName = methodClass.qualifiedName
            if (qualifiedName == null || evaluator.inheritsFrom(
                thisClass,
                qualifiedName,
                false
            )
            ) {
                return
            }

            if (!isTestContext(context, node)) {
                reportVisibilityError(context, node, "protected")
            }
        }
    }

    private fun reportVisibilityError(
        context: JavaContext,
        node: UElement,
        desc: String
    ) {
        val message = "This method should only be accessed from tests or within $desc scope"
        val location: Location = if (node is UCallExpression) {
            context.getCallLocation(node, false, false)
        } else {
            context.getLocation(node)
        }

        report(context, TEST_VISIBILITY, node, location, message)
    }

    // TODO: Test XML access of restricted classes
    private fun checkRestrictTo(
        context: JavaContext,
        node: UElement,
        method: PsiMember?,
        annotation: UAnnotation,
        allMethodAnnotations: List<UAnnotation>,
        allClassAnnotations: List<UAnnotation>,
        applyClassAnnotationsToMembers: Boolean = true
    ) {
        val scope = getRestrictionScope(annotation)
        if (scope != 0) {
            checkRestrictTo(
                context, node, method, annotation, allMethodAnnotations,
                allClassAnnotations, scope, applyClassAnnotationsToMembers
            )
        }
    }

    private fun checkRestrictTo(
        context: JavaContext,
        node: UElement,
        member: PsiMember?,
        annotation: UAnnotation,
        allMethodAnnotations: List<UAnnotation>,
        allClassAnnotations: List<UAnnotation>,
        scope: Int,
        applyClassAnnotationsToMembers: Boolean = true
    ) {

        val containingClass = when {
            node is UTypeReferenceExpression -> PsiTypesUtil.getPsiClass(node.type)
            member != null -> member.containingClass
            node is UCallExpression -> node.classReference?.resolve() as PsiClass?
            node is PsiClass -> node
            else -> null
        }

        containingClass ?: return

        var isClassAnnotation = false
        if (containsAnnotation(allMethodAnnotations, annotation)) {
            // Make sure that the annotation is *not* inherited.
            // For example, NavigationView (a public, exposed class) extends ScrimInsetsFrameLayout, which
            // is a restricted class. We don't want to make all uses of NavigationView to suddenly be
            // treated as Restricted just because it inherits code from a restricted API.
            if (member != null && context.evaluator.isInherited(annotation, member)) {
                return
            }
        } else if (applyClassAnnotationsToMembers) {
            // Found restriction or class or package: make sure we only check on the most
            // specific scope, otherwise we report the same error multiple times
            // or report errors on restrictions that have been redefined
            if (containsRestrictionAnnotation(allMethodAnnotations)) {
                return
            }
            isClassAnnotation = containsAnnotation(allClassAnnotations, annotation)
            if (isClassAnnotation) {
                if (context.evaluator.isInherited(annotation, containingClass)) {
                    return
                }
            } else if (containsRestrictionAnnotation(allClassAnnotations)) {
                return
            }
        } else { // not in member annotations and applyClassAnnotationToMembers is false.
            return
        }

        if (scope and RESTRICT_TO_LIBRARY_GROUP != 0 && member != null) {
            val evaluator = context.evaluator
            val thisCoordinates = evaluator.getLibrary(node) ?: context.project.mavenCoordinate
            val methodCoordinates = evaluator.getLibrary(member) ?: run {
                if (thisCoordinates != null && member !is PsiCompiledElement) {
                    // Local source?
                    context.evaluator.getProject(member)?.mavenCoordinate
                } else {
                    null
                }
            }
            val thisGroup = thisCoordinates?.groupId
            val methodGroup = methodCoordinates?.groupId
            if (thisGroup != methodGroup && methodGroup != null) {
                val where = "from within the same library group (groupId=$methodGroup)"
                reportRestriction(
                    where, containingClass, member, context,
                    node, isClassAnnotation
                )
            }
        } else if (scope and RESTRICT_TO_LIBRARY_GROUP_PREFIX != 0 && member != null) {
            val evaluator = context.evaluator
            val thisCoordinates = evaluator.getLibrary(node) ?: context.project.mavenCoordinate
            val methodCoordinates = evaluator.getLibrary(member) ?: run {
                if (thisCoordinates != null && member !is PsiCompiledElement) {
                    // Local source?
                    context.evaluator.getProject(member)?.mavenCoordinate
                } else {
                    null
                }
            }
            val thisGroup = thisCoordinates?.groupId
            val methodGroup = methodCoordinates?.groupId
            if (methodGroup != null &&
                (thisGroup == null || !sameLibraryGroupPrefix(thisGroup, methodGroup))
            ) {
                val expectedPrefix = methodGroup.substring(0, methodGroup.lastIndexOf('.'))
                val where =
                    "from within the same library group prefix (referenced groupId=`$methodGroup` with prefix $expectedPrefix${if (thisGroup != null) " from groupId=`$thisGroup`" else ""})"
                reportRestriction(
                    where, containingClass, member, context,
                    node, isClassAnnotation
                )
            }
        } else if (scope and RESTRICT_TO_LIBRARY != 0 && member != null) {
            val evaluator = context.evaluator
            val thisCoordinates = evaluator.getLibrary(node) ?: context.project.mavenCoordinate
            val methodCoordinates = evaluator.getLibrary(member)
            val thisGroup = thisCoordinates?.groupId
            val methodGroup = methodCoordinates?.groupId
            if (thisGroup != methodGroup && methodGroup != null) {
                val thisArtifact = thisCoordinates?.artifactId
                val methodArtifact = methodCoordinates.artifactId
                if (thisArtifact != methodArtifact) {
                    val where = "from within the same library ($methodGroup:$methodArtifact)"
                    reportRestriction(
                        where, containingClass, member, context,
                        node, isClassAnnotation
                    )
                }
            } else if (member !is PsiCompiledElement) {
                // If the resolved method is source, make sure they're part
                // of the same Gradle project
                val project = context.evaluator.getProject(member)
                if (project != null && project != context.project) {
                    val coordinates = project.mavenCoordinate
                    val name = if (coordinates != null) {
                        "${coordinates.groupId}:${coordinates.artifactId}"
                    } else {
                        project.name
                    }
                    val where = "from within the same library ($name)"
                    reportRestriction(
                        where, containingClass, member, context,
                        node, isClassAnnotation
                    )
                }
            }
        }

        if (scope and RESTRICT_TO_TESTS != 0) {
            if (!isTestContext(context, node)) {
                reportRestriction(
                    "from tests", containingClass, member, context,
                    node, isClassAnnotation
                )
            }
        }

        if (scope and RESTRICT_TO_ALL != 0) {
            if (!isGmsContext(context, node)) {
                reportRestriction(
                    null, containingClass, member, context,
                    node, isClassAnnotation
                )
            }
        }

        if (scope and RESTRICT_TO_SUBCLASSES != 0) {
            val qualifiedName = containingClass.qualifiedName
            if (qualifiedName != null) {
                val evaluator = context.evaluator

                var outer: UClass?
                var isSubClass = false
                var prev = node

                while (true) {
                    outer = prev.getParentOfType(UClass::class.java, true)
                    if (outer == null) {
                        break
                    }
                    if (evaluator.inheritsFrom(outer, qualifiedName, false)) {
                        isSubClass = true
                        break
                    }

                    if (evaluator.isStatic(outer)) {
                        break
                    }
                    prev = outer
                }

                if (!isSubClass) {
                    reportRestriction(
                        "from subclasses", containingClass, member,
                        context, node, isClassAnnotation
                    )
                }
            }
        }
    }

    private fun reportRestriction(
        where: String?,
        containingClass: PsiClass,
        member: PsiMember?,
        context: JavaContext,
        node: UElement,
        isClassAnnotation: Boolean
    ) {
        var api: String
        api = if (member == null || member is PsiMethod && member.isConstructor) {
            member?.name ?: containingClass.name + " constructor"
        } else if (containingClass == member) {
            member.name ?: "class"
        } else {
            containingClass.name + "." + member.name
        }

        var locationNode = node
        if (node is UCallExpression) {
            val nameElement = node.methodIdentifier
            if (nameElement != null) {
                locationNode = nameElement
            }

            // If the annotation was reported on the class, and the left hand side
            // expression is that class, use it as the name node?
            if (isClassAnnotation) {
                val qualifier = node.receiver
                val className = containingClass.name
                if (qualifier != null && className != null && qualifier.asSourceString() == className) {
                    locationNode = qualifier
                    api = className
                }
            }
        }

        // If this error message changes, you need to also update ResourceTypeInspection#guessLintIssue
        var message: String
        if (where == null) {
            message = "$api is marked as internal and should not be accessed from apps"
        } else {
            val refType = if (member is PsiMethod) "called" else "accessed"
            message = "$api can only be $refType $where"

            // Most users will encounter this for the support library; let's have a clearer error message
            // for that specific scenario
            if (where == "from within the same library (groupId=com.android.support)") {
                // If this error message changes, you need to also update ResourceTypeInspection#guessLintIssue
                message =
                    "This API is marked as internal to the support library and should not be accessed from apps"
            }
        }

        val location: Location
        location = if (locationNode is UCallExpression) {
            context.getCallLocation(locationNode, false, false)
        } else {
            context.getLocation(locationNode)
        }
        report(context, RESTRICTED, node, location, message, null)
    }

    private fun containsRestrictionAnnotation(list: List<UAnnotation>): Boolean {
        return containsAnnotation(
            list,
            RESTRICT_TO_ANNOTATION.oldName()
        ) || containsAnnotation(list, RESTRICT_TO_ANNOTATION.newName())
    }

    companion object {
        private val IMPLEMENTATION = Implementation(
            RestrictToDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )

        private const val ATTR_OTHERWISE = "otherwise"
        private const val ATTR_PRODUCTION_VISIBILITY = "productionVisibility"

        // Must match constants in @VisibleForTesting:
        private const val VISIBILITY_PRIVATE = 2
        private const val VISIBILITY_PACKAGE_PRIVATE = 3
        private const val VISIBILITY_PROTECTED = 4
        private const val VISIBILITY_NONE = 5
        // TODO: Kotlin "module" visibility

        private fun getVisibilityForTesting(annotation: UAnnotation): Int {
            val value = annotation.findDeclaredAttributeValue(ATTR_OTHERWISE)
                // Guava within Google3:
                ?: annotation.findDeclaredAttributeValue(ATTR_PRODUCTION_VISIBILITY)
            if (value is ULiteralExpression) {
                val v = value.value
                if (v is Int) {
                    return (v as Int?)!!
                }
            } else if (value is UReferenceExpression) {
                // Not compiled; this is unlikely (but can happen when editing the support
                // library project itself)
                val name = value.resolvedName
                when (name) {
                    "NONE" -> return VISIBILITY_NONE
                    "PRIVATE" -> return VISIBILITY_PRIVATE
                    "PROTECTED" -> return VISIBILITY_PROTECTED
                    "PACKAGE_PRIVATE" -> return VISIBILITY_PACKAGE_PRIVATE
                }
            } else if (value is UastEmptyExpression) {
                // Some kind of error in UAST; try harder. JavaUAnnotation is used to wrap
                // class file annotations and in findDeclaredAttributeValue it returns
                // UastEmptyExpression if it cannot convert it to UAST.
                val psi = annotation.sourcePsi
                if (psi is ClsAnnotationImpl) {
                    val otherwise = psi.findAttribute(ATTR_OTHERWISE)
                        ?: psi.findAttribute(ATTR_PRODUCTION_VISIBILITY)
                    val v = otherwise?.attributeValue
                    if (v is JvmAnnotationConstantValue) {
                        val constant = v.constantValue
                        if (constant is Number) {
                            return constant.toInt()
                        }
                    }
                }
            }

            return VISIBILITY_PRIVATE // the default
        }

        /** `RestrictTo(RestrictTo.Scope.GROUP_ID`  */
        private const val RESTRICT_TO_LIBRARY_GROUP = 1 shl 0

        /** `RestrictTo(RestrictTo.Scope.GROUP_ID`  */
        private const val RESTRICT_TO_LIBRARY = 1 shl 1

        /** `RestrictTo(RestrictTo.Scope.GROUP_ID`  */
        private const val RESTRICT_TO_LIBRARY_GROUP_PREFIX = 1 shl 2

        /** `RestrictTo(RestrictTo.Scope.TESTS`  */
        private const val RESTRICT_TO_TESTS = 1 shl 3

        /** `RestrictTo(RestrictTo.Scope.SUBCLASSES`  */
        private const val RESTRICT_TO_SUBCLASSES = 1 shl 4
        private const val RESTRICT_TO_ALL = 1 shl 5

        private fun getRestrictionScope(annotation: UAnnotation): Int {
            val value = annotation.findDeclaredAttributeValue(ATTR_VALUE)
            if (value != null) {
                return getRestrictionScope(value, annotation)
            } else if (GMS_HIDE_ANNOTATION == annotation.qualifiedName) {
                return RESTRICT_TO_ALL
            }
            return 0
        }

        private fun getRestrictionScope(expression: UExpression?, annotation: UAnnotation): Int {
            var scope = 0
            if (expression != null) {
                if (expression.isArrayInitializer()) {
                    val initializerExpression = expression as UCallExpression?
                    val initializers = initializerExpression!!.valueArguments
                    for (initializer in initializers) {
                        scope = scope or getRestrictionScope(initializer, annotation)
                    }
                } else if (expression is UReferenceExpression) {
                    val resolved = expression.resolve()
                    if (resolved is PsiField) {
                        val name = resolved.name
                        if ("GROUP_ID" == name || "LIBRARY_GROUP" == name) {
                            scope = scope or RESTRICT_TO_LIBRARY_GROUP
                        } else if ("SUBCLASSES" == name) {
                            scope = scope or RESTRICT_TO_SUBCLASSES
                        } else if ("TESTS" == name) {
                            scope = scope or RESTRICT_TO_TESTS
                        } else if ("LIBRARY" == name) {
                            scope = scope or RESTRICT_TO_LIBRARY
                        } else if ("LIBRARY_GROUP_PREFIX" == name) {
                            scope = scope or RESTRICT_TO_LIBRARY_GROUP_PREFIX
                        }
                    }
                } else if (expression is UastEmptyExpression) {
                    // See JavaUAnnotation.findDeclaredAttributeValue
                    val psi = annotation.sourcePsi
                    if (psi is ClsAnnotationImpl) {
                        val otherwise = psi.findAttribute(ATTR_VALUE)
                        val v = otherwise?.attributeValue
                        if (v is JvmAnnotationConstantValue) {
                            val constant = v.constantValue
                            if (constant is Number) {
                                scope = scope or constant.toInt()
                            }
                        }
                    }
                }
            }

            return scope
        }

        /**
         * Implements the group prefix equality that is described in the documentation
         * for the RestrictTo.Scope.LIBRARY_GROUP_PREFIX enum constant
         */
        fun sameLibraryGroupPrefix(group1: String, group2: String): Boolean {
            // TODO: Allow group1.startsWith(group2) || group2.startsWith(group1) ?

            if (group1 == group2) {
                return true
            }

            val i1 = group1.lastIndexOf('.')
            val i2 = group2.lastIndexOf('.')
            if (i2 != i1 || i1 == -1) {
                return false
            }

            return group1.regionMatches(0, group2, 0, i1)
        }

        /** Using a restricted API */
        @JvmField
        val RESTRICTED = Issue.create(
            id = "RestrictedApi",
            briefDescription = "Restricted API",
            explanation =
                """
                This API has been flagged with a restriction that has not been met.

                Examples of API restrictions:
                * Method can only be invoked by a subclass
                * Method can only be accessed from within the same library (defined by the Gradle library group id)
                * Method can only be accessed from tests.

                You can add your own API restrictions with the `@RestrictTo` annotation.""",
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION
        )

        /** Using an intended-for-tests API */
        @JvmField
        val TEST_VISIBILITY = Issue.create(
            id = "VisibleForTests",
            briefDescription = "Visible Only For Tests",
            explanation =
                """
                With the `@VisibleForTesting` annotation you can specify an `otherwise=` \
                attribute which specifies the intended visibility if the method had not \
                been made more widely visible for the tests.

                This check looks for accesses from production code (e.g. not tests) where \
                the access would not have been allowed with the intended production \
                visibility.""",
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION
        )
    }
}
