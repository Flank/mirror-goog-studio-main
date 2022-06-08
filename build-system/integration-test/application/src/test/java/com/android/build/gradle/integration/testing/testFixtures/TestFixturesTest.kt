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

package com.android.build.gradle.integration.testing.testFixtures

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import java.io.File

class TestFixturesTest {

    @get:Rule
    val project: GradleTestProject =
        GradleTestProject.builder().fromTestProject("testFixturesApp").create()

    private fun setUpProject(publishJavaLib: Boolean, publishAndroidLib: Boolean) {
        if (publishJavaLib) {
            TestFileUtils.searchAndReplace(
                project.getSubproject(":app").buildFile,
                "project(\":javaLib\")",
                "'com.example.javaLib:javaLib:1.0'"
            )

            TestFileUtils.searchAndReplace(
                project.getSubproject(":appTests").buildFile,
                "project(\":javaLib\")",
                "'com.example.javaLib:javaLib:1.0'"
            )
        }

        if (publishAndroidLib) {
            TestFileUtils.searchAndReplace(
                project.getSubproject(":app").buildFile,
                "project(\":lib\")",
                "'com.example.lib:lib:1.0'"
            )

            TestFileUtils.searchAndReplace(
                project.getSubproject(":lib2").buildFile,
                "project(\":lib\")",
                "'com.example.lib:lib:1.0'"
            )

            TestFileUtils.searchAndReplace(
                project.getSubproject(":appTests").buildFile,
                "project(\":lib\")",
                "'com.example.lib:lib:1.0'"
            )
        }

        if (publishJavaLib) {
            TestFileUtils.appendToFile(project.getSubproject(":javaLib").buildFile,
                """
                publishing {
                    repositories {
                        maven {
                            url = uri("../testrepo")
                        }
                    }
                    publications {
                        release(MavenPublication) {
                            from components.java
                            groupId = 'com.example.javaLib'
                            artifactId = 'javaLib'
                            version = '1.0'
                        }
                    }
                }

                // required for testFixtures publishing
                group = 'com.example.javaLib'
            """.trimIndent()
            )
        }

        if (publishAndroidLib) {
            TestFileUtils.appendToFile(project.getSubproject(":lib").buildFile,
                """
                afterEvaluate {
                    publishing {
                        repositories {
                            maven {
                                url = uri("../testrepo")
                            }
                        }
                        publications {
                            release(MavenPublication) {
                                from components.release
                                groupId = 'com.example.lib'
                                artifactId = 'lib'
                                version = '1.0'
                            }
                        }
                    }
                }

                // required for testFixtures publishing
                group = 'com.example.lib'
            """.trimIndent()
            )
        }

        if (publishJavaLib) {
            project.executor()
                .run(":javaLib:publish")
        }

        if (publishAndroidLib) {
            project.executor()
                .run(":lib:publish")
        }
    }

    @Test
    fun `library consumes local test fixtures`() {
        project.executor()
            .run(":lib:testDebugUnitTest")
    }

    @Test
    fun `verify library test fixtures resources dependency on main resources`() {
        project.executor()
            .run(":lib:verifyReleaseTestFixturesResources")
    }

    @Test
    fun `verify library resources dependency on test fixtures resources from local project`() {
        setUpProject(
            publishJavaLib = false,
            publishAndroidLib = false
        )
        project.executor()
            .run(":lib2:verifyReleaseResources")
    }

    @Test
    fun `verify library resources dependency on test fixtures resources from published lib`() {
        setUpProject(
            publishJavaLib = false,
            publishAndroidLib = true
        )
        project.executor()
            .run(":lib2:verifyReleaseResources")
    }

    @Test
    fun `verify library test dependency on test fixtures resources from local project`() {
        setUpProject(
            publishJavaLib = false,
            publishAndroidLib = false
        )
        project.executor()
            .run(":lib2:testDebugUnitTest")
    }

    @Test
    fun `verify library test dependency on test fixtures resources from published lib`() {
        setUpProject(
            publishJavaLib = false,
            publishAndroidLib = true
        )
        project.executor()
            .run(":lib2:testDebugUnitTest")
    }

    @Test
    fun `app consumes local, java and android library test fixtures`() {
        setUpProject(
            publishJavaLib = false,
            publishAndroidLib = false
        )
        project.executor()
            .run(":app:testDebugUnitTest")
    }

