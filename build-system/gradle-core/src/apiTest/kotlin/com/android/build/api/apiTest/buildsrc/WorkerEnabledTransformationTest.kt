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

import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.google.common.truth.Truth
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Test
import java.io.File
import kotlin.test.assertNotNull

class WorkerEnabledTransformationTest: BuildSrcScriptApiTest() {
    @Test
    fun workerEnabledTransformation() {
        given {
            tasksToInvoke.add(":app:copyDebugApks")

            addBuildSrc() {
                testingElements.addCopyApksTask(this)
                addSource(
                    "src/main/kotlin/ExamplePlugin.kt",
                    // language=kotlin
                    """
                import org.gradle.api.Plugin
                import org.gradle.api.Project
                import java.io.File
                import com.android.build.api.variant.AndroidComponentsExtension
                import com.android.build.api.artifact.SingleArtifact

                abstract class ExamplePlugin: Plugin<Project> {

                    override fun apply(project: Project) {

                        val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)

                        androidComponents.onVariants { variant ->

                            val copyApksProvider = project.tasks.register("copy${'$'}{variant.name}Apks", CopyApksTask::class.java)

                            val transformationRequest = variant.artifacts.use(copyApksProvider)
                                .wiredWithDirectories(
                                    CopyApksTask::apkFolder,
                                    CopyApksTask::outFolder)
                                .toTransformMany(SingleArtifact.APK)

                            copyApksProvider.configure {
                                it.transformationRequest.set(transformationRequest)
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
        withDocs {
            index =
                    // language=markdown
                    """
# Test TransformationRequest

This sample shows how to transform the artifact.
It copies the build apk to the specified directory.

## To Run
./gradlew copydebugApks
            """.trimIndent()
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("BUILD SUCCESSFUL")
            val task = task(":app:copydebugApks")
            assertNotNull(task)
            Truth.assertThat(task.outcome).isEqualTo(TaskOutcome.SUCCESS)
            val outFolder = File(testProjectDir.root, "${testName.methodName}/app/build/intermediates/apk/copydebugApks")
            Truth.assertThat(outFolder.listFiles()?.asList()?.map { it.name }).containsExactly(
                "app-debug.apk", BuiltArtifactsImpl.METADATA_FILE_NAME
            )
        }
    }
}
