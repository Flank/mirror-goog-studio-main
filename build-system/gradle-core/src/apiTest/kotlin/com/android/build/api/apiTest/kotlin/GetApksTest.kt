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
import com.android.build.gradle.options.BooleanOption
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.ArtifactAccess
import org.junit.Test
import kotlin.test.assertNotNull

class GetApksTest: VariantApiBaseTest(TestType.Script) {
    @Test
    fun getApksTest() {
        given {
            tasksToInvoke.addAll(listOf("clean", ":app:debugDisplayApks"))
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
            import org.gradle.api.file.DirectoryProperty
            import org.gradle.api.tasks.InputFiles
            import org.gradle.api.tasks.TaskAction
            import com.android.build.api.variant.BuiltArtifactsLoader
            import com.android.build.api.artifact.SingleArtifact
            import org.gradle.api.provider.Property
            import org.gradle.api.tasks.Internal
            ${testingElements.getDisplayApksTask()}
            android {
                ${testingElements.addCommonAndroidBuildLogic()}
            }

            androidComponents {
                onVariants { variant ->
                    project.tasks.register<DisplayApksTask>("${ '$' }{variant.name}DisplayApks") {
                        apkFolder.set(variant.artifacts.get(SingleArtifact.APK))
                        builtArtifactsLoader.set(variant.artifacts.getBuiltArtifactsLoader())
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
This sample show how to obtain a built artifact from the AGP. The built artifact is identified by
its [SingleArtifact. and in this case, it's [SingleArtifact.APK].
The [onVariants] block will wire the [DisplayApksTask] input property (apkFolder) by using
the [Artifacts.get] call with the right [SingleArtifact.
`apkFolder.set(artifacts.get(SingleArtifact.APK))`
Since more than one APK can be produced by the build when dealing with multi-apk, you should use the
[BuiltArtifacts] interface to load the metadata associated with produced files using
[BuiltArtifacts.load] method.
`builtArtifactsLoader.get().load(apkFolder.get())'
Once loaded, the built artifacts can be accessed.
## To Run
./gradlew debugDisplayApks
expected result : "Got an APK...." message.
            """.trimIndent()
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("Got an APK")
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
