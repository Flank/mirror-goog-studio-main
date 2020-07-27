/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.SdkConstants
import com.android.SdkConstants.ANDROID_PKG
import com.android.SdkConstants.ID_PREFIX
import com.android.SdkConstants.NEW_ID_PREFIX
import com.android.resources.ResourceType
import com.android.tools.lint.detector.api.isKotlin
import com.android.tools.lint.detector.api.stripIdPrefix
import com.google.common.base.Joiner
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiType
import com.intellij.psi.PsiVariable
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UResolvable
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.asQualifiedPath
import org.jetbrains.uast.getContainingUFile
import org.jetbrains.uast.getQualifiedParentOrThis
import org.jetbrains.uast.java.JavaUDeclarationsExpression

/**
 * A reference to an Android resource in the AST; the reference may not be qualified.
 * For example, in the below, the `foo` reference on the right hand side of
 * the assignment can be resolved as an [ResourceReference].
 * <pre>
 * import my.pkg.R.string.foo;
 * ...
 * int id = foo;
</pre> *
 */
class ResourceReference(
    val node: UExpression,
    // getPackage() can be empty if not a package-qualified import (e.g. android.R.id.name).
    val `package`: String,
    val type: ResourceType,
    val name: String,
    val heuristic: Boolean = false
) {

    internal val isFramework: Boolean
        get() = `package` == ANDROID_PKG

    companion object {
        private fun toAndroidReference(expression: UQualifiedReferenceExpression): ResourceReference? {
            val path = expression.asQualifiedPath() ?: return null

            var packageNameFromResolved: String? = null

            val containingClass =
                PsiTreeUtil.getParentOfType(expression.resolve(), PsiClass::class.java)
            if (containingClass != null) {
                val containingClassFqName = containingClass.qualifiedName
                if (containingClassFqName != null) {
                    val i = containingClassFqName.lastIndexOf(".R.")
                    if (i >= 0) {
                        packageNameFromResolved = containingClassFqName.substring(0, i)
                    }
                }
            }

            val size = path.size
            if (size < 3) {
                return null
            }

            val r = path[size - 3]
            if (r != SdkConstants.R_CLASS) {
                return null
            }

            val packageName = if (packageNameFromResolved != null)
                packageNameFromResolved
            else Joiner.on('.').join(path.subList(0, size - 3))

            val type = path[size - 2]
            val name = path[size - 1]

            val resourceType = ResourceType.fromClassName(type) ?: return null
            return ResourceReference(expression, packageName, resourceType, name)
        }

        @JvmStatic
        fun get(element: UElement): ResourceReference? {
            // Optimization for Java: instead of resolving the field just peek at the reference
            // and pick out the resource type and name from the context.
            // This also lets us pick up resource references even when the R fields don't
            // resolve (e.g. when there are symbol or source error problems.)
            if (element is UQualifiedReferenceExpression) {
                val ref = toAndroidReference(element)
                if (ref != null) {
                    return ref
                }
            }

            val declaration = when (element) {
                is UVariable -> element.psi
                is UResolvable -> (element as UResolvable).resolve()
                else -> return null
            }

            if (declaration == null && element is USimpleNameReferenceExpression) {
                // R class can't be resolved in tests so we need to use heuristics to calc the reference
                val maybeQualified = (element as UExpression).getQualifiedParentOrThis()
                if (maybeQualified is UQualifiedReferenceExpression) {
                    val ref = toAndroidReference(maybeQualified)
                    if (ref != null) {
                        return ref
                    }
                }
            }

            if (declaration !is PsiVariable) {
                // Synthetic import?
                // In the IDE, this will resolved into XML PSI. Attempt to use reflection to
                // pick out the relevant attribute.
                if (declaration != null &&
                    declaration::class.java.name == "com.intellij.psi.impl.source.xml.XmlAttributeValueImpl" &&
                    element is UExpression
                ) {
                    try {
                        val method = declaration::class.java.getDeclaredMethod("getValue")
                        val value = method.invoke(declaration)?.toString() ?: ""
                        if (value.startsWith(ID_PREFIX) || value.startsWith(NEW_ID_PREFIX)) {
                            return ResourceReference(
                                element,
                                "",
                                ResourceType.ID,
                                stripIdPrefix(value)
                            )
                        }
                    } catch (ignore: Throwable) {
                    }
                }

                val parent = element.uastParent
                if (parent is UQualifiedReferenceExpression && parent.selector === element) {
                    // synthetic import reference is usually not qualified
                    return null
                }
                if (parent is UCallExpression && parent.classReference === element) {
                    return null
                }

                if (declaration == null &&
                    // In the IDE we have proper reference resolving for synthetic imports
                    !LintClient.isStudio &&
                    element is USimpleNameReferenceExpression &&
                    isKotlin(element.sourcePsi) &&
                    element.identifier != "it"
                ) {
                    // If we have any synthetic imports in this class, this unresolved symbol is
                    // probably referring to it
                    element.getContainingUFile()?.imports?.forEach {
                        val expression = it.importReference as? USimpleNameReferenceExpression
                        val resolved = expression?.resolvedName
                        if (resolved != null &&
                            (
                                resolved.startsWith("import kotlinx.android.synthetic.") ||
                                    resolved.startsWith("kotlinx.android.synthetic.")
                                )
                        ) {
                            return ResourceReference(
                                element,
                                "",
                                ResourceType.ID,
                                element.identifier,
                                heuristic = true
                            )
                        }
                    }
                }

                return null
            }

            val variable = declaration as PsiVariable?
            if (variable !is PsiField ||
                (variable.type != PsiType.INT && !isIntArray(variable.type)) ||
                // Note that we don't check for PsiModifier.FINAL; in library projects
                // the R class fields are deliberately not made final such that their
                // values can be substituted when all the resources are merged together
                // in the app module and unique id's can be assigned for all resources
                !variable.hasModifierProperty(PsiModifier.STATIC)
            ) {
                return null
            }

            val resTypeClass = variable.containingClass
            if (resTypeClass == null || !resTypeClass.hasModifierProperty(PsiModifier.STATIC)) {
                return null
            }

            val rClass = resTypeClass.containingClass
            if (rClass == null || rClass.containingClass != null) {
                return null
            } else {
                val className = rClass.name
                if (!("R" == className || "R2" == className)) { // R2: butterknife library
                    return null
                }
            }

            val packageName = (rClass.containingFile as PsiJavaFile).packageName
            if (packageName.isEmpty()) {
                return null
            }

            val resourceType =
                ResourceType.fromClassName(resTypeClass.name ?: return null) ?: return null
            val resourceName = variable.name
            val node: UExpression = when (element) {
                is UExpression -> element
                is UVariable -> JavaUDeclarationsExpression(null, listOf(element))
                else -> throw IllegalArgumentException("element must be an expression or a UVariable")
            }

            return ResourceReference(node, packageName, resourceType, resourceName)
        }

        /**
         * Returns true if the type represents an int array (int[]), which is the
         * type of styleable R fields.
         */
        private fun isIntArray(type: PsiType): Boolean =
            (type as? PsiArrayType)?.componentType == PsiType.INT
    }
}
