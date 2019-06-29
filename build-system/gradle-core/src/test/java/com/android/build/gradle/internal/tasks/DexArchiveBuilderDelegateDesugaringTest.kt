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

import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.Status
import com.android.build.api.transform.Status.CHANGED
import com.android.build.api.transform.Status.REMOVED
import com.android.build.api.transform.TransformException
import com.android.build.api.transform.TransformOutputProvider
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.transforms.testdata.Animal
import com.android.build.gradle.internal.transforms.testdata.CarbonForm
import com.android.build.gradle.internal.transforms.testdata.Cat
import com.android.build.gradle.internal.transforms.testdata.Tiger
import com.android.build.gradle.internal.transforms.testdata.Toy
import com.android.build.gradle.options.SyncOptions
import com.android.builder.core.DefaultDexOptions
import com.android.builder.dexing.DexerTool
import com.android.builder.utils.FileCache
import com.android.testutils.TestInputsGenerator
import com.android.testutils.TestUtils
import com.android.testutils.truth.MoreTruth
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.FileUtils
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Iterables
import com.google.common.truth.Truth.assertThat
import org.gradle.api.file.FileCollection
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.HashMap
import java.util.Objects
import java.util.regex.Pattern

class DexArchiveBuilderTransformDesugaringTest {

    @JvmField
    @Rule
    var tmpDir = TemporaryFolder()

    private lateinit var outputProvider: TransformOutputProvider
    private lateinit var out: Path

    @Before
    @Throws(IOException::class)
    fun setUp() {
        out = tmpDir.root.toPath().resolve("out")
        Files.createDirectories(out)
        outputProvider = TestTransformOutputProvider(out)
    }

