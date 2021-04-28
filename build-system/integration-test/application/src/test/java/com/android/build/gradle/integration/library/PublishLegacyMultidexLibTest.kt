/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.integration.library

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.testutils.truth.PathSubject.assertThat
import org.junit.Rule
import org.junit.Test

class PublishLegacyMultidexLibTest {
    @JvmField
    @Rule
    val project = GradleTestProject.builder().fromTestApp(
        MinimalSubProject.lib("com.android.test")
            .withFile(
                "src/main/java/test/Test.java", """
                package test;

                public class Test {}
            """.trimIndent()
            )
            .appendToBuild(
                """
        apply plugin: 'maven-publish'

        publishing {
            repositories {
                maven { url 'testrepo' }
            }
        }

        android {
            buildTypes {
                debug {
                    multiDexEnabled true
                }
            }
            defaultConfig {
                minSdkVersion 19
            }

            publishing {
                singleVariant("debug")
            }
        }

        afterEvaluate {
            publishing {
                publications {
                    debug(MavenPublication) {
                        groupId = 'com.android.test'
                        artifactId = 'lib'
                        version = '0.1'

                        from components.debug
                    }
                }
            }
        }
    """.trimIndent()
            )
    ).create()

    @Test
    fun testMultidexSupportNotAddedToPom() {
        project.executor().run("publish")

        // Check that Multidex support dependency is not added.
        assertThat(project.file("testrepo/com/android/test/lib/0.1/lib-0.1.pom"))
            .doesNotContain("<artifactId>multidex</artifactId>")
    }
}
