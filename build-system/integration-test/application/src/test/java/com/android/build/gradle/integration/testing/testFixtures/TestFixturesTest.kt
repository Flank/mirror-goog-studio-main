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
import org.junit.Rule
import org.junit.Test

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
}
