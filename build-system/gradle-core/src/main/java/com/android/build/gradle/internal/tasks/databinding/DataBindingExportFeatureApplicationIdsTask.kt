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
import android.databinding.tool.store.FeatureInfoList
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.EagerTaskCreationAction
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.Workers
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSplitDeclaration
import com.android.utils.FileUtils
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.tooling.BuildException
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.io.FileNotFoundException
import java.io.Serializable
import javax.inject.Inject

/**
 * This task collects the feature information and exports their ids into a file which can be
 * read by the DataBindingAnnotationProcessor.
 */
@CacheableTask
open class DataBindingExportFeatureApplicationIdsTask @Inject constructor(
    workerExecutor: WorkerExecutor
) : DefaultTask() {
    // where to keep the log of the task
    @get:OutputDirectory lateinit var packageListOutFolder: File
        private set
    @get:InputFiles lateinit var featureDeclarations: FileCollection
        private set

    val workers = Workers.getWorker(workerExecutor)

    @TaskAction
    fun fullTaskAction() {
        workers.use {
            it.submit(ExportApplicationIdsRunnable::class.java,
                ExportApplicationIdsParams(
                    featureDeclarations = featureDeclarations.asFileTree.files,
                    packageListOutFolder = packageListOutFolder
                )
            )
        }
    }

    class CreationAction(
        private val variantScope: VariantScope
    ) :
        EagerTaskCreationAction<DataBindingExportFeatureApplicationIdsTask>() {

        override val name: String
            get() = variantScope.getTaskName("dataBindingExportFeaturePackageIds")
        override val type: Class<DataBindingExportFeatureApplicationIdsTask>
            get() = DataBindingExportFeatureApplicationIdsTask::class.java

        override fun execute(task: DataBindingExportFeatureApplicationIdsTask) {
            task.packageListOutFolder = variantScope.artifacts
                .appendArtifact(InternalArtifactType.FEATURE_DATA_BINDING_BASE_FEATURE_INFO, task)
            task.featureDeclarations = variantScope.getArtifactFileCollection(
                    AndroidArtifacts.ConsumedConfigType.METADATA_VALUES,
                    AndroidArtifacts.ArtifactScope.MODULE,
                    AndroidArtifacts.ArtifactType.METADATA_FEATURE_DECLARATION
            )
        }
    }
}

data class ExportApplicationIdsParams(
    val featureDeclarations: Set<File>,
    val packageListOutFolder: File
) : Serializable

class ExportApplicationIdsRunnable @Inject constructor(
    val params: ExportApplicationIdsParams
) : Runnable {
    override fun run() {
        val packages = mutableSetOf<String>()
        for (featureSplitDeclaration in params.featureDeclarations) {
            try {
                val loaded = FeatureSplitDeclaration.load(featureSplitDeclaration)
                packages.add(loaded.applicationId)
            } catch (e: FileNotFoundException) {
                throw BuildException("Cannot read features split declaration file", e)
            }
        }
        FileUtils.cleanOutputDir(params.packageListOutFolder)
        params.packageListOutFolder.mkdirs()
        // save the list.
        FeatureInfoList(packages).serialize(
                File(
                        params.packageListOutFolder,
                        DataBindingBuilder.FEATURE_PACKAGE_LIST_FILE_NAME
                )
        )
    }
}