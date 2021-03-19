/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProjectBuilder
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class JacocoWithUnitTestReportTest {

    @get:Rule
    val testProject = GradleTestProjectBuilder()
        .fromTestProject("unitTesting")
        .create()

    @Before
    fun setup() {
        TestFileUtils.appendToFile(testProject.buildFile,
        """
          apply plugin: 'jacoco'
          android {
            buildTypes {
                debug {
                    testCoverageEnabled true
                }
            }
         }
        """.trimIndent())
    }

    @Test
    fun `test expected report contents`() {
        testProject.executor().run("createDebugUnitTestCoverageReport")
        val generatedCoverageReport = FileUtils.join(
            testProject.buildDir,
            "reports",
            "coverage",
            "test",
            "debug",
            "index.html"
        )
        assertThat(generatedCoverageReport.exists()).isTrue();
        val generatedCoverageReportHTML = generatedCoverageReport.readLines().joinToString("\n")
        val reportTitle = Regex("<span class=\"el_report\">(.*?)</span")
            .find(generatedCoverageReportHTML)
        val totalCoverageMetricsContents = Regex("<tfoot>(.*?)</tfoot>")
            .find(generatedCoverageReportHTML)
        val totalCoverageInfo = Regex("<td class=\"ctr2\">(.*?)</td>")
                .find(totalCoverageMetricsContents?.groups?.first()!!.value)
        val totalUnitTestCoveragePercentage = totalCoverageInfo!!.groups[1]!!.value
        // Checks if the report title is expected.
        assertThat(reportTitle!!.groups[1]!!.value).isEqualTo("debug")
        // Checks if the total line coverage on unit tests exceeds 0% i.e
        assertThat(totalUnitTestCoveragePercentage.trimEnd('%').toInt() > 0).isTrue()
    }
}
