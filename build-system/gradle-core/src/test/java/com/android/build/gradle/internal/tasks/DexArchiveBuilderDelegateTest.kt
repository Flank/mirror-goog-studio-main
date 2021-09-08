/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.build.gradle.internal.dexing.DexParameters
import com.android.build.gradle.internal.fixtures.FakeFileChange
import com.android.build.gradle.internal.fixtures.FakeGradleWorkExecutor
import com.android.build.gradle.internal.fixtures.FakeNoOpAnalyticsService
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.build.gradle.internal.fixtures.FakeProviderFactory
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.options.SyncOptions
import com.android.testutils.TestClassesGenerator
import com.android.testutils.TestInputsGenerator.dirWithEmptyClasses
import com.android.testutils.TestInputsGenerator.jarWithEmptyClasses
import com.android.testutils.TestUtils
import com.android.testutils.apk.Dex
import com.android.testutils.truth.DexSubject.assertThat
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.FileUtils
import com.google.common.collect.ImmutableList
import com.google.common.collect.Iterables
import com.google.common.io.ByteStreams
import com.google.common.truth.Truth.assertThat
import org.gradle.api.file.FileType
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.work.ChangeType
import org.gradle.work.FileChange
import org.gradle.workers.WorkerExecutor
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runners.Parameterized
import java.io.File
import java.io.ObjectOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.fail

/** Testing the [DexArchiveBuilderTaskDelegate].  */
class DexArchiveBuilderDelegateTest {

    private lateinit var out: Path

    @JvmField
    @Rule
    var tmpDir = TemporaryFolder()

    private lateinit var workerExecutor: WorkerExecutor

    @Before
    fun setUp() {
        out = tmpDir.root.toPath().resolve("out")

        with(ProjectBuilder.builder().withProjectDir(tmpDir.newFolder()).build()) {
            workerExecutor = FakeGradleWorkExecutor(objects, tmpDir.newFolder())
        }

        Files.createDirectories(out)
    }

    @Test
    fun testInitialBuild() {
        val dirInput = tmpDir.root.toPath().resolve("dir_input")
        dirWithEmptyClasses(dirInput, ImmutableList.of("$PACKAGE/A"))

        val jarInput = tmpDir.root.toPath().resolve("input.jar")
        jarWithEmptyClasses(jarInput, ImmutableList.of("$PACKAGE/B"))

        getDelegate(
            projectClasses = setOf(dirInput.toFile(), jarInput.toFile()),
            projectOutput = out.toFile()
        ).doProcess()

        assertThat(FileUtils.find(out.toFile(), Pattern.compile(".*\\.dex"))).hasSize(1)
        val jarDexArchives = FileUtils.find(out.toFile(), Pattern.compile(".*\\.jar"))
        assertThat(jarDexArchives).hasSize(1)
    }

    @Test
    fun testEntryRemovedFromTheArchive() {
        val inputDir = tmpDir.root.toPath().resolve("dir_input")
        val inputJar = tmpDir.root.toPath().resolve("input.jar")

        dirWithEmptyClasses(inputDir, ImmutableList.of("$PACKAGE/A", "$PACKAGE/B"))
        jarWithEmptyClasses(inputJar, ImmutableList.of("$PACKAGE/C"))

        val inputFileHashes = tmpDir.newFile()
        getDelegate(
            projectClasses = setOf(inputDir.toFile(), inputJar.toFile()),
            projectOutput = out.toFile(),
            inputJarHashesFile = inputFileHashes
        ).doProcess()

        assertThat(FileUtils.find(out.toFile(), "B.dex").orNull()).isFile()

        val toDelete = inputDir.resolve("$PACKAGE/B.class").toFile()
        assertThat(toDelete.delete()).isTrue()

        val change = FakeFileChange(
            toDelete, ChangeType.REMOVED, FileType.FILE, "$PACKAGE/B.class"
        )
        getDelegate(
            projectClasses = setOf(inputDir.toFile(), inputJar.toFile()),
            isIncremental = true,
            projectChanges = setOf(change),
            projectOutput = out.toFile(),
            inputJarHashesFile = inputFileHashes
        ).doProcess()

        assertThat(FileUtils.find(out.toFile(), "B.dex").orNull()).isNull()
        assertThat(FileUtils.find(out.toFile(), "A.dex").orNull()).isFile()
    }

