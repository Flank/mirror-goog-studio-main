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

package com.android.build.gradle.tasks

import com.android.SdkConstants.FD_RES_VALUES
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.aapt.SharedExecutorResourceCompilationService
import com.android.build.gradle.internal.res.getAapt2FromMavenAndVersion
import com.android.build.gradle.internal.res.namespaced.Aapt2ServiceKey
import com.android.build.gradle.internal.res.namespaced.registerAaptService
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.NewIncrementalTask
import com.android.build.gradle.internal.tasks.TaskInputHelper
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.options.SyncOptions
import com.android.builder.internal.aapt.v2.Aapt2RenamingConventions
import com.android.builder.png.VectorDrawableRenderer
import com.android.ide.common.resources.CompileResourceRequest
import com.android.ide.common.resources.NoOpResourcePreprocessor
import com.android.ide.common.resources.ResourcePreprocessor
import com.android.ide.common.workers.WorkerExecutorFacade
import com.android.resources.Density
import com.android.utils.FileUtils
import com.google.common.collect.ImmutableList
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileType
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.ChangeType
import org.gradle.work.FileChange
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import javax.inject.Inject

@CacheableTask
abstract class CompileLibraryResourcesTask : NewIncrementalTask() {

    private lateinit var errorFormatMode: SyncOptions.ErrorFormatMode

    @get:InputFiles
    @get:Incremental
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mergedLibraryResourcesDir: DirectoryProperty

    @get:Input
    var pseudoLocalesEnabled: Boolean = false
        private set

    @get:Input
    var crunchPng: Boolean = true
        private set

    @get:Input
    lateinit var aapt2Version: String
        private set

    @get:Internal
    abstract val aapt2FromMaven: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    lateinit var generatedDensities: Set<String>
        private set

    private lateinit var generatedPngsOutputDir: File

    /**
     * Used to load [generatedFilesMap] when running incrementally.
     */
    @get:OutputFile
    @get:Optional
    lateinit var generatedFilesMapBlobFile: File
        private set

    /**
     * Contains for each vector drawable file a list of generated pngs to help deleting previously
     * generated files in incremental builds.
     *
     * We can identify the files uniquely using a combination of parent name and file name.
     */
    private var generatedFilesMap: MutableMap<FileIdentifier, Collection<FileIdentifier>> =
        HashMap()

    @get:Input
    var vectorSupportLibraryIsUsed: Boolean = false
        private set

    private lateinit var resourcePreprocessor: ResourcePreprocessor

    @get:Input
    abstract val minSdk: Property<Int>

    override fun doTaskAction(inputChanges: InputChanges) {
        if (generatedDensities.isEmpty()) {
            resourcePreprocessor = NoOpResourcePreprocessor.INSTANCE
        } else {
            resourcePreprocessor = VectorDrawableRenderer(
                minSdk.get(),
                vectorSupportLibraryIsUsed,
                generatedPngsOutputDir,
                generatedDensities.map { Density.getEnum(it) },
                LoggerWrapper.supplierFor(CompileLibraryResourcesTask::class.java)
            )
        }

        val aapt2ServiceKey = registerAaptService(
            aapt2FromMaven, LoggerWrapper(logger)
        )

        getWorkerFacadeWithWorkers().use { workers ->
            val requests = ImmutableList.builder<CompileResourceRequest>()
            val generatedFilesRequests = ImmutableList.builder<CompileResourceRequest>()

            if (inputChanges.isIncremental && loadGeneratedFilesMap()) {
                doIncrementalTaskAction(
                    inputChanges.getFileChanges(mergedLibraryResourcesDir),
                    requests,
                    generatedFilesRequests,
                    workers
                )
            } else {
                // do full task action
                if (generatedPngsOutputDir.exists()) {
                    FileUtils.deleteDirectoryContents(generatedPngsOutputDir)
                }

                FileUtils.deleteDirectoryContents(outputDir.asFile.get())

                // filter out the values files as they have to go through the resources merging
                // pipeline.
                mergedLibraryResourcesDir.asFile.get().listFiles()!!
                    .filter { it.isDirectory && !it.name.startsWith(FD_RES_VALUES) }
                    .forEach { dir ->
                        dir.listFiles()!!.forEach { file ->
                            submitFileToBeCompiled(file, requests, generatedFilesRequests, workers)
                        }
                    }
            }

            // Wait for the file generation to be completed
            workers.await()

            // Save the generated files map
            generatedFilesMapBlobFile.parentFile.mkdirs()
            ObjectOutputStream(FileOutputStream(generatedFilesMapBlobFile)).use {
                it.writeObject(generatedFilesMap)
            }

            // Generated resources override normal resources so we need to add them at the end
            requests.addAll(generatedFilesRequests.build())

            workers.submit(
                CompileLibraryResourcesRunnable::class.java,
                CompileLibraryResourcesParams(
                    projectName,
                    path,
                    aapt2ServiceKey,
                    errorFormatMode,
                    requests.build()
                )
            )
        }
    }

