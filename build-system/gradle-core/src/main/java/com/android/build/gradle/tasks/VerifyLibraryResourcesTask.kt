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

import com.android.annotations.VisibleForTesting
import com.android.build.api.artifact.BuildableArtifact
import com.android.build.gradle.internal.aapt.AaptGeneration
import com.android.build.gradle.internal.aapt.AaptGradleFactory
import com.android.build.gradle.internal.api.artifact.singleFile
import com.android.build.gradle.internal.dsl.AaptOptions
import com.android.build.gradle.internal.dsl.convert
import com.android.build.gradle.internal.res.Aapt2ProcessResourcesRunnable
import com.android.build.gradle.internal.res.getAapt2FromMavenIfEnabled
import com.android.build.gradle.internal.res.namespaced.Aapt2CompileRunnable
import com.android.build.gradle.internal.res.namespaced.Aapt2ServiceKey
import com.android.build.gradle.internal.res.namespaced.registerAaptService
import com.android.build.gradle.internal.scope.ExistingBuildElements
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.IncrementalTask
import com.android.build.gradle.internal.tasks.Workers
import com.android.builder.core.AndroidBuilder
import com.android.builder.core.VariantTypeImpl
import com.android.builder.internal.aapt.AaptException
import com.android.builder.internal.aapt.AaptPackageConfig
import com.android.builder.internal.aapt.BlockingResourceLinker
import com.android.builder.internal.aapt.v2.Aapt2RenamingConventions
import com.android.ide.common.blame.MergingLog
import com.android.ide.common.blame.MergingLogRewriter
import com.android.ide.common.blame.ParsingProcessOutputHandler
import com.android.ide.common.blame.parser.ToolOutputParser
import com.android.ide.common.blame.parser.aapt.Aapt2OutputParser
import com.android.ide.common.resources.CompileResourceRequest
import com.android.ide.common.resources.FileStatus
import com.android.ide.common.resources.QueueableResourceCompiler
import com.android.ide.common.workers.WorkerExecutorFacade
import com.android.utils.FileUtils
import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Iterables
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.nio.file.Files
import java.util.ArrayList
import java.util.concurrent.Future
import javax.inject.Inject
import java.util.function.Function as JavaFunction