    @Test
    fun testNonIncremental() {
        val inputDir = tmpDir.root.toPath().resolve("dir_input")
        dirWithEmptyClasses(inputDir, ImmutableList.of("$PACKAGE/A"))

        val inputJar = tmpDir.root.toPath().resolve("input.jar")
        jarWithEmptyClasses(inputJar, ImmutableList.of("$PACKAGE/B"))

        getDelegate(
            projectClasses = setOf(inputDir.toFile(), inputJar.toFile()),
            projectOutput = out.toFile()
        ).doProcess()

        val inputDir2 = tmpDir.root.toPath().resolve("dir_2_input")
        dirWithEmptyClasses(inputDir2, ImmutableList.of("$PACKAGE/C"))

        getDelegate(
            projectClasses = setOf(inputDir2.toFile(), inputJar.toFile()),
            projectOutput = out.toFile()
        ).doProcess()

        assertThat(FileUtils.find(out.toFile(), "A.dex").orNull()).isNull()
    }

    @Test
    fun testIncrementalUnchangedDirInput() {
        val input = tmpDir.newFolder("classes").toPath()
        dirWithEmptyClasses(input, ImmutableList.of("test/A", "test/B"))

        val inputJarHashesFile = tmpDir.newFile().also {
            writeEmptyInputJarHashes(it)
        }
        getDelegate(
            isIncremental = true,
            projectClasses = setOf(input.toFile()),
            projectOutput = out.toFile(),
            inputJarHashesFile = inputJarHashesFile
        ).doProcess()

        assertThat(FileUtils.getAllFiles(out.toFile())).isEmpty()
    }

    /** Regression test for b/65241720.  */
    @Test
    fun testDirectoryRemovedInIncrementalBuild() {
        val input = tmpDir.root.toPath().resolve("classes")
        val nestedDir = input.resolve("nested_dir")
        Files.createDirectories(nestedDir)
        val nestedDirOutput = out.resolve("nested_dir")
        Files.createDirectories(nestedDirOutput)

        val inputJarHashesFile = tmpDir.newFile().also {
            writeEmptyInputJarHashes(it)
        }
        getDelegate(
            isIncremental = true,
            projectClasses = setOf(input.toFile()),
            projectOutput = out.toFile(),
            projectChanges = setOf(FakeFileChange(
                nestedDir.toFile(),
                ChangeType.REMOVED,
                FileType.DIRECTORY,
                "nested_dir"
            )),
            inputJarHashesFile = inputJarHashesFile
        ).doProcess()

        assertThat(nestedDirOutput).doesNotExist()
    }

    @Test
    fun testMultiReleaseJar() {
        val input = tmpDir.root.toPath().resolve("classes.jar")
        ZipOutputStream(Files.newOutputStream(input)).use { stream ->
            stream.putNextEntry(ZipEntry("test/A.class"))
            stream.write(TestClassesGenerator.emptyClass("test", "A"))
            stream.closeEntry()
            stream.putNextEntry(ZipEntry("module-info.class"))
            stream.write(byteArrayOf(0x1))
            stream.closeEntry()
            stream.putNextEntry(ZipEntry("META-INF/9/test/B.class"))
            stream.write(TestClassesGenerator.emptyClass("test", "B"))
            stream.closeEntry()
            stream.putNextEntry(ZipEntry("/META-INF/9/test/C.class"))
            stream.write(TestClassesGenerator.emptyClass("test", "C"))
            stream.closeEntry()
        }

        getDelegate(
            projectClasses = setOf(input.toFile()),
            projectOutput = out.toFile()
        ).doProcess()

        // verify output contains only test/A
        val jarWithDex = Iterables.getOnlyElement(FileUtils.getAllFiles(out.toFile()))
        ZipFile(jarWithDex).use { zipFile ->
            assertThat(zipFile.size()).isEqualTo(1)
            val inputStream = zipFile.getInputStream(zipFile.entries().nextElement())

            val dex = Dex(ByteStreams.toByteArray(inputStream), "unknown")
            assertThat(dex).containsExactlyClassesIn(ImmutableList.of("Ltest/A;"))
        }
    }

    @Test
    fun testMultiReleaseDir() {
        val input = tmpDir.root.resolve("classes").also {
            it.resolve("test").mkdirs()
            it.resolve("test/A.class").writeBytes(TestClassesGenerator.emptyClass("test", "A"))
            it.resolve("META-INF/9/test/").mkdirs()
            it.resolve("META-INF/9/test/C.class").writeBytes(TestClassesGenerator.emptyClass("test", "C"))
            it.resolve("module-info.class").writeBytes("should be ignored".toByteArray())
        }

        val inputJarHashes = tmpDir.newFile()
        getDelegate(
                projectClasses = setOf(input),
                projectOutput = out.toFile(),
                inputJarHashesFile = inputJarHashes
        ).doProcess()

        assertThat(FileUtils.find(out.toFile(), "A.dex").orNull()).isFile()
        assertThat(FileUtils.find(out.toFile(), "C.dex").orNull()).isNull()

        // Remove module-info.class, the build should succeed (regression test for bug 164036336)
        val moduleInfoClass = input.resolve("module-info.class")
        FileUtils.delete(moduleInfoClass)
        @Suppress("UnstableApiUsage")
        getDelegate(
                isIncremental = true,
                projectClasses = setOf(input),
                projectOutput = out.toFile(),
                projectChanges = setOf(FakeFileChange(
                        moduleInfoClass,
                        ChangeType.REMOVED,
                        FileType.FILE,
                        "module-info.class"
                )),
                inputJarHashesFile = inputJarHashes
        ).doProcess()
    }