    @Test
    fun `app consumes local, published java and android library test fixtures`() {
        setUpProject(
            publishJavaLib = true,
            publishAndroidLib = true
        )
        project.executor()
            .run(":app:testDebugUnitTest")
    }

    @Test
    fun `app consumes android library test fixtures published using new publishing dsl`() {
        addNewPublishingDslForAndroidLibrary()
        setUpProject(
            publishJavaLib = false,
            publishAndroidLib = true
        )
        project.executor()
            .run(":app:testDebugUnitTest")
    }

    @Test
    fun `publish android library main variant without its test fixtures`() {
        TestFileUtils.appendToFile(
            project.getSubproject(":lib").buildFile,
            """

                afterEvaluate {
                    components.release.withVariantsFromConfiguration(
                        configurations.releaseTestFixturesVariantReleaseApiPublication) { skip() }
                    components.release.withVariantsFromConfiguration(
                        configurations.releaseTestFixturesVariantReleaseRuntimePublication) { skip() }
                }

            """.trimIndent()
        )
        setUpProject(
            publishAndroidLib = true,
            publishJavaLib = true
        )
        val testFixtureAar = "testrepo/com/example/lib/lib/1.0/lib-1.0-test-fixtures.aar"
        val mainVariantAar = "testrepo/com/example/lib/lib/1.0/lib-1.0.aar"
        assertThat(project.projectDir.resolve(testFixtureAar)).doesNotExist()
        assertThat(project.projectDir.resolve(mainVariantAar)).exists()
    }

    @Test
    fun `lint analyzes local and library module testFixtures sources`() {
        setUpProjectForLint(
            ignoreTestFixturesSourcesInApp = false
        )
        setUpProject(
            publishAndroidLib = false,
            publishJavaLib = false
        )
        project.executor().run(":app:lintRelease")
        val reportFile = File(project.getSubproject("app").projectDir, "lint-results.txt")
        assertThat(reportFile).exists()
        assertThat(reportFile).containsAllOf(
            "AppInterfaceTester.java:22: Error: STOPSHIP comment found;",
            "LibResourcesTester.java:35: Error: Missing permissions required by LibResourcesTester.methodWithUnavailablePermission: android.permission.ACCESS_COARSE_LOCATION [MissingPermission]"
        )
    }

    @Test
    fun `lint ignores local testFixtures sources`() {
        setUpProjectForLint(
            ignoreTestFixturesSourcesInApp = true
        )
        setUpProject(
            publishAndroidLib = false,
            publishJavaLib = false
        )
        project.executor().run(":app:lintRelease")
        val reportFile = File(project.getSubproject("app").projectDir, "lint-results.txt")
        assertThat(reportFile).exists()
        assertThat(reportFile).containsAllOf(
            "LibResourcesTester.java:35: Error: Missing permissions required by LibResourcesTester.methodWithUnavailablePermission: android.permission.ACCESS_COARSE_LOCATION [MissingPermission]"
        )
    }

    @Test
    fun `test plugin consumes test fixtures`() {
        setUpProject(
            publishAndroidLib = false,
            publishJavaLib = false
        )
        useAndroidX()

        project.executor().run(":appTests:packageDebug")

        val testApk = project.getSubproject(":appTests").getApk(GradleTestProject.ApkType.DEBUG)

        testApk.mainDexFile.get().classes.keys.let { classes ->
            // test fixtures classes
            Truth.assertThat(classes).containsAtLeastElementsIn(
                listOf(
                    "Lcom/example/app/testFixtures/AppInterfaceTester;",
                    "Lcom/example/javalib/testFixtures/JavaLibInterfaceTester;",
                    "Lcom/example/lib/testFixtures/LibInterfaceTester;",
                    "Lcom/example/lib/testFixtures/LibResourcesTester;"
                )
            )

            // lib and java lib classes
            Truth.assertThat(classes).containsAtLeastElementsIn(
                listOf(
                    "Lcom/example/javalib/JavaLibInterface;",
                    "Lcom/example/lib/LibInterface;",
                    "Lcom/example/lib/BuildConfig;",
                )
            )

            // app classes shouldn't be packaged in the test apk
            Truth.assertThat(classes).doesNotContain(
                "Lcom/example/app/AppInterface;"
            )
        }
    }

