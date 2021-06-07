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

import com.android.SdkConstants.ATTR_ID
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.TextFormat
import com.android.tools.lint.detector.api.guessGradleLocation
import com.google.common.annotations.Beta
import java.io.File

/**
 * Lint configuration for an Android project such as which specific
 * rules to include, which specific rules to exclude, and which specific
 * errors to ignore.
 *
 * **NOTE: This is not a public or final API; if you rely on this be
 * prepared to adjust your code for the next tools release.**
 */
@Beta
abstract class Configuration(
    val configurations: ConfigurationHierarchy
) {
    val client: LintClient get() = configurations.client

    /** Returns the parent configuration, if any. */
    val parent: Configuration? get() = configurations.getParentConfiguration(this)

    /**
     * Whether this configuration applies below the project level, e.g.
     * typically for a source folder.
     */
    open var fileLevel: Boolean = true

    /**
     * Whether this configuration is an overriding configuration. This
     * isn't just true for [ConfigurationHierarchy.overrides] but any
     * parent configuration of it as well.
     */
    var isOverriding: Boolean = false

    /**
     * Returns the overriding configuration, if any. Returns null when
     * called on the overriding configuration itself (or any of its
     * parents)
     */
    protected val overrides: Configuration?
        get() = if (isOverriding) null else client.configurations.overrides

    /**
     * The "scope" of this configuration. Will be null for
     * configurations that aren't associated with a specific scope,
     * such as a fallback configuration (--config) or an override
     * configuration (always applies first).
     */
    var dir: File? = null

    /**
     * The baseline file to use, if any. The baseline file is an XML
     * report previously created by lint, and any warnings and errors
     * listed in that report will be ignored from analysis.
     *
     * If you have a project with a large number of existing warnings,
     * this lets you set a baseline and only see newly introduced
     * warnings until you get a chance to go back and address the
     * "technical debt" of the earlier warnings.
     */
    abstract var baselineFile: File?

    /**
     * Checks whether this incident should be ignored because the user
     * has already suppressed the error. Note that this refers to
     * individual issues being suppressed/ignored, not a whole detector
     * being disabled via something like [isEnabled].
     *
     * @param context the context used by the detector when the issue
     *     was found
     * @param issue the issue that was found
     * @param location the location of the issue
     * @param message the associated user message
     * @return true if this issue should be suppressed
     */
    @Deprecated(
        "Use the new isIgnored(Context, Incident) method instead",
        ReplaceWith(
            "isIgnored(Incident(context, incident))",
            "com.android.tools.lint.detector.api.Incident"
        )
    )
    fun isIgnored(
        context: Context,
        issue: Issue,
        location: Location?,
        message: String
    ): Boolean {
        return isIgnored(context, Incident(issue, location ?: Location.NONE, message))
    }

    /**
     * Checks whether this [incident] should be ignored because the
     * user has already suppressed the error. Note that this refers to
     * individual issues being suppressed/ignored, not a whole detector
     * being disabled via something like [isEnabled].
     */
    open fun isIgnored(context: Context, incident: Incident): Boolean {
        return parent?.isIgnored(context, incident) ?: false
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
     * Return the severity configured for this [issue] by this
     * configuration or any configurations it inherits from, or null
     * if the given issue has not been configured. The [source]
     * is the original configuration this is requested for. The
     * [visibleDefault] severity is a severity to use if the
     * configuration forces an issue to be visible (for example via
     * `--check IssueId` or in a unit test where the test infrastructure
     * forces the tested issues to not be hidden) without specifying
     * what the severity should be (normally [Issue.defaultSeverity]).
     */
    open fun getDefinedSeverity(
        issue: Issue,
        source: Configuration = this,
        visibleDefault: Severity = issue.defaultSeverity
    ): Severity? {
        if (!isOverriding && source === this) {
            overrides?.let {
                it.getDefinedSeverity(issue, source, visibleDefault)?.let { severity ->
                    return severity
                }
            }
        }

        return null
    }

    /**
     * The default severity of an issue; should be ignore for disabled
     * issues, not its severity when it's enabled.
     */
    protected open fun getDefaultSeverity(issue: Issue, visibleDefault: Severity = issue.defaultSeverity): Severity {
        return if (!issue.isEnabledByDefault()) Severity.IGNORE else visibleDefault
    }

    /**
     * Returns the severity for a given issue. This is the same as the
     * [Issue.defaultSeverity] unless the user has selected a custom
     * severity (which is tool context dependent).
     *
     * If the issue is not configured by this configuration (or
     * configurations it inherits from), this will return the default
     * severity. To get the severity only if it's configured, use
     * [getDefinedSeverity].
     *
     * @param issue the issue to look up the severity from
     * @return the severity use for issues for the given detector
     */
    fun getSeverity(issue: Issue): Severity {
        overrides?.getDefinedSeverity(issue, this)?.let {
            return it
        }

        return getDefinedSeverity(issue) ?: getDefaultSeverity(issue)
    }

    /**
     * Returns the value for the given option, or the default value
     * (normally null) if it has not been specified.
     */
    open fun getOption(
        issue: Issue,
        name: String,
        default: String? = null
    ): String? {
        // Using null as the default here: if not defined in the override
        // configuration, we don't want to just return the default, we want
        // to proceed with the non-override configurations
        overrides?.getOption(issue, name, null)?.let {
            return it
        }
        return parent?.getOption(issue, name, default) ?: default
    }

    /**
     * Returns the value for the given option as an int, or the default
     * value if not specified (or if the option is not a valid integer)
     */
    fun getOptionAsInt(issue: Issue, name: String, default: Int): Int {
        return try {
            getOption(issue, name, null)?.toInt() ?: default
        } catch (e: NumberFormatException) {
            return default
        }
    }

    /**
     * Returns the value for the given option as a boolean, or the
     * default value if not specified.
     */
    fun getOptionAsBoolean(issue: Issue, name: String, default: Boolean): Boolean {
        return getOption(issue, name, null)?.toBoolean() ?: default
    }

    /**
     * Returns the value for the given option as an absolute [File].
     * It's important to use this method instead of trying to interpret
     * the string options returned from [getOption] yourself, since we
     * support relative paths, and the path is relative to the lint.xml
     * file which defines the option, and since configurations can
     * inherit from other configurations, you can't know by just calling
     * [getOption] where a value is defined, and therefore how to
     * interpret the relative path.
     */
    open fun getOptionAsFile(
        issue: Issue,
        name: String,
        default: File? = null
    ): File? {
        return parent?.getOptionAsFile(issue, name, default) ?: default
    }

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

    /** Like [ignore(Issue,file)] but with just the string id. */
    abstract fun ignore(issueId: String, file: File)

    /**
     * Sets the severity to be used for this issue.
     *
     * @param issue the issue to set the severity for
     * @param severity the severity to associate with this issue, or
     *     null to reset the severity to the default
     */
    abstract fun setSeverity(issue: Issue, severity: Severity?)

    /**
     * Marks the beginning of a "bulk" editing operation with repeated
     * calls to [setSeverity] or [ignore]. After all the values have
     * been set, the client **must** call [finishBulkEditing]. This
     * allows configurations to avoid doing expensive I/O (such as
     * writing out a config XML file) for each and every editing
     * operation when they are applied in bulk, such as from a
     * configuration dialog's "Apply" action.
     */
    open fun startBulkEditing() {}

    /**
     * Marks the end of a "bulk" editing operation, where values should
     * be committed to persistent storage. See [startBulkEditing] for
     * details.
     */
    open fun finishBulkEditing() {}

    /**
     * Makes sure that any custom severity definitions defined in this
     * configuration refer to valid issue id's, valid severities etc.
     * This helps catch bugs in manually edited config files (see issue
     * 194382).
     *
     * @param client the lint client to report to
     * @param driver the active lint driver
     * @param project the project relevant to the configuration, if
     *     known
     * @param registry the fully initialized registry (might include
     *     custom lint checks from libraries etc)
     */
    open fun validateIssueIds(
        client: LintClient,
        driver: LintDriver,
        project: Project?,
        registry: IssueRegistry
    ) {
        parent?.validateIssueIds(client, driver, project, registry)
    }

    /** Returns a list of lint jar files to include in the analysis. */
    open fun getLintJars(): List<File> = parent?.getLintJars() ?: emptyList()

    /** Sets the given configuration as the parent of this one. */
    fun setParent(parent: Configuration) = configurations.setParent(this, parent)

    /**
     * Returns a map of all the issues configured by this configuration
     * and the configured severities. The issue registry is the
     * registry used for analysis; this normally has no effect, but if
     * a configuration for example specifies "enable all warnings",
     * then all the (not warning by default) issues found in the given
     * registry will be returned. (Another example: lint.xml specifying
     * <issue="all" ...>. We need to store the specific id's rather than
     * "all" because the meaning of "all" can vary from module to module
     * based on which issues are present in the issue registry -- saying
     * severity for "all" is error in a library shouldn't also configure
     * additional issues available in downstream app modules as well.)
     */
    fun getConfiguredIssues(registry: IssueRegistry, specificOnly: Boolean): Map<String, Severity> {
        val map = mutableMapOf<String, Severity>()
        overrides?.addConfiguredIssues(map, registry, specificOnly)
        addConfiguredIssues(map, registry, specificOnly)
        return map
    }

    /**
     * Helper method overridden in most configurations to provide
     * partial results for [getConfiguredIssues]. Generally the
     * algorithm is to first call super, and then to analyze the
     * current configuration; that makes sure that if a more specific
     * configuration overrides an outer configuration, the map ends up
     * with the override severity.
     *
     * If [specificOnly] is true, it will ignore generic configuration
     * matches (such as references to "all" or flags like
     * checkAllWarnings).
     */
    abstract fun addConfiguredIssues(
        targetMap: MutableMap<String, Severity>,
        registry: IssueRegistry,
        specificOnly: Boolean
        // TODO: IF you enable all warnings with -w, those will be enabled individually
        // here. Decide if that's the right behavior. Probably more relevant for
        // -nowarn.
    )

    /**
     * Attempts to find the configuration location responsible for a
     * given [issue]'s configuration (such as severity). It will make
     * sure that the location applies; e.g. if an issue is specified in
     * a parent configuration, but is blocked by an "all" match in a
     * closer configuration, this will return that "all" location, or if
     * [specificOnly] is true, null.
     *
     * If [specificOnly] is true, it will ignore generic configuration
     * matches (such as references to "all" or flags like
     * checkAllWarnings).
     *
     * If [severityOnly] is true, limit the search to configurations
     * that set the issue severity (as opposed to option configuration,
     * setting ignore paths, etc.)
     */
    fun getIssueConfigLocation(
        issue: String,
        specificOnly: Boolean = false,
        severityOnly: Boolean = false
    ): Location? {
        overrides?.getLocalIssueConfigLocation(issue, specificOnly, severityOnly, this)?.let {
            return it
        }

        return getLocalIssueConfigLocation(issue, specificOnly, severityOnly)
    }

    /**
     * Like [getIssueConfigLocation] but only looks at this specific
     * configuration whereas [getIssueConfigLocation] will consult
     * override configurations too (and should not recurse).
     */
    open fun getLocalIssueConfigLocation(
        issue: String,
        specificOnly: Boolean = false,
        severityOnly: Boolean = false,
        source: Configuration = this
    ): Location? = null

    /**
     * Convenience method for configurations to report unknown issue id
     * problems.
     */
    protected fun reportNonExistingIssueId(
        client: LintClient,
        driver: LintDriver?,
        issueRegistry: IssueRegistry,
        project: Project?,
        id: String
    ) {
        val newId = IssueRegistry.getNewId(id)
        val message =
            if (newId != null) {
                return
            } else if (IssueRegistry.isDeletedIssueId(id)) {
                // Recently deleted, but avoid complaining about leftover configuration
                null
            } else {
                getUnknownIssueIdErrorMessage(id, issueRegistry)
            }
                ?: return
        if (driver != null) {
            val severity = getSeverity(IssueRegistry.UNKNOWN_ISSUE_ID)
            if (severity !== Severity.IGNORE) {
                val location = getIssueConfigLocation(id, specificOnly = true, severityOnly = false)
                    ?: if (project != null) {
                        guessGradleLocation(project)
                    } else {
                        Location.create(File("(unknown location; supplied by command line flags)"))
                    }
                LintClient.report(
                    client = client,
                    issue = IssueRegistry.UNKNOWN_ISSUE_ID,
                    message = message,
                    driver = driver,
                    project = project,
                    location = location,
                    fix = LintFix.create().data(ATTR_ID, id)
                )
            }
        }
    }

    companion object {
        /**
         * Creates the error message to show when an unknown issue id is
         * encountered.
         */
        fun getUnknownIssueIdErrorMessage(id: String, issueRegistry: IssueRegistry): String {
            val message = StringBuilder(30)
            message.append("Unknown issue id \"").append(id).append("\"")
            val suggestions: List<String> = issueRegistry.getIdSpellingSuggestions(id)
            if (suggestions.isNotEmpty()) {
                message.append(". Did you mean")
                val size = suggestions.size
                if (size == 1) {
                    message.append(" ")
                    appendIssueDescription(message, suggestions[0], issueRegistry)
                    message.append(" ")
                } else {
                    message.append(":\n")
                    for (suggestion in suggestions) {
                        appendIssueDescription(message, suggestion, issueRegistry)
                        message.append("\n")
                    }
                }
                message.append("?")
            }
            return message.toString()
        }

        private fun appendIssueDescription(
            message: StringBuilder,
            id: String,
            issueRegistry: IssueRegistry
        ) {
            message.append("'").append(id).append("'")
            val issue = issueRegistry.getIssue(id)
            if (issue != null) {
                message.append(" (")
                message.append(issue.getBriefDescription(TextFormat.RAW))
                message.append(")")
            }
        }
    }
}
