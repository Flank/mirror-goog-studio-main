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
import kotlin.test.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized


@RunWith(Parameterized::class)
class TransformApiTest(private val artifact: String, private val plugin: String):
    VariantApiBaseTest(TestType.Script) {
    companion object {

        @Parameterized.Parameters(name = "artifact_{0}")
        @JvmStatic
        fun parameters() = listOf(
            arrayOf("AAR", "com.android.library"),
            arrayOf("BUNDLE", "com.android.application")
        )
    }
    @Test
    fun androidArtifactTransformTest() {
        given {
            tasksToInvoke.add(":module:debugConsumeArtifact")
            addModule(":module") {
                @Suppress("RemoveExplicitTypeArguments")
                buildFile =
                        // language=kotlin
                    """
        plugins {
                id("$plugin")
                kotlin("android")
                kotlin("android.extensions")
        }
        import org.gradle.api.DefaultTask
        import org.gradle.api.file.RegularFileProperty
        import org.gradle.api.tasks.InputFiles
        import org.gradle.api.tasks.TaskAction
        import org.gradle.api.provider.Property
        import org.gradle.api.tasks.Internal
        import com.android.build.api.artifact.ArtifactType
        import org.gradle.api.tasks.OutputFile
        import com.android.utils.appendCapitalized

        abstract class UpdateArtifactTask: DefaultTask() {
            @get: InputFiles
            abstract val  initialArtifact: RegularFileProperty

            @get: OutputFile
            abstract val updatedArtifact: RegularFileProperty

            @TaskAction
            fun taskAction() {
                val versionCode = "artifactTransformed = true"
                println("artifactPresent = " + initialArtifact.isPresent)
                updatedArtifact.get().asFile.writeText(versionCode)
            }
        }
        abstract class ConsumeArtifactTask: DefaultTask() {
            @get: InputFiles
            abstract val finalArtifact: RegularFileProperty

            @TaskAction
            fun taskAction() {
                println(finalArtifact.get().asFile.readText())
            }
        }
        android {
            ${testingElements.addCommonAndroidBuildLogic()}
            defaultConfig {
                versionCode = 3
            }
            onVariantProperties {
                val updateArtifact = project.tasks.register<UpdateArtifactTask>("${'$'}{name}UpdateArtifact") {
                    initialArtifact.set(artifacts.get(ArtifactType.$artifact))
                }
                val finalArtifact = project.tasks.register<ConsumeArtifactTask>("${'$'}{name}ConsumeArtifact") {
                    finalArtifact.set(artifacts.get(ArtifactType.$artifact))
                }
                artifacts.use(updateArtifact)
                    .wiredWithFiles(
                        UpdateArtifactTask::initialArtifact,
                        UpdateArtifactTask::updatedArtifact)
                .toTransform(ArtifactType.$artifact)
            }
        }
    """.trimIndent()
                testingElements.addManifest(this)
            }
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("artifactPresent = true")
            Truth.assertThat(output).contains("artifactTransformed = true")
            Truth.assertThat(output).contains("BUILD SUCCESSFUL")
        }
    }
}