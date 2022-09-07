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
import com.android.build.gradle.integration.common.truth.GradleTaskSubject.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.lint.AndroidLintAnalysisTask
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
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

    @get:Rule
    val tmp = TemporaryFolder()

    private val extraSourceDirMap by lazy {
        mapOf(
            ":app" to tmp.newFolder(),
            ":feature" to tmp.newFolder(),
            ":lib" to tmp.newFolder(),
            ":java-lib" to tmp.newFolder(),
        )
    }

    private val extraTestSourceDirMap by lazy {
        mapOf(
            ":app" to tmp.newFolder(),
            ":feature" to tmp.newFolder(),
            ":lib" to tmp.newFolder(),
            ":java-lib" to tmp.newFolder(),
        )
    }

    /**
     * Add source directories that are outside the project directories. Add debug source directories
     * to the Android modules, and add an extra java source directory to the java library. Add an
     * extra test java source directory to all the modules.
     */
    @Before
    fun before() {
        listOf(":app", ":feature", ":lib", ":java-lib").forEach { moduleName ->
            val extraSourceDir = extraSourceDirMap[moduleName]!!
            FileUtils.copyDirectory(
                FileUtils.join(project1.getSubproject(moduleName).projectDir, "src", "main"),
                extraSourceDir
            )
            val oldJavaFile = FileUtils.join(extraSourceDir, "java", "com", "example", "Foo.java")
            val newJavaFile = FileUtils.join(extraSourceDir, "java", "com", "example", "Extra.java")
            FileUtils.copyFile(oldJavaFile, newJavaFile)
            FileUtils.delete(oldJavaFile)
            TestFileUtils.searchAndReplace(newJavaFile, "Foo", "Extra")
            val extraTestSourceDir = extraTestSourceDirMap[moduleName]!!
            val extraTestJavaFile =
                FileUtils.join(extraTestSourceDir, "java", "com", "example", "Test.java")
            extraTestJavaFile.parentFile.mkdirs()
            FileUtils.copyFile(newJavaFile, extraTestJavaFile)
            TestFileUtils.searchAndReplace(extraTestJavaFile, "Extra", "Test")
            if (moduleName != ":java-lib") {
                val manifestFile = FileUtils.join(extraSourceDir, "AndroidManifest.xml")
                TestFileUtils.searchAndReplace(manifestFile, "<app", "<!--\ufeff--><app")
                val resFile = FileUtils.join(extraSourceDir, "res", "values", "strings.xml")
                resFile.parentFile.mkdirs()
                resFile.writeText(
                    """
                        <resources>
                            <string name="${moduleName.substring(1)}Extra">Extra</string>
                        </resources>
                    """.trimIndent()
                )
            }
            listOf(project1, project2).forEach { project ->
                val extraSourceDirPath =
                    FileUtils.toSystemIndependentPath(extraSourceDir.absolutePath)
                val extraTestSourceDirPath =
                    FileUtils.toSystemIndependentPath(extraTestSourceDir.absolutePath)
                if (moduleName == ":java-lib") {
                    TestFileUtils.appendToFile(
                        project.getSubproject(moduleName).buildFile,
                        """

                            sourceSets {
                                main {
                                    java.srcDirs += '$extraSourceDirPath/java'
                                }
                                test {
                                    java.srcDirs += '$extraTestSourceDirPath/java'
                                }
                            }
                        """.trimIndent()
                    )
                } else {
                    TestFileUtils.appendToFile(
                        project.getSubproject(moduleName).buildFile,
                        """

                            android {
                                sourceSets {
                                    debug.setRoot('$extraSourceDirPath')
                                    test {
                                        java.srcDirs += '$extraTestSourceDirPath/java'
                                    }
                                }
                            }
                        """.trimIndent()
                    )
                }
            }
        }
    }

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

        // Assert that lint issues from extra source directories are present in the lint reports
        val slash = File.separator
        listOf(":app", ":feature", ":lib", ":java-lib").forEach { moduleName ->
            listOf(lintReport1, lintReport2).forEach { lintReport ->
                assertThat(lintReport).contains(
                    "${extraSourceDirMap[moduleName]!!.name}${slash}java${slash}com${slash}example${slash}Extra.java"
                )
                assertThat(lintReport).contains(
                    "${extraTestSourceDirMap[moduleName]!!.name}${slash}java${slash}com${slash}example${slash}Test.java"
                )
                if (moduleName != ":java-lib") {
                    assertThat(lintReport).contains(
                        "${extraSourceDirMap[moduleName]!!.name}${slash}res${slash}values${slash}strings.xml"
                    )
                    assertThat(lintReport).contains(
                        "${extraSourceDirMap[moduleName]!!.name}${slash}AndroidManifest.xml"
                    )
                }
            }
        }

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
            if (moduleName == ":java-lib") {
                assertThat(File(partialResultsDir1, lintDefiniteFileName).readText())
                    .contains("{$moduleName*main*sourceProvider*0*javaDir*1}")
                assertThat(File(partialResultsDir1, lintDefiniteFileName).readText())
                    .contains("{$moduleName*main*sourceProvider*0*javaDir*2}")
                assertThat(File(partialResultsDir1, lintDefiniteFileName).readText())
                    .contains("{$moduleName*main*testSourceProvider*0*javaDir*1}")
                assertThat(File(partialResultsDir1, lintDefiniteFileName).readText())
                    .contains("{$moduleName*main*testSourceProvider*0*javaDir*2}")
            } else {
                // There are no lint issues in the java library's build directory, so only check for
                // the build directory encoding in the android modules.
                assertThat(File(partialResultsDir1, lintDefiniteFileName).readText())
                    .contains("{$moduleName*debug*sourceProvider*0*javaDir*2}")
                assertThat(File(partialResultsDir2, lintDefiniteFileName).readText())
                    .contains("{$moduleName*debug*sourceProvider*0*javaDir*2}")
                assertThat(File(partialResultsDir1, lintDefiniteFileName).readText())
                    .contains("{$moduleName*debug*sourceProvider*1*manifest*0}")
                assertThat(File(partialResultsDir1, lintDefiniteFileName).readText())
                    .contains("{$moduleName*debug*sourceProvider*0*javaDir*0}")
                assertThat(File(partialResultsDir1, lintPartialFileName).readText())
                    .contains("{$moduleName*debug*sourceProvider*0*resDir*1}")
                assertThat(File(partialResultsDir1, lintDefiniteFileName).readText())
                    .contains("{$moduleName*debug*testSourceProvider*0*javaDir*0}")
                assertThat(File(partialResultsDir1, lintDefiniteFileName).readText())
                    .contains("{$moduleName*debug*testSourceProvider*0*javaDir*1}")
                assertThat(File(partialResultsDir1, lintDefiniteFileName).readText())
                    .contains("{$moduleName*debug*testSourceProvider*1*javaDir*0}")
            }

            // assert that the lint analysis outputs do not contain the extra source paths.
            assertThat(File(partialResultsDir1, lintDefiniteFileName).readText())
                .doesNotContain(extraSourceDirMap[moduleName]!!.name)
            assertThat(File(partialResultsDir1, lintDefiniteFileName).readText())
                .doesNotContain(extraTestSourceDirMap[moduleName]!!.name)
            assertThat(File(partialResultsDir1, lintPartialFileName).readText())
                .doesNotContain(extraSourceDirMap[moduleName]!!.name)
            assertThat(File(partialResultsDir1, lintPartialFileName).readText())
                .doesNotContain(extraTestSourceDirMap[moduleName]!!.name)
            assertThat(File(partialResultsDir2, lintDefiniteFileName).readText())
                .doesNotContain(extraSourceDirMap[moduleName]!!.name)
            assertThat(File(partialResultsDir2, lintDefiniteFileName).readText())
                .doesNotContain(extraTestSourceDirMap[moduleName]!!.name)
            assertThat(File(partialResultsDir2, lintPartialFileName).readText())
                .doesNotContain(extraSourceDirMap[moduleName]!!.name)
            assertThat(File(partialResultsDir2, lintPartialFileName).readText())
                .doesNotContain(extraTestSourceDirMap[moduleName]!!.name)
        }
    }

    /**
     * Lint's encoding and decoding of source set paths depends on all the lint tasks having the
     * correct source set information. This test checks that the expected lint tasks are not
     * UP-TO-DATE after modifying the library module's source directories.
     */
    @Test
    fun testLintNotUpToDateAfterModifyingSourceSets() {
        // Create extra source folders. foo1 and foo2 are identical, but bar is different.
        listOf("foo1", "foo2", "bar").forEach { srcDirName ->
            val srcDir =
                FileUtils.join(project1.getSubproject(":lib").projectDir, "src", srcDirName)
            val assetFile =
                FileUtils.join(srcDir, "assets", if (srcDirName == "bar") "bar.txt" else "foo.txt")
            assetFile.parentFile.mkdirs()
            assetFile.writeText(if (srcDirName == "bar") "bar" else "foo")
        }

        project1.executor().run(":app:lintDebug")
        assertThat(project1.buildResult.getTask(":app:lintReportDebug")).didWork()
        assertThat(project1.buildResult.getTask(":app:lintAnalyzeDebug")).didWork()
        assertThat(project1.buildResult.getTask(":lib:lintAnalyzeDebug")).didWork()
        assertThat(project1.buildResult.getTask(":lib:generateDebugLintModel")).didWork()

        // Add new source directories and check that expected tasks do work
        TestFileUtils.appendToFile(
            project1.getSubproject(":lib").buildFile,
            """

                android {
                    sourceSets {
                        main.assets.srcDirs += 'src/foo1/assets'
                        main.assets.srcDirs += 'src/bar/assets'
                    }
                }
            """.trimIndent()
        )
        project1.executor().run(":app:lintDebug")
        assertThat(project1.buildResult.getTask(":app:lintReportDebug")).didWork()
        assertThat(project1.buildResult.getTask(":app:lintAnalyzeDebug")).didWork()
        assertThat(project1.buildResult.getTask(":lib:lintAnalyzeDebug")).didWork()
        assertThat(project1.buildResult.getTask(":lib:generateDebugLintModel")).didWork()

        // Replace foo1 and foo2 and check again. In this case, we expect lib's lint analysis task
        // to be up-to-date because foo1 and foo2 are identical.
        TestFileUtils.searchAndReplace(project1.getSubproject(":lib").buildFile, "foo1", "foo2")
        project1.executor().run(":app:lintDebug")
        assertThat(project1.buildResult.getTask(":app:lintReportDebug")).didWork()
        assertThat(project1.buildResult.getTask(":app:lintAnalyzeDebug")).wasUpToDate()
        assertThat(project1.buildResult.getTask(":lib:lintAnalyzeDebug")).wasUpToDate()
        assertThat(project1.buildResult.getTask(":lib:generateDebugLintModel")).didWork()

        // Swap the order in which the foo2 and bar directories are added and check again.
        TestFileUtils.searchAndReplace(project1.getSubproject(":lib").buildFile, "foo2", "temp")
        TestFileUtils.searchAndReplace(project1.getSubproject(":lib").buildFile, "bar", "foo2")
        TestFileUtils.searchAndReplace(project1.getSubproject(":lib").buildFile, "temp", "bar")
        project1.executor().run(":app:lintDebug")
        assertThat(project1.buildResult.getTask(":app:lintReportDebug")).didWork()
        assertThat(project1.buildResult.getTask(":app:lintAnalyzeDebug")).wasUpToDate()
        assertThat(project1.buildResult.getTask(":lib:lintAnalyzeDebug")).didWork()
        assertThat(project1.buildResult.getTask(":lib:generateDebugLintModel")).didWork()

        // Add a fake source directory and check again.
        TestFileUtils.appendToFile(
            project1.getSubproject(":lib").buildFile,
            """

                android {
                    sourceSets {
                        main.assets.srcDirs += 'src/fake/assets'
                    }
                }
            """.trimIndent()
        )
        project1.executor().run(":app:lintDebug")
        assertThat(project1.buildResult.getTask(":app:lintReportDebug")).didWork()
        assertThat(project1.buildResult.getTask(":app:lintAnalyzeDebug")).wasUpToDate()
        assertThat(project1.buildResult.getTask(":lib:lintAnalyzeDebug")).didWork()
        assertThat(project1.buildResult.getTask(":lib:generateDebugLintModel")).didWork()

        // Switch the order of the fake source directory and check again.
        TestFileUtils.searchAndReplace(project1.getSubproject(":lib").buildFile, "fake", "temp")
        TestFileUtils.searchAndReplace(project1.getSubproject(":lib").buildFile, "bar", "fake")
        TestFileUtils.searchAndReplace(project1.getSubproject(":lib").buildFile, "temp", "bar")
        project1.executor().run(":app:lintDebug")
        assertThat(project1.buildResult.getTask(":app:lintReportDebug")).didWork()
        assertThat(project1.buildResult.getTask(":app:lintAnalyzeDebug")).wasUpToDate()
        assertThat(project1.buildResult.getTask(":lib:lintAnalyzeDebug")).didWork()
        assertThat(project1.buildResult.getTask(":lib:generateDebugLintModel")).didWork()
    }

    @Test
    fun testLintAnalysisTasksFromCache() {
        TestFileUtils.appendToFile(project1.gradlePropertiesFile, "\norg.gradle.caching=true\n")
        project1.executor().run("clean", ":app:lintDebug")
        project1.executor().run("clean", ":app:lintDebug")
        assertThat(project1.buildResult.getTask(":app:lintAnalyzeDebug")).wasFromCache()
        assertThat(project1.buildResult.getTask(":feature:lintAnalyzeDebug")).wasFromCache()
        assertThat(project1.buildResult.getTask(":lib:lintAnalyzeDebug")).wasFromCache()
        assertThat(project1.buildResult.getTask(":java-lib:lintAnalyze")).wasFromCache()
    }
}

