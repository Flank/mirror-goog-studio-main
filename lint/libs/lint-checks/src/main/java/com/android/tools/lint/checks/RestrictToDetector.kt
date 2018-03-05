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
import com.android.tools.lint.detector.api.UastLintUtils.containsAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UReferenceExpression
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
            allPackageAnnotations: List<UAnnotation>) {

        when (qualifiedName) {
            RESTRICT_TO_ANNOTATION.oldName(), RESTRICT_TO_ANNOTATION.newName(),
            GMS_HIDE_ANNOTATION -> {
                if (method != null) {
                    checkRestrictTo(context, usage, method, annotation, allMemberAnnotations,
                            allClassAnnotations)
                }
            }
            VISIBLE_FOR_TESTING_ANNOTATION.oldName(), VISIBLE_FOR_TESTING_ANNOTATION.newName(),
            GUAVA_VISIBLE_FOR_TESTING -> {
                if (method != null) {
                    checkVisibleForTesting(context,
                            usage,
                            method,
                            annotation,
                            allMemberAnnotations,
                            allClassAnnotations)
                }
            }
        }
    }

    // Checks whether the client code is in the GMS codebase; if so, allow @Hide calls
    // there
    private fun isGmsContext(
            context: JavaContext,
            element: UElement): Boolean {
        val evaluator = context.evaluator
        val pkg = evaluator.getPackage(element) ?: return false

        val qualifiedName = pkg.qualifiedName
        if (!qualifiedName.startsWith("com.google.")) {
            return false
        }

        return qualifiedName.startsWith("com.google.firebase") ||
                qualifiedName.startsWith("com.google.android.gms") ||
                qualifiedName.startsWith("com.google.ads")
    }

    private fun isTestContext(
            context: JavaContext,
            element: UElement): Boolean {
        var current = element
        // (1) Is this compilation unit in a test source path?
        if (context.isTestSource) {
            return true
        }

        // (2) Is this AST node surrounded by a test-only annotation?
        while (true) {
            val owner = current.getParentOfType<UAnnotated>(
                    UAnnotated::class.java, true) ?: break

            for (annotation in owner.annotations) {
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
            method: PsiMethod,
            annotation: UAnnotation,
            allMethodAnnotations: List<UAnnotation>,
            allClassAnnotations: List<UAnnotation>) {

        val visibility = getVisibilityForTesting(annotation)
        if (visibility == VISIBILITY_NONE) { // not the default
            checkRestrictTo(context, node, method, annotation, allMethodAnnotations,
                    allClassAnnotations, RESTRICT_TO_TESTS)
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

            // Sanity check (since Kotlin UAST creates several light classes around
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
            if (pkg == methodPackage) {
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
            if (qualifiedName == null || evaluator.inheritsFrom(thisClass,
                    qualifiedName,
                    false)) {
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
            desc: String) {
        val message = String.format("This method should only be accessed from tests "
                //+ "(intended visibility is %1$s)", desc);
                + "or within %1\$s scope", desc)
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
            method: PsiMethod,
            annotation: UAnnotation,
            allMethodAnnotations: List<UAnnotation>,
            allClassAnnotations: List<UAnnotation>) {
        val scope = getRestrictionScope(annotation)
        if (scope != 0) {
            checkRestrictTo(context, node, method, annotation, allMethodAnnotations,
                    allClassAnnotations, scope)
        }
    }

    private fun checkRestrictTo(
            context: JavaContext,
            node: UElement,
            method: PsiMethod,
            annotation: UAnnotation,
            allMethodAnnotations: List<UAnnotation>,
            allClassAnnotations: List<UAnnotation>,
            scope: Int) {

        val containingClass = method.containingClass ?: return

        var isClassAnnotation = false
        if (containsAnnotation(allMethodAnnotations, annotation)) {
            // Make sure that the annotation is *not* inherited.
            // For example, NavigationView (a public, exposed class) extends ScrimInsetsFrameLayout, which
            // is a restricted class. We don't want to make all uses of NavigationView to suddenly be
            // treated as Restricted just because it inherits code from a restricted API.
            if (context.evaluator.isInherited(annotation, method)) {
                return
            }
        } else {
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
        }

        if (scope and RESTRICT_TO_LIBRARY_GROUP != 0) {
            val evaluator = context.evaluator
            val thisCoordinates = evaluator.getLibrary(node)
            val methodCoordinates = evaluator.getLibrary(method)
            val thisGroup = thisCoordinates?.groupId
            val methodGroup = methodCoordinates?.groupId
            if (thisGroup != methodGroup && methodGroup != null) {
                val where = String.format("from within the same library group " + "(groupId=%1\$s)",
                        methodGroup)
                reportRestriction(where, containingClass, method, context,
                        node, isClassAnnotation)
            }
        }

        if (scope and RESTRICT_TO_LIBRARY != 0) {
            val evaluator = context.evaluator
            val thisCoordinates = evaluator.getLibrary(node)
            val methodCoordinates = evaluator.getLibrary(method)
            val thisGroup = thisCoordinates?.groupId
            val methodGroup = methodCoordinates?.groupId
            if (thisGroup != methodGroup && methodGroup != null) {
                val thisArtifact = thisCoordinates?.artifactId
                val methodArtifact = methodCoordinates.artifactId
                if (thisArtifact != methodArtifact) {
                    val where = String.format("from within the same library " + "(%1\$s:%2\$s)",
                            methodGroup,
                            methodArtifact)
                    reportRestriction(where, containingClass, method, context,
                            node, isClassAnnotation)
                }
            }
        }

        if (scope and RESTRICT_TO_TESTS != 0) {
            if (!isTestContext(context, node)) {
                reportRestriction("from tests", containingClass, method, context,
                        node, isClassAnnotation)
            }
        }

        if (scope and RESTRICT_TO_ALL != 0) {
            if (!isGmsContext(context, node)) {
                reportRestriction(null, containingClass, method, context,
                        node, isClassAnnotation)
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
                    reportRestriction("from subclasses", containingClass, method,
                            context, node, isClassAnnotation)
                }
            }
        }
    }

    private fun reportRestriction(
            where: String?,
            containingClass: PsiClass,
            method: PsiMethod,
            context: JavaContext,
            node: UElement,
            isClassAnnotation: Boolean) {
        var api: String
        api = if (method.isConstructor) {
            method.name + " constructor"
        } else {
            containingClass.name + "." + method.name
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
            message = api + " is marked as internal and should not be accessed from apps"
        } else {
            message = api + " can only be called " + where

            // Most users will encounter this for the support library; let's have a clearer error message
            // for that specific scenario
            if (where == "from within the same library (groupId=com.android.support)") {
                // If this error message changes, you need to also update ResourceTypeInspection#guessLintIssue
                message = "This API is marked as internal to the support library and should not be accessed from apps"
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
        return containsAnnotation(list, RESTRICT_TO_ANNOTATION.oldName()) || containsAnnotation(list, RESTRICT_TO_ANNOTATION.newName())
    }

    companion object {
        private val IMPLEMENTATION = Implementation(RestrictToDetector::class.java,
                Scope.JAVA_FILE_SCOPE)

        private const val ATTR_OTHERWISE = "otherwise"

        // Must match constants in @VisibleForTesting:
        private const val VISIBILITY_PRIVATE = 2
        private const val VISIBILITY_PACKAGE_PRIVATE = 3
        private const val VISIBILITY_PROTECTED = 4
        private const val VISIBILITY_NONE = 5
        // TODO: Kotlin "module" visibility

        private fun getVisibilityForTesting(annotation: UAnnotation): Int {
            val value = annotation.findDeclaredAttributeValue(ATTR_OTHERWISE)
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
            }

            return VISIBILITY_PRIVATE // the default
        }

        /** `RestrictTo(RestrictTo.Scope.GROUP_ID`  */
        private val RESTRICT_TO_LIBRARY_GROUP = 1 shl 0
        /** `RestrictTo(RestrictTo.Scope.GROUP_ID`  */
        private val RESTRICT_TO_LIBRARY = 1 shl 1
        /** `RestrictTo(RestrictTo.Scope.TESTS`  */
        private val RESTRICT_TO_TESTS = 1 shl 2
        /** `RestrictTo(RestrictTo.Scope.SUBCLASSES`  */
        private val RESTRICT_TO_SUBCLASSES = 1 shl 3
        private val RESTRICT_TO_ALL = 1 shl 4

        private fun getRestrictionScope(annotation: UAnnotation): Int {
            val value = annotation.findDeclaredAttributeValue(ATTR_VALUE)
            if (value != null) {
                return getRestrictionScope(value)
            } else if (GMS_HIDE_ANNOTATION == annotation.qualifiedName) {
                return RESTRICT_TO_ALL
            }
            return 0
        }

        private fun getRestrictionScope(expression: UExpression?): Int {
            var scope = 0
            if (expression != null) {
                if (expression.isArrayInitializer()) {
                    val initializerExpression = expression as UCallExpression?
                    val initializers = initializerExpression!!.valueArguments
                    for (initializer in initializers) {
                        scope = scope or getRestrictionScope(initializer)
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
                        }
                    }
                }
            }

            return scope
        }

        /** Using a restricted API */
        @JvmField
        val RESTRICTED = Issue.create(
                "RestrictedApi",
                "Restricted API",

                "This API has been flagged with a restriction that has not been met.\n" +
                        "\n" +
                        "Examples of API restrictions:\n" +
                        "* Method can only be invoked by a subclass\n" +
                        "* Method can only be accessed from within the same library (defined by " +
                        " the Gradle library group id)\n." +
                        "* Method can only be accessed from tests.\n." +
                        "\n" +
                        "You can add your own API restrictions with the `@RestrictTo` annotation.",

                Category.CORRECTNESS,
                4,
                Severity.ERROR,
                IMPLEMENTATION)

        /** Using an intended-for-tests API */
        @JvmField
        val TEST_VISIBILITY = Issue.create(
                "VisibleForTests",
                "Visible Only For Tests",

                "With the `@VisibleForTesting` annotation you can specify an `otherwise=` " +
                        "attribute which specifies the intended visibility if the method had not " +
                        "been made more widely visible for the tests.\n" +
                        "\n" +
                        "This check looks for accesses from production code (e.g. not tests) where " +
                        "the access would not have been allowed with the intended production visibility.",

                Category.CORRECTNESS,
                4,
                Severity.WARNING,
                IMPLEMENTATION)
    }
}