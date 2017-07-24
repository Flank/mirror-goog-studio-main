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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat

/**
 * Assemble tests for lintLibraryModel.
 * <p>
 * To run just this test: ./gradlew :base:integration-test:test -D:base:integration-test:test.single=LintSkipDependenciesTest
 */
@CompileStatic
class LintSkipDependenciesTest {
    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("lintLibrarySkipDeps")
            .create()

    @BeforeClass
    static void setup() {
        project.execute("clean", ":app:lintDebug")
    }

    @AfterClass
    static void cleanUp() {
        project = null
    }

    @Test
    void "check lint dependencies skipped"() {
        def file = new File(project.getSubproject('app').getTestDir(), "lint-results.txt")
        assertThat(file).exists()
        assertThat(file).contentWithUnixLineSeparatorsIsExactly("No issues found.")
    }
}
