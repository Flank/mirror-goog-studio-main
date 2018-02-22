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

package com.android.build.gradle.internal.transforms

import com.android.build.api.transform.Format
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.SecondaryFile
import com.android.build.api.transform.TransformInvocation
import com.android.build.gradle.internal.PostprocessingFeatures
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.pipeline.TransformManager.CONTENT_DEX
import com.android.build.gradle.internal.pipeline.TransformManager.CONTENT_JARS
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.transforms.TransformInputUtil.getAllFiles
import com.android.builder.core.VariantType
import com.android.builder.dexing.MainDexListConfig
import com.android.builder.dexing.ProguardConfig
import com.android.builder.dexing.R8OutputType
import com.android.builder.dexing.ToolConfig
import com.android.builder.dexing.runR8
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import java.io.File
import java.nio.file.Files

/**
 * Transform that uses R8 to convert class files to dex. In case of a library variant, this
 * transform outputs class files.
 *
 * R8 transforms inputs are: program class files, library class files (e.g. android.jar), Proguard
 * configuration files, main dex list configuration files, other tool-specific parameters. Output
 * is dex or class files, depending on whether we are building an APK, or AAR.
 */
class R8Transform(
    private val bootClasspath: Lazy<List<File>>,
    private val minSdkVersion: Int,
    private val isDebuggable: Boolean,
    private val java8Support: VariantScope.Java8LangSupport,
    private var disableTreeShaking: Boolean,
    private var disableMinification: Boolean,
    private val mainDexListFiles: FileCollection,
    private val mainDexRulesFiles: FileCollection,
    private val inputProguardMapping: FileCollection,
    private val outputProguardMapping: File,
    private val typesToOutput: MutableSet<QualifiedContent.ContentType>,
    proguardConfigurationFiles: ConfigurableFileCollection,
    variantType: VariantType
) :
        ProguardConfigurable(proguardConfigurationFiles, variantType) {

    // This is a huge sledgehammer, but it is necessary until http://b/72683872 is fixed.
    private val proguardConfigurations: MutableList<String> = mutableListOf("-ignorewarnings")

    constructor(
        scope: VariantScope,
        mainDexListFiles: FileCollection,
        mainDexRulesFiles: FileCollection,
        inputProguardMapping: FileCollection,
        outputProguardMapping: File
    ) :
            this(
                    lazy { scope.globalScope.androidBuilder.getBootClasspath(true) },
                    scope.minSdkVersion.featureLevel,
                    scope.variantConfiguration.buildType.isDebuggable,
                    scope.java8LangSupportType,
                    false,
                    false,
                    mainDexListFiles,
                    mainDexRulesFiles,
                    inputProguardMapping,
                    outputProguardMapping,
                    if (scope.variantData.type.isAar) CONTENT_JARS else CONTENT_DEX,
                    scope.globalScope.project.files(),
                    scope.variantData.type
            )

    override fun getName(): String = "r8"

    override fun getInputTypes(): MutableSet<out QualifiedContent.ContentType> = CONTENT_JARS

    override fun getOutputTypes(): MutableSet<out QualifiedContent.ContentType> = typesToOutput

    override fun isIncremental(): Boolean = false

    override fun getSecondaryFiles(): MutableCollection<SecondaryFile> =
        mutableListOf(
                SecondaryFile.nonIncremental(allConfigurationFiles),
                SecondaryFile.nonIncremental(mainDexListFiles),
                SecondaryFile.nonIncremental(mainDexRulesFiles)
        )

    override fun getParameterInputs(): MutableMap<String, Any> =
        mutableMapOf(
                "minSdkVersion" to minSdkVersion,
                "isDebuggable" to isDebuggable,
                "disableTreeShaking" to disableTreeShaking,
                "java8Support" to (java8Support == VariantScope.Java8LangSupport.R8),
                "disableMinification" to disableMinification,
                "proguardConfiguration" to proguardConfigurations
        )

    override fun getSecondaryFileOutputs(): MutableCollection<File> =
        mutableListOf(outputProguardMapping)

    override fun keep(keep: String) {
        proguardConfigurations.add("-keep " + keep)
    }

    override fun keepattributes() {
        proguardConfigurations.add("-keepattributes *")
    }

    override fun dontwarn(dontwarn: String) {
        proguardConfigurations.add("-dontwarn " + dontwarn)
    }

    override fun setActions(actions: PostprocessingFeatures) {
        disableTreeShaking = !actions.isRemoveUnusedCode
        disableMinification = !actions.isObfuscate
        if (!actions.isOptimize) {
            proguardConfigurations.add("-dontoptimize")
        }
    }

    override fun transform(transformInvocation: TransformInvocation) {
        val outputProvider = requireNotNull(
                transformInvocation.outputProvider,
                { "No output provider set" }
        )
        outputProvider.deleteAll()

        val r8OutputType: R8OutputType
        val outputFormat: Format
        if (typesToOutput == TransformManager.CONTENT_JARS) {
            r8OutputType = R8OutputType.CLASSES
            outputFormat = Format.JAR
        } else {
            r8OutputType = R8OutputType.DEX
            outputFormat = Format.DIRECTORY
        }
        val toolConfig = ToolConfig(
                minSdkVersion = minSdkVersion,
                isDebuggable = isDebuggable,
                disableTreeShaking = disableTreeShaking,
                disableDesugaring = java8Support != VariantScope.Java8LangSupport.R8,
                disableMinification = disableMinification,
                r8OutputType = r8OutputType
        )

        val proguardMappingInput =
                if (inputProguardMapping.isEmpty) null else inputProguardMapping.singleFile.toPath()
        val proguardConfig = ProguardConfig(
                allConfigurationFiles.files.map { it.toPath() },
                outputProguardMapping.toPath(),
                proguardMappingInput,
                proguardConfigurations
        )

        val mainDexListConfig = MainDexListConfig(
                mainDexRulesFiles.files.map { it.toPath() },
                mainDexListFiles.files.map { it.toPath() },
                MainDexListTransform.getPlatformRules().map { it -> "-keep " + it }
        )

        val allFiles = getAllFiles(transformInvocation.inputs).map { it.toPath() }

        val output = outputProvider.getContentLocation(
                "main",
                TransformManager.CONTENT_DEX,
                TransformManager.SCOPE_FULL_PROJECT,
                outputFormat
        )
        Files.createDirectories(output.toPath())

        val bootClasspathInputs =
                getAllFiles(transformInvocation.referencedInputs) + bootClasspath.value

        runR8(
                allFiles,
                output.toPath(),
                bootClasspathInputs.map { it.toPath() },
                toolConfig,
                proguardConfig,
                mainDexListConfig
        )
    }
}