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

import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.QualifiedContent.DefaultContentType.RESOURCES
import com.android.build.api.transform.QualifiedContent.Scope.EXTERNAL_LIBRARIES
import com.android.build.api.transform.QualifiedContent.Scope.PROJECT
import com.android.build.api.transform.QualifiedContent.Scope.SUB_PROJECTS
import com.android.build.api.transform.QualifiedContent.ScopeType
import com.android.build.gradle.internal.InternalScope
import com.android.build.gradle.internal.packaging.ParsedPackagingOptions
import com.android.build.gradle.internal.packaging.SerializablePackagingOptions
import com.android.build.gradle.internal.pipeline.StreamFilter.PROJECT_RESOURCES
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.builder.files.FileCacheByPath
import com.android.builder.merge.IncrementalFileMergerInput
import com.android.ide.common.resources.FileStatus
import com.android.utils.FileUtils
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.io.Serializable
import javax.inject.Inject

/**
 * Task to merge java resources from multiple modules
 *
 * TODO: Make task cacheable. Using @get:Classpath instead of @get:InputFiles would allow caching
 * but leads to issues with incremental task action: https://github.com/gradle/gradle/issues/1931.
 * We can make task cacheable after gradle implements https://github.com/gradle/gradle/issues/8491.
 */
open class MergeJavaResourceTask
@Inject constructor(workerExecutor: WorkerExecutor) : IncrementalTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var projectJavaRes: FileCollection
        private set

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    var subProjectJavaRes: FileCollection? = null
        private set

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    var externalLibJavaRes: FileCollection? = null
        private set

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    var featureJavaRes: FileCollection? = null
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

    @get:OutputFile
    lateinit var outputFile: File
        private set

    private val workers = Workers.getWorker(workerExecutor)

    override fun isIncremental() = true

    override fun doFullTaskAction() {
        workers.use {
            it.submit(
                MergeJavaResRunnable::class.java,
                MergeJavaResParams(
                    projectJavaRes.files,
                    subProjectJavaRes?.files,
                    externalLibJavaRes?.files,
                    featureJavaRes?.files,
                    outputFile,
                    packagingOptions,
                    incrementalStateFile,
                    false,
                    cacheDir,
                    null
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
                MergeJavaResParams(
                    projectJavaRes.files,
                    subProjectJavaRes?.files,
                    externalLibJavaRes?.files,
                    featureJavaRes?.files,
                    outputFile,
                    packagingOptions,
                    incrementalStateFile,
                    true,
                    cacheDir,
                    changedInputs
                )
            )
        }
    }

    class CreationAction(
        private val mergeScopes: Collection<ScopeType>,
        variantScope: VariantScope
    ) : VariantTaskCreationAction<MergeJavaResourceTask>(variantScope) {

        private val projectJavaResFromStreams: FileCollection?

        override val name: String
            get() = variantScope.getTaskName("merge", "JavaResource")

        override val type: Class<MergeJavaResourceTask>
            get() = MergeJavaResourceTask::class.java

        private lateinit var outputFile: File

        init {
            if (variantScope.needsJavaResStreams) {
                // Because ordering matters for Transform pipeline, we need to fetch the java res
                // as soon as this creation action is instantiated, in needed.
                projectJavaResFromStreams =
                        variantScope.transformManager
                            .getPipelineOutputAsFileCollection(PROJECT_RESOURCES)
                // We must also consume corresponding streams to avoid duplicates; any downstream
                // transforms will use the merged-java-res stream instead.
                variantScope.transformManager
                    .consumeStreams(mutableSetOf(PROJECT), setOf(RESOURCES))
            } else {
                projectJavaResFromStreams = null
            }
        }

        override fun preConfigure(taskName: String) {
            super.preConfigure(taskName)
            outputFile =
                    variantScope.artifacts
                        .appendArtifact(InternalArtifactType.MERGED_JAVA_RES, taskName, "out.jar")
        }

        override fun configure(task: MergeJavaResourceTask) {
            super.configure(task)

            task.projectJavaRes = projectJavaResFromStreams ?: getProjectJavaRes(variantScope)

            if (mergeScopes.contains(SUB_PROJECTS)) {
                task.subProjectJavaRes =
                        variantScope.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.MODULE,
                            AndroidArtifacts.ArtifactType.JAVA_RES
                        )
            }

            if (mergeScopes.contains(EXTERNAL_LIBRARIES)) {
                task.externalLibJavaRes =
                        variantScope.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.EXTERNAL,
                            AndroidArtifacts.ArtifactType.JAVA_RES
                        )
            }

            if (mergeScopes.contains(InternalScope.FEATURES)) {
                task.featureJavaRes =
                        variantScope.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.METADATA_VALUES,
                            AndroidArtifacts.ArtifactScope.MODULE,
                            AndroidArtifacts.ArtifactType.METADATA_JAVA_RES
                        )
            }

            task.mergeScopes = mergeScopes

            task.packagingOptions =
                    SerializablePackagingOptions(
                        variantScope.globalScope.extension.packagingOptions)

            task.intermediateDir =
                    variantScope.getIncrementalDir("${variantScope.fullVariantName}-mergeJavaRes")

            task.cacheDir = File(task.intermediateDir, "zip-cache")

            task.incrementalStateFile = File(task.intermediateDir, "merge-state")

            task.outputFile = outputFile
        }
    }
}

