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
import com.android.build.gradle.internal.aapt.WorkerExecutorResourceCompilationService
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.Aapt2Input
import com.android.build.gradle.internal.tasks.NewIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.builder.files.SerializableInputChanges
import com.android.builder.internal.aapt.v2.Aapt2RenamingConventions
import com.android.ide.common.resources.CompileResourceRequest
import com.android.ide.common.resources.FileStatus
import com.android.utils.FileUtils
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import org.gradle.workers.WorkerExecutor
import java.io.File
import javax.inject.Inject

@CacheableTask
abstract class CompileLibraryResourcesTask : NewIncrementalTask() {

    @get:InputFiles
    @get:Incremental
    @get:PathSensitive(PathSensitivity.ABSOLUTE) // TODO(b/141301405): use relative paths
    abstract val mergedLibraryResourcesDir: DirectoryProperty

    @get:Input
    var pseudoLocalesEnabled: Boolean = false
        private set

    @get:Input
    var crunchPng: Boolean = true
        private set

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Nested
    abstract val aapt2: Aapt2Input

    override fun doTaskAction(inputChanges: InputChanges) {
        workerExecutor.noIsolation()
            .submit(CompileLibraryResourcesAction::class.java) { parameters ->
                parameters.initializeFromAndroidVariantTask(this)
                parameters.outputDirectory.set(outputDir)
                parameters.aapt2.set(aapt2)
                parameters.incremental.set(inputChanges.isIncremental)
                parameters.incrementalChanges.set(
                    if (inputChanges.isIncremental) {
                        inputChanges.getChangesInSerializableForm(mergedLibraryResourcesDir)
                    } else {
                        null
                    }
                )
                parameters.mergedLibraryResourceDirectory.set(mergedLibraryResourcesDir)
                parameters.pseudoLocalize.set(pseudoLocalesEnabled)
                parameters.crunchPng.set(crunchPng)
            }
    }

    protected abstract class CompileLibraryResourcesParams : ProfileAwareWorkAction.Parameters() {
        abstract val outputDirectory: DirectoryProperty

        @get:Nested
        abstract val aapt2: Property<Aapt2Input>
        abstract val incremental: Property<Boolean>
        abstract val incrementalChanges: Property<SerializableInputChanges>
        abstract val mergedLibraryResourceDirectory: DirectoryProperty
        abstract val pseudoLocalize: Property<Boolean>
        abstract val crunchPng: Property<Boolean>
    }

    protected abstract class CompileLibraryResourcesAction :
        ProfileAwareWorkAction<CompileLibraryResourcesParams>() {

        @get:Inject
        abstract val workerExecutor: WorkerExecutor

        override fun run() {

            WorkerExecutorResourceCompilationService(
                projectName = parameters.projectName.get(),
                taskOwner = parameters.taskOwner.get(),
                workerExecutor = workerExecutor,
                analyticsService = parameters.analyticsService,
                aapt2Input = parameters.aapt2.get()
            ).use { compilationService ->
                if (parameters.incremental.get()) {
                    handleIncrementalChanges(parameters.incrementalChanges.get(), compilationService)
                } else {
                    handleFullRun(compilationService)
                }
            }
        }

        private fun handleFullRun(processor: WorkerExecutorResourceCompilationService) {
            FileUtils.deleteDirectoryContents(parameters.outputDirectory.asFile.get())
            // filter out the values files as they have to go through the resources merging
            // pipeline.
            parameters.mergedLibraryResourceDirectory.asFile.get().listFiles()!!
                .filter { it.isDirectory && !it.name.startsWith(FD_RES_VALUES) }
                .forEach { dir ->
                    dir.listFiles()!!.forEach { file ->
                        submitFileToBeCompiled(file, processor)
                    }
                }
        }

        private fun submitFileToBeCompiled(
            file: File,
            compilationService: WorkerExecutorResourceCompilationService
        ) {
            val request = CompileResourceRequest(
                file,
                parameters.outputDirectory.asFile.get(),
                isPseudoLocalize = parameters.pseudoLocalize.get(),
                isPngCrunching = parameters.crunchPng.get()
            )
            compilationService.submitCompile(request)
        }

        private fun handleModifiedFile(
            file: File,
            changeType: FileStatus,
            compilationService: WorkerExecutorResourceCompilationService
        ) {
            if (changeType == FileStatus.CHANGED || changeType == FileStatus.REMOVED) {
                FileUtils.deleteIfExists(
                    File(
                        parameters.outputDirectory.asFile.get(),
                        Aapt2RenamingConventions.compilationRename(file)
                    )
                )
            }
            if (changeType == FileStatus.NEW || changeType == FileStatus.CHANGED) {
                submitFileToBeCompiled(file, compilationService)
            }
        }

        private fun handleIncrementalChanges(
            fileChanges: SerializableInputChanges,
            compilationService: WorkerExecutorResourceCompilationService
        ) {
            fileChanges.changes.filter {
                !it.file.parentFile.name.startsWith(FD_RES_VALUES)
            }
                .forEach { fileChange ->
                    handleModifiedFile(
                        fileChange.file,
                        fileChange.fileStatus,
                        compilationService
                    )
                }
        }
    }

    class CreationAction(
        creationConfig: VariantCreationConfig
    ) : VariantTaskCreationAction<CompileLibraryResourcesTask, VariantCreationConfig>(
        creationConfig
    ) {
        override val name: String
            get() = computeTaskName("compile", "LibraryResources")
        override val type: Class<CompileLibraryResourcesTask>
            get() = CompileLibraryResourcesTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<CompileLibraryResourcesTask>
        ) {
            super.handleProvider(taskProvider)

            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                CompileLibraryResourcesTask::outputDir
            ).withName("out").on(InternalArtifactType.COMPILED_LOCAL_RESOURCES)
        }

        override fun configure(
            task: CompileLibraryResourcesTask
        ) {
            super.configure(task)

            creationConfig.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.PACKAGED_RES,
                task.mergedLibraryResourcesDir
            )

            task.pseudoLocalesEnabled = creationConfig
                .variantDslInfo
                .isPseudoLocalesEnabled

            task.crunchPng = creationConfig.variantScope.isCrunchPngs

            creationConfig.services.initializeAapt2Input(task.aapt2)

        }
    }
}
