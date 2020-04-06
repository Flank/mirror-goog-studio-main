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

package com.android.build.api.apiTest.groovy

import com.android.build.api.apiTest.TestingElements
import com.android.build.api.apiTest.VariantApiBaseTest
import com.google.common.truth.Truth
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Test
import kotlin.test.assertNotNull

class GroovyReplaceArtifactTest : VariantApiBaseTest(ScriptingLanguage.Groovy) {

    private val testingElements = TestingElements(scriptingLanguage)

    override fun tasksToInvoke(): Array<String> = arrayOf(":app:processDebugResources")

    @Test
    fun manifestReplacementTask() {
        given {
            addModule(":app") {
                buildFile =
                    """
                plugins {
                    id 'com.android.application'
                } 
                ${testingElements.getGitVersionTask()}
                ${testingElements.getManifestProducerTask()}

                import com.android.build.api.artifact.ArtifactTypes

                android {
                    compileSdkVersion 29
                    buildToolsVersion "29.0.3"
                    defaultConfig {
                        minSdkVersion 21
                        targetSdkVersion 29
                    }
                    
                    onVariantProperties {
                        TaskProvider gitVersionProvider = tasks.register(it.getName() + 'GitVersionProvider', GitVersionTask) {
                            task ->
                                task.getGitVersionOutputFile().set(
                                    new File(project.buildDir, "intermediates/gitVersionProvider/output")
                                )
                                task.getOutputs().upToDateWhen { false }
                        }
                
                        TaskProvider manifestProducer = tasks.register(it.getName() + 'ManifestProducer', ManifestProducerTask) {
                            task ->
                                task.getGitInfoFile().set(gitVersionProvider.flatMap { it.getGitVersionOutputFile() })
                                task.getOutputManifest().set(
                                    new File(project.buildDir, "intermediates/" + getName() + "ManifestProducer/output")
                                )
                        }
                        it.operations.replace(manifestProducer,
                                { it.getOutputManifest() })
                        .on(ArtifactTypes.MERGED_MANIFEST.INSTANCE)
                    }
                }
                """.trimIndent()

                testingElements.addManifest(this)
                testingElements.addMainActivity(this)
            }
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("BUILD SUCCESSFUL")
            arrayOf(
                ":app:debugGitVersionProvider",
                ":app:debugManifestProducer"
            ).forEach {
                val task = task(it)
                assertNotNull(task)
                Truth.assertThat(task.outcome).isEqualTo(TaskOutcome.SUCCESS)

            }
            Truth.assertThat(task(":app:processDebugMainManifest")).isNull()
        }
    }
}