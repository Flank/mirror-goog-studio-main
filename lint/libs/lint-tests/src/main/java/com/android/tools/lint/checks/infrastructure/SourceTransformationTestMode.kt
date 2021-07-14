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

import com.android.SdkConstants.DOT_JAVA
import com.android.SdkConstants.DOT_KT
import com.android.tools.lint.LintIssueDocGenerator.Companion.MESSAGE_PATTERN
import com.android.tools.lint.checks.infrastructure.LintFixVerifier.Companion.FIX_PATTERN
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
import java.lang.IllegalStateException

/**
 * Test mode which modifies Kotlin and Java source files in various
 * compatible ways (such as inserting extra parentheses for clarity) and
 * makes sure the test continues to pass.
 *
 * Remaining ideas for things to try modifying:
 * * Extract all literals into constants (to make sure constant
 *   evaluator is used)
 * * For == calls in Kotlin files to non primitives, consider switching
 *   to .equals() to make sure detector dealing with
 *   UBinaryExpression also handle UCallExpression
 * * Advanced: Consider switching if-then-else chains to when
 *   statements?
 * * Advanced: Consider trying to introduce apply statements for
 *   repeated construction chains, or "with"
 *   statements for repeated initialization
 * * In Kotlin add explicit types as well as try to remove them in case
 *   they're implicit
 * * Make unicode escapes for symbol names and/or unicode symbols
 * * Add boolean inversion (if (C) A else B => if (!C) B else A)
 * * Rename symbols in Kotlin to reserved names or names with spaces?
 * * Adding "+ 0" to integer expressions and "false || " to boolean
 *   expressions to make sure code is properly using
 *   a constant evaluator. (This obviously won't work
 *   for resource type and typedef expressions!)
 */
