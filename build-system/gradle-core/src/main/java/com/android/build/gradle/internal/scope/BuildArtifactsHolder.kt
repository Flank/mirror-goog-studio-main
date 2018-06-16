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
import com.android.build.api.artifact.BuildArtifactTransformBuilder
import com.android.build.api.artifact.BuildableArtifact
import com.android.build.gradle.internal.api.artifact.BuildableArtifactImpl
import com.android.build.gradle.internal.api.artifact.toArtifactType
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.utils.FileUtils
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskDependency
import java.io.File
import java.io.FileReader
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

typealias Report = Map<ArtifactType, List<BuildArtifactsHolder.BuildableArtifactData>>

/**
 * Buildable artifact holder.
 *
 * This class manages buildable artifacts, allowing users to transform [ArtifactType].
 *
 * [BuildArtifactTransformBuilder] can then use these [BuildableArtifact] to
 * allow users to create transform task.
 *
 * @param project the Gradle [Project]
 * @param rootOutputDir a supplier for the intermediate directories to place output files.
 * @parma dslScope the scope for dsl parsing issue raising.
 */
abstract class BuildArtifactsHolder(
    private val project: Project,
    private val rootOutputDir: () -> File,
    private val dslScope: DslScope) {

    private val artifactRecordMap = ConcurrentHashMap<ArtifactType, ArtifactRecord>()
    private val finalArtifactsMap = ConcurrentHashMap<ArtifactType, BuildableArtifact>()

    /**
     * Internal private class for storing [BuildableArtifact] created for a [ArtifactType]
     */
    private class ArtifactRecord {

        // A list of all BuildableFiles for the artifact.  Technically, only the last
        // BuildableFiles are needed.  Storing the all BuildableFiles allows better error
        // messages to be generated in the future.
        val history: MutableList<BuildableArtifact> = mutableListOf()

        // last registered task that produced a [BuildableArtifact] for this artifact type record.
        var lastProducer: BuildableProducer? = null

        /** The latest [BuildableArtifact] created for this artifact */
        var last : BuildableArtifact
            get() = history.last()
            set(value) {
                add(value, null)
            }

        // adds a new [BuildableArtifact] with the producer information
        fun add(buildableArtifact: BuildableArtifact, producer: BuildableProducer?) {
            history.add(buildableArtifact)
            this.lastProducer = producer
        }

        val size : Int
            get() = history.size
    }

    // unless a [BuildableArtifact] or [FileCollection] was used to initialize a new instance of
    // [BuildableArtifact], the task as well as the Provider<> producing the file or folder
    // associated with the new BuildableArtifact will be recorded here.
    private data class BuildableProducer(
        val fileOrDirProperty: Property<in FileSystemLocation>,
        val task: Task,
        val fileName: String)

    private class FinalBuildableArtifact(
        val artifactType: ArtifactType,
        val artifacts: BuildArtifactsHolder) : BuildableArtifact {
        override val files: Set<File>
            get() = final().files
        override fun isEmpty(): Boolean = final().isEmpty()
        override fun iterator(): Iterator<File> = final().iterator()
        override fun getBuildDependencies(): TaskDependency = final().buildDependencies
        override fun get(): FileCollection = final().get()
        private fun final() : BuildableArtifact = artifacts.getArtifactRecord(artifactType).last
    }

    /**
     * Returns an appropriate task name for the variant with the given prefix.
     */
    fun getTaskName(prefix : String) : String {
        return prefix + getIdentifier().capitalize()
    }

    /**
     * Returns a identifier that will uniquely identify this artifacts holder against other holders.
     * This can be used to create unique folder or provide unique task name for this context.
     *
     * @return a unique [String]
     */
    abstract fun getIdentifier(): String

    /**
     * Returns the current [BuildableArtifact] associated with the artifactType.
     * When a chain of tasks are registered to append or replace the artifact, this API will return
     * the current (possibly intermediary) version of the BuildableArtifact at the time of this
     * call.
     *
     * @param artifactType the requested artifact type.
     * @return the possibly empty [BuildableArtifact] for this artifact type.
     */
    fun getArtifactFiles(artifactType: ArtifactType): BuildableArtifact {
        return getArtifactRecord(artifactType).last
    }

    /**
     * Returns the final [BuildableArtifact] associated with the artifactType.
     * Irrespective of the timing of this method call, it will always return the final version of
     * the [BuildableArtifact] for the passed artifact type.
     *
     * This should not be used to transform further the artifact type.
     *
     * @param artifactType the requested [BuildableArtifact] artifact type.
     * @return the possibly empty final [BuildableArtifact] for this artifact type.
     */
    fun getFinalArtifactFiles(artifactType: ArtifactType) : BuildableArtifact {
        return finalArtifactsMap.computeIfAbsent(artifactType,
            { FinalBuildableArtifact(artifactType, this) })
    }

    /**
     * Returns the final [BuildableArtifact] associated with the artifactType or an empty
     * BuildableArtifact if no [BuildableArtifact] has been registered for this artifact type.
     *
     * Irrespective of the timing of this method call, it will always return the final version of
     * the [BuildableArtifact] for the passed artifact type or an empty one.
     *
     * This should not be used to transform further the artifact type.
     *
     * @param artifactType the requested [BuildableArtifact] artifact type.
     * @return the possibly empty final [BuildableArtifact] for this artifact type.
     */

    fun getOptionalFinalArtifactFiles(artifactType: ArtifactType): BuildableArtifact {
        return if (hasArtifact(artifactType)) {
            getFinalArtifactFiles(artifactType)
        } else {
            BuildableArtifactImpl(project.files(), dslScope)
        }
    }

    /**
     * Returns the final [BuildableArtifact] associated with the `artifactType` or `null` if no
     * [BuildableArtifact] has been registered for this artifact type.
     *
     * See [getFinalArtifactFiles] for more details.
     */
    fun getFinalArtifactFilesIfPresent(artifactType: ArtifactType): BuildableArtifact? {
        return if (hasArtifact(artifactType)) {
            getFinalArtifactFiles(artifactType)
        } else {
            null
        }
    }

    /**
     * Returns whether the artifactType exists in the holder.
     */
    fun hasArtifact(artifactType: ArtifactType) : Boolean {
        return artifactRecordMap.containsKey(artifactType)
    }

    /**
     * Replaces the output of the specified artifactType.
     *
     * This method allows files associated with the artifactType to be replaced such that subsequent
     * call to [getArtifactFiles] will return the newly created files.
     * The path of File are created from the supplied filenames and the name of the Task that will
     * generate these files.
     *
     * @param artifactType artifactType to be replaced.
     * @param newFiles names of the new files.
     * @param task [Task] that will create the new files.
     * @return BuildableFiles containing files that the specified task should create.
     */
    fun replaceArtifact(
            artifactType: ArtifactType,
            newFiles : Collection<File>,
            task : Task) : BuildableArtifact {
        val collection = project.files(newFiles).builtBy(task)
        val files = BuildableArtifactImpl(collection, dslScope)
        createOutput(artifactType, files)
        return files
    }

    /**
     * Append output to the specified artifactType. The [newFiles] will be added after any
     * existing content.
     *
     * After invoking this method, [getArtifactFiles] will return a [BuildableArtifact] that
     * contains both the new files and the original files.
     * The path of File are created from the supplied filenames and the name of the Task that will
     * generate these files.
     *
     * @param artifactType [ArtifactType] the new files will be classified under.
     * @param newFiles names of the new files.
     * @param task [Task] that responsible for producing or updating the new files.
     * @return BuildableFiles containing files that the specified task should create.
     */
    fun appendArtifact(
            artifactType: ArtifactType,
            newFiles : Collection<File>,
            task : Task) : BuildableArtifact {
        return doAppendArtifact(artifactType,
            createFileCollection(artifactRecordMap[artifactType], newFiles).builtBy(task))
    }

    /**
     * Append existing files to a specified artifact type. The [existingFiles] will be added after
     * any existing content.
     *
     * This should only be called when files already exists during configuration time (which usually
     * is the case with source directory files) or when dependency information is embedded inside
     * the file collection.
     *
     * @param artifactType [ArtifactType] for the existing files
     * @param existingFiles existing files' [FileCollection] with
     */
    fun appendArtifact(artifactType: ArtifactType, existingFiles: FileCollection) {
        doAppendArtifact(artifactType,
            createFileCollection(artifactRecordMap[artifactType], existingFiles))
    }
    /**
     * Append existing BuildableArtifact to a specified artifact type. The new content will be added
     * after any existing content.
     *
     * @param artifactType [ArtifactType] for the existing files
     * @param buildableArtifact existing [BuildableArtifact] holding files and dependencies
     */
    fun appendArtifact(artifactType: ArtifactType, buildableArtifact: BuildableArtifact) {
        doAppendArtifact(artifactType,
            createFileCollection(artifactRecordMap[artifactType], buildableArtifact))
    }

    /**
     * Append a new file or folder to a specified artifact type. The new content will be added
     * after any existing content.
     *
     * @param artifactType [ArtifactType] the new file or folder will be classified under.
     * @param task [Task] producing the file or folder.
     * @param fileName expected file name for the file (location is determined by the build)
     * @return [File] handle to use to create the file or folder (potentially with subfolders
     * or multiple files)
     */
    fun appendArtifact(
        artifactType: ArtifactType,
        task : Task,
        fileName: String = "out") : File {
        val output = createFile(task, artifactType, fileName)
        appendArtifact(artifactType, listOf(output), task)
        return output
    }

    /**
     * set a new file to a specified artifact type. The new content will be added
     * after any existing content.
     *
     * @param artifactType [ArtifactType] the new file will be classified under.
     * @param task [Task] producing the file.
     * @param fileName expected file name for the file (location is determined by the build)
     * @return [Provider<RegularFile] object that will resolve during execution phase to the final
     * location to write the [task] output to.
     */
    fun setArtifactFile(
        artifactType: ArtifactType,
        task : Task,
        fileName: String = "out") : Provider<RegularFile> =
        setArtifactFile(artifactType, task, File(FileUtils.join(
            artifactType.getOutputPath(),
            artifactType.name().toLowerCase(),
            getIdentifier(),
            fileName)))

    /**
     * set a new file to a specified artifact type. The new content will be added
     * after any existing content.
     *
     * @param artifactType [ArtifactType] the new file will be classified under.
     * @param task [Task] producing the file.
     * @param requestedFileLocation expected location for the file
     * @return [Provider<RegularFile] object that will resolve during execution phase to the final
     * location to write the [task] output to.
     */
    fun setArtifactFile(
        artifactType: ArtifactType,
        task : Task,
        requestedFileLocation: File) : Provider<RegularFile> {

        // TODO : split this method in 2, replaceArtifactFile, and setArtifactFile.

        val artifactRecord = artifactRecordMap[artifactType]
        val intermediatesOutput = InternalArtifactType.Category.INTERMEDIATES.outputPath

        val lastProducer = artifactRecord?.lastProducer
        lastProducer?.fileOrDirProperty?.set(project.layout.buildDirectory.file(
            FileUtils.join(
                intermediatesOutput,
                artifactType.name().toLowerCase(),
                getIdentifier(),
                lastProducer.task.name,
                lastProducer.fileName))
        )

        val provider = project.layout.buildDirectory.file(
            if (artifactRecord == null || artifactType.getOutputPath() != intermediatesOutput) {
                requestedFileLocation.path
            } else {
                FileUtils.join(
                    intermediatesOutput,
                    artifactType.name().toLowerCase(),
                    getIdentifier(),
                    task.name,
                    requestedFileLocation.name
                )
            })


        val fileProperty = project.layout.fileProperty(provider)
        createOutput(artifactType,
            BuildableArtifactImpl(
                project.files(fileProperty).builtBy(task),
                dslScope),
            @Suppress("UNCHECKED_CAST")
            BuildableProducer(
                fileProperty as Property<in FileSystemLocation>,
                task,
                requestedFileLocation.name))
        return fileProperty
    }

    private fun createFileCollection(artifactRecord: ArtifactRecord?, newFiles: Any) =
        if (artifactRecord != null) {
            project.files(artifactRecord.last, newFiles)
        } else {
            project.files(newFiles)
        }

    private fun doAppendArtifact(type: ArtifactType, files: FileCollection) : BuildableArtifact {
        val newBuildableArtifact = BuildableArtifactImpl(files, dslScope)
        createOutput(type, newBuildableArtifact)
        return newBuildableArtifact
    }

    private fun createOutput(
        type: ArtifactType,
        artifact: BuildableArtifact,
        producer: BuildableProducer? = null) : ArtifactRecord {

        synchronized(artifactRecordMap) {
            val output = artifactRecordMap.computeIfAbsent(type) { ArtifactRecord() }
            output.add(artifact, producer)
            return output
        }
    }

    private fun getArtifactRecord(artifactType : ArtifactType) : ArtifactRecord {
        return artifactRecordMap[artifactType] ?:
        createOutput(artifactType, BuildableArtifactImpl(project.files(), dslScope))
    }

    fun createReport() : Report =
            artifactRecordMap.entries.associate {artifactRecordMap
                it.key to it.value.history.map(this::newArtifact)
            }

    /**
     * Create a File for a task and artifact type.
     * @param task the task the file should be created for.
     * @param artifactType artifact type that will be associated with the file.
     * @param filename desired file name.
     */
    internal fun createFile(task: Task, artifactType: ArtifactType, filename: String) =
            FileUtils.join(artifactType.getOutputDir(rootOutputDir()),
                getIdentifier(),
                task.name,
                filename)


    /**
     * Creates a File for a task. Prefer [createFile] when artifact type is known and unique for
     * the output file. This will stuff all the tasks directly under "multi-types" leading to
     * potentially confusing directory structure.
     *
     * @param task the task creating the output file.
     * @param filename name of the file.
     */
    internal fun createFile(task: Task, filename : String) =
            FileUtils.join(
                InternalArtifactType.Category.INTERMEDIATES.getOutputDir(rootOutputDir()),
                MULTI_TYPES,
                task.name,
                filename)

    internal fun getArtifactFilename(artifactType: ArtifactType) : String {
        val record = getArtifactRecord(artifactType)
        return artifactType.name().toLowerCase(Locale.US) + record.size.toString()
    }

    /**
     * Return history of all [BuildableArtifact] for an [ArtifactType].
     */
    internal fun getHistory(artifactType: ArtifactType) : List<BuildableArtifact> {
        val record = getArtifactRecord(artifactType)
        return record.history
    }

    /** A data class for use with GSON. */
    data class BuildableArtifactData(
        @SerializedName("files") var files : Collection<File>,
        @SerializedName("builtBy") var builtBy : List<String>)

    /** Create [BuildableArtifactData] from [BuildableArtifact]. */
    private fun newArtifact(artifact : BuildableArtifact) =
            // getDependencies accepts null.
        @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
        BuildableArtifactData(
                artifact.files,
                artifact.buildDependencies.getDependencies(null).map(Task::getPath))

    companion object {
        const val MULTI_TYPES = "multi-types"

        fun parseReport(file : File) : Report {
            val result = mutableMapOf<ArtifactType, List<BuildableArtifactData>>()
            val parser = JsonParser()
            FileReader(file).use { reader ->
                for ((key, value) in parser.parse(reader).asJsonObject.entrySet()) {
                    val history =
                            value.asJsonArray.map {
                                val obj = it.asJsonObject
                                BuildableArtifactData(
                                        obj.getAsJsonArray("files").map {
                                            File(it.asJsonObject.get("path").asString)
                                        },
                                        obj.getAsJsonArray("builtBy").map(
                                                JsonElement::getAsString))
                            }
                    result[key.toArtifactType()] = history
                }
            }
            return result
        }
    }
}
