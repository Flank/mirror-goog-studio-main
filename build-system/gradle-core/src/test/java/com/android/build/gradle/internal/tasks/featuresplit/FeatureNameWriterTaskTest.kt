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

package com.android.build.gradle.internal.tasks.featuresplit

import com.google.common.truth.Truth.assertThat

import java.io.File
import java.io.IOException
import org.apache.commons.io.FileUtils
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.function.Supplier

/** Tests for the [FeatureNameWriterTask]  */
class FeatureNameWriterTaskTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    internal lateinit var project: Project
    internal lateinit var task: FeatureNameWriterTask
    internal lateinit var outputFile: File

    @Before
    @Throws(IOException::class)
    fun setUp() {
        val testDir = temporaryFolder.newFolder()
        outputFile = temporaryFolder.newFile()
        project = ProjectBuilder.builder().withProjectDir(testDir).build()

        task = project.tasks.create("test", FeatureNameWriterTask::class.java)
        task.outputFile.set(outputFile)
    }

    @Test
    @Throws(IOException::class)
    fun testTask() {
        task.featureName.set("someFeature")
        task.doTaskAction()
        assertThat(outputFile.exists()).isTrue()

        assertThat(FileUtils.readFileToString(outputFile)).isEqualTo("someFeature")
    }
}
