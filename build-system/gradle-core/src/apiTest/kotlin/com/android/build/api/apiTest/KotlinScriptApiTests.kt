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

package com.android.build.api.apiTest

import com.google.common.truth.Truth
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Test
import kotlin.test.assertNotNull

class KotlinScriptApiTests: VariantApiBaseTest(TestType.Script) {

    private val testingElements= TestingElements(scriptingLanguage)

    @Test
    fun getApksTest() {
        given {
            tasksToInvoke.add(":app:debugDisplayApks")
            addModule(":app") {
                @Suppress("RemoveExplicitTypeArguments")
                buildFile =
                        // language=kotlin
                    """
                    plugins {
                            id("com.android.application")
                            kotlin("android")
                            kotlin("android.extensions")
                    }
                
                    ${testingElements.getDisplayApksTask()}

                    android {
                        compileSdkVersion(29)
                        defaultConfig {
                            minSdkVersion(21)
                            targetSdkVersion(29)
                        }
                        
                        onVariantProperties {
                            project.tasks.register<DisplayApksTask>("${ '$' }{name}DisplayApks") {
                                apkFolder.set(operations.get(ArtifactTypes.APK))
                                builtArtifactsLoader.set(operations.getBuiltArtifactsLoader())
                            }
                        }

                    }
                """.trimIndent()

                testingElements.addManifest(this)
            }
        }

        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("Got an APK")
            Truth.assertThat(output).contains("BUILD SUCCESSFUL")
        }
    }

    @Test
    fun manifestTransformerTest() {
        given {
            addModule(":app") {
                buildFile =
                    """
                    plugins {
                            id("com.android.application")
                            kotlin("android")
                            kotlin("android.extensions")
                    }
                    ${testingElements.getGitVersionTask()}

                    ${testingElements.getManifestTransformerTask()}
                    android {
                        compileSdkVersion(29)
                        defaultConfig {
                            minSdkVersion(21)
                            targetSdkVersion(29)
                        }

                        onVariantProperties {
                            val gitVersionProvider = tasks.register<GitVersionTask>("${'$'}{name}GitVersionProvider") {
                                gitVersionOutputFile.set(
                                    File(project.buildDir, "intermediates/gitVersionProvider/output"))
                                outputs.upToDateWhen { false }
                            }

                            val manifestUpdater = tasks.register<ManifestTransformerTask>("${'$'}{name}ManifestUpdater") {
                                gitInfoFile.set(gitVersionProvider.flatMap { it.gitVersionOutputFile })
                            }
                            operations.transform(manifestUpdater,
                                    { it.mergedManifest },
                                    { it.updatedManifest })
                            .on(com.android.build.api.artifact.ArtifactTypes.MERGED_MANIFEST)
                        }
                    }
                """.trimIndent()

                testingElements.addManifest(this)
            }
        }

        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("BUILD SUCCESSFUL")
            arrayOf(
                ":app:debugGitVersionProvider",
                ":app:processDebugMainManifest",
                ":app:debugManifestUpdater"
            ).forEach {
                val task = task(it)
                assertNotNull(task)
                Truth.assertThat(task.outcome).isEqualTo(TaskOutcome.SUCCESS)

            }
        }
    }
}