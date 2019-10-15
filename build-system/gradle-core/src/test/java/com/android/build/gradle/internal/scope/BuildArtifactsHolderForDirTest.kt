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
import com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_MANIFESTS
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Locale
import javax.inject.Inject

/**
 * Test for [BuildArtifactsHolder]
 */
class BuildArtifactsHolderForDirTest {

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
        val finalVersion = holder.getFinalProduct(MERGED_MANIFESTS)
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
        val taskProvider = registerDirectoryTask("final")
        newHolder.producesDir(
            MERGED_MANIFESTS,
            taskProvider,
            DirectoryProducerTask::output,
            fileName = "finalFile"
        )

        // now get final version.
        val finalVersion = newHolder.getFinalProduct(MERGED_MANIFESTS)
        assertThat(finalVersion.get().asFile.name).isEqualTo("finalFile")
    }

    @Test
    fun finalProducerLocation() {
        val newHolder = TestBuildArtifactsHolder(
            project,
            "test",
            ::root
        )

        val taskProvider = registerDirectoryTask("test")
        newHolder.producesDir(
            MERGED_MANIFESTS,
            taskProvider,
            DirectoryProducerTask::output,
            fileName = "finalFile"
        )


        val finalArtifactFiles = newHolder.getFinalProduct(MERGED_MANIFESTS)
        val outputFile = finalArtifactFiles.get().asFile
        val relativeFile = outputFile.relativeTo(project.buildDir)
        assertThat(relativeFile).isNotNull()
        assertThat(relativeFile.path).isEqualTo(
            FileUtils.join(
                InternalArtifactType.Category.INTERMEDIATES.name.toLowerCase(),
                artifactTypeToString(MERGED_MANIFESTS),
                "test",
                "finalFile"))
    }

    private fun registerDirectoryTask(taskName: String) =
        project.tasks.register(taskName, DirectoryProducerTask::class.java) {
            initializedTasks[taskName] = it
        }

    abstract class TaskWithOutput<T>(val output: T) : DefaultTask() {
        @TaskAction
        fun execute() {
            assertThat(output).isNotNull()
        }
    }

    private fun artifactTypeToString(type: ArtifactType<*>)=type.name().toLowerCase(Locale.US)

    open class DirectoryProducerTask @Inject constructor(objectFactory: ObjectFactory)
        : TaskWithOutput<DirectoryProperty>(objectFactory.directoryProperty())

}