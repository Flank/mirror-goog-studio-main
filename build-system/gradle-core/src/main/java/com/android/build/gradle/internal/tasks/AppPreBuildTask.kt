/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.MANIFEST
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.NON_NAMESPACED_MANIFEST
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH
import com.android.build.gradle.internal.scope.VariantScope
import com.google.common.collect.Maps
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.internal.component.local.model.OpaqueComponentArtifactIdentifier
import java.io.File

/** Pre build task that does some checks for application variants  */
@CacheableTask
open class AppPreBuildTask : NonIncrementalTask() {

    // list of Android only compile and runtime classpath.
    private lateinit var compileManifests: ArtifactCollection
    private lateinit var compileNonNamespacedManifests: ArtifactCollection
    private lateinit var runtimeManifests: ArtifactCollection
    private lateinit var runtimeNonNamespacedManifests: ArtifactCollection

    @get:OutputDirectory
    lateinit var fakeOutputDirectory: File
        private set

    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    fun getCompileManifests(): FileCollection {
        return compileManifests.artifactFiles
    }

    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    fun getCompileNonNamespacedManifests(): FileCollection {
        return compileNonNamespacedManifests.artifactFiles
    }

    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    fun getRuntimeManifests(): FileCollection {
        return runtimeManifests.artifactFiles
    }

    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    fun getRuntimeNonNamespacedManifests(): FileCollection {
        return runtimeNonNamespacedManifests.artifactFiles
    }

    override fun doTaskAction() {
        val compileArtifacts = mutableSetOf<ResolvedArtifactResult>()
        compileArtifacts.addAll(compileManifests.artifacts)
        compileArtifacts.addAll(compileNonNamespacedManifests.artifacts)

        val runtimeArtifacts = mutableSetOf<ResolvedArtifactResult>()
        runtimeArtifacts.addAll(runtimeManifests.artifacts)
        runtimeArtifacts.addAll(runtimeNonNamespacedManifests.artifacts)

        // create a map where the key is either the sub-project path, or groupId:artifactId for
        // external dependencies.
        // For external libraries, the value is the version.
        val runtimeIds = Maps.newHashMapWithExpectedSize<String, String>(runtimeArtifacts.size)

        // build a list of the runtime artifacts
        for (artifact in runtimeArtifacts) {
            handleArtifact(artifact.id.componentIdentifier) { key, value ->
                runtimeIds[key] = value
            }
        }

        // run through the compile ones to check for provided only.
        for (artifact in compileArtifacts) {
            val compileId = artifact.id.componentIdentifier
            handleArtifact(
                compileId
            ) { key, _ ->
                runtimeIds[key] ?: throw RuntimeException(
                    "Android dependency '${compileId.displayName}' is set to compileOnly/provided which is not supported"
                )
            }
        }
    }

    private fun handleArtifact(
        id: ComponentIdentifier,
        handler: (String, String) -> Unit
    ) {
        when (id) {
            is ProjectComponentIdentifier -> handler(id.projectPath, "")
            is ModuleComponentIdentifier -> handler("${id.group}:${id.module}", id.version)
            is OpaqueComponentArtifactIdentifier -> {
                // skip those for now.
                // These are file-based dependencies and it's unlikely to be an AAR.
            }
            else -> logger
                .warn(
                    "Unknown ComponentIdentifier type: ${id.javaClass.canonicalName}."
                )
        }
    }

    private class EmptyCreationAction(variantScope: VariantScope) :
        TaskManager.AbstractPreBuildCreationAction<DefaultTask>(variantScope) {

        override val type: Class<DefaultTask>
            get() = DefaultTask::class.java
    }

    private class CheckCreationAction(variantScope: VariantScope) :
        TaskManager.AbstractPreBuildCreationAction<AppPreBuildTask>(variantScope) {

        override val type: Class<AppPreBuildTask>
            get() = AppPreBuildTask::class.java

        override fun configure(task: AppPreBuildTask) {
            super.configure(task)
            task.variantName = variantScope.fullVariantName

            task.compileManifests =
                variantScope.getArtifactCollection(COMPILE_CLASSPATH, ALL, MANIFEST)
            task.compileNonNamespacedManifests = variantScope.getArtifactCollection(
                COMPILE_CLASSPATH, ALL, NON_NAMESPACED_MANIFEST
            )
            task.runtimeManifests =
                variantScope.getArtifactCollection(RUNTIME_CLASSPATH, ALL, MANIFEST)
            task.runtimeNonNamespacedManifests = variantScope.getArtifactCollection(
                RUNTIME_CLASSPATH, ALL, NON_NAMESPACED_MANIFEST
            )

            task.fakeOutputDirectory = File(
                variantScope.globalScope.intermediatesDir,
                "prebuild/${variantScope.variantConfiguration.dirName}"
            )

        }
    }

    companion object {
        @JvmStatic
        fun getCreationAction(
            variantScope: VariantScope
        ): TaskManager.AbstractPreBuildCreationAction<*> {
            return if (variantScope.type.isBaseModule && variantScope.globalScope.hasDynamicFeatures()) {
                CheckCreationAction(variantScope)
            } else EmptyCreationAction(variantScope)

        }
    }
}
