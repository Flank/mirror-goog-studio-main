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
import com.android.build.gradle.internal.dexing.DxDexParameters
import com.android.build.gradle.internal.fixtures.FakeFileChange
import com.android.build.gradle.internal.transforms.NoOpMessageReceiver
import com.android.build.gradle.internal.transforms.testdata.Animal
import com.android.build.gradle.internal.transforms.testdata.CarbonForm
import com.android.build.gradle.internal.transforms.testdata.Cat
import com.android.build.gradle.internal.transforms.testdata.Tiger
import com.android.build.gradle.internal.transforms.testdata.Toy
import com.android.build.gradle.options.SyncOptions
import com.android.builder.dexing.DexerTool
import com.android.builder.utils.FileCache
import com.android.testutils.TestInputsGenerator
import com.android.testutils.TestUtils
import com.android.testutils.truth.MoreTruth.assertThatDex
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.gradle.work.ChangeType
import org.gradle.work.FileChange
import org.gradle.workers.WorkerExecutor
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.Mockito
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.Objects
import java.util.regex.Pattern

@RunWith(Parameterized::class)
class DexArchiveBuilderDelegateDesugaringTest(private val withIncrementalDexingV2: Boolean) {

    companion object {

        @Parameterized.Parameters(name = "incrementalDexingV2_{0}")
        @JvmStatic
        fun parameters() = arrayOf(true, false)
    }

    @Rule
    @JvmField
    var tmpDir = TemporaryFolder()

    private lateinit var out: Path
    private var desugarGraphDir: Path? = null

    @Before
    fun setUp() {
        out = tmpDir.root.toPath().resolve("out")
        Files.createDirectories(out)

        desugarGraphDir = if (withIncrementalDexingV2) {
            tmpDir.root.toPath().resolve("desugarGraphDir")
        } else {
            null
        }
    }

    private val workerExecutor: WorkerExecutor = Mockito.mock(WorkerExecutor::class.java)

    @Test
    fun testLambdas() {
        val input = tmpDir.root.toPath().resolve("input")
        TestInputsGenerator.pathWithClasses(
            input,
            setOf(
                CarbonForm::class.java, Animal::class.java, Cat::class.java, Toy::class.java
            )
        )

        getDelegate(
            projectClasses = setOf(input.toFile()),
            projectOutput = out.toFile()
        ).doProcess()

        // it should contain Cat and synthesized lambda class
        assertThatDex(getDex(Cat::class.java)).hasClassesCount(2)
    }

    internal interface WithDefault {
        @JvmDefault
        fun foo() {
        }
    }

    internal interface WithStatic {
        companion object {
            @JvmStatic
            fun bar() {
            }
        }
    }

    internal class ImplementsWithDefault : WithDefault

    internal object InvokesDefault {
        @JvmStatic
        fun main(args: Array<String>) {
            ImplementsWithDefault().foo()
        }
    }

    @Test
    fun testDefaultMethods_minApiBelow24() {
        val input = tmpDir.root.toPath().resolve("input")
        TestInputsGenerator.pathWithClasses(
            input, setOf(WithDefault::class.java, WithStatic::class.java)
        )

        getDelegate(
            minSdkVersion = 23,
            projectClasses = setOf(input.toFile()),
            projectOutput = out.toFile()
        ).doProcess()

        // it contains both original and synthesized
        assertThatDex(getDex(WithDefault::class.java)).hasClassesCount(2)
        assertThatDex(getDex(WithStatic::class.java)).hasClassesCount(2)
    }

    @Test
    fun testDefaultMethods_minApiAbove24() {
        val input = tmpDir.root.toPath().resolve("input")
        TestInputsGenerator.pathWithClasses(
            input, setOf(WithDefault::class.java, WithStatic::class.java)
        )

        getDelegate(
            minSdkVersion = 24,
            projectClasses = setOf(input.toFile()),
            projectOutput = out.toFile()
        ).doProcess()

        // it contains only the original class
        assertThatDex(getDex(WithDefault::class.java)).hasClassesCount(1)
        assertThatDex(getDex(WithStatic::class.java)).hasClassesCount(1)
    }

