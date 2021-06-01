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

package com.android.tools.lint.checks.infrastructure

import com.android.tools.lint.checks.infrastructure.ProjectDescription.Companion.populateProjectDirectory
import com.android.tools.lint.checks.infrastructure.TestMode.TestModeContext
import com.android.tools.lint.client.api.Configuration
import com.android.tools.lint.client.api.ConfigurationHierarchy
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.client.api.LintClient.Companion.clientName
import com.android.tools.lint.client.api.LintClient.Companion.ensureClientNameInitialized
import com.android.tools.lint.client.api.LintDriver
import com.android.tools.lint.client.api.LintListener
import com.android.tools.lint.client.api.LintXmlConfiguration.Companion.create
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Platform
import com.android.tools.lint.detector.api.Project
import com.google.common.collect.Lists
import com.google.common.collect.ObjectArrays
import com.google.common.io.Files
import org.junit.Assert
import org.junit.Assert.assertEquals
import java.io.File
import java.io.IOException
import java.util.EnumSet
import javax.annotation.CheckReturnValue

/**
 * The actual machinery for running lint tests for a given [task]. This
 * class is tied closely to the [TestLintTask] class, performing a
 * number of operations with(Task) to access package private state; the
 * intent is for the task class to only contain state and setup, and for
 * this class to contain actual test running code.
 */
class TestLintRunner(private val task: TestLintTask) {
    /** Whether the [.run] method has already been invoked. */
    var alreadyRun = false
        private set

    /** First exception encountered during the lint run, if any */
    var firstThrowable: Throwable? = null

    /** Returns all the platforms encountered by the given issues. */
    private fun computePlatforms(issues: List<Issue>): EnumSet<Platform> {
        val platforms = EnumSet.noneOf(Platform::class.java)
        for (issue in issues) {
            platforms.addAll(issue.platforms)
        }
        return platforms
    }

    /** The test mode currently being checked. */
    var currentTestMode: TestMode = TestMode.DEFAULT

