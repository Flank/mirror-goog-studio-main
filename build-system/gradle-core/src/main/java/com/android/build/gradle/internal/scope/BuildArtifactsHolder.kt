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

package com.android.build.gradle.internal.scope

import com.android.build.api.artifact.ArtifactType
import com.android.build.api.artifact.ArtifactKind
import com.android.build.api.artifact.impl.OperationsImpl
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.io.FileReader

typealias Report = Map<String, BuildArtifactsHolder.ProducersData>

/**
 * Buildable artifact holder.
 *
 * This class manages buildable artifacts, allowing users to transform [ArtifactType].
 *
 * @param project the Gradle [Project]
 * @param rootOutputDir a supplier for the intermediate directories to place output files.
 * @parma dslServices the scope for dsl parsing issue raising.
 */
abstract class BuildArtifactsHolder(
    private val project: Project,
    private val rootOutputDir: () -> File,
    identifier: String
) {

    private val operations= OperationsImpl(project.objects, identifier,
        project.layout.buildDirectory)

    fun getOperations(): OperationsImpl = operations

    // because of existing public APIs, we cannot move [AnchorOutputType.ALL_CLASSES] to Provider<>
    private val allClasses= project.files()

    /**
     * Republishes an [ArtifactType] under a different type. This is useful when a level of
     * indirection is used.
     *
     * @param sourceType the original [ArtifactType] for the built artifacts.
     * @param targetType the supplemental [ArtifactType] the same built artifacts will also be
     * published under.
     */
    fun <T: FileSystemLocation, U> republish(sourceType: U, targetType: U)
            where U: ArtifactType<T>, U : ArtifactType.Single {
        operations.republish(sourceType, targetType)
    }

    // TODO : remove these 2 APIs once all java tasks stopped using those after Kotlin translation.
    fun <T: Task, ARTIFACT_TYPE> producesFile(
        artifactType: ARTIFACT_TYPE,
        taskProvider: TaskProvider<out T>,
        productProvider: (T) -> RegularFileProperty,
        fileName: String = "out"
    )
            where ARTIFACT_TYPE : ArtifactType<RegularFile>,
                  ARTIFACT_TYPE : ArtifactType.Single
            = operations.setInitialProvider(
                taskProvider,
                productProvider)
                .withName(fileName)
                .on(artifactType)

    /**
     * Registers a new [Directory] producer for a particular [ArtifactType]. The producer is
     * identified by a [TaskProvider] to avoid configuring the task until the produced [Directory]
     * is required by another [Task].
     *
     * The simplest way to use the mechanism is as follow :
     * <pre>
     *     open class MyTask(objectFactory: ObjectFactory): Task() {
     *          val outputFile = objectFactory.directoryProperty()
     *     }
     *
     *     val myTaskProvider = taskFactory.register("myTask", MyTask::class.java)
     *
     *     scope.artifacts.producesDir(InternalArtifactType.SOME_ID,
     *            OperationType.INITIAL,
     *            myTaskProvider,
     *            myTaskProvider.map { it -> it.outputFile }
     *            "some_file_name")
     *
     * </pre>
     *
     * Consumers should use [OperationsImpl.get] or [OperationsImpl.getAll] to get a [Provider] instance
     * for registered products which ensures that [Task]s don't get initialized until the
     * [Provider.get] method is invoked during a consumer task configuration execution for instance.
     *
     * @param artifactType the produced artifact type
     * @param taskProvider the [TaskProvider] for the task ultimately responsible for producing the
     * artifact.
     * @param productProvider the [Provider] of the artifact [Directory]
     * @param buildDirectory the destination directory of the produced artifact or not provided if
     * using the default location.
     * @param fileName the desired directory name or empty string if no sub-directory should be
     * used.
     */
    fun <T: Task, ARTIFACT_TYPE> producesDir(
        artifactType: ARTIFACT_TYPE,
        taskProvider: TaskProvider<out T>,
        propertyProvider: (T) -> DirectoryProperty,
        fileName: String = "out"
    ) where ARTIFACT_TYPE : ArtifactType<Directory>,
            ARTIFACT_TYPE : ArtifactType.Single
            =         operations.setInitialProvider(
        taskProvider,
        propertyProvider
    ).withName(fileName).on(artifactType)

    /**
     * Appends a [FileCollection] to the [AnchorOutputType.ALL_CLASSES] artifact.
     *
     * @param files the [FileCollection] to add.
     */
    fun appendToAllClasses(files: FileCollection) {
        synchronized(allClasses) {
            allClasses.from(files)
        }
    }

    /**
     * Returns the current [FileCollection] for [AnchorOutputType.ALL_CLASSES] as of now.
     * The returned file collection is final but its content can change.
     */
    fun getAllClasses(): FileCollection = allClasses

    // TODO: Reimplement or remove feature.
    fun createReport(): Report = mapOf()

    /** A data class for use with GSON. */
    data class ProducerData(
        @SerializedName("files")
        val files: List<String>,
        @SerializedName("builtBy")
        val builtBy : String
    )

    data class ProducersData(
        @SerializedName("producer")
        val producers: List<ProducerData>
    )

    companion object {

        fun parseReport(file : File) : Report {
            val result = mutableMapOf<String, ProducersData>()
            val parser = JsonParser()
            FileReader(file).use { reader ->
                for ((key, value) in parser.parse(reader).asJsonObject.entrySet()) {
                    val obj = value.asJsonObject
                    val producers = obj.getAsJsonArray("producer").map {
                        ProducerData(
                            it.asJsonObject.getAsJsonArray("files").map {
                                it.asString
                            },
                            it.asJsonObject.get("builtBy").asString
                        )
                    }

                    result[key] = ProducersData(producers)
                }
            }
            return result
        }
    }
}