    @Test
    fun testIncremental_lambdaClass() {
        val input = tmpDir.root.toPath().resolve("input")
        TestInputsGenerator.pathWithClasses(
            input,
            setOf(
                CarbonForm::class.java,
                Animal::class.java,
                Cat::class.java,
                Toy::class.java
            )
        )

        getDelegate(
            projectClasses = setOf(input.toFile()),
            projectOutput = out.toFile()
        ).doProcess()

        var catDex = getDex(Cat::class.java)
        var animalDex = getDex(Animal::class.java)
        val catTimestamp = catDex.lastModified()
        val animalTimestamp = animalDex.lastModified()

        TestUtils.waitForFileSystemTick()

        getDelegate(
            projectClasses = setOf(input.toFile()),
            projectChanges = getChanges(input, ChangeType.MODIFIED, Toy::class.java),
            projectOutput = out.toFile(),
            isIncremental = true
        ).doProcess()

        catDex = getDex(Cat::class.java)
        animalDex = getDex(Animal::class.java)
        assertThat(catTimestamp).isLessThan(catDex.lastModified())
        assertThat(animalTimestamp).isEqualTo(animalDex.lastModified())
    }

    @Test
    fun testIncremental_changeSuperTypes() {
        val input = tmpDir.root.toPath().resolve("input")
        TestInputsGenerator.pathWithClasses(
            input,
            setOf(
                CarbonForm::class.java,
                Animal::class.java,
                Cat::class.java,
                Tiger::class.java,
                Toy::class.java
            )
        )

        getDelegate(
            projectClasses = setOf(input.toFile()),
            projectOutput = out.toFile()
        ).doProcess()

        var tigerDex = getDex(Tiger::class.java)
        var carbonFormDex = getDex(CarbonForm::class.java)
        val tigerTimestamp = tigerDex.lastModified()
        val carbonFormTimestamp = carbonFormDex.lastModified()

        TestUtils.waitForFileSystemTick()
        getDelegate(
            projectClasses = setOf(input.toFile()),
            projectChanges = getChanges(input, ChangeType.MODIFIED, Animal::class.java),
            projectOutput = out.toFile(),
            isIncremental = true
        ).doProcess()

        tigerDex = getDex(Tiger::class.java)
        carbonFormDex = getDex(CarbonForm::class.java)
        assertThat(tigerTimestamp).isLessThan(tigerDex.lastModified())
        assertThat(carbonFormTimestamp).isEqualTo(carbonFormDex.lastModified())

        TestUtils.waitForFileSystemTick()
        getDelegate(
            projectClasses = setOf(input.toFile()),
            projectChanges = getChanges(input, ChangeType.MODIFIED, Cat::class.java),
            projectOutput = out.toFile(),
            isIncremental = true
        ).doProcess()
        tigerDex = getDex(Tiger::class.java)
        carbonFormDex = getDex(CarbonForm::class.java)
        assertThat(tigerTimestamp).isLessThan(tigerDex.lastModified())
        assertThat(carbonFormTimestamp).isEqualTo(carbonFormDex.lastModified())
    }

    @Test
    fun test_incremental_full_incremental() {
        val input = tmpDir.root.toPath().resolve("input")
        TestInputsGenerator.pathWithClasses(
            input,
            setOf(CarbonForm::class.java, Animal::class.java)
        )

        getDelegate(
            projectClasses = setOf(input.toFile()),
            projectChanges = getChanges(
                input,
                ChangeType.ADDED,
                CarbonForm::class.java,
                Animal::class.java
            ),
            projectOutput = out.toFile(),
            isIncremental = true
        ).doProcess()

        val animalDex = getDex(Animal::class.java)

        getDelegate(
            projectClasses = setOf(input.toFile()),
            projectOutput = out.toFile(),
            isIncremental = false
        ).doProcess()

        val deletions = getChanges(input, ChangeType.REMOVED, Animal::class.java)
        deletions.forEach { it.file.delete() }
        getDelegate(
            projectClasses = setOf(input.toFile()),
            projectChanges = getChanges(input, ChangeType.REMOVED, Animal::class.java),
            projectOutput = out.toFile(),
            isIncremental = true
        ).doProcess()

        assertThat(getDex(CarbonForm::class.java)).exists()
        assertThat(animalDex).doesNotExist()
    }

