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

import com.android.build.gradle.internal.fixtures.FakeGradleWorkExecutor
import com.android.build.gradle.internal.fixtures.FakeLogger
import com.android.build.gradle.internal.fixtures.FakeNoOpAnalyticsService
import com.android.build.gradle.internal.lint.AndroidLintTextOutputTask
import com.android.build.gradle.internal.lint.AndroidLintWorkAction.Companion.ERRNO_CREATED_BASELINE
import com.android.build.gradle.internal.lint.AndroidLintWorkAction.Companion.ERRNO_ERRORS
import com.google.common.truth.Truth.assertThat
import junit.framework.Assert.fail
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.workers.WorkerExecutor
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.lang.RuntimeException
import javax.inject.Inject

/**
 * Unit tests for [AndroidLintTextOutputTask].
 */
class AndroidLintTextOutputTaskTest {

    @get: Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var task: AndroidLintTextOutputTask

    abstract class TaskForTest @Inject constructor(
        testWorkerExecutor: WorkerExecutor,
        val testLogger: FakeLogger,
    ) : AndroidLintTextOutputTask() {
        override val workerExecutor = testWorkerExecutor
        override fun getLogger() = testLogger
    }

    @Before
    fun setUp() {
        val project = ProjectBuilder.builder().withProjectDir(temporaryFolder.root).build()
        task = project.tasks.register(
            "androidLintTextOutputTask",
            TaskForTest::class.java,
            FakeGradleWorkExecutor(project.objects, temporaryFolder.newFolder()),
            FakeLogger()
        ).get()
        task.analyticsService.set(FakeNoOpAnalyticsService())
    }

    @Test
    fun testBasic() {
        task.textReportInputFile.set(temporaryFolder.newFile().also { it.writeText("Foo") })
        task.returnValueInputFile.set(temporaryFolder.newFile().also { it.writeText("0") })
        task.outputStream.set(AndroidLintTextOutputTask.OutputStream.STDOUT)
        task.fatalOnly.set(false)
        task.android.set(true)
        task.abortOnError.set(true)
        task.taskAction()
        assertThat((task as TaskForTest).testLogger.lifeCycles).contains("Foo")
        assertThat((task as TaskForTest).testLogger.errors).isEmpty()
    }

    @Test
    fun testNoIssuesFound() {
        task.textReportInputFile.set(
            temporaryFolder.newFile().also { it.writeText("No issues found.") }
        )
        task.returnValueInputFile.set(temporaryFolder.newFile().also { it.writeText("0") })
        task.outputStream.set(AndroidLintTextOutputTask.OutputStream.STDOUT)
        task.fatalOnly.set(false)
        task.android.set(true)
        task.abortOnError.set(true)
        task.taskAction()
        assertThat((task as TaskForTest).testLogger.lifeCycles).isEmpty()
        assertThat((task as TaskForTest).testLogger.errors).isEmpty()
    }

    @Test
    fun testNoErrorsOrWarnings() {
        task.textReportInputFile.set(
            temporaryFolder.newFile().also { it.writeText("Foo bar 0 errors, 0 warnings.") }
        )
        task.returnValueInputFile.set(temporaryFolder.newFile().also { it.writeText("0") })
        task.outputStream.set(AndroidLintTextOutputTask.OutputStream.STDOUT)
        task.fatalOnly.set(false)
        task.android.set(true)
        task.abortOnError.set(true)
        task.taskAction()
        assertThat((task as TaskForTest).testLogger.lifeCycles).isEmpty()
        assertThat((task as TaskForTest).testLogger.errors).isEmpty()
    }

    @Test
    fun testStderr() {
        task.textReportInputFile.set(temporaryFolder.newFile().also { it.writeText("Foo") })
        task.returnValueInputFile.set(temporaryFolder.newFile().also { it.writeText("0") })
        task.outputStream.set(AndroidLintTextOutputTask.OutputStream.STDERR)
        task.fatalOnly.set(false)
        task.android.set(true)
        task.abortOnError.set(true)
        task.taskAction()
        assertThat((task as TaskForTest).testLogger.lifeCycles).isEmpty()
        assertThat((task as TaskForTest).testLogger.errors).contains("Foo")
    }

