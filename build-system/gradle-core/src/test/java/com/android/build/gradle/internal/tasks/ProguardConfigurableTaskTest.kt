/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.ProguardFiles
import com.android.build.gradle.internal.fixtures.FakeGradleProvider
import com.android.builder.core.ComponentTypeImpl
import com.google.common.truth.Truth
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.file.ProjectLayout
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito
import org.mockito.Mockito.spy
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness
import java.io.File
import javax.inject.Inject

internal class ProguardConfigurableTaskTest {

    @get:Rule
    val rule: MockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

    @get: Rule
    val temporaryFolder = TemporaryFolder()

    lateinit var project: Project
    lateinit var task: ProguardConfigurableTask

    abstract class ProguardTestTask @Inject constructor(
        projectLayout: ProjectLayout
    ) : ProguardConfigurableTask(projectLayout) {
        override fun doTaskAction() {
        }
    }

    @Before
    fun setup() {
        project = ProjectBuilder.builder().withProjectDir(temporaryFolder.root).build()
        task = project.tasks.register(
            "proguardConfigurationTask",
            ProguardTestTask::class.java,
            project.layout
        ).get()
    }

    @Test
    fun testEmptyProguardFilesReconciliation() {
        Truth.assertThat(task).isNotNull()
        val fileCollection = Mockito.mock(FileCollection::class.java)
        val folder = temporaryFolder.newFolder("proguard_files")
        task.componentType.set(ComponentTypeImpl.BASE_APK)
        Truth.assertThat(
            task.reconcileDefaultProguardFile(
                fileCollection,
                FakeGradleProvider(project.layout.projectDirectory.dir(
                    folder.absolutePath)))
        ).isEmpty()
    }

    @Test
    fun testNonBaseModuleProguardFilesReconciliation() {
        Truth.assertThat(task).isNotNull()
        val fileCollection = Mockito.mock(FileCollection::class.java)
        val folder = temporaryFolder.newFolder("proguard_files")
        val file1 = spy(File(folder, "android.txt"))
        `when`(file1.isFile).thenReturn(true)
        `when`(fileCollection.files).thenReturn(setOf(
            file1
        ))
        task.componentType.set(ComponentTypeImpl.JAVA_LIBRARY)
        val result = task.reconcileDefaultProguardFile(
            fileCollection,
            FakeGradleProvider(project.layout.projectDirectory.dir(
                folder.absolutePath)))
        Truth.assertThat(result).hasSize(1)
        Truth.assertThat(result.single()).isEqualTo(
            file1
        )
    }

    @Test
    fun testSubstitution() {
        Truth.assertThat(task).isNotNull()
        val fileCollection = Mockito.mock(FileCollection::class.java)
        val srcFolder = temporaryFolder.newFolder("proguard_files")
        val finalDefaultFolder = temporaryFolder.newFolder("default_proguard_files")

        val defaultFile = ProguardFiles.getDefaultProguardFile(
            ProguardFiles.ProguardFile.OPTIMIZE.fileName,
            project.layout.buildDirectory
        )
        val file1 = spy(File(srcFolder, "user1.txt"))
        `when`(file1.isFile).thenReturn(true)
        val file2 = spy(File(srcFolder, "user2.txt"))
        `when`(file2.isFile).thenReturn(true)

        `when`(fileCollection.files).thenReturn(setOf(
                file1,
                file2,
                defaultFile
        ))
        task.componentType.set(ComponentTypeImpl.BASE_APK)
        val result = task.reconcileDefaultProguardFile(
            fileCollection,
            FakeGradleProvider(project.layout.projectDirectory.dir(
                finalDefaultFolder.absolutePath)))
        Truth.assertThat(result).hasSize(3)
        val substitutedFile = result.find {
            it.name.equals(defaultFile.name)
        }
        Truth.assertThat(substitutedFile).isNotNull()
        Truth.assertThat(substitutedFile!!.parentFile).isEqualTo(finalDefaultFolder)
    }

    @Test
    fun testFilesWithDefaultFileNameInSourceFoldersAreNotSubstituted() {
        Truth.assertThat(task).isNotNull()
        val fileCollection = Mockito.mock(FileCollection::class.java)
        val srcFolder = temporaryFolder.newFolder("proguard_files")
        val finalDefaultFolder = temporaryFolder.newFolder("default_proguard_files")

        val defaultFile = ProguardFiles.getDefaultProguardFile(
            ProguardFiles.ProguardFile.OPTIMIZE.fileName,
            project.layout.buildDirectory
        )
        val file1 = spy(File(srcFolder, "user1.txt"))
        `when`(file1.isFile).thenReturn(true)
        val file2 = spy(File(srcFolder, "user2.txt"))
        `when`(file2.isFile).thenReturn(true)
        val file3 = spy(File(srcFolder, defaultFile.name))
        `when`(file3.isFile).thenReturn(true)

        `when`(fileCollection.files).thenReturn(setOf(
                file1,
                file2,
                file3
        ))
        task.componentType.set(ComponentTypeImpl.BASE_APK)
        val result = task.reconcileDefaultProguardFile(
            fileCollection,
            FakeGradleProvider(project.layout.projectDirectory.dir(
                finalDefaultFolder.absolutePath)))
        Truth.assertThat(result).hasSize(3)
        val substitutedFile = result.find {
            it.name.equals(defaultFile.name)
        }
        Truth.assertThat(substitutedFile).isNotNull()
        Truth.assertThat(substitutedFile!!.parentFile).isEqualTo(srcFolder)
    }

    @Test
    fun `test files which do not exist are filtered out`() {
        Truth.assertThat(task).isNotNull()
        val fileCollection = Mockito.mock(FileCollection::class.java)
        val srcFolder = temporaryFolder.newFolder("proguard_files")
        val finalDefaultFolder = temporaryFolder.newFolder("default_proguard_files")

        val file1 = spy(File(srcFolder, "user1.txt"))
        `when`(file1.isFile).thenReturn(true)
        val file2 = spy(File(srcFolder, "user2.txt"))
        `when`(file2.isFile).thenReturn(false)

        `when`(fileCollection.files).thenReturn(setOf(
                file1,
                file2,
        ))
        task.componentType.set(ComponentTypeImpl.BASE_APK)
        val result = task.reconcileDefaultProguardFile(
                fileCollection,
                FakeGradleProvider(project.layout.projectDirectory.dir(
                        finalDefaultFolder.absolutePath)))
        Truth.assertThat(result).hasSize(1)
        Truth.assertThat(result.find { it.name.equals("user1.txt") }).isNotNull()
    }
}
