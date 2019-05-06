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
import com.android.build.api.artifact.BuildableArtifact
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.api.artifact.singlePath
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.res.getAapt2FromMaven
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.OutputScope
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.Workers
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.SyncOptions
import com.android.builder.core.VariantTypeImpl
import com.android.builder.internal.aapt.AaptOptions
import com.android.builder.internal.aapt.AaptPackageConfig
import com.android.utils.FileUtils
import com.google.common.collect.ImmutableList
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.nio.file.Files
import javax.inject.Inject

/**
 * Task to link the resource and the AAPT2 static libraries of its dependencies.
 *
 * Currently only implemented for tests. TODO: Clean up split support so this can support splits too.
 *
 * Outputs an ap_ file that can then be merged with the rest of the app to become a functioning apk,
 * as well as the generated R classes for this app that can be compiled against.
 */
@CacheableTask
abstract class ProcessAndroidAppResourcesTask
@Inject constructor(workerExecutor: WorkerExecutor) : NonIncrementalTask() {
    private val workers = Workers.preferWorkers(project.name, path, workerExecutor)

    private lateinit var errorFormatMode: SyncOptions.ErrorFormatMode

    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) lateinit var manifestFileDirectory: Provider<Directory> private set
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) lateinit var thisSubProjectStaticLibrary: BuildableArtifact private set
    @get:InputFiles @get:PathSensitive(PathSensitivity.NONE) lateinit var libraryDependencies: FileCollection private set
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    @get:Optional
    var convertedLibraryDependencies: BuildableArtifact? = null
        private set
    @get:InputFiles @get:PathSensitive(PathSensitivity.NONE) lateinit var sharedLibraryDependencies: FileCollection private set
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var aapt2FromMaven: FileCollection private set

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    lateinit var androidJar: Provider<File>
        private set

    @get:OutputDirectory lateinit var aaptIntermediateDir: File private set
    @get:OutputDirectory abstract val rClassSource: DirectoryProperty
    @get:OutputFile abstract val resourceApUnderscore: RegularFileProperty

    @get:Internal lateinit var outputScope: OutputScope private set

    override fun doTaskAction() {
        val staticLibraries = ImmutableList.builder<File>()
        staticLibraries.addAll(libraryDependencies.files)
        convertedLibraryDependencies?.singlePath()?.let { convertedDir ->
            Files.list(convertedDir).use { convertedLibraries ->
                convertedLibraries.forEach { staticLibraries.add(it.toFile()) }
            }
        }
        staticLibraries.add(thisSubProjectStaticLibrary.single())
        val config = AaptPackageConfig(
                androidJarPath = androidJar.get().absolutePath,
                manifestFile = (File(manifestFileDirectory.get().asFile, SdkConstants.ANDROID_MANIFEST_XML)),
                options = AaptOptions(null, false, null),
                staticLibraryDependencies = staticLibraries.build(),
                imports = ImmutableList.copyOf(sharedLibraryDependencies.asIterable()),
                sourceOutputDir = rClassSource.get().asFile,
                resourceOutputApk = resourceApUnderscore.get().asFile,
                variantType = VariantTypeImpl.LIBRARY,
                intermediateDir = aaptIntermediateDir)

        val aapt2ServiceKey = registerAaptService(
            aapt2FromMaven = aapt2FromMaven, logger = LoggerWrapper(logger)
        )
        workers.use {
            it.submit(
                Aapt2LinkRunnable::class.java,
                Aapt2LinkRunnable.Params(aapt2ServiceKey, config, errorFormatMode)
            )
        }
    }

    class CreationAction(variantScope: VariantScope) :
        VariantTaskCreationAction<ProcessAndroidAppResourcesTask>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("process", "NamespacedResources")
        override val type: Class<ProcessAndroidAppResourcesTask>
            get() = ProcessAndroidAppResourcesTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<out ProcessAndroidAppResourcesTask>) {
            super.handleProvider(taskProvider)
            variantScope.artifacts.producesDir(
                InternalArtifactType.RUNTIME_R_CLASS_SOURCES,
                BuildArtifactsHolder.OperationType.INITIAL,
                taskProvider,
                taskProvider.map { it.rClassSource },
                fileName = "out"
            )
            // TODO: This is not correct, other location expect a directory for this type.
            variantScope.artifacts.producesFile(
                InternalArtifactType.PROCESSED_RES,
                BuildArtifactsHolder.OperationType.INITIAL,
                taskProvider,
                taskProvider.map { it.resourceApUnderscore },
                "res.apk"

            )
        }

        override fun configure(task: ProcessAndroidAppResourcesTask) {
            super.configure(task)

            val artifacts = variantScope.artifacts
            task.manifestFileDirectory =
                    when {
                        artifacts.hasArtifact(InternalArtifactType.AAPT_FRIENDLY_MERGED_MANIFESTS)
                            -> artifacts.getFinalProduct(InternalArtifactType.AAPT_FRIENDLY_MERGED_MANIFESTS)
                        variantScope.globalScope.projectOptions.get(BooleanOption.IDE_DEPLOY_AS_INSTANT_APP)
                            -> artifacts.getFinalProduct(InternalArtifactType.INSTANT_APP_MANIFEST)
                        else -> artifacts.getFinalProduct(InternalArtifactType.MERGED_MANIFESTS)
                    }
            task.thisSubProjectStaticLibrary = variantScope.artifacts.getFinalArtifactFiles(
                InternalArtifactType.RES_STATIC_LIBRARY)
            task.libraryDependencies =
                    variantScope.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.ALL,
                            AndroidArtifacts.ArtifactType.RES_STATIC_LIBRARY)
            if (variantScope.globalScope.extension.aaptOptions.namespaced &&
                variantScope.globalScope.projectOptions.get(BooleanOption.CONVERT_NON_NAMESPACED_DEPENDENCIES)) {
                task.convertedLibraryDependencies =
                        variantScope
                            .artifacts
                            .getArtifactFiles(
                                InternalArtifactType.RES_CONVERTED_NON_NAMESPACED_REMOTE_DEPENDENCIES)
            }
            task.sharedLibraryDependencies =
                    variantScope.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.ALL,
                            AndroidArtifacts.ArtifactType.RES_SHARED_STATIC_LIBRARY)

            task.outputScope = variantScope.outputScope
            task.aaptIntermediateDir =
                    FileUtils.join(
                            variantScope.globalScope.intermediatesDir, "res-process-intermediate", variantScope.variantConfiguration.dirName)
            task.aapt2FromMaven = getAapt2FromMaven(variantScope.globalScope)
            task.androidJar = variantScope.globalScope.sdkComponents.androidJarProvider
            task.errorFormatMode = SyncOptions.getErrorFormatMode(
                variantScope.globalScope.projectOptions
            )
        }
    }

}
