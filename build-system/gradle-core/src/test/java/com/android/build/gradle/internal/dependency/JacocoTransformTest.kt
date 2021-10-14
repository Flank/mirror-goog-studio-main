/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.SdkConstants
import com.android.build.gradle.internal.coverage.JacocoOptions
import com.android.build.gradle.internal.fixtures.FakeConfigurableFileCollection
import com.android.build.gradle.internal.fixtures.FakeFileChange
import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.build.gradle.internal.fixtures.FakeGradleProvider
import com.android.build.gradle.internal.fixtures.FakeGradleRegularFile
import com.android.build.gradle.internal.fixtures.FakeInputChanges
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.services.getBuildServiceName
import com.android.build.gradle.internal.transforms.testdata.Cat
import com.android.build.gradle.internal.transforms.testdata.ClassWithStaticField
import com.android.build.gradle.internal.transforms.testdata.NewClass
import com.android.build.gradle.internal.transforms.testdata.SomeOtherClass
import com.android.testutils.TestInputsGenerator
import com.android.testutils.TestUtils
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.FileType
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.work.ChangeType
import org.gradle.work.InputChanges
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.URLClassLoader
import kotlin.reflect.KClass

class JacocoTransformTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var testClassDir: File

    private lateinit var testJar: File

    private lateinit var project: Project

    @Before
    fun setup() {
        testClassDir = temporaryFolder.newFolder("instrumented_classes")
        val testClasses = listOf(ClassWithStaticField::class.java, SomeOtherClass::class.java)
        TestInputsGenerator.pathWithClasses(testClassDir.toPath(), testClasses)
        testJar = File(temporaryFolder.newFolder(), "test.jar").apply {
            writeBytes(TestInputsGenerator.jarWithClasses(testClasses))
        }

        project = ProjectBuilder.builder().withProjectDir(temporaryFolder.newFolder()).build()
        project.gradle.sharedServices.registerIfAbsent(
            getBuildServiceName(JacocoInstrumentationService::class.java),
            JacocoInstrumentationService::class.java
        ) {}
    }

    @Test
    fun `transform with classes produces expected class outputs`() {
        val fileChanges = getInitialFileChanges(testClassDir)
        val transform = getTestTransform(testClassDir, fileChanges)
        val transformOutputs = FakeTransformOutputs(temporaryFolder.newFolder("outputs"))
        transform.transform(transformOutputs)

        val getFileName = File::getName
        val outputFiles = transformOutputs.outputFiles.associateBy(getFileName)
        val instrumentedClassesDir = outputFiles[testClassDir.name]
        val instrumentedClasses = instrumentedClassesDir
            ?.walkTopDown()?.filter { it.isFile && it.extension == "class" }?.toList()
        assertThat(instrumentedClasses?.map(getFileName)).containsExactlyElementsIn(
            listOf("ClassWithStaticField.class", "SomeOtherClass.class")
        )
        val urls = instrumentedClasses !!.map { it.toURI().toURL() }.toTypedArray()
        URLClassLoader.newInstance(urls).use { urlClassLoader ->
            urlClassLoader.loadClass(ClassWithStaticField::class.java.canonicalName)
        }
    }

    @Test
    fun `transform with jar produces expected outputs`() {
        val transform = getTestTransform(testJar)
        val outputDir = temporaryFolder.newFolder()
        val transformOutputs = FakeTransformOutputs(outputDir)
        transform.transform(transformOutputs)
        assertThat(transformOutputs.outputFiles.map(File::getName)).containsExactlyElementsIn(
            listOf("instrumented_${testJar.name}")
        )
        val expectedOutputJar = File(outputDir, "instrumented_${testJar.name}")
        // The output jar should be larger than the original since the output jar should contain
        // the additional pre-instrumentation logic.
        assertThat(expectedOutputJar.length()).isGreaterThan(testJar.length())
    }

    @Test
    fun `directory added file incrementally`() {
        // Setup transform with directory with SomeOtherClass.class as input.
        val inputDir = temporaryFolder.newFolder()
        val testClasses = listOf(SomeOtherClass::class.java)
        TestInputsGenerator.pathWithClasses(inputDir.toPath(), testClasses)
        val outputDir = temporaryFolder.newFolder()
        val transformOutputs = FakeTransformOutputs(outputDir)

        getTestTransform(inputDir).transform(transformOutputs)

        val instrumentedSomeOtherClass = FileUtils.join(
            outputDir,
            getClassFilepath(SomeOtherClass::class)
        )

        // Delete the output file, it should not be added back by the transform as there are no
        // source file changes.
        instrumentedSomeOtherClass.delete()
        // Add NewClass.class to the input directory.
        TestInputsGenerator.pathWithClasses(
            inputDir.toPath(), testClasses + NewClass::class.java
        )
        val newClass = File(inputDir, getClassFilepath(NewClass::class))
        val newClassInstrumented = FileUtils.join(
            outputDir,
            "instrumented_classes",
            getClassFilepath(NewClass::class)
        )
        val transformIncremental = getTestTransform(
            inputDir,
            listOf(
                FakeFileChange(
                    newClass,
                    ChangeType.ADDED,
                    FileType.FILE,
                    newClass.toRelativeString(inputDir)
                )
            )
        )
        transformIncremental.transform(transformOutputs)

        // Check that the SomeOtherClass.class has not been overwritten during the second transform.
        assertThat(instrumentedSomeOtherClass.exists()).isFalse()
        assertThat(newClassInstrumented.length() > 0).isTrue()
    }

    private fun <T: Any> getClassFilepath(clazz: KClass<T>): String =
        "${clazz.java.canonicalName.replace('.', '/')}${SdkConstants.DOT_CLASS}"

    @Test
    fun `directory modified file incrementally`() {
        val inputDir = temporaryFolder.newFolder()
        val testClasses = listOf(SomeOtherClass::class.java, Cat::class.java)
        TestInputsGenerator.pathWithClasses(inputDir.toPath(), testClasses)

        // META-INF files are copied to the output directory and not instrumented.
        val metaInfA = File(FileUtils.join(inputDir, "META-INF"), "a.kotlin_module")
        val metaInfB = File(FileUtils.join(inputDir, "META-INF"), "b.kotlin_module")
        FileUtils.createFile(metaInfA, "Transform 1")
        FileUtils.createFile(metaInfB, "Transform 1")

        val outputDir = temporaryFolder.newFolder()
        val transformOutputs = FakeTransformOutputs(outputDir)
        getTestTransform(inputDir).transform(transformOutputs)

        val metaInfOutputDir = FileUtils.join(outputDir, "instrumented_classes", "META-INF")
        val metaInfAOutput = File(metaInfOutputDir, "a.kotlin_module")
        val metaInfBOutput = File(metaInfOutputDir, "b.kotlin_module")
        assertThat(metaInfOutputDir.listFiles().map(File::getName))
            .containsExactly("b.kotlin_module", "a.kotlin_module")
        assertThat(metaInfAOutput.exists()).isTrue()
        assertThat(metaInfBOutput.exists()).isTrue()

        val catClassFilepath = getClassFilepath(Cat::class)
        val catClass = File(inputDir, catClassFilepath)
        val outputCatClass = FileUtils.join(outputDir, "instrumented_classes", catClassFilepath)
        // Modifying the META-INF files between transforms. metaInfB will not be declared as
        // modified to the transform to confirm that the original output file is unchanged.
        FileUtils.writeToFile(metaInfA, "Transform 2")
        FileUtils.writeToFile(metaInfB, "Transform 2")

        outputCatClass.delete()
        getTestTransform(
            inputDir,
            listOf(
                FakeFileChange(
                    catClass,
                    ChangeType.MODIFIED,
                    FileType.FILE,
                    catClass.toRelativeString(inputDir)
                ),
                FakeFileChange(
                    metaInfA,
                    ChangeType.MODIFIED,
                    FileType.FILE,
                    metaInfA.toRelativeString(inputDir)
                )
            )
        ).transform(transformOutputs)

        assertThat(metaInfAOutput.readText()).isEqualTo("Transform 2")
        assertThat(metaInfBOutput.readText()).isEqualTo("Transform 1")
        assertThat(outputCatClass.exists()).isTrue()
    }

    @Test
    fun `directory removed file incrementally`() {
        val inputDir = temporaryFolder.newFolder()
        val testClasses = listOf(SomeOtherClass::class.java, Cat::class.java)
        TestInputsGenerator.pathWithClasses(inputDir.toPath(), testClasses)
        val transformNonIncremental = getTestTransform(inputDir)
        val outputDir = temporaryFolder.newFolder()
        val transformOutputs = FakeTransformOutputs(outputDir)

        transformNonIncremental.transform(transformOutputs)
        val catPath = getClassFilepath(Cat::class)
        val catClass = File(inputDir, catPath)
        val outputCatClass = FileUtils.join(outputDir, "instrumented_classes", catPath)
        val someOtherClassPath = getClassFilepath(SomeOtherClass::class)
        val someOtherClassOutput = FileUtils.join(
            outputDir, "instrumented_classes", someOtherClassPath)
        val someOtherClassOutputCreationTimestamp = someOtherClassOutput.lastModified()

        val removedCatTransform = getTestTransform(
            inputDir,
            listOf(
                FakeFileChange(
                    catClass,
                    ChangeType.REMOVED,
                    FileType.FILE,
                    catClass.toRelativeString(inputDir)
                )
            )
        )

        removedCatTransform.transform(transformOutputs)

        assertThat(someOtherClassOutputCreationTimestamp)
            .isEqualTo(someOtherClassOutput.lastModified())
        assertThat(outputCatClass.exists()).isFalse()
    }

    private fun getTestTransform(
        input: File,
        fileChanges: List<FakeFileChange> = getInitialFileChanges(input)
    ) : JacocoTransform {
        return object : JacocoTransform() {
            override val inputChanges: InputChanges
                get() = FakeInputChanges(true, fileChanges)

            override val inputArtifact: Provider<FileSystemLocation>
                get() = FakeGradleProvider(FakeGradleRegularFile(input))

            override fun getParameters(): Params = object : Params() {
                override val jacocoVersion: Property<String>
                    get() = FakeGradleProperty(JacocoOptions.DEFAULT_VERSION)
                override val jacocoConfiguration: ConfigurableFileCollection
                    get() {
                        val jacocoVersion = JacocoOptions.DEFAULT_VERSION
                        val jacocoJars = listOf(
                            "org/jacoco/org.jacoco.core/$jacocoVersion/org.jacoco.core-$jacocoVersion.jar",
                            "org/ow2/asm/asm/9.1/asm-9.1.jar",
                            "org/ow2/asm/asm-commons/9.1/asm-commons-9.1.jar",
                            "org/ow2/asm/asm-tree/9.1/asm-tree-9.1.jar"
                        ).map(this@JacocoTransformTest::getTestJar)
                        return FakeConfigurableFileCollection(jacocoJars)
                    }
                override val jacocoInstrumentationService: Property<JacocoInstrumentationService>
                    get() = FakeGradleProperty(getBuildService().get())
                override val projectName: Property<String>
                    get() = FakeGradleProperty(project.name)
            }

            private fun getBuildService(): Provider<JacocoInstrumentationService> {
                return getBuildService(project.gradle.sharedServices)
            }
        }
    }

    private fun getTestJar(path: String): File {
        return TestUtils.getLocalMavenRepoFile(path).toFile()
    }

    private fun getInitialFileChanges(directory: File): List<FakeFileChange> {
        return directory.walkTopDown()
            .filter(File::isFile)
            .map { FakeFileChange(it, ChangeType.ADDED, FileType.FILE, it.toRelativeString(directory)) }
            .toList()
    }
}

