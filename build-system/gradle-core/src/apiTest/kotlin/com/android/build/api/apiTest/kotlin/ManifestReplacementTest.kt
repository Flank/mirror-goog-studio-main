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
import com.google.common.truth.Truth
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Test
import kotlin.test.assertNotNull

class ManifestReplacementTest: VariantApiBaseTest(TestType.Script) {
    @Test
    fun manifestReplacementTest() {
        given {
            tasksToInvoke.add(":app:processDebugResources")
            addModule(":app") {
                buildFile =
                        // language=kotlin
                    """
            plugins {
                    id("com.android.application")
                    kotlin("android")
                    kotlin("android.extensions")
            }
            import org.gradle.api.DefaultTask
            import org.gradle.api.file.RegularFileProperty
            import org.gradle.api.tasks.InputFile
            import org.gradle.api.tasks.OutputFile
            import org.gradle.api.tasks.TaskAction
            import com.android.build.api.artifact.SingleArtifact
            ${testingElements.getGitVersionTask()}
            ${testingElements.getManifestProducerTask()}
            android {
                ${testingElements.addCommonAndroidBuildLogic()}
            }
            androidComponents {
                val gitVersionProvider = tasks.register<GitVersionTask>("gitVersionProvider") {
                    gitVersionOutputFile.set(
                        File(project.buildDir, "intermediates/gitVersionProvider/output"))
                    outputs.upToDateWhen { false }
                }
                onVariants { variant ->
                    val manifestProducer = tasks.register<ManifestProducerTask>("${'$'}{variant.name}ManifestProducer") {
                        gitInfoFile.set(gitVersionProvider.flatMap(GitVersionTask::gitVersionOutputFile))
                    }
                    variant.artifacts.use(manifestProducer)
                        .wiredWith(ManifestProducerTask::outputManifest)
                        .toCreate(SingleArtifact.MERGED_MANIFEST)
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
                ":app:gitVersionProvider",
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
