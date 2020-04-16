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
import java.io.File
import kotlin.test.assertNotNull

class GroovyScriptApiTests : VariantApiBaseTest(TestType.Script, ScriptingLanguage.Groovy) {

    private val testingElements = TestingElements(scriptingLanguage)

    @Test
    fun getApksTest() {
        given {
            tasksToInvoke.add(":app:debugDisplayApks")

            addModule(":app") {
                buildFile =
                    """
                    plugins {
                        id 'com.android.application'
                    }

                    ${testingElements.getDisplayApksTask()}

                    android {
                        compileSdkVersion(29)
                        defaultConfig {
                            minSdkVersion(21)
                            targetSdkVersion(29)
                        }

                        onVariantProperties {
                            project.tasks.register(it.getName() + "DisplayApks", DisplayApksTask.class) {
                                it.apkFolder.set(operations.get(ArtifactTypes.APK.INSTANCE))
                                it.builtArtifactsLoader.set(operations.getBuiltArtifactsLoader())
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
    fun manifestReplacementTest() {
        given {
            tasksToInvoke.add(":app:processDebugResources")

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
                    defaultConfig {
                        minSdkVersion 21
                        targetSdkVersion 29
                    }

                    onVariantProperties {
                        TaskProvider gitVersionProvider = tasks.register(it.getName() + 'GitVersionProvider', GitVersionTask) {
                            task ->
                                task.gitVersionOutputFile.set(
                                    new File(project.buildDir, "intermediates/gitVersionProvider/output")
                                )
                                task.outputs.upToDateWhen { false }
                        }

                        TaskProvider manifestProducer = tasks.register(it.getName() + 'ManifestProducer', ManifestProducerTask) {
                            task ->
                                task.gitInfoFile.set(gitVersionProvider.flatMap { it.getGitVersionOutputFile() })
                                task.outputManifest.set(
                                    new File(project.buildDir, "intermediates/" + getName() + "ManifestProducer/output")
                                )
                        }
                        it.operations.replace(manifestProducer,
                                { it.outputManifest })
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
                ${testingElements.getManifestTransformerTask()}

                import com.android.build.api.artifact.ArtifactTypes

                android {
                    compileSdkVersion 29
                    defaultConfig {
                        minSdkVersion 21
                        targetSdkVersion 29
                    }

                    TaskProvider gitVersionProvider = tasks.register('gitVersionProvider', GitVersionTask) {
                        task ->
                            task.gitVersionOutputFile.set(
                                new File(project.buildDir, "intermediates/gitVersionProvider/output")
                            )
                            task.outputs.upToDateWhen { false }
                    }

                    onVariantProperties {
                        TaskProvider manifestUpdater = tasks.register(it.getName() + 'ManifestUpdater', ManifestTransformerTask) {
                            task ->
                                task.gitInfoFile.set(gitVersionProvider.flatMap { it.getGitVersionOutputFile() })
                        }
                        it.operations.transform(manifestUpdater,
                                { it.mergedManifest },
                                { it.updatedManifest })
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

    @Test
    fun workerEnabledTransformation() {
        val outFolderForApk = File(testProjectDir.root, "${testName.methodName}/build/acme_apks")
        given {
            tasksToInvoke.add(":app:copyDebugApks")
            addModule(":app") {
                buildFile =
                    """
                    plugins {
                        id 'com.android.application'
                    }
                    import java.io.Serializable
                    import javax.inject.Inject
                    import org.gradle.api.DefaultTask
                    import org.gradle.api.file.RegularFileProperty
                    import org.gradle.api.tasks.InputFile
                    import org.gradle.api.tasks.OutputFile
                    import org.gradle.api.tasks.TaskAction
                    import org.gradle.workers.WorkerExecutor
                    import com.android.build.api.artifact.ArtifactTypes 
                    import com.android.build.api.artifact.ArtifactTransformationRequest
                    import com.android.build.api.variant.BuiltArtifact

                    import com.android.build.api.artifact.ArtifactKind
                    import com.android.build.api.artifact.ArtifactType
                    import com.android.build.api.artifact.ArtifactType.Replaceable
                    import com.android.build.api.artifact.ArtifactType.ContainsMany
                    import com.android.build.api.artifact.ArtifactTransformationRequest

                    class ACME_APK extends ArtifactType<Directory> implements Replaceable, ContainsMany {
                            ACME_APK() {
                                super(ArtifactKind.DIRECTORY.INSTANCE)
                            }

                    }

                    ACME_APK acme_apk_instance = new ACME_APK()


                    ${testingElements.getCopyApksTask()}

                    android {
                        compileSdkVersion(29)
                        defaultConfig {
                            minSdkVersion(21)
                            targetSdkVersion(29)
                        }

                        onVariantProperties {
                            TaskProvider copyApksProvider = tasks.register('copy' + it.getName() + 'Apks', CopyApksTask)

                            ArtifactTransformationRequest request = 
                                it.operations.use(copyApksProvider)
                                .toRead(ArtifactTypes.APK.INSTANCE, { it.getApkFolder() })
                                .andWrite(acme_apk_instance, { it.getOutFolder()}, "${outFolderForApk.absolutePath}")

                            copyApksProvider.configure {
                                it.transformationRequest.set(request)
                            }
                        }
                    } 
                """.trimIndent()
                testingElements.addManifest(this)
            }
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("BUILD SUCCESSFUL")
            val task = task(":app:copydebugApks")
            assertNotNull(task)
            Truth.assertThat(task.outcome).isEqualTo(TaskOutcome.SUCCESS)
            Truth.assertThat(outFolderForApk.listFiles()?.asList()?.map { it.name }).containsExactly(
                "app-debug.apk", "output.json"
            )
        }
    }
}
