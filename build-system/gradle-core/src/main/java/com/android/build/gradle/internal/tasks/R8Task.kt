/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.build.api.transform.Format
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.PostprocessingFeatures
import com.android.build.gradle.internal.scope.InternalArtifactType.DUPLICATE_CLASSES_CHECK
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.options.BooleanOption
import com.android.builder.core.VariantType
import com.android.builder.dexing.DexingType
import com.android.builder.dexing.MainDexListConfig
import com.android.builder.dexing.ProguardConfig
import com.android.builder.dexing.ProguardOutputFiles
import com.android.builder.dexing.R8OutputType
import com.android.builder.dexing.ToolConfig
import com.android.builder.dexing.getR8Version
import com.android.builder.dexing.runR8
import com.android.ide.common.blame.MessageReceiver
import com.android.utils.FileUtils
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File

/**
 * Task that uses R8 to convert class files to dex. In case of a library variant, this
 * task outputs class files.
 *
 * R8 task inputs are: program class files, library class files (e.g. android.jar), java resources,
 * Proguard configuration files, main dex list configuration files, other tool-specific parameters.
 * Output is dex or class files, depending on whether we are building an APK, or AAR.
 */


// TODO(b/139181913): add workers
@CacheableTask
abstract class R8Task: ProguardConfigurableTask() {

    @get:Input
    abstract val enableDesugaring: Property<Boolean>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mainDexListFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mainDexRulesFiles: ConfigurableFileCollection

    @get:Classpath
    abstract val bootClasspath: ConfigurableFileCollection

    private lateinit var messageReceiver: MessageReceiver

    @get:Input
    abstract val minSdkVersion: Property<Int>

    @get:Input
    abstract val debuggable: Property<Boolean>

    @get:Input
    abstract val disableTreeShaking: Property<Boolean>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val duplicateClassesCheck: ConfigurableFileCollection

    @get:Input
    abstract val disableMinification: Property<Boolean>

    @get:Input
    lateinit var proguardConfigurations: MutableList<String>
        private set

    @get:Input
    abstract val useFullR8: Property<Boolean>

    @get:Input
    lateinit var dexingType: DexingType
        private set

    // R8 will produce either classes or dex
    @get:Optional
    @get:OutputFile
    abstract val outputClasses: RegularFileProperty

    @get:Optional
    @get:OutputDirectory
    abstract val outputDex: DirectoryProperty

    @get:OutputFile
    abstract val outputResources: RegularFileProperty

    @Optional
    @OutputFile
    fun getProguardSeedsOutput(): File? =
        mappingFile.orNull?.asFile?.resolveSibling("seeds.txt")

    @Optional
    @OutputFile
    fun getProguardUsageOutput(): File? =
        mappingFile.orNull?.asFile?.resolveSibling("usage.txt")

    @get:Optional
    @get:OutputFile
    abstract val mainDexListOutput: RegularFileProperty

