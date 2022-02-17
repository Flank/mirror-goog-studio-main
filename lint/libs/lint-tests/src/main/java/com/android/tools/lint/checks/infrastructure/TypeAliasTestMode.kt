/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.lint.checks.infrastructure

import com.android.SdkConstants.DOT_KT
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.acceptSourceFile
import com.android.tools.lint.detector.api.isKotlin
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiCapturedWildcardType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiDisjunctionType
import com.intellij.psi.PsiEllipsisType
import com.intellij.psi.PsiField
import com.intellij.psi.PsiIntersectionType
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.PsiWildcardType
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClassLiteralExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression

/**
 * Test mode which introduces type aliases for all types to make sure
 * detectors handle presence of type aliases.
 *
 * (See also the [ImportAliasTestMode].)
 */
class TypeAliasTestMode : UastSourceTransformationTestMode(
    description = "Type aliases",
    "TestMode.TYPE_ALIAS",
    "type-alias"
) {
    override val diffExplanation: String =
        // first line shorter: expecting to prefix that line with
        // "org.junit.ComparisonFailure: "
        """
         In Kotlin, types can be renamed
        via type aliases. This means that detectors should look at the
        resolved types, not the local identifier names.

        This test mode introduces type aliases for all types accessed
        from Kotlin source files and makes sure the test results are
        unaffected.

        In the unlikely event that your lint check is actually doing something
        specific to type aliasing, you can turn off this test mode using
        `.skipTestModes($fieldName)`.
        """.trimIndent()

    override fun isRelevantFile(file: TestFile): Boolean {
        // Typealias is only applicable to Kotlin
        return file.targetRelativePath.endsWith(DOT_KT)
    }

    class PackageAliases(
        var nextAliasId: Int = 1,
        val aliasesPerPackage: MutableMap<String, MutableMap<String, String>> = mutableMapOf()
    )

    override fun transform(
        source: String,
        context: JavaContext,
        root: UFile,
        clientData: MutableMap<String, Any>
    ): MutableList<Edit> {
        if (!isKotlin(root.sourcePsi)) {
            return mutableListOf()
        }
        val editMap = mutableMapOf<Int, Edit>()

        val pkg = root.packageName
        val packageAliases = clientData[TYPE_ALIAS.folderName] as? PackageAliases
            ?: PackageAliases().also { clientData[TYPE_ALIAS.folderName] = it }
        val aliasesPerPackage = packageAliases.aliasesPerPackage

        // aliasesPerPackage keeps track of aliases we've already added to each
        // package; in the following map we track which ones were newly added
        // in this compilation unit as well, such that we can list them on the bottom
        val newAliases = linkedMapOf<String, String>()

        root.acceptSourceFile(object : FullyQualifyNamesTestMode.TypeVisitor(context, source) {
            private fun getTypeAlias(typeText: String): String {
                val packageMap = aliasesPerPackage[pkg]
                    ?: linkedMapOf<String, String>().also { aliasesPerPackage[pkg] = it }
                return packageMap[typeText]
                    ?: "TYPE_ALIAS_${packageAliases.nextAliasId++}".also {
                        packageMap[typeText] = it
                        newAliases[typeText] = it
                    }
            }

            override fun visitAnnotation(node: UAnnotation): Boolean {
                // don't type alias annotation names
                return false
            }

            override fun checkTypeReference(
                node: UElement,
                cls: PsiClass?,
                offset: Int,
                type: PsiType
            ) {
                if (cls?.containingClass != null) {
                    // Don't try to handle containing classes yet; we need to fix references that include
                    // the outer class reference first
                    return
                }
                val range = node.sourcePsi?.textRange ?: return
                val start = range.startOffset
                var end = range.endOffset
                val typeText = node.sourcePsi?.text?.let {
                    var name = it

                    val next = node.sourcePsi?.parent?.nextSibling
                    if (next is KtTypeArgumentList) {
                        val wildcards = next.text
                        name += wildcards
                        end += wildcards.length
                    }

                    var curr = cls
                    while (curr != null) {
                        val parent = curr.containingClass
                        if (parent == null) {
                            break
                        } else {
                            curr = parent
                            name = curr.name + "." + name
                        }
                    }
                    name
                } ?: return
                if (typeText.isBlank() || type is PsiEllipsisType || type.hasTypeParameter()) {
                    return
                }
                val aliasName = getTypeAlias(typeText)
                editMap[offset] = replace(start, end, aliasName)
            }

            private fun PsiType.hasTypeParameter(): Boolean {
                return when (this) {
                    is PsiPrimitiveType -> false
                    is PsiClassType -> parameters.any { it.hasTypeParameter() } || resolve() is PsiTypeParameter
                    is PsiWildcardType -> isBounded && bound?.hasTypeParameter() == true || isExtends && extendsBound.hasTypeParameter()
                    is PsiCapturedWildcardType -> upperBound.hasTypeParameter()
                    is PsiArrayType -> componentType.hasTypeParameter() // includes PsiEllipsisType
                    is PsiIntersectionType -> conjuncts.any { it.hasTypeParameter() }
                    is PsiDisjunctionType -> disjunctions.any { it.hasTypeParameter() }
                    else -> false
                }
            }

            override fun allowClassReference(
                node: USimpleNameReferenceExpression,
                parent: UQualifiedReferenceExpression
            ): Boolean {
                val parentResolved = parent.resolve() ?: return false
                return parentResolved is PsiField
            }

            override fun visitClassLiteralExpression(node: UClassLiteralExpression): Boolean {
                return false
            }

            override fun visitCallExpression(node: UCallExpression): Boolean {
                // For some reason, if we create a typealias to say GridLayout and
                // then try to invoke the constructor on the typealias, even though
                // that's valid in Kotlin, UAST will fail to resolve that constructor
                // call, so don't use type aliases in these cases.
                return false
            }

            override fun afterVisitFile(node: UFile) {
                val aliases = newAliases.map { (type, name) -> "typealias $name = $type" }.joinToString("\n")
                val end = source.length
                editMap[end] = insert(end, "\n$aliases")
            }
        })

        return editMap.values.toMutableList()
    }

    override fun messagesMatch(original: String, modified: String): Boolean {
        if (original == modified) return true
        val index = modified.indexOf("TYPE_ALIAS_")
        if (index == -1) {
            return false
        }
        return original.regionMatches(0, modified, 0, index)
    }
}
