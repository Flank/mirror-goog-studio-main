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

package com.android.build.gradle.internal.test.report

import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.io.Files
import java.io.File
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class TestReportTest {
    @get:Rule
    val tempDirRule = TemporaryFolder()

    private lateinit var resultsOutDir: File
    private lateinit var reportOutDir: File

    @Before
    fun setupDirectory() {
        resultsOutDir = tempDirRule.newFolder()
        reportOutDir = tempDirRule.newFolder()
    }

    @Test
    fun generateReport() {
        val reportXml = File(resultsOutDir, "TEST-Pixel_4_XL_API_30(AVD) - 11-app-.xml")
        Files.asCharSink(reportXml, Charsets.UTF_8).write("""
            <?xml version='1.0' encoding='UTF-8' ?>
            <testsuite name="com.example.myapplication.ExampleInstrumentedTest" tests="8" failures="1" errors="0" skipped="2" time="3.272" timestamp="2021-08-10T21:09:43" hostname="localhost">
              <properties>
                <property name="device" value="Pixel_4_XL_API_30(AVD) - 11" />
                <property name="flavor" value="" />
                <property name="project" value="app" />
              </properties>
              <testcase name="thisTestCaseShouldBeIgnored" classname="com.example.myapplication.ExampleInstrumentedTest" time="0.035">
                <skipped />
              </testcase>
              <testcase name="useAppContext1" classname="com.example.myapplication.ExampleInstrumentedTest" time="0.004" />
              <testcase name="useAppContext2" classname="com.example.myapplication.ExampleInstrumentedTest" time="0.002" />
              <testcase name="useAppContext3" classname="com.example.myapplication.ExampleInstrumentedTest" time="0.003" />
              <testcase name="useAppContext4" classname="com.example.myapplication.ExampleInstrumentedTest" time="0.001" />
              <testcase name="assumptionFailure" classname="com.example.myapplication.ExampleInstrumentedTest" time="0.005">
                <skipped />
              </testcase>
              <testcase name="failedTest" classname="com.example.myapplication.ExampleInstrumentedTest" time="0.0">
                <failure>java.lang.AssertionError: This test fails
            at org.junit.Assert.fail(Assert.java:88)
            at com.example.myapplication.ExampleInstrumentedTest.failedTest(ExampleInstrumentedTest.kt:64)</failure>
              </testcase>
              <testcase name="runActivity" classname="com.example.myapplication.ExampleInstrumentedTest" time="2.34" />
            </testsuite>
        """.trimIndent())

        TestReport(ReportType.SINGLE_FLAVOR, resultsOutDir, reportOutDir).generateReport()

        val indexHtml = File(reportOutDir, "index.html")
        assertThat(indexHtml).exists()
        assertThat(indexHtml).contains("""<div class="percent">87%</div>""")

        val moduleHtml = File(reportOutDir, "com.example.myapplication.html")
        assertThat(moduleHtml).exists()

        val classHtml = File(reportOutDir, "com.example.myapplication.ExampleInstrumentedTest.html")
        assertThat(classHtml).exists()
        assertThat(classHtml).contains("""<td class="success">passed (0.004s)</td>""")
        assertThat(classHtml).contains("""<td class="failures">failed (0s)</td>""")
        assertThat(classHtml).contains("""<td class="skipped">ignored (-)</td>""")
    }
}
