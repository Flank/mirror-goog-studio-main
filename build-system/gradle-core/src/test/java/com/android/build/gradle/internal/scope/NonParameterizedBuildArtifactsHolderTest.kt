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

package com.android.build.gradle.internal.scope

import com.android.build.api.artifact.ArtifactType
import com.android.build.gradle.internal.fixtures.FakeDeprecationReporter
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.build.gradle.internal.variant2.DslScopeImpl
import com.android.builder.errors.FakeEvalIssueReporter
import com.google.common.truth.Truth
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.AbstractTask
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Test
import java.io.File
import javax.inject.Inject

class NonParameterizedBuildArtifactsHolderTest {

    private lateinit var project : Project
    lateinit var root : File
    private val dslScope = DslScopeImpl(
        FakeEvalIssueReporter(throwOnError = true),
        FakeDeprecationReporter(),
        FakeObjectFactory()
    )

    @Before
    fun setUp() {
        project = ProjectBuilder.builder().build()
        root = project.file("build")
    }

    private val initializedTasks = mutableMapOf<String, TaskWithOutput<*>>()

    @Test
    fun setOutputFileLocationTest() {

        val newHolder = TestBuildArtifactsHolder(
            project,
            "test",
            ::root
        )
        val task1Provider = registerRegularFileTask("original")
        newHolder.producesFile(
            InternalArtifactType.LIBRARY_MANIFEST,
            BuildArtifactsHolder.OperationType.INITIAL,
            task1Provider,
            RegularFileProducerTask::output,
            "set_location",
            "initialFile"
        )

        val finalVersion = newHolder.getFinalProduct(InternalArtifactType.LIBRARY_MANIFEST)
        Truth.assertThat(finalVersion.get().asFile.name).isEqualTo("initialFile")
        Truth.assertThat(finalVersion.get().asFile.parentFile.name).isEqualTo("set_location")
    }

    @Test
    fun setOutputDirLocationTest() {

        val newHolder = TestBuildArtifactsHolder(
            project,
            "test",
            ::root
        )
        val task1Provider = registerDirectoryTask("original")
        newHolder.producesDir(
            InternalArtifactType.MERGED_MANIFESTS,
            BuildArtifactsHolder.OperationType.INITIAL,
            task1Provider,
            DirectoryProducerTask::output,
            buildDirectory= "set_location"
        )

        val finalVersion = newHolder.getFinalProduct(InternalArtifactType.MERGED_MANIFESTS)
        Truth.assertThat(finalVersion.get().asFile.name).isEqualTo("out")
        Truth.assertThat(finalVersion.get().asFile.parentFile.name).isEqualTo("set_location")
    }

    @Test
    fun locationProvidedOutput() {
        val newHolder = TestBuildArtifactsHolder(
            project,
            "test",
            ::root
        )
        val taskProvider = project.tasks.register("locationProvidedTask", ProviderBasedProducerTask::class.java)
        newHolder.producesFile(
            InternalArtifactType.AAR,
            BuildArtifactsHolder.OperationType.INITIAL,
            taskProvider,
            ProviderBasedProducerTask::outputFile)

        val files = newHolder.getCurrentProduct(InternalArtifactType.AAR)
        Truth.assertThat(files).isNotNull()
        Truth.assertThat(files?.isPresent)
        Truth.assertThat(files?.get()?.asFile?.name).isEqualTo("provided_folder_name")

        // now get final version.
        val finalVersion = newHolder.getFinalProduct(InternalArtifactType.AAR)
        Truth.assertThat(finalVersion.get().asFile.name).isEqualTo("provided_folder_name")
    }

    @Test(expected=java.lang.RuntimeException::class)
    fun wrongDirAPIUsageTest() {
        val newHolder = TestBuildArtifactsHolder(
            project,
            "test",
            ::root
        )
        val taskProvider = registerDirectoryTask("final")
        @Suppress("UNCHECKED_CAST") // Wrong API Test !
        newHolder.producesDir(
            InternalArtifactType.APP_CLASSES as ArtifactType<Directory>,
            BuildArtifactsHolder.OperationType.INITIAL,
            taskProvider,
            DirectoryProducerTask::output,
            fileName = "finalDir"
        )
    }

    @Test(expected=java.lang.RuntimeException::class)
    fun wrongFileAPIUsageTest() {
        val newHolder = TestBuildArtifactsHolder(
            project,
            "test",
            ::root
        )
        val taskProvider = registerRegularFileTask("final")
        @Suppress("UNCHECKED_CAST") // Wrong API Test !
        newHolder.producesFile(
            InternalArtifactType.JAVA_RES as ArtifactType<RegularFile>,
            BuildArtifactsHolder.OperationType.INITIAL,
            taskProvider,
            RegularFileProducerTask::output,
            fileName = "finalFile"
        )
    }

    private fun registerDirectoryTask(taskName: String) =
        project.tasks.register(taskName, DirectoryProducerTask::class.java) {
            initializedTasks[taskName] = it
        }

    private fun registerRegularFileTask(taskName: String) =
        project.tasks.register(taskName, RegularFileProducerTask::class.java) {
            initializedTasks[taskName] = it
        }

    abstract class TaskWithOutput<T>(val output: T) : DefaultTask() {
        @TaskAction
        fun execute() {
            Truth.assertThat(output).isNotNull()
        }
    }

    open class DirectoryProducerTask @Inject constructor(objectFactory: ObjectFactory)
        : TaskWithOutput<DirectoryProperty>(objectFactory.directoryProperty())

    open class RegularFileProducerTask @Inject constructor(objectFactory: ObjectFactory)
        : TaskWithOutput<RegularFileProperty>(objectFactory.fileProperty())

    abstract class ProviderBasedProducerTask @Inject constructor(objectFactory: ObjectFactory): AbstractTask() {
        @get:OutputFile
        val outputFile: Provider<RegularFile>

        init {
            val regularFileProperty= objectFactory.fileProperty()
            regularFileProperty.set(File("/tmp/provided_folder_name"))
            outputFile = regularFileProperty
        }
    }
}
