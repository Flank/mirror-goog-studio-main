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

package com.android.build.gradle.internal.tasks.databinding

import android.databinding.tool.CompilerArguments
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.options.BooleanOption
import com.google.common.collect.Iterables
import org.gradle.api.Task
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.CommandLineArgumentProvider
import java.io.File

/**
 * Arguments passed to data binding. This class mimics the [CompilerArguments] class except that it
 * also implements [CommandLineArgumentProvider] for input/output annotations.
 */
@Suppress("MemberVisibilityCanBePrivate")
class DataBindingCompilerArguments constructor(
    @get:Input
    val artifactType: CompilerArguments.Type,

    @get:Input
    val modulePackage: String,

    @get:Input
    val minApi: Int,

    // We can't set the sdkDir as an @InputDirectory because it is too large to compute a hash. We
    // can't set it as an @Input either because it would break cache relocatability. Therefore, we
    // annotate it with @Internal, expecting that the directory's contents should be stable and this
    // won't affect correctness.
    @get:Internal
    val sdkDir: File,

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val buildDir: File,

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val layoutInfoDir: File,

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val classLogDir: File,

    @get:Optional
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val baseFeatureInfoDir: File?,

    @get:Optional
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val featureInfoDir: File?,

    @get:Optional
    @get:OutputDirectory
    val aarOutDir: File?,

    @get:Optional
    @get:OutputFile
    val exportClassListOutFile: File?,

    @get:Input
    val enableDebugLogs: Boolean,

    @get:Input
    val printEncodedErrorLogs: Boolean,

    @get:Input
    val isTestVariant: Boolean,

    @get:Input
    val isEnabledForTests: Boolean,

    @get:Input
    val isEnableV2: Boolean
) : CommandLineArgumentProvider {

    fun toMap(): Map<String, String> {
        return CompilerArguments(
            artifactType = artifactType,
            modulePackage = modulePackage,
            minApi = minApi,
            sdkDir = sdkDir,
            buildDir = buildDir,
            layoutInfoDir = layoutInfoDir,
            classLogDir = classLogDir,
            baseFeatureInfoDir = baseFeatureInfoDir,
            featureInfoDir = featureInfoDir,
            aarOutDir = aarOutDir,
            exportClassListOutFile = exportClassListOutFile,
            enableDebugLogs = enableDebugLogs,
            printEncodedErrorLogs = printEncodedErrorLogs,
            isTestVariant = isTestVariant,
            isEnabledForTests = isEnabledForTests,
            isEnableV2 = isEnableV2
        ).toMap()
    }

    override fun asArguments(): Iterable<String> {
        // Don't need to sort the returned list as the order shouldn't matter to Gradle.
        // Also don't need to escape the key and value strings as they will be passed as-is to
        // the Java compiler.
        return toMap().map { entry -> "-A${entry.key}=${entry.value}" }
    }

    /**
     * Configures inputs and outputs for the given task with the properties of the current instance.
     */
    fun configureInputsOutputsForTask(task: Task) {
        // The following needs to be consistent with the annotations in this class.
        val inputs = task.inputs
        val outputs = task.outputs
        val prefix = "databinding"

        inputs.property("$prefix.artifactType", artifactType)
        inputs.property("$prefix.modulePackage", modulePackage)
        inputs.property("$prefix.minApi", minApi)

        inputs.dir(buildDir).withPropertyName("$prefix.buildDir")
            .withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.dir(layoutInfoDir).withPropertyName("$prefix.layoutInfoDir")
            .withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.dir(classLogDir).withPropertyName("$prefix.classLogDir")
            .withPathSensitivity(PathSensitivity.RELATIVE)

        if (baseFeatureInfoDir != null) {
            inputs.dir(baseFeatureInfoDir).withPropertyName("$prefix.baseFeatureInfoDir")
                .withPathSensitivity(PathSensitivity.RELATIVE)
        }
        if (featureInfoDir != null) {
            inputs.dir(featureInfoDir).withPropertyName("$prefix.featureInfoDir")
                .withPathSensitivity(PathSensitivity.RELATIVE)
        }
        if (aarOutDir != null) {
            outputs.dir(aarOutDir).withPropertyName("$prefix.aarOutDir")
        }
        if (exportClassListOutFile != null) {
            outputs.file(exportClassListOutFile).withPropertyName("$prefix.exportClassListOutFile")
        }

        inputs.property("$prefix.enableDebugLogs", enableDebugLogs)
        inputs.property("$prefix.printEncodedErrorLogs", printEncodedErrorLogs)
        inputs.property("$prefix.isTestVariant", isTestVariant)
        inputs.property("$prefix.isEnabledForTests", isEnabledForTests)
        inputs.property("$prefix.isEnableV2", isEnableV2)
    }

    companion object {

        @JvmStatic
        fun createArguments(
            variantScope: VariantScope,
            enableDebugLogs: Boolean,
            printEncodedErrorLogs: Boolean
        ): DataBindingCompilerArguments {
            val globalScope = variantScope.globalScope
            val extension = globalScope.extension
            val variantData = variantScope.variantData
            val variantConfig = variantScope.variantConfiguration
            val artifacts = variantScope.artifacts

            // Get artifactType
            val artifactVariantData = if (variantData.type.isTestComponent) {
                variantScope.testedVariantData!!
            } else {
                variantData
            }
            val artifactType = if (artifactVariantData.type.isAar) {
                CompilerArguments.Type.LIBRARY
            } else {
                if (artifactVariantData.type.isBaseModule) {
                    CompilerArguments.Type.APPLICATION
                } else {
                    CompilerArguments.Type.FEATURE
                }
            }

            // TODO: find a way to not pass the packageName if possible, as this may force us to
            // parse the manifest for data binding during configuration.
            val modulePackage = variantConfig.originalApplicationId

            // Get classLogDir
            val classLogDir = Iterables.getOnlyElement<File>(
                artifacts.getFinalArtifactFiles(
                    InternalArtifactType.DATA_BINDING_BASE_CLASS_LOG_ARTIFACT
                ).get()
            )

            // Get baseFeatureInfoDir
            val baseFeatureInfoDir = if (artifacts.hasArtifact(
                    InternalArtifactType.FEATURE_DATA_BINDING_BASE_FEATURE_INFO
                )
            ) {
                Iterables.getOnlyElement<File>(
                    artifacts
                        .getFinalArtifactFiles(
                            InternalArtifactType
                                .FEATURE_DATA_BINDING_BASE_FEATURE_INFO
                        )
                        .get()
                )
            } else {
                null
            }

            // Get featureInfoDir
            val featureInfoDir = if (artifacts.hasArtifact(
                    InternalArtifactType.FEATURE_DATA_BINDING_FEATURE_INFO
                )
            ) {
                Iterables.getOnlyElement<File>(
                    artifacts
                        .getFinalArtifactFiles(
                            InternalArtifactType
                                .FEATURE_DATA_BINDING_FEATURE_INFO
                        )
                        .get()
                )
            } else {
                null
            }

            // Get exportClassListOutFile
            val exportClassListOutFile = if (variantData.type.isExportDataBindingClassList) {
                variantScope.generatedClassListOutputFileForDataBinding
            } else {
                null
            }

            return DataBindingCompilerArguments(
                artifactType = artifactType,
                modulePackage = modulePackage,
                minApi = variantConfig.minSdkVersion.apiLevel,
                sdkDir = globalScope.sdkHandler.checkAndGetSdkFolder(),
                buildDir = variantScope.buildFolderForDataBindingCompiler,
                layoutInfoDir = variantScope.layoutInfoOutputForDataBinding,
                classLogDir = classLogDir,
                baseFeatureInfoDir = baseFeatureInfoDir,
                featureInfoDir = featureInfoDir,
                aarOutDir = variantScope.bundleArtifactFolderForDataBinding,
                exportClassListOutFile = exportClassListOutFile,
                enableDebugLogs = enableDebugLogs,
                printEncodedErrorLogs = printEncodedErrorLogs,
                isTestVariant = variantData.type.isTestComponent,
                isEnabledForTests = extension.dataBinding.isEnabledForTests,
                isEnableV2 = globalScope.projectOptions.get(BooleanOption.ENABLE_DATA_BINDING_V2)
            )
        }
    }
}