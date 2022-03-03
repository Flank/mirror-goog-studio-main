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

import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.builder
import com.android.build.gradle.integration.common.truth.ScannerSubject.Companion.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.integration.connected.utils.getEmulator
import com.android.testutils.TestUtils
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.truth.Truth.assertThat
import com.google.testing.platform.plugin.android.info.host.proto.AndroidTestDeviceInfoProto.AndroidTestDeviceInfo
import com.google.testing.platform.proto.api.core.TestSuiteResultProto.TestSuiteResult
import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

/**
 * Connected tests using UTP test executor.
 */
class UtpConnectedTest {

    companion object {
        @ClassRule
        @JvmField
        val EMULATOR = getEmulator()

        const val TEST_RESULT_XML = "build/outputs/androidTest-results/connected/TEST-emulator-5554 - 10-_app-.xml"
        const val LOGCAT = "build/outputs/androidTest-results/connected/emulator-5554 - 10/logcat-com.example.android.kotlin.ExampleInstrumentedTest-useAppContext.txt"
        const val TEST_REPORT = "build/reports/androidTests/connected/com.example.android.kotlin.html"
        const val TEST_RESULT_PB = "build/outputs/androidTest-results/connected/emulator-5554 - 10/test-result.pb"
        const val AGGREGATED_TEST_RESULT_PB = "build/outputs/androidTest-results/connected/test-result.pb"
        const val TEST_COV_XML = "build/reports/coverage/androidTest/debug/report.xml"
        const val ENABLE_UTP_TEST_REPORT_PROPERTY = "com.android.tools.utp.GradleAndroidProjectResolverExtension.enable"
        const val TEST_ADDITIONAL_OUTPUT = "build/outputs/connected_android_test_additional_output/debugAndroidTest/connected/emulator-5554 - 10"
    }

    @get:Rule
    var project = builder()
            .fromTestProject("utp")
            .create()

