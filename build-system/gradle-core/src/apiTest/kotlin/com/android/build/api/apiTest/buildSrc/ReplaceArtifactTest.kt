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
package com.android.build.api.apiTest.buildSrc

import com.android.build.api.apiTest.TestingElements
import com.android.build.api.apiTest.VariantApiBaseTest
import com.google.common.truth.Truth.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Test
import kotlin.test.assertNotNull

/**
 * Test with a buildSrc plugin that replace the manifest file producer task.
 */
class BuildSrcPluginReplaceArtifactTest: VariantApiBaseTest() {

    private val testingElements = TestingElements(scriptingLanguage)

    override fun tasksToInvoke(): Array<String> = arrayOf(":app:processDebugResources")

    @Test
    fun replaceManifest() {
        given {
            addBuildSrc() {

                testingElements.addGitVersionTask(this)
                testingElements.addManifestProducerTask(this)
                testingElements.addManifestVerifierTask(this)

                addSource(
                    "src/main/kotlin/ExamplePlugin.kt",
                    // language=kotlin
                    """ 
                import com.android.build.api.artifact.ArtifactTypes
                import org.gradle.api.Plugin
                import org.gradle.api.Project
                import java.io.File
                import com.android.build.api.dsl.CommonExtension

                abstract class ExamplePlugin: Plugin<Project> {

                    override fun apply(project: Project) {
                        val gitVersionProvider =
                            project.tasks.register("gitVersionProvider", GitVersionTask::class.java) {
                                it.gitVersionOutputFile.set(
                                    File(project.buildDir, "intermediates/gitVersionProvider/output")
                                )
                                it.outputs.upToDateWhen { false }
                            }

                        val android = project.extensions.getByType(CommonExtension::class.java)

                        android.onVariantProperties {

                            val manifestUpdater =
                                project.tasks.register(name + "ManifestUpdater", ManifestProducerTask::class.java) {
                                    it.gitInfoFile.set(gitVersionProvider.flatMap { task -> task.gitVersionOutputFile })
                                }
                            operations.replace(manifestUpdater, ManifestProducerTask::updatedManifest)
                                .on(ArtifactTypes.MERGED_MANIFEST)

                            project.tasks.register(name + "Verifier", VerifyManifestTask::class.java) {
                                it.apkFolder.set(operations.get(ArtifactTypes.APK))
                                it.builtArtifactsLoader.set(operations.getBuiltArtifactsLoader())
                            }
                        }
                    }
                }
                """.trimIndent()
                )

                buildFile =
                    """
                dependencies {
                    implementation(kotlin("stdlib"))
                    implementation("com.android.tools.build:gradle-api:${com.android.Version.ANDROID_GRADLE_PLUGIN_VERSION}")
                    implementation("com.android.tools.apkparser:apkanalyzer-cli:${com.android.Version.ANDROID_TOOLS_BASE_VERSION}")
                    gradleApi()
                }
                """.trimIndent()
            }
            addModule(":app") {
                buildFile =
                    """
                    plugins {
                            id("com.android.application")
                            kotlin("android")
                            kotlin("android.extensions")
                    }
                    apply<ExamplePlugin>()

                    android {
                        compileSdkVersion(29)
                        buildToolsVersion("29.0.3")
                        defaultConfig {
                            minSdkVersion(21)
                            targetSdkVersion(29)
                        }
                    }
                    """.trimIndent()

                testingElements.addManifest(this)

                testingElements.addMainActivity(this)
            }
        }
        check {
            assertNotNull(this)
            assertThat(output).contains("BUILD SUCCESSFUL")
            val assembleTask = task(":app:debugManifestUpdater")
            assertNotNull(assembleTask)
            assertThat(assembleTask.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":app:processDebugMainManifest")).isNull()
        }
    }
}