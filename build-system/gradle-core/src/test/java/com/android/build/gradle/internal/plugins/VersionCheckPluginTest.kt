/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.internal.plugins

import com.android.build.gradle.internal.fixture.TestProjects
import com.google.common.truth.Truth.assertThat
import org.gradle.api.Project
import org.gradle.api.internal.GradleInternal
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import java.io.File

class VersionCheckPluginTest {
    @get:Rule
    val projectDirectory = TemporaryFolder()

    private lateinit var plugin: VersionCheckPlugin
    private lateinit var project: Project

    @Before
    fun setUp() {
        project = TestProjects.builder(projectDirectory.newFolder("project").toPath())
            .build()
        plugin = project.plugins.getPlugin(VersionCheckPlugin::class.java)
    }

    @Test
    fun `path in error message is correct`() {
        val mockGradle = mock(GradleInternal::class.java)
        `when`(mockGradle.gradleVersion).thenReturn("0.0.0")
        val spyProject = spy(project)
        `when`(spyProject.gradle).thenReturn(mockGradle)
        try {
            plugin.apply(spyProject)
            fail("A RuntimeException was expected but not generated")
        }
        catch (generatedException: RuntimeException) {
            val expectedPath = "${project.projectDir.absolutePath}${File.separator}gradle${File.separator}wrapper${File.separator}gradle-wrapper.properties"
            val message = generatedException.message
            assertThat(message).contains(" $expectedPath")
        }
    }
}