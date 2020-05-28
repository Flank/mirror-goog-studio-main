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

import org.gradle.api.Incubating
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.FileSystemLocationProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider

@Incubating
interface TaskBasedOperations<TaskT : Task> {

    /**
     * Initiates an append request to a [Artifact.Multiple] artifact type.
     *
     * @param type the [Artifact] of [FileTypeT] identifying the artifact to append to.
     * @param with the method reference to get the [FileSystemLocationProperty] to retrieve the
     * produced [FileTypeT] when needed.
     *
     * The artifact type must be [Artifact.Multiple] and [Artifact.Appendable]
     *
     * Let's take a [Task] with a [org.gradle.api.file.RegularFile] output :
     * <pre>
     *     abstract class MyTask: DefaultTask() {
     *          @get:OutputFile abstract val outputFile: RegularFileProperty
     *
     *          @TaskAction fun taskAction() {
     *              ... outputFile.get().asFile.write( ... ) ...
     *          }
     *     }
     * </pre>
     *
     * and an ArtifactType defined as follows :
     *
     * <pre>
     *     sealed class ArtifactType<T: FileSystemLocation>(val kind: ArtifactKind) {
     *          object MULTIPLE_FILE_ARTIFACT:
     *                  ArtifactType<RegularFile>(FILE), Multiple, Appendable
     *     }
     * </pre>
     *
     * you can then register the above task as a Provider of [org.gradle.api.file.RegularFile] for
     * that artifact type.
     *
     * <pre>
     *     val taskProvider= projects.tasks.register(MyTask::class.java, "appendTask")
     *     artifacts.use(taskProvider).toAppend(
     *          ArtifactType.MULTIPLE_FILE_ARTIFACT
     *          MyTask::outputFile)
     * </pre>
     */
    fun <FileTypeT : FileSystemLocation, ArtifactTypeT> toAppend(
        type: ArtifactTypeT,
        with: (TaskT) -> FileSystemLocationProperty<FileTypeT>
    ) where ArtifactTypeT : Artifact<FileTypeT>,
            ArtifactTypeT : Artifact.Appendable

    /**
     * Initiates a replacement request to a single [Artifact.Replaceable] artifact type.
     *
     * @param type  the [Artifact] of [FileTypeT] identifying the artifact to replace.
     * @param with the method reference to obtain the [Provider] for the produced [FileTypeT]
     *
     * The artifact type must be [Artifact.Replaceable]
     *
     * A replacement request does not care about the existing producer as it replaces it. Therefore
     * the existing producer will not execute.
     * Please note that when such replace requests are made, the TASK will replace initial AGP
     * providers.
     *
     * You cannot replace [Artifact.Multiple] artifact type, therefore you must instead combine
     * it using the [TaskBasedOperations.toTransformAll] API.
     *
     * Let's take a [Task] with a [org.gradle.api.file.RegularFile] output :
     *
     * <pre>
     *     abstract class MyTask: DefaultTask() {
     *          @get:OutputFile abstract val outputFile: RegularFileProperty
     *
     *          @TaskAction fun taskAction() {
     *              ... write outputFile ...
     *          }
     *     }
     * </pre>
     *
     * and an ArtifactType defined as follows :
     *
     * <pre>
     *     sealed class ArtifactType<T: FileSystemLocation>(val kind: ArtifactKind) {
     *          object SINGLE_FILE_ARTIFACT:
     *                  ArtifactType<RegularFile>(FILE), Replaceable
     *     }
     * </pre>
     *
     * you can register a transform to the collection of [org.gradle.api.file.RegularFile]
     *
     * <pre>
     *     val taskProvider= projects.tasks.register(MyTask::class.java, "replaceTask")
     *     artifacts.use(taskProvider).toReplace(
     *          ArtifactType.SINGLE_FILE_ARTIFACT
     *          MyTask::outputFile)
     * </pre>
     */
    fun <FileTypeT : FileSystemLocation, ArtifactTypeT> toReplace(
        type: ArtifactTypeT,
        with: (TaskT) -> FileSystemLocationProperty<FileTypeT>
    ) where ArtifactTypeT : Artifact<FileTypeT>,
            ArtifactTypeT : Artifact.Replaceable

    /**
     * Initiates a transform request to a single [Artifact.Transformable] artifact type.
     *
     * @param type  the [Artifact] of [FileTypeT] identifying the artifact to transform.
     * @param from the method reference to get a [Property] of [FileTypeT] to set the transform input
     * @param into the method reference to get the [Property] of [FileTypeT] to retrieve the
     * produced [FileTypeT] when needed.
     *
     * The artifact type must be [Artifact.Single] and [Artifact.Transformable]
     *
     * Let's take a [Task] transforming an input [org.gradle.api.file.RegularFile] into an
     * output :
     * <pre>
     *     abstract class MyTask: DefaultTask() {
     *          @get:InputFile abstract val inputFile: RegularFileProperty
     *          @get:OutputFile abstract val outputFile: RegularFileProperty
     *
     *          @TaskAction fun taskAction() {
     *              ... read inputFile and write outputFile ...
     *          }
     *     }
     * </pre>
     *
     * and an ArtifactType defined as follows :
     *
     * <pre>
     *     sealed class ArtifactType<T: FileSystemLocation>(val kind: ArtifactKind) {
     *          object SINGLE_FILE_ARTIFACT:
     *                  ArtifactType<RegularFile>(FILE), Single, Transformable
     *     }
     * </pre>
     *
     * you can register a transform to the collection of [org.gradle.api.file.RegularFile]
     *
     * <pre>
     *     val taskProvider= projects.tasks.register(MyTask::class.java, "transformTask")
     *     artifacts.use(taskProvider).toTransform(
     *          ArtifactType.SINGLE_FILE_ARTIFACT
     *          MyTask::inputFile,
     *          MyTask::outputFile)
     * </pre>
     */
    fun <FileTypeT : FileSystemLocation, ArtifactTypeT> toTransform(
        type: ArtifactTypeT,
        from: (TaskT) -> FileSystemLocationProperty<FileTypeT>,
        into: (TaskT) -> FileSystemLocationProperty<FileTypeT>
    )
            where ArtifactTypeT : Artifact<FileTypeT>,
                  ArtifactTypeT : Artifact.Single,
                  ArtifactTypeT : Artifact.Transformable

