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

import com.android.SdkConstants.DOT_XML
import com.android.SdkConstants.RES_FOLDER
import com.android.tools.lint.checks.infrastructure.Edit.Companion.performEdits
import com.android.utils.XmlUtils.CDATA_PREFIX
import com.android.utils.XmlUtils.CDATA_SUFFIX
import java.io.File

/**
 * Test mode which inserts unnecessary whitespace characters into the
 * source code
 */
class CharacterDataTestMode : SourceTransformationTestMode(
    description = "Converting text nodes to CDATA XML sections",
    "TestMode.CDATA",
    "cdata"
) {
    override val diffExplanation: String =
        // first line shorter: expecting to prefix that line with
        // "org.junit.ComparisonFailure: "
        """
        In XML, text content can be wrapped
        in special CDATA sections, like <!CDATA[this]]>. Code processing the
        XML documents need to handle this.

        In the unlikely event that your lint check is actually doing something
        CDATA specific, you can turn off this test mode using
        `.skipTestModes($fieldName)`.
        """.trimIndent()

    override fun applies(context: TestModeContext): Boolean {
        return context.projects.any {
            it.files.any { file -> file is TestFile.XmlTestFile && file.contents.contains("<string") }
        }
    }

    override fun before(context: TestModeContext): Any? {
        val projectFolders = context.projectFolders

        // Replace string content
        for (project in projectFolders) {
            val res = findRes(project) ?: continue
            res.listFiles()?.forEach { resourceFolder ->
                resourceFolder.listFiles()?.forEach { resourceFile ->
                    if (resourceFile.isFile && resourceFile.path.endsWith(DOT_XML)) {
                        val source = resourceFile.readText()
                        val updated = transform(source)
                        if (updated != source) {
                            resourceFile.writeText(updated)
                        }
                    }
                }
            }
        }

        return null // success/continue
    }

    fun transform(source: String): String {
        val edits = computeEdits(source)
        return performEdits(source, edits)
    }

    private fun computeEdits(contents: String): List<Edit> {
        val edits = mutableListOf<Edit>()
        var offset = 0

        while (true) {
            offset = contents.indexOf("<string ", offset)
            if (offset == -1) {
                break
            }
            val next = contents.indexOf('>', offset) + 1
            if (next == 0) {
                break
            }
            val end = contents.indexOf("</string>", next)
            if (end == -1) {
                break
            }
            val span = contents.substring(next, end)

            // Skip strings that already use character data, or are resource URLs, or contain nested markup
            if (!span.contains(CDATA_PREFIX) && !span.trim().startsWith("@") && !span.contains("<")) {
                edits.add(Edit(next, next, CDATA_PREFIX, false))
                edits.add(Edit(end, end, CDATA_SUFFIX, true))
            }

            offset = end
        }

        return edits
    }

    override fun transformMessage(message: String): String {
        return message.replace(CDATA_PREFIX, "").replace(CDATA_SUFFIX, "")
    }

    private fun findRes(projectDir: File): File? {
        var res = File(projectDir, RES_FOLDER)
        if (!res.exists()) {
            // Gradle test project? If so the test sources are moved over to src/main/
            res = File(projectDir, "src/main/$RES_FOLDER")
        }
        if (!res.exists()) {
            return null
        }
        return res
    }
}
