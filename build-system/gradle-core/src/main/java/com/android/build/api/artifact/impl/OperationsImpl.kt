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

package com.android.build.api.artifact.impl

import com.android.build.api.artifact.AppendRequest
import com.android.build.api.artifact.ArtifactType
import com.android.build.api.artifact.MultipleTransformRequest
import com.android.build.api.artifact.Operations
import com.android.build.api.artifact.ReplaceRequest
import com.android.build.api.artifact.TransformRequest
import com.android.build.gradle.internal.scope.getOutputDirectory
import org.gradle.api.Task
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.FileSystemLocationProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider

/**
 * Implementation of the [Operations] Variant API interface.
 *
 * Implementation will delegate most of its [FileSystemLocationProperty] handling to
 * [ArtifactContainer] only handling the [TaskProvider] relationships.
 *
 * This call will also contains some AGP private methods for added services to support AGP
 * specific services like setting initial AGP providers.
 */
class OperationsImpl(
    private val objects: ObjectFactory,
    private val identifier: String,
    private val buildDirectory: DirectoryProperty): Operations {

    private val storageProvider = StorageProviderImpl()

    override fun <FILE_TYPE : FileSystemLocation, ARTIFACT_TYPE> get(type: ARTIFACT_TYPE): Provider<FILE_TYPE>
            where ARTIFACT_TYPE : ArtifactType<FILE_TYPE>, ARTIFACT_TYPE : ArtifactType.Single
            = getArtifactContainer(type).get()

    override fun <FILE_TYPE : FileSystemLocation, ARTIFACT_TYPE> getAll(type: ARTIFACT_TYPE): Provider<List<FILE_TYPE>>
            where ARTIFACT_TYPE : ArtifactType<FILE_TYPE>,
                  ARTIFACT_TYPE : ArtifactType.Multiple
            = getArtifactContainer(type).get()

    override fun <TASK : Task, FILE_TYPE : FileSystemLocation> append(
        taskProvider: TaskProvider<TASK>,
        with: (TASK) -> Provider<FILE_TYPE>
    ): AppendRequest<FILE_TYPE> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun <TASK : Task, FILE_TYPE : FileSystemLocation> transform(
        taskProvider: TaskProvider<TASK>,
        from: (TASK) -> Property<FILE_TYPE>,
        into: (TASK) -> FileSystemLocationProperty<FILE_TYPE>
    ): TransformRequest<FILE_TYPE> = TransformRequestImpl(this, taskProvider, from, into)

    override fun <TASK : Task, FILE_TYPE : FileSystemLocation> transformAll(
        taskProvider: TaskProvider<TASK>,
        from: (TASK) -> ListProperty<FILE_TYPE>,
        into: (TASK) -> Provider<FILE_TYPE>
    ): MultipleTransformRequest<FILE_TYPE> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun <TASK : Task, FILE_TYPE : FileSystemLocation> replace(
        taskProvider: TaskProvider<TASK>,
        with: (TASK) -> FileSystemLocationProperty<FILE_TYPE>
    ): ReplaceRequest<FILE_TYPE> = ReplaceRequestImpl(this, taskProvider, with)

    // End of public API implementation, start of private AGP services.

    internal fun <T: FileSystemLocation> getOutputDirectory(type: ArtifactType<T>, taskName: String)=
        type.getOutputDirectory(buildDirectory, identifier, taskName)

    /**
     * Returns the [ArtifactContainer] for the passed [type]. The instance may be allocated as part
     * of the call if there is not [ArtifactContainer] for this [type] registered yet.
     *
     * @param type requested artifact type
     * @return the [ArtifactContainer] for the passed type
     */
    internal fun <ARTIFACT_TYPE, FILE_TYPE> getArtifactContainer(type: ARTIFACT_TYPE): SingleArtifactContainer<FILE_TYPE> where
            ARTIFACT_TYPE : ArtifactType.Single,
            ARTIFACT_TYPE : ArtifactType<FILE_TYPE>,
            FILE_TYPE : FileSystemLocation {

        return storageProvider.getStorage(type.kind).getArtifact(objects, type)
    }

    /**
     * Returns the [ArtifactContainer] for the passed [type]. The instance may be allocated as part
     * of the call if there is not [ArtifactContainer] for this [type] registered yet.
     *
     * @param type requested artifact type
     * @return the [ArtifactContainer] for the passed type
     */
    internal fun <ARTIFACT_TYPE, FILE_TYPE> getArtifactContainer(type: ARTIFACT_TYPE): MultipleArtifactContainer<FILE_TYPE> where
            ARTIFACT_TYPE : ArtifactType.Multiple,
            ARTIFACT_TYPE : ArtifactType<FILE_TYPE>,
            FILE_TYPE : FileSystemLocation {

        return storageProvider.getStorage(type.kind).getArtifact(objects, type)
    }

    /**
     * Sets the Android Gradle Plugin producer. Although conceptually the AGP producers are first
     * to process/consume artifacts, we want to register them last after all custom code has had
     * opportunities to transform or replace it.
     *
     * Therefore, we cannot rely on the usual append/replace pattern but instead use this API to
     * be artificially put first in the list of producers for the passed [type]
     *
     * @param type the artifact type being produced
     * @param taskProvider the [TaskProvider] for the task producing the artifact
     * @param property: the field reference to retrieve the output from the task
     */
    fun <ARTIFACT_TYPE, FILE_TYPE, TASK> setInitialProvider(
        type: ARTIFACT_TYPE,
        taskProvider: TaskProvider<TASK>,
        property: (TASK) -> FileSystemLocationProperty<FILE_TYPE>) where
            ARTIFACT_TYPE : ArtifactType.Single,
            ARTIFACT_TYPE : ArtifactType<FILE_TYPE>,
            FILE_TYPE : FileSystemLocation,
            TASK: Task {

        val artifactContainer = getArtifactContainer(type)
        taskProvider.configure {
            // since the taskProvider will execute, resolve its output path.
            property(it).set(type.getOutputDirectory(buildDirectory, identifier,
                if (artifactContainer.hasCustomProviders()) taskProvider.name else ""))
        }
        artifactContainer.setInitialProvider(taskProvider.flatMap { property(it) })
    }

    /**
     * Adds an Android Gradle Plugin producer.
     *
     * The passed [type] must be a [ArtifactType.Multiple] that accepts more than one producer.
     *
     * Although conceptually the AGP producers are first to produce artifacts, we want to register
     * them last after all custom code had the opportunity to transform or replace it.
     *
     * Therefore, we cannot rely on the usual append/replace pattern but instead use this API to
     * be artificially put first in the list of producers for the passed [type]
     *
     * @param type the [ArtifactType.Multiple] artifact type being produced
     * @param taskProvider the [TaskProvider] for the task producing the artifact
     * @param property: the field reference to retrieve the output from the task
     */
    internal fun <ARTIFACT_TYPE, FILE_TYPE, TASK> addInitialProvider(
        type: ARTIFACT_TYPE,
        taskProvider: TaskProvider<TASK>,
        property: (TASK) -> FileSystemLocationProperty<FILE_TYPE>) where
            ARTIFACT_TYPE : ArtifactType.Multiple,
            ARTIFACT_TYPE : ArtifactType<FILE_TYPE>,
            FILE_TYPE : FileSystemLocation,
            TASK: Task {

        val artifactContainer = getArtifactContainer(type)
        taskProvider.configure {
            // since the taskProvider will execute, resolve its output path, and since there can
            // be multiple ones, just put the task name at all times.
            property(it).set(type.getOutputDirectory(buildDirectory, identifier, taskProvider.name))
        }
        artifactContainer.addInitialProvider(taskProvider.flatMap { property(it) })
    }
}

