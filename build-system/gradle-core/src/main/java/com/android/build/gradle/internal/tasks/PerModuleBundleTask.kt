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

import com.android.SdkConstants
import com.android.SdkConstants.FD_ASSETS
import com.android.SdkConstants.FD_DEX
import com.android.build.gradle.internal.pipeline.StreamFilter
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.build.gradle.internal.scope.VariantScope
import com.android.builder.packaging.JarMerger
import com.android.utils.FileUtils
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Task that zips a module's bundle elements into a zip file. This gets published
 * so that the base app can package into the bundle.
 *
 */
open class PerModuleBundleTask : AndroidVariantTask() {

    @get:OutputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var outputFile: File
        private set

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var dexFiles: FileCollection
        private set

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var resFiles: FileCollection
        private set

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var javaResFiles: FileCollection
        private set

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var assetsFiles: FileCollection
        private set

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var nativeLibsFiles: FileCollection
        private set

    @TaskAction
    fun zip() {
        FileUtils.mkdirs(outputFile.parentFile)
        val jarMerger = JarMerger(outputFile.toPath())

        jarMerger.use { it ->

            it.addDirectory(
                assetsFiles.singleFile.toPath(),
                null,
                null,
                Relocator(FD_ASSETS)
            )

            it.addJar(resFiles.singleFile.toPath(), null,
                ResRelocator()
            )

            // dex files
            addHybridFolder(it, dexFiles.files,
                Relocator(FD_DEX)
            )

            addHybridFolder(it, javaResFiles.files,
                Relocator("root")
            )

            addHybridFolder(it, nativeLibsFiles.files, null)
        }
    }

    private fun addHybridFolder(jarMerger: JarMerger, files: Set<File>, relocator: Relocator?) {
        // in this case the artifact is a folder containing things to add
        // to the zip. These can be file to put directly, jars to copy the content
        // of, or folders
        for (file in files) {
            if (file.isFile) {
                if (file.name.endsWith(SdkConstants.DOT_JAR)) {
                    jarMerger.addJar(file.toPath(), null, relocator)
                } else {
                    if (relocator != null) {
                        jarMerger.addFile(relocator.relocate(file.name), file.toPath())
                    } else {
                        jarMerger.addFile(file.name, file.toPath())
                    }
                }
            } else {
                jarMerger.addDirectory(
                    file.toPath(),
                    null,
                    null,
                    relocator)
            }
        }
    }


    class ConfigAction(
            private val variantScope: VariantScope
    ) : TaskConfigAction<PerModuleBundleTask> {
        override fun getName() = variantScope.getTaskName("build", "PreBundle")

        override fun getType() = PerModuleBundleTask::class.java

        override fun execute(task: PerModuleBundleTask) {
            task.variantName = variantScope.fullVariantName

            // FIXME with proper feature name computation
            val zipName = if (variantScope.type.isBaseModule)
                "base.zip"
            else
                "${variantScope.globalScope.project.name}.zip"

            task.outputFile = variantScope.buildArtifactsHolder.appendArtifact(
                InternalArtifactType.MODULE_BUNDLE, task, zipName)

            task.assetsFiles = variantScope.getOutput(InternalArtifactType.MERGED_ASSETS)
            task.resFiles = variantScope.getOutput(InternalArtifactType.LINKED_RES_FOR_BUNDLE)
            task.dexFiles = variantScope.transformManager.getPipelineOutputAsFileCollection(
                StreamFilter.DEX)
            task.javaResFiles = variantScope.transformManager.getPipelineOutputAsFileCollection(
                StreamFilter.RESOURCES)
            task.nativeLibsFiles = variantScope.transformManager.getPipelineOutputAsFileCollection(
                StreamFilter.NATIVE_LIBS)
        }
    }
}

private class Relocator(private val prefix: String): JarMerger.Relocator {
    override fun relocate(entryPath: String) = "$prefix/$entryPath"
}


private class ResRelocator : JarMerger.Relocator {
    override fun relocate(entryPath: String) = when(entryPath) {
        SdkConstants.FN_ANDROID_MANIFEST_XML -> "manifest/" + SdkConstants.FN_ANDROID_MANIFEST_XML
        else -> entryPath
    }
}

