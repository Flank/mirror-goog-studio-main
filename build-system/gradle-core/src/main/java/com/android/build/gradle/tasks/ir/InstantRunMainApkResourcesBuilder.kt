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

package com.android.build.gradle.tasks.ir

import com.android.build.api.artifact.ArtifactType
import com.android.build.gradle.internal.aapt.AaptGeneration
import com.android.build.gradle.internal.scope.ExistingBuildElements
import com.android.build.gradle.internal.scope.InternalArtifactType.INSTANT_RUN_MAIN_APK_RESOURCES
import com.android.build.gradle.internal.scope.InternalArtifactType.INSTANT_RUN_MERGED_MANIFESTS
import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.AndroidBuilderTask
import com.android.build.gradle.internal.transforms.InstantRunSliceSplitApkBuilder
import com.android.build.gradle.internal.transforms.InstantRunSplitApkBuilder
import com.android.builder.internal.aapt.BlockingResourceLinker
import com.android.builder.utils.FileCache
import com.android.ide.common.build.ApkInfo
import com.android.ide.common.process.ProcessException
import com.google.common.collect.ImmutableList
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.IOException

/**
 * Task to create the main APK resources.ap_ file. This file will only contain the merged
 * manifest that must be packaged in the main apk, all the resources are packaged in a separate
 * APK.
 *
 * This task should only run when targeting an Android platform 26 and above.
 *
 */
open class InstantRunMainApkResourcesBuilder : AndroidBuilderTask() {

    private lateinit var fileCache: FileCache
    private lateinit var aaptIntermediateFolder: File

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var resourceFiles: FileCollection private set

    private lateinit var aaptGeneration: AaptGeneration
    @Input
    fun getAaptGenerationString() = aaptGeneration.toString()

    @get:OutputDirectory
    lateinit var outputDirectory: File private set

    @get:InputFiles
    lateinit var manifestFiles: FileCollection private set

    @TaskAction
    @Throws(IOException::class)
    fun doFullTaskAction() {

        // at this point, there should only be one instant-run merged manifest, but this may
        // change in the future.
        ExistingBuildElements.from(INSTANT_RUN_MERGED_MANIFESTS, manifestFiles)
                .transform { apkData, processedResources ->
                    processSplit(apkData, processedResources) }
                .into(INSTANT_RUN_MAIN_APK_RESOURCES, outputDirectory)
    }

    @Throws(IOException::class)
    protected open fun processSplit(apkData: ApkInfo, manifestFile: File?): File? {
        if (manifestFile == null) {
            return null
        }

        try {
            return InstantRunSplitApkBuilder.getLinker(
                        aaptGeneration, builder, aaptIntermediateFolder).use { aapt ->
                processSplit(manifestFile, aapt)
            }
        } catch (e: InterruptedException) {
            Thread.interrupted()
            throw IOException("Exception while generating InstantRun main resources APK", e)
        } catch (e: ProcessException) {
            throw IOException("Exception while generating InstantRun main resources APK", e)
        }
    }

    // use default values for aaptOptions since we don't package any resources.
    private fun processSplit(manifestFile: File, aapt: BlockingResourceLinker): File =
            InstantRunSliceSplitApkBuilder.generateSplitApkResourcesAp(
                    logger,
                    aapt,
                    manifestFile,
                    outputDirectory,
                    com.android.builder.internal.aapt.AaptOptions(
                            ImmutableList.of(), false, ImmutableList.of()),
                    builder,
                    resourceFiles,
                    "main_resources")

    class ConfigAction(
            val variantScope: VariantScope,
            private val taskInputType: ArtifactType) :
            TaskConfigAction<InstantRunMainApkResourcesBuilder> {

        override fun getName() = variantScope.getTaskName("instantRunMainApkResources")

        override fun getType() = InstantRunMainApkResourcesBuilder::class.java

        override fun execute(task: InstantRunMainApkResourcesBuilder) {
            task.variantName = variantScope.fullVariantName
            task.setAndroidBuilder(variantScope.globalScope.androidBuilder)

            task.resourceFiles = variantScope.getOutput(taskInputType)
            task.manifestFiles = variantScope.getOutput(INSTANT_RUN_MERGED_MANIFESTS)
            task.outputDirectory = variantScope.instantRunMainApkResourcesDir
            task.aaptGeneration = AaptGeneration.fromProjectOptions(
                    variantScope.globalScope.projectOptions)
            task.fileCache = variantScope.globalScope.buildCache!!
            task.aaptIntermediateFolder = File(variantScope.getIncrementalDir(name), "aapt-temp")

            variantScope.addTaskOutput(INSTANT_RUN_MAIN_APK_RESOURCES, task.outputDirectory, name)
        }

    }
}
