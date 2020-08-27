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

import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.assertionsEnabled
import java.io.File

/** Manages configurations, included nested configurations */
open class ConfigurationHierarchy(
    private var client: LintClient,

    /**
     * The root folder where lint.xml configuration search should end, or null.
     */
    var rootDir: File? = System.getenv("LINT_XML_ROOT")?.let { File(it) }
        ?: File(System.getProperty("user.home"))
) {
    private val cache: MutableMap<File, Configuration> = mutableMapOf()
    private val parentOf: MutableMap<Configuration, Configuration> = mutableMapOf()
    private val projects: MutableMap<Project, Configuration> = mutableMapOf()

    fun getConfigurationForProject(
        project: Project,
        create: ((LintClient, File) -> Configuration) = lintXmlCreator
    ): Configuration {
        val file = File(project.dir, LintXmlConfiguration.CONFIG_FILE_NAME)
        val configuration = getConfigurationForFile(file, create)
        projects[project] = configuration
        configuration.projectLevel = true
        return configuration
    }

    @Suppress("FileComparisons")
    fun getConfigurationForFile(
        xmlFile: File,
        create: ((LintClient, File) -> Configuration) = lintXmlCreator
    ): Configuration {
        val cached = cache[xmlFile]
        if (cached != null) {
            assert(cached !== NONE)
            return cached
        }
        val configuration = create(client, xmlFile)
        val dir = configuration.dir

        if (cache[dir] == null) {
            // looking up the configuration for a folder *containing* a lint.xml should also
            // return this instance
            cache[dir] = configuration
        }

        if (create !== lintXmlCreator &&
            (
                configuration !is LintXmlConfiguration ||
                    configuration.configFile != xmlFile
                )
        ) {
            // Custom configuration class; see if there is *also* a lint.xml file
            // in the same folder; in which case we should read that one in too and
            // inherit from it
            if (xmlFile.isFile) {
                val parent = getConfigurationForFile(xmlFile, lintXmlCreator)
                if (configuration.projectLevel) {
                    parent.projectLevel = true
                }
                setParent(configuration, parent)
            }
        }

        // Set cache here *after* possibly initializing the lint.xml file in the same
        // directory since we're keying the configuration by the default file path
        cache[xmlFile] = configuration

        return configuration
    }

    /**
     * Looks up the configuration to use for a given [dir].
     * The [default] configuration, if specified, will be returned
     * as the fallback (including as a parent, if an intermediate configuration file is found.)
     */
    @Suppress("FileComparisons")
    fun getConfigurationForFolder(
        dir: File?,
        default: Configuration? = null
    ): Configuration? {
        if (dir == null) {
            return default
        }
        val cached = cache[dir]
        if (cached != null) {
            return if (cached !== NONE) cached else null
        }
        val file = File(dir, LintXmlConfiguration.CONFIG_FILE_NAME)
        if (file.isFile) {
            return getConfigurationForFile(file)
        }
        val parent =
            if (dir != rootDir)
                getConfigurationForFolder(getParentFolder(dir), default)
            else
                default
        cache[dir] = parent ?: NONE
        return parent
    }

    /** Looks up the parent configuration folder to check from the given folder.
     * Normally this is just the parent folder, but this allows clients to
     * customize the behavior (to for example stop at multiple roots or follow some
     * scheme that makes sense in the local tool.)
     */
    open fun getParentFolder(folder: File): File? = folder.parentFile

    /** Returns the parent configuration from the given configuration, if any. */
    @Suppress("FileComparisons")
    fun getParentConfiguration(configuration: Configuration): Configuration? {
        val cached = parentOf[configuration]
        if (cached != null) {
            return if (cached !== NONE) cached else null
        }

        val dir = getParentFolder(configuration.dir)
        val parent = if (dir != null && dir != rootDir) {
            getConfigurationForFolder(dir, null)
        } else {
            null
        }
        if (parent != null) {
            parentOf[configuration] = parent
            if (configuration.projectLevel) {
                parent.projectLevel = true
            }
        } else {
            parentOf[configuration] = NONE
        }

        return parent
    }

    fun setParent(child: Configuration, parent: Configuration) {
        parentOf[child] = parent

        // Cycle check
        if (assertionsEnabled()) {
            var current: Configuration? = child
            var current2: Configuration? = child
            while (current != null) {
                current = parentOf[current2] ?: return
                current2 = parentOf[parentOf[current2] ?: return] ?: return
                if (current === current2) {
                    error("Cyclical configuration chain")
                }
            }
        }
    }

    companion object {
        /** Default factory for configurations from file: creates [LintXmlConfiguration] */
        private val lintXmlCreator: ((LintClient, File) -> Configuration) = { client, configFile ->
            LintXmlConfiguration.create(client, configFile)
        }

        /**
         * Represents absence of a configuration; used in the cache to remember places we've
         * looked where nothing was found
         */
        private val NONE = object : Configuration() {
            override val dir: File get() = File(".")
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
            override fun getOption(issue: Issue, name: String, default: String?): String? = default
            override fun getOptionAsFile(issue: Issue, name: String, default: File?): File? =
                default

            override fun toString(): String = "NONE"
        }
    }
}