    @Test
    fun test_incremental_jarAndDir() {
        val jar = tmpDir.root.toPath().resolve("input.jar")
        val input = tmpDir.root.toPath().resolve("input")
        TestInputsGenerator.pathWithClasses(
            jar,
            setOf(CarbonForm::class.java, Animal::class.java)
        )
        TestInputsGenerator.pathWithClasses(
            input, setOf(Toy::class.java, Cat::class.java, Tiger::class.java)
        )

        getDelegate(
            projectClasses = setOf(input.toFile(), jar.toFile()),
            projectOutput = out.toFile()
        ).doProcess()

        val catTimestamp = getDex(Cat::class.java).lastModified()
        val toyTimestamp = getDex(Toy::class.java).lastModified()
        TestUtils.waitForFileSystemTick()

        getDelegate(
            projectClasses = setOf(input.toFile(), jar.toFile()),
            projectChanges = setOf(
                FakeFileChange(
                    file = jar.toFile(),
                    changeType = ChangeType.MODIFIED
                )
            ),
            projectOutput = out.toFile(),
            isIncremental = true
        ).doProcess()

        assertThat(catTimestamp).isLessThan(getDex(Cat::class.java).lastModified())
        assertThat(toyTimestamp).isEqualTo(getDex(Toy::class.java).lastModified())
    }

    /** Regression test to make sure we do not add unchanged files to cache.  */
    @Test
    fun test_incremental_notChangedNotAddedToCache() {
        val jar = tmpDir.root.toPath().resolve("input.jar")
        TestInputsGenerator.pathWithClasses(
            jar,
            setOf(CarbonForm::class.java, Animal::class.java)
        )

        val inputJarHashes = tmpDir.newFile()
        val cache = FileCache.getInstanceWithSingleProcessLocking(tmpDir.newFolder())
        getDelegate(
            userCache = cache,
            externalLibClasses = setOf(jar.toFile()),
            inputJarHashes = inputJarHashes

        ).doProcess()

        val numEntries = Objects.requireNonNull(cache.cacheDirectory.list())

        val referencedJar = tmpDir.newFile("referenced.jar")
        TestInputsGenerator.jarWithEmptyClasses(referencedJar.toPath(), listOf("A"))

        getDelegate(
            isIncremental = true,
            userCache = cache,
            externalLibClasses = setOf(jar.toFile()),
            desugaringClasspath = setOf(referencedJar),
            inputJarHashes = inputJarHashes
        ).doProcess()

        assertThat(cache.cacheDirectory.list()).named("cache entries").isEqualTo(numEntries)
    }

    @Test
    fun test_duplicateClasspathEntries() {
        val lib1 = tmpDir.root.toPath().resolve("lib1.jar")
        TestInputsGenerator.pathWithClasses(
            lib1,
            setOf(ImplementsWithDefault::class.java, WithDefault::class.java)
        )
        val lib2 = tmpDir.root.toPath().resolve("lib2.jar")
        TestInputsGenerator.pathWithClasses(
            lib2,
            setOf(WithDefault::class.java)
        )
        val app = tmpDir.root.toPath().resolve("app")
        TestInputsGenerator.pathWithClasses(
            app,
            setOf(InvokesDefault::class.java)
        )

        getDelegate(
            includeAndroidJar = true,
            projectClasses = setOf(app.toFile()),
            projectOutput = out.toFile(),
            externalLibClasses = setOf(lib1.toFile(), lib2.toFile())
        ).doProcess()

        assertThatDex(getDex(InvokesDefault::class.java)).hasClassesCount(1)
    }

