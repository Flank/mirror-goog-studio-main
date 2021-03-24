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

import com.android.build.api.apiTest.VariantApiBaseTest
import com.google.common.truth.Truth
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Test
import kotlin.test.assertNotNull

class ManifestTransformerTest: VariantApiBaseTest(TestType.Script, ScriptingLanguage.Groovy) {
    @Test
    fun manifestTransformerTest() {
        given {
            tasksToInvoke.add(":app:processDebugResources")

            addModule(":app") {
                buildFile =
                    """
            plugins {
                id 'com.android.application'
            }
            ${testingElements.getGitVersionTask()}
            ${testingElements.getGitVersionManifestTransformerTask()}

            import com.android.build.api.artifact.SingleArtifact

            android {
                ${testingElements.addCommonAndroidBuildLogic()}
            }
            TaskProvider gitVersionProvider = tasks.register('gitVersionProvider', GitVersionTask) {
                task ->
                    task.gitVersionOutputFile.set(
                        new File(project.buildDir, "intermediates/gitVersionProvider/output")
                    )
                    task.outputs.upToDateWhen { false }
            }

            androidComponents {
                onVariants(selector().all(), {
                    TaskProvider manifestUpdater = tasks.register(it.getName() + 'ManifestUpdater', ManifestTransformerTask) {
                        task ->
                            task.gitInfoFile.set(gitVersionProvider.flatMap { it.getGitVersionOutputFile() })
                    }
                    it.artifacts.use(manifestUpdater)
                        .wiredWithFiles(
                            { it.mergedManifest },
                            { it.updatedManifest })
                        .toTransform(SingleArtifact.MERGED_MANIFEST.INSTANCE)
                })
            }
            """.trimIndent()

                testingElements.addManifest(this)
                testingElements.addMainActivity(this)
            }
        }
        withDocs {
            index =
                    // language=markdown
                    """
# Test manifest transformation

This sample shows how to transform the manifest file.

## To Run
./gradlew debugManifestUpdater
            """.trimIndent()
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("BUILD SUCCESSFUL")
            arrayOf(
                ":app:gitVersionProvider",
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
