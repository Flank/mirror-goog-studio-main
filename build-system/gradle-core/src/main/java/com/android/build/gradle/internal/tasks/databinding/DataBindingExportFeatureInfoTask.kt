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
import android.databinding.tool.FeaturePackageInfo
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSplitPackageIds
import com.android.utils.FileUtils
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.IsolationMode
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.io.IOException
import java.io.Serializable

import javax.inject.Inject

/**
 * This task collects necessary information for the data binding annotation processor to generate
 * the correct code.
 * <p>
 * It has 2 main functionality:
 * a) copy the package id resource offset for the feature so that data binding can properly offset
 * BR class ids.
 *
 * b) copy the BR-bin files from dependencies FOR WHICH a BR file needs to be generated.
 * These are basically dependencies which need to be packaged by this feature. (e.g. if a library
 * dependency is already a dependency of another feature, its BR class will already have been
 * generated)
 */
open class DataBindingExportFeatureInfoTask @Inject constructor(
    private val workerExecutor: WorkerExecutor
) : DefaultTask() {
    @get:OutputDirectory lateinit var outFolder: File
        private set
    @get:Input lateinit var uniqueIdentifier: String
        private set
    @get:InputFiles lateinit var featurePackageIds: FileCollection
        private set

    /**
     * In a feature, we only need to generate code for its Runtime dependencies as compile
     * dependencies are already available via other dependencies (base feature or another feature)
     */
    @get:InputFiles lateinit var directDependencies: FileCollection
        private set

    @TaskAction
    @Throws(IOException::class)
    fun fullTaskAction() {
        workerExecutor.submit(ExportFeatureInfoRunnable::class.java) {
            it.isolationMode = IsolationMode.NONE
            it.setParams(
                    ExportFeatureInfoParams(
                            outFolder = outFolder,
                            uniqueIdentifier = uniqueIdentifier,
                            featurePackageIds = featurePackageIds.asFileTree.files,
                            directDependencies = directDependencies.asFileTree.files
                    )
            )
        }
    }

    class ConfigAction(
        private val variantScope: VariantScope,
        private var outFolder: File
    ) : TaskConfigAction<DataBindingExportFeatureInfoTask> {
        override fun getName() =
            variantScope.getTaskName("dataBindingExportFeatureInfo")

        override fun getType() = DataBindingExportFeatureInfoTask::class.java

        override fun execute(task: DataBindingExportFeatureInfoTask) {
            task.outFolder = outFolder
            task.directDependencies = variantScope.getArtifactFileCollection(
                    AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.ALL,
                    AndroidArtifacts.ArtifactType.DATA_BINDING_ARTIFACT
            )
            task.featurePackageIds = variantScope.getArtifactFileCollection(
                    AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.MODULE,
                    AndroidArtifacts.ArtifactType.FEATURE_IDS_DECLARATION
            )
            task.uniqueIdentifier = variantScope.globalScope.project.path
        }
    }
}

data class ExportFeatureInfoParams(
    val outFolder: File,
    val uniqueIdentifier: String,
    val featurePackageIds: Set<File>,
    val directDependencies: Set<File>
) : Serializable

class ExportFeatureInfoRunnable @Inject constructor(
    val params: ExportFeatureInfoParams
) : Runnable {
    override fun run() {
        val packageIdFileSet = params.featurePackageIds
        val allDeclarations = FeatureSplitPackageIds.load(packageIdFileSet)
        val packageId = allDeclarations.getIdFor(params.uniqueIdentifier)
        FileUtils.cleanOutputDir(params.outFolder)
        params.outFolder.mkdirs()
        params.directDependencies.filter {
            it.name.endsWith(DataBindingBuilder.BR_FILE_EXT)
        }.forEach {
            FileUtils.copyFile(it, File(params.outFolder, it.name))
        }
        // save the package id offset
        FeaturePackageInfo(packageId = packageId ?: 0).serialize(
                File(params.outFolder, DataBindingBuilder.FEATURE_BR_OFFSET_FILE_NAME)
        )
    }
}