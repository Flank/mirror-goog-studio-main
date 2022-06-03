/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.api.variant.impl

import com.android.build.gradle.internal.scope.ProjectInfo
import com.android.build.gradle.internal.services.VariantServices
import com.google.common.truth.Truth
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness
import java.io.File

internal class SourceDirectoriesImplTest {
    @get:Rule
    val rule: MockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Mock
    lateinit var variantServices: VariantServices

    private lateinit var project: Project

    @Before
    fun setup() {
        project = ProjectBuilder.builder()
            .withProjectDir(temporaryFolder.newFolder())
            .build()

        Mockito.`when`(variantServices.directoryProperty()).thenReturn(
            project.objects.directoryProperty()
        )

        val projectInfo = Mockito.mock(ProjectInfo::class.java)
        Mockito.`when`(variantServices.projectInfo).thenReturn(projectInfo)
        Mockito.`when`(projectInfo.projectDirectory).thenReturn(project.layout.projectDirectory)
        Mockito.`when`(projectInfo.buildDirectory).thenReturn(project.layout.buildDirectory)

        Mockito.`when`(variantServices.newListPropertyForInternalUse(DirectoryEntry::class.java))
            .thenReturn(project.objects.listProperty(DirectoryEntry::class.java))
        Mockito.`when`(variantServices.newListPropertyForInternalUse(Directory::class.java))
            .thenReturn(project.objects.listProperty(Directory::class.java))

    }

    @Test
    fun testGetAll() {
        val addedSourceFromTask = project.layout.buildDirectory.dir("generated/_for_test/srcAddingTask").get().asFile
        val addedSrcDir = temporaryFolder.newFolder("somewhere/safe")
        val testTarget = createTestTarget(addedSrcDir)
        val directories = testTarget.all.get()
        Truth.assertThat(directories).hasSize(2)
        Truth.assertThat(directories.map { it.asFile.absolutePath }).containsExactly(
            addedSourceFromTask.absolutePath,
            addedSrcDir.absolutePath
        )
    }

    @Test
    fun testAsFileTree() {
        Mockito.`when`(variantServices.fileTree()).thenReturn(
            project.objects.fileTree(),
            project.objects.fileTree(),
        )
        val addedSourceFromTask = project.layout.buildDirectory.dir("generated/_for_test/srcAddingTask").get().asFile
        val addedSrcDir = temporaryFolder.newFolder("somewhere/safe")
        val testTarget = createTestTarget(addedSrcDir)
        val fileTrees = testTarget.getAsFileTrees().get()
        Truth.assertThat(fileTrees).hasSize(2)
        Truth.assertThat(fileTrees.map { it.dir.absolutePath }).containsExactly(
            addedSourceFromTask.absolutePath,
            addedSrcDir.absolutePath
        )
    }

    @Test
    fun testVariantSourcesForModel() {
        val addedSourceFromTask = project.layout.buildDirectory.dir("generated/_for_test/srcAddingTask").get().asFile
        val addedSrcDir = temporaryFolder.newFolder("somewhere/safe")
        val testTarget = createTestTarget(addedSrcDir)
        val fileTrees = testTarget.variantSourcesForModel { it.shouldBeAddedToIdeModel }
        Truth.assertThat(fileTrees).hasSize(2)
        Truth.assertThat(fileTrees.map { it.absolutePath }).containsExactly(
            addedSourceFromTask.absolutePath,
            addedSrcDir.absolutePath
        )
    }

    @Test
    fun testVariantSourcesWithFilteringForModel() {
        val addedSourceFromTask = project.layout.buildDirectory.dir("generated/_for_test/srcAddingTask").get().asFile
        val addedSrcDir = temporaryFolder.newFolder("somewhere/safe")
        val testTarget = createTestTarget(addedSrcDir)
        val fileTrees = testTarget.variantSourcesForModel { entry ->
            entry.isGenerated
        }
        Truth.assertThat(fileTrees).hasSize(1)
        Truth.assertThat(fileTrees.map { it.absolutePath }).containsExactly(
            addedSourceFromTask.absolutePath,
        )
    }

    private fun createTestTarget(
        addedSrcDir: File,
        patternFilterable: PatternFilterable? = null
    ): FlatSourceDirectoriesImpl {

        val testTarget = FlatSourceDirectoriesImpl(
            "_for_test",
            variantServices,
            patternFilterable,
        )
        abstract class AddingTask: DefaultTask() {
            @get:OutputFiles
            abstract val output: DirectoryProperty
        }

        val taskProvider = project.tasks.register("srcAddingTask", AddingTask::class.java)
        testTarget.addGeneratedSourceDirectory(taskProvider, AddingTask::output)

        testTarget.addStaticSourceDirectory(addedSrcDir.absolutePath)

        return testTarget
    }
}
