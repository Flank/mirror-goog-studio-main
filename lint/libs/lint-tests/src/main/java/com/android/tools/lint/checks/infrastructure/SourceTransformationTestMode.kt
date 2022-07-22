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

import com.android.tools.lint.LintIssueDocGenerator.Companion.MESSAGE_PATTERN
import com.android.tools.lint.checks.infrastructure.LintFixVerifier.Companion.FIX_PATTERN
import com.android.tools.lint.detector.api.JavaContext

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
     * Special comparison function for the output; normally, test modes
     * change conditions and expect the output to be identical, but here
     * we're modifying the source itself, which affects the sources
     * snippets shown in the error output as well as the error ranges.
     * However, we want to treat this error:
     * ```
     *    src/test/pkg/CheckPermissions.java:22: Warning: The result of extractAlpha is not used [CheckResult]
     *            bitmap.extractAlpha(); // WARNING
     *            ~~~~~~~~~~~~~~~~~~~~~
     * ```
     *
     * and this error:
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
                    // Line numbers can drift in quickfixes since the source transformations
                    // can easily introduce some new lines here and there.
                    val adjustedExpectations = LintFixVerifier.adjustLineNumbers(expectedLine) { 0 }
                    val adjustedActual = LintFixVerifier.adjustLineNumbers(actualLine) { 0 }
                    if (messagesMatch(adjustedExpectations, adjustedActual)) {
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
}

class Edit(
    val startOffset: Int,
    val endOffset: Int,
    val with: String,
    private val biasRight: Boolean = false,
    private val ordinal: Int = nextOrdinal++
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

    companion object {
        private var nextOrdinal = 0

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
    }
}

/**
 * Checks that the given list of [edits] for a given test [mode]
 * operating on a source file pointed to by the given [context] is a
 * valid set of edits: no overlaps. This is normally the case, but
 * there are some tricky situations where UAST will map a single source
 * element into multiple separate AST elements (examples include
 * `@JvmStatic` methods in companion objects and properties) so test
 * modes need to be careful around these. This adds a safeguard
 * mechanism such that if the test mode produces an invalid set of
 * edits, we catch it, log it and resume (or if it's a built-in test,
 * fail the test). This method should return true if there are no
 * conflicts.
 */
internal fun ensureConflictFree(
    mode: TestMode,
    context: JavaContext,
    edits: List<Edit>
): Boolean {
    // Make sure the edits are valid
    var prev: Edit? = null
    for (edit in edits.sorted()) {
        if (prev != null && prev.startOffset < edit.endOffset) {
            val message = "" +
                "Invalid source transform test mode (${mode.fieldName}):\n" +
                "edits overlap; $prev and $edit.\n" +
                "This means that the test mode is broken.\n" +
                "Please file a bug with details; the source file where this happened is:\n" +
                "${listFile(context.file.path, context.getContents()?.toString() ?: "")}\n" +
                "and the list of edits is:\n" +
                "$edits"
            if (Throwable().fillInStackTrace().stackTrace.any {
                val name = it.className
                name.startsWith("com.android.tools.") &&
                    (!name.contains(".infrastructure.") || name.endsWith("Test"))
            }
            ) {
                // For built-in tests we want to fail
                error(message)
            } else {
                // Don't cause external detector tests to fail because the test mode is broken;
                // log and encourage people to submit bugs letting us know.
                System.err.println(message)
                return false
            }
        }
        prev = edit
    }

    return true
}
