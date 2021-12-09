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

package com.android.build.gradle.internal.dependency

import com.android.build.gradle.internal.fixtures.FakeConfigurableFileCollection
import com.android.build.gradle.internal.fixtures.FakeFileChange
import com.android.build.gradle.internal.fixtures.FakeGradleDirectory
import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.build.gradle.internal.fixtures.FakeGradleProvider
import com.android.build.gradle.internal.fixtures.FakeGradleRegularFile
import com.android.build.gradle.internal.fixtures.FakeInputChanges
import com.android.build.gradle.internal.fixtures.FakeTransformOutputs
import com.android.build.gradle.internal.transforms.testdata.Animal
import com.android.build.gradle.internal.transforms.testdata.CarbonForm
import com.android.build.gradle.internal.transforms.testdata.Cat
import com.android.build.gradle.internal.transforms.testdata.ClassUsingInterfaceWithDefaultMethod
import com.android.build.gradle.internal.transforms.testdata.InterfaceWithDefaultMethod
import com.android.build.gradle.internal.transforms.testdata.StandAloneClass
import com.android.build.gradle.internal.transforms.testdata.Toy
import com.android.build.gradle.options.SyncOptions
import com.android.testutils.TestClassesGenerator
import com.android.testutils.TestInputsGenerator
import com.android.testutils.TestUtils
import com.android.testutils.apk.Dex
import com.android.testutils.truth.DexSubject.assertThat
import com.android.testutils.truth.DexSubject.assertThatDex
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.FileType
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.work.ChangeType
import org.gradle.work.InputChanges
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.objectweb.asm.Type
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.pow
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** Tests for dexing artifact transform. */
class DexingTransformTest {

    @Rule
    @JvmField
    val tmp = TemporaryFolder()

    @Test
    fun testDexingJar() {
        val input = tmp.newFile("classes.jar")
        val dexingTransform = TestDexingTransform(
            FakeGradleProvider(FakeGradleRegularFile(input)),
            parameters = TestDexingTransform.TestParameters()
        )
        val outputs = FakeTransformOutputs(tmp)
        TestInputsGenerator.jarWithEmptyClasses(input.toPath(), listOf("test/A"))
        dexingTransform.transform(outputs)
        assertThatDex(outputs.outputDirectory.resolve("classes.dex"))
            .containsExactlyClassesIn(listOf("Ltest/A;"))
    }

    @Test
    fun testDexingDir() {
        val input = tmp.newFolder("classes")
        val dexingTransform =
            TestDexingTransform(
                FakeGradleProvider(FakeGradleDirectory(input)),
                parameters = TestDexingTransform.TestParameters()
            )
        val outputs = FakeTransformOutputs(tmp)

        TestInputsGenerator.dirWithEmptyClasses(input.toPath(), listOf("test/A"))
        dexingTransform.transform(outputs)

        val dexFiles = FileUtils.getAllFiles(outputs.outputDirectory)
        assertThat(dexFiles).containsExactly(outputs.outputDirectory.resolve("test/A.dex"))
        val dexClasses = dexFiles.flatMap { Dex(it).classes.keys }
        assertThat(dexClasses).containsExactly("Ltest/A;")
    }

    @Test
    fun testDexingBigJar() {
        val methodsPerClass = 200
        // more than 64K methods
        val totalMethods = (2.0.pow(16) + methodsPerClass).toInt()

        val input = tmp.newFile("classes.jar")
        ZipOutputStream(input.outputStream()).use {
            for (i in 0 until (totalMethods / methodsPerClass)) {
                val methodNames = (0 until methodsPerClass).map { index -> "foo$index:()V" }
                val classContent = TestClassesGenerator.classWithEmptyMethods(
                    "test/A$i",
                    *methodNames.toTypedArray()
                )

                it.putNextEntry(ZipEntry("test/A$i.class"))
                it.write(classContent)
                it.closeEntry()
            }
        }
        val transform =
            TestDexingTransform(
                FakeGradleProvider(FakeGradleRegularFile(input)),
                parameters = TestDexingTransform.TestParameters()
            )
        val outputs = FakeTransformOutputs(tmp)
        transform.transform(outputs)

        assertThat(
            Dex(outputs.outputDirectory.resolve("classes.dex")).classes.size +
                    Dex(outputs.outputDirectory.resolve("classes2.dex")).classes.size
        ).isEqualTo(totalMethods / methodsPerClass)
    }

