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

import com.google.common.collect.Maps
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.io.File
import java.util.HashMap

/**
 * Abstract class used to compare two configurations, and to report differences in versions of
 * artifacts. This is useful to warn user about potential issues that could arise from such
 * differences. E.g. for application, differences in runtime and compile classpath could result in
 * runtime failure.
 */
abstract class ClasspathComparisonTask : NonIncrementalTask() {

    @get:Internal
    protected lateinit var runtimeClasspath: ArtifactCollection
    @get:Internal
    protected lateinit var compileClasspath: ArtifactCollection
    // fake output dir so that the task doesn't run unless an input has changed.
    @get:OutputDirectory
    var fakeOutputDirectory: File? = null
        protected set

    // even though the files are jars, we don't care about changes to the files, only if files
    // are removed or added. We need to find a better way to declare this.
    @get:CompileClasspath
    @get:PathSensitive(PathSensitivity.NONE)
    val runtimeClasspathFC: FileCollection
        get() = runtimeClasspath.artifactFiles

    // even though the files are jars, we don't care about changes to the files, only if files
    // are removed or added. We need to find a better way to declare this.
    @get:CompileClasspath
    @get:PathSensitive(PathSensitivity.NONE)
    val compileClasspathFC: FileCollection
        get() = compileClasspath.artifactFiles

    protected abstract fun onDifferentVersionsFound(
        group: String,
        module: String,
        runtimeVersion: String,
        compileVersion: String
    )

    override fun doTaskAction() {
        val runtimeArtifacts = runtimeClasspath.artifacts
        val compileArtifacts = compileClasspath.artifacts

        // Store a map of groupId -> (artifactId -> versions)
        val runtimeIds =
            Maps.newHashMapWithExpectedSize<String, MutableMap<String, String>>(runtimeArtifacts.size)

        for (artifact in runtimeArtifacts) {
            // only care about external dependencies to compare versions.
            val componentIdentifier = artifact.id.componentIdentifier
            if (componentIdentifier is ModuleComponentIdentifier) {

                // get the sub-map, creating it if needed.
                val subMap = runtimeIds.computeIfAbsent(componentIdentifier.group) {  HashMap() }

                subMap[componentIdentifier.module] = componentIdentifier.version
            }
        }

        for (artifact in compileArtifacts) {
            // only care about external dependencies to compare versions.
            val componentIdentifier = artifact.id.componentIdentifier
            if (componentIdentifier is ModuleComponentIdentifier) {

                val subMap = runtimeIds[componentIdentifier.group] ?: continue

                val runtimeVersion = subMap[componentIdentifier.module] ?: continue

                if (runtimeVersion == componentIdentifier.version) {
                    continue
                }

                onDifferentVersionsFound(
                    componentIdentifier.group,
                    componentIdentifier.module,
                    runtimeVersion,
                    componentIdentifier.version
                )
            }
        }
    }
}
