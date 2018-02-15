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

package com.android.build.gradle.internal.tasks

import com.android.annotations.NonNull
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.build.gradle.internal.scope.VariantScope
import com.google.common.io.Files
import org.apache.commons.io.Charsets
import org.gradle.api.GradleException
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Task to check no two features bundle the same library.
 */
open class CheckInstantAppLibrariesTask : AndroidVariantTask() {

    private lateinit var libraryFilters : ArtifactCollection

    @InputFiles
    @NonNull
    @PathSensitive(PathSensitivity.RELATIVE)
    fun getLibraryFilters() : FileCollection = libraryFilters.artifactFiles

    @TaskAction
    fun taskAction() {
        // Build a map of library to their corresponding feature project list. If two features
        // package the same library, we will use the map to output a user-friendly error message.
        val map = mutableMapOf<String, MutableList<String>>()
        var found = false
        for (artifact in libraryFilters) {
            // Sanity check. This should never happen.
            if (artifact.id.componentIdentifier !is ProjectComponentIdentifier) {
                throw GradleException(
                        artifact.id.componentIdentifier.displayName + " is not a Gradle project.")
            }

            val project =
                    (artifact.id.componentIdentifier as ProjectComponentIdentifier).projectPath
            if (artifact.file.isFile) {
                for (library in Files.readLines(artifact.file, Charsets.UTF_8)) {
                    if (map.containsKey(library)) {
                        found = true
                        map[library]?.add(project)
                    } else {
                        map[library] = mutableListOf(project)
                    }
                }
            }
        }

        if (found) {
            // Build the error message.
            val output = StringBuilder()
            for ((library, projects) in map) {
                if (projects.size > 1) {
                    output
                        .append("Features ")
                        .append(
                                projects.joinToString(
                                        prefix = "[",
                                        separator = ", ",
                                        postfix = "]"))
                        .append(" all package the same library [")
                        .append(library)
                        .append("].\n")
                }
            }
            throw GradleException(output.toString())
        }
    }

    class ConfigAction(val scope: VariantScope):
            TaskConfigAction<CheckInstantAppLibrariesTask> {

        override fun getName() = scope.getTaskName("check", "Libraries")

        override fun getType() = CheckInstantAppLibrariesTask::class.java

        override fun execute(task: CheckInstantAppLibrariesTask) {
            task.variantName = scope.fullVariantName
            task.libraryFilters =
                    scope.getArtifactCollection(
                            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.MODULE,
                            AndroidArtifacts.ArtifactType.FEATURE_TRANSITIVE_DEPS)
        }
    }
}