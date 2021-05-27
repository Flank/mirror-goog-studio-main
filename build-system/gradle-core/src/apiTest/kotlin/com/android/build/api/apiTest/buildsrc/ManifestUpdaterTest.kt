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

package com.android.build.api.apiTest.buildsrc

import com.google.common.truth.Truth
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Test
import kotlin.test.assertNotNull

class ManifestUpdaterTest: BuildSrcScriptApiTest() {

    @Test
    fun manifestUpdaterTest() {
        given {
            addBuildSrc() {
                testingElements.addGitVersionTask(this)
                testingElements.addManifestTransformerTask(this)
                testingElements.addManifestVerifierTask(this)
                addSource(
                    "src/main/kotlin/ExamplePlugin.kt",
                    // language=kotlin
                    """
                import com.android.build.api.artifact.SingleArtifact
                import org.gradle.api.Plugin
                import org.gradle.api.Project
                import java.io.File
                import com.android.build.api.variant.AndroidComponentsExtension

                abstract class ExamplePlugin: Plugin<Project> {

                    override fun apply(project: Project) {
                        val gitVersionProvider =
                            project.tasks.register("gitVersionProvider", GitVersionTask::class.java) {
                                it.gitVersionOutputFile.set(
                                    File(project.buildDir, "intermediates/gitVersionProvider/output")
                                )
                                it.outputs.upToDateWhen { false }
                            }

                        val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)

                        androidComponents.onVariants { variant ->

                            val manifestUpdater =
                                project.tasks.register(variant.name + "ManifestUpdater", ManifestTransformerTask::class.java) {
                                    it.gitInfoFile.set(gitVersionProvider.flatMap(GitVersionTask::gitVersionOutputFile))
                                }
                            variant.artifacts.use(manifestUpdater)
                                .wiredWithFiles(
                                    ManifestTransformerTask::mergedManifest,
                                    ManifestTransformerTask::updatedManifest)
                                .toTransform(SingleArtifact.MERGED_MANIFEST)

                            project.tasks.register(variant.name + "Verifier", VerifyManifestTask::class.java) {
                                it.apkFolder.set(variant.artifacts.get(SingleArtifact.APK))
                                it.builtArtifactsLoader.set(variant.artifacts.getBuiltArtifactsLoader())
                            }
                        }
                    }
                }
                """.trimIndent()
                )
            }
            addModule(":app") {
                addCommonBuildFile(this)
                testingElements.addManifest(this)
                testingElements.addMainActivity(this)
            }
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("BUILD SUCCESSFUL")
            arrayOf(
                ":app:debugManifestUpdater",
                ":app:processDebugMainManifest",
                ":app:gitVersionProvider"
            ).forEach {
                val task = task(it)
                assertNotNull(task)
                Truth.assertThat(task.outcome).isEqualTo(TaskOutcome.SUCCESS)
            }
        }
    }
}