    /** Regression test for b/117062425.  */
    @Test
    fun test_incrementalDesugaring_removesImpactedJars() {
        val lib1 = tmpDir.root.toPath().resolve("lib1.jar")
        TestInputsGenerator.pathWithClasses(
            lib1,
            setOf(ImplementsWithDefault::class.java)
        )
        val lib2 = tmpDir.root.toPath().resolve("lib2.jar")
        TestInputsGenerator.pathWithClasses(
            lib2,
            setOf(WithDefault::class.java)
        )

        val inputJarHashes = tmpDir.newFile()
        getDelegate(
            includeAndroidJar = true,
            projectClasses = setOf(lib1.toFile(), lib2.toFile()),
            projectOutput = out.toFile(),
            inputJarHashes = inputJarHashes

        ).doProcess()
        val initialTimestamps = out.toFile().listFiles()!!.associate { it to it.lastModified() }
        TestUtils.waitForFileSystemTick()

        // Changed lib2.jar should trigger re-processing of lib1.jar.
        getDelegate(
            isIncremental = true,
            includeAndroidJar = true,
            projectClasses = setOf(lib1.toFile(), lib2.toFile()),
            projectChanges = setOf(
                FakeFileChange(
                    file = lib2.toFile(),
                    changeType = ChangeType.MODIFIED
                )
            ),
            projectOutput = out.toFile(),
            inputJarHashes = inputJarHashes
        ).doProcess()

        val newFilesAndTimestamps = out.toFile().listFiles()!!.associate { it to it.lastModified() }

        assertThat(initialTimestamps.keys).containsExactlyElementsIn(newFilesAndTimestamps.keys)
        for ((file, timestamp) in newFilesAndTimestamps) {
            // new files should be created, with new timestamps
            assertThat(timestamp).isGreaterThan(initialTimestamps.getValue(file))
        }
    }

    private fun getDelegate(
        userCache: FileCache? = null,
        minSdkVersion: Int = 15,
        isDebuggable: Boolean = true,
        includeAndroidJar: Boolean = false,
        isIncremental: Boolean = false,
        projectClasses: Set<File> = emptySet(),
        projectChanges: Set<FileChange> = emptySet(),
        projectOutput: File = tmpDir.newFolder(),
        externalLibClasses: Set<File> = emptySet(),
        desugaringClasspath: Set<File> = emptySet(),
        inputJarHashes: File = tmpDir.newFile(),
        libConfiguration: String? = null
    ): DexArchiveBuilderTaskDelegate {

        val bootClasspath = if (includeAndroidJar) {
            setOf(TestUtils.getPlatformFile("android.jar"))
        } else {
            emptySet()
        }


        return DexArchiveBuilderTaskDelegate(
            isIncremental = isIncremental,
            projectClasses = projectClasses,
            projectChangedClasses = projectChanges,
            subProjectClasses = emptySet(),
            subProjectChangedClasses = emptySet(),
            externalLibClasses = externalLibClasses,
            externalLibChangedClasses = emptySet(),
            mixedScopeClasses = emptySet(),
            mixedScopeChangedClasses = emptySet(),
            projectOutputDex = projectOutput,
            projectOutputKeepRules = null,
            subProjectOutputDex = tmpDir.newFolder(),
            subProjectOutputKeepRules = null,
            externalLibsOutputDex = tmpDir.newFolder(),
            externalLibsOutputKeepRules = null,
            mixedScopeOutputDex = tmpDir.newFolder(),
            mixedScopeOutputKeepRules = null,
            dexParams = DexParameters(
                minSdkVersion = minSdkVersion,
                debuggable = isDebuggable,
                withDesugaring = true,
                desugarBootclasspath = bootClasspath.toList(),
                desugarClasspath = desugaringClasspath.toList(),
                coreLibDesugarConfig = libConfiguration,
                errorFormatMode = SyncOptions.ErrorFormatMode.HUMAN_READABLE
            ),
            dxDexParams = DxDexParameters(
                inBufferSize = 10,
                outBufferSize = 10,
                dxNoOptimizeFlagPresent = false,
                jumboMode = true
            ),
            desugarClasspathChangedClasses = emptySet(),
            incrementalDexingV2 = withIncrementalDexingV2,
            desugarGraphDir =  desugarGraphDir?.toFile(),
            projectVariant = "myVariant",
            inputJarHashesFile = inputJarHashes,
            dexer = DexerTool.D8,
            numberOfBuckets = 2,
            useGradleWorkers = false,
            workerExecutor = workerExecutor,
            userLevelCache = userCache,
            messageReceiver = NoOpMessageReceiver()
        )
    }

    private fun getDex(clazz: Class<*>): File {
        return FileUtils.find(
            out.toFile(), Pattern.compile(".*" + clazz.simpleName + "\\.dex")
        ).single()
    }

    private fun getChanges(
        root: Path, status: ChangeType, vararg classes: Class<*>
    ): Set<FileChange> {
        return classes.map {
            val normalizedPath = TestInputsGenerator.getPath(it)
            FakeFileChange(
                file = root.resolve(normalizedPath).toFile(),
                changeType = status,
                normalizedPath = normalizedPath
            )
        }.toSet()
    }
}
