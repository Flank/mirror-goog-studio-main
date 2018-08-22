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

import com.android.build.api.artifact.BuildableArtifact
import com.android.build.gradle.FeatureExtension
import com.android.build.gradle.internal.api.artifact.singleFile
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import com.android.build.gradle.internal.process.JarSigner
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.build.gradle.options.StringOption
import com.android.builder.packaging.PackagingUtils
import com.android.bundle.Config
import com.android.tools.build.bundletool.commands.BuildBundleCommand
import com.android.utils.FileUtils
import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableList
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.io.Serializable
import java.nio.file.Path
import javax.inject.Inject

/**
 * Task that generates the final bundle (.aab) with all the modules.
 */
open class PackageBundleTask @Inject constructor(workerExecutor: WorkerExecutor) : AndroidVariantTask() {

    private val workers = Workers.getWorker(workerExecutor)

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    lateinit var baseModuleZip: BuildableArtifact
        private set

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    lateinit var featureZips: FileCollection
        private set

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    var mainDexList: BuildableArtifact? = null
        private set

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    var obsfuscationMappingFile: BuildableArtifact? = null
        private set

    @get:Input
    lateinit var aaptOptionsNoCompress: Collection<String>
        private set

    @get:Nested
    lateinit var bundleOptions: BundleOptions
        private set

    @get:InputFile
    @get:Optional
    var keystoreFile: File? = null
        private set

    @get:Input
    @get:Optional
    var keystorePassword: String? = null
        private set

    @get:Input
    @get:Optional
    var keyAlias: String? = null
        private set

    @get:Input
    @get:Optional
    var keyPassword: String? = null
        private set

    @get:OutputDirectory
    @get:PathSensitive(PathSensitivity.NONE)
    val bundleLocation: File
        get() = bundleFile.get().asFile.parentFile

    @get:Input
    val fileName: String
        get() = bundleFile.get().asFile.name

    private lateinit var bundleFile: Provider<RegularFile>

    @TaskAction
    fun bundleModules() {

        val signature = if (keystoreFile != null)
            JarSigner.Signature(keystoreFile!!, keystorePassword, keyAlias, keyPassword)
        else null

        workers.use {
            it.submit(
                BundleToolRunnable::class.java,
                Params(
                    baseModuleFile = baseModuleZip.singleFile(),
                    featureFiles = featureZips.files,
                    mainDexList = mainDexList?.singleFile(),
                    obfuscationMappingFile = obsfuscationMappingFile?.singleFile(),
                    aaptOptionsNoCompress = aaptOptionsNoCompress,
                    bundleOptions = bundleOptions,
                    signature = signature,
                    bundleFile = bundleFile.get().asFile
                )
            )
        }
    }

    private data class Params(
        val baseModuleFile: File,
        val featureFiles: Set<File>,
        val mainDexList: File?,
        val obfuscationMappingFile: File?,
        val aaptOptionsNoCompress: Collection<String>,
        val bundleOptions: BundleOptions,
        val signature: JarSigner.Signature?,
        val bundleFile: File
    ) : Serializable

    private class BundleToolRunnable @Inject constructor(private val params: Params): Runnable {
        override fun run() {
            // BundleTool requires that the destination directory for the bundle file exists,
            // and that the bundle file itself does not
            val bundleFile = params.bundleFile
            FileUtils.cleanOutputDir(bundleFile.parentFile)

            val builder = ImmutableList.builder<Path>()
            builder.add(getBundlePath(params.baseModuleFile))
            params.featureFiles.forEach { builder.add(getBundlePath(it)) }

            val noCompressGlobsForBundle =
                PackagingUtils.getNoCompressGlobsForBundle(params.aaptOptionsNoCompress)

            val splitsConfig =  Config.SplitsConfig.newBuilder()
                .splitBy(Config.SplitDimension.Value.ABI, params.bundleOptions.enableAbi)
                .splitBy(Config.SplitDimension.Value.SCREEN_DENSITY, params.bundleOptions.enableDensity)
                .splitBy(Config.SplitDimension.Value.LANGUAGE, params.bundleOptions.enableLanguage)

            val bundleConfig =
                Config.BundleConfig.newBuilder()
                    .setCompression(
                        Config.Compression.newBuilder()
                            .addAllUncompressedGlob(noCompressGlobsForBundle))
                    .setOptimizations(
                        Config.Optimizations.newBuilder()
                            .setSplitsConfig(splitsConfig))

            val command = BuildBundleCommand.builder()
                .setBundleConfig(bundleConfig.build())
                .setOutputPath(bundleFile.toPath())
                .setModulesPaths(builder.build())

            params.mainDexList?.let {
                command.setMainDexListFile(it.toPath())
            }

            params.obfuscationMappingFile?.let {
                command.addMetadataFile(
                    "com.android.tools.build.obfuscation",
                    "proguard.map",
                    it.toPath()
                )
            }

            command.build().execute()

            if (params.signature != null) {
                JarSigner().sign(bundleFile, params.signature)
            }
        }

