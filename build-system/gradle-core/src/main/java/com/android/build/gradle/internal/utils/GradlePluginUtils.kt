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
import org.gradle.api.initialization.dsl.ScriptHandler.CLASSPATH_CONFIGURATION

private const val INTERNAL__CHECKED_MINIMUM_PLUGIN_VERSIONS =
    "INTERNAL__CHECKED_MINIMUM_PLUGIN_VERSIONS"

// See https://issuetracker.google.com/79997489
private const val CRASHLYTICS_PLUGIN_NAME = "Crashlytics"
private const val CRASHLYTICS_PLUGIN_DEPENDENCY_GROUP_AND_NAME = "io.fabric.tools:gradle"
private const val CRASHLYTICS_PLUGIN_MINIMUM_VERSION = "1.25.4"

// See https://issuetracker.google.com/110564407
private const val PROTOBUF_PLUGIN_NAME = "Protobuf"
private const val PROTOBUF_PLUGIN_DEPENDENCY_GROUP_AND_NAME =
    "com.google.protobuf:protobuf-gradle-plugin"
private const val PROTOBUF_PLUGIN_MINIMUM_VERSION = "0.8.6"

// See https://issuetracker.google.com/69243050
private const val KOTLIN_PLUGIN_NAME = "Kotlin"
private const val KOTLIN_PLUGIN_DEPENDENCY_GROUP_AND_NAME =
    "org.jetbrains.kotlin:kotlin-gradle-plugin"
private const val KOTLIN_PLUGIN_MINIMUM_VERSION = "1.2.50"

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
            enforceMinimumVersionOfPlugin(
                it,
                issueReporter,
                CRASHLYTICS_PLUGIN_NAME,
                CRASHLYTICS_PLUGIN_DEPENDENCY_GROUP_AND_NAME,
                CRASHLYTICS_PLUGIN_MINIMUM_VERSION
            )
            enforceMinimumVersionOfPlugin(
                it,
                issueReporter,
                PROTOBUF_PLUGIN_NAME,
                PROTOBUF_PLUGIN_DEPENDENCY_GROUP_AND_NAME,
                PROTOBUF_PLUGIN_MINIMUM_VERSION
            )
            enforceMinimumVersionOfPlugin(
                it,
                issueReporter,
                KOTLIN_PLUGIN_NAME,
                KOTLIN_PLUGIN_DEPENDENCY_GROUP_AND_NAME,
                KOTLIN_PLUGIN_MINIMUM_VERSION
            )
        }
    }
}

private fun enforceMinimumVersionOfPlugin(
    project: Project,
    issueReporter: EvalIssueReporter,
    pluginName: String,
    dependencyGroupAndName: String,
    minimumVersion: String
) {
    // Use 'continue' to avoid too many nesting levels in this loop.
    for (artifact in project.buildscript.configurations.getByName(CLASSPATH_CONFIGURATION)
        .resolvedConfiguration.resolvedArtifacts) {
        val artifactId = artifact.moduleVersion.id
        if ("${artifactId.group}:${artifactId.name}" != dependencyGroupAndName) {
            continue
        }
        // Use GradleVersion to parse the version since the format accepted by GradleVersion is
        // general enough. However, in the unlikely event that the version cannot be parsed, let's
        // be lenient and ignore the check.
        val currentVersion = GradleVersion.tryParse(artifactId.version) ?: continue
        if (currentVersion >= minimumVersion) {
            continue
        }
        issueReporter.reportError(
            EvalIssueReporter.Type.THIRD_PARTY_GRADLE_PLUGIN_TOO_OLD,
            EvalIssueException(
                "The Android Gradle plugin supports only $pluginName Gradle plugin" +
                        " version $minimumVersion and higher." +
                        " Project '${project.name}' is using version $currentVersion.",
                project.projectDir.path,
                null
            )
        )
    }
}