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

package com.android.build.gradle.tasks

import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.aapt.SharedExecutorResourceCompilationService
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.res.Aapt2CompileRunnable
import com.android.build.gradle.internal.res.Aapt2ProcessResourcesRunnable
import com.android.build.gradle.internal.res.getAapt2FromMavenAndVersion
import com.android.build.gradle.internal.res.namespaced.Aapt2ServiceKey
import com.android.build.gradle.internal.res.namespaced.registerAaptService
import com.android.build.gradle.internal.scope.ExistingBuildElements
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.NewIncrementalTask
import com.android.build.gradle.internal.tasks.Workers
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.build.gradle.internal.utils.toImmutableList
import com.android.build.gradle.options.SyncOptions
import com.android.builder.core.VariantTypeImpl
import com.android.builder.files.SerializableInputChanges
import com.android.builder.internal.aapt.AaptException
import com.android.builder.internal.aapt.AaptOptions
import com.android.builder.internal.aapt.AaptPackageConfig
import com.android.builder.internal.aapt.v2.Aapt2RenamingConventions
import com.android.ide.common.resources.CompileResourceRequest
import com.android.ide.common.resources.FileStatus
import com.android.ide.common.workers.WorkerExecutorFacade
import com.android.utils.FileUtils
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Iterables
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import java.io.File
import java.io.Serializable
import java.lang.RuntimeException
import javax.inject.Inject

@CacheableTask
abstract class VerifyLibraryResourcesTask : NewIncrementalTask() {

    @get:OutputDirectory
    lateinit var compiledDirectory: File
        private set

    // Merged resources directory.
    @get:Incremental
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputDirectory: DirectoryProperty

    lateinit var taskInputType: InternalArtifactType<Directory> private set

    @Input
    fun getTaskInputType(): String {
        return taskInputType.javaClass.name
    }

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val manifestFiles: DirectoryProperty

    @get:Input
    lateinit var aapt2Version: String
        private set
    @get:Internal
    abstract val aapt2FromMaven: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    lateinit var androidJar: Provider<File>
        private set

    /** A file collection of the directories containing the compiled dependencies resource files. */
    @get:Incremental
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val compiledDependenciesResources: ConfigurableFileCollection

    private lateinit var mergeBlameFolder: File

    private lateinit var manifestMergeBlameFile: Provider<RegularFile>

    private lateinit var errorFormatMode: SyncOptions.ErrorFormatMode

    override fun doTaskAction(inputChanges: InputChanges) {
        val manifestsOutputs = ExistingBuildElements.from(taskInputType, manifestFiles.get().asFile)
        val manifestFile = Iterables.getOnlyElement(manifestsOutputs).outputFile

        val aapt2ServiceKey = registerAaptService(aapt2FromMaven, LoggerWrapper(logger))
        val parameter = Params(
            projectName = projectName,
            owner = path,
            androidJar = androidJar.get(),
            aapt2ServiceKey = aapt2ServiceKey,
            errorFormatMode = errorFormatMode,
            inputs = inputChanges.getChangesInSerializableForm(inputDirectory),
            manifestFile = manifestFile,
            compiledDependenciesResources = compiledDependenciesResources.files,
            manifestMergeBlameFile = manifestMergeBlameFile.get().asFile,
            compiledDirectory = compiledDirectory,
            mergeBlameFolder = mergeBlameFolder

            )
        getWorkerFacadeWithWorkers().submit(Action::class.java, parameter)
    }

    private data class Params(
        val projectName: String,
        val owner: String,
        val androidJar: File,
        val aapt2ServiceKey: Aapt2ServiceKey,
        val errorFormatMode: SyncOptions.ErrorFormatMode,
        val inputs: SerializableInputChanges,
        val manifestFile: File,
        val compiledDependenciesResources: Iterable<File>,
        val manifestMergeBlameFile: File?,
        val compiledDirectory: File,
        val mergeBlameFolder: File
        ) : Serializable

    /**
     * Compiles and links the resources of the library.
     */
    private class Action @Inject constructor(private val params: Params) : Runnable {
        override fun run() {
            Workers.getSharedExecutorForAapt2(params.projectName, params.owner).use { facade ->
                compileResources(
                    inputs = params.inputs,
                    outDirectory = params.compiledDirectory,
                    workerExecutor = facade,
                    aapt2ServiceKey = params.aapt2ServiceKey,
                    errorFormatMode = params.errorFormatMode,
                    mergeBlameFolder = params.mergeBlameFolder
                )

                val config = getAaptPackageConfig(
                    compiledDependenciesResources = params.compiledDependenciesResources,
                    androidJar = params.androidJar,
                    resDir = params.compiledDirectory,
                    manifestFile = params.manifestFile
                )


                val params = Aapt2ProcessResourcesRunnable.Params(
                    aapt2ServiceKey = params.aapt2ServiceKey,
                    request = config,
                    errorFormatMode = params.errorFormatMode,
                    mergeBlameFolder = params.mergeBlameFolder,
                    manifestMergeBlameFile = params.manifestMergeBlameFile
                )

                facade.await() // All compilation must be done before linking.
                facade.submit(Aapt2ProcessResourcesRunnable::class.java, params)
            }
        }
    }

    class CreationAction(
        variantScope: VariantScope
    ) : VariantTaskCreationAction<VerifyLibraryResourcesTask>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("verify", "Resources")
        override val type: Class<VerifyLibraryResourcesTask>
            get() = VerifyLibraryResourcesTask::class.java

