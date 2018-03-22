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

import com.android.SdkConstants
import com.android.build.api.artifact.BuildableArtifact
import com.android.build.gradle.internal.api.artifact.singleFile
import com.android.build.gradle.internal.res.getAapt2FromMaven
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.tasks.WorkerExecutorAdapter
import com.android.tools.build.bundletool.commands.BuildApksCommand
import com.android.tools.build.bundletool.model.Aapt2Command
import com.android.utils.FileUtils
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.io.Serializable
import javax.inject.Inject

/**
 * Task that generates APKs from a bundle. All the APKs are bundled into a single zip file.
 */
open class BundleToApkTask @Inject constructor(private val workerExecutor: WorkerExecutor) : AndroidVariantTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    lateinit var bundle: BuildableArtifact
        private set

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    lateinit var aapt2FromMaven: FileCollection
        private set

    @get:OutputDirectory
    lateinit var outputDir: File
        private set

    @TaskAction
    fun generateApk() {
        val adapter = WorkerExecutorAdapter<Params>(workerExecutor, BundleToolRunnable::class.java)

        adapter.submit(
            Params(
                bundle.singleFile(),
                File(aapt2FromMaven.singleFile, SdkConstants.FN_AAPT2),
                outputDir
            )
        )

        adapter.taskActionDone()
    }

    private data class Params(
        val bundleFile: File,
        val aapt2File: File,
        val outputDir: File
    ) : Serializable

    private class BundleToolRunnable @Inject constructor(private val params: Params): Runnable {
        override fun run() {
            FileUtils.cleanOutputDir(params.outputDir)

            val command = BuildApksCommand
                .builder()
                .setBundlePath(params.bundleFile.toPath())
                .setOutputDirectory(params.outputDir.toPath())
                .setAapt2Command(Aapt2Command.createFromExecutablePath(params.aapt2File.toPath()))

            command.build().execute()
        }
    }

    class ConfigAction(private val scope: VariantScope) : TaskConfigAction<BundleToApkTask> {

        override fun getName() = scope.getTaskName("makeApkFromBundleFor")
        override fun getType() = BundleToApkTask::class.java

        override fun execute(task: BundleToApkTask) {
            task.variantName = scope.fullVariantName
            task.outputDir = scope.artifacts.appendArtifact(InternalArtifactType.APKS_FROM_BUNDLE, task)
            task.bundle = scope.artifacts.getFinalArtifactFiles(InternalArtifactType.BUNDLE)
            task.aapt2FromMaven = getAapt2FromMaven(scope.globalScope)

        }
    }
}
