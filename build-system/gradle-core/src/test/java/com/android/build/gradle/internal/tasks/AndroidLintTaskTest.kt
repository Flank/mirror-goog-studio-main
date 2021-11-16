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

import com.android.Version
import com.android.build.gradle.internal.fixtures.FakeGradleWorkExecutor
import com.android.build.gradle.internal.fixtures.FakeNoOpAnalyticsService
import com.android.build.gradle.internal.lint.AndroidLintTask
import com.google.common.truth.Truth.assertThat
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.workers.WorkerExecutor
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import javax.inject.Inject

/**
 * Unit tests for [AndroidLintTask].
 */
class AndroidLintTaskTest {

    @get: Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var task: AndroidLintTask

    abstract class TaskForTest @Inject constructor(testWorkerExecutor: WorkerExecutor) :
        AndroidLintTask() {
        override val workerExecutor = testWorkerExecutor
    }

    @Before
    fun setUp() {
        val project = ProjectBuilder.builder().withProjectDir(temporaryFolder.root).build()
        task = project.tasks.register(
            "androidLintTask",
            TaskForTest::class.java,
            FakeGradleWorkExecutor(project.objects, temporaryFolder.newFolder())
        ).get()
        task.analyticsService.set(FakeNoOpAnalyticsService())
    }

    @Test
    fun testGenerateCommandLineArguments() {
        task.autoFix.set(false)
        task.fatalOnly.set(false)
        task.systemPropertyInputs.javaHome.set("javaHome")
        task.androidSdkHome.set("androidSdkHome")
        task.intermediateTextReport.set(temporaryFolder.newFile())
        task.textReportEnabled.set(false)
        task.htmlReportEnabled.set(false)
        task.xmlReportEnabled.set(false)
        task.sarifReportEnabled.set(false)
        task.textReportToStdOut.set(false)
        task.lintModelDirectory.set(temporaryFolder.newFolder())
        task.lintModelWriterTaskOutputPath.set("lintModelWriterTaskOutputPath")
        task.checkDependencies.set(true)
        task.printStackTrace.set(false)
        task.lintTool.lintCacheDirectory.set(temporaryFolder.newFolder())
        task.lintTool.versionKey.set("test-version")
        val commandLineArguments = task.generateCommandLineArguments().joinToString(" ")
        assertThat(commandLineArguments).contains("--client-id gradle")
        assertThat(commandLineArguments).contains("--client-name AGP")
        assertThat(commandLineArguments)
            .contains("--client-version ${Version.ANDROID_GRADLE_PLUGIN_VERSION}")
    }
}
