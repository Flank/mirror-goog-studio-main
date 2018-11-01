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

@file:JvmName("GradlePluginUtils")

package com.android.build.gradle.internal.utils

import com.android.builder.errors.EvalIssueException
import com.android.builder.errors.EvalIssueReporter
import com.android.ide.common.repository.GradleVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.initialization.dsl.ScriptHandler.CLASSPATH_CONFIGURATION

private const val INTERNAL__CHECKED_MINIMUM_PLUGIN_VERSIONS =
    "INTERNAL__CHECKED_MINIMUM_PLUGIN_VERSIONS"

private val pluginList = listOf(
    // https://issuetracker.google.com/79997489
    DependencyInfo(
        "Crashlytics",
        "io.fabric.tools",
        "gradle",
        GradleVersion.parse("1.25.4")
    ),

    // https://issuetracker.google.com/110564407
    DependencyInfo(
        "Protobuf",
        "com.google.protobuf",
        "protobuf-gradle-plugin",
        GradleVersion.parse("0.8.6")
    ),

    // https://issuetracker.google.com/118644551
    DependencyInfo(
        "Kotlin",
        "org.jetbrains.kotlin",
        "kotlin-gradle-plugin",
        GradleVersion.parse("1.3.0")
    )
)

private data class DependencyInfo(
    val displayName: String,
    val dependencyGroup: String,
    val dependencyName: String,
    val minimumVersion: GradleVersion
)

/**
 * Enforces minimum versions of certain plugins.
 */
fun enforceMinimumVersionsOfPlugins(project: Project, issueReporter: EvalIssueReporter) {
    // We're going to check all projects at the end of the configuration phase, so make sure to do
    // this check only once by marking a custom property of the root project. The access doesn't
    // need to be thread-safe as configuration is single-threaded.
    val alreadyChecked =
        project.rootProject.extensions.extraProperties.has(
            INTERNAL__CHECKED_MINIMUM_PLUGIN_VERSIONS
        )
    if (alreadyChecked) {
        return
    }
    project.rootProject.extensions.extraProperties.set(
        INTERNAL__CHECKED_MINIMUM_PLUGIN_VERSIONS,
        true
    )

    project.gradle.projectsEvaluated { gradle ->
        gradle.allprojects {
            for (plugin in pluginList) {
                enforceMinimumVersionOfPlugin(it, plugin, issueReporter)
            }
        }
    }
}

private fun enforceMinimumVersionOfPlugin(
    project: Project,
    pluginInfo: DependencyInfo,
    issueReporter: EvalIssueReporter
) {
    // Traverse the dependency graph to collect violating plugins
    val buildScriptClasspath = project.buildscript.configurations.getByName(CLASSPATH_CONFIGURATION)
    val pathsToViolatingPlugins = mutableListOf<String>()
    for (dependency in buildScriptClasspath.incoming.resolutionResult.root.dependencies) {
        visitDependency(
            dependency as ResolvedDependencyResult,
            project.displayName,
            pluginInfo,
            pathsToViolatingPlugins,
            mutableSetOf()
        )
    }

    // Report violating plugins
    if (pathsToViolatingPlugins.isNotEmpty()) {
        issueReporter.reportError(
            EvalIssueReporter.Type.THIRD_PARTY_GRADLE_PLUGIN_TOO_OLD,
            EvalIssueException(
                "The Android Gradle plugin supports only ${pluginInfo.displayName} Gradle plugin" +
                        " version ${pluginInfo.minimumVersion} and higher.\n" +
                        "The following dependencies do not satisfy the required version:\n" +
                        pathsToViolatingPlugins.joinToString("\n"),
                listOf(
                    pluginInfo.displayName,
                    pluginInfo.dependencyGroup,
                    pluginInfo.dependencyName,
                    pluginInfo.minimumVersion,
                    pathsToViolatingPlugins.joinToString(",", "[", "]")
                ).joinToString(";"),
                null
            )
        )
    }
}

private fun visitDependency(
    dependency: ResolvedDependencyResult,
    parentPath: String,
    dependencyInfo: DependencyInfo,
    pathsToViolatingDeps: MutableList<String>,
    visitedDependencies: MutableSet<String>
) {
    val fullName = dependency.selected.moduleVersion!!
    val group = fullName.module.group
    val name = fullName.module.name
    val selectedVersion = fullName.version

    // The selected version may be different than the requested version
    val requestedVersion = (dependency.requested as ModuleComponentSelector).version

    val requestedToSelectedVersion =
        if (requestedVersion == selectedVersion) selectedVersion
        else "$requestedVersion->$selectedVersion"
    val currentPath = "$parentPath -> $group:$name:$requestedToSelectedVersion"

    // Detect violating dependencies
    if (group == dependencyInfo.dependencyGroup && name == dependencyInfo.dependencyName) {
        // Use GradleVersion to parse the version since the format accepted by GradleVersion is
        // general enough. In the unlikely event that the version cannot be parsed (the return
        // result is null), let's be lenient and ignore the error.
        val parsedSelectedVersion = GradleVersion.tryParse(selectedVersion)
        if (parsedSelectedVersion != null
            && parsedSelectedVersion < dependencyInfo.minimumVersion
        ) {
            pathsToViolatingDeps.add(currentPath)
        }
    }

    // Don't visit a dependency twice (except for the dependency being searched, that's why this
    // check should be after the detection above)
    val dependencyFullName = "$group:$name:$selectedVersion"
    if (visitedDependencies.contains(dependencyFullName)) {
        return
    }
    visitedDependencies.add(dependencyFullName)

    for (childDependency in dependency.selected.dependencies) {
        visitDependency(
            childDependency as ResolvedDependencyResult,
            currentPath,
            dependencyInfo,
            pathsToViolatingDeps,
            visitedDependencies
        )
    }
}