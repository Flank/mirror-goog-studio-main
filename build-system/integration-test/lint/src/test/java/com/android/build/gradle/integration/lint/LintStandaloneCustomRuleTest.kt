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

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

/**
 * Test for the standalone lint plugin.
 *
 *
 * <p>Tip: To execute just this test run:
 *
 * <pre>
 *     $ cd tools
 *     $ ./gradlew :base:build-system:integration-test:lint:test --tests=LintStandaloneCustomRuleTest
 * </pre>
 */
class LintStandaloneCustomRuleTest {

    @get:Rule
    val project =
        GradleTestProject.builder().fromTestProject("lintStandaloneCustomRules").create()

    @Test
    @Throws(Exception::class)
    fun checkStandaloneLint() {
        // Run twice to catch issues with configuration caching
        project.executor().run(":library:cleanLint", ":library:lint")
        project.executor().run(":library:cleanLint", ":library:lint")

        val file = project.getSubproject("library").file("lint-results.txt")
        assertThat(file).exists()
        assertThat(file).contains("MyClass.java:3: Error: Do not implement java.util.List directly [UnitTestLintCheck2 from com.example.google.lint]")
        assertThat(file).contains("1 errors, 0 warnings")
    }

    @Test
    fun checkPublishing() {
        project.executor().run(":library:publishAllPublicationsToMavenRepository")

        val publishDir = project.file("repo/org/example/sample/library/0.1")
        val publishedFiles = publishDir.list()?.filter { !isCheckSum(it) }
        assertThat(publishedFiles)
            .containsExactly("library-0.1.jar", "library-0.1.module", "library-0.1.pom")
    }

    private fun isCheckSum(fileName: String) : Boolean {
        return fileName.endsWith("md5") ||
                fileName.endsWith("sha1")||
                fileName.endsWith("sha256")||
                fileName.endsWith("sha512")
    }
}
