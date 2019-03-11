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

package com.android.build.gradle.internal.res

import com.android.SdkConstants
import com.android.SdkConstants.FN_RES_BASE
import com.android.SdkConstants.RES_QUALIFIER_SEP
import com.android.build.VariantOutput
import com.android.build.api.artifact.BuildableArtifact
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.internal.api.artifact.singleFile
import com.android.build.gradle.internal.dsl.AaptOptions
import com.android.build.gradle.internal.dsl.convert
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.MODULE
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.FEATURE_RESOURCE_PKG
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH
import com.android.build.gradle.internal.res.namespaced.Aapt2ServiceKey
import com.android.build.gradle.internal.res.namespaced.getAaptDaemon
import com.android.build.gradle.internal.res.namespaced.registerAaptService
import com.android.build.gradle.internal.scope.ApkData
import com.android.build.gradle.internal.scope.BuildElements
import com.android.build.gradle.internal.scope.BuildOutput
import com.android.build.gradle.internal.scope.ExistingBuildElements
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.SplitList
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.TaskInputHelper
import com.android.build.gradle.internal.tasks.Workers
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSetMetadata
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.MultiOutputPolicy
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.StringOption
import com.android.build.gradle.tasks.ProcessAndroidResources
import com.android.builder.core.AndroidBuilder
import com.android.builder.core.VariantType
import com.android.builder.core.VariantTypeImpl
import com.android.builder.internal.aapt.AaptPackageConfig
import com.android.builder.internal.aapt.v2.Aapt2Exception
import com.android.ide.common.process.ProcessException
import com.android.ide.common.symbols.SymbolIo
import com.android.ide.common.workers.WorkerExecutorFacade
import com.android.utils.FileUtils
import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.tooling.BuildException
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.io.IOException
import java.io.Serializable
import java.nio.file.Files
import java.nio.file.Path
import java.util.ArrayList
import java.util.function.Supplier
import java.util.regex.Pattern
import javax.inject.Inject

