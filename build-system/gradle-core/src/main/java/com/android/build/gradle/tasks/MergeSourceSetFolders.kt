/*
 * Copyright (C) 2012 The Android Open Source Project
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

import com.android.build.api.artifact.ArtifactType
import com.android.build.api.artifact.BuildableArtifact
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.errors.MessageReceiverImpl
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.ASSETS
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.IncrementalTask
import com.android.build.gradle.internal.tasks.Workers
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.options.SyncOptions
import com.android.builder.core.BuilderConstants
import com.android.builder.model.SourceProvider
import com.android.ide.common.resources.AssetMerger
import com.android.ide.common.resources.AssetSet
import com.android.ide.common.resources.FileStatus
import com.android.ide.common.resources.FileValidity
import com.android.ide.common.resources.MergedAssetWriter
import com.android.ide.common.resources.MergingException
import com.android.ide.common.workers.WorkerExecutorFacade
import com.android.utils.FileUtils
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.Lists
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.io.IOException
import java.util.function.Function
import java.util.function.Supplier
import javax.inject.Inject

@CacheableTask
abstract class MergeSourceSetFolders @Inject constructor(
    workerExecutor: WorkerExecutor
) : IncrementalTask() {

    // ----- PUBLIC TASK API -----
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    // ----- PRIVATE TASK API -----

    // file inputs as raw files, lazy behind a memoized/bypassed supplier
    private lateinit var sourceFolderInputs: Supplier<Collection<File>>

    // supplier of the assets set, for execution only.
    @get:Internal("for testing")
    internal lateinit var assetSetSupplier: Supplier<List<AssetSet>>

    // for the dependencies
    @get:Internal("for testing")
    internal var libraryCollection: ArtifactCollection? = null

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    var shadersOutputDir: BuildableArtifact? = null
        internal set

    @get:Input
    @get:Optional
    var ignoreAssets: String? = null
        private set

    private lateinit var errorFormatMode: SyncOptions.ErrorFormatMode

    private val fileValidity = FileValidity<AssetSet>()

    private val workerExecutor: WorkerExecutorFacade =
        Workers.preferWorkers(project.name, path, workerExecutor)

    override val incremental: Boolean
        @Internal
        get() = true

    @Throws(IOException::class)
    override fun doFullTaskAction() {
        val incFolder = incrementalFolder!!

        // this is full run, clean the previous output
        val destinationDir = outputDir.get().asFile
        FileUtils.cleanOutputDir(destinationDir)

        val assetSets = computeAssetSetList()

        // create a new merger and populate it with the sets.
        val merger = AssetMerger()

        val logger = LoggerWrapper(logger)
        try {
            this.workerExecutor.use { workerExecutor ->
                for (assetSet in assetSets) {
                    // set needs to be loaded.
                    assetSet.loadFromFiles(logger)
                    merger.addDataSet(assetSet)
                }

                // get the merged set and write it down.
                val writer = MergedAssetWriter(destinationDir, workerExecutor)

                merger.mergeData(writer, false /*doCleanUp*/)

                // No exception? Write the known state.
                merger.writeBlobTo(incFolder, writer, false)
            }
        } catch (e: Exception) {
            MergingException.findAndReportMergingException(
                e, MessageReceiverImpl(errorFormatMode, getLogger())
            )
            try {
                throw e
            } catch (mergingException: MergingException) {
                merger.cleanBlob(incFolder)
                throw ResourceException(mergingException.message, mergingException)
            }

        }
    }

    @Throws(IOException::class)
    override fun doIncrementalTaskAction(changedInputs: Map<File, FileStatus>) {
        // create a merger and load the known state.
        val merger = AssetMerger()
        try {
            this.workerExecutor.use { workerExecutor ->
                if (!/*incrementalState*/merger.loadFromBlob(incrementalFolder!!, true)) {
                    doFullTaskAction()
                    return
                }

                // compare the known state to the current sets to detect incompatibility.
                // This is in case there's a change that's too hard to do incrementally. In this case
                // we'll simply revert to full build.
                val assetSets = computeAssetSetList()

                if (!merger.checkValidUpdate(assetSets)) {
                    logger.info("Changed Asset sets: full task run!")
                    doFullTaskAction()
                    return

                }

                val logger = LoggerWrapper(logger)

                // The incremental process is the following:
                // Loop on all the changed files, find which ResourceSet it belongs to, then ask
                // the resource set to update itself with the new file.
                for ((changedFile, value) in changedInputs) {

                    // Ignore directories.
                    if (changedFile.isDirectory) {
                        continue
                    }

                    merger.findDataSetContaining(changedFile, fileValidity)
                    if (fileValidity.status == FileValidity.FileStatus.UNKNOWN_FILE) {
                        doFullTaskAction()
                        return

                    } else if (fileValidity.status == FileValidity.FileStatus.VALID_FILE) {
                        if (!fileValidity
                                .dataSet
                                .updateWith(
                                    fileValidity.sourceFile,
                                    changedFile,
                                    value,
                                    logger
                                )
                        ) {
                            getLogger().info(
                                "Failed to process {} event! Full task run", value
                            )
                            doFullTaskAction()
                            return
                        }
                    }
                }

                val writer = MergedAssetWriter(outputDir.get().asFile, workerExecutor)

                merger.mergeData(writer, false /*doCleanUp*/)

                // No exception? Write the known state.
                merger.writeBlobTo(incrementalFolder!!, writer, false)
            }
        } catch (e: Exception) {
            MergingException.findAndReportMergingException(
                e, MessageReceiverImpl(errorFormatMode, logger)
            )
            try {
                throw e
            } catch (mergingException: MergingException) {
                merger.cleanBlob(incrementalFolder!!)
                throw ResourceException(mergingException.message, mergingException)
            }

        } finally {
            // some clean up after the task to help multi variant/module builds.
            fileValidity.clear()
        }
    }

    @Optional
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    fun getLibraries(): FileCollection? = libraryCollection?.artifactFiles

    // input list for the source folder based asset folders.
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    fun getSourceFolderInputs(): Collection<File> = sourceFolderInputs.get()

    /**
     * Compute the list of Asset set to be used during execution based all the inputs.
     */
    @VisibleForTesting
    internal fun computeAssetSetList(): List<AssetSet> {
        val assetSetList: List<AssetSet>

        val assetSets = assetSetSupplier.get()
        if (shadersOutputDir == null
            && ignoreAssets == null
            && libraryCollection == null
        ) {
            assetSetList = assetSets
        } else {
            var size = assetSets.size + 3
            libraryCollection?.let {
                size += it.artifacts.size
            }

            assetSetList = Lists.newArrayListWithExpectedSize(size)

            // get the dependency base assets sets.
            // add at the beginning since the libraries are less important than the folder based
            // asset sets.
            libraryCollection?.let {
                // the order of the artifact is descending order, so we need to reverse it.
                val libArtifacts = it.artifacts
                for (artifact in libArtifacts) {
                    val assetSet = AssetSet(ProcessApplicationManifest.getArtifactName(artifact))
                    assetSet.addSource(artifact.file)

                    // add to 0 always, since we need to reverse the order.
                    assetSetList.add(0, assetSet)
                }
            }

            // add the generated folders to the first set of the folder-based sets.
            val generatedAssetFolders = Lists.newArrayList<File>()

            if (shadersOutputDir != null) {
                generatedAssetFolders.addAll(shadersOutputDir!!.files)
            }

            // add the generated files to the main set.
            val mainAssetSet = assetSets[0]
            assert(mainAssetSet.configName == BuilderConstants.MAIN)
            mainAssetSet.addSources(generatedAssetFolders)

            assetSetList.addAll(assetSets)
        }

        if (ignoreAssets != null) {
            for (set in assetSetList) {
                set.setIgnoredPatterns(ignoreAssets)
            }
        }

        return assetSetList
    }

    abstract class CreationAction protected constructor(scope: VariantScope) :
        VariantTaskCreationAction<MergeSourceSetFolders>(scope) {

        override val type: Class<MergeSourceSetFolders>
            get() = MergeSourceSetFolders::class.java

        override fun configure(task: MergeSourceSetFolders) {
            super.configure(task)
            val scope = variantScope

            task.incrementalFolder = scope.getIncrementalDir(name)

            task.errorFormatMode = SyncOptions.getErrorFormatMode(scope.globalScope.projectOptions)
        }
    }

    open class MergeAssetBaseCreationAction(
        scope: VariantScope,
        private val outputArtifactType: ArtifactType,
        private val includeDependencies: Boolean
    ) : CreationAction(scope) {

        override val name: String
            get() = variantScope.getTaskName("merge", "Assets")

        override fun preConfigure(taskName: String) {
            super.preConfigure(taskName)
        }

        override fun handleProvider(taskProvider: TaskProvider<out MergeSourceSetFolders>) {
            super.handleProvider(taskProvider)

            variantScope
                .artifacts
                .producesDir(
                    outputArtifactType,
                    BuildArtifactsHolder.OperationType.INITIAL,
                    taskProvider,
                    taskProvider.map { it.outputDir },
                    "out"
                )
            variantScope.taskContainer.mergeAssetsTask = taskProvider
        }

        override fun configure(task: MergeSourceSetFolders) {
            super.configure(task)
            val scope = variantScope

            val variantData = scope.variantData
            val variantConfig = variantData.variantConfiguration

            val assetDirFunction =
                Function<SourceProvider, Collection<File>> { it.assetsDirectories }
            task.assetSetSupplier = Supplier { variantConfig.getSourceFilesAsAssetSets(assetDirFunction) }
            task.sourceFolderInputs = Supplier { variantConfig.getSourceFiles(assetDirFunction) }

            task.shadersOutputDir =
                scope.artifacts.getFinalArtifactFiles(InternalArtifactType.SHADER_ASSETS)

            val options = scope.globalScope.extension.aaptOptions
            if (options != null) {
                task.ignoreAssets = options.ignoreAssets
            }

            if (includeDependencies) {
                task.libraryCollection = scope.getArtifactCollection(RUNTIME_CLASSPATH, ALL, ASSETS)
            }

            task.dependsOn(scope.taskContainer.assetGenTask)
        }
    }

    class MergeAppAssetCreationAction(scope: VariantScope) :
        MergeAssetBaseCreationAction(scope, InternalArtifactType.MERGED_ASSETS, true) {

        override val name: String
            get() = variantScope.getTaskName("merge", "Assets")
    }

    class LibraryAssetCreationAction(scope: VariantScope) :
        MergeAssetBaseCreationAction(scope, InternalArtifactType.LIBRARY_ASSETS, false) {

        override val name: String
            get() = variantScope.getTaskName("package", "Assets")
    }

    class MergeJniLibFoldersCreationAction(scope: VariantScope) : CreationAction(scope) {

        override val name: String
            get() = variantScope.getTaskName("merge", "JniLibFolders")

        override fun handleProvider(taskProvider: TaskProvider<out MergeSourceSetFolders>) {
            super.handleProvider(taskProvider)
            variantScope
                .artifacts
                .producesDir(
                    InternalArtifactType.MERGED_JNI_LIBS,
                    BuildArtifactsHolder.OperationType.INITIAL,
                    taskProvider,
                    taskProvider.map { it.outputDir },
                    "out"
                )
        }

        override fun configure(task: MergeSourceSetFolders) {
            super.configure(task)
            val variantData = variantScope.variantData
            val variantConfig = variantData.variantConfiguration

            val assetDirFunction =
                Function<SourceProvider, Collection<File>> { it.jniLibsDirectories }
            task.assetSetSupplier =
                Supplier { variantConfig.getSourceFilesAsAssetSets(assetDirFunction) }
            task.sourceFolderInputs = Supplier { variantConfig.getSourceFiles(assetDirFunction) }
        }
    }

    class MergeShaderSourceFoldersCreationAction(scope: VariantScope) : CreationAction(scope) {

        override val name: String
            get() = variantScope.getTaskName("merge", "Shaders")

        override fun handleProvider(taskProvider: TaskProvider<out MergeSourceSetFolders>) {
            super.handleProvider(taskProvider)
            variantScope
                .artifacts
                .producesDir(
                    InternalArtifactType.MERGED_SHADERS,
                    BuildArtifactsHolder.OperationType.INITIAL,
                    taskProvider,
                    taskProvider.map { it.outputDir },
                    "out"
                )
        }

        override fun configure(task: MergeSourceSetFolders) {
            super.configure(task)
            val variantData = variantScope.variantData
            val variantConfig = variantData.variantConfiguration

            val assetDirFunction = Function<SourceProvider, Collection<File>> { it.shadersDirectories }
            task.assetSetSupplier =
                Supplier { variantConfig.getSourceFilesAsAssetSets(assetDirFunction) }
            task.sourceFolderInputs = Supplier { variantConfig.getSourceFiles(assetDirFunction) }

        }
    }
}
