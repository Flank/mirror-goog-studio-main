/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProjectBuilder
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.setUpHelloWorld
import com.android.testutils.truth.PathSubject
import com.google.common.io.Resources
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files

class SigningPublishedArtifactsTest {

    @get:Rule
    val project = createGradleProjectBuilder {
        subProject(":lib") {
            plugins.add(PluginType.ANDROID_LIB)
            android {
                setUpHelloWorld()
            }
            appendToBuildFile {
                """
                    apply plugin: 'maven-publish'
                    apply plugin: 'signing'

                    android {
                        publishing {
                            multipleVariants {
                                allVariants()
                                withSourcesJar()
                                withJavadocJar()
                            }
                        }
                    }

                    afterEvaluate {
                        publishing {
                            publications {
                                myPublication(MavenPublication) {
                                    groupId = 'com.android'
                                    artifactId = 'lib'
                                    version = '1.0'

                                    from(components["default"])
                                }
                            }

                            repositories {
                                maven { url '../testrepo' }
                            }
                        }

                        signing {
                            sign publishing.publications
                        }
                    }
                """.trimIndent()
            }
            addFile("./gradle.properties",
                """
                    signing.keyId=70E99D38
                    signing.password=Testing123
                    signing.secretKeyRingFile=./../secring.gpg
                """.trimIndent())
        } //https://github.com/gradle/gradle/issues/20275
    }.withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF).create()

    @Before
    fun setUp() {
        val url = Resources.getResource(
            SigningPublishedArtifactsTest::class.java,
            "SigningPublishedArtifactsTest/secring.gpg"
        )
        Files.write(
            project.projectDir.resolve("secring.gpg").toPath(),
            Resources.toByteArray(url)
        )
    }

    @Test
    fun testIntegrationWithGradleSigningPlugin() {
        project.getSubproject("lib").execute("clean", "publish")
        val artifactsDir = project.projectDir.resolve("testrepo/com/android/lib/1.0")
        val javadocDebugAsc = artifactsDir.resolve("lib-1.0-debug-javadoc.jar.asc")
        val sourcesDebugAsc = artifactsDir.resolve("lib-1.0-debug-sources.jar.asc")
        val javadocReleaseAsc = artifactsDir.resolve("lib-1.0-release-javadoc.jar.asc")
        PathSubject.assertThat(javadocDebugAsc).exists()
        PathSubject.assertThat(sourcesDebugAsc).exists()
        PathSubject.assertThat(javadocReleaseAsc).exists()
    }
}
