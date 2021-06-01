/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.integration.common.fixture.model

import com.android.build.gradle.integration.common.fixture.ModelBuilderV2
import com.android.build.gradle.integration.common.fixture.ModelContainerV2
import com.android.builder.model.v2.models.AndroidDsl
import com.android.builder.model.v2.models.AndroidProject
import com.android.builder.model.v2.models.GlobalLibraryMap
import com.android.builder.model.v2.models.Versions
import com.android.builder.model.v2.models.VariantDependencies
import com.android.builder.model.v2.models.ndk.NativeModule
import com.android.utils.FileUtils
import com.google.common.base.Charsets
import com.google.common.io.Resources
import com.google.common.truth.Truth
import junit.framework.Assert.fail
import java.io.File

/**
 * Base interface for classes using [Comparator]
 */
interface BaseModelComparator

/**
 * Compare models to golden files.
 * Also allows recreating the golden files when passing a special properties
 *
 * This is meant to be used as a base class for tests running sync.
 */
open class ModelComparator: BaseModelComparator {

    open fun with(
        result: ModelBuilderV2.FetchResult<ModelContainerV2>,
        referenceResult: ModelBuilderV2.FetchResult<ModelContainerV2>? = null): Comparator {
        return Comparator(this, result, referenceResult)
    }
}