    @Test
    @Throws(Exception::class)
    fun testLambdas() {
        val input = tmpDir.root.toPath().resolve("input")
        TestInputsGenerator.pathWithClasses(
            input,
            ImmutableSet.of(
                CarbonForm::class.java,
                Animal::class.java,
                Cat::class.java,
                Toy::class.java
            )
        )

        val dirInput = TransformTestHelper.directoryBuilder(input.toFile()).build()

        val invocation = TransformTestHelper.invocationBuilder()
            .setTransformOutputProvider(outputProvider)
            .addInput(dirInput)
            .setIncremental(false)
            .build()
        getTransform(null, 15, true, false).transform(invocation)

        // it should contain Cat and synthesized lambda class
        MoreTruth.assertThatDex(getDex(Cat::class.java)).hasClassesCount(2)
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
    @Throws(Exception::class)
    fun testDefaultMethods_minApiBelow24() {
        val input = tmpDir.root.toPath().resolve("input")
        TestInputsGenerator.pathWithClasses(
            input, ImmutableSet.of(WithDefault::class.java, WithStatic::class.java)
        )

        val dirInput = TransformTestHelper.directoryBuilder(input.toFile()).build()

        val invocation = TransformTestHelper.invocationBuilder()
            .setTransformOutputProvider(outputProvider)
            .addInput(dirInput)
            .setIncremental(false)
            .build()
        getTransform(null, 23, true, false).transform(invocation)

        // it contains both original and synthesized
        MoreTruth.assertThatDex(getDex(WithDefault::class.java)).hasClassesCount(2)
        MoreTruth.assertThatDex(getDex(WithStatic::class.java)).hasClassesCount(2)
    }

    @Test
    @Throws(Exception::class)
    fun testDefaultMethods_minApiAbove24() {
        val input = tmpDir.root.toPath().resolve("input")
        TestInputsGenerator.pathWithClasses(
            input, ImmutableSet.of(WithDefault::class.java, WithStatic::class.java)
        )

        val dirInput = TransformTestHelper.directoryBuilder(input.toFile()).build()

        val invocation = TransformTestHelper.invocationBuilder()
            .setTransformOutputProvider(outputProvider)
            .addInput(dirInput)
            .setIncremental(false)
            .build()
        getTransform(null, 24, true, false).transform(invocation)

        // it contains only the original class
        MoreTruth.assertThatDex(getDex(WithDefault::class.java)).hasClassesCount(1)
        MoreTruth.assertThatDex(getDex(WithStatic::class.java)).hasClassesCount(1)
    }

    @Test
    @Throws(Exception::class)
    fun testIncremental_lambdaClass() {
        val input = tmpDir.root.toPath().resolve("input")
        TestInputsGenerator.pathWithClasses(
            input,
            ImmutableSet.of(
                CarbonForm::class.java,
                Animal::class.java,
                Cat::class.java,
                Toy::class.java
            )
        )

        var dirInput = TransformTestHelper.directoryBuilder(input.toFile()).build()

        var invocation = TransformTestHelper.invocationBuilder()
            .setTransformOutputProvider(outputProvider)
            .addInput(dirInput)
            .setIncremental(false)
            .build()
        getTransform(null).transform(invocation)

        var catDex = getDex(Cat::class.java)
        var animalDex = getDex(Animal::class.java)
        val catTimestamp = catDex.lastModified()
        val animalTimestamp = animalDex.lastModified()

        TestUtils.waitForFileSystemTick()

        dirInput = TransformTestHelper.directoryBuilder(input.toFile())
            .putChangedFiles(getChangedStatusMap(input, CHANGED, Toy::class.java))
            .build()
        invocation = TransformTestHelper.invocationBuilder()
            .setIncremental(true)
            .setTransformOutputProvider(outputProvider)
            .addInput(dirInput)
            .build()

        getTransform(null).transform(invocation)
        catDex = getDex(Cat::class.java)
        animalDex = getDex(Animal::class.java)
        assertThat(catTimestamp).isLessThan(catDex.lastModified())
        assertThat(animalTimestamp).isEqualTo(animalDex.lastModified())
    }

    @Test
    @Throws(Exception::class)
    fun testIncremental_lambdaClass_removed() {
        val input = tmpDir.root.toPath().resolve("input")
        TestInputsGenerator.pathWithClasses(
            input,
            ImmutableSet.of(
                CarbonForm::class.java,
                Animal::class.java,
                Cat::class.java,
                Toy::class.java
            )
        )

        var dirInput = TransformTestHelper.directoryBuilder(input.toFile()).build()

        var invocation = TransformTestHelper.invocationBuilder()
            .setTransformOutputProvider(outputProvider)
            .addInput(dirInput)
            .setIncremental(false)
            .build()
        getTransform(null).transform(invocation)

        var catDex = getDex(Cat::class.java)
        var animalDex = getDex(Animal::class.java)
        val catTimestamp = catDex.lastModified()
        val animalTimestamp = animalDex.lastModified()

        TestUtils.waitForFileSystemTick()

        dirInput = TransformTestHelper.directoryBuilder(input.toFile())
            .putChangedFiles(getChangedStatusMap(input, REMOVED, Toy::class.java))
            .build()
        invocation = TransformTestHelper.invocationBuilder()
            .setIncremental(true)
            .setTransformOutputProvider(outputProvider)
            .addInput(dirInput)
            .build()

        val toyDex = getDex(Toy::class.java)
        getTransform(null).transform(invocation)
        catDex = getDex(Cat::class.java)
        animalDex = getDex(Animal::class.java)
        assertThat(catTimestamp).isLessThan(catDex.lastModified())
        assertThat(animalTimestamp).isEqualTo(animalDex.lastModified())

        assertThat(toyDex).doesNotExist()
    }

    @Test
    @Throws(Exception::class)
    fun testIncremental_changeSuperTypes() {
        val input = tmpDir.root.toPath().resolve("input")
        TestInputsGenerator.pathWithClasses(
            input,
            ImmutableSet.of(
                CarbonForm::class.java,
                Animal::class.java,
                Cat::class.java,
                Tiger::class.java,
                Toy::class.java
            )
        )

        var dirInput = TransformTestHelper.directoryBuilder(input.toFile()).build()

        var invocation = TransformTestHelper.invocationBuilder()
            .setTransformOutputProvider(outputProvider)
            .addInput(dirInput)
            .setIncremental(false)
            .build()
        getTransform(null).transform(invocation)

        var tigerDex = getDex(Tiger::class.java)
        var carbonFormDex = getDex(CarbonForm::class.java)
        val tigerTimestamp = tigerDex.lastModified()
        val carbonFormTimestamp = carbonFormDex.lastModified()

        dirInput = TransformTestHelper.directoryBuilder(input.toFile())
            .putChangedFiles(getChangedStatusMap(input, CHANGED, Animal::class.java))
            .build()
        invocation = TransformTestHelper.invocationBuilder()
            .setIncremental(true)
            .setTransformOutputProvider(outputProvider)
            .addInput(dirInput)
            .build()

        TestUtils.waitForFileSystemTick()
        getTransform(null).transform(invocation)
        tigerDex = getDex(Tiger::class.java)
        carbonFormDex = getDex(CarbonForm::class.java)
        assertThat(tigerTimestamp).isLessThan(tigerDex.lastModified())
        assertThat(carbonFormTimestamp).isEqualTo(carbonFormDex.lastModified())

        dirInput = TransformTestHelper.directoryBuilder(input.toFile())
            .putChangedFiles(getChangedStatusMap(input, CHANGED, Cat::class.java))
            .build()
        invocation = TransformTestHelper.invocationBuilder()
            .setIncremental(true)
            .setTransformOutputProvider(outputProvider)
            .addInput(dirInput)
            .build()

        TestUtils.waitForFileSystemTick()
        getTransform(null).transform(invocation)
        tigerDex = getDex(Tiger::class.java)
        carbonFormDex = getDex(CarbonForm::class.java)
        assertThat(tigerTimestamp).isLessThan(tigerDex.lastModified())
        assertThat(carbonFormTimestamp).isEqualTo(carbonFormDex.lastModified())
    }

    @Test
    @Throws(IOException::class, TransformException::class, InterruptedException::class)
    fun test_incremental_full_incremental() {
        val input = tmpDir.root.toPath().resolve("input")
        TestInputsGenerator.pathWithClasses(
            input,
            ImmutableSet.of<Class<*>>(CarbonForm::class.java, Animal::class.java)
        )

        var dirInput = TransformTestHelper.directoryBuilder(input.toFile())
            .putChangedFiles(
                getChangedStatusMap(
                    input, Status.ADDED, CarbonForm::class.java, Animal::class.java
                )
            )
            .build()
        var invocation = TransformTestHelper.invocationBuilder()
            .setTransformOutputProvider(outputProvider)
            .addInput(dirInput)
            .setIncremental(true)
            .build()
        getTransform(null).transform(invocation)
        val animalDex = getDex(Animal::class.java)

        dirInput = TransformTestHelper.directoryBuilder(input.toFile()).build()
        invocation = TransformTestHelper.invocationBuilder()
            .setTransformOutputProvider(outputProvider)
            .addInput(dirInput)
            .setIncremental(false)
            .build()
        getTransform(null).transform(invocation)

        dirInput = TransformTestHelper.directoryBuilder(input.toFile())
            .putChangedFiles(getChangedStatusMap(input, Status.REMOVED, Animal::class.java))
            .build()
        invocation = TransformTestHelper.invocationBuilder()
            .setTransformOutputProvider(outputProvider)
            .addInput(dirInput)
            .setIncremental(true)
            .build()
        getTransform(null).transform(invocation)
        assertThat(getDex(CarbonForm::class.java)).exists()
        assertThat(animalDex).doesNotExist()
    }

    @Test
    @Throws(IOException::class, TransformException::class, InterruptedException::class)
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

        val dirInput = TransformTestHelper.directoryBuilder(input.toFile()).build()
        var jarInput = TransformTestHelper.singleJarBuilder(jar.toFile()).build()
        var invocation = TransformTestHelper.invocationBuilder()
            .setTransformOutputProvider(outputProvider)
            .addInput(dirInput)
            .addInput(jarInput)
            .setIncremental(false)
            .build()
        getTransform(null).transform(invocation)
        val catTimestamp = getDex(Cat::class.java).lastModified()
        val toyTimestamp = getDex(Toy::class.java).lastModified()

        jarInput = TransformTestHelper.singleJarBuilder(jar.toFile())
            .setStatus(Status.CHANGED)
            .build()
        invocation = TransformTestHelper.invocationBuilder()
            .setTransformOutputProvider(outputProvider)
            .addInput(dirInput)
            .addInput(jarInput)
            .setIncremental(true)
            .build()
        TestUtils.waitForFileSystemTick()
        getTransform(null).transform(invocation)

        assertThat(catTimestamp).isLessThan(getDex(Cat::class.java).lastModified())
        assertThat(toyTimestamp).isEqualTo(getDex(Toy::class.java).lastModified())
    }

