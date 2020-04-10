
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
                    import org.gradle.api.DefaultTask
                    import org.gradle.api.file.DirectoryProperty
                    import org.gradle.api.tasks.InputFiles
                    import org.gradle.api.tasks.TaskAction

                    import com.android.build.api.variant.BuiltArtifactsLoader
                    import com.android.build.api.artifact.ArtifactTypes
                    import org.gradle.api.provider.Property
                    import org.gradle.api.tasks.Internal

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
        withDocs {
            index =
                    // language=markdown
                """
# Operations.get in Kotlin
           
This sample show how to obtain a built artifact from the AGP. The built artifact is identified by
its [ArtifactTypes] and in this case, it's [ArtifactTypes.APK].
The [onVariantProperties] block will wire the [DisplayApksTask] input property (apkFolder) by using
the Operations.get call with the right ArtifactTypes
`apkFolder.set(operations.get(ArtifactTypes.APK))`
Since more than one APK can be produced by the build when dealing with multi-apk, you should use the 
[BuiltArtifacts] interface to load the metadata associated with produced files using 
[BuiltArtifacts.load] method. 
`builtArtifactsLoader.get().load(apkFolder.get())'
Once loaded, the built artifacts can be accessed. 
## To Run
/path/to/gradle debugDisplayApks 
expected result : "Got an APK...." message.
            """.trimIndent()
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("Got an APK")
            Truth.assertThat(output).contains("BUILD SUCCESSFUL")
        }
    }
    @Test
    fun manifestReplacementTest() {
        given {
            tasksToInvoke.add(":app:processDebugResources")
            addModule(":app") {
                buildFile =
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
                import com.android.build.api.artifact.ArtifactTypes
                ${testingElements.getGitVersionTask()}
                ${testingElements.getManifestProducerTask()}
                android {
                        compileSdkVersion(29)
                        buildToolsVersion("29.0.3")
                        defaultConfig {
                            minSdkVersion(21)
                            targetSdkVersion(29)
                        }
                    val gitVersionProvider = tasks.register<GitVersionTask>("gitVersionProvider") {
                        gitVersionOutputFile.set(
                            File(project.buildDir, "intermediates/gitVersionProvider/output"))
                        outputs.upToDateWhen { false }
                    }
                    onVariantProperties {
                        val manifestProducer = tasks.register<ManifestProducerTask>("${'$'}{name}ManifestProducer") {
                            gitInfoFile.set(gitVersionProvider.flatMap(GitVersionTask::gitVersionOutputFile))
                            outputManifest.set(
                                File(project.buildDir, "intermediates/${'$'}{name}/ManifestProducer/output")
                            )
                        }
                        operations.replace(manifestProducer, ManifestProducerTask::outputManifest)
                            .on(ArtifactTypes.MERGED_MANIFEST)
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
                                gitInfoFile.set(gitVersionProvider.flatMap(GitVersionTask::gitVersionOutputFile))
                            }
                            operations.transform(manifestUpdater,
                                    ManifestTransformerTask::mergedManifest,
                                    ManifestTransformerTask::updatedManifest)
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
