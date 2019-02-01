/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this/ file except in compliance with the License.
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

package com.android.build.gradle.tasks

import com.android.build.api.artifact.BuildableArtifact
import com.android.build.gradle.internal.api.artifact.singleFile
import com.android.build.gradle.internal.dsl.TestOptions
import com.android.build.gradle.internal.scope.ApkData
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.ExistingBuildElements
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.APK_FOR_LOCAL_TEST
import com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_ASSETS
import com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_MANIFESTS
import com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_RES
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.AndroidVariantTask
import com.android.build.gradle.internal.tasks.Workers
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.options.BooleanOption
import com.android.ide.common.workers.WorkerExecutorFacade
import com.google.common.annotations.VisibleForTesting
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.io.IOException
import java.io.Serializable
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import javax.inject.Inject

/**
 * Generates the `test_config.properties` file that is put on the classpath for running unit
 * tests.
 *
 * See DSL documentation at [TestOptions.UnitTestOptions.isIncludeAndroidResources].
 */
@CacheableTask
open class GenerateTestConfig @Inject constructor(workerExecutor: WorkerExecutor, objectFactory: ObjectFactory) :
    AndroidVariantTask() {

    // This task has 2 types of inputs:
    //   - testConfigInputs: Inputs that do not directly affect the output
    //   - testConfigProperties: Inputs that are computed from testConfigInputs and directly affect
    //     the output
    // Since we want this task to rerun only when necessary, we do not register the indirect inputs
    // but only the direct inputs with Gradle.
    private lateinit var testConfigInputs: TestConfigInputs

    @get:Nested
    val testConfigProperties: TestConfigProperties by lazy {
        // The base for the relative paths in the test config file should be the current project
        // directory, not the root project directory.
        testConfigInputs.computeProperties(project.projectDir)
    }

    @get:OutputDirectory
    val outputDirectory: DirectoryProperty = objectFactory.directoryProperty()

    private val workers: WorkerExecutorFacade = Workers.getWorker(workerExecutor)

    @TaskAction
    fun generateTestConfig() {
        workers.submit(
            GenerateTestConfigRunnable::class.java,
            GenerateTestConfigParams(testConfigProperties, outputDirectory.get().asFile)
        )
        workers.close()
    }

    private class GenerateTestConfigRunnable
    @Inject internal constructor(private val params: GenerateTestConfigParams) : Runnable {

        override fun run() {
            generateTestConfigFile(params.testConfigProperties, params.outputDirectory.toPath())
        }
    }

    private class GenerateTestConfigParams internal constructor(
        val testConfigProperties: TestConfigProperties,
        val outputDirectory: File
    ) : Serializable

    class CreationAction(scope: VariantScope) :
        VariantTaskCreationAction<GenerateTestConfig>(scope) {

        override val name: String
            get() = variantScope.getTaskName("generate", "Config")

        override val type: Class<GenerateTestConfig>
            get() = GenerateTestConfig::class.java

        override fun handleProvider(taskProvider: TaskProvider<out GenerateTestConfig>) {
            super.handleProvider(taskProvider)

            variantScope.artifacts
                .producesDir(
                    InternalArtifactType.UNIT_TEST_CONFIG_DIRECTORY,
                    BuildArtifactsHolder.OperationType.INITIAL,
                    taskProvider,
                    taskProvider.map { task -> task.outputDirectory },
                    "out")
        }

        override fun configure(task: GenerateTestConfig) {
            super.configure(task)

            task.testConfigInputs = TestConfigInputs(variantScope)
        }
    }

    class TestConfigInputs(scope: VariantScope) {
        @get:Input
        val isUseRelativePathEnabled: Boolean

        @get:InputFiles
        @get:PathSensitive(PathSensitivity.RELATIVE)
        @get:Optional
        val resourceApk: BuildableArtifact?

        @get:InputFiles
        @get:PathSensitive(PathSensitivity.RELATIVE)
        @get:Optional
        val mergedResources: BuildableArtifact?

        @get:InputFiles
        @get:PathSensitive(PathSensitivity.RELATIVE)
        val mergedAssets: Provider<Directory>

        @get:InputFiles
        @get:PathSensitive(PathSensitivity.RELATIVE)
        val mergedManifest: Provider<Directory>

        @get:Input
        val mainApkInfo: ApkData

        private val packageNameOfFinalRClassProvider: () -> String

        init {
            val testedVariantData = scope.testedVariantData ?: error("Not a unit test variant")
            val testedScope = testedVariantData.scope
            val unitTestBinaryResourcesEnabled = scope
                .globalScope
                .projectOptions
                .get(BooleanOption.ENABLE_UNIT_TEST_BINARY_RESOURCES)

            isUseRelativePathEnabled = scope.globalScope.projectOptions.get(
                BooleanOption.USE_RELATIVE_PATH_IN_TEST_CONFIG
            )
            resourceApk = if (unitTestBinaryResourcesEnabled) {
                scope.artifacts.getFinalArtifactFiles(APK_FOR_LOCAL_TEST)
            } else {
                null
            }
            mergedResources = if (unitTestBinaryResourcesEnabled) {
                null
            } else {
                scope.artifacts.getFinalArtifactFiles(MERGED_RES)
            }
            mergedAssets = testedScope.artifacts.getFinalProduct(MERGED_ASSETS)
            mergedManifest = testedScope.artifacts.getFinalProduct(MERGED_MANIFESTS)
            mainApkInfo = testedScope.outputScope.mainSplit
            packageNameOfFinalRClassProvider = {
                testedScope.variantConfiguration.originalApplicationId
            }
        }

        @get:Input
        val packageNameOfFinalRClass: String by lazy {
            packageNameOfFinalRClassProvider()
        }

        fun computeProperties(projectDir: File): TestConfigProperties {
            val manifestOutput =
                ExistingBuildElements.from(InternalArtifactType.MERGED_MANIFESTS, mergedManifest)
                    .element(mainApkInfo) ?: error("Unable to find manifest output")

            return TestConfigProperties(
                resourceApk?.let { getRelativePathIfRequired(it.singleFile(), projectDir) },
                mergedResources?.let { getRelativePathIfRequired(it.singleFile(), projectDir) },
                getRelativePathIfRequired(mergedAssets.get().asFile, projectDir),
                getRelativePathIfRequired(manifestOutput.outputFile, projectDir),
                packageNameOfFinalRClass
            )
        }

        private fun getRelativePathIfRequired(file: File, rootProjectDir: File): String {
            return if (isUseRelativePathEnabled) {
                rootProjectDir.toPath().relativize(file.toPath()).toString()
            } else {
                rootProjectDir.toPath().resolve(file.toPath()).toString()
            }
        }
    }

    class TestConfigProperties constructor(
        @get:Input
        @get:Optional
        val resourceApkFile: String?,

        @get:Input
        @get:Optional
        val mergedResourcesDir: String?,

        @get:Input
        val mergedAssetsDir: String,

        @get:Input
        val mergedManifestDir: String,

        @get:Input
        val customPackage: String
    ) : Serializable

    companion object {

        private const val TEST_CONFIG_FILE = "com/android/tools/test_config.properties"
        private const val ANDROID_RESOURCE_APK = "android_resource_apk"
        private const val ANDROID_MERGED_RESOURCES = "android_merged_resources"
        private const val ANDROID_MERGED_ASSETS = "android_merged_assets"
        private const val ANDROID_MERGED_MANIFEST = "android_merged_manifest"
        private const val ANDROID_CUSTOM_PACKAGE = "android_custom_package"
        private const val COMMENT_GENERATED_BY_AGP = "Generated by the Android Gradle plugin"

        @JvmStatic
        @VisibleForTesting
        @Throws(IOException::class)
        fun generateTestConfigFile(config: TestConfigProperties, outputDir: Path) {
            val properties = Properties()
            if (config.resourceApkFile != null) {
                properties.setProperty(ANDROID_RESOURCE_APK, config.resourceApkFile)
            }
            if (config.mergedResourcesDir != null) {
                properties.setProperty(ANDROID_MERGED_RESOURCES, config.mergedResourcesDir)
            }
            properties.setProperty(ANDROID_MERGED_ASSETS, config.mergedAssetsDir)
            properties.setProperty(ANDROID_MERGED_MANIFEST, config.mergedManifestDir)
            properties.setProperty(ANDROID_CUSTOM_PACKAGE, config.customPackage)

            // Write the properties to a String first so we can remove the line comment containing a
            // timestamp. We want to keep using the API provided by the Properties class to deal
            // with character encoding and escaping.
            val stringWriter = StringWriter()
            stringWriter.use {
                properties.store(it, COMMENT_GENERATED_BY_AGP)
            }
            val lines = stringWriter.toString().lines().filter { !it.isEmpty() }
            val linesWithoutTimestamp =
                lines.filter { !it.startsWith("#") || it.contains(COMMENT_GENERATED_BY_AGP) }

            val testConfigFile =
                outputDir.resolve(TEST_CONFIG_FILE.replace("/", outputDir.fileSystem.separator))
            Files.createDirectories(testConfigFile.parent)
            Files.write(testConfigFile, linesWithoutTimestamp)
        }
    }
}