    /**
     * Performs the lint check, returning the results of the lint check.
     * Note that this does not assert anything about the result; for
     * that, you'll want to call [TestLintResult.expect] or one or more
     * of the other check result methods.
     */
    @CheckReturnValue
    fun run(): TestLintResult {
        alreadyRun = true
        with(task) {
            ensureConfigured()
            val rootDir: File = when {
                rootDirectory != null -> rootDirectory
                testName != null -> File(tempDir, testName)
                else -> tempDir
            }.let {
                try {
                    // Use canonical path to make sure we don't end up failing
                    // to chop off the prefix from Project#getDisplayPath
                    it.canonicalFile
                } catch (ignore: IOException) {
                    it
                }
            }

            // Make sure tests don't pick up random things outside the test directory.
            // I had accidentally placed a file named lint.xml in /tmp, and this caused
            // a number of confusing failures!
            ConfigurationHierarchy.Companion.defaultRootDir = rootDir
            if (platforms == null) {
                platforms = computePlatforms(checkedIssues)
            }
            if (projects.implicitReportFrom != null &&
                platforms.contains(Platform.JDK) &&
                !platforms.contains(Platform.ANDROID)
            ) {
                for (project in projects) {
                    project.type = ProjectDescription.Type.JAVA
                }
            }
            projects.expandProjects()
            projects.addProject(reportFrom)
            val projectMap: MutableMap<String, List<File>> = HashMap()
            val results: MutableMap<TestMode, TestResultState> = HashMap()
            val notApplicable = HashSet<TestMode>()
            return try {
                // Note that the test types are taken care of in enum order.
                // This allows a test type to decide if it applies based on
                // an earlier test type (for example, resource repository
                // tests may only apply if we discovered in the default test
                // mode that the test actually consults the resource repository.)
                for (mode in task.testModes) {
                    currentTestMode = mode
                    firstThrowable = null

                    // Run lint with a specific test type?
                    // For example, the UInjectionHost tests are only relevant
                    // if the project contains Kotlin source files.
                    val projectList = projects.projects
                    if (!mode.applies(TestModeContext(this, projectList, emptyList(), null))) {
                        notApplicable.add(mode)
                        continue
                    }

                    // Look up output folder for projects; this allows
                    // multiple test types to share a single project tree
                    // (for example, UInjectionHost mode does not modify
                    // the project structure in any way)
                    val folderName: String = mode.folderName
                    val root = File(rootDir, folderName)
                    var files = projectMap[folderName]
                    if (files == null) {
                        files = this@TestLintRunner.createProjects(root)
                        if (TestLintTask.duplicateFinder != null && task.testName != null) {
                            TestLintTask.duplicateFinder.recordTestProject(task.testName, task, mode, files)
                        }
                        projectMap[folderName] = files
                    }
                    val beforeState = TestModeContext(this, projectList, files, null)
                    val clientState: Any? = mode.before(beforeState)
                    var listener: LintListener? = null
                    try {
                        val lintClient: TestLintClient = createClient()
                        mode.eventListener?.let {
                            listener =
                                object : LintListener {
                                    override fun update(
                                        driver: LintDriver,
                                        type: LintListener.EventType,
                                        project: Project?,
                                        context: Context?
                                    ) {
                                        val testContext = TestModeContext(
                                            task, projectList, files,
                                            clientState, driver, context
                                        )
                                        it.invoke(testContext, type, clientState)
                                    }
                                }
                            listeners.add(listener)
                        }

                        val testResult: TestResultState = checkLint(lintClient, root, files, mode)
                        results[mode] = testResult
                        if (projectInspector != null) {
                            val knownProjects = lintClient.knownProjects
                            val projects: List<Project> = ArrayList(knownProjects)
                            val driver = lintClient.driver
                            projects.sortedWith(Comparator.comparing { obj: Project -> obj.name })
                            projectInspector.inspect(driver, projects)
                        }
                    } finally {
                        val afterState = TestModeContext(this, projectList, files, clientState)
                        mode.after(afterState)
                        if (listener != null) {
                            listeners.remove(listener)
                        }
                    }
                }
                checkConsistentOutput(results)

                // If you specifically configure a test mode which is not applicable
                // in this test, produce a fake result which pinpoints the problem:
                for (mode in notApplicable) {
                    results[mode] = TestResultState(
                        createClient(), rootDir,
                        "No output because the configured test mode $mode is not " +
                            "applicable in this project context",
                        emptyList(),
                        null
                    )
                }

                val defaultMode = pickDefaultMode(results)
                TestLintResult(this, results, defaultMode)
            } catch (e: Throwable) {
                val state =
                    TestResultState(
                        createClient(), rootDir, e.message ?: "", emptyList(), e
                    )
                val defaultType: TestMode = testModes.iterator().next()
                results[defaultType] = state
                TestLintResult(this, results, defaultType)
            } finally {
                TestFile.deleteFilesRecursively(tempDir)
            }
        }
    }

    private fun checkLint(
        client: TestLintClient,
        rootDir: File,
        files: List<File>,
        mode: TestMode
    ): TestResultState {
        client.addCleanupDir(rootDir)
        client.setLintTask(task)
        return try {
            task.optionSetter?.set(client.flags)
            client.checkLint(rootDir, files, task.checkedIssues, mode)
        } finally {
            client.setLintTask(null)
        }
    }

    private fun pickDefaultMode(results: Map<TestMode, TestResultState>): TestMode {
        for (mode in TestMode.values()) {
            if (results.containsKey(mode)) {
                return mode
            }
        }

        // The test mode is not one of the built-in test modes; just use one of them
        return task.testModes.firstOrNull()
            ?: throw RuntimeException(
                "Invalid testModes configuration: ${task.testModes} and $results"
            )
    }

