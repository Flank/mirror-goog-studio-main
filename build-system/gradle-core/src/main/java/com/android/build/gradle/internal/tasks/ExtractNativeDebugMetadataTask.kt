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

package com.android.build.gradle.internal.tasks

import com.android.SdkConstants
import com.android.build.api.component.impl.ComponentPropertiesImpl
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.process.GradleProcessExecutor
import com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_NATIVE_LIBS
import com.android.build.gradle.internal.scope.InternalArtifactType.NATIVE_DEBUG_METADATA
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.ide.common.process.LoggedProcessOutputHandler
import com.android.ide.common.process.ProcessExecutor
import com.android.ide.common.process.ProcessInfoBuilder
import com.android.ide.common.workers.WorkerExecutorFacade
import com.android.repository.Revision
import com.android.utils.FileUtils
import com.google.common.annotations.VisibleForTesting
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileTree
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskProvider
import org.gradle.process.ExecOperations
import java.io.File
import java.io.Serializable
import javax.inject.Inject

/**
 * Task to produce native debug metadata files to be included in the app bundle.
 */
@CacheableTask
abstract class ExtractNativeDebugMetadataTask : NonIncrementalTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @get:Input
    lateinit var ndkRevision: Provider<Revision>
        private set

    // We need this inputFiles property because SkipWhenEmpty doesn't work for inputDir because it's
    // a DirectoryProperty
    @get:InputFiles
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputFiles: Property<FileTree>

    private lateinit var objcopyExecutableMapProvider: Provider<Map<Abi, File>>

    override fun doTaskAction() {
        getWorkerFacadeWithThreads(useGradleExecutor = false).use { workers ->
            ExtractNativeDebugMetadataDelegate(
                workers,
                inputDir.get().asFile,
                outputDir.get().asFile,
                objcopyExecutableMapProvider.get(),
                GradleProcessExecutor(execOperations::exec)
            ).run()
        }
    }

    class CreationAction(
        componentProperties: ComponentPropertiesImpl
    ) : VariantTaskCreationAction<ExtractNativeDebugMetadataTask, ComponentPropertiesImpl>(
        componentProperties
    ) {

        override val name: String
            get() = computeTaskName("extract", "NativeDebugMetadata")

        override val type: Class<ExtractNativeDebugMetadataTask>
            get() = ExtractNativeDebugMetadataTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<out ExtractNativeDebugMetadataTask>
        ) {
            super.handleProvider(taskProvider)

            creationConfig.artifacts.producesDir(
                NATIVE_DEBUG_METADATA,
                taskProvider,
                ExtractNativeDebugMetadataTask::outputDir,
                fileName = "out"
            )
        }

        override fun configure(task: ExtractNativeDebugMetadataTask) {
            super.configure(task)

            creationConfig.artifacts.setTaskInputToFinalProduct(MERGED_NATIVE_LIBS, task.inputDir)
            task.ndkRevision = creationConfig.globalScope.sdkComponents.ndkRevisionProvider
            task.objcopyExecutableMapProvider =
                creationConfig.globalScope.sdkComponents.objcopyExecutableMapProvider
            task.inputFiles.setDisallowChanges(
                creationConfig.globalScope.project.provider {
                    creationConfig.globalScope.project.layout.files(task.inputDir).asFileTree
                }
            )
        }
    }
}

/**
 * Delegate to extract debug metadata from native libraries
 */
@VisibleForTesting
class ExtractNativeDebugMetadataDelegate(
    val workers: WorkerExecutorFacade,
    val inputDir: File,
    val outputDir: File,
    private val objcopyExecutableMap: Map<Abi, File>,
    val processExecutor: ProcessExecutor
) {
    private val logger : LoggerWrapper
        get() = LoggerWrapper(Logging.getLogger(ExtractNativeDebugMetadataTask::class.java))

    fun run() {
        FileUtils.cleanOutputDir(outputDir)
        for (inputFile in FileUtils.getAllFiles(inputDir)) {
            if (!inputFile.name.endsWith(SdkConstants.DOT_NATIVE_LIBS, ignoreCase = true)) {
                continue
            }
            val debugInfoOutputFile =
                File(outputDir, "${FileUtils.relativePath(inputFile, inputDir)}.dbg")
            val symbolTableOutputFile =
                File(outputDir, "${FileUtils.relativePath(inputFile, inputDir)}.sym")
            val objcopyExecutable = objcopyExecutableMap[Abi.getByName(inputFile.parentFile.name)]
            if (objcopyExecutable == null) {
                logger.warning(
                    "Unable to extract native debug metadata from ${inputFile.absolutePath} " +
                            "because unable to locate the objcopy executable for the " +
                            "${inputFile.parentFile.name} ABI."
                )
                continue
            }
            workers.submit(
                ExtractNativeDebugMetadataRunnable::class.java,
                ExtractNativeDebugMetadataRunnable.Params(
                    inputFile,
                    debugInfoOutputFile,
                    symbolTableOutputFile,
                    objcopyExecutable,
                    processExecutor
                )
            )

        }
    }
}

/**
 * Runnable to extract debug metadata from a native library
 */
private class ExtractNativeDebugMetadataRunnable @Inject constructor(val params: Params): Runnable {

    private val logger : LoggerWrapper
        get() = LoggerWrapper(Logging.getLogger(ExtractNativeDebugMetadataTask::class.java))

    override fun run() {
        FileUtils.mkdirs(params.fullOutputFile.parentFile)
        FileUtils.mkdirs(params.symbolTableOutputFile.parentFile)

        // first run process to create the full result file (i.e., debug info *and* symbol table)
        val fullBuilder = ProcessInfoBuilder()
        fullBuilder.setExecutable(params.objcopyExecutable)
        fullBuilder.addArgs(
            "--only-keep-debug",
            params.inputFile.toString(),
            params.fullOutputFile.toString()
        )
        val fullResult =
            params.processExecutor.execute(
                fullBuilder.createProcess(),
                LoggedProcessOutputHandler(
                    LoggerWrapper(Logging.getLogger(ExtractNativeDebugMetadataTask::class.java))
                )
            )
        if (fullResult.exitValue != 0) {
            logger.warning(
                "Unable to extract native debug metadata from ${params.inputFile.absolutePath} " +
                        "because of non-zero exit value from objcopy."
            )
        }

        // then run process to create the symbol table file
        val symbolTableBuilder = ProcessInfoBuilder()
        symbolTableBuilder.setExecutable(params.objcopyExecutable)
        symbolTableBuilder.addArgs(
            "-j",
            "symtab",
            "-j",
            "dynsym",
            params.inputFile.toString(),
            params.symbolTableOutputFile.toString()
        )
        val symbolTableResult =
            params.processExecutor.execute(
                symbolTableBuilder.createProcess(),
                LoggedProcessOutputHandler(
                    LoggerWrapper(Logging.getLogger(ExtractNativeDebugMetadataTask::class.java))
                )
            )
        if (symbolTableResult.exitValue != 0) {
            logger.warning(
                "Unable to extract symbol table from ${params.inputFile.absolutePath} " +
                        "because of non-zero exit value from objcopy."
            )
        }
    }

    data class Params(
        val inputFile: File,
        val fullOutputFile: File,
        val symbolTableOutputFile: File,
        val objcopyExecutable: File,
        val processExecutor: ProcessExecutor
    ): Serializable
}
