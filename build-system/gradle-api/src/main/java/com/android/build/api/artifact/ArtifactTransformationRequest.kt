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
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkQueue
import java.io.File
import java.io.Serializable
import java.util.function.Supplier

@Incubating
interface ArtifactTransformationRequest<TaskT: Task> {

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
        task: TaskT,
        workQueue: WorkQueue,
        actionType: Class<out WorkAction<ParamT>>,
        parameterType: Class<out ParamT>,
        parameterConfigurator: (
            builtArtifact: BuiltArtifact,
            outputLocation: Directory,
            parameters: ParamT) -> File
    ): Supplier<BuiltArtifacts>
            where ParamT : WorkParameters, ParamT: Serializable

    /**
     * Submit a lambda to process synchronously each input [BuiltArtifact]
     */
    fun submit(task: TaskT, transformer: (input: BuiltArtifact) -> File)
}