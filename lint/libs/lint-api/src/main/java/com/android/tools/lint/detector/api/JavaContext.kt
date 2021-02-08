/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.tools.lint.detector.api

import com.android.tools.lint.client.api.JavaEvaluator
import com.android.tools.lint.client.api.LintDriver
import com.android.tools.lint.client.api.UastParser
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnonymousClass
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiLabeledStatement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiSwitchStatement
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UDeclaration
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UEnumConstant
import org.jetbrains.uast.UField
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.USwitchExpression
import org.jetbrains.uast.UastContext
import org.jetbrains.uast.getUastContext
import java.io.File

/**
 * A [Context] used when checking Java files.
 *
 * @constructor Constructs a [JavaContext] for running lint on the given file, with
 * the given scope, in the given project reporting errors to the given
 * client.
 *
 * **NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.**
 */
open class JavaContext(
    /** the driver running through the checks */
    driver: LintDriver,

    /** the project to run lint on which contains the given file */
    project: Project,

    /**
     * The main project if this project is a library project, or
     * null if this is not a library project. The main project is
     * the root project of all library projects, not necessarily the
     * directly including project.
     */
    main: Project?,

    /** the file to be analyzed */
    file: File
) : Context(driver, project, main, file) {

    /** The parse tree, when using PSI  */
    var psiFile: PsiFile? = null
        private set

    /** The parse tree, when using UAST  */
    var uastFile: UFile? = null

    /** The parser which produced the parse tree  */
    lateinit var uastParser: UastParser

    /** Whether this context is in a test source folder  */
    var isTestSource: Boolean = false

    /** Whether this context is in a generated source folder  */
    var isGeneratedSource: Boolean = false

    /**
     * Returns a location for the given node range (from the starting offset of the first node to
     * the ending offset of the second node).
     *
     * @param from the AST node to get a starting location from
     *
     * @param fromDelta Offset delta to apply to the starting offset
     *
     * @param to the AST node to get a ending location from
     *
     * @param toDelta Offset delta to apply to the ending offset
     *
     * @return a location for the given node
     */
    fun getRangeLocation(
        from: PsiElement,
        fromDelta: Int,
        to: PsiElement,
        toDelta: Int
    ): Location =
        uastParser.getRangeLocation(this, from, fromDelta, to, toDelta)

    fun getRangeLocation(
        from: UElement,
        fromDelta: Int,
        to: UElement,
        toDelta: Int
    ): Location =
        uastParser.getRangeLocation(this, from, fromDelta, to, toDelta)

    // Disambiguate since UDeclarations implement both PsiElement and UElement
    fun getRangeLocation(
        from: UDeclaration,
        fromDelta: Int,
        to: UDeclaration,
        toDelta: Int
    ): Location =
        uastParser.getRangeLocation(this, from as PsiElement, fromDelta, to, toDelta)

    /**
     * Returns a location for the given node range (from the starting offset of the first node to
     * the ending offset of the second node).
     *
     * @param from the AST node to get a starting location from
     *
     * @param fromDelta Offset delta to apply to the starting offset
     *
     * @param length The number of characters to add from the delta
     *
     * @return a location for the given node
     */
    @Suppress("unused", "unused")
    fun getRangeLocation(
        from: PsiElement,
        fromDelta: Int,
        length: Int
    ): Location =
        uastParser.getRangeLocation(this, from, fromDelta, fromDelta + length)

    fun getRangeLocation(
        from: UElement,
        fromDelta: Int,
        length: Int
    ): Location =
        uastParser.getRangeLocation(this, from, fromDelta, fromDelta + length)

    /**
     * Returns a [Location] for the given element. This attempts to pick a shorter
     * location range than the entire element; for a class or method for example, it picks
     * the name element (if found). For statement constructs such as a `switch` statement
     * it will highlight the keyword, etc.
     *
     * @param element the AST element to create a location for
     *
     * @return a location for the given element
     */
    fun getNameLocation(element: PsiElement): Location {
        return run {
            if (element is PsiSwitchStatement) {
                // Just use keyword
                return uastParser.getRangeLocation(this, element, 0, 6) // 6: "switch".length()
            }
            uastParser.getNameLocation(this, element)
        }
    }

    /**
     * Returns a [Location] for the given element. This attempts to pick a shorter
     * location range than the entire element; for a class or method for example, it picks
     * the name element (if found). For statement constructs such as a `switch` statement
     * it will highlight the keyword, etc.
     *
     * @param element the AST element to create a location for
     *
     * @return a location for the given element
     */
    fun getNameLocation(element: UElement): Location {
        if (element is USwitchExpression) {
            // Just use keyword
            return uastParser.getRangeLocation(this, element, 0, 6) // 6: "switch".length()
        }
        return uastParser.getNameLocation(this, element)
    }

    /**
     * Returns a [Location] for the given element. This attempts to pick a shorter
     * location range than the entire element; for a class or method for example, it picks
     * the name element (if found). For statement constructs such as a `switch` statement
     * it will highlight the keyword, etc.
     *
     *
     * [UClass] is both a [PsiElement] and a [UElement] so this method
     * is here to make calling getNameLocation(UClass) easier without having to make an
     * explicit cast.
     *
     * @param cls the AST class element to create a location for
     *
     * @return a location for the given element
     */
    fun getNameLocation(cls: UDeclaration): Location = getNameLocation(cls as UElement)

    fun getNameLocation(cls: UClass): Location = getNameLocation(cls as UElement)

    fun getNameLocation(cls: UMethod): Location = getNameLocation(cls as UElement)

    fun getLocation(node: PsiElement): Location = uastParser.getLocation(this, node)

    fun getLocation(element: UElement): Location {
        if (element is UCallExpression) {
            return uastParser.getCallLocation(this, element, true, true)
        }
        return uastParser.getLocation(this, element)
    }

    fun getLocation(element: UMethod): Location =
        uastParser.getLocation(this, element as PsiMethod)

    fun getLocation(element: UField): Location =
        uastParser.getLocation(this, element as PsiField)

    /**
     * Creates a location for the given call.
     *
     * @param call the call to create a location range for
     *
     * @param includeReceiver whether we should include the receiver of the method call if
     *                         applicable
     *
     * @param includeArguments whether we should include the arguments to the call
     *
     * @return a location
     */
    fun getCallLocation(
        call: UCallExpression,
        includeReceiver: Boolean,
        includeArguments: Boolean
    ): Location =
        uastParser.getCallLocation(this, call, includeReceiver, includeArguments)

    val evaluator: JavaEvaluator
        get() = uastParser.evaluator

    /**
     * Returns the [PsiJavaFile].
     *
     * @return the parsed Java source file
     */
    @Suppress("unused")
    @Deprecated(
        "Use {@link #getPsiFile()} instead",
        replaceWith = ReplaceWith("psiFile")
    )
    val javaFile: PsiJavaFile?
        get() {
            return if (psiFile is PsiJavaFile) {
                psiFile as PsiJavaFile?
            } else {
                null
            }
        }

    /**
     * Sets the compilation result. Not intended for client usage; the lint infrastructure
     * will set this when a context has been processed
     *
     * @param javaFile the parse tree
     */
    fun setJavaFile(javaFile: PsiFile?) {
        this.psiFile = javaFile
    }

    override fun report(
        issue: Issue,
        location: Location,
        message: String,
        quickfixData: LintFix?
    ) {
        when (val source = location.source) {
            is UElement -> {
                // Detector accidentally invoked scope-less report method inherited
                // from generic Context, but we remember the actual node from the
                // location construction, so use it to find the best suppress scope
                report(issue, source, location, message, quickfixData)
            }
            is PsiElement -> {
                report(issue, source, location, message, quickfixData)
            }
            else -> {
                // No specific scope node for the error: just look at the root
                // of the file for suppress annotations
                if (driver.isSuppressed(this, issue, psiFile)) {
                    return
                }
                super.report(issue, location, message, quickfixData)
            }
        }
    }

    /**
     * Reports an issue applicable to a given AST node. The AST node is used as the
     * scope to check for suppress lint annotations.
     *
     * @param issue the issue to report
     *
     * @param scope the AST node scope the error applies to. The lint infrastructure will
     *                     check whether there are suppress annotations on this node (or its
     *                     enclosing nodes) and if so suppress the warning without involving the
     *                     client.
     *
     * @param location the location of the issue, or null if not known
     *
     * @param message the message for this warning
     *
     * @param quickfixData optional data to pass to the IDE for use by a quickfix.
     */
    @JvmOverloads
    fun report(
        issue: Issue,
        scope: PsiElement?,
        location: Location,
        message: String,
        quickfixData: LintFix? = null
    ) {
        if (scope != null) {
            if (scope is UAnnotated) {
                if (driver.isSuppressed(this, issue, scope as UAnnotated)) {
                    return
                }
            } else if (driver.isSuppressed(this, issue, scope)) {
                return
            }
        }
        super.doReport(issue, location, message, quickfixData)
    }

    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated(
        "Here for temporary compatibility; the new typed quickfix data parameter " +
            "should be used instead"
    )
    fun report(
        issue: Issue,
        scope: PsiElement?,
        location: Location,
        message: String,
        quickfixData: Any?
    ) = report(issue, scope, location, message)

    /**
     * Reports an issue applicable to a given AST node. The AST node is used as the
     * scope to check for suppress lint annotations.
     *
     * @param issue the issue to report
     *
     * @param scope the AST node scope the error applies to. The lint infrastructure will
     *                     check whether there are suppress annotations on this node (or its
     *                     enclosing nodes) and if so suppress the warning without involving the
     *                     client.
     *
     * @param location the location of the issue, or null if not known
     *
     * @param message the message for this warning
     *
     * @param quickfixData optional data to pass to the IDE for use by a quickfix.
     */
    @JvmOverloads
    fun report(
        issue: Issue,
        scope: UElement?,
        location: Location,
        message: String,
        quickfixData: LintFix? = null
    ) {
        if (scope is UAnnotated) {
            if (driver.isSuppressed(this, issue, scope)) {
                return
            }
        } else if (driver.isSuppressed(this, issue, scope)) {
            return
        }
        super.doReport(issue, location, message, quickfixData)
    }

    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated(
        "Here for temporary compatibility; the new typed quickfix data parameter " +
            "should be used instead"
    )
    fun report(
        issue: Issue,
        scope: UElement?,
        location: Location,
        message: String,
        quickfixData: Any?
    ) = report(issue, scope, location, message)

    /**
     * [UClass] is both a [PsiElement] and a [UElement] so this method
     * is here to make calling report(..., UClass, ...) easier without having to make
     * an explicit cast.
     */
    fun report(
        issue: Issue,
        scopeClass: UClass?,
        location: Location,
        message: String
    ) = report(issue, scopeClass as UElement?, location, message)

    /**
     * [UClass] is both a [PsiElement] and a [UElement] so this method
     * is here to make calling report(..., UClass, ...) easier without having to make
     * an explicit cast.
     */
    fun report(
        issue: Issue,
        scopeClass: UClass?,
        location: Location,
        message: String,
        quickfixData: LintFix?
    ) = report(issue, scopeClass as UElement?, location, message, quickfixData)

    /**
     * [UMethod] is both a [PsiElement] and a [UElement] so this method
     * is here to make calling report(..., UMethod, ...) easier without having to make
     * an explicit cast.
     */
    fun report(
        issue: Issue,
        scopeClass: UMethod?,
        location: Location,
        message: String
    ) = report(issue, scopeClass as UElement?, location, message)

    /**
     * [UMethod] is both a [PsiElement] and a [UElement] so this method
     * is here to make calling report(..., UMethod, ...) easier without having to make
     * an explicit cast.
     */
    fun report(
        issue: Issue,
        scopeClass: UMethod?,
        location: Location,
        message: String,
        quickfixData: LintFix?
    ) =
        report(issue, scopeClass as UElement?, location, message, quickfixData)

    /**
     * [UField] is both a [PsiElement] and a [UElement] so this method
     * is here to make calling report(..., UField, ...) easier without having to make
     * an explicit cast.
     */
    fun report(
        issue: Issue,
        scopeClass: UField?,
        location: Location,
        message: String
    ) =
        report(issue, scopeClass as UElement?, location, message)

    /**
     * [UField] is both a [PsiElement] and a [UElement] so this method
     * is here to make calling report(..., UField, ...) easier without having to make
     * an explicit cast.
     */
    fun report(
        issue: Issue,
        scopeClass: UField?,
        location: Location,
        message: String,
        quickfixData: LintFix?
    ) =
        report(issue, scopeClass as UElement?, location, message, quickfixData)

    override val suppressCommentPrefix: String?
        get() = SUPPRESS_JAVA_COMMENT_PREFIX

    fun isSuppressedWithComment(scope: PsiElement, issue: Issue): Boolean {
        if (scope is PsiCompiledElement) {
            return false
        }

        // Check whether there is a comment marker
        getContents() ?: return false
        val textRange = scope.textRange ?: return false
        val start = textRange.startOffset
        return isSuppressedWithComment(start, issue)
    }

    fun isSuppressedWithComment(scope: UElement, issue: Issue): Boolean {
        val psi = scope.psi
        return psi != null && isSuppressedWithComment(psi, issue)
    }

    @Deprecated("Use UastFacade instead", ReplaceWith("org.jetbrains.uast.UastFacade"))
    val uastContext: UastContext
        get() = uastFile?.getUastContext()!!

    companion object {
        // TODO: Move to LintUtils etc
        @JvmStatic
        fun getMethodName(call: UElement): String? =
            when (call) {
                is UEnumConstant -> call.name
                is UCallExpression -> call.methodName ?: call.classReference?.resolvedName
                else -> null
            }

        /**
         * Searches for a name node corresponding to the given node
         * @return the name node to use, if applicable
         */
        @JvmStatic
        fun findNameElement(element: PsiElement): PsiElement? {
            when (element) {
                is PsiClass -> {
                    if (element is PsiAnonymousClass) {
                        return element.baseClassReference
                    }
                    return element.nameIdentifier
                }
                is PsiMethod -> return element.nameIdentifier
                is PsiMethodCallExpression -> return element.methodExpression.referenceNameElement
                is PsiNewExpression -> return element.classReference
                is PsiField -> return element.nameIdentifier
                is PsiAnnotation -> return element.nameReferenceElement
                is PsiReferenceExpression -> return element.referenceNameElement
                is PsiLabeledStatement -> return element.labelIdentifier
                else -> return null
            }
        }

        @JvmStatic
        fun findNameElement(element: UElement): UElement? {
            if (element is UDeclaration) {
                return element.uastAnchor
                // } else if (element instanceof PsiNameIdentifierOwner) {
                //    return ((PsiNameIdentifierOwner) element).getNameIdentifier();
            } else if (element is UCallExpression) {
                return element.methodIdentifier
            }

            return null
        }
    }
}
