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

package com.android.build.gradle.tasks

import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.BuildElements
import com.android.build.gradle.internal.scope.BuildElementsCopyParams
import com.android.build.gradle.internal.scope.BuildElementsCopyRunnable
import com.android.build.gradle.internal.scope.BuildOutput
import com.android.build.gradle.internal.scope.ExistingBuildElements
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.utils.FileUtils
import com.google.common.collect.ImmutableList
import org.gradle.api.file.Directory
import java.io.File
import java.util.ArrayList
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskProvider

/**
 * Copy the location our various tasks outputs into a single location.
 *
 * <p>This is useful when having configuration or feature splits which are located in different
 * folders since they are produced by different tasks.
 */
abstract class CopyOutputs : NonIncrementalTask() {

    @get:OutputDirectory
    abstract val destinationDir: DirectoryProperty

    @get:InputFiles
    abstract val fullApks: DirectoryProperty

    @get:InputFiles
    @get:Optional
    abstract val abiSplits: DirectoryProperty

    @get:InputFiles
    @get:Optional
    abstract val resourcesSplits: DirectoryProperty

    // FIX ME : add incrementality
    override fun doTaskAction() {
        FileUtils.cleanOutputDir(destinationDir.get().asFile)

        val buildElementsCallables = ArrayList<Callable<BuildElements>>()

        buildElementsCallables.add(copy(InternalArtifactType.FULL_APK, fullApks))
        buildElementsCallables.add(copy(InternalArtifactType.ABI_PACKAGED_SPLIT, abiSplits))
        buildElementsCallables.add(
            copy(
                InternalArtifactType.DENSITY_OR_LANGUAGE_PACKAGED_SPLIT,
                resourcesSplits
            )
        )

        val buildOutputs = ImmutableList.builder<BuildOutput>()

        for (buildElementsCallable in buildElementsCallables) {
            try {
                buildOutputs.addAll(buildElementsCallable.call())
            } catch (e: Exception) {
                throw ExecutionException(e)
            }

        }

        BuildElements(buildOutputs.build()).save(destinationDir.get().asFile)
    }

    private fun copy(
        inputType: InternalArtifactType<Directory>,
        inputs: DirectoryProperty
    ): Callable<BuildElements> {
        return ExistingBuildElements.from(inputType, inputs)
            .transform(
                getWorkerFacadeWithWorkers(),
                BuildElementsCopyRunnable::class.java
            ) { _, inputFile ->
                BuildElementsCopyParams(
                    inputFile,
                    File(
                        destinationDir.get().asFile,
                        inputFile.name
                    )
                )
            }
            .intoCallable(InternalArtifactType.APK)
    }

    class CreationAction(variantScope: VariantScope, private val destinationDir: File) :
        VariantTaskCreationAction<CopyOutputs>(variantScope) {

        override val name: String = variantScope.getTaskName("copyOutputs")
        override val type: Class<CopyOutputs> = CopyOutputs::class.java

        override fun handleProvider(taskProvider: TaskProvider<out CopyOutputs>) {
            super.handleProvider(taskProvider)

            variantScope
                .artifacts
                .producesDir(
                    InternalArtifactType.APK,
                    BuildArtifactsHolder.OperationType.INITIAL,
                    taskProvider,
                    CopyOutputs::destinationDir,
                    destinationDir.absolutePath,
                    ""
                )
        }

        override fun configure(task: CopyOutputs) {
            super.configure(task)

            val artifacts = variantScope.artifacts
            artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.FULL_APK,
                task.fullApks
            )
            artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.ABI_PACKAGED_SPLIT, task.abiSplits
            )
            artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.DENSITY_OR_LANGUAGE_PACKAGED_SPLIT,
                task.resourcesSplits
            )
        }
    }
}