    /** Regression test to make sure we do not add unchanged files to cache.  */
    @Test
    @Throws(Exception::class)
    fun test_incremental_notChangedNotAddedToCache() {
        val jar = tmpDir.root.toPath().resolve("input.jar")
        TestInputsGenerator.pathWithClasses(
            jar,
            setOf(CarbonForm::class.java, Animal::class.java)
        )

        var jarInput = TransformTestHelper.singleJarBuilder(jar.toFile())
            .setScopes(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
            .setContentTypes(QualifiedContent.DefaultContentType.CLASSES)
            .build()
        var invocation = TransformTestHelper.invocationBuilder()
            .setTransformOutputProvider(outputProvider)
            .addInput(jarInput)
            .setIncremental(false)
            .build()
        val cache = FileCache.getInstanceWithSingleProcessLocking(tmpDir.newFolder())
        getTransform(cache).transform(invocation)
        val numEntries = Objects.requireNonNull(cache.cacheDirectory.list())

        val referencedJar = tmpDir.newFile("referenced.jar")
        TestInputsGenerator.jarWithEmptyClasses(referencedJar.toPath(), ImmutableList.of("A"))
        val referencedInput = TransformTestHelper.singleJarBuilder(referencedJar).build()

        jarInput = TransformTestHelper.singleJarBuilder(jar.toFile())
            .setStatus(Status.NOTCHANGED)
            .setScopes(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
            .setContentTypes(QualifiedContent.DefaultContentType.CLASSES)
            .build()
        invocation = TransformTestHelper.invocationBuilder()
            .setTransformOutputProvider(outputProvider)
            .addInput(jarInput)
            .addReferenceInput(referencedInput)
            .setIncremental(true)
            .build()
        getTransform(cache).transform(invocation)
        assertThat(cache.cacheDirectory.list()).named("cache entries").isEqualTo(numEntries)
    }

    @Test
    @Throws(Exception::class)
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

        val lib1Input = TransformTestHelper.singleJarBuilder(lib1.toFile())
            .setScopes(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
            .setContentTypes(QualifiedContent.DefaultContentType.CLASSES)
            .build()
        val lib2Input = TransformTestHelper.singleJarBuilder(lib2.toFile())
            .setScopes(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
            .setContentTypes(QualifiedContent.DefaultContentType.CLASSES)
            .build()
        val appInput = TransformTestHelper.directoryBuilder(app.toFile()).build()

        val invocation = TransformTestHelper.invocationBuilder()
            .setTransformOutputProvider(outputProvider)
            .addInput(lib1Input)
            .addInput(lib2Input)
            .addInput(appInput)
            .setIncremental(false)
            .build()
        getTransform(null, 15, true, true).transform(invocation)

        MoreTruth.assertThatDex(getDex(InvokesDefault::class.java)).hasClassesCount(1)
    }

    /** Regression test for b/117062425.  */
    @Test
    @Throws(Exception::class)
    fun test_incrementalDesugaringWithCaching() {
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

        val lib1Input = TransformTestHelper.singleJarBuilder(lib1.toFile())
            .setScopes(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
            .setContentTypes(QualifiedContent.DefaultContentType.CLASSES)
            .build()
        // Mimics dex that from cache for lib1.jar. Transform invocation should remove it.
        Files.createFile(out.resolve("lib1.jar.jar"))

        val lib2Input = TransformTestHelper.singleJarBuilder(lib2.toFile())
            .setScopes(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
            .setContentTypes(QualifiedContent.DefaultContentType.CLASSES)
            .setStatus(Status.CHANGED)
            .build()
        val invocation = TransformTestHelper.invocationBuilder()
            .setTransformOutputProvider(outputProvider)
            .addInput(lib1Input)
            .addInput(lib2Input)
            .setIncremental(true)
            .build()
        getTransform(null, 15, true, true).transform(invocation)

        val lib1DexOutputs = out.toFile().listFiles()!!.filter { it.name.startsWith("lib1.jar") }
        assertThat(lib1DexOutputs).hasSize(1)
    }

    private fun getTransform(
        userCache: FileCache?,
        minSdkVersion: Int = 15,
        isDebuggable: Boolean = true,
        includeAndroidJar: Boolean = false
    ): DexArchiveBuilderTransform {
        val classpath = Mockito.mock(FileCollection::class.java)
        Mockito.`when`(classpath.files)
            .thenReturn(
                if (includeAndroidJar)
                    ImmutableSet.of(TestUtils.getPlatformFile("android.jar"))
                else
                    ImmutableSet.of()
            )
        return DexArchiveBuilderTransformBuilder()
            .setAndroidJarClasspath(classpath)
            .setDexOptions(DefaultDexOptions())
            .setMessageReceiver(NoOpMessageReceiver())
            .setErrorFormatMode(SyncOptions.ErrorFormatMode.HUMAN_READABLE)
            .setUserLevelCache(userCache)
            .setMinSdkVersion(minSdkVersion)
            .setDexer(DexerTool.D8)
            .setUseGradleWorkers(false)
            .setInBufferSize(10)
            .setOutBufferSize(10)
            .setIsDebuggable(isDebuggable)
            .setJava8LangSupportType(VariantScope.Java8LangSupport.D8)
            .setProjectVariant("myVariant")
            .setIncludeFeaturesInScope(false)
            .setNumberOfBuckets(2)
            .createDexArchiveBuilderTransform()
    }

    private fun getDex(clazz: Class<*>): File {
        return Iterables.getOnlyElement(
            FileUtils.find(
                out.toFile(), Pattern.compile(".*" + clazz.simpleName + "\\.dex")
            )
        )
    }

    private fun getChangedStatusMap(
        root: Path, status: Status, vararg classes: Class<*>
    ): Map<File, Status> {
        val statusMap = HashMap<File, Status>()
        for (clazz in classes) {
            statusMap[root.resolve(TestInputsGenerator.getPath(clazz)).toFile()] = status
        }
        return statusMap
    }
}
