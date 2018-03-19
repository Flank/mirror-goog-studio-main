/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.tools.lint.checks

import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.utils.CharSequences
import com.android.utils.SdkUtils.escapePropertyValue
import com.google.common.base.Splitter
import java.io.File

/**
 * Check for errors in .property files
 *
 *
 * TODO: Warn about bad paths like sdk properties with ' in the path, or suffix of " " etc
 */
/** Constructs a new [PropertyFileDetector]  */
class PropertyFileDetector : Detector() {
    override fun run(context: Context) {
        val contents = context.getContents() ?: return
        var offset = 0
        val iterator = Splitter.on('\n').split(contents).iterator()
        var line: String
        while (iterator.hasNext()) {
            line = iterator.next()
            if (line.startsWith("#") || line.startsWith(" ")) {
                offset += line.length + 1
                continue
            }

            val valueStart = line.indexOf('=') + 1
            if (valueStart == 0) {
                offset += line.length + 1
                continue
            }

            checkLine(context, contents, offset, line, valueStart)
            offset += line.length + 1
        }
    }

    private fun checkLine(
        context: Context,
        contents: CharSequence,
        offset: Int,
        line: String,
        valueStart: Int
    ) {
        val distributionPrefix = "distributionUrl=http\\"
        if (line.startsWith(distributionPrefix)) {
            val https = "https" + line.substring(distributionPrefix.length - 1)
            val message = String.format(
                "Replace HTTP with HTTPS for better security; use %1\$s",
                https.replace("\\", "\\\\")
            )
            val startOffset = offset + valueStart
            val endOffset = startOffset + 4 // 4: "http".length()
            val fix = LintFix.create().replace().text("http").with("https").build()
            val location = Location.create(context.file, contents, startOffset, endOffset)
            context.report(HTTP, location, message, fix)
        } else if (line.startsWith("systemProp.http.proxyPassword=") ||
            line.startsWith("systemProp.https.proxyPassword=")
        ) {
            if (isGitIgnored(context.client, context.file)) {
                return
            }

            val startOffset = offset + valueStart
            val endOffset = line.length
            val location = Location.create(context.file, contents, startOffset, endOffset)
            context.report(
                PROXY_PASSWORD,
                location,
                "Storing passwords in clear text is risky; " +
                        "make sure this file is not shared or checked in via version control"
            )
        } else if (line.indexOf('\\') != -1 || line.indexOf(':') != -1) {
            checkEscapes(context, contents, line, offset, valueStart)
        }
    }

    private fun isGitIgnored(client: LintClient, file: File): Boolean {
        var curr: File? = file.parentFile
        while (curr != null) {
            val ignoreFile = File(curr, ".gitignore")
            if (ignoreFile.exists()) {
                val ignored = client.readFile(ignoreFile)
                if (CharSequences.indexOf(ignored, file.name) != -1) {
                    return true
                }
            }
            curr = curr.parentFile
        }

        return false
    }

    private fun checkEscapes(
        context: Context,
        contents: CharSequence,
        line: String,
        offset: Int,
        valueStart: Int
    ) {
        var escaped = false
        var hadNonPathEscape = false
        var errorStart = -1
        var errorEnd = -1
        val path = StringBuilder()
        for (i in valueStart until line.length) {
            val c = line[i]
            if (c == '\\') {
                escaped = !escaped
                if (escaped) {
                    path.append(c)
                }
            } else if (c == ':') {
                if (!escaped) {
                    hadNonPathEscape = true
                    if (errorStart < 0) {
                        errorStart = i
                    }
                    errorEnd = i
                } else {
                    escaped = false
                }
                path.append(c)
            } else {
                if (escaped) {
                    hadNonPathEscape = true
                    if (errorStart < 0) {
                        errorStart = i
                    }
                    errorEnd = i
                }
                escaped = false
                path.append(c)
            }
        }
        val pathString = path.toString()
        val key = line.substring(0, valueStart)
        if (hadNonPathEscape && key.endsWith(".dir=") || File(pathString).exists()) {
            val escapedPath = suggestEscapes(line.substring(valueStart, line.length))

            val message = ("Windows file separators (`\\`) and drive letter " +
                    "separators (':') must be escaped (`\\\\`) in property files; use " +
                    escapedPath
                // String is already escaped for Java; must double escape for the raw text
                // format
                .replace("\\", "\\\\"))
            val startOffset = offset + errorStart
            val endOffset = offset + errorEnd + 1

            val locationRange = contents.subSequence(startOffset, endOffset).toString()
            val escapedRange = suggestEscapes(locationRange)
            val fix = LintFix.create()
                .name("Escape")
                .replace()
                .text(locationRange)
                .with(escapedRange)
                .build()
            val location = Location.create(context.file, contents, startOffset, endOffset)
            context.report(ESCAPE, location, message, fix)
        }
    }

    companion object {
        /** Property file not escaped  */
        @JvmField
        val ESCAPE = Issue.create(
            "PropertyEscape",
            "Incorrect property escapes",
            "All backslashes and colons in .property files must be escaped with " +
                    "a backslash (\\). This means that when writing a Windows path, you " +
                    "must escape the file separators, so the path \\My\\Files should be " +
                    "written as `key=\\\\My\\\\Files.`",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            Implementation(PropertyFileDetector::class.java, Scope.PROPERTY_SCOPE)
        )

        /** Using HTTP instead of HTTPS for the wrapper  */
        @JvmField
        val HTTP = Issue.create(
            "UsingHttp",
            "Using HTTP instead of HTTPS",
            "The Gradle Wrapper is available both via HTTP and HTTPS. HTTPS is more " +
                    "secure since it protects against man-in-the-middle attacks etc. Older " +
                    "projects created in Android Studio used HTTP but we now default to HTTPS " +
                    "and recommend upgrading existing projects.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            Implementation(PropertyFileDetector::class.java, Scope.PROPERTY_SCOPE)
        )

        /** Using HTTP instead of HTTPS for the wrapper  */
        @JvmField
        val PROXY_PASSWORD = Issue.create(
            "ProxyPassword",
            "Proxy Password in Cleartext",
            "Storing proxy server passwords in clear text is dangerous if this file is " +
                    "shared via version control. If this is deliberate or this is a truly " +
                    "private project, suppress this warning.",
            Category.SECURITY,
            2,
            Severity.WARNING,
            Implementation(PropertyFileDetector::class.java, Scope.PROPERTY_SCOPE)
        )

        fun suggestEscapes(value: String): String {
            val escaped = value.replace("\\:", ":").replace("\\\\", "\\")
            return escapePropertyValue(escaped)
        }
    }
}
