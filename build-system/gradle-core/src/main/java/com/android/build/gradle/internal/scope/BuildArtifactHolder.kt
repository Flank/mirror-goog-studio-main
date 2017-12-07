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
import com.android.build.api.artifact.BuildArtifactType
import com.android.build.gradle.internal.api.artifact.BuildableArtifactImpl
import com.android.builder.errors.EvalIssueReporter
import com.android.utils.FileUtils
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import java.io.File

/**
 * Buildable artifact holder.
 *
 * This class manages buildable artifacts, allowing users to transform [BuildArtifactType].
 *
 * When the class is constructed, it creates [BuildableArtifact] for all of the
 * initialArtifactTypes.  [BuildArtifactTransformBuilder] can then use these [BuildableArtifact] to
 * allow users to create transform task.  The [BuildableArtifact] will be initially empty and will
 * need to be initialized with the appropriate file and task dependencies using
 * [createFirstArtifactFiles] or [initializeFirstArtifactFiles].
 *
 * @param project the Gradle [Project]
 * @param variantName name of the Variant
 * @param rootOutputDir the intermediate directories to place output files.
 * @param variantDirName the subdirectory where outputs should be placed for the variant
 * @param initialArtifactTypes the list of artifact types to initialize the holder with.  This list
 *         determines all artifact types available to an external user.
 */
class BuildArtifactHolder(
        private val project : Project,
        private val variantName : String,
        private val rootOutputDir : File,
        private val variantDirName : String,
        initialArtifactTypes: List<BuildArtifactType>,
        private val issueReporter: EvalIssueReporter) {

    private val artifactRecordMap =
            initialArtifactTypes.associate{
                it to ArtifactRecord(BuildableArtifactImpl(null, issueReporter))
            }

    /**
     * Internal private class for storing [BuildableArtifact] created for a [BuildArtifactType]
     */
    private class ArtifactRecord(first : BuildableArtifactImpl) {
        // Artifact is initialized when setFirst is called.
        var initialized = false

        // A list of all BuildableFiles for the artifact.  Technically, only the first and the
        // last BuildableFiles are needed.  Storing the all BuildableFiles allows better error
        // messages to be generated in the future.
        var history: MutableList<BuildableArtifact> = mutableListOf(first)

        /** The latest [BuildableArtifact] created for this artifact */
        var last : BuildableArtifact
            get() = history.last()
            set(value) {
                history.add(value)
            }

        /** The first [BuildableArtifact] created for this artifact */
        val first : BuildableArtifact
            get() = history.first()

        fun modifyFirst(collection: FileCollection) {
            initialized = true
            (history.first() as BuildableArtifactImpl).fileCollection = collection
        }
    }

    /**
     * Returns an appropriate task name for the variant with the given prefix.
     */
    fun getTaskName(prefix : String) : String {
        return prefix + variantName.capitalize()
    }

    /**
     * Returns the [BuildableArtifact] associated with the artifactType.
     *
     * @param artifactType the requested artifactType.
     * @throws MissingTaskOutputException if the artifactType is not created.
     */
    fun getArtifactFiles(artifactType : ArtifactType) : BuildableArtifact {
        val output = artifactRecordMap[artifactType]
                ?: throw MissingBuildableArtifactException(artifactType)
        return output.last
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
     * @param filenames names of the new files.
     * @param taskName name of the Task that will create the new files.
     * @return BuildableFiles containing files that the specified task should create.
     */
    fun replaceArtifact(
            artifactType: ArtifactType,
            filenames : Collection<String>,
            taskName : String)
            : BuildableArtifact {
        val collection = project.files(filenames.map{ createFile(taskName, it)}).builtBy(taskName)
        val files = BuildableArtifactImpl(collection, issueReporter)
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
     * @param artifactType artifactType that new files will be associated with.
     * @param filenames names of the new files.
     * @param taskName name of the Task that will create the new files.
     * @return BuildableFiles containing files that the specified task should create.
     */
    fun appendArtifact(
            artifactType: ArtifactType,
            filenames : Collection<String>,
            taskName : String)
            : BuildableArtifact {
        val originalOutput = getArtifactFiles(artifactType)
        val collection =
                project.files(
                        filenames.map{ createFile(taskName, it) },
                        originalOutput)
                        .builtBy(taskName, originalOutput)
        val files = BuildableArtifactImpl(collection, issueReporter)
        createOutput(artifactType, files)
        return files
    }

    private fun createOutput(artifactType: ArtifactType, artifact: BuildableArtifact) {
        val output = artifactRecordMap[artifactType]
                ?: throw MissingBuildableArtifactException(artifactType)
        output.last = artifact
    }

    /**
     * Create Files and modify the first [BuildableArtifact] for the artifactType.
     *
     * When a [BuildArtifactHolder], a set of [BuildableArtifact] are created, but not populated.
     * The content of the [BuildableArtifact] should come from a source from the Android Gradle plugin.
     * This method modifies the initial [BuildableArtifact] with the appropriate files.
     *
     * @artifactType type of the artifact
     * @filenames names of the files to be created.
     * @taskName name of the Task that will generate the files.
     * @return the initial [BuildableArtifact] that is now populated with the new files.
     */
    fun createFirstArtifactFiles(
            artifactType: ArtifactType, filenames : Collection<String>, taskName : String)
            : BuildableArtifact {
        val collection = project.files(filenames.map{ createFile(artifactType.name(), it)})
        collection.builtBy(taskName)
        return initializeFirstArtifactFiles(artifactType, collection)
    }

    /**
     * Same as the other [createFirstArtifactFiles], but for the a single file.
     *
     * @artifactType type of the artifact.
     * @filename name of the file to be created.
     * @taskName name of the Task that will generate the output file.
     * @return the initial [BuildableArtifact] that is now populated with the new file.
     */
    fun createFirstArtifactFiles(artifactType: ArtifactType, filename : String, taskName : String)
            : BuildableArtifact {
        val collection = project.files(createFile(artifactType.name(), filename))
        collection.builtBy(taskName)
        return initializeFirstArtifactFiles(artifactType, collection)
    }

    /**
     * Initialize the first output with the specified FileCollection.
     *
     * Similar to createFirstArtifactFiles, this method modifies the content of the
     * [BuildableArtifact] that were created during construction of [BuildArtifactHolder].  Unlike
     * createFirstOutput, this methods does not determine the location of the generated files.
     * Instead, a FileCollection has to be supplied.  This allows outputs not created from a task to
     * be added (e.g. Configuration, SourceSet, etc).
     */
    private fun initializeFirstArtifactFiles(
            artifactType: ArtifactType, collection : FileCollection)
            : BuildableArtifact {
        val output = artifactRecordMap[artifactType]
                ?: throw MissingBuildableArtifactException(artifactType)
        if (output.initialized) {
            throw RuntimeException("Artifact already registered for type: $artifactType")
        }
        output.modifyFirst(collection)
        return output.first
    }

    /**
     * Creates a File for a task.
     *
     * @param taskName taskName
     * @param filename name of the file.
     */
    internal fun createFile(taskName: String, filename : String) =
            FileUtils.join(
                    rootOutputDir,
                    taskName,
                    variantDirName,
                    filename)

    /**
     * Return history of all [BuildableArtifact] for an [ArtifactType].
     */
    //FIXME: VisibleForTesting.  Make this internal when bazel supports it for tests (b/71602857)
    fun getHistory(artifactType: ArtifactType) : List<BuildableArtifact> {
        val record = artifactRecordMap[artifactType]
                ?: throw MissingBuildableArtifactException(artifactType)
        return record.history
    }
}
