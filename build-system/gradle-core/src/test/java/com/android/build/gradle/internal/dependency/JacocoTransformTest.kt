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

import com.android.build.gradle.internal.coverage.JacocoOptions
import com.android.build.gradle.internal.fixtures.FakeConfigurableFileCollection
import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.build.gradle.internal.fixtures.FakeGradleProvider
import com.android.build.gradle.internal.fixtures.FakeGradleRegularFile
import com.android.build.gradle.internal.fixtures.FakeInputChanges
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.services.getBuildServiceName
import com.android.build.gradle.internal.transforms.testdata.ClassWithStaticField
import com.android.build.gradle.internal.transforms.testdata.SomeClass
import com.android.build.gradle.internal.transforms.testdata.SomeOtherClass
import com.android.testutils.TestInputsGenerator
import com.android.testutils.TestUtils
import com.google.common.truth.Truth.assertThat
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.work.InputChanges
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.URLClassLoader

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
        val transform = getTestTransform(testClassDir)
        val transformOutputs = FakeTransformOutputs(temporaryFolder.newFolder().absoluteFile)
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
            val loadedClass =
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
            listOf("instrumented_${testJar.nameWithoutExtension}.jar")
        )
        val expectedOutputJar =
            File(outputDir, "instrumented_${testJar.nameWithoutExtension}.jar")
        // The output jar should be larger than the original since the output jar should contain
        // the additional pre-instrumentation logic.
        assertThat(expectedOutputJar.length()).isGreaterThan(testJar.length())
    }

    private fun getTestTransform(input: File): JacocoTransform {
        return object : JacocoTransform() {
            override val inputChanges: InputChanges
                get() = FakeInputChanges(false)

            override val inputArtifact: Provider<FileSystemLocation>
                get() = FakeGradleProvider(FakeGradleRegularFile(input))

            override fun getParameters(): Params = object : Params() {
                override val jacocoVersion: Property<String>
                    get() = FakeGradleProperty(JacocoOptions.DEFAULT_VERSION)
                override val jacocoConfiguration: ConfigurableFileCollection
                    get() {
                        val jacocoJars = listOf(
                            "prebuilts/tools/common/m2/repository/org/jacoco/org.jacoco.core/0.8.3/org.jacoco.core-0.8.3.jar",
                            "prebuilts/tools/common/m2/repository/org/ow2/asm/asm/7.0/asm-7.0.jar",
                            "prebuilts/tools/common/m2/repository/org/ow2/asm/asm-commons/7.0/asm-commons-7.0.jar",
                            "prebuilts/tools/common/m2/repository/org/ow2/asm/asm-tree/7.0/asm-tree-7.0.jar"
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
        return TestUtils.resolveWorkspacePath(path).toFile()
    }
}

