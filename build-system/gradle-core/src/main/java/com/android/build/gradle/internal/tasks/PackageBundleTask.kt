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
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.options.BooleanOption
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
 * Task that generates the bundle (.aab) with all the modules.
 */
open class PackageBundleTask @Inject constructor(workerExecutor: WorkerExecutor) :
    AndroidVariantTask() {

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

    @get:Nested
    lateinit var bundleFlags: BundleFlags
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
                    bundleFlags = bundleFlags,
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
        val bundleFlags: BundleFlags,
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

            val uncompressNativeLibrariesConfig = Config.UncompressNativeLibraries.newBuilder()
                .setEnabled(params.bundleFlags.enableUncompressedNativeLibs)

            val bundleConfig =
                Config.BundleConfig.newBuilder()
                    .setCompression(
                        Config.Compression.newBuilder()
                            .addAllUncompressedGlob(noCompressGlobsForBundle))
                    .setOptimizations(
                        Config.Optimizations.newBuilder()
                            .setSplitsConfig(splitsConfig)
                            .setUncompressNativeLibraries(uncompressNativeLibrariesConfig))

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

    data class BundleFlags(
        @get:Input
        val enableUncompressedNativeLibs: Boolean
    ) : Serializable

    /**
     * CreateAction for a Task that will pack the bundle artifact.
     */
    class CreationAction(variantScope: VariantScope) :
        VariantTaskCreationAction<PackageBundleTask>(variantScope) {
        override val name: String
            get() = variantScope.getTaskName("package", "Bundle")

        override val type: Class<PackageBundleTask>
            get() = PackageBundleTask::class.java

        private lateinit var bundleFile: Provider<RegularFile>

        override fun preConfigure(taskName: String) {
            super.preConfigure(taskName)

            val bundleName = "${variantScope.globalScope.projectBaseName}.aab"

            bundleFile = variantScope.artifacts.createArtifactFile(
                InternalArtifactType.INTERMEDIARY_BUNDLE,
                BuildArtifactsHolder.OperationType.INITIAL,
                taskName,
                bundleName)
        }

        override fun configure(task: PackageBundleTask) {
            super.configure(task)

            task.bundleFile = bundleFile
            task.baseModuleZip = variantScope.artifacts.getFinalArtifactFiles(InternalArtifactType.MODULE_BUNDLE)

            task.featureZips = variantScope.getArtifactFileCollection(
                AndroidArtifacts.ConsumedConfigType.METADATA_VALUES,
                AndroidArtifacts.ArtifactScope.ALL,
                AndroidArtifacts.ArtifactType.MODULE_BUNDLE
            )

            task.aaptOptionsNoCompress =
                    variantScope.globalScope.extension.aaptOptions.noCompress ?: listOf()

            if (variantScope.type.isHybrid) {
                task.bundleOptions =
                        ((variantScope.globalScope.extension as FeatureExtension).bundle).convert()
            } else {
                task.bundleOptions =
                        ((variantScope.globalScope.extension as BaseAppModuleExtension).bundle).convert()
            }

            task.bundleFlags = BundleFlags(
                enableUncompressedNativeLibs = variantScope.globalScope.projectOptions[BooleanOption.ENABLE_UNCOMPRESSED_NATIVE_LIBS_IN_BUNDLE]
            )

            if (variantScope.needsMainDexListForBundle) {
                task.mainDexList =
                        variantScope.artifacts.getFinalArtifactFiles(
                            InternalArtifactType.MAIN_DEX_LIST_FOR_BUNDLE
                        )
                // The dex files from this application are still processed for legacy multidex
                // in this case, as if none of the dynamic features are fused the bundle tool will
                // not reprocess the dex files.
            }

            if (variantScope.artifacts.hasArtifact(InternalArtifactType.APK_MAPPING)) {
                task.obsfuscationMappingFile =
                        variantScope.artifacts.getFinalArtifactFiles(InternalArtifactType.APK_MAPPING)
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
