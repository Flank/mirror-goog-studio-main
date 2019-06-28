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

package com.android.build.gradle.internal.transforms

import com.android.SdkConstants
import com.android.build.api.transform.Context
import com.android.build.api.transform.Format
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.Status
import com.android.build.api.transform.TransformInput
import com.android.build.api.transform.TransformOutputProvider
import com.android.build.gradle.internal.fixtures.FakeFileCollection
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.transforms.testdata.Animal
import com.android.build.gradle.internal.transforms.testdata.CarbonForm
import com.android.build.gradle.internal.transforms.testdata.Dog
import com.android.build.gradle.options.SyncOptions
import com.android.builder.core.DefaultDexOptions
import com.android.builder.dexing.DexerTool
import com.android.builder.utils.FileCache
import com.android.builder.utils.FileCacheTestUtils
import com.android.testutils.TestClassesGenerator
import com.android.testutils.TestInputsGenerator
import com.android.testutils.TestInputsGenerator.dirWithEmptyClasses
import com.android.testutils.TestInputsGenerator.jarWithEmptyClasses
import com.android.testutils.apk.Dex
import com.android.testutils.truth.MoreTruth.assertThat
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.FileUtils
import com.google.common.io.ByteStreams
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import org.gradle.api.Action
import org.gradle.workers.WorkerConfiguration
import org.gradle.workers.WorkerExecutionException
import org.gradle.workers.WorkerExecutor
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import java.io.File
import java.io.FileFilter
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Testing the [DexArchiveBuilderTransform].  */
@RunWith(Parameterized::class)
class DexArchiveBuilderTransformTest {
    private var cacheDir: File? = null
    private var userCache: FileCache? = null
    private var expectedCacheEntryCount: Int = 0
    private var expectedCacheMisses: Int = 0

    @JvmField
    @Parameterized.Parameter
    var dexerTool: DexerTool? = null

