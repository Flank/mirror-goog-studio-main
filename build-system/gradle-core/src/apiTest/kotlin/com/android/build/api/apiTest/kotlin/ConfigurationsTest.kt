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
                            ${testingElements.addCommonAndroidBuildLogic("com.android.build.example.lib1")}
                        }
                        """.trimIndent()
            }
            addModule(":lib1Sub") {
                buildFile =
                    // language=kotlin
                    """
                        plugins {
                                id("com.android.library")
                                kotlin("android")
                        }

                        android {
                            ${testingElements.addCommonAndroidBuildLogic("com.android.build.example.lib1Sub")}
                        }
                        """.trimIndent()
            }
            addModule(":testLib") {
                buildFile =
                        // language=kotlin
                    """
                        plugins {
                                id("com.android.library")
                                kotlin("android")
                        }

                        android {
                            ${testingElements.addCommonAndroidBuildLogic("com.android.build.example.testLib")}
                        }
                        """.trimIndent()
            }
            addModule(":testLibSub") {
                buildFile =
                        // language=kotlin
                    """
                        plugins {
                                id("com.android.library")
                                kotlin("android")
                        }

                        android {
                            ${testingElements.addCommonAndroidBuildLogic("com.android.build.example.testLibSub")}
                        }
                        """.trimIndent()
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
                            ${testingElements.addCommonAndroidBuildLogic("com.android.build.example.lib2")}
                        }
                        """.trimIndent()
            }
            addModule(":lib2Sub") {
                buildFile =
                        // language=kotlin
                    """
                        plugins {
                                id("com.android.library")
                                kotlin("android")
                        }

                        android {
                            ${testingElements.addCommonAndroidBuildLogic("com.android.build.example.lib2Sub")}
                        }
                        """.trimIndent()
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
                                // components contains the variant and all of its nested components.
                                variant.components.forEach { component ->
                                    // configure compile and runtime configurations in the same way.
                                    listOf(
                                        component.compileConfiguration,
                                        component.runtimeConfiguration
                                    ).forEach { configuration ->
                                        configuration.resolutionStrategy.dependencySubstitution {
                                            substitute(project(":lib1")).using(project(":lib1Sub"))
                                        }
                                    }
                                }

                                // nestedComponents contains the variant's nested components, but
                                // not the release variant itself
                                variant.nestedComponents.forEach { component ->
                                    // configure compile and runtime configurations in the same way.
                                    listOf(
                                        component.compileConfiguration,
                                        component.runtimeConfiguration
                                    ).forEach { configuration ->
                                        configuration.resolutionStrategy.dependencySubstitution {
                                            substitute(project(":testLib")).using(project(":testLibSub"))
                                        }
                                        configuration.resolutionStrategy.dependencySubstitution {
                                            substitute(project(":lib2")).using(project(":lib2Sub"))
                                        }
                                    }
                                }
                            }
                        }

                        dependencies {
                            implementation(project(":lib1"))
                            implementation(project(":lib2"))
                            testImplementation(project(":lib1"))
                            testImplementation(project(":lib2"))
                            testImplementation(project(":testLib"))
                            androidTestImplementation(project(":lib1"))
                            androidTestImplementation(project(":lib2"))
                            androidTestImplementation(project(":testLib"))
                        }
                        """.trimIndent()
                testingElements.addManifest(this)
            }
            tasksToInvoke.clear()
            tasksToInvoke.addAll(
                listOf(
                    ":app:assemble",
                    ":app:testDebugUnitTest",
                    ":app:testReleaseUnitTest",
                    ":app:assembleAndroidTest"
                )
            )
        }
        check {
            assertNotNull(this)
            assertThat(output).contains("BUILD SUCCESSFUL")

            // We expect compileDebugKotlin tasks to run for the libraries which are the original
            // app dependencies because we don't do any dependency substitution for the app's debug
            // variant
            assertThat(task(":lib1:compileDebugKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":lib1Sub:compileDebugKotlin")).isNull()
            assertThat(task(":testLib:compileDebugKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":testLibSub:compileDebugKotlin")).isNull()
            assertThat(task(":lib2:compileDebugKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":lib2Sub:compileDebugKotlin")).isNull()

            // We expect compileReleaseKotlin tasks to run for the substitute libraries. We also
            // expect it to run for lib2 because lib2 was substituted with lib2Sub only for the
            // nested components, not the app's release variant.
            assertThat(task(":lib1:compileReleaseKotlin")).isNull()
            assertThat(task(":lib1Sub:compileReleaseKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":testLib:compileReleaseKotlin")).isNull()
            assertThat(task(":testLibSub:compileReleaseKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":lib2:compileReleaseKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":lib2Sub:compileReleaseKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }
    }
}
