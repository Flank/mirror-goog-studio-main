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

package com.android.build.gradle.internal.tasks

import com.android.build.api.artifact.BuildableArtifact
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.build.gradle.internal.scope.VariantScope
import com.android.tools.build.bundletool.BuildBundleCommand
import com.android.utils.FileUtils
import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableList
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.file.Path

/**
 * Task that generates the final bundle (.aab) with all the modules.
 */
open class BundleTask : AndroidVariantTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var baseModuleZip: BuildableArtifact
        private set

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var featureZips: FileCollection
        private set

    @get:OutputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var bundleFile: File
        private set

    private lateinit var _configFile: File

    @get:Optional
    @get:InputFile
    @Suppress("MemberVisibilityCanPrivate")
    val configFile: File?
        get() = if (_configFile.exists()) _configFile else null

    @TaskAction
    fun bundleModules() {
        // BundleTool requires that the destination directory for the bundle file exists,
        // and that the bundle file itself does not
        FileUtils.mkdirs(bundleFile.parentFile)

        if (bundleFile.isFile) {
            FileUtils.delete(bundleFile)
        }

        val builder = ImmutableList.builder<Path>()
        builder.add(getBundlePath(baseModuleZip.files.single()))
        featureZips.forEach { builder.add(getBundlePath(it)) }

        val command = BuildBundleCommand.builder().setOutputPath(bundleFile.toPath())
            .setModulesPaths(builder.build())
        configFile?.let {
            command.setBundleConfigPath(it.toPath())
        }

        command.build().execute()
    }

    private fun getBundlePath(folder: File): Path {
        val children = folder.listFiles()
        Preconditions.checkNotNull(children)
        Preconditions.checkState(children.size == 1)

        return children[0].toPath()
    }

    class ConfigAction(private val scope: VariantScope) : TaskConfigAction<BundleTask> {

        override fun getName() = scope.getTaskName("bundle")
        override fun getType() = BundleTask::class.java

        override fun execute(task: BundleTask) {
            task.variantName = scope.fullVariantName

            // FIXME we need to improve the location of this.
            task.bundleFile = scope.artifacts.appendArtifact(InternalArtifactType.BUNDLE, task, "bundle.aab")

            task.baseModuleZip = scope.artifacts.getFinalArtifactFiles(InternalArtifactType.MODULE_BUNDLE)

            task.featureZips = scope.getArtifactFileCollection(
                AndroidArtifacts.ConsumedConfigType.METADATA_VALUES,
                AndroidArtifacts.ArtifactScope.ALL,
                AndroidArtifacts.ArtifactType.MODULE_BUNDLE)

            task._configFile = scope.globalScope.project.file("BundleConfig.xml")
        }
    }
}
