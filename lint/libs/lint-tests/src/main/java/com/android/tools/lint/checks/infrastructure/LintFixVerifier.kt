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

import com.android.SdkConstants.DOT_XML
import com.android.ide.common.xml.XmlPrettyPrinter
import com.android.tools.lint.LintFixPerformer
import com.android.tools.lint.LintFixPerformer.Companion.getLocation
import com.android.tools.lint.checks.infrastructure.TestLintResult.Companion.getDiff
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.LintFix.GroupType
import com.android.tools.lint.detector.api.LintFix.LintFixGroup
import com.android.tools.lint.detector.api.LintFix.SetAttribute
import com.android.tools.lint.detector.api.LintFix.ShowUrl
import com.android.utils.XmlUtils
import com.google.common.base.Splitter
import com.google.common.collect.Lists
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.xml.sax.SAXException
import java.io.File
import java.io.IOException
import java.util.Base64
import java.util.regex.Pattern

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
                val location = getLocation(incident, lintFix)
                val project = incident.project
                val targetPath = project?.getDisplayPath(location.file) ?: location.file.path
                if (lintFix is LintFix.DataMap && diffs != null) {
                    // Doesn't edit file, but include in diffs so fixes can verify the
                    // correct data is passed
                    appendDataMap(incident, lintFix, diffs)
                } else if (lintFix is ShowUrl && diffs != null) {
                    appendShowUrl(incident, lintFix, diffs)
                }
                var reformat = reformat

                val initial: MutableMap<String, String> = HashMap()
                val edited: MutableMap<String, String> = HashMap()

                if (!applyFix(incident, lintFix, initial, edited)) {
                    continue
                }

                if (reformat == null && haveSetAttribute(lintFix)) {
                    reformat = true
                }
                if (expectedFile != null) {
                    val after = edited[targetPath]!!
                    assertEquals(
                        expectedFile.getContents()!!.trimIndent(),
                        after.trimIndent()
                    )
                }
                if (diffs != null) {
                    if (reformat != null && reformat) {
                        for ((f, contents) in edited) {
                            if (!f.endsWith(DOT_XML)) {
                                continue
                            }
                            try {
                                initial[f] = XmlPrettyPrinter.prettyPrint(
                                    XmlUtils.parseDocument(initial[f]!!, true), true
                                )
                                edited[f] = XmlPrettyPrinter.prettyPrint(
                                    XmlUtils.parseDocument(contents, true), true
                                )
                            } catch (e: SAXException) {
                                throw RuntimeException(e)
                            } catch (e: IOException) {
                                throw RuntimeException(e)
                            }
                        }
                    }
                    appendDiff(incident, lintFix.getDisplayName(), initial, edited, diffs)
                }
                val name = lintFix.getDisplayName()
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
        before: MutableMap<String, String>,
        after: MutableMap<String, String>
    ): Boolean {
        if (LintFixPerformer.isEditingFix(lintFix) || lintFix is LintFixGroup) {
            val edits = getLeafFixes(lintFix)
            val performer = object : LintFixPerformer(
                client,
                printStatistics = false,
                requireAutoFixable = false,
                includeMarkers = task.includeSelectionMarkers
            ) {
                override fun writeFile(pendingFile: PendingEditFile, contents: ByteArray?) {
                    val project = incident.project
                    val targetPath = project?.getDisplayPath(pendingFile.file) ?: pendingFile.file.path
                    val initial = findTestFile(targetPath)?.getContents() ?: "" // creating new file
                    before[targetPath] = initial
                    if (contents == null) {
                        after[targetPath] = ""
                    } else {
                        val base64 = "base64: " + Base64.getEncoder().encodeToString(contents)
                        after[targetPath] = Splitter.fixedLength(60).split(base64).joinToString("\n") { "  $it" }
                    }
                }

                override fun writeFile(pendingFile: PendingEditFile, contents: String) {
                    val project = incident.project
                    val targetPath = project?.getDisplayPath(pendingFile.file) ?: pendingFile.file.path
                    val initial = findTestFile(targetPath)?.getContents() ?: "" // creating new file
                    before[targetPath] = initial
                    after[targetPath] = contents
                }
            }
            performer.fix(incident, edits)
            return true
        }
        return false
    }

    private fun appendDiff(
        incident: Incident,
        fixDescription: String?,
        initial: MutableMap<String, String>,
        edited: MutableMap<String, String>,
        diffs: StringBuilder
    ) {
        var first = true

        // List quickfixes in alphabetical order, except the file that the
        // incident location is associated with goes first
        val incidentPath = incident.getDisplayPath()
        val comparator = object : Comparator<String> {
            override fun compare(o1: String, o2: String): Int {
                val v1 = if (o1 == incidentPath) 0 else 1
                val v2 = if (o2 == incidentPath) 0 else 1
                val delta = v1 - v2
                if (delta != 0) {
                    return delta
                }
                return v1.compareTo(v2)
            }
        }
        val sortedFiles = edited.keys.sortedWith(comparator)

        for (file in sortedFiles) {
            val after = edited[file]!!
            val before = initial[file]!!
            val diff = getDiff(before, after, diffWindow)
            if (diff.isNotEmpty()) {
                val targetPath = file.replace(File.separatorChar, '/')
                if (first) {
                    diffs.append("Fix for ")
                        .append(incidentPath)
                        .append(" line ")
                        .append(incident.line + 1)
                        .append(":")
                    if (fixDescription != null) {
                        first = false
                        diffs.append(" ").append(fixDescription).append(":\n")
                    } else {
                        diffs.append("\n")
                    }
                }
                if (targetPath != incidentPath) {
                    diffs.append(targetPath).append(":\n")
                }
                diffs.append(diff).append("\n")
            }
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
        val fixDescription = fix.getDisplayName()
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
        val fixDescription = map.getDisplayName()
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

        private fun getLeafFixes(fix: LintFix): List<LintFix> {
            val flattened: MutableList<LintFix> = mutableListOf()
            fun flatten(fix: LintFix) {
                if (LintFixPerformer.isEditingFix(fix)) {
                    flattened.add(fix)
                } else if (fix is LintFixGroup && fix.type == GroupType.COMPOSITE) {
                    for (nested in fix.fixes) {
                        flatten(nested)
                    }
                }
            }
            flatten(fix)
            return flattened
        }
    }
}