    @Test
    fun testJarNameDoesNotImpactOutput() {
        val inputJar1 = tmpDir.root.toPath().resolve("input_1.jar")
        jarWithEmptyClasses(
            inputJar1, ImmutableList.of("$PACKAGE/A", "$PACKAGE/B", "$PACKAGE/C")
        )

        getDelegate(
            projectClasses = setOf(inputJar1.toFile()),
            projectOutput = out.toFile()
        ).doProcess()

        val outputNames = out.toFile().list()

        val inputJar2 = inputJar1.resolveSibling("input_2.jar")
        Files.copy(inputJar1, inputJar2)

        getDelegate(
            projectClasses = setOf(inputJar2.toFile()),
            projectOutput = out.toFile()
        ).doProcess()

        assertThat(out.toFile().list()).isEqualTo(outputNames)
    }

    /**
     * Check bucket number is based on relative path for directory input or package name for jar
     * input.
     *
     * When java api desugaring is enabled in release build, d8 is dexing in DexIndexed mode only
     * and the dex archive output location is determined by bucket number. The bucket number
     * should not rely on the absolute path of input so that dex archive task can be relocatable
     * across machines.
     */
    @Test
    fun testBucketingStrategy() {
        val outputKeepRule = tmpDir.root.toPath().resolve("outputKeepRule")
        Files.createDirectories(outputKeepRule)
        val numberOfBuckets = 3

        val dirInput1 = tmpDir.root.toPath().resolve("dir_input1")
        dirWithEmptyClasses(dirInput1, ImmutableList.of("$PACKAGE/Cat"))

        val jarInput1 = tmpDir.root.toPath().resolve("input1.jar")
        jarWithEmptyClasses(jarInput1, ImmutableList.of("$PACKAGE/Dog"))

        getDelegate(
            projectClasses = setOf(dirInput1.toFile(), jarInput1.toFile()),
            projectOutput = out.toFile(),
            projectOutputKeepRules = outputKeepRule.toFile(),
            numberOfBuckets = numberOfBuckets
        ).doProcess()

        val dirBucket = findOutputBucketForDirInput(numberOfBuckets)
        val jarBucket = findOutputBucketForJarInput(numberOfBuckets)

        FileUtils.cleanOutputDir(out.toFile())
        FileUtils.cleanOutputDir(outputKeepRule.toFile())

        val dirInput2 = tmpDir.root.toPath().resolve("dir_input2")
        dirWithEmptyClasses(dirInput2, ImmutableList.of("$PACKAGE/Dog"))

        val jarInput2 = tmpDir.root.toPath().resolve("input2.jar")
        jarWithEmptyClasses(jarInput2, ImmutableList.of("$PACKAGE/Cat"))

        getDelegate(
            projectClasses = setOf(dirInput2.toFile(), jarInput2.toFile()),
            projectOutput = out.toFile(),
            projectOutputKeepRules = outputKeepRule.toFile(),
            numberOfBuckets = numberOfBuckets
        ).doProcess()

        // Check that dir bucket location can change as the relative paths of the input classes are
        // different.
        val newDirBucket = findOutputBucketForDirInput(numberOfBuckets)
        assertThat(FileUtils.find(out.resolve(dirBucket).toFile(), Pattern.compile(".*\\.dex")))
            .hasSize(0)
        assertThat(FileUtils.find(out.resolve(newDirBucket).toFile(), Pattern.compile(".*\\.dex")))
                .hasSize(1)

        // Check that jar bucket location does not change as the package names of the input classes
        // are the same.
        assertThat(FileUtils.find(out.toFile(), Pattern.compile(".*_$jarBucket\\.jar")))
            .hasSize(1)
    }