/**
 * Specialization of the [TransformRequest] public API with added services private to AGP.
 */
class TransformRequestImpl<TASK : Task, FILE_TYPE : FileSystemLocation>(
    private val operationsImpl: OperationsImpl,
    private val taskProvider: TaskProvider<TASK>,
    private val from: (TASK) -> Property<FILE_TYPE>,
    private val into: (TASK) -> FileSystemLocationProperty<FILE_TYPE>
) : TransformRequest<FILE_TYPE> {

    private var outputDirectory: String? = null

    /**
     * Overrides default location for the output
     */
    fun at(location: String): TransformRequestImpl<TASK, FILE_TYPE> {
        outputDirectory = location
        return this
    }

    override fun <ARTIFACT_TYPE> on(type: ARTIFACT_TYPE)
            where ARTIFACT_TYPE : ArtifactType<FILE_TYPE>,
                  ARTIFACT_TYPE : ArtifactType.Transformable,
                  ARTIFACT_TYPE : ArtifactType.Single {

        val artifactContainer = operationsImpl.getArtifactContainer(type)
        val currentProvider =  artifactContainer.transform(taskProvider.flatMap { into(it) })
        taskProvider.configure {
            from(it).set(currentProvider)
            // since the task will now execute, resolve its output path.
            into(it).set(operationsImpl.getOutputDirectory(type, taskProvider.name))
        }
    }
}

/**
 * Specialization of the [ReplaceRequest] public API with added services private to AGP.
 */
class ReplaceRequestImpl<TASK: Task, FILE_TYPE: FileSystemLocation>(
    private val operationsImpl: OperationsImpl,
    private val taskProvider: TaskProvider<TASK>,
    private val with: (TASK) -> FileSystemLocationProperty<FILE_TYPE>
): ReplaceRequest<FILE_TYPE> {
    override fun <ARTIFACT_TYPE> on(type: ARTIFACT_TYPE) where
            ARTIFACT_TYPE : ArtifactType<FILE_TYPE>,
            ARTIFACT_TYPE : ArtifactType.Replaceable {

        val artifactContainer = operationsImpl.getArtifactContainer(type)
        taskProvider.configure {
            with(it).set(operationsImpl.getOutputDirectory(type, taskProvider.name))
        }
        artifactContainer.replace(taskProvider.flatMap { with(it) })
    }

}