    @Test
    fun testNoTextOutput() {
        task.textReportInputFile.set(temporaryFolder.newFile().also { it.writeText("Foo") })
        task.returnValueInputFile.set(temporaryFolder.newFile().also { it.writeText("0") })
        task.outputStream.set(AndroidLintTextOutputTask.OutputStream.NONE)
        task.fatalOnly.set(false)
        task.android.set(true)
        task.abortOnError.set(true)
        task.taskAction()
        assertThat((task as TaskForTest).testLogger.lifeCycles).isEmpty()
        assertThat((task as TaskForTest).testLogger.errors).isEmpty()
    }

    @Test
    fun testFailure() {
        task.textReportInputFile.set(temporaryFolder.newFile().also { it.writeText("Foo") })
        task.returnValueInputFile.set(
            temporaryFolder.newFile().also { it.writeText("$ERRNO_ERRORS") }
        )
        task.outputStream.set(AndroidLintTextOutputTask.OutputStream.STDOUT)
        task.fatalOnly.set(false)
        task.android.set(true)
        task.abortOnError.set(true)
        try {
            task.taskAction()
            fail("expected RuntimeException")
        } catch (e: RuntimeException) {
            assertThat(e.message).contains("Lint found errors in the project")
            assertThat(e.message).contains("android {")
        }
        assertThat((task as TaskForTest).testLogger.lifeCycles).contains("Foo")
        assertThat((task as TaskForTest).testLogger.errors).isEmpty()
    }

    @Test
    fun testNonAndroidFailure() {
        task.textReportInputFile.set(temporaryFolder.newFile().also { it.writeText("Foo") })
        task.returnValueInputFile.set(
            temporaryFolder.newFile().also { it.writeText("$ERRNO_ERRORS") }
        )
        task.outputStream.set(AndroidLintTextOutputTask.OutputStream.STDOUT)
        task.fatalOnly.set(false)
        task.android.set(false)
        task.abortOnError.set(true)
        try {
            task.taskAction()
            fail("expected RuntimeException")
        } catch (e: RuntimeException) {
            assertThat(e.message).contains("Lint found errors in the project")
            assertThat(e.message).doesNotContain("android {")
        }
        assertThat((task as TaskForTest).testLogger.lifeCycles).contains("Foo")
        assertThat((task as TaskForTest).testLogger.errors).isEmpty()
    }

    @Test
    fun testFatalOnlyFailure() {
        task.textReportInputFile.set(temporaryFolder.newFile().also { it.writeText("Foo") })
        task.returnValueInputFile.set(
            temporaryFolder.newFile().also { it.writeText("$ERRNO_ERRORS") }
        )
        task.outputStream.set(AndroidLintTextOutputTask.OutputStream.STDOUT)
        task.fatalOnly.set(true)
        task.android.set(true)
        task.abortOnError.set(true)
        try {
            task.taskAction()
            fail("expected RuntimeException")
        } catch (e: RuntimeException) {
            assertThat(e.message).contains(
                "Lint found fatal errors while assembling a release target."
            )
            assertThat(e.message).contains("android {")
        }
        assertThat((task as TaskForTest).testLogger.lifeCycles).contains("Foo")
        assertThat((task as TaskForTest).testLogger.errors).isEmpty()
    }

    @Test
    fun testAbortOnErrorFalse() {
        task.textReportInputFile.set(temporaryFolder.newFile().also { it.writeText("Foo") })
        task.returnValueInputFile.set(
            temporaryFolder.newFile().also { it.writeText("$ERRNO_ERRORS") }
        )
        task.outputStream.set(AndroidLintTextOutputTask.OutputStream.STDOUT)
        task.fatalOnly.set(false)
        task.android.set(true)
        task.abortOnError.set(false)
        task.taskAction()
        assertThat((task as TaskForTest).testLogger.lifeCycles).contains("Foo")
        assertThat((task as TaskForTest).testLogger.errors).isEmpty()
    }

    @Test
    fun testNewBaselineFileCreated() {
        task.textReportInputFile.set(temporaryFolder.newFile().also { it.writeText("Foo") })
        task.returnValueInputFile.set(
            temporaryFolder.newFile().also { it.writeText("$ERRNO_CREATED_BASELINE") }
        )
        task.outputStream.set(AndroidLintTextOutputTask.OutputStream.STDOUT)
        task.fatalOnly.set(false)
        task.android.set(true)
        task.abortOnError.set(false)
        try {
            task.taskAction()
            fail("expected RuntimeException")
        } catch (e: RuntimeException) {
            assertThat(e.message).contains("Aborting build since new baseline file was created")
        }
        assertThat((task as TaskForTest).testLogger.lifeCycles).contains("Foo")
        assertThat((task as TaskForTest).testLogger.errors).isEmpty()
    }
}
