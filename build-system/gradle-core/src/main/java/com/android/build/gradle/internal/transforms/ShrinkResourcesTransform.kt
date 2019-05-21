/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.internal.transforms

import com.android.build.api.artifact.BuildableArtifact
import com.android.build.api.transform.QualifiedContent.ContentType
import com.android.build.api.transform.QualifiedContent.DefaultContentType
import com.android.build.api.transform.QualifiedContent.Scope
import com.android.build.api.transform.SecondaryFile
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformInvocation
import com.android.build.gradle.internal.api.artifact.*
import com.android.build.gradle.internal.dsl.AaptOptions
import com.android.build.gradle.internal.pipeline.ExtendedContentType
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.scope.ApkData
import com.android.build.gradle.internal.scope.BuildElements
import com.android.build.gradle.internal.scope.BuildElementsTransformParams
import com.android.build.gradle.internal.scope.BuildElementsTransformRunnable
import com.android.build.gradle.internal.scope.BuildOutput
import com.android.build.gradle.internal.scope.ExistingBuildElements
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.Workers
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.MultiOutputPolicy
import com.android.build.gradle.tasks.ResourceUsageAnalyzer
import com.android.builder.core.VariantType
import com.android.utils.FileUtils
import com.google.common.base.Joiner
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Maps
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.xml.parsers.ParserConfigurationException
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Provider
import org.xml.sax.SAXException

/**
 * Implementation of Resource Shrinking as a transform.
 *
 * Since this transform only reads the data from the stream but does not output anything
 * back into the stream, it is a no-op transform, asking only for referenced scopes, and not
 * "consumed" scopes.
 */
