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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.pipeline.StreamFilter
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.options.ProjectOptions
import com.android.utils.FileUtils
import com.google.common.io.Files
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.io.Serializable
import javax.inject.Inject

/**
 * Task that converts from a pipeline output [FileCollection] to a File that's publishable.
 *
 * Pipeline outputs, queried via [TransformManager.getPipelineOutputAsFileCollection] can be
 * consumed by tasks, but they cannot be published, since a single [File] must be known during
 * configuration.
 * The [FileCollection] is a dynamic one as it points to the folder output of a transform, but
 * the actual content is in another layer of folders, and this is filled *after* the transform
 * as run, filtered via the [StreamFilter].
 *
 * This task therefore, copies the required files from the transform output into a known location
 * that can be published.
 *
 * The files directly inside the folder are the files found in the pipeline. This means
 * - input is foo.jar => output is outputDir/foo.jar
 * - input is foo/ => output is outputDir/foo/
 *
 * This way the consumer of outputDir can distinguish between published jars and published folders.
 */
@CacheableTask
open class PipelineToPublicationTask : DefaultTask() {

    private var internalDelegate: PipelineToPublicationDelegate? = null

    @Suppress("MemberVisibilityCanPrivate")
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val inputCollection: FileCollection
        get() = internalDelegate?.inputCollection ?: throw IllegalStateException("Null internal delegate")

    @get:OutputDirectory
    val outputFile: File
        get() = internalDelegate?.outputFile ?: throw IllegalStateException("Null internal delegate")

    @TaskAction
    fun transform() {
        if (inputCollection.isEmpty) {
            return
        }

        val executor = services.get(WorkerExecutor::class.java)
        internalDelegate?.run(executor)
    }

    class ConfigAction(
            private val variantScope: VariantScope,
            private val inputCollection: FileCollection,
            private val outputFile: File,
            private val outputType: InternalArtifactType):
            TaskConfigAction<PipelineToPublicationTask> {

        override fun getName() = variantScope.getTaskName("prepare$outputType", "ForPublishing")

        override fun getType() = PipelineToPublicationTask::class.java

        override fun execute(task: PipelineToPublicationTask) {
            task.internalDelegate = PipelineToPublicationDelegate(
                    inputCollection, outputFile, variantScope.globalScope.projectOptions)
        }
    }
}

/**
 * the immutable delegate that handles running the task action.
 */
private class PipelineToPublicationDelegate(
        val inputCollection: FileCollection,
        val outputFile: File,
        projectOptions: ProjectOptions)
    : TaskDelegateWithWorker(projectOptions) {

    override fun getWorkerClass() = PipelineOutputCopier::class.java

    override fun getWorkerParam() =
            PipelineToPublicationParam(inputCollection.singleFile, outputFile)
}


/**
 * Data class that encapsulate the Runnable params. This is to provide type safety.
 */
private data class PipelineToPublicationParam(val inputFile: File, val outputFile: File)
    : Serializable

/**
 * The runnable that is the actual task action.
 *
 * This can be run directly or via a Worker Action
 */
private class PipelineOutputCopier @Inject constructor(
        val param: PipelineToPublicationParam)
        : Runnable {

    override fun run() {
        val inputFile = param.inputFile
        val outputFile = param.outputFile

        FileUtils.deleteDirectoryContents(outputFile)

        val targetFile = File(outputFile, inputFile.name)

        if (inputFile.isFile) {
            Files.copy(inputFile, targetFile)
        } else {
            // make the folder
            FileUtils.mkdirs(targetFile)

            FileUtils.copyDirectoryContentToDirectory(inputFile, targetFile)
        }
    }
}