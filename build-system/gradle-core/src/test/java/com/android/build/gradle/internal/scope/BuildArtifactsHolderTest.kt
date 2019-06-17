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

package com.android.build.gradle.internal.scope

import com.android.build.api.artifact.ArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.LIBRARY_MANIFEST
import com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_MANIFESTS
import com.android.build.gradle.internal.fixtures.FakeDeprecationReporter
import com.android.build.gradle.internal.fixtures.FakeEvalIssueReporter
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.build.gradle.internal.scope.BuildArtifactsHolder.OperationType
import com.android.build.gradle.internal.variant2.DslScopeImpl
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import javax.inject.Inject

/**
 * Test for [BuildArtifactsHolder]
 */
@RunWith(Parameterized::class)
class BuildArtifactsHolderTest(
    private val operationType: OperationType, private val artifactType: InternalArtifactType) {

    companion object {

        @JvmStatic
        @Parameterized.Parameters(name = "{index}: test({0})={1})")
        fun parameters(): Iterable<Array<Any>> {
            return listOf(
                arrayOf<Any>(OperationType.INITIAL, LIBRARY_MANIFEST),
                arrayOf<Any>(OperationType.APPEND, LIBRARY_MANIFEST),
                arrayOf<Any>(OperationType.TRANSFORM, LIBRARY_MANIFEST),
                arrayOf<Any>(OperationType.INITIAL, MERGED_MANIFESTS),
                arrayOf<Any>(OperationType.APPEND, MERGED_MANIFESTS),
                arrayOf<Any>(OperationType.TRANSFORM, MERGED_MANIFESTS)
            )
        }
    }

    private lateinit var project : Project
    lateinit var root : File
    private val dslScope = DslScopeImpl(
            FakeEvalIssueReporter(throwOnError = true),
            FakeDeprecationReporter(),
            FakeObjectFactory())
    private lateinit var holder : TestBuildArtifactsHolder
    private lateinit var task1 : Task
    private lateinit var task2 : Task
    private lateinit var task3 : Task

    @Before
    fun setUp() {
        project = ProjectBuilder.builder().build()
        root = project.file("build")
        holder = TestBuildArtifactsHolder(
            project,
            "debug",
            ::root,
            dslScope
        )
        task1 = project.tasks.create("task1")
        task2 = project.tasks.create("task2")
        task3 = project.tasks.create("task3")
    }

    @Test
    fun earlyFinalOutput() {
        val finalVersion = holder.getFinalProduct<Directory>(MERGED_MANIFESTS)
        // no-one appends or replaces, it's not provided.
        assertThat(finalVersion.isPresent).isFalse()
    }

    private val initializedTasks = mutableMapOf<String, TaskWithOutput<*>>()

    @Test
    fun lateFinalOutput() {
        val newHolder = TestBuildArtifactsHolder(
            project,
            "test",
            ::root,
            dslScope
        )
        val taskProvider = registerDirectoryTask("final")
        newHolder.producesDir(
            MERGED_MANIFESTS,
            operationType,
            taskProvider,
            DirectoryProducerTask::output,
            fileName = "finalFile"
        )

        val files = newHolder.getCurrentProduct(MERGED_MANIFESTS)
        assertThat(files).isNotNull()
        assertThat(files?.isPresent)
        assertThat(files?.get()?.asFile?.name).isEqualTo("finalFile")

        // now get final version.
        val finalVersion = newHolder.getFinalProduct<FileSystemLocation>(MERGED_MANIFESTS)
        assertThat(finalVersion.get().asFile.name).isEqualTo("finalFile")
    }

    @Test
    fun appendProducerTest() {
        val newHolder = TestBuildArtifactsHolder(
            project,
            "test",
            ::root,
            dslScope
        )
        val task1Provider = registerDirectoryTask("original")
        newHolder.producesDir(
            MERGED_MANIFESTS,
            OperationType.INITIAL,
            task1Provider,
            DirectoryProducerTask::output,
            fileName = "initialFile"
        )

        val task2Provider = registerDirectoryTask(operationType.name)
        val appendShouldFail = operationType != OperationType.APPEND

        try {
            newHolder.producesDir(
                MERGED_MANIFESTS,
                operationType,
                task2Provider,
                DirectoryProducerTask::output,
                fileName = "appended"
            )
        } catch(e:RuntimeException) {
            assertThat(appendShouldFail).isTrue()
        }

        val files: ListProperty<Directory> = newHolder.getFinalProducts(MERGED_MANIFESTS)
        assertThat(files).isNotNull()
        assertThat(files.get()).hasSize(if (appendShouldFail) 1 else 2)

        try {
            newHolder.getFinalProduct<FileSystemLocation>(MERGED_MANIFESTS)
            if (!appendShouldFail) Assert.fail("Exception not raised")
        } catch(e: RuntimeException) {
            assertThat(e).hasMessageThat().contains(
                if (appendShouldFail) "original" else "original,APPEND")
        }

        assertThat(initializedTasks).hasSize(if (appendShouldFail) 1 else 2)

        assertThat(files.get()[0].asFile.name).isEqualTo(
            if (operationType == OperationType.TRANSFORM) "appended" else "initialFile")
        if (!appendShouldFail) {
            assertThat(files.get()[1].asFile.name).isEqualTo("appended")
        }

        assertThat(initializedTasks).hasSize(if (appendShouldFail) 1 else 2)
        assertThat(initializedTasks.keys).containsExactly(*when(operationType) {
            OperationType.TRANSFORM -> arrayOf(OperationType.TRANSFORM.name)
            OperationType.INITIAL -> arrayOf("original")
            OperationType.APPEND -> arrayOf("original", OperationType.APPEND.name)
        })
    }

    @Test
    fun finalProducerLocation() {
        val newHolder = TestBuildArtifactsHolder(
            project,
            "test",
            ::root,
            dslScope
        )
        if (artifactType.kind == ArtifactType.Kind.FILE) {
            val taskProvider = registerRegularFileTask("test")
            newHolder.producesFile(
                artifactType,
                operationType,
                taskProvider,
                RegularFileProducerTask::output,
                fileName = "finalFile"
            )
        } else {
            val taskProvider = registerDirectoryTask("test")
            newHolder.producesDir(
                artifactType,
                operationType,
                taskProvider,
                DirectoryProducerTask::output,
                fileName = "finalFile"
            )
        }

        val finalArtifactFiles = newHolder.getFinalProduct<FileSystemLocation>(artifactType)
        val outputFile = finalArtifactFiles.get().asFile
        val relativeFile = outputFile.relativeTo(project.buildDir)
        assertThat(relativeFile).isNotNull()
        assertThat(relativeFile.path).isEqualTo(
            FileUtils.join(
                InternalArtifactType.Category.INTERMEDIATES.name.toLowerCase(),
                artifactType.name.toLowerCase(),
                "test",
                "finalFile"))
    }

    @Test
    fun finalProducersLocation() {
        val newHolder = TestBuildArtifactsHolder(
            project,
            "test",
            ::root,
            dslScope
        )
        val firstProvider : TaskProvider<out Task>
        val secondProvider : TaskProvider<out Task>

        if (artifactType.kind == ArtifactType.Kind.FILE) {
            firstProvider = registerRegularFileTask("first")
            newHolder.producesFile(
                artifactType,
                operationType,
                firstProvider,
                RegularFileProducerTask::output,
                fileName = "firstFile"
            )

            secondProvider = registerRegularFileTask("second")
            newHolder.producesFile(
                artifactType,
                OperationType.APPEND,
                secondProvider,
                RegularFileProducerTask::output,
                fileName = "secondFile"
            )
        } else {
            firstProvider = registerDirectoryTask("first")
            newHolder.producesDir(
                artifactType,
                operationType,
                firstProvider,
                DirectoryProducerTask::output,
                fileName = "firstFile"
            )

            secondProvider = registerDirectoryTask("second")
            newHolder.producesDir(
                artifactType,
                OperationType.APPEND,
                secondProvider,
                DirectoryProducerTask::output,
                fileName = "secondFile"
            )
        }

        val finalArtifactFiles: ListProperty<out FileSystemLocation> = newHolder.getFinalProducts(artifactType)
        assertThat(finalArtifactFiles.get()).hasSize(2)
        var outputFile = finalArtifactFiles.get()[0].asFile
        var relativeFile = outputFile.relativeTo(project.buildDir)
        assertThat(relativeFile).isNotNull()
        assertThat(relativeFile.path).isEqualTo(
            FileUtils.join(
                InternalArtifactType.Category.INTERMEDIATES.name.toLowerCase(),
                artifactType.name.toLowerCase(),
                "test",
                firstProvider.name,
                "firstFile"))

        outputFile = finalArtifactFiles.get()[1].asFile
        relativeFile = outputFile.relativeTo(project.buildDir)
        assertThat(relativeFile).isNotNull()
        assertThat(relativeFile.path).isEqualTo(
            FileUtils.join(
                InternalArtifactType.Category.INTERMEDIATES.name.toLowerCase(),
                artifactType.name.toLowerCase(),
                "test",
                secondProvider.name,
                "secondFile"))
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
            assertThat(output).isNotNull()
        }
    }

    open class DirectoryProducerTask @Inject constructor(objectFactory: ObjectFactory)
        : TaskWithOutput<DirectoryProperty>(objectFactory.directoryProperty())

    open class RegularFileProducerTask @Inject constructor(objectFactory: ObjectFactory)
        : TaskWithOutput<RegularFileProperty>(objectFactory.fileProperty())

}