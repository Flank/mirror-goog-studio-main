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

import com.android.ide.common.resources.ResourceRepository
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.TextFormat
import com.android.tools.lint.detector.api.assertionsEnabled
import com.android.tools.lint.model.LintModelLintOptions
import java.io.File
import java.util.HashSet

/**
 * Manages configurations, included nested configurations. This is
 * intended for lint itself (and integrations of lint into tools), not
 * for detector usage.
 */
open class ConfigurationHierarchy(
    val client: LintClient,

    /**
     * The root folder where lint.xml configuration search should end,
     * or null.
     */
    var rootDir: File? = defaultRootDir
) {
    private val dirToConfiguration: MutableMap<File, Configuration> = HashMap()
    private val projectToConfiguration: MutableMap<Project, Configuration> = HashMap()
    private val parentOf: MutableMap<Configuration, Configuration> = HashMap()

    /**
     * The fallback configuration to use (specified via --config or
     * lintConfig); not implicitly parented from its directory location)
     */
    var fallback: Configuration? = null

    /**
     * A configuration which overrides everything else (is consulted
     * first)
     */
    var overrides: Configuration? = null

    fun getConfigurationForProject(
        project: Project,
        /**
         * Create a configuration for the given directory. If the
         * configuration parameter is not null, this corresponds to
         * a LintXmlConfiguration that should be included in the
         * inheritance chain.
         */
        create: ((File, Configuration?) -> Configuration?) = lintXmlCreator
    ): Configuration {
        val prev = dirToConfiguration[project.dir]
        if (prev != null && prev !== NONE) {
            return prev
        }

        val dir = project.dir
        val file = dir.getLintXmlFile()
        val default = if (file.isFile) {
            LintXmlConfiguration.create(this, file).also { it.fileLevel = false }
        } else {
            null
        }

        val configuration = create(dir, default)
            ?: ProjectPlaceholderConfiguration(this, dir)
        projectToConfiguration[project] = configuration
        if (dirToConfiguration[dir] == null) dirToConfiguration[dir] = configuration
        if (default != null) {
            dirToConfiguration[file] = default
        }
        configuration.fileLevel = false
        return configuration
    }

    @Suppress("FileComparisons")
    fun getConfigurationForFile(
        xmlFile: File
    ): Configuration {
        val prev = dirToConfiguration[xmlFile]
        if (prev != null) {
            assert(prev !== NONE)
            return prev
        }
        val configuration = LintXmlConfiguration.create(this, xmlFile)
        val dir = configuration.dir
        if (dir != null && client.isKnownProjectDir(dir)) {
            configuration.fileLevel = false
        }

        if (dir != null && dirToConfiguration[dir] == null) {
            // looking up the configuration for a folder *containing* a lint.xml should also
            // return this instance
            dirToConfiguration[dir] = configuration
        }

        // Set cache here *after* possibly initializing the lint.xml file in the same
        // directory since we're keying the configuration by the default file path
        dirToConfiguration[xmlFile] = configuration

        return configuration
    }

    /**
     * Looks up the configuration to use for a given [dir]. The
     * [default] configuration, if specified, will be returned as the
     * fallback (including as a parent, if an intermediate configuration
     * file is found.)
     */
    @Suppress("FileComparisons")
    fun getConfigurationForFolder(
        dir: File?,
        default: Configuration? = null
    ): Configuration? {
        if (dir == null) {
            return default
        }
        val prev = dirToConfiguration[dir]
        if (prev != null) {
            return if (prev !== NONE) prev else null
        }
        val file = dir.getLintXmlFile()
        if (file.isFile) {
            return getConfigurationForFile(file) // already stores in [dirToConfiguration]
        }
        val parent =
            if (dir != rootDir)
                getConfigurationForFolder(getParentFolder(dir), default)
            else
                default
                    ?: fallback
        dirToConfiguration[dir] = parent ?: NONE
        return parent
    }

    /**
     * Looks up the parent configuration folder to check from the
     * given folder. Normally this is just the parent folder, but this
     * allows clients to customize the behavior (to for example stop
     * at multiple roots or follow some scheme that makes sense in the
     * local tool.)
     */
    open fun getParentFolder(folder: File): File? = folder.parentFile

    /**
     * Returns the parent configuration from the given configuration, if
     * any.
     */
    @Suppress("FileComparisons")
    fun getParentConfiguration(configuration: Configuration): Configuration? {
        val previouslyCreated = parentOf[configuration]
        if (previouslyCreated != null) {
            return when {
                previouslyCreated !== NONE -> previouslyCreated
                configuration.isOverriding -> null
                configuration !== fallback -> fallback
                else -> null
            }
        }

        val configurationDir = configuration.dir ?: return null
        val dir = getParentFolder(configurationDir)
        val parent = if (dir != null && dir != rootDir) {
            getConfigurationForFolder(dir, null)
        } else {
            null
        }
        if (parent != null) {
            setParent(configuration, parent)
            if (!configuration.fileLevel) {
                parent.fileLevel = false
            }
        } else {
            parentOf[configuration] = NONE
        }

        return parent
    }

    fun setParent(child: Configuration, parent: Configuration?) {
        if (parent == null) {
            parentOf.remove(child)
            return
        }
        parentOf[child] = parent
        checkForCycle(child)
    }

    fun checkForCycle(child: Configuration) {
        // Cycle check
        if (assertionsEnabled()) {
            var current: Configuration? = child
            var current2: Configuration? = child
            while (current != null) {
                current = parentOf[current] ?: return
                current2 = parentOf[parentOf[current2] ?: return] ?: return
                if (current === current2) {
                    client.log(Severity.ERROR, null, "Cyclical configuration chain")
                    parentOf.remove(child)
                    break
                }
            }
        }
    }

    /**
     * There can be multiple configurations for a given project
     * directory; given a start configuration, this method return the
     * last parent in the parent chain that is referencing the same
     * scope/project/directory.
     */
    fun getScopeLeaf(child: Configuration): Configuration {
        val dir = child.dir
        var p = child
        var parent = parentOf[p] ?: return p
        //noinspection FileComparisons
        while (parent.dir == dir) {
            p = parent
            parent = parentOf[p] ?: break
        }
        return p
    }

    /**
     * For a project that has a lint model, create a suitable
     * configuration. This could require wiring up 3 configurations.
     * First, the [LintOptionsConfiguration] itself, which is a flag
     * configuration and represents the DSL options like "checkOnly",
     * "checkAllWarnings", and so on.
     *
     * Second, if there is a `lint.xml` file in the project directory,
     * this is the next configuration in the inheritance chain.
     *
     * Finally, the DSL may reference another XML file via
     * `android.lintOptions.lintConfig`. This is the last configuration
     * in the inheritance chain.
     *
     * The last configuration is then inheriting from the configuration
     * of the parent directory.
     *
     * Note that only one of the three is required (the flag
     * configuration); the others area optional, and in that case, they
     * are skipped in the parenting chain.
     */
    fun createLintOptionsConfiguration(
        project: Project,
        lintOptions: LintModelLintOptions,
        fatalOnly: Boolean,
        /**
         * The lint.xml configuration in the project root directory, if
         * any.
         */
        default: Configuration?,
        configFactory: (() -> LintOptionsConfiguration) = {
            LintOptionsConfiguration(this, lintOptions, fatalOnly)
                .also { it.associatedLocation = Location.create(project.dir) }
        }
    ): Configuration {
        return createChainedConfigurations(
            project,
            default,
            configFactory,
            {
                val lintConfigXml = lintOptions.lintConfig
                if (lintConfigXml != null && lintConfigXml.isFile) {
                    LintXmlConfiguration.create(this, lintConfigXml).apply {
                        fileLevel = false
                    }
                } else {
                    null
                }
            }
        )
    }

    /**
     * Creates up to 3 configurations associated with the current
     * project. The factory method [createFirst] needs to return the
     * first configuration (the "override"). [createLast] can optionally
     * return the last configuration (the "fallback"), and if passed in,
     * the [middle] configuration is inserted in between them. Finally,
     * the last configuration is inheriting from the parent directory's
     * configuration. Note that only the override configuration is
     * required; the other two are optional, and are omitted if null.
     */
    fun createChainedConfigurations(
        project: Project,
        /**
         * The lint.xml configuration in the project root directory, if
         * any.
         */
        middle: Configuration?,
        /**
         * Creates the new configuration that should be the primary
         * configuration for the project.
         */
        createFirst: (() -> Configuration),
        /**
         * Optionally creates the new configuration that should be the
         * last/fallback configuration for the project (also known as
         * the scope leaf; see [getScopeLeaf])
         */
        createLast: (() -> Configuration?) = { null }
    ): Configuration {
        val dir = project.dir

        val primary = createFirst().apply {
            this.dir = dir
            fileLevel = false
        }

        val auxiliary = createLast()

        // Set up parent chains
        val parentFolder = dir.parentFile
        val parent =
            //noinspection FileComparisons
            if (middle != null && parentOf[middle] != null) {
                parentOf[middle] ?: NONE
            } else if (parentFolder != null && dir != rootDir) {
                getConfigurationForFolder(parentFolder) ?: NONE
            } else {
                NONE
            }

        if (auxiliary != null) {
            if (middle != null) {
                // Parent chain is primary => middle => auxiliary => parent
                parentOf[primary] = middle
                parentOf[middle] = auxiliary
                parentOf[auxiliary] = parent
            } else {
                // Parent chain is primary => auxiliary => parent
                parentOf[primary] = auxiliary
                parentOf[auxiliary] = parent
            }
        } else {
            if (middle != null) {
                // Parent chain is primary => middle => parent
                parentOf[primary] = middle
                parentOf[middle] = parent
            } else {
                // Parent chain is primary => parent
                parentOf[primary] = parent
            }
        }

        checkForCycle(primary)

        dirToConfiguration[dir] = primary
        return primary
    }

    /**
     * Default factory for configurations from file: creates
     * [LintXmlConfiguration]
     */
    private val lintXmlCreator: ((File, Configuration?) -> Configuration?) = { _, default ->
        default
    }

    fun addGlobalConfigurationFromFile(fallback: File? = null, override: Configuration? = null) {
        // Make sure flags take precedence over XML; e.g. if you specify
        // --check we want that to override any severity settings in the
        // XML file
        if (fallback != null) {
            if (!fallback.exists()) {
                val warned = ourAlreadyWarned ?: HashSet<File>().also {
                    ourAlreadyWarned = it
                }
                if (warned.add(fallback)) {
                    client.log(
                        Severity.ERROR,
                        null,
                        "Warning: Configuration file %1\$s does not exist",
                        fallback
                    )
                }
            } else {
                val xmlConfiguration = LintXmlConfiguration.create(this, fallback)
                // This XML configuration is not associated with its location so remove its
                // directory scope
                xmlConfiguration.dir = null
                xmlConfiguration.fileLevel = false
                addGlobalConfigurations(xmlConfiguration, override)
                return
            }
        }
        addGlobalConfigurations(null, override)
    }

    fun addGlobalConfigurations(fallback: Configuration? = null, override: Configuration? = null) {
        if (override != null) {
            override.isOverriding = true
            override.fileLevel = false
            val prev = overrides
            overrides = override
            //noinspection ExpensiveAssertion
            assert(parentOf[override] == null)
            prev?.let { override.setParent(prev) }
        }

        if (fallback != null) {
            fallback.fileLevel = false
            val prev = this.fallback
            //noinspection ExpensiveAssertion
            assert(parentOf[fallback] == null)
            this.fallback = fallback
            prev?.let { fallback.setParent(prev) }
        }
    }

    /**
     * Looks up the defined severity (if any) for the given [issue] in
     * the given [source] configuration or inherited configurations, but
     * do not apply the override semantics. This is typically needed
     * when an override configuration needs to know what the severity
     * would have been without the override; for example, in the
     * [FlagConfiguration], if you specify "--check" or "--enable" we
     * want to set the severity of the configuration to something other
     * than [Severity.IGNORE] but we want the severity to be what was
     * configured in the original severity context (lint.xml etc), not
     * just the default severity.
     */
    fun getDefinedSeverityWithoutOverride(
        source: Configuration,
        issue: Issue,
        visibleDefault: Severity = issue.defaultSeverity
    ): Severity? {
        if (source == overrides || overrides == null) {
            return null
        }
        val prev = overrides
        try {
            overrides = null
            return source.getDefinedSeverity(issue, source, visibleDefault)
        } finally {
            overrides = prev
        }
    }

    companion object {
        var defaultRootDir = System.getenv("LINT_XML_ROOT")?.let { File(it) }
            ?: File(System.getProperty("user.home"))

        private var ourAlreadyWarned: MutableSet<File>? = null

        /** Return the lint.xml file for the given directory. */
        fun File.getLintXmlFile() = File(this, LintXmlConfiguration.CONFIG_FILE_NAME)

        /**
         * Represents absence of a configuration; used in the cache to
         * remember places we've looked where nothing was found.
         */
        private val NONE = object : Configuration(
            ConfigurationHierarchy(object : LintClient() {
                private fun unsupported(): Nothing {
                    error("Not supported")
                }

                override fun report(context: Context, incident: Incident, format: TextFormat) {
                    unsupported()
                }

                override fun log(
                    severity: Severity,
                    exception: Throwable?,
                    format: String?,
                    vararg args: Any
                ) {
                    unsupported()
                }

                override val xmlParser: XmlParser
                    get() = unsupported()

                override fun getUastParser(project: Project?): UastParser {
                    unsupported()
                }

                override fun getGradleVisitor(): GradleVisitor {
                    unsupported()
                }

                override fun readFile(file: File): CharSequence {
                    unsupported()
                }

                override fun getResources(
                    project: Project,
                    scope: ResourceRepositoryScope
                ): ResourceRepository {
                    unsupported()
                }
            })
        ) {
            override var baselineFile: File? = null
            override fun ignore(
                context: Context,
                issue: Issue,
                location: Location?,
                message: String
            ) {
            }

            override fun ignore(issue: Issue, file: File) {}
            override fun ignore(issueId: String, file: File) {}
            override fun setSeverity(issue: Issue, severity: Severity?) {}

            override fun addConfiguredIssues(
                targetMap: MutableMap<String, Severity>,
                registry: IssueRegistry,
                specificOnly: Boolean
            ) {
            }

            override fun getOption(issue: Issue, name: String, default: String?): String? = default
            override fun getOptionAsFile(issue: Issue, name: String, default: File?): File? =
                default

            override fun toString(): String = "NONE"
        }
    }

    /**
     * This [ProjectPlaceholderConfiguration] corresponds to a project
     * root which does not have a lint.xml configuration. If we didn't
     * have these, we'd run into trouble if for example the user invokes
     * ignore("FlatIcon", File("icon.png")); this would edit the
     * (presumably shared) lintConfig configuration file. Similarly,
     * when we have multiple modules, we parent library modules pointing
     * to the dependent app module. If in the library module we don't
     * have a lint.xml file, we'll just use the fallback (lintConfig)
     * configuration. And then we'd end up re-parenting this fallback
     * configuration to point to the app module, and the app module in
     * turn will point to the same fallback configuration, and now we
     * have a cycle.
     *
     * Therefore, we have this [ProjectPlaceholderConfiguration],
     * which is just a forwarding configuration which sits at each
     * project root. In each project, it's this configuration rather
     * than the fallback configuration which is inherited down into
     * folders, and when we re-parent the library's configuration to
     * point to the app's configuration, we're updating each individual
     * [ProjectPlaceholderConfiguration] instead of touching the shared
     * fallback configuration.
     *
     * Similarly, if the user invokes an ignore action which writes
     * to the lint.xml file, we'll intercept that here, and create
     * the lint.xml file on the fly, and then delegate the updating
     * actions to it. (We do this by parenting the new configuration
     * to our current parent, and then changing our parent to the new
     * configuration.)
     */
    private inner class ProjectPlaceholderConfiguration(
        configurations: ConfigurationHierarchy,
        dir: File
    ) : Configuration(configurations) {
        init {
            fallback?.let { setParent(it) }
            this.dir = dir
        }

        override fun isEnabled(issue: Issue): Boolean {
            return parent?.isEnabled(issue) ?: super.isEnabled(issue)
        }

        override fun getDefinedSeverity(issue: Issue, source: Configuration, visibleDefault: Severity): Severity? {
            return parent?.getDefinedSeverity(issue, source, visibleDefault)
                ?: super.getDefinedSeverity(issue, source, visibleDefault)
        }

        override fun startBulkEditing() {
            parent?.startBulkEditing()
        }

        override fun finishBulkEditing() {
            parent?.finishBulkEditing()
        }

        override fun validateIssueIds(
            client: LintClient,
            driver: LintDriver,
            project: Project?,
            registry: IssueRegistry
        ) {
            parent?.validateIssueIds(client, driver, project, registry)
        }

        override fun addConfiguredIssues(
            targetMap: MutableMap<String, Severity>,
            registry: IssueRegistry,
            specificOnly: Boolean
        ) {
            parent?.addConfiguredIssues(targetMap, registry, specificOnly)
        }

        override fun getLocalIssueConfigLocation(
            issue: String,
            specificOnly: Boolean,
            severityOnly: Boolean,
            source: Configuration
        ): Location? {
            return parent?.getLocalIssueConfigLocation(issue, specificOnly, severityOnly, source)
        }

        override var fileLevel = false

        /**
         * If the configuration is modified, create the lint.xml file
         * and a new configuration representing it, insert it into the
         * hierarchy, and then forward the write action to it. Future
         * read actions will read from our new parent.
         */
        private fun ensureParentIsLocalLintXml() {
            val xmlFile = dir?.getLintXmlFile() ?: return
            if (!xmlFile.isFile) {
                xmlFile.writeText(
                    """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <lint>
                    </lint>
                    """.trimIndent()
                )
                val configuration = LintXmlConfiguration.create(configurations, xmlFile)
                configuration.fileLevel = false
                fileLevel = true
                val currentParent = parent
                currentParent?.let { configuration.setParent(it) }
                setParent(configuration)
            }
        }

        override var baselineFile: File?
            get() = parent?.baselineFile
            set(value) {
                ensureParentIsLocalLintXml()
                parent?.baselineFile = value
            }

        override fun ignore(context: Context, issue: Issue, location: Location?, message: String) {
            ensureParentIsLocalLintXml()
            parent?.ignore(context, issue, location, message)
        }

        override fun ignore(issue: Issue, file: File) {
            ignore(issue.id, file)
        }

        override fun ignore(issueId: String, file: File) {
            ensureParentIsLocalLintXml()
            parent?.ignore(issueId, file)
        }

        override fun setSeverity(issue: Issue, severity: Severity?) {
            ensureParentIsLocalLintXml()
            parent?.setSeverity(issue, severity)
        }
    }
}
