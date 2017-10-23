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

import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import java.io.File
import org.junit.Rule
import org.junit.Test

/**
 * Test for the standalone lint plugin.
 *
 *
 * To run just this test:
 * ./gradlew :base:integration-test:test -D:base:integration-test:test.single=LintStandaloneTest
 */
class LintStandaloneTest {
    @Rule @JvmField
    var project = GradleTestProject.builder().fromTestProject("lintStandalone").create()

    @Test
    @Throws(Exception::class)
    fun checkStandaloneLint() {
        project.execute("clean", "lint")

        val file = project.file("lint-results.txt")
        assertThat(file).exists()
        assertThat(file).contains("MyClass.java:4: Warning: Do not hardcode \"/sdcard/\"")
        assertThat(file).contains("0 errors, 1 warnings")
    }
}
