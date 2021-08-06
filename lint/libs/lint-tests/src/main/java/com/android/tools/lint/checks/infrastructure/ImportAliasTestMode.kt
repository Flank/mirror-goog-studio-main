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
import com.android.tools.lint.detector.api.isKotlin
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiType
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UImportStatement
import org.jetbrains.uast.kotlin.KotlinUImportStatement
import org.jetbrains.uast.textRange
import java.util.Locale
import kotlin.math.min

/**
 * Test mode which introduces import aliases for all imported types to
 * make sure detectors handle presence of import aliases.
 *
 * (See also the [TypeAliasTestMode].)
 */
class ImportAliasTestMode : SourceTransformationTestMode(
    description = "Import aliases",
    "TestMode.IMPORT_ALIAS",
    "import-alias"
) {
    override val diffExplanation: String =
        // first line shorter: expecting to prefix that line with
        // "org.junit.ComparisonFailure: "
        """
        In Kotlin, types can be renamed
        via import aliases. This means that detectors should look at the
        resolved types, not the local identifier names.

        This test mode introduces import aliases for all types accessed
        from Kotlin source files and makes sure the test results are
        unaffected.

        In the unlikely event that your lint check is actually doing something
        specific to import aliasing, you can turn off this test mode using
        `.skipTestModes($fieldName)`.
        """.trimIndent()

    override fun isRelevantFile(file: TestFile): Boolean {
        // import as is only applicable to Kotlin
        return file.targetRelativePath.endsWith(DOT_KT)
    }

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
        val imported = mutableSetOf<String>()
        val aliasNames = linkedMapOf<String, String>()

        root.accept(object : FullyQualifyNamesTestMode.TypeVisitor(context, source) {
            override fun visitImportStatement(node: UImportStatement): Boolean {
                if (node is KotlinUImportStatement && !node.isOnDemand &&
                    node.sourcePsi.aliasName == null
                ) {
                    val resolved = node.resolve()
                    val reference = node.importReference
                    val text = reference?.sourcePsi?.text
                    if (text != null && resolved != null) {
                        if (resolved is PsiClass) {
                            val qualifiedName = resolved.qualifiedName
                            if (qualifiedName != null) {
                                imported.add(qualifiedName)
                            }
                        }
                    }
                }
                return super.visitImportStatement(node)
            }

            private fun getImportAlias(qualified: String?): String? {
                return if (qualified != null && imported.contains(qualified)) {
                    aliasNames[qualified]
                        ?: "IMPORT_ALIAS_${aliasNames.size + 1}_${qualified.substring(qualified.lastIndexOf('.') + 1).toUpperCase(Locale.US)}".also { aliasNames[qualified] = it }
                } else {
                    null
                }
            }

            override fun checkTypeReference(
                node: UElement,
                cls: PsiClass?,
                offset: Int,
                type: PsiType
            ) {
                val typeText = node.sourcePsi?.text?.substringBefore('<') ?: return
                if (typeText.isBlank()) {
                    return
                }
                val range = node.sourcePsi?.textRange ?: return
                if (type is PsiArrayType && cls != null) {
                    getImportAlias(cls.qualifiedName)?.let { aliasName ->
                        editMap[offset] = replace(range.startOffset, range.endOffset, "Array<$aliasName>")
                    }
                    return
                } else if (type is PsiClassType) {
                    val qualified = cls?.qualifiedName
                    getImportAlias(qualified)?.let { aliasName ->
                        editMap[offset] = replace(
                            range.startOffset, min(range.endOffset, range.startOffset + typeText.length),
                            if (typeText.endsWith("?")) "$aliasName?"
                            else if (typeText.endsWith("!!")) "$aliasName!!" else aliasName
                        )
                    }
                }
            }

            override fun afterVisitFile(node: UFile) {
                if (aliasNames.isNotEmpty()) {
                    val start = node.imports.lastOrNull()?.textRange?.endOffset
                        ?: node.classes.firstOrNull()?.textRange?.startOffset
                        ?: run {
                            val index = source.indexOf(node.packageName)
                            val end = index + node.packageName.length
                            source.indexOf('\n', end) + 1
                        }

                    val aliases = aliasNames.map { (type, name) -> "import $type as $name" }.joinToString("\n")
                    editMap[start] = insert(start, "\n$aliases")
                }
                super.afterVisitFile(node)
            }
        })

        return editMap.values.toMutableList()
    }

    override fun messagesMatch(original: String, modified: String): Boolean {
        val index = modified.indexOf("IMPORT_ALIAS_")
        if (index == -1) {
            return false
        }
        return original.regionMatches(0, modified, 0, index)
    }
}
