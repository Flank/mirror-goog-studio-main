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
import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.google.common.truth.Truth
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Test
import java.io.File
import kotlin.test.assertNotNull

class WorkerEnabledTransformationTest: VariantApiBaseTest(TestType.Script, ScriptingLanguage.Groovy) {

    @Test
    fun workerEnabledTransformation() {
        given {
            tasksToInvoke.add(":app:copyDebugApks")
            addModule(":app") {
                buildFile = """
            plugins {
                id 'com.android.application'
            }
            import java.io.Serializable
            import java.nio.file.Files
            import javax.inject.Inject

            import org.gradle.api.file.Directory
            import org.gradle.api.file.DirectoryProperty
            import org.gradle.api.file.RegularFileProperty
            import org.gradle.api.provider.Property
            import org.gradle.api.tasks.InputFiles
            import org.gradle.api.tasks.Internal
            import org.gradle.api.tasks.TaskAction
            import org.gradle.workers.WorkerExecutor

            import com.android.build.api.artifact.SingleArtifact
            import com.android.build.api.artifact.ArtifactTransformationRequest
            import com.android.build.api.variant.BuiltArtifact

            ${testingElements.getCopyApksTask()}

            android {
                ${testingElements.addCommonAndroidBuildLogic()}
            }

            androidComponents {

                onVariants(selector().all(), { variant ->
                    TaskProvider copyApksProvider = tasks.register('copy' + variant.getName() + 'Apks', CopyApksTask)

                    ArtifactTransformationRequest request =
                        variant.artifacts.use(copyApksProvider)
                            .wiredWithDirectories(
                                { it.getApkFolder() },
                                { it.getOutFolder()})
                            .toTransformMany(SingleArtifact.APK.INSTANCE)

                    copyApksProvider.configure {
                        it.transformationRequest.set(request)
                    }
                })
            }
            """.trimIndent()
                testingElements.addManifest(this)
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
