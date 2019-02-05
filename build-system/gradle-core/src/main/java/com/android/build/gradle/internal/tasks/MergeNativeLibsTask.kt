/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.build.api.transform.QualifiedContent.Scope.EXTERNAL_LIBRARIES
import com.android.build.api.transform.QualifiedContent.Scope.SUB_PROJECTS
import com.android.build.api.transform.QualifiedContent.ScopeType
import com.android.build.gradle.internal.packaging.SerializablePackagingOptions
import com.android.build.gradle.internal.pipeline.ExtendedContentType.NATIVE_LIBS
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_NATIVE_LIBS
import com.android.build.gradle.internal.scope.InternalArtifactType.RENDERSCRIPT_LIB
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.ide.common.resources.FileStatus
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.workers.WorkerExecutor
import java.io.File
import javax.inject.Inject

/**
 * Task to merge native libs from multiple modules
 */
@CacheableTask
open class MergeNativeLibsTask
@Inject constructor(workerExecutor: WorkerExecutor, objects: ObjectFactory) : IncrementalTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var projectNativeLibs: FileCollection
        private set

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    var subProjectNativeLibs: FileCollection? = null
        private set

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    var externalLibNativeLibs: FileCollection? = null
        private set

    @get:Input
    lateinit var mergeScopes: Collection<ScopeType>
        private set

    @get:Nested
    lateinit var packagingOptions: SerializablePackagingOptions
        private set

    private lateinit var intermediateDir: File

    @get:OutputDirectory
    lateinit var cacheDir: File
        private set

    private lateinit var incrementalStateFile: File

    @get:OutputDirectory
    val outputDir: DirectoryProperty = objects.directoryProperty()

    private val workers = Workers.getWorker(path, workerExecutor)

    override fun isIncremental() = true

    override fun doFullTaskAction() {
        workers.use {
            it.submit(
                MergeJavaResRunnable::class.java,
                MergeJavaResRunnable.Params(
                    projectNativeLibs.files,
                    subProjectNativeLibs?.files,
                    externalLibNativeLibs?.files,
                    null,
                    outputDir.get().asFile,
                    packagingOptions,
                    incrementalStateFile,
                    false,
                    cacheDir,
                    null,
                    NATIVE_LIBS
                )
            )
        }
    }

    override fun doIncrementalTaskAction(changedInputs: MutableMap<File, FileStatus>) {
        if (!incrementalStateFile.isFile) {
            doFullTaskAction()
            return
        }
        workers.use {
            it.submit(
                MergeJavaResRunnable::class.java,
                MergeJavaResRunnable.Params(
                    projectNativeLibs.files,
                    subProjectNativeLibs?.files,
                    externalLibNativeLibs?.files,
                    null,
                    outputDir.get().asFile,
                    packagingOptions,
                    incrementalStateFile,
                    true,
                    cacheDir,
                    changedInputs,
                    NATIVE_LIBS
                )
            )
        }
    }

    class CreationAction(
        private val mergeScopes: Collection<ScopeType>,
        variantScope: VariantScope
    ) : VariantTaskCreationAction<MergeNativeLibsTask>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("merge", "NativeLibs")

        override val type: Class<MergeNativeLibsTask>
            get() = MergeNativeLibsTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<out MergeNativeLibsTask>) {
            super.handleProvider(taskProvider)

            variantScope.artifacts.producesDir(
                MERGED_NATIVE_LIBS,
                BuildArtifactsHolder.OperationType.APPEND,
                taskProvider,
                taskProvider.map { it.outputDir },
                "out"
            )
        }

        override fun configure(task: MergeNativeLibsTask) {
            super.configure(task)

            task.projectNativeLibs = getProjectNativeLibs(variantScope)

            if (mergeScopes.contains(SUB_PROJECTS)) {
                task.subProjectNativeLibs = getSubProjectNativeLibs(variantScope)
            }

            if (mergeScopes.contains(EXTERNAL_LIBRARIES)) {
                task.externalLibNativeLibs = getExternalNativeLibs(variantScope)
            }

            task.mergeScopes = mergeScopes
            task.packagingOptions =
                    SerializablePackagingOptions(
                        variantScope.globalScope.extension.packagingOptions)
            task.intermediateDir =
                    variantScope.getIncrementalDir(
                        "${variantScope.fullVariantName}-mergeNativeLibs")
            task.cacheDir = File(task.intermediateDir, "zip-cache")
            task.incrementalStateFile = File(task.intermediateDir, "merge-state")
        }
    }
}

fun getProjectNativeLibs(scope: VariantScope): FileCollection {
    val nativeLibs = scope.globalScope.project.files()
    // add merged project native libs
    nativeLibs.from(
        scope.artifacts.getFinalProduct<Directory>(InternalArtifactType.MERGED_JNI_LIBS)
    )
    // add content of the local external native build
    val project = scope.globalScope.project
    val taskContainer = scope.taskContainer
    if (taskContainer.externalNativeJsonGenerator != null) {
        nativeLibs.from(
            project
                .files(taskContainer.externalNativeJsonGenerator?.objFolder)
                .builtBy(taskContainer.externalNativeBuildTask?.name)
        )
    }
    // add renderscript compilation output if support mode is enabled.
    if (scope.variantConfiguration.renderscriptSupportModeEnabled) {
        val rsFileCollection: ConfigurableFileCollection =
                project.files(scope.artifacts.getFinalArtifactFiles(RENDERSCRIPT_LIB).get())
        val rsLibs = scope.globalScope.sdkComponents.supportNativeLibFolderProvider.orNull
        if (rsLibs?.isDirectory != null) {
            rsFileCollection.from(rsLibs)
        }
        if (scope.variantConfiguration.renderscriptSupportModeBlasEnabled) {
            val rsBlasLib = scope.globalScope.sdkComponents.supportBlasLibFolderProvider.orNull
            if (rsBlasLib == null || !rsBlasLib.isDirectory) {
                throw GradleException(
                    "Renderscript BLAS support mode is not supported in BuildTools $rsBlasLib"
                )
            } else {
                rsFileCollection.from(rsBlasLib)
            }
        }
        nativeLibs.from(rsFileCollection)
    }
    return nativeLibs
}

fun getSubProjectNativeLibs(scope: VariantScope): FileCollection {
    val nativeLibs = scope.globalScope.project.files()
    nativeLibs.from(
        scope.getArtifactFileCollection(
            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
            AndroidArtifacts.ArtifactScope.MODULE,
            AndroidArtifacts.ArtifactType.JAVA_RES
        )
    )
    nativeLibs.from(
        scope.getArtifactFileCollection(
            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
            AndroidArtifacts.ArtifactScope.MODULE,
            AndroidArtifacts.ArtifactType.JNI
        )
    )
    return nativeLibs
}

fun getExternalNativeLibs(scope: VariantScope): FileCollection {
    val nativeLibs = scope.globalScope.project.files()
    nativeLibs.from(
        scope.getArtifactFileCollection(
            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
            AndroidArtifacts.ArtifactScope.EXTERNAL,
            AndroidArtifacts.ArtifactType.JAVA_RES
        )
    )
    nativeLibs.from(
        scope.getArtifactFileCollection(
            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
            AndroidArtifacts.ArtifactScope.EXTERNAL,
            AndroidArtifacts.ArtifactType.JNI
        )
    )
    return nativeLibs
}
