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

package com.android.build.gradle.tasks

import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.build.gradle.internal.fixtures.FakeNoOpAnalyticsService
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.build.gradle.internal.profile.AnalyticsService
import com.google.common.base.Charsets
import com.google.common.io.Files
import com.google.common.truth.Truth.assertThat
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class JavaPreCompileTaskTest {

    private lateinit var project: Project
    private lateinit var outputFile: File

    @Before
    fun setUp() {
        project = ProjectBuilder.builder().withProjectDir(temporaryFolder.newFolder()).build()
        outputFile = temporaryFolder.root.resolve("outputFile")
    }

    @Test
    fun `no annotation processors are present`() {
        getWorkAction(
            getAnnotationProcessorArtifacts(jar, directory, nonJarFile),
            emptyList(),
            outputFile
        ).execute()

        assertThat(getAnnotationProcessorList()).isEmpty()
    }

    @Test
    fun `annotation processors are present`() {
        getWorkAction(
            getAnnotationProcessorArtifacts(
                jarWithAnnotationProcessor,
                directoryWithAnnotationProcessor,
                jar,
                directory,
                nonJarFile
            ),
            emptyList(),
            outputFile
        ).execute()

        assertThat(getAnnotationProcessorList()).containsExactly(
            jarWithAnnotationProcessor.name,
            directoryWithAnnotationProcessor.name
        )
    }

    @Test
    fun `annotation processor class names are specified`() {
        getWorkAction(
            getAnnotationProcessorArtifacts(
                jarWithAnnotationProcessor,
                directoryWithAnnotationProcessor,
                jar,
                directory,
                nonJarFile
            ),
            listOf(ANNOTATION_PROCESSOR_CLASS_NAME),
            outputFile
        ).execute()

        assertThat(getAnnotationProcessorList()).containsExactly(ANNOTATION_PROCESSOR_CLASS_NAME)
    }

    private fun getAnnotationProcessorArtifacts(vararg files: File): List<SerializableArtifact> {
        val configuration = project.configurations.create("annotationProcessor")
        project.dependencies.add("annotationProcessor", project.files(files))
        return configuration.incoming.artifacts.artifacts.map { SerializableArtifact(it) }
    }

    private fun getWorkAction(
        annotationProcessorArtifacts: List<SerializableArtifact>,
        annotationProcessorClassNames: List<String>,
        annotationProcessorListFile: File
    ): JavaPreCompileWorkAction {
        return object : JavaPreCompileWorkAction() {
            override fun getParameters(): JavaPreCompileParameters {
                return object : JavaPreCompileParameters() {
                    override val annotationProcessorArtifacts =
                        project.objects.listProperty(SerializableArtifact::class.java)
                            .value(annotationProcessorArtifacts)
                    override val annotationProcessorClassNames =
                        project.objects.listProperty(String::class.java)
                            .value(annotationProcessorClassNames)
                    override val annotationProcessorListFile: RegularFileProperty =
                        FakeObjectFactory.factory.fileProperty()
                            .fileValue(annotationProcessorListFile)
                    override val projectPath = FakeGradleProperty("projectName")
                    override val taskOwner = FakeGradleProperty("taskOwner")
                    override val workerKey = FakeGradleProperty("workerKey")
                    override val analyticsService: Property<AnalyticsService>
                        get() = FakeGradleProperty(FakeNoOpAnalyticsService())
                }
            }
        }
    }

    private fun getAnnotationProcessorList() = readAnnotationProcessorsFromJsonFile(outputFile).keys

    companion object {

        @JvmStatic
        @get:ClassRule
        val temporaryFolder = TemporaryFolder()

        private const val ANNOTATION_PROCESSOR_CLASS_NAME = "com.example.MyAnnotationProcessor"

        private lateinit var directory: File
        private lateinit var directoryWithAnnotationProcessor: File
        private lateinit var jar: File
        private lateinit var jarWithAnnotationProcessor: File
        private lateinit var nonJarFile: File

        @Suppress("unused")
        @BeforeClass
        @JvmStatic
        fun classSetUp() {
            directory = temporaryFolder.newFolder("directory")

            directoryWithAnnotationProcessor =
                temporaryFolder.newFolder("directoryWithAnnotationProcessor")
            val processorMetaInfFile =
                File(directoryWithAnnotationProcessor, ANNOTATION_PROCESSORS_INDICATOR_FILE)
            Files.createParentDirs(processorMetaInfFile)
            assertThat(processorMetaInfFile.createNewFile()).isTrue()

            jar = temporaryFolder.newFile("jar.jar")
            ZipOutputStream(FileOutputStream(jar)).use { }

            jarWithAnnotationProcessor = temporaryFolder.newFile("jarWithAnnotationProcessor.jar")
            ZipOutputStream(FileOutputStream(jarWithAnnotationProcessor))
                .use { out ->
                    out.putNextEntry(ZipEntry(ANNOTATION_PROCESSORS_INDICATOR_FILE))
                    out.write(ANNOTATION_PROCESSOR_CLASS_NAME.toByteArray())
                    out.closeEntry()
                }

            nonJarFile = temporaryFolder.newFile("nonJarFile.txt")
            Files.asCharSink(nonJarFile, Charsets.UTF_8).write("This is not a jar file")
        }
    }
}
