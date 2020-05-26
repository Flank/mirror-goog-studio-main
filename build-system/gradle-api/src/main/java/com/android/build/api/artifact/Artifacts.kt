/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.api.artifact

import com.android.build.api.variant.BuiltArtifactsLoader
import org.gradle.api.Incubating
import org.gradle.api.Task
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider

/**
 * Access to the artifacts on a Variant object.
 *
 * Artifacts are temporary or final files or directories that are produced by the Android Gradle
 * Plugin during the build. Depending on its configuration, each [com.android.build.api.variant.Variant]
 * produces different versions of some of the output artifacts.
 *
 * Example of temporary artifacts are .class files obtained from compiling source files that will
 * eventually get transformed further into dex files. Final artifacts are APKs and bundle files that
 * are not transformed further.
 *
 * Artifacts are uniquely defined by their [Artifact] type and public artifact types that can be
 * accessed from third party plugins or build script are defined in [ArtifactType]
 *
 * @since 4.1
 */
@Incubating
interface Artifacts {

    /**
     * Provides an implementation of [BuiltArtifactsLoader] that can be used to load built artifacts
     * metadata.
     *
     * @return a thread safe implementation pf [BuiltArtifactsLoader] that can be reused.
     */
    fun getBuiltArtifactsLoader(): BuiltArtifactsLoader

    /**
     * Get the [Provider] of [FileTypeT] for the passed [Artifact].
     *
     * The [Artifact] must be of the [FileTypeT] and [Artifact.Single]
     */
    fun <FileTypeT: FileSystemLocation, ArtifactTypeT> get(type: ArtifactTypeT): Provider<FileTypeT>
            where ArtifactTypeT: ArtifactType<out FileTypeT>, ArtifactTypeT: Artifact.Single

    /**
     * Get all the [Provider]s of [FileTypeT] for the passed [Artifact].
     *
     * The [Artifact] must be [Artifact.Multiple]
     */
    fun <FileTypeT: FileSystemLocation, ArtifactTypeT> getAll(type: ArtifactTypeT): Provider<List<FileTypeT>>
            where ArtifactTypeT : ArtifactType<FileTypeT>, ArtifactTypeT : Artifact.Multiple


    /**
     * Access [Task] based operations.
     *
     * @param taskProvider the [TaskProvider] for the [TaskT] that will be producing and or
     * consuming artifact types.
     * @return a [TaskBasedOperations] using the passed [TaskProvider] for all its operations
     */
    fun <TaskT: Task> use(taskProvider: TaskProvider<TaskT>): TaskBasedOperations<TaskT>
}