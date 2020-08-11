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

package com.android.tools.lint.client.api

import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Severity
import com.google.common.annotations.Beta
import java.io.File
import java.lang.NumberFormatException

/**
 * Lint configuration for an Android project such as which specific rules to include,
 * which specific rules to exclude, and which specific errors to ignore.
 *
 * **NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.**
 */
@Beta
abstract class Configuration {
    /** The "scope" of this configuration */
    abstract val dir: File

    /**
     * The baseline file to use, if any. The baseline file is
     * an XML report previously created by lint, and any warnings and
     * errors listed in that report will be ignored from analysis.
     *
     * If you have a project with a large number of existing warnings,
     * this lets you set a baseline and only see newly introduced warnings
     * until you get a chance to go back and address the "technical debt"
     * of the earlier warnings.
     */
    abstract var baselineFile: File?

    /**
     * Checks whether this issue should be ignored because the user has already
     * suppressed the error? Note that this refers to individual issues being
     * suppressed/ignored, not a whole detector being disabled via something
     * like [.isEnabled].
     *
     * @param context the context used by the detector when the issue was found
     * @param issue the issue that was found
     * @param location the location of the issue
     * @param message the associated user message
     * @return true if this issue should be suppressed
     */
    open fun isIgnored(
        context: Context,
        issue: Issue,
        location: Location?,
        message: String
    ): Boolean {
        return false
    }

    /**
     * Returns false if the given issue has been disabled. This is just
     * a convenience method for `getSeverity(issue) != Severity.IGNORE`.
     *
     * @param issue the issue to check
     * @return false if the issue has been disabled
     */
    open fun isEnabled(issue: Issue): Boolean {
        return getSeverity(issue) !== Severity.IGNORE
    }

    /**
     * Returns the severity for a given issue. This is the same as the
     * [Issue.defaultSeverity] unless the user has selected a custom
     * severity (which is tool context dependent).
     *
     * @param issue the issue to look up the severity from
     * @return the severity use for issues for the given detector
     */
    open fun getSeverity(issue: Issue): Severity {
        return issue.defaultSeverity
    }

    /**
     * Returns the value for the given option, or the default value (normally null)
     * if it has not been specified.
     */
    abstract fun getOption(
        issue: Issue,
        name: String,
        default: String? = null
    ): String?

    /**
     * Returns the value for the given option as an int, or the default value if
     * not specified (or if the option is not a valid integer)
     */
    fun getOptionAsInt(issue: Issue, name: String, default: Int): Int {
        return try {
            getOption(issue, name, null)?.toInt() ?: default
        } catch (e: NumberFormatException) {
            return default
        }
    }

    /**
     * Returns the value for the given option as a boolean, or the default value if
     * not specified
     */
    fun getOptionAsBoolean(issue: Issue, name: String, default: Boolean): Boolean {
        return getOption(issue, name, null)?.toBoolean() ?: default
    }

    /**
     * Returns the value for the given option as an absolute [File]. It's important to use
     * this method instead of trying to interpret the string options returned from
     * [getOption] yourself, since we support relative paths, and the path is relative
     * to the lint.xml file which defines the option, and since configurations can
     * inherit from other configurations, you can't know by just calling
     * [getOption] where a value is defined, and therefore how to interpret the
     * relative path.
     */
    abstract fun getOptionAsFile(
        issue: Issue,
        name: String,
        default: File? = null
    ): File?

    // Editing configurations

    /**
     * Marks the given warning as "ignored".
     *
     * @param context The scanning context
     * @param issue the issue to be ignored
     * @param location The location to ignore the warning at, if any
     * @param message The message for the warning
     */
    abstract fun ignore(
        context: Context,
        issue: Issue,
        location: Location?,
        message: String
    )

    /**
     * Marks the given issue and file combination as being ignored.
     *
     * @param issue the issue to be ignored in the given file
     * @param file the file to ignore the issue in
     */
    abstract fun ignore(issue: Issue, file: File)

    /**
     * Like [ignore(Issue,file)] but with just the string id
     */
    abstract fun ignore(issueId: String, file: File)

    /**
     * Sets the severity to be used for this issue.
     *
     * @param issue the issue to set the severity for
     * @param severity the severity to associate with this issue, or null to
     * reset the severity to the default
     */
    abstract fun setSeverity(issue: Issue, severity: Severity?)

    /**
     * Marks the beginning of a "bulk" editing operation with repeated calls to
     * [.setSeverity] or [.ignore]. After all the values have been
     * set, the client **must** call [.finishBulkEditing]. This
     * allows configurations to avoid doing expensive I/O (such as writing out a
     * config XML file) for each and every editing operation when they are
     * applied in bulk, such as from a configuration dialog's "Apply" action.
     */
    open fun startBulkEditing() {}

    /**
     * Marks the end of a "bulk" editing operation, where values should be
     * committed to persistent storage. See [.startBulkEditing] for
     * details.
     */
    open fun finishBulkEditing() {}

    /**
     * Makes sure that any custom severity definitions defined in this configuration refer to valid
     * issue id's, valid severities etc. This helps catch bugs in manually edited config files (see
     * issue 194382).
     *
     * @param client the lint client to report to
     * @param driver the active lint driver
     * @param project the project relevant to the configuration, if known
     * @param registry the fully initialized registry (might include custom lint checks from
     *                 libraries etc)
     */
    open fun validateIssueIds(
        client: LintClient,
        driver: LintDriver,
        project: Project,
        registry: IssueRegistry
    ) {
    }

    /** Returns a list of lint jar files to include in the analysis */
    open fun getLintJars(): List<File> = emptyList()

    /**
     * Whether this configuration corresponds to a project folder or higher.
     * Certain configuration is not allowed in individual source file folders,
     * such as enabling disabled lint checks.
     */
    open var projectLevel: Boolean = false
}
