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

import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.truth.PathSubject.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/**
 * Integration test testing cases when lint should analyze test sources.
 */
@RunWith(FilterableParameterized::class)
class LintCheckTestSourcesTest(private val usePartialAnalysis: Boolean) {

    companion object {
        @Parameterized.Parameters(name = "usePartialAnalysis = {0}")
        @JvmStatic
        fun params() = listOf(true, false)
    }

    private val app = MinimalSubProject.app("com.example.app")
    private val lib = MinimalSubProject.lib("com.example.lib")
    private val javaLib = MinimalSubProject.javaLibrary()

    @get:Rule
    val project: GradleTestProject =
        GradleTestProject.builder()
            .fromTestApp(
                MultiModuleTestProject.builder()
                    .subproject(":app", app)
                    .subproject(":lib", lib)
                    .subproject(":javaLib", javaLib)
                    .dependency(app, lib)
                    .dependency(app, javaLib)
                    .build()
            ).create()

    @Before
    fun before() {
        project.getSubproject(":app")
            .buildFile
            .appendText(
                """
                    android {
                        testBuildType "release"
                        lintOptions {
                            abortOnError false
                            enable 'StopShip'
                            textOutput file("lint-results.txt")
                            checkDependencies true
                        }
                    }
                """.trimIndent()
            )

        project.getSubproject(":lib")
            .buildFile
            .appendText(
                """
                    android {
                        testBuildType "release"
                        lintOptions {
                            enable 'StopShip'
                        }
                    }
                """.trimIndent()
            )

        project.getSubproject(":javaLib")
            .buildFile
            .appendText(
                """
                    apply plugin: 'com.android.lint'

                    lintOptions {
                        enable 'StopShip'
                    }
                """.trimIndent()
            )

        // Add a resource which is only used by tests - this should cause a lint warning only if
        // ignoreTestSources is true.
        val appStringsSourceFile =
            project.getSubproject(":app").file("src/main/res/values/strings.xml")
        appStringsSourceFile.parentFile.mkdirs()
        appStringsSourceFile.writeText(
            """
                <resources>
                    <string name="foo">Foo</string>
                </resources>
            """.trimIndent()
        )

        val appUnitTestSourceFile =
            project.getSubproject(":app").file("src/test/java/com/example/foo/AppUnitTest.java")
        appUnitTestSourceFile.parentFile.mkdirs()
        appUnitTestSourceFile.writeText(
            """
                package com.example.foo;

                public class AppUnitTest {
                    // STOPSHIP
                    int foo = R.string.foo;
                }
            """.trimIndent()
        )

        val appAndroidTestSourceFile =
            project.getSubproject(":app")
                .file("src/androidTest/java/com/example/foo/AppAndroidTest.java")
        appAndroidTestSourceFile.parentFile.mkdirs()
        appAndroidTestSourceFile.writeText(
            """
                package com.example.foo;

                public class AppAndroidTest {
                    // STOPSHIP
                }
            """.trimIndent()
        )

        val libUnitTestSourceFile =
            project.getSubproject(":lib").file("src/test/java/com/example/foo/LibUnitTest.java")
        libUnitTestSourceFile.parentFile.mkdirs()
        libUnitTestSourceFile.writeText(
            """
                package com.example.foo;

                public class LibUnitTest {
                    // STOPSHIP
                }
            """.trimIndent()
        )

        val libAndroidTestSourceFile =
            project.getSubproject(":lib")
                .file("src/androidTest/java/com/example/foo/LibAndroidTest.java")
        libAndroidTestSourceFile.parentFile.mkdirs()
        libAndroidTestSourceFile.writeText(
            """
                package com.example.foo;

                public class LibAndroidTest {
                    // STOPSHIP
                }
            """.trimIndent()
        )

        val javaLibUnitTestSourceFile =
            project.getSubproject(":javaLib")
                .file("src/test/java/com/example/foo/JavaLibUnitTest.java")
        javaLibUnitTestSourceFile.parentFile.mkdirs()
        javaLibUnitTestSourceFile.writeText(
            """
                package com.example.foo;

                public class JavaLibUnitTest {
                    // STOPSHIP
                }
            """.trimIndent()
        )
    }

    @Test
    fun testCheckTestSources() {
        // check that there are no errors before adding "checkTestSources true"
        getExecutor().run(":app:lintRelease")
        val reportFile = File(project.getSubproject("app").projectDir, "lint-results.txt")
        assertThat(reportFile).exists()
        assertThat(reportFile).doesNotContain("Error: STOPSHIP")
        // Add "checkTestSources true" to all build files, and then check for errors.
        project.getSubproject(":app")
            .buildFile
            .appendText("\nandroid.lintOptions.checkTestSources true\n")
        project.getSubproject(":lib")
            .buildFile
            .appendText("\nandroid.lintOptions.checkTestSources true\n")
        project.getSubproject(":javaLib")
            .buildFile
            .appendText("\nlintOptions.checkTestSources true\n")
        getExecutor().run(":app:lintRelease")
        assertThat(reportFile).exists()
        assertThat(reportFile).containsAllOf(
            "AppUnitTest.java:4: Error: STOPSHIP comment found",
            "AppAndroidTest.java:4: Error: STOPSHIP comment found",
            "JavaLibUnitTest.java:4: Error: STOPSHIP comment found",
        )
        if (usePartialAnalysis) {
            // TODO(b/183138097) Analyze test sources in android library dependencies when not using
            //  partial analysis? Currently, we omit test sources from published lint models
            //  intentionally for some reason (see
            //  LintModelWriterTask.BaseCreationAction::configure)
            assertThat(reportFile).containsAllOf(
                "LibUnitTest.java:4: Error: STOPSHIP comment found",
                "LibAndroidTest.java:4: Error: STOPSHIP comment found",
            )
        }
    }

    @Test
    fun testIgnoreTestSources() {
        // check that there are no unused resource warnings before adding "ignoreTestSources true"
        getExecutor().run(":app:lintRelease")
        val reportFile = File(project.getSubproject("app").projectDir, "lint-results.txt")
        assertThat(reportFile).exists()
        assertThat(reportFile).doesNotContain(
            "Warning: The resource R.string.foo appears to be unused"
        )
        // Add "ignoreTestSources true" to the app build file, and then check for the warning.
        project.getSubproject(":app")
            .buildFile
            .appendText("\nandroid.lintOptions.ignoreTestSources true\n")
        getExecutor().run(":app:lintRelease")
        assertThat(reportFile).exists()
        assertThat(reportFile).contains("Warning: The resource R.string.foo appears to be unused")
    }

    private fun getExecutor(): GradleTaskExecutor =
        project.executor().with(BooleanOption.USE_LINT_PARTIAL_ANALYSIS, usePartialAnalysis)
}