    @Before
    @Throws(IOException::class)
    fun setUp() {
        // fail fast if no response
        project.addAdbTimeout();
        // run the uninstall tasks in order to (1) make sure nothing is installed at the beginning
        // of each test and (2) check the adb connection before taking the time to build anything.
        project.execute("uninstallAll")
    }

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
    fun connectedAndroidTest() {
        val testTaskName = ":app:connectedAndroidTest"
        val testResultXmlPath = "app/$TEST_RESULT_XML"
        val testReportPath = "app/$TEST_REPORT"
        val testResultPbPath = "app/$TEST_RESULT_PB"
        val aggTestResultPbPath = "app/$AGGREGATED_TEST_RESULT_PB"

        project.executor()
            .run(testTaskName)

        assertThat(project.file(testResultXmlPath)).exists()
        assertThat(project.file(testReportPath)).exists()
        assertThat(project.file(testResultPbPath)).exists()
        assertThat(project.file(aggTestResultPbPath)).exists()

        val deviceInfo = getDeviceInfo(project.file(aggTestResultPbPath))
        assertThat(deviceInfo).isNotNull()
        assertThat(deviceInfo?.name).isEqualTo("emulator-5554")

        // Run the task again after clean. This time the task configuration is
        // restored from the configuration cache. We expect no crashes.
        project.executor().run("clean")

        assertThat(project.file(testResultXmlPath)).doesNotExist()
        assertThat(project.file(testReportPath)).doesNotExist()
        assertThat(project.file(testResultPbPath)).doesNotExist()
        assertThat(project.file(aggTestResultPbPath)).doesNotExist()

        project.executor().run(testTaskName)

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
    fun connectedAndroidTestWithCodeCoverage() {
        val testTaskName = ":app:connectedCheck"
        val testReportPath = "app/$TEST_REPORT"
        val testResultPbPath = "app/$TEST_RESULT_PB"
        val testCoverageXmlPath = "app/$TEST_COV_XML"

        enableCodeCoverage("app")

        project.executor().run(testTaskName)

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
    fun connectedAndroidTestWithTestFailures() {
        val testTaskName = ":appWithTestFailures:connectedAndroidTest"
        val testReportPath = "appWithTestFailures/$TEST_REPORT"
        val testResultPbPath = "appWithTestFailures/$TEST_RESULT_PB"

        project.executor().expectFailure().run(testTaskName)
        assertThat(project.file(testReportPath)).exists()
        assertThat(project.file(testResultPbPath)).exists()
    }

    @Test
    @Throws(Exception::class)
    fun connectedAndroidTestWithOrchestrator() {
        val testTaskName = ":app:connectedAndroidTest"
        val testReportPath = "app/$TEST_REPORT"
        val testResultPbPath = "app/$TEST_RESULT_PB"

        enableAndroidTestOrchestrator("app")

        project.executor().run(testTaskName)

        assertThat(project.file(testReportPath)).exists()
        assertThat(project.file(testResultPbPath)).exists()
    }

    @Test
    @Throws(Exception::class)
    fun connectedAndroidTestWithOrchestratorAndCodeCoverage() {
        val testTaskName = ":app:connectedCheck"
        val testReportPath = "app/$TEST_REPORT"
        val testResultPbPath = "app/$TEST_RESULT_PB"
        val testCoverageXmlPath = "app/$TEST_COV_XML"

        enableAndroidTestOrchestrator("app")
        enableCodeCoverage("app")

        project.executor().run(testTaskName)

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
    fun connectedAndroidTestWithUtpTestResultListener() {
        val initScriptPath = TestUtils.resolveWorkspacePath(
                "tools/adt/idea/utp/addGradleAndroidTestListener.gradle")
        val testTaskName = ":app:connectedAndroidTest"
        val testReportPath = "app/$TEST_REPORT"
        val testResultPbPath = "app/$TEST_RESULT_PB"

        val result = project.executor()
                .withArgument("--init-script")
                .withArgument(initScriptPath.toString())
                .withArgument("-P${ENABLE_UTP_TEST_REPORT_PROPERTY}=true")
                .run(testTaskName)

        result.stdout.use {
            assertThat(it).contains("<UTP_TEST_RESULT_ON_TEST_RESULT_EVENT>")
            assertThat(it).contains("</UTP_TEST_RESULT_ON_TEST_RESULT_EVENT>")
        }
        assertThat(project.file(testReportPath)).exists()
        assertThat(project.file(testResultPbPath)).exists()

        // Run the task again after clean. This time the task configuration is
        // restored from the configuration cache. We expect no crashes.
        project.executor().run("clean")

        assertThat(project.file(testReportPath)).doesNotExist()
        assertThat(project.file(testResultPbPath)).doesNotExist()

        val resultWithConfigCache = project.executor()
                .withArgument("--init-script")
                .withArgument(initScriptPath.toString())
                .withArgument("-P${ENABLE_UTP_TEST_REPORT_PROPERTY}=true")
                .run(testTaskName)

        resultWithConfigCache.stdout.use {
            assertThat(it).contains("<UTP_TEST_RESULT_ON_TEST_RESULT_EVENT>")
            assertThat(it).contains("</UTP_TEST_RESULT_ON_TEST_RESULT_EVENT>")
        }
        assertThat(project.file(testReportPath)).exists()
        assertThat(project.file(testResultPbPath)).exists()
    }

    @Test
    @Throws(Exception::class)
    fun connectedAndroidTestWithUtpTestResultListenerAndTestReportingDisabled() {
        val initScriptPath = TestUtils.resolveWorkspacePath(
                "tools/adt/idea/utp/addGradleAndroidTestListener.gradle")
        val testTaskName = ":app:connectedAndroidTest"
        val testReportPath = "app/$TEST_REPORT"
        val testResultPbPath = "app/$TEST_RESULT_PB"

        val result = project.executor()
                .withArgument("--init-script")
                .withArgument(initScriptPath.toString())
                .run(testTaskName)

        result.stdout.use {
            assertThat(it).doesNotContain("<UTP_TEST_RESULT_ON_TEST_RESULT_EVENT>")
            assertThat(it).doesNotContain("</UTP_TEST_RESULT_ON_TEST_RESULT_EVENT>")
        }
        assertThat(project.file(testReportPath)).exists()
        assertThat(project.file(testResultPbPath)).exists()
    }

    @Test
    @Throws(Exception::class)
    fun connectedAndroidTestWithLogcat() {
        val testTaskName = ":app:connectedAndroidTest"
        val testLogcatPath = "app/$LOGCAT"

        project.executor().run(testTaskName)

        assertThat(project.file(testLogcatPath)).exists()
        val logcatText = project.file(testLogcatPath).readText()
        assertThat(logcatText).contains("TestRunner: started: useAppContext(com.example.android.kotlin.ExampleInstrumentedTest)")
        assertThat(logcatText).contains("TestLogger: test logs")
        assertThat(logcatText).contains("TestRunner: finished: useAppContext(com.example.android.kotlin.ExampleInstrumentedTest)")
    }

    @Test
    @Throws(Exception::class)
    fun connectedAndroidTestFromTestOnlyModule() {
        val testTaskName = ":testOnlyModule:connectedAndroidTest"
        val testReportPath = "testOnlyModule/$TEST_REPORT"
        val testResultPbPath = "testOnlyModule/$TEST_RESULT_PB"

        project.executor().run(testTaskName)

        assertThat(project.file(testReportPath)).exists()
        assertThat(project.file(testResultPbPath)).exists()
    }

    @Test
    @Throws(Exception::class)
    fun connectedAndroidTestWithAdditionalTestOutputUsingTestStorageService() {
        enableTestStorageService("app")
        val testTaskName = ":app:connectedCheck"

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
        Files.createDirectories(testStorageServiceExampleTest.getParent())
        Files.write(testStorageServiceExampleTest, testSrc.toByteArray())

        project.executor().run(testTaskName)

        assertThat(project.file("app/${TEST_ADDITIONAL_OUTPUT}/myTestStorageOutputFile1"))
            .contains("output message1")
        assertThat(project.file("app/${TEST_ADDITIONAL_OUTPUT}/myTestStorageOutputFile2.txt"))
            .contains("output message2")
        assertThat(project.file("app/${TEST_ADDITIONAL_OUTPUT}/subdir/myTestStorageOutputFile3"))
            .contains("output message3")
        assertThat(project.file("app/${TEST_ADDITIONAL_OUTPUT}/subdir/nested/myTestStorageOutputFile4"))
            .contains("output message4")
        assertThat(project.file("app/${TEST_ADDITIONAL_OUTPUT}/subdir/white space/myTestStorageOutputFile5"))
            .contains("output message5")
    }
}
