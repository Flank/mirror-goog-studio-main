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

import com.android.SdkConstants.DOT_JAR
import com.android.build.api.transform.QualifiedContent
import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.res.namespaced.JarRequest
import com.android.build.gradle.internal.res.namespaced.JarWorkerRunnable
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.builder.dexing.ClassFileInput.CLASS_MATCHER
import com.android.ide.common.workers.WorkerExecutorFacade
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskProvider
import org.gradle.workers.WorkerExecutor
import java.io.File
import javax.inject.Inject

/**
 * A task that merges the project and runtime dependency class files into a single jar.
 *
 * This task is necessary in a feature or base module when minification is enabled in the base
 * because the base needs to know which classes came from which modules to eventually split the
 * classes to the correct APKs via the Dex Splitter.
 */
@CacheableTask
abstract class MergeClassesTask
@Inject constructor(workerExecutor: WorkerExecutor) : NonIncrementalTask() {

    @get:Classpath
    abstract var inputFiles: FileCollection
        protected set

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    private val workers = Workers.preferWorkers(project.name, path, workerExecutor)

    override fun doTaskAction() {
        // We use a delegate here to simplify testing
        MergeClassesDelegate(inputFiles.files, outputFile.get().asFile, workers).mergeClasses()
    }

    class MergeClassesDelegate(
        val inputFiles: Collection<File>,
        val outputFile: File,
        val workers: WorkerExecutorFacade
    ) {
        fun mergeClasses() {
            val fromJars = inputFiles.filter { it.isFile && it.name.endsWith(DOT_JAR) }
            val fromDirectories = inputFiles.filter { it.isDirectory }
            workers.use { workers ->
                workers.submit(
                    JarWorkerRunnable::class.java,
                    JarRequest(
                        toFile = outputFile,
                        fromJars = fromJars,
                        fromDirectories = fromDirectories,
                        filter = { CLASS_MATCHER.test(it) }
                    )
                )
            }
        }
    }

    class CreationAction(
        variantScope: VariantScope
    ) : VariantTaskCreationAction<MergeClassesTask>(variantScope) {
        override val type = MergeClassesTask::class.java
        override val name: String = variantScope.getTaskName("merge", "Classes")

        // Because ordering matters for the transform pipeline, we need to fetch the classes as soon
        // as this creation action is instantiated.
        private val inputFiles =
            variantScope
                .transformManager
                .getPipelineOutputAsFileCollection { contentTypes, scopes ->
                    contentTypes.contains(QualifiedContent.DefaultContentType.CLASSES)
                            && scopes.intersect(TransformManager.SCOPE_FULL_PROJECT).isNotEmpty()
                }

        override fun handleProvider(taskProvider: TaskProvider<out MergeClassesTask>) {
            super.handleProvider(taskProvider)
            variantScope.artifacts.producesFile(
                artifactType = InternalArtifactType.MODULE_AND_RUNTIME_DEPS_CLASSES,
                operationType = BuildArtifactsHolder.OperationType.INITIAL,
                taskProvider = taskProvider,
                product = taskProvider.map(MergeClassesTask::outputFile),
                fileName = if (variantScope.type.isBaseModule) {
                    "base.jar"
                } else {
                    TaskManager.getFeatureFileName(variantScope.globalScope.project.path, DOT_JAR)
                }
            )
        }

        override fun configure(task: MergeClassesTask) {
            super.configure(task)
            task.inputFiles = inputFiles
        }
    }
}
