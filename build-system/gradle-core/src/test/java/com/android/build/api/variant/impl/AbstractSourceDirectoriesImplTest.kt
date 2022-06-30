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
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito
import java.io.File

internal class AbstractSourceDirectoriesImplTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    val listOfSources = mutableListOf<DirectoryEntry>()

    private lateinit var project: Project

    @Before
    fun setup() {
        project = ProjectBuilder.builder()
            .withProjectDir(temporaryFolder.newFolder())
            .build()
    }

    @Test
    fun testGetName() {
        Truth.assertThat(createTestTarget().name).isEqualTo("_for_test")
    }

    @Test
    fun testAddSrcDir() {
        val testTarget = createTestTarget()
        val addedSource = temporaryFolder.newFolder("somewhere/safe")
        testTarget.addStaticSourceDirectory(
            addedSource.absolutePath
        )

        Truth.assertThat(listOfSources.size).isEqualTo(1)
        val directoryProperty = listOfSources.single().asFiles { project.objects.directoryProperty() }
        Truth.assertThat(directoryProperty.get().asFile.absolutePath).isEqualTo(
            addedSource.absolutePath
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun testAddIllegalSrcDir() {
        val testTarget = createTestTarget()
        val addedSource = File(temporaryFolder.root, "somewhere/not/existing")
        testTarget.addStaticSourceDirectory(
            addedSource.absolutePath
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun testAddIllegalFileAsSrcDir() {
        val testTarget = createTestTarget()
        val addedSource = temporaryFolder.newFile("new_file")
        testTarget.addStaticSourceDirectory(
            addedSource.absolutePath
        )
    }

    @Test
    fun testAddSrcDirFromTask() {
        abstract class AddingTask: DefaultTask() {
            @get:OutputFiles
            abstract val output: DirectoryProperty
        }

        val addedSource = project.layout.buildDirectory.dir("generated/_for_test/srcAddingTask").get().asFile
        val taskProvider = project.tasks.register("srcAddingTask", AddingTask::class.java)

        val testTarget = createTestTarget()
        testTarget.addGeneratedSourceDirectory(taskProvider, AddingTask::output)
        Truth.assertThat(listOfSources.size).isEqualTo(1)
        val directoryProperty = listOfSources.single().asFiles { project.objects.directoryProperty() }
        Truth.assertThat(directoryProperty.get().asFile.absolutePath).isEqualTo(
            addedSource.absolutePath
        )
    }

    @Test
    fun testFiltering() {
        val pattern = Mockito.mock(PatternFilterable::class.java)
        Mockito.`when`(pattern.includes).thenReturn(setOf("*.java", "*.kt"))
        Mockito.`when`(pattern.excludes).thenReturn(setOf("*.bak"))
        val testTarget = createTestTarget(pattern)
        val addedSource = temporaryFolder.newFolder("somewhere/safe")
        testTarget.addStaticSourceDirectory(
            addedSource.absolutePath
        )

        Truth.assertThat(listOfSources.size).isEqualTo(1)
        val filter = listOfSources.single().filter
        Truth.assertThat(filter).isNotNull()
        Truth.assertThat(filter?.includes).containsExactly("*.java", "*.kt")
        Truth.assertThat(filter?.excludes).containsExactly("*.bak")
    }

    private fun createTestTarget(patternFilterable: PatternFilterable? = null): SourceDirectoriesImpl {
        val variantServices = Mockito.mock(VariantServices::class.java)
        val projectInfo = Mockito.mock(ProjectInfo::class.java)
        Mockito.`when`(variantServices.projectInfo).thenReturn(projectInfo)
        Mockito.`when`(projectInfo.projectDirectory).thenReturn(project.layout.projectDirectory)
        Mockito.`when`(projectInfo.buildDirectory).thenReturn(project.layout.buildDirectory)

        return object : SourceDirectoriesImpl(
            "_for_test",
            variantServices,
            patternFilterable
        ) {
            override fun addSource(directoryEntry: DirectoryEntry) {
                listOfSources.add(directoryEntry)
            }

            override fun variantSourcesForModel(filter: (DirectoryEntry) -> Boolean): List<File> =
                emptyList()
        }
    }
}