    class CreationAction(
        variantScope: VariantScope,
        isTestApplication: Boolean = false
    ) : ProguardConfigurableTask.CreationAction<R8Task>(variantScope, isTestApplication) {
        override val type = R8Task::class.java
        override val name =  variantScope.getTaskName("minify", "WithR8")

        private var disableTreeShaking: Boolean = false
        private var disableMinification: Boolean = false

        // This is a huge sledgehammer, but it is necessary until http://b/72683872 is fixed.
        private val proguardConfigurations: MutableList<String> = mutableListOf("-ignorewarnings")

        override fun handleProvider(taskProvider: TaskProvider<out R8Task>) {
            super.handleProvider(taskProvider)

            if (variantType.isAar) {
                variantScope.artifacts.producesFile(
                    artifactType = InternalArtifactType.SHRUNK_CLASSES,
                    operationType = BuildArtifactsHolder.OperationType.INITIAL,
                    taskProvider = taskProvider,
                    productProvider = R8Task::outputClasses,
                    fileName = "shrunkClasses.jar"
                )
            } else {
                variantScope.artifacts.producesDir(
                    artifactType = InternalArtifactType.DEX,
                    operationType = BuildArtifactsHolder.OperationType.APPEND,
                    taskProvider = taskProvider,
                    productProvider = R8Task::outputDex,
                    fileName = "shrunkDex"
                )
            }

            variantScope.artifacts.producesFile(
                artifactType = InternalArtifactType.SHRUNK_JAVA_RES,
                operationType = BuildArtifactsHolder.OperationType.INITIAL,
                taskProvider = taskProvider,
                productProvider = R8Task::outputResources,
                fileName = "shrunkJavaRes.jar"
            )

            if (variantScope.needsMainDexListForBundle) {
                variantScope
                    .artifacts
                    .producesFile(
                        InternalArtifactType.MAIN_DEX_LIST_FOR_BUNDLE,
                        BuildArtifactsHolder.OperationType.INITIAL,
                        taskProvider,
                        R8Task::mainDexListOutput,
                        "mainDexList.txt"
                    )
            }
        }

        override fun configure(task: R8Task) {
            super.configure(task)

            val artifacts = variantScope.artifacts


            task.enableDesugaring.set(
                variantScope.java8LangSupportType == VariantScope.Java8LangSupport.R8
                        && !variantType.isAar)

            task.bootClasspath.from(variantScope.globalScope.fullBootClasspath)
            task.minSdkVersion.set(variantScope.minSdkVersion.featureLevel)
            task.debuggable.set(variantScope.variantConfiguration.buildType.isDebuggable)
            task.disableTreeShaking.set(disableTreeShaking)
            task.disableMinification.set(disableMinification)
            task.messageReceiver = variantScope.globalScope.messageReceiver
            task.dexingType = variantScope.dexingType
            task.useFullR8.set(variantScope.globalScope.projectOptions[BooleanOption.FULL_R8])

            task.proguardConfigurations = proguardConfigurations

            task.duplicateClassesCheck.from(
                artifacts
                    .getFinalProductAsFileCollection(DUPLICATE_CLASSES_CHECK)
                    .get())

            variantScope.variantConfiguration.multiDexKeepProguard?.let { multiDexKeepProguard ->
                task.mainDexRulesFiles.from(multiDexKeepProguard)
            }

            if (artifacts.hasFinalProduct(
                    InternalArtifactType.LEGACY_MULTIDEX_AAPT_DERIVED_PROGUARD_RULES)) {
                task.mainDexRulesFiles.from(
                    artifacts.getFinalProduct(
                        InternalArtifactType.LEGACY_MULTIDEX_AAPT_DERIVED_PROGUARD_RULES
                    )
                )
            }

            variantScope.variantConfiguration.multiDexKeepFile?.let { multiDexKeepFile ->
                task.mainDexListFiles.from(multiDexKeepFile)
            }
        }

        override fun keep(keep: String) {
            proguardConfigurations.add("-keep $keep")
        }

        override fun keepAttributes() {
            proguardConfigurations.add("-keepattributes *")
        }

        override fun dontWarn(dontWarn: String) {
            proguardConfigurations.add("-dontwarn $dontWarn")
        }

        override fun setActions(actions: PostprocessingFeatures) {
            disableTreeShaking = !actions.isRemoveUnusedCode
            disableMinification = !actions.isObfuscate
            if (!actions.isOptimize) {
                proguardConfigurations.add("-dontoptimize")
            }
        }

    }

