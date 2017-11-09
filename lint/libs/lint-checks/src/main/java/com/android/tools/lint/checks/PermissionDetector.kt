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

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_REQUIRED
import com.android.SdkConstants.CLASS_INTENT
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_PERMISSION
import com.android.SdkConstants.TAG_USES_LIBRARY
import com.android.SdkConstants.TAG_USES_PERMISSION
import com.android.SdkConstants.TAG_USES_PERMISSION_SDK_23
import com.android.SdkConstants.TAG_USES_PERMISSION_SDK_M
import com.android.SdkConstants.VALUE_FALSE
import com.android.tools.lint.checks.AnnotationDetector.PERMISSION_ANNOTATION
import com.android.tools.lint.checks.AnnotationDetector.PERMISSION_ANNOTATION_READ
import com.android.tools.lint.checks.AnnotationDetector.PERMISSION_ANNOTATION_WRITE
import com.android.tools.lint.checks.AnnotationDetector.SECURITY_EXCEPTION
import com.android.tools.lint.checks.PermissionFinder.Operation.ACTION
import com.android.tools.lint.checks.PermissionFinder.Operation.READ
import com.android.tools.lint.checks.PermissionFinder.Operation.WRITE
import com.android.tools.lint.checks.PermissionRequirement.ATTR_PROTECTION_LEVEL
import com.android.tools.lint.checks.PermissionRequirement.VALUE_DANGEROUS
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.utils.XmlUtils
import com.google.common.collect.Sets
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UTryExpression
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.util.isMethodCall
import org.jetbrains.uast.visitor.AbstractUastVisitor
import java.util.Arrays

class PermissionDetector : AbstractAnnotationDetector(), Detector.UastScanner {
    override fun applicableAnnotations(): List<String> = listOf(
            PERMISSION_ANNOTATION,
            PERMISSION_ANNOTATION_READ,
            PERMISSION_ANNOTATION_WRITE
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
        if (annotations === allMemberAnnotations) {
            // Permission annotation specified on method:
            val requirement = PermissionRequirement.create(annotation)
            checkPermission(context, argument, method, null, requirement)
        } else {
            // PERMISSION_ANNOTATION, PERMISSION_ANNOTATION_READ, PERMISSION_ANNOTATION_WRITE
            // When specified on a parameter, that indicates that we're dealing with
            // a permission requirement on this *method* which depends on the value
            // supplied by this parameter
            if (argument is UExpression && method != null) {
                checkParameterPermission(context, qualifiedName, method, argument)
            }
        }
    }

    private fun checkParameterPermission(
            context: JavaContext,
            signature: String,
            method: PsiMethod,
            argument: UExpression) {
        var operation: PermissionFinder.Operation? = null

        if (signature == PERMISSION_ANNOTATION_READ) {
            operation = READ
        } else if (signature == PERMISSION_ANNOTATION_WRITE) {
            operation = WRITE
        } else {
            val type = argument.getExpressionType()
            if (type != null && CLASS_INTENT == type.canonicalText) {
                operation = ACTION
            }
        }
        if (operation == null) {
            return
        }
        val result = PermissionFinder.findRequiredPermissions(context, operation, argument)
        if (result != null) {
            checkPermission(context, argument, method, result, result.requirement)
        }
    }

    private fun checkPermission(
            context: JavaContext,
            node: UElement,
            method: PsiMethod?,
            result: PermissionFinder.Result?,
            requirement: PermissionRequirement) {
        if (requirement.isConditional) {
            return
        }
        var permissions = getPermissions(context)
        if (!requirement.isSatisfied(permissions)) {
            // See if it looks like we're holding the permission implicitly by @RequirePermission
            // annotations in the surrounding context
            permissions = addLocalPermissions(permissions, node)
            if (!requirement.isSatisfied(permissions)) {
                val operation: PermissionFinder.Operation
                val name: String
                if (result != null) {
                    name = result.name
                    operation = result.operation
                } else {
                    assert(method != null)
                    val containingClass = method!!.containingClass
                    name = if (containingClass != null) {
                        containingClass.name + "." + method.name
                    } else {
                        method.name
                    }
                    operation = PermissionFinder.Operation.CALL
                }
                val message = String.format(
                        "Missing permissions required %1\$s %2\$s: %3\$s", operation.prefix(),
                        name, requirement.describeMissingPermissions(permissions))
                // Report locations on the call, not just the flown parameter
                var location = context.getLocation(node)
                val expressionNode = node.getParentOfType<UCallExpression>(UCallExpression::class.java, true)
                if (expressionNode != null) {
                    val callIdentifier = expressionNode.methodIdentifier
                    if (callIdentifier != null && callIdentifier != node) {
                        location = context.getRangeLocation(callIdentifier, 0, node, 0)
                    }
                }

                report(context, MISSING_PERMISSION, node, location, message,
                        // Pass data to IDE quickfix: names to add, and max applicable API version
                        fix().data(requirement.getMissingPermissions(permissions),
                                requirement.lastApplicableApi))
            }
        } else if (requirement.isRevocable(permissions)
                && context.mainProject.targetSdkVersion.featureLevel >= 23
                && requirement.lastApplicableApi >= 23
                && !isAndroidThingsProject(context)) {

            var handlesMissingPermission = handlesSecurityException(node)

            // If not, check to see if the code is deliberately checking to see if the
            // given permission is available.
            if (!handlesMissingPermission) {

                val methodNode = node.getParentOfType<UMethod>(UMethod::class.java, true)
                if (methodNode != null) {
                    val visitor = CheckPermissionVisitor(node)
                    methodNode.accept(visitor)
                    handlesMissingPermission = visitor.checksPermission()
                }
            }

            if (!handlesMissingPermission) {
                val message = "Call requires permission which may be rejected by user: code should explicitly " +
                "check to see if permission is available (with `checkPermission`) or explicitly " +
                "handle a potential `SecurityException`"
                val location = context.getLocation(node)
                report(context, MISSING_PERMISSION, node, location, message,
                        // Pass data to IDE quickfix: revocable names, and permission requirement
                        fix().data(requirement.getRevocablePermissions(permissions),
                                requirement))
            }
        }
    }

