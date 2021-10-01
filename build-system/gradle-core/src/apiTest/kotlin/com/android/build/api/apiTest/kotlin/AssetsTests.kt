/*
 * Copyright (C) 2021 The Android Open Source Project
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
import com.android.build.api.artifact.Artifact
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl
import com.android.build.gradle.options.BooleanOption
import com.google.common.truth.Truth
import org.junit.Test
import java.io.File
import java.util.Locale
import java.util.jar.JarFile
import kotlin.test.assertNotNull

class AssetsTests: VariantApiBaseTest(
    TestType.Script
) {

    @Test
    fun addCustomAsset() {
        given {
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

            import com.android.build.api.artifact.MultipleArtifact

            android {
                ${testingElements.addCommonAndroidBuildLogic()}
            }

            abstract class AssetCreatorTask: DefaultTask() {
                @get:OutputFiles
                abstract val outputDirectory: DirectoryProperty

                @ExperimentalStdlibApi
                @TaskAction
                fun taskAction() {
                    outputDirectory.get().asFile.mkdirs()
                    File(outputDirectory.get().asFile, "custom_asset.txt")
                        .writeText("some real asset file")
                }
            }

            androidComponents {
                onVariants(selector().withBuildType("debug")) { variant ->

                    val assetCreationTask =
                        project.tasks.register<AssetCreatorTask>("create${'$'}{variant.name}Asset")
                    variant.artifacts.use(assetCreationTask)
                            .wiredWith(
                                AssetCreatorTask::outputDirectory
                            )
                            .toAppendTo(MultipleArtifact.ASSETS)
                }
            }
                """.trimIndent()
                testingElements.addManifest(this)
                addSource(
                    "src/main/kotlin/com/android/build/example/minimal/MainActivity.kt",
                    //language=kotlin
                    """
                    package com.android.build.example.minimal

                    import android.app.Activity

                    class MainActivity : Activity() {
                    }
                    """.trimIndent())
            }
        }
        withOptions(mapOf(BooleanOption.ENABLE_PROFILE_JSON to true))
        withDocs {
            index =
                    // language=markdown
                """
# Adding a BuildConfig field in Kotlin

This sample show how to add a field in the BuildConfig class for which the value is known at
configuration time.

The added field is used in the MainActivity.kt file.
            """.trimIndent()
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("BUILD SUCCESSFUL")
        }
        val apkFolder = File(super.testProjectDir.root,
            "addCustomAsset/app/build/"
                    + Artifact.Category.OUTPUTS.name.toLowerCase(Locale.US)
                    + "/"
                    + SingleArtifact.APK.getFolderName()
                    + "/debug")
        val builtArtifacts = BuiltArtifactsLoaderImpl.loadFromFile(
            File(apkFolder, BuiltArtifactsImpl.METADATA_FILE_NAME)
        )
            ?: throw RuntimeException("Cannot load APKs")
        if (builtArtifacts.elements.size != 1)
            throw RuntimeException("Expected one APK !")
        val apk = File(builtArtifacts.elements.single().outputFile)
        JarFile(apk).use {
            Truth.assertThat(it.getJarEntry("assets/custom_asset.txt")).isNotNull()
        }
    }
}