private class MergeJavaResParams(
    val projectJavaRes: Collection<File>,
    val subProjectJavaRes: Collection<File>?,
    val externalLibJavaRes: Collection<File>?,
    val featureJavaRes: Collection<File>?,
    val outputFile: File,
    val packagingOptions: SerializablePackagingOptions,
    val incrementalStateFile: File,
    val isIncremental: Boolean,
    val cacheDir: File,
    val changedInputs: Map<File, FileStatus>?
): Serializable

private class MergeJavaResRunnable @Inject constructor(val params: MergeJavaResParams) : Runnable {
    override fun run() {
        if (!params.isIncremental) {
            FileUtils.deleteIfExists(params.outputFile)
        }
        FileUtils.mkdirs(params.cacheDir)

        val zipCache = FileCacheByPath(params.cacheDir)
        val cacheUpdates = mutableListOf<Runnable>()
        val contentMap = mutableMapOf<IncrementalFileMergerInput, QualifiedContent>()

        val inputMap = mutableMapOf<File, ScopeType>()
        params.projectJavaRes.forEach { inputMap[it] = PROJECT}
        params.subProjectJavaRes?.forEach { inputMap[it] = SUB_PROJECTS}
        params.externalLibJavaRes?.forEach { inputMap[it] = EXTERNAL_LIBRARIES}
        params.featureJavaRes?.forEach { inputMap[it] = InternalScope.FEATURES}

        val inputs =
            toInputs(
                inputMap,
                params.changedInputs,
                zipCache,
                cacheUpdates,
                !params.isIncremental,
                RESOURCES,
                contentMap
            )

        val mergeJavaResDelegate =
            MergeJavaResourcesDelegate(
                inputs,
                params.outputFile,
                contentMap,
                ParsedPackagingOptions(params.packagingOptions),
                RESOURCES,
                params.incrementalStateFile,
                params.isIncremental
            )
        mergeJavaResDelegate.run()
        cacheUpdates.forEach(Runnable::run)
    }
}

fun getProjectJavaRes(scope: VariantScope): FileCollection {
    val javaRes = scope.globalScope.project.files()
    javaRes.from(scope.artifacts.getFinalArtifactFiles(InternalArtifactType.JAVA_RES).get())
    javaRes.from(scope.artifacts.getFinalArtifactFiles(InternalArtifactType.JAVAC).get())
    javaRes.from(scope.variantData.allPreJavacGeneratedBytecode)
    javaRes.from(scope.variantData.allPostJavacGeneratedBytecode)
    javaRes.from(
        scope.artifacts.getFinalArtifactFiles(InternalArtifactType.RUNTIME_R_CLASS_CLASSES).get()
    )
    return javaRes
}

