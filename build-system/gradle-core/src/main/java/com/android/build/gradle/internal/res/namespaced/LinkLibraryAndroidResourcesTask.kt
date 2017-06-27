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
package com.android.build.gradle.internal.res.namespaced

import com.android.SdkConstants
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.BuildOutputs
import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.BaseTask
import com.android.builder.core.VariantType
import com.android.builder.internal.aapt.AaptOptions
import com.android.builder.internal.aapt.AaptPackageConfig
import com.android.builder.internal.aapt.v2.AaptV2Jni
import com.android.builder.utils.FileCache
import com.android.ide.common.internal.WaitableExecutor
import com.android.ide.common.process.LoggedProcessOutputHandler
import com.android.utils.FileUtils
import com.google.common.base.Suppliers
import com.google.common.collect.ImmutableList
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.function.Supplier

/**
 * Task to link the resources in a library project into an AAPT2 static library.
 */
@CacheableTask
open class LinkLibraryAndroidResourcesTask : BaseTask() {

    @get:InputFiles lateinit var manifestFileDirectory: FileCollection
    @get:InputFiles lateinit var inputResourcesDir: FileCollection
    @get:InputFiles lateinit var libraryDependencies: FileCollection
    @get:InputFiles @get:Optional var featureDependencies: FileCollection? = null
    @get:InputFiles @get:Optional var tested: FileCollection? = null

    @get:Internal lateinit var packageForRSupplier: Supplier<String>
    @get:Input val packageForR get() = packageForRSupplier.get()

    @get:OutputDirectory lateinit var aaptIntermediateDir: File
    @get:OutputDirectory @get:Optional var rClassSource: File? = null
    @get:OutputFile lateinit var staticLibApk: File

    @get:Internal var fileCache: FileCache? = null

    @TaskAction
    fun taskAction() {

        val aapt = AaptV2Jni(
                aaptIntermediateDir,
                WaitableExecutor.useDirectExecutor(),
                LoggedProcessOutputHandler(iLogger),
                fileCache)

        val imports = ImmutableList.builder<File>()
        // Link against library dependencies
        imports.addAll(libraryDependencies.files)

        // Link against features
        featureDependencies?.let {
            imports.addAll(
                    it.files
                            .map { BuildOutputs.load(TaskOutputType.PROCESSED_RES, it) }
                            .filter { it.isNotEmpty() }
                            .map { splitOutputs -> splitOutputs.single().outputFile })
        }

        val config =
                AaptPackageConfig.Builder()
                        .setAndroidTarget(builder.target)
                        .setManifestFile(File(manifestFileDirectory.singleFile, SdkConstants.ANDROID_MANIFEST_XML))
                        .setOptions(AaptOptions(null, false, null))
                        .setResourceDir(inputResourcesDir.singleFile)
                        .setLibrarySymbolTableFiles(null)
                        .setIsStaticLibrary(true)
                        .setImports(imports.build())
                        .setSourceOutputDir(rClassSource)
                        .setResourceOutputApk(staticLibApk)
                        .setVariantType(VariantType.LIBRARY)
                        .setCustomPackageForR(packageForR)
                        .setLogger(iLogger)
                        .setBuildToolInfo(builder.buildToolInfo)
                        .build()

        aapt.link(config).get()
    }

    class ConfigAction(
            private val scope: VariantScope,
            private val rClassSource: File?,
            private val staticLibApk: File) : TaskConfigAction<LinkLibraryAndroidResourcesTask> {

        override fun getName() = scope.getTaskName("link", "Resources")

        override fun getType() = LinkLibraryAndroidResourcesTask::class.java

        override fun execute(task: LinkLibraryAndroidResourcesTask) {
            task.variantName = scope.fullVariantName
            task.manifestFileDirectory =
                    if (scope.hasOutput(TaskOutputType.AAPT_FRIENDLY_MERGED_MANIFESTS)) {
                        scope.getOutput(TaskOutputType.AAPT_FRIENDLY_MERGED_MANIFESTS)
                    } else {
                        scope.getOutput(TaskOutputType.MERGED_MANIFESTS)
                    }
            task.inputResourcesDir = scope.getOutput(TaskOutputType.RES_COMPILED_FLAT_FILES)
            task.libraryDependencies =
                    scope.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.ALL,
                            AndroidArtifacts.ArtifactType.RES_STATIC_LIBRARY)

            if (scope.variantData.type == VariantType.FEATURE && !scope.isBaseFeature) {
                task.featureDependencies =
                        scope.getArtifactFileCollection(
                                AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                                AndroidArtifacts.ArtifactScope.MODULE,
                                AndroidArtifacts.ArtifactType.FEATURE_RESOURCE_PKG)
            }

            val testedScope = scope.testedVariantData?.scope
            if (testedScope != null) {
                task.tested = testedScope.getOutput(TaskOutputType.RES_STATIC_LIBRARY)
            }

            task.aaptIntermediateDir =
                    FileUtils.join(
                            scope.globalScope.intermediatesDir, "res-link-intermediate", scope.variantConfiguration.dirName)
            task.rClassSource = rClassSource
            task.staticLibApk = staticLibApk
            task.setAndroidBuilder(scope.globalScope.androidBuilder)
            task.fileCache = scope.globalScope.buildCache
            task.packageForRSupplier = Suppliers.memoize(scope.variantConfiguration::getOriginalApplicationId)
        }
    }

}
