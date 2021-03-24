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

class GetMappingFileTest: VariantApiBaseTest(TestType.Script) {
    @Test
    fun getMappingFile() {
        given {
            tasksToInvoke.add(":app:debugMappingFileUpload")
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
            import org.gradle.api.file.RegularFileProperty
            import org.gradle.api.tasks.InputFile
            import org.gradle.api.tasks.TaskAction

            import com.android.build.api.variant.BuiltArtifactsLoader
            import com.android.build.api.artifact.SingleArtifact
            import org.gradle.api.provider.Property
            import org.gradle.api.tasks.Internal

            abstract class MappingFileUploadTask: DefaultTask() {

                @get:InputFile
                abstract val mappingFile: RegularFileProperty

                @TaskAction
                fun taskAction() {
                    println("Uploading ${'$'}{mappingFile.get().asFile.absolutePath} to fantasy server...")
                }
            }
            android {
                ${testingElements.addCommonAndroidBuildLogic()}
                buildTypes {
                    getByName("debug") {
                        isMinifyEnabled = true
                    }
                }
            }
            androidComponents {
                onVariants { variant ->
                    project.tasks.register<MappingFileUploadTask>("${ '$' }{variant.name}MappingFileUpload") {
                        mappingFile.set(variant.artifacts.get(SingleArtifact.OBFUSCATION_MAPPING_FILE))
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

This sample shows how to obtain the obfuscation mapping file from the AGP.
The [onVariants] block will wire the [MappingFileUploadTask] input property (apkFolder) by using
the [Artifacts.get] call with the right [SingleArtifact.
`mapping.set(artifacts.get(SingleArtifact.OBFUSCATION_MAPPING_FILE))`
## To Run
./gradlew debugMappingFileUpload
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
