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

class GetBundleTest: VariantApiBaseTest(TestType.Script)  {
    @Test
    fun getBundleTest() {
        given {
            tasksToInvoke.add(":app:debugDisplayBundleFile")
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

            abstract class DisplayBundleFileTask: DefaultTask() {
                @get:InputFile
                abstract val bundleFile: RegularFileProperty

                @TaskAction
                fun taskAction() {
                    println("Got the Bundle  ${'$'}{bundleFile.get().asFile.absolutePath}")
                }
            }
            android {
                ${testingElements.addCommonAndroidBuildLogic()}
                defaultConfig {
                    versionCode = 3
                }
            }
            androidComponents {
                onVariants { variant ->
                    project.tasks.register<DisplayBundleFileTask>("${ '$' }{variant.name}DisplayBundleFile") {
                        bundleFile.set(variant.artifacts.get(SingleArtifact.BUNDLE))
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
# Artifacts.get in Kotlin

This sample shows how to obtain the bundle file from the AGP.
The [onVariants] block will wire the [DisplayBundleFile] input property (bundleFile) by using
the Artifacts.get call with the right SingleArtifact
`bundleFile.set(artifacts.get(SingleArtifact.BUNDLE))`
## To Run
./gradlew debugDisplayBundleFile
expected result : "Got the Bundle ...." message.
            """.trimIndent()
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("Got the Bundle")
            Truth.assertThat(output).contains("BUILD SUCCESSFUL")
        }
    }
}
