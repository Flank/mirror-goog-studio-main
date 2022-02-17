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

package com.android.build.api.apiTest.kotlin

import com.android.build.api.apiTest.VariantApiBaseTest
import com.google.common.truth.Truth.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Test
import kotlin.test.assertNotNull

class ConfigurationsTest : VariantApiBaseTest(TestType.Script, ScriptingLanguage.Kotlin) {

    @Test
    fun configurationsTest() {
        given {
            addModule(":lib1") {
                buildFile =
                    // language=kotlin
                    """
                        plugins {
                                id("com.android.library")
                                kotlin("android")
                        }

                        android {
                            ${testingElements.addCommonAndroidBuildLogic()}
                        }
                        """.trimIndent()
                testingElements.addLibraryManifest(libName = "lib1", this)
            }
            addModule(":lib2") {
                buildFile =
                    // language=kotlin
                    """
                        plugins {
                                id("com.android.library")
                                kotlin("android")
                        }

                        android {
                            ${testingElements.addCommonAndroidBuildLogic()}
                        }
                        """.trimIndent()
                testingElements.addLibraryManifest(libName = "lib2", this)
            }
            addModule(":app") {
                buildFile =
                    // language=kotlin
                    """
                        plugins {
                                id("com.android.application")
                                kotlin("android")
                        }

                        android {
                                ${testingElements.addCommonAndroidBuildLogic()}
                        }

                        androidComponents {
                            onVariants(selector().withBuildType("release")) { variant ->
                                // configure compile and runtime configurations with a single call.
                                variant.configurations { configuration ->
                                    configuration.resolutionStrategy.dependencySubstitution {
                                        substitute(project(":lib1")).using(project(":lib2"))
                                    }
                                }
                                // must configure annotation processor configuration separately, if
                                // necessary.
                                variant.annotationProcessorConfiguration.resolutionStrategy.dependencySubstitution {
                                    substitute(project(":lib1")).using(project(":lib2"))
                                }
                            }
                        }

                        dependencies {
                            api(project(":lib1"))
                        }
                        """.trimIndent()
                testingElements.addManifest(this)
            }
            tasksToInvoke.clear()
            tasksToInvoke.add(":app:assemble")
        }
        check {
            assertNotNull(this)
            assertThat(output).contains("BUILD SUCCESSFUL")
            assertThat(task(":lib1:compileDebugKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":lib2:compileReleaseKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":lib1:compileReleaseKotlin")).isNull()
            assertThat(task(":lib2:compileDebugKotlin")).isNull()
        }
    }
}
