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

package com.android.build.api.artifact

import com.android.build.api.variant.BuiltArtifactsLoader
import org.gradle.api.Incubating
import org.gradle.api.Task
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.FileSystemLocationProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider

/**
 * Access to the artifacts on a Variant object.
 *
 * Artifacts are temporary or final files or directories that are produced by the Android Gradle
 * Plugin during the build. Depending on its configuration, each [Variant] produces different
 * versions of some of the output artifacts.
 *
 * Example of temporary artifacts are .class files obtained from compiling source files that will
 * eventually get transformed further into dex files. Final artifacts are APKs and bundle files that
 * are not transformed further.
 *
 * Artifacts are uniquely defined by their [ArtifactType] and public artifact types that can be
 * accessed from third party plugins or build script are defined in [ArtifactTypes]
 *
 * @since 4.1
 */
@Incubating
interface Artifacts {

    /**
     * Provides an implementation of [BuiltArtifactsLoader] that can be used to load built artifacts
     * metadata.
     *
     * @return a thread safe implementation pf [BuiltArtifactsLoader] that can be reused.
     */
    fun getBuiltArtifactsLoader(): BuiltArtifactsLoader

    /**
     * Get the [Provider] of [FILE_TYPE] for the passed [Artifact].
     *
     * The [Artifact] must be of the [FILE_TYPE] and [Artifact.Single]
     */
    fun <FILE_TYPE: FileSystemLocation, ARTIFACT_TYPE> get(type: ARTIFACT_TYPE): Provider<FILE_TYPE>
            where ARTIFACT_TYPE: ArtifactType<out FILE_TYPE>, ARTIFACT_TYPE: Artifact.Single

    /**
     * Get all the [Provider]s of [FILE_TYPE] for the passed [Artifact].
     *
     * The [Artifact] must be [Artifact.Multiple]
     */
    fun <FILE_TYPE: FileSystemLocation, ARTIFACT_TYPE> getAll(type: ARTIFACT_TYPE): Provider<List<FILE_TYPE>>
            where ARTIFACT_TYPE : ArtifactType<FILE_TYPE>, ARTIFACT_TYPE : Artifact.Multiple

    /**
     * Initiates an append request to a [Artifact.Multiple] artifact type.
     *
     * @param taskProvider the [TaskProvider] for the task producing an instance of [FILE_TYPE]
     * @param with the method reference to get the [FileSystemLocationProperty] to retrieve the
     * produced [FILE_TYPE] when needed.
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
     * <pre
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
     *     artifacts.append(taskProvider, MyTask::outputFile)
     *              .on(ArtifactType.MULTIPLE_FILE_ARTIFACT)
     * </pre>
     *
     * @return an [AppendRequest] to finish the append request.
     */
    fun <TASK: Task, FILE_TYPE: FileSystemLocation> append(
        taskProvider: TaskProvider<TASK>,
        with: (TASK)-> FileSystemLocationProperty<FILE_TYPE>
    ): AppendRequest<FILE_TYPE>

    /**
     * Initiates a transform request to a single [ArtifactType.Transformable] artifact type.
     *
     * @param taskProvider the [TaskProvider] for the task transforming an instance of [FILE_TYPE]
     * @param from the method reference to get a [Property] of [FILE_TYPE] to set the transform input
     * @param into the method reference to get the [Property] of [FILE_TYPE] to retrieve the
     * produced [FILE_TYPE] when needed.
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
     * <pre
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
     *     artifacts.transform(taskProvider, MyTask::inputFile, MyTask::outputFile)
     *              .on(ArtifactType.SINGLE_FILE_ARTIFACT)
     * </pre>
     *
     * @return an instance of [TransformRequest] that can be used to specify the artifact type.
     */
    fun <TASK: Task, FILE_TYPE: FileSystemLocation> transform(
        taskProvider: TaskProvider<TASK>,
        from: (TASK)-> FileSystemLocationProperty<FILE_TYPE>,
        into: (TASK) -> FileSystemLocationProperty<FILE_TYPE>
    ): TransformRequest<FILE_TYPE>

    /**
     * Initiates a transform request to a multiple [ArtifactType.Transformable] artifact type.
     *
     * @param taskProvider the [TaskProvider] for the task transforming an instance of [FILE_TYPE]
     * @param from the method reference to get a [ListProperty] to set all the transform inputs
     * @param into the method reference to get the [Property] to retrieve the produced [FILE_TYPE]
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
     * <pre
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
     *     artifacts.transformAll(taskProvider, MyTask::inputFiles, MyTask::outputFile)
     *              .on(ArtifactType.MULTIPLE_FILE_ARTIFACT)
     * </pre>
     *
     * @return an instance of [TransformRequest] that can be used to specify the artifact type.
     */
    fun <TASK: Task, FILE_TYPE: FileSystemLocation> transformAll(
        taskProvider: TaskProvider<TASK>,
        from: (TASK)-> ListProperty<FILE_TYPE>,
        into: (TASK) -> FileSystemLocationProperty<FILE_TYPE>
    ): MultipleTransformRequest<FILE_TYPE>

