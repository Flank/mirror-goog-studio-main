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

package com.android.build.gradle.integration.utp

import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.truth.PathSubject.assertThat
import com.android.tools.utp.plugins.host.device.info.proto.AndroidTestDeviceInfoProto.AndroidTestDeviceInfo
import com.google.common.truth.Truth.assertThat
import com.google.testing.platform.proto.api.core.TestSuiteResultProto.TestSuiteResult
import java.io.File
import java.nio.file.Files
import org.junit.Rule
import org.junit.Test

/**
 * A base test class for UTP integration tests. Tests defined in this class will be
 * executed against both connected check and managed devices to ensure the feature
 * parity.
 */
abstract class UtpTestBase {

    lateinit var testTaskName: String
    lateinit var testResultXmlPath: String
    lateinit var testReportPath: String
    lateinit var testResultPbPath: String
    lateinit var aggTestResultPbPath: String
    lateinit var testCoverageXmlPath: String
    lateinit var testLogcatPath: String
    lateinit var testAdditionalOutputPath: String

    @get:Rule
    var project = GradleTestProject.builder()
        .fromTestProject("utp")
        .create()

    open val executor: GradleTaskExecutor
        get() = project.executor()

    abstract val deviceName: String

    abstract fun selectModule(moduleName: String)

    private fun enableAndroidTestOrchestrator(subProjectName: String) {
        val subProject = project.getSubproject(subProjectName)
        TestFileUtils.appendToFile(
            subProject.buildFile,
            """
            android.testOptions.execution 'ANDROIDX_TEST_ORCHESTRATOR'
            // Orchestrator requires some setup time and it usually takes
            // about an minute. Increase the timeout for running "am instrument" command
            // to 3 minutes.
            android.adbOptions.timeOutInMs=180000

            android.defaultConfig.testInstrumentationRunnerArguments useTestStorageService: 'true'
            android.defaultConfig.testInstrumentationRunnerArguments clearPackageData: 'true'

            dependencies {
              androidTestUtil 'androidx.test:orchestrator:1.4.0-alpha06'
              androidTestUtil 'androidx.test.services:test-services:1.4.0-alpha06'
            }
            """.trimIndent())
    }

    private fun enableCodeCoverage(subProjectName: String) {
        val subProject = project.getSubproject(subProjectName)
        TestFileUtils.appendToFile(
            subProject.buildFile,
            """
            android.buildTypes.debug.testCoverageEnabled true
            android.defaultConfig.testInstrumentationRunnerArguments useTestStorageService: 'true'

            dependencies {
              androidTestUtil 'androidx.test.services:test-services:1.4.0-alpha06'
            }
            """.trimIndent())
    }

    private fun enableTestStorageService(subProjectName: String) {
        val subProject = project.getSubproject(subProjectName)
        TestFileUtils.appendToFile(
            subProject.buildFile,
            """
            dependencies {
              androidTestUtil 'androidx.test.services:test-services:1.4.0-alpha06'
            }
            """.trimIndent())
    }

    @Test
    @Throws(Exception::class)
    fun androidTest() {
        selectModule("app")

        executor.run(testTaskName)

        assertThat(project.file(testResultXmlPath)).exists()
        assertThat(project.file(testReportPath)).exists()
        assertThat(project.file(testResultPbPath)).exists()
        assertThat(project.file(aggTestResultPbPath)).exists()

        val deviceInfo = getDeviceInfo(project.file(aggTestResultPbPath))
        assertThat(deviceInfo).isNotNull()
        assertThat(deviceInfo?.name).isEqualTo(deviceName)

        // Run the task again after clean. This time the task configuration is
        // restored from the configuration cache. We expect no crashes.
        executor.run("clean")

        assertThat(project.file(testResultXmlPath)).doesNotExist()
        assertThat(project.file(testReportPath)).doesNotExist()
        assertThat(project.file(testResultPbPath)).doesNotExist()
        assertThat(project.file(aggTestResultPbPath)).doesNotExist()

        executor.run(testTaskName)

        assertThat(project.file(testResultXmlPath)).exists()
        assertThat(project.file(testReportPath)).exists()
        assertThat(project.file(testResultPbPath)).exists()
        assertThat(project.file(aggTestResultPbPath)).exists()
    }

    private fun getDeviceInfo(aggTestResultPb: File): AndroidTestDeviceInfo? {
        val testSuiteResult = aggTestResultPb.inputStream().use {
            TestSuiteResult.parseFrom(it)
        }
        return testSuiteResult.testResultList.asSequence()
            .flatMap { testResult ->
                testResult.outputArtifactList
            }
            .filter { artifact ->
                artifact.label.label == "device-info" && artifact.label.namespace == "android"
            }
            .map { artifact ->
                File(artifact.sourcePath.path).inputStream().use {
                    AndroidTestDeviceInfo.parseFrom(it)
                }
            }
            .firstOrNull()
    }

