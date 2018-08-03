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
import com.android.build.gradle.internal.api.artifact.singlePath
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.res.getAapt2FromMaven
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.OutputScope
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.AndroidBuilderTask
import com.android.build.gradle.internal.tasks.Workers
import com.android.build.gradle.options.BooleanOption
import com.android.builder.core.VariantTypeImpl
import com.android.builder.internal.aapt.AaptOptions
import com.android.builder.internal.aapt.AaptPackageConfig
import com.android.sdklib.IAndroidTarget
import com.android.utils.FileUtils
import com.google.common.collect.ImmutableList
import com.google.common.collect.Iterables
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
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
open class ProcessAndroidAppResourcesTask
@Inject constructor(workerExecutor: WorkerExecutor) : AndroidBuilderTask() {

    private val workers = Workers.getWorker(workerExecutor)


    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) lateinit var manifestFileDirectory: BuildableArtifact private set
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) lateinit var thisSubProjectStaticLibrary: BuildableArtifact private set
    @get:InputFiles @get:PathSensitive(PathSensitivity.NONE) lateinit var libraryDependencies: FileCollection private set
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    @get:Optional
    var convertedLibraryDependencies: BuildableArtifact? = null
        private set
    @get:InputFiles @get:PathSensitive(PathSensitivity.NONE) lateinit var sharedLibraryDependencies: FileCollection private set
    @get:InputFiles @get:Optional @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var aapt2FromMaven: FileCollection private set

    @get:OutputDirectory lateinit var aaptIntermediateDir: File private set
    @get:OutputDirectory lateinit var rClassSource: File private set
    @get:OutputFile lateinit var resourceApUnderscore: File private set

    @get:Internal lateinit var outputScope: OutputScope private set

    @TaskAction
    fun taskAction() {
        val staticLibraries = ImmutableList.builder<File>()
        staticLibraries.addAll(libraryDependencies.files)
        convertedLibraryDependencies?.singlePath()?.let { convertedDir ->
            Files.list(convertedDir).use { convertedLibraries ->
                convertedLibraries.forEach { staticLibraries.add(it.toFile()) }
            }
        }
        staticLibraries.add(thisSubProjectStaticLibrary.single())
        val config = AaptPackageConfig(
                androidJarPath = builder.target.getPath(IAndroidTarget.ANDROID_JAR),
                manifestFile = (File(Iterables.getOnlyElement(manifestFileDirectory),
                        SdkConstants.ANDROID_MANIFEST_XML)),
                options = AaptOptions(null, false, null),
                staticLibraryDependencies = staticLibraries.build(),
                imports = ImmutableList.copyOf(sharedLibraryDependencies.asIterable()),
                sourceOutputDir = rClassSource,
                resourceOutputApk = resourceApUnderscore,
                variantType = VariantTypeImpl.LIBRARY,
                intermediateDir = aaptIntermediateDir)

        val aapt2ServiceKey = registerAaptService(
            aapt2FromMaven = aapt2FromMaven, logger = iLogger
        )
        workers.use {
            it.submit(Aapt2LinkRunnable::class.java,
                Aapt2LinkRunnable.Params(aapt2ServiceKey, config))
        }
    }

    class CreationAction(private val scope: VariantScope) :
        TaskCreationAction<ProcessAndroidAppResourcesTask>() {

        override val name: String
            get() = scope.getTaskName("process", "Resources")
        override val type: Class<ProcessAndroidAppResourcesTask>
            get() = ProcessAndroidAppResourcesTask::class.java

        override fun execute(task: ProcessAndroidAppResourcesTask) {
            task.variantName = scope.fullVariantName
            val artifacts = scope.artifacts
            task.manifestFileDirectory =
                    if (artifacts.hasArtifact(InternalArtifactType.AAPT_FRIENDLY_MERGED_MANIFESTS)) {
                        artifacts.getFinalArtifactFiles(InternalArtifactType.AAPT_FRIENDLY_MERGED_MANIFESTS)
                    } else {
                        artifacts.getFinalArtifactFiles(InternalArtifactType.MERGED_MANIFESTS)
                    }
            task.thisSubProjectStaticLibrary = scope.artifacts.getFinalArtifactFiles(
                InternalArtifactType.RES_STATIC_LIBRARY)
            task.libraryDependencies =
                    scope.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.ALL,
                            AndroidArtifacts.ArtifactType.RES_STATIC_LIBRARY)
            if (scope.globalScope.extension.aaptOptions.namespaced &&
                scope.globalScope.projectOptions.get(BooleanOption.CONVERT_NON_NAMESPACED_DEPENDENCIES)) {
                task.convertedLibraryDependencies =
                        scope
                            .artifacts
                            .getArtifactFiles(
                                InternalArtifactType.RES_CONVERTED_NON_NAMESPACED_REMOTE_DEPENDENCIES)
            }
            task.sharedLibraryDependencies =
                    scope.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.ALL,
                            AndroidArtifacts.ArtifactType.RES_SHARED_STATIC_LIBRARY)

            task.outputScope = scope.outputScope
            task.aaptIntermediateDir =
                    FileUtils.join(
                            scope.globalScope.intermediatesDir, "res-process-intermediate", scope.variantConfiguration.dirName)
            task.rClassSource = artifacts.appendArtifact(
                InternalArtifactType.RUNTIME_R_CLASS_SOURCES,
                task)
            task.resourceApUnderscore = scope.artifacts
                .appendArtifact(
                    InternalArtifactType.PROCESSED_RES,
                    task,
                    "res.apk")
            task.setAndroidBuilder(scope.globalScope.androidBuilder)
            task.aapt2FromMaven = getAapt2FromMaven(scope.globalScope)
        }
    }

}