    @Test
    fun testDexingWithDesugaring() {
        val input = tmp.newFolder("classes")
        val classes =
            listOf(Animal::class.java, CarbonForm::class.java, Toy::class.java, Cat::class.java)
        TestInputsGenerator.pathWithClasses(input.toPath(), classes)
        val dexingTransform = TestDexingTransform(
            FakeGradleProvider(FakeGradleDirectory(input)),
            classpath = listOf(),
            parameters = TestDexingTransform.TestParameters(
                desugaring = true
            )
        )
        val outputs = FakeTransformOutputs(tmp)
        dexingTransform.transform(outputs)

        val dexFiles = FileUtils.getAllFiles(outputs.outputDirectory)
        assertThat(dexFiles).hasSize(classes.size)
        val dexClasses = dexFiles.flatMap { Dex(it).classes.keys }
        assertThat(dexClasses).hasSize(classes.size + 1)
        assertThat(dexClasses).containsAtLeastElementsIn(classes.map { Type.getDescriptor(it) })
        val synthesizedLambdas = dexClasses.filter { it.contains("\$\$ExternalSyntheticLambda") }
        assertThat(synthesizedLambdas).hasSize(1)
    }

    @Test
    fun testDexingWithDesugaringBootclasspath() {
        val bootclasspath = tmp.newFile("bootclasspath.jar")
        TestInputsGenerator.pathWithClasses(
            bootclasspath.toPath(),
            listOf(Animal::class.java, CarbonForm::class.java, Toy::class.java)
        )

        val input = tmp.newFile("classes.jar")
        val classes = listOf(Cat::class.java)
        TestInputsGenerator.pathWithClasses(input.toPath(), classes)
        val dexingTransform = TestDexingTransform(
            FakeGradleProvider(FakeGradleRegularFile(input)),
            classpath = listOf(),
            parameters = TestDexingTransform.TestParameters(
                desugaring = true
            )
        )
        val outputs = FakeTransformOutputs(tmp)
        dexingTransform.transform(outputs)

        val dex = Dex(outputs.outputDirectory.resolve("classes.dex"))
        assertThat(dex).containsClassesIn(classes.map { Type.getDescriptor(it) })
        assertThat(dex.classes).hasSize(classes.size + 1)

        val synthesizedLambdas =
            dex.classes.keys.filter { it.contains("\$\$ExternalSyntheticLambda") }
        assertThat(synthesizedLambdas).hasSize(1)

    }

