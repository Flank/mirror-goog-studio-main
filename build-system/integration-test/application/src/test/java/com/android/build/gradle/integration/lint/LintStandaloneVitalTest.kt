/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.integration.lint

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import org.junit.Rule
import org.junit.Test

/**
 * Test for the standalone lint plugin.
 *
 *
 * To run just this test:
 * ./gradlew :base:integration-test:test -D:base:integration-test:test.single=LintStandaloneVitalTest
 */
class LintStandaloneVitalTest {
    @Rule
    @JvmField
    var project = GradleTestProject.builder().fromTestProject("lintStandaloneVital").create()

    @Test
    @Throws(Exception::class)
    fun checkStandaloneLintVital() {
        project.executeExpectingFailure("clean", "lintVital")

        val stderr = project.buildResult.stderr
        assertThat(stderr).contains("" +
                "Lint found errors in the project; aborting build.\n" +
                "  \n" +
                "  Fix the issues identified by lint, or add the following to your build script to proceed with errors:\n" +
                "  ...\n" +
                "  lintOptions {\n" +
                "      abortOnError false\n" +
                "  }\n" +
                "  ...")

        val file = project.file("lint-results.txt")
        assertThat(file).exists()
        assertThat(file).contains("MyClass.java:4: Error: Do not hardcode \"/sdcard/\"")
        assertThat(file).contains("1 errors, 0 warnings")
    }
}