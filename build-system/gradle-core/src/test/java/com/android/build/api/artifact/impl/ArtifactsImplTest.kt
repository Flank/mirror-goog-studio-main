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

import com.android.build.api.artifact.Artifact
import com.android.build.api.artifact.ArtifactKind
import com.android.build.api.artifact.impl.ArtifactsImplTest.TestMultipleArtifactType.TEST_APPENDABLE_DIRECTORIES
import com.android.build.api.artifact.impl.ArtifactsImplTest.TestMultipleArtifactType.TEST_APPENDABLE_FILES
import com.android.build.api.artifact.impl.ArtifactsImplTest.TestMultipleArtifactType.TEST_DIRECTORIES
import com.android.build.api.artifact.impl.ArtifactsImplTest.TestMultipleArtifactType.TEST_FILES
import com.android.build.api.artifact.impl.ArtifactsImplTest.TestMultipleArtifactType.TEST_TRANSFORMABLE_DIRECTORIES
import com.android.build.api.artifact.impl.ArtifactsImplTest.TestMultipleArtifactType.TEST_TRANSFORMABLE_FILES
import com.android.build.api.artifact.impl.ArtifactsImplTest.TestSingleArtifactType.TEST_DIRECTORY
import com.android.build.api.artifact.impl.ArtifactsImplTest.TestSingleArtifactType.TEST_FILE
import com.android.build.api.artifact.impl.ArtifactsImplTest.TestSingleArtifactType.TEST_REPLACABLE_DIRECTORY
import com.android.build.api.artifact.impl.ArtifactsImplTest.TestSingleArtifactType.TEST_REPLACABLE_FILE
import com.android.build.api.artifact.impl.ArtifactsImplTest.TestSingleArtifactType.TEST_TRANSFORMABLE_DIRECTORY
import com.android.build.api.artifact.impl.ArtifactsImplTest.TestSingleArtifactType.TEST_TRANSFORMABLE_FILE
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
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

class ArtifactsImplTest {

    @Suppress("ClassName")
    sealed class TestSingleArtifactType<T : FileSystemLocation>(
        kind: ArtifactKind<T>
    ) : Artifact.Single<T>(kind, Category.INTERMEDIATES) {

        object TEST_FILE : TestSingleArtifactType<RegularFile>(FILE)
        object TEST_DIRECTORY : TestSingleArtifactType<Directory>(DIRECTORY)
        object TEST_TRANSFORMABLE_FILE : TestSingleArtifactType<RegularFile>(FILE), Transformable
        object TEST_TRANSFORMABLE_DIRECTORY : TestSingleArtifactType<Directory>(DIRECTORY),
            Transformable
        object TEST_REPLACABLE_FILE : TestSingleArtifactType<RegularFile>(FILE), Replaceable
        object TEST_REPLACABLE_DIRECTORY : TestSingleArtifactType<Directory>(DIRECTORY),
            Replaceable
    }

    sealed class TestMultipleArtifactType<T : FileSystemLocation>(
        kind: ArtifactKind<T>
    ) : Artifact.Multiple<T>(kind, Category.INTERMEDIATES){

        object TEST_FILES : TestMultipleArtifactType<RegularFile>(FILE)
        object TEST_DIRECTORIES : TestMultipleArtifactType<Directory>(DIRECTORY)
        object TEST_APPENDABLE_FILES : TestMultipleArtifactType<RegularFile>(FILE), Appendable

        object TEST_APPENDABLE_DIRECTORIES : TestMultipleArtifactType<Directory>(DIRECTORY),
            Appendable
        object TEST_TRANSFORMABLE_FILES : TestMultipleArtifactType<RegularFile>(FILE),
            Transformable, Appendable
        object TEST_TRANSFORMABLE_DIRECTORIES : TestMultipleArtifactType<Directory>(DIRECTORY),
            Transformable
    }

    @Rule
    @JvmField val tmpDir: TemporaryFolder = TemporaryFolder()
    private lateinit var project: Project
    private lateinit var artifacts: ArtifactsImpl

    @Before
    fun setUp() {
        project = ProjectBuilder.builder().withProjectDir(
            tmpDir.newFolder()).build()
        artifacts = ArtifactsImpl(project, "debug")

    }

    @Test
    fun testEarlyFileLookup() {
        val finalVersion = artifacts.get(TEST_FILE)
        // no-one has provided it so far, so it's not present.
        Truth.assertThat(finalVersion.isPresent).isFalse()
        // now provide it.

        abstract class AGPTask: DefaultTask() {
            @get:OutputFile abstract val outputFile: RegularFileProperty
        }
        val agpInitialized = AtomicBoolean(false)
        val agpTaskProvider = project.tasks.register("agpTaskProvider", AGPTask::class.java) {
            agpInitialized.set(true)
        }
        artifacts.setInitialProvider(agpTaskProvider, AGPTask::outputFile).on(TEST_FILE)

        // now get it.
        Truth.assertThat(finalVersion.get().asFile.relativeTo(project.buildDir).path).isEqualTo(
            FileUtils.join(
                Artifact.Category.INTERMEDIATES.name.toLowerCase(),
                TEST_FILE.name().toLowerCase(),
                "debug",
                "out"))
    }