    @Test
    fun `test plugin consumes published test fixtures`() {
        setUpProject(
            publishAndroidLib = true,
            publishJavaLib = true
        )
        useAndroidX()

        project.executor().run(":appTests:packageDebug")

        val testApk = project.getSubproject(":appTests").getApk(GradleTestProject.ApkType.DEBUG)

        testApk.mainDexFile.get().classes.keys.let { classes ->
            // test fixtures classes
            Truth.assertThat(classes).containsAtLeastElementsIn(
                listOf(
                    "Lcom/example/app/testFixtures/AppInterfaceTester;",
                    "Lcom/example/javalib/testFixtures/JavaLibInterfaceTester;",
                    "Lcom/example/lib/testFixtures/LibInterfaceTester;",
                    "Lcom/example/lib/testFixtures/LibResourcesTester;"
                )
            )

            // lib and java lib classes
            Truth.assertThat(classes).containsAtLeastElementsIn(
                listOf(
                    "Lcom/example/javalib/JavaLibInterface;",
                    "Lcom/example/lib/LibInterface;",
                    "Lcom/example/lib/BuildConfig;",
                )
            )

            // app classes shouldn't be packaged in the test apk
            Truth.assertThat(classes).doesNotContain(
                "Lcom/example/app/AppInterface;"
            )
        }
    }

    @Test
    fun `test plugin excludes main lib classes but includes test fixtures`() {
        setUpProject(
            publishAndroidLib = false,
            publishJavaLib = false
        )
        useAndroidX()

        TestFileUtils.searchAndReplace(
            project.getSubproject(":app").buildFile,
            "compileOnly",
            "implementation"
        )

        project.executor().run(":appTests:packageDebug")

        val testApk = project.getSubproject(":appTests").getApk(GradleTestProject.ApkType.DEBUG)

        testApk.mainDexFile.get().classes.keys.let { classes ->
            // test fixtures classes
            Truth.assertThat(classes).containsAtLeastElementsIn(
                listOf(
                    "Lcom/example/app/testFixtures/AppInterfaceTester;",
                    "Lcom/example/javalib/testFixtures/JavaLibInterfaceTester;",
                    "Lcom/example/lib/testFixtures/LibInterfaceTester;",
                    "Lcom/example/lib/testFixtures/LibResourcesTester;"
                )
            )

            // lib and java lib classes shouldn't be packaged in the test apk
            Truth.assertThat(classes).doesNotContain("Lcom/example/javalib/JavaLibInterface;")
            Truth.assertThat(classes).doesNotContain("Lcom/example/lib/LibInterface;")
            Truth.assertThat(classes).doesNotContain("Lcom/example/lib/BuildConfig;")

            // app classes shouldn't be packaged in the test apk
            Truth.assertThat(classes).doesNotContain(
                "Lcom/example/app/AppInterface;"
            )
        }
    }

    private fun setUpProjectForLint(ignoreTestFixturesSourcesInApp: Boolean) {
        project.getSubproject(":app").buildFile.appendText(
            """
                android {
                    testBuildType "release"
                    lint {
                        abortOnError false
                        enable 'StopShip'
                        textOutput file("lint-results.txt")
                        checkDependencies true
                        ignoreTestFixturesSources $ignoreTestFixturesSourcesInApp
                    }
                }
            """.trimIndent()
        )
        TestFileUtils.searchAndReplace(
            project.getSubproject(":app")
                .file("src/testFixtures/java/com/example/app/testFixtures/AppInterfaceTester.java"),
            "public class AppInterfaceTester {",
            "// STOPSHIP\n" + "public class AppInterfaceTester {"
        )
        project.getSubproject(":lib").buildFile.appendText(
            """
                android {
                    testBuildType "release"
                }

                dependencies {
                    testFixturesApi 'androidx.annotation:annotation:1.1.0'
                }
            """.trimIndent()
        )
        TestFileUtils.searchAndReplace(
            project.getSubproject(":lib")
                .file("src/testFixtures/java/com/example/lib/testFixtures/LibResourcesTester.java"),
            "public void test() {",
            """
                @androidx.annotation.RequiresPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                public void methodWithUnavailablePermission() {
                }

                public void test() {
                    methodWithUnavailablePermission();
            """.trimIndent()
        )
        useAndroidX()
    }

    private fun addNewPublishingDslForAndroidLibrary() {
        TestFileUtils.appendToFile(
            project.getSubproject(":lib").buildFile,
            """
                android{
                    publishing{
                        singleVariant("release")
                    }
                }
            """.trimIndent()
        )
    }

    private fun useAndroidX() {
        FileUtils.createFile(project.file("gradle.properties"), "android.useAndroidX=true")
    }
}