        /** Configure the given newly-created task object.  */
        override fun configure(task: VerifyLibraryResourcesTask) {
            super.configure(task)

            val (aapt2FromMaven, aapt2Version) = getAapt2FromMavenAndVersion(variantScope.globalScope)
            task.aapt2FromMaven.fromDisallowChanges(aapt2FromMaven)
            task.aapt2Version = aapt2Version

            variantScope.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.MERGED_RES,
                task.inputDirectory
            )

            task.compiledDirectory = variantScope.compiledResourcesOutputDir

            val aaptFriendlyManifestsFilePresent = variantScope.artifacts
                .hasFinalProduct(InternalArtifactType.AAPT_FRIENDLY_MERGED_MANIFESTS)
            task.taskInputType = when {
                aaptFriendlyManifestsFilePresent ->
                    InternalArtifactType.AAPT_FRIENDLY_MERGED_MANIFESTS
                else ->
                    InternalArtifactType.MERGED_MANIFESTS
            }
            variantScope.artifacts.setTaskInputToFinalProduct(task.taskInputType, task.manifestFiles)

            task.androidJar = variantScope.globalScope.sdkComponents.androidJarProvider

            task.mergeBlameFolder = variantScope.resourceBlameLogDir

            task.manifestMergeBlameFile = variantScope.artifacts.getFinalProduct(
                InternalArtifactType.MANIFEST_MERGE_BLAME_FILE
            )

            task.errorFormatMode = SyncOptions.getErrorFormatMode(
                variantScope.globalScope.projectOptions
            )

            if (variantScope.isPrecompileDependenciesResourcesEnabled) {
                task.compiledDependenciesResources.fromDisallowChanges(
                    variantScope.getArtifactFileCollection(
                        AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                        AndroidArtifacts.ArtifactScope.ALL,
                        AndroidArtifacts.ArtifactType.COMPILED_DEPENDENCIES_RESOURCES
                    ))
            }
        }
    }

    companion object {
        private fun getAaptPackageConfig(compiledDependenciesResources: Iterable<File>, androidJar: File, resDir: File, manifestFile: File): AaptPackageConfig {
            val compiledDependenciesResourcesDirs = compiledDependenciesResources.reversed()

            // We're do not want to generate any files - only to make sure everything links properly.
            return AaptPackageConfig.Builder()
                .setManifestFile(manifestFile)
                .addResourceDirectories(compiledDependenciesResourcesDirs)
                .addResourceDir(resDir)
                .setLibrarySymbolTableFiles(ImmutableSet.of())
                .setOptions(AaptOptions(failOnMissingConfigEntry = false))
                .setVariantType(VariantTypeImpl.LIBRARY)
                .setAndroidTarget(androidJar)
                .build()
        }

        /**
         * Compiles new or changed files and removes files that were compiled from the removed files.
         *
         *
         * Should only be called when using AAPT2.
         *
         * @param inputs the new, changed or modified files that need to be compiled or removed.
         * @param outDirectory the directory containing compiled resources.
         * @param aapt AAPT tool to execute the resource compiling, either must be supplied or
         * worker executor and revision must be supplied.
         * @param aapt2ServiceKey the AAPT2 service to inject in to the worker executor.
         * @param workerExecutor the worker executor to submit AAPT compilations to.
         * @param mergedResDirectory directory containing merged uncompiled resources.
         */
        @JvmStatic
        @VisibleForTesting
        fun compileResources(
            inputs: SerializableInputChanges,
            outDirectory: File,
            workerExecutor: WorkerExecutorFacade,
            aapt2ServiceKey: Aapt2ServiceKey,
            errorFormatMode: SyncOptions.ErrorFormatMode,
            mergeBlameFolder: File
        ) {

            for (change in inputs.changes) {
                // Accept only files in subdirectories of the merged resources directory.
                // Ignore files and directories directly under the merged resources directory.
                val dirName = change.normalizedPath.substringBeforeLast('/', "")
                if (dirName.isEmpty() || dirName.contains('/')) {
                    continue
                }

                when (change.fileStatus) {
                    FileStatus.NEW, FileStatus.CHANGED ->
                        // If the file is NEW or CHANGED we need to compile it into the output
                        // directory. AAPT2 overwrites files in case they were CHANGED so no need to
                        // remove the corresponding file.
                        try {
                            val request = CompileResourceRequest(
                                change.file,
                                outDirectory,
                                dirName,
                                isPseudoLocalize = false,
                                isPngCrunching = false,
                                mergeBlameFolder = mergeBlameFolder
                            )
                            workerExecutor.submit(
                                Aapt2CompileRunnable::class.java,
                                Aapt2CompileRunnable.Params(
                                    aapt2ServiceKey,
                                    listOf(request),
                                    errorFormatMode,
                                    true
                                )
                            )
                        } catch (e: Exception) {
                            throw AaptException("Failed to compile file ${change.file.absolutePath}", e)
                        }

                    FileStatus.REMOVED ->
                        // If the file was REMOVED we need to remove the corresponding file from the
                        // output directory.
                        FileUtils.deleteIfExists(
                            File(outDirectory, Aapt2RenamingConventions.compilationRename(change.file))
                        )
                }
            }
            // We need to wait for the files to finish compiling before we do the link.
            workerExecutor.await()
        }
    }
}