abstract class SourceTransformationTestMode(description: String, testMode: String, folder: String) :
    TestMode(description, testMode) {
    override val folderName: String = folder
    override val modifiesSources: Boolean = true

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
        return file.targetRelativePath.endsWith(DOT_KT) || file.targetRelativePath.endsWith(DOT_JAVA)
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
            if (project.walk().any { it.path.endsWith(DOT_KT) || it.path.endsWith(DOT_JAVA) }) {
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
            val edited = performEdits(source, edits)
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
                result.add(Pair(context, edits))
            }
        }

        return result
    }

    /**
     * Special comparison function for the output; normally, test modes
     * change conditions and expect the output to be identical, but here
     * we're modifying the source itself, which affects the sources
     * snippets shown in the error output as well as the error ranges.
     * However, we want to treat this error:
     *
     * ```
     *    src/test/pkg/CheckPermissions.java:22: Warning: The result of extractAlpha is not used [CheckResult]
     *            bitmap.extractAlpha(); // WARNING
     *            ~~~~~~~~~~~~~~~~~~~~~
     * ```
     *
     * and this error:
     *
     * ```
     *    src/test/pkg/CheckPermissions.java:22: Warning: The result of extractAlpha is not used [CheckResult]
     *            (bitmap).extractAlpha(); // WARNING
     *            ~~~~~~~~~~~~~~~~~~~~~~~
     * ```
     *
     * as equivalent.
     */
    override fun sameOutput(expected: String, actual: String, type: OutputKind): Boolean {
        if (expected == actual) {
            return true
        }

        // Just compare the error lines. Change details about the source
        // line included in the diff should not affect comparison.
        val (pattern, group) = if (type == OutputKind.REPORT) Pair(MESSAGE_PATTERN, 3) else Pair(FIX_PATTERN, 0)
        val expectedLines = expected.lines().mapNotNull {
            val matcher = pattern.matcher(it)
            if (matcher.matches()) matcher.group(group) else null
        }.toList()
        val actualLines = actual.lines().mapNotNull {
            val matcher = pattern.matcher(it)
            if (matcher.matches()) matcher.group(group) else null
        }.toList()
        if (expectedLines.size != actualLines.size) {
            return false
        }
        for (i in expectedLines.indices) {
            val expectedLine = expectedLines[i]
            val actualLine = actualLines[i]
            if (expectedLine != actualLine && !messagesMatch(expectedLine, actualLine)) {
                if (type == OutputKind.QUICKFIXES) {
                    // Many existing unit tests used 0-based line numbers; this was changed
                    // at some point but continue letting older unit tests pass if they
                    // only vary by line numbers
                    val adjusted = LintFixVerifier.bumpFixLineNumbers(expectedLine)
                    if (messagesMatch(adjusted, actualLine)) {
                        continue
                    }
                }
                return false
            }
        }

        return true
    }

    /**
     * Converts an error message in the error report in such a way that
     * it's made comparable to the default. For example, in parenthesis
     * mode, we're adding unnecessary parentheses. If a detector just
     * verbatim includes the node text, it will now include parentheses
     * as well, and the normal equals comparison will fail. The override
     * of this method in the parenthesis test mode will drop all
     * parentheses, which would make the comparison succeeded.
     */
    open fun transformMessage(message: String): String = message

    open fun messagesMatch(original: String, modified: String): Boolean {
        if (original == modified) return true
        val l1 = transformMessage(original)
        val l2 = transformMessage(modified)
        return l1 == l2
    }

    class Edit(
        val startOffset: Int,
        val endOffset: Int,
        val with: String,
        private val biasRight: Boolean,
        private val ordinal: Int
    ) : Comparable<Edit> {

        override fun compareTo(other: Edit): Int {
            val offsetDelta = other.startOffset - this.startOffset
            if (offsetDelta != 0) {
                return offsetDelta
            }
            val biasDelta = (if (other.biasRight) 1 else 0) - (if (biasRight) 1 else 0)
            if (biasDelta != 0) {
                return biasDelta
            }

            val ordinalDelta = ordinal - other.ordinal
            return if (biasRight) -ordinalDelta else ordinalDelta
        }

        override fun toString(): String {
            return "Edit($startOffset,$endOffset,\"$with\",$ordinal${if (biasRight) "" else ",left"})"
        }
    }

    fun performEdits(source: String, edits: List<Edit>): String {
        var s = source
        for (edit in edits.sorted()) {
            s = s.substring(0, edit.startOffset) + edit.with + s.substring(edit.endOffset)
        }

        return s
    }

    private fun Edit.conflicts(other: Edit): Boolean {
        // Don't allow overlap (>= instead of >) because we may have
        // something like the source "X" along with edits to change it
        // to "(X)" and to "pkg.X" and the order here matters -- we'd
        // want it to be "(pkg.X)", not "pkg.(X)", and we don't have a
        // way to know how to prioritize these
        if (other.startOffset > endOffset) {
            return false
        }
        if (other.endOffset < startOffset) {
            return false
        }

        return true
    }

    fun List<Edit>.conflicts(otherEdits: List<Edit>): Boolean {
        // I don't need to do a full n^2 comparison here; since lists
        // are sorted can do sub-region checks. However, these lists are
        // short so not worth worrying over.
        for (edit in this) {
            for (other in otherEdits) {
                if (edit.conflicts(other)) {
                    return true
                }
            }
        }

        return false
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

/**
 * Special composite [TestMode] which takes a list of source
 * transforming test modes, and attempts to apply as many of them as
 * possible at the same time. If there is a failure, it will then re-run
 * each individual test mode in isolation. This helps speed up the test
 * suite as we add more and more individual test modes since (with the
 * exception of very noisy test modes like the one inserting unnecessary
 * parentheses) often test modes don't overlap and so we don't need to
 * run through all the machinery twice.
 */
internal class TestModeGroup(vararg modes: TestMode) : SourceTransformationTestMode(
    "Source code transformations",
    "TestMode.SOURCE_CODE_TRANSFORMATIONS",
    "default"
) {
    override fun transform(
        source: String,
        context: JavaContext,
        root: UFile,
        clientData: MutableMap<String, Any>
    ): MutableList<Edit> {
        // This should never be called since we override [processTestFiles]
        // to perform composite editing
        throw IllegalStateException()
    }

    val modes: List<TestMode> get() = modes.toList()
    private val validModes: MutableList<SourceTransformationTestMode> =
        modes.mapNotNull { it as? SourceTransformationTestMode }.toMutableList()
    override val folderName: String = "default"
    override val modifiesSources: Boolean = true

    override fun applies(context: TestModeContext): Boolean {
        val applies = validModes.filter { it.applies(context) }
        return if (applies.isEmpty()) {
            false
        } else {
            validModes.clear()
            validModes.addAll(applies)
            true
        }
    }

    override fun partition(context: TestModeContext): List<TestMode> {
        val (contexts, disposable) = parse(
            dir = context.projectFolders.first(),
            sdkHome = context.task.sdkHome
        )
        try {
            return partition(context, contexts)
        } finally {
            Disposer.dispose(disposable)
        }
    }

    private fun partition(
        testContext: TestModeContext,
        contexts: List<JavaContext>
    ): List<MergedSourceTransformationTestMode> {
        // We're assuming two test modes don't cancel each other out, e.g. we shouldn't
        // put both an "add unnecessary parentheses" and a "remove unnecessary parentheses" mode
        // here into the same group)
        var currentModes: MutableList<SourceTransformationTestMode> = mutableListOf()
        var currentEditMap: MutableMap<File, Pair<String, MutableList<Edit>>> = mutableMapOf()
        var current = MergedSourceTransformationTestMode(currentModes, currentEditMap)
        val partitions = mutableListOf<MergedSourceTransformationTestMode>()

        val contents = mutableMapOf<File, String>()
        val rootDir = testContext.rootDir
        for (mode in this.validModes) {
            if (testContext.task.ignoredTestModes.contains(mode)) {
                continue
            }
            val pending = mutableMapOf<File, List<Edit>>()
            val clientData = mutableMapOf<String, Any>()
            for (fileContext in contexts) {
                val file = fileContext.uastFile ?: continue
                val source = file.sourcePsi.text
                val relativePath = fileContext.file.relativeTo(rootDir)
                contents[relativePath] = source
                val edits = mode.transform(source, fileContext, file, clientData)
                if (edits.isNotEmpty()) {
                    edits.sort()
                    pending[relativePath] = edits
                }
            }
            if (pending.isEmpty()) {
                continue
            }
            var conflict = false
            for ((file, edits) in pending) {
                val pair: Pair<String, MutableList<Edit>> = currentEditMap[file]
                    ?: Pair<String, MutableList<Edit>>(contents[file]!!, mutableListOf()).also { currentEditMap[file] = it }
                val currentEdits = pair.second
                if (currentEdits.conflicts(edits)) {
                    conflict = true
                    break
                }
            }
            if (conflict) {
                assert(currentModes.isNotEmpty())
                partitions.add(current)
                currentModes = mutableListOf()
                currentEditMap = mutableMapOf()
                current = MergedSourceTransformationTestMode(currentModes, currentEditMap)

                for ((file, _) in pending) {
                    currentEditMap[file] = Pair(contents[file]!!, mutableListOf())
                }
            }
            for ((file, edits) in pending) {
                // List should already be non-null because of the above iteration
                currentEditMap[file]?.second?.addAll(edits)
            }
            currentModes.add(mode)
        }

        if (currentModes.isNotEmpty()) {
            partitions.add(current)
        }

        // Make sure that all modified files are reset in subsequent partitions!
        val files = mutableMapOf<File, String>()
        for (mode in partitions) {
            val edits: MutableMap<File, Pair<String, MutableList<Edit>>> = mode.edits

            // Make sure all files we've seen so far are present in all subsequent partitions
            for ((file, original) in files) {
                if (!edits.containsKey(file)) {
                    edits[file] = Pair(original, mutableListOf())
                }
            }

            // And any newly added files should be added to the list such that from here
            // on we include it
            for ((file, pair) in edits) {
                if (!files.containsKey(file)) {
                    files[file] = pair.first
                }
            }
        }

        return partitions
    }

    override fun processTestFiles(
        testContext: TestModeContext,
        projectDir: File,
        sdkHome: File?,
        changeCallback: (JavaContext, String) -> Unit,
    ): Boolean {
        error("Should not be called")
    }
}

/**
 * An individual [TestMode] which performs a pre-computed set of edits
 * on a set of source files. These are merged from multiple individual
 * modes.
 */
internal class MergedSourceTransformationTestMode(
    internal val modes: List<TestMode>,
    internal val edits: MutableMap<File, Pair<String, MutableList<Edit>>>
) : SourceTransformationTestMode(
    "Merged Source code transformations",
    "TestMode.SOURCE_CODE_TRANSFORMATIONS",
    "source-transformations"
) {
    override val description: String
        get() = modes.joinToString { it.description }

    override val fieldName: String
        get() = modes.joinToString { it.fieldName }

    override val folderName: String
        get() = modes.joinToString("-") { it.folderName }

    override val modifiesSources: Boolean = true

    private fun initializeSources(testContext: TestModeContext) {
        for ((file, contents) in edits) {
            val (original, edits) = contents
            val edited = performEdits(original, edits)
            val target = if (file.isAbsolute) file else File(testContext.rootDir, file.path)
            assert(target.isFile) { target.path }
            target.writeText(edited)
        }
    }

    override fun sameOutput(expected: String, actual: String, type: OutputKind): Boolean {
        return modes.any { mode -> mode.sameOutput(expected, actual, type) }
    }

    override fun processTestFiles(
        testContext: TestModeContext,
        projectDir: File,
        sdkHome: File?,
        changeCallback: (JavaContext, String) -> Unit,
    ): Boolean {
        initializeSources(testContext)
        return true
    }

    override fun transform(
        source: String,
        context: JavaContext,
        root: UFile,
        clientData: MutableMap<String, Any>
    ): MutableList<Edit> {
        // This should never be called since we override [processTestFiles]
        // to perform composite editing
        throw IllegalStateException()
    }

    override fun toString(): String {
        return MergedSourceTransformationTestMode::class.java.simpleName + ":" + modes.joinToString()
    }
}
