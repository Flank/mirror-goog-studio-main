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
import com.android.utils.appendCapitalized
import com.google.common.base.Joiner
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
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
 * @parma dslScope the scope for dsl parsing issue raising.
 */
abstract class BuildArtifactsHolder(
    private val project: Project,
    private val rootOutputDir: () -> File
) {

    private val fileProducersMap = ProducersMap(
        ArtifactType.FILE,
        project.objects,
        project.layout.buildDirectory,
        this::getIdentifier)
    private val directoryProducersMap = ProducersMap(
        ArtifactType.DIRECTORY,
        project.objects,
        project.layout.buildDirectory,
        this::getIdentifier)

    // because of existing public APIs, we cannot move [AnchorOutputType.ALL_CLASSES] to Provider<>
    private val allClasses= project.files()

    /**
     * Types of operation on Buildable artifact as indicated by tasks producing the artifact.
     */
    enum class OperationType {
        /**
         * Initial producer of the artifact
         */
        INITIAL,
        /**
         * Producer appends more files/directories to artifact
         */
        APPEND,
        /**
         * Producer transforms (replace) existing artifact files/directories with new ones.
         */
        TRANSFORM
    }

    /**
     * Republishes an [ArtifactType] under a different type. This is useful when a level of
     * indirection is used.
     *
     * @param sourceType the original [ArtifactType] for the built artifacts.
     * @param targetType the supplemental [ArtifactType] the same built artifacts will also be
     * published under.
     */
    fun <T: FileSystemLocation> republish(sourceType: ArtifactType<T>, targetType: ArtifactType<T>) {
        getProducerMap(sourceType).republish(sourceType, targetType)
    }

    /**
     * Copies a published [ArtifactType] from another instance of [BuildArtifactsHolder] to this
     * instance.
     * This does not remove the original elements from the source [BuildArtifactsHolder].
     *
     * @param artifactType artifact type to copy to this holder.
     * @param from source [BuildArtifactsHolder] to copy the produced artifacts from.
     */
    fun <T: FileSystemLocation> copy(artifactType: ArtifactType<T>, from: BuildArtifactsHolder) {
        copy(artifactType, from, artifactType)
    }

    /**
     * Copies a published [ArtifactType] from another instance of [BuildArtifactsHolder] to this
     * instance.
     * This does not remove the original elements from the source [BuildArtifactsHolder].
     *
     * @param artifactType artifact type to copy to this holder.
     * @param from source [BuildArtifactsHolder] to copy the produced artifacts from.
     * @param originalArtifactType artifact type under which the producers are registered in the
     * source [BuildArtifactsHolder], by default is the same [artifactType]
     */
    fun <T: FileSystemLocation> copy(artifactType: ArtifactType<T>, from: BuildArtifactsHolder, originalArtifactType: ArtifactType<T> = artifactType) {
        getProducerMap(artifactType).copy(artifactType,
            from.getProducerMap(originalArtifactType).getProducers(originalArtifactType))
    }

    /**
     * Registers a new [RegularFile] producer for a particular [ArtifactType]. The producer is
     * identified by a [TaskProvider] to avoid configuring the task until the produced [RegularFile]
     * is required by another [Task].
     *
     * The simplest way to use the mechanism is as follow :
     * <pre>
     *     open class MyTask(objectFactory: ObjectFactory): Task() {
     *          val outputFile = objectFactory.fileProperty()
     *     }
     *
     *     val myTaskProvider = taskFactory.register("myTask", MyTask::class.java)
     *
     *     scope.artifacts.producesFile(InternalArtifactType.SOME_ID,
     *            OperationType.INITIAL,
     *            myTaskProvider,
     *            myTaskProvider.map { it -> it.outputFile }
     *            "some_file_name")
     *
     * </pre>
     *
     * Consumers should use [getFinalProduct] or [getFinalProducts] to get a [Provider] instance
     * for registered products which ensures that [Task]s don't get initialized until the
     * [Provider.get] method is invoked during a consumer task configuration execution for instance.
     *
     * @param artifactType the produced artifact type
     * @param operationType the expected type of production, there can be only one
     * [OperationType.INITIAL] but many [OperationType.APPEND] or [OperationType.TRANSFORM]
     * @param taskProvider the [TaskProvider] for the task ultimately responsible for producing the
     * artifact.
     * @param productProvider the [Provider] of the artifact [RegularFile]
     * @param buildDirectory the destination directory of the produced artifact or not provided if
     * using the default location.
     * @param fileName the desired file name, must be provided.
     */
    fun <T: Task> producesFile(
        artifactType: ArtifactType<RegularFile>,
        operationType: OperationType,
        taskProvider: TaskProvider<out T>,
        productProvider: (T) -> RegularFileProperty,
        buildDirectory: String? = null,
        fileName: String
    ) {

        val settableProperty = project.objects.fileProperty()
        produces(artifactType,
            fileProducersMap,
            operationType,
            taskProvider,
            productProvider,
            settableProperty,
            fileName,
            buildDirectory)
    }

    /**
     * Registers a new [RegularFile] producer for a particular [ArtifactType]. The producer is
     * identified by a [TaskProvider] to avoid configuring the task until the produced [RegularFile]
     * is required by another [Task].
     *
     * The passed [productProvider] returns a [Provider] which mean that the output location cannot
     * be changed and will be set by the task itself or during its configuration.
     */
    fun <T: Task> producesFile(
        artifactType: ArtifactType<RegularFile>,
        operationType: OperationType,
        taskProvider: TaskProvider<out T>,
        productProvider: (T) -> Provider<RegularFile>) {

        produces(artifactType,
            fileProducersMap,
            operationType,
            taskProvider,
            productProvider)
    }

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
     * Consumers should use [getFinalProduct] or [getFinalProducts] to get a [Provider] instance
     * for registered products which ensures that [Task]s don't get initialized until the
     * [Provider.get] method is invoked during a consumer task configuration execution for instance.
     *
     * @param artifactType the produced artifact type
     * @param operationType the expected type of production, there can be only one
     * [OperationType.INITIAL] but many [OperationType.APPEND] or [OperationType.TRANSFORM]
     * @param taskProvider the [TaskProvider] for the task ultimately responsible for producing the
     * artifact.
     * @param productProvider the [Provider] of the artifact [Directory]
     * @param buildDirectory the destination directory of the produced artifact or not provided if
     * using the default location.
     * @param fileName the desired directory name or empty string if no sub-directory should be
     * used.
     */
    fun <T: Task> producesDir(
        artifactType: ArtifactType<Directory>,
        operationType: OperationType,
        taskProvider: TaskProvider<out T>,
        productProvider: (T) -> DirectoryProperty,
        buildDirectory: String? = null,
        fileName: String = "out"
    ) {

        produces(artifactType,
            directoryProducersMap,
            operationType,
            taskProvider,
            productProvider,
            project.objects.directoryProperty(),
            fileName,
            buildDirectory)
    }

    // TODO : remove these 2 APIs once all java tasks stopped using those after Kotlin translation.
    fun <T: Task> producesFile(
        artifactType: ArtifactType<RegularFile>,
        operationType: OperationType,
        taskProvider: TaskProvider<out T>,
        productProvider: (T) -> RegularFileProperty,
        fileName: String = "out"
    )= producesFile(artifactType, operationType, taskProvider, productProvider, null, fileName)


    fun <T: Task> producesDir(
        artifactType: ArtifactType<Directory>,
        operationType: OperationType,
        taskProvider: TaskProvider<out T>,
        propertyProvider: (T) -> DirectoryProperty,
        fileName: String = "out"
    )= producesDir(artifactType, operationType, taskProvider, propertyProvider, null, fileName)

    private val dummyTask by lazy {

        project.tasks.register("dummy" + getIdentifier(), DummyTask::class.java)
    }

    abstract class DummyTask: DefaultTask() {
        abstract val emptyFileProperty: RegularFileProperty
    }

    fun emptyFile(artifactType: ArtifactType<RegularFile>) {
        produces<RegularFile, DummyTask>(artifactType,
            fileProducersMap,
            OperationType.INITIAL,
            dummyTask,
            DummyTask::emptyFileProperty,
            project.objects.fileProperty(),
            "out"
        )
    }

    private fun <T: FileSystemLocation, U: Task> produces(artifactType: ArtifactType<T>,
        producersMap: ProducersMap<T>,
        operationType: OperationType,
        taskProvider: TaskProvider<out U>,
        productProvider: (U) -> Provider<T>) {

        val producers = producersMap.getProducers(artifactType)
        val product = taskProvider.map { productProvider(it) }

        checkOperationType(operationType, artifactType, producers, taskProvider)
        producers.add(product, taskProvider.name)
    }

    private fun <T : FileSystemLocation, U: Task> produces(artifactType: ArtifactType<T>,
        producersMap: ProducersMap<T>,
        operationType: OperationType,
        taskProvider: TaskProvider<out U>,
        productProvider: (U) -> Property<T>,
        settableFileLocation: Property<T>,
        fileName: String,
        buildDirectory: String? = null) {

        if (producersMap.artifactKind != artifactType.kind) {
            val correctApiFamily = if (artifactType.kind==ArtifactType.FILE)
                "producesFile" else "producesDir"
            throw RuntimeException("Wrong usage of the BuildArtifacts APIs by task ${taskProvider.name}\n" +
                    "who is trying to publish $artifactType as a ${producersMap.artifactKind} while the " +
                    "artifact is defined as a ${artifactType.kind}\n" +
                    "For ${artifactType.kind} use $correctApiFamily type of APIs")
        }

        val producers = producersMap.getProducers(artifactType, buildDirectory)
        val product= taskProvider.map { productProvider(it) }

        checkOperationType(operationType, artifactType, producers, taskProvider)
        producers.add(settableFileLocation, product, taskProvider.name, fileName)

        // note that this configuration block may be called immediately in case the task has
        // already been initialized.
        taskProvider.configure {

            product.get().set(settableFileLocation)

            // add a new configuration action to make sure the producers are configured even
            // if no one injects the result. The task is being configured so it will be executed
            // and output folders must be set correctly.
            // this can happen when users request an intermediary task execution (instead of
            // assemble for instance).
            producers.resolveAllAndReturnLast()
        }
    }

    private fun <T: FileSystemLocation> checkOperationType(operationType: OperationType,
        artifactType: ArtifactType<T>,
        producers: ProducersMap.Producers<T>,
        taskProvider: TaskProvider<out Task>) {
        when(operationType) {
            OperationType.INITIAL -> {
                if (!producers.isEmpty()) {
                    val plural = producers.hasMultipleProducers()
                    throw RuntimeException(
                        """|Task ${taskProvider.name} is expecting to be the initial producer of
                                |$artifactType, but the following ${if (plural) "tasks" else "task"} : ${Joiner.on(',').join(producers.map { it.taskName})}
                                |${if (plural) "are" else "is"} already registered as ${if (plural) "producers" else "producer"}"""
                            .trimMargin()
                    )
                }
            }
            OperationType.APPEND -> {
            }
            OperationType.TRANSFORM -> {
                producers.clear()
            }
        }
    }

    private fun <T: FileSystemLocation> getProducerMap(artifactType: ArtifactType<T>): ProducersMap<T> {

        return when(artifactType.kind) {
            ArtifactType.FILE -> fileProducersMap as ProducersMap<T>
            ArtifactType.DIRECTORY -> directoryProducersMap as ProducersMap<T>
            else -> throw RuntimeException("${artifactType.kind} not handled.")
        }
    }

    /**
     * Returns the current produced version of an [ArtifactType]
     *
     * @param artifactType the identifier for the built artifact.
     */
    fun <T : FileSystemLocation> getCurrentProduct(artifactType: ArtifactType<T>) =
        getProducerMap(artifactType).getProducers(artifactType).getCurrent()

    /**
     * Returns true if there is at least one producer for the passed [ArtifactType]
     *
     * @param artifactType the identifier for the built artifact.
     */
    fun <T: FileSystemLocation> hasFinalProduct(artifactType: ArtifactType<T>) = getProducerMap(artifactType).hasProducers(artifactType)

    /**
     * Returns a [Provider] of either a [Directory] or a [RegularFile] depending on the passed
     * [ArtifactKind]. The [Provider] will represent the final value of the built artifact
     * irrespective of when this call is made.
     *
     * If there are more than one producer appending artifacts for the passed type, calling this
     * method will generate an error and [getFinalProducts] should be used instead.
     *
     * The simplest way to use the mechanism is as follow :
     * <pre>
     *     open class MyTask(objectFactory: ObjectFactory): Task() {
     *          val inputFile: Provider<RegularFile>
     *     }
     *
     *     val myTaskProvider = taskFactory.register("myTask", MyTask::class.java) {
     *          it.inputFile = scope.artifacts.getFinalProduct(InternalArtifactTYpe.SOME_ID)
     *     }
     * </pre>
     *
     * @param artifactType the identifier for the built artifact.
     */
    fun <T: FileSystemLocation> getFinalProduct(artifactType: ArtifactType<T>): Provider<T> {
        val producers = getProducerMap(artifactType).getProducers(artifactType)
        if (producers.size > 1) {
            throw java.lang.RuntimeException(
                """A single producer of $artifactType was requested, but the following tasks
                    |produce it: ${Joiner.on(',').join(
                    producers.map { it.taskName})}""".trimMargin())
        }
        return producers.injectable
    }

    /**
     * Returns a [Provider] of a [FileCollection] for the passed [ArtifactType].
     * The [FileCollection] will represent the final value of the built artifact irrespective of
     * when this call is made as long as the [Provider] is resolved at execution time.
     *
     * @param  artifactType the identifier for thje built artifact.
     */
    fun getFinalProductAsFileCollection(artifactType: ArtifactType<out FileSystemLocation>): Provider<FileCollection> {
        return project.provider {
            if (artifactType == AnchorOutputType.ALL_CLASSES) {
                getAllClasses()
            } else {
                if (hasFinalProduct(artifactType)) {
                    project.files(getFinalProduct(artifactType))
                } else project.files()
            }
        }
    }

    /**
     * Sets a [Property] value to the final producer for the given artifact type.
     *
     * If there are more than one producer appending artifacts for the passed type, calling this
     * method will generate an error and [setFinalProducts] should be used instead.
     *
     * The simplest way to use the mechanism is as follow :
     * <pre>
     *     abstract class MyTask: Task() {
     *          @InputFile
     *          abstract val inputFile: RegularFileProperty
     *     }
     *
     *     val myTaskProvider = taskFactory.register("myTask", MyTask::class.java) {
     *          scope.artifacts.setTaskInputToFinalProduct(InternalArtifactTYpe.SOME_ID, it.inputFile)
     *     }
     * </pre>
     *
     * @param artifactType requested artifact type
     * @param taskInputProperty the [Property] to set the final producer on.
     */
    fun <T: FileSystemLocation> setTaskInputToFinalProduct(artifactType: ArtifactType<T>, taskInputProperty: Property<T>) {
        val finalProduct = getFinalProduct<T>(artifactType)
        taskInputProperty.set(finalProduct)
    }

    /**
     * Sets a [ListProperty] value to all producers for the given artifact type.
     *
     * The simplest way to use the mechanism is as follow :
     * <pre>
     *     abstract class MyTask: Task() {
     *          @InputFiles
     *          abstract val inputFiles: ListProperty<RegularFile>
     *     }
     *
     *     val myTaskProvider = taskFactory.register("myTask", MyTask::class.java) {
     *          scope.artifacts.setTaskInputToFinalProducts(InternalArtifactTYpe.SOME_ID, it.inputFiles)
     *     }
     * </pre>
     *
     * @param artifactType requested artifact type
     * @param taskInputProperty the [ListProperty] to set the producers on.
     */
    fun <T: FileSystemLocation> setTaskInputToFinalProducts(artifactType: ArtifactType<T>, taskInputProperty: ListProperty<T>) {
        val finalProducts = getFinalProducts<T>(artifactType)
        taskInputProperty.set(finalProducts)
    }

    /**
     * Sets a [ListProperty] value to all the produces for the given artifact type.
     *
     * The simplest way to use the mechanism is as follow :
     * <pre>
     *     abstract class MyTask: Task() {
     *          @InputFiles
     *          abstract val inputFiles: ListProperty<RegularFile>
     *     }
     *
     *     val myTaskProvider = taskFactory.register("myTask", MyTask::class.java) {
     *          scope.artifacts.setFinalProducts(InternalArtifactTYpe.SOME_ID, it.inputFiles)
     *     }
     * </pre>
     *
     * @param artifactType requested artifact type
     * @param receiver the [ListProperty] to set the producers on.
     */
    fun <T: FileSystemLocation> setFinalProducts(artifactType: ArtifactType<T>, receiver: ListProperty<T>) {
        val finalProduct = getFinalProducts<T>(artifactType)
        receiver.set(finalProduct)
    }

    /**
     * See [getFinalProducts] API.
     *
     * On top of returning the [Provider] of [Directory] or [RegularFile], also returns a
     * [Provider] of [String] which represents the task name of the final producer task for the
     * passed artifact type.
     *
     * @param artifactType the identifier for the built artifact.
     * @return a [Pair] of [Provider] for the [FileSystemLocation] and task name producing it.
     */
    fun <T: FileSystemLocation> getFinalProductWithTaskName(artifactType: ArtifactType<T>): Pair<Provider<String>, Provider<T>> {
        return Pair(getProducerMap(artifactType).getProducers(artifactType).lastProducerTaskName,
            getFinalProduct(artifactType))
    }

    /**
     * Returns a [ListProperty]s of either [Directory] or [RegularFile] depending on the
     * passed [ArtifactKind]. This possibly empty list will contain the final
     * values of the built artifacts irrespective of when this call is made.
     *
     * @param artifactType the identifier for the built artifact.
     */
    fun <T: FileSystemLocation> getFinalProducts(artifactType: ArtifactType<T>): ListProperty<T> {
        val producers = getProducerMap(artifactType).getProducers(artifactType)
        return producers.getAllProducers()
    }

    /**
     * Returns an appropriate task name for the variant with the given prefix.
     */
    fun getTaskName(prefix : String) : String {
        return prefix.appendCapitalized(getIdentifier())
    }

    /**
     * Returns a identifier that will uniquely identify this artifacts holder against other holders.
     * This can be used to create unique folder or provide unique task name for this context.
     *
     * @return a unique [String]
     */
    abstract fun getIdentifier(): String

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

    fun createReport(): Report =
            fileProducersMap.entrySet().associate {
                it.key.name() to it.value.toProducersData()
            }

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