    @Test
    fun testEarlyDirectoryLookup() {
        val finalVersion = artifacts.get(TEST_DIRECTORY)
        // no-one has provided it so far, so it's not present.
        Truth.assertThat(finalVersion.isPresent).isFalse()
        // now provide it.

        abstract class AGPTask: DefaultTask() {
            @get:OutputDirectory abstract val outputFiles: DirectoryProperty
        }
        val agpInitialized = AtomicBoolean(false)
        val agpTaskProvider = project.tasks.register("agpTaskProvider", AGPTask::class.java) {
            agpInitialized.set(true)
        }
        artifacts.setInitialProvider(agpTaskProvider, AGPTask::outputFiles).on(TEST_DIRECTORY)

        // now get it.
        Truth.assertThat(finalVersion.get().asFile.relativeTo(project.buildDir).path).isEqualTo(
            FileUtils.join(
                Artifact.Category.INTERMEDIATES.name.toLowerCase(),
                TEST_DIRECTORY.name().toLowerCase(),
                "debug"))
    }

    @Test
    fun testSingleFileAGPProvider() {
        abstract class AGPTask: DefaultTask() {
            @get:OutputFile abstract val outputFile: RegularFileProperty
        }
        val agpInitialized = AtomicBoolean(false)
        val agpTaskProvider = project.tasks.register("agpTaskProvider", AGPTask::class.java) {
            agpInitialized.set(true)
        }
        artifacts.setInitialProvider(agpTaskProvider, AGPTask::outputFile).on(TEST_FILE)

        Truth.assertThat(agpInitialized.get()).isFalse()
        val artifactContainer = artifacts.getArtifactContainer(TEST_FILE)
        Truth.assertThat(agpTaskProvider.get().outputFile.get().asFile.absolutePath).contains("test_file")
        Truth.assertThat(agpTaskProvider.get().outputFile.get().asFile.absolutePath).doesNotContain("agpProvider")
        // final artifact value should be the agp producer task output
        Truth.assertThat(artifactContainer.get().get().asFile.absolutePath)
            .isEqualTo(agpTaskProvider.get().outputFile.asFile.get().absolutePath)
    }

    @Test
    fun testSingleDirectoryAGPProvider() {
        abstract class AGPTask: DefaultTask() {
            @get:OutputDirectory abstract val outputFolder: DirectoryProperty
        }
        val agpInitialized = AtomicBoolean(false)
        val agpTaskProvider = project.tasks.register("agpTaskProvider", AGPTask::class.java) {
            agpInitialized.set(true)
        }
        artifacts.setInitialProvider(agpTaskProvider, AGPTask::outputFolder).on(TEST_DIRECTORY)

        Truth.assertThat(agpInitialized.get()).isFalse()
        val artifactContainer = artifacts.getArtifactContainer(TEST_DIRECTORY)
        Truth.assertThat(agpTaskProvider.get().outputFolder.get().asFile.absolutePath).contains("test_directory")
        Truth.assertThat(agpTaskProvider.get().outputFolder.get().asFile.absolutePath).doesNotContain("agpTaskProvider")
        // final artifact value should be the agp producer task output
        Truth.assertThat(artifactContainer.get().get().asFile.absolutePath)
            .isEqualTo(agpTaskProvider.get().outputFolder.asFile.get().absolutePath)
    }

    @Test
    fun testOneAGPProviderForMultipleFileArtifactType() {
        abstract class AGPTask: DefaultTask() {
            @get:OutputFile
            abstract val outputFile: RegularFileProperty
        }
        val agpTaskProvider = project.tasks.register("agpTaskProvider", AGPTask::class.java)
        artifacts.addInitialProvider(TEST_FILES, agpTaskProvider, AGPTask::outputFile)

        val artifactContainer = artifacts.getArtifactContainer(TEST_FILES)
        Truth.assertThat(artifactContainer.get().get()).hasSize(1)
        val outputFile = artifactContainer.get().get()[0]
        Truth.assertThat(outputFile.asFile.absolutePath).contains("test_files")
        // since multiple producer are possible, task name is provided in path even with a single registered producer
        Truth.assertThat(outputFile.asFile.absolutePath).contains("agpTaskProvider")
    }