class ShrinkResourcesTransform(
    /**
     * Associated variant data that the strip task will be run against. Used to locate not only
     * locations the task needs (e.g. for resources and generated R classes) but also to obtain the
     * resource merging task, since we will run it a second time here to generate a new .ap_ file
     * with fewer resources
     */
    private val variantData: BaseVariantData,
    private val uncompressedResources: Provider<Directory>,
    private val logger: Logger
) : Transform() {

    private val lightRClasses: Provider<RegularFile>
    private val resourceDir: Provider<Directory>
    private val mappingFileSrc: BuildableArtifact?
    private val mergedManifests: Provider<Directory>

    private val aaptOptions: AaptOptions
    private val variantType: VariantType
    private val isDebuggableBuildType: Boolean
    private val multiOutputPolicy: MultiOutputPolicy

    private var compressedResources: DirectoryProperty? = null

    init {
        val variantScope = variantData.scope

        val artifacts = variantScope.artifacts

        this.lightRClasses = artifacts.getFinalProduct(
            InternalArtifactType.COMPILE_AND_RUNTIME_NOT_NAMESPACED_R_CLASS_JAR
        )

        this.resourceDir = variantScope
            .artifacts
            .getFinalProduct(InternalArtifactType.MERGED_NOT_COMPILED_RES)
        this.mappingFileSrc =
            if (variantScope.artifacts.hasArtifact(InternalArtifactType.APK_MAPPING))
                variantScope
                    .artifacts
                    .getFinalArtifactFiles(InternalArtifactType.APK_MAPPING)
            else
                null
        this.mergedManifests = artifacts.getFinalProduct(InternalArtifactType.MERGED_MANIFESTS)

        this.aaptOptions = variantScope.globalScope.extension.aaptOptions
        this.variantType = variantData.type
        this.isDebuggableBuildType = variantData.variantConfiguration.buildType.isDebuggable
        this.multiOutputPolicy = variantData.multiOutputPolicy
    }

    override fun getName(): String {
        return "shrinkRes"
    }

    override fun getInputTypes(): Set<ContentType> {
        // When R8 produces dex files, this transform analyzes them. If R8 or Proguard produce
        // class files, this transform will analyze those. That is why both types are specified.
        return ImmutableSet.of(ExtendedContentType.DEX, DefaultContentType.CLASSES)
    }

    override fun getOutputTypes(): Set<ContentType> = setOf()

    override fun getScopes(): MutableSet<in Scope> {
        return TransformManager.EMPTY_SCOPES
    }

    override fun getReferencedScopes(): MutableSet<in Scope> {
        return TransformManager.SCOPE_FULL_PROJECT
    }

    /**
     * Sets the directory where we should output compressed resources
     *
     * @param directory the output directory
     */
    override fun setOutputDirectory(directory: DirectoryProperty) {
        compressedResources = directory
    }

    override fun getSecondaryFiles(): Collection<SecondaryFile> {
        val secondaryFiles = mutableListOf<SecondaryFile>()

        // FIXME use Task output to get FileCollection for sourceDir/resourceDir
        secondaryFiles.add(SecondaryFile.nonIncremental(lightRClasses.get().asFile))
        secondaryFiles.add(SecondaryFile.nonIncremental(resourceDir.get().asFile))

        if (mappingFileSrc != null) {
            secondaryFiles.add(SecondaryFile.nonIncremental(mappingFileSrc))
        }

        secondaryFiles.add(SecondaryFile.nonIncremental(mergedManifests.get().asFile))
        secondaryFiles.add(SecondaryFile.nonIncremental(uncompressedResources.get().asFile))

        return secondaryFiles
    }

    override fun getParameterInputs(): Map<String, Any> {
        val params = Maps.newHashMapWithExpectedSize<String, Any>(7)
        params["aaptOptions"] = Joiner.on(";")
            .join(
                if (aaptOptions.ignoreAssetsPattern != null) {
                    aaptOptions.ignoreAssetsPattern
                } else {
                    ""
                },
                if (aaptOptions.noCompress != null) {
                    Joiner.on(":").join(aaptOptions.noCompress)
                } else {
                    ""
                },
                aaptOptions.failOnMissingConfigEntry,
                if (aaptOptions.additionalParameters != null) {
                    Joiner.on(":").join(aaptOptions.additionalParameters!!)
                } else {
                    ""
                },
                aaptOptions.cruncherProcesses
            )
        params["variantType"] = variantType.name
        params["isDebuggableBuildType"] = isDebuggableBuildType
        params["splitHandlingPolicy"] = multiOutputPolicy

        return params
    }

    override fun getSecondaryDirectoryOutputs(): Collection<File> {
        return ImmutableList.of(compressedResources!!.get().asFile)
    }

    override fun isIncremental(): Boolean {
        return false
    }

    override fun isCacheable(): Boolean {
        return true
    }

    override fun transform(invocation: TransformInvocation) {

        val referencedInputs = invocation.referencedInputs
        val classes = mutableListOf<File>()
        for (transformInput in referencedInputs) {
            for (directoryInput in transformInput.directoryInputs) {
                classes.add(directoryInput.file)
            }
            for (jarInput in transformInput.jarInputs) {
                classes.add(jarInput.file)
            }
        }

        val mergedManifestsOutputs =
            ExistingBuildElements.from(InternalArtifactType.MERGED_MANIFESTS, mergedManifests)

        Workers.preferWorkers(
            invocation.context.projectName,
            invocation.context.path,
            invocation.context.workerExecutor
        ).use { workers ->
            ExistingBuildElements.from(InternalArtifactType.PROCESSED_RES, uncompressedResources)
                .transform(
                    workers,
                    SplitterRunnable::class.java
                ) { apkInfo: ApkData, buildInput: File ->
                    SplitterParams(
                        apkInfo,
                        buildInput,
                        mergedManifestsOutputs,
                        classes,
                        this
                    )
                }
                .into(
                    InternalArtifactType.SHRUNK_PROCESSED_RES,
                    compressedResources!!.get().asFile
                )
        }
    }

    private inner class SplitterRunnable @Inject
    constructor(params: SplitterParams) : BuildElementsTransformRunnable(params) {

        override fun run() {
            val params = params as SplitterParams
            var reportFile: File? = null
            if (params.mappingFile != null) {
                val logDir = params.mappingFile.parentFile
                if (logDir != null) {
                    reportFile = File(logDir, "resources.txt")
                }
            }

            FileUtils.mkdirs(params.output.parentFile)

            if (params.mergedManifest == null) {
                try {
                    FileUtils.copyFile(
                        params.uncompressedResourceFile, params.output
                    )
                } catch (e: IOException) {
                    Logging.getLogger(ShrinkResourcesTransform::class.java)
                        .error("Failed to copy uncompressed resource file :", e)
                    throw RuntimeException("Failed to copy uncompressed resource file", e)
                }

                return
            }

            // Analyze resources and usages and strip out unused
            val analyzer = ResourceUsageAnalyzer(
                params.lightRClasses,
                params.classes,
                params.mergedManifest.outputFile,
                params.mappingFile,
                params.resourceDir,
                reportFile,
                ResourceUsageAnalyzer.ApkFormat.BINARY
            )
            try {
                analyzer.isVerbose = params.isInfoLoggingEnabled
                analyzer.isDebug = params.isDebugLoggingEnabled
                try {
                    analyzer.analyze()
                } catch (e: IOException) {
                    throw RuntimeException(e)
                } catch (e: ParserConfigurationException) {
                    throw RuntimeException(e)
                } catch (e: SAXException) {
                    throw RuntimeException(e)
                }

                // Just rewrite the .ap_ file to strip out the res/ files for unused resources
                try {
                    analyzer.rewriteResourceZip(
                        params.uncompressedResourceFile, params.output
                    )
                } catch (e: IOException) {
                    throw RuntimeException(e)
                }

                // Dump some stats
                val unused = analyzer.unusedResourceCount
                if (unused > 0) {
                    val sb = StringBuilder(200)
                    sb.append("Removed unused resources")

                    // This is a bit misleading until we can strip out all resource types:
                    //int total = analyzer.getTotalResourceCount()
                    //sb.append("(" + unused + "/" + total + ")")

                    val before = params.uncompressedResourceFile.length()
                    val after = params.output.length()
                    val percent = ((before - after) * 100 / before).toInt().toLong()
                    sb.append(": Binary resource data reduced from ${toKbString(before)}")
                        .append("KB to ${toKbString(after)}")
                        .append("KB: Removed ${percent}%")
                    if (!ourWarned) {
                        ourWarned = true
                        sb.append("""
                            Note: If necessary, you can disable resource shrinking by adding
                            android {
                                buildTypes {
                                    ${params.buildTypeName} {
                                        shrinkResources false
                                    }
                                }
                            }""".trimIndent())
                    }

                    logger.log(LogLevel.INFO, sb.toString())
                }
            } finally {
                analyzer.dispose()
            }
        }
    }

    private inner class SplitterParams internal constructor(
        apkInfo: ApkData,
        val uncompressedResourceFile: File,
        mergedManifests: BuildElements,
        val classes: List<File>,
        transform: ShrinkResourcesTransform
    ) : BuildElementsTransformParams() {
        override val output: File = File(
            transform.compressedResources!!.get().asFile,
            "resources-${apkInfo.baseName}-stripped.ap_"
        )
        val mergedManifest: BuildOutput? = mergedManifests.element(apkInfo)
        val mappingFile: File? = transform.mappingFileSrc?.singleFile()
        val buildTypeName: String = transform.variantData.variantConfiguration.buildType.name
        val lightRClasses: File = transform.lightRClasses.get().asFile
        val resourceDir: File = transform.resourceDir.get().asFile
        val isInfoLoggingEnabled: Boolean = transform.logger.isEnabled(LogLevel.INFO)
        val isDebugLoggingEnabled: Boolean = transform.logger.isEnabled(LogLevel.DEBUG)

    }

    companion object {

        /** Whether we've already warned about how to turn off shrinking. Used to avoid
         * repeating the same multi-line message for every repeated abi split.  */
        private var ourWarned = true // Logging disabled until shrinking is on by default.

        private fun toKbString(size: Long): String {
            return Integer.toString(size.toInt() / 1024)
        }
    }
}
