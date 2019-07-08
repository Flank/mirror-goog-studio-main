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

import com.android.apksig.ApkSigner
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.options.StringOption
import com.android.ide.common.signing.KeystoreHelper
import com.android.utils.FileUtils
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.io.Serializable
import javax.inject.Inject

/**
 * Task that copies the bundle file (.aab) to it's final destination and do final touches like:
 * <ul>
 *     <li>Signing the bundle if credentials are available and it's not a debuggable variant.
 * </ul>
 */
abstract class FinalizeBundleTask @Inject constructor(workerExecutor: WorkerExecutor) :
    NonIncrementalTask() {

    private val workers = Workers.preferWorkers(project.name, path, workerExecutor)

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val intermediaryBundleFile: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    var signingConfig: FileCollection? = null
        private set

    @get:Input
    val finalBundleFileName: String
        get() = finalBundleFile.get().asFile.name

    @get:OutputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val finalBundleFile: RegularFileProperty

    override fun doTaskAction() {
        workers.use {
            it.submit(
                BundleToolRunnable::class.java,
                Params(
                    intermediaryBundleFile = intermediaryBundleFile.get().asFile,
                    finalBundleFile = finalBundleFile.get().asFile,
                    signingConfig = SigningConfigUtils.getOutputFile(signingConfig)
                )
            )
        }
    }

    private data class Params(
        val intermediaryBundleFile: File,
        val finalBundleFile: File,
        val signingConfig: File?) : Serializable

    private class BundleToolRunnable @Inject constructor(private val params: Params): Runnable {
        override fun run() {
            FileUtils.cleanOutputDir(params.finalBundleFile.parentFile)

            SigningConfigUtils.load(params.signingConfig)?.let {
                val certificateInfo =
                    KeystoreHelper.getCertificateInfo(
                        it.storeType,
                        it.storeFile!!,
                        it.storePassword!!,
                        it.keyPassword!!,
                        it.keyAlias!!)
                val signingConfig =
                    ApkSigner.SignerConfig.Builder(
                        it.keyAlias!!.toUpperCase(), certificateInfo.key, listOf(certificateInfo.certificate))
                        .build()
                ApkSigner.Builder(listOf(signingConfig))
                    .setOutputApk(params.finalBundleFile)
                    .setInputApk(params.intermediaryBundleFile)
                    .setV2SigningEnabled(false)
                    .setV3SigningEnabled(false)
                    .setMinSdkVersion(18) // So that RSA + SHA256 are used
                    .build()
                    .sign()
            } ?: run {
                FileUtils.copyFile(params.intermediaryBundleFile, params.finalBundleFile)
            }
        }
    }

    /**
     * CreateAction for a task that will sign the bundle artifact.
     */
    class CreationAction(variantScope: VariantScope) :
        VariantTaskCreationAction<FinalizeBundleTask>(variantScope) {
        override val name: String
            get() = variantScope.getTaskName("sign", "Bundle")

        override val type: Class<FinalizeBundleTask>
            get() = FinalizeBundleTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<out FinalizeBundleTask>) {
            super.handleProvider(taskProvider)

            val bundleName = "${variantScope.globalScope.projectBaseName}-${variantScope.variantConfiguration.baseName}.aab"
            val apkLocationOverride = variantScope.globalScope.projectOptions.get(StringOption.IDE_APK_LOCATION)
            if (apkLocationOverride == null) {
                variantScope.artifacts.producesFile(
                    InternalArtifactType.BUNDLE,
                    BuildArtifactsHolder.OperationType.INITIAL,
                    taskProvider,
                    FinalizeBundleTask::finalBundleFile,
                    bundleName
                )
            } else {
                variantScope.artifacts.producesFile(
                    InternalArtifactType.BUNDLE,
                    BuildArtifactsHolder.OperationType.INITIAL,
                    taskProvider,
                    FinalizeBundleTask::finalBundleFile,
                    FileUtils.join(
                        variantScope.globalScope.project.file(apkLocationOverride),
                        variantScope.variantConfiguration.dirName).absolutePath
                    ,
                    bundleName
                )
            }
        }

        override fun configure(task: FinalizeBundleTask) {
            super.configure(task)

            variantScope.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.INTERMEDIARY_BUNDLE,
                task.intermediaryBundleFile)

            // Don't sign debuggable bundles.
            if (!variantScope.variantConfiguration.buildType.isDebuggable) {
                task.signingConfig = variantScope.signingConfigFileCollection
            }
        }

    }

}
