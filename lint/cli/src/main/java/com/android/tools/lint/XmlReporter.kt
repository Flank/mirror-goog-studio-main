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
import com.android.tools.lint.detector.api.Incident
import com.google.common.annotations.Beta
import com.google.common.io.Files
import java.io.BufferedWriter
import java.io.File
import java.io.IOException

/**
 * A reporter which emits lint results into an XML report.
 *
 * **NOTE: This is not a public or final API; if you rely on this be
 * prepared to adjust your code for the next tools release.**
 */
@Beta
class XmlReporter constructor(
    /** Client handling IO, path normalization and error reporting. */
    client: LintCliClient,
    /** File to write report to. */
    output: File,
    /**
     * The type of XML file to create; this is used to control details
     * like whether locations are annotated with the surrounding source
     * contents.
     */
    var type: XmlFileType
) : Reporter(client, output) {
    private var attributes: MutableMap<String, String>? = null

    fun setBaselineAttributes(client: LintClient, variant: String?) {
        setAttribute(ATTR_CLIENT, LintClient.clientName)
        setAttribute(ATTR_CLIENT_NAME, client.getClientDisplayName())
        val revision = client.getClientDisplayRevision()
        if (revision != null) {
            setAttribute(ATTR_VERSION, revision)
        }
        if (variant != null) {
            setAttribute(ATTR_VARIANT, variant)
        }
    }

    /**
     * Sets a custom attribute to be written out on the root element of
     * the report.
     */
    fun setAttribute(name: String, value: String) {
        val attributes = attributes ?: run {
            val newMap = mutableMapOf<String, String>()
            attributes = newMap
            newMap
        }
        attributes[name] = value
    }

    @Throws(IOException::class)
    override fun write(stats: LintStats, incidents: List<Incident>) {
        val writer = BufferedWriter(Files.newWriter(output!!, Charsets.UTF_8))
        val xmlWriter = XmlWriter(client, type, writer)

        val clientAttributes: List<Pair<String, String?>> =
            attributes?.asSequence()?.sortedBy { it.key }?.map { Pair(it.key, it.value) }?.toList()
                ?: emptyList()
        xmlWriter.writeIncidents(incidents, clientAttributes)
    }
}