    /**
     * Initiates a transform request to a multiple [Artifact.Transformable] artifact type.
     *
     * @param type  the [Artifact] of [FileTypeT] identifying the artifact to transform.
     * @param from the method reference to get a [ListProperty] to set all the transform inputs
     * @param into the method reference to get the [Property] to retrieve the produced [FileTypeT]
     * when needed.
     *
     * The artifact type must be [Artifact.Multiple] and [Artifact.Transformable]
     *
     * The implementation of the task must combine all the inputs returned [from] the method
     * reference and store [into] a single output.
     * Chained transforms will get a list of a single output from the upstream transforms.
     *
     * If some [append] calls are made on the same artifact type, the first transform will always
     * get the complete list of artifacts irrespective of the timing of the calls.
     *
     * Let's take a [Task] to transform a list of [org.gradle.api.file.RegularFile] as inputs into
     * a single output :
     * <pre>
     *     abstract class MyTask: DefaultTask() {
     *          @get:InputFiles abstract val inputFiles: ListProperty<RegularFile>
     *          @get:OutputFile abstract val outputFile: RegularFileProperty
     *
     *          @TaskAction fun taskAction() {
     *              ... read all inputFiles and write outputFile ...
     *          }
     *     }
     * </pre>
     *
     * and an ArtifactType defined as follows :
     *
     * <pre>
     *     sealed class ArtifactType<T: FileSystemLocation>(val kind: ArtifactKind) {
     *          object MULTIPLE_FILE_ARTIFACT:
     *                  ArtifactType<RegularFile>(FILE), Multiple, Transformable
     *     }
     * </pre>
     *
     * you then register the task as follows :
     *
     * <pre>
     *     val taskProvider= projects.tasks.register(MyTask::class.java, "combineTask")
     *     artifacts.use(taskProvider).toTransformAll(
     *          ArtifactType.MULTIPLE_FILE_ARTIFACT
     *          MyTask::inputFiles,
     *          MyTask::outputFile)
     * </pre>
     */
    fun <FileTypeT : FileSystemLocation, ArtifactTypeT> toTransformAll(
        type: ArtifactTypeT,
        from: (TaskT) -> ListProperty<FileTypeT>,
        into: (TaskT) -> FileSystemLocationProperty<FileTypeT>
    )
            where ArtifactTypeT : Artifact<FileTypeT>,
                  ArtifactTypeT : Artifact.Multiple,
                  ArtifactTypeT : Artifact.Transformable

    /**
     * Initiates a transform request to a single [Artifact.Transformable] artifact type that can
     * contains more than one artifact.
     *
     * @param type  the [Artifact] of [Directory] identifying the artifact to transform.
     * @param from the method reference to get a [DirectoryProperty] to set current provider.
     * @param into the method reference to get the [DirectoryProperty] to retrieve the produced
     * [Directory] when needed.
     *
     * The artifact type must be [Artifact.Single] and [Artifact.Transformable]
     * and [Artifact.ContainsMany]
     *
     * The implementation of the task must combine all the inputs returned [from] the method
     * reference and store [into] a single output.
     * Chained transforms will get a list of a single output from the upstream transforms.
     *
     * If some [append] calls are made on the same artifact type, the first transform will always
     * get the complete list of artifacts irrespective of the timing of the calls.
     *
     * Let's take a [Task] to transform a list of [org.gradle.api.file.RegularFile] as inputs into
     * a single output :
     * <pre>
     *     abstract class MyTask: DefaultTask() {
     *          @get:InputFiles abstract val inputFolder: DirectoryProperty
     *          @get:OutputFile abstract val outputFolder: DirectoryProperty
     *          @Internal abstract Property<ArtifactTransformationRequest<MyTask>> getTransformationRequest()
     *
     *          @TaskAction fun taskAction() {
     *             transformationRequest.get().submit(
     *                  ... submit a work item for each input file ...
     *             )
     *          }
     *     }
     * </pre>
     **
     * you then register the task as follows :
     *
     * <pre>
     *     val taskProvider= projects.tasks.register(MyTask::class.java, "combineTask")
     *     artifacts.use(taskProvider).toTransformAll(
     *          ArtifactType.APK,
     *          MyTask::inputFolder,
     *          MyTask::outputFolder)
     * </pre>
     */
    fun <ArtifactTypeT> toTransformMany(
        type: ArtifactTypeT,
        from: (TaskT) -> DirectoryProperty,
        into: (TaskT) -> DirectoryProperty
    ): ArtifactTransformationRequest<TaskT>
            where ArtifactTypeT : Artifact<Directory>,
                  ArtifactTypeT : Artifact.Single,
                  ArtifactTypeT : Artifact.ContainsMany,
                  ArtifactTypeT : Artifact.Transformable
}