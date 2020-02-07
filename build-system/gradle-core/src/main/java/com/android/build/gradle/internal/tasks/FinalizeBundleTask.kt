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
import com.android.build.api.component.impl.ComponentPropertiesImpl
import com.android.build.api.variant.VariantOutputConfiguration
import com.android.build.api.variant.impl.BuiltArtifactImpl
import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.scope.ExistingBuildElements
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.signing.SigningConfigProvider
import com.android.build.gradle.internal.signing.SigningConfigProviderParams
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.StringOption
import com.android.ide.common.signing.KeystoreHelper
import com.android.utils.FileUtils
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.io.Serializable
import java.util.Locale
import javax.inject.Inject

/**
 * Task that copies the bundle file (.aab) to it's final destination and do final touches like:
 * <ul>
 *     <li>Signing the bundle if credentials are available and it's not a debuggable variant.
 * </ul>
 */
abstract class FinalizeBundleTask : NonIncrementalTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val intermediaryBundleFile: RegularFileProperty

    @get:Nested
    @get:Optional
    var signingConfig: SigningConfigProvider? = null
        private set

    @get:Input
    val finalBundleFileName: String
        get() = finalBundleFile.get().asFile.name

    @get:OutputFile
    abstract val finalBundleFile: RegularFileProperty

    @get:OutputFile
    abstract val bundleIdeModel: RegularFileProperty

    @get:Input
    abstract val applicationId: Property<String>

    override fun doTaskAction() {
        getWorkerFacadeWithWorkers().use {
            it.submit(
                BundleToolRunnable::class.java,
                Params(
                    intermediaryBundleFile = intermediaryBundleFile.get().asFile,
                    finalBundleFile = finalBundleFile.get().asFile,
                    signingConfig = signingConfig?.convertToParams(),
                    bundleIdeModel = bundleIdeModel.get().asFile,
                    applicationId = applicationId.get(),
                    variantName = variantName
                )
            )
        }
    }

    private data class Params(
        val intermediaryBundleFile: File,
        val finalBundleFile: File,
        val signingConfig: SigningConfigProviderParams?,
        val bundleIdeModel: File,
        val applicationId: String,
        val variantName: String
    ) : Serializable

    private class BundleToolRunnable @Inject constructor(private val params: Params): Runnable {
        override fun run() {
            FileUtils.cleanOutputDir(params.finalBundleFile.parentFile)

            params.signingConfig?.resolve()?.let {
                val certificateInfo =
                    KeystoreHelper.getCertificateInfo(
                        it.storeType,
                        it.storeFile!!,
                        it.storePassword!!,
                        it.keyPassword!!,
                        it.keyAlias!!)
                val signingConfig =
                    ApkSigner.SignerConfig.Builder(
                        it.keyAlias.toUpperCase(Locale.US),
                        certificateInfo.key,
                        listOf(certificateInfo.certificate)
                    )
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

            BuiltArtifactsImpl(
                artifactType = InternalArtifactType.BUNDLE,
                applicationId = params.applicationId,
                variantName = params.variantName,
                elements = listOf(
                    BuiltArtifactImpl(
                        outputFile = params.finalBundleFile.absolutePath,
                        outputType = VariantOutputConfiguration.OutputType.SINGLE)
                )
            ).saveToFile(params.bundleIdeModel)
        }
    }

    /**
     * CreateAction for a task that will sign the bundle artifact.
     */
    class CreationAction(creationConfig: ApkCreationConfig) :
        VariantTaskCreationAction<FinalizeBundleTask, ApkCreationConfig>(
            creationConfig
        ) {
        override val name: String
            get() = computeTaskName("sign", "Bundle")

        override val type: Class<FinalizeBundleTask>
            get() = FinalizeBundleTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<out FinalizeBundleTask>
        ) {
            super.handleProvider(taskProvider)

            val bundleName = "${creationConfig.globalScope.projectBaseName}-${creationConfig.baseName}.aab"
            val apkLocationOverride = creationConfig.globalScope.projectOptions.get(StringOption.IDE_APK_LOCATION)
            if (apkLocationOverride == null) {
                creationConfig.artifacts.producesFile(
                    InternalArtifactType.BUNDLE,
                    taskProvider,
                    FinalizeBundleTask::finalBundleFile,
                    bundleName
                )
            } else {
                creationConfig.artifacts.producesFile(
                    InternalArtifactType.BUNDLE,
                    taskProvider,
                    FinalizeBundleTask::finalBundleFile,
                    FileUtils.join(
                        creationConfig.variantPropertiesApiServices.file(apkLocationOverride),
                        creationConfig.dirName).absolutePath,
                    bundleName
                )
            }

            creationConfig.artifacts.producesFile(
                InternalArtifactType.BUNDLE_IDE_MODEL,
                taskProvider,
                FinalizeBundleTask::bundleIdeModel,
                ExistingBuildElements.METADATA_FILE_NAME
            )
        }

        override fun configure(
            task: FinalizeBundleTask
        ) {
            super.configure(task)

            creationConfig.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.INTERMEDIARY_BUNDLE,
                task.intermediaryBundleFile)

            // Don't sign debuggable bundles.
            if (!creationConfig.debuggable) {
                task.signingConfig = SigningConfigProvider.create(creationConfig as ComponentPropertiesImpl)
            }

            task.applicationId.setDisallowChanges(creationConfig.applicationId)
        }

    }

}
