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

import com.android.SdkConstants
import com.android.tools.lint.detector.api.JavaContext
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.TreeElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UFile
import org.jetbrains.uast.visitor.AbstractUastVisitor
import org.junit.rules.TemporaryFolder
import java.io.File

abstract class UastSourceTransformationTestMode(description: String, testMode: String, folder: String) :
    SourceTransformationTestMode(description, testMode, folder) {

    /**
     * Transform the given AST in [root] which corresponds to the
     * given [source] code, returning a list of edit operations. The
     * [clientData] map can be used to store shared state across the
     * various test files; the type alias test mode for example will use
     * it to keep track of type aliases assigned per package.
     *
     * The result list should be a mutable list because the test
     * infrastructure will merge these lists.
     */
    abstract fun transform(
        source: String,
        context: JavaContext,
        root: UFile,
        clientData: MutableMap<String, Any>
    ): MutableList<Edit>

    protected open fun isRelevantFile(file: TestFile): Boolean {
        return file.targetRelativePath.endsWith(SdkConstants.DOT_KT) || file.targetRelativePath.endsWith(SdkConstants.DOT_JAVA)
    }

    override fun applies(context: TestModeContext): Boolean {
        return context.task.incrementalFileName == null && context.projects.any { project ->
            project.files.any { file ->
                if (file is CompiledSourceFile && file.type == CompiledSourceFile.Type.SOURCE_AND_BYTECODE)
                    isRelevantFile(file.source)
                else
                    isRelevantFile(file)
            }
        }
    }

    override fun before(context: TestModeContext): Any? {
        for (project in context.projectFolders) {
            if (project.walk().any { it.path.endsWith(SdkConstants.DOT_KT) || it.path.endsWith(SdkConstants.DOT_JAVA) }) {
                if (!processTestFiles(context, project, sdkHome = context.task.sdkHome)) {
                    return CANCEL
                }
            }
        }

        return null // success/continue
    }

    open fun processTestFiles(
        testContext: TestModeContext,
        projectDir: File,
        sdkHome: File?,
        changeCallback: (JavaContext, String) -> Unit = { _, _ -> },
    ): Boolean {
        val (contexts, disposable) = parse(
            dir = projectDir,
            sdkHome = sdkHome
        )
        try {
            return processTestFiles(contexts, changeCallback)
        } finally {
            Disposer.dispose(disposable)
        }
    }

    // For unit tests only
    open fun processTestFiles(
        testFiles: List<TestFile>,
        sdkHome: File?,
        changeCallback: (JavaContext, String) -> Unit = { _, _ -> },
    ): Boolean {
        val temporaryFolder = TemporaryFolder().apply { create() }
        try {
            val (contexts, disposable) = parse(
                temporaryFolder = temporaryFolder,
                sdkHome = sdkHome,
                testFiles = testFiles.toTypedArray()
            )
            try {
                return processTestFiles(contexts, changeCallback)
            } finally {
                Disposer.dispose(disposable)
            }
        } finally {
            temporaryFolder.delete()
        }
    }

    private fun processTestFiles(
        contexts: List<JavaContext>,
        changeCallback: (JavaContext, String) -> Unit
    ): Boolean {
        val fileEdits = processTestFiles(contexts, mutableMapOf())
        if (fileEdits.isEmpty()) {
            return false
        }
        for ((context, edits) in fileEdits) {
            val file = context.uastFile ?: continue
            val source = file.sourcePsi.text
            val edited = Edit.performEdits(source, edits)
            if (edited != source) {
                context.file.writeText(edited)
                changeCallback(context, edited)
            }
        }
        return true
    }

    protected open fun processTestFiles(
        contexts: List<JavaContext>,
        clientData: MutableMap<String, Any>
    ): List<Pair<JavaContext, List<Edit>>> {
        val result: MutableList<Pair<JavaContext, List<Edit>>> = mutableListOf()
        for (context in contexts.sortedBy { it.file.path }) {
            val file = context.uastFile ?: continue
            val source = file.sourcePsi.text
            val edits = transform(source, context, file, clientData)
            if (edits.isNotEmpty()) {
                if (!ensureConflictFree(this, context, edits)) {
                    return emptyList()
                }
                result.add(Pair(context, edits))
            }
        }

        return result
    }

    open class EditVisitor : AbstractUastVisitor() {
        private var depth = 0

        override fun visitElement(node: UElement): Boolean {
            depth++
            return super.visitElement(node)
        }

        override fun afterVisitElement(node: UElement) {
            depth--
            super.afterVisitElement(node)
        }

        protected fun insert(offset: Int, text: String, biasRight: Boolean = true): Edit {
            return Edit(offset, offset, text, biasRight, depth)
        }

        protected fun replace(start: Int, end: Int, text: String, biasRight: Boolean = true): Edit {
            return Edit(start, end, text, biasRight, depth)
        }

        protected fun remove(start: Int, end: Int, biasRight: Boolean = true): Edit {
            return Edit(start, end, "", biasRight, depth)
        }

        protected fun remove(offset: Int, text: String, biasRight: Boolean = true): Edit {
            return Edit(offset, offset + text.length, "", biasRight, depth)
        }

        fun MutableList<Edit>.surround(
            beginNode: UExpression,
            endNode: UExpression,
            open: String,
            close: String
        ) {
            surround(beginNode.sourcePsi, endNode.sourcePsi, open, close)
        }

        fun MutableList<Edit>.surround(
            beginPsi: PsiElement?,
            endPsi: PsiElement?,
            open: String,
            close: String
        ) {
            beginPsi ?: return
            endPsi ?: return

            val next = (endPsi as? KtLiteralStringTemplateEntry)?.nextSibling as? TreeElement
            val endDelta = if (next != null && next.elementType == KtTokens.CLOSING_QUOTE) {
                next.textLength
            } else {
                0
            }
            val prev = (beginPsi as? KtLiteralStringTemplateEntry)?.prevSibling as? TreeElement
            val beginDelta = if (prev != null && prev.elementType == KtTokens.OPEN_QUOTE) {
                prev.textLength
            } else {
                0
            }

            val start = beginPsi.textRange.startOffset - beginDelta
            val end = endPsi.textRange.endOffset + endDelta
            add(insert(start, open, biasRight = true))
            add(insert(end, close, biasRight = false))
        }

        fun MutableList<Edit>.unsurround(
            beginNode: UExpression,
            endNode: UExpression,
            open: String,
            close: String,
            source: String
        ) {
            unsurround(beginNode.sourcePsi, endNode.sourcePsi, open, close, source)
        }

        fun MutableList<Edit>.unsurround(
            beginPsi: PsiElement?,
            endPsi: PsiElement?,
            open: String,
            close: String,
            source: String
        ) {
            beginPsi ?: return
            endPsi ?: return

            val next = (endPsi as? KtLiteralStringTemplateEntry)?.nextSibling as? TreeElement
            val endDelta = if (next != null && next.elementType == KtTokens.CLOSING_QUOTE) {
                next.textLength
            } else {
                0
            }
            val prev = (beginPsi as? KtLiteralStringTemplateEntry)?.prevSibling as? TreeElement
            val beginDelta = if (prev != null && prev.elementType == KtTokens.OPEN_QUOTE) {
                prev.textLength
            } else {
                0
            }

            val start = beginPsi.textRange.startOffset - beginDelta
            val end = endPsi.textRange.endOffset + endDelta - 1
            if (source.startsWith(open, start) && source.startsWith(close, end)) {
                if (source[start + open.length] == ' ') {
                    add(remove(start, "$open ", biasRight = true))
                } else {
                    add(remove(start, open, biasRight = true))
                }
                if (source[end - 1] == ' ') {
                    add(remove(end - 1, " $close", biasRight = true))
                } else {
                    add(remove(end, close, biasRight = true))
                }
            }
        }
    }
}
