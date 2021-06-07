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
import com.android.tools.lint.LintStats
import com.android.tools.lint.Reporter
import com.android.tools.lint.UastEnvironment
import com.android.tools.lint.XmlFileType
import com.android.tools.lint.checks.BuiltinIssueRegistry
import com.android.tools.lint.client.api.LintBaseline
import com.android.tools.lint.detector.api.Incident
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.util.regex.Pattern
import java.util.regex.Pattern.DOTALL
import java.util.regex.Pattern.MULTILINE
import javax.swing.text.BadLocationException
import javax.swing.text.Document
import javax.swing.text.PlainDocument
import kotlin.math.max
import kotlin.math.min

/**
 * The result of running a [TestLintTask].
 *
 * **NOTE: This is not a public or final API; if you rely on this be
 * prepared to adjust your code for the next tools release.**
 */

class TestLintResult internal constructor(
    private val task: TestLintTask,
    private val states: MutableMap<TestMode, TestResultState>,
    /**
     * The mode to use for result tasks that do not pass in a specific
     * mode to check.
     */
    private val defaultMode: TestMode
) {
    private var maxLineLength: Int = 0

    /**
     * Sets the maximum line length in the report. This is useful if
     * some lines are particularly long and you don't care about the
     * details at the end of the line
     *
     * @param maxLineLength the maximum number of characters to show in
     *     the report
     * @return this
     */
    // Lint internally always using 100, but allow others
    fun maxLineLength(maxLineLength: Int): TestLintResult {
        this.maxLineLength = maxLineLength
        return this
    }

    /**
     * Checks that the lint result had the expected report format.
     * The [expectedText] is the output of the text report
     * generated from the text (which you can customize with
     * [TestLintTask.textFormat].) If the lint check is expected
     * to throw an exception, you can pass in its class with
     * [expectedException]. The [transformer] lets you modify the test
     * results; the default one will unify paths and remove absolute
     * paths to just be relative to the test roots. Finally, the
     * [testMode] lets you check a particular test type output.
     *
     * @param expectedText the text to expect
     * @param expectedException class, if any
     * @param transformer a mapping function which can modify the result
     *     before comparison
     * @param testMode the test mode whose results to check
     * @return this
     */
    @JvmOverloads
    fun expect(
        expectedText: String,
        expectedException: Class<out Exception>? = null,
        transformer: TestResultTransformer = TestResultTransformer { it },
        testMode: TestMode = defaultMode
    ): TestLintResult {
        // If not expecting an exception, and not allowing any, make sure we
        // didn't have any exceptions across all states, not just one.
        // We don't want to require people to check each test mode individually
        // and we don't need the power to individually expect exceptions separately
        // for each mode.
        if (expectedException == null && !task.allowExceptions) {
            states.asSequence().mapNotNull { it.value.firstThrowable }.firstOrNull()?.let {
                throw it
            }
        }

        val actual = transformer.transform(describeOutput(expectedException, testMode))
        val expected = normalizeOutput(expectedText)

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

    /** Checks that the lint report contains the given [substring] */
    @JvmOverloads
    fun expectContains(
        expectedText: String,
        transformer: TestResultTransformer = TestResultTransformer { it },
        testMode: TestMode = defaultMode
    ): TestLintResult {
        val actual = transformer.transform(describeOutput(null, testMode))
        val expected = normalizeOutput(expectedText)

        val expectedWithoutIndent = expected.trimIndent()
        val unixPath = expectedWithoutIndent.replace(File.separatorChar, '/')
        if (!actual.trim().contains(expectedWithoutIndent.trim())) {
            // See if it's a Windows path issue
            if (actual.contains(unixPath)) {
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
        }

        assertTrue(
            "Not true that\n\"$expectedWithoutIndent\$\nis found in lint output\n\"$actual\$",
            actual.contains(expectedWithoutIndent)
        )

        cleanup()
        return this
    }

    private fun describeOutput(
        expectedException: Class<out Throwable>? = null,
        testMode: TestMode = defaultMode
    ): String {
        val state = states[testMode]!!
        return formatOutput(state.output, state.firstThrowable, expectedException)
    }

    /**
     * The test output is already formatted by the text reporter in
     * the states passed in to this result, but we do some extra post
     * processing here to truncate output, clean up whitespace only
     * diffs, etc.
     */
    private fun formatOutput(
        originalOutput: String,
        throwable: Throwable?,
        expectedThrowable: Class<out Throwable>?
    ): String {
        var output = originalOutput
        if (maxLineLength > TRUNCATION_MARKER.length) {
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
            if (output.endsWith("\n\n") && !originalOutput.endsWith("\n\n")) {
                output = output.substring(0, output.length - 1)
            }
        }

        return if (throwable != null) {
            val writer = StringWriter()
            if (expectedThrowable != null && expectedThrowable.isInstance(throwable)) {
                val throwableMessage = throwable.message
                if (throwableMessage != null && !output.contains(throwableMessage)) {
                    writer.write("$throwableMessage\n")
                }
            } else {
                throwable.printStackTrace(PrintWriter(writer))
            }

            if (output.isNotEmpty()) {
                writer.write(normalizeOutput(output))
            }

            writer.toString()
        } else {
            normalizeOutput(output)
        }
    }

    private fun normalizeOutput(output: String): String {
        if (output.contains(OLD_ERROR_MESSAGE) || output.contains("$")) {
            val first = output.replace('$', '＄')
            return MATCH_OLD_ERROR_MESSAGE.replace(first) { "$OLD_ERROR_MESSAGE>${output[it.range.last]}" }
        }

        return output
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
     * Checks that the results correspond to the messages inlined in the
     * source files
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
                    val matches = findIncidents(targetPath, defaultMode)

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
                            val tag = incident.severity.toName()
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

    private fun findIncidents(targetFile: String, mode: TestMode): List<Incident> {
        val state = states[mode]!!
        val incidents = state.incidents

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
     * @param regexp the regular expression to match the input with
     *     (note that it's using [Matcher.find], not [Matcher.match],
     *     so you don't have to include wildcards at the beginning
     *     or end if looking for a match inside the report
     * @return this
     */
    @JvmOverloads
    fun expectMatches(
        @Language("RegExp") regexp: String,
        transformer: TestResultTransformer = TestResultTransformer { it }
    ): TestLintResult {
        val throwable = states[defaultMode]?.firstThrowable
        if (!task.allowExceptions && throwable != null) {
            throw throwable
        }
        val output = transformer.transform(describeOutput())
        var pattern = Pattern.compile(regexp, MULTILINE or DOTALL)
        var found = pattern.matcher(output).find()
        if (!found) {
            pattern = Pattern.compile(regexp.trimIndent(), MULTILINE or DOTALL)
            found = pattern.matcher(output).find()
        }
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
     * Checks the output using the given custom checker, which should
     * throw an exception if the result is not as expected.
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
     * Checks that the actual number of errors in this lint check
     * matches exactly the given count
     *
     * @param expectedCount the expected error count
     * @return this
     */
    fun expectWarningCount(expectedCount: Int): TestLintResult {
        return expectCount(expectedCount, Severity.WARNING)
    }

    /**
     * Checks that the actual number of errors in this lint check
     * matches exactly the given count
     *
     * @param expectedCount the expected error count
     * @return this
     */
    fun expectErrorCount(expectedCount: Int): TestLintResult {
        return expectCount(expectedCount, Severity.ERROR, Severity.FATAL)
    }

    /**
     * Checks that the actual number of problems with a given severity
     * in this lint check matches exactly the given count.
     *
     * @param expectedCount the expected count
     * @param severities the severities to count
     * @return this
     */
    @Suppress("MemberVisibilityCanBePrivate") // Also allow calls by 3rd party checks
    fun expectCount(expectedCount: Int, vararg severities: Severity): TestLintResult {
        val state = states[defaultMode]!!
        val throwable = state.firstThrowable
        val incidents = state.incidents

        if (!task.allowExceptions && throwable != null) {
            throw throwable
        }
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

    /** Verify quick fixes. */
    fun verifyFixes(): LintFixVerifier {
        return LintFixVerifier(task, states[defaultMode]!!)
    }

    /**
     * Verify quick fixes for a particular test type. Most checks have
     * identical output and quickfixes across test types, but not all,
     * so this lets you test individual fixes for each type.
     */
    fun verifyFixes(testMode: TestMode): LintFixVerifier {
        return LintFixVerifier(task, states[testMode]!!)
    }

    /**
     * Checks what happens with the given fix in this result as applied
     * to the given test file, and making sure that the result is the
     * new contents
     *
     * @param fix the fix description, or null to pick the first one
     * @param after the file after applying the fix
     * @return this
     */
    @Suppress("Unused") // Library method
    fun checkFix(fix: String?, after: TestFile): TestLintResult {
        verifyFixes().checkFix(fix, after)
        return this
    }

    /**
     * Applies the fixes and provides diffs to all the
     * files. Convenience wrapper around [.verifyFixes] and
     * [LintFixVerifier.expectFixDiffs] if you don't want to configure
     * any diff options.
     *
     * @param expected the diff description resulting from applying the
     *     diffs
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
    @Suppress("MemberVisibilityCanBePrivate") // Also allow calls by 3rd party checks
    fun checkHtmlReport(
        vararg checkers: TestResultChecker,
        transformer: TestResultTransformer = TestResultTransformer { it }
    ): TestLintResult {
        return checkReport(
            html = true,
            fullPaths = false,
            xmlReportType = XmlFileType.REPORT,
            transformer = transformer,
            checkers = checkers
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
        val trimmed = normalizeOutput(expected.trimIndent())
        return checkHtmlReport(
            TestResultChecker { actual ->
                val s = normalizeOutput(actual.trimIndent())
                if (s != trimmed && s.replace('\\', '/') == trimmed) {
                    // Allow Windows file separators to differ
                } else {
                    assertEquals(trimmed, s)
                }
            },
            transformer = transformer
        )
    }

    /**
     * Checks that the XML report is as expected
     *
     * @param checkers one or more checks to apply to the output
     */
    @Suppress("MemberVisibilityCanBePrivate") // Also allow calls by 3rd party checks
    fun checkXmlReport(
        vararg checkers: TestResultChecker,
        transformer: TestResultTransformer = TestResultTransformer { it }
    ): TestLintResult {
        return checkReport(
            xml = true,
            fullPaths = false,
            xmlReportType = XmlFileType.REPORT,
            transformer = transformer,
            checkers = checkers
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
                    assertEquals(expected, s)
                }
            },
            transformer = transformer
        )
    }

    /**
     * Checks that the XML report, optionally fix descriptions, full
     * paths, etc, is as expected
     *
     * @param checkers one or more checks to apply to the output
     */
    fun checkXmlReport(
        vararg checkers: TestResultChecker,
        fullPaths: Boolean = false,
        reportType: XmlFileType = XmlFileType.REPORT,
        transformer: TestResultTransformer = TestResultTransformer { it }
    ): TestLintResult {
        return checkReport(
            xml = true,
            fullPaths = fullPaths,
            xmlReportType = reportType,
            transformer = transformer,
            checkers = checkers
        )
    }

    /**
     * Checks that the XML report, including fix descriptions, is as
     * expected
     *
     * @param expected the expected XML report
     */
    fun expectXml(
        @Language("XML") expected: String,
        fullPaths: Boolean = false,
        reportType: XmlFileType = XmlFileType.REPORT,
        transformer: TestResultTransformer = TestResultTransformer { it }
    ): TestLintResult {
        val trimmed = normalizeOutput(expected.trimIndent())
        return checkXmlReport(
            fullPaths = fullPaths,
            reportType = reportType,
            transformer = transformer,
            checkers = arrayOf(
                TestResultChecker { actual ->
                    val s = normalizeOutput(actual.trimIndent())
                    if (s != trimmed && s.replace('\\', '/') == trimmed) {
                        // Allow Windows file separators to differ
                    } else {
                        assertEquals(trimmed, s)
                    }
                }
            ),
        )
    }

    /**
     * Checks that the SARIF report is as expected
     *
     * @param expected the expected SARIF report
     */
    fun expectSarif(
        @Language("JSON") expected: String,
        transformer: TestResultTransformer = TestResultTransformer { it }
    ): TestLintResult {
        val trimmed = normalizeOutput(expected.trimIndent())
        return checkSarifReport(
            transformer = transformer,
            checkers = arrayOf(
                TestResultChecker { actual ->
                    val s = normalizeOutput(actual.trimIndent())
                    if (s != trimmed && s.replace('\\', '/') == trimmed) {
                        // Allow Windows file separators to differ
                    } else {
                        assertEquals(trimmed, s)
                    }
                }
            ),
        )
    }

    /**
     * Checks that the SARIF report is as expected
     *
     * @param checkers one or more checks to apply to the output
     */
    @Suppress("MemberVisibilityCanBePrivate") // Also allow calls by 3rd party checks
    fun checkSarifReport(
        vararg checkers: TestResultChecker,
        transformer: TestResultTransformer = TestResultTransformer { it }
    ): TestLintResult {
        return checkReport(
            sarif = true,
            transformer = transformer,
            checkers = checkers
        )
    }

    private inline fun <T, R : Any> Iterable<T>.firstNotNullResult(transform: (T) -> R?): R? {
        for (element in this) {
            val result = transform(element)
            if (result != null) return result
        }
        return null
    }

    private fun checkReport(
        xml: Boolean = false,
        html: Boolean = false,
        sarif: Boolean = false,
        xmlReportType: XmlFileType = XmlFileType.REPORT,
        fullPaths: Boolean = false,
        transformer: TestResultTransformer = TestResultTransformer { it },
        vararg checkers: TestResultChecker
    ): TestLintResult {
        assertTrue(xml || html || sarif)
        val state = states[defaultMode]!!
        val throwable = state.firstThrowable
        val incidents = state.incidents
        if (!task.allowExceptions && throwable != null) {
            throw throwable
        }
        try {
            // Place the report file near the project sources if possible to make absolute
            // paths (in HTML Reports etc, which are relative to the report) as close as
            // possible
            val root = task.dirToProjectDescription.keys.firstOrNull()?.parentFile
            val name = "test-lint"
            val extension = if (html) ".html" else if (sarif) ".sarif" else DOT_XML

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

            file.parentFile.mkdirs()
            val reporter = when {
                html -> Reporter.createHtmlReporter(client, file, client.flags)
                xml -> Reporter.createXmlReporter(client, file, xmlReportType)
                else -> Reporter.createSarifReporter(client, file)
            }

            val oldFullPath = client.flags.isFullPath
            if (fullPaths) {
                client.flags.isFullPath = true
            }
            val stats = LintStats.create(incidents, null as LintBaseline?)
            reporter.write(stats, incidents)
            val actual = normalizeOutput(Files.asCharSource(file, Charsets.UTF_8).read())
            val defaultState = states[defaultMode]!!
            val transformed = task.stripRoot(defaultState.rootDir, transformer.transform(actual))
            for (checker in checkers) {
                checker.check(transformed)
            }

            // Make sure the XML is valid
            if (xml) {
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
        for (state in states.values) {
            state.client.disposeProjects(emptyList())
        }
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

        // TestLintClient used to have a typo (it would emit the message
        // "<No location-specific message", without the closing >.)
        // We don't want to just change the error message to the new spelling,
        // since that would break a lot of existing golden files. Instead,
        // we normalize both the expected and actual test output to use
        // the correct format, and only if they differ does the test fail.
        // This means that going forward, new tests will use the correct
        // format but old tests continue to pass.
        private const val OLD_ERROR_MESSAGE = "No location-specific message"
        private val MATCH_OLD_ERROR_MESSAGE = Regex("$OLD_ERROR_MESSAGE[^>]")

        /** Returns a test-suitable diff of the two strings. */
        fun getDiff(before: String, after: String): String {
            return getDiff(before, after, 0)
        }

        /**
         * Returns a test-suitable diff of the two strings, including
         * [windowSize] lines around.
         */
        fun getDiff(before: String, after: String, windowSize: Int): String {
            return getDiff(
                if (before.isEmpty()) emptyArray() else before.split("\n").toTypedArray(),
                if (after.isEmpty()) emptyArray() else after.split("\n").toTypedArray(),
                windowSize
            )
        }

        /**
         * Returns a test-suitable diff of the two string arrays,
         * including [windowSize] lines of delta.
         */
        fun getDiff(
            before: Array<String>,
            after: Array<String>,
            windowSize: Int = 0
        ): String {
            // Based on the LCS section in http://introcs.cs.princeton.edu/java/96optimization/
            val sb = java.lang.StringBuilder()
            val n = before.size
            val m = after.size

            // Compute longest common subsequence of x[i..m] and y[j..n] bottom up
            val lcs = Array(n + 1) {
                IntArray(
                    m + 1
                )
            }
            for (i in n - 1 downTo 0) {
                for (j in m - 1 downTo 0) {
                    if (before[i] == after[j]) {
                        lcs[i][j] = lcs[i + 1][j + 1] + 1
                    } else {
                        lcs[i][j] = max(lcs[i + 1][j], lcs[i][j + 1])
                    }
                }
            }
            var i = 0
            var j = 0
            while (i < n && j < m) {
                if (before[i] == after[j]) {
                    i++
                    j++
                } else {
                    sb.append("@@ -")
                    sb.append((i + 1).toString())
                    sb.append(" +")
                    sb.append((j + 1).toString())
                    sb.append('\n')
                    if (windowSize > 0) {
                        for (context in max(0, i - windowSize) until i) {
                            sb.append("  ")
                            sb.append(before[context].trimEnd())
                            sb.append("\n")
                        }
                    }
                    while (i < n && j < m && before[i] != after[j]) {
                        if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                            sb.append('-')
                            if (before[i].trim().isNotEmpty()) {
                                sb.append(' ')
                            }
                            sb.append(before[i].trimEnd())
                            sb.append('\n')
                            i++
                        } else {
                            sb.append('+')
                            if (after[j].trim().isNotEmpty()) {
                                sb.append(' ')
                            }
                            sb.append(after[j].trimEnd())
                            sb.append('\n')
                            j++
                        }
                    }
                    if (windowSize > 0) {
                        for (context in i until min(n, i + windowSize)) {
                            sb.append("  ")
                            sb.append(before[context].trimEnd())
                            sb.append("\n")
                        }
                    }
                }
            }
            if (i < n || j < m) {
                assert(i == n || j == m)
                sb.append("@@ -")
                sb.append((i + 1).toString())
                sb.append(" +")
                sb.append((j + 1).toString())
                sb.append('\n')
                while (i < n) {
                    sb.append('-')
                    if (before[i].trim().isNotEmpty()) {
                        sb.append(' ')
                    }
                    sb.append(before[i])
                    sb.append('\n')
                    i++
                }
                while (j < m) {
                    sb.append('+')
                    if (after[j].trim().isNotEmpty()) {
                        sb.append(' ')
                    }
                    sb.append(after[j])
                    sb.append('\n')
                    j++
                }
            }
            return sb.toString().trim()
        }
    }
}