    private fun submitFileToBeCompiled(
        file: File,
        requests: ImmutableList.Builder<CompileResourceRequest>,
        generatedFilesRequests: ImmutableList.Builder<CompileResourceRequest>,
        workers: WorkerExecutorFacade
    ) {
        val generatedFiles = resourcePreprocessor.getFilesToBeGenerated(file)
        if (generatedFiles.isEmpty()) {
            requests.add(
                CompileResourceRequest(
                    file,
                    outputDir.asFile.get(),
                    isPseudoLocalize = pseudoLocalesEnabled,
                    isPngCrunching = crunchPng
                )
            )
        } else {
            generatedFilesMap[FileIdentifier(file)] = generatedFiles.map { FileIdentifier(it) }
            generatedFiles.forEach {
                workers.submit(
                    FileGenerationWorkAction::class.java,
                    FileGenerationParameters(it, file, resourcePreprocessor)
                )

                generatedFilesRequests.add(
                    CompileResourceRequest(
                        it,
                        outputDir.asFile.get(),
                        isPseudoLocalize = pseudoLocalesEnabled,
                        isPngCrunching = crunchPng
                    )
                )
            }
        }
    }

    private fun deleteFile(file: File) {
        val generatedFiles = generatedFilesMap[FileIdentifier(file)]
        if (generatedFiles?.isEmpty() == false) {
            generatedFiles.forEach {
                FileUtils.deleteIfExists(
                    File(
                        outputDir.asFile.get(),
                        Aapt2RenamingConventions.compilationRename(File(it.parentName, it.fileName))
                    )
                )
            }
            generatedFilesMap.remove(FileIdentifier(file))
        } else {
            FileUtils.deleteIfExists(
                File(
                    outputDir.asFile.get(),
                    Aapt2RenamingConventions.compilationRename(file)
                )
            )
        }
    }