open class VerifyLibraryResourcesTask @Inject
constructor(workerExecutor: WorkerExecutor) : IncrementalTask() {

    @get:OutputDirectory
    lateinit var compiledDirectory: File private set

    // Merged resources directory.
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var inputDirectory: BuildableArtifact private set

    @get:Input
    lateinit var mergeBlameLogFolder: File private set

    lateinit var taskInputType: InternalArtifactType private set

    @Input
    fun getTaskInputType(): String {
        return taskInputType.name
    }

    @get:InputFiles
    lateinit var manifestFiles: BuildableArtifact private set

    private lateinit var aaptGeneration: AaptGeneration

    @Input
    fun getAaptGeneration(): String {
        return aaptGeneration.name
    }

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    var aapt2FromMaven: FileCollection? = null
        private set

    private val workers: WorkerExecutorFacade = Workers.getWorker(workerExecutor)

    override fun isIncremental(): Boolean {
        return true
    }

    override fun doFullTaskAction() {
        // Mark all files as NEW and continue with the verification.
        val fileStatusMap = HashMap<File, FileStatus>()

        inputDirectory.singleFile().listFiles()
                .filter { it.isDirectory}
                .forEach { dir ->
                    dir.listFiles()
                            .filter { file -> Files.isRegularFile(file.toPath()) }
                            .forEach { file -> fileStatusMap[file] = FileStatus.NEW }
                }

        FileUtils.cleanOutputDir(compiledDirectory)
        compileAndVerifyResources(fileStatusMap)
    }

    override fun doIncrementalTaskAction(changedInputs: Map<File, FileStatus>) {
        compileAndVerifyResources(changedInputs)
    }

    /**
     * Compiles and links the resources of the library.
     *
     * @param inputs the new, changed or modified files that need to be compiled or removed.
     */
    private fun compileAndVerifyResources(inputs: Map<File, FileStatus>) {

        val manifestsOutputs = ExistingBuildElements.from(taskInputType, manifestFiles)
        val manifestFile = Iterables.getOnlyElement(manifestsOutputs).outputFile

        if (aaptGeneration == AaptGeneration.AAPT_V2_DAEMON_SHARED_POOL) {
            val aapt2ServiceKey = registerAaptService(aapt2FromMaven, buildTools, iLogger)
            // If we're using AAPT2 we need to compile the resources into the compiled directory
            // first as we need the .flat files for linking.
            workers.use { facade ->
                compileResources(
                        inputs,
                        compiledDirectory, null,
                        facade,
                        aapt2ServiceKey,
                        inputDirectory.singleFile())
                val config = getAaptPackageConfig(compiledDirectory, manifestFile)
                val params = Aapt2ProcessResourcesRunnable.Params(aapt2ServiceKey, config)
                facade.submit(Aapt2ProcessResourcesRunnable::class.java, params)
            }
            return
        }

        val mergingLogRewriter =
                MergingLogRewriter(
                        JavaFunction { MergingLog(mergeBlameLogFolder).find(it) },
                        builder.messageReceiver)

        val processOutputHandler = ParsingProcessOutputHandler(
                ToolOutputParser(Aapt2OutputParser(), iLogger),
                mergingLogRewriter)

        AaptGradleFactory.make(aaptGeneration, builder, processOutputHandler).use { aapt ->
            // If we're using AAPT2 we need to compile the resources into the compiled directory
            // first as we need the .flat files for linking.
            compileResources(
                    inputs,
                    compiledDirectory,
                    aapt,
                    null,
                    null,
                    inputDirectory.singleFile())
            linkResources(compiledDirectory, aapt, manifestFile)
        }
    }

    /**
     * Calls AAPT link to verify the correctness of the library's resources.
     *
     * @param resDir directory containing resources to link.
     * @param aapt AAPT tool to execute the resource linking.
     * @param manifestFile the manifest file to package.
     */
    private fun linkResources(resDir: File, aapt: BlockingResourceLinker, manifestFile: File) {
        val config = getAaptPackageConfig(resDir, manifestFile)
        AndroidBuilder.processResources(aapt, config, iLogger)
    }

    private fun getAaptPackageConfig(resDir: File, manifestFile: File): AaptPackageConfig {
        // We're do not want to generate any files - only to make sure everything links properly.
        return AaptPackageConfig.Builder()
                .setManifestFile(manifestFile)
                .setResourceDir(resDir)
                .setLibrarySymbolTableFiles(ImmutableSet.of())
                .setOptions(AaptOptions().convert())
                .setVariantType(VariantTypeImpl.LIBRARY)
                .setAndroidTarget(builder.target)
                .build()
    }

    class ConfigAction(
        private val scope: VariantScope
    ) : TaskConfigAction<VerifyLibraryResourcesTask> {

        /** Return the name of the task to be configured.  */
        override fun getName(): String = scope.getTaskName("verify", "Resources")

        /** Return the class type of the task to be configured.  */
        override fun getType(): Class<VerifyLibraryResourcesTask> =
                VerifyLibraryResourcesTask::class.java

        /** Configure the given newly-created task object.  */
        override fun execute(verifyLibraryResources: VerifyLibraryResourcesTask) {
            val variantData = scope.variantData
            val config = variantData.variantConfiguration
            verifyLibraryResources.variantName = config.fullName

            verifyLibraryResources.setAndroidBuilder(scope.globalScope.androidBuilder)

            verifyLibraryResources.aaptGeneration =
                    AaptGeneration.fromProjectOptions(scope.globalScope.projectOptions)
            verifyLibraryResources.aapt2FromMaven = getAapt2FromMavenIfEnabled(scope.globalScope)
            verifyLibraryResources.incrementalFolder = scope.getIncrementalDir(name)

            verifyLibraryResources.inputDirectory =
                    scope.artifacts.getFinalArtifactFiles(InternalArtifactType.MERGED_RES)

            verifyLibraryResources.compiledDirectory = scope.compiledResourcesOutputDir
            verifyLibraryResources.mergeBlameLogFolder = scope.resourceBlameLogDir

            val aaptFriendlyManifestsFilePresent = scope.artifacts
                    .hasArtifact(InternalArtifactType.AAPT_FRIENDLY_MERGED_MANIFESTS)
            verifyLibraryResources.taskInputType = when {
                aaptFriendlyManifestsFilePresent ->
                    InternalArtifactType.AAPT_FRIENDLY_MERGED_MANIFESTS
                scope.instantRunBuildContext.isInInstantRunMode ->
                    InternalArtifactType.INSTANT_RUN_MERGED_MANIFESTS
                else ->
                    InternalArtifactType.MERGED_MANIFESTS
            }
            verifyLibraryResources.manifestFiles = scope.artifacts
                    .getFinalArtifactFiles(verifyLibraryResources.taskInputType)
        }
    }

    companion object {
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
                inputs: Map<File, FileStatus>,
                outDirectory: File,
                aapt: QueueableResourceCompiler?,
                workerExecutor: WorkerExecutorFacade?,
                aapt2ServiceKey: Aapt2ServiceKey?,
                mergedResDirectory: File) {

            Preconditions.checkState(
                    aapt != null || (workerExecutor != null && aapt2ServiceKey != null),
                    "Either local AAPT or AAPT from Maven needs to be used, neither was set")

            val compiling = ArrayList<Future<File>>()

            for ((key, value) in inputs) {
                // Accept only files in subdirectories of the merged resources directory.
                // Ignore files and directories directly under the merged resources directory.
                if (key.parentFile.parentFile != mergedResDirectory) continue
                when (value) {
                    FileStatus.NEW, FileStatus.CHANGED ->
                        // If the file is NEW or CHANGED we need to compile it into the output
                        // directory. AAPT2 overwrites files in case they were CHANGED so no need to
                        // remove the corresponding file.
                        try {
                            val request = CompileResourceRequest(
                                    key,
                                    outDirectory,
                                    key.parent,
                                    false /* pseudo-localize */,
                                    false /* crunch PNGs */)
                            if (aapt != null) {
                                val result = aapt.compile(request)
                                compiling.add(result)
                            } else {
                                workerExecutor!!.submit(
                                        Aapt2CompileRunnable::class.java,
                                        Aapt2CompileRunnable.Params(
                                                aapt2ServiceKey!!, listOf(request)))
                            }
                        } catch (e: Exception) {
                            throw AaptException("Failed to compile file ${key.absolutePath}", e)
                        }

                    FileStatus.REMOVED ->
                        // If the file was REMOVED we need to remove the corresponding file from the
                        // output directory.
                        FileUtils.deleteIfExists(
                                File(outDirectory,Aapt2RenamingConventions.compilationRename(key)))
                }
            }
            // We need to wait for the files to finish compiling before we do the link.
            workerExecutor?.await() ?: compiling.forEach { it.get() }
        }
    }
}
