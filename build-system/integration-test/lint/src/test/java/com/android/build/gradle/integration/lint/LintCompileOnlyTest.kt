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
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.truth.PathSubject
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import java.io.File

class LintCompileOnlyTest {

    private val app =
        MinimalSubProject.app("com.example.app")
            .appendToBuild(
                """
                    android {
                        lintOptions {
                            textOutput file("lint-results.txt")
                            error 'StopShip'
                        }
                    }
                """.trimIndent()
            )

    private val lib1 =
        MinimalSubProject.lib("com.example.one")
            .appendToBuild(
                """
                    android {
                        lintOptions {
                            error 'StopShip'
                        }
                    }
                """.trimIndent()
            ).withFile(
                "src/main/java/com/example/one/Foo.java",
                """
                    package com.example.one;

                    public class Foo {
                        // STOPSHIP
                    }
                """.trimIndent()
            )

    private val lib2 = MinimalSubProject.lib("com.example.two")

    private val javaLib =
        MinimalSubProject.javaLibrary()
            .appendToBuild(
                """
                    apply plugin: 'com.android.lint'

                    lintOptions {
                        error 'StopShip'
                    }
                """.trimIndent()
            ).withFile(
                "src/main/java/com/example/Bar.java",
                """
                        package com.example;

                        public class Bar {
                            // STOPSHIP
                        }
                    """.trimIndent()
            )

    private val indirectLib =
        MinimalSubProject.lib("com.example.indirect")
            .appendToBuild(
                """
                    android {
                        lintOptions {
                            error 'StopShip'
                        }
                    }
                """.trimIndent()
            ).withFile(
                "src/main/java/com/example/indirect/Baz.java",
                """
                    package com.example.indirect;

                    public class Baz {
                        // STOPSHIP
                    }
                """.trimIndent()
            )

    @get:Rule
    val project: GradleTestProject =
        GradleTestProject.builder()
            .withName("project")
            .fromTestApp(
                MultiModuleTestProject.builder()
                    .subproject(":app", app)
                    .subproject(":lib1", lib1)
                    .subproject(":lib2", lib2)
                    .subproject(":javaLib", javaLib)
                    .subproject(":indirectLib", indirectLib)
                    .dependency("compileOnly", app, lib1)
                    .dependency("implementation", app, lib2)
                    .dependency("compileOnly", app, javaLib)
                    .dependency("compileOnly", lib2, indirectLib)
                    .build()
            )
            .create()

    // Regression test for b/185232013
    @Test
    fun testLintWithCompileOnlyDependencies() {
        // Run twice to catch issues with configuration caching
        project.executor().run("clean", ":app:lint")
        project.executor().run("clean", ":app:lint")
        Truth.assertThat(project.buildResult.failedTasks).isEmpty()
    }

    @Test
    fun testLintCheckDependenciesWithCompileOnlyDependencies() {
        TestFileUtils.appendToFile(
            project.getSubproject(":app").buildFile,
            """
                    android {
                        lintOptions {
                            checkDependencies true
                            abortOnError false
                        }
                    }
                    """.trimIndent()
        )
        // Run twice to catch issues with configuration caching
        project.executor().run("clean", ":app:lintRelease")
        project.executor().run("clean", ":app:lintRelease")
        Truth.assertThat(project.buildResult.failedTasks).isEmpty()
        val reportFile = File(project.getSubproject(":app").projectDir, "lint-results.txt")
        PathSubject.assertThat(reportFile).exists()
        PathSubject.assertThat(reportFile).containsAllOf(
            "Foo.java:4: Error: STOPSHIP comment found",
            "Bar.java:4: Error: STOPSHIP comment found"
        )
        // We don't expect issues from the indirect compileOnly dependency, but we include it in the
        // project as a regression test for b/191296077
        PathSubject.assertThat(reportFile).doesNotContain(
            "Baz.java:4: Error: STOPSHIP comment found"
        )
    }
}
