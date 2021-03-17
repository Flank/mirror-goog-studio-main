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
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.truth.ScannerSubject.Companion.assertThat
import com.android.testutils.truth.PathSubject
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Test for running lintFix with the standalone lint plugin.
 *
 * <p>Tip: To execute just this test run:
 *
 * <pre>
 *     $ cd tools
 *     $ ./gradlew :base:build-system:integration-test:lint:test --tests=LintStandaloneFixTest
 * </pre>
 */
class LintStandaloneFixTest {

    @get:Rule
    val project = GradleTestProject.builder().fromTestApp(MinimalSubProject.javaLibrary()).create()

    @Before
    fun before() {
        project.buildFile.appendText("\n" +
            """
                apply plugin: 'com.android.lint'

                lintOptions {
                    error 'SyntheticAccessor'
                }
            """.trimIndent()
        )

        val sourceFile = project.file("src/main/java/com/example/foo/Foo.java")
        sourceFile.parentFile.mkdirs()
        sourceFile.writeText(
            """
                package com.example.foo;

                public class Foo {

                    private void foo() {
                        new InnerClass().bar();
                    }

                    static class InnerClass {
                        private void bar() {}
                    }
                }
            """.trimIndent()
        )
    }

    @Test
    fun checkStandaloneLintFix() {
        val result = project.executor().expectFailure().run("lintFix")
        assertThat(result.stderr)
            .contains(
                "Aborting build since sources were modified to apply quickfixes after compilation"
            )

        // Make sure quickfix worked too
        val sourceFile = project.file("src/main/java/com/example/foo/Foo.java")
        // The original source has this:
        //    ...
        //    private void bar() {}
        //    ...
        // After applying quickfixes, it contains this:
        //    ...
        //    void bar() {}
        //    ...
        PathSubject.assertThat(sourceFile).doesNotContain("private void bar()")
        PathSubject.assertThat(sourceFile).contains("void bar()")
        val result2 = project.executor().run("clean", "lintFix")
        assertThat(result2.stdout).contains("BUILD SUCCESSFUL")
    }
}
