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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.transforms.NoOpMessageReceiver
import com.android.build.gradle.options.SyncOptions
import com.android.builder.dexing.ClassFileInputs
import com.android.builder.dexing.DexArchiveBuilder
import com.android.builder.dexing.DexMergerTool
import com.android.builder.dexing.DexingType
import com.android.builder.dexing.r8.ClassFileProviderFactory
import com.android.testutils.TestInputsGenerator
import com.android.testutils.truth.MoreTruth.assertThatDex
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.collect.ImmutableSet
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Path

class DexMergingTaskTest {

    @Rule
    @JvmField
    val tmp = TemporaryFolder()

    @Test
    fun testMonoDexSingleClass() {
        val dexFiles = generateArchive("test/A")

        val output = tmp.newFolder()
        DexMergingTaskRunnable(
            DexMergingParams(
                DexingType.MONO_DEX,
                SyncOptions.ErrorFormatMode.HUMAN_READABLE,
                DexMergerTool.D8,
                21,
                true,
                0,
                null,
                ImmutableSet.of(dexFiles),
                null,
                output
            )
        ).run()
        assertThatDex(output.resolve("classes.dex")).containsExactlyClassesIn(listOf("Ltest/A;"))
    }

    @Test
    fun testMonoDexMultipleClasses() {
        val dexFiles = generateArchive("test/A", "test/B", "test/C")

        val output = tmp.newFolder()
        DexMergingTaskRunnable(
            DexMergingParams(
                DexingType.MONO_DEX,
                SyncOptions.ErrorFormatMode.HUMAN_READABLE,
                DexMergerTool.D8,
                21,
                true,
                0,
                null,
                ImmutableSet.of(dexFiles),
                null,
                output
            )
        ).run()
        assertThatDex(output.resolve("classes.dex")).containsExactlyClassesIn(
            listOf(
                "Ltest/A;",
                "Ltest/B;",
                "Ltest/C;"
            )
        )
    }

    @Test
    fun testLegacyMultiDex() {
        val dexFiles = generateArchive("test/A", "test/B", "test/C")

        val mainDexList = tmp.newFile()
        mainDexList.writeText("test/A.class")

        val output = tmp.newFolder()
        DexMergingTaskRunnable(
            DexMergingParams(
                DexingType.LEGACY_MULTIDEX,
                SyncOptions.ErrorFormatMode.HUMAN_READABLE,
                DexMergerTool.D8,
                19,
                true,
                0,
                mainDexList,
                ImmutableSet.of(dexFiles),
                null,
                output
            )
        ).run()
        assertThatDex(output.resolve("classes.dex")).containsExactlyClassesIn(listOf("Ltest/A;"))
        assertThatDex(output.resolve("classes2.dex")).containsExactlyClassesIn(
            listOf(
                "Ltest/B;",
                "Ltest/C;"
            )
        )
    }

    @Test
    fun testNativeMultiDexWithThreshold() {
        val numInputs = 5
        val inputFiles = (0 until numInputs).map {
            tmp.newFile("input_$it")
        }

        val output = tmp.newFolder()
        DexMergingTaskRunnable(
            DexMergingParams(
                DexingType.NATIVE_MULTIDEX,
                SyncOptions.ErrorFormatMode.HUMAN_READABLE,
                DexMergerTool.D8,
                21,
                true,
                numInputs + 1,
                null,
                inputFiles.toSet(),
                null,
                output
            )
        ).run()

        (0 until numInputs).forEach {
            assertThat(output.resolve("classes_$it.dex")).exists()
        }
    }

    @Test
    fun testNativeMultiDexWithThresholdToMerge() {
        val numInputs = 5
        val inputFiles = (0 until numInputs).map {
            generateArchive("test/A$it")
        }

        val output = tmp.newFolder()
        DexMergingTaskRunnable(
            DexMergingParams(
                DexingType.NATIVE_MULTIDEX,
                SyncOptions.ErrorFormatMode.HUMAN_READABLE,
                DexMergerTool.D8,
                21,
                true,
                numInputs,
                null,
                inputFiles.toSet(),
                null,
                output
            )
        ).run()

        assertThatDex(output.resolve("classes.dex")).containsExactlyClassesIn(
            (0 until numInputs).map { "Ltest/A$it;" })
    }

