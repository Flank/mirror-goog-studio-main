/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.tools.lint

import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.LintFix.GroupType.ALTERNATIVES
import com.android.tools.lint.detector.api.LintFix.LintFixGroup
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.TextFormat.RAW
import com.android.utils.SdkUtils
import com.android.utils.XmlUtils.toXmlAttributeValue
import com.google.common.annotations.Beta
import com.google.common.base.Joiner
import com.google.common.io.Files
import java.io.BufferedWriter
import java.io.File
import java.io.IOException
import java.io.Writer
import kotlin.math.max
import kotlin.math.min

/**
 * A reporter which emits lint results into an XML report.
 *
 * **NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.**
 */
@Beta
class XmlReporter
/**
 * Constructs a new [XmlReporter]
 *
 * @param client the client
 * @param output the output file
 * @throws IOException if an error occurs
 */
@Throws(IOException::class)
constructor(client: LintCliClient, output: File) : Reporter(client, output) {
    private val writer: Writer = BufferedWriter(Files.newWriter(output, Charsets.UTF_8))
    var isIntendedForBaseline: Boolean = false
    var includeFixes: Boolean = false
    private var attributes: MutableMap<String, String>? = null

    /** Sets a custom attribute to be written out on the root element of the report */
    private fun setAttribute(name: String, value: String) {
        val attributes = attributes ?: run {
            val newMap = mutableMapOf<String, String>()
            attributes = newMap
            newMap
        }
        attributes[name] = value
    }

    fun setBaselineAttributes(client: LintClient, variant: String?) {
        setAttribute("client", LintClient.clientName)
        val revision = client.getClientDisplayRevision()
        if (revision != null) {
            setAttribute("version", revision)
        }
        if (variant != null) {
            setAttribute("variant", variant)
        }
    }

    @Throws(IOException::class)
    override fun write(stats: LintStats, issues: List<Incident>) {
        writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        // Format 4: added urls= attribute with all more info links, comma separated
        writer.write("<issues format=\"5\"")
        val revision = client.getClientDisplayRevision()
        if (revision != null) {
            writer.write(String.format(" by=\"lint %1\$s\"", revision))
        }
        attributes?.let { map ->
            map.asSequence().sortedBy { it.key }.forEach {
                writer.write(" ${it.key}=\"${toXmlAttributeValue(it.value)}\"")
            }
        }
        writer.write(">\n")

        if (issues.isNotEmpty()) {
            writeIncidents(issues)
        }

        writer.write("\n</issues>\n")
        writer.close()

        if (!client.flags.isQuiet && output != null && (stats.errorCount > 0 || stats.warningCount > 0)) {
            val url = SdkUtils.fileToUrlString(output.absoluteFile)
            println(String.format("Wrote XML report to %1\$s", url))
        }
    }

    private fun writeIncidents(issues: List<Incident>) {
        for (incident in issues) {
            writeIncident(incident)
        }
    }

    private fun writeIncident(incident: Incident) {
        writer.write('\n'.toInt())
        indent(1)
        writer.write("<issue")
        val issue = incident.issue
        writeAttribute(writer, 2, "id", issue.id)
        if (!isIntendedForBaseline) {
            writeAttribute(
                writer, 2, "severity",
                incident.severity.description
            )
        }
        writeAttribute(writer, 2, "message", incident.message)

        if (!isIntendedForBaseline) {
            writeAttribute(writer, 2, "category", issue.category.fullName)
            writeAttribute(writer, 2, "priority", issue.priority.toString())
            // We don't need issue metadata in baselines
            writeAttribute(writer, 2, "summary", issue.getBriefDescription(RAW))
            writeAttribute(writer, 2, "explanation", issue.getExplanation(RAW))

            val moreInfo = issue.moreInfo
            if (moreInfo.isNotEmpty()) {
                // Compatibility with old format: list first URL
                writeAttribute(writer, 2, "url", moreInfo[0])
                writeAttribute(writer, 2, "urls", Joiner.on(',').join(issue.moreInfo))
            }
        }
        if (client.flags.isShowSourceLines) {
            val line = incident.getErrorLines(textProvider = { client.getSourceText(it) })
            if (line != null && line.isNotEmpty()) {
                val index1 = line.indexOf('\n')
                if (index1 != -1) {
                    val index2 = line.indexOf('\n', index1 + 1)
                    if (index2 != -1) {
                        val line1 = line.substring(0, index1)
                        val line2 = line.substring(index1 + 1, index2)
                        writeAttribute(writer, 2, "errorLine1", line1)
                        writeAttribute(writer, 2, "errorLine2", line2)
                    }
                }
            }
        }

        val applicableVariants = incident.applicableVariants
        if (applicableVariants != null && applicableVariants.variantSpecific) {
            writeAttribute(
                writer,
                2,
                "includedVariants",
                Joiner.on(',').join(applicableVariants.includedVariantNames)
            )
            writeAttribute(
                writer,
                2,
                "excludedVariants",
                Joiner.on(',').join(applicableVariants.excludedVariantNames)
            )
        }

        if (!isIntendedForBaseline && includeFixes &&
            (incident.fix != null || hasAutoFix(issue))
        ) {
            writeAttribute(writer, 2, "quickfix", "studio")
        }

        var hasChildren = false

        val fixData = incident.fix
        if (includeFixes && fixData != null) {
            writer.write(">\n")
            emitFixes(incident, fixData)
            hasChildren = true
        }

        var location: Location? = incident.location
        if (location != null) {
            if (!hasChildren) {
                writer.write(">\n")
            }
            while (location != null) {
                indent(2)
                writer.write("<location")
                val path = client.getDisplayPath(
                    incident.project,
                    location.file,
                    // Don't use absolute paths in baseline files
                    client.flags.isFullPath && !isIntendedForBaseline
                )
                writeAttribute(writer, 3, "file", path)
                val start = location.start
                if (start != null) {
                    val line = start.line
                    val column = start.column
                    if (line >= 0) {
                        // +1: Line numbers internally are 0-based, report should be
                        // 1-based.
                        writeAttribute(writer, 3, "line", (line + 1).toString())
                        if (column >= 0) {
                            writeAttribute(writer, 3, "column", (column + 1).toString())
                        }
                    }
                }

                writer.write("/>\n")
                location = location.secondary
            }
            hasChildren = true
        }

        if (hasChildren) {
            indent(1)
            writer.write("</issue>\n")
        } else {
            writer.write('\n'.toInt())
            indent(1)
            writer.write("/>\n")
        }
    }

    private fun emitFixes(incident: Incident, lintFix: LintFix) {
        val fixes = if (lintFix is LintFixGroup && lintFix.type == ALTERNATIVES) {
            lintFix.fixes
        } else {
            listOf(lintFix)
        }
        for (fix in fixes) {
            emitFix(incident, fix)
        }
    }

    private fun emitFix(incident: Incident, lintFix: LintFix) {
        indent(2)
        writer.write("<fix")
        lintFix.displayName?.let {
            writeAttribute(writer, 3, "description", it)
        }
        writeAttribute(
            writer, 3, "auto",
            LintFixPerformer.canAutoFix(lintFix).toString()
        )

        var haveChildren = false

        val performer = LintFixPerformer(client, false)
        val files = performer.computeEdits(incident, lintFix)
        if (files != null && files.isNotEmpty()) {
            haveChildren = true
            writer.write(">\n")

            for (file in files) {
                for (edit in file.edits) {
                    indent(3)
                    writer.write("<edit")

                    val path = client.getDisplayPath(
                        incident.project,
                        file.file,
                        // Don't use absolute paths in baseline files
                        client.flags.isFullPath && !isIntendedForBaseline
                    )
                    writeAttribute(writer, 4, "file", path)

                    with(edit) {
                        val after = source.substring(max(startOffset - 12, 0), startOffset)
                        val before = source.substring(
                            startOffset,
                            min(max(startOffset + 12, endOffset), source.length)
                        )
                        writeAttribute(writer, 4, "offset", startOffset.toString())
                        writeAttribute(writer, 4, "after", after)
                        writeAttribute(writer, 4, "before", before)
                        if (endOffset > startOffset) {
                            writeAttribute(
                                writer, 4, "delete",
                                source.substring(startOffset, endOffset)
                            )
                        }
                        if (replacement.isNotEmpty()) {
                            writeAttribute(writer, 4, "insert", replacement)
                        }
                    }

                    writer.write("/>\n")
                }
            }
        }

        if (haveChildren) {
            indent(2)
            writer.write("</fix>\n")
        } else {
            writer.write("/>\n")
        }
    }

    @Throws(IOException::class)
    private fun writeAttribute(writer: Writer, indent: Int, name: String, value: String) {
        writer.write('\n'.toInt())
        indent(indent)
        writer.write(name)
        writer.write('='.toInt())
        writer.write('"'.toInt())
        writer.write(toXmlAttributeValue(value))
        writer.write('"'.toInt())
    }

    @Throws(IOException::class)
    private fun indent(indent: Int) {
        for (level in 0 until indent) {
            writer.write("    ")
        }
    }
}