/**
 * Creates a multi-module android project, including an app module, a dynamic-feature module, an
 * android library module, and a java library module, with lint issues scattered throughout.
 *
 * @param name the name of the project
 * @param heapSize the max heap size of the project
 */
fun createGradleTestProject(name: String, heapSize: String = "2048M"): GradleTestProject {
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
                            abortOnError = false
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
                            abortOnError = false
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
                        abortOnError = false
                        checkGeneratedSources = true
                        checkAllWarnings = true
                        checkTestSources = true
                    }
                """.trimIndent()
            )

    // Add lintPublish dependency from lib to javalib as a regression test for Issue 224967104
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
                .dependency("lintPublish", lib, javaLib)
                .build()
        )
        .withHeap(heapSize)
        .create()
}

private fun MinimalSubProject.addLintIssues(isAndroid: Boolean = true): MinimalSubProject {
    val byteOrderMark = "\ufeff"
    this.withFile(
        "src/main/java/com/example/Foo.java",
        """
            package com.example;

            public class Foo {
                private String foo = "$byteOrderMark";
            }
        """.trimIndent()
    )
    this.withFile(
        "src/test/java/com/example/Bar.java",
        """
            package com.example;

            public class Bar {
                private String bar = "$byteOrderMark";
            }
        """.trimIndent()
    )
    if (isAndroid) {
        this.withFile(
            "src/androidTest/java/com/example/Baz.java",
            """
                package com.example;

                public class Baz {
                    private String baz = "$byteOrderMark";
                }
            """.trimIndent()
        )
    }
    return this
}
