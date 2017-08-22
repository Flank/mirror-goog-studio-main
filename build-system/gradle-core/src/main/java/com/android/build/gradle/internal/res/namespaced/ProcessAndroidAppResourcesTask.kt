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
import com.android.build.gradle.internal.scope.OutputScope
import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.build.gradle.internal.scope.TaskOutputHolder
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.AndroidBuilderTask
import com.android.builder.core.VariantType
import com.android.builder.internal.aapt.AaptOptions
import com.android.builder.internal.aapt.AaptPackageConfig
import com.android.builder.internal.aapt.v2.AaptV2Jni
import com.android.builder.utils.FileCache
import com.android.ide.common.internal.WaitableExecutor
import com.android.ide.common.process.LoggedProcessOutputHandler
import com.android.utils.FileUtils
import com.google.common.collect.ImmutableList
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Task to link the resource and the AAPT2 static libraries of its dependencies.
 *
 * Currently only implemented for tests. TODO: Clean up split support so this can support splits too.
 *
 * Outputs an ap_ file that can then be merged with the rest of the app to become a functioning apk,
 * as well as the generated R classes for this app that can be compiled against.
 */
@CacheableTask
open class ProcessAndroidAppResourcesTask : AndroidBuilderTask() {

    @get:InputFiles lateinit var manifestFileDirectory: FileCollection
    @get:InputFiles lateinit var thisSubProjectStaticLibrary: FileCollection
    @get:InputFiles lateinit var libraryDependencies: FileCollection

    @get:OutputDirectory lateinit var aaptIntermediateDir: File
    @get:OutputDirectory lateinit var rClassSource: File
    @get:OutputFile lateinit var resourceApUnderscore: File

    @get:Internal var fileCache: FileCache? = null
    @get:Internal lateinit var outputScope: OutputScope

    @TaskAction
    fun taskAction() {

        val aapt = AaptV2Jni(
                aaptIntermediateDir,
                WaitableExecutor.useGlobalSharedThreadPool(),
                LoggedProcessOutputHandler(iLogger),
                fileCache)


        val staticLibraries = ImmutableList.builder<File>()
        staticLibraries.addAll(libraryDependencies.files)
        staticLibraries.add(thisSubProjectStaticLibrary.singleFile)
        val config =
                AaptPackageConfig.Builder()
                        .setAndroidTarget(builder.target)
                        .setManifestFile(File(manifestFileDirectory.singleFile, SdkConstants.ANDROID_MANIFEST_XML))
                        .setOptions(AaptOptions(null, false, null))
                        .setLibrarySymbolTableFiles(null)
                        .setStaticLibraryDependencies(staticLibraries.build())
                        .setSourceOutputDir(rClassSource)
                        .setResourceOutputApk(resourceApUnderscore)
                        .setVariantType(VariantType.LIBRARY)
                        .setLogger(iLogger)
                        .setBuildToolInfo(builder.buildToolInfo)
                        .build()
        val result = aapt.link(config)
        result.get()

    }

    class ConfigAction(
            private val scope: VariantScope,
            private val rClassSource: File,
            private val resourceApUnderscore: File,
            val isLibrary: Boolean) : TaskConfigAction<ProcessAndroidAppResourcesTask> {

        override fun getName(): String {
            return scope.getTaskName("process", "Resources")
        }

        override fun getType(): Class<ProcessAndroidAppResourcesTask> {
            return ProcessAndroidAppResourcesTask::class.java
        }

        override fun execute(task: ProcessAndroidAppResourcesTask) {
            task.variantName = scope.fullVariantName
            task.manifestFileDirectory =
                    if (scope.hasOutput(TaskOutputHolder.TaskOutputType.AAPT_FRIENDLY_MERGED_MANIFESTS)) {
                        scope.getOutput(TaskOutputHolder.TaskOutputType.AAPT_FRIENDLY_MERGED_MANIFESTS)
                    } else {
                        scope.getOutput(TaskOutputHolder.TaskOutputType.MERGED_MANIFESTS)
                    }
            task.thisSubProjectStaticLibrary = scope.getOutput(TaskOutputHolder.TaskOutputType.RES_STATIC_LIBRARY)
            task.libraryDependencies =
                    scope.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.ALL,
                            AndroidArtifacts.ArtifactType.RES_STATIC_LIBRARY)

            task.outputScope = scope.outputScope
            task.aaptIntermediateDir =
                    FileUtils.join(
                            scope.globalScope.intermediatesDir, "res-process-intermediate", scope.variantConfiguration.dirName)
            task.rClassSource = rClassSource
            task.resourceApUnderscore = resourceApUnderscore
            task.setAndroidBuilder(scope.globalScope.androidBuilder)
            task.fileCache = scope.globalScope.buildCache
        }
    }

}
