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

import com.google.common.collect.Lists
import org.junit.Assert
import java.util.ArrayList
import java.util.HashMap
import java.util.HashSet

/**
 * A list of project descriptors for a project to be created and
 * analyzed with Lint.
 */
internal class ProjectDescriptionList(
    /**
     * Initial set of project descriptors; may have dependencies on
     * projects not included in this list, or contain implicit projects
     * inferred from relative test file paths.
     */
    var projects: MutableList<ProjectDescription> = mutableListOf(),

    /**
     * If not null, the project to consider the "base" to report from.
     * E.g. if you have projects "app" and "lib" and you get warnings
     * from both, by default (with [reportFrom] null) the errors in the
     * report will show paths like `app/src/main` and `lib/src/main`.
     * If [reportFrom] is set to the app, the paths will instead be
     * `src/main/` and `../lib/src/main`.
     */
    var reportFrom: ProjectDescription? = null
) : Iterable<ProjectDescription> {
    /** Original number of projects in the list. */
    val originalSize = projects.size

    /**
     * If the project set was constructed implicitly (via
     * ../module/path) file names, this property will point to the
     * implicit "main" (or app) project.
     */
    var implicitReportFrom: ProjectDescription? = null
        private set

    /** Number of projects in the list. */
    val size: Int get() = projects.size

    /** Whether the project list is empty. */
    fun isEmpty(): Boolean = projects.isEmpty()

    /** Return the [index]th project in the list. */
    operator fun get(index: Int): ProjectDescription = projects[index]

    /**
     * Adds the given [project], if not null, to the project list unless
     * it's already there.
     */
    fun addProject(project: ProjectDescription?) {
        if (project != null && !projects.contains(project)) {
            projects.add(project)
        }
    }

    /**
     * Adds all projects reachable via dependencies into the target
     * list.
     */
    fun addProjects(projects: List<ProjectDescription>) {
        for (project in projects) {
            addProject(project)
            addProjects(project.dependsOn)
        }
    }

    /**
     * The project list is allowed to just contain root projects
     * (which depend on other projects), and even implicit projects:
     * You can place relative paths (../library/...) in test files to
     * easily configure multiple test modules. This method will use
     * this to split up the test file list into multiple projects.
     * The names will be used to infer relative dependencies. For
     * example, if you point to "../app/" then the assumption
     * is that the created project has app type and depends on
     * the current module, whereas if you create ../library or
     * ../lib-something then the assumption is that the created project
     * is a library and is depended upon by the current project.
     */
    fun expandProjects() {
        val allProjects: MutableList<ProjectDescription> = ArrayList(projects)
        val nameMap: MutableMap<String, ProjectDescription> = HashMap()
        for (project in allProjects) {
            nameMap[project.name] = project
        }
        val added: MutableList<ProjectDescription> = ArrayList()
        for (project in allProjects) {
            val files: Array<out TestFile> = project.files
            val filtered: MutableList<TestFile> = ArrayList()
            for (file in files) {
                val path = file.targetRelativePath
                if (path.startsWith("../") && path.indexOf('/', 3) != -1) {
                    val name = path.substring(3, path.indexOf('/', 3))
                    var newProject = nameMap[name]
                    if (newProject == null) {
                        newProject = ProjectDescription()
                        newProject.name = name
                        nameMap[name] = newProject
                        newProject.primary = false
                        added.add(newProject)
                        if (name.startsWith("lib") || name.startsWith("Lib")) {
                            project.dependsOn(newProject)
                            newProject.type = ProjectDescription.Type.LIBRARY
                            implicitReportFrom = project
                        } else {
                            // Projects not named "lib" something are assumed to be
                            // consuming projects depending on this project (and the
                            // dependency project is assumed to be a library)
                            newProject.dependsOn(project)
                            newProject.type = ProjectDescription.Type.APP
                            implicitReportFrom = newProject
                            if (project.name.isEmpty()) {
                                project.type = ProjectDescription.Type.LIBRARY
                                pickUniqueName(getProjectNames(), project)
                            }
                        }
                    }
                    if (reportFrom == null) {
                        reportFrom = if (name.startsWith("app") || name.startsWith("main")) {
                            newProject
                        } else {
                            project
                        }
                    }
                    // move the test file over and update target path
                    newProject.files = Lists.asList(file, newProject.files).toTypedArray()
                    file.targetRelativePath = file.targetRelativePath.substring(name.length + 4)
                    if (file is CompiledSourceFile && file.source.targetRelativePath.startsWith("../$name/")) {
                        file.source.targetRelativePath = file.source.targetRelativePath.substring(name.length + 4)
                    }
                } else {
                    filtered.add(file)
                }
            }
            project.files = filtered.toTypedArray()
        }
        allProjects.addAll(added)
        projects = allProjects
        // Dependencies are sometimes just recorded by name instead of project
        // reference; resolve these now
        addNamedDependencies()
    }

    /**
     * Returns all the project names. Note that this only includes
     * the projects explicitly listed in the project list, so if not
     * intended, call [expandProjects] first.
     */
    fun getProjectNames(): Set<String> {
        val names: MutableSet<String> = HashSet()
        for (project in projects) {
            val projectName = project.name
            if (projectName.isNotEmpty()) {
                names.add(projectName)
            }
            for (file in project.files) {
                val path = file.targetRelativePath
                if (path.startsWith("../") &&
                    path.indexOf('/', 3) != -1
                ) {
                    // Bingo
                    val name = path.substring(3, path.indexOf('/', 3))
                    names.add(name)
                }
            }
        }
        return names
    }

    /**
     * Assigns unique names to the given project that have not been
     * explicitly named. It's okay to call this repeatedly since the
     * project set can change over time (as we add in provisional test
     * projects etc); it will only touch unnamed projects, and will not
     * clash with any existing project names.
     */
    fun assignProjectNames() {
        val usedNames: MutableSet<String> = HashSet(getProjectNames())
        for (project in projects) {
            if (project.name.isEmpty()) {
                val name = pickUniqueName(usedNames, project)
                usedNames.add(name)
                project.name = name
            }
        }
    }

    /**
     * Assigns a unique name to the given project if it has not already
     * been named.
     */
    fun assignProjectName(project: ProjectDescription) {
        val usedNames: Set<String> = HashSet(getProjectNames())
        if (project.name.isEmpty()) {
            val name = pickUniqueName(usedNames, project)
            project.name = name
        }
    }

    /**
     * Finds a unique name for the given project, not conflicting with
     * any of the existing names passed in.
     */
    private fun pickUniqueName(usedNames: Set<String>, project: ProjectDescription): String {
        val root = when (project.type) {
            ProjectDescription.Type.APP -> "app"
            ProjectDescription.Type.LIBRARY -> "lib"
            ProjectDescription.Type.JAVA -> "javalib"
        }
        if (!usedNames.contains(root)) {
            return root
        }
        var index = 2
        while (true) {
            val name = root + index++
            if (!usedNames.contains(name)) {
                return name
            }
        }
    }

    private fun addNamedDependencies() {
        val nameMap: MutableMap<String, ProjectDescription> = HashMap()
        for (project in projects) {
            nameMap[project.name] = project
        }
        for (project in projects) {
            for (name in project.dependsOnNames) {
                val dependency = nameMap[name]
                if (dependency == null) {
                    Assert.fail("Unknown named project " + name + " from " + project.name)
                } else {
                    project.dependsOn(dependency)
                }
            }
        }
    }

    /** Sort the projects (into dependency order, then alphabetical) */
    fun sort() {
        projects.sort()
    }

    override fun iterator(): Iterator<ProjectDescription> = projects.iterator()
}
