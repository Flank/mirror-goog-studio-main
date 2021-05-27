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

package com.android.build.gradle.integration.connected.application

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.builder
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.integration.connected.utils.getEmulator
import com.android.testutils.truth.PathSubject.assertThat
import com.google.testing.platform.proto.api.core.TestArtifactProto
import com.google.testing.platform.proto.api.core.TestResultProto
import com.google.testing.platform.proto.api.core.TestSuiteResultProto
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.gradle.tooling.BuildException
import org.junit.Before
import org.junit.ClassRule
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

/**
 * Connected tests for Android Test Failure Retention.
 */
class FailureRetentionConnectedTest {

    companion object {

        @ClassRule
        @JvmField
        val EMULATOR = getEmulator()
    }

    @get:Rule
    var project = builder()
        .fromTestProject("failureRetention")
        .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.WARN) // b/158092419
        .addGradleProperties("org.gradle.unsafe.configuration-cache.max-problems=45")
        .create()

    @Before
    @Throws(IOException::class)
    fun setUp() {
        // fail fast if no response
        project.getSubproject("app").addAdbTimeout()
        // run the uninstall tasks in order to (1) make sure nothing is installed at the beginning
        // of each test and (2) check the adb connection before taking the time to build anything.
        project.execute("uninstallAll")
    }

    @Test
    @Throws(Exception::class)
    fun connectedAndroidTest() {
        project.executor()
            .withArguments(
                listOf(
                    "-Dandroid.emulator.home=${System.getProperty("user.dir")}/.android",
                    "-Pandroid.testInstrumentationRunnerArguments.class=" +
                            "com.example.android.kotlin.ExampleInstrumentedTest#useAppContext"
                )
            )
            .run("connectedAndroidTest")
        val connectedDir = project.projectDir
            .resolve("app/build/outputs/androidTest-results/connected/emulator-5554 - 10")
        assertThat(connectedDir.resolve("test-result.pb")).exists()
    }

    @Test
    @Throws(Exception::class)
    fun connectedAndroidTestWithFailures() {
        assertFailsWith<BuildException> {
            project.executor()
                .withArguments(
                    listOf(
                        "-Dandroid.emulator.home=${System.getProperty("user.dir")}/.android",
                        "-Pandroid.testInstrumentationRunnerArguments.class=" +
                                "com.example.android.kotlin.ExampleInstrumentedTest"
                    )
                )
                .run("connectedAndroidTest")
        }
        validateFailureOutputs()
    }

    @Test
    @Throws(Exception::class)
    fun connectedAndroidTestWithOrchestratorAndFailures() {
        TestFileUtils.appendToFile(
            project.getSubproject("app").buildFile,
            "\n"
                    + "android.testOptions.execution 'ANDROIDX_TEST_ORCHESTRATOR'\n"
        )

        assertFailsWith<BuildException> {
            project.executor()
                .withArguments(
                    listOf(
                        "-Dandroid.emulator.home=${System.getProperty("user.dir")}/.android",
                        "-Pandroid.testInstrumentationRunnerArguments.class=" +
                                "com.example.android.kotlin.ExampleInstrumentedTest"
                    )
                )
                .run("connectedAndroidTest")
        }
        validateFailureOutputs()
    }

    private fun validateSnapshotArtifact(path: String) {
        assertThat(File(path)).exists()
        FileInputStream(path).use { inputStream ->
            TarArchiveInputStream(inputStream).use { tarInputStream ->
                var hasSnapshotPb = false
                var entry: TarArchiveEntry?
                while (tarInputStream.nextTarEntry.also { entry = it } != null) {
                    if (entry!!.name == "snapshot.pb") {
                        hasSnapshotPb = true
                        break
                    }
                }
                assertTrue(hasSnapshotPb, "Snapshot file ${path} corrupted")
            }
        }
    }

    private fun validateIceboxArtifacts(testResult: TestResultProto.TestResult) {
        testResult.outputArtifactList.first {
            it.label.label == "icebox.info" && it.label.namespace == "android"
        }.also { artifact ->
            assertThat(File(artifact.sourcePath.path)).exists()
        }
        testResult.outputArtifactList.first {
            it.label.label == "icebox.snapshot" && it.label.namespace == "android"
        }.also { artifact ->
            validateSnapshotArtifact(artifact.sourcePath.path)
        }
    }

    private fun validateFailureOutputs() {
        val connectedDir = project.projectDir
            .resolve("app/build/outputs/androidTest-results/connected/emulator-5554 - 10")
        val testResultFile = connectedDir.resolve("test-result.pb")
        assertThat(testResultFile).exists()
        val testSuiteResultProto = TestSuiteResultProto.TestSuiteResult.parseFrom(
            testResultFile.inputStream())
        testSuiteResultProto.testResultList.first {
            it.testCase.testMethod == "failingTest0"
        }.also {
            validateIceboxArtifacts(it)
        }
        testSuiteResultProto.testResultList.first {
            it.testCase.testMethod == "failingTest1"
        }.also {
            validateIceboxArtifacts(it)
        }
    }

    @Test
    @Throws(Exception::class)
    fun connectedAndroidTestWithUncaughtExceptions() {
        assertFailsWith<BuildException> {
            project.executor()
                .withArguments(
                    listOf(
                        "-Dandroid.emulator.home=${System.getProperty("user.dir")}/.android",
                        "-Pandroid.testInstrumentationRunnerArguments.class=" +
                                "com.example.android.kotlin.UncaughtExceptionInstrumentedTest"
                    )
                )
                .run("connectedAndroidTest")
        }
        val connectedDir = project.projectDir
            .resolve("app/build/outputs/androidTest-results/connected/emulator-5554 - 10")
        val testResultFile = connectedDir.resolve("test-result.pb")
        assertThat(testResultFile).exists()
        val testSuiteResultProto = TestSuiteResultProto.TestSuiteResult.parseFrom(
            testResultFile.inputStream())
        var snapshotArtifact: TestArtifactProto.Artifact? = null

        testSuiteResultProto.testResultList.first {
            it.testCase.testMethod == "uncaughtExceptionTest"
        }.also { testResult ->
            snapshotArtifact = testResult.outputArtifactList.firstOrNull {
                it.label.label == "icebox.snapshot" && it.label.namespace == "android"
            }
        }
        // TODO(b/188572060): Re-enable the following assert once the bug is fixed.
        // Snapshot should not be taken for non-supported type of exceptions however due
        // to synchronization issues between UTP test listener and emulator state,
        // it occasionally attaches the snapshot to a wrong test case.
        // assertThat(snapshotArtifact).isNull()
        testSuiteResultProto.testResultList.first {
            it.testCase.testMethod == "assertFailureTest"
        }.also { testResult ->
            val newSnapshotArtifact = testResult.outputArtifactList.firstOrNull {
                it.label.label == "icebox.snapshot" && it.label.namespace == "android"
            }
            // The snapshot artifact might be assigned to the wrong failure. But there must be only
            // 1 snapshot artifact. (That is, until we add support for uncaught exceptions.)
            assertTrue((snapshotArtifact != null) xor (newSnapshotArtifact != null))
            if (snapshotArtifact == null) {
                snapshotArtifact = newSnapshotArtifact
            }
        }
        validateSnapshotArtifact(snapshotArtifact!!.sourcePath.path)
    }
}
