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

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.google.common.annotations.Beta
import com.google.common.collect.Maps
import com.google.common.collect.Sets
import java.util.ArrayList
import java.util.Collections
import java.util.EnumSet
import java.util.HashMap
import java.util.HashSet

/**
 * Registry which provides a list of checks to be performed on an Android project
 *
 *
 * **NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.**
 */
@Beta
abstract class IssueRegistry
/**
 * Creates a new [IssueRegistry]
 */
protected constructor() {

    /**
     * The Lint API version this issue registry's checks were compiled.
     * You should return
     */
    open val api: Int = -1

    /**
     * The minimum API version this issue registry works with. Normally the
     * same as [api], but if you have tested it with older version and it
     * works, you can return that level.
     */
    open val minApi: Int
        get() {
            return api
        }

    /**
     * The list of issues that can be found by all known detectors (including those that may be
     * disabled!)
     */
    abstract val issues: List<Issue>

    /**
     * Whether this issue registry is up to date. Normally true but for example
     * for custom rules loaded from disk, may return false if the underlying file is updated
     * or deleted.
     */
    open val isUpToDate: Boolean = true

    /**
     * Get an approximate issue count for a given scope. This is just an optimization,
     * so the number does not have to be accurate.
     *
     * @param scope the scope set
     * @return an approximate ceiling of the number of issues expected for a given scope set
     */
    protected open fun getIssueCapacity(scope: EnumSet<Scope>): Int = 20

    /**
     * Returns all available issues of a given scope (regardless of whether
     * they are actually enabled for a given configuration etc)
     *
     * @param scope the applicable scope set
     * @return a list of issues
     */
    protected open fun getIssuesForScope(scope: EnumSet<Scope>): List<Issue> {
        var list: List<Issue>? = scopeIssues[scope]
        if (list == null) {
            val issues = issues
            if (scope == Scope.ALL) {
                list = issues
            } else {
                list = ArrayList(getIssueCapacity(scope))
                for (issue in issues) {
                    // Determine if the scope matches
                    if (issue.implementation.isAdequate(scope)) {
                        list.add(issue)
                    }
                }
            }
            scopeIssues[scope] = list
        }

        return list
    }

    /**
     * Creates a list of detectors applicable to the given scope, and with the
     * given configuration.
     *
     * @param client the client to report errors to
     * @param configuration the configuration to look up which issues are
     * enabled etc from
     * @param scope the scope for the analysis, to filter out detectors that
     * require wider analysis than is currently being performed
     * @param scopeToDetectors an optional map which (if not null) will be
     * filled by this method to contain mappings from each scope to
     * the applicable detectors for that scope
     * @return a list of new detector instances
     */
    internal fun createDetectors(
        client: LintClient,
        configuration: Configuration,
        scope: EnumSet<Scope>,
        scopeToDetectors: MutableMap<Scope, MutableList<Detector>>?
    ): List<Detector> {

        val issues = getIssuesForScope(scope)
        if (issues.isEmpty()) {
            return emptyList()
        }

        val detectorClasses = HashSet<Class<out Detector>>()
        val detectorToScope = HashMap<Class<out Detector>, EnumSet<Scope>>()

        for (issue in issues) {
            val implementation = issue.implementation
            var detectorClass: Class<out Detector> = implementation.detectorClass
            val issueScope = implementation.scope
            if (!detectorClasses.contains(detectorClass)) {
                // Determine if the issue is enabled
                if (!configuration.isEnabled(issue)) {
                    continue
                }

                assert(implementation.isAdequate(scope)) // Ensured by getIssuesForScope above
                detectorClass = client.replaceDetector(detectorClass)
                detectorClasses.add(detectorClass)
            }

            if (scopeToDetectors != null) {
                val s = detectorToScope[detectorClass]
                if (s == null) {
                    detectorToScope[detectorClass] = issueScope
                } else if (!s.containsAll(issueScope)) {
                    val union = EnumSet.copyOf(s)
                    union.addAll(issueScope)
                    detectorToScope[detectorClass] = union
                }
            }
        }

        val detectors = ArrayList<Detector>(detectorClasses.size)
        for (clz in detectorClasses) {
            try {
                val detector = clz.newInstance()
                detectors.add(detector)

                if (scopeToDetectors != null) {
                    val union = detectorToScope[clz] ?: continue
                    for (s in union) {
                        var list: MutableList<Detector>? = scopeToDetectors[s]
                        if (list == null) {
                            list = ArrayList()
                            scopeToDetectors[s] = list
                        }
                        list.add(detector)
                    }
                }
            } catch (t: Throwable) {
                client.log(t, "Can't initialize detector %1\$s", clz.name)
            }
        }

        return detectors
    }

    /**
     * Returns true if the given id represents a valid issue id
     *
     * @param id the id to be checked
     * @return true if the given id is valid
     */
    fun isIssueId(id: String): Boolean {
        return getIssue(id) != null
    }

    /**
     * Returns true if the given category is a valid category
     *
     * @param name the category name to be checked
     * @return true if the given string is a valid category
     */
    fun isCategoryName(name: String): Boolean {
        for (category in getCategories()) {
            if (category.name == name || category.fullName == name) {
                return true
            }
        }

        return false
    }

    /**
     * Returns the available categories
     *
     * @return an iterator for all the categories, never null
     */
    fun getCategories(): List<Category> {
        var categories = IssueRegistry.categories
        if (categories == null) {
            synchronized(IssueRegistry::class.java) {
                categories = IssueRegistry.categories
                if (categories == null) {
                    categories = Collections.unmodifiableList(createCategoryList())
                    IssueRegistry.categories = categories
                }
            }
        }

        return categories!!
    }

    private fun createCategoryList(): List<Category> {
        val categorySet = Sets.newHashSetWithExpectedSize<Category>(20)
        for (issue in issues) {
            categorySet.add(issue.category)
        }
        val sorted = ArrayList(categorySet)
        Collections.sort(sorted)
        return sorted
    }

    /**
     * Returns the issue for the given id, or null if it's not a valid id
     *
     * @param id the id to be checked
     * @return the corresponding issue, or null
     */
    fun getIssue(id: String): Issue? {
        var map = idToIssue
        if (map == null) {
            synchronized(IssueRegistry::class.java) {
                map = idToIssue
                if (map == null) {
                    map = createIdToIssueMap()
                    idToIssue = map
                }
            }
        }

        return map!![id]
    }

    private fun createIdToIssueMap(): Map<String, Issue> {
        val issues = issues
        val map = Maps.newHashMapWithExpectedSize<String, Issue>(issues.size + 2)
        for (issue in issues) {
            map[issue.id] = issue
        }

        map[PARSER_ERROR.id] = PARSER_ERROR
        map[LINT_ERROR.id] = LINT_ERROR
        map[BASELINE.id] = BASELINE
        map[OBSOLETE_LINT_CHECK.id] = OBSOLETE_LINT_CHECK
        return map
    }

    companion object {
        @Volatile
        private var categories: List<Category>? = null
        @Volatile
        private var idToIssue: Map<String, Issue>? = null
        private var scopeIssues: MutableMap<EnumSet<Scope>, List<Issue>> = Maps.newHashMap()

        private val DUMMY_IMPLEMENTATION = Implementation(
            Detector::class.java,
            EnumSet.noneOf(Scope::class.java)
        )
        /**
         * Issue reported by lint (not a specific detector) when it cannot even
         * parse an XML file prior to analysis
         */
        @JvmField // temporarily
        val PARSER_ERROR = Issue.create(
            "ParserError",
            "Parser Errors",
            "Lint will ignore any files that contain fatal parsing errors. These may contain other errors, or contain code which affects issues in other files.",
            Category.LINT,
            10,
            Severity.ERROR,
            DUMMY_IMPLEMENTATION
        )

        /**
         * Issue reported by lint for various other issues which prevents lint from
         * running normally when it's not necessarily an error in the user's code base.
         */
        @JvmField // temporarily
        val LINT_ERROR = Issue.create(
            "LintError",
            "Lint Failure",
            "This issue type represents a problem running lint itself. Examples include " +
                    "failure to find bytecode for source files (which means certain detectors " +
                    "could not be run), parsing errors in lint configuration files, etc." +
                    "\n" +
                    "These errors are not errors in your own code, but they are shown to make " +
                    "it clear that some checks were not completed.",

            Category.LINT,
            10,
            Severity.ERROR,
            DUMMY_IMPLEMENTATION
        )

        /**
         * Issue reported when lint is canceled
         */
        @JvmField // temporarily
        val CANCELLED = Issue.create(
            "LintCanceled",
            "Lint Canceled",
            "Lint canceled by user; the issue report may not be complete.",

            Category.LINT,
            0,
            Severity.INFORMATIONAL,
            DUMMY_IMPLEMENTATION
        )

        /**
         * Issue reported by lint for various other issues which prevents lint from
         * running normally when it's not necessarily an error in the user's code base.
         */
        @JvmField // temporarily
        val BASELINE = Issue.create(
            "LintBaseline",
            "Baseline Issues",
            "Lint can be configured with a \"baseline\"; a set of current issues found in " +
                    "a codebase, which future runs of lint will silently ignore. Only new issues " +
                    "not found in the baseline are reported.\n" +
                    "\n" +
                    "Note that while opening files in the IDE, baseline issues are not filtered out; " +
                    "the purpose of baselines is to allow you to get started using lint and break " +
                    "the build on all newly introduced errors, without having to go back and fix the " +
                    "entire codebase up front. However, when you open up existing files you still " +
                    "want to be aware of and fix issues as you come across them.\n" +
                    "\n" +
                    "This issue type is used to emit two types of informational messages in reports: " +
                    "first, whether any issues were filtered out so you don't have a false sense of " +
                    "security if you forgot that you've checked in a baseline file, and second, " +
                    "whether any issues in the baseline file appear to have been fixed such that you " +
                    "can stop filtering them out and get warned if the issues are re-introduced.",

            Category.LINT,
            10,
            Severity.INFORMATIONAL,
            DUMMY_IMPLEMENTATION
        )

        /**
         * Issue reported by lint when it encounters old lint checks that haven't been
         * updated to the latest APIs.
         */
        @JvmField // temporarily
        val OBSOLETE_LINT_CHECK = Issue.create(
            "ObsoleteLintCustomCheck",
            "Obsolete custom lint check",

            "Lint can be extended with \"custom checks\": additional checks implemented by " +
                    "developers and libraries to for example enforce specific API usages required " +
                    "by a library or a company coding style guideline.\n" +
                    "\n" +
                    "The Lint APIs are not yet stable, so these checks may either cause a performance, " +
                    "degradation, or stop working, or provide wrong results.\n" +
                    "\n" +
                    "This warning flags custom lint checks that are found to be using obsolete APIs and " +
                    "will need to be updated to run in the current lint environment." +
                    "\n" +
                    "It may also flag issues found to be using a **newer** version of the API, " +
                    "meaning that you need to use a newer version of lint (or Android Studio " +
                    "or Gradle plugin etc) to work with these checks.",

            Category.LINT,
            10,
            Severity.WARNING,
            DUMMY_IMPLEMENTATION
        )

        /**
         * Reset the registry such that it recomputes its available issues.
         */
        fun reset() {
            synchronized(IssueRegistry::class.java) {
                idToIssue = null
                categories = null
                scopeIssues = Maps.newHashMap()
            }
        }
    }
}