    private fun handlesSecurityException(node: UElement): Boolean {
        // Ensure that the caller is handling a security exception
        // First check to see if we're inside a try/catch which catches a SecurityException
        // (or some wider exception than that). Check for nested try/catches too.
        var parent = node
        while (true) {
            val tryCatch = parent.getParentOfType<UTryExpression>(UTryExpression::class.java, true)
            if (tryCatch == null) {
                break
            } else {
                for (catchClause in tryCatch.catchClauses) {
                    if (containsSecurityException(catchClause.types)) {
                        return true
                    }
                }

                parent = tryCatch
            }
        }

        // If not, check to see if the method itself declares that it throws a
        // SecurityException or something wider.
        val declaration = parent.getParentOfType<UMethod>(UMethod::class.java, false)
        if (declaration != null) {
            val thrownTypes = declaration.throwsList.referencedTypes
            if (containsSecurityException(Arrays.asList<PsiClassType>(*thrownTypes))) {
                return true
            }
        }

        return false
    }

    private fun addLocalPermissions(
            permissions: PermissionHolder,
            node: UElement): PermissionHolder {
        var merged = permissions
        // Accumulate @RequirePermissions available in the local context
        val method = node.getParentOfType<UMethod>(UMethod::class.java, true) ?: return merged
        var annotation = method.findAnnotation(PERMISSION_ANNOTATION)
        merged = mergeAnnotationPermissions(merged, annotation)

        val containingClass = method.getContainingUClass()
        if (containingClass != null) {
            annotation = containingClass.findAnnotation(PERMISSION_ANNOTATION)
            merged = mergeAnnotationPermissions(merged, annotation)
        }
        return merged
    }

    private fun mergeAnnotationPermissions(
            permissions: PermissionHolder,
            annotation: UAnnotation?): PermissionHolder {
        var merged = permissions
        if (annotation != null) {
            val requirement = PermissionRequirement.create(annotation)
            merged = PermissionHolder.SetPermissionLookup.join(merged, requirement)
        }

        return merged
    }

    /**
     * Visitor which looks through a method, up to a given call (the one requiring a
     * permission) and checks whether it's preceded by a call to checkPermission or
     * checkCallingPermission or enforcePermission etc.
     *
     *
     * Currently it only looks for the presence of this check; it does not perform
     * flow analysis to determine whether the check actually affects program flow
     * up to the permission call, or whether the check permission is checking for
     * permissions sufficient to satisfy the permission requirement of the target call,
     * or whether the check return value (== PERMISSION_GRANTED vs != PERMISSION_GRANTED)
     * is handled correctly, etc.
     */
    private class CheckPermissionVisitor(private val mTarget: UElement) : AbstractUastVisitor() {
        private var mChecksPermission: Boolean = false
        private var mDone: Boolean = false

        override fun visitElement(node: UElement): Boolean {
            return mDone || super.visitElement(node)
        }

        override fun visitCallExpression(node: UCallExpression): Boolean {
            if (node.isMethodCall()) {
                visitMethodCallExpression(node)
            }
            return super.visitCallExpression(node)
        }

        private fun visitMethodCallExpression(node: UCallExpression) {
            if (node === mTarget) {
                mDone = true
            }

            val name = node.methodName
            if (name != null
                    && (name.startsWith("check") || name.startsWith("enforce"))
                    && name.endsWith("Permission")) {
                mChecksPermission = true
                mDone = true
            }
        }

        fun checksPermission(): Boolean {
            return mChecksPermission
        }
    }

