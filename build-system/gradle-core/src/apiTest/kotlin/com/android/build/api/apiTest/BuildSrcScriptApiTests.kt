
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

import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Test
import java.io.File
import kotlin.test.assertNotNull
/**
 * Test with a buildSrc plugin that replace the manifest file producer task.
 */
class BuildSrcScriptApiTests: VariantApiBaseTest(
    TestType.BuildSrc
) {
    private val testingElements = TestingElements(scriptingLanguage)
    @Test
    fun getApksTest() {
        given {
            addBuildSrc() {
                testingElements.addGitVersionTask(this)
                testingElements.addManifestProducerTask(this)
                testingElements.addManifestVerifierTask(this)
                addSource(
                    "src/main/kotlin/ExamplePlugin.kt",
                    // language=kotlin
                    """
                import com.android.build.api.artifact.ArtifactType
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

                            val manifestProducer =
                                project.tasks.register(name + "ManifestProducer", ManifestProducerTask::class.java) {
                                    it.gitInfoFile.set(gitVersionProvider.flatMap(GitVersionTask::gitVersionOutputFile))
                                }
                            artifacts.use(manifestProducer)
                                .wiredWith(ManifestProducerTask::outputManifest)
                                .toCreate(ArtifactType.MERGED_MANIFEST)

                            project.tasks.register(name + "Verifier", VerifyManifestTask::class.java) {
                                it.apkFolder.set(artifacts.get(ArtifactType.APK))
                                it.builtArtifactsLoader.set(artifacts.getBuiltArtifactsLoader())
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
            assertThat(output).contains("BUILD SUCCESSFUL")
            val assembleTask = task(":app:debugManifestProducer")
            assertNotNull(assembleTask)
            assertThat(assembleTask.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":app:processDebugMainManifest")).isNull()
        }
    }
    @Test
    fun manifestReplacementTest() {
        given {
            addBuildSrc() {
                testingElements.addGitVersionTask(this)
                testingElements.addManifestProducerTask(this)
                testingElements.addManifestVerifierTask(this)
                addSource(
                    "src/main/kotlin/ExamplePlugin.kt",
                    // language=kotlin
                    """ 
                import com.android.build.api.artifact.ArtifactType
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

                            val manifestProducer =
                                project.tasks.register(name + "ManifestProducer", ManifestProducerTask::class.java) {
                                    it.gitInfoFile.set(gitVersionProvider.flatMap(GitVersionTask::gitVersionOutputFile))
                                }
                            artifacts.use(manifestProducer)
                                .wiredWith(ManifestProducerTask::outputManifest)
                                .toCreate(ArtifactType.MERGED_MANIFEST)

                            project.tasks.register(name + "Verifier", VerifyManifestTask::class.java) {
                                it.apkFolder.set(artifacts.get(ArtifactType.APK))
                                it.builtArtifactsLoader.set(artifacts.getBuiltArtifactsLoader())
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
            assertThat(output).contains("BUILD SUCCESSFUL")
            val assembleTask = task(":app:debugManifestProducer")
            assertNotNull(assembleTask)
            assertThat(assembleTask.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(task(":app:processDebugMainManifest")).isNull()
        }
    }
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
                import com.android.build.api.artifact.ArtifactType
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
                                project.tasks.register(name + "ManifestUpdater", ManifestTransformerTask::class.java) {
                                    it.gitInfoFile.set(gitVersionProvider.flatMap(GitVersionTask::gitVersionOutputFile))
                                }
                            artifacts.use(manifestUpdater)
                                .wiredWithFiles(
                                    ManifestTransformerTask::mergedManifest,
                                    ManifestTransformerTask::updatedManifest)
                                .toTransform(ArtifactType.MERGED_MANIFEST)
                
                            project.tasks.register(name + "Verifier", VerifyManifestTask::class.java) {
                                it.apkFolder.set(artifacts.get(ArtifactType.APK))
                                it.builtArtifactsLoader.set(artifacts.getBuiltArtifactsLoader())
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
            assertThat(output).contains("BUILD SUCCESSFUL")
            arrayOf(
                ":app:debugManifestUpdater",
                ":app:processDebugMainManifest",
                ":app:gitVersionProvider"
            ).forEach {
                val task = task(it)
                assertNotNull(task)
                assertThat(task.outcome).isEqualTo(TaskOutcome.SUCCESS)
            }
        }
    }

    @Test
    fun workerEnabledTransformation() {
        val outFolderForApk = File(testProjectDir.root, "${testName.methodName}/build/acme_apks")
        outFolderForApk.deleteRecursively()

        given {
            tasksToInvoke.add(":app:copyDebugApks")

            addBuildSrc() {
                testingElements.addCopyApksTask(this)
                addSource("src/main/kotlin/AcmeArtifactType.kt",
                    """
                    import org.gradle.api.file.Directory
                    import org.gradle.api.file.FileSystemLocation

                    import com.android.build.api.artifact.ArtifactKind
                    import com.android.build.api.artifact.Artifact.Category
                    import com.android.build.api.artifact.Artifact.SingleArtifact
                    import com.android.build.api.artifact.Artifact.Replaceable
                    import com.android.build.api.artifact.Artifact.ContainsMany

                    sealed class AcmeArtifactType<T : FileSystemLocation>(
                        kind: ArtifactKind<T>
                    ) : SingleArtifact<T>(kind, Category.INTERMEDIATES) {

                        object ACME_APK: AcmeArtifactType<Directory>(ArtifactKind.DIRECTORY), Replaceable, ContainsMany
                    }
                    """.trimIndent())
                addSource(
                    "src/main/kotlin/ExamplePlugin.kt",
                    // language=kotlin
                    """ 
                import org.gradle.api.Plugin
                import org.gradle.api.Project
                import java.io.File
                import com.android.build.api.dsl.CommonExtension
                import com.android.build.api.artifact.ArtifactType

                abstract class ExamplePlugin: Plugin<Project> {

                    override fun apply(project: Project) {

                        val android = project.extensions.getByType(CommonExtension::class.java)

                        android.onVariantProperties {

                            val copyApksProvider = project.tasks.register("copy${'$'}{name}Apks", CopyApksTask::class.java)

                            val transformationRequest = artifacts.use(copyApksProvider)
                                .wiredWithDirectories(
                                    CopyApksTask::apkFolder,
                                    CopyApksTask::outFolder)
                                .toTransformMany(ArtifactType.APK)

                            copyApksProvider.configure {
                                it.transformationRequest.set(transformationRequest)
                                it.outFolder.set(File("${outFolderForApk.absolutePath}"))
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
            val task = task(":app:copydebugApks")
            assertNotNull(task)
            Truth.assertThat(task.outcome).isEqualTo(TaskOutcome.SUCCESS)
            Truth.assertThat(outFolderForApk.listFiles()?.asList()?.map { it.name }).containsExactly(
                "app-debug.apk", BuiltArtifactsImpl.METADATA_FILE_NAME
            )
        }
    }

    private fun addCommonBuildFile(givenBuilder: GivenBuilder) {
        givenBuilder.buildFile =
            """
            plugins {
                    id("com.android.application")
                    kotlin("android")
                    kotlin("android.extensions")
            }

            apply<ExamplePlugin>()

            android { ${testingElements.addCommonAndroidBuildLogic()}
            }
            """.trimIndent()
    }
}
