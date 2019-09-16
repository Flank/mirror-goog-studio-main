/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.api.artifact.impl

import com.android.build.api.artifact.ArtifactKind
import com.android.build.api.artifact.ArtifactType
import com.android.build.api.artifact.impl.OperationsImplTest.TestArtifactType.TEST_DIRECTORIES
import com.android.build.api.artifact.impl.OperationsImplTest.TestArtifactType.TEST_DIRECTORY
import com.android.build.api.artifact.impl.OperationsImplTest.TestArtifactType.TEST_FILE
import com.android.build.api.artifact.impl.OperationsImplTest.TestArtifactType.TEST_FILES
import com.android.build.api.artifact.impl.OperationsImplTest.TestArtifactType.TEST_REPLACABLE_DIRECTORY
import com.android.build.api.artifact.impl.OperationsImplTest.TestArtifactType.TEST_REPLACABLE_FILE
import com.android.build.api.artifact.impl.OperationsImplTest.TestArtifactType.TEST_TRANSFORMABLE_DIRECTORY
import com.android.build.api.artifact.impl.OperationsImplTest.TestArtifactType.TEST_TRANSFORMABLE_FILE
import com.google.common.truth.Truth
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskProvider
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class OperationsImplTest {

    @Suppress("ClassName")
    sealed class TestArtifactType<T : FileSystemLocation>(kind: ArtifactKind<T>) :
        ArtifactType<T>(kind) {
        override val isPublic: Boolean
            get() = false

        object TEST_FILE : TestArtifactType<RegularFile>(FILE), Single
        object TEST_FILES : TestArtifactType<RegularFile>(FILE), Multiple
        object TEST_DIRECTORY : TestArtifactType<Directory>(DIRECTORY), Single
        object TEST_DIRECTORIES : TestArtifactType<Directory>(DIRECTORY), Multiple

        object TEST_APPENDABLE_FILES : TestArtifactType<RegularFile>(FILE), Multiple, Appendable
        object TEST_APPENDABLE_DIRECTORIES : TestArtifactType<Directory>(DIRECTORY), Multiple,
            Appendable

        object TEST_TRANSFORMABLE_FILE : TestArtifactType<RegularFile>(FILE), Single, Transformable
        object TEST_TRANSFORMABLE_FILES : TestArtifactType<RegularFile>(FILE), Multiple,
            Transformable

        object TEST_TRANSFORMABLE_DIRECTORY : TestArtifactType<Directory>(DIRECTORY), Single,
            Transformable

        object TEST_TRANSFORMABLE_DIRECTORIES : TestArtifactType<Directory>(DIRECTORY), Multiple,
            Transformable

        object TEST_REPLACABLE_FILE : TestArtifactType<RegularFile>(FILE), Single, Replaceable
        object TEST_REPLACABLE_DIRECTORY : TestArtifactType<Directory>(DIRECTORY), Single,
            Replaceable
    }

    @Rule
    @JvmField val tmpDir: TemporaryFolder = TemporaryFolder()
    lateinit var project: Project
    lateinit var operations: OperationsImpl

    @Before
    fun setUp() {
        project = ProjectBuilder.builder().withProjectDir(
            tmpDir.newFolder()).build()
        operations = OperationsImpl(project.objects,"debug", project.layout.buildDirectory)

    }

    @Test
    fun testSingleFileAGPProvider() {
        abstract class AGPTask: DefaultTask() {
            @get:OutputFile abstract val outputFile: RegularFileProperty
        }
        val agpInitialized = AtomicBoolean(false)
        val agpProducer = project.tasks.register("agpProducer", AGPTask::class.java) {
            agpInitialized.set(true)
        }
        operations.setInitialProvider(TEST_FILE, agpProducer, AGPTask::outputFile)

        Truth.assertThat(agpInitialized.get()).isFalse()
        val artifactContainer = operations.getArtifactContainer(TEST_FILE)
        Truth.assertThat(agpProducer.get().outputFile.get().asFile.absolutePath).contains("test_file")
        Truth.assertThat(agpProducer.get().outputFile.get().asFile.absolutePath).doesNotContain("agpProvider")
        // final artifact value should be the agp producer task output
        Truth.assertThat(artifactContainer.get().get().asFile.absolutePath)
            .isEqualTo(agpProducer.get().outputFile.asFile.get().absolutePath)
    }

    @Test
    fun testSingleDirectoryAGPProvider() {
        abstract class AGPTask: DefaultTask() {
            @get:OutputDirectory abstract val outputFolder: DirectoryProperty
        }
        val agpInitialized = AtomicBoolean(false)
        val agpProducer = project.tasks.register("agpProducer", AGPTask::class.java) {
            agpInitialized.set(true)
        }
        operations.setInitialProvider(TEST_DIRECTORY, agpProducer, AGPTask::outputFolder)

        Truth.assertThat(agpInitialized.get()).isFalse()
        val artifactContainer = operations.getArtifactContainer(TEST_DIRECTORY)
        Truth.assertThat(agpProducer.get().outputFolder.get().asFile.absolutePath).contains("test_directory")
        Truth.assertThat(agpProducer.get().outputFolder.get().asFile.absolutePath).doesNotContain("agpProducer")
        // final artifact value should be the agp producer task output
        Truth.assertThat(artifactContainer.get().get().asFile.absolutePath)
            .isEqualTo(agpProducer.get().outputFolder.asFile.get().absolutePath)
    }

    @Test
    fun testOneAGPProviderForMultipleFileArtifactType() {
        abstract class AGPTask: DefaultTask() {
            @get:OutputFile
            abstract val outputFile: RegularFileProperty
        }
        val agpProducer = project.tasks.register("agpProducer", AGPTask::class.java)
        operations.addInitialProvider(TEST_FILES, agpProducer, AGPTask::outputFile)

        val artifactContainer = operations.getArtifactContainer(TEST_FILES)
        Truth.assertThat(artifactContainer.get().get()).hasSize(1)
        val outputFile = artifactContainer.get().get()[0]
        Truth.assertThat(outputFile.asFile.absolutePath).contains("test_files")
        // since multiple producer are possible, task name is provided in path even with a single registered producer
        Truth.assertThat(outputFile.asFile.absolutePath).contains("agpProducer")
    }

    @Test
    fun testMultipleAGPProvidersForMultipleFileArtifactType() {
        abstract class AGPTask: DefaultTask() {
            @get:OutputFile
            abstract val outputFile: RegularFileProperty
        }
        val initializedTasks = AtomicInteger(0)
        val agpProducers = mutableListOf<TaskProvider<AGPTask>>()
        for (i in 0..2) {
            val agpProducer = project.tasks.register("agpProducer$i", AGPTask::class.java) {
                initializedTasks.incrementAndGet()
            }
            agpProducers.add(agpProducer)
            operations.addInitialProvider(TEST_FILES, agpProducer, AGPTask::outputFile)
        }

        Truth.assertThat(initializedTasks.get()).isEqualTo(0)
        val artifactContainer = operations.getArtifactContainer(TEST_FILES)
        for (i in 0..2) {
            Truth.assertThat(agpProducers[i].get().outputFile.get().asFile.absolutePath).contains("test_files")
            // since multiple producer, task name is provided in path.
            Truth.assertThat(agpProducers[i].get().outputFile.get().asFile.absolutePath).contains("agpProducer$i")
        }
        Truth.assertThat(artifactContainer.get().get()).hasSize(3)
        Truth.assertThat(initializedTasks.get()).isEqualTo(3)
    }

    @Test
    fun testOneAGPProviderForMultipleDirectoryArtifactType() {
        abstract class AGPTask: DefaultTask() {
            @get:OutputDirectory
            abstract val outputDirectory: DirectoryProperty
        }
        val agpProducer = project.tasks.register("agpProducer", AGPTask::class.java)
        operations.addInitialProvider(TEST_DIRECTORIES, agpProducer, AGPTask::outputDirectory)

        val artifactContainer = operations.getArtifactContainer(TEST_DIRECTORIES)
        Truth.assertThat(artifactContainer.get().get()).hasSize(1)
        val outputFile = artifactContainer.get().get()[0]
        Truth.assertThat(outputFile.asFile.absolutePath).contains("test_directories")
        // since multiple producer are possible, task name is provided in path even with a single registered producer
        Truth.assertThat(outputFile.asFile.absolutePath).contains("agpProducer")
    }

    @Test
    fun testMultipleAGPProvidersForMultipleDirectoryArtifactType() {
        abstract class AGPTask: DefaultTask() {
            @get:OutputDirectory
            abstract val outputDirectory: DirectoryProperty
        }
        val initializedTasks = AtomicInteger(0)
        val agpProducers = mutableListOf<TaskProvider<AGPTask>>()
        for (i in 0..2) {
            val agpProducer = project.tasks.register("agpProducer$i", AGPTask::class.java) {
                initializedTasks.incrementAndGet()
            }
            agpProducers.add(agpProducer)
            operations.addInitialProvider(TEST_DIRECTORIES, agpProducer, AGPTask::outputDirectory)
        }

        Truth.assertThat(initializedTasks.get()).isEqualTo(0)
        val artifactContainer = operations.getArtifactContainer(TEST_DIRECTORIES)
        for (i in 0..2) {
            Truth.assertThat(agpProducers[i].get().outputDirectory.get().asFile.absolutePath).contains("test_directories")
            // since multiple producer, task name is provided in path.
            Truth.assertThat(agpProducers[i].get().outputDirectory.get().asFile.absolutePath).contains("agpProducer$i")
        }
        Truth.assertThat(artifactContainer.get().get()).hasSize(3)
        Truth.assertThat(initializedTasks.get()).isEqualTo(3)
    }

    @Test
    fun testFileTransform() {
        abstract class TransformTask: DefaultTask() {
            @get:InputFile abstract val inputFile: RegularFileProperty
            @get:OutputFile abstract val outputFile: RegularFileProperty
        }

        val transformerInitialized = AtomicBoolean(false)
        val transformerProvider = project.tasks.register("transformer", TransformTask::class.java) {
            transformerInitialized.set(true)
        }
        operations.transform(transformerProvider,
            TransformTask::inputFile,
            TransformTask::outputFile).on(TEST_TRANSFORMABLE_FILE)

        Truth.assertThat(transformerInitialized.get()).isFalse()

        // now registers AGP provider.
        abstract class AGPTask: DefaultTask() {
            @get:OutputFile abstract val outputFile: RegularFileProperty
        }
        val agpInitialized = AtomicBoolean(false)
        val agpProducer = project.tasks.register("agpProducer", AGPTask::class.java) {
            agpInitialized.set(true)
        }
        operations.setInitialProvider(
            TEST_TRANSFORMABLE_FILE,
            agpProducer, AGPTask::outputFile)

        Truth.assertThat(agpInitialized.get()).isFalse()
        Truth.assertThat(transformerInitialized.get()).isFalse()

        val artifactContainer = operations.getArtifactContainer(TEST_TRANSFORMABLE_FILE)
        // agp Producer should output in a folder with its task name since there are transforms registered.
        Truth.assertThat(agpProducer.get().outputFile.get().asFile.absolutePath).contains("agpProducer")
        // transform input should be in the agp producer.
        Truth.assertThat(transformerProvider.get().inputFile.asFile.get().absolutePath).contains("agpProducer")
        // transform output should have the task name in its output.
        Truth.assertThat(transformerProvider.get().outputFile.get().asFile.absolutePath).contains("transformer")
        // final artifact value should be the transformer task output
        Truth.assertThat(artifactContainer.get().get().asFile.absolutePath)
            .isEqualTo(transformerProvider.get().outputFile.asFile.get().absolutePath)
    }


    @Test
    fun testDirectoryTransform() {
        abstract class TransformTask: DefaultTask() {
            @get:InputDirectory abstract val inputFolder: DirectoryProperty
            @get:OutputDirectory abstract val outputFolder: DirectoryProperty
        }

        val transformerInitialized = AtomicBoolean(false)
        val transformerProvider = project.tasks.register("transformer", TransformTask::class.java) {
            transformerInitialized.set(true)
        }
        operations.transform(transformerProvider,
            TransformTask::inputFolder,
            TransformTask::outputFolder).on(TEST_TRANSFORMABLE_DIRECTORY)

        Truth.assertThat(transformerInitialized.get()).isFalse()

        // now registers AGP provider.
        abstract class AGPTask: DefaultTask() {
            @get:OutputDirectory abstract val outputFolder: DirectoryProperty
        }
        val agpInitialized = AtomicBoolean(false)
        val agpProducer = project.tasks.register("agpProducer", AGPTask::class.java) {
            agpInitialized.set(true)
        }
        operations.setInitialProvider(TEST_TRANSFORMABLE_DIRECTORY, agpProducer, AGPTask::outputFolder)

        Truth.assertThat(agpInitialized.get()).isFalse()
        Truth.assertThat(transformerInitialized.get()).isFalse()

        val artifactContainer = operations.getArtifactContainer(TEST_TRANSFORMABLE_DIRECTORY)
        // agp Producer should output in a folder with its task name since there are transforms registered.
        Truth.assertThat(agpProducer.get().outputFolder.get().asFile.absolutePath).contains("agpProducer")
        // transform input should be in the agp producer.
        Truth.assertThat(transformerProvider.get().inputFolder.asFile.get().absolutePath).contains("agpProducer")
        // transform output should have the task name in its output.
        Truth.assertThat(transformerProvider.get().outputFolder.get().asFile.absolutePath).contains("transformer")
        // final artifact value should be the transformer task output
        Truth.assertThat(artifactContainer.get().get().asFile.absolutePath)
            .isEqualTo(transformerProvider.get().outputFolder.asFile.get().absolutePath)
    }

    @Test
    fun testOverlappingFileTransforms() {
        abstract class TransformTask: DefaultTask() {
            @get:InputFile abstract val inputFile: RegularFileProperty
            @get:OutputFile abstract val outputFile: RegularFileProperty
        }

        // register first transformer.
        val transformerOneInitialized = AtomicBoolean(false)
        val transformerOneProvider = project.tasks.register("transformerOne", TransformTask::class.java) {
            transformerOneInitialized.set(true)
        }
        operations.transform(transformerOneProvider,
            TransformTask::inputFile,
            TransformTask::outputFile).on(TEST_TRANSFORMABLE_FILE)

        Truth.assertThat(transformerOneInitialized.get()).isFalse()

        // register second transformer
        val transformerTwoInitialized = AtomicBoolean(false)
        val transformerTwoProvider = project.tasks.register("transformerTwo", TransformTask::class.java) {
            transformerTwoInitialized.set(true)
        }
        operations.transform(transformerTwoProvider,
            TransformTask::inputFile,
            TransformTask::outputFile).on(TEST_TRANSFORMABLE_FILE)

        Truth.assertThat(transformerTwoInitialized.get()).isFalse()

        // now registers AGP provider.
        abstract class AGPTask: DefaultTask() {
            @get:OutputFile abstract val outputFile: RegularFileProperty
        }

        val agpInitialized = AtomicBoolean(false)
        val agpProducer = project.tasks.register("agpProducer", AGPTask::class.java)

        operations.setInitialProvider(TEST_TRANSFORMABLE_FILE, agpProducer, AGPTask::outputFile)
        agpProducer.configure {
            agpInitialized.set(true)
        }

        Truth.assertThat(agpInitialized.get()).isFalse()
        Truth.assertThat(transformerOneInitialized.get()).isFalse()
        Truth.assertThat(transformerTwoInitialized.get()).isFalse()

        val artifactContainer = operations.getArtifactContainer(TEST_TRANSFORMABLE_FILE)

        // final artifact value should be the transformerTwo task output
        Truth.assertThat(artifactContainer.get().get().asFile.absolutePath)
            .isEqualTo(transformerTwoProvider.get().outputFile.asFile.get().absolutePath)

        // agp Producer should output in a folder with its task name since there are transforms registered.
        Truth.assertThat(agpProducer.get().outputFile.get().asFile.absolutePath).contains("agpProducer")
        // transformOne input should be in the agp producer.
        Truth.assertThat(transformerOneProvider.get().inputFile.asFile.get().absolutePath).contains("agpProducer")
        // transformOne output should have the task name in its output.
        Truth.assertThat(transformerOneProvider.get().outputFile.get().asFile.absolutePath).contains("transformerOne")
        // transformTwo input should be TransformOne output
        Truth.assertThat(transformerTwoProvider.get().inputFile.asFile.get().absolutePath).contains("transformerOne")
        // transformTwo output should have the task name in its output.
        Truth.assertThat(transformerTwoProvider.get().outputFile.get().asFile.absolutePath).contains("transformerTwo")

        // the producers have been looked up, it should now be configured
        Truth.assertThat(agpInitialized.get()).isTrue()
        Truth.assertThat(transformerOneInitialized.get()).isTrue()
        Truth.assertThat(transformerTwoInitialized.get()).isTrue()
    }

    @Test
    fun testOverlappingDirectoryTransforms() {
        abstract class TransformTask: DefaultTask() {
            @get:InputDirectory abstract val inputFolder: DirectoryProperty
            @get:OutputDirectory abstract val outputFolder: DirectoryProperty
        }

        // register first transformer.
        val transformerOneInitialized = AtomicBoolean(false)
        val transformerOneProvider = project.tasks.register("transformerOne", TransformTask::class.java) {
            transformerOneInitialized.set(true)
        }
        operations.transform(transformerOneProvider,
            TransformTask::inputFolder,
            TransformTask::outputFolder).on(TEST_TRANSFORMABLE_DIRECTORY)

        Truth.assertThat(transformerOneInitialized.get()).isFalse()

        // register second transformer
        val transformerTwoInitialized = AtomicBoolean(false)
        val transformerTwoProvider = project.tasks.register("transformerTwo", TransformTask::class.java) {
            transformerTwoInitialized.set(true)
        }
        operations.transform(transformerTwoProvider,
            TransformTask::inputFolder,
            TransformTask::outputFolder).on(TEST_TRANSFORMABLE_DIRECTORY)

        Truth.assertThat(transformerTwoInitialized.get()).isFalse()

        // now registers AGP provider.
        abstract class AGPTask: DefaultTask() {
            @get:OutputDirectory abstract val outputFolder: DirectoryProperty
        }

        val agpInitialized = AtomicBoolean(false)
        val agpProducer = project.tasks.register("agpProducer", AGPTask::class.java)

        operations.setInitialProvider(TEST_TRANSFORMABLE_DIRECTORY, agpProducer, AGPTask::outputFolder)
        agpProducer.configure {
            agpInitialized.set(true)
        }

        Truth.assertThat(agpInitialized.get()).isFalse()
        Truth.assertThat(transformerOneInitialized.get()).isFalse()
        Truth.assertThat(transformerTwoInitialized.get()).isFalse()

        val artifactContainer = operations.getArtifactContainer(TEST_TRANSFORMABLE_DIRECTORY)

        // final artifact value should be the transformerTwo task output
        Truth.assertThat(artifactContainer.get().get().asFile.absolutePath)
            .isEqualTo(transformerTwoProvider.get().outputFolder.asFile.get().absolutePath)

        // agp Producer should output in a folder with its task name since there are transforms registered.
        Truth.assertThat(agpProducer.get().outputFolder.get().asFile.absolutePath).contains("agpProducer")
        // transformOne input should be in the agp producer.
        Truth.assertThat(transformerOneProvider.get().inputFolder.asFile.get().absolutePath).contains("agpProducer")
        // transformOne output should have the task name in its output.
        Truth.assertThat(transformerOneProvider.get().outputFolder.get().asFile.absolutePath).contains("transformerOne")
        // transformTwo input should be TransformOne output
        Truth.assertThat(transformerTwoProvider.get().inputFolder.asFile.get().absolutePath).contains("transformerOne")
        // transformTwo output should have the task name in its output.
        Truth.assertThat(transformerTwoProvider.get().outputFolder.get().asFile.absolutePath).contains("transformerTwo")

        // the producers have been looked up, it should now be configured
        Truth.assertThat(agpInitialized.get()).isTrue()
        Truth.assertThat(transformerOneInitialized.get()).isTrue()
        Truth.assertThat(transformerTwoInitialized.get()).isTrue()
    }

    @Test
    fun testFileReplace() {
        abstract class ReplaceTask: DefaultTask() {
            @get:OutputFile abstract val outputFile: RegularFileProperty
        }

        val replaceTaskInitialized = AtomicBoolean(false)
        val replaceTaskProvider = project.tasks.register("replaceTask", ReplaceTask::class.java) {
            replaceTaskInitialized.set(true)
        }
        operations.replace(replaceTaskProvider,
            ReplaceTask::outputFile).on(TEST_REPLACABLE_FILE)

        Truth.assertThat(replaceTaskInitialized.get()).isFalse()

        // now registers AGP provider.
        abstract class AGPTask: DefaultTask() {
            @get:OutputFile abstract val outputFile: RegularFileProperty
        }

        Truth.assertThat(operations.getArtifactContainer(TEST_REPLACABLE_FILE)
            .needInitialProducer().get()).isFalse()

        val agpInitialized = AtomicBoolean(false)
        val agpProducer = project.tasks.register("agpProducer", AGPTask::class.java) {
            agpInitialized.set(true)
        }
        operations.setInitialProvider(
            TEST_REPLACABLE_FILE,
            agpProducer, AGPTask::outputFile)

        Truth.assertThat(agpInitialized.get()).isFalse()
        Truth.assertThat(replaceTaskInitialized.get()).isFalse()

        val artifactContainer = operations.getArtifactContainer(TEST_REPLACABLE_FILE)
        // transform output should have the task name in its output.
        Truth.assertThat(replaceTaskProvider.get().outputFile.get().asFile.absolutePath).contains("replaceTask")
        // final artifact value should be the transformer task output
        Truth.assertThat(artifactContainer.get().get().asFile.absolutePath)
            .isEqualTo(replaceTaskProvider.get().outputFile.asFile.get().absolutePath)

        // agp provider should be ignored.
        Truth.assertThat(agpInitialized.get()).isFalse()
        Truth.assertThat(replaceTaskInitialized.get()).isTrue()
    }

    @Test
    fun testDirectoryReplace() {
        abstract class ReplaceTask: DefaultTask() {
            @get:OutputDirectory abstract val outputFolder: DirectoryProperty
        }

        val replaceTaskInitialized = AtomicBoolean(false)
        val replaceTaskProvider = project.tasks.register("replaceTask", ReplaceTask::class.java) {
            replaceTaskInitialized.set(true)
        }
        operations.replace(replaceTaskProvider,
            ReplaceTask::outputFolder).on(TEST_REPLACABLE_DIRECTORY)

        Truth.assertThat(replaceTaskInitialized.get()).isFalse()

        // now registers AGP provider.
        abstract class AGPTask: DefaultTask() {
            @get:OutputDirectory abstract val outputFolder: DirectoryProperty
        }

        Truth.assertThat(operations.getArtifactContainer(TEST_REPLACABLE_DIRECTORY)
            .needInitialProducer().get()).isFalse()

        val agpInitialized = AtomicBoolean(false)
        val agpProducer = project.tasks.register("agpProducer", AGPTask::class.java) {
            agpInitialized.set(true)
        }
        operations.setInitialProvider(TEST_REPLACABLE_DIRECTORY, agpProducer, AGPTask::outputFolder)

        Truth.assertThat(agpInitialized.get()).isFalse()
        Truth.assertThat(replaceTaskInitialized.get()).isFalse()

        val artifactContainer = operations.getArtifactContainer(TEST_REPLACABLE_DIRECTORY)
        // transform output should have the task name in its output.
        Truth.assertThat(replaceTaskProvider.get().outputFolder.get().asFile.absolutePath).contains("replaceTask")
        // final artifact value should be the transformer task output
        Truth.assertThat(artifactContainer.get().get().asFile.absolutePath)
            .isEqualTo(replaceTaskProvider.get().outputFolder.asFile.get().absolutePath)

        // agp provider should be ignored.
        Truth.assertThat(agpInitialized.get()).isFalse()
        Truth.assertThat(replaceTaskInitialized.get()).isTrue()
    }

    @Test
    fun testOverlappingFileReplace() {
        abstract class ReplaceTask: DefaultTask() {
            @get:OutputFile abstract val outputFile: RegularFileProperty
        }

        val replaceTaskOneInitialized = AtomicBoolean(false)
        val replaceTaskOneProvider = project.tasks.register("replaceTaskOne", ReplaceTask::class.java) {
            replaceTaskOneInitialized.set(true)
        }
        operations.replace(replaceTaskOneProvider,
            ReplaceTask::outputFile).on(TEST_REPLACABLE_FILE)

        Truth.assertThat(replaceTaskOneInitialized.get()).isFalse()

        val replaceTaskTwoInitialized = AtomicBoolean(false)
        val replaceTaskTwoProvider = project.tasks.register("replaceTaskTwo", ReplaceTask::class.java) {
            replaceTaskTwoInitialized.set(true)
        }
        operations.replace(replaceTaskTwoProvider, ReplaceTask::outputFile).on(TEST_REPLACABLE_FILE)

        Truth.assertThat(replaceTaskOneInitialized.get()).isFalse()
        Truth.assertThat(replaceTaskTwoInitialized.get()).isFalse()

        Truth.assertThat(operations.getArtifactContainer(TEST_REPLACABLE_FILE).needInitialProducer().get())
            .isFalse()

        // now registers AGP provider.
        abstract class AGPTask: DefaultTask() {
            @get:OutputFile abstract val outputFile: RegularFileProperty
        }

        val agpInitialized = AtomicBoolean(false)
        val agpProducer = project.tasks.register("agpProducer", AGPTask::class.java) {
            agpInitialized.set(true)
        }
        operations.setInitialProvider(
            TEST_REPLACABLE_FILE,
            agpProducer, AGPTask::outputFile)

        Truth.assertThat(agpInitialized.get()).isFalse()
        Truth.assertThat(replaceTaskOneInitialized.get()).isFalse()
        Truth.assertThat(replaceTaskTwoInitialized.get()).isFalse()

        val artifactContainer = operations.getArtifactContainer(TEST_REPLACABLE_FILE)
        // transform output should have the task name in its output.
        Truth.assertThat(replaceTaskTwoProvider.get().outputFile.get().asFile.absolutePath).contains("replaceTaskTwo")
        // final artifact value should be the transformer task output
        Truth.assertThat(artifactContainer.get().get().asFile.absolutePath)
            .isEqualTo(replaceTaskTwoProvider.get().outputFile.asFile.get().absolutePath)

        // agp provider should be ignored, so is the fist transform.
        Truth.assertThat(agpInitialized.get()).isFalse()
        Truth.assertThat(replaceTaskOneInitialized.get()).isFalse()
        Truth.assertThat(replaceTaskTwoInitialized.get()).isTrue()
    }

    @Test
    fun testOverlappingDirectoryReplace() {
        abstract class ReplaceTask: DefaultTask() {
            @get:OutputDirectory abstract val outputFolder: DirectoryProperty
        }

        val replaceTaskOneInitialized = AtomicBoolean(false)
        val replaceTaskOneProvider = project.tasks.register("replaceTaskOne", ReplaceTask::class.java) {
            replaceTaskOneInitialized.set(true)
        }
        operations.replace(replaceTaskOneProvider,
            ReplaceTask::outputFolder).on(TEST_REPLACABLE_DIRECTORY)

        Truth.assertThat(replaceTaskOneInitialized.get()).isFalse()

        val replaceTaskTwoInitialized = AtomicBoolean(false)
        val replaceTaskTwoProvider = project.tasks.register("replaceTaskTwo", ReplaceTask::class.java) {
            replaceTaskTwoInitialized.set(true)
        }
        operations.replace(replaceTaskTwoProvider, ReplaceTask::outputFolder).on(TEST_REPLACABLE_DIRECTORY)

        Truth.assertThat(replaceTaskOneInitialized.get()).isFalse()
        Truth.assertThat(replaceTaskTwoInitialized.get()).isFalse()

        Truth.assertThat(operations.getArtifactContainer(TEST_REPLACABLE_DIRECTORY)
            .needInitialProducer().get()).isFalse()

        // now registers AGP provider.
        abstract class AGPTask: DefaultTask() {
            @get:OutputDirectory abstract val outputFolder: DirectoryProperty
        }

        val agpInitialized = AtomicBoolean(false)
        val agpProducer = project.tasks.register("agpProducer", AGPTask::class.java) {
            agpInitialized.set(true)
        }
        operations.setInitialProvider(
            TEST_REPLACABLE_DIRECTORY,
            agpProducer, AGPTask::outputFolder)

        Truth.assertThat(agpInitialized.get()).isFalse()
        Truth.assertThat(replaceTaskOneInitialized.get()).isFalse()
        Truth.assertThat(replaceTaskTwoInitialized.get()).isFalse()

        val artifactContainer = operations.getArtifactContainer(TEST_REPLACABLE_DIRECTORY)
        // transform output should have the task name in its output.
        Truth.assertThat(replaceTaskTwoProvider.get().outputFolder.get().asFile.absolutePath).contains("replaceTaskTwo")
        // final artifact value should be the transformer task output
        Truth.assertThat(artifactContainer.get().get().asFile.absolutePath)
            .isEqualTo(replaceTaskTwoProvider.get().outputFolder.asFile.get().absolutePath)

        // agp provider should be ignored, so is the fist transform.
        Truth.assertThat(agpInitialized.get()).isFalse()
        Truth.assertThat(replaceTaskOneInitialized.get()).isFalse()
        Truth.assertThat(replaceTaskTwoInitialized.get()).isTrue()
    }
}