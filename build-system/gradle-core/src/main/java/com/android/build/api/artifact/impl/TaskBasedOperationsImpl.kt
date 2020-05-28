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

import com.android.build.api.artifact.Artifact
import com.android.build.api.artifact.ArtifactTransformationRequest
import com.android.build.api.artifact.TaskBasedOperations
import com.android.build.api.variant.impl.BuiltArtifactsImpl
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.FileSystemLocationProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import java.util.concurrent.atomic.AtomicReference

class TaskBasedOperationsImpl<TaskT: Task>(
    private val objects: ObjectFactory,
    private val artifacts: ArtifactsImpl,
    private val taskProvider: TaskProvider<TaskT>
): TaskBasedOperations<TaskT> {

    override fun <FileTypeT : FileSystemLocation, ArtifactTypeT : Artifact<FileTypeT>> toAppend(
        type: ArtifactTypeT,
        with: (TaskT) -> FileSystemLocationProperty<FileTypeT>
    ) where ArtifactTypeT : Artifact.Appendable {
        val artifactContainer = artifacts.getArtifactContainer(type)
        taskProvider.configure {
            with(it).set(artifacts.getOutputPath(type, taskProvider.name))
        }
        // all producers of a multiple artifact type are added to the initial list (just like
        // the AGP producers) since the transforms always operate on the complete list of added
        // providers.
        artifactContainer.addInitialProvider(taskProvider.flatMap { with(it) })
    }

    override fun <FileTypeT : FileSystemLocation, ArtifactTypeT : Artifact<FileTypeT>> toReplace(
        type: ArtifactTypeT,
        with: (TaskT) -> FileSystemLocationProperty<FileTypeT>
    ) where ArtifactTypeT : Artifact.Replaceable {
        val artifactContainer = artifacts.getArtifactContainer(type)
        taskProvider.configure {
            with(it).set(artifacts.getOutputPath(type, taskProvider.name))
        }
        artifactContainer.replace(taskProvider.flatMap { with(it) })
    }

    override fun <FileTypeT : FileSystemLocation, ArtifactTypeT : Artifact<FileTypeT>> toTransform(
        type: ArtifactTypeT,
        from: (TaskT) -> FileSystemLocationProperty<FileTypeT>,
        into: (TaskT) -> FileSystemLocationProperty<FileTypeT>
    ) where ArtifactTypeT : Artifact.Single, ArtifactTypeT : Artifact.Transformable {
        val artifactContainer = artifacts.getArtifactContainer(type)
        val currentProvider =  artifactContainer.transform(taskProvider.flatMap { into(it) })
        taskProvider.configure {
            from(it).set(currentProvider)
            // since the task will now execute, resolve its output path.
            into(it).set(
                artifacts.getOutputPath(type,
                    taskProvider.name,
                    type.getFileSystemLocationName()
                )
            )
        }
    }


    override fun <FileTypeT : FileSystemLocation, ArtifactTypeT> toTransformAll(
        type: ArtifactTypeT,
        from: (TaskT) -> ListProperty<FileTypeT>,
        into: (TaskT) -> FileSystemLocationProperty<FileTypeT>
    ) where ArtifactTypeT : Artifact<FileTypeT>,
            ArtifactTypeT : Artifact.Multiple,
            ArtifactTypeT : Artifact.Transformable {
        val artifactContainer = artifacts.getArtifactContainer(type)
        val newList = objects.listProperty(type.kind.dataType().java)
        val currentProviders= artifactContainer.transform(taskProvider.flatMap { newList })
        taskProvider.configure {
            newList.add(into(it))
            into(it).set(artifacts.getOutputPath(type, taskProvider.name))
            from(it).set(currentProviders)
        }
    }

    override fun <ArtifactTypeT : Artifact<Directory>> toTransformMany(
        type: ArtifactTypeT,
        from: (TaskT) -> DirectoryProperty,
        into: (TaskT) -> DirectoryProperty
    ): ArtifactTransformationRequest<TaskT> where ArtifactTypeT : Artifact.Single, ArtifactTypeT : Artifact.ContainsMany, ArtifactTypeT : Artifact.Transformable {

        val artifactContainer = artifacts.getArtifactContainer(type)
        val currentProvider =  artifactContainer.transform(taskProvider.flatMap { into(it) })
        val builtArtifactsReference = AtomicReference<BuiltArtifactsImpl>()

        initializeInput(taskProvider, from, into, currentProvider, builtArtifactsReference)

        return ArtifactTransformationRequestImpl(
            builtArtifactsReference,
            inputLocation = from,
            outputArtifactType = type,
            outputLocation = into
        )
    }

    fun <ArtifactTypeT, ArtifactTypeU> toTransformMany(
        sourceType: ArtifactTypeT,
        from: (TaskT) -> DirectoryProperty,
        targetType: ArtifactTypeU,
        into: (TaskT) -> DirectoryProperty,
        atLocation: String? = null
    ): ArtifactTransformationRequest<TaskT>
            where
            ArtifactTypeT : Artifact<Directory>,
            ArtifactTypeT : Artifact.Single,
            ArtifactTypeT : Artifact.ContainsMany,
            ArtifactTypeU : Artifact<Directory>,
            ArtifactTypeU : Artifact.Single,
            ArtifactTypeU : Artifact.ContainsMany {
        val initialProvider = artifacts.setInitialProvider(taskProvider, into)
        if (atLocation != null) {
            initialProvider.atLocation(atLocation)
        }
        initialProvider.on(targetType)

        return initializeTransform(sourceType, targetType, from, into)
    }

    private fun <ArtifactTypeT, ArtifactTypeU> initializeTransform(
        sourceType: ArtifactTypeT,
        targetType: ArtifactTypeU,
        inputLocation: (TaskT) -> DirectoryProperty,
        outputLocation: (TaskT) -> DirectoryProperty
    ): ArtifactTransformationRequestImpl<ArtifactTypeU, TaskT>
            where ArtifactTypeT : Artifact<Directory>,
                  ArtifactTypeT : Artifact.ContainsMany,
                  ArtifactTypeT : Artifact.Single,
                  ArtifactTypeU : Artifact<Directory>,
                  ArtifactTypeU : Artifact.Single,
                  ArtifactTypeU : Artifact.ContainsMany {

        val builtArtifactsReference = AtomicReference<BuiltArtifactsImpl>()
        val inputProvider = artifacts.get(sourceType)

        initializeInput(taskProvider, inputLocation, outputLocation, inputProvider, builtArtifactsReference)

        return ArtifactTransformationRequestImpl(
            builtArtifactsReference,
            inputLocation = inputLocation,
            outputArtifactType = targetType,
            outputLocation = outputLocation
        )
    }

    companion object {

        /**
         * Keep this method as a static to avoid all possible unwanted variable capturing from
         * lambdas.
         */
        fun <T : Task> initializeInput(
            taskProvider: TaskProvider<T>,
            inputLocation: (T) -> FileSystemLocationProperty<Directory>,
            outputLocation: (T) -> FileSystemLocationProperty<Directory>,
            inputProvider: Provider<Directory>,
            builtArtifactsReference: AtomicReference<BuiltArtifactsImpl>
        ) {
            taskProvider.configure { task: T ->
                inputLocation(task).set(inputProvider)
                task.doLast {
                    builtArtifactsReference.get().save(outputLocation(task).get())
                }
            }
        }
    }
}