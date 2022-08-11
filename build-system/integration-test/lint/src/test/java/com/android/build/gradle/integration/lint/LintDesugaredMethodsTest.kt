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

package com.android.build.gradle.integration.lint

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.truth.PathSubject
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import java.io.File

class LintDesugaredMethodsTest {

    private val app =
        MinimalSubProject.app("com.example.app")
            .appendToBuild(
                """
                    android {
                        lintOptions {
                            abortOnError false
                            textOutput file("lint-results.txt")
                        }
                    }
                """.trimIndent()
            )
            .withFile(
                "src/main/java/com/example/app/Foo.java",
                """
                    package com.example.app;

                    import java.util.Set;

                    public class Foo {
                        public void foo() {
                            Set<String> set = Set.of("foo");
                        }
                    }
                """.trimIndent()
            )
            .withFile(
                "abridgedDesugaredMethods.txt",
                """
                    java/lang/Boolean#compare(ZZ)I

                """.trimIndent()
            )

    @get:Rule
    val project: GradleTestProject = GradleTestProject.builder().fromTestApp(app).create()

    @Test
    fun testDesugaredMethodsPassedToLint() {
        val newApiError =
            "Foo.java:7: Error: Call requires API level 30 (current min is 14): java.util.Set#of [NewApi]"

        // First check that we do not get the lint error when AndroidLintAnalysisTask adds the
        // correct desugared methods file(s).
        project.executor().run("clean", "lintDebug")
        Truth.assertThat(project.buildResult.failedTasks).isEmpty()
        val reportFile = File(project.projectDir, "lint-results.txt")
        PathSubject.assertThat(reportFile).exists()
        PathSubject.assertThat(reportFile).doesNotContain(newApiError)

        // Then check that there's an error when we replace the files from the lint analysis task's
        // desugared methods files input with an abridged desugared methods file.
        TestFileUtils.appendToFile(
            project.buildFile,
            // language=groovy
            """
                afterEvaluate {
                    project.tasks.withType(AndroidLintAnalysisTask).configureEach {
                        def fileCollection = it.desugaredMethodsFiles
                        def disallowChanges =
                            fileCollection.getClass().getDeclaredField("disallowChanges")
                        disallowChanges.setAccessible(true)
                        disallowChanges.set(fileCollection, false)
                        fileCollection.setFrom("abridgedDesugaredMethods.txt")
                        disallowChanges.set(fileCollection, true)

                        fileCollection = it.variantInputs.mainArtifact.desugaredMethodsFiles
                        disallowChanges =
                            fileCollection.getClass().getDeclaredField("disallowChanges")
                        disallowChanges.setAccessible(true)
                        disallowChanges.set(fileCollection, false)
                        fileCollection.setFrom("abridgedDesugaredMethods.txt")
                        disallowChanges.set(fileCollection, true)
                    }
                }
            """.trimIndent()
        )
        TestFileUtils.searchAndReplace(
            project.buildFile,
            "apply plugin: 'com.android.application'",
            // language=groovy
            """
                apply plugin: 'com.android.application'
                import com.android.build.gradle.internal.lint.AndroidLintAnalysisTask
            """.trimIndent()
        )
        project.executor().run("clean", "lintDebug")
        Truth.assertThat(project.buildResult.failedTasks).isEmpty()
        PathSubject.assertThat(reportFile).exists()
        PathSubject.assertThat(reportFile).contains(newApiError)
    }
}
