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

package com.android.build.gradle.tasks

import com.android.SdkConstants
import com.google.common.truth.Truth
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.MockitoAnnotations
import java.io.IOException
import java.lang.IllegalStateException

internal class ProcessLibraryArtProfileTaskTest {
    @get:Rule
    var projectFolder = TemporaryFolder()

    @get:Rule
    var sourceFolder = TemporaryFolder()

    private lateinit var task: ProcessLibraryArtProfileTask
    private lateinit var project: Project

    @Before
    @Throws(IOException::class)
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        project= ProjectBuilder.builder().withProjectDir(projectFolder.root).build()
        val taskProvider = project.tasks.register(
                "processLibraryArtProfile", ProcessLibraryArtProfileTask::class.java
        )
        task = taskProvider.get()
    }

    @Test
    fun testValidArtProfile() {
        val sourceFile = sourceFolder.newFile(SdkConstants.FN_ART_PROFILE).also {
            it.writeText(
                    """
                        HSPLcom/google/Foo;->method(II)I
                        HSPLcom/google/Foo;->method-name-with-hyphens(II)I
                    """.trimIndent()
            )
        }
        task.profileSource.set(sourceFile)
        val outputFile = projectFolder.newFile("output.txt")
        task.outputFile.set(outputFile)
        task.taskAction()

        Truth.assertThat(outputFile.exists()).isTrue()
        Truth.assertThat(outputFile.readText()).isEqualTo(sourceFile.readText())
    }

    @Test(expected = RuntimeException::class)
    fun testInvalidArtProfile() {
        val sourceFile = sourceFolder.newFile(SdkConstants.FN_ART_PROFILE).also {
            it.writeText(
                    """
                        garbage
                    """.trimIndent()
            )
        }
        task.profileSource.set(sourceFile)
        val outputFile = projectFolder.newFile("output.txt")
        task.outputFile.set(outputFile)
        task.taskAction()
    }
}
