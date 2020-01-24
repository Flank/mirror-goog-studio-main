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

package com.android.build.api.artifact.impl

import com.android.build.api.artifact.ArtifactType
import com.android.build.api.artifact.ArtifactTransformationRequest
import com.android.build.api.artifact.TaskBasedOperations
import com.android.build.api.artifact.WorkItemParameters
import com.android.build.api.variant.BuiltArtifact
import com.android.build.api.variant.BuiltArtifacts
import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.file.FileSystemLocationProperty
import org.gradle.api.tasks.TaskProvider
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkQueue
import java.io.File
import java.lang.RuntimeException
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier

internal class TaskBasedOperationsImpl<TaskT: Task>(
    val operations: OperationsImpl,
    val taskProvider: TaskProvider<TaskT>
) : TaskBasedOperations<TaskT> {

    override fun <T> toRead(
        type: T,
        at: (TaskT) -> FileSystemLocationProperty<Directory>
    ): TaskBasedOperations<TaskT> where T : ArtifactType<Directory>, T : ArtifactType.Many, T : ArtifactType.Single =
        TaskBasedOperationsWithInputImpl(operations = operations,
            taskProvider = taskProvider,
            inputArtifactType = type,
            inputLocation = at)

    override fun <T> andWrite(
        type: T,
        at: (TaskT) -> FileSystemLocationProperty<Directory>
    ): ArtifactTransformationRequest where T : ArtifactType<Directory>, T : ArtifactType.Many, T : ArtifactType.Single {
        throw InvalidUserCodeException("You must call toRead() before calling andWrite() method")
    }
}

class TaskBasedOperationsWithInputImpl<ArtifactTypeT, TaskT: Task>(
    private val operations: OperationsImpl,
    private val taskProvider: TaskProvider<TaskT>,
    private val inputArtifactType: ArtifactTypeT,
    private val inputLocation: (TaskT) -> FileSystemLocationProperty<Directory>
): TaskBasedOperations<TaskT> where ArtifactTypeT: ArtifactType<Directory>, ArtifactTypeT: ArtifactType.Single
{
    override fun <T> toRead(
        type: T,
        at: (TaskT) -> FileSystemLocationProperty<Directory>
    ): TaskBasedOperations<TaskT> where T : ArtifactType<Directory>, T : ArtifactType.Many, T : ArtifactType.Single {
        throw InvalidUserCodeException("You cannot call toRead() twice.")
    }

    override fun <T> andWrite(
        type: T,
        at: (TaskT) -> FileSystemLocationProperty<Directory>
    ): ArtifactTransformationRequest where T : ArtifactType<Directory>, T : ArtifactType.Many, T : ArtifactType.Single {
        // register the task as the provider of this artifact type
        operations.setInitialProvider(taskProvider, at).on(type)
        return ArtifactTransformationRequestImpl(
            operations = operations,
            taskProvider = taskProvider,
            inputArtifactType = inputArtifactType,
            inputLocation = inputLocation,
            outputArtifactType = type,
            outputLocation = at
        )
    }
}

class ArtifactTransformationRequestImpl<ArtifactTypeT, TaskT: Task>(
    private val operations: OperationsImpl,
    private val taskProvider: TaskProvider<TaskT>,
    private val inputLocation: (TaskT) -> FileSystemLocationProperty<Directory>,
    private val inputArtifactType: ArtifactTypeT,
    private val outputLocation: (TaskT) -> FileSystemLocationProperty<Directory>,
    private val outputArtifactType: ArtifactType<Directory>
) : ArtifactTransformationRequest where ArtifactTypeT: ArtifactType<Directory>, ArtifactTypeT: ArtifactType.Single {

    private val builtArtifactsReference = AtomicReference<BuiltArtifactsImpl>()
    private val inputLocationReference = AtomicReference<FileSystemLocationProperty<Directory>>()

    init {
        taskProvider.configure { task: TaskT ->
            inputLocation(task).apply {
                inputLocationReference.set(this)
                set(operations.get(inputArtifactType))
            }
            task.doLast {
                wrapUp(task)
            }
        }
    }

    internal fun wrapUp(task: TaskT) {
        builtArtifactsReference.get().save(outputLocation(task).get())
    }

    override fun <ParamT : WorkItemParameters> submit(
        workQueue: WorkQueue,
        parameters: Class<out ParamT>,
        action: Class<out WorkAction<ParamT>>,
        parametersConfigurator: (parameters: ParamT) -> Unit
    ): Supplier<BuiltArtifacts> {

        val mapOfBuiltArtifactsToParameters = mutableMapOf<BuiltArtifact, File>()
        val sourceBuiltArtifacts =
            BuiltArtifactsLoaderImpl().load(inputLocationReference.get())
                ?: throw RuntimeException("No provided artifacts.")
        sourceBuiltArtifacts.elements.forEach {builtArtifact ->
            workQueue.submit(action) {
                mapOfBuiltArtifactsToParameters[builtArtifact] =
                    it.initProperties(builtArtifact, outputLocation(taskProvider.get()).get())
                parametersConfigurator(it)
            }
        }

        builtArtifactsReference.set(
            BuiltArtifactsImpl(
                artifactType = outputArtifactType,
                applicationId = sourceBuiltArtifacts.applicationId,
                variantName = sourceBuiltArtifacts.variantName,
                elements = sourceBuiltArtifacts.elements
                    .map {
                        val output = mapOfBuiltArtifactsToParameters[it]
                            ?: throw RuntimeException("Unknown BuiltArtifact $it, file a bug")
                        it.newOutput(output.toPath())
                    }
            )
        )
        return Supplier {
            workQueue.await()
            builtArtifactsReference.get()
        }
    }

    override fun submit(transformer: (input: BuiltArtifact) -> File) {

        val sourceBuiltArtifacts = BuiltArtifactsLoaderImpl().load(inputLocationReference.get())
            ?: throw RuntimeException("No provided artifacts.")

        builtArtifactsReference.set(BuiltArtifactsImpl(
            applicationId = sourceBuiltArtifacts.applicationId,
            variantName = sourceBuiltArtifacts.variantName,
            artifactType = outputArtifactType,
            elements = sourceBuiltArtifacts.elements.map {
                it.newOutput(transformer(it).toPath())
            }))
    }
}