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

package com.android.build.api.artifact

import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskProvider
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

/**
 * Sanity tests to check API usage patterns.
 *
 * If you change this class, this could mean the public API is being changed so proceed with caution
 */
class OperationsTest {

    @Mock lateinit var operations: Operations

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
    }

    @Suppress("ClassName")
    sealed class TestArtifactType<T: FileSystemLocation>(kind: ArtifactKind<T>)
        : ArtifactType<T>(kind)  {
        override val isPublic: Boolean
            get() = false

        object TEST_FILE: TestArtifactType<RegularFile>(FILE), Single
        object TEST_FILES: TestArtifactType<RegularFile>(FILE), Multiple
        object TEST_DIRECTORY: TestArtifactType<Directory>(DIRECTORY), Single
        object TEST_DIRECTORIES: TestArtifactType<Directory>(DIRECTORY), Multiple

        object TEST_APPENDABLE_FILES: TestArtifactType<RegularFile>(FILE), Multiple, Appendable
        object TEST_APPENDABLE_DIRECTORIES: TestArtifactType<Directory>(DIRECTORY), Multiple, Appendable

        object TEST_TRANSFORMABLE_FILE: TestArtifactType<RegularFile>(FILE), Single, Transformable
        object TEST_TRANSFORMABLE_FILES: TestArtifactType<RegularFile>(FILE), Multiple, Transformable
        object TEST_TRANSFORMABLE_DIRECTORY: TestArtifactType<Directory>(DIRECTORY), Single, Transformable
        object TEST_TRANSFORMABLE_DIRECTORIES: TestArtifactType<Directory>(DIRECTORY), Multiple, Transformable

        object TEST_REPLACABLE_FILE: TestArtifactType<RegularFile>(FILE), Single, Replaceable
        object TEST_REPLACABLE_DIRECTORY: TestArtifactType<Directory>(DIRECTORY), Single, Replaceable
    }

    @Test
    fun testGet() {
        operations.get(TestArtifactType.TEST_FILE)
        operations.get(TestArtifactType.TEST_DIRECTORY)
        operations.getAll(TestArtifactType.TEST_FILES)
        operations.getAll(TestArtifactType.TEST_DIRECTORIES)
    }

    @Test
    fun  testFileAppend() {
        val appendRegularFile = getAppendRequest<RegularFile>()
        val taskProvider = getTaskProvider<FileProducerTask>()

        `when`(operations.append(taskProvider, FileProducerTask::getOutputFile))
            .thenReturn(appendRegularFile)

        // actual API
        operations.append(taskProvider, FileProducerTask::getOutputFile)
            .to(TestArtifactType.TEST_APPENDABLE_FILES)
    }

    @Test
    fun  testDirectoryAppend() {
        val appendDirectory = getAppendRequest<Directory>()
        val taskProvider = getTaskProvider<DirectoryProducerTask>()

        `when`(operations.append(taskProvider, DirectoryProducerTask::getOutputDir))
            .thenReturn(appendDirectory)

        // actual API
        operations.append(taskProvider, DirectoryProducerTask::getOutputDir)
            .to(TestArtifactType.TEST_APPENDABLE_DIRECTORIES)
    }

    @Test
    fun  testFileTransformed() {
        val transformRequest = getTransformRequest<RegularFile>()
        val taskProvider = getTaskProvider<FileProducerTask>()

        `when`(operations.transform(taskProvider,
            FileProducerTask::getInputFile,
            FileProducerTask::getOutputFile))
            .thenReturn(transformRequest)

        // actual API
        operations.transform(taskProvider, FileProducerTask::getInputFile, FileProducerTask::getOutputFile)
            .on(TestArtifactType.TEST_TRANSFORMABLE_FILE)
    }

    @Test
    fun  testDirectoryTransformed() {
        val transformRequest = getTransformRequest<Directory>()
        val taskProvider = getTaskProvider<DirectoryProducerTask>()

        `when`(operations.transform(taskProvider,
            DirectoryProducerTask::getInputDir,
            DirectoryProducerTask::getOutputDir))
            .thenReturn(transformRequest)

        // actual API
        operations.transform(taskProvider,
            DirectoryProducerTask::getInputDir,
            DirectoryProducerTask::getOutputDir)
            .on(TestArtifactType.TEST_TRANSFORMABLE_DIRECTORY)
    }

    @Test
    fun  testFilesCombined() {
        val combineRequest = object: MultipleTransformRequest<RegularFile> {
            override fun <ARTIFACT_TYPE> on(type: ARTIFACT_TYPE)
                    where ARTIFACT_TYPE : ArtifactType<RegularFile>,
                          ARTIFACT_TYPE : ArtifactType.Transformable,
                          ARTIFACT_TYPE : ArtifactType.Multiple {}
        }
        val taskProvider = getTaskProvider<FilesTransformerTask>()

        `when`(operations.transformAll(taskProvider,
            FilesTransformerTask::getInputFiles,
            FilesTransformerTask::getOutputFile))
            .thenReturn(combineRequest)

        // actual API
        operations.transformAll(taskProvider,
            FilesTransformerTask::getInputFiles,
            FilesTransformerTask::getOutputFile)
            .on(TestArtifactType.TEST_TRANSFORMABLE_FILES)
    }

    @Test
    fun  testDirectoriesCombined() {
        val combineRequest = object: MultipleTransformRequest<Directory> {
            override fun <ARTIFACT_TYPE> on(type: ARTIFACT_TYPE)
                    where ARTIFACT_TYPE : ArtifactType<Directory>,
                          ARTIFACT_TYPE : ArtifactType.Transformable,
                          ARTIFACT_TYPE : ArtifactType.Multiple {}
        }
        val taskProvider = getTaskProvider<DirectoriesTransformerTask>()

        `when`(operations.transformAll(taskProvider,
            DirectoriesTransformerTask::getInputDirectories,
            DirectoriesTransformerTask::getOutputDirectory))
            .thenReturn(combineRequest)

        // actual API
        operations.transformAll(taskProvider, DirectoriesTransformerTask::getInputDirectories,
            DirectoriesTransformerTask::getOutputDirectory)
            .on(TestArtifactType.TEST_TRANSFORMABLE_DIRECTORIES)
    }

    @Test
    fun  testFileReplaced() {
        val transformRequest = getReplaceRequest<RegularFile>()
        val taskProvider = getTaskProvider<FileProducerTask>()

        `when`(operations.replace(taskProvider,
            FileProducerTask::getOutputFile))
            .thenReturn(transformRequest)

        // actual API
        operations.replace(taskProvider, FileProducerTask::getOutputFile)
            .on(TestArtifactType.TEST_REPLACABLE_FILE)
    }

    @Test
    fun  testDirectoryReplaced() {
        val transformRequest = getReplaceRequest<Directory>()
        val taskProvider = getTaskProvider<DirectoryProducerTask>()

        `when`(operations.replace(taskProvider,
            DirectoryProducerTask::getOutputDir))
            .thenReturn(transformRequest)

        // actual API
        operations.replace(taskProvider, DirectoryProducerTask::getOutputDir)
            .on(TestArtifactType.TEST_REPLACABLE_DIRECTORY)
    }

    private fun <T: FileSystemLocation> getAppendRequest()= object: AppendRequest<T> {
        override fun <ARTIFACT_TYPE> to(type: ARTIFACT_TYPE)
                where ARTIFACT_TYPE : ArtifactType<T>, ARTIFACT_TYPE : ArtifactType.Appendable {}
    }

    private fun <T: Task> getTaskProvider(): TaskProvider<T> {
        @Suppress("UNCHECKED_CAST")
        return Mockito.mock(TaskProvider::class.java) as TaskProvider<T>
    }

    private fun <T: FileSystemLocation> getTransformRequest()= object: TransformRequest<T> {
        override fun <ARTIFACT_TYPE> on(type: ARTIFACT_TYPE)
                where ARTIFACT_TYPE : ArtifactType<T>,
                      ARTIFACT_TYPE : ArtifactType.Transformable,
                      ARTIFACT_TYPE : ArtifactType.Single {}
    }

    private fun <T: FileSystemLocation> getReplaceRequest()= object: ReplaceRequest<T> {
        override fun <ARTIFACT_TYPE> on(type: ARTIFACT_TYPE)
                where ARTIFACT_TYPE : ArtifactType<T>,
                      ARTIFACT_TYPE : ArtifactType.Replaceable {}
    }

    abstract class FileProducerTask: DefaultTask() {
        @InputFile abstract fun getInputFile(): RegularFileProperty
        @OutputFile abstract fun getOutputFile(): RegularFileProperty
    }

    abstract class DirectoryProducerTask: DefaultTask() {
        @InputFile abstract fun getInputDir(): DirectoryProperty
        @OutputFile abstract fun getOutputDir(): DirectoryProperty
    }

    abstract class FilesTransformerTask: DefaultTask() {
        @InputFile abstract fun getInputFiles(): ListProperty<RegularFile>
        @OutputFile abstract fun getOutputFile(): RegularFileProperty
    }

    abstract class DirectoriesTransformerTask: DefaultTask() {
        @InputFile abstract fun getInputDirectories(): ListProperty<Directory>
        @OutputFile abstract fun getOutputDirectory(): DirectoryProperty
    }
}