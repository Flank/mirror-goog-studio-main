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
package com.android.tools.lint.checks.infrastructure

import com.android.SdkConstants.ANDROID_NS_NAME
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.DOT_XML
import com.android.SdkConstants.TOOLS_URI
import com.android.SdkConstants.XMLNS_PREFIX
import com.android.ide.common.xml.XmlPrettyPrinter
import com.android.tools.lint.LintFixPerformer
import com.android.tools.lint.LintFixPerformer.Companion.createAnnotationFix
import com.android.tools.lint.LintFixPerformer.Companion.getLocation
import com.android.tools.lint.checks.infrastructure.TestLintResult.Companion.getDiff
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.LintFix.AnnotateFix
import com.android.tools.lint.detector.api.LintFix.GroupType
import com.android.tools.lint.detector.api.LintFix.LintFixGroup
import com.android.tools.lint.detector.api.LintFix.SetAttribute
import com.android.tools.lint.detector.api.LintFix.ShowUrl
import com.android.utils.PositionXmlParser
import com.android.utils.XmlUtils
import com.google.common.collect.Lists
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.SAXException
import java.io.File
import java.io.IOException
import java.util.regex.Pattern
import javax.xml.parsers.ParserConfigurationException

/**
 * Verifier which can simulate IDE quickfixes and check fix data.
 *
 * TODO: Merge with [LintFixPerformer] (though they work slightly
 *     differently; the fix verifier shows each individual
 *     fix applied to the doc in sequence, whereas the fix
 *     performer accumulates all the fixes into a single edit.
 *     But we should be able to share a bunch of the logic.)
 */
class LintFixVerifier(private val task: TestLintTask, state: TestResultState) {
    private val incidents: List<Incident> = state.incidents
    private val client: TestLintClient = state.client
    private var diffWindow = 0
    private var reformat: Boolean? = null
    private var robot = false

    /** Sets up 2 lines of context in the diffs */
    fun window(): LintFixVerifier {
        diffWindow = 2
        return this
    }

    /**
     * Specifies whether a robot is driving the quickfixes (which means
     * it will only allow auto-fixable lint fixes. Default is false.
     */
    fun robot(isRobot: Boolean): LintFixVerifier {
        robot = isRobot
        return this
    }

    /** Sets up a specific number of lines of contexts around diffs */
    fun window(size: Int): LintFixVerifier {
        assertTrue(size in 0..100)
        diffWindow = size
        return this
    }