    private lateinit var context: Context
    private lateinit var outputProvider: TransformOutputProvider
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
                DexArchiveBuilderTransform.DexConversionParameters::class.java
            )
            action.execute(workerConfiguration)
            verify(workerConfiguration).setParams(captor.capture())
            val workAction = DexArchiveBuilderTransform.DexConversionWorkAction(
                captor.value
            )
            workAction.run()
        }

        @Throws(WorkerExecutionException::class)
        override fun await() {
            // do nothing;
        }
    }

    @Before
    @Throws(IOException::class)
    fun setUp() {
        expectedCacheEntryCount = 0
        expectedCacheMisses = 0
        cacheDir = FileUtils.join(tmpDir.root, "cache")
        userCache = FileCache.getInstanceWithMultiProcessLocking(cacheDir!!)

        context = Mockito.mock(Context::class.java)
        `when`(context.workerExecutor).thenReturn(workerExecutor)

        out = tmpDir.root.toPath().resolve("out")
        Files.createDirectories(out)
        outputProvider = TestTransformOutputProvider(out)
    }

    @Test
    @Throws(Exception::class)
    fun testInitialBuild() {
        val dirInput = getDirInput(
            tmpDir.root.toPath().resolve("dir_input"),
            setOf("$PACKAGE/A")
        )
        val jarInput = getJarInput(
            tmpDir.root.toPath().resolve("input.jar"),
            setOf("$PACKAGE/B")
        )
        val invocation = TransformTestHelper.invocationBuilder()
            .setContext(context)
            .setTransformOutputProvider(outputProvider)
            .setInputs(setOf(dirInput, jarInput))
            .setIncremental(true)
            .build()
        getTransform(null).transform(invocation)

        assertThat(FileUtils.find(out.toFile(), Pattern.compile(".*\\.dex"))).hasSize(1)
        val jarDexArchives = FileUtils.find(out.toFile(), Pattern.compile(".*\\.jar"))
        assertThat(jarDexArchives).hasSize(1)
    }

    @Test
    @Throws(Exception::class)
    fun testCacheUsedForExternalLibOnly() {
        val cacheDir = FileUtils.join(tmpDir.root, "cache")
        val userCache = FileCache.getInstanceWithMultiProcessLocking(cacheDir)

        val dirInput = getDirInput(
            tmpDir.root.toPath().resolve("dir_input"),
            setOf("$PACKAGE/A")
        )
        val jarInput = getJarInput(
            tmpDir.root.toPath().resolve("input.jar"),
            setOf("$PACKAGE/B")
        )
        val invocation = TransformTestHelper.invocationBuilder()
            .setContext(context)
            .setInputs(setOf(dirInput, jarInput))
            .setTransformOutputProvider(outputProvider)
            .setIncremental(true)
            .build()
        val transform = getTransform(userCache)
        transform.transform(invocation)

        assertThat(cacheEntriesCount(cacheDir)).isEqualTo(1)
    }

    @Test
    @Throws(Exception::class)
    fun testCacheUsedForLocalJars() {
        val cacheDir = FileUtils.join(tmpDir.root, "cache")
        val cache = FileCache.getInstanceWithSingleProcessLocking(cacheDir)

        val inputJar = tmpDir.root.toPath().resolve("input.jar")
        val input = getJarInput(inputJar, setOf("$PACKAGE/A"))

        val invocation = TransformTestHelper.invocationBuilder()
            .setContext(context)
            .setInputs(input)
            .setTransformOutputProvider(outputProvider)
            .setIncremental(true)
            .build()
        val transform = getTransform(cache)
        transform.transform(invocation)

        assertThat(cacheDir.listFiles(FileFilter { it.isDirectory() })).hasLength(1)
    }

    @Test
    @Throws(Exception::class)
    fun testEntryRemovedFromTheArchive() {
        val inputDir = tmpDir.root.toPath().resolve("dir_input")
        val inputJar = tmpDir.root.toPath().resolve("input.jar")

        val dirTransformInput = getDirInput(inputDir, setOf("$PACKAGE/A", "$PACKAGE/B"))
        val jarTransformInput = getJarInput(inputJar, setOf("$PACKAGE/C"))

        val invocation = TransformTestHelper.invocationBuilder()
            .setContext(context)
            .setInputs(dirTransformInput, jarTransformInput)
            .setTransformOutputProvider(outputProvider)
            .setIncremental(true)
            .build()
        getTransform(null).transform(invocation)
        assertThat(FileUtils.find(out.toFile(), "B.dex").orNull()).isFile()

        // remove the class file
        val deletedDirInput = TransformTestHelper.directoryBuilder(inputDir.toFile())
            .putChangedFiles(
                mapOf(
                    inputDir.resolve("$PACKAGE/B.class").toFile() to Status.REMOVED
                )
            )
            .setScope(QualifiedContent.Scope.PROJECT)
            .build()

        val unchangedJarInput = TransformTestHelper.singleJarBuilder(inputJar.toFile())
            .setStatus(Status.NOTCHANGED)
            .setScopes(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
            .build()
        val secondInvocation = TransformTestHelper.invocationBuilder()
            .setContext(context)
            .setInputs(deletedDirInput, unchangedJarInput)
            .setTransformOutputProvider(outputProvider)
            .setIncremental(true)
            .build()
        getTransform(null).transform(secondInvocation)
        assertThat(FileUtils.find(out.toFile(), "B.dex").orNull()).isNull()
        assertThat(FileUtils.find(out.toFile(), "A.dex").orNull()).isFile()
    }

    @Test
    @Throws(Exception::class)
    fun testNonIncremental() {
        val dirInput = getDirInput(
            tmpDir.root.toPath().resolve("dir_input"),
            setOf("$PACKAGE/A")
        )

        val jarInput = getJarInput(
            tmpDir.root.toPath().resolve("input.jar"),
            setOf("$PACKAGE/B")
        )
        val invocation = TransformTestHelper.invocationBuilder()
            .setContext(context)
            .setInputs(dirInput, jarInput)
            .setIncremental(true)
            .setTransformOutputProvider(outputProvider)
            .build()
        getTransform(null).transform(invocation)

        val dir2Input = getDirInput(
            tmpDir.root.toPath().resolve("dir_2_input"),
            setOf("$PACKAGE/C")
        )
        val jar2Input = getJarInput(
            tmpDir.root.toPath().resolve("input.jar"),
            setOf("$PACKAGE/B")
        )
        val invocation2 = TransformTestHelper.invocationBuilder()
            .setContext(context)
            .setInputs(dir2Input, jar2Input)
            .setIncremental(false)
            .setTransformOutputProvider(outputProvider)
            .build()
        getTransform(null).transform(invocation2)
        assertThat(FileUtils.find(out.toFile(), "A.dex").orNull()).isNull()
    }

    @Test
    @Throws(Exception::class)
    fun testCacheKeyInputsChanges() {
        val cacheDir = FileUtils.join(tmpDir.root, "cache")
        val userCache = FileCache.getInstanceWithMultiProcessLocking(cacheDir)

        val inputJar = tmpDir.root.toPath().resolve("input.jar")
        val jarInput = getJarInput(inputJar, setOf())
        val invocation = TransformTestHelper.invocationBuilder()
            .addInput(jarInput)
            .setTransformOutputProvider(outputProvider)
            .setContext(context)
            .build()

        val transform = getTransform(userCache, 19, true)
        transform.transform(invocation)
        assertThat(cacheEntriesCount(cacheDir)).isEqualTo(1)

        val minChangedTransform = getTransform(userCache, 20, true)
        minChangedTransform.transform(invocation)
        assertThat(cacheEntriesCount(cacheDir)).isEqualTo(2)

        val debuggableChangedTransform = getTransform(userCache, 19, false)
        debuggableChangedTransform.transform(invocation)
        assertThat(cacheEntriesCount(cacheDir)).isEqualTo(3)

        val minAndDebuggableChangedTransform = getTransform(userCache, 20, false)
        minAndDebuggableChangedTransform.transform(invocation)
        assertThat(cacheEntriesCount(cacheDir)).isEqualTo(4)

        val useDifferentDexerTransform = DexArchiveBuilderTransformBuilder()
            .setAndroidJarClasspath(FakeFileCollection())
            .setDexOptions(DefaultDexOptions())
            .setMessageReceiver(NoOpMessageReceiver())
            .setErrorFormatMode(SyncOptions.ErrorFormatMode.HUMAN_READABLE)
            .setUserLevelCache(userCache)
            .setMinSdkVersion(20)
            .setDexer(if (dexerTool == DexerTool.DX) DexerTool.D8 else DexerTool.DX)
            .setUseGradleWorkers(true)
            .setInBufferSize(10)
            .setOutBufferSize(10)
            .setIsDebuggable(false)
            .setJava8LangSupportType(VariantScope.Java8LangSupport.UNUSED)
            .setProjectVariant("myVariant")
            .setIncludeFeaturesInScope(false)
            .createDexArchiveBuilderTransform()
        useDifferentDexerTransform.transform(invocation)
        assertThat(cacheEntriesCount(cacheDir)).isEqualTo(5)
    }

    @Test
    @Throws(Exception::class)
    fun testD8DesugaringCacheKeys() {

        // Only for D8, Ignore DX
        Assume.assumeTrue(dexerTool == DexerTool.D8)

        val inputJar = tmpDir.root.toPath().resolve("input.jar")
        val emptyLibDir = tmpDir.root.toPath().resolve("emptylibDir")
        val emptyLibJar = tmpDir.root.toPath().resolve("emptylib.jar")
        val carbonFormLibJar = tmpDir.root.toPath().resolve("carbonFormlib.jar")
        val animalLibDir = tmpDir.root.toPath().resolve("animalLibDir")
        val animalLibJar = tmpDir.root.toPath().resolve("animalLib.jar")
        TestInputsGenerator.pathWithClasses(
            carbonFormLibJar,
            setOf(CarbonForm::class.java)
        )
        TestInputsGenerator.pathWithClasses(
            animalLibDir,
            setOf(Animal::class.java)
        )
        TestInputsGenerator.pathWithClasses(
            animalLibJar,
            setOf(Animal::class.java)
        )
        TestInputsGenerator.pathWithClasses(inputJar, setOf(Dog::class.java))
        val jarInput = getJarInput(inputJar)
        val emptyLibDirInput = getDirInput(emptyLibDir, setOf())
        val emptyLibJarInput = getDirInput(emptyLibJar, setOf())
        val carbonFormLibJarInput = getJarInput(carbonFormLibJar)
        val animalLibJarInput = getJarInput(animalLibJar)
        val animalLibDirInput = getJarInput(animalLibDir)

        // Initial compilation: no lib
        val inintialInvocation = TransformTestHelper.invocationBuilder()
            .addInput(jarInput)
            .setTransformOutputProvider(outputProvider)
            .setContext(context)
            .build()

        getTransform(userCache, 19, true, VariantScope.Java8LangSupport.D8)
            .transform(inintialInvocation)
        // Cache was empty so it's a miss and result was cached.
        expectedCacheEntryCount++
        expectedCacheMisses++
        checkCache()

        // With a dependency to a class file in a directory
        val invocation01 = TransformTestHelper.invocationBuilder()
            .addInput(jarInput)
            .setTransformOutputProvider(outputProvider)
            .setContext(context)
            .addReferenceInput(animalLibDirInput)
            .addReferenceInput(carbonFormLibJarInput)
            .build()
        getTransform(userCache, 19, true, VariantScope.Java8LangSupport.D8).transform(invocation01)
        // The directory dependency should disable caching
        checkCache()

        // Rerun initial invocation with D8 desugaring
        getTransform(userCache, 19, true, VariantScope.Java8LangSupport.D8)
            .transform(inintialInvocation)
        // Exact same run as inintialInvocation: should be a hit
        checkCache()

        // With the dependencies as jar and an empty directory
        val invocation02 = TransformTestHelper.invocationBuilder()
            .addInput(jarInput)
            .setTransformOutputProvider(outputProvider)
            .setContext(context)
            .addReferenceInput(animalLibJarInput)
            .addReferenceInput(carbonFormLibJarInput)
            .addReferenceInput(emptyLibDirInput)
            .build()
        getTransform(userCache, 19, true, VariantScope.Java8LangSupport.D8).transform(invocation02)
        // The dir without dependency doesn't prevent caching, presence of the dependencies
        // changes the cache key
        expectedCacheMisses++
        expectedCacheEntryCount++
        checkCache()

        // Same as invocation02 without the empty directory
        val invocation03 = TransformTestHelper.invocationBuilder()
            .addInput(jarInput)
            .setTransformOutputProvider(outputProvider)
            .setContext(context)
            .addReferenceInput(animalLibJarInput)
            .addReferenceInput(carbonFormLibJarInput)
            .build()
        getTransform(userCache, 19, true, VariantScope.Java8LangSupport.D8).transform(invocation03)
        // Removing the empty directory doesn't change the cache key
        checkCache()

        // Same as invocation03 with empty jar
        val invocation04 = TransformTestHelper.invocationBuilder()
            .addInput(jarInput)
            .setTransformOutputProvider(outputProvider)
            .setContext(context)
            .addReferenceInput(animalLibJarInput)
            .addReferenceInput(carbonFormLibJarInput)
            .addReferenceInput(emptyLibJarInput)
            .build()
        getTransform(userCache, 19, true, VariantScope.Java8LangSupport.D8).transform(invocation04)
        // Adding the empty jar doesn't change the cache key
        checkCache()

        // Same as invocation03 without Animal
        val invocation05 = TransformTestHelper.invocationBuilder()
            .addInput(jarInput)
            .setTransformOutputProvider(outputProvider)
            .setContext(context)
            .addReferenceInput(carbonFormLibJarInput)
            .build()
        getTransform(userCache, 19, true, VariantScope.Java8LangSupport.D8).transform(invocation05)
        // Without Animal we can not see the dependency to animalLibJarInput so it's a hit
        // on "initial invocation with D8 desugaring"
        checkCache()

        // Same as invocation03 without CarbonForm
        val invocation06 = TransformTestHelper.invocationBuilder()
            .addInput(jarInput)
            .setTransformOutputProvider(outputProvider)
            .setContext(context)
            .addReferenceInput(animalLibJarInput)
            .build()
        getTransform(userCache, 19, true, VariantScope.Java8LangSupport.D8).transform(invocation06)
        // Even with incomplete hierarchy we should still be able to identify the dependency to the
        // one available classpath entry.
        expectedCacheMisses++
        expectedCacheEntryCount++
        checkCache()
    }

    @Test
    @Throws(Exception::class)
    fun testIncrementalUnchangedDirInput() {
        val input = tmpDir.newFolder("classes").toPath()
        dirWithEmptyClasses(input, setOf("test/A", "test/B"))

        val dirInput = TransformTestHelper.directoryBuilder(input.toFile())
            .putChangedFiles(emptyMap())
            .build()
        val invocation = TransformTestHelper.invocationBuilder()
            .setInputs(setOf(dirInput))
            .setIncremental(true)
            .setTransformOutputProvider(outputProvider)
            .setContext(context)
            .build()
        getTransform(null, 21, true).transform(invocation)
        Truth.assertThat(FileUtils.getAllFiles(out.toFile())).isEmpty()
    }

    /** Regression test for b/65241720.  */
    @Test
    @Throws(Exception::class)
    fun testIncrementalWithSharding() {
        val cacheDir = FileUtils.join(tmpDir.root, "cache")
        val userCache = FileCache.getInstanceWithMultiProcessLocking(cacheDir)
        val input = tmpDir.root.toPath().resolve("classes.jar")
        jarWithEmptyClasses(input, setOf("test/A", "test/B"))

        val jarInput = TransformTestHelper.singleJarBuilder(input.toFile())
            .setStatus(Status.ADDED)
            .setScopes(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
            .setContentTypes(QualifiedContent.DefaultContentType.CLASSES)
            .build()

        val noCacheInvocation = TransformTestHelper.invocationBuilder()
            .setInputs(jarInput)
            .setIncremental(false)
            .setTransformOutputProvider(outputProvider)
            .setContext(context)
            .build()

        val noCacheTransform = getTransform(userCache)
        noCacheTransform.transform(noCacheInvocation)
        assertThat(out.resolve("classes.jar.jar")).doesNotExist()

        // clean the output of the previous transform
        FileUtils.cleanOutputDir(out.toFile())

        val fromCacheInvocation = TransformTestHelper.invocationBuilder()
            .setInputs(jarInput)
            .setIncremental(true)
            .setTransformOutputProvider(outputProvider)
            .setContext(context)
            .build()
        val fromCacheTransform = getTransform(userCache)
        fromCacheTransform.transform(fromCacheInvocation)
        assertThat(FileUtils.getAllFiles(out.toFile())).hasSize(1)
        assertThat(out.resolve("classes.jar.jar")).exists()

        // modify the file so it is not a build cache hit any more
        Files.deleteIfExists(input)
        jarWithEmptyClasses(input, setOf("test/C"))

        val changedInput = TransformTestHelper.singleJarBuilder(input.toFile())
            .setStatus(Status.CHANGED)
            .setScopes(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
            .setContentTypes(QualifiedContent.DefaultContentType.CLASSES)
            .build()
        val changedInputInvocation = TransformTestHelper.invocationBuilder()
            .setInputs(changedInput)
            .setIncremental(true)
            .setTransformOutputProvider(outputProvider)
            .setContext(context)
            .build()
        val changedInputTransform = getTransform(userCache)
        changedInputTransform.transform(changedInputInvocation)
        assertThat(out.resolve("classes.jar.jar")).doesNotExist()
    }

    /** Regression test for b/65241720.  */
    @Test
    @Throws(Exception::class)
    fun testDirectoryRemovedInIncrementalBuild() {
        val input = tmpDir.root.toPath().resolve("classes")
        val nestedDir = input.resolve("nested_dir")
        Files.createDirectories(nestedDir)
        val nestedDirOutput = out.resolve("classes/nested_dir")
        Files.createDirectories(nestedDirOutput)

        val dirInput = TransformTestHelper.directoryBuilder(input.toFile())
            .putChangedFiles(mapOf(nestedDir.toFile() to Status.REMOVED))
            .build()

        val invocation = TransformTestHelper.invocationBuilder()
            .setInputs(dirInput)
            .setIncremental(true)
            .setTransformOutputProvider(outputProvider)
            .setContext(context)
            .build()
        val noCacheTransform = getTransform(null)
        noCacheTransform.transform(invocation)
        assertThat(nestedDirOutput).doesNotExist()
    }

    @Test
    @Throws(Exception::class)
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

        val dirInput = TransformTestHelper.singleJarBuilder(input.toFile()).build()
        val invocation = TransformTestHelper.invocationBuilder()
            .setInputs(setOf(dirInput))
            .setIncremental(false)
            .setTransformOutputProvider(outputProvider)
            .setContext(context)
            .build()
        getTransform(null, 21, true).transform(invocation)

        // verify output contains only test/A
        val jarWithDex = FileUtils.getAllFiles(out.toFile()).toList().single()
        ZipFile(jarWithDex).use { zipFile ->
            assertThat(zipFile.size()).isEqualTo(1)
            val inputStream = zipFile.getInputStream(zipFile.entries().nextElement())

            val dex = Dex(ByteStreams.toByteArray(inputStream), "unknown")
            assertThat(dex).containsExactlyClassesIn(setOf("Ltest/A;"))
        }
    }

    @Test
    @Throws(Exception::class)
    fun testChangingStreamName() {
        // make output provider that outputs based on name
        outputProvider = object : TestTransformOutputProvider(out) {
            override fun getContentLocation(
                name: String,
                types: MutableSet<QualifiedContent.ContentType>,
                scopes: MutableSet<in QualifiedContent.Scope>,
                format: Format
            ): File {
                return out.resolve(name.hashCode().toLong().toString()).toFile()
            }
        }

        val folder = tmpDir.root.toPath().resolve("dir_input")
        dirWithEmptyClasses(
            folder, setOf("$PACKAGE/A", "$PACKAGE/B", "$PACKAGE/C")
        )
        var dirInput = TransformTestHelper.directoryBuilder(folder.toFile())
            .setName("first-run")
            .setScope(QualifiedContent.Scope.PROJECT)
            .build()
        var invocation = TransformTestHelper.invocationBuilder()
            .setContext(context)
            .setInputs(setOf(dirInput))
            .setTransformOutputProvider(outputProvider)
            .build()
        val transform = DexArchiveBuilderTransformBuilder()
            .setAndroidJarClasspath(FakeFileCollection())
            .setDexOptions(DefaultDexOptions())
            .setMessageReceiver(NoOpMessageReceiver())
            .setErrorFormatMode(SyncOptions.ErrorFormatMode.HUMAN_READABLE)
            .setMinSdkVersion(21)
            .setDexer(dexerTool!!)
            .setIsDebuggable(true)
            .setJava8LangSupportType(VariantScope.Java8LangSupport.UNUSED)
            .setProjectVariant("myVariant")
            .createDexArchiveBuilderTransform()
        transform.transform(invocation)

        dirInput = TransformTestHelper.directoryBuilder(folder.toFile())
            .setName("second-run")
            .setScope(QualifiedContent.Scope.PROJECT)
            .putChangedFiles(
                mapOf(
                    folder.resolve("$PACKAGE/A.class").toFile() to Status.REMOVED
                )
            )
            .build()
        invocation = TransformTestHelper.invocationBuilder()
            .setContext(context)
            .setInputs(setOf(dirInput))
            .setTransformOutputProvider(outputProvider)
            .setIncremental(true)
            .build()
        transform.transform(invocation)

        val dexA = FileUtils.find(out.toFile(), "B.dex").get()!!
        assertThat(dexA.toPath().resolveSibling("A.dex")).doesNotExist()
        assertThat(dexA.toPath().resolveSibling("C.dex")).exists()
    }

    @Test
    @Throws(Exception::class)
    fun testDexingArtifactTransformOnlyProjectDexed() {
        val folder = tmpDir.root.toPath().resolve("dir_input")
        dirWithEmptyClasses(folder, setOf("$PACKAGE/A"))
        val projectInput = TransformTestHelper.directoryBuilder(folder.toFile())
            .setScope(QualifiedContent.Scope.PROJECT)
            .build()
        val folderExternal = tmpDir.root.toPath().resolve("external")
        dirWithEmptyClasses(folderExternal, setOf("$PACKAGE/B"))
        val externalInput = TransformTestHelper.directoryBuilder(folderExternal.toFile())
            .setScope(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
            .build()

        val invocation = TransformTestHelper.invocationBuilder()
            .setContext(context)
            .setTransformOutputProvider(outputProvider)
            .setInputs(setOf(projectInput, externalInput))
            .setIncremental(false)
            .build()

        val transform = DexArchiveBuilderTransformBuilder()
            .setAndroidJarClasspath(FakeFileCollection())
            .setDexOptions(DefaultDexOptions())
            .setMessageReceiver(NoOpMessageReceiver())
            .setErrorFormatMode(SyncOptions.ErrorFormatMode.HUMAN_READABLE)
            .setUserLevelCache(userCache)
            .setMinSdkVersion(21)
            .setDexer(dexerTool!!)
            .setUseGradleWorkers(false)
            .setInBufferSize(10)
            .setOutBufferSize(10)
            .setIsDebuggable(true)
            .setJava8LangSupportType(VariantScope.Java8LangSupport.UNUSED)
            .setProjectVariant("myVariant")
            .setIncludeFeaturesInScope(false)
            .setEnableDexingArtifactTransform(true)
            .createDexArchiveBuilderTransform()

        transform.transform(invocation)

        val dex = FileUtils.find(out.toFile(), "A.dex").get()!!
        assertThat(dex.toPath().resolveSibling("A.dex")).exists()
        assertThat(dex.toPath().resolveSibling("B.dex")).doesNotExist()
    }

    private fun getTransform(
        userCache: FileCache?,
        minSdkVersion: Int = 1,
        isDebuggable: Boolean = true,
        java8Support: VariantScope.Java8LangSupport = VariantScope.Java8LangSupport.UNUSED
    ): DexArchiveBuilderTransform {

        return DexArchiveBuilderTransformBuilder()
            .setAndroidJarClasspath(FakeFileCollection())
            .setDexOptions(DefaultDexOptions())
            .setMessageReceiver(NoOpMessageReceiver())
            .setErrorFormatMode(SyncOptions.ErrorFormatMode.HUMAN_READABLE)
            .setUserLevelCache(userCache)
            .setMinSdkVersion(minSdkVersion)
            .setDexer(dexerTool!!)
            .setUseGradleWorkers(true)
            .setInBufferSize(10)
            .setOutBufferSize(10)
            .setIsDebuggable(isDebuggable)
            .setJava8LangSupportType(java8Support)
            .setProjectVariant("myVariant")
            .setIncludeFeaturesInScope(false)
            .createDexArchiveBuilderTransform()
    }

    private fun cacheEntriesCount(cacheDir: File): Int {
        val files = cacheDir.listFiles(FileFilter { it.isDirectory })
        return files!!.size

    }

    @Throws(Exception::class)
    private fun getDirInput(path: Path, classes: Collection<String>): TransformInput {
        dirWithEmptyClasses(path, classes)
        return getDirInput(path)
    }

    @Throws(IOException::class)
    private fun getDirInput(path: Path): TransformInput {
        return TransformTestHelper.directoryBuilder(path.toFile())
            .setContentType(QualifiedContent.DefaultContentType.CLASSES)
            .setScope(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
            .putChangedFiles(
                path.toFile().walkTopDown()
                    .filter { it.isFile && it.extension == SdkConstants.EXT_CLASS }.associate { it to Status.ADDED }
            )
            .build()
    }

    @Throws(Exception::class)
    private fun getJarInput(path: Path, classes: Collection<String>): TransformInput {
        jarWithEmptyClasses(path, classes)
        return getJarInput(path)
    }

    @Throws(Exception::class)
    private fun getJarInput(path: Path): TransformInput {
        return TransformTestHelper.singleJarBuilder(path.toFile())
            .setScopes(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
            .setContentTypes(QualifiedContent.DefaultContentType.CLASSES)
            .setStatus(Status.ADDED)
            .build()
    }

    private fun checkCache() {
        val entriesCount = cacheEntriesCount(userCache!!.cacheDirectory)
        assertThat(entriesCount).named("Cache entry count").isEqualTo(expectedCacheEntryCount)
        // Misses occurs when filling the cache
        assertThat(FileCacheTestUtils.getMisses(userCache!!))
            .named("Cache misses")
            .isEqualTo(expectedCacheMisses)
    }

    companion object {

        @JvmStatic
        @Parameterized.Parameters
        fun setups(): Iterable<Array<DexerTool>> {
            return setOf(arrayOf(DexerTool.DX), arrayOf(DexerTool.D8))
        }

        private val PACKAGE = "com/example/tools"
    }

}
