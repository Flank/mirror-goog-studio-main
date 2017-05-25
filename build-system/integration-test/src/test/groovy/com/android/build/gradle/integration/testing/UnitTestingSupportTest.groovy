/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.integration.testing

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import org.junit.Rule
import org.junit.Test

import static com.android.build.gradle.integration.testing.JUnitResults.Outcome.PASSED
import static com.android.build.gradle.integration.testing.JUnitResults.Outcome.SKIPPED
import static com.google.common.truth.Truth.assertThat
/**
 * Meta-level tests for the app-level unit testing support. Checks the default values mode.
 */
class UnitTestingSupportTest {
    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("unitTesting")
            .create()

    @Test
    public void appProject() throws Exception {
        doTestProject(project)
    }

    @Test
    public void libProject() throws Exception {
        TestFileUtils.searchAndReplace(
                project.buildFile,
                "com.android.application",
                "com.android.library")
        doTestProject(project)
    }

    private static void doTestProject(GradleTestProject project) {
        project.execute("clean", "test")

        for (variant in ["debug", "release"]) {
            def dirName = "test${variant.capitalize()}UnitTest"
            def unitTestXml = "build/test-results/${dirName}/TEST-com.android.tests.UnitTest.xml"
            def unitTextResults = new JUnitResults(project.file(unitTestXml))

            assertThat(unitTextResults.stdErr).contains("INFO: I can use commons-logging")

            checkResults(
                    unitTestXml,
                    [
                            "aarDependencies",
                            "commonsLogging",
                            "enums",
                            "exceptions",
                            "instanceFields",
                            "javaResourcesOnClasspath",
                            "kotlinProductionCode",
                            "mockFinalClass",
                            "mockFinalMethod",
                            "mockInnerClass",
                            "prodJavaResourcesOnClasspath",
                            "prodRClass",
                            "referenceProductionCode",
                            "taskConfiguration",
                    ],
                    ["thisIsIgnored"],
                    project)

            checkResults(
                    "build/test-results/${dirName}/TEST-com.android.tests.NonStandardName.xml",
                    ["passingTest"],
                    [],
                    project)

            checkResults(
                    "build/test-results/${dirName}/TEST-com.android.tests.TestInKotlin.xml",
                    ["passesInKotlin"],
                    [],
                    project)
        }
    }

    private static void checkResults(
            String xmlPath,
            ArrayList<String> passed,
            ArrayList<String> ignored,
            GradleTestProject project) {
        def results = new JUnitResults(project.file(xmlPath))
        assertThat(results.allTestCases).containsExactlyElementsIn(ignored + passed)
        passed.each { assert results.outcome(it) == PASSED }
        ignored.each { assert results.outcome(it) == SKIPPED }
    }
}