    private fun checkConsistentOutput(results: Map<TestMode, TestResultState>) {
        with(task) {
            if (!testModesIdenticalOutput) {
                return
            }

            // Make sure the output matches
            var prev: TestMode? = null
            for (mode in testModes) {
                if (prev == null) {
                    prev = mode
                    continue
                }
                // Skip if this is a configured test type which we skipped during analysis
                val resultState = results[mode] ?: continue
                val actual = resultState.output
                val expected = results[prev]?.output
                if (expected != actual) {
                    val expectedLabel = prev.description
                    val actualLabel = mode.description
                    val message = mode.diffExplanation
                        ?: """
                        The lint output was different between the test types
                        $prev and $mode.

                        If this difference is expected, you can set the
                        eventType() set to include only one of these two.
                        """.trimIndent().trim()

                    // We've already checked that the output does not match. Now include
                    // the mode labels in the assertion (which will fail) to clearly label
                    // the junit diff explaining which output is which.
                    assertEquals(
                        message,
                        "$expectedLabel:\n\n$expected",
                        "$actualLabel:\n\n$actual"
                    )
                }
                prev = mode
            }
        }
    }

    /**
     * Given a result string possibly containing absolute paths
     * to the given directory, replaces the directory prefixes
     * with `TESTROOT`, and optionally (if configured via
     * TestLinkTask.stripRoot) makes the path relative to the test root.
     */
    fun stripRoot(rootDir: File, path: String): String {
        var s = path
        var rootPath = rootDir.path
        if (s.contains(rootPath)) {
            s = s.replace(rootPath, "TESTROOT")
        }
        rootPath = rootPath.replace(File.separatorChar, '/')
        if (s.contains(rootPath)) {
            s = s.replace(rootPath, "/TESTROOT")
        }
        if (task.stripRoot && s.contains("TESTROOT")) {
            s = s
                .replace("/TESTROOT/", "")
                .replace("/TESTROOT\\", "")
                .replace("\nTESTROOT/", "\n")
            if (s.startsWith("TESTROOT/")) {
                s = s.substring("TESTROOT/".length)
            }
        }
        return s
    }

    fun createClient(): TestLintClient {
        val client: TestLintClient
        with(task) {
            client = if (clientFactory != null) {
                clientFactory.create()
            } else {
                ensureClientNameInitialized()
                val clientName = clientName
                try {
                    TestLintClient()
                } finally {
                    if (clientName != LintClient.CLIENT_UNKNOWN) {
                        LintClient.clientName = clientName
                    }
                }
            }
            if (!useTestConfiguration && overrideConfigFile != null) {
                val configurations = client.configurations
                if (configurations.overrides == null) {
                    val config: Configuration = create(configurations, overrideConfigFile)
                    configurations.addGlobalConfigurations(null, config)
                }
            }
            client.task = this

            if (!client.pathVariables.any()) { // otherwise test is responsible on its own
                client.pathVariables.add("TEST_ROOT", tempDir, false)
                rootDirectory?.let { client.pathVariables.add("ROOT", it) }
                client.pathVariables.normalize()
            }

            return client
        }
    }

    /**
     * Creates lint test projects according to the configured project
     * descriptions. Note that these are not the same projects that will
     * be used if the [.run] method is called. This method is intended
     * mainly for testing the lint infrastructure itself. Most detector
     * tests will just want to use [.run].
     *
     * @param keepFiles if true, don't delete the generated temporary
     *     project source files
     */
    fun createProjects(keepFiles: Boolean): List<Project> {
        var rootDir = Files.createTempDir()
        try {
            // Use canonical path to make sure we don't end up failing
            // to chop off the prefix from Project#getDisplayPath
            rootDir = rootDir.canonicalFile
        } catch (ignore: IOException) {
        }
        val projectDirs: List<File> = createProjects(rootDir)
        val lintClient = createClient()
        lintClient.setLintTask(task)
        return try {
            val projects: MutableList<Project> = Lists.newArrayList()
            for (dir in projectDirs) {
                projects.add(lintClient.getProject(dir, rootDir))
            }
            projects
        } finally {
            lintClient.setLintTask(task)
            if (!keepFiles) {
                TestFile.deleteFilesRecursively(rootDir)
            }
        }
    }

