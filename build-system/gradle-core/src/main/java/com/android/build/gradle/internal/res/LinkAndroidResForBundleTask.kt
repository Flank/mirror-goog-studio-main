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
import com.android.build.gradle.internal.aapt.AaptGeneration
import com.android.build.gradle.internal.aapt.AaptGradleFactory
import com.android.build.gradle.internal.dsl.convert
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.MODULE
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.FEATURE_RESOURCE_PKG
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH
import com.android.build.gradle.internal.res.namespaced.registerAaptService
import com.android.build.gradle.internal.scope.ExistingBuildElements
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_MANIFESTS
import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.AndroidBuilderTask
import com.android.build.gradle.internal.tasks.TaskInputHelper
import com.android.build.gradle.internal.tasks.Workers
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSetMetadata
import com.android.build.gradle.options.StringOption
import com.android.builder.core.VariantTypeImpl
import com.android.builder.internal.aapt.Aapt
import com.android.builder.internal.aapt.AaptPackageConfig
import com.android.ide.common.blame.MergingLog
import com.android.ide.common.blame.MergingLogRewriter
import com.android.ide.common.blame.ParsingProcessOutputHandler
import com.android.ide.common.blame.SourceFilePosition
import com.android.ide.common.blame.parser.ToolOutputParser
import com.android.ide.common.blame.parser.aapt.Aapt2OutputParser
import com.android.ide.common.build.ApkInfo
import com.android.sdklib.IAndroidTarget
import com.android.utils.FileUtils
import com.google.common.collect.ImmutableList
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.io.IOException
import java.util.function.Function
import java.util.function.Supplier
import javax.inject.Inject

/**
 * Task to link app resources into a proto format so that it can be consumed by the bundle tool.
 */
