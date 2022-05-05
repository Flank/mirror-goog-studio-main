/*
 * Copyright (C) 2022 The Android Open Source Project
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
import com.android.build.gradle.options.BooleanOption
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.ArtifactAccess
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.assertNotNull

class BundleTest: VariantApiBaseTest(TestType.Script) {
    @Test
    fun addMetadataFileTest() {
        given {
            tasksToInvoke.addAll(listOf("clean", ":app:debugDisplayBundle"))
            addModule(":app") {
                @Suppress("RemoveExplicitTypeArguments")
                buildFile =
                        // language=kotlin
                    """
            plugins {
                    id("com.android.application")
                    kotlin("android")
            }
            import org.gradle.api.DefaultTask
            import org.gradle.api.file.RegularFileProperty
            import org.gradle.api.file.DirectoryProperty
            import org.gradle.api.tasks.InputFile
            import org.gradle.api.tasks.OutputFile
            import org.gradle.api.tasks.TaskAction
            import com.android.build.api.variant.BuiltArtifactsLoader
            import com.android.build.api.artifact.SingleArtifact
            import org.gradle.api.provider.Property
            import org.gradle.api.tasks.Internal
            import java.util.zip.ZipFile

            abstract class AddMetadataInBundleTask: DefaultTask() {
                @get:OutputFile
                abstract val metadataFile: RegularFileProperty

                @TaskAction
                fun taskAction() {
                    metadataFile.get().asFile.writeText("some metadata")
                }
            }

            abstract class DisplayBundleTask: DefaultTask() {

                @get:InputFile
                abstract val bundle: RegularFileProperty

                @TaskAction
                fun taskAction() {

                    ZipFile(bundle.get().asFile).use {
                        it.entries().asIterator().forEach { entry ->
                            println(entry.name)
                        }
                    }
                }
            }

            android {
                compileSdkVersion(29)
                defaultConfig {
                    minSdkVersion(21)
                    versionCode = 1
                }
                namespace = "com.example.addjavasource"
            }

            androidComponents {
                onVariants { variant ->
                    val metadataTask = project.tasks.register<AddMetadataInBundleTask>("${ '$' }{variant.name}AddMetadata") {
                        File(getBuildDir(), name).also {
                                metadataFile.set(File(it, "metadata.pb"))
                        }
                    }

                    variant.bundleConfig.addMetadataFile(
                        "com.android.build",
                        metadataTask.flatMap { it.metadataFile }
                    )

                    project.tasks.register<DisplayBundleTask>("${ '$' }{variant.name}DisplayBundle") {
                        bundle.set(variant.artifacts.get(SingleArtifact.BUNDLE))
                    }
                }
            }
        """.trimIndent()
                testingElements.addManifest( this)
            }
        }
        withOptions(mapOf(BooleanOption.ENABLE_PROFILE_JSON to true))
        withDocs {
            index =
                    // language=markdown
                """
# bundleConfig.addMetadataFile in Kotlin
This sample shows how to add a metadata file to the built bundle.
The [BundleConfig] variant object will be used to register the output of the AddMetadataInBundleTask
Task to a new metadata file to be added to the resulting bundle file.
## To Run
./gradlew debugDisplayBundle
expected result : You should see the added metadata.pb file added to the resulting bundle.
            """.trimIndent()
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("BUNDLE-METADATA/com.android.build/metadata.pb")
            Truth.assertThat(output).contains("BUILD SUCCESSFUL")
            super.onVariantStats {
                if (it.isDebug) {
                    Truth.assertThat(it.variantApiAccess.artifactAccessList).hasSize(1)
                    val artifactAccess = it.variantApiAccess.artifactAccessList[0]
                    Truth.assertThat(artifactAccess.type).isEqualTo(
                        ArtifactAccess.AccessType.GET
                    )
                }
            }
        }
    }
}