    /**
     * Regression test for b/132840182. When number of top-level inputs is below threshold, but
     * number of dex files that we would produce is higher than threshold, we should merge all.
     */
    @Test
    fun testNativeMultiDexThresholdMustNotCopy() {
        val numInputs = 5
        val inputFiles = (0 until numInputs).map {
            generateArchive("test/A$it", "test/B$it")
        }

        val output = tmp.newFolder()
        DexMergingTaskRunnable(
            DexMergingParams(
                DexingType.NATIVE_MULTIDEX,
                SyncOptions.ErrorFormatMode.HUMAN_READABLE,
                DexMergerTool.D8,
                21,
                true,
                numInputs + 1,
                null,
                inputFiles.toSet(),
                null,
                output
            )
        ).run()

        assertThatDex(output.resolve("classes.dex")).containsExactlyClassesIn(
            (0 until numInputs).flatMap { listOf("Ltest/A$it;", "Ltest/B$it;") }
        )
        assertThat(output.resolve("classes2.dex")).doesNotExist()
    }

    @Test
    fun testFileCollectionOrdering() {
        val directoryB = tmp.newFolder("b").let { rootDir ->
            generateArchive(tmp, rootDir.toPath(), listOf("test/B"))
            rootDir.resolve("test2").also {
                it.mkdirs()
                generateArchive(tmp, it.toPath(), listOf("test/B2"))
            }
            rootDir.resolve("test1").also {
                it.mkdirs()
                generateArchive(tmp, it.toPath(), listOf("test/B12", "test/B11"))
            }
            rootDir
        }
        val directoryA = tmp.newFolder("a").let { rootDir ->
            generateArchive(tmp, rootDir.toPath(), listOf("test/A2", "test/A1"))
            rootDir
        }
        val inputFiles = listOf(directoryB, directoryA)

        val output = tmp.newFolder()
        DexMergingTaskRunnable(
            DexMergingParams(
                DexingType.NATIVE_MULTIDEX,
                SyncOptions.ErrorFormatMode.HUMAN_READABLE,
                DexMergerTool.D8,
                21,
                true,
                Int.MAX_VALUE,
                null,
                inputFiles.toSet(),
                null,
                output
            )
        ).run()

        // Ordering within file collection should not change, but entries inside directories should
        // be sorted.
        assertThatDex(output.resolve("classes_0.dex")).containsExactlyClassesIn(listOf("Ltest/B;"))
        assertThatDex(output.resolve("classes_1.dex"))
            .containsExactlyClassesIn(listOf("Ltest/B11;"))
        assertThatDex(output.resolve("classes_2.dex"))
            .containsExactlyClassesIn(listOf("Ltest/B12;"))
        assertThatDex(output.resolve("classes_3.dex")).containsExactlyClassesIn(listOf("Ltest/B2;"))
        assertThatDex(output.resolve("classes_4.dex")).containsExactlyClassesIn(listOf("Ltest/A1;"))
        assertThatDex(output.resolve("classes_5.dex")).containsExactlyClassesIn(listOf("Ltest/A2;"))
    }

    private fun generateArchive(vararg classes: String): File {
        val dexArchivePath = tmp.newFolder()
        generateArchive(tmp, dexArchivePath.toPath(), classes.toList())
        return dexArchivePath
    }
}

fun generateArchive(tmp: TemporaryFolder, output: Path, classes: Collection<String>) {
    val classesInput = tmp.newFolder().toPath().resolve("input")
    TestInputsGenerator.dirWithEmptyClasses(classesInput, classes.toList())

    // now convert to dex archive
    val builder = DexArchiveBuilder.createD8DexBuilder(
        1,
        true,
        ClassFileProviderFactory(emptyList()),
        ClassFileProviderFactory(emptyList()),
        false,
        null,
        NoOpMessageReceiver()
    )

    ClassFileInputs.fromPath(classesInput)
        .use { input ->
            builder.convert(
                input.entries { p -> true },
                output,
                true
            )
        }
}