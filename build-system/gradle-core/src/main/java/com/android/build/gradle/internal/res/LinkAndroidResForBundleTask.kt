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

package com.android.build.gradle.internal.res

import com.android.build.api.artifact.BuildableArtifact
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.dsl.convert
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.PROJECT
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.FEATURE_RESOURCE_PKG
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH
import com.android.build.gradle.internal.res.namespaced.registerAaptService
import com.android.build.gradle.internal.scope.ApkData
import com.android.build.gradle.internal.scope.ExistingBuildElements
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.TaskInputHelper
import com.android.build.gradle.internal.tasks.Workers
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSetMetadata
import com.android.build.gradle.options.StringOption
import com.android.build.gradle.internal.utils.toImmutableList
import com.android.build.gradle.options.BooleanOption

import com.android.build.gradle.options.SyncOptions
import com.android.builder.core.VariantTypeImpl
import com.android.builder.internal.aapt.AaptOptions
import com.android.builder.internal.aapt.AaptPackageConfig
import com.android.sdklib.AndroidVersion
import com.android.utils.FileUtils
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.io.IOException
import java.util.function.Supplier
import javax.inject.Inject

/**
 * Task to link app resources into a proto format so that it can be consumed by the bundle tool.
 */
@CacheableTask
open class LinkAndroidResForBundleTask
@Inject constructor(workerExecutor: WorkerExecutor) : NonIncrementalTask() {

    private val workers = Workers.preferWorkers(project.name, path, workerExecutor)

    @get:Input
    var debuggable: Boolean = false
        private set

    private lateinit var aaptOptions: AaptOptions

    private lateinit var errorFormatMode: SyncOptions.ErrorFormatMode

    private var mergeBlameLogFolder: File? = null

    // Not an input as it is only used to rewrite exception and doesn't affect task output
    private lateinit var manifestMergeBlameFile: Provider<RegularFile>

    private var buildTargetDensity: String? = null

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    lateinit var androidJar: Provider<File>
        private set

    @get:OutputFile
    lateinit var bundledResFile: File
        private set

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var featureResourcePackages: FileCollection
        private set

    private var resOffsetSupplier: Supplier<Int>? = null

    @Suppress("MemberVisibilityCanBePrivate")
    @get:Input
    @get:Optional
    val resOffset: Int?
        get() = resOffsetSupplier?.get()

    @get:Input
    @get:Optional
    lateinit var versionName: Provider<String?> private set

    @get:Input
    lateinit var versionCode: Provider<Int?> private set

    @get:OutputDirectory
    lateinit var incrementalFolder: File
        private set

    @get:Input
    lateinit var mainSplit: ApkData
        private set

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var aapt2FromMaven: FileCollection
        private set

    private var compiledRemoteResources: ArtifactCollection? = null

    override fun doTaskAction() {

        val manifestFile = ExistingBuildElements.from(InternalArtifactType.BUNDLE_MANIFEST, manifestFiles)
                .element(mainSplit)
                ?.outputFile
                ?: throw RuntimeException("Cannot find merged manifest file")

        FileUtils.mkdirs(bundledResFile.parentFile)

        val featurePackagesBuilder = ImmutableList.builder<File>()
        for (featurePackage in featureResourcePackages) {
            val buildElements =
                ExistingBuildElements.from(InternalArtifactType.PROCESSED_RES, featurePackage)
            if (buildElements.size() != 1) {
                throw IOException("Found more than one PROCESSED_RES output at $featurePackage")
            }

            featurePackagesBuilder.add(buildElements.iterator().next().outputFile)
        }

        val compiledRemoteResourcesDirs =
            if (getCompiledRemoteResources() == null) emptyList<File>()
            else {
                // the order of the artifact is descending order, so we need to reverse it.
                getCompiledRemoteResources()!!.reversed().toImmutableList()
            }

        val config = AaptPackageConfig(
            androidJarPath = androidJar.get().absolutePath,
            generateProtos = true,
            manifestFile = manifestFile,
            options = aaptOptions,
            resourceOutputApk = bundledResFile,
            variantType = VariantTypeImpl.BASE_APK,
            debuggable = debuggable,
            packageId = resOffset,
            allowReservedPackageId = minSdkVersion < AndroidVersion.VersionCodes.O,
            dependentFeatures = featurePackagesBuilder.build(),
            resourceDirs = ImmutableList.Builder<File>().addAll(compiledRemoteResourcesDirs).add(
                checkNotNull(getInputResourcesDir()).single()
            ).build(),
            resourceConfigs = ImmutableSet.copyOf(resConfig)
        )
        if (logger.isInfoEnabled) {
            logger.info("Aapt output file {}", bundledResFile.absolutePath)
        }

        val aapt2ServiceKey = registerAaptService(
            aapt2FromMaven = aapt2FromMaven,
            logger = LoggerWrapper(logger)
        )
        workers.use {
            it.submit(
                Aapt2ProcessResourcesRunnable::class.java,
                Aapt2ProcessResourcesRunnable.Params(
                    aapt2ServiceKey,
                    config,
                    errorFormatMode,
                    mergeBlameLogFolder,
                    manifestMergeBlameFile.orNull?.asFile
                )
            )
        }
    }

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var manifestFiles: BuildableArtifact
        private set

    private var inputResourcesDir: BuildableArtifact? = null

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    fun getInputResourcesDir(): BuildableArtifact? {
        return inputResourcesDir
    }

    @get:Input
    lateinit var resConfig: Collection<String> private set

    @Nested
    fun getAaptOptionsInput(): LinkingTaskInputAaptOptions {
        return LinkingTaskInputAaptOptions(aaptOptions)
    }

    /**
     * Returns a file collection of the directories containing the compiled remote libraries
     * resource files.
     */
    @Optional
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    fun getCompiledRemoteResources(): FileCollection? {
        return compiledRemoteResources?.artifactFiles
    }

    @get:Input
    var minSdkVersion: Int = 1
        private set

    class CreationAction(variantScope: VariantScope) :
        VariantTaskCreationAction<LinkAndroidResForBundleTask>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("bundle", "Resources")
        override val type: Class<LinkAndroidResForBundleTask>
            get() = LinkAndroidResForBundleTask::class.java

        private lateinit var bundledResFile: File

        override fun preConfigure(taskName: String) {
            super.preConfigure(taskName)
            bundledResFile = variantScope.artifacts
                .appendArtifact(InternalArtifactType.LINKED_RES_FOR_BUNDLE,
                    taskName,
                    "bundled-res.ap_")
        }

        override fun configure(task: LinkAndroidResForBundleTask) {
            super.configure(task)

            val variantData = variantScope.variantData

            val projectOptions = variantScope.globalScope.projectOptions

            val config = variantData.variantConfiguration

            task.bundledResFile = bundledResFile

            task.incrementalFolder = variantScope.getIncrementalDir(name)

            task.versionCode = TaskInputHelper.memoizeToProvider(task.project) {
                config.versionCode
            }
            task.versionName = TaskInputHelper.memoizeToProvider(task.project) {
                config.versionName
            }

            task.mainSplit = variantData.outputScope.mainSplit

            task.manifestFiles = variantScope.artifacts.getFinalArtifactFiles(
                InternalArtifactType.BUNDLE_MANIFEST)

            task.inputResourcesDir =
                    variantScope.artifacts.getFinalArtifactFiles(InternalArtifactType.MERGED_RES)

            task.featureResourcePackages = variantScope.getArtifactFileCollection(
                COMPILE_CLASSPATH, PROJECT, FEATURE_RESOURCE_PKG)

            if (variantScope.type.isFeatureSplit) {
                // get the res offset supplier
                task.resOffsetSupplier =
                        FeatureSetMetadata.getInstance().getResOffsetSupplierForTask(
                            variantScope, task)
            }

            task.debuggable = config.buildType.isDebuggable
            task.aaptOptions = variantScope.globalScope.extension.aaptOptions.convert()

            task.buildTargetDensity =
                    projectOptions.get(StringOption.IDE_BUILD_TARGET_DENSITY)

            task.mergeBlameLogFolder = variantScope.resourceBlameLogDir
            task.aapt2FromMaven = getAapt2FromMaven(variantScope.globalScope)
            task.minSdkVersion = variantScope.minSdkVersion.apiLevel

            task.resConfig =
                    variantScope.variantConfiguration.mergedFlavor.resourceConfigurations

            task.androidJar = variantScope.globalScope.sdkComponents.androidJarProvider

            task.errorFormatMode = SyncOptions.getErrorFormatMode(
                variantScope.globalScope.projectOptions
            )

            task.manifestMergeBlameFile = variantScope.artifacts.getFinalProduct(
                InternalArtifactType.MANIFEST_MERGE_BLAME_FILE
            )

            if (variantScope.globalScope.projectOptions.
                    get(BooleanOption.PRECOMPILE_REMOTE_RESOURCES)) {
                task.compiledRemoteResources =
                    variantScope.getArtifactCollection(
                        AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                        AndroidArtifacts.ArtifactScope.ALL,
                        AndroidArtifacts.ArtifactType.COMPILED_REMOTE_RESOURCES
                    )
            }
        }
    }
}
