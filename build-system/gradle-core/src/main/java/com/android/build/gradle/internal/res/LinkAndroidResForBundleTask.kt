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

import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.dsl.convert
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.PROJECT
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.FEATURE_RESOURCE_PKG
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH
import com.android.build.gradle.internal.res.namespaced.registerAaptService
import com.android.build.gradle.internal.scope.ApkData
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.ExistingBuildElements
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.TaskInputHelper
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSetMetadata
import com.android.build.gradle.internal.utils.toImmutableList
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.StringOption
import com.android.build.gradle.options.SyncOptions
import com.android.builder.core.VariantTypeImpl
import com.android.builder.internal.aapt.AaptOptions
import com.android.builder.internal.aapt.AaptPackageConfig
import com.android.sdklib.AndroidVersion
import com.android.utils.FileUtils
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.io.IOException
import java.util.function.Supplier

/**
 * Task to link app resources into a proto format so that it can be consumed by the bundle tool.
 */
@CacheableTask
abstract class LinkAndroidResForBundleTask : NonIncrementalTask() {

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
    abstract val bundledResFile: RegularFileProperty

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

    @get:Input
    lateinit var aapt2Version: String
        private set

    @get:Internal
    abstract val aapt2FromMaven: ConfigurableFileCollection

    @get:Input
    var excludeResSources: Boolean = false
        private set

    private var compiledDependenciesResources: ArtifactCollection? = null

    override fun doTaskAction() {

        val manifestFile = ExistingBuildElements.from(InternalArtifactType.BUNDLE_MANIFEST, manifestFiles)
                .element(mainSplit)
                ?.outputFile
                ?: throw RuntimeException("Cannot find merged manifest file")

        val outputFile = bundledResFile.get().asFile
        FileUtils.mkdirs(outputFile.parentFile)

        val featurePackagesBuilder = ImmutableList.builder<File>()
        for (featurePackage in featureResourcePackages) {
            val buildElements =
                ExistingBuildElements.from(InternalArtifactType.PROCESSED_RES, featurePackage)
            if (buildElements.size() != 1) {
                throw IOException("Found more than one PROCESSED_RES output at $featurePackage")
            }

            featurePackagesBuilder.add(buildElements.iterator().next().outputFile)
        }

        val compiledDependenciesResourcesDirs =
            getCompiledDependenciesResources()?.reversed()?.toImmutableList() ?: emptyList<File>()

        val config = AaptPackageConfig(
            androidJarPath = androidJar.get().absolutePath,
            generateProtos = true,
            manifestFile = manifestFile,
            options = aaptOptions,
            resourceOutputApk = outputFile,
            variantType = VariantTypeImpl.BASE_APK,
            debuggable = debuggable,
            packageId = resOffset,
            allowReservedPackageId = minSdkVersion < AndroidVersion.VersionCodes.O,
            dependentFeatures = featurePackagesBuilder.build(),
            resourceDirs = ImmutableList.Builder<File>().addAll(compiledDependenciesResourcesDirs)
                .add(
                    checkNotNull(getInputResourcesDir().orNull?.asFile)
                ).build(),
            resourceConfigs = ImmutableSet.copyOf(resConfig),
            excludeSources = excludeResSources
        )
        if (logger.isInfoEnabled) {
            logger.info("Aapt output file {}", outputFile.absolutePath)
        }

        val aapt2ServiceKey = registerAaptService(
            aapt2FromMaven = aapt2FromMaven,
            logger = LoggerWrapper(logger)
        )
        getWorkerFacadeWithWorkers().use {
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
    abstract val manifestFiles: DirectoryProperty

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract fun getInputResourcesDir(): DirectoryProperty

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
    fun getCompiledDependenciesResources(): FileCollection? {
        return compiledDependenciesResources?.artifactFiles
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

        override fun handleProvider(taskProvider: TaskProvider<out LinkAndroidResForBundleTask>) {
            super.handleProvider(taskProvider)
            variantScope.artifacts.producesFile(
                InternalArtifactType.LINKED_RES_FOR_BUNDLE,
                BuildArtifactsHolder.OperationType.INITIAL,
                taskProvider,
                LinkAndroidResForBundleTask::bundledResFile,
                "bundled-res.ap_")

        }

        override fun configure(task: LinkAndroidResForBundleTask) {
            super.configure(task)

            val variantData = variantScope.variantData

            val projectOptions = variantScope.globalScope.projectOptions

            val config = variantData.variantConfiguration

            task.incrementalFolder = variantScope.getIncrementalDir(name)

            task.versionCode = TaskInputHelper.memoizeToProvider(task.project) {
                config.versionCode
            }
            task.versionName = TaskInputHelper.memoizeToProvider(task.project) {
                config.versionName
            }

            task.mainSplit = variantData.outputScope.mainSplit

            variantScope.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.BUNDLE_MANIFEST,
                task.manifestFiles)

            variantScope.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.MERGED_RES,
                task.getInputResourcesDir()
            )

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

            // We only want to exclude res sources for release builds and when the flag is turned on
            // This will result in smaller release bundles
            task.excludeResSources = !task.debuggable
                    && projectOptions.get(BooleanOption.EXCLUDE_RES_SOURCES_FOR_RELEASE_BUNDLES)

            task.buildTargetDensity =
                    projectOptions.get(StringOption.IDE_BUILD_TARGET_DENSITY)

            task.mergeBlameLogFolder = variantScope.resourceBlameLogDir
            val (aapt2FromMaven, aapt2Version) = getAapt2FromMavenAndVersion(variantScope.globalScope)
            task.aapt2FromMaven.from(aapt2FromMaven)
            task.aapt2Version = aapt2Version
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

            if (variantScope.isPrecompileDependenciesResourcesEnabled) {
                task.compiledDependenciesResources = variantScope.getArtifactCollection(
                    AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.ALL,
                    AndroidArtifacts.ArtifactType.COMPILED_DEPENDENCIES_RESOURCES
                )
            }
        }
    }
}
