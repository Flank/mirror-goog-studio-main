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

import com.android.build.gradle.internal.fixtures.FakeConfigurableFileCollection
import com.android.build.gradle.internal.fixtures.FakeFileChange
import com.android.build.gradle.internal.fixtures.FakeFileCollection
import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.build.gradle.internal.fixtures.FakeGradleProvider
import com.android.build.gradle.internal.fixtures.FakeGradleWorkExecutor
import com.android.build.gradle.internal.fixtures.FakeInputChanges
import com.android.build.gradle.internal.fixtures.FakeNoOpAnalyticsService
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.build.gradle.internal.fixtures.FakeProviderFactory
import com.android.build.gradle.internal.fixtures.ProjectFactory
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.build.gradle.internal.transforms.NoOpMessageReceiver
import com.android.build.gradle.options.SyncOptions
import com.android.build.gradle.tasks.toSerializable
import com.android.builder.dexing.ClassFileInputs
import com.android.builder.dexing.DexArchiveBuilder
import com.android.builder.dexing.DexParameters
import com.android.builder.dexing.DexingType
import com.android.builder.dexing.r8.ClassFileProviderFactory
import com.android.testutils.TestInputsGenerator
import com.android.testutils.TestUtils
import com.android.testutils.truth.DexSubject.assertThatDex
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.truth.Truth.assertThat
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.work.ChangeType
import org.gradle.work.InputChanges
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DexMergingTaskTest {

    @Rule
    @JvmField
    val tmp = TemporaryFolder()

    private val factory=  ProjectFactory.project.objects

    @Test
    fun testMonoDex() {
        val inputDirs = listOf(generateDexArchive(listOf("test/A", "test/B", "test/C")))
        val outputDir = tmp.newFolder()

        runDexMerging(inputDirs, outputDir, dexingType = DexingType.MONO_DEX)

        assertThatDex(outputDir.resolve("classes.dex")).containsExactlyClassesIn(
                listOf("Ltest/A;", "Ltest/B;", "Ltest/C;")
        )
    }

    @Test
    fun testLegacyMultiDex() {
        val inputDirs = listOf(generateDexArchive(listOf("test/A", "test/B", "test/C")))
        val multidexProguardRulesFile = tmp.newFile().apply {
            writeText(" -keep class test.A")
        }
        val userMultidexKeepFile = tmp.newFile().apply {
            writeText("test/B.class")
        }
        val outputDir = tmp.newFolder()
        runDexMerging(
            inputDirs,
            outputDir,
            dexingType = DexingType.LEGACY_MULTIDEX,
            minSdkVersion = 19,
            multidexProguardRulesFile = multidexProguardRulesFile,
            libraryClasses = listOf(TestUtils.resolvePlatformPath("android.jar").toFile()),
            userMultidexKeepFile = userMultidexKeepFile
        )
        assertThatDex(outputDir.resolve("classes.dex")).containsExactlyClassesIn(
            listOf("Ltest/A;", "Ltest/B;")
        )
        assertThatDex(outputDir.resolve("classes2.dex")).containsExactlyClassesIn(
            listOf("Ltest/C;")
        )
    }

    @Test
    fun testNativeMultiDex() {
        val numInputDirs = 5
        val inputDirs = (0 until numInputDirs).map {
            generateDexArchive(listOf("test/A$it"))
        }
        val outputDir = tmp.newFolder()

        runDexMerging(inputDirs, outputDir, dexingType = DexingType.NATIVE_MULTIDEX)

        assertThatDex(outputDir.resolve("classes.dex")).containsExactlyClassesIn(
                (0 until numInputDirs).map { "Ltest/A$it;" })
    }

    @Test
    fun `test non-incremental build with buckets`() {
        val inputDirs = (0 until 2).map {
            generateDexArchive(listOf("package$it/Class0", "package$it/Class1"), outputToDir = true)
        }
        val inputJars = (2 until 4).map {
            generateDexArchive(listOf("package$it/Class0", "package$it/Class1"), outputToDir = false)
        }
        val outputDir = tmp.newFolder()

        runDexMerging(inputDirs + inputJars, outputDir, numberOfBuckets = 3)

        // Check that dex files are put into buckets
        assertThat(outputDir.listFiles()).hasLength(3)

        // Also check that classes of the same package are put in the same bucket/merged dex
        // file
        val bucket0 = outputDir.resolve("0/classes.dex")
        val bucket1 = outputDir.resolve("1/classes.dex")
        val bucket2 = outputDir.resolve("2/classes.dex")
        assertThat(bucket0).doesNotExist()
        assertThatDex(bucket1).containsExactlyClassesIn(
                listOf("Lpackage0/Class0;", "Lpackage0/Class1;", "Lpackage2/Class0;", "Lpackage2/Class1;"))
        assertThatDex(bucket2).containsExactlyClassesIn(
                listOf("Lpackage1/Class0;", "Lpackage1/Class1;", "Lpackage3/Class0;", "Lpackage3/Class1;"))
    }

    @Test
    fun `test incremental build with changed dex file`() {
        val inputDirs = (0 until 4).map {
            generateDexArchive(listOf("package$it/Class$it"))
        }
        val outputDir = tmp.newFolder()

        // Run full build
        runDexMerging(inputDirs, outputDir, numberOfBuckets = 3)

        val bucket0 = outputDir.resolve("0/classes.dex")
        val bucket1 = outputDir.resolve("1/classes.dex")
        val bucket2 = outputDir.resolve("2/classes.dex")
        assertThat(bucket0).doesNotExist()
        assertThatDex(bucket1).containsExactlyClassesIn(
                listOf("Lpackage0/Class0;", "Lpackage2/Class2;"))
        assertThatDex(bucket2).containsExactlyClassesIn(
                listOf("Lpackage1/Class1;", "Lpackage3/Class3;"))
        val bucket1Timestamp = bucket1.lastModified()
        val bucket2Timestamp = bucket2.lastModified()

        // Change a dex file
        val inputChanges = FakeInputChanges(
                incremental = true,
                inputChanges = listOf(
                        FakeFileChange(
                                file = inputDirs[0].resolve("package0/Class0.dex"),
                                normalizedPath = "package0/Class0.dex",
                                changeType = ChangeType.MODIFIED
                        )
                )
        )

        // Run incremental build
        TestUtils.waitForFileSystemTick()
        runDexMerging(inputDirs, outputDir, numberOfBuckets = 3, inputChanges = inputChanges)

        assertThat(bucket0).doesNotExist()
        assertThatDex(bucket1).containsExactlyClassesIn(
                listOf("Lpackage0/Class0;", "Lpackage2/Class2;"))
        assertThatDex(bucket2).containsExactlyClassesIn(
                listOf("Lpackage1/Class1;", "Lpackage3/Class3;"))

        // Check that only the impacted bucket(s) are re-merged
        assertThat(bucket1Timestamp).isLessThan(bucket1.lastModified())
        assertThat(bucket2Timestamp).isEqualTo(bucket2.lastModified())
    }

    @Test
    fun `test incremental build with added jar`() {
        val inputJars = (0 until 4).map {
            generateDexArchive(listOf("package$it/Class$it"), outputToDir = false)
        }
        val outputDir = tmp.newFolder()

        // Run full build
        runDexMerging(inputJars, outputDir, numberOfBuckets = 3)

        val bucket0 = outputDir.resolve("0/classes.dex")
        val bucket1 = outputDir.resolve("1/classes.dex")
        val bucket2 = outputDir.resolve("2/classes.dex")
        assertThat(bucket0).doesNotExist()
        assertThatDex(bucket1).containsExactlyClassesIn(
                listOf("Lpackage0/Class0;", "Lpackage2/Class2;"))
        assertThatDex(bucket2).containsExactlyClassesIn(
                listOf("Lpackage1/Class1;", "Lpackage3/Class3;"))
        val bucket1Timestamp = bucket1.lastModified()
        val bucket2Timestamp = bucket2.lastModified()

        // Add a jar
        val addedInputJar = generateDexArchive(listOf("package4/Class4"), outputToDir = false)
        val inputChanges = FakeInputChanges(
                incremental = true,
                inputChanges = listOf(
                        FakeFileChange(
                                file = addedInputJar,
                                normalizedPath = addedInputJar.name,
                                changeType = ChangeType.ADDED
                        )
                )
        )

        // Run incremental build
        TestUtils.waitForFileSystemTick()
        runDexMerging(inputJars + addedInputJar, outputDir, numberOfBuckets = 3, inputChanges = inputChanges)

        assertThat(bucket0).doesNotExist()
        assertThatDex(bucket1).containsExactlyClassesIn(
                listOf("Lpackage0/Class0;", "Lpackage2/Class2;", "Lpackage4/Class4;"))
        assertThatDex(bucket2).containsExactlyClassesIn(
                listOf("Lpackage1/Class1;", "Lpackage3/Class3;"))

        // Check that only the impacted bucket(s) are re-merged
        assertThat(bucket1Timestamp).isLessThan(bucket1.lastModified())
        assertThat(bucket2Timestamp).isEqualTo(bucket2.lastModified())
    }

    @Test
    fun `test incremental build with modified jar`() {
        val inputJars = (0 until 4).map {
            generateDexArchive(listOf("package$it/Class$it"), outputToDir = false)
        }
        val outputDir = tmp.newFolder()

        // Run full build
        runDexMerging(inputJars, outputDir, numberOfBuckets = 3)

        val bucket0 = outputDir.resolve("0/classes.dex")
        val bucket1 = outputDir.resolve("1/classes.dex")
        val bucket2 = outputDir.resolve("2/classes.dex")
        assertThat(bucket0).doesNotExist()
        assertThatDex(bucket1).containsExactlyClassesIn(
                listOf("Lpackage0/Class0;", "Lpackage2/Class2;"))
        assertThatDex(bucket2).containsExactlyClassesIn(
                listOf("Lpackage1/Class1;", "Lpackage3/Class3;"))
        val bucket1Timestamp = bucket1.lastModified()
        val bucket2Timestamp = bucket2.lastModified()

        // Modify a jar
        val inputChanges = FakeInputChanges(
                incremental = true,
                inputChanges = listOf(
                        FakeFileChange(
                                file = inputJars[0],
                                normalizedPath = inputJars[0].name,
                                changeType = ChangeType.MODIFIED
                        )
                )
        )

        // Run incremental build
        TestUtils.waitForFileSystemTick()
        runDexMerging(inputJars, outputDir, numberOfBuckets = 3, inputChanges = inputChanges)

        assertThat(bucket0).doesNotExist()
        assertThatDex(bucket1).containsExactlyClassesIn(
                listOf("Lpackage0/Class0;", "Lpackage2/Class2;"))
        assertThatDex(bucket2).containsExactlyClassesIn(
                listOf("Lpackage1/Class1;", "Lpackage3/Class3;"))

        // Check that all the buckets are re-merged
        assertThat(bucket1Timestamp).isLessThan(bucket1.lastModified())
        assertThat(bucket2Timestamp).isLessThan(bucket2.lastModified())
    }

    @Test
    fun `test incremental build with removed jar`() {
        val inputJars = (0 until 4).map {
            generateDexArchive(listOf("package$it/Class$it"), outputToDir = false)
        }
        val outputDir = tmp.newFolder()

        // Run full build
        runDexMerging(inputJars, outputDir, numberOfBuckets = 3)

        val bucket0 = outputDir.resolve("0/classes.dex")
        val bucket1 = outputDir.resolve("1/classes.dex")
        val bucket2 = outputDir.resolve("2/classes.dex")
        assertThat(bucket0).doesNotExist()
        assertThatDex(bucket1).containsExactlyClassesIn(
                listOf("Lpackage0/Class0;", "Lpackage2/Class2;"))
        assertThatDex(bucket2).containsExactlyClassesIn(
                listOf("Lpackage1/Class1;", "Lpackage3/Class3;"))
        val bucket1Timestamp = bucket1.lastModified()
        val bucket2Timestamp = bucket2.lastModified()

        // Remove a jar
        val inputChanges = FakeInputChanges(
                incremental = true,
                inputChanges = listOf(
                        FakeFileChange(
                                file = inputJars[0],
                                normalizedPath = inputJars[0].name,
                                changeType = ChangeType.REMOVED
                        )
                )
        )

        // Run incremental build
        TestUtils.waitForFileSystemTick()
        runDexMerging(inputJars.drop(1), outputDir, numberOfBuckets = 3, inputChanges = inputChanges)

        assertThat(bucket0).doesNotExist()
        assertThatDex(bucket1).containsExactlyClassesIn(
                listOf("Lpackage2/Class2;"))
        assertThatDex(bucket2).containsExactlyClassesIn(
                listOf("Lpackage1/Class1;", "Lpackage3/Class3;"))

        // Check that all the buckets are re-merged
        assertThat(bucket1Timestamp).isLessThan(bucket1.lastModified())
        assertThat(bucket2Timestamp).isLessThan(bucket2.lastModified())
    }

    @Suppress("UnstableApiUsage")
    private fun runDexMerging(
            inputDirsOrJars: List<File>,
            outputDir: File,
            dexingType: DexingType = DexingType.NATIVE_MULTIDEX,
            minSdkVersion: Int = 21,
            userMultidexKeepFile: File? = null,
            numberOfBuckets: Int = 1,
            inputChanges: InputChanges = FakeInputChanges(),
            libraryClasses: List<File> = listOf(),
            multidexProguardRulesFile: File? = null,
            mainDexListOutput: File? = null
    ) {
        val project = ProjectBuilder.builder().withProjectDir(tmp.newFolder()).build()
        object : DexMergingTaskDelegate() {
            override fun getParameters(): Params {
                return object : Params() {
                    override val sharedParams =
                            project.objects.property(DexMergingTask.SharedParams::class.java).also {
                                it.set(object : DexMergingTask.SharedParams() {
                                    override val dexingType = FakeGradleProperty(dexingType)
                                    override val minSdkVersion = FakeGradleProperty(minSdkVersion)
                                    override val debuggable = FakeGradleProperty(true)
                                    override val errorFormatMode =
                                            FakeGradleProperty(SyncOptions.ErrorFormatMode.HUMAN_READABLE)
                                    override val mainDexListConfig: MainDexListConfig
                                        get() = object : MainDexListConfig() {
                                            override val userMultidexKeepFile =
                                                    FakeGradleProperty(userMultidexKeepFile)
                                            override val aaptGeneratedRules =
                                                    FakeObjectFactory.factory.fileProperty()
                                            override val userMultidexProguardRules =
                                                    FakeObjectFactory.factory.listProperty(RegularFile::class.java).also { listProperty ->
                                                        multidexProguardRulesFile?.let { file ->
                                                            listProperty.add(FakeObjectFactory.factory.fileProperty()
                                                                    .fileValue(file))
                                                        }
                                                    }
                                            override val libraryClasses =
                                                    FakeConfigurableFileCollection(libraryClasses)
                                            override val platformMultidexProguardRules =
                                                FakeObjectFactory.factory.listProperty(String::class.java)
                                        }
                                })
                            }
                    override val numberOfBuckets = FakeGradleProperty(numberOfBuckets)
                    override val dexDirsOrJars =
                            FakeObjectFactory.factory.listProperty(File::class.java)
                                    .value(inputDirsOrJars)
                    override val outputDir =
                            FakeObjectFactory.factory.directoryProperty().fileValue(outputDir)
                    override val mainDexListOutput =
                        FakeObjectFactory.factory.fileProperty().fileValue(mainDexListOutput)
                    override val incremental = FakeGradleProperty(inputChanges.isIncremental)
                    override val fileChanges = FakeGradleProperty(
                            inputChanges.getFileChanges(
                                    FakeFileCollection(dexDirsOrJars.get())).toSerializable()
                    )
                    override val projectPath = factory.property(String::class.java).value("projectName")
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

    private fun generateDexArchive(emptyClassNames: List<String>, outputToDir: Boolean = true)
            : File {
        val dexArchivePath = if (outputToDir) {
            tmp.newFolder()
        } else {
            tmp.newFolder().resolve("file.jar")
        }
        generateDexArchive(emptyClassNames, dexArchivePath)
        return dexArchivePath
    }

    private fun generateDexArchive(emptyClassNames: List<String>, dexArchivePath: File) {
        val emptyClassesDir = tmp.newFolder().also {
            TestInputsGenerator.dirWithEmptyClasses(it.toPath(), emptyClassNames)
        }
        generateDexArchive(emptyClassesDir, dexArchivePath)
    }

    private fun generateDexArchive(classesDirOrJar: File, dexArchivePath: File) {
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
        ClassFileInputs.fromPath(classesDirOrJar.toPath()).use { input ->
            builder.convert(
                    input.entries { _, _ -> true },
                    dexArchivePath.toPath()
            )
        }
    }
}
