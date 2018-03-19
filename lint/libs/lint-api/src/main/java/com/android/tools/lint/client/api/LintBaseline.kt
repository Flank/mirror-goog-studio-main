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

package com.android.tools.lint.client.api

import com.android.SdkConstants
import com.android.SdkConstants.ATTR_FILE
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_LINE
import com.android.SdkConstants.ATTR_MESSAGE
import com.android.SdkConstants.TAG_ISSUE
import com.android.SdkConstants.TAG_ISSUES
import com.android.SdkConstants.TAG_LOCATION
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintUtils
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.TextFormat
import com.android.utils.XmlUtils
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Lists
import com.google.common.collect.Maps
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.Writer
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.Collections

/**
 * A lint baseline is a collection of warnings for a project that have been
 * obtained from a previous run of lint. These warnings are them exempt from
 * reporting. This lets you set a "baseline" with a known set of issues that you
 * haven't attempted to fix yet, but then be alerted whenever new issues crop
 * up.
 */
class LintBaseline(
    /** Client to log to  */
    private val client: LintClient?,

    /**
     * The file to read the baselines from, and if [.writeOnClose] is set, to write
     * to when the baseline is [.close]'ed.
     */
    val file: File
) {

    /** Count of number of errors that were filtered out  */
    /** Returns the number of errors that have been matched from the baseline  */
    var foundErrorCount: Int = 0
        private set

    /** Count of number of warnings that were filtered out  */
    /** Returns the number of warnings that have been matched from the baseline  */
    var foundWarningCount: Int = 0
        private set

    /** Raw number of issues found in the baseline when opened  */
    /** Returns the total number of issues contained in this baseline  */
    var totalCount: Int = 0
        private set

    /** Map from message to [Entry]  */
    private val messageToEntry = ArrayListMultimap.create<String, Entry>(100, 20)

    /**
     * Whether we should write the baseline file when the baseline is closed, if the
     * baseline file doesn't already exist. We don't always do this because for example
     * when lint is run from Gradle, and it's analyzing multiple variants, it does its own
     * merging (across variants) of the results first and then writes that, via the
     * XML reporter.
     */
    /** Returns whether this baseline is writing its result upon close  */
    /** Sets whether the baseline should write its matched entries on [.close]  */
    var isWriteOnClose: Boolean = false
        set(writeOnClose) {
            if (writeOnClose) {
                val count = if (totalCount > 0) totalCount + 10 else 30
                entriesToWrite = ArrayList(count)
            }
            field = writeOnClose
        }

    /**
     * Whether the baseline, when configured to write results into the file, will
     * include all found issues, or only issues that are already known. The difference
     * here is whether we're initially creating the baseline (or resetting it), or
     * whether we're trying to only remove fixed issues.
     */
    var isRemoveFixed: Boolean = false

    /**
     * If non-null, a list of issues to write back out to the baseline file when the
     * baseline is closed.
     */
    private var entriesToWrite: MutableList<ReportedEntry>? = null

    /**
     * Returns the number of issues that appear to have been fixed (e.g. are present
     * in the baseline but have not been matched
     */
    val fixedCount: Int
        get() = totalCount - foundErrorCount - foundWarningCount

    init {
        readBaselineFile()
    }

    /**
     * Checks if we should report baseline activity (filtered out issues, found fixed issues etc
     * and if so reports them
     */
    internal fun reportBaselineIssues(driver: LintDriver, project: Project) {
        if (foundErrorCount > 0 || foundWarningCount > 0) {
            val client = driver.client
            val baselineFile = file
            val message = describeBaselineFilter(
                foundErrorCount,
                foundWarningCount, getDisplayPath(project, baselineFile)
            )
            LintClient.report(
                client, IssueRegistry.BASELINE, message,
                file = baselineFile, project = project, driver = driver
            )
        }

        val fixedCount = fixedCount
        if (fixedCount > 0 && !(isWriteOnClose && isRemoveFixed)) {
            val client = driver.client
            val baselineFile = file
            val ids = Maps.newHashMap<String, Int>()
            for (entry in messageToEntry.values()) {
                var count: Int? = ids[entry.issueId]
                if (count == null) {
                    count = 1
                } else {
                    count += 1
                }
                ids[entry.issueId] = count
            }
            val sorted = Lists.newArrayList(ids.keys)
            Collections.sort(sorted)
            val issueTypes = StringBuilder()
            for (id in sorted) {
                if (issueTypes.isNotEmpty()) {
                    issueTypes.append(", ")
                }
                issueTypes.append(id)
                val count = ids[id]
                if (count != null && count > 1) {
                    issueTypes.append(" (").append(Integer.toString(count)).append(")")
                }
            }

            // Keep in sync with isFixedMessage() below
            var message = String.format(
                "%1\$d errors/warnings were listed in the " +
                        "baseline file (%2\$s) but not found in the project; perhaps they have " +
                        "been fixed?", fixedCount, getDisplayPath(project, baselineFile)
            )
            if (LintClient.isGradle && project.gradleProjectModel != null &&
                !project.gradleProjectModel!!.lintOptions.isCheckDependencies
            ) {
                message += " Another possible explanation is that lint recently stopped " +
                        "analyzing (and including results from) dependent projects by default. " +
                        "You can turn this back on with " +
                        "`android.lintOptions.checkDependencies=true`."
            }
            message += " Unmatched issue types: " + issueTypes

            LintClient.report(
                client, IssueRegistry.BASELINE, message,
                file = baselineFile, project = project, driver = driver
            )
        }
    }

    /**
     * Checks whether the given warning (of the given issue type, message and location)
     * is present in this baseline, and if so marks it as used such that a second call will
     * not find it.
     *
     *
     * When issue analysis is done you can call [.getFoundErrorCount] and
     * [.getFoundWarningCount] to get a count of the warnings or errors that were
     * matched during the run, and [.getFixedCount] to get a count of the issues
     * that were present in the baseline that were not matched (e.g. have been fixed.)
     *
     * @param issue the issue type
     * @param location the location of the error
     * @param message the exact error message (in [TextFormat.RAW] format)
     * @param severity the severity of the issue, used to count baseline match as error or warning
     * @param project the relevant project, if any
     * @return true if this error was found in the baseline and marked as used, and false if this
     * issue is not part of the baseline
     */
    fun findAndMark(
        issue: Issue,
        location: Location,
        message: String,
        severity: Severity?,
        project: Project?
    ): Boolean {
        val found = findAndMark(issue, location, message, severity)
        if (isWriteOnClose && (!isRemoveFixed || found)) {

            if (entriesToWrite != null) {
                entriesToWrite!!.add(ReportedEntry(issue, project, location, message))
            }
        }

        return found
    }

    private fun findAndMark(
        issue: Issue,
        location: Location,
        message: String,
        severity: Severity?
    ): Boolean {
        val entries = messageToEntry.get(message)
        if (entries == null || entries.isEmpty()) {
            return false
        }

        val file = location.file
        val path = file.path
        val issueId = issue.id
        for (entry in entries) {
            if (entry!!.issueId == issueId) {
                if (isSamePathSuffix(path, entry.path)) {
                    // Remove all linked entries. We don't loop through all the locations;
                    // they're allowed to vary over time, we just assume that all entries
                    // for the same warning should be cleared.
                    var curr = entry
                    while (curr.previous != null) {
                        curr = curr.previous
                    }
                    while (curr != null) {
                        messageToEntry.remove(curr.message, curr)
                        curr = curr.next
                    }

                    if ((severity ?: issue.defaultSeverity).isError) {
                        foundErrorCount++
                    } else {
                        foundWarningCount++
                    }

                    return true
                }
            }
        }

        return false
    }

    /** Read in the XML report  */
    private fun readBaselineFile() {
        if (!file.exists()) {
            return
        }

        try {
            BufferedReader(
                InputStreamReader(
                    FileInputStream(file), StandardCharsets.UTF_8
                )
            ).use { reader ->
                val parser = KXmlParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                parser.setInput(reader)

                var issue: String? = null
                var message: String? = null
                var path: String? = null
                var currentEntry: Entry? = null

                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    val eventType = parser.eventType
                    if (eventType == XmlPullParser.END_TAG) {
                        val tag = parser.name
                        if (tag == SdkConstants.TAG_LOCATION) {
                            if (issue != null && message != null && path != null) {
                                val entry = Entry(issue, message, path)
                                if (currentEntry != null) {
                                    currentEntry.next = entry
                                }
                                entry.previous = currentEntry
                                currentEntry = entry
                                messageToEntry.put(entry.message, entry)
                            }
                        } else if (tag == TAG_ISSUE) {
                            totalCount++
                            issue = null
                            message = null
                            path = null
                            currentEntry = null
                        }
                    } else if (eventType != XmlPullParser.START_TAG) {
                        continue
                    }

                    var i = 0
                    val n = parser.attributeCount
                    while (i < n) {
                        val name = parser.getAttributeName(i)
                        val value = parser.getAttributeValue(i)
                        when (name) {
                            ATTR_ID -> issue = value
                            ATTR_MESSAGE -> {
                                message = value
                                // Error message changed recently; let's stay compatible
                                if (message!!.startsWith("[")) {
                                    if (message.startsWith("[I18N] ")) {
                                        message = message.substring("[I18N] ".length)
                                    } else if (message.startsWith("[Accessibility] ")) {
                                        message = message.substring("[Accessibility] ".length)
                                    }
                                }
                            }
                            ATTR_FILE -> path = value
                        // For now not reading ATTR_LINE; not used for baseline entry matching
                        //ATTR_LINE -> line = value
                        }
                        i++
                    }
                }
            }
        } catch (e: IOException) {
            if (client != null) {
                client.log(e, null)
            } else {
                e.printStackTrace()
            }
        } catch (e: XmlPullParserException) {
            if (client != null) {
                client.log(e, null)
            } else {
                e.printStackTrace()
            }
        }
    }

    /** Finishes writing the baseline  */
    fun close() {
        if (isWriteOnClose) {
            val parentFile = file.parentFile
            if (parentFile != null && !parentFile.exists()) {
                val mkdirs = parentFile.mkdirs()
                if (!mkdirs) {
                    client!!.log(null, "Couldn't create %1\$s", parentFile)
                    return
                }
            }

            try {
                BufferedWriter(FileWriter(file)).use { writer ->
                    writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                    // Format 4: added urls= attribute with all more info links, comma separated
                    writer.write("<")
                    writer.write(TAG_ISSUES)
                    writer.write(" format=\"4\"")
                    val revision = client!!.getClientRevision()
                    if (revision != null) {
                        writer.write(String.format(" by=\"lint %1\$s\"", revision))
                    }
                    writer.write(">\n")

                    totalCount = 0
                    if (entriesToWrite != null) {
                        Collections.sort(entriesToWrite!!)
                        for (entry in entriesToWrite!!) {
                            entry.write(writer, client)
                            totalCount++
                        }
                    }

                    writer.write("\n</")
                    writer.write(TAG_ISSUES)
                    writer.write(">\n")
                    writer.close()
                }
            } catch (ioe: IOException) {
                client!!.log(ioe, null)
            }
        }
    }

    /**
     * Entries that have been reported during this lint run. We only create these
     * when we need to write a baseline file (since we need to sort them before
     * writing out the result file, to ensure stable files.
     */
    private class ReportedEntry(
        val issue: Issue,
        val project: Project?,
        val location: Location,
        val message: String
    ) : Comparable<ReportedEntry> {

        override fun compareTo(other: ReportedEntry): Int {
            // Sort by category, then by priority, then by id,
            // then by file, then by line
            val categoryDelta = issue.category.compareTo(other.issue.category)
            if (categoryDelta != 0) {
                return categoryDelta
            }
            // DECREASING priority order
            val priorityDelta = other.issue.priority - issue.priority
            if (priorityDelta != 0) {
                return priorityDelta
            }
            val id1 = issue.id
            val id2 = other.issue.id
            val idDelta = id1.compareTo(id2)
            if (idDelta != 0) {
                return idDelta
            }
            val file = location.file
            val otherFile = other.location.file
            val fileDelta = file.name.compareTo(
                otherFile.name
            )
            if (fileDelta != 0) {
                return fileDelta
            }

            val start = location.start
            val otherStart = other.location.start
            val line = start?.line ?: -1
            val otherLine = otherStart?.line ?: -1

            if (line != otherLine) {
                return line - otherLine
            }

            var delta = message.compareTo(other.message)
            if (delta != 0) {
                return delta
            }

            delta = file.compareTo(otherFile)
            if (delta != 0) {
                return delta
            }

            val secondary1 = location.secondary
            val secondaryFile1 = secondary1?.file
            val secondary2 = other.location.secondary
            val secondaryFile2 = secondary2?.file
            if (secondaryFile1 != null) {
                return if (secondaryFile2 != null) {
                    secondaryFile1.compareTo(secondaryFile2)
                } else {
                    -1
                }
            } else if (secondaryFile2 != null) {
                return 1
            }

            // This handles the case where you have a huge XML document without hewlines,
            // such that all the errors end up on the same line.
            if (start != null && otherStart != null) {
                delta = start.column - otherStart.column
                if (delta != 0) {
                    return delta
                }
            }

            return 0
        }

        /**
         * Given the report of an issue, add it to the baseline being built in the XML writer
         */
        internal fun write(
            writer: Writer,
            client: LintClient
        ) {
            try {
                writer.write("\n")
                indent(writer, 1)
                writer.write("<")
                writer.write(TAG_ISSUE)
                writeAttribute(writer, 2, ATTR_ID, issue.id)

                writeAttribute(writer, 2, ATTR_MESSAGE, message)

                writer.write(">\n")
                var currentLocation: Location? = location
                while (currentLocation != null) {
                    //
                    //
                    //
                    // IMPORTANT: Keep this format compatible with the XML report format
                    //            encoded by the XmlReporter! That way XML reports and baseline
                    //            files can be mix & matched. (Compatible=subset.)
                    //
                    //
                    indent(writer, 2)
                    writer.write("<")
                    writer.write(TAG_LOCATION)
                    val path = getDisplayPath(project, currentLocation.file)
                    writeAttribute(writer, 3, ATTR_FILE, path)
                    val start = currentLocation.start
                    if (start != null) {
                        val line = start.line
                        if (line >= 0) {
                            // +1: Line numbers internally are 0-based, report should be
                            // 1-based.
                            writeAttribute(writer, 3, ATTR_LINE, Integer.toString(line + 1))
                        }
                    }

                    writer.write("/>\n")
                    currentLocation = currentLocation.secondary
                }
                indent(writer, 1)
                writer.write("</")
                writer.write(TAG_ISSUE)
                writer.write(">\n")
            } catch (ioe: IOException) {
                client.log(ioe, null)
            }
        }
    }

    /**
     * Entry loaded from the baseline file. Note that for an error with multiple locations,
     * there may be multiple entries; these are linked by next/previous fields.
     */
    private class Entry(
        val issueId: String,
        val message: String,
        val path: String
    ) {
        /**
         * An issue can have multiple locations; we create a separate entry for each
         * but we link them together such that we can mark them all fixed
         */
        var next: Entry? = null
        var previous: Entry? = null
    }

    companion object {
        /**
         * Given an error message produced by this lint detector for the given issue type,
         * determines whether this corresponds to the warning (produced by
         * {link {@link #reportBaselineIssues(LintDriver, Project)} above) that one or
         * more issues have been filtered out.
         * <p>
         * Intended for IDE quickfix implementations.
         */
        @SuppressWarnings("unused") // Used from the IDE
        fun isFilteredMessage(errorMessage: String, format: TextFormat): Boolean {
            return format.toText(errorMessage).contains("filtered out because")
        }

        /**
         * Given an error message produced by this lint detector for the given issue type,
         * determines whether this corresponds to the warning (produced by
         * {link {@link #reportBaselineIssues(LintDriver, Project)} above) that one or
         * more issues have been fixed (present in baseline but not in project.)
         * <p>
         * Intended for IDE quickfix implementations.
         */
        @SuppressWarnings("unused") // Used from the IDE
        fun isFixedMessage(errorMessage: String, format: TextFormat): Boolean {
            return format.toText(errorMessage).contains("perhaps they have been fixed")
        }

        fun describeBaselineFilter(
            errors: Int,
            warnings: Int,
            baselineDisplayPath: String
        ): String {
            val counts = LintUtils.describeCounts(errors, warnings, false, true)
            // Keep in sync with isFilteredMessage() below
            return if (errors + warnings == 1) {
                "$counts was filtered out because it is listed in the baseline file, $baselineDisplayPath\n"
            } else {
                "$counts were filtered out because they are listed in the baseline file, $baselineDisplayPath\n"
            }
        }

        /** Like path.endsWith(suffix), but considers \\ and / identical  */
        fun isSamePathSuffix(path: String, suffix: String): Boolean {
            var i = path.length - 1
            var j = suffix.length - 1

            var begin = 0
            while (begin < j) {
                val c = suffix[begin]
                if (c != '.' && c != '/' && c != '\\') {
                    break
                }
                begin++
            }

            if (j - begin > i) {
                return false
            }

            while (j > begin) {
                var c1 = path[i]
                var c2 = suffix[j]
                if (c1 != c2) {
                    if (c1 == '\\') {
                        c1 = '/'
                    }
                    if (c2 == '\\') {
                        c2 = '/'
                    }
                    if (c1 != c2) {
                        return false
                    }
                }
                i--
                j--
            }

            return true
        }

        private fun getDisplayPath(project: Project?, file: File): String {
            var path = file.path
            if (project != null && path.startsWith(project.referenceDir.path)) {
                var chop = project.referenceDir.path.length
                if (path.length > chop && path[chop] == File.separatorChar) {
                    chop++
                }
                path = path.substring(chop)
                if (path.isEmpty()) {
                    path = file.name
                }
            }

            return path
        }

        @Throws(IOException::class)
        private fun writeAttribute(writer: Writer, indent: Int, name: String, value: String) {
            writer.write("\n")
            indent(writer, indent)
            writer.write(name)
            writer.write("=\"")
            writer.write(XmlUtils.toXmlAttributeValue(value))
            writer.write("\"")
        }

        @Throws(IOException::class)
        private fun indent(writer: Writer, indent: Int) {
            for (level in 0 until indent) {
                writer.write("    ")
            }
        }
    }
}
