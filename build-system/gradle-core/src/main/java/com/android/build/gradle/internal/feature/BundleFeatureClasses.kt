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

package com.android.build.gradle.internal.feature

import com.android.build.api.artifact.BuildableArtifact
import com.android.build.gradle.internal.api.artifact.BuildableArtifactImpl
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.COMPILE_ONLY_NAMESPACED_R_CLASS_JAR
import com.android.build.gradle.internal.res.namespaced.JarRequest
import com.android.build.gradle.internal.res.namespaced.JarWorkerRunnable
import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.Workers
import com.android.ide.common.workers.WorkerExecutorFacade
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.ReproducibleFileVisitor
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.IsolationMode
import org.gradle.workers.WorkerExecutor
import java.io.File
import javax.inject.Inject

/**
 * Task to jar all classes bundled in a feature so that dependent features can compile against those
 * classes without bundling them.
 */
open class BundleFeatureClasses @Inject constructor(workerExecutor: WorkerExecutor) : DefaultTask() {

    val workers: WorkerExecutorFacade = Workers.getWorker(workerExecutor)

    @get:OutputFile lateinit var outputJar: File

    @get:InputFiles lateinit var javacClasses: BuildableArtifact
    @get:InputFiles lateinit var preJavacClasses: FileCollection
    @get:InputFiles lateinit var postJavacClasses: FileCollection
    @get:InputFiles private var thisRClassClasses: BuildableArtifact? = null
    @get:InputFiles private var dependencyRClassClasses: FileCollection? = null

    @TaskAction
    fun merge() {
        val files = HashMap<String, File>()
        val collector = object: ReproducibleFileVisitor {
            override fun isReproducibleFileOrder() = true
            override fun visitFile(fileVisitDetails: FileVisitDetails) {
                files.put(fileVisitDetails.relativePath.pathString, fileVisitDetails.file)
            }
            override fun visitDir(fileVisitDetails: FileVisitDetails) {
            }
        }
        (javacClasses as BuildableArtifactImpl).asFileTree.visit(collector)
        preJavacClasses.asFileTree.visit(collector)
        postJavacClasses.asFileTree.visit(collector)
        thisRClassClasses?.get()?.asFileTree?.visit(collector)

        workers.use {
            it.submit(JarWorkerRunnable::class.java,
                JarRequest(toFile = outputJar,
                    fromJars = dependencyRClassClasses?.files?.toList() ?: listOf(),
                    fromFiles = files))
        }
    }

    class ConfigAction(private val scope: VariantScope) : TaskConfigAction<BundleFeatureClasses> {

        override fun getName() = scope.getTaskName("bundle", "Classes")

        override fun getType() = BundleFeatureClasses::class.java

        override fun execute(task: BundleFeatureClasses) {
            task.outputJar = scope.artifacts.appendArtifact(
                InternalArtifactType.FEATURE_CLASSES, task, "classes.jar")
            task.javacClasses =
                    scope.artifacts.getArtifactFiles(InternalArtifactType.JAVAC)
            task.preJavacClasses = scope.variantData.allPreJavacGeneratedBytecode
            task.postJavacClasses = scope.variantData.allPostJavacGeneratedBytecode
            val globalScope = scope.globalScope
            if (java.lang.Boolean.TRUE == globalScope.extension.aaptOptions.namespaced) {
                task.thisRClassClasses = scope.artifacts
                    .getFinalArtifactFiles(InternalArtifactType.COMPILE_ONLY_NAMESPACED_R_CLASS_JAR)
                task.dependencyRClassClasses = scope.getArtifactFileCollection(
                        AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                        ALL,
                        COMPILE_ONLY_NAMESPACED_R_CLASS_JAR)
            }
        }
    }
}