    override fun doTaskAction() {

        val output: Property<out FileSystemLocation> =
            if (variantType.orNull?.isAar == true) {
                outputClasses
            } else {
                outputDex
            }

        shrink(
            bootClasspath = bootClasspath.toList(),
            minSdkVersion = minSdkVersion.get(),
            isDebuggable = debuggable.get(),
            enableDesugaring = enableDesugaring.get(),
            disableTreeShaking = disableTreeShaking.get(),
            disableMinification = disableMinification.get(),
            mainDexListFiles = mainDexListFiles.toList(),
            mainDexRulesFiles = mainDexRulesFiles.toList(),
            inputProguardMapping =
                if (testedMappingFile.isEmpty) {
                    null
                } else {
                    testedMappingFile.singleFile
                },
            proguardConfigurationFiles = configurationFiles.toList(),
            proguardConfigurations = proguardConfigurations,
            variantType = variantType.orNull,
            messageReceiver = messageReceiver,
            dexingType = dexingType,
            useFullR8 = useFullR8.get(),
            referencedInputs = (referencedClasses + referencedResources).toList(),
            classes = classes.toList(),
            resources = resources.toList(),
            proguardOutputFiles =
                if (mappingFile.isPresent) {
                    ProguardOutputFiles(
                        mappingFile.get().asFile.toPath(),
                        getProguardSeedsOutput()!!.toPath(),
                        getProguardUsageOutput()!!.toPath())
                } else {
                    null
                },
            output = output.get().asFile,
            outputResources = outputResources.get().asFile,
            mainDexListOutput = mainDexListOutput.orNull?.asFile
        )
    }

    companion object {
        fun shrink(
            bootClasspath: List<File>,
            minSdkVersion: Int,
            isDebuggable: Boolean,
            enableDesugaring: Boolean,
            disableTreeShaking: Boolean,
            disableMinification: Boolean,
            mainDexListFiles: List<File>,
            mainDexRulesFiles: List<File>,
            inputProguardMapping: File?,
            proguardConfigurationFiles: List<File>,
            proguardConfigurations: MutableList<String>,
            variantType: VariantType?,
            messageReceiver: MessageReceiver,
            dexingType: DexingType,
            useFullR8: Boolean,
            referencedInputs: List<File>,
            classes: List<File>,
            resources: List<File>,
            proguardOutputFiles: ProguardOutputFiles?,
            output: File,
            outputResources: File,
            mainDexListOutput: File?
        ) {
            LoggerWrapper.getLogger(R8Task::class.java)
                .info(
                    """
                |R8 is a new Android code shrinker. If you experience any issues, please file a bug at
                |https://issuetracker.google.com, using 'Shrinker (R8)' as component name. You can
                |disable R8 by updating gradle.properties with 'android.enableR8=false'.
                |Current version is: ${getR8Version()}.
                |""".trimMargin()
                )

            val r8OutputType: R8OutputType
            val outputFormat: Format
            if (variantType?.isAar == true) {
                r8OutputType = R8OutputType.CLASSES
                outputFormat = Format.JAR
            } else {
                r8OutputType = R8OutputType.DEX
                outputFormat = Format.DIRECTORY
            }

            FileUtils.deleteIfExists(outputResources)
            when (outputFormat) {
                Format.DIRECTORY -> FileUtils.cleanOutputDir(output)
                Format.JAR -> FileUtils.deleteIfExists(output)
            }

            val toolConfig = ToolConfig(
                minSdkVersion = minSdkVersion,
                isDebuggable = isDebuggable,
                disableTreeShaking = disableTreeShaking,
                disableDesugaring = !enableDesugaring,
                disableMinification = disableMinification,
                r8OutputType = r8OutputType
            )

            val proguardConfig = ProguardConfig(
                proguardConfigurationFiles.map { it.toPath() },
                inputProguardMapping?.toPath(),
                proguardConfigurations,
                proguardOutputFiles
            )

            val mainDexListConfig = if (dexingType == DexingType.LEGACY_MULTIDEX) {
                MainDexListConfig(
                    mainDexRulesFiles.map { it.toPath() },
                    mainDexListFiles.map { it.toPath() },
                    getPlatformRules(),
                    mainDexListOutput?.toPath()
                )
            } else {
                MainDexListConfig()
            }

            val bootClasspathInputs = referencedInputs + bootClasspath

            runR8(
                classes.map { it.toPath() },
                output.toPath(),
                resources.map { it.toPath() },
                outputResources.toPath(),
                bootClasspathInputs.map { it.toPath() },
                toolConfig,
                proguardConfig,
                mainDexListConfig,
                messageReceiver,
                useFullR8
            )
        }
    }
}