    @Test
    @Throws(Exception::class)
    fun androidTestWithCodeCoverage() {
        selectModule("app")
        enableCodeCoverage("app")

        executor.run(testTaskName)

        assertThat(project.file(testReportPath)).exists()
        assertThat(project.file(testResultPbPath)).exists()
        assertThat(project.file(testCoverageXmlPath)).contains(
            """<method name="stubFuncForTestingCodeCoverage" desc="()V" line="9">"""
        )
        assertThat(project.file(testCoverageXmlPath)).contains(
            """<counter type="INSTRUCTION" missed="3" covered="5"/>"""
        )
    }

    @Test
    @Throws(Exception::class)
    fun androidTestWithTestFailures() {
        selectModule("appWithTestFailures")

        executor.expectFailure().run(testTaskName)
        assertThat(project.file(testReportPath)).exists()
        assertThat(project.file(testResultPbPath)).exists()
    }

    @Test
    @Throws(Exception::class)
    fun androidTestWithOrchestrator() {
        selectModule("app")
        enableAndroidTestOrchestrator("app")

        executor.run(testTaskName)

        assertThat(project.file(testReportPath)).exists()
        assertThat(project.file(testResultPbPath)).exists()
    }

    @Test
    @Throws(Exception::class)
    fun androidTestWithOrchestratorAndCodeCoverage() {
        selectModule("app")
        enableAndroidTestOrchestrator("app")
        enableCodeCoverage("app")

        executor.run(testTaskName)

        assertThat(project.file(testReportPath)).exists()
        assertThat(project.file(testResultPbPath)).exists()
        assertThat(project.file(testCoverageXmlPath)).contains(
            """<method name="stubFuncForTestingCodeCoverage" desc="()V" line="9">"""
        )
        assertThat(project.file(testCoverageXmlPath)).contains(
            """<counter type="INSTRUCTION" missed="3" covered="5"/>"""
        )
    }

    @Test
    @Throws(Exception::class)
    fun connectedAndroidTestWithLogcat() {
        selectModule("app")

        executor.run(testTaskName)

        assertThat(project.file(testLogcatPath)).exists()
        val logcatText = project.file(testLogcatPath).readText()
        assertThat(logcatText).contains("TestRunner: started: useAppContext(com.example.android.kotlin.ExampleInstrumentedTest)")
        assertThat(logcatText).contains("TestLogger: test logs")
        assertThat(logcatText).contains("TestRunner: finished: useAppContext(com.example.android.kotlin.ExampleInstrumentedTest)")
    }

    @Test
    @Throws(Exception::class)
    fun connectedAndroidTestFromTestOnlyModule() {
        selectModule("testOnlyModule")

        executor.run(testTaskName)

        assertThat(project.file(testReportPath)).exists()
        assertThat(project.file(testResultPbPath)).exists()
    }

    @Test
    @Throws(Exception::class)
    fun connectedAndroidTestWithAdditionalTestOutputUsingTestStorageService() {
        selectModule("app")
        enableTestStorageService("app")

        val testSrc = """
            package com.example.helloworld

            import androidx.test.ext.junit.runners.AndroidJUnit4
            import androidx.test.services.storage.TestStorage
            import org.junit.Test
            import org.junit.runner.RunWith

            @RunWith(AndroidJUnit4::class)
            class TestStorageServiceExampleTest {
                @Test
                fun writeFileUsingTestStorageService() {
                    TestStorage().openOutputFile("myTestStorageOutputFile1").use {
                        it.write("output message1".toByteArray())
                    }
                    TestStorage().openOutputFile("myTestStorageOutputFile2.txt").use {
                        it.write("output message2".toByteArray())
                    }
                    TestStorage().openOutputFile("subdir/myTestStorageOutputFile3").use {
                        it.write("output message3".toByteArray())
                    }
                    TestStorage().openOutputFile("subdir/nested/myTestStorageOutputFile4").use {
                        it.write("output message4".toByteArray())
                    }
                    TestStorage().openOutputFile("subdir/white space/myTestStorageOutputFile5").use {
                        it.write("output message5".toByteArray())
                    }
                }
            }
        """.trimIndent()
        val testStorageServiceExampleTest = project.projectDir
            .toPath()
            .resolve("app/src/androidTest/java/com/example/helloworld/TestStorageServiceExampleTest.kt")
        Files.createDirectories(testStorageServiceExampleTest.parent)
        Files.write(testStorageServiceExampleTest, testSrc.toByteArray())

        executor.run(testTaskName)

        assertThat(project.file("${testAdditionalOutputPath}/myTestStorageOutputFile1"))
            .contains("output message1")
        assertThat(project.file("${testAdditionalOutputPath}/myTestStorageOutputFile2.txt"))
            .contains("output message2")
        assertThat(project.file("${testAdditionalOutputPath}/subdir/myTestStorageOutputFile3"))
            .contains("output message3")
        assertThat(project.file("${testAdditionalOutputPath}/subdir/nested/myTestStorageOutputFile4"))
            .contains("output message4")
        assertThat(project.file("${testAdditionalOutputPath}/subdir/white space/myTestStorageOutputFile5"))
            .contains("output message5")
    }
}
