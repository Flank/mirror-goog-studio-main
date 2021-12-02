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

import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.isKotlin
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDisjunctionType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtConstructorCalleeExpression
import org.jetbrains.kotlin.psi.KtConstructorDelegationCall
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UBinaryExpressionWithType
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCatchClause
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UClassLiteralExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UFile
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UTypeReferenceExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.util.isConstructorCall
import java.util.ArrayDeque

/**
 * Test mode which replaces simple symbols with fully qualified names,
 * and in Kotlin files, replaces symbols with import aliases, to help
 * catch bugs where code isn't properly handling fully qualified names
 * which are allowed.
 *
 * Note that it does not rewrite types inside type reference (such as
 * wildcard bounds, disjointed types, etc) since these are not modeled
 * by UAST and it would need to resort to string heuristics. For this
 * reason, it also does not attempt to remove the import statements
 * (since they may be needed for these type expressions).
 *
 * It also deliberately leaves core types like "String", "Throwable" etc
 * unqualified in Kotlin files such that Kotlin can correctly handle
 * these (changing to for example kotlin.String wouldn't always be
 * correct either).
 *
 * TODO: See if we can do statically imported methods and fields as
 *     well?
 */
class FullyQualifyNamesTestMode : UastSourceTransformationTestMode(
    description = "Names replaced with Fully Qualified Names",
    "TestMode.FULLY_QUALIFIED",
    "qualified-imports"
) {
    override val diffExplanation: String =
        // first line shorter: expecting to prefix that line with
        // "org.junit.ComparisonFailure: "
        """
        The user is allowed to use fully
        qualified names, or import aliases, in the source code. This
        test mode replaces symbols with fully qualified names and imports
        to make sure the lint detectors are properly handling these scenarios
        instead of simply hardcoding to simple names in the source.

        In the unlikely event that your lint check is actually doing something
        specific to fully qualified names, you can turn off this test mode using
        `.skipTestModes($fieldName)`.
        """.trimIndent()

    override fun transform(
        source: String,
        context: JavaContext,
        root: UFile,
        clientData: MutableMap<String, Any>
    ): MutableList<Edit> {
        // Edits at given offsets. By storing it this way
        // we can avoid a few cases where multiple PSI elements redundantly
        // refer to the same element; when we just recorded insertions
        // directly this sometimes led to double-prefixing symbols.
        val editMap = mutableMapOf<Int, Edit>()

        root.accept(object : TypeVisitor(context, source) {
            override fun checkClassReference(node: UElement, cls: PsiClass, offset: Int, name: String) {
                val fqn = cls.qualifiedName ?: return
                editMap[offset] = replace(offset, offset + name.length, fqn)
            }

            override fun allowKotlinCoreTypes(): Boolean = false
        })

        return editMap.values.toMutableList()
    }

    abstract class TypeVisitor(private val context: JavaContext, private val source: String) : EditVisitor() {
        open fun checkClassReference(node: UElement, cls: PsiClass, offset: Int, name: String) { }
        open fun checkTypeReference(node: UElement, cls: PsiClass?, offset: Int, type: PsiType) { }
        open fun allowKotlinCoreTypes() = true

        private fun replaceClassReference(
            cls: PsiClass,
            node: UElement,
            type: PsiType?
        ) {
            val fqn = cls.qualifiedName ?: return

            // The type may contain things like wildcards, arrays, etc; we just want the
            // name prefix.
            val psi = node.sourcePsi ?: return
            if (psi is KtSuperTypeCallEntry || psi is KtThisExpression || psi is KtConstructorDelegationCall) {
                return
            }
            if (!visitedElements.add(psi)) {
                // We can encounter the same underlying element multiple times
                // for example for Kotlin properties where UAST will also create methods
                // and the return type element is the same as the one for the UVariable field
                return
            }

            val start = node.sourcePsi?.textRange?.startOffset ?: return
            if (type != null) {
                checkTypeReference(node, cls, start, type)
            }

            // Don't replace (for example) "String" with "java.lang.String";
            // it should be "kotlin.String" instead, or some subtle behaviors
            // will change (such as a scenario found by the RemoteViewDetector's tests). However,
            // actually putting kotlin.String in won't always work either so leave it alone
            // if it's one of these targeted classes.
            if (!allowKotlinCoreTypes() && isKotlinCoreType(fqn) && isKotlin(psi)) {
                return
            }

            val nameStart = fqn.lastIndexOf('.') + 1
            if (nameStart == 0) {
                return
            }
            val nameLength = fqn.length - nameStart

            // Find out where the end is. Normally, we're just prefixing a symbol with
            // its package name, but in Kotlin we may be replacing an import alias
            // whose length is totally unrelated to the simple name of the class name
            // we're replacing
            var end = start
            while (end < source.length && source[end].isJavaIdentifierPart()) {
                end++
            }

            val name = source.substring(start, end)
            if (name == "this" || name == "super") {
                return
            }

            // Extra safety to make sure offsets are good: we expect the leaf name of
            // the symbol to match the current symbol, unless it matches an import alias or constructor delegation
            if (!source.regionMatches(start, fqn, nameStart, nameLength)) {
                if (context.uastFile!!.imports.none {
                    val alias = (it.sourcePsi as? KtImportDirective)?.importPath?.alias?.toString()
                    alias == name
                }
                ) {
                    // Probably something like a MutableMap reference or other Kotlin syntactic
                    // sugar such that the source reference (e.g. MutableMap<String>) does not
                    // match the underlying resolved type (e.g. java.util.Map<String>)
                    return
                }
            }

            // Make sure we don't have a variable name with the first symbol of the qualified
            // name; if so, that clash will cause incorrect inference
            val first = fqn.substringBefore('.')
            if (isDefined(first)) {
                return
            }

            checkClassReference(node, cls, start, name)
        }

        private val visitedElements = HashSet<PsiElement>()

        override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression): Boolean {
            checkNameReference(node)
            return super.visitSimpleNameReferenceExpression(node)
        }

        protected open fun allowClassReference(
            node: USimpleNameReferenceExpression,
            parent: UQualifiedReferenceExpression
        ): Boolean {
            return true
        }

        private fun checkNameReference(node: USimpleNameReferenceExpression) {
            val parent = node.uastParent

            if (parent is UQualifiedReferenceExpression && parent.receiver.skipParenthesizedExprDown() === node) {
                val resolved = node.resolve()
                if (resolved is PsiClass) {
                    if (!allowClassReference(node, parent)) {
                        return
                    }
                    val type = context.evaluator.getClassType(resolved)
                    replaceClassReference(resolved, node, type)
                }
            }
        }

        // Stack of scopes; each one just a list of variable names
        private val scopes = ArrayDeque<MutableList<String>>()

        override fun visitMethod(node: UMethod): Boolean {
            scopes.addLast(node.uastParameters.map(UParameter::getName).toMutableList())
            node.returnTypeReference?.let {
                checkTypeReference(it)
            }
            return super.visitMethod(node)
        }

        override fun visitBlockExpression(node: UBlockExpression): Boolean {
            scopes.addLast(mutableListOf())
            return super.visitBlockExpression(node)
        }

        override fun afterVisitBlockExpression(node: UBlockExpression) {
            scopes.removeLast()
            super.afterVisitBlockExpression(node)
        }

        override fun afterVisitMethod(node: UMethod) {
            scopes.removeLast()
            super.afterVisitMethod(node)
        }

        override fun visitLocalVariable(node: ULocalVariable): Boolean {
            scopes.lastOrNull()?.add(node.name)
            return super.visitLocalVariable(node)
        }

        override fun visitLambdaExpression(node: ULambdaExpression): Boolean {
            scopes.addLast(node.valueParameters.map(UParameter::getName).toMutableList())
            return super.visitLambdaExpression(node)
        }

        override fun afterVisitLambdaExpression(node: ULambdaExpression) {
            scopes.removeLast()
            super.afterVisitLambdaExpression(node)
        }

        /**
         * Returns true if there's a variable in a surrounding scope of
         * the given name
         */
        private fun isDefined(name: String): Boolean {
            return scopes.any { it.any { s -> s == name } }
        }

        override fun visitAnnotation(node: UAnnotation): Boolean {
            checkAnnotation(node)
            return super.visitAnnotation(node)
        }

        private fun checkAnnotation(node: UAnnotation) {
            val anchor = node.uastAnchor
            if (anchor != null) {
                // The anchor is usually constructed from just the selector (but
                // the parent node is not a UQualifiedReferenceExpression) so manually
                // check for fully qualified names
                val start = anchor.sourcePsi?.textRange?.startOffset ?: return
                if (start > 0 && (source[start - 1] == '.' || source[start - 1] == ':')) {
                    return
                }
                node.resolve()?.let { cls ->
                    replaceClassReference(cls, anchor, context.evaluator.getClassType(cls))
                }
            }
        }

        override fun visitClass(node: UClass): Boolean {
            node.uastSuperTypes.forEach(::checkTypeReference)
            return super.visitClass(node)
        }

        override fun visitCatchClause(node: UCatchClause): Boolean {
            node.typeReferences.forEach(::checkTypeReference)
            return super.visitCatchClause(node)
        }

        override fun visitVariable(node: UVariable): Boolean {
            node.typeReference?.let(::checkTypeReference)
            return super.visitVariable(node)
        }

        private fun checkTypeReference(typeReference: UTypeReferenceExpression) {
            val text = typeReference.sourcePsi?.text
            if (text != null) {
                val index = text.indexOf('.')
                if (index != -1 && (index < text.length - 3 || !text.endsWith("..."))) {
                    // Already qualified
                    return
                }
            }
            val type = typeReference.type
            if (context.evaluator.getTypeClass(type) is PsiTypeParameter) {
                return
            }
            if (type is PsiDisjunctionType) {
                return
            } else {
                val erased = context.evaluator.erasure(type.deepComponentType)
                val cls = context.evaluator.getTypeClass(erased)
                cls?.let { replaceClassReference(it, typeReference, typeReference.type) }
                    ?: run {
                        val offset = typeReference.sourcePsi?.textOffset ?: return
                        checkTypeReference(typeReference, cls, offset, type)
                    }
            }
        }

        override fun visitCallExpression(node: UCallExpression): Boolean {
            checkCall(node)
            return super.visitCallExpression(node)
        }

        private fun checkCall(node: UCallExpression) {
            if (node.receiver != null || !node.isConstructorCall()) {
                return
            }
            val reference = node.classReference ?: return
            val identifier = node.methodIdentifier
            if (reference is UQualifiedReferenceExpression || identifier == null) {
                return
            }

            val cls = reference.resolve() as? PsiClass ?: return

            // We sometimes get qualified expressions that are not
            // provided as a UQualifiedReferenceExpression
            if (reference.sourcePsi is KtCallElement) {
                val ktCallElement = reference.sourcePsi as KtCallElement
                val typeReference =
                    (ktCallElement.calleeExpression as? KtConstructorCalleeExpression)?.typeReference?.toUElement()
                if (typeReference != null) {
                    replaceClassReference(cls, typeReference, node.getExpressionType())
                    return
                }
                if (identifier.sourcePsi?.parent?.prevSibling?.text == ".") {
                    return
                }
            }

            replaceClassReference(cls, identifier, node.getExpressionType())
        }

        override fun visitClassLiteralExpression(node: UClassLiteralExpression): Boolean {
            node.type?.let { type ->
                val erased = context.evaluator.erasure(type.deepComponentType)
                val cls = context.evaluator.getTypeClass(erased)
                val sourcePsi = node.sourcePsi
                val typeReference = node.expression
                    ?: if (sourcePsi is KtClassLiteralExpression)
                        sourcePsi.lhs.toUElement() ?: node
                    else
                        node
                if (cls != null) {
                    replaceClassReference(cls, typeReference, type)
                } else {
                    val offset = typeReference.sourcePsi?.textOffset
                    if (offset != null) {
                        checkTypeReference(typeReference, cls, offset, type)
                    }
                }
            }

            return super.visitClassLiteralExpression(node)
        }

        override fun visitTypeReferenceExpression(node: UTypeReferenceExpression): Boolean {
            // Doesn't seem to be called where we expect, such as on variables on method returns,
            // but it IS called for casts (like UBinaryExpressionWithType)
            checkTypeReference(node)
            return super.visitTypeReferenceExpression(node)
        }

        override fun visitBinaryExpressionWithType(node: UBinaryExpressionWithType): Boolean {
            node.typeReference?.let {
                checkTypeReference(it)
            }
            return super.visitBinaryExpressionWithType(node)
        }

        private fun isKotlinCoreType(fqn: String): Boolean {
            return when (fqn) {
                // see core/builtins/native/kotlin
                "java.lang.String",
                "java.lang.Boolean",
                "java.lang.Enum",
                "java.lang.Number",
                "java.lang.Throwable",
                "java.lang.CharSequence",
                "java.lang.Comparable",
                "java.lang.Byte",
                "java.lang.Short",
                "java.lang.Character",
                "java.lang.Integer",
                "java.lang.Iterable",
                "java.lang.Long",
                "java.lang.Float",
                "java.lang.Double",

                "java.util.Iterator",
                "java.util.ListIterator",
                "java.util.List",
                "java.util.Collection",
                "java.util.Set",
                "java.util.Map" -> true
                else -> false
            }
        }
    }

    // Remove all qualifiers in the sentence to make comparisons match, e.g.
    // "The id test.pkg.R.id.duplicated has already been looked up" would transform to
    // "The id duplicated has already been looked up
    override fun transformMessage(message: String): String {
        // Consider keeping a map of all the substitutions we've performed and reverse them here
        var s = message
        while (true) {
            val result = fqnPattern.find(s) ?: return s
            val groups = result.groups
            val range = groups[0]?.range ?: return s
            val from = range.first + 1
            val to = range.last + 1
            val before = s.substring(0, from)
            val after = s.substring(to)
            s = before + after
        }
    }

    private val fqnPattern = Regex("""[^\p{Alnum}_]([\p{Alnum}_]+\.)+""")
}
