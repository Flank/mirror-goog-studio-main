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

package com.android.build.gradle.internal.tasks

import com.android.SdkConstants.MAVEN_ARTIFACT_ID_PROPERTY
import com.android.SdkConstants.MAVEN_GROUP_ID_PROPERTY
import com.android.build.gradle.internal.fixtures.FakeGradleWorkExecutor
import com.android.build.gradle.internal.fixtures.FakeNoOpAnalyticsService
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.truth.Truth.assertThat
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.workers.WorkerExecutor
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Properties
import javax.inject.Inject

/**
 * Unit tests for [LintModelMetadataTask].
 */
class LintModelMetadataTaskTest {

    @get: Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var task: LintModelMetadataTask
    private lateinit var outputFile: File

    abstract class TaskForTest @Inject constructor(testWorkerExecutor: WorkerExecutor) :
        LintModelMetadataTask() {
        override val workerExecutor = testWorkerExecutor
    }

    @Before
    fun setUp() {
        val project = ProjectBuilder.builder().withProjectDir(temporaryFolder.root).build()
        task = project.tasks.register(
            "lintModelMetadataTask",
            TaskForTest::class.java,
            FakeGradleWorkExecutor(project.objects, temporaryFolder.newFolder())
        ).get()
        task.analyticsService.set(FakeNoOpAnalyticsService())
        outputFile = temporaryFolder.newFile()
    }

    @Test
    fun testBasic() {
        task.outputFile.set(outputFile)
        task.mavenArtifactId.set("foo")
        task.mavenGroupId.set("foo.bar")
        task.taskAction()

        checkOutputFile(outputFile, mavenArtifactId = "foo", mavenGroupId = "foo.bar")
    }

    private fun checkOutputFile(file: File, mavenArtifactId: String, mavenGroupId: String) {
        assertThat(file).exists()
        val properties = Properties()
        file.inputStream().use { properties.load(it) }
        assertThat(properties.getProperty(MAVEN_ARTIFACT_ID_PROPERTY)).isEqualTo(mavenArtifactId)
        assertThat(properties.getProperty(MAVEN_GROUP_ID_PROPERTY)).isEqualTo(mavenGroupId)
    }
}
