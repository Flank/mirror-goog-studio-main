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

import com.android.build.api.transform.QualifiedContent.DefaultContentType.RESOURCES
import com.android.build.api.transform.QualifiedContent.Scope.EXTERNAL_LIBRARIES
import com.android.build.api.transform.QualifiedContent.Scope.PROJECT
import com.android.build.api.transform.QualifiedContent.Scope.SUB_PROJECTS
import com.android.build.api.transform.QualifiedContent.ScopeType
import com.android.build.gradle.internal.InternalScope
import com.android.build.gradle.internal.packaging.SerializablePackagingOptions
import com.android.build.gradle.internal.pipeline.StreamFilter.PROJECT_RESOURCES
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_JAVA_RES
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.ide.common.resources.FileStatus
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskProvider
import org.gradle.workers.WorkerExecutor
import java.io.File
import javax.inject.Inject

/**
 * Task to merge java resources from multiple modules
 */
@CacheableTask
open class MergeJavaResourceTask
@Inject constructor(workerExecutor: WorkerExecutor, objects: ObjectFactory) : IncrementalTask() {

    @get:Classpath
    lateinit var projectJavaRes: FileCollection
        private set

    @get:Classpath
    @get:Optional
    var subProjectJavaRes: FileCollection? = null
        private set

    @get:Classpath
    @get:Optional
    var externalLibJavaRes: FileCollection? = null
        private set

    @get:Classpath
    @get:Optional
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
    val outputFile: RegularFileProperty = objects.fileProperty()

    private val workers = Workers.getWorker(project.name, path, workerExecutor)

    override fun isIncremental() = true

    override fun doFullTaskAction() {
        workers.use {
            it.submit(
                MergeJavaResRunnable::class.java,
                MergeJavaResRunnable.Params(
                    projectJavaRes.files,
                    subProjectJavaRes?.files,
                    externalLibJavaRes?.files,
                    featureJavaRes?.files,
                    outputFile.get().asFile,
                    packagingOptions,
                    incrementalStateFile,
                    false,
                    cacheDir,
                    null,
                    RESOURCES
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
                    projectJavaRes.files,
                    subProjectJavaRes?.files,
                    externalLibJavaRes?.files,
                    featureJavaRes?.files,
                    outputFile.get().asFile,
                    packagingOptions,
                    incrementalStateFile,
                    true,
                    cacheDir,
                    changedInputs,
                    RESOURCES
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

        init {
            if (variantScope.needsJavaResStreams) {
                // Because ordering matters for Transform pipeline, we need to fetch the java res
                // as soon as this creation action is instantiated, if needed.
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

        override fun handleProvider(taskProvider: TaskProvider<out MergeJavaResourceTask>) {
            super.handleProvider(taskProvider)

            variantScope.artifacts.producesFile(
                MERGED_JAVA_RES,
                BuildArtifactsHolder.OperationType.APPEND,
                taskProvider,
                taskProvider.map { it.outputFile },
                "out.jar"
            )
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
        }
    }
}

fun getProjectJavaRes(scope: VariantScope): FileCollection {
    val javaRes = scope.globalScope.project.files()
    javaRes.from(scope.artifacts.getFinalArtifactFiles(InternalArtifactType.JAVA_RES))
    javaRes.from(scope.artifacts.getFinalProduct<Directory>(InternalArtifactType.JAVAC))
    javaRes.from(scope.variantData.allPreJavacGeneratedBytecode)
    javaRes.from(scope.variantData.allPostJavacGeneratedBytecode)
    javaRes.from(
        scope.artifacts.getFinalArtifactFiles(InternalArtifactType.RUNTIME_R_CLASS_CLASSES)
    )
    return javaRes
}
