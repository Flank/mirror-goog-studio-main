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

import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.build.gradle.internal.fixtures.FakeGradleWorkExecutor
import com.android.build.gradle.internal.fixtures.FakeNoOpAnalyticsService
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.build.gradle.internal.transforms.NoOpMessageReceiver
import com.android.build.gradle.options.SyncOptions
import com.android.builder.dexing.ClassFileInputs
import com.android.builder.dexing.DexArchiveBuilder
import com.android.builder.dexing.DexMergerTool
import com.android.builder.dexing.DexParameters
import com.android.builder.dexing.DexingType
import com.android.builder.dexing.r8.ClassFileProviderFactory
import com.android.testutils.TestInputsGenerator
import com.android.testutils.truth.DexSubject.assertThatDex
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DexMergingTaskTest {

    @Rule
    @JvmField
    val tmp = TemporaryFolder()

    @Test
    fun testMonoDex() {
        val inputDirs = listOf(generateDexArchive("test/A", "test/B", "test/C"))
        val outputDir = tmp.newFolder()
        runDexMerging(inputDirs, outputDir, DexingType.MONO_DEX)

        assertThatDex(outputDir.resolve("classes.dex")).containsExactlyClassesIn(
                listOf("Ltest/A;", "Ltest/B;", "Ltest/C;")
        )
    }

    @Test
    fun testLegacyMultiDex() {
        val inputDirs = listOf(generateDexArchive("test/A", "test/B", "test/C"))
        val mainDexListFile = tmp.newFile().apply { writeText("test/A.class") }
        val outputDir = tmp.newFolder()
        runDexMerging(inputDirs, outputDir, DexingType.LEGACY_MULTIDEX, 19, mainDexListFile)

        assertThatDex(outputDir.resolve("classes.dex")).containsExactlyClassesIn(
                listOf("Ltest/A;")
        )
        assertThatDex(outputDir.resolve("classes2.dex")).containsExactlyClassesIn(
                listOf("Ltest/B;", "Ltest/C;")
        )
    }

    @Test
    fun testNativeMultiDex() {
        val numInputDirs = 5
        val inputDirs = (0 until numInputDirs).map {
            generateDexArchive("test/A$it")
        }
        val outputDir = tmp.newFolder()
        runDexMerging(inputDirs, outputDir, DexingType.NATIVE_MULTIDEX)

        assertThatDex(outputDir.resolve("classes.dex")).containsExactlyClassesIn(
                (0 until numInputDirs).map { "Ltest/A$it;" })
    }

    @Suppress("UnstableApiUsage")
    private fun runDexMerging(
            inputDirs: List<File>,
            outputDir: File,
            dexingType: DexingType,
            minSdkVersion: Int = 21,
            mainDexListFile: File? = null
    ) {
        val project = ProjectBuilder.builder().withProjectDir(tmp.newFolder()).build()
        object : DexMergingTaskDelegate() {
            override fun getParameters(): Params {
                return object : Params() {
                    override val sharedParams =
                            project.objects.property(DexMergingTask.SharedParams::class.java).also {
                                it.set(object : DexMergingTask.SharedParams() {
                                    override val dexingType = FakeGradleProperty(dexingType)
                                    override val dexMerger = FakeGradleProperty(DexMergerTool.D8)
                                    override val minSdkVersion = FakeGradleProperty(minSdkVersion)
                                    override val debuggable = FakeGradleProperty(true)
                                    override val errorFormatMode =
                                            FakeGradleProperty(SyncOptions.ErrorFormatMode.HUMAN_READABLE)
                                    override val mainDexListFile =
                                            FakeObjectFactory.factory.fileProperty()
                                                    .fileValue(mainDexListFile)
                                })
                            }
                    override val numberOfBuckets = FakeGradleProperty(1)
                    override val dexDirsOrJars =
                            FakeObjectFactory.factory.listProperty(File::class.java)
                                    .value(inputDirs)
                    override val outputDir =
                            FakeObjectFactory.factory.directoryProperty().fileValue(outputDir)
                    override val projectName = FakeGradleProperty("projectName")
                    override val taskOwner = FakeGradleProperty("taskOwner")
                    override val workerKey = FakeGradleProperty("workerKey")
                    override val analyticsService =
                            project.objects.property(AnalyticsService::class.java).also {
                                it.set(FakeNoOpAnalyticsService())
                            }
                }
            }

            override val workerExecutor = FakeGradleWorkExecutor(project.objects, tmp.newFolder())
        }.execute()
    }

    private fun generateDexArchive(vararg classes: String): File {
        val classesDir = tmp.newFolder().toPath().resolve("input")
        TestInputsGenerator.dirWithEmptyClasses(classesDir, classes.toList())
        val dexArchiveDir = tmp.newFolder()

        // now convert to dex archive
        val builder = DexArchiveBuilder.createD8DexBuilder(
                DexParameters(
                        minSdkVersion = 1,
                        debuggable = true,
                        dexPerClass = true,
                        withDesugaring = false,
                        desugarBootclasspath = ClassFileProviderFactory(emptyList()),
                        desugarClasspath = ClassFileProviderFactory(emptyList()),
                        coreLibDesugarConfig = null,
                        coreLibDesugarOutputKeepRuleFile = null,
                        messageReceiver = NoOpMessageReceiver()
                )
        )
        ClassFileInputs.fromPath(classesDir).use { input ->
            builder.convert(
                    input.entries { _, _ -> true },
                    dexArchiveDir.toPath()
            )
        }

        return dexArchiveDir
    }
}
