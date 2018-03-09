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

import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.google.common.annotations.Beta
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UastContext
import java.io.File

/**
 * A wrapper for a UAST parser. This allows tools integrating lint to map directly
 * to builtin services, such as already-parsed data structures in Java editors.
 *
 *
 * **NOTE: This is not public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.**
 */
@Beta
abstract class UastParser {

    /**
     * Returns an evaluator which can perform various resolution tasks,
     * evaluate inheritance lookup etc.
     *
     * @return an evaluator
     */
    abstract val evaluator: JavaEvaluator

    /**
     * Returns a UastContext which can provide UAST representations for source files
     */
    abstract val uastContext: UastContext?

    /**
     * Prepare to parse the given contexts. This method will be called before
     * a series of [.parse] calls, which allows some
     * parsers to do up front global computation in case they want to more
     * efficiently process multiple files at the same time. This allows a single
     * type-attribution pass for example, which is a lot more efficient than
     * performing global type analysis over and over again for each individual
     * file
     *
     * @param contexts a list of production source contexts to be parsed
     * @param testContexts a list of test source contexts to be parsed
     * @return true if the preparation succeeded; false if there were errors
     */
    open fun prepare(
        contexts: List<JavaContext>,
        testContexts: List<JavaContext>
    ): Boolean {
        return true
    }

    /**
     * Parse the file pointed to by the given context.
     *
     * @param context the context pointing to the file to be parsed, typically via
     * [Context.getContents] but the file handle ( [Context.file] can also be
     * used to map to an existing editor buffer in the surrounding tool, etc)
     * @return the compilation unit node for the file
     */
    abstract fun parse(context: JavaContext): UFile?

    /**
     * Returns a [Location] for the given element
     *
     * @param context information about the file being parsed
     * @param element the element to create a location for
     * @return a location for the given node
     */
    abstract fun getLocation(context: JavaContext, element: PsiElement): Location

    abstract fun getLocation(context: JavaContext, element: UElement): Location

    abstract fun getCallLocation(
        context: JavaContext,
        call: UCallExpression,
        includeReceiver: Boolean,
        includeArguments: Boolean
    ): Location

    abstract fun getFile(file: PsiFile): File?

    abstract fun getFileContents(file: PsiFile): CharSequence

    abstract fun createLocation(element: PsiElement): Location

    abstract fun createLocation(element: UElement): Location

    /**
     * Returns a [Location] for the given node range (from the starting offset of the first
     * node to the ending offset of the second node).
     *
     * @param context information about the file being parsed
     * @param from the AST node to get a starting location from
     * @param fromDelta Offset delta to apply to the starting offset
     * @param to the AST node to get a ending location from
     * @param toDelta Offset delta to apply to the ending offset
     * @return a location for the given node
     */
    abstract fun getRangeLocation(
        context: JavaContext,
        from: PsiElement,
        fromDelta: Int,
        to: PsiElement,
        toDelta: Int
    ): Location

    abstract fun getRangeLocation(
        context: JavaContext,
        from: UElement,
        fromDelta: Int,
        to: UElement,
        toDelta: Int
    ): Location

    /**
     * Like [.getRangeLocation]
     * but both offsets are relative to the starting offset of the given node. This is
     * sometimes more convenient than operating relative to the ending offset when you
     * have a fixed range in mind.
     *
     * @param context information about the file being parsed
     * @param from the AST node to get a starting location from
     * @param fromDelta Offset delta to apply to the starting offset
     * @param toDelta Offset delta to apply to the starting offset
     * @return a location for the given node
     */
    abstract fun getRangeLocation(
        context: JavaContext,
        from: PsiElement,
        fromDelta: Int,
        toDelta: Int
    ): Location

    abstract fun getRangeLocation(
        context: JavaContext,
        from: UElement,
        fromDelta: Int,
        toDelta: Int
    ): Location

    /**
     * Returns a [Location] for the given node. This attempts to pick a shorter
     * location range than the entire node; for a class or method for example, it picks
     * the name node (if found). For statement constructs such as a `switch` statement
     * it will highlight the keyword, etc.
     *
     * @param context information about the file being parsed
     * @param element the node to create a location for
     * @return a location for the given node
     */
    abstract fun getNameLocation(context: JavaContext, element: PsiElement): Location

    abstract fun getNameLocation(context: JavaContext, element: UElement): Location

    /**
     * Dispose any data structures held for the given context.
     *
     * @param context information about the file previously parsed
     * @param compilationUnit the compilation unit being disposed
     */
    open fun dispose(context: JavaContext, compilationUnit: UFile) {
    }

    /**
     * Dispose any remaining data structures held for all contexts.
     * Typically frees up any resources allocated by
     * [.prepare]
     */
    open fun dispose() {
    }
}
