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
import com.android.build.gradle.internal.scope.BuildArtifactsHolder.OperationType
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Locale
import javax.inject.Inject
import kotlin.test.fail

/**
 * Test for [BuildArtifactsHolder]
 */
class BuildArtifactsHolderForFileTest {

    private lateinit var project : Project
    lateinit var root : File
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
            ::root
        )
        task1 = project.tasks.create("task1")
        task2 = project.tasks.create("task2")
        task3 = project.tasks.create("task3")
    }

    @Test
    fun earlyFinalOutput() {
        val finalVersion = holder.getFinalProduct(LIBRARY_MANIFEST)
        // no-one appends or replaces, it's not provided.
        assertThat(finalVersion.isPresent).isFalse()
    }

    private val initializedTasks = mutableMapOf<String, TaskWithOutput<*>>()

    @Test
    fun lateFinalOutput() {
        val newHolder = TestBuildArtifactsHolder(
            project,
            "test",
            ::root
        )
        val taskProvider = registerRegularFileTask("final")
        newHolder.producesFile(
            LIBRARY_MANIFEST,
            OperationType.INITIAL,
            taskProvider,
            RegularFileProducerTask::output,
            fileName = "finalFile"
        )

        val files = newHolder.getCurrentProduct(LIBRARY_MANIFEST)
        assertThat(files).isNotNull()
        assertThat(files?.isPresent)
        assertThat(files?.get()?.asFile?.name).isEqualTo("finalFile")

        // now get final version.
        val finalVersion = newHolder.getFinalProduct(LIBRARY_MANIFEST)
        assertThat(finalVersion.get().asFile.name).isEqualTo("finalFile")
    }

    @Test
    fun appendProducerTest() {
        val newHolder = TestBuildArtifactsHolder(
            project,
            "test",
            ::root
        )
        val task1Provider = registerRegularFileTask("original")
        newHolder.producesFile(
            LIBRARY_MANIFEST,
            OperationType.INITIAL,
            task1Provider,
            RegularFileProducerTask::output,
            fileName = "initialFile"
        )

        val task2Provider = registerRegularFileTask("original2")

        try {
            newHolder.producesFile(
                LIBRARY_MANIFEST,
                OperationType.INITIAL,
                task2Provider,
                RegularFileProducerTask::output,
                fileName = "initialFile2"
            )
            fail("2 initial providers should fail.")
        } catch(e:RuntimeException) {
            // fine we were expecting this.
        }

        try {
            newHolder.producesFile(
                LIBRARY_MANIFEST,
                OperationType.APPEND,
                task2Provider,
                RegularFileProducerTask::output,
                fileName = "appended"
            )
        } catch(e:RuntimeException) {
            // fine we were expecting this.
        }

        var files: ListProperty<RegularFile> = newHolder.getFinalProducts(LIBRARY_MANIFEST)
        assertThat(files).isNotNull()
        assertThat(files.get()).hasSize(2)

        newHolder.producesFile(
            LIBRARY_MANIFEST,
            OperationType.TRANSFORM,
            task2Provider,
            RegularFileProducerTask::output,
            fileName = "replaced"
        )

        files= newHolder.getFinalProducts(LIBRARY_MANIFEST)
        assertThat(files).isNotNull()
        assertThat(files.get()).hasSize(1)

        assertThat(initializedTasks).hasSize(2)

        assertThat(files.get()[0].asFile.name).isEqualTo("replaced")
    }

    @Test
    fun finalProducerLocation() {
        val newHolder = TestBuildArtifactsHolder(
            project,
            "test",
            ::root
        )
        val taskProvider = registerRegularFileTask("test")
        newHolder.producesFile(
            LIBRARY_MANIFEST,
            OperationType.INITIAL,
            taskProvider,
            RegularFileProducerTask::output,
            fileName = "finalFile"
        )


        val finalArtifactFiles = newHolder.getFinalProduct(LIBRARY_MANIFEST)
        val outputFile = finalArtifactFiles.get().asFile
        val relativeFile = outputFile.relativeTo(project.buildDir)
        assertThat(relativeFile).isNotNull()
        assertThat(relativeFile.path).isEqualTo(
            FileUtils.join(
                InternalArtifactType.Category.INTERMEDIATES.name.toLowerCase(),
                artifactTypeToString(LIBRARY_MANIFEST),
                "test",
                "finalFile"))
    }

    @Test
    fun finalProducersLocation() {
        val newHolder = TestBuildArtifactsHolder(
            project,
            "test",
            ::root
        )
        val firstProvider : TaskProvider<out Task>
        val secondProvider : TaskProvider<out Task>

        firstProvider = registerRegularFileTask("first")
        newHolder.producesFile(
            LIBRARY_MANIFEST,
            OperationType.INITIAL,
            firstProvider,
            RegularFileProducerTask::output,
            fileName = "firstFile"
        )

        secondProvider = registerRegularFileTask("second")
        newHolder.producesFile(
            LIBRARY_MANIFEST,
            OperationType.APPEND,
            secondProvider,
            RegularFileProducerTask::output,
            fileName = "secondFile"
        )


        val finalArtifactFiles: ListProperty<RegularFile> = newHolder.getFinalProducts(LIBRARY_MANIFEST)
        assertThat(finalArtifactFiles.get()).hasSize(2)
        var outputFile = finalArtifactFiles.get()[0].asFile
        var relativeFile = outputFile.relativeTo(project.buildDir)
        assertThat(relativeFile).isNotNull()
        assertThat(relativeFile.path).isEqualTo(
            FileUtils.join(
                InternalArtifactType.Category.INTERMEDIATES.name.toLowerCase(),
                artifactTypeToString(LIBRARY_MANIFEST),
                "test",
                firstProvider.name,
                "firstFile"))

        outputFile = finalArtifactFiles.get()[1].asFile
        relativeFile = outputFile.relativeTo(project.buildDir)
        assertThat(relativeFile).isNotNull()
        assertThat(relativeFile.path).isEqualTo(
            FileUtils.join(
                InternalArtifactType.Category.INTERMEDIATES.name.toLowerCase(),
                artifactTypeToString(LIBRARY_MANIFEST),
                "test",
                secondProvider.name,
                "secondFile"))
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

    private fun artifactTypeToString(type: ArtifactType<*>)=type.name().toLowerCase(Locale.US)

    open class RegularFileProducerTask @Inject constructor(objectFactory: ObjectFactory)
        : TaskWithOutput<RegularFileProperty>(objectFactory.fileProperty())

}