class Comparator(
    private val testClass: BaseModelComparator,
    private val result: ModelBuilderV2.FetchResult<ModelContainerV2>,
    private val referenceResult: ModelBuilderV2.FetchResult<ModelContainerV2>?
) {

    fun compareVersions(
        modelAction: ModelBuilderV2.FetchResult<ModelContainerV2>.() -> Versions,
        goldenFile: String
    ) {
        val content = snapshotModel(
            modelName = "Versions",
            modelAction = modelAction,
            project = result,
            referenceProject = referenceResult
        ) {
            snapshotVersions()
        }.also {
            generateStdoutHeader()
            println(it)
        }

        runComparison("ModelVersions", content, goldenFile)
    }

    fun compareAndroidProject(
        modelAction: ModelBuilderV2.FetchResult<ModelContainerV2>.() -> AndroidProject,
        goldenFile: String
    ) {
        val content = snapshotModel(
            modelName = "AndroidProject",
            modelAction = modelAction,
            project = result,
            referenceProject = referenceResult
        ) {
            snapshotAndroidProject()
        }.also {
            generateStdoutHeader()
            println(it)
        }

        runComparison("AndroidProject", content, goldenFile)
    }

    fun ensureAndroidProjectIsEmpty(
        modelAction: ModelBuilderV2.FetchResult<ModelContainerV2>.() -> AndroidProject,
    ) {
        checkEmptyDelta(
            modelName = "AndroidProject",
            modelAction = modelAction,
            project = result,
            referenceProject = referenceResult!!,
            action = { snapshotAndroidProject() },
            failureAction =  {
                generateStdoutHeader()
                println(SnapshotItemWriter().write(it))

                fail("Expected no different between model. See stdout for details")
            }
        )
    }

    fun compareAndroidDsl(
        modelAction: ModelBuilderV2.FetchResult<ModelContainerV2>.() -> AndroidDsl,
        goldenFile: String
    ) {
        val content = snapshotModel(
            modelName = "AndroidDsl",
            modelAction = modelAction,
            project = result,
            referenceProject = referenceResult
        ) {
            snapshotAndroidDsl()
        }.also {
            generateStdoutHeader()
            println(it)
        }

        runComparison("AndroidDsl", content, goldenFile)
    }

    fun ensureAndroidDslIsEmpty(
        modelAction: ModelBuilderV2.FetchResult<ModelContainerV2>.() -> AndroidDsl,
    ) {
        checkEmptyDelta(
            modelName = "AndroidDsl",
            modelAction = modelAction,
            project = result,
            referenceProject = referenceResult!!,
            action = { snapshotAndroidDsl() },
            failureAction =  {
                generateStdoutHeader()
                println(SnapshotItemWriter().write(it))

                fail("Expected no different between model. See stdout for details")
            }
        )
    }

    fun compareVariantDependencies(
        modelAction: ModelBuilderV2.FetchResult<ModelContainerV2>.() -> VariantDependencies,
        goldenFile: String,
        dumpGlobalLibrary: Boolean = true
    ) {
        val content = snapshotModel(
            modelName = "VariantDependencies",
            modelAction = modelAction,
            project = result,
            referenceProject = referenceResult
        ) {
            snapshotVariantDependencies()
        }

        val finalString = if (dumpGlobalLibrary) {
            result.container.globalLibraryMap?.let { libraryMap ->
                content + snapshotModel(
                    modelName = "GlobalLibraryMap",
                    modelAction = {  container.globalLibraryMap!! },
                    project = result,
                    referenceProject = referenceResult
                ) {
                    snapshotGlobalLibraryMap()
                }
            } ?: content
        } else {
            content
        }

        generateStdoutHeader()
        println(finalString)

        runComparison("VariantDependencies", finalString, goldenFile)
    }

    fun ensureVariantDependenciesIsEmpty(
        modelAction: ModelBuilderV2.FetchResult<ModelContainerV2>.() -> VariantDependencies,
    ) {
        checkEmptyDelta(
            modelName = "VariantDependencies",
            modelAction = modelAction,
            project = result,
            referenceProject = referenceResult!!,
            action = { snapshotVariantDependencies() },
            failureAction =  {
                generateStdoutHeader()
                println(SnapshotItemWriter().write(it))

                fail("Expected no different between model. See stdout for details")
            }
        )
    }

    /**
     * Entry point to dump a [GlobalLibraryMap]
     */
    fun compareGlobalLibraryMap(
        modelAction: ModelBuilderV2.FetchResult<ModelContainerV2>.() -> GlobalLibraryMap,
        goldenFile: String
    ) {
        val content = snapshotModel(
            modelName = "GlobalLibraryMap",
            modelAction = modelAction,
            project = result,
            referenceProject = referenceResult
        ) {
            snapshotGlobalLibraryMap()
        }.also {
            generateStdoutHeader()
            println(it)
        }

        runComparison("GlobalLibraryMap", content, goldenFile)
    }

    fun compareNativeModule(
        modelAction: ModelBuilderV2.FetchResult<ModelContainerV2>.() -> NativeModule,
        goldenFile: String
    ) {
        val content = snapshotModel(
            modelName = "NativeModule",
            modelAction = modelAction,
            project = result,
            referenceProject = referenceResult
        ) {
            snapshotNativeModule()
        }.also {
            generateStdoutHeader()
            println(it)
        }

        runComparison("NativeModule", content, goldenFile)
    }

    /**
     * Runs the comparison
     *
     * @param name a display name for the dump model class
     * @param actualContent the result of the model dump
     * @param goldenFile the test specific portion of the golden file name.
     */
    private fun runComparison(
        name: String,
        actualContent: String,
        goldenFile: String
    ) {
        if (System.getenv("GENERATE_MODEL_GOLDEN_FILES").isNullOrEmpty()) {
            Truth.assertWithMessage("Dumped $name (full version in stdout)")
                .that(actualContent)
                .isEqualTo(loadGoldenFile(goldenFile))
        } else {
            val file = findGoldenFileLocation(goldenFile)
            file.writeText(actualContent)
        }
    }

    private fun generateStdoutHeader() {
        println("--------------------------------------------------")
        println("To regenerate the golden files use:")
        println("$ GENERATE_MODEL_GOLDEN_FILES=true ./gradlew ...")
        println("--------------------------------------------------")
        println(result.normalizer)
        println("--------------------------------------------------")
    }

    private fun findGoldenFileLocation(name: String): File {
        val sep = File.separatorChar
        val path = testClass.javaClass.name.replace('.', sep)
        val root = System.getenv("PROJECT_ROOT")

        val fullPath = if (name.isNotBlank()) {
            "$root${sep}src${sep}test${sep}resources${sep}${path}_$name.txt"
        } else {
            "$root${sep}src${sep}test${sep}resources${sep}${path}.txt"
        }

        return File(fullPath).also {
            FileUtils.mkdirs(it.parentFile)
        }
    }

    private fun loadGoldenFile(name: String): String? {
        val resourceName = if (name.isNotBlank()) {
            "${testClass.javaClass.simpleName}_${name}.txt"
        } else {
            "${testClass.javaClass.simpleName}.txt"
        }
        return Resources.toString(
            Resources.getResource(testClass.javaClass, resourceName
            ), Charsets.UTF_8
        )
    }
}