    /** Constructs the actual lint projects on disk. */
    fun createProjects(rootDir: File): List<File> {
        val projectDirs: MutableList<File> = Lists.newArrayList()
        with(task) {
            dirToProjectDescription.clear()
            projectMocks.clear()
            projects.assignProjectNames()
            projects.expandProjects()

            if (task.reportFrom == null) {
                projects.implicitReportFrom?.let { task.reportFrom(it) }
            }

            // Pick a report-from project to ensure the analysis relative to something
            if (task.reportFrom == null) {
                val app = projects.firstOrNull { it.type == ProjectDescription.Type.APP }
                    ?: projects.firstOrNull()
                app?.let { task.reportFrom(it) }
            }

            // Sort into dependency order such that dependencies are always listed before
            // their dependents. Stable order is also useful for stable test output.
            projects.sort()
            val allProjects: List<ProjectDescription> = projects.projects

            // If more than one project is primary, prioritize the app.
            // This is used to control which gradle mock controls the CLI flags.
            var primaryCount = 0
            var app: ProjectDescription? = null
            for (project in allProjects) {
                if (project.primary) {
                    primaryCount++
                }
                if (project.type === ProjectDescription.Type.APP) {
                    app = project
                }
            }
            if (primaryCount != 1) {
                if (app == null) {
                    app = allProjects[allProjects.size - 1]
                }
                for (project in allProjects) {
                    project.primary = false
                }
                app.primary = true
            }

            // First create project directories (before populating them) since
            // the directories need to exist to make relative path computations
            // between the projects (used for example for dependencies) work
            // properly.
            for (project in allProjects) {
                try {
                    project.ensureUnique()
                    val projectDir = ProjectDescription.getProjectDirectory(project, rootDir)
                    dirToProjectDescription[projectDir] = project
                    if (!projectDir.isDirectory) {
                        val ok = projectDir.mkdirs()
                        Assert.assertTrue("Couldn't create projectDir $projectDir", ok)
                    }
                    projectDirs.add(projectDir)
                } catch (e: Exception) {
                    throw java.lang.RuntimeException(e.message, e)
                }
            }

            // Populate project directories with source files
            for (project in allProjects) {
                try {
                    var files = project.files
                    val projectDir = ProjectDescription.getProjectDirectory(project, rootDir)

                    // Also create dependency files
                    if (project.dependsOn.isNotEmpty()) {
                        var propertyFile: TestFile.PropertyTestFile? = null
                        for (file in files) {
                            if (file is TestFile.PropertyTestFile) {
                                propertyFile = file
                                break
                            }
                        }
                        if (propertyFile == null) {
                            propertyFile = TestFiles.projectProperties()
                            files = ObjectArrays.concat(files, propertyFile)
                        }
                        var index = 1
                        for (dependency in project.dependsOn) {
                            val dependencyDir =
                                ProjectDescription.getProjectDirectory(dependency, rootDir)
                            val client = TestLintClient(clientName)
                            val relative = client.getRelativePath(projectDir, dependencyDir)
                            val referenceKey = "android.library.reference." + index++
                            propertyFile.property(referenceKey, relative)
                        }
                    }
                    populateProjectDirectory(project, projectDir, *files)
                    if (baseline != null) {
                        baselineFile = baseline.createFile(projectDir)
                    }
                    if (overrideConfig != null) {
                        overrideConfigFile = overrideConfig.createFile(projectDir)
                    }
                } catch (e: Exception) {
                    throw java.lang.RuntimeException(e.message, e)
                }
            }
        }
        return projectDirs
    }
}
