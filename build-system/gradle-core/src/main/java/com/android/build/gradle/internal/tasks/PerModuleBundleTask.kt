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
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSetMetadata
import com.android.builder.packaging.JarMerger
import com.android.builder.packaging.ZipEntryFilter
import com.android.utils.FileUtils
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.function.Supplier

/**
 * Task that zips a module's bundle elements into a zip file. This gets published
 * so that the base app can package into the bundle.
 *
 */
open class PerModuleBundleTask : AndroidVariantTask() {

    @get:OutputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var outputDir: File
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

    private lateinit var fileNameSupplier: Supplier<String>

    @get:Input
    val fileName: String
        get() = fileNameSupplier.get()

    @TaskAction
    fun zip() {
        FileUtils.cleanOutputDir(outputDir)
        val jarMerger = JarMerger(File(outputDir, fileName).toPath())

        jarMerger.use { it ->

            it.addDirectory(
                assetsFiles.singleFile.toPath(),
                null,
                null,
                Relocator(FD_ASSETS)
            )

            it.addJar(resFiles.singleFile.toPath(), null, ResRelocator())

            // dex files
            addHybridFolder(it, dexFiles.files, Relocator(FD_DEX))

            addHybridFolder(it, javaResFiles.files,
                Relocator("root"),
                ZipEntryFilter.EXCLUDE_CLASSES)

            addHybridFolder(it, nativeLibsFiles.files)
        }
    }

    private fun addHybridFolder(
        jarMerger: JarMerger,
        files: Set<File>,
        relocator: Relocator? = null,
        fileFilter: ZipEntryFilter? = null ) {
        // in this case the artifact is a folder containing things to add
        // to the zip. These can be file to put directly, jars to copy the content
        // of, or folders
        for (file in files) {
            if (file.isFile) {
                if (file.name.endsWith(SdkConstants.DOT_JAR)) {
                    jarMerger.addJar(file.toPath(), fileFilter, relocator)
                } else if (fileFilter == null || fileFilter.checkEntry(file.name)) {
                    if (relocator != null) {
                        jarMerger.addFile(relocator.relocate(file.name), file.toPath())
                    } else {
                        jarMerger.addFile(file.name, file.toPath())
                    }
                }
            } else {
                jarMerger.addDirectory(
                    file.toPath(),
                    fileFilter,
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

            task.fileNameSupplier = if (variantScope.type.isBaseModule)
                Supplier { "base.zip"}
            else {
                val featureName: Supplier<String> = FeatureSetMetadata.getInstance()
                    .getFeatureNameSupplierForTask(variantScope, task)
                Supplier { "${featureName.get()}.zip"}
            }

            task.outputDir = variantScope.artifacts.appendArtifact(
                InternalArtifactType.MODULE_BUNDLE, task)

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