    /**
     * Sets whether lint should reformat before and after files before
     * diffing. If not set explicitly to true or false, it will default
     * to true for XML files that set/remove attributes and false
     * otherwise. (May not have any effect on other file types than
     * XML.)
     */
    fun reformatDiffs(reformatDiffs: Boolean): LintFixVerifier {
        reformat = reformatDiffs
        return this
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
    fun checkFix(fix: String?, after: TestFile): LintFixVerifier {
        checkFixes(fix, after, null)
        return this
    }

    /**
     * Applies the fixes and provides diffs of all the affected files,
     * then compares it against the expected result.
     *
     * @param expected the diff description resulting from applying the
     *     diffs
     * @return this
     */
    fun expectFixDiffs(expected: String): LintFixVerifier {
        var expected = expected
        val diff = StringBuilder(100)
        checkFixes(null, null, diff)
        val actual = diff.toString().replace("\r\n", "\n").trimIndent().replace('$', '＄')
        expected = expected.trimIndent().replace('$', '＄')
        if (expected != actual &&
            // Also allow trailing spaces in embedded lines since the old differ
            // included that
            actual.replace("\\s+\n".toRegex(), "\n")
                .trim { it <= ' ' } != expected.replace("\\s+\n".toRegex(), "\n").trim { it <= ' ' }
        ) {
            // Until 3.2 canary 10 the line numbers were off by one; try adjusting
            if (bumpFixLineNumbers(expected.replace("\\s+\n".toRegex(), "\n"))
                .trim { it <= ' ' } != actual.replace("\\s+\n".toRegex(), "\n").trim { it <= ' ' }
            ) {
                // If not implicitly matching with whitespace cleanup and number adjustments
                // just assert that they're equal -- this will never be true but we want
                // the test failure output to show the original comparison such that updated
                // test copying from the diff includes the new normalized output.
                assertEquals(expected, actual)
            }
        }
        return this
    }

    private fun findTestFile(path: String): TestFile? {
        val unixPath = path.replace(File.separatorChar, '/')
        for (project in task.projects) {
            for (file in project.files) {
                val targetPath = file.targetPath
                if (targetPath == unixPath) {
                    return file
                }
            }
        }
        for (project in task.projects) {
            for (file in project.files) {
                val targetPath = file.targetPath
                if (unixPath.endsWith(targetPath)) {
                    return file
                }
            }
        }
        return null
    }

    private fun checkFixes(
        fixName: String?,
        expectedFile: TestFile?,
        diffs: StringBuilder?
    ) {
        assertTrue(expectedFile != null || diffs != null)
        val names: MutableList<String?> = Lists.newArrayList()
        for (incident in incidents) {
            val fix = incident.fix ?: continue
            if (robot && !fix.robot) {
                // Fix requires human intervention
                continue
            }
            val list: List<LintFix> = if (fix is LintFixGroup) {
                if (fix.type == GroupType.COMPOSITE) {
                    // separated out again in applyFix
                    listOf(fix)
                } else {
                    fix.fixes
                }
            } else {
                listOf(fix)
            }
            for (lintFix in list) {
                val location = getLocation(incident)
                val project = incident.project
                val targetPath = project?.getDisplayPath(location.file) ?: location.file.path
                val file = findTestFile(targetPath) ?: error("Didn't find test file $targetPath")
                var before = file.getContents() ?: continue
                assertNotNull(file.targetPath, before)
                if (lintFix is LintFix.DataMap && diffs != null) {
                    // Doesn't edit file, but include in diffs so fixes can verify the
                    // correct data is passed
                    appendDataMap(incident, lintFix, diffs)
                } else if (lintFix is ShowUrl && diffs != null) {
                    appendShowUrl(incident, lintFix, diffs)
                }
                var reformat = reformat
                var after = applyFix(incident, lintFix, before) ?: continue
                if (reformat == null && haveSetAttribute(lintFix)) {
                    reformat = true
                }
                if (expectedFile != null) {
                    assertEquals(
                        expectedFile.getContents()!!.trimIndent(),
                        after.trimIndent()
                    )
                }
                if (diffs != null) {
                    if (reformat != null && reformat &&
                        incident.getDisplayPath().endsWith(DOT_XML)
                    ) {
                        try {
                            before = XmlPrettyPrinter.prettyPrint(
                                XmlUtils.parseDocument(before, true), true
                            )
                            after = XmlPrettyPrinter.prettyPrint(
                                XmlUtils.parseDocument(after, true), true
                            )
                        } catch (e: SAXException) {
                            throw RuntimeException(e)
                        } catch (e: IOException) {
                            throw RuntimeException(e)
                        }
                    }
                    appendDiff(incident, lintFix.displayName, before, after, diffs)
                }
                val name = lintFix.displayName
                if (fixName != null && fixName != name) {
                    if (!names.contains(name)) {
                        names.add(name)
                    }
                    continue
                }
                names.add(fixName)
            }
        }
    }

    private fun applyFix(
        incident: Incident,
        lintFix: LintFix,
        before: String
    ): String? {
        if (lintFix is LintFix.ReplaceString) {
            return checkReplaceString(lintFix, incident, before)
        } else if (lintFix is SetAttribute) {
            return checkSetAttribute(lintFix, before, incident)
        } else if (lintFix is AnnotateFix) {
            return checkAnnotationFix(lintFix, before, incident)
        } else if (lintFix is LintFixGroup && lintFix.type == GroupType.COMPOSITE) {
            var cumulative = before
            for (nested in lintFix.fixes) {
                val after = applyFix(incident, nested, cumulative) ?: return null
                cumulative = after
            }
            return cumulative
        }
        return null
    }

    private fun checkAnnotationFix(
        fix: AnnotateFix,
        contents: String,
        incident: Incident
    ): String? {
        val replaceFix = createAnnotationFix(
            fix, if (fix.range != null) fix.range else incident.location, contents
        )
        return checkReplaceString(replaceFix, incident, contents)
    }

    private fun checkSetAttribute(
        setFix: SetAttribute,
        contents: String,
        incident: Incident
    ): String {
        val location = if (setFix.range != null) setFix.range else incident.location
        location ?: return contents
        val start = location.start ?: return contents
        return try {
            val document: Document = PositionXmlParser.parse(contents)
            var node = PositionXmlParser.findNodeAtOffset(document, start.offset)
            assertNotNull("No node found at offset " + start.offset, node)
            node!!
            if (node.nodeType == Node.ATTRIBUTE_NODE) {
                node = (node as Attr).ownerElement
            } else if (node.nodeType != Node.ELEMENT_NODE) {
                // text, comments
                node = node.parentNode
            }
            if (node == null || node.nodeType != Node.ELEMENT_NODE) {
                fail(
                    "Didn't find element at offset ${start.offset} (line ${start.line}1, " +
                        "column ${start.column}1) in ${incident.getDisplayPath()}:\n$contents"
                )
            }
            val element = node as Element
            var value = setFix.value
            val namespace = setFix.namespace
            if (value == null) {
                if (namespace != null) {
                    element.removeAttributeNS(namespace, setFix.attribute)
                } else {
                    element.removeAttribute(setFix.attribute)
                }
            } else {
                // Indicate the caret position by "|"
                if (setFix.dot >= 0 && setFix.dot <= value.length) {
                    value = if (setFix.mark >= 0 && setFix.mark != setFix.dot) {
                        // Selection
                        assert(setFix.mark < setFix.dot)
                        value.substring(0, setFix.mark) + "[" + value.substring(setFix.mark, setFix.dot) +
                            "]|" + value.substring(setFix.dot)
                    } else {
                        // Just caret
                        value.substring(0, setFix.dot) + "|" + value.substring(setFix.dot)
                    }
                }
                if (namespace != null) {
                    // Workaround for the fact that the namespace-setter method
                    // doesn't seem to work on these documents
                    var prefix = document.lookupPrefix(namespace)
                    if (prefix == null) {
                        val base = when {
                            ANDROID_URI == namespace -> ANDROID_NS_NAME
                            TOOLS_URI == namespace -> "tools"
                            AUTO_URI == namespace -> "app"
                            else -> "ns"
                        }
                        val root = document.documentElement
                        var index = 1
                        while (true) {
                            prefix = base + if (index == 1) "" else index.toString()
                            if (!root.hasAttribute(XMLNS_PREFIX + prefix)) {
                                break
                            }
                            index++
                        }
                        root.setAttribute(XMLNS_PREFIX + prefix, namespace)
                    }
                    element.setAttribute(prefix + ":" + setFix.attribute, value)
                } else {
                    element.setAttribute(setFix.attribute, value)
                }
            }
            XmlPrettyPrinter.prettyPrint(document, true)
        } catch (e: ParserConfigurationException) {
            throw RuntimeException(e)
        } catch (e: SAXException) {
            throw RuntimeException(e)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    private fun appendDiff(
        incident: Incident,
        fixDescription: String?,
        before: String,
        after: String,
        diffs: StringBuilder
    ) {
        val diff = getDiff(before, after, diffWindow)
        if (diff.isNotEmpty()) {
            val targetPath = incident.getDisplayPath().replace(File.separatorChar, '/')
            diffs.append("Fix for ")
                .append(targetPath)
                .append(" line ")
                .append(incident.line + 1)
                .append(": ")
            if (fixDescription != null) {
                diffs.append(fixDescription).append(":\n")
            } else {
                diffs.append("\n")
            }
            diffs.append(diff).append("\n")
        }
    }

    private fun appendShowUrl(
        incident: Incident,
        fix: ShowUrl,
        diffs: StringBuilder
    ) {
        val targetPath = incident.getDisplayPath()
        diffs.append("Show URL for ")
            .append(targetPath.replace(File.separatorChar, '/'))
            .append(" line ")
            .append(incident.line + 1)
            .append(": ")
        val fixDescription = fix.displayName
        if (fixDescription != null) {
            diffs.append(fixDescription).append(":\n")
        }
        diffs.append(fix.url)
        diffs.append("\n")
    }

    private fun appendDataMap(incident: Incident, map: LintFix.DataMap, diffs: StringBuilder) {
        val targetPath = incident.getDisplayPath()
        diffs.append("Data for ")
            .append(targetPath.replace(File.separatorChar, '/'))
            .append(" line ")
            .append(incident.line + 1)
            .append(": ")
        val fixDescription = map.displayName
        if (fixDescription != null) {
            diffs.append(fixDescription).append(":\n")
        }
        val keys: List<String> = Lists.newArrayList(map.keys()).sorted()
        for (key in keys) {
            diffs.append("  ")
            diffs.append(key)
            diffs.append(" : ")
            diffs.append(map[key])
        }
    }

    companion object {
        /**
         * Given fix-delta output, increases the line numbers by one
         * (needed to gracefully handle older fix diffs where the line
         * numbers were 0-based instead of 1-based like the error
         * output.)
         */
        private fun bumpFixLineNumbers(output: String): String {
            val sb = StringBuilder(output.length)
            val pattern = Pattern.compile("((Fix|Data) for .* line )(\\d+)(: .+)")
            for (line in output.split("\n").toTypedArray()) {
                val matcher = pattern.matcher(line)
                if (matcher.matches()) {
                    val prefix = matcher.group(1)
                    val lineNumber = matcher.group(3)
                    val suffix = matcher.group(4)
                    sb.append(prefix)
                    sb.append(lineNumber.toInt() + 1)
                    sb.append(suffix)
                } else {
                    sb.append(line)
                }
                sb.append('\n')
            }
            return sb.toString()
        }

        private fun haveSetAttribute(lintFix: LintFix): Boolean {
            if (lintFix is SetAttribute) {
                return true
            } else if (lintFix is LintFixGroup &&
                lintFix.type == GroupType.COMPOSITE
            ) {
                for (nested in lintFix.fixes) {
                    if (haveSetAttribute(nested)) {
                        return true
                    }
                }
            }
            return false
        }

        private fun checkReplaceString(
            replaceFix: LintFix.ReplaceString,
            incident: Incident,
            contents: String
        ): String? {
            val oldPattern = replaceFix.oldPattern
            val oldString = replaceFix.oldString
            val location = if (replaceFix.range != null) replaceFix.range else incident.location
            location ?: return contents
            val start = location.start ?: return contents
            val end = location.end ?: return contents
            val locationRange = contents.substring(start.offset, end.offset)
            var startOffset: Int
            var endOffset: Int
            var replacement = replaceFix.replacement
            if (oldString == null && oldPattern == null) {
                // Replace the whole range
                startOffset = start.offset
                endOffset = end.offset

                // See if there's nothing left on the line; if so, delete the whole line
                var allSpace = true
                for (offset in replacement.indices) {
                    val c = contents[offset]
                    if (!Character.isWhitespace(c)) {
                        allSpace = false
                        break
                    }
                }
                if (allSpace) {
                    var lineBegin = startOffset
                    while (lineBegin > 0) {
                        val c = contents[lineBegin - 1]
                        if (c == '\n') {
                            break
                        } else if (!Character.isWhitespace(c)) {
                            allSpace = false
                            break
                        }
                        lineBegin--
                    }
                    var lineEnd = endOffset
                    while (lineEnd < contents.length) {
                        val c = contents[lineEnd]
                        lineEnd++
                        if (c == '\n') {
                            break
                        }
                    }
                    if (allSpace) {
                        startOffset = lineBegin
                        endOffset = lineEnd
                    }
                }
            } else if (oldString != null) {
                val index = locationRange.indexOf(oldString)
                when {
                    index != -1 -> {
                        startOffset = start.offset + index
                        endOffset = start.offset + index + oldString.length
                    }
                    oldString == LintFix.ReplaceString.INSERT_BEGINNING -> {
                        startOffset = start.offset
                        endOffset = startOffset
                    }
                    oldString == LintFix.ReplaceString.INSERT_END -> {
                        startOffset = end.offset
                        endOffset = startOffset
                    }
                    else -> {
                        fail(
                            "Did not find \"" +
                                oldString +
                                "\" in \"" +
                                locationRange +
                                "\" as suggested in the quickfix. Consider calling " +
                                "ReplaceStringBuilder#range() to set a larger range to " +
                                "search than the default highlight range."
                        )
                        return null
                    }
                }
            } else {
                oldPattern ?: return null
                val pattern = Pattern.compile(oldPattern)
                val matcher = pattern.matcher(locationRange)
                if (!matcher.find()) {
                    fail(
                        "Did not match pattern \"" +
                            oldPattern +
                            "\" in \"" +
                            locationRange +
                            "\" as suggested in the quickfix"
                    )
                    return null
                } else {
                    startOffset = start.offset
                    endOffset = startOffset
                    if (matcher.groupCount() > 0) {
                        if (oldPattern.contains("target")) {
                            try {
                                startOffset += matcher.start("target")
                                endOffset += matcher.end("target")
                            } catch (ignore: IllegalArgumentException) {
                                // Occurrence of "target" not actually a named group
                                startOffset += matcher.start(1)
                                endOffset += matcher.end(1)
                            }
                        } else {
                            startOffset += matcher.start(1)
                            endOffset += matcher.end(1)
                        }
                    } else {
                        startOffset += matcher.start()
                        endOffset += matcher.end()
                    }
                    replacement = replaceFix.expandBackReferences(matcher)
                }
            }
            if (replaceFix.shortenNames) {
                // This isn't fully shortening names, it's only removing fully qualified names
                // for symbols already imported.
                //
                // Also, this will not correctly handle some conflicts. This is only used for
                // unit testing lint fixes, not for actually operating on code; for that we're using
                // IntelliJ's built in import cleanup when running in the IDE.
                val imported: MutableSet<String> = HashSet()
                for (line in contents.split("\n").toTypedArray()) {
                    if (line.startsWith("package ")) {
                        var to = line.indexOf(';')
                        if (to == -1) {
                            to = line.length
                        }
                        imported.add(line.substring("package ".length, to).trim { it <= ' ' } + ".")
                    } else if (line.startsWith("import ")) {
                        var from = "import ".length
                        if (line.startsWith("static ")) {
                            from += " static ".length
                        }
                        var to = line.indexOf(';')
                        if (to == -1) {
                            to = line.length
                        }
                        if (line[to - 1] == '*') {
                            to--
                        }
                        imported.add(line.substring(from, to).trim { it <= ' ' })
                    }
                }
                for (full in imported) {
                    var clz = full
                    if (replacement.contains(clz)) {
                        if (!clz.endsWith(".")) {
                            val index = clz.lastIndexOf('.')
                            if (index == -1) {
                                continue
                            }
                            clz = clz.substring(0, index + 1)
                        }
                        replacement = replacement.replace(clz, "")
                    }
                }
            }
            var s = contents.substring(0, startOffset) + replacement + contents.substring(endOffset)

            // Insert selection/caret markers if configured for this fix
            val selectPattern = replaceFix.selectPattern
            if (selectPattern != null) {
                val pattern = Pattern.compile(selectPattern)
                val matcher = pattern.matcher(s)
                if (matcher.find(start.offset)) {
                    val selectStart: Int
                    val selectEnd: Int
                    if (matcher.groupCount() > 0) {
                        selectStart = matcher.start(1)
                        selectEnd = matcher.end(1)
                    } else {
                        selectStart = matcher.start()
                        selectEnd = matcher.end()
                    }
                    s = if (selectStart == selectEnd) {
                        s.substring(0, selectStart) + "|" + s.substring(selectEnd)
                    } else {
                        (
                            s.substring(0, selectStart) +
                                "[" +
                                s.substring(selectStart, selectEnd) +
                                "]" +
                                s.substring(selectEnd)
                            )
                    }
                }
            }
            return s
        }
    }
}
