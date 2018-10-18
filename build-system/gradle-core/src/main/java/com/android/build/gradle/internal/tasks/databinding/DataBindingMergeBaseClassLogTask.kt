/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks.databinding

import android.databinding.tool.DataBindingBuilder
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.EXTERNAL
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.MODULE
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.IncrementalTask
import com.android.build.gradle.internal.tasks.Workers
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.ide.common.resources.FileStatus
import com.android.ide.common.workers.WorkerExecutorFacade
import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.IOFileFilter
import org.apache.commons.io.filefilter.TrueFileFilter
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.io.Serializable
import javax.inject.Inject

private fun isClassListFile(listFile: String) =
    listFile.endsWith(DataBindingBuilder.BINDING_CLASS_LIST_SUFFIX)

open class DataBindingMergeBaseClassLogTask @Inject
constructor(workerExecutor: WorkerExecutor): IncrementalTask() {

    @get:InputFiles
    lateinit var moduleClassLog: FileCollection
        private set

    @get:InputFiles
    lateinit var externalClassLog: FileCollection
        private set

    @get:OutputDirectory
    lateinit var outFolder: Provider<Directory>
        private set

    private val workers: WorkerExecutorFacade = Workers.getWorker(workerExecutor)

    override fun isIncremental(): Boolean {
        return true
    }

    override fun doFullTaskAction() {
        com.android.utils.FileUtils.cleanOutputDir(outFolder.get().asFile)

        workers.let { facade ->
            moduleClassLog
                .union(externalClassLog)
                .filter { it.exists() }
                .forEach { folder ->
                    FileUtils.listFiles(
                        folder,
                        object : IOFileFilter {
                            override fun accept(file: File): Boolean {
                                return isClassListFile(file.name)
                            }

                            override fun accept(dir: File, name: String): Boolean {
                                return isClassListFile(name)
                            }
                        },
                        TrueFileFilter.INSTANCE
                    ).forEach {
                        facade.submit(
                            DataBindingMergeBaseClassLogDelegate::class.java,
                            DataBindingMergeBaseClassLogDelegate.Params(it, outFolder.get().asFile, FileStatus.NEW)
                        )
                    }
                }
        }
    }

    override fun doIncrementalTaskAction(changedInputs: Map<File, FileStatus>) {
        workers.let { facade ->
            changedInputs.forEach {
                facade.submit(
                    DataBindingMergeBaseClassLogDelegate::class.java,
                    DataBindingMergeBaseClassLogDelegate.Params(it.key, outFolder.get().asFile, it.value)
                )
            }
        }
    }

    class CreationAction(variantScope: VariantScope) :
        VariantTaskCreationAction<DataBindingMergeBaseClassLogTask>(variantScope) {

        override val name = variantScope.getTaskName("dataBindingMergeGenClasses")
        override val type = DataBindingMergeBaseClassLogTask::class.java

        private lateinit var outFolder: Provider<Directory>

        override fun preConfigure(taskName: String) {
            super.preConfigure(taskName)
            outFolder = variantScope
                .artifacts
                .createDirectory(
                    InternalArtifactType.DATA_BINDING_BASE_CLASS_LOGS_DEPENDENCY_ARTIFACTS,
                    taskName)
        }

        override fun configure(task: DataBindingMergeBaseClassLogTask) {
            super.configure(task)

            task.outFolder = outFolder

            // data binding related artifacts for external libs
            task.moduleClassLog = variantScope.getArtifactFileCollection(
                COMPILE_CLASSPATH,
                MODULE,
                ArtifactType.DATA_BINDING_BASE_CLASS_LOG_ARTIFACT
            )

            task.externalClassLog = variantScope.getArtifactFileCollection(
                COMPILE_CLASSPATH,
                EXTERNAL,
                ArtifactType.DATA_BINDING_BASE_CLASS_LOG_ARTIFACT
            )
        }
    }
}


class DataBindingMergeBaseClassLogDelegate @Inject constructor(private val params: Params) : Runnable {

    data class Params(val file: File, val outFolder: File, val status: FileStatus) : Serializable

    override fun run() {
        if (isClassListFile(params.file.name)) {
            when (params.status) {
                FileStatus.NEW, FileStatus.CHANGED ->
                    FileUtils.copyFile(params.file, File(params.outFolder, params.file.name))

                FileStatus.REMOVED -> {
                    val outFile = File(params.outFolder, params.file.name)
                    if (outFile.exists()) {
                        FileUtils.forceDelete(outFile)
                    }
                }
            }
        }
    }
}