@CacheableTask
open class LinkAndroidResForBundleTask
@Inject constructor(workerExecutor: WorkerExecutor) : AndroidBuilderTask() {

    private var aaptGeneration: AaptGeneration? = null
    private val workers = Workers.getWorker(workerExecutor)

    @Input
    fun getAaptGenerationString() = aaptGeneration.toString()

    @get:Input
    var debuggable: Boolean = false
        private set

    private var pseudoLocalesEnabled: Boolean = false

    private lateinit var aaptOptions: com.android.build.gradle.internal.dsl.AaptOptions

    private var mergeBlameLogFolder: File? = null

    private var buildTargetDensity: String? = null

    @get:OutputFile
    lateinit var bundledResFile: File
        private set

    @get:InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    lateinit var featureResourcePackages: FileCollection
        private set

    private var resOffsetSupplier: Supplier<Int>? = null

    @Suppress("MemberVisibilityCanBePrivate")
    @get:Input
    @get:Optional
    val resOffset: Int?
        get() = resOffsetSupplier?.get()

    @get:Internal lateinit var versionNameSupplier: Supplier<String?>
        private set

    @get:Input
    @get:Optional
    val versionName
        get() = versionNameSupplier.get()

    @get:Internal lateinit var versionCodeSupplier: Supplier<Int?>
        private set

    @get:Input val versionCode
        get() = versionCodeSupplier.get()

    @get:OutputDirectory
    lateinit var incrementalFolder: File
        private set

    @get:Input
    lateinit var mainSplit: ApkInfo
        private set

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    var aapt2FromMaven: FileCollection? = null
        private set

    @TaskAction
    fun taskAction() {

        val manifestFile = ExistingBuildElements.from(MERGED_MANIFESTS, manifestFiles)
                .element(mainSplit)
                ?.outputFile
                ?: throw RuntimeException("Cannot find merged manifest file")

        FileUtils.mkdirs(bundledResFile.parentFile)

        val featurePackagesBuilder = ImmutableList.builder<File>()
        for (featurePackage in featureResourcePackages) {
            val buildElements =
                ExistingBuildElements.from(InternalArtifactType.PROCESSED_RES, featurePackage)
            if (buildElements.size() != 1) {
                throw IOException("Found more than one PROCESSED_RES output at " + featurePackage)
            }

            featurePackagesBuilder.add(buildElements.iterator().next().outputFile)
        }

        val config = AaptPackageConfig(
                androidJarPath = builder.target.getPath(IAndroidTarget.ANDROID_JAR),
                generateProtos = true,
                manifestFile = manifestFile,
                options = aaptOptions.convert(),
                resourceOutputApk = bundledResFile,
                variantType = VariantTypeImpl.BASE_APK,
                debuggable = debuggable,
                packageId =  resOffset,
                dependentFeatures = featurePackagesBuilder.build(),
                pseudoLocalize = getPseudoLocalesEnabled(),
                resourceDirs = ImmutableList.of(checkNotNull(getInputResourcesDir()).single()))
        if (logger.isInfoEnabled) {
            logger.info("Aapt output file {}", bundledResFile.absolutePath)
        }

        val aapt2ServiceKey = registerAaptService(
            aapt2FromMaven = aapt2FromMaven,
            buildToolInfo = null,
            logger = builder.logger
        )
        //TODO: message rewriting.
        workers.use {
            it.submit(Aapt2ProcessResourcesRunnable::class.java,
                Aapt2ProcessResourcesRunnable.Params(aapt2ServiceKey, config))
        }
    }

    /**
     * Create an instance of AAPT. Whenever calling this method make sure the close() method is
     * called on the instance once the work is done.
     */
    private fun makeAapt(): Aapt {
        val builder = builder
        val mergingLog = MergingLog(mergeBlameLogFolder!!)

        val processOutputHandler = ParsingProcessOutputHandler(
                ToolOutputParser(Aapt2OutputParser(), iLogger),
                MergingLogRewriter(
                        Function<SourceFilePosition, SourceFilePosition> { mergingLog.find(it) },
                        builder.messageReceiver))

        return AaptGradleFactory.make(
            aaptGeneration!!,
            builder,
            processOutputHandler,
            true,
            aaptOptions.cruncherProcesses)
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

    @Input
    fun getBuildToolsVersion(): String {
        return buildTools.revision.toString()
    }

    @Input
    fun getPseudoLocalesEnabled(): Boolean {
        return pseudoLocalesEnabled
    }

    @Nested
    fun getAaptOptions(): com.android.build.gradle.internal.dsl.AaptOptions? {
        return aaptOptions
    }

    class ConfigAction(private val variantScope: VariantScope)
        : TaskConfigAction<LinkAndroidResForBundleTask> {

        override fun getName(): String {
            return variantScope.getTaskName("bundle", "Resources")
        }

        override fun getType(): Class<LinkAndroidResForBundleTask> {
            return LinkAndroidResForBundleTask::class.java
        }

        override fun execute(processResources: LinkAndroidResForBundleTask) {
            val variantData = variantScope.variantData

            val projectOptions = variantScope.globalScope.projectOptions

            val config = variantData.variantConfiguration

            processResources.setAndroidBuilder(variantScope.globalScope.androidBuilder)
            processResources.variantName = config.fullName
            processResources.bundledResFile = variantScope.artifacts
                .appendArtifact(InternalArtifactType.LINKED_RES_FOR_BUNDLE,
                    processResources,
                    "bundled-res.ap_")
            processResources.aaptGeneration = AaptGeneration.fromProjectOptions(projectOptions)

            processResources.incrementalFolder = variantScope.getIncrementalDir(name)

            processResources.versionCodeSupplier = TaskInputHelper.memoize {
                config.versionCode
            }
            processResources.versionNameSupplier = TaskInputHelper.memoize {
                config.versionName
            }

            processResources.mainSplit = variantData.outputScope.mainSplit

            processResources.manifestFiles = variantScope.artifacts
                .getFinalArtifactFiles(MERGED_MANIFESTS)

            processResources.inputResourcesDir =
                    variantScope.artifacts.getFinalArtifactFiles(InternalArtifactType.MERGED_RES)

            processResources.featureResourcePackages = variantScope.getArtifactFileCollection(
                COMPILE_CLASSPATH, MODULE, FEATURE_RESOURCE_PKG)

            if (variantScope.type.isFeatureSplit) {
                // get the res offset supplier
                processResources.resOffsetSupplier =
                        FeatureSetMetadata.getInstance().getResOffsetSupplierForTask(
                            variantScope, processResources)
            }

            processResources.debuggable = config.buildType.isDebuggable
            processResources.aaptOptions = variantScope.globalScope.extension.aaptOptions
            processResources.pseudoLocalesEnabled = config.buildType.isPseudoLocalesEnabled

            processResources.buildTargetDensity = projectOptions.get(StringOption.IDE_BUILD_TARGET_DENSITY)

            processResources.mergeBlameLogFolder = variantScope.resourceBlameLogDir
            processResources.aapt2FromMaven = getAapt2FromMaven(variantScope.globalScope)
        }
    }

}