        private fun getBundlePath(folder: File): Path {
            val children = folder.listFiles()
            Preconditions.checkNotNull(children)
            Preconditions.checkState(children.size == 1)

            return children[0].toPath()
        }
    }

    data class BundleOptions (
        @get:Input
        @get:Optional
        val enableAbi: Boolean?,
        @get:Input
        @get:Optional
        val enableDensity: Boolean?,
        @get:Input
        @get:Optional
        val enableLanguage: Boolean?) : Serializable

    class CreationAction(private val scope: VariantScope) : TaskCreationAction<PackageBundleTask>() {
        override val name: String
            get() = scope.getTaskName("package", "Bundle")
        override val type: Class<PackageBundleTask>
            get() = PackageBundleTask::class.java

        private lateinit var bundleFile: Provider<RegularFile>

        override fun preConfigure(taskName: String) {
            super.preConfigure(taskName)

            val apkLocationOverride =
                scope.globalScope.projectOptions.get(StringOption.IDE_APK_LOCATION)

            val bundleName = "${scope.globalScope.projectBaseName}.aab"

            bundleFile = if (apkLocationOverride == null)
                scope.artifacts.createArtifactFile(
                    InternalArtifactType.BUNDLE,
                    BuildArtifactsHolder.OperationType.INITIAL,
                    taskName,
                    bundleName)
            else
                scope.artifacts.createArtifactFile(
                    InternalArtifactType.BUNDLE,
                    BuildArtifactsHolder.OperationType.INITIAL,
                    taskName,
                    FileUtils.join(
                        scope.globalScope.project.file(apkLocationOverride),
                        scope.variantConfiguration.dirName,
                        bundleName))

        }

        override fun configure(task: PackageBundleTask) {
            task.variantName = scope.fullVariantName

            task.bundleFile = bundleFile
            task.baseModuleZip = scope.artifacts.getFinalArtifactFiles(InternalArtifactType.MODULE_BUNDLE)

            task.featureZips = scope.getArtifactFileCollection(
                AndroidArtifacts.ConsumedConfigType.METADATA_VALUES,
                AndroidArtifacts.ArtifactScope.ALL,
                AndroidArtifacts.ArtifactType.MODULE_BUNDLE
            )

            task.aaptOptionsNoCompress =
                    scope.globalScope.extension.aaptOptions.noCompress ?: listOf()

            if (scope.type.isHybrid) {
                task.bundleOptions =
                        ((scope.globalScope.extension as FeatureExtension).bundle).convert()
            } else {
                task.bundleOptions =
                        ((scope.globalScope.extension as BaseAppModuleExtension).bundle).convert()
            }

            if (scope.needsMainDexListForBundle) {
                task.mainDexList =
                        scope.artifacts.getFinalArtifactFiles(
                            InternalArtifactType.MAIN_DEX_LIST_FOR_BUNDLE
                        )
                // The dex files from this application are still processed for legacy multidex
                // in this case, as if none of the dynamic features are fused the bundle tool will
                // not reprocess the dex files.
            }

            if (scope.artifacts.hasArtifact(InternalArtifactType.APK_MAPPING)) {
                task.obsfuscationMappingFile =
                        scope.artifacts.getFinalArtifactFiles(InternalArtifactType.APK_MAPPING)
            }

            scope.variantConfiguration.signingConfig?.let {
                task.keystoreFile = it.storeFile
                task.keystorePassword = it.storePassword
                task.keyAlias = it.keyAlias
                task.keyPassword = it.keyPassword
            }
        }
    }
}

private fun com.android.build.gradle.internal.dsl.BundleOptions.convert() =
    PackageBundleTask.BundleOptions(
        enableAbi = abi.enableSplit,
        enableDensity = density.enableSplit,
        enableLanguage = language.enableSplit
    )

/**
 * convenience function to call [Config.SplitsConfig.Builder.addSplitDimension]
 *
 * @param flag the [Config.SplitDimension.Value] on which to set the value
 * @param value if true, split is enbaled for the given flag. If null, no change is made and the
 *              bundle-tool will decide the value.
 */
private fun Config.SplitsConfig.Builder.splitBy(
    flag: Config.SplitDimension.Value,
    value: Boolean?
): Config.SplitsConfig.Builder {
    value?.let {
        addSplitDimension(Config.SplitDimension.newBuilder().setValue(flag).setNegate(!it))
    }
    return this
}
