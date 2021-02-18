/*
 * Copyright (C) 2020 The Android Open Source Project
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
import com.android.builder.model.v2.ide.Variant
import com.android.builder.model.v2.models.AndroidProject
import com.android.builder.model.v2.models.GlobalLibraryMap
import com.android.builder.model.v2.models.VariantDependencies
import com.android.builder.model.v2.models.ndk.NativeModule
import com.google.common.base.Charsets
import com.google.common.io.Resources
import com.google.common.truth.Truth
import org.junit.Assert.fail
import java.io.File

/**
 * Compare models to golden files.
 * Also allows recreating the golden files when passing a special properties
 */
open class ModelComparator {

    fun <T> with(result: ModelBuilderV2.FetchResult<ModelContainerV2<T>>): Comparator<T> {
        return Comparator(this, result)
    }

    class Comparator<T>(
        private val testClass: ModelComparator,
        private val result: ModelBuilderV2.FetchResult<ModelContainerV2<T>>
    ) {
        fun compare(
            model: AndroidProject,
            goldenFile: String
        ) {
            val content = dump(AndroidProject::class.java, result.normalizer) {
                model.writeToBuilder(this)
            }.also {
                generateStdoutHeader()
                println(it)
            }

            runComparison("AndroidProject", content, goldenFile)
        }

        fun compare(
            model: VariantDependencies,
            goldenFile: String,
            dumpGlobalLibrary: Boolean = true
        ) {
            val content = dump(
                VariantDependencies::class.java,
                result.normalizer,
                result.container.infoMaps.keys.map { it.rootDir.absolutePath }.sorted()
            ) {
                model.writeToBuilder(this)
            }

            val finalString = if (dumpGlobalLibrary) {
                content + dump(
                    GlobalLibraryMap::class.java,
                    result.normalizer,
                    result.container.infoMaps.keys.map { it.rootDir.absolutePath }.sorted()
                ) {
                    result.container.globalLibraryMap?.writeToBuilder(this)
                }
            } else {
                content
            }

            generateStdoutHeader()
            println(finalString)

            runComparison("VariantDependencies", finalString, goldenFile)
        }

        /**
         * Entry point to dump a [GlobalLibraryMap]
         */
        fun compare(
            model: GlobalLibraryMap,
            goldenFile: String
        ) {
            val content = dump(
                GlobalLibraryMap::class.java,
                result.normalizer,
                result.container.infoMaps.keys.map { it.rootDir.absolutePath }.sorted()
            ) {
                model.writeToBuilder(this)
            }.also {
                generateStdoutHeader()
                println(it)
            }

            runComparison("GlobalLibraryMap", content, goldenFile)
        }

        fun compare(
            model: Variant,
            goldenFile: String
        ) {
            val content = dump(Variant::class.java, result.normalizer) {
                model.writeToBuilder(this)
            }.also {
                generateStdoutHeader()
                println(it)
            }

            runComparison("Variant", content, goldenFile)
        }

        fun compare(
            model: NativeModule,
            goldenFile: String
        ) {
            val content = dump(NativeModule::class.java, result.normalizer) {
                model.writeToBuilder(this)
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

            val fullPath = "$root${sep}src${sep}test${sep}resources${sep}${path}_$name.txt"

            val file = File(fullPath)
            if (!file.isFile) {
                fail("GoldenFile not found: $fullPath")
            }

            return file
        }

        private fun loadGoldenFile(name: String) = Resources.toString(
            Resources.getResource(
                testClass.javaClass,
                "${testClass.javaClass.simpleName}_${name}.txt"
            ), Charsets.UTF_8
        )
    }
}
