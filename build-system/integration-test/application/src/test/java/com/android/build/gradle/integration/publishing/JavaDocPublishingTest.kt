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

package com.android.build.gradle.integration.publishing

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.truth.PathSubject.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Integration test for publishing generated Java docs for library projects.
 */
class JavaDocPublishingTest {

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestProject("kotlinApp")
        .create()

    private lateinit var library: GradleTestProject

    @Before
    fun setUp() {
        project.projectDir.resolve("testrepo").mkdirs()
        library = project.getSubproject("library")

        TestFileUtils.appendToFile(
            library.buildFile,
            """
                apply plugin: 'maven-publish'

                afterEvaluate {
                    publishing {
                        repositories {
                            maven { url '../testrepo' }
                        }
                    }
                }
            """.trimIndent()
        )
    }

    @Test
    fun testPublishJavaDocWithSingleVariant() {
        TestFileUtils.appendToFile(
            library.buildFile,
            """

                android {
                    publishing {
                        singleVariant('$RELEASE') {
                            withJavadocJar()
                        }
                    }
                }
            """.trimIndent()
        )
        addPublication(RELEASE)

        library.execute("publish")

        val docJar = project.projectDir.resolve("$DOC_JAR_DIR/library-1.1-javadoc.jar")
        assertThat(docJar).exists()
    }

    @Test
    fun testPublishJavaDocWithMultipleVariants() {
        TestFileUtils.appendToFile(
            library.buildFile,
            """

                android {
                    publishing {
                        multipleVariants {
                            allVariants()
                            withJavadocJar()
                        }
                    }
                }
            """.trimIndent()
        )
        addPublication(DEFAULT)

        library.execute("publish")

        val docJar =
            project.projectDir.resolve("$DOC_JAR_DIR/library-1.1-releaseJavadoc.jar")
        assertThat(docJar).exists()
    }

    private fun addPublication(componentName: String) {
        TestFileUtils.appendToFile(
            library.buildFile,
            """
                afterEvaluate {
                    publishing {
                        publications {
                            myPublication(MavenPublication) {
                                groupId = 'org.gradle.sample'
                                artifactId = 'library'
                                version = '1.1'

                                from components.$componentName
                            }
                        }
                    }
                }
            """.trimIndent()
        )
    }

    companion object {
        private const val RELEASE: String = "release"
        private const val DEFAULT: String = "default"
        private const val DOC_JAR_DIR: String = "testrepo/org/gradle/sample/library/1.1"
    }
}