    @Test
    fun test_removingInputHashesRunsNonIncrementally() {
        val input = tmpDir.root.toPath().resolve("input")
        dirWithEmptyClasses(input, listOf("test/A", "test/B"))

        val inputJarHashes = tmpDir.newFile()
        getDelegate(
            projectClasses = setOf(input.toFile()),
            projectOutput = out.toFile(),
            inputJarHashesFile = inputJarHashes
        ).doProcess()

        val initialTimestamp = FileUtils.find(out.toFile(), "A.dex").get().lastModified()
        inputJarHashes.delete()
        TestUtils.waitForFileSystemTick()

        getDelegate(
            projectClasses = setOf(input.toFile()),
            projectOutput = out.toFile(),
            isIncremental = true,
            inputJarHashesFile = inputJarHashes
        ).doProcess()

        assertThat(FileUtils.find(out.toFile(), "A.dex").get().lastModified()).isGreaterThan(
            initialTimestamp
        )
    }

    private fun findOutputBucketForDirInput(numberOfBuckets: Int): String =
        (0 until numberOfBuckets).find {
            FileUtils.find(out.resolve(it.toString()).toFile(),
                Pattern.compile(".*\\.dex")).size == 1
        }?.toString() ?: fail("Failed to find a folder with dex output")

    private fun findOutputBucketForJarInput(numberOfBuckets: Int): String {
        val file = FileUtils.find(out.toFile(), Pattern.compile(".*\\.jar")).first() ?:
            fail("Failed to find a jar dex output")
        val name = file.name
        return name.substring(name.lastIndexOf("_") + 1, name.lastIndexOf("."))
    }

    private fun getDelegate(
        isIncremental: Boolean = false,
        isDebuggable: Boolean = true,
        minSdkVersion: Int = 1,
        projectClasses: Set<File> = emptySet(),
        projectChanges: Set<FileChange> = emptySet(),
        externalLibClasses: Set<File> = emptySet(),
        externalLibChanges: Set<FileChange> = emptySet(),
        projectOutput: File = tmpDir.newFolder(),
        externalLibsOutput: File = tmpDir.newFolder(),
        java8Desugaring: VariantScope.Java8LangSupport = VariantScope.Java8LangSupport.UNUSED,
        desugaringClasspath: Set<File> = emptySet(),
        libConfiguration: String? = null,
        projectOutputKeepRules: File? = null,
        externalLibsOutputKeepRules: File? = null,
        numberOfBuckets: Int = 1,
        inputJarHashesFile: File = tmpDir.newFile()
    ): DexArchiveBuilderTaskDelegate {
        return DexArchiveBuilderTaskDelegate(
            isIncremental = isIncremental,
            projectClasses = projectClasses,
            projectChangedClasses = projectChanges,
            subProjectClasses = emptySet(),
            subProjectChangedClasses = emptySet(),
            externalLibClasses = externalLibClasses,
            externalLibChangedClasses = externalLibChanges,
            mixedScopeClasses = emptySet(),
            mixedScopeChangedClasses = emptySet(),
            projectOutputDex = projectOutput,
            projectOutputKeepRules = projectOutputKeepRules,
            subProjectOutputDex = tmpDir.newFolder(),
            subProjectOutputKeepRules = null,
            externalLibsOutputDex = externalLibsOutput,
            externalLibsOutputKeepRules = externalLibsOutputKeepRules,
            mixedScopeOutputDex = tmpDir.newFolder(),
            mixedScopeOutputKeepRules = null,
            inputJarHashesFile = inputJarHashesFile,
            desugarClasspathChangedClasses = emptySet(),
            desugarGraphDir =  tmpDir.newFolder().takeIf { java8Desugaring == VariantScope.Java8LangSupport.D8 },
            projectVariant = "myVariant",
            numberOfBuckets = numberOfBuckets,
            workerExecutor = workerExecutor,
            dexParams = DexParameters(
                minSdkVersion = minSdkVersion,
                debuggable = isDebuggable,
                withDesugaring = (java8Desugaring == VariantScope.Java8LangSupport.D8),
                desugarBootclasspath = emptyList(),
                desugarClasspath = desugaringClasspath.toList(),
                coreLibDesugarConfig = libConfiguration,
                errorFormatMode = SyncOptions.ErrorFormatMode.HUMAN_READABLE
            ),
            projectPath = FakeProviderFactory.factory.provider { "" },
            taskPath = "",
            analyticsService = FakeObjectFactory.factory.property(AnalyticsService::class.java)
                .value(FakeNoOpAnalyticsService())
        )
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "incrementalDexingTaskV2_{0}")
        fun parameters() = listOf(
            arrayOf(false),
            arrayOf(true)
        )
    }
}

internal fun writeEmptyInputJarHashes(file: File) {
    ObjectOutputStream(file.outputStream().buffered()).use {
        it.writeObject(mutableMapOf<File, String>())
    }
}

private const val PACKAGE = "com/example/tools"