    private fun containsSecurityException(
            types: List<PsiType>): Boolean {
        for (type in types) {
            if (type is PsiClassType) {
                val cls = type.resolve()
                // In earlier versions we checked not just for java.lang.SecurityException but
                // any super type as well, however that probably hides warnings in cases where
                // users don't want that; see http://b.android.com/182165
                //return context.getEvaluator().extendsClass(cls, "java.lang.SecurityException", false);
                if (cls != null && SECURITY_EXCEPTION == cls.qualifiedName) {
                    return true
                }
            }
        }

        return false
    }

    private var mPermissions: PermissionHolder? = null

    private fun getPermissions(
            context: JavaContext): PermissionHolder {
        if (mPermissions == null) {
            val permissions = Sets.newHashSetWithExpectedSize<String>(30)
            val revocable = Sets.newHashSetWithExpectedSize<String>(4)
            val mainProject = context.mainProject
            val mergedManifest = mainProject.mergedManifest

            if (mergedManifest != null) {
                for (element in XmlUtils.getSubTags(mergedManifest.documentElement)) {
                    val nodeName = element.nodeName
                    if (TAG_USES_PERMISSION == nodeName
                            || TAG_USES_PERMISSION_SDK_23 == nodeName
                            || TAG_USES_PERMISSION_SDK_M == nodeName) {
                        val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
                        if (!name.isEmpty()) {
                            permissions.add(name)
                        }
                    } else if (nodeName == TAG_PERMISSION) {
                        val protectionLevel = element.getAttributeNS(ANDROID_URI,
                                ATTR_PROTECTION_LEVEL)
                        if (VALUE_DANGEROUS == protectionLevel) {
                            val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
                            if (!name.isEmpty()) {
                                revocable.add(name)
                            }
                        }
                    }
                }
            }
            val minSdkVersion = mainProject.minSdkVersion
            val targetSdkVersion = mainProject.targetSdkVersion
            mPermissions = PermissionHolder.SetPermissionLookup(permissions,
                    revocable,
                    minSdkVersion,
                    targetSdkVersion)
        }

        return mPermissions!!
    }

    private var mIsAndroidThingsProject: Boolean? = null

    private fun isAndroidThingsProject(context: Context): Boolean {
        if (mIsAndroidThingsProject == null) {
            val project = context.mainProject
            val mergedManifest = project.mergedManifest ?: return false
            val manifest = mergedManifest.documentElement ?: return false

            val application = XmlUtils.getFirstSubTagByName(manifest,
                    TAG_APPLICATION) ?: return false
            var usesLibrary = XmlUtils.getFirstSubTagByName(application, TAG_USES_LIBRARY)

            while (usesLibrary != null && mIsAndroidThingsProject == null) {
                val name = usesLibrary.getAttributeNS(ANDROID_URI, ATTR_NAME)
                var isThingsLibraryRequired = true
                val required = usesLibrary.getAttributeNS(ANDROID_URI, ATTR_REQUIRED)
                if (VALUE_FALSE == required) {
                    isThingsLibraryRequired = false
                }

                if (THINGS_LIBRARY == name && isThingsLibraryRequired) {
                    mIsAndroidThingsProject = true
                }

                usesLibrary = XmlUtils.getNextTagByName(usesLibrary, TAG_USES_LIBRARY)
            }
            if (mIsAndroidThingsProject == null) {
                mIsAndroidThingsProject = false
            }
        }
        return mIsAndroidThingsProject!!
    }

    companion object {
        private val IMPLEMENTATION = Implementation(PermissionDetector::class.java,
                Scope.JAVA_FILE_SCOPE)

        private const val THINGS_LIBRARY = "com.google.android.things"

        /** Failing to enforce security by just calling check permission  */
        @JvmField
        val CHECK_PERMISSION = Issue.create(
                "UseCheckPermission",
                "Using the result of check permission calls",

                "You normally want to use the result of checking a permission; these methods " +
                        "return whether the permission is held; they do not throw an error if the permission " +
                        "is not granted. Code which does not do anything with the return value probably " +
                        "meant to be calling the enforce methods instead, e.g. rather than " +
                        "`Context#checkCallingPermission` it should call `Context#enforceCallingPermission`.",

                Category.SECURITY,
                6,
                Severity.WARNING,
                IMPLEMENTATION)

        /** Method result should be used  */
        @JvmField
        val MISSING_PERMISSION = Issue.create(
                "MissingPermission",
                "Missing Permissions",

                "This check scans through your code and libraries and looks at the APIs being used, " +
                        "and checks this against the set of permissions required to access those APIs. If " +
                        "the code using those APIs is called at runtime, then the program will crash.\n" +
                        "\n" +
                        "Furthermore, for permissions that are revocable (with targetSdkVersion 23), client " +
                        "code must also be prepared to handle the calls throwing an exception if the user " +
                        "rejects the request for permission at runtime.",

                Category.CORRECTNESS,
                9,
                Severity.ERROR,
                IMPLEMENTATION)
    }
}