@CacheableTask
open class LinkApplicationAndroidResourcesTask @Inject constructor(workerExecutor: WorkerExecutor) :
    ProcessAndroidResources() {

    companion object {
        private val LOG = Logging.getLogger(LinkApplicationAndroidResourcesTask::class.java)

        private fun getOutputBaseNameFile(apkData: ApkData, resPackageOutputFolder: File): File {
            return File(
                resPackageOutputFolder,
                FN_RES_BASE + RES_QUALIFIER_SEP + apkData.fullName + SdkConstants.DOT_RES
            )
        }
    }

    private var sourceOutputDir: File? = null

    private var textSymbolOutputDir: Supplier<File?> = Supplier { null }

    @get:org.gradle.api.tasks.OutputFile
    @get:Optional
    var symbolsWithPackageNameOutputFile: File? = null
        private set

    @get:org.gradle.api.tasks.OutputFile
    @get:Optional
    var proguardOutputFile: File? = null

    @get:org.gradle.api.tasks.OutputFile
    @get:Optional
    var mainDexListProguardOutputFile: File? = null
        private set

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    var dependenciesFileCollection: FileCollection? = null
        private set

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    var sharedLibraryDependencies: FileCollection? = null
        private set

    private var resOffsetSupplier: (Supplier<Int>)? = null

    @get:Input
    lateinit var multiOutputPolicy: MultiOutputPolicy
        private set

    private lateinit var type: VariantType

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var aapt2FromMaven: FileCollection
        private set

    private var debuggable: Boolean = false

    @get:Nested
    lateinit var aaptOptions: AaptOptions

    private lateinit var mergeBlameLogFolder: File

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    var featureResourcePackages: FileCollection? = null
        private set

    private lateinit var originalApplicationId: Supplier<String?>

    @get:Input
    @get:Optional
    var buildTargetDensity: String? = null
        private set

    @get:Input
    var useConditionalKeepRules: Boolean = false
        private set

    @get:OutputDirectory
    lateinit var resPackageOutputFolder: File
        private set

    @get:Input
    lateinit var projectBaseName: String
        private set

    @get:Input
    lateinit var taskInputType: InternalArtifactType
        private set

    @get:Input
    var isNamespaced = false
        private set

    @get:Nested
    @get:Optional
    lateinit var splitList: SplitList
        private set

    private lateinit var applicationId: Supplier<String?>

    private lateinit var supportDirectory: File

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var apkList: BuildableArtifact
        private set

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    @get:Optional
    var convertedLibraryDependencies: BuildableArtifact? = null
        private set

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    var inputResourcesDir: BuildableArtifact? = null
        private set

    private lateinit var variantScope: VariantScope

    @get:Input
    var isLibrary: Boolean = false
        private set

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    lateinit var androidJar: Provider<File>
        private set

    @get:Input
    var useFinalIds: Boolean = true
        private set

    private val workers: WorkerExecutorFacade = Workers.getWorker(project.name, path, workerExecutor)

    // FIXME : make me incremental !
    override fun doFullTaskAction() {
        FileUtils.deleteDirectoryContents(resPackageOutputFolder)

        val manifestBuildElements = ExistingBuildElements.from(taskInputType, manifestFiles)

        val featureResourcePackages = if (featureResourcePackages != null)
            featureResourcePackages!!.files
        else
            ImmutableSet.of()

        val dependencies = if (dependenciesFileCollection != null)
            dependenciesFileCollection!!.files
        else
            emptySet()
        val imports = if (sharedLibraryDependencies != null)
            sharedLibraryDependencies!!.files
        else
            emptySet()
        val aapt2ServiceKey = registerAaptService(
            aapt2FromMaven, iLogger
        )

        workers.use {
            val unprocessedManifest = manifestBuildElements.toMutableList()
            val mainOutput = chooseOutput(manifestBuildElements)

            unprocessedManifest.remove(mainOutput)

            it.submit(
                AaptSplitInvoker::class.java,
                AaptSplitInvokerParams(
                    mainOutput,
                    dependencies,
                    imports,
                    splitList,
                    featureResourcePackages,
                    mainOutput.apkData,
                    true,
                    aapt2ServiceKey,
                    this
                )
            )

            if (variantScope.type.canHaveSplits) {
                // If there are remaining splits to be processed we await for the main split to
                // finish since the output of the main split is used by the full splits bellow.
                it.await()

                for (manifestBuildOutput in unprocessedManifest) {
                    val apkInfo = manifestBuildOutput.apkData
                    if (apkInfo.requiresAapt()) {
                        it.submit(
                            AaptSplitInvoker::class.java,
                            AaptSplitInvokerParams(
                                manifestBuildOutput,
                                dependencies,
                                imports,
                                splitList,
                                featureResourcePackages,
                                apkInfo,
                                false,
                                aapt2ServiceKey,
                                this
                            )
                        )
                    }
                }
            }
        }

        if (multiOutputPolicy === MultiOutputPolicy.SPLITS) {
            // The output of the worker runnables submitted before is used in this code block, so
            // we have to make sure that all work is finished.
            workers.await()

            for (manifestBuildOutput in manifestBuildElements.toList()) {
                val apkInfo = manifestBuildOutput.apkData
                if (apkInfo.filters
                        .stream()
                        .anyMatch { f -> f.filterType == VariantOutput.FilterType.ABI.name }
                ) {
                    // NOTE: This if exists because ABI splits are produced by
                    // GenerateSplitAbiRes, so for ABI splits we're not supposed to find them
                    // here anyway.
                    continue
                }

                // In case we generated pure splits, we may have more than one
                // resource AP_ in the output directory. reconcile with the
                // splits list and save it for downstream tasks.
                val packagedResForSplit = findPackagedResForSplit(resPackageOutputFolder, apkInfo)

                if (packagedResForSplit != null) {
                    AaptSplitInvoker.appendOutput(
                        BuildOutput(
                            InternalArtifactType.DENSITY_OR_LANGUAGE_SPLIT_PROCESSED_RES,
                            apkInfo,
                            packagedResForSplit
                        ),
                        resPackageOutputFolder
                    )
                } else {
                    logger.warn("Cannot find output for $apkInfo")
                }
            }
        }
    }

    private fun chooseOutput(manifestBuildElements: BuildElements): BuildOutput {
        when (multiOutputPolicy) {
            MultiOutputPolicy.SPLITS -> {
                val main = manifestBuildElements
                    .stream()
                    .filter { output -> output.apkData.type == VariantOutput.OutputType.MAIN }
                    .findFirst()
                if (!main.isPresent) {
                    throw RuntimeException("No main apk found")
                }
                return main.get()
            }
            MultiOutputPolicy.MULTI_APK -> {
                val nonDensity = manifestBuildElements
                    .stream()
                    .filter { output ->
                        output.apkData
                            .getFilter(
                                VariantOutput.FilterType
                                    .DENSITY
                            ) == null
                    }
                    .findFirst()
                if (!nonDensity.isPresent) {
                    throw RuntimeException("No non-density apk found")
                }
                return nonDensity.get()
            }
            else -> throw RuntimeException(
                "Unexpected MultiOutputPolicy type: $multiOutputPolicy"
            )
        }
    }

    abstract class BaseCreationAction(
        scope: VariantScope,
        private val generateLegacyMultidexMainDexProguardRules: Boolean,
        private val baseName: String?,
        private val isLibrary: Boolean
    ) : VariantTaskCreationAction<LinkApplicationAndroidResourcesTask>(scope) {
        private lateinit var resPackageOutputFolder: File
        private lateinit var proguardOutputFile: File
        private lateinit var aaptMainDexListProguardOutputFile: File

        override val name: String
            get() = variantScope.getTaskName("process", "Resources")

        override val type: Class<LinkApplicationAndroidResourcesTask>
            get() = LinkApplicationAndroidResourcesTask::class.java

        protected open fun preconditionsCheck(variantData: BaseVariantData) {}

        override fun preConfigure(taskName: String) {
            super.preConfigure(taskName)
            val variantScope = variantScope

            resPackageOutputFolder = variantScope
                .artifacts
                .appendArtifact(InternalArtifactType.PROCESSED_RES, taskName, "out")

            if (ProcessAndroidResources.generatesProguardOutputFile(variantScope)) {
                proguardOutputFile = variantScope.processAndroidResourcesProguardOutputFile
                variantScope
                    .artifacts
                    .appendArtifact(
                        InternalArtifactType.AAPT_PROGUARD_FILE,
                        ImmutableList.of(proguardOutputFile),
                        taskName
                    )
            }

            if (generateLegacyMultidexMainDexProguardRules) {
                aaptMainDexListProguardOutputFile = variantScope
                    .artifacts
                    .appendArtifact(
                        InternalArtifactType
                            .LEGACY_MULTIDEX_AAPT_DERIVED_PROGUARD_RULES,
                        taskName,
                        "manifest_keep.txt"
                    )
            }
        }

        override fun handleProvider(
            taskProvider: TaskProvider<out LinkApplicationAndroidResourcesTask>
        ) {
            super.handleProvider(taskProvider)
            variantScope.taskContainer.processAndroidResTask = taskProvider
        }

        override fun configure(task: LinkApplicationAndroidResourcesTask) {
            super.configure(task)
            val variantScope = variantScope
            val variantData = variantScope.variantData
            val projectOptions = variantScope.globalScope.projectOptions
            val config = variantData.variantConfiguration

            preconditionsCheck(variantData)

            task.resPackageOutputFolder = resPackageOutputFolder
            task.aapt2FromMaven = getAapt2FromMaven(variantScope.globalScope)

            task.applicationId = TaskInputHelper.memoize { config.applicationId }

            task.incrementalFolder = variantScope.getIncrementalDir(name)
            if (variantData.type.canHaveSplits) {
                val splits = variantScope.globalScope.extension.splits

                val densitySet = if (splits.density.isEnable)
                    ImmutableSet.copyOf(splits.densityFilters)
                else
                    ImmutableSet.of()
                val languageSet = if (splits.language.isEnable)
                    ImmutableSet.copyOf(splits.languageFilters)
                else
                    ImmutableSet.of()
                val abiSet = if (splits.abi.isEnable)
                    ImmutableSet.copyOf(splits.abiFilters)
                else
                    ImmutableSet.of()
                val resConfigSet = ImmutableSet.copyOf(
                    variantScope
                        .variantConfiguration
                        .mergedFlavor
                        .resourceConfigurations
                )

                task.splitList = SplitList(densitySet, languageSet, abiSet, resConfigSet)
            } else {
                task.splitList = SplitList(
                    ImmutableSet.of(),
                    ImmutableSet.of(),
                    ImmutableSet.of(),
                    ImmutableSet.of()
                )
            }

            task.multiOutputPolicy = variantData.multiOutputPolicy
            task.apkList = variantScope
                .artifacts
                .getFinalArtifactFiles(InternalArtifactType.APK_LIST)

            if (ProcessAndroidResources.generatesProguardOutputFile(variantScope)) {
                task.proguardOutputFile = proguardOutputFile
            }

            if (generateLegacyMultidexMainDexProguardRules) {
                task.setAaptMainDexListProguardOutputFile(aaptMainDexListProguardOutputFile)
            }

            task.variantScope = variantScope
            task.outputScope = variantData.outputScope
            task.originalApplicationId = TaskInputHelper.memoize { config.originalApplicationId }

            val aaptFriendlyManifestsFilePresent = variantScope
                .artifacts
                .hasFinalProduct(InternalArtifactType.AAPT_FRIENDLY_MERGED_MANIFESTS)
            task.taskInputType = if (aaptFriendlyManifestsFilePresent)
                InternalArtifactType.AAPT_FRIENDLY_MERGED_MANIFESTS
            else
                variantScope.manifestArtifactType
            task.setManifestFiles(
                variantScope.artifacts.getFinalProduct(task.taskInputType)
            )

            task.setType(config.type)
            task.setDebuggable(config.buildType.isDebuggable)
            task.aaptOptions = variantScope.globalScope.extension.aaptOptions

            task.buildTargetDensity = projectOptions.get(StringOption.IDE_BUILD_TARGET_DENSITY)

            task.useConditionalKeepRules = projectOptions.get(BooleanOption.CONDITIONAL_KEEP_RULES)

            task.setMergeBlameLogFolder(variantScope.resourceBlameLogDir)

            val variantType = variantScope.type

            // Tests should not have feature dependencies, however because they include the
            // tested production component in their dependency graph, we see the tested feature
            // package in their graph. Therefore we have to manually not set this up for tests.
            task.featureResourcePackages = if (variantType.isForTesting)
                null
            else
                variantScope.getArtifactFileCollection(
                    COMPILE_CLASSPATH, MODULE, FEATURE_RESOURCE_PKG
                )

            if (variantType.isFeatureSplit) {
                task.resOffsetSupplier = FeatureSetMetadata.getInstance()
                    .getResOffsetSupplierForTask(variantScope, task)
            }

            task.projectBaseName = baseName!!
            task.isLibrary = isLibrary
            task.supportDirectory = File(variantScope.splitApkOutputFolder, "resources")

            task.androidJar = variantScope.globalScope.sdkComponents.androidJarProvider

            if (variantScope.type.isForTesting) {
                task.useFinalIds = !projectOptions.get(BooleanOption.USE_NON_FINAL_RES_IDS_IN_TESTS)
            }
        }
    }

    class CreationAction(
        scope: VariantScope,
        private val symbolLocation: Supplier<File>,
        private val symbolsWithPackageNameOutputFile: File,
        generateLegacyMultidexMainDexProguardRules: Boolean,
        private val sourceArtifactType: TaskManager.MergeType,
        baseName: String,
        isLibrary: Boolean
    ) : BaseCreationAction(scope, generateLegacyMultidexMainDexProguardRules, baseName, isLibrary) {
        private var sourceOutputDir: File? = null

        override fun preconditionsCheck(variantData: BaseVariantData) {
            if (variantData.type.isAar) {
                throw IllegalArgumentException("Use GenerateLibraryRFileTask")
            } else {
                Preconditions.checkState(
                    sourceArtifactType === TaskManager.MergeType.MERGE,
                    "source output type should be MERGE",
                    sourceArtifactType
                )
            }
        }

        override fun preConfigure(taskName: String) {
            super.preConfigure(taskName)
            sourceOutputDir = variantScope
                .artifacts
                .appendArtifact(
                    InternalArtifactType.NOT_NAMESPACED_R_CLASS_SOURCES,
                    taskName,
                    SdkConstants.FD_RES_CLASS
                )
        }

        override fun configure(task: LinkApplicationAndroidResourcesTask) {
            super.configure(task)

            // TODO: unify with generateBuilderConfig, compileAidl, and library packaging somehow?
            task.setSourceOutputDir(sourceOutputDir)

            task.dependenciesFileCollection = variantScope
                .getArtifactFileCollection(
                    RUNTIME_CLASSPATH,
                    ALL,
                    AndroidArtifacts.ArtifactType.SYMBOL_LIST_WITH_PACKAGE_NAME
                )

            task.inputResourcesDir = variantScope
                .artifacts
                .getFinalArtifactFiles(sourceArtifactType.outputType)

            @Suppress("UNCHECKED_CAST")
            task.textSymbolOutputDir = symbolLocation as Supplier<File?>
            task.symbolsWithPackageNameOutputFile = symbolsWithPackageNameOutputFile
        }
    }

    /**
     * TODO: extract in to a separate task implementation once splits are calculated in the split
     * discovery task.
     */
    class NamespacedCreationAction(
        scope: VariantScope,
        generateLegacyMultidexMainDexProguardRules: Boolean,
        baseName: String?
    ) : BaseCreationAction(scope, generateLegacyMultidexMainDexProguardRules, baseName, false) {
        private var sourceOutputDir: File? = null

        override fun preConfigure(taskName: String) {
            super.preConfigure(taskName)

            sourceOutputDir = variantScope.artifacts
                .appendArtifact(
                    InternalArtifactType.RUNTIME_R_CLASS_SOURCES, taskName, "out"
                )
        }

        override fun configure(task: LinkApplicationAndroidResourcesTask) {
            super.configure(task)

            val projectOptions = variantScope.globalScope.projectOptions

            task.sourceOutputDir = sourceOutputDir

            val dependencies = ArrayList<FileCollection>(2)
            dependencies.add(
                variantScope.artifacts.getFinalArtifactFiles(InternalArtifactType.RES_STATIC_LIBRARY).get()
            )
            dependencies.add(
                variantScope.getArtifactFileCollection(
                    RUNTIME_CLASSPATH,
                    ALL,
                    AndroidArtifacts.ArtifactType.RES_STATIC_LIBRARY
                )
            )
            if (variantScope.globalScope.extension.aaptOptions.namespaced && projectOptions.get(
                    BooleanOption.CONVERT_NON_NAMESPACED_DEPENDENCIES
                )
            ) {
                task.convertedLibraryDependencies = variantScope
                    .artifacts
                    .getArtifactFiles(
                        InternalArtifactType
                            .RES_CONVERTED_NON_NAMESPACED_REMOTE_DEPENDENCIES
                    )
            }

            task.dependenciesFileCollection =
                variantScope.globalScope.project.files(dependencies)

            task.sharedLibraryDependencies = variantScope.getArtifactFileCollection(
                AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                AndroidArtifacts.ArtifactScope.ALL,
                AndroidArtifacts.ArtifactType.RES_SHARED_STATIC_LIBRARY
            )

            task.isNamespaced = true
        }
    }

    private class AaptSplitInvoker @Inject
    internal constructor(private val params: AaptSplitInvokerParams) : Runnable {

        companion object {
            @Synchronized
            @Throws(IOException::class)
            fun appendOutput(
                output: BuildOutput, resPackageOutputFolder: File
            ) {
                val buildOutputs = ArrayList(
                    ExistingBuildElements.from(resPackageOutputFolder).elements
                )
                buildOutputs.add(output)
                BuildElements(buildOutputs).save(resPackageOutputFolder)
            }
        }

        override fun run() {
            try {
                invokeAaptForSplit(params)
            } catch (e: IOException) {
                throw RuntimeException(e)
            }

        }

        @Throws(IOException::class)
        private fun invokeAaptForSplit(params: AaptSplitInvokerParams) {

            val featurePackagesBuilder = ImmutableList.builder<File>()
            for (featurePackage in params.featureResourcePackages) {
                val buildElements = ExistingBuildElements.from(
                    InternalArtifactType.PROCESSED_RES, featurePackage
                )
                if (!buildElements.isEmpty()) {
                    val mainBuildOutput = buildElements.elementByType(VariantOutput.OutputType.MAIN)
                    if (mainBuildOutput != null) {
                        featurePackagesBuilder.add(mainBuildOutput.outputFile)
                    } else {
                        throw IOException(
                            "Cannot find PROCESSED_RES output for " + params.variantScopeMainSplit
                        )
                    }
                }
            }

            val resOutBaseNameFile =
                getOutputBaseNameFile(params.apkData, params.resPackageOutputFolder)
            val manifestFile = params.manifestOutput.outputFile

            var packageForR: String? = null
            var srcOut: File? = null
            var symbolOutputDir: File? = null
            var proguardOutputFile: File? = null
            var mainDexListProguardOutputFile: File? = null
            if (params.generateCode) {
                // workaround for b/74068247. Until that's fixed, if it's a namespaced feature,
                // an extra empty dummy R.java file will be generated as well
                packageForR =
                    if (params.isNamespaced && params.variantDataType === VariantTypeImpl.FEATURE) {
                        "dummy"
                    } else {
                        params.originalApplicationId
                    }

                // we have to clean the source folder output in case the package name changed.
                srcOut = params.sourceOutputDir
                if (srcOut != null) {
                    FileUtils.cleanOutputDir(srcOut)
                }

                symbolOutputDir = params.textSymbolOutputDir
                proguardOutputFile = params.proguardOutputFile
                mainDexListProguardOutputFile = params.mainDexListProguardOutputFile
            }

            val densityFilterData = params.apkData.getFilter(VariantOutput.FilterType.DENSITY)
            // if resConfigs is set, we should not use our preferredDensity.
            val preferredDensity =
                densityFilterData?.identifier
                    ?: if (params.resourceConfigs.isEmpty()) params.buildTargetDensity else null


            try {

                // If the new resources flag is enabled and if we are dealing with a library process
                // resources through the new parsers
                run {
                    val configBuilder = AaptPackageConfig.Builder()
                        .setManifestFile(manifestFile)
                        .setOptions(params.aaptOptions)
                        .setCustomPackageForR(packageForR)
                        .setSymbolOutputDir(symbolOutputDir)
                        .setSourceOutputDir(srcOut)
                        .setResourceOutputApk(resOutBaseNameFile)
                        .setProguardOutputFile(proguardOutputFile)
                        .setMainDexListProguardOutputFile(mainDexListProguardOutputFile)
                        .setVariantType(params.variantType)
                        .setDebuggable(params.debuggable)
                        .setResourceConfigs(params.resourceConfigs)
                        .setSplits(params.multiOutputPolicySplitList)
                        .setPreferredDensity(preferredDensity)
                        .setPackageId(params.packageId)
                        .setAllowReservedPackageId(
                            params.packageId != null && params.packageId < FeatureSetMetadata.BASE_ID
                        )
                        .setDependentFeatures(featurePackagesBuilder.build())
                        .setImports(params.imports)
                        .setIntermediateDir(params.incrementalFolder)
                        .setAndroidJarPath(params.androidJarPath)
                        .setUseConditionalKeepRules(params.useConditionalKeepRules)
                        .setUseFinalIds(params.useFinalIds)

                    if (params.isNamespaced) {
                        val packagedDependencies = ImmutableList.builder<File>()
                        packagedDependencies.addAll(params.dependencies)
                        if (params.convertedLibraryDependenciesPath != null) {
                            Files.list(params.convertedLibraryDependenciesPath).map { it.toFile() }
                                .forEach { packagedDependencies.add(it) }
                        }
                        configBuilder.setStaticLibraryDependencies(packagedDependencies.build())
                    } else {
                        if (params.generateCode) {
                            configBuilder.setLibrarySymbolTableFiles(params.dependencies)
                        }
                        configBuilder.setResourceDir(checkNotNull(params.inputResourcesDir))
                    }

                    @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
                    Preconditions.checkNotNull<Aapt2ServiceKey>(
                        params.aapt2ServiceKey, "AAPT2 daemon manager service not initialized"
                    )
                    try {
                        getAaptDaemon(params.aapt2ServiceKey!!).use { aaptDaemon ->

                            AndroidBuilder.processResources(
                                aaptDaemon,
                                configBuilder.build(),
                                LoggerWrapper(
                                    Logging.getLogger(
                                        LinkApplicationAndroidResourcesTask::class.java
                                    )
                                )
                            )
                        }
                    } catch (e: Aapt2Exception) {
                        throw rewriteLinkException(e, params.mergeBlameFolder)
                    }

                    if (LOG.isInfoEnabled) {
                        LOG.info("Aapt output file {}", resOutBaseNameFile.absolutePath)
                    }
                }
                if (params.generateCode
                    && (params.isLibrary || !params.dependencies.isEmpty())
                    && params.symbolsWithPackageNameOutputFile != null
                ) {
                    SymbolIo.writeSymbolListWithPackageName(
                        File(
                            params.textSymbolOutputDir!!,
                            SdkConstants.R_CLASS + SdkConstants.DOT_TXT
                        )
                            .toPath(),
                        manifestFile.toPath(),
                        params.symbolsWithPackageNameOutputFile.toPath()
                    )
                }
                appendOutput(
                    BuildOutput(
                        InternalArtifactType.PROCESSED_RES,
                        params.apkData,
                        resOutBaseNameFile,
                        params.manifestOutput.properties
                    ),
                    params.resPackageOutputFolder
                )
            } catch (e: ProcessException) {
                throw BuildException(
                    "Failed to process resources, see aapt output above for details.", e
                )
            }

        }
    }

    private class AaptSplitInvokerParams internal constructor(
        val manifestOutput: BuildOutput,
        val dependencies: Set<File>,
        val imports: Set<File>,
        splitList: SplitList,
        val featureResourcePackages: Set<File>,
        val apkData: ApkData,
        val generateCode: Boolean,
        val aapt2ServiceKey: Aapt2ServiceKey?,
        task: LinkApplicationAndroidResourcesTask
    ) : Serializable {
        val resourceConfigs: Set<String> = splitList.resourceConfigs
        val multiOutputPolicySplitList: Set<String> = splitList.getSplits(task.multiOutputPolicy)
        val variantScopeMainSplit: ApkData = task.variantScope.outputScope.mainSplit
        val resPackageOutputFolder: File = task.resPackageOutputFolder
        val isNamespaced: Boolean = task.isNamespaced
        val variantDataType: VariantType = task.variantScope.variantData.type
        val originalApplicationId: String? = task.originalApplicationId.get()
        val sourceOutputDir: File? = task.getSourceOutputDir()
        val textSymbolOutputDir: File? = task.textSymbolOutputDir.get()
        val proguardOutputFile: File? = task.proguardOutputFile
        val mainDexListProguardOutputFile: File? = task.mainDexListProguardOutputFile
        val buildTargetDensity: String? = task.buildTargetDensity
        val aaptOptions: com.android.builder.internal.aapt.AaptOptions = task.aaptOptions.convert()
        val variantType: VariantType = task.type
        val debuggable: Boolean = task.getDebuggable()
        val packageId: Int? = task.getResOffset()
        val incrementalFolder: File = task.incrementalFolder
        val androidJarPath: String =
            task.androidJar.get().absolutePath
        val convertedLibraryDependenciesPath: Path? =
            task.convertedLibraryDependencies?.singleFile()?.toPath()
        val inputResourcesDir: File? = task.inputResourcesDir?.singleFile()
        val mergeBlameFolder: File = task.mergeBlameLogFolder
        val isLibrary: Boolean = task.isLibrary
        val symbolsWithPackageNameOutputFile: File? = task.symbolsWithPackageNameOutputFile
        val useConditionalKeepRules: Boolean = task.useConditionalKeepRules
        val useFinalIds: Boolean = task.useFinalIds
    }

    @Input
    fun canHaveSplits(): Boolean {
        return variantScope.type.canHaveSplits
    }

    @Input
    fun getApplicationId(): String? {
        return applicationId.get()
    }

    @Optional
    @Input
    fun getResOffset(): Int? {
        return resOffsetSupplier?.get()
    }

    @OutputDirectory
    @Optional
    override fun getSourceOutputDir(): File? {
        return sourceOutputDir
    }

    @org.gradle.api.tasks.OutputFile
    @Optional
    fun getTextSymbolOutputFile(): File? {
        val outputDir = textSymbolOutputDir.get()
        return if (outputDir != null)
            File(outputDir, SdkConstants.R_CLASS + SdkConstants.DOT_TXT)
        else
            null
    }

    fun setAaptMainDexListProguardOutputFile(mainDexListProguardOutputFile: File) {
        this.mainDexListProguardOutputFile = mainDexListProguardOutputFile
    }

    @Input
    fun getTypeAsString(): String {
        return type.name
    }

    fun setType(type: VariantType) {
        this.type = type
    }

    fun setSourceOutputDir(sourceOutputDir: File?) {
        this.sourceOutputDir = sourceOutputDir
    }

    @Input
    fun getDebuggable(): Boolean {
        return debuggable
    }

    fun setDebuggable(debuggable: Boolean) {
        this.debuggable = debuggable
    }

    fun setMergeBlameLogFolder(mergeBlameLogFolder: File) {
        this.mergeBlameLogFolder = mergeBlameLogFolder
    }

    @Input
    fun getOriginalApplicationId(): String? {
        return originalApplicationId.get()
    }

    private fun findPackagedResForSplit(outputFolder: File?, apkData: ApkData): File? {
        val resourcePattern = Pattern.compile(
            FN_RES_BASE + RES_QUALIFIER_SEP + apkData.fullName + ".ap__(.*)"
        )

        if (outputFolder == null) {
            return null
        }
        val files = outputFolder.listFiles()
        if (files != null) {
            for (file in files) {
                val match = resourcePattern.matcher(file.name)
                // each time we match, we remove the associated filter from our copies.
                if (match.matches()
                    && !match.group(1).isEmpty()
                    && isValidSplit(apkData, match.group(1))
                ) {
                    return file
                }
            }
        }
        return null
    }

    /**
     * Returns true if the passed split identifier is a valid identifier (valid mean it is a
     * requested split for this task). A density split identifier can be suffixed with characters
     * added by aapt.
     */
    private fun isValidSplit(apkData: ApkData, splitWithOptionalSuffix: String): Boolean {

        var splitFilter = apkData.getFilter(VariantOutput.FilterType.DENSITY)
        if (splitFilter != null) {
            if (splitWithOptionalSuffix.startsWith(splitFilter.identifier)) {
                return true
            }
        }
        val mangledName = unMangleSplitName(splitWithOptionalSuffix)
        splitFilter = apkData.getFilter(VariantOutput.FilterType.LANGUAGE)
        return splitFilter != null && mangledName == splitFilter.identifier
    }

    /**
     * Un-mangle a split name as created by the aapt tool to retrieve a split name as configured in
     * the project's build.gradle.
     *
     *
     * when dealing with several split language in a single split, each language (+ optional
     * region) will be separated by an underscore.
     *
     *
     * note that there is currently an aapt bug, remove the 'r' in the region so for instance,
     * fr-rCA becomes fr-CA, temporarily put it back until it is fixed.
     *
     * @param splitWithOptionalSuffix the mangled split name.
     */
    private fun unMangleSplitName(splitWithOptionalSuffix: String): String {
        val mangledName = splitWithOptionalSuffix.replace("_".toRegex(), ",")
        return if (mangledName.contains("-r")) mangledName else mangledName.replace("-", "-r")
    }
}