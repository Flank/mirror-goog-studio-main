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

import com.android.build.api.apiTest.TestingElements
import com.android.build.api.apiTest.VariantApiBaseTest
import com.google.common.truth.Truth
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Test
import kotlin.test.assertNotNull

class KotlinScriptTransformArtifactTest: VariantApiBaseTest(ScriptingLanguage.Kotlin) {

    private val testingElements= TestingElements(ScriptingLanguage.Kotlin)

    override fun tasksToInvoke(): Array<String> = arrayOf(":app:processDebugResources")

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
                        buildToolsVersion("29.0.3")
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