    @Test
    fun testIncrementalDexingWithDesugaring() {
        val input = tmp.newFolder("classes")
        val outputs = FakeTransformOutputs(tmp)

        val classes =
            listOf(
                InterfaceWithDefaultMethod::class.java,
                ClassUsingInterfaceWithDefaultMethod::class.java,
                StandAloneClass::class.java
            )
        TestInputsGenerator.pathWithClasses(input.toPath(), classes)
        var dexingTransform = TestDexingTransform(
            FakeGradleProvider(FakeGradleDirectory(input)),
            classpath = listOf(),
            parameters = TestDexingTransform.TestParameters(
                desugaring = true
            ),
            inputChanges = FakeInputChanges(incremental = false, inputChanges = emptyList())
        )
        dexingTransform.transform(outputs)

        var dexFiles = FileUtils.getAllFiles(outputs.outputDirectory)
        assertThat(dexFiles).hasSize(classes.size)
        var dexClasses = dexFiles.flatMap { Dex(it).classes.keys }
        assertThat(dexClasses).hasSize(classes.size + 1)
        val synthesizedDefaultMethodClass =
            Type.getDescriptor(InterfaceWithDefaultMethod::class.java)
                .replace("InterfaceWithDefaultMethod", "InterfaceWithDefaultMethod\$-CC")
        assertThat(dexClasses)
            .containsExactlyElementsIn(
                classes.map { Type.getDescriptor(it) } + synthesizedDefaultMethodClass)

        val interfaceWithDefaultMethodClass =
            TestInputsGenerator.getPath(InterfaceWithDefaultMethod::class.java)
        val classUsingInterfaceWithDefaultMethodClass =
            TestInputsGenerator.getPath(ClassUsingInterfaceWithDefaultMethod::class.java)
        val standAloneClass = TestInputsGenerator.getPath(StandAloneClass::class.java)

        val interfaceWithDefaultMethodDex =
            interfaceWithDefaultMethodClass.replace(".class", ".dex")
        val classUsingInterfaceWithDefaultMethodDex =
            classUsingInterfaceWithDefaultMethodClass.replace(".class", ".dex")
        val standAloneClassDex = standAloneClass.replace(".class", ".dex")

        val interfaceWithDefaultMethodTimestampBefore = Files.getLastModifiedTime(
            outputs.outputDirectory.resolve(interfaceWithDefaultMethodDex).toPath()
        )
        val classUsingInterfaceWithDefaultMethodTimestampBefore = Files.getLastModifiedTime(
            outputs.outputDirectory.resolve(classUsingInterfaceWithDefaultMethodDex).toPath()
        )
        val standAloneClassTimestampBefore = Files.getLastModifiedTime(
            outputs.outputDirectory.resolve(standAloneClassDex).toPath()
        )

        dexingTransform = TestDexingTransform(
            FakeGradleProvider(FakeGradleDirectory(input)),
            classpath = listOf(),
            parameters = TestDexingTransform.TestParameters(
                desugaring = true
            ),
            inputChanges = FakeInputChanges(
                incremental = true, inputChanges = listOf(
                    FakeFileChange(
                        file = input.resolve(interfaceWithDefaultMethodClass),
                        changeType = ChangeType.MODIFIED,
                        fileType = FileType.FILE,
                        normalizedPath = interfaceWithDefaultMethodClass
                    )
                )
            )
        )
        TestUtils.waitForFileSystemTick()
        dexingTransform.transform(outputs)

        dexFiles = FileUtils.getAllFiles(outputs.outputDirectory)
        assertThat(dexFiles).hasSize(classes.size)
        dexClasses = dexFiles.flatMap { Dex(it).classes.keys }
        assertThat(dexClasses).hasSize(classes.size + 1)
        assertThat(dexClasses)
            .containsExactlyElementsIn(
                classes.map { Type.getDescriptor(it) } + synthesizedDefaultMethodClass)

        val interfaceWithDefaultMethodTimestampAfter = Files.getLastModifiedTime(
            outputs.outputDirectory.resolve(interfaceWithDefaultMethodDex).toPath()
        )
        val classUsingInterfaceWithDefaultMethodTimestampAfter = Files.getLastModifiedTime(
            outputs.outputDirectory.resolve(classUsingInterfaceWithDefaultMethodDex).toPath()
        )
        val standAloneClassTimestampAfter = Files.getLastModifiedTime(
            outputs.outputDirectory.resolve(standAloneClassDex).toPath()
        )

        assertNotEquals(
            interfaceWithDefaultMethodTimestampBefore,
            interfaceWithDefaultMethodTimestampAfter
        )
        assertNotEquals(
            classUsingInterfaceWithDefaultMethodTimestampBefore,
            classUsingInterfaceWithDefaultMethodTimestampAfter
        )
        assertEquals(standAloneClassTimestampBefore, standAloneClassTimestampAfter)
    }

    private class TestDexingTransform(
        override val primaryInput: Provider<FileSystemLocation>,
        private val parameters: TestParameters,
        private val classpath: List<File> = listOf(),
        override val inputChanges: InputChanges = FakeInputChanges()
    ) : BaseDexingTransform<BaseDexingTransform.Parameters>() {

        override fun computeClasspathFiles() = classpath.map(File::toPath)

        class TestParameters(
            minSdkVersion: Int = 12,
            debuggable: Boolean = true,
            bootClasspath: List<File> = listOf(),
            desugaring: Boolean = false,
            errorFormat: SyncOptions.ErrorFormatMode = SyncOptions.ErrorFormatMode.MACHINE_PARSABLE,
        ) : Parameters {
            override var projectName = FakeGradleProperty(":test")
            override var debuggable = FakeGradleProperty(debuggable)
            override val minSdkVersion = FakeGradleProperty(minSdkVersion)
            override val bootClasspath = FakeConfigurableFileCollection(bootClasspath)
            override val errorFormat = FakeGradleProperty(errorFormat)
            override val enableDesugaring = FakeGradleProperty(desugaring)
            override val libConfiguration: Property<String> = FakeGradleProperty()
        }

        override fun getParameters(): Parameters {
            return parameters
        }

    }
}
