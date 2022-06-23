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

import com.android.build.gradle.integration.common.truth.ScannerSubject.Companion.assertThat
import com.android.build.gradle.integration.connected.utils.getEmulator
import com.android.build.gradle.integration.utp.UtpTestBase
import com.android.testutils.TestUtils
import com.android.testutils.truth.PathSubject.assertThat
import java.io.IOException
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test

/**
 * Connected tests using UTP test executor.
 */
class UtpConnectedTest : UtpTestBase() {

    companion object {
        @ClassRule
        @JvmField
        val EMULATOR = getEmulator()

        private const val TEST_RESULT_XML = "build/outputs/androidTest-results/connected/TEST-emulator-5554 - 10-_app-.xml"
        private const val LOGCAT = "build/outputs/androidTest-results/connected/emulator-5554 - 10/logcat-com.example.android.kotlin.ExampleInstrumentedTest-useAppContext.txt"
        private const val TEST_REPORT = "build/reports/androidTests/connected/com.example.android.kotlin.html"
        private const val TEST_RESULT_PB = "build/outputs/androidTest-results/connected/emulator-5554 - 10/test-result.pb"
        private const val AGGREGATED_TEST_RESULT_PB = "build/outputs/androidTest-results/connected/test-result.pb"
        private const val TEST_COV_XML = "build/reports/coverage/androidTest/debug/connected/report.xml"
        private const val ENABLE_UTP_TEST_REPORT_PROPERTY = "com.android.tools.utp.GradleAndroidProjectResolverExtension.enable"
        private const val TEST_ADDITIONAL_OUTPUT = "build/outputs/connected_android_test_additional_output/debugAndroidTest/connected/emulator-5554 - 10"
    }

    @Before
    @Throws(IOException::class)
    fun setUp() {
        // fail fast if no response
        project.addAdbTimeout();
        // run the uninstall tasks in order to (1) make sure nothing is installed at the beginning
        // of each test and (2) check the adb connection before taking the time to build anything.
        project.execute("uninstallAll")
    }

    override val deviceName: String = "emulator-5554"

    override fun selectModule(moduleName: String) {
        testTaskName = ":${moduleName}:connectedAndroidTest"
        testResultXmlPath = "${moduleName}/$TEST_RESULT_XML"
        testReportPath = "${moduleName}/$TEST_REPORT"
        testResultPbPath = "${moduleName}/$TEST_RESULT_PB"
        aggTestResultPbPath = "${moduleName}/$AGGREGATED_TEST_RESULT_PB"
        testCoverageXmlPath = "${moduleName}/$TEST_COV_XML"
        testLogcatPath = "${moduleName}/$LOGCAT"
        testAdditionalOutputPath = "${moduleName}/${TEST_ADDITIONAL_OUTPUT}"
    }

    @Test
    @Throws(Exception::class)
    fun connectedAndroidTestWithUtpTestResultListener() {
        selectModule("app")
        val initScriptPath = TestUtils.resolveWorkspacePath(
                "tools/adt/idea/utp/addGradleAndroidTestListener.gradle")

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
        selectModule("app")
        val initScriptPath = TestUtils.resolveWorkspacePath(
                "tools/adt/idea/utp/addGradleAndroidTestListener.gradle")

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
}
