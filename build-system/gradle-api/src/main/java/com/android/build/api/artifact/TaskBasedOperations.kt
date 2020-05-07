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

package com.android.build.api.artifact

import com.android.build.api.variant.BuiltArtifact
import com.android.build.api.variant.BuiltArtifacts
import org.gradle.api.Incubating
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.file.FileSystemLocationProperty
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkQueue
import java.io.File
import java.io.Serializable
import java.util.function.Supplier

/**
 * Operations that requires a [org.gradle.api.tasks.TaskProvider] of [TaskT] to be performed.
 */
@Incubating
interface TaskBasedOperations<TaskT: Task> {

    /**
     * Construct a new operation request where the [TaskT] expects to read the provided
     * [ArtifactTypeT] built artifacts from the location specified by the [at] parameter.
     *
     *
     * @param type the artifact type the [TaskT] is interested in.
     * @param at the location at which the [TaskT] will be reading the built artifacts from.
     */
    fun <ArtifactTypeT> toRead(type: ArtifactTypeT, at: (TaskT) -> FileSystemLocationProperty<Directory>): TaskBasedOperations<TaskT>
            where ArtifactTypeT: ArtifactType<Directory>, ArtifactTypeT: ArtifactType.ContainsMany, ArtifactTypeT: ArtifactType.Single

    /**
     * Completes a [TaskBasedOperations] by specifying that [TaskT] will be producing [BuiltArtifact]
     * of type [ArtifactTypeT] at the location identified by [at].
     *
     * @param type the artifact type the [TaskT] is producing
     * @param at the location it produces these artifacts in.
     */
    fun <ArtifactTypeT> andWrite(type: ArtifactTypeT, at: (TaskT) -> FileSystemLocationProperty<Directory>): ArtifactTransformationRequest
        where ArtifactTypeT : ArtifactType<Directory>, ArtifactTypeT : ArtifactType.ContainsMany, ArtifactTypeT: ArtifactType.Single

    /**
     * Completes a [TaskBasedOperations] by specifying that [TaskT] will be producing [BuiltArtifact]
     * of type [ArtifactTypeT] at the location identified by [at].
     *
     * @param type the artifact type the [TaskT] is producing
     * @param at the task's Property to produce these artifacts in.
     * @param atLocation the file system location to put these artifacts.
     */
    // TODO : consider moving both andWrite to a separate interface to support atLocation, withName...
    fun <ArtifactTypeT> andWrite(type: ArtifactTypeT, at: (TaskT) -> FileSystemLocationProperty<Directory>, atLocation: String): ArtifactTransformationRequest
            where ArtifactTypeT : ArtifactType<Directory>, ArtifactTypeT : ArtifactType.ContainsMany, ArtifactTypeT: ArtifactType.Single
}

/**
 * Denotes a completed transformation request where a [Task] will be transforming an incoming
 * [ArtifactType] into an outgoing [ArtifactType].
 *
 * The implementation will take care of reading the source built artifacts from the input location
 * provided through the [TaskBasedOperations.toRead] method.
 *
 * The [Task] can then use one of the [submit] methods to provide a Gradle's `org.gradle.workers`
 * style of [WorkAction] to process each input [BuiltArtifact].
 * Alternatively, the [Task] can use the simpler synchronous lambda to process each input
 * [BuiltArtifact]
 *
 * Once the [Task] execution finishes, the output directory will be written using the expected
 * metadata format and consumers will be unlocked.
 */
@Incubating
interface ArtifactTransformationRequest {

    /**
     * Submit a `org.gradle.workers` style of [WorkAction] to process each input [BuiltArtifact]
     *
     * @param task : the Task initiating the [WorkQueue] requests.
     * @param workQueue the Gradle [WorkQueue] instance to use to spawn worker items with.
     * @param actionType the type of the [WorkAction] subclass that process that input [BuiltArtifact]
     * @param parameterType the type of parameters expected by the [WorkAction]
     * @param parameterConfigurator the lambda to configure instances of [parameterType] for each
     * [BuiltArtifact]
     */
    fun <ParamT> submit(
        task: Task,
        workQueue: WorkQueue,
        actionType: Class<out WorkAction<ParamT>>,
        parameterType: Class<out ParamT>,
        parameterConfigurator: (
            builtArtifact: BuiltArtifact,
            outputLocation: Directory,
            parameters: ParamT) -> File): Supplier<BuiltArtifacts>
    where ParamT : WorkParameters, ParamT: Serializable

    /**
     * Submit a lambda to process synchronously each input [BuiltArtifact]
     */
    fun submit(task: Task, transformer: (input: BuiltArtifact) -> File)
}
