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
import com.android.utils.FileUtils
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.jar.JarFile
import kotlin.test.assertTrue

/**
 * Integration test for publishing java & kotlin source for library and application projects.
 */
class SourcePublishingTest {

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestProject("kotlinApp")
        .create()

    private lateinit var library: GradleTestProject

    @Before
    fun setUp() {
        project.projectDir.resolve("testrepo").mkdirs()
        library = project.getSubproject("library")

        val javaSource = library.mainSrcDir.resolve(JAVA_SOURCE)
        FileUtils.createFile(javaSource, """
            package com.example.android.java;

            class HelloWorld {
                public void sayHello() {}
            }
        """.trimIndent())

        val kotlinSourceWithAnnotation =
            library.mainSrcDir.resolve("com/example/android/java/SomeService.kt")
        FileUtils.createFile(kotlinSourceWithAnnotation, """
            package com.example.android.java

            import javax.inject.Inject

            class SomeService @Inject constructor(val message: String)
        """.trimIndent())

        TestFileUtils.appendToFile(
            library.buildFile,
            """
                apply plugin: 'maven-publish'
                apply plugin: 'kotlin-kapt'

                dependencies {
                    kapt 'com.google.dagger:dagger-compiler:2.28.3'
                    api 'com.google.dagger:dagger:2.28.3'
                }

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
    fun testPublishSourceWithSingleVariant() {
        TestFileUtils.appendToFile(
            library.buildFile,
            """

                android {
                    publishing {
                        singleVariant('$RELEASE') {
                            withSourcesJar()
                        }
                    }
                }
            """.trimIndent()
        )
        addPublication(RELEASE)

        library.execute("publish")

        val sourceJar =
            project.projectDir.resolve("$SOURCE_JAR_DIR/library-1.1-sources.jar")
        checkSourceJarContent(sourceJar)
    }

    @Test
    fun testPublishSourceWithMultipleVariants() {
        TestFileUtils.appendToFile(
            library.buildFile,
            """

                android {
                    publishing {
                        multipleVariants {
                            allVariants()
                            withSourcesJar()
                        }
                    }
                }
            """.trimIndent()
        )
        addPublication(DEFAULT)

        library.execute("publish")

        val sourceJar =
            project.projectDir.resolve("$SOURCE_JAR_DIR/library-1.1-release-sources.jar")
        checkSourceJarContent(sourceJar)
    }

    private fun checkSourceJarContent(sourceJar: File) {
        assertThat(sourceJar).exists()
        JarFile(sourceJar).use {
            assertTrue(it.getEntry(KOTLIN_SOURCE) != null)
            assertTrue(it.getEntry(JAVA_SOURCE) != null)
            assertTrue(it.getEntry(AGP_GENERATED_SOURCE) != null)
            assertTrue(it.getEntry(KAPT_GENERATED_SOURCE) != null)
        }
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
        private const val KOTLIN_SOURCE: String = "com/example/android/kotlin/LibActivity.kt"
        private const val JAVA_SOURCE: String = "com/example/android/java/HelloWorld.java"
        private const val AGP_GENERATED_SOURCE: String = "com/example/android/kotlin/lib/BuildConfig.java"
        private const val KAPT_GENERATED_SOURCE: String = "com/example/android/java/SomeService_Factory.java"
        private const val SOURCE_JAR_DIR = "testrepo/org/gradle/sample/library/1.1"
    }
}