    /**
     * Access [Task] based operations.
     *
     * @param taskProvider the [TaskProvider] for the [TASK] that will be producing and or
     * consuming artifact types.
     * @return a [TaskBasedOperations] using the passed [TaskProvider] for all its operations/
     */
    fun <TASK: Task> use(
        taskProvider: TaskProvider<TASK>
    ): TaskBasedOperations<TASK>

    /**
     * Initiates a replacement request to a single [ArtifactType.Replaceable] artifact type.
     *
     * @param taskProvider a [TaskProvider] for the task producing an instance of [FILE_TYPE]
     * @param with the method reference to obtain the [Provider] for the produced [FILE_TYPE]
     *
     * The artifact type must be [Artifact.Replaceable]
     *
     * A replacement request does not care about the existing producer as it replaces it. Therefore
     * the existing producer will not execute.
     * Please note that when such replace requests are made, the TASK will replace initial AGP
     * providers.
     *
     * You cannot replace [Artifact.Multiple] artifact type, therefore you must instead combine
     * it using the [Artifacts.transformAll] API.
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
     * <pre
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
     *     artifacts.replace(taskProvider, MyTask::outputFile)
     *              .on(ArtifactType.SINGLE_FILE_ARTIFACT)
     * </pre>
     */
    fun <TASK: Task, FILE_TYPE: FileSystemLocation> replace(
        taskProvider: TaskProvider<TASK>,
        with: (TASK)-> FileSystemLocationProperty<FILE_TYPE>
    ): ReplaceRequest<FILE_TYPE>
}

/**
 * A transform request on a single [FILE_TYPE] abstraction.
 */
@Incubating
interface TransformRequest<FILE_TYPE: FileSystemLocation> {
    /**
     * Specifies the artifact type this single file transform request applies to.
     * @param type the artifact type which must be of the right [FILE_TYPE], but also
     * [Artifact.Transformable] and [Artifact.Single]
     */
    fun <ARTIFACT_TYPE> on(type: ARTIFACT_TYPE)
            where ARTIFACT_TYPE: ArtifactType<FILE_TYPE>,
                  ARTIFACT_TYPE: Artifact.Transformable,
                  ARTIFACT_TYPE: Artifact.Single
}

/**
 * A transform request on a multiple [FILE_TYPE] abstraction.
 */
@Incubating
interface MultipleTransformRequest<FILE_TYPE: FileSystemLocation> {
    /**
     * Specifies the artifact type this multiple file transform request applies to.
     * @param type the artifact type which must be of the right [FILE_TYPE], but also
     * [Artifact.Transformable] and [Artifact.Multiple]
     */
    fun <ARTIFACT_TYPE> on(type: ARTIFACT_TYPE)
            where ARTIFACT_TYPE: ArtifactType<FILE_TYPE>,
                  ARTIFACT_TYPE: Artifact.Transformable,
                  ARTIFACT_TYPE: Artifact.Multiple
}

/**
 * A replace request on a single or multiple [FILE_TYPE] abstraction.
 */
@Incubating
interface ReplaceRequest<FILE_TYPE: FileSystemLocation> {
    /**
     * Specifies the artifact type this multiple file replace request applies to.
     * @param type the artifact type which must be of the right [FILE_TYPE], but also
     * [Artifact.Replaceable].
     */
    fun <ARTIFACT_TYPE> on(type: ARTIFACT_TYPE)
            where ARTIFACT_TYPE: ArtifactType<FILE_TYPE>,
                  ARTIFACT_TYPE: Artifact.Replaceable
}

/**
 * An append request on a multiple [FILE_TYPE] abstraction.
 */
@Incubating
interface AppendRequest<FILE_TYPE: FileSystemLocation> {
    /**
     * Specifies the artifact type this multiple file append request applies to.
     * @param type the artifact type which must be of the right [FILE_TYPE], but also
     * [Artifact.Appendable] and [Artifact.Multiple]
     */
    fun <ARTIFACT_TYPE> on(type: ARTIFACT_TYPE): AppendRequest<FILE_TYPE>
            where ARTIFACT_TYPE: ArtifactType<FILE_TYPE>,
                  ARTIFACT_TYPE: Artifact.Appendable
}