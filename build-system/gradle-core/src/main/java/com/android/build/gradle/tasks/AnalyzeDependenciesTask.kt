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

package com.android.build.gradle.tasks

import com.android.SdkConstants
import com.android.build.api.artifact.BuildableArtifact
import com.android.build.gradle.internal.scope.AnchorOutputType
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.utils.FileUtils
import com.google.gson.GsonBuilder
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider

abstract class AnalyzeDependenciesTask: NonIncrementalTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var compiledClasses: BuildableArtifact
        private set

    @get:OutputFile
    abstract val outputFile: DirectoryProperty

    override fun doTaskAction() {
        val analyzer = DependenciesAnalyzer()
        val dependencies = mutableSetOf<String>()

        compiledClasses.files.forEach { file ->
            file.walk().forEach { classFile ->
                val name = classFile.name
                if (classFile.isFile && name.endsWith(SdkConstants.DOT_CLASS)) {
                    dependencies.addAll(analyzer.findAllDependencies(classFile))
                }
            }
        }

        outputResults(dependencies)
    }

    class CreationAction(val scope: VariantScope) :
        VariantTaskCreationAction<AnalyzeDependenciesTask>(scope) {

        override val name: String
            get() = scope.getTaskName("analyze", "Dependencies")
        override val type: Class<AnalyzeDependenciesTask>
            get() = AnalyzeDependenciesTask::class.java

        override fun configure(task: AnalyzeDependenciesTask) {
            super.configure(task)

            task.compiledClasses = scope.artifacts.getFinalArtifactFiles(AnchorOutputType.ALL_CLASSES)
        }

        override fun handleProvider(taskProvider: TaskProvider<out AnalyzeDependenciesTask>) {
            super.handleProvider(taskProvider)

            variantScope.artifacts.producesDir(
                artifactType = InternalArtifactType.USED_DEPENDENCIES,
                operationType = BuildArtifactsHolder.OperationType.INITIAL,
                taskProvider = taskProvider,
                productProvider = AnalyzeDependenciesTask::outputFile,
                fileName = "dependencies.json"
            )
        }
    }

    private fun outputResults(dependencies: Set<String>) {
        FileUtils.writeToFile(
            outputFile.asFile.get(),
            GsonBuilder().setPrettyPrinting().create().toJson(dependencies))
        println(outputFile.asFile.get().path)
    }

}