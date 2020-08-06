/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.lint.checks.studio

import com.android.SdkConstants
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.OtherFileScanner
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UDeclaration
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UFile
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.kotlin.KotlinStringTemplateUPolyadicExpression
import java.util.EnumSet

/**
 * Looks at identifiers and comments to check for terminology where we
 * have recommended replacements in our codebase
 */
class TerminologyDetector : Detector(), SourceCodeScanner, OtherFileScanner {
    companion object {
        private val IMPLEMENTATION =
            Implementation(
                TerminologyDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.OTHER, Scope.TEST_SOURCES),
                Scope.JAVA_FILE_SCOPE
            )

        /** Looks for terminology that has suggested replacements for our codebase */
        val ISSUE =
            Issue.create(
                id = "WrongTerminology",
                briefDescription = "Code uses deprecated terminology",
                explanation =
                    """
                    Our codebase follows the recommendations in \
                    https://developers.google.com/style/word-list. This lint check \
                    flags accidental usages in names, strings and comments of terminology that \
                    is not recommended.
                    """,
                category = Category.CORRECTNESS,
                priority = 10,
                severity = Severity.FATAL,
                androidSpecific = false,
                moreInfo = "https://developers.google.com/style/word-list",
                implementation = IMPLEMENTATION
            )
    }

    // Implements OtherFileScanner

    override fun getApplicableFiles(): EnumSet<Scope> = Scope.OTHER_SCOPE

    override fun run(context: Context) {
        if (!context.project.reportIssues) {
            // If this is a library project not being analyzed, ignore it
            return
        }
        val name = context.file.name
        if (name.endsWith(SdkConstants.DOT_JAVA)) {
            // Already checking class names; no need to flag file names.
            return
        }
        // For other file types such as Kotlin and resource there's no required
        // link between contents and filename
        checkCommentStateMachine(context, null, name)

        if (name.endsWith(SdkConstants.DOT_KT)) {
            // Contents already checked
            return
        }

        // Resource files, BUILD files, etc:
        val contents = context.getContents() ?: return
        checkCommentStateMachine(context, null, contents)
    }

    // Implements SourceFileScanner

    override fun getApplicableUastTypes(): List<Class<out UElement?>>? {
        return listOf(
            UFile::class.java,
            UVariable::class.java,
            UMethod::class.java,
            UClass::class.java,
            ULiteralExpression::class.java
        )
    }

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        // We're using a UAST visitor here instead of just visiting the file
        // as raw text since we'd like to only visit declarations, comments and strings,
        // not for example class, method and field *references* to APIs outside of
        // our control
        return object : UElementHandler() {
            // There's some duplication in comments between UFile#allCommentsInFile
            // and the comments returned for each declaration, but unfortunately each
            // one is missing some from the other so we need to check both and just
            // keep track of the ones we've checked so we don't report errors multiple
            // times
            private val checkedComments = mutableSetOf<String>()

            override fun visitFile(node: UFile) {
                checkedComments.clear()
                for (comment in node.allCommentsInFile) {
                    if (comment.uastParent is UDeclaration) { // handled in checkDeclaration
                        continue
                    }
                    val contents = comment.text
                    checkedComments.add(contents)
                    checkCommentStateMachine(context, comment, contents)
                }
            }

            override fun visitVariable(node: UVariable) {
                checkDeclaration(node, node.name)
            }

            override fun visitMethod(node: UMethod) {
                checkDeclaration(node, node.name)
            }

            override fun visitClass(node: UClass) {
                checkDeclaration(node, node.name)
            }

            private fun checkDeclaration(node: UDeclaration, name: String?) {
                name ?: return
                checkCommentStateMachine(context, node, name)
                for (comment in node.comments) {
                    val contents = comment.text
                    if (checkedComments.add(contents)) {
                        checkCommentStateMachine(context, comment, contents)
                    }
                }
            }

            override fun visitLiteralExpression(node: ULiteralExpression) {
                if (node.isString) {
                    val string = node.value as? String ?: return
                    checkCommentStateMachine(context, node, string)
                }
            }
        }
    }

    private fun report(
        context: Context,
        element: UElement?,
        source: CharSequence,
        start: Int,
        end: Int,
        suggestion: String,
        checkWholeWords: Boolean = false
    ) {
        if (checkWholeWords && !matchesWholeWords(source, start, end)) {
            return
        }
        val word = source.substring(start, end)

        val message = StringBuilder("Avoid using \"$word\"")

        // See if it's within another word and if so include in message
        var s = start
        while (s > 0 && source[s - 1].isJavaIdentifierPart()) {
            s--
        }
        var e = end
        while (e < source.length && source[e].isJavaIdentifierPart()) {
            e++
        }
        if (s < start || e > end) {
            val wholeWord = source.substring(s, e)
            if (!wholeWord.startsWith(word)) {
                message.append(" in \"$wholeWord\"")
            }
        }

        if (suggestion != "?") {
            message.append("; consider something like \"$suggestion\"")
        }
        message.append("; see https://developers.google.com/style/word-list")

        if (context is JavaContext && element != null) {
            val location = getStringLocation(context, element, word)
            context.report(ISSUE, element, location, message.toString())
        } else {
            // match on plain file; either matching file name or file contents
            val fileName = context.file.name
            val location =
                if (fileName == source.toString()) {
                    val index = message.indexOf("\"", message.indexOf("\"") + 1) + 1
                    message.insert(index, " in filename")
                    // Filename match
                    Location.create(context.file)
                } else {
                    // Content match
                    Location.create(context.file, source, start, end)
                }
            context.report(ISSUE, location, message.toString())
        }
    }

    private fun getStringLocation(
        context: JavaContext,
        argument: UElement,
        string: String,
        location: Location = context.getLocation(argument)
    ): Location {
        val start = location.start?.offset
            ?: return location
        val end = location.end?.offset
            ?: return location
        val contents = context.getContents()
        var index = contents?.indexOf(string, ignoreCase = false, startIndex = start)
            ?: return location
        val result = if (index != -1) {
            if (index > end) {
                // Look for earlier occurrence too. We're seeking the string in the given
                // expression/argument position. If it's included as a literal, it will be
                // between start and end. But if we find one *after* the end, that's likely
                // another, unrelated one. Instead, find it earlier in the source; this is most
                // likely an earlier assignment which is then referenced in the expression.
                val alt = contents.lastIndexOf(string, ignoreCase = false, startIndex = start)
                if (alt != -1) {
                    index = alt
                }
            }
            if (argument is KotlinStringTemplateUPolyadicExpression &&
                argument.operands.size == 1 &&
                location.source === argument.operands[0]
            ) {
                context.getRangeLocation(argument.operands[0], index - start, string.length)
            } else {
                context.getRangeLocation(argument, index - start, string.length)
            }
        } else {
            // Couldn't find string; this typically happens if the string value is split across
            // multiple string literals (line concatenations)  or has escapes etc. Just
            // use the reference location.
            location
        }

        if (result.start == null) {
            return Location.create(context.file, contents, index, index + string.length)
        }

        return result
    }

    /**
     * See whether the text span in the given range in [source] is separated by
     * word boundaries; this doesn't just include punctuation but also allows words
     * within camel case strings, for example in the string getFooBar, the word "Foo"
     * is a whole word match, whereas in getFoobar it would not have been.
     */
    private fun matchesWholeWords(source: CharSequence, start: Int, end: Int): Boolean {
        // Check beginning
        if (start > 0) {
            val first = source[start]
            val prev = source[start - 1]
            if (prev.isJavaIdentifierPart() &&
                // Allow camel case
                !(prev.isLowerCase() && first.isUpperCase())
            ) {
                return false
            }
        }

        // Check end
        val length = source.length
        if (end < length - 1) {
            val last = source[end - 1]
            val next = source[end]
            if (next.isJavaIdentifierPart() &&
                // Allow camel case
                !(last.isLowerCase() && next.isUpperCase())
            ) {
                return false
            }
        }

        return true
    }

    /**
     * Checks the text in [source].
     *
     * If it finds matches in the string, it will report errors into the given
     * context. The associated AST [element] is used to look look up suppress
     * annotations and to find the right error range.
     */
    @Suppress("SpellCheckingInspection")
    private fun checkCommentStateMachine(
        context: Context,
        element: UElement?,
        source: CharSequence
    ) {
        // Simple state machine which walks through strings looking for terminology
        // that is not recommended. This is generated by
        // TerminologyDetectorTest#createStateMachine
        // <editor-fold defaultstate="collapsed" desc="Generated state machine">
        // @formatter:off
        var state = 1
        var begin = 0
        var i = 0
        while (i < source.length) {
            val c = source[i++]
            when (state) {
                1 -> {
                    begin = i - 1
                    state = when (c) {
                        'b', 'B' -> 2
                        'f', 'F' -> 3
                        'g', 'G' -> 4
                        's', 'S' -> 5
                        'w', 'W' -> 6
                        else -> 1
                    }
                }
                2 -> {
                    state = when (c) {
                        'l', 'L' -> 7
                        else -> {
                            i--; 1
                        }
                    }
                }
                3 -> {
                    state = when (c) {
                        'u', 'U' -> 8
                        else -> {
                            i--; 1
                        }
                    }
                }
                4 -> {
                    state = when (c) {
                        'r', 'R' -> 11
                        else -> {
                            i--; 1
                        }
                    }
                }
                5 -> {
                    state = when (c) {
                        'l', 'L' -> 10
                        else -> {
                            i--; 1
                        }
                    }
                }
                6 -> {
                    state = when (c) {
                        'h', 'H' -> 9
                        else -> {
                            i--; 1
                        }
                    }
                }
                7 -> {
                    state = when (c) {
                        'a', 'A' -> 14
                        else -> {
                            i--; 1
                        }
                    }
                }
                8 -> {
                    state = when (c) {
                        'c', 'C' -> 16
                        else -> {
                            i--; 1
                        }
                    }
                }
                9 -> {
                    state = when (c) {
                        'i', 'I' -> 13
                        else -> {
                            i--; 1
                        }
                    }
                }
                10 -> {
                    state = when (c) {
                        'a', 'A' -> 12
                        else -> {
                            i--; 1
                        }
                    }
                }
                11 -> {
                    state = when (c) {
                        'a', 'A' -> 15
                        else -> {
                            i--; 1
                        }
                    }
                }
                12 -> {
                    state = when (c) {
                        'v', 'V' -> 21
                        else -> {
                            i--; 1
                        }
                    }
                }
                13 -> {
                    state = when (c) {
                        't', 'T' -> 17
                        else -> {
                            i--; 1
                        }
                    }
                }
                14 -> {
                    state = when (c) {
                        'c', 'C' -> 18
                        else -> {
                            i--; 1
                        }
                    }
                }
                15 -> {
                    state = when (c) {
                        'n', 'N' -> 20
                        else -> {
                            i--; 1
                        }
                    }
                }
                16 -> {
                    state = when (c) {
                        'k', 'K' -> {
                            report(context, element, source, i - 4, i, "?", false)
                            1
                        }
                        else -> {
                            i--; 1
                        }
                    }
                }
                17 -> {
                    state = when (c) {
                        'e', 'E' -> 22
                        else -> {
                            i--; 1
                        }
                    }
                }
                18 -> {
                    state = when (c) {
                        'k', 'K' -> 24
                        else -> {
                            i--; 1
                        }
                    }
                }
                20 -> {
                    state = when (c) {
                        'd', 'D' -> 25
                        else -> {
                            i--; 1
                        }
                    }
                }
                21 -> {
                    state = when (c) {
                        'e', 'E' -> {
                            report(context, element, source, begin, i, "secondary", true)
                            1
                        }
                        else -> {
                            i--; 1
                        }
                    }
                }
                22 -> {
                    state = when (c) {
                        'l', 'L' -> 27
                        '-' -> 29
                        else -> {
                            i--; 1
                        }
                    }
                }
                24 -> {
                    state = when (c) {
                        'l', 'L' -> 26
                        '-' -> 28
                        else -> {
                            i--; 1
                        }
                    }
                }
                25 -> {
                    state = when (c) {
                        'f', 'F' -> 30
                        else -> {
                            i--; 1
                        }
                    }
                }
                26 -> {
                    state = when (c) {
                        'i', 'I' -> 33
                        else -> {
                            i--; 1
                        }
                    }
                }
                27 -> {
                    state = when (c) {
                        'i', 'I' -> 31
                        else -> {
                            i--; 1
                        }
                    }
                }
                28 -> {
                    state = when (c) {
                        'l', 'L' -> 34
                        else -> {
                            i--; 1
                        }
                    }
                }
                29 -> {
                    state = when (c) {
                        'l', 'L' -> 32
                        else -> {
                            i--; 1
                        }
                    }
                }
                30 -> {
                    state = when (c) {
                        'a', 'A' -> 35
                        else -> {
                            i--; 3
                        }
                    }
                }
                31 -> {
                    state = when (c) {
                        's', 'S' -> 36
                        else -> {
                            i--; 1
                        }
                    }
                }
                32 -> {
                    state = when (c) {
                        'i', 'I' -> 38
                        else -> {
                            i--; 1
                        }
                    }
                }
                33 -> {
                    state = when (c) {
                        's', 'S' -> 37
                        else -> {
                            i--; 1
                        }
                    }
                }
                34 -> {
                    state = when (c) {
                        'i', 'I' -> 40
                        else -> {
                            i--; 1
                        }
                    }
                }
                35 -> {
                    state = when (c) {
                        't', 'T' -> 39
                        else -> {
                            i--; 1
                        }
                    }
                }
                36 -> {
                    state = when (c) {
                        't', 'T' -> {
                            report(context, element, source, i - 9, i, "include", false)
                            1
                        }
                        else -> {
                            i--; 5
                        }
                    }
                }
                37 -> {
                    state = when (c) {
                        't', 'T' -> {
                            report(context, element, source, i - 9, i, "exclude", false)
                            1
                        }
                        else -> {
                            i--; 5
                        }
                    }
                }
                38 -> {
                    state = when (c) {
                        's', 'S' -> 44
                        else -> {
                            i--; 1
                        }
                    }
                }
                39 -> {
                    state = when (c) {
                        'h', 'H' -> 41
                        else -> {
                            i--; 1
                        }
                    }
                }
                40 -> {
                    state = when (c) {
                        's', 'S' -> 42
                        else -> {
                            i--; 1
                        }
                    }
                }
                41 -> {
                    state = when (c) {
                        'e', 'E' -> 48
                        else -> {
                            i--; 1
                        }
                    }
                }
                42 -> {
                    state = when (c) {
                        't', 'T' -> {
                            report(context, element, source, i - 10, i, "exclude", false)
                            1
                        }
                        else -> {
                            i--; 5
                        }
                    }
                }
                44 -> {
                    state = when (c) {
                        't', 'T' -> {
                            report(context, element, source, i - 10, i, "include", false)
                            1
                        }
                        else -> {
                            i--; 5
                        }
                    }
                }
                48 -> {
                    state = when (c) {
                        'r', 'R' -> 49
                        else -> {
                            i--; 1
                        }
                    }
                }
                49 -> {
                    state = when (c) {
                        'e', 'E' -> 50
                        else -> {
                            i--; 1
                        }
                    }
                }
                50 -> {
                    state = when (c) {
                        'd', 'D' -> {
                            report(context, element, source, begin, i, "baseline", true)
                            1
                        }
                        else -> {
                            i--; 1
                        }
                    }
                }
            }
        }
        // @formatter:on
        // </editor-fold>
    }
}
