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
import org.gradle.api.Project
import org.gradle.api.Task
import java.io.File
import java.util.Locale
import com.google.gson.annotations.SerializedName
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.TaskDependency
import java.io.FileReader

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
 * @param variantName name of the Variant
 * @param rootOutputDir the intermediate directories to place output files.
 * @param variantDirName the subdirectory where outputs should be placed for the variant
 */
class BuildArtifactsHolder(
    private val project: Project,
    private val variantName: String,
    private val rootOutputDir: File,
    private val variantDirName: String,
    private val dslScope: DslScope) {

    private val artifactRecordMap = mutableMapOf<ArtifactType, ArtifactRecord>()

    /**
     * Internal private class for storing [BuildableArtifact] created for a [ArtifactType]
     */
    private class ArtifactRecord(first : BuildableArtifact) {

        // A list of all BuildableFiles for the artifact.  Technically, only the last
        // BuildableFiles are needed.  Storing the all BuildableFiles allows better error
        // messages to be generated in the future.
        var history: MutableList<BuildableArtifact> = mutableListOf(first)

        /** The latest [BuildableArtifact] created for this artifact */
        var last : BuildableArtifact
            get() = history.last()
            set(value) {
                history.add(value)
            }

        val size : Int
            get() = history.size

        fun final() : BuildableArtifact {
            return object : BuildableArtifact {
                override val files: Set<File>
                    get() = last.files

                override fun isEmpty(): Boolean = last.isEmpty()
                override fun iterator(): Iterator<File> = last.iterator()
                override fun getBuildDependencies(): TaskDependency = last.buildDependencies
                override fun get(): FileCollection = last.get()
            }
        }
    }

    /**
     * Returns an appropriate task name for the variant with the given prefix.
     */
    fun getTaskName(prefix : String) : String {
        return prefix + variantName.capitalize()
    }

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
        return getArtifactRecord(artifactType).final()
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
     * Append output to the specified artifactType.
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
        val artifactRecord = artifactRecordMap[artifactType]
        val collection =
            if (artifactRecord != null) {
                project.files(newFiles, artifactRecord.last)
            } else {
                project.files(newFiles)
            }.builtBy(task)
        return _appendArtifact(artifactType, collection)
    }

    /**
     * Append existing files to a specified artifact type.
     *
     * This should only be called when files already exists during configuration time (which usually
     * is the case with source directory files) or when dependency information is embedded inside
     * the file collection.
     *
     * @param artifactType [ArtifactType] for the existing files
     * @param existingFiles existing files' [FileCollection] with
     */
    fun appendArtifact(
        artifactType: ArtifactType,
        existingFiles: FileCollection) {

        val artifactRecord = artifactRecordMap[artifactType]
        val collection =
            if (artifactRecord != null) {
                project.files(existingFiles, artifactRecord.last)
            } else {
                project.files(existingFiles)
            }
        _appendArtifact(artifactType, collection)
    }

    /**
     * Append a new file or folder to a specified artifact type.
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

    private fun _appendArtifact(artifactType: ArtifactType, files: FileCollection) : BuildableArtifact {
        val newBuildableArtifact = BuildableArtifactImpl(files, dslScope)
        createOutput(artifactType, newBuildableArtifact)
        return newBuildableArtifact
    }

    private fun createOutput(artifactType: ArtifactType, artifact: BuildableArtifact) : ArtifactRecord {
        synchronized(artifactRecordMap,  {
            var output = artifactRecordMap[artifactType]
            if (output == null) {
                output = ArtifactRecord(artifact)
                artifactRecordMap[artifactType] = output
            } else {
                output.last = artifact
            }
            return output
        })
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
            FileUtils.join(artifactType.getOutputDir(rootOutputDir),
                variantName,
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
                InternalArtifactType.Kind.INTERMEDIATES.getOutputDir(rootOutputDir),
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
        val MULTI_TYPES = "multi-types"

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
                    result.put(key.toArtifactType(), history)
                }
            }
            return result
        }
    }
}
