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

package com.android.tools.lint.client.api

import com.android.tools.lint.detector.api.TextFormat
import com.android.tools.lint.detector.api.TextFormat.Companion.HTTPS_PREFIX
import com.android.tools.lint.detector.api.TextFormat.Companion.HTTP_PREFIX
import java.lang.StringBuilder

/** Information about the provenance of a lint check. */
data class Vendor
@JvmOverloads
constructor(
    /**
     * The author or vendor for issues returned by this lint check.
     * These names are currently shown after "Vendor:" in output
     * reports, in case that helps frame how you phrase the vendor name.
     */
    val vendorName: String? = null,

    /**
     * An identifier to display next to error messages; this typically
     * is more specific than the vendor name; for example, for
     * Maven libraries this would be the maven artifact, or if lint
     * checks from another module in the project, the subproject.
     *
     * This helps users pinpoint where the check is coming from.
     *
     * If no vendor is supplied, lint will try to compute a reasonable
     * default: for Gradle artifacts, the artifact name and version
     * (e.g. recyclerview-1.0), and failing that, the issue registry
     * fully qualified name (minus common suffixes like IssueRegistry
     * and Registry).
     */
    val identifier: String? = null,

    /**
     * A URL pointing to the place to report bugs. This lets users
     * report false positives or false negatives as well as suggest
     * improvements. This doesn't have to point directly to your issue
     * tracker (which might lead to a lot of low quality bug reports);
     * it can point to a web page which explains.
     */
    val feedbackUrl: String? = null,

    /**
     * A contact address (such as an e-mail address or a general contact
     * web page, or perhaps a GitHub page).
     *
     * Note that this is optional, especially if your [feedbackUrl]
     * provides general information.
     */
    val contact: String? = null
) {

    /** Returns a description of the vendor with the given [format] */
    fun describe(format: TextFormat): String {
        val sb = StringBuilder()
        describeInto(sb, format)
        return sb.toString()
    }

    /**
     * Appends a description of this vendor into the given
     * [stringBuilder] using the given text [format].
     */
    fun describeInto(stringBuilder: StringBuilder, format: TextFormat, indent: String = "") {
        if (format == TextFormat.HTML || format == TextFormat.HTML_WITH_UNICODE) {
            with(stringBuilder) {
                vendorName?.let {
                    if (it.isNotBlank()) {
                        append(indent).append("Vendor: ")
                        append(TextFormat.RAW.convertTo(it, format))
                        append("<br/>\n")
                    }
                }
                identifier?.let {
                    if (it.isNotBlank()) {
                        append(indent).append("Identifier: ")
                        append(TextFormat.RAW.convertTo(it, format))
                        append("<br/>\n")
                    }
                }
                contact?.let {
                    if (it.isNotBlank()) {
                        append(indent).append("Contact: ")
                        if (contact.startsWith(HTTP_PREFIX) || contact.startsWith(HTTPS_PREFIX)) {
                            append("<a href=\"")
                            append(it)
                            append("\">")
                            append(it)
                            append("</a>")
                        } else {
                            append(TextFormat.RAW.convertTo(it, format))
                        }
                        append("<br/>\n")
                    }
                }
                feedbackUrl?.let {
                    if (it.isNotBlank()) {
                        append(indent).append("Feedback: <a href=\"")
                        append(it)
                        append("\">")
                        append(it)
                        append("</a><br/>\n")
                    }
                }
            }
        } else {
            with(stringBuilder) {
                vendorName?.let {
                    if (it.isNotBlank()) {
                        append(indent).append("Vendor: ")
                        append(TextFormat.RAW.convertTo(it, format))
                        append('\n')
                    }
                }
                identifier?.let {
                    if (it.isNotBlank()) {
                        append(indent).append("Identifier: ")
                        append(TextFormat.RAW.convertTo(it, format))
                        append('\n')
                    }
                }
                contact?.let {
                    if (it.isNotBlank()) {
                        append(indent).append("Contact: ")
                        append(TextFormat.RAW.convertTo(it, format))
                        append('\n')
                    }
                }
                feedbackUrl?.let {
                    if (it.isNotBlank()) {
                        append(indent).append("Feedback: ")
                        append(TextFormat.RAW.convertTo(it, format))
                        append('\n')
                    }
                }
            }
        }
    }
}
