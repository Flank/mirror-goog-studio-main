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

package com.android.tools.lint.checks.infrastructure

import com.android.SdkConstants.DOT_XML
import com.android.tools.lint.Incident
import com.android.tools.lint.LintStats
import com.android.tools.lint.UastEnvironment
import com.android.tools.lint.Reporter
import com.android.tools.lint.XmlReporter
import com.android.tools.lint.checks.BuiltinIssueRegistry
import com.android.tools.lint.client.api.LintBaseline
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.TextFormat
import com.android.utils.PositionXmlParser
import com.google.common.base.Charsets
import com.google.common.base.Joiner
import com.google.common.base.Splitter
import com.google.common.collect.Lists
import com.google.common.collect.Maps
import com.google.common.io.Files
import com.intellij.util.ArrayUtil
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.utils.addToStdlib.firstNotNullResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Locale
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.regex.Pattern.MULTILINE
import javax.swing.text.BadLocationException
import javax.swing.text.Document
import javax.swing.text.PlainDocument

/**
 * The result of running a [TestLintTask].
 *
 * **NOTE: This is not a public or final API; if you rely on this be prepared to adjust your
 * code for the next tools release.**
 */

class TestLintResult internal constructor(
    private val task: TestLintTask,
    private val output: String?,
    private val throwable: Throwable?,
    private val incidents: List<Incident>
) {
    private var maxLineLength: Int = 0

    /**
     * Sets the maximum line length in the report. This is useful if some lines are particularly
     * long and you don't care about the details at the end of the line
     *
     * @param maxLineLength the maximum number of characters to show in the report
     * @return this
     */
    // Lint internally always using 100, but allow others
    fun maxLineLength(maxLineLength: Int): TestLintResult {
        this.maxLineLength = maxLineLength
        return this
    }

    /**
     * Checks that the lint result had the expected report format.
     *
     * @param expectedText the text to expect
     * @return this
     */
    @JvmOverloads
    fun expect(
        expectedText: String,
        expectedException: Class<out Exception>? = null,
        transformer: TestResultTransformer = TestResultTransformer { it }
    ): TestLintResult {
        if (expectedException == null && !task.allowExceptions && throwable != null) {
            throw throwable
        }

        val actual = transformer.transform(describeOutput(expectedException))
        val expected = expectedText.replace('$', '＄')

        if (actual.trim() != expected.trimIndent().trim()) {
            // See if it's a Windows path issue
            if (actual == expected.replace(File.separatorChar, '/')) {
                assertEquals(
                    "The expected lint output does not match, but it *does* " +
                        "match when Windows file separators (\\) are replaced by Unix ones.\n" +
                        "Make sure your lint detector calls LintClient.getDisplayPath(File) " +
                        "instead of displaying paths directly (in unit tests they will then " +
                        "be converted to forward slashes for test output stability.)\n",
                    expected,
                    actual
                )
            }

            assertEquals(expected.trimIndent(), actual.trimIndent())
        }
        cleanup()
        return this
    }

    private fun describeOutput(expectedException: Class<out Throwable>? = null): String {
        return formatOutput(this.output, expectedException)
    }

    private fun formatOutput(outputOrNull: String?, expectedThrowable: Class<out Throwable>?): String {
        var output = outputOrNull
        if (output == null) {
            output = ""
        } else if (maxLineLength > TRUNCATION_MARKER.length) {
            val sb = StringBuilder()
            for (line in Splitter.on('\n').split(output)) {
                val truncated =
                    if (line.length > maxLineLength) {
                        line.substring(0, maxLineLength - TRUNCATION_MARKER.length) +
                            TRUNCATION_MARKER
                    } else {
                        line
                    }
                sb.append(truncated).append('\n')
            }
            output = sb.toString()
            if (output.endsWith("\n\n") && !this.output!!.endsWith("\n\n")) {
                output = output.substring(0, output.length - 1)
            }
        }

        return if (throwable != null) {
            val writer = StringWriter()
            if (expectedThrowable != null && expectedThrowable.isInstance(throwable)) {
                writer.write("${throwable.message}\n")
            } else {
                throwable.printStackTrace(PrintWriter(writer))
            }

            if (output.isNotEmpty()) {
                writer.write(output)
            }

            writer.toString()
        } else {
            output.replace('$', '＄')
        }
    }

    /**
     * Checks that there were no errors or exceptions.
     *
     * @return this
     */
    fun expectClean(): TestLintResult {
        expect("No warnings.")
        cleanup()
        return this
    }

    /**
     * Checks that the results correspond to the messages inlined in the source files
     *
     * @return this
     */
    fun expectInlinedMessages(useRaw: Boolean = false): TestLintResult {
        for (project in task.projects) {
            for (file in project.files) {
                val plainContents: String?
                val contents: String?
                try {
                    plainContents = file.getContents()
                    contents = file.rawContents
                    if (contents == null || plainContents == null) {
                        continue
                    }
                } catch (ignore: Throwable) {
                    continue
                }

                val targetPath = file.targetPath
                val isXml = targetPath.endsWith(DOT_XML)

                try {
                    // TODO: Make comment token warnings depend on the source language
                    // For now, only handling Java

                    // We'll perform this check by going through all the files
                    // in the project, removing any inlined error messages in the file,
                    // then inserting error messages from the lint check, then asserting
                    // that the original file (with inlined error messages) is identical
                    // to the annotated file.

                    // We'll use a Swing document such that we can remove error comment
                    // ranges from the doc, and use the Position class to easily map
                    // offsets in reported errors to the corresponding location in the
                    // document after those edits have been made.

                    val doc = PlainDocument()
                    doc.insertString(0, if (isXml) plainContents else contents, null)
                    val positionMap = Maps.newHashMap<Int, javax.swing.text.Position>()

                    // Find any errors reported in this document
                    val matches = findIncidents(targetPath)

                    for (incident in matches) {
                        val location = incident.location
                        val start = location.start
                        val end = location.end

                        val startOffset = start?.offset ?: 0
                        val endOffset = end?.offset ?: 0

                        val startPos = doc.createPosition(startOffset)
                        val endPos = doc.createPosition(endOffset)

                        positionMap[startOffset] = startPos
                        positionMap[endOffset] = endPos
                    }

                    // Next remove any error regions from the document
                    stripMarkers(isXml, doc, contents)

                    // Finally write the expected errors back in
                    for (incident in matches) {
                        val location = incident.location

                        val start = location.start
                        val end = location.end

                        var startOffset = start?.offset ?: 0
                        var endOffset = end?.offset ?: 0

                        val startPos = positionMap[startOffset]!!
                        val endPos = positionMap[endOffset]!!

                        val startMarker: String
                        val endMarker: String
                        var message = incident.message

                        if (!useRaw) {
                            // Use plain ascii in the test golden files for now. (This also ensures
                            // that the markup is well-formed, e.g. if we have a ` without a matching
                            // closing `, the ` would show up in the plain text.)
                            message = TextFormat.RAW.convertTo(message, TextFormat.TEXT)
                        }
                        if (isXml) {
                            val tag = incident.severity.description.toLowerCase(Locale.ROOT)
                            startMarker = "<?$tag message=\"$message\"?>"
                            endMarker = "<?$tag?>"
                        } else {
                            // Java, Gradle, Kotlin, ...
                            startMarker = "/*$message*/"
                            endMarker = "/**/"
                        }

                        startOffset = startPos.offset
                        endOffset = endPos.offset

                        doc.insertString(endOffset, endMarker, null)
                        doc.insertString(startOffset, startMarker, null)
                    }

                    // Assert equality
                    assertEquals(contents, doc.getText(0, doc.length))
                } catch (ignore: BadLocationException) {
                }
            }
        }

        cleanup()
        return this
    }

    private fun findIncidents(targetFile: String): List<Incident> {
        // The target file should be platform neutral (/, not \ as separator)
        assertTrue(targetFile, !targetFile.contains("\\"))

        // Find any errors reported in this document
        val matches = Lists.newArrayList<Incident>()
        for (incident in incidents) {
            val path = incident.file.path.replace(File.separatorChar, '/')
            if (path.endsWith(targetFile)) {
                matches.add(incident)
            }
        }

        // Sort by descending offset
        matches.sortWith(comparator)

        return matches
    }

    /**
     * Checks that the lint report matches the given regular expression
     *
     * @param regexp the regular expression to match the input with (note that it's using
     * [Matcher.find], not [Matcher.match], so you don't have to include
     * wildcards at the beginning or end if looking for a match inside the report
     * @return this
     */
    @JvmOverloads
    fun expectMatches(
        @Language("RegExp") regexp: String,
        transformer: TestResultTransformer = TestResultTransformer { it }
    ): TestLintResult {
        val output = transformer.transform(describeOutput())
        val pattern = Pattern.compile(regexp, MULTILINE)
        val found = pattern.matcher(output).find()
        if (!found) {
            val reached = computeSubstringMatch(pattern, output)
            fail(
                "Did not find pattern\n  " +
                    regexp +
                    "\n in \n" +
                    output +
                    "; " +
                    "the incomplete match was " +
                    output.substring(0, reached)
            )
        }

        cleanup()
        return this
    }

    /**
     * Checks the output using the given custom checker, which should throw an exception
     * if the result is not as expected.
     *
     * @param checker the checker to apply, typically a lambda
     * @return this
     */
    @JvmOverloads
    fun check(
        checker: TestResultChecker,
        transformer: TestResultTransformer = TestResultTransformer { it }
    ): TestLintResult {
        val output = transformer.transform(describeOutput())
        checker.check(output)
        cleanup()
        return this
    }

    /**
     * Checks that the actual number of errors in this lint check matches exactly the given count
     *
     * @param expectedCount the expected error count
     * @return this
     */
    fun expectWarningCount(expectedCount: Int): TestLintResult {
        return expectCount(expectedCount, Severity.WARNING)
    }

    /**
     * Checks that the actual number of errors in this lint check matches exactly the given count
     *
     * @param expectedCount the expected error count
     * @return this
     */
    fun expectErrorCount(expectedCount: Int): TestLintResult {
        return expectCount(expectedCount, Severity.ERROR, Severity.FATAL)
    }

    /**
     * Checks that the actual number of problems with a given severity in this lint check matches
     * exactly the given count.
     *
     * @param expectedCount the expected count
     * @param severities the severities to count
     * @return this
     */
    fun expectCount(expectedCount: Int, vararg severities: Severity): TestLintResult {
        var count = 0
        for (incident in incidents) {
            if (ArrayUtil.contains(incident.severity, *severities)) {
                count++
            }
        }

        if (count != expectedCount) {
            assertEquals(
                "Expected " +
                    expectedCount +
                    " problems with severity " +
                    Joiner.on(" or ").join(severities) +
                    " but was " +
                    count,
                expectedCount.toLong(),
                count.toLong()
            )
        }

        return this
    }

    /** Verify quick fixes  */
    fun verifyFixes(): LintFixVerifier {
        return LintFixVerifier(task, incidents)
    }

    /**
     * Checks what happens with the given fix in this result as applied to the given test file, and
     * making sure that the result is the new contents
     *
     * @param fix the fix description, or null to pick the first one
     * @param after the file after applying the fix
     * @return this
     */
    fun checkFix(fix: String?, after: TestFile): TestLintResult {
        verifyFixes().checkFix(fix, after)
        return this
    }

    /**
     * Applies the fixes and provides diffs to all the files. Convenience wrapper around
     * [.verifyFixes] and [LintFixVerifier.expectFixDiffs] if you don't want to
     * configure any diff options.
     *
     * @param expected the diff description resulting from applying the diffs
     * @return this
     */
    fun expectFixDiffs(expected: String): TestLintResult {
        verifyFixes().expectFixDiffs(expected)
        return this
    }

    /**
     * Checks that the HTML report is as expected
     *
     * @param checkers one or more checks to apply to the output
     */
    fun checkHtmlReport(
        vararg checkers: TestResultChecker,
        transformer: TestResultTransformer = TestResultTransformer { it }
    ): TestLintResult {
        return checkReport(
            html = true,
            includeFixes = false,
            fullPaths = false,
            intendedForBaseline = false,
            transformer = transformer,
            checkers = *checkers
        )
    }

    /**
     * Checks that the XML report is as expected XML
     *
     * @param expected the expected XML report
     */
    fun expectHtml(
        @Language("HTML") expected: String,
        transformer: TestResultTransformer = TestResultTransformer { it }
    ): TestLintResult {
        return checkHtmlReport(
            TestResultChecker { s ->
                assertEquals(
                    expected,
                    s
                )
            },
            transformer = transformer
        )
    }

    /**
     * Checks that the XML report is as expected
     *
     * @param checkers one or more checks to apply to the output
     */
    fun checkXmlReport(
        vararg checkers: TestResultChecker,
        transformer: TestResultTransformer = TestResultTransformer { it }
    ): TestLintResult {
        return checkReport(
            html = false,
            includeFixes = false,
            fullPaths = false,
            intendedForBaseline = false,
            transformer = transformer,
            checkers = *checkers
        )
    }

    /**
     * Checks that the XML report is as expected XML
     *
     * @param expected the expected XML report
     */
    fun expectXml(
        @Language("XML") expected: String,
        transformer: TestResultTransformer = TestResultTransformer { it }
    ): TestLintResult {
        return checkXmlReport(
            TestResultChecker { s ->
                if (s != expected && s.replace('\\', '/') == expected) {
                    // Allow Windows file separators to differ
                } else {
                    assertEquals(
                        expected,
                        s
                    )
                }
            },
            transformer = transformer
        )
    }

    /**
     * Checks that the XML report, optionally fix descriptions, full paths, etc, is as expected
     *
     * @param checkers one or more checks to apply to the output
     */
    fun checkXmlReport(
        vararg checkers: TestResultChecker,
        includeFixes: Boolean = false,
        fullPaths: Boolean = false,
        intendedForBaseline: Boolean = false,
        transformer: TestResultTransformer = TestResultTransformer { it }
    ): TestLintResult {
        return checkReport(
            false,
            includeFixes,
            fullPaths,
            intendedForBaseline,
            transformer,
            *checkers
        )
    }

    /**
     * Checks that the XML report, including fix descriptions, is as expected
     *
     * @param expected the expected XML report
     */
    fun expectXml(
        @Language("XML") expected: String,
        includeFixes: Boolean = false,
        fullPaths: Boolean = false,
        intendedForBaseline: Boolean = false,
        transformer: TestResultTransformer = TestResultTransformer { it }
    ): TestLintResult {
        return checkXmlReport(
            checkers = *arrayOf(
                TestResultChecker { s ->
                    if (s != expected && s.replace('\\', '/') == expected) {
                        // Allow Windows file separators to differ
                    } else {
                        assertEquals(
                            expected,
                            s
                        )
                    }
                }
            ),
            includeFixes = includeFixes,
            fullPaths = fullPaths,
            intendedForBaseline = intendedForBaseline,
            transformer = transformer
        )
    }

    private fun checkReport(
        html: Boolean,
        includeFixes: Boolean = false,
        fullPaths: Boolean = false,
        intendedForBaseline: Boolean = false,
        transformer: TestResultTransformer = TestResultTransformer { it },
        vararg checkers: TestResultChecker
    ): TestLintResult {
        try {
            // Place the report file near the project sources if possible to make absolute
            // paths (in HTML Reports etc, which are relative to the report) as close as
            // possible
            val root = task.dirToProjectDescription.keys.firstOrNull()?.parentFile
            val name = "test-lint"
            val extension = if (html) ".html" else DOT_XML

            val file =
                if (root != null) {
                    File(root, name + extension)
                } else {
                    File.createTempFile(name, extension)
                }
            val client = incidents.firstNotNullResult {
                it.project?.client as? TestLintClient
            }
                ?: object : TestLintClient() {
                    override fun getClientRevision(): String? {
                        // HACK
                        if (registry == null) {
                            registry = BuiltinIssueRegistry()
                        }
                        return super.getClientRevision()
                    }
                }.apply {
                    getClientRevision() // force registry initialization
                }

            val reporter = if (html)
                Reporter.createHtmlReporter(client, file, client.flags)
            else
                Reporter.createXmlReporter(client, file, false, includeFixes)
            if (includeFixes) {
                (reporter as XmlReporter).includeFixes = true
            }
            if (intendedForBaseline) {
                (reporter as XmlReporter).isIntendedForBaseline = true
            }
            val oldFullPath = client.flags.isFullPath
            if (fullPaths) {
                client.flags.isFullPath = true
            }
            val stats = LintStats.create(incidents, null as LintBaseline?)
            reporter.write(stats, incidents)
            val actual = Files.asCharSource(file, Charsets.UTF_8).read()
            val transformed = transformer.transform(actual)
            for (checker in checkers) {
                checker.check(transformed)
            }

            // Make sure the XML is valid
            if (!html) {
                try {
                    val document = PositionXmlParser.parse(actual)
                    assertNotNull(document)
                    assertEquals(
                        incidents.size.toLong(),
                        document.getElementsByTagName("issue").length.toLong()
                    )
                } catch (t: Throwable) {
                    throw RuntimeException("Could not parse XML report file: " + t.message, t)
                }
            }
            client.flags.isFullPath = oldFullPath
        } catch (ioe: IOException) {
            fail(ioe.message)
        }

        cleanup()
        return this
    }

    private fun cleanup() {
        task.client?.disposeProjects(emptyList())
        UastEnvironment.disposeApplicationEnvironment()
    }

    @Throws(BadLocationException::class)
    private fun stripMarkers(isXml: Boolean, doc: Document, contents: String) {

        if (isXml) {
            // For processing instructions just remove all non-XML processing instructions
            // (we don't need to match beginning and ending ones)
            var index = contents.length
            while (index >= 0) {
                val endEndOffset = contents.lastIndexOf("?>", index)
                if (endEndOffset == -1) {
                    break
                }
                val endStartOffset = contents.lastIndexOf("<?", endEndOffset)
                if (endStartOffset == -1) {
                    break
                }
                if (contents.startsWith("<?xml", endStartOffset)) {
                    index = endStartOffset - 1
                    continue
                }

                doc.remove(endStartOffset, endEndOffset + "?>".length - endStartOffset)

                index = endStartOffset
            }
        } else {
            // For Java/Groovy/Kotlin etc we don't want to remove *all* block comments;
            // only those that end with /**/. Note that this may not handle nested
            // ones correctly.
            var index = contents.length
            while (index >= 0) {
                val endOffset = contents.lastIndexOf("/**/", index)
                if (endOffset == -1) {
                    break
                }
                val regionStart = contents.lastIndexOf("/*", endOffset - 1)
                if (regionStart == -1) {
                    break
                }
                val commentEnd = contents.indexOf("*/", regionStart + 2)
                if (commentEnd == -1 || commentEnd > endOffset) {
                    break
                }

                doc.remove(endOffset, 4)
                doc.remove(regionStart, commentEnd + 2 - regionStart)

                index = regionStart
            }
        }
    }

    private fun computeSubstringMatch(pattern: Pattern, output: String): Int {
        for (i in output.length - 1 downTo 1) {
            val partial = output.substring(0, i)
            val matcher = pattern.matcher(partial)
            if (!matcher.matches() && matcher.hitEnd()) {
                return i
            }
        }

        return 0
    }

    companion object {
        private const val TRUNCATION_MARKER = "\u2026"
        val comparator: Comparator<Incident> = Comparator { o1, o2 -> o2.startOffset - o1.startOffset }
    }
}