    private fun loadGeneratedFilesMap(): Boolean {
        return try {
            ObjectInputStream(FileInputStream(generatedFilesMapBlobFile)).use {
                @Suppress("UNCHECKED_CAST")
                generatedFilesMap =
                    it.readObject() as MutableMap<FileIdentifier, Collection<FileIdentifier>>
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun handleModifiedFile(
        file: File,
        changeType: ChangeType,
        requests: ImmutableList.Builder<CompileResourceRequest>,
        generatedFilesRequests: ImmutableList.Builder<CompileResourceRequest>,
        workers: WorkerExecutorFacade
    ) {
        if (changeType == ChangeType.MODIFIED || changeType == ChangeType.REMOVED) {
            deleteFile(file)
        }
        if (changeType == ChangeType.ADDED || changeType == ChangeType.MODIFIED) {
            submitFileToBeCompiled(file, requests, generatedFilesRequests, workers)
        }
    }

    private fun doIncrementalTaskAction(
        fileChanges: Iterable<FileChange>,
        requests: ImmutableList.Builder<CompileResourceRequest>,
        generatedFilesRequests: ImmutableList.Builder<CompileResourceRequest>,
        workers: WorkerExecutorFacade
    ) {
        fileChanges.filter {
            it.fileType == FileType.FILE &&
                    !it.file.parentFile.name.startsWith(FD_RES_VALUES)
        }
            .forEach { fileChange ->
                handleModifiedFile(
                    fileChange.file,
                    fileChange.changeType,
                    requests,
                    generatedFilesRequests,
                    workers
                )
            }
    }

    private data class FileIdentifier(val fileName: String, val parentName: String) {
        constructor(file: File) : this(file.name, file.parentFile.name)
    }

    private data class CompileLibraryResourcesParams(
        val projectName: String,
        val owner: String,
        val aapt2ServiceKey: Aapt2ServiceKey,
        val errorFormatMode: SyncOptions.ErrorFormatMode,
        val requests: List<CompileResourceRequest>
    ) : Serializable

    private class CompileLibraryResourcesRunnable
    @Inject constructor(private val params: CompileLibraryResourcesParams) : Runnable {
        override fun run() {
            SharedExecutorResourceCompilationService(
                params.projectName,
                params.owner,
                params.aapt2ServiceKey,
                params.errorFormatMode
            ).use {
                it.submitCompile(params.requests)
            }
        }
    }

    class CreationAction(variantScope: VariantScope) :
        VariantTaskCreationAction<CompileLibraryResourcesTask>(variantScope) {
        override val name: String
            get() = variantScope.getTaskName("compile", "LibraryResources")
        override val type: Class<CompileLibraryResourcesTask>
            get() = CompileLibraryResourcesTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<out CompileLibraryResourcesTask>) {
            super.handleProvider(taskProvider)

            variantScope.artifacts.producesDir(
                InternalArtifactType.COMPILED_LOCAL_RESOURCES,
                BuildArtifactsHolder.OperationType.INITIAL,
                taskProvider,
                CompileLibraryResourcesTask::outputDir
            )
        }

        override fun configure(task: CompileLibraryResourcesTask) {
            super.configure(task)

            variantScope.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.PACKAGED_RES,
                task.mergedLibraryResourcesDir
            )

            val (aapt2FromMaven, aapt2Version) = getAapt2FromMavenAndVersion(variantScope.globalScope)
            task.aapt2FromMaven.from(aapt2FromMaven)
            task.aapt2Version = aapt2Version

            task.pseudoLocalesEnabled = variantScope
                .variantData
                .variantConfiguration
                .buildType
                .isPseudoLocalesEnabled

            task.crunchPng = variantScope.isCrunchPngs

            task.errorFormatMode =
                SyncOptions.getErrorFormatMode(variantScope.globalScope.projectOptions)

            val vectorDrawablesOptions =
                variantScope.variantData.variantConfiguration.mergedFlavor.vectorDrawables
            task.generatedDensities = vectorDrawablesOptions.generatedDensities ?: emptySet()
            task.vectorSupportLibraryIsUsed = vectorDrawablesOptions.useSupportLibrary ?: false
            task.minSdk.set(
                TaskInputHelper.memoizeToProvider(variantScope.globalScope.project) {
                    variantScope.variantData.variantConfiguration.minSdkVersion.apiLevel
                }
            )
            task.generatedPngsOutputDir =
                FileUtils.join(
                    variantScope.globalScope.generatedDir,
                    "compile-library-resources",
                    "pngs",
                    variantScope.variantConfiguration.dirName
                )
            task.generatedFilesMapBlobFile =
                FileUtils.join(variantScope.getIncrementalDir(name), "generatedFilesMap")
        }
    }

    data class FileGenerationParameters constructor(
        val file: File, val sourceFile: File, val resourcePreprocessor: ResourcePreprocessor
    ) : Serializable

    class FileGenerationWorkAction @Inject
    constructor(private val params: FileGenerationParameters) : Runnable {

        override fun run() {
            try {
                params.resourcePreprocessor.generateFile(
                    params.file,
                    params.sourceFile
                )
            } catch (e: Exception) {
                throw RuntimeException(
                    "Error while processing "
                            + params.sourceFile
                            + " : "
                            + e.message,
                    e
                )
            }

        }
    }
}