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
import com.android.build.gradle.internal.lint.AndroidLintAnalysisTask
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * This test checks that the [AndroidLintAnalysisTask]'s outputs are not affected by properties
 * which aren't annotated as inputs.
 */
class AndroidLintAnalysisTaskCacheabilityTest {

    @get:Rule
    val project1: GradleTestProject = createGradleTestProject("project1")

    @get:Rule
    val project2: GradleTestProject = createGradleTestProject("project2")

    @Test
    fun testDifferentProjectsProduceSameAnalysisOutputs() {
        project1.executor().run(":app:clean", ":app:lintDebug")
        project2.executor().run(":app:clean", ":app:lintDebug")

        // Assert that the differences between the projects cause differences in the projects' lint
        // reports.
        val lintReport1 = project1.file("app/lint-report.txt")
        val lintReport2 = project2.file("app/lint-report.txt")
        assertThat(lintReport1).contains("project1")
        assertThat(lintReport1).doesNotContain("project2")
        assertThat(lintReport1).doesNotContain("projectDir")
        assertThat(lintReport1).doesNotContain("buildDir")
        assertThat(lintReport2).contains("project2")
        assertThat(lintReport2).doesNotContain("project1")
        assertThat(lintReport2).doesNotContain("projectDir")
        assertThat(lintReport2).doesNotContain("buildDir")

        // Assert that lint analysis outputs are identical and contain the project and build
        // directory encodings
        listOf(":app", ":feature", ":lib", ":java-lib").forEach { moduleName ->
            val partialResultsDir1 =
                FileUtils.join(
                    project1.getSubproject(moduleName)
                        .getIntermediateFile(
                            InternalArtifactType.LINT_PARTIAL_RESULTS.getFolderName()
                        ),
                    if (moduleName == ":java-lib") "global" else "debug",
                    "out"
                )
            val partialResultsDir2 =
                FileUtils.join(
                    project2.getSubproject(moduleName)
                        .getIntermediateFile(
                            InternalArtifactType.LINT_PARTIAL_RESULTS.getFolderName()
                        ),
                    if (moduleName == ":java-lib") "global" else "debug",
                    "out"
                )
            val lintDefiniteFileName =
                if (moduleName == ":java-lib") {
                    "lint-definite-main.xml"
                } else {
                    "lint-definite-debug.xml"
                }
            val lintPartialFileName =
                if (moduleName == ":java-lib") {
                    "lint-partial-main.xml"
                } else {
                    "lint-partial-debug.xml"
                }
            listOf(partialResultsDir1, partialResultsDir2).forEach {
                assertThat(it.listFiles()?.asList())
                    .containsExactlyElementsIn(
                        listOf(File(it, lintDefiniteFileName), File(it, lintPartialFileName))
                    )
            }
            assertThat(File(partialResultsDir1, lintDefiniteFileName).readText())
                .isEqualTo(File(partialResultsDir2, lintDefiniteFileName).readText())
            assertThat(File(partialResultsDir1, lintPartialFileName).readText())
                .isEqualTo(File(partialResultsDir2, lintPartialFileName).readText())
            assertThat(File(partialResultsDir1, lintDefiniteFileName).readText())
                .contains("{$moduleName*projectDir}")
            assertThat(File(partialResultsDir2, lintDefiniteFileName).readText())
                .contains("{$moduleName*projectDir}")
            // There are no lint issues in the java library's build directory, so only check for the
            // build directory encoding in the android modules.
            if (moduleName != ":java-lib") {
                assertThat(File(partialResultsDir1, lintDefiniteFileName).readText())
                    .contains("{$moduleName*buildDir}")
                assertThat(File(partialResultsDir2, lintDefiniteFileName).readText())
                    .contains("{$moduleName*buildDir}")
            }
        }
    }

    private fun createGradleTestProject(name: String): GradleTestProject {
        val app =
            MinimalSubProject.app("com.example.test")
                .addLintIssues()
                .appendToBuild(
                    """

                        android {
                            dynamicFeatures =  [':feature']
                            buildTypes {
                                debug {
                                    // this triggers ByteOrderMarkDetector in build directory
                                    buildConfigField "String", "FOO", "\"\uFEFF\""
                                }
                            }
                            lint {
                                abortOnError = false
                                checkDependencies = true
                                textOutput = file("lint-report.txt")
                                checkGeneratedSources = true
                                checkAllWarnings = true
                                checkTestSources = true
                            }
                        }
                    """.trimIndent()
                )
        val feature =
            MinimalSubProject.dynamicFeature("com.example.test")
                .addLintIssues()
                .appendToBuild(
                    """

                        android {
                            buildTypes {
                                debug {
                                    // this triggers ByteOrderMarkDetector in build directory
                                    buildConfigField "String", "FOO", "\"\uFEFF\""
                                }
                            }
                            lint {
                                checkGeneratedSources = true
                                checkAllWarnings = true
                                checkTestSources = true
                            }
                        }
                    """.trimIndent()
                )
        val lib =
            MinimalSubProject.lib("com.example.lib")
                .addLintIssues()
                .appendToBuild(
                    """

                        android {
                            buildTypes {
                                debug {
                                    // this triggers ByteOrderMarkDetector in build directory
                                    buildConfigField "String", "FOO", "\"\uFEFF\""
                                }
                            }
                            lint {
                                checkGeneratedSources = true
                                checkAllWarnings = true
                                checkTestSources = true
                            }
                        }
                    """.trimIndent()
                )
        val javaLib =
            MinimalSubProject.javaLibrary()
                .addLintIssues(isAndroid = false)
                .appendToBuild(
                    """

                        apply plugin: 'com.android.lint'

                        lint {
                            checkGeneratedSources = true
                            checkAllWarnings = true
                            checkTestSources = true
                        }
                    """.trimIndent()
                )

        return GradleTestProject.builder()
            .withName(name)
            .fromTestApp(
                MultiModuleTestProject.builder()
                    .subproject(":app", app)
                    .subproject(":feature", feature)
                    .subproject(":lib", lib)
                    .subproject(":java-lib", javaLib)
                    .dependency(feature, app)
                    .dependency(app, lib)
                    .dependency(app, javaLib)
                    .build()
            )
            .create()
    }
}

private fun MinimalSubProject.addLintIssues(isAndroid: Boolean = true): MinimalSubProject {
    val byteOrderMark = "\ufeff"
    this.withFile(
        "src/main/java/com/example/Foo.java",
        """package com.example;

        public class Foo {
            private String foo = "$byteOrderMark";
        }
        """.trimIndent()
    )
    this.withFile(
        "src/test/java/com/example/Bar.java",
        """package com.example;

        public class Bar {
            private String bar = "$byteOrderMark";
        }
        """.trimIndent()
    )
    if (isAndroid) {
        this.withFile(
            "src/androidTest/java/com/example/Baz.java",
            """package com.example;

            public class Baz {
                private String baz = "$byteOrderMark";
            }
            """.trimIndent()
        )
    }
    return this
}