    @Test
    fun testMultipleAGPProvidersForMultipleFileArtifactType() {
        abstract class AGPTask: DefaultTask() {
            @get:OutputFile
            abstract val outputFile: RegularFileProperty
        }
        val initializedTasks = AtomicInteger(0)
        val agpTaskProviders = mutableListOf<TaskProvider<AGPTask>>()
        for (i in 0..2) {
            val agpTaskProvider = project.tasks.register("agpTaskProvider$i", AGPTask::class.java) {
                initializedTasks.incrementAndGet()
            }
            agpTaskProviders.add(agpTaskProvider)
            artifacts.addInitialProvider(TEST_FILES, agpTaskProvider, AGPTask::outputFile)
        }

        Truth.assertThat(initializedTasks.get()).isEqualTo(0)
        val artifactContainer = artifacts.getArtifactContainer(TEST_FILES)
        for (i in 0..2) {
            Truth.assertThat(agpTaskProviders[i].get().outputFile.get().asFile.absolutePath).contains("test_files")
            // since multiple producer, task name is provided in path.
            Truth.assertThat(agpTaskProviders[i].get().outputFile.get().asFile.absolutePath).contains("agpTaskProvider$i")
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
        val agpTaskProvider = project.tasks.register("agpTaskProvider", AGPTask::class.java)
        artifacts.addInitialProvider(TEST_DIRECTORIES, agpTaskProvider, AGPTask::outputDirectory)

        val artifactContainer = artifacts.getArtifactContainer(TEST_DIRECTORIES)
        Truth.assertThat(artifactContainer.get().get()).hasSize(1)
        val outputFile = artifactContainer.get().get()[0]
        Truth.assertThat(outputFile.asFile.absolutePath).contains("test_directories")
        // since multiple producer are possible, task name is provided in path even with a single registered producer
        Truth.assertThat(outputFile.asFile.absolutePath).contains("agpTaskProvider")
    }

    @Test
    fun testMultipleAGPProvidersForMultipleDirectoryArtifactType() {
        abstract class AGPTask: DefaultTask() {
            @get:OutputDirectory
            abstract val outputDirectory: DirectoryProperty
        }
        val initializedTasks = AtomicInteger(0)
        val agpTaskProviders = mutableListOf<TaskProvider<AGPTask>>()
        for (i in 0..2) {
            val agpTaskProvider = project.tasks.register("agpTaskProvider$i", AGPTask::class.java) {
                initializedTasks.incrementAndGet()
            }
            agpTaskProviders.add(agpTaskProvider)
            artifacts.addInitialProvider(TEST_DIRECTORIES, agpTaskProvider, AGPTask::outputDirectory)
        }

        Truth.assertThat(initializedTasks.get()).isEqualTo(0)
        val artifactContainer = artifacts.getArtifactContainer(TEST_DIRECTORIES)
        for (i in 0..2) {
            Truth.assertThat(agpTaskProviders[i].get().outputDirectory.get().asFile.absolutePath).contains("test_directories")
            // since multiple producer, task name is provided in path.
            Truth.assertThat(agpTaskProviders[i].get().outputDirectory.get().asFile.absolutePath).contains("agpTaskProvider$i")
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
        artifacts.use(transformerProvider)
            .wiredWithFiles(TransformTask::inputFile, TransformTask::outputFile)
            .toTransform(TEST_TRANSFORMABLE_FILE)

        Truth.assertThat(transformerInitialized.get()).isFalse()

        // now registers AGP provider.
        abstract class AGPTask: DefaultTask() {
            @get:OutputFile abstract val outputFile: RegularFileProperty
        }
        val agpInitialized = AtomicBoolean(false)
        val agpTaskProvider = project.tasks.register("agpTaskProvider", AGPTask::class.java) {
            agpInitialized.set(true)
        }
        artifacts.setInitialProvider(agpTaskProvider, AGPTask::outputFile)
            .on(TEST_TRANSFORMABLE_FILE)

        Truth.assertThat(agpInitialized.get()).isFalse()
        Truth.assertThat(transformerInitialized.get()).isFalse()

        val artifactContainer = artifacts.getArtifactContainer(TEST_TRANSFORMABLE_FILE)
        // agp Producer should output in a folder with its task name since there are transforms registered.
        Truth.assertThat(agpTaskProvider.get().outputFile.get().asFile.absolutePath).contains("agpTaskProvider")
        // transform input should be in the agp producer.
        Truth.assertThat(transformerProvider.get().inputFile.asFile.get().absolutePath).contains("agpTaskProvider")
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
        artifacts.use(transformerProvider)
            .wiredWithDirectories(TransformTask::inputFolder, TransformTask::outputFolder)
            .toTransform(TEST_TRANSFORMABLE_DIRECTORY)

        Truth.assertThat(transformerInitialized.get()).isFalse()

        // now registers AGP provider.
        abstract class AGPTask: DefaultTask() {
            @get:OutputDirectory abstract val outputFolder: DirectoryProperty
        }
        val agpInitialized = AtomicBoolean(false)
        val agpTaskProvider = project.tasks.register("agpTaskProvider", AGPTask::class.java) {
            agpInitialized.set(true)
        }
        artifacts.setInitialProvider(agpTaskProvider, AGPTask::outputFolder)
            .on(TEST_TRANSFORMABLE_DIRECTORY)

        Truth.assertThat(agpInitialized.get()).isFalse()
        Truth.assertThat(transformerInitialized.get()).isFalse()

        val artifactContainer = artifacts.getArtifactContainer(TEST_TRANSFORMABLE_DIRECTORY)
        // agp Producer should output in a folder with its task name since there are transforms registered.
        Truth.assertThat(agpTaskProvider.get().outputFolder.get().asFile.absolutePath).contains("agpTaskProvider")
        // transform input should be in the agp producer.
        Truth.assertThat(transformerProvider.get().inputFolder.asFile.get().absolutePath).contains("agpTaskProvider")
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
        artifacts.use(transformerOneProvider)
            .wiredWithFiles(TransformTask::inputFile, TransformTask::outputFile)
            .toTransform(TEST_TRANSFORMABLE_FILE)

        Truth.assertThat(transformerOneInitialized.get()).isFalse()

        // register second transformer
        val transformerTwoInitialized = AtomicBoolean(false)
        val transformerTwoProvider = project.tasks.register("transformerTwo", TransformTask::class.java) {
            transformerTwoInitialized.set(true)
        }
        artifacts.use(transformerTwoProvider)
            .wiredWithFiles(TransformTask::inputFile, TransformTask::outputFile)
            .toTransform(TEST_TRANSFORMABLE_FILE)

        Truth.assertThat(transformerTwoInitialized.get()).isFalse()

        // now registers AGP provider.
        abstract class AGPTask: DefaultTask() {
            @get:OutputFile abstract val outputFile: RegularFileProperty
        }

        val agpInitialized = AtomicBoolean(false)
        val agpTaskProvider = project.tasks.register("agpTaskProvider", AGPTask::class.java)

        artifacts.setInitialProvider(agpTaskProvider, AGPTask::outputFile)
            .on(TEST_TRANSFORMABLE_FILE)

        agpTaskProvider.configure {
            agpInitialized.set(true)
        }

        Truth.assertThat(agpInitialized.get()).isFalse()
        Truth.assertThat(transformerOneInitialized.get()).isFalse()
        Truth.assertThat(transformerTwoInitialized.get()).isFalse()

        val artifactContainer = artifacts.getArtifactContainer(TEST_TRANSFORMABLE_FILE)

        // final artifact value should be the transformerTwo task output
        Truth.assertThat(artifactContainer.get().get().asFile.absolutePath)
            .isEqualTo(transformerTwoProvider.get().outputFile.asFile.get().absolutePath)

        // agp Producer should output in a folder with its task name since there are transforms registered.
        Truth.assertThat(agpTaskProvider.get().outputFile.get().asFile.absolutePath).contains("agpTaskProvider")
        // transformOne input should be in the agp producer.
        Truth.assertThat(transformerOneProvider.get().inputFile.asFile.get().absolutePath).contains("agpTaskProvider")
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
        artifacts.use(transformerOneProvider)
            .wiredWithDirectories(TransformTask::inputFolder, TransformTask::outputFolder)
            .toTransform(TEST_TRANSFORMABLE_DIRECTORY)

        Truth.assertThat(transformerOneInitialized.get()).isFalse()

        // register second transformer
        val transformerTwoInitialized = AtomicBoolean(false)
        val transformerTwoProvider = project.tasks.register("transformerTwo", TransformTask::class.java) {
            transformerTwoInitialized.set(true)
        }
        artifacts.use(transformerTwoProvider)
            .wiredWithDirectories(TransformTask::inputFolder, TransformTask::outputFolder)
            .toTransform(TEST_TRANSFORMABLE_DIRECTORY)

        Truth.assertThat(transformerTwoInitialized.get()).isFalse()

        // now registers AGP provider.
        abstract class AGPTask: DefaultTask() {
            @get:OutputDirectory abstract val outputFolder: DirectoryProperty
        }

        val agpInitialized = AtomicBoolean(false)
        val agpTaskProvider = project.tasks.register("agpTaskProvider", AGPTask::class.java)

        artifacts.setInitialProvider(agpTaskProvider, AGPTask::outputFolder)
            .on(TEST_TRANSFORMABLE_DIRECTORY)
        agpTaskProvider.configure {
            agpInitialized.set(true)
        }

        Truth.assertThat(agpInitialized.get()).isFalse()
        Truth.assertThat(transformerOneInitialized.get()).isFalse()
        Truth.assertThat(transformerTwoInitialized.get()).isFalse()

        val artifactContainer = artifacts.getArtifactContainer(TEST_TRANSFORMABLE_DIRECTORY)

        // final artifact value should be the transformerTwo task output
        Truth.assertThat(artifactContainer.get().get().asFile.absolutePath)
            .isEqualTo(transformerTwoProvider.get().outputFolder.asFile.get().absolutePath)

        // agp Producer should output in a folder with its task name since there are transforms registered.
        Truth.assertThat(agpTaskProvider.get().outputFolder.get().asFile.absolutePath).contains("agpTaskProvider")
        // transformOne input should be in the agp producer.
        Truth.assertThat(transformerOneProvider.get().inputFolder.asFile.get().absolutePath).contains("agpTaskProvider")
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
        artifacts.use(replaceTaskProvider)
            .wiredWith(ReplaceTask::outputFile)
            .toCreate(TEST_REPLACABLE_FILE)

        Truth.assertThat(replaceTaskInitialized.get()).isFalse()

        // now registers AGP provider.
        abstract class AGPTask: DefaultTask() {
            @get:OutputFile abstract val outputFile: RegularFileProperty
        }

        Truth.assertThat(artifacts.getArtifactContainer(TEST_REPLACABLE_FILE)
            .needInitialProducer().get()).isFalse()

        val agpInitialized = AtomicBoolean(false)
        val agpTaskProvider = project.tasks.register("agpTaskProvider", AGPTask::class.java) {
            agpInitialized.set(true)
        }
        artifacts.setInitialProvider(agpTaskProvider, AGPTask::outputFile)
            .on(TEST_REPLACABLE_FILE)

        Truth.assertThat(agpInitialized.get()).isFalse()
        Truth.assertThat(replaceTaskInitialized.get()).isFalse()

        val artifactContainer = artifacts.getArtifactContainer(TEST_REPLACABLE_FILE)
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
        artifacts.use(replaceTaskProvider)
            .wiredWith(ReplaceTask::outputFolder)
            .toCreate(TEST_REPLACABLE_DIRECTORY)

        Truth.assertThat(replaceTaskInitialized.get()).isFalse()

        // now registers AGP provider.
        abstract class AGPTask: DefaultTask() {
            @get:OutputDirectory abstract val outputFolder: DirectoryProperty
        }

        Truth.assertThat(artifacts.getArtifactContainer(TEST_REPLACABLE_DIRECTORY)
            .needInitialProducer().get()).isFalse()

        val agpInitialized = AtomicBoolean(false)
        val agpTaskProvider = project.tasks.register("agpTaskProvider", AGPTask::class.java) {
            agpInitialized.set(true)
        }
        artifacts.setInitialProvider(agpTaskProvider, AGPTask::outputFolder)
            .on(TEST_REPLACABLE_DIRECTORY)

        Truth.assertThat(agpInitialized.get()).isFalse()
        Truth.assertThat(replaceTaskInitialized.get()).isFalse()

        val artifactContainer = artifacts.getArtifactContainer(TEST_REPLACABLE_DIRECTORY)
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
        artifacts.use(replaceTaskOneProvider)
            .wiredWith(ReplaceTask::outputFile)
            .toCreate(TEST_REPLACABLE_FILE)

        Truth.assertThat(replaceTaskOneInitialized.get()).isFalse()

        val replaceTaskTwoInitialized = AtomicBoolean(false)
        val replaceTaskTwoProvider = project.tasks.register("replaceTaskTwo", ReplaceTask::class.java) {
            replaceTaskTwoInitialized.set(true)
        }
        artifacts.use(replaceTaskTwoProvider)
            .wiredWith(ReplaceTask::outputFile)
            .toCreate(TEST_REPLACABLE_FILE)

        Truth.assertThat(replaceTaskOneInitialized.get()).isFalse()
        Truth.assertThat(replaceTaskTwoInitialized.get()).isFalse()

        Truth.assertThat(artifacts.getArtifactContainer(TEST_REPLACABLE_FILE).needInitialProducer().get())
            .isFalse()

        // now registers AGP provider.
        abstract class AGPTask: DefaultTask() {
            @get:OutputFile abstract val outputFile: RegularFileProperty
        }

        val agpInitialized = AtomicBoolean(false)
        val agpTaskProvider = project.tasks.register("agpTaskProvider", AGPTask::class.java) {
            agpInitialized.set(true)
        }
        artifacts.setInitialProvider(agpTaskProvider, AGPTask::outputFile)
            .on(TEST_REPLACABLE_FILE)

        Truth.assertThat(agpInitialized.get()).isFalse()
        Truth.assertThat(replaceTaskOneInitialized.get()).isFalse()
        Truth.assertThat(replaceTaskTwoInitialized.get()).isFalse()

        val artifactContainer = artifacts.getArtifactContainer(TEST_REPLACABLE_FILE)
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
        artifacts.use(replaceTaskOneProvider)
            .wiredWith(ReplaceTask::outputFolder)
            .toCreate(TEST_REPLACABLE_DIRECTORY)

        Truth.assertThat(replaceTaskOneInitialized.get()).isFalse()

        val replaceTaskTwoInitialized = AtomicBoolean(false)
        val replaceTaskTwoProvider = project.tasks.register("replaceTaskTwo", ReplaceTask::class.java) {
            replaceTaskTwoInitialized.set(true)
        }
        artifacts.use(replaceTaskTwoProvider)
            .wiredWith(ReplaceTask::outputFolder)
            .toCreate(TEST_REPLACABLE_DIRECTORY)

        Truth.assertThat(replaceTaskOneInitialized.get()).isFalse()
        Truth.assertThat(replaceTaskTwoInitialized.get()).isFalse()

        Truth.assertThat(artifacts.getArtifactContainer(TEST_REPLACABLE_DIRECTORY)
            .needInitialProducer().get()).isFalse()

        // now registers AGP provider.
        abstract class AGPTask: DefaultTask() {
            @get:OutputDirectory abstract val outputFolder: DirectoryProperty
        }

        val agpInitialized = AtomicBoolean(false)
        val agpTaskProvider = project.tasks.register("agpTaskProvider", AGPTask::class.java) {
            agpInitialized.set(true)
        }
        artifacts.setInitialProvider(agpTaskProvider, AGPTask::outputFolder)
            .on(TEST_REPLACABLE_DIRECTORY)

        Truth.assertThat(agpInitialized.get()).isFalse()
        Truth.assertThat(replaceTaskOneInitialized.get()).isFalse()
        Truth.assertThat(replaceTaskTwoInitialized.get()).isFalse()

        val artifactContainer = artifacts.getArtifactContainer(TEST_REPLACABLE_DIRECTORY)
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

    @Test
    fun testSingleFileAppend() {
        abstract class AppendTask: DefaultTask() {
            @get:OutputFile abstract val outputFile: RegularFileProperty
        }

        val appendTaskInitialized = AtomicBoolean(false)
        val appendTaskProvider = project.tasks.register("appendTask", AppendTask::class.java) {
            appendTaskInitialized.set(true)
        }
        artifacts.use(appendTaskProvider)
            .wiredWith(AppendTask::outputFile)
            .toAppendTo(TEST_APPENDABLE_FILES)

        Truth.assertThat(appendTaskInitialized.get()).isFalse()

        // now registers AGP provider.
        abstract class AGPTask: DefaultTask() {
            @get:OutputFile abstract val outputFile: RegularFileProperty
        }

        val agpInitialized = AtomicBoolean(false)
        val agpTaskProvider = project.tasks.register("agpTaskProvider", AGPTask::class.java) {
            agpInitialized.set(true)
        }
        artifacts.addInitialProvider(
            TEST_APPENDABLE_FILES,
            agpTaskProvider, AGPTask::outputFile)

        Truth.assertThat(agpInitialized.get()).isFalse()
        Truth.assertThat(appendTaskInitialized.get()).isFalse()

        val artifactContainer = artifacts.getArtifactContainer(TEST_APPENDABLE_FILES)
        // append output should have the task name in its output.
        Truth.assertThat(appendTaskProvider.get().outputFile.get().asFile.absolutePath).contains("appendTask")
        Truth.assertThat(agpTaskProvider.get().outputFile.get().asFile.absolutePath).contains("agpTaskProvider")
        // final artifact values should have all tasks output.
        Truth.assertThat(artifactContainer.get().get()).hasSize(2)

        Truth.assertThat(artifactContainer.get().get()[0].asFile.absolutePath)
            .isEqualTo(appendTaskProvider.get().outputFile.asFile.get().absolutePath)

        Truth.assertThat(artifactContainer.get().get()[1].asFile.absolutePath)
            .isEqualTo(agpTaskProvider.get().outputFile.asFile.get().absolutePath)

        // agp provider should not be ignored.
        Truth.assertThat(agpInitialized.get()).isTrue()
        Truth.assertThat(appendTaskInitialized.get()).isTrue()
    }

    @Test
    fun testMultipleFilesAppend() {
        abstract class AppendTask: DefaultTask() {
            @get:OutputFile abstract val outputFile: RegularFileProperty
        }

        for (i in 0..2) {
            val appendTaskProvider = project.tasks.register("appendTask$i", AppendTask::class.java)
            artifacts.use(appendTaskProvider)
                .wiredWith(AppendTask::outputFile)
                .toAppendTo(TEST_APPENDABLE_FILES)
        }

        // now registers AGP provider.
        abstract class AGPTask: DefaultTask() {
            @get:OutputFile abstract val outputFile: RegularFileProperty
        }

        val agpTaskProvider = project.tasks.register("agpTaskProvider", AGPTask::class.java)
        artifacts.addInitialProvider(
            TEST_APPENDABLE_FILES,
            agpTaskProvider, AGPTask::outputFile)

        val artifactContainer = artifacts.getArtifactContainer(TEST_APPENDABLE_FILES)
        // final artifact values should have all tasks output.
        Truth.assertThat(artifactContainer.get().get()).hasSize(4)

        for (i in 0..2) {
            Truth.assertThat(artifactContainer.get().get()[i].asFile.absolutePath)
                .contains("appendTask")
        }
        Truth.assertThat(artifactContainer.get().get()[3].asFile.absolutePath)
            .contains("agpTaskProvider")
    }

    @Test
    fun testSingleDirectoryAppend() {
        abstract class AppendTask: DefaultTask() {
            @get:OutputDirectory abstract val outputDirectory: DirectoryProperty
        }

        val appendTaskInitialized = AtomicBoolean(false)
        val appendTaskProvider = project.tasks.register("appendTask", AppendTask::class.java) {
            appendTaskInitialized.set(true)
        }
        artifacts.use(appendTaskProvider)
            .wiredWith(AppendTask::outputDirectory)
            .toAppendTo(TEST_APPENDABLE_DIRECTORIES)

        Truth.assertThat(appendTaskInitialized.get()).isFalse()

        // now registers AGP provider.
        abstract class AGPTask: DefaultTask() {
            @get:OutputDirectory abstract val outputDirectory: DirectoryProperty
        }

        val agpInitialized = AtomicBoolean(false)
        val agpTaskProvider = project.tasks.register("agpTaskProvider", AGPTask::class.java) {
            agpInitialized.set(true)
        }
        artifacts.addInitialProvider(
            TEST_APPENDABLE_DIRECTORIES,
            agpTaskProvider, AGPTask::outputDirectory)

        Truth.assertThat(agpInitialized.get()).isFalse()
        Truth.assertThat(appendTaskInitialized.get()).isFalse()

        val artifactContainer = artifacts.getArtifactContainer(TEST_APPENDABLE_DIRECTORIES)
        // append output should have the task name in its output.
        Truth.assertThat(appendTaskProvider.get().outputDirectory.get().asFile.absolutePath).contains("appendTask")
        Truth.assertThat(agpTaskProvider.get().outputDirectory.get().asFile.absolutePath).contains("agpTaskProvider")
        // final artifact values should have all tasks output.
        Truth.assertThat(artifactContainer.get().get()).hasSize(2)

        Truth.assertThat(artifactContainer.get().get()[0].asFile.absolutePath)
            .isEqualTo(appendTaskProvider.get().outputDirectory.asFile.get().absolutePath)

        Truth.assertThat(artifactContainer.get().get()[1].asFile.absolutePath)
            .isEqualTo(agpTaskProvider.get().outputDirectory.asFile.get().absolutePath)

        // agp provider should not be ignored.
        Truth.assertThat(agpInitialized.get()).isTrue()
        Truth.assertThat(appendTaskInitialized.get()).isTrue()
    }

    @Test
    fun testMultipleDirectoriesAppend() {
        abstract class AppendTask: DefaultTask() {
            @get:OutputDirectory abstract val outputDirectory: DirectoryProperty
        }

        for (i in 0..2) {
            val appendTaskProvider = project.tasks.register("appendTask$i", AppendTask::class.java)
            artifacts.use(appendTaskProvider)
                .wiredWith(AppendTask::outputDirectory)
                .toAppendTo(TEST_APPENDABLE_DIRECTORIES)
        }

        // now registers AGP provider.
        abstract class AGPTask: DefaultTask() {
            @get:OutputDirectory abstract val outputDirectory: DirectoryProperty
        }

        val agpTaskProvider = project.tasks.register("agpTaskProvider", AGPTask::class.java)
        artifacts.addInitialProvider(
            TEST_APPENDABLE_DIRECTORIES,
            agpTaskProvider, AGPTask::outputDirectory)

        val artifactContainer = artifacts.getArtifactContainer(TEST_APPENDABLE_DIRECTORIES)
        // final artifact values should have all tasks output.
        Truth.assertThat(artifactContainer.get().get()).hasSize(4)

        for (i in 0..2) {
            Truth.assertThat(artifactContainer.get().get()[i].asFile.absolutePath)
                .contains("appendTask")
        }
        Truth.assertThat(artifactContainer.get().get()[3].asFile.absolutePath)
            .contains("agpTaskProvider")
    }

    @Test
    fun testMultipleAGPProvidersReplacementOnMultipleFileArtifactType() {
        abstract class AGPTask: DefaultTask() {
            @get:OutputFile
            abstract val outputFile: RegularFileProperty
        }
        val initializedTasks = AtomicInteger(0)
        val agpTaskProviders = mutableListOf<TaskProvider<AGPTask>>()
        for (i in 0..2) {
            val agpTaskProvider = project.tasks.register("agpTaskProvider$i", AGPTask::class.java) {
                initializedTasks.incrementAndGet()
            }
            agpTaskProviders.add(agpTaskProvider)
            artifacts.addInitialProvider(
                TEST_TRANSFORMABLE_FILES, agpTaskProvider, AGPTask::outputFile)
        }
        Truth.assertThat(initializedTasks.get()).isEqualTo(0)

        abstract class TransformMultipleTask: DefaultTask() {
            @get:InputFiles
            abstract val inputFiles: ListProperty<RegularFile>

            @get:OutputFile
            abstract val outputFile: RegularFileProperty
        }

        val transformTask = project.tasks.register("transformTask", TransformMultipleTask::class.java)
        artifacts.use(transformTask)
            .wiredWith(TransformMultipleTask::inputFiles, TransformMultipleTask::outputFile)
            .toTransform(TEST_TRANSFORMABLE_FILES)

        val artifactContainer = artifacts.getArtifactContainer(TEST_TRANSFORMABLE_FILES)
        Truth.assertThat(artifactContainer.get().get()).hasSize(1)
        Truth.assertThat(artifactContainer.get().get()[0].asFile.absolutePath)
            .contains("transformTask")
        Truth.assertThat(transformTask.get().inputFiles.get()).hasSize(3)
        for (i in 0..2) {
            Truth.assertThat(transformTask.get().inputFiles.get()[i].asFile.absolutePath)
                .contains("agpTaskProvider$i")
        }
        Truth.assertThat(transformTask.get().outputFile.get().asFile).isEqualTo(
            artifactContainer.get().get()[0].asFile)
    }

    @Test
    fun testSuccessiveMultipleAGPProvidersReplacementOnMultipleFileArtifactType() {
        abstract class AGPTask: DefaultTask() {
            @get:OutputFile
            abstract val outputFile: RegularFileProperty
        }
        for (i in 0..2) {
            val agpTaskProvider = project.tasks.register("agpTaskProvider$i", AGPTask::class.java)
            artifacts.addInitialProvider(
                TEST_TRANSFORMABLE_FILES, agpTaskProvider, AGPTask::outputFile)
        }

        abstract class TransformMultipleTask: DefaultTask() {
            @get:InputFiles
            abstract val inputFiles: ListProperty<RegularFile>

            @get:OutputFile
            abstract val outputFile: RegularFileProperty
        }

        val transformOneTask = project.tasks.register("transformOneTask", TransformMultipleTask::class.java)
        artifacts.use(transformOneTask)
            .wiredWith(TransformMultipleTask::inputFiles, TransformMultipleTask::outputFile)
            .toTransform(TEST_TRANSFORMABLE_FILES)

        val transformTwoTask = project.tasks.register("transformTwoTask", TransformMultipleTask::class.java)
        artifacts.use(transformTwoTask)
            .wiredWith(TransformMultipleTask::inputFiles, TransformMultipleTask::outputFile)
            .toTransform(TEST_TRANSFORMABLE_FILES)

        val artifactContainer = artifacts.getArtifactContainer(TEST_TRANSFORMABLE_FILES)
        Truth.assertThat(artifactContainer.get().get()).hasSize(1)
        Truth.assertThat(artifactContainer.get().get()[0].asFile.absolutePath)
            .contains("transformTwoTask")
        Truth.assertThat(transformOneTask.get().inputFiles.get()).hasSize(3)
        for (i in 0..2) {
            Truth.assertThat(transformOneTask.get().inputFiles.get()[i].asFile.absolutePath)
                .contains("agpTaskProvider$i")
        }
        Truth.assertThat(transformOneTask.get().outputFile.get().asFile.absolutePath)
            .contains("transformOneTask")
        Truth.assertThat(transformTwoTask.get().inputFiles.get()).hasSize(1)
        Truth.assertThat(transformTwoTask.get().inputFiles.get()[0].asFile.absolutePath)
            .contains("transformOneTask")

        Truth.assertThat(transformTwoTask.get().outputFile.get().asFile).isEqualTo(
            artifactContainer.get().get()[0].asFile)
    }


    @Test
    fun testMultipleAGPProvidersReplacementOnMultipleDirectoryArtifactType() {
        abstract class AGPTask: DefaultTask() {
            @get:OutputDirectory
            abstract val outputDirectory: DirectoryProperty
        }
        val initializedTasks = AtomicInteger(0)
        for (i in 0..2) {
            val agpTaskProvider = project.tasks.register("agpTaskProvider$i", AGPTask::class.java) {
                initializedTasks.incrementAndGet()
            }
            artifacts.addInitialProvider(
                TEST_TRANSFORMABLE_DIRECTORIES, agpTaskProvider, AGPTask::outputDirectory)
        }
        Truth.assertThat(initializedTasks.get()).isEqualTo(0)

        abstract class TransformMultipleTask: DefaultTask() {
            @get:InputFiles
            abstract val inputDirectories: ListProperty<Directory>

            @get:OutputFile
            abstract val outputDirectory: DirectoryProperty
        }

        val transformTask = project.tasks.register("transformTask", TransformMultipleTask::class.java)
        artifacts.use(transformTask)
            .wiredWith(TransformMultipleTask::inputDirectories, TransformMultipleTask::outputDirectory)
            .toTransform(TEST_TRANSFORMABLE_DIRECTORIES)

        val artifactContainer = artifacts.getArtifactContainer(TEST_TRANSFORMABLE_DIRECTORIES)
        Truth.assertThat(artifactContainer.get().get()).hasSize(1)
        Truth.assertThat(artifactContainer.get().get()[0].asFile.absolutePath)
            .contains("transformTask")
        Truth.assertThat(transformTask.get().inputDirectories.get()).hasSize(3)
        for (i in 0..2) {
            Truth.assertThat(transformTask.get().inputDirectories.get()[i].asFile.absolutePath)
                .contains("agpTaskProvider$i")
        }
        Truth.assertThat(transformTask.get().outputDirectory.get().asFile).isEqualTo(
            artifactContainer.get().get()[0].asFile)
    }

    @Test
    fun testSuccessiveMultipleAGPProvidersReplacementOnMultipleDirectoryArtifactType() {
        abstract class AGPTask: DefaultTask() {
            @get:OutputDirectory
            abstract val outputDirectory: DirectoryProperty
        }
        for (i in 0..2) {
            val agpTaskProvider = project.tasks.register("agpTaskProvider$i", AGPTask::class.java)
            artifacts.addInitialProvider(
                TEST_TRANSFORMABLE_DIRECTORIES, agpTaskProvider, AGPTask::outputDirectory)
        }

        abstract class TransformMultipleTask: DefaultTask() {
            @get:InputFiles
            abstract val inputDirectories: ListProperty<Directory>

            @get:OutputFile
            abstract val outputDirectory: DirectoryProperty
        }

        val transformOneTask = project.tasks.register("transformOneTask", TransformMultipleTask::class.java)
        artifacts.use(transformOneTask)
            .wiredWith(TransformMultipleTask::inputDirectories, TransformMultipleTask::outputDirectory)
            .toTransform(TEST_TRANSFORMABLE_DIRECTORIES)

        val transformTwoTask = project.tasks.register("transformTwoTask", TransformMultipleTask::class.java)
        artifacts.use(transformTwoTask)
            .wiredWith(TransformMultipleTask::inputDirectories, TransformMultipleTask::outputDirectory)
            .toTransform(TEST_TRANSFORMABLE_DIRECTORIES)

        val artifactContainer = artifacts.getArtifactContainer(TEST_TRANSFORMABLE_DIRECTORIES)
        Truth.assertThat(artifactContainer.get().get()).hasSize(1)
        Truth.assertThat(artifactContainer.get().get()[0].asFile.absolutePath)
            .contains("transformTwoTask")
        Truth.assertThat(transformOneTask.get().inputDirectories.get()).hasSize(3)
        for (i in 0..2) {
            Truth.assertThat(transformOneTask.get().inputDirectories.get()[i].asFile.absolutePath)
                .contains("agpTaskProvider$i")
        }
        Truth.assertThat(transformOneTask.get().outputDirectory.get().asFile.absolutePath)
            .contains("transformOneTask")
        Truth.assertThat(transformTwoTask.get().inputDirectories.get()).hasSize(1)
        Truth.assertThat(transformTwoTask.get().inputDirectories.get()[0].asFile.absolutePath)
            .contains("transformOneTask")

        Truth.assertThat(transformTwoTask.get().outputDirectory.get().asFile).isEqualTo(
            artifactContainer.get().get()[0].asFile)
    }

    @Test
    fun testLateAppendReplacementOnMultipleFileArtifactType() {
        abstract class ProducerTask: DefaultTask() {
            @get:OutputFile
            abstract val outputFile: RegularFileProperty
        }
        val initializedTasks = AtomicInteger(0)
        val agpTaskProviders = mutableListOf<TaskProvider<ProducerTask>>()
        for (i in 0..2) {
            val agpTaskProvider = project.tasks.register("agpTaskProvider$i", ProducerTask::class.java) {
                initializedTasks.incrementAndGet()
            }
            agpTaskProviders.add(agpTaskProvider)
            artifacts.addInitialProvider(
                TEST_TRANSFORMABLE_FILES, agpTaskProvider, ProducerTask::outputFile)
        }
        Truth.assertThat(initializedTasks.get()).isEqualTo(0)

        abstract class TransformMultipleTask: DefaultTask() {
            @get:InputFiles
            abstract val inputFiles: ListProperty<RegularFile>

            @get:OutputFile
            abstract val outputFile: RegularFileProperty
        }

        val transformTask = project.tasks.register("transformTask", TransformMultipleTask::class.java)
        artifacts.use(transformTask)
            .wiredWith(TransformMultipleTask::inputFiles, TransformMultipleTask::outputFile)
            .toTransform(TEST_TRANSFORMABLE_FILES)

        // now add a new producer, after the transfomrAll is called. Yet the transform should get
        // this appended producer anyhow.
        val lateProducer = project.tasks.register("lateProducer", ProducerTask::class.java)
        artifacts.use(lateProducer)
            .wiredWith(ProducerTask::outputFile)
            .toAppendTo(TEST_TRANSFORMABLE_FILES)

        val artifactContainer = artifacts.getArtifactContainer(TEST_TRANSFORMABLE_FILES)
        Truth.assertThat(artifactContainer.get().get()).hasSize(1)
        Truth.assertThat(artifactContainer.get().get()[0].asFile.absolutePath)
            .contains("transformTask")
        Truth.assertThat(transformTask.get().inputFiles.get()).hasSize(4)
        for (i in 0..2) {
            Truth.assertThat(transformTask.get().inputFiles.get()[i].asFile.absolutePath)
                .contains("agpTaskProvider$i")
        }
        Truth.assertThat(transformTask.get().inputFiles.get()[3].asFile.absolutePath)
            .contains("lateProducer")
        Truth.assertThat(transformTask.get().outputFile.get().asFile).isEqualTo(
            artifactContainer.get().get()[0].asFile)
    }

    /** Regression test for bug 151076862 */
    @Test
    fun `test regular-file artifacts have a default file name`() {
        abstract class AGPTask: DefaultTask() {
            @get:OutputFile abstract val outputFile: RegularFileProperty
        }
        val agpTaskProvider = project.tasks.register("agpTaskProvider", AGPTask::class.java)
        artifacts.setInitialProvider(agpTaskProvider, AGPTask::outputFile).on(TEST_FILE)

        Truth.assertThat(artifacts.getArtifactContainer(TEST_FILE).get().get().asFile.absolutePath)
            .endsWith(
                FileUtils.join("test_file", "debug", DEFAULT_FILE_NAME_OF_REGULAR_FILE_ARTIFACTS)
            )
    }
}
