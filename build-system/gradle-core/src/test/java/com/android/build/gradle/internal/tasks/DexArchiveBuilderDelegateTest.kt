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

import com.android.build.gradle.internal.dexing.DexWorkActionParams
import com.android.build.gradle.internal.dexing.DexParameters
import com.android.build.gradle.internal.dexing.DexWorkAction
import com.android.build.gradle.internal.dexing.DxDexParameters
import com.android.build.gradle.internal.fixtures.FakeFileChange
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.transforms.NoOpMessageReceiver
import com.android.build.gradle.internal.transforms.testdata.Animal
import com.android.build.gradle.internal.transforms.testdata.CarbonForm
import com.android.build.gradle.internal.transforms.testdata.ClassWithDesugarApi
import com.android.build.gradle.internal.transforms.testdata.Dog
import com.android.build.gradle.internal.transforms.testdata.Toy
import com.android.build.gradle.options.SyncOptions
import com.android.builder.dexing.DexerTool
import com.android.builder.utils.FileCache
import com.android.builder.utils.FileCacheTestUtils
import com.android.testutils.TestClassesGenerator
import com.android.testutils.TestInputsGenerator
import com.android.testutils.TestInputsGenerator.dirWithEmptyClasses
import com.android.testutils.TestInputsGenerator.jarWithEmptyClasses
import com.android.testutils.TestUtils
import com.android.testutils.apk.Dex
import com.android.testutils.truth.FileSubject.assertThat
import com.android.testutils.truth.MoreTruth.assertThat
import com.android.utils.FileUtils
import com.google.common.collect.ImmutableList
import com.google.common.collect.Iterables
import com.google.common.io.ByteStreams
import com.google.common.truth.Truth.assertThat
import org.gradle.api.Action
import org.gradle.api.file.FileType
import org.gradle.work.ChangeType
import org.gradle.work.FileChange
import org.gradle.workers.ClassLoaderWorkerSpec
import org.gradle.workers.ProcessWorkerSpec
import org.gradle.workers.WorkQueue
import org.gradle.workers.WorkerConfiguration
import org.gradle.workers.WorkerExecutionException
import org.gradle.workers.WorkerExecutor
import org.gradle.workers.WorkerSpec
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.verify
import java.io.BufferedReader
import java.io.File
import java.io.FileFilter
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.fail

/** Testing the [DexArchiveBuilderTaskDelegate].  */
@RunWith(Parameterized::class)
class DexArchiveBuilderDelegateTest(
    private var dexerTool: DexerTool,
    private val withIncrementalDexingV2: Boolean
) {

    private lateinit var cacheDir: File
    private lateinit var userCache: FileCache
    private var expectedCacheEntryCount: Int = 0
    private var expectedCacheMisses: Int = 0
    private lateinit var out: Path

    @JvmField
    @Rule
    var tmpDir = TemporaryFolder()

    private val workerExecutor = object : WorkerExecutor {
        override fun submit(
            aClass: Class<out Runnable>,
            action: Action<in WorkerConfiguration>
        ) {
            val workerConfiguration = Mockito.mock(WorkerConfiguration::class.java)
            val captor = ArgumentCaptor.forClass(
                DexWorkActionParams::class.java
            )
            action.execute(workerConfiguration)
            verify(workerConfiguration).setParams(captor.capture())
            val workAction = DexWorkAction(
                captor.value
            )
            workAction.run()
        }

        @Throws(WorkerExecutionException::class)
        override fun await() {
            // do nothing;
        }

        override fun processIsolation(): WorkQueue {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun processIsolation(p0: Action<ProcessWorkerSpec>?): WorkQueue {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun noIsolation(): WorkQueue {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun noIsolation(p0: Action<WorkerSpec>?): WorkQueue {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun classLoaderIsolation(): WorkQueue {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun classLoaderIsolation(p0: Action<ClassLoaderWorkerSpec>?): WorkQueue {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }
    }

    @Before
    fun setUp() {
        expectedCacheEntryCount = 0
        expectedCacheMisses = 0
        cacheDir = FileUtils.join(tmpDir.root, "cache")
        userCache = FileCache.getInstanceWithMultiProcessLocking(cacheDir)

        out = tmpDir.root.toPath().resolve("out")
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
    fun testCacheUsedForExternalLibOnly() {
        // Caching is currently not supported when withIncrementalDexingV2 == true
        Assume.assumeFalse(dexerTool == DexerTool.D8 && withIncrementalDexingV2)

        val projectJar = tmpDir.root.toPath().resolve("projectInput.jar")
        jarWithEmptyClasses(projectJar, ImmutableList.of("$PACKAGE/A"))

        val jarInput = tmpDir.root.toPath().resolve("input.jar")
        jarWithEmptyClasses(jarInput, ImmutableList.of("$PACKAGE/B"))

        getDelegate(
            withCache = true,
            projectClasses = setOf(projectJar.toFile()),
            externalLibClasses = setOf(jarInput.toFile())
        ).doProcess()

        assertThat(cacheEntriesCount()).isEqualTo(1)
    }

    @Test
    fun testEntryRemovedFromTheArchive() {
        val inputDir = tmpDir.root.toPath().resolve("dir_input")
        val inputJar = tmpDir.root.toPath().resolve("input.jar")

        dirWithEmptyClasses(inputDir, ImmutableList.of("$PACKAGE/A", "$PACKAGE/B"))
        jarWithEmptyClasses(inputJar, ImmutableList.of("$PACKAGE/C"))

        getDelegate(
            projectClasses = setOf(inputDir.toFile(), inputJar.toFile()),
            projectOutput = out.toFile()
        ).doProcess()

        assertThat(FileUtils.find(out.toFile(), "B.dex").orNull()).isFile()

        val toDelete = inputDir.resolve("$PACKAGE/B.class").toFile()
        assertThat(toDelete.delete()).isTrue()

        val change = FakeFileChange(
            toDelete, ChangeType.REMOVED, FileType.FILE, "$PACKAGE/B.class"
        )
        getDelegate(
            isIncremental = true,
            projectChanges = setOf(change),
            projectOutput = out.toFile()
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
    fun testCacheKeyInputsChanges() {
        // Caching is currently not supported when withIncrementalDexingV2 == true
        Assume.assumeFalse(dexerTool == DexerTool.D8 && withIncrementalDexingV2)

        val inputJar = tmpDir.root.toPath().resolve("input.jar")
        jarWithEmptyClasses(inputJar, ImmutableList.of())

        getDelegate(
            withCache = true,
            externalLibClasses = setOf(inputJar.toFile()),
            minSdkVersion = 19
        ).doProcess()
        assertThat(cacheEntriesCount()).isEqualTo(1)

        getDelegate(
            withCache = true,
            externalLibClasses = setOf(inputJar.toFile()),
            minSdkVersion = 20
        ).doProcess()
        assertThat(cacheEntriesCount()).isEqualTo(2)

        getDelegate(
            withCache = true,
            externalLibClasses = setOf(inputJar.toFile()),
            minSdkVersion = 19,
            isDebuggable = false
        ).doProcess()
        assertThat(cacheEntriesCount()).isEqualTo(3)

        getDelegate(
            withCache = true,
            externalLibClasses = setOf(inputJar.toFile()),
            minSdkVersion = 20,
            isDebuggable = false
        ).doProcess()
        assertThat(cacheEntriesCount()).isEqualTo(4)

        getDelegate(
            withCache = true,
            externalLibClasses = setOf(inputJar.toFile()),
            minSdkVersion = 19,
            dexerTool = if (dexerTool == DexerTool.D8) DexerTool.DX else DexerTool.D8
        ).doProcess()
        assertThat(cacheEntriesCount()).isEqualTo(5)
    }

    @Test
    fun testD8DesugaringCacheKeys() {
        // Only for D8, Ignore DX
        Assume.assumeTrue(dexerTool == DexerTool.D8)

        // Caching is currently not supported when withIncrementalDexingV2 == true
        Assume.assumeFalse(dexerTool == DexerTool.D8 && withIncrementalDexingV2)

        val inputJar = tmpDir.root.toPath().resolve("input.jar")
        val emptyLibDir = tmpDir.root.toPath().resolve("emptylibDir")
        val emptyLibJar = tmpDir.root.toPath().resolve("emptylib.jar")
        val carbonFormLibJar = tmpDir.root.toPath().resolve("carbonFormlib.jar")
        val carbonFormLibJar2 = tmpDir.root.toPath().resolve("carbonFormlib2.jar")
        val animalLibDir = tmpDir.root.toPath().resolve("animalLibDir")
        val animalLibJar = tmpDir.root.toPath().resolve("animalLib.jar")
        TestInputsGenerator.pathWithClasses(
            carbonFormLibJar,
            ImmutableList.of<Class<*>>(CarbonForm::class.java)
        )
        TestInputsGenerator.pathWithClasses(
            carbonFormLibJar2, ImmutableList.of(CarbonForm::class.java, Toy::class.java)
        )
        TestInputsGenerator.pathWithClasses(
            animalLibDir,
            ImmutableList.of<Class<*>>(Animal::class.java)
        )
        TestInputsGenerator.pathWithClasses(
            animalLibJar,
            ImmutableList.of<Class<*>>(Animal::class.java)
        )
        TestInputsGenerator.pathWithClasses(inputJar, ImmutableList.of<Class<*>>(Dog::class.java))
        TestInputsGenerator.pathWithClasses(emptyLibDir, ImmutableList.of())
        TestInputsGenerator.pathWithClasses(emptyLibJar, ImmutableList.of())

        getDelegate(
            withCache = true,
            externalLibClasses = setOf(inputJar.toFile()),
            java8Desugaring = VariantScope.Java8LangSupport.D8
        ).doProcess()
        // Cache was empty so it's a miss and result was cached.
        expectedCacheEntryCount++
        expectedCacheMisses++
        checkCache()

        getDelegate(
            withCache = true,
            externalLibClasses = setOf(inputJar.toFile()),
            java8Desugaring = VariantScope.Java8LangSupport.D8,
            desugaringClasspath = setOf(animalLibDir.toFile(), carbonFormLibJar.toFile())
        ).doProcess()
        // The directory dependency should disable caching
        checkCache()

        // Rerun initial invocation with D8 desugaring
        getDelegate(
            withCache = true,
            externalLibClasses = setOf(inputJar.toFile()),
            java8Desugaring = VariantScope.Java8LangSupport.D8
        ).doProcess()
        // Exact same run as inintialInvocation: should be a hit
        checkCache()

        // With the dependencies as jar and an empty directory
        getDelegate(
            withCache = true,
            externalLibClasses = setOf(inputJar.toFile()),
            java8Desugaring = VariantScope.Java8LangSupport.D8,
            desugaringClasspath = setOf(
                animalLibJar.toFile(), carbonFormLibJar.toFile(), emptyLibDir.toFile()
            )
        ).doProcess()
        // The dir without dependency doesn't prevent caching, presence of the dependencies
        // changes the cache key
        expectedCacheMisses++
        expectedCacheEntryCount++
        checkCache()

        // Same as invocation02 without the empty directory

        getDelegate(
            withCache = true,
            externalLibClasses = setOf(inputJar.toFile()),
            java8Desugaring = VariantScope.Java8LangSupport.D8,
            desugaringClasspath = setOf(animalLibJar.toFile(), carbonFormLibJar.toFile())
        ).doProcess()
        // Removing the empty directory doesn't change the cache key
        checkCache()

        // Same as invocation03 with empty jar
        getDelegate(
            withCache = true,
            externalLibClasses = setOf(inputJar.toFile()),
            java8Desugaring = VariantScope.Java8LangSupport.D8,
            desugaringClasspath = setOf(
                animalLibJar.toFile(), carbonFormLibJar.toFile(), emptyLibJar.toFile()
            )
        ).doProcess()
        // Adding the empty jar doesn't change the cache key
        checkCache()

        // Same as invocation03 without Animal
        getDelegate(
            withCache = true,
            externalLibClasses = setOf(inputJar.toFile()),
            java8Desugaring = VariantScope.Java8LangSupport.D8,
            desugaringClasspath = setOf(carbonFormLibJar.toFile())
        ).doProcess()
        // Without Animal we can not see the dependency to animalLibJarInput so it's a hit
        // on "initial invocation with D8 desugaring"
        checkCache()

        // Same as invocation03 without CarbonForm
        getDelegate(
            withCache = true,
            externalLibClasses = setOf(inputJar.toFile()),
            java8Desugaring = VariantScope.Java8LangSupport.D8,
            desugaringClasspath = setOf(animalLibJar.toFile())
        ).doProcess()
        // Even with incomplete hierarchy we should still be able to identify the dependency to the
        // one available classpath entry.
        expectedCacheMisses++
        expectedCacheEntryCount++
        checkCache()
    }

    @Test
    fun testCachingExternalLibsForCoreLibraryDesugaring() {
        Assume.assumeTrue(dexerTool == DexerTool.D8)
        Assume.assumeFalse(withIncrementalDexingV2)

        val inputJar = tmpDir.root.toPath().resolve("input.jar")
        TestInputsGenerator.pathWithClasses(
            inputJar,
            ImmutableList.of<Class<*>>(ClassWithDesugarApi::class.java)
        )
        val externalLibsOutputKeepRules = tmpDir.newFolder("keep_rules")
        // 1 round, no shrinking, cache misses as cache is empty
        getDelegate(
            withCache = true,
            externalLibClasses = setOf(inputJar.toFile()),
            java8Desugaring = VariantScope.Java8LangSupport.D8,
            libConfiguration = desugarConfig
        ).doProcess()
        expectedCacheEntryCount++
        expectedCacheMisses++
        checkCache()

        // 2 round, same as round 1, cache hits
        getDelegate(
            withCache = true,
            externalLibClasses = setOf(inputJar.toFile()),
            java8Desugaring = VariantScope.Java8LangSupport.D8,
            libConfiguration = desugarConfig
        ).doProcess()
        checkCache()

        // 3 round, shrining enabled, cache misses as there is no cache for keep rule files and we
        // have to re-process the input
        getDelegate(
            withCache = true,
            externalLibClasses = setOf(inputJar.toFile()),
            java8Desugaring = VariantScope.Java8LangSupport.D8,
            externalLibsOutputKeepRules =  externalLibsOutputKeepRules,
            libConfiguration = desugarConfig
        ).doProcess()
        // expectedCacheEntryCount is increased by two: one is for dex outputs, the other one is
        // for keep_rule outputs
        expectedCacheEntryCount += 2
        expectedCacheMisses += 2
        checkCache()

        // 4 round, same as round 1, cache hits
        getDelegate(
            withCache = true,
            externalLibClasses = setOf(inputJar.toFile()),
            java8Desugaring = VariantScope.Java8LangSupport.D8,
            libConfiguration = desugarConfig
        ).doProcess()
        checkCache()

        // 5 round, same as round 3, cache hits
        getDelegate(
            withCache = true,
            externalLibClasses = setOf(inputJar.toFile()),
            java8Desugaring = VariantScope.Java8LangSupport.D8,
            externalLibsOutputKeepRules =  externalLibsOutputKeepRules,
            libConfiguration = desugarConfig
        ).doProcess()
        checkCache()

        // 6 round, there is no libConfiguration compared with round 1, cache misses
        getDelegate(
            withCache = true,
            externalLibClasses = setOf(inputJar.toFile()),
            java8Desugaring = VariantScope.Java8LangSupport.D8
        ).doProcess()
        expectedCacheEntryCount++
        expectedCacheMisses++
        checkCache()
    }

    @Test
    fun testIncrementalUnchangedDirInput() {
        val input = tmpDir.newFolder("classes").toPath()
        dirWithEmptyClasses(input, ImmutableList.of("test/A", "test/B"))

        getDelegate(
            isIncremental = true,
            projectClasses = setOf(input.toFile()),
            projectOutput = out.toFile()
        ).doProcess()

        assertThat(FileUtils.getAllFiles(out.toFile())).isEmpty()
    }

    /** Regression test for b/65241720.  */
    @Test
    fun testIncrementalWithSharding() {
        val input = tmpDir.root.toPath().resolve("classes.jar")
        jarWithEmptyClasses(input, ImmutableList.of("test/A", "test/B"))

        getDelegate(
            withCache = true,
            externalLibClasses = setOf(input.toFile()),
            externalLibsOutput = out.toFile()
        ).doProcess()

        assertThat(FileUtils.find(out.toFile(), Pattern.compile(".*_\\d\\.jar")).size)
            .isAtLeast(1)

        // clean the output of the previous transform
        FileUtils.cleanOutputDir(out.toFile())

        getDelegate(
            withCache = true,
            externalLibClasses = setOf(input.toFile()),
            externalLibsOutput = out.toFile()
        ).doProcess()

        assertThat(FileUtils.getAllFiles(out.toFile())).hasSize(1)
        assertThat(out.toFile().listFiles()!!.size).isEqualTo(1)
        val onlyDexOutput = out.toFile().listFiles()!![0]

        // modify the file so it is not a build cache hit any more
        Files.deleteIfExists(input)
        jarWithEmptyClasses(input, ImmutableList.of("test/C"))

        getDelegate(
            withCache = true,
            externalLibClasses = setOf(input.toFile()),
            externalLibsOutput = out.toFile(),
            externalLibChanges = setOf(FakeFileChange(input.toFile(), ChangeType.MODIFIED, FileType.FILE, ""))
        ).doProcess()

        assertThat(onlyDexOutput).doesNotExist()
    }

    /** Regression test for b/65241720.  */
    @Test
    fun testDirectoryRemovedInIncrementalBuild() {
        val input = tmpDir.root.toPath().resolve("classes")
        val nestedDir = input.resolve("nested_dir")
        Files.createDirectories(nestedDir)
        val nestedDirOutput = out.resolve("nested_dir")
        Files.createDirectories(nestedDirOutput)

        getDelegate(
            isIncremental = true,
            projectClasses = setOf(input.toFile()),
            projectOutput = out.toFile(),
            projectChanges = setOf(FakeFileChange(
                nestedDir.toFile(),
                ChangeType.REMOVED,
                FileType.DIRECTORY,
                "nested_dir"
            ))
        ).doProcess()

        assertThat(nestedDirOutput.toFile()).doesNotExist()
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
     * Check bucket number is based on package name for directory input or entry name for jar input.
     *
     * When java api desugaring is enabled in release build, d8 is dexing in DexIndexed mode only
     * and the dex archive output location is determined by bucket number. The bucket number
     * should not rely on the absolute path of input so that dex archive task can be relocatable
     * across machines.
     */
    @Test
    fun testConsistentBucketing() {
        Assume.assumeTrue(dexerTool == DexerTool.D8)

        val outputKeepRule = tmpDir.root.toPath().resolve("outputKeepRule")
        Files.createDirectories(outputKeepRule)
        val numberOfBuckets = 3

        val dirInput1 = tmpDir.root.toPath().resolve("dir_input1")
        dirWithEmptyClasses(dirInput1, ImmutableList.of("$PACKAGE/Animal"))

        val jarInput1 = tmpDir.root.toPath().resolve("input1.jar")
        jarWithEmptyClasses(jarInput1, ImmutableList.of("$PACKAGE/Boy"))

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
        dirWithEmptyClasses(dirInput2, ImmutableList.of("$PACKAGE/Account"))

        val jarInput2 = tmpDir.root.toPath().resolve("input2.jar")
        jarWithEmptyClasses(jarInput2, ImmutableList.of("$PACKAGE/Boy"))

        getDelegate(
            projectClasses = setOf(dirInput2.toFile(), jarInput2.toFile()),
            projectOutput = out.toFile(),
            projectOutputKeepRules = outputKeepRule.toFile(),
            numberOfBuckets = numberOfBuckets
        ).doProcess()

        assertThat(FileUtils.find(out.resolve(dirBucket).toFile(), Pattern.compile(".*\\.dex")))
            .hasSize(1)
        assertThat(FileUtils.find(out.toFile(), Pattern.compile(".*_$jarBucket\\.jar")))
            .hasSize(1)
    }

    @Test
    fun testDesugaringAdditionalPaths() {
        // Only for D8, Ignore DX
        Assume.assumeTrue(dexerTool == DexerTool.D8)
        // Caching is currently not supported when withIncrementalDexingV2 == true
        Assume.assumeFalse(dexerTool == DexerTool.D8 && withIncrementalDexingV2)

        val carbonFormLibJar = tmpDir.root.resolve("carbonFormLib.jar")
        TestInputsGenerator.pathWithClasses(
            carbonFormLibJar.toPath(), ImmutableList.of<Class<*>>(CarbonForm::class.java)
        )

        val animalLibJar = tmpDir.root.resolve("animalLib.jar")
        TestInputsGenerator.pathWithClasses(
            animalLibJar.toPath(), ImmutableList.of<Class<*>>(Animal::class.java)
        )

        val externalLibsOutput = tmpDir.newFolder()
        getDelegate(
            withCache = true,
            externalLibClasses = setOf(carbonFormLibJar, animalLibJar),
            java8Desugaring = VariantScope.Java8LangSupport.D8,
            externalLibsOutput = externalLibsOutput
        ).doProcess()
        externalLibsOutput.deleteRecursively()
        // Outputs should be retrieved from Android build cache.
        getDelegate(
            withCache = true,
            externalLibClasses = setOf(carbonFormLibJar, animalLibJar),
            java8Desugaring = VariantScope.Java8LangSupport.D8,
            externalLibsOutput = externalLibsOutput
        ).doProcess()

        // This matches "hash_1.jar" but not "hash.jar" which is retrieved from cache.
        val matchProcessedName = Regex(".*_\\d+.jar")
        assertThat(
            externalLibsOutput.listFiles()!!.filter { it.name.contains(matchProcessedName) }).named(
            "list of external libraries processed"
        ).isEmpty()

        // Modify carbonFormLib as animalLib desugaring depends on it.
        TestInputsGenerator.pathWithClasses(
            carbonFormLibJar.toPath(),
            ImmutableList.of<Class<*>>(CarbonForm::class.java, Toy::class.java)
        )
        getDelegate(
            isIncremental = true,
            withCache = true,
            externalLibClasses = setOf(carbonFormLibJar, animalLibJar),
            externalLibChanges = setOf(
                FakeFileChange(
                    carbonFormLibJar,
                    changeType = ChangeType.MODIFIED
                )
            ),
            java8Desugaring = VariantScope.Java8LangSupport.D8,
            externalLibsOutput = externalLibsOutput
        ).doProcess()
        assertThat(
            externalLibsOutput.listFiles()!!
                .filterNot { it.name.contains(matchProcessedName) }).named(
            "list of external libraries from cache"
        ).isEmpty()
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


    private fun checkCache() {
        val entriesCount = cacheEntriesCount()
        assertThat(entriesCount).named("Cache entry count").isEqualTo(expectedCacheEntryCount)
        // Misses occurs when filling the cache
        assertThat(FileCacheTestUtils.getMisses(userCache))
            .named("Cache misses")
            .isEqualTo(expectedCacheMisses)
    }

    private fun getDelegate(
        isIncremental: Boolean = false,
        withCache: Boolean = false,
        isDebuggable: Boolean = true,
        minSdkVersion: Int = 1,
        dexerTool: DexerTool = this.dexerTool,
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
        numberOfBuckets: Int = 1
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
            inputJarHashesFile = tmpDir.newFile(),
            desugarClasspathChangedClasses = emptySet(),
            incrementalDexingV2 = withIncrementalDexingV2,
            desugarGraphDir =  tmpDir.newFolder().takeIf{ withIncrementalDexingV2 },
            dexer = dexerTool,
            useGradleWorkers = false,
            projectVariant = "myVariant",
            numberOfBuckets = numberOfBuckets,
            messageReceiver = NoOpMessageReceiver(),
            userLevelCache = userCache.takeIf { withCache },
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
            dxDexParams = DxDexParameters(
                inBufferSize = 10,
                outBufferSize = 10,
                dxNoOptimizeFlagPresent = false,
                jumboMode = true
            )
        )
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "dexerTool_{0}_incrementalDexingV2_{1}")
        fun parameters() = listOf(
            arrayOf(DexerTool.DX, false),
            // No need to test arrayOf(DexerTool.DX, true) as incrementalDexingV2 is only for D8
            arrayOf(DexerTool.D8, false),
            arrayOf(DexerTool.D8, true)
        )
        val desugarConfig = TestUtils.getDesugarLibConfigContentWithVersion("0.11.0")
    }

    private fun cacheEntriesCount(): Int {
        return userCache.cacheDirectory.listFiles(FileFilter { it.isDirectory })!!.size
    }
}

private const val PACKAGE = "com/example/tools"