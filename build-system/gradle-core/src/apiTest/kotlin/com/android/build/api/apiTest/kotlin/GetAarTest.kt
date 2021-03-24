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
import org.junit.Test
import kotlin.test.assertNotNull

class GetAarTest: VariantApiBaseTest(TestType.Script) {

    @Test
    fun getAarTest() {
        given {
            tasksToInvoke.add(":lib:debugAarUpload")
            addModule(":lib") {
                @Suppress("RemoveExplicitTypeArguments")
                buildFile =
                        // language=kotlin
                    """
            plugins {
                    id("com.android.library")
                    kotlin("android")
                    kotlin("android.extensions")
            }
            import org.gradle.api.DefaultTask
            import org.gradle.api.file.RegularFileProperty
            import org.gradle.api.tasks.InputFile
            import org.gradle.api.tasks.TaskAction

            import com.android.build.api.variant.BuiltArtifactsLoader
            import com.android.build.api.artifact.SingleArtifact
            import org.gradle.api.provider.Property
            import org.gradle.api.tasks.Internal

            abstract class AarUploadTask: DefaultTask() {

                @get:InputFile
                abstract val aar: RegularFileProperty

                @TaskAction
                fun taskAction() {
                    println("Uploading ${'$'}{aar.get().asFile.absolutePath} to fantasy server...")
                }
            }
            android {
                ${testingElements.addCommonAndroidBuildLogic()}
            }
            androidComponents {
                onVariants { variant ->
                    project.tasks.register<AarUploadTask>("${ '$' }{variant.name}AarUpload") {
                        aar.set(variant.artifacts.get(SingleArtifact.AAR))
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
# artifacts.get in Kotlin

This sample shows how to obtain the aar from the AGP.
The [onVariants] block will wire the [AarUploadTask] input property (apkFolder) by using
the [Artifacts.get] call with the right [SingleArtifact.
`aar.set(artifacts.get(SingleArtifact.AAR))`
## To Run
./gradlew debugAarUpload
expected result : "Uploading .... to a fantasy server...s" message.
            """.trimIndent()
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("Uploading")
            Truth.assertThat(output).contains("BUILD SUCCESSFUL")
        }
    }
}
