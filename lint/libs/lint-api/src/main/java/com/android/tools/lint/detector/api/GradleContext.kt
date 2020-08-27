/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.lint.detector.api

import com.android.tools.lint.client.api.GradleVisitor
import com.android.tools.lint.client.api.LintDriver
import java.io.File
import java.util.regex.Pattern

/** Context for analyzing a particular file */
class GradleContext(
    /** Visitor to use to analyze the file */
    val gradleVisitor: GradleVisitor,

    /** the driver running through the checks */
    driver: LintDriver,

    /** the project to run lint on which contains the given file */
    project: Project,

    /**
     * The main project if this project is a library project, or
     * null if this is not a library project. The main project is
     * the root project of all library projects, not necessarily the
     * directly including project.
     */
    main: Project?,

    /** the file to be analyzed */
    file: File
) : Context(driver, project, main, file) {

    /** Returns a location for the given range */
    fun getLocation(cookie: Any): Location = gradleVisitor.createLocation(this, cookie)

    fun isSuppressedWithComment(cookie: Any, issue: Issue): Boolean {
        val startOffset = gradleVisitor.getStartOffset(this, cookie)
        return startOffset >= 0 && isSuppressedWithComment(startOffset, issue)
    }

    @Deprecated(message = "unused", replaceWith = ReplaceWith(expression = "cookie"))
    fun getPropertyKeyCookie(cookie: Any): Any = cookie

    @Deprecated(message = "unused", replaceWith = ReplaceWith(expression = "cookie"))
    fun getPropertyPairCookie(cookie: Any): Any = cookie

    /**
     * Reports an issue applicable to a given source location. The source location is used as the
     * scope to check for suppress lint annotations.
     *
     * @param issue the issue to report
     *
     * @param cookie the node scope the error applies to. The lint infrastructure will
     *                     check whether there are suppress annotations on this node (or its
     *                     enclosing nodes) and if so suppress the warning without involving the
     *                     client.
     *
     * @param location the location of the issue, or null if not known
     *
     * @param message the message for this warning
     *
     * @param fix optional data to pass to the IDE for use by a quickfix.
     */
    fun report(
        issue: Issue,
        cookie: Any,
        location: Location,
        message: String,
        fix: LintFix? = null
    ) {
        val context = this
        if (context.isEnabled(issue)) {
            // Suppressed?
            // Temporarily unconditionally checking for suppress comments in Gradle files
            // since Studio insists on an AndroidLint id prefix
            val checkComments = /*context.getClient().checkForSuppressComments() &&*/
                context.containsCommentSuppress()
            if (checkComments) {
                val startOffset = gradleVisitor.getStartOffset(context, cookie)
                if (startOffset >= 0 && context.isSuppressedWithComment(startOffset, issue)) {
                    return
                }
            }

            super.doReport(issue, location, message, fix)
        }
    }

    companion object {
        fun getStringLiteralValue(value: String): String? {
            return if (value.length > 2 && (
                value.startsWith("'") && value.endsWith("'") || value.startsWith(
                    "\""
                ) && value.endsWith(
                    "\""
                )
                )
            ) {
                value.substring(1, value.length - 1)
            } else null
        }

        fun getIntLiteralValue(value: String, defaultValue: Int): Int {
            return try {
                Integer.parseInt(value)
            } catch (e: NumberFormatException) {
                defaultValue
            }
        }

        private val DIGITS = Pattern.compile("\\d+")

        fun isNonNegativeInteger(token: String): Boolean {
            return DIGITS.matcher(token).matches()
        }

        fun isStringLiteral(token: String): Boolean {
            return token.startsWith("\"") && token.endsWith("\"") ||
                token.startsWith("'") && token.endsWith("'")
        }
    }
}
