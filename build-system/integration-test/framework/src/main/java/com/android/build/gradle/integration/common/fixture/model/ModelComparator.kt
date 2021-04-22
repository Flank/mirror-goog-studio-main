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
import com.android.builder.model.v2.models.ModelVersions
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

    fun compare(
        model: ModelVersions,
        referenceModel: ModelVersions? = null,
        goldenFile: String
    ) {
        val content = snapshotModel(
            modelName = "ModelVersions",
            normalizer = result.normalizer,
            model = model,
            referenceModel = referenceModel,
            referenceNormalizer = referenceResult?.normalizer,
        ) {
            snapshotVersions()
        }.also {
            generateStdoutHeader()
            println(it)
        }

        runComparison("ModelVersions", content, goldenFile)
    }

    fun compare(
        model: AndroidProject,
        referenceModel: AndroidProject? = null,
        goldenFile: String
    ) {
        val content = snapshotModel(
            modelName = "AndroidProject",
            normalizer = result.normalizer,
            model = model,
            referenceModel = referenceModel,
            referenceNormalizer = referenceResult?.normalizer,
        ) {
            snapshotAndroidProject()
        }.also {
            generateStdoutHeader()
            println(it)
        }

        runComparison("AndroidProject", content, goldenFile)
    }

    fun ensureIsEmpty(
        model: AndroidProject,
        referenceModel: AndroidProject? = null,
    ) {
        checkEmptyDelta(
            modelName = "AndroidProject",
            normalizer = result.normalizer,
            model = model,
            referenceModel = referenceModel,
            referenceNormalizer = referenceResult?.normalizer,
            action = { snapshotAndroidProject() },
            failureAction =  {
                generateStdoutHeader()
                println(SnapshotItemWriter().write(it))

                fail("Expected no different between model. See stdout for details")
            }
        )
    }

    fun compare(
        model: AndroidDsl,
        referenceModel: AndroidDsl? = null,
        goldenFile: String
    ) {
        val content = snapshotModel(
            modelName = "AndroidDsl",
            normalizer = result.normalizer,
            model = model,
            referenceModel = referenceModel,
            referenceNormalizer = referenceResult?.normalizer,
        ) {
            snapshotAndroidDsl()
        }.also {
            generateStdoutHeader()
            println(it)
        }

        runComparison("AndroidDsl", content, goldenFile)
    }

    fun ensureIsEmpty(
        model: AndroidDsl,
        referenceModel: AndroidDsl? = null,
    ) {
        checkEmptyDelta(
            modelName = "AndroidDsl",
            normalizer = result.normalizer,
            model = model,
            referenceModel = referenceModel,
            referenceNormalizer = referenceResult?.normalizer,
            action = { snapshotAndroidDsl() },
            failureAction =  {
                generateStdoutHeader()
                println(SnapshotItemWriter().write(it))

                fail("Expected no different between model. See stdout for details")
            }
        )
    }

    fun compare(
        model: VariantDependencies,
        referenceModel: VariantDependencies? = null,
        goldenFile: String,
        dumpGlobalLibrary: Boolean = true
    ) {
        val content = snapshotModel(
            modelName = "VariantDependencies",
            normalizer = result.normalizer,
            model = model,
            referenceModel = referenceModel,
            referenceNormalizer = referenceResult?.normalizer,
            includedBuilds = result.container.infoMaps.keys.map { it.rootDir.absolutePath }.sorted()
        ) {
            snapshotVariantDependencies()
        }

        val finalString = if (dumpGlobalLibrary) {
            result.container.globalLibraryMap?.let { libraryMap ->
                content + snapshotModel(
                    modelName = "GlobalLibraryMap",
                    normalizer = result.normalizer,
                    model = libraryMap,
                    referenceModel = referenceResult?.container?.globalLibraryMap,
                    referenceNormalizer = referenceResult?.normalizer,
                    includedBuilds = result.container.infoMaps.keys.map { it.rootDir.absolutePath }.sorted()
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

    fun ensureIsEmpty(
        model: VariantDependencies,
        referenceModel: VariantDependencies? = null,
    ) {
        checkEmptyDelta(
            modelName = "VariantDependencies",
            normalizer = result.normalizer,
            model = model,
            referenceModel = referenceModel,
            referenceNormalizer = referenceResult?.normalizer,
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
    fun compare(
        model: GlobalLibraryMap,
        goldenFile: String
    ) {
        val content = snapshotModel(
            modelName = "GlobalLibraryMap",
            normalizer = result.normalizer,
            model = model,
            referenceModel = referenceResult?.container?.globalLibraryMap,
            referenceNormalizer = referenceResult?.normalizer,
            includedBuilds = result.container.infoMaps.keys.map { it.rootDir.absolutePath }.sorted()
        ) {
            snapshotGlobalLibraryMap()
        }.also {
            generateStdoutHeader()
            println(it)
        }

        runComparison("GlobalLibraryMap", content, goldenFile)
    }

    fun compare(
        model: NativeModule,
        referenceModel: NativeModule? = null,
        goldenFile: String
    ) {
        val content = snapshotModel(
            modelName = "NativeModule",
            normalizer = result.normalizer,
            model = model,
            referenceModel = referenceModel,
            referenceNormalizer = referenceResult?.normalizer,
            includedBuilds = result.container.infoMaps.keys.map { it.rootDir.absolutePath }.sorted()
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
