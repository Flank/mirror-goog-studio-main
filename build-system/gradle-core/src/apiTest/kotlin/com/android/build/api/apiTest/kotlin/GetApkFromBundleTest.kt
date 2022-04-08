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
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Test
import kotlin.test.assertNotNull

class GetApkFromBundleTest: VariantApiBaseTest(TestType.Script) {
    @Test
    fun getApksFromBundleTest() {
        given {
            tasksToInvoke.addAll(listOf("clean", ":app:debugDisplayApkFromBundle"))
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
            import org.gradle.api.file.RegularFileProperty
            import org.gradle.api.DefaultTask
            import org.gradle.api.tasks.InputFile
            import org.gradle.api.tasks.TaskAction
            import com.android.build.api.artifact.SingleArtifact
            import org.gradle.api.provider.Property
            import org.gradle.api.tasks.Internal
            abstract class DisplayApkFromBundleTask: DefaultTask() {
                @get:InputFile
                abstract val apkFromBundle: RegularFileProperty

                @TaskAction
                fun taskAction() {
                    println("Got a Universal APK " + apkFromBundle.get().asFile.canonicalPath)
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
                    project.tasks.register<DisplayApkFromBundleTask>("${ '$' }{variant.name}DisplayApkFromBundle") {
                        apkFromBundle.set(variant.artifacts.get(SingleArtifact.APK_FROM_BUNDLE))
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
# artifacts.get in Kotlin
This sample shows how to obtain a universal APK from the AGP. Because it goes through the bundle
file, it is a slower build flow than using the [SingleArtifact.APK] public artifact.
The built artifact is identified by its [SingleArtifact and in this case, it's [SingleArtifact.APK_FROM_BUNDLE].
The [onVariants] block will wire the [DisplayApkFromBundle] input property (apkFromBundle) by using
the [Artifacts.get] call with the right [SingleArtifact.
`apkFromBundle.set(artifacts.get(SingleArtifact.APK_FROM_BUNDLE))`
## To Run
./gradlew debugDisplayApkFromBundle
expected result : "Got an Universal APK...." message.
            """.trimIndent()
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("Got a Universal APK ")
            Truth.assertThat(output).contains("BUILD SUCCESSFUL")
            Truth.assertThat(task(":app:packageDebugBundle")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
            Truth.assertThat(task(":app:packageDebugUniversalApk")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }
    }
}
