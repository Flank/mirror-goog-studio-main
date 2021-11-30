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
import kotlin.test.assertFailsWith

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
        task.outputStream.set(AndroidLintTextOutputTask.OutputStream.ABBREVIATED)
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

    @Test
    fun testFailureAbbreviated() {
        val textReportFile = temporaryFolder.newFile().also { it.writeText(
            """
                |lintKotlin/app/src/main/res/layout/activity_layout.xml:2: Warning: The resource R.layout.activity_layout appears to be unused [UnusedResources]
                |<RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
                |^
                |
                |   Explanation for issues of type "UnusedResources":
                |   Unused resources make applications larger and slow down builds.
                |
                |   The unused resource check can ignore tests. If you want to include
                |   resources that are only referenced from tests, consider packaging them in a
                |   test source set instead.
                |
                |   You can include test sources in the unused resource check by setting the
                |   system property lint.unused-resources.include-tests=true, and to exclude
                |   them (usually for performance reasons), use
                |   lint.unused-resources.exclude-tests=true.
                |
                |lintKotlin/app/src/main/AndroidManifest.xml:6: Error: Class referenced in the manifest, com.example.android.lint.kotlin.MainActivity, was not found in the project or the libraries [MissingClass]
                |        <activity android:name=".MainActivity" android:exported="false">
                |                                ~~~~~~~~~~~~~
                |
                |   Explanation for issues of type "MissingClass":
                |   If a class is referenced in the manifest or in a layout file, it must also
                |   exist in the project (or in one of the libraries included by the project.
                |   This check helps uncover typos in registration names, or attempts to rename
                |   or move classes without updating the XML references
                |   properly.
                |
                |   https://developer.android.com/guide/topics/manifest/manifest-intro.html
                |
                |lintKotlin/app/src/main/kotlin/com/example/android/lint/kotlin/SampleFragment.java:5: Error: This fragment should provide a default constructor (a public constructor with no arguments) (com.example.android.lint.kotlin.SampleFragment) [ValidFragment]
                |public class SampleFragment extends BaseFragment {
                |             ~~~~~~~~~~~~~~
                |lintKotlin/app/src/main/kotlin/com/example/android/lint/kotlin/SampleFragment.java:6: Error: Avoid non-default constructors in fragments: use a default constructor plus Fragment#setArguments(Bundle) instead [ValidFragment]
                |    public SampleFragment(String foo) { // Deliberate lint error
                |           ~~~~~~~~~~~~~~
                |
                |   Explanation for issues of type "ValidFragment":
                |   From the Fragment documentation:
                |   Every fragment must have an empty constructor, so it can be instantiated
                |   when restoring its activity's state. It is strongly recommended that
                |   subclasses do not have other constructors with parameters, since these
                |   constructors will not be called when the fragment is re-instantiated;
                |   instead, arguments can be supplied by the caller with setArguments(Bundle)
                |   and later retrieved by the Fragment with getArguments().
                |
                |   Note that this is no longer true when you are using
                |   androidx.fragment.app.Fragment; with the FragmentFactory you can supply any
                |   arguments you want (as of version androidx version 1.1).
                |
                |   https://developer.android.com/reference/android/app/Fragment.html#Fragment()
                |
                |lintKotlin/app/src/main/AndroidManifest.xml:6: Error: A launchable activity must be exported as of Android 12, which also makes it available to other apps. [IntentFilterExportedReceiver]
                |        <activity android:name=".MainActivity" android:exported="false">
                |                                               ~~~~~~~~~~~~~~~~~~~~~~~~
                |
                |   Explanation for issues of type "IntentFilterExportedReceiver":
                |   Apps targeting Android 12 and higher are required to specify an explicit
                |   value for android:exported when the corresponding component has an intent
                |   filter defined. Otherwise, installation will fail. Set it to true to make
                |   this activity accessible to other apps, and false to limit it to be used
                |   only by this app or the OS. For launch activities, this should be set to
                |   true; otherwise, the app will fail to launch.
                |
                |   Previously, android:exported for components without any intent filters
                |   present used to default to false, and when intent filters were present, the
                |   default was true. Defaults which change value based on other values are
                |   confusing and lead to apps accidentally exporting components as a
                |   side-effect of adding intent filters. This is a security risk, and we have
                |   made this change to avoid introducing accidental vulnerabilities.
                |
                |   While the default without intent filters remains unchanged, it is now
                |   required to explicitly specify a value when intent filters are present. Any
                |   app failing to meet this requirement will fail to install on any Android
                |   version after Android 11.
                |
                |   We recommend setting android:exported to false (even on previous versions
                |   of Android prior to this requirement) unless you have a good reason to
                |   export a particular component.
                |
                |9 errors, 4 warnings
                |
                """.trimMargin()
            )
        }
        task.textReportInputFile.set(textReportFile)
        task.returnValueInputFile.set(temporaryFolder.newFile().also { it.writeText("$ERRNO_ERRORS") })
        task.outputStream.set(AndroidLintTextOutputTask.OutputStream.ABBREVIATED)
        task.fatalOnly.set(false)
        task.android.set(true)
        task.abortOnError.set(true)
        val e = assertFailsWith<RuntimeException> { task.taskAction() }
        assertThat(e.message).contains("Lint found errors in the project")
        assertThat(e.message).contains("android {")
        val expected = """
            |Lint found 9 errors, 4 warnings. First failure:
            |
            |lintKotlin/app/src/main/AndroidManifest.xml:6: Error: Class referenced in the manifest, com.example.android.lint.kotlin.MainActivity, was not found in the project or the libraries [MissingClass]
            |        <activity android:name=".MainActivity" android:exported="false">
            |                                ~~~~~~~~~~~~~
            |
            |   Explanation for issues of type "MissingClass":
            |   If a class is referenced in the manifest or in a layout file, it must also
            |   exist in the project (or in one of the libraries included by the project.
            |   This check helps uncover typos in registration names, or attempts to rename
            |   or move classes without updating the XML references
            |   properly.
            |
            |   https://developer.android.com/guide/topics/manifest/manifest-intro.html
            |
            |
            |The full lint text report is located at:
            |  ${textReportFile.absolutePath}
        """.trimMargin()
        assertThat((task as TaskForTest).testLogger.lifeCycles.single()).isEqualTo(expected)
        assertThat((task as TaskForTest).testLogger.errors).isEmpty()
